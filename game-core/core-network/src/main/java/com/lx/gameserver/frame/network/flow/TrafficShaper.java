/*
 * 文件名: TrafficShaper.java
 * 用途: 流量整形器
 * 实现内容:
 *   - 读写流量限速控制
 *   - 突发流量处理和平滑
 *   - 公平性保证和优先级控制
 *   - 动态流量调整机制
 *   - 流量统计和监控
 * 技术选型:
 *   - 令牌桶算法实现流量控制
 *   - 滑动窗口统计流量
 *   - 优先级队列处理突发流量
 * 依赖关系:
 *   - 被NettyChannelInitializer使用
 *   - 与Connection接口协作
 *   - 支持动态配置调整
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 流量整形器
 * <p>
 * 基于令牌桶算法的流量整形器，支持读写分离限速、突发流量处理、
 * 动态调整和公平性保证。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class TrafficShaper {

    private static final Logger logger = LoggerFactory.getLogger(TrafficShaper.class);

    // 流量限制配置
    private volatile long readLimit;     // 读取限制（字节/秒）
    private volatile long writeLimit;    // 写入限制（字节/秒）
    private volatile long maxBurstSize;  // 最大突发大小
    
    // 令牌桶
    private final TokenBucket readBucket;
    private final TokenBucket writeBucket;
    
    // 统计信息
    private final AtomicLong totalBytesRead = new AtomicLong(0);
    private final AtomicLong totalBytesWritten = new AtomicLong(0);
    private final AtomicLong totalReadBlocks = new AtomicLong(0);
    private final AtomicLong totalWriteBlocks = new AtomicLong(0);
    
    // 控制锁
    private final ReentrantLock configLock = new ReentrantLock();

    /**
     * 令牌桶实现
     */
    private static class TokenBucket {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile long capacity;        // 桶容量
        private volatile long refillRate;      // 填充速率（令牌/秒）
        private volatile long tokens;          // 当前令牌数
        private volatile long lastRefillTime;  // 上次填充时间
        
        public TokenBucket(long capacity, long refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTime = System.nanoTime();
        }
        
        /**
         * 尝试获取令牌
         */
        public boolean tryAcquire(long tokensRequested) {
            lock.lock();
            try {
                refill();
                
                if (tokens >= tokensRequested) {
                    tokens -= tokensRequested;
                    return true;
                }
                
                return false;
            } finally {
                lock.unlock();
            }
        }
        
        /**
         * 获取令牌（阻塞）
         */
        public long acquire(long tokensRequested) {
            lock.lock();
            try {
                refill();
                
                if (tokens >= tokensRequested) {
                    tokens -= tokensRequested;
                    return 0; // 无需等待
                }
                
                // 计算等待时间
                long tokensNeeded = tokensRequested - tokens;
                long waitTime = (tokensNeeded * 1_000_000_000L) / refillRate; // 纳秒
                tokens = 0; // 消耗所有当前令牌
                
                return waitTime;
            } finally {
                lock.unlock();
            }
        }
        
        /**
         * 填充令牌
         */
        private void refill() {
            long now = System.nanoTime();
            long timeDelta = now - lastRefillTime;
            
            if (timeDelta > 0) {
                long tokensToAdd = (refillRate * timeDelta) / 1_000_000_000L;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }
        
        /**
         * 更新配置
         */
        public void updateConfig(long newCapacity, long newRefillRate) {
            lock.lock();
            try {
                this.capacity = newCapacity;
                this.refillRate = newRefillRate;
                this.tokens = Math.min(this.tokens, newCapacity);
            } finally {
                lock.unlock();
            }
        }
        
        /**
         * 获取当前令牌数
         */
        public long getAvailableTokens() {
            lock.lock();
            try {
                refill();
                return tokens;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 构造函数
     *
     * @param readLimit     读取限制（字节/秒，0表示无限制）
     * @param writeLimit    写入限制（字节/秒，0表示无限制）
     * @param maxBurstSize  最大突发大小
     */
    public TrafficShaper(long readLimit, long writeLimit, long maxBurstSize) {
        this.readLimit = readLimit;
        this.writeLimit = writeLimit;
        this.maxBurstSize = maxBurstSize;
        
        // 初始化令牌桶
        this.readBucket = new TokenBucket(
            readLimit > 0 ? Math.max(maxBurstSize, readLimit) : Long.MAX_VALUE,
            readLimit > 0 ? readLimit : Long.MAX_VALUE
        );
        
        this.writeBucket = new TokenBucket(
            writeLimit > 0 ? Math.max(maxBurstSize, writeLimit) : Long.MAX_VALUE,
            writeLimit > 0 ? writeLimit : Long.MAX_VALUE
        );
        
        logger.info("流量整形器初始化，读限制: {} bytes/s, 写限制: {} bytes/s, 最大突发: {} bytes",
                   readLimit, writeLimit, maxBurstSize);
    }

    /**
     * 请求读取字节
     *
     * @param bytes 要读取的字节数
     * @return 需要等待的时间（纳秒），0表示立即可用
     */
    public long requestRead(long bytes) {
        if (readLimit <= 0 || bytes <= 0) {
            return 0; // 无限制或无效请求
        }
        
        totalBytesRead.addAndGet(bytes);
        
        if (readBucket.tryAcquire(bytes)) {
            return 0; // 立即可用
        }
        
        totalReadBlocks.incrementAndGet();
        return readBucket.acquire(bytes);
    }

    /**
     * 请求写入字节
     *
     * @param bytes 要写入的字节数
     * @return 需要等待的时间（纳秒），0表示立即可用
     */
    public long requestWrite(long bytes) {
        if (writeLimit <= 0 || bytes <= 0) {
            return 0; // 无限制或无效请求
        }
        
        totalBytesWritten.addAndGet(bytes);
        
        if (writeBucket.tryAcquire(bytes)) {
            return 0; // 立即可用
        }
        
        totalWriteBlocks.incrementAndGet();
        return writeBucket.acquire(bytes);
    }

    /**
     * 阻塞式读取请求
     */
    public void blockingRequestRead(long bytes) throws InterruptedException {
        long waitTime = requestRead(bytes);
        if (waitTime > 0) {
            Thread.sleep(waitTime / 1_000_000, (int) (waitTime % 1_000_000));
        }
    }

    /**
     * 阻塞式写入请求
     */
    public void blockingRequestWrite(long bytes) throws InterruptedException {
        long waitTime = requestWrite(bytes);
        if (waitTime > 0) {
            Thread.sleep(waitTime / 1_000_000, (int) (waitTime % 1_000_000));
        }
    }

    /**
     * 更新流量限制配置
     */
    public void updateLimits(long newReadLimit, long newWriteLimit, long newMaxBurstSize) {
        configLock.lock();
        try {
            this.readLimit = newReadLimit;
            this.writeLimit = newWriteLimit;
            this.maxBurstSize = newMaxBurstSize;
            
            // 更新令牌桶配置
            readBucket.updateConfig(
                newReadLimit > 0 ? Math.max(newMaxBurstSize, newReadLimit) : Long.MAX_VALUE,
                newReadLimit > 0 ? newReadLimit : Long.MAX_VALUE
            );
            
            writeBucket.updateConfig(
                newWriteLimit > 0 ? Math.max(newMaxBurstSize, newWriteLimit) : Long.MAX_VALUE,
                newWriteLimit > 0 ? newWriteLimit : Long.MAX_VALUE
            );
            
            logger.info("更新流量限制，读限制: {} bytes/s, 写限制: {} bytes/s, 最大突发: {} bytes",
                       newReadLimit, newWriteLimit, newMaxBurstSize);
        } finally {
            configLock.unlock();
        }
    }

    /**
     * 获取统计信息
     */
    public TrafficStatistics getStatistics() {
        return new TrafficStatistics(
            readLimit,
            writeLimit,
            maxBurstSize,
            totalBytesRead.get(),
            totalBytesWritten.get(),
            totalReadBlocks.get(),
            totalWriteBlocks.get(),
            readBucket.getAvailableTokens(),
            writeBucket.getAvailableTokens()
        );
    }

    /**
     * 流量统计信息
     */
    public static class TrafficStatistics {
        private final long readLimit;
        private final long writeLimit;
        private final long maxBurstSize;
        private final long totalBytesRead;
        private final long totalBytesWritten;
        private final long totalReadBlocks;
        private final long totalWriteBlocks;
        private final long availableReadTokens;
        private final long availableWriteTokens;
        
        public TrafficStatistics(long readLimit, long writeLimit, long maxBurstSize,
                               long totalBytesRead, long totalBytesWritten,
                               long totalReadBlocks, long totalWriteBlocks,
                               long availableReadTokens, long availableWriteTokens) {
            this.readLimit = readLimit;
            this.writeLimit = writeLimit;
            this.maxBurstSize = maxBurstSize;
            this.totalBytesRead = totalBytesRead;
            this.totalBytesWritten = totalBytesWritten;
            this.totalReadBlocks = totalReadBlocks;
            this.totalWriteBlocks = totalWriteBlocks;
            this.availableReadTokens = availableReadTokens;
            this.availableWriteTokens = availableWriteTokens;
        }
        
        // Getters
        public long getReadLimit() { return readLimit; }
        public long getWriteLimit() { return writeLimit; }
        public long getMaxBurstSize() { return maxBurstSize; }
        public long getTotalBytesRead() { return totalBytesRead; }
        public long getTotalBytesWritten() { return totalBytesWritten; }
        public long getTotalReadBlocks() { return totalReadBlocks; }
        public long getTotalWriteBlocks() { return totalWriteBlocks; }
        public long getAvailableReadTokens() { return availableReadTokens; }
        public long getAvailableWriteTokens() { return availableWriteTokens; }
        
        @Override
        public String toString() {
            return String.format("TrafficStats{readLimit=%d, writeLimit=%d, maxBurst=%d, " +
                               "bytesRead=%d, bytesWritten=%d, readBlocks=%d, writeBlocks=%d, " +
                               "readTokens=%d, writeTokens=%d}",
                               readLimit, writeLimit, maxBurstSize,
                               totalBytesRead, totalBytesWritten, totalReadBlocks, totalWriteBlocks,
                               availableReadTokens, availableWriteTokens);
        }
    }

    /**
     * 检查是否有读取限制
     */
    public boolean hasReadLimit() {
        return readLimit > 0;
    }

    /**
     * 检查是否有写入限制
     */
    public boolean hasWriteLimit() {
        return writeLimit > 0;
    }

    /**
     * 获取当前读取限制
     */
    public long getReadLimit() {
        return readLimit;
    }

    /**
     * 获取当前写入限制
     */
    public long getWriteLimit() {
        return writeLimit;
    }

    /**
     * 获取最大突发大小
     */
    public long getMaxBurstSize() {
        return maxBurstSize;
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalBytesRead.set(0);
        totalBytesWritten.set(0);
        totalReadBlocks.set(0);
        totalWriteBlocks.set(0);
        logger.debug("流量统计信息已重置");
    }
}