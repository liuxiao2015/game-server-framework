/*
 * 文件名: MainCityScene.java
 * 用途: 主城场景实现类
 * 实现内容:
 *   - 主城场景的具体逻辑实现
 *   - 安全区设置和PVP限制
 *   - NPC管理和功能建筑
 *   - 传送点和活动区域管理
 *   - 主城特有的服务和功能
 * 技术选型:
 *   - 继承Scene基类实现具体场景逻辑
 *   - 策略模式处理不同NPC功能
 *   - 定时任务管理活动区域
 * 依赖关系:
 *   - 继承Scene基类
 *   - 使用SceneConfig配置主城参数
 *   - 与NPC系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.impl;

import com.lx.gameserver.business.scene.core.Scene;
import com.lx.gameserver.business.scene.core.SceneConfig;
import com.lx.gameserver.business.scene.core.SceneType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主城场景实现
 * <p>
 * 实现主城场景的具体逻辑，包括安全区管理、NPC服务、
 * 传送点、功能建筑等主城特有的功能。主城是玩家的
 * 安全区域，提供各种基础服务和社交功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class MainCityScene extends Scene {

    /** 主城NPC列表 */
    private final Map<String, NPCInfo> npcs = new ConcurrentHashMap<>();

    /** 功能建筑列表 */
    private final Map<String, Building> buildings = new ConcurrentHashMap<>();

    /** 传送点列表 */
    private final Map<String, Portal> portals = new ConcurrentHashMap<>();

    /** 活动区域列表 */
    private final Map<String, ActivityArea> activityAreas = new ConcurrentHashMap<>();

    /** 安全区域列表 */
    private final List<SafeZone> safeZones = new ArrayList<>();

    /** 主城服务列表 */
    private final Set<String> availableServices = ConcurrentHashMap.newKeySet();

    /**
     * NPC信息
     */
    public static class NPCInfo {
        private final String npcId;
        private final String npcType;
        private final String npcName;
        private final Position position;
        private final Set<String> functions;
        private final Map<String, Object> properties;
        private boolean active;

        public NPCInfo(String npcId, String npcType, String npcName, Position position) {
            this.npcId = npcId;
            this.npcType = npcType;
            this.npcName = npcName;
            this.position = position;
            this.functions = new HashSet<>();
            this.properties = new HashMap<>();
            this.active = true;
        }

        // Getters and setters
        public String getNpcId() { return npcId; }
        public String getNpcType() { return npcType; }
        public String getNpcName() { return npcName; }
        public Position getPosition() { return position; }
        public Set<String> getFunctions() { return functions; }
        public Map<String, Object> getProperties() { return properties; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public void addFunction(String function) { functions.add(function); }
        public void removeFunction(String function) { functions.remove(function); }
        public boolean hasFunction(String function) { return functions.contains(function); }
    }

    /**
     * 功能建筑
     */
    public static class Building {
        private final String buildingId;
        private final String buildingType;
        private final String buildingName;
        private final Position position;
        private final String function;
        private boolean active;
        private final Map<String, Object> properties;

        public Building(String buildingId, String buildingType, String buildingName, 
                       Position position, String function) {
            this.buildingId = buildingId;
            this.buildingType = buildingType;
            this.buildingName = buildingName;
            this.position = position;
            this.function = function;
            this.active = true;
            this.properties = new HashMap<>();
        }

        // Getters and setters
        public String getBuildingId() { return buildingId; }
        public String getBuildingType() { return buildingType; }
        public String getBuildingName() { return buildingName; }
        public Position getPosition() { return position; }
        public String getFunction() { return function; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public Map<String, Object> getProperties() { return properties; }
    }

    /**
     * 传送点
     */
    public static class Portal {
        private final String portalId;
        private final String portalName;
        private final Position position;
        private final Long targetSceneId;
        private final Position targetPosition;
        private boolean active;
        private final Map<String, Object> conditions;

        public Portal(String portalId, String portalName, Position position, 
                     Long targetSceneId, Position targetPosition) {
            this.portalId = portalId;
            this.portalName = portalName;
            this.position = position;
            this.targetSceneId = targetSceneId;
            this.targetPosition = targetPosition;
            this.active = true;
            this.conditions = new HashMap<>();
        }

        // Getters and setters
        public String getPortalId() { return portalId; }
        public String getPortalName() { return portalName; }
        public Position getPosition() { return position; }
        public Long getTargetSceneId() { return targetSceneId; }
        public Position getTargetPosition() { return targetPosition; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public Map<String, Object> getConditions() { return conditions; }
    }

    /**
     * 活动区域
     */
    public static class ActivityArea {
        private final String areaId;
        private final String areaName;
        private final String areaType;
        private final Position centerPosition;
        private final double radius;
        private boolean active;
        private final Map<String, Object> properties;

        public ActivityArea(String areaId, String areaName, String areaType, 
                           Position centerPosition, double radius) {
            this.areaId = areaId;
            this.areaName = areaName;
            this.areaType = areaType;
            this.centerPosition = centerPosition;
            this.radius = radius;
            this.active = true;
            this.properties = new HashMap<>();
        }

        // Getters and setters
        public String getAreaId() { return areaId; }
        public String getAreaName() { return areaName; }
        public String getAreaType() { return areaType; }
        public Position getCenterPosition() { return centerPosition; }
        public double getRadius() { return radius; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public Map<String, Object> getProperties() { return properties; }

        public boolean containsPosition(Position position) {
            return centerPosition.distanceTo(position) <= radius;
        }
    }

    /**
     * 安全区域
     */
    public static class SafeZone {
        private final String zoneId;
        private final String zoneName;
        private final Position centerPosition;
        private final double radius;
        private final Set<String> restrictions;

        public SafeZone(String zoneId, String zoneName, Position centerPosition, double radius) {
            this.zoneId = zoneId;
            this.zoneName = zoneName;
            this.centerPosition = centerPosition;
            this.radius = radius;
            this.restrictions = new HashSet<>();
            
            // 默认安全区限制
            restrictions.add("no_pvp");
            restrictions.add("no_death");
        }

        // Getters
        public String getZoneId() { return zoneId; }
        public String getZoneName() { return zoneName; }
        public Position getCenterPosition() { return centerPosition; }
        public double getRadius() { return radius; }
        public Set<String> getRestrictions() { return restrictions; }

        public boolean containsPosition(Position position) {
            return centerPosition.distanceTo(position) <= radius;
        }

        public boolean hasRestriction(String restriction) {
            return restrictions.contains(restriction);
        }
    }

    /**
     * 构造函数
     *
     * @param sceneId 场景ID
     * @param sceneName 场景名称
     * @param sceneType 场景类型
     * @param config 场景配置
     */
    public MainCityScene(Long sceneId, String sceneName, SceneType sceneType, SceneConfig config) {
        super(sceneId, sceneName, sceneType, config);
        
        // 添加主城服务
        availableServices.add("bank");
        availableServices.add("shop");
        availableServices.add("warehouse");
        availableServices.add("auction");
        availableServices.add("guild");
        availableServices.add("mail");
        availableServices.add("quest");
    }

    @Override
    protected void onCreate() throws Exception {
        log.info("创建主城场景: {}", getSceneName());
        
        // 初始化安全区域
        initializeSafeZones();
        
        // 加载NPC
        loadNPCs();
        
        // 加载功能建筑
        loadBuildings();
        
        // 加载传送点
        loadPortals();
        
        // 加载活动区域
        loadActivityAreas();
        
        log.info("主城场景创建完成: {}", getSceneName());
    }

    @Override
    protected void onDestroy() throws Exception {
        log.info("销毁主城场景: {}", getSceneName());
        
        // 清理所有数据
        npcs.clear();
        buildings.clear();
        portals.clear();
        activityAreas.clear();
        safeZones.clear();
        availableServices.clear();
        
        log.info("主城场景销毁完成: {}", getSceneName());
    }

    @Override
    protected void onTick(long deltaTime) {
        // 主城场景的定时更新逻辑
        
        // 检查活动区域状态
        checkActivityAreas();
        
        // 更新NPC状态
        updateNPCs();
        
        // 检查传送点状态
        checkPortals();
    }

    @Override
    protected void onEntityEnter(Long entityId, Position position) {
        log.debug("实体进入主城: entityId={}, position={}", entityId, position);
        
        // 检查是否进入安全区域
        checkSafeZoneEntry(entityId, position);
        
        // 检查是否进入活动区域
        checkActivityAreaEntry(entityId, position);
        
        // 发送主城欢迎信息
        sendWelcomeMessage(entityId);
    }

    @Override
    protected void onEntityLeave(Long entityId) {
        log.debug("实体离开主城: entityId={}", entityId);
        
        // 清理实体相关状态
        cleanupEntityState(entityId);
    }

    @Override
    protected void onEntityMove(Long entityId, Position oldPosition, Position newPosition) {
        // 检查区域变化
        checkZoneTransition(entityId, oldPosition, newPosition);
    }

    // ========== 初始化方法 ==========

    /**
     * 初始化安全区域
     */
    private void initializeSafeZones() {
        // 创建主城中心安全区
        Position centerPosition = getConfig().getMapConfig().getDefaultSpawnPoint();
        SafeZone mainSafeZone = new SafeZone("main_safe_zone", "主城中心安全区", 
                                           centerPosition, 200.0);
        safeZones.add(mainSafeZone);
        
        log.debug("安全区域初始化完成: {}", safeZones.size());
    }

    /**
     * 加载NPC
     */
    private void loadNPCs() {
        for (SceneConfig.NpcConfig npcConfig : getConfig().getNpcs()) {
            NPCInfo npc = new NPCInfo(
                npcConfig.getNpcId(),
                npcConfig.getNpcType(),
                npcConfig.getNpcName(),
                npcConfig.getPosition()
            );
            
            // 添加NPC功能
            npc.getFunctions().addAll(npcConfig.getFunctions());
            
            npcs.put(npc.getNpcId(), npc);
        }
        
        // 如果没有配置NPC，创建默认NPC
        if (npcs.isEmpty()) {
            createDefaultNPCs();
        }
        
        log.debug("NPC加载完成: {}", npcs.size());
    }

    /**
     * 创建默认NPC
     */
    private void createDefaultNPCs() {
        Position spawnPoint = getConfig().getMapConfig().getDefaultSpawnPoint();
        
        // 银行NPC
        NPCInfo banker = new NPCInfo("npc_banker", "banker", "银行家", 
                                   new Position(spawnPoint.getX() + 10, 0, spawnPoint.getZ()));
        banker.addFunction("bank");
        npcs.put(banker.getNpcId(), banker);
        
        // 商店NPC
        NPCInfo merchant = new NPCInfo("npc_merchant", "merchant", "商人", 
                                     new Position(spawnPoint.getX() - 10, 0, spawnPoint.getZ()));
        merchant.addFunction("shop");
        npcs.put(merchant.getNpcId(), merchant);
        
        // 仓库NPC
        NPCInfo warehouse = new NPCInfo("npc_warehouse", "warehouse", "仓库管理员", 
                                      new Position(spawnPoint.getX(), 0, spawnPoint.getZ() + 10));
        warehouse.addFunction("warehouse");
        npcs.put(warehouse.getNpcId(), warehouse);
    }

    /**
     * 加载功能建筑
     */
    private void loadBuildings() {
        Position spawnPoint = getConfig().getMapConfig().getDefaultSpawnPoint();
        
        // 银行建筑
        Building bank = new Building("building_bank", "bank", "主城银行", 
                                   new Position(spawnPoint.getX() + 50, 0, spawnPoint.getZ()), "bank");
        buildings.put(bank.getBuildingId(), bank);
        
        // 拍卖行
        Building auction = new Building("building_auction", "auction", "拍卖行", 
                                      new Position(spawnPoint.getX() - 50, 0, spawnPoint.getZ()), "auction");
        buildings.put(auction.getBuildingId(), auction);
        
        // 公会大厅
        Building guild = new Building("building_guild", "guild", "公会大厅", 
                                    new Position(spawnPoint.getX(), 0, spawnPoint.getZ() + 50), "guild");
        buildings.put(guild.getBuildingId(), guild);
        
        log.debug("功能建筑加载完成: {}", buildings.size());
    }

    /**
     * 加载传送点
     */
    private void loadPortals() {
        for (SceneConfig.Portal portalConfig : getConfig().getPortals()) {
            Portal portal = new Portal(
                portalConfig.getPortalId(),
                portalConfig.getPortalName(),
                portalConfig.getPosition(),
                portalConfig.getTargetSceneId(),
                portalConfig.getTargetPosition()
            );
            
            portal.getConditions().putAll(portalConfig.getConditions());
            portals.put(portal.getPortalId(), portal);
        }
        
        log.debug("传送点加载完成: {}", portals.size());
    }

    /**
     * 加载活动区域
     */
    private void loadActivityAreas() {
        Position spawnPoint = getConfig().getMapConfig().getDefaultSpawnPoint();
        
        // 社交区域
        ActivityArea socialArea = new ActivityArea("area_social", "社交广场", "social", 
                                                  spawnPoint, 30.0);
        activityAreas.put(socialArea.getAreaId(), socialArea);
        
        // 交易区域
        ActivityArea tradeArea = new ActivityArea("area_trade", "交易市场", "trade", 
                                                new Position(spawnPoint.getX() + 30, 0, spawnPoint.getZ()), 20.0);
        activityAreas.put(tradeArea.getAreaId(), tradeArea);
        
        log.debug("活动区域加载完成: {}", activityAreas.size());
    }

    // ========== 更新方法 ==========

    /**
     * 检查活动区域状态
     */
    private void checkActivityAreas() {
        // 这里可以添加活动区域的状态检查逻辑
        // 例如：检查活动是否应该开始或结束
    }

    /**
     * 更新NPC状态
     */
    private void updateNPCs() {
        // 这里可以添加NPC状态更新逻辑
        // 例如：NPC对话状态、任务状态等
    }

    /**
     * 检查传送点状态
     */
    private void checkPortals() {
        // 这里可以添加传送点状态检查逻辑
        // 例如：传送点是否可用、冷却时间等
    }

    // ========== 事件处理方法 ==========

    /**
     * 检查安全区域进入
     */
    private void checkSafeZoneEntry(Long entityId, Position position) {
        for (SafeZone safeZone : safeZones) {
            if (safeZone.containsPosition(position)) {
                // 实体进入安全区域
                setEntityData(entityId.toString() + "_in_safe_zone", true);
                log.debug("实体进入安全区域: entityId={}, zone={}", entityId, safeZone.getZoneName());
                break;
            }
        }
    }

    /**
     * 检查活动区域进入
     */
    private void checkActivityAreaEntry(Long entityId, Position position) {
        for (ActivityArea area : activityAreas.values()) {
            if (area.isActive() && area.containsPosition(position)) {
                // 实体进入活动区域
                log.debug("实体进入活动区域: entityId={}, area={}", entityId, area.getAreaName());
                // 这里可以触发相应的活动区域事件
            }
        }
    }

    /**
     * 发送欢迎信息
     */
    private void sendWelcomeMessage(Long entityId) {
        // 这里应该通过消息系统发送欢迎信息
        log.debug("发送主城欢迎信息: entityId={}", entityId);
    }

    /**
     * 清理实体状态
     */
    private void cleanupEntityState(Long entityId) {
        // 清理实体相关的临时状态
        removeEntityData(entityId.toString() + "_in_safe_zone");
    }

    /**
     * 检查区域转换
     */
    private void checkZoneTransition(Long entityId, Position oldPosition, Position newPosition) {
        // 检查安全区域转换
        boolean wasInSafeZone = getEntityData(entityId.toString() + "_in_safe_zone", false);
        boolean isInSafeZone = false;
        
        for (SafeZone safeZone : safeZones) {
            if (safeZone.containsPosition(newPosition)) {
                isInSafeZone = true;
                break;
            }
        }
        
        if (wasInSafeZone && !isInSafeZone) {
            // 离开安全区域
            setEntityData(entityId.toString() + "_in_safe_zone", false);
            log.debug("实体离开安全区域: entityId={}", entityId);
        } else if (!wasInSafeZone && isInSafeZone) {
            // 进入安全区域
            setEntityData(entityId.toString() + "_in_safe_zone", true);
            log.debug("实体进入安全区域: entityId={}", entityId);
        }
    }

    // ========== 工具方法 ==========

    /**
     * 设置实体数据
     */
    private void setEntityData(String key, Object value) {
        setSceneData(key, value);
    }

    /**
     * 获取实体数据
     */
    @SuppressWarnings("unchecked")
    private <T> T getEntityData(String key, T defaultValue) {
        Object value = getSceneData(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 移除实体数据
     */
    private void removeEntityData(String key) {
        getSceneData().remove(key);
    }

    // ========== 公共查询方法 ==========

    /**
     * 获取NPC信息
     *
     * @param npcId NPC ID
     * @return NPC信息
     */
    public NPCInfo getNPC(String npcId) {
        return npcs.get(npcId);
    }

    /**
     * 获取所有NPC
     *
     * @return NPC列表
     */
    public Collection<NPCInfo> getAllNPCs() {
        return npcs.values();
    }

    /**
     * 获取功能建筑
     *
     * @param buildingId 建筑ID
     * @return 建筑信息
     */
    public Building getBuilding(String buildingId) {
        return buildings.get(buildingId);
    }

    /**
     * 获取所有功能建筑
     *
     * @return 建筑列表
     */
    public Collection<Building> getAllBuildings() {
        return buildings.values();
    }

    /**
     * 获取传送点
     *
     * @param portalId 传送点ID
     * @return 传送点信息
     */
    public Portal getPortal(String portalId) {
        return portals.get(portalId);
    }

    /**
     * 获取所有传送点
     *
     * @return 传送点列表
     */
    public Collection<Portal> getAllPortals() {
        return portals.values();
    }

    /**
     * 检查是否在安全区域
     *
     * @param position 位置
     * @return 是否在安全区域
     */
    public boolean isInSafeZone(Position position) {
        return safeZones.stream().anyMatch(zone -> zone.containsPosition(position));
    }

    /**
     * 检查服务是否可用
     *
     * @param service 服务名称
     * @return 是否可用
     */
    public boolean isServiceAvailable(String service) {
        return availableServices.contains(service);
    }

    /**
     * 获取可用服务列表
     *
     * @return 服务列表
     */
    public Set<String> getAvailableServices() {
        return new HashSet<>(availableServices);
    }

    @Override
    public String toString() {
        return String.format("MainCityScene{name='%s', npcs=%d, buildings=%d, portals=%d}", 
                getSceneName(), npcs.size(), buildings.size(), portals.size());
    }
}