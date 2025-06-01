/*
 * 文件名: Scene.java
 * 用途: 场景基类
 * 实现内容:
 *   - 场景的基础属性和配置管理
 *   - 场景实体管理和AOI功能
 *   - 场景事件处理和状态同步
 *   - 场景切换和生命周期管理
 *   - 多线程安全的场景操作
 * 技术选型:
 *   - ECS架构进行实体管理
 *   - 空间索引优化AOI查询
 *   - 事件驱动架构处理场景事件
 *   - 读写锁保证并发安全
 * 依赖关系:
 *   - 被SceneManager管理和调度
 *   - 与Player实体协作进行玩家管理
 *   - 集成ECS World进行实体处理
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.scene;

import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.business.logic.player.Player;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 场景基类
 * <p>
 * 提供场景管理的基础功能，包括玩家进入离开、实体管理、
 * AOI（Area of Interest）处理、事件广播等核心功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Data
public abstract class Scene {

    /**
     * 场景状态枚举
     */
    public enum SceneState {
        /** 创建中 */
        CREATING,
        /** 运行中 */
        RUNNING,
        /** 暂停中 */
        PAUSED,
        /** 维护中 */
        MAINTENANCE,
        /** 销毁中 */
        DESTROYING,
        /** 已销毁 */
        DESTROYED
    }

    /**
     * 场景类型枚举
     */
    public enum SceneType {
        /** 城镇 */
        TOWN,
        /** 副本 */
        DUNGEON,
        /** 野外 */
        FIELD,
        /** 竞技场 */
        ARENA,
        /** 公会领地 */
        GUILD_TERRITORY,
        /** 特殊场景 */
        SPECIAL
    }

    /**
     * 场景事件基类
     */
    public static abstract class SceneEvent {
        private final Long sceneId;
        private final LocalDateTime timestamp;

        public SceneEvent(Long sceneId) {
            this.sceneId = sceneId;
            this.timestamp = LocalDateTime.now();
        }

        public Long getSceneId() {
            return sceneId;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 玩家进入场景事件
     */
    public static class PlayerEnterSceneEvent extends SceneEvent {
        private final Long playerId;

        public PlayerEnterSceneEvent(Long sceneId, Long playerId) {
            super(sceneId);
            this.playerId = playerId;
        }

        public Long getPlayerId() {
            return playerId;
        }
    }

    /**
     * 玩家离开场景事件
     */
    public static class PlayerLeaveSceneEvent extends SceneEvent {
        private final Long playerId;
        private final String reason;

        public PlayerLeaveSceneEvent(Long sceneId, Long playerId, String reason) {
            super(sceneId);
            this.playerId = playerId;
            this.reason = reason;
        }

        public Long getPlayerId() {
            return playerId;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 实体进入场景事件
     */
    public static class EntityEnterSceneEvent extends SceneEvent {
        private final Long entityId;

        public EntityEnterSceneEvent(Long sceneId, Long entityId) {
            super(sceneId);
            this.entityId = entityId;
        }

        public Long getEntityId() {
            return entityId;
        }
    }

    /**
     * 实体离开场景事件
     */
    public static class EntityLeaveSceneEvent extends SceneEvent {
        private final Long entityId;

        public EntityLeaveSceneEvent(Long sceneId, Long entityId) {
            super(sceneId);
            this.entityId = entityId;
        }

        public Long getEntityId() {
            return entityId;
        }
    }

    /**
     * 位置信息
     */
    @Data
    public static class Position {
        private float x;
        private float y;
        private float z;
        private float rotation;

        public Position() {}

        public Position(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = 0.0f;
        }

        public Position(float x, float y, float z, float rotation) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
        }

        /**
         * 计算与另一个位置的距离
         */
        public double distanceTo(Position other) {
            if (other == null) return Double.MAX_VALUE;
            
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /**
         * 检查是否在指定范围内
         */
        public boolean isWithinRange(Position other, double range) {
            return distanceTo(other) <= range;
        }

        @Override
        public String toString() {
            return String.format("Position{x=%.2f, y=%.2f, z=%.2f, rotation=%.2f}", x, y, z, rotation);
        }
    }

    /** 场景ID */
    private final Long sceneId;

    /** 场景名称 */
    private String sceneName;

    /** 场景类型 */
    private SceneType sceneType;

    /** 场景状态 */
    private volatile SceneState state = SceneState.CREATING;

    /** 场景容量 */
    private int capacity = 500;

    /** AOI范围 */
    private double aoiRange = 100.0;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 最后更新时间 */
    private volatile LocalDateTime lastUpdateTime;

    /** 场景中的玩家 */
    private final Map<Long, Player> players = new ConcurrentHashMap<>();

    /** 玩家位置信息 */
    private final Map<Long, Position> playerPositions = new ConcurrentHashMap<>();

    /** 场景中的实体 */
    private final Set<Long> entities = ConcurrentHashMap.newKeySet();

    /** 实体位置信息 */
    private final Map<Long, Position> entityPositions = new ConcurrentHashMap<>();

    /** 场景数据 */
    private final Map<String, Object> sceneData = new ConcurrentHashMap<>();

    /** 玩家数量计数器 */
    private final AtomicInteger playerCount = new AtomicInteger(0);

    /** 实体数量计数器 */
    private final AtomicInteger entityCount = new AtomicInteger(0);

    /** 消息发送计数器 */
    private final AtomicLong messageSentCount = new AtomicLong(0);

    /** 读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** 事件监听器 */
    private final List<SceneEventListener> eventListeners = new ArrayList<>();

    /**
     * 构造函数
     */
    public Scene(Long sceneId, String sceneName, SceneType sceneType) {
        this.sceneId = sceneId;
        this.sceneName = sceneName;
        this.sceneType = sceneType;
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = createTime;
    }

    /**
     * 初始化场景
     */
    public void initialize() throws Exception {
        log.info("初始化场景: {} (ID: {})", sceneName, sceneId);
        
        try {
            // 调用子类初始化方法
            doInitialize();
            
            state = SceneState.RUNNING;
            log.info("场景 {} 初始化完成", sceneName);
            
        } catch (Exception e) {
            log.error("场景 {} 初始化失败", sceneName, e);
            state = SceneState.DESTROYED;
            throw e;
        }
    }

    /**
     * 销毁场景
     */
    public void destroy() {
        log.info("销毁场景: {} (ID: {})", sceneName, sceneId);
        
        state = SceneState.DESTROYING;
        
        try {
            // 移除所有玩家
            removeAllPlayers("场景销毁");
            
            // 移除所有实体
            removeAllEntities();
            
            // 调用子类销毁方法
            doDestroy();
            
            // 清理数据
            sceneData.clear();
            eventListeners.clear();
            
            state = SceneState.DESTROYED;
            log.info("场景 {} 销毁完成", sceneName);
            
        } catch (Exception e) {
            log.error("场景 {} 销毁失败", sceneName, e);
        }
    }

    /**
     * 玩家进入场景
     */
    public boolean playerEnter(Player player, Position position) {
        if (player == null) {
            log.warn("玩家对象为空，无法进入场景 {}", sceneId);
            return false;
        }

        Long playerId = player.getPlayerId();
        
        lock.writeLock().lock();
        try {
            // 检查场景状态
            if (state != SceneState.RUNNING) {
                log.warn("场景 {} 不在运行状态，玩家 {} 无法进入", sceneId, playerId);
                return false;
            }

            // 检查容量限制
            if (playerCount.get() >= capacity) {
                log.warn("场景 {} 已满，玩家 {} 无法进入", sceneId, playerId);
                return false;
            }

            // 检查玩家是否已在场景中
            if (players.containsKey(playerId)) {
                log.warn("玩家 {} 已在场景 {} 中", playerId, sceneId);
                return false;
            }

            // 添加玩家
            players.put(playerId, player);
            if (position != null) {
                playerPositions.put(playerId, position);
            }
            
            playerCount.incrementAndGet();
            lastUpdateTime = LocalDateTime.now();

            // 调用子类方法
            onPlayerEnter(player, position);

            // 发布事件
            publishEvent(new PlayerEnterSceneEvent(sceneId, playerId));

            log.info("玩家 {} 进入场景 {}，当前人数: {}", playerId, sceneId, playerCount.get());
            return true;

        } catch (Exception e) {
            log.error("玩家 {} 进入场景 {} 失败", playerId, sceneId, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 玩家离开场景
     */
    public boolean playerLeave(Long playerId, String reason) {
        if (playerId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Player player = players.remove(playerId);
            if (player == null) {
                log.warn("玩家 {} 不在场景 {} 中", playerId, sceneId);
                return false;
            }

            // 移除位置信息
            playerPositions.remove(playerId);
            
            playerCount.decrementAndGet();
            lastUpdateTime = LocalDateTime.now();

            // 调用子类方法
            onPlayerLeave(player, reason);

            // 发布事件
            publishEvent(new PlayerLeaveSceneEvent(sceneId, playerId, reason));

            log.info("玩家 {} 离开场景 {}，原因: {}，当前人数: {}", 
                    playerId, sceneId, reason, playerCount.get());
            return true;

        } catch (Exception e) {
            log.error("玩家 {} 离开场景 {} 失败", playerId, sceneId, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新玩家位置
     */
    public void updatePlayerPosition(Long playerId, Position position) {
        if (playerId == null || position == null) {
            return;
        }

        lock.readLock().lock();
        try {
            if (players.containsKey(playerId)) {
                Position oldPosition = playerPositions.put(playerId, position);
                
                // 调用子类方法
                onPlayerPositionUpdate(playerId, oldPosition, position);
                
                // 处理AOI更新
                updateAOI(playerId, position);
                
                lastUpdateTime = LocalDateTime.now();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 实体进入场景
     */
    public boolean entityEnter(Long entityId, Position position) {
        if (entityId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (entities.add(entityId)) {
                if (position != null) {
                    entityPositions.put(entityId, position);
                }
                
                entityCount.incrementAndGet();
                lastUpdateTime = LocalDateTime.now();

                // 调用子类方法
                onEntityEnter(entityId, position);

                // 发布事件
                publishEvent(new EntityEnterSceneEvent(sceneId, entityId));

                log.debug("实体 {} 进入场景 {}", entityId, sceneId);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("实体 {} 进入场景 {} 失败", entityId, sceneId, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 实体离开场景
     */
    public boolean entityLeave(Long entityId) {
        if (entityId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (entities.remove(entityId)) {
                entityPositions.remove(entityId);
                entityCount.decrementAndGet();
                lastUpdateTime = LocalDateTime.now();

                // 调用子类方法
                onEntityLeave(entityId);

                // 发布事件
                publishEvent(new EntityLeaveSceneEvent(sceneId, entityId));

                log.debug("实体 {} 离开场景 {}", entityId, sceneId);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("实体 {} 离开场景 {} 失败", entityId, sceneId, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 广播消息给所有玩家
     */
    public void broadcast(Object message) {
        broadcast(message, null);
    }

    /**
     * 广播消息给指定条件的玩家
     */
    public void broadcast(Object message, java.util.function.Predicate<Player> filter) {
        lock.readLock().lock();
        try {
            List<Player> targetPlayers = new ArrayList<>();
            
            for (Player player : players.values()) {
                if (filter == null || filter.test(player)) {
                    targetPlayers.add(player);
                }
            }

            for (Player player : targetPlayers) {
                try {
                    sendMessageToPlayer(player.getPlayerId(), message);
                } catch (Exception e) {
                    log.warn("向玩家 {} 发送消息失败", player.getPlayerId(), e);
                }
            }

            messageSentCount.addAndGet(targetPlayers.size());
            log.debug("场景 {} 广播消息给 {} 个玩家", sceneId, targetPlayers.size());

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 范围广播
     */
    public void broadcastInRange(Position center, double range, Object message) {
        lock.readLock().lock();
        try {
            int count = 0;
            for (Map.Entry<Long, Position> entry : playerPositions.entrySet()) {
                Position playerPos = entry.getValue();
                if (playerPos != null && center.isWithinRange(playerPos, range)) {
                    try {
                        sendMessageToPlayer(entry.getKey(), message);
                        count++;
                    } catch (Exception e) {
                        log.warn("向玩家 {} 发送范围消息失败", entry.getKey(), e);
                    }
                }
            }

            messageSentCount.addAndGet(count);
            log.debug("场景 {} 在范围 {:.2f} 内广播消息给 {} 个玩家", sceneId, range, count);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取玩家列表
     */
    public List<Player> getPlayers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(players.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取玩家
     */
    public Player getPlayer(Long playerId) {
        lock.readLock().lock();
        try {
            return players.get(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 检查玩家是否在场景中
     */
    public boolean containsPlayer(Long playerId) {
        lock.readLock().lock();
        try {
            return players.containsKey(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取玩家位置
     */
    public Position getPlayerPosition(Long playerId) {
        lock.readLock().lock();
        try {
            return playerPositions.get(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取范围内的玩家
     */
    public List<Player> getPlayersInRange(Position center, double range) {
        lock.readLock().lock();
        try {
            List<Player> result = new ArrayList<>();
            for (Map.Entry<Long, Position> entry : playerPositions.entrySet()) {
                Position playerPos = entry.getValue();
                if (playerPos != null && center.isWithinRange(playerPos, range)) {
                    Player player = players.get(entry.getKey());
                    if (player != null) {
                        result.add(player);
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 设置场景数据
     */
    public void setSceneData(String key, Object value) {
        sceneData.put(key, value);
    }

    /**
     * 获取场景数据
     */
    public Object getSceneData(String key) {
        return sceneData.get(key);
    }

    /**
     * 获取场景数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getSceneData(String key, T defaultValue) {
        Object value = sceneData.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 添加事件监听器
     */
    public void addEventListener(SceneEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(SceneEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * 移除所有玩家
     */
    private void removeAllPlayers(String reason) {
        lock.writeLock().lock();
        try {
            List<Long> playerIds = new ArrayList<>(players.keySet());
            for (Long playerId : playerIds) {
                playerLeave(playerId, reason);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 移除所有实体
     */
    private void removeAllEntities() {
        lock.writeLock().lock();
        try {
            List<Long> entityIds = new ArrayList<>(entities);
            for (Long entityId : entityIds) {
                entityLeave(entityId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新AOI
     */
    private void updateAOI(Long playerId, Position position) {
        // AOI更新逻辑，这里简化处理
        // 实际实现中可能需要更复杂的空间索引算法
    }

    /**
     * 发送消息给玩家
     */
    protected void sendMessageToPlayer(Long playerId, Object message) {
        // 这里应该通过Actor系统或网络连接发送消息
        log.debug("发送消息给玩家 {}: {}", playerId, message.getClass().getSimpleName());
    }

    /**
     * 发布事件
     */
    private void publishEvent(SceneEvent event) {
        for (SceneEventListener listener : eventListeners) {
            try {
                listener.onSceneEvent(event);
            } catch (Exception e) {
                log.warn("处理场景事件失败", e);
            }
        }
    }

    /**
     * 获取场景统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sceneId", sceneId);
        stats.put("sceneName", sceneName);
        stats.put("sceneType", sceneType.name());
        stats.put("state", state.name());
        stats.put("playerCount", playerCount.get());
        stats.put("entityCount", entityCount.get());
        stats.put("capacity", capacity);
        stats.put("aoiRange", aoiRange);
        stats.put("messageSentCount", messageSentCount.get());
        stats.put("createTime", createTime);
        stats.put("lastUpdateTime", lastUpdateTime);
        stats.put("uptime", Duration.between(createTime, LocalDateTime.now()).toMillis());
        return stats;
    }

    /**
     * 检查场景是否为空
     */
    public boolean isEmpty() {
        return playerCount.get() == 0;
    }

    /**
     * 检查场景是否已满
     */
    public boolean isFull() {
        return playerCount.get() >= capacity;
    }

    /**
     * 检查场景是否运行中
     */
    public boolean isRunning() {
        return state == SceneState.RUNNING;
    }

    // ========== 子类需要实现的抽象方法 ==========

    /**
     * 子类初始化方法
     */
    protected abstract void doInitialize() throws Exception;

    /**
     * 子类销毁方法
     */
    protected abstract void doDestroy() throws Exception;

    /**
     * 玩家进入场景回调
     */
    protected abstract void onPlayerEnter(Player player, Position position);

    /**
     * 玩家离开场景回调
     */
    protected abstract void onPlayerLeave(Player player, String reason);

    /**
     * 玩家位置更新回调
     */
    protected abstract void onPlayerPositionUpdate(Long playerId, Position oldPosition, Position newPosition);

    /**
     * 实体进入场景回调
     */
    protected abstract void onEntityEnter(Long entityId, Position position);

    /**
     * 实体离开场景回调
     */
    protected abstract void onEntityLeave(Long entityId);

    /**
     * 场景事件监听器接口
     */
    public interface SceneEventListener {
        /**
         * 处理场景事件
         */
        void onSceneEvent(SceneEvent event);
    }

    @Override
    public String toString() {
        return String.format("Scene{id=%d, name='%s', type=%s, state=%s, players=%d/%d}",
                sceneId, sceneName, sceneType, state, playerCount.get(), capacity);
    }
}