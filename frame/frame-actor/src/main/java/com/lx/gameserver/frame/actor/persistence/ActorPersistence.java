/*
 * 文件名: ActorPersistence.java
 * 用途: Actor持久化接口
 * 实现内容:
 *   - Actor状态持久化和恢复接口
 *   - 快照机制和事件溯源支持
 *   - 持久化策略配置和恢复机制
 *   - 异步持久化操作和错误处理
 * 技术选型:
 *   - 接口设计支持多种持久化后端
 *   - CompletableFuture提供异步操作
 *   - 策略模式支持不同持久化策略
 * 依赖关系:
 *   - 与SnapshotStore和ActorRecovery协作
 *   - 被持久化Actor实现使用
 *   - 支持事件溯源和状态恢复
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.persistence;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Actor持久化接口
 * <p>
 * 定义Actor状态持久化和恢复的基本操作，支持快照和事件溯源机制。
 * 提供异步操作以避免阻塞Actor处理流程。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface ActorPersistence {
    
    /**
     * 持久化Actor快照
     *
     * @param actorPath Actor路径
     * @param snapshot 快照数据
     * @param sequenceNr 序列号
     * @return 持久化操作结果
     */
    CompletableFuture<PersistenceResult> saveSnapshot(String actorPath, Object snapshot, long sequenceNr);
    
    /**
     * 加载Actor快照
     *
     * @param actorPath Actor路径
     * @return 快照数据
     */
    CompletableFuture<Optional<Snapshot>> loadSnapshot(String actorPath);
    
    /**
     * 删除Actor快照
     *
     * @param actorPath Actor路径
     * @param criteria 删除条件
     * @return 删除操作结果
     */
    CompletableFuture<PersistenceResult> deleteSnapshot(String actorPath, SnapshotSelectionCriteria criteria);
    
    /**
     * 持久化事件
     *
     * @param actorPath Actor路径
     * @param events 事件列表
     * @param expectedSequenceNr 期望的序列号
     * @return 持久化操作结果
     */
    CompletableFuture<PersistenceResult> persistEvents(String actorPath, List<Object> events, long expectedSequenceNr);
    
    /**
     * 加载事件
     *
     * @param actorPath Actor路径
     * @param fromSequenceNr 起始序列号
     * @param toSequenceNr 结束序列号
     * @param maxEvents 最大事件数
     * @return 事件列表
     */
    CompletableFuture<List<PersistentEvent>> loadEvents(String actorPath, long fromSequenceNr, long toSequenceNr, int maxEvents);
    
    /**
     * 删除事件
     *
     * @param actorPath Actor路径
     * @param toSequenceNr 删除到指定序列号
     * @return 删除操作结果
     */
    CompletableFuture<PersistenceResult> deleteEvents(String actorPath, long toSequenceNr);
    
    /**
     * 获取最高序列号
     *
     * @param actorPath Actor路径
     * @return 最高序列号
     */
    CompletableFuture<Long> getHighestSequenceNr(String actorPath);
    
    /**
     * 快照数据类
     */
    class Snapshot {
        private final String actorPath;
        private final Object data;
        private final long sequenceNr;
        private final Instant timestamp;
        
        public Snapshot(String actorPath, Object data, long sequenceNr, Instant timestamp) {
            this.actorPath = actorPath;
            this.data = data;
            this.sequenceNr = sequenceNr;
            this.timestamp = timestamp;
        }
        
        public String getActorPath() { return actorPath; }
        public Object getData() { return data; }
        public long getSequenceNr() { return sequenceNr; }
        public Instant getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("Snapshot{actor=%s, sequenceNr=%d, timestamp=%s}", 
                    actorPath, sequenceNr, timestamp);
        }
    }
    
    /**
     * 持久化事件类
     */
    class PersistentEvent {
        private final String actorPath;
        private final Object event;
        private final long sequenceNr;
        private final Instant timestamp;
        private final String eventType;
        
        public PersistentEvent(String actorPath, Object event, long sequenceNr, Instant timestamp) {
            this.actorPath = actorPath;
            this.event = event;
            this.sequenceNr = sequenceNr;
            this.timestamp = timestamp;
            this.eventType = event.getClass().getSimpleName();
        }
        
        public String getActorPath() { return actorPath; }
        public Object getEvent() { return event; }
        public long getSequenceNr() { return sequenceNr; }
        public Instant getTimestamp() { return timestamp; }
        public String getEventType() { return eventType; }
        
        @Override
        public String toString() {
            return String.format("PersistentEvent{actor=%s, type=%s, sequenceNr=%d, timestamp=%s}", 
                    actorPath, eventType, sequenceNr, timestamp);
        }
    }
    
    /**
     * 快照选择条件
     */
    class SnapshotSelectionCriteria {
        private final long maxSequenceNr;
        private final Instant maxTimestamp;
        private final long minSequenceNr;
        private final Instant minTimestamp;
        
        public SnapshotSelectionCriteria(long maxSequenceNr, Instant maxTimestamp, 
                                       long minSequenceNr, Instant minTimestamp) {
            this.maxSequenceNr = maxSequenceNr;
            this.maxTimestamp = maxTimestamp;
            this.minSequenceNr = minSequenceNr;
            this.minTimestamp = minTimestamp;
        }
        
        public static SnapshotSelectionCriteria latest() {
            return new SnapshotSelectionCriteria(Long.MAX_VALUE, Instant.MAX, 0L, Instant.MIN);
        }
        
        public static SnapshotSelectionCriteria beforeSequenceNr(long sequenceNr) {
            return new SnapshotSelectionCriteria(sequenceNr - 1, Instant.MAX, 0L, Instant.MIN);
        }
        
        public static SnapshotSelectionCriteria beforeTimestamp(Instant timestamp) {
            return new SnapshotSelectionCriteria(Long.MAX_VALUE, timestamp, 0L, Instant.MIN);
        }
        
        public long getMaxSequenceNr() { return maxSequenceNr; }
        public Instant getMaxTimestamp() { return maxTimestamp; }
        public long getMinSequenceNr() { return minSequenceNr; }
        public Instant getMinTimestamp() { return minTimestamp; }
        
        public boolean matches(Snapshot snapshot) {
            return snapshot.getSequenceNr() >= minSequenceNr && 
                   snapshot.getSequenceNr() <= maxSequenceNr &&
                   !snapshot.getTimestamp().isBefore(minTimestamp) &&
                   !snapshot.getTimestamp().isAfter(maxTimestamp);
        }
    }
    
    /**
     * 持久化操作结果
     */
    class PersistenceResult {
        private final boolean success;
        private final String message;
        private final Throwable error;
        private final long sequenceNr;
        
        private PersistenceResult(boolean success, String message, Throwable error, long sequenceNr) {
            this.success = success;
            this.message = message;
            this.error = error;
            this.sequenceNr = sequenceNr;
        }
        
        public static PersistenceResult success(long sequenceNr) {
            return new PersistenceResult(true, "操作成功", null, sequenceNr);
        }
        
        public static PersistenceResult success(String message, long sequenceNr) {
            return new PersistenceResult(true, message, null, sequenceNr);
        }
        
        public static PersistenceResult failure(String message, Throwable error) {
            return new PersistenceResult(false, message, error, -1L);
        }
        
        public static PersistenceResult failure(Throwable error) {
            return new PersistenceResult(false, error.getMessage(), error, -1L);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isFailure() { return !success; }
        public String getMessage() { return message; }
        public Throwable getError() { return error; }
        public long getSequenceNr() { return sequenceNr; }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("PersistenceResult{success=true, sequenceNr=%d, message=%s}", 
                        sequenceNr, message);
            } else {
                return String.format("PersistenceResult{success=false, message=%s, error=%s}", 
                        message, error != null ? error.getClass().getSimpleName() : "null");
            }
        }
    }
    
    /**
     * 持久化配置
     */
    class PersistenceConfig {
        private final boolean snapshotEnabled;
        private final int snapshotEvery;
        private final int maxSnapshotsToKeep;
        private final boolean deleteEventsOnSnapshot;
        private final int maxEventsToKeep;
        private final boolean asyncPersistence;
        private final int persistenceTimeoutSeconds;
        
        public PersistenceConfig(boolean snapshotEnabled, int snapshotEvery, int maxSnapshotsToKeep,
                               boolean deleteEventsOnSnapshot, int maxEventsToKeep,
                               boolean asyncPersistence, int persistenceTimeoutSeconds) {
            this.snapshotEnabled = snapshotEnabled;
            this.snapshotEvery = snapshotEvery;
            this.maxSnapshotsToKeep = maxSnapshotsToKeep;
            this.deleteEventsOnSnapshot = deleteEventsOnSnapshot;
            this.maxEventsToKeep = maxEventsToKeep;
            this.asyncPersistence = asyncPersistence;
            this.persistenceTimeoutSeconds = persistenceTimeoutSeconds;
        }
        
        public static PersistenceConfig defaultConfig() {
            return new PersistenceConfig(true, 100, 5, false, 1000, true, 30);
        }
        
        public boolean isSnapshotEnabled() { return snapshotEnabled; }
        public int getSnapshotEvery() { return snapshotEvery; }
        public int getMaxSnapshotsToKeep() { return maxSnapshotsToKeep; }
        public boolean isDeleteEventsOnSnapshot() { return deleteEventsOnSnapshot; }
        public int getMaxEventsToKeep() { return maxEventsToKeep; }
        public boolean isAsyncPersistence() { return asyncPersistence; }
        public int getPersistenceTimeoutSeconds() { return persistenceTimeoutSeconds; }
    }
}