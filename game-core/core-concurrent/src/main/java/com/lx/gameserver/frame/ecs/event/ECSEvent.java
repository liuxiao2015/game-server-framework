/*
 * 文件名: ECSEvent.java
 * 用途: ECS事件基类
 * 实现内容:
 *   - ECS事件基类定义
 *   - 事件类型管理
 *   - 事件优先级设置
 *   - 事件传播控制
 *   - 事件数据携带
 * 技术选型:
 *   - 抽象基类提供统一接口
 *   - 泛型设计支持类型安全
 *   - 时间戳标记事件发生时间
 * 依赖关系:
 *   - ECS事件系统的基础类
 *   - 被所有具体事件类继承
 *   - 为EventBus提供统一抽象
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.event;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECS事件基类
 * <p>
 * 所有ECS事件的基础类，提供事件的基本属性和行为。
 * 包含事件类型、时间戳、优先级等基础信息。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class ECSEvent implements Serializable {
    
    /**
     * 事件ID生成器
     */
    private static final AtomicLong EVENT_ID_GENERATOR = new AtomicLong(0);
    
    /**
     * 事件优先级枚举
     */
    public enum Priority {
        /** 最低优先级 */
        LOWEST(1),
        /** 低优先级 */
        LOW(2),
        /** 普通优先级 */
        NORMAL(3),
        /** 高优先级 */
        HIGH(4),
        /** 最高优先级 */
        HIGHEST(5);
        
        private final int value;
        
        Priority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 事件ID
     */
    private final long eventId;
    
    /**
     * 事件类型
     */
    private final String eventType;
    
    /**
     * 事件优先级
     */
    private final Priority priority;
    
    /**
     * 事件发生时间戳
     */
    private final long timestamp;
    
    /**
     * 事件来源
     */
    private final String source;
    
    /**
     * 是否可以取消
     */
    private final boolean cancellable;
    
    /**
     * 是否已被取消
     */
    private volatile boolean cancelled = false;
    
    /**
     * 是否已被处理
     */
    private volatile boolean handled = false;
    
    /**
     * 事件属性
     */
    private final Map<String, Object> properties;
    
    /**
     * 构造函数
     *
     * @param eventType 事件类型
     * @param source 事件来源
     * @param priority 事件优先级
     * @param cancellable 是否可以取消
     */
    protected ECSEvent(String eventType, String source, Priority priority, boolean cancellable) {
        this.eventId = EVENT_ID_GENERATOR.incrementAndGet();
        this.eventType = eventType;
        this.source = source;
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.cancellable = cancellable;
        this.timestamp = java.lang.System.currentTimeMillis();
        this.properties = new ConcurrentHashMap<>();
    }
    
    /**
     * 构造函数（默认优先级和不可取消）
     *
     * @param eventType 事件类型
     * @param source 事件来源
     */
    protected ECSEvent(String eventType, String source) {
        this(eventType, source, Priority.NORMAL, false);
    }
    
    /**
     * 构造函数（默认来源）
     *
     * @param eventType 事件类型
     */
    protected ECSEvent(String eventType) {
        this(eventType, "unknown", Priority.NORMAL, false);
    }
    
    /**
     * 取消事件
     *
     * @throws UnsupportedOperationException 如果事件不可取消
     */
    public void cancel() {
        if (!cancellable) {
            throw new UnsupportedOperationException("此事件不可取消");
        }
        this.cancelled = true;
    }
    
    /**
     * 标记事件为已处理
     */
    public void markHandled() {
        this.handled = true;
    }
    
    /**
     * 设置事件属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    /**
     * 获取事件属性
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @param <T> 属性类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        Object value = properties.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 获取事件属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在返回null
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * 检查是否有指定属性
     *
     * @param key 属性键
     * @return 如果存在返回true
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * 移除事件属性
     *
     * @param key 属性键
     * @return 被移除的属性值
     */
    public Object removeProperty(String key) {
        return properties.remove(key);
    }
    
    /**
     * 清空所有属性
     */
    public void clearProperties() {
        properties.clear();
    }
    
    /**
     * 获取所有属性
     *
     * @return 属性映射的副本
     */
    public Map<String, Object> getAllProperties() {
        return new ConcurrentHashMap<>(properties);
    }
    
    /**
     * 获取事件年龄（毫秒）
     *
     * @return 事件年龄
     */
    public long getAge() {
        return java.lang.System.currentTimeMillis() - timestamp;
    }
    
    /**
     * 检查事件是否过期
     *
     * @param maxAge 最大年龄（毫秒）
     * @return 如果过期返回true
     */
    public boolean isExpired(long maxAge) {
        return getAge() > maxAge;
    }
    
    /**
     * 创建事件的副本
     *
     * @return 事件副本
     */
    public abstract ECSEvent copy();
    
    // Getters
    public long getEventId() {
        return eventId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public Priority getPriority() {
        return priority;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getSource() {
        return source;
    }
    
    public boolean isCancellable() {
        return cancellable;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public boolean isHandled() {
        return handled;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ECSEvent ecsEvent = (ECSEvent) obj;
        return eventId == ecsEvent.eventId;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(eventId);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "eventId=" + eventId +
                ", eventType='" + eventType + '\'' +
                ", priority=" + priority +
                ", timestamp=" + timestamp +
                ", source='" + source + '\'' +
                ", cancellable=" + cancellable +
                ", cancelled=" + cancelled +
                ", handled=" + handled +
                ", properties=" + properties.size() +
                '}';
    }
}