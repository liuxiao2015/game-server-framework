/*
 * 文件名: CacheConfiguration.java
 * 用途: 缓存配置类
 * 实现内容:
 *   - 全局缓存配置管理
 *   - 缓存实例配置支持
 *   - 动态配置和配置验证
 *   - 配置导出和导入功能
 *   - Spring Boot配置集成
 * 技术选型:
 *   - Spring Boot Configuration
 *   - YAML配置文件支持
 *   - 配置属性绑定
 *   - 环境变量支持
 * 依赖关系:
 *   - 被缓存管理器使用
 *   - 提供配置管理功能
 *   - 支持外部化配置
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置类
 * <p>
 * 统一管理框架的缓存配置，支持全局配置和实例配置。
 * 集成Spring Boot配置系统，支持外部化配置管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
@ConfigurationProperties(prefix = "frame.cache")
public class CacheConfiguration {

    /**
     * 全局配置
     */
    private GlobalConfig global = new GlobalConfig();

    /**
     * 本地缓存配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * Redis配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * 多级缓存配置
     */
    private MultiLevelConfig multiLevel = new MultiLevelConfig();

    /**
     * 预热配置
     */
    private WarmupConfig warmup = new WarmupConfig();

    /**
     * 监控配置
     */
    private MonitorConfig monitor = new MonitorConfig();

    /**
     * 自定义缓存配置
     */
    private Map<String, InstanceConfig> instances = new HashMap<>();

    // Getters and Setters
    public GlobalConfig getGlobal() { return global; }
    public void setGlobal(GlobalConfig global) { this.global = global; }

    public LocalConfig getLocal() { return local; }
    public void setLocal(LocalConfig local) { this.local = local; }

    public RedisConfig getRedis() { return redis; }
    public void setRedis(RedisConfig redis) { this.redis = redis; }

    public MultiLevelConfig getMultiLevel() { return multiLevel; }
    public void setMultiLevel(MultiLevelConfig multiLevel) { this.multiLevel = multiLevel; }

    public WarmupConfig getWarmup() { return warmup; }
    public void setWarmup(WarmupConfig warmup) { this.warmup = warmup; }

    public MonitorConfig getMonitor() { return monitor; }
    public void setMonitor(MonitorConfig monitor) { this.monitor = monitor; }

    public Map<String, InstanceConfig> getInstances() { return instances; }
    public void setInstances(Map<String, InstanceConfig> instances) { this.instances = instances; }

    /**
     * 全局配置
     */
    public static class GlobalConfig {
        private Duration defaultExpireTime = Duration.ofHours(1);
        private String maxMemoryUsage = "2GB";
        private boolean statisticsEnabled = true;
        private boolean asyncEnabled = true;

        public Duration getDefaultExpireTime() { return defaultExpireTime; }
        public void setDefaultExpireTime(Duration defaultExpireTime) { this.defaultExpireTime = defaultExpireTime; }

        public String getMaxMemoryUsage() { return maxMemoryUsage; }
        public void setMaxMemoryUsage(String maxMemoryUsage) { this.maxMemoryUsage = maxMemoryUsage; }

        public boolean isStatisticsEnabled() { return statisticsEnabled; }
        public void setStatisticsEnabled(boolean statisticsEnabled) { this.statisticsEnabled = statisticsEnabled; }

        public boolean isAsyncEnabled() { return asyncEnabled; }
        public void setAsyncEnabled(boolean asyncEnabled) { this.asyncEnabled = asyncEnabled; }
    }

    /**
     * 本地缓存配置
     */
    public static class LocalConfig {
        private String provider = "caffeine";
        private long maxSize = 10000;
        private Duration expireAfterWrite = Duration.ofMinutes(10);
        private Duration expireAfterAccess = Duration.ofMinutes(5);
        private String evictionPolicy = "lru";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }

        public Duration getExpireAfterWrite() { return expireAfterWrite; }
        public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }

        public Duration getExpireAfterAccess() { return expireAfterAccess; }
        public void setExpireAfterAccess(Duration expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }

        public String getEvictionPolicy() { return evictionPolicy; }
        public void setEvictionPolicy(String evictionPolicy) { this.evictionPolicy = evictionPolicy; }
    }

    /**
     * Redis配置
     */
    public static class RedisConfig {
        private String mode = "cluster";
        private String[] addresses = {"redis://127.0.0.1:7000", "redis://127.0.0.1:7001"};
        private String password;
        private Duration timeout = Duration.ofSeconds(3);
        private PoolConfig pool = new PoolConfig();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String[] getAddresses() { return addresses; }
        public void setAddresses(String[] addresses) { this.addresses = addresses; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }

        public PoolConfig getPool() { return pool; }
        public void setPool(PoolConfig pool) { this.pool = pool; }

        public static class PoolConfig {
            private int maxActive = 100;
            private int maxIdle = 50;
            private int minIdle = 10;

            public int getMaxActive() { return maxActive; }
            public void setMaxActive(int maxActive) { this.maxActive = maxActive; }

            public int getMaxIdle() { return maxIdle; }
            public void setMaxIdle(int maxIdle) { this.maxIdle = maxIdle; }

            public int getMinIdle() { return minIdle; }
            public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        }
    }

    /**
     * 多级缓存配置
     */
    public static class MultiLevelConfig {
        private boolean l1Enabled = true;
        private boolean l2Enabled = true;
        private boolean syncEnabled = true;
        private Duration syncDelay = Duration.ofMillis(100);

        public boolean isL1Enabled() { return l1Enabled; }
        public void setL1Enabled(boolean l1Enabled) { this.l1Enabled = l1Enabled; }

        public boolean isL2Enabled() { return l2Enabled; }
        public void setL2Enabled(boolean l2Enabled) { this.l2Enabled = l2Enabled; }

        public boolean isSyncEnabled() { return syncEnabled; }
        public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }

        public Duration getSyncDelay() { return syncDelay; }
        public void setSyncDelay(Duration syncDelay) { this.syncDelay = syncDelay; }
    }

    /**
     * 预热配置
     */
    public static class WarmupConfig {
        private boolean enabled = true;
        private boolean startupWarmup = true;
        private int parallelThreads = 4;
        private int batchSize = 1000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isStartupWarmup() { return startupWarmup; }
        public void setStartupWarmup(boolean startupWarmup) { this.startupWarmup = startupWarmup; }

        public int getParallelThreads() { return parallelThreads; }
        public void setParallelThreads(int parallelThreads) { this.parallelThreads = parallelThreads; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    /**
     * 监控配置
     */
    public static class MonitorConfig {
        private boolean metricsEnabled = true;
        private Duration exportInterval = Duration.ofSeconds(60);
        private Duration slowQueryThreshold = Duration.ofMillis(100);

        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }

        public Duration getExportInterval() { return exportInterval; }
        public void setExportInterval(Duration exportInterval) { this.exportInterval = exportInterval; }

        public Duration getSlowQueryThreshold() { return slowQueryThreshold; }
        public void setSlowQueryThreshold(Duration slowQueryThreshold) { this.slowQueryThreshold = slowQueryThreshold; }
    }

    /**
     * 实例配置
     */
    public static class InstanceConfig {
        private String type = "local";
        private long maxSize = 1000;
        private Duration expireAfterWrite;
        private Duration expireAfterAccess;
        private String evictionPolicy = "lru";
        private boolean statisticsEnabled = true;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }

        public Duration getExpireAfterWrite() { return expireAfterWrite; }
        public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }

        public Duration getExpireAfterAccess() { return expireAfterAccess; }
        public void setExpireAfterAccess(Duration expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }

        public String getEvictionPolicy() { return evictionPolicy; }
        public void setEvictionPolicy(String evictionPolicy) { this.evictionPolicy = evictionPolicy; }

        public boolean isStatisticsEnabled() { return statisticsEnabled; }
        public void setStatisticsEnabled(boolean statisticsEnabled) { this.statisticsEnabled = statisticsEnabled; }
    }
}