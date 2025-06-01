/*
 * 文件名: ByteBufPool.java
 * 用途: ByteBuf内存池管理
 * 实现内容:
 *   - 内存池化ByteBuf管理
 *   - 内存泄漏检测和防护
 *   - 统计监控和性能优化
 *   - 自动调整和内存回收
 *   - 多线程安全的池化操作
 * 技术选型:
 *   - Netty ByteBuf池化
 *   - 内存泄漏检测机制
 *   - 线程安全的对象池
 * 依赖关系:
 *   - 被NettyConnection使用
 *   - 为编解码器提供内存管理
 *   - 支持内存使用监控
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ByteBuf内存池管理器
 * <p>
 * 提供高效的ByteBuf内存管理，支持池化复用、内存泄漏检测、
 * 统计监控等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ByteBufPool {

    private static final Logger logger = LoggerFactory.getLogger(ByteBufPool.class);

    // 默认配置
    private static final int DEFAULT_INITIAL_CAPACITY = 256;
    private static final int DEFAULT_MAX_CAPACITY = 1024 * 1024; // 1MB
    
    // 内存分配器
    private final ByteBufAllocator allocator;
    private final boolean directMemory;
    private final boolean pooled;
    
    // 内存泄漏检测
    private final ConcurrentHashMap<ByteBuf, AllocationInfo> allocatedBuffers = new ConcurrentHashMap<>();
    private final boolean leakDetectionEnabled;
    
    // 统计信息
    private final AtomicLong totalAllocated = new AtomicLong(0);
    private final AtomicLong totalDeallocated = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong peakBytes = new AtomicLong(0);
    private final AtomicLong leakCount = new AtomicLong(0);

    /**
     * 分配信息
     */
    private static class AllocationInfo {
        private final long timestamp;
        private final int capacity;
        private final StackTraceElement[] stackTrace;
        
        public AllocationInfo(int capacity, boolean captureStack) {
            this.timestamp = System.currentTimeMillis();
            this.capacity = capacity;
            this.stackTrace = captureStack ? Thread.currentThread().getStackTrace() : null;
        }
        
        public long getTimestamp() { return timestamp; }
        public int getCapacity() { return capacity; }
        public StackTraceElement[] getStackTrace() { return stackTrace; }
        public long getAge() { return System.currentTimeMillis() - timestamp; }
    }

    /**
     * 池配置
     */
    public static class PoolConfig {
        private final boolean directMemory;
        private final boolean pooled;
        private final boolean leakDetection;
        private final int maxCachedBufferCapacity;
        private final int maxCachedBuffers;
        
        public PoolConfig(boolean directMemory, boolean pooled, boolean leakDetection,
                         int maxCachedBufferCapacity, int maxCachedBuffers) {
            this.directMemory = directMemory;
            this.pooled = pooled;
            this.leakDetection = leakDetection;
            this.maxCachedBufferCapacity = maxCachedBufferCapacity;
            this.maxCachedBuffers = maxCachedBuffers;
        }
        
        public boolean isDirectMemory() { return directMemory; }
        public boolean isPooled() { return pooled; }
        public boolean isLeakDetection() { return leakDetection; }
        public int getMaxCachedBufferCapacity() { return maxCachedBufferCapacity; }
        public int getMaxCachedBuffers() { return maxCachedBuffers; }
        
        public static PoolConfig defaultConfig() {
            return new PoolConfig(true, true, true, 1024 * 1024, 1024);
        }
    }

    /**
     * 构造函数
     */
    public ByteBufPool(PoolConfig config) {
        this.directMemory = config.isDirectMemory();
        this.pooled = config.isPooled();
        this.leakDetectionEnabled = config.isLeakDetection();
        
        if (pooled) {
            // 使用池化分配器
            this.allocator = new PooledByteBufAllocator(
                directMemory,
                Math.min(Runtime.getRuntime().availableProcessors(), 32), // nHeapArena
                Math.min(Runtime.getRuntime().availableProcessors(), 32), // nDirectArena
                8192,  // pageSize
                11,    // maxOrder
                256,   // tinyCacheSize
                512,   // smallCacheSize
                64,    // normalCacheSize
                true   // useCacheForAllThreads
            );
        } else {
            // 使用非池化分配器
            this.allocator = new UnpooledByteBufAllocator(directMemory);
        }
        
        logger.info("ByteBuf池初始化完成，直接内存: {}, 池化: {}, 泄漏检测: {}",
                   directMemory, pooled, leakDetectionEnabled);
    }

    /**
     * 分配ByteBuf
     *
     * @param initialCapacity 初始容量
     * @return ByteBuf实例
     */
    public ByteBuf allocate(int initialCapacity) {
        return allocate(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    /**
     * 分配ByteBuf
     *
     * @param initialCapacity 初始容量
     * @param maxCapacity     最大容量
     * @return ByteBuf实例
     */
    public ByteBuf allocate(int initialCapacity, int maxCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("初始容量不能为负数: " + initialCapacity);
        }
        
        if (maxCapacity < initialCapacity) {
            throw new IllegalArgumentException("最大容量不能小于初始容量: " + maxCapacity + " < " + initialCapacity);
        }
        
        ByteBuf buffer;
        if (directMemory) {
            buffer = allocator.directBuffer(initialCapacity, maxCapacity);
        } else {
            buffer = allocator.heapBuffer(initialCapacity, maxCapacity);
        }
        
        // 更新统计信息
        totalAllocated.incrementAndGet();
        long currentBytes = totalBytes.addAndGet(buffer.capacity());
        peakBytes.updateAndGet(peak -> Math.max(peak, currentBytes));
        
        // 内存泄漏检测
        if (leakDetectionEnabled) {
            allocatedBuffers.put(buffer, new AllocationInfo(buffer.capacity(), true));
        }
        
        logger.debug("分配ByteBuf，容量: {}/{}, 直接内存: {}", 
                    initialCapacity, maxCapacity, buffer.isDirect());
        
        return buffer;
    }

    /**
     * 释放ByteBuf
     *
     * @param buffer 要释放的ByteBuf
     */
    public void release(ByteBuf buffer) {
        if (buffer == null) {
            return;
        }
        
        // 检查是否已经释放
        if (buffer.refCnt() <= 0) {
            logger.warn("尝试释放已经释放的ByteBuf");
            return;
        }
        
        // 移除泄漏检测记录
        if (leakDetectionEnabled) {
            AllocationInfo info = allocatedBuffers.remove(buffer);
            if (info != null) {
                totalBytes.addAndGet(-info.getCapacity());
                totalDeallocated.incrementAndGet();
            }
        } else {
            totalBytes.addAndGet(-buffer.capacity());
            totalDeallocated.incrementAndGet();
        }
        
        // 释放缓冲区
        try {
            ReferenceCountUtil.release(buffer);
            logger.debug("释放ByteBuf，容量: {}", buffer.capacity());
        } catch (Exception e) {
            logger.warn("释放ByteBuf失败", e);
        }
    }

    /**
     * 安全释放ByteBuf
     *
     * @param buffer 要释放的ByteBuf
     * @return true表示成功释放
     */
    public boolean safeRelease(ByteBuf buffer) {
        if (buffer == null) {
            return true;
        }
        
        try {
            release(buffer);
            return true;
        } catch (Exception e) {
            logger.warn("安全释放ByteBuf失败", e);
            return false;
        }
    }

    /**
     * 复制ByteBuf
     *
     * @param source 源ByteBuf
     * @return 复制的ByteBuf
     */
    public ByteBuf copy(ByteBuf source) {
        if (source == null) {
            return null;
        }
        
        ByteBuf copy = allocate(source.readableBytes());
        copy.writeBytes(source, source.readerIndex(), source.readableBytes());
        return copy;
    }

    /**
     * 检查内存泄漏
     *
     * @param maxAge 最大存活时间（毫秒）
     * @return 泄漏的缓冲区数量
     */
    public int checkLeaks(long maxAge) {
        if (!leakDetectionEnabled) {
            return 0;
        }
        
        int leakCount = 0;
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<ByteBuf, AllocationInfo> entry : allocatedBuffers.entrySet()) {
            ByteBuf buffer = entry.getKey();
            AllocationInfo info = entry.getValue();
            
            if (currentTime - info.getTimestamp() > maxAge) {
                leakCount++;
                this.leakCount.incrementAndGet();
                
                logger.warn("检测到内存泄漏，ByteBuf容量: {}, 存活时间: {}ms, 引用计数: {}",
                           buffer.capacity(), info.getAge(), buffer.refCnt());
                
                // 打印分配栈跟踪
                if (info.getStackTrace() != null) {
                    StringBuilder sb = new StringBuilder("分配栈跟踪:\n");
                    for (StackTraceElement element : info.getStackTrace()) {
                        sb.append("  at ").append(element.toString()).append("\n");
                    }
                    logger.warn(sb.toString());
                }
            }
        }
        
        return leakCount;
    }

    /**
     * 清理所有泄漏的缓冲区
     */
    public void cleanupLeaks() {
        if (!leakDetectionEnabled) {
            return;
        }
        
        int cleaned = 0;
        for (Map.Entry<ByteBuf, AllocationInfo> entry : allocatedBuffers.entrySet()) {
            ByteBuf buffer = entry.getKey();
            if (buffer.refCnt() > 0) {
                try {
                    ReferenceCountUtil.release(buffer);
                    cleaned++;
                } catch (Exception e) {
                    logger.warn("清理泄漏缓冲区失败", e);
                }
            }
        }
        
        allocatedBuffers.clear();
        logger.info("清理泄漏缓冲区完成，清理数量: {}", cleaned);
    }

    /**
     * 获取统计信息
     */
    public PoolStatistics getStatistics() {
        return new PoolStatistics(
            totalAllocated.get(),
            totalDeallocated.get(),
            totalBytes.get(),
            peakBytes.get(),
            allocatedBuffers.size(),
            leakCount.get(),
            directMemory,
            pooled,
            leakDetectionEnabled
        );
    }

    /**
     * 内存池统计信息
     */
    public static class PoolStatistics {
        private final long totalAllocated;
        private final long totalDeallocated;
        private final long currentBytes;
        private final long peakBytes;
        private final int activeBuffers;
        private final long leakCount;
        private final boolean directMemory;
        private final boolean pooled;
        private final boolean leakDetection;

        public PoolStatistics(long totalAllocated, long totalDeallocated, long currentBytes,
                            long peakBytes, int activeBuffers, long leakCount,
                            boolean directMemory, boolean pooled, boolean leakDetection) {
            this.totalAllocated = totalAllocated;
            this.totalDeallocated = totalDeallocated;
            this.currentBytes = currentBytes;
            this.peakBytes = peakBytes;
            this.activeBuffers = activeBuffers;
            this.leakCount = leakCount;
            this.directMemory = directMemory;
            this.pooled = pooled;
            this.leakDetection = leakDetection;
        }

        public long getTotalAllocated() { return totalAllocated; }
        public long getTotalDeallocated() { return totalDeallocated; }
        public long getCurrentBytes() { return currentBytes; }
        public long getPeakBytes() { return peakBytes; }
        public int getActiveBuffers() { return activeBuffers; }
        public long getLeakCount() { return leakCount; }
        public boolean isDirectMemory() { return directMemory; }
        public boolean isPooled() { return pooled; }
        public boolean isLeakDetection() { return leakDetection; }
        
        public long getActiveAllocations() { return totalAllocated - totalDeallocated; }
        
        public double getLeakRate() {
            return totalAllocated > 0 ? (double) leakCount / totalAllocated : 0.0;
        }

        @Override
        public String toString() {
            return String.format("PoolStats{allocated=%d, deallocated=%d, active=%d, " +
                               "currentBytes=%d, peakBytes=%d, leaks=%d(%.2f%%), " +
                               "direct=%s, pooled=%s, leakDetection=%s}",
                               totalAllocated, totalDeallocated, getActiveAllocations(),
                               currentBytes, peakBytes, leakCount, getLeakRate() * 100,
                               directMemory, pooled, leakDetection);
        }
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalAllocated.set(0);
        totalDeallocated.set(0);
        totalBytes.set(0);
        peakBytes.set(0);
        leakCount.set(0);
        logger.debug("ByteBuf池统计信息已重置");
    }

    /**
     * 获取分配器
     */
    public ByteBufAllocator getAllocator() {
        return allocator;
    }

    /**
     * 是否使用直接内存
     */
    public boolean isDirectMemory() {
        return directMemory;
    }

    /**
     * 是否启用池化
     */
    public boolean isPooled() {
        return pooled;
    }

    /**
     * 是否启用泄漏检测
     */
    public boolean isLeakDetectionEnabled() {
        return leakDetectionEnabled;
    }

    /**
     * 创建默认内存池
     */
    public static ByteBufPool createDefault() {
        return new ByteBufPool(PoolConfig.defaultConfig());
    }

    /**
     * 创建高性能内存池（直接内存+池化）
     */
    public static ByteBufPool createHighPerformance() {
        return new ByteBufPool(new PoolConfig(true, true, false, 1024 * 1024, 2048));
    }

    /**
     * 创建调试内存池（启用泄漏检测）
     */
    public static ByteBufPool createDebug() {
        return new ByteBufPool(new PoolConfig(false, false, true, 512 * 1024, 512));
    }
}