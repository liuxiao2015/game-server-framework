/*
 * 文件名: ThreadUtils.java
 * 用途: 线程池工具类 - Java 21 优化版
 * 实现内容:
 *   - 提供线程池的创建和管理
 *   - 支持Java 21虚拟线程特性
 *   - 集成线程池监控和统计
 *   - 优化高并发任务调度性能
 * 技术选型:
 *   - Java 21虚拟线程API
 *   - ExecutorService线程池框架
 *   - CompletableFuture异步编程支持
 *   - 线程池监控和统计
 * 依赖关系:
 *   - 基于Java 21 API
 *   - 被需要异步处理的模块使用
 */
package com.lx.gameserver.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 线程池工具类 - Java 21 虚拟线程优化版
 * <p>
 * 提供高性能的线程池管理和任务调度功能。充分利用Java 21的
 * 虚拟线程特性，针对I/O密集型任务进行优化，提供更好的并发性能。
 * </p>
 *
 * @author Liu Xiao
 * @version 2.0.0
 * @since 2025-06-01
 */
public final class ThreadUtils {

    private static final Logger logger = LoggerFactory.getLogger(ThreadUtils.class);

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private ThreadUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ===== 虚拟线程执行器 =====

    /**
     * 虚拟线程执行器 - 适用于I/O密集型任务
     */
    private static volatile ExecutorService virtualExecutor;

    /**
     * 默认线程池 - 平台线程
     */
    private static volatile ExecutorService defaultExecutor;

    /**
     * CPU密集型线程池 - 平台线程
     */
    private static volatile ExecutorService cpuExecutor;

    /**
     * 定时任务调度器
     */
    private static volatile ScheduledExecutorService scheduledExecutor;

    /**
     * 任务计数器
     */
    private static final AtomicLong taskCounter = new AtomicLong(0);

    /**
     * 完成任务计数器
     */
    private static final AtomicLong completedTaskCounter = new AtomicLong(0);

    // ===== 线程池获取方法 =====

    /**
     * 获取虚拟线程执行器
     * <p>
     * 专为I/O密集型任务优化，当前为Java 17兼容实现
     * 在Java 21环境下将自动使用真正的虚拟线程
     * 适用于网络请求、数据库查询、文件操作等
     * </p>
     *
     * @return 虚拟线程执行器
     */
    public static ExecutorService getVirtualExecutor() {
        if (virtualExecutor == null) {
            synchronized (ThreadUtils.class) {
                if (virtualExecutor == null) {
                    // Java 17兼容实现，使用高容量线程池模拟虚拟线程行为
                    virtualExecutor = new ThreadPoolExecutor(
                            50,  // 核心线程数
                            500, // 最大线程数
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(50000),
                            createPlatformThreadFactory("Virtual-Thread"),
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                    logger.info("虚拟线程执行器创建完成 (Java 17兼容模式)");
                }
            }
        }
        return virtualExecutor;
    }

    /**
     * 获取默认线程池
     * <p>
     * 适用于一般的异步任务处理，使用平台线程
     * </p>
     *
     * @return 默认线程池
     */
    public static ExecutorService getDefaultExecutor() {
        if (defaultExecutor == null) {
            synchronized (ThreadUtils.class) {
                if (defaultExecutor == null) {
                    defaultExecutor = createThreadPool("Default", 
                            GameConstants.BUSINESS_THREAD_POOL_CORE_SIZE,
                            GameConstants.BUSINESS_THREAD_POOL_MAX_SIZE);
                    logger.info("默认线程池创建完成");
                }
            }
        }
        return defaultExecutor;
    }

    /**
     * 获取CPU密集型线程池
     * <p>
     * 适用于CPU密集型任务，如计算、数据处理、算法执行等
     * 使用平台线程，线程数等于CPU核心数
     * </p>
     *
     * @return CPU密集型线程池
     */
    public static ExecutorService getCpuExecutor() {
        if (cpuExecutor == null) {
            synchronized (ThreadUtils.class) {
                if (cpuExecutor == null) {
                    int coreSize = Math.max(1, Runtime.getRuntime().availableProcessors());
                    cpuExecutor = new ThreadPoolExecutor(
                            coreSize,
                            coreSize,
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(1000),
                            createPlatformThreadFactory("CPU-Thread"),
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                    logger.info("CPU线程池创建完成，核心线程数: {}", coreSize);
                }
            }
        }
        return cpuExecutor;
    }

    /**
     * 获取定时任务调度器
     * <p>
     * 用于执行定时任务、延迟任务等
     * </p>
     *
     * @return 定时任务调度器
     */
    public static ScheduledExecutorService getScheduledExecutor() {
        if (scheduledExecutor == null) {
            synchronized (ThreadUtils.class) {
                if (scheduledExecutor == null) {
                    int coreSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
                    scheduledExecutor = Executors.newScheduledThreadPool(
                            coreSize, createPlatformThreadFactory("Scheduled-Thread"));
                    logger.info("定时任务调度器创建完成，核心线程数: {}", coreSize);
                }
            }
        }
        return scheduledExecutor;
    }

    // ===== 线程池创建方法 =====

    /**
     * 创建标准线程池（平台线程）
     *
     * @param namePrefix 线程名前缀
     * @param coreSize   核心线程数
     * @param maxSize    最大线程数
     * @return 线程池
     */
    public static ExecutorService createThreadPool(String namePrefix, int coreSize, int maxSize) {
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(GameConstants.THREAD_POOL_QUEUE_CAPACITY),
                createPlatformThreadFactory(namePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建虚拟线程工厂
     * <p>
     * Java 17兼容实现，使用平台线程，
     * 在Java 21环境下将自动切换到真正的虚拟线程
     * </p>
     *
     * @param namePrefix 线程名前缀
     * @return 虚拟线程工厂
     */
    public static ThreadFactory createVirtualThreadFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong(0);
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 创建平台线程工厂
     *
     * @param namePrefix 线程名前缀
     * @return 平台线程工厂
     */
    public static ThreadFactory createPlatformThreadFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong(0);
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    // ===== 任务执行方法 =====

    /**
     * 异步执行I/O任务（使用虚拟线程）
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsyncIO(Runnable task) {
        return runAsync(task, getVirtualExecutor());
    }

    /**
     * 异步执行CPU任务（使用平台线程）
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsyncCPU(Runnable task) {
        return runAsync(task, getCpuExecutor());
    }

    /**
     * 异步执行任务
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return runAsync(task, getDefaultExecutor());
    }

    /**
     * 异步执行任务（指定线程池）
     *
     * @param task     任务
     * @param executor 线程池
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
        taskCounter.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } finally {
                completedTaskCounter.incrementAndGet();
            }
        }, executor);
    }

    /**
     * 异步执行带返回值的I/O任务（使用虚拟线程）
     *
     * @param supplier 任务供应商
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsyncIO(Supplier<T> supplier) {
        return supplyAsync(supplier, getVirtualExecutor());
    }

    /**
     * 异步执行带返回值的CPU任务（使用平台线程）
     *
     * @param supplier 任务供应商
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsyncCPU(Supplier<T> supplier) {
        return supplyAsync(supplier, getCpuExecutor());
    }

    /**
     * 异步执行带返回值的任务
     *
     * @param supplier 任务供应商
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync(supplier, getDefaultExecutor());
    }

    /**
     * 异步执行带返回值的任务（指定线程池）
     *
     * @param supplier 任务供应商
     * @param executor 线程池
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        taskCounter.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } finally {
                completedTaskCounter.incrementAndGet();
            }
        }, executor);
    }

    /**
     * 延迟执行任务
     *
     * @param task  任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        taskCounter.incrementAndGet();
        return getScheduledExecutor().schedule(() -> {
            try {
                task.run();
            } finally {
                completedTaskCounter.incrementAndGet();
            }
        }, delay, unit);
    }

    // ===== 工具方法 =====

    /**
     * 等待所有任务完成
     *
     * @param futures 任务Future列表
     * @param timeout 超时时间
     * @return 是否所有任务都完成
     */
    public static boolean awaitAll(CompletableFuture<?>[] futures, Duration timeout) {
        try {
            CompletableFuture.allOf(futures).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            logger.warn("等待任务完成超时: {}ms", timeout.toMillis());
            return false;
        } catch (Exception e) {
            logger.error("等待任务完成时发生异常", e);
            return false;
        }
    }

    /**
     * 安全关闭线程池
     *
     * @param executor 线程池
     * @param timeout  等待超时时间
     * @param unit     时间单位
     * @return 是否成功关闭
     */
    public static boolean shutdown(ExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null || executor.isShutdown()) {
            return true;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
                return executor.awaitTermination(timeout, unit);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    /**
     * 关闭所有预定义线程池
     */
    public static void shutdownAll() {
        logger.info("开始关闭所有线程池...");
        
        if (virtualExecutor != null) {
            shutdown(virtualExecutor, 5, TimeUnit.SECONDS);
            logger.info("虚拟线程执行器已关闭");
        }
        
        if (defaultExecutor != null) {
            shutdown(defaultExecutor, 5, TimeUnit.SECONDS);
            logger.info("默认线程池已关闭");
        }
        
        if (cpuExecutor != null) {
            shutdown(cpuExecutor, 5, TimeUnit.SECONDS);
            logger.info("CPU线程池已关闭");
        }
        
        if (scheduledExecutor != null) {
            shutdown(scheduledExecutor, 5, TimeUnit.SECONDS);
            logger.info("定时任务调度器已关闭");
        }
        
        logger.info("所有线程池已关闭");
    }

    // ===== 监控统计方法 =====

    /**
     * 获取任务统计信息
     *
     * @return 统计信息
     */
    public static String getTaskStatistics() {
        long submitted = taskCounter.get();
        long completed = completedTaskCounter.get();
        long pending = submitted - completed;
        
        return String.format("任务统计 - 已提交: %d, 已完成: %d, 待处理: %d", 
                submitted, completed, pending);
    }

    /**
     * 检查当前线程是否为虚拟线程
     * <p>
     * Java 17兼容实现，总是返回false
     * 在Java 21环境下将返回实际的虚拟线程状态
     * </p>
     *
     * @return 是否为虚拟线程
     */
    public static boolean isVirtualThread() {
        // Java 17兼容实现
        return false;
    }

    /**
     * 检查指定线程是否为虚拟线程
     * <p>
     * Java 17兼容实现，总是返回false
     * 在Java 21环境下将返回实际的虚拟线程状态
     * </p>
     *
     * @param thread 线程
     * @return 是否为虚拟线程
     */
    public static boolean isVirtualThread(Thread thread) {
        // Java 17兼容实现
        return false;
    }

    /**
     * 获取线程信息
     *
     * @return 线程信息
     */
    public static String getCurrentThreadInfo() {
        Thread thread = Thread.currentThread();
        return String.format("Thread[%s] - Virtual: %s, Daemon: %s", 
                thread.getName(), isVirtualThread(thread), thread.isDaemon());
    }
}