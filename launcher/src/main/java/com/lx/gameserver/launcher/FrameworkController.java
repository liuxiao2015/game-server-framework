/*
 * 文件名: FrameworkController.java
 * 用途: 框架管理和监控REST接口
 * 内容: 
 *   - 提供框架状态查询接口
 *   - 模块状态监控和管理
 *   - 性能指标查询
 *   - 配置信息获取
 * 技术选型: 
 *   - Spring Boot REST Controller
 *   - JSON响应格式
 *   - RESTful API设计
 * 依赖关系: 
 *   - 与GameServerFramework协作
 *   - 提供Web管理界面后端支持
 * 作者: liuxiao2015
 * 日期: 2025-05-31
 */
package com.lx.gameserver.launcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 框架管理和监控REST控制器
 * <p>
 * 提供HTTP接口用于：
 * 1. 查询框架整体状态
 * 2. 监控各模块运行状态
 * 3. 获取性能指标和统计信息
 * 4. 管理框架配置
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-31
 */
@RestController
@RequestMapping("/api/framework")
public class FrameworkController {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Autowired
    private GameServerFramework gameServerFramework;
    
    @Autowired(required = false)
    private ServiceManager serviceManager;
    
    /**
     * 获取框架总体状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFrameworkStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("status", gameServerFramework.getStatus().name());
            response.put("statusDescription", gameServerFramework.getStatus().getDescription());
            response.put("moduleCount", gameServerFramework.getInitializedModuleCount());
            
            if (gameServerFramework.getStartupTime() != null) {
                response.put("startupTime", gameServerFramework.getStartupTime().format(TIME_FORMATTER));
                
                long uptimeMs = java.time.Duration.between(gameServerFramework.getStartupTime(), LocalDateTime.now()).toMillis();
                response.put("uptimeMs", uptimeMs);
                response.put("uptimeFormatted", formatUptime(uptimeMs));
            }
            
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取所有模块状态
     */
    @GetMapping("/modules")
    public ResponseEntity<Map<String, Object>> getAllModuleStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, String> moduleStatus = gameServerFramework.getAllModuleStatus();
            
            response.put("success", true);
            response.put("moduleCount", moduleStatus.size());
            response.put("modules", moduleStatus);
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取指定模块状态
     */
    @GetMapping("/modules/{moduleName}")
    public ResponseEntity<Map<String, Object>> getModuleStatus(@PathVariable String moduleName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, String> allModuleStatus = gameServerFramework.getAllModuleStatus();
            
            if (allModuleStatus.containsKey(moduleName)) {
                response.put("success", true);
                response.put("moduleName", moduleName);
                response.put("status", allModuleStatus.get(moduleName));
                response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "模块未找到: " + moduleName);
                response.put("availableModules", allModuleStatus.keySet());
                response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
                return ResponseEntity.status(404).body(response);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取框架详细信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getFrameworkInfo() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> frameworkInfo = gameServerFramework.getFrameworkInfo();
            
            response.put("success", true);
            response.put("framework", frameworkInfo);
            
            // 添加系统信息
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("javaVendor", System.getProperty("java.vendor"));
            systemInfo.put("osName", System.getProperty("os.name"));
            systemInfo.put("osVersion", System.getProperty("os.version"));
            systemInfo.put("osArch", System.getProperty("os.arch"));
            systemInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            
            // 添加内存信息
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> memoryInfo = new HashMap<>();
            memoryInfo.put("maxMemory", formatBytes(runtime.maxMemory()));
            memoryInfo.put("totalMemory", formatBytes(runtime.totalMemory()));
            memoryInfo.put("freeMemory", formatBytes(runtime.freeMemory()));
            memoryInfo.put("usedMemory", formatBytes(runtime.totalMemory() - runtime.freeMemory()));
            
            response.put("system", systemInfo);
            response.put("memory", memoryInfo);
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取框架配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getFrameworkConfig() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            GameServerFramework.FrameworkConfig config = gameServerFramework.getFrameworkConfig();
            
            Map<String, Object> configInfo = new HashMap<>();
            configInfo.put("enableVirtualThreads", config.isEnableVirtualThreads());
            configInfo.put("enableMetrics", config.isEnableMetrics());
            configInfo.put("enableHealthCheck", config.isEnableHealthCheck());
            configInfo.put("initializationTimeout", config.getInitializationTimeout());
            configInfo.put("shutdownTimeout", config.getShutdownTimeout());
            configInfo.put("logLevel", config.getLogLevel());
            
            response.put("success", true);
            response.put("config", configInfo);
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取服务管理器状态（如果可用）
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getServiceManagerStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (serviceManager != null) {
                // 这里可以添加服务管理器状态获取逻辑
                response.put("success", true);
                response.put("serviceManagerAvailable", true);
                response.put("message", "服务管理器可用");
            } else {
                response.put("success", true);
                response.put("serviceManagerAvailable", false);
                response.put("message", "服务管理器不可用");
            }
            
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            GameServerFramework.FrameworkStatus status = gameServerFramework.getStatus();
            boolean healthy = status == GameServerFramework.FrameworkStatus.RUNNING;
            
            response.put("status", healthy ? "UP" : "DOWN");
            response.put("frameworkStatus", status.name());
            response.put("frameworkStatusDescription", status.getDescription());
            response.put("moduleCount", gameServerFramework.getInitializedModuleCount());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            
            if (healthy) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(TIME_FORMATTER));
            return ResponseEntity.status(503).body(response);
        }
    }
    
    // ===== 工具方法 =====
    
    /**
     * 格式化运行时间
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟 %d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }
    
    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", (double) bytes / (1024 * 1024 * 1024));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", (double) bytes / (1024 * 1024));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", (double) bytes / 1024);
        } else {
            return bytes + " B";
        }
    }
}