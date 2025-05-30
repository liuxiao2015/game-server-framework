/*
 * 文件名: ActorSupervisor.java
 * 用途: Actor监督者实现
 * 实现内容:
 *   - 监督者实现，管理子Actor
 *   - 子Actor监控和失败检测
 *   - 失败处理和重启策略执行
 *   - 死亡通知和监督层级传播
 * 技术选型:
 *   - 观察者模式监控子Actor状态
 *   - 策略模式执行不同监督策略
 *   - 并发安全的状态管理
 * 依赖关系:
 *   - 与SupervisorStrategy协作
 *   - 管理Actor生命周期
 *   - 支持监督层级传播
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.supervision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actor监督者实现
 * <p>
 * 负责监督子Actor的生命周期，处理失败情况，
 * 执行重启策略等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorSupervisor {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorSupervisor.class);
    
    /** 监督策略 */
    private final SupervisorStrategy strategy;
    
    /** 监督的Actor路径 */
    private final String supervisedActorPath;
    
    /** 失败统计信息 */
    private final ConcurrentHashMap<String, FailureStats> failureStatsMap = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     *
     * @param strategy              监督策略
     * @param supervisedActorPath   被监督的Actor路径
     */
    public ActorSupervisor(SupervisorStrategy strategy, String supervisedActorPath) {
        this.strategy = strategy;
        this.supervisedActorPath = supervisedActorPath;
    }
    
    /**
     * 处理子Actor失败
     *
     * @param childActorPath 失败的子Actor路径
     * @param cause          失败原因
     * @param message        导致失败的消息
     * @return 监督决策
     */
    public SupervisorStrategy.Directive handleChildFailure(String childActorPath, Throwable cause, Object message) {
        logger.warn("子Actor[{}]失败: {}, 消息: {}", childActorPath, cause.getMessage(), message);
        
        // 获取或创建失败统计
        FailureStats stats = failureStatsMap.computeIfAbsent(childActorPath, k -> new FailureStats());
        
        // 检查是否超过重试限制
        if (isWithinTimeWindow(stats) && stats.getFailureCount() >= strategy.getMaxRetries()) {
            logger.error("子Actor[{}]在时间窗口内失败次数超过限制[{}]，执行停止策略", 
                    childActorPath, strategy.getMaxRetries());
            SupervisorStrategy.Directive directive = SupervisorStrategy.Directive.STOP;
            strategy.handleFailure(childActorPath, cause, directive);
            return directive;
        }
        
        // 更新失败统计
        stats.recordFailure();
        
        // 执行监督决策
        SupervisorStrategy.Directive directive = strategy.decide(cause);
        strategy.handleFailure(childActorPath, cause, directive);
        
        return directive;
    }
    
    /**
     * 处理子Actor成功处理消息
     *
     * @param childActorPath 子Actor路径
     */
    public void handleChildSuccess(String childActorPath) {
        // 成功处理消息时，可以重置失败统计
        FailureStats stats = failureStatsMap.get(childActorPath);
        if (stats != null && !isWithinTimeWindow(stats)) {
            stats.reset();
        }
    }
    
    /**
     * 检查失败是否在时间窗口内
     *
     * @param stats 失败统计
     * @return 如果在时间窗口内返回true
     */
    private boolean isWithinTimeWindow(FailureStats stats) {
        if (stats.getFirstFailureTime() == null) {
            return false;
        }
        
        Instant now = Instant.now();
        Instant windowStart = stats.getFirstFailureTime();
        Duration elapsed = Duration.between(windowStart, now);
        
        return elapsed.compareTo(strategy.getWithinTimeRange()) <= 0;
    }
    
    /**
     * 添加监视的子Actor
     *
     * @param childActorPath 子Actor路径
     */
    public void watchChild(String childActorPath) {
        logger.debug("开始监视子Actor: {}", childActorPath);
        failureStatsMap.putIfAbsent(childActorPath, new FailureStats());
    }
    
    /**
     * 移除监视的子Actor
     *
     * @param childActorPath 子Actor路径
     */
    public void unwatchChild(String childActorPath) {
        logger.debug("停止监视子Actor: {}", childActorPath);
        failureStatsMap.remove(childActorPath);
    }
    
    /**
     * 获取监督策略
     *
     * @return 监督策略
     */
    public SupervisorStrategy getStrategy() {
        return strategy;
    }
    
    /**
     * 获取被监督的Actor路径
     *
     * @return Actor路径
     */
    public String getSupervisedActorPath() {
        return supervisedActorPath;
    }
    
    /**
     * 获取子Actor的失败统计
     *
     * @param childActorPath 子Actor路径
     * @return 失败统计，如果不存在返回null
     */
    public FailureStats getFailureStats(String childActorPath) {
        return failureStatsMap.get(childActorPath);
    }
    
    /**
     * 获取监督的子Actor数量
     *
     * @return 子Actor数量
     */
    public int getChildrenCount() {
        return failureStatsMap.size();
    }
    
    /**
     * 失败统计信息类
     */
    public static class FailureStats {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile Instant firstFailureTime;
        private volatile Instant lastFailureTime;
        
        /**
         * 记录一次失败
         */
        public void recordFailure() {
            Instant now = Instant.now();
            int count = failureCount.incrementAndGet();
            
            if (count == 1) {
                firstFailureTime = now;
            }
            lastFailureTime = now;
        }
        
        /**
         * 重置失败统计
         */
        public void reset() {
            failureCount.set(0);
            firstFailureTime = null;
            lastFailureTime = null;
        }
        
        /**
         * 获取失败次数
         *
         * @return 失败次数
         */
        public int getFailureCount() {
            return failureCount.get();
        }
        
        /**
         * 获取首次失败时间
         *
         * @return 首次失败时间
         */
        public Instant getFirstFailureTime() {
            return firstFailureTime;
        }
        
        /**
         * 获取最后失败时间
         *
         * @return 最后失败时间
         */
        public Instant getLastFailureTime() {
            return lastFailureTime;
        }
        
        @Override
        public String toString() {
            return String.format("FailureStats{count=%d, first=%s, last=%s}", 
                    failureCount.get(), firstFailureTime, lastFailureTime);
        }
    }
}