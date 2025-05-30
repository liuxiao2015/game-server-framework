/*
 * 文件名: ComponentChangedEvent.java
 * 用途: 组件变更事件
 * 实现内容:
 *   - 组件变更事件定义
 *   - 携带组件变更信息
 *   - 支持事件监听和处理
 *   - 提供变更前后对比信息
 * 技术选型:
 *   - 继承ECSEvent基类
 *   - 不可取消事件（变更已完成）
 *   - 保存变更前后的组件状态
 * 依赖关系:
 *   - 继承ECSEvent基类
 *   - 被组件在状态变更时触发
 *   - 被相关系统监听处理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.event;

import com.lx.gameserver.frame.ecs.core.Component;

/**
 * 组件变更事件
 * <p>
 * 当组件状态发生变化时触发的事件，包含变更前后的组件状态。
 * 此事件不可取消，因为组件变更已经完成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ComponentChangedEvent extends ECSEvent {
    
    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "component.changed";
    
    /**
     * 实体ID
     */
    private final long entityId;
    
    /**
     * 变更前的组件状态
     */
    private final Component oldComponent;
    
    /**
     * 变更后的组件状态
     */
    private final Component newComponent;
    
    /**
     * 组件类型
     */
    private final Class<? extends Component> componentType;
    
    /**
     * 变更字段列表
     */
    private final String[] changedFields;
    
    /**
     * 变更原因
     */
    private final String changeReason;
    
    /**
     * 构造函数
     *
     * @param entityId 实体ID
     * @param oldComponent 变更前的组件状态
     * @param newComponent 变更后的组件状态
     * @param source 事件来源
     * @param changeReason 变更原因
     * @param changedFields 变更的字段列表
     */
    public ComponentChangedEvent(long entityId, Component oldComponent, Component newComponent, 
                                String source, String changeReason, String... changedFields) {
        super(EVENT_TYPE, source, Priority.NORMAL, false);
        this.entityId = entityId;
        this.oldComponent = oldComponent != null ? oldComponent.clone() : null;
        this.newComponent = newComponent != null ? newComponent.clone() : null;
        this.componentType = newComponent != null ? newComponent.getClass() : 
                           (oldComponent != null ? oldComponent.getClass() : null);
        this.changeReason = changeReason != null ? changeReason : "unknown";
        this.changedFields = changedFields != null ? changedFields.clone() : new String[0];
        
        // 设置事件属性
        setProperty("entityId", entityId);
        if (componentType != null) {
            setProperty("componentType", componentType.getSimpleName());
        }
        if (oldComponent != null) {
            setProperty("oldVersion", oldComponent.getVersion());
        }
        if (newComponent != null) {
            setProperty("newVersion", newComponent.getVersion());
            setProperty("componentTypeId", newComponent.getTypeId());
        }
        setProperty("changeReason", this.changeReason);
        setProperty("changedFieldCount", this.changedFields.length);
        if (this.changedFields.length > 0) {
            setProperty("changedFields", String.join(",", this.changedFields));
        }
    }
    
    /**
     * 构造函数（无变更字段）
     *
     * @param entityId 实体ID
     * @param oldComponent 变更前的组件状态
     * @param newComponent 变更后的组件状态
     * @param source 事件来源
     * @param changeReason 变更原因
     */
    public ComponentChangedEvent(long entityId, Component oldComponent, Component newComponent, 
                                String source, String changeReason) {
        this(entityId, oldComponent, newComponent, source, changeReason, (String[]) null);
    }
    
    /**
     * 构造函数（无变更原因和字段）
     *
     * @param entityId 实体ID
     * @param oldComponent 变更前的组件状态
     * @param newComponent 变更后的组件状态
     * @param source 事件来源
     */
    public ComponentChangedEvent(long entityId, Component oldComponent, Component newComponent, String source) {
        this(entityId, oldComponent, newComponent, source, null);
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
     * 获取变更前的组件状态
     *
     * @return 变更前的组件快照，如果不存在返回null
     */
    public Component getOldComponent() {
        return oldComponent;
    }
    
    /**
     * 获取变更后的组件状态
     *
     * @return 变更后的组件快照，如果不存在返回null
     */
    public Component getNewComponent() {
        return newComponent;
    }
    
    /**
     * 获取组件类型
     *
     * @return 组件类型
     */
    public Class<? extends Component> getComponentType() {
        return componentType;
    }
    
    /**
     * 获取变更原因
     *
     * @return 变更原因
     */
    public String getChangeReason() {
        return changeReason;
    }
    
    /**
     * 获取变更字段列表
     *
     * @return 变更字段数组的副本
     */
    public String[] getChangedFields() {
        return changedFields.clone();
    }
    
    /**
     * 检查指定字段是否发生了变更
     *
     * @param fieldName 字段名
     * @return 如果字段发生了变更返回true
     */
    public boolean isFieldChanged(String fieldName) {
        for (String field : changedFields) {
            if (field.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取变更前组件的类型安全实例
     *
     * @param expectedType 期望的组件类型
     * @param <T> 组件类型泛型
     * @return 类型安全的组件实例
     * @throws ClassCastException 如果类型不匹配
     * @throws IllegalStateException 如果组件为null
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getOldComponent(Class<T> expectedType) {
        if (oldComponent == null) {
            throw new IllegalStateException("变更前的组件为null");
        }
        if (!expectedType.isAssignableFrom(componentType)) {
            throw new ClassCastException("期望组件类型 " + expectedType.getSimpleName() + 
                    "，但实际类型是 " + componentType.getSimpleName());
        }
        return (T) oldComponent;
    }
    
    /**
     * 获取变更后组件的类型安全实例
     *
     * @param expectedType 期望的组件类型
     * @param <T> 组件类型泛型
     * @return 类型安全的组件实例
     * @throws ClassCastException 如果类型不匹配
     * @throws IllegalStateException 如果组件为null
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getNewComponent(Class<T> expectedType) {
        if (newComponent == null) {
            throw new IllegalStateException("变更后的组件为null");
        }
        if (!expectedType.isAssignableFrom(componentType)) {
            throw new ClassCastException("期望组件类型 " + expectedType.getSimpleName() + 
                    "，但实际类型是 " + componentType.getSimpleName());
        }
        return (T) newComponent;
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
     * 检查是否有版本变更
     *
     * @return 如果版本发生了变更返回true
     */
    public boolean hasVersionChanged() {
        if (oldComponent == null || newComponent == null) {
            return true; // 如果任一组件为null，认为发生了变更
        }
        return oldComponent.getVersion() != newComponent.getVersion();
    }
    
    /**
     * 获取版本变更差异
     *
     * @return 版本变更差异，如果无法计算返回0
     */
    public long getVersionDelta() {
        if (oldComponent == null || newComponent == null) {
            return 0;
        }
        return newComponent.getVersion() - oldComponent.getVersion();
    }
    
    /**
     * 获取变更字段数量
     *
     * @return 变更字段数量
     */
    public int getChangedFieldCount() {
        return changedFields.length;
    }
    
    @Override
    public ECSEvent copy() {
        ComponentChangedEvent copy = new ComponentChangedEvent(entityId, oldComponent, newComponent, 
                getSource(), changeReason, changedFields);
        copy.getAllProperties().forEach(copy::setProperty);
        return copy;
    }
    
    @Override
    public String toString() {
        return "ComponentChangedEvent{" +
                "entityId=" + entityId +
                ", componentType=" + (componentType != null ? componentType.getSimpleName() : "null") +
                ", oldVersion=" + (oldComponent != null ? oldComponent.getVersion() : "null") +
                ", newVersion=" + (newComponent != null ? newComponent.getVersion() : "null") +
                ", changeReason='" + changeReason + '\'' +
                ", changedFields=" + changedFields.length +
                ", source='" + getSource() + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}