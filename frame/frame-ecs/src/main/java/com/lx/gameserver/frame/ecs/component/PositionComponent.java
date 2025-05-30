/*
 * 文件名: PositionComponent.java
 * 用途: 位置组件模板
 * 实现内容:
 *   - 实体位置信息管理
 *   - 2D/3D坐标支持
 *   - 朝向角度管理
 *   - 位置变更通知
 * 技术选型:
 *   - 数据导向设计
 *   - 内存布局优化
 *   - 变更检测机制
 * 依赖关系:
 *   - 继承AbstractComponent
 *   - 被移动系统、渲染系统使用
 *   - 游戏中最基础的组件之一
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.AbstractComponent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 位置组件
 * <p>
 * 存储实体的位置信息，包括坐标、旋转角度等空间数据。
 * 支持2D和3D坐标系统，提供位置变更检测功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PositionComponent extends AbstractComponent {
    
    /**
     * 组件类型ID
     */
    public static final int TYPE_ID = generateTypeId();
    
    /**
     * X坐标
     */
    private float x;
    
    /**
     * Y坐标
     */
    private float y;
    
    /**
     * Z坐标（3D场景使用）
     */
    private float z;
    
    /**
     * 旋转角度（弧度）
     */
    private float rotation;
    
    /**
     * 缩放比例X
     */
    private float scaleX = 1.0f;
    
    /**
     * 缩放比例Y
     */
    private float scaleY = 1.0f;
    
    /**
     * 缩放比例Z
     */
    private float scaleZ = 1.0f;
    
    /**
     * 上一帧的X坐标（用于变更检测）
     */
    private transient float lastX;
    
    /**
     * 上一帧的Y坐标（用于变更检测）
     */
    private transient float lastY;
    
    /**
     * 上一帧的Z坐标（用于变更检测）
     */
    private transient float lastZ;
    
    /**
     * 默认构造函数
     */
    public PositionComponent() {
        this(0, 0, 0);
    }
    
    /**
     * 2D构造函数
     *
     * @param x X坐标
     * @param y Y坐标
     */
    public PositionComponent(float x, float y) {
        this(x, y, 0);
    }
    
    /**
     * 3D构造函数
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    public PositionComponent(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }
    
    @Override
    public int getTypeId() {
        return TYPE_ID;
    }
    
    @Override
    public void reset() {
        super.reset();
        x = 0;
        y = 0;
        z = 0;
        rotation = 0;
        scaleX = 1.0f;
        scaleY = 1.0f;
        scaleZ = 1.0f;
        lastX = 0;
        lastY = 0;
        lastZ = 0;
    }
    
    /**
     * 设置位置
     *
     * @param x X坐标
     * @param y Y坐标
     */
    public void setPosition(float x, float y) {
        setPosition(x, y, this.z);
    }
    
    /**
     * 设置位置
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    public void setPosition(float x, float y, float z) {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.x = x;
        this.y = y;
        this.z = z;
        notifyModified();
    }
    
    /**
     * 移动位置
     *
     * @param deltaX X偏移
     * @param deltaY Y偏移
     */
    public void move(float deltaX, float deltaY) {
        move(deltaX, deltaY, 0);
    }
    
    /**
     * 移动位置
     *
     * @param deltaX X偏移
     * @param deltaY Y偏移
     * @param deltaZ Z偏移
     */
    public void move(float deltaX, float deltaY, float deltaZ) {
        setPosition(x + deltaX, y + deltaY, z + deltaZ);
    }
    
    /**
     * 设置旋转角度
     *
     * @param rotation 旋转角度（弧度）
     */
    public void setRotation(float rotation) {
        this.rotation = rotation;
        notifyModified();
    }
    
    /**
     * 旋转
     *
     * @param deltaRotation 旋转增量（弧度）
     */
    public void rotate(float deltaRotation) {
        setRotation(rotation + deltaRotation);
    }
    
    /**
     * 设置缩放
     *
     * @param scale 统一缩放比例
     */
    public void setScale(float scale) {
        setScale(scale, scale, scale);
    }
    
    /**
     * 设置缩放
     *
     * @param scaleX X轴缩放
     * @param scaleY Y轴缩放
     */
    public void setScale(float scaleX, float scaleY) {
        setScale(scaleX, scaleY, this.scaleZ);
    }
    
    /**
     * 设置缩放
     *
     * @param scaleX X轴缩放
     * @param scaleY Y轴缩放
     * @param scaleZ Z轴缩放
     */
    public void setScale(float scaleX, float scaleY, float scaleZ) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        notifyModified();
    }
    
    /**
     * 计算与另一个位置的距离（2D）
     *
     * @param other 另一个位置组件
     * @return 距离
     */
    public float distanceTo(PositionComponent other) {
        float dx = x - other.x;
        float dy = y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 计算与另一个位置的距离（3D）
     *
     * @param other 另一个位置组件
     * @return 距离
     */
    public float distance3DTo(PositionComponent other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dz = z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * 计算与指定坐标的距离（2D）
     *
     * @param x 目标X坐标
     * @param y 目标Y坐标
     * @return 距离
     */
    public float distanceTo(float x, float y) {
        float dx = this.x - x;
        float dy = this.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 检查位置是否发生变更
     *
     * @return 如果位置发生变更返回true
     */
    public boolean hasPositionChanged() {
        return Float.compare(x, lastX) != 0 || 
               Float.compare(y, lastY) != 0 || 
               Float.compare(z, lastZ) != 0;
    }
    
    /**
     * 更新上一帧位置（用于变更检测）
     */
    public void updateLastPosition() {
        lastX = x;
        lastY = y;
        lastZ = z;
    }
    
    /**
     * 获取位置变化量
     *
     * @return 位置变化的距离
     */
    public float getPositionDelta() {
        float dx = x - lastX;
        float dy = y - lastY;
        float dz = z - lastZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    @Override
    public int getSize() {
        return Float.BYTES * 10; // x, y, z, rotation, scaleX, scaleY, scaleZ, lastX, lastY, lastZ
    }
    
    @Override
    public String toString() {
        return String.format("Position{x=%.2f, y=%.2f, z=%.2f, rotation=%.2f, scale=(%.2f,%.2f,%.2f)}", 
            x, y, z, rotation, scaleX, scaleY, scaleZ);
    }
}