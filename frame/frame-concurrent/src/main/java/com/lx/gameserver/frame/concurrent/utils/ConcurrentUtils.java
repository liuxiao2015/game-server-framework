/*
 * 文件名: ConcurrentUtils.java
 * 用途: 并发工具类
 * 实现内容:
 *   - 提供常用并发操作的便捷方法
 *   - 异步执行辅助方法
 *   - CompletableFuture工具方法
 *   - 集合并发操作
 *   - 线程安全工具
 * 技术选型:
 *   - Java 17并发API封装
 *   - CompletableFuture异步编程
 *   - 线程安全集合操作
 * 依赖关系:
 *   - 依赖ExecutorManager获取执行器
 *   - 简化并发编程，提高开发效率
 *   - 被业务模块广泛使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.utils;

import com.lx.gameserver.frame.concurrent.executor.ExecutorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * 并发工具类
 * <p>
 * 提供常用并发操作的便捷方法，简化并发编程模型，提高开发效率。
 * 包含异步执行、Future组合、集合并发操作、线程安全工具等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class ConcurrentUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentUtils.class);

    /**
     * 默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT = 30;

    /**
     * 执行器管理器
     */
    private static volatile ExecutorManager executorManager;

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private ConcurrentUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 设置执行器管理器
     */
    public static void setExecutorManager(ExecutorManager manager) {
        executorManager = manager;
    }

    /**
     * 获取默认执行器
     */
    private static ExecutorService getDefaultExecutor() {
        if (executorManager != null) {
            return executorManager.getDefaultExecutor();
        }
        return ForkJoinPool.commonPool();
    }

    // ===== 异步执行方法 =====

    /**
     * 异步执行任务
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, getDefaultExecutor());
    }

    /**
     * 异步执行任务（指定执行器）
     *
     * @param task     任务
     * @param executor 执行器
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
        return CompletableFuture.runAsync(task, executor);
    }

    /**
     * 异步执行带返回值的任务
     *
     * @param supplier 任务供应商
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, getDefaultExecutor());
    }

    /**
     * 异步执行带返回值的任务（指定执行器）
     *
     * @param supplier 任务供应商
     * @param executor 执行器
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /**
     * 延迟执行任务
     *
     * @param task  任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> delay(Runnable task, long delay, TimeUnit unit) {
        return CompletableFuture.runAsync(() -> {
            try {
                unit.sleep(delay);
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("任务被中断", e);
            }
        }, getDefaultExecutor());
    }

    // ===== CompletableFuture工具方法 =====

    /**
     * 等待所有任务完成
     *
     * @param futures 任务列表
     * @return 组合的CompletableFuture
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... futures) {
        return CompletableFuture.allOf(futures);
    }

    /**
     * 等待所有任务完成并收集结果
     *
     * @param futures 任务列表
     * @param <T>     结果类型
     * @return 包含所有结果的CompletableFuture
     */
    @SafeVarargs
    public static <T> CompletableFuture<List<T>> allOfResults(CompletableFuture<T>... futures) {
        return CompletableFuture.allOf(futures)
            .thenApply(v -> List.of(futures).stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    /**
     * 等待任意一个任务完成
     *
     * @param futures 任务列表
     * @return 组合的CompletableFuture
     */
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... futures) {
        return CompletableFuture.anyOf(futures);
    }

    /**
     * 带超时的异步执行
     *
     * @param supplier 任务供应商
     * @param timeout  超时时间
     * @param unit     时间单位
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsyncWithTimeout(Supplier<T> supplier, 
                                                                 long timeout, TimeUnit unit) {
        CompletableFuture<T> future = supplyAsync(supplier);
        return addTimeout(future, timeout, unit);
    }

    /**
     * 为CompletableFuture添加超时控制
     *
     * @param future  原始Future
     * @param timeout 超时时间
     * @param unit    时间单位
     * @param <T>     结果类型
     * @return 带超时的CompletableFuture
     */
    public static <T> CompletableFuture<T> addTimeout(CompletableFuture<T> future, 
                                                     long timeout, TimeUnit unit) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        
        // 设置超时
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            timeoutFuture.completeExceptionally(new TimeoutException("操作超时"));
            scheduler.shutdown();
        }, timeout, unit);
        
        // 原Future完成时完成结果
        future.whenComplete((result, throwable) -> {
            scheduler.shutdown();
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
     *
     * @param supplier   任务供应商
     * @param maxRetries 最大重试次数
     * @param retryDelay 重试延迟（毫秒）
     * @param <T>        返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> retry(Supplier<T> supplier, int maxRetries, long retryDelay) {
        return retryInternal(supplier, maxRetries, retryDelay, 0);
    }

    /**
     * 内部重试实现
     */
    private static <T> CompletableFuture<T> retryInternal(Supplier<T> supplier, int maxRetries, 
                                                         long retryDelay, int currentAttempt) {
        return supplyAsync(supplier)
            .exceptionallyCompose(throwable -> {
                if (currentAttempt >= maxRetries) {
                    return CompletableFuture.failedFuture(throwable);
                }
                
                logger.warn("任务执行失败，第{}次重试，延迟{}ms", currentAttempt + 1, retryDelay, throwable);
                
                return delay(() -> {}, retryDelay, TimeUnit.MILLISECONDS)
                    .thenCompose(v -> retryInternal(supplier, maxRetries, retryDelay, currentAttempt + 1));
            });
    }

    // ===== 集合并发操作 =====

    /**
     * 并行处理集合元素
     *
     * @param collection 集合
     * @param processor  处理函数
     * @param <T>        元素类型
     * @param <R>        结果类型
     * @return 处理结果列表的CompletableFuture
     */
    public static <T, R> CompletableFuture<List<R>> parallelProcess(Collection<T> collection, 
                                                                   Function<T, R> processor) {
        List<CompletableFuture<R>> futures = collection.stream()
            .map(item -> supplyAsync(() -> processor.apply(item)))
            .collect(Collectors.toList());
        
        return allOfResults(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 并行处理集合元素（指定并发度）
     *
     * @param collection  集合
     * @param processor   处理函数
     * @param concurrency 并发度
     * @param <T>         元素类型
     * @param <R>         结果类型
     * @return 处理结果列表的CompletableFuture
     */
    public static <T, R> CompletableFuture<List<R>> parallelProcess(Collection<T> collection, 
                                                                   Function<T, R> processor, 
                                                                   int concurrency) {
        Semaphore semaphore = new Semaphore(concurrency);
        List<CompletableFuture<R>> futures = collection.stream()
            .map(item -> supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    return processor.apply(item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("任务被中断", e);
                } finally {
                    semaphore.release();
                }
            }))
            .collect(Collectors.toList());
        
        return allOfResults(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 分批处理集合元素
     *
     * @param collection 集合
     * @param batchSize  批次大小
     * @param processor  批处理函数
     * @param <T>        元素类型
     * @param <R>        结果类型
     * @return 处理结果列表的CompletableFuture
     */
    public static <T, R> CompletableFuture<List<R>> batchProcess(Collection<T> collection, 
                                                                int batchSize, 
                                                                Function<List<T>, List<R>> processor) {
        List<List<T>> batches = partition(new ArrayList<>(collection), batchSize);
        List<CompletableFuture<List<R>>> futures = batches.stream()
            .map(batch -> supplyAsync(() -> processor.apply(batch)))
            .collect(Collectors.toList());
        
        @SuppressWarnings("unchecked")
        CompletableFuture<List<R>>[] futureArray = futures.toArray(new CompletableFuture[0]);
        
        return allOfResults(futureArray)
            .thenApply(results -> {
                List<R> flatResults = new ArrayList<>();
                for (List<R> batch : results) {
                    flatResults.addAll(batch);
                }
                return flatResults;
            });
    }

    /**
     * 将列表分割成批次
     */
    private static <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    // ===== 线程安全工具 =====

    /**
     * 创建线程安全的计数器
     *
     * @param initialValue 初始值
     * @return 原子计数器
     */
    public static java.util.concurrent.atomic.AtomicLong createCounter(long initialValue) {
        return new java.util.concurrent.atomic.AtomicLong(initialValue);
    }

    /**
     * 创建线程安全的引用
     *
     * @param initialValue 初始值
     * @param <T>          引用类型
     * @return 原子引用
     */
    public static <T> AtomicReference<T> createReference(T initialValue) {
        return new AtomicReference<>(initialValue);
    }

    /**
     * 创建线程安全的Map
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 并发Map
     */
    public static <K, V> ConcurrentMap<K, V> createConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 创建线程安全的Set
     *
     * @param <T> 元素类型
     * @return 并发Set
     */
    public static <T> Set<T> createConcurrentSet() {
        return ConcurrentHashMap.newKeySet();
    }

    /**
     * 创建线程安全的队列
     *
     * @param <T> 元素类型
     * @return 并发队列
     */
    public static <T> Queue<T> createConcurrentQueue() {
        return new ConcurrentLinkedQueue<>();
    }

    // ===== 异常处理工具 =====

    /**
     * 安全执行任务（捕获异常）
     *
     * @param task 任务
     */
    public static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            logger.error("任务执行异常", e);
        }
    }

    /**
     * 安全执行任务（捕获异常，自定义异常处理）
     *
     * @param task             任务
     * @param exceptionHandler 异常处理器
     */
    public static void safeRun(Runnable task, Consumer<Exception> exceptionHandler) {
        try {
            task.run();
        } catch (Exception e) {
            exceptionHandler.accept(e);
        }
    }

    /**
     * 安全获取值（捕获异常）
     *
     * @param supplier     值供应商
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 值或默认值
     */
    public static <T> T safeGet(Supplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception e) {
            logger.error("获取值异常，返回默认值", e);
            return defaultValue;
        }
    }

    // ===== 性能监控工具 =====

    /**
     * 测量任务执行时间
     *
     * @param task 任务
     * @return 执行时间（毫秒）
     */
    public static long measureTime(Runnable task) {
        long startTime = System.currentTimeMillis();
        task.run();
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 测量任务执行时间（带返回值）
     *
     * @param supplier 任务供应商
     * @param <T>      返回值类型
     * @return 包含结果和执行时间的对象
     */
    public static <T> TimedResult<T> measureTime(Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        T result = supplier.get();
        long executionTime = System.currentTimeMillis() - startTime;
        return new TimedResult<>(result, executionTime);
    }

    /**
     * 计时结果包装类
     */
    public static class TimedResult<T> {
        private final T result;
        private final long executionTime;

        public TimedResult(T result, long executionTime) {
            this.result = result;
            this.executionTime = executionTime;
        }

        public T getResult() {
            return result;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        @Override
        public String toString() {
            return String.format("TimedResult[result=%s, time=%dms]", result, executionTime);
        }
    }
}