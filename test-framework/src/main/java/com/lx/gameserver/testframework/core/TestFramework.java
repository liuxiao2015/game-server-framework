/*
 * 文件名: TestFramework.java
 * 用途: 游戏服务器测试框架主类
 * 内容: 
 *   - 测试框架核心管理器
 *   - 测试套件管理和生命周期控制
 *   - 测试环境初始化和清理
 *   - 测试结果汇总和分析
 *   - 扩展点管理和插件支持
 * 技术选型: 
 *   - Java 21 API
 *   - Spring Boot框架
 *   - 观察者模式
 *   - 策略模式
 * 依赖关系: 
 *   - 依赖TestContext进行上下文管理
 *   - 依赖TestSuite进行套件管理
 *   - 依赖扩展接口实现插件化
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.core;

import com.lx.gameserver.testframework.extension.TestPlugin;
import com.lx.gameserver.testframework.extension.TestListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏服务器测试框架主类
 * <p>
 * 提供完整的测试管理能力，包括测试套件管理、生命周期控制、
 * 环境初始化、结果汇总和扩展点管理。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@Component
public class TestFramework {
    
    /**
     * 测试上下文
     */
    private final TestContext testContext;
    
    /**
     * 测试套件集合
     */
    private final Map<String, TestSuite> testSuites;
    
    /**
     * 注册的测试插件
     */
    private final Map<String, TestPlugin> plugins;
    
    /**
     * 测试监听器
     */
    private final List<TestListener> listeners;
    
    /**
     * 线程池执行器
     */
    private final ExecutorService executorService;
    
    /**
     * 框架状态
     */
    private volatile FrameworkState state;
    
    /**
     * 框架状态枚举
     */
    public enum FrameworkState {
        /** 未初始化 */
        UNINITIALIZED,
        /** 已初始化 */
        INITIALIZED,
        /** 运行中 */
        RUNNING,
        /** 已停止 */
        STOPPED,
        /** 出错 */
        ERROR
    }
    
    /**
     * 构造函数
     */
    public TestFramework() {
        this.testContext = new TestContext();
        this.testSuites = new ConcurrentHashMap<>();
        this.plugins = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
        this.executorService = Executors.newCachedThreadPool();
        this.state = FrameworkState.UNINITIALIZED;
    }
    
    /**
     * 初始化测试框架
     * 
     * @throws Exception 初始化异常
     */
    public void initialize() throws Exception {
        log.info("正在初始化测试框架...");
        
        try {
            // 初始化测试上下文
            testContext.initialize();
            
            // 加载插件
            loadPlugins();
            
            // 初始化监听器
            initializeListeners();
            
            // 设置状态
            state = FrameworkState.INITIALIZED;
            
            log.info("测试框架初始化完成");
            
        } catch (Exception e) {
            state = FrameworkState.ERROR;
            log.error("测试框架初始化失败", e);
            throw e;
        }
    }
    
    /**
     * 注册测试套件
     * 
     * @param testSuite 测试套件
     */
    public void registerTestSuite(TestSuite testSuite) {
        if (testSuite == null) {
            throw new IllegalArgumentException("测试套件不能为空");
        }
        
        String suiteName = testSuite.getName();
        if (testSuites.containsKey(suiteName)) {
            log.warn("测试套件已存在，将被覆盖: {}", suiteName);
        }
        
        testSuites.put(suiteName, testSuite);
        log.info("注册测试套件: {}", suiteName);
    }
    
    /**
     * 运行所有测试套件
     * 
     * @return 测试结果汇总
     */
    public CompletableFuture<TestSummary> runAllTests() {
        return runTests(testSuites.keySet());
    }
    
    /**
     * 运行指定的测试套件
     * 
     * @param suiteNames 套件名称集合
     * @return 测试结果汇总
     */
    public CompletableFuture<TestSummary> runTests(Collection<String> suiteNames) {
        if (state != FrameworkState.INITIALIZED) {
            throw new IllegalStateException("框架尚未初始化");
        }
        
        return CompletableFuture.supplyAsync(() -> {
            state = FrameworkState.RUNNING;
            
            TestSummary summary = new TestSummary();
            
            try {
                log.info("开始运行测试套件，共{}个", suiteNames.size());
                
                // 通知监听器测试开始
                notifyTestsStarted();
                
                // 并行执行测试套件
                List<CompletableFuture<TestSuite.TestResult>> futures = suiteNames.stream()
                    .map(testSuites::get)
                    .filter(Objects::nonNull)
                    .map(suite -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return suite.execute(testContext);
                        } catch (Exception e) {
                            log.error("测试套件执行失败: {}", suite.getName(), e);
                            return TestSuite.TestResult.builder()
                                .suiteName(suite.getName())
                                .success(false)
                                .error(e.getMessage())
                                .build();
                        }
                    }, executorService))
                    .toList();
                
                // 等待所有测试完成
                List<TestSuite.TestResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                // 汇总结果
                summary = aggregateResults(results);
                
                // 通知监听器测试完成
                notifyTestsCompleted(summary);
                
                log.info("测试套件执行完成，成功率: {:.2f}%", summary.getSuccessRate() * 100);
                
            } catch (Exception e) {
                log.error("测试执行过程中发生异常", e);
                summary.setError(e.getMessage());
                notifyTestsError(e);
            } finally {
                state = FrameworkState.INITIALIZED;
            }
            
            return summary;
        }, executorService);
    }
    
    /**
     * 注册测试插件
     * 
     * @param plugin 测试插件
     */
    public void registerPlugin(TestPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("插件不能为空");
        }
        
        String pluginName = plugin.getPluginInfo().getName();
        plugins.put(pluginName, plugin);
        log.info("注册测试插件: {}", pluginName);
    }
    
    /**
     * 添加测试监听器
     * 
     * @param listener 测试监听器
     */
    public void addListener(TestListener listener) {
        if (listener != null) {
            listeners.add(listener);
            log.debug("添加测试监听器: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 停止测试框架
     */
    public void shutdown() {
        log.info("正在停止测试框架...");
        
        try {
            // 清理资源
            testContext.cleanup();
            
            // 卸载插件
            unloadPlugins();
            
            // 关闭线程池
            if (!executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            state = FrameworkState.STOPPED;
            log.info("测试框架已停止");
            
        } catch (Exception e) {
            log.error("停止测试框架时发生异常", e);
            state = FrameworkState.ERROR;
        }
    }
    
    /**
     * 获取框架状态
     * 
     * @return 框架状态
     */
    public FrameworkState getState() {
        return state;
    }
    
    /**
     * 获取测试上下文
     * 
     * @return 测试上下文
     */
    public TestContext getTestContext() {
        return testContext;
    }
    
    /**
     * 获取已注册的测试套件
     * 
     * @return 测试套件映射
     */
    public Map<String, TestSuite> getTestSuites() {
        return Collections.unmodifiableMap(testSuites);
    }
    
    /**
     * 加载插件
     */
    private void loadPlugins() {
        log.debug("加载测试插件...");
        
        // 使用SPI机制自动发现插件
        ServiceLoader<TestPlugin> pluginLoader = ServiceLoader.load(TestPlugin.class);
        
        for (TestPlugin plugin : pluginLoader) {
            try {
                plugin.load(testContext);
                registerPlugin(plugin);
            } catch (Exception e) {
                log.error("加载插件失败: {}", plugin.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 卸载插件
     */
    private void unloadPlugins() {
        log.debug("卸载测试插件...");
        
        plugins.values().forEach(plugin -> {
            try {
                plugin.unload();
            } catch (Exception e) {
                log.error("卸载插件失败: {}", plugin.getPluginInfo().getName(), e);
            }
        });
        
        plugins.clear();
    }
    
    /**
     * 初始化监听器
     */
    private void initializeListeners() {
        log.debug("初始化测试监听器...");
        
        // 使用SPI机制自动发现监听器
        ServiceLoader<TestListener> listenerLoader = ServiceLoader.load(TestListener.class);
        
        for (TestListener listener : listenerLoader) {
            addListener(listener);
        }
    }
    
    /**
     * 汇总测试结果
     * 
     * @param results 测试结果列表
     * @return 测试汇总
     */
    private TestSummary aggregateResults(List<TestSuite.TestResult> results) {
        TestSummary summary = new TestSummary();
        
        int totalTests = results.size();
        int successfulTests = (int) results.stream()
            .mapToInt(result -> result.isSuccess() ? 1 : 0)
            .sum();
        
        summary.setTotalTests(totalTests);
        summary.setSuccessfulTests(successfulTests);
        summary.setFailedTests(totalTests - successfulTests);
        summary.setSuccessRate((double) successfulTests / totalTests);
        summary.setResults(results);
        
        return summary;
    }
    
    /**
     * 通知监听器测试开始
     */
    private void notifyTestsStarted() {
        listeners.forEach(listener -> {
            try {
                listener.onTestsStarted();
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        });
    }
    
    /**
     * 通知监听器测试完成
     * 
     * @param summary 测试汇总
     */
    private void notifyTestsCompleted(TestSummary summary) {
        listeners.forEach(listener -> {
            try {
                listener.onTestsCompleted(summary);
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        });
    }
    
    /**
     * 通知监听器测试出错
     * 
     * @param error 异常信息
     */
    private void notifyTestsError(Exception error) {
        listeners.forEach(listener -> {
            try {
                listener.onTestsError(error);
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        });
    }
    
    /**
     * 测试结果汇总
     */
    public static class TestSummary {
        private int totalTests;
        private int successfulTests;
        private int failedTests;
        private double successRate;
        private String error;
        private List<TestSuite.TestResult> results;
        
        // Getters and Setters
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        
        public int getSuccessfulTests() { return successfulTests; }
        public void setSuccessfulTests(int successfulTests) { this.successfulTests = successfulTests; }
        
        public int getFailedTests() { return failedTests; }
        public void setFailedTests(int failedTests) { this.failedTests = failedTests; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public List<TestSuite.TestResult> getResults() { return results; }
        public void setResults(List<TestSuite.TestResult> results) { this.results = results; }
    }
}