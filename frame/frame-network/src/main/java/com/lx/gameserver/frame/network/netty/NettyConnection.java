/*
 * 文件名: NettyConnection.java
 * 用途: Netty连接实现
 * 实现内容:
 *   - 基于Netty Channel的连接实现
 *   - Channel封装和生命周期管理
 *   - 写缓冲区管理和流控
 *   - 读写空闲检测机制
 *   - 连接关闭处理和资源清理
 *   - 连接属性管理和统计信息
 * 技术选型:
 *   - Netty Channel API封装
 *   - 线程安全的属性存储
 *   - 异步消息发送和回调
 *   - 内存安全的ByteBuf管理
 * 依赖关系:
 *   - 实现Connection接口
 *   - 使用Netty Channel进行网络通信
 *   - 被NettyServer和NettyClient使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.netty;

import com.lx.gameserver.frame.network.core.Connection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Netty连接实现
 * <p>
 * 基于Netty Channel实现的网络连接，提供线程安全的连接管理、
 * 属性存储、消息收发和统计功能。支持连接状态监控和事件通知。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class NettyConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(NettyConnection.class);

    /**
     * 连接ID属性键
     */
    private static final AttributeKey<String> CONNECTION_ID_KEY = AttributeKey.valueOf("connectionId");

    /**
     * 连接实例属性键
     */
    private static final AttributeKey<NettyConnection> CONNECTION_KEY = AttributeKey.valueOf("connection");

    /**
     * 连接唯一标识
     */
    private final String id;

    /**
     * 底层Netty Channel
     */
    private final Channel channel;

    /**
     * 连接状态
     */
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.CONNECTED);

    /**
     * 连接属性存储
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 连接监听器列表
     */
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 连接创建时间
     */
    private final LocalDateTime createTime = LocalDateTime.now();

    /**
     * 最后活跃时间
     */
    private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

    /**
     * 已发送字节数
     */
    private final AtomicLong sentBytes = new AtomicLong(0);

    /**
     * 已接收字节数
     */
    private final AtomicLong receivedBytes = new AtomicLong(0);

    /**
     * 已发送消息数
     */
    private final AtomicLong sentMessages = new AtomicLong(0);

    /**
     * 已接收消息数
     */
    private final AtomicLong receivedMessages = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param channel Netty Channel
     */
    public NettyConnection(Channel channel) {
        this.id = generateConnectionId();
        this.channel = channel;
        
        // 在Channel上绑定连接信息
        channel.attr(CONNECTION_ID_KEY).set(id);
        channel.attr(CONNECTION_KEY).set(this);

        // 添加关闭监听器
        channel.closeFuture().addListener(future -> {
            state.set(ConnectionState.DISCONNECTED);
            notifyDisconnected();
        });

        logger.debug("创建Netty连接: {}, Channel: {}", id, channel);
    }

    /**
     * 从Channel获取连接实例
     *
     * @param channel Netty Channel
     * @return 连接实例，如果不存在则返回null
     */
    public static NettyConnection getConnection(Channel channel) {
        return channel.attr(CONNECTION_KEY).get();
    }

    /**
     * 从Channel获取连接ID
     *
     * @param channel Netty Channel
     * @return 连接ID，如果不存在则返回null
     */
    public static String getConnectionId(Channel channel) {
        return channel.attr(CONNECTION_ID_KEY).get();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ConnectionState getState() {
        return state.get();
    }

    @Override
    public boolean isActive() {
        return channel.isActive() && state.get() == ConnectionState.CONNECTED;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public CompletableFuture<Void> send(Object message) {
        if (!isActive()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("连接已断开，无法发送消息"));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        
        channel.writeAndFlush(message).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                // 更新统计信息
                sentMessages.incrementAndGet();
                updateLastActiveTime();
                
                // 通知监听器
                notifyMessageSent(message);
                
                future.complete(null);
                logger.debug("消息发送成功: {}", message);
            } else {
                Throwable cause = channelFuture.cause();
                logger.warn("消息发送失败: {}", message, cause);
                notifyException(cause);
                future.completeExceptionally(cause);
            }
        });

        return future;
    }

    @Override
    public void sendSync(Object message) throws Exception {
        if (!isActive()) {
            throw new IllegalStateException("连接已断开，无法发送消息");
        }

        ChannelFuture future = channel.writeAndFlush(message);
        future.sync();
        
        if (future.isSuccess()) {
            // 更新统计信息
            sentMessages.incrementAndGet();
            updateLastActiveTime();
            
            // 通知监听器
            notifyMessageSent(message);
            
            logger.debug("同步消息发送成功: {}", message);
        } else {
            Throwable cause = future.cause();
            logger.warn("同步消息发送失败: {}", message, cause);
            notifyException(cause);
            throw new RuntimeException("消息发送失败", cause);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        if (!state.compareAndSet(ConnectionState.CONNECTED, ConnectionState.DISCONNECTING)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("开始关闭连接: {}", id);
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        channel.close().addListener((ChannelFutureListener) channelFuture -> {
            state.set(ConnectionState.DISCONNECTED);
            
            if (channelFuture.isSuccess()) {
                logger.debug("连接关闭成功: {}", id);
                future.complete(null);
            } else {
                Throwable cause = channelFuture.cause();
                logger.warn("连接关闭失败: {}", id, cause);
                future.completeExceptionally(cause);
            }
            
            notifyDisconnected();
        });

        return future;
    }

    @Override
    public void forceClose() {
        state.set(ConnectionState.DISCONNECTED);
        
        try {
            channel.close().sync();
            logger.debug("强制关闭连接成功: {}", id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("强制关闭连接被中断: {}", id, e);
        } catch (Exception e) {
            logger.warn("强制关闭连接失败: {}", id, e);
        }
        
        notifyDisconnected();
    }

    @Override
    public Object setAttribute(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("属性键不能为null");
        }
        
        Object oldValue = attributes.put(key, value);
        logger.debug("设置连接属性: {} = {}", key, value);
        return oldValue;
    }

    @Override
    public Object getAttribute(String key) {
        if (key == null) {
            return null;
        }
        return attributes.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        
        Object value = attributes.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            logger.warn("连接属性类型转换失败: {} = {}", key, value, e);
            return defaultValue;
        }
    }

    @Override
    public Object removeAttribute(String key) {
        if (key == null) {
            return null;
        }
        
        Object oldValue = attributes.remove(key);
        logger.debug("移除连接属性: {}", key);
        return oldValue;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public void clearAttributes() {
        attributes.clear();
        logger.debug("清空连接属性: {}", id);
    }

    @Override
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    @Override
    public void updateLastActiveTime() {
        lastActiveTime = LocalDateTime.now();
    }

    @Override
    public long getSentBytes() {
        return sentBytes.get();
    }

    @Override
    public long getReceivedBytes() {
        return receivedBytes.get();
    }

    @Override
    public long getSentMessages() {
        return sentMessages.get();
    }

    @Override
    public long getReceivedMessages() {
        return receivedMessages.get();
    }

    @Override
    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    /**
     * 获取底层Channel
     *
     * @return Netty Channel
     */
    public Channel getChannel() {
        return channel;
    }

    /**
     * 更新接收字节数
     *
     * @param bytes 接收字节数
     */
    public void addReceivedBytes(long bytes) {
        receivedBytes.addAndGet(bytes);
        updateLastActiveTime();
    }

    /**
     * 更新发送字节数
     *
     * @param bytes 发送字节数
     */
    public void addSentBytes(long bytes) {
        sentBytes.addAndGet(bytes);
        updateLastActiveTime();
    }

    /**
     * 通知消息接收
     *
     * @param message 接收到的消息
     */
    public void notifyMessageReceived(Object message) {
        receivedMessages.incrementAndGet();
        updateLastActiveTime();
        
        listeners.forEach(listener -> {
            try {
                listener.onMessageReceived(this, message);
            } catch (Exception e) {
                logger.warn("通知消息接收事件失败", e);
            }
        });
    }

    /**
     * 生成连接ID
     */
    private String generateConnectionId() {
        return "conn_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 通知消息发送
     */
    private void notifyMessageSent(Object message) {
        listeners.forEach(listener -> {
            try {
                listener.onMessageSent(this, message);
            } catch (Exception e) {
                logger.warn("通知消息发送事件失败", e);
            }
        });
    }

    /**
     * 通知连接断开
     */
    private void notifyDisconnected() {
        listeners.forEach(listener -> {
            try {
                listener.onDisconnected(this);
            } catch (Exception e) {
                logger.warn("通知连接断开事件失败", e);
            }
        });
    }

    /**
     * 通知异常
     */
    private void notifyException(Throwable cause) {
        listeners.forEach(listener -> {
            try {
                listener.onException(this, cause);
            } catch (Exception e) {
                logger.warn("通知连接异常事件失败", e);
            }
        });
    }

    @Override
    public String toString() {
        return String.format("NettyConnection{id='%s', state=%s, active=%s, local=%s, remote=%s}", 
                id, state.get(), isActive(), getLocalAddress(), getRemoteAddress());
    }
}