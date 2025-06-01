/*
 * 文件名: UnitTestRunner.java
 * 用途: 单元测试运行器
 * 内容: 
 *   - JUnit 5集成和测试执行
 *   - 参数化测试支持
 *   - Mock框架集成
 *   - 测试覆盖率统计
 *   - 测试隔离机制
 * 技术选型: 
 *   - JUnit 5平台
 *   - Mockito框架
 *   - 反射API
 *   - 注解驱动
 * 依赖关系: 
 *   - 依赖JUnit 5引擎
 *   - 依赖Mockito框架
 *   - 依赖MockFactory
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.unit;

import com.lx.gameserver.testframework.core.TestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单元测试运行器
 * <p>
 * 集成JUnit 5平台，提供单元测试的执行、管理和结果收集功能，
 * 支持参数化测试、Mock对象和测试隔离。
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
     * JUnit 5启动器
     */
    private final Launcher launcher;
    
    /**
     * 测试结果收集器
     */
    private final TestResultCollector resultCollector;
    
    /**
     * Mock工厂
     */
    private final MockFactory mockFactory;
    
    /**
     * 测试执行配置
     */
    private final Map<String, Object> executionConfig;
    
    /**
     * 构造函数
     */
    public UnitTestRunner() {
        this.launcher = LauncherFactory.create();
        this.resultCollector = new TestResultCollector();
        this.mockFactory = new MockFactory();
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
        
        try {
            // 准备测试环境
            prepareTestEnvironment(context);
            
            // 配置测试发现
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClass))
                .build();
            
            // 执行测试
            launcher.execute(request, resultCollector);
            
            // 收集结果
            UnitTestResult result = resultCollector.getResult();
            
            log.info("单元测试类 {} 执行完成: 成功={}, 失败={}", 
                testClass.getSimpleName(), result.getSuccessfulTests(), result.getFailedTests());
            
            return result;
            
        } catch (Exception e) {
            log.error("运行单元测试类失败: {}", testClass.getSimpleName(), e);
            return UnitTestResult.failure(testClass.getSimpleName(), e);
        } finally {
            // 清理测试环境
            cleanupTestEnvironment();
        }
    }
    
    /**
     * 运行包下的所有测试
     * 
     * @param packageName 包名
     * @param context 测试上下文
     * @return 测试结果
     */
    public UnitTestResult runTestPackage(String packageName, TestContext context) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("包名不能为空");
        }
        
        log.info("开始运行包下的单元测试: {}", packageName);
        
        try {
            // 准备测试环境
            prepareTestEnvironment(context);
            
            // 配置测试发现
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage(packageName))
                .build();
            
            // 执行测试
            launcher.execute(request, resultCollector);
            
            // 收集结果
            UnitTestResult result = resultCollector.getResult();
            
            log.info("包 {} 下的单元测试执行完成: 成功={}, 失败={}", 
                packageName, result.getSuccessfulTests(), result.getFailedTests());
            
            return result;
            
        } catch (Exception e) {
            log.error("运行包下的单元测试失败: {}", packageName, e);
            return UnitTestResult.failure(packageName, e);
        } finally {
            // 清理测试环境
            cleanupTestEnvironment();
        }
    }
    
    /**
     * 运行指定的测试方法
     * 
     * @param testClass 测试类
     * @param methodName 方法名
     * @param context 测试上下文
     * @return 测试结果
     */
    public UnitTestResult runTestMethod(Class<?> testClass, String methodName, TestContext context) {
        if (testClass == null || methodName == null) {
            throw new IllegalArgumentException("测试类和方法名不能为空");
        }
        
        log.info("开始运行单元测试方法: {}.{}", testClass.getSimpleName(), methodName);
        
        try {
            // 准备测试环境
            prepareTestEnvironment(context);
            
            // 配置测试发现
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectMethod(testClass, methodName))
                .build();
            
            // 执行测试
            launcher.execute(request, resultCollector);
            
            // 收集结果
            UnitTestResult result = resultCollector.getResult();
            
            log.info("单元测试方法 {}.{} 执行完成: 成功={}, 失败={}", 
                testClass.getSimpleName(), methodName, result.getSuccessfulTests(), result.getFailedTests());
            
            return result;
            
        } catch (Exception e) {
            log.error("运行单元测试方法失败: {}.{}", testClass.getSimpleName(), methodName, e);
            return UnitTestResult.failure(testClass.getSimpleName() + "." + methodName, e);
        } finally {
            // 清理测试环境
            cleanupTestEnvironment();
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
     * 初始化默认配置
     */
    private void initializeDefaultConfig() {
        log.debug("初始化单元测试运行器默认配置...");
        
        // 设置默认配置
        setConfig("junit.jupiter.execution.parallel.enabled", true);
        setConfig("junit.jupiter.execution.parallel.mode.default", "concurrent");
        setConfig("junit.jupiter.execution.parallel.config.strategy", "dynamic");
        setConfig("junit.jupiter.testinstance.lifecycle.default", "per_class");
        
        log.debug("单元测试运行器默认配置初始化完成");
    }
    
    /**
     * 准备测试环境
     * 
     * @param context 测试上下文
     */
    private void prepareTestEnvironment(TestContext context) {
        log.debug("准备单元测试环境...");
        
        // 重置结果收集器
        resultCollector.reset();
        
        // 初始化Mock工厂
        mockFactory.initialize(context);
        
        // 设置系统属性
        executionConfig.forEach((key, value) -> {
            if (key.startsWith("junit.")) {
                System.setProperty(key, String.valueOf(value));
            }
        });
        
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
            
            // 清理系统属性
            executionConfig.keySet().stream()
                .filter(key -> key.startsWith("junit."))
                .forEach(System::clearProperty);
            
            log.debug("单元测试环境清理完成");
            
        } catch (Exception e) {
            log.error("清理单元测试环境失败", e);
        }
    }
    
    /**
     * 测试结果收集器
     */
    private static class TestResultCollector implements TestExecutionListener {
        private int totalTests = 0;
        private int successfulTests = 0;
        private int failedTests = 0;
        private int skippedTests = 0;
        private final List<TestFailure> failures = new ArrayList<>();
        private long startTime;
        private long endTime;
        
        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            if (testIdentifier.isTest()) {
                if (totalTests == 0) {
                    startTime = System.currentTimeMillis();
                }
                totalTests++;
            }
        }
        
        @Override
        public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
            if (testIdentifier.isTest()) {
                switch (testExecutionResult.getStatus()) {
                    case SUCCESSFUL:
                        successfulTests++;
                        break;
                    case FAILED:
                        failedTests++;
                        testExecutionResult.getThrowable().ifPresent(throwable -> 
                            failures.add(new TestFailure(testIdentifier.getDisplayName(), throwable)));
                        break;
                    case ABORTED:
                        skippedTests++;
                        break;
                }
                
                if (successfulTests + failedTests + skippedTests >= totalTests) {
                    endTime = System.currentTimeMillis();
                }
            }
        }
        
        public UnitTestResult getResult() {
            return new UnitTestResult(
                totalTests, successfulTests, failedTests, skippedTests,
                endTime - startTime, failures
            );
        }
        
        public void reset() {
            totalTests = 0;
            successfulTests = 0;
            failedTests = 0;
            skippedTests = 0;
            failures.clear();
            startTime = 0;
            endTime = 0;
        }
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
        
        public static UnitTestResult failure(String testName, Exception exception) {
            return new UnitTestResult(1, 0, 1, 0, 0, 
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