/*
 * 文件名: EntityDestroyedEvent.java
 * 用途: 实体销毁事件
 * 实现内容:
 *   - 实体销毁事件定义
 *   - 携带实体销毁信息
 *   - 支持事件监听和处理
 *   - 提供实体访问接口
 * 技术选型:
 *   - 继承ECSEvent基类
 *   - 不可取消事件（销毁已完成）
 *   - 包含销毁前的实体信息
 * 依赖关系:
 *   - 继承ECSEvent基类
 *   - 被World在销毁实体时触发
 *   - 被相关系统监听处理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.event;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;

import java.util.Collections;
import java.util.Map;

/**
 * 实体销毁事件
 * <p>
 * 当实体被销毁时触发的事件，包含被销毁的实体信息。
 * 此事件不可取消，因为实体销毁已经完成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class EntityDestroyedEvent extends ECSEvent {
    
    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "entity.destroyed";
    
    /**
     * 被销毁的实体
     */
    private final Entity entity;
    
    /**
     * 销毁原因
     */
    private final String destroyReason;
    
    /**
     * 销毁前的组件快照
     */
    private final Map<Class<? extends Component>, Component> componentSnapshot;
    
    /**
     * 构造函数
     *
     * @param entity 被销毁的实体
     * @param source 事件来源
     * @param destroyReason 销毁原因
     */
    public EntityDestroyedEvent(Entity entity, String source, String destroyReason) {
        this(entity, source, destroyReason, Collections.emptyMap());
    }
    
    /**
     * 构造函数
     *
     * @param entity 被销毁的实体
     * @param source 事件来源
     * @param destroyReason 销毁原因
     * @param componentSnapshot 组件快照
     */
    public EntityDestroyedEvent(Entity entity, String source, String destroyReason, 
                               Map<Class<? extends Component>, Component> componentSnapshot) {
        super(EVENT_TYPE, source, Priority.NORMAL, false);
        this.entity = entity;
        this.destroyReason = destroyReason != null ? destroyReason : "unknown";
        this.componentSnapshot = componentSnapshot != null ? Map.copyOf(componentSnapshot) : Collections.emptyMap();
        
        // 设置事件属性
        setProperty("entityId", entity.getId());
        setProperty("entityVersion", entity.getVersion());
        setProperty("destroyReason", this.destroyReason);
        setProperty("componentCount", this.componentSnapshot.size());
    }
    
    /**
     * 获取被销毁的实体
     *
     * @return 实体对象
     */
    public Entity getEntity() {
        return entity;
    }
    
    /**
     * 获取实体ID
     *
     * @return 实体ID
     */
    public long getEntityId() {
        return entity.getId();
    }
    
    /**
     * 获取销毁原因
     *
     * @return 销毁原因
     */
    public String getDestroyReason() {
        return destroyReason;
    }
    
    /**
     * 获取组件快照
     *
     * @return 销毁前的组件映射
     */
    public Map<Class<? extends Component>, Component> getComponentSnapshot() {
        return componentSnapshot;
    }
    
    /**
     * 获取指定类型的组件快照
     *
     * @param componentType 组件类型
     * @param <T> 组件类型泛型
     * @return 组件实例，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> componentType) {
        return (T) componentSnapshot.get(componentType);
    }
    
    /**
     * 检查是否有指定类型的组件
     *
     * @param componentType 组件类型
     * @return 如果存在返回true
     */
    public boolean hasComponent(Class<? extends Component> componentType) {
        return componentSnapshot.containsKey(componentType);
    }
    
    /**
     * 获取组件数量
     *
     * @return 组件数量
     */
    public int getComponentCount() {
        return componentSnapshot.size();
    }
    
    @Override
    public ECSEvent copy() {
        EntityDestroyedEvent copy = new EntityDestroyedEvent(entity, getSource(), destroyReason, componentSnapshot);
        copy.getAllProperties().forEach(copy::setProperty);
        return copy;
    }
    
    @Override
    public String toString() {
        return "EntityDestroyedEvent{" +
                "entityId=" + entity.getId() +
                ", destroyReason='" + destroyReason + '\'' +
                ", componentCount=" + componentSnapshot.size() +
                ", source='" + getSource() + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}