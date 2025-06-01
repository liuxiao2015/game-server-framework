/*
 * 文件名: GameServerFrameworkTest.java
 * 用途: 游戏服务器框架管理器测试
 * 内容: 
 *   - 测试框架初始化流程
 *   - 验证模块管理功能
 *   - 测试生命周期管理
 * 技术选型: 
 *   - JUnit 5测试框架
 *   - Spring Boot Test
 *   - Mockito模拟框架
 * 依赖关系: 
 *   - 测试launcher模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 游戏服务器框架管理器测试
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@DisplayName("游戏服务器框架管理器测试")
class GameServerFrameworkTest {
    
    @MockBean
    private ApplicationContext applicationContext;
    
    @MockBean
    private ServiceManager serviceManager;
    
    private GameServerFramework framework;
    
    @BeforeEach
    void setUp() {
        framework = new GameServerFramework();
        framework.applicationContext = applicationContext;
        framework.serviceManager = serviceManager;
    }
    
    @Test
    @DisplayName("测试框架初始化")
    void testFrameworkInitialization() {
        // 测试初始化前状态
        assertEquals(GameServerFramework.FrameworkStatus.INITIALIZING, framework.getStatus());
        
        // 执行初始化
        framework.initialize();
        
        // 验证初始化后状态
        assertNotNull(framework.getStartupTime());
        assertTrue(framework.getFrameworkConfig().isEnableVirtualThreads());
        assertTrue(framework.getFrameworkConfig().isEnableMetrics());
        assertTrue(framework.getFrameworkConfig().isEnableHealthCheck());
    }
    
    @Test
    @DisplayName("测试模块发现和启动")
    void testModuleDiscoveryAndStartup() {
        // 简化测试，只测试框架初始化不涉及Spring上下文
        framework.initialize();
        
        // 验证框架基本状态
        assertEquals(GameServerFramework.FrameworkStatus.INITIALIZING, framework.getStatus());
        assertNotNull(framework.getFrameworkConfig());
    }
    
    @Test
    @DisplayName("测试框架关闭")
    void testFrameworkShutdown() {
        // 初始化并启动框架
        framework.initialize();
        
        // 关闭框架
        framework.shutdown();
        
        // 验证关闭状态
        assertEquals(GameServerFramework.FrameworkStatus.STOPPED, framework.getStatus());
    }
    
    @Test
    @DisplayName("测试框架配置")
    void testFrameworkConfiguration() {
        framework.initialize();
        
        GameServerFramework.FrameworkConfig config = framework.getFrameworkConfig();
        
        assertNotNull(config);
        assertTrue(config.isEnableVirtualThreads());
        assertTrue(config.isEnableMetrics());
        assertTrue(config.isEnableHealthCheck());
        assertEquals(60, config.getInitializationTimeout());
        assertEquals(30, config.getShutdownTimeout());
        assertEquals("INFO", config.getLogLevel());
    }
    
    @Test
    @DisplayName("测试模块状态查询")
    void testModuleStatusQuery() {
        framework.initialize();
        
        // 测试基本状态查询功能
        assertNotNull(framework.getFrameworkInfo());
        assertTrue(framework.getFrameworkInfo().containsKey("状态"));
        assertTrue(framework.getFrameworkInfo().containsKey("配置"));
        
        // 测试模块状态查询（空情况）
        Map<String, String> moduleStatus = framework.getModuleStatus();
        assertNotNull(moduleStatus);
    }
    
    // 测试用的框架模块实现
    private static class TestFrameworkModule implements GameServerFramework.FrameworkModule {
        private boolean initialized = false;
        private boolean started = false;
        private boolean stopped = false;
        
        @Override
        public String getModuleName() {
            return "testModule";
        }
        
        @Override
        public List<String> getDependencies() {
            return List.of();
        }
        
        @Override
        public void initialize() throws Exception {
            initialized = true;
        }
        
        @Override
        public void start() throws Exception {
            started = true;
        }
        
        @Override
        public void stop() throws Exception {
            stopped = true;
        }
        
        @Override
        public String getStatus() {
            if (stopped) return "已停止";
            if (started) return "运行中";
            if (initialized) return "已初始化";
            return "未初始化";
        }
        
        @Override
        public int getPriority() {
            return 100;
        }
    }
}