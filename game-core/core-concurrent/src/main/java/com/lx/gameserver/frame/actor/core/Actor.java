/*
 * 文件名: Actor.java
 * 用途: Actor基类定义
 * 实现内容:
 *   - 所有游戏Actor的父类
 *   - 唯一标识符（ActorId）管理
 *   - 内部状态管理（State Pattern）
 *   - 消息处理接口（receive方法）
 *   - 生命周期回调（preStart、postStop、preRestart、postRestart）
 *   - 错误处理机制和定时器支持
 * 技术选型:
 *   - 抽象类设计提供基础实现
 *   - 状态模式管理Actor状态
 *   - 模板方法模式定义生命周期
 * 依赖关系:
 *   - 与ActorContext协作处理消息
 *   - 被具体Actor实现类继承
 *   - 集成监督和调度机制
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Actor基类
 * <p>
 * 所有Actor的基类，提供了Actor的基本生命周期管理、
 * 消息处理、状态管理等核心功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class Actor {
    
    /** 日志记录器 */
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    /** Actor上下文 */
    private ActorContext context;
    
    /** Actor唯一标识符 */
    private final String actorId;
    
    /** Actor当前状态 */
    private ActorState state = ActorState.CREATED;
    
    /** 构造函数 */
    protected Actor() {
        this.actorId = generateActorId();
    }
    
    /**
     * 生成Actor唯一标识符
     *
     * @return 唯一标识符
     */
    private String generateActorId() {
        return getClass().getSimpleName() + "-" + 
               System.currentTimeMillis() + "-" + 
               ThreadLocalRandom.current().nextInt(10000);
    }
    
    /**
     * 设置Actor上下文（由ActorSystem调用）
     *
     * @param context Actor上下文
     */
    public final void setContext(ActorContext context) {
        this.context = context;
    }
    
    /**
     * 获取Actor上下文
     *
     * @return Actor上下文
     */
    protected final ActorContext getContext() {
        return context;
    }
    
    /**
     * 获取自身Actor引用
     *
     * @return 自身ActorRef
     */
    protected final ActorRef getSelf() {
        return context != null ? context.getSelf() : null;
    }
    
    /**
     * 获取当前消息的发送者
     *
     * @return 发送者ActorRef
     */
    protected final ActorRef getSender() {
        return context != null ? context.getSender() : ActorRef.noSender();
    }
    
    /**
     * 获取Actor唯一标识符
     *
     * @return Actor ID
     */
    public final String getActorId() {
        return actorId;
    }
    
    /**
     * 获取Actor当前状态
     *
     * @return Actor状态
     */
    public final ActorState getState() {
        return state;
    }
    
    /**
     * 设置Actor状态（内部使用）
     *
     * @param state 新状态
     */
    public final void setState(ActorState state) {
        this.state = state;
        logger.debug("Actor[{}] 状态变更为: {}", actorId, state);
    }
    
    /**
     * 抽象消息接收方法（子类必须实现）
     *
     * @return 消息接收行为
     */
    public abstract Receive createReceive();
    
    /**
     * Actor启动前回调
     * <p>
     * 在Actor开始处理消息之前调用，用于初始化操作。
     * </p>
     */
    public void preStart() {
        logger.debug("Actor[{}] 启动前初始化", actorId);
    }
    
    /**
     * Actor停止后回调
     * <p>
     * 在Actor停止后调用，用于清理资源。
     * </p>
     */
    public void postStop() {
        logger.debug("Actor[{}] 停止后清理", actorId);
    }
    
    /**
     * Actor重启前回调
     * <p>
     * 在Actor因异常重启前调用。
     * </p>
     *
     * @param reason 重启原因
     * @param message 导致失败的消息
     */
    public void preRestart(Throwable reason, Object message) {
        logger.warn("Actor[{}] 重启前处理，原因: {}", actorId, reason.getMessage());
        postStop();
    }
    
    /**
     * Actor重启后回调
     * <p>
     * 在Actor重启后调用。
     * </p>
     *
     * @param reason 重启原因
     */
    public void postRestart(Throwable reason) {
        logger.info("Actor[{}] 重启后处理", actorId);
        preStart();
    }
    
    /**
     * 处理未处理的消息
     * <p>
     * 当消息无法被receive方法处理时调用。
     * </p>
     *
     * @param message 未处理的消息
     */
    public void unhandled(Object message) {
        logger.warn("Actor[{}] 收到未处理的消息: {}", actorId, message);
        if (message instanceof Message) {
            // 将未处理的消息发送到死信队列
            getContext().getSystem().deadLetters().tell(message, getSelf());
        }
    }
    
    /**
     * 处理Actor失败
     *
     * @param cause 失败原因
     * @param message 导致失败的消息
     */
    public void supervisorStrategy(Throwable cause, Object message) {
        logger.error("Actor[{}] 处理消息失败: {}", actorId, message, cause);
        // 默认处理：记录错误并继续
    }
    
    /**
     * 调度延迟消息
     *
     * @param delay 延迟时间
     * @param message 消息内容
     * @return 可取消的任务
     */
    protected final ActorContext.Cancellable scheduleOnce(Duration delay, Object message) {
        return getContext().schedule(delay, getSelf(), message);
    }
    
    /**
     * 调度周期消息
     *
     * @param initialDelay 初始延迟
     * @param interval 重复间隔
     * @param message 消息内容
     * @return 可取消的任务
     */
    protected final ActorContext.Cancellable schedule(Duration initialDelay, Duration interval, Object message) {
        return getContext().scheduleAtFixedRate(initialDelay, interval, getSelf(), message);
    }
    
    /**
     * 创建子Actor
     *
     * @param props Actor属性
     * @param name Actor名称
     * @return 子Actor引用
     */
    protected final ActorRef actorOf(ActorProps props, String name) {
        return getContext().actorOf(props, name);
    }
    
    /**
     * 创建子Actor（自动生成名称）
     *
     * @param props Actor属性
     * @return 子Actor引用
     */
    protected final ActorRef actorOf(ActorProps props) {
        return getContext().actorOf(props);
    }
    
    /**
     * 停止子Actor
     *
     * @param child 子Actor引用
     */
    protected final void stop(ActorRef child) {
        getContext().stop(child);
    }
    
    /**
     * 停止自身
     */
    protected final void stop() {
        getContext().stop();
    }
    
    /**
     * 监视其他Actor
     *
     * @param actorRef 要监视的Actor
     */
    protected final void watch(ActorRef actorRef) {
        getContext().watch(actorRef);
    }
    
    /**
     * 取消监视其他Actor
     *
     * @param actorRef 要取消监视的Actor
     */
    protected final void unwatch(ActorRef actorRef) {
        getContext().unwatch(actorRef);
    }
    
    /**
     * 切换行为
     *
     * @param behavior 新的行为
     */
    protected final void become(Receive behavior) {
        getContext().become(behavior);
    }
    
    /**
     * 恢复原始行为
     */
    protected final void unbecome() {
        getContext().unbecome();
    }
    
    @Override
    public String toString() {
        return String.format("Actor[%s, id=%s, state=%s]", 
                getClass().getSimpleName(), actorId, state);
    }
    
    /**
     * Actor状态枚举
     */
    public enum ActorState {
        /** 已创建 */
        CREATED,
        /** 正在启动 */
        STARTING,
        /** 运行中 */
        RUNNING,
        /** 正在停止 */
        STOPPING,
        /** 已停止 */
        STOPPED,
        /** 正在重启 */
        RESTARTING,
        /** 已失败 */
        FAILED
    }
}