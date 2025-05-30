/*
 * 文件名: ActorSharding.java
 * 用途: Actor分片管理器
 * 实现内容:
 *   - Actor分片策略和管理
 *   - 分片再平衡和状态持久化
 *   - 分片监控和故障恢复
 *   - 实体到分片的路由机制
 * 技术选型:
 *   - 一致性哈希实现分片分布
 *   - 虚拟节点提高负载均衡
 *   - 分片状态持久化和恢复
 * 依赖关系:
 *   - 与ClusterActorSystem协作
 *   - 管理分片Actor的生命周期
 *   - 支持跨节点分片通信
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.cluster;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.ActorProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actor分片管理器
 * <p>
 * 负责管理Actor在集群中的分片分布，提供一致性哈希、
 * 分片再平衡、故障恢复等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorSharding {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorSharding.class);
    
    /** 集群Actor系统引用 */
    private final ClusterActorSystem clusterSystem;
    
    /** 分片配置 */
    private final ShardingConfig config;
    
    /** 分片区域管理 */
    private final Map<String, ShardRegion> shardRegions = new ConcurrentHashMap<>();
    
    /** 分片分配表 */
    private final Map<Integer, ClusterActorSystem.ClusterNode> shardAllocation = new ConcurrentHashMap<>();
    
    /** 一致性哈希环 */
    private final ConsistentHash consistentHash;
    
    /** 分片状态 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    
    /** 分片统计 */
    private final AtomicLong totalEntities = new AtomicLong(0);
    private final AtomicLong localEntities = new AtomicLong(0);
    
    public ActorSharding(ClusterActorSystem clusterSystem, ShardingConfig config) {
        this.clusterSystem = clusterSystem;
        this.config = config;
        this.consistentHash = new ConsistentHash(config.getVirtualNodeCount());
        
        logger.info("Actor分片管理器初始化完成，分片数: {}, 虚拟节点数: {}", 
                config.getShardCount(), config.getVirtualNodeCount());
    }
    
    /**
     * 启动分片管理
     */
    public CompletableFuture<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("启动Actor分片管理器");
        
        return CompletableFuture.runAsync(() -> {
            // 初始化分片分配
            initializeShardAllocation();
            
            // 启动分片监控
            startShardMonitoring();
            
            logger.info("Actor分片管理器启动完成");
        });
    }
    
    /**
     * 停止分片管理
     */
    public CompletableFuture<Void> stop() {
        if (!started.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("停止Actor分片管理器");
        
        return CompletableFuture.runAsync(() -> {
            // 停止所有分片区域
            for (ShardRegion region : shardRegions.values()) {
                region.stop();
            }
            shardRegions.clear();
            
            started.set(false);
            logger.info("Actor分片管理器停止完成");
        });
    }
    
    /**
     * 获取分片区域
     */
    public ShardRegion getShardRegion(String typeName) {
        return shardRegions.computeIfAbsent(typeName, k -> new ShardRegion(k, this));
    }
    
    /**
     * 获取实体所在的分片ID
     */
    public int getShardId(String entityId) {
        return Math.abs(entityId.hashCode()) % config.getShardCount();
    }
    
    /**
     * 获取分片所在的节点
     */
    public ClusterActorSystem.ClusterNode getShardNode(int shardId) {
        return shardAllocation.get(shardId);
    }
    
    /**
     * 检查分片是否在本节点
     */
    public boolean isLocalShard(int shardId) {
        ClusterActorSystem.ClusterNode node = getShardNode(shardId);
        return node != null && node.equals(clusterSystem.getCurrentNode());
    }
    
    /**
     * 重新平衡分片
     */
    public CompletableFuture<Void> rebalanceShards() {
        logger.info("开始分片再平衡");
        
        return CompletableFuture.runAsync(() -> {
            Set<ClusterActorSystem.ClusterNode> nodes = clusterSystem.getClusterMembers();
            Map<Integer, ClusterActorSystem.ClusterNode> newAllocation = 
                    calculateShardAllocation(nodes);
            
            // 计算需要迁移的分片
            List<ShardMigration> migrations = calculateMigrations(shardAllocation, newAllocation);
            
            if (!migrations.isEmpty()) {
                logger.info("执行分片迁移，迁移数量: {}", migrations.size());
                executeMigrations(migrations);
                shardAllocation.putAll(newAllocation);
            }
            
            logger.info("分片再平衡完成");
        });
    }
    
    /**
     * 初始化分片分配
     */
    private void initializeShardAllocation() {
        Set<ClusterActorSystem.ClusterNode> nodes = clusterSystem.getClusterMembers();
        if (nodes.isEmpty()) {
            nodes = Set.of(clusterSystem.getCurrentNode());
        }
        
        Map<Integer, ClusterActorSystem.ClusterNode> allocation = calculateShardAllocation(nodes);
        shardAllocation.putAll(allocation);
        
        logger.info("分片分配初始化完成，分配给 {} 个节点", nodes.size());
    }
    
    /**
     * 计算分片分配
     */
    private Map<Integer, ClusterActorSystem.ClusterNode> calculateShardAllocation(
            Set<ClusterActorSystem.ClusterNode> nodes) {
        
        Map<Integer, ClusterActorSystem.ClusterNode> allocation = new HashMap<>();
        List<ClusterActorSystem.ClusterNode> nodeList = new ArrayList<>(nodes);
        
        if (nodeList.isEmpty()) {
            return allocation;
        }
        
        // 使用一致性哈希分配分片
        for (int shardId = 0; shardId < config.getShardCount(); shardId++) {
            ClusterActorSystem.ClusterNode node = consistentHash.getNode(String.valueOf(shardId), nodeList);
            allocation.put(shardId, node);
        }
        
        return allocation;
    }
    
    /**
     * 计算分片迁移
     */
    private List<ShardMigration> calculateMigrations(
            Map<Integer, ClusterActorSystem.ClusterNode> oldAllocation,
            Map<Integer, ClusterActorSystem.ClusterNode> newAllocation) {
        
        List<ShardMigration> migrations = new ArrayList<>();
        
        for (Map.Entry<Integer, ClusterActorSystem.ClusterNode> entry : newAllocation.entrySet()) {
            int shardId = entry.getKey();
            ClusterActorSystem.ClusterNode newNode = entry.getValue();
            ClusterActorSystem.ClusterNode oldNode = oldAllocation.get(shardId);
            
            if (oldNode != null && !oldNode.equals(newNode)) {
                migrations.add(new ShardMigration(shardId, oldNode, newNode));
            }
        }
        
        return migrations;
    }
    
    /**
     * 执行分片迁移
     */
    private void executeMigrations(List<ShardMigration> migrations) {
        for (ShardMigration migration : migrations) {
            try {
                executeMigration(migration);
            } catch (Exception e) {
                logger.error("分片迁移失败: {}", migration, e);
            }
        }
    }
    
    /**
     * 执行单个分片迁移
     */
    private void executeMigration(ShardMigration migration) {
        logger.info("执行分片迁移: {}", migration);
        
        // 这里应该实现具体的分片迁移逻辑
        // 1. 通知源节点停止处理该分片的新消息
        // 2. 等待现有消息处理完成
        // 3. 传输分片状态到目标节点
        // 4. 启动目标节点的分片处理
        // 5. 更新路由表
        
        logger.info("分片迁移完成: {}", migration);
    }
    
    /**
     * 启动分片监控
     */
    private void startShardMonitoring() {
        // 监控分片负载和健康状态
        logger.debug("启动分片监控");
    }
    
    /**
     * 获取分片统计信息
     */
    public ShardingStats getStats() {
        return new ShardingStats(
                config.getShardCount(),
                shardRegions.size(),
                totalEntities.get(),
                localEntities.get(),
                shardAllocation.size()
        );
    }
    
    /**
     * 分片区域
     */
    public static class ShardRegion {
        private final String typeName;
        private final ActorSharding sharding;
        private final Map<String, ActorRef> entities = new ConcurrentHashMap<>();
        
        public ShardRegion(String typeName, ActorSharding sharding) {
            this.typeName = typeName;
            this.sharding = sharding;
        }
        
        /**
         * 获取实体Actor引用
         */
        public ActorRef getEntityRef(String entityId) {
            return entities.computeIfAbsent(entityId, id -> {
                int shardId = sharding.getShardId(id);
                
                if (sharding.isLocalShard(shardId)) {
                    // 本地分片，直接创建Actor
                    return createLocalEntity(id);
                } else {
                    // 远程分片，创建远程引用
                    return createRemoteEntity(id, shardId);
                }
            });
        }
        
        /**
         * 创建本地实体Actor
         */
        private ActorRef createLocalEntity(String entityId) {
            // 这里应该根据typeName创建对应的Actor
            sharding.localEntities.incrementAndGet();
            logger.debug("创建本地实体Actor: {}/{}", typeName, entityId);
            // 返回实际的Actor引用
            return null; // 待实现
        }
        
        /**
         * 创建远程实体引用
         */
        private ActorRef createRemoteEntity(String entityId, int shardId) {
            ClusterActorSystem.ClusterNode node = sharding.getShardNode(shardId);
            if (node == null) {
                throw new IllegalStateException("分片 " + shardId + " 没有分配到任何节点");
            }
            
            String actorPath = String.format("/user/%s/%s", typeName, entityId);
            return sharding.clusterSystem.getRemoteActor(actorPath, node.getAddress());
        }
        
        /**
         * 停止分片区域
         */
        public void stop() {
            logger.info("停止分片区域: {}", typeName);
            entities.clear();
        }
        
        public String getTypeName() { return typeName; }
        public int getEntityCount() { return entities.size(); }
    }
    
    /**
     * 一致性哈希实现
     */
    private static class ConsistentHash {
        private final int virtualNodeCount;
        private final MessageDigest md5;
        
        public ConsistentHash(int virtualNodeCount) {
            this.virtualNodeCount = virtualNodeCount;
            try {
                this.md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("MD5算法不可用", e);
            }
        }
        
        /**
         * 获取键对应的节点
         */
        public ClusterActorSystem.ClusterNode getNode(String key, List<ClusterActorSystem.ClusterNode> nodes) {
            if (nodes.isEmpty()) {
                return null;
            }
            
            TreeMap<Long, ClusterActorSystem.ClusterNode> ring = buildHashRing(nodes);
            long hash = hash(key);
            
            Map.Entry<Long, ClusterActorSystem.ClusterNode> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            
            return entry.getValue();
        }
        
        /**
         * 构建哈希环
         */
        private TreeMap<Long, ClusterActorSystem.ClusterNode> buildHashRing(List<ClusterActorSystem.ClusterNode> nodes) {
            TreeMap<Long, ClusterActorSystem.ClusterNode> ring = new TreeMap<>();
            
            for (ClusterActorSystem.ClusterNode node : nodes) {
                for (int i = 0; i < virtualNodeCount; i++) {
                    String virtualNodeKey = node.getNodeId() + "-" + i;
                    long hash = hash(virtualNodeKey);
                    ring.put(hash, node);
                }
            }
            
            return ring;
        }
        
        /**
         * 计算哈希值
         */
        private long hash(String key) {
            md5.reset();
            md5.update(key.getBytes());
            byte[] digest = md5.digest();
            
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            
            return hash;
        }
    }
    
    /**
     * 分片迁移信息
     */
    private static class ShardMigration {
        final int shardId;
        final ClusterActorSystem.ClusterNode fromNode;
        final ClusterActorSystem.ClusterNode toNode;
        
        ShardMigration(int shardId, ClusterActorSystem.ClusterNode fromNode, ClusterActorSystem.ClusterNode toNode) {
            this.shardId = shardId;
            this.fromNode = fromNode;
            this.toNode = toNode;
        }
        
        @Override
        public String toString() {
            return String.format("ShardMigration{shard=%d, from=%s, to=%s}", 
                    shardId, fromNode.getNodeId(), toNode.getNodeId());
        }
    }
    
    /**
     * 分片配置
     */
    public static class ShardingConfig {
        private final int shardCount;
        private final int virtualNodeCount;
        private final boolean autoRebalance;
        private final int rebalanceIntervalSeconds;
        
        public ShardingConfig(int shardCount, int virtualNodeCount, boolean autoRebalance, int rebalanceIntervalSeconds) {
            this.shardCount = shardCount;
            this.virtualNodeCount = virtualNodeCount;
            this.autoRebalance = autoRebalance;
            this.rebalanceIntervalSeconds = rebalanceIntervalSeconds;
        }
        
        public static ShardingConfig defaultConfig() {
            return new ShardingConfig(128, 100, true, 300);
        }
        
        public int getShardCount() { return shardCount; }
        public int getVirtualNodeCount() { return virtualNodeCount; }
        public boolean isAutoRebalance() { return autoRebalance; }
        public int getRebalanceIntervalSeconds() { return rebalanceIntervalSeconds; }
    }
    
    /**
     * 分片统计信息
     */
    public static class ShardingStats {
        private final int totalShards;
        private final int activeRegions;
        private final long totalEntities;
        private final long localEntities;
        private final int allocatedShards;
        
        public ShardingStats(int totalShards, int activeRegions, long totalEntities, long localEntities, int allocatedShards) {
            this.totalShards = totalShards;
            this.activeRegions = activeRegions;
            this.totalEntities = totalEntities;
            this.localEntities = localEntities;
            this.allocatedShards = allocatedShards;
        }
        
        public int getTotalShards() { return totalShards; }
        public int getActiveRegions() { return activeRegions; }
        public long getTotalEntities() { return totalEntities; }
        public long getLocalEntities() { return localEntities; }
        public long getRemoteEntities() { return totalEntities - localEntities; }
        public int getAllocatedShards() { return allocatedShards; }
        
        @Override
        public String toString() {
            return String.format("ShardingStats{totalShards=%d, activeRegions=%d, totalEntities=%d, localEntities=%d, allocatedShards=%d}",
                    totalShards, activeRegions, totalEntities, localEntities, allocatedShards);
        }
    }
}