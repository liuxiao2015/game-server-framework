/*
 * 文件名: Message.java
 * 用途: Actor消息基类
 * 实现内容:
 *   - 定义消息的基本属性和行为
 *   - 消息ID用于追踪
 *   - 发送者引用支持
 *   - 消息优先级和路由信息
 *   - 消息创建时间戳
 * 技术选型:
 *   - 接口设计模式提供扩展性
 *   - 支持消息优先级排序
 *   - 消息追踪和路由支持
 * 依赖关系:
 *   - 被所有Actor消息类型实现
 *   - 与ActorRef协作进行消息传递
 *   - 支持消息路由和优先级队列
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import java.io.Serializable;

/**
 * Actor消息基接口
 * <p>
 * 定义了Actor系统中所有消息的基本属性和行为。
 * 支持消息追踪、优先级控制、路由信息等特性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface Message extends Serializable, Comparable<Message> {
    
    /**
     * 获取消息ID
     *
     * @return 消息ID
     */
    String getMessageId();
    
    /**
     * 获取发送者引用
     *
     * @return 发送者ActorRef，如果没有发送者则返回null
     */
    ActorRef getSender();
    
    /**
     * 设置发送者引用
     *
     * @param sender 发送者ActorRef
     */
    void setSender(ActorRef sender);
    
    /**
     * 获取消息优先级
     *
     * @return 优先级值，数值越小优先级越高
     */
    int getPriority();
    
    /**
     * 获取消息创建时间戳
     *
     * @return 创建时间戳（毫秒）
     */
    long getTimestamp();
    
    /**
     * 获取消息路由信息
     *
     * @return 路由键，用于消息路由决策
     */
    String getRouteKey();
    
    /**
     * 设置消息路由信息
     *
     * @param routeKey 路由键
     */
    void setRouteKey(String routeKey);
    
    /**
     * 获取消息路由信息（用于路由决策）
     *
     * @return 路由信息映射，包含路由决策所需的各种信息
     */
    default java.util.Map<String, String> getRoutingInfo() {
        java.util.Map<String, String> routingInfo = new java.util.HashMap<>();
        if (getRouteKey() != null) {
            routingInfo.put("routeKey", getRouteKey());
        }
        routingInfo.put("messageType", getMessageType());
        routingInfo.put("priority", String.valueOf(getPriority()));
        return routingInfo;
    }
    
    /**
     * 检查消息是否有效
     *
     * @return 如果消息有效返回true
     */
    default boolean isValid() {
        return getMessageId() != null && !getMessageId().isEmpty();
    }
    
    /**
     * 获取消息类型
     *
     * @return 消息类型字符串
     */
    default String getMessageType() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 默认比较实现，优先根据优先级，然后根据时间戳
     */
    @Override
    default int compareTo(Message other) {
        if (other == null) {
            return -1;
        }
        
        // 首先按优先级排序（数值小的优先级高）
        int priorityCompare = Integer.compare(this.getPriority(), other.getPriority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        
        // 优先级相同时按时间戳排序（早的优先）
        return Long.compare(this.getTimestamp(), other.getTimestamp());
    }
    
    /**
     * 消息优先级常量
     */
    interface Priority {
        /** 系统级消息，最高优先级 */
        int SYSTEM = 0;
        /** 高优先级消息 */
        int HIGH = 100;
        /** 普通优先级消息（默认） */
        int NORMAL = 500;
        /** 低优先级消息 */
        int LOW = 1000;
    }
}