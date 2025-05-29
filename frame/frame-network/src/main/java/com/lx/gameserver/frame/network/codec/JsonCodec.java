/*
 * 文件名: JsonCodec.java
 * 用途: JSON协议实现
 * 实现内容:
 *   - 基于JSON的消息编解码
 *   - 高性能JSON库集成（Jackson）
 *   - 消息类型映射和动态解析
 *   - 压缩支持和格式验证
 *   - 调试友好的可读格式
 *   - 灵活的类型转换和错误处理
 * 技术选型:
 *   - Jackson JSON处理库
 *   - 高性能序列化和反序列化
 *   - 类型安全的消息映射
 *   - UTF-8编码支持
 * 依赖关系:
 *   - 继承MessageCodec基类
 *   - 使用Jackson ObjectMapper
 *   - 与JsonUtils工具类协作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lx.gameserver.common.JsonUtils;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON协议实现
 * <p>
 * 基于JSON格式的消息编解码器，提供高性能的JSON序列化和反序列化。
 * 支持消息类型映射、压缩和调试友好的可读格式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class JsonCodec extends MessageCodec {

    private static final Logger logger = LoggerFactory.getLogger(JsonCodec.class);

    /**
     * JSON对象映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 消息类型到ID的映射
     */
    private final Map<Class<?>, Integer> messageTypeToId = new ConcurrentHashMap<>();

    /**
     * 消息ID到类型的映射
     */
    private final Map<Integer, Class<?>> messageIdToType = new ConcurrentHashMap<>();

    /**
     * 是否使用紧凑格式（不包含null值）
     */
    private final boolean compactFormat;

    /**
     * 是否美化输出（用于调试）
     */
    private final boolean prettyPrint;

    /**
     * 构造函数
     */
    public JsonCodec() {
        this(1024 * 1024, true, 1024, true, true, false);
    }

    /**
     * 构造函数
     *
     * @param maxMessageSize       最大消息大小
     * @param compressionEnabled   是否启用压缩
     * @param compressionThreshold 压缩阈值
     * @param checksumEnabled      是否启用校验
     * @param compactFormat        是否使用紧凑格式
     * @param prettyPrint          是否美化输出
     */
    public JsonCodec(int maxMessageSize, boolean compressionEnabled, 
                    int compressionThreshold, boolean checksumEnabled,
                    boolean compactFormat, boolean prettyPrint) {
        super(maxMessageSize, compressionEnabled, compressionThreshold, checksumEnabled);
        this.compactFormat = compactFormat;
        this.prettyPrint = prettyPrint;
        this.objectMapper = createObjectMapper();
        
        logger.info("初始化JSON编解码器，最大消息大小: {}, 压缩: {}, 紧凑格式: {}, 美化输出: {}", 
                   maxMessageSize, compressionEnabled, compactFormat, prettyPrint);
    }

    /**
     * 注册JSON消息类型
     *
     * @param messageId    消息ID
     * @param messageClass 消息类型
     */
    public void registerMessage(int messageId, Class<?> messageClass) {
        if (messageClass == null) {
            throw new IllegalArgumentException("消息类型不能为null");
        }

        // 检查ID冲突
        Class<?> existingClass = messageIdToType.put(messageId, messageClass);
        if (existingClass != null && !existingClass.equals(messageClass)) {
            logger.warn("消息ID冲突: {} -> {} (覆盖: {})", 
                       messageId, messageClass.getSimpleName(), existingClass.getSimpleName());
        }

        // 检查类型冲突
        Integer existingId = messageTypeToId.put(messageClass, messageId);
        if (existingId != null && !existingId.equals(messageId)) {
            logger.warn("消息类型重复注册: {} -> {} (覆盖: {})", 
                       messageClass.getSimpleName(), messageId, existingId);
        }

        logger.debug("注册JSON消息: {} -> {}", messageId, messageClass.getSimpleName());
    }

    /**
     * 注销消息类型
     *
     * @param messageId 消息ID
     */
    public void unregisterMessage(int messageId) {
        Class<?> messageClass = messageIdToType.remove(messageId);
        if (messageClass != null) {
            messageTypeToId.remove(messageClass);
            logger.debug("注销JSON消息: {} -> {}", messageId, messageClass.getSimpleName());
        }
    }

    @Override
    public boolean supports(Class<?> messageClass) {
        // JSON编解码器支持所有可序列化的对象
        return messageClass != null && messageTypeToId.containsKey(messageClass);
    }

    @Override
    protected void encodeMessageBody(Object message, ByteBuf out) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("消息不能为null");
        }

        // 获取消息ID
        Integer messageId = messageTypeToId.get(message.getClass());
        if (messageId == null) {
            throw new IllegalArgumentException("未注册的JSON消息类型: " + message.getClass());
        }

        // 序列化为JSON
        String jsonString;
        try {
            if (prettyPrint) {
                jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(message);
            } else {
                jsonString = objectMapper.writeValueAsString(message);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON序列化失败", e);
        }

        // 转换为字节数组
        byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        
        // 写入消息ID（4字节）
        out.writeInt(messageId);
        
        // 写入JSON长度（4字节）
        out.writeInt(jsonBytes.length);
        
        // 写入JSON数据
        out.writeBytes(jsonBytes);

        logger.debug("编码JSON消息: {} (ID: {}, 大小: {})", 
                    message.getClass().getSimpleName(), messageId, jsonBytes.length);
    }

    @Override
    protected Object decodeMessageBody(MessageHeader header, ByteBuf in) throws Exception {
        if (in.readableBytes() < 8) { // 至少需要8字节（消息ID + 长度）
            throw new IllegalStateException("消息体数据不足");
        }

        // 读取消息ID
        int messageId = in.readInt();
        
        // 读取JSON长度
        int jsonLength = in.readInt();
        
        if (jsonLength < 0 || jsonLength > in.readableBytes()) {
            throw new IllegalStateException("无效的JSON长度: " + jsonLength);
        }

        // 获取消息类型
        Class<?> messageClass = messageIdToType.get(messageId);
        if (messageClass == null) {
            throw new IllegalArgumentException("未知的消息ID: " + messageId);
        }

        // 读取JSON数据
        byte[] jsonBytes = new byte[jsonLength];
        in.readBytes(jsonBytes);
        String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);

        // 反序列化JSON
        try {
            Object message = objectMapper.readValue(jsonString, messageClass);
            logger.debug("解码JSON消息: {} (ID: {}, 大小: {})", 
                        messageClass.getSimpleName(), messageId, jsonLength);
            return message;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON反序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected int estimateMessageBodySize(Object message) {
        if (message == null) {
            return 64; // 默认预估大小
        }

        try {
            // 简单预估：消息头(8字节) + JSON字符串长度的UTF-8字节数
            String jsonString = objectMapper.writeValueAsString(message);
            return 8 + jsonString.getBytes(StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException e) {
            logger.warn("预估JSON消息大小失败: {}", message.getClass().getSimpleName(), e);
            return 256; // 保守预估
        }
    }

    @Override
    protected int getMessageType(Object message) {
        Integer messageId = messageTypeToId.get(message.getClass());
        return messageId != null ? messageId : 0;
    }

    /**
     * 创建ObjectMapper
     */
    private ObjectMapper createObjectMapper() {
        if (compactFormat) {
            return JsonUtils.getCompactMapper();
        } else if (prettyPrint) {
            return JsonUtils.getPrettyMapper();
        } else {
            return JsonUtils.getObjectMapper();
        }
    }

    /**
     * 获取已注册的消息类型数量
     *
     * @return 消息类型数量
     */
    public int getRegisteredMessageCount() {
        return messageTypeToId.size();
    }

    /**
     * 检查消息ID是否已注册
     *
     * @param messageId 消息ID
     * @return true表示已注册
     */
    public boolean isMessageRegistered(int messageId) {
        return messageIdToType.containsKey(messageId);
    }

    /**
     * 检查消息类型是否已注册
     *
     * @param messageClass 消息类型
     * @return true表示已注册
     */
    public boolean isMessageRegistered(Class<?> messageClass) {
        return messageTypeToId.containsKey(messageClass);
    }

    /**
     * 获取消息类型对应的ID
     *
     * @param messageClass 消息类型
     * @return 消息ID，如果未注册则返回null
     */
    public Integer getMessageId(Class<?> messageClass) {
        return messageTypeToId.get(messageClass);
    }

    /**
     * 获取消息ID对应的类型
     *
     * @param messageId 消息ID
     * @return 消息类型，如果未注册则返回null
     */
    public Class<?> getMessageClass(int messageId) {
        return messageIdToType.get(messageId);
    }

    /**
     * 清空所有注册的消息类型
     */
    public void clearRegistrations() {
        messageTypeToId.clear();
        messageIdToType.clear();
        logger.info("清空所有JSON消息注册");
    }

    /**
     * 获取所有已注册的消息ID
     *
     * @return 消息ID集合
     */
    public java.util.Set<Integer> getRegisteredMessageIds() {
        return java.util.Collections.unmodifiableSet(messageIdToType.keySet());
    }

    /**
     * 获取所有已注册的消息类型
     *
     * @return 消息类型集合
     */
    public java.util.Set<Class<?>> getRegisteredMessageClasses() {
        return java.util.Collections.unmodifiableSet(messageTypeToId.keySet());
    }

    /**
     * 转换消息为JSON字符串（用于调试）
     *
     * @param message 消息对象
     * @return JSON字符串
     */
    public String toJsonString(Object message) {
        try {
            if (prettyPrint) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(message);
            } else {
                return objectMapper.writeValueAsString(message);
            }
        } catch (JsonProcessingException e) {
            logger.warn("转换消息为JSON字符串失败", e);
            return "{}";
        }
    }

    /**
     * 从JSON字符串解析消息（用于调试）
     *
     * @param jsonString   JSON字符串
     * @param messageClass 目标消息类型
     * @param <T>          消息类型参数
     * @return 解析后的消息对象
     */
    public <T> T fromJsonString(String jsonString, Class<T> messageClass) {
        try {
            return objectMapper.readValue(jsonString, messageClass);
        } catch (JsonProcessingException e) {
            logger.warn("从JSON字符串解析消息失败", e);
            throw new RuntimeException("JSON解析失败", e);
        }
    }

    @Override
    public String toString() {
        return String.format("JsonCodec{registeredMessages=%d, maxSize=%d, compression=%s, compact=%s, pretty=%s}", 
                           getRegisteredMessageCount(), maxMessageSize, compressionEnabled, 
                           compactFormat, prettyPrint);
    }
}