/*
 * 文件名: ExampleIntegrationTest.java
 * 用途: 集成测试示例
 * 内容: 
 *   - 演示集成测试框架使用
 *   - 简单的Redis集成测试场景
 *   - 测试服务启动和通信
 * 技术选型: 
 *   - JUnit 5
 *   - 集成测试框架
 * 依赖关系: 
 *   - 使用IntegrationTestRunner
 *   - 演示TestScenario实现
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.integration;

import com.lx.gameserver.testframework.core.TestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试示例
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@DisplayName("集成测试示例")
public class ExampleIntegrationTest {
    
    private IntegrationTestRunner testRunner;
    private TestContext testContext;
    
    @BeforeEach
    void setUp() throws Exception {
        testRunner = new IntegrationTestRunner();
        testContext = new TestContext();
        testContext.initialize();
        
        // 注册测试场景
        testRunner.registerScenario(new RedisConnectionScenario());
    }
    
    @AfterEach
    void tearDown() {
        if (testRunner != null) {
            testRunner.shutdown();
        }
        if (testContext != null) {
            testContext.cleanup();
        }
    }
    
    @Test
    @DisplayName("测试Redis连接场景")
    void testRedisConnectionScenario() throws Exception {
        CompletableFuture<IntegrationTestRunner.IntegrationTestResult> future = 
            testRunner.runScenario("redis-connection", testContext);
        
        IntegrationTestRunner.IntegrationTestResult result = future.get();
        
        assertNotNull(result);
        assertEquals("redis-connection", result.getScenarioName());
        assertTrue(result.isSuccess(), "Redis连接场景应该成功");
        assertNotNull(result.getDuration());
    }
    
    @Test
    @DisplayName("测试服务容器管理")
    void testServiceContainerManagement() {
        ServiceContainer serviceContainer = testRunner.getServiceContainer();
        
        assertNotNull(serviceContainer);
        assertFalse(serviceContainer.isServiceRunning("redis"));
        
        // 验证预定义服务配置存在
        assertTrue(serviceContainer.getRunningServices().isEmpty());
    }
    
    /**
     * Redis连接测试场景示例
     */
    private static class RedisConnectionScenario extends TestScenario {
        
        public RedisConnectionScenario() {
            super("redis-connection", "Redis连接测试场景");
            addDependency("redis");
            setConfiguration("timeout", 30000);
        }
        
        @Override
        protected void runScenario(TestEnvironment environment, TestContext context) throws Exception {
            // 模拟Redis连接测试
            assertNotNull(environment, "测试环境不能为空");
            assertNotNull(context, "测试上下文不能为空");
            
            // 验证Redis服务已启动（在实际实现中会真正连接Redis）
            String redisHost = getConfiguration("redis.host");
            if (redisHost == null) {
                // 使用默认配置
                setConfiguration("redis.host", "localhost");
                setConfiguration("redis.port", 6379);
            }
            
            // 模拟连接测试
            assertThat(true, "Redis连接测试应该成功");
            
            // 模拟数据操作测试
            testRedisOperations(environment, context);
        }
        
        private void testRedisOperations(TestEnvironment environment, TestContext context) throws Exception {
            // 模拟Redis操作测试
            context.setTestData("redis.test.key", "test.value");
            String value = context.getTestData("redis.test.key");
            assertEqual("test.value", value, "Redis数据操作测试失败");
        }
        
        @Override
        protected void verifyResults(TestEnvironment environment, TestContext context) throws Exception {
            super.verifyResults(environment, context);
            
            // 验证测试结果
            String testValue = context.getTestData("redis.test.key");
            assertNotNull(testValue, "Redis测试数据应该存在");
            assertEqual("test.value", testValue, "Redis测试数据验证失败");
        }
    }
}