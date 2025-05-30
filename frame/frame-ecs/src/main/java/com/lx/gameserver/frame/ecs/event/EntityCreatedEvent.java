/*
 * 文件名: EntityCreatedEvent.java
 * 用途: 实体创建事件
 * 实现内容:
 *   - 实体创建事件定义
 *   - 携带实体创建信息
 *   - 支持事件监听和处理
 *   - 提供实体访问接口
 * 技术选型:
 *   - 继承ECSEvent基类
 *   - 不可取消事件（创建已完成）
 *   - 包含完整实体信息
 * 依赖关系:
 *   - 继承ECSEvent基类
 *   - 被World在创建实体时触发
 *   - 被相关系统监听处理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.event;

import com.lx.gameserver.frame.ecs.core.Entity;

/**
 * 实体创建事件
 * <p>
 * 当实体被创建时触发的事件，包含被创建的实体信息。
 * 此事件不可取消，因为实体创建已经完成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class EntityCreatedEvent extends ECSEvent {
    
    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "entity.created";
    
    /**
     * 被创建的实体
     */
    private final Entity entity;
    
    /**
     * 创建原型ID（如果有）
     */
    private final Long archetypeId;
    
    /**
     * 构造函数
     *
     * @param entity 被创建的实体
     * @param source 事件来源
     */
    public EntityCreatedEvent(Entity entity, String source) {
        this(entity, source, null);
    }
    
    /**
     * 构造函数
     *
     * @param entity 被创建的实体
     * @param source 事件来源
     * @param archetypeId 原型ID
     */
    public EntityCreatedEvent(Entity entity, String source, Long archetypeId) {
        super(EVENT_TYPE, source, Priority.NORMAL, false);
        this.entity = entity;
        this.archetypeId = archetypeId;
        
        // 设置事件属性
        setProperty("entityId", entity.getId());
        setProperty("entityVersion", entity.getVersion());
        if (archetypeId != null) {
            setProperty("archetypeId", archetypeId);
        }
    }
    
    /**
     * 获取被创建的实体
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
     * 获取原型ID
     *
     * @return 原型ID，如果没有使用原型返回null
     */
    public Long getArchetypeId() {
        return archetypeId;
    }
    
    /**
     * 检查是否使用了原型创建
     *
     * @return 如果使用了原型返回true
     */
    public boolean hasArchetype() {
        return archetypeId != null;
    }
    
    @Override
    public ECSEvent copy() {
        EntityCreatedEvent copy = new EntityCreatedEvent(entity, getSource(), archetypeId);
        copy.getAllProperties().forEach(copy::setProperty);
        return copy;
    }
    
    @Override
    public String toString() {
        return "EntityCreatedEvent{" +
                "entityId=" + entity.getId() +
                ", archetypeId=" + archetypeId +
                ", source='" + getSource() + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}