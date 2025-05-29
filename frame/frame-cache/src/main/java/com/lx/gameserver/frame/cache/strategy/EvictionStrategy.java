/*
 * 文件名: EvictionStrategy.java
 * 用途: 淘汰策略接口
 * 实现内容:
 *   - 定义缓存淘汰策略的标准接口
 *   - 支持LRU、LFU、FIFO等常见策略
 *   - 提供自定义策略扩展能力
 *   - 支持动态策略切换
 *   - 淘汰过程监控和统计
 * 技术选型:
 *   - Java 17 接口定义
 *   - 泛型设计保证类型安全
 *   - 函数式接口支持
 *   - 策略模式实现
 * 依赖关系:
 *   - 被缓存实现类使用
 *   - 提供淘汰逻辑抽象
 *   - 支持缓存容量管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 淘汰策略接口
 * <p>
 * 定义了缓存淘汰策略的标准接口，包括淘汰决策、批量淘汰、
 * 策略切换等功能。支持常见的LRU、LFU、FIFO策略以及自定义策略。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface EvictionStrategy<K, V> {

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 选择要淘汰的条目
     *
     * @param entries     所有缓存条目
     * @param maxSize     最大容量
     * @param currentSize 当前大小
     * @return 要淘汰的条目列表
     */
    List<CacheEntry<V>> selectForEviction(Collection<CacheEntry<V>> entries, 
                                          long maxSize, long currentSize);

    /**
     * 单个淘汰决策
     *
     * @param entry   缓存条目
     * @param context 淘汰上下文
     * @return 是否应该淘汰
     */
    boolean shouldEvict(CacheEntry<V> entry, EvictionContext context);

    /**
     * 记录访问
     *
     * @param key   缓存键
     * @param entry 缓存条目
     */
    void onAccess(K key, CacheEntry<V> entry);

    /**
     * 记录写入
     *
     * @param key   缓存键
     * @param entry 缓存条目
     */
    void onWrite(K key, CacheEntry<V> entry);

    /**
     * 记录淘汰
     *
     * @param key   缓存键
     * @param entry 被淘汰的条目
     */
    void onEviction(K key, CacheEntry<V> entry);

    /**
     * 重置策略状态
     */
    void reset();

    /**
     * 获取策略统计信息
     *
     * @return 统计信息
     */
    EvictionStatistics getStatistics();

    /**
     * 创建策略实例
     *
     * @param strategyName 策略名称
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 策略实例
     */
    static <K, V> EvictionStrategy<K, V> create(String strategyName) {
        return switch (strategyName.toUpperCase()) {
            case "LRU" -> new LruEvictionStrategy<>();
            case "LFU" -> new LfuEvictionStrategy<>();
            case "FIFO" -> new FifoEvictionStrategy<>();
            default -> throw new IllegalArgumentException("不支持的淘汰策略: " + strategyName);
        };
    }

    /**
     * 淘汰上下文
     */
    class EvictionContext {
        private final long maxSize;
        private final long currentSize;
        private final Predicate<CacheEntry<?>> customPredicate;

        public EvictionContext(long maxSize, long currentSize) {
            this(maxSize, currentSize, null);
        }

        public EvictionContext(long maxSize, long currentSize, 
                              Predicate<CacheEntry<?>> customPredicate) {
            this.maxSize = maxSize;
            this.currentSize = currentSize;
            this.customPredicate = customPredicate;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public long getCurrentSize() {
            return currentSize;
        }

        public boolean isOverCapacity() {
            return currentSize > maxSize;
        }

        public double getLoadFactor() {
            return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        }

        public boolean hasCustomPredicate() {
            return customPredicate != null;
        }

        public boolean testCustomPredicate(CacheEntry<?> entry) {
            return customPredicate != null && customPredicate.test(entry);
        }
    }

    /**
     * 淘汰统计信息
     */
    interface EvictionStatistics {
        /**
         * 获取淘汰次数
         *
         * @return 淘汰次数
         */
        long getEvictionCount();

        /**
         * 获取总访问次数
         *
         * @return 访问次数
         */
        long getAccessCount();

        /**
         * 获取平均访问时间
         *
         * @return 平均访问时间（纳秒）
         */
        double getAverageAccessTime();

        /**
         * 重置统计信息
         */
        void reset();
    }
}