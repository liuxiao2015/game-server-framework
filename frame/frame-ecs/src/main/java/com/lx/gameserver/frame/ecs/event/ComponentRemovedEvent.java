/*
 * 文件名: ComponentRemovedEvent.java
 * 用途: 组件移除事件
 * 实现内容:
 *   - 组件移除事件定义
 *   - 携带组件移除信息
 *   - 支持事件监听和处理
 *   - 提供移除前组件信息
 * 技术选型:
 *   - 继承ECSEvent基类
 *   - 不可取消事件（移除已完成）
 *   - 保存移除前的组件状态
 * 依赖关系:
 *   - 继承ECSEvent基类
 *   - 被World在移除组件时触发
 *   - 被相关系统监听处理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.event;

import com.lx.gameserver.frame.ecs.core.Component;

/**
 * 组件移除事件
 * <p>
 * 当组件从实体中移除时触发的事件，包含被移除的组件信息。
 * 此事件不可取消，因为组件移除已经完成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ComponentRemovedEvent extends ECSEvent {
    
    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "component.removed";
    
    /**
     * 实体ID
     */
    private final long entityId;
    
    /**
     * 被移除的组件（快照）
     */
    private final Component removedComponent;
    
    /**
     * 组件类型
     */
    private final Class<? extends Component> componentType;
    
    /**
     * 移除原因
     */
    private final String removeReason;
    
    /**
     * 构造函数
     *
     * @param entityId 实体ID
     * @param removedComponent 被移除的组件
     * @param source 事件来源
     * @param removeReason 移除原因
     */
    public ComponentRemovedEvent(long entityId, Component removedComponent, String source, String removeReason) {
        super(EVENT_TYPE, source, Priority.NORMAL, false);
        this.entityId = entityId;
        this.removedComponent = removedComponent != null ? removedComponent.clone() : null;
        this.componentType = removedComponent != null ? removedComponent.getClass() : null;
        this.removeReason = removeReason != null ? removeReason : "unknown";
        
        // 设置事件属性
        setProperty("entityId", entityId);
        if (componentType != null) {
            setProperty("componentType", componentType.getSimpleName());
        }
        if (removedComponent != null) {
            setProperty("componentVersion", removedComponent.getVersion());
            setProperty("componentTypeId", removedComponent.getTypeId());
        }
        setProperty("removeReason", this.removeReason);
    }
    
    /**
     * 构造函数（无移除原因）
     *
     * @param entityId 实体ID
     * @param removedComponent 被移除的组件
     * @param source 事件来源
     */
    public ComponentRemovedEvent(long entityId, Component removedComponent, String source) {
        this(entityId, removedComponent, source, null);
    }
    
    /**
     * 获取实体ID
     *
     * @return 实体ID
     */
    public long getEntityId() {
        return entityId;
    }
    
    /**
     * 获取被移除的组件
     *
     * @return 组件实例的快照，如果组件为null返回null
     */
    public Component getRemovedComponent() {
        return removedComponent;
    }
    
    /**
     * 获取组件类型
     *
     * @return 组件类型，如果组件为null返回null
     */
    public Class<? extends Component> getComponentType() {
        return componentType;
    }
    
    /**
     * 获取移除原因
     *
     * @return 移除原因
     */
    public String getRemoveReason() {
        return removeReason;
    }
    
    /**
     * 获取组件的类型安全实例
     *
     * @param expectedType 期望的组件类型
     * @param <T> 组件类型泛型
     * @return 类型安全的组件实例
     * @throws ClassCastException 如果类型不匹配
     * @throws IllegalStateException 如果组件为null
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getRemovedComponent(Class<T> expectedType) {
        if (removedComponent == null) {
            throw new IllegalStateException("被移除的组件为null");
        }
        if (!expectedType.isAssignableFrom(componentType)) {
            throw new ClassCastException("期望组件类型 " + expectedType.getSimpleName() + 
                    "，但实际类型是 " + componentType.getSimpleName());
        }
        return (T) removedComponent;
    }
    
    /**
     * 检查组件类型是否匹配
     *
     * @param expectedType 期望的组件类型
     * @return 如果类型匹配返回true
     */
    public boolean isComponentType(Class<? extends Component> expectedType) {
        return componentType != null && expectedType.isAssignableFrom(componentType);
    }
    
    /**
     * 检查是否有有效的组件信息
     *
     * @return 如果有有效组件信息返回true
     */
    public boolean hasValidComponent() {
        return removedComponent != null && componentType != null;
    }
    
    /**
     * 获取组件版本
     *
     * @return 组件版本号，如果组件为null返回-1
     */
    public long getComponentVersion() {
        return removedComponent != null ? removedComponent.getVersion() : -1;
    }
    
    /**
     * 获取组件类型ID
     *
     * @return 组件类型ID，如果组件为null返回-1
     */
    public int getComponentTypeId() {
        return removedComponent != null ? removedComponent.getTypeId() : -1;
    }
    
    @Override
    public ECSEvent copy() {
        ComponentRemovedEvent copy = new ComponentRemovedEvent(entityId, removedComponent, getSource(), removeReason);
        copy.getAllProperties().forEach(copy::setProperty);
        return copy;
    }
    
    @Override
    public String toString() {
        return "ComponentRemovedEvent{" +
                "entityId=" + entityId +
                ", componentType=" + (componentType != null ? componentType.getSimpleName() : "null") +
                ", componentVersion=" + getComponentVersion() +
                ", removeReason='" + removeReason + '\'' +
                ", source='" + getSource() + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}