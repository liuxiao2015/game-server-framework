/*
 * 文件名: PerformanceMonitor.java
 * 用途: 性能监控器
 * 实现内容:
 *   - 性能指标收集
 *   - 监控数据统计
 *   - 告警机制
 *   - 监控报告生成
 * 技术选型:
 *   - Micrometer指标库
 *   - Spring Boot Actuator
 *   - 自定义指标收集
 * 依赖关系:
 *   - 被框架各模块使用
 *   - 提供统一的监控服务
 * 作者: liuxiao2015
 * 日期: 2025-05-31
 */
package com.lx.gameserver.frame.monitor;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 性能监控器
 * <p>
 * 提供统一的性能监控功能，支持指标收集、监控统计、告警等特性。
 * 集成Micrometer指标库，提供丰富的监控数据。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-31
 */
@Slf4j
@Service
public class PerformanceMonitor {
    
    @Autowired
    MeterRegistry meterRegistry;
    
    /**
     * 计数器缓存
     */
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    
    /**
     * 计时器缓存
     */
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    
    /**
     * 量表缓存
     */
    private final Map<String, Gauge> gaugeCache = new ConcurrentHashMap<>();
    
    /**
     * 监控开始时间
     */
    private final Instant startTime = Instant.now();
    
    /**
     * 记录计数指标
     *
     * @param name 指标名称
     * @param tags 标签
     */
    public void incrementCounter(String name, String... tags) {
        getOrCreateCounter(name, tags).increment();
    }
    
    /**
     * 记录计数指标（带增量）
     *
     * @param name 指标名称
     * @param amount 增量
     * @param tags 标签
     */
    public void incrementCounter(String name, double amount, String... tags) {
        getOrCreateCounter(name, tags).increment(amount);
    }
    
    /**
     * 记录计时指标
     *
     * @param name 指标名称
     * @param duration 时长
     * @param tags 标签
     */
    public void recordTimer(String name, Duration duration, String... tags) {
        getOrCreateTimer(name, tags).record(duration);
    }
    
    /**
     * 执行并计时
     *
     * @param name 指标名称
     * @param operation 操作
     * @param tags 标签
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T time(String name, Supplier<T> operation, String... tags) {
        try {
            return getOrCreateTimer(name, tags).recordCallable(operation::get);
        } catch (Exception e) {
            log.error("执行计时操作失败: {}", name, e);
            throw new RuntimeException("执行计时操作失败", e);
        }
    }
    
    /**
     * 记录量表指标
     *
     * @param name 指标名称
     * @param value 数值提供者
     * @param tags 标签
     */
    public void registerGauge(String name, Supplier<Number> value, String... tags) {
        String key = generateKey(name, tags);
        if (!gaugeCache.containsKey(key)) {
            Gauge gauge = Gauge.builder(name, value, s -> s.get().doubleValue())
                    .tags(Tags.of(tags))
                    .register(meterRegistry);
            gaugeCache.put(key, gauge);
        }
    }
    
    /**
     * 获取或创建计数器
     *
     * @param name 名称
     * @param tags 标签
     * @return 计数器
     */
    private Counter getOrCreateCounter(String name, String... tags) {
        String key = generateKey(name, tags);
        return counterCache.computeIfAbsent(key, k -> 
                Counter.builder(name)
                        .tags(Tags.of(tags))
                        .register(meterRegistry));
    }
    
    /**
     * 获取或创建计时器
     *
     * @param name 名称
     * @param tags 标签
     * @return 计时器
     */
    private Timer getOrCreateTimer(String name, String... tags) {
        String key = generateKey(name, tags);
        return timerCache.computeIfAbsent(key, k ->
                Timer.builder(name)
                        .tags(Tags.of(tags))
                        .register(meterRegistry));
    }
    
    /**
     * 生成缓存键
     *
     * @param name 名称
     * @param tags 标签
     * @return 缓存键
     */
    private String generateKey(String name, String... tags) {
        StringBuilder key = new StringBuilder(name);
        if (tags != null && tags.length > 0) {
            for (String tag : tags) {
                key.append(":").append(tag);
            }
        }
        return key.toString();
    }
    
    /**
     * 获取运行时间
     *
     * @return 运行时间
     */
    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }
    
    /**
     * 获取监控指标数量
     *
     * @return 指标数量
     */
    public int getMetricsCount() {
        return counterCache.size() + timerCache.size() + gaugeCache.size();
    }
    
    /**
     * 清理监控缓存
     */
    public void clearCache() {
        counterCache.clear();
        timerCache.clear();
        gaugeCache.clear();
        log.info("监控缓存已清空");
    }
    
    /**
     * 生成监控报告
     *
     * @return 监控报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 性能监控报告 ===\n");
        report.append("运行时间: ").append(getUptime()).append("\n");
        report.append("指标数量: ").append(getMetricsCount()).append("\n");
        report.append("计数器: ").append(counterCache.size()).append("\n");
        report.append("计时器: ").append(timerCache.size()).append("\n");
        report.append("量表: ").append(gaugeCache.size()).append("\n");
        
        // 添加系统性能信息
        report.append("\n=== 系统性能 ===\n");
        Runtime runtime = Runtime.getRuntime();
        report.append("内存使用: ").append(getMemoryUsage()).append("\n");
        report.append("可用处理器: ").append(runtime.availableProcessors()).append("\n");
        
        return report.toString();
    }
    
    /**
     * 获取内存使用情况
     *
     * @return 内存使用描述
     */
    public String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return String.format("已用: %dMB / 总计: %dMB / 最大: %dMB (使用率: %.1f%%)",
                usedMemory / 1024 / 1024,
                totalMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                (double) usedMemory / maxMemory * 100);
    }
    
    /**
     * 获取CPU使用率估算
     *
     * @return CPU使用率百分比
     */
    public double getCpuUsageEstimate() {
        // 简单的CPU使用率估算
        // 在实际应用中，可以使用更复杂的方法来获取准确的CPU使用率
        long startTime = System.nanoTime();
        
        // 执行一些计算来测量CPU响应
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sqrt(i);
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        // 根据计算时间估算CPU负载（这是一个简化的方法）
        double normalizedTime = duration / 1_000_000.0; // 转换为毫秒
        return Math.min(normalizedTime / 10.0 * 100, 100.0); // 简化的CPU使用率计算
    }
    
    /**
     * 记录性能警告
     *
     * @param metric 指标名称
     * @param value 当前值
     * @param threshold 阈值
     */
    public void recordPerformanceWarning(String metric, double value, double threshold) {
        if (value > threshold) {
            incrementCounter("performance.warning", "metric", metric);
            log.warn("性能警告: {} 当前值 {} 超过阈值 {}", metric, value, threshold);
        }
    }
    
    /**
     * 自动性能检查
     */
    public void performanceHealthCheck() {
        // 内存使用率检查
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        recordPerformanceWarning("memory_usage", memoryUsage * 100, 80.0);
        
        // CPU使用率检查
        double cpuUsage = getCpuUsageEstimate();
        recordPerformanceWarning("cpu_usage", cpuUsage, 70.0);
        
        // 记录健康检查
        recordTimer("performance.health_check", Duration.ofMillis(System.currentTimeMillis() - startTime.toEpochMilli()));
    }
}