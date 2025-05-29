/*
 * 文件名: NetworkServer.java
 * 用途: 网络服务器抽象基类
 * 实现内容:
 *   - 网络服务器的基础框架和生命周期管理
 *   - 支持TCP/UDP/WebSocket多协议
 *   - 端口绑定和连接监听
 *   - 优雅启动和关闭机制
 *   - 连接数限制和管控
 *   - Virtual Threads集成支持
 * 技术选型:
 *   - 抽象基类设计，支持多种传输层实现
 *   - 状态机模式管理服务器生命周期
 *   - 观察者模式支持事件通知
 *   - 兼容Java 17，为Java 21 Virtual Threads做准备
 * 依赖关系:
 *   - 被NettyServer等具体实现继承
 *   - 使用Connection接口管理连接
 *   - 使用Protocol接口处理协议
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网络服务器抽象基类
 * <p>
 * 提供网络服务器的基础框架，支持多种传输协议和连接管理。
 * 实现了标准的生命周期管理、事件通知和配置管理功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class NetworkServer {

    private static final Logger logger = LoggerFactory.getLogger(NetworkServer.class);

    /**
     * 服务器状态枚举
     */
    public enum ServerState {
        /** 未启动 */
        STOPPED("未启动"),
        
        /** 启动中 */
        STARTING("启动中"),
        
        /** 运行中 */
        RUNNING("运行中"),
        
        /** 停止中 */
        STOPPING("停止中");

        private final String description;

        ServerState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 服务器配置
     */
    protected final ServerConfig config;

    /**
     * 服务器状态
     */
    protected final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.STOPPED);

    /**
     * 服务器监听器列表
     */
    protected final List<ServerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 构造函数
     *
     * @param config 服务器配置
     */
    protected NetworkServer(ServerConfig config) {
        this.config = config;
    }

    /**
     * 启动服务器
     *
     * @return 启动结果的Future对象
     */
    public final CompletableFuture<Void> start() {
        if (!state.compareAndSet(ServerState.STOPPED, ServerState.STARTING)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("服务器已启动或正在启动，当前状态: " + state.get()));
        }

        logger.info("开始启动网络服务器，端口: {}, 协议: {}", 
                   config.getPort(), config.getProtocol().getProtocolType());

        try {
            // 通知监听器启动开始
            notifyServerStarting();

            // 执行具体的启动逻辑
            return doStart()
                .thenRun(() -> {
                    state.set(ServerState.RUNNING);
                    logger.info("网络服务器启动成功，监听地址: {}", getBindAddress());
                    notifyServerStarted();
                })
                .exceptionally(throwable -> {
                    state.set(ServerState.STOPPED);
                    logger.error("网络服务器启动失败", throwable);
                    notifyServerStartFailed(throwable);
                    throw new RuntimeException(throwable);
                });
        } catch (Exception e) {
            state.set(ServerState.STOPPED);
            logger.error("网络服务器启动失败", e);
            notifyServerStartFailed(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 停止服务器
     *
     * @return 停止结果的Future对象
     */
    public final CompletableFuture<Void> stop() {
        if (!state.compareAndSet(ServerState.RUNNING, ServerState.STOPPING)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("服务器未运行或正在停止，当前状态: " + state.get()));
        }

        logger.info("开始停止网络服务器");

        try {
            // 通知监听器停止开始
            notifyServerStopping();

            // 执行具体的停止逻辑
            return doStop()
                .thenRun(() -> {
                    state.set(ServerState.STOPPED);
                    logger.info("网络服务器停止成功");
                    notifyServerStopped();
                })
                .exceptionally(throwable -> {
                    logger.error("网络服务器停止失败", throwable);
                    notifyServerStopFailed(throwable);
                    throw new RuntimeException(throwable);
                });
        } catch (Exception e) {
            logger.error("网络服务器停止失败", e);
            notifyServerStopFailed(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 重启服务器
     *
     * @return 重启结果的Future对象
     */
    public final CompletableFuture<Void> restart() {
        logger.info("重启网络服务器");
        return stop().thenCompose(v -> start());
    }

    /**
     * 获取服务器状态
     *
     * @return 当前服务器状态
     */
    public final ServerState getState() {
        return state.get();
    }

    /**
     * 检查服务器是否正在运行
     *
     * @return true表示正在运行
     */
    public final boolean isRunning() {
        return state.get() == ServerState.RUNNING;
    }

    /**
     * 获取服务器配置
     *
     * @return 服务器配置
     */
    public final ServerConfig getConfig() {
        return config;
    }

    /**
     * 获取绑定地址
     *
     * @return 服务器绑定的socket地址
     */
    public abstract SocketAddress getBindAddress();

    /**
     * 获取当前连接数
     *
     * @return 当前活跃连接数
     */
    public abstract int getConnectionCount();

    /**
     * 获取所有连接
     *
     * @return 连接列表的只读视图
     */
    public abstract List<Connection> getConnections();

    /**
     * 根据ID查找连接
     *
     * @param connectionId 连接ID
     * @return 连接对象，如果不存在则返回null
     */
    public abstract Connection getConnection(String connectionId);

    /**
     * 广播消息给所有连接
     *
     * @param message 要广播的消息
     * @return 广播结果的Future对象
     */
    public abstract CompletableFuture<Void> broadcast(Object message);

    /**
     * 添加服务器监听器
     *
     * @param listener 服务器监听器
     */
    public final void addServerListener(ServerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除服务器监听器
     *
     * @param listener 服务器监听器
     */
    public final void removeServerListener(ServerListener listener) {
        listeners.remove(listener);
    }

    /**
     * 执行具体的启动逻辑
     *
     * @return 启动结果的Future对象
     */
    protected abstract CompletableFuture<Void> doStart();

    /**
     * 执行具体的停止逻辑
     *
     * @return 停止结果的Future对象
     */
    protected abstract CompletableFuture<Void> doStop();

    // ===== 事件通知方法 =====

    protected void notifyServerStarting() {
        listeners.forEach(listener -> {
            try {
                listener.onServerStarting(this);
            } catch (Exception e) {
                logger.warn("通知服务器启动开始事件失败", e);
            }
        });
    }

    protected void notifyServerStarted() {
        listeners.forEach(listener -> {
            try {
                listener.onServerStarted(this);
            } catch (Exception e) {
                logger.warn("通知服务器启动成功事件失败", e);
            }
        });
    }

    protected void notifyServerStartFailed(Throwable cause) {
        listeners.forEach(listener -> {
            try {
                listener.onServerStartFailed(this, cause);
            } catch (Exception e) {
                logger.warn("通知服务器启动失败事件失败", e);
            }
        });
    }

    protected void notifyServerStopping() {
        listeners.forEach(listener -> {
            try {
                listener.onServerStopping(this);
            } catch (Exception e) {
                logger.warn("通知服务器停止开始事件失败", e);
            }
        });
    }

    protected void notifyServerStopped() {
        listeners.forEach(listener -> {
            try {
                listener.onServerStopped(this);
            } catch (Exception e) {
                logger.warn("通知服务器停止成功事件失败", e);
            }
        });
    }

    protected void notifyServerStopFailed(Throwable cause) {
        listeners.forEach(listener -> {
            try {
                listener.onServerStopFailed(this, cause);
            } catch (Exception e) {
                logger.warn("通知服务器停止失败事件失败", e);
            }
        });
    }

    protected void notifyConnectionAccepted(Connection connection) {
        listeners.forEach(listener -> {
            try {
                listener.onConnectionAccepted(this, connection);
            } catch (Exception e) {
                logger.warn("通知连接接受事件失败", e);
            }
        });
    }

    protected void notifyConnectionClosed(Connection connection) {
        listeners.forEach(listener -> {
            try {
                listener.onConnectionClosed(this, connection);
            } catch (Exception e) {
                logger.warn("通知连接关闭事件失败", e);
            }
        });
    }

    /**
     * 服务器监听器接口
     */
    public interface ServerListener {
        
        /**
         * 服务器启动开始事件
         *
         * @param server 服务器实例
         */
        default void onServerStarting(NetworkServer server) {}

        /**
         * 服务器启动成功事件
         *
         * @param server 服务器实例
         */
        default void onServerStarted(NetworkServer server) {}

        /**
         * 服务器启动失败事件
         *
         * @param server 服务器实例
         * @param cause  失败原因
         */
        default void onServerStartFailed(NetworkServer server, Throwable cause) {}

        /**
         * 服务器停止开始事件
         *
         * @param server 服务器实例
         */
        default void onServerStopping(NetworkServer server) {}

        /**
         * 服务器停止成功事件
         *
         * @param server 服务器实例
         */
        default void onServerStopped(NetworkServer server) {}

        /**
         * 服务器停止失败事件
         *
         * @param server 服务器实例
         * @param cause  失败原因
         */
        default void onServerStopFailed(NetworkServer server, Throwable cause) {}

        /**
         * 连接接受事件
         *
         * @param server     服务器实例
         * @param connection 新接受的连接
         */
        default void onConnectionAccepted(NetworkServer server, Connection connection) {}

        /**
         * 连接关闭事件
         *
         * @param server     服务器实例
         * @param connection 关闭的连接
         */
        default void onConnectionClosed(NetworkServer server, Connection connection) {}
    }

    /**
     * 服务器配置接口
     */
    public interface ServerConfig {
        
        /**
         * 获取服务器端口
         *
         * @return 服务器端口
         */
        int getPort();

        /**
         * 获取绑定地址
         *
         * @return 绑定地址，null表示绑定所有接口
         */
        String getBindAddress();

        /**
         * 获取协议配置
         *
         * @return 协议配置
         */
        Protocol getProtocol();

        /**
         * 获取最大连接数
         *
         * @return 最大连接数
         */
        int getMaxConnections();

        /**
         * 获取连接超时时间
         *
         * @return 连接超时时间（毫秒）
         */
        long getConnectionTimeout();

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

        /**
         * 获取backlog大小
         *
         * @return backlog大小
         */
        int getBacklog();

        /**
         * 是否复用地址
         *
         * @return true表示复用
         */
        boolean isReuseAddress();
    }
}