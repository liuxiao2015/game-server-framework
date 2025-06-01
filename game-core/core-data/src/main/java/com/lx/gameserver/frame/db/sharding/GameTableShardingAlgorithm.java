/*
 * 文件名: GameTableShardingAlgorithm.java
 * 用途: 游戏表分片算法
 * 实现内容:
 *   - 实现表分片算法，支持按ID/时间分表
 *   - 支持精确分片和范围分片
 *   - 针对不同业务场景优化分片策略
 *   - 支持日志表按时间分表
 * 技术选型:
 *   - ShardingSphere StandardShardingAlgorithm
 *   - 灵活的分片策略支持
 *   - 时间和ID混合分片
 * 依赖关系:
 *   - 被ShardingConfig使用
 *   - 配合数据库分片算法工作
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Properties;

/**
 * 游戏表分片算法
 * <p>
 * 支持多种分片策略的表分片算法，包括按ID取模、按时间分表等。
 * 根据不同的业务表特点选择合适的分片策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class GameTableShardingAlgorithm implements StandardShardingAlgorithm<Comparable<?>> {

    private static final Logger logger = LoggerFactory.getLogger(GameTableShardingAlgorithm.class);

    /**
     * 分片数量
     */
    private int shardingCount;

    /**
     * 分片策略：mod(取模) 或 time(时间)
     */
    private String shardingStrategy;

    /**
     * 时间格式
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    public void init(Properties props) {
        this.shardingCount = Integer.parseInt(props.getProperty("sharding-count", "4"));
        this.shardingStrategy = props.getProperty("sharding-strategy", "mod");
        logger.info("初始化表分片算法，分片数量: {}, 分片策略: {}", shardingCount, shardingStrategy);
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Comparable<?>> shardingValue) {
        Comparable<?> value = shardingValue.getValue();
        String logicTableName = shardingValue.getLogicTableName();
        String columnName = shardingValue.getColumnName();
        
        int shardIndex;
        if ("time".equals(shardingStrategy) && value instanceof LocalDateTime) {
            // 按时间分片
            shardIndex = calculateTimeShardIndex((LocalDateTime) value);
        } else if (value instanceof Number) {
            // 按数字ID分片
            shardIndex = calculateModShardIndex(((Number) value).longValue());
        } else {
            // 默认策略
            shardIndex = calculateHashShardIndex(value);
        }
        
        String targetTable = findTargetTable(availableTargetNames, logicTableName, shardIndex);
        
        logger.debug("精确分片 - 表: {}, 列: {}, 值: {}, 目标表: {}", 
                logicTableName, columnName, value, targetTable);
        
        return targetTable;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Comparable<?>> shardingValue) {
        String logicTableName = shardingValue.getLogicTableName();
        String columnName = shardingValue.getColumnName();
        Comparable<?> lowerEndpoint = shardingValue.getValueRange().lowerEndpoint();
        Comparable<?> upperEndpoint = shardingValue.getValueRange().upperEndpoint();
        
        Collection<String> targetTables;
        
        if ("time".equals(shardingStrategy) && lowerEndpoint instanceof LocalDateTime) {
            // 按时间范围分片
            targetTables = calculateTimeRangeTargetTables(
                    availableTargetNames, logicTableName, 
                    (LocalDateTime) lowerEndpoint, (LocalDateTime) upperEndpoint);
        } else if (lowerEndpoint instanceof Number && upperEndpoint instanceof Number) {
            // 按数字范围分片
            targetTables = calculateModRangeTargetTables(
                    availableTargetNames, logicTableName,
                    ((Number) lowerEndpoint).longValue(), ((Number) upperEndpoint).longValue());
        } else {
            // 默认返回所有相关表
            targetTables = filterTablesByLogicName(availableTargetNames, logicTableName);
        }
        
        logger.debug("范围分片 - 表: {}, 列: {}, 范围: [{}, {}], 目标表: {}", 
                logicTableName, columnName, lowerEndpoint, upperEndpoint, targetTables);
        
        return targetTables;
    }

    /**
     * 计算按模取分片索引
     *
     * @param value ID值
     * @return 分片索引
     */
    private int calculateModShardIndex(Long value) {
        if (value == null) {
            logger.warn("分片键值为null，使用默认分片0");
            return 0;
        }

        int index = (int) (Math.abs(value) % shardingCount);
        logger.debug("按模分片 - 值: {}, 分片索引: {}", value, index);
        return index;
    }

    /**
     * 计算按时间分片索引
     *
     * @param dateTime 时间值
     * @return 分片索引
     */
    private int calculateTimeShardIndex(LocalDateTime dateTime) {
        if (dateTime == null) {
            logger.warn("时间分片键值为null，使用默认分片0");
            return 0;
        }

        // 按年月计算分片，例如202301对应分片1，202302对应分片2
        String monthStr = dateTime.format(DATE_FORMATTER);
        int monthValue = Integer.parseInt(monthStr);
        int index = monthValue % shardingCount;
        
        logger.debug("按时间分片 - 时间: {}, 月份值: {}, 分片索引: {}", dateTime, monthValue, index);
        return index;
    }

    /**
     * 计算哈希分片索引
     *
     * @param value 值
     * @return 分片索引
     */
    private int calculateHashShardIndex(Comparable<?> value) {
        if (value == null) {
            logger.warn("分片键值为null，使用默认分片0");
            return 0;
        }

        int index = Math.abs(value.hashCode()) % shardingCount;
        logger.debug("按哈希分片 - 值: {}, 哈希值: {}, 分片索引: {}", value, value.hashCode(), index);
        return index;
    }

    /**
     * 查找目标表
     *
     * @param availableTargetNames 可用目标表名称集合
     * @param logicTableName 逻辑表名
     * @param shardIndex 分片索引
     * @return 目标表名
     */
    private String findTargetTable(Collection<String> availableTargetNames, String logicTableName, int shardIndex) {
        String targetPattern = logicTableName + "_" + shardIndex;
        
        for (String targetName : availableTargetNames) {
            if (targetName.equals(targetPattern)) {
                return targetName;
            }
        }
        
        // 如果没有找到精确匹配，查找包含逻辑表名和索引的表
        for (String targetName : availableTargetNames) {
            if (targetName.contains(logicTableName) && targetName.endsWith("_" + shardIndex)) {
                return targetName;
            }
        }
        
        // 如果还是没找到，返回第一个包含逻辑表名的表
        for (String targetName : availableTargetNames) {
            if (targetName.contains(logicTableName)) {
                logger.warn("未找到分片索引{}对应的表，使用备用表: {}", shardIndex, targetName);
                return targetName;
            }
        }
        
        // 最后的备用方案
        String fallback = availableTargetNames.iterator().next();
        logger.warn("未找到逻辑表{}对应的任何物理表，使用第一个可用表: {}", logicTableName, fallback);
        return fallback;
    }

    /**
     * 计算按模取范围的目标表
     */
    private Collection<String> calculateModRangeTargetTables(
            Collection<String> availableTargetNames, String logicTableName, Long lower, Long upper) {
        
        java.util.Set<String> targetTables = new java.util.HashSet<>();
        
        // 如果范围跨度很大，涉及所有分片
        if (upper - lower >= shardingCount) {
            targetTables.addAll(filterTablesByLogicName(availableTargetNames, logicTableName));
        } else {
            // 计算具体涉及的分片
            for (long value = lower; value <= upper; value++) {
                int shardIndex = calculateModShardIndex(value);
                String targetTable = findTargetTable(availableTargetNames, logicTableName, shardIndex);
                targetTables.add(targetTable);
            }
        }
        
        return targetTables;
    }

    /**
     * 计算按时间范围的目标表
     */
    private Collection<String> calculateTimeRangeTargetTables(
            Collection<String> availableTargetNames, String logicTableName, 
            LocalDateTime lower, LocalDateTime upper) {
        
        java.util.Set<String> targetTables = new java.util.HashSet<>();
        
        if (lower != null && upper != null) {
            // 按月遍历时间范围
            LocalDateTime current = lower.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime end = upper.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            
            while (!current.isAfter(end)) {
                int shardIndex = calculateTimeShardIndex(current);
                String targetTable = findTargetTable(availableTargetNames, logicTableName, shardIndex);
                targetTables.add(targetTable);
                current = current.plusMonths(1);
            }
        } else {
            // 如果是开放区间，涉及所有相关表
            targetTables.addAll(filterTablesByLogicName(availableTargetNames, logicTableName));
        }
        
        return targetTables;
    }

    /**
     * 过滤出属于指定逻辑表的物理表
     */
    private Collection<String> filterTablesByLogicName(Collection<String> availableTargetNames, String logicTableName) {
        return availableTargetNames.stream()
                .filter(name -> name.contains(logicTableName))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String getType() {
        return "GAME_TABLE_SHARDING";
    }
}