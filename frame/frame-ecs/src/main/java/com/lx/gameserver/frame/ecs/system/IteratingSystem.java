/*
 * 文件名: IteratingSystem.java
 * 用途: 迭代实体系统基类
 * 实现内容:
 *   - 遍历实体系统基类
 *   - 自动过滤符合条件的实体
 *   - 提供单实体处理接口
 *   - 批量处理优化
 * 技术选型:
 *   - 模板方法模式
 *   - 实体查询优化
 *   - 批处理性能优化
 * 依赖关系:
 *   - 继承AbstractSystem
 *   - 使用EntityQuery进行实体查询
 *   - 为具体业务系统提供基础
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.core.AbstractSystem;
import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.ecs.query.EntityQuery;

import java.util.Collection;
import java.util.Set;

/**
 * 迭代实体系统
 * <p>
 * 自动查询符合条件的实体，并对每个实体执行处理逻辑。
 * 提供组件过滤、批量处理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class IteratingSystem extends AbstractSystem {
    
    /**
     * 必须包含的组件类型
     */
    private final Set<Class<? extends Component>> requiredComponents;
    
    /**
     * 不能包含的组件类型
     */
    private final Set<Class<? extends Component>> excludedComponents;
    
    /**
     * 实体查询
     */
    private EntityQuery entityQuery;
    
    /**
     * 批量处理大小
     */
    private int batchSize = 100;
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param requiredComponents 必须包含的组件
     */
    @SafeVarargs
    protected IteratingSystem(String name, int priority, Class<? extends Component>... requiredComponents) {
        this(name, priority, Set.of(requiredComponents), Set.of());
    }
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param requiredComponents 必须包含的组件
     * @param excludedComponents 不能包含的组件
     */
    protected IteratingSystem(String name, int priority, 
                            Set<Class<? extends Component>> requiredComponents,
                            Set<Class<? extends Component>> excludedComponents) {
        super(name, priority);
        this.requiredComponents = Set.copyOf(requiredComponents);
        this.excludedComponents = Set.copyOf(excludedComponents);
    }
    
    @Override
    protected void onInitialize() {
        // 构建实体查询
        EntityQuery.Builder queryBuilder = createQuery();
        
        // 添加必须包含的组件
        if (!requiredComponents.isEmpty()) {
            queryBuilder.all(requiredComponents.toArray(new Class[0]));
        }
        
        // 添加不能包含的组件
        if (!excludedComponents.isEmpty()) {
            queryBuilder.none(excludedComponents.toArray(new Class[0]));
        }
        
        this.entityQuery = queryBuilder.build();
        
        // 调用子类初始化
        onSystemInitialize();
    }
    
    @Override
    protected void onUpdate(float deltaTime) {
        // 获取符合条件的实体
        Collection<Entity> entities = entityQuery.execute();
        
        if (entities.isEmpty()) {
            return;
        }
        
        // 批量处理
        if (batchSize > 1 && entities.size() > batchSize) {
            processBatch(entities, deltaTime);
        } else {
            // 逐个处理
            for (Entity entity : entities) {
                if (shouldProcessEntity(entity)) {
                    processEntity(entity, deltaTime);
                }
            }
        }
    }
    
    /**
     * 批量处理实体
     *
     * @param entities 实体集合
     * @param deltaTime 时间增量
     */
    protected void processBatch(Collection<Entity> entities, float deltaTime) {
        Entity[] entityArray = entities.toArray(new Entity[0]);
        int totalEntities = entityArray.length;
        
        for (int i = 0; i < totalEntities; i += batchSize) {
            int endIndex = Math.min(i + batchSize, totalEntities);
            
            // 处理批次前的准备工作
            onBatchStart(i, endIndex - i, deltaTime);
            
            // 处理批次中的每个实体
            for (int j = i; j < endIndex; j++) {
                Entity entity = entityArray[j];
                if (shouldProcessEntity(entity)) {
                    processEntity(entity, deltaTime);
                }
            }
            
            // 处理批次后的清理工作
            onBatchEnd(i, endIndex - i, deltaTime);
        }
    }
    
    /**
     * 检查是否应该处理该实体
     *
     * @param entity 实体
     * @return 如果应该处理返回true
     */
    protected boolean shouldProcessEntity(Entity entity) {
        return entity.isActive();
    }
    
    /**
     * 获取符合条件的实体集合
     *
     * @return 实体集合
     */
    protected Collection<Entity> getEntities() {
        return entityQuery.execute();
    }
    
    /**
     * 处理单个实体
     *
     * @param entity 实体
     * @param deltaTime 时间增量
     */
    protected abstract void processEntity(Entity entity, float deltaTime);
    
    /**
     * 系统初始化回调（子类实现）
     */
    protected void onSystemInitialize() {
        // 默认空实现
    }
    
    /**
     * 批处理开始回调
     *
     * @param startIndex 开始索引
     * @param batchSize 批次大小
     * @param deltaTime 时间增量
     */
    protected void onBatchStart(int startIndex, int batchSize, float deltaTime) {
        // 默认空实现
    }
    
    /**
     * 批处理结束回调
     *
     * @param startIndex 开始索引
     * @param batchSize 批次大小
     * @param deltaTime 时间增量
     */
    protected void onBatchEnd(int startIndex, int batchSize, float deltaTime) {
        // 默认空实现
    }
    
    /**
     * 设置批处理大小
     *
     * @param batchSize 批处理大小
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }
    
    /**
     * 获取批处理大小
     *
     * @return 批处理大小
     */
    public int getBatchSize() {
        return batchSize;
    }
    
    /**
     * 获取必须包含的组件类型
     *
     * @return 组件类型集合
     */
    public Set<Class<? extends Component>> getRequiredComponents() {
        return requiredComponents;
    }
    
    /**
     * 获取不能包含的组件类型
     *
     * @return 组件类型集合
     */
    public Set<Class<? extends Component>> getExcludedComponents() {
        return excludedComponents;
    }
    
    /**
     * 获取当前符合条件的实体数量
     *
     * @return 实体数量
     */
    public int getEntityCount() {
        return entityQuery != null ? entityQuery.execute().size() : 0;
    }
}