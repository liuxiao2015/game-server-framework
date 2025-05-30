/*
 * 文件名: AbstractMessage.java
 * 用途: 抽象消息基类实现
 * 实现内容:
 *   - 提供Message接口的默认实现
 *   - 消息ID生成和管理
 *   - 发送者引用和优先级支持
 *   - 路由信息和时间戳管理
 * 技术选型:
 *   - 抽象类提供通用实现
 *   - UUID生成唯一消息ID
 *   - 支持消息优先级排序
 * 依赖关系:
 *   - 实现Message接口
 *   - 被具体消息类型继承
 *   - 支持Actor系统消息传递
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import java.util.UUID;

/**
 * 抽象消息基类
 * <p>
 * 提供Message接口的默认实现，包含消息的基本属性
 * 和行为。具体的消息类型可以继承此类。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class AbstractMessage implements Message {
    
    private static final long serialVersionUID = 1L;
    
    /** 消息ID */
    private final String messageId;
    
    /** 发送者引用 */
    private ActorRef sender;
    
    /** 消息优先级 */
    private final int priority;
    
    /** 创建时间戳 */
    private final long timestamp;
    
    /** 路由键 */
    private String routeKey;
    
    /**
     * 构造函数（使用默认优先级）
     */
    protected AbstractMessage() {
        this(Priority.NORMAL);
    }
    
    /**
     * 构造函数
     *
     * @param priority 消息优先级
     */
    protected AbstractMessage(int priority) {
        this.messageId = generateMessageId();
        this.priority = priority;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 构造函数（完整参数）
     *
     * @param priority 消息优先级
     * @param routeKey 路由键
     */
    protected AbstractMessage(int priority, String routeKey) {
        this(priority);
        this.routeKey = routeKey;
    }
    
    /**
     * 生成消息ID
     *
     * @return 唯一消息ID
     */
    private String generateMessageId() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public String getMessageId() {
        return messageId;
    }
    
    @Override
    public ActorRef getSender() {
        return sender;
    }
    
    @Override
    public void setSender(ActorRef sender) {
        this.sender = sender;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getRouteKey() {
        return routeKey;
    }
    
    @Override
    public void setRouteKey(String routeKey) {
        this.routeKey = routeKey;
    }
    
    @Override
    public String toString() {
        return String.format("%s{id=%s, priority=%d, timestamp=%d, routeKey=%s}", 
                getMessageType(), messageId, priority, timestamp, routeKey);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        AbstractMessage that = (AbstractMessage) obj;
        return messageId.equals(that.messageId);
    }
    
    @Override
    public int hashCode() {
        return messageId.hashCode();
    }
}