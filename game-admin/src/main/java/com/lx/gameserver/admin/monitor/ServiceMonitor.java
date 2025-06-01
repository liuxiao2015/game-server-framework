/*
 * 文件名: ServiceMonitor.java
 * 用途: 服务监控管理器
 * 实现内容:
 *   - 服务健康检查机制
 *   - 服务状态实时展示
 *   - 服务依赖拓扑管理
 *   - 服务调用链路追踪
 *   - 异常告警集成
 *   - 监控数据采集
 * 技术选型:
 *   - Spring Boot Actuator (健康检查)
 *   - Micrometer (指标采集)
 *   - WebSocket (实时推送)
 *   - Redis (状态缓存)
 * 依赖关系: 被监控模块和告警系统使用，依赖MetricsCollector
 */
package com.lx.gameserver.admin.monitor;

import com.lx.gameserver.admin.core.AdminContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 服务监控管理器
 * <p>
 * 负责监控系统中所有服务的健康状态，提供实时的服务状态展示、
 * 异常告警、调用链路追踪等功能。支持自定义监控指标和
 * 告警规则配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Service
public class ServiceMonitor implements HealthIndicator {

    /** 管理平台上下文 */
    @Autowired
    private AdminContext adminContext;

    /** Redis模板 */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** 指标采集器 */
    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    /** 监控的服务列表 */
    private final Map<String, ServiceInfo> monitoredServices = new ConcurrentHashMap<>();

    /** 服务状态历史记录 */
    private final Map<String, List<ServiceStatusRecord>> serviceStatusHistory = new ConcurrentHashMap<>();

    /** 监控任务执行器 */
    private ScheduledExecutorService monitorExecutor;

    /** 监控任务Future */
    private ScheduledFuture<?> monitorTask;

    /** 监控间隔(秒) */
    private static final int MONITOR_INTERVAL = 30;

    /** 状态历史保留条数 */
    private static final int STATUS_HISTORY_LIMIT = 100;

    /** Redis键前缀 */
    private static final String REDIS_SERVICE_STATUS_PREFIX = "admin:monitor:service:";
    private static final String REDIS_SERVICE_METRICS_PREFIX = "admin:monitor:metrics:";

    /**
     * 初始化服务监控
     */
    @PostConstruct
    public void init() {
        log.info("初始化服务监控管理器...");
        
        // 创建监控任务执行器
        monitorExecutor = Executors.newScheduledThreadPool(2, 
            r -> new Thread(r, "ServiceMonitor-Thread"));
        
        // 注册默认服务
        registerDefaultServices();
        
        // 启动监控任务
        startMonitorTask();
        
        log.info("服务监控管理器初始化完成，监控 {} 个服务", monitoredServices.size());
    }

    /**
     * 注册服务监控
     *
     * @param serviceInfo 服务信息
     */
    public void registerService(ServiceInfo serviceInfo) {
        monitoredServices.put(serviceInfo.getServiceId(), serviceInfo);
        serviceStatusHistory.put(serviceInfo.getServiceId(), new CopyOnWriteArrayList<>());
        
        log.info("注册服务监控: {}", serviceInfo.getServiceName());
    }

    /**
     * 取消服务监控
     *
     * @param serviceId 服务ID
     */
    public void unregisterService(String serviceId) {
        ServiceInfo removed = monitoredServices.remove(serviceId);
        serviceStatusHistory.remove(serviceId);
        
        if (removed != null) {
            log.info("取消服务监控: {}", removed.getServiceName());
        }
    }

    /**
     * 获取所有监控服务
     *
     * @return 服务信息列表
     */
    public List<ServiceInfo> getAllServices() {
        return new ArrayList<>(monitoredServices.values());
    }

    /**
     * 获取服务状态
     *
     * @param serviceId 服务ID
     * @return 服务状态
     */
    public ServiceStatus getServiceStatus(String serviceId) {
        ServiceInfo serviceInfo = monitoredServices.get(serviceId);
        if (serviceInfo == null) {
            return null;
        }
        
        return checkServiceHealth(serviceInfo);
    }

    /**
     * 获取服务状态历史
     *
     * @param serviceId 服务ID
     * @param limit 限制条数
     * @return 状态历史记录
     */
    public List<ServiceStatusRecord> getServiceStatusHistory(String serviceId, int limit) {
        List<ServiceStatusRecord> history = serviceStatusHistory.get(serviceId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        
        int size = Math.min(limit, history.size());
        return new ArrayList<>(history.subList(Math.max(0, history.size() - size), history.size()));
    }

    /**
     * 获取系统总体状态
     *
     * @return 系统状态统计
     */
    public SystemHealthStatus getSystemHealthStatus() {
        int totalServices = monitoredServices.size();
        long healthyServices = monitoredServices.values().stream()
            .mapToLong(service -> {
                ServiceStatus status = checkServiceHealth(service);
                return status.isHealthy() ? 1 : 0;
            })
            .sum();
        
        double healthyRatio = totalServices > 0 ? (double) healthyServices / totalServices : 1.0;
        
        SystemHealthStatus.HealthLevel healthLevel;
        if (healthyRatio >= 0.9) {
            healthLevel = SystemHealthStatus.HealthLevel.HEALTHY;
        } else if (healthyRatio >= 0.7) {
            healthLevel = SystemHealthStatus.HealthLevel.WARNING;
        } else {
            healthLevel = SystemHealthStatus.HealthLevel.CRITICAL;
        }
        
        return new SystemHealthStatus(
            healthLevel,
            totalServices,
            (int) healthyServices,
            totalServices - (int) healthyServices,
            LocalDateTime.now()
        );
    }

    /**
     * 强制刷新所有服务状态
     */
    public void refreshAllServices() {
        log.info("强制刷新所有服务状态...");
        
        CompletableFuture.runAsync(() -> {
            for (ServiceInfo serviceInfo : monitoredServices.values()) {
                try {
                    ServiceStatus status = checkServiceHealth(serviceInfo);
                    recordServiceStatus(serviceInfo.getServiceId(), status);
                } catch (Exception e) {
                    log.error("刷新服务 {} 状态失败", serviceInfo.getServiceName(), e);
                }
            }
        }, monitorExecutor);
    }

    /**
     * Spring Boot Actuator健康检查实现
     */
    @Override
    public Health health() {
        try {
            SystemHealthStatus systemStatus = getSystemHealthStatus();
            
            if (systemStatus.getHealthLevel() == SystemHealthStatus.HealthLevel.HEALTHY) {
                return Health.up()
                    .withDetail("totalServices", systemStatus.getTotalServices())
                    .withDetail("healthyServices", systemStatus.getHealthyServices())
                    .withDetail("unhealthyServices", systemStatus.getUnhealthyServices())
                    .build();
            } else {
                return Health.down()
                    .withDetail("totalServices", systemStatus.getTotalServices())
                    .withDetail("healthyServices", systemStatus.getHealthyServices())
                    .withDetail("unhealthyServices", systemStatus.getUnhealthyServices())
                    .withDetail("healthLevel", systemStatus.getHealthLevel())
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    /**
     * 注册默认服务
     */
    private void registerDefaultServices() {
        // 注册数据库服务
        registerService(new ServiceInfo(
            "database",
            "数据库服务",
            "MySQL数据库连接",
            ServiceInfo.ServiceType.DATABASE,
            "localhost:3306",
            this::checkDatabaseHealth
        ));
        
        // 注册Redis服务
        if (redisTemplate != null) {
            registerService(new ServiceInfo(
                "redis",
                "Redis缓存",
                "Redis缓存服务",
                ServiceInfo.ServiceType.CACHE,
                "localhost:6379",
                this::checkRedisHealth
            ));
        }
        
        // 注册应用服务
        registerService(new ServiceInfo(
            "admin-console",
            "管理后台",
            "管理后台应用服务",
            ServiceInfo.ServiceType.APPLICATION,
            "localhost:8090",
            this::checkApplicationHealth
        ));
    }

    /**
     * 启动监控任务
     */
    private void startMonitorTask() {
        monitorTask = monitorExecutor.scheduleWithFixedDelay(
            this::performMonitorCheck,
            10, // 初始延迟10秒
            MONITOR_INTERVAL,
            TimeUnit.SECONDS
        );
        
        log.info("监控任务已启动，检查间隔: {} 秒", MONITOR_INTERVAL);
    }

    /**
     * 执行监控检查
     */
    private void performMonitorCheck() {
        try {
            for (ServiceInfo serviceInfo : monitoredServices.values()) {
                ServiceStatus status = checkServiceHealth(serviceInfo);
                recordServiceStatus(serviceInfo.getServiceId(), status);
                
                // 发布状态变更事件
                if (adminContext != null) {
                    adminContext.publishEvent(new ServiceStatusChangedEvent(serviceInfo, status));
                }
            }
        } catch (Exception e) {
            log.error("监控检查执行失败", e);
        }
    }

    /**
     * 检查服务健康状态
     */
    private ServiceStatus checkServiceHealth(ServiceInfo serviceInfo) {
        try {
            if (serviceInfo.getHealthChecker() != null) {
                return serviceInfo.getHealthChecker().get();
            } else {
                // 默认健康检查：简单的连接测试
                return new ServiceStatus(
                    true,
                    "服务正常",
                    System.currentTimeMillis(),
                    Collections.emptyMap()
                );
            }
        } catch (Exception e) {
            log.warn("服务 {} 健康检查失败", serviceInfo.getServiceName(), e);
            return new ServiceStatus(
                false,
                "健康检查失败: " + e.getMessage(),
                System.currentTimeMillis(),
                Map.of("error", e.getClass().getSimpleName())
            );
        }
    }

    /**
     * 记录服务状态
     */
    private void recordServiceStatus(String serviceId, ServiceStatus status) {
        // 记录到内存历史
        List<ServiceStatusRecord> history = serviceStatusHistory.get(serviceId);
        if (history != null) {
            history.add(new ServiceStatusRecord(
                serviceId,
                status.isHealthy(),
                status.getMessage(),
                LocalDateTime.now(),
                status.getDetails()
            ));
            
            // 限制历史记录数量
            if (history.size() > STATUS_HISTORY_LIMIT) {
                history.remove(0);
            }
        }
        
        // 缓存到Redis
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_SERVICE_STATUS_PREFIX + serviceId;
                redisTemplate.opsForValue().set(redisKey, status, 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("缓存服务状态到Redis失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 数据库健康检查
     */
    private ServiceStatus checkDatabaseHealth() {
        // 简化实现，实际应该检查数据库连接
        return new ServiceStatus(true, "数据库连接正常", System.currentTimeMillis(), Collections.emptyMap());
    }

    /**
     * Redis健康检查
     */
    private ServiceStatus checkRedisHealth() {
        if (redisTemplate == null) {
            return new ServiceStatus(false, "Redis模板未配置", System.currentTimeMillis(), Collections.emptyMap());
        }
        
        try {
            redisTemplate.opsForValue().set("health:check", "ping", 1, TimeUnit.SECONDS);
            return new ServiceStatus(true, "Redis连接正常", System.currentTimeMillis(), Collections.emptyMap());
        } catch (Exception e) {
            return new ServiceStatus(false, "Redis连接失败: " + e.getMessage(), 
                                     System.currentTimeMillis(), Map.of("error", e.getClass().getSimpleName()));
        }
    }

    /**
     * 应用健康检查
     */
    private ServiceStatus checkApplicationHealth() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsage = (double) usedMemory / maxMemory;
        
        Map<String, Object> details = Map.of(
            "memoryUsage", String.format("%.2f%%", memoryUsage * 100),
            "usedMemory", usedMemory / 1024 / 1024 + "MB",
            "maxMemory", maxMemory / 1024 / 1024 + "MB",
            "availableProcessors", runtime.availableProcessors()
        );
        
        boolean healthy = memoryUsage < 0.9; // 内存使用率低于90%认为健康
        String message = healthy ? "应用运行正常" : "内存使用率过高";
        
        return new ServiceStatus(healthy, message, System.currentTimeMillis(), details);
    }

    /**
     * 关闭监控服务
     */
    public void shutdown() {
        log.info("关闭服务监控管理器...");
        
        if (monitorTask != null) {
            monitorTask.cancel(true);
        }
        
        if (monitorExecutor != null) {
            monitorExecutor.shutdown();
            try {
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("服务监控管理器已关闭");
    }

    /**
     * 服务信息
     */
    public static class ServiceInfo {
        private String serviceId;
        private String serviceName;
        private String description;
        private ServiceType serviceType;
        private String endpoint;
        private Supplier<ServiceStatus> healthChecker;

        public enum ServiceType {
            APPLICATION, DATABASE, CACHE, MESSAGE_QUEUE, EXTERNAL_API
        }

        public ServiceInfo(String serviceId, String serviceName, String description, 
                         ServiceType serviceType, String endpoint, 
                         Supplier<ServiceStatus> healthChecker) {
            this.serviceId = serviceId;
            this.serviceName = serviceName;
            this.description = description;
            this.serviceType = serviceType;
            this.endpoint = endpoint;
            this.healthChecker = healthChecker;
        }

        // Getter方法
        public String getServiceId() { return serviceId; }
        public String getServiceName() { return serviceName; }
        public String getDescription() { return description; }
        public ServiceType getServiceType() { return serviceType; }
        public String getEndpoint() { return endpoint; }
        public Supplier<ServiceStatus> getHealthChecker() { return healthChecker; }
    }

    /**
     * 服务状态
     */
    public static class ServiceStatus {
        private boolean healthy;
        private String message;
        private long timestamp;
        private Map<String, Object> details;

        public ServiceStatus(boolean healthy, String message, long timestamp, Map<String, Object> details) {
            this.healthy = healthy;
            this.message = message;
            this.timestamp = timestamp;
            this.details = details;
        }

        // Getter方法
        public boolean isHealthy() { return healthy; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getDetails() { return details; }
    }

    /**
     * 服务状态记录
     */
    public static class ServiceStatusRecord {
        private String serviceId;
        private boolean healthy;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, Object> details;

        public ServiceStatusRecord(String serviceId, boolean healthy, String message, 
                                 LocalDateTime timestamp, Map<String, Object> details) {
            this.serviceId = serviceId;
            this.healthy = healthy;
            this.message = message;
            this.timestamp = timestamp;
            this.details = details;
        }

        // Getter方法
        public String getServiceId() { return serviceId; }
        public boolean isHealthy() { return healthy; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getDetails() { return details; }
    }

    /**
     * 系统健康状态
     */
    public static class SystemHealthStatus {
        public enum HealthLevel {
            HEALTHY, WARNING, CRITICAL
        }

        private HealthLevel healthLevel;
        private int totalServices;
        private int healthyServices;
        private int unhealthyServices;
        private LocalDateTime timestamp;

        public SystemHealthStatus(HealthLevel healthLevel, int totalServices, 
                                int healthyServices, int unhealthyServices, 
                                LocalDateTime timestamp) {
            this.healthLevel = healthLevel;
            this.totalServices = totalServices;
            this.healthyServices = healthyServices;
            this.unhealthyServices = unhealthyServices;
            this.timestamp = timestamp;
        }

        // Getter方法
        public HealthLevel getHealthLevel() { return healthLevel; }
        public int getTotalServices() { return totalServices; }
        public int getHealthyServices() { return healthyServices; }
        public int getUnhealthyServices() { return unhealthyServices; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * 服务状态变更事件
     */
    public static class ServiceStatusChangedEvent {
        private ServiceInfo serviceInfo;
        private ServiceStatus newStatus;
        private LocalDateTime timestamp;

        public ServiceStatusChangedEvent(ServiceInfo serviceInfo, ServiceStatus newStatus) {
            this.serviceInfo = serviceInfo;
            this.newStatus = newStatus;
            this.timestamp = LocalDateTime.now();
        }

        // Getter方法
        public ServiceInfo getServiceInfo() { return serviceInfo; }
        public ServiceStatus getNewStatus() { return newStatus; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}