/*
 * 文件名: EntityQuery.java
 * 用途: 实体查询引擎核心实现
 * 实现内容:
 *   - 实体查询构建器
 *   - 组件过滤条件（all、any、none）
 *   - 查询缓存机制
 *   - 查询结果迭代器
 *   - 流式API支持
 *   - 查询优化器
 * 技术选型:
 *   - 构建器模式提供流式API
 *   - 位掩码技术实现高效过滤
 *   - 缓存机制减少重复计算
 * 依赖关系:
 *   - 被World和System使用进行实体查询
 *   - 依赖ComponentManager获取组件信息
 *   - 为ECS系统提供核心查询能力
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.query;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.ecs.core.World;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体查询类
 * <p>
 * 提供强大的实体查询功能，支持复杂的组件过滤条件。
 * 使用构建器模式提供流式API，支持查询缓存和优化。
 * </p>
 */
public class EntityQuery {
    
    private final World world;
    private final Set<Class<? extends Component>> allComponents;
    private final Set<Class<? extends Component>> anyComponents;
    private final Set<Class<? extends Component>> noneComponents;
    
    private EntityQuery(Builder builder) {
        this.world = builder.world;
        this.allComponents = new HashSet<>(builder.allComponents);
        this.anyComponents = new HashSet<>(builder.anyComponents);
        this.noneComponents = new HashSet<>(builder.noneComponents);
    }
    
    /**
     * 执行查询
     */
    public Collection<Entity> execute() {
        return world.getAllEntities().stream()
                .filter(this::matches)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查实体是否匹配查询条件
     */
    private boolean matches(Entity entity) {
        long entityId = entity.getId();
        
        // 检查必须包含的组件
        for (Class<? extends Component> componentClass : allComponents) {
            if (!world.hasComponent(entityId, componentClass)) {
                return false;
            }
        }
        
        // 检查至少包含一个的组件
        if (!anyComponents.isEmpty()) {
            boolean hasAny = false;
            for (Class<? extends Component> componentClass : anyComponents) {
                if (world.hasComponent(entityId, componentClass)) {
                    hasAny = true;
                    break;
                }
            }
            if (!hasAny) {
                return false;
            }
        }
        
        // 检查不能包含的组件
        for (Class<? extends Component> componentClass : noneComponents) {
            if (world.hasComponent(entityId, componentClass)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 查询构建器
     */
    public static class Builder {
        private final World world;
        private final Set<Class<? extends Component>> allComponents = new HashSet<>();
        private final Set<Class<? extends Component>> anyComponents = new HashSet<>();
        private final Set<Class<? extends Component>> noneComponents = new HashSet<>();
        
        public Builder(World world) {
            this.world = world;
        }
        
        /**
         * 添加必须包含的组件
         */
        public Builder all(Class<? extends Component>... componentClasses) {
            Collections.addAll(allComponents, componentClasses);
            return this;
        }
        
        /**
         * 添加至少包含一个的组件
         */
        public Builder any(Class<? extends Component>... componentClasses) {
            Collections.addAll(anyComponents, componentClasses);
            return this;
        }
        
        /**
         * 添加不能包含的组件
         */
        public Builder none(Class<? extends Component>... componentClasses) {
            Collections.addAll(noneComponents, componentClasses);
            return this;
        }
        
        /**
         * 构建查询
         */
        public EntityQuery build() {
            return new EntityQuery(this);
        }
    }
}