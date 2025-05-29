/*
 * 文件名: ConcurrentMetricsMXBean.java
 * 用途: 并发指标JMX接口
 * 实现内容:
 *   - 定义JMX监控接口
 *   - 暴露并发指标给JMX管理工具
 *   - 提供远程监控能力
 * 技术选型:
 *   - JMX MXBean标准接口
 *   - 管理和监控API
 * 依赖关系:
 *   - 被ConcurrentMetrics实现
 *   - 与JMX管理工具集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.metrics;

import java.util.Map;

/**
 * 并发指标JMX接口
 * <p>
 * 定义JMX监控接口，暴露并发指标给JMX管理工具，提供远程监控能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface ConcurrentMetricsMXBean {

    /**
     * 获取总执行器数量
     *
     * @return 执行器数量
     */
    int getTotalExecutors();

    /**
     * 获取总完成任务数
     *
     * @return 完成任务数
     */
    long getTotalCompletedTasks();

    /**
     * 获取总失败任务数
     *
     * @return 失败任务数
     */
    long getTotalFailedTasks();

    /**
     * 获取整体成功率
     *
     * @return 成功率（百分比）
     */
    double getOverallSuccessRate();

    /**
     * 获取线程池状态
     *
     * @return 线程池状态映射
     */
    Map<String, String> getThreadPoolStatus();

    /**
     * 获取任务指标
     *
     * @return 任务指标映射
     */
    Map<String, String> getTaskMetrics();

    /**
     * 获取系统状态摘要
     *
     * @return 系统状态字符串
     */
    String getSystemStatus();
}