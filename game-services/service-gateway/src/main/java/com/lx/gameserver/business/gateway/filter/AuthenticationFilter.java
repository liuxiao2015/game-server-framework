/*
 * 文件名: AuthenticationFilter.java
 * 用途: 身份认证过滤器
 * 实现内容:
 *   - JWT Token验证
 *   - 用户身份认证
 *   - 认证失败处理
 *   - 白名单路径跳过认证
 * 技术选型:
 *   - Spring Cloud Gateway Filter
 *   - JWT Token验证
 *   - WebFlux响应式处理
 * 依赖关系:
 *   - 与SecurityConfiguration协作
 *   - 集成TokenValidator
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 身份认证过滤器
 * <p>
 * 对请求进行身份认证，验证JWT Token的有效性，
 * 对于白名单路径跳过认证，认证失败返回401状态码。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // 白名单路径（无需认证）
    private final List<String> whiteListPaths = Arrays.asList(
        "/api/auth/login",
        "/api/auth/register", 
        "/api/public/**",
        "/actuator/health",
        "/favicon.ico"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // 检查是否在白名单中
        if (isWhiteListPath(path)) {
            log.debug("请求路径在白名单中，跳过认证: {}", path);
            return chain.filter(exchange);
        }
        
        // 提取Authorization头
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少有效的Authorization头: {}", path);
            return handleAuthenticationFailure(exchange, "缺少认证信息");
        }
        
        // 提取Token
        String token = authHeader.substring(7);
        
        // 验证Token
        return validateToken(token)
            .flatMap(isValid -> {
                if (isValid) {
                    log.debug("Token验证成功，路径: {}", path);
                    // 将用户信息添加到请求头中传递给下游服务
                    ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-ID", extractUserIdFromToken(token))
                        .header("X-User-Role", extractUserRoleFromToken(token))
                        .build();
                    
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                } else {
                    log.warn("Token验证失败，路径: {}", path);
                    return handleAuthenticationFailure(exchange, "认证信息无效");
                }
            })
            .onErrorResume(error -> {
                log.error("认证过程发生异常，路径: {}", path, error);
                return handleAuthenticationFailure(exchange, "认证服务异常");
            });
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，在其他过滤器之前执行
    }

    /**
     * 检查路径是否在白名单中
     *
     * @param path 请求路径
     * @return 是否在白名单中
     */
    private boolean isWhiteListPath(String path) {
        return whiteListPaths.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 验证Token
     *
     * @param token JWT Token
     * @return 验证结果
     */
    private Mono<Boolean> validateToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                // TODO: 实现实际的JWT Token验证逻辑
                // 这里简化处理，实际应该验证签名、过期时间等
                return token != null && token.length() > 10;
            } catch (Exception e) {
                log.error("Token验证异常", e);
                return false;
            }
        });
    }

    /**
     * 从Token中提取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    private String extractUserIdFromToken(String token) {
        // TODO: 实现实际的用户ID提取逻辑
        return "user123"; // 简化实现
    }

    /**
     * 从Token中提取用户角色
     *
     * @param token JWT Token
     * @return 用户角色
     */
    private String extractUserRoleFromToken(String token) {
        // TODO: 实现实际的用户角色提取逻辑
        return "USER"; // 简化实现
    }

    /**
     * 处理认证失败
     *
     * @param exchange ServerWebExchange
     * @param message 错误消息
     * @return 响应结果
     */
    private Mono<Void> handleAuthenticationFailure(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json; charset=UTF-8");
        
        String errorResponse = String.format(
            "{\"code\":401,\"message\":\"%s\",\"timestamp\":%d}", 
            message, System.currentTimeMillis()
        );
        
        org.springframework.core.io.buffer.DataBuffer buffer = 
            exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes());
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}