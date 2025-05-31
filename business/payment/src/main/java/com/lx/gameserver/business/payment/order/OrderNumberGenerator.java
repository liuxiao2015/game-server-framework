/*
 * 文件名: OrderNumberGenerator.java
 * 用途: 订单号生成器
 * 实现内容:
 *   - 分布式ID生成（雪花算法优化版）
 *   - 业务前缀支持和定制化
 *   - 时间戳嵌入和可读性
 *   - 防猜测设计和安全性
 *   - 批量生成优化和性能提升
 * 技术选型:
 *   - 雪花算法改进版
 *   - 时间戳编码
 *   - 随机数混合
 *   - 线程安全设计
 * 依赖关系:
 *   - 被OrderService使用
 *   - 独立组件无外部依赖
 *   - 支持分布式部署
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.order;

import com.lx.gameserver.business.payment.core.PaymentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单号生成器
 * <p>
 * 基于雪花算法的改进版本，生成全局唯一、有序、
 * 可读性强的订单号，支持业务前缀和防猜测设计。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class OrderNumberGenerator {

    private static final Logger logger = LoggerFactory.getLogger(OrderNumberGenerator.class);

    /**
     * 业务前缀枚举
     */
    public enum BusinessPrefix {
        /** 游戏充值 */
        GAME_RECHARGE("GM"),
        /** 商品购买 */
        PRODUCT_BUY("PD"),
        /** 服务费用 */
        SERVICE_FEE("SF"),
        /** 订阅服务 */
        SUBSCRIPTION("SB"),
        /** 其他 */
        OTHER("OT");

        private final String code;

        BusinessPrefix(String code) {
            this.code = code;
        }

        public String getCode() { return code; }
    }

    // ========== 雪花算法相关常量 ==========

    /**
     * 开始时间戳 (2024-01-01 00:00:00)
     */
    private static final long START_TIMESTAMP = 1704067200000L;

    /**
     * 机器ID位数
     */
    private static final long MACHINE_ID_BITS = 5L;

    /**
     * 数据中心ID位数
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * 序列号位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 机器ID最大值
     */
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);

    /**
     * 数据中心ID最大值
     */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * 序列号最大值
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

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
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;

    // ========== 实例变量 ==========

    /**
     * 机器ID
     */
    private final long machineId;

    /**
     * 数据中心ID
     */
    private final long datacenterId;

    /**
     * 序列号
     */
    private final AtomicLong sequence = new AtomicLong(0L);

    /**
     * 上次生成ID的时间戳
     */
    private volatile long lastTimestamp = -1L;

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 构造函数
     */
    public OrderNumberGenerator() {
        // 从系统属性或环境变量获取机器ID和数据中心ID
        this.machineId = getMachineId();
        this.datacenterId = getDatacenterId();
        
        logger.info("订单号生成器初始化完成: 机器ID={}, 数据中心ID={}", machineId, datacenterId);
    }

    /**
     * 生成订单号
     *
     * @param paymentContext 支付上下文
     * @return 订单号
     */
    public String generateOrderNumber(PaymentContext paymentContext) {
        try {
            // 确定业务前缀
            String prefix = determineBusinessPrefix(paymentContext);
            
            // 生成雪花ID
            long snowflakeId = generateSnowflakeId();
            
            // 生成时间部分
            String timePart = LocalDateTime.now().format(TIME_FORMATTER);
            
            // 生成随机部分（防猜测）
            String randomPart = generateRandomPart();
            
            // 组装订单号: 前缀 + 时间 + 雪花ID后6位 + 随机数
            String orderId = String.format("%s%s%06d%s", 
                    prefix, 
                    timePart, 
                    snowflakeId % 1000000, 
                    randomPart);
            
            logger.debug("生成订单号: {} (前缀={}, 雪花ID={})", orderId, prefix, snowflakeId);
            return orderId;
            
        } catch (Exception e) {
            logger.error("生成订单号失败", e);
            // 降级方案：使用时间戳+随机数
            return generateFallbackOrderNumber();
        }
    }

    /**
     * 批量生成订单号
     *
     * @param count 生成数量
     * @param prefix 业务前缀
     * @return 订单号数组
     */
    public String[] generateBatchOrderNumbers(int count, BusinessPrefix prefix) {
        if (count <= 0 || count > 1000) {
            throw new IllegalArgumentException("批量生成数量必须在1-1000之间");
        }

        String[] orderNumbers = new String[count];
        String prefixCode = prefix.getCode();
        String timePart = LocalDateTime.now().format(TIME_FORMATTER);

        for (int i = 0; i < count; i++) {
            try {
                long snowflakeId = generateSnowflakeId();
                String randomPart = generateRandomPart();
                
                orderNumbers[i] = String.format("%s%s%06d%s", 
                        prefixCode, 
                        timePart, 
                        snowflakeId % 1000000, 
                        randomPart);
                        
            } catch (Exception e) {
                logger.error("批量生成订单号失败: 索引={}", i, e);
                orderNumbers[i] = generateFallbackOrderNumber();
            }
        }

        logger.info("批量生成订单号完成: 数量={}, 前缀={}", count, prefixCode);
        return orderNumbers;
    }

    /**
     * 验证订单号格式
     *
     * @param orderId 订单号
     * @return 是否有效
     */
    public boolean validateOrderNumber(String orderId) {
        if (orderId == null || orderId.length() < 20) {
            return false;
        }

        try {
            // 检查前缀
            String prefix = orderId.substring(0, 2);
            boolean validPrefix = false;
            for (BusinessPrefix bp : BusinessPrefix.values()) {
                if (bp.getCode().equals(prefix)) {
                    validPrefix = true;
                    break;
                }
            }
            if (!validPrefix) {
                return false;
            }

            // 检查时间部分（14位数字）
            String timePart = orderId.substring(2, 16);
            if (!timePart.matches("\\d{14}")) {
                return false;
            }

            // 验证时间是否合理
            LocalDateTime.parse(timePart, TIME_FORMATTER);

            return true;

        } catch (Exception e) {
            logger.debug("订单号格式验证失败: {}", orderId, e);
            return false;
        }
    }

    /**
     * 从订单号提取时间
     *
     * @param orderId 订单号
     * @return 创建时间
     */
    public LocalDateTime extractTimeFromOrderNumber(String orderId) {
        if (!validateOrderNumber(orderId)) {
            return null;
        }

        try {
            String timePart = orderId.substring(2, 16);
            return LocalDateTime.parse(timePart, TIME_FORMATTER);
        } catch (Exception e) {
            logger.debug("从订单号提取时间失败: {}", orderId, e);
            return null;
        }
    }

    /**
     * 从订单号提取业务前缀
     *
     * @param orderId 订单号
     * @return 业务前缀
     */
    public BusinessPrefix extractBusinessPrefix(String orderId) {
        if (orderId == null || orderId.length() < 2) {
            return null;
        }

        String prefix = orderId.substring(0, 2);
        for (BusinessPrefix bp : BusinessPrefix.values()) {
            if (bp.getCode().equals(prefix)) {
                return bp;
            }
        }

        return null;
    }

    // ========== 私有方法 ==========

    /**
     * 确定业务前缀
     */
    private String determineBusinessPrefix(PaymentContext context) {
        // 根据支付上下文确定业务类型
        if (context.getBusinessScene() != null) {
            return switch (context.getBusinessScene().toLowerCase()) {
                case "recharge", "充值" -> BusinessPrefix.GAME_RECHARGE.getCode();
                case "subscription", "订阅" -> BusinessPrefix.SUBSCRIPTION.getCode();
                case "service", "服务" -> BusinessPrefix.SERVICE_FEE.getCode();
                default -> BusinessPrefix.PRODUCT_BUY.getCode();
            };
        }

        // 默认商品购买
        return BusinessPrefix.PRODUCT_BUY.getCode();
    }

    /**
     * 生成雪花ID
     */
    private synchronized long generateSnowflakeId() {
        long timestamp = getCurrentTimestamp();

        // 时钟回拨处理
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 小幅回拨，等待时钟追上
                try {
                    Thread.sleep(offset << 1);
                    timestamp = getCurrentTimestamp();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException("时钟回拨异常");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("等待时钟同步被中断", e);
                }
            } else {
                throw new RuntimeException("时钟回拨异常: " + offset + "ms");
            }
        }

        // 同一毫秒内，序列号递增
        if (lastTimestamp == timestamp) {
            long seq = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (seq == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = waitForNextMillis(timestamp);
                sequence.set(0);
            }
        } else {
            // 新的毫秒，序列号重置
            sequence.set(0);
        }

        lastTimestamp = timestamp;

        // 组装雪花ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_LEFT_SHIFT) |
               (datacenterId << DATACENTER_ID_SHIFT) |
               (machineId << MACHINE_ID_SHIFT) |
               sequence.get();
    }

    /**
     * 生成随机部分
     */
    private String generateRandomPart() {
        // 生成4位随机数字
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.valueOf(random);
    }

    /**
     * 生成降级订单号
     */
    private String generateFallbackOrderNumber() {
        String timePart = LocalDateTime.now().format(TIME_FORMATTER);
        long randomPart = ThreadLocalRandom.current().nextLong(100000, 999999);
        return String.format("FB%s%06d", timePart, randomPart);
    }

    /**
     * 获取当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 等待下一毫秒
     */
    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    /**
     * 获取机器ID
     */
    private long getMachineId() {
        String machineIdStr = System.getProperty("machine.id", System.getenv("MACHINE_ID"));
        if (machineIdStr != null) {
            try {
                long id = Long.parseLong(machineIdStr);
                if (id >= 0 && id <= MAX_MACHINE_ID) {
                    return id;
                }
            } catch (NumberFormatException e) {
                logger.warn("无效的机器ID配置: {}", machineIdStr);
            }
        }

        // 默认使用本机网络地址生成
        try {
            String hostAddress = java.net.InetAddress.getLocalHost().getHostAddress();
            return Math.abs(hostAddress.hashCode()) % (MAX_MACHINE_ID + 1);
        } catch (Exception e) {
            logger.warn("获取机器ID失败，使用随机值", e);
            return ThreadLocalRandom.current().nextLong(0, MAX_MACHINE_ID + 1);
        }
    }

    /**
     * 获取数据中心ID
     */
    private long getDatacenterId() {
        String datacenterIdStr = System.getProperty("datacenter.id", System.getenv("DATACENTER_ID"));
        if (datacenterIdStr != null) {
            try {
                long id = Long.parseLong(datacenterIdStr);
                if (id >= 0 && id <= MAX_DATACENTER_ID) {
                    return id;
                }
            } catch (NumberFormatException e) {
                logger.warn("无效的数据中心ID配置: {}", datacenterIdStr);
            }
        }

        // 默认使用主机名生成
        try {
            String hostName = java.net.InetAddress.getLocalHost().getHostName();
            return Math.abs(hostName.hashCode()) % (MAX_DATACENTER_ID + 1);
        } catch (Exception e) {
            logger.warn("获取数据中心ID失败，使用随机值", e);
            return ThreadLocalRandom.current().nextLong(0, MAX_DATACENTER_ID + 1);
        }
    }

    /**
     * 获取生成器状态信息
     */
    public String getGeneratorInfo() {
        return String.format("OrderNumberGenerator{machineId=%d, datacenterId=%d, lastTimestamp=%d, sequence=%d}",
                machineId, datacenterId, lastTimestamp, sequence.get());
    }
}