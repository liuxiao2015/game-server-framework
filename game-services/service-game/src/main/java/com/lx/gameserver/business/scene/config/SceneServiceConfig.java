/*
 * 文件名: SceneServiceConfig.java
 * 用途: 场景服务配置类
 * 实现内容:
 *   - 场景服务的全局配置管理
 *   - Actor配置和线程池设置
 *   - AOI配置和性能参数
 *   - 监控配置和统计设置
 *   - 各种阈值和限制参数
 * 技术选型:
 *   - Spring Boot配置属性绑定
 *   - Builder模式提供灵活配置
 *   - 验证注解保证配置有效性
 * 依赖关系:
 *   - 被Spring容器管理
 *   - 为各个组件提供配置参数
 *   - 与application.yml配置文件绑定
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 场景服务配置
 * <p>
 * 定义场景服务的所有配置参数，包括Actor配置、
 * AOI配置、性能配置、监控配置等。通过Spring Boot
 * 的配置属性绑定机制从配置文件加载参数。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@Component
@ConfigurationProperties(prefix = "game.scene")
public class SceneServiceConfig {

    /** 场景管理配置 */
    private ManagerConfig manager = new ManagerConfig();

    /** Actor配置 */
    private ActorConfig actor = new ActorConfig();

    /** AOI配置 */
    private AoiConfig aoi = new AoiConfig();

    /** 同步配置 */
    private SyncConfig sync = new SyncConfig();

    /** 场景类型配置 */
    private TypesConfig types = new TypesConfig();

    /** 性能配置 */
    private PerformanceConfig performance = new PerformanceConfig();

    /** 监控配置 */
    private MonitorConfig monitor = new MonitorConfig();

    /**
     * 场景管理配置
     */
    @Data
    public static class ManagerConfig {
        /** 最大场景数量 */
        private int maxScenes = 1000;
        /** 场景池大小 */
        private int scenePoolSize = 100;
        /** 负载均衡策略 */
        private String loadBalanceStrategy = "least-loaded";
        /** 场景清理间隔 */
        private Duration cleanupInterval = Duration.ofMinutes(5);
        /** 空场景超时时间 */
        private Duration emptySceneTimeout = Duration.ofMinutes(10);
    }

    /**
     * Actor配置
     */
    @Data
    public static class ActorConfig {
        /** 调度器名称 */
        private String dispatcher = "scene-dispatcher";
        /** 邮箱大小 */
        private int mailboxSize = 10000;
        /** Tick间隔 */
        private Duration tickInterval = Duration.ofMillis(100);
        /** 最大Actor数量 */
        private int maxActors = 5000;
        /** Actor线程池大小 */
        private int threadPoolSize = 4;
        /** 消息批处理大小 */
        private int messageBatchSize = 50;
    }

    /**
     * AOI配置
     */
    @Data
    public static class AoiConfig {
        /** AOI算法类型 */
        private String algorithm = "nine-grid";
        /** 网格大小 */
        private float gridSize = 100.0f;
        /** 视野距离 */
        private double viewDistance = 200.0;
        /** 更新间隔 */
        private Duration updateInterval = Duration.ofMillis(200);
        /** 是否启用优化 */
        private boolean optimizationEnabled = true;
        /** 缓存大小 */
        private int cacheSize = 1000;
        /** 批处理大小 */
        private int batchSize = 100;
        /** 事件队列大小 */
        private int eventQueueSize = 10000;
    }

    /**
     * 同步配置
     */
    @Data
    public static class SyncConfig {
        /** 位置同步间隔 */
        private Duration positionSyncInterval = Duration.ofMillis(100);
        /** 状态同步间隔 */
        private Duration stateSyncInterval = Duration.ofMillis(500);
        /** 批处理大小 */
        private int batchSize = 50;
        /** 是否启用压缩 */
        private boolean compression = true;
        /** 最大同步距离 */
        private double maxSyncDistance = 1000.0;
        /** 同步优化级别 */
        private int optimizationLevel = 2;
    }

    /**
     * 场景类型配置
     */
    @Data
    public static class TypesConfig {
        /** 主城配置 */
        private SceneTypeConfig mainCity = new SceneTypeConfig(500, Duration.ZERO);
        /** 副本配置 */
        private SceneTypeConfig dungeon = new SceneTypeConfig(10, Duration.ofMinutes(30));
        /** 战场配置 */
        private SceneTypeConfig battlefield = new SceneTypeConfig(100, Duration.ofMinutes(20));
        /** 野外配置 */
        private SceneTypeConfig field = new SceneTypeConfig(200, Duration.ZERO);
        /** 竞技场配置 */
        private SceneTypeConfig arena = new SceneTypeConfig(20, Duration.ofMinutes(10));

        @Data
        public static class SceneTypeConfig {
            /** 最大玩家数 */
            private int maxPlayers;
            /** 时间限制 */
            private Duration timeLimit;

            public SceneTypeConfig() {}

            public SceneTypeConfig(int maxPlayers, Duration timeLimit) {
                this.maxPlayers = maxPlayers;
                this.timeLimit = timeLimit;
            }
        }
    }

    /**
     * 性能配置
     */
    @Data
    public static class PerformanceConfig {
        /** 实体池大小 */
        private int entityPoolSize = 10000;
        /** 消息批处理大小 */
        private int messageBatchSize = 100;
        /** 是否使用本地内存 */
        private boolean useNativeMemory = false;
        /** GC优化级别 */
        private int gcOptimizationLevel = 1;
        /** 缓存预热 */
        private boolean enableCacheWarming = true;
        /** 内存监控阈值 */
        private double memoryThreshold = 0.8;
        /** CPU监控阈值 */
        private double cpuThreshold = 0.8;
    }

    /**
     * 监控配置
     */
    @Data
    public static class MonitorConfig {
        /** 是否启用监控 */
        private boolean enable = true;
        /** 指标收集间隔 */
        private Duration metricsInterval = Duration.ofSeconds(60);
        /** 慢Tick阈值 */
        private Duration slowTickThreshold = Duration.ofMillis(200);
        /** 告警阈值 */
        private AlertConfig alert = new AlertConfig();
        /** 统计保留时间 */
        private Duration statisticsRetention = Duration.ofHours(24);
        /** 是否启用详细日志 */
        private boolean enableDetailedLogging = false;

        @Data
        public static class AlertConfig {
            /** CPU使用率告警阈值 */
            private double cpuUsageThreshold = 80.0;
            /** 内存使用率告警阈值 */
            private double memoryUsageThreshold = 80.0;
            /** 实体数量告警阈值 */
            private int entityCountThreshold = 5000;
            /** 消息积压告警阈值 */
            private int messageBacklogThreshold = 1000;
            /** 延迟告警阈值 */
            private Duration latencyThreshold = Duration.ofMillis(500);
        }
    }

    /**
     * 验证配置有效性
     *
     * @return 是否有效
     */
    public boolean validate() {
        // 基本参数验证
        if (manager.getMaxScenes() <= 0) {
            return false;
        }
        if (actor.getMaxActors() <= 0) {
            return false;
        }
        if (aoi.getGridSize() <= 0) {
            return false;
        }
        if (aoi.getViewDistance() <= 0) {
            return false;
        }
        
        // 性能参数验证
        if (performance.getEntityPoolSize() <= 0) {
            return false;
        }
        if (performance.getMemoryThreshold() < 0 || performance.getMemoryThreshold() > 1) {
            return false;
        }
        if (performance.getCpuThreshold() < 0 || performance.getCpuThreshold() > 1) {
            return false;
        }
        
        return true;
    }

    /**
     * 获取场景类型配置
     *
     * @param sceneType 场景类型
     * @return 场景类型配置
     */
    public TypesConfig.SceneTypeConfig getSceneTypeConfig(String sceneType) {
        return switch (sceneType.toLowerCase()) {
            case "main_city" -> types.getMainCity();
            case "dungeon" -> types.getDungeon();
            case "battlefield" -> types.getBattlefield();
            case "field" -> types.getField();
            case "arena" -> types.getArena();
            default -> new TypesConfig.SceneTypeConfig(100, Duration.ZERO);
        };
    }

    /**
     * 创建默认配置
     *
     * @return 默认配置
     */
    public static SceneServiceConfig createDefault() {
        SceneServiceConfig config = new SceneServiceConfig();
        
        // 设置默认值
        config.manager = new ManagerConfig();
        config.actor = new ActorConfig();
        config.aoi = new AoiConfig();
        config.sync = new SyncConfig();
        config.types = new TypesConfig();
        config.performance = new PerformanceConfig();
        config.monitor = new MonitorConfig();
        
        return config;
    }

    @Override
    public String toString() {
        return String.format("SceneServiceConfig{maxScenes=%d, gridSize=%.1f, viewDistance=%.1f}", 
                manager.getMaxScenes(), aoi.getGridSize(), aoi.getViewDistance());
    }
}