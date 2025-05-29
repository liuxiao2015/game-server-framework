/*
 * 文件名: CustomCodec.java
 * 用途: 自定义协议编解码器
 * 实现内容:
 *   - 灵活的自定义消息格式支持
 *   - 二进制优化的编解码实现
 *   - 变长编码和位域支持
 *   - 高性能序列化机制
 *   - 协议扩展性设计
 * 技术选型:
 *   - 继承MessageCodec基类
 *   - 自定义二进制格式
 *   - 变长整数编码（VarInt）
 *   - 位操作优化
 * 依赖关系:
 *   - 继承MessageCodec
 *   - 被Protocol接口使用
 *   - 支持Message对象序列化
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.codec;

import com.lx.gameserver.frame.network.core.Protocol;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义协议编解码器
 * <p>
 * 提供高性能的自定义二进制协议支持，采用变长编码和位域优化，
 * 支持灵活的消息格式定义和协议扩展。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CustomCodec extends MessageCodec {

    private static final Logger logger = LoggerFactory.getLogger(CustomCodec.class);

    // 魔数，用于协议识别
    public static final int MAGIC_NUMBER = 0xCAFEBABE;
    
    // 版本号
    public static final byte VERSION = 1;
    
    // 消息类型注册表
    private final Map<Class<?>, Byte> classToTypeMap = new ConcurrentHashMap<>();
    private final Map<Byte, Class<?>> typeToClassMap = new ConcurrentHashMap<>();
    
    // 字段类型枚举
    public enum FieldType {
        BYTE(1), SHORT(2), INT(4), LONG(8),
        FLOAT(4), DOUBLE(8), STRING(0), 
        BYTES(0), OBJECT(0), LIST(0), MAP(0);
        
        private final int fixedSize;
        
        FieldType(int fixedSize) {
            this.fixedSize = fixedSize;
        }
        
        public int getFixedSize() {
            return fixedSize;
        }
        
        public boolean isFixedSize() {
            return fixedSize > 0;
        }
    }

    /**
     * 构造函数
     */
    public CustomCodec() {
        super(1024 * 1024, true, 1024, true);  // maxMessageSize, compressionEnabled, compressionThreshold, checksumEnabled
        registerDefaultTypes();
    }

    /**
     * 注册默认消息类型
     */
    private void registerDefaultTypes() {
        // 这里可以注册常用的消息类型
        // registerMessageType(LoginMessage.class, (byte) 1);
        // registerMessageType(ChatMessage.class, (byte) 2);
        logger.debug("自定义编解码器初始化完成");
    }

    /**
     * 注册消息类型
     */
    public void registerMessageType(Class<?> messageClass, byte typeId) {
        classToTypeMap.put(messageClass, typeId);
        typeToClassMap.put(typeId, messageClass);
        logger.debug("注册消息类型: {} -> {}", messageClass.getSimpleName(), typeId);
    }

    @Override
    protected void encodeMessageBody(Object message, ByteBuf out) throws Exception {
        // 写入魔数
        out.writeInt(MAGIC_NUMBER);
        
        // 写入版本
        out.writeByte(VERSION);
        
        // 写入消息类型
        Byte messageType = classToTypeMap.get(message.getClass());
        if (messageType == null) {
            throw new IllegalArgumentException("未注册的消息类型: " + message.getClass());
        }
        out.writeByte(messageType);
        
        // 写入消息体
        encodeObject(message, out);
        
        logger.debug("编码自定义消息: {}, 类型: {}, 大小: {} bytes", 
                    message.getClass().getSimpleName(), messageType, out.readableBytes());
    }

    @Override
    protected Object decodeMessageBody(MessageHeader header, ByteBuf in) throws Exception {
        // 读取魔数
        int magic = in.readInt();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException("无效的魔数: " + Integer.toHexString(magic));
        }
        
        // 读取版本
        byte version = in.readByte();
        if (version != VERSION) {
            throw new IllegalArgumentException("不支持的版本: " + version);
        }
        
        // 读取消息类型
        byte messageType = in.readByte();
        Class<?> messageClass = typeToClassMap.get(messageType);
        if (messageClass == null) {
            throw new IllegalArgumentException("未知的消息类型: " + messageType);
        }
        
        // 解码消息体
        Object message = decodeObject(messageClass, in);
        
        logger.debug("解码自定义消息: {}, 类型: {}", messageClass.getSimpleName(), messageType);
        return message;
    }

    /**
     * 编码对象
     */
    private void encodeObject(Object obj, ByteBuf out) throws Exception {
        if (obj == null) {
            out.writeByte(0); // null标记
            return;
        }
        
        out.writeByte(1); // 非null标记
        
        if (obj instanceof String) {
            encodeString((String) obj, out);
        } else if (obj instanceof Integer) {
            encodeVarInt((Integer) obj, out);
        } else if (obj instanceof Long) {
            encodeVarLong((Long) obj, out);
        } else if (obj instanceof Boolean) {
            out.writeBoolean((Boolean) obj);
        } else if (obj instanceof Byte) {
            out.writeByte((Byte) obj);
        } else if (obj instanceof Short) {
            out.writeShort((Short) obj);
        } else if (obj instanceof Float) {
            out.writeFloat((Float) obj);
        } else if (obj instanceof Double) {
            out.writeDouble((Double) obj);
        } else if (obj instanceof byte[]) {
            encodeBytes((byte[]) obj, out);
        } else {
            // 复杂对象，使用反射或自定义序列化
            encodeComplexObject(obj, out);
        }
    }

    /**
     * 解码对象
     */
    private Object decodeObject(Class<?> clazz, ByteBuf in) throws Exception {
        byte nullFlag = in.readByte();
        if (nullFlag == 0) {
            return null;
        }
        
        if (clazz == String.class) {
            return decodeString(in);
        } else if (clazz == Integer.class || clazz == int.class) {
            return decodeVarInt(in);
        } else if (clazz == Long.class || clazz == long.class) {
            return decodeVarLong(in);
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return in.readBoolean();
        } else if (clazz == Byte.class || clazz == byte.class) {
            return in.readByte();
        } else if (clazz == Short.class || clazz == short.class) {
            return in.readShort();
        } else if (clazz == Float.class || clazz == float.class) {
            return in.readFloat();
        } else if (clazz == Double.class || clazz == double.class) {
            return in.readDouble();
        } else if (clazz == byte[].class) {
            return decodeBytes(in);
        } else {
            // 复杂对象，使用反射或自定义反序列化
            return decodeComplexObject(clazz, in);
        }
    }

    /**
     * 编码字符串
     */
    private void encodeString(String str, ByteBuf out) {
        if (str == null) {
            encodeVarInt(0, out);
            return;
        }
        
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        encodeVarInt(bytes.length, out);
        out.writeBytes(bytes);
    }

    /**
     * 解码字符串
     */
    private String decodeString(ByteBuf in) {
        int length = decodeVarInt(in);
        if (length == 0) {
            return "";
        }
        
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 编码字节数组
     */
    private void encodeBytes(byte[] bytes, ByteBuf out) {
        if (bytes == null) {
            encodeVarInt(0, out);
            return;
        }
        
        encodeVarInt(bytes.length, out);
        out.writeBytes(bytes);
    }

    /**
     * 解码字节数组
     */
    private byte[] decodeBytes(ByteBuf in) {
        int length = decodeVarInt(in);
        if (length == 0) {
            return new byte[0];
        }
        
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return bytes;
    }

    /**
     * 编码变长整数
     */
    private void encodeVarInt(int value, ByteBuf out) {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    /**
     * 解码变长整数
     */
    private int decodeVarInt(ByteBuf in) {
        int value = 0;
        int shift = 0;
        byte b;
        
        do {
            b = in.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        
        return value;
    }

    /**
     * 编码变长长整数
     */
    private void encodeVarLong(long value, ByteBuf out) {
        while ((value & 0xFFFFFFFFFFFFFF80L) != 0) {
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((int) (value & 0x7F));
    }

    /**
     * 解码变长长整数
     */
    private long decodeVarLong(ByteBuf in) {
        long value = 0;
        int shift = 0;
        byte b;
        
        do {
            b = in.readByte();
            value |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        
        return value;
    }

    /**
     * 编码复杂对象（简单实现）
     */
    private void encodeComplexObject(Object obj, ByteBuf out) throws Exception {
        // 这里应该实现更复杂的对象序列化逻辑
        // 可以使用反射或者对象实现Serializable接口
        throw new UnsupportedOperationException("复杂对象编码尚未实现: " + obj.getClass());
    }

    /**
     * 解码复杂对象（简单实现）
     */
    private Object decodeComplexObject(Class<?> clazz, ByteBuf in) throws Exception {
        // 这里应该实现更复杂的对象反序列化逻辑
        throw new UnsupportedOperationException("复杂对象解码尚未实现: " + clazz);
    }

    @Override
    protected int estimateMessageBodySize(Object message) {
        if (message == null) {
            return 0;
        }
        
        // 简单估算：基于消息类型
        if (message instanceof String) {
            return ((String) message).length() * 3; // UTF-8最大3字节per字符
        } else if (message instanceof byte[]) {
            return ((byte[]) message).length;
        } else {
            // 默认估算
            return 128;
        }
    }

    @Override
    protected int getMessageType(Object message) {
        Byte messageType = classToTypeMap.get(message.getClass());
        return messageType != null ? messageType : 0;
    }

    public Protocol.MessageFormat getMessageFormat() {
        return Protocol.MessageFormat.CUSTOM_BINARY;
    }

    @Override
    public boolean supports(Class<?> messageClass) {
        return classToTypeMap.containsKey(messageClass);
    }

    @Override
    public int getEstimatedSize(Object message) {
        if (message == null) {
            return 0;
        }
        return 9 + estimateMessageBodySize(message); // 魔数(4) + 版本(1) + 类型(1) + 其他头信息(3) + 消息体
    }

    /**
     * 获取已注册的消息类型数量
     */
    public int getRegisteredTypeCount() {
        return classToTypeMap.size();
    }

    /**
     * 检查消息类型是否已注册
     */
    public boolean isTypeRegistered(Class<?> messageClass) {
        return classToTypeMap.containsKey(messageClass);
    }

    /**
     * 检查类型ID是否已使用
     */
    public boolean isTypeIdUsed(byte typeId) {
        return typeToClassMap.containsKey(typeId);
    }
}