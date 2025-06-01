/*
 * 文件名: Protocol.java
 * 用途: 协议定义接口
 * 实现内容:
 *   - 定义协议的标准规范
 *   - 协议ID和版本管理
 *   - 编码器和解码器接口定义
 *   - 协议升级和兼容性支持
 *   - 协议特性配置
 * 技术选型:
 *   - 接口抽象设计，支持多种协议实现
 *   - 泛型支持，适配不同消息类型
 *   - 版本控制机制，支持协议演进
 * 依赖关系:
 *   - 被ProtobufCodec、JsonCodec等具体实现
 *   - 为消息编解码提供统一接口
 *   - 被NetworkServer和NetworkClient使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.core;

import io.netty.buffer.ByteBuf;

/**
 * 协议定义接口
 * <p>
 * 定义网络协议的标准规范，包括协议标识、版本控制、
 * 编解码器接口和特性配置。支持协议升级和向后兼容。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface Protocol {

    /**
     * 协议类型枚举
     */
    enum ProtocolType {
        /** TCP协议 */
        TCP("TCP", "传输控制协议"),
        
        /** UDP协议 */
        UDP("UDP", "用户数据报协议"),
        
        /** WebSocket协议 */
        WEBSOCKET("WebSocket", "Web套接字协议"),
        
        /** HTTP协议 */
        HTTP("HTTP", "超文本传输协议");

        private final String code;
        private final String description;

        ProtocolType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 消息格式枚举
     */
    enum MessageFormat {
        /** Protobuf格式 */
        PROTOBUF("protobuf", "Protocol Buffers二进制格式"),
        
        /** JSON格式 */
        JSON("json", "JavaScript对象表示法"),
        
        /** 自定义二进制格式 */
        CUSTOM_BINARY("custom-binary", "自定义二进制格式"),
        
        /** XML格式 */
        XML("xml", "可扩展标记语言格式");

        private final String code;
        private final String description;

        MessageFormat(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 获取协议ID
     *
     * @return 协议唯一标识
     */
    String getProtocolId();

    /**
     * 获取协议名称
     *
     * @return 协议名称
     */
    String getProtocolName();

    /**
     * 获取协议版本
     *
     * @return 协议版本号
     */
    String getVersion();

    /**
     * 获取协议类型
     *
     * @return 协议类型
     */
    ProtocolType getProtocolType();

    /**
     * 获取消息格式
     *
     * @return 消息格式
     */
    MessageFormat getMessageFormat();

    /**
     * 获取编码器
     *
     * @return 消息编码器
     */
    MessageEncoder getEncoder();

    /**
     * 获取解码器
     *
     * @return 消息解码器
     */
    MessageDecoder getDecoder();

    /**
     * 检查协议版本兼容性
     *
     * @param version 要检查的版本
     * @return true表示兼容，false表示不兼容
     */
    boolean isVersionCompatible(String version);

    /**
     * 获取支持的最低版本
     *
     * @return 最低兼容版本
     */
    String getMinSupportedVersion();

    /**
     * 获取最大消息大小限制
     *
     * @return 最大消息大小（字节）
     */
    int getMaxMessageSize();

    /**
     * 是否支持压缩
     *
     * @return true表示支持压缩
     */
    boolean supportCompression();

    /**
     * 是否支持加密
     *
     * @return true表示支持加密
     */
    boolean supportEncryption();

    /**
     * 消息编码器接口
     */
    interface MessageEncoder {
        
        /**
         * 编码消息
         *
         * @param message 要编码的消息对象
         * @param out     输出缓冲区
         * @throws Exception 编码失败时抛出异常
         */
        void encode(Object message, ByteBuf out) throws Exception;

        /**
         * 获取编码后的预估大小
         *
         * @param message 要编码的消息对象
         * @return 预估大小（字节）
         */
        int getEstimatedSize(Object message);

        /**
         * 是否支持该消息类型
         *
         * @param messageClass 消息类型
         * @return true表示支持
         */
        boolean supports(Class<?> messageClass);
    }

    /**
     * 消息解码器接口
     */
    interface MessageDecoder {
        
        /**
         * 解码消息
         *
         * @param in 输入缓冲区
         * @return 解码后的消息对象，如果数据不完整返回null
         * @throws Exception 解码失败时抛出异常
         */
        Object decode(ByteBuf in) throws Exception;

        /**
         * 检查是否有完整的消息可解码
         *
         * @param in 输入缓冲区
         * @return true表示有完整消息
         */
        boolean hasCompleteMessage(ByteBuf in);

        /**
         * 获取下一个消息的长度
         *
         * @param in 输入缓冲区
         * @return 消息长度，-1表示长度未知
         */
        int getNextMessageLength(ByteBuf in);
    }

    /**
     * 协议构建器接口
     */
    interface ProtocolBuilder {
        
        /**
         * 设置协议ID
         *
         * @param protocolId 协议ID
         * @return 构建器实例
         */
        ProtocolBuilder protocolId(String protocolId);

        /**
         * 设置协议名称
         *
         * @param name 协议名称
         * @return 构建器实例
         */
        ProtocolBuilder name(String name);

        /**
         * 设置协议版本
         *
         * @param version 协议版本
         * @return 构建器实例
         */
        ProtocolBuilder version(String version);

        /**
         * 设置协议类型
         *
         * @param type 协议类型
         * @return 构建器实例
         */
        ProtocolBuilder type(ProtocolType type);

        /**
         * 设置消息格式
         *
         * @param format 消息格式
         * @return 构建器实例
         */
        ProtocolBuilder messageFormat(MessageFormat format);

        /**
         * 设置编码器
         *
         * @param encoder 编码器
         * @return 构建器实例
         */
        ProtocolBuilder encoder(MessageEncoder encoder);

        /**
         * 设置解码器
         *
         * @param decoder 解码器
         * @return 构建器实例
         */
        ProtocolBuilder decoder(MessageDecoder decoder);

        /**
         * 设置最大消息大小
         *
         * @param maxSize 最大消息大小
         * @return 构建器实例
         */
        ProtocolBuilder maxMessageSize(int maxSize);

        /**
         * 启用压缩
         *
         * @return 构建器实例
         */
        ProtocolBuilder enableCompression();

        /**
         * 启用加密
         *
         * @return 构建器实例
         */
        ProtocolBuilder enableEncryption();

        /**
         * 构建协议实例
         *
         * @return 协议实例
         */
        Protocol build();
    }
}