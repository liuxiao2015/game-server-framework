/*
 * 文件名: EventSystem.java
 * 用途: 事件处理系统基类
 * 实现内容:
 *   - 事件处理系统基类
 *   - 事件订阅和处理机制
 *   - 事件过滤和路由
 *   - 异步事件处理支持
 * 技术选型:
 *   - 观察者模式实现事件处理
 *   - 泛型设计支持类型安全
 *   - 异步处理提升性能
 * 依赖关系:
 *   - 继承AbstractSystem
 *   - 依赖EventBus进行事件通信
 *   - 为具体事件处理系统提供基础
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.core.AbstractSystem;
import com.lx.gameserver.frame.ecs.event.EventBus;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 事件处理系统
 * <p>
 * 专门用于处理各种游戏事件的系统基类。
 * 支持事件订阅、过滤和异步处理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class EventSystem extends AbstractSystem {
    
    /**
     * 事件处理器映射表
     */
    private final Set<Class<?>> subscribedEventTypes;
    
    /**
     * 事件总线引用
     */
    private EventBus eventBus;
    
    /**
     * 是否支持异步处理
     */
    private final boolean asyncProcessing;
    
    /**
     * 事件处理队列大小限制
     */
    private int maxQueueSize = 1000;
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param asyncProcessing 是否支持异步处理
     */
    protected EventSystem(String name, int priority, boolean asyncProcessing) {
        super(name, priority);
        this.asyncProcessing = asyncProcessing;
        this.subscribedEventTypes = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * 构造函数（默认同步处理）
     *
     * @param name 系统名称
     * @param priority 系统优先级
     */
    protected EventSystem(String name, int priority) {
        this(name, priority, false);
    }
    
    @Override
    protected void onInitialize() {
        this.eventBus = getWorld().getEventBus();
        
        // 注册事件处理器
        registerEventHandlers();
        
        // 订阅感兴趣的事件类型
        for (Class<?> eventType : subscribedEventTypes) {
            subscribeToEvent(eventType);
        }
        
        // 调用子类初始化
        onSystemInitialize();
    }
    
    @Override
    protected void onUpdate(float deltaTime) {
        // 事件系统通常不需要在update中做太多工作
        // 大部分工作在事件处理器中完成
        onSystemUpdate(deltaTime);
    }
    
    @Override
    protected void onDestroy() {
        // 取消所有事件订阅
        if (eventBus != null) {
            for (Class<?> eventType : subscribedEventTypes) {
                unsubscribeFromEvent(eventType);
            }
        }
        subscribedEventTypes.clear();
        
        // 调用子类销毁
        onSystemDestroy();
    }
    
    /**
     * 订阅事件类型
     *
     * @param eventType 事件类型
     * @param <T> 事件类型泛型
     */
    protected <T> void subscribeToEvent(Class<T> eventType) {
        subscribeToEvent(eventType, this::handleEvent);
    }
    
    /**
     * 订阅事件类型并指定处理器
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型泛型
     */
    protected <T> void subscribeToEvent(Class<T> eventType, Consumer<T> handler) {
        if (eventBus != null) {
            if (asyncProcessing) {
                eventBus.subscribeAsync(eventType, handler);
            } else {
                eventBus.subscribe(eventType, handler);
            }
            subscribedEventTypes.add(eventType);
        }
    }
    
    /**
     * 取消订阅事件类型
     *
     * @param eventType 事件类型
     * @param <T> 事件类型泛型
     */
    protected <T> void unsubscribeFromEvent(Class<T> eventType) {
        if (eventBus != null) {
            eventBus.unsubscribe(eventType, this::handleEvent);
            subscribedEventTypes.remove(eventType);
        }
    }
    
    /**
     * 发布事件
     *
     * @param event 事件对象
     * @param <T> 事件类型泛型
     */
    protected <T> void publishEvent(T event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }
    
    /**
     * 异步发布事件
     *
     * @param event 事件对象
     * @param <T> 事件类型泛型
     */
    protected <T> void publishEventAsync(T event) {
        if (eventBus != null) {
            eventBus.publishAsync(event);
        }
    }
    
    /**
     * 处理事件（子类实现）
     *
     * @param event 事件对象
     * @param <T> 事件类型泛型
     */
    protected abstract <T> void handleEvent(T event);
    
    /**
     * 注册事件处理器（子类实现）
     */
    protected abstract void registerEventHandlers();
    
    /**
     * 系统初始化回调
     */
    protected void onSystemInitialize() {
        // 默认空实现
    }
    
    /**
     * 系统更新回调
     *
     * @param deltaTime 时间增量
     */
    protected void onSystemUpdate(float deltaTime) {
        // 默认空实现
    }
    
    /**
     * 系统销毁回调
     */
    protected void onSystemDestroy() {
        // 默认空实现
    }
    
    /**
     * 检查是否支持异步处理
     *
     * @return 如果支持异步处理返回true
     */
    public boolean isAsyncProcessing() {
        return asyncProcessing;
    }
    
    /**
     * 获取订阅的事件类型
     *
     * @return 事件类型集合
     */
    public Set<Class<?>> getSubscribedEventTypes() {
        return Set.copyOf(subscribedEventTypes);
    }
    
    /**
     * 设置事件队列最大大小
     *
     * @param maxQueueSize 最大队列大小
     */
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = Math.max(1, maxQueueSize);
    }
    
    /**
     * 获取事件队列最大大小
     *
     * @return 最大队列大小
     */
    public int getMaxQueueSize() {
        return maxQueueSize;
    }
}