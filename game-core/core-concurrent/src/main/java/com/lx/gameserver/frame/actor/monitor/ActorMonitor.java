/*
 * 文件名: ActorMonitor.java
 * 用途: Actor监控器
 * 实现内容:
 *   - Actor实时状态查看和监控
 *   - 消息追踪和死锁检测
 *   - 性能瓶颈分析和监控仪表盘
 *   - 健康检查和报警机制
 * 技术选型:
 *   - 定时监控和异步检测
 *   - 监控数据聚合和分析
 *   - Web界面和API接口
 * 依赖关系:
 *   - 与ActorMetrics协作
 *   - 被管理控制台使用
 *   - 支持监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.monitor;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Actor监控器
 * <p>
 * 提供Actor系统的实时监控功能，包括状态查看、性能分析、
 * 死锁检测、健康检查等监控能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorMonitor.class);
    
    /** Actor系统引用 */
    private final ActorSystem actorSystem;
    
    /** 指标收集器 */
    private final ActorMetrics metrics;
    
    /** 消息追踪器 */
    private final MessageTracer messageTracer;
    
    /** 监控配置 */
    private final MonitorConfig config;
    
    /** 监控任务调度器 */
    private final ScheduledExecutorService scheduler;
    
    /** 是否已启动 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    
    /** 监控统计 */
    private final AtomicLong monitoringCycles = new AtomicLong(0);
    private final AtomicLong healthCheckFailures = new AtomicLong(0);
    
    /** 报警处理器 */
    private final List<AlertHandler> alertHandlers = new ArrayList<>();
    
    /** 活跃监控会话 */
    private final ConcurrentHashMap<String, MonitorSession> activeSessions = new ConcurrentHashMap<>();
    
    public ActorMonitor(ActorSystem actorSystem, MonitorConfig config) {
        this.actorSystem = actorSystem;
        this.config = config;
        this.metrics = ActorMetrics.getInstance();
        this.messageTracer = new MessageTracer(config.getMessageTraceConfig());
        this.scheduler = Executors.newScheduledThreadPool(
                config.getMonitorThreadCount(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("actor-monitor-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
        
        logger.info("Actor监控器初始化完成，系统: {}", actorSystem.getName());
    }
    
    public ActorMonitor(ActorSystem actorSystem) {
        this(actorSystem, MonitorConfig.defaultConfig());
    }
    
    /**
     * 启动监控
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        
        logger.info("启动Actor监控器");
        
        // 启动性能监控
        startPerformanceMonitoring();
        
        // 启动健康检查
        startHealthCheck();
        
        // 启动死锁检测
        startDeadlockDetection();
        
        // 启动消息追踪
        messageTracer.start();
        
        logger.info("Actor监控器启动完成");
    }
    
    /**
     * 停止监控
     */
    public void stop() {
        if (!started.get()) {
            return;
        }
        
        logger.info("停止Actor监控器");
        
        started.set(false);
        
        // 停止消息追踪
        messageTracer.stop();
        
        // 停止调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清理监控会话
        activeSessions.clear();
        
        logger.info("Actor监控器停止完成");
    }
    
    /**
     * 添加报警处理器
     */
    public void addAlertHandler(AlertHandler handler) {
        alertHandlers.add(handler);
    }
    
    /**
     * 移除报警处理器
     */
    public void removeAlertHandler(AlertHandler handler) {
        alertHandlers.remove(handler);
    }
    
    /**
     * 创建监控会话
     */
    public MonitorSession createSession(String sessionId) {
        MonitorSession session = new MonitorSession(sessionId, this);
        activeSessions.put(sessionId, session);
        logger.debug("创建监控会话: {}", sessionId);
        return session;
    }
    
    /**
     * 获取监控会话
     */
    public MonitorSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }
    
    /**
     * 关闭监控会话
     */
    public void closeSession(String sessionId) {
        MonitorSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.close();
            logger.debug("关闭监控会话: {}", sessionId);
        }
    }
    
    /**
     * 获取系统概览
     */
    public SystemOverview getSystemOverview() {
        ActorMetrics.SystemMetrics systemMetrics = metrics.getSystemMetrics();
        
        return new SystemOverview(
                actorSystem.getName(),
                systemMetrics.getCurrentActorCount(),
                systemMetrics.getTotalMessagesSent(),
                systemMetrics.getTotalMessagesProcessed(),
                systemMetrics.getSystemErrorRate(),
                systemMetrics.getSystemAverageProcessingTime(),
                systemMetrics.getMessageThroughput(Duration.ofMinutes(1)),
                Instant.now()
        );
    }
    
    /**
     * 获取Actor详情
     */
    public ActorDetails getActorDetails(String actorPath) {
        ActorMetrics.ActorMetric actorMetric = metrics.getActorMetric(actorPath);
        
        return new ActorDetails(
                actorPath,
                actorMetric.getCreatedTime(),
                actorMetric.getCurrentMailboxSize(),
                actorMetric.getMaxMailboxSize(),
                actorMetric.getMessagesProcessed(),
                actorMetric.getMessageErrors(),
                actorMetric.getErrorRate(),
                actorMetric.getAverageProcessingTime(),
                actorMetric.getMessageTypeMetrics()
        );
    }
    
    /**
     * 获取性能瓶颈
     */
    public List<PerformanceBottleneck> getPerformanceBottlenecks() {
        List<PerformanceBottleneck> bottlenecks = new ArrayList<>();
        
        // 检查高延迟Actor
        metrics.getAllActorMetrics().values().stream()
                .filter(metric -> metric.getAverageProcessingTime() > config.getHighLatencyThreshold())
                .forEach(metric -> bottlenecks.add(new PerformanceBottleneck(
                        BottleneckType.HIGH_LATENCY,
                        metric.getActorPath(),
                        "处理延迟过高: " + metric.getAverageProcessingTime() + "ms",
                        BottleneckSeverity.WARNING
                )));
        
        // 检查高错误率Actor
        metrics.getAllActorMetrics().values().stream()
                .filter(metric -> metric.getErrorRate() > config.getHighErrorRateThreshold())
                .forEach(metric -> bottlenecks.add(new PerformanceBottleneck(
                        BottleneckType.HIGH_ERROR_RATE,
                        metric.getActorPath(),
                        "错误率过高: " + String.format("%.2f%%", metric.getErrorRate() * 100),
                        BottleneckSeverity.ERROR
                )));
        
        // 检查大邮箱
        metrics.getAllActorMetrics().values().stream()
                .filter(metric -> metric.getCurrentMailboxSize() > config.getLargeMailboxThreshold())
                .forEach(metric -> bottlenecks.add(new PerformanceBottleneck(
                        BottleneckType.LARGE_MAILBOX,
                        metric.getActorPath(),
                        "邮箱积压: " + metric.getCurrentMailboxSize() + " 条消息",
                        BottleneckSeverity.WARNING
                )));
        
        return bottlenecks;
    }
    
    /**
     * 启动性能监控
     */
    private void startPerformanceMonitoring() {
        int intervalSeconds = config.getPerformanceMonitorIntervalSeconds();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                monitoringCycles.incrementAndGet();
                
                // 检查性能瓶颈
                List<PerformanceBottleneck> bottlenecks = getPerformanceBottlenecks();
                if (!bottlenecks.isEmpty()) {
                    handlePerformanceBottlenecks(bottlenecks);
                }
                
                // 更新监控会话
                updateMonitorSessions();
                
            } catch (Exception e) {
                logger.error("性能监控执行失败", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        logger.debug("性能监控已启动，间隔: {}秒", intervalSeconds);
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        int intervalSeconds = config.getHealthCheckIntervalSeconds();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean healthy = performHealthCheck();
                if (!healthy) {
                    healthCheckFailures.incrementAndGet();
                    handleHealthCheckFailure();
                }
            } catch (Exception e) {
                logger.error("健康检查执行失败", e);
                healthCheckFailures.incrementAndGet();
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        logger.debug("健康检查已启动，间隔: {}秒", intervalSeconds);
    }
    
    /**
     * 启动死锁检测
     */
    private void startDeadlockDetection() {
        int intervalSeconds = config.getDeadlockDetectionIntervalSeconds();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<DeadlockInfo> deadlocks = detectDeadlocks();
                if (!deadlocks.isEmpty()) {
                    handleDeadlocks(deadlocks);
                }
            } catch (Exception e) {
                logger.error("死锁检测执行失败", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        logger.debug("死锁检测已启动，间隔: {}秒", intervalSeconds);
    }
    
    /**
     * 执行健康检查
     */
    private boolean performHealthCheck() {
        // 检查系统指标
        ActorMetrics.SystemMetrics systemMetrics = metrics.getSystemMetrics();
        
        // 检查错误率
        if (systemMetrics.getSystemErrorRate() > config.getSystemErrorRateThreshold()) {
            return false;
        }
        
        // 检查平均处理时间
        if (systemMetrics.getSystemAverageProcessingTime() > config.getSystemLatencyThreshold()) {
            return false;
        }
        
        // 检查吞吐量
        double throughput = systemMetrics.getMessageThroughput(Duration.ofMinutes(1));
        if (throughput < config.getMinThroughputThreshold()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检测死锁
     */
    private List<DeadlockInfo> detectDeadlocks() {
        // 这里应该实现实际的死锁检测算法
        // 可以通过分析Actor之间的消息等待关系来检测死锁
        
        List<DeadlockInfo> deadlocks = new ArrayList<>();
        
        // 简化的死锁检测示例
        // 实际实现中需要更复杂的算法
        
        return deadlocks;
    }
    
    /**
     * 处理性能瓶颈
     */
    private void handlePerformanceBottlenecks(List<PerformanceBottleneck> bottlenecks) {
        for (PerformanceBottleneck bottleneck : bottlenecks) {
            Alert alert = new Alert(
                    AlertType.PERFORMANCE,
                    bottleneck.getSeverity().toString(),
                    bottleneck.getDescription(),
                    Map.of("actorPath", bottleneck.getActorPath(), "type", bottleneck.getType().toString()),
                    Instant.now()
            );
            
            triggerAlert(alert);
        }
    }
    
    /**
     * 处理健康检查失败
     */
    private void handleHealthCheckFailure() {
        Alert alert = new Alert(
                AlertType.HEALTH_CHECK,
                "ERROR",
                "系统健康检查失败",
                Map.of("failureCount", String.valueOf(healthCheckFailures.get())),
                Instant.now()
        );
        
        triggerAlert(alert);
    }
    
    /**
     * 处理死锁
     */
    private void handleDeadlocks(List<DeadlockInfo> deadlocks) {
        for (DeadlockInfo deadlock : deadlocks) {
            Alert alert = new Alert(
                    AlertType.DEADLOCK,
                    "CRITICAL",
                    "检测到Actor死锁: " + deadlock.getDescription(),
                    Map.of("involvedActors", String.join(",", deadlock.getInvolvedActors())),
                    Instant.now()
            );
            
            triggerAlert(alert);
        }
    }
    
    /**
     * 触发报警
     */
    private void triggerAlert(Alert alert) {
        logger.warn("触发报警: {}", alert);
        
        for (AlertHandler handler : alertHandlers) {
            try {
                handler.handleAlert(alert);
            } catch (Exception e) {
                logger.error("报警处理失败", e);
            }
        }
    }
    
    /**
     * 更新监控会话
     */
    private void updateMonitorSessions() {
        for (MonitorSession session : activeSessions.values()) {
            session.update();
        }
    }
    
    /**
     * 获取监控统计
     */
    public MonitorStats getMonitorStats() {
        return new MonitorStats(
                monitoringCycles.get(),
                healthCheckFailures.get(),
                activeSessions.size(),
                started.get()
        );
    }
    
    // 内部类和枚举定义...
    
    /**
     * 系统概览
     */
    public static class SystemOverview {
        private final String systemName;
        private final long actorCount;
        private final long totalMessagesSent;
        private final long totalMessagesProcessed;
        private final double errorRate;
        private final double averageProcessingTime;
        private final double throughput;
        private final Instant timestamp;
        
        public SystemOverview(String systemName, long actorCount, long totalMessagesSent, 
                            long totalMessagesProcessed, double errorRate, double averageProcessingTime,
                            double throughput, Instant timestamp) {
            this.systemName = systemName;
            this.actorCount = actorCount;
            this.totalMessagesSent = totalMessagesSent;
            this.totalMessagesProcessed = totalMessagesProcessed;
            this.errorRate = errorRate;
            this.averageProcessingTime = averageProcessingTime;
            this.throughput = throughput;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getSystemName() { return systemName; }
        public long getActorCount() { return actorCount; }
        public long getTotalMessagesSent() { return totalMessagesSent; }
        public long getTotalMessagesProcessed() { return totalMessagesProcessed; }
        public double getErrorRate() { return errorRate; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public double getThroughput() { return throughput; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Actor详情
     */
    public static class ActorDetails {
        private final String actorPath;
        private final Instant createdTime;
        private final long currentMailboxSize;
        private final long maxMailboxSize;
        private final long messagesProcessed;
        private final long messageErrors;
        private final double errorRate;
        private final double averageProcessingTime;
        private final Map<String, ActorMetrics.MessageTypeMetric> messageTypeMetrics;
        
        public ActorDetails(String actorPath, Instant createdTime, long currentMailboxSize,
                          long maxMailboxSize, long messagesProcessed, long messageErrors,
                          double errorRate, double averageProcessingTime,
                          Map<String, ActorMetrics.MessageTypeMetric> messageTypeMetrics) {
            this.actorPath = actorPath;
            this.createdTime = createdTime;
            this.currentMailboxSize = currentMailboxSize;
            this.maxMailboxSize = maxMailboxSize;
            this.messagesProcessed = messagesProcessed;
            this.messageErrors = messageErrors;
            this.errorRate = errorRate;
            this.averageProcessingTime = averageProcessingTime;
            this.messageTypeMetrics = messageTypeMetrics;
        }
        
        // Getters
        public String getActorPath() { return actorPath; }
        public Instant getCreatedTime() { return createdTime; }
        public long getCurrentMailboxSize() { return currentMailboxSize; }
        public long getMaxMailboxSize() { return maxMailboxSize; }
        public long getMessagesProcessed() { return messagesProcessed; }
        public long getMessageErrors() { return messageErrors; }
        public double getErrorRate() { return errorRate; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public Map<String, ActorMetrics.MessageTypeMetric> getMessageTypeMetrics() { return messageTypeMetrics; }
    }
    
    /**
     * 性能瓶颈类型
     */
    public enum BottleneckType {
        HIGH_LATENCY,
        HIGH_ERROR_RATE,
        LARGE_MAILBOX,
        LOW_THROUGHPUT,
        MEMORY_PRESSURE
    }
    
    /**
     * 瓶颈严重程度
     */
    public enum BottleneckSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    /**
     * 性能瓶颈
     */
    public static class PerformanceBottleneck {
        private final BottleneckType type;
        private final String actorPath;
        private final String description;
        private final BottleneckSeverity severity;
        private final Instant detectedTime;
        
        public PerformanceBottleneck(BottleneckType type, String actorPath, String description, BottleneckSeverity severity) {
            this.type = type;
            this.actorPath = actorPath;
            this.description = description;
            this.severity = severity;
            this.detectedTime = Instant.now();
        }
        
        public BottleneckType getType() { return type; }
        public String getActorPath() { return actorPath; }
        public String getDescription() { return description; }
        public BottleneckSeverity getSeverity() { return severity; }
        public Instant getDetectedTime() { return detectedTime; }
    }
    
    /**
     * 死锁信息
     */
    public static class DeadlockInfo {
        private final List<String> involvedActors;
        private final String description;
        private final Instant detectedTime;
        
        public DeadlockInfo(List<String> involvedActors, String description) {
            this.involvedActors = new ArrayList<>(involvedActors);
            this.description = description;
            this.detectedTime = Instant.now();
        }
        
        public List<String> getInvolvedActors() { return involvedActors; }
        public String getDescription() { return description; }
        public Instant getDetectedTime() { return detectedTime; }
    }
    
    /**
     * 报警类型
     */
    public enum AlertType {
        PERFORMANCE,
        HEALTH_CHECK,
        DEADLOCK,
        RESOURCE,
        SECURITY
    }
    
    /**
     * 报警信息
     */
    public static class Alert {
        private final AlertType type;
        private final String severity;
        private final String message;
        private final Map<String, String> metadata;
        private final Instant timestamp;
        
        public Alert(AlertType type, String severity, String message, Map<String, String> metadata, Instant timestamp) {
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.metadata = new HashMap<>(metadata);
            this.timestamp = timestamp;
        }
        
        public AlertType getType() { return type; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public Map<String, String> getMetadata() { return metadata; }
        public Instant getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("Alert{type=%s, severity=%s, message=%s, timestamp=%s}", 
                    type, severity, message, timestamp);
        }
    }
    
    /**
     * 报警处理器接口
     */
    public interface AlertHandler {
        void handleAlert(Alert alert);
    }
    
    /**
     * 监控会话
     */
    public static class MonitorSession {
        private final String sessionId;
        private final ActorMonitor monitor;
        private final Instant createdTime = Instant.now();
        private volatile Instant lastUpdateTime = Instant.now();
        
        public MonitorSession(String sessionId, ActorMonitor monitor) {
            this.sessionId = sessionId;
            this.monitor = monitor;
        }
        
        public void update() {
            lastUpdateTime = Instant.now();
        }
        
        public void close() {
            // 清理会话资源
        }
        
        public String getSessionId() { return sessionId; }
        public Instant getCreatedTime() { return createdTime; }
        public Instant getLastUpdateTime() { return lastUpdateTime; }
    }
    
    /**
     * 监控配置
     */
    public static class MonitorConfig {
        private final int monitorThreadCount;
        private final int performanceMonitorIntervalSeconds;
        private final int healthCheckIntervalSeconds;
        private final int deadlockDetectionIntervalSeconds;
        private final double highLatencyThreshold;
        private final double highErrorRateThreshold;
        private final int largeMailboxThreshold;
        private final double systemErrorRateThreshold;
        private final double systemLatencyThreshold;
        private final double minThroughputThreshold;
        private final MessageTracer.MessageTraceConfig messageTraceConfig;
        
        public MonitorConfig(int monitorThreadCount, int performanceMonitorIntervalSeconds,
                           int healthCheckIntervalSeconds, int deadlockDetectionIntervalSeconds,
                           double highLatencyThreshold, double highErrorRateThreshold,
                           int largeMailboxThreshold, double systemErrorRateThreshold,
                           double systemLatencyThreshold, double minThroughputThreshold,
                           MessageTracer.MessageTraceConfig messageTraceConfig) {
            this.monitorThreadCount = monitorThreadCount;
            this.performanceMonitorIntervalSeconds = performanceMonitorIntervalSeconds;
            this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
            this.deadlockDetectionIntervalSeconds = deadlockDetectionIntervalSeconds;
            this.highLatencyThreshold = highLatencyThreshold;
            this.highErrorRateThreshold = highErrorRateThreshold;
            this.largeMailboxThreshold = largeMailboxThreshold;
            this.systemErrorRateThreshold = systemErrorRateThreshold;
            this.systemLatencyThreshold = systemLatencyThreshold;
            this.minThroughputThreshold = minThroughputThreshold;
            this.messageTraceConfig = messageTraceConfig;
        }
        
        public static MonitorConfig defaultConfig() {
            return new MonitorConfig(
                    2, 30, 60, 120,
                    1000.0, 0.05, 1000,
                    0.01, 2000.0, 100.0,
                    MessageTracer.MessageTraceConfig.defaultConfig()
            );
        }
        
        // Getters
        public int getMonitorThreadCount() { return monitorThreadCount; }
        public int getPerformanceMonitorIntervalSeconds() { return performanceMonitorIntervalSeconds; }
        public int getHealthCheckIntervalSeconds() { return healthCheckIntervalSeconds; }
        public int getDeadlockDetectionIntervalSeconds() { return deadlockDetectionIntervalSeconds; }
        public double getHighLatencyThreshold() { return highLatencyThreshold; }
        public double getHighErrorRateThreshold() { return highErrorRateThreshold; }
        public int getLargeMailboxThreshold() { return largeMailboxThreshold; }
        public double getSystemErrorRateThreshold() { return systemErrorRateThreshold; }
        public double getSystemLatencyThreshold() { return systemLatencyThreshold; }
        public double getMinThroughputThreshold() { return minThroughputThreshold; }
        public MessageTracer.MessageTraceConfig getMessageTraceConfig() { return messageTraceConfig; }
    }
    
    /**
     * 监控统计
     */
    public static class MonitorStats {
        private final long monitoringCycles;
        private final long healthCheckFailures;
        private final int activeSessions;
        private final boolean running;
        
        public MonitorStats(long monitoringCycles, long healthCheckFailures, int activeSessions, boolean running) {
            this.monitoringCycles = monitoringCycles;
            this.healthCheckFailures = healthCheckFailures;
            this.activeSessions = activeSessions;
            this.running = running;
        }
        
        public long getMonitoringCycles() { return monitoringCycles; }
        public long getHealthCheckFailures() { return healthCheckFailures; }
        public int getActiveSessions() { return activeSessions; }
        public boolean isRunning() { return running; }
    }
}