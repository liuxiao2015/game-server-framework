/*
 * 文件名: MessageRouter.java
 * 用途: 消息路由器
 * 实现内容:
 *   - 消息路由规则配置和管理
 *   - 动态路由决策和转发
 *   - 负载均衡算法支持
 *   - 广播、单播、组播功能
 *   - 路由缓存和性能优化
 * 技术选型:
 *   - 策略模式实现路由算法
 *   - 一致性Hash负载均衡
 *   - 缓存优化路由性能
 * 依赖关系:
 *   - 被MessageDispatcher使用
 *   - 与Connection接口协作
 *   - 支持自定义路由策略
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.message;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息路由器
 * <p>
 * 负责消息的路由决策和转发，支持多种路由策略和负载均衡算法。
 * 提供广播、单播、组播等多种消息发送模式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class MessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(MessageRouter.class);

    // 路由规则表
    private final Map<String, RouteRule> routeRules = new ConcurrentHashMap<>();
    
    // 连接分组
    private final Map<String, Set<Connection>> connectionGroups = new ConcurrentHashMap<>();
    
    // 路由缓存
    private final Map<String, List<Connection>> routeCache = new ConcurrentHashMap<>();
    
    // 负载均衡器
    private final Map<String, LoadBalancer> loadBalancers = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalRouted = new AtomicLong(0);
    private final AtomicLong totalBroadcast = new AtomicLong(0);
    private final AtomicLong totalMulticast = new AtomicLong(0);
    
    // 默认路由策略
    private RouteStrategy defaultStrategy = RouteStrategy.ROUND_ROBIN;

    /**
     * 路由策略枚举
     */
    public enum RouteStrategy {
        /** 轮询 */
        ROUND_ROBIN,
        /** 随机 */
        RANDOM,
        /** 最少连接 */
        LEAST_CONNECTIONS,
        /** 一致性Hash */
        CONSISTENT_HASH,
        /** 权重轮询 */
        WEIGHTED_ROUND_ROBIN,
        /** 自定义 */
        CUSTOM
    }

    /**
     * 路由规则
     */
    public static class RouteRule {
        private final String messageType;
        private final RouteStrategy strategy;
        private final String targetGroup;
        private final Function<Object, String> routeFunction;
        private final Map<String, Object> properties;
        
        public RouteRule(String messageType, RouteStrategy strategy, String targetGroup) {
            this(messageType, strategy, targetGroup, null, new HashMap<>());
        }
        
        public RouteRule(String messageType, RouteStrategy strategy, String targetGroup,
                        Function<Object, String> routeFunction, Map<String, Object> properties) {
            this.messageType = messageType;
            this.strategy = strategy;
            this.targetGroup = targetGroup;
            this.routeFunction = routeFunction;
            this.properties = properties != null ? properties : new HashMap<>();
        }
        
        public String getMessageType() { return messageType; }
        public RouteStrategy getStrategy() { return strategy; }
        public String getTargetGroup() { return targetGroup; }
        public Function<Object, String> getRouteFunction() { return routeFunction; }
        public Map<String, Object> getProperties() { return properties; }
    }

    /**
     * 负载均衡器接口
     */
    public interface LoadBalancer {
        /**
         * 选择连接
         *
         * @param connections 可用连接列表
         * @param message     消息对象
         * @return 选中的连接
         */
        Connection select(List<Connection> connections, Object message);
    }

    /**
     * 路由结果
     */
    public static class RouteResult {
        private final List<Connection> connections;
        private final RouteStrategy strategy;
        private final boolean cached;
        
        public RouteResult(List<Connection> connections, RouteStrategy strategy, boolean cached) {
            this.connections = connections;
            this.strategy = strategy;
            this.cached = cached;
        }
        
        public List<Connection> getConnections() { return connections; }
        public RouteStrategy getStrategy() { return strategy; }
        public boolean isCached() { return cached; }
        public boolean isEmpty() { return connections == null || connections.isEmpty(); }
        public int size() { return connections != null ? connections.size() : 0; }
    }

    /**
     * 构造函数
     */
    public MessageRouter() {
        // 注册默认负载均衡器
        registerDefaultLoadBalancers();
        logger.info("消息路由器初始化完成");
    }

    /**
     * 注册默认负载均衡器
     */
    private void registerDefaultLoadBalancers() {
        // 轮询负载均衡器
        registerLoadBalancer(RouteStrategy.ROUND_ROBIN, new RoundRobinLoadBalancer());
        
        // 随机负载均衡器
        registerLoadBalancer(RouteStrategy.RANDOM, new RandomLoadBalancer());
        
        // 最少连接负载均衡器
        registerLoadBalancer(RouteStrategy.LEAST_CONNECTIONS, new LeastConnectionsLoadBalancer());
        
        // 一致性Hash负载均衡器
        registerLoadBalancer(RouteStrategy.CONSISTENT_HASH, new ConsistentHashLoadBalancer());
    }

    /**
     * 注册负载均衡器
     */
    public void registerLoadBalancer(RouteStrategy strategy, LoadBalancer loadBalancer) {
        loadBalancers.put(strategy.name(), loadBalancer);
        logger.debug("注册负载均衡器: {}", strategy);
    }

    /**
     * 添加路由规则
     */
    public void addRouteRule(RouteRule rule) {
        routeRules.put(rule.getMessageType(), rule);
        // 清除相关缓存
        clearCache(rule.getMessageType());
        logger.debug("添加路由规则: {} -> {}", rule.getMessageType(), rule.getTargetGroup());
    }

    /**
     * 移除路由规则
     */
    public void removeRouteRule(String messageType) {
        RouteRule removed = routeRules.remove(messageType);
        if (removed != null) {
            clearCache(messageType);
            logger.debug("移除路由规则: {}", messageType);
        }
    }

    /**
     * 添加连接到分组
     */
    public void addConnectionToGroup(String groupName, Connection connection) {
        connectionGroups.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet()).add(connection);
        // 清除相关缓存
        clearCacheByGroup(groupName);
        logger.debug("添加连接到分组: {} -> {}", connection.getId(), groupName);
    }

    /**
     * 从分组移除连接
     */
    public void removeConnectionFromGroup(String groupName, Connection connection) {
        Set<Connection> group = connectionGroups.get(groupName);
        if (group != null && group.remove(connection)) {
            // 清除相关缓存
            clearCacheByGroup(groupName);
            logger.debug("从分组移除连接: {} <- {}", connection.getId(), groupName);
        }
    }

    /**
     * 路由消息（单播）
     */
    public RouteResult route(Object message) {
        String messageType = getMessageType(message);
        RouteRule rule = routeRules.get(messageType);
        
        if (rule == null) {
            // 使用默认策略
            return routeWithDefault(message, messageType);
        }
        
        return routeWithRule(message, rule);
    }

    /**
     * 广播消息
     */
    public RouteResult broadcast(Object message) {
        return broadcast(message, null);
    }

    /**
     * 向指定分组广播消息
     */
    public RouteResult broadcast(Object message, String groupName) {
        List<Connection> connections;
        
        if (groupName != null) {
            Set<Connection> group = connectionGroups.get(groupName);
            connections = group != null ? new ArrayList<>(group) : Collections.emptyList();
        } else {
            // 向所有连接广播
            connections = connectionGroups.values().stream()
                    .flatMap(Set::stream)
                    .distinct()
                    .collect(Collectors.toList());
        }
        
        totalBroadcast.incrementAndGet();
        logger.debug("广播消息: {}, 目标连接数: {}", getMessageType(message), connections.size());
        
        return new RouteResult(connections, RouteStrategy.ROUND_ROBIN, false);
    }

    /**
     * 组播消息
     */
    public RouteResult multicast(Object message, Collection<String> groupNames) {
        Set<Connection> connections = new HashSet<>();
        
        for (String groupName : groupNames) {
            Set<Connection> group = connectionGroups.get(groupName);
            if (group != null) {
                connections.addAll(group);
            }
        }
        
        totalMulticast.incrementAndGet();
        logger.debug("组播消息: {}, 目标分组: {}, 连接数: {}", 
                    getMessageType(message), groupNames, connections.size());
        
        return new RouteResult(new ArrayList<>(connections), RouteStrategy.ROUND_ROBIN, false);
    }

    /**
     * 使用默认策略路由
     */
    private RouteResult routeWithDefault(Object message, String messageType) {
        // 检查缓存
        List<Connection> cached = routeCache.get(messageType);
        if (cached != null && !cached.isEmpty()) {
            Connection selected = selectConnection(cached, message, defaultStrategy);
            return new RouteResult(selected != null ? Arrays.asList(selected) : Collections.emptyList(), 
                                 defaultStrategy, true);
        }
        
        // 从所有连接中选择
        List<Connection> allConnections = getAllActiveConnections();
        if (allConnections.isEmpty()) {
            return new RouteResult(Collections.emptyList(), defaultStrategy, false);
        }
        
        Connection selected = selectConnection(allConnections, message, defaultStrategy);
        List<Connection> result = selected != null ? Arrays.asList(selected) : Collections.emptyList();
        
        // 缓存结果
        routeCache.put(messageType, allConnections);
        totalRouted.incrementAndGet();
        
        return new RouteResult(result, defaultStrategy, false);
    }

    /**
     * 使用规则路由
     */
    private RouteResult routeWithRule(Object message, RouteRule rule) {
        String cacheKey = rule.getMessageType() + ":" + rule.getTargetGroup();
        
        // 检查缓存
        List<Connection> cached = routeCache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            Connection selected = selectConnection(cached, message, rule.getStrategy());
            return new RouteResult(selected != null ? Arrays.asList(selected) : Collections.emptyList(), 
                                 rule.getStrategy(), true);
        }
        
        // 获取目标连接
        List<Connection> targetConnections = getTargetConnections(rule);
        if (targetConnections.isEmpty()) {
            return new RouteResult(Collections.emptyList(), rule.getStrategy(), false);
        }
        
        Connection selected = selectConnection(targetConnections, message, rule.getStrategy());
        List<Connection> result = selected != null ? Arrays.asList(selected) : Collections.emptyList();
        
        // 缓存结果
        routeCache.put(cacheKey, targetConnections);
        totalRouted.incrementAndGet();
        
        return new RouteResult(result, rule.getStrategy(), false);
    }

    /**
     * 获取目标连接
     */
    private List<Connection> getTargetConnections(RouteRule rule) {
        if (rule.getTargetGroup() != null) {
            Set<Connection> group = connectionGroups.get(rule.getTargetGroup());
            return group != null ? new ArrayList<>(group) : Collections.emptyList();
        }
        
        return getAllActiveConnections();
    }

    /**
     * 选择连接
     */
    private Connection selectConnection(List<Connection> connections, Object message, RouteStrategy strategy) {
        if (connections.isEmpty()) {
            return null;
        }
        
        LoadBalancer loadBalancer = loadBalancers.get(strategy.name());
        if (loadBalancer != null) {
            return loadBalancer.select(connections, message);
        }
        
        // 默认使用第一个连接
        return connections.get(0);
    }

    /**
     * 获取所有活跃连接
     */
    private List<Connection> getAllActiveConnections() {
        return connectionGroups.values().stream()
                .flatMap(Set::stream)
                .filter(Connection::isActive)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取消息类型
     */
    private String getMessageType(Object message) {
        if (message == null) {
            return "null";
        }
        return message.getClass().getSimpleName();
    }

    /**
     * 清除缓存
     */
    private void clearCache(String messageType) {
        routeCache.entrySet().removeIf(entry -> entry.getKey().startsWith(messageType + ":"));
    }

    /**
     * 按分组清除缓存
     */
    private void clearCacheByGroup(String groupName) {
        routeCache.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + groupName));
    }

    /**
     * 轮询负载均衡器
     */
    private static class RoundRobinLoadBalancer implements LoadBalancer {
        private final AtomicLong counter = new AtomicLong(0);
        
        @Override
        public Connection select(List<Connection> connections, Object message) {
            if (connections.isEmpty()) return null;
            int index = (int) (counter.getAndIncrement() % connections.size());
            return connections.get(index);
        }
    }

    /**
     * 随机负载均衡器
     */
    private static class RandomLoadBalancer implements LoadBalancer {
        @Override
        public Connection select(List<Connection> connections, Object message) {
            if (connections.isEmpty()) return null;
            int index = ThreadLocalRandom.current().nextInt(connections.size());
            return connections.get(index);
        }
    }

    /**
     * 最少连接负载均衡器
     */
    private static class LeastConnectionsLoadBalancer implements LoadBalancer {
        @Override
        public Connection select(List<Connection> connections, Object message) {
            if (connections.isEmpty()) return null;
            
            // 简单实现：选择第一个连接
            // 实际应该根据连接的活跃请求数选择
            return connections.get(0);
        }
    }

    /**
     * 一致性Hash负载均衡器
     */
    private static class ConsistentHashLoadBalancer implements LoadBalancer {
        @Override
        public Connection select(List<Connection> connections, Object message) {
            if (connections.isEmpty()) return null;
            
            // 使用消息hash值选择连接
            int hash = message.hashCode();
            int index = Math.abs(hash) % connections.size();
            return connections.get(index);
        }
    }

    /**
     * 获取统计信息
     */
    public RouterStatistics getStatistics() {
        return new RouterStatistics(
            routeRules.size(),
            connectionGroups.size(),
            routeCache.size(),
            totalRouted.get(),
            totalBroadcast.get(),
            totalMulticast.get()
        );
    }

    /**
     * 路由统计信息
     */
    public static class RouterStatistics {
        private final int ruleCount;
        private final int groupCount;
        private final int cacheSize;
        private final long totalRouted;
        private final long totalBroadcast;
        private final long totalMulticast;
        
        public RouterStatistics(int ruleCount, int groupCount, int cacheSize,
                              long totalRouted, long totalBroadcast, long totalMulticast) {
            this.ruleCount = ruleCount;
            this.groupCount = groupCount;
            this.cacheSize = cacheSize;
            this.totalRouted = totalRouted;
            this.totalBroadcast = totalBroadcast;
            this.totalMulticast = totalMulticast;
        }
        
        public int getRuleCount() { return ruleCount; }
        public int getGroupCount() { return groupCount; }
        public int getCacheSize() { return cacheSize; }
        public long getTotalRouted() { return totalRouted; }
        public long getTotalBroadcast() { return totalBroadcast; }
        public long getTotalMulticast() { return totalMulticast; }
        
        @Override
        public String toString() {
            return String.format("RouterStats{rules=%d, groups=%d, cache=%d, routed=%d, broadcast=%d, multicast=%d}",
                               ruleCount, groupCount, cacheSize, totalRouted, totalBroadcast, totalMulticast);
        }
    }

    /**
     * 设置默认路由策略
     */
    public void setDefaultStrategy(RouteStrategy strategy) {
        this.defaultStrategy = strategy;
        logger.debug("设置默认路由策略: {}", strategy);
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        routeCache.clear();
        logger.debug("清除所有路由缓存");
    }
}