/*
 * 文件名: EntityFactory.java
 * 用途: ECS实体工厂实现
 * 实现内容:
 *   - 基于原型的实体创建
 *   - 实体池化支持
 *   - 创建后处理器
 *   - 工厂方法模式
 *   - 批量创建优化
 * 技术选型:
 *   - 工厂模式提供统一的创建接口
 *   - 对象池技术提高性能
 *   - 后处理器模式支持扩展
 * 依赖关系:
 *   - 使用Archetype进行实体模板创建
 *   - 依赖World进行实体注册
 *   - 被游戏系统用于实体创建
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.archetype;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.ecs.core.World;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * ECS实体工厂
 * <p>
 * 提供基于原型的实体创建功能，支持实体池化、批量创建和创建后处理。
 * 通过工厂模式统一实体创建流程，提高创建效率和代码复用性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class EntityFactory {
    
    /**
     * 默认实体池大小
     */
    private static final int DEFAULT_POOL_SIZE = 1000;
    
    /**
     * 实体池
     */
    private final Map<String, Queue<Entity>> entityPools = new ConcurrentHashMap<>();
    
    /**
     * 池配置
     */
    private final Map<String, PoolConfig> poolConfigs = new ConcurrentHashMap<>();
    
    /**
     * 创建后处理器
     */
    private final Map<String, List<Consumer<Entity>>> postProcessors = new ConcurrentHashMap<>();
    
    /**
     * 全局创建后处理器
     */
    private final List<Consumer<Entity>> globalPostProcessors = new ArrayList<>();
    
    /**
     * 关联的世界
     */
    private final World world;
    
    /**
     * 是否启用池化
     */
    private boolean poolingEnabled = true;
    
    /**
     * 创建统计
     */
    private final CreationStatistics statistics = new CreationStatistics();
    
    /**
     * 池配置类
     */
    public static class PoolConfig {
        public final int initialSize;
        public final int maxSize;
        public final boolean preWarm;
        
        public PoolConfig(int initialSize, int maxSize, boolean preWarm) {
            this.initialSize = initialSize;
            this.maxSize = maxSize;
            this.preWarm = preWarm;
        }
        
        public static PoolConfig defaultConfig() {
            return new PoolConfig(DEFAULT_POOL_SIZE / 10, DEFAULT_POOL_SIZE, false);
        }
    }
    
    /**
     * 创建统计类
     */
    public static class CreationStatistics {
        private long totalCreated = 0;
        private long poolHits = 0;
        private long poolMisses = 0;
        private final Map<String, Long> archetypeCreationCounts = new ConcurrentHashMap<>();
        
        public void recordCreation(String archetypeName, boolean fromPool) {
            totalCreated++;
            archetypeCreationCounts.merge(archetypeName, 1L, Long::sum);
            if (fromPool) {
                poolHits++;
            } else {
                poolMisses++;
            }
        }
        
        public double getPoolHitRate() {
            long total = poolHits + poolMisses;
            return total > 0 ? (double) poolHits / total : 0.0;
        }
        
        // Getters
        public long getTotalCreated() { return totalCreated; }
        public long getPoolHits() { return poolHits; }
        public long getPoolMisses() { return poolMisses; }
        public Map<String, Long> getArchetypeCreationCounts() { 
            return Collections.unmodifiableMap(archetypeCreationCounts); 
        }
    }
    
    /**
     * 构造函数
     */
    public EntityFactory(World world) {
        this.world = world;
    }
    
    /**
     * 根据原型创建实体
     */
    public Entity create(Archetype archetype) {
        return create(archetype, Collections.emptyMap());
    }
    
    /**
     * 根据原型创建实体（带参数覆盖）
     */
    public Entity create(Archetype archetype, Map<String, Object> parameterOverrides) {
        if (archetype == null) {
            throw new IllegalArgumentException("原型不能为空");
        }
        
        long startTime = System.nanoTime();
        Entity entity = null;
        boolean fromPool = false;
        
        try {
            // 尝试从池中获取实体
            if (poolingEnabled) {
                entity = getFromPool(archetype.getName());
                fromPool = (entity != null);
            }
            
            // 如果池中没有，创建新实体
            if (entity == null) {
                entity = Entity.create();
            }
            
            // 设置原型ID
            entity.setArchetypeId(archetype.getId());
            
            // 实体已创建，无需额外添加到世界
            // 世界会在组件添加时自动管理实体
            
            // 添加组件
            addComponents(entity, archetype, parameterOverrides);
            
            // 执行后处理
            runPostProcessors(entity, archetype);
            
            // 记录统计
            statistics.recordCreation(archetype.getName(), fromPool);
            
            log.debug("实体创建完成: archetype={}, entityId={}, fromPool={}, time={}ns", 
                     archetype.getName(), entity.getId(), fromPool, 
                     System.nanoTime() - startTime);
            
            return entity;
            
        } catch (Exception e) {
            log.error("实体创建失败: archetype={}", archetype.getName(), e);
            
            // 如果创建失败且实体已存在于世界，需要清理
            if (entity != null && world.hasEntity(entity.getId())) {
                world.destroyEntityImmediate(entity.getId());
            }
            
            throw new RuntimeException("实体创建失败", e);
        }
    }
    
    /**
     * 根据原型名称创建实体
     */
    public Entity create(String archetypeName) {
        Archetype archetype = Archetype.get(archetypeName);
        if (archetype == null) {
            throw new IllegalArgumentException("未找到原型: " + archetypeName);
        }
        return create(archetype);
    }
    
    /**
     * 批量创建实体
     */
    public List<Entity> createBatch(Archetype archetype, int count) {
        return createBatch(archetype, count, Collections.emptyMap());
    }
    
    /**
     * 批量创建实体（带参数覆盖）
     */
    public List<Entity> createBatch(Archetype archetype, int count, 
                                   Map<String, Object> parameterOverrides) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        
        List<Entity> entities = new ArrayList<>(count);
        long startTime = System.nanoTime();
        
        try {
            for (int i = 0; i < count; i++) {
                entities.add(create(archetype, parameterOverrides));
            }
            
            log.debug("批量创建实体完成: archetype={}, count={}, time={}ns", 
                     archetype.getName(), count, System.nanoTime() - startTime);
            
            return entities;
            
        } catch (Exception e) {
            log.error("批量创建实体失败: archetype={}, count={}", archetype.getName(), count, e);
            
            // 清理已创建的实体
            for (Entity entity : entities) {
                if (world.hasEntity(entity.getId())) {
                    world.destroyEntityImmediate(entity.getId());
                }
            }
            
            throw new RuntimeException("批量创建实体失败", e);
        }
    }
    
    /**
     * 回收实体到池
     */
    public void recycle(Entity entity) {
        if (!poolingEnabled || entity == null) {
            return;
        }
        
        try {
            // 清理实体状态
            entity.activate();
            entity.setTags(0L);
            
            // 移除所有组件（使用ComponentManager API）
            // 这里简化处理，实际应该获取所有组件类型然后逐个移除
            
            // 获取原型名称
            long archetypeId = entity.getArchetypeId();
            String archetypeName = findArchetypeNameById(archetypeId);
            
            if (archetypeName != null) {
                returnToPool(archetypeName, entity);
                log.debug("实体已回收到池: archetypeName={}, entityId={}", 
                         archetypeName, entity.getId());
            }
            
        } catch (Exception e) {
            log.warn("实体回收失败: entityId={}", entity.getId(), e);
        }
    }
    
    /**
     * 配置实体池
     */
    public void configurePool(String archetypeName, PoolConfig config) {
        poolConfigs.put(archetypeName, config);
        
        // 如果需要预热，创建初始实体
        if (config.preWarm) {
            preWarmPool(archetypeName, config.initialSize);
        }
    }
    
    /**
     * 添加创建后处理器
     */
    public void addPostProcessor(String archetypeName, Consumer<Entity> processor) {
        postProcessors.computeIfAbsent(archetypeName, k -> new ArrayList<>()).add(processor);
    }
    
    /**
     * 添加全局创建后处理器
     */
    public void addGlobalPostProcessor(Consumer<Entity> processor) {
        globalPostProcessors.add(processor);
    }
    
    /**
     * 启用池化
     */
    public void enablePooling() {
        this.poolingEnabled = true;
    }
    
    /**
     * 禁用池化
     */
    public void disablePooling() {
        this.poolingEnabled = false;
    }
    
    /**
     * 获取创建统计
     */
    public CreationStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清空所有池
     */
    public void clearPools() {
        entityPools.clear();
        log.info("所有实体池已清空");
    }
    
    /**
     * 从池中获取实体
     */
    private Entity getFromPool(String archetypeName) {
        Queue<Entity> pool = entityPools.get(archetypeName);
        return pool != null ? pool.poll() : null;
    }
    
    /**
     * 返回实体到池
     */
    private void returnToPool(String archetypeName, Entity entity) {
        PoolConfig config = poolConfigs.getOrDefault(archetypeName, PoolConfig.defaultConfig());
        Queue<Entity> pool = entityPools.computeIfAbsent(archetypeName, 
                                                         k -> new ConcurrentLinkedQueue<>());
        
        if (pool.size() < config.maxSize) {
            pool.offer(entity);
        }
    }
    
    /**
     * 预热池
     */
    private void preWarmPool(String archetypeName, int count) {
        Archetype archetype = Archetype.get(archetypeName);
        if (archetype == null) {
            log.warn("无法预热池，未找到原型: {}", archetypeName);
            return;
        }
        
        Queue<Entity> pool = entityPools.computeIfAbsent(archetypeName, 
                                                         k -> new ConcurrentLinkedQueue<>());
        
        for (int i = 0; i < count; i++) {
            Entity entity = Entity.create();
            entity.setArchetypeId(archetype.getId());
            pool.offer(entity);
        }
        
        log.info("池预热完成: archetypeName={}, count={}", archetypeName, count);
    }
    
    /**
     * 添加组件到实体
     */
    private void addComponents(Entity entity, Archetype archetype, 
                              Map<String, Object> parameterOverrides) {
        Map<Class<? extends Component>, Component> defaultComponents = 
            archetype.getAllDefaultComponents();
        
        for (Map.Entry<Class<? extends Component>, Component> entry : defaultComponents.entrySet()) {
            Component originalComponent = entry.getValue();
            Component component;
            
            try {
                Class<? extends Component> componentType = entry.getKey();
                component = originalComponent.clone();
                
                // 应用参数覆盖
                applyParameterOverrides(component, archetype, parameterOverrides);
                
                world.addComponent(entity.getId(), component);
                
            } catch (Exception e) {
                log.warn("无法克隆组件: {}", entry.getKey().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 应用参数覆盖
     */
    private void applyParameterOverrides(Component component, Archetype archetype, 
                                        Map<String, Object> parameterOverrides) {
        // 这里是简化实现，实际应该根据组件类型进行参数设置
        // 可以通过反射或预定义的参数映射来实现
    }
    
    /**
     * 执行后处理器
     */
    private void runPostProcessors(Entity entity, Archetype archetype) {
        // 执行原型特定的后处理器
        List<Consumer<Entity>> processors = postProcessors.get(archetype.getName());
        if (processors != null) {
            for (Consumer<Entity> processor : processors) {
                try {
                    processor.accept(entity);
                } catch (Exception e) {
                    log.warn("后处理器执行失败: archetypeName={}, entityId={}", 
                            archetype.getName(), entity.getId(), e);
                }
            }
        }
        
        // 执行全局后处理器
        for (Consumer<Entity> processor : globalPostProcessors) {
            try {
                processor.accept(entity);
            } catch (Exception e) {
                log.warn("全局后处理器执行失败: entityId={}", entity.getId(), e);
            }
        }
    }
    
    /**
     * 根据原型ID查找原型名称
     */
    private String findArchetypeNameById(long archetypeId) {
        for (Archetype archetype : Archetype.getAll()) {
            if (archetype.getId() == archetypeId) {
                return archetype.getName();
            }
        }
        return null;
    }
}