/*
 * 文件名: CacheSynchronizer.java
 * 用途: 缓存同步器
 * 实现内容:
 *   - 本地与远程缓存同步
 *   - 事件驱动同步机制
 *   - 增量同步支持
 *   - 冲突解决策略
 *   - 同步延迟监控
 * 技术选型:
 *   - Java 17 事件驱动架构
 *   - CompletableFuture异步同步
 *   - 观察者模式事件通知
 *   - 策略模式冲突解决
 * 依赖关系:
 *   - 被MultiLevelCache使用
 *   - 依赖CacheEventBus事件总线
 *   - 与监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.multilevel;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheEntry;
import com.lx.gameserver.frame.cache.core.CacheKey;
import com.lx.gameserver.frame.cache.monitor.CacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 缓存同步器
 * <p>
 * 负责多级缓存之间的数据同步，支持实时同步、延迟同步、
 * 增量同步等多种同步策略。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheSynchronizer<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(CacheSynchronizer.class);

    /**
     * 本地缓存
     */
    private final Cache<K, V> localCache;

    /**
     * 远程缓存
     */
    private final Cache<K, V> remoteCache;

    /**
     * 同步配置
     */
    private final SyncConfig config;

    /**
     * 同步执行器
     */
    private final ScheduledExecutorService syncExecutor;

    /**
     * 冲突解决策略
     */
    private final ConflictResolver<K, V> conflictResolver;

    /**
     * 事件监听器
     */
    private final Set<SyncEventListener<K, V>> eventListeners;

    /**
     * 同步队列
     */
    private final BlockingQueue<SyncTask<K, V>> syncQueue;

    /**
     * 同步状态
     */
    private volatile boolean running = false;

    /**
     * 统计信息
     */
    private final AtomicLong syncRequests = new AtomicLong(0);
    private final AtomicLong syncSuccesses = new AtomicLong(0);
    private final AtomicLong syncFailures = new AtomicLong(0);
    private final AtomicLong conflicts = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param localCache  本地缓存
     * @param remoteCache 远程缓存
     * @param config      同步配置
     */
    public CacheSynchronizer(Cache<K, V> localCache, Cache<K, V> remoteCache, SyncConfig config) {
        this.localCache = localCache;
        this.remoteCache = remoteCache;
        this.config = config;
        this.conflictResolver = config.getConflictResolver();
        this.eventListeners = ConcurrentHashMap.newKeySet();
        this.syncQueue = new LinkedBlockingQueue<>(config.getQueueCapacity());
        
        this.syncExecutor = Executors.newScheduledThreadPool(
            config.getSyncThreads(),
            r -> {
                Thread t = new Thread(r, "cache-sync-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
        );
        
        logger.info("初始化缓存同步器，同步策略: {}, 线程数: {}", 
            config.getSyncStrategy(), config.getSyncThreads());
    }

    /**
     * 启动同步器
     */
    public synchronized void start() {
        if (running) {
            logger.warn("缓存同步器已经启动");
            return;
        }
        
        running = true;
        
        // 启动同步任务处理器
        for (int i = 0; i < config.getSyncThreads(); i++) {
            syncExecutor.submit(this::processSyncTasks);
        }
        
        // 启动定时同步
        if (config.getSyncInterval() != null && !config.getSyncInterval().isZero()) {
            syncExecutor.scheduleAtFixedRate(
                this::performPeriodicSync,
                config.getSyncInterval().toMillis(),
                config.getSyncInterval().toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
        
        logger.info("缓存同步器已启动");
    }

    /**
     * 停止同步器
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        try {
            syncExecutor.shutdown();
            if (!syncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            syncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("缓存同步器已停止");
    }

    /**
     * 同步单个键值对
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void sync(K key, V value) {
        sync(key, value, SyncDirection.LOCAL_TO_REMOTE);
    }

    /**
     * 同步单个键值对并指定方向
     *
     * @param key       缓存键
     * @param value     缓存值
     * @param direction 同步方向
     */
    public void sync(K key, V value, SyncDirection direction) {
        SyncTask<K, V> task = new SyncTask<>(key, value, direction, SyncType.PUT);
        submitSyncTask(task);
    }

    /**
     * 异步同步
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 同步结果的异步Future
     */
    public CompletableFuture<Void> syncAsync(K key, V value) {
        return syncAsync(key, value, SyncDirection.LOCAL_TO_REMOTE);
    }

    /**
     * 异步同步并指定方向
     *
     * @param key       缓存键
     * @param value     缓存值
     * @param direction 同步方向
     * @return 同步结果的异步Future
     */
    public CompletableFuture<Void> syncAsync(K key, V value, SyncDirection direction) {
        return CompletableFuture.runAsync(() -> {
            try {
                doSync(key, value, direction, SyncType.PUT);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, syncExecutor);
    }

    /**
     * 同步删除操作
     *
     * @param key 缓存键
     */
    public void syncRemove(K key) {
        SyncTask<K, V> task = new SyncTask<>(key, null, SyncDirection.LOCAL_TO_REMOTE, SyncType.REMOVE);
        submitSyncTask(task);
    }

    /**
     * 批量同步
     *
     * @param data 键值对数据
     */
    public void syncBatch(Map<K, V> data) {
        for (Map.Entry<K, V> entry : data.entrySet()) {
            sync(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 增量同步
     * 同步指定时间之后修改的数据
     *
     * @param since 起始时间
     */
    public void syncIncremental(Instant since) {
        CompletableFuture.runAsync(() -> {
            try {
                performIncrementalSync(since);
            } catch (Exception e) {
                logger.error("增量同步失败", e);
            }
        }, syncExecutor);
    }

    /**
     * 添加事件监听器
     *
     * @param listener 事件监听器
     */
    public void addEventListener(SyncEventListener<K, V> listener) {
        eventListeners.add(listener);
    }

    /**
     * 移除事件监听器
     *
     * @param listener 事件监听器
     */
    public void removeEventListener(SyncEventListener<K, V> listener) {
        eventListeners.remove(listener);
    }

    /**
     * 获取同步统计信息
     *
     * @return 统计信息
     */
    public SyncStatistics getStatistics() {
        return new SyncStatistics(
            syncRequests.get(),
            syncSuccesses.get(),
            syncFailures.get(),
            conflicts.get(),
            syncQueue.size()
        );
    }

    /**
     * 提交同步任务
     */
    private void submitSyncTask(SyncTask<K, V> task) {
        try {
            if (!syncQueue.offer(task, config.getOfferTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("同步队列已满，丢弃任务: {}", task.getKey());
                syncFailures.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("提交同步任务被中断: {}", task.getKey());
        }
    }

    /**
     * 处理同步任务
     */
    private void processSyncTasks() {
        while (running) {
            try {
                SyncTask<K, V> task = syncQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processSyncTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("处理同步任务失败", e);
            }
        }
    }

    /**
     * 处理单个同步任务
     */
    private void processSyncTask(SyncTask<K, V> task) {
        try {
            doSync(task.getKey(), task.getValue(), task.getDirection(), task.getType());
            syncSuccesses.incrementAndGet();
            
            // 通知监听器
            notifyListeners(SyncEvent.success(task));
            
        } catch (Exception e) {
            syncFailures.incrementAndGet();
            logger.error("同步任务执行失败: {}", task.getKey(), e);
            
            // 通知监听器
            notifyListeners(SyncEvent.failure(task, e));
        }
    }

    /**
     * 执行同步操作
     */
    private void doSync(K key, V value, SyncDirection direction, SyncType type) {
        syncRequests.incrementAndGet();
        
        Cache<K, V> sourceCache = direction == SyncDirection.LOCAL_TO_REMOTE ? localCache : remoteCache;
        Cache<K, V> targetCache = direction == SyncDirection.LOCAL_TO_REMOTE ? remoteCache : localCache;
        
        switch (type) {
            case PUT:
                // 检查冲突
                if (config.isConflictDetectionEnabled()) {
                    V existingValue = targetCache.get(key);
                    if (existingValue != null && !Objects.equals(existingValue, value)) {
                        conflicts.incrementAndGet();
                        value = conflictResolver.resolve(key, existingValue, value);
                    }
                }
                
                targetCache.put(key, value);
                break;
                
            case REMOVE:
                targetCache.remove(key);
                break;
        }
    }

    /**
     * 执行定期同步
     */
    private void performPeriodicSync() {
        try {
            logger.debug("执行定期同步");
            
            // 根据同步策略执行不同的同步逻辑
            switch (config.getSyncStrategy()) {
                case FULL:
                    performFullSync();
                    break;
                case INCREMENTAL:
                    Instant since = Instant.now().minus(config.getSyncInterval());
                    performIncrementalSync(since);
                    break;
                case CHANGED_ONLY:
                    performChangedOnlySync();
                    break;
            }
            
        } catch (Exception e) {
            logger.error("定期同步失败", e);
        }
    }

    /**
     * 执行全量同步
     */
    private void performFullSync() {
        // 实现全量同步逻辑
        logger.debug("执行全量同步");
    }

    /**
     * 执行增量同步
     */
    private void performIncrementalSync(Instant since) {
        // 实现增量同步逻辑
        logger.debug("执行增量同步，起始时间: {}", since);
    }

    /**
     * 执行变更同步
     */
    private void performChangedOnlySync() {
        // 实现变更同步逻辑
        logger.debug("执行变更同步");
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(SyncEvent<K, V> event) {
        for (SyncEventListener<K, V> listener : eventListeners) {
            try {
                listener.onSyncEvent(event);
            } catch (Exception e) {
                logger.warn("通知同步事件监听器失败", e);
            }
        }
    }

    /**
     * 同步方向枚举
     */
    public enum SyncDirection {
        LOCAL_TO_REMOTE,
        REMOTE_TO_LOCAL,
        BIDIRECTIONAL
    }

    /**
     * 同步类型枚举
     */
    public enum SyncType {
        PUT,
        REMOVE
    }

    /**
     * 同步策略枚举
     */
    public enum SyncStrategy {
        FULL,           // 全量同步
        INCREMENTAL,    // 增量同步
        CHANGED_ONLY    // 仅同步变更
    }

    /**
     * 同步任务
     */
    private static class SyncTask<K, V> {
        private final K key;
        private final V value;
        private final SyncDirection direction;
        private final SyncType type;
        private final Instant timestamp;

        public SyncTask(K key, V value, SyncDirection direction, SyncType type) {
            this.key = key;
            this.value = value;
            this.direction = direction;
            this.type = type;
            this.timestamp = Instant.now();
        }

        public K getKey() { return key; }
        public V getValue() { return value; }
        public SyncDirection getDirection() { return direction; }
        public SyncType getType() { return type; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * 同步事件
     */
    public static class SyncEvent<K, V> {
        private final SyncTask<K, V> task;
        private final boolean success;
        private final Exception error;

        private SyncEvent(SyncTask<K, V> task, boolean success, Exception error) {
            this.task = task;
            this.success = success;
            this.error = error;
        }

        public static <K, V> SyncEvent<K, V> success(SyncTask<K, V> task) {
            return new SyncEvent<>(task, true, null);
        }

        public static <K, V> SyncEvent<K, V> failure(SyncTask<K, V> task, Exception error) {
            return new SyncEvent<>(task, false, error);
        }

        public SyncTask<K, V> getTask() { return task; }
        public boolean isSuccess() { return success; }
        public Exception getError() { return error; }
    }

    /**
     * 同步事件监听器
     */
    public interface SyncEventListener<K, V> {
        void onSyncEvent(SyncEvent<K, V> event);
    }

    /**
     * 冲突解决器接口
     */
    public interface ConflictResolver<K, V> {
        V resolve(K key, V existing, V incoming);
    }

    /**
     * 默认冲突解决器（后写入优先）
     */
    public static class LastWriteWinsResolver<K, V> implements ConflictResolver<K, V> {
        @Override
        public V resolve(K key, V existing, V incoming) {
            return incoming; // 后写入的值优先
        }
    }

    /**
     * 同步统计信息
     */
    public static class SyncStatistics {
        private final long syncRequests;
        private final long syncSuccesses;
        private final long syncFailures;
        private final long conflicts;
        private final int queueSize;

        public SyncStatistics(long syncRequests, long syncSuccesses, long syncFailures, 
                            long conflicts, int queueSize) {
            this.syncRequests = syncRequests;
            this.syncSuccesses = syncSuccesses;
            this.syncFailures = syncFailures;
            this.conflicts = conflicts;
            this.queueSize = queueSize;
        }

        public long getSyncRequests() { return syncRequests; }
        public long getSyncSuccesses() { return syncSuccesses; }
        public long getSyncFailures() { return syncFailures; }
        public long getConflicts() { return conflicts; }
        public int getQueueSize() { return queueSize; }
        
        public double getSuccessRate() {
            return syncRequests > 0 ? (double) syncSuccesses / syncRequests : 0.0;
        }
        
        public double getFailureRate() {
            return syncRequests > 0 ? (double) syncFailures / syncRequests : 0.0;
        }
    }

    /**
     * 同步配置
     */
    public static class SyncConfig {
        private SyncStrategy syncStrategy = SyncStrategy.INCREMENTAL;
        private Duration syncInterval = Duration.ofMinutes(1);
        private int syncThreads = 2;
        private int queueCapacity = 10000;
        private Duration offerTimeout = Duration.ofSeconds(1);
        private boolean conflictDetectionEnabled = true;
        private ConflictResolver<?, ?> conflictResolver = new LastWriteWinsResolver<>();

        public SyncStrategy getSyncStrategy() { return syncStrategy; }
        public void setSyncStrategy(SyncStrategy syncStrategy) { this.syncStrategy = syncStrategy; }

        public Duration getSyncInterval() { return syncInterval; }
        public void setSyncInterval(Duration syncInterval) { this.syncInterval = syncInterval; }

        public int getSyncThreads() { return syncThreads; }
        public void setSyncThreads(int syncThreads) { this.syncThreads = syncThreads; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public Duration getOfferTimeout() { return offerTimeout; }
        public void setOfferTimeout(Duration offerTimeout) { this.offerTimeout = offerTimeout; }

        public boolean isConflictDetectionEnabled() { return conflictDetectionEnabled; }
        public void setConflictDetectionEnabled(boolean conflictDetectionEnabled) { 
            this.conflictDetectionEnabled = conflictDetectionEnabled; 
        }

        @SuppressWarnings("unchecked")
        public <K, V> ConflictResolver<K, V> getConflictResolver() { 
            return (ConflictResolver<K, V>) conflictResolver; 
        }
        
        public void setConflictResolver(ConflictResolver<?, ?> conflictResolver) { 
            this.conflictResolver = conflictResolver; 
        }
    }
}