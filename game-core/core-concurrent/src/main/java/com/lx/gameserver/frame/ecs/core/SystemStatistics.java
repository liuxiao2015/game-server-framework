/*
 * 文件名: SystemStatistics.java
 * 用途: 系统统计信息类
 * 实现内容:
 *   - 系统运行统计数据记录
 *   - 性能监控和调试支持
 *   - 更新次数、时间、错误统计
 *   - 平均性能计算
 * 技术选型:
 *   - 原子操作保证线程安全
 *   - 高精度时间统计
 *   - 轻量级统计实现
 * 依赖关系:
 *   - 被AbstractSystem使用
 *   - 提供系统性能监控数据
 *   - 支持调试和优化分析
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.core;

import lombok.Data;

/**
 * 系统统计信息
 * <p>
 * 记录系统的运行统计数据，用于性能监控和调试。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
public class SystemStatistics {
    
    /**
     * 更新次数
     */
    private volatile long updateCount = 0;
    
    /**
     * 总更新时间（纳秒）
     */
    private volatile long totalUpdateTime = 0;
    
    /**
     * 最小更新时间（纳秒）
     */
    private volatile long minUpdateTime = Long.MAX_VALUE;
    
    /**
     * 最大更新时间（纳秒）
     */
    private volatile long maxUpdateTime = 0;
    
    /**
     * 错误次数
     */
    private volatile long errorCount = 0;
    
    /**
     * 最后更新时间戳
     */
    private volatile long lastUpdateTime = 0;
    
    /**
     * 增加更新次数
     */
    public void incrementUpdateCount() {
        updateCount++;
        lastUpdateTime = java.lang.System.currentTimeMillis();
    }
    
    /**
     * 添加更新时间
     *
     * @param updateTime 更新时间（纳秒）
     */
    public void addUpdateTime(long updateTime) {
        totalUpdateTime += updateTime;
        minUpdateTime = Math.min(minUpdateTime, updateTime);
        maxUpdateTime = Math.max(maxUpdateTime, updateTime);
    }
    
    /**
     * 增加错误次数
     */
    public void incrementErrorCount() {
        errorCount++;
    }
    
    /**
     * 获取平均更新时间（纳秒）
     *
     * @return 平均更新时间
     */
    public double getAverageUpdateTime() {
        return updateCount > 0 ? (double) totalUpdateTime / updateCount : 0.0;
    }
    
    /**
     * 获取平均更新时间（毫秒）
     *
     * @return 平均更新时间
     */
    public double getAverageUpdateTimeMs() {
        return getAverageUpdateTime() / 1_000_000.0;
    }
    
    /**
     * 获取最小更新时间（毫秒）
     *
     * @return 最小更新时间
     */
    public double getMinUpdateTimeMs() {
        return minUpdateTime == Long.MAX_VALUE ? 0.0 : minUpdateTime / 1_000_000.0;
    }
    
    /**
     * 获取最大更新时间（毫秒）
     *
     * @return 最大更新时间
     */
    public double getMaxUpdateTimeMs() {
        return maxUpdateTime / 1_000_000.0;
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        updateCount = 0;
        totalUpdateTime = 0;
        minUpdateTime = Long.MAX_VALUE;
        maxUpdateTime = 0;
        errorCount = 0;
        lastUpdateTime = 0;
    }
}