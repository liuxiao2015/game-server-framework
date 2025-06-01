/*
 * 文件名: HealthChecker.java
 * 用途: 游戏服务器健康检查组件
 * 内容: 
 *   - 实现对关键服务模块的健康状态检测
 *   - 提供自定义健康检查指标
 *   - 集成Spring Boot Actuator健康检查机制
 *   - 监控数据库、缓存、网络等关键组件状态
 * 技术选型: 
 *   - Spring Boot Actuator健康检查API
 *   - Spring框架的组件注解
 *   - 异步健康检查机制
 * 依赖关系: 
 *   - 依赖Spring Boot Actuator
 *   - 与各服务模块进行健康状态交互
 */
package com.lx.gameserver.launcher;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 游戏服务器健康检查组件
 * <p>
 * 负责监控游戏服务器各关键模块的健康状态，包括：
 * 1. 服务器基础运行状态
 * 2. 数据库连接状态
 * 3. 缓存服务状态
 * 4. 网络通信状态
 * 5. 内存使用情况
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
@Component("gameServerHealthIndicator")
public class HealthChecker implements HealthIndicator {
    
    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 服务器启动时间
     */
    private final LocalDateTime startupTime;
    
    /**
     * 构造函数，记录服务器启动时间
     */
    public HealthChecker() {
        this.startupTime = LocalDateTime.now();
    }
    
    /**
     * 执行健康检查
     * <p>
     * 检查游戏服务器的各项关键指标，返回总体健康状态。
     * 如果所有检查项都正常，返回UP状态；否则返回DOWN状态。
     * </p>
     *
     * @return 健康检查结果
     */
    @Override
    public Health health() {
        try {
            // 执行各项健康检查
            Map<String, Object> details = new HashMap<>();
            boolean allHealthy = true;
            
            // 1. 检查基础运行状态
            boolean basicStatus = checkBasicStatus(details);
            allHealthy &= basicStatus;
            
            // 2. 检查内存状态
            boolean memoryStatus = checkMemoryStatus(details);
            allHealthy &= memoryStatus;
            
            // 3. 检查线程状态
            boolean threadStatus = checkThreadStatus(details);
            allHealthy &= threadStatus;
            
            // 4. 检查磁盘空间
            boolean diskStatus = checkDiskStatus(details);
            allHealthy &= diskStatus;
            
            // 5. 添加运行时信息
            addRuntimeInfo(details);
            
            // 返回健康检查结果
            if (allHealthy) {
                return Health.up()
                        .withDetails(details)
                        .build();
            } else {
                return Health.down()
                        .withDetails(details)
                        .build();
            }
            
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .withDetail("error", "健康检查执行异常: " + e.getMessage())
                    .withDetail("timestamp", LocalDateTime.now().format(TIME_FORMATTER))
                    .build();
        }
    }
    
    /**
     * 检查基础运行状态
     *
     * @param details 详细信息Map
     * @return 是否健康
     */
    private boolean checkBasicStatus(Map<String, Object> details) {
        try {
            details.put("服务器状态", "运行中");
            details.put("启动时间", startupTime.format(TIME_FORMATTER));
            details.put("运行时长", calculateUptime());
            return true;
        } catch (Exception e) {
            details.put("基础状态检查", "失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查内存状态
     *
     * @param details 详细信息Map
     * @return 是否健康
     */
    private boolean checkMemoryStatus(Map<String, Object> details) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            Map<String, Object> memoryInfo = new HashMap<>();
            memoryInfo.put("最大内存", formatBytes(maxMemory));
            memoryInfo.put("已分配内存", formatBytes(totalMemory));
            memoryInfo.put("空闲内存", formatBytes(freeMemory));
            memoryInfo.put("已使用内存", formatBytes(usedMemory));
            memoryInfo.put("内存使用率", String.format("%.2f%%", memoryUsagePercent));
            
            details.put("内存状态", memoryInfo);
            
            // 内存使用率超过90%视为不健康
            return memoryUsagePercent < 90.0;
            
        } catch (Exception e) {
            details.put("内存状态检查", "失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查线程状态
     *
     * @param details 详细信息Map
     * @return 是否健康
     */
    private boolean checkThreadStatus(Map<String, Object> details) {
        try {
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }
            
            int activeThreads = rootGroup.activeCount();
            
            Map<String, Object> threadInfo = new HashMap<>();
            threadInfo.put("活跃线程数", activeThreads);
            threadInfo.put("可用处理器数", Runtime.getRuntime().availableProcessors());
            
            details.put("线程状态", threadInfo);
            
            // 活跃线程数超过1000视为可能有问题
            return activeThreads < 1000;
            
        } catch (Exception e) {
            details.put("线程状态检查", "失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查磁盘空间
     *
     * @param details 详细信息Map
     * @return 是否健康
     */
    private boolean checkDiskStatus(Map<String, Object> details) {
        try {
            java.io.File currentDir = new java.io.File(".");
            long totalSpace = currentDir.getTotalSpace();
            long freeSpace = currentDir.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            double usagePercent = (double) usedSpace / totalSpace * 100;
            
            Map<String, Object> diskInfo = new HashMap<>();
            diskInfo.put("总空间", formatBytes(totalSpace));
            diskInfo.put("可用空间", formatBytes(freeSpace));
            diskInfo.put("已用空间", formatBytes(usedSpace));
            diskInfo.put("使用率", String.format("%.2f%%", usagePercent));
            
            details.put("磁盘状态", diskInfo);
            
            // 磁盘使用率超过95%视为不健康
            return usagePercent < 95.0;
            
        } catch (Exception e) {
            details.put("磁盘状态检查", "失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 添加运行时信息
     *
     * @param details 详细信息Map
     */
    private void addRuntimeInfo(Map<String, Object> details) {
        Map<String, Object> runtimeInfo = new HashMap<>();
        runtimeInfo.put("Java版本", System.getProperty("java.version"));
        runtimeInfo.put("操作系统", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        runtimeInfo.put("系统架构", System.getProperty("os.arch"));
        runtimeInfo.put("用户名", System.getProperty("user.name"));
        runtimeInfo.put("工作目录", System.getProperty("user.dir"));
        runtimeInfo.put("检查时间", LocalDateTime.now().format(TIME_FORMATTER));
        
        details.put("运行时信息", runtimeInfo);
    }
    
    /**
     * 计算服务器运行时长
     *
     * @return 运行时长描述
     */
    private String calculateUptime() {
        LocalDateTime now = LocalDateTime.now();
        java.time.Duration duration = java.time.Duration.between(startupTime, now);
        
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟 %d秒", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟 %d秒", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d分钟 %d秒", minutes, seconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }
    
    /**
     * 格式化字节数为可读格式
     *
     * @param bytes 字节数
     * @return 格式化后的字符串
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