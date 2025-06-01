/*
 * 文件名: DatabaseMetrics.java
 * 用途: 数据库监控指标收集器
 * 实现内容:
 *   - 连接池监控：活跃连接数、空闲连接数、等待数
 *   - SQL监控：执行次数、平均耗时、慢查询
 *   - 事务监控：事务数量、回滚率、平均耗时
 *   - 缓存监控：命中率、加载耗时
 *   - 支持Prometheus指标导出
 * 技术选型:
 *   - Micrometer指标框架
 *   - 定时任务收集指标
 *   - HikariCP连接池监控
 * 依赖关系:
 *   - 依赖数据源配置
 *   - 导出给监控系统
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.monitor;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 数据库监控指标收集器
 * <p>
 * 收集数据库连接池、SQL执行、事务等各种监控指标，
 * 支持导出到Prometheus等监控系统。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class DatabaseMetrics implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMetrics.class);

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * SQL执行计数器
     */
    private final Map<String, Counter> sqlCounters = new ConcurrentHashMap<>();

    /**
     * SQL执行时间计时器
     */
    private final Map<String, Timer> sqlTimers = new ConcurrentHashMap<>();

    /**
     * 事务统计
     */
    private final LongAdder transactionTotal = new LongAdder();
    private final LongAdder transactionCommit = new LongAdder();
    private final LongAdder transactionRollback = new LongAdder();

    /**
     * 慢查询统计
     */
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    private final AtomicLong slowQueryThreshold = new AtomicLong(1000); // 1秒

    /**
     * 连接池监控
     */
    private HikariPoolMXBean hikariPoolMXBean;

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeMetrics();
        registerConnectionPoolMetrics();
        logger.info("数据库监控指标初始化完成");
    }

    /**
     * 初始化指标
     */
    private void initializeMetrics() {
        // 注册事务指标
        Gauge.builder("db.transaction.total", transactionTotal, adder -> adder.sum())
                .description("总事务数")
                .register(meterRegistry);

        Gauge.builder("db.transaction.commit", transactionCommit, adder -> adder.sum())
                .description("提交事务数")
                .register(meterRegistry);

        Gauge.builder("db.transaction.rollback", transactionRollback, adder -> adder.sum())
                .description("回滚事务数")
                .register(meterRegistry);

        // 注册慢查询指标
        Gauge.builder("db.slow.query.count", slowQueryCount, AtomicLong::doubleValue)
                .description("慢查询数量")
                .register(meterRegistry);
    }

    /**
     * 注册连接池监控指标
     */
    private void registerConnectionPoolMetrics() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();

            // 活跃连接数
            Gauge.builder("db.pool.active.connections", this, metrics -> 
                            metrics.hikariPoolMXBean != null ? (double) metrics.hikariPoolMXBean.getActiveConnections() : 0.0)
                    .description("活跃连接数")
                    .register(meterRegistry);

            // 空闲连接数
            Gauge.builder("db.pool.idle.connections", this, metrics -> 
                            metrics.hikariPoolMXBean != null ? (double) metrics.hikariPoolMXBean.getIdleConnections() : 0.0)
                    .description("空闲连接数")
                    .register(meterRegistry);

            // 等待连接数
            Gauge.builder("db.pool.pending.threads", this, metrics -> 
                            metrics.hikariPoolMXBean != null ? (double) metrics.hikariPoolMXBean.getThreadsAwaitingConnection() : 0.0)
                    .description("等待连接的线程数")
                    .register(meterRegistry);

            // 总连接数
            Gauge.builder("db.pool.total.connections", this, metrics -> 
                            metrics.hikariPoolMXBean != null ? (double) metrics.hikariPoolMXBean.getTotalConnections() : 0.0)
                    .description("总连接数")
                    .register(meterRegistry);
        }
    }

    /**
     * 记录SQL执行
     *
     * @param sqlType SQL类型（SELECT, INSERT, UPDATE, DELETE）
     * @param executionTime 执行时间（毫秒）
     */
    public void recordSqlExecution(String sqlType, long executionTime) {
        // 记录SQL执行次数
        sqlCounters.computeIfAbsent(sqlType, type -> 
                Counter.builder("db.sql.execution.count")
                        .description("SQL执行次数")
                        .tag("type", type)
                        .register(meterRegistry))
                .increment();

        // 记录SQL执行时间
        sqlTimers.computeIfAbsent(sqlType, type -> 
                Timer.builder("db.sql.execution.time")
                        .description("SQL执行时间")
                        .tag("type", type)
                        .register(meterRegistry))
                .record(executionTime, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 检查是否为慢查询
        if (executionTime > slowQueryThreshold.get()) {
            slowQueryCount.incrementAndGet();
            logger.warn("检测到慢查询: 类型={}, 耗时={}ms", sqlType, executionTime);
        }
    }

    /**
     * 记录事务提交
     */
    public void recordTransactionCommit() {
        transactionTotal.increment();
        transactionCommit.increment();
    }

    /**
     * 记录事务回滚
     */
    public void recordTransactionRollback() {
        transactionTotal.increment();
        transactionRollback.increment();
    }

    /**
     * 获取连接池状态
     *
     * @return 连接池状态信息
     */
    public ConnectionPoolStatus getConnectionPoolStatus() {
        if (hikariPoolMXBean == null) {
            return new ConnectionPoolStatus();
        }

        ConnectionPoolStatus status = new ConnectionPoolStatus();
        status.activeConnections = hikariPoolMXBean.getActiveConnections();
        status.idleConnections = hikariPoolMXBean.getIdleConnections();
        status.totalConnections = hikariPoolMXBean.getTotalConnections();
        status.threadsAwaitingConnection = hikariPoolMXBean.getThreadsAwaitingConnection();
        status.timestamp = LocalDateTime.now();

        return status;
    }

    /**
     * 获取SQL执行统计
     *
     * @return SQL执行统计信息
     */
    public Map<String, SqlExecutionStats> getSqlExecutionStats() {
        Map<String, SqlExecutionStats> stats = new ConcurrentHashMap<>();

        for (Map.Entry<String, Counter> entry : sqlCounters.entrySet()) {
            String sqlType = entry.getKey();
            Counter counter = entry.getValue();
            Timer timer = sqlTimers.get(sqlType);

            SqlExecutionStats stat = new SqlExecutionStats();
            stat.sqlType = sqlType;
            stat.executionCount = (long) counter.count();
            if (timer != null) {
                stat.totalExecutionTime = (long) timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
                stat.averageExecutionTime = stat.executionCount > 0 ? 
                        stat.totalExecutionTime / stat.executionCount : 0;
                stat.maxExecutionTime = (long) timer.max(java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            stats.put(sqlType, stat);
        }

        return stats;
    }

    /**
     * 获取事务统计
     *
     * @return 事务统计信息
     */
    public TransactionStats getTransactionStats() {
        TransactionStats stats = new TransactionStats();
        stats.totalTransactions = transactionTotal.sum();
        stats.commitTransactions = transactionCommit.sum();
        stats.rollbackTransactions = transactionRollback.sum();
        stats.rollbackRate = stats.totalTransactions > 0 ? 
                (double) stats.rollbackTransactions / stats.totalTransactions : 0.0;
        stats.timestamp = LocalDateTime.now();

        return stats;
    }

    /**
     * 定时收集指标
     */
    @Scheduled(fixedRate = 30000) // 每30秒执行一次
    public void collectMetrics() {
        try {
            ConnectionPoolStatus poolStatus = getConnectionPoolStatus();
            logger.debug("连接池状态: 活跃={}, 空闲={}, 总数={}, 等待={}", 
                    poolStatus.activeConnections, poolStatus.idleConnections, 
                    poolStatus.totalConnections, poolStatus.threadsAwaitingConnection);

            TransactionStats transactionStats = getTransactionStats();
            logger.debug("事务统计: 总数={}, 提交={}, 回滚={}, 回滚率={}", 
                    transactionStats.totalTransactions, transactionStats.commitTransactions,
                    transactionStats.rollbackTransactions, transactionStats.rollbackRate);

        } catch (Exception e) {
            logger.error("收集数据库指标异常", e);
        }
    }

    /**
     * 设置慢查询阈值
     *
     * @param thresholdMs 阈值（毫秒）
     */
    public void setSlowQueryThreshold(long thresholdMs) {
        slowQueryThreshold.set(thresholdMs);
        logger.info("慢查询阈值设置为: {}ms", thresholdMs);
    }

    /**
     * 重置所有统计数据
     */
    public void resetStats() {
        transactionTotal.reset();
        transactionCommit.reset();
        transactionRollback.reset();
        slowQueryCount.set(0);
        sqlCounters.clear();
        sqlTimers.clear();
        logger.info("数据库监控统计数据已重置");
    }

    /**
     * 连接池状态信息
     */
    public static class ConnectionPoolStatus {
        public int activeConnections = 0;
        public int idleConnections = 0;
        public int totalConnections = 0;
        public int threadsAwaitingConnection = 0;
        public LocalDateTime timestamp;

        @Override
        public String toString() {
            return "ConnectionPoolStatus{" +
                    "activeConnections=" + activeConnections +
                    ", idleConnections=" + idleConnections +
                    ", totalConnections=" + totalConnections +
                    ", threadsAwaitingConnection=" + threadsAwaitingConnection +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * SQL执行统计信息
     */
    public static class SqlExecutionStats {
        public String sqlType;
        public long executionCount = 0;
        public long totalExecutionTime = 0;
        public long averageExecutionTime = 0;
        public long maxExecutionTime = 0;

        @Override
        public String toString() {
            return "SqlExecutionStats{" +
                    "sqlType='" + sqlType + '\'' +
                    ", executionCount=" + executionCount +
                    ", totalExecutionTime=" + totalExecutionTime +
                    ", averageExecutionTime=" + averageExecutionTime +
                    ", maxExecutionTime=" + maxExecutionTime +
                    '}';
        }
    }

    /**
     * 事务统计信息
     */
    public static class TransactionStats {
        public long totalTransactions = 0;
        public long commitTransactions = 0;
        public long rollbackTransactions = 0;
        public double rollbackRate = 0.0;
        public LocalDateTime timestamp;

        @Override
        public String toString() {
            return "TransactionStats{" +
                    "totalTransactions=" + totalTransactions +
                    ", commitTransactions=" + commitTransactions +
                    ", rollbackTransactions=" + rollbackTransactions +
                    ", rollbackRate=" + rollbackRate +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}