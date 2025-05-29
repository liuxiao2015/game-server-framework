/*
 * 文件名: ActorSystem.java
 * 用途: Actor系统核心接口（前向声明）
 * 实现内容:
 *   - 定义Actor系统的基本接口
 *   - 后续在system包中提供完整实现
 * 技术选型:
 *   - 接口设计避免循环依赖
 * 依赖关系:
 *   - 被ActorRef引用
 *   - 在system包中提供具体实现
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

/**
 * Actor系统接口（前向声明）
 * <p>
 * 这是一个前向声明接口，避免循环依赖。
 * 完整的实现在system包中提供。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface ActorSystem {
    /**
     * 获取系统名称
     *
     * @return 系统名称
     */
    String getName();
    
    /**
     * 获取死信队列
     *
     * @return 死信队列ActorRef
     */
    ActorRef deadLetters();
}