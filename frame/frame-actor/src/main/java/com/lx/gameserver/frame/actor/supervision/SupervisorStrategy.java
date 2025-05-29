/*
 * 文件名: SupervisorStrategy.java
 * 用途: 监督策略接口（前向声明）
 * 实现内容:
 *   - 定义监督策略的基本接口
 *   - 后续在supervision包中提供完整实现
 * 技术选型:
 *   - 接口设计避免循环依赖
 * 依赖关系:
 *   - 被ActorProps和ActorContext引用
 *   - 在supervision包中提供具体实现
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.supervision;

/**
 * 监督策略接口（前向声明）
 * <p>
 * 这是一个前向声明接口，避免循环依赖。
 * 完整的实现在supervision包中提供。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface SupervisorStrategy {
    
    /**
     * 监督决策枚举
     */
    enum Directive {
        /** 恢复Actor */
        RESUME,
        /** 重启Actor */
        RESTART,
        /** 停止Actor */
        STOP,
        /** 向上级传递 */
        ESCALATE
    }
    
    /**
     * 决定如何处理失败
     *
     * @param cause 失败原因
     * @return 监督决策
     */
    Directive decide(Throwable cause);
}