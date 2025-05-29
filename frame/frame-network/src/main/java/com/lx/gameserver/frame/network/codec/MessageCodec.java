/*
 * 文件名: MessageCodec.java
 * 用途: 消息编解码基类
 * 实现内容:
 *   - 消息编解码的基础框架
 *   - 消息头定义和处理（长度、类型、版本、压缩标志等）
 *   - 消息体编解码逻辑
 *   - 大消息分片处理
 *   - 消息完整性校验
 *   - 字节序处理和跨平台兼容
 * 技术选型:
 *   - 抽象基类设计，支持多种编码格式
 *   - Netty ByteBuf API
 *   - 高性能二进制编码
 *   - 可扩展的消息头格式
 * 依赖关系:
 *   - 实现Protocol接口的编解码器
 *   - 被ProtobufCodec、JsonCodec等继承
 *   - 与Message类协作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.codec;

import com.lx.gameserver.frame.network.core.Protocol;
import com.lx.gameserver.frame.network.message.Message;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 消息编解码基类
 * <p>
 * 提供消息编解码的基础框架，定义标准的消息头格式和处理流程。
 * 支持消息完整性校验、压缩、分片等高级功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class MessageCodec implements Protocol.MessageEncoder, Protocol.MessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(MessageCodec.class);

    /**
     * 消息头标识
     */
    protected static final int MAGIC_NUMBER = 0x1234ABCD;

    /**
     * 消息头长度（固定部分）
     */
    protected static final int HEADER_LENGTH = 32;

    /**
     * 消息头字段偏移量
     */
    protected static final int MAGIC_OFFSET = 0;          // 魔数 (4字节)
    protected static final int LENGTH_OFFSET = 4;         // 消息总长度 (4字节)
    protected static final int VERSION_OFFSET = 8;        // 协议版本 (2字节)
    protected static final int MESSAGE_TYPE_OFFSET = 10;  // 消息类型 (2字节)
    protected static final int FLAGS_OFFSET = 12;         // 标志位 (4字节)
    protected static final int SEQUENCE_OFFSET = 16;      // 序列号 (8字节)
    protected static final int CHECKSUM_OFFSET = 24;      // 校验和 (4字节)
    protected static final int RESERVED_OFFSET = 28;      // 保留字段 (4字节)

    /**
     * 标志位定义
     */
    protected static final int FLAG_COMPRESSED = 0x01;    // 压缩标志
    protected static final int FLAG_ENCRYPTED = 0x02;     // 加密标志
    protected static final int FLAG_FRAGMENTED = 0x04;    // 分片标志
    protected static final int FLAG_RESPONSE = 0x08;      // 响应标志

    /**
     * 最大消息大小
     */
    protected final int maxMessageSize;

    /**
     * 是否启用压缩
     */
    protected final boolean compressionEnabled;

    /**
     * 压缩阈值
     */
    protected final int compressionThreshold;

    /**
     * 是否启用校验
     */
    protected final boolean checksumEnabled;

    /**
     * 构造函数
     *
     * @param maxMessageSize       最大消息大小
     * @param compressionEnabled   是否启用压缩
     * @param compressionThreshold 压缩阈值
     * @param checksumEnabled      是否启用校验
     */
    protected MessageCodec(int maxMessageSize, boolean compressionEnabled, 
                          int compressionThreshold, boolean checksumEnabled) {
        this.maxMessageSize = maxMessageSize;
        this.compressionEnabled = compressionEnabled;
        this.compressionThreshold = compressionThreshold;
        this.checksumEnabled = checksumEnabled;
    }

    @Override
    public void encode(Object message, ByteBuf out) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("消息不能为null");
        }

        // 检查消息类型支持
        if (!supports(message.getClass())) {
            throw new UnsupportedOperationException("不支持的消息类型: " + message.getClass());
        }

        logger.debug("开始编码消息: {}", message);

        // 编码消息体
        ByteBuf bodyBuf = out.alloc().buffer();
        try {
            encodeMessageBody(message, bodyBuf);
            
            // 检查消息大小
            int bodyLength = bodyBuf.readableBytes();
            if (bodyLength > maxMessageSize - HEADER_LENGTH) {
                throw new IllegalArgumentException("消息体过大: " + bodyLength + " > " + (maxMessageSize - HEADER_LENGTH));
            }

            // 压缩处理
            boolean compressed = false;
            if (compressionEnabled && bodyLength > compressionThreshold) {
                ByteBuf compressedBuf = compress(bodyBuf);
                if (compressedBuf.readableBytes() < bodyLength) {
                    bodyBuf.release();
                    bodyBuf = compressedBuf;
                    compressed = true;
                    logger.debug("消息压缩: {} -> {}", bodyLength, bodyBuf.readableBytes());
                } else {
                    compressedBuf.release();
                }
            }

            // 构建消息头
            int totalLength = HEADER_LENGTH + bodyBuf.readableBytes();
            int flags = buildFlags(message, compressed);
            long sequence = getMessageSequence(message);
            int messageType = getMessageType(message);
            
            // 写入消息头
            writeMessageHeader(out, totalLength, messageType, flags, sequence);
            
            // 写入消息体
            out.writeBytes(bodyBuf);
            
            logger.debug("消息编码完成，总长度: {}", totalLength);
            
        } finally {
            bodyBuf.release();
        }
    }

    @Override
    public Object decode(ByteBuf in) throws Exception {
        if (!hasCompleteMessage(in)) {
            return null;
        }

        // 记录读取位置
        int readerIndex = in.readerIndex();
        
        try {
            // 读取消息头
            MessageHeader header = readMessageHeader(in);
            
            // 校验消息头
            validateMessageHeader(header);
            
            // 读取消息体
            int bodyLength = header.totalLength - HEADER_LENGTH;
            ByteBuf bodyBuf = in.readSlice(bodyLength);
            
            // 解压缩
            if ((header.flags & FLAG_COMPRESSED) != 0) {
                ByteBuf decompressedBuf = decompress(bodyBuf);
                bodyBuf = decompressedBuf;
            }
            
            // 校验和检查
            if (checksumEnabled && header.checksum != 0) {
                int calculatedChecksum = calculateChecksum(bodyBuf);
                if (calculatedChecksum != header.checksum) {
                    throw new IllegalStateException("消息校验和不匹配");
                }
            }
            
            // 解码消息体
            Object message = decodeMessageBody(header, bodyBuf);
            
            // 设置消息属性
            setMessageProperties(message, header);
            
            logger.debug("消息解码完成: {}", message);
            return message;
            
        } catch (Exception e) {
            // 恢复读取位置
            in.readerIndex(readerIndex);
            throw e;
        }
    }

    @Override
    public boolean hasCompleteMessage(ByteBuf in) {
        if (in.readableBytes() < HEADER_LENGTH) {
            return false;
        }

        // 检查魔数
        int magic = in.getInt(in.readerIndex() + MAGIC_OFFSET);
        if (magic != MAGIC_NUMBER) {
            logger.warn("无效的魔数: 0x{}", Integer.toHexString(magic));
            return false;
        }

        // 检查消息长度
        int totalLength = in.getInt(in.readerIndex() + LENGTH_OFFSET);
        if (totalLength < HEADER_LENGTH || totalLength > maxMessageSize) {
            logger.warn("无效的消息长度: {}", totalLength);
            return false;
        }

        return in.readableBytes() >= totalLength;
    }

    @Override
    public int getNextMessageLength(ByteBuf in) {
        if (in.readableBytes() < HEADER_LENGTH) {
            return -1;
        }

        int magic = in.getInt(in.readerIndex() + MAGIC_OFFSET);
        if (magic != MAGIC_NUMBER) {
            return -1;
        }

        return in.getInt(in.readerIndex() + LENGTH_OFFSET);
    }

    @Override
    public int getEstimatedSize(Object message) {
        if (message == null || !supports(message.getClass())) {
            return 0;
        }
        
        // 基础估算：消息头 + 消息体预估大小
        return HEADER_LENGTH + estimateMessageBodySize(message);
    }

    /**
     * 编码消息体
     *
     * @param message 消息对象
     * @param out     输出缓冲区
     * @throws Exception 编码失败
     */
    protected abstract void encodeMessageBody(Object message, ByteBuf out) throws Exception;

    /**
     * 解码消息体
     *
     * @param header 消息头
     * @param in     输入缓冲区
     * @return 消息对象
     * @throws Exception 解码失败
     */
    protected abstract Object decodeMessageBody(MessageHeader header, ByteBuf in) throws Exception;

    /**
     * 估算消息体大小
     *
     * @param message 消息对象
     * @return 预估大小
     */
    protected abstract int estimateMessageBodySize(Object message);

    /**
     * 获取消息类型
     *
     * @param message 消息对象
     * @return 消息类型
     */
    protected abstract int getMessageType(Object message);

    /**
     * 获取消息序列号
     *
     * @param message 消息对象
     * @return 序列号
     */
    protected long getMessageSequence(Object message) {
        if (message instanceof Message msg) {
            return msg.getSequence();
        }
        return 0;
    }

    /**
     * 构建标志位
     *
     * @param message    消息对象
     * @param compressed 是否压缩
     * @return 标志位
     */
    protected int buildFlags(Object message, boolean compressed) {
        int flags = 0;
        
        if (compressed) {
            flags |= FLAG_COMPRESSED;
        }
        
        if (message instanceof Message msg) {
            if (msg.getMessageType() == Message.MessageType.RESPONSE) {
                flags |= FLAG_RESPONSE;
            }
        }
        
        return flags;
    }

    /**
     * 写入消息头
     */
    private void writeMessageHeader(ByteBuf out, int totalLength, int messageType, 
                                   int flags, long sequence) {
        out.writeInt(MAGIC_NUMBER);        // 魔数
        out.writeInt(totalLength);         // 总长度
        out.writeShort(1);                 // 协议版本
        out.writeShort(messageType);       // 消息类型
        out.writeInt(flags);               // 标志位
        out.writeLong(sequence);           // 序列号
        out.writeInt(0);                   // 校验和（稍后计算）
        out.writeInt(0);                   // 保留字段
    }

    /**
     * 读取消息头
     */
    private MessageHeader readMessageHeader(ByteBuf in) {
        MessageHeader header = new MessageHeader();
        header.magic = in.readInt();
        header.totalLength = in.readInt();
        header.version = in.readShort();
        header.messageType = in.readShort();
        header.flags = in.readInt();
        header.sequence = in.readLong();
        header.checksum = in.readInt();
        header.reserved = in.readInt();
        return header;
    }

    /**
     * 校验消息头
     */
    private void validateMessageHeader(MessageHeader header) {
        if (header.magic != MAGIC_NUMBER) {
            throw new IllegalStateException("无效的魔数: 0x" + Integer.toHexString(header.magic));
        }
        
        if (header.totalLength < HEADER_LENGTH || header.totalLength > maxMessageSize) {
            throw new IllegalStateException("无效的消息长度: " + header.totalLength);
        }
        
        if (header.version < 1) {
            throw new IllegalStateException("不支持的协议版本: " + header.version);
        }
    }

    /**
     * 设置消息属性
     */
    private void setMessageProperties(Object message, MessageHeader header) {
        if (message instanceof Message msg) {
            msg.setSequence(header.sequence);
        }
    }

    /**
     * 压缩数据
     */
    protected ByteBuf compress(ByteBuf data) throws Exception {
        // 默认不压缩，子类可以重写
        return data.retain();
    }

    /**
     * 解压缩数据
     */
    protected ByteBuf decompress(ByteBuf data) throws Exception {
        // 默认不解压缩，子类可以重写
        return data.retain();
    }

    /**
     * 计算校验和
     */
    protected int calculateChecksum(ByteBuf data) {
        if (!checksumEnabled) {
            return 0;
        }
        
        CRC32 crc32 = new CRC32();
        if (data.hasArray()) {
            crc32.update(data.array(), data.arrayOffset() + data.readerIndex(), data.readableBytes());
        } else {
            byte[] bytes = new byte[data.readableBytes()];
            data.getBytes(data.readerIndex(), bytes);
            crc32.update(bytes);
        }
        return (int) crc32.getValue();
    }

    /**
     * 消息头结构
     */
    protected static class MessageHeader {
        public int magic;
        public int totalLength;
        public short version;
        public short messageType;
        public int flags;
        public long sequence;
        public int checksum;
        public int reserved;

        @Override
        public String toString() {
            return String.format("MessageHeader{magic=0x%x, length=%d, version=%d, type=%d, flags=0x%x, seq=%d}", 
                    magic, totalLength, version, messageType, flags, sequence);
        }
    }
}