/*
 * 文件名: NetworkConfig.java
 * 用途: 网络配置类
 * 实现内容:
 *   - 网络模块的统一配置管理
 *   - 服务器和客户端配置选项
 *   - 协议配置和编解码器设置
 *   - 性能调优参数配置
 *   - 安全配置和SSL设置
 *   - 配置验证和默认值管理
 * 技术选型:
 *   - 建造者模式构建配置
 *   - 不可变配置对象
 *   - 类型安全的配置项
 *   - 支持配置文件和代码配置
 * 依赖关系:
 *   - 被NetworkServer和NetworkClient使用
 *   - 与Protocol接口配合
 *   - 支持Spring Boot配置绑定
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.config;

import com.lx.gameserver.frame.network.core.NetworkClient;
import com.lx.gameserver.frame.network.core.NetworkServer;
import com.lx.gameserver.frame.network.core.Protocol;

import java.time.Duration;
import java.util.Objects;

/**
 * 网络配置类
 * <p>
 * 提供网络模块的统一配置管理，包括服务器配置、客户端配置、
 * 协议配置、性能配置和安全配置等。使用建造者模式创建不可变配置对象。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class NetworkConfig {

    // ===== 服务器配置 =====
    
    /**
     * 服务器端口
     */
    private final int serverPort;
    
    /**
     * 绑定地址
     */
    private final String bindAddress;
    
    /**
     * 协议类型
     */
    private final Protocol.ProtocolType protocolType;
    
    /**
     * backlog大小
     */
    private final int backlog;
    
    /**
     * 是否复用地址
     */
    private final boolean reuseAddress;
    
    /**
     * 是否启用TCP_NODELAY
     */
    private final boolean tcpNoDelay;
    
    /**
     * 是否启用SO_KEEPALIVE
     */
    private final boolean keepAlive;

    // ===== 性能配置 =====
    
    /**
     * Boss线程数
     */
    private final int bossThreads;
    
    /**
     * Worker线程数
     */
    private final int workerThreads;
    
    /**
     * 是否使用Epoll（Linux）
     */
    private final boolean useEpoll;
    
    /**
     * 是否使用Virtual Threads
     */
    private final boolean useVirtualThreads;
    
    /**
     * 缓冲池大小
     */
    private final int bufferPoolSize;
    
    /**
     * 是否使用直接内存
     */
    private final boolean useDirectMemory;

    // ===== 连接配置 =====
    
    /**
     * 最大连接数
     */
    private final int maxConnections;
    
    /**
     * 空闲超时时间
     */
    private final Duration idleTimeout;
    
    /**
     * 心跳间隔
     */
    private final Duration heartbeatInterval;
    
    /**
     * 写缓冲区高水位
     */
    private final int writeBufferHigh;
    
    /**
     * 写缓冲区低水位
     */
    private final int writeBufferLow;
    
    /**
     * 连接超时时间
     */
    private final Duration connectionTimeout;

    // ===== 协议配置 =====
    
    /**
     * 消息格式
     */
    private final Protocol.MessageFormat messageFormat;
    
    /**
     * 是否启用压缩
     */
    private final boolean compressionEnabled;
    
    /**
     * 压缩阈值
     */
    private final int compressionThreshold;
    
    /**
     * 最大帧长度
     */
    private final int maxFrameLength;
    
    /**
     * 是否启用校验和
     */
    private final boolean checksumEnabled;

    // ===== 安全配置 =====
    
    /**
     * 是否启用SSL
     */
    private final boolean sslEnabled;
    
    /**
     * 证书路径
     */
    private final String certPath;
    
    /**
     * 私钥路径
     */
    private final String keyPath;
    
    /**
     * 是否启用限流
     */
    private final boolean rateLimitEnabled;
    
    /**
     * 每秒最大请求数
     */
    private final int maxRequestsPerSecond;

    // ===== 客户端配置 =====
    
    /**
     * 重连策略
     */
    private final NetworkClient.ReconnectStrategy reconnectStrategy;
    
    /**
     * 重连间隔
     */
    private final Duration reconnectInterval;
    
    /**
     * 最大重连间隔
     */
    private final Duration maxReconnectInterval;
    
    /**
     * 最大重连次数
     */
    private final int maxReconnectAttempts;
    
    /**
     * 请求超时时间
     */
    private final Duration requestTimeout;

    /**
     * 私有构造函数，通过Builder创建
     */
    private NetworkConfig(Builder builder) {
        this.serverPort = builder.serverPort;
        this.bindAddress = builder.bindAddress;
        this.protocolType = builder.protocolType;
        this.backlog = builder.backlog;
        this.reuseAddress = builder.reuseAddress;
        this.tcpNoDelay = builder.tcpNoDelay;
        this.keepAlive = builder.keepAlive;
        
        this.bossThreads = builder.bossThreads;
        this.workerThreads = builder.workerThreads;
        this.useEpoll = builder.useEpoll;
        this.useVirtualThreads = builder.useVirtualThreads;
        this.bufferPoolSize = builder.bufferPoolSize;
        this.useDirectMemory = builder.useDirectMemory;
        
        this.maxConnections = builder.maxConnections;
        this.idleTimeout = builder.idleTimeout;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.writeBufferHigh = builder.writeBufferHigh;
        this.writeBufferLow = builder.writeBufferLow;
        this.connectionTimeout = builder.connectionTimeout;
        
        this.messageFormat = builder.messageFormat;
        this.compressionEnabled = builder.compressionEnabled;
        this.compressionThreshold = builder.compressionThreshold;
        this.maxFrameLength = builder.maxFrameLength;
        this.checksumEnabled = builder.checksumEnabled;
        
        this.sslEnabled = builder.sslEnabled;
        this.certPath = builder.certPath;
        this.keyPath = builder.keyPath;
        this.rateLimitEnabled = builder.rateLimitEnabled;
        this.maxRequestsPerSecond = builder.maxRequestsPerSecond;
        
        this.reconnectStrategy = builder.reconnectStrategy;
        this.reconnectInterval = builder.reconnectInterval;
        this.maxReconnectInterval = builder.maxReconnectInterval;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.requestTimeout = builder.requestTimeout;
    }

    /**
     * 创建默认配置
     *
     * @return 默认网络配置
     */
    public static NetworkConfig defaultConfig() {
        return new Builder().build();
    }

    /**
     * 创建服务器配置
     *
     * @param port 服务器端口
     * @return 服务器配置
     */
    public static NetworkConfig serverConfig(int port) {
        return new Builder().serverPort(port).build();
    }

    /**
     * 创建客户端配置
     *
     * @return 客户端配置
     */
    public static NetworkConfig clientConfig() {
        return new Builder()
            .reconnectStrategy(NetworkClient.ReconnectStrategy.EXPONENTIAL_BACKOFF)
            .maxReconnectAttempts(10)
            .build();
    }

    /**
     * 创建配置构建器
     *
     * @return 配置构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    // ===== Getter方法 =====

    public int getServerPort() { return serverPort; }
    public String getBindAddress() { return bindAddress; }
    public Protocol.ProtocolType getProtocolType() { return protocolType; }
    public int getBacklog() { return backlog; }
    public boolean isReuseAddress() { return reuseAddress; }
    public boolean isTcpNoDelay() { return tcpNoDelay; }
    public boolean isKeepAlive() { return keepAlive; }
    
    public int getBossThreads() { return bossThreads; }
    public int getWorkerThreads() { return workerThreads; }
    public boolean isUseEpoll() { return useEpoll; }
    public boolean isUseVirtualThreads() { return useVirtualThreads; }
    public int getBufferPoolSize() { return bufferPoolSize; }
    public boolean isUseDirectMemory() { return useDirectMemory; }
    
    public int getMaxConnections() { return maxConnections; }
    public Duration getIdleTimeout() { return idleTimeout; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public int getWriteBufferHigh() { return writeBufferHigh; }
    public int getWriteBufferLow() { return writeBufferLow; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    
    public Protocol.MessageFormat getMessageFormat() { return messageFormat; }
    public boolean isCompressionEnabled() { return compressionEnabled; }
    public int getCompressionThreshold() { return compressionThreshold; }
    public int getMaxFrameLength() { return maxFrameLength; }
    public boolean isChecksumEnabled() { return checksumEnabled; }
    
    public boolean isSslEnabled() { return sslEnabled; }
    public String getCertPath() { return certPath; }
    public String getKeyPath() { return keyPath; }
    public boolean isRateLimitEnabled() { return rateLimitEnabled; }
    public int getMaxRequestsPerSecond() { return maxRequestsPerSecond; }
    
    public NetworkClient.ReconnectStrategy getReconnectStrategy() { return reconnectStrategy; }
    public Duration getReconnectInterval() { return reconnectInterval; }
    public Duration getMaxReconnectInterval() { return maxReconnectInterval; }
    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public Duration getRequestTimeout() { return requestTimeout; }

    /**
     * 配置构建器
     */
    public static final class Builder {
        // 服务器配置
        private int serverPort = 8080;
        private String bindAddress = null;
        private Protocol.ProtocolType protocolType = Protocol.ProtocolType.TCP;
        private int backlog = 1024;
        private boolean reuseAddress = true;
        private boolean tcpNoDelay = true;
        private boolean keepAlive = true;
        
        // 性能配置
        private int bossThreads = 2;
        private int workerThreads = 0; // 0表示自动
        private boolean useEpoll = true;
        private boolean useVirtualThreads = true;
        private int bufferPoolSize = 8192;
        private boolean useDirectMemory = true;
        
        // 连接配置
        private int maxConnections = 100000;
        private Duration idleTimeout = Duration.ofSeconds(300);
        private Duration heartbeatInterval = Duration.ofSeconds(30);
        private int writeBufferHigh = 64 * 1024; // 64KB
        private int writeBufferLow = 32 * 1024;  // 32KB
        private Duration connectionTimeout = Duration.ofSeconds(30);
        
        // 协议配置
        private Protocol.MessageFormat messageFormat = Protocol.MessageFormat.PROTOBUF;
        private boolean compressionEnabled = true;
        private int compressionThreshold = 1024;
        private int maxFrameLength = 1024 * 1024; // 1MB
        private boolean checksumEnabled = true;
        
        // 安全配置
        private boolean sslEnabled = false;
        private String certPath = null;
        private String keyPath = null;
        private boolean rateLimitEnabled = true;
        private int maxRequestsPerSecond = 1000;
        
        // 客户端配置
        private NetworkClient.ReconnectStrategy reconnectStrategy = NetworkClient.ReconnectStrategy.NONE;
        private Duration reconnectInterval = Duration.ofSeconds(5);
        private Duration maxReconnectInterval = Duration.ofSeconds(60);
        private int maxReconnectAttempts = 5;
        private Duration requestTimeout = Duration.ofSeconds(10);

        // 服务器配置方法
        public Builder serverPort(int port) {
            this.serverPort = port;
            return this;
        }
        
        public Builder bindAddress(String address) {
            this.bindAddress = address;
            return this;
        }
        
        public Builder protocolType(Protocol.ProtocolType type) {
            this.protocolType = Objects.requireNonNull(type, "协议类型不能为null");
            return this;
        }
        
        public Builder backlog(int backlog) {
            this.backlog = Math.max(1, backlog);
            return this;
        }
        
        public Builder reuseAddress(boolean reuse) {
            this.reuseAddress = reuse;
            return this;
        }
        
        public Builder tcpNoDelay(boolean noDelay) {
            this.tcpNoDelay = noDelay;
            return this;
        }
        
        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        // 性能配置方法
        public Builder bossThreads(int threads) {
            this.bossThreads = Math.max(1, threads);
            return this;
        }
        
        public Builder workerThreads(int threads) {
            this.workerThreads = Math.max(0, threads);
            return this;
        }
        
        public Builder useEpoll(boolean use) {
            this.useEpoll = use;
            return this;
        }
        
        public Builder useVirtualThreads(boolean use) {
            this.useVirtualThreads = use;
            return this;
        }
        
        public Builder bufferPoolSize(int size) {
            this.bufferPoolSize = Math.max(1024, size);
            return this;
        }
        
        public Builder useDirectMemory(boolean use) {
            this.useDirectMemory = use;
            return this;
        }

        // 连接配置方法
        public Builder maxConnections(int max) {
            this.maxConnections = Math.max(1, max);
            return this;
        }
        
        public Builder idleTimeout(Duration timeout) {
            this.idleTimeout = Objects.requireNonNull(timeout, "空闲超时时间不能为null");
            return this;
        }
        
        public Builder heartbeatInterval(Duration interval) {
            this.heartbeatInterval = Objects.requireNonNull(interval, "心跳间隔不能为null");
            return this;
        }
        
        public Builder writeBufferHigh(int high) {
            this.writeBufferHigh = Math.max(1024, high);
            return this;
        }
        
        public Builder writeBufferLow(int low) {
            this.writeBufferLow = Math.max(512, low);
            return this;
        }
        
        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = Objects.requireNonNull(timeout, "连接超时时间不能为null");
            return this;
        }

        // 协议配置方法
        public Builder messageFormat(Protocol.MessageFormat format) {
            this.messageFormat = Objects.requireNonNull(format, "消息格式不能为null");
            return this;
        }
        
        public Builder compressionEnabled(boolean enabled) {
            this.compressionEnabled = enabled;
            return this;
        }
        
        public Builder compressionThreshold(int threshold) {
            this.compressionThreshold = Math.max(0, threshold);
            return this;
        }
        
        public Builder maxFrameLength(int length) {
            this.maxFrameLength = Math.max(1024, length);
            return this;
        }
        
        public Builder checksumEnabled(boolean enabled) {
            this.checksumEnabled = enabled;
            return this;
        }

        // 安全配置方法
        public Builder sslEnabled(boolean enabled) {
            this.sslEnabled = enabled;
            return this;
        }
        
        public Builder certPath(String path) {
            this.certPath = path;
            return this;
        }
        
        public Builder keyPath(String path) {
            this.keyPath = path;
            return this;
        }
        
        public Builder rateLimitEnabled(boolean enabled) {
            this.rateLimitEnabled = enabled;
            return this;
        }
        
        public Builder maxRequestsPerSecond(int max) {
            this.maxRequestsPerSecond = Math.max(1, max);
            return this;
        }

        // 客户端配置方法
        public Builder reconnectStrategy(NetworkClient.ReconnectStrategy strategy) {
            this.reconnectStrategy = Objects.requireNonNull(strategy, "重连策略不能为null");
            return this;
        }
        
        public Builder reconnectInterval(Duration interval) {
            this.reconnectInterval = Objects.requireNonNull(interval, "重连间隔不能为null");
            return this;
        }
        
        public Builder maxReconnectInterval(Duration interval) {
            this.maxReconnectInterval = Objects.requireNonNull(interval, "最大重连间隔不能为null");
            return this;
        }
        
        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = Math.max(0, attempts);
            return this;
        }
        
        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = Objects.requireNonNull(timeout, "请求超时时间不能为null");
            return this;
        }

        /**
         * 构建配置对象
         *
         * @return 网络配置实例
         */
        public NetworkConfig build() {
            // 配置验证
            validate();
            return new NetworkConfig(this);
        }

        /**
         * 验证配置参数
         */
        private void validate() {
            if (serverPort < 1 || serverPort > 65535) {
                throw new IllegalArgumentException("无效的服务器端口: " + serverPort);
            }
            
            if (writeBufferLow >= writeBufferHigh) {
                throw new IllegalArgumentException("写缓冲区低水位必须小于高水位");
            }
            
            if (sslEnabled && (certPath == null || keyPath == null)) {
                throw new IllegalArgumentException("启用SSL时必须提供证书和私钥路径");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("NetworkConfig{port=%d, protocol=%s, maxConnections=%d, messageFormat=%s}", 
                serverPort, protocolType, maxConnections, messageFormat);
    }
}