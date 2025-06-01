/*
 * 文件名: Message.java
 * 用途: 消息基类
 * 实现内容:
 *   - 网络消息的基础抽象和通用属性
 *   - 消息ID和类型管理
 *   - 消息时间戳和路由信息
 *   - 消息来源和目标信息
 *   - 扩展字段支持
 *   - 消息序列化支持
 * 技术选型:
 *   - 抽象基类设计，支持消息继承
 *   - 线程安全的属性存储
 *   - 高性能序列化接口
 *   - 灵活的扩展机制
 * 依赖关系:
 *   - 被具体消息类继承
 *   - 被MessageDispatcher处理
 *   - 被协议编解码器使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.message;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息基类
 * <p>
 * 定义网络消息的基础结构和通用属性，提供消息标识、
 * 路由信息、时间戳等基础功能。支持扩展字段和自定义属性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /** 请求消息 */
        REQUEST("请求"),
        
        /** 响应消息 */
        RESPONSE("响应"),
        
        /** 通知消息 */
        NOTIFICATION("通知"),
        
        /** 心跳消息 */
        HEARTBEAT("心跳"),
        
        /** 系统消息 */
        SYSTEM("系统"),
        
        /** 业务消息 */
        BUSINESS("业务");

        private final String description;

        MessageType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 消息优先级枚举
     */
    public enum MessagePriority {
        /** 低优先级 */
        LOW(1, "低优先级"),
        
        /** 普通优先级 */
        NORMAL(5, "普通优先级"),
        
        /** 高优先级 */
        HIGH(8, "高优先级"),
        
        /** 紧急优先级 */
        URGENT(10, "紧急优先级");

        private final int level;
        private final String description;

        MessagePriority(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 消息唯一标识
     */
    private final String messageId;

    /**
     * 消息类型
     */
    private final MessageType messageType;

    /**
     * 消息优先级
     */
    private MessagePriority priority = MessagePriority.NORMAL;

    /**
     * 消息创建时间
     */
    private final LocalDateTime createTime;

    /**
     * 消息序列号
     */
    private long sequence;

    /**
     * 关联的请求ID（用于响应消息）
     */
    private String requestId;

    /**
     * 消息来源ID（连接ID、用户ID等）
     */
    private String sourceId;

    /**
     * 消息目标ID（连接ID、用户ID等）
     */
    private String targetId;

    /**
     * 路由键（用于消息路由）
     */
    private String routingKey;

    /**
     * 消息版本号
     */
    private String version = "1.0";

    /**
     * 扩展属性
     */
    private final Map<String, Object> properties = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param messageType 消息类型
     */
    protected Message(MessageType messageType) {
        this.messageId = generateMessageId();
        this.messageType = messageType;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 构造函数（带消息ID）
     *
     * @param messageId   消息ID
     * @param messageType 消息类型
     */
    protected Message(String messageId, MessageType messageType) {
        this.messageId = messageId != null ? messageId : generateMessageId();
        this.messageType = messageType;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 获取消息ID
     *
     * @return 消息唯一标识
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 获取消息类型
     *
     * @return 消息类型
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * 获取消息优先级
     *
     * @return 消息优先级
     */
    public MessagePriority getPriority() {
        return priority;
    }

    /**
     * 设置消息优先级
     *
     * @param priority 消息优先级
     * @return 当前消息实例（支持链式调用）
     */
    public Message setPriority(MessagePriority priority) {
        this.priority = priority != null ? priority : MessagePriority.NORMAL;
        return this;
    }

    /**
     * 获取消息创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取消息序列号
     *
     * @return 序列号
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * 设置消息序列号
     *
     * @param sequence 序列号
     * @return 当前消息实例（支持链式调用）
     */
    public Message setSequence(long sequence) {
        this.sequence = sequence;
        return this;
    }

    /**
     * 获取关联的请求ID
     *
     * @return 请求ID
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 设置关联的请求ID
     *
     * @param requestId 请求ID
     * @return 当前消息实例（支持链式调用）
     */
    public Message setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * 获取消息来源ID
     *
     * @return 来源ID
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * 设置消息来源ID
     *
     * @param sourceId 来源ID
     * @return 当前消息实例（支持链式调用）
     */
    public Message setSourceId(String sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    /**
     * 获取消息目标ID
     *
     * @return 目标ID
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * 设置消息目标ID
     *
     * @param targetId 目标ID
     * @return 当前消息实例（支持链式调用）
     */
    public Message setTargetId(String targetId) {
        this.targetId = targetId;
        return this;
    }

    /**
     * 获取路由键
     *
     * @return 路由键
     */
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * 设置路由键
     *
     * @param routingKey 路由键
     * @return 当前消息实例（支持链式调用）
     */
    public Message setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
        return this;
    }

    /**
     * 获取消息版本号
     *
     * @return 版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置消息版本号
     *
     * @param version 版本号
     * @return 当前消息实例（支持链式调用）
     */
    public Message setVersion(String version) {
        this.version = version != null ? version : "1.0";
        return this;
    }

    /**
     * 设置扩展属性
     *
     * @param key   属性键
     * @param value 属性值
     * @return 当前消息实例（支持链式调用）
     */
    public Message setProperty(String key, Object value) {
        if (key != null) {
            if (value != null) {
                properties.put(key, value);
            } else {
                properties.remove(key);
            }
        }
        return this;
    }

    /**
     * 获取扩展属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回null
     */
    public Object getProperty(String key) {
        return key != null ? properties.get(key) : null;
    }

    /**
     * 获取扩展属性（带默认值）
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @param <T>          属性值类型
     * @return 属性值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * 移除扩展属性
     *
     * @param key 属性键
     * @return 被移除的属性值
     */
    public Object removeProperty(String key) {
        return key != null ? properties.remove(key) : null;
    }

    /**
     * 获取所有扩展属性
     *
     * @return 属性映射的只读视图
     */
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * 清空所有扩展属性
     *
     * @return 当前消息实例（支持链式调用）
     */
    public Message clearProperties() {
        properties.clear();
        return this;
    }

    /**
     * 检查是否有扩展属性
     *
     * @param key 属性键
     * @return true表示存在该属性
     */
    public boolean hasProperty(String key) {
        return key != null && properties.containsKey(key);
    }

    /**
     * 复制消息的基础属性到新消息
     *
     * @param target 目标消息
     */
    protected void copyBasicPropertiesTo(Message target) {
        if (target != null) {
            target.setPriority(this.priority)
                  .setSequence(this.sequence)
                  .setRequestId(this.requestId)
                  .setSourceId(this.sourceId)
                  .setTargetId(this.targetId)
                  .setRoutingKey(this.routingKey)
                  .setVersion(this.version);
            
            // 复制扩展属性
            target.properties.putAll(this.properties);
        }
    }

    /**
     * 创建响应消息
     *
     * @param responseClass 响应消息类
     * @param <T>           响应消息类型
     * @return 响应消息实例
     */
    public <T extends Message> T createResponse(Class<T> responseClass) {
        try {
            T response = responseClass.getDeclaredConstructor().newInstance();
            response.setRequestId(this.messageId)
                   .setSourceId(this.targetId)
                   .setTargetId(this.sourceId)
                   .setPriority(this.priority)
                   .setVersion(this.version);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("创建响应消息失败", e);
        }
    }

    /**
     * 获取消息摘要信息（用于日志）
     *
     * @return 消息摘要
     */
    public String getDigest() {
        return String.format("%s[id=%s, type=%s, source=%s, target=%s]", 
                getClass().getSimpleName(), messageId, messageType, sourceId, targetId);
    }

    /**
     * 生成消息ID
     */
    private String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String toString() {
        return String.format("Message{id='%s', type=%s, priority=%s, sequence=%d, source='%s', target='%s', createTime=%s}", 
                messageId, messageType, priority, sequence, sourceId, targetId, createTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Message message = (Message) obj;
        return messageId.equals(message.messageId);
    }

    @Override
    public int hashCode() {
        return messageId.hashCode();
    }
}