/*
 * 文件名: AdminApplicationTest.java
 * 用途: 管理后台应用启动测试
 * 实现内容:
 *   - 应用上下文加载测试
 *   - 基本功能验证测试
 *   - 配置正确性测试
 * 技术选型:
 *   - JUnit 5 (测试框架)
 *   - Spring Boot Test (集成测试)
 *   - Mockito (模拟框架)
 * 依赖关系: 测试AdminApplication和相关核心组件
 */
package com.lx.gameserver.admin.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 管理后台应用启动测试
 * <p>
 * 测试管理后台应用的基本启动功能和核心组件的正确性。
 * 验证Spring容器的正确配置和依赖注入。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "admin.plugins.auto-load=false",
    "admin.security.jwt.secret=test-secret-key-for-junit-testing-only"
})
class AdminApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AdminContext adminContext;

    @Autowired
    private PluginManager pluginManager;

    /**
     * 测试应用上下文加载
     * <p>
     * 验证Spring Boot应用能够正确启动，
     * 所有必要的Bean都能正确创建和配置。
     * </p>
     */
    @Test
    void contextLoads() {
        // 验证应用上下文正确加载
        assertNotNull(applicationContext, "应用上下文应该正确加载");
        
        // 验证核心组件正确注入
        assertNotNull(adminContext, "管理平台上下文应该正确注入");
        assertNotNull(pluginManager, "插件管理器应该正确注入");
    }

    /**
     * 测试应用基本信息
     * <p>
     * 验证应用的基本信息配置正确。
     * </p>
     */
    @Test
    void testApplicationInfo() {
        // 验证应用上下文已初始化
        assertNotNull(applicationContext, "应用上下文应该已初始化");
        
        // 验证管理平台上下文已初始化
        assertNotNull(adminContext, "管理平台上下文应该已初始化");
        assertNotNull(adminContext.getApplicationContext(), "Spring应用上下文应该可访问");
    }

    /**
     * 测试核心组件
     * <p>
     * 验证核心组件的基本功能。
     * </p>
     */
    @Test
    void testCoreComponents() {
        // 验证插件管理器
        assertNotNull(pluginManager, "插件管理器应该正确创建");
        assertNotNull(pluginManager.getLoadedPlugins(), "已加载插件列表应该不为空");
        
        // 验证管理平台上下文
        assertNotNull(adminContext.getApplicationContext(), "应用上下文应该可访问");
        assertNotNull(adminContext.getEnvironment(), "环境配置应该可访问");
    }
}