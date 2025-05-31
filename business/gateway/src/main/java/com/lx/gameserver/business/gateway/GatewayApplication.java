/*
 * 文件名: GatewayApplication.java
 * 用途: 网关服务启动类
 * 实现内容:
 *   - Spring Boot应用启动入口
 *   - Gateway服务配置激活
 *   - 服务发现和配置中心集成
 * 技术选型:
 *   - Spring Boot 3.2+
 *   - Spring Cloud Gateway
 *   - Nacos服务发现
 * 依赖关系:
 *   - 作为Gateway服务的启动入口
 *   - 加载所有配置和组件
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 游戏服务器网关应用启动类
 * <p>
 * 统一网关服务的启动入口，提供API网关、路由转发、
 * 安全认证、限流熔断等企业级功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}