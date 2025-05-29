/*
 * 文件名: GameEvent.java
 * 用途: 游戏事件基类
 * 实现内容:
 *   - 定义所有游戏事件的基础属性和行为
 *   - 事件ID、类型、时间戳、来源、优先级等基本信息
 *   - 提供事件的标准接口和行为
 * 技术选型:
 *   - 抽象类设计
 *   - 不可变对象模式
 *   - 事件溯源支持
 * 依赖关系:
 *   - 被所有具体事件类继承
 *   - 被EventHandler和EventBus使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.core;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏事件基类
 * <p>
 * 作为游戏服务器中所有事件的基类，定义了事件的基本属性和行为。
 * 所有具体的游戏事件都应该继承此类。
 * 事件对象是不可变的，一旦创建不能修改。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class GameEvent {
    
    /** 事件ID生成器 */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    
    /** 事件ID，全局唯一 */
    private final long eventId;
    
    /** 事件类型，通常为类的简单名称 */
    private final String eventType;
    
    /** 事件产生时间戳（毫秒） */
    private final long timestamp;
    
    /** 事件产生的本地时间 */
    private final LocalDateTime occurTime;
    
    /** 事件来源，标识事件的产生源 */
    private final String source;
    
    /** 事件优先级 */
    private final EventPriority priority;
    
    /** 事件版本号，用于事件结构变更时的兼容性处理 */
    private final int version;
    
    /**
     * 构造函数 - 使用默认优先级
     *
     * @param source 事件来源
     */
    protected GameEvent(String source) {
        this(source, EventPriority.NORMAL);
    }
    
    /**
     * 构造函数 - 指定优先级
     *
     * @param source   事件来源
     * @param priority 事件优先级
     */
    protected GameEvent(String source, EventPriority priority) {
        this(source, priority, 1);
    }
    
    /**
     * 构造函数 - 完整参数
     *
     * @param source   事件来源
     * @param priority 事件优先级
     * @param version  事件版本号
     */
    protected GameEvent(String source, EventPriority priority, int version) {
        this.eventId = ID_GENERATOR.getAndIncrement();
        this.eventType = this.getClass().getSimpleName();
        this.timestamp = System.currentTimeMillis();
        this.occurTime = LocalDateTime.now();
        this.source = Objects.requireNonNull(source, "事件来源不能为空");
        this.priority = Objects.requireNonNull(priority, "事件优先级不能为空");
        this.version = version;
    }
    
    /**
     * 获取事件ID
     *
     * @return 事件ID
     */
    public long getEventId() {
        return eventId;
    }
    
    /**
     * 获取事件类型
     *
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
    }
    
    /**
     * 获取事件时间戳
     *
     * @return 时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取事件发生时间
     *
     * @return 本地时间
     */
    public LocalDateTime getOccurTime() {
        return occurTime;
    }
    
    /**
     * 获取事件来源
     *
     * @return 事件来源
     */
    public String getSource() {
        return source;
    }
    
    /**
     * 获取事件优先级
     *
     * @return 事件优先级
     */
    public EventPriority getPriority() {
        return priority;
    }
    
    /**
     * 获取事件版本号
     *
     * @return 版本号
     */
    public int getVersion() {
        return version;
    }
    
    /**
     * 获取事件的业务数据
     * <p>
     * 子类应该重写此方法，返回具体的业务数据。
     * 默认返回null。
     * </p>
     *
     * @return 业务数据对象
     */
    public Object getPayload() {
        return null;
    }
    
    /**
     * 判断事件是否有效
     * <p>
     * 子类可以重写此方法实现自定义的有效性检查。
     * 默认实现检查基本属性是否正确。
     * </p>
     *
     * @return 如果事件有效返回true
     */
    public boolean isValid() {
        return eventId > 0 && eventType != null && !eventType.isEmpty() 
               && timestamp > 0 && source != null && !source.isEmpty() 
               && priority != null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GameEvent gameEvent = (GameEvent) o;
        return eventId == gameEvent.eventId;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    @Override
    public String toString() {
        return String.format("%s{eventId=%d, eventType='%s', timestamp=%d, source='%s', priority=%s, version=%d}",
                getClass().getSimpleName(), eventId, eventType, timestamp, source, priority, version);
    }
}