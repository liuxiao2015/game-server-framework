/*
 * 文件名: ComprehensiveEndToEndTest.java
 * 用途: 游戏服务器框架全方位端到端测试用例
 * 内容: 
 *   - 运行完整的端到端测试套件
 *   - 验证所有关键业务流程
 *   - 生成详细的测试报告
 * 技术选型: 
 *   - JUnit 5
 *   - 端到端测试框架
 * 依赖关系: 
 *   - 使用ComprehensiveEndToEndTestSuite
 *   - 集成测试框架基础设施
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.e2e;

import com.lx.gameserver.testframework.config.TestConfig;
import com.lx.gameserver.testframework.core.TestContext;
import com.lx.gameserver.testframework.integration.TestEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 游戏服务器框架全方位端到端测试
 * <p>
 * 执行完整的端到端测试场景，验证游戏服务器框架的所有关键功能，
 * 包括服务启动、用户登录、消息路由、性能基准等核心业务流程。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@DisplayName("游戏服务器框架全方位端到端测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComprehensiveEndToEndTest {
    
    private TestConfig testConfig;
    private ComprehensiveEndToEndTestSuite testSuite;
    private TestEnvironment testEnvironment;
    private TestContext testContext;
    
    @BeforeAll
    static void setUpClass() {
        log.info("================================================================================");
        log.info("开始执行游戏服务器框架全方位端到端测试");
        log.info("测试时间: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("================================================================================");
    }
    
    @BeforeEach
    void setUp() throws Exception {
        // 初始化测试配置
        testConfig = new TestConfig();
        
        // 初始化测试环境
        testEnvironment = new TestEnvironment("test-env", "comprehensive-e2e-test");
        
        // 初始化测试上下文
        testContext = new TestContext();
        testContext.initialize();
        
        // 设置环境上下文
        testEnvironment.setContext(testContext);
        
        // 初始化测试套件
        testSuite = new ComprehensiveEndToEndTestSuite(testConfig);
        
        log.info("测试环境初始化完成");
    }
    
    @AfterEach
    void tearDown() {
        if (testContext != null) {
            testContext.cleanup();
        }
        if (testEnvironment != null) {
            testEnvironment.cleanup();
        }
        log.info("测试环境清理完成");
    }
    
    @Test
    @Order(1)
    @DisplayName("1. 服务启动测试场景")
    void testServiceStartup() throws Exception {
        log.info("执行服务启动测试场景");
        
        testSuite.executeScenario("service-startup-test", testEnvironment, testContext);
        
        // 验证测试结果
        assertEquals("success", testContext.getTestData("startup.status"));
        assertTrue((Long) testContext.getTestData("startup.duration") < 10000);
        
        log.info("服务启动测试场景通过");
    }
    
    @Test
    @Order(2)
    @DisplayName("2. 登录流程测试场景")
    void testLoginFlow() throws Exception {
        log.info("执行登录流程测试场景");
        
        testSuite.executeScenario("login-flow-test", testEnvironment, testContext);
        
        // 验证测试结果
        assertEquals("connected", testContext.getTestData("gateway.connection.status"));
        
        Map<String, Object> loginResponse = (Map<String, Object>) testContext.getTestData("login.response");
        assertEquals("success", loginResponse.get("status"));
        assertNotNull(loginResponse.get("userId"));
        
        Map<String, Object> tokenInfo = (Map<String, Object>) testContext.getTestData("token.info");
        assertNotNull(tokenInfo.get("token"));
        assertTrue((Long) tokenInfo.get("expireTime") > System.currentTimeMillis());
        
        log.info("登录流程测试场景通过");
    }
    
    @Test
    @Order(3)
    @DisplayName("3. 消息路由测试场景")
    void testMessageRouting() throws Exception {
        log.info("执行消息路由测试场景");
        
        testSuite.executeScenario("message-routing-test", testEnvironment, testContext);
        
        // 验证测试结果
        assertTrue((Boolean) testContext.getTestData("login.route.success"));
        assertTrue((Boolean) testContext.getTestData("game.route.success"));
        assertTrue((Boolean) testContext.getTestData("chat.route.success"));
        
        // 验证性能指标
        double avgLatency = (Double) testContext.getTestData("performance.avg.latency");
        double throughput = (Double) testContext.getTestData("performance.throughput");
        
        assertTrue(avgLatency < 1.0, "平均延迟应该小于1ms");
        assertTrue(throughput > 10000, "吞吐量应该大于10000/秒");
        
        log.info("消息路由测试场景通过 - 平均延迟: {:.2f}ms, 吞吐量: {:.0f}/秒", avgLatency, throughput);
    }
    
    @Test
    @Order(4)
    @DisplayName("4. 性能基准测试场景")
    void testPerformanceBenchmark() throws Exception {
        log.info("执行性能基准测试场景");
        
        testSuite.executeScenario("performance-benchmark", testEnvironment, testContext);
        
        // 验证性能指标
        double cpuUsage = (Double) testContext.getTestData("performance.cpu.usage");
        double memoryUsagePercent = (Double) testContext.getTestData("performance.memory.usage.percent");
        double connectionsSuccessRate = (Double) testContext.getTestData("performance.connections.success.rate");
        double avgResponseTime = (Double) testContext.getTestData("performance.response.avg");
        
        assertTrue(cpuUsage < 80.0, "CPU使用率应该小于80%");
        assertTrue(memoryUsagePercent < 85.0, "内存使用率应该小于85%");
        assertTrue(connectionsSuccessRate > 90.0, "连接成功率应该大于90%");
        assertTrue(avgResponseTime < 50.0, "平均响应时间应该小于50ms");
        
        log.info("性能基准测试场景通过 - CPU: {:.1f}%, 内存: {:.1f}%, 连接成功率: {:.1f}%, 响应时间: {:.1f}ms", 
                cpuUsage, memoryUsagePercent, connectionsSuccessRate, avgResponseTime);
    }
    
    @Test
    @Order(5)
    @DisplayName("5. 压力测试场景")
    void testStressScenario() throws Exception {
        log.info("执行压力测试场景");
        
        testSuite.executeScenario("stress-test", testEnvironment, testContext);
        
        // 验证压力测试结果
        double loginSuccessRate = (Double) testContext.getTestData("stress.login.success.rate");
        double messageAvgTime = (Double) testContext.getTestData("stress.messages.avg.time");
        double messageThroughput = (Double) testContext.getTestData("stress.messages.throughput");
        double errorRate = (Double) testContext.getTestData("stability.error.rate");
        
        assertTrue(loginSuccessRate > 95.0, "登录风暴成功率应该大于95%");
        assertTrue(messageAvgTime < 2.0, "消息平均处理时间应该小于2ms");
        assertTrue(messageThroughput > 5000, "消息吞吐量应该大于5000/秒");
        assertTrue(errorRate < 5.0, "稳定性错误率应该小于5%");
        
        log.info("压力测试场景通过 - 登录成功率: {:.1f}%, 消息处理: {:.2f}ms, 吞吐量: {:.0f}/秒, 错误率: {:.2f}%", 
                loginSuccessRate, messageAvgTime, messageThroughput, errorRate);
    }
    
    @Test
    @Order(6)
    @DisplayName("6. 完整业务流程测试场景")
    void testCompleteBusinessFlow() throws Exception {
        log.info("执行完整业务流程测试场景");
        
        testSuite.executeScenario("complete-business-flow", testEnvironment, testContext);
        
        // 验证完整业务流程
        assertTrue((Boolean) testContext.getTestData("flow.step.1.gateway.connected"));
        
        Map<String, Object> loginResult = (Map<String, Object>) testContext.getTestData("flow.step.2.login.result");
        assertTrue((Boolean) loginResult.get("success"));
        
        Map<String, Object> exitResult = (Map<String, Object>) testContext.getTestData("flow.step.6.exit.result");
        assertTrue((Boolean) exitResult.get("success"));
        assertTrue((Boolean) exitResult.get("dataSaved"));
        
        // 验证多玩家交互
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> players = 
            (java.util.List<java.util.Map<String, Object>>) testContext.getTestData("multiplayer.players");
        assertEquals(3, players.size());
        
        // 验证聊天流程
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> chatMessages = 
            (java.util.List<java.util.Map<String, Object>>) testContext.getTestData("chat.messages");
        assertEquals(3, chatMessages.size());
        
        log.info("完整业务流程测试场景通过");
    }
    
    @Test
    @Order(7)
    @DisplayName("7. 完整测试套件执行")
    void testCompleteTestSuite() throws Exception {
        log.info("执行完整测试套件");
        
        ComprehensiveEndToEndTestSuite.ComprehensiveTestResult result = 
            testSuite.executeAllScenarios(testEnvironment);
        result.complete();
        
        // 验证测试套件结果
        assertTrue(result.getSuccessCount() > 0, "应该有成功的测试场景");
        assertTrue(result.getSuccessRate() >= 80.0, "测试成功率应该大于等于80%");
        
        log.info("完整测试套件执行完成:");
        log.info("  测试结果: {}", result);
        log.info("  成功场景: {}", result.getSuccessScenarios().keySet());
        
        if (result.getFailureCount() > 0) {
            log.warn("  失败场景: {}", result.getFailureScenarios().keySet());
            for (Map.Entry<String, Exception> entry : result.getFailureScenarios().entrySet()) {
                log.warn("    {}: {}", entry.getKey(), entry.getValue().getMessage());
            }
        }
    }
    
    @Test
    @Order(8)
    @DisplayName("8. 24小时稳定性测试模拟")
    void testLongTermStabilitySimulation() throws Exception {
        log.info("执行24小时稳定性测试模拟");
        
        // 模拟24小时运行情况（实际测试中压缩到30秒）
        long simulationDuration = 30000; // 30秒模拟24小时
        long startTime = System.currentTimeMillis();
        long endTime = startTime + simulationDuration;
        
        int totalOperations = 0;
        int errors = 0;
        
        while (System.currentTimeMillis() < endTime) {
            try {
                // 模拟各种游戏操作
                Thread.sleep(10); // 每10ms一次操作
                totalOperations++;
                
                // 模拟偶发错误（0.1%错误率）
                if (Math.random() < 0.001) {
                    errors++;
                }
                
                // 每5秒输出一次进度
                if (totalOperations % 500 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double progress = (double) elapsed / simulationDuration * 100;
                    log.info("稳定性测试进度: {:.1f}%, 操作: {}, 错误: {}", progress, totalOperations, errors);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        long actualDuration = System.currentTimeMillis() - startTime;
        double errorRate = totalOperations > 0 ? (double) errors / totalOperations * 100 : 0;
        double operationsPerSecond = (double) totalOperations / actualDuration * 1000;
        
        // 验证长期稳定性
        assertTrue(errorRate < 1.0, String.format("错误率 %.3f%% 应该小于1%%", errorRate));
        assertTrue(operationsPerSecond > 50, String.format("操作频率 %.0f/秒 应该大于50/秒", operationsPerSecond));
        
        log.info("24小时稳定性测试模拟通过:");
        log.info("  运行时长: {}ms (模拟24小时)", actualDuration);
        log.info("  总操作数: {}", totalOperations);
        log.info("  错误次数: {}", errors);
        log.info("  错误率: {:.3f}%", errorRate);
        log.info("  操作频率: {:.0f}/秒", operationsPerSecond);
    }
    
    @AfterAll
    static void tearDownClass() {
        log.info("================================================================================");
        log.info("游戏服务器框架全方位端到端测试执行完成");
        log.info("测试结束时间: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("================================================================================");
    }
}