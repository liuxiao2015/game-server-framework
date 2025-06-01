/*
 * 文件名: ActorRef.java
 * 用途: Actor引用接口
 * 实现内容:
 *   - 提供消息发送能力（tell、ask、forward）
 *   - 支持异步消息发送和请求-响应模式
 *   - 消息优先级和死信队列处理
 *   - Actor路径和标识管理
 * 技术选型:
 *   - 接口设计提供多种实现方式
 *   - CompletableFuture支持异步编程
 *   - 支持消息转发和广播
 * 依赖关系:
 *   - 与Actor协作进行消息传递
 *   - 被ActorSystem管理和路由
 *   - 支持集群间通信
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Actor引用接口
 * <p>
 * 提供向Actor发送消息的能力，支持多种消息发送模式。
 * 是Actor系统中进行消息通信的主要接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface ActorRef extends Serializable {
    
    /**
     * 异步发送消息（fire-and-forget模式）
     *
     * @param message 要发送的消息
     * @param sender  发送者引用
     */
    void tell(Object message, ActorRef sender);
    
    /**
     * 异步发送消息（无发送者）
     *
     * @param message 要发送的消息
     */
    default void tell(Object message) {
        tell(message, ActorRef.noSender());
    }
    
    /**
     * 请求-响应模式发送消息
     *
     * @param message 要发送的消息
     * @param timeout 超时时间
     * @param <T>     响应类型
     * @return 响应的Future对象
     */
    <T> CompletableFuture<T> ask(Object message, Duration timeout);
    
    /**
     * 请求-响应模式发送消息（默认超时）
     *
     * @param message 要发送的消息
     * @param <T>     响应类型
     * @return 响应的Future对象
     */
    default <T> CompletableFuture<T> ask(Object message) {
        return ask(message, Duration.ofSeconds(5));
    }
    
    /**
     * 转发消息（保持原始发送者）
     *
     * @param message 要转发的消息
     * @param target  目标ActorRef
     */
    void forward(Object message, ActorRef target);
    
    /**
     * 发送高优先级消息
     *
     * @param message 要发送的消息
     * @param sender  发送者引用
     */
    void tellWithPriority(Object message, ActorRef sender, int priority);
    
    /**
     * 获取Actor路径
     *
     * @return Actor路径字符串
     */
    String getPath();
    
    /**
     * 获取Actor名称
     *
     * @return Actor名称
     */
    String getName();
    
    /**
     * 获取Actor系统引用
     *
     * @return ActorSystem引用
     */
    ActorSystem getActorSystem();
    
    /**
     * 检查Actor是否已终止
     *
     * @return 如果Actor已终止返回true
     */
    boolean isTerminated();
    
    /**
     * 比较两个ActorRef是否相等
     *
     * @param other 其他ActorRef
     * @return 如果路径相同返回true
     */
    @Override
    boolean equals(Object other);
    
    /**
     * 获取哈希码
     *
     * @return 基于路径的哈希码
     */
    @Override
    int hashCode();
    
    /**
     * 获取字符串表示
     *
     * @return Actor路径字符串
     */
    @Override
    String toString();
    
    /**
     * 创建无发送者的ActorRef
     *
     * @return 特殊的noSender引用
     */
    static ActorRef noSender() {
        return NoSender.INSTANCE;
    }
    
    /**
     * 空发送者实现
     */
    final class NoSender implements ActorRef {
        static final NoSender INSTANCE = new NoSender();
        
        private NoSender() {}
        
        @Override
        public void tell(Object message, ActorRef sender) {
            throw new UnsupportedOperationException("NoSender cannot receive messages");
        }
        
        @Override
        public <T> CompletableFuture<T> ask(Object message, Duration timeout) {
            return CompletableFuture.failedFuture(
                new UnsupportedOperationException("NoSender cannot receive messages"));
        }
        
        @Override
        public void forward(Object message, ActorRef target) {
            target.tell(message, this);
        }
        
        @Override
        public void tellWithPriority(Object message, ActorRef sender, int priority) {
            throw new UnsupportedOperationException("NoSender cannot receive messages");
        }
        
        @Override
        public String getPath() {
            return "akka://system/deadLetters";
        }
        
        @Override
        public String getName() {
            return "deadLetters";
        }
        
        @Override
        public ActorSystem getActorSystem() {
            return null;
        }
        
        @Override
        public boolean isTerminated() {
            return false;
        }
        
        @Override
        public String toString() {
            return "Actor[akka://system/deadLetters]";
        }
        
        @Override
        public boolean equals(Object obj) {
            return obj instanceof NoSender;
        }
        
        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
        
        // 确保单例模式
        private Object readResolve() {
            return INSTANCE;
        }
    }
}