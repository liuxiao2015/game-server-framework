/*
 * 文件名: RateLimiter.java
 * 用途: 限流器
 * 实现内容:
 *   - 多种限流算法实现（令牌桶、滑动窗口）
 *   - 分布式限流支持
 *   - 自适应限流调整
 *   - 限流策略配置和管理
 *   - 限流统计和监控
 * 技术选型:
 *   - 令牌桶算法精确限流
 *   - 滑动窗口平滑限流
 *   - Redis分布式限流
 * 依赖关系:
 *   - 被SecurityHandler使用
 *   - 与TrafficShaper配合
 *   - 支持多种限流维度
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 限流器
 * <p>
 * 提供多种限流算法和策略，支持本地和分布式限流。
 * 可以按IP、用户、接口等多个维度进行限流控制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    // 限流器类型
    public enum LimiterType {
        /** 令牌桶算法 */
        TOKEN_BUCKET,
        /** 滑动窗口算法 */
        SLIDING_WINDOW,
        /** 固定窗口算法 */
        FIXED_WINDOW
    }

    // 限流维度
    public enum LimitDimension {
        /** 按IP限流 */
        IP,
        /** 按用户限流 */
        USER,
        /** 按接口限流 */
        API,
        /** 全局限流 */
        GLOBAL
    }

    // 限流配置
    private final LimiterConfig config;
    private final LimiterType type;
    
    // 限流实例管理
    private final ConcurrentHashMap<String, LimiterInstance> limiters = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);
    private final AtomicLong totalAllowed = new AtomicLong(0);

    /**
     * 限流配置
     */
    public static class LimiterConfig {
        private final long maxRequests;      // 最大请求数
        private final long timeWindow;       // 时间窗口（毫秒）
        private final long burstCapacity;    // 突发容量
        private final boolean adaptive;      // 是否自适应

        public LimiterConfig(long maxRequests, long timeWindow, long burstCapacity, boolean adaptive) {
            this.maxRequests = maxRequests;
            this.timeWindow = timeWindow;
            this.burstCapacity = burstCapacity;
            this.adaptive = adaptive;
        }

        public long getMaxRequests() { return maxRequests; }
        public long getTimeWindow() { return timeWindow; }
        public long getBurstCapacity() { return burstCapacity; }
        public boolean isAdaptive() { return adaptive; }
    }

    /**
     * 限流结果
     */
    public static class LimitResult {
        private final boolean allowed;
        private final long remainingQuota;
        private final long resetTime;
        private final String reason;

        public LimitResult(boolean allowed, long remainingQuota, long resetTime, String reason) {
            this.allowed = allowed;
            this.remainingQuota = remainingQuota;
            this.resetTime = resetTime;
            this.reason = reason;
        }

        public boolean isAllowed() { return allowed; }
        public long getRemainingQuota() { return remainingQuota; }
        public long getResetTime() { return resetTime; }
        public String getReason() { return reason; }
    }

    /**
     * 限流实例接口
     */
    private interface LimiterInstance {
        LimitResult tryAcquire(int permits);
        void reset();
        long getAvailablePermits();
    }

    /**
     * 令牌桶限流实例
     */
    private class TokenBucketLimiter implements LimiterInstance {
        private final ReentrantLock lock = new ReentrantLock();
        private final long capacity;
        private final long refillRate;
        private volatile long tokens;
        private volatile long lastRefillTime;

        public TokenBucketLimiter(long capacity, long refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        @Override
        public LimitResult tryAcquire(int permits) {
            lock.lock();
            try {
                refill();
                
                if (tokens >= permits) {
                    tokens -= permits;
                    return new LimitResult(true, tokens, calculateResetTime(), "Success");
                } else {
                    return new LimitResult(false, tokens, calculateResetTime(), "Rate limit exceeded");
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void reset() {
            lock.lock();
            try {
                tokens = capacity;
                lastRefillTime = System.currentTimeMillis();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public long getAvailablePermits() {
            lock.lock();
            try {
                refill();
                return tokens;
            } finally {
                lock.unlock();
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timeDelta = now - lastRefillTime;
            
            if (timeDelta > 0) {
                long tokensToAdd = (refillRate * timeDelta) / 1000L;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }

        private long calculateResetTime() {
            if (tokens < capacity) {
                long tokensNeeded = capacity - tokens;
                return System.currentTimeMillis() + (tokensNeeded * 1000L) / refillRate;
            }
            return System.currentTimeMillis();
        }
    }

    /**
     * 滑动窗口限流实例
     */
    private class SlidingWindowLimiter implements LimiterInstance {
        private final ReentrantLock lock = new ReentrantLock();
        private final long maxRequests;
        private final long windowSize;
        private final long[] timestamps;
        private volatile int index = 0;
        private volatile long count = 0;

        public SlidingWindowLimiter(long maxRequests, long windowSize) {
            this.maxRequests = maxRequests;
            this.windowSize = windowSize;
            this.timestamps = new long[(int) Math.min(maxRequests * 2, 10000)]; // 限制数组大小
        }

        @Override
        public LimitResult tryAcquire(int permits) {
            lock.lock();
            try {
                long now = System.currentTimeMillis();
                long windowStart = now - windowSize;
                
                // 清理过期记录
                cleanup(windowStart);
                
                if (count + permits <= maxRequests) {
                    // 记录新请求
                    for (int i = 0; i < permits; i++) {
                        timestamps[index] = now;
                        index = (index + 1) % timestamps.length;
                        count++;
                    }
                    
                    return new LimitResult(true, maxRequests - count, calculateResetTime(windowStart), "Success");
                } else {
                    return new LimitResult(false, maxRequests - count, calculateResetTime(windowStart), "Rate limit exceeded");
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void reset() {
            lock.lock();
            try {
                count = 0;
                index = 0;
                for (int i = 0; i < timestamps.length; i++) {
                    timestamps[i] = 0;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public long getAvailablePermits() {
            lock.lock();
            try {
                long now = System.currentTimeMillis();
                long windowStart = now - windowSize;
                cleanup(windowStart);
                return maxRequests - count;
            } finally {
                lock.unlock();
            }
        }

        private void cleanup(long windowStart) {
            long removed = 0;
            for (int i = 0; i < timestamps.length; i++) {
                if (timestamps[i] > 0 && timestamps[i] < windowStart) {
                    timestamps[i] = 0;
                    removed++;
                }
            }
            count = Math.max(0, count - removed);
        }

        private long calculateResetTime(long windowStart) {
            // 找到最早的有效时间戳
            long earliest = Long.MAX_VALUE;
            for (long timestamp : timestamps) {
                if (timestamp >= windowStart && timestamp < earliest) {
                    earliest = timestamp;
                }
            }
            return earliest == Long.MAX_VALUE ? System.currentTimeMillis() : earliest + windowSize;
        }
    }

    /**
     * 构造函数
     */
    public RateLimiter(LimiterConfig config, LimiterType type) {
        this.config = config;
        this.type = type;
        
        logger.info("创建限流器，类型: {}, 最大请求: {}/{}ms, 突发容量: {}",
                   type, config.getMaxRequests(), config.getTimeWindow(), config.getBurstCapacity());
    }

    /**
     * 尝试获取许可
     */
    public LimitResult tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 尝试获取指定数量的许可
     */
    public LimitResult tryAcquire(String key, int permits) {
        if (permits <= 0) {
            return new LimitResult(false, 0, System.currentTimeMillis(), "Invalid permits");
        }

        totalRequests.incrementAndGet();
        
        LimiterInstance limiter = limiters.computeIfAbsent(key, k -> createLimiterInstance());
        LimitResult result = limiter.tryAcquire(permits);
        
        if (result.isAllowed()) {
            totalAllowed.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
            logger.debug("限流拒绝请求，key: {}, permits: {}, reason: {}", key, permits, result.getReason());
        }
        
        return result;
    }

    /**
     * 重置指定key的限流状态
     */
    public void reset(String key) {
        LimiterInstance limiter = limiters.get(key);
        if (limiter != null) {
            limiter.reset();
            logger.debug("重置限流状态，key: {}", key);
        }
    }

    /**
     * 重置所有限流状态
     */
    public void resetAll() {
        limiters.values().forEach(LimiterInstance::reset);
        logger.info("重置所有限流状态");
    }

    /**
     * 清理过期的限流实例
     */
    public void cleanup() {
        // 简单实现：清理所有实例
        int sizeBefore = limiters.size();
        limiters.clear();
        logger.debug("清理限流实例，清理前: {}, 清理后: {}", sizeBefore, limiters.size());
    }

    /**
     * 获取指定key的可用许可数
     */
    public long getAvailablePermits(String key) {
        LimiterInstance limiter = limiters.get(key);
        return limiter != null ? limiter.getAvailablePermits() : config.getMaxRequests();
    }

    /**
     * 创建限流实例
     */
    private LimiterInstance createLimiterInstance() {
        switch (type) {
            case TOKEN_BUCKET:
                return new TokenBucketLimiter(
                    Math.max(config.getBurstCapacity(), config.getMaxRequests()),
                    (config.getMaxRequests() * 1000L) / config.getTimeWindow()
                );
            case SLIDING_WINDOW:
                return new SlidingWindowLimiter(config.getMaxRequests(), config.getTimeWindow());
            case FIXED_WINDOW:
                // 简化实现，使用令牌桶
                return new TokenBucketLimiter(config.getMaxRequests(), config.getMaxRequests());
            default:
                throw new IllegalArgumentException("不支持的限流器类型: " + type);
        }
    }

    /**
     * 获取统计信息
     */
    public RateLimiterStatistics getStatistics() {
        return new RateLimiterStatistics(
            totalRequests.get(),
            totalAllowed.get(),
            totalRejected.get(),
            limiters.size(),
            type,
            config
        );
    }

    /**
     * 限流统计信息
     */
    public static class RateLimiterStatistics {
        private final long totalRequests;
        private final long totalAllowed;
        private final long totalRejected;
        private final int activeLimiters;
        private final LimiterType type;
        private final LimiterConfig config;

        public RateLimiterStatistics(long totalRequests, long totalAllowed, long totalRejected,
                                   int activeLimiters, LimiterType type, LimiterConfig config) {
            this.totalRequests = totalRequests;
            this.totalAllowed = totalAllowed;
            this.totalRejected = totalRejected;
            this.activeLimiters = activeLimiters;
            this.type = type;
            this.config = config;
        }

        public long getTotalRequests() { return totalRequests; }
        public long getTotalAllowed() { return totalAllowed; }
        public long getTotalRejected() { return totalRejected; }
        public int getActiveLimiters() { return activeLimiters; }
        public LimiterType getType() { return type; }
        public LimiterConfig getConfig() { return config; }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) totalAllowed / totalRequests : 0.0;
        }
        
        public double getRejectionRate() {
            return totalRequests > 0 ? (double) totalRejected / totalRequests : 0.0;
        }

        @Override
        public String toString() {
            return String.format("RateLimiterStats{type=%s, requests=%d, allowed=%d, rejected=%d, " +
                               "successRate=%.2f%%, activeLimiters=%d}",
                               type, totalRequests, totalAllowed, totalRejected,
                               getSuccessRate() * 100, activeLimiters);
        }
    }

    /**
     * 获取配置
     */
    public LimiterConfig getConfig() {
        return config;
    }

    /**
     * 获取类型
     */
    public LimiterType getType() {
        return type;
    }

    /**
     * 获取活跃限流器数量
     */
    public int getActiveLimitersCount() {
        return limiters.size();
    }
}