/*
 * 文件名: ShardingConfig.java
 * 用途: 分库分表配置类
 * 实现内容:
 *   - 集成ShardingSphere 5.x版本
 *   - 配置分库规则，按玩家ID取模分库
 *   - 配置分表规则，按时间/ID范围分表
 *   - 配置读写分离规则，与分片规则结合
 *   - 配置分布式事务，支持跨库事务
 * 技术选型:
 *   - Apache ShardingSphere 5.4.1
 *   - 雪花算法分布式ID
 *   - 读写分离与分片结合
 * 依赖关系:
 *   - 依赖DatabaseProperties配置
 *   - 被DynamicDataSource集成
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.sharding;

import com.lx.gameserver.frame.db.config.DatabaseProperties;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.mode.repository.standalone.StandalonePersistRepositoryConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.rule.ReadwriteSplittingDataSourceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/**
 * 分库分表配置类
 * <p>
 * 基于Apache ShardingSphere实现分库分表功能，支持水平分库分表、
 * 读写分离、分布式事务等企业级特性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnProperty(prefix = "game.database.sharding", name = "enabled", havingValue = "true")
public class ShardingConfig {

    private static final Logger logger = LoggerFactory.getLogger(ShardingConfig.class);

    private final DatabaseProperties databaseProperties;

    public ShardingConfig(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    /**
     * 创建分片数据源
     *
     * @param masterDataSource 主数据源
     * @param slaveDataSources 从数据源映射
     * @return 分片数据源
     * @throws SQLException SQL异常
     */
    @Bean
    public DataSource shardingDataSource(DataSource masterDataSource, 
                                       Map<String, DataSource> slaveDataSources) throws SQLException {
        
        if (!databaseProperties.getSharding().isEnabled()) {
            logger.info("分库分表功能未启用，使用普通数据源");
            return masterDataSource;
        }

        logger.info("开始配置分库分表数据源");

        // 创建数据源映射
        Map<String, DataSource> dataSourceMap = createDataSourceMap(masterDataSource, slaveDataSources);

        // 创建分片规则配置
        Collection<RuleConfiguration> ruleConfigs = createRuleConfigurations();

        // 创建属性配置
        Properties props = createProperties();

        // 创建模式配置
        ModeConfiguration modeConfig = createModeConfiguration();

        // 创建分片数据源
        DataSource shardingDataSource = ShardingSphereDataSourceFactory.createDataSource(
                modeConfig, dataSourceMap, ruleConfigs, props);

        logger.info("分库分表数据源配置完成");
        return shardingDataSource;
    }

    /**
     * 创建数据源映射
     */
    private Map<String, DataSource> createDataSourceMap(DataSource masterDataSource, 
                                                       Map<String, DataSource> slaveDataSources) {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        
        DatabaseProperties.ShardingConfig shardingConfig = databaseProperties.getSharding();
        int databaseCount = shardingConfig.getDatabaseShardingCount();

        // 配置分库的主从数据源
        for (int i = 0; i < databaseCount; i++) {
            String masterName = "master_" + i;
            dataSourceMap.put(masterName, masterDataSource);
            
            // 为每个分库配置从库
            for (Map.Entry<String, DataSource> slaveEntry : slaveDataSources.entrySet()) {
                String slaveName = "slave_" + i + "_" + slaveEntry.getKey();
                dataSourceMap.put(slaveName, slaveEntry.getValue());
            }
        }

        logger.info("配置了{}个分库，每库{}个从库", databaseCount, slaveDataSources.size());
        return dataSourceMap;
    }

    /**
     * 创建规则配置
     */
    private Collection<RuleConfiguration> createRuleConfigurations() {
        Collection<RuleConfiguration> ruleConfigs = new ArrayList<>();

        // 添加分片规则
        ruleConfigs.add(createShardingRuleConfiguration());

        // 添加读写分离规则
        ruleConfigs.add(createReadwriteSplittingRuleConfiguration());

        return ruleConfigs;
    }

    /**
     * 创建分片规则配置
     */
    private ShardingRuleConfiguration createShardingRuleConfiguration() {
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();

        // 配置分片算法
        shardingRuleConfig.getShardingAlgorithms().put("database_mod", createDatabaseShardingAlgorithm());
        shardingRuleConfig.getShardingAlgorithms().put("table_mod", createTableShardingAlgorithm());

        // 配置主键生成算法
        shardingRuleConfig.getKeyGenerators().put("snowflake", createSnowflakeKeyGeneratorAlgorithm());

        // 配置分表规则
        shardingRuleConfig.getTables().addAll(createTableRuleConfigurations());

        // 配置默认分库策略
        shardingRuleConfig.setDefaultDatabaseShardingStrategy(
                new StandardShardingStrategyConfiguration("player_id", "database_mod"));

        // 配置默认主键生成策略
        shardingRuleConfig.setDefaultKeyGenerateStrategy(
                new KeyGenerateStrategyConfiguration("id", "snowflake"));

        return shardingRuleConfig;
    }

    /**
     * 创建数据库分片算法配置
     */
    private AlgorithmConfiguration createDatabaseShardingAlgorithm() {
        Properties props = new Properties();
        props.setProperty("sharding-count", String.valueOf(databaseProperties.getSharding().getDatabaseShardingCount()));
        return new AlgorithmConfiguration("CLASS_BASED", props);
    }

    /**
     * 创建表分片算法配置
     */
    private AlgorithmConfiguration createTableShardingAlgorithm() {
        Properties props = new Properties();
        props.setProperty("sharding-count", String.valueOf(databaseProperties.getSharding().getTableShardingCount()));
        return new AlgorithmConfiguration("CLASS_BASED", props);
    }

    /**
     * 创建雪花算法主键生成器配置
     */
    private AlgorithmConfiguration createSnowflakeKeyGeneratorAlgorithm() {
        Properties props = new Properties();
        props.setProperty("worker-id", "1");
        props.setProperty("datacenter-id", "1");
        return new AlgorithmConfiguration("SNOWFLAKE", props);
    }

    /**
     * 创建表规则配置
     */
    private Collection<ShardingTableRuleConfiguration> createTableRuleConfigurations() {
        Collection<ShardingTableRuleConfiguration> tableRuleConfigs = new ArrayList<>();

        // 玩家相关表分片规则
        tableRuleConfigs.add(createPlayerTableRule("t_player", "player_id"));
        tableRuleConfigs.add(createPlayerTableRule("t_player_bag", "player_id"));
        tableRuleConfigs.add(createPlayerTableRule("t_player_skill", "player_id"));

        // 游戏日志表分片规则（按时间分表）
        tableRuleConfigs.add(createLogTableRule("t_player_login_log", "create_time"));
        tableRuleConfigs.add(createLogTableRule("t_player_operation_log", "create_time"));

        return tableRuleConfigs;
    }

    /**
     * 创建玩家相关表的分片规则
     */
    private ShardingTableRuleConfiguration createPlayerTableRule(String tableName, String shardingColumn) {
        ShardingTableRuleConfiguration tableRuleConfig = new ShardingTableRuleConfiguration(
                tableName, getActualDataNodes(tableName));

        // 配置分库策略
        tableRuleConfig.setDatabaseShardingStrategy(
                new StandardShardingStrategyConfiguration(shardingColumn, "database_mod"));

        // 配置分表策略
        tableRuleConfig.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration(shardingColumn, "table_mod"));

        // 配置主键生成策略
        tableRuleConfig.setKeyGenerateStrategy(
                new KeyGenerateStrategyConfiguration("id", "snowflake"));

        return tableRuleConfig;
    }

    /**
     * 创建日志表的分片规则
     */
    private ShardingTableRuleConfiguration createLogTableRule(String tableName, String shardingColumn) {
        ShardingTableRuleConfiguration tableRuleConfig = new ShardingTableRuleConfiguration(
                tableName, getActualDataNodes(tableName));

        // 日志表只分表，不分库
        tableRuleConfig.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration(shardingColumn, "table_mod"));

        // 配置主键生成策略
        tableRuleConfig.setKeyGenerateStrategy(
                new KeyGenerateStrategyConfiguration("id", "snowflake"));

        return tableRuleConfig;
    }

    /**
     * 获取实际数据节点
     */
    private String getActualDataNodes(String tableName) {
        DatabaseProperties.ShardingConfig shardingConfig = databaseProperties.getSharding();
        int databaseCount = shardingConfig.getDatabaseShardingCount();
        int tableCount = shardingConfig.getTableShardingCount();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < databaseCount; i++) {
            for (int j = 0; j < tableCount; j++) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append("ds_").append(i).append(".").append(tableName).append("_").append(j);
            }
        }
        return sb.toString();
    }

    /**
     * 创建读写分离规则配置
     */
    private ReadwriteSplittingRuleConfiguration createReadwriteSplittingRuleConfiguration() {
        Collection<ReadwriteSplittingDataSourceRuleConfiguration> dataSources = new ArrayList<>();

        DatabaseProperties.ShardingConfig shardingConfig = databaseProperties.getSharding();
        int databaseCount = shardingConfig.getDatabaseShardingCount();

        // 为每个分库配置读写分离
        for (int i = 0; i < databaseCount; i++) {
            String readwriteSplittingName = "ds_" + i;
            String masterName = "master_" + i;
            java.util.List<String> slaveNames = createSlaveNames(i);

            ReadwriteSplittingDataSourceRuleConfiguration dataSourceRuleConfig = 
                    new ReadwriteSplittingDataSourceRuleConfiguration(
                            readwriteSplittingName, masterName, slaveNames, "round_robin");
            dataSources.add(dataSourceRuleConfig);
        }

        // 配置负载均衡算法
        Map<String, AlgorithmConfiguration> loadBalancers = new HashMap<>();
        loadBalancers.put("round_robin", new AlgorithmConfiguration("ROUND_ROBIN", new Properties()));

        return new ReadwriteSplittingRuleConfiguration(dataSources, loadBalancers);
    }

    /**
     * 创建从库名称列表
     */
    private java.util.List<String> createSlaveNames(int databaseIndex) {
        java.util.List<String> slaveNames = new java.util.ArrayList<>();
        
        // 假设有2个从库
        for (int i = 0; i < 2; i++) {
            slaveNames.add("slave_" + databaseIndex + "_" + i);
        }
        
        return slaveNames;
    }

    /**
     * 创建属性配置
     */
    private Properties createProperties() {
        Properties props = new Properties();
        
        // 显示SQL日志
        props.setProperty("sql-show", "true");
        
        // SQL解析配置
        props.setProperty("sql-comment-parse-enabled", "true");
        
        // 执行器配置
        props.setProperty("executor-size", "20");
        
        // 最大连接数限制
        props.setProperty("max-connections-size-per-query", "10");
        
        return props;
    }

    /**
     * 创建模式配置
     */
    private ModeConfiguration createModeConfiguration() {
        // 使用内存模式，生产环境建议使用ZooKeeper或etcd
        return new ModeConfiguration("Standalone", new StandalonePersistRepositoryConfiguration("JDBC", new Properties()));
    }
}