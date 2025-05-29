/*
 * 文件名: IdGenerator.java
 * 用途: 基于雪花算法的全局唯一ID生成器
 * 实现内容:
 *   - 实现Snowflake雪花算法生成唯一ID
 *   - 支持多机房、多机器部署
 *   - 保证ID的唯一性和有序性
 *   - 提供高性能的ID生成服务
 * 技术选型:
 *   - 雪花算法: 64位长整型ID结构
 *   - 时间戳(41位) + 机房ID(5位) + 机器ID(5位) + 序列号(12位)
 *   - 使用synchronized保证线程安全
 *   - 支持时钟回拨检测和处理
 * 依赖关系:
 *   - 无外部依赖，纯算法实现
 *   - 被需要生成唯一ID的模块使用
 */
package com.lx.gameserver.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于雪花算法的全局唯一ID生成器
 * <p>
 * 雪花算法是Twitter开源的分布式ID生成算法，生成64位的长整型ID。
 * ID结构：1位符号位 + 41位时间戳 + 5位数据中心ID + 5位机器ID + 12位序列号
 * 
 * 特点：
 * - 高性能：每秒可生成约409万个ID
 * - 有序性：同一机器生成的ID按时间递增
 * - 唯一性：不同机器生成的ID不会重复
 * - 可扩展：支持32个数据中心，每个数据中心支持32台机器
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public class IdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(IdGenerator.class);

    // ===== 雪花算法常量定义 =====
    
    /**
     * 开始时间戳 (2020-01-01 00:00:00)
     */
    private static final long START_TIMESTAMP = 1577808000000L;

    /**
     * 序列号占用位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 机器ID占用位数
     */
    private static final long MACHINE_ID_BITS = 5L;

    /**
     * 数据中心ID占用位数
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * 序列号最大值
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /**
     * 机器ID最大值
     */
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);

    /**
     * 数据中心ID最大值
     */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * 机器ID左移位数
     */
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 数据中心ID左移位数
     */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    /**
     * 时间戳左移位数
     */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;

    // ===== 实例变量 =====

    /**
     * 数据中心ID
     */
    private final long datacenterId;

    /**
     * 机器ID
     */
    private final long machineId;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上一次生成ID的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 单例实例
     */
    private static volatile IdGenerator instance;

    /**
     * 构造函数
     *
     * @param datacenterId 数据中心ID (0-31)
     * @param machineId    机器ID (0-31)
     */
    public IdGenerator(long datacenterId, long machineId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    String.format("数据中心ID必须在0到%d之间", MAX_DATACENTER_ID));
        }
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    String.format("机器ID必须在0到%d之间", MAX_MACHINE_ID));
        }
        
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        
        logger.info("IdGenerator初始化完成 - 数据中心ID: {}, 机器ID: {}", datacenterId, machineId);
    }

    /**
     * 获取单例实例（使用默认ID）
     *
     * @return IdGenerator实例
     */
    public static IdGenerator getInstance() {
        if (instance == null) {
            synchronized (IdGenerator.class) {
                if (instance == null) {
                    // 默认使用数据中心ID=1, 机器ID=1
                    instance = new IdGenerator(1, 1);
                }
            }
        }
        return instance;
    }

    /**
     * 获取单例实例（指定ID）
     *
     * @param datacenterId 数据中心ID
     * @param machineId    机器ID
     * @return IdGenerator实例
     */
    public static IdGenerator getInstance(long datacenterId, long machineId) {
        if (instance == null) {
            synchronized (IdGenerator.class) {
                if (instance == null) {
                    instance = new IdGenerator(datacenterId, machineId);
                }
            }
        }
        return instance;
    }

    /**
     * 生成下一个ID
     *
     * @return 全局唯一ID
     */
    public synchronized long nextId() {
        long timestamp = getCurrentTimestamp();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 时钟回拨在5ms内，等待
                try {
                    Thread.sleep(offset << 1);
                    timestamp = getCurrentTimestamp();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException(String.format("时钟回拨过多，拒绝生成ID。当前时间戳: %d, 上次时间戳: %d", 
                                timestamp, lastTimestamp));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("等待时钟同步时被中断", e);
                }
            } else {
                throw new RuntimeException(String.format("时钟回拨过多，拒绝生成ID。当前时间戳: %d, 上次时间戳: %d", 
                        timestamp, lastTimestamp));
            }
        }

        // 同一毫秒内生成多个ID
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号用完，等待下一毫秒
                timestamp = getNextTimestamp(lastTimestamp);
            }
        } else {
            // 新的毫秒，序列号重置为0
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 生成ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    /**
     * 批量生成ID
     *
     * @param count 生成数量
     * @return ID数组
     */
    public long[] nextIds(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("生成数量必须大于0");
        }
        if (count > 10000) {
            throw new IllegalArgumentException("单次生成数量不能超过10000");
        }

        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = nextId();
        }
        return ids;
    }

    /**
     * 解析ID获取时间戳
     *
     * @param id 雪花ID
     * @return 时间戳（毫秒）
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
    }

    /**
     * 解析ID获取数据中心ID
     *
     * @param id 雪花ID
     * @return 数据中心ID
     */
    public static long parseDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 解析ID获取机器ID
     *
     * @param id 雪花ID
     * @return 机器ID
     */
    public static long parseMachineId(long id) {
        return (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }

    /**
     * 解析ID获取序列号
     *
     * @param id 雪花ID
     * @return 序列号
     */
    public static long parseSequence(long id) {
        return id & MAX_SEQUENCE;
    }

    /**
     * 解析ID获取详细信息
     *
     * @param id 雪花ID
     * @return ID信息字符串
     */
    public static String parseIdInfo(long id) {
        long timestamp = parseTimestamp(id);
        long datacenterId = parseDatacenterId(id);
        long machineId = parseMachineId(id);
        long sequence = parseSequence(id);
        
        return String.format("ID: %d, 时间戳: %d, 数据中心: %d, 机器: %d, 序列号: %d",
                id, timestamp, datacenterId, machineId, sequence);
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳（毫秒）
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 获取下一毫秒的时间戳
     *
     * @param lastTimestamp 上一次时间戳
     * @return 下一毫秒时间戳
     */
    private long getNextTimestamp(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    /**
     * 获取生成器信息
     *
     * @return 生成器信息
     */
    public String getGeneratorInfo() {
        return String.format("IdGenerator{数据中心ID: %d, 机器ID: %d, 当前序列号: %d, 上次时间戳: %d}",
                datacenterId, machineId, sequence, lastTimestamp);
    }

    /**
     * 获取算法统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("雪花算法统计信息:\n");
        sb.append(String.format("  - 开始时间戳: %d\n", START_TIMESTAMP));
        sb.append(String.format("  - 最大序列号: %d (每毫秒可生成%d个ID)\n", MAX_SEQUENCE, MAX_SEQUENCE + 1));
        sb.append(String.format("  - 最大数据中心数: %d\n", MAX_DATACENTER_ID + 1));
        sb.append(String.format("  - 最大机器数/数据中心: %d\n", MAX_MACHINE_ID + 1));
        sb.append(String.format("  - 理论最大QPS: %d\n", (MAX_SEQUENCE + 1) * 1000));
        sb.append(String.format("  - 当前配置: 数据中心%d, 机器%d\n", datacenterId, machineId));
        return sb.toString();
    }

    // ===== Getter 方法 =====

    /**
     * 获取数据中心ID
     *
     * @return 数据中心ID
     */
    public long getDatacenterId() {
        return datacenterId;
    }

    /**
     * 获取机器ID
     *
     * @return 机器ID
     */
    public long getMachineId() {
        return machineId;
    }

    /**
     * 获取当前序列号
     *
     * @return 当前序列号
     */
    public synchronized long getCurrentSequence() {
        return sequence;
    }

    /**
     * 获取上次时间戳
     *
     * @return 上次时间戳
     */
    public synchronized long getLastTimestamp() {
        return lastTimestamp;
    }
}