/*
 * 文件名: ConsistentHashRouter.java
 * 用途: 一致性哈希路由器
 * 实现内容:
 *   - 一致性哈希算法路由消息
 *   - 虚拟节点支持负载均衡
 *   - 动态节点管理和故障处理
 *   - 路由缓存和性能优化
 * 技术选型:
 *   - MD5哈希算法保证分布均匀
 *   - 红黑树结构高效查找
 *   - 虚拟节点提高负载均衡
 * 依赖关系:
 *   - 继承Router基类
 *   - 与ActorRef和消息系统集成
 *   - 支持集群环境下的路由
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希路由器
 * <p>
 * 使用一致性哈希算法进行消息路由，支持虚拟节点、
 * 动态节点管理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ConsistentHashRouter extends Router {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashRouter.class);
    
    /** 虚拟节点数量 */
    private final int virtualNodeCount;
    
    /** 哈希环 */
    private final TreeMap<Long, ActorRef> hashRing = new TreeMap<>();
    
    /** 虚拟节点映射 */
    private final ConcurrentHashMap<String, Set<Long>> virtualNodes = new ConcurrentHashMap<>();
    
    /** MD5消息摘要 */
    private final MessageDigest md5;
    
    public ConsistentHashRouter(String name, RouterConfig config, int virtualNodeCount) {
        super(name);
        this.virtualNodeCount = virtualNodeCount;
        
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
        
        logger.info("一致性哈希路由器[{}]初始化完成，虚拟节点数: {}", name, virtualNodeCount);
    }

    public ConsistentHashRouter(String name, RouterConfig config) {
        this(name, config, 150); // 默认150个虚拟节点
    }

    public ConsistentHashRouter(String name) {
        this(name, null, 150); // 默认150个虚拟节点，无配置
    }
    
    @Override
    public RouteResult route(Object message, ActorRef sender) {
        recordRouteSuccess();
        return selectRoutees(message, sender);
    }
    
    protected RouteResult selectRoutees(Object message, ActorRef sender) {
        if (hashRing.isEmpty()) {
            return new RouteResult("没有可用的路由目标");
        }
        
        // 计算消息的哈希值
        long hash = calculateHash(message);
        
        // 在哈希环中查找目标节点
        ActorRef target = findTarget(hash);
        
        if (target != null) {
            recordRouteSuccess();
            return new RouteResult(List.of(target));
        } else {
            recordRouteFailure();
            return new RouteResult("未找到路由目标");
        }
    }
    
    @Override
    public synchronized void addRoutee(ActorRef routee) {
        super.addRoutee(routee);
        addToHashRing(routee);
    }
    
    @Override
    public synchronized void removeRoutee(ActorRef routee) {
        super.removeRoutee(routee);
        removeFromHashRing(routee);
    }
    
    /**
     * 添加节点到哈希环
     */
    private void addToHashRing(ActorRef routee) {
        String nodeKey = routee.getPath();
        Set<Long> virtualHashes = new HashSet<>();
        
        for (int i = 0; i < virtualNodeCount; i++) {
            String virtualNodeKey = nodeKey + "#" + i;
            long hash = calculateHash(virtualNodeKey);
            hashRing.put(hash, routee);
            virtualHashes.add(hash);
        }
        
        virtualNodes.put(nodeKey, virtualHashes);
        logger.debug("添加节点到哈希环: {} ({}个虚拟节点)", nodeKey, virtualNodeCount);
    }
    
    /**
     * 从哈希环移除节点
     */
    private void removeFromHashRing(ActorRef routee) {
        String nodeKey = routee.getPath();
        Set<Long> virtualHashes = virtualNodes.remove(nodeKey);
        
        if (virtualHashes != null) {
            for (Long hash : virtualHashes) {
                hashRing.remove(hash);
            }
            logger.debug("从哈希环移除节点: {} ({}个虚拟节点)", nodeKey, virtualHashes.size());
        }
    }
    
    /**
     * 在哈希环中查找目标节点
     */
    private ActorRef findTarget(long hash) {
        if (hashRing.isEmpty()) {
            return null;
        }
        
        // 查找第一个大于等于hash值的节点
        Map.Entry<Long, ActorRef> entry = hashRing.ceilingEntry(hash);
        
        // 如果没有找到，则使用环的第一个节点
        if (entry == null) {
            entry = hashRing.firstEntry();
        }
        
        return entry.getValue();
    }
    
    /**
     * 计算哈希值
     */
    private long calculateHash(Object obj) {
        String key;
        if (obj instanceof Message) {
            // 对于Message对象，使用特定的键生成策略
            Message msg = (Message) obj;
            key = extractHashKey(msg);
        } else {
            key = obj.toString();
        }
        
        return calculateHash(key);
    }
    
    /**
     * 计算字符串的哈希值
     */
    private long calculateHash(String key) {
        synchronized (md5) {
            md5.reset();
            md5.update(key.getBytes());
            byte[] digest = md5.digest();
            
            // 取前8个字节转换为long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            
            return hash;
        }
    }
    
    /**
     * 从消息中提取哈希键
     */
    private String extractHashKey(Message message) {
        // 优先使用路由信息中的键
        Map<String, String> routingInfo = message.getRoutingInfo();
        if (routingInfo.containsKey("hashKey")) {
            return routingInfo.get("hashKey");
        }
        
        // 使用消息ID作为哈希键
        if (message.getMessageId() != null) {
            return message.getMessageId();
        }
        
        // 使用发送者路径
        if (message.getSender() != null) {
            return message.getSender().getPath();
        }
        
        // 最后使用消息类型
        return message.getMessageType();
    }
    
    /**
     * 获取哈希环状态
     */
    public HashRingStatus getHashRingStatus() {
        return new HashRingStatus(
                hashRing.size(),
                virtualNodes.size(),
                virtualNodeCount,
                getRouteeCount()
        );
    }
    
    /**
     * 获取节点分布情况
     */
    public Map<String, Integer> getNodeDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        
        for (ActorRef routee : getRoutees()) {
            String nodeKey = routee.getPath();
            Set<Long> virtualHashes = virtualNodes.get(nodeKey);
            distribution.put(nodeKey, virtualHashes != null ? virtualHashes.size() : 0);
        }
        
        return distribution;
    }
    
    /**
     * 预测消息路由目标
     */
    public ActorRef predictTarget(Object message) {
        long hash = calculateHash(message);
        return findTarget(hash);
    }
    
    /**
     * 哈希环状态
     */
    public static class HashRingStatus {
        private final int totalVirtualNodes;
        private final int physicalNodes;
        private final int virtualNodeCount;
        private final int routeeCount;
        
        public HashRingStatus(int totalVirtualNodes, int physicalNodes, int virtualNodeCount, int routeeCount) {
            this.totalVirtualNodes = totalVirtualNodes;
            this.physicalNodes = physicalNodes;
            this.virtualNodeCount = virtualNodeCount;
            this.routeeCount = routeeCount;
        }
        
        public int getTotalVirtualNodes() { return totalVirtualNodes; }
        public int getPhysicalNodes() { return physicalNodes; }
        public int getVirtualNodeCount() { return virtualNodeCount; }
        public int getRouteeCount() { return routeeCount; }
        public double getAverageVirtualNodesPerPhysical() {
            return physicalNodes > 0 ? (double) totalVirtualNodes / physicalNodes : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("HashRingStatus{totalVirtual=%d, physical=%d, virtualPerPhysical=%.1f}",
                    totalVirtualNodes, physicalNodes, getAverageVirtualNodesPerPhysical());
        }
    }
    
    @Override
    protected void onRouteesChanged() {
        super.onRouteesChanged();
        logger.debug("一致性哈希路由器[{}]路由目标发生变化，当前哈希环大小: {}", getName(), hashRing.size());
    }
    
    @Override
    public String toString() {
        return String.format("ConsistentHashRouter{name=%s, routees=%d, virtualNodes=%d, hashRing=%d}",
                getName(), getRouteeCount(), virtualNodeCount, hashRing.size());
    }
}