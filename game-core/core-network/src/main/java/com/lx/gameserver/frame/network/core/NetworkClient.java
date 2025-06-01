/*
 * 文件名: NetworkClient.java
 * 用途: 网络客户端抽象基类
 * 实现内容:
 *   - 网络客户端的基础框架和连接管理
 *   - 连接池管理和复用
 *   - 自动重连机制和策略配置
 *   - 心跳保活机制
 *   - 异步连接和请求超时控制
 *   - 连接状态监控和事件通知
 * 技术选型:
 *   - 抽象基类设计，支持多种传输层实现
 *   - 状态机模式管理客户端生命周期
 *   - 观察者模式支持事件通知
 *   - 异步编程模型，支持高并发
 * 依赖关系:
 *   - 被NettyClient等具体实现继承
 *   - 使用Connection接口管理连接
 *   - 使用Protocol接口处理协议
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网络客户端抽象基类
 * <p>
 * 提供网络客户端的基础框架，支持连接池管理、自动重连、
 * 心跳保活等功能。实现了标准的生命周期管理和事件通知。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class NetworkClient {

    private static final Logger logger = LoggerFactory.getLogger(NetworkClient.class);

    /**
     * 客户端状态枚举
     */
    public enum ClientState {
        /** 已断开 */
        DISCONNECTED("已断开"),
        
        /** 连接中 */
        CONNECTING("连接中"),
        
        /** 已连接 */
        CONNECTED("已连接"),
        
        /** 重连中 */
        RECONNECTING("重连中"),
        
        /** 断开中 */
        DISCONNECTING("断开中");

        private final String description;

        ClientState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 重连策略枚举
     */
    public enum ReconnectStrategy {
        /** 不重连 */
        NONE("不重连"),
        
        /** 固定间隔重连 */
        FIXED_INTERVAL("固定间隔重连"),
        
        /** 指数退避重连 */
        EXPONENTIAL_BACKOFF("指数退避重连"),
        
        /** 线性退避重连 */
        LINEAR_BACKOFF("线性退避重连");

        private final String description;

        ReconnectStrategy(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 客户端配置
     */
    protected final ClientConfig config;

    /**
     * 客户端状态
     */
    protected final AtomicReference<ClientState> state = new AtomicReference<>(ClientState.DISCONNECTED);

    /**
     * 当前连接
     */
    protected volatile Connection currentConnection;

    /**
     * 客户端监听器列表
     */
    protected final List<ClientListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 重连计数器
     */
    protected final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    /**
     * 重连任务
     */
    protected volatile ScheduledFuture<?> reconnectTask;

    /**
     * 心跳任务
     */
    protected volatile ScheduledFuture<?> heartbeatTask;

    /**
     * 是否手动断开
     */
    protected final AtomicBoolean manualDisconnect = new AtomicBoolean(false);

    /**
     * 构造函数
     *
     * @param config 客户端配置
     */
    protected NetworkClient(ClientConfig config) {
        this.config = config;
    }

    /**
     * 连接服务器
     *
     * @return 连接结果的Future对象
     */
    public final CompletableFuture<Connection> connect() {
        if (!state.compareAndSet(ClientState.DISCONNECTED, ClientState.CONNECTING)) {
            ClientState currentState = state.get();
            if (currentState == ClientState.CONNECTED) {
                return CompletableFuture.completedFuture(currentConnection);
            }
            return CompletableFuture.failedFuture(
                new IllegalStateException("客户端正在连接或已连接，当前状态: " + currentState));
        }

        logger.info("开始连接服务器，地址: {}", config.getServerAddress());
        manualDisconnect.set(false);
        reconnectAttempts.set(0);

        // 通知监听器连接开始
        notifyConnecting();

        return doConnect()
            .thenApply(connection -> {
                currentConnection = connection;
                state.set(ClientState.CONNECTED);
                logger.info("连接服务器成功，地址: {}", config.getServerAddress());
                
                // 启动心跳
                startHeartbeat();
                
                // 通知监听器连接成功
                notifyConnected(connection);
                
                return connection;
            })
            .exceptionally(throwable -> {
                state.set(ClientState.DISCONNECTED);
                logger.error("连接服务器失败", throwable);
                notifyConnectFailed(throwable);
                
                // 根据配置决定是否重连
                if (shouldReconnect()) {
                    scheduleReconnect();
                }
                
                throw new RuntimeException(throwable);
            });
    }

    /**
     * 断开连接
     *
     * @return 断开结果的Future对象
     */
    public final CompletableFuture<Void> disconnect() {
        manualDisconnect.set(true);
        
        if (!state.compareAndSet(ClientState.CONNECTED, ClientState.DISCONNECTING)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("开始断开连接");

        // 停止心跳和重连任务
        stopHeartbeat();
        stopReconnectTask();

        // 通知监听器断开开始
        notifyDisconnecting();

        return doDisconnect()
            .thenRun(() -> {
                currentConnection = null;
                state.set(ClientState.DISCONNECTED);
                logger.info("断开连接成功");
                notifyDisconnected();
            })
            .exceptionally(throwable -> {
                logger.error("断开连接失败", throwable);
                notifyDisconnectFailed(throwable);
                throw new RuntimeException(throwable);
            });
    }

    /**
     * 重连服务器
     *
     * @return 重连结果的Future对象
     */
    public final CompletableFuture<Connection> reconnect() {
        logger.info("手动重连服务器");
        return disconnect().thenCompose(v -> connect());
    }

    /**
     * 发送消息
     *
     * @param message 要发送的消息
     * @return 发送结果的Future对象
     */
    public final CompletableFuture<Void> send(Object message) {
        Connection connection = currentConnection;
        if (connection == null || !connection.isActive()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("客户端未连接"));
        }
        return connection.send(message);
    }

    /**
     * 发送消息并等待响应
     *
     * @param message 要发送的消息
     * @param timeout 超时时间
     * @param <T>     响应类型
     * @return 响应结果的Future对象
     */
    public abstract <T> CompletableFuture<T> sendAndReceive(Object message, Duration timeout);

    /**
     * 获取客户端状态
     *
     * @return 当前客户端状态
     */
    public final ClientState getState() {
        return state.get();
    }

    /**
     * 检查是否已连接
     *
     * @return true表示已连接
     */
    public final boolean isConnected() {
        return state.get() == ClientState.CONNECTED && 
               currentConnection != null && 
               currentConnection.isActive();
    }

    /**
     * 获取当前连接
     *
     * @return 当前连接，如果未连接则返回null
     */
    public final Connection getConnection() {
        return currentConnection;
    }

    /**
     * 获取客户端配置
     *
     * @return 客户端配置
     */
    public final ClientConfig getConfig() {
        return config;
    }

    /**
     * 添加客户端监听器
     *
     * @param listener 客户端监听器
     */
    public final void addClientListener(ClientListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除客户端监听器
     *
     * @param listener 客户端监听器
     */
    public final void removeClientListener(ClientListener listener) {
        listeners.remove(listener);
    }

    /**
     * 执行具体的连接逻辑
     *
     * @return 连接结果的Future对象
     */
    protected abstract CompletableFuture<Connection> doConnect();

    /**
     * 执行具体的断开逻辑
     *
     * @return 断开结果的Future对象
     */
    protected abstract CompletableFuture<Void> doDisconnect();

    /**
     * 获取调度器
     *
     * @return 调度器
     */
    protected abstract ScheduledExecutorService getScheduler();

    /**
     * 判断是否应该重连
     */
    private boolean shouldReconnect() {
        return !manualDisconnect.get() && 
               config.getReconnectStrategy() != ReconnectStrategy.NONE &&
               reconnectAttempts.get() < config.getMaxReconnectAttempts();
    }

    /**
     * 安排重连任务
     */
    private void scheduleReconnect() {
        if (reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }

        long delay = calculateReconnectDelay();
        int attempt = reconnectAttempts.incrementAndGet();
        
        logger.info("安排第{}次重连，延迟{}毫秒", attempt, delay);
        
        state.set(ClientState.RECONNECTING);
        notifyReconnecting(attempt);

        reconnectTask = getScheduler().schedule(() -> {
            try {
                doConnect()
                    .thenAccept(connection -> {
                        currentConnection = connection;
                        state.set(ClientState.CONNECTED);
                        reconnectAttempts.set(0);
                        logger.info("重连成功，重置重连计数器");
                        
                        // 启动心跳
                        startHeartbeat();
                        
                        // 通知监听器重连成功
                        notifyReconnected(connection);
                    })
                    .exceptionally(throwable -> {
                        logger.warn("第{}次重连失败", attempt, throwable);
                        notifyReconnectFailed(attempt, throwable);
                        
                        // 继续重连
                        if (shouldReconnect()) {
                            scheduleReconnect();
                        } else {
                            state.set(ClientState.DISCONNECTED);
                            logger.warn("达到最大重连次数，停止重连");
                        }
                        return null;
                    });
            } catch (Exception e) {
                logger.error("重连过程发生异常", e);
                if (shouldReconnect()) {
                    scheduleReconnect();
                } else {
                    state.set(ClientState.DISCONNECTED);
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 计算重连延迟时间
     */
    private long calculateReconnectDelay() {
        ReconnectStrategy strategy = config.getReconnectStrategy();
        long baseDelay = config.getReconnectInterval().toMillis();
        int attempts = reconnectAttempts.get();

        return switch (strategy) {
            case FIXED_INTERVAL -> baseDelay;
            case EXPONENTIAL_BACKOFF -> Math.min(baseDelay * (1L << attempts), config.getMaxReconnectInterval().toMillis());
            case LINEAR_BACKOFF -> Math.min(baseDelay * (attempts + 1), config.getMaxReconnectInterval().toMillis());
            default -> baseDelay;
        };
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        if (!config.isHeartbeatEnabled()) {
            return;
        }

        stopHeartbeat();

        long interval = config.getHeartbeatInterval().toMillis();
        heartbeatTask = getScheduler().scheduleAtFixedRate(() -> {
            try {
                if (isConnected()) {
                    sendHeartbeat();
                }
            } catch (Exception e) {
                logger.warn("发送心跳失败", e);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);

        logger.debug("启动心跳任务，间隔: {}毫秒", interval);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
            logger.debug("停止心跳任务");
        }
    }

    /**
     * 停止重连任务
     */
    private void stopReconnectTask() {
        if (reconnectTask != null && !reconnectTask.isDone()) {
            reconnectTask.cancel(false);
            reconnectTask = null;
            logger.debug("停止重连任务");
        }
    }

    /**
     * 发送心跳消息
     */
    protected abstract void sendHeartbeat();

    // ===== 事件通知方法 =====

    protected void notifyConnecting() {
        listeners.forEach(listener -> {
            try {
                listener.onConnecting(this);
            } catch (Exception e) {
                logger.warn("通知连接开始事件失败", e);
            }
        });
    }

    protected void notifyConnected(Connection connection) {
        listeners.forEach(listener -> {
            try {
                listener.onConnected(this, connection);
            } catch (Exception e) {
                logger.warn("通知连接成功事件失败", e);
            }
        });
    }

    protected void notifyConnectFailed(Throwable cause) {
        listeners.forEach(listener -> {
            try {
                listener.onConnectFailed(this, cause);
            } catch (Exception e) {
                logger.warn("通知连接失败事件失败", e);
            }
        });
    }

    protected void notifyDisconnecting() {
        listeners.forEach(listener -> {
            try {
                listener.onDisconnecting(this);
            } catch (Exception e) {
                logger.warn("通知断开开始事件失败", e);
            }
        });
    }

    protected void notifyDisconnected() {
        listeners.forEach(listener -> {
            try {
                listener.onDisconnected(this);
            } catch (Exception e) {
                logger.warn("通知断开成功事件失败", e);
            }
        });
    }

    protected void notifyDisconnectFailed(Throwable cause) {
        listeners.forEach(listener -> {
            try {
                listener.onDisconnectFailed(this, cause);
            } catch (Exception e) {
                logger.warn("通知断开失败事件失败", e);
            }
        });
    }

    protected void notifyReconnecting(int attempt) {
        listeners.forEach(listener -> {
            try {
                listener.onReconnecting(this, attempt);
            } catch (Exception e) {
                logger.warn("通知重连开始事件失败", e);
            }
        });
    }

    protected void notifyReconnected(Connection connection) {
        listeners.forEach(listener -> {
            try {
                listener.onReconnected(this, connection);
            } catch (Exception e) {
                logger.warn("通知重连成功事件失败", e);
            }
        });
    }

    protected void notifyReconnectFailed(int attempt, Throwable cause) {
        listeners.forEach(listener -> {
            try {
                listener.onReconnectFailed(this, attempt, cause);
            } catch (Exception e) {
                logger.warn("通知重连失败事件失败", e);
            }
        });
    }

    /**
     * 客户端监听器接口
     */
    public interface ClientListener {
        
        /**
         * 连接开始事件
         *
         * @param client 客户端实例
         */
        default void onConnecting(NetworkClient client) {}

        /**
         * 连接成功事件
         *
         * @param client     客户端实例
         * @param connection 连接对象
         */
        default void onConnected(NetworkClient client, Connection connection) {}

        /**
         * 连接失败事件
         *
         * @param client 客户端实例
         * @param cause  失败原因
         */
        default void onConnectFailed(NetworkClient client, Throwable cause) {}

        /**
         * 断开开始事件
         *
         * @param client 客户端实例
         */
        default void onDisconnecting(NetworkClient client) {}

        /**
         * 断开成功事件
         *
         * @param client 客户端实例
         */
        default void onDisconnected(NetworkClient client) {}

        /**
         * 断开失败事件
         *
         * @param client 客户端实例
         * @param cause  失败原因
         */
        default void onDisconnectFailed(NetworkClient client, Throwable cause) {}

        /**
         * 重连开始事件
         *
         * @param client  客户端实例
         * @param attempt 重连次数
         */
        default void onReconnecting(NetworkClient client, int attempt) {}

        /**
         * 重连成功事件
         *
         * @param client     客户端实例
         * @param connection 连接对象
         */
        default void onReconnected(NetworkClient client, Connection connection) {}

        /**
         * 重连失败事件
         *
         * @param client  客户端实例
         * @param attempt 重连次数
         * @param cause   失败原因
         */
        default void onReconnectFailed(NetworkClient client, int attempt, Throwable cause) {}
    }

    /**
     * 客户端配置接口
     */
    public interface ClientConfig {
        
        /**
         * 获取服务器地址
         *
         * @return 服务器socket地址
         */
        SocketAddress getServerAddress();

        /**
         * 获取协议配置
         *
         * @return 协议配置
         */
        Protocol getProtocol();

        /**
         * 获取连接超时时间
         *
         * @return 连接超时时间
         */
        Duration getConnectionTimeout();

        /**
         * 获取请求超时时间
         *
         * @return 请求超时时间
         */
        Duration getRequestTimeout();

        /**
         * 获取重连策略
         *
         * @return 重连策略
         */
        ReconnectStrategy getReconnectStrategy();

        /**
         * 获取重连间隔
         *
         * @return 重连间隔时间
         */
        Duration getReconnectInterval();

        /**
         * 获取最大重连间隔
         *
         * @return 最大重连间隔时间
         */
        Duration getMaxReconnectInterval();

        /**
         * 获取最大重连次数
         *
         * @return 最大重连次数
         */
        int getMaxReconnectAttempts();

        /**
         * 是否启用心跳
         *
         * @return true表示启用心跳
         */
        boolean isHeartbeatEnabled();

        /**
         * 获取心跳间隔
         *
         * @return 心跳间隔时间
         */
        Duration getHeartbeatInterval();

        /**
         * 是否启用TCP_NODELAY
         *
         * @return true表示启用
         */
        boolean isTcpNoDelay();

        /**
         * 是否启用SO_KEEPALIVE
         *
         * @return true表示启用
         */
        boolean isKeepAlive();
    }
}