/*
 * 文件名: CircuitBreakerConfig.java
 * 用途: 熔断器配置（使用Resilience4j）
 * 实现内容:
 *   - 熔断阈值配置（失败率、慢调用率）
 *   - 熔断时间窗口配置
 *   - 半开状态配置
 *   - 熔断降级处理
 * 技术选型:
 *   - Resilience4j CircuitBreaker
 *   - Spring Boot自动配置
 *   - 自定义熔断策略
 * 依赖关系:
 *   - 与RpcProperties配置集成
 *   - 与Feign客户端集成
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 熔断器配置类
 * <p>
 * 配置Resilience4j熔断器，提供熔断保护机制。当服务调用失败率
 * 或慢调用率超过阈值时，自动熔断以保护系统稳定性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(prefix = "game.rpc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RpcProperties.class)
public class CircuitBreakerConfig {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    @Autowired
    private RpcProperties rpcProperties;

    /**
     * 创建熔断器注册表
     * <p>
     * 配置默认的熔断器参数，用于创建熔断器实例
     * </p>
     *
     * @return 熔断器注册表
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        RpcProperties.CircuitBreakerProperties cbConfig = rpcProperties.getCircuitBreaker();
        
        // 创建熔断器配置
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = 
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            // 失败率阈值（百分比）
            .failureRateThreshold(cbConfig.getFailureRateThreshold())
            // 慢调用率阈值（百分比）
            .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
            // 慢调用时间阈值
            .slowCallDurationThreshold(cbConfig.getSlowCallDurationThreshold())
            // 滑动窗口大小
            .slidingWindowSize(cbConfig.getSlidingWindowSize())
            // 最小调用次数
            .minimumNumberOfCalls(cbConfig.getMinimumNumberOfCalls())
            // 熔断器打开状态持续时间
            .waitDurationInOpenState(cbConfig.getWaitDurationInOpenState())
            // 半开状态下允许的调用次数
            .permittedNumberOfCallsInHalfOpenState(5)
            // 自动从半开状态转换到关闭状态
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // 忽略的异常类型（这些异常不会触发熔断）
            .ignoreExceptions(IllegalArgumentException.class)
            // 记录的异常类型（这些异常会被记录但不触发熔断）
            .recordExceptions(Exception.class)
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        
        // 添加事件监听器
        registry.getEventPublisher()
            .onEntryAdded(event -> logger.info("熔断器创建: {}", event.getAddedEntry().getName()))
            .onEntryRemoved(event -> logger.info("熔断器移除: {}", event.getRemovedEntry().getName()));
        
        return registry;
    }

    /**
     * 创建默认熔断器
     * <p>
     * 为没有特定配置的服务提供默认熔断器
     * </p>
     *
     * @param registry 熔断器注册表
     * @return 默认熔断器
     */
    @Bean
    public CircuitBreaker defaultCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("default");
        
        // 添加事件监听器
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                logger.info("熔断器状态变化: {} -> {} ({})", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState(),
                    event.getCircuitBreakerName()))
            .onFailureRateExceeded(event -> 
                logger.warn("熔断器失败率超过阈值: {}% ({})", 
                    event.getFailureRate(),
                    event.getCircuitBreakerName()))
            .onSlowCallRateExceeded(event -> 
                logger.warn("熔断器慢调用率超过阈值: {}% ({})", 
                    event.getSlowCallRate(),
                    event.getCircuitBreakerName()));
        
        return circuitBreaker;
    }

    /**
     * 创建Feign装饰器
     * <p>
     * 为Feign客户端添加熔断器支持
     * </p>
     *
     * @param circuitBreaker 熔断器
     * @return Feign装饰器
     */
    @Bean
    public FeignDecorators feignDecorators(CircuitBreaker circuitBreaker) {
        return FeignDecorators.builder()
            .withCircuitBreaker(circuitBreaker)
            .build();
    }
}