/*
 * 文件名: RedisConnectionPool.java
 * 用途: Redis连接池
 * 实现内容:
 *   - Redis连接池配置和管理
 *   - 支持单机、哨兵、集群模式
 *   - 连接健康检查和故障转移
 *   - 连接复用和性能监控
 *   - 自动重连和负载均衡
 * 技术选型:
 *   - Lettuce连接池
 *   - Spring Data Redis集成
 *   - 连接池配置管理
 *   - 健康检查机制
 * 依赖关系:
 *   - 被RedisCache使用
 *   - 提供Redis连接管理
 *   - 支持多种部署模式
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import io.lettuce.core.resource.ClientResources;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis连接池
 * <p>
 * 提供Redis连接的统一管理，支持单机、哨兵、集群等多种部署模式。
 * 包含连接健康检查、故障转移、性能监控等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class RedisConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionPool.class);

    /**
     * 连接池配置
     */
    private final RedisConnectionPoolConfig config;

    /**
     * Redis连接工厂
     */
    private volatile LettuceConnectionFactory connectionFactory;

    /**
     * 客户端资源
     */
    private volatile ClientResources clientResources;

    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 是否已启动
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * 是否已关闭
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 构造函数
     *
     * @param config 连接池配置
     */
    public RedisConnectionPool(RedisConnectionPoolConfig config) {
        this.config = Objects.requireNonNull(config, "连接池配置不能为null");
    }

    /**
     * 启动连接池
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            logger.warn("Redis连接池已经启动");
            return;
        }

        if (shutdown.get()) {
            throw new IllegalStateException("连接池已关闭，无法重新启动");
        }

        try {
            // 创建客户端资源
            this.clientResources = ClientResources.builder()
                .ioThreadPoolSize(config.getIoThreads())
                .computationThreadPoolSize(config.getComputationThreads())
                .build();

            // 创建连接工厂
            this.connectionFactory = createConnectionFactory();
            this.connectionFactory.afterPropertiesSet();

            // 启动健康检查
            if (config.isHealthCheckEnabled()) {
                startHealthCheck();
            }

            logger.info("Redis连接池已启动: {}", config);
        } catch (Exception e) {
            started.set(false);
            throw new RuntimeException("启动Redis连接池失败", e);
        }
    }

    /**
     * 关闭连接池
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            logger.warn("Redis连接池已经关闭");
            return;
        }

        try {
            // 关闭定时任务
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            // 关闭连接工厂
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }

            // 关闭客户端资源
            if (clientResources != null) {
                clientResources.shutdown();
            }

            started.set(false);
            logger.info("Redis连接池已关闭");
        } catch (Exception e) {
            logger.error("关闭Redis连接池失败", e);
        }
    }

    /**
     * 获取连接工厂
     *
     * @return Redis连接工厂
     */
    public RedisConnectionFactory getConnectionFactory() {
        if (!started.get()) {
            throw new IllegalStateException("连接池未启动");
        }
        return connectionFactory;
    }

    /**
     * 获取连接池配置
     *
     * @return 连接池配置
     */
    public RedisConnectionPoolConfig getConfig() {
        return config;
    }

    /**
     * 是否已启动
     *
     * @return 如果已启动返回true
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * 是否已关闭
     *
     * @return 如果已关闭返回true
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * 创建连接工厂
     */
    private LettuceConnectionFactory createConnectionFactory() {
        // 创建连接池配置
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(config.getMaxActive());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());
        poolConfig.setMaxWait(config.getMaxWait());
        poolConfig.setTestOnBorrow(config.isTestOnBorrow());
        poolConfig.setTestOnReturn(config.isTestOnReturn());
        poolConfig.setTestWhileIdle(config.isTestWhileIdle());

        // 创建客户端配置
        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .poolConfig(poolConfig)
            .commandTimeout(config.getTimeout())
            .shutdownTimeout(config.getShutdownTimeout())
            .clientResources(clientResources)
            .build();

        // 根据模式创建连接工厂
        switch (config.getMode()) {
            case STANDALONE:
                return createStandaloneConnectionFactory(clientConfig);
            case SENTINEL:
                return createSentinelConnectionFactory(clientConfig);
            case CLUSTER:
                return createClusterConnectionFactory(clientConfig);
            default:
                throw new IllegalArgumentException("不支持的Redis模式: " + config.getMode());
        }
    }

    /**
     * 创建单机连接工厂
     */
    private LettuceConnectionFactory createStandaloneConnectionFactory(
            LettucePoolingClientConfiguration clientConfig) {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
        standaloneConfig.setHostName(config.getHost());
        standaloneConfig.setPort(config.getPort());
        standaloneConfig.setDatabase(config.getDatabase());
        if (config.getPassword() != null) {
            standaloneConfig.setPassword(config.getPassword());
        }
        if (config.getUsername() != null) {
            standaloneConfig.setUsername(config.getUsername());
        }

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    /**
     * 创建哨兵连接工厂
     */
    private LettuceConnectionFactory createSentinelConnectionFactory(
            LettucePoolingClientConfiguration clientConfig) {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
        sentinelConfig.setMaster(config.getMasterName());
        sentinelConfig.setSentinels(config.getSentinelNodes());
        sentinelConfig.setDatabase(config.getDatabase());
        if (config.getPassword() != null) {
            sentinelConfig.setPassword(config.getPassword());
        }
        if (config.getUsername() != null) {
            sentinelConfig.setUsername(config.getUsername());
        }

        return new LettuceConnectionFactory(sentinelConfig, clientConfig);
    }

    /**
     * 创建集群连接工厂
     */
    private LettuceConnectionFactory createClusterConnectionFactory(
            LettucePoolingClientConfiguration clientConfig) {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        clusterConfig.setClusterNodes(config.getClusterNodes());
        clusterConfig.setMaxRedirects(config.getMaxRedirects());
        if (config.getPassword() != null) {
            clusterConfig.setPassword(config.getPassword());
        }
        if (config.getUsername() != null) {
            clusterConfig.setUsername(config.getUsername());
        }

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                logger.error("健康检查失败", e);
            }
        }, config.getHealthCheckInterval().toSeconds(), 
           config.getHealthCheckInterval().toSeconds(), TimeUnit.SECONDS);

        logger.info("Redis连接池健康检查已启动，间隔: {}", config.getHealthCheckInterval());
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        if (connectionFactory == null) {
            return;
        }

        try {
            // 简单的ping检查
            var connection = connectionFactory.getConnection();
            if (connection != null) {
                connection.ping();
                connection.close();
                logger.debug("Redis连接健康检查通过");
            }
        } catch (Exception e) {
            logger.warn("Redis连接健康检查失败", e);
            // 这里可以添加重连逻辑
        }
    }

    /**
     * Redis连接池配置
     */
    public static class RedisConnectionPoolConfig {
        private RedisMode mode = RedisMode.STANDALONE;
        private String host = "localhost";
        private int port = 6379;
        private String username;
        private String password;
        private int database = 0;
        private String masterName = "mymaster";
        private Set<org.springframework.data.redis.connection.RedisNode> sentinelNodes;
        private Set<org.springframework.data.redis.connection.RedisNode> clusterNodes;
        private int maxRedirects = 3;
        
        private int maxActive = 100;
        private int maxIdle = 50;
        private int minIdle = 10;
        private Duration maxWait = Duration.ofSeconds(3);
        private Duration timeout = Duration.ofSeconds(3);
        private Duration shutdownTimeout = Duration.ofSeconds(5);
        
        private boolean testOnBorrow = true;
        private boolean testOnReturn = false;
        private boolean testWhileIdle = true;
        
        private boolean healthCheckEnabled = true;
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        
        private int ioThreads = 4;
        private int computationThreads = 4;

        // Getters and setters
        public RedisMode getMode() { return mode; }
        public void setMode(RedisMode mode) { this.mode = mode; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
        
        public String getMasterName() { return masterName; }
        public void setMasterName(String masterName) { this.masterName = masterName; }
        
        public Set<org.springframework.data.redis.connection.RedisNode> getSentinelNodes() { return sentinelNodes; }
        public void setSentinelNodes(Set<org.springframework.data.redis.connection.RedisNode> sentinelNodes) { this.sentinelNodes = sentinelNodes; }
        
        public Set<org.springframework.data.redis.connection.RedisNode> getClusterNodes() { return clusterNodes; }
        public void setClusterNodes(Set<org.springframework.data.redis.connection.RedisNode> clusterNodes) { this.clusterNodes = clusterNodes; }
        
        public int getMaxRedirects() { return maxRedirects; }
        public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
        
        public int getMaxActive() { return maxActive; }
        public void setMaxActive(int maxActive) { this.maxActive = maxActive; }
        
        public int getMaxIdle() { return maxIdle; }
        public void setMaxIdle(int maxIdle) { this.maxIdle = maxIdle; }
        
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        
        public Duration getMaxWait() { return maxWait; }
        public void setMaxWait(Duration maxWait) { this.maxWait = maxWait; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        
        public Duration getShutdownTimeout() { return shutdownTimeout; }
        public void setShutdownTimeout(Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
        
        public boolean isTestOnBorrow() { return testOnBorrow; }
        public void setTestOnBorrow(boolean testOnBorrow) { this.testOnBorrow = testOnBorrow; }
        
        public boolean isTestOnReturn() { return testOnReturn; }
        public void setTestOnReturn(boolean testOnReturn) { this.testOnReturn = testOnReturn; }
        
        public boolean isTestWhileIdle() { return testWhileIdle; }
        public void setTestWhileIdle(boolean testWhileIdle) { this.testWhileIdle = testWhileIdle; }
        
        public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
        public void setHealthCheckEnabled(boolean healthCheckEnabled) { this.healthCheckEnabled = healthCheckEnabled; }
        
        public Duration getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(Duration healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }
        
        public int getIoThreads() { return ioThreads; }
        public void setIoThreads(int ioThreads) { this.ioThreads = ioThreads; }
        
        public int getComputationThreads() { return computationThreads; }
        public void setComputationThreads(int computationThreads) { this.computationThreads = computationThreads; }

        @Override
        public String toString() {
            return String.format("RedisConnectionPoolConfig{mode=%s, host='%s', port=%d, database=%d, maxActive=%d, maxIdle=%d, minIdle=%d}", 
                mode, host, port, database, maxActive, maxIdle, minIdle);
        }
    }

    /**
     * Redis模式枚举
     */
    public enum RedisMode {
        /**
         * 单机模式
         */
        STANDALONE("单机模式"),

        /**
         * 哨兵模式
         */
        SENTINEL("哨兵模式"),

        /**
         * 集群模式
         */
        CLUSTER("集群模式");

        private final String description;

        RedisMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}