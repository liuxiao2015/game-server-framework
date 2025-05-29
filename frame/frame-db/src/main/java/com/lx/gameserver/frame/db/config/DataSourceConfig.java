/*
 * 文件名: DataSourceConfig.java
 * 用途: 数据源配置类
 * 实现内容:
 *   - 主数据源配置，使用HikariCP连接池
 *   - 从数据源配置，支持多个从库配置
 *   - 开发环境数据源，H2内存数据库配置
 *   - 动态数据源配置，实现主从自动切换
 *   - 连接池优化参数配置
 * 技术选型:
 *   - Spring Boot配置
 *   - HikariCP连接池
 *   - 动态数据源路由
 * 依赖关系:
 *   - 依赖DatabaseProperties配置属性
 *   - 配置DynamicDataSource和各个数据源
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.config;

import com.lx.gameserver.frame.db.datasource.DynamicDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据源配置类
 * <p>
 * 配置游戏服务器的数据源，包括主库、从库和动态路由。
 * 支持开发环境的H2内存数据库和生产环境的MySQL集群。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
@ConditionalOnProperty(prefix = "game.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    private final DatabaseProperties databaseProperties;

    public DataSourceConfig(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    /**
     * 主数据源配置
     * <p>
     * 配置主数据库连接，用于写操作和强一致性读操作。
     * </p>
     *
     * @return 主数据源
     */
    @Bean("masterDataSource")
    @Profile("!dev")
    public DataSource masterDataSource() {
        DatabaseProperties.DataSourceConfig masterConfig = databaseProperties.getMaster();
        logger.info("配置主数据源: {}", maskUrl(masterConfig.getUrl()));
        return createHikariDataSource(masterConfig, "master");
    }

    /**
     * 开发环境数据源配置
     * <p>
     * 使用H2内存数据库，用于开发和测试环境。
     * </p>
     *
     * @return H2数据源
     */
    @Bean("masterDataSource")
    @Profile("dev")
    public DataSource devDataSource() {
        logger.info("配置开发环境H2数据源");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:gamedb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setPoolName("GameH2Pool");
        
        // 基础连接池参数
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // H2特定配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }

    /**
     * 动态数据源配置
     * <p>
     * 配置支持读写分离和负载均衡的动态数据源。
     * </p>
     *
     * @param masterDataSource 主数据源
     * @return 动态数据源
     */
    @Bean
    @Primary
    public DynamicDataSource dynamicDataSource(DataSource masterDataSource) {
        logger.info("配置动态数据源");
        
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        // 配置目标数据源映射
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DynamicDataSource.MASTER_KEY, masterDataSource);
        
        // 配置从库数据源
        configureSlaveDataSources(targetDataSources, dynamicDataSource);
        
        // 设置目标数据源和默认数据源
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        // 设置负载均衡算法
        dynamicDataSource.setLoadBalanceType(DynamicDataSource.LoadBalanceType.ROUND_ROBIN);
        
        return dynamicDataSource;
    }

    /**
     * 配置从库数据源
     *
     * @param targetDataSources 目标数据源映射
     * @param dynamicDataSource 动态数据源
     */
    @Profile("!dev")
    private void configureSlaveDataSources(Map<Object, Object> targetDataSources, 
                                         DynamicDataSource dynamicDataSource) {
        var slaves = databaseProperties.getSlaves();
        if (slaves == null || slaves.isEmpty()) {
            logger.info("未配置从库数据源");
            return;
        }

        for (int i = 0; i < slaves.size(); i++) {
            DatabaseProperties.DataSourceConfig slaveConfig = slaves.get(i);
            if (!slaveConfig.isEnabled()) {
                logger.info("跳过已禁用的从库: slave_{}", i);
                continue;
            }

            String slaveKey = DynamicDataSource.SLAVE_KEY_PREFIX + i;
            DataSource slaveDataSource = createHikariDataSource(slaveConfig, slaveKey);
            
            targetDataSources.put(slaveKey, slaveDataSource);
            dynamicDataSource.addSlaveDataSource(slaveKey, slaveConfig.getWeight());
            
            logger.info("配置从库数据源: {}, URL: {}, 权重: {}", 
                       slaveKey, maskUrl(slaveConfig.getUrl()), slaveConfig.getWeight());
        }
    }

    /**
     * 创建HikariCP数据源
     *
     * @param config 数据源配置
     * @param poolName 连接池名称
     * @return HikariDataSource实例
     */
    private HikariDataSource createHikariDataSource(DatabaseProperties.DataSourceConfig config, 
                                                   String poolName) {
        HikariConfig hikariConfig = new HikariConfig();
        
        // 基础连接参数
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        hikariConfig.setPoolName("Game" + capitalize(poolName) + "Pool");
        
        // 连接池参数
        DatabaseProperties.PoolConfig poolConfig = databaseProperties.getPool();
        hikariConfig.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(poolConfig.getMinimumIdle());
        hikariConfig.setConnectionTimeout(poolConfig.getConnectionTimeout().toMillis());
        hikariConfig.setIdleTimeout(poolConfig.getIdleTimeout().toMillis());
        hikariConfig.setMaxLifetime(poolConfig.getMaxLifetime().toMillis());
        hikariConfig.setLeakDetectionThreshold(poolConfig.getLeakDetectionThreshold().toMillis());
        
        // MySQL性能优化参数
        configureMySQL8Performance(hikariConfig, poolConfig);
        
        // 健康检查
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setValidationTimeout(3000);
        
        return new HikariDataSource(hikariConfig);
    }

    /**
     * 配置MySQL 8.0性能优化参数
     *
     * @param config HikariCP配置
     * @param poolConfig 连接池配置
     */
    private void configureMySQL8Performance(HikariConfig config, DatabaseProperties.PoolConfig poolConfig) {
        // 预编译语句缓存
        config.addDataSourceProperty("cachePrepStmts", poolConfig.isPrepStmtCacheSize());
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", poolConfig.getPrepStmtCacheSqlLimit());
        
        // 服务器端预编译语句
        config.addDataSourceProperty("useServerPrepStmts", poolConfig.isUseServerPrepStmts());
        
        // 批量重写
        config.addDataSourceProperty("rewriteBatchedStatements", poolConfig.isRewriteBatchedStatements());
        
        // 其他性能优化
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("useLocalTransactionState", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // SSL配置
        config.addDataSourceProperty("useSSL", "false");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        
        // 字符集配置
        config.addDataSourceProperty("characterEncoding", "utf8mb4");
        config.addDataSourceProperty("useUnicode", "true");
        
        // 时区配置
        config.addDataSourceProperty("serverTimezone", "Asia/Shanghai");
    }

    /**
     * 首字母大写
     *
     * @param str 输入字符串
     * @return 首字母大写的字符串
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 掩码URL中的敏感信息
     *
     * @param url 数据库URL
     * @return 掩码后的URL
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        
        // 简单的URL掩码，隐藏密码等敏感信息
        return url.replaceAll("password=[^&]*", "password=***");
    }
}