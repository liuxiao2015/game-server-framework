/*
 * 文件名: LocalCacheManager.java
 * 用途: 本地缓存管理器
 * 实现内容:
 *   - 多个本地缓存实例的统一管理
 *   - 缓存的创建、销毁和生命周期管理
 *   - 全局配置和资源限制
 *   - 缓存监控和统计信息聚合
 *   - 线程安全的缓存操作
 * 技术选型:
 *   - ConcurrentHashMap保证线程安全
 *   - 单例模式管理全局实例
 *   - 资源管理和清理
 *   - 配置化管理
 * 依赖关系:
 *   - 管理LocalCache实例
 *   - 提供缓存工厂功能
 *   - 支持全局缓存配置
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.local;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 本地缓存管理器
 * <p>
 * 提供本地缓存实例的统一管理功能，包括缓存创建、销毁、监控等。
 * 支持全局配置管理和资源限制，确保缓存使用的规范性和安全性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LocalCacheManager {

    private static final Logger logger = LoggerFactory.getLogger(LocalCacheManager.class);

    /**
     * 单例实例
     */
    private static volatile LocalCacheManager instance;

    /**
     * 实例锁
     */
    private static final Object INSTANCE_LOCK = new Object();

    /**
     * 缓存实例映射
     */
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    /**
     * 缓存配置映射
     */
    private final Map<String, CacheConfig> configs = new ConcurrentHashMap<>();

    /**
     * 读写锁
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 管理器配置
     */
    private final LocalCacheManagerConfig managerConfig;

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 是否已关闭
     */
    private volatile boolean shutdown = false;

    /**
     * 私有构造函数
     */
    private LocalCacheManager() {
        this(LocalCacheManagerConfig.defaultConfig());
    }

    /**
     * 私有构造函数
     *
     * @param config 管理器配置
     */
    private LocalCacheManager(LocalCacheManagerConfig config) {
        this.managerConfig = Objects.requireNonNull(config, "管理器配置不能为null");
        logger.info("本地缓存管理器已创建: {}", config);
    }

    /**
     * 获取单例实例
     *
     * @return 缓存管理器实例
     */
    public static LocalCacheManager getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new LocalCacheManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取单例实例
     *
     * @param config 管理器配置
     * @return 缓存管理器实例
     */
    public static LocalCacheManager getInstance(LocalCacheManagerConfig config) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new LocalCacheManager(config);
                }
            }
        }
        return instance;
    }

    /**
     * 启动管理器
     */
    public void start() {
        lock.writeLock().lock();
        try {
            if (started) {
                logger.warn("缓存管理器已经启动");
                return;
            }
            
            if (shutdown) {
                throw new IllegalStateException("缓存管理器已关闭，无法重新启动");
            }

            // 启动监控任务
            if (managerConfig.isMonitoringEnabled()) {
                startMonitoring();
            }

            // 启动清理任务
            if (managerConfig.isCleanupEnabled()) {
                startCleanup();
            }

            started = true;
            logger.info("本地缓存管理器已启动");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        lock.writeLock().lock();
        try {
            if (shutdown) {
                logger.warn("缓存管理器已经关闭");
                return;
            }

            // 清除所有缓存
            clearAll();

            // 关闭定时任务
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            shutdown = true;
            started = false;
            logger.info("本地缓存管理器已关闭");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取缓存
     *
     * @param name 缓存名称
     * @param <K>  键类型
     * @param <V>  值类型
     * @return 缓存实例，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        Objects.requireNonNull(name, "缓存名称不能为null");
        
        lock.readLock().lock();
        try {
            return (Cache<K, V>) caches.get(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取缓存，如果不存在则创建
     *
     * @param name   缓存名称
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存实例
     */
    public <K, V> Cache<K, V> getOrCreateCache(String name, CacheConfig config) {
        Objects.requireNonNull(name, "缓存名称不能为null");
        Objects.requireNonNull(config, "缓存配置不能为null");
        
        Cache<K, V> cache = getCache(name);
        if (cache != null) {
            return cache;
        }

        lock.writeLock().lock();
        try {
            // 双重检查
            cache = getCache(name);
            if (cache != null) {
                return cache;
            }

            // 检查缓存数量限制
            if (managerConfig.getMaxCacheCount() > 0 && caches.size() >= managerConfig.getMaxCacheCount()) {
                throw new IllegalStateException("已达到最大缓存数量限制: " + managerConfig.getMaxCacheCount());
            }

            // 创建新缓存
            cache = createCache(config);
            caches.put(name, cache);
            configs.put(name, config);
            
            logger.info("缓存已创建: name={}, config={}", name, config);
            return cache;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取缓存，如果不存在则使用默认配置创建
     *
     * @param name 缓存名称
     * @param <K>  键类型
     * @param <V>  值类型
     * @return 缓存实例
     */
    public <K, V> Cache<K, V> getOrCreateCache(String name) {
        CacheConfig config = CacheConfig.defaultConfig(name);
        return getOrCreateCache(name, config);
    }

    /**
     * 移除缓存
     *
     * @param name 缓存名称
     * @return 如果移除成功返回true
     */
    public boolean removeCache(String name) {
        Objects.requireNonNull(name, "缓存名称不能为null");
        
        lock.writeLock().lock();
        try {
            Cache<?, ?> cache = caches.remove(name);
            configs.remove(name);
            
            if (cache != null) {
                // 清理缓存
                cache.clear();
                logger.info("缓存已移除: name={}", name);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 检查缓存是否存在
     *
     * @param name 缓存名称
     * @return 如果存在返回true
     */
    public boolean containsCache(String name) {
        Objects.requireNonNull(name, "缓存名称不能为null");
        
        lock.readLock().lock();
        try {
            return caches.containsKey(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有缓存名称
     *
     * @return 缓存名称集合
     */
    public Collection<String> getCacheNames() {
        lock.readLock().lock();
        try {
            return caches.keySet();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取缓存数量
     *
     * @return 缓存数量
     */
    public int getCacheCount() {
        lock.readLock().lock();
        try {
            return caches.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清除所有缓存内容
     */
    public void clearAll() {
        lock.readLock().lock();
        try {
            caches.values().forEach(Cache::clear);
            logger.info("所有缓存内容已清除");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 移除所有缓存
     */
    public void removeAll() {
        lock.writeLock().lock();
        try {
            caches.values().forEach(Cache::clear);
            caches.clear();
            configs.clear();
            logger.info("所有缓存已移除");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取管理器配置
     *
     * @return 管理器配置
     */
    public LocalCacheManagerConfig getManagerConfig() {
        return managerConfig;
    }

    /**
     * 是否已启动
     *
     * @return 如果已启动返回true
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 是否已关闭
     *
     * @return 如果已关闭返回true
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * 创建缓存实例
     *
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存实例
     */
    @SuppressWarnings("unchecked")
    private <K, V> Cache<K, V> createCache(CacheConfig config) {
        return (Cache<K, V>) new LocalCache<>(config);
    }

    /**
     * 启动监控任务
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                lock.readLock().lock();
                try {
                    for (Map.Entry<String, Cache<?, ?>> entry : caches.entrySet()) {
                        String name = entry.getKey();
                        Cache<?, ?> cache = entry.getValue();
                        Cache.CacheStatistics stats = cache.getStatistics();
                        
                        logger.debug("缓存统计: name={}, size={}, hitRate={}, requestCount={}", 
                            name, cache.size(), stats.getHitRate(), stats.getRequestCount());
                    }
                } finally {
                    lock.readLock().unlock();
                }
            } catch (Exception e) {
                logger.error("监控任务执行失败", e);
            }
        }, managerConfig.getMonitoringInterval().toSeconds(), 
           managerConfig.getMonitoringInterval().toSeconds(), TimeUnit.SECONDS);
        
        logger.info("缓存监控任务已启动，间隔: {}", managerConfig.getMonitoringInterval());
    }

    /**
     * 启动清理任务
     */
    private void startCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 这里可以添加清理逻辑，如清理过期统计数据等
                logger.debug("执行缓存清理任务");
            } catch (Exception e) {
                logger.error("清理任务执行失败", e);
            }
        }, managerConfig.getCleanupInterval().toSeconds(), 
           managerConfig.getCleanupInterval().toSeconds(), TimeUnit.SECONDS);
        
        logger.info("缓存清理任务已启动，间隔: {}", managerConfig.getCleanupInterval());
    }

    /**
     * 本地缓存管理器配置
     */
    public static class LocalCacheManagerConfig {
        private final int maxCacheCount;
        private final boolean monitoringEnabled;
        private final java.time.Duration monitoringInterval;
        private final boolean cleanupEnabled;
        private final java.time.Duration cleanupInterval;

        private LocalCacheManagerConfig(Builder builder) {
            this.maxCacheCount = builder.maxCacheCount;
            this.monitoringEnabled = builder.monitoringEnabled;
            this.monitoringInterval = builder.monitoringInterval;
            this.cleanupEnabled = builder.cleanupEnabled;
            this.cleanupInterval = builder.cleanupInterval;
        }

        /**
         * 默认配置
         */
        public static LocalCacheManagerConfig defaultConfig() {
            return builder().build();
        }

        /**
         * 创建构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        public int getMaxCacheCount() { return maxCacheCount; }
        public boolean isMonitoringEnabled() { return monitoringEnabled; }
        public java.time.Duration getMonitoringInterval() { return monitoringInterval; }
        public boolean isCleanupEnabled() { return cleanupEnabled; }
        public java.time.Duration getCleanupInterval() { return cleanupInterval; }

        @Override
        public String toString() {
            return String.format("LocalCacheManagerConfig{maxCacheCount=%d, monitoringEnabled=%s, monitoringInterval=%s, cleanupEnabled=%s, cleanupInterval=%s}", 
                maxCacheCount, monitoringEnabled, monitoringInterval, cleanupEnabled, cleanupInterval);
        }

        public static class Builder {
            private int maxCacheCount = 100;
            private boolean monitoringEnabled = true;
            private java.time.Duration monitoringInterval = java.time.Duration.ofMinutes(1);
            private boolean cleanupEnabled = true;
            private java.time.Duration cleanupInterval = java.time.Duration.ofMinutes(5);

            public Builder maxCacheCount(int maxCacheCount) {
                this.maxCacheCount = maxCacheCount;
                return this;
            }

            public Builder monitoringEnabled(boolean monitoringEnabled) {
                this.monitoringEnabled = monitoringEnabled;
                return this;
            }

            public Builder monitoringInterval(java.time.Duration monitoringInterval) {
                this.monitoringInterval = monitoringInterval;
                return this;
            }

            public Builder cleanupEnabled(boolean cleanupEnabled) {
                this.cleanupEnabled = cleanupEnabled;
                return this;
            }

            public Builder cleanupInterval(java.time.Duration cleanupInterval) {
                this.cleanupInterval = cleanupInterval;
                return this;
            }

            public LocalCacheManagerConfig build() {
                return new LocalCacheManagerConfig(this);
            }
        }
    }
}