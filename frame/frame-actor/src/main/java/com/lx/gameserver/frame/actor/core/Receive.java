/*
 * 文件名: Receive.java
 * 用途: Actor消息接收行为接口
 * 实现内容:
 *   - 定义Actor的消息处理行为
 *   - 支持模式匹配和消息类型路由
 *   - 提供函数式消息处理接口
 * 技术选型:
 *   - 函数式接口设计
 *   - 支持链式调用和组合
 *   - 模式匹配消息处理
 * 依赖关系:
 *   - 被Actor用于定义消息处理逻辑
 *   - 与ActorContext协作处理消息
 *   - 支持行为动态切换
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Actor消息接收行为接口
 * <p>
 * 定义了Actor处理消息的行为模式，支持模式匹配
 * 和类型安全的消息处理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@FunctionalInterface
public interface Receive {
    
    /**
     * 应用消息处理逻辑
     *
     * @param message 要处理的消息
     * @return 如果消息被处理返回true，否则返回false
     */
    boolean apply(Object message);
    
    /**
     * 创建基于类型匹配的接收器
     *
     * @param messageType 消息类型
     * @param handler     处理函数
     * @param <T>         消息类型参数
     * @return Receive实例
     */
    static <T> Receive match(Class<T> messageType, Function<T, Void> handler) {
        return message -> {
            if (messageType.isInstance(message)) {
                handler.apply(messageType.cast(message));
                return true;
            }
            return false;
        };
    }
    
    /**
     * 创建基于类型和条件匹配的接收器
     *
     * @param messageType 消息类型
     * @param predicate   匹配条件
     * @param handler     处理函数
     * @param <T>         消息类型参数
     * @return Receive实例
     */
    static <T> Receive matchIf(Class<T> messageType, Predicate<T> predicate, Function<T, Void> handler) {
        return message -> {
            if (messageType.isInstance(message)) {
                T typedMessage = messageType.cast(message);
                if (predicate.test(typedMessage)) {
                    handler.apply(typedMessage);
                    return true;
                }
            }
            return false;
        };
    }
    
    /**
     * 创建匹配任意消息的接收器
     *
     * @param handler 处理函数
     * @return Receive实例
     */
    static Receive matchAny(Function<Object, Void> handler) {
        return message -> {
            handler.apply(message);
            return true;
        };
    }
    
    /**
     * 组合多个接收器
     *
     * @param other 其他接收器
     * @return 组合后的接收器
     */
    default Receive orElse(Receive other) {
        return message -> this.apply(message) || other.apply(message);
    }
    
    /**
     * 创建接收器构建器
     *
     * @return 构建器实例
     */
    static ReceiveBuilder receiveBuilder() {
        return new ReceiveBuilder();
    }
    
    /**
     * 接收器构建器
     */
    class ReceiveBuilder {
        private Receive receive = message -> false;
        
        /**
         * 添加类型匹配规则
         *
         * @param messageType 消息类型
         * @param handler     处理函数
         * @param <T>         消息类型参数
         * @return 构建器实例
         */
        public <T> ReceiveBuilder match(Class<T> messageType, Function<T, Void> handler) {
            Receive newReceive = Receive.match(messageType, handler);
            this.receive = this.receive.orElse(newReceive);
            return this;
        }
        
        /**
         * 添加条件匹配规则
         *
         * @param messageType 消息类型
         * @param predicate   匹配条件
         * @param handler     处理函数
         * @param <T>         消息类型参数
         * @return 构建器实例
         */
        public <T> ReceiveBuilder matchIf(Class<T> messageType, Predicate<T> predicate, Function<T, Void> handler) {
            Receive newReceive = Receive.matchIf(messageType, predicate, handler);
            this.receive = this.receive.orElse(newReceive);
            return this;
        }
        
        /**
         * 添加任意消息匹配规则
         *
         * @param handler 处理函数
         * @return 构建器实例
         */
        public ReceiveBuilder matchAny(Function<Object, Void> handler) {
            Receive newReceive = Receive.matchAny(handler);
            this.receive = this.receive.orElse(newReceive);
            return this;
        }
        
        /**
         * 构建接收器
         *
         * @return Receive实例
         */
        public Receive build() {
            return receive;
        }
    }
}