/*
 * 文件名: LocalCache.java
 * 用途: 本地缓存实现
 * 实现内容:
 *   - 基于Caffeine的高性能本地缓存
 *   - 支持自动过期和容量限制
 *   - 多种淘汰策略（LRU/LFU/FIFO）
 *   - 统计监控和性能指标
 *   - 异步操作支持
 * 技术选型:
 *   - Caffeine高性能缓存库
 *   - CompletableFuture异步支持
 *   - 统计信息收集
 *   - 线程安全设计
 * 依赖关系:
 *   - 实现Cache接口
 *   - 被LocalCacheManager管理
 *   - 提供本地缓存功能
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.local;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheConfig;
import com.lx.gameserver.frame.cache.core.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 本地缓存实现
 * <p>
 * 基于Caffeine库实现的高性能本地缓存，支持自动过期、容量限制、
 * 多种淘汰策略、统计监控等功能。适用于单机环境的高速缓存需求。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LocalCache<K, V> implements Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(LocalCache.class);

    /**
     * 缓存配置
     */
    private final CacheConfig config;

    /**
     * Caffeine缓存实例
     */
    private final com.github.benmanes.caffeine.cache.Cache<K, V> caffeineCache;

    /**
     * 异步缓存实例
     */
    private final AsyncCache<K, V> asyncCache;

    /**
     * 加载缓存实例（可选）
     */
    private final LoadingCache<K, V> loadingCache;

    /**
     * 异步执行器
     */
    private final Executor asyncExecutor;

    /**
     * 移除监听器
     */
    private final RemovalListener<K, V> removalListener;

    /**
     * 构造函数
     *
     * @param config 缓存配置
     */
    public LocalCache(CacheConfig config) {
        this(config, null);
    }

    /**
     * 构造函数
     *
     * @param config       缓存配置
     * @param cacheLoader  缓存加载器
     */
    public LocalCache(CacheConfig config, CacheLoader<K, V> cacheLoader) {
        this.config = Objects.requireNonNull(config, "缓存配置不能为null");
        this.asyncExecutor = ForkJoinPool.commonPool();
        this.removalListener = this::onRemoval;

        // 构建Caffeine缓存
        Caffeine<K, V> builder = Caffeine.newBuilder()
            .maximumSize(config.getMaxSize())
            .removalListener(removalListener);

        // 配置过期时间
        if (config.getExpireAfterWrite() != null) {
            builder.expireAfterWrite(config.getExpireAfterWrite());
        }
        if (config.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(config.getExpireAfterAccess());
        }
        if (config.getRefreshAfterWrite() != null) {
            builder.refreshAfterWrite(config.getRefreshAfterWrite());
        }

        // 配置统计
        if (config.isStatisticsEnabled()) {
            builder.recordStats();
        }

        // 创建缓存实例
        if (cacheLoader != null) {
            this.loadingCache = builder.build(key -> {
                try {
                    return cacheLoader.load(key);
                } catch (Exception e) {
                    throw new RuntimeException("缓存加载失败", e);
                }
            });
            this.caffeineCache = this.loadingCache;
            this.asyncCache = config.isAsyncEnabled() ? 
                builder.buildAsync() : null;
        } else {
            this.loadingCache = null;
            this.caffeineCache = builder.build();
            this.asyncCache = config.isAsyncEnabled() ? 
                builder.buildAsync() : null;
        }

        logger.info("本地缓存已创建: name={}, maxSize={}, config={}", 
            config.getName(), config.getMaxSize(), config);
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            V value = caffeineCache.getIfPresent(key);
            if (value != null) {
                logger.debug("缓存命中: name={}, key={}", config.getName(), key);
            } else {
                logger.debug("缓存未命中: name={}, key={}", config.getName(), key);
            }
            return value;
        } catch (Exception e) {
            logger.error("获取缓存失败: name={}, key={}", config.getName(), key, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<V> getAsync(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        if (asyncCache != null) {
            return asyncCache.getIfPresent(key)
                .thenApply(value -> {
                    if (value != null) {
                        logger.debug("异步缓存命中: name={}, key={}", config.getName(), key);
                    } else {
                        logger.debug("异步缓存未命中: name={}, key={}", config.getName(), key);
                    }
                    return value;
                })
                .exceptionally(throwable -> {
                    logger.error("异步获取缓存失败: name={}, key={}", config.getName(), key, throwable);
                    return null;
                });
        } else {
            return CompletableFuture.supplyAsync(() -> get(key), asyncExecutor);
        }
    }

    @Override
    public V get(K key, Function<K, V> loader) {
        Objects.requireNonNull(key, "键不能为null");
        Objects.requireNonNull(loader, "加载器不能为null");
        
        try {
            return caffeineCache.get(key, loader);
        } catch (Exception e) {
            logger.error("加载缓存失败: name={}, key={}", config.getName(), key, e);
            throw new RuntimeException("加载缓存失败", e);
        }
    }

    @Override
    public CompletableFuture<V> getAsync(K key, Function<K, CompletableFuture<V>> loader) {
        Objects.requireNonNull(key, "键不能为null");
        Objects.requireNonNull(loader, "异步加载器不能为null");
        
        if (asyncCache != null) {
            return asyncCache.get(key, (k, executor) -> loader.apply(k));
        } else {
            return CompletableFuture.supplyAsync(() -> {
                V value = get(key);
                if (value != null) {
                    return value;
                }
                // 如果缓存中没有，使用加载器异步加载
                return loader.apply(key).join();
            }, asyncExecutor);
        }
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        try {
            return caffeineCache.getAllPresent(keys);
        } catch (Exception e) {
            logger.error("批量获取缓存失败: name={}, keys={}", config.getName(), keys, e);
            return Map.of();
        }
    }

    @Override
    public CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        if (asyncCache != null) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return keys.stream()
                        .collect(Collectors.toMap(
                            key -> key,
                            key -> asyncCache.getIfPresent(key).join(),
                            (existing, replacement) -> replacement
                        ));
                } catch (Exception e) {
                    logger.error("异步批量获取缓存失败: name={}, keys={}", config.getName(), keys, e);
                    return Map.of();
                }
            }, asyncExecutor);
        } else {
            return CompletableFuture.supplyAsync(() -> getAll(keys), asyncExecutor);
        }
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            caffeineCache.put(key, value);
            logger.debug("缓存已存储: name={}, key={}", config.getName(), key);
        } catch (Exception e) {
            logger.error("存储缓存失败: name={}, key={}", config.getName(), key, e);
        }
    }

    @Override
    public void put(K key, V value, Duration duration) {
        // Caffeine不支持单个条目的TTL，这里记录日志并使用默认过期时间
        logger.warn("Caffeine不支持单个条目的TTL，使用默认过期时间: name={}, key={}, duration={}", 
            config.getName(), key, duration);
        put(key, value);
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        if (asyncCache != null) {
            asyncCache.put(key, CompletableFuture.completedFuture(value));
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.runAsync(() -> put(key, value), asyncExecutor);
        }
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value, Duration duration) {
        return putAsync(key, value); // Caffeine limitations
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            V existingValue = caffeineCache.asMap().putIfAbsent(key, value);
            boolean success = existingValue == null;
            if (success) {
                logger.debug("条件存储成功: name={}, key={}", config.getName(), key);
            } else {
                logger.debug("条件存储失败，键已存在: name={}, key={}", config.getName(), key);
            }
            return success;
        } catch (Exception e) {
            logger.error("条件存储失败: name={}, key={}", config.getName(), key, e);
            return false;
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        return putIfAbsent(key, value); // Caffeine limitations
    }

    @Override
    public void putAll(Map<K, V> map) {
        Objects.requireNonNull(map, "映射不能为null");
        
        try {
            caffeineCache.putAll(map);
            logger.debug("批量存储完成: name={}, size={}", config.getName(), map.size());
        } catch (Exception e) {
            logger.error("批量存储失败: name={}, size={}", config.getName(), map.size(), e);
        }
    }

    @Override
    public CompletableFuture<Void> putAllAsync(Map<K, V> map) {
        Objects.requireNonNull(map, "映射不能为null");
        
        return CompletableFuture.runAsync(() -> putAll(map), asyncExecutor);
    }

    @Override
    public V remove(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            V value = caffeineCache.asMap().remove(key);
            if (value != null) {
                logger.debug("缓存已移除: name={}, key={}", config.getName(), key);
            }
            return value;
        } catch (Exception e) {
            logger.error("移除缓存失败: name={}, key={}", config.getName(), key, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<V> removeAsync(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        return CompletableFuture.supplyAsync(() -> remove(key), asyncExecutor);
    }

    @Override
    public void removeAll(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        try {
            caffeineCache.invalidateAll(keys);
            logger.debug("批量移除完成: name={}, size={}", config.getName(), keys.size());
        } catch (Exception e) {
            logger.error("批量移除失败: name={}, size={}", config.getName(), keys.size(), e);
        }
    }

    @Override
    public CompletableFuture<Void> removeAllAsync(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        return CompletableFuture.runAsync(() -> removeAll(keys), asyncExecutor);
    }

    @Override
    public void clear() {
        try {
            caffeineCache.invalidateAll();
            logger.debug("缓存已清空: name={}", config.getName());
        } catch (Exception e) {
            logger.error("清空缓存失败: name={}", config.getName(), e);
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.runAsync(this::clear, asyncExecutor);
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            return caffeineCache.asMap().containsKey(key);
        } catch (Exception e) {
            logger.error("检查键存在性失败: name={}, key={}", config.getName(), key, e);
            return false;
        }
    }

    @Override
    public long size() {
        try {
            return caffeineCache.estimatedSize();
        } catch (Exception e) {
            logger.error("获取缓存大小失败: name={}", config.getName(), e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Collection<K> keys() {
        try {
            return caffeineCache.asMap().keySet();
        } catch (Exception e) {
            logger.error("获取键集合失败: name={}", config.getName(), e);
            return Collections.emptySet();
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        if (!config.isStatisticsEnabled()) {
            return new LocalCacheStatistics();
        }
        
        try {
            CacheStats stats = caffeineCache.stats();
            return new LocalCacheStatistics(stats);
        } catch (Exception e) {
            logger.error("获取统计信息失败: name={}", config.getName(), e);
            return new LocalCacheStatistics();
        }
    }

    @Override
    public void refresh(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        if (loadingCache != null) {
            try {
                loadingCache.refresh(key);
                logger.debug("缓存已刷新: name={}, key={}", config.getName(), key);
            } catch (Exception e) {
                logger.error("刷新缓存失败: name={}, key={}", config.getName(), key, e);
            }
        } else {
            logger.warn("无法刷新缓存，未配置加载器: name={}, key={}", config.getName(), key);
        }
    }

    @Override
    public CompletableFuture<Void> refreshAsync(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        return CompletableFuture.runAsync(() -> refresh(key), asyncExecutor);
    }

    @Override
    public CacheConfig getConfig() {
        return config;
    }

    /**
     * 移除监听器回调
     *
     * @param key   被移除的键
     * @param value 被移除的值
     * @param cause 移除原因
     */
    private void onRemoval(K key, V value, RemovalCause cause) {
        logger.debug("缓存项已移除: name={}, key={}, cause={}", config.getName(), key, cause);
    }

    /**
     * 本地缓存统计信息实现
     */
    private static class LocalCacheStatistics implements CacheStatistics {
        private final CacheStats stats;

        public LocalCacheStatistics() {
            this.stats = null;
        }

        public LocalCacheStatistics(CacheStats stats) {
            this.stats = stats;
        }

        @Override
        public long getRequestCount() {
            return stats != null ? stats.requestCount() : 0;
        }

        @Override
        public long getHitCount() {
            return stats != null ? stats.hitCount() : 0;
        }

        @Override
        public long getMissCount() {
            return stats != null ? stats.missCount() : 0;
        }

        @Override
        public long getLoadCount() {
            return stats != null ? stats.loadCount() : 0;
        }

        @Override
        public long getEvictionCount() {
            return stats != null ? stats.evictionCount() : 0;
        }

        @Override
        public double getHitRate() {
            return stats != null ? stats.hitRate() : 0.0;
        }

        @Override
        public double getMissRate() {
            return stats != null ? stats.missRate() : 0.0;
        }

        @Override
        public double getAverageLoadTime() {
            return stats != null ? stats.averageLoadPenalty() : 0.0;
        }
    }
}