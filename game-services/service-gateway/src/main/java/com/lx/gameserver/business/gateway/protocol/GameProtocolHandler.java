/*
 * 文件名: GameProtocolHandler.java
 * 用途: 游戏协议处理器
 * 实现内容:
 *   - 游戏协议处理核心逻辑
 *   - Protobuf协议解析
 *   - JSON协议支持
 *   - 二进制协议支持
 *   - 协议版本管理
 *   - 协议升级支持
 * 技术选型:
 *   - Google Protobuf
 *   - Jackson JSON处理
 *   - 自定义二进制协议
 * 依赖关系:
 *   - 实现ProtocolConverter接口
 *   - 与编解码器集成
 *   - 支持多版本协议
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏协议处理器
 * <p>
 * 处理游戏相关的各种协议格式，包括Protobuf、JSON、自定义二进制协议等。
 * 支持协议版本管理和协议升级，提供高性能的编解码能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameProtocolHandler implements ProtocolConverter<byte[], Object> {

    private final ObjectMapper objectMapper;
    private final Map<String, ProtocolCodec> codecRegistry = new ConcurrentHashMap<>();
    private final Map<Integer, String> versionMapping = new ConcurrentHashMap<>();

    /**
     * 初始化协议处理器
     */
    public void init() {
        // 注册协议编解码器
        registerCodec("JSON", new JsonProtocolCodec());
        registerCodec("PROTOBUF", new ProtobufProtocolCodec());
        registerCodec("BINARY", new BinaryProtocolCodec());
        
        // 注册协议版本映射
        registerVersion(1, "JSON");
        registerVersion(2, "PROTOBUF");
        registerVersion(3, "BINARY");
        
        log.info("游戏协议处理器初始化完成，支持协议: {}", codecRegistry.keySet());
    }

    @Override
    public Mono<Object> convert(byte[] source, ConversionContext context) {
        return Mono.fromCallable(() -> {
            try {
                // 解析协议头
                ProtocolHeader header = parseProtocolHeader(source);
                
                // 获取协议编解码器
                String protocolType = getProtocolType(header.getVersion());
                ProtocolCodec codec = codecRegistry.get(protocolType);
                
                if (codec == null) {
                    throw new UnsupportedOperationException("不支持的协议版本: " + header.getVersion());
                }
                
                // 解码消息体
                byte[] payload = extractPayload(source, header);
                GameMessage message = codec.decode(payload, header);
                
                // 设置上下文信息
                context.setAttribute("protocol.version", header.getVersion());
                context.setAttribute("protocol.type", protocolType);
                context.setAttribute("message.id", message.getMessageId());
                context.setAttribute("message.type", message.getMessageType());
                
                log.debug("协议转换完成: {} -> {}, messageId: {}", 
                    protocolType, message.getClass().getSimpleName(), message.getMessageId());
                
                return message;
                
            } catch (Exception e) {
                log.error("协议转换失败", e);
                throw new RuntimeException("协议转换失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public boolean supports(Class<?> sourceType, Class<?> targetType) {
        return byte[].class.isAssignableFrom(sourceType) && Object.class.isAssignableFrom(targetType);
    }

    @Override
    public String getName() {
        return "GameProtocolHandler";
    }

    @Override
    public int getPriority() {
        return 100; // 高优先级
    }

    /**
     * 注册协议编解码器
     *
     * @param protocolType 协议类型
     * @param codec 编解码器
     */
    public void registerCodec(String protocolType, ProtocolCodec codec) {
        codecRegistry.put(protocolType, codec);
        log.info("注册协议编解码器: {}", protocolType);
    }

    /**
     * 注册协议版本映射
     *
     * @param version 版本号
     * @param protocolType 协议类型
     */
    public void registerVersion(int version, String protocolType) {
        versionMapping.put(version, protocolType);
        log.info("注册协议版本映射: {} -> {}", version, protocolType);
    }

    /**
     * 解析协议头
     *
     * @param data 原始数据
     * @return 协议头
     */
    private ProtocolHeader parseProtocolHeader(byte[] data) {
        if (data.length < 12) { // 最小协议头长度
            throw new IllegalArgumentException("数据长度不足，无法解析协议头");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        ProtocolHeader header = new ProtocolHeader();
        header.setMagic(buffer.getInt());           // 4字节魔数
        header.setVersion(buffer.getShort());       // 2字节版本号
        header.setHeaderLength(buffer.getShort());  // 2字节头长度
        header.setPayloadLength(buffer.getInt());   // 4字节载荷长度
        
        // 验证魔数
        if (header.getMagic() != 0x12345678) {
            throw new IllegalArgumentException("无效的协议魔数: " + Integer.toHexString(header.getMagic()));
        }
        
        // 解析扩展头信息
        if (header.getHeaderLength() > 12) {
            byte[] extHeaderData = new byte[header.getHeaderLength() - 12];
            buffer.get(extHeaderData);
            parseExtendedHeader(header, extHeaderData);
        }
        
        return header;
    }

    /**
     * 解析扩展协议头
     *
     * @param header 协议头
     * @param extData 扩展数据
     */
    private void parseExtendedHeader(ProtocolHeader header, byte[] extData) {
        ByteBuffer buffer = ByteBuffer.wrap(extData);
        Map<String, String> extHeaders = new HashMap<>();
        
        while (buffer.hasRemaining()) {
            try {
                byte keyLen = buffer.get();
                if (keyLen <= 0 || keyLen > buffer.remaining()) break;
                
                byte[] keyBytes = new byte[keyLen];
                buffer.get(keyBytes);
                String key = new String(keyBytes, StandardCharsets.UTF_8);
                
                byte valueLen = buffer.get();
                if (valueLen <= 0 || valueLen > buffer.remaining()) break;
                
                byte[] valueBytes = new byte[valueLen];
                buffer.get(valueBytes);
                String value = new String(valueBytes, StandardCharsets.UTF_8);
                
                extHeaders.put(key, value);
            } catch (Exception e) {
                log.warn("解析扩展协议头失败", e);
                break;
            }
        }
        
        header.setExtHeaders(extHeaders);
    }

    /**
     * 提取消息载荷
     *
     * @param data 原始数据
     * @param header 协议头
     * @return 载荷数据
     */
    private byte[] extractPayload(byte[] data, ProtocolHeader header) {
        int payloadStart = header.getHeaderLength();
        int payloadLength = header.getPayloadLength();
        
        if (payloadStart + payloadLength > data.length) {
            throw new IllegalArgumentException("载荷长度超出数据范围");
        }
        
        byte[] payload = new byte[payloadLength];
        System.arraycopy(data, payloadStart, payload, 0, payloadLength);
        
        return payload;
    }

    /**
     * 获取协议类型
     *
     * @param version 版本号
     * @return 协议类型
     */
    private String getProtocolType(short version) {
        return versionMapping.getOrDefault((int) version, "JSON"); // 默认JSON协议
    }

    /**
     * 协议头
     */
    @Data
    public static class ProtocolHeader {
        private int magic;              // 魔数
        private short version;          // 版本号
        private short headerLength;     // 头长度
        private int payloadLength;      // 载荷长度
        private Map<String, String> extHeaders = new HashMap<>(); // 扩展头信息
    }

    /**
     * 游戏消息
     */
    @Data
    public static class GameMessage implements ProtocolConverter.ProtocolMessage {
        private String messageId;
        private String messageType;
        private Map<String, String> headers = new HashMap<>();
        private Object body;
        private long timestamp;
        
        // 游戏特有字段
        private String userId;
        private String sessionId;
        private String serverId;
        private int sequence;
    }

    /**
     * 协议编解码器接口
     */
    public interface ProtocolCodec {
        
        /**
         * 编码消息
         *
         * @param message 消息对象
         * @return 编码后的字节数组
         */
        byte[] encode(GameMessage message) throws IOException;
        
        /**
         * 解码消息
         *
         * @param data 字节数据
         * @param header 协议头
         * @return 解码后的消息对象
         */
        GameMessage decode(byte[] data, ProtocolHeader header) throws IOException;
    }

    /**
     * JSON协议编解码器
     */
    public class JsonProtocolCodec implements ProtocolCodec {
        
        @Override
        public byte[] encode(GameMessage message) throws IOException {
            return objectMapper.writeValueAsBytes(message);
        }
        
        @Override
        public GameMessage decode(byte[] data, ProtocolHeader header) throws IOException {
            return objectMapper.readValue(data, GameMessage.class);
        }
    }

    /**
     * Protobuf协议编解码器
     */
    public static class ProtobufProtocolCodec implements ProtocolCodec {
        
        @Override
        public byte[] encode(GameMessage message) throws IOException {
            // TODO: 实现Protobuf编码
            // 这里需要根据具体的Protobuf定义来实现
            throw new UnsupportedOperationException("Protobuf编码待实现");
        }
        
        @Override
        public GameMessage decode(byte[] data, ProtocolHeader header) throws IOException {
            // TODO: 实现Protobuf解码
            // 这里需要根据具体的Protobuf定义来实现
            throw new UnsupportedOperationException("Protobuf解码待实现");
        }
    }

    /**
     * 二进制协议编解码器
     */
    public static class BinaryProtocolCodec implements ProtocolCodec {
        
        @Override
        public byte[] encode(GameMessage message) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 写入消息ID
            writeString(baos, message.getMessageId());
            // 写入消息类型
            writeString(baos, message.getMessageType());
            // 写入时间戳
            writeLong(baos, message.getTimestamp());
            // 写入用户ID
            writeString(baos, message.getUserId());
            // 写入序列号
            writeInt(baos, message.getSequence());
            
            // 写入消息体（简化处理，实际应根据具体协议定义）
            if (message.getBody() instanceof String) {
                writeString(baos, (String) message.getBody());
            } else if (message.getBody() instanceof byte[]) {
                writeBytes(baos, (byte[]) message.getBody());
            }
            
            return baos.toByteArray();
        }
        
        @Override
        public GameMessage decode(byte[] data, ProtocolHeader header) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            
            GameMessage message = new GameMessage();
            message.setMessageId(readString(bais));
            message.setMessageType(readString(bais));
            message.setTimestamp(readLong(bais));
            message.setUserId(readString(bais));
            message.setSequence(readInt(bais));
            
            // 读取剩余数据作为消息体
            int remaining = bais.available();
            if (remaining > 0) {
                byte[] bodyData = new byte[remaining];
                bais.read(bodyData);
                message.setBody(bodyData);
            }
            
            return message;
        }
        
        private void writeString(ByteArrayOutputStream baos, String str) throws IOException {
            if (str == null) str = "";
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            writeInt(baos, bytes.length);
            baos.write(bytes);
        }
        
        private void writeInt(ByteArrayOutputStream baos, int value) throws IOException {
            baos.write((value >>> 24) & 0xFF);
            baos.write((value >>> 16) & 0xFF);
            baos.write((value >>> 8) & 0xFF);
            baos.write(value & 0xFF);
        }
        
        private void writeLong(ByteArrayOutputStream baos, long value) throws IOException {
            writeInt(baos, (int) (value >>> 32));
            writeInt(baos, (int) value);
        }
        
        private void writeBytes(ByteArrayOutputStream baos, byte[] data) throws IOException {
            writeInt(baos, data.length);
            baos.write(data);
        }
        
        private String readString(ByteArrayInputStream bais) throws IOException {
            int length = readInt(bais);
            if (length <= 0) return "";
            
            byte[] bytes = new byte[length];
            int bytesRead = bais.read(bytes);
            if (bytesRead != length) {
                throw new IOException("读取字符串数据不完整");
            }
            
            return new String(bytes, StandardCharsets.UTF_8);
        }
        
        private int readInt(ByteArrayInputStream bais) throws IOException {
            int b1 = bais.read();
            int b2 = bais.read();
            int b3 = bais.read();
            int b4 = bais.read();
            
            if ((b1 | b2 | b3 | b4) < 0) {
                throw new IOException("读取整数数据不完整");
            }
            
            return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        }
        
        private long readLong(ByteArrayInputStream bais) throws IOException {
            long high = readInt(bais);
            long low = readInt(bais) & 0xFFFFFFFFL;
            return (high << 32) | low;
        }
    }
}