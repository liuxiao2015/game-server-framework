/*
 * 文件名: MultiInstanceConfig.java
 * 用途: 多实例部署配置
 * 内容: 
 *   - 多实例配置定义
 *   - 实例ID自动分配
 *   - 端口自动递增管理
 *   - 实例隔离配置
 * 技术选型: 
 *   - Spring Boot配置绑定
 *   - YAML配置解析
 *   - 网络端口管理
 * 依赖关系: 
 *   - 与ConfigManager集成
 *   - 被ServiceManager使用
 */
package com.lx.gameserver.launcher;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多实例部署配置
 * <p>
 * 支持同一服务的多实例部署，提供以下功能：
 * 1. 实例数量配置
 * 2. 端口自动分配
 * 3. 实例ID管理
 * 4. 配置隔离
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-06-01
 */
@Configuration
@ConfigurationProperties(prefix = "game.multi-instance")
public class MultiInstanceConfig {
    
    /**
     * 是否启用多实例模式
     */
    private boolean enabled = false;
    
    /**
     * 服务实例配置映射
     */
    private Map<String, ServiceInstanceConfig> services = new HashMap<>();
    
    /**
     * 实例ID生成器
     */
    private static final AtomicInteger instanceIdGenerator = new AtomicInteger(1);
    
    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Map<String, ServiceInstanceConfig> getServices() {
        return services;
    }
    
    public void setServices(Map<String, ServiceInstanceConfig> services) {
        this.services = services;
    }
    
    /**
     * 获取服务实例配置
     */
    public ServiceInstanceConfig getServiceConfig(String serviceName) {
        return services.get(serviceName);
    }
    
    /**
     * 生成新的实例ID
     */
    public String generateInstanceId(String serviceName) {
        return serviceName + "-" + instanceIdGenerator.getAndIncrement();
    }
    
    /**
     * 计算实例端口
     */
    public int calculateInstancePort(String serviceName, int instanceIndex) {
        ServiceInstanceConfig config = getServiceConfig(serviceName);
        if (config == null) {
            throw new IllegalArgumentException("未找到服务配置: " + serviceName);
        }
        
        return config.getBasePort() + (instanceIndex * config.getPortIncrement());
    }
    
    /**
     * 获取所有实例信息
     */
    public List<InstanceInfo> getAllInstances() {
        List<InstanceInfo> instances = new ArrayList<>();
        
        for (Map.Entry<String, ServiceInstanceConfig> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            ServiceInstanceConfig config = entry.getValue();
            
            for (int i = 0; i < config.getInstances(); i++) {
                InstanceInfo instance = new InstanceInfo();
                instance.setServiceName(serviceName);
                instance.setInstanceId(generateInstanceId(serviceName));
                instance.setInstanceIndex(i);
                instance.setPort(calculateInstancePort(serviceName, i));
                instance.setLogPath(generateLogPath(serviceName, i));
                instances.add(instance);
            }
        }
        
        return instances;
    }
    
    /**
     * 生成日志文件路径
     */
    private String generateLogPath(String serviceName, int instanceIndex) {
        return String.format("logs/%s/instance-%d/%s.log", serviceName, instanceIndex, serviceName);
    }
    
    /**
     * 服务实例配置类
     */
    public static class ServiceInstanceConfig {
        /**
         * 实例数量
         */
        private int instances = 1;
        
        /**
         * 基础端口
         */
        private int basePort = 9000;
        
        /**
         * 端口递增值
         */
        private int portIncrement = 1;
        
        /**
         * 是否启用负载均衡
         */
        private boolean loadBalanceEnabled = true;
        
        /**
         * 负载均衡策略
         */
        private LoadBalanceStrategy loadBalanceStrategy = LoadBalanceStrategy.ROUND_ROBIN;
        
        /**
         * 健康检查间隔（秒）
         */
        private int healthCheckInterval = 30;
        
        /**
         * 是否启用实例隔离
         */
        private boolean isolationEnabled = true;
        
        // Getters and Setters
        public int getInstances() {
            return instances;
        }
        
        public void setInstances(int instances) {
            this.instances = instances;
        }
        
        public int getBasePort() {
            return basePort;
        }
        
        public void setBasePort(int basePort) {
            this.basePort = basePort;
        }
        
        public int getPortIncrement() {
            return portIncrement;
        }
        
        public void setPortIncrement(int portIncrement) {
            this.portIncrement = portIncrement;
        }
        
        public boolean isLoadBalanceEnabled() {
            return loadBalanceEnabled;
        }
        
        public void setLoadBalanceEnabled(boolean loadBalanceEnabled) {
            this.loadBalanceEnabled = loadBalanceEnabled;
        }
        
        public LoadBalanceStrategy getLoadBalanceStrategy() {
            return loadBalanceStrategy;
        }
        
        public void setLoadBalanceStrategy(LoadBalanceStrategy loadBalanceStrategy) {
            this.loadBalanceStrategy = loadBalanceStrategy;
        }
        
        public int getHealthCheckInterval() {
            return healthCheckInterval;
        }
        
        public void setHealthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
        }
        
        public boolean isIsolationEnabled() {
            return isolationEnabled;
        }
        
        public void setIsolationEnabled(boolean isolationEnabled) {
            this.isolationEnabled = isolationEnabled;
        }
    }
    
    /**
     * 实例信息类
     */
    public static class InstanceInfo {
        private String serviceName;
        private String instanceId;
        private int instanceIndex;
        private int port;
        private String logPath;
        private InstanceStatus status = InstanceStatus.STOPPED;
        
        // Getters and Setters
        public String getServiceName() {
            return serviceName;
        }
        
        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public String getInstanceId() {
            return instanceId;
        }
        
        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }
        
        public int getInstanceIndex() {
            return instanceIndex;
        }
        
        public void setInstanceIndex(int instanceIndex) {
            this.instanceIndex = instanceIndex;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public String getLogPath() {
            return logPath;
        }
        
        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }
        
        public InstanceStatus getStatus() {
            return status;
        }
        
        public void setStatus(InstanceStatus status) {
            this.status = status;
        }
    }
    
    /**
     * 负载均衡策略枚举
     */
    public enum LoadBalanceStrategy {
        ROUND_ROBIN("轮询"),
        RANDOM("随机"),
        LEAST_CONNECTIONS("最少连接"),
        WEIGHTED_RESPONSE_TIME("响应时间权重"),
        CONSISTENT_HASH("一致性哈希");
        
        private final String description;
        
        LoadBalanceStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 实例状态枚举
     */
    public enum InstanceStatus {
        STOPPED("已停止"),
        STARTING("启动中"),
        RUNNING("运行中"),
        STOPPING("停止中"),
        ERROR("错误"),
        UNKNOWN("未知");
        
        private final String description;
        
        InstanceStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}