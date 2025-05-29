/*
 * 文件名: ThreadUtils.java
 * 用途: 线程池工具类
 * 实现内容:
 *   - 提供线程池的创建和管理
 *   - 支持不同类型的线程池配置
 *   - 集成线程池监控和统计
 *   - 优化高并发任务调度性能
 * 技术选型:
 *   - Java标准线程池API（兼容Java 17）
 *   - ExecutorService线程池框架
 *   - CompletableFuture异步编程支持
 *   - 线程池监控和统计
 * 依赖关系:
 *   - 基于Java标准API，兼容Java 17+
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
 * 线程池工具类
 * <p>
 * 提供高性能的线程池管理和任务调度功能。针对不同类型的任务
 * 提供专门优化的线程池配置，特别适合I/O密集型任务和高并发场景。
 * 兼容Java 17，为将来升级到虚拟线程做准备。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public final class ThreadUtils {

    private static final Logger logger = LoggerFactory.getLogger(ThreadUtils.class);

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private ThreadUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ===== 预定义线程池 =====

    /**
     * 默认线程池
     */
    private static volatile ExecutorService defaultExecutor;

    /**
     * IO密集型线程池
     */
    private static volatile ExecutorService ioExecutor;

    /**
     * CPU密集型传统线程池
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
     * 获取默认线程池
     * <p>
     * 适用于一般的异步任务处理，如用户请求处理、数据库操作等
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
     * 获取IO密集型线程池
     * <p>
     * 专门用于IO密集型任务，如文件读写、网络请求、数据库查询等
     * </p>
     *
     * @return IO密集型线程池
     */
    public static ExecutorService getIoExecutor() {
        if (ioExecutor == null) {
            synchronized (ThreadUtils.class) {
                if (ioExecutor == null) {
                    ioExecutor = createThreadPool("IO", 
                            GameConstants.IO_THREAD_POOL_CORE_SIZE,
                            GameConstants.IO_THREAD_POOL_MAX_SIZE);
                    logger.info("IO线程池创建完成");
                }
            }
        }
        return ioExecutor;
    }

    /**
     * 获取CPU密集型线程池
     * <p>
     * 适用于CPU密集型任务，如计算、数据处理、算法执行等
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
                            coreSize * 2,
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(1000),
                            createThreadFactory("CPU-Thread"),
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
                            coreSize, createThreadFactory("Scheduled-Thread"));
                    logger.info("定时任务调度器创建完成，核心线程数: {}", coreSize);
                }
            }
        }
        return scheduledExecutor;
    }

    // ===== 线程池创建方法 =====

    /**
     * 创建标准线程池
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
                createThreadFactory(namePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建有界线程池
     *
     * @param namePrefix   线程名前缀
     * @param coreSize     核心线程数
     * @param maxSize      最大线程数
     * @param queueSize    队列大小
     * @return 有界线程池
     */
    public static ExecutorService createBoundedThreadPool(String namePrefix, int coreSize, int maxSize, int queueSize) {
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                createThreadFactory(namePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建传统线程工厂
     *
     * @param namePrefix 线程名前缀
     * @return 传统线程工厂
     */
    public static ThreadFactory createThreadFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong(0);
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    // ===== 任务执行方法 =====

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

    /**
     * 定期执行任务
     *
     * @param task         任务
     * @param initialDelay 初始延迟
     * @param period       执行周期
     * @param unit         时间单位
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return getScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("定时任务执行异常", e);
            }
        }, initialDelay, period, unit);
    }

    /**
     * 延迟执行任务（固定延迟）
     *
     * @param task         任务
     * @param initialDelay 初始延迟
     * @param delay        固定延迟
     * @param unit         时间单位
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return getScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("定时任务执行异常", e);
            }
        }, initialDelay, delay, unit);
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
     * 等待任意任务完成
     *
     * @param futures 任务Future列表
     * @param timeout 超时时间
     * @return 完成的任务结果
     */
    public static Object awaitAny(CompletableFuture<?>[] futures, Duration timeout) {
        try {
            return CompletableFuture.anyOf(futures).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("等待任务完成超时: {}ms", timeout.toMillis());
            return null;
        } catch (Exception e) {
            logger.error("等待任务完成时发生异常", e);
            return null;
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
        
        if (defaultExecutor != null) {
            shutdown(defaultExecutor, 5, TimeUnit.SECONDS);
            logger.info("默认线程池已关闭");
        }
        
        if (ioExecutor != null) {
            shutdown(ioExecutor, 5, TimeUnit.SECONDS);
            logger.info("IO线程池已关闭");
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
     * 获取线程池状态信息
     *
     * @return 状态信息
     */
    public static String getThreadPoolStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("线程池状态信息:\n");
        
        if (defaultExecutor != null) {
            sb.append("  默认线程池: ").append(getExecutorStatus(defaultExecutor)).append("\n");
        }
        
        if (ioExecutor != null) {
            sb.append("  IO线程池: ").append(getExecutorStatus(ioExecutor)).append("\n");
        }
        
        if (cpuExecutor instanceof ThreadPoolExecutor tpe) {
            sb.append(String.format("  CPU线程池: 核心=%d, 最大=%d, 活跃=%d, 队列=%d\n",
                    tpe.getCorePoolSize(), tpe.getMaximumPoolSize(), 
                    tpe.getActiveCount(), tpe.getQueue().size()));
        }
        
        if (scheduledExecutor instanceof ThreadPoolExecutor stpe) {
            sb.append(String.format("  定时任务调度器: 核心=%d, 活跃=%d, 队列=%d\n",
                    stpe.getCorePoolSize(), stpe.getActiveCount(), stpe.getQueue().size()));
        }
        
        return sb.toString();
    }

    /**
     * 获取执行器状态
     *
     * @param executor 执行器
     * @return 状态描述
     */
    private static String getExecutorStatus(ExecutorService executor) {
        if (executor.isShutdown()) {
            return "已关闭";
        } else if (executor.isTerminated()) {
            return "已终止";
        } else {
            return "运行中";
        }
    }
}