/*
 * 文件名: DatabaseProperties.java
 * 用途: 数据库配置属性类
 * 实现内容:
 *   - 主从数据库连接配置
 *   - 连接池参数配置
 *   - 分库分表配置参数
 *   - 监控和优化配置
 * 技术选型:
 *   - Spring Boot配置绑定
 *   - HikariCP连接池配置
 *   - 支持多环境配置
 * 依赖关系:
 *   - 被DataSourceConfig使用
 *   - 支持application.yml配置文件绑定
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库配置属性
 * <p>
 * 统一管理数据库相关的配置参数，支持主从库配置、连接池参数、
 * 分库分表配置等。使用Spring Boot的配置绑定机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@ConfigurationProperties(prefix = "game.database")
public class DatabaseProperties {

    /**
     * 是否启用数据库功能
     */
    private boolean enabled = true;

    /**
     * 主数据源配置
     */
    @NestedConfigurationProperty
    private DataSourceConfig master = new DataSourceConfig();

    /**
     * 从数据源配置列表
     */
    private List<DataSourceConfig> slaves = new ArrayList<>();

    /**
     * 连接池配置
     */
    @NestedConfigurationProperty
    private PoolConfig pool = new PoolConfig();

    /**
     * 分库分表配置
     */
    @NestedConfigurationProperty
    private ShardingConfig sharding = new ShardingConfig();

    /**
     * 监控配置
     */
    @NestedConfigurationProperty
    private MonitorConfig monitor = new MonitorConfig();

    /**
     * 数据源配置
     */
    public static class DataSourceConfig {
        /**
         * 数据库连接URL
         */
        private String url;

        /**
         * 数据库用户名
         */
        private String username;

        /**
         * 数据库密码
         */
        private String password;

        /**
         * 数据库驱动类名
         */
        private String driverClassName = "com.mysql.cj.jdbc.Driver";

        /**
         * 数据源权重(用于负载均衡)
         */
        private int weight = 1;

        /**
         * 是否启用该数据源
         */
        private boolean enabled = true;

        // Getters and Setters
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 连接池配置
     */
    public static class PoolConfig {
        /**
         * 最大连接数
         */
        private int maximumPoolSize = 20;

        /**
         * 最小空闲连接数
         */
        private int minimumIdle = 5;

        /**
         * 连接超时时间
         */
        private Duration connectionTimeout = Duration.ofSeconds(30);

        /**
         * 空闲超时时间
         */
        private Duration idleTimeout = Duration.ofMinutes(10);

        /**
         * 最大生命周期
         */
        private Duration maxLifetime = Duration.ofMinutes(30);

        /**
         * 连接泄露检测阈值
         */
        private Duration leakDetectionThreshold = Duration.ofSeconds(60);

        /**
         * 是否启用预编译语句缓存
         */
        private boolean prepStmtCacheSize = true;

        /**
         * 预编译语句缓存大小
         */
        private int prepStmtCacheSqlLimit = 2048;

        /**
         * 是否使用服务器端预编译语句
         */
        private boolean useServerPrepStmts = true;

        /**
         * 是否重写批量语句
         */
        private boolean rewriteBatchedStatements = true;

        // Getters and Setters
        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Duration getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Duration getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(Duration maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public Duration getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }

        public void setLeakDetectionThreshold(Duration leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
        }

        public boolean isPrepStmtCacheSize() {
            return prepStmtCacheSize;
        }

        public void setPrepStmtCacheSize(boolean prepStmtCacheSize) {
            this.prepStmtCacheSize = prepStmtCacheSize;
        }

        public int getPrepStmtCacheSqlLimit() {
            return prepStmtCacheSqlLimit;
        }

        public void setPrepStmtCacheSqlLimit(int prepStmtCacheSqlLimit) {
            this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
        }

        public boolean isUseServerPrepStmts() {
            return useServerPrepStmts;
        }

        public void setUseServerPrepStmts(boolean useServerPrepStmts) {
            this.useServerPrepStmts = useServerPrepStmts;
        }

        public boolean isRewriteBatchedStatements() {
            return rewriteBatchedStatements;
        }

        public void setRewriteBatchedStatements(boolean rewriteBatchedStatements) {
            this.rewriteBatchedStatements = rewriteBatchedStatements;
        }
    }

    /**
     * 分库分表配置
     */
    public static class ShardingConfig {
        /**
         * 是否启用分库分表
         */
        private boolean enabled = false;

        /**
         * 数据库分片数量
         */
        private int databaseShardingCount = 2;

        /**
         * 表分片数量
         */
        private int tableShardingCount = 4;

        /**
         * 分片算法类型
         */
        private String shardingAlgorithm = "mod";

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDatabaseShardingCount() {
            return databaseShardingCount;
        }

        public void setDatabaseShardingCount(int databaseShardingCount) {
            this.databaseShardingCount = databaseShardingCount;
        }

        public int getTableShardingCount() {
            return tableShardingCount;
        }

        public void setTableShardingCount(int tableShardingCount) {
            this.tableShardingCount = tableShardingCount;
        }

        public String getShardingAlgorithm() {
            return shardingAlgorithm;
        }

        public void setShardingAlgorithm(String shardingAlgorithm) {
            this.shardingAlgorithm = shardingAlgorithm;
        }
    }

    /**
     * 监控配置
     */
    public static class MonitorConfig {
        /**
         * 是否启用监控
         */
        private boolean enabled = true;

        /**
         * 慢查询阈值(毫秒)
         */
        private long slowQueryThreshold = 1000;

        /**
         * 指标采集间隔(秒)
         */
        private int metricsInterval = 60;

        /**
         * 健康检查间隔(秒)
         */
        private int healthCheckInterval = 10;

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowQueryThreshold() {
            return slowQueryThreshold;
        }

        public void setSlowQueryThreshold(long slowQueryThreshold) {
            this.slowQueryThreshold = slowQueryThreshold;
        }

        public int getMetricsInterval() {
            return metricsInterval;
        }

        public void setMetricsInterval(int metricsInterval) {
            this.metricsInterval = metricsInterval;
        }

        public int getHealthCheckInterval() {
            return healthCheckInterval;
        }

        public void setHealthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
        }
    }

    // Main class getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DataSourceConfig getMaster() {
        return master;
    }

    public void setMaster(DataSourceConfig master) {
        this.master = master;
    }

    public List<DataSourceConfig> getSlaves() {
        return slaves;
    }

    public void setSlaves(List<DataSourceConfig> slaves) {
        this.slaves = slaves;
    }

    public PoolConfig getPool() {
        return pool;
    }

    public void setPool(PoolConfig pool) {
        this.pool = pool;
    }

    public ShardingConfig getSharding() {
        return sharding;
    }

    public void setSharding(ShardingConfig sharding) {
        this.sharding = sharding;
    }

    public MonitorConfig getMonitor() {
        return monitor;
    }

    public void setMonitor(MonitorConfig monitor) {
        this.monitor = monitor;
    }
}