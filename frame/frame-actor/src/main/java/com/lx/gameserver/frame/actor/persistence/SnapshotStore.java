/*
 * 文件名: SnapshotStore.java
 * 用途: 快照存储接口和实现
 * 实现内容:
 *   - 快照存储接口定义
 *   - 支持多种存储后端（内存、Redis、数据库）
 *   - 快照版本管理和压缩支持
 *   - 异步存储和错误处理
 * 技术选型:
 *   - 接口设计支持多种实现
 *   - 内存存储用于测试和开发
 *   - 支持数据压缩减少存储空间
 * 依赖关系:
 *   - 被ActorPersistence使用
 *   - 支持不同的存储后端
 *   - 与序列化组件集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 快照存储接口
 * <p>
 * 定义快照存储的基本操作，支持多种存储后端实现。
 * 提供版本管理、压缩、异步操作等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface SnapshotStore {
    
    /**
     * 保存快照
     *
     * @param snapshot 快照数据
     * @return 保存操作结果
     */
    CompletableFuture<StoreResult> saveSnapshot(ActorPersistence.Snapshot snapshot);
    
    /**
     * 加载快照
     *
     * @param actorPath Actor路径
     * @param criteria 选择条件
     * @return 快照数据
     */
    CompletableFuture<Optional<ActorPersistence.Snapshot>> loadSnapshot(String actorPath, 
                                                                       ActorPersistence.SnapshotSelectionCriteria criteria);
    
    /**
     * 删除快照
     *
     * @param actorPath Actor路径
     * @param criteria 删除条件
     * @return 删除操作结果
     */
    CompletableFuture<StoreResult> deleteSnapshot(String actorPath, ActorPersistence.SnapshotSelectionCriteria criteria);
    
    /**
     * 列出快照
     *
     * @param actorPath Actor路径
     * @param criteria 选择条件
     * @return 快照元数据列表
     */
    CompletableFuture<List<SnapshotMetadata>> listSnapshots(String actorPath, 
                                                           ActorPersistence.SnapshotSelectionCriteria criteria);
    
    /**
     * 关闭存储
     */
    void close();
    
    /**
     * 存储操作结果
     */
    class StoreResult {
        private final boolean success;
        private final String message;
        private final Throwable error;
        
        private StoreResult(boolean success, String message, Throwable error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }
        
        public static StoreResult success() {
            return new StoreResult(true, "操作成功", null);
        }
        
        public static StoreResult success(String message) {
            return new StoreResult(true, message, null);
        }
        
        public static StoreResult failure(String message) {
            return new StoreResult(false, message, null);
        }
        
        public static StoreResult failure(Throwable error) {
            return new StoreResult(false, error.getMessage(), error);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isFailure() { return !success; }
        public String getMessage() { return message; }
        public Throwable getError() { return error; }
    }
    
    /**
     * 快照元数据
     */
    class SnapshotMetadata {
        private final String actorPath;
        private final long sequenceNr;
        private final Instant timestamp;
        private final long size;
        private final boolean compressed;
        
        public SnapshotMetadata(String actorPath, long sequenceNr, Instant timestamp, long size, boolean compressed) {
            this.actorPath = actorPath;
            this.sequenceNr = sequenceNr;
            this.timestamp = timestamp;
            this.size = size;
            this.compressed = compressed;
        }
        
        public String getActorPath() { return actorPath; }
        public long getSequenceNr() { return sequenceNr; }
        public Instant getTimestamp() { return timestamp; }
        public long getSize() { return size; }
        public boolean isCompressed() { return compressed; }
        
        @Override
        public String toString() {
            return String.format("SnapshotMetadata{actor=%s, sequenceNr=%d, timestamp=%s, size=%d, compressed=%s}",
                    actorPath, sequenceNr, timestamp, size, compressed);
        }
    }
}

/**
 * 内存快照存储实现
 * <p>
 * 基于内存的快照存储实现，适用于测试和开发环境。
 * 支持压缩和版本管理功能。
 * </p>
 */
class InMemorySnapshotStore implements SnapshotStore {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemorySnapshotStore.class);
    
    /** 快照存储 */
    private final Map<String, List<StoredSnapshot>> snapshots = new ConcurrentHashMap<>();
    
    /** 是否启用压缩 */
    private final boolean compressionEnabled;
    
    /** 最大保留快照数 */
    private final int maxSnapshotsPerActor;
    
    /** 统计信息 */
    private final AtomicLong totalSnapshots = new AtomicLong(0);
    private final AtomicLong totalSize = new AtomicLong(0);
    
    public InMemorySnapshotStore(boolean compressionEnabled, int maxSnapshotsPerActor) {
        this.compressionEnabled = compressionEnabled;
        this.maxSnapshotsPerActor = maxSnapshotsPerActor;
        logger.info("内存快照存储初始化完成，压缩: {}, 最大快照数: {}", compressionEnabled, maxSnapshotsPerActor);
    }
    
    public InMemorySnapshotStore() {
        this(true, 10);
    }
    
    @Override
    public CompletableFuture<StoreResult> saveSnapshot(ActorPersistence.Snapshot snapshot) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = serializeSnapshot(snapshot.getData());
                if (compressionEnabled) {
                    data = compress(data);
                }
                
                StoredSnapshot storedSnapshot = new StoredSnapshot(
                        snapshot.getActorPath(),
                        snapshot.getSequenceNr(),
                        snapshot.getTimestamp(),
                        data,
                        compressionEnabled
                );
                
                snapshots.computeIfAbsent(snapshot.getActorPath(), k -> new ArrayList<>())
                         .add(storedSnapshot);
                
                // 清理旧快照
                cleanupOldSnapshots(snapshot.getActorPath());
                
                totalSnapshots.incrementAndGet();
                totalSize.addAndGet(data.length);
                
                logger.debug("快照保存成功: {}, 大小: {} bytes", snapshot.getActorPath(), data.length);
                return StoreResult.success();
                
            } catch (Exception e) {
                logger.error("快照保存失败: {}", snapshot.getActorPath(), e);
                return StoreResult.failure(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<ActorPersistence.Snapshot>> loadSnapshot(String actorPath, 
                                                                              ActorPersistence.SnapshotSelectionCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<StoredSnapshot> actorSnapshots = snapshots.get(actorPath);
                if (actorSnapshots == null || actorSnapshots.isEmpty()) {
                    return Optional.empty();
                }
                
                Optional<StoredSnapshot> matchingSnapshot = actorSnapshots.stream()
                        .filter(s -> criteria.matches(new ActorPersistence.Snapshot(s.actorPath, null, s.sequenceNr, s.timestamp)))
                        .max(Comparator.comparing(s -> s.sequenceNr));
                
                if (matchingSnapshot.isPresent()) {
                    StoredSnapshot stored = matchingSnapshot.get();
                    byte[] data = stored.data;
                    
                    if (stored.compressed) {
                        data = decompress(data);
                    }
                    
                    Object snapshotData = deserializeSnapshot(data);
                    ActorPersistence.Snapshot snapshot = new ActorPersistence.Snapshot(
                            stored.actorPath, snapshotData, stored.sequenceNr, stored.timestamp);
                    
                    logger.debug("快照加载成功: {}, 序列号: {}", actorPath, stored.sequenceNr);
                    return Optional.of(snapshot);
                }
                
                return Optional.empty();
                
            } catch (Exception e) {
                logger.error("快照加载失败: {}", actorPath, e);
                return Optional.empty();
            }
        });
    }
    
    @Override
    public CompletableFuture<StoreResult> deleteSnapshot(String actorPath, ActorPersistence.SnapshotSelectionCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<StoredSnapshot> actorSnapshots = snapshots.get(actorPath);
                if (actorSnapshots == null) {
                    return StoreResult.success("无快照需要删除");
                }
                
                int beforeSize = actorSnapshots.size();
                actorSnapshots.removeIf(s -> criteria.matches(
                        new ActorPersistence.Snapshot(s.actorPath, null, s.sequenceNr, s.timestamp)));
                
                int deletedCount = beforeSize - actorSnapshots.size();
                if (actorSnapshots.isEmpty()) {
                    snapshots.remove(actorPath);
                }
                
                logger.debug("删除快照: {}, 删除数量: {}", actorPath, deletedCount);
                return StoreResult.success("删除了 " + deletedCount + " 个快照");
                
            } catch (Exception e) {
                logger.error("删除快照失败: {}", actorPath, e);
                return StoreResult.failure(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<SnapshotMetadata>> listSnapshots(String actorPath, 
                                                                  ActorPersistence.SnapshotSelectionCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            List<StoredSnapshot> actorSnapshots = snapshots.get(actorPath);
            if (actorSnapshots == null) {
                return new ArrayList<>();
            }
            
            return actorSnapshots.stream()
                    .filter(s -> criteria.matches(new ActorPersistence.Snapshot(s.actorPath, null, s.sequenceNr, s.timestamp)))
                    .map(s -> new SnapshotMetadata(s.actorPath, s.sequenceNr, s.timestamp, s.data.length, s.compressed))
                    .sorted(Comparator.comparing(SnapshotMetadata::getSequenceNr))
                    .toList();
        });
    }
    
    @Override
    public void close() {
        logger.info("关闭内存快照存储，总快照数: {}, 总大小: {} bytes", totalSnapshots.get(), totalSize.get());
        snapshots.clear();
    }
    
    /**
     * 清理旧快照
     */
    private void cleanupOldSnapshots(String actorPath) {
        List<StoredSnapshot> actorSnapshots = snapshots.get(actorPath);
        if (actorSnapshots != null && actorSnapshots.size() > maxSnapshotsPerActor) {
            actorSnapshots.sort(Comparator.comparing(s -> s.sequenceNr));
            int toRemove = actorSnapshots.size() - maxSnapshotsPerActor;
            for (int i = 0; i < toRemove; i++) {
                actorSnapshots.remove(0);
            }
            logger.debug("清理旧快照: {}, 清理数量: {}", actorPath, toRemove);
        }
    }
    
    /**
     * 序列化快照数据
     */
    private byte[] serializeSnapshot(Object data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
            return baos.toByteArray();
        }
    }
    
    /**
     * 反序列化快照数据
     */
    private Object deserializeSnapshot(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }
    
    /**
     * 压缩数据
     */
    private byte[] compress(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();
        }
    }
    
    /**
     * 解压数据
     */
    private byte[] decompress(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
    
    /**
     * 存储的快照数据
     */
    private static class StoredSnapshot {
        final String actorPath;
        final long sequenceNr;
        final Instant timestamp;
        final byte[] data;
        final boolean compressed;
        
        StoredSnapshot(String actorPath, long sequenceNr, Instant timestamp, byte[] data, boolean compressed) {
            this.actorPath = actorPath;
            this.sequenceNr = sequenceNr;
            this.timestamp = timestamp;
            this.data = data;
            this.compressed = compressed;
        }
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSnapshots", totalSnapshots.get());
        stats.put("totalSize", totalSize.get());
        stats.put("actorCount", snapshots.size());
        stats.put("compressionEnabled", compressionEnabled);
        stats.put("maxSnapshotsPerActor", maxSnapshotsPerActor);
        return stats;
    }
}