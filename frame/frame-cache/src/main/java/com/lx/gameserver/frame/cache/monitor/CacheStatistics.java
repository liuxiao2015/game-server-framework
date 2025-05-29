/*
 * 文件名: CacheStatistics.java
 * 用途: 缓存统计信息
 * 实现内容:
 *   - 缓存历史数据统计
 *   - 趋势分析和报表生成
 *   - 告警阈值监控
 *   - 统计数据导出功能
 *   - 多维度数据聚合
 * 技术选型:
 *   - 时间序列数据存储
 *   - 统计算法实现
 *   - JSON序列化支持
 *   - 定时数据收集
 * 依赖关系:
 *   - 基于CacheMetrics构建
 *   - 提供历史统计分析
 *   - 支持监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.monitor;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 缓存统计信息管理器
 * <p>
 * 提供缓存的历史统计信息管理，包括趋势分析、报表生成、告警监控等功能。
 * 支持多维度的数据聚合和导出，便于性能分析和优化。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheStatistics {

    private static final Logger logger = LoggerFactory.getLogger(CacheStatistics.class);

    /**
     * 缓存名称
     */
    private final String cacheName;

    /**
     * 历史快照数据
     */
    private final Map<Instant, StatisticsSnapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * 告警配置
     */
    private final Map<String, AlarmThreshold> alarmThresholds = new ConcurrentHashMap<>();

    /**
     * 统计配置
     */
    private final StatisticsConfig config;

    /**
     * 当前快照
     */
    private final AtomicReference<StatisticsSnapshot> currentSnapshot = new AtomicReference<>();

    /**
     * 定时调度器
     */
    private final ScheduledExecutorService scheduler;

    /**
     * JSON序列化器
     */
    private final ObjectMapper objectMapper;

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 构造函数
     *
     * @param cacheName 缓存名称
     */
    public CacheStatistics(String cacheName) {
        this(cacheName, StatisticsConfig.defaultConfig());
    }

    /**
     * 构造函数
     *
     * @param cacheName 缓存名称
     * @param config    统计配置
     */
    public CacheStatistics(String cacheName, StatisticsConfig config) {
        this.cacheName = cacheName;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "cache-statistics-" + cacheName);
                t.setDaemon(true);
                return t;
            });
        this.objectMapper = new ObjectMapper();
        
        // 初始化默认告警阈值
        initDefaultAlarmThresholds();
    }

    /**
     * 启动统计收集
     *
     * @param metrics 缓存指标
     */
    public synchronized void start(CacheMetrics metrics) {
        if (started) {
            logger.warn("缓存统计已经启动: {}", cacheName);
            return;
        }

        logger.info("启动缓存统计收集: {}, 采集间隔: {}秒", 
            cacheName, config.getCollectionInterval().getSeconds());
        started = true;

        // 定期收集统计数据
        scheduler.scheduleAtFixedRate(() -> {
            try {
                collectStatistics(metrics);
            } catch (Exception e) {
                logger.error("收集统计数据失败: {}", cacheName, e);
            }
        }, 0, config.getCollectionInterval().getSeconds(), TimeUnit.SECONDS);

        // 定期清理过期数据
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredData();
            } catch (Exception e) {
                logger.error("清理过期统计数据失败: {}", cacheName, e);
            }
        }, config.getRetentionPeriod().toMinutes(), 
           config.getRetentionPeriod().toMinutes(), TimeUnit.MINUTES);
    }

    /**
     * 停止统计收集
     */
    public synchronized void stop() {
        if (!started) {
            return;
        }

        logger.info("停止缓存统计收集: {}", cacheName);
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
     * 获取当前统计快照
     *
     * @return 统计快照
     */
    public StatisticsSnapshot getCurrentSnapshot() {
        return currentSnapshot.get();
    }

    /**
     * 获取历史快照
     *
     * @param from 开始时间
     * @param to   结束时间
     * @return 历史快照列表
     */
    public List<StatisticsSnapshot> getHistorySnapshots(Instant from, Instant to) {
        return snapshots.entrySet().stream()
            .filter(entry -> !entry.getKey().isBefore(from) && !entry.getKey().isAfter(to))
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparing(StatisticsSnapshot::getTimestamp))
            .collect(Collectors.toList());
    }

    /**
     * 获取统计报告
     *
     * @param period 统计周期
     * @return 统计报告
     */
    public StatisticsReport generateReport(Duration period) {
        Instant now = Instant.now();
        Instant from = now.minus(period);
        List<StatisticsSnapshot> snapshots = getHistorySnapshots(from, now);
        
        return new StatisticsReport(cacheName, from, now, snapshots);
    }

    /**
     * 检查告警
     *
     * @param snapshot 统计快照
     * @return 告警列表
     */
    public List<AlarmEvent> checkAlarms(StatisticsSnapshot snapshot) {
        List<AlarmEvent> alarms = new ArrayList<>();
        
        for (Map.Entry<String, AlarmThreshold> entry : alarmThresholds.entrySet()) {
            String metric = entry.getKey();
            AlarmThreshold threshold = entry.getValue();
            
            double value = getMetricValue(snapshot, metric);
            AlarmEvent alarm = threshold.check(metric, value, snapshot.getTimestamp());
            if (alarm != null) {
                alarms.add(alarm);
            }
        }
        
        return alarms;
    }

    /**
     * 设置告警阈值
     *
     * @param metric    指标名称
     * @param threshold 阈值配置
     */
    public void setAlarmThreshold(String metric, AlarmThreshold threshold) {
        alarmThresholds.put(metric, threshold);
        logger.info("设置告警阈值: {} = {}", metric, threshold);
    }

    /**
     * 导出统计数据
     *
     * @param format 导出格式
     * @param period 统计周期
     * @return 导出数据
     */
    public String exportData(ExportFormat format, Duration period) {
        Instant now = Instant.now();
        Instant from = now.minus(period);
        List<StatisticsSnapshot> snapshots = getHistorySnapshots(from, now);
        
        switch (format) {
            case JSON:
                return exportAsJson(snapshots);
            case CSV:
                return exportAsCsv(snapshots);
            default:
                throw new IllegalArgumentException("不支持的导出格式: " + format);
        }
    }

    /**
     * 获取统计摘要
     *
     * @return 统计摘要
     */
    public StatisticsSummary getSummary() {
        StatisticsSnapshot current = getCurrentSnapshot();
        if (current == null) {
            return new StatisticsSummary();
        }
        
        // 计算趋势数据
        List<StatisticsSnapshot> recent = getHistorySnapshots(
            Instant.now().minus(Duration.ofHours(1)), Instant.now());
        
        return new StatisticsSummary(current, recent);
    }

    /**
     * 收集统计数据
     */
    private void collectStatistics(CacheMetrics metrics) {
        StatisticsSnapshot snapshot = new StatisticsSnapshot(
            Instant.now(),
            metrics.getTotalRequests(),
            metrics.getHits(),
            metrics.getMisses(),
            metrics.getHitRate(),
            metrics.getLoads(),
            metrics.getAverageLoadTime(),
            metrics.getEvictions(),
            metrics.getExpirations(),
            metrics.getPuts(),
            metrics.getRemovals(),
            metrics.getCurrentSize(),
            metrics.getEstimatedMemoryUsage(),
            metrics.getRequestsPerSecond()
        );
        
        // 保存快照
        snapshots.put(snapshot.getTimestamp(), snapshot);
        currentSnapshot.set(snapshot);
        
        // 检查告警
        List<AlarmEvent> alarms = checkAlarms(snapshot);
        if (!alarms.isEmpty()) {
            logger.warn("缓存告警触发: {}, 告警数量: {}", cacheName, alarms.size());
            alarms.forEach(alarm -> logger.warn("告警详情: {}", alarm));
        }
        
        logger.debug("收集统计数据: {}", snapshot);
    }

    /**
     * 清理过期数据
     */
    private void cleanupExpiredData() {
        Instant cutoff = Instant.now().minus(config.getRetentionPeriod());
        
        int removedCount = 0;
        Iterator<Map.Entry<Instant, StatisticsSnapshot>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Instant, StatisticsSnapshot> entry = iterator.next();
            if (entry.getKey().isBefore(cutoff)) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            logger.debug("清理过期统计数据: {}, 清理数量: {}", cacheName, removedCount);
        }
    }

    /**
     * 初始化默认告警阈值
     */
    private void initDefaultAlarmThresholds() {
        // 命中率低于80%告警
        setAlarmThreshold("hitRate", new AlarmThreshold(
            AlarmThreshold.Operator.LESS_THAN, 0.8, AlarmLevel.WARNING));
        
        // 内存使用超过500MB告警
        setAlarmThreshold("memoryUsage", new AlarmThreshold(
            AlarmThreshold.Operator.GREATER_THAN, 500 * 1024 * 1024, AlarmLevel.WARNING));
        
        // 平均加载时间超过1秒告警
        setAlarmThreshold("averageLoadTime", new AlarmThreshold(
            AlarmThreshold.Operator.GREATER_THAN, 1000_000_000, AlarmLevel.CRITICAL));
    }

    /**
     * 获取指标值
     */
    private double getMetricValue(StatisticsSnapshot snapshot, String metric) {
        return switch (metric) {
            case "hitRate" -> snapshot.getHitRate();
            case "memoryUsage" -> snapshot.getMemoryUsage();
            case "averageLoadTime" -> snapshot.getAverageLoadTime();
            case "requestsPerSecond" -> snapshot.getRequestsPerSecond();
            default -> 0.0;
        };
    }

    /**
     * 导出为JSON格式
     */
    private String exportAsJson(List<StatisticsSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException e) {
            logger.error("导出JSON失败", e);
            return "{}";
        }
    }

    /**
     * 导出为CSV格式
     */
    private String exportAsCsv(List<StatisticsSnapshot> snapshots) {
        StringBuilder csv = new StringBuilder();
        
        // CSV标题行
        csv.append("timestamp,totalRequests,hits,misses,hitRate,loads,averageLoadTime,")
           .append("evictions,expirations,puts,removals,currentSize,memoryUsage,requestsPerSecond\n");
        
        // 数据行
        for (StatisticsSnapshot snapshot : snapshots) {
            csv.append(snapshot.getTimestamp()).append(",")
               .append(snapshot.getTotalRequests()).append(",")
               .append(snapshot.getHits()).append(",")
               .append(snapshot.getMisses()).append(",")
               .append(snapshot.getHitRate()).append(",")
               .append(snapshot.getLoads()).append(",")
               .append(snapshot.getAverageLoadTime()).append(",")
               .append(snapshot.getEvictions()).append(",")
               .append(snapshot.getExpirations()).append(",")
               .append(snapshot.getPuts()).append(",")
               .append(snapshot.getRemovals()).append(",")
               .append(snapshot.getCurrentSize()).append(",")
               .append(snapshot.getMemoryUsage()).append(",")
               .append(snapshot.getRequestsPerSecond()).append("\n");
        }
        
        return csv.toString();
    }

    /**
     * 统计快照
     */
    public static class StatisticsSnapshot {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private final Instant timestamp;
        private final long totalRequests;
        private final long hits;
        private final long misses;
        private final double hitRate;
        private final long loads;
        private final double averageLoadTime;
        private final long evictions;
        private final long expirations;
        private final long puts;
        private final long removals;
        private final long currentSize;
        private final long memoryUsage;
        private final double requestsPerSecond;

        public StatisticsSnapshot(Instant timestamp, long totalRequests, long hits, long misses,
                                double hitRate, long loads, double averageLoadTime,
                                long evictions, long expirations, long puts, long removals,
                                long currentSize, long memoryUsage, double requestsPerSecond) {
            this.timestamp = timestamp;
            this.totalRequests = totalRequests;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.loads = loads;
            this.averageLoadTime = averageLoadTime;
            this.evictions = evictions;
            this.expirations = expirations;
            this.puts = puts;
            this.removals = removals;
            this.currentSize = currentSize;
            this.memoryUsage = memoryUsage;
            this.requestsPerSecond = requestsPerSecond;
        }

        // Getters
        public Instant getTimestamp() { return timestamp; }
        public long getTotalRequests() { return totalRequests; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public double getHitRate() { return hitRate; }
        public long getLoads() { return loads; }
        public double getAverageLoadTime() { return averageLoadTime; }
        public long getEvictions() { return evictions; }
        public long getExpirations() { return expirations; }
        public long getPuts() { return puts; }
        public long getRemovals() { return removals; }
        public long getCurrentSize() { return currentSize; }
        public long getMemoryUsage() { return memoryUsage; }
        public double getRequestsPerSecond() { return requestsPerSecond; }

        @Override
        public String toString() {
            return "StatisticsSnapshot{" +
                   "timestamp=" + timestamp +
                   ", hitRate=" + String.format("%.2f%%", hitRate * 100) +
                   ", totalRequests=" + totalRequests +
                   ", currentSize=" + currentSize +
                   ", memoryUsage=" + memoryUsage +
                   '}';
        }
    }

    /**
     * 导出格式
     */
    public enum ExportFormat {
        JSON, CSV
    }

    /**
     * 告警级别
     */
    public enum AlarmLevel {
        INFO, WARNING, CRITICAL
    }

    /**
     * 统计配置
     */
    public static class StatisticsConfig {
        private final Duration collectionInterval;
        private final Duration retentionPeriod;
        private final boolean alarmEnabled;

        public StatisticsConfig(Duration collectionInterval, Duration retentionPeriod, boolean alarmEnabled) {
            this.collectionInterval = collectionInterval;
            this.retentionPeriod = retentionPeriod;
            this.alarmEnabled = alarmEnabled;
        }

        public static StatisticsConfig defaultConfig() {
            return new StatisticsConfig(Duration.ofMinutes(1), Duration.ofDays(7), true);
        }

        public Duration getCollectionInterval() { return collectionInterval; }
        public Duration getRetentionPeriod() { return retentionPeriod; }
        public boolean isAlarmEnabled() { return alarmEnabled; }
    }

    /**
     * 告警阈值
     */
    public static class AlarmThreshold {
        public enum Operator { GREATER_THAN, LESS_THAN, EQUALS }

        private final Operator operator;
        private final double value;
        private final AlarmLevel level;

        public AlarmThreshold(Operator operator, double value, AlarmLevel level) {
            this.operator = operator;
            this.value = value;
            this.level = level;
        }

        public AlarmEvent check(String metric, double currentValue, Instant timestamp) {
            boolean triggered = switch (operator) {
                case GREATER_THAN -> currentValue > value;
                case LESS_THAN -> currentValue < value;
                case EQUALS -> Math.abs(currentValue - value) < 0.001;
            };

            return triggered ? new AlarmEvent(metric, currentValue, value, level, timestamp) : null;
        }

        @Override
        public String toString() {
            return operator + " " + value + " (" + level + ")";
        }
    }

    /**
     * 告警事件
     */
    public static class AlarmEvent {
        private final String metric;
        private final double currentValue;
        private final double thresholdValue;
        private final AlarmLevel level;
        private final Instant timestamp;

        public AlarmEvent(String metric, double currentValue, double thresholdValue, 
                         AlarmLevel level, Instant timestamp) {
            this.metric = metric;
            this.currentValue = currentValue;
            this.thresholdValue = thresholdValue;
            this.level = level;
            this.timestamp = timestamp;
        }

        // Getters
        public String getMetric() { return metric; }
        public double getCurrentValue() { return currentValue; }
        public double getThresholdValue() { return thresholdValue; }
        public AlarmLevel getLevel() { return level; }
        public Instant getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return "AlarmEvent{" +
                   "metric='" + metric + '\'' +
                   ", currentValue=" + currentValue +
                   ", thresholdValue=" + thresholdValue +
                   ", level=" + level +
                   ", timestamp=" + timestamp +
                   '}';
        }
    }

    /**
     * 统计报告
     */
    public static class StatisticsReport {
        private final String cacheName;
        private final Instant fromTime;
        private final Instant toTime;
        private final List<StatisticsSnapshot> snapshots;

        public StatisticsReport(String cacheName, Instant fromTime, Instant toTime, 
                               List<StatisticsSnapshot> snapshots) {
            this.cacheName = cacheName;
            this.fromTime = fromTime;
            this.toTime = toTime;
            this.snapshots = snapshots;
        }

        // Getters and analysis methods
        public String getCacheName() { return cacheName; }
        public Instant getFromTime() { return fromTime; }
        public Instant getToTime() { return toTime; }
        public List<StatisticsSnapshot> getSnapshots() { return snapshots; }

        public double getAverageHitRate() {
            return snapshots.stream().mapToDouble(StatisticsSnapshot::getHitRate).average().orElse(0.0);
        }

        public long getTotalRequests() {
            return snapshots.stream().mapToLong(StatisticsSnapshot::getTotalRequests).max().orElse(0);
        }
    }

    /**
     * 统计摘要
     */
    public static class StatisticsSummary {
        private StatisticsSnapshot current;
        private double hitRateTrend;
        private double requestsTrend;
        private String status;

        public StatisticsSummary() {
            this.status = "NO_DATA";
        }

        public StatisticsSummary(StatisticsSnapshot current, List<StatisticsSnapshot> recent) {
            this.current = current;
            calculateTrends(recent);
            this.status = determineStatus();
        }

        private void calculateTrends(List<StatisticsSnapshot> recent) {
            if (recent.size() < 2) {
                this.hitRateTrend = 0.0;
                this.requestsTrend = 0.0;
                return;
            }

            StatisticsSnapshot first = recent.get(0);
            StatisticsSnapshot last = recent.get(recent.size() - 1);

            this.hitRateTrend = last.getHitRate() - first.getHitRate();
            this.requestsTrend = last.getRequestsPerSecond() - first.getRequestsPerSecond();
        }

        private String determineStatus() {
            if (current == null) return "UNKNOWN";
            if (current.getHitRate() > 0.9) return "EXCELLENT";
            if (current.getHitRate() > 0.8) return "GOOD";
            if (current.getHitRate() > 0.6) return "FAIR";
            return "POOR";
        }

        // Getters
        public StatisticsSnapshot getCurrent() { return current; }
        public double getHitRateTrend() { return hitRateTrend; }
        public double getRequestsTrend() { return requestsTrend; }
        public String getStatus() { return status; }
    }
}