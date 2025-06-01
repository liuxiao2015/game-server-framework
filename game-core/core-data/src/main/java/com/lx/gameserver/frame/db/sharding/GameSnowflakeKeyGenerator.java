/*
 * 文件名: GameSnowflakeKeyGenerator.java
 * 用途: 游戏分布式ID生成器
 * 实现内容:
 *   - 基于雪花算法的分布式ID生成器
 *   - 保证ID的有序性和唯一性
 *   - 支持多数据中心部署
 *   - 针对游戏业务优化时间戳精度
 * 技术选型:
 *   - 雪花算法改进版本
 *   - 毫秒级时间戳
 *   - 机器码和数据中心码
 * 依赖关系:
 *   - 被ShardingConfig使用
 *   - 为分片表提供主键生成
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.sharding;

import org.apache.shardingsphere.sharding.spi.KeyGenerateAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏雪花算法ID生成器
 * <p>
 * 基于Twitter雪花算法的分布式ID生成器，适用于游戏服务器的高并发场景。
 * 生成的ID具有趋势递增、全局唯一、高性能等特点。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class GameSnowflakeKeyGenerator implements KeyGenerateAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(GameSnowflakeKeyGenerator.class);

    /**
     * 开始时间戳 (2023-01-01 00:00:00)
     */
    private static final long START_TIMESTAMP = 1672531200000L;

    /**
     * 序列号占用位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 机器码占用位数
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 数据中心码占用位数
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * 序列号最大值
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /**
     * 机器码最大值
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 数据中心码最大值
     */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * 机器码左移位数
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 数据中心码左移位数
     */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 时间戳左移位数
     */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /**
     * 机器码
     */
    private long workerId;

    /**
     * 数据中心码
     */
    private long datacenterId;

    /**
     * 序列号
     */
    private final AtomicLong sequence = new AtomicLong(0L);

    /**
     * 上次生成ID的时间戳
     */
    private volatile long lastTimestamp = -1L;

    /**
     * 同步锁
     */
    private final Object lock = new Object();

    @Override
    public void init(Properties props) {
        this.workerId = getLongProperty(props, "worker-id", 1L);
        this.datacenterId = getLongProperty(props, "datacenter-id", 1L);

        // 验证参数有效性
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("机器码必须在0-%d之间，当前值: %d", MAX_WORKER_ID, workerId));
        }

        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("数据中心码必须在0-%d之间，当前值: %d", MAX_DATACENTER_ID, datacenterId));
        }

        logger.info("雪花算法ID生成器初始化完成 - 机器码: {}, 数据中心码: {}", workerId, datacenterId);
    }

    @Override
    public Comparable<?> generateKey() {
        return nextId();
    }

    /**
     * 生成下一个ID
     *
     * @return 生成的ID
     */
    public long nextId() {
        synchronized (lock) {
            long timestamp = getCurrentTimestamp();

            // 检查时钟回拨
            if (timestamp < lastTimestamp) {
                long offset = lastTimestamp - timestamp;
                if (offset <= 5) {
                    // 小幅回拨，等待时钟追上
                    try {
                        wait(offset << 1);
                        timestamp = getCurrentTimestamp();
                        if (timestamp < lastTimestamp) {
                            throw new RuntimeException(
                                    String.format("时钟回拨异常，当前时间: %d, 上次时间: %d", timestamp, lastTimestamp));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("等待时钟同步被中断", e);
                    }
                } else {
                    throw new RuntimeException(
                            String.format("严重时钟回拨，当前时间: %d, 上次时间: %d, 回拨: %dms", 
                                    timestamp, lastTimestamp, offset));
                }
            }

            // 同一毫秒内生成多个ID
            if (timestamp == lastTimestamp) {
                long currentSequence = sequence.incrementAndGet();
                if (currentSequence > MAX_SEQUENCE) {
                    // 序列号溢出，等待下一毫秒
                    timestamp = waitNextMillis(lastTimestamp);
                    sequence.set(0L);
                    currentSequence = 0L;
                }
            } else {
                // 新的毫秒，序列号重置为0
                sequence.set(0L);
            }

            lastTimestamp = timestamp;

            // 生成ID
            long id = ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence.get();

            logger.debug("生成ID: {}, 时间戳: {}, 数据中心: {}, 机器: {}, 序列: {}", 
                    id, timestamp, datacenterId, workerId, sequence.get());

            return id;
        }
    }

    /**
     * 等待下一毫秒
     *
     * @param lastTimestamp 上次时间戳
     * @return 新的时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 从属性中获取Long值
     *
     * @param props 属性配置
     * @param key 键名
     * @param defaultValue 默认值
     * @return Long值
     */
    private long getLongProperty(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("属性{}的值{}无法解析为数字，使用默认值{}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 解析ID中的时间戳
     *
     * @param id 雪花算法生成的ID
     * @return 时间戳
     */
    public long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
    }

    /**
     * 解析ID中的数据中心码
     *
     * @param id 雪花算法生成的ID
     * @return 数据中心码
     */
    public long parseDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & (~(-1L << DATACENTER_ID_BITS));
    }

    /**
     * 解析ID中的机器码
     *
     * @param id 雪花算法生成的ID
     * @return 机器码
     */
    public long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & (~(-1L << WORKER_ID_BITS));
    }

    /**
     * 解析ID中的序列号
     *
     * @param id 雪花算法生成的ID
     * @return 序列号
     */
    public long parseSequence(long id) {
        return id & (~(-1L << SEQUENCE_BITS));
    }

    @Override
    public String getType() {
        return "GAME_SNOWFLAKE";
    }

    /**
     * 获取机器码
     *
     * @return 机器码
     */
    public long getWorkerId() {
        return workerId;
    }

    /**
     * 获取数据中心码
     *
     * @return 数据中心码
     */
    public long getDatacenterId() {
        return datacenterId;
    }
}