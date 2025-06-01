/*
 * 文件名: PerformanceMonitorTest.java
 * 用途: 性能监控器测试
 * 内容: 
 *   - 测试性能指标收集
 *   - 验证监控数据准确性
 *   - 测试监控告警机制
 * 技术选型: 
 *   - JUnit 5测试框架
 *   - Mockito模拟框架
 * 依赖关系: 
 *   - 测试frame-monitor模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.frame.monitor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

/**
 * 性能监控器测试
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@DisplayName("性能监控器测试")
class PerformanceMonitorTest {
    
    private PerformanceMonitor monitor;
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitor = new PerformanceMonitor();
        monitor.meterRegistry = meterRegistry;
    }
    
    @Test
    @DisplayName("测试性能监控器创建")
    void testPerformanceMonitorCreation() {
        assertNotNull(monitor);
        assertNotNull(monitor.meterRegistry);
    }
    
    @Test
    @DisplayName("测试计数器功能")
    void testCounter() {
        String counterName = "test.counter";
        
        monitor.incrementCounter(counterName);
        monitor.incrementCounter(counterName);
        monitor.incrementCounter(counterName, 3.0);
        
        // 验证计数器值通过MeterRegistry
        double count = meterRegistry.counter(counterName).count();
        assertEquals(5.0, count, 0.001);
    }
    
    @Test
    @DisplayName("测试计时器功能")
    void testTimer() {
        String timerName = "test.timer";
        Duration duration = Duration.ofMillis(100);
        
        monitor.recordTimer(timerName, duration);
        
        // 验证计时器记录
        long count = meterRegistry.timer(timerName).count();
        assertEquals(1, count);
    }
    
    @Test
    @DisplayName("测试计时器记录功能")
    void testTimerRecord() {
        String timerName = "test.timer.record";
        
        // 使用time方法记录执行时间
        String result = monitor.time(timerName, () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "completed";
        });
        
        assertEquals("completed", result);
        
        // 验证计时器有记录
        long count = meterRegistry.timer(timerName).count();
        assertEquals(1, count);
    }
    
    @Test
    @DisplayName("测试量表功能")
    void testGauge() {
        String gaugeName = "test.gauge";
        
        monitor.registerGauge(gaugeName, () -> 42.0);
        
        // 验证量表值
        Gauge gauge = meterRegistry.find(gaugeName).gauge();
        assertNotNull(gauge);
        assertEquals(42.0, gauge.value(), 0.001);
    }
    
    @Test
    @DisplayName("测试监控数据存在性")
    void testMonitoringDataExists() {
        // 测试基本监控功能存在
        assertNotNull(monitor);
        
        // 测试可以创建各种指标
        monitor.incrementCounter("test.existence");
        monitor.recordTimer("test.timer.existence", Duration.ofMillis(1));
        monitor.registerGauge("test.gauge.existence", () -> 1.0);
        
        // 验证MeterRegistry有数据
        assertTrue(meterRegistry.getMeters().size() > 0);
        
        // 测试其他方法
        assertTrue(monitor.getUptime().toMillis() >= 0);
        assertTrue(monitor.getMetricsCount() >= 0);
        
        // 测试报告生成
        String report = monitor.generateReport();
        assertNotNull(report);
        assertFalse(report.isEmpty());
    }
}