/*
 * 文件名: TestScenario.java
 * 用途: 集成测试场景
 * 内容: 
 *   - 测试场景定义和执行
 *   - 多服务协同测试
 *   - 端到端测试流程
 *   - 业务流程验证
 *   - 异常场景处理
 * 技术选型: 
 *   - 策略模式
 *   - 模板方法模式
 *   - 异常处理机制
 * 依赖关系: 
 *   - 被IntegrationTestRunner使用
 *   - 依赖TestEnvironment
 *   - 与ServiceContainer集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.integration;

import com.lx.gameserver.testframework.core.TestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 集成测试场景
 * <p>
 * 定义和执行集成测试场景，支持多服务协同测试、
 * 端到端业务流程验证和异常场景处理。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
public abstract class TestScenario {
    
    /**
     * 场景名称
     */
    private final String name;
    
    /**
     * 场景描述
     */
    private final String description;
    
    /**
     * 依赖的服务
     */
    private final List<String> dependencies;
    
    /**
     * 场景配置
     */
    private final Map<String, Object> configuration;
    
    /**
     * 构造函数
     * 
     * @param name 场景名称
     * @param description 场景描述
     */
    protected TestScenario(String name, String description) {
        this.name = name;
        this.description = description;
        this.dependencies = new ArrayList<>();
        this.configuration = new HashMap<>();
    }
    
    /**
     * 执行测试场景
     * 
     * @param environment 测试环境
     * @param context 测试上下文
     * @throws Exception 执行异常
     */
    public final void execute(TestEnvironment environment, TestContext context) throws Exception {
        log.info("开始执行测试场景: {}", name);
        
        try {
            // 前置处理
            setUp(environment, context);
            
            // 执行场景逻辑
            runScenario(environment, context);
            
            // 验证结果
            verifyResults(environment, context);
            
            log.info("测试场景 {} 执行成功", name);
            
        } catch (Exception e) {
            log.error("测试场景 {} 执行失败", name, e);
            throw e;
        } finally {
            try {
                // 后置清理
                tearDown(environment, context);
            } catch (Exception e) {
                log.error("测试场景 {} 清理失败", name, e);
            }
        }
    }
    
    /**
     * 前置处理（子类可重写）
     * 
     * @param environment 测试环境
     * @param context 测试上下文
     * @throws Exception 处理异常
     */
    protected void setUp(TestEnvironment environment, TestContext context) throws Exception {
        log.debug("执行场景前置处理: {}", name);
    }
    
    /**
     * 执行场景逻辑（子类必须实现）
     * 
     * @param environment 测试环境
     * @param context 测试上下文
     * @throws Exception 执行异常
     */
    protected abstract void runScenario(TestEnvironment environment, TestContext context) throws Exception;
    
    /**
     * 验证结果（子类可重写）
     * 
     * @param environment 测试环境
     * @param context 测试上下文
     * @throws Exception 验证异常
     */
    protected void verifyResults(TestEnvironment environment, TestContext context) throws Exception {
        log.debug("验证场景结果: {}", name);
    }
    
    /**
     * 后置清理（子类可重写）
     * 
     * @param environment 测试环境
     * @param context 测试上下文
     * @throws Exception 清理异常
     */
    protected void tearDown(TestEnvironment environment, TestContext context) throws Exception {
        log.debug("执行场景后置清理: {}", name);
    }
    
    /**
     * 添加服务依赖
     * 
     * @param serviceName 服务名称
     * @return 当前场景
     */
    public TestScenario addDependency(String serviceName) {
        if (serviceName != null && !dependencies.contains(serviceName)) {
            dependencies.add(serviceName);
        }
        return this;
    }
    
    /**
     * 设置配置
     * 
     * @param key 配置键
     * @param value 配置值
     * @return 当前场景
     */
    public TestScenario setConfiguration(String key, Object value) {
        configuration.put(key, value);
        return this;
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
     * 获取场景名称
     * 
     * @return 场景名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取场景描述
     * 
     * @return 场景描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取依赖服务列表
     * 
     * @return 依赖服务列表
     */
    public List<String> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }
    
    /**
     * 获取所有配置
     * 
     * @return 配置映射
     */
    public Map<String, Object> getConfiguration() {
        return Collections.unmodifiableMap(configuration);
    }
    
    /**
     * 等待条件满足
     * 
     * @param condition 条件
     * @param timeoutMillis 超时时间（毫秒）
     * @param message 错误消息
     * @throws Exception 等待异常
     */
    protected void waitForCondition(java.util.function.Supplier<Boolean> condition, 
                                   long timeoutMillis, String message) throws Exception {
        long startTime = System.currentTimeMillis();
        
        while (!condition.get()) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                throw new RuntimeException("等待条件超时: " + message);
            }
            
            Thread.sleep(100); // 等待100ms后重试
        }
    }
    
    /**
     * 断言条件为真
     * 
     * @param condition 条件
     * @param message 错误消息
     * @throws Exception 断言异常
     */
    protected void assertThat(boolean condition, String message) throws Exception {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
    
    /**
     * 断言对象相等
     * 
     * @param expected 期望值
     * @param actual 实际值
     * @param message 错误消息
     * @throws Exception 断言异常
     */
    protected void assertEqual(Object expected, Object actual, String message) throws Exception {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(String.format("%s: expected=<%s> but was=<%s>", 
                message, expected, actual));
        }
    }
    
    /**
     * 断言对象不为空
     * 
     * @param object 对象
     * @param message 错误消息
     * @throws Exception 断言异常
     */
    protected void assertNotNull(Object object, String message) throws Exception {
        if (object == null) {
            throw new AssertionError(message);
        }
    }
}