/*
 * 文件名: ChatConfig.java
 * 用途: 聊天模块配置类
 * 实现内容:
 *   - 聊天系统的全局配置管理
 *   - 连接配置（WebSocket、TCP端口等）
 *   - 频道配置（各类型频道的参数）
 *   - 消息配置（长度、历史保留等）
 *   - 安全配置（敏感词、限流等）
 *   - 存储配置（数据库、缓存等）
 *   - 监控配置（指标、告警等）
 * 技术选型:
 *   - Spring Boot Configuration Properties
 *   - 支持动态配置刷新
 *   - 分层配置结构便于管理
 * 依赖关系:
 *   - 被所有聊天服务组件使用
 *   - 支持外部配置文件覆盖
 *   - 提供配置验证和默认值
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 聊天模块配置类
 * <p>
 * 管理聊天系统的所有配置项，包括连接配置、频道配置、
 * 消息配置、安全配置、存储配置和监控配置等。
 * 支持通过application.yml进行外部配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@Component
@ConfigurationProperties(prefix = "game.chat")
public class ChatConfig {

    /**
     * 连接配置
     */
    private ConnectionConfig connection = new ConnectionConfig();

    /**
     * 频道配置
     */
    private ChannelsConfig channels = new ChannelsConfig();

    /**
     * 消息配置
     */
    private MessageConfig message = new MessageConfig();

    /**
     * 安全配置
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * 存储配置
     */
    private StorageConfig storage = new StorageConfig();

    /**
     * 监控配置
     */
    private MonitorConfig monitor = new MonitorConfig();

    // ===== 内部配置类定义 =====

    /**
     * 连接配置
     */
    @Data
    public static class ConnectionConfig {
        /** WebSocket端口 */
        private int websocketPort = 8080;

        /** TCP端口 */
        private int tcpPort = 9090;

        /** 最大连接数 */
        private int maxConnections = 10000;

        /** 心跳间隔 */
        private Duration heartbeatInterval = Duration.ofSeconds(30);

        /** 空闲超时 */
        private Duration idleTimeout = Duration.ofMinutes(5);

        /** 连接缓冲区大小 */
        private int bufferSize = 8192;

        /** 是否启用压缩 */
        private boolean enableCompression = true;

        /** 是否启用SSL */
        private boolean enableSsl = false;

        /** SSL证书路径 */
        private String sslCertPath;

        /** SSL私钥路径 */
        private String sslKeyPath;
    }

    /**
     * 频道配置
     */
    @Data
    public static class ChannelsConfig {
        /** 世界频道配置 */
        private ChannelConfig world = new ChannelConfig(true, -1, Duration.ofSeconds(5), 200);

        /** 公会频道配置 */
        private ChannelConfig guild = new ChannelConfig(true, 500, Duration.ofSeconds(2), 500);

        /** 队伍频道配置 */
        private ChannelConfig team = new ChannelConfig(true, 20, Duration.ofSeconds(1), 300);

        /** 私聊频道配置 */
        private ChannelConfig privateChat = new ChannelConfig(true, 2, Duration.ofSeconds(1), 1000);

        /** 系统频道配置 */
        private ChannelConfig system = new ChannelConfig(true, -1, Duration.ZERO, 500);

        /** 自定义频道配置 */
        private Map<String, ChannelConfig> custom = new HashMap<>();
    }

    /**
     * 单个频道配置
     */
    @Data
    public static class ChannelConfig {
        /** 是否启用 */
        private boolean enabled = true;

        /** 最大成员数（-1为无限制） */
        private int maxMembers = -1;

        /** 消息发送间隔 */
        private Duration messageInterval = Duration.ofSeconds(5);

        /** 最大消息长度 */
        private int maxMessageLength = 500;

        /** 是否需要权限 */
        private boolean requirePermission = false;

        /** 是否允许匿名 */
        private boolean allowAnonymous = false;

        /** 历史消息保留天数 */
        private int historyRetentionDays = 7;

        /** 离线消息过期天数 */
        private int offlineExpireDays = 3;

        /** 频道公告 */
        private String announcement;

        /** 扩展配置 */
        private Map<String, Object> extra = new HashMap<>();

        public ChannelConfig() {}

        public ChannelConfig(boolean enabled, int maxMembers, Duration messageInterval, int maxMessageLength) {
            this.enabled = enabled;
            this.maxMembers = maxMembers;
            this.messageInterval = messageInterval;
            this.maxMessageLength = maxMessageLength;
        }
    }

    /**
     * 消息配置
     */
    @Data
    public static class MessageConfig {
        /** 默认最大消息长度 */
        private int maxLength = 500;

        /** 最大语音时长（秒） */
        private int maxVoiceDuration = 60;

        /** 最大图片大小（MB） */
        private int maxImageSize = 2;

        /** 历史消息保留天数 */
        private int historyDays = 30;

        /** 批量处理大小 */
        private int batchSize = 100;

        /** 消息缓存大小 */
        private int cacheSize = 10000;

        /** 缓存过期时间（分钟） */
        private int cacheExpireMinutes = 30;

        /** 是否启用消息压缩 */
        private boolean enableCompression = false;

        /** 是否启用消息加密 */
        private boolean enableEncryption = false;

        /** 撤回时间限制（分钟） */
        private int recallTimeLimit = 5;
    }

    /**
     * 安全配置
     */
    @Data
    public static class SecurityConfig {
        /** 是否启用敏感词过滤 */
        private boolean sensitiveWordsEnabled = true;

        /** 敏感词库文件路径 */
        private String sensitiveWordsFile = "classpath:sensitive-words.txt";

        /** 限流配置 */
        private RateLimitConfig rateLimit = new RateLimitConfig();

        /** 默认禁言时长（小时） */
        private int banDuration = 24;

        /** 是否启用IP限制 */
        private boolean enableIpLimit = true;

        /** 单IP最大连接数 */
        private int maxConnectionsPerIp = 10;

        /** 是否启用验证码 */
        private boolean enableCaptcha = false;

        /** 扩展安全配置 */
        private Map<String, Object> extra = new HashMap<>();
    }

    /**
     * 限流配置
     */
    @Data
    public static class RateLimitConfig {
        /** 每分钟最大消息数 */
        private int messagesPerMinute = 30;

        /** 相同消息发送间隔（秒） */
        private int identicalMessageInterval = 10;

        /** 是否启用限流 */
        private boolean enabled = true;

        /** 限流窗口大小（秒） */
        private int windowSize = 60;

        /** 超出限制的惩罚时间（秒） */
        private int penaltyDuration = 300;
    }

    /**
     * 存储配置
     */
    @Data
    public static class StorageConfig {
        /** 存储类型 */
        private StorageType type = StorageType.MONGODB;

        /** 分区策略 */
        private PartitionStrategy partitionStrategy = PartitionStrategy.DAILY;

        /** 数据保留天数 */
        private int retentionDays = 90;

        /** 是否启用归档 */
        private boolean archiveEnabled = true;

        /** 归档阈值天数 */
        private int archiveThresholdDays = 30;

        /** 数据库连接池大小 */
        private int connectionPoolSize = 20;

        /** 查询超时（秒） */
        private int queryTimeout = 10;

        /** 批量写入大小 */
        private int batchWriteSize = 1000;

        /** 是否启用读写分离 */
        private boolean enableReadWriteSeparation = false;

        /** 扩展存储配置 */
        private Map<String, Object> extra = new HashMap<>();
    }

    /**
     * 存储类型枚举
     */
    public enum StorageType {
        MONGODB("mongodb"),
        MYSQL("mysql"),
        REDIS("redis"),
        ELASTICSEARCH("elasticsearch");

        private final String code;

        StorageType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 分区策略枚举
     */
    public enum PartitionStrategy {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly"),
        YEARLY("yearly");

        private final String code;

        PartitionStrategy(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 监控配置
     */
    @Data
    public static class MonitorConfig {
        /** 是否启用指标收集 */
        private boolean metricsEnabled = true;

        /** 指标报告间隔（秒） */
        private int reportInterval = 60;

        /** 告警阈值配置 */
        private AlertThresholdConfig alertThreshold = new AlertThresholdConfig();

        /** 是否启用性能监控 */
        private boolean performanceMonitorEnabled = true;

        /** 是否启用错误监控 */
        private boolean errorMonitorEnabled = true;

        /** 日志级别 */
        private String logLevel = "INFO";

        /** 扩展监控配置 */
        private Map<String, Object> extra = new HashMap<>();
    }

    /**
     * 告警阈值配置
     */
    @Data
    public static class AlertThresholdConfig {
        /** 消息延迟阈值（毫秒） */
        private long messageDelay = 1000;

        /** 错误率阈值 */
        private double errorRate = 0.01;

        /** CPU使用率阈值 */
        private double cpuUsage = 0.8;

        /** 内存使用率阈值 */
        private double memoryUsage = 0.8;

        /** 连接数阈值 */
        private int connectionCount = 8000;
    }

    // ===== 配置验证和工具方法 =====

    /**
     * 验证配置的有效性
     *
     * @return 验证结果
     */
    public boolean validate() {
        try {
            // 验证端口不冲突
            if (connection.websocketPort == connection.tcpPort) {
                return false;
            }

            // 验证存储配置
            if (storage.retentionDays < storage.archiveThresholdDays) {
                return false;
            }

            // 验证限流配置
            if (security.rateLimit.messagesPerMinute <= 0) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取指定频道类型的配置
     *
     * @param channelType 频道类型
     * @return 频道配置
     */
    public ChannelConfig getChannelConfig(String channelType) {
        switch (channelType.toLowerCase()) {
            case "world":
                return channels.world;
            case "guild":
                return channels.guild;
            case "team":
                return channels.team;
            case "private":
                return channels.privateChat;
            case "system":
                return channels.system;
            default:
                return channels.custom.get(channelType);
        }
    }

    /**
     * 获取配置摘要信息
     *
     * @return 配置摘要
     */
    public String getConfigSummary() {
        return String.format("ChatConfig[ws:%d, tcp:%d, maxConn:%d, storage:%s]",
                connection.websocketPort,
                connection.tcpPort,
                connection.maxConnections,
                storage.type);
    }

    /**
     * 获取所有已启用的频道类型
     *
     * @return 启用的频道类型列表
     */
    public java.util.List<String> getEnabledChannelTypes() {
        java.util.List<String> enabled = new java.util.ArrayList<>();
        
        if (channels.world.enabled) enabled.add("world");
        if (channels.guild.enabled) enabled.add("guild");
        if (channels.team.enabled) enabled.add("team");
        if (channels.privateChat.enabled) enabled.add("private");
        if (channels.system.enabled) enabled.add("system");
        
        // 添加启用的自定义频道
        channels.custom.entrySet().stream()
                .filter(entry -> entry.getValue().enabled)
                .forEach(entry -> enabled.add(entry.getKey()));
        
        return enabled;
    }
}