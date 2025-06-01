/*
 * 文件名: ActorRecovery.java
 * 用途: Actor恢复管理器
 * 实现内容:
 *   - Actor从快照和事件恢复
 *   - 恢复状态校验和失败处理
 *   - 重放事件和状态重建
 *   - 恢复性能监控和优化
 * 技术选型:
 *   - 事件溯源模式支持状态重建
 *   - 异步恢复避免阻塞
 *   - 恢复策略和错误处理
 * 依赖关系:
 *   - 与ActorPersistence和SnapshotStore协作
 *   - 被Actor系统在启动时使用
 *   - 支持各种恢复策略
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.persistence;

import com.lx.gameserver.frame.actor.core.Actor;
import com.lx.gameserver.frame.actor.core.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actor恢复管理器
 * <p>
 * 负责Actor的状态恢复，包括从快照恢复和事件重放。
 * 提供多种恢复策略和错误处理机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorRecovery {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorRecovery.class);
    
    /** 持久化服务 */
    private final ActorPersistence persistence;
    
    /** 恢复配置 */
    private final RecoveryConfig config;
    
    /** 恢复统计 */
    private final AtomicLong recoveryCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    
    public ActorRecovery(ActorPersistence persistence, RecoveryConfig config) {
        this.persistence = persistence;
        this.config = config;
        logger.info("Actor恢复管理器初始化完成，配置: {}", config);
    }
    
    public ActorRecovery(ActorPersistence persistence) {
        this(persistence, RecoveryConfig.defaultConfig());
    }
    
    /**
     * 恢复Actor状态
     *
     * @param actorPath Actor路径
     * @param actor Actor实例
     * @return 恢复结果
     */
    public CompletableFuture<RecoveryResult> recoverActor(String actorPath, Actor actor) {
        recoveryCount.incrementAndGet();
        Instant startTime = Instant.now();
        
        logger.info("开始恢复Actor: {}", actorPath);
        
        return recoverFromSnapshot(actorPath, actor)
                .thenCompose(snapshotResult -> {
                    if (snapshotResult.isSuccess()) {
                        // 从快照恢复成功，继续重放后续事件
                        return replayEventsAfterSnapshot(actorPath, actor, snapshotResult.getLastSequenceNr());
                    } else {
                        // 快照恢复失败，尝试完整事件重放
                        return replayAllEvents(actorPath, actor);
                    }
                })
                .handle((result, throwable) -> {
                    Duration recoveryTime = Duration.between(startTime, Instant.now());
                    
                    if (throwable != null) {
                        failureCount.incrementAndGet();
                        logger.error("Actor恢复失败: {}, 耗时: {}ms", actorPath, recoveryTime.toMillis(), throwable);
                        return RecoveryResult.failure("恢复失败: " + throwable.getMessage(), throwable);
                    } else if (result.isSuccess()) {
                        successCount.incrementAndGet();
                        logger.info("Actor恢复成功: {}, 序列号: {}, 耗时: {}ms", 
                                actorPath, result.getLastSequenceNr(), recoveryTime.toMillis());
                        return result;
                    } else {
                        failureCount.incrementAndGet();
                        logger.error("Actor恢复失败: {}, 原因: {}, 耗时: {}ms", 
                                actorPath, result.getFailureReason(), recoveryTime.toMillis());
                        return result;
                    }
                });
    }
    
    /**
     * 从快照恢复
     */
    private CompletableFuture<RecoveryResult> recoverFromSnapshot(String actorPath, Actor actor) {
        if (!config.isSnapshotRecoveryEnabled()) {
            return CompletableFuture.completedFuture(RecoveryResult.noSnapshot());
        }
        
        return persistence.loadSnapshot(actorPath)
                .thenApply(snapshotOpt -> {
                    if (snapshotOpt.isPresent()) {
                        ActorPersistence.Snapshot snapshot = snapshotOpt.get();
                        try {
                            // 应用快照到Actor
                            applySnapshot(actor, snapshot);
                            logger.debug("快照恢复成功: {}, 序列号: {}", actorPath, snapshot.getSequenceNr());
                            return RecoveryResult.snapshotRecovered(snapshot.getSequenceNr());
                        } catch (Exception e) {
                            logger.warn("快照应用失败: {}", actorPath, e);
                            return RecoveryResult.failure("快照应用失败", e);
                        }
                    } else {
                        logger.debug("未找到快照: {}", actorPath);
                        return RecoveryResult.noSnapshot();
                    }
                });
    }
    
    /**
     * 重放快照后的事件
     */
    private CompletableFuture<RecoveryResult> replayEventsAfterSnapshot(String actorPath, Actor actor, long fromSequenceNr) {
        return persistence.getHighestSequenceNr(actorPath)
                .thenCompose(highestSeqNr -> {
                    if (highestSeqNr <= fromSequenceNr) {
                        // 没有新事件需要重放
                        return CompletableFuture.completedFuture(RecoveryResult.success(fromSequenceNr));
                    }
                    
                    return replayEvents(actorPath, actor, fromSequenceNr + 1, highestSeqNr);
                });
    }
    
    /**
     * 重放所有事件
     */
    private CompletableFuture<RecoveryResult> replayAllEvents(String actorPath, Actor actor) {
        return persistence.getHighestSequenceNr(actorPath)
                .thenCompose(highestSeqNr -> {
                    if (highestSeqNr == 0) {
                        // 没有事件需要重放
                        return CompletableFuture.completedFuture(RecoveryResult.success(0));
                    }
                    
                    return replayEvents(actorPath, actor, 1, highestSeqNr);
                });
    }
    
    /**
     * 重放指定范围的事件
     */
    private CompletableFuture<RecoveryResult> replayEvents(String actorPath, Actor actor, long fromSeqNr, long toSeqNr) {
        int batchSize = config.getEventReplayBatchSize();
        long currentSeqNr = fromSeqNr;
        
        return replayEventsBatch(actorPath, actor, currentSeqNr, Math.min(currentSeqNr + batchSize - 1, toSeqNr))
                .thenCompose(result -> {
                    if (!result.isSuccess()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    long nextSeqNr = currentSeqNr + batchSize;
                    if (nextSeqNr > toSeqNr) {
                        return CompletableFuture.completedFuture(RecoveryResult.success(toSeqNr));
                    }
                    
                    // 递归处理下一批事件
                    return replayEvents(actorPath, actor, nextSeqNr, toSeqNr);
                });
    }
    
    /**
     * 重放一批事件
     */
    private CompletableFuture<RecoveryResult> replayEventsBatch(String actorPath, Actor actor, long fromSeqNr, long toSeqNr) {
        int maxEvents = config.getEventReplayBatchSize();
        
        return persistence.loadEvents(actorPath, fromSeqNr, toSeqNr, maxEvents)
                .thenApply(events -> {
                    try {
                        for (ActorPersistence.PersistentEvent event : events) {
                            applyEvent(actor, event);
                        }
                        
                        logger.debug("事件重放成功: {}, 范围: {}-{}, 事件数: {}", 
                                actorPath, fromSeqNr, toSeqNr, events.size());
                        return RecoveryResult.success(toSeqNr);
                        
                    } catch (Exception e) {
                        logger.error("事件重放失败: {}, 范围: {}-{}", actorPath, fromSeqNr, toSeqNr, e);
                        return RecoveryResult.failure("事件重放失败", e);
                    }
                });
    }
    
    /**
     * 应用快照到Actor
     */
    private void applySnapshot(Actor actor, ActorPersistence.Snapshot snapshot) {
        // 这里需要Actor实现特定的快照恢复接口
        if (actor instanceof SnapshotRecoverable) {
            ((SnapshotRecoverable) actor).recoverFromSnapshot(snapshot.getData(), snapshot.getSequenceNr());
        } else {
            throw new IllegalArgumentException("Actor不支持快照恢复: " + actor.getClass().getName());
        }
    }
    
    /**
     * 应用事件到Actor
     */
    private void applyEvent(Actor actor, ActorPersistence.PersistentEvent event) {
        // 这里需要Actor实现特定的事件重放接口
        if (actor instanceof EventRecoverable) {
            ((EventRecoverable) actor).recoverFromEvent(event.getEvent(), event.getSequenceNr());
        } else {
            throw new IllegalArgumentException("Actor不支持事件恢复: " + actor.getClass().getName());
        }
    }
    
    /**
     * 获取恢复统计信息
     */
    public RecoveryStats getStats() {
        return new RecoveryStats(
                recoveryCount.get(),
                successCount.get(),
                failureCount.get()
        );
    }
    
    /**
     * 快照恢复接口
     */
    public interface SnapshotRecoverable {
        /**
         * 从快照恢复状态
         *
         * @param snapshot 快照数据
         * @param sequenceNr 序列号
         */
        void recoverFromSnapshot(Object snapshot, long sequenceNr);
    }
    
    /**
     * 事件恢复接口
     */
    public interface EventRecoverable {
        /**
         * 从事件恢复状态
         *
         * @param event 事件数据
         * @param sequenceNr 序列号
         */
        void recoverFromEvent(Object event, long sequenceNr);
    }
    
    /**
     * 恢复结果
     */
    public static class RecoveryResult {
        private final boolean success;
        private final String message;
        private final Throwable error;
        private final long lastSequenceNr;
        private final RecoveryMode mode;
        
        private RecoveryResult(boolean success, String message, Throwable error, long lastSequenceNr, RecoveryMode mode) {
            this.success = success;
            this.message = message;
            this.error = error;
            this.lastSequenceNr = lastSequenceNr;
            this.mode = mode;
        }
        
        public static RecoveryResult success(long sequenceNr) {
            return new RecoveryResult(true, "恢复成功", null, sequenceNr, RecoveryMode.EVENT_REPLAY);
        }
        
        public static RecoveryResult snapshotRecovered(long sequenceNr) {
            return new RecoveryResult(true, "快照恢复成功", null, sequenceNr, RecoveryMode.SNAPSHOT);
        }
        
        public static RecoveryResult noSnapshot() {
            return new RecoveryResult(false, "无快照可用", null, 0, RecoveryMode.NONE);
        }
        
        public static RecoveryResult failure(String message, Throwable error) {
            return new RecoveryResult(false, message, error, -1, RecoveryMode.FAILED);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getFailureReason() { return success ? null : message; }
        public Throwable getError() { return error; }
        public long getLastSequenceNr() { return lastSequenceNr; }
        public RecoveryMode getMode() { return mode; }
        
        @Override
        public String toString() {
            return String.format("RecoveryResult{success=%s, mode=%s, sequenceNr=%d, message=%s}",
                    success, mode, lastSequenceNr, message);
        }
    }
    
    /**
     * 恢复模式
     */
    public enum RecoveryMode {
        /** 无恢复 */
        NONE,
        /** 快照恢复 */
        SNAPSHOT,
        /** 事件重放 */
        EVENT_REPLAY,
        /** 失败 */
        FAILED
    }
    
    /**
     * 恢复配置
     */
    public static class RecoveryConfig {
        private final boolean snapshotRecoveryEnabled;
        private final boolean eventReplayEnabled;
        private final int eventReplayBatchSize;
        private final int maxRecoveryTimeSeconds;
        private final boolean failOnRecoveryError;
        
        public RecoveryConfig(boolean snapshotRecoveryEnabled, boolean eventReplayEnabled,
                            int eventReplayBatchSize, int maxRecoveryTimeSeconds, boolean failOnRecoveryError) {
            this.snapshotRecoveryEnabled = snapshotRecoveryEnabled;
            this.eventReplayEnabled = eventReplayEnabled;
            this.eventReplayBatchSize = eventReplayBatchSize;
            this.maxRecoveryTimeSeconds = maxRecoveryTimeSeconds;
            this.failOnRecoveryError = failOnRecoveryError;
        }
        
        public static RecoveryConfig defaultConfig() {
            return new RecoveryConfig(true, true, 100, 60, true);
        }
        
        public boolean isSnapshotRecoveryEnabled() { return snapshotRecoveryEnabled; }
        public boolean isEventReplayEnabled() { return eventReplayEnabled; }
        public int getEventReplayBatchSize() { return eventReplayBatchSize; }
        public int getMaxRecoveryTimeSeconds() { return maxRecoveryTimeSeconds; }
        public boolean isFailOnRecoveryError() { return failOnRecoveryError; }
        
        @Override
        public String toString() {
            return String.format("RecoveryConfig{snapshot=%s, eventReplay=%s, batchSize=%d, maxTime=%ds, failOnError=%s}",
                    snapshotRecoveryEnabled, eventReplayEnabled, eventReplayBatchSize, maxRecoveryTimeSeconds, failOnRecoveryError);
        }
    }
    
    /**
     * 恢复统计信息
     */
    public static class RecoveryStats {
        private final long totalRecoveries;
        private final long successfulRecoveries;
        private final long failedRecoveries;
        
        public RecoveryStats(long totalRecoveries, long successfulRecoveries, long failedRecoveries) {
            this.totalRecoveries = totalRecoveries;
            this.successfulRecoveries = successfulRecoveries;
            this.failedRecoveries = failedRecoveries;
        }
        
        public long getTotalRecoveries() { return totalRecoveries; }
        public long getSuccessfulRecoveries() { return successfulRecoveries; }
        public long getFailedRecoveries() { return failedRecoveries; }
        public double getSuccessRate() { 
            return totalRecoveries > 0 ? (double) successfulRecoveries / totalRecoveries : 0.0; 
        }
        
        @Override
        public String toString() {
            return String.format("RecoveryStats{total=%d, success=%d, failed=%d, successRate=%.2f%%}",
                    totalRecoveries, successfulRecoveries, failedRecoveries, getSuccessRate() * 100);
        }
    }
}