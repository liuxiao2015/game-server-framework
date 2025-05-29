/*
 * 文件名: ExpirationStrategy.java
 * 用途: 过期策略接口
 * 实现内容:
 *   - 定义缓存过期策略的标准接口
 *   - 支持固定过期、滑动过期等策略
 *   - 提供懒惰删除和定期清理
 *   - 自定义过期条件支持
 *   - 过期事件通知机制
 * 技术选型:
 *   - Java 17 接口定义
 *   - 时间API使用Instant和Duration
 *   - 策略模式实现
 *   - 事件驱动设计
 * 依赖关系:
 *   - 被缓存实现类使用
 *   - 提供过期逻辑抽象
 *   - 支持缓存生命周期管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 过期策略接口
 * <p>
 * 定义了缓存过期策略的标准接口，包括过期检查、批量清理、
 * 过期时间计算等功能。支持多种过期策略和自定义过期条件。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface ExpirationStrategy<K, V> {

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 检查条目是否过期
     *
     * @param entry 缓存条目
     * @return 是否过期
     */
    boolean isExpired(CacheEntry<V> entry);

    /**
     * 检查条目是否过期（指定当前时间）
     *
     * @param entry 缓存条目
     * @param now   当前时间
     * @return 是否过期
     */
    boolean isExpired(CacheEntry<V> entry, Instant now);

    /**
     * 计算过期时间
     *
     * @param key      缓存键
     * @param value    缓存值
     * @param createTime 创建时间
     * @return 过期时间
     */
    Instant calculateExpirationTime(K key, V value, Instant createTime);

    /**
     * 计算过期时间（使用默认TTL）
     *
     * @param createTime 创建时间
     * @param ttl        生存时间
     * @return 过期时间
     */
    Instant calculateExpirationTime(Instant createTime, Duration ttl);

    /**
     * 选择过期的条目
     *
     * @param entries 所有缓存条目
     * @return 过期的条目列表
     */
    List<CacheEntry<V>> selectExpiredEntries(Collection<CacheEntry<V>> entries);

    /**
     * 选择过期的条目（指定当前时间）
     *
     * @param entries 所有缓存条目
     * @param now     当前时间
     * @return 过期的条目列表
     */
    List<CacheEntry<V>> selectExpiredEntries(Collection<CacheEntry<V>> entries, Instant now);

    /**
     * 记录访问（用于滑动过期）
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
     * 记录过期
     *
     * @param key   缓存键
     * @param entry 过期的条目
     */
    void onExpiration(K key, CacheEntry<V> entry);

    /**
     * 获取下次清理时间
     *
     * @return 下次清理时间
     */
    Instant getNextCleanupTime();

    /**
     * 是否需要定期清理
     *
     * @return 是否需要定期清理
     */
    boolean requiresPeriodicCleanup();

    /**
     * 获取清理间隔
     *
     * @return 清理间隔
     */
    Duration getCleanupInterval();

    /**
     * 重置策略状态
     */
    void reset();

    /**
     * 获取过期统计信息
     *
     * @return 统计信息
     */
    ExpirationStatistics getStatistics();

    /**
     * 创建策略实例
     *
     * @param strategyName 策略名称
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 策略实例
     */
    static <K, V> ExpirationStrategy<K, V> create(String strategyName) {
        return switch (strategyName.toUpperCase()) {
            case "FIXED" -> new FixedExpirationStrategy<>();
            case "SLIDING" -> new SlidingExpirationStrategy<>();
            case "LAZY" -> new LazyExpirationStrategy<>();
            case "PERIODIC" -> new PeriodicExpirationStrategy<>();
            default -> throw new IllegalArgumentException("不支持的过期策略: " + strategyName);
        };
    }

    /**
     * 创建带参数的策略实例
     *
     * @param strategyName 策略名称
     * @param ttl          默认TTL
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 策略实例
     */
    static <K, V> ExpirationStrategy<K, V> create(String strategyName, Duration ttl) {
        return switch (strategyName.toUpperCase()) {
            case "FIXED" -> new FixedExpirationStrategy<>(ttl);
            case "SLIDING" -> new SlidingExpirationStrategy<>(ttl);
            case "LAZY" -> new LazyExpirationStrategy<>(ttl);
            case "PERIODIC" -> new PeriodicExpirationStrategy<>(ttl);
            default -> throw new IllegalArgumentException("不支持的过期策略: " + strategyName);
        };
    }

    /**
     * 过期上下文
     */
    class ExpirationContext {
        private final Instant currentTime;
        private final Duration defaultTtl;
        private final Function<CacheEntry<?>, Duration> ttlCalculator;
        private final Predicate<CacheEntry<?>> customPredicate;

        public ExpirationContext(Instant currentTime, Duration defaultTtl) {
            this(currentTime, defaultTtl, null, null);
        }

        public ExpirationContext(Instant currentTime, Duration defaultTtl,
                                Function<CacheEntry<?>, Duration> ttlCalculator,
                                Predicate<CacheEntry<?>> customPredicate) {
            this.currentTime = currentTime;
            this.defaultTtl = defaultTtl;
            this.ttlCalculator = ttlCalculator;
            this.customPredicate = customPredicate;
        }

        public Instant getCurrentTime() {
            return currentTime;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public boolean hasTtlCalculator() {
            return ttlCalculator != null;
        }

        public Duration calculateTtl(CacheEntry<?> entry) {
            return ttlCalculator != null ? ttlCalculator.apply(entry) : defaultTtl;
        }

        public boolean hasCustomPredicate() {
            return customPredicate != null;
        }

        public boolean testCustomPredicate(CacheEntry<?> entry) {
            return customPredicate != null && customPredicate.test(entry);
        }
    }

    /**
     * 过期统计信息
     */
    interface ExpirationStatistics {
        /**
         * 获取过期次数
         *
         * @return 过期次数
         */
        long getExpirationCount();

        /**
         * 获取总检查次数
         *
         * @return 检查次数
         */
        long getCheckCount();

        /**
         * 获取平均检查时间
         *
         * @return 平均检查时间（纳秒）
         */
        double getAverageCheckTime();

        /**
         * 获取清理次数
         *
         * @return 清理次数
         */
        long getCleanupCount();

        /**
         * 重置统计信息
         */
        void reset();
    }
}