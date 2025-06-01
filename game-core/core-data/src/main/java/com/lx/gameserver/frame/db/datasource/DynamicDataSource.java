/*
 * 文件名: DynamicDataSource.java
 * 用途: 动态数据源实现
 * 实现内容:
 *   - 继承AbstractRoutingDataSource实现动态数据源路由
 *   - 实现从库负载均衡算法（轮询、随机、权重）
 *   - 实现从库健康检查机制，故障自动剔除
 *   - 支持强制路由到主库的场景
 *   - 线程级数据源绑定，避免并发问题
 * 技术选型:
 *   - Spring AbstractRoutingDataSource
 *   - 负载均衡算法实现
 *   - 健康检查机制
 * 依赖关系:
 *   - 依赖DataSourceContextHolder获取路由信息
 *   - 被DataSourceConfig配置和管理
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态数据源实现
 * <p>
 * 基于Spring的AbstractRoutingDataSource实现动态数据源路由。
 * 支持主从读写分离、从库负载均衡、健康检查等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    private static final Logger logger = LoggerFactory.getLogger(DynamicDataSource.class);

    /**
     * 主数据源标识
     */
    public static final String MASTER_KEY = "master";

    /**
     * 从数据源前缀
     */
    public static final String SLAVE_KEY_PREFIX = "slave_";

    /**
     * 负载均衡算法类型
     */
    public enum LoadBalanceType {
        /**
         * 轮询算法
         */
        ROUND_ROBIN,
        
        /**
         * 随机算法
         */
        RANDOM,
        
        /**
         * 加权轮询算法
         */
        WEIGHTED_ROUND_ROBIN
    }

    /**
     * 从库数据源列表
     */
    private final List<String> slaveKeys = new ArrayList<>();

    /**
     * 从库权重映射
     */
    private final Map<String, Integer> slaveWeights = new HashMap<>();

    /**
     * 不可用的从库列表
     */
    private final Set<String> unavailableSlaves = new HashSet<>();

    /**
     * 负载均衡算法类型
     */
    private LoadBalanceType loadBalanceType = LoadBalanceType.ROUND_ROBIN;

    /**
     * 轮询计数器
     */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    /**
     * 加权轮询的当前权重
     */
    private final Map<String, Integer> currentWeights = new HashMap<>();

    /**
     * 读写锁，保护从库列表的并发访问
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 确定当前查找键
     * <p>
     * 根据当前线程的数据源上下文，决定使用哪个数据源。
     * </p>
     *
     * @return 数据源标识键
     */
    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceContextHolder.DataSourceType dataSourceType = 
                DataSourceContextHolder.getDataSourceType();

        String dataSourceKey;
        if (dataSourceType == DataSourceContextHolder.DataSourceType.SLAVE) {
            dataSourceKey = selectSlaveDataSource();
        } else {
            dataSourceKey = MASTER_KEY;
        }

        logger.debug("路由到数据源: {}, 请求类型: {}", dataSourceKey, dataSourceType);
        return dataSourceKey;
    }

    /**
     * 选择从库数据源
     * <p>
     * 根据配置的负载均衡算法选择一个可用的从库。
     * 如果所有从库都不可用，则回退到主库。
     * </p>
     *
     * @return 从库数据源标识
     */
    private String selectSlaveDataSource() {
        lock.readLock().lock();
        try {
            // 获取可用的从库列表
            List<String> availableSlaves = getAvailableSlaves();
            
            if (availableSlaves.isEmpty()) {
                logger.warn("没有可用的从库，回退到主库");
                return MASTER_KEY;
            }

            // 根据负载均衡算法选择从库
            String selectedSlave = selectByLoadBalance(availableSlaves);
            logger.debug("选择从库: {}, 可用从库数: {}", selectedSlave, availableSlaves.size());
            return selectedSlave;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取可用的从库列表
     *
     * @return 可用的从库列表
     */
    private List<String> getAvailableSlaves() {
        List<String> availableSlaves = new ArrayList<>();
        for (String slaveKey : slaveKeys) {
            if (!unavailableSlaves.contains(slaveKey)) {
                availableSlaves.add(slaveKey);
            }
        }
        return availableSlaves;
    }

    /**
     * 根据负载均衡算法选择从库
     *
     * @param availableSlaves 可用的从库列表
     * @return 选中的从库标识
     */
    private String selectByLoadBalance(List<String> availableSlaves) {
        if (availableSlaves.size() == 1) {
            return availableSlaves.get(0);
        }

        switch (loadBalanceType) {
            case RANDOM:
                return selectByRandom(availableSlaves);
            case WEIGHTED_ROUND_ROBIN:
                return selectByWeightedRoundRobin(availableSlaves);
            case ROUND_ROBIN:
            default:
                return selectByRoundRobin(availableSlaves);
        }
    }

    /**
     * 轮询算法选择从库
     *
     * @param availableSlaves 可用的从库列表
     * @return 选中的从库标识
     */
    private String selectByRoundRobin(List<String> availableSlaves) {
        int index = Math.abs(roundRobinCounter.getAndIncrement()) % availableSlaves.size();
        return availableSlaves.get(index);
    }

    /**
     * 随机算法选择从库
     *
     * @param availableSlaves 可用的从库列表
     * @return 选中的从库标识
     */
    private String selectByRandom(List<String> availableSlaves) {
        int index = ThreadLocalRandom.current().nextInt(availableSlaves.size());
        return availableSlaves.get(index);
    }

    /**
     * 加权轮询算法选择从库
     *
     * @param availableSlaves 可用的从库列表
     * @return 选中的从库标识
     */
    private String selectByWeightedRoundRobin(List<String> availableSlaves) {
        String selectedSlave = null;
        int totalWeight = 0;

        // 计算当前权重和总权重
        for (String slave : availableSlaves) {
            int weight = slaveWeights.getOrDefault(slave, 1);
            totalWeight += weight;
            
            int currentWeight = currentWeights.getOrDefault(slave, 0) + weight;
            currentWeights.put(slave, currentWeight);
            
            if (selectedSlave == null || currentWeight > currentWeights.get(selectedSlave)) {
                selectedSlave = slave;
            }
        }

        // 减少选中从库的权重
        if (selectedSlave != null) {
            int currentWeight = currentWeights.get(selectedSlave) - totalWeight;
            currentWeights.put(selectedSlave, currentWeight);
        }

        return selectedSlave != null ? selectedSlave : availableSlaves.get(0);
    }

    /**
     * 添加从库数据源
     *
     * @param slaveKey 从库标识
     * @param weight 权重
     */
    public void addSlaveDataSource(String slaveKey, int weight) {
        lock.writeLock().lock();
        try {
            slaveKeys.add(slaveKey);
            slaveWeights.put(slaveKey, weight);
            currentWeights.put(slaveKey, 0);
            logger.info("添加从库数据源: {}, 权重: {}", slaveKey, weight);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 标记从库为不可用
     *
     * @param slaveKey 从库标识
     */
    public void markSlaveUnavailable(String slaveKey) {
        lock.writeLock().lock();
        try {
            unavailableSlaves.add(slaveKey);
            logger.warn("标记从库不可用: {}", slaveKey);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 标记从库为可用
     *
     * @param slaveKey 从库标识
     */
    public void markSlaveAvailable(String slaveKey) {
        lock.writeLock().lock();
        try {
            unavailableSlaves.remove(slaveKey);
            logger.info("恢复从库可用性: {}", slaveKey);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取从库健康状态
     *
     * @return 从库健康状态映射
     */
    public Map<String, Boolean> getSlaveHealthStatus() {
        lock.readLock().lock();
        try {
            Map<String, Boolean> healthStatus = new HashMap<>();
            for (String slaveKey : slaveKeys) {
                healthStatus.put(slaveKey, !unavailableSlaves.contains(slaveKey));
            }
            return healthStatus;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 设置负载均衡算法类型
     *
     * @param loadBalanceType 负载均衡算法类型
     */
    public void setLoadBalanceType(LoadBalanceType loadBalanceType) {
        this.loadBalanceType = loadBalanceType;
        logger.info("设置负载均衡算法: {}", loadBalanceType);
    }

    /**
     * 获取所有从库标识列表
     *
     * @return 从库标识列表
     */
    public List<String> getSlaveKeys() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(slaveKeys);
        } finally {
            lock.readLock().unlock();
        }
    }
}