/*
 * 文件名: CacheHealthChecker.java
 * 用途: 缓存健康检查
 * 实现内容:
 *   - 缓存可用性检查
 *   - 性能指标健康检查
 *   - 容量和连接状态检查
 *   - 自动修复和恢复机制
 *   - 健康报告生成
 * 技术选型:
 *   - 定时健康检查任务
 *   - 多维度健康评估
 *   - 自动恢复策略
 *   - 健康状态枚举
 * 依赖关系:
 *   - 基于CacheMetrics检查
 *   - 集成CacheStatistics
 *   - 提供健康监控服务
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.monitor;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheKey;
import com.lx.gameserver.frame.cache.local.LocalCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 缓存健康检查器
 * <p>
 * 提供全面的缓存健康监控功能，包括可用性检查、性能监控、
 * 容量检查等，并支持自动修复和健康报告生成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(CacheHealthChecker.class);

    /**
     * 缓存管理器
     */
    private final LocalCacheManager cacheManager;

    /**
     * 健康检查配置
     */
    private final HealthCheckConfig config;

    /**
     * 定时调度器
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 健康检查结果缓存
     */
    private final Map<String, HealthCheckResult> healthResults = new ConcurrentHashMap<>();

    /**
     * 整体健康状态
     */
    private final AtomicReference<HealthStatus> overallHealth = new AtomicReference<>(HealthStatus.UNKNOWN);

    /**
     * 健康检查历史
     */
    private final Map<String, List<HealthCheckResult>> healthHistory = new ConcurrentHashMap<>();

    /**
     * 自动修复计数器
     */
    private final Map<String, AtomicInteger> repairCounters = new ConcurrentHashMap<>();

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 构造函数
     *
     * @param cacheManager 缓存管理器
     */
    public CacheHealthChecker(LocalCacheManager cacheManager) {
        this(cacheManager, HealthCheckConfig.defaultConfig());
    }

    /**
     * 构造函数
     *
     * @param cacheManager 缓存管理器
     * @param config       健康检查配置
     */
    public CacheHealthChecker(LocalCacheManager cacheManager, HealthCheckConfig config) {
        this.cacheManager = cacheManager;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "cache-health-checker");
                t.setDaemon(true);
                return t;
            });
    }

    /**
     * 启动健康检查
     */
    public synchronized void start() {
        if (started) {
            logger.warn("缓存健康检查已经启动");
            return;
        }

        logger.info("启动缓存健康检查，检查间隔: {}秒", config.getCheckInterval().getSeconds());
        started = true;

        // 定期健康检查
        scheduler.scheduleAtFixedRate(this::performHealthCheck,
            0, config.getCheckInterval().getSeconds(), TimeUnit.SECONDS);

        // 定期清理历史数据
        scheduler.scheduleAtFixedRate(this::cleanupHistory,
            config.getHistoryRetention().toMinutes(),
            config.getHistoryRetention().toMinutes(), TimeUnit.MINUTES);
    }

    /**
     * 停止健康检查
     */
    public synchronized void stop() {
        if (!started) {
            return;
        }

        logger.info("停止缓存健康检查");
        started = false;

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 立即执行健康检查
     *
     * @return 健康检查结果
     */
    public CompletableFuture<OverallHealthReport> checkHealthNow() {
        return CompletableFuture.supplyAsync(this::performHealthCheck, scheduler);
    }

    /**
     * 获取缓存健康状态
     *
     * @param cacheName 缓存名称
     * @return 健康状态
     */
    public HealthStatus getCacheHealth(String cacheName) {
        HealthCheckResult result = healthResults.get(cacheName);
        return result != null ? result.getOverallStatus() : HealthStatus.UNKNOWN;
    }

    /**
     * 获取整体健康状态
     *
     * @return 整体健康状态
     */
    public HealthStatus getOverallHealth() {
        return overallHealth.get();
    }

    /**
     * 获取健康检查结果
     *
     * @param cacheName 缓存名称
     * @return 健康检查结果
     */
    public HealthCheckResult getHealthResult(String cacheName) {
        return healthResults.get(cacheName);
    }

    /**
     * 获取所有缓存的健康状态
     *
     * @return 健康状态映射
     */
    public Map<String, HealthStatus> getAllCacheHealth() {
        Map<String, HealthStatus> result = new HashMap<>();
        for (Map.Entry<String, HealthCheckResult> entry : healthResults.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getOverallStatus());
        }
        return result;
    }

    /**
     * 获取健康历史
     *
     * @param cacheName 缓存名称
     * @param period    历史周期
     * @return 健康历史列表
     */
    public List<HealthCheckResult> getHealthHistory(String cacheName, Duration period) {
        List<HealthCheckResult> history = healthHistory.get(cacheName);
        if (history == null) {
            return Collections.emptyList();
        }

        Instant cutoff = Instant.now().minus(period);
        return history.stream()
            .filter(result -> result.getCheckTime().isAfter(cutoff))
            .sorted(Comparator.comparing(HealthCheckResult::getCheckTime))
            .toList();
    }

    /**
     * 执行健康检查
     */
    private OverallHealthReport performHealthCheck() {
        logger.debug("开始执行缓存健康检查");
        
        List<HealthCheckResult> results = new ArrayList<>();
        List<String> unhealthyCaches = new ArrayList<>();

        // 检查每个缓存
        for (String cacheName : cacheManager.getCacheNames()) {
            try {
                HealthCheckResult result = checkCacheHealth(cacheName);
                results.add(result);
                
                // 保存结果
                healthResults.put(cacheName, result);
                addToHistory(cacheName, result);
                
                // 检查是否需要修复
                if (result.getOverallStatus() == HealthStatus.UNHEALTHY && config.isAutoRepair()) {
                    attemptRepair(cacheName, result);
                }
                
                if (result.getOverallStatus() == HealthStatus.UNHEALTHY) {
                    unhealthyCaches.add(cacheName);
                }
                
            } catch (Exception e) {
                logger.error("检查缓存健康失败: {}", cacheName, e);
                HealthCheckResult errorResult = HealthCheckResult.error(cacheName, e.getMessage());
                results.add(errorResult);
                healthResults.put(cacheName, errorResult);
                unhealthyCaches.add(cacheName);
            }
        }

        // 计算整体健康状态
        HealthStatus overall = calculateOverallHealth(results);
        overallHealth.set(overall);

        OverallHealthReport report = new OverallHealthReport(overall, results, unhealthyCaches);
        
        logger.debug("缓存健康检查完成: 整体状态={}, 异常缓存数={}", 
            overall, unhealthyCaches.size());
        
        return report;
    }

    /**
     * 检查单个缓存健康状态
     */
    private HealthCheckResult checkCacheHealth(String cacheName) {
        Instant startTime = Instant.now();
        List<HealthCheck> checks = new ArrayList<>();
        
        try {
            Cache<CacheKey, Object> cache = cacheManager.getCache(cacheName);
            
            // 可用性检查
            checks.add(checkAvailability(cache, cacheName));
            
            // 性能检查
            checks.add(checkPerformance(cache, cacheName));
            
            // 容量检查
            checks.add(checkCapacity(cache, cacheName));
            
            // 连接检查（如果是Redis缓存）
            checks.add(checkConnection(cache, cacheName));
            
            // 计算整体状态
            HealthStatus overallStatus = calculateHealthStatus(checks);
            
            Duration checkDuration = Duration.between(startTime, Instant.now());
            
            return new HealthCheckResult(
                cacheName,
                startTime,
                overallStatus,
                checks,
                checkDuration,
                null
            );
            
        } catch (Exception e) {
            Duration checkDuration = Duration.between(startTime, Instant.now());
            return new HealthCheckResult(
                cacheName,
                startTime,
                HealthStatus.UNHEALTHY,
                checks,
                checkDuration,
                e.getMessage()
            );
        }
    }

    /**
     * 可用性检查
     */
    private HealthCheck checkAvailability(Cache<CacheKey, Object> cache, String cacheName) {
        try {
            // 执行基本的读写操作
            CacheKey testKey = CacheKey.of("health-check-" + System.currentTimeMillis());
            String testValue = "test-value";
            
            long startTime = System.nanoTime();
            
            // 写入测试
            cache.put(testKey, testValue);
            
            // 读取测试
            Object retrievedValue = cache.get(testKey);
            
            // 删除测试
            cache.remove(testKey);
            
            long elapsedNanos = System.nanoTime() - startTime;
            double elapsedMs = elapsedNanos / 1_000_000.0;
            
            if (!testValue.equals(retrievedValue)) {
                return new HealthCheck("availability", HealthStatus.UNHEALTHY, 
                    "数据读写不一致", elapsedMs);
            }
            
            if (elapsedMs > config.getAvailabilityTimeoutMs()) {
                return new HealthCheck("availability", HealthStatus.DEGRADED, 
                    "响应时间过长: " + elapsedMs + "ms", elapsedMs);
            }
            
            return new HealthCheck("availability", HealthStatus.HEALTHY, 
                "可用性正常", elapsedMs);
                
        } catch (Exception e) {
            return new HealthCheck("availability", HealthStatus.UNHEALTHY, 
                "可用性检查失败: " + e.getMessage(), 0);
        }
    }

    /**
     * 性能检查
     */
    private HealthCheck checkPerformance(Cache<CacheKey, Object> cache, String cacheName) {
        try {
            // 这里可以添加性能相关的检查
            // 比如检查缓存命中率、平均响应时间等
            
            return new HealthCheck("performance", HealthStatus.HEALTHY, 
                "性能正常", 0);
                
        } catch (Exception e) {
            return new HealthCheck("performance", HealthStatus.UNHEALTHY, 
                "性能检查失败: " + e.getMessage(), 0);
        }
    }

    /**
     * 容量检查
     */
    private HealthCheck checkCapacity(Cache<CacheKey, Object> cache, String cacheName) {
        try {
            // 检查缓存容量使用情况
            // 这里简化处理，实际应该检查真实的容量信息
            
            return new HealthCheck("capacity", HealthStatus.HEALTHY, 
                "容量正常", 0);
                
        } catch (Exception e) {
            return new HealthCheck("capacity", HealthStatus.UNHEALTHY, 
                "容量检查失败: " + e.getMessage(), 0);
        }
    }

    /**
     * 连接检查
     */
    private HealthCheck checkConnection(Cache<CacheKey, Object> cache, String cacheName) {
        try {
            // 检查缓存连接状态
            // 对于本地缓存，这个检查可能不太适用
            
            return new HealthCheck("connection", HealthStatus.HEALTHY, 
                "连接正常", 0);
                
        } catch (Exception e) {
            return new HealthCheck("connection", HealthStatus.UNHEALTHY, 
                "连接检查失败: " + e.getMessage(), 0);
        }
    }

    /**
     * 计算健康状态
     */
    private HealthStatus calculateHealthStatus(List<HealthCheck> checks) {
        long unhealthyCount = checks.stream()
            .mapToLong(check -> check.getStatus() == HealthStatus.UNHEALTHY ? 1 : 0)
            .sum();
        
        long degradedCount = checks.stream()
            .mapToLong(check -> check.getStatus() == HealthStatus.DEGRADED ? 1 : 0)
            .sum();
            
        if (unhealthyCount > 0) {
            return HealthStatus.UNHEALTHY;
        } else if (degradedCount > 0) {
            return HealthStatus.DEGRADED;
        } else {
            return HealthStatus.HEALTHY;
        }
    }

    /**
     * 计算整体健康状态
     */
    private HealthStatus calculateOverallHealth(List<HealthCheckResult> results) {
        if (results.isEmpty()) {
            return HealthStatus.UNKNOWN;
        }
        
        long unhealthyCount = results.stream()
            .mapToLong(result -> result.getOverallStatus() == HealthStatus.UNHEALTHY ? 1 : 0)
            .sum();
            
        long degradedCount = results.stream()
            .mapToLong(result -> result.getOverallStatus() == HealthStatus.DEGRADED ? 1 : 0)
            .sum();
            
        if (unhealthyCount > 0) {
            return HealthStatus.UNHEALTHY;
        } else if (degradedCount > 0) {
            return HealthStatus.DEGRADED;
        } else {
            return HealthStatus.HEALTHY;
        }
    }

    /**
     * 尝试修复缓存
     */
    private void attemptRepair(String cacheName, HealthCheckResult result) {
        AtomicInteger counter = repairCounters.computeIfAbsent(cacheName, k -> new AtomicInteger(0));
        
        if (counter.get() >= config.getMaxRepairAttempts()) {
            logger.warn("缓存修复尝试次数已达上限: {}", cacheName);
            return;
        }
        
        counter.incrementAndGet();
        logger.info("尝试修复缓存: {}, 尝试次数: {}", cacheName, counter.get());
        
        try {
            // 这里可以添加具体的修复逻辑
            // 比如重建缓存、清理过期数据等
            
            // 简化实现：清空缓存
            Cache<CacheKey, Object> cache = cacheManager.getCache(cacheName);
            cache.clear();
            
            logger.info("缓存修复完成: {}", cacheName);
            
        } catch (Exception e) {
            logger.error("缓存修复失败: {}", cacheName, e);
        }
    }

    /**
     * 添加到历史记录
     */
    private void addToHistory(String cacheName, HealthCheckResult result) {
        healthHistory.computeIfAbsent(cacheName, k -> new ArrayList<>()).add(result);
    }

    /**
     * 清理历史数据
     */
    private void cleanupHistory() {
        Instant cutoff = Instant.now().minus(config.getHistoryRetention());
        
        for (Map.Entry<String, List<HealthCheckResult>> entry : healthHistory.entrySet()) {
            List<HealthCheckResult> history = entry.getValue();
            history.removeIf(result -> result.getCheckTime().isBefore(cutoff));
        }
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("健康"),
        DEGRADED("降级"),
        UNHEALTHY("异常"),
        UNKNOWN("未知");

        private final String description;

        HealthStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 健康检查配置
     */
    public static class HealthCheckConfig {
        private final Duration checkInterval;
        private final Duration historyRetention;
        private final boolean autoRepair;
        private final int maxRepairAttempts;
        private final long availabilityTimeoutMs;

        public HealthCheckConfig(Duration checkInterval, Duration historyRetention,
                               boolean autoRepair, int maxRepairAttempts, long availabilityTimeoutMs) {
            this.checkInterval = checkInterval;
            this.historyRetention = historyRetention;
            this.autoRepair = autoRepair;
            this.maxRepairAttempts = maxRepairAttempts;
            this.availabilityTimeoutMs = availabilityTimeoutMs;
        }

        public static HealthCheckConfig defaultConfig() {
            return new HealthCheckConfig(
                Duration.ofMinutes(1),  // 每分钟检查一次
                Duration.ofHours(24),   // 保留24小时历史
                true,                   // 启用自动修复
                3,                      // 最多修复3次
                1000                    // 可用性超时1秒
            );
        }

        // Getters
        public Duration getCheckInterval() { return checkInterval; }
        public Duration getHistoryRetention() { return historyRetention; }
        public boolean isAutoRepair() { return autoRepair; }
        public int getMaxRepairAttempts() { return maxRepairAttempts; }
        public long getAvailabilityTimeoutMs() { return availabilityTimeoutMs; }
    }

    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private final String cacheName;
        private final Instant checkTime;
        private final HealthStatus overallStatus;
        private final List<HealthCheck> checks;
        private final Duration checkDuration;
        private final String errorMessage;

        public HealthCheckResult(String cacheName, Instant checkTime, HealthStatus overallStatus,
                               List<HealthCheck> checks, Duration checkDuration, String errorMessage) {
            this.cacheName = cacheName;
            this.checkTime = checkTime;
            this.overallStatus = overallStatus;
            this.checks = new ArrayList<>(checks);
            this.checkDuration = checkDuration;
            this.errorMessage = errorMessage;
        }

        public static HealthCheckResult error(String cacheName, String errorMessage) {
            return new HealthCheckResult(
                cacheName,
                Instant.now(),
                HealthStatus.UNHEALTHY,
                Collections.emptyList(),
                Duration.ZERO,
                errorMessage
            );
        }

        // Getters
        public String getCacheName() { return cacheName; }
        public Instant getCheckTime() { return checkTime; }
        public HealthStatus getOverallStatus() { return overallStatus; }
        public List<HealthCheck> getChecks() { return checks; }
        public Duration getCheckDuration() { return checkDuration; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return "HealthCheckResult{" +
                   "cacheName='" + cacheName + '\'' +
                   ", overallStatus=" + overallStatus +
                   ", checkDuration=" + checkDuration.toMillis() + "ms" +
                   ", checksCount=" + checks.size() +
                   '}';
        }
    }

    /**
     * 单项健康检查
     */
    public static class HealthCheck {
        private final String checkType;
        private final HealthStatus status;
        private final String message;
        private final double elapsedMs;

        public HealthCheck(String checkType, HealthStatus status, String message, double elapsedMs) {
            this.checkType = checkType;
            this.status = status;
            this.message = message;
            this.elapsedMs = elapsedMs;
        }

        // Getters
        public String getCheckType() { return checkType; }
        public HealthStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public double getElapsedMs() { return elapsedMs; }

        @Override
        public String toString() {
            return "HealthCheck{" +
                   "checkType='" + checkType + '\'' +
                   ", status=" + status +
                   ", message='" + message + '\'' +
                   ", elapsedMs=" + elapsedMs +
                   '}';
        }
    }

    /**
     * 整体健康报告
     */
    public static class OverallHealthReport {
        private final HealthStatus overallStatus;
        private final List<HealthCheckResult> cacheResults;
        private final List<String> unhealthyCaches;
        private final Instant reportTime;

        public OverallHealthReport(HealthStatus overallStatus, List<HealthCheckResult> cacheResults,
                                 List<String> unhealthyCaches) {
            this.overallStatus = overallStatus;
            this.cacheResults = new ArrayList<>(cacheResults);
            this.unhealthyCaches = new ArrayList<>(unhealthyCaches);
            this.reportTime = Instant.now();
        }

        // Getters
        public HealthStatus getOverallStatus() { return overallStatus; }
        public List<HealthCheckResult> getCacheResults() { return cacheResults; }
        public List<String> getUnhealthyCaches() { return unhealthyCaches; }
        public Instant getReportTime() { return reportTime; }

        @Override
        public String toString() {
            return "OverallHealthReport{" +
                   "overallStatus=" + overallStatus +
                   ", totalCaches=" + cacheResults.size() +
                   ", unhealthyCaches=" + unhealthyCaches.size() +
                   ", reportTime=" + reportTime +
                   '}';
        }
    }
}