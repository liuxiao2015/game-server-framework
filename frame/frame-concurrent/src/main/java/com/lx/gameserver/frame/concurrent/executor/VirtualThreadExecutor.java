/*
 * 文件名: VirtualThreadExecutor.java
 * 用途: 虚拟线程执行器
 * 实现内容:
 *   - 基于Java高性能线程池的执行器（兼容Java 17）
 *   - 支持任务提交、批量执行、优雅关闭
 *   - 集成监控指标（活跃线程数、完成任务数等）
 *   - 异常处理和任务包装机制
 * 技术选型:
 *   - Java标准ExecutorService API（兼容Java 17）
 *   - 自定义线程池配置，为将来升级Java 21做准备
 *   - CompletableFuture异步编程支持
 * 依赖关系:
 *   - 基于Java标准API，兼容Java 17+
 *   - 依赖GameThreadFactory进行线程创建
 *   - 被ExecutorManager管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 虚拟线程执行器
 * <p>
 * 基于Java高性能线程池的执行器，为游戏服务器提供轻量级并发执行能力。
 * 当升级到Java 21时，可以轻松切换到真正的虚拟线程实现。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class VirtualThreadExecutor implements ExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadExecutor.class);

    /**
     * 执行器名称
     */
    private final String name;

    /**
     * 底层线程池执行器
     */
    private final ExecutorService delegate;

    /**
     * 提交任务计数器
     */
    private final AtomicLong submittedTaskCount = new AtomicLong(0);

    /**
     * 完成任务计数器
     */
    private final AtomicLong completedTaskCount = new AtomicLong(0);

    /**
     * 失败任务计数器
     */
    private final AtomicLong failedTaskCount = new AtomicLong(0);

    /**
     * 执行器是否已关闭
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 创建时间
     */
    private final long createTime;

    /**
     * 构造函数
     *
     * @param name           执行器名称
     * @param threadFactory  线程工厂
     * @param corePoolSize   核心线程数
     * @param maximumPoolSize 最大线程数
     */
    public VirtualThreadExecutor(String name, ThreadFactory threadFactory, int corePoolSize, int maximumPoolSize) {
        this.name = name;
        this.createTime = System.currentTimeMillis();
        
        // 使用线程池实现，为将来升级Java 21做准备
        this.delegate = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        logger.info("虚拟线程执行器[{}]已创建，核心线程数:{}, 最大线程数:{}", name, corePoolSize, maximumPoolSize);
    }

    /**
     * 包装任务，添加监控和异常处理
     */
    private Runnable wrapTask(Runnable task) {
        return () -> {
            long startTime = System.currentTimeMillis();
            try {
                task.run();
                completedTaskCount.incrementAndGet();
                logger.debug("任务执行成功，耗时:{}ms", System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                failedTaskCount.incrementAndGet();
                logger.error("任务执行失败，耗时:{}ms", System.currentTimeMillis() - startTime, e);
                throw e;
            }
        };
    }

    /**
     * 包装任务，添加监控和异常处理
     */
    private <T> Callable<T> wrapTask(Callable<T> task) {
        return () -> {
            long startTime = System.currentTimeMillis();
            try {
                T result = task.call();
                completedTaskCount.incrementAndGet();
                logger.debug("任务执行成功，耗时:{}ms", System.currentTimeMillis() - startTime);
                return result;
            } catch (Exception e) {
                failedTaskCount.incrementAndGet();
                logger.error("任务执行失败，耗时:{}ms", System.currentTimeMillis() - startTime, e);
                throw e;
            }
        };
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.incrementAndGet();
        delegate.execute(wrapTask(command));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.incrementAndGet();
        return delegate.submit(wrapTask(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.incrementAndGet();
        return delegate.submit(wrapTask(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.incrementAndGet();
        return delegate.submit(wrapTask(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.addAndGet(tasks.size());
        return delegate.invokeAll(tasks.stream().map(this::wrapTask).toList());
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) 
            throws InterruptedException {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.addAndGet(tasks.size());
        return delegate.invokeAll(tasks.stream().map(this::wrapTask).toList(), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.addAndGet(tasks.size());
        return delegate.invokeAny(tasks.stream().map(this::wrapTask).toList());
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) 
            throws InterruptedException, ExecutionException, TimeoutException {
        if (shutdown.get()) {
            throw new RejectedExecutionException("执行器已关闭");
        }
        submittedTaskCount.addAndGet(tasks.size());
        return delegate.invokeAny(tasks.stream().map(this::wrapTask).toList(), timeout, unit);
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            logger.info("虚拟线程执行器[{}]开始关闭", name);
            delegate.shutdown();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (shutdown.compareAndSet(false, true)) {
            logger.info("虚拟线程执行器[{}]强制关闭", name);
            return delegate.shutdownNow();
        }
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    // ===== 监控指标方法 =====

    /**
     * 获取执行器名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取提交任务数
     */
    public long getSubmittedTaskCount() {
        return submittedTaskCount.get();
    }

    /**
     * 获取完成任务数
     */
    public long getCompletedTaskCount() {
        return completedTaskCount.get();
    }

    /**
     * 获取失败任务数
     */
    public long getFailedTaskCount() {
        return failedTaskCount.get();
    }

    /**
     * 获取活跃线程数
     */
    public int getActiveCount() {
        if (delegate instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) delegate).getActiveCount();
        }
        return 0;
    }

    /**
     * 获取线程池大小
     */
    public int getPoolSize() {
        if (delegate instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) delegate).getPoolSize();
        }
        return 0;
    }

    /**
     * 获取队列中任务数
     */
    public int getQueueSize() {
        if (delegate instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) delegate).getQueue().size();
        }
        return 0;
    }

    /**
     * 获取创建时间
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 获取运行时间（毫秒）
     */
    public long getUpTime() {
        return System.currentTimeMillis() - createTime;
    }

    /**
     * 获取执行器状态信息
     */
    public String getStatus() {
        return String.format("VirtualThreadExecutor[name=%s, active=%d, pool=%d, queue=%d, submitted=%d, completed=%d, failed=%d]",
            name, getActiveCount(), getPoolSize(), getQueueSize(), 
            getSubmittedTaskCount(), getCompletedTaskCount(), getFailedTaskCount());
    }

    @Override
    public String toString() {
        return getStatus();
    }
}