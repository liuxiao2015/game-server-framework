/*
 * 文件名: SceneEntity.java
 * 用途: 场景实体基类定义
 * 实现内容:
 *   - 场景实体的基础属性和行为定义
 *   - 实体类型枚举和状态管理
 *   - 位置信息和移动组件集成
 *   - 视野范围和AOI相关属性
 *   - 扩展属性支持和状态同步
 * 技术选型:
 *   - 抽象类设计提供基础实现和扩展点
 *   - 枚举类型保证实体类型安全
 *   - 组合模式集成移动和视野组件
 *   - ConcurrentHashMap保证属性线程安全
 * 依赖关系:
 *   - 被Scene类管理和调度
 *   - 与AOI系统协作进行视野管理
 *   - 被EntityManager统一管理
 *   - 与MovementSystem协作处理移动
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景实体基类
 * <p>
 * 定义场景中所有实体的基础属性和行为，包括实体类型、
 * 位置信息、状态管理、视野范围等核心功能。为不同类型
 * 的实体提供统一的基础框架。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Data
public abstract class SceneEntity {

    /**
     * 实体类型枚举
     */
    public enum EntityType {
        /** 玩家实体 */
        PLAYER("player", "玩家"),
        /** NPC实体 */
        NPC("npc", "NPC"),
        /** 怪物实体 */
        MONSTER("monster", "怪物"),
        /** 物品实体 */
        ITEM("item", "物品"),
        /** 陷阱实体 */
        TRAP("trap", "陷阱"),
        /** 传送门实体 */
        PORTAL("portal", "传送门"),
        /** 建筑实体 */
        BUILDING("building", "建筑"),
        /** 载具实体 */
        VEHICLE("vehicle", "载具"),
        /** 技能效果实体 */
        SKILL_EFFECT("skill_effect", "技能效果"),
        /** 环境实体 */
        ENVIRONMENT("environment", "环境实体");

        private final String code;
        private final String name;

        EntityType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 实体状态枚举
     */
    public enum EntityState {
        /** 未激活 */
        INACTIVE("inactive", "未激活"),
        /** 激活中 */
        ACTIVE("active", "激活中"),
        /** 移动中 */
        MOVING("moving", "移动中"),
        /** 战斗中 */
        COMBAT("combat", "战斗中"),
        /** 死亡状态 */
        DEAD("dead", "死亡"),
        /** 隐身状态 */
        INVISIBLE("invisible", "隐身"),
        /** 无敌状态 */
        INVINCIBLE("invincible", "无敌"),
        /** 被销毁 */
        DESTROYED("destroyed", "被销毁");

        private final String code;
        private final String name;

        EntityState(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 移动组件
     */
    @Data
    public static class MovementComponent {
        /** 移动速度 */
        private float speed = 5.0f;
        /** 最大速度 */
        private float maxSpeed = 10.0f;
        /** 是否可以移动 */
        private boolean canMove = true;
        /** 最后移动时间 */
        private LocalDateTime lastMoveTime;
        /** 移动目标位置 */
        private Scene.Position targetPosition;
        /** 移动路径 */
        private java.util.List<Scene.Position> movePath;

        public MovementComponent() {
            this.lastMoveTime = LocalDateTime.now();
        }

        /**
         * 检查是否正在移动
         *
         * @return 是否正在移动
         */
        public boolean isMoving() {
            return targetPosition != null || (movePath != null && !movePath.isEmpty());
        }

        /**
         * 停止移动
         */
        public void stopMovement() {
            targetPosition = null;
            movePath = null;
        }
    }

    // ========== 基础属性 ==========

    /** 实体ID */
    private final Long entityId;

    /** 实体类型 */
    private final EntityType entityType;

    /** 实体名称 */
    private String entityName;

    /** 实体状态 */
    private volatile EntityState state = EntityState.INACTIVE;

    /** 所在场景ID */
    private Long sceneId;

    /** 当前位置 */
    private Scene.Position position;

    /** 朝向 */
    private float direction = 0.0f;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 最后更新时间 */
    private volatile LocalDateTime lastUpdateTime;

    // ========== 视野和AOI属性 ==========

    /** 视野范围 */
    private double viewRange = 100.0;

    /** 是否可见 */
    private boolean visible = true;

    /** 是否对其他实体可见 */
    private boolean visibleToOthers = true;

    // ========== 移动组件 ==========

    /** 移动组件 */
    private final MovementComponent movementComponent;

    // ========== 扩展属性 ==========

    /** 实体属性 */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /** 实体标签 */
    private final Map<String, String> tags = new ConcurrentHashMap<>();

    /** 实体数据 */
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    // ========== 统计信息 ==========

    /** 更新计数器 */
    private final AtomicLong updateCount = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param entityId 实体ID
     * @param entityType 实体类型
     * @param entityName 实体名称
     */
    public SceneEntity(Long entityId, EntityType entityType, String entityName) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.entityName = entityName;
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = createTime;
        this.movementComponent = new MovementComponent();
        this.position = new Scene.Position(0, 0, 0);
    }

    // ========== 生命周期方法 ==========

    /**
     * 初始化实体
     *
     * @throws Exception 初始化异常
     */
    public final void initialize() throws Exception {
        log.debug("初始化实体: {} (ID: {}, Type: {})", entityName, entityId, entityType);
        
        try {
            // 调用子类初始化方法
            onInitialize();
            
            state = EntityState.ACTIVE;
            lastUpdateTime = LocalDateTime.now();
            
            log.debug("实体 {} 初始化完成", entityName);
            
        } catch (Exception e) {
            log.error("实体 {} 初始化失败", entityName, e);
            state = EntityState.DESTROYED;
            throw e;
        }
    }

    /**
     * 销毁实体
     */
    public final void destroy() {
        log.debug("销毁实体: {} (ID: {})", entityName, entityId);
        
        try {
            // 停止移动
            movementComponent.stopMovement();
            
            // 调用子类销毁方法
            onDestroy();
            
            // 清理数据
            attributes.clear();
            tags.clear();
            data.clear();
            
            state = EntityState.DESTROYED;
            lastUpdateTime = LocalDateTime.now();
            
            log.debug("实体 {} 销毁完成", entityName);
            
        } catch (Exception e) {
            log.error("实体 {} 销毁失败", entityName, e);
        }
    }

    /**
     * 更新实体
     *
     * @param deltaTime 距离上次更新的时间间隔（毫秒）
     */
    public final void update(long deltaTime) {
        if (state == EntityState.DESTROYED) {
            return;
        }

        try {
            // 调用子类更新方法
            onUpdate(deltaTime);
            
            updateCount.incrementAndGet();
            lastUpdateTime = LocalDateTime.now();
            
        } catch (Exception e) {
            log.error("实体 {} 更新失败", entityName, e);
        }
    }

    // ========== 位置和移动 ==========

    /**
     * 设置位置
     *
     * @param newPosition 新位置
     */
    public void setPosition(Scene.Position newPosition) {
        if (newPosition == null) {
            return;
        }

        Scene.Position oldPosition = this.position;
        this.position = newPosition.copy();
        
        // 调用位置变化回调
        onPositionChanged(oldPosition, newPosition);
        
        lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 移动到指定位置
     *
     * @param targetPos 目标位置
     */
    public void moveTo(Scene.Position targetPos) {
        if (targetPos == null || !movementComponent.isCanMove()) {
            return;
        }

        movementComponent.setTargetPosition(targetPos.copy());
        movementComponent.setLastMoveTime(LocalDateTime.now());
        
        if (state == EntityState.ACTIVE) {
            state = EntityState.MOVING;
        }
        
        // 调用移动开始回调
        onMoveStart(targetPos);
    }

    /**
     * 停止移动
     */
    public void stopMovement() {
        movementComponent.stopMovement();
        
        if (state == EntityState.MOVING) {
            state = EntityState.ACTIVE;
        }
        
        // 调用移动停止回调
        onMoveStop();
    }

    /**
     * 计算与另一个实体的距离
     *
     * @param other 另一个实体
     * @return 距离值
     */
    public double distanceTo(SceneEntity other) {
        if (other == null || other.position == null) {
            return Double.MAX_VALUE;
        }
        return this.position.distanceTo(other.position);
    }

    /**
     * 检查是否在指定实体的范围内
     *
     * @param other 目标实体
     * @param range 范围
     * @return 是否在范围内
     */
    public boolean isWithinRange(SceneEntity other, double range) {
        return distanceTo(other) <= range;
    }

    // ========== 属性管理 ==========

    /**
     * 设置属性
     *
     * @param key 属性名
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取属性
     *
     * @param key 属性名
     * @return 属性值
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 获取属性
     *
     * @param key 属性名
     * @param defaultValue 默认值
     * @param <T> 类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 移除属性
     *
     * @param key 属性名
     * @return 被移除的属性值
     */
    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }

    /**
     * 设置标签
     *
     * @param key 标签名
     * @param value 标签值
     */
    public void setTag(String key, String value) {
        tags.put(key, value);
    }

    /**
     * 获取标签
     *
     * @param key 标签名
     * @return 标签值
     */
    public String getTag(String key) {
        return tags.get(key);
    }

    /**
     * 检查是否有标签
     *
     * @param key 标签名
     * @return 是否有该标签
     */
    public boolean hasTag(String key) {
        return tags.containsKey(key);
    }

    /**
     * 设置数据
     *
     * @param key 数据键
     * @param value 数据值
     */
    public void setData(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取数据
     *
     * @param key 数据键
     * @return 数据值
     */
    public Object getData(String key) {
        return data.get(key);
    }

    /**
     * 获取数据
     *
     * @param key 数据键
     * @param defaultValue 默认值
     * @param <T> 类型
     * @return 数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }

    // ========== 状态检查 ==========

    /**
     * 检查实体是否激活
     *
     * @return 是否激活
     */
    public boolean isActive() {
        return state == EntityState.ACTIVE || state == EntityState.MOVING || state == EntityState.COMBAT;
    }

    /**
     * 检查实体是否可见
     *
     * @return 是否可见
     */
    public boolean isVisible() {
        return visible && state != EntityState.DESTROYED && state != EntityState.INVISIBLE;
    }

    /**
     * 检查实体是否正在移动
     *
     * @return 是否正在移动
     */
    public boolean isMoving() {
        return state == EntityState.MOVING || movementComponent.isMoving();
    }

    /**
     * 检查实体是否死亡
     *
     * @return 是否死亡
     */
    public boolean isDead() {
        return state == EntityState.DEAD;
    }

    /**
     * 检查实体是否被销毁
     *
     * @return 是否被销毁
     */
    public boolean isDestroyed() {
        return state == EntityState.DESTROYED;
    }

    // ========== 抽象方法 ==========

    /**
     * 实体初始化回调
     * 子类实现具体的初始化逻辑
     *
     * @throws Exception 初始化异常
     */
    protected abstract void onInitialize() throws Exception;

    /**
     * 实体销毁回调
     * 子类实现具体的清理逻辑
     *
     * @throws Exception 销毁异常
     */
    protected abstract void onDestroy() throws Exception;

    /**
     * 实体更新回调
     * 子类实现具体的更新逻辑
     *
     * @param deltaTime 距离上次更新的时间间隔（毫秒）
     */
    protected abstract void onUpdate(long deltaTime);

    /**
     * 位置变化回调
     * 子类实现具体的处理逻辑
     *
     * @param oldPosition 旧位置
     * @param newPosition 新位置
     */
    protected abstract void onPositionChanged(Scene.Position oldPosition, Scene.Position newPosition);

    /**
     * 移动开始回调
     * 子类实现具体的处理逻辑
     *
     * @param targetPosition 目标位置
     */
    protected abstract void onMoveStart(Scene.Position targetPosition);

    /**
     * 移动停止回调
     * 子类实现具体的处理逻辑
     */
    protected abstract void onMoveStop();

    @Override
    public String toString() {
        return String.format("SceneEntity{id=%d, name='%s', type=%s, state=%s, scene=%d}",
                entityId, entityName, entityType.getCode(), state.getCode(), sceneId);
    }
}