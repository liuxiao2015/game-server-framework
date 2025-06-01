/*
 * 文件名: UnitTestRunner.java
 * 用途: 单元测试运行器
 * 内容: 
 *   - 简化的单元测试执行
 *   - Mock框架集成
 *   - 测试覆盖率统计
 *   - 测试隔离机制
 * 技术选型: 
 *   - 反射API
 *   - Mockito框架
 *   - 注解驱动
 * 依赖关系: 
 *   - 依赖Mockito框架
 *   - 依赖MockFactory
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.unit;

import com.lx.gameserver.testframework.core.TestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单元测试运行器
 * <p>
 * 简化的单元测试执行器，提供基本的测试运行、管理和结果收集功能，
 * 支持Mock对象和测试隔离。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@Component
public class UnitTestRunner {
    
    /**
     * Mock工厂
     */
    private final MockFactory mockFactory;
    
    /**
     * 测试数据构建器
     */
    private final TestDataBuilder testDataBuilder;
    
    /**
     * 测试执行配置
     */
    private final Map<String, Object> executionConfig;
    
    /**
     * 构造函数
     */
    public UnitTestRunner() {
        this.mockFactory = new MockFactory();
        this.testDataBuilder = new TestDataBuilder();
        this.executionConfig = new ConcurrentHashMap<>();
        
        // 设置默认配置
        initializeDefaultConfig();
    }
    
    /**
     * 运行单元测试类
     * 
     * @param testClass 测试类
     * @param context 测试上下文
     * @return 测试结果
     */
    public UnitTestResult runTestClass(Class<?> testClass, TestContext context) {
        if (testClass == null) {
            throw new IllegalArgumentException("测试类不能为空");
        }
        
        log.info("开始运行单元测试类: {}", testClass.getSimpleName());
        
        Instant startTime = Instant.now();
        List<TestMethodResult> methodResults = new ArrayList<>();
        
        try {
            // 准备测试环境
            prepareTestEnvironment(context);
            
            // 获取测试方法
            Method[] testMethods = findTestMethods(testClass);
            
            // 执行测试方法
            for (Method method : testMethods) {
                TestMethodResult result = runTestMethod(testClass, method, context);
                methodResults.add(result);
            }
            
            // 计算总体结果
            Duration duration = Duration.between(startTime, Instant.now());
            UnitTestResult result = aggregateResults(testClass.getSimpleName(), methodResults, duration);
            
            log.info("单元测试类 {} 执行完成: 成功={}, 失败={}", 
                testClass.getSimpleName(), result.getSuccessfulTests(), result.getFailedTests());
            
            return result;
            
        } catch (Exception e) {
            log.error("运行单元测试类失败: {}", testClass.getSimpleName(), e);
            Duration duration = Duration.between(startTime, Instant.now());
            return UnitTestResult.failure(testClass.getSimpleName(), duration, e);
        } finally {
            // 清理测试环境
            cleanupTestEnvironment();
        }
    }
    
    /**
     * 运行单个测试方法
     * 
     * @param testClass 测试类
     * @param testMethod 测试方法
     * @param context 测试上下文
     * @return 测试方法结果
     */
    public TestMethodResult runTestMethod(Class<?> testClass, Method testMethod, TestContext context) {
        String methodName = testMethod.getName();
        log.debug("运行测试方法: {}.{}", testClass.getSimpleName(), methodName);
        
        Instant startTime = Instant.now();
        
        try {
            // 创建测试实例
            Object testInstance = testClass.getDeclaredConstructor().newInstance();
            
            // 执行前置方法
            executeBeforeMethods(testInstance);
            
            // 执行测试方法
            testMethod.invoke(testInstance);
            
            // 执行后置方法
            executeAfterMethods(testInstance);
            
            Duration duration = Duration.between(startTime, Instant.now());
            log.debug("测试方法 {}.{} 执行成功 (耗时: {}ms)", 
                testClass.getSimpleName(), methodName, duration.toMillis());
            
            return TestMethodResult.success(methodName, duration);
            
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            log.error("测试方法 {}.{} 执行失败 (耗时: {}ms)", 
                testClass.getSimpleName(), methodName, duration.toMillis(), e);
            
            return TestMethodResult.failure(methodName, duration, e);
        }
    }
    
    /**
     * 设置执行配置
     * 
     * @param key 配置键
     * @param value 配置值
     */
    public void setConfig(String key, Object value) {
        executionConfig.put(key, value);
    }
    
    /**
     * 获取执行配置
     * 
     * @param key 配置键
     * @param <T> 值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key) {
        return (T) executionConfig.get(key);
    }
    
    /**
     * 获取Mock工厂
     * 
     * @return Mock工厂
     */
    public MockFactory getMockFactory() {
        return mockFactory;
    }
    
    /**
     * 获取测试数据构建器
     * 
     * @return 测试数据构建器
     */
    public TestDataBuilder getTestDataBuilder() {
        return testDataBuilder;
    }
    
    /**
     * 初始化默认配置
     */
    private void initializeDefaultConfig() {
        log.debug("初始化单元测试运行器默认配置...");
        
        // 设置默认配置
        setConfig("parallel.enabled", false);
        setConfig("timeout.default", 30000L);
        setConfig("retry.count", 0);
        
        log.debug("单元测试运行器默认配置初始化完成");
    }
    
    /**
     * 准备测试环境
     * 
     * @param context 测试上下文
     */
    private void prepareTestEnvironment(TestContext context) {
        log.debug("准备单元测试环境...");
        
        // 初始化Mock工厂
        mockFactory.initialize(context);
        
        log.debug("单元测试环境准备完成");
    }
    
    /**
     * 清理测试环境
     */
    private void cleanupTestEnvironment() {
        log.debug("清理单元测试环境...");
        
        try {
            // 清理Mock工厂
            mockFactory.cleanup();
            
            log.debug("单元测试环境清理完成");
            
        } catch (Exception e) {
            log.error("清理单元测试环境失败", e);
        }
    }
    
    /**
     * 查找测试方法
     * 
     * @param testClass 测试类
     * @return 测试方法数组
     */
    private Method[] findTestMethods(Class<?> testClass) {
        return Arrays.stream(testClass.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("test") || 
                             method.isAnnotationPresent(org.junit.jupiter.api.Test.class))
            .toArray(Method[]::new);
    }
    
    /**
     * 执行前置方法
     * 
     * @param testInstance 测试实例
     */
    private void executeBeforeMethods(Object testInstance) throws Exception {
        Method[] methods = testInstance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().startsWith("setUp") || 
                method.isAnnotationPresent(org.junit.jupiter.api.BeforeEach.class)) {
                method.setAccessible(true);
                method.invoke(testInstance);
            }
        }
    }
    
    /**
     * 执行后置方法
     * 
     * @param testInstance 测试实例
     */
    private void executeAfterMethods(Object testInstance) throws Exception {
        Method[] methods = testInstance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().startsWith("tearDown") || 
                method.isAnnotationPresent(org.junit.jupiter.api.AfterEach.class)) {
                method.setAccessible(true);
                method.invoke(testInstance);
            }
        }
    }
    
    /**
     * 聚合测试结果
     * 
     * @param testName 测试名称
     * @param methodResults 方法结果列表
     * @param duration 总耗时
     * @return 聚合结果
     */
    private UnitTestResult aggregateResults(String testName, List<TestMethodResult> methodResults, Duration duration) {
        int total = methodResults.size();
        int successful = (int) methodResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        int failed = total - successful;
        
        List<TestFailure> failures = methodResults.stream()
            .filter(r -> !r.isSuccess())
            .map(r -> new TestFailure(r.getMethodName(), r.getException()))
            .toList();
        
        return new UnitTestResult(total, successful, failed, 0, duration.toMillis(), failures);
    }
    
    /**
     * 测试方法结果
     */
    public static class TestMethodResult {
        private final String methodName;
        private final boolean success;
        private final Duration duration;
        private final Throwable exception;
        
        private TestMethodResult(String methodName, boolean success, Duration duration, Throwable exception) {
            this.methodName = methodName;
            this.success = success;
            this.duration = duration;
            this.exception = exception;
        }
        
        public static TestMethodResult success(String methodName, Duration duration) {
            return new TestMethodResult(methodName, true, duration, null);
        }
        
        public static TestMethodResult failure(String methodName, Duration duration, Throwable exception) {
            return new TestMethodResult(methodName, false, duration, exception);
        }
        
        // Getters
        public String getMethodName() { return methodName; }
        public boolean isSuccess() { return success; }
        public Duration getDuration() { return duration; }
        public Throwable getException() { return exception; }
    }
    
    /**
     * 测试失败信息
     */
    public static class TestFailure {
        private final String testName;
        private final Throwable throwable;
        
        public TestFailure(String testName, Throwable throwable) {
            this.testName = testName;
            this.throwable = throwable;
        }
        
        public String getTestName() { return testName; }
        public Throwable getThrowable() { return throwable; }
    }
    
    /**
     * 单元测试结果
     */
    public static class UnitTestResult {
        private final int totalTests;
        private final int successfulTests;
        private final int failedTests;
        private final int skippedTests;
        private final long durationMillis;
        private final List<TestFailure> failures;
        
        public UnitTestResult(int totalTests, int successfulTests, int failedTests, 
                             int skippedTests, long durationMillis, List<TestFailure> failures) {
            this.totalTests = totalTests;
            this.successfulTests = successfulTests;
            this.failedTests = failedTests;
            this.skippedTests = skippedTests;
            this.durationMillis = durationMillis;
            this.failures = new ArrayList<>(failures);
        }
        
        public static UnitTestResult failure(String testName, Duration duration, Exception exception) {
            return new UnitTestResult(1, 0, 1, 0, duration.toMillis(), 
                List.of(new TestFailure(testName, exception)));
        }
        
        // Getters
        public int getTotalTests() { return totalTests; }
        public int getSuccessfulTests() { return successfulTests; }
        public int getFailedTests() { return failedTests; }
        public int getSkippedTests() { return skippedTests; }
        public long getDurationMillis() { return durationMillis; }
        public List<TestFailure> getFailures() { return Collections.unmodifiableList(failures); }
        
        public double getSuccessRate() {
            return totalTests > 0 ? (double) successfulTests / totalTests : 0.0;
        }
        
        public boolean isSuccess() {
            return failedTests == 0;
        }
    }
}