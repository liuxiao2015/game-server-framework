/*
 * 文件名: ISceneService.java
 * 用途: 场景服务接口定义
 * 实现内容:
 *   - 定义场景进入和退出接口
 *   - 定义玩家移动和位置同步接口
 *   - 定义场景内对象管理接口
 *   - 支持场景广播和事件通知
 * 技术选型:
 *   - 使用Java接口定义服务规范
 *   - 集成Result通用返回类型
 *   - 支持实时场景数据同步
 * 依赖关系:
 *   - 依赖common-core的Result
 *   - 被scene-service模块实现
 *   - 被需要场景功能的模块调用
 */
package com.lx.gameserver.api;

import com.lx.gameserver.common.Result;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 场景服务接口
 * <p>
 * 定义了场景系统的所有核心功能，包括场景进入退出、玩家移动、
 * 对象同步、广播通知等。支持多种场景类型和实时交互功能，
 * 为游戏提供完整的场景管理能力。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public interface ISceneService {

    // ===== 场景进入退出接口 =====

    /**
     * 玩家进入场景
     *
     * @param playerId     玩家ID
     * @param sceneId      目标场景ID
     * @param entryPointId 进入点ID（可选）
     * @param enterType    进入方式
     * @param fromSceneId  来源场景ID（可选）
     * @return 进入结果，包含场景信息和初始位置
     */
    Result<EnterSceneResult> enterScene(Long playerId, Integer sceneId, Integer entryPointId, 
                                       EnterType enterType, Integer fromSceneId);

    /**
     * 异步进入场景
     *
     * @param playerId     玩家ID
     * @param sceneId      目标场景ID
     * @param entryPointId 进入点ID
     * @param enterType    进入方式
     * @param fromSceneId  来源场景ID
     * @return 进入结果的Future
     */
    CompletableFuture<Result<EnterSceneResult>> enterSceneAsync(Long playerId, Integer sceneId, 
                                                               Integer entryPointId, EnterType enterType, 
                                                               Integer fromSceneId);

    /**
     * 玩家离开场景
     *
     * @param playerId  玩家ID
     * @param leaveType 离开方式
     * @param reason    离开原因
     * @return 离开结果
     */
    Result<Void> leaveScene(Long playerId, LeaveType leaveType, String reason);

    /**
     * 传送玩家到指定场景位置
     *
     * @param playerId 玩家ID
     * @param sceneId  目标场景ID
     * @param position 目标位置
     * @return 传送结果
     */
    Result<Void> teleportPlayer(Long playerId, Integer sceneId, Position position);

    /**
     * 切换场景线路
     *
     * @param playerId 玩家ID
     * @param lineId   目标线路ID
     * @return 切换结果
     */
    Result<Void> switchSceneLine(Long playerId, Integer lineId);

    // ===== 玩家移动接口 =====

    /**
     * 玩家移动
     *
     * @param playerId       玩家ID
     * @param targetPosition 目标位置
     * @param moveSpeed      移动速度
     * @param path           移动路径（可选，用于验证）
     * @return 移动结果，包含服务器确认的位置
     */
    Result<MoveResult> playerMove(Long playerId, Position targetPosition, Float moveSpeed, 
                                 List<Position> path);

    /**
     * 同步玩家位置
     *
     * @param playerId 玩家ID
     * @param position 当前位置
     * @param direction 朝向角度
     * @return 同步结果
     */
    Result<Void> syncPlayerPosition(Long playerId, Position position, Float direction);

    /**
     * 停止玩家移动
     *
     * @param playerId 玩家ID
     * @param position 停止位置
     * @return 操作结果
     */
    Result<Void> stopPlayerMove(Long playerId, Position position);

    /**
     * 获取玩家当前位置
     *
     * @param playerId 玩家ID
     * @return 玩家位置信息
     */
    Result<PlayerPosition> getPlayerPosition(Long playerId);

    /**
     * 批量获取玩家位置
     *
     * @param playerIds 玩家ID列表
     * @return 玩家位置映射
     */
    Result<Map<Long, PlayerPosition>> getPlayersPosition(List<Long> playerIds);

    // ===== 场景信息接口 =====

    /**
     * 获取场景信息
     *
     * @param sceneId 场景ID
     * @return 场景信息
     */
    Result<SceneInfo> getSceneInfo(Integer sceneId);

    /**
     * 获取场景内玩家列表
     *
     * @param sceneId 场景ID
     * @return 场景内玩家列表
     */
    Result<List<ScenePlayer>> getScenePlayers(Integer sceneId);

    /**
     * 获取场景内NPC列表
     *
     * @param sceneId 场景ID
     * @return 场景内NPC列表
     */
    Result<List<SceneNpc>> getSceneNpcs(Integer sceneId);

    /**
     * 获取场景内物品列表
     *
     * @param sceneId 场景ID
     * @return 场景内物品列表
     */
    Result<List<SceneItem>> getSceneItems(Integer sceneId);

    /**
     * 获取玩家附近的对象
     *
     * @param playerId 玩家ID
     * @param radius   搜索半径
     * @return 附近对象信息
     */
    Result<NearbyObjects> getNearbyObjects(Long playerId, Float radius);

    // ===== 场景对象管理接口 =====

    /**
     * 在场景中创建NPC
     *
     * @param sceneId      场景ID
     * @param npcConfigId  NPC配置ID
     * @param position     生成位置
     * @param properties   NPC属性
     * @return 创建结果，包含NPC实例ID
     */
    Result<Long> createSceneNpc(Integer sceneId, Integer npcConfigId, Position position, 
                               Map<String, Object> properties);

    /**
     * 移除场景中的NPC
     *
     * @param sceneId 场景ID
     * @param npcId   NPC实例ID
     * @return 移除结果
     */
    Result<Void> removeSceneNpc(Integer sceneId, Long npcId);

    /**
     * 在场景中掉落物品
     *
     * @param sceneId      场景ID
     * @param itemConfigId 物品配置ID
     * @param position     掉落位置
     * @param quantity     物品数量
     * @param ownerId      归属玩家ID（可选）
     * @return 掉落结果，包含物品实例ID
     */
    Result<Long> dropSceneItem(Integer sceneId, Integer itemConfigId, Position position, 
                              Integer quantity, Long ownerId);

    /**
     * 玩家拾取场景物品
     *
     * @param playerId 玩家ID
     * @param itemId   物品实例ID
     * @return 拾取结果
     */
    Result<PickupResult> pickupSceneItem(Long playerId, Long itemId);

    /**
     * 创建场景特效
     *
     * @param sceneId        场景ID
     * @param effectConfigId 特效配置ID
     * @param position       特效位置
     * @param duration       持续时间（毫秒）
     * @param parameters     特效参数
     * @return 创建结果，包含特效ID
     */
    Result<Long> createSceneEffect(Integer sceneId, Integer effectConfigId, Position position, 
                                  Integer duration, Map<String, String> parameters);

    // ===== 场景广播接口 =====

    /**
     * 向场景内所有玩家广播消息
     *
     * @param sceneId 场景ID
     * @param message 广播消息
     * @param data    消息数据
     * @return 广播结果
     */
    Result<Void> broadcastToScene(Integer sceneId, String message, Object data);

    /**
     * 向玩家附近区域广播消息
     *
     * @param playerId 玩家ID
     * @param radius   广播半径
     * @param message  广播消息
     * @param data     消息数据
     * @return 广播结果
     */
    Result<Void> broadcastToNearby(Long playerId, Float radius, String message, Object data);

    /**
     * 向指定玩家列表广播消息
     *
     * @param playerIds 玩家ID列表
     * @param message   广播消息
     * @param data      消息数据
     * @return 广播结果
     */
    Result<Void> broadcastToPlayers(List<Long> playerIds, String message, Object data);

    /**
     * 场景内聊天广播
     *
     * @param sceneId   场景ID
     * @param senderId  发送者ID
     * @param content   聊天内容
     * @param radius    广播半径（可选，为空则全场景广播）
     * @return 广播结果
     */
    Result<Void> broadcastSceneChat(Integer sceneId, Long senderId, String content, Float radius);

    // ===== 场景管理接口 =====

    /**
     * 创建场景实例
     *
     * @param sceneConfigId 场景配置ID
     * @param sceneType     场景类型
     * @param maxCapacity   最大容量
     * @param properties    场景属性
     * @return 创建结果，包含场景实例ID
     */
    Result<Integer> createSceneInstance(Integer sceneConfigId, SceneType sceneType, 
                                       Integer maxCapacity, Map<String, Object> properties);

    /**
     * 销毁场景实例
     *
     * @param sceneId 场景ID
     * @param reason  销毁原因
     * @return 销毁结果
     */
    Result<Void> destroySceneInstance(Integer sceneId, String reason);

    /**
     * 设置场景状态
     *
     * @param sceneId 场景ID
     * @param status  场景状态
     * @return 设置结果
     */
    Result<Void> setSceneStatus(Integer sceneId, SceneStatus status);

    /**
     * 获取场景统计信息
     *
     * @param sceneId 场景ID
     * @return 场景统计信息
     */
    Result<SceneStatistics> getSceneStatistics(Integer sceneId);

    // ===== 内部数据结构定义 =====

    /**
     * 进入场景方式枚举
     */
    enum EnterType {
        /** 正常进入 */
        NORMAL,
        /** 传送进入 */
        TELEPORT,
        /** 复活进入 */
        REVIVE,
        /** 登录进入 */
        LOGIN,
        /** 切换线路 */
        SWITCH_LINE
    }

    /**
     * 离开场景方式枚举
     */
    enum LeaveType {
        /** 正常离开 */
        NORMAL,
        /** 传送离开 */
        TELEPORT,
        /** 掉线离开 */
        DISCONNECT,
        /** 被踢出 */
        KICKED,
        /** 死亡离开 */
        DEATH
    }

    /**
     * 场景类型枚举
     */
    enum SceneType {
        /** 普通场景 */
        NORMAL,
        /** 副本场景 */
        DUNGEON,
        /** PVP场景 */
        PVP,
        /** 主城场景 */
        CITY,
        /** 野外场景 */
        FIELD
    }

    /**
     * 场景状态枚举
     */
    enum SceneStatus {
        /** 正常运行 */
        NORMAL,
        /** 准备中 */
        PREPARING,
        /** 已满员 */
        FULL,
        /** 维护中 */
        MAINTENANCE,
        /** 关闭中 */
        CLOSING
    }

    /**
     * 位置信息
     */
    class Position {
        /** X坐标 */
        private Float x;
        /** Y坐标 */
        private Float y;
        /** Z坐标 */
        private Float z;

        public Position() {}

        public Position(Float x, Float y, Float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        // Getters and Setters
        public Float getX() { return x; }
        public void setX(Float x) { this.x = x; }
        public Float getY() { return y; }
        public void setY(Float y) { this.y = y; }
        public Float getZ() { return z; }
        public void setZ(Float z) { this.z = z; }
    }

    /**
     * 进入场景结果
     */
    class EnterSceneResult {
        /** 场景信息 */
        private SceneInfo sceneInfo;
        /** 初始位置 */
        private Position initialPosition;
        /** 场景内其他玩家 */
        private List<ScenePlayer> otherPlayers;
        /** 场景内NPC */
        private List<SceneNpc> npcs;
        /** 场景内物品 */
        private List<SceneItem> items;

        // Getters and Setters
        public SceneInfo getSceneInfo() { return sceneInfo; }
        public void setSceneInfo(SceneInfo sceneInfo) { this.sceneInfo = sceneInfo; }
        public Position getInitialPosition() { return initialPosition; }
        public void setInitialPosition(Position initialPosition) { this.initialPosition = initialPosition; }
        public List<ScenePlayer> getOtherPlayers() { return otherPlayers; }
        public void setOtherPlayers(List<ScenePlayer> otherPlayers) { this.otherPlayers = otherPlayers; }
        public List<SceneNpc> getNpcs() { return npcs; }
        public void setNpcs(List<SceneNpc> npcs) { this.npcs = npcs; }
        public List<SceneItem> getItems() { return items; }
        public void setItems(List<SceneItem> items) { this.items = items; }
    }

    /**
     * 移动结果
     */
    class MoveResult {
        /** 服务器确认的位置 */
        private Position confirmedPosition;
        /** 移动是否成功 */
        private Boolean success;
        /** 服务器时间戳 */
        private Long serverTimestamp;

        // Getters and Setters
        public Position getConfirmedPosition() { return confirmedPosition; }
        public void setConfirmedPosition(Position confirmedPosition) { this.confirmedPosition = confirmedPosition; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Long getServerTimestamp() { return serverTimestamp; }
        public void setServerTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; }
    }

    /**
     * 玩家位置信息
     */
    class PlayerPosition {
        /** 玩家ID */
        private Long playerId;
        /** 当前位置 */
        private Position position;
        /** 朝向角度 */
        private Float direction;
        /** 移动状态 */
        private String moveState;
        /** 更新时间 */
        private Long updateTime;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
        public Float getDirection() { return direction; }
        public void setDirection(Float direction) { this.direction = direction; }
        public String getMoveState() { return moveState; }
        public void setMoveState(String moveState) { this.moveState = moveState; }
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
    }

    /**
     * 场景信息
     */
    class SceneInfo {
        /** 场景ID */
        private Integer sceneId;
        /** 场景名称 */
        private String sceneName;
        /** 场景类型 */
        private SceneType sceneType;
        /** 场景状态 */
        private SceneStatus sceneStatus;
        /** 当前玩家数量 */
        private Integer currentPlayers;
        /** 最大玩家容量 */
        private Integer maxCapacity;
        /** 场景配置 */
        private Map<String, Object> sceneConfig;
        /** 创建时间 */
        private Long createTime;

        // Getters and Setters
        public Integer getSceneId() { return sceneId; }
        public void setSceneId(Integer sceneId) { this.sceneId = sceneId; }
        public String getSceneName() { return sceneName; }
        public void setSceneName(String sceneName) { this.sceneName = sceneName; }
        public SceneType getSceneType() { return sceneType; }
        public void setSceneType(SceneType sceneType) { this.sceneType = sceneType; }
        public SceneStatus getSceneStatus() { return sceneStatus; }
        public void setSceneStatus(SceneStatus sceneStatus) { this.sceneStatus = sceneStatus; }
        public Integer getCurrentPlayers() { return currentPlayers; }
        public void setCurrentPlayers(Integer currentPlayers) { this.currentPlayers = currentPlayers; }
        public Integer getMaxCapacity() { return maxCapacity; }
        public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }
        public Map<String, Object> getSceneConfig() { return sceneConfig; }
        public void setSceneConfig(Map<String, Object> sceneConfig) { this.sceneConfig = sceneConfig; }
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }

    /**
     * 场景中的玩家
     */
    class ScenePlayer {
        /** 玩家ID */
        private Long playerId;
        /** 玩家名称 */
        private String playerName;
        /** 玩家等级 */
        private Integer level;
        /** 当前位置 */
        private Position position;
        /** 朝向角度 */
        private Float direction;
        /** 移动状态 */
        private String moveState;
        /** 玩家状态 */
        private String playerState;
        /** 头像 */
        private String avatar;
        /** VIP等级 */
        private Integer vipLevel;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
        public Float getDirection() { return direction; }
        public void setDirection(Float direction) { this.direction = direction; }
        public String getMoveState() { return moveState; }
        public void setMoveState(String moveState) { this.moveState = moveState; }
        public String getPlayerState() { return playerState; }
        public void setPlayerState(String playerState) { this.playerState = playerState; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public Integer getVipLevel() { return vipLevel; }
        public void setVipLevel(Integer vipLevel) { this.vipLevel = vipLevel; }
    }

    /**
     * 场景中的NPC
     */
    class SceneNpc {
        /** NPC实例ID */
        private Long npcId;
        /** NPC配置ID */
        private Integer npcConfigId;
        /** NPC名称 */
        private String npcName;
        /** 当前位置 */
        private Position position;
        /** 朝向角度 */
        private Float direction;
        /** NPC状态 */
        private String npcState;
        /** NPC等级 */
        private Integer level;
        /** 当前血量 */
        private Integer currentHp;
        /** 最大血量 */
        private Integer maxHp;

        // Getters and Setters
        public Long getNpcId() { return npcId; }
        public void setNpcId(Long npcId) { this.npcId = npcId; }
        public Integer getNpcConfigId() { return npcConfigId; }
        public void setNpcConfigId(Integer npcConfigId) { this.npcConfigId = npcConfigId; }
        public String getNpcName() { return npcName; }
        public void setNpcName(String npcName) { this.npcName = npcName; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
        public Float getDirection() { return direction; }
        public void setDirection(Float direction) { this.direction = direction; }
        public String getNpcState() { return npcState; }
        public void setNpcState(String npcState) { this.npcState = npcState; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Integer getCurrentHp() { return currentHp; }
        public void setCurrentHp(Integer currentHp) { this.currentHp = currentHp; }
        public Integer getMaxHp() { return maxHp; }
        public void setMaxHp(Integer maxHp) { this.maxHp = maxHp; }
    }

    /**
     * 场景中的物品
     */
    class SceneItem {
        /** 物品实例ID */
        private Long itemInstanceId;
        /** 物品配置ID */
        private Integer itemConfigId;
        /** 物品名称 */
        private String itemName;
        /** 物品位置 */
        private Position position;
        /** 物品数量 */
        private Integer quantity;
        /** 掉落时间 */
        private Long dropTime;
        /** 归属玩家ID */
        private Long ownerPlayerId;
        /** 拾取保护时间 */
        private Long pickupProtectTime;

        // Getters and Setters
        public Long getItemInstanceId() { return itemInstanceId; }
        public void setItemInstanceId(Long itemInstanceId) { this.itemInstanceId = itemInstanceId; }
        public Integer getItemConfigId() { return itemConfigId; }
        public void setItemConfigId(Integer itemConfigId) { this.itemConfigId = itemConfigId; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Long getDropTime() { return dropTime; }
        public void setDropTime(Long dropTime) { this.dropTime = dropTime; }
        public Long getOwnerPlayerId() { return ownerPlayerId; }
        public void setOwnerPlayerId(Long ownerPlayerId) { this.ownerPlayerId = ownerPlayerId; }
        public Long getPickupProtectTime() { return pickupProtectTime; }
        public void setPickupProtectTime(Long pickupProtectTime) { this.pickupProtectTime = pickupProtectTime; }
    }

    /**
     * 附近对象信息
     */
    class NearbyObjects {
        /** 附近玩家 */
        private List<ScenePlayer> nearbyPlayers;
        /** 附近NPC */
        private List<SceneNpc> nearbyNpcs;
        /** 附近物品 */
        private List<SceneItem> nearbyItems;

        // Getters and Setters
        public List<ScenePlayer> getNearbyPlayers() { return nearbyPlayers; }
        public void setNearbyPlayers(List<ScenePlayer> nearbyPlayers) { this.nearbyPlayers = nearbyPlayers; }
        public List<SceneNpc> getNearbyNpcs() { return nearbyNpcs; }
        public void setNearbyNpcs(List<SceneNpc> nearbyNpcs) { this.nearbyNpcs = nearbyNpcs; }
        public List<SceneItem> getNearbyItems() { return nearbyItems; }
        public void setNearbyItems(List<SceneItem> nearbyItems) { this.nearbyItems = nearbyItems; }
    }

    /**
     * 拾取结果
     */
    class PickupResult {
        /** 拾取是否成功 */
        private Boolean success;
        /** 拾取的物品信息 */
        private SceneItem pickedItem;
        /** 失败原因 */
        private String failReason;

        // Getters and Setters
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public SceneItem getPickedItem() { return pickedItem; }
        public void setPickedItem(SceneItem pickedItem) { this.pickedItem = pickedItem; }
        public String getFailReason() { return failReason; }
        public void setFailReason(String failReason) { this.failReason = failReason; }
    }

    /**
     * 场景统计信息
     */
    class SceneStatistics {
        /** 场景ID */
        private Integer sceneId;
        /** 当前玩家数 */
        private Integer currentPlayerCount;
        /** 峰值玩家数 */
        private Integer peakPlayerCount;
        /** NPC数量 */
        private Integer npcCount;
        /** 物品数量 */
        private Integer itemCount;
        /** 创建时间 */
        private Long createTime;
        /** 运行时长（秒） */
        private Long uptime;

        // Getters and Setters
        public Integer getSceneId() { return sceneId; }
        public void setSceneId(Integer sceneId) { this.sceneId = sceneId; }
        public Integer getCurrentPlayerCount() { return currentPlayerCount; }
        public void setCurrentPlayerCount(Integer currentPlayerCount) { this.currentPlayerCount = currentPlayerCount; }
        public Integer getPeakPlayerCount() { return peakPlayerCount; }
        public void setPeakPlayerCount(Integer peakPlayerCount) { this.peakPlayerCount = peakPlayerCount; }
        public Integer getNpcCount() { return npcCount; }
        public void setNpcCount(Integer npcCount) { this.npcCount = npcCount; }
        public Integer getItemCount() { return itemCount; }
        public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
        public Long getUptime() { return uptime; }
        public void setUptime(Long uptime) { this.uptime = uptime; }
    }
}