/*
 * 文件名: RateLimiter.java
 * 用途: 限流控制器
 * 实现内容:
 *   - 令牌桶算法
 *   - 滑动窗口算法
 *   - 分布式限流
 *   - 多维度限流
 *   - 限流策略配置
 * 技术选型:
 *   - 本地内存计数
 *   - Redis分布式支持
 *   - 滑动窗口与令牌桶混合
 * 依赖关系:
 *   - 被安全过滤器使用
 *   - 被资源访问控制使用
 */
package com.lx.gameserver.frame.security.protection;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流控制器
 * <p>
 * 提供高性能、多模式的限流机制，支持令牌桶和滑动窗口
 * 算法，可以实现API限流、登录限流和针对特定用户或IP的限流。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class RateLimiter {

    /**
     * 默认限流参数
     */
    public static final int DEFAULT_CAPACITY = 100;  // 默认容量
    public static final int DEFAULT_RATE = 10;       // 默认速率 (每秒请求数)
    public static final int DEFAULT_WINDOW = 60;     // 默认窗口大小（秒）
    
    /**
     * 本地存储（令牌桶）
     * Key: 资源ID
     * Value: 令牌桶
     */
    private final Map<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();
    
    /**
     * 本地存储（滑动窗口）
     * Key: 资源ID
     * Value: 滑动窗口计数器
     */
    private final Map<String, SlidingWindow> slidingWindows = new ConcurrentHashMap<>();
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * Redis客户端（用于分布式限流，可选）
     */
    @Nullable
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 分布式限流键前缀
     */
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param redisTemplate Redis客户端
     */
    public RateLimiter(SecurityProperties securityProperties, @Nullable RedisTemplate<String, Object> redisTemplate) {
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
        
        // 初始化默认限流配置
        initDefaultLimits();
        
        // 启动令牌补充调度器
        scheduleTokenRefill();
        
        log.info("限流控制器初始化完成");
    }
    
    /**
     * 初始化默认限流配置
     */
    private void initDefaultLimits() {
        // API限流配置
        int apiQps = securityProperties.getRateLimit().getApiQps();
        tokenBuckets.put("api", new TokenBucket(apiQps * 2, apiQps));
        
        // 登录限流配置
        int loginQps = securityProperties.getRateLimit().getLoginQps();
        tokenBuckets.put("login", new TokenBucket(loginQps * 2, loginQps));
        
        // 注册限流
        tokenBuckets.put("register", new TokenBucket(5, 1));
        
        // 重置密码限流
        tokenBuckets.put("resetPassword", new TokenBucket(10, 2));
    }
    
    /**
     * 启动令牌补充调度器
     */
    private void scheduleTokenRefill() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TokenBucketRefiller");
            thread.setDaemon(true);
            return thread;
        });
        
        // 每秒执行一次令牌补充
        scheduler.scheduleAtFixedRate(this::refillAllBuckets, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * 补充所有令牌桶
     */
    private void refillAllBuckets() {
        try {
            tokenBuckets.values().forEach(TokenBucket::refill);
        } catch (Exception e) {
            log.error("补充令牌桶时出错", e);
        }
    }
    
    /**
     * 尝试获取令牌（不指定用户）
     *
     * @param key 资源键
     * @param limit 速率限制（每秒请求数）
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, int limit) {
        return tryAcquire(key, null, limit);
    }
    
    /**
     * 尝试获取令牌（指定用户）
     *
     * @param key 资源键
     * @param userId 用户标识（用户ID或IP）
     * @param limit 速率限制（每秒请求数）
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, @Nullable String userId, int limit) {
        String resourceKey = key;
        
        // 如果指定了用户，则限流更细粒度
        if (userId != null) {
            resourceKey = key + ":" + userId;
        }
        
        // 是否启用分布式限流
        boolean distributedEnabled = securityProperties.getRateLimit().isEnableDistributed();
        
        if (distributedEnabled && redisTemplate != null) {
            // 使用Redis实现分布式限流
            return tryAcquireDistributed(resourceKey, limit);
        } else {
            // 使用本地内存实现限流
            return tryAcquireLocal(resourceKey, limit);
        }
    }
    
    /**
     * 本地限流
     *
     * @param key 资源键
     * @param limit 速率限制
     * @return 是否获取成功
     */
    private boolean tryAcquireLocal(String key, int limit) {
        // 对不同类型的资源采用不同的限流算法
        if (key.startsWith("api") || key.startsWith("login")) {
            // API和登录使用令牌桶
            return getOrCreateTokenBucket(key, limit).tryAcquire();
        } else {
            // 其他资源使用滑动窗口
            return getOrCreateSlidingWindow(key, limit).tryAcquire();
        }
    }
    
    /**
     * 分布式限流
     *
     * @param key 资源键
     * @param limit 速率限制
     * @return 是否获取成功
     */
    private boolean tryAcquireDistributed(String key, int limit) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            long now = Instant.now().getEpochSecond();
            
            // 滑动窗口时间戳
            String windowKey = redisKey + ":" + now;
            
            // 增加当前窗口的计数
            Long currentCount = redisTemplate.opsForValue().increment(windowKey, 1);
            if (currentCount == 1) {
                // 设置窗口过期时间
                redisTemplate.expire(windowKey, 10, TimeUnit.SECONDS);
            }
            
            // 获取最近几个窗口的总计数
            long total = 0;
            for (int i = 0; i <= 2; i++) {
                String historyKey = redisKey + ":" + (now - i);
                Object count = redisTemplate.opsForValue().get(historyKey);
                if (count instanceof Number) {
                    total += ((Number) count).longValue();
                }
            }
            
            // 检查是否超过限制
            return total <= limit;
            
        } catch (Exception e) {
            log.error("分布式限流检查失败，降级为本地限流", e);
            return tryAcquireLocal(key, limit);
        }
    }
    
    /**
     * 获取或创建令牌桶
     *
     * @param key 资源键
     * @param rate 补充速率
     * @return 令牌桶实例
     */
    private TokenBucket getOrCreateTokenBucket(String key, int rate) {
        return tokenBuckets.computeIfAbsent(key, k -> new TokenBucket(rate * 2, rate));
    }
    
    /**
     * 获取或创建滑动窗口
     *
     * @param key 资源键
     * @param limit 限制次数
     * @return 滑动窗口实例
     */
    private SlidingWindow getOrCreateSlidingWindow(String key, int limit) {
        return slidingWindows.computeIfAbsent(key, k -> new SlidingWindow(limit, DEFAULT_WINDOW));
    }
    
    /**
     * 令牌桶实现
     */
    private static class TokenBucket {
        private final int capacity;    // 桶容量
        private final int refillRate;  // 补充速率（每秒）
        private double tokens;         // 当前令牌数量
        private long lastRefillTime;   // 上次补充时间
        
        /**
         * 构造函数
         *
         * @param capacity 桶容量
         * @param refillRate 补充速率
         */
        public TokenBucket(int capacity, int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }
        
        /**
         * 补充令牌
         */
        public synchronized void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;
            
            // 计算新增令牌
            double newTokens = (timePassed / 1000.0) * refillRate;
            if (newTokens > 0) {
                tokens = Math.min(capacity, tokens + newTokens);
                lastRefillTime = now;
            }
        }
        
        /**
         * 尝试获取令牌
         *
         * @return 是否获取成功
         */
        public synchronized boolean tryAcquire() {
            refill(); // 先补充
            
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            } else {
                return false;
            }
        }
    }
    
    /**
     * 滑动窗口实现
     */
    private static class SlidingWindow {
        private final int limit;           // 窗口内允许的最大请求数
        private final int windowSeconds;   // 窗口大小（秒）
        
        // 存储每秒请求次数的数组，用作环形缓冲区
        private final ConcurrentHashMap<Long, AtomicInteger> hitCounts = new ConcurrentHashMap<>();
        
        /**
         * 构造函数
         *
         * @param limit 限流阈值
         * @param windowSeconds 窗口大小
         */
        public SlidingWindow(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
        
        /**
         * 尝试获取访问权限
         *
         * @return 是否允许访问
         */
        public boolean tryAcquire() {
            long now = Instant.now().getEpochSecond();
            
            // 清除过期的计数
            cleanupExpiredCounts(now);
            
            // 获取窗口内的总请求数
            int totalHits = getTotalHits(now);
            
            // 检查是否超过限制
            if (totalHits >= limit) {
                return false;
            }
            
            // 增加当前秒的计数
            hitCounts.computeIfAbsent(now, k -> new AtomicInteger(0)).incrementAndGet();
            
            return true;
        }
        
        /**
         * 获取窗口内的总请求数
         *
         * @param now 当前时间（秒）
         * @return 总请求数
         */
        private int getTotalHits(long now) {
            int total = 0;
            
            for (long i = now - windowSeconds + 1; i <= now; i++) {
                AtomicInteger hits = hitCounts.get(i);
                if (hits != null) {
                    total += hits.get();
                }
            }
            
            return total;
        }
        
        /**
         * 清除过期的计数
         *
         * @param now 当前时间（秒）
         */
        private void cleanupExpiredCounts(long now) {
            long expireTime = now - windowSeconds;
            hitCounts.keySet().removeIf(k -> k <= expireTime);
        }
    }
}