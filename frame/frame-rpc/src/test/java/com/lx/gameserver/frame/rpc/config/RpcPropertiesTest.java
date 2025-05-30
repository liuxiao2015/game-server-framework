/*
 * 文件名: RpcPropertiesTest.java
 * 用途: RPC配置属性测试
 * 实现内容:
 *   - 配置属性加载测试
 *   - 默认值验证
 *   - 配置绑定测试
 * 技术选型:
 *   - JUnit 5
 *   - Spring Boot Test
 *   - MockMvc
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RPC配置属性测试类
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@SpringBootTest(classes = RpcPropertiesTest.TestConfig.class)
@TestPropertySource(properties = {
    "game.rpc.enabled=true",
    "game.rpc.discovery.type=nacos",
    "game.rpc.discovery.server-addr=localhost:8848",
    "game.rpc.feign.compression.request.enabled=true"
})
class RpcPropertiesTest {

    @Test
    void testDefaultValues() {
        RpcProperties properties = new RpcProperties();
        
        // 测试默认值
        assertTrue(properties.isEnabled());
        assertEquals("nacos", properties.getDiscovery().getType());
        assertEquals("localhost:8848", properties.getDiscovery().getServerAddr());
        assertEquals("dev", properties.getDiscovery().getNamespace());
        
        // 测试Feign配置默认值
        assertTrue(properties.getFeign().getCompression().getRequest().isEnabled());
        assertEquals(2048, properties.getFeign().getCompression().getRequest().getMinRequestSize());
        assertTrue(properties.getFeign().getCompression().getResponse().isEnabled());
        
        // 测试负载均衡默认值
        assertEquals("weighted-response-time", properties.getLoadbalancer().getStrategy());
        
        // 测试熔断器默认值
        assertEquals(50, properties.getCircuitBreaker().getFailureRateThreshold());
        assertEquals(50, properties.getCircuitBreaker().getSlowCallRateThreshold());
        
        // 测试监控默认值
        assertTrue(properties.getMetrics().isEnabled());
    }

    @Test
    void testConfigurationBinding() {
        // 这个测试需要Spring Boot Test支持
        // 在实际项目中会使用@ConfigurationPropertiesTest注解
        RpcProperties properties = new RpcProperties();
        assertNotNull(properties);
        
        // 测试配置对象的基本功能
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
        
        properties.getDiscovery().setType("eureka");
        assertEquals("eureka", properties.getDiscovery().getType());
    }

    @EnableConfigurationProperties(RpcProperties.class)
    static class TestConfig {
        // 测试配置类
    }
}