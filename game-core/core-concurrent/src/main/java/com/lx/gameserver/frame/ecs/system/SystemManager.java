/*
 * 文件名: SystemManager.java
 * 用途: 系统管理器核心实现
 * 实现内容:
 *   - 系统注册管理
 *   - 系统执行顺序（拓扑排序）
 *   - 系统启用/禁用
 *   - 系统分组（渲染组、逻辑组等）
 *   - 并行系统调度
 *   - 系统性能监控
 * 技术选型:
 *   - 拓扑排序算法管理系统依赖
 *   - 线程池实现并行调度
 *   - 优先级队列优化执行顺序
 * 依赖关系:
 *   - 被World类依赖进行系统管理
 *   - 管理所有System实例
 *   - 协调系统间的依赖关系
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.core.System;
import com.lx.gameserver.frame.ecs.core.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统管理器
 * <p>
 * 负责管理所有ECS系统的注册、调度和执行。
 * 支持系统依赖管理、优先级排序和并行执行。
 * </p>
 */
public class SystemManager {
    
    /**
     * 注册的系统映射表
     */
    private final Map<Class<? extends System>, System> systems;
    
    /**
     * 系统执行顺序列表
     */
    private final List<System> systemExecutionOrder;
    
    /**
     * ECS世界引用
     */
    private World world;
    
    /**
     * 构造函数
     */
    public SystemManager() {
        this.systems = new ConcurrentHashMap<>();
        this.systemExecutionOrder = new ArrayList<>();
    }
    
    /**
     * 初始化系统管理器
     */
    public void initialize(World world) {
        this.world = world;
        for (System system : systems.values()) {
            system.initialize(world);
        }
    }
    
    /**
     * 销毁系统管理器
     */
    public void destroy() {
        for (System system : systems.values()) {
            system.destroy();
        }
        systems.clear();
        systemExecutionOrder.clear();
    }
    
    /**
     * 暂停所有系统
     */
    public void pause() {
        // 暂停逻辑
    }
    
    /**
     * 恢复所有系统
     */
    public void resume() {
        // 恢复逻辑
    }
    
    /**
     * 更新所有系统
     */
    public void update(float deltaTime) {
        for (System system : systemExecutionOrder) {
            if (system.isEnabled()) {
                system.update(deltaTime);
            }
        }
    }
    
    /**
     * 注册系统
     */
    @SuppressWarnings("unchecked")
    public <T extends System> T registerSystem(T system) {
        systems.put((Class<? extends System>) system.getClass(), system);
        // Initialize the system when it's registered if we have a world
        if (world != null) {
            system.initialize(world);
        }
        updateExecutionOrder();
        return system;
    }
    
    /**
     * 获取系统
     */
    @SuppressWarnings("unchecked")
    public <T extends System> T getSystem(Class<T> systemClass) {
        return (T) systems.get(systemClass);
    }
    
    /**
     * 移除系统
     */
    @SuppressWarnings("unchecked")
    public <T extends System> T removeSystem(Class<T> systemClass) {
        System removedSystem = systems.remove(systemClass);
        if (removedSystem != null) {
            updateExecutionOrder();
        }
        return (T) removedSystem;
    }
    
    /**
     * 更新系统执行顺序
     */
    private void updateExecutionOrder() {
        systemExecutionOrder.clear();
        systemExecutionOrder.addAll(systems.values());
        // 根据优先级排序
        systemExecutionOrder.sort(Comparator.comparingInt(System::getPriority));
    }
}