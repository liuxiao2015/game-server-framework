/*
 * 文件名: TestSuite.java
 * 用途: 游戏服务器测试套件管理
 * 内容: 
 *   - 测试用例组织和管理
 *   - 测试执行顺序控制
 *   - 并行执行支持
 *   - 测试依赖管理
 *   - 结果聚合和统计
 * 技术选型: 
 *   - Java 21 API
 *   - 并发执行框架
 *   - 依赖注入模式
 *   - 组合模式
 * 依赖关系: 
 *   - 依赖TestCase执行具体测试
 *   - 依赖TestContext管理测试环境
 *   - 支持依赖关系管理
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.core;

import lombok.extern.slf4j.Slf4j;
import lombok.Builder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 游戏服务器测试套件
 * <p>
 * 管理一组相关的测试用例，支持串行和并行执行，
 * 提供依赖管理、结果聚合等功能。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
public class TestSuite {
    
    /**
     * 套件名称
     */
    private final String name;
    
    /**
     * 套件描述
     */
    private final String description;
    
    /**
     * 测试用例列表
     */
    private final List<TestCase> testCases;
    
    /**
     * 测试依赖关系
     */
    private final Map<String, Set<String>> dependencies;
    
    /**
     * 是否并行执行
     */
    private boolean parallelExecution;
    
    /**
     * 并行线程数
     */
    private int parallelThreads;
    
    /**
     * 套件超时时间（毫秒）
     */
    private long timeoutMillis;
    
    /**
     * 失败时是否继续执行
     */
    private boolean continueOnFailure;
    
    /**
     * 线程池执行器
     */
    private ExecutorService executorService;
    
    /**
     * 构造函数
     * 
     * @param name 套件名称
     */
    public TestSuite(String name) {
        this(name, "");
    }
    
    /**
     * 构造函数
     * 
     * @param name 套件名称
     * @param description 套件描述
     */
    public TestSuite(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("测试套件名称不能为空");
        }
        
        this.name = name.trim();
        this.description = description != null ? description : "";
        this.testCases = new ArrayList<>();
        this.dependencies = new HashMap<>();
        this.parallelExecution = false;
        this.parallelThreads = Runtime.getRuntime().availableProcessors();
        this.timeoutMillis = 300000; // 默认5分钟超时
        this.continueOnFailure = true; // 默认失败后继续
    }
    
    /**
     * 添加测试用例
     * 
     * @param testCase 测试用例
     * @return 当前测试套件
     */
    public TestSuite addTestCase(TestCase testCase) {
        if (testCase == null) {
            throw new IllegalArgumentException("测试用例不能为空");
        }
        
        testCases.add(testCase);
        log.debug("添加测试用例到套件 {}: {}", name, testCase.getName());
        return this;
    }
    
    /**
     * 添加多个测试用例
     * 
     * @param testCases 测试用例列表
     * @return 当前测试套件
     */
    public TestSuite addTestCases(List<TestCase> testCases) {
        if (testCases != null) {
            testCases.forEach(this::addTestCase);
        }
        return this;
    }
    
    /**
     * 添加测试依赖
     * 
     * @param testName 测试名称
     * @param dependsOn 依赖的测试名称
     * @return 当前测试套件
     */
    public TestSuite addDependency(String testName, String dependsOn) {
        if (testName == null || dependsOn == null) {
            throw new IllegalArgumentException("测试名称和依赖名称不能为空");
        }
        
        dependencies.computeIfAbsent(testName, k -> new HashSet<>()).add(dependsOn);
        log.debug("添加测试依赖: {} 依赖于 {}", testName, dependsOn);
        return this;
    }
    
    /**
     * 设置并行执行
     * 
     * @param enabled 是否启用并行执行
     * @return 当前测试套件
     */
    public TestSuite parallelExecution(boolean enabled) {
        this.parallelExecution = enabled;
        return this;
    }
    
    /**
     * 设置并行线程数
     * 
     * @param threads 线程数
     * @return 当前测试套件
     */
    public TestSuite parallelThreads(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("并行线程数必须大于0");
        }
        this.parallelThreads = threads;
        return this;
    }
    
    /**
     * 设置超时时间
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 当前测试套件
     */
    public TestSuite timeout(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("超时时间必须大于0");
        }
        this.timeoutMillis = timeoutMillis;
        return this;
    }
    
    /**
     * 设置失败后是否继续执行
     * 
     * @param continueOnFailure 是否继续执行
     * @return 当前测试套件
     */
    public TestSuite continueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
        return this;
    }
    
    /**
     * 执行测试套件
     * 
     * @param context 测试上下文
     * @return 测试结果
     */
    public TestResult execute(TestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("测试上下文不能为空");
        }
        
        Instant startTime = Instant.now();
        log.info("开始执行测试套件: {} (包含{}个测试用例)", name, testCases.size());
        
        try {
            // 初始化执行器
            initializeExecutor();
            
            // 根据依赖关系排序测试用例
            List<TestCase> orderedTests = orderTestsByDependencies();
            
            // 执行测试
            List<TestCase.TestResult> results = parallelExecution 
                ? executeInParallel(orderedTests, context)
                : executeSequentially(orderedTests, context);
            
            // 计算执行时长
            Duration duration = Duration.between(startTime, Instant.now());
            
            // 聚合结果
            return aggregateResults(results, duration);
            
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            log.error("测试套件执行失败: {}", name, e);
            return TestResult.builder()
                .suiteName(name)
                .success(false)
                .duration(duration)
                .error(e.getMessage())
                .exception(e)
                .build();
        } finally {
            // 清理执行器
            cleanupExecutor();
        }
    }
    
    /**
     * 获取套件名称
     * 
     * @return 套件名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取套件描述
     * 
     * @return 套件描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取测试用例数量
     * 
     * @return 测试用例数量
     */
    public int getTestCaseCount() {
        return testCases.size();
    }
    
    /**
     * 获取所有测试用例
     * 
     * @return 测试用例列表
     */
    public List<TestCase> getTestCases() {
        return Collections.unmodifiableList(testCases);
    }
    
    /**
     * 初始化执行器
     */
    private void initializeExecutor() {
        if (parallelExecution) {
            executorService = Executors.newFixedThreadPool(parallelThreads);
            log.debug("初始化并行执行器，线程数: {}", parallelThreads);
        }
    }
    
    /**
     * 清理执行器
     */
    private void cleanupExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 根据依赖关系排序测试用例
     * 
     * @return 排序后的测试用例列表
     */
    private List<TestCase> orderTestsByDependencies() {
        if (dependencies.isEmpty()) {
            return new ArrayList<>(testCases);
        }
        
        // 使用拓扑排序处理依赖关系
        Map<String, TestCase> testMap = testCases.stream()
            .collect(Collectors.toMap(TestCase::getName, test -> test));
        
        List<TestCase> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (TestCase testCase : testCases) {
            if (!visited.contains(testCase.getName())) {
                topologicalSort(testCase.getName(), testMap, visited, visiting, ordered);
            }
        }
        
        return ordered;
    }
    
    /**
     * 拓扑排序
     * 
     * @param testName 测试名称
     * @param testMap 测试映射
     * @param visited 已访问集合
     * @param visiting 正在访问集合
     * @param ordered 排序结果
     */
    private void topologicalSort(String testName, Map<String, TestCase> testMap, 
                                Set<String> visited, Set<String> visiting, 
                                List<TestCase> ordered) {
        if (visiting.contains(testName)) {
            throw new IllegalStateException("检测到循环依赖: " + testName);
        }
        
        if (visited.contains(testName)) {
            return;
        }
        
        visiting.add(testName);
        
        Set<String> deps = dependencies.get(testName);
        if (deps != null) {
            for (String dep : deps) {
                topologicalSort(dep, testMap, visited, visiting, ordered);
            }
        }
        
        visiting.remove(testName);
        visited.add(testName);
        
        TestCase testCase = testMap.get(testName);
        if (testCase != null) {
            ordered.add(testCase);
        }
    }
    
    /**
     * 串行执行测试用例
     * 
     * @param orderedTests 排序后的测试用例
     * @param context 测试上下文
     * @return 测试结果列表
     */
    private List<TestCase.TestResult> executeSequentially(List<TestCase> orderedTests, 
                                                          TestContext context) {
        List<TestCase.TestResult> results = new ArrayList<>();
        
        for (TestCase testCase : orderedTests) {
            try {
                TestCase.TestResult result = testCase.execute(context);
                results.add(result);
                
                // 如果测试失败且不继续执行，则停止
                if (!result.isSuccess() && !continueOnFailure) {
                    log.warn("测试失败，停止执行后续测试: {}", testCase.getName());
                    break;
                }
                
            } catch (Exception e) {
                log.error("执行测试用例时发生异常: {}", testCase.getName(), e);
                TestCase.TestResult failureResult = TestCase.TestResult.failure(
                    testCase.getName(), testCase.getDuration(), e);
                results.add(failureResult);
                
                if (!continueOnFailure) {
                    break;
                }
            }
        }
        
        return results;
    }
    
    /**
     * 并行执行测试用例
     * 
     * @param orderedTests 排序后的测试用例
     * @param context 测试上下文
     * @return 测试结果列表
     */
    private List<TestCase.TestResult> executeInParallel(List<TestCase> orderedTests, 
                                                       TestContext context) {
        // 对于有依赖关系的测试，需要分层执行
        if (!dependencies.isEmpty()) {
            return executeInLayers(orderedTests, context);
        }
        
        // 无依赖关系，可以完全并行执行
        List<CompletableFuture<TestCase.TestResult>> futures = orderedTests.stream()
            .map(testCase -> CompletableFuture.supplyAsync(() -> 
                testCase.execute(context), executorService))
            .toList();
        
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }
    
    /**
     * 分层执行（处理依赖关系的并行执行）
     * 
     * @param orderedTests 排序后的测试用例
     * @param context 测试上下文
     * @return 测试结果列表
     */
    private List<TestCase.TestResult> executeInLayers(List<TestCase> orderedTests, 
                                                     TestContext context) {
        List<TestCase.TestResult> allResults = new ArrayList<>();
        Set<String> completed = new HashSet<>();
        
        while (completed.size() < orderedTests.size()) {
            // 找出可以执行的测试（所有依赖都已完成）
            List<TestCase> readyTests = orderedTests.stream()
                .filter(test -> !completed.contains(test.getName()))
                .filter(test -> canExecute(test.getName(), completed))
                .toList();
            
            if (readyTests.isEmpty()) {
                log.error("存在无法解决的依赖关系");
                break;
            }
            
            // 并行执行这一层的测试
            List<CompletableFuture<TestCase.TestResult>> futures = readyTests.stream()
                .map(testCase -> CompletableFuture.supplyAsync(() -> 
                    testCase.execute(context), executorService))
                .toList();
            
            List<TestCase.TestResult> layerResults = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
            
            allResults.addAll(layerResults);
            
            // 更新已完成集合
            layerResults.forEach(result -> completed.add(result.getTestName()));
            
            // 检查是否有失败且需要停止
            if (!continueOnFailure && layerResults.stream().anyMatch(r -> !r.isSuccess())) {
                log.warn("检测到测试失败，停止执行");
                break;
            }
        }
        
        return allResults;
    }
    
    /**
     * 检查测试是否可以执行
     * 
     * @param testName 测试名称
     * @param completed 已完成的测试集合
     * @return 是否可以执行
     */
    private boolean canExecute(String testName, Set<String> completed) {
        Set<String> deps = dependencies.get(testName);
        return deps == null || completed.containsAll(deps);
    }
    
    /**
     * 聚合测试结果
     * 
     * @param results 测试结果列表
     * @param duration 总执行时长
     * @return 套件测试结果
     */
    private TestResult aggregateResults(List<TestCase.TestResult> results, Duration duration) {
        int total = results.size();
        int successful = (int) results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        int failed = total - successful;
        
        boolean overallSuccess = failed == 0;
        
        log.info("测试套件 {} 执行完成: 总数={}, 成功={}, 失败={}, 成功率={:.2f}%", 
            name, total, successful, failed, (double) successful / total * 100);
        
        return TestResult.builder()
            .suiteName(name)
            .success(overallSuccess)
            .duration(duration)
            .totalTests(total)
            .successfulTests(successful)
            .failedTests(failed)
            .testResults(results)
            .build();
    }
    
    /**
     * 测试套件结果类
     */
    @Builder
    public static class TestResult {
        private final String suiteName;
        private final boolean success;
        private final Duration duration;
        private final int totalTests;
        private final int successfulTests;
        private final int failedTests;
        private final List<TestCase.TestResult> testResults;
        private final String error;
        private final Throwable exception;
        
        // Getters
        public String getSuiteName() { return suiteName; }
        public boolean isSuccess() { return success; }
        public Duration getDuration() { return duration; }
        public int getTotalTests() { return totalTests; }
        public int getSuccessfulTests() { return successfulTests; }
        public int getFailedTests() { return failedTests; }
        public List<TestCase.TestResult> getTestResults() { return testResults; }
        public String getError() { return error; }
        public Throwable getException() { return exception; }
        
        public double getSuccessRate() {
            return totalTests > 0 ? (double) successfulTests / totalTests : 0.0;
        }
    }
}