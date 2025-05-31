/*
 * 文件名: SceneConfig.java
 * 用途: 场景配置类定义
 * 实现内容:
 *   - 场景的基础配置参数管理
 *   - 地图配置和刷新点定义
 *   - 传送点和NPC配置管理
 *   - 怪物刷新和场景规则配置
 *   - 动态配置加载和热更新支持
 * 技术选型:
 *   - Builder模式提供灵活的配置构建
 *   - 不可变对象设计保证配置安全
 *   - JSON序列化支持配置持久化
 *   - 默认值机制保证配置完整性
 * 依赖关系:
 *   - 被Scene类使用进行配置管理
 *   - 被SceneFactory使用进行场景创建
 *   - 与SceneManager协作进行配置加载
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.core;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

/**
 * 场景配置类
 * <p>
 * 定义场景的各种配置参数，包括地图信息、刷新点、
 * 传送点、NPC、怪物等配置。支持动态配置和热更新。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Data
@Builder
public class SceneConfig {

    // ========== 基础配置 ==========

    /** 场景模板ID */
    @Builder.Default
    private String templateId = "";

    /** 场景版本 */
    @Builder.Default
    private String version = "1.0.0";

    /** 最大实体数量 */
    @Builder.Default
    private int maxEntities = 1000;

    /** 最大玩家数量 */
    @Builder.Default
    private int maxPlayers = 100;

    /** 场景生存时间（毫秒），0表示永久 */
    @Builder.Default
    private long lifeTime = 0;

    /** 空场景超时时间（毫秒） */
    @Builder.Default
    private long emptyTimeout = Duration.ofMinutes(5).toMillis();

    // ========== 地图配置 ==========

    /** 地图信息 */
    @Builder.Default
    private MapConfig mapConfig = new MapConfig();

    /**
     * 地图配置
     */
    @Data
    @Builder
    public static class MapConfig {
        /** 地图ID */
        @Builder.Default
        private String mapId = "";

        /** 地图名称 */
        @Builder.Default
        private String mapName = "";

        /** 地图宽度 */
        @Builder.Default
        private float width = 1000.0f;

        /** 地图高度 */
        @Builder.Default
        private float height = 1000.0f;

        /** 地图最小X坐标 */
        @Builder.Default
        private float minX = 0.0f;

        /** 地图最大X坐标 */
        @Builder.Default
        private float maxX = 1000.0f;

        /** 地图最小Z坐标 */
        @Builder.Default
        private float minZ = 0.0f;

        /** 地图最大Z坐标 */
        @Builder.Default
        private float maxZ = 1000.0f;

        /** 默认出生点 */
        @Builder.Default
        private Scene.Position defaultSpawnPoint = new Scene.Position(500, 0, 500);

        /** 地图文件路径 */
        @Builder.Default
        private String mapFilePath = "";

        /** 寻路网格文件路径 */
        @Builder.Default
        private String navMeshPath = "";

        /**
         * 检查位置是否在地图范围内
         *
         * @param position 位置
         * @return 是否在范围内
         */
        public boolean isValidPosition(Scene.Position position) {
            return position.getX() >= minX && position.getX() <= maxX &&
                   position.getZ() >= minZ && position.getZ() <= maxZ;
        }

        /**
         * 修正位置到地图范围内
         *
         * @param position 位置
         * @return 修正后的位置
         */
        public Scene.Position clampPosition(Scene.Position position) {
            float x = Math.max(minX, Math.min(maxX, position.getX()));
            float z = Math.max(minZ, Math.min(maxZ, position.getZ()));
            return new Scene.Position(x, position.getY(), z, position.getRotation());
        }
    }

    // ========== 刷新点配置 ==========

    /** 刷新点列表 */
    @Builder.Default
    private List<SpawnPoint> spawnPoints = new ArrayList<>();

    /**
     * 刷新点配置
     */
    @Data
    @Builder
    public static class SpawnPoint {
        /** 刷新点ID */
        private String spawnId;

        /** 刷新点名称 */
        private String spawnName;

        /** 刷新点类型（player/monster/npc/item） */
        private String spawnType;

        /** 刷新位置 */
        private Scene.Position position;

        /** 刷新半径 */
        @Builder.Default
        private float radius = 5.0f;

        /** 刷新间隔（毫秒） */
        @Builder.Default
        private long spawnInterval = 30000;

        /** 最大刷新数量 */
        @Builder.Default
        private int maxCount = 1;

        /** 刷新的实体模板ID */
        private String templateId;

        /** 是否启用 */
        @Builder.Default
        private boolean enabled = true;

        /** 扩展属性 */
        @Builder.Default
        private Map<String, Object> properties = new HashMap<>();
    }

    // ========== 传送点配置 ==========

    /** 传送点列表 */
    @Builder.Default
    private List<Portal> portals = new ArrayList<>();

    /**
     * 传送点配置
     */
    @Data
    @Builder
    public static class Portal {
        /** 传送点ID */
        private String portalId;

        /** 传送点名称 */
        private String portalName;

        /** 传送点位置 */
        private Scene.Position position;

        /** 触发半径 */
        @Builder.Default
        private float triggerRadius = 2.0f;

        /** 目标场景ID */
        private Long targetSceneId;

        /** 目标位置 */
        private Scene.Position targetPosition;

        /** 传送条件 */
        @Builder.Default
        private Map<String, Object> conditions = new HashMap<>();

        /** 是否启用 */
        @Builder.Default
        private boolean enabled = true;

        /** 传送冷却时间（毫秒） */
        @Builder.Default
        private long cooldown = 5000;
    }

    // ========== NPC配置 ==========

    /** NPC列表 */
    @Builder.Default
    private List<NpcConfig> npcs = new ArrayList<>();

    /**
     * NPC配置
     */
    @Data
    @Builder
    public static class NpcConfig {
        /** NPC ID */
        private String npcId;

        /** NPC模板ID */
        private String templateId;

        /** NPC名称 */
        private String npcName;

        /** NPC位置 */
        private Scene.Position position;

        /** NPC类型 */
        private String npcType;

        /** NPC功能 */
        @Builder.Default
        private List<String> functions = new ArrayList<>();

        /** 对话内容 */
        @Builder.Default
        private Map<String, Object> dialogues = new HashMap<>();

        /** 是否可移动 */
        @Builder.Default
        private boolean movable = false;

        /** 巡逻路径 */
        @Builder.Default
        private List<Scene.Position> patrolPath = new ArrayList<>();

        /** 扩展属性 */
        @Builder.Default
        private Map<String, Object> attributes = new HashMap<>();
    }

    // ========== 怪物配置 ==========

    /** 怪物配置 */
    @Builder.Default
    private MonsterConfig monsterConfig = new MonsterConfig();

    /**
     * 怪物配置
     */
    @Data
    @Builder
    public static class MonsterConfig {
        /** 是否启用怪物刷新 */
        @Builder.Default
        private boolean enableSpawn = true;

        /** 全局怪物刷新间隔（毫秒） */
        @Builder.Default
        private long globalSpawnInterval = 60000;

        /** 最大怪物数量 */
        @Builder.Default
        private int maxMonsters = 100;

        /** 怪物组配置 */
        @Builder.Default
        private List<MonsterGroup> monsterGroups = new ArrayList<>();

        /**
         * 怪物组配置
         */
        @Data
        @Builder
        public static class MonsterGroup {
            /** 组ID */
            private String groupId;

            /** 组名称 */
            private String groupName;

            /** 怪物模板ID列表 */
            private List<String> monsterTemplates;

            /** 刷新区域 */
            private List<Scene.Position> spawnArea;

            /** 刷新间隔（毫秒） */
            @Builder.Default
            private long spawnInterval = 30000;

            /** 最大数量 */
            @Builder.Default
            private int maxCount = 10;

            /** 巡逻范围 */
            @Builder.Default
            private float patrolRange = 50.0f;

            /** 是否启用 */
            @Builder.Default
            private boolean enabled = true;
        }
    }

    // ========== AOI配置 ==========

    /** AOI配置 */
    @Builder.Default
    private AoiConfig aoiConfig = new AoiConfig();

    /**
     * AOI配置
     */
    @Data
    @Builder
    public static class AoiConfig {
        /** 网格大小 */
        @Builder.Default
        private float gridSize = 100.0f;

        /** 默认视野范围 */
        @Builder.Default
        private double defaultViewRange = 150.0;

        /** 最大视野范围 */
        @Builder.Default
        private double maxViewRange = 300.0;

        /** AOI更新间隔（毫秒） */
        @Builder.Default
        private long updateInterval = 200;

        /** 是否启用AOI优化 */
        @Builder.Default
        private boolean optimizationEnabled = true;

        /** 兴趣区域缓存大小 */
        @Builder.Default
        private int cacheSize = 1000;
    }

    // ========== 场景规则配置 ==========

    /** 场景规则 */
    @Builder.Default
    private SceneRules rules = new SceneRules();

    /**
     * 场景规则
     */
    @Data
    @Builder
    public static class SceneRules {
        /** 是否允许PVP */
        @Builder.Default
        private boolean pvpEnabled = false;

        /** 是否为安全区域 */
        @Builder.Default
        private boolean safeZone = true;

        /** 是否允许复活 */
        @Builder.Default
        private boolean allowRespawn = true;

        /** 死亡惩罚类型 */
        @Builder.Default
        private String deathPenalty = "none";

        /** 进入条件 */
        @Builder.Default
        private Map<String, Object> enterConditions = new HashMap<>();

        /** 离开条件 */
        @Builder.Default
        private Map<String, Object> exitConditions = new HashMap<>();

        /** 场景buff列表 */
        @Builder.Default
        private List<String> sceneBuffs = new ArrayList<>();

        /** 禁用技能列表 */
        @Builder.Default
        private List<String> disabledSkills = new ArrayList<>();

        /** 移动速度修正 */
        @Builder.Default
        private float speedModifier = 1.0f;

        /** 经验获取修正 */
        @Builder.Default
        private float expModifier = 1.0f;

        /** 掉落率修正 */
        @Builder.Default
        private float dropRateModifier = 1.0f;
    }

    // ========== 性能配置 ==========

    /** 性能配置 */
    @Builder.Default
    private PerformanceConfig performanceConfig = new PerformanceConfig();

    /**
     * 性能配置
     */
    @Data
    @Builder
    public static class PerformanceConfig {
        /** Tick间隔（毫秒） */
        @Builder.Default
        private long tickInterval = 100;

        /** 实体更新间隔（毫秒） */
        @Builder.Default
        private long entityUpdateInterval = 200;

        /** 同步间隔（毫秒） */
        @Builder.Default
        private long syncInterval = 100;

        /** 是否启用实体池化 */
        @Builder.Default
        private boolean entityPoolingEnabled = true;

        /** 实体池大小 */
        @Builder.Default
        private int entityPoolSize = 1000;

        /** 是否启用批量处理 */
        @Builder.Default
        private boolean batchProcessingEnabled = true;

        /** 批处理大小 */
        @Builder.Default
        private int batchSize = 50;

        /** 垃圾回收优化 */
        @Builder.Default
        private boolean gcOptimizationEnabled = true;
    }

    // ========== 扩展配置 ==========

    /** 扩展属性 */
    @Builder.Default
    private Map<String, Object> extensions = new HashMap<>();

    /** 自定义配置 */
    @Builder.Default
    private Map<String, Object> customConfig = new HashMap<>();

    // ========== 默认构造函数 ==========

    public SceneConfig() {
        this.templateId = "";
        this.version = "1.0.0";
        this.maxEntities = 1000;
        this.maxPlayers = 100;
        this.lifeTime = 0;
        this.emptyTimeout = Duration.ofMinutes(5).toMillis();
        this.mapConfig = new MapConfig();
        this.spawnPoints = new ArrayList<>();
        this.portals = new ArrayList<>();
        this.npcs = new ArrayList<>();
        this.monsterConfig = new MonsterConfig();
        this.aoiConfig = new AoiConfig();
        this.rules = new SceneRules();
        this.performanceConfig = new PerformanceConfig();
        this.extensions = new HashMap<>();
        this.customConfig = new HashMap<>();
    }

    // ========== 工具方法 ==========

    /**
     * 获取刷新点
     *
     * @param spawnId 刷新点ID
     * @return 刷新点配置
     */
    public SpawnPoint getSpawnPoint(String spawnId) {
        return spawnPoints.stream()
                .filter(sp -> spawnId.equals(sp.getSpawnId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取指定类型的刷新点
     *
     * @param spawnType 刷新点类型
     * @return 刷新点列表
     */
    public List<SpawnPoint> getSpawnPointsByType(String spawnType) {
        return spawnPoints.stream()
                .filter(sp -> spawnType.equals(sp.getSpawnType()) && sp.isEnabled())
                .toList();
    }

    /**
     * 获取传送点
     *
     * @param portalId 传送点ID
     * @return 传送点配置
     */
    public Portal getPortal(String portalId) {
        return portals.stream()
                .filter(p -> portalId.equals(p.getPortalId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取NPC配置
     *
     * @param npcId NPC ID
     * @return NPC配置
     */
    public NpcConfig getNpc(String npcId) {
        return npcs.stream()
                .filter(npc -> npcId.equals(npc.getNpcId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取扩展属性
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @param <T> 类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, T defaultValue) {
        Object value = extensions.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 设置扩展属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void setExtension(String key, Object value) {
        extensions.put(key, value);
    }

    /**
     * 获取自定义配置
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @param <T> 类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomConfig(String key, T defaultValue) {
        Object value = customConfig.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 设置自定义配置
     *
     * @param key 配置键
     * @param value 配置值
     */
    public void setCustomConfig(String key, Object value) {
        customConfig.put(key, value);
    }

    /**
     * 验证配置有效性
     *
     * @return 验证结果
     */
    public boolean validate() {
        try {
            // 基础配置验证
            if (maxEntities <= 0 || maxPlayers <= 0) {
                log.error("场景配置验证失败：实体或玩家数量配置无效");
                return false;
            }

            // 地图配置验证
            if (mapConfig.getWidth() <= 0 || mapConfig.getHeight() <= 0) {
                log.error("场景配置验证失败：地图尺寸配置无效");
                return false;
            }

            // AOI配置验证
            if (aoiConfig.getGridSize() <= 0 || aoiConfig.getDefaultViewRange() <= 0) {
                log.error("场景配置验证失败：AOI配置无效");
                return false;
            }

            // 性能配置验证
            if (performanceConfig.getTickInterval() <= 0) {
                log.error("场景配置验证失败：Tick间隔配置无效");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("场景配置验证异常", e);
            return false;
        }
    }

    /**
     * 创建默认配置
     *
     * @param sceneType 场景类型
     * @return 默认配置
     */
    public static SceneConfig createDefault(SceneType sceneType) {
        SceneConfigBuilder builder = SceneConfig.builder();

        switch (sceneType) {
            case MAIN_CITY:
                builder.maxPlayers(1000)
                       .rules(SceneRules.builder()
                               .safeZone(true)
                               .pvpEnabled(false)
                               .build());
                break;
            case FIELD:
                builder.maxPlayers(200)
                       .rules(SceneRules.builder()
                               .safeZone(false)
                               .pvpEnabled(true)
                               .build());
                break;
            case DUNGEON:
                builder.maxPlayers(10)
                       .lifeTime(Duration.ofHours(2).toMillis())
                       .rules(SceneRules.builder()
                               .safeZone(false)
                               .pvpEnabled(false)
                               .build());
                break;
            case BATTLEFIELD:
                builder.maxPlayers(200)
                       .lifeTime(Duration.ofMinutes(30).toMillis())
                       .rules(SceneRules.builder()
                               .safeZone(false)
                               .pvpEnabled(true)
                               .build());
                break;
            case ARENA:
                builder.maxPlayers(20)
                       .lifeTime(Duration.ofMinutes(10).toMillis())
                       .rules(SceneRules.builder()
                               .safeZone(false)
                               .pvpEnabled(true)
                               .build());
                break;
            default:
                // 使用默认配置
                break;
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return String.format("SceneConfig{template='%s', version='%s', maxPlayers=%d, maxEntities=%d}",
                templateId, version, maxPlayers, maxEntities);
    }
}