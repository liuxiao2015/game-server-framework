/*
 * 文件名: GameWorld.java
 * 用途: 游戏世界扩展
 * 实现内容:
 *   - 游戏世界扩展功能
 *   - 场景管理集成
 *   - 玩家实体管理
 *   - NPC实体管理
 *   - 道具实体管理
 * 技术选型:
 *   - 继承World类扩展游戏功能
 *   - 工厂模式创建游戏实体
 *   - 模板方法模式处理游戏逻辑
 * 依赖关系:
 *   - 继承ECS World类
 *   - 整合游戏特定的系统和组件
 *   - 被游戏服务器使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.game;

import com.lx.gameserver.frame.ecs.component.*;
import com.lx.gameserver.frame.ecs.core.*;
import com.lx.gameserver.frame.ecs.system.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏世界
 * <p>
 * 基于ECS World的游戏世界扩展，提供游戏特定的实体管理和系统集成。
 * 包含玩家、NPC、道具等游戏对象的创建和管理功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class GameWorld extends World {
    
    private static final Logger logger = LoggerFactory.getLogger(GameWorld.class);
    
    /**
     * 游戏实体类型枚举
     */
    public enum GameEntityType {
        /** 玩家 */
        PLAYER(1, "玩家"),
        /** NPC */
        NPC(2, "NPC"),
        /** 怪物 */
        MONSTER(3, "怪物"),
        /** 道具 */
        ITEM(4, "道具"),
        /** 建筑 */
        BUILDING(5, "建筑"),
        /** 陷阱 */
        TRAP(6, "陷阱"),
        /** 特效 */
        EFFECT(7, "特效"),
        /** 传送门 */
        PORTAL(8, "传送门");
        
        private final int id;
        private final String displayName;
        
        GameEntityType(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * 玩家实体映射
     */
    private final Map<Long, Long> playerIdToEntityMap;
    
    /**
     * 实体类型映射
     */
    private final Map<Long, GameEntityType> entityTypeMap;
    
    /**
     * 场景ID
     */
    private final long sceneId;
    
    /**
     * 游戏配置
     */
    private final GameWorldConfig config;
    
    /**
     * 游戏世界配置
     */
    public static class GameWorldConfig extends WorldConfig {
        private int maxPlayers = 1000;
        private int maxNpcs = 5000;
        private int maxItems = 10000;
        private boolean enablePvP = true;
        private boolean enableDropItems = true;
        private float worldBoundaryX = 1000.0f;
        private float worldBoundaryY = 1000.0f;
        private float worldBoundaryZ = 100.0f;
        
        // Getters and Setters
        public int getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = Math.max(0, maxPlayers); }
        
        public int getMaxNpcs() { return maxNpcs; }
        public void setMaxNpcs(int maxNpcs) { this.maxNpcs = Math.max(0, maxNpcs); }
        
        public int getMaxItems() { return maxItems; }
        public void setMaxItems(int maxItems) { this.maxItems = Math.max(0, maxItems); }
        
        public boolean isEnablePvP() { return enablePvP; }
        public void setEnablePvP(boolean enablePvP) { this.enablePvP = enablePvP; }
        
        public boolean isEnableDropItems() { return enableDropItems; }
        public void setEnableDropItems(boolean enableDropItems) { this.enableDropItems = enableDropItems; }
        
        public float getWorldBoundaryX() { return worldBoundaryX; }
        public void setWorldBoundaryX(float worldBoundaryX) { this.worldBoundaryX = worldBoundaryX; }
        
        public float getWorldBoundaryY() { return worldBoundaryY; }
        public void setWorldBoundaryY(float worldBoundaryY) { this.worldBoundaryY = worldBoundaryY; }
        
        public float getWorldBoundaryZ() { return worldBoundaryZ; }
        public void setWorldBoundaryZ(float worldBoundaryZ) { this.worldBoundaryZ = worldBoundaryZ; }
    }
    
    /**
     * 构造函数
     *
     * @param sceneId 场景ID
     */
    public GameWorld(long sceneId) {
        this(sceneId, new GameWorldConfig());
    }
    
    /**
     * 构造函数
     *
     * @param sceneId 场景ID
     * @param config 游戏世界配置
     */
    public GameWorld(long sceneId, GameWorldConfig config) {
        super();
        this.sceneId = sceneId;
        this.config = config;
        this.playerIdToEntityMap = new ConcurrentHashMap<>();
        this.entityTypeMap = new ConcurrentHashMap<>();
        
        // 初始化游戏系统
        initializeGameSystems();
    }
    
    /**
     * 初始化游戏系统
     */
    private void initializeGameSystems() {
        // 注册移动系统
        registerSystem(new MovementSystem());
        
        // 这里可以注册更多游戏系统
        // registerSystem(new CombatSystem());
        // registerSystem(new AISystem());
        // registerSystem(new PhysicsSystem());
        
        logger.info("游戏世界初始化完成，场景ID: {}", sceneId);
    }
    
    /**
     * 创建玩家实体
     *
     * @param playerId 玩家ID
     * @param playerName 玩家名称
     * @param x 初始X坐标
     * @param y 初始Y坐标
     * @param z 初始Z坐标
     * @return 玩家实体
     */
    public Entity createPlayer(long playerId, String playerName, float x, float y, float z) {
        if (playerIdToEntityMap.containsKey(playerId)) {
            throw new IllegalArgumentException("玩家ID已存在: " + playerId);
        }
        
        if (getPlayerCount() >= config.getMaxPlayers()) {
            throw new IllegalStateException("玩家数量已达上限: " + config.getMaxPlayers());
        }
        
        // 创建实体
        Entity entity = createEntity();
        
        // 添加基础组件
        addComponent(entity.getId(), new PositionComponent(x, y, z));
        addComponent(entity.getId(), new VelocityComponent(0, 0, 0));
        addComponent(entity.getId(), new HealthComponent(100, 0)); // 100血量，0护盾
        addComponent(entity.getId(), new StatsComponent());
        addComponent(entity.getId(), new InventoryComponent(InventoryComponent.InventoryType.MAIN));
        
        // 记录映射关系
        playerIdToEntityMap.put(playerId, entity.getId());
        entityTypeMap.put(entity.getId(), GameEntityType.PLAYER);
        
        logger.info("创建玩家实体: playerId={}, entityId={}, name={}", playerId, entity.getId(), playerName);
        return entity;
    }
    
    /**
     * 创建NPC实体
     *
     * @param npcId NPC配置ID
     * @param x 初始X坐标
     * @param y 初始Y坐标
     * @param z 初始Z坐标
     * @return NPC实体
     */
    public Entity createNPC(int npcId, float x, float y, float z) {
        if (getNpcCount() >= config.getMaxNpcs()) {
            throw new IllegalStateException("NPC数量已达上限: " + config.getMaxNpcs());
        }
        
        // 创建实体
        Entity entity = createEntity();
        
        // 添加基础组件
        addComponent(entity.getId(), new PositionComponent(x, y, z));
        addComponent(entity.getId(), new HealthComponent(50, 0)); // 基础血量
        addComponent(entity.getId(), new AIComponent(AIComponent.AIType.PASSIVE));
        addComponent(entity.getId(), new StatsComponent());
        
        // 设置AI出生点
        AIComponent aiComponent = getComponent(entity.getId(), AIComponent.class);
        if (aiComponent != null) {
            aiComponent.setSpawnPoint(x, y, z);
        }
        
        // 记录类型
        entityTypeMap.put(entity.getId(), GameEntityType.NPC);
        
        logger.debug("创建NPC实体: npcId={}, entityId={}", npcId, entity.getId());
        return entity;
    }
    
    /**
     * 创建怪物实体
     *
     * @param monsterId 怪物配置ID
     * @param x 初始X坐标
     * @param y 初始Y坐标
     * @param z 初始Z坐标
     * @param aggressive 是否主动攻击
     * @return 怪物实体
     */
    public Entity createMonster(int monsterId, float x, float y, float z, boolean aggressive) {
        // 创建实体
        Entity entity = createEntity();
        
        // 添加基础组件
        addComponent(entity.getId(), new PositionComponent(x, y, z));
        addComponent(entity.getId(), new VelocityComponent(0, 0, 0));
        addComponent(entity.getId(), new HealthComponent(80, 0)); // 怪物血量
        addComponent(entity.getId(), new StatsComponent());
        
        // 设置AI类型
        AIComponent.AIType aiType = aggressive ? AIComponent.AIType.AGGRESSIVE : AIComponent.AIType.PASSIVE;
        AIComponent aiComponent = new AIComponent(aiType);
        aiComponent.setSpawnPoint(x, y, z);
        
        // 配置AI参数
        aiComponent.getParameters().setDetectionRange(15.0f);
        aiComponent.getParameters().setAttackRange(5.0f);
        aiComponent.getParameters().setChaseRange(25.0f);
        
        addComponent(entity.getId(), aiComponent);
        
        // 记录类型
        entityTypeMap.put(entity.getId(), GameEntityType.MONSTER);
        
        logger.debug("创建怪物实体: monsterId={}, entityId={}, aggressive={}", monsterId, entity.getId(), aggressive);
        return entity;
    }
    
    /**
     * 创建道具实体
     *
     * @param itemId 道具配置ID
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @param quantity 数量
     * @return 道具实体
     */
    public Entity createItem(int itemId, float x, float y, float z, int quantity) {
        if (getItemCount() >= config.getMaxItems()) {
            logger.warn("道具数量已达上限，无法创建新道具: {}", config.getMaxItems());
            return null;
        }
        
        // 创建实体
        Entity entity = createEntity();
        
        // 添加基础组件
        addComponent(entity.getId(), new PositionComponent(x, y, z));
        
        // 创建简单的道具组件（这里可以扩展为更复杂的道具系统）
        // 使用自定义属性存储道具信息
        // 实际实现中可能需要专门的ItemComponent
        
        // 记录类型
        entityTypeMap.put(entity.getId(), GameEntityType.ITEM);
        
        logger.debug("创建道具实体: itemId={}, entityId={}, quantity={}", itemId, entity.getId(), quantity);
        return entity;
    }
    
    /**
     * 获取玩家实体
     *
     * @param playerId 玩家ID
     * @return 玩家实体，如果不存在返回null
     */
    public Entity getPlayerEntity(long playerId) {
        Long entityId = playerIdToEntityMap.get(playerId);
        return entityId != null ? getEntity(entityId) : null;
    }
    
    /**
     * 移除玩家
     *
     * @param playerId 玩家ID
     */
    public void removePlayer(long playerId) {
        Long entityId = playerIdToEntityMap.remove(playerId);
        if (entityId != null) {
            destroyEntity(entityId);
            entityTypeMap.remove(entityId);
            logger.info("移除玩家: playerId={}, entityId={}", playerId, entityId);
        }
    }
    
    /**
     * 获取指定类型的实体列表
     *
     * @param entityType 实体类型
     * @return 实体列表
     */
    public List<Entity> getEntitiesByType(GameEntityType entityType) {
        List<Entity> result = new ArrayList<>();
        for (Map.Entry<Long, GameEntityType> entry : entityTypeMap.entrySet()) {
            if (entry.getValue() == entityType) {
                Entity entity = getEntity(entry.getKey());
                if (entity != null && entity.isActive()) {
                    result.add(entity);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取指定范围内的实体
     *
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param centerZ 中心Z坐标
     * @param radius 半径
     * @return 范围内的实体列表
     */
    public List<Entity> getEntitiesInRange(float centerX, float centerY, float centerZ, float radius) {
        List<Entity> result = new ArrayList<>();
        
        // 这里需要遍历所有有位置组件的实体
        // 实际实现中应该使用空间索引优化查询性能
        // TODO: 实现空间索引查询
        
        return result;
    }
    
    /**
     * 检查坐标是否在世界边界内
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @return 如果在边界内返回true
     */
    public boolean isWithinBounds(float x, float y, float z) {
        return x >= -config.getWorldBoundaryX() && x <= config.getWorldBoundaryX() &&
               y >= -config.getWorldBoundaryY() && y <= config.getWorldBoundaryY() &&
               z >= 0 && z <= config.getWorldBoundaryZ();
    }
    
    /**
     * 获取玩家数量
     *
     * @return 玩家数量
     */
    public int getPlayerCount() {
        return playerIdToEntityMap.size();
    }
    
    /**
     * 获取NPC数量
     *
     * @return NPC数量
     */
    public int getNpcCount() {
        return (int) entityTypeMap.values().stream()
                .filter(type -> type == GameEntityType.NPC)
                .count();
    }
    
    /**
     * 获取怪物数量
     *
     * @return 怪物数量
     */
    public int getMonsterCount() {
        return (int) entityTypeMap.values().stream()
                .filter(type -> type == GameEntityType.MONSTER)
                .count();
    }
    
    /**
     * 获取道具数量
     *
     * @return 道具数量
     */
    public int getItemCount() {
        return (int) entityTypeMap.values().stream()
                .filter(type -> type == GameEntityType.ITEM)
                .count();
    }
    
    /**
     * 获取世界统计信息
     *
     * @return 统计信息映射
     */
    public Map<String, Object> getWorldStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sceneId", sceneId);
        stats.put("totalEntities", getEntityCount());
        stats.put("players", getPlayerCount());
        stats.put("npcs", getNpcCount());
        stats.put("monsters", getMonsterCount());
        stats.put("items", getItemCount());
        stats.put("maxPlayers", config.getMaxPlayers());
        stats.put("maxNpcs", config.getMaxNpcs());
        stats.put("maxItems", config.getMaxItems());
        stats.put("enablePvP", config.isEnablePvP());
        stats.put("worldBounds", String.format("%.1f x %.1f x %.1f", 
                config.getWorldBoundaryX() * 2, 
                config.getWorldBoundaryY() * 2, 
                config.getWorldBoundaryZ()));
        return stats;
    }
    
    @Override
    public void destroy() {
        playerIdToEntityMap.clear();
        entityTypeMap.clear();
        super.destroy();
        logger.info("游戏世界已销毁，场景ID: {}", sceneId);
    }
    
    // Getters
    public long getSceneId() { return sceneId; }
    public GameWorldConfig getConfig() { return config; }
    
    public Map<Long, Long> getPlayerIdToEntityMap() { return new HashMap<>(playerIdToEntityMap); }
    public Map<Long, GameEntityType> getEntityTypeMap() { return new HashMap<>(entityTypeMap); }
}