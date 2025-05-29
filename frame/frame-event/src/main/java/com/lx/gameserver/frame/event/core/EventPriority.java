/*
 * 文件名: EventPriority.java
 * 用途: 事件优先级枚举
 * 实现内容:
 *   - 定义事件处理的优先级
 *   - 支持不同优先级事件的差异化处理
 *   - 提供优先级值比较
 * 技术选型:
 *   - 枚举类型设计
 *   - 优先级值排序
 * 依赖关系:
 *   - 被GameEvent使用
 *   - 被EventBusManager用于事件总线选择
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.core;

/**
 * 事件优先级枚举
 * <p>
 * 定义事件处理的优先级等级，用于支持不同优先级事件的差异化处理。
 * 优先级值越小，优先级越高。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public enum EventPriority {
    /** 最高优先级 - 系统关键事件 */
    HIGHEST(0, "最高优先级"),
    
    /** 高优先级 - 战斗事件 */
    HIGH(1, "高优先级"),
    
    /** 普通优先级 - 一般游戏逻辑 */
    NORMAL(2, "普通优先级"),
    
    /** 低优先级 - 日志统计事件 */
    LOW(3, "低优先级"),
    
    /** 最低优先级 - 后台任务 */
    LOWEST(4, "最低优先级");
    
    /** 优先级值，数值越小优先级越高 */
    private final int value;
    
    /** 优先级描述 */
    private final String description;
    
    /**
     * 构造函数
     *
     * @param value       优先级值
     * @param description 优先级描述
     */
    EventPriority(int value, String description) {
        this.value = value;
        this.description = description;
    }
    
    /**
     * 获取优先级值
     *
     * @return 优先级值
     */
    public int getValue() {
        return value;
    }
    
    /**
     * 获取优先级描述
     *
     * @return 优先级描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 比较优先级
     *
     * @param other 其他优先级
     * @return 比较结果，负数表示当前优先级更高
     */
    public int compareValue(EventPriority other) {
        return Integer.compare(this.value, other.value);
    }
    
    /**
     * 判断是否是高优先级（HIGH及以上）
     *
     * @return 如果是高优先级返回true
     */
    public boolean isHighPriority() {
        return this.value <= HIGH.value;
    }
    
    /**
     * 判断是否是低优先级（LOW及以下）
     *
     * @return 如果是低优先级返回true
     */
    public boolean isLowPriority() {
        return this.value >= LOW.value;
    }
}