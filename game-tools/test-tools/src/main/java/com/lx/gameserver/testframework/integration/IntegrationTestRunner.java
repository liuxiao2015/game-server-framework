/*
 * 文件名: IntegrationTestRunner.java
 * 用途: 集成测试运行器
 * 内容: 
 *   - 集成测试环境管理
 *   - 服务启动和依赖管理
 *   - 测试容器管理
 *   - 环境隔离和清理
 *   - 端到端测试支持
 * 技术选型: 
 *   - TestContainers
 *   - Docker集成
 *   - Spring Test
 *   - 服务编排
 * 依赖关系: 
 *   - 依赖TestContext环境管理
 *   - 依赖ServiceContainer
 *   - 与TestFramework集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.integration;

import com.lx.gameserver.testframework.core.TestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 集成测试运行器
 * <p>
 * 提供集成测试的环境管理、服务启动、依赖管理和测试执行功能，
 * 支持容器化测试环境和端到端测试场景。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@Component
public class IntegrationTestRunner {
    
    /**
     * 服务容器管理器
     */
    private final ServiceContainer serviceContainer;
    
    /**
     * 测试场景集合
     */
    private final Map<String, TestScenario> testScenarios;
    
    /**
     * 运行中的测试环境
     */
    private final Map<String, TestEnvironment> activeEnvironments;
    
    /**
     * 线程池执行器
     */
    private final ExecutorService executorService;
    
    /**
     * 配置管理
     */
    private final Map<String, Object> configuration;
    
    /**
     * 构造函数
     */
    public IntegrationTestRunner() {
        this.serviceContainer = new ServiceContainer();
        this.testScenarios = new ConcurrentHashMap<>();
        this.activeEnvironments = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.configuration = new ConcurrentHashMap<>();
        
        // 初始化默认配置
        initializeDefaultConfiguration();
    }
    
    /**
     * 运行集成测试场景
     * 
     * @param scenarioName 场景名称
     * @param context 测试上下文
     * @return 测试结果
     */
    public CompletableFuture<IntegrationTestResult> runScenario(String scenarioName, TestContext context) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("开始运行集成测试场景: {}", scenarioName);
            Instant startTime = Instant.now();
            
            try {
                // 获取测试场景
                TestScenario scenario = testScenarios.get(scenarioName);
                if (scenario == null) {
                    throw new IllegalArgumentException("找不到测试场景: " + scenarioName);
                }
                
                // 准备测试环境
                TestEnvironment environment = prepareTestEnvironment(scenario, context);
                
                // 启动依赖服务
                startDependentServices(scenario, environment);
                
                // 执行测试场景
                ScenarioResult result = executeScenario(scenario, environment, context);
                
                // 清理环境
                cleanupEnvironment(environment);
                
                Duration duration = Duration.between(startTime, Instant.now());
                log.info("集成测试场景 {} 执行完成: 成功={}", scenarioName, result.isSuccess());
                
                return IntegrationTestResult.builder()
                    .scenarioName(scenarioName)
                    .success(result.isSuccess())
                    .duration(duration)
                    .details(result.getDetails())
                    .build();
                
            } catch (Exception e) {
                Duration duration = Duration.between(startTime, Instant.now());
                log.error("集成测试场景 {} 执行失败", scenarioName, e);
                
                return IntegrationTestResult.builder()
                    .scenarioName(scenarioName)
                    .success(false)
                    .duration(duration)
                    .error(e.getMessage())
                    .exception(e)
                    .build();
            }
        }, executorService);
    }
    
    /**
     * 注册测试场景
     * 
     * @param scenario 测试场景
     */
    public void registerScenario(TestScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("测试场景不能为空");
        }
        
        testScenarios.put(scenario.getName(), scenario);
        log.info("注册集成测试场景: {}", scenario.getName());
    }
    
    /**
     * 运行所有注册的测试场景
     * 
     * @param context 测试上下文
     * @return 所有测试结果
     */
    public CompletableFuture<List<IntegrationTestResult>> runAllScenarios(TestContext context) {
        List<CompletableFuture<IntegrationTestResult>> futures = testScenarios.keySet()
            .stream()
            .map(scenarioName -> runScenario(scenarioName, context))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    /**
     * 设置配置
     * 
     * @param key 配置键
     * @param value 配置值
     */
    public void setConfiguration(String key, Object value) {
        configuration.put(key, value);
    }
    
    /**
     * 获取配置
     * 
     * @param key 配置键
     * @param <T> 值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(String key) {
        return (T) configuration.get(key);
    }
    
    /**
     * 获取服务容器
     * 
     * @return 服务容器
     */
    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }
    
    /**
     * 关闭集成测试运行器
     */
    public void shutdown() {
        log.info("关闭集成测试运行器...");
        
        try {
            // 清理所有活动环境
            activeEnvironments.values().forEach(this::cleanupEnvironment);
            activeEnvironments.clear();
            
            // 关闭服务容器
            serviceContainer.shutdown();
            
            // 关闭线程池
            if (!executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            log.info("集成测试运行器已关闭");
            
        } catch (Exception e) {
            log.error("关闭集成测试运行器失败", e);
        }
    }
    
    /**
     * 初始化默认配置
     */
    private void initializeDefaultConfiguration() {
        setConfiguration("environment.timeout", Duration.ofMinutes(5));
        setConfiguration("service.startup.timeout", Duration.ofMinutes(2));
        setConfiguration("cleanup.auto", true);
        setConfiguration("parallel.execution", false);
        
        log.debug("集成测试运行器默认配置初始化完成");
    }
    
    /**
     * 准备测试环境
     * 
     * @param scenario 测试场景
     * @param context 测试上下文
     * @return 测试环境
     */
    private TestEnvironment prepareTestEnvironment(TestScenario scenario, TestContext context) {
        log.debug("准备集成测试环境: {}", scenario.getName());
        
        String environmentId = generateEnvironmentId(scenario.getName());
        TestEnvironment environment = new TestEnvironment(environmentId, scenario.getName());
        
        // 配置环境
        environment.setConfiguration(scenario.getConfiguration());
        environment.setContext(context);
        
        // 注册到活动环境
        activeEnvironments.put(environmentId, environment);
        
        log.debug("集成测试环境准备完成: {}", environmentId);
        return environment;
    }
    
    /**
     * 启动依赖服务
     * 
     * @param scenario 测试场景
     * @param environment 测试环境
     */
    private void startDependentServices(TestScenario scenario, TestEnvironment environment) {
        log.debug("启动依赖服务: {}", scenario.getName());
        
        List<String> dependencies = scenario.getDependencies();
        for (String dependency : dependencies) {
            try {
                serviceContainer.startService(dependency, environment);
                log.debug("依赖服务 {} 启动成功", dependency);
            } catch (Exception e) {
                log.error("启动依赖服务失败: {}", dependency, e);
                throw new RuntimeException("启动依赖服务失败: " + dependency, e);
            }
        }
        
        log.debug("所有依赖服务启动完成");
    }
    
    /**
     * 执行测试场景
     * 
     * @param scenario 测试场景
     * @param environment 测试环境
     * @param context 测试上下文
     * @return 场景结果
     */
    private ScenarioResult executeScenario(TestScenario scenario, TestEnvironment environment, TestContext context) {
        log.debug("执行测试场景: {}", scenario.getName());
        
        try {
            // 执行场景逻辑
            scenario.execute(environment, context);
            
            return ScenarioResult.success("场景执行成功");
            
        } catch (Exception e) {
            log.error("测试场景执行失败: {}", scenario.getName(), e);
            return ScenarioResult.failure("场景执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理测试环境
     * 
     * @param environment 测试环境
     */
    private void cleanupEnvironment(TestEnvironment environment) {
        if (environment == null) {
            return;
        }
        
        log.debug("清理集成测试环境: {}", environment.getEnvironmentId());
        
        try {
            // 停止服务
            serviceContainer.stopAllServices(environment);
            
            // 清理资源
            environment.cleanup();
            
            // 从活动环境中移除
            activeEnvironments.remove(environment.getEnvironmentId());
            
            log.debug("集成测试环境清理完成: {}", environment.getEnvironmentId());
            
        } catch (Exception e) {
            log.error("清理集成测试环境失败: {}", environment.getEnvironmentId(), e);
        }
    }
    
    /**
     * 生成环境ID
     * 
     * @param scenarioName 场景名称
     * @return 环境ID
     */
    private String generateEnvironmentId(String scenarioName) {
        return String.format("env_%s_%d", scenarioName, System.currentTimeMillis());
    }
    
    /**
     * 场景结果
     */
    public static class ScenarioResult {
        private final boolean success;
        private final String details;
        private final Exception exception;
        
        private ScenarioResult(boolean success, String details, Exception exception) {
            this.success = success;
            this.details = details;
            this.exception = exception;
        }
        
        public static ScenarioResult success(String details) {
            return new ScenarioResult(true, details, null);
        }
        
        public static ScenarioResult failure(String details, Exception exception) {
            return new ScenarioResult(false, details, exception);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getDetails() { return details; }
        public Exception getException() { return exception; }
    }
    
    /**
     * 集成测试结果
     */
    public static class IntegrationTestResult {
        private final String scenarioName;
        private final boolean success;
        private final Duration duration;
        private final String details;
        private final String error;
        private final Exception exception;
        
        private IntegrationTestResult(Builder builder) {
            this.scenarioName = builder.scenarioName;
            this.success = builder.success;
            this.duration = builder.duration;
            this.details = builder.details;
            this.error = builder.error;
            this.exception = builder.exception;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getScenarioName() { return scenarioName; }
        public boolean isSuccess() { return success; }
        public Duration getDuration() { return duration; }
        public String getDetails() { return details; }
        public String getError() { return error; }
        public Exception getException() { return exception; }
        
        public static class Builder {
            private String scenarioName;
            private boolean success;
            private Duration duration;
            private String details;
            private String error;
            private Exception exception;
            
            public Builder scenarioName(String scenarioName) {
                this.scenarioName = scenarioName;
                return this;
            }
            
            public Builder success(boolean success) {
                this.success = success;
                return this;
            }
            
            public Builder duration(Duration duration) {
                this.duration = duration;
                return this;
            }
            
            public Builder details(String details) {
                this.details = details;
                return this;
            }
            
            public Builder error(String error) {
                this.error = error;
                return this;
            }
            
            public Builder exception(Exception exception) {
                this.exception = exception;
                return this;
            }
            
            public IntegrationTestResult build() {
                return new IntegrationTestResult(this);
            }
        }
    }
}