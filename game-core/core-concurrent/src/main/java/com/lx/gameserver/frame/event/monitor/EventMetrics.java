/*
 * 文件名: EventMetrics.java
 * 用途: 事件指标收集器
 * 实现内容:
 *   - 收集事件处理的性能指标
 *   - 提供事件总线监控数据
 *   - 支持统计数据查询和导出
 * 技术选型:
 *   - 内存统计计数器
 *   - 线程安全设计
 *   - 简化的监控实现
 * 依赖关系:
 *   - 被DisruptorEventBus使用
 *   - 可选的Micrometer集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 事件指标收集器
 * <p>
 * 收集事件处理的性能指标，包括发布计数、处理耗时、错误统计等。
 * 提供事件总线的监控数据，支持运行时性能分析。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
public class EventMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(EventMetrics.class);
    
    /** 时间格式化器 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /** 事件发布计数器 */
    private final ConcurrentMap<String, LongAdder> publishedEvents = new ConcurrentHashMap<>();
    
    /** 事件处理计数器 */
    private final ConcurrentMap<String, LongAdder> processedEvents = new ConcurrentHashMap<>();
    
    /** 事件处理错误计数器 */
    private final ConcurrentMap<String, LongAdder> errorEvents = new ConcurrentHashMap<>();
    
    /** 事件处理总耗时（纳秒） */
    private final ConcurrentMap<String, LongAdder> totalProcessingTime = new ConcurrentHashMap<>();
    
    /** 事件处理次数 */
    private final ConcurrentMap<String, LongAdder> processingCount = new ConcurrentHashMap<>();
    
    /** 最大处理耗时（纳秒） */
    private final ConcurrentMap<String, AtomicLong> maxProcessingTime = new ConcurrentHashMap<>();
    
    /** 最小处理耗时（纳秒） */
    private final ConcurrentMap<String, AtomicLong> minProcessingTime = new ConcurrentHashMap<>();
    
    /** 全局统计 */
    private final LongAdder totalPublished = new LongAdder();
    private final LongAdder totalProcessed = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();
    
    /** 统计开始时间 */
    private final LocalDateTime startTime = LocalDateTime.now();
    
    /**
     * 记录事件发布
     *
     * @param eventType 事件类型
     */
    public void recordPublish(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return;
        }
        
        publishedEvents.computeIfAbsent(eventType, k -> new LongAdder()).increment();
        totalPublished.increment();
        
        if (logger.isDebugEnabled()) {
            logger.debug("记录事件发布: eventType={}", eventType);
        }
    }
    
    /**
     * 记录事件处理完成
     *
     * @param eventType 事件类型
     */
    public void recordProcessed(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return;
        }
        
        processedEvents.computeIfAbsent(eventType, k -> new LongAdder()).increment();
        totalProcessed.increment();
        
        if (logger.isDebugEnabled()) {
            logger.debug("记录事件处理: eventType={}", eventType);
        }
    }
    
    /**
     * 记录事件处理耗时
     *
     * @param eventType 事件类型
     * @param duration  处理耗时（纳秒）
     */
    public void recordProcessing(String eventType, long duration) {
        if (eventType == null || eventType.isEmpty() || duration < 0) {
            return;
        }
        
        // 累计处理时间
        totalProcessingTime.computeIfAbsent(eventType, k -> new LongAdder()).add(duration);
        processingCount.computeIfAbsent(eventType, k -> new LongAdder()).increment();
        
        // 更新最大处理时间
        maxProcessingTime.computeIfAbsent(eventType, k -> new AtomicLong(0))
                .updateAndGet(current -> Math.max(current, duration));
        
        // 更新最小处理时间
        minProcessingTime.computeIfAbsent(eventType, k -> new AtomicLong(Long.MAX_VALUE))
                .updateAndGet(current -> Math.min(current, duration));
        
        if (logger.isDebugEnabled()) {
            logger.debug("记录事件处理耗时: eventType={}, duration={}ns", eventType, duration);
        }
    }
    
    /**
     * 记录事件处理错误
     *
     * @param eventType 事件类型
     */
    public void recordError(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return;
        }
        
        errorEvents.computeIfAbsent(eventType, k -> new LongAdder()).increment();
        totalErrors.increment();
        
        logger.warn("记录事件处理错误: eventType={}", eventType);
    }
    
    /**
     * 获取事件发布数量
     *
     * @param eventType 事件类型
     * @return 发布数量
     */
    public long getPublishedCount(String eventType) {
        LongAdder counter = publishedEvents.get(eventType);
        return counter != null ? counter.sum() : 0;
    }
    
    /**
     * 获取事件处理数量
     *
     * @param eventType 事件类型
     * @return 处理数量
     */
    public long getProcessedCount(String eventType) {
        LongAdder counter = processedEvents.get(eventType);
        return counter != null ? counter.sum() : 0;
    }
    
    /**
     * 获取事件错误数量
     *
     * @param eventType 事件类型
     * @return 错误数量
     */
    public long getErrorCount(String eventType) {
        LongAdder counter = errorEvents.get(eventType);
        return counter != null ? counter.sum() : 0;
    }
    
    /**
     * 获取平均处理耗时（毫秒）
     *
     * @param eventType 事件类型
     * @return 平均处理耗时
     */
    public double getAverageProcessingTime(String eventType) {
        LongAdder totalTime = totalProcessingTime.get(eventType);
        LongAdder count = processingCount.get(eventType);
        
        if (totalTime == null || count == null || count.sum() == 0) {
            return 0.0;
        }
        
        return (totalTime.sum() / (double) count.sum()) / 1_000_000.0; // 转换为毫秒
    }
    
    /**
     * 获取最大处理耗时（毫秒）
     *
     * @param eventType 事件类型
     * @return 最大处理耗时
     */
    public double getMaxProcessingTime(String eventType) {
        AtomicLong maxTime = maxProcessingTime.get(eventType);
        return maxTime != null ? maxTime.get() / 1_000_000.0 : 0.0; // 转换为毫秒
    }
    
    /**
     * 获取最小处理耗时（毫秒）
     *
     * @param eventType 事件类型
     * @return 最小处理耗时
     */
    public double getMinProcessingTime(String eventType) {
        AtomicLong minTime = minProcessingTime.get(eventType);
        long time = minTime != null ? minTime.get() : 0;
        return time == Long.MAX_VALUE ? 0.0 : time / 1_000_000.0; // 转换为毫秒
    }
    
    /**
     * 获取事件成功率
     *
     * @param eventType 事件类型
     * @return 成功率（0-1之间）
     */
    public double getSuccessRate(String eventType) {
        long processed = getProcessedCount(eventType);
        long errors = getErrorCount(eventType);
        long total = processed + errors;
        
        return total == 0 ? 1.0 : (double) processed / total;
    }
    
    /**
     * 获取总发布数量
     *
     * @return 总发布数量
     */
    public long getTotalPublished() {
        return totalPublished.sum();
    }
    
    /**
     * 获取总处理数量
     *
     * @return 总处理数量
     */
    public long getTotalProcessed() {
        return totalProcessed.sum();
    }
    
    /**
     * 获取总错误数量
     *
     * @return 总错误数量
     */
    public long getTotalErrors() {
        return totalErrors.sum();
    }
    
    /**
     * 获取统计报告
     *
     * @return 统计报告字符串
     */
    public String getReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 事件处理统计报告 ===\n");
        report.append("统计开始时间: ").append(startTime.format(TIME_FORMATTER)).append("\n");
        report.append("当前时间: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n");
        report.append("总发布事件: ").append(getTotalPublished()).append("\n");
        report.append("总处理事件: ").append(getTotalProcessed()).append("\n");
        report.append("总错误事件: ").append(getTotalErrors()).append("\n");
        report.append("整体成功率: ").append(String.format("%.2f%%", getOverallSuccessRate() * 100)).append("\n");
        report.append("\n=== 各事件类型详细统计 ===\n");
        
        // 按事件类型统计
        for (String eventType : publishedEvents.keySet()) {
            report.append(String.format("事件类型: %s\n", eventType));
            report.append(String.format("  发布数量: %d\n", getPublishedCount(eventType)));
            report.append(String.format("  处理数量: %d\n", getProcessedCount(eventType)));
            report.append(String.format("  错误数量: %d\n", getErrorCount(eventType)));
            report.append(String.format("  成功率: %.2f%%\n", getSuccessRate(eventType) * 100));
            report.append(String.format("  平均耗时: %.2fms\n", getAverageProcessingTime(eventType)));
            report.append(String.format("  最大耗时: %.2fms\n", getMaxProcessingTime(eventType)));
            report.append(String.format("  最小耗时: %.2fms\n", getMinProcessingTime(eventType)));
            report.append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * 获取整体成功率
     *
     * @return 整体成功率
     */
    public double getOverallSuccessRate() {
        long processed = getTotalProcessed();
        long errors = getTotalErrors();
        long total = processed + errors;
        
        return total == 0 ? 1.0 : (double) processed / total;
    }
    
    /**
     * 重置所有统计数据
     */
    public void reset() {
        publishedEvents.clear();
        processedEvents.clear();
        errorEvents.clear();
        totalProcessingTime.clear();
        processingCount.clear();
        maxProcessingTime.clear();
        minProcessingTime.clear();
        
        totalPublished.reset();
        totalProcessed.reset();
        totalErrors.reset();
        
        logger.info("事件统计数据已重置");
    }
    
    /**
     * 获取指定事件类型的详细统计
     *
     * @param eventType 事件类型
     * @return 详细统计信息
     */
    public EventTypeMetrics getEventTypeMetrics(String eventType) {
        return new EventTypeMetrics(
                eventType,
                getPublishedCount(eventType),
                getProcessedCount(eventType),
                getErrorCount(eventType),
                getAverageProcessingTime(eventType),
                getMaxProcessingTime(eventType),
                getMinProcessingTime(eventType),
                getSuccessRate(eventType)
        );
    }
    
    /**
     * 事件类型统计数据
     */
    public static class EventTypeMetrics {
        private final String eventType;
        private final long publishedCount;
        private final long processedCount;
        private final long errorCount;
        private final double averageProcessingTime;
        private final double maxProcessingTime;
        private final double minProcessingTime;
        private final double successRate;
        
        public EventTypeMetrics(String eventType, long publishedCount, long processedCount,
                               long errorCount, double averageProcessingTime, double maxProcessingTime,
                               double minProcessingTime, double successRate) {
            this.eventType = eventType;
            this.publishedCount = publishedCount;
            this.processedCount = processedCount;
            this.errorCount = errorCount;
            this.averageProcessingTime = averageProcessingTime;
            this.maxProcessingTime = maxProcessingTime;
            this.minProcessingTime = minProcessingTime;
            this.successRate = successRate;
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public long getPublishedCount() { return publishedCount; }
        public long getProcessedCount() { return processedCount; }
        public long getErrorCount() { return errorCount; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public double getMaxProcessingTime() { return maxProcessingTime; }
        public double getMinProcessingTime() { return minProcessingTime; }
        public double getSuccessRate() { return successRate; }
        
        @Override
        public String toString() {
            return String.format("EventTypeMetrics{eventType='%s', published=%d, processed=%d, errors=%d, avgTime=%.2fms, successRate=%.2f%%}",
                    eventType, publishedCount, processedCount, errorCount, averageProcessingTime, successRate * 100);
        }
    }
}