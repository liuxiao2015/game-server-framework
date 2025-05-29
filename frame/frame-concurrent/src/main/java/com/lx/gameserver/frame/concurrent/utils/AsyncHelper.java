/*
 * 文件名: AsyncHelper.java
 * 用途: 异步操作辅助类
 * 实现内容:
 *   - 简化异步编程模型
 *   - 异步任务组合
 *   - 超时控制
 *   - 异常处理链
 *   - 结果转换
 * 技术选型:
 *   - CompletableFuture + 虚拟线程
 *   - 函数式编程接口
 *   - 响应式编程模式
 * 依赖关系:
 *   - 依赖ConcurrentUtils工具方法
 *   - 提供链式异步调用支持
 *   - 被业务层广泛使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.*;
import java.util.List;
import java.util.ArrayList;

/**
 * 异步操作辅助类
 * <p>
 * 简化异步编程模型，提供链式异步调用支持。
 * 包含异步任务组合、超时控制、异常处理链、结果转换等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class AsyncHelper {

    private static final Logger logger = LoggerFactory.getLogger(AsyncHelper.class);

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private AsyncHelper() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ===== 异步任务构建器 =====

    /**
     * 创建异步任务构建器
     *
     * @param supplier 初始任务
     * @param <T>      结果类型
     * @return 异步任务构建器
     */
    public static <T> AsyncTaskBuilder<T> async(Supplier<T> supplier) {
        return new AsyncTaskBuilder<>(ConcurrentUtils.supplyAsync(supplier));
    }

    /**
     * 创建异步任务构建器（基于已有Future）
     *
     * @param future 已有Future
     * @param <T>    结果类型
     * @return 异步任务构建器
     */
    public static <T> AsyncTaskBuilder<T> async(CompletableFuture<T> future) {
        return new AsyncTaskBuilder<>(future);
    }

    /**
     * 创建异步任务构建器（基于值）
     *
     * @param value 值
     * @param <T>   值类型
     * @return 异步任务构建器
     */
    public static <T> AsyncTaskBuilder<T> asyncValue(T value) {
        return new AsyncTaskBuilder<>(CompletableFuture.completedFuture(value));
    }

    /**
     * 创建失败的异步任务构建器
     *
     * @param throwable 异常
     * @param <T>       结果类型
     * @return 异步任务构建器
     */
    public static <T> AsyncTaskBuilder<T> asyncFailed(Throwable throwable) {
        return new AsyncTaskBuilder<>(CompletableFuture.failedFuture(throwable));
    }

    // ===== 异步任务构建器类 =====

    /**
     * 异步任务构建器
     * <p>
     * 提供链式调用方式构建复杂的异步操作流程。
     * </p>
     *
     * @param <T> 结果类型
     */
    public static class AsyncTaskBuilder<T> {
        private final CompletableFuture<T> future;

        private AsyncTaskBuilder(CompletableFuture<T> future) {
            this.future = future;
        }

        /**
         * 转换结果
         *
         * @param mapper 转换函数
         * @param <R>    新结果类型
         * @return 新的构建器
         */
        public <R> AsyncTaskBuilder<R> map(Function<T, R> mapper) {
            return new AsyncTaskBuilder<>(future.thenApply(mapper));
        }

        /**
         * 异步转换结果
         *
         * @param mapper 异步转换函数
         * @param <R>    新结果类型
         * @return 新的构建器
         */
        public <R> AsyncTaskBuilder<R> flatMap(Function<T, CompletableFuture<R>> mapper) {
            return new AsyncTaskBuilder<>(future.thenCompose(mapper));
        }

        /**
         * 过滤结果
         *
         * @param predicate 过滤条件
         * @return 构建器
         */
        public AsyncTaskBuilder<T> filter(Predicate<T> predicate) {
            return new AsyncTaskBuilder<>(future.thenCompose(result -> {
                if (predicate.test(result)) {
                    return CompletableFuture.completedFuture(result);
                } else {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("结果不满足过滤条件"));
                }
            }));
        }

        /**
         * 执行副作用操作
         *
         * @param action 副作用操作
         * @return 构建器
         */
        public AsyncTaskBuilder<T> peek(Consumer<T> action) {
            return new AsyncTaskBuilder<>(future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    try {
                        action.accept(result);
                    } catch (Exception e) {
                        logger.warn("副作用操作执行异常", e);
                    }
                }
            }));
        }

        /**
         * 异常处理
         *
         * @param handler 异常处理函数
         * @return 构建器
         */
        public AsyncTaskBuilder<T> onError(Function<Throwable, T> handler) {
            return new AsyncTaskBuilder<>(future.exceptionally(handler));
        }

        /**
         * 异常恢复
         *
         * @param handler 异常恢复函数
         * @return 构建器
         */
        public AsyncTaskBuilder<T> recover(Function<Throwable, CompletableFuture<T>> handler) {
            return new AsyncTaskBuilder<>(future.exceptionallyCompose(handler));
        }

        /**
         * 添加超时控制
         *
         * @param timeout 超时时间
         * @param unit    时间单位
         * @return 构建器
         */
        public AsyncTaskBuilder<T> timeout(long timeout, TimeUnit unit) {
            return new AsyncTaskBuilder<>(ConcurrentUtils.addTimeout(future, timeout, unit));
        }

        /**
         * 重试机制
         *
         * @param maxRetries 最大重试次数
         * @param retryDelay 重试延迟（毫秒）
         * @return 构建器
         */
        public AsyncTaskBuilder<T> retry(int maxRetries, long retryDelay) {
            return new AsyncTaskBuilder<>(future.exceptionallyCompose(throwable -> {
                logger.warn("任务执行失败，准备重试", throwable);
                return retryInternal(() -> future, maxRetries, retryDelay, 1);
            }));
        }

        /**
         * 内部重试实现
         */
        private CompletableFuture<T> retryInternal(Supplier<CompletableFuture<T>> taskSupplier, 
                                                  int maxRetries, long retryDelay, int currentAttempt) {
            if (currentAttempt > maxRetries) {
                return CompletableFuture.failedFuture(
                    new RuntimeException("重试次数已达上限: " + maxRetries));
            }

            return ConcurrentUtils.delay(() -> {}, retryDelay, TimeUnit.MILLISECONDS)
                .thenCompose(v -> taskSupplier.get())
                .exceptionallyCompose(throwable -> {
                    logger.warn("第{}次重试失败", currentAttempt, throwable);
                    return retryInternal(taskSupplier, maxRetries, retryDelay, currentAttempt + 1);
                });
        }

        /**
         * 与另一个任务组合
         *
         * @param other   另一个任务
         * @param combiner 组合函数
         * @param <U>     另一个任务的结果类型
         * @param <R>     组合结果类型
         * @return 新的构建器
         */
        public <U, R> AsyncTaskBuilder<R> combine(CompletableFuture<U> other, 
                                                 BiFunction<T, U, R> combiner) {
            return new AsyncTaskBuilder<>(future.thenCombine(other, combiner));
        }

        /**
         * 与另一个任务组合（接受构建器）
         *
         * @param other   另一个任务构建器
         * @param combiner 组合函数
         * @param <U>     另一个任务的结果类型
         * @param <R>     组合结果类型
         * @return 新的构建器
         */
        public <U, R> AsyncTaskBuilder<R> combine(AsyncTaskBuilder<U> other, 
                                                 BiFunction<T, U, R> combiner) {
            return combine(other.future, combiner);
        }

        /**
         * 执行完成后的操作
         *
         * @param action 完成后的操作
         * @return 构建器
         */
        public AsyncTaskBuilder<T> onComplete(BiConsumer<T, Throwable> action) {
            return new AsyncTaskBuilder<>(future.whenComplete(action));
        }

        /**
         * 执行成功后的操作
         *
         * @param action 成功后的操作
         * @return 构建器
         */
        public AsyncTaskBuilder<T> onSuccess(Consumer<T> action) {
            return new AsyncTaskBuilder<>(future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    action.accept(result);
                }
            }));
        }

        /**
         * 执行失败后的操作
         *
         * @param action 失败后的操作
         * @return 构建器
         */
        public AsyncTaskBuilder<T> onFailure(Consumer<Throwable> action) {
            return new AsyncTaskBuilder<>(future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    action.accept(throwable);
                }
            }));
        }

        /**
         * 获取Future对象
         *
         * @return CompletableFuture
         */
        public CompletableFuture<T> toFuture() {
            return future;
        }

        /**
         * 阻塞获取结果
         *
         * @return 结果
         */
        public T get() {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("获取异步结果失败", e);
            }
        }

        /**
         * 带超时的阻塞获取结果
         *
         * @param timeout 超时时间
         * @param unit    时间单位
         * @return 结果
         */
        public T get(long timeout, TimeUnit unit) {
            try {
                return future.get(timeout, unit);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException("获取异步结果失败", e);
            }
        }

        /**
         * 非阻塞获取结果
         *
         * @param defaultValue 默认值
         * @return 结果或默认值
         */
        public T getNow(T defaultValue) {
            return future.getNow(defaultValue);
        }
    }

    // ===== 异步任务组合工具 =====

    /**
     * 并行执行多个任务并等待所有完成
     *
     * @param tasks 任务列表
     * @param <T>   结果类型
     * @return 包含所有结果的异步构建器
     */
    @SafeVarargs
    public static <T> AsyncTaskBuilder<List<T>> allOf(Supplier<T>... tasks) {
        CompletableFuture<T>[] futures = new CompletableFuture[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = ConcurrentUtils.supplyAsync(tasks[i]);
        }
        return new AsyncTaskBuilder<>(ConcurrentUtils.allOfResults(futures));
    }

    /**
     * 并行执行多个任务并等待任意一个完成
     *
     * @param tasks 任务列表
     * @param <T>   结果类型
     * @return 第一个完成的任务结果
     */
    @SafeVarargs
    public static <T> AsyncTaskBuilder<T> anyOf(Supplier<T>... tasks) {
        CompletableFuture<T>[] futures = new CompletableFuture[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = ConcurrentUtils.supplyAsync(tasks[i]);
        }
        
        @SuppressWarnings("unchecked")
        CompletableFuture<T> anyFuture = (CompletableFuture<T>) CompletableFuture.anyOf(futures);
        return new AsyncTaskBuilder<>(anyFuture);
    }

    /**
     * 顺序执行任务链
     *
     * @param tasks 任务链
     * @param <T>   结果类型
     * @return 最后一个任务的结果
     */
    @SafeVarargs
    public static <T> AsyncTaskBuilder<T> sequence(Supplier<T>... tasks) {
        if (tasks.length == 0) {
            return asyncValue(null);
        }

        CompletableFuture<T> result = ConcurrentUtils.supplyAsync(tasks[0]);
        for (int i = 1; i < tasks.length; i++) {
            final Supplier<T> task = tasks[i];
            result = result.thenCompose(v -> ConcurrentUtils.supplyAsync(task));
        }
        
        return new AsyncTaskBuilder<>(result);
    }

    /**
     * 条件异步执行
     *
     * @param condition 条件
     * @param trueTask  条件为真时执行的任务
     * @param falseTask 条件为假时执行的任务
     * @param <T>       结果类型
     * @return 异步构建器
     */
    public static <T> AsyncTaskBuilder<T> conditional(boolean condition, 
                                                     Supplier<T> trueTask, 
                                                     Supplier<T> falseTask) {
        Supplier<T> task = condition ? trueTask : falseTask;
        return async(task);
    }

    /**
     * 异步条件执行
     *
     * @param conditionTask 条件任务
     * @param trueTask      条件为真时执行的任务
     * @param falseTask     条件为假时执行的任务
     * @param <T>           结果类型
     * @return 异步构建器
     */
    public static <T> AsyncTaskBuilder<T> conditionalAsync(Supplier<Boolean> conditionTask, 
                                                          Supplier<T> trueTask, 
                                                          Supplier<T> falseTask) {
        return async(conditionTask)
            .flatMap(condition -> {
                Supplier<T> task = condition ? trueTask : falseTask;
                return ConcurrentUtils.supplyAsync(task);
            });
    }

    // ===== 流式异步操作 =====

    /**
     * 创建异步流
     *
     * @param items 元素列表
     * @param <T>   元素类型
     * @return 异步流
     */
    public static <T> AsyncStream<T> stream(List<T> items) {
        return new AsyncStream<>(items);
    }

    /**
     * 异步流
     *
     * @param <T> 元素类型
     */
    public static class AsyncStream<T> {
        private final List<T> items;

        private AsyncStream(List<T> items) {
            this.items = new ArrayList<>(items);
        }

        /**
         * 异步映射
         *
         * @param mapper 映射函数
         * @param <R>    结果类型
         * @return 新的异步流
         */
        public <R> AsyncStream<R> mapAsync(Function<T, R> mapper) {
            List<CompletableFuture<R>> futures = items.stream()
                .map(item -> ConcurrentUtils.supplyAsync(() -> mapper.apply(item)))
                .toList();
            
            CompletableFuture<List<R>> allResults = ConcurrentUtils.allOfResults(
                futures.toArray(new CompletableFuture[0]));
            
            try {
                return new AsyncStream<>(allResults.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("异步映射失败", e);
            }
        }

        /**
         * 异步过滤
         *
         * @param predicate 过滤条件
         * @return 新的异步流
         */
        public AsyncStream<T> filterAsync(Predicate<T> predicate) {
            List<CompletableFuture<Boolean>> futures = items.stream()
                .map(item -> ConcurrentUtils.supplyAsync(() -> predicate.test(item)))
                .toList();

            try {
                @SuppressWarnings("unchecked")
                CompletableFuture<Boolean>[] futureArray = futures.toArray(new CompletableFuture[0]);
                List<Boolean> results = ConcurrentUtils.allOfResults(futureArray).get();
                
                List<T> filtered = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    if (results.get(i)) {
                        filtered.add(items.get(i));
                    }
                }
                
                return new AsyncStream<>(filtered);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("异步过滤失败", e);
            }
        }

        /**
         * 收集结果
         *
         * @return 异步构建器
         */
        public AsyncTaskBuilder<List<T>> collect() {
            return asyncValue(items);
        }
    }
}