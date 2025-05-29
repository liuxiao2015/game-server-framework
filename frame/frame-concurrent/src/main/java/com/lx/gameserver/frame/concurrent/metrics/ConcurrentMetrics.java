/*
 * 文件名: ConcurrentMetrics.java
 * 用途: 并发指标收集器
 * 实现内容:
 *   - 收集并发相关的性能指标
 *   - 线程池指标（大小、活跃数、队列长度）
 *   - 任务执行指标（成功数、失败数、平均执行时间）
 *   - 定时器指标（待执行任务数、延迟统计）
 * 技术选型:
 *   - Micrometer + JMX
 *   - 统计学指标计算
 *   - MBean注册和管理
 * 依赖关系:
 *   - 与执行器管理器集成
 *   - 监控线程池状态、任务执行情况
 *   - 提供Prometheus指标导出
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.metrics;

import com.lx.gameserver.frame.concurrent.executor.ExecutorManager;
import com.lx.gameserver.frame.concurrent.executor.VirtualThreadExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 并发指标收集器
 * <p>
 * 收集并发相关的性能指标，包括线程池状态、任务执行情况、定时器统计等。
 * 支持JMX监控和Prometheus指标导出。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
public class ConcurrentMetrics implements ConcurrentMetricsMXBean {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentMetrics.class);

    /**
     * 执行器管理器
     */
    @Autowired
    private ExecutorManager executorManager;

    /**
     * MBean服务器
     */
    private MBeanServer mBeanServer;

    /**
     * MBean对象名
     */
    private ObjectName objectName;

    /**
     * 指标收集定时器
     */
    private ScheduledExecutorService metricsCollector;

    /**
     * 任务执行指标
     */
    private final Map<String, TaskMetrics> taskMetricsMap = new ConcurrentHashMap<>();

    /**
     * 全局指标
     */
    private final GlobalMetrics globalMetrics = new GlobalMetrics();

    /**
     * 线程池指标缓存
     */
    private volatile Map<String, ThreadPoolMetrics> threadPoolMetricsCache = new HashMap<>();

    /**
     * 初始化指标收集器
     */
    @PostConstruct
    public void initialize() {
        try {
            // 注册MBean
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
            objectName = new ObjectName("com.lx.gameserver.concurrent:type=ConcurrentMetrics");
            mBeanServer.registerMBean(this, objectName);
            
            // 启动指标收集定时器
            metricsCollector = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ConcurrentMetrics-Collector");
                t.setDaemon(true);
                return t;
            });
            
            // 每5秒收集一次指标
            metricsCollector.scheduleAtFixedRate(this::collectMetrics, 5, 5, TimeUnit.SECONDS);
            
            logger.info("并发指标收集器初始化完成");
            
        } catch (Exception e) {
            logger.error("并发指标收集器初始化失败", e);
        }
    }

    /**
     * 收集指标
     */
    private void collectMetrics() {
        try {
            collectThreadPoolMetrics();
            collectGlobalMetrics();
            logger.debug("指标收集完成");
        } catch (Exception e) {
            logger.error("指标收集异常", e);
        }
    }

    /**
     * 收集线程池指标
     */
    private void collectThreadPoolMetrics() {
        Map<String, ThreadPoolMetrics> newMetrics = new HashMap<>();
        
        for (String executorName : executorManager.getExecutorNames()) {
            ExecutorService executor = executorManager.getExecutor(executorName);
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                ThreadPoolMetrics metrics = new ThreadPoolMetrics();
                
                metrics.corePoolSize = tpe.getCorePoolSize();
                metrics.maximumPoolSize = tpe.getMaximumPoolSize();
                metrics.currentPoolSize = tpe.getPoolSize();
                metrics.activeThreadCount = tpe.getActiveCount();
                metrics.queueSize = tpe.getQueue().size();
                metrics.completedTaskCount = tpe.getCompletedTaskCount();
                metrics.totalTaskCount = tpe.getTaskCount();
                
                newMetrics.put(executorName, metrics);
                
            } else if (executor instanceof VirtualThreadExecutor) {
                VirtualThreadExecutor vte = (VirtualThreadExecutor) executor;
                ThreadPoolMetrics metrics = new ThreadPoolMetrics();
                
                metrics.activeThreadCount = vte.getActiveCount();
                metrics.currentPoolSize = vte.getPoolSize();
                metrics.queueSize = vte.getQueueSize();
                metrics.completedTaskCount = vte.getCompletedTaskCount();
                metrics.totalTaskCount = vte.getSubmittedTaskCount();
                
                newMetrics.put(executorName, metrics);
            }
        }
        
        threadPoolMetricsCache = newMetrics;
    }

    /**
     * 收集全局指标
     */
    private void collectGlobalMetrics() {
        // 更新全局任务统计
        long totalCompleted = 0;
        long totalFailed = 0;
        
        for (TaskMetrics metrics : taskMetricsMap.values()) {
            totalCompleted += metrics.completedCount.sum();
            totalFailed += metrics.failedCount.sum();
        }
        
        globalMetrics.totalCompletedTasks = totalCompleted;
        globalMetrics.totalFailedTasks = totalFailed;
        globalMetrics.totalExecutors = executorManager.getExecutorNames().size();
    }

    /**
     * 记录任务开始
     *
     * @param taskName 任务名称
     */
    public void recordTaskStart(String taskName) {
        TaskMetrics metrics = getTaskMetrics(taskName);
        metrics.startedCount.increment();
        metrics.lastStartTime.set(System.currentTimeMillis());
    }

    /**
     * 记录任务完成
     *
     * @param taskName      任务名称
     * @param executionTime 执行时间（毫秒）
     */
    public void recordTaskCompletion(String taskName, long executionTime) {
        TaskMetrics metrics = getTaskMetrics(taskName);
        metrics.completedCount.increment();
        metrics.totalExecutionTime.add(executionTime);
        metrics.lastCompletionTime.set(System.currentTimeMillis());
        
        // 更新最小/最大执行时间
        updateMinMax(metrics.minExecutionTime, metrics.maxExecutionTime, executionTime);
    }

    /**
     * 记录任务失败
     *
     * @param taskName      任务名称
     * @param executionTime 执行时间（毫秒）
     */
    public void recordTaskFailure(String taskName, long executionTime) {
        TaskMetrics metrics = getTaskMetrics(taskName);
        metrics.failedCount.increment();
        metrics.totalExecutionTime.add(executionTime);
        metrics.lastFailureTime.set(System.currentTimeMillis());
        
        updateMinMax(metrics.minExecutionTime, metrics.maxExecutionTime, executionTime);
    }

    /**
     * 更新最小/最大值
     */
    private void updateMinMax(AtomicLong min, AtomicLong max, long value) {
        min.updateAndGet(current -> current == 0 ? value : Math.min(current, value));
        max.updateAndGet(current -> Math.max(current, value));
    }

    /**
     * 获取任务指标
     */
    private TaskMetrics getTaskMetrics(String taskName) {
        return taskMetricsMap.computeIfAbsent(taskName, k -> new TaskMetrics());
    }

    // ===== MXBean接口实现 =====

    @Override
    public int getTotalExecutors() {
        return globalMetrics.totalExecutors;
    }

    @Override
    public long getTotalCompletedTasks() {
        return globalMetrics.totalCompletedTasks;
    }

    @Override
    public long getTotalFailedTasks() {
        return globalMetrics.totalFailedTasks;
    }

    @Override
    public double getOverallSuccessRate() {
        long total = globalMetrics.totalCompletedTasks + globalMetrics.totalFailedTasks;
        return total == 0 ? 0.0 : (double) globalMetrics.totalCompletedTasks / total * 100.0;
    }

    @Override
    public Map<String, String> getThreadPoolStatus() {
        Map<String, String> status = new HashMap<>();
        for (Map.Entry<String, ThreadPoolMetrics> entry : threadPoolMetricsCache.entrySet()) {
            ThreadPoolMetrics metrics = entry.getValue();
            status.put(entry.getKey(), String.format(
                "active=%d, pool=%d, queue=%d, completed=%d",
                metrics.activeThreadCount, metrics.currentPoolSize, 
                metrics.queueSize, metrics.completedTaskCount));
        }
        return status;
    }

    @Override
    public Map<String, String> getTaskMetrics() {
        Map<String, String> metrics = new HashMap<>();
        for (Map.Entry<String, TaskMetrics> entry : taskMetricsMap.entrySet()) {
            TaskMetrics tm = entry.getValue();
            long completed = tm.completedCount.sum();
            long failed = tm.failedCount.sum();
            long total = completed + failed;
            double avgTime = total == 0 ? 0 : (double) tm.totalExecutionTime.sum() / total;
            
            metrics.put(entry.getKey(), String.format(
                "completed=%d, failed=%d, avg=%dms, min=%dms, max=%dms",
                completed, failed, (long) avgTime, tm.minExecutionTime.get(), tm.maxExecutionTime.get()));
        }
        return metrics;
    }

    @Override
    public String getSystemStatus() {
        return String.format(
            "Executors: %d, Completed: %d, Failed: %d, Success Rate: %.2f%%",
            getTotalExecutors(), getTotalCompletedTasks(), getTotalFailedTasks(), getOverallSuccessRate());
    }

    // ===== 公共API方法 =====

    /**
     * 获取线程池指标
     *
     * @param executorName 执行器名称
     * @return 线程池指标
     */
    public ThreadPoolMetrics getThreadPoolMetrics(String executorName) {
        return threadPoolMetricsCache.get(executorName);
    }

    /**
     * 获取所有线程池指标
     *
     * @return 线程池指标列表
     */
    public List<ThreadPoolMetrics> getAllThreadPoolMetrics() {
        return new ArrayList<>(threadPoolMetricsCache.values());
    }

    /**
     * 获取任务执行指标
     *
     * @param taskName 任务名称
     * @return 任务指标
     */
    public TaskMetrics getTaskExecutionMetrics(String taskName) {
        return taskMetricsMap.get(taskName);
    }

    /**
     * 重置所有指标
     */
    public void resetMetrics() {
        taskMetricsMap.clear();
        globalMetrics.reset();
        logger.info("所有指标已重置");
    }

    /**
     * 生成指标报告
     *
     * @return 指标报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 并发指标报告 ===\n");
        report.append(String.format("总执行器数: %d\n", getTotalExecutors()));
        report.append(String.format("总完成任务数: %d\n", getTotalCompletedTasks()));
        report.append(String.format("总失败任务数: %d\n", getTotalFailedTasks()));
        report.append(String.format("整体成功率: %.2f%%\n", getOverallSuccessRate()));
        
        report.append("\n=== 线程池状态 ===\n");
        for (Map.Entry<String, String> entry : getThreadPoolStatus().entrySet()) {
            report.append(String.format("%s: %s\n", entry.getKey(), entry.getValue()));
        }
        
        report.append("\n=== 任务指标 ===\n");
        for (Map.Entry<String, String> entry : getTaskMetrics().entrySet()) {
            report.append(String.format("%s: %s\n", entry.getKey(), entry.getValue()));
        }
        
        return report.toString();
    }

    // ===== 内部类 =====

    /**
     * 线程池指标
     */
    public static class ThreadPoolMetrics {
        public int corePoolSize;
        public int maximumPoolSize;
        public int currentPoolSize;
        public int activeThreadCount;
        public int queueSize;
        public long completedTaskCount;
        public long totalTaskCount;

        @Override
        public String toString() {
            return String.format("ThreadPoolMetrics[core=%d, max=%d, current=%d, active=%d, queue=%d, completed=%d, total=%d]",
                corePoolSize, maximumPoolSize, currentPoolSize, activeThreadCount, 
                queueSize, completedTaskCount, totalTaskCount);
        }
    }

    /**
     * 任务指标
     */
    public static class TaskMetrics {
        public final LongAdder startedCount = new LongAdder();
        public final LongAdder completedCount = new LongAdder();
        public final LongAdder failedCount = new LongAdder();
        public final LongAdder totalExecutionTime = new LongAdder();
        public final AtomicLong minExecutionTime = new AtomicLong(0);
        public final AtomicLong maxExecutionTime = new AtomicLong(0);
        public final AtomicLong lastStartTime = new AtomicLong(0);
        public final AtomicLong lastCompletionTime = new AtomicLong(0);
        public final AtomicLong lastFailureTime = new AtomicLong(0);

        public double getAverageExecutionTime() {
            long total = completedCount.sum() + failedCount.sum();
            return total == 0 ? 0.0 : (double) totalExecutionTime.sum() / total;
        }

        public double getSuccessRate() {
            long total = completedCount.sum() + failedCount.sum();
            return total == 0 ? 0.0 : (double) completedCount.sum() / total * 100.0;
        }
    }

    /**
     * 全局指标
     */
    private static class GlobalMetrics {
        public volatile int totalExecutors;
        public volatile long totalCompletedTasks;
        public volatile long totalFailedTasks;

        public void reset() {
            totalExecutors = 0;
            totalCompletedTasks = 0;
            totalFailedTasks = 0;
        }
    }

    /**
     * 关闭指标收集器
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (metricsCollector != null) {
                metricsCollector.shutdown();
                if (!metricsCollector.awaitTermination(5, TimeUnit.SECONDS)) {
                    metricsCollector.shutdownNow();
                }
            }
            
            if (mBeanServer != null && objectName != null) {
                mBeanServer.unregisterMBean(objectName);
            }
            
            logger.info("并发指标收集器已关闭");
            
        } catch (Exception e) {
            logger.error("关闭并发指标收集器异常", e);
        }
    }
}