/*
 * 文件名: PeriodicExpirationStrategy.java
 * 用途: 定期过期策略实现
 * 实现内容:
 *   - 定期清理过期策略
 *   - 主动的定期过期检查
 *   - 可配置的清理间隔
 *   - 批量过期清理优化
 *   - 后台清理任务调度
 * 技术选型:
 *   - 基于Instant和Duration的时间计算
 *   - 定期任务调度
 *   - 批量处理优化
 *   - 可配置的清理策略
 * 依赖关系:
 *   - 实现ExpirationStrategy接口
 *   - 被缓存实现使用
 *   - 提供定期过期逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 定期过期策略实现
 * <p>
 * 基于定期清理的过期策略，定期主动检查和清理过期条目，
 * 确保过期数据及时被清理，避免内存泄漏。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class PeriodicExpirationStrategy<K, V> implements ExpirationStrategy<K, V> {

    /**
     * 默认TTL
     */
    private final Duration defaultTtl;

    /**
     * 清理间隔
     */
    private final Duration cleanupInterval;

    /**
     * 统计信息
     */
    private final PeriodicExpirationStatistics statistics = new PeriodicExpirationStatistics();

    /**
     * 上次清理时间
     */
    private volatile Instant lastCleanupTime = Instant.now();

    /**
     * 下次清理时间
     */
    private volatile Instant nextCleanupTime;

    /**
     * 默认构造函数
     */
    public PeriodicExpirationStrategy() {
        this(Duration.ofHours(1), Duration.ofMinutes(1));
    }

    /**
     * 构造函数
     *
     * @param defaultTtl 默认TTL
     */
    public PeriodicExpirationStrategy(Duration defaultTtl) {
        this(defaultTtl, Duration.ofMinutes(1));
    }

    /**
     * 构造函数
     *
     * @param defaultTtl      默认TTL
     * @param cleanupInterval 清理间隔
     */
    public PeriodicExpirationStrategy(Duration defaultTtl, Duration cleanupInterval) {
        this.defaultTtl = defaultTtl;
        this.cleanupInterval = cleanupInterval;
        this.nextCleanupTime = lastCleanupTime.plus(cleanupInterval);
    }

    @Override
    public String getName() {
        return "PERIODIC";
    }

    @Override
    public boolean isExpired(CacheEntry<V> entry) {
        return isExpired(entry, Instant.now());
    }

    @Override
    public boolean isExpired(CacheEntry<V> entry, Instant now) {
        long start = System.nanoTime();
        try {
            Instant expireTime = entry.getExpireTime();
            if (expireTime == null) {
                return false;
            }
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
        statistics.recordAccess();
    }

    @Override
    public void onWrite(K key, CacheEntry<V> entry) {
        statistics.recordWrite();
    }

    @Override
    public void onExpiration(K key, CacheEntry<V> entry) {
        statistics.recordExpiration();
    }

    @Override
    public Instant getNextCleanupTime() {
        return nextCleanupTime;
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
        lastCleanupTime = Instant.now();
        nextCleanupTime = lastCleanupTime.plus(cleanupInterval);
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
        nextCleanupTime = lastCleanupTime.plus(cleanupInterval);
        statistics.recordCleanup();
    }

    /**
     * 检查是否需要清理
     *
     * @return 是否需要清理
     */
    public boolean shouldCleanup() {
        return Instant.now().isAfter(nextCleanupTime);
    }

    /**
     * 定期过期统计信息实现
     */
    private static class PeriodicExpirationStatistics implements ExpirationStatistics {
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