/*
 * 文件名: RequestLoggingFilter.java
 * 用途: 请求日志记录过滤器
 * 实现内容:
 *   - 请求响应日志记录
 *   - 性能统计（响应时间）
 *   - 请求追踪ID生成
 *   - 敏感信息脱敏
 * 技术选型:
 *   - Spring Cloud Gateway Filter
 *   - SLF4J日志框架
 *   - WebFlux响应式处理
 * 依赖关系:
 *   - 集成MDC上下文传递
 *   - 与监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 请求日志记录过滤器
 * <p>
 * 记录每个请求的详细信息，包括请求路径、方法、响应时间、状态码等。
 * 生成唯一的追踪ID用于请求链路追踪，支持敏感信息脱敏。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String START_TIME_ATTR = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 生成追踪ID
        String traceId = generateTraceId();
        
        // 设置追踪ID到MDC
        MDC.put("traceId", traceId);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);
        
        // 添加追踪ID到响应头
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(TRACE_ID_HEADER, traceId);
        
        // 记录请求信息
        logRequest(request, traceId);
        
        return chain.filter(exchange)
            .doFinally(signalType -> {
                // 记录响应信息
                logResponse(exchange, traceId, startTime);
                // 清理MDC
                MDC.clear();
            });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 最高优先级
    }

    /**
     * 生成追踪ID
     *
     * @return 追踪ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 记录请求信息
     *
     * @param request HTTP请求
     * @param traceId 追踪ID
     */
    private void logRequest(ServerHttpRequest request, String traceId) {
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        String query = request.getURI().getQuery();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        
        // 构建请求日志
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Gateway请求开始 - ");
        logBuilder.append("TraceId: ").append(traceId);
        logBuilder.append(", Method: ").append(method);
        logBuilder.append(", Path: ").append(path);
        
        if (query != null && !query.isEmpty()) {
            logBuilder.append(", Query: ").append(maskSensitiveInfo(query));
        }
        
        logBuilder.append(", ClientIP: ").append(clientIp);
        
        if (userAgent != null) {
            logBuilder.append(", UserAgent: ").append(userAgent);
        }
        
        // 记录认证信息（脱敏）
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null) {
            logBuilder.append(", Auth: ").append(maskAuthHeader(authHeader));
        }
        
        log.info(logBuilder.toString());
    }

    /**
     * 记录响应信息
     *
     * @param exchange ServerWebExchange
     * @param traceId 追踪ID
     * @param startTime 开始时间
     */
    private void logResponse(ServerWebExchange exchange, String traceId, long startTime) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
        
        // 构建响应日志
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Gateway请求完成 - ");
        logBuilder.append("TraceId: ").append(traceId);
        logBuilder.append(", Method: ").append(method);
        logBuilder.append(", Path: ").append(path);
        logBuilder.append(", Status: ").append(statusCode);
        logBuilder.append(", Duration: ").append(duration).append("ms");
        
        // 根据状态码和响应时间决定日志级别
        if (statusCode >= 500) {
            log.error(logBuilder.toString());
        } else if (statusCode >= 400) {
            log.warn(logBuilder.toString());
        } else if (duration > 5000) { // 响应时间超过5秒
            log.warn(logBuilder.toString() + " [SLOW_REQUEST]");
        } else {
            log.info(logBuilder.toString());
        }
        
        // 记录性能指标
        recordMetrics(method, path, statusCode, duration);
    }

    /**
     * 获取客户端IP地址
     *
     * @param request HTTP请求
     * @return 客户端IP
     */
    private String getClientIp(ServerHttpRequest request) {
        // 优先从X-Forwarded-For头获取
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 其次从X-Real-IP头获取
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        // 最后从RemoteAddress获取
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * 敏感信息脱敏
     *
     * @param info 原始信息
     * @return 脱敏后的信息
     */
    private String maskSensitiveInfo(String info) {
        if (info == null || info.isEmpty()) {
            return info;
        }
        
        // 脱敏密码、token等敏感参数
        return info.replaceAll("(?i)(password|token|secret|key)=([^&]*)", "$1=***")
                  .replaceAll("(?i)(pwd|pass)=([^&]*)", "$1=***");
    }

    /**
     * 认证头脱敏
     *
     * @param authHeader 认证头
     * @return 脱敏后的认证头
     */
    private String maskAuthHeader(String authHeader) {
        if (authHeader == null || authHeader.length() <= 10) {
            return "***";
        }
        
        // 只显示前缀和后几位
        return authHeader.substring(0, 10) + "..." + 
               authHeader.substring(authHeader.length() - 4);
    }

    /**
     * 记录性能指标
     *
     * @param method HTTP方法
     * @param path 请求路径
     * @param statusCode 状态码
     * @param duration 响应时间
     */
    private void recordMetrics(String method, String path, int statusCode, long duration) {
        try {
            // TODO: 集成实际的监控系统（如Micrometer）
            // 这里可以记录各种指标：
            // - 请求计数
            // - 响应时间分布
            // - 错误率统计
            // - QPS统计等
            
            log.debug("记录性能指标: method={}, path={}, status={}, duration={}ms", 
                method, path, statusCode, duration);
            
        } catch (Exception e) {
            log.warn("记录性能指标失败", e);
        }
    }
}