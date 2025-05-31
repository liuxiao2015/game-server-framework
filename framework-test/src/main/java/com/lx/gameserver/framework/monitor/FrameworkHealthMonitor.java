package com.lx.gameserver.framework.monitor;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏服务器框架健康监控工具
 * <p>
 * 提供框架实时健康监控功能：
 * - JVM性能监控
 * - 内存使用监控
 * - GC性能监控
 * - 线程池状态监控
 * - 自定义指标监控
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-31
 */
@Component
public class FrameworkHealthMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(FrameworkHealthMonitor.class);
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    
    // 健康指标
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong errorRequests = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private volatile boolean healthy = true;
    private volatile Instant lastHealthCheck = Instant.now();
    
    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("健康"),
        WARNING("警告"),
        CRITICAL("严重"),
        DOWN("宕机");
        
        private final String description;
        
        HealthStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 健康报告
     */
    public static class HealthReport {
        private HealthStatus status;
        private String timestamp;
        private double cpuUsage;
        private double memoryUsage;
        private long totalMemory;
        private long usedMemory;
        private long freeMemory;
        private int activeThreads;
        private long gcCount;
        private long gcTime;
        private long uptime;
        private double requestRate;
        private double errorRate;
        private long activeConnections;
        private String details;
        
        // Getters and setters
        public HealthStatus getStatus() { return status; }
        public void setStatus(HealthStatus status) { this.status = status; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
        public long getTotalMemory() { return totalMemory; }
        public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }
        public long getUsedMemory() { return usedMemory; }
        public void setUsedMemory(long usedMemory) { this.usedMemory = usedMemory; }
        public long getFreeMemory() { return freeMemory; }
        public void setFreeMemory(long freeMemory) { this.freeMemory = freeMemory; }
        public int getActiveThreads() { return activeThreads; }
        public void setActiveThreads(int activeThreads) { this.activeThreads = activeThreads; }
        public long getGcCount() { return gcCount; }
        public void setGcCount(long gcCount) { this.gcCount = gcCount; }
        public long getGcTime() { return gcTime; }
        public void setGcTime(long gcTime) { this.gcTime = gcTime; }
        public long getUptime() { return uptime; }
        public void setUptime(long uptime) { this.uptime = uptime; }
        public double getRequestRate() { return requestRate; }
        public void setRequestRate(double requestRate) { this.requestRate = requestRate; }
        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
        public long getActiveConnections() { return activeConnections; }
        public void setActiveConnections(long activeConnections) { this.activeConnections = activeConnections; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
    }
    
    /**
     * 启动健康监控
     */
    public void startMonitoring() {
        logger.info("启动框架健康监控...");
        
        // 每30秒进行一次健康检查
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 0, 30, TimeUnit.SECONDS);
        
        // 每5分钟输出详细健康报告
        scheduler.scheduleAtFixedRate(this::printDetailedHealthReport, 60, 300, TimeUnit.SECONDS);
        
        logger.info("框架健康监控已启动");
    }
    
    /**
     * 停止健康监控
     */
    public void stopMonitoring() {
        logger.info("停止框架健康监控...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("框架健康监控已停止");
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        try {
            HealthReport report = generateHealthReport();
            evaluateHealth(report);
            lastHealthCheck = Instant.now();
            
            if (report.getStatus() != HealthStatus.HEALTHY) {
                logger.warn("健康检查警告: {} - {}", report.getStatus().getDescription(), report.getDetails());
            }
            
        } catch (Exception e) {
            logger.error("健康检查执行失败", e);
            healthy = false;
        }
    }
    
    /**
     * 生成健康报告
     */
    public HealthReport generateHealthReport() {
        HealthReport report = new HealthReport();
        report.setTimestamp(Instant.now().toString());
        
        try {
            // JVM基本信息
            Runtime runtime = Runtime.getRuntime();
            report.setTotalMemory(runtime.totalMemory());
            report.setUsedMemory(runtime.totalMemory() - runtime.freeMemory());
            report.setFreeMemory(runtime.freeMemory());
            report.setMemoryUsage((report.getUsedMemory() * 100.0) / report.getTotalMemory());
            
            // CPU使用率（通过JMX获取）
            try {
                ObjectName osName = new ObjectName("java.lang:type=OperatingSystem");
                Double cpuUsage = (Double) mBeanServer.getAttribute(osName, "ProcessCpuLoad");
                report.setCpuUsage(cpuUsage != null ? cpuUsage * 100 : 0);
            } catch (Exception e) {
                report.setCpuUsage(0);
            }
            
            // 线程信息
            report.setActiveThreads(Thread.activeCount());
            
            // 运行时间
            try {
                ObjectName runtimeName = new ObjectName("java.lang:type=Runtime");
                Long uptime = (Long) mBeanServer.getAttribute(runtimeName, "Uptime");
                report.setUptime(uptime != null ? uptime : 0);
            } catch (Exception e) {
                report.setUptime(0);
            }
            
            // GC信息
            long totalGcCount = 0;
            long totalGcTime = 0;
            try {
                ObjectName[] gcNames = {
                    new ObjectName("java.lang:type=GarbageCollector,name=G1 Young Generation"),
                    new ObjectName("java.lang:type=GarbageCollector,name=G1 Old Generation"),
                    new ObjectName("java.lang:type=GarbageCollector,name=PS Scavenge"),
                    new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep")
                };
                
                for (ObjectName gcName : gcNames) {
                    try {
                        if (mBeanServer.isRegistered(gcName)) {
                            Long count = (Long) mBeanServer.getAttribute(gcName, "CollectionCount");
                            Long time = (Long) mBeanServer.getAttribute(gcName, "CollectionTime");
                            if (count != null) totalGcCount += count;
                            if (time != null) totalGcTime += time;
                        }
                    } catch (Exception ignored) {
                        // 忽略不存在的GC
                    }
                }
            } catch (Exception e) {
                logger.debug("获取GC信息失败", e);
            }
            
            report.setGcCount(totalGcCount);
            report.setGcTime(totalGcTime);
            
            // 业务指标
            long total = totalRequests.get();
            long errors = errorRequests.get();
            report.setRequestRate(total);
            report.setErrorRate(total > 0 ? (errors * 100.0) / total : 0);
            report.setActiveConnections(activeConnections.get());
            
        } catch (Exception e) {
            logger.error("生成健康报告失败", e);
            report.setDetails("生成健康报告失败: " + e.getMessage());
        }
        
        return report;
    }
    
    /**
     * 评估健康状态
     */
    private void evaluateHealth(HealthReport report) {
        StringBuilder details = new StringBuilder();
        HealthStatus status = HealthStatus.HEALTHY;
        
        // 检查内存使用率
        if (report.getMemoryUsage() > 90) {
            status = HealthStatus.CRITICAL;
            details.append("内存使用率过高(").append(String.format("%.1f", report.getMemoryUsage())).append("%)；");
        } else if (report.getMemoryUsage() > 75) {
            status = HealthStatus.WARNING;
            details.append("内存使用率偏高(").append(String.format("%.1f", report.getMemoryUsage())).append("%)；");
        }
        
        // 检查CPU使用率
        if (report.getCpuUsage() > 90) {
            if (status == HealthStatus.HEALTHY) status = HealthStatus.CRITICAL;
            details.append("CPU使用率过高(").append(String.format("%.1f", report.getCpuUsage())).append("%)；");
        } else if (report.getCpuUsage() > 75) {
            if (status == HealthStatus.HEALTHY) status = HealthStatus.WARNING;
            details.append("CPU使用率偏高(").append(String.format("%.1f", report.getCpuUsage())).append("%)；");
        }
        
        // 检查错误率
        if (report.getErrorRate() > 10) {
            if (status == HealthStatus.HEALTHY) status = HealthStatus.CRITICAL;
            details.append("错误率过高(").append(String.format("%.1f", report.getErrorRate())).append("%)；");
        } else if (report.getErrorRate() > 5) {
            if (status == HealthStatus.HEALTHY) status = HealthStatus.WARNING;
            details.append("错误率偏高(").append(String.format("%.1f", report.getErrorRate())).append("%)；");
        }
        
        // 检查线程数
        if (report.getActiveThreads() > 1000) {
            if (status == HealthStatus.HEALTHY) status = HealthStatus.WARNING;
            details.append("活跃线程数过多(").append(report.getActiveThreads()).append(")；");
        }
        
        report.setStatus(status);
        report.setDetails(details.length() > 0 ? details.toString() : "系统运行正常");
        
        healthy = (status == HealthStatus.HEALTHY || status == HealthStatus.WARNING);
    }
    
    /**
     * 打印详细健康报告
     */
    private void printDetailedHealthReport() {
        HealthReport report = generateHealthReport();
        evaluateHealth(report);
        
        logger.info("=== 框架健康监控报告 ===");
        logger.info("状态: {} - {}", report.getStatus().getDescription(), report.getDetails());
        logger.info("运行时间: {}ms", report.getUptime());
        logger.info("CPU使用率: {:.1f}%", report.getCpuUsage());
        logger.info("内存使用: {:.1f}% ({}/{}MB)", 
                report.getMemoryUsage(), 
                report.getUsedMemory() / 1024 / 1024,
                report.getTotalMemory() / 1024 / 1024);
        logger.info("活跃线程: {}", report.getActiveThreads());
        logger.info("GC统计: {}次，总耗时{}ms", report.getGcCount(), report.getGcTime());
        logger.info("请求统计: 总计{}，错误率{:.1f}%", (long)report.getRequestRate(), report.getErrorRate());
        logger.info("活跃连接: {}", report.getActiveConnections());
        logger.info("========================");
    }
    
    /**
     * 记录请求
     */
    public void recordRequest() {
        totalRequests.incrementAndGet();
    }
    
    /**
     * 记录错误请求
     */
    public void recordError() {
        errorRequests.incrementAndGet();
    }
    
    /**
     * 记录连接变化
     */
    public void recordConnection(boolean connect) {
        if (connect) {
            activeConnections.incrementAndGet();
        } else {
            activeConnections.decrementAndGet();
        }
    }
    
    /**
     * 检查是否健康
     */
    public boolean isHealthy() {
        return healthy;
    }
    
    /**
     * 获取最后健康检查时间
     */
    public Instant getLastHealthCheck() {
        return lastHealthCheck;
    }
}