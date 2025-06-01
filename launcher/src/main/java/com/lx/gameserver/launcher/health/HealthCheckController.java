/*
 * 文件名: HealthCheckController.java
 * 用途: 健康检查控制器
 * 内容: 
 *   - 提供系统健康状态API
 *   - 框架模块状态监控
 *   - 性能指标查询接口
 * 技术选型: 
 *   - Spring Boot Web
 *   - REST API设计
 *   - 健康检查标准
 * 依赖关系: 
 *   - 集成GameServerFramework
 *   - 使用PerformanceMonitor
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.launcher.health;

import com.lx.gameserver.launcher.GameServerFramework;
import com.lx.gameserver.frame.monitor.PerformanceMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * <p>
 * 提供系统健康状态和性能监控的REST API接口，
 * 支持运维和监控系统查询服务状态。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {
    
    @Autowired(required = false)
    private GameServerFramework gameServerFramework;
    
    @Autowired(required = false)
    private PerformanceMonitor performanceMonitor;
    
    /**
     * 基本健康检查
     */
    @GetMapping("/status")
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("status", "UP");
        status.put("timestamp", System.currentTimeMillis());
        
        if (gameServerFramework != null) {
            status.put("framework", Map.of(
                "status", gameServerFramework.getStatus().getDescription(),
                "uptime", gameServerFramework.getStartupTime() != null ? 
                    java.time.Duration.between(gameServerFramework.getStartupTime(), java.time.LocalDateTime.now()).toMillis() : 0,
                "modules", gameServerFramework.getInitializedModuleCount()
            ));
        }
        
        return status;
    }
    
    /**
     * 详细健康检查
     */
    @GetMapping("/detailed")
    public Map<String, Object> getDetailedHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // 基本状态
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        
        // 系统信息
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> system = new HashMap<>();
        system.put("processors", runtime.availableProcessors());
        system.put("memory", Map.of(
            "max", runtime.maxMemory(),
            "total", runtime.totalMemory(),
            "free", runtime.freeMemory(),
            "used", runtime.totalMemory() - runtime.freeMemory(),
            "usage_percent", (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100
        ));
        health.put("system", system);
        
        // 框架状态
        if (gameServerFramework != null) {
            Map<String, Object> framework = new HashMap<>();
            framework.put("status", gameServerFramework.getStatus());
            framework.put("info", gameServerFramework.getFrameworkInfo());
            framework.put("modules", gameServerFramework.getModuleStatus());
            health.put("framework", framework);
        }
        
        // 性能监控
        if (performanceMonitor != null) {
            Map<String, Object> performance = new HashMap<>();
            performance.put("uptime", performanceMonitor.getUptime().toString());
            performance.put("metrics_count", performanceMonitor.getMetricsCount());
            performance.put("memory_usage", performanceMonitor.getMemoryUsage());
            performance.put("cpu_estimate", performanceMonitor.getCpuUsageEstimate());
            health.put("performance", performance);
        }
        
        return health;
    }
    
    /**
     * 性能指标
     */
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        if (performanceMonitor != null) {
            metrics.put("report", performanceMonitor.generateReport());
            metrics.put("uptime_seconds", performanceMonitor.getUptime().getSeconds());
            metrics.put("metrics_count", performanceMonitor.getMetricsCount());
            
            // 执行健康检查
            performanceMonitor.performanceHealthCheck();
        }
        
        // JVM指标
        Runtime runtime = Runtime.getRuntime();
        metrics.put("jvm", Map.of(
            "memory_max_mb", runtime.maxMemory() / 1024 / 1024,
            "memory_total_mb", runtime.totalMemory() / 1024 / 1024,
            "memory_used_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024,
            "memory_free_mb", runtime.freeMemory() / 1024 / 1024,
            "processors", runtime.availableProcessors()
        ));
        
        return metrics;
    }
    
    /**
     * 框架模块状态
     */
    @GetMapping("/modules")
    public Map<String, Object> getModuleStatus() {
        Map<String, Object> result = new HashMap<>();
        
        if (gameServerFramework != null) {
            result.put("framework_status", gameServerFramework.getStatus());
            result.put("modules", gameServerFramework.getModuleStatus());
            result.put("module_count", gameServerFramework.getInitializedModuleCount());
        } else {
            result.put("error", "GameServerFramework not available");
        }
        
        return result;
    }
    
    /**
     * 简单的存活检查
     */
    @GetMapping("/alive")
    public Map<String, String> alive() {
        return Map.of("status", "alive", "timestamp", String.valueOf(System.currentTimeMillis()));
    }
    
    /**
     * 就绪检查
     */
    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> result = new HashMap<>();
        
        boolean isReady = true;
        String message = "Service is ready";
        
        if (gameServerFramework != null) {
            GameServerFramework.FrameworkStatus status = gameServerFramework.getStatus();
            isReady = status == GameServerFramework.FrameworkStatus.RUNNING;
            message = "Framework status: " + status.getDescription();
        }
        
        result.put("ready", isReady);
        result.put("status", isReady ? "UP" : "DOWN");
        result.put("message", message);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }
}