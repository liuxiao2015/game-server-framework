/*
 * 文件名: TestEnvironment.java
 * 用途: 集成测试环境
 * 内容: 
 *   - 测试环境状态管理
 *   - 资源分配和清理
 *   - 环境配置管理
 *   - 服务实例跟踪
 * 技术选型: 
 *   - 状态模式
 *   - 资源管理
 * 依赖关系: 
 *   - 被IntegrationTestRunner使用
 *   - 与ServiceContainer集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.integration;

import com.lx.gameserver.testframework.core.TestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集成测试环境
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
public class TestEnvironment {
    
    private final String environmentId;
    private final String scenarioName;
    private final Map<String, Object> configuration;
    private final Map<String, Object> resources;
    private TestContext context;
    
    public TestEnvironment(String environmentId, String scenarioName) {
        this.environmentId = environmentId;
        this.scenarioName = scenarioName;
        this.configuration = new ConcurrentHashMap<>();
        this.resources = new ConcurrentHashMap<>();
    }
    
    public String getEnvironmentId() { return environmentId; }
    public String getScenarioName() { return scenarioName; }
    
    public void setConfiguration(Map<String, Object> config) {
        if (config != null) {
            this.configuration.putAll(config);
        }
    }
    
    public void setContext(TestContext context) {
        this.context = context;
    }
    
    public TestContext getContext() {
        return context;
    }
    
    public void cleanup() {
        log.debug("清理测试环境资源: {}", environmentId);
        resources.clear();
        configuration.clear();
    }
}

/*
 * 文件名: ServiceConfiguration.java
 * 用途: 服务配置
 */
class ServiceConfiguration {
    private final String serviceName;
    private final String imageName;
    private final int port;
    private final java.time.Duration startupTimeout;
    private final Map<String, String> environmentVariables;
    private final String healthCheckCommand;
    
    private ServiceConfiguration(Builder builder) {
        this.serviceName = builder.serviceName;
        this.imageName = builder.imageName;
        this.port = builder.port;
        this.startupTimeout = builder.startupTimeout;
        this.environmentVariables = new HashMap<>(builder.environmentVariables);
        this.healthCheckCommand = builder.healthCheckCommand;
    }
    
    public static Builder builder() { return new Builder(); }
    
    // Getters
    public String getServiceName() { return serviceName; }
    public String getImageName() { return imageName; }
    public int getPort() { return port; }
    public java.time.Duration getStartupTimeout() { return startupTimeout; }
    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public String getHealthCheckCommand() { return healthCheckCommand; }
    
    public static class Builder {
        private String serviceName;
        private String imageName;
        private int port;
        private java.time.Duration startupTimeout = java.time.Duration.ofMinutes(2);
        private Map<String, String> environmentVariables = new HashMap<>();
        private String healthCheckCommand;
        
        public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
        public Builder imageName(String imageName) { this.imageName = imageName; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder startupTimeout(java.time.Duration timeout) { this.startupTimeout = timeout; return this; }
        public Builder environmentVariable(String key, String value) { 
            this.environmentVariables.put(key, value); return this; 
        }
        public Builder healthCheckCommand(String command) { this.healthCheckCommand = command; return this; }
        
        public ServiceConfiguration build() { return new ServiceConfiguration(this); }
    }
}

/*
 * 文件名: ServiceInstance.java
 * 用途: 服务实例
 */
class ServiceInstance {
    private final String serviceName;
    private final String imageName;
    private final int port;
    private final String environmentId;
    private final ServiceConfiguration configuration;
    
    private String containerId;
    private int actualPort;
    private boolean running;
    private long startTime;
    
    private ServiceInstance(Builder builder) {
        this.serviceName = builder.serviceName;
        this.imageName = builder.imageName;
        this.port = builder.port;
        this.environmentId = builder.environmentId;
        this.configuration = builder.configuration;
    }
    
    public static Builder builder() { return new Builder(); }
    
    // Getters and Setters
    public String getServiceName() { return serviceName; }
    public String getImageName() { return imageName; }
    public int getPort() { return port; }
    public String getEnvironmentId() { return environmentId; }
    public ServiceConfiguration getConfiguration() { return configuration; }
    
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    
    public int getActualPort() { return actualPort; }
    public void setActualPort(int actualPort) { this.actualPort = actualPort; }
    
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public static class Builder {
        private String serviceName;
        private String imageName;
        private int port;
        private String environmentId;
        private ServiceConfiguration configuration;
        
        public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
        public Builder imageName(String imageName) { this.imageName = imageName; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder environmentId(String environmentId) { this.environmentId = environmentId; return this; }
        public Builder configuration(ServiceConfiguration configuration) { this.configuration = configuration; return this; }
        
        public ServiceInstance build() { return new ServiceInstance(this); }
    }
}