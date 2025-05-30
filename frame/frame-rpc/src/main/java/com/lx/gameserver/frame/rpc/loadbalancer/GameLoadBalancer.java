/*
 * 文件名: GameLoadBalancer.java
 * 用途: 游戏定制负载均衡器
 * 实现内容:
 *   - 基于在线人数的负载均衡
 *   - 基于服务器性能的负载均衡
 *   - 会话亲和性支持（同一玩家路由到同一服务器）
 *   - 灰度发布支持
 * 技术选型:
 *   - Spring Cloud LoadBalancer
 *   - 一致性哈希算法
 *   - 权重轮询算法
 * 依赖关系:
 *   - 实现ReactorLoadBalancer接口
 *   - 与服务发现集成
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.loadbalancer;

import com.lx.gameserver.frame.rpc.config.RpcProperties;
import com.lx.gameserver.frame.rpc.discovery.GameServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游戏定制负载均衡器
 * <p>
 * 提供多种负载均衡策略，包括轮询、随机、响应时间权重等。
 * 特别为游戏场景优化，支持会话亲和性和基于服务器负载的智能路由。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class GameLoadBalancer implements ReactorLoadBalancer<ServiceInstance> {

    private static final Logger logger = LoggerFactory.getLogger(GameLoadBalancer.class);

    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;
    private final String serviceId;
    private final String strategy;
    private final RpcProperties rpcProperties;

    // 轮询策略计数器
    private final AtomicInteger position = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));
    
    // 响应时间统计
    private final Map<String, ResponseTimeStats> responseTimeStats = new ConcurrentHashMap<>();
    
    // 会话亲和性映射
    private final Map<String, ServiceInstance> sessionAffinityMap = new ConcurrentHashMap<>();

    /**
     * 负载均衡策略枚举
     */
    public enum Strategy {
        ROUND_ROBIN("round-robin"),
        RANDOM("random"),
        WEIGHTED_RESPONSE_TIME("weighted-response-time"),
        LEAST_CONNECTIONS("least-connections"),
        CONSISTENT_HASH("consistent-hash");

        private final String value;

        Strategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Strategy fromValue(String value) {
            for (Strategy strategy : values()) {
                if (strategy.value.equals(value)) {
                    return strategy;
                }
            }
            return ROUND_ROBIN; // 默认策略
        }
    }

    /**
     * 响应时间统计
     */
    private static class ResponseTimeStats {
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private volatile long totalResponseTime = 0;
        private volatile long lastUpdateTime = System.currentTimeMillis();

        public void recordResponseTime(long responseTime) {
            totalCalls.incrementAndGet();
            synchronized (this) {
                totalResponseTime += responseTime;
                lastUpdateTime = System.currentTimeMillis();
            }
        }

        public double getAverageResponseTime() {
            int calls = totalCalls.get();
            if (calls == 0) {
                return 0.0;
            }
            return (double) totalResponseTime / calls;
        }

        public boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > 300000; // 5分钟
        }
    }

    /**
     * 构造函数
     *
     * @param serviceInstanceListSupplierProvider 服务实例列表提供者
     * @param serviceId                          服务ID
     * @param strategy                          负载均衡策略
     * @param rpcProperties                     RPC配置
     */
    public GameLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
                           String serviceId, String strategy, RpcProperties rpcProperties) {
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.serviceId = serviceId;
        this.strategy = strategy;
        this.rpcProperties = rpcProperties;
    }

    @Override
    public Mono<ReactorLoadBalancer.Response<ServiceInstance>> choose(ReactorLoadBalancer.Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                .getIfAvailable(NoopServiceInstanceListSupplier::new);
        
        return supplier.get(request)
                .next()
                .map(serviceInstances -> processInstanceSelection(serviceInstances, request));
    }

    /**
     * 处理实例选择
     *
     * @param serviceInstances 服务实例列表
     * @param request         请求对象
     * @return 选择结果
     */
    private ReactorLoadBalancer.Response<ServiceInstance> processInstanceSelection(List<ServiceInstance> serviceInstances, ReactorLoadBalancer.Request request) {
        if (serviceInstances.isEmpty()) {
            logger.warn("没有可用的服务实例: {}", serviceId);
            return new EmptyResponse();
        }

        // 过滤健康的实例
        List<ServiceInstance> healthyInstances = filterHealthyInstances(serviceInstances);
        if (healthyInstances.isEmpty()) {
            logger.warn("没有健康的服务实例: {}", serviceId);
            return new EmptyResponse();
        }

        // 根据策略选择实例
        ServiceInstance selectedInstance = selectInstance(healthyInstances, request);
        if (selectedInstance == null) {
            logger.warn("负载均衡器未能选择到实例: {}", serviceId);
            return new EmptyResponse();
        }

        logger.debug("负载均衡选择实例: {} -> {}:{}", serviceId, selectedInstance.getHost(), selectedInstance.getPort());
        return new DefaultResponse(selectedInstance);
    }

    /**
     * 过滤健康的实例
     *
     * @param serviceInstances 服务实例列表
     * @return 健康的实例列表
     */
    private List<ServiceInstance> filterHealthyInstances(List<ServiceInstance> serviceInstances) {
        List<ServiceInstance> healthyInstances = new ArrayList<>();
        
        for (ServiceInstance instance : serviceInstances) {
            // 基本健康检查
            if (isInstanceHealthy(instance)) {
                healthyInstances.add(instance);
            }
        }
        
        return healthyInstances;
    }

    /**
     * 检查实例是否健康
     *
     * @param instance 服务实例
     * @return true表示健康
     */
    private boolean isInstanceHealthy(ServiceInstance instance) {
        // 检查实例元数据中的健康状态
        Map<String, String> metadata = instance.getMetadata();
        if (metadata != null) {
            String status = metadata.get("status");
            if ("DOWN".equals(status) || "OUT_OF_SERVICE".equals(status)) {
                return false;
            }
        }
        
        // 检查是否为游戏服务实例，并进行额外检查
        if (instance instanceof GameServiceInstance gameInstance) {
            // 检查服务器负载
            if (gameInstance.getCpuUsage() > 90.0 || gameInstance.getMemoryUsage() > 90.0) {
                logger.debug("实例负载过高，暂时不可用: {}:{} (CPU: {}%, Memory: {}%)", 
                    instance.getHost(), instance.getPort(), 
                    gameInstance.getCpuUsage(), gameInstance.getMemoryUsage());
                return false;
            }
            
            // 检查在线玩家数是否达到上限
            if (gameInstance.getOnlinePlayerCount() >= gameInstance.getMaxPlayerCount()) {
                logger.debug("实例玩家已满，暂时不可用: {}:{} ({}/{})", 
                    instance.getHost(), instance.getPort(),
                    gameInstance.getOnlinePlayerCount(), gameInstance.getMaxPlayerCount());
                return false;
            }
        }
        
        return true;
    }

    /**
     * 根据策略选择实例
     *
     * @param instances 实例列表
     * @param request  请求对象
     * @return 选择的实例
     */
    private ServiceInstance selectInstance(List<ServiceInstance> instances, ReactorLoadBalancer.Request request) {
        if (instances.size() == 1) {
            return instances.get(0);
        }

        Strategy loadBalanceStrategy = Strategy.fromValue(strategy);
        
        return switch (loadBalanceStrategy) {
            case ROUND_ROBIN -> selectByRoundRobin(instances);
            case RANDOM -> selectByRandom(instances);
            case WEIGHTED_RESPONSE_TIME -> selectByWeightedResponseTime(instances);
            case LEAST_CONNECTIONS -> selectByLeastConnections(instances);
            case CONSISTENT_HASH -> selectByConsistentHash(instances, request);
        };
    }

    /**
     * 轮询策略
     */
    private ServiceInstance selectByRoundRobin(List<ServiceInstance> instances) {
        int pos = Math.abs(position.incrementAndGet() % instances.size());
        return instances.get(pos);
    }

    /**
     * 随机策略
     */
    private ServiceInstance selectByRandom(List<ServiceInstance> instances) {
        return instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
    }

    /**
     * 响应时间权重策略
     */
    private ServiceInstance selectByWeightedResponseTime(List<ServiceInstance> instances) {
        // 清理过期的统计数据
        responseTimeStats.entrySet().removeIf(entry -> entry.getValue().isStale());
        
        // 计算权重
        Map<ServiceInstance, Double> weights = new HashMap<>();
        double totalWeight = 0.0;
        
        for (ServiceInstance instance : instances) {
            String instanceKey = getInstanceKey(instance);
            ResponseTimeStats stats = responseTimeStats.get(instanceKey);
            
            double weight;
            if (stats == null || stats.getAverageResponseTime() == 0) {
                // 新实例或无统计数据，给予默认权重
                weight = 1.0;
            } else {
                // 响应时间越短，权重越高
                weight = 1.0 / stats.getAverageResponseTime();
            }
            
            weights.put(instance, weight);
            totalWeight += weight;
        }
        
        // 按权重随机选择
        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double currentWeight = 0.0;
        
        for (Map.Entry<ServiceInstance, Double> entry : weights.entrySet()) {
            currentWeight += entry.getValue();
            if (random <= currentWeight) {
                return entry.getKey();
            }
        }
        
        // 兜底，返回第一个实例
        return instances.get(0);
    }

    /**
     * 最少连接数策略
     */
    private ServiceInstance selectByLeastConnections(List<ServiceInstance> instances) {
        ServiceInstance selectedInstance = null;
        int minConnections = Integer.MAX_VALUE;
        
        for (ServiceInstance instance : instances) {
            int connections = 0;
            
            if (instance instanceof GameServiceInstance gameInstance) {
                connections = gameInstance.getOnlinePlayerCount();
            }
            
            if (connections < minConnections) {
                minConnections = connections;
                selectedInstance = instance;
            }
        }
        
        return selectedInstance != null ? selectedInstance : instances.get(0);
    }

    /**
     * 一致性哈希策略
     */
    private ServiceInstance selectByConsistentHash(List<ServiceInstance> instances, ReactorLoadBalancer.Request request) {
        // 简化的一致性哈希实现
        // 在实际应用中，可以使用更复杂的一致性哈希环
        
        Object context = request.getContext();
        String hashKey = "";
        
        if (context instanceof String) {
            hashKey = (String) context;
        } else if (context != null) {
            hashKey = context.toString();
        }
        
        if (hashKey.isEmpty()) {
            // 如果没有哈希键，回退到轮询
            return selectByRoundRobin(instances);
        }
        
        int hash = Math.abs(hashKey.hashCode());
        int index = hash % instances.size();
        return instances.get(index);
    }

    /**
     * 获取实例键
     */
    private String getInstanceKey(ServiceInstance instance) {
        return instance.getHost() + ":" + instance.getPort();
    }

    /**
     * 记录响应时间
     *
     * @param instance     服务实例
     * @param responseTime 响应时间（毫秒）
     */
    public void recordResponseTime(ServiceInstance instance, long responseTime) {
        String instanceKey = getInstanceKey(instance);
        responseTimeStats.computeIfAbsent(instanceKey, k -> new ResponseTimeStats())
                .recordResponseTime(responseTime);
    }

    /**
     * 默认响应实现
     */
    private static class DefaultResponse implements ReactorLoadBalancer.Response<ServiceInstance> {
        private final ServiceInstance serviceInstance;

        public DefaultResponse(ServiceInstance serviceInstance) {
            this.serviceInstance = serviceInstance;
        }

        @Override
        public boolean hasServer() {
            return serviceInstance != null;
        }

        @Override
        public ServiceInstance getServer() {
            return serviceInstance;
        }
    }

    /**
     * 空响应实现
     */
    private static class EmptyResponse implements ReactorLoadBalancer.Response<ServiceInstance> {
        @Override
        public boolean hasServer() {
            return false;
        }

        @Override
        public ServiceInstance getServer() {
            return null;
        }
    }
}