/*
 * 文件名: SchedulerUtils.java
 * 用途: 调度工具类
 * 实现内容:
 *   - 定时任务和延迟执行管理
 *   - 周期执行和任务调度
 *   - 任务管理和优雅关闭
 *   - 线程池管理和性能监控
 *   - 异常处理和任务恢复
 * 技术选型:
 *   - ScheduledExecutorService实现任务调度
 *   - CompletableFuture支持异步编程
 *   - 虚拟线程优化并发性能
 *   - 监控和统计功能集成
 * 依赖关系:
 *   - 被所有需要定时任务的模块使用
 *   - 提供统一的任务调度接口
 *   - 集成日志和监控系统
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 调度工具类
 * <p>
 * 提供完整的任务调度功能，包括定时任务、延迟执行、
 * 周期执行等。支持任务管理、监控和优雅关闭。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
public final class SchedulerUtils {

    /** 默认调度器 */
    private static volatile GameScheduler defaultScheduler;

    /** 调度器注册表 */
    private static final Map<String, GameScheduler> schedulers = new ConcurrentHashMap<>();

    /** 全局任务计数器 */
    private static final AtomicLong globalTaskCounter = new AtomicLong(0);

    // 私有构造函数
    private SchedulerUtils() {
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    /**
     * 游戏调度器
     */
    public static class GameScheduler {
        private final String name;
        private final ScheduledExecutorService executor;
        private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
        private final AtomicInteger taskCounter = new AtomicInteger(0);
        private final AtomicLong completedTasks = new AtomicLong(0);
        private final AtomicLong failedTasks = new AtomicLong(0);
        private volatile boolean shutdown = false;

        public GameScheduler(String name, int corePoolSize) {
            this.name = name;
            this.executor = Executors.newScheduledThreadPool(corePoolSize, r -> {
                Thread t = new Thread(r, name + "-scheduler-" + taskCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            
            log.info("创建调度器: {}, 核心线程数: {}", name, corePoolSize);
        }

        /**
         * 延迟执行任务
         */
        public ScheduledTask schedule(Runnable task, Duration delay) {
            return schedule(generateTaskId(), task, delay);
        }

        /**
         * 延迟执行任务（带ID）
         */
        public ScheduledTask schedule(String taskId, Runnable task, Duration delay) {
            checkNotShutdown();
            
            ScheduledFuture<?> future = executor.schedule(
                    wrapTask(taskId, task), 
                    delay.toMillis(), 
                    TimeUnit.MILLISECONDS
            );
            
            ScheduledTask scheduledTask = new ScheduledTask(taskId, TaskType.DELAYED, future, this);
            tasks.put(taskId, scheduledTask);
            
            log.debug("调度延迟任务: {}, 延迟: {}ms", taskId, delay.toMillis());
            return scheduledTask;
        }

        /**
         * 周期性执行任务（固定延迟）
         */
        public ScheduledTask scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
            return scheduleWithFixedDelay(generateTaskId(), task, initialDelay, delay);
        }

        /**
         * 周期性执行任务（固定延迟，带ID）
         */
        public ScheduledTask scheduleWithFixedDelay(String taskId, Runnable task, Duration initialDelay, Duration delay) {
            checkNotShutdown();
            
            ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                    wrapTask(taskId, task),
                    initialDelay.toMillis(),
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            
            ScheduledTask scheduledTask = new ScheduledTask(taskId, TaskType.FIXED_DELAY, future, this);
            tasks.put(taskId, scheduledTask);
            
            log.debug("调度固定延迟任务: {}, 初始延迟: {}ms, 间隔: {}ms", 
                    taskId, initialDelay.toMillis(), delay.toMillis());
            return scheduledTask;
        }

        /**
         * 周期性执行任务（固定频率）
         */
        public ScheduledTask scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period) {
            return scheduleAtFixedRate(generateTaskId(), task, initialDelay, period);
        }

        /**
         * 周期性执行任务（固定频率，带ID）
         */
        public ScheduledTask scheduleAtFixedRate(String taskId, Runnable task, Duration initialDelay, Duration period) {
            checkNotShutdown();
            
            ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                    wrapTask(taskId, task),
                    initialDelay.toMillis(),
                    period.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            
            ScheduledTask scheduledTask = new ScheduledTask(taskId, TaskType.FIXED_RATE, future, this);
            tasks.put(taskId, scheduledTask);
            
            log.debug("调度固定频率任务: {}, 初始延迟: {}ms, 周期: {}ms", 
                    taskId, initialDelay.toMillis(), period.toMillis());
            return scheduledTask;
        }

        /**
         * 在指定时间执行任务
         */
        public ScheduledTask scheduleAt(Runnable task, LocalDateTime dateTime) {
            return scheduleAt(generateTaskId(), task, dateTime);
        }

        /**
         * 在指定时间执行任务（带ID）
         */
        public ScheduledTask scheduleAt(String taskId, Runnable task, LocalDateTime dateTime) {
            LocalDateTime now = LocalDateTime.now();
            if (dateTime.isBefore(now)) {
                throw new IllegalArgumentException("调度时间不能早于当前时间");
            }
            
            Duration delay = Duration.between(now, dateTime);
            return schedule(taskId, task, delay);
        }

        /**
         * 每日定时执行
         */
        public ScheduledTask scheduleDaily(Runnable task, LocalTime time) {
            return scheduleDaily(generateTaskId(), task, time);
        }

        /**
         * 每日定时执行（带ID）
         */
        public ScheduledTask scheduleDaily(String taskId, Runnable task, LocalTime time) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRun = now.toLocalDate().atTime(time);
            
            // 如果今天的时间已过，则调度到明天
            if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
                nextRun = nextRun.plusDays(1);
            }
            
            Duration initialDelay = Duration.between(now, nextRun);
            Duration period = Duration.ofDays(1);
            
            return scheduleAtFixedRate(taskId, task, initialDelay, period);
        }

        /**
         * 取消任务
         */
        public boolean cancelTask(String taskId) {
            ScheduledTask task = tasks.remove(taskId);
            if (task != null) {
                boolean cancelled = task.cancel();
                log.debug("取消任务: {}, 结果: {}", taskId, cancelled);
                return cancelled;
            }
            return false;
        }

        /**
         * 获取任务
         */
        public ScheduledTask getTask(String taskId) {
            return tasks.get(taskId);
        }

        /**
         * 获取所有任务ID
         */
        public Set<String> getTaskIds() {
            return new HashSet<>(tasks.keySet());
        }

        /**
         * 获取活跃任务数量
         */
        public int getActiveTaskCount() {
            return (int) tasks.values().stream().filter(task -> !task.isDone()).count();
        }

        /**
         * 获取调度器统计信息
         */
        public Map<String, Object> getStatistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("name", name);
            stats.put("totalTasks", tasks.size());
            stats.put("activeTasks", getActiveTaskCount());
            stats.put("completedTasks", completedTasks.get());
            stats.put("failedTasks", failedTasks.get());
            stats.put("shutdown", shutdown);
            
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                stats.put("poolSize", tpe.getPoolSize());
                stats.put("activeCount", tpe.getActiveCount());
                stats.put("queueSize", tpe.getQueue().size());
            }
            
            return stats;
        }

        /**
         * 优雅关闭
         */
        public void shutdown() {
            if (shutdown) {
                return;
            }
            
            log.info("关闭调度器: {}", name);
            shutdown = true;
            
            // 取消所有任务
            tasks.values().forEach(ScheduledTask::cancel);
            tasks.clear();
            
            // 关闭执行器
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.warn("调度器 {} 无法正常关闭", name);
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            log.info("调度器 {} 已关闭", name);
        }

        /**
         * 包装任务（添加异常处理和统计）
         */
        private Runnable wrapTask(String taskId, Runnable task) {
            return () -> {
                try {
                    log.debug("开始执行任务: {}", taskId);
                    long startTime = System.currentTimeMillis();
                    
                    task.run();
                    
                    long endTime = System.currentTimeMillis();
                    completedTasks.incrementAndGet();
                    
                    log.debug("任务 {} 执行完成，耗时: {}ms", taskId, endTime - startTime);
                    
                } catch (Exception e) {
                    failedTasks.incrementAndGet();
                    log.error("任务 {} 执行失败", taskId, e);
                    
                    // 移除失败的一次性任务
                    ScheduledTask scheduledTask = tasks.get(taskId);
                    if (scheduledTask != null && scheduledTask.getType() == TaskType.DELAYED) {
                        tasks.remove(taskId);
                    }
                }
            };
        }

        /**
         * 生成任务ID
         */
        private String generateTaskId() {
            return name + "-task-" + globalTaskCounter.incrementAndGet();
        }

        /**
         * 检查是否已关闭
         */
        private void checkNotShutdown() {
            if (shutdown) {
                throw new IllegalStateException("调度器已关闭: " + name);
            }
        }

        public String getName() {
            return name;
        }

        public boolean isShutdown() {
            return shutdown;
        }
    }

    /**
     * 任务类型
     */
    public enum TaskType {
        /** 延迟执行 */
        DELAYED,
        /** 固定延迟 */
        FIXED_DELAY,
        /** 固定频率 */
        FIXED_RATE
    }

    /**
     * 调度任务
     */
    public static class ScheduledTask {
        private final String taskId;
        private final TaskType type;
        private final ScheduledFuture<?> future;
        private final GameScheduler scheduler;
        private final LocalDateTime createTime;

        public ScheduledTask(String taskId, TaskType type, ScheduledFuture<?> future, GameScheduler scheduler) {
            this.taskId = taskId;
            this.type = type;
            this.future = future;
            this.scheduler = scheduler;
            this.createTime = LocalDateTime.now();
        }

        /**
         * 取消任务
         */
        public boolean cancel() {
            boolean cancelled = future.cancel(false);
            if (cancelled) {
                scheduler.tasks.remove(taskId);
            }
            return cancelled;
        }

        /**
         * 强制取消任务
         */
        public boolean cancelNow() {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                scheduler.tasks.remove(taskId);
            }
            return cancelled;
        }

        /**
         * 检查任务是否已完成
         */
        public boolean isDone() {
            return future.isDone();
        }

        /**
         * 检查任务是否已取消
         */
        public boolean isCancelled() {
            return future.isCancelled();
        }

        /**
         * 获取剩余延迟时间
         */
        public Duration getRemainingDelay() {
            long delayMs = future.getDelay(TimeUnit.MILLISECONDS);
            return Duration.ofMillis(Math.max(0, delayMs));
        }

        // Getters
        public String getTaskId() { return taskId; }
        public TaskType getType() { return type; }
        public LocalDateTime getCreateTime() { return createTime; }
        public GameScheduler getScheduler() { return scheduler; }
    }

    // ========== 静态方法 ==========

    /**
     * 获取默认调度器
     */
    public static GameScheduler getDefaultScheduler() {
        if (defaultScheduler == null) {
            synchronized (SchedulerUtils.class) {
                if (defaultScheduler == null) {
                    defaultScheduler = new GameScheduler("default", 
                            Math.max(2, Runtime.getRuntime().availableProcessors()));
                    schedulers.put("default", defaultScheduler);
                }
            }
        }
        return defaultScheduler;
    }

    /**
     * 创建调度器
     */
    public static GameScheduler createScheduler(String name, int corePoolSize) {
        GameScheduler scheduler = new GameScheduler(name, corePoolSize);
        schedulers.put(name, scheduler);
        return scheduler;
    }

    /**
     * 获取调度器
     */
    public static GameScheduler getScheduler(String name) {
        return schedulers.get(name);
    }

    /**
     * 移除调度器
     */
    public static void removeScheduler(String name) {
        GameScheduler scheduler = schedulers.remove(name);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    /**
     * 延迟执行（使用默认调度器）
     */
    public static ScheduledTask schedule(Runnable task, Duration delay) {
        return getDefaultScheduler().schedule(task, delay);
    }

    /**
     * 延迟执行（带ID）
     */
    public static ScheduledTask schedule(String taskId, Runnable task, Duration delay) {
        return getDefaultScheduler().schedule(taskId, task, delay);
    }

    /**
     * 周期执行（固定延迟）
     */
    public static ScheduledTask scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
        return getDefaultScheduler().scheduleWithFixedDelay(task, initialDelay, delay);
    }

    /**
     * 周期执行（固定频率）
     */
    public static ScheduledTask scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period) {
        return getDefaultScheduler().scheduleAtFixedRate(task, initialDelay, period);
    }

    /**
     * 在指定时间执行
     */
    public static ScheduledTask scheduleAt(Runnable task, LocalDateTime dateTime) {
        return getDefaultScheduler().scheduleAt(task, dateTime);
    }

    /**
     * 每日定时执行
     */
    public static ScheduledTask scheduleDaily(Runnable task, LocalTime time) {
        return getDefaultScheduler().scheduleDaily(task, time);
    }

    /**
     * 取消任务
     */
    public static boolean cancelTask(String taskId) {
        return getDefaultScheduler().cancelTask(taskId);
    }

    // ========== 便捷方法 ==========

    /**
     * 延迟执行（秒）
     */
    public static ScheduledTask scheduleSeconds(Runnable task, long seconds) {
        return schedule(task, Duration.ofSeconds(seconds));
    }

    /**
     * 延迟执行（分钟）
     */
    public static ScheduledTask scheduleMinutes(Runnable task, long minutes) {
        return schedule(task, Duration.ofMinutes(minutes));
    }

    /**
     * 延迟执行（小时）
     */
    public static ScheduledTask scheduleHours(Runnable task, long hours) {
        return schedule(task, Duration.ofHours(hours));
    }

    /**
     * 每秒执行
     */
    public static ScheduledTask scheduleEverySecond(Runnable task) {
        return scheduleAtFixedRate(task, Duration.ZERO, Duration.ofSeconds(1));
    }

    /**
     * 每分钟执行
     */
    public static ScheduledTask scheduleEveryMinute(Runnable task) {
        return scheduleAtFixedRate(task, Duration.ZERO, Duration.ofMinutes(1));
    }

    /**
     * 每小时执行
     */
    public static ScheduledTask scheduleEveryHour(Runnable task) {
        return scheduleAtFixedRate(task, Duration.ZERO, Duration.ofHours(1));
    }

    /**
     * 异步执行
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, getDefaultScheduler().executor);
    }

    /**
     * 异步执行（带返回值）
     */
    public static <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, getDefaultScheduler().executor);
    }

    /**
     * 超时执行
     */
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, Duration timeout) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        
        ScheduledTask timeoutTask = schedule(() -> {
            timeoutFuture.completeExceptionally(new TimeoutException("操作超时"));
        }, timeout);
        
        future.whenComplete((result, throwable) -> {
            timeoutTask.cancel();
            if (throwable != null) {
                timeoutFuture.completeExceptionally(throwable);
            } else {
                timeoutFuture.complete(result);
            }
        });
        
        return timeoutFuture;
    }

    /**
     * 重试执行
     */
    public static CompletableFuture<Void> retryAsync(Runnable task, int maxRetries, Duration retryDelay) {
        return retryAsync(() -> {
            task.run();
            return null;
        }, maxRetries, retryDelay);
    }

    /**
     * 重试执行（带返回值）
     */
    public static <T> CompletableFuture<T> retryAsync(java.util.function.Supplier<T> supplier, int maxRetries, Duration retryDelay) {
        CompletableFuture<T> result = new CompletableFuture<>();
        retryAsyncInternal(supplier, maxRetries, retryDelay, result, 0);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> void retryAsyncInternal(java.util.function.Supplier<T> supplier, int maxRetries, 
                                               Duration retryDelay, CompletableFuture<T> result, int attempt) {
        try {
            T value = supplier.get();
            result.complete(value);
        } catch (Exception e) {
            if (attempt >= maxRetries) {
                result.completeExceptionally(e);
            } else {
                schedule(() -> retryAsyncInternal(supplier, maxRetries, retryDelay, result, attempt + 1), retryDelay);
            }
        }
    }

    // ========== 生命周期管理 ==========

    /**
     * 关闭所有调度器
     */
    public static void shutdownAll() {
        log.info("关闭所有调度器...");
        
        List<GameScheduler> schedulerList = new ArrayList<>(schedulers.values());
        for (GameScheduler scheduler : schedulerList) {
            try {
                scheduler.shutdown();
            } catch (Exception e) {
                log.error("关闭调度器失败: {}", scheduler.getName(), e);
            }
        }
        
        schedulers.clear();
        defaultScheduler = null;
        
        log.info("所有调度器已关闭");
    }

    /**
     * 获取全局统计信息
     */
    public static Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSchedulers", schedulers.size());
        stats.put("globalTaskCounter", globalTaskCounter.get());
        
        Map<String, Map<String, Object>> schedulerStats = new HashMap<>();
        for (Map.Entry<String, GameScheduler> entry : schedulers.entrySet()) {
            schedulerStats.put(entry.getKey(), entry.getValue().getStatistics());
        }
        stats.put("schedulers", schedulerStats);
        
        return stats;
    }

    /**
     * 注册关闭钩子
     */
    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM关闭，执行调度器清理...");
            shutdownAll();
        }, "scheduler-shutdown-hook"));
    }
}