/*
 * 文件名: GatewayConfiguration.java
 * 用途: Spring Cloud Gateway核心配置
 * 实现内容:
 *   - Gateway核心配置Bean
 *   - 全局过滤器配置
 *   - 路由谓词工厂配置
 *   - 过滤器工厂配置
 *   - 负载均衡配置
 *   - 超时配置（连接超时、响应超时）
 * 技术选型:
 *   - Spring Cloud Gateway
 *   - Spring Cloud LoadBalancer
 *   - WebFlux响应式编程
 * 依赖关系:
 *   - 被GatewayApplication启动类使用
 *   - 与各种Filter和PredicateFactory集成
 *   - 配置全局路由策略
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Spring Cloud Gateway核心配置类
 * <p>
 * 提供Gateway的核心配置，包括全局过滤器、路由规则、负载均衡策略、
 * 超时配置等。支持高并发场景下的请求处理和路由转发。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Configuration
public class GatewayConfiguration {

    /**
     * 配置WebClient用于下游服务调用
     * <p>
     * 配置连接池、超时时间、重试策略等，支持高并发访问。
     * </p>
     *
     * @return 配置好的WebClient
     */
    @Bean
    public WebClient webClient() {
        // 配置连接池
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gateway-pool")
                .maxConnections(1000)                    // 最大连接数
                .maxIdleTime(Duration.ofSeconds(30))     // 连接空闲时间
                .maxLifeTime(Duration.ofMinutes(5))      // 连接最大生存时间
                .pendingAcquireTimeout(Duration.ofSeconds(10)) // 获取连接超时时间
                .evictInBackground(Duration.ofSeconds(120))    // 后台清理间隔
                .build();

        // 配置HttpClient
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 连接超时
                .responseTimeout(Duration.ofSeconds(10))  // 响应超时
                .keepAlive(true)                         // 启用Keep-Alive
                .compress(true);                         // 启用压缩

        // 创建WebClient
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    // 配置编解码器
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
                })
                .build();
    }

    /**
     * 自定义路由配置
     * <p>
     * 定义默认路由规则，包括游戏服务、认证服务等的路由配置。
     * </p>
     *
     * @param builder 路由构建器
     * @return 路由定位器
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 游戏服务路由
                .route("game-service", r -> r
                        .path("/api/game/**")
                        .filters(f -> f
                                .stripPrefix(2)                    // 移除路径前缀
                                .addRequestHeader("X-Gateway", "game-gateway")
                                .addResponseHeader("X-Response-Source", "game-service")
                                .retry(config -> config
                                        .setRetries(3)             // 重试次数
                                        .setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(1), 2, false)
                                )
                        )
                        .uri("lb://game-service"))
                
                // 认证服务路由
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .addRequestHeader("X-Gateway", "game-gateway")
                                .addResponseHeader("X-Response-Source", "auth-service")
                        )
                        .uri("lb://auth-service"))
                
                // WebSocket路由
                .route("websocket-route", r -> r
                        .path("/ws/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "game-gateway")
                        )
                        .uri("lb://game-service"))
                
                // 健康检查路由
                .route("health-check", r -> r
                        .path("/actuator/health")
                        .filters(f -> f
                                .addResponseHeader("X-Health-Check", "gateway")
                        )
                        .uri("http://localhost:8080"))
                
                .build();
    }

    /**
     * 请求日志过滤器
     * <p>
     * 记录请求的基本信息，包括请求路径、方法、耗时等。
     * </p>
     *
     * @return 全局过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            String requestPath = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();
            
            log.debug("Gateway处理请求: {} {}", method, requestPath);
            
            return chain.filter(exchange).doFinally(signalType -> {
                long endTime = System.currentTimeMillis();
                log.debug("Gateway请求完成: {} {} - 耗时: {}ms", 
                    method, requestPath, endTime - startTime);
            });
        };
    }

    /**
     * 全局异常处理过滤器
     * <p>
     * 统一处理Gateway层面的异常，提供友好的错误响应。
     * </p>
     *
     * @return 全局过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public GlobalFilter globalExceptionFilter() {
        return (exchange, chain) -> {
            return chain.filter(exchange)
                    .onErrorResume(throwable -> {
                        log.error("Gateway处理请求异常: {}", throwable.getMessage(), throwable);
                        
                        // 设置错误响应
                        exchange.getResponse().setStatusCode(
                            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
                        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                        
                        String errorResponse = "{\"code\":500,\"message\":\"网关服务异常\",\"timestamp\":" + 
                            System.currentTimeMillis() + "}";
                        
                        org.springframework.core.io.buffer.DataBuffer buffer = 
                            exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes());
                        
                        return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                    });
        };
    }

    /**
     * 响应头增强过滤器
     * <p>
     * 为所有响应添加通用的响应头信息。
     * </p>
     *
     * @return 全局过滤器
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public GlobalFilter responseHeaderFilter() {
        return (exchange, chain) -> {
            return chain.filter(exchange).then(
                reactor.core.publisher.Mono.fromRunnable(() -> {
                    // 添加响应头
                    exchange.getResponse().getHeaders().add("X-Gateway-Version", "1.0.0");
                    exchange.getResponse().getHeaders().add("X-Request-ID", 
                        exchange.getRequest().getId());
                    exchange.getResponse().getHeaders().add("X-Response-Time", 
                        String.valueOf(System.currentTimeMillis()));
                })
            );
        };
    }
}