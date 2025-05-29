/*
 * 文件名: CacheWarmer.java
 * 用途: 缓存预热器
 * 实现内容:
 *   - 启动时缓存预热功能
 *   - 定时缓存预热调度
 *   - 数据源配置和加载
 *   - 并行预热优化
 *   - 预热进度监控和报告
 * 技术选型:
 *   - 线程池并发预热
 *   - CompletableFuture异步支持
 *   - 调度器定时预热
 *   - 进度回调和监控
 * 依赖关系:
 *   - 与缓存管理器集成
 *   - 使用WarmupStrategy策略
 *   - 执行WarmupTask任务
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.warmup;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheConfig;
import com.lx.gameserver.frame.cache.core.CacheKey;
import com.lx.gameserver.frame.cache.local.LocalCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 缓存预热器
 * <p>
 * 提供缓存预热功能，支持启动预热、定时预热、并行预热等多种预热模式。
 * 通过合理的预热策略，提升缓存命中率和系统启动性能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheWarmer {

    private static final Logger logger = LoggerFactory.getLogger(CacheWarmer.class);

    /**
     * 缓存管理器
     */
    private final LocalCacheManager cacheManager;

    /**
     * 预热配置
     */
    private final WarmupConfig config;

    /**
     * 预热执行器
     */
    private final ExecutorService warmupExecutor;

    /**
     * 定时调度器
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 预热任务队列
     */
    private final BlockingQueue<WarmupTask> taskQueue;

    /**
     * 预热统计
     */
    private final WarmupStatistics statistics;

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 是否正在预热
     */
    private volatile boolean warming = false;

    /**
     * 预热进度回调
     */
    private Consumer<WarmupProgress> progressCallback;

    /**
     * 构造函数
     *
     * @param cacheManager 缓存管理器
     */
    public CacheWarmer(LocalCacheManager cacheManager) {
        this(cacheManager, WarmupConfig.defaultConfig());
    }

    /**
     * 构造函数
     *
     * @param cacheManager 缓存管理器
     * @param config       预热配置
     */
    public CacheWarmer(LocalCacheManager cacheManager, WarmupConfig config) {
        this.cacheManager = cacheManager;
        this.config = config;
        this.warmupExecutor = Executors.newFixedThreadPool(config.getParallelThreads(),
            r -> {
                Thread t = new Thread(r, "cache-warmer-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
        this.scheduler = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "cache-warmer-scheduler-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
        this.taskQueue = new LinkedBlockingQueue<>();
        this.statistics = new WarmupStatistics();
    }

    /**
     * 启动预热器
     */
    public synchronized void start() {
        if (started) {
            logger.warn("缓存预热器已经启动");
            return;
        }

        logger.info("启动缓存预热器，并行线程数: {}", config.getParallelThreads());
        started = true;

        // 启动任务处理线程
        startTaskProcessor();

        // 启动时预热
        if (config.isStartupWarmup()) {
            warmupOnStartup();
        }

        // 定时预热
        if (config.getWarmupInterval() != null && !config.getWarmupInterval().isZero()) {
            schedulePeriodicWarmup();
        }
    }

    /**
     * 停止预热器
     */
    public synchronized void stop() {
        if (!started) {
            return;
        }

        logger.info("停止缓存预热器");
        started = false;

        // 停止调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 停止预热执行器
        warmupExecutor.shutdown();
        try {
            if (!warmupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                warmupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            warmupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 添加预热任务
     *
     * @param task 预热任务
     * @return 是否添加成功
     */
    public boolean addWarmupTask(WarmupTask task) {
        if (!started) {
            logger.warn("预热器未启动，无法添加任务");
            return false;
        }
        return taskQueue.offer(task);
    }

    /**
     * 立即执行预热
     *
     * @return 预热结果
     */
    public CompletableFuture<WarmupResult> warmupNow() {
        return warmupNow(Collections.emptyList());
    }

    /**
     * 立即执行预热
     *
     * @param tasks 指定的预热任务
     * @return 预热结果
     */
    public CompletableFuture<WarmupResult> warmupNow(List<WarmupTask> tasks) {
        if (warming) {
            logger.warn("预热正在进行中，跳过此次预热");
            return CompletableFuture.completedFuture(
                new WarmupResult(false, "预热正在进行中", 0, 0, Duration.ZERO));
        }

        warming = true;
        Instant startTime = Instant.now();
        logger.info("开始立即预热，任务数量: {}", tasks.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWarmup(tasks);
            } finally {
                warming = false;
                Duration elapsed = Duration.between(startTime, Instant.now());
                logger.info("立即预热完成，耗时: {}ms", elapsed.toMillis());
            }
        }, warmupExecutor);
    }

    /**
     * 设置进度回调
     *
     * @param callback 进度回调
     */
    public void setProgressCallback(Consumer<WarmupProgress> callback) {
        this.progressCallback = callback;
    }

    /**
     * 获取预热统计
     *
     * @return 统计信息
     */
    public WarmupStatistics getStatistics() {
        return statistics;
    }

    /**
     * 是否正在预热
     *
     * @return 是否正在预热
     */
    public boolean isWarming() {
        return warming;
    }

    /**
     * 启动任务处理线程
     */
    private void startTaskProcessor() {
        warmupExecutor.submit(() -> {
            while (started) {
                try {
                    WarmupTask task = taskQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        processWarmupTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("处理预热任务失败", e);
                }
            }
        });
    }

    /**
     * 启动时预热
     */
    private void warmupOnStartup() {
        scheduler.schedule(() -> {
            logger.info("开始启动预热");
            try {
                List<WarmupTask> startupTasks = createStartupTasks();
                WarmupResult result = executeWarmup(startupTasks);
                logger.info("启动预热完成: {}", result);
            } catch (Exception e) {
                logger.error("启动预热失败", e);
            }
        }, config.getStartupDelay().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 定时预热调度
     */
    private void schedulePeriodicWarmup() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!warming) {
                logger.debug("开始定时预热");
                try {
                    List<WarmupTask> periodicTasks = createPeriodicTasks();
                    executeWarmup(periodicTasks);
                } catch (Exception e) {
                    logger.error("定时预热失败", e);
                }
            }
        }, config.getWarmupInterval().toMinutes(), config.getWarmupInterval().toMinutes(), TimeUnit.MINUTES);
    }

    /**
     * 创建启动任务
     */
    private List<WarmupTask> createStartupTasks() {
        List<WarmupTask> tasks = new ArrayList<>();
        
        // 预热所有缓存的热点数据
        for (String cacheName : cacheManager.getCacheNames()) {
            WarmupTask task = WarmupTask.builder()
                .cacheName(cacheName)
                .taskName("startup-warmup-" + cacheName)
                .priority(WarmupTask.Priority.HIGH)
                .dataSupplier(() -> generateStartupData(cacheName))
                .build();
            tasks.add(task);
        }
        
        return tasks;
    }

    /**
     * 创建定时任务
     */
    private List<WarmupTask> createPeriodicTasks() {
        List<WarmupTask> tasks = new ArrayList<>();
        
        // 刷新缓存中即将过期的数据
        for (String cacheName : cacheManager.getCacheNames()) {
            WarmupTask task = WarmupTask.builder()
                .cacheName(cacheName)
                .taskName("periodic-warmup-" + cacheName)
                .priority(WarmupTask.Priority.NORMAL)
                .dataSupplier(() -> generatePeriodicData(cacheName))
                .build();
            tasks.add(task);
        }
        
        return tasks;
    }

    /**
     * 生成启动数据
     */
    private Map<CacheKey, Object> generateStartupData(String cacheName) {
        // 这里应该根据实际业务逻辑生成预热数据
        // 简化处理，返回空Map
        return Collections.emptyMap();
    }

    /**
     * 生成定时数据
     */
    private Map<CacheKey, Object> generatePeriodicData(String cacheName) {
        // 这里应该根据实际业务逻辑生成刷新数据
        // 简化处理，返回空Map
        return Collections.emptyMap();
    }

    /**
     * 执行预热
     */
    private WarmupResult executeWarmup(List<WarmupTask> tasks) {
        if (tasks.isEmpty()) {
            return new WarmupResult(true, "没有预热任务", 0, 0, Duration.ZERO);
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 按优先级排序
        tasks.sort(Comparator.comparing(WarmupTask::getPriority));

        // 并行执行预热任务
        List<CompletableFuture<Void>> futures = tasks.stream()
            .map(task -> CompletableFuture.runAsync(() -> {
                try {
                    processWarmupTask(task);
                    successCount.incrementAndGet();
                    
                    // 报告进度
                    if (progressCallback != null) {
                        WarmupProgress progress = new WarmupProgress(
                            task.getTaskName(),
                            successCount.get() + failureCount.get(),
                            tasks.size(),
                            true,
                            null
                        );
                        progressCallback.accept(progress);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    logger.error("预热任务执行失败: {}", task.getTaskName(), e);
                    
                    // 报告进度
                    if (progressCallback != null) {
                        WarmupProgress progress = new WarmupProgress(
                            task.getTaskName(),
                            successCount.get() + failureCount.get(),
                            tasks.size(),
                            false,
                            e.getMessage()
                        );
                        progressCallback.accept(progress);
                    }
                }
            }, warmupExecutor))
            .toList();

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Duration elapsed = Duration.between(startTime, Instant.now());
        WarmupResult result = new WarmupResult(
            failureCount.get() == 0,
            failureCount.get() == 0 ? "预热成功" : "部分预热失败",
            successCount.get(),
            failureCount.get(),
            elapsed
        );

        // 更新统计
        statistics.recordWarmup(result);

        return result;
    }

    /**
     * 处理单个预热任务
     */
    private void processWarmupTask(WarmupTask task) {
        logger.debug("执行预热任务: {}", task.getTaskName());
        
        Cache<CacheKey, Object> cache = cacheManager.getCache(task.getCacheName());
        Map<CacheKey, Object> data = task.getDataSupplier().get();
        
        int batchSize = config.getBatchSize();
        List<Map.Entry<CacheKey, Object>> entries = new ArrayList<>(data.entrySet());
        
        // 分批处理
        for (int i = 0; i < entries.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, entries.size());
            List<Map.Entry<CacheKey, Object>> batch = entries.subList(i, endIndex);
            
            for (Map.Entry<CacheKey, Object> entry : batch) {
                try {
                    cache.put(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    logger.warn("预热数据写入失败: key={}", entry.getKey(), e);
                }
            }
            
            // 批次间延迟
            if (config.getBatchDelay() != null && !config.getBatchDelay().isZero()) {
                try {
                    Thread.sleep(config.getBatchDelay().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.debug("预热任务完成: {}, 数据量: {}", task.getTaskName(), data.size());
    }
}