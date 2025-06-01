/*
 * 文件名: SlaveHealthChecker.java
 * 用途: 从库健康检查器
 * 实现内容:
 *   - 定期检查从库健康状态，每10秒执行一次
 *   - 使用SELECT 1测试连接可用性
 *   - 故障从库自动标记为不可用状态
 *   - 从库恢复后自动加入可用列表
 *   - 提供手动刷新健康状态的接口
 * 技术选型:
 *   - ScheduledExecutorService定时任务
 *   - JDBC连接测试
 *   - 线程安全的状态管理
 * 依赖关系:
 *   - 被DynamicDataSource使用
 *   - 依赖DataSource进行健康检查
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从库健康检查器
 * <p>
 * 定期检查从库的健康状态，自动识别故障从库并进行标记。
 * 支持故障恢复检测，确保读写分离的稳定性和可靠性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class SlaveHealthChecker implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SlaveHealthChecker.class);

    /**
     * 健康检查间隔（秒）
     */
    private static final int CHECK_INTERVAL_SECONDS = 10;

    /**
     * 健康检查SQL
     */
    private static final String HEALTH_CHECK_SQL = "SELECT 1";

    /**
     * 连接超时时间（毫秒）
     */
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    /**
     * 查询超时时间（秒）
     */
    private static final int QUERY_TIMEOUT_SECONDS = 3;

    /**
     * 定时执行器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 从库数据源映射
     * Key: 从库名称，Value: 数据源
     */
    private final Map<String, DataSource> slaveDataSources = new ConcurrentHashMap<>();

    /**
     * 从库健康状态映射
     * Key: 从库名称，Value: 是否健康
     */
    private final Map<String, Boolean> healthStatus = new ConcurrentHashMap<>();

    /**
     * 从库最后检查时间
     * Key: 从库名称，Value: 最后检查时间
     */
    private final Map<String, LocalDateTime> lastCheckTime = new ConcurrentHashMap<>();

    /**
     * 从库连续失败次数
     * Key: 从库名称，Value: 连续失败次数
     */
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();

    /**
     * 最大连续失败次数，超过此次数将标记为不可用
     */
    private static final int MAX_FAILURE_COUNT = 3;

    /**
     * 检查器运行状态
     */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Override
    public void afterPropertiesSet() throws Exception {
        startHealthCheck();
    }

    @Override
    public void destroy() throws Exception {
        stopHealthCheck();
    }

    /**
     * 启动健康检查
     */
    public void startHealthCheck() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread thread = new Thread(r, "slave-health-checker");
                thread.setDaemon(true);
                return thread;
            });

            scheduler.scheduleWithFixedDelay(
                this::performHealthCheck,
                0,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );

            logger.info("从库健康检查器启动，检查间隔: {}秒", CHECK_INTERVAL_SECONDS);
        }
    }

    /**
     * 停止健康检查
     */
    public void stopHealthCheck() {
        if (isRunning.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    scheduler.shutdownNow();
                }
            }
            logger.info("从库健康检查器已停止");
        }
    }

    /**
     * 注册从库数据源
     *
     * @param slaveName 从库名称
     * @param dataSource 数据源
     */
    public void registerSlaveDataSource(String slaveName, DataSource dataSource) {
        if (slaveName == null || dataSource == null) {
            logger.warn("注册从库数据源失败，参数不能为空");
            return;
        }

        slaveDataSources.put(slaveName, dataSource);
        healthStatus.put(slaveName, true); // 初始状态为健康
        failureCount.put(slaveName, 0);
        logger.info("注册从库数据源: {}", slaveName);
    }

    /**
     * 移除从库数据源
     *
     * @param slaveName 从库名称
     */
    public void removeSlaveDataSource(String slaveName) {
        slaveDataSources.remove(slaveName);
        healthStatus.remove(slaveName);
        lastCheckTime.remove(slaveName);
        failureCount.remove(slaveName);
        logger.info("移除从库数据源: {}", slaveName);
    }

    /**
     * 检查从库是否健康
     *
     * @param slaveName 从库名称
     * @return 如果健康返回true，否则返回false
     */
    public boolean isSlaveHealthy(String slaveName) {
        return healthStatus.getOrDefault(slaveName, false);
    }

    /**
     * 获取所有健康的从库名称列表
     *
     * @return 健康的从库名称列表
     */
    public java.util.List<String> getHealthySlaves() {
        return healthStatus.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取从库健康状态快照
     *
     * @return 健康状态映射
     */
    public Map<String, HealthStatus> getHealthStatusSnapshot() {
        Map<String, HealthStatus> snapshot = new ConcurrentHashMap<>();
        
        for (String slaveName : slaveDataSources.keySet()) {
            HealthStatus status = new HealthStatus();
            status.slaveName = slaveName;
            status.isHealthy = healthStatus.getOrDefault(slaveName, false);
            status.lastCheckTime = lastCheckTime.get(slaveName);
            status.failureCount = failureCount.getOrDefault(slaveName, 0);
            snapshot.put(slaveName, status);
        }
        
        return snapshot;
    }

    /**
     * 手动刷新所有从库健康状态
     */
    public void refreshHealthStatus() {
        logger.info("手动刷新从库健康状态");
        performHealthCheck();
    }

    /**
     * 手动刷新指定从库健康状态
     *
     * @param slaveName 从库名称
     */
    public void refreshHealthStatus(String slaveName) {
        DataSource dataSource = slaveDataSources.get(slaveName);
        if (dataSource != null) {
            logger.info("手动刷新从库健康状态: {}", slaveName);
            checkSingleSlave(slaveName, dataSource);
        } else {
            logger.warn("从库不存在: {}", slaveName);
        }
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        if (slaveDataSources.isEmpty()) {
            return;
        }

        logger.debug("开始执行从库健康检查，共{}个从库", slaveDataSources.size());
        
        for (Map.Entry<String, DataSource> entry : slaveDataSources.entrySet()) {
            String slaveName = entry.getKey();
            DataSource dataSource = entry.getValue();
            
            try {
                checkSingleSlave(slaveName, dataSource);
            } catch (Exception e) {
                logger.error("检查从库{}时发生异常", slaveName, e);
                markSlaveUnhealthy(slaveName);
            }
        }
        
        logger.debug("从库健康检查完成");
    }

    /**
     * 检查单个从库
     *
     * @param slaveName 从库名称
     * @param dataSource 数据源
     */
    private void checkSingleSlave(String slaveName, DataSource dataSource) {
        boolean isHealthy = false;
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setNetworkTimeout(Executors.newSingleThreadExecutor(), CONNECTION_TIMEOUT_MS);
            
            try (PreparedStatement statement = connection.prepareStatement(HEALTH_CHECK_SQL)) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) == 1) {
                        isHealthy = true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("从库{}健康检查失败: {}", slaveName, e.getMessage());
        }

        long checkTime = System.currentTimeMillis() - startTime;
        lastCheckTime.put(slaveName, LocalDateTime.now());

        if (isHealthy) {
            markSlaveHealthy(slaveName);
            logger.debug("从库{}健康检查通过，耗时{}ms", slaveName, checkTime);
        } else {
            markSlaveUnhealthy(slaveName);
            logger.warn("从库{}健康检查失败，耗时{}ms", slaveName, checkTime);
        }
    }

    /**
     * 标记从库为健康状态
     *
     * @param slaveName 从库名称
     */
    private void markSlaveHealthy(String slaveName) {
        boolean wasUnhealthy = !healthStatus.getOrDefault(slaveName, true);
        healthStatus.put(slaveName, true);
        failureCount.put(slaveName, 0);

        if (wasUnhealthy) {
            logger.info("从库{}恢复健康状态", slaveName);
        }
    }

    /**
     * 标记从库为不健康状态
     *
     * @param slaveName 从库名称
     */
    private void markSlaveUnhealthy(String slaveName) {
        int currentFailureCount = failureCount.getOrDefault(slaveName, 0) + 1;
        failureCount.put(slaveName, currentFailureCount);

        if (currentFailureCount >= MAX_FAILURE_COUNT) {
            boolean wasHealthy = healthStatus.getOrDefault(slaveName, true);
            healthStatus.put(slaveName, false);

            if (wasHealthy) {
                logger.error("从库{}连续失败{}次，标记为不可用", slaveName, currentFailureCount);
            }
        } else {
            logger.warn("从库{}健康检查失败，当前连续失败次数: {}", slaveName, currentFailureCount);
        }
    }

    /**
     * 健康状态信息
     */
    public static class HealthStatus {
        public String slaveName;
        public boolean isHealthy;
        public LocalDateTime lastCheckTime;
        public int failureCount;

        @Override
        public String toString() {
            return "HealthStatus{" +
                    "slaveName='" + slaveName + '\'' +
                    ", isHealthy=" + isHealthy +
                    ", lastCheckTime=" + lastCheckTime +
                    ", failureCount=" + failureCount +
                    '}';
        }
    }
}