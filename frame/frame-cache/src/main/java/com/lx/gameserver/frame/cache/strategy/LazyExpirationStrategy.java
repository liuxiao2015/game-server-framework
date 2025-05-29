/*
 * 文件名: LazyExpirationStrategy.java
 * 用途: 懒惰过期策略实现
 * 实现内容:
 *   - 懒惰删除过期策略
 *   - 仅在访问时检查过期
 *   - 最小化定期清理开销
 *   - 按需过期检查
 *   - 高性能访问优化
 * 技术选型:
 *   - 基于Instant和Duration的时间计算
 *   - 最小化内存和CPU开销
 *   - 访问时过期检查
 *   - 简单高效的实现
 * 依赖关系:
 *   - 实现ExpirationStrategy接口
 *   - 被缓存实现使用
 *   - 提供懒惰过期逻辑
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
 * 懒惰过期策略实现
 * <p>
 * 基于懒惰删除的过期策略，只在访问时检查过期，
 * 不进行主动的定期清理，最小化系统开销。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LazyExpirationStrategy<K, V> implements ExpirationStrategy<K, V> {

    /**
     * 默认TTL
     */
    private final Duration defaultTtl;

    /**
     * 统计信息
     */
    private final LazyExpirationStatistics statistics = new LazyExpirationStatistics();

    /**
     * 默认构造函数
     */
    public LazyExpirationStrategy() {
        this(Duration.ofHours(1));
    }

    /**
     * 构造函数
     *
     * @param defaultTtl 默认TTL
     */
    public LazyExpirationStrategy(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    @Override
    public String getName() {
        return "LAZY";
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
        // 懒惰策略不需要定期清理
        return Instant.MAX;
    }

    @Override
    public boolean requiresPeriodicCleanup() {
        return false;
    }

    @Override
    public Duration getCleanupInterval() {
        // 懒惰策略不需要清理间隔
        return Duration.ZERO;
    }

    @Override
    public void reset() {
        statistics.reset();
    }

    @Override
    public ExpirationStatistics getStatistics() {
        return statistics;
    }

    /**
     * 懒惰过期统计信息实现
     */
    private static class LazyExpirationStatistics implements ExpirationStatistics {
        private final AtomicLong expirationCount = new AtomicLong(0);
        private final AtomicLong checkCount = new AtomicLong(0);
        private final AtomicLong accessCount = new AtomicLong(0);
        private final AtomicLong writeCount = new AtomicLong(0);
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
            // 懒惰策略不进行清理
            return 0;
        }

        @Override
        public void reset() {
            expirationCount.set(0);
            checkCount.set(0);
            accessCount.set(0);
            writeCount.set(0);
            totalCheckTime.set(0);
            startTime = System.nanoTime();
        }
    }
}