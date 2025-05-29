/*
 * 文件名: WarmupResult.java
 * 用途: 预热结果
 * 实现内容:
 *   - 预热执行结果封装
 *   - 成功/失败状态记录
 *   - 执行统计信息
 *   - 错误信息记录
 *   - 性能指标收集
 * 技术选型:
 *   - 不可变结果对象
 *   - 详细的执行信息
 *   - 时间Duration类型
 *   - 结果状态枚举
 * 依赖关系:
 *   - 被CacheWarmer返回
 *   - 提供预热执行反馈
 *   - 支持监控和统计
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.warmup;

import java.time.Duration;
import java.util.Objects;

/**
 * 预热结果
 * <p>
 * 封装缓存预热的执行结果，包括成功状态、执行统计、
 * 错误信息等，便于监控和调试。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class WarmupResult {

    /**
     * 是否成功
     */
    private final boolean success;

    /**
     * 结果消息
     */
    private final String message;

    /**
     * 成功任务数
     */
    private final int successCount;

    /**
     * 失败任务数
     */
    private final int failureCount;

    /**
     * 执行耗时
     */
    private final Duration elapsed;

    /**
     * 构造函数
     *
     * @param success      是否成功
     * @param message      结果消息
     * @param successCount 成功任务数
     * @param failureCount 失败任务数
     * @param elapsed      执行耗时
     */
    public WarmupResult(boolean success, String message, int successCount, int failureCount, Duration elapsed) {
        this.success = success;
        this.message = message;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.elapsed = elapsed;
    }

    /**
     * 是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取结果消息
     *
     * @return 结果消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取成功任务数
     *
     * @return 成功任务数
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * 获取失败任务数
     *
     * @return 失败任务数
     */
    public int getFailureCount() {
        return failureCount;
    }

    /**
     * 获取总任务数
     *
     * @return 总任务数
     */
    public int getTotalCount() {
        return successCount + failureCount;
    }

    /**
     * 获取成功率
     *
     * @return 成功率（0.0-1.0）
     */
    public double getSuccessRate() {
        int total = getTotalCount();
        return total > 0 ? (double) successCount / total : 0.0;
    }

    /**
     * 获取执行耗时
     *
     * @return 执行耗时
     */
    public Duration getElapsed() {
        return elapsed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        WarmupResult that = (WarmupResult) obj;
        return success == that.success &&
               successCount == that.successCount &&
               failureCount == that.failureCount &&
               Objects.equals(message, that.message) &&
               Objects.equals(elapsed, that.elapsed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, successCount, failureCount, elapsed);
    }

    @Override
    public String toString() {
        return "WarmupResult{" +
               "success=" + success +
               ", message='" + message + '\'' +
               ", successCount=" + successCount +
               ", failureCount=" + failureCount +
               ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
               ", elapsed=" + elapsed.toMillis() + "ms" +
               '}';
    }
}

/**
 * 预热进度
 * <p>
 * 表示预热过程中的进度信息，用于实时监控预热状态。
 * </p>
 */
class WarmupProgress {

    /**
     * 当前任务名称
     */
    private final String currentTask;

    /**
     * 已完成数量
     */
    private final int completed;

    /**
     * 总数量
     */
    private final int total;

    /**
     * 当前任务是否成功
     */
    private final boolean success;

    /**
     * 错误信息
     */
    private final String error;

    /**
     * 构造函数
     *
     * @param currentTask 当前任务名称
     * @param completed   已完成数量
     * @param total       总数量
     * @param success     是否成功
     * @param error       错误信息
     */
    public WarmupProgress(String currentTask, int completed, int total, boolean success, String error) {
        this.currentTask = currentTask;
        this.completed = completed;
        this.total = total;
        this.success = success;
        this.error = error;
    }

    /**
     * 获取当前任务名称
     *
     * @return 当前任务名称
     */
    public String getCurrentTask() {
        return currentTask;
    }

    /**
     * 获取已完成数量
     *
     * @return 已完成数量
     */
    public int getCompleted() {
        return completed;
    }

    /**
     * 获取总数量
     *
     * @return 总数量
     */
    public int getTotal() {
        return total;
    }

    /**
     * 获取进度百分比
     *
     * @return 进度百分比（0.0-1.0）
     */
    public double getProgress() {
        return total > 0 ? (double) completed / total : 0.0;
    }

    /**
     * 当前任务是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    public String getError() {
        return error;
    }

    /**
     * 是否已完成
     *
     * @return 是否已完成
     */
    public boolean isCompleted() {
        return completed >= total;
    }

    @Override
    public String toString() {
        return "WarmupProgress{" +
               "currentTask='" + currentTask + '\'' +
               ", progress=" + String.format("%.1f%%", getProgress() * 100) +
               " (" + completed + "/" + total + ")" +
               ", success=" + success +
               (error != null ? ", error='" + error + '\'' : "") +
               '}';
    }
}

/**
 * 预热统计
 * <p>
 * 收集和管理预热操作的统计信息，用于性能监控和优化。
 * </p>
 */
class WarmupStatistics {

    /**
     * 总预热次数
     */
    private volatile long totalWarmups = 0;

    /**
     * 成功预热次数
     */
    private volatile long successfulWarmups = 0;

    /**
     * 失败预热次数
     */
    private volatile long failedWarmups = 0;

    /**
     * 总预热时间（毫秒）
     */
    private volatile long totalElapsedMillis = 0;

    /**
     * 最后预热时间
     */
    private volatile java.time.Instant lastWarmupTime;

    /**
     * 记录预热结果
     *
     * @param result 预热结果
     */
    public synchronized void recordWarmup(WarmupResult result) {
        totalWarmups++;
        if (result.isSuccess()) {
            successfulWarmups++;
        } else {
            failedWarmups++;
        }
        totalElapsedMillis += result.getElapsed().toMillis();
        lastWarmupTime = java.time.Instant.now();
    }

    /**
     * 获取总预热次数
     *
     * @return 总预热次数
     */
    public long getTotalWarmups() {
        return totalWarmups;
    }

    /**
     * 获取成功预热次数
     *
     * @return 成功预热次数
     */
    public long getSuccessfulWarmups() {
        return successfulWarmups;
    }

    /**
     * 获取失败预热次数
     *
     * @return 失败预热次数
     */
    public long getFailedWarmups() {
        return failedWarmups;
    }

    /**
     * 获取成功率
     *
     * @return 成功率（0.0-1.0）
     */
    public double getSuccessRate() {
        return totalWarmups > 0 ? (double) successfulWarmups / totalWarmups : 0.0;
    }

    /**
     * 获取平均预热时间
     *
     * @return 平均预热时间（毫秒）
     */
    public double getAverageElapsedMillis() {
        return totalWarmups > 0 ? (double) totalElapsedMillis / totalWarmups : 0.0;
    }

    /**
     * 获取最后预热时间
     *
     * @return 最后预热时间
     */
    public java.time.Instant getLastWarmupTime() {
        return lastWarmupTime;
    }

    /**
     * 重置统计信息
     */
    public synchronized void reset() {
        totalWarmups = 0;
        successfulWarmups = 0;
        failedWarmups = 0;
        totalElapsedMillis = 0;
        lastWarmupTime = null;
    }

    @Override
    public String toString() {
        return "WarmupStatistics{" +
               "totalWarmups=" + totalWarmups +
               ", successfulWarmups=" + successfulWarmups +
               ", failedWarmups=" + failedWarmups +
               ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
               ", averageElapsed=" + String.format("%.1fms", getAverageElapsedMillis()) +
               ", lastWarmupTime=" + lastWarmupTime +
               '}';
    }
}