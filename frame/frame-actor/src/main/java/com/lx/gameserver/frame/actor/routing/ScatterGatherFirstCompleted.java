/*
 * 文件名: ScatterGatherFirstCompleted.java
 * 用途: 分散收集首个完成路由器
 * 实现内容:
 *   - 分散消息到多个目标Actor
 *   - 收集响应并返回首个完成的结果
 *   - 超时处理和异常管理
 *   - 性能监控和统计
 * 技术选型:
 *   - CompletableFuture支持异步操作
 *   - 超时机制防止无限等待
 *   - 原子操作保证线程安全
 * 依赖关系:
 *   - 继承Router基类
 *   - 与ActorRef的ask操作集成
 *   - 支持异步消息处理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 分散收集首个完成路由器
 * <p>
 * 将消息发送到所有可用的routee，返回第一个完成的响应。
 * 适用于需要快速响应的场景，如多个服务提供相同功能时的竞速。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ScatterGatherFirstCompleted extends Router {
    
    private static final Logger logger = LoggerFactory.getLogger(ScatterGatherFirstCompleted.class);
    
    /** 默认超时时间 */
    private final Duration defaultTimeout;
    
    /** 请求统计 */
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    public ScatterGatherFirstCompleted(String name, RouterConfig config, Duration defaultTimeout) {
        super(name, config);
        this.defaultTimeout = defaultTimeout;
        logger.info("分散收集首个完成路由器[{}]初始化完成，默认超时: {}", name, defaultTimeout);
    }
    
    public ScatterGatherFirstCompleted(String name, RouterConfig config) {
        this(name, config, Duration.ofSeconds(5));
    }
    
    @Override
    protected RouteResult selectRoutees(Object message, ActorRef sender) {
        List<ActorRef> availableRoutees = getAvailableRoutees();
        
        if (availableRoutees.isEmpty()) {
            recordRouteFailure();
            return new RouteResult("没有可用的路由目标");
        }
        
        recordRouteSuccess();
        return new RouteResult(availableRoutees);
    }
    
    /**
     * 分散收集请求
     *
     * @param message 请求消息
     * @param sender 发送者
     * @param timeout 超时时间
     * @return 第一个完成的响应
     */
    public CompletableFuture<Object> scatterGather(Object message, ActorRef sender, Duration timeout) {
        List<ActorRef> routees = getAvailableRoutees();
        
        if (routees.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("没有可用的路由目标"));
        }
        
        if (routees.size() == 1) {
            // 只有一个目标，直接发送
            return routees.get(0).ask(message, timeout);
        }
        
        return scatterGatherToMultiple(message, routees, timeout);
    }
    
    /**
     * 使用默认超时的分散收集请求
     */
    public CompletableFuture<Object> scatterGather(Object message, ActorRef sender) {
        return scatterGather(message, sender, defaultTimeout);
    }
    
    /**
     * 向多个目标发送并收集首个响应
     */
    private CompletableFuture<Object> scatterGatherToMultiple(Object message, List<ActorRef> routees, Duration timeout) {
        requestCount.incrementAndGet();
        Instant startTime = Instant.now();
        
        // 创建响应容器
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        AtomicReference<Object> firstResult = new AtomicReference<>();
        AtomicLong completedCount = new AtomicLong(0);
        
        // 向所有routee发送请求
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        
        for (ActorRef routee : routees) {
            CompletableFuture<Object> future = routee.ask(message, timeout)
                    .whenComplete((result, throwable) -> {
                        long completed = completedCount.incrementAndGet();
                        
                        if (throwable == null) {
                            // 成功响应
                            if (firstResult.compareAndSet(null, result)) {
                                // 这是第一个成功的响应
                                resultFuture.complete(result);
                                successCount.incrementAndGet();
                                
                                Duration responseTime = Duration.between(startTime, Instant.now());
                                logger.debug("分散收集[{}]首个响应完成，耗时: {}ms, 来源: {}", 
                                        getName(), responseTime.toMillis(), routee.getPath());
                            }
                        } else {
                            // 失败响应
                            logger.debug("分散收集[{}]单个请求失败: {}, 错误: {}", 
                                    getName(), routee.getPath(), throwable.getMessage());
                        }
                        
                        // 检查是否所有请求都已完成
                        if (completed == routees.size() && !resultFuture.isDone()) {
                            // 所有请求都失败了
                            errorCount.incrementAndGet();
                            resultFuture.completeExceptionally(
                                    new RuntimeException("所有路由目标都未能成功响应"));
                        }
                    });
            
            futures.add(future);
        }
        
        // 设置超时处理
        CompletableFuture.delayedExecutor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (!resultFuture.isDone()) {
                        timeoutCount.incrementAndGet();
                        resultFuture.completeExceptionally(
                                new java.util.concurrent.TimeoutException("分散收集请求超时"));
                    }
                });
        
        return resultFuture;
    }
    
    /**
     * 分散收集所有响应
     *
     * @param message 请求消息
     * @param sender 发送者
     * @param timeout 超时时间
     * @return 所有响应的列表
     */
    public CompletableFuture<List<Object>> scatterGatherAll(Object message, ActorRef sender, Duration timeout) {
        List<ActorRef> routees = getAvailableRoutees();
        
        if (routees.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        
        for (ActorRef routee : routees) {
            CompletableFuture<Object> future = routee.ask(message, timeout)
                    .exceptionally(throwable -> {
                        logger.debug("分散收集全部[{}]单个请求失败: {}, 错误: {}", 
                                getName(), routee.getPath(), throwable.getMessage());
                        return new ErrorResponse(routee.getPath(), throwable);
                    });
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(result -> !(result instanceof ErrorResponse))
                        .toList());
    }
    
    /**
     * 获取可用的路由目标
     */
    private List<ActorRef> getAvailableRoutees() {
        return getRoutees().stream()
                .filter(routee -> !routee.isTerminated())
                .toList();
    }
    
    /**
     * 获取统计信息
     */
    public ScatterGatherStats getStats() {
        return new ScatterGatherStats(
                requestCount.get(),
                successCount.get(),
                timeoutCount.get(),
                errorCount.get(),
                getRouteeCount()
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        requestCount.set(0);
        successCount.set(0);
        timeoutCount.set(0);
        errorCount.set(0);
        logger.debug("分散收集路由器[{}]统计信息已重置", getName());
    }
    
    /**
     * 错误响应包装类
     */
    private static class ErrorResponse {
        private final String routeePath;
        private final Throwable error;
        
        public ErrorResponse(String routeePath, Throwable error) {
            this.routeePath = routeePath;
            this.error = error;
        }
        
        public String getRouteePath() { return routeePath; }
        public Throwable getError() { return error; }
    }
    
    /**
     * 分散收集统计信息
     */
    public static class ScatterGatherStats {
        private final long totalRequests;
        private final long successfulRequests;
        private final long timeoutRequests;
        private final long errorRequests;
        private final int routeeCount;
        
        public ScatterGatherStats(long totalRequests, long successfulRequests, 
                                long timeoutRequests, long errorRequests, int routeeCount) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.timeoutRequests = timeoutRequests;
            this.errorRequests = errorRequests;
            this.routeeCount = routeeCount;
        }
        
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public long getTimeoutRequests() { return timeoutRequests; }
        public long getErrorRequests() { return errorRequests; }
        public int getRouteeCount() { return routeeCount; }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
        }
        
        public double getTimeoutRate() {
            return totalRequests > 0 ? (double) timeoutRequests / totalRequests : 0.0;
        }
        
        public double getErrorRate() {
            return totalRequests > 0 ? (double) errorRequests / totalRequests : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ScatterGatherStats{total=%d, success=%d(%.1f%%), timeout=%d(%.1f%%), error=%d(%.1f%%), routees=%d}",
                    totalRequests, successfulRequests, getSuccessRate() * 100,
                    timeoutRequests, getTimeoutRate() * 100,
                    errorRequests, getErrorRate() * 100, routeeCount);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ScatterGatherFirstCompleted{name=%s, routees=%d, timeout=%s}",
                getName(), getRouteeCount(), defaultTimeout);
    }
}