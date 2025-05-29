/*
 * 文件名: GameTask.java
 * 用途: 任务基类
 * 实现内容:
 *   - 定义任务的基本属性和行为
 *   - 任务ID、名称、优先级
 *   - 执行状态跟踪
 *   - 重试机制支持
 * 技术选型:
 *   - 抽象类设计模式
 *   - 状态机模式管理任务状态
 *   - 重试策略和异常处理
 * 依赖关系:
 *   - 被具体业务任务继承
 *   - 与GameTaskScheduler协作调度
 *   - 统一任务管理和监控
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务基类
 * <p>
 * 定义任务的基本属性和行为，包括任务ID、名称、优先级、执行状态跟踪、重试机制等。
 * 所有游戏任务都应该继承此基类以获得统一的任务管理能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class GameTask implements Runnable, Comparable<GameTask> {

    private static final Logger logger = LoggerFactory.getLogger(GameTask.class);

    /**
     * 全局任务ID生成器
     */
    private static final AtomicLong TASK_ID_GENERATOR = new AtomicLong(0);

    /**
     * 任务ID
     */
    private final long taskId;

    /**
     * 任务名称
     */
    private final String taskName;

    /**
     * 任务描述
     */
    private final String description;

    /**
     * 任务优先级
     */
    private final int priority;

    /**
     * 任务状态
     */
    private final AtomicReference<TaskStatus> status = new AtomicReference<>(TaskStatus.CREATED);

    /**
     * 创建时间
     */
    private final long createTime;

    /**
     * 开始执行时间
     */
    private volatile long startTime;

    /**
     * 完成时间
     */
    private volatile long completeTime;

    /**
     * 重试次数
     */
    private final AtomicInteger retryCount = new AtomicInteger(0);

    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 重试延迟（毫秒）
     */
    private final long retryDelay;

    /**
     * 执行异常
     */
    private volatile Throwable exception;

    /**
     * 任务上下文
     */
    private volatile Object context;

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        /**
         * 已创建
         */
        CREATED,
        /**
         * 排队中
         */
        QUEUED,
        /**
         * 执行中
         */
        RUNNING,
        /**
         * 已完成
         */
        COMPLETED,
        /**
         * 已取消
         */
        CANCELLED,
        /**
         * 执行失败
         */
        FAILED,
        /**
         * 重试中
         */
        RETRYING
    }

    /**
     * 任务优先级常量
     */
    public static class Priority {
        public static final int HIGHEST = 1;
        public static final int HIGH = 3;
        public static final int NORMAL = 5;
        public static final int LOW = 7;
        public static final int LOWEST = 9;
    }

    /**
     * 构造函数 - 使用默认配置
     *
     * @param taskName 任务名称
     */
    protected GameTask(String taskName) {
        this(taskName, null, Priority.NORMAL, 0, 0);
    }

    /**
     * 构造函数 - 指定优先级
     *
     * @param taskName 任务名称
     * @param priority 任务优先级
     */
    protected GameTask(String taskName, int priority) {
        this(taskName, null, priority, 0, 0);
    }

    /**
     * 构造函数 - 完整配置
     *
     * @param taskName    任务名称
     * @param description 任务描述
     * @param priority    任务优先级
     * @param maxRetries  最大重试次数
     * @param retryDelay  重试延迟（毫秒）
     */
    protected GameTask(String taskName, String description, int priority, int maxRetries, long retryDelay) {
        this.taskId = TASK_ID_GENERATOR.incrementAndGet();
        this.taskName = taskName != null ? taskName : "GameTask-" + taskId;
        this.description = description;
        this.priority = Math.max(1, Math.min(9, priority)); // 限制在1-9范围内
        this.maxRetries = Math.max(0, maxRetries);
        this.retryDelay = Math.max(0, retryDelay);
        this.createTime = System.currentTimeMillis();
    }

    @Override
    public final void run() {
        if (!status.compareAndSet(TaskStatus.QUEUED, TaskStatus.RUNNING) && 
            !status.compareAndSet(TaskStatus.RETRYING, TaskStatus.RUNNING)) {
            logger.warn("任务[{}]状态异常，无法执行", getTaskInfo());
            return;
        }

        startTime = System.currentTimeMillis();
        logger.debug("任务[{}]开始执行", getTaskInfo());

        try {
            // 执行具体任务逻辑
            doExecute();
            
            // 任务执行成功
            completeTime = System.currentTimeMillis();
            status.set(TaskStatus.COMPLETED);
            logger.debug("任务[{}]执行完成，耗时:{}ms", getTaskInfo(), getExecutionTime());
            
            // 调用成功回调
            onSuccess();
            
        } catch (Exception e) {
            exception = e;
            logger.error("任务[{}]执行失败", getTaskInfo(), e);
            
            // 检查是否需要重试
            if (shouldRetry()) {
                scheduleRetry();
            } else {
                // 任务最终失败
                completeTime = System.currentTimeMillis();
                status.set(TaskStatus.FAILED);
                
                // 调用失败回调
                onFailure(e);
            }
        }
    }

    /**
     * 具体任务执行逻辑（由子类实现）
     */
    protected abstract void doExecute() throws Exception;

    /**
     * 任务成功回调
     */
    protected void onSuccess() {
        // 子类可以重写此方法
    }

    /**
     * 任务失败回调
     *
     * @param e 异常信息
     */
    protected void onFailure(Throwable e) {
        // 子类可以重写此方法
    }

    /**
     * 重试前回调
     *
     * @param retryAttempt 重试次数
     */
    protected void onRetry(int retryAttempt) {
        // 子类可以重写此方法
    }

    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry() {
        return maxRetries > 0 && retryCount.get() < maxRetries && !isInterruptedException();
    }

    /**
     * 判断是否为中断异常
     */
    private boolean isInterruptedException() {
        return exception instanceof InterruptedException || 
               (exception.getCause() instanceof InterruptedException);
    }

    /**
     * 安排重试
     */
    private void scheduleRetry() {
        int currentRetry = retryCount.incrementAndGet();
        status.set(TaskStatus.RETRYING);
        
        logger.info("任务[{}]准备第{}次重试", getTaskInfo(), currentRetry);
        
        // 调用重试回调
        onRetry(currentRetry);
        
        // 如果有重试延迟，需要在延迟后重新提交任务
        if (retryDelay > 0) {
            // 这里应该通过调度器来延迟重试，暂时记录日志
            logger.debug("任务[{}]将在{}ms后重试", getTaskInfo(), retryDelay);
        }
        
        // 重置异常
        exception = null;
    }

    /**
     * 取消任务
     */
    public boolean cancel() {
        TaskStatus currentStatus = status.get();
        if (currentStatus == TaskStatus.COMPLETED || currentStatus == TaskStatus.FAILED || 
            currentStatus == TaskStatus.CANCELLED) {
            return false;
        }
        
        boolean cancelled = status.compareAndSet(currentStatus, TaskStatus.CANCELLED);
        if (cancelled) {
            logger.info("任务[{}]已取消", getTaskInfo());
        }
        return cancelled;
    }

    /**
     * 设置任务状态为排队
     */
    public void setQueued() {
        if (status.compareAndSet(TaskStatus.CREATED, TaskStatus.QUEUED)) {
            logger.debug("任务[{}]进入排队状态", getTaskInfo());
        }
    }

    @Override
    public int compareTo(GameTask other) {
        // 按优先级排序（数字越小优先级越高）
        int priorityComparison = Integer.compare(this.priority, other.priority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        
        // 优先级相同时按创建时间排序
        return Long.compare(this.createTime, other.createTime);
    }

    // ===== Getter方法 =====

    public long getTaskId() {
        return taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status.get();
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getCompleteTime() {
        return completeTime;
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getRetryDelay() {
        return retryDelay;
    }

    public Throwable getException() {
        return exception;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    /**
     * 获取任务等待时间（毫秒）
     */
    public long getWaitTime() {
        return startTime > 0 ? startTime - createTime : System.currentTimeMillis() - createTime;
    }

    /**
     * 获取任务执行时间（毫秒）
     */
    public long getExecutionTime() {
        if (startTime == 0) {
            return 0;
        }
        long endTime = completeTime > 0 ? completeTime : System.currentTimeMillis();
        return endTime - startTime;
    }

    /**
     * 获取任务总时间（毫秒）
     */
    public long getTotalTime() {
        long endTime = completeTime > 0 ? completeTime : System.currentTimeMillis();
        return endTime - createTime;
    }

    /**
     * 判断任务是否已完成
     */
    public boolean isCompleted() {
        TaskStatus currentStatus = status.get();
        return currentStatus == TaskStatus.COMPLETED || currentStatus == TaskStatus.FAILED || 
               currentStatus == TaskStatus.CANCELLED;
    }

    /**
     * 判断任务是否成功
     */
    public boolean isSuccessful() {
        return status.get() == TaskStatus.COMPLETED;
    }

    /**
     * 判断任务是否失败
     */
    public boolean isFailed() {
        return status.get() == TaskStatus.FAILED;
    }

    /**
     * 判断任务是否被取消
     */
    public boolean isCancelled() {
        return status.get() == TaskStatus.CANCELLED;
    }

    /**
     * 获取任务信息字符串
     */
    public String getTaskInfo() {
        return String.format("id=%d, name=%s, priority=%d", taskId, taskName, priority);
    }

    @Override
    public String toString() {
        return String.format("GameTask[%s, status=%s, retries=%d/%d, waitTime=%dms, execTime=%dms]",
            getTaskInfo(), status.get(), retryCount.get(), maxRetries, getWaitTime(), getExecutionTime());
    }
}