/*
 * 文件名: GameDatabaseShardingAlgorithm.java
 * 用途: 游戏数据库分片算法
 * 实现内容:
 *   - 实现数据库分片算法，按玩家ID取模分库
 *   - 支持精确分片和范围分片
 *   - 考虑游戏业务特点，确保分片均匀性
 *   - 支持复合分片键场景
 * 技术选型:
 *   - ShardingSphere StandardShardingAlgorithm
 *   - 取模算法保证分布均匀
 *   - 支持Long类型主键
 * 依赖关系:
 *   - 被ShardingConfig使用
 *   - 配合分表算法一起工作
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Properties;

/**
 * 游戏数据库分片算法
 * <p>
 * 基于玩家ID取模的数据库分片算法，确保玩家数据均匀分布到各个数据库。
 * 支持精确查询和范围查询的分片路由。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class GameDatabaseShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    private static final Logger logger = LoggerFactory.getLogger(GameDatabaseShardingAlgorithm.class);

    /**
     * 分片数量
     */
    private int shardingCount;

    @Override
    public void init(Properties props) {
        this.shardingCount = Integer.parseInt(props.getProperty("sharding-count", "2"));
        logger.info("初始化数据库分片算法，分片数量: {}", shardingCount);
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        Long value = shardingValue.getValue();
        String logicTableName = shardingValue.getLogicTableName();
        
        // 基于玩家ID取模计算分库
        int shardIndex = calculateShardIndex(value);
        String targetDataSource = findTargetDataSource(availableTargetNames, shardIndex);
        
        logger.debug("精确分片 - 表: {}, 分片键值: {}, 目标数据库: {}", 
                logicTableName, value, targetDataSource);
        
        return targetDataSource;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
        String logicTableName = shardingValue.getLogicTableName();
        Long lowerEndpoint = shardingValue.getValueRange().lowerEndpoint();
        Long upperEndpoint = shardingValue.getValueRange().upperEndpoint();
        
        // 范围查询需要涉及多个分片
        Collection<String> targetDataSources = calculateRangeTargetDataSources(
                availableTargetNames, lowerEndpoint, upperEndpoint);
        
        logger.debug("范围分片 - 表: {}, 范围: [{}, {}], 目标数据库: {}", 
                logicTableName, lowerEndpoint, upperEndpoint, targetDataSources);
        
        return targetDataSources;
    }

    /**
     * 计算分片索引
     *
     * @param value 分片键值
     * @return 分片索引
     */
    private int calculateShardIndex(Long value) {
        if (value == null) {
            logger.warn("分片键值为null，使用默认分片0");
            return 0;
        }

        // 使用绝对值确保非负数，然后取模
        int index = (int) (Math.abs(value) % shardingCount);
        
        logger.debug("分片键值: {}, 分片索引: {}", value, index);
        return index;
    }

    /**
     * 查找目标数据源
     *
     * @param availableTargetNames 可用目标名称集合
     * @param shardIndex 分片索引
     * @return 目标数据源名称
     */
    private String findTargetDataSource(Collection<String> availableTargetNames, int shardIndex) {
        String targetPattern = "_" + shardIndex;
        
        for (String targetName : availableTargetNames) {
            if (targetName.endsWith(targetPattern)) {
                return targetName;
            }
        }
        
        // 如果没有找到匹配的，返回第一个可用的
        String fallback = availableTargetNames.iterator().next();
        logger.warn("未找到分片索引{}对应的数据源，使用备用数据源: {}", shardIndex, fallback);
        return fallback;
    }

    /**
     * 计算范围查询的目标数据源
     *
     * @param availableTargetNames 可用目标名称集合
     * @param lowerEndpoint 下界
     * @param upperEndpoint 上界
     * @return 目标数据源集合
     */
    private Collection<String> calculateRangeTargetDataSources(
            Collection<String> availableTargetNames, Long lowerEndpoint, Long upperEndpoint) {
        
        java.util.Set<String> targetDataSources = new java.util.HashSet<>();
        
        // 计算范围内所有可能涉及的分片
        if (lowerEndpoint != null && upperEndpoint != null) {
            // 如果范围跨度很大，可能涉及所有分片
            if (upperEndpoint - lowerEndpoint >= shardingCount) {
                targetDataSources.addAll(availableTargetNames);
            } else {
                // 计算具体涉及的分片
                for (long value = lowerEndpoint; value <= upperEndpoint; value++) {
                    int shardIndex = calculateShardIndex(value);
                    String targetDataSource = findTargetDataSource(availableTargetNames, shardIndex);
                    targetDataSources.add(targetDataSource);
                }
            }
        } else {
            // 如果是开放区间，涉及所有分片
            targetDataSources.addAll(availableTargetNames);
        }
        
        return targetDataSources;
    }

    @Override
    public String getType() {
        return "GAME_DATABASE_MOD";
    }
}