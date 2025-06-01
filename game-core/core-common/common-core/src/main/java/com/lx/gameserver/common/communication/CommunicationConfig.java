/*
 * 文件名: CommunicationConfig.java
 * 用途: 统一通信配置类
 * 实现内容:
 *   - 整合网络传输和RPC服务配置
 *   - 统一连接、心跳、负载均衡配置
 *   - 消除frame-network和frame-rpc的配置重复
 *   - 支持多种传输协议和RPC框架
 * 技术选型:
 *   - 继承BaseServerConfig
 *   - 分层配置设计
 *   - 准备Java 21 Record优化
 * 依赖关系:
 *   - 替代frame-network/NetworkConfig和frame-rpc/RpcProperties
 *   - 被所有通信相关模块使用
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.common.communication;

import com.lx.gameserver.common.config.BaseServerConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 统一通信配置类
 * <p>
 * 整合网络传输层（frame-network）和RPC服务层（frame-rpc）的配置，
 * 消除重复配置，提供统一的通信参数管理。支持TCP、UDP、WebSocket、
 * HTTP等多种传输协议，以及Feign、Dubbo等RPC框架。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
public class CommunicationConfig extends BaseServerConfig {

    /** 传输层配置 */
    private TransportConfig transport = new TransportConfig();

    /** RPC服务配置 */
    private RpcConfig rpc = new RpcConfig();

    /** 服务发现配置 */
    private DiscoveryConfig discovery = new DiscoveryConfig();

    /** 负载均衡配置 */
    private LoadBalancerConfig loadBalancer = new LoadBalancerConfig();

    /**
     * 传输层配置
     * 统一管理TCP、UDP、WebSocket、HTTP等传输协议配置
     */
    public static class TransportConfig {
        /** 启用的传输协议 */
        private List<String> enabledProtocols = List.of("tcp", "http");

        /** TCP配置 */
        private TcpConfig tcp = new TcpConfig();

        /** UDP配置 */
        private UdpConfig udp = new UdpConfig();

        /** WebSocket配置 */
        private WebSocketConfig websocket = new WebSocketConfig();

        /** HTTP配置 */
        private HttpConfig http = new HttpConfig();

        /** 连接池配置 */
        private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();

        /** 心跳配置 */
        private HeartbeatConfig heartbeat = new HeartbeatConfig();

        // Getters and Setters
        public List<String> getEnabledProtocols() { return enabledProtocols; }
        public void setEnabledProtocols(List<String> enabledProtocols) { this.enabledProtocols = enabledProtocols; }
        public TcpConfig getTcp() { return tcp; }
        public void setTcp(TcpConfig tcp) { this.tcp = tcp; }
        public UdpConfig getUdp() { return udp; }
        public void setUdp(UdpConfig udp) { this.udp = udp; }
        public WebSocketConfig getWebsocket() { return websocket; }
        public void setWebsocket(WebSocketConfig websocket) { this.websocket = websocket; }
        public HttpConfig getHttp() { return http; }
        public void setHttp(HttpConfig http) { this.http = http; }
        public ConnectionPoolConfig getConnectionPool() { return connectionPool; }
        public void setConnectionPool(ConnectionPoolConfig connectionPool) { this.connectionPool = connectionPool; }
        public HeartbeatConfig getHeartbeat() { return heartbeat; }
        public void setHeartbeat(HeartbeatConfig heartbeat) { this.heartbeat = heartbeat; }
    }

    /**
     * TCP传输配置
     */
    public static class TcpConfig {
        /** TCP端口 */
        private int port = 9001;

        /** 是否启用Nagle算法 */
        private boolean tcpNoDelay = true;

        /** 是否启用SO_KEEPALIVE */
        private boolean keepAlive = true;

        /** 接收缓冲区大小 */
        private int receiveBufferSize = 65536;

        /** 发送缓冲区大小 */
        private int sendBufferSize = 65536;

        // Getters and Setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isTcpNoDelay() { return tcpNoDelay; }
        public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }
        public boolean isKeepAlive() { return keepAlive; }
        public void setKeepAlive(boolean keepAlive) { this.keepAlive = keepAlive; }
        public int getReceiveBufferSize() { return receiveBufferSize; }
        public void setReceiveBufferSize(int receiveBufferSize) { this.receiveBufferSize = receiveBufferSize; }
        public int getSendBufferSize() { return sendBufferSize; }
        public void setSendBufferSize(int sendBufferSize) { this.sendBufferSize = sendBufferSize; }
    }

    /**
     * UDP传输配置
     */
    public static class UdpConfig {
        /** UDP端口 */
        private int port = 9002;

        /** 数据包最大大小 */
        private int maxPacketSize = 1400;

        // Getters and Setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getMaxPacketSize() { return maxPacketSize; }
        public void setMaxPacketSize(int maxPacketSize) { this.maxPacketSize = maxPacketSize; }
    }

    /**
     * WebSocket配置
     */
    public static class WebSocketConfig {
        /** WebSocket端口 */
        private int port = 9003;

        /** WebSocket路径 */
        private String path = "/ws";

        /** 最大帧大小 */
        private int maxFrameSize = 65536;

        // Getters and Setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getMaxFrameSize() { return maxFrameSize; }
        public void setMaxFrameSize(int maxFrameSize) { this.maxFrameSize = maxFrameSize; }
    }

    /**
     * HTTP配置
     */
    public static class HttpConfig {
        /** HTTP端口 */
        private int port = 8080;

        /** 最大请求大小 */
        private long maxRequestSize = 10485760L; // 10MB

        /** 连接超时 */
        private Duration connectionTimeout = Duration.ofSeconds(30);

        /** 读取超时 */
        private Duration readTimeout = Duration.ofSeconds(60);

        // Getters and Setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public long getMaxRequestSize() { return maxRequestSize; }
        public void setMaxRequestSize(long maxRequestSize) { this.maxRequestSize = maxRequestSize; }
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    /**
     * 连接池配置
     */
    public static class ConnectionPoolConfig {
        /** 最大连接数 */
        private int maxConnections = 1000;

        /** 核心连接数 */
        private int coreConnections = 100;

        /** 连接空闲超时 */
        private Duration idleTimeout = Duration.ofMinutes(10);

        /** 连接获取超时 */
        private Duration acquireTimeout = Duration.ofSeconds(30);

        // Getters and Setters
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public int getCoreConnections() { return coreConnections; }
        public void setCoreConnections(int coreConnections) { this.coreConnections = coreConnections; }
        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        public Duration getAcquireTimeout() { return acquireTimeout; }
        public void setAcquireTimeout(Duration acquireTimeout) { this.acquireTimeout = acquireTimeout; }
    }

    /**
     * 心跳配置
     */
    public static class HeartbeatConfig {
        /** 是否启用心跳 */
        private boolean enabled = true;

        /** 心跳间隔 */
        private Duration interval = Duration.ofSeconds(30);

        /** 心跳超时 */
        private Duration timeout = Duration.ofSeconds(10);

        /** 最大失败次数 */
        private int maxFailures = 3;

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getMaxFailures() { return maxFailures; }
        public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
    }

    /**
     * RPC服务配置
     */
    public static class RpcConfig {
        /** 启用的RPC框架 */
        private List<String> enabledFrameworks = List.of("feign");

        /** Feign配置 */
        private FeignConfig feign = new FeignConfig();

        /** Dubbo配置 */
        private DubboConfig dubbo = new DubboConfig();

        /** 熔断器配置 */
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

        // Getters and Setters
        public List<String> getEnabledFrameworks() { return enabledFrameworks; }
        public void setEnabledFrameworks(List<String> enabledFrameworks) { this.enabledFrameworks = enabledFrameworks; }
        public FeignConfig getFeign() { return feign; }
        public void setFeign(FeignConfig feign) { this.feign = feign; }
        public DubboConfig getDubbo() { return dubbo; }
        public void setDubbo(DubboConfig dubbo) { this.dubbo = dubbo; }
        public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    }

    /**
     * Feign配置
     */
    public static class FeignConfig {
        /** 连接超时 */
        private Duration connectTimeout = Duration.ofSeconds(10);

        /** 读取超时 */
        private Duration readTimeout = Duration.ofSeconds(60);

        /** 重试次数 */
        private int retryCount = 3;

        // Getters and Setters
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    }

    /**
     * Dubbo配置
     */
    public static class DubboConfig {
        /** 协议 */
        private String protocol = "dubbo";

        /** 端口 */
        private int port = 20880;

        /** 线程数 */
        private int threads = 200;

        // Getters and Setters
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getThreads() { return threads; }
        public void setThreads(int threads) { this.threads = threads; }
    }

    /**
     * 熔断器配置
     */
    public static class CircuitBreakerConfig {
        /** 是否启用 */
        private boolean enabled = true;

        /** 失败率阈值 */
        private double failureThreshold = 0.5;

        /** 最小请求数 */
        private int minimumRequests = 10;

        /** 等待时间 */
        private Duration waitDuration = Duration.ofSeconds(60);

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(double failureThreshold) { this.failureThreshold = failureThreshold; }
        public int getMinimumRequests() { return minimumRequests; }
        public void setMinimumRequests(int minimumRequests) { this.minimumRequests = minimumRequests; }
        public Duration getWaitDuration() { return waitDuration; }
        public void setWaitDuration(Duration waitDuration) { this.waitDuration = waitDuration; }
    }

    /**
     * 服务发现配置
     */
    public static class DiscoveryConfig {
        /** 服务发现类型 */
        private String type = "nacos";

        /** 服务器地址 */
        private String serverAddr = "localhost:8848";

        /** 命名空间 */
        private String namespace = "dev";

        /** 服务名称 */
        private String serviceName;

        /** 服务组 */
        private String group = "DEFAULT_GROUP";

        /** 实例权重 */
        private double weight = 1.0;

        /** 服务元数据 */
        private Map<String, String> metadata = Map.of();

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getServerAddr() { return serverAddr; }
        public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    /**
     * 负载均衡配置
     */
    public static class LoadBalancerConfig {
        /** 负载均衡策略 */
        private String strategy = "round_robin";

        /** 健康检查间隔 */
        private Duration healthCheckInterval = Duration.ofSeconds(30);

        /** 不健康阈值 */
        private int unhealthyThreshold = 3;

        /** 健康阈值 */
        private int healthyThreshold = 2;

        // Getters and Setters
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public Duration getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(Duration healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }
        public int getUnhealthyThreshold() { return unhealthyThreshold; }
        public void setUnhealthyThreshold(int unhealthyThreshold) { this.unhealthyThreshold = unhealthyThreshold; }
        public int getHealthyThreshold() { return healthyThreshold; }
        public void setHealthyThreshold(int healthyThreshold) { this.healthyThreshold = healthyThreshold; }
    }

    // Main class getters and setters
    public TransportConfig getTransport() { return transport; }
    public void setTransport(TransportConfig transport) { this.transport = transport; }
    public RpcConfig getRpc() { return rpc; }
    public void setRpc(RpcConfig rpc) { this.rpc = rpc; }
    public DiscoveryConfig getDiscovery() { return discovery; }
    public void setDiscovery(DiscoveryConfig discovery) { this.discovery = discovery; }
    public LoadBalancerConfig getLoadBalancer() { return loadBalancer; }
    public void setLoadBalancer(LoadBalancerConfig loadBalancer) { this.loadBalancer = loadBalancer; }

    /**
     * 获取指定协议的端口
     *
     * @param protocol 协议名称
     * @return 端口号，如果协议不存在返回-1
     */
    public int getPortByProtocol(String protocol) {
        return switch (protocol.toLowerCase()) {
            case "tcp" -> transport.getTcp().getPort();
            case "udp" -> transport.getUdp().getPort();
            case "websocket", "ws" -> transport.getWebsocket().getPort();
            case "http" -> transport.getHttp().getPort();
            case "dubbo" -> rpc.getDubbo().getPort();
            default -> -1;
        };
    }

    /**
     * 检查指定协议是否启用
     *
     * @param protocol 协议名称
     * @return 是否启用
     */
    public boolean isProtocolEnabled(String protocol) {
        return transport.getEnabledProtocols().contains(protocol.toLowerCase());
    }

    /**
     * 检查指定RPC框架是否启用
     *
     * @param framework RPC框架名称
     * @return 是否启用
     */
    public boolean isRpcFrameworkEnabled(String framework) {
        return rpc.getEnabledFrameworks().contains(framework.toLowerCase());
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }

        // 验证端口不冲突
        if (transport.getTcp().getPort() == transport.getUdp().getPort() ||
            transport.getTcp().getPort() == transport.getWebsocket().getPort() ||
            transport.getTcp().getPort() == transport.getHttp().getPort()) {
            return false;
        }

        // 验证服务发现配置
        if (discovery.getServiceName() == null || discovery.getServiceName().isBlank()) {
            return false;
        }

        return true;
    }
}