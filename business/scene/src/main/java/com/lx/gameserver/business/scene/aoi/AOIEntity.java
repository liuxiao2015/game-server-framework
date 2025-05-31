/*
 * 文件名: AOIEntity.java
 * 用途: AOI实体接口定义
 * 实现内容:
 *   - AOI实体的基础行为定义
 *   - 获取位置和视野范围接口
 *   - 观察者和被观察者列表管理
 *   - 视野事件通知机制
 *   - AOI状态管理和查询
 * 技术选型:
 *   - 接口设计保证AOI系统的灵活性
 *   - 观察者模式实现视野事件通知
 *   - 泛型设计支持不同类型的实体
 * 依赖关系:
 *   - 被AOIManager使用进行视野管理
 *   - 被具体实体类实现AOI功能
 *   - 与Scene坐标系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.aoi;

import com.lx.gameserver.business.scene.core.Scene;

import java.util.Set;

/**
 * AOI实体接口
 * <p>
 * 定义AOI系统中实体的基础行为，包括位置获取、
 * 视野范围管理、观察者列表维护等功能。
 * 所有参与AOI的实体都需要实现此接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface AOIEntity {

    /**
     * AOI事件类型枚举
     */
    enum AOIEventType {
        /** 进入视野 */
        ENTER_AOI,
        /** 离开视野 */
        LEAVE_AOI,
        /** 位置更新 */
        POSITION_UPDATE,
        /** 视野范围变化 */
        VIEW_RANGE_CHANGE
    }

    /**
     * AOI事件
     */
    interface AOIEvent {
        /**
         * 获取事件类型
         *
         * @return 事件类型
         */
        AOIEventType getEventType();

        /**
         * 获取事件源实体
         *
         * @return 事件源实体
         */
        AOIEntity getSourceEntity();

        /**
         * 获取事件目标实体
         *
         * @return 事件目标实体
         */
        AOIEntity getTargetEntity();

        /**
         * 获取事件时间戳
         *
         * @return 时间戳
         */
        long getTimestamp();

        /**
         * 获取事件数据
         *
         * @return 事件数据
         */
        Object getEventData();
    }

    /**
     * AOI事件监听器
     */
    interface AOIEventListener {
        /**
         * 处理AOI事件
         *
         * @param event AOI事件
         */
        void onAOIEvent(AOIEvent event);
    }

    // ========== 基础属性获取 ==========

    /**
     * 获取实体ID
     *
     * @return 实体ID
     */
    Long getEntityId();

    /**
     * 获取实体类型
     *
     * @return 实体类型
     */
    String getEntityType();

    /**
     * 获取当前位置
     *
     * @return 当前位置
     */
    Scene.Position getPosition();

    /**
     * 获取视野范围
     *
     * @return 视野范围
     */
    double getViewRange();

    /**
     * 获取所在场景ID
     *
     * @return 场景ID
     */
    Long getSceneId();

    // ========== AOI状态管理 ==========

    /**
     * 是否可见
     *
     * @return 是否可见
     */
    boolean isVisible();

    /**
     * 是否对其他实体可见
     *
     * @return 是否对其他实体可见
     */
    boolean isVisibleToOthers();

    /**
     * 是否需要接收AOI事件
     *
     * @return 是否需要接收AOI事件
     */
    boolean needsAOIEvents();

    /**
     * 是否处于AOI活跃状态
     *
     * @return 是否处于AOI活跃状态
     */
    boolean isAOIActive();

    // ========== 观察者管理 ==========

    /**
     * 获取观察者列表（能看到当前实体的实体）
     *
     * @return 观察者实体ID集合
     */
    Set<Long> getObservers();

    /**
     * 获取被观察者列表（当前实体能看到的实体）
     *
     * @return 被观察者实体ID集合
     */
    Set<Long> getObservees();

    /**
     * 添加观察者
     *
     * @param observerId 观察者实体ID
     * @return 是否添加成功
     */
    boolean addObserver(Long observerId);

    /**
     * 移除观察者
     *
     * @param observerId 观察者实体ID
     * @return 是否移除成功
     */
    boolean removeObserver(Long observerId);

    /**
     * 添加被观察者
     *
     * @param observeeId 被观察者实体ID
     * @return 是否添加成功
     */
    boolean addObservee(Long observeeId);

    /**
     * 移除被观察者
     *
     * @param observeeId 被观察者实体ID
     * @return 是否移除成功
     */
    boolean removeObservee(Long observeeId);

    /**
     * 清空所有观察者
     */
    void clearObservers();

    /**
     * 清空所有被观察者
     */
    void clearObservees();

    // ========== 视野检查 ==========

    /**
     * 检查是否在视野范围内
     *
     * @param other 其他实体
     * @return 是否在视野范围内
     */
    boolean isInViewRange(AOIEntity other);

    /**
     * 检查是否能看到其他实体
     *
     * @param other 其他实体
     * @return 是否能看到
     */
    boolean canSee(AOIEntity other);

    /**
     * 检查是否能被其他实体看到
     *
     * @param other 其他实体
     * @return 是否能被看到
     */
    boolean canBeSeenBy(AOIEntity other);

    /**
     * 计算与其他实体的距离
     *
     * @param other 其他实体
     * @return 距离值
     */
    double distanceTo(AOIEntity other);

    // ========== 事件处理 ==========

    /**
     * 添加AOI事件监听器
     *
     * @param listener 事件监听器
     */
    void addAOIEventListener(AOIEventListener listener);

    /**
     * 移除AOI事件监听器
     *
     * @param listener 事件监听器
     */
    void removeAOIEventListener(AOIEventListener listener);

    /**
     * 处理AOI事件
     *
     * @param event AOI事件
     */
    void handleAOIEvent(AOIEvent event);

    /**
     * 触发AOI事件
     *
     * @param eventType 事件类型
     * @param targetEntity 目标实体
     * @param eventData 事件数据
     */
    void triggerAOIEvent(AOIEventType eventType, AOIEntity targetEntity, Object eventData);

    // ========== 位置更新 ==========

    /**
     * 位置更新通知
     * AOI系统调用此方法通知实体位置已更新
     *
     * @param oldPosition 旧位置
     * @param newPosition 新位置
     */
    void onPositionUpdated(Scene.Position oldPosition, Scene.Position newPosition);

    /**
     * 视野范围更新通知
     * AOI系统调用此方法通知实体视野范围已更新
     *
     * @param oldRange 旧视野范围
     * @param newRange 新视野范围
     */
    void onViewRangeUpdated(double oldRange, double newRange);

    // ========== AOI数据 ==========

    /**
     * 获取AOI网格坐标
     *
     * @param gridSize 网格大小
     * @return 网格坐标
     */
    default AOIGrid.GridCoordinate getGridCoordinate(float gridSize) {
        Scene.Position pos = getPosition();
        if (pos == null) {
            return new AOIGrid.GridCoordinate(0, 0);
        }
        int gridX = (int) Math.floor(pos.getX() / gridSize);
        int gridZ = (int) Math.floor(pos.getZ() / gridSize);
        return new AOIGrid.GridCoordinate(gridX, gridZ);
    }

    /**
     * 获取AOI扩展数据
     *
     * @param key 数据键
     * @return 数据值
     */
    Object getAOIData(String key);

    /**
     * 设置AOI扩展数据
     *
     * @param key 数据键
     * @param value 数据值
     */
    void setAOIData(String key, Object value);

    /**
     * 获取AOI优先级
     * 用于在AOI计算中确定处理优先级
     *
     * @return 优先级值，数值越大优先级越高
     */
    default int getAOIPriority() {
        return 0;
    }

    /**
     * 是否需要精确AOI计算
     * 某些重要实体（如玩家）可能需要更精确的AOI计算
     *
     * @return 是否需要精确计算
     */
    default boolean needsPreciseAOI() {
        return false;
    }

    /**
     * 获取AOI更新间隔
     * 不同实体可能有不同的AOI更新频率
     *
     * @return 更新间隔（毫秒）
     */
    default long getAOIUpdateInterval() {
        return 200; // 默认200ms
    }

    // ========== 默认实现 ==========

    /**
     * 默认的视野检查实现
     */
    default boolean defaultCanSee(AOIEntity other) {
        if (other == null || !other.isVisible() || !this.isAOIActive()) {
            return false;
        }
        
        if (!other.getSceneId().equals(this.getSceneId())) {
            return false;
        }
        
        double distance = this.distanceTo(other);
        return distance <= this.getViewRange();
    }

    /**
     * 默认的距离计算实现
     */
    default double defaultDistanceTo(AOIEntity other) {
        if (other == null) {
            return Double.MAX_VALUE;
        }
        
        Scene.Position myPos = this.getPosition();
        Scene.Position otherPos = other.getPosition();
        
        if (myPos == null || otherPos == null) {
            return Double.MAX_VALUE;
        }
        
        return myPos.distanceTo(otherPos);
    }
}