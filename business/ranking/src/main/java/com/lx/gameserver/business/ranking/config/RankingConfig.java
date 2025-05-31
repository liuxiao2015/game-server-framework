/*
 * 文件名: RankingConfig.java
 * 用途: 排行榜配置类
 * 实现内容:
 *   - 排行榜系统的核心配置参数
 *   - Redis连接和缓存配置
 *   - 更新策略和调度配置
 *   - 监控和性能配置
 * 技术选型:
 *   - 使用Spring Boot配置绑定
 *   - 支持外部配置文件
 *   - 提供默认值和验证
 * 依赖关系:
 *   - 被Spring容器管理
 *   - 被各个组件引用
 *   - 支持配置热更新
 */
package com.lx.gameserver.business.ranking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 排行榜配置类
 * <p>
 * 包含排行榜系统的所有配置参数，支持从配置文件加载，
 * 提供合理的默认值和参数验证。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@Component
@ConfigurationProperties(prefix = "game.ranking")
public class RankingConfig {

    /**
     * Redis配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * 全局配置
     */
    private GlobalConfig global = new GlobalConfig();

    /**
     * 缓存配置
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * 更新策略配置
     */
    private UpdateConfig update = new UpdateConfig();

    /**
     * 结算配置
     */
    private SettlementConfig settlement = new SettlementConfig();

    /**
     * 监控配置
     */
    private MonitorConfig monitor = new MonitorConfig();

    /**
     * Redis配置
     */
    @Data
    public static class RedisConfig {
        /** Redis模式 */
        private String mode = "standalone";
        /** Redis地址列表 */
        private String[] addresses = {"redis://127.0.0.1:6379"};
        /** 密码 */
        private String password;
        /** 数据库索引 */
        private int database = 0;
        /** 连接超时 */
        private Duration connectTimeout = Duration.ofSeconds(10);
        /** 读取超时 */
        private Duration readTimeout = Duration.ofSeconds(10);
        /** 连接池配置 */
        private PoolConfig pool = new PoolConfig();

        @Data
        public static class PoolConfig {
            /** 最大连接数 */
            private int maxTotal = 20;
            /** 最大空闲连接数 */
            private int maxIdle = 10;
            /** 最小空闲连接数 */
            private int minIdle = 2;
        }
    }

    /**
     * 全局配置
     */
    @Data
    public static class GlobalConfig {
        /** 默认榜单大小 */
        private int defaultSize = 100;
        /** 分页大小 */
        private int pageSize = 20;
        /** 更新批次大小 */
        private int updateBatchSize = 1000;
        /** 是否启用历史记录 */
        private boolean enableHistory = true;
        /** 键前缀 */
        private String keyPrefix = "ranking:";
    }

    /**
     * 缓存配置
     */
    @Data
    public static class CacheConfig {
        /** 是否启用缓存 */
        private boolean enabled = true;
        /** 过期时间 */
        private Duration expireTime = Duration.ofMinutes(5);
        /** 是否预热 */
        private boolean warmUp = true;
        /** 最大条目数 */
        private int maxEntries = 10000;
        /** 本地缓存配置 */
        private LocalCacheConfig local = new LocalCacheConfig();

        @Data
        public static class LocalCacheConfig {
            /** 是否启用本地缓存 */
            private boolean enabled = true;
            /** 本地缓存大小 */
            private int maxSize = 1000;
            /** 本地缓存过期时间 */
            private Duration expireTime = Duration.ofMinutes(1);
        }
    }

    /**
     * 更新策略配置
     */
    @Data
    public static class UpdateConfig {
        /** 更新模式 */
        private String mode = "realtime";
        /** 批次间隔 */
        private Duration batchInterval = Duration.ofSeconds(10);
        /** 队列大小 */
        private int queueSize = 10000;
        /** 更新线程数 */
        private int updateThreads = 4;
        /** 最大等待时间 */
        private Duration maxWaitTime = Duration.ofSeconds(5);
    }

    /**
     * 结算配置
     */
    @Data
    public static class SettlementConfig {
        /** 每日重置时间 */
        private String dailyResetTime = "00:00:00";
        /** 每周重置日 */
        private String weeklyResetDay = "MONDAY";
        /** 每月重置日 */
        private int monthlyResetDay = 1;
        /** 赛季持续时间 */
        private Duration seasonDuration = Duration.ofDays(90);
        /** 是否自动结算 */
        private boolean autoSettle = true;
    }

    /**
     * 监控配置
     */
    @Data
    public static class MonitorConfig {
        /** 是否启用监控 */
        private boolean enabled = true;
        /** 指标收集间隔 */
        private Duration metricsInterval = Duration.ofSeconds(60);
        /** 慢查询阈值 */
        private Duration slowQueryThreshold = Duration.ofMillis(100);
        /** 是否记录详细日志 */
        private boolean detailedLogging = false;
    }

    /**
     * 获取排行榜特定配置
     *
     * @param rankingType 排行榜类型
     * @return 特定配置
     */
    public Map<String, Object> getRankingSpecificConfig(String rankingType) {
        // 可以扩展为支持不同排行榜类型的特定配置
        Map<String, Object> config = new HashMap<>();
        
        switch (rankingType.toLowerCase()) {
            case "level":
                config.put("minLevel", 10);
                config.put("maxCapacity", 1000);
                break;
            case "power":
                config.put("minPower", 1000L);
                config.put("maxCapacity", 500);
                config.put("seasonEnabled", true);
                break;
            case "arena":
                config.put("seasonEnabled", true);
                config.put("maxCapacity", 200);
                config.put("resetDaily", true);
                break;
            default:
                config.put("maxCapacity", global.getDefaultSize());
                break;
        }
        
        return config;
    }

    /**
     * 验证配置有效性
     *
     * @return 验证结果
     */
    public boolean isValid() {
        if (global.getDefaultSize() <= 0 || global.getPageSize() <= 0) {
            return false;
        }
        
        if (cache.getMaxEntries() <= 0) {
            return false;
        }
        
        if (update.getQueueSize() <= 0 || update.getUpdateThreads() <= 0) {
            return false;
        }
        
        return true;
    }

    /**
     * 获取Redis键前缀
     *
     * @param rankingId 排行榜ID
     * @return 完整的Redis键
     */
    public String getRedisKey(String rankingId) {
        return global.getKeyPrefix() + rankingId;
    }

    /**
     * 获取历史键前缀
     *
     * @param rankingId 排行榜ID
     * @return 历史记录Redis键
     */
    public String getHistoryKey(String rankingId) {
        return global.getKeyPrefix() + "history:" + rankingId;
    }

    /**
     * 获取快照键前缀
     *
     * @param rankingId 排行榜ID
     * @param timestamp 时间戳
     * @return 快照Redis键
     */
    public String getSnapshotKey(String rankingId, long timestamp) {
        return global.getKeyPrefix() + "snapshot:" + rankingId + ":" + timestamp;
    }
}