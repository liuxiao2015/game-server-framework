/*
 * 文件名: PerformanceOptimizationUtil.java
 * 用途: 性能优化工具类
 * 内容:
 *   - JVM参数动态调优
 *   - 内存使用优化
 *   - 线程池动态调整
 *   - 缓存命中率优化
 * 技术选型:
 *   - Java Management API
 *   - MBean监控
 *   - 动态配置调整
 * 依赖关系:
 *   - 集成到frame-monitor模块
 */
package com.lx.gameserver.frame.monitor.optimization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

/**
 * 性能优化工具类
 * <p>
 * 提供自动化的性能优化功能，包括JVM调优、内存管理、
 * 线程池调整等。支持运行时动态优化。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Component
public class PerformanceOptimizationUtil {

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    
    private final AtomicLong lastGcTime = new AtomicLong(0);
    private final AtomicLong lastOptimizationTime = new AtomicLong(0);
    
    @Autowired(required = false)
    private ThreadPoolExecutor gameThreadPool;

    /**
     * 执行全面性能优化
     */
    public void performComprehensiveOptimization() {
        log.info("开始执行全面性能优化...");
        
        try {
            // 内存优化
            optimizeMemoryUsage();
            
            // 线程池优化
            optimizeThreadPool();
            
            // GC优化建议
            analyzeAndOptimizeGC();
            
            // 缓存优化
            optimizeCacheUsage();
            
            lastOptimizationTime.set(System.currentTimeMillis());
            log.info("全面性能优化完成");
            
        } catch (Exception e) {
            log.error("性能优化过程中发生错误", e);
        }
    }

    /**
     * 内存使用优化
     */
    private void optimizeMemoryUsage() {
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double usageRatio = (double) heapUsed / heapMax;
        
        log.info("当前堆内存使用率: {:.2f}%", usageRatio * 100);
        
        if (usageRatio > 0.8) {
            log.warn("内存使用率过高，建议执行GC");
            System.gc(); // 在高内存压力下建议GC
        }
        
        // 检查非堆内存
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        long nonHeapMax = memoryBean.getNonHeapMemoryUsage().getMax();
        if (nonHeapMax > 0) {
            double nonHeapRatio = (double) nonHeapUsed / nonHeapMax;
            log.info("非堆内存使用率: {:.2f}%", nonHeapRatio * 100);
        }
    }

    /**
     * 线程池动态优化
     */
    private void optimizeThreadPool() {
        if (gameThreadPool == null) {
            log.debug("未找到游戏线程池，跳过线程池优化");
            return;
        }
        
        int corePoolSize = gameThreadPool.getCorePoolSize();
        int activeCount = gameThreadPool.getActiveCount();
        int queueSize = gameThreadPool.getQueue().size();
        
        log.info("线程池状态 - 核心线程数: {}, 活跃线程数: {}, 队列大小: {}", 
                corePoolSize, activeCount, queueSize);
        
        // 动态调整线程池大小
        if (activeCount >= corePoolSize * 0.8 && queueSize > 100) {
            int newCoreSize = Math.min(corePoolSize + 2, Runtime.getRuntime().availableProcessors() * 2);
            gameThreadPool.setCorePoolSize(newCoreSize);
            log.info("线程池负载较高，扩展核心线程数至: {}", newCoreSize);
        } else if (activeCount < corePoolSize * 0.3 && corePoolSize > 4) {
            int newCoreSize = Math.max(corePoolSize - 1, 4);
            gameThreadPool.setCorePoolSize(newCoreSize);
            log.info("线程池负载较低，缩减核心线程数至: {}", newCoreSize);
        }
    }

    /**
     * GC分析和优化
     */
    private void analyzeAndOptimizeGC() {
        try {
            // 获取GC信息
            ObjectName gcObjectName = new ObjectName("java.lang:type=GarbageCollector,name=*");
            
            // 分析GC频率
            analyzeGcFrequency();
            
            // 提供GC优化建议
            provideGcOptimizationSuggestions();
            
        } catch (Exception e) {
            log.warn("GC分析过程中发生错误", e);
        }
    }

    /**
     * 分析GC频率
     */
    private void analyzeGcFrequency() {
        // 这里可以添加具体的GC分析逻辑
        long currentTime = System.currentTimeMillis();
        long timeSinceLastGc = currentTime - lastGcTime.get();
        
        if (timeSinceLastGc < 10000) { // 10秒内有GC
            log.warn("GC频率较高，可能需要调优JVM参数");
        }
    }

    /**
     * 提供GC优化建议
     */
    private void provideGcOptimizationSuggestions() {
        List<String> suggestions = new ArrayList<>();
        
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double usageRatio = (double) heapUsed / heapMax;
        
        if (usageRatio > 0.9) {
            suggestions.add("考虑增加堆内存大小 (-Xmx)");
        }
        
        if (usageRatio > 0.8) {
            suggestions.add("考虑使用ZGC或Shenandoah低延迟垃圾回收器");
        }
        
        // 检查是否是服务器模式
        List<String> vmArguments = runtimeBean.getInputArguments();
        boolean isServerMode = vmArguments.stream().anyMatch(arg -> arg.contains("-server"));
        if (!isServerMode) {
            suggestions.add("建议启用服务器模式 (-server)");
        }
        
        if (!suggestions.isEmpty()) {
            log.info("GC优化建议: {}", String.join(", ", suggestions));
        }
    }

    /**
     * 缓存使用优化
     */
    private void optimizeCacheUsage() {
        // 这里可以添加缓存命中率分析和优化逻辑
        log.debug("执行缓存使用优化...");
        
        // 可以集成具体的缓存监控逻辑
        // 例如: Caffeine缓存统计，Redis连接池状态等
    }

    /**
     * 定期自动优化
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void autoOptimization() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastOptimization = currentTime - lastOptimizationTime.get();
        
        // 避免过于频繁的优化
        if (timeSinceLastOptimization > 300000) { // 5分钟
            performLightweightOptimization();
        }
    }

    /**
     * 轻量级优化（定期执行）
     */
    private void performLightweightOptimization() {
        try {
            // 只执行轻量级检查，避免影响性能
            checkMemoryUsage();
            checkThreadPoolHealth();
            
        } catch (Exception e) {
            log.debug("轻量级优化检查中发生错误", e);
        }
    }

    /**
     * 检查内存使用状况
     */
    private void checkMemoryUsage() {
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double usageRatio = (double) heapUsed / heapMax;
        
        if (usageRatio > 0.85) {
            log.warn("内存使用率过高: {:.2f}%，建议检查内存泄漏", usageRatio * 100);
        }
    }

    /**
     * 检查线程池健康状态
     */
    private void checkThreadPoolHealth() {
        if (gameThreadPool != null) {
            int queueSize = gameThreadPool.getQueue().size();
            if (queueSize > 1000) {
                log.warn("线程池队列积压过多: {} 个任务", queueSize);
            }
        }
    }

    /**
     * 获取性能优化报告
     */
    public String getOptimizationReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 性能优化报告 ===\n");
        
        // 内存状态
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        report.append(String.format("堆内存使用: %d MB / %d MB (%.2f%%)\n", 
                heapUsed / 1024 / 1024, heapMax / 1024 / 1024, 
                (double) heapUsed / heapMax * 100));
        
        // 运行时信息
        report.append(String.format("JVM运行时间: %d 分钟\n", 
                runtimeBean.getUptime() / 1000 / 60));
        
        // 处理器信息
        report.append(String.format("可用处理器: %d 个\n", 
                Runtime.getRuntime().availableProcessors()));
        
        // 线程池状态
        if (gameThreadPool != null) {
            report.append(String.format("线程池状态: 核心=%d, 活跃=%d, 队列=%d\n",
                    gameThreadPool.getCorePoolSize(),
                    gameThreadPool.getActiveCount(),
                    gameThreadPool.getQueue().size()));
        }
        
        return report.toString();
    }
}