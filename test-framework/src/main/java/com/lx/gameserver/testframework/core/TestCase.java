/*
 * 文件名: TestCase.java
 * 用途: 游戏服务器测试用例基类
 * 内容: 
 *   - 测试用例生命周期管理
 *   - 前置和后置处理机制
 *   - 断言工具集成
 *   - 异常处理和错误恢复
 *   - 超时控制和重试机制
 * 技术选型: 
 *   - Java 21 API
 *   - JUnit 5断言集成
 *   - 模板方法设计模式
 *   - 装饰器模式
 * 依赖关系: 
 *   - 依赖TestContext获取测试环境
 *   - 依赖断言工具进行结果验证
 *   - 支持自定义扩展和插件
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.core;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 游戏服务器测试用例基类
 * <p>
 * 提供测试用例的标准生命周期管理、断言工具、异常处理、
 * 超时控制和重试机制，所有具体测试用例都应继承此类。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
public abstract class TestCase {
    
    /**
     * 测试用例名称
     */
    private final String name;
    
    /**
     * 测试用例描述
     */
    private final String description;
    
    /**
     * 测试超时时间（毫秒）
     */
    private long timeoutMillis;
    
    /**
     * 重试次数
     */
    private int retryCount;
    
    /**
     * 测试上下文
     */
    protected TestContext testContext;
    
    /**
     * 测试开始时间
     */
    private Instant startTime;
    
    /**
     * 测试结束时间
     */
    private Instant endTime;
    
    /**
     * 测试结果
     */
    private TestResult result;
    
    /**
     * 构造函数
     * 
     * @param name 测试用例名称
     */
    protected TestCase(String name) {
        this(name, "");
    }
    
    /**
     * 构造函数
     * 
     * @param name 测试用例名称
     * @param description 测试用例描述
     */
    protected TestCase(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("测试用例名称不能为空");
        }
        
        this.name = name.trim();
        this.description = description != null ? description : "";
        this.timeoutMillis = 30000; // 默认30秒超时
        this.retryCount = 0; // 默认不重试
    }
    
    /**
     * 执行测试用例
     * 
     * @param context 测试上下文
     * @return 测试结果
     */
    public final TestResult execute(TestContext context) {
        this.testContext = context;
        this.startTime = Instant.now();
        
        log.info("开始执行测试用例: {}", name);
        
        try {
            // 设置测试状态
            context.setTestState(name, TestContext.TestState.PREPARING);
            
            // 执行前置处理
            setUp();
            
            // 设置运行状态
            context.setTestState(name, TestContext.TestState.RUNNING);
            
            // 执行测试逻辑（支持重试）
            executeWithRetry();
            
            // 测试成功
            this.result = TestResult.success(name, getDuration());
            context.setTestState(name, TestContext.TestState.SUCCESS);
            
            log.info("测试用例执行成功: {} (耗时: {}ms)", name, getDuration().toMillis());
            
        } catch (Exception e) {
            // 测试失败
            this.result = TestResult.failure(name, getDuration(), e);
            context.setTestState(name, TestContext.TestState.FAILED);
            
            log.error("测试用例执行失败: {} (耗时: {}ms)", name, getDuration().toMillis(), e);
            
        } finally {
            this.endTime = Instant.now();
            
            try {
                // 执行后置处理
                context.setTestState(name, TestContext.TestState.CLEANING);
                tearDown();
            } catch (Exception e) {
                log.error("测试用例清理失败: {}", name, e);
                if (result.isSuccess()) {
                    // 如果测试成功但清理失败，标记为失败
                    this.result = TestResult.failure(name, getDuration(), e);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 前置处理（子类可重写）
     * 
     * @throws Exception 设置异常
     */
    protected void setUp() throws Exception {
        // 默认空实现
        log.debug("执行测试前置处理: {}", name);
    }
    
    /**
     * 测试逻辑（子类必须实现）
     * 
     * @throws Exception 测试异常
     */
    protected abstract void runTest() throws Exception;
    
    /**
     * 后置处理（子类可重写）
     * 
     * @throws Exception 清理异常
     */
    protected void tearDown() throws Exception {
        // 默认空实现
        log.debug("执行测试后置处理: {}", name);
    }
    
    /**
     * 设置超时时间
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 当前测试用例
     */
    public TestCase timeout(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("超时时间必须大于0");
        }
        this.timeoutMillis = timeoutMillis;
        return this;
    }
    
    /**
     * 设置重试次数
     * 
     * @param retryCount 重试次数
     * @return 当前测试用例
     */
    public TestCase retry(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("重试次数不能为负数");
        }
        this.retryCount = retryCount;
        return this;
    }
    
    /**
     * 断言为真
     * 
     * @param condition 条件
     * @param message 错误消息
     */
    protected void assertThat(boolean condition, String message) {
        assertTrue(condition, message);
    }
    
    /**
     * 断言对象相等
     * 
     * @param expected 期望值
     * @param actual 实际值
     * @param message 错误消息
     */
    protected void assertEqual(Object expected, Object actual, String message) {
        assertEquals(expected, actual, message);
    }
    
    /**
     * 断言对象不为空
     * 
     * @param object 对象
     * @param message 错误消息
     */
    protected void assertNotNull(Object object, String message) {
        org.junit.jupiter.api.Assertions.assertNotNull(object, message);
    }
    
    /**
     * 断言抛出异常
     * 
     * @param expectedType 期望的异常类型
     * @param executable 可执行代码
     * @param <T> 异常类型
     * @return 抛出的异常
     */
    protected <T extends Throwable> T assertThrows(Class<T> expectedType, 
                                                   ThrowingExecutable executable) {
        return org.junit.jupiter.api.Assertions.assertThrows(expectedType, executable);
    }
    
    /**
     * 断言在指定时间内完成
     * 
     * @param timeout 超时时间
     * @param executable 可执行代码
     */
    protected void assertTimeout(Duration timeout, ThrowingExecutable executable) {
        org.junit.jupiter.api.Assertions.assertTimeout(timeout, executable);
    }
    
    /**
     * 等待条件成立
     * 
     * @param condition 条件供应商
     * @param timeout 超时时间
     * @param message 错误消息
     * @throws TimeoutException 超时异常
     */
    protected void waitFor(Supplier<Boolean> condition, Duration timeout, String message) 
            throws TimeoutException {
        Instant start = Instant.now();
        
        while (!condition.get()) {
            if (Duration.between(start, Instant.now()).compareTo(timeout) > 0) {
                throw new TimeoutException(message);
            }
            
            try {
                Thread.sleep(100); // 等待100ms后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待被中断", e);
            }
        }
    }
    
    /**
     * 获取测试用例名称
     * 
     * @return 测试用例名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取测试用例描述
     * 
     * @return 测试用例描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取测试执行时长
     * 
     * @return 执行时长
     */
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }
    
    /**
     * 获取测试结果
     * 
     * @return 测试结果
     */
    public TestResult getResult() {
        return result;
    }
    
    /**
     * 带重试的执行测试
     * 
     * @throws Exception 测试异常
     */
    private void executeWithRetry() throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                // 执行带超时的测试
                executeWithTimeout();
                return; // 成功则返回
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < retryCount) {
                    log.warn("测试用例执行失败，第{}次重试: {}", attempt + 1, name, e);
                    
                    // 重试前清理状态
                    try {
                        tearDown();
                        setUp();
                    } catch (Exception setupException) {
                        log.error("重试前的状态重置失败: {}", name, setupException);
                    }
                } else {
                    log.error("测试用例执行失败，已达到最大重试次数: {}", name, e);
                }
            }
        }
        
        // 所有重试都失败，抛出最后的异常
        throw lastException;
    }
    
    /**
     * 带超时的执行测试
     * 
     * @throws Exception 测试异常
     */
    private void executeWithTimeout() throws Exception {
        if (timeoutMillis <= 0) {
            // 无超时直接执行
            runTest();
            return;
        }
        
        // 使用超时执行
        assertTimeout(Duration.ofMillis(timeoutMillis), this::runTest);
    }
    
    /**
     * 抛出异常的可执行接口
     */
    @FunctionalInterface
    public interface ThrowingExecutable {
        void execute() throws Exception;
    }
    
    /**
     * 测试结果类
     */
    public static class TestResult {
        private final String testName;
        private final boolean success;
        private final Duration duration;
        private final String errorMessage;
        private final Throwable exception;
        
        private TestResult(String testName, boolean success, Duration duration, 
                          String errorMessage, Throwable exception) {
            this.testName = testName;
            this.success = success;
            this.duration = duration;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }
        
        /**
         * 创建成功结果
         * 
         * @param testName 测试名称
         * @param duration 执行时长
         * @return 测试结果
         */
        public static TestResult success(String testName, Duration duration) {
            return new TestResult(testName, true, duration, null, null);
        }
        
        /**
         * 创建失败结果
         * 
         * @param testName 测试名称
         * @param duration 执行时长
         * @param exception 异常
         * @return 测试结果
         */
        public static TestResult failure(String testName, Duration duration, Throwable exception) {
            String message = exception != null ? exception.getMessage() : "未知错误";
            return new TestResult(testName, false, duration, message, exception);
        }
        
        // Getters
        public String getTestName() { return testName; }
        public boolean isSuccess() { return success; }
        public Duration getDuration() { return duration; }
        public String getErrorMessage() { return errorMessage; }
        public Throwable getException() { return exception; }
    }
}