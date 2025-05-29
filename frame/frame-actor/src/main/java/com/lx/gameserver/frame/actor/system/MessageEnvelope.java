/*
 * 文件名: MessageEnvelope.java
 * 用途: 消息信封类
 * 实现内容:
 *   - 包装消息和发送者信息
 *   - 支持消息优先级排序
 *   - 提供消息追踪和统计信息
 * 技术选型:
 *   - 不可变对象设计保证线程安全
 *   - 实现Comparable接口支持优先级队列
 *   - 消息封装和路由支持
 * 依赖关系:
 *   - 在ActorMailbox中使用
 *   - 包装Message和ActorRef
 *   - 支持消息调度和路由
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Message;

/**
 * 消息信封类
 * <p>
 * 包装消息对象和相关元数据，用于在Actor系统中传递消息。
 * 支持消息优先级排序和追踪功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class MessageEnvelope implements Comparable<MessageEnvelope> {
    
    /** 消息内容 */
    private final Object message;
    
    /** 发送者引用 */
    private final ActorRef sender;
    
    /** 接收者引用 */
    private final ActorRef receiver;
    
    /** 消息优先级 */
    private final int priority;
    
    /** 创建时间戳 */
    private final long timestamp;
    
    /** 消息ID（如果消息实现了Message接口） */
    private final String messageId;
    
    /**
     * 构造函数
     *
     * @param message  消息内容
     * @param sender   发送者引用
     * @param receiver 接收者引用
     */
    public MessageEnvelope(Object message, ActorRef sender, ActorRef receiver) {
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.timestamp = System.currentTimeMillis();
        
        // 如果消息实现了Message接口，获取其优先级和ID
        if (message instanceof Message msg) {
            this.priority = msg.getPriority();
            this.messageId = msg.getMessageId();
            // 设置发送者信息
            msg.setSender(sender);
        } else {
            this.priority = Message.Priority.NORMAL;
            this.messageId = null;
        }
    }
    
    /**
     * 构造函数（指定优先级）
     *
     * @param message  消息内容
     * @param sender   发送者引用
     * @param receiver 接收者引用
     * @param priority 消息优先级
     */
    public MessageEnvelope(Object message, ActorRef sender, ActorRef receiver, int priority) {
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.priority = priority;
        this.timestamp = System.currentTimeMillis();
        this.messageId = message instanceof Message msg ? msg.getMessageId() : null;
        
        // 设置发送者信息
        if (message instanceof Message msg) {
            msg.setSender(sender);
        }
    }
    
    /**
     * 获取消息内容
     *
     * @return 消息内容
     */
    public Object getMessage() {
        return message;
    }
    
    /**
     * 获取发送者引用
     *
     * @return 发送者引用
     */
    public ActorRef getSender() {
        return sender;
    }
    
    /**
     * 获取接收者引用
     *
     * @return 接收者引用
     */
    public ActorRef getReceiver() {
        return receiver;
    }
    
    /**
     * 获取消息优先级
     *
     * @return 消息优先级
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * 获取创建时间戳
     *
     * @return 创建时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取消息ID
     *
     * @return 消息ID，如果消息未实现Message接口则返回null
     */
    public String getMessageId() {
        return messageId;
    }
    
    /**
     * 获取消息类型名称
     *
     * @return 消息类型名称
     */
    public String getMessageType() {
        return message.getClass().getSimpleName();
    }
    
    /**
     * 计算消息在队列中的等待时间
     *
     * @return 等待时间（毫秒）
     */
    public long getWaitingTime() {
        return System.currentTimeMillis() - timestamp;
    }
    
    @Override
    public int compareTo(MessageEnvelope other) {
        if (other == null) {
            return -1;
        }
        
        // 首先按优先级排序（数值小的优先级高）
        int priorityCompare = Integer.compare(this.priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        
        // 优先级相同时按时间戳排序（早的优先）
        return Long.compare(this.timestamp, other.timestamp);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MessageEnvelope that = (MessageEnvelope) obj;
        
        if (messageId != null && that.messageId != null) {
            return messageId.equals(that.messageId);
        }
        
        return message.equals(that.message) && 
               timestamp == that.timestamp &&
               sender.equals(that.sender) &&
               receiver.equals(that.receiver);
    }
    
    @Override
    public int hashCode() {
        if (messageId != null) {
            return messageId.hashCode();
        }
        
        int result = message.hashCode();
        result = 31 * result + sender.hashCode();
        result = 31 * result + receiver.hashCode();
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("MessageEnvelope{message=%s, sender=%s, receiver=%s, priority=%d, timestamp=%d, waitingTime=%d}", 
                getMessageType(), sender, receiver, priority, timestamp, getWaitingTime());
    }
}