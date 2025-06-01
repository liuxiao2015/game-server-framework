/*
 * 文件名: RefreshStrategy.java
 * 用途: 刷新策略接口
 * 实现内容:
 *   - 定义缓存刷新策略的标准接口
 *   - 支持定时刷新、访问刷新等策略
 *   - 提供预加载和异步刷新
 *   - 批量刷新优化
 *   - 刷新失败处理机制
 * 技术选型:
 *   - Java 17 接口定义
 *   - CompletableFuture异步支持
 *   - 策略模式实现
 *   - 事件驱动设计
 * 依赖关系:
 *   - 被缓存实现类使用
 *   - 提供刷新逻辑抽象
 *   - 支持缓存数据更新
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;
import com.lx.gameserver.frame.cache.core.CacheKey;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 刷新策略接口
 * <p>
 * 定义了缓存刷新策略的标准接口，包括刷新时机判断、
 * 批量刷新、异步刷新等功能。支持多种刷新策略和自定义刷新逻辑。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface RefreshStrategy<K, V> {

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 检查条目是否需要刷新
     *
     * @param entry 缓存条目
     * @return 是否需要刷新
     */
    boolean shouldRefresh(CacheEntry<V> entry);

    /**
     * 检查条目是否需要刷新（指定当前时间）
     *
     * @param entry 缓存条目
     * @param now   当前时间
     * @return 是否需要刷新
     */
    boolean shouldRefresh(CacheEntry<V> entry, Instant now);

    /**
     * 计算下次刷新时间
     *
     * @param key        缓存键
     * @param entry      缓存条目
     * @param accessTime 访问时间
     * @return 下次刷新时间
     */
    Instant calculateNextRefreshTime(K key, CacheEntry<V> entry, Instant accessTime);

    /**
     * 选择需要刷新的条目
     *
     * @param entries 所有缓存条目
     * @return 需要刷新的条目列表
     */
    List<CacheEntry<V>> selectRefreshableEntries(Collection<CacheEntry<V>> entries);

    /**
     * 选择需要刷新的条目（指定当前时间）
     *
     * @param entries 所有缓存条目
     * @param now     当前时间
     * @return 需要刷新的条目列表
     */
    List<CacheEntry<V>> selectRefreshableEntries(Collection<CacheEntry<V>> entries, Instant now);

    /**
     * 同步刷新
     *
     * @param key     缓存键
     * @param entry   缓存条目
     * @param loader  加载器
     * @return 刷新后的值
     */
    V refresh(K key, CacheEntry<V> entry, Function<K, V> loader);

    /**
     * 异步刷新
     *
     * @param key     缓存键
     * @param entry   缓存条目
     * @param loader  异步加载器
     * @return 刷新后的值的Future
     */
    CompletableFuture<V> refreshAsync(K key, CacheEntry<V> entry, 
                                     Function<K, CompletableFuture<V>> loader);

    /**
     * 批量刷新
     *
     * @param entries 需要刷新的条目
     * @param loader  批量加载器
     * @return 刷新结果
     */
    CompletableFuture<List<CacheEntry<V>>> refreshBatch(List<CacheEntry<V>> entries,
                                                        Function<List<CacheKey>, CompletableFuture<List<V>>> loader);

    /**
     * 记录访问（用于访问触发刷新）
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
     * 记录刷新
     *
     * @param key      缓存键
     * @param oldEntry 旧条目
     * @param newEntry 新条目
     */
    void onRefresh(K key, CacheEntry<V> oldEntry, CacheEntry<V> newEntry);

    /**
     * 记录刷新失败
     *
     * @param key       缓存键
     * @param entry     缓存条目
     * @param exception 异常
     */
    void onRefreshFailure(K key, CacheEntry<V> entry, Throwable exception);

    /**
     * 获取下次批量刷新时间
     *
     * @return 下次批量刷新时间
     */
    Instant getNextBatchRefreshTime();

    /**
     * 是否需要定期批量刷新
     *
     * @return 是否需要定期批量刷新
     */
    boolean requiresPeriodicRefresh();

    /**
     * 获取刷新间隔
     *
     * @return 刷新间隔
     */
    Duration getRefreshInterval();

    /**
     * 重置策略状态
     */
    void reset();

    /**
     * 获取刷新统计信息
     *
     * @return 统计信息
     */
    RefreshStatistics getStatistics();

    /**
     * 创建策略实例
     *
     * @param strategyName 策略名称
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 策略实例
     */
    static <K, V> RefreshStrategy<K, V> create(String strategyName) {
        return switch (strategyName.toUpperCase()) {
            case "TIMED" -> new TimedRefreshStrategy<>();
            case "ACCESS" -> new AccessRefreshStrategy<>();
            case "PRELOAD" -> new PreloadRefreshStrategy<>();
            case "ASYNC" -> new AsyncRefreshStrategy<>();
            case "BATCH" -> new BatchRefreshStrategy<>();
            default -> throw new IllegalArgumentException("不支持的刷新策略: " + strategyName);
        };
    }

    /**
     * 创建带参数的策略实例
     *
     * @param strategyName    策略名称
     * @param refreshInterval 刷新间隔
     * @param <K>             键类型
     * @param <V>             值类型
     * @return 策略实例
     */
    static <K, V> RefreshStrategy<K, V> create(String strategyName, Duration refreshInterval) {
        return switch (strategyName.toUpperCase()) {
            case "TIMED" -> new TimedRefreshStrategy<>(refreshInterval);
            case "ACCESS" -> new AccessRefreshStrategy<>(refreshInterval);
            case "PRELOAD" -> new PreloadRefreshStrategy<>(refreshInterval);
            case "ASYNC" -> new AsyncRefreshStrategy<>(refreshInterval);
            case "BATCH" -> new BatchRefreshStrategy<>(refreshInterval);
            default -> throw new IllegalArgumentException("不支持的刷新策略: " + strategyName);
        };
    }

    /**
     * 刷新统计信息
     */
    interface RefreshStatistics {
        /**
         * 获取刷新次数
         *
         * @return 刷新次数
         */
        long getRefreshCount();

        /**
         * 获取刷新成功次数
         *
         * @return 刷新成功次数
         */
        long getRefreshSuccessCount();

        /**
         * 获取刷新失败次数
         *
         * @return 刷新失败次数
         */
        long getRefreshFailureCount();

        /**
         * 获取平均刷新时间
         *
         * @return 平均刷新时间（纳秒）
         */
        double getAverageRefreshTime();

        /**
         * 获取批量刷新次数
         *
         * @return 批量刷新次数
         */
        long getBatchRefreshCount();

        /**
         * 重置统计信息
         */
        void reset();
    }
}