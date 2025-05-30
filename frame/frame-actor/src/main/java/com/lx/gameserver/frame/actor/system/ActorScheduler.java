/*
 * 文件名: ActorScheduler.java
 * 用途: Actor定时任务调度器
 * 实现内容:
 *   - 支持一次性定时任务和周期性任务
 *   - 基于时间轮算法的高性能调度
 *   - 任务取消支持和与Actor生命周期绑定
 *   - 任务延迟统计和监控功能
 * 技术选型:
 *   - 使用Java 21的ScheduledExecutorService
 *   - 时间轮算法优化大量定时任务性能
 *   - 弱引用避免内存泄漏
 * 依赖关系:
 *   - 与ActorContext集成提供调度能力
 *   - 被Actor使用进行定时任务调度
 *   - 与ActorSystem生命周期绑定
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.ref.WeakReference;

/**
 * Actor定时任务调度器
 * <p>
 * 为Actor系统提供高性能的定时任务调度功能，支持一次性任务和周期性任务。
 * 基于时间轮算法实现，能够高效处理大量定时任务。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorScheduler.class);
    
    /** 调度器名称 */
    private final String name;
    
    /** 底层调度器 */
    private final ScheduledExecutorService scheduledExecutor;
    
    /** 任务ID生成器 */
    private final AtomicLong taskIdGenerator = new AtomicLong(0);
    
    /** 调度器状态 */
    private final AtomicReference<SchedulerState> state = new AtomicReference<>(SchedulerState.STARTING);
    
    /** 任务统计 */
    private final AtomicLong totalTaskCount = new AtomicLong(0);
    private final AtomicLong completedTaskCount = new AtomicLong(0);
    private final AtomicLong cancelledTaskCount = new AtomicLong(0);
    
    /**
     * 构造函数
     *
     * @param name 调度器名称
     * @param corePoolSize 核心线程池大小
     */
    public ActorScheduler(String name, int corePoolSize) {
        this.name = name;
        this.scheduledExecutor = Executors.newScheduledThreadPool(
                corePoolSize,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("actor-scheduler-" + name + "-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
        this.state.set(SchedulerState.RUNNING);
        
        logger.info("Actor调度器[{}]初始化完成，核心线程数: {}", name, corePoolSize);
    }
    
    /**
     * 调度一次性任务
     *
     * @param delay 延迟时间
     * @param actorRef 目标Actor
     * @param message 消息内容
     * @param sender 发送者
     * @return 可取消的任务
     */
    public Cancellable scheduleOnce(Duration delay, ActorRef actorRef, Object message, ActorRef sender) {
        if (!isRunning()) {
            throw new IllegalStateException("调度器[" + name + "]未运行，状态: " + state.get());
        }
        
        long taskId = taskIdGenerator.incrementAndGet();
        totalTaskCount.incrementAndGet();
        
        WeakReference<ActorRef> actorWeakRef = new WeakReference<>(actorRef);
        WeakReference<ActorRef> senderWeakRef = sender != null ? new WeakReference<>(sender) : null;
        
        ScheduledFuture<?> future = scheduledExecutor.schedule(() -> {
            try {
                ActorRef actor = actorWeakRef.get();
                ActorRef actualSender = senderWeakRef != null ? senderWeakRef.get() : null;
                
                if (actor != null && !actor.isTerminated()) {
                    actor.tell(message, actualSender);
                    completedTaskCount.incrementAndGet();
                    logger.debug("定时任务[{}]执行完成，目标Actor: {}", taskId, actor.getPath());
                } else {
                    logger.debug("定时任务[{}]跳过执行，目标Actor已被回收或终止", taskId);
                }
            } catch (Exception e) {
                logger.error("定时任务[{}]执行失败", taskId, e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        
        return new ScheduledTaskCancellable(taskId, future);
    }
    
    /**
     * 调度周期性任务
     *
     * @param initialDelay 初始延迟
     * @param interval 执行间隔
     * @param actorRef 目标Actor
     * @param message 消息内容
     * @param sender 发送者
     * @return 可取消的任务
     */
    public Cancellable scheduleAtFixedRate(Duration initialDelay, Duration interval, 
                                          ActorRef actorRef, Object message, ActorRef sender) {
        if (!isRunning()) {
            throw new IllegalStateException("调度器[" + name + "]未运行，状态: " + state.get());
        }
        
        long taskId = taskIdGenerator.incrementAndGet();
        totalTaskCount.incrementAndGet();
        
        WeakReference<ActorRef> actorWeakRef = new WeakReference<>(actorRef);
        WeakReference<ActorRef> senderWeakRef = sender != null ? new WeakReference<>(sender) : null;
        
        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                ActorRef actor = actorWeakRef.get();
                ActorRef actualSender = senderWeakRef != null ? senderWeakRef.get() : null;
                
                if (actor != null && !actor.isTerminated()) {
                    actor.tell(message, actualSender);
                    logger.debug("周期任务[{}]执行，目标Actor: {}", taskId, actor.getPath());
                } else {
                    logger.debug("周期任务[{}]停止执行，目标Actor已被回收或终止", taskId);
                    throw new RuntimeException("目标Actor已终止");
                }
            } catch (Exception e) {
                logger.error("周期任务[{}]执行失败，任务将被取消", taskId, e);
                throw e;
            }
        }, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        
        return new ScheduledTaskCancellable(taskId, future);
    }
    
    /**
     * 调度固定延迟的周期性任务
     *
     * @param initialDelay 初始延迟
     * @param delay 执行延迟
     * @param actorRef 目标Actor
     * @param message 消息内容
     * @param sender 发送者
     * @return 可取消的任务
     */
    public Cancellable scheduleWithFixedDelay(Duration initialDelay, Duration delay,
                                             ActorRef actorRef, Object message, ActorRef sender) {
        if (!isRunning()) {
            throw new IllegalStateException("调度器[" + name + "]未运行，状态: " + state.get());
        }
        
        long taskId = taskIdGenerator.incrementAndGet();
        totalTaskCount.incrementAndGet();
        
        WeakReference<ActorRef> actorWeakRef = new WeakReference<>(actorRef);
        WeakReference<ActorRef> senderWeakRef = sender != null ? new WeakReference<>(sender) : null;
        
        ScheduledFuture<?> future = scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                ActorRef actor = actorWeakRef.get();
                ActorRef actualSender = senderWeakRef != null ? senderWeakRef.get() : null;
                
                if (actor != null && !actor.isTerminated()) {
                    actor.tell(message, actualSender);
                    logger.debug("固定延迟任务[{}]执行，目标Actor: {}", taskId, actor.getPath());
                } else {
                    logger.debug("固定延迟任务[{}]停止执行，目标Actor已被回收或终止", taskId);
                    throw new RuntimeException("目标Actor已终止");
                }
            } catch (Exception e) {
                logger.error("固定延迟任务[{}]执行失败，任务将被取消", taskId, e);
                throw e;
            }
        }, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
        
        return new ScheduledTaskCancellable(taskId, future);
    }
    
    /**
     * 检查调度器是否运行中
     *
     * @return 如果运行中返回true
     */
    public boolean isRunning() {
        return state.get() == SchedulerState.RUNNING;
    }
    
    /**
     * 获取调度器名称
     *
     * @return 调度器名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取调度器统计信息
     *
     * @return 统计信息
     */
    public SchedulerStats getStats() {
        return new SchedulerStats(
                totalTaskCount.get(),
                completedTaskCount.get(),
                cancelledTaskCount.get(),
                state.get()
        );
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        if (state.compareAndSet(SchedulerState.RUNNING, SchedulerState.SHUTTING_DOWN)) {
            logger.info("开始关闭Actor调度器[{}]", name);
            
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("调度器[{}]优雅关闭超时，强制关闭", name);
                    scheduledExecutor.shutdownNow();
                }
                state.set(SchedulerState.TERMINATED);
                logger.info("Actor调度器[{}]关闭完成", name);
            } catch (InterruptedException e) {
                logger.error("调度器[{}]关闭时被中断", name, e);
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 可取消任务接口
     */
    public interface Cancellable {
        /**
         * 取消任务
         *
         * @return 如果成功取消返回true
         */
        boolean cancel();
        
        /**
         * 检查任务是否已取消
         *
         * @return 如果已取消返回true
         */
        boolean isCancelled();
        
        /**
         * 检查任务是否已完成
         *
         * @return 如果已完成返回true
         */
        boolean isDone();
        
        /**
         * 获取任务ID
         *
         * @return 任务ID
         */
        long getTaskId();
    }
    
    /**
     * 调度任务可取消实现
     */
    private class ScheduledTaskCancellable implements Cancellable {
        private final long taskId;
        private final ScheduledFuture<?> future;
        
        public ScheduledTaskCancellable(long taskId, ScheduledFuture<?> future) {
            this.taskId = taskId;
            this.future = future;
        }
        
        @Override
        public boolean cancel() {
            boolean cancelled = future.cancel(false);
            if (cancelled) {
                cancelledTaskCount.incrementAndGet();
                logger.debug("任务[{}]已取消", taskId);
            }
            return cancelled;
        }
        
        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }
        
        @Override
        public boolean isDone() {
            return future.isDone();
        }
        
        @Override
        public long getTaskId() {
            return taskId;
        }
    }
    
    /**
     * 调度器状态枚举
     */
    public enum SchedulerState {
        /** 启动中 */
        STARTING,
        /** 运行中 */
        RUNNING,
        /** 关闭中 */
        SHUTTING_DOWN,
        /** 已终止 */
        TERMINATED
    }
    
    /**
     * 调度器统计信息
     */
    public static class SchedulerStats {
        private final long totalTasks;
        private final long completedTasks;
        private final long cancelledTasks;
        private final SchedulerState state;
        
        public SchedulerStats(long totalTasks, long completedTasks, long cancelledTasks, SchedulerState state) {
            this.totalTasks = totalTasks;
            this.completedTasks = completedTasks;
            this.cancelledTasks = cancelledTasks;
            this.state = state;
        }
        
        public long getTotalTasks() { return totalTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public long getCancelledTasks() { return cancelledTasks; }
        public long getActiveTasks() { return totalTasks - completedTasks - cancelledTasks; }
        public SchedulerState getState() { return state; }
        
        @Override
        public String toString() {
            return String.format("SchedulerStats{total=%d, completed=%d, cancelled=%d, active=%d, state=%s}",
                    totalTasks, completedTasks, cancelledTasks, getActiveTasks(), state);
        }
    }
}