/*
 * 文件名: Connection.java
 * 用途: 连接抽象接口
 * 实现内容:
 *   - 定义网络连接的标准操作接口
 *   - 连接ID管理和状态跟踪
 *   - 连接属性存储和管理
 *   - 消息发送接收接口
 *   - 连接统计信息收集
 * 技术选型:
 *   - 接口抽象设计，支持多种底层实现
 *   - 泛型支持，适配不同消息类型
 *   - 状态机模式管理连接生命周期
 * 依赖关系:
 *   - 被NettyConnection等具体实现类实现
 *   - 被ConnectionManager管理
 *   - 为上层业务提供统一连接接口
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.core;

import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 连接抽象接口
 * <p>
 * 定义网络连接的标准操作，支持连接生命周期管理、属性存储、
 * 消息收发和统计信息收集。为不同协议和传输层提供统一抽象。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface Connection {

    /**
     * 连接状态枚举
     */
    enum ConnectionState {
        /** 连接中 */
        CONNECTING("连接中"),
        
        /** 已连接 */
        CONNECTED("已连接"),
        
        /** 断开中 */
        DISCONNECTING("断开中"),
        
        /** 已断开 */
        DISCONNECTED("已断开");

        private final String description;

        ConnectionState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 获取连接唯一标识
     *
     * @return 连接ID
     */
    String getId();

    /**
     * 获取连接状态
     *
     * @return 当前连接状态
     */
    ConnectionState getState();

    /**
     * 检查连接是否活跃
     *
     * @return true表示连接活跃，false表示已断开
     */
    boolean isActive();

    /**
     * 获取本地地址
     *
     * @return 本地socket地址
     */
    SocketAddress getLocalAddress();

    /**
     * 获取远程地址
     *
     * @return 远程socket地址
     */
    SocketAddress getRemoteAddress();

    /**
     * 发送消息
     *
     * @param message 要发送的消息
     * @return 发送结果的Future对象
     */
    CompletableFuture<Void> send(Object message);

    /**
     * 同步发送消息并等待完成
     *
     * @param message 要发送的消息
     * @throws Exception 发送失败时抛出异常
     */
    void sendSync(Object message) throws Exception;

    /**
     * 关闭连接
     *
     * @return 关闭操作的Future对象
     */
    CompletableFuture<Void> close();

    /**
     * 强制关闭连接
     */
    void forceClose();

    /**
     * 设置连接属性
     *
     * @param key   属性键
     * @param value 属性值
     * @return 之前的属性值，如果没有则返回null
     */
    Object setAttribute(String key, Object value);

    /**
     * 获取连接属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回null
     */
    Object getAttribute(String key);

    /**
     * 获取连接属性，如果不存在则返回默认值
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @param <T>          属性值类型
     * @return 属性值或默认值
     */
    <T> T getAttribute(String key, T defaultValue);

    /**
     * 移除连接属性
     *
     * @param key 属性键
     * @return 被移除的属性值，如果不存在则返回null
     */
    Object removeAttribute(String key);

    /**
     * 获取所有连接属性
     *
     * @return 属性映射的只读视图
     */
    Map<String, Object> getAttributes();

    /**
     * 清空所有连接属性
     */
    void clearAttributes();

    /**
     * 获取连接创建时间
     *
     * @return 连接创建时间
     */
    LocalDateTime getCreateTime();

    /**
     * 获取最后活跃时间
     *
     * @return 最后活跃时间
     */
    LocalDateTime getLastActiveTime();

    /**
     * 更新最后活跃时间
     */
    void updateLastActiveTime();

    /**
     * 获取已发送字节数
     *
     * @return 已发送字节数
     */
    long getSentBytes();

    /**
     * 获取已接收字节数
     *
     * @return 已接收字节数
     */
    long getReceivedBytes();

    /**
     * 获取已发送消息数
     *
     * @return 已发送消息数
     */
    long getSentMessages();

    /**
     * 获取已接收消息数
     *
     * @return 已接收消息数
     */
    long getReceivedMessages();

    /**
     * 添加连接监听器
     *
     * @param listener 连接监听器
     */
    void addConnectionListener(ConnectionListener listener);

    /**
     * 移除连接监听器
     *
     * @param listener 连接监听器
     */
    void removeConnectionListener(ConnectionListener listener);

    /**
     * 连接监听器接口
     */
    interface ConnectionListener {
        
        /**
         * 连接建立事件
         *
         * @param connection 连接对象
         */
        default void onConnected(Connection connection) {}

        /**
         * 连接断开事件
         *
         * @param connection 连接对象
         */
        default void onDisconnected(Connection connection) {}

        /**
         * 消息接收事件
         *
         * @param connection 连接对象
         * @param message    接收到的消息
         */
        default void onMessageReceived(Connection connection, Object message) {}

        /**
         * 消息发送事件
         *
         * @param connection 连接对象
         * @param message    发送的消息
         */
        default void onMessageSent(Connection connection, Object message) {}

        /**
         * 异常事件
         *
         * @param connection 连接对象
         * @param cause      异常原因
         */
        default void onException(Connection connection, Throwable cause) {}
    }
}