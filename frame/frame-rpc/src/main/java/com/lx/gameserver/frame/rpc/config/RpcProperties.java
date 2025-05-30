/*
 * 文件名: RpcProperties.java
 * 用途: RPC配置属性类
 * 实现内容:
 *   - 服务发现配置（Nacos/Eureka/Consul）
 *   - 超时配置（全局默认、服务级别）
 *   - 重试配置（次数、间隔、指数退避）
 *   - 熔断配置（阈值、窗口期）
 *   - 日志配置（日志级别、采样率）
 *   - 负载均衡配置
 * 技术选型:
 *   - Spring Boot ConfigurationProperties
 *   - 支持YAML和Properties配置
 *   - 嵌套配置类设计
 * 依赖关系:
 *   - 被所有RPC相关配置类使用
 *   - 与Spring Boot自动配置集成
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC配置属性类
 * <p>
 * 提供RPC模块的统一配置管理，包括服务发现、超时、重试、
 * 熔断、日志等各方面的配置。支持全局默认配置和服务级别配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@ConfigurationProperties(prefix = "game.rpc")
public class RpcProperties {

    /**
     * 是否启用RPC模块
     */
    private boolean enabled = true;

    /**
     * 服务发现配置
     */
    @NestedConfigurationProperty
    private DiscoveryProperties discovery = new DiscoveryProperties();

    /**
     * Feign配置
     */
    @NestedConfigurationProperty
    private FeignProperties feign = new FeignProperties();

    /**
     * 负载均衡配置
     */
    @NestedConfigurationProperty
    private LoadBalancerProperties loadbalancer = new LoadBalancerProperties();

    /**
     * 熔断器配置
     */
    @NestedConfigurationProperty
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    /**
     * 监控配置
     */
    @NestedConfigurationProperty
    private MetricsProperties metrics = new MetricsProperties();

    /**
     * 服务发现配置
     */
    public static class DiscoveryProperties {
        /**
         * 服务发现类型
         */
        private String type = "nacos";

        /**
         * 服务器地址
         */
        private String serverAddr = "localhost:8848";

        /**
         * 命名空间
         */
        private String namespace = "dev";

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * 分组
         */
        private String group = "DEFAULT_GROUP";

        // Getter and Setter methods
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getServerAddr() { return serverAddr; }
        public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
    }

    /**
     * Feign配置
     */
    public static class FeignProperties {
        /**
         * 压缩配置
         */
        @NestedConfigurationProperty
        private CompressionProperties compression = new CompressionProperties();

        /**
         * 客户端配置
         */
        @NestedConfigurationProperty
        private ClientProperties client = new ClientProperties();

        /**
         * 重试配置
         */
        @NestedConfigurationProperty
        private RetryProperties retry = new RetryProperties();

        /**
         * 压缩配置
         */
        public static class CompressionProperties {
            /**
             * 请求压缩配置
             */
            @NestedConfigurationProperty
            private RequestCompressionProperties request = new RequestCompressionProperties();

            /**
             * 响应压缩配置
             */
            @NestedConfigurationProperty
            private ResponseCompressionProperties response = new ResponseCompressionProperties();

            /**
             * 请求压缩配置
             */
            public static class RequestCompressionProperties {
                private boolean enabled = true;
                private int minRequestSize = 2048;

                public boolean isEnabled() { return enabled; }
                public void setEnabled(boolean enabled) { this.enabled = enabled; }
                public int getMinRequestSize() { return minRequestSize; }
                public void setMinRequestSize(int minRequestSize) { this.minRequestSize = minRequestSize; }
            }

            /**
             * 响应压缩配置
             */
            public static class ResponseCompressionProperties {
                private boolean enabled = true;

                public boolean isEnabled() { return enabled; }
                public void setEnabled(boolean enabled) { this.enabled = enabled; }
            }

            public RequestCompressionProperties getRequest() { return request; }
            public void setRequest(RequestCompressionProperties request) { this.request = request; }
            public ResponseCompressionProperties getResponse() { return response; }
            public void setResponse(ResponseCompressionProperties response) { this.response = response; }
        }

        /**
         * 客户端配置
         */
        public static class ClientProperties {
            /**
             * 默认配置
             */
            @NestedConfigurationProperty
            private DefaultClientConfig defaultConfig = new DefaultClientConfig();

            /**
             * 服务级别配置
             */
            private Map<String, DefaultClientConfig> config = new HashMap<>();

            /**
             * 默认客户端配置
             */
            public static class DefaultClientConfig {
                private Duration connectTimeout = Duration.ofSeconds(5);
                private Duration readTimeout = Duration.ofSeconds(10);
                private String loggerLevel = "BASIC";

                public Duration getConnectTimeout() { return connectTimeout; }
                public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
                public Duration getReadTimeout() { return readTimeout; }
                public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
                public String getLoggerLevel() { return loggerLevel; }
                public void setLoggerLevel(String loggerLevel) { this.loggerLevel = loggerLevel; }
            }

            public DefaultClientConfig getDefaultConfig() { return defaultConfig; }
            public void setDefaultConfig(DefaultClientConfig defaultConfig) { this.defaultConfig = defaultConfig; }
            public Map<String, DefaultClientConfig> getConfig() { return config; }
            public void setConfig(Map<String, DefaultClientConfig> config) { this.config = config; }
        }

        /**
         * 重试配置
         */
        public static class RetryProperties {
            private int maxAttempts = 3;
            @NestedConfigurationProperty
            private BackoffProperties backoff = new BackoffProperties();

            /**
             * 退避策略配置
             */
            public static class BackoffProperties {
                private Duration delay = Duration.ofSeconds(1);
                private Duration maxDelay = Duration.ofSeconds(5);
                private double multiplier = 1.5;

                public Duration getDelay() { return delay; }
                public void setDelay(Duration delay) { this.delay = delay; }
                public Duration getMaxDelay() { return maxDelay; }
                public void setMaxDelay(Duration maxDelay) { this.maxDelay = maxDelay; }
                public double getMultiplier() { return multiplier; }
                public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
            }

            public int getMaxAttempts() { return maxAttempts; }
            public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
            public BackoffProperties getBackoff() { return backoff; }
            public void setBackoff(BackoffProperties backoff) { this.backoff = backoff; }
        }

        public CompressionProperties getCompression() { return compression; }
        public void setCompression(CompressionProperties compression) { this.compression = compression; }
        public ClientProperties getClient() { return client; }
        public void setClient(ClientProperties client) { this.client = client; }
        public RetryProperties getRetry() { return retry; }
        public void setRetry(RetryProperties retry) { this.retry = retry; }
    }

    /**
     * 负载均衡配置
     */
    public static class LoadBalancerProperties {
        /**
         * 负载均衡策略
         */
        private String strategy = "weighted-response-time";

        /**
         * 健康检查配置
         */
        @NestedConfigurationProperty
        private HealthCheckProperties healthCheck = new HealthCheckProperties();

        /**
         * 健康检查配置
         */
        public static class HealthCheckProperties {
            private Duration interval = Duration.ofSeconds(5);
            private Duration timeout = Duration.ofSeconds(3);

            public Duration getInterval() { return interval; }
            public void setInterval(Duration interval) { this.interval = interval; }
            public Duration getTimeout() { return timeout; }
            public void setTimeout(Duration timeout) { this.timeout = timeout; }
        }

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public HealthCheckProperties getHealthCheck() { return healthCheck; }
        public void setHealthCheck(HealthCheckProperties healthCheck) { this.healthCheck = healthCheck; }
    }

    /**
     * 熔断器配置
     */
    public static class CircuitBreakerProperties {
        private int failureRateThreshold = 50;
        private int slowCallRateThreshold = 50;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(3);
        private int slidingWindowSize = 100;
        private int minimumNumberOfCalls = 20;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);

        public int getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(int failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        public int getSlowCallRateThreshold() { return slowCallRateThreshold; }
        public void setSlowCallRateThreshold(int slowCallRateThreshold) { this.slowCallRateThreshold = slowCallRateThreshold; }
        public Duration getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) { this.slowCallDurationThreshold = slowCallDurationThreshold; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }
    }

    /**
     * 监控配置
     */
    public static class MetricsProperties {
        private boolean enabled = true;
        private Duration exportInterval = Duration.ofSeconds(10);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getExportInterval() { return exportInterval; }
        public void setExportInterval(Duration exportInterval) { this.exportInterval = exportInterval; }
    }

    // Main class getter and setter methods
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public DiscoveryProperties getDiscovery() { return discovery; }
    public void setDiscovery(DiscoveryProperties discovery) { this.discovery = discovery; }
    public FeignProperties getFeign() { return feign; }
    public void setFeign(FeignProperties feign) { this.feign = feign; }
    public LoadBalancerProperties getLoadbalancer() { return loadbalancer; }
    public void setLoadbalancer(LoadBalancerProperties loadbalancer) { this.loadbalancer = loadbalancer; }
    public CircuitBreakerProperties getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    public MetricsProperties getMetrics() { return metrics; }
    public void setMetrics(MetricsProperties metrics) { this.metrics = metrics; }
}