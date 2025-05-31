/*
 * 文件名: GatewayApplicationTest.java
 * 用途: Gateway应用启动测试
 * 实现内容:
 *   - Spring Boot应用上下文加载测试
 *   - 基础配置验证
 * 技术选型:
 *   - Spring Boot Test
 *   - JUnit 5
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Gateway应用启动测试
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@SpringBootTest(classes = GatewayApplication.class)
@TestPropertySource(properties = {
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false"
})
public class GatewayApplicationTest {

    @Test
    void contextLoads() {
        // 测试Spring上下文是否能正常加载
    }
}