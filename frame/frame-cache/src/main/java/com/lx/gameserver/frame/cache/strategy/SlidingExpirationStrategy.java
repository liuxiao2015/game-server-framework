/*
 * 文件名: SlidingExpirationStrategy.java
 * 用途: 滑动过期策略实现
 * 实现内容:
 *   - 滑动时间过期策略
 *   - 基于最后访问时间的过期计算
 *   - 访问时间动态更新
 *   - 自动续期机制
 *   - 访问模式优化
 * 技术选型:
 *   - 基于Instant和Duration的时间计算
 *   - ConcurrentHashMap跟踪访问时间
 *   - 原子操作保证线程安全
 *   - 高效的时间更新机制
 * 依赖关系:
 *   - 实现ExpirationStrategy接口
 *   - 被缓存实现使用
 *   - 提供滑动过期逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 滑动过期策略实现
 * <p>
 * 基于滑动时间的过期策略，条目在最后访问后的固定时间后过期。
 * 每次访问都会重置过期时间。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class SlidingExpirationStrategy<K, V> implements ExpirationStrategy<K, V> {

    /**
     * 默认TTL
     */
    private final Duration defaultTtl;

    /**
     * 清理间隔
     */
    private final Duration cleanupInterval;

    /**
     * 最后访问时间记录
     */
    private final Map<K, Instant> lastAccessTimes = new ConcurrentHashMap<>();

    /**
     * 统计信息
     */
    private final SlidingExpirationStatistics statistics = new SlidingExpirationStatistics();

    /**
     * 上次清理时间
     */
    private volatile Instant lastCleanupTime = Instant.now();

    /**
     * 默认构造函数
     */
    public SlidingExpirationStrategy() {
        this(Duration.ofHours(1));
    }

    /**
     * 构造函数
     *
     * @param defaultTtl 默认TTL
     */
    public SlidingExpirationStrategy(Duration defaultTtl) {
        this(defaultTtl, Duration.ofMinutes(5));
    }

    /**
     * 构造函数
     *
     * @param defaultTtl      默认TTL
     * @param cleanupInterval 清理间隔
     */
    public SlidingExpirationStrategy(Duration defaultTtl, Duration cleanupInterval) {
        this.defaultTtl = defaultTtl;
        this.cleanupInterval = cleanupInterval;
    }

    @Override
    public String getName() {
        return "SLIDING";
    }

    @Override
    public boolean isExpired(CacheEntry<V> entry) {
        return isExpired(entry, Instant.now());
    }

    @Override
    public boolean isExpired(CacheEntry<V> entry, Instant now) {
        long start = System.nanoTime();
        try {
            // 获取最后访问时间
            Instant lastAccess = lastAccessTimes.get(entry.getKey());
            if (lastAccess == null) {
                lastAccess = entry.getCreateTime();
            }

            // 计算过期时间
            Instant expireTime = lastAccess.plus(defaultTtl);
            return now.isAfter(expireTime);
        } finally {
            statistics.recordCheck(System.nanoTime() - start);
        }
    }

    @Override
    public Instant calculateExpirationTime(K key, V value, Instant createTime) {
        return calculateExpirationTime(createTime, defaultTtl);
    }

    @Override
    public Instant calculateExpirationTime(Instant createTime, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null; // 永不过期
        }
        return createTime.plus(ttl);
    }

    @Override
    public List<CacheEntry<V>> selectExpiredEntries(Collection<CacheEntry<V>> entries) {
        return selectExpiredEntries(entries, Instant.now());
    }

    @Override
    public List<CacheEntry<V>> selectExpiredEntries(Collection<CacheEntry<V>> entries, Instant now) {
        return entries.stream()
            .filter(entry -> isExpired(entry, now))
            .collect(Collectors.toList());
    }

    @Override
    public void onAccess(K key, CacheEntry<V> entry) {
        // 更新最后访问时间
        lastAccessTimes.put(key, Instant.now());
        statistics.recordAccess();
    }

    @Override
    public void onWrite(K key, CacheEntry<V> entry) {
        // 写入也更新访问时间
        lastAccessTimes.put(key, Instant.now());
        statistics.recordWrite();
    }

    @Override
    public void onExpiration(K key, CacheEntry<V> entry) {
        lastAccessTimes.remove(key);
        statistics.recordExpiration();
    }

    @Override
    public Instant getNextCleanupTime() {
        return lastCleanupTime.plus(cleanupInterval);
    }

    @Override
    public boolean requiresPeriodicCleanup() {
        return true;
    }

    @Override
    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    @Override
    public void reset() {
        lastAccessTimes.clear();
        lastCleanupTime = Instant.now();
        statistics.reset();
    }

    @Override
    public ExpirationStatistics getStatistics() {
        return statistics;
    }

    /**
     * 更新清理时间
     */
    public void updateCleanupTime() {
        lastCleanupTime = Instant.now();
        statistics.recordCleanup();
    }

    /**
     * 滑动过期统计信息实现
     */
    private static class SlidingExpirationStatistics implements ExpirationStatistics {
        private final AtomicLong expirationCount = new AtomicLong(0);
        private final AtomicLong checkCount = new AtomicLong(0);
        private final AtomicLong accessCount = new AtomicLong(0);
        private final AtomicLong writeCount = new AtomicLong(0);
        private final AtomicLong cleanupCount = new AtomicLong(0);
        private final AtomicLong totalCheckTime = new AtomicLong(0);
        private volatile long startTime = System.nanoTime();

        void recordCheck(long elapsedNanos) {
            checkCount.incrementAndGet();
            totalCheckTime.addAndGet(elapsedNanos);
        }

        void recordAccess() {
            accessCount.incrementAndGet();
        }

        void recordWrite() {
            writeCount.incrementAndGet();
        }

        void recordExpiration() {
            expirationCount.incrementAndGet();
        }

        void recordCleanup() {
            cleanupCount.incrementAndGet();
        }

        @Override
        public long getExpirationCount() {
            return expirationCount.get();
        }

        @Override
        public long getCheckCount() {
            return checkCount.get();
        }

        @Override
        public double getAverageCheckTime() {
            long totalChecks = checkCount.get();
            return totalChecks > 0 ? (double) totalCheckTime.get() / totalChecks : 0.0;
        }

        @Override
        public long getCleanupCount() {
            return cleanupCount.get();
        }

        @Override
        public void reset() {
            expirationCount.set(0);
            checkCount.set(0);
            accessCount.set(0);
            writeCount.set(0);
            cleanupCount.set(0);
            totalCheckTime.set(0);
            startTime = System.nanoTime();
        }
    }
}