/*
 * 文件名: SupervisorStrategy.java
 * 用途: 监督策略接口和基本实现
 * 实现内容:
 *   - 监督策略抽象类
 *   - OneForOneStrategy：单个子Actor失败处理
 *   - AllForOneStrategy：所有子Actor失败处理
 *   - 失败决策：Resume、Restart、Stop、Escalate
 *   - 支持自定义决策函数
 * 技术选型:
 *   - 抽象类设计提供基础实现
 *   - 策略模式支持不同监督策略
 *   - 函数式接口支持自定义决策
 * 依赖关系:
 *   - 被ActorProps和ActorContext使用
 *   - 与Actor生命周期管理集成
 *   - 支持异常处理和恢复机制
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.supervision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Function;

/**
 * 监督策略抽象类
 * <p>
 * 定义了Actor监督的基本策略和决策逻辑，
 * 包含常见的监督策略实现。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class SupervisorStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(SupervisorStrategy.class);
    
    /**
     * 监督决策枚举
     */
    public enum Directive {
        /** 恢复Actor，继续处理消息 */
        RESUME,
        /** 重启Actor，清除状态重新开始 */
        RESTART,
        /** 停止Actor，终止其生命周期 */
        STOP,
        /** 向上级传递异常 */
        ESCALATE
    }
    
    /** 最大重试次数 */
    protected final int maxRetries;
    
    /** 时间窗口 */
    protected final Duration withinTimeRange;
    
    /** 决策函数 */
    protected final Function<Throwable, Directive> decider;
    
    /**
     * 构造函数
     *
     * @param maxRetries       最大重试次数
     * @param withinTimeRange  时间窗口
     * @param decider          决策函数
     */
    protected SupervisorStrategy(int maxRetries, Duration withinTimeRange, 
                                Function<Throwable, Directive> decider) {
        this.maxRetries = maxRetries;
        this.withinTimeRange = withinTimeRange;
        this.decider = decider;
    }
    
    /**
     * 决定如何处理失败
     *
     * @param cause 失败原因
     * @return 监督决策
     */
    public Directive decide(Throwable cause) {
        try {
            return decider.apply(cause);
        } catch (Exception e) {
            logger.error("监督决策函数执行失败", e);
            return Directive.ESCALATE;
        }
    }
    
    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 获取时间窗口
     *
     * @return 时间窗口
     */
    public Duration getWithinTimeRange() {
        return withinTimeRange;
    }
    
    /**
     * 处理子Actor失败（由子类实现具体策略）
     *
     * @param failedActor 失败的Actor路径
     * @param cause       失败原因
     * @param directive   监督决策
     */
    public abstract void handleFailure(String failedActor, Throwable cause, Directive directive);
    
    /**
     * 默认决策函数
     *
     * @return 默认决策函数
     */
    public static Function<Throwable, Directive> defaultDecider() {
        return cause -> {
            if (cause instanceof IllegalArgumentException || 
                cause instanceof NullPointerException) {
                return Directive.RESTART;
            } else if (cause instanceof InterruptedException) {
                return Directive.STOP;
            } else if (cause instanceof RuntimeException) {
                return Directive.RESTART;
            } else {
                return Directive.ESCALATE;
            }
        };
    }
    
    /**
     * 创建always重启策略
     *
     * @return 总是重启的决策函数
     */
    public static Function<Throwable, Directive> alwaysRestart() {
        return cause -> Directive.RESTART;
    }
    
    /**
     * 创建always停止策略
     *
     * @return 总是停止的决策函数
     */
    public static Function<Throwable, Directive> alwaysStop() {
        return cause -> Directive.STOP;
    }
    
    /**
     * 创建always恢复策略
     *
     * @return 总是恢复的决策函数
     */
    public static Function<Throwable, Directive> alwaysResume() {
        return cause -> Directive.RESUME;
    }
    
    /**
     * 创建always上升策略
     *
     * @return 总是上升的决策函数
     */
    public static Function<Throwable, Directive> alwaysEscalate() {
        return cause -> Directive.ESCALATE;
    }
    
    /**
     * OneForOne监督策略
     * <p>
     * 只影响失败的子Actor，其他子Actor继续正常运行。
     * </p>
     */
    public static class OneForOneStrategy extends SupervisorStrategy {
        
        public OneForOneStrategy(int maxRetries, Duration withinTimeRange, 
                                Function<Throwable, Directive> decider) {
            super(maxRetries, withinTimeRange, decider);
        }
        
        public OneForOneStrategy() {
            this(3, Duration.ofMinutes(1), defaultDecider());
        }
        
        @Override
        public void handleFailure(String failedActor, Throwable cause, Directive directive) {
            logger.info("OneForOne策略处理Actor[{}]失败: {} -> {}", failedActor, cause.getMessage(), directive);
            
            switch (directive) {
                case RESUME -> logger.debug("恢复Actor[{}]继续运行", failedActor);
                case RESTART -> logger.info("重启Actor[{}]", failedActor);
                case STOP -> logger.info("停止Actor[{}]", failedActor);
                case ESCALATE -> logger.warn("向上级传递Actor[{}]的异常", failedActor);
            }
        }
    }
    
    /**
     * AllForOne监督策略
     * <p>
     * 一个子Actor失败会影响所有子Actor。
     * </p>
     */
    public static class AllForOneStrategy extends SupervisorStrategy {
        
        public AllForOneStrategy(int maxRetries, Duration withinTimeRange, 
                                Function<Throwable, Directive> decider) {
            super(maxRetries, withinTimeRange, decider);
        }
        
        public AllForOneStrategy() {
            this(3, Duration.ofMinutes(1), defaultDecider());
        }
        
        @Override
        public void handleFailure(String failedActor, Throwable cause, Directive directive) {
            logger.info("AllForOne策略处理Actor[{}]失败，影响所有子Actor: {} -> {}", 
                    failedActor, cause.getMessage(), directive);
            
            switch (directive) {
                case RESUME -> logger.debug("恢复所有子Actor继续运行");
                case RESTART -> logger.info("重启所有子Actor");
                case STOP -> logger.info("停止所有子Actor");
                case ESCALATE -> logger.warn("向上级传递异常，影响所有子Actor");
            }
        }
    }
}