/*
 * 文件名: ClusterActorSystem.java
 * 用途: 集群Actor系统实现
 * 实现内容:
 *   - 集群Actor系统核心功能
 *   - 节点发现和管理
 *   - Actor分片和跨节点消息路由
 *   - 集群事件处理和状态同步
 * 技术选型:
 *   - 扩展GameActorSystem支持集群功能
 *   - 一致性哈希实现Actor分片
 *   - 网络通信和序列化支持
 * 依赖关系:
 *   - 扩展GameActorSystem
 *   - 与ActorSharding和RemoteActorRef协作
 *   - 支持网络通信和节点发现
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.cluster;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.ActorSystem;
import com.lx.gameserver.frame.actor.core.ActorProps;
import com.lx.gameserver.frame.actor.system.GameActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 集群Actor系统实现
 * <p>
 * 扩展基础Actor系统，支持集群功能，包括节点管理、
 * Actor分片、跨节点通信等特性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ClusterActorSystem extends GameActorSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterActorSystem.class);
    
    /** 当前节点信息 */
    private final ClusterNode currentNode;
    
    /** 集群配置 */
    private final ClusterConfig clusterConfig;
    
    /** 集群状态 */
    private final AtomicReference<ClusterState> clusterState = new AtomicReference<>(ClusterState.JOINING);
    
    /** 集群成员管理 */
    private final ClusterMemberManager memberManager;
    
    /** Actor分片管理 */
    private final ActorSharding actorSharding;
    
    /** 远程Actor引用管理 */
    private final Map<String, RemoteActorRef> remoteActors = new ConcurrentHashMap<>();
    
    /** 集群事件监听器 */
    private final List<ClusterEventListener> eventListeners = new ArrayList<>();
    
    /** 集群是否已启动 */
    private final AtomicBoolean clusterStarted = new AtomicBoolean(false);
    
    public ClusterActorSystem(String name, ClusterConfig clusterConfig) {
        super(name);
        this.clusterConfig = clusterConfig;
        this.currentNode = new ClusterNode(
                clusterConfig.getNodeId(),
                clusterConfig.getBindAddress(),
                clusterConfig.getNodeRoles(),
                Instant.now()
        );
        this.memberManager = new ClusterMemberManager(this, clusterConfig);
        this.actorSharding = new ActorSharding(this, clusterConfig.getShardingConfig());
        
        logger.info("集群Actor系统[{}]初始化完成，节点: {}", name, currentNode);
    }
    
    /**
     * 启动集群功能
     */
    public CompletableFuture<Void> startCluster() {
        if (!clusterStarted.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("启动集群Actor系统[{}]", getName());
        
        return memberManager.start()
                .thenCompose(v -> actorSharding.start())
                .thenRun(() -> {
                    clusterState.set(ClusterState.UP);
                    notifyClusterEvent(new ClusterEvent.NodeUp(currentNode));
                    logger.info("集群Actor系统[{}]启动完成", getName());
                })
                .exceptionally(throwable -> {
                    clusterState.set(ClusterState.DOWN);
                    logger.error("集群Actor系统[{}]启动失败", getName(), throwable);
                    throw new RuntimeException("集群启动失败", throwable);
                });
    }
    
    /**
     * 关闭集群功能
     */
    public CompletableFuture<Void> stopCluster() {
        if (!clusterStarted.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("关闭集群Actor系统[{}]", getName());
        clusterState.set(ClusterState.LEAVING);
        
        return actorSharding.stop()
                .thenCompose(v -> memberManager.stop())
                .thenRun(() -> {
                    clusterState.set(ClusterState.DOWN);
                    notifyClusterEvent(new ClusterEvent.NodeDown(currentNode));
                    logger.info("集群Actor系统[{}]关闭完成", getName());
                })
                .whenComplete((v, throwable) -> {
                    clusterStarted.set(false);
                    if (throwable != null) {
                        logger.error("集群Actor系统[{}]关闭失败", getName(), throwable);
                    }
                });
    }
    
    /**
     * 获取集群状态
     */
    public ClusterState getClusterState() {
        return clusterState.get();
    }
    
    /**
     * 获取当前节点信息
     */
    public ClusterNode getCurrentNode() {
        return currentNode;
    }
    
    /**
     * 获取集群成员列表
     */
    public Set<ClusterNode> getClusterMembers() {
        return memberManager.getMembers();
    }
    
    /**
     * 添加集群事件监听器
     */
    public void addClusterEventListener(ClusterEventListener listener) {
        synchronized (eventListeners) {
            eventListeners.add(listener);
        }
    }
    
    /**
     * 移除集群事件监听器
     */
    public void removeClusterEventListener(ClusterEventListener listener) {
        synchronized (eventListeners) {
            eventListeners.remove(listener);
        }
    }
    
    /**
     * 通知集群事件
     */
    private void notifyClusterEvent(ClusterEvent event) {
        synchronized (eventListeners) {
            for (ClusterEventListener listener : eventListeners) {
                try {
                    listener.onClusterEvent(event);
                } catch (Exception e) {
                    logger.error("集群事件处理失败", e);
                }
            }
        }
    }
    
    /**
     * 创建分片Actor
     */
    public ActorRef shardedActorOf(ActorProps props, String entityId) {
        return actorSharding.getShardRegion(props.getActorClass().getSimpleName())
                .getEntityRef(entityId);
    }
    
    /**
     * 获取远程Actor引用
     */
    public ActorRef getRemoteActor(String actorPath, InetSocketAddress nodeAddress) {
        String key = nodeAddress + "/" + actorPath;
        return remoteActors.computeIfAbsent(key, k -> new RemoteActorRef(actorPath, nodeAddress, this));
    }
    
    /**
     * 集群节点信息
     */
    public static class ClusterNode {
        private final String nodeId;
        private final InetSocketAddress address;
        private final Set<String> roles;
        private final Instant joinTime;
        private volatile NodeStatus status;
        
        public ClusterNode(String nodeId, InetSocketAddress address, Set<String> roles, Instant joinTime) {
            this.nodeId = nodeId;
            this.address = address;
            this.roles = new HashSet<>(roles);
            this.joinTime = joinTime;
            this.status = NodeStatus.JOINING;
        }
        
        public String getNodeId() { return nodeId; }
        public InetSocketAddress getAddress() { return address; }
        public Set<String> getRoles() { return roles; }
        public Instant getJoinTime() { return joinTime; }
        public NodeStatus getStatus() { return status; }
        
        public void setStatus(NodeStatus status) { 
            this.status = status; 
        }
        
        public boolean hasRole(String role) {
            return roles.contains(role);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClusterNode)) return false;
            ClusterNode that = (ClusterNode) o;
            return Objects.equals(nodeId, that.nodeId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(nodeId);
        }
        
        @Override
        public String toString() {
            return String.format("ClusterNode{id=%s, address=%s, roles=%s, status=%s}", 
                    nodeId, address, roles, status);
        }
    }
    
    /**
     * 节点状态枚举
     */
    public enum NodeStatus {
        /** 加入中 */
        JOINING,
        /** 已上线 */
        UP,
        /** 离开中 */
        LEAVING,
        /** 已下线 */
        DOWN,
        /** 不可达 */
        UNREACHABLE
    }
    
    /**
     * 集群状态枚举
     */
    public enum ClusterState {
        /** 加入中 */
        JOINING,
        /** 已上线 */
        UP,
        /** 离开中 */
        LEAVING,
        /** 已下线 */
        DOWN
    }
    
    /**
     * 集群配置
     */
    public static class ClusterConfig {
        private final String nodeId;
        private final InetSocketAddress bindAddress;
        private final Set<String> nodeRoles;
        private final List<InetSocketAddress> seedNodes;
        private final ActorSharding.ShardingConfig shardingConfig;
        private final int heartbeatIntervalSeconds;
        private final int nodeTimeoutSeconds;
        
        public ClusterConfig(String nodeId, InetSocketAddress bindAddress, Set<String> nodeRoles,
                           List<InetSocketAddress> seedNodes, ActorSharding.ShardingConfig shardingConfig,
                           int heartbeatIntervalSeconds, int nodeTimeoutSeconds) {
            this.nodeId = nodeId;
            this.bindAddress = bindAddress;
            this.nodeRoles = nodeRoles;
            this.seedNodes = seedNodes;
            this.shardingConfig = shardingConfig;
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
            this.nodeTimeoutSeconds = nodeTimeoutSeconds;
        }
        
        public String getNodeId() { return nodeId; }
        public InetSocketAddress getBindAddress() { return bindAddress; }
        public Set<String> getNodeRoles() { return nodeRoles; }
        public List<InetSocketAddress> getSeedNodes() { return seedNodes; }
        public ActorSharding.ShardingConfig getShardingConfig() { return shardingConfig; }
        public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
        public int getNodeTimeoutSeconds() { return nodeTimeoutSeconds; }
    }
    
    /**
     * 集群事件接口
     */
    public static abstract class ClusterEvent {
        private final Instant timestamp;
        
        protected ClusterEvent() {
            this.timestamp = Instant.now();
        }
        
        public Instant getTimestamp() { return timestamp; }
        
        /**
         * 节点上线事件
         */
        public static class NodeUp extends ClusterEvent {
            private final ClusterNode node;
            
            public NodeUp(ClusterNode node) {
                this.node = node;
            }
            
            public ClusterNode getNode() { return node; }
            
            @Override
            public String toString() {
                return String.format("NodeUp{node=%s, timestamp=%s}", node, getTimestamp());
            }
        }
        
        /**
         * 节点下线事件
         */
        public static class NodeDown extends ClusterEvent {
            private final ClusterNode node;
            
            public NodeDown(ClusterNode node) {
                this.node = node;
            }
            
            public ClusterNode getNode() { return node; }
            
            @Override
            public String toString() {
                return String.format("NodeDown{node=%s, timestamp=%s}", node, getTimestamp());
            }
        }
        
        /**
         * 节点不可达事件
         */
        public static class NodeUnreachable extends ClusterEvent {
            private final ClusterNode node;
            
            public NodeUnreachable(ClusterNode node) {
                this.node = node;
            }
            
            public ClusterNode getNode() { return node; }
            
            @Override
            public String toString() {
                return String.format("NodeUnreachable{node=%s, timestamp=%s}", node, getTimestamp());
            }
        }
        
        /**
         * 节点可达事件
         */
        public static class NodeReachable extends ClusterEvent {
            private final ClusterNode node;
            
            public NodeReachable(ClusterNode node) {
                this.node = node;
            }
            
            public ClusterNode getNode() { return node; }
            
            @Override
            public String toString() {
                return String.format("NodeReachable{node=%s, timestamp=%s}", node, getTimestamp());
            }
        }
    }
    
    /**
     * 集群事件监听器接口
     */
    public interface ClusterEventListener {
        /**
         * 处理集群事件
         *
         * @param event 集群事件
         */
        void onClusterEvent(ClusterEvent event);
    }
}