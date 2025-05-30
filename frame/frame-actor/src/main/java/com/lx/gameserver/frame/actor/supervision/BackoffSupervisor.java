/*
 * 文件名: BackoffSupervisor.java
 * 用途: 指数退避重启策略
 * 实现内容:
 *   - 指数退避重启策略实现
 *   - 防止频繁重启机制
 *   - 可配置最大重启次数和重启间隔
 *   - 重启窗口期管理
 * 技术选型:
 *   - 指数退避算法
 *   - 时间窗口管理
 *   - 并发安全的状态跟踪
 * 依赖关系:
 *   - 扩展SupervisorStrategy
 *   - 与Actor重启机制集成
 *   - 支持自定义退避策略
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.supervision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 指数退避监督策略
 * <p>
 * 实现指数退避重启策略，防止频繁重启对系统造成的压力。
 * 重启间隔会随着重启次数指数级增长。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class BackoffSupervisor extends SupervisorStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(BackoffSupervisor.class);
    
    /** 初始延迟 */
    private final Duration initialDelay;
    
    /** 最大延迟 */
    private final Duration maxDelay;
    
    /** 退避倍数 */
    private final double backoffMultiplier;
    
    /** 随机因子 */
    private final double randomFactor;
    
    /** 调度器 */
    private final ScheduledExecutorService scheduler;
    
    /** 重启状态跟踪 */
    private final ConcurrentHashMap<String, BackoffState> backoffStates = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     *
     * @param maxRetries         最大重试次数
     * @param withinTimeRange    时间窗口
     * @param decider            决策函数
     * @param initialDelay       初始延迟
     * @param maxDelay           最大延迟
     * @param backoffMultiplier  退避倍数
     * @param randomFactor       随机因子
     * @param scheduler          调度器
     */
    public BackoffSupervisor(int maxRetries, Duration withinTimeRange,
                            Function<Throwable, Directive> decider,
                            Duration initialDelay, Duration maxDelay,
                            double backoffMultiplier, double randomFactor,
                            ScheduledExecutorService scheduler) {
        super(maxRetries, withinTimeRange, decider);
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.backoffMultiplier = backoffMultiplier;
        this.randomFactor = randomFactor;
        this.scheduler = scheduler;
    }
    
    /**
     * 构造函数（使用默认参数）
     *
     * @param scheduler 调度器
     */
    public BackoffSupervisor(ScheduledExecutorService scheduler) {
        this(3, Duration.ofMinutes(1), defaultDecider(),
             Duration.ofSeconds(1), Duration.ofMinutes(5),
             2.0, 0.1, scheduler);
    }
    
    @Override
    public void handleFailure(String failedActor, Throwable cause, Directive directive) {
        logger.info("BackoffSupervisor处理Actor[{}]失败: {} -> {}", failedActor, cause.getMessage(), directive);
        
        if (directive == Directive.RESTART) {
            scheduleRestart(failedActor, cause);
        } else {
            // 清理状态
            backoffStates.remove(failedActor);
            logDirectiveAction(failedActor, directive);
        }
    }
    
    /**
     * 调度重启
     *
     * @param actorPath Actor路径
     * @param cause     失败原因
     */
    private void scheduleRestart(String actorPath, Throwable cause) {
        BackoffState state = backoffStates.computeIfAbsent(actorPath, k -> new BackoffState());
        
        // 检查是否超过最大重启次数
        if (state.getRestartCount() >= maxRetries) {
            logger.error("Actor[{}]重启次数超过限制[{}]，停止重启", actorPath, maxRetries);
            backoffStates.remove(actorPath);
            // 这里应该停止Actor
            return;
        }
        
        // 计算延迟时间
        Duration delay = calculateDelay(state.getRestartCount());
        state.incrementRestartCount();
        
        logger.info("调度Actor[{}]在{}后重启，当前重启次数: {}", actorPath, delay, state.getRestartCount());
        
        // 调度重启任务
        ScheduledFuture<?> restartTask = scheduler.schedule(
                () -> performRestart(actorPath, cause),
                delay.toMillis(),
                TimeUnit.MILLISECONDS
        );
        
        state.setRestartTask(restartTask);
    }
    
    /**
     * 执行重启
     *
     * @param actorPath Actor路径
     * @param cause     失败原因
     */
    private void performRestart(String actorPath, Throwable cause) {
        logger.info("执行Actor[{}]重启", actorPath);
        
        try {
            // 这里应该调用实际的重启逻辑
            restartActor(actorPath, cause);
            
            // 重启成功，重置状态（在成功处理消息一段时间后）
            scheduleStateReset(actorPath);
            
        } catch (Exception e) {
            logger.error("Actor[{}]重启失败", actorPath, e);
            // 重启失败，继续退避重试
            scheduleRestart(actorPath, e);
        }
    }
    
    /**
     * 实际重启Actor（子类或外部实现）
     *
     * @param actorPath Actor路径
     * @param cause     失败原因
     */
    protected void restartActor(String actorPath, Throwable cause) {
        // 默认实现，记录日志
        logger.info("重启Actor[{}]，原因: {}", actorPath, cause.getMessage());
    }
    
    /**
     * 调度状态重置
     *
     * @param actorPath Actor路径
     */
    private void scheduleStateReset(String actorPath) {
        scheduler.schedule(
                () -> {
                    BackoffState state = backoffStates.get(actorPath);
                    if (state != null && state.canReset()) {
                        state.reset();
                        logger.debug("重置Actor[{}]的退避状态", actorPath);
                    }
                },
                withinTimeRange.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 计算延迟时间
     *
     * @param restartCount 重启次数
     * @return 延迟时间
     */
    private Duration calculateDelay(int restartCount) {
        double delayMs = initialDelay.toMillis() * Math.pow(backoffMultiplier, restartCount);
        
        // 添加随机因子
        if (randomFactor > 0) {
            double randomOffset = delayMs * randomFactor * (Math.random() - 0.5) * 2;
            delayMs += randomOffset;
        }
        
        // 限制最大延迟
        delayMs = Math.min(delayMs, maxDelay.toMillis());
        
        return Duration.ofMillis((long) delayMs);
    }
    
    /**
     * 记录决策动作
     *
     * @param actorPath Actor路径
     * @param directive 监督决策
     */
    private void logDirectiveAction(String actorPath, Directive directive) {
        switch (directive) {
            case RESUME -> logger.debug("恢复Actor[{}]继续运行", actorPath);
            case STOP -> logger.info("停止Actor[{}]", actorPath);
            case ESCALATE -> logger.warn("向上级传递Actor[{}]的异常", actorPath);
        }
    }
    
    /**
     * 取消Actor的重启任务
     *
     * @param actorPath Actor路径
     */
    public void cancelRestart(String actorPath) {
        BackoffState state = backoffStates.remove(actorPath);
        if (state != null && state.getRestartTask() != null) {
            state.getRestartTask().cancel(false);
            logger.info("取消Actor[{}]的重启任务", actorPath);
        }
    }
    
    /**
     * 获取Actor的退避状态
     *
     * @param actorPath Actor路径
     * @return 退避状态
     */
    public BackoffState getBackoffState(String actorPath) {
        return backoffStates.get(actorPath);
    }
    
    /**
     * 退避状态类
     */
    public static class BackoffState {
        private int restartCount = 0;
        private Instant lastRestartTime;
        private ScheduledFuture<?> restartTask;
        
        /**
         * 增加重启次数
         */
        public void incrementRestartCount() {
            restartCount++;
            lastRestartTime = Instant.now();
        }
        
        /**
         * 重置状态
         */
        public void reset() {
            restartCount = 0;
            lastRestartTime = null;
            if (restartTask != null) {
                restartTask.cancel(false);
                restartTask = null;
            }
        }
        
        /**
         * 检查是否可以重置
         *
         * @return 如果可以重置返回true
         */
        public boolean canReset() {
            return restartTask == null || restartTask.isDone();
        }
        
        // Getters and Setters
        public int getRestartCount() {
            return restartCount;
        }
        
        public Instant getLastRestartTime() {
            return lastRestartTime;
        }
        
        public ScheduledFuture<?> getRestartTask() {
            return restartTask;
        }
        
        public void setRestartTask(ScheduledFuture<?> restartTask) {
            this.restartTask = restartTask;
        }
        
        @Override
        public String toString() {
            return String.format("BackoffState{restartCount=%d, lastRestartTime=%s, hasTask=%s}", 
                    restartCount, lastRestartTime, restartTask != null);
        }
    }
}