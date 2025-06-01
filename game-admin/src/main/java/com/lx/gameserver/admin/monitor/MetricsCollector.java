/*
 * 文件名: MetricsCollector.java
 * 用途: 指标采集器实现
 * 实现内容:
 *   - JVM运行时指标采集
 *   - 业务指标采集管理
 *   - 自定义指标注册
 *   - 时序数据存储
 *   - Prometheus集成支持
 *   - 指标聚合和统计
 * 技术选型:
 *   - Micrometer (指标抽象层)
 *   - Prometheus (指标导出)
 *   - Spring Boot Actuator (内置指标)
 *   - Redis (指标缓存)
 * 依赖关系: 被ServiceMonitor和RealTimeMonitor使用
 */
package com.lx.gameserver.admin.monitor;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标采集器
 * <p>
 * 负责采集系统运行时的各种指标数据，包括JVM指标、业务指标、
 * 自定义指标等。支持指标的聚合、存储和导出，提供
 * 时序数据的查询和分析功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Service
public class MetricsCollector {

    /** Micrometer注册表 */
    @Autowired
    private MeterRegistry meterRegistry;

    /** Redis模板 */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** 自定义指标注册表 */
    private final Map<String, CustomMetric> customMetrics = new ConcurrentHashMap<>();

    /** 指标历史数据 */
    private final Map<String, List<MetricRecord>> metricsHistory = new ConcurrentHashMap<>();

    /** 采集任务执行器 */
    private ScheduledExecutorService collectorExecutor;

    /** 采集任务Future */
    private ScheduledFuture<?> collectionTask;

    /** 采集间隔(秒) */
    private static final int COLLECTION_INTERVAL = 10;

    /** 历史数据保留条数 */
    private static final int HISTORY_LIMIT = 360; // 1小时数据(10秒间隔)

    /** Redis键前缀 */
    private static final String REDIS_METRICS_PREFIX = "admin:metrics:";
    private static final String REDIS_METRICS_HISTORY_PREFIX = "admin:metrics:history:";

    /** 内置计数器 */
    private Counter requestCounter;
    private Counter errorCounter;
    private io.micrometer.core.instrument.Timer requestTimer;
    private Gauge memoryUsageGauge;

    /** 业务指标计数器 */
    private final AtomicLong onlineUserCount = new AtomicLong(0);
    private final AtomicLong totalLoginCount = new AtomicLong(0);
    private final AtomicLong totalApiCalls = new AtomicLong(0);

    /**
     * 初始化指标采集器
     */
    @PostConstruct
    public void init() {
        log.info("初始化指标采集器...");
        
        // 注册JVM指标
        registerJvmMetrics();
        
        // 注册业务指标
        registerBusinessMetrics();
        
        // 创建采集任务执行器
        collectorExecutor = Executors.newScheduledThreadPool(1, 
            r -> new Thread(r, "MetricsCollector-Thread"));
        
        // 启动采集任务
        startCollectionTask();
        
        log.info("指标采集器初始化完成");
    }

    /**
     * 注册自定义指标
     *
     * @param name 指标名称
     * @param description 指标描述
     * @param type 指标类型
     * @param tags 标签
     * @return 自定义指标对象
     */
    public CustomMetric registerCustomMetric(String name, String description, 
                                           MetricType type, Map<String, String> tags) {
        CustomMetric metric = new CustomMetric(name, description, type, tags);
        customMetrics.put(name, metric);
        metricsHistory.put(name, new CopyOnWriteArrayList<>());
        
        // 根据类型注册到Micrometer
        switch (type) {
            case COUNTER:
                metric.setMicrometerMetric(Counter.builder(name)
                    .description(description)
                    .tags(Tags.of(tags.entrySet().stream()
                        .map(e -> Tag.of(e.getKey(), e.getValue()))
                        .toArray(Tag[]::new)))
                    .register(meterRegistry));
                break;
            case GAUGE:
                // For Gauge, register it with the metric value supplier
                Gauge.builder(name, metric, CustomMetric::getValue)
                    .description(description)
                    .tags(Tags.of(tags.entrySet().stream()
                        .map(e -> Tag.of(e.getKey(), e.getValue()))
                        .toArray(Tag[]::new)))
                    .register(meterRegistry);
                break;
            case TIMER:
                metric.setMicrometerMetric(io.micrometer.core.instrument.Timer.builder(name)
                    .description(description)
                    .tags(Tags.of(tags.entrySet().stream()
                        .map(e -> Tag.of(e.getKey(), e.getValue()))
                        .toArray(Tag[]::new)))
                    .register(meterRegistry));
                break;
        }
        
        log.info("注册自定义指标: {} ({})", name, type);
        return metric;
    }

    /**
     * 获取指标当前值
     *
     * @param metricName 指标名称
     * @return 指标值
     */
    public Double getMetricValue(String metricName) {
        // 先查找自定义指标
        CustomMetric customMetric = customMetrics.get(metricName);
        if (customMetric != null) {
            return customMetric.getValue();
        }
        
        // 查找Micrometer指标
        Meter meter = meterRegistry.find(metricName).meter();
        if (meter != null) {
            if (meter instanceof Counter) {
                return ((Counter) meter).count();
            } else if (meter instanceof Gauge) {
                return ((Gauge) meter).value();
            } else if (meter instanceof io.micrometer.core.instrument.Timer) {
                return ((io.micrometer.core.instrument.Timer) meter).mean(TimeUnit.MILLISECONDS);
            }
        }
        
        return null;
    }

    /**
     * 获取指标历史数据
     *
     * @param metricName 指标名称
     * @param duration 时间范围(分钟)
     * @return 历史数据列表
     */
    public List<MetricRecord> getMetricHistory(String metricName, int duration) {
        List<MetricRecord> history = metricsHistory.get(metricName);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(duration);
        return history.stream()
            .filter(record -> record.getTimestamp().isAfter(cutoffTime))
            .toList();
    }

    /**
     * 获取所有指标概览
     *
     * @return 指标概览信息
     */
    public MetricsOverview getMetricsOverview() {
        Map<String, Double> currentValues = new HashMap<>();
        
        // 添加内置指标
        currentValues.put("requests.total", requestCounter.count());
        currentValues.put("errors.total", errorCounter.count());
        currentValues.put("requests.avg.duration", requestTimer.mean(TimeUnit.MILLISECONDS));
        currentValues.put("memory.usage", getMemoryUsagePercentage());
        
        // 添加业务指标
        currentValues.put("users.online", (double) onlineUserCount.get());
        currentValues.put("logins.total", (double) totalLoginCount.get());
        currentValues.put("api.calls.total", (double) totalApiCalls.get());
        
        // 添加自定义指标
        for (Map.Entry<String, CustomMetric> entry : customMetrics.entrySet()) {
            currentValues.put(entry.getKey(), entry.getValue().getValue());
        }
        
        return new MetricsOverview(
            currentValues,
            customMetrics.size(),
            LocalDateTime.now()
        );
    }

    /**
     * 增加请求计数
     */
    public void incrementRequestCount() {
        requestCounter.increment();
        totalApiCalls.incrementAndGet();
    }

    /**
     * 增加错误计数
     */
    public void incrementErrorCount() {
        errorCounter.increment();
    }

    /**
     * 记录请求时间
     *
     * @param duration 请求时长(毫秒)
     */
    public void recordRequestTime(long duration) {
        requestTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    /**
     * 更新在线用户数
     *
     * @param count 在线用户数
     */
    public void updateOnlineUserCount(long count) {
        onlineUserCount.set(count);
    }

    /**
     * 增加登录计数
     */
    public void incrementLoginCount() {
        totalLoginCount.incrementAndGet();
    }

    /**
     * 导出Prometheus格式指标
     *
     * @return Prometheus格式的指标数据
     */
    public String exportPrometheusMetrics() {
        StringBuilder sb = new StringBuilder();
        
        // 导出内置指标
        sb.append("# HELP admin_requests_total Total number of requests\n");
        sb.append("# TYPE admin_requests_total counter\n");
        sb.append("admin_requests_total ").append(requestCounter.count()).append("\n");
        
        sb.append("# HELP admin_errors_total Total number of errors\n");
        sb.append("# TYPE admin_errors_total counter\n");
        sb.append("admin_errors_total ").append(errorCounter.count()).append("\n");
        
        sb.append("# HELP admin_memory_usage_ratio Memory usage ratio\n");
        sb.append("# TYPE admin_memory_usage_ratio gauge\n");
        sb.append("admin_memory_usage_ratio ").append(getMemoryUsagePercentage() / 100.0).append("\n");
        
        // 导出业务指标
        sb.append("# HELP admin_users_online Current online users\n");
        sb.append("# TYPE admin_users_online gauge\n");
        sb.append("admin_users_online ").append(onlineUserCount.get()).append("\n");
        
        // 导出自定义指标
        for (Map.Entry<String, CustomMetric> entry : customMetrics.entrySet()) {
            String name = entry.getKey().replace('.', '_');
            CustomMetric metric = entry.getValue();
            
            sb.append("# HELP admin_").append(name).append(" ").append(metric.getDescription()).append("\n");
            sb.append("# TYPE admin_").append(name).append(" ").append(metric.getType().name().toLowerCase()).append("\n");
            sb.append("admin_").append(name).append(" ").append(metric.getValue()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * 注册JVM指标
     */
    private void registerJvmMetrics() {
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
        
        log.debug("JVM指标注册完成");
    }

    /**
     * 注册业务指标
     */
    private void registerBusinessMetrics() {
        // 注册计数器
        requestCounter = Counter.builder("admin.requests.total")
            .description("Total number of admin requests")
            .register(meterRegistry);
        
        errorCounter = Counter.builder("admin.errors.total")
            .description("Total number of admin errors")
            .register(meterRegistry);
        
        // 注册计时器
        requestTimer = io.micrometer.core.instrument.Timer.builder("admin.requests.duration")
            .description("Admin request duration")
            .register(meterRegistry);
        
        // 注册仪表
        memoryUsageGauge = Gauge.builder("admin.memory.usage.percentage", this, MetricsCollector::getMemoryUsagePercentage)
            .description("Memory usage percentage")
            .register(meterRegistry);
        
        Gauge.builder("admin.users.online", onlineUserCount, AtomicLong::doubleValue)
            .description("Current online users")
            .register(meterRegistry);
        
        log.debug("业务指标注册完成");
    }

    /**
     * 启动采集任务
     */
    private void startCollectionTask() {
        collectionTask = collectorExecutor.scheduleWithFixedDelay(
            this::collectMetrics,
            5, // 初始延迟5秒
            COLLECTION_INTERVAL,
            TimeUnit.SECONDS
        );
        
        log.info("指标采集任务已启动，采集间隔: {} 秒", COLLECTION_INTERVAL);
    }

    /**
     * 采集指标数据
     */
    private void collectMetrics() {
        try {
            LocalDateTime timestamp = LocalDateTime.now();
            
            // 采集内置指标
            recordMetric("requests.total", requestCounter.count(), timestamp);
            recordMetric("errors.total", errorCounter.count(), timestamp);
            recordMetric("memory.usage", getMemoryUsagePercentage(), timestamp);
            
            // 采集业务指标
            recordMetric("users.online", (double) onlineUserCount.get(), timestamp);
            recordMetric("logins.total", (double) totalLoginCount.get(), timestamp);
            
            // 采集自定义指标
            for (Map.Entry<String, CustomMetric> entry : customMetrics.entrySet()) {
                recordMetric(entry.getKey(), entry.getValue().getValue(), timestamp);
            }
            
        } catch (Exception e) {
            log.error("指标采集失败", e);
        }
    }

    /**
     * 记录指标数据
     */
    private void recordMetric(String metricName, double value, LocalDateTime timestamp) {
        MetricRecord record = new MetricRecord(metricName, value, timestamp);
        
        // 记录到内存历史
        List<MetricRecord> history = metricsHistory.computeIfAbsent(metricName, 
            k -> new CopyOnWriteArrayList<>());
        history.add(record);
        
        // 限制历史记录数量
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
        
        // 缓存到Redis
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_METRICS_PREFIX + metricName;
                redisTemplate.opsForValue().set(redisKey, record, 1, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("缓存指标到Redis失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取内存使用百分比
     */
    private double getMemoryUsagePercentage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return maxMemory > 0 ? (double) usedMemory / maxMemory * 100.0 : 0.0;
    }

    /**
     * 关闭指标采集器
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭指标采集器...");
        
        if (collectionTask != null) {
            collectionTask.cancel(true);
        }
        
        if (collectorExecutor != null) {
            collectorExecutor.shutdown();
            try {
                if (!collectorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    collectorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                collectorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("指标采集器已关闭");
    }

    /**
     * 指标类型枚举
     */
    public enum MetricType {
        COUNTER,    // 计数器
        GAUGE,      // 仪表
        TIMER,      // 计时器
        HISTOGRAM   // 直方图
    }

    /**
     * 自定义指标
     */
    public static class CustomMetric {
        private String name;
        private String description;
        private MetricType type;
        private Map<String, String> tags;
        private double value;
        private Object micrometerMetric;

        public CustomMetric(String name, String description, MetricType type, Map<String, String> tags) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.tags = tags;
            this.value = 0.0;
        }

        public void increment() {
            if (type == MetricType.COUNTER) {
                value++;
                if (micrometerMetric instanceof Counter) {
                    ((Counter) micrometerMetric).increment();
                }
            }
        }

        public void increment(double amount) {
            if (type == MetricType.COUNTER) {
                value += amount;
                if (micrometerMetric instanceof Counter) {
                    ((Counter) micrometerMetric).increment(amount);
                }
            }
        }

        public void setValue(double newValue) {
            this.value = newValue;
        }

        // Getter和Setter方法
        public String getName() { return name; }
        public String getDescription() { return description; }
        public MetricType getType() { return type; }
        public Map<String, String> getTags() { return tags; }
        public double getValue() { return value; }
        public Object getMicrometerMetric() { return micrometerMetric; }
        public void setMicrometerMetric(Object micrometerMetric) { this.micrometerMetric = micrometerMetric; }
    }

    /**
     * 指标记录
     */
    public static class MetricRecord {
        private String metricName;
        private double value;
        private LocalDateTime timestamp;

        public MetricRecord(String metricName, double value, LocalDateTime timestamp) {
            this.metricName = metricName;
            this.value = value;
            this.timestamp = timestamp;
        }

        // Getter方法
        public String getMetricName() { return metricName; }
        public double getValue() { return value; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * 指标概览
     */
    public static class MetricsOverview {
        private Map<String, Double> currentValues;
        private int customMetricsCount;
        private LocalDateTime timestamp;

        public MetricsOverview(Map<String, Double> currentValues, int customMetricsCount, LocalDateTime timestamp) {
            this.currentValues = currentValues;
            this.customMetricsCount = customMetricsCount;
            this.timestamp = timestamp;
        }

        // Getter方法
        public Map<String, Double> getCurrentValues() { return currentValues; }
        public int getCustomMetricsCount() { return customMetricsCount; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}