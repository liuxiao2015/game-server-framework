/*
 * 文件名: ProtocolConverter.java
 * 用途: 协议转换接口
 * 实现内容:
 *   - 协议转换核心接口定义
 *   - TCP/WebSocket到HTTP转换
 *   - HTTP到TCP/WebSocket转换
 *   - 协议头映射和消息体转换
 *   - 编解码器管理
 * 技术选型:
 *   - WebFlux响应式编程
 *   - Netty编解码器
 *   - 协议适配器模式
 * 依赖关系:
 *   - 被GameProtocolHandler实现
 *   - 与WebSocketHandler协作
 *   - 集成Gateway路由
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.protocol;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 协议转换器接口
 * <p>
 * 定义协议转换的核心接口，支持多种协议之间的相互转换，
 * 包括TCP、WebSocket、HTTP等协议的转换和适配。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface ProtocolConverter<SOURCE, TARGET> {

    /**
     * 协议转换
     * <p>
     * 将源协议消息转换为目标协议消息。
     * </p>
     *
     * @param source 源协议消息
     * @param context 转换上下文
     * @return 目标协议消息
     */
    Mono<TARGET> convert(SOURCE source, ConversionContext context);

    /**
     * 检查是否支持指定的转换
     * <p>
     * 判断当前转换器是否支持从源类型到目标类型的转换。
     * </p>
     *
     * @param sourceType 源类型
     * @param targetType 目标类型
     * @return 是否支持转换
     */
    boolean supports(Class<?> sourceType, Class<?> targetType);

    /**
     * 获取转换器名称
     *
     * @return 转换器名称
     */
    String getName();

    /**
     * 获取转换器优先级
     * <p>
     * 优先级数值越小，优先级越高。
     * </p>
     *
     * @return 优先级
     */
    default int getPriority() {
        return 1000;
    }

    /**
     * 转换上下文
     * <p>
     * 封装转换过程中需要的上下文信息。
     * </p>
     */
    interface ConversionContext {

        /**
         * 获取上下文属性
         *
         * @param key 属性键
         * @return 属性值
         */
        Object getAttribute(String key);

        /**
         * 设置上下文属性
         *
         * @param key 属性键
         * @param value 属性值
         */
        void setAttribute(String key, Object value);

        /**
         * 获取所有属性
         *
         * @return 属性映射
         */
        Map<String, Object> getAttributes();

        /**
         * 获取源协议类型
         *
         * @return 源协议类型
         */
        String getSourceProtocol();

        /**
         * 获取目标协议类型
         *
         * @return 目标协议类型
         */
        String getTargetProtocol();

        /**
         * 获取客户端信息
         *
         * @return 客户端信息
         */
        ClientInfo getClientInfo();
    }

    /**
     * 客户端信息
     */
    interface ClientInfo {

        /**
         * 获取客户端IP
         *
         * @return 客户端IP
         */
        String getClientIp();

        /**
         * 获取用户代理
         *
         * @return 用户代理
         */
        String getUserAgent();

        /**
         * 获取会话ID
         *
         * @return 会话ID
         */
        String getSessionId();

        /**
         * 获取用户ID
         *
         * @return 用户ID
         */
        String getUserId();
    }

    /**
     * 协议消息
     * <p>
     * 通用的协议消息封装。
     * </p>
     */
    interface ProtocolMessage {

        /**
         * 获取消息ID
         *
         * @return 消息ID
         */
        String getMessageId();

        /**
         * 获取消息类型
         *
         * @return 消息类型
         */
        String getMessageType();

        /**
         * 获取消息头
         *
         * @return 消息头
         */
        Map<String, String> getHeaders();

        /**
         * 获取消息体
         *
         * @return 消息体
         */
        Object getBody();

        /**
         * 获取时间戳
         *
         * @return 时间戳
         */
        long getTimestamp();
    }

    /**
     * HTTP协议消息
     */
    interface HttpMessage extends ProtocolMessage {

        /**
         * 获取HTTP方法
         *
         * @return HTTP方法
         */
        String getMethod();

        /**
         * 获取请求路径
         *
         * @return 请求路径
         */
        String getPath();

        /**
         * 获取查询参数
         *
         * @return 查询参数
         */
        Map<String, String> getQueryParams();

        /**
         * 获取状态码
         *
         * @return 状态码
         */
        Integer getStatusCode();
    }

    /**
     * WebSocket协议消息
     */
    interface WebSocketMessage extends ProtocolMessage {

        /**
         * 获取消息帧类型
         *
         * @return 帧类型
         */
        FrameType getFrameType();

        /**
         * 是否为最后一帧
         *
         * @return 是否为最后一帧
         */
        boolean isFinalFragment();

        /**
         * WebSocket帧类型
         */
        enum FrameType {
            TEXT, BINARY, PING, PONG, CLOSE
        }
    }

    /**
     * TCP协议消息
     */
    interface TcpMessage extends ProtocolMessage {

        /**
         * 获取连接ID
         *
         * @return 连接ID
         */
        String getConnectionId();

        /**
         * 获取数据长度
         *
         * @return 数据长度
         */
        int getDataLength();

        /**
         * 获取原始数据
         *
         * @return 原始数据
         */
        byte[] getRawData();
    }
}