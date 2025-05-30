/*
 * 文件名: MovementSystem.java
 * 用途: 移动系统实现
 * 实现内容:
 *   - 处理实体的位置更新
 *   - 根据速度组件更新位置组件
 *   - 支持物理模拟和约束
 *   - 边界检查和碰撞检测
 * 技术选型:
 *   - 继承IteratingSystem实现实体遍历
 *   - 组件映射器提高访问性能
 *   - 向量数学计算优化
 * 依赖关系:
 *   - 依赖PositionComponent和VelocityComponent
 *   - 被场景系统、物理系统使用
 *   - 游戏中最常用的系统之一
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.component.ComponentMapper;
import com.lx.gameserver.frame.ecs.component.PositionComponent;
import com.lx.gameserver.frame.ecs.component.VelocityComponent;
import com.lx.gameserver.frame.ecs.core.AbstractSystem;
import com.lx.gameserver.frame.ecs.core.Entity;

/**
 * 移动系统
 * <p>
 * 负责根据实体的速度组件更新位置组件。
 * 支持物理模拟、边界约束、碰撞检测等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class MovementSystem extends IteratingSystem {
    
    /**
     * 位置组件映射器
     */
    private ComponentMapper<PositionComponent> positionMapper;
    
    /**
     * 速度组件映射器
     */
    private ComponentMapper<VelocityComponent> velocityMapper;
    
    /**
     * 世界边界
     */
    private WorldBounds worldBounds;
    
    /**
     * 是否启用边界检查
     */
    private boolean enableBoundaryCheck = true;
    
    /**
     * 是否启用物理模拟
     */
    private boolean enablePhysics = false;
    
    /**
     * 重力加速度
     */
    private float gravity = -9.8f;
    
    /**
     * 默认构造函数
     */
    public MovementSystem() {
        this(AbstractSystem.Priority.NORMAL);
    }
    
    /**
     * 构造函数
     *
     * @param priority 系统优先级
     */
    public MovementSystem(int priority) {
        super("MovementSystem", priority, PositionComponent.class, VelocityComponent.class);
        this.worldBounds = new WorldBounds(-1000, -1000, 1000, 1000);
    }
    
    @Override
    protected void onSystemInitialize() {
        // 初始化组件映射器
        this.positionMapper = new ComponentMapper<>(PositionComponent.class, world, true);
        this.velocityMapper = new ComponentMapper<>(VelocityComponent.class, world, true);
    }
    
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // 获取位置和速度组件
        PositionComponent position = positionMapper.get(entity);
        VelocityComponent velocity = velocityMapper.get(entity);
        
        if (position == null || velocity == null) {
            return;
        }
        
        // 如果速度为0，跳过处理
        if (velocity.isStationary()) {
            return;
        }
        
        // 更新速度（应用加速度和物理效果）
        if (enablePhysics) {
            updateVelocity(velocity, deltaTime);
        }
        
        // 更新位置
        updatePosition(position, velocity, deltaTime);
        
        // 边界检查
        if (enableBoundaryCheck) {
            checkBoundaries(position, velocity);
        }
        
        // 更新上一帧位置（用于变更检测）
        position.updateLastPosition();
    }
    
    /**
     * 更新速度
     *
     * @param velocity 速度组件
     * @param deltaTime 时间增量
     */
    private void updateVelocity(VelocityComponent velocity, float deltaTime) {
        // 应用重力
        if (velocity.isGravityAffected()) {
            velocity.setAcceleration(velocity.getAccelerationX(), 
                                   velocity.getAccelerationY() + gravity, 
                                   velocity.getAccelerationZ());
        }
        
        // 更新速度（基于加速度）
        velocity.updateVelocity(deltaTime);
    }
    
    /**
     * 更新位置
     *
     * @param position 位置组件
     * @param velocity 速度组件
     * @param deltaTime 时间增量
     */
    private void updatePosition(PositionComponent position, VelocityComponent velocity, float deltaTime) {
        // 计算位移
        float deltaX = velocity.getVelocityX() * deltaTime;
        float deltaY = velocity.getVelocityY() * deltaTime;
        float deltaZ = velocity.getVelocityZ() * deltaTime;
        
        // 更新位置
        position.move(deltaX, deltaY, deltaZ);
        
        // 更新旋转
        if (velocity.getAngularVelocity() != 0) {
            float deltaRotation = velocity.getAngularVelocity() * deltaTime;
            position.rotate(deltaRotation);
        }
    }
    
    /**
     * 检查边界约束
     *
     * @param position 位置组件
     * @param velocity 速度组件
     */
    private void checkBoundaries(PositionComponent position, VelocityComponent velocity) {
        boolean hitBoundary = false;
        
        // 检查X轴边界
        if (position.getX() < worldBounds.minX) {
            position.setX(worldBounds.minX);
            velocity.setVelocityX(0);
            hitBoundary = true;
        } else if (position.getX() > worldBounds.maxX) {
            position.setX(worldBounds.maxX);
            velocity.setVelocityX(0);
            hitBoundary = true;
        }
        
        // 检查Y轴边界
        if (position.getY() < worldBounds.minY) {
            position.setY(worldBounds.minY);
            velocity.setVelocityY(0);
            hitBoundary = true;
        } else if (position.getY() > worldBounds.maxY) {
            position.setY(worldBounds.maxY);
            velocity.setVelocityY(0);
            hitBoundary = true;
        }
        
        // 如果碰到边界，可以触发事件
        if (hitBoundary) {
            onBoundaryHit(position, velocity);
        }
    }
    
    /**
     * 边界碰撞回调
     *
     * @param position 位置组件
     * @param velocity 速度组件
     */
    protected void onBoundaryHit(PositionComponent position, VelocityComponent velocity) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 设置世界边界
     *
     * @param minX 最小X坐标
     * @param minY 最小Y坐标
     * @param maxX 最大X坐标
     * @param maxY 最大Y坐标
     */
    public void setWorldBounds(float minX, float minY, float maxX, float maxY) {
        this.worldBounds = new WorldBounds(minX, minY, maxX, maxY);
    }
    
    /**
     * 获取世界边界
     *
     * @return 世界边界
     */
    public WorldBounds getWorldBounds() {
        return worldBounds;
    }
    
    /**
     * 设置是否启用边界检查
     *
     * @param enableBoundaryCheck 是否启用
     */
    public void setEnableBoundaryCheck(boolean enableBoundaryCheck) {
        this.enableBoundaryCheck = enableBoundaryCheck;
    }
    
    /**
     * 检查是否启用边界检查
     *
     * @return 如果启用返回true
     */
    public boolean isEnableBoundaryCheck() {
        return enableBoundaryCheck;
    }
    
    /**
     * 设置是否启用物理模拟
     *
     * @param enablePhysics 是否启用
     */
    public void setEnablePhysics(boolean enablePhysics) {
        this.enablePhysics = enablePhysics;
    }
    
    /**
     * 检查是否启用物理模拟
     *
     * @return 如果启用返回true
     */
    public boolean isEnablePhysics() {
        return enablePhysics;
    }
    
    /**
     * 设置重力加速度
     *
     * @param gravity 重力加速度
     */
    public void setGravity(float gravity) {
        this.gravity = gravity;
    }
    
    /**
     * 获取重力加速度
     *
     * @return 重力加速度
     */
    public float getGravity() {
        return gravity;
    }
    
    /**
     * 世界边界类
     */
    public static class WorldBounds {
        public final float minX, minY, maxX, maxY;
        
        public WorldBounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
        
        /**
         * 检查点是否在边界内
         *
         * @param x X坐标
         * @param y Y坐标
         * @return 如果在边界内返回true
         */
        public boolean contains(float x, float y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
        
        /**
         * 获取边界宽度
         *
         * @return 宽度
         */
        public float getWidth() {
            return maxX - minX;
        }
        
        /**
         * 获取边界高度
         *
         * @return 高度
         */
        public float getHeight() {
            return maxY - minY;
        }
        
        /**
         * 获取中心X坐标
         *
         * @return 中心X坐标
         */
        public float getCenterX() {
            return (minX + maxX) / 2;
        }
        
        /**
         * 获取中心Y坐标
         *
         * @return 中心Y坐标
         */
        public float getCenterY() {
            return (minY + maxY) / 2;
        }
        
        @Override
        public String toString() {
            return String.format("WorldBounds{min=(%.1f,%.1f), max=(%.1f,%.1f), size=(%.1f,%.1f)}", 
                minX, minY, maxX, maxY, getWidth(), getHeight());
        }
    }
}