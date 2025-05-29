/*
 * 文件名: MultiLevelCache.java
 * 用途: 多级缓存实现
 * 实现内容:
 *   - L1本地缓存和L2分布式缓存组合
 *   - 缓存穿透处理和级联更新
 *   - 一致性保证和数据同步
 *   - 智能路由和性能优化
 *   - 统计信息聚合
 * 技术选型:
 *   - 组合模式设计
 *   - 异步操作支持
 *   - 事件驱动同步
 *   - 性能监控
 * 依赖关系:
 *   - 实现Cache接口
 *   - 组合LocalCache和RedisCache
 *   - 提供多级缓存功能
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.multilevel;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 多级缓存实现
 * <p>
 * 实现多级缓存架构，通常包含L1本地缓存和L2分布式缓存。
 * 提供智能的缓存路由、数据一致性保证和性能优化功能。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class MultiLevelCache<K, V> implements Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(MultiLevelCache.class);

    /**
     * 缓存配置
     */
    private final CacheConfig config;

    /**
     * L1缓存（本地缓存）
     */
    private final Cache<K, V> l1Cache;

    /**
     * L2缓存（分布式缓存）
     */
    private final Cache<K, V> l2Cache;

    /**
     * 多级缓存配置
     */
    private final MultiLevelCacheConfig multiConfig;

    /**
     * 异步执行器
     */
    private final Executor asyncExecutor;

    /**
     * 统计信息
     */
    private final MultiLevelCacheStatistics statistics;

    /**
     * 构造函数
     *
     * @param config      缓存配置
     * @param l1Cache     L1缓存
     * @param l2Cache     L2缓存
     * @param multiConfig 多级缓存配置
     */
    public MultiLevelCache(CacheConfig config,
                          Cache<K, V> l1Cache,
                          Cache<K, V> l2Cache,
                          MultiLevelCacheConfig multiConfig) {
        this.config = Objects.requireNonNull(config, "缓存配置不能为null");
        this.l1Cache = Objects.requireNonNull(l1Cache, "L1缓存不能为null");
        this.l2Cache = Objects.requireNonNull(l2Cache, "L2缓存不能为null");
        this.multiConfig = Objects.requireNonNull(multiConfig, "多级缓存配置不能为null");
        this.asyncExecutor = ForkJoinPool.commonPool();
        this.statistics = new MultiLevelCacheStatistics();

        logger.info("多级缓存已创建: name={}, l1={}, l2={}, config={}", 
            config.getName(), l1Cache.getName(), l2Cache.getName(), multiConfig);
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        statistics.recordRequest();
        
        // 首先尝试L1缓存
        V value = l1Cache.get(key);
        if (value != null) {
            statistics.recordL1Hit();
            logger.debug("L1缓存命中: name={}, key={}", config.getName(), key);
            return value;
        }
        
        statistics.recordL1Miss();
        
        // L1未命中，尝试L2缓存
        value = l2Cache.get(key);
        if (value != null) {
            statistics.recordL2Hit();
            logger.debug("L2缓存命中: name={}, key={}", config.getName(), key);
            
            // 异步回填L1缓存
            if (multiConfig.isL1BackfillEnabled()) {
                asyncBackfillL1(key, value);
            }
            
            return value;
        }
        
        statistics.recordL2Miss();
        logger.debug("缓存完全未命中: name={}, key={}", config.getName(), key);
        
        return null;
    }

    @Override
    public CompletableFuture<V> getAsync(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        return CompletableFuture.supplyAsync(() -> {
            statistics.recordRequest();
            
            // 尝试L1缓存
            V value = l1Cache.get(key);
            if (value != null) {
                statistics.recordL1Hit();
                return value;
            }
            
            statistics.recordL1Miss();
            return null;
        }, asyncExecutor)
        .thenCompose(l1Value -> {
            if (l1Value != null) {
                return CompletableFuture.completedFuture(l1Value);
            }
            
            // L1未命中，异步尝试L2
            return l2Cache.getAsync(key).thenApply(l2Value -> {
                if (l2Value != null) {
                    statistics.recordL2Hit();
                    
                    // 异步回填L1
                    if (multiConfig.isL1BackfillEnabled()) {
                        asyncBackfillL1(key, l2Value);
                    }
                } else {
                    statistics.recordL2Miss();
                }
                
                return l2Value;
            });
        });
    }

    @Override
    public V get(K key, Function<K, V> loader) {
        V value = get(key);
        if (value != null) {
            return value;
        }

        try {
            value = loader.apply(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        } catch (Exception e) {
            logger.error("多级缓存加载失败: name={}, key={}", config.getName(), key, e);
            throw new RuntimeException("多级缓存加载失败", e);
        }
    }

    @Override
    public CompletableFuture<V> getAsync(K key, Function<K, CompletableFuture<V>> loader) {
        return getAsync(key).thenCompose(value -> {
            if (value != null) {
                return CompletableFuture.completedFuture(value);
            }
            
            return loader.apply(key).thenApply(loadedValue -> {
                if (loadedValue != null) {
                    putAsync(key, loadedValue);
                }
                return loadedValue;
            });
        });
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        if (keys.isEmpty()) {
            return Map.of();
        }

        statistics.recordRequest(keys.size());
        
        // 首先从L1批量获取
        Map<K, V> l1Results = l1Cache.getAll(keys);
        statistics.recordL1Hit(l1Results.size());
        statistics.recordL1Miss(keys.size() - l1Results.size());
        
        if (l1Results.size() == keys.size()) {
            // L1完全命中
            return l1Results;
        }
        
        // 找出L1未命中的键
        Collection<K> l1MissedKeys = keys.stream()
            .filter(key -> !l1Results.containsKey(key))
            .toList();
        
        // 从L2获取未命中的键
        Map<K, V> l2Results = l2Cache.getAll(l1MissedKeys);
        statistics.recordL2Hit(l2Results.size());
        statistics.recordL2Miss(l1MissedKeys.size() - l2Results.size());
        
        // 合并结果
        Map<K, V> finalResults = new HashMap<>(l1Results);
        finalResults.putAll(l2Results);
        
        // 异步回填L1
        if (multiConfig.isL1BackfillEnabled() && !l2Results.isEmpty()) {
            asyncBackfillL1Batch(l2Results);
        }
        
        logger.debug("多级缓存批量获取完成: name={}, total={}, l1Hits={}, l2Hits={}", 
            config.getName(), keys.size(), l1Results.size(), l2Results.size());
        
        return finalResults;
    }

    @Override
    public CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys) {
        return CompletableFuture.supplyAsync(() -> getAll(keys), asyncExecutor);
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            // 同时写入L1和L2
            if (multiConfig.isL1Enabled()) {
                l1Cache.put(key, value);
            }
            
            if (multiConfig.isL2Enabled()) {
                l2Cache.put(key, value);
            }
            
            logger.debug("多级缓存已存储: name={}, key={}", config.getName(), key);
        } catch (Exception e) {
            logger.error("多级缓存存储失败: name={}, key={}", config.getName(), key, e);
        }
    }

    @Override
    public void put(K key, V value, Duration duration) {
        Objects.requireNonNull(key, "键不能为null");
        Objects.requireNonNull(duration, "过期时间不能为null");
        
        try {
            if (multiConfig.isL1Enabled()) {
                l1Cache.put(key, value, duration);
            }
            
            if (multiConfig.isL2Enabled()) {
                l2Cache.put(key, value, duration);
            }
            
            logger.debug("多级缓存已存储（带过期时间）: name={}, key={}, duration={}", 
                config.getName(), key, duration);
        } catch (Exception e) {
            logger.error("多级缓存存储失败: name={}, key={}, duration={}", 
                config.getName(), key, duration, e);
        }
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value) {
        return CompletableFuture.allOf(
            multiConfig.isL1Enabled() ? l1Cache.putAsync(key, value) : CompletableFuture.completedFuture(null),
            multiConfig.isL2Enabled() ? l2Cache.putAsync(key, value) : CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value, Duration duration) {
        return CompletableFuture.allOf(
            multiConfig.isL1Enabled() ? l1Cache.putAsync(key, value, duration) : CompletableFuture.completedFuture(null),
            multiConfig.isL2Enabled() ? l2Cache.putAsync(key, value, duration) : CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            // 先检查L2缓存（权威缓存）
            boolean l2Success = multiConfig.isL2Enabled() ? l2Cache.putIfAbsent(key, value) : true;
            
            if (l2Success && multiConfig.isL1Enabled()) {
                l1Cache.putIfAbsent(key, value);
            }
            
            boolean success = l2Success;
            if (success) {
                logger.debug("多级缓存条件存储成功: name={}, key={}", config.getName(), key);
            } else {
                logger.debug("多级缓存条件存储失败，键已存在: name={}, key={}", config.getName(), key);
            }
            
            return success;
        } catch (Exception e) {
            logger.error("多级缓存条件存储失败: name={}, key={}", config.getName(), key, e);
            return false;
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        Objects.requireNonNull(key, "键不能为null");
        Objects.requireNonNull(duration, "过期时间不能为null");
        
        try {
            boolean l2Success = multiConfig.isL2Enabled() ? l2Cache.putIfAbsent(key, value, duration) : true;
            
            if (l2Success && multiConfig.isL1Enabled()) {
                l1Cache.putIfAbsent(key, value, duration);
            }
            
            return l2Success;
        } catch (Exception e) {
            logger.error("多级缓存条件存储失败: name={}, key={}, duration={}", 
                config.getName(), key, duration, e);
            return false;
        }
    }

    @Override
    public void putAll(Map<K, V> map) {
        Objects.requireNonNull(map, "映射不能为null");
        
        if (map.isEmpty()) {
            return;
        }

        try {
            if (multiConfig.isL1Enabled()) {
                l1Cache.putAll(map);
            }
            
            if (multiConfig.isL2Enabled()) {
                l2Cache.putAll(map);
            }
            
            logger.debug("多级缓存批量存储完成: name={}, size={}", config.getName(), map.size());
        } catch (Exception e) {
            logger.error("多级缓存批量存储失败: name={}, size={}", config.getName(), map.size(), e);
        }
    }

    @Override
    public CompletableFuture<Void> putAllAsync(Map<K, V> map) {
        return CompletableFuture.allOf(
            multiConfig.isL1Enabled() ? l1Cache.putAllAsync(map) : CompletableFuture.completedFuture(null),
            multiConfig.isL2Enabled() ? l2Cache.putAllAsync(map) : CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public V remove(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            // 先从L1获取值（如果需要返回）
            V value = multiConfig.isL1Enabled() ? l1Cache.get(key) : null;
            if (value == null && multiConfig.isL2Enabled()) {
                value = l2Cache.get(key);
            }
            
            // 从两级缓存中移除
            if (multiConfig.isL1Enabled()) {
                l1Cache.remove(key);
            }
            
            if (multiConfig.isL2Enabled()) {
                l2Cache.remove(key);
            }
            
            if (value != null) {
                logger.debug("多级缓存已移除: name={}, key={}", config.getName(), key);
            }
            
            return value;
        } catch (Exception e) {
            logger.error("多级缓存移除失败: name={}, key={}", config.getName(), key, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<V> removeAsync(K key) {
        return CompletableFuture.supplyAsync(() -> remove(key), asyncExecutor);
    }

    @Override
    public void removeAll(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        if (keys.isEmpty()) {
            return;
        }

        try {
            if (multiConfig.isL1Enabled()) {
                l1Cache.removeAll(keys);
            }
            
            if (multiConfig.isL2Enabled()) {
                l2Cache.removeAll(keys);
            }
            
            logger.debug("多级缓存批量移除完成: name={}, size={}", config.getName(), keys.size());
        } catch (Exception e) {
            logger.error("多级缓存批量移除失败: name={}, size={}", config.getName(), keys.size(), e);
        }
    }

    @Override
    public CompletableFuture<Void> removeAllAsync(Collection<K> keys) {
        return CompletableFuture.allOf(
            multiConfig.isL1Enabled() ? l1Cache.removeAllAsync(keys) : CompletableFuture.completedFuture(null),
            multiConfig.isL2Enabled() ? l2Cache.removeAllAsync(keys) : CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public void clear() {
        try {
            if (multiConfig.isL1Enabled()) {
                l1Cache.clear();
            }
            
            if (multiConfig.isL2Enabled()) {
                l2Cache.clear();
            }
            
            logger.debug("多级缓存已清空: name={}", config.getName());
        } catch (Exception e) {
            logger.error("多级缓存清空失败: name={}", config.getName(), e);
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.allOf(
            multiConfig.isL1Enabled() ? l1Cache.clearAsync() : CompletableFuture.completedFuture(null),
            multiConfig.isL2Enabled() ? l2Cache.clearAsync() : CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            if (multiConfig.isL1Enabled() && l1Cache.containsKey(key)) {
                return true;
            }
            
            if (multiConfig.isL2Enabled() && l2Cache.containsKey(key)) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("多级缓存检查键存在性失败: name={}, key={}", config.getName(), key, e);
            return false;
        }
    }

    @Override
    public long size() {
        try {
            // 返回L2缓存的大小（权威大小）
            if (multiConfig.isL2Enabled()) {
                return l2Cache.size();
            } else if (multiConfig.isL1Enabled()) {
                return l1Cache.size();
            }
            return 0;
        } catch (Exception e) {
            logger.error("获取多级缓存大小失败: name={}", config.getName(), e);
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
            // 返回L2缓存的键集合（权威键集合）
            if (multiConfig.isL2Enabled()) {
                return l2Cache.keys();
            } else if (multiConfig.isL1Enabled()) {
                return l1Cache.keys();
            }
            return java.util.Collections.emptySet();
        } catch (Exception e) {
            logger.error("获取多级缓存键集合失败: name={}", config.getName(), e);
            return java.util.Collections.emptySet();
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void refresh(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        if (multiConfig.isL1Enabled()) {
            l1Cache.refresh(key);
        }
        
        if (multiConfig.isL2Enabled()) {
            l2Cache.refresh(key);
        }
        
        logger.debug("多级缓存已刷新: name={}, key={}", config.getName(), key);
    }

    @Override
    public CompletableFuture<Void> refreshAsync(K key) {
        return CompletableFuture.allOf(
            multiConfig.isL1Enabled() ? l1Cache.refreshAsync(key) : CompletableFuture.completedFuture(null),
            multiConfig.isL2Enabled() ? l2Cache.refreshAsync(key) : CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public CacheConfig getConfig() {
        return config;
    }

    /**
     * 获取L1缓存
     *
     * @return L1缓存
     */
    public Cache<K, V> getL1Cache() {
        return l1Cache;
    }

    /**
     * 获取L2缓存
     *
     * @return L2缓存
     */
    public Cache<K, V> getL2Cache() {
        return l2Cache;
    }

    /**
     * 获取多级缓存配置
     *
     * @return 多级缓存配置
     */
    public MultiLevelCacheConfig getMultiConfig() {
        return multiConfig;
    }

    /**
     * 异步回填L1缓存
     */
    private void asyncBackfillL1(K key, V value) {
        CompletableFuture.runAsync(() -> {
            try {
                l1Cache.put(key, value);
                logger.debug("L1缓存回填完成: name={}, key={}", config.getName(), key);
            } catch (Exception e) {
                logger.warn("L1缓存回填失败: name={}, key={}", config.getName(), key, e);
            }
        }, asyncExecutor);
    }

    /**
     * 异步批量回填L1缓存
     */
    private void asyncBackfillL1Batch(Map<K, V> data) {
        CompletableFuture.runAsync(() -> {
            try {
                l1Cache.putAll(data);
                logger.debug("L1缓存批量回填完成: name={}, size={}", config.getName(), data.size());
            } catch (Exception e) {
                logger.warn("L1缓存批量回填失败: name={}, size={}", config.getName(), data.size(), e);
            }
        }, asyncExecutor);
    }

    /**
     * 多级缓存配置
     */
    public static class MultiLevelCacheConfig {
        private boolean l1Enabled = true;
        private boolean l2Enabled = true;
        private boolean l1BackfillEnabled = true;
        private Duration backfillDelay = Duration.ofMillis(100);

        public boolean isL1Enabled() { return l1Enabled; }
        public void setL1Enabled(boolean l1Enabled) { this.l1Enabled = l1Enabled; }
        
        public boolean isL2Enabled() { return l2Enabled; }
        public void setL2Enabled(boolean l2Enabled) { this.l2Enabled = l2Enabled; }
        
        public boolean isL1BackfillEnabled() { return l1BackfillEnabled; }
        public void setL1BackfillEnabled(boolean l1BackfillEnabled) { this.l1BackfillEnabled = l1BackfillEnabled; }
        
        public Duration getBackfillDelay() { return backfillDelay; }
        public void setBackfillDelay(Duration backfillDelay) { this.backfillDelay = backfillDelay; }

        @Override
        public String toString() {
            return String.format("MultiLevelCacheConfig{l1Enabled=%s, l2Enabled=%s, l1BackfillEnabled=%s, backfillDelay=%s}", 
                l1Enabled, l2Enabled, l1BackfillEnabled, backfillDelay);
        }
    }

    /**
     * 多级缓存统计信息实现
     */
    private static class MultiLevelCacheStatistics implements CacheStatistics {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong l1HitCount = new AtomicLong(0);
        private final AtomicLong l1MissCount = new AtomicLong(0);
        private final AtomicLong l2HitCount = new AtomicLong(0);
        private final AtomicLong l2MissCount = new AtomicLong(0);

        public void recordRequest() {
            requestCount.incrementAndGet();
        }

        public void recordRequest(int count) {
            requestCount.addAndGet(count);
        }

        public void recordL1Hit() {
            l1HitCount.incrementAndGet();
        }

        public void recordL1Hit(int count) {
            l1HitCount.addAndGet(count);
        }

        public void recordL1Miss() {
            l1MissCount.incrementAndGet();
        }

        public void recordL1Miss(int count) {
            l1MissCount.addAndGet(count);
        }

        public void recordL2Hit() {
            l2HitCount.incrementAndGet();
        }

        public void recordL2Hit(int count) {
            l2HitCount.addAndGet(count);
        }

        public void recordL2Miss() {
            l2MissCount.incrementAndGet();
        }

        public void recordL2Miss(int count) {
            l2MissCount.addAndGet(count);
        }

        @Override
        public long getRequestCount() {
            return requestCount.get();
        }

        @Override
        public long getHitCount() {
            return l1HitCount.get() + l2HitCount.get();
        }

        @Override
        public long getMissCount() {
            return l2MissCount.get(); // 只有L2也未命中才算真正的miss
        }

        @Override
        public long getLoadCount() {
            return 0; // 暂不实现
        }

        @Override
        public long getEvictionCount() {
            return 0; // 暂不实现
        }

        @Override
        public double getHitRate() {
            long requests = getRequestCount();
            return requests == 0 ? 0.0 : (double) getHitCount() / requests;
        }

        @Override
        public double getMissRate() {
            long requests = getRequestCount();
            return requests == 0 ? 0.0 : (double) getMissCount() / requests;
        }

        @Override
        public double getAverageLoadTime() {
            return 0.0; // 暂不实现
        }

        /**
         * 获取L1命中次数
         */
        public long getL1HitCount() {
            return l1HitCount.get();
        }

        /**
         * 获取L1未命中次数
         */
        public long getL1MissCount() {
            return l1MissCount.get();
        }

        /**
         * 获取L2命中次数
         */
        public long getL2HitCount() {
            return l2HitCount.get();
        }

        /**
         * 获取L2未命中次数
         */
        public long getL2MissCount() {
            return l2MissCount.get();
        }

        /**
         * 获取L1命中率
         */
        public double getL1HitRate() {
            long requests = getRequestCount();
            return requests == 0 ? 0.0 : (double) l1HitCount.get() / requests;
        }

        /**
         * 获取L2命中率
         */
        public double getL2HitRate() {
            long l1Misses = l1MissCount.get();
            return l1Misses == 0 ? 0.0 : (double) l2HitCount.get() / l1Misses;
        }
    }
}