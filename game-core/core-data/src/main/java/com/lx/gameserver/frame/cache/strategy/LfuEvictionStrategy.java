/*
 * 文件名: LfuEvictionStrategy.java
 * 用途: LFU淘汰策略实现
 * 实现内容:
 *   - 最少频率使用淘汰策略
 *   - 基于访问频率的淘汰决策
 *   - 高效的频率计数跟踪
 *   - 频率衰减和老化机制
 *   - 访问模式分析
 * 技术选型:
 *   - ConcurrentHashMap存储频率计数
 *   - 原子操作保证线程安全
 *   - 定期频率衰减机制
 *   - 最小堆优化淘汰选择
 * 依赖关系:
 *   - 实现EvictionStrategy接口
 *   - 被缓存实现使用
 *   - 提供LFU淘汰逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * LFU淘汰策略实现
 * <p>
 * 基于最少频率使用原则的淘汰策略，优先淘汰访问频率最低的缓存条目。
 * 通过维护访问频率计数和定期衰减来实现准确的频率跟踪。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LfuEvictionStrategy<K, V> implements EvictionStrategy<K, V> {

    /**
     * 访问频率记录
     */
    private final Map<K, AtomicLong> frequencies = new ConcurrentHashMap<>();

    /**
     * 访问时间记录（用于相同频率时的LRU决策）
     */
    private final Map<K, Long> accessTimes = new ConcurrentHashMap<>();

    /**
     * 统计信息
     */
    private final LfuStatistics statistics = new LfuStatistics();

    /**
     * 访问计数器
     */
    private final AtomicLong accessCounter = new AtomicLong(0);

    /**
     * 频率衰减因子
     */
    private static final double DECAY_FACTOR = 0.9;

    /**
     * 衰减间隔（访问次数）
     */
    private static final long DECAY_INTERVAL = 10000;

    @Override
    public String getName() {
        return "LFU";
    }

    @Override
    public List<CacheEntry<V>> selectForEviction(Collection<CacheEntry<V>> entries, 
                                                 long maxSize, long currentSize) {
        if (currentSize <= maxSize) {
            return Collections.emptyList();
        }

        long evictCount = currentSize - maxSize;
        return entries.stream()
            .sorted(this::compareByFrequency)
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

        // 获取访问频率
        AtomicLong frequency = frequencies.get(entry.getKey());
        if (frequency == null) {
            return true; // 没有访问记录的条目优先淘汰
        }

        // 检查是否是最低频率
        long minFrequency = frequencies.values().stream()
            .mapToLong(AtomicLong::get)
            .min()
            .orElse(Long.MAX_VALUE);

        return frequency.get() == minFrequency;
    }

    @Override
    public void onAccess(K key, CacheEntry<V> entry) {
        // 更新访问频率
        frequencies.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        // 更新访问时间（用于相同频率时的LRU决策）
        long accessTime = accessCounter.incrementAndGet();
        accessTimes.put(key, accessTime);
        
        statistics.recordAccess();
        
        // 定期频率衰减
        if (accessTime % DECAY_INTERVAL == 0) {
            decayFrequencies();
        }
    }

    @Override
    public void onWrite(K key, CacheEntry<V> entry) {
        // 写入也算作一次访问
        onAccess(key, entry);
        statistics.recordWrite();
    }

    @Override
    public void onEviction(K key, CacheEntry<V> entry) {
        frequencies.remove(key);
        accessTimes.remove(key);
        statistics.recordEviction();
    }

    @Override
    public void reset() {
        frequencies.clear();
        accessTimes.clear();
        accessCounter.set(0);
        statistics.reset();
    }

    @Override
    public EvictionStatistics getStatistics() {
        return statistics;
    }

    /**
     * 频率衰减
     * 定期减少所有键的访问频率，避免历史热点数据永远不被淘汰
     */
    private void decayFrequencies() {
        frequencies.forEach((key, frequency) -> {
            long oldValue = frequency.get();
            long newValue = Math.max(1, (long) (oldValue * DECAY_FACTOR));
            frequency.set(newValue);
        });
        statistics.recordDecay();
    }

    /**
     * 比较访问频率
     *
     * @param entry1 条目1
     * @param entry2 条目2
     * @return 比较结果
     */
    private int compareByFrequency(CacheEntry<V> entry1, CacheEntry<V> entry2) {
        AtomicLong freq1 = frequencies.get(entry1.getKey());
        AtomicLong freq2 = frequencies.get(entry2.getKey());

        // 没有访问记录的优先淘汰
        if (freq1 == null && freq2 == null) {
            return 0;
        }
        if (freq1 == null) {
            return -1;
        }
        if (freq2 == null) {
            return 1;
        }

        long f1 = freq1.get();
        long f2 = freq2.get();

        // 频率相同时使用LRU策略
        if (f1 == f2) {
            Long time1 = accessTimes.get(entry1.getKey());
            Long time2 = accessTimes.get(entry2.getKey());
            
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

        return Long.compare(f1, f2);
    }

    /**
     * LFU统计信息实现
     */
    private static class LfuStatistics implements EvictionStatistics {
        private final AtomicLong evictionCount = new AtomicLong(0);
        private final AtomicLong accessCount = new AtomicLong(0);
        private final AtomicLong writeCount = new AtomicLong(0);
        private final AtomicLong decayCount = new AtomicLong(0);
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

        void recordDecay() {
            decayCount.incrementAndGet();
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
            decayCount.set(0);
            totalAccessTime.set(0);
            startTime = System.nanoTime();
        }

        public long getWriteCount() {
            return writeCount.get();
        }

        public long getDecayCount() {
            return decayCount.get();
        }

        public long getUptime() {
            return System.nanoTime() - startTime;
        }
    }
}