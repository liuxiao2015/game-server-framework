/*
 * 文件名: MessageBuilder.java
 * 用途: 消息构建器
 * 实现内容:
 *   - 链式API构建复杂消息
 *   - 消息验证和序列化优化
 *   - 消息模板和批量构建
 *   - 消息元数据和路由信息管理
 * 技术选型:
 *   - 建造者模式提供流畅API
 *   - 泛型支持类型安全
 *   - 验证机制保证消息完整性
 * 依赖关系:
 *   - 与Message接口集成
 *   - 支持各种消息类型构建
 *   - 被Actor系统广泛使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.util;

import com.lx.gameserver.frame.actor.core.Message;
import com.lx.gameserver.frame.actor.core.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 消息构建器
 * <p>
 * 提供流畅的API来构建各种类型的消息，支持验证、
 * 序列化优化、模板等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class MessageBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageBuilder.class);
    
    /** 消息ID生成器 */
    private static final AtomicLong messageIdGenerator = new AtomicLong(0);
    
    /** 默认消息超时时间 */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /** 消息模板缓存 */
    private static final Map<String, MessageTemplate> templates = new HashMap<>();
    
    /**
     * 创建新的消息构建器
     *
     * @param <T> 消息负载类型
     * @return 消息构建器
     */
    public static <T> Builder<T> create() {
        return new Builder<>();
    }
    
    /**
     * 创建指定类型的消息构建器
     *
     * @param payloadClass 负载类型
     * @param <T> 消息负载类型
     * @return 消息构建器
     */
    public static <T> Builder<T> create(Class<T> payloadClass) {
        return new Builder<T>().payloadType(payloadClass);
    }
    
    /**
     * 从模板创建消息构建器
     *
     * @param templateName 模板名称
     * @param <T> 消息负载类型
     * @return 消息构建器
     */
    @SuppressWarnings("unchecked")
    public static <T> Builder<T> fromTemplate(String templateName) {
        MessageTemplate template = templates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("未找到消息模板: " + templateName);
        }
        
        Builder<T> builder = new Builder<>();
        template.apply(builder);
        return builder;
    }
    
    /**
     * 注册消息模板
     *
     * @param name 模板名称
     * @param template 模板
     */
    public static void registerTemplate(String name, MessageTemplate template) {
        templates.put(name, template);
        logger.debug("注册消息模板: {}", name);
    }
    
    /**
     * 批量构建消息
     *
     * @param count 消息数量
     * @param builderConfig 构建器配置
     * @param <T> 消息负载类型
     * @return 消息列表
     */
    public static <T> List<BuiltMessage<T>> buildBatch(int count, Consumer<Builder<T>> builderConfig) {
        List<BuiltMessage<T>> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Builder<T> builder = create();
            builderConfig.accept(builder);
            messages.add(builder.build());
        }
        return messages;
    }
    
    /**
     * 消息构建器实现
     */
    public static class Builder<T> {
        private String messageId;
        private T payload;
        private Class<T> payloadType;
        private ActorRef sender;
        private ActorRef replyTo;
        private String correlationId;
        private int priority = 0;
        private Duration timeout = DEFAULT_TIMEOUT;
        private Instant timestamp;
        private final Map<String, Object> headers = new HashMap<>();
        private final Map<String, String> tags = new HashMap<>();
        private String messageType;
        private boolean persistent = false;
        private String routingKey;
        private final List<Predicate<BuiltMessage<T>>> validators = new ArrayList<>();
        
        /**
         * 设置消息ID
         */
        public Builder<T> messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }
        
        /**
         * 设置消息负载
         */
        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }
        
        /**
         * 设置负载类型
         */
        public Builder<T> payloadType(Class<T> payloadType) {
            this.payloadType = payloadType;
            return this;
        }
        
        /**
         * 设置发送者
         */
        public Builder<T> sender(ActorRef sender) {
            this.sender = sender;
            return this;
        }
        
        /**
         * 设置回复目标
         */
        public Builder<T> replyTo(ActorRef replyTo) {
            this.replyTo = replyTo;
            return this;
        }
        
        /**
         * 设置关联ID
         */
        public Builder<T> correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        /**
         * 设置优先级
         */
        public Builder<T> priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * 设置超时时间
         */
        public Builder<T> timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        /**
         * 设置时间戳
         */
        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        /**
         * 添加消息头
         */
        public Builder<T> header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }
        
        /**
         * 批量添加消息头
         */
        public Builder<T> headers(Map<String, Object> headers) {
            this.headers.putAll(headers);
            return this;
        }
        
        /**
         * 添加标签
         */
        public Builder<T> tag(String key, String value) {
            this.tags.put(key, value);
            return this;
        }
        
        /**
         * 批量添加标签
         */
        public Builder<T> tags(Map<String, String> tags) {
            this.tags.putAll(tags);
            return this;
        }
        
        /**
         * 设置消息类型
         */
        public Builder<T> messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }
        
        /**
         * 设置是否持久化
         */
        public Builder<T> persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }
        
        /**
         * 设置路由键
         */
        public Builder<T> routingKey(String routingKey) {
            this.routingKey = routingKey;
            return this;
        }
        
        /**
         * 添加验证器
         */
        public Builder<T> validator(Predicate<BuiltMessage<T>> validator) {
            this.validators.add(validator);
            return this;
        }
        
        /**
         * 设置为请求消息
         */
        public Builder<T> asRequest() {
            return tag("messageCategory", "REQUEST");
        }
        
        /**
         * 设置为响应消息
         */
        public Builder<T> asResponse() {
            return tag("messageCategory", "RESPONSE");
        }
        
        /**
         * 设置为事件消息
         */
        public Builder<T> asEvent() {
            return tag("messageCategory", "EVENT");
        }
        
        /**
         * 设置为命令消息
         */
        public Builder<T> asCommand() {
            return tag("messageCategory", "COMMAND");
        }
        
        /**
         * 设置为系统消息
         */
        public Builder<T> asSystem() {
            return tag("messageCategory", "SYSTEM");
        }
        
        /**
         * 标记为重要消息
         */
        public Builder<T> important() {
            return priority(10).tag("importance", "HIGH");
        }
        
        /**
         * 标记为紧急消息
         */
        public Builder<T> urgent() {
            return priority(20).tag("urgency", "HIGH");
        }
        
        /**
         * 设置短超时
         */
        public Builder<T> shortTimeout() {
            return timeout(Duration.ofSeconds(5));
        }
        
        /**
         * 设置长超时
         */
        public Builder<T> longTimeout() {
            return timeout(Duration.ofMinutes(5));
        }
        
        /**
         * 构建消息
         */
        public BuiltMessage<T> build() {
            // 生成默认值
            if (messageId == null) {
                messageId = generateMessageId();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (messageType == null && payload != null) {
                messageType = payload.getClass().getSimpleName();
            }
            
            // 创建消息
            BuiltMessage<T> message = new BuiltMessage<>(
                    messageId, payload, payloadType, sender, replyTo,
                    correlationId, priority, timeout, timestamp,
                    new HashMap<>(headers), new HashMap<>(tags),
                    messageType, persistent, routingKey
            );
            
            // 执行验证
            for (Predicate<BuiltMessage<T>> validator : validators) {
                if (!validator.test(message)) {
                    throw new IllegalStateException("消息验证失败");
                }
            }
            
            return message;
        }
        
        /**
         * 构建并发送
         */
        public void buildAndSend(ActorRef target) {
            BuiltMessage<T> message = build();
            target.tell(message, sender);
        }
        
        /**
         * 构建并异步发送
         */
        public void buildAndSendAsync(ActorRef target, Consumer<Throwable> errorHandler) {
            try {
                buildAndSend(target);
            } catch (Exception e) {
                if (errorHandler != null) {
                    errorHandler.accept(e);
                } else {
                    logger.error("消息发送失败", e);
                }
            }
        }
        
        private String generateMessageId() {
            return "msg-" + System.currentTimeMillis() + "-" + messageIdGenerator.incrementAndGet();
        }
    }
    
    /**
     * 构建完成的消息
     */
    public static class BuiltMessage<T> implements Message {
        private final String messageId;
        private final T payload;
        private final Class<T> payloadType;
        private final ActorRef sender;
        private final ActorRef replyTo;
        private final String correlationId;
        private final int priority;
        private final Duration timeout;
        private final Instant timestamp;
        private final Map<String, Object> headers;
        private final Map<String, String> tags;
        private final String messageType;
        private final boolean persistent;
        private final String routingKey;
        
        public BuiltMessage(String messageId, T payload, Class<T> payloadType,
                          ActorRef sender, ActorRef replyTo, String correlationId,
                          int priority, Duration timeout, Instant timestamp,
                          Map<String, Object> headers, Map<String, String> tags,
                          String messageType, boolean persistent, String routingKey) {
            this.messageId = messageId;
            this.payload = payload;
            this.payloadType = payloadType;
            this.sender = sender;
            this.replyTo = replyTo;
            this.correlationId = correlationId;
            this.priority = priority;
            this.timeout = timeout;
            this.timestamp = timestamp;
            this.headers = headers;
            this.tags = tags;
            this.messageType = messageType;
            this.persistent = persistent;
            this.routingKey = routingKey;
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
        public int getPriority() {
            return priority;
        }
        
        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
        
        @Override
        public Map<String, String> getRoutingInfo() {
            Map<String, String> routing = new HashMap<>();
            if (routingKey != null) {
                routing.put("routingKey", routingKey);
            }
            routing.putAll(tags);
            return routing;
        }
        
        @Override
        public int compareTo(Message other) {
            int priorityCompare = Integer.compare(other.getPriority(), this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return this.timestamp.compareTo(other.getTimestamp());
        }
        
        public T getPayload() {
            return payload;
        }
        
        public Class<T> getPayloadType() {
            return payloadType;
        }
        
        public ActorRef getReplyTo() {
            return replyTo;
        }
        
        public String getCorrelationId() {
            return correlationId;
        }
        
        public Duration getTimeout() {
            return timeout;
        }
        
        public Map<String, Object> getHeaders() {
            return headers;
        }
        
        public Map<String, String> getTags() {
            return tags;
        }
        
        @Override
        public String getMessageType() {
            return messageType;
        }
        
        public boolean isPersistent() {
            return persistent;
        }
        
        public String getRoutingKey() {
            return routingKey;
        }
        
        public Object getHeader(String key) {
            return headers.get(key);
        }
        
        public String getTag(String key) {
            return tags.get(key);
        }
        
        public boolean hasHeader(String key) {
            return headers.containsKey(key);
        }
        
        public boolean hasTag(String key) {
            return tags.containsKey(key);
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(timestamp.plus(timeout));
        }
        
        public Duration getAge() {
            return Duration.between(timestamp, Instant.now());
        }
        
        @Override
        public boolean isValid() {
            return messageId != null && !messageId.isEmpty() && 
                   timestamp != null && !isExpired();
        }
        
        @Override
        public String toString() {
            return String.format("BuiltMessage{id=%s, type=%s, priority=%d, payload=%s}",
                    messageId, messageType, priority, 
                    payload != null ? payload.getClass().getSimpleName() : "null");
        }
    }
    
    /**
     * 消息模板接口
     */
    @FunctionalInterface
    public interface MessageTemplate {
        /**
         * 应用模板到构建器
         *
         * @param builder 消息构建器
         */
        void apply(Builder<?> builder);
    }
    
    /**
     * 预定义的消息模板
     */
    public static class Templates {
        
        /**
         * 系统通知模板
         */
        public static MessageTemplate systemNotification() {
            return builder -> builder
                    .asSystem()
                    .priority(5)
                    .timeout(Duration.ofMinutes(1))
                    .tag("category", "notification");
        }
        
        /**
         * 用户命令模板
         */
        public static MessageTemplate userCommand() {
            return builder -> builder
                    .asCommand()
                    .priority(1)
                    .timeout(Duration.ofSeconds(30))
                    .tag("category", "user-command");
        }
        
        /**
         * 游戏事件模板
         */
        public static MessageTemplate gameEvent() {
            return builder -> builder
                    .asEvent()
                    .priority(3)
                    .timeout(Duration.ofSeconds(10))
                    .tag("category", "game-event");
        }
        
        /**
         * 数据同步模板
         */
        public static MessageTemplate dataSync() {
            return builder -> builder
                    .asSystem()
                    .priority(2)
                    .persistent(true)
                    .timeout(Duration.ofMinutes(2))
                    .tag("category", "data-sync");
        }
        
        /**
         * 紧急告警模板
         */
        public static MessageTemplate emergency() {
            return builder -> builder
                    .urgent()
                    .asSystem()
                    .persistent(true)
                    .shortTimeout()
                    .tag("category", "emergency");
        }
        
        /**
         * 批量操作模板
         */
        public static MessageTemplate batchOperation() {
            return builder -> builder
                    .asCommand()
                    .priority(0)
                    .longTimeout()
                    .tag("category", "batch");
        }
        
        /**
         * 心跳消息模板
         */
        public static MessageTemplate heartbeat() {
            return builder -> builder
                    .asSystem()
                    .priority(-1)
                    .timeout(Duration.ofSeconds(5))
                    .tag("category", "heartbeat");
        }
    }
    
    /**
     * 消息验证器
     */
    public static class Validators {
        
        /**
         * 验证负载不为空
         */
        public static <T> Predicate<BuiltMessage<T>> nonNullPayload() {
            return message -> message.getPayload() != null;
        }
        
        /**
         * 验证发送者不为空
         */
        public static <T> Predicate<BuiltMessage<T>> nonNullSender() {
            return message -> message.getSender() != null;
        }
        
        /**
         * 验证消息未过期
         */
        public static <T> Predicate<BuiltMessage<T>> notExpired() {
            return message -> !message.isExpired();
        }
        
        /**
         * 验证优先级范围
         */
        public static <T> Predicate<BuiltMessage<T>> priorityRange(int min, int max) {
            return message -> message.getPriority() >= min && message.getPriority() <= max;
        }
        
        /**
         * 验证包含指定标签
         */
        public static <T> Predicate<BuiltMessage<T>> hasTag(String key) {
            return message -> message.hasTag(key);
        }
        
        /**
         * 验证标签值匹配
         */
        public static <T> Predicate<BuiltMessage<T>> tagEquals(String key, String value) {
            return message -> value.equals(message.getTag(key));
        }
        
        /**
         * 验证负载类型
         */
        public static <T> Predicate<BuiltMessage<T>> payloadType(Class<?> expectedType) {
            return message -> message.getPayload() != null && 
                    expectedType.isInstance(message.getPayload());
        }
        
        /**
         * 验证可序列化
         */
        public static <T> Predicate<BuiltMessage<T>> serializable() {
            return message -> message.getPayload() instanceof Serializable;
        }
    }
    
    static {
        // 注册预定义模板
        registerTemplate("system-notification", Templates.systemNotification());
        registerTemplate("user-command", Templates.userCommand());
        registerTemplate("game-event", Templates.gameEvent());
        registerTemplate("data-sync", Templates.dataSync());
        registerTemplate("emergency", Templates.emergency());
        registerTemplate("batch-operation", Templates.batchOperation());
        registerTemplate("heartbeat", Templates.heartbeat());
    }
}