/*
 * 文件名: TestContext.java
 * 用途: 游戏服务器测试上下文管理
 * 内容: 
 *   - 测试环境配置和状态管理
 *   - 测试数据和资源管理
 *   - 服务模拟和依赖注入
 *   - 测试生命周期跟踪
 *   - 资源清理和回收机制
 * 技术选型: 
 *   - Java 21 API
 *   - Spring框架依赖注入
 *   - 线程安全设计
 *   - 资源管理模式
 * 依赖关系: 
 *   - 依赖配置管理模块
 *   - 依赖数据管理模块
 *   - 依赖服务容器模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏服务器测试上下文
 * <p>
 * 管理测试执行过程中的环境配置、数据状态、资源分配等，
 * 提供统一的测试环境管理和资源清理能力。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@Component
public class TestContext {
    
    /**
     * 测试配置属性
     */
    private final Map<String, Object> properties;
    
    /**
     * 测试数据存储
     */
    private final Map<String, Object> testData;
    
    /**
     * 服务模拟对象
     */
    private final Map<String, Object> mockServices;
    
    /**
     * 资源清理回调
     */
    private final List<Runnable> cleanupCallbacks;
    
    /**
     * 测试状态追踪
     */
    private final Map<String, TestState> testStates;
    
    /**
     * 上下文初始化状态
     */
    private final AtomicBoolean initialized;
    
    /**
     * 上下文创建时间
     */
    private final long createdTime;
    
    /**
     * 测试计数器
     */
    private final AtomicLong testCounter;
    
    /**
     * 测试状态枚举
     */
    public enum TestState {
        /** 准备中 */
        PREPARING,
        /** 运行中 */
        RUNNING,
        /** 成功 */
        SUCCESS,
        /** 失败 */
        FAILED,
        /** 跳过 */
        SKIPPED,
        /** 清理中 */
        CLEANING
    }
    
    /**
     * 构造函数
     */
    public TestContext() {
        this.properties = new ConcurrentHashMap<>();
        this.testData = new ConcurrentHashMap<>();
        this.mockServices = new ConcurrentHashMap<>();
        this.cleanupCallbacks = Collections.synchronizedList(new ArrayList<>());
        this.testStates = new ConcurrentHashMap<>();
        this.initialized = new AtomicBoolean(false);
        this.createdTime = System.currentTimeMillis();
        this.testCounter = new AtomicLong(0);
    }
    
    /**
     * 初始化测试上下文
     * 
     * @throws Exception 初始化异常
     */
    public void initialize() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            log.info("正在初始化测试上下文...");
            
            try {
                // 加载默认配置
                loadDefaultProperties();
                
                // 初始化测试环境
                initializeTestEnvironment();
                
                log.info("测试上下文初始化完成");
                
            } catch (Exception e) {
                initialized.set(false);
                log.error("测试上下文初始化失败", e);
                throw e;
            }
        }
    }
    
    /**
     * 设置配置属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    public void setProperty(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("属性键不能为空");
        }
        
        properties.put(key, value);
        log.debug("设置配置属性: {} = {}", key, value);
    }
    
    /**
     * 获取配置属性
     * 
     * @param key 属性键
     * @param <T> 值类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key) {
        return (T) properties.get(key);
    }
    
    /**
     * 获取配置属性（带默认值）
     * 
     * @param key 属性键
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        return (T) properties.getOrDefault(key, defaultValue);
    }
    
    /**
     * 设置测试数据
     * 
     * @param key 数据键
     * @param data 数据值
     */
    public void setTestData(String key, Object data) {
        if (key == null) {
            throw new IllegalArgumentException("数据键不能为空");
        }
        
        testData.put(key, data);
        log.debug("设置测试数据: {}", key);
    }
    
    /**
     * 获取测试数据
     * 
     * @param key 数据键
     * @param <T> 数据类型
     * @return 数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getTestData(String key) {
        return (T) testData.get(key);
    }
    
    /**
     * 移除测试数据
     * 
     * @param key 数据键
     * @return 被移除的数据
     */
    public Object removeTestData(String key) {
        Object removed = testData.remove(key);
        if (removed != null) {
            log.debug("移除测试数据: {}", key);
        }
        return removed;
    }
    
    /**
     * 注册服务模拟对象
     * 
     * @param serviceName 服务名称
     * @param mockService 模拟服务对象
     */
    public void registerMockService(String serviceName, Object mockService) {
        if (serviceName == null || mockService == null) {
            throw new IllegalArgumentException("服务名称和模拟对象不能为空");
        }
        
        mockServices.put(serviceName, mockService);
        log.info("注册模拟服务: {}", serviceName);
    }
    
    /**
     * 获取服务模拟对象
     * 
     * @param serviceName 服务名称
     * @param <T> 服务类型
     * @return 模拟服务对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getMockService(String serviceName) {
        return (T) mockServices.get(serviceName);
    }
    
    /**
     * 移除服务模拟对象
     * 
     * @param serviceName 服务名称
     * @return 被移除的模拟服务
     */
    public Object removeMockService(String serviceName) {
        Object removed = mockServices.remove(serviceName);
        if (removed != null) {
            log.info("移除模拟服务: {}", serviceName);
        }
        return removed;
    }
    
    /**
     * 添加资源清理回调
     * 
     * @param cleanupCallback 清理回调
     */
    public void addCleanupCallback(Runnable cleanupCallback) {
        if (cleanupCallback != null) {
            cleanupCallbacks.add(cleanupCallback);
            log.debug("添加资源清理回调");
        }
    }
    
    /**
     * 设置测试状态
     * 
     * @param testName 测试名称
     * @param state 测试状态
     */
    public void setTestState(String testName, TestState state) {
        if (testName == null || state == null) {
            throw new IllegalArgumentException("测试名称和状态不能为空");
        }
        
        testStates.put(testName, state);
        log.debug("设置测试状态: {} -> {}", testName, state);
    }
    
    /**
     * 获取测试状态
     * 
     * @param testName 测试名称
     * @return 测试状态
     */
    public TestState getTestState(String testName) {
        return testStates.get(testName);
    }
    
    /**
     * 生成唯一的测试ID
     * 
     * @return 测试ID
     */
    public String generateTestId() {
        long id = testCounter.incrementAndGet();
        return String.format("test_%d_%d", createdTime, id);
    }
    
    /**
     * 检查上下文是否已初始化
     * 
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * 获取上下文创建时间
     * 
     * @return 创建时间（毫秒时间戳）
     */
    public long getCreatedTime() {
        return createdTime;
    }
    
    /**
     * 获取测试计数
     * 
     * @return 测试计数
     */
    public long getTestCount() {
        return testCounter.get();
    }
    
    /**
     * 获取所有配置属性
     * 
     * @return 配置属性映射
     */
    public Map<String, Object> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }
    
    /**
     * 获取所有测试数据
     * 
     * @return 测试数据映射
     */
    public Map<String, Object> getAllTestData() {
        return Collections.unmodifiableMap(testData);
    }
    
    /**
     * 获取所有测试状态
     * 
     * @return 测试状态映射
     */
    public Map<String, TestState> getAllTestStates() {
        return Collections.unmodifiableMap(testStates);
    }
    
    /**
     * 清理测试上下文
     */
    public void cleanup() {
        log.info("正在清理测试上下文...");
        
        try {
            // 执行清理回调
            cleanupCallbacks.forEach(callback -> {
                try {
                    callback.run();
                } catch (Exception e) {
                    log.error("执行清理回调失败", e);
                }
            });
            
            // 清理资源
            cleanupResources();
            
            log.info("测试上下文清理完成");
            
        } catch (Exception e) {
            log.error("清理测试上下文时发生异常", e);
        }
    }
    
    /**
     * 重置测试上下文
     */
    public void reset() {
        log.info("重置测试上下文...");
        
        // 清理现有资源
        cleanup();
        
        // 清空数据
        testData.clear();
        mockServices.clear();
        testStates.clear();
        cleanupCallbacks.clear();
        
        // 重置计数器
        testCounter.set(0);
        
        log.info("测试上下文重置完成");
    }
    
    /**
     * 加载默认配置
     */
    private void loadDefaultProperties() {
        log.debug("加载默认配置...");
        
        // 设置默认的测试配置
        setProperty("test.timeout", 30000L); // 30秒超时
        setProperty("test.retry.count", 3); // 重试3次
        setProperty("test.parallel.enabled", true); // 启用并行测试
        setProperty("test.parallel.threads", Runtime.getRuntime().availableProcessors()); // 并行线程数
        setProperty("test.cleanup.auto", true); // 自动清理
        setProperty("test.logging.level", "INFO"); // 日志级别
        
        log.debug("默认配置加载完成");
    }
    
    /**
     * 初始化测试环境
     */
    private void initializeTestEnvironment() {
        log.debug("初始化测试环境...");
        
        // 设置测试环境属性
        setProperty("test.environment", "test");
        setProperty("test.context.id", generateTestId());
        setProperty("test.start.time", System.currentTimeMillis());
        
        // 初始化其他组件
        // 这里可以根据需要初始化数据库连接、缓存等
        
        log.debug("测试环境初始化完成");
    }
    
    /**
     * 清理资源
     */
    private void cleanupResources() {
        log.debug("清理测试资源...");
        
        // 清理模拟服务
        mockServices.clear();
        
        // 清理测试数据
        testData.clear();
        
        // 重置状态
        testStates.clear();
        
        log.debug("测试资源清理完成");
    }
}