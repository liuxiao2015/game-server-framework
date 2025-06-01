/*
 * 文件名: ServiceContainer.java
 * 用途: 服务容器管理
 * 内容: 
 *   - Docker容器集成
 *   - 服务生命周期管理
 *   - 网络配置和端口管理
 *   - 资源限制和监控
 *   - 日志收集和管理
 * 技术选型: 
 *   - TestContainers
 *   - Docker API
 *   - 服务发现
 *   - 健康检查
 * 依赖关系: 
 *   - 被IntegrationTestRunner使用
 *   - 依赖Docker环境
 *   - 与TestEnvironment集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.integration;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务容器管理器
 * <p>
 * 提供Docker容器的管理功能，包括服务启动、停止、网络配置、
 * 资源监控和日志收集等。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
public class ServiceContainer {
    
    /**
     * 运行中的服务实例
     */
    private final Map<String, ServiceInstance> runningServices;
    
    /**
     * 服务配置
     */
    private final Map<String, ServiceConfiguration> serviceConfigurations;
    
    /**
     * 网络配置
     */
    private final NetworkConfiguration networkConfiguration;
    
    /**
     * 构造函数
     */
    public ServiceContainer() {
        this.runningServices = new ConcurrentHashMap<>();
        this.serviceConfigurations = new ConcurrentHashMap<>();
        this.networkConfiguration = new NetworkConfiguration();
        
        // 初始化预定义服务配置
        initializePredefinedServices();
    }
    
    /**
     * 启动服务
     * 
     * @param serviceName 服务名称
     * @param environment 测试环境
     * @return 服务实例
     */
    public ServiceInstance startService(String serviceName, TestEnvironment environment) {
        log.info("启动服务: {}", serviceName);
        
        try {
            // 检查服务是否已运行
            if (runningServices.containsKey(serviceName)) {
                log.warn("服务 {} 已在运行中", serviceName);
                return runningServices.get(serviceName);
            }
            
            // 获取服务配置
            ServiceConfiguration config = serviceConfigurations.get(serviceName);
            if (config == null) {
                throw new IllegalArgumentException("未找到服务配置: " + serviceName);
            }
            
            // 创建服务实例
            ServiceInstance instance = createServiceInstance(config, environment);
            
            // 启动容器
            startContainer(instance);
            
            // 等待服务就绪
            waitForServiceReady(instance);
            
            // 注册服务实例
            runningServices.put(serviceName, instance);
            
            log.info("服务 {} 启动成功，端口: {}", serviceName, instance.getPort());
            return instance;
            
        } catch (Exception e) {
            log.error("启动服务失败: {}", serviceName, e);
            throw new RuntimeException("启动服务失败: " + serviceName, e);
        }
    }
    
    /**
     * 停止服务
     * 
     * @param serviceName 服务名称
     */
    public void stopService(String serviceName) {
        log.info("停止服务: {}", serviceName);
        
        ServiceInstance instance = runningServices.remove(serviceName);
        if (instance != null) {
            try {
                stopContainer(instance);
                log.info("服务 {} 已停止", serviceName);
            } catch (Exception e) {
                log.error("停止服务失败: {}", serviceName, e);
            }
        }
    }
    
    /**
     * 停止环境中的所有服务
     * 
     * @param environment 测试环境
     */
    public void stopAllServices(TestEnvironment environment) {
        log.info("停止环境中的所有服务: {}", environment.getEnvironmentId());
        
        List<String> servicesToStop = runningServices.entrySet().stream()
            .filter(entry -> entry.getValue().getEnvironmentId().equals(environment.getEnvironmentId()))
            .map(Map.Entry::getKey)
            .toList();
        
        servicesToStop.forEach(this::stopService);
    }
    
    /**
     * 获取服务实例
     * 
     * @param serviceName 服务名称
     * @return 服务实例
     */
    public ServiceInstance getServiceInstance(String serviceName) {
        return runningServices.get(serviceName);
    }
    
    /**
     * 注册服务配置
     * 
     * @param serviceName 服务名称
     * @param configuration 服务配置
     */
    public void registerServiceConfiguration(String serviceName, ServiceConfiguration configuration) {
        serviceConfigurations.put(serviceName, configuration);
        log.debug("注册服务配置: {}", serviceName);
    }
    
    /**
     * 检查服务是否运行
     * 
     * @param serviceName 服务名称
     * @return 是否运行
     */
    public boolean isServiceRunning(String serviceName) {
        ServiceInstance instance = runningServices.get(serviceName);
        return instance != null && instance.isRunning();
    }
    
    /**
     * 获取所有运行中的服务
     * 
     * @return 运行中的服务映射
     */
    public Map<String, ServiceInstance> getRunningServices() {
        return Collections.unmodifiableMap(runningServices);
    }
    
    /**
     * 关闭服务容器
     */
    public void shutdown() {
        log.info("关闭服务容器...");
        
        // 停止所有运行中的服务
        List<String> allServices = new ArrayList<>(runningServices.keySet());
        allServices.forEach(this::stopService);
        
        log.info("服务容器已关闭");
    }
    
    /**
     * 初始化预定义服务配置
     */
    private void initializePredefinedServices() {
        // Redis配置
        registerServiceConfiguration("redis", ServiceConfiguration.builder()
            .serviceName("redis")
            .imageName("redis:7-alpine")
            .port(6379)
            .startupTimeout(Duration.ofMinutes(1))
            .healthCheckCommand("redis-cli ping")
            .build());
        
        // MySQL配置
        registerServiceConfiguration("mysql", ServiceConfiguration.builder()
            .serviceName("mysql")
            .imageName("mysql:8.0")
            .port(3306)
            .startupTimeout(Duration.ofMinutes(2))
            .environmentVariable("MYSQL_ROOT_PASSWORD", "test123")
            .environmentVariable("MYSQL_DATABASE", "testdb")
            .healthCheckCommand("mysqladmin ping -h localhost")
            .build());
        
        log.debug("预定义服务配置初始化完成");
    }
    
    /**
     * 创建服务实例
     * 
     * @param config 服务配置
     * @param environment 测试环境
     * @return 服务实例
     */
    private ServiceInstance createServiceInstance(ServiceConfiguration config, TestEnvironment environment) {
        return ServiceInstance.builder()
            .serviceName(config.getServiceName())
            .imageName(config.getImageName())
            .port(config.getPort())
            .environmentId(environment.getEnvironmentId())
            .configuration(config)
            .build();
    }
    
    /**
     * 启动容器
     * 
     * @param instance 服务实例
     */
    private void startContainer(ServiceInstance instance) {
        log.debug("启动容器: {}", instance.getServiceName());
        
        // 模拟容器启动（实际实现中会使用TestContainers或Docker API）
        instance.setContainerId("container_" + instance.getServiceName() + "_" + System.currentTimeMillis());
        instance.setRunning(true);
        instance.setStartTime(System.currentTimeMillis());
        
        // 分配端口
        int assignedPort = networkConfiguration.allocatePort();
        instance.setActualPort(assignedPort);
        
        log.debug("容器 {} 启动成功，分配端口: {}", instance.getServiceName(), assignedPort);
    }
    
    /**
     * 等待服务就绪
     * 
     * @param instance 服务实例
     */
    private void waitForServiceReady(ServiceInstance instance) {
        log.debug("等待服务就绪: {}", instance.getServiceName());
        
        Duration timeout = instance.getConfiguration().getStartupTimeout();
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeout.toMillis()) {
            if (checkServiceHealth(instance)) {
                log.debug("服务 {} 已就绪", instance.getServiceName());
                return;
            }
            
            try {
                Thread.sleep(1000); // 等待1秒后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待服务就绪被中断", e);
            }
        }
        
        throw new RuntimeException("服务 " + instance.getServiceName() + " 启动超时");
    }
    
    /**
     * 检查服务健康状态
     * 
     * @param instance 服务实例
     * @return 是否健康
     */
    private boolean checkServiceHealth(ServiceInstance instance) {
        // 模拟健康检查（实际实现中会执行健康检查命令）
        return instance.isRunning();
    }
    
    /**
     * 停止容器
     * 
     * @param instance 服务实例
     */
    private void stopContainer(ServiceInstance instance) {
        log.debug("停止容器: {}", instance.getServiceName());
        
        try {
            // 模拟容器停止
            instance.setRunning(false);
            
            // 释放端口
            if (instance.getActualPort() > 0) {
                networkConfiguration.releasePort(instance.getActualPort());
            }
            
            log.debug("容器 {} 已停止", instance.getServiceName());
            
        } catch (Exception e) {
            log.error("停止容器失败: {}", instance.getServiceName(), e);
        }
    }
    
    /**
     * 网络配置
     */
    private static class NetworkConfiguration {
        private final Set<Integer> allocatedPorts = new HashSet<>();
        private int nextPort = 10000;
        
        public synchronized int allocatePort() {
            while (allocatedPorts.contains(nextPort)) {
                nextPort++;
            }
            allocatedPorts.add(nextPort);
            return nextPort++;
        }
        
        public synchronized void releasePort(int port) {
            allocatedPorts.remove(port);
        }
    }
}