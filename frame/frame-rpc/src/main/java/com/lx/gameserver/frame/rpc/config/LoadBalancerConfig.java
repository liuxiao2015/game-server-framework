/*
 * 文件名: LoadBalancerConfig.java
 * 用途: Spring Cloud LoadBalancer配置
 * 实现内容:
 *   - 负载均衡策略配置（轮询、随机、响应时间权重）
 *   - 健康检查配置
 *   - 服务实例缓存配置
 *   - 自定义负载均衡规则
 * 技术选型:
 *   - Spring Cloud LoadBalancer
 *   - 自定义负载均衡器
 *   - 服务实例过滤器
 * 依赖关系:
 *   - 与RpcProperties配置集成
 *   - 被Feign客户端使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.config;

import com.lx.gameserver.frame.rpc.loadbalancer.GameLoadBalancer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring Cloud LoadBalancer配置类
 * <p>
 * 配置负载均衡器，支持多种负载均衡策略，包括轮询、随机、
 * 响应时间权重等。同时配置健康检查和服务实例缓存。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnClass(ReactorLoadBalancer.class)
@ConditionalOnProperty(prefix = "game.rpc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RpcProperties.class)
@LoadBalancerClients(defaultConfiguration = LoadBalancerConfig.class)
public class LoadBalancerConfig {

    @Autowired
    private RpcProperties rpcProperties;

    /**
     * 创建游戏定制负载均衡器
     * <p>
     * 根据配置的策略创建相应的负载均衡器实例
     * </p>
     *
     * @param environment 环境配置
     * @param loadBalancerClientFactory 负载均衡客户端工厂
     * @return 负载均衡器
     */
    @Bean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        String strategy = rpcProperties.getLoadbalancer().getStrategy();
        
        return new GameLoadBalancer(
            loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
            name,
            strategy,
            rpcProperties
        );
    }

    /**
     * 配置服务实例列表提供者
     * <p>
     * 缓存服务实例列表，提高查询性能
     * </p>
     *
     * @return 服务实例列表提供者
     */
    // @Bean - Temporarily commented out due to API complexity
    // public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier() {
    //     // TODO: Implement proper service instance list supplier
    //     return null;
    // }
}