/*
 * 文件名: VelocityComponent.java
 * 用途: 速度组件模板
 * 实现内容:
 *   - 实体速度信息管理
 *   - 线性速度和角速度
 *   - 加速度支持
 *   - 阻力系数
 * 技术选型:
 *   - 物理模拟数据结构
 *   - 向量计算优化
 *   - 数值稳定性保证
 * 依赖关系:
 *   - 继承AbstractComponent
 *   - 与PositionComponent配合使用
 *   - 被移动系统、物理系统使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.AbstractComponent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 速度组件
 * <p>
 * 存储实体的速度信息，包括线性速度、角速度和加速度。
 * 支持物理模拟计算，提供速度限制和阻力等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VelocityComponent extends AbstractComponent {
    
    /**
     * 组件类型ID
     */
    public static final int TYPE_ID = generateTypeId();
    
    /**
     * X轴线性速度（单位/秒）
     */
    private float velocityX;
    
    /**
     * Y轴线性速度（单位/秒）
     */
    private float velocityY;
    
    /**
     * Z轴线性速度（单位/秒）
     */
    private float velocityZ;
    
    /**
     * 角速度（弧度/秒）
     */
    private float angularVelocity;
    
    /**
     * X轴加速度（单位/秒²）
     */
    private float accelerationX;
    
    /**
     * Y轴加速度（单位/秒²）
     */
    private float accelerationY;
    
    /**
     * Z轴加速度（单位/秒²）
     */
    private float accelerationZ;
    
    /**
     * 角加速度（弧度/秒²）
     */
    private float angularAcceleration;
    
    /**
     * 最大速度限制
     */
    private float maxSpeed = Float.MAX_VALUE;
    
    /**
     * 最大角速度限制
     */
    private float maxAngularSpeed = Float.MAX_VALUE;
    
    /**
     * 线性阻力系数（0-1之间，1表示无阻力）
     */
    private float linearDamping = 1.0f;
    
    /**
     * 角阻力系数（0-1之间，1表示无阻力）
     */
    private float angularDamping = 1.0f;
    
    /**
     * 是否受重力影响
     */
    private boolean gravityAffected = true;
    
    /**
     * 默认构造函数
     */
    public VelocityComponent() {
        this(0, 0, 0);
    }
    
    /**
     * 2D构造函数
     *
     * @param velocityX X轴速度
     * @param velocityY Y轴速度
     */
    public VelocityComponent(float velocityX, float velocityY) {
        this(velocityX, velocityY, 0);
    }
    
    /**
     * 3D构造函数
     *
     * @param velocityX X轴速度
     * @param velocityY Y轴速度
     * @param velocityZ Z轴速度
     */
    public VelocityComponent(float velocityX, float velocityY, float velocityZ) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
    }
    
    @Override
    public int getTypeId() {
        return TYPE_ID;
    }
    
    @Override
    public void reset() {
        super.reset();
        velocityX = 0;
        velocityY = 0;
        velocityZ = 0;
        angularVelocity = 0;
        accelerationX = 0;
        accelerationY = 0;
        accelerationZ = 0;
        angularAcceleration = 0;
        maxSpeed = Float.MAX_VALUE;
        maxAngularSpeed = Float.MAX_VALUE;
        linearDamping = 1.0f;
        angularDamping = 1.0f;
        gravityAffected = true;
    }
    
    /**
     * 设置线性速度
     *
     * @param velocityX X轴速度
     * @param velocityY Y轴速度
     */
    public void setVelocity(float velocityX, float velocityY) {
        setVelocity(velocityX, velocityY, this.velocityZ);
    }
    
    /**
     * 设置线性速度
     *
     * @param velocityX X轴速度
     * @param velocityY Y轴速度
     * @param velocityZ Z轴速度
     */
    public void setVelocity(float velocityX, float velocityY, float velocityZ) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        clampVelocity();
        notifyModified();
    }
    
    /**
     * 添加线性速度
     *
     * @param deltaVelocityX X轴速度增量
     * @param deltaVelocityY Y轴速度增量
     */
    public void addVelocity(float deltaVelocityX, float deltaVelocityY) {
        addVelocity(deltaVelocityX, deltaVelocityY, 0);
    }
    
    /**
     * 添加线性速度
     *
     * @param deltaVelocityX X轴速度增量
     * @param deltaVelocityY Y轴速度增量
     * @param deltaVelocityZ Z轴速度增量
     */
    public void addVelocity(float deltaVelocityX, float deltaVelocityY, float deltaVelocityZ) {
        setVelocity(velocityX + deltaVelocityX, velocityY + deltaVelocityY, velocityZ + deltaVelocityZ);
    }
    
    /**
     * 设置加速度
     *
     * @param accelerationX X轴加速度
     * @param accelerationY Y轴加速度
     */
    public void setAcceleration(float accelerationX, float accelerationY) {
        setAcceleration(accelerationX, accelerationY, this.accelerationZ);
    }
    
    /**
     * 设置加速度
     *
     * @param accelerationX X轴加速度
     * @param accelerationY Y轴加速度
     * @param accelerationZ Z轴加速度
     */
    public void setAcceleration(float accelerationX, float accelerationY, float accelerationZ) {
        this.accelerationX = accelerationX;
        this.accelerationY = accelerationY;
        this.accelerationZ = accelerationZ;
        notifyModified();
    }
    
    /**
     * 设置角速度
     *
     * @param angularVelocity 角速度
     */
    public void setAngularVelocity(float angularVelocity) {
        this.angularVelocity = Math.max(-maxAngularSpeed, Math.min(maxAngularSpeed, angularVelocity));
        notifyModified();
    }
    
    /**
     * 添加角速度
     *
     * @param deltaAngularVelocity 角速度增量
     */
    public void addAngularVelocity(float deltaAngularVelocity) {
        setAngularVelocity(angularVelocity + deltaAngularVelocity);
    }
    
    /**
     * 设置角加速度
     *
     * @param angularAcceleration 角加速度
     */
    public void setAngularAcceleration(float angularAcceleration) {
        this.angularAcceleration = angularAcceleration;
        notifyModified();
    }
    
    /**
     * 获取速度大小
     *
     * @return 速度大小
     */
    public float getSpeed() {
        return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    }
    
    /**
     * 获取2D速度大小
     *
     * @return 2D速度大小
     */
    public float getSpeed2D() {
        return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
    }
    
    /**
     * 获取速度方向（2D）
     *
     * @return 速度方向角度（弧度）
     */
    public float getDirection() {
        return (float) Math.atan2(velocityY, velocityX);
    }
    
    /**
     * 设置速度方向和大小（2D）
     *
     * @param speed 速度大小
     * @param direction 方向角度（弧度）
     */
    public void setSpeedAndDirection(float speed, float direction) {
        velocityX = speed * (float) Math.cos(direction);
        velocityY = speed * (float) Math.sin(direction);
        clampVelocity();
        notifyModified();
    }
    
    /**
     * 应用冲量
     *
     * @param impulseX X轴冲量
     * @param impulseY Y轴冲量
     */
    public void applyImpulse(float impulseX, float impulseY) {
        applyImpulse(impulseX, impulseY, 0);
    }
    
    /**
     * 应用冲量
     *
     * @param impulseX X轴冲量
     * @param impulseY Y轴冲量
     * @param impulseZ Z轴冲量
     */
    public void applyImpulse(float impulseX, float impulseY, float impulseZ) {
        addVelocity(impulseX, impulseY, impulseZ);
    }
    
    /**
     * 应用力（需要质量参数）
     *
     * @param forceX X轴力
     * @param forceY Y轴力
     * @param mass 质量
     * @param deltaTime 时间增量
     */
    public void applyForce(float forceX, float forceY, float mass, float deltaTime) {
        applyForce(forceX, forceY, 0, mass, deltaTime);
    }
    
    /**
     * 应用力（需要质量参数）
     *
     * @param forceX X轴力
     * @param forceY Y轴力
     * @param forceZ Z轴力
     * @param mass 质量
     * @param deltaTime 时间增量
     */
    public void applyForce(float forceX, float forceY, float forceZ, float mass, float deltaTime) {
        if (mass > 0) {
            float impulseX = (forceX / mass) * deltaTime;
            float impulseY = (forceY / mass) * deltaTime;
            float impulseZ = (forceZ / mass) * deltaTime;
            applyImpulse(impulseX, impulseY, impulseZ);
        }
    }
    
    /**
     * 更新速度（应用加速度）
     *
     * @param deltaTime 时间增量
     */
    public void updateVelocity(float deltaTime) {
        velocityX += accelerationX * deltaTime;
        velocityY += accelerationY * deltaTime;
        velocityZ += accelerationZ * deltaTime;
        angularVelocity += angularAcceleration * deltaTime;
        
        // 应用阻力
        velocityX *= linearDamping;
        velocityY *= linearDamping;
        velocityZ *= linearDamping;
        angularVelocity *= angularDamping;
        
        clampVelocity();
        clampAngularVelocity();
        notifyModified();
    }
    
    /**
     * 停止移动
     */
    public void stop() {
        velocityX = 0;
        velocityY = 0;
        velocityZ = 0;
        angularVelocity = 0;
        notifyModified();
    }
    
    /**
     * 停止线性移动
     */
    public void stopLinear() {
        velocityX = 0;
        velocityY = 0;
        velocityZ = 0;
        notifyModified();
    }
    
    /**
     * 停止角度移动
     */
    public void stopAngular() {
        angularVelocity = 0;
        notifyModified();
    }
    
    /**
     * 限制速度在最大值范围内
     */
    private void clampVelocity() {
        float speed = getSpeed();
        if (speed > maxSpeed) {
            float factor = maxSpeed / speed;
            velocityX *= factor;
            velocityY *= factor;
            velocityZ *= factor;
        }
    }
    
    /**
     * 限制角速度在最大值范围内
     */
    private void clampAngularVelocity() {
        angularVelocity = Math.max(-maxAngularSpeed, Math.min(maxAngularSpeed, angularVelocity));
    }
    
    /**
     * 检查是否静止
     *
     * @param threshold 阈值
     * @return 如果速度小于阈值返回true
     */
    public boolean isStationary(float threshold) {
        return getSpeed() < threshold && Math.abs(angularVelocity) < threshold;
    }
    
    /**
     * 检查是否静止（使用默认阈值）
     *
     * @return 如果静止返回true
     */
    public boolean isStationary() {
        return isStationary(0.01f);
    }
    
    @Override
    public int getSize() {
        return Float.BYTES * 13; // 所有float字段 + boolean字段
    }
    
    @Override
    public String toString() {
        return String.format("Velocity{linear=(%.2f,%.2f,%.2f), angular=%.2f, speed=%.2f}", 
            velocityX, velocityY, velocityZ, angularVelocity, getSpeed());
    }
}