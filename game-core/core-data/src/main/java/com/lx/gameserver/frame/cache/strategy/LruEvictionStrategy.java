/*
 * 文件名: LruEvictionStrategy.java
 * 用途: LRU淘汰策略实现
 * 实现内容:
 *   - 最近最少使用淘汰策略
 *   - 基于访问时间的淘汰决策
 *   - 高效的访问时间跟踪
 *   - 批量淘汰优化
 *   - 访问统计和监控
 * 技术选型:
 *   - LinkedHashMap维护访问顺序
 *   - 原子操作保证线程安全
 *   - 时间戳跟踪访问记录
 *   - 高效的批量操作
 * 依赖关系:
 *   - 实现EvictionStrategy接口
 *   - 被缓存实现使用
 *   - 提供LRU淘汰逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * LRU淘汰策略实现
 * <p>
 * 基于最近最少使用原则的淘汰策略，优先淘汰最长时间未被访问的缓存条目。
 * 通过维护访问时间戳和访问顺序来实现高效的淘汰决策。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LruEvictionStrategy<K, V> implements EvictionStrategy<K, V> {

    /**
     * 访问时间记录
     */
    private final Map<K, Long> accessTimes = new ConcurrentHashMap<>();

    /**
     * 统计信息
     */
    private final LruStatistics statistics = new LruStatistics();

    /**
     * 访问计数器
     */
    private final AtomicLong accessCounter = new AtomicLong(0);

    @Override
    public String getName() {
        return "LRU";
    }

    @Override
    public List<CacheEntry<V>> selectForEviction(Collection<CacheEntry<V>> entries, 
                                                 long maxSize, long currentSize) {
        if (currentSize <= maxSize) {
            return Collections.emptyList();
        }

        long evictCount = currentSize - maxSize;
        return entries.stream()
            .sorted(this::compareByAccessTime)
            .limit(evictCount)
            .collect(Collectors.toList());
    }

    @Override
    public boolean shouldEvict(CacheEntry<V> entry, EvictionContext context) {
        if (!context.isOverCapacity()) {
            return false;
        }

        // 如果有自定义条件，优先使用
        if (context.hasCustomPredicate()) {
            return context.testCustomPredicate(entry);
        }

        // 获取访问时间
        Long accessTime = accessTimes.get(entry.getKey());
        if (accessTime == null) {
            return true; // 没有访问记录的条目优先淘汰
        }

        // 检查是否是最老的访问记录
        long oldestTime = accessTimes.values().stream()
            .min(Long::compareTo)
            .orElse(Long.MAX_VALUE);

        return accessTime.equals(oldestTime);
    }

    @Override
    public void onAccess(K key, CacheEntry<V> entry) {
        long accessTime = accessCounter.incrementAndGet();
        accessTimes.put(key, accessTime);
        statistics.recordAccess();
    }

    @Override
    public void onWrite(K key, CacheEntry<V> entry) {
        long accessTime = accessCounter.incrementAndGet();
        accessTimes.put(key, accessTime);
        statistics.recordWrite();
    }

    @Override
    public void onEviction(K key, CacheEntry<V> entry) {
        accessTimes.remove(key);
        statistics.recordEviction();
    }

    @Override
    public void reset() {
        accessTimes.clear();
        accessCounter.set(0);
        statistics.reset();
    }

    @Override
    public EvictionStatistics getStatistics() {
        return statistics;
    }

    /**
     * 比较访问时间
     *
     * @param entry1 条目1
     * @param entry2 条目2
     * @return 比较结果
     */
    private int compareByAccessTime(CacheEntry<V> entry1, CacheEntry<V> entry2) {
        Long time1 = accessTimes.get(entry1.getKey());
        Long time2 = accessTimes.get(entry2.getKey());

        // 没有访问记录的优先淘汰
        if (time1 == null && time2 == null) {
            return 0;
        }
        if (time1 == null) {
            return -1;
        }
        if (time2 == null) {
            return 1;
        }

        return Long.compare(time1, time2);
    }

    /**
     * LRU统计信息实现
     */
    private static class LruStatistics implements EvictionStatistics {
        private final AtomicLong evictionCount = new AtomicLong(0);
        private final AtomicLong accessCount = new AtomicLong(0);
        private final AtomicLong writeCount = new AtomicLong(0);
        private final AtomicLong totalAccessTime = new AtomicLong(0);
        private volatile long startTime = System.nanoTime();

        void recordAccess() {
            long start = System.nanoTime();
            accessCount.incrementAndGet();
            totalAccessTime.addAndGet(System.nanoTime() - start);
        }

        void recordWrite() {
            writeCount.incrementAndGet();
        }

        void recordEviction() {
            evictionCount.incrementAndGet();
        }

        @Override
        public long getEvictionCount() {
            return evictionCount.get();
        }

        @Override
        public long getAccessCount() {
            return accessCount.get();
        }

        @Override
        public double getAverageAccessTime() {
            long totalAccess = accessCount.get();
            return totalAccess > 0 ? (double) totalAccessTime.get() / totalAccess : 0.0;
        }

        @Override
        public void reset() {
            evictionCount.set(0);
            accessCount.set(0);
            writeCount.set(0);
            totalAccessTime.set(0);
            startTime = System.nanoTime();
        }

        public long getWriteCount() {
            return writeCount.get();
        }

        public long getUptime() {
            return System.nanoTime() - startTime;
        }
    }
}