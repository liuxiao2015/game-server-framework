/*
 * 文件名: MessageTracer.java
 * 用途: 消息追踪器
 * 实现内容:
 *   - 消息流程追踪和调用链路分析
 *   - 消息延迟分析和丢失检测
 *   - 分布式追踪和性能分析
 *   - 追踪数据存储和查询
 * 技术选型:
 *   - 轻量级追踪机制避免性能影响
 *   - 采样策略控制追踪开销
 *   - 环形缓冲区高效存储追踪数据
 * 依赖关系:
 *   - 被ActorMonitor使用
 *   - 与消息系统集成
 *   - 支持分布式追踪协议
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.monitor;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Message;
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
 * 消息追踪器
 * <p>
 * 提供消息流程的详细追踪功能，包括消息路径分析、
 * 延迟统计、调用链路追踪等能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class MessageTracer {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageTracer.class);
    
    /** 追踪配置 */
    private final MessageTraceConfig config;
    
    /** 追踪数据存储 */
    private final TraceStorage traceStorage;
    
    /** 追踪ID生成器 */
    private final AtomicLong traceIdGenerator = new AtomicLong(0);
    
    /** 是否已启动 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    
    /** 追踪统计 */
    private final AtomicLong totalTraces = new AtomicLong(0);
    private final AtomicLong sampledTraces = new AtomicLong(0);
    private final AtomicLong droppedTraces = new AtomicLong(0);
    
    /** 当前追踪上下文 */
    private final ThreadLocal<TraceContext> currentContext = new ThreadLocal<>();
    
    /** 追踪数据处理器 */
    private final ExecutorService traceProcessor;
    
    public MessageTracer(MessageTraceConfig config) {
        this.config = config;
        this.traceStorage = new TraceStorage(config.getMaxTraces());
        this.traceProcessor = Executors.newFixedThreadPool(
                config.getProcessorThreadCount(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("message-tracer-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
        
        logger.info("消息追踪器初始化完成，采样率: {}%", config.getSamplingRate() * 100);
    }
    
    /**
     * 启动追踪器
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        
        logger.info("启动消息追踪器");
    }
    
    /**
     * 停止追踪器
     */
    public void stop() {
        if (!started.get()) {
            return;
        }
        
        logger.info("停止消息追踪器");
        
        started.set(false);
        
        // 停止处理器
        traceProcessor.shutdown();
        try {
            if (!traceProcessor.awaitTermination(30, TimeUnit.SECONDS)) {
                traceProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            traceProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("消息追踪器停止完成");
    }
    
    /**
     * 开始消息追踪
     */
    public TraceContext startTrace(ActorRef sender, ActorRef receiver, Object message) {
        if (!started.get() || !shouldSample()) {
            return null;
        }
        
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        
        TraceContext context = new TraceContext(traceId, spanId, null);
        currentContext.set(context);
        
        MessageSpan span = new MessageSpan(
                traceId,
                spanId,
                null,
                sender != null ? sender.getPath() : "unknown",
                receiver.getPath(),
                message.getClass().getSimpleName(),
                Instant.now()
        );
        
        context.addSpan(span);
        totalTraces.incrementAndGet();
        sampledTraces.incrementAndGet();
        
        logger.debug("开始消息追踪: {}", traceId);
        return context;
    }
    
    /**
     * 继续追踪（在消息转发时）
     */
    public TraceContext continueTrace(String traceId, String parentSpanId, ActorRef sender, ActorRef receiver, Object message) {
        if (!started.get() || traceId == null) {
            return null;
        }
        
        String spanId = generateSpanId();
        
        TraceContext context = new TraceContext(traceId, spanId, parentSpanId);
        currentContext.set(context);
        
        MessageSpan span = new MessageSpan(
                traceId,
                spanId,
                parentSpanId,
                sender != null ? sender.getPath() : "unknown",
                receiver.getPath(),
                message.getClass().getSimpleName(),
                Instant.now()
        );
        
        context.addSpan(span);
        
        logger.debug("继续消息追踪: {} -> {}", traceId, spanId);
        return context;
    }
    
    /**
     * 结束当前span
     */
    public void finishSpan(TraceContext context, boolean success, String errorMessage) {
        if (context == null) {
            return;
        }
        
        MessageSpan currentSpan = context.getCurrentSpan();
        if (currentSpan != null) {
            currentSpan.finish(success, errorMessage);
            
            // 异步存储追踪数据
            traceProcessor.submit(() -> {
                try {
                    traceStorage.storeSpan(currentSpan);
                } catch (Exception e) {
                    logger.warn("存储追踪数据失败", e);
                    droppedTraces.incrementAndGet();
                }
            });
        }
        
        currentContext.remove();
        logger.debug("结束消息追踪: {}", context.getTraceId());
    }
    
    /**
     * 获取当前追踪上下文
     */
    public TraceContext getCurrentContext() {
        return currentContext.get();
    }
    
    /**
     * 查询追踪数据
     */
    public List<MessageTrace> queryTraces(TraceQuery query) {
        return traceStorage.query(query);
    }
    
    /**
     * 分析消息延迟
     */
    public LatencyAnalysis analyzeLatency(String actorPath, Duration timeWindow) {
        Instant since = Instant.now().minus(timeWindow);
        
        List<MessageSpan> spans = traceStorage.getSpansForActor(actorPath, since);
        
        if (spans.isEmpty()) {
            return new LatencyAnalysis(actorPath, 0, 0.0, 0L, 0L, Collections.emptyList());
        }
        
        List<Long> latencies = spans.stream()
                .filter(span -> span.getDuration() != null)
                .map(span -> span.getDuration().toMillis())
                .sorted()
                .collect(Collectors.toList());
        
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minLatency = latencies.get(0);
        long maxLatency = latencies.get(latencies.size() - 1);
        
        // 计算百分位数
        List<LatencyPercentile> percentiles = Arrays.asList(
                new LatencyPercentile(50, getPercentile(latencies, 0.5)),
                new LatencyPercentile(90, getPercentile(latencies, 0.9)),
                new LatencyPercentile(95, getPercentile(latencies, 0.95)),
                new LatencyPercentile(99, getPercentile(latencies, 0.99))
        );
        
        return new LatencyAnalysis(actorPath, latencies.size(), avgLatency, minLatency, maxLatency, percentiles);
    }
    
    /**
     * 检测消息丢失
     */
    public List<LostMessage> detectLostMessages(Duration timeWindow) {
        List<LostMessage> lostMessages = new ArrayList<>();
        
        // 这里应该实现消息丢失检测逻辑
        // 可以通过分析未完成的追踪来检测丢失的消息
        
        return lostMessages;
    }
    
    /**
     * 生成追踪ID
     */
    private String generateTraceId() {
        return "trace-" + System.currentTimeMillis() + "-" + traceIdGenerator.incrementAndGet();
    }
    
    /**
     * 生成span ID
     */
    private String generateSpanId() {
        return "span-" + System.nanoTime();
    }
    
    /**
     * 判断是否应该采样
     */
    private boolean shouldSample() {
        return Math.random() < config.getSamplingRate();
    }
    
    /**
     * 计算百分位数
     */
    private long getPercentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }
    
    /**
     * 获取追踪统计
     */
    public TracingStats getStats() {
        return new TracingStats(
                totalTraces.get(),
                sampledTraces.get(),
                droppedTraces.get(),
                traceStorage.getStoredTraceCount(),
                config.getSamplingRate()
        );
    }
    
    /**
     * 追踪上下文
     */
    public static class TraceContext {
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final List<MessageSpan> spans = new ArrayList<>();
        private final Map<String, String> baggage = new HashMap<>();
        
        public TraceContext(String traceId, String spanId, String parentSpanId) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
        }
        
        public void addSpan(MessageSpan span) {
            spans.add(span);
        }
        
        public MessageSpan getCurrentSpan() {
            return spans.isEmpty() ? null : spans.get(spans.size() - 1);
        }
        
        public void setBaggage(String key, String value) {
            baggage.put(key, value);
        }
        
        public String getBaggage(String key) {
            return baggage.get(key);
        }
        
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getParentSpanId() { return parentSpanId; }
        public List<MessageSpan> getSpans() { return new ArrayList<>(spans); }
        public Map<String, String> getBaggage() { return new HashMap<>(baggage); }
    }
    
    /**
     * 消息span
     */
    public static class MessageSpan {
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final String senderPath;
        private final String receiverPath;
        private final String messageType;
        private final Instant startTime;
        private volatile Instant endTime;
        private volatile boolean success;
        private volatile String errorMessage;
        private final Map<String, String> tags = new HashMap<>();
        
        public MessageSpan(String traceId, String spanId, String parentSpanId, 
                          String senderPath, String receiverPath, String messageType, Instant startTime) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.senderPath = senderPath;
            this.receiverPath = receiverPath;
            this.messageType = messageType;
            this.startTime = startTime;
        }
        
        public void finish(boolean success, String errorMessage) {
            this.endTime = Instant.now();
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public void addTag(String key, String value) {
            tags.put(key, value);
        }
        
        public Duration getDuration() {
            return endTime != null ? Duration.between(startTime, endTime) : null;
        }
        
        // Getters
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getParentSpanId() { return parentSpanId; }
        public String getSenderPath() { return senderPath; }
        public String getReceiverPath() { return receiverPath; }
        public String getMessageType() { return messageType; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, String> getTags() { return new HashMap<>(tags); }
        
        @Override
        public String toString() {
            return String.format("MessageSpan{trace=%s, span=%s, %s->%s, type=%s, duration=%s}",
                    traceId, spanId, senderPath, receiverPath, messageType, getDuration());
        }
    }
    
    /**
     * 追踪存储
     */
    private static class TraceStorage {
        private final int maxTraces;
        private final ConcurrentLinkedQueue<MessageSpan> spans = new ConcurrentLinkedQueue<>();
        private final AtomicLong storedCount = new AtomicLong(0);
        
        public TraceStorage(int maxTraces) {
            this.maxTraces = maxTraces;
        }
        
        public void storeSpan(MessageSpan span) {
            spans.offer(span);
            storedCount.incrementAndGet();
            
            // 控制存储大小
            while (spans.size() > maxTraces) {
                spans.poll();
            }
        }
        
        public List<MessageTrace> query(TraceQuery query) {
            Map<String, List<MessageSpan>> traceGroups = spans.stream()
                    .filter(span -> query.matches(span))
                    .collect(Collectors.groupingBy(MessageSpan::getTraceId));
            
            return traceGroups.entrySet().stream()
                    .map(entry -> new MessageTrace(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparing(trace -> trace.getStartTime()))
                    .limit(query.getLimit())
                    .collect(Collectors.toList());
        }
        
        public List<MessageSpan> getSpansForActor(String actorPath, Instant since) {
            return spans.stream()
                    .filter(span -> (span.getSenderPath().equals(actorPath) || span.getReceiverPath().equals(actorPath))
                            && span.getStartTime().isAfter(since))
                    .collect(Collectors.toList());
        }
        
        public long getStoredTraceCount() {
            return storedCount.get();
        }
    }
    
    /**
     * 追踪查询
     */
    public static class TraceQuery {
        private String traceId;
        private String actorPath;
        private String messageType;
        private Instant startTime;
        private Instant endTime;
        private Boolean success;
        private int limit = 100;
        
        public TraceQuery traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public TraceQuery actorPath(String actorPath) {
            this.actorPath = actorPath;
            return this;
        }
        
        public TraceQuery messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }
        
        public TraceQuery timeRange(Instant startTime, Instant endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
            return this;
        }
        
        public TraceQuery success(Boolean success) {
            this.success = success;
            return this;
        }
        
        public TraceQuery limit(int limit) {
            this.limit = limit;
            return this;
        }
        
        public boolean matches(MessageSpan span) {
            if (traceId != null && !traceId.equals(span.getTraceId())) {
                return false;
            }
            if (actorPath != null && !actorPath.equals(span.getSenderPath()) && !actorPath.equals(span.getReceiverPath())) {
                return false;
            }
            if (messageType != null && !messageType.equals(span.getMessageType())) {
                return false;
            }
            if (startTime != null && span.getStartTime().isBefore(startTime)) {
                return false;
            }
            if (endTime != null && span.getEndTime() != null && span.getEndTime().isAfter(endTime)) {
                return false;
            }
            if (success != null && success != span.isSuccess()) {
                return false;
            }
            return true;
        }
        
        public int getLimit() { return limit; }
    }
    
    /**
     * 消息追踪
     */
    public static class MessageTrace {
        private final String traceId;
        private final List<MessageSpan> spans;
        private final Instant startTime;
        private final Duration totalDuration;
        
        public MessageTrace(String traceId, List<MessageSpan> spans) {
            this.traceId = traceId;
            this.spans = new ArrayList<>(spans);
            this.spans.sort(Comparator.comparing(MessageSpan::getStartTime));
            this.startTime = this.spans.isEmpty() ? Instant.now() : this.spans.get(0).getStartTime();
            
            // 计算总持续时间
            Instant endTime = this.spans.stream()
                    .map(MessageSpan::getEndTime)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(startTime);
            this.totalDuration = Duration.between(startTime, endTime);
        }
        
        public String getTraceId() { return traceId; }
        public List<MessageSpan> getSpans() { return spans; }
        public Instant getStartTime() { return startTime; }
        public Duration getTotalDuration() { return totalDuration; }
        public int getSpanCount() { return spans.size(); }
        public boolean hasErrors() {
            return spans.stream().anyMatch(span -> !span.isSuccess());
        }
    }
    
    /**
     * 延迟分析
     */
    public static class LatencyAnalysis {
        private final String actorPath;
        private final int sampleCount;
        private final double averageLatency;
        private final long minLatency;
        private final long maxLatency;
        private final List<LatencyPercentile> percentiles;
        
        public LatencyAnalysis(String actorPath, int sampleCount, double averageLatency,
                             long minLatency, long maxLatency, List<LatencyPercentile> percentiles) {
            this.actorPath = actorPath;
            this.sampleCount = sampleCount;
            this.averageLatency = averageLatency;
            this.minLatency = minLatency;
            this.maxLatency = maxLatency;
            this.percentiles = percentiles;
        }
        
        public String getActorPath() { return actorPath; }
        public int getSampleCount() { return sampleCount; }
        public double getAverageLatency() { return averageLatency; }
        public long getMinLatency() { return minLatency; }
        public long getMaxLatency() { return maxLatency; }
        public List<LatencyPercentile> getPercentiles() { return percentiles; }
    }
    
    /**
     * 延迟百分位数
     */
    public static class LatencyPercentile {
        private final int percentile;
        private final long latency;
        
        public LatencyPercentile(int percentile, long latency) {
            this.percentile = percentile;
            this.latency = latency;
        }
        
        public int getPercentile() { return percentile; }
        public long getLatency() { return latency; }
    }
    
    /**
     * 丢失消息
     */
    public static class LostMessage {
        private final String traceId;
        private final String senderPath;
        private final String receiverPath;
        private final String messageType;
        private final Instant lastSeenTime;
        
        public LostMessage(String traceId, String senderPath, String receiverPath, String messageType, Instant lastSeenTime) {
            this.traceId = traceId;
            this.senderPath = senderPath;
            this.receiverPath = receiverPath;
            this.messageType = messageType;
            this.lastSeenTime = lastSeenTime;
        }
        
        public String getTraceId() { return traceId; }
        public String getSenderPath() { return senderPath; }
        public String getReceiverPath() { return receiverPath; }
        public String getMessageType() { return messageType; }
        public Instant getLastSeenTime() { return lastSeenTime; }
    }
    
    /**
     * 追踪配置
     */
    public static class MessageTraceConfig {
        private final boolean enabled;
        private final double samplingRate;
        private final int maxTraces;
        private final int processorThreadCount;
        private final Duration traceTimeout;
        
        public MessageTraceConfig(boolean enabled, double samplingRate, int maxTraces, 
                                int processorThreadCount, Duration traceTimeout) {
            this.enabled = enabled;
            this.samplingRate = samplingRate;
            this.maxTraces = maxTraces;
            this.processorThreadCount = processorThreadCount;
            this.traceTimeout = traceTimeout;
        }
        
        public static MessageTraceConfig defaultConfig() {
            return new MessageTraceConfig(true, 0.01, 10000, 2, Duration.ofMinutes(5));
        }
        
        public boolean isEnabled() { return enabled; }
        public double getSamplingRate() { return samplingRate; }
        public int getMaxTraces() { return maxTraces; }
        public int getProcessorThreadCount() { return processorThreadCount; }
        public Duration getTraceTimeout() { return traceTimeout; }
    }
    
    /**
     * 追踪统计
     */
    public static class TracingStats {
        private final long totalTraces;
        private final long sampledTraces;
        private final long droppedTraces;
        private final long storedTraces;
        private final double samplingRate;
        
        public TracingStats(long totalTraces, long sampledTraces, long droppedTraces, long storedTraces, double samplingRate) {
            this.totalTraces = totalTraces;
            this.sampledTraces = sampledTraces;
            this.droppedTraces = droppedTraces;
            this.storedTraces = storedTraces;
            this.samplingRate = samplingRate;
        }
        
        public long getTotalTraces() { return totalTraces; }
        public long getSampledTraces() { return sampledTraces; }
        public long getDroppedTraces() { return droppedTraces; }
        public long getStoredTraces() { return storedTraces; }
        public double getSamplingRate() { return samplingRate; }
        public double getDropRate() { 
            return sampledTraces > 0 ? (double) droppedTraces / sampledTraces : 0.0; 
        }
        
        @Override
        public String toString() {
            return String.format("TracingStats{total=%d, sampled=%d, dropped=%d, stored=%d, samplingRate=%.2f%%, dropRate=%.2f%%}",
                    totalTraces, sampledTraces, droppedTraces, storedTraces, samplingRate * 100, getDropRate() * 100);
        }
    }
}