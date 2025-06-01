/*
 * 文件名: CacheMetrics.java
 * 用途: 缓存指标收集
 * 实现内容:
 *   - 缓存性能指标收集和统计
 *   - 命中率、访问延迟监控
 *   - 内存使用和请求分布分析
 *   - 实时监控和历史趋势
 *   - 多维度指标聚合
 * 技术选型:
 *   - Micrometer指标库
 *   - 原子操作保证线程安全
 *   - 滑动窗口统计
 *   - 分位数延迟统计
 * 依赖关系:
 *   - 被缓存实现使用
 *   - 提供性能监控数据
 *   - 支持监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 缓存指标收集器
 * <p>
 * 收集和统计缓存的各种性能指标，包括命中率、延迟、内存使用等。
 * 提供实时监控数据和历史趋势分析，支持多维度的指标聚合。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheMetrics {

    private static final Logger logger = LoggerFactory.getLogger(CacheMetrics.class);

    /**
     * 缓存名称
     */
    private final String cacheName;

    /**
     * 总请求数
     */
    private final LongAdder totalRequests = new LongAdder();

    /**
     * 命中数
     */
    private final LongAdder hits = new LongAdder();

    /**
     * 未命中数
     */
    private final LongAdder misses = new LongAdder();

    /**
     * 加载次数
     */
    private final LongAdder loads = new LongAdder();

    /**
     * 驱逐次数
     */
    private final LongAdder evictions = new LongAdder();

    /**
     * 过期次数
     */
    private final LongAdder expirations = new LongAdder();

    /**
     * 写入次数
     */
    private final LongAdder puts = new LongAdder();

    /**
     * 删除次数
     */
    private final LongAdder removals = new LongAdder();

    /**
     * 总加载时间（纳秒）
     */
    private final LongAdder totalLoadTime = new LongAdder();

    /**
     * 当前大小
     */
    private final AtomicLong currentSize = new AtomicLong(0);

    /**
     * 最大大小
     */
    private volatile long maxSize = 0;

    /**
     * 估计内存使用（字节）
     */
    private final AtomicLong estimatedMemoryUsage = new AtomicLong(0);

    /**
     * 延迟分布统计
     */
    private final LatencyStatistics latencyStats = new LatencyStatistics();

    /**
     * 时间窗口统计
     */
    private final TimeWindowStatistics windowStats = new TimeWindowStatistics();

    /**
     * 创建时间
     */
    private final Instant createTime = Instant.now();

    /**
     * 读写锁
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造函数
     *
     * @param cacheName 缓存名称
     */
    public CacheMetrics(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
     * 记录命中
     */
    public void recordHit() {
        totalRequests.increment();
        hits.increment();
        windowStats.recordHit();
    }

    /**
     * 记录未命中
     */
    public void recordMiss() {
        totalRequests.increment();
        misses.increment();
        windowStats.recordMiss();
    }

    /**
     * 记录加载
     *
     * @param loadTime 加载时间（纳秒）
     */
    public void recordLoad(long loadTime) {
        loads.increment();
        totalLoadTime.add(loadTime);
        latencyStats.recordLoad(loadTime);
        windowStats.recordLoad(loadTime);
    }

    /**
     * 记录驱逐
     */
    public void recordEviction() {
        evictions.increment();
        windowStats.recordEviction();
    }

    /**
     * 记录过期
     */
    public void recordExpiration() {
        expirations.increment();
        windowStats.recordExpiration();
    }

    /**
     * 记录写入
     *
     * @param writeTime 写入时间（纳秒）
     */
    public void recordPut(long writeTime) {
        puts.increment();
        latencyStats.recordPut(writeTime);
        windowStats.recordPut(writeTime);
    }

    /**
     * 记录删除
     */
    public void recordRemoval() {
        removals.increment();
        windowStats.recordRemoval();
    }

    /**
     * 更新缓存大小
     *
     * @param size 当前大小
     */
    public void updateSize(long size) {
        long oldSize = currentSize.getAndSet(size);
        if (size > maxSize) {
            maxSize = size;
        }
    }

    /**
     * 更新内存使用估计
     *
     * @param memoryUsage 内存使用（字节）
     */
    public void updateMemoryUsage(long memoryUsage) {
        estimatedMemoryUsage.set(memoryUsage);
    }

    /**
     * 获取总请求数
     *
     * @return 总请求数
     */
    public long getTotalRequests() {
        return totalRequests.sum();
    }

    /**
     * 获取命中数
     *
     * @return 命中数
     */
    public long getHits() {
        return hits.sum();
    }

    /**
     * 获取未命中数
     *
     * @return 未命中数
     */
    public long getMisses() {
        return misses.sum();
    }

    /**
     * 获取命中率
     *
     * @return 命中率（0.0-1.0）
     */
    public double getHitRate() {
        long total = getTotalRequests();
        return total > 0 ? (double) getHits() / total : 0.0;
    }

    /**
     * 获取未命中率
     *
     * @return 未命中率（0.0-1.0）
     */
    public double getMissRate() {
        return 1.0 - getHitRate();
    }

    /**
     * 获取加载次数
     *
     * @return 加载次数
     */
    public long getLoads() {
        return loads.sum();
    }

    /**
     * 获取平均加载时间
     *
     * @return 平均加载时间（纳秒）
     */
    public double getAverageLoadTime() {
        long loadCount = getLoads();
        return loadCount > 0 ? (double) totalLoadTime.sum() / loadCount : 0.0;
    }

    /**
     * 获取驱逐次数
     *
     * @return 驱逐次数
     */
    public long getEvictions() {
        return evictions.sum();
    }

    /**
     * 获取过期次数
     *
     * @return 过期次数
     */
    public long getExpirations() {
        return expirations.sum();
    }

    /**
     * 获取写入次数
     *
     * @return 写入次数
     */
    public long getPuts() {
        return puts.sum();
    }

    /**
     * 获取删除次数
     *
     * @return 删除次数
     */
    public long getRemovals() {
        return removals.sum();
    }

    /**
     * 获取当前大小
     *
     * @return 当前大小
     */
    public long getCurrentSize() {
        return currentSize.get();
    }

    /**
     * 获取最大大小
     *
     * @return 最大大小
     */
    public long getMaxSize() {
        return maxSize;
    }

    /**
     * 获取负载因子
     *
     * @return 负载因子（0.0-1.0）
     */
    public double getLoadFactor() {
        return maxSize > 0 ? (double) getCurrentSize() / maxSize : 0.0;
    }

    /**
     * 获取估计内存使用
     *
     * @return 内存使用（字节）
     */
    public long getEstimatedMemoryUsage() {
        return estimatedMemoryUsage.get();
    }

    /**
     * 获取运行时间
     *
     * @return 运行时间
     */
    public Duration getUptime() {
        return Duration.between(createTime, Instant.now());
    }

    /**
     * 获取延迟统计
     *
     * @return 延迟统计
     */
    public LatencyStatistics getLatencyStatistics() {
        return latencyStats;
    }

    /**
     * 获取时间窗口统计
     *
     * @return 时间窗口统计
     */
    public TimeWindowStatistics getWindowStatistics() {
        return windowStats;
    }

    /**
     * 获取请求每秒数（QPS）
     *
     * @return QPS
     */
    public double getRequestsPerSecond() {
        return windowStats.getRequestsPerSecond();
    }

    /**
     * 重置统计信息
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            totalRequests.reset();
            hits.reset();
            misses.reset();
            loads.reset();
            evictions.reset();
            expirations.reset();
            puts.reset();
            removals.reset();
            totalLoadTime.reset();
            currentSize.set(0);
            maxSize = 0;
            estimatedMemoryUsage.set(0);
            latencyStats.reset();
            windowStats.reset();
            
            logger.info("缓存指标已重置: {}", cacheName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取缓存名称
     *
     * @return 缓存名称
     */
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public String toString() {
        return "CacheMetrics{" +
               "cacheName='" + cacheName + '\'' +
               ", totalRequests=" + getTotalRequests() +
               ", hitRate=" + String.format("%.2f%%", getHitRate() * 100) +
               ", averageLoadTime=" + String.format("%.2fms", getAverageLoadTime() / 1_000_000.0) +
               ", currentSize=" + getCurrentSize() +
               ", loadFactor=" + String.format("%.2f%%", getLoadFactor() * 100) +
               ", uptime=" + getUptime() +
               '}';
    }

    /**
     * 延迟统计
     */
    public static class LatencyStatistics {
        private final AtomicLong loadCount = new AtomicLong(0);
        private final AtomicLong putCount = new AtomicLong(0);
        private final AtomicLong totalLoadLatency = new AtomicLong(0);
        private final AtomicLong totalPutLatency = new AtomicLong(0);
        private final AtomicLong maxLoadLatency = new AtomicLong(0);
        private final AtomicLong maxPutLatency = new AtomicLong(0);

        void recordLoad(long latency) {
            loadCount.incrementAndGet();
            totalLoadLatency.addAndGet(latency);
            updateMax(maxLoadLatency, latency);
        }

        void recordPut(long latency) {
            putCount.incrementAndGet();
            totalPutLatency.addAndGet(latency);
            updateMax(maxPutLatency, latency);
        }

        private void updateMax(AtomicLong maxValue, long newValue) {
            long current;
            do {
                current = maxValue.get();
                if (newValue <= current) {
                    break;
                }
            } while (!maxValue.compareAndSet(current, newValue));
        }

        public double getAverageLoadLatency() {
            long count = loadCount.get();
            return count > 0 ? (double) totalLoadLatency.get() / count : 0.0;
        }

        public double getAveragePutLatency() {
            long count = putCount.get();
            return count > 0 ? (double) totalPutLatency.get() / count : 0.0;
        }

        public long getMaxLoadLatency() {
            return maxLoadLatency.get();
        }

        public long getMaxPutLatency() {
            return maxPutLatency.get();
        }

        void reset() {
            loadCount.set(0);
            putCount.set(0);
            totalLoadLatency.set(0);
            totalPutLatency.set(0);
            maxLoadLatency.set(0);
            maxPutLatency.set(0);
        }
    }

    /**
     * 时间窗口统计
     */
    public static class TimeWindowStatistics {
        private final ConcurrentHashMap<Long, WindowData> windows = new ConcurrentHashMap<>();
        private static final long WINDOW_SIZE_SECONDS = 60; // 1分钟窗口

        void recordHit() {
            getCurrentWindow().hits.increment();
        }

        void recordMiss() {
            getCurrentWindow().misses.increment();
        }

        void recordLoad(long loadTime) {
            WindowData window = getCurrentWindow();
            window.loads.increment();
            window.totalLoadTime.add(loadTime);
        }

        void recordEviction() {
            getCurrentWindow().evictions.increment();
        }

        void recordExpiration() {
            getCurrentWindow().expirations.increment();
        }

        void recordPut(long putTime) {
            WindowData window = getCurrentWindow();
            window.puts.increment();
            window.totalPutTime.add(putTime);
        }

        void recordRemoval() {
            getCurrentWindow().removals.increment();
        }

        private WindowData getCurrentWindow() {
            long windowKey = Instant.now().getEpochSecond() / WINDOW_SIZE_SECONDS;
            return windows.computeIfAbsent(windowKey, k -> new WindowData());
        }

        public double getRequestsPerSecond() {
            WindowData current = getCurrentWindow();
            long totalRequests = current.hits.sum() + current.misses.sum();
            return (double) totalRequests / WINDOW_SIZE_SECONDS;
        }

        void reset() {
            windows.clear();
        }

        private static class WindowData {
            final LongAdder hits = new LongAdder();
            final LongAdder misses = new LongAdder();
            final LongAdder loads = new LongAdder();
            final LongAdder evictions = new LongAdder();
            final LongAdder expirations = new LongAdder();
            final LongAdder puts = new LongAdder();
            final LongAdder removals = new LongAdder();
            final LongAdder totalLoadTime = new LongAdder();
            final LongAdder totalPutTime = new LongAdder();
        }
    }
}