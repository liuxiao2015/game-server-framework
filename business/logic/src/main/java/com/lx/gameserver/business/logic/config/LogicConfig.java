/*
 * 文件名: LogicConfig.java
 * 用途: 逻辑服务配置
 * 实现内容:
 *   - 逻辑服务器的各项配置参数管理
 *   - 服务器配置、游戏参数配置
 *   - 功能开关和调试配置
 *   - 性能配置和Actor、ECS配置
 *   - 场景配置和缓存配置
 * 技术选型:
 *   - Spring Boot配置绑定机制
 *   - YAML配置文件支持
 *   - 配置热更新和验证
 *   - 环境变量覆盖支持
 * 依赖关系:
 *   - 被LogicContext和各个模块使用
 *   - 提供全局配置访问接口
 *   - 支持配置动态更新
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;

/**
 * 逻辑服务配置
 * <p>
 * 统一管理逻辑服务的所有配置参数，包括服务器配置、
 * 游戏参数、功能开关、性能配置等。支持配置验证
 * 和热更新机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "game.logic")
public class LogicConfig {

    /** 服务器配置 */
    @Valid
    private ServerConfig server = new ServerConfig();

    /** Actor配置 */
    @Valid
    private ActorConfig actor = new ActorConfig();

    /** ECS配置 */
    @Valid
    private EcsConfig ecs = new EcsConfig();

    /** 场景配置 */
    @Valid
    private SceneConfig scene = new SceneConfig();

    /** 功能开关 */
    @Valid
    private FeaturesConfig features = new FeaturesConfig();

    /** 性能配置 */
    @Valid
    private PerformanceConfig performance = new PerformanceConfig();

    /**
     * 服务器配置
     */
    @Data
    public static class ServerConfig {
        /** 服务器ID */
        @NotBlank(message = "服务器ID不能为空")
        private String id = "logic-1";

        /** 服务器名称 */
        @NotBlank(message = "服务器名称不能为空")
        private String name = "逻辑服务器1";

        /** 最大玩家数 */
        @Min(value = 1, message = "最大玩家数不能小于1")
        @Max(value = 100000, message = "最大玩家数不能超过100000")
        private int maxPlayers = 5000;

        /** Tick频率（每秒tick次数） */
        @Min(value = 1, message = "Tick频率不能小于1")
        @Max(value = 100, message = "Tick频率不能超过100")
        private int tickRate = 20;

        /** 服务器描述 */
        private String description = "游戏逻辑服务器";

        /** 维护模式 */
        private boolean maintenanceMode = false;

        /** 调试模式 */
        private boolean debugMode = false;
    }

    /**
     * Actor配置
     */
    @Data
    public static class ActorConfig {
        /** 玩家Actor调度器 */
        @NotBlank(message = "玩家Actor调度器不能为空")
        private String playerActorDispatcher = "game-dispatcher";

        /** 场景Actor调度器 */
        @NotBlank(message = "场景Actor调度器不能为空")
        private String sceneActorDispatcher = "scene-dispatcher";

        /** 最大Actor数量 */
        @Min(value = 1000, message = "最大Actor数量不能小于1000")
        private int maxActors = 100000;

        /** Actor消息队列大小 */
        @Min(value = 100, message = "Actor消息队列大小不能小于100")
        private int messageQueueSize = 10000;

        /** Actor超时时间 */
        @NotNull(message = "Actor超时时间不能为空")
        private Duration actorTimeout = Duration.ofSeconds(30);

        /** 启用Actor监控 */
        private boolean enableMonitoring = true;
    }

    /**
     * ECS配置
     */
    @Data
    public static class EcsConfig {
        /** 世界更新间隔 */
        @NotNull(message = "世界更新间隔不能为空")
        private Duration worldUpdateInterval = Duration.ofMillis(50);

        /** 实体池大小 */
        @Min(value = 1000, message = "实体池大小不能小于1000")
        private int entityPoolSize = 10000;

        /** 组件池大小 */
        @Min(value = 5000, message = "组件池大小不能小于5000")
        private int componentPoolSize = 50000;

        /** 启用并行系统 */
        private boolean enableParallelSystems = true;

        /** 系统线程数 */
        @Min(value = 1, message = "系统线程数不能小于1")
        private int systemThreads = Runtime.getRuntime().availableProcessors();

        /** 启用系统性能监控 */
        private boolean enableSystemProfiling = false;
    }

    /**
     * 场景配置
     */
    @Data
    public static class SceneConfig {
        /** 最大场景数 */
        @Min(value = 1, message = "最大场景数不能小于1")
        private int maxScenes = 100;

        /** 场景容量 */
        @Min(value = 1, message = "场景容量不能小于1")
        private int sceneCapacity = 500;

        /** AOI范围 */
        @Min(value = 10, message = "AOI范围不能小于10")
        private int aoiRange = 100;

        /** 场景更新间隔 */
        @NotNull(message = "场景更新间隔不能为空")
        private Duration updateInterval = Duration.ofMillis(100);

        /** 启用场景预加载 */
        private boolean enablePreload = true;

        /** 场景空闲超时 */
        @NotNull(message = "场景空闲超时不能为空")
        private Duration idleTimeout = Duration.ofMinutes(10);
    }

    /**
     * 功能开关
     */
    @Data
    public static class FeaturesConfig {
        /** 启用战斗系统 */
        private boolean enableBattle = true;

        /** 启用交易系统 */
        private boolean enableTrade = true;

        /** 启用公会系统 */
        private boolean enableGuild = true;

        /** 启用跨服功能 */
        private boolean enableCrossServer = false;

        /** 启用PVP */
        private boolean enablePvp = true;

        /** 启用PVE */
        private boolean enablePve = true;

        /** 启用聊天系统 */
        private boolean enableChat = true;

        /** 启用好友系统 */
        private boolean enableFriend = true;

        /** 启用排行榜 */
        private boolean enableRanking = true;

        /** 启用活动系统 */
        private boolean enableActivity = true;
    }

    /**
     * 性能配置
     */
    @Data
    public static class PerformanceConfig {
        /** 使用虚拟线程 */
        private boolean useVirtualThreads = true;

        /** 批处理大小 */
        @Min(value = 1, message = "批处理大小不能小于1")
        private int batchSize = 100;

        /** 缓存大小 */
        @Min(value = 1000, message = "缓存大小不能小于1000")
        private int cacheSize = 10000;

        /** 工作线程数 */
        @Min(value = 1, message = "工作线程数不能小于1")
        private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;

        /** IO线程数 */
        @Min(value = 1, message = "IO线程数不能小于1")
        private int ioThreads = Runtime.getRuntime().availableProcessors();

        /** 垃圾回收优化 */
        private boolean gcOptimization = true;

        /** 内存预分配 */
        private boolean preAllocateMemory = true;

        /** 启用性能监控 */
        private boolean enableProfiling = false;

        /** 监控间隔 */
        @NotNull(message = "监控间隔不能为空")
        private Duration monitoringInterval = Duration.ofSeconds(30);
    }

    /**
     * 获取Tick间隔（毫秒）
     *
     * @return Tick间隔
     */
    public long getTickInterval() {
        return 1000L / server.getTickRate();
    }

    /**
     * 检查是否启用调试模式
     *
     * @return 是否启用调试模式
     */
    public boolean isDebugEnabled() {
        return server.isDebugMode();
    }

    /**
     * 检查是否为维护模式
     *
     * @return 是否为维护模式
     */
    public boolean isMaintenanceMode() {
        return server.isMaintenanceMode();
    }

    /**
     * 获取ECS世界更新间隔（毫秒）
     *
     * @return 更新间隔毫秒数
     */
    public long getEcsUpdateIntervalMs() {
        return ecs.getWorldUpdateInterval().toMillis();
    }

    /**
     * 获取场景更新间隔（毫秒）
     *
     * @return 更新间隔毫秒数
     */
    public long getSceneUpdateIntervalMs() {
        return scene.getUpdateInterval().toMillis();
    }

    /**
     * 验证配置
     *
     * @return 验证结果
     */
    public boolean validate() {
        try {
            // 验证服务器配置
            if (server.getMaxPlayers() <= 0) {
                return false;
            }

            // 验证性能配置
            if (performance.getWorkerThreads() <= 0 || performance.getIoThreads() <= 0) {
                return false;
            }

            // 验证ECS配置
            if (ecs.getEntityPoolSize() <= 0 || ecs.getComponentPoolSize() <= 0) {
                return false;
            }

            // 验证场景配置
            if (scene.getMaxScenes() <= 0 || scene.getSceneCapacity() <= 0) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重置为默认配置
     */
    public void resetToDefaults() {
        this.server = new ServerConfig();
        this.actor = new ActorConfig();
        this.ecs = new EcsConfig();
        this.scene = new SceneConfig();
        this.features = new FeaturesConfig();
        this.performance = new PerformanceConfig();
    }

    @Override
    public String toString() {
        return String.format("LogicConfig{serverId='%s', serverName='%s', maxPlayers=%d, tickRate=%d}",
                server.getId(), server.getName(), server.getMaxPlayers(), server.getTickRate());
    }
}