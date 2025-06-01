/*
 * 文件名: Scene.java
 * 用途: 场景基类抽象实现
 * 实现内容:
 *   - 场景的基础属性和配置管理
 *   - 场景实体管理和生命周期控制
 *   - 场景状态管理和事件处理
 *   - 多线程安全的并发访问控制
 *   - 为子类提供可扩展的抽象框架
 * 技术选型:
 *   - 抽象类设计提供基础实现和扩展点
 *   - ConcurrentHashMap保证线程安全
 *   - AtomicInteger提供无锁计数
 *   - ReadWriteLock实现读写分离
 *   - Actor模型集成支持消息处理
 * 依赖关系:
 *   - 被SceneManager管理和调度
 *   - 与SceneActor协作进行消息处理
 *   - 使用SceneType进行类型标识
 *   - 依赖SceneConfig进行配置管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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
 * 提供场景管理的基础功能，包括场景状态管理、实体管理、
 * 生命周期控制、事件处理等核心功能。通过抽象方法为
 * 子类提供可扩展的实现框架。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Data
public abstract class Scene {

    /**
     * 场景状态枚举
     */
    public enum SceneState {
        /** 创建中 */
        CREATING("creating", "创建中"),
        /** 运行中 */
        RUNNING("running", "运行中"),
        /** 暂停中 */
        PAUSED("paused", "暂停中"),
        /** 维护中 */
        MAINTENANCE("maintenance", "维护中"),
        /** 关闭中 */
        CLOSING("closing", "关闭中"),
        /** 已关闭 */
        CLOSED("closed", "已关闭");

        private final String code;
        private final String name;

        SceneState(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 位置信息
     */
    @Data
    public static class Position {
        /** X坐标 */
        private float x;
        /** Y坐标 */
        private float y;
        /** Z坐标 */
        private float z;
        /** 朝向角度 */
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
         *
         * @param other 另一个位置
         * @return 距离值
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
         *
         * @param other 目标位置
         * @param range 范围
         * @return 是否在范围内
         */
        public boolean isWithinRange(Position other, double range) {
            return distanceTo(other) <= range;
        }

        /**
         * 创建位置副本
         *
         * @return 位置副本
         */
        public Position copy() {
            return new Position(x, y, z, rotation);
        }

        @Override
        public String toString() {
            return String.format("Position{x=%.2f, y=%.2f, z=%.2f, rotation=%.2f}", x, y, z, rotation);
        }
    }

    // ========== 基础属性 ==========

    /** 场景ID */
    private final Long sceneId;

    /** 场景名称 */
    private String sceneName;

    /** 场景类型 */
    private SceneType sceneType;

    /** 场景状态 */
    private volatile SceneState state = SceneState.CREATING;

    /** 场景配置 */
    private SceneConfig config;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 最后更新时间 */
    private volatile LocalDateTime lastUpdateTime;

    /** Actor引用路径 */
    private String actorPath;

    // ========== 实体管理 ==========

    /** 场景中的实体集合 */
    private final Set<Long> entities = ConcurrentHashMap.newKeySet();

    /** 实体位置信息 */
    private final Map<Long, Position> entityPositions = new ConcurrentHashMap<>();

    /** 实体数量计数器 */
    private final AtomicInteger entityCount = new AtomicInteger(0);

    // ========== 扩展属性 ==========

    /** 场景数据存储 */
    private final Map<String, Object> sceneData = new ConcurrentHashMap<>();

    /** 场景属性 */
    private final Map<String, Object> properties = new ConcurrentHashMap<>();

    // ========== 性能统计 ==========

    /** 消息处理计数器 */
    private final AtomicLong messageCount = new AtomicLong(0);

    /** Tick计数器 */
    private final AtomicLong tickCount = new AtomicLong(0);

    /** 最后一次Tick时间 */
    private volatile LocalDateTime lastTickTime;

    // ========== 并发控制 ==========

    /** 读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造函数
     *
     * @param sceneId 场景ID
     * @param sceneName 场景名称
     * @param sceneType 场景类型
     * @param config 场景配置
     */
    public Scene(Long sceneId, String sceneName, SceneType sceneType, SceneConfig config) {
        this.sceneId = sceneId;
        this.sceneName = sceneName;
        this.sceneType = sceneType;
        this.config = config != null ? config : new SceneConfig();
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = createTime;
        this.lastTickTime = createTime;
    }

    // ========== 生命周期方法 ==========

    /**
     * 初始化场景
     *
     * @throws Exception 初始化异常
     */
    public final void initialize() throws Exception {
        log.info("初始化场景: {} (ID: {}, Type: {})", sceneName, sceneId, sceneType);
        
        try {
            // 调用子类初始化方法
            onCreate();
            
            state = SceneState.RUNNING;
            lastUpdateTime = LocalDateTime.now();
            
            log.info("场景 {} 初始化完成", sceneName);
            
        } catch (Exception e) {
            log.error("场景 {} 初始化失败", sceneName, e);
            state = SceneState.CLOSED;
            throw e;
        }
    }

    /**
     * 销毁场景
     */
    public final void destroy() {
        log.info("销毁场景: {} (ID: {})", sceneName, sceneId);
        
        state = SceneState.CLOSING;
        
        try {
            // 移除所有实体
            removeAllEntities();
            
            // 调用子类销毁方法
            onDestroy();
            
            // 清理数据
            sceneData.clear();
            properties.clear();
            
            state = SceneState.CLOSED;
            lastUpdateTime = LocalDateTime.now();
            
            log.info("场景 {} 销毁完成", sceneName);
            
        } catch (Exception e) {
            log.error("场景 {} 销毁失败", sceneName, e);
        }
    }

    /**
     * 场景更新（Tick）
     *
     * @param deltaTime 距离上次更新的时间间隔（毫秒）
     */
    public final void tick(long deltaTime) {
        if (state != SceneState.RUNNING) {
            return;
        }

        try {
            // 调用子类tick方法
            onTick(deltaTime);
            
            tickCount.incrementAndGet();
            lastTickTime = LocalDateTime.now();
            
        } catch (Exception e) {
            log.error("场景 {} tick执行失败", sceneName, e);
        }
    }

    // ========== 实体管理 ==========

    /**
     * 添加实体到场景
     *
     * @param entityId 实体ID
     * @param position 初始位置
     * @return 是否添加成功
     */
    public boolean addEntity(Long entityId, Position position) {
        if (entityId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (state != SceneState.RUNNING) {
                log.warn("场景 {} 不在运行状态，无法添加实体 {}", sceneId, entityId);
                return false;
            }

            if (entities.add(entityId)) {
                if (position != null) {
                    entityPositions.put(entityId, position.copy());
                }
                
                entityCount.incrementAndGet();
                lastUpdateTime = LocalDateTime.now();

                // 调用子类方法
                onEntityEnter(entityId, position);

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
     * 从场景移除实体
     *
     * @param entityId 实体ID
     * @return 是否移除成功
     */
    public boolean removeEntity(Long entityId) {
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
     * 更新实体位置
     *
     * @param entityId 实体ID
     * @param position 新位置
     */
    public void updateEntityPosition(Long entityId, Position position) {
        if (entityId == null || position == null) {
            return;
        }

        lock.readLock().lock();
        try {
            if (entities.contains(entityId)) {
                Position oldPosition = entityPositions.put(entityId, position.copy());
                
                // 调用子类方法
                onEntityMove(entityId, oldPosition, position);
                
                lastUpdateTime = LocalDateTime.now();
            }
        } finally {
            lock.readLock().unlock();
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
                removeEntity(entityId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 查询方法 ==========

    /**
     * 检查实体是否在场景中
     *
     * @param entityId 实体ID
     * @return 是否在场景中
     */
    public boolean containsEntity(Long entityId) {
        lock.readLock().lock();
        try {
            return entities.contains(entityId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取实体位置
     *
     * @param entityId 实体ID
     * @return 实体位置，如果不存在返回null
     */
    public Position getEntityPosition(Long entityId) {
        lock.readLock().lock();
        try {
            Position pos = entityPositions.get(entityId);
            return pos != null ? pos.copy() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有实体ID
     *
     * @return 实体ID集合
     */
    public Set<Long> getAllEntities() {
        lock.readLock().lock();
        try {
            return new HashSet<>(entities);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取范围内的实体
     *
     * @param center 中心位置
     * @param range 范围
     * @return 范围内的实体ID列表
     */
    public List<Long> getEntitiesInRange(Position center, double range) {
        lock.readLock().lock();
        try {
            List<Long> result = new ArrayList<>();
            for (Map.Entry<Long, Position> entry : entityPositions.entrySet()) {
                Position entityPos = entry.getValue();
                if (entityPos != null && center.isWithinRange(entityPos, range)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========== 状态和属性管理 ==========

    /**
     * 设置场景数据
     *
     * @param key 键
     * @param value 值
     */
    public void setSceneData(String key, Object value) {
        sceneData.put(key, value);
    }

    /**
     * 获取场景数据
     *
     * @param key 键
     * @return 值
     */
    public Object getSceneData(String key) {
        return sceneData.get(key);
    }

    /**
     * 获取场景数据
     *
     * @param key 键
     * @param defaultValue 默认值
     * @param <T> 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getSceneData(String key, T defaultValue) {
        Object value = sceneData.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 设置场景属性
     *
     * @param key 键
     * @param value 值
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * 获取场景属性
     *
     * @param key 键
     * @return 值
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * 获取场景属性
     *
     * @param key 键
     * @param defaultValue 默认值
     * @param <T> 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        Object value = properties.get(key);
        return value != null ? (T) value : defaultValue;
    }

    // ========== 状态检查 ==========

    /**
     * 检查场景是否运行中
     *
     * @return 是否运行中
     */
    public boolean isRunning() {
        return state == SceneState.RUNNING;
    }

    /**
     * 检查场景是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return entityCount.get() == 0;
    }

    /**
     * 检查场景是否已满
     *
     * @return 是否已满
     */
    public boolean isFull() {
        return entityCount.get() >= config.getMaxEntities();
    }

    // ========== 性能监控 ==========

    /**
     * 增加消息计数
     */
    public void incrementMessageCount() {
        messageCount.incrementAndGet();
    }

    /**
     * 获取场景统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sceneId", sceneId);
        stats.put("sceneName", sceneName);
        stats.put("sceneType", sceneType.getCode());
        stats.put("state", state.getCode());
        stats.put("entityCount", entityCount.get());
        stats.put("maxEntities", config.getMaxEntities());
        stats.put("messageCount", messageCount.get());
        stats.put("tickCount", tickCount.get());
        stats.put("createTime", createTime);
        stats.put("lastUpdateTime", lastUpdateTime);
        stats.put("lastTickTime", lastTickTime);
        return stats;
    }

    // ========== 抽象方法 ==========

    /**
     * 场景创建回调
     * 子类实现具体的初始化逻辑
     *
     * @throws Exception 初始化异常
     */
    protected abstract void onCreate() throws Exception;

    /**
     * 场景销毁回调
     * 子类实现具体的清理逻辑
     *
     * @throws Exception 销毁异常
     */
    protected abstract void onDestroy() throws Exception;

    /**
     * 场景更新回调
     * 子类实现具体的更新逻辑
     *
     * @param deltaTime 距离上次更新的时间间隔（毫秒）
     */
    protected abstract void onTick(long deltaTime);

    /**
     * 实体进入场景回调
     * 子类实现具体的处理逻辑
     *
     * @param entityId 实体ID
     * @param position 初始位置
     */
    protected abstract void onEntityEnter(Long entityId, Position position);

    /**
     * 实体离开场景回调
     * 子类实现具体的处理逻辑
     *
     * @param entityId 实体ID
     */
    protected abstract void onEntityLeave(Long entityId);

    /**
     * 实体移动回调
     * 子类实现具体的处理逻辑
     *
     * @param entityId 实体ID
     * @param oldPosition 旧位置
     * @param newPosition 新位置
     */
    protected abstract void onEntityMove(Long entityId, Position oldPosition, Position newPosition);

    @Override
    public String toString() {
        return String.format("Scene{id=%d, name='%s', type=%s, state=%s, entities=%d/%d}",
                sceneId, sceneName, sceneType.getCode(), state.getCode(), 
                entityCount.get(), config.getMaxEntities());
    }
}