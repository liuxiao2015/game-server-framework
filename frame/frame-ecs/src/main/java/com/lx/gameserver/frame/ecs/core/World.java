/*
 * 文件名: World.java
 * 用途: ECS世界管理器核心实现
 * 实现内容:
 *   - ECS世界管理器
 *   - 实体创建/销毁
 *   - 组件添加/移除/查询
 *   - 系统注册/排序/执行
 *   - 世界快照功能
 *   - 事件分发机制
 * 技术选型:
 *   - 集中式管理架构
 *   - 高性能数据结构
 *   - 线程安全设计
 * 依赖关系:
 *   - ECS框架的核心协调器
 *   - 管理所有Entity、Component、System
 *   - 提供统一的访问接口
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.core;

import com.lx.gameserver.frame.ecs.component.ComponentManager;
import com.lx.gameserver.frame.ecs.config.ECSConfig;
import com.lx.gameserver.frame.ecs.event.EventBus;
import com.lx.gameserver.frame.ecs.query.EntityQuery;
import com.lx.gameserver.frame.ecs.query.QueryCache;
import com.lx.gameserver.frame.ecs.system.SystemManager;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ECS世界管理器
 * <p>
 * 世界是ECS框架的核心管理器，负责协调实体、组件和系统之间的交互。
 * 提供了实体生命周期管理、组件操作、系统调度和事件分发等核心功能。
 * 支持多线程安全访问和高性能批量操作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class World {
    
    private static final Logger logger = LoggerFactory.getLogger(World.class);
    
    /**
     * 世界配置
     */
    @Getter
    private final ECSConfig.WorldConfig config;
    
    /**
     * 实体存储
     */
    private final Map<Long, Entity> entities;
    
    /**
     * 待删除实体队列
     */
    private final Set<Long> pendingRemovalEntities;
    
    /**
     * 组件管理器
     */
    @Getter
    private final ComponentManager componentManager;
    
    /**
     * 系统管理器
     */
    @Getter
    private final SystemManager systemManager;
    
    /**
     * 查询缓存
     */
    @Getter
    private final QueryCache queryCache;
    
    /**
     * 事件总线
     */
    @Getter
    private final EventBus eventBus;
    
    /**
     * 世界状态
     */
    private volatile WorldState state = WorldState.CREATED;
    
    /**
     * 读写锁（保护实体操作）
     */
    private final ReadWriteLock entityLock = new ReentrantReadWriteLock();
    
    /**
     * 世界统计信息
     */
    @Getter
    private final WorldStatistics statistics;
    
    /**
     * 世界状态枚举
     */
    public enum WorldState {
        CREATED,
        INITIALIZED,
        RUNNING,
        PAUSED,
        DESTROYED
    }
    
    /**
     * 构造函数
     *
     * @param config 世界配置
     */
    public World(ECSConfig.WorldConfig config) {
        this.config = Objects.requireNonNull(config, "世界配置不能为null");
        this.entities = new ConcurrentHashMap<>(config.getInitialEntityCapacity());
        this.pendingRemovalEntities = ConcurrentHashMap.newKeySet();
        this.componentManager = new ComponentManager(config.getComponentPoolSize());
        this.systemManager = new SystemManager();
        this.queryCache = new QueryCache();
        this.eventBus = new EventBus();
        this.statistics = new WorldStatistics();
        
        logger.info("ECS世界创建完成，初始实体容量: {}", config.getInitialEntityCapacity());
    }
    
    /**
     * 使用默认配置构造
     */
    public World() {
        this(new ECSConfig.WorldConfig());
    }
    
    /**
     * 初始化世界
     */
    public void initialize() {
        if (state != WorldState.CREATED) {
            throw new IllegalStateException("世界只能在CREATED状态下初始化");
        }
        
        try {
            // 初始化各个管理器
            componentManager.initialize();
            systemManager.initialize(this);
            queryCache.initialize();
            eventBus.initialize();
            
            state = WorldState.INITIALIZED;
            state = WorldState.RUNNING;
            
            logger.info("ECS世界初始化完成");
        } catch (Exception e) {
            logger.error("ECS世界初始化失败", e);
            throw new RuntimeException("世界初始化失败", e);
        }
    }
    
    /**
     * 更新世界
     *
     * @param deltaTime 时间增量（秒）
     */
    public void update(float deltaTime) {
        if (state != WorldState.RUNNING) {
            return;
        }
        
        long startTime = java.lang.System.nanoTime();
        
        try {
            // 处理待删除实体
            processPendingRemovals();
            
            // 更新系统
            systemManager.update(deltaTime);
            
            // 处理事件
            eventBus.processEvents();
            
            // 更新统计信息
            statistics.incrementUpdateCount();
            
        } catch (Exception e) {
            statistics.incrementErrorCount();
            logger.error("世界更新时发生错误", e);
            throw e;
        } finally {
            long endTime = java.lang.System.nanoTime();
            statistics.addUpdateTime(endTime - startTime);
        }
    }
    
    /**
     * 销毁世界
     */
    public void destroy() {
        if (state == WorldState.DESTROYED) {
            return;
        }
        
        try {
            // 销毁所有系统
            systemManager.destroy();
            
            // 清理所有实体
            clearAllEntities();
            
            // 销毁各个管理器
            componentManager.destroy();
            queryCache.destroy();
            eventBus.destroy();
            
            state = WorldState.DESTROYED;
            
            logger.info("ECS世界销毁完成");
        } catch (Exception e) {
            logger.error("ECS世界销毁时发生错误", e);
        }
    }
    
    /**
     * 暂停世界
     */
    public void pause() {
        if (state == WorldState.RUNNING) {
            state = WorldState.PAUSED;
            systemManager.pause();
            logger.info("ECS世界已暂停");
        }
    }
    
    /**
     * 恢复世界
     */
    public void resume() {
        if (state == WorldState.PAUSED) {
            state = WorldState.RUNNING;
            systemManager.resume();
            logger.info("ECS世界已恢复");
        }
    }
    
    // ================ 实体管理 ================
    
    /**
     * 创建实体
     *
     * @return 新创建的实体
     */
    public Entity createEntity() {
        Entity entity = Entity.create();
        
        entityLock.writeLock().lock();
        try {
            entities.put(entity.getId(), entity);
            statistics.incrementEntityCreatedCount();
            
            // 发布实体创建事件
            // eventBus.publish(new EntityCreatedEvent(entity));
            
            logger.debug("创建实体: {}", entity.getId());
            return entity;
        } finally {
            entityLock.writeLock().unlock();
        }
    }
    
    /**
     * 创建带原型的实体
     *
     * @param archetypeId 原型ID
     * @return 新创建的实体
     */
    public Entity createEntity(long archetypeId) {
        Entity entity = createEntity();
        entity.setArchetypeId(archetypeId);
        return entity;
    }
    
    /**
     * 获取实体
     *
     * @param entityId 实体ID
     * @return 实体实例，如果不存在返回null
     */
    public Entity getEntity(long entityId) {
        entityLock.readLock().lock();
        try {
            return entities.get(entityId);
        } finally {
            entityLock.readLock().unlock();
        }
    }
    
    /**
     * 检查实体是否存在
     *
     * @param entityId 实体ID
     * @return 如果存在返回true
     */
    public boolean hasEntity(long entityId) {
        entityLock.readLock().lock();
        try {
            return entities.containsKey(entityId);
        } finally {
            entityLock.readLock().unlock();
        }
    }
    
    /**
     * 销毁实体
     *
     * @param entityId 实体ID
     */
    public void destroyEntity(long entityId) {
        Entity entity = getEntity(entityId);
        if (entity == null) {
            return;
        }
        
        // 标记实体为待删除
        entity.markForRemoval();
        pendingRemovalEntities.add(entityId);
        
        logger.debug("标记实体待删除: {}", entityId);
    }
    
    /**
     * 立即销毁实体
     *
     * @param entityId 实体ID
     */
    public void destroyEntityImmediate(long entityId) {
        Entity entity = getEntity(entityId);
        if (entity == null) {
            return;
        }
        
        entityLock.writeLock().lock();
        try {
            // 移除所有组件
            componentManager.removeAllComponents(entityId);
            
            // 移除实体
            entities.remove(entityId);
            pendingRemovalEntities.remove(entityId);
            
            // 标记实体为已销毁
            entity.destroy();
            
            // 清理查询缓存
            queryCache.invalidate();
            
            statistics.incrementEntityDestroyedCount();
            
            // 发布实体销毁事件
            // eventBus.publish(new EntityDestroyedEvent(entity));
            
            logger.debug("立即销毁实体: {}", entityId);
        } finally {
            entityLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取所有实体
     *
     * @return 实体集合
     */
    public Collection<Entity> getAllEntities() {
        entityLock.readLock().lock();
        try {
            return new ArrayList<>(entities.values());
        } finally {
            entityLock.readLock().unlock();
        }
    }
    
    /**
     * 获取实体数量
     *
     * @return 实体数量
     */
    public int getEntityCount() {
        entityLock.readLock().lock();
        try {
            return entities.size();
        } finally {
            entityLock.readLock().unlock();
        }
    }
    
    /**
     * 清理所有实体
     */
    public void clearAllEntities() {
        entityLock.writeLock().lock();
        try {
            // 移除所有组件
            for (long entityId : entities.keySet()) {
                componentManager.removeAllComponents(entityId);
            }
            
            // 清理实体
            entities.clear();
            pendingRemovalEntities.clear();
            
            // 清理查询缓存
            queryCache.clear();
            
            logger.info("清理所有实体完成");
        } finally {
            entityLock.writeLock().unlock();
        }
    }
    
    // ================ 组件管理 ================
    
    /**
     * 为实体添加组件
     *
     * @param entityId 实体ID
     * @param component 组件实例
     * @param <T> 组件类型
     * @return 添加的组件
     */
    public <T extends Component> T addComponent(long entityId, T component) {
        Entity entity = getEntity(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }
        
        T addedComponent = componentManager.addComponent(entityId, component);
        entity.incrementVersion();
        queryCache.invalidate();
        
        // 发布组件添加事件
        // eventBus.publish(new ComponentAddedEvent(entity, addedComponent));
        
        return addedComponent;
    }
    
    /**
     * 获取实体的组件
     *
     * @param entityId 实体ID
     * @param componentClass 组件类型
     * @param <T> 组件类型
     * @return 组件实例，如果不存在返回null
     */
    public <T extends Component> T getComponent(long entityId, Class<T> componentClass) {
        return componentManager.getComponent(entityId, componentClass);
    }
    
    /**
     * 检查实体是否有指定组件
     *
     * @param entityId 实体ID
     * @param componentClass 组件类型
     * @return 如果有返回true
     */
    public boolean hasComponent(long entityId, Class<? extends Component> componentClass) {
        return componentManager.hasComponent(entityId, componentClass);
    }
    
    /**
     * 移除实体的组件
     *
     * @param entityId 实体ID
     * @param componentClass 组件类型
     * @param <T> 组件类型
     * @return 移除的组件，如果不存在返回null
     */
    public <T extends Component> T removeComponent(long entityId, Class<T> componentClass) {
        Entity entity = getEntity(entityId);
        if (entity == null) {
            return null;
        }
        
        T removedComponent = componentManager.removeComponent(entityId, componentClass);
        if (removedComponent != null) {
            entity.incrementVersion();
            queryCache.invalidate();
            
            // 发布组件移除事件
            // eventBus.publish(new ComponentRemovedEvent(entity, removedComponent));
        }
        
        return removedComponent;
    }
    
    /**
     * 获取实体的所有组件
     *
     * @param entityId 实体ID
     * @return 组件集合
     */
    public Collection<Component> getAllComponents(long entityId) {
        return componentManager.getAllComponents(entityId);
    }
    
    // ================ 系统管理 ================
    
    /**
     * 注册系统
     *
     * @param system 系统实例
     * @param <T> 系统类型
     * @return 注册的系统
     */
    public <T extends System> T registerSystem(T system) {
        return systemManager.registerSystem(system);
    }
    
    /**
     * 获取系统
     *
     * @param systemClass 系统类型
     * @param <T> 系统类型
     * @return 系统实例，如果不存在返回null
     */
    public <T extends System> T getSystem(Class<T> systemClass) {
        return systemManager.getSystem(systemClass);
    }
    
    /**
     * 移除系统
     *
     * @param systemClass 系统类型
     * @param <T> 系统类型
     * @return 移除的系统，如果不存在返回null
     */
    public <T extends System> T removeSystem(Class<T> systemClass) {
        return systemManager.removeSystem(systemClass);
    }
    
    // ================ 查询功能 ================
    
    /**
     * 创建实体查询
     *
     * @return 查询构建器
     */
    public EntityQuery.Builder createQuery() {
        return new EntityQuery.Builder(this);
    }
    
    /**
     * 查询实体
     *
     * @param query 查询条件
     * @return 查询结果
     */
    public Collection<Entity> queryEntities(EntityQuery query) {
        return query.execute();
    }
    
    // ================ 私有方法 ================
    
    /**
     * 处理待删除实体
     */
    private void processPendingRemovals() {
        if (pendingRemovalEntities.isEmpty()) {
            return;
        }
        
        List<Long> toRemove = new ArrayList<>(pendingRemovalEntities);
        pendingRemovalEntities.clear();
        
        for (Long entityId : toRemove) {
            destroyEntityImmediate(entityId);
        }
    }
    
    /**
     * 获取世界状态
     *
     * @return 世界状态
     */
    public WorldState getState() {
        return state;
    }
    
    /**
     * 检查世界是否正在运行
     *
     * @return 如果正在运行返回true
     */
    public boolean isRunning() {
        return state == WorldState.RUNNING;
    }
    
    /**
     * 获取世界配置
     *
     * @return 世界配置
     */
    public ECSConfig.WorldConfig getConfig() {
        return config;
    }
}

/**
 * 世界统计信息
 */
@Getter
class WorldStatistics {
    
    private volatile long updateCount = 0;
    private volatile long totalUpdateTime = 0;
    private volatile long minUpdateTime = Long.MAX_VALUE;
    private volatile long maxUpdateTime = 0;
    private volatile long errorCount = 0;
    private volatile long entityCreatedCount = 0;
    private volatile long entityDestroyedCount = 0;
    private volatile long lastUpdateTime = 0;
    
    public void incrementUpdateCount() {
        updateCount++;
        lastUpdateTime = java.lang.System.currentTimeMillis();
    }
    
    public void addUpdateTime(long updateTime) {
        totalUpdateTime += updateTime;
        minUpdateTime = Math.min(minUpdateTime, updateTime);
        maxUpdateTime = Math.max(maxUpdateTime, updateTime);
    }
    
    public void incrementErrorCount() {
        errorCount++;
    }
    
    public void incrementEntityCreatedCount() {
        entityCreatedCount++;
    }
    
    public void incrementEntityDestroyedCount() {
        entityDestroyedCount++;
    }
    
    public double getAverageUpdateTimeMs() {
        return updateCount > 0 ? (totalUpdateTime / 1_000_000.0) / updateCount : 0.0;
    }
    
    public void reset() {
        updateCount = 0;
        totalUpdateTime = 0;
        minUpdateTime = Long.MAX_VALUE;
        maxUpdateTime = 0;
        errorCount = 0;
        entityCreatedCount = 0;
        entityDestroyedCount = 0;
        lastUpdateTime = 0;
    }
}