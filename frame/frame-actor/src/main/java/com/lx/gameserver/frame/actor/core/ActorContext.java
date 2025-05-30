/*
 * 文件名: ActorContext.java
 * 用途: Actor执行上下文接口
 * 实现内容:
 *   - 提供Actor运行时环境访问
 *   - 支持获取自身和父子Actor引用
 *   - 提供子Actor创建和监督能力
 *   - 调度器和监控指标访问
 * 技术选型:
 *   - 接口设计提供上下文环境抽象
 *   - 支持Actor层级管理
 *   - 集成调度和监控功能
 * 依赖关系:
 *   - 被Actor实现类使用
 *   - 与ActorSystem和调度器集成
 *   - 支持监督策略和监控
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import com.lx.gameserver.frame.actor.supervision.SupervisorStrategy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Actor执行上下文接口
 * <p>
 * 为Actor提供运行时环境，包括Actor层级管理、消息调度、
 * 监督策略等核心功能的访问接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface ActorContext {
    
    /**
     * 获取自身Actor引用
     *
     * @return 自身ActorRef
     */
    ActorRef getSelf();
    
    /**
     * 获取父Actor引用
     *
     * @return 父Actor引用，如果是根Actor则返回null
     */
    ActorRef getParent();
    
    /**
     * 获取子Actor列表
     *
     * @return 子Actor引用列表
     */
    List<ActorRef> getChildren();
    
    /**
     * 根据名称查找子Actor
     *
     * @param name 子Actor名称
     * @return Actor引用，如果不存在返回null
     */
    ActorRef getChild(String name);
    
    /**
     * 创建子Actor
     *
     * @param props Actor属性配置
     * @param name  Actor名称
     * @return 子Actor引用
     */
    ActorRef actorOf(ActorProps props, String name);
    
    /**
     * 创建子Actor（自动生成名称）
     *
     * @param props Actor属性配置
     * @return 子Actor引用
     */
    ActorRef actorOf(ActorProps props);
    
    /**
     * 停止子Actor
     *
     * @param child 要停止的子Actor引用
     */
    void stop(ActorRef child);
    
    /**
     * 停止自身
     */
    void stop();
    
    /**
     * 获取Actor系统引用
     *
     * @return ActorSystem引用
     */
    ActorSystem getSystem();
    
    /**
     * 获取发送者引用
     *
     * @return 当前消息的发送者
     */
    ActorRef getSender();
    
    /**
     * 调度延迟任务
     *
     * @param delay    延迟时间
     * @param receiver 接收者
     * @param message  消息内容
     * @return 可取消的任务引用
     */
    Cancellable schedule(Duration delay, ActorRef receiver, Object message);
    
    /**
     * 调度周期任务
     *
     * @param initialDelay 初始延迟
     * @param interval     重复间隔
     * @param receiver     接收者
     * @param message      消息内容
     * @return 可取消的任务引用
     */
    Cancellable scheduleAtFixedRate(Duration initialDelay, Duration interval, ActorRef receiver, Object message);
    
    /**
     * 设置监督策略
     *
     * @param strategy 监督策略
     */
    void setSupervisorStrategy(SupervisorStrategy strategy);
    
    /**
     * 获取监督策略
     *
     * @return 当前监督策略
     */
    SupervisorStrategy getSupervisorStrategy();
    
    /**
     * 监视其他Actor
     *
     * @param actorRef 要监视的Actor
     */
    void watch(ActorRef actorRef);
    
    /**
     * 取消监视其他Actor
     *
     * @param actorRef 要取消监视的Actor
     */
    void unwatch(ActorRef actorRef);
    
    /**
     * 成为其他Actor的行为
     *
     * @param behavior 新的行为
     */
    void become(Receive behavior);
    
    /**
     * 取消成为行为，恢复原始行为
     */
    void unbecome();
    
    /**
     * 可取消的任务接口
     */
    interface Cancellable {
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
    }
}