/*
 * 文件名: Cache.java
 * 用途: 缓存接口定义
 * 实现内容:
 *   - 定义缓存的基本操作接口
 *   - 支持同步和异步操作
 *   - 提供批量操作功能
 *   - 过期时间和统计信息支持
 *   - 泛型类型安全
 * 技术选型:
 *   - Java 17 接口定义
 *   - CompletableFuture异步支持
 *   - 泛型设计保证类型安全
 *   - 函数式接口支持
 * 依赖关系:
 *   - 被所有缓存实现类实现
 *   - 提供统一的缓存操作接口
 *   - 支持缓存管理器使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.core;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 缓存接口定义
 * <p>
 * 定义了缓存操作的标准接口，包括基础的增删改查操作、批量操作、
 * 异步操作等。所有缓存实现都应该实现此接口。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface Cache<K, V> {

    /**
     * 获取缓存名称
     *
     * @return 缓存名称
     */
    String getName();

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值，如果不存在则返回null
     */
    V get(K key);

    /**
     * 异步获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值的异步结果
     */
    CompletableFuture<V> getAsync(K key);

    /**
     * 获取缓存值，如果不存在则通过加载器加载
     *
     * @param key    缓存键
     * @param loader 值加载器
     * @return 缓存值
     */
    V get(K key, Function<K, V> loader);

    /**
     * 异步获取缓存值，如果不存在则通过加载器加载
     *
     * @param key    缓存键
     * @param loader 异步值加载器
     * @return 缓存值的异步结果
     */
    CompletableFuture<V> getAsync(K key, Function<K, CompletableFuture<V>> loader);

    /**
     * 批量获取缓存值
     *
     * @param keys 缓存键集合
     * @return 键值对映射
     */
    Map<K, V> getAll(Collection<K> keys);

    /**
     * 异步批量获取缓存值
     *
     * @param keys 缓存键集合
     * @return 键值对映射的异步结果
     */
    CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys);

    /**
     * 存储缓存值
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void put(K key, V value);

    /**
     * 存储缓存值并指定过期时间
     *
     * @param key      缓存键
     * @param value    缓存值
     * @param duration 过期时间
     */
    void put(K key, V value, Duration duration);

    /**
     * 异步存储缓存值
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 存储完成的异步结果
     */
    CompletableFuture<Void> putAsync(K key, V value);

    /**
     * 异步存储缓存值并指定过期时间
     *
     * @param key      缓存键
     * @param value    缓存值
     * @param duration 过期时间
     * @return 存储完成的异步结果
     */
    CompletableFuture<Void> putAsync(K key, V value, Duration duration);

    /**
     * 仅当键不存在时存储缓存值
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 如果存储成功返回true，如果键已存在返回false
     */
    boolean putIfAbsent(K key, V value);

    /**
     * 仅当键不存在时存储缓存值并指定过期时间
     *
     * @param key      缓存键
     * @param value    缓存值
     * @param duration 过期时间
     * @return 如果存储成功返回true，如果键已存在返回false
     */
    boolean putIfAbsent(K key, V value, Duration duration);

    /**
     * 批量存储缓存值
     *
     * @param map 键值对映射
     */
    void putAll(Map<K, V> map);

    /**
     * 异步批量存储缓存值
     *
     * @param map 键值对映射
     * @return 存储完成的异步结果
     */
    CompletableFuture<Void> putAllAsync(Map<K, V> map);

    /**
     * 移除缓存项
     *
     * @param key 缓存键
     * @return 被移除的值，如果不存在则返回null
     */
    V remove(K key);

    /**
     * 异步移除缓存项
     *
     * @param key 缓存键
     * @return 被移除的值的异步结果
     */
    CompletableFuture<V> removeAsync(K key);

    /**
     * 批量移除缓存项
     *
     * @param keys 缓存键集合
     */
    void removeAll(Collection<K> keys);

    /**
     * 异步批量移除缓存项
     *
     * @param keys 缓存键集合
     * @return 移除完成的异步结果
     */
    CompletableFuture<Void> removeAllAsync(Collection<K> keys);

    /**
     * 清空所有缓存
     */
    void clear();

    /**
     * 异步清空所有缓存
     *
     * @return 清空完成的异步结果
     */
    CompletableFuture<Void> clearAsync();

    /**
     * 检查键是否存在
     *
     * @param key 缓存键
     * @return 如果存在返回true，否则返回false
     */
    boolean containsKey(K key);

    /**
     * 获取缓存大小
     *
     * @return 缓存项数量
     */
    long size();

    /**
     * 检查缓存是否为空
     *
     * @return 如果为空返回true，否则返回false
     */
    boolean isEmpty();

    /**
     * 获取所有缓存键
     *
     * @return 缓存键的集合
     */
    Collection<K> keys();

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    CacheStatistics getStatistics();

    /**
     * 刷新缓存项
     *
     * @param key 缓存键
     */
    void refresh(K key);

    /**
     * 异步刷新缓存项
     *
     * @param key 缓存键
     * @return 刷新完成的异步结果
     */
    CompletableFuture<Void> refreshAsync(K key);

    /**
     * 获取缓存配置
     *
     * @return 缓存配置
     */
    CacheConfig getConfig();

    /**
     * 缓存统计信息接口
     */
    interface CacheStatistics {
        /**
         * 获取请求总数
         */
        long getRequestCount();

        /**
         * 获取命中次数
         */
        long getHitCount();

        /**
         * 获取未命中次数
         */
        long getMissCount();

        /**
         * 获取加载次数
         */
        long getLoadCount();

        /**
         * 获取逐出次数
         */
        long getEvictionCount();

        /**
         * 获取命中率
         */
        double getHitRate();

        /**
         * 获取未命中率
         */
        double getMissRate();

        /**
         * 获取平均加载时间（纳秒）
         */
        double getAverageLoadTime();
    }
}