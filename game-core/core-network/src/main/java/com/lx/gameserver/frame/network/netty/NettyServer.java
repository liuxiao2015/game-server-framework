/*
 * 文件名: NettyServer.java
 * 用途: Netty服务器实现
 * 实现内容:
 *   - 基于Netty的高性能网络服务器实现
 *   - EventLoopGroup配置（支持Epoll/KQueue优化）
 *   - Channel初始化和Pipeline配置
 *   - Boss/Worker线程池优化
 *   - 内存池配置和资源管理
 *   - 连接管理和事件处理
 * 技术选型:
 *   - Netty 4.x NIO框架
 *   - Epoll/KQueue原生传输优化
 *   - PooledByteBufAllocator内存池
 *   - Virtual Threads兼容设计
 * 依赖关系:
 *   - 继承NetworkServer抽象基类
 *   - 使用NettyConnection管理连接
 *   - 使用NettyChannelInitializer初始化Channel
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.netty;

import com.lx.gameserver.frame.network.core.Connection;
import com.lx.gameserver.frame.network.core.NetworkServer;
import com.lx.gameserver.frame.network.core.Protocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Netty服务器实现
 * <p>
 * 基于Netty框架的高性能网络服务器，支持TCP/UDP/WebSocket协议，
 * 提供连接管理、事件处理和资源优化功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class NettyServer extends NetworkServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private final ServerBootstrap bootstrap;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final NettyChannelInitializer channelInitializer;
    private final ConcurrentHashMap<String, NettyConnection> connections = new ConcurrentHashMap<>();
    
    private volatile Channel serverChannel;

    /**
     * 构造函数
     *
     * @param config 服务器配置
     */
    public NettyServer(ServerConfig config) {
        super(config);
        
        // 创建EventLoopGroup
        this.bossGroup = createEventLoopGroup(getBossThreads(), "NettyServer-Boss", true);
        this.workerGroup = createEventLoopGroup(getWorkerThreads(), "NettyServer-Worker", false);
        
        // 创建Channel初始化器
        this.channelInitializer = new NettyChannelInitializer(config, this::onChannelActive, this::onChannelInactive);
        
        // 配置ServerBootstrap
        this.bootstrap = new ServerBootstrap();
        configureBootstrap();
        
        logger.info("Netty服务器创建完成，Boss线程: {}, Worker线程: {}, 传输类型: {}", 
                getBossThreads(),
                getWorkerThreads(),
                bossGroup instanceof EpollEventLoopGroup ? "Epoll" : 
                bossGroup instanceof KQueueEventLoopGroup ? "KQueue" : "NIO");
    }

    /**
     * 创建EventLoopGroup
     */
    private EventLoopGroup createEventLoopGroup(int threads, String namePrefix, boolean isBoss) {
        // 检查是否支持原生传输
        if (isLinuxEpollAvailable() && isUseEpoll()) {
            logger.debug("使用Epoll传输，线程数: {}", threads);
            return new EpollEventLoopGroup(threads, new DefaultThreadFactory(namePrefix, true));
        } else if (isMacOSKQueueAvailable() && isUseKQueue()) {
            logger.debug("使用KQueue传输，线程数: {}", threads);
            return new KQueueEventLoopGroup(threads, new DefaultThreadFactory(namePrefix, true));
        } else {
            logger.debug("使用NIO传输，线程数: {}", threads);
            return new NioEventLoopGroup(threads, new DefaultThreadFactory(namePrefix, true));
        }
    }

    /**
     * 检查是否支持Linux Epoll
     */
    private boolean isLinuxEpollAvailable() {
        try {
            Class.forName("io.netty.channel.epoll.Epoll");
            return io.netty.channel.epoll.Epoll.isAvailable();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检查是否支持macOS KQueue
     */
    private boolean isMacOSKQueueAvailable() {
        try {
            Class.forName("io.netty.channel.kqueue.KQueue");
            return io.netty.channel.kqueue.KQueue.isAvailable();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 配置ServerBootstrap
     */
    private void configureBootstrap() {
        bootstrap.group(bossGroup, workerGroup)
                .channelFactory(() -> {
                    if (bossGroup instanceof EpollEventLoopGroup) {
                        return new EpollServerSocketChannel();
                    } else if (bossGroup instanceof KQueueEventLoopGroup) {
                        return new KQueueServerSocketChannel();
                    } else {
                        return new NioServerSocketChannel();
                    }
                })
                .childHandler(channelInitializer)
                .option(ChannelOption.SO_BACKLOG, getConfig().getBacklog())
                .option(ChannelOption.SO_REUSEADDR, getConfig().isReuseAddress())
                .childOption(ChannelOption.TCP_NODELAY, getConfig().isTcpNoDelay())
                .childOption(ChannelOption.SO_KEEPALIVE, getConfig().isKeepAlive())
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                    new WriteBufferWaterMark(getWriteBufferLowWaterMark(), 
                                           getWriteBufferHighWaterMark()));

        // 配置内存分配器
        if (isUseDirectMemory()) {
            bootstrap.childOption(ChannelOption.ALLOCATOR, io.netty.buffer.PooledByteBufAllocator.DEFAULT);
        }

        logger.debug("ServerBootstrap配置完成");
    }

    @Override
    protected CompletableFuture<Void> doStart() {
        SocketAddress bindAddress = new InetSocketAddress(getConfig().getBindAddress(), getConfig().getPort());
        return doStart(bindAddress);
    }

    /**
     * 执行实际的启动逻辑
     */
    private CompletableFuture<Void> doStart(SocketAddress bindAddress) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        bootstrap.bind(bindAddress).addListener((ChannelFuture bindFuture) -> {
            if (bindFuture.isSuccess()) {
                serverChannel = bindFuture.channel();
                logger.info("Netty服务器启动成功，绑定地址: {}", bindAddress);
                future.complete(null);
                
                // 注册关闭监听器
                serverChannel.closeFuture().addListener((ChannelFuture closeFuture) -> {
                    logger.info("Netty服务器Channel关闭");
                });
            } else {
                logger.error("Netty服务器启动失败，绑定地址: " + bindAddress, bindFuture.cause());
                future.completeExceptionally(bindFuture.cause());
            }
        });
        
        return future;
    }

    @Override
    protected CompletableFuture<Void> doStop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        logger.info("开始关闭Netty服务器");
        
        // 关闭服务器Channel
        if (serverChannel != null && serverChannel.isActive()) {
            serverChannel.close().addListener((ChannelFuture closeFuture) -> {
                logger.debug("服务器Channel关闭完成");
                shutdownEventLoopGroups(future);
            });
        } else {
            shutdownEventLoopGroups(future);
        }
        
        return future;
    }

    /**
     * 关闭EventLoopGroup
     */
    private void shutdownEventLoopGroups(CompletableFuture<Void> future) {
        // 优雅关闭Worker组
        workerGroup.shutdownGracefully(100, 5000, TimeUnit.MILLISECONDS)
                .addListener(workerFuture -> {
                    logger.debug("Worker EventLoopGroup关闭完成");
                    
                    // 优雅关闭Boss组
                    bossGroup.shutdownGracefully(100, 1000, TimeUnit.MILLISECONDS)
                            .addListener(bossFuture -> {
                                logger.info("Netty服务器关闭完成");
                                future.complete(null);
                            });
                });
    }

    /**
     * Channel激活回调
     */
    private void onChannelActive(Channel channel) {
        NettyConnection connection = new NettyConnection(channel);
        connections.put(connection.getId(), connection);
        logger.debug("新连接建立: {}", connection.getId());
        
        // 通知监听器
        notifyConnectionAccepted(connection);
    }

    /**
     * Channel非激活回调
     */
    private void onChannelInactive(Channel channel) {
        NettyConnection connection = getConnectionByChannel(channel);
        if (connection != null) {
            connections.remove(connection.getId());
            logger.debug("连接关闭: {}", connection.getId());
            
            // 通知监听器
            notifyConnectionClosed(connection);
        }
    }

    /**
     * 根据Channel获取连接
     */
    private NettyConnection getConnectionByChannel(Channel channel) {
        return connections.values().stream()
                .filter(conn -> conn.getChannel() == channel)
                .findFirst()
                .orElse(null);
    }

    // ===== 实现抽象方法 =====

    @Override
    public SocketAddress getBindAddress() {
        if (serverChannel != null) {
            return serverChannel.localAddress();
        }
        return new InetSocketAddress(getConfig().getBindAddress(), getConfig().getPort());
    }

    @Override
    public int getConnectionCount() {
        return connections.size();
    }

    @Override
    public List<Connection> getConnections() {
        return Collections.unmodifiableList(new CopyOnWriteArrayList<>(connections.values()));
    }

    @Override
    public Connection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    @Override
    public CompletableFuture<Void> broadcast(Object message) {
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
        
        for (NettyConnection connection : connections.values()) {
            if (connection.isActive()) {
                futures.add(connection.send(message));
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    // ===== 配置辅助方法 =====

    /**
     * 获取Boss线程数
     */
    private int getBossThreads() {
        return 2; // 默认值，应该从配置中获取
    }

    /**
     * 获取Worker线程数
     */
    private int getWorkerThreads() {
        return 0; // 0表示自动，应该从配置中获取
    }

    /**
     * 是否使用Epoll
     */
    private boolean isUseEpoll() {
        return true; // 默认启用，应该从配置中获取
    }

    /**
     * 是否使用KQueue
     */
    private boolean isUseKQueue() {
        return true; // 默认启用，应该从配置中获取
    }

    /**
     * 获取写缓冲区低水位
     */
    private int getWriteBufferLowWaterMark() {
        return 32 * 1024; // 32KB，应该从配置中获取
    }

    /**
     * 获取写缓冲区高水位
     */
    private int getWriteBufferHighWaterMark() {
        return 64 * 1024; // 64KB，应该从配置中获取
    }

    /**
     * 是否使用直接内存
     */
    private boolean isUseDirectMemory() {
        return true; // 默认启用，应该从配置中获取
    }

    /**
     * 获取服务器Channel
     */
    public Channel getServerChannel() {
        return serverChannel;
    }

    /**
     * 获取Boss EventLoopGroup
     */
    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    /**
     * 获取Worker EventLoopGroup
     */
    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }
}