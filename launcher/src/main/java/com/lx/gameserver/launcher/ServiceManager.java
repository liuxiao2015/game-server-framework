/*
 * 文件名: ServiceManager.java
 * 用途: 游戏服务器服务管理器
 * 内容: 
 *   - 统一管理各服务模块的启动和关闭
 *   - 提供服务生命周期管理
 *   - 监控服务状态和健康信息
 *   - 处理服务间的依赖关系
 * 技术选型: 
 *   - Spring框架生命周期管理
 *   - Java 21并发API
 *   - 异步服务启动和关闭
 * 依赖关系: 
 *   - 与Spring容器集成
 *   - 被Bootstrap启动类使用
 *   - 与HealthChecker协作监控服务状态
 */
package com.lx.gameserver.launcher;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 服务管理器
 * <p>
 * 负责游戏服务器中各个服务模块的生命周期管理，包括：
 * 1. 服务的启动顺序控制
 * 2. 服务的优雅关闭
 * 3. 服务状态监控
 * 4. 服务依赖关系管理
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
@Component
public class ServiceManager implements ApplicationListener<ContextClosedEvent> {
    
    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 服务管理器状态枚举
     */
    public enum ServiceManagerStatus {
        /** 初始化中 */
        INITIALIZING("初始化中"),
        /** 运行中 */
        RUNNING("运行中"),
        /** 关闭中 */
        SHUTTING_DOWN("关闭中"),
        /** 已关闭 */
        STOPPED("已关闭");
        
        private final String description;
        
        ServiceManagerStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 服务信息类
     */
    public static class ServiceInfo {
        private final String name;
        private final String description;
        private ServiceStatus status;
        private LocalDateTime startTime;
        private String errorMessage;
        
        public ServiceInfo(String name, String description) {
            this.name = name;
            this.description = description;
            this.status = ServiceStatus.STOPPED;
        }
        
        // Getters and setters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public ServiceStatus getStatus() { return status; }
        public void setStatus(ServiceStatus status) { this.status = status; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    /**
     * 服务状态枚举
     */
    public enum ServiceStatus {
        /** 已停止 */
        STOPPED("已停止"),
        /** 启动中 */
        STARTING("启动中"),
        /** 运行中 */
        RUNNING("运行中"),
        /** 错误 */
        ERROR("错误"),
        /** 关闭中 */
        STOPPING("关闭中");
        
        private final String description;
        
        ServiceStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 当前管理器状态
     */
    private volatile ServiceManagerStatus status = ServiceManagerStatus.INITIALIZING;
    
    /**
     * 管理的服务列表
     */
    private final List<ServiceInfo> managedServices = new ArrayList<>();
    
    /**
     * 线程池执行器
     */
    private ExecutorService executorService;
    
    /**
     * 服务管理器启动时间
     */
    private LocalDateTime startupTime;
    
    /**
     * 初始化服务管理器
     */
    @PostConstruct
    public void initialize() {
        startupTime = LocalDateTime.now();
        status = ServiceManagerStatus.INITIALIZING;
        
        System.out.println("服务管理器初始化开始 - " + startupTime.format(TIME_FORMATTER));
        
        try {
            // 初始化线程池
            executorService = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ServiceManager-Thread");
                t.setDaemon(true);
                return t;
            });
            
            // 注册核心服务
            registerCoreServices();
            
            // 启动所有服务
            startAllServices();
            
            status = ServiceManagerStatus.RUNNING;
            System.out.println("服务管理器初始化完成，管理 " + managedServices.size() + " 个服务");
            
        } catch (Exception e) {
            System.err.println("服务管理器初始化失败: " + e.getMessage());
            status = ServiceManagerStatus.STOPPED;
            throw new RuntimeException("服务管理器初始化失败", e);
        }
    }
    
    /**
     * 注册核心服务
     */
    private void registerCoreServices() {
        // 注册网络服务
        registerService("网络通信服务", "负责客户端连接和网络消息处理");
        
        // 注册数据库服务
        registerService("数据库服务", "负责数据持久化和缓存管理");
        
        // 注册游戏逻辑服务
        registerService("游戏逻辑服务", "负责核心游戏业务逻辑处理");
        
        // 注册监控服务
        registerService("监控服务", "负责系统监控和性能统计");
    }
    
    /**
     * 注册服务
     *
     * @param name        服务名称
     * @param description 服务描述
     */
    public void registerService(String name, String description) {
        ServiceInfo serviceInfo = new ServiceInfo(name, description);
        managedServices.add(serviceInfo);
        System.out.println("注册服务: " + name + " - " + description);
    }
    
    /**
     * 启动所有服务
     */
    private void startAllServices() {
        System.out.println("开始启动所有注册的服务...");
        
        List<CompletableFuture<Void>> startupFutures = new ArrayList<>();
        
        for (ServiceInfo service : managedServices) {
            CompletableFuture<Void> future = CompletableFuture
                    .runAsync(() -> startService(service), executorService)
                    .exceptionally(throwable -> {
                        service.setStatus(ServiceStatus.ERROR);
                        service.setErrorMessage(throwable.getMessage());
                        System.err.println("服务启动失败: " + service.getName() + " - " + throwable.getMessage());
                        return null;
                    });
            startupFutures.add(future);
        }
        
        // 等待所有服务启动完成
        try {
            CompletableFuture.allOf(startupFutures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
            System.out.println("所有服务启动完成");
        } catch (Exception e) {
            System.err.println("等待服务启动时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 启动单个服务
     *
     * @param service 服务信息
     */
    private void startService(ServiceInfo service) {
        try {
            service.setStatus(ServiceStatus.STARTING);
            service.setStartTime(LocalDateTime.now());
            
            // 模拟服务启动过程
            Thread.sleep(100 + (int)(Math.random() * 500));
            
            service.setStatus(ServiceStatus.RUNNING);
            System.out.println("服务启动成功: " + service.getName());
            
        } catch (Exception e) {
            service.setStatus(ServiceStatus.ERROR);
            service.setErrorMessage(e.getMessage());
            System.err.println("服务启动失败: " + service.getName() + " - " + e.getMessage());
        }
    }
    
    /**
     * 关闭服务管理器
     */
    @PreDestroy
    public void shutdown() {
        if (status == ServiceManagerStatus.SHUTTING_DOWN || status == ServiceManagerStatus.STOPPED) {
            return;
        }
        
        status = ServiceManagerStatus.SHUTTING_DOWN;
        System.out.println("服务管理器开始关闭 - " + LocalDateTime.now().format(TIME_FORMATTER));
        
        try {
            // 关闭所有服务
            shutdownAllServices();
            
            // 关闭线程池
            if (executorService != null) {
                executorService.shutdown();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            }
            
            status = ServiceManagerStatus.STOPPED;
            System.out.println("服务管理器关闭完成");
            
        } catch (Exception e) {
            System.err.println("服务管理器关闭时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 关闭所有服务
     */
    private void shutdownAllServices() {
        System.out.println("开始关闭所有服务...");
        
        // 逆序关闭服务
        for (int i = managedServices.size() - 1; i >= 0; i--) {
            ServiceInfo service = managedServices.get(i);
            shutdownService(service);
        }
        
        System.out.println("所有服务关闭完成");
    }
    
    /**
     * 关闭单个服务
     *
     * @param service 服务信息
     */
    private void shutdownService(ServiceInfo service) {
        try {
            if (service.getStatus() == ServiceStatus.RUNNING) {
                service.setStatus(ServiceStatus.STOPPING);
                
                // 模拟服务关闭过程
                Thread.sleep(50 + (int)(Math.random() * 200));
                
                service.setStatus(ServiceStatus.STOPPED);
                System.out.println("服务关闭成功: " + service.getName());
            }
        } catch (Exception e) {
            System.err.println("服务关闭失败: " + service.getName() + " - " + e.getMessage());
        }
    }
    
    /**
     * 处理应用程序关闭事件
     *
     * @param event 关闭事件
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        System.out.println("收到应用程序关闭事件，开始优雅关闭服务...");
        shutdown();
    }
    
    /**
     * 获取服务管理器状态
     *
     * @return 当前状态
     */
    public ServiceManagerStatus getStatus() {
        return status;
    }
    
    /**
     * 获取管理的服务列表
     *
     * @return 服务列表的只读副本
     */
    public List<ServiceInfo> getManagedServices() {
        return new ArrayList<>(managedServices);
    }
    
    /**
     * 获取运行时长
     *
     * @return 运行时长描述
     */
    public String getUptime() {
        if (startupTime == null) {
            return "未启动";
        }
        
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
     * 获取服务统计信息
     *
     * @return 统计信息字符串
     */
    public String getServiceStatistics() {
        long runningCount = managedServices.stream()
                .mapToLong(s -> s.getStatus() == ServiceStatus.RUNNING ? 1 : 0)
                .sum();
        
        long errorCount = managedServices.stream()
                .mapToLong(s -> s.getStatus() == ServiceStatus.ERROR ? 1 : 0)
                .sum();
        
        return String.format("总服务数: %d, 运行中: %d, 错误: %d", 
                managedServices.size(), runningCount, errorCount);
    }
}