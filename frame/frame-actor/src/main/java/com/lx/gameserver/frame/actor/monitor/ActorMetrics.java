/*
 * 文件名: ActorMetrics.java
 * 用途: Actor指标收集器
 * 实现内容:
 *   - Actor指标收集和统计
 *   - 消息处理速率和邮箱队列长度监控
 *   - 处理延迟统计和错误率统计
 *   - 内存使用和性能指标收集
 * 技术选型:
 *   - 无锁原子操作保证高性能
 *   - 滑动窗口统计和实时指标
 *   - 内存高效的指标存储
 * 依赖关系:
 *   - 被Actor和ActorSystem使用
 *   - 与监控系统集成
 *   - 支持指标导出和查询
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Actor指标收集器
 * <p>
 * 收集和统计Actor系统的各种性能指标，包括消息处理、
 * 邮箱队列、延迟、错误率等关键指标。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorMetrics.class);
    
    /** 单例实例 */
    private static final ActorMetrics INSTANCE = new ActorMetrics();
    
    /** Actor指标映射 */
    private final ConcurrentHashMap<String, ActorMetric> actorMetrics = new ConcurrentHashMap<>();
    
    /** 系统级指标 */
    private final SystemMetrics systemMetrics = new SystemMetrics();
    
    /** 指标收集开始时间 */
    private final Instant startTime = Instant.now();
    
    private ActorMetrics() {
        logger.info("Actor指标收集器初始化完成");
    }
    
    /**
     * 获取单例实例
     */
    public static ActorMetrics getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取Actor指标
     */
    public ActorMetric getActorMetric(String actorPath) {
        return actorMetrics.computeIfAbsent(actorPath, ActorMetric::new);
    }
    
    /**
     * 获取系统指标
     */
    public SystemMetrics getSystemMetrics() {
        return systemMetrics;
    }
    
    /**
     * 记录消息发送
     */
    public void recordMessageSent(String senderPath, String receiverPath, String messageType) {
        getActorMetric(senderPath).recordMessageSent(messageType);
        getActorMetric(receiverPath).recordMessageReceived(messageType);
        systemMetrics.recordMessageSent();
    }
    
    /**
     * 记录消息处理
     */
    public void recordMessageProcessed(String actorPath, String messageType, Duration processingTime) {
        getActorMetric(actorPath).recordMessageProcessed(messageType, processingTime);
        systemMetrics.recordMessageProcessed(processingTime);
    }
    
    /**
     * 记录消息处理错误
     */
    public void recordMessageError(String actorPath, String messageType, Throwable error) {
        getActorMetric(actorPath).recordMessageError(messageType, error);
        systemMetrics.recordMessageError();
    }
    
    /**
     * 记录邮箱队列长度
     */
    public void recordMailboxSize(String actorPath, int size) {
        getActorMetric(actorPath).recordMailboxSize(size);
        systemMetrics.recordMailboxSize(size);
    }
    
    /**
     * 记录Actor创建
     */
    public void recordActorCreated(String actorPath) {
        systemMetrics.recordActorCreated();
        logger.debug("Actor创建: {}", actorPath);
    }
    
    /**
     * 记录Actor停止
     */
    public void recordActorStopped(String actorPath) {
        systemMetrics.recordActorStopped();
        logger.debug("Actor停止: {}", actorPath);
    }
    
    /**
     * 清理Actor指标
     */
    public void cleanupActorMetrics(String actorPath) {
        actorMetrics.remove(actorPath);
    }
    
    /**
     * 获取所有Actor指标快照
     */
    public ConcurrentHashMap<String, ActorMetric> getAllActorMetrics() {
        return new ConcurrentHashMap<>(actorMetrics);
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        actorMetrics.clear();
        systemMetrics.reset();
        logger.info("Actor指标已重置");
    }
    
    /**
     * Actor指标类
     */
    public static class ActorMetric {
        private final String actorPath;
        private final Instant createdTime = Instant.now();
        
        // 消息统计
        private final LongAdder messagesSent = new LongAdder();
        private final LongAdder messagesReceived = new LongAdder();
        private final LongAdder messagesProcessed = new LongAdder();
        private final LongAdder messageErrors = new LongAdder();
        
        // 处理时间统计
        private final LongAdder totalProcessingTime = new LongAdder();
        private final LongAccumulator maxProcessingTime = new LongAccumulator(Long::max, 0L);
        private final LongAccumulator minProcessingTime = new LongAccumulator(Long::min, Long.MAX_VALUE);
        
        // 邮箱统计
        private final AtomicLong currentMailboxSize = new AtomicLong(0);
        private final LongAccumulator maxMailboxSize = new LongAccumulator(Long::max, 0L);
        
        // 消息类型统计
        private final ConcurrentHashMap<String, MessageTypeMetric> messageTypeMetrics = new ConcurrentHashMap<>();
        
        public ActorMetric(String actorPath) {
            this.actorPath = actorPath;
        }
        
        /**
         * 记录消息发送
         */
        public void recordMessageSent(String messageType) {
            messagesSent.increment();
            getMessageTypeMetric(messageType).recordSent();
        }
        
        /**
         * 记录消息接收
         */
        public void recordMessageReceived(String messageType) {
            messagesReceived.increment();
            getMessageTypeMetric(messageType).recordReceived();
        }
        
        /**
         * 记录消息处理
         */
        public void recordMessageProcessed(String messageType, Duration processingTime) {
            messagesProcessed.increment();
            
            long timeMillis = processingTime.toMillis();
            totalProcessingTime.add(timeMillis);
            maxProcessingTime.accumulate(timeMillis);
            minProcessingTime.accumulate(timeMillis);
            
            getMessageTypeMetric(messageType).recordProcessed(processingTime);
        }
        
        /**
         * 记录消息错误
         */
        public void recordMessageError(String messageType, Throwable error) {
            messageErrors.increment();
            getMessageTypeMetric(messageType).recordError(error);
        }
        
        /**
         * 记录邮箱大小
         */
        public void recordMailboxSize(int size) {
            currentMailboxSize.set(size);
            maxMailboxSize.accumulate(size);
        }
        
        /**
         * 获取消息类型指标
         */
        private MessageTypeMetric getMessageTypeMetric(String messageType) {
            return messageTypeMetrics.computeIfAbsent(messageType, MessageTypeMetric::new);
        }
        
        // Getter methods
        public String getActorPath() { return actorPath; }
        public Instant getCreatedTime() { return createdTime; }
        public long getMessagesSent() { return messagesSent.sum(); }
        public long getMessagesReceived() { return messagesReceived.sum(); }
        public long getMessagesProcessed() { return messagesProcessed.sum(); }
        public long getMessageErrors() { return messageErrors.sum(); }
        public long getCurrentMailboxSize() { return currentMailboxSize.get(); }
        public long getMaxMailboxSize() { return maxMailboxSize.get(); }
        
        public double getErrorRate() {
            long processed = messagesProcessed.sum();
            return processed > 0 ? (double) messageErrors.sum() / processed : 0.0;
        }
        
        public double getAverageProcessingTime() {
            long processed = messagesProcessed.sum();
            return processed > 0 ? (double) totalProcessingTime.sum() / processed : 0.0;
        }
        
        public long getMaxProcessingTime() { return maxProcessingTime.get(); }
        public long getMinProcessingTime() { 
            long min = minProcessingTime.get();
            return min == Long.MAX_VALUE ? 0L : min;
        }
        
        public ConcurrentHashMap<String, MessageTypeMetric> getMessageTypeMetrics() {
            return new ConcurrentHashMap<>(messageTypeMetrics);
        }
        
        @Override
        public String toString() {
            return String.format("ActorMetric{path=%s, sent=%d, received=%d, processed=%d, errors=%d, errorRate=%.2f%%, avgTime=%.2fms}",
                    actorPath, getMessagesSent(), getMessagesReceived(), getMessagesProcessed(), 
                    getMessageErrors(), getErrorRate() * 100, getAverageProcessingTime());
        }
    }
    
    /**
     * 消息类型指标类
     */
    public static class MessageTypeMetric {
        private final String messageType;
        private final LongAdder sent = new LongAdder();
        private final LongAdder received = new LongAdder();
        private final LongAdder processed = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalProcessingTime = new LongAdder();
        
        public MessageTypeMetric(String messageType) {
            this.messageType = messageType;
        }
        
        public void recordSent() { sent.increment(); }
        public void recordReceived() { received.increment(); }
        public void recordProcessed(Duration processingTime) {
            processed.increment();
            totalProcessingTime.add(processingTime.toMillis());
        }
        public void recordError(Throwable error) { errors.increment(); }
        
        public String getMessageType() { return messageType; }
        public long getSent() { return sent.sum(); }
        public long getReceived() { return received.sum(); }
        public long getProcessed() { return processed.sum(); }
        public long getErrors() { return errors.sum(); }
        public double getErrorRate() {
            long proc = processed.sum();
            return proc > 0 ? (double) errors.sum() / proc : 0.0;
        }
        public double getAverageProcessingTime() {
            long proc = processed.sum();
            return proc > 0 ? (double) totalProcessingTime.sum() / proc : 0.0;
        }
    }
    
    /**
     * 系统级指标类
     */
    public static class SystemMetrics {
        private final LongAdder totalMessagesSent = new LongAdder();
        private final LongAdder totalMessagesProcessed = new LongAdder();
        private final LongAdder totalMessageErrors = new LongAdder();
        private final LongAdder totalProcessingTime = new LongAdder();
        
        private final AtomicLong totalActorsCreated = new AtomicLong(0);
        private final AtomicLong totalActorsStopped = new AtomicLong(0);
        private final AtomicLong currentActorCount = new AtomicLong(0);
        
        private final LongAdder totalMailboxSizes = new LongAdder();
        private final LongAdder mailboxSamples = new LongAdder();
        private final LongAccumulator maxMailboxSize = new LongAccumulator(Long::max, 0L);
        
        public void recordMessageSent() {
            totalMessagesSent.increment();
        }
        
        public void recordMessageProcessed(Duration processingTime) {
            totalMessagesProcessed.increment();
            totalProcessingTime.add(processingTime.toMillis());
        }
        
        public void recordMessageError() {
            totalMessageErrors.increment();
        }
        
        public void recordActorCreated() {
            totalActorsCreated.incrementAndGet();
            currentActorCount.incrementAndGet();
        }
        
        public void recordActorStopped() {
            totalActorsStopped.incrementAndGet();
            currentActorCount.decrementAndGet();
        }
        
        public void recordMailboxSize(int size) {
            totalMailboxSizes.add(size);
            mailboxSamples.increment();
            maxMailboxSize.accumulate(size);
        }
        
        public void reset() {
            totalMessagesSent.reset();
            totalMessagesProcessed.reset();
            totalMessageErrors.reset();
            totalProcessingTime.reset();
            totalMailboxSizes.reset();
            mailboxSamples.reset();
            maxMailboxSize.reset();
        }
        
        // Getter methods
        public long getTotalMessagesSent() { return totalMessagesSent.sum(); }
        public long getTotalMessagesProcessed() { return totalMessagesProcessed.sum(); }
        public long getTotalMessageErrors() { return totalMessageErrors.sum(); }
        public long getCurrentActorCount() { return currentActorCount.get(); }
        public long getTotalActorsCreated() { return totalActorsCreated.get(); }
        public long getTotalActorsStopped() { return totalActorsStopped.get(); }
        public long getMaxMailboxSize() { return maxMailboxSize.get(); }
        
        public double getSystemErrorRate() {
            long processed = totalMessagesProcessed.sum();
            return processed > 0 ? (double) totalMessageErrors.sum() / processed : 0.0;
        }
        
        public double getSystemAverageProcessingTime() {
            long processed = totalMessagesProcessed.sum();
            return processed > 0 ? (double) totalProcessingTime.sum() / processed : 0.0;
        }
        
        public double getAverageMailboxSize() {
            long samples = mailboxSamples.sum();
            return samples > 0 ? (double) totalMailboxSizes.sum() / samples : 0.0;
        }
        
        public double getMessageThroughput(Duration timeWindow) {
            return (double) totalMessagesProcessed.sum() / timeWindow.toSeconds();
        }
        
        @Override
        public String toString() {
            return String.format("SystemMetrics{actors=%d, sent=%d, processed=%d, errors=%d, errorRate=%.2f%%, avgTime=%.2fms, throughput=%.1f/s}",
                    getCurrentActorCount(), getTotalMessagesSent(), getTotalMessagesProcessed(),
                    getTotalMessageErrors(), getSystemErrorRate() * 100, getSystemAverageProcessingTime(),
                    getMessageThroughput(Duration.ofSeconds(1)));
        }
    }
}