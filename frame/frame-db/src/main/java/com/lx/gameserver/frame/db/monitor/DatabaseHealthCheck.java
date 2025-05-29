/*
 * 文件名: DatabaseHealthCheck.java
 * 用途: 数据库健康检查
 * 实现内容:
 *   - 数据库连接健康检查
 *   - 主从延迟监控
 *   - 表空间使用率监控
 *   - 死锁检测和告警
 * 技术选型:
 *   - Spring Boot健康检查机制
 *   - 定时任务监控
 *   - JMX指标收集
 * 依赖关系:
 *   - 使用DataSource进行检查
 *   - 配合DatabaseMetrics使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.actuate.health.Health;
// import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据库健康检查
 * <p>
 * 提供全面的数据库健康状态监控，包括连接状态、主从延迟、
 * 表空间使用率、死锁检测等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
@ConditionalOnProperty(prefix = "game.database.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseHealthCheck { // implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthCheck.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 健康状态缓存
     */
    private final Map<String, Object> healthStatus = new ConcurrentHashMap<>();

    /**
     * 检查计数器
     */
    private final AtomicLong checkCount = new AtomicLong(0);

    /**
     * 最后检查时间
     */
    private volatile LocalDateTime lastCheckTime;

    // @Override
    public Map<String, Object> health() {
        try {
            // 执行健康检查
            Map<String, Object> details = performHealthCheck();
            
            // 判断整体健康状态
            boolean isHealthy = (Boolean) details.getOrDefault("connectionHealthy", false);
            
            details.put("status", isHealthy ? "UP" : "DOWN");
            return details;
            
        } catch (Exception e) {
            logger.error("数据库健康检查失败", e);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("status", "DOWN");
            errorDetails.put("error", e.getMessage());
            errorDetails.put("timestamp", LocalDateTime.now());
            return errorDetails;
        }
    }

    /**
     * 执行健康检查
     */
    private Map<String, Object> performHealthCheck() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // 连接检查
            boolean connectionHealthy = checkConnection();
            details.put("connectionHealthy", connectionHealthy);
            
            // 数据库信息
            details.putAll(getDatabaseInfo());
            
            // 表空间使用率
            details.put("tablespaceUsage", getTablespaceUsage());
            
            // 主从延迟
            details.put("replicationLag", getReplicationLag());
            
            // 死锁检测
            details.put("deadlockCount", getDeadlockCount());
            
            // 活跃连接数
            details.put("activeConnections", getActiveConnectionCount());
            
            // 检查统计
            details.put("checkCount", checkCount.incrementAndGet());
            details.put("lastCheckTime", LocalDateTime.now());
            
            this.lastCheckTime = LocalDateTime.now();
            this.healthStatus.putAll(details);
            
        } catch (Exception e) {
            logger.error("健康检查执行失败", e);
            details.put("error", e.getMessage());
            details.put("healthy", false);
        }
        
        return details;
    }

    /**
     * 检查数据库连接
     */
    private boolean checkConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5); // 5秒超时
        } catch (SQLException e) {
            logger.warn("数据库连接检查失败", e);
            return false;
        }
    }

    /**
     * 获取数据库信息
     */
    private Map<String, Object> getDatabaseInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            info.put("databaseProduct", metaData.getDatabaseProductName());
            info.put("databaseVersion", metaData.getDatabaseProductVersion());
            info.put("driverName", metaData.getDriverName());
            info.put("driverVersion", metaData.getDriverVersion());
            info.put("maxConnections", metaData.getMaxConnections());
            
        } catch (SQLException e) {
            logger.warn("获取数据库信息失败", e);
            info.put("error", "无法获取数据库信息: " + e.getMessage());
        }
        
        return info;
    }

    /**
     * 获取表空间使用率
     */
    private Map<String, Object> getTablespaceUsage() {
        Map<String, Object> usage = new HashMap<>();
        
        try {
            // MySQL查询表空间使用情况
            String sql = "SELECT " +
                    "table_schema as 'database_name', " +
                    "ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) as 'size_mb' " +
                    "FROM information_schema.tables " +
                    "GROUP BY table_schema";
            
            jdbcTemplate.query(sql, rs -> {
                String dbName = rs.getString("database_name");
                double sizeMb = rs.getDouble("size_mb");
                usage.put(dbName, sizeMb);
            });
            
        } catch (Exception e) {
            logger.debug("查询表空间使用率失败: {}", e.getMessage());
            usage.put("error", "不支持的数据库类型或权限不足");
        }
        
        return usage;
    }

    /**
     * 获取主从复制延迟
     */
    private Object getReplicationLag() {
        try {
            // MySQL查询主从延迟
            String sql = "SHOW SLAVE STATUS";
            
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> status = new HashMap<>();
                status.put("secondsBehindMaster", rs.getObject("Seconds_Behind_Master"));
                status.put("slaveIORunning", rs.getString("Slave_IO_Running"));
                status.put("slaveSQLRunning", rs.getString("Slave_SQL_Running"));
                return status;
            });
            
        } catch (Exception e) {
            logger.debug("查询主从延迟失败: {}", e.getMessage());
            return "不支持或非从库";
        }
    }

    /**
     * 获取死锁数量
     */
    private long getDeadlockCount() {
        try {
            // MySQL查询死锁信息
            String sql = "SELECT COUNT(*) FROM information_schema.INNODB_LOCKS";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            logger.debug("查询死锁数量失败: {}", e.getMessage());
            return -1; // 表示无法获取
        }
    }

    /**
     * 获取活跃连接数
     */
    private int getActiveConnectionCount() {
        try {
            String sql = "SHOW STATUS LIKE 'Threads_connected'";
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getInt("Value"));
            
        } catch (Exception e) {
            logger.debug("查询活跃连接数失败: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 定时健康检查（每30秒）
     */
    @Scheduled(fixedRate = 30000)
    public void scheduledHealthCheck() {
        try {
            Map<String, Object> status = performHealthCheck();
            
            // 检查是否有异常情况需要告警
            checkAndAlert(status);
            
        } catch (Exception e) {
            logger.error("定时健康检查失败", e);
        }
    }

    /**
     * 检查并告警
     */
    private void checkAndAlert(Map<String, Object> status) {
        // 连接健康检查
        Boolean connectionHealthy = (Boolean) status.get("connectionHealthy");
        if (connectionHealthy != null && !connectionHealthy) {
            logger.error("数据库连接异常！");
        }
        
        // 主从延迟检查
        Object replicationLag = status.get("replicationLag");
        if (replicationLag instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lagInfo = (Map<String, Object>) replicationLag;
            Object secondsBehind = lagInfo.get("secondsBehindMaster");
            if (secondsBehind instanceof Number && ((Number) secondsBehind).intValue() > 10) {
                logger.warn("主从复制延迟过高: {} 秒", secondsBehind);
            }
        }
        
        // 死锁检查
        Long deadlockCount = (Long) status.get("deadlockCount");
        if (deadlockCount != null && deadlockCount > 0) {
            logger.warn("检测到数据库死锁: {} 个", deadlockCount);
        }
    }

    /**
     * 获取当前健康状态
     */
    public Map<String, Object> getCurrentStatus() {
        return new HashMap<>(healthStatus);
    }

    /**
     * 获取最后检查时间
     */
    public LocalDateTime getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * 获取检查次数
     */
    public long getCheckCount() {
        return checkCount.get();
    }
}