/*
 * 文件名: ComponentAddedEvent.java
 * 用途: 组件添加事件
 * 实现内容:
 *   - 组件添加事件定义
 *   - 携带组件添加信息
 *   - 支持事件监听和处理
 *   - 提供组件访问接口
 * 技术选型:
 *   - 继承ECSEvent基类
 *   - 不可取消事件（添加已完成）
 *   - 包含完整组件信息
 * 依赖关系:
 *   - 继承ECSEvent基类
 *   - 被World在添加组件时触发
 *   - 被相关系统监听处理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.event;

import com.lx.gameserver.frame.ecs.core.Component;

/**
 * 组件添加事件
 * <p>
 * 当组件被添加到实体时触发的事件，包含被添加的组件信息。
 * 此事件不可取消，因为组件添加已经完成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ComponentAddedEvent extends ECSEvent {
    
    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "component.added";
    
    /**
     * 实体ID
     */
    private final long entityId;
    
    /**
     * 被添加的组件
     */
    private final Component component;
    
    /**
     * 组件类型
     */
    private final Class<? extends Component> componentType;
    
    /**
     * 构造函数
     *
     * @param entityId 实体ID
     * @param component 被添加的组件
     * @param source 事件来源
     */
    public ComponentAddedEvent(long entityId, Component component, String source) {
        super(EVENT_TYPE, source, Priority.NORMAL, false);
        this.entityId = entityId;
        this.component = component;
        this.componentType = component.getClass();
        
        // 设置事件属性
        setProperty("entityId", entityId);
        setProperty("componentType", componentType.getSimpleName());
        setProperty("componentVersion", component.getVersion());
        setProperty("componentTypeId", component.getTypeId());
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
     * 获取被添加的组件
     *
     * @return 组件实例
     */
    public Component getComponent() {
        return component;
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
     * 获取组件的类型安全实例
     *
     * @param expectedType 期望的组件类型
     * @param <T> 组件类型泛型
     * @return 类型安全的组件实例
     * @throws ClassCastException 如果类型不匹配
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> expectedType) {
        if (!expectedType.isAssignableFrom(componentType)) {
            throw new ClassCastException("期望组件类型 " + expectedType.getSimpleName() + 
                    "，但实际类型是 " + componentType.getSimpleName());
        }
        return (T) component;
    }
    
    /**
     * 检查组件类型是否匹配
     *
     * @param expectedType 期望的组件类型
     * @return 如果类型匹配返回true
     */
    public boolean isComponentType(Class<? extends Component> expectedType) {
        return expectedType.isAssignableFrom(componentType);
    }
    
    /**
     * 获取组件版本
     *
     * @return 组件版本号
     */
    public long getComponentVersion() {
        return component.getVersion();
    }
    
    /**
     * 获取组件类型ID
     *
     * @return 组件类型ID
     */
    public int getComponentTypeId() {
        return component.getTypeId();
    }
    
    @Override
    public ECSEvent copy() {
        // 创建组件的副本
        Component componentCopy = component.clone();
        ComponentAddedEvent copy = new ComponentAddedEvent(entityId, componentCopy, getSource());
        copy.getAllProperties().forEach(copy::setProperty);
        return copy;
    }
    
    @Override
    public String toString() {
        return "ComponentAddedEvent{" +
                "entityId=" + entityId +
                ", componentType=" + componentType.getSimpleName() +
                ", componentVersion=" + component.getVersion() +
                ", source='" + getSource() + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}