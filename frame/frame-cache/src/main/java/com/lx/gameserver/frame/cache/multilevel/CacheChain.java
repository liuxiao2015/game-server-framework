/*
 * 文件名: CacheChain.java
 * 用途: 缓存链
 * 实现内容:
 *   - 责任链模式实现多级缓存
 *   - 缓存查找顺序管理
 *   - 回填机制实现
 *   - 失效传播处理
 *   - 链路监控支持
 * 技术选型:
 *   - Java 17 责任链模式
 *   - CompletableFuture异步支持
 *   - 策略模式回填机制
 *   - 监控指标收集
 * 依赖关系:
 *   - 被MultiLevelCache使用
 *   - 管理多个Cache实例
 *   - 与监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.multilevel;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheKey;
import com.lx.gameserver.frame.cache.monitor.CacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 缓存链
 * <p>
 * 基于责任链模式实现的多级缓存管理，支持缓存查找顺序控制、
 * 自动回填机制、失效传播等功能。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheChain<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(CacheChain.class);

    /**
     * 缓存链节点
     */
    private final List<CacheLevel<K, V>> cacheLevels;

    /**
     * 回填策略
     */
    private final BackfillStrategy backfillStrategy;

    /**
     * 是否启用回填
     */
    private final boolean backfillEnabled;

    /**
     * 是否启用失效传播
     */
    private final boolean evictionPropagationEnabled;

    /**
     * 链路监控
     */
    private final CacheMetrics metrics;

    /**
     * 统计信息
     */
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong l1Hits = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong backfills = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param cacheLevels 缓存级别列表
     * @param config      配置
     */
    public CacheChain(List<Cache<K, V>> cacheLevels, CacheChainConfig config) {
        this.cacheLevels = new ArrayList<>();
        for (int i = 0; i < cacheLevels.size(); i++) {
            this.cacheLevels.add(new CacheLevel<>(i, cacheLevels.get(i)));
        }
        
        this.backfillStrategy = config.getBackfillStrategy();
        this.backfillEnabled = config.isBackfillEnabled();
        this.evictionPropagationEnabled = config.isEvictionPropagationEnabled();
        this.metrics = config.getMetrics();
        
        logger.info("初始化缓存链，级别数: {}, 回填策略: {}", 
            cacheLevels.size(), backfillStrategy.getName());
    }

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值
     */
    public V get(K key) {
        totalRequests.incrementAndGet();
        
        for (CacheLevel<K, V> level : cacheLevels) {
            V value = level.getCache().get(key);
            if (value != null) {
                // 记录命中
                recordHit(level.getLevel());
                
                // 执行回填
                if (backfillEnabled && level.getLevel() > 0) {
                    backfillToHigherLevels(key, value, level.getLevel());
                }
                
                return value;
            }
        }
        
        // 记录未命中
        misses.incrementAndGet();
        return null;
    }

    /**
     * 异步获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值的异步结果
     */
    public CompletableFuture<V> getAsync(K key) {
        totalRequests.incrementAndGet();
        
        return getFromLevelAsync(key, 0);
    }

    /**
     * 从指定级别开始异步获取
     */
    private CompletableFuture<V> getFromLevelAsync(K key, int levelIndex) {
        if (levelIndex >= cacheLevels.size()) {
            misses.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
        
        CacheLevel<K, V> level = cacheLevels.get(levelIndex);
        return level.getCache().getAsync(key)
            .thenCompose(value -> {
                if (value != null) {
                    // 记录命中
                    recordHit(level.getLevel());
                    
                    // 执行回填
                    if (backfillEnabled && level.getLevel() > 0) {
                        backfillToHigherLevelsAsync(key, value, level.getLevel());
                    }
                    
                    return CompletableFuture.completedFuture(value);
                } else {
                    // 尝试下一级
                    return getFromLevelAsync(key, levelIndex + 1);
                }
            });
    }

    /**
     * 获取缓存值，如果不存在则加载
     *
     * @param key    缓存键
     * @param loader 加载器
     * @return 缓存值
     */
    public V get(K key, Function<K, V> loader) {
        V value = get(key);
        if (value != null) {
            return value;
        }
        
        // 加载数据
        value = loader.apply(key);
        if (value != null) {
            // 存储到所有级别
            putToAllLevels(key, value);
        }
        
        return value;
    }

    /**
     * 存储缓存值到所有级别
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void put(K key, V value) {
        putToAllLevels(key, value);
    }

    /**
     * 存储缓存值到所有级别并指定过期时间
     *
     * @param key      缓存键
     * @param value    缓存值
     * @param duration 过期时间
     */
    public void put(K key, V value, Duration duration) {
        for (CacheLevel<K, V> level : cacheLevels) {
            level.getCache().put(key, value, duration);
        }
    }

    /**
     * 移除缓存项
     *
     * @param key 缓存键
     * @return 被移除的值
     */
    public V remove(K key) {
        V result = null;
        
        // 从所有级别移除
        for (CacheLevel<K, V> level : cacheLevels) {
            V value = level.getCache().remove(key);
            if (result == null && value != null) {
                result = value;
            }
        }
        
        return result;
    }

    /**
     * 清空所有级别的缓存
     */
    public void clear() {
        for (CacheLevel<K, V> level : cacheLevels) {
            level.getCache().clear();
        }
    }

    /**
     * 获取链路统计信息
     *
     * @return 统计信息
     */
    public ChainStatistics getStatistics() {
        return new ChainStatistics(
            totalRequests.get(),
            l1Hits.get(),
            l2Hits.get(),
            misses.get(),
            backfills.get()
        );
    }

    /**
     * 存储到所有级别
     */
    private void putToAllLevels(K key, V value) {
        for (CacheLevel<K, V> level : cacheLevels) {
            level.getCache().put(key, value);
        }
    }

    /**
     * 回填到更高级别
     */
    private void backfillToHigherLevels(K key, V value, int fromLevel) {
        if (!backfillStrategy.shouldBackfill(key, value, fromLevel)) {
            return;
        }
        
        for (int i = 0; i < fromLevel; i++) {
            CacheLevel<K, V> level = cacheLevels.get(i);
            level.getCache().put(key, value);
        }
        
        backfills.incrementAndGet();
    }

    /**
     * 异步回填到更高级别
     */
    private void backfillToHigherLevelsAsync(K key, V value, int fromLevel) {
        if (!backfillStrategy.shouldBackfill(key, value, fromLevel)) {
            return;
        }
        
        CompletableFuture<Void>[] futures = new CompletableFuture[fromLevel];
        for (int i = 0; i < fromLevel; i++) {
            CacheLevel<K, V> level = cacheLevels.get(i);
            futures[i] = level.getCache().putAsync(key, value);
        }
        
        CompletableFuture.allOf(futures)
            .whenComplete((result, throwable) -> {
                if (throwable == null) {
                    backfills.incrementAndGet();
                } else {
                    logger.warn("异步回填失败: {}", throwable.getMessage());
                }
            });
    }

    /**
     * 记录命中
     */
    private void recordHit(int level) {
        switch (level) {
            case 0:
                l1Hits.incrementAndGet();
                break;
            case 1:
                l2Hits.incrementAndGet();
                break;
            default:
                // 其他级别暂不单独统计
                break;
        }
    }

    /**
     * 缓存级别
     */
    private static class CacheLevel<K, V> {
        private final int level;
        private final Cache<K, V> cache;

        public CacheLevel(int level, Cache<K, V> cache) {
            this.level = level;
            this.cache = cache;
        }

        public int getLevel() {
            return level;
        }

        public Cache<K, V> getCache() {
            return cache;
        }
    }

    /**
     * 回填策略接口
     */
    public interface BackfillStrategy {
        /**
         * 判断是否应该回填
         *
         * @param key       缓存键
         * @param value     缓存值
         * @param fromLevel 来源级别
         * @return 是否回填
         */
        boolean shouldBackfill(Object key, Object value, int fromLevel);

        /**
         * 获取策略名称
         *
         * @return 策略名称
         */
        String getName();
    }

    /**
     * 默认回填策略
     */
    public static class DefaultBackfillStrategy implements BackfillStrategy {
        @Override
        public boolean shouldBackfill(Object key, Object value, int fromLevel) {
            return true; // 总是回填
        }

        @Override
        public String getName() {
            return "DEFAULT";
        }
    }

    /**
     * 链路统计信息
     */
    public static class ChainStatistics {
        private final long totalRequests;
        private final long l1Hits;
        private final long l2Hits;
        private final long misses;
        private final long backfills;

        public ChainStatistics(long totalRequests, long l1Hits, long l2Hits, 
                             long misses, long backfills) {
            this.totalRequests = totalRequests;
            this.l1Hits = l1Hits;
            this.l2Hits = l2Hits;
            this.misses = misses;
            this.backfills = backfills;
        }

        public long getTotalRequests() { return totalRequests; }
        public long getL1Hits() { return l1Hits; }
        public long getL2Hits() { return l2Hits; }
        public long getMisses() { return misses; }
        public long getBackfills() { return backfills; }
        
        public double getL1HitRate() {
            return totalRequests > 0 ? (double) l1Hits / totalRequests : 0.0;
        }
        
        public double getL2HitRate() {
            return totalRequests > 0 ? (double) l2Hits / totalRequests : 0.0;
        }
        
        public double getMissRate() {
            return totalRequests > 0 ? (double) misses / totalRequests : 0.0;
        }
    }

    /**
     * 缓存链配置
     */
    public static class CacheChainConfig {
        private BackfillStrategy backfillStrategy = new DefaultBackfillStrategy();
        private boolean backfillEnabled = true;
        private boolean evictionPropagationEnabled = true;
        private CacheMetrics metrics;

        public BackfillStrategy getBackfillStrategy() { return backfillStrategy; }
        public void setBackfillStrategy(BackfillStrategy backfillStrategy) { 
            this.backfillStrategy = backfillStrategy; 
        }

        public boolean isBackfillEnabled() { return backfillEnabled; }
        public void setBackfillEnabled(boolean backfillEnabled) { 
            this.backfillEnabled = backfillEnabled; 
        }

        public boolean isEvictionPropagationEnabled() { return evictionPropagationEnabled; }
        public void setEvictionPropagationEnabled(boolean evictionPropagationEnabled) { 
            this.evictionPropagationEnabled = evictionPropagationEnabled; 
        }

        public CacheMetrics getMetrics() { return metrics; }
        public void setMetrics(CacheMetrics metrics) { this.metrics = metrics; }
    }
}