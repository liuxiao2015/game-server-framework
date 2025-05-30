/*
 * 文件名: RpcAutoConfiguration.java
 * 用途: RPC模块自动配置类
 * 实现内容:
 *   - 自动装配所有RPC相关组件
 *   - 条件化配置
 *   - 配置属性绑定
 *   - 组件扫描
 * 技术选型:
 *   - Spring Boot自动配置
 *   - 条件注解
 *   - 配置属性
 * 依赖关系:
 *   - 集成所有配置类
 *   - 被Spring Boot自动发现
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC模块自动配置类
 * <p>
 * 自动装配RPC模块的所有组件，包括Feign客户端、负载均衡器、
 * 熔断器、配置属性等。通过条件注解控制组件的启用。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnProperty(prefix = "game.rpc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RpcProperties.class)
@EnableFeignClients(basePackages = "com.lx.gameserver")
@ComponentScan(basePackages = "com.lx.gameserver.frame.rpc")
@Import({
    FeignConfig.class,
    LoadBalancerConfig.class,
    CircuitBreakerConfig.class
})
public class RpcAutoConfiguration {

    /**
     * 配置说明
     * <p>
     * 本配置类会自动装配以下组件：
     * - Feign客户端配置（编码器、解码器、错误处理器等）
     * - 负载均衡器配置（支持多种负载均衡策略）
     * - 熔断器配置（基于Resilience4j）
     * - RPC属性配置（统一配置管理）
     * - 自动扫描Feign客户端接口
     * </p>
     */
}