/*
 * 文件名: GatewayMetrics.java
 * 用途: 网关指标收集
 * 实现内容:
 *   - 请求量统计（QPS/TPS）
 *   - 响应时间分布
 *   - 错误率统计
 *   - 限流统计
 *   - 熔断统计
 * 技术选型:
 *   - Micrometer指标库
 *   - Prometheus指标导出
 *   - 内存指标统计
 * 依赖关系:
 *   - 与过滤器集成收集指标
 *   - 导出到监控系统
 *   - 支持告警阈值检查
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.monitor;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 网关指标收集器
 * <p>
 * 收集和统计网关运行过程中的各种性能指标，包括请求量、响应时间、
 * 错误率、限流熔断等关键指标，支持实时监控和告警。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayMetrics {

    // 请求计数器
    private final LongAdder totalRequestCount = new LongAdder();
    private final LongAdder successRequestCount = new LongAdder();
    private final LongAdder errorRequestCount = new LongAdder();
    
    // 响应时间统计
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong maxResponseTime = new AtomicLong(0);
    private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    
    // 按状态码统计
    private final Map<Integer, LongAdder> statusCodeCounters = new ConcurrentHashMap<>();
    
    // 按路径统计
    private final Map<String, PathMetrics> pathMetricsMap = new ConcurrentHashMap<>();
    
    // 限流统计
    private final LongAdder rateLimitCount = new LongAdder();
    
    // 熔断统计
    private final LongAdder circuitBreakerOpenCount = new LongAdder();
    
    // 启动时间
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 记录请求指标
     *
     * @param method HTTP方法
     * @param path 请求路径
     * @param statusCode 状态码
     * @param responseTime 响应时间（毫秒）
     */
    public void recordRequest(String method, String path, int statusCode, long responseTime) {
        // 总请求数
        totalRequestCount.increment();
        
        // 成功/错误请求数
        if (statusCode >= 200 && statusCode < 400) {
            successRequestCount.increment();
        } else {
            errorRequestCount.increment();
        }
        
        // 状态码统计
        statusCodeCounters.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
        
        // 响应时间统计
        totalResponseTime.addAndGet(responseTime);
        updateMaxResponseTime(responseTime);
        updateMinResponseTime(responseTime);
        
        // 路径级别统计
        String pathKey = method + ":" + path;
        pathMetricsMap.computeIfAbsent(pathKey, k -> new PathMetrics()).recordRequest(statusCode, responseTime);
        
        log.debug("记录请求指标: {}:{}, status={}, responseTime={}ms", method, path, statusCode, responseTime);
    }

    /**
     * 记录限流事件
     *
     * @param path 被限流的路径
     * @param limitType 限流类型
     */
    public void recordRateLimit(String path, String limitType) {
        rateLimitCount.increment();
        log.info("记录限流事件: path={}, type={}", path, limitType);
    }

    /**
     * 记录熔断事件
     *
     * @param serviceName 服务名称
     * @param state 熔断器状态
     */
    public void recordCircuitBreaker(String serviceName, String state) {
        if ("OPEN".equals(state)) {
            circuitBreakerOpenCount.increment();
        }
        log.info("记录熔断事件: service={}, state={}", serviceName, state);
    }

    /**
     * 获取当前QPS
     *
     * @return 每秒请求数
     */
    public double getCurrentQPS() {
        long totalRequests = totalRequestCount.sum();
        long uptimeSeconds = getUptimeSeconds();
        return uptimeSeconds > 0 ? (double) totalRequests / uptimeSeconds : 0.0;
    }

    /**
     * 获取平均响应时间
     *
     * @return 平均响应时间（毫秒）
     */
    public double getAverageResponseTime() {
        long totalRequests = totalRequestCount.sum();
        long totalTime = totalResponseTime.get();
        return totalRequests > 0 ? (double) totalTime / totalRequests : 0.0;
    }

    /**
     * 获取错误率
     *
     * @return 错误率（0-1之间）
     */
    public double getErrorRate() {
        long totalRequests = totalRequestCount.sum();
        long errorRequests = errorRequestCount.sum();
        return totalRequests > 0 ? (double) errorRequests / totalRequests : 0.0;
    }

    /**
     * 获取成功率
     *
     * @return 成功率（0-1之间）
     */
    public double getSuccessRate() {
        return 1.0 - getErrorRate();
    }

    /**
     * 获取网关统计信息
     *
     * @return 统计信息
     */
    public GatewayStats getGatewayStats() {
        GatewayStats stats = new GatewayStats();
        stats.setTotalRequests(totalRequestCount.sum());
        stats.setSuccessRequests(successRequestCount.sum());
        stats.setErrorRequests(errorRequestCount.sum());
        stats.setCurrentQPS(getCurrentQPS());
        stats.setAverageResponseTime(getAverageResponseTime());
        stats.setMaxResponseTime(maxResponseTime.get());
        stats.setMinResponseTime(minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get());
        stats.setErrorRate(getErrorRate());
        stats.setSuccessRate(getSuccessRate());
        stats.setRateLimitCount(rateLimitCount.sum());
        stats.setCircuitBreakerOpenCount(circuitBreakerOpenCount.sum());
        stats.setUptimeSeconds(getUptimeSeconds());
        stats.setStartTime(startTime);
        
        return stats;
    }

    /**
     * 获取路径级别统计
     *
     * @return 路径统计映射
     */
    public Map<String, PathMetrics> getPathMetrics() {
        return new ConcurrentHashMap<>(pathMetricsMap);
    }

    /**
     * 获取状态码统计
     *
     * @return 状态码统计映射
     */
    public Map<Integer, Long> getStatusCodeStats() {
        Map<Integer, Long> stats = new ConcurrentHashMap<>();
        statusCodeCounters.forEach((code, counter) -> stats.put(code, counter.sum()));
        return stats;
    }

    /**
     * 重置所有指标
     */
    public void resetMetrics() {
        totalRequestCount.reset();
        successRequestCount.reset();
        errorRequestCount.reset();
        totalResponseTime.set(0);
        maxResponseTime.set(0);
        minResponseTime.set(Long.MAX_VALUE);
        statusCodeCounters.clear();
        pathMetricsMap.clear();
        rateLimitCount.reset();
        circuitBreakerOpenCount.reset();
        
        log.info("网关指标已重置");
    }

    /**
     * 检查告警阈值
     *
     * @return 告警信息列表
     */
    public java.util.List<AlertInfo> checkAlertThresholds() {
        java.util.List<AlertInfo> alerts = new java.util.ArrayList<>();
        
        // 检查错误率告警
        double errorRate = getErrorRate();
        if (errorRate > 0.05) { // 错误率超过5%
            alerts.add(new AlertInfo("ERROR_RATE_HIGH", 
                String.format("错误率过高: %.2f%%", errorRate * 100)));
        }
        
        // 检查响应时间告警
        double avgResponseTime = getAverageResponseTime();
        if (avgResponseTime > 5000) { // 平均响应时间超过5秒
            alerts.add(new AlertInfo("RESPONSE_TIME_HIGH", 
                String.format("响应时间过长: %.2fms", avgResponseTime)));
        }
        
        // 检查QPS告警
        double currentQPS = getCurrentQPS();
        if (currentQPS > 10000) { // QPS超过1万
            alerts.add(new AlertInfo("QPS_HIGH", 
                String.format("QPS过高: %.2f", currentQPS)));
        }
        
        // 检查熔断告警
        long circuitBreakerCount = circuitBreakerOpenCount.sum();
        if (circuitBreakerCount > 0) {
            alerts.add(new AlertInfo("CIRCUIT_BREAKER_OPEN", 
                String.format("熔断器打开次数: %d", circuitBreakerCount)));
        }
        
        return alerts;
    }

    /**
     * 更新最大响应时间
     *
     * @param responseTime 响应时间
     */
    private void updateMaxResponseTime(long responseTime) {
        long currentMax = maxResponseTime.get();
        while (responseTime > currentMax) {
            if (maxResponseTime.compareAndSet(currentMax, responseTime)) {
                break;
            }
            currentMax = maxResponseTime.get();
        }
    }

    /**
     * 更新最小响应时间
     *
     * @param responseTime 响应时间
     */
    private void updateMinResponseTime(long responseTime) {
        long currentMin = minResponseTime.get();
        while (responseTime < currentMin) {
            if (minResponseTime.compareAndSet(currentMin, responseTime)) {
                break;
            }
            currentMin = minResponseTime.get();
        }
    }

    /**
     * 获取运行时间（秒）
     *
     * @return 运行时间秒数
     */
    private long getUptimeSeconds() {
        return java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 路径级别指标
     */
    @Data
    public static class PathMetrics {
        private final LongAdder requestCount = new LongAdder();
        private final LongAdder errorCount = new LongAdder();
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        
        public void recordRequest(int statusCode, long responseTime) {
            requestCount.increment();
            if (statusCode >= 400) {
                errorCount.increment();
            }
            totalResponseTime.addAndGet(responseTime);
            
            long currentMax = maxResponseTime.get();
            while (responseTime > currentMax) {
                if (maxResponseTime.compareAndSet(currentMax, responseTime)) {
                    break;
                }
                currentMax = maxResponseTime.get();
            }
        }
        
        public double getAverageResponseTime() {
            long requests = requestCount.sum();
            return requests > 0 ? (double) totalResponseTime.get() / requests : 0.0;
        }
        
        public double getErrorRate() {
            long total = requestCount.sum();
            return total > 0 ? (double) errorCount.sum() / total : 0.0;
        }
    }

    /**
     * 网关统计信息
     */
    @Data
    public static class GatewayStats {
        private long totalRequests;
        private long successRequests;
        private long errorRequests;
        private double currentQPS;
        private double averageResponseTime;
        private long maxResponseTime;
        private long minResponseTime;
        private double errorRate;
        private double successRate;
        private long rateLimitCount;
        private long circuitBreakerOpenCount;
        private long uptimeSeconds;
        private LocalDateTime startTime;
    }

    /**
     * 告警信息
     */
    @Data
    public static class AlertInfo {
        private String type;
        private String message;
        private LocalDateTime timestamp;
        
        public AlertInfo(String type, String message) {
            this.type = type;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
    }
}