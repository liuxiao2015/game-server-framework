/*
 * 文件名: ConfigManagerTest.java
 * 用途: 配置管理器测试
 * 内容: 
 *   - 测试配置加载和获取
 *   - 验证配置变更监听
 *   - 测试配置缓存机制
 * 技术选型: 
 *   - JUnit 5测试框架
 *   - Mockito模拟框架
 * 依赖关系: 
 *   - 测试frame-config模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.frame.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 配置管理器测试
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@DisplayName("配置管理器测试")
class ConfigManagerTest {
    
    @Mock
    private Environment environment;
    
    @Mock
    private ApplicationContext applicationContext;
    
    private ConfigManager configManager;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configManager = new ConfigManager();
        // 注入模拟的依赖
        configManager.environment = environment;
        configManager.applicationContext = applicationContext;
    }
    
    @Test
    @DisplayName("测试配置获取")
    void testGetConfig() {
        // 模拟环境变量
        when(environment.getProperty("test.key", String.class)).thenReturn("test_value");
        
        String value = configManager.getConfig("test.key", String.class);
        assertEquals("test_value", value);
        
        // 验证缓存机制
        String cachedValue = configManager.getConfig("test.key", String.class);
        assertEquals("test_value", cachedValue);
        
        // 验证Environment只调用一次（缓存生效）
        verify(environment, times(1)).getProperty("test.key", String.class);
    }
    
    @Test
    @DisplayName("测试配置默认值")
    void testGetConfigWithDefault() {
        when(environment.getProperty("missing.key", String.class, "default_value")).thenReturn("default_value");
        
        String value = configManager.getConfig("missing.key", String.class, "default_value");
        assertEquals("default_value", value);
    }
    
    @Test
    @DisplayName("测试数值类型配置")
    void testGetIntegerConfig() {
        when(environment.getProperty("int.key", Integer.class)).thenReturn(42);
        
        Integer value = configManager.getConfig("int.key", Integer.class);
        assertEquals(42, value);
    }
    
    @Test
    @DisplayName("测试布尔类型配置")
    void testGetBooleanConfig() {
        when(environment.getProperty("bool.key", Boolean.class)).thenReturn(true);
        
        Boolean value = configManager.getConfig("bool.key", Boolean.class);
        assertTrue(value);
    }
    
    @Test
    @DisplayName("测试配置变更监听")
    void testConfigChangeListener() {
        final String[] receivedValue = {null};
        
        // 添加监听器
        configManager.addConfigChangeListener("test.key", (newValue) -> {
            receivedValue[0] = (String) newValue;
        });
        
        // 模拟配置更新
        configManager.updateConfig("test.key", "new_value");
        
        // 验证监听器被调用
        assertEquals("new_value", receivedValue[0]);
    }
    
    @Test
    @DisplayName("测试配置缓存清理")
    void testCacheClear() {
        when(environment.getProperty("cache.key", String.class)).thenReturn("cache_value");
        
        // 获取配置并缓存
        String value1 = configManager.getConfig("cache.key", String.class);
        assertEquals("cache_value", value1);
        
        // 清理缓存
        configManager.clearCache();
        
        // 再次获取配置
        String value2 = configManager.getConfig("cache.key", String.class);
        assertEquals("cache_value", value2);
        
        // 验证Environment被调用了两次（缓存清理后重新加载）
        verify(environment, times(2)).getProperty("cache.key", String.class);
    }
}