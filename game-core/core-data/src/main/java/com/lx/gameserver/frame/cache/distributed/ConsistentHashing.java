/*
 * 文件名: ConsistentHashing.java
 * 用途: 一致性哈希
 * 实现内容:
 *   - 一致性哈希算法实现
 *   - 虚拟节点支持
 *   - 节点动态增减
 *   - 数据迁移处理
 *   - 负载均衡优化
 *   - 容错处理机制
 * 技术选型:
 *   - TreeMap实现哈希环
 *   - SHA-1哈希算法
 *   - 虚拟节点负载均衡
 *   - 并发安全设计
 * 依赖关系:
 *   - 被分布式缓存使用
 *   - 提供节点路由功能
 *   - 支持集群扩缩容
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * 一致性哈希
 * <p>
 * 基于哈希环的一致性哈希实现，支持虚拟节点、动态扩缩容、
 * 负载均衡等功能。适用于分布式缓存的节点路由。
 * </p>
 *
 * @param <T> 节点类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ConsistentHashing<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashing.class);

    /**
     * 默认虚拟节点数量
     */
    private static final int DEFAULT_VIRTUAL_NODES = 160;

    /**
     * 哈希环（有序Map）
     */
    private final TreeMap<Long, VirtualNode<T>> hashRing;

    /**
     * 实际节点映射
     */
    private final Map<T, Set<VirtualNode<T>>> physicalNodes;

    /**
     * 虚拟节点数量
     */
    private final int virtualNodeCount;

    /**
     * 哈希函数
     */
    private final Function<String, Long> hashFunction;

    /**
     * 读写锁
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 节点状态监听器
     */
    private final Set<NodeChangeListener<T>> listeners = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     */
    public ConsistentHashing() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    /**
     * 构造函数
     *
     * @param virtualNodeCount 虚拟节点数量
     */
    public ConsistentHashing(int virtualNodeCount) {
        this(virtualNodeCount, ConsistentHashing::defaultHash);
    }

    /**
     * 构造函数
     *
     * @param virtualNodeCount 虚拟节点数量
     * @param hashFunction     哈希函数
     */
    public ConsistentHashing(int virtualNodeCount, Function<String, Long> hashFunction) {
        this.virtualNodeCount = virtualNodeCount;
        this.hashFunction = hashFunction;
        this.hashRing = new TreeMap<>();
        this.physicalNodes = new ConcurrentHashMap<>();
        
        logger.info("初始化一致性哈希，虚拟节点数: {}", virtualNodeCount);
    }

    /**
     * 添加节点
     *
     * @param node 节点
     */
    public void addNode(T node) {
        lock.writeLock().lock();
        try {
            if (physicalNodes.containsKey(node)) {
                logger.warn("节点已存在: {}", node);
                return;
            }

            Set<VirtualNode<T>> virtualNodes = new HashSet<>();
            
            // 创建虚拟节点
            for (int i = 0; i < virtualNodeCount; i++) {
                String virtualKey = node.toString() + "#" + i;
                long hash = hashFunction.apply(virtualKey);
                
                VirtualNode<T> virtualNode = new VirtualNode<>(node, i, hash);
                virtualNodes.add(virtualNode);
                hashRing.put(hash, virtualNode);
            }
            
            physicalNodes.put(node, virtualNodes);
            
            logger.info("添加节点: {}, 虚拟节点数: {}", node, virtualNodeCount);
            
            // 通知监听器
            notifyNodeAdded(node);
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 移除节点
     *
     * @param node 节点
     */
    public void removeNode(T node) {
        lock.writeLock().lock();
        try {
            Set<VirtualNode<T>> virtualNodes = physicalNodes.remove(node);
            if (virtualNodes == null) {
                logger.warn("节点不存在: {}", node);
                return;
            }

            // 从哈希环中移除虚拟节点
            for (VirtualNode<T> virtualNode : virtualNodes) {
                hashRing.remove(virtualNode.getHash());
            }
            
            logger.info("移除节点: {}, 虚拟节点数: {}", node, virtualNodes.size());
            
            // 通知监听器
            notifyNodeRemoved(node);
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取节点
     *
     * @param key 键
     * @return 节点，如果没有节点则返回null
     */
    public T getNode(String key) {
        lock.readLock().lock();
        try {
            if (hashRing.isEmpty()) {
                return null;
            }

            long hash = hashFunction.apply(key);
            Map.Entry<Long, VirtualNode<T>> entry = hashRing.ceilingEntry(hash);
            
            if (entry == null) {
                // 环形结构，取第一个节点
                entry = hashRing.firstEntry();
            }
            
            return entry.getValue().getPhysicalNode();
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取多个节点（用于副本）
     *
     * @param key         键
     * @param replicaCount 副本数量
     * @return 节点列表
     */
    public List<T> getNodes(String key, int replicaCount) {
        lock.readLock().lock();
        try {
            if (hashRing.isEmpty()) {
                return Collections.emptyList();
            }

            List<T> nodes = new ArrayList<>();
            Set<T> selectedNodes = new HashSet<>();
            
            long hash = hashFunction.apply(key);
            NavigableMap<Long, VirtualNode<T>> tailMap = hashRing.tailMap(hash, true);
            
            // 从当前位置开始查找
            addNodesFromMap(tailMap, nodes, selectedNodes, replicaCount);
            
            // 如果还没有找够，从头开始查找
            if (nodes.size() < replicaCount) {
                NavigableMap<Long, VirtualNode<T>> headMap = hashRing.headMap(hash, false);
                addNodesFromMap(headMap, nodes, selectedNodes, replicaCount);
            }
            
            return nodes;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有节点
     *
     * @return 节点集合
     */
    public Set<T> getAllNodes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(physicalNodes.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取节点数量
     *
     * @return 节点数量
     */
    public int getNodeCount() {
        lock.readLock().lock();
        try {
            return physicalNodes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取虚拟节点总数
     *
     * @return 虚拟节点总数
     */
    public int getVirtualNodeCount() {
        lock.readLock().lock();
        try {
            return hashRing.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取节点负载信息
     *
     * @return 节点负载映射
     */
    public Map<T, NodeLoad> getNodeLoads() {
        lock.readLock().lock();
        try {
            Map<T, NodeLoad> loads = new HashMap<>();
            
            if (hashRing.isEmpty()) {
                return loads;
            }
            
            Map<T, Long> nodeSizes = new HashMap<>();
            Long lastHash = null;
            T lastNode = null;
            
            // 计算每个节点负责的哈希区间大小
            for (Map.Entry<Long, VirtualNode<T>> entry : hashRing.entrySet()) {
                Long currentHash = entry.getKey();
                T currentNode = entry.getValue().getPhysicalNode();
                
                if (lastHash != null && lastNode != null) {
                    long size = currentHash - lastHash;
                    nodeSizes.merge(lastNode, size, Long::sum);
                }
                
                lastHash = currentHash;
                lastNode = currentNode;
            }
            
            // 处理环形结构的最后一段
            if (lastHash != null && lastNode != null) {
                Long firstHash = hashRing.firstKey();
                long size = (Long.MAX_VALUE - lastHash) + (firstHash - Long.MIN_VALUE);
                nodeSizes.merge(lastNode, size, Long::sum);
            }
            
            // 计算负载百分比
            long totalSize = nodeSizes.values().stream().mapToLong(Long::longValue).sum();
            for (Map.Entry<T, Long> entry : nodeSizes.entrySet()) {
                double percentage = totalSize > 0 ? (double) entry.getValue() / totalSize * 100 : 0;
                loads.put(entry.getKey(), new NodeLoad(entry.getValue(), percentage));
            }
            
            return loads;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 添加节点变更监听器
     *
     * @param listener 监听器
     */
    public void addNodeChangeListener(NodeChangeListener<T> listener) {
        listeners.add(listener);
    }

    /**
     * 移除节点变更监听器
     *
     * @param listener 监听器
     */
    public void removeNodeChangeListener(NodeChangeListener<T> listener) {
        listeners.remove(listener);
    }

    /**
     * 从映射中添加节点
     */
    private void addNodesFromMap(NavigableMap<Long, VirtualNode<T>> map, 
                                List<T> nodes, Set<T> selectedNodes, int replicaCount) {
        for (VirtualNode<T> virtualNode : map.values()) {
            T physicalNode = virtualNode.getPhysicalNode();
            if (!selectedNodes.contains(physicalNode)) {
                nodes.add(physicalNode);
                selectedNodes.add(physicalNode);
                
                if (nodes.size() >= replicaCount) {
                    break;
                }
            }
        }
    }

    /**
     * 通知节点添加
     */
    private void notifyNodeAdded(T node) {
        for (NodeChangeListener<T> listener : listeners) {
            try {
                listener.onNodeAdded(node);
            } catch (Exception e) {
                logger.warn("通知节点添加监听器失败", e);
            }
        }
    }

    /**
     * 通知节点移除
     */
    private void notifyNodeRemoved(T node) {
        for (NodeChangeListener<T> listener : listeners) {
            try {
                listener.onNodeRemoved(node);
            } catch (Exception e) {
                logger.warn("通知节点移除监听器失败", e);
            }
        }
    }

    /**
     * 默认哈希函数（SHA-1）
     */
    private static long defaultHash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            
            // 取前8个字节转换为long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // 降级为简单哈希
            return key.hashCode();
        }
    }

    /**
     * 虚拟节点
     */
    private static class VirtualNode<T> {
        private final T physicalNode;
        private final int virtualIndex;
        private final long hash;

        public VirtualNode(T physicalNode, int virtualIndex, long hash) {
            this.physicalNode = physicalNode;
            this.virtualIndex = virtualIndex;
            this.hash = hash;
        }

        public T getPhysicalNode() {
            return physicalNode;
        }

        public int getVirtualIndex() {
            return virtualIndex;
        }

        public long getHash() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VirtualNode<?> that = (VirtualNode<?>) o;
            return virtualIndex == that.virtualIndex && 
                   hash == that.hash && 
                   Objects.equals(physicalNode, that.physicalNode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(physicalNode, virtualIndex, hash);
        }

        @Override
        public String toString() {
            return String.format("VirtualNode{physical=%s, index=%d, hash=%d}", 
                physicalNode, virtualIndex, hash);
        }
    }

    /**
     * 节点负载信息
     */
    public static class NodeLoad {
        private final long hashRangeSize;
        private final double percentage;

        public NodeLoad(long hashRangeSize, double percentage) {
            this.hashRangeSize = hashRangeSize;
            this.percentage = percentage;
        }

        public long getHashRangeSize() {
            return hashRangeSize;
        }

        public double getPercentage() {
            return percentage;
        }

        @Override
        public String toString() {
            return String.format("NodeLoad{size=%d, percentage=%.2f%%}", 
                hashRangeSize, percentage);
        }
    }

    /**
     * 节点变更监听器
     */
    public interface NodeChangeListener<T> {
        /**
         * 节点添加事件
         *
         * @param node 添加的节点
         */
        void onNodeAdded(T node);

        /**
         * 节点移除事件
         *
         * @param node 移除的节点
         */
        void onNodeRemoved(T node);
    }

    /**
     * 一致性哈希统计信息
     */
    public static class HashRingStatistics {
        private final int physicalNodeCount;
        private final int virtualNodeCount;
        private final Map<Object, NodeLoad> nodeLoads;
        private final double loadBalance;

        public HashRingStatistics(int physicalNodeCount, int virtualNodeCount, 
                                Map<Object, NodeLoad> nodeLoads) {
            this.physicalNodeCount = physicalNodeCount;
            this.virtualNodeCount = virtualNodeCount;
            this.nodeLoads = new HashMap<>(nodeLoads);
            this.loadBalance = calculateLoadBalance(nodeLoads);
        }

        public int getPhysicalNodeCount() { return physicalNodeCount; }
        public int getVirtualNodeCount() { return virtualNodeCount; }
        public Map<Object, NodeLoad> getNodeLoads() { return nodeLoads; }
        public double getLoadBalance() { return loadBalance; }

        /**
         * 计算负载均衡度（标准差，越小越均衡）
         */
        private double calculateLoadBalance(Map<Object, NodeLoad> loads) {
            if (loads.isEmpty()) {
                return 0.0;
            }
            
            double[] percentages = loads.values().stream()
                .mapToDouble(NodeLoad::getPercentage)
                .toArray();
                
            double mean = Arrays.stream(percentages).average().orElse(0.0);
            double variance = Arrays.stream(percentages)
                .map(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0.0);
                
            return Math.sqrt(variance);
        }
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public HashRingStatistics getStatistics() {
        lock.readLock().lock();
        try {
            Map<Object, NodeLoad> loads = new HashMap<>();
            for (Map.Entry<T, NodeLoad> entry : getNodeLoads().entrySet()) {
                loads.put(entry.getKey(), entry.getValue());
            }
            
            return new HashRingStatistics(
                getNodeCount(),
                getVirtualNodeCount(),
                loads
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}