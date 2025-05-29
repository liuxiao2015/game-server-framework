/*
 * 文件名: ProtobufCodec.java
 * 用途: Protobuf协议实现
 * 实现内容:
 *   - 基于Protocol Buffers的消息编解码
 *   - 消息注册机制和类型映射
 *   - 动态消息解析和序列化
 *   - 协议版本兼容性处理
 *   - 性能优化和内存管理
 *   - 错误处理和恢复机制
 * 技术选型:
 *   - Google Protocol Buffers
 *   - 高性能二进制序列化
 *   - 动态消息类型解析
 *   - 内存池化和对象复用
 * 依赖关系:
 *   - 继承MessageCodec基类
 *   - 使用Google Protobuf库
 *   - 与Message类协作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.codec;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protobuf协议实现
 * <p>
 * 基于Google Protocol Buffers实现的高性能消息编解码器。
 * 支持消息类型注册、动态解析和协议版本兼容。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ProtobufCodec extends MessageCodec {

    private static final Logger logger = LoggerFactory.getLogger(ProtobufCodec.class);

    /**
     * 消息类型到ID的映射
     */
    private final Map<Class<?>, Integer> messageTypeToId = new ConcurrentHashMap<>();

    /**
     * 消息ID到类型的映射
     */
    private final Map<Integer, Class<?>> messageIdToType = new ConcurrentHashMap<>();

    /**
     * 消息ID到解析器的映射
     */
    private final Map<Integer, Parser<? extends MessageLite>> messageParsers = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public ProtobufCodec() {
        this(1024 * 1024, true, 1024, true); // 1MB, 启用压缩, 1KB压缩阈值, 启用校验
    }

    /**
     * 构造函数
     *
     * @param maxMessageSize       最大消息大小
     * @param compressionEnabled   是否启用压缩
     * @param compressionThreshold 压缩阈值
     * @param checksumEnabled      是否启用校验
     */
    public ProtobufCodec(int maxMessageSize, boolean compressionEnabled, 
                        int compressionThreshold, boolean checksumEnabled) {
        super(maxMessageSize, compressionEnabled, compressionThreshold, checksumEnabled);
        logger.info("初始化Protobuf编解码器，最大消息大小: {}, 压缩: {}, 校验: {}", 
                   maxMessageSize, compressionEnabled, checksumEnabled);
    }

    /**
     * 注册Protobuf消息类型
     *
     * @param messageId    消息ID
     * @param messageClass 消息类型
     * @param parser       消息解析器
     * @param <T>          消息类型参数
     */
    public <T extends MessageLite> void registerMessage(int messageId, Class<T> messageClass, 
                                                        Parser<T> parser) {
        if (messageClass == null || parser == null) {
            throw new IllegalArgumentException("消息类型和解析器不能为null");
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

        messageParsers.put(messageId, parser);

        logger.debug("注册Protobuf消息: {} -> {}", messageId, messageClass.getSimpleName());
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
            messageParsers.remove(messageId);
            logger.debug("注销Protobuf消息: {} -> {}", messageId, messageClass.getSimpleName());
        }
    }

    @Override
    public boolean supports(Class<?> messageClass) {
        return MessageLite.class.isAssignableFrom(messageClass) && 
               messageTypeToId.containsKey(messageClass);
    }

    @Override
    protected void encodeMessageBody(Object message, ByteBuf out) throws Exception {
        if (!(message instanceof MessageLite protobufMessage)) {
            throw new IllegalArgumentException("消息必须是Protobuf MessageLite类型: " + message.getClass());
        }

        // 获取消息ID
        Integer messageId = messageTypeToId.get(message.getClass());
        if (messageId == null) {
            throw new IllegalArgumentException("未注册的Protobuf消息类型: " + message.getClass());
        }

        // 序列化消息
        byte[] messageBytes = protobufMessage.toByteArray();
        
        // 写入消息ID（4字节）
        out.writeInt(messageId);
        
        // 写入消息长度（4字节）
        out.writeInt(messageBytes.length);
        
        // 写入消息体
        out.writeBytes(messageBytes);

        logger.debug("编码Protobuf消息: {} (ID: {}, 大小: {})", 
                    message.getClass().getSimpleName(), messageId, messageBytes.length);
    }

    @Override
    protected Object decodeMessageBody(MessageHeader header, ByteBuf in) throws Exception {
        if (in.readableBytes() < 8) { // 至少需要8字节（消息ID + 长度）
            throw new IllegalStateException("消息体数据不足");
        }

        // 读取消息ID
        int messageId = in.readInt();
        
        // 读取消息长度
        int messageLength = in.readInt();
        
        if (messageLength < 0 || messageLength > in.readableBytes()) {
            throw new IllegalStateException("无效的消息长度: " + messageLength);
        }

        // 获取解析器
        Parser<? extends MessageLite> parser = messageParsers.get(messageId);
        if (parser == null) {
            throw new IllegalArgumentException("未知的消息ID: " + messageId);
        }

        // 读取消息数据
        byte[] messageBytes = new byte[messageLength];
        in.readBytes(messageBytes);

        // 解析消息
        try {
            MessageLite message = parser.parseFrom(messageBytes);
            logger.debug("解码Protobuf消息: {} (ID: {}, 大小: {})", 
                        message.getClass().getSimpleName(), messageId, messageLength);
            return message;
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Protobuf消息解析失败", e);
        }
    }

    @Override
    protected int estimateMessageBodySize(Object message) {
        if (!(message instanceof MessageLite protobufMessage)) {
            return 64; // 默认预估大小
        }

        // Protobuf消息的预估大小：消息头(8字节) + 序列化大小
        return 8 + protobufMessage.getSerializedSize();
    }

    @Override
    protected int getMessageType(Object message) {
        Integer messageId = messageTypeToId.get(message.getClass());
        return messageId != null ? messageId : 0;
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
        messageParsers.clear();
        logger.info("清空所有Protobuf消息注册");
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

    @Override
    public String toString() {
        return String.format("ProtobufCodec{registeredMessages=%d, maxSize=%d, compression=%s}", 
                           getRegisteredMessageCount(), maxMessageSize, compressionEnabled);
    }
}