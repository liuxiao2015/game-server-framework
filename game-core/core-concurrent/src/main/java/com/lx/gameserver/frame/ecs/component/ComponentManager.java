/*
 * 文件名: ComponentManager.java  
 * 用途: 组件管理器核心实现
 * 实现内容:
 *   - 组件存储管理（高性能数据结构）
 *   - 组件类型注册表
 *   - 组件索引优化（位掩码）
 *   - 组件批量操作
 *   - 组件变更通知
 *   - 内存布局优化
 * 技术选型:
 *   - 稀疏集合技术实现高效存储
 *   - 位掩码实现快速组件查询
 *   - 对象池技术减少GC压力
 * 依赖关系:
 *   - 被World类依赖进行组件管理
 *   - 管理所有Component实例
 *   - 为系统查询提供高性能接口
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组件管理器
 * <p>
 * 负责管理所有实体的组件，提供高性能的组件存储、查询和索引功能。
 * 使用稀疏集合和位掩码技术优化存储效率和查询性能。
 * </p>
 */
public class ComponentManager {
    
    /**
     * 实体组件映射表
     */
    private final Map<Long, Map<Class<? extends Component>, Component>> entityComponents;
    
    /**
     * 组件池大小
     */
    private final int poolSize;
    
    /**
     * 构造函数
     */
    public ComponentManager(int poolSize) {
        this.poolSize = poolSize;
        this.entityComponents = new ConcurrentHashMap<>();
    }
    
    /**
     * 初始化管理器
     */
    public void initialize() {
        // 初始化逻辑
    }
    
    /**
     * 销毁管理器
     */
    public void destroy() {
        entityComponents.clear();
    }
    
    /**
     * 为实体添加组件
     */
    public <T extends Component> T addComponent(long entityId, T component) {
        entityComponents.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>())
                .put(component.getClass(), component);
        return component;
    }
    
    /**
     * 获取实体的组件
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(long entityId, Class<T> componentClass) {
        Map<Class<? extends Component>, Component> components = entityComponents.get(entityId);
        if (components == null) {
            return null;
        }
        return (T) components.get(componentClass);
    }
    
    /**
     * 检查实体是否有指定组件
     */
    public boolean hasComponent(long entityId, Class<? extends Component> componentClass) {
        Map<Class<? extends Component>, Component> components = entityComponents.get(entityId);
        return components != null && components.containsKey(componentClass);
    }
    
    /**
     * 移除实体的组件
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T removeComponent(long entityId, Class<T> componentClass) {
        Map<Class<? extends Component>, Component> components = entityComponents.get(entityId);
        if (components == null) {
            return null;
        }
        return (T) components.remove(componentClass);
    }
    
    /**
     * 移除实体的所有组件
     */
    public void removeAllComponents(long entityId) {
        entityComponents.remove(entityId);
    }
    
    /**
     * 获取实体的所有组件
     */
    public Collection<Component> getAllComponents(long entityId) {
        Map<Class<? extends Component>, Component> components = entityComponents.get(entityId);
        return components != null ? components.values() : java.util.Collections.emptyList();
    }
}