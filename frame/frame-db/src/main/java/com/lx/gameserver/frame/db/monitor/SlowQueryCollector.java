/*
 * 文件名: SlowQueryCollector.java
 * 用途: 慢查询收集器
 * 实现内容:
 *   - 收集执行时间超过阈值的SQL
 *   - 记录SQL语句、执行时间、调用栈
 *   - 支持慢查询TOP N统计
 *   - 定期生成慢查询报告
 * 技术选型:
 *   - 内存环形缓冲区
 *   - 定时任务分析
 *   - 线程安全的统计结构
 * 依赖关系:
 *   - 被MyBatis拦截器调用
 *   - 配合DatabaseMetrics使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 慢查询收集器
 * <p>
 * 收集和分析数据库慢查询，提供详细的性能分析报告。
 * 支持实时监控和历史统计。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class SlowQueryCollector {

    private static final Logger logger = LoggerFactory.getLogger(SlowQueryCollector.class);

    /**
     * 慢查询阈值（毫秒）
     */
    private volatile long slowQueryThreshold = 1000;

    /**
     * 最大保存的慢查询记录数
     */
    private static final int MAX_SLOW_QUERIES = 1000;

    /**
     * 慢查询记录队列
     */
    private final ConcurrentLinkedQueue<SlowQueryRecord> slowQueries = new ConcurrentLinkedQueue<>();

    /**
     * 慢查询统计映射
     * Key: SQL模式（去掉参数），Value: 统计信息
     */
    private final Map<String, SlowQueryStats> slowQueryStatsMap = new ConcurrentHashMap<>();

    /**
     * 总慢查询计数
     */
    private final AtomicLong totalSlowQueryCount = new AtomicLong(0);

    /**
     * 记录慢查询
     *
     * @param sql SQL语句
     * @param executionTime 执行时间（毫秒）
     * @param parameters 参数
     */
    public void recordSlowQuery(String sql, long executionTime, Object parameters) {
        if (executionTime < slowQueryThreshold) {
            return;
        }

        try {
            // 创建慢查询记录
            SlowQueryRecord record = new SlowQueryRecord();
            record.sql = sql;
            record.executionTime = executionTime;
            record.parameters = parameters != null ? parameters.toString() : "";
            record.timestamp = LocalDateTime.now();
            record.stackTrace = captureStackTrace();

            // 添加到队列
            slowQueries.offer(record);
            
            // 保持队列大小
            while (slowQueries.size() > MAX_SLOW_QUERIES) {
                slowQueries.poll();
            }

            // 更新统计信息
            updateSlowQueryStats(sql, executionTime);
            
            // 增加总计数
            totalSlowQueryCount.incrementAndGet();

            logger.warn("检测到慢查询: SQL={}, 耗时={}ms", normalizeSql(sql), executionTime);

        } catch (Exception e) {
            logger.error("记录慢查询失败", e);
        }
    }

    /**
     * 更新慢查询统计
     */
    private void updateSlowQueryStats(String sql, long executionTime) {
        String normalizedSql = normalizeSql(sql);
        
        slowQueryStatsMap.compute(normalizedSql, (key, stats) -> {
            if (stats == null) {
                stats = new SlowQueryStats();
                stats.sqlPattern = normalizedSql;
            }
            
            stats.count.incrementAndGet();
            stats.totalTime.addAndGet(executionTime);
            
            // 更新最大执行时间
            long currentMax = stats.maxTime.get();
            while (executionTime > currentMax) {
                if (stats.maxTime.compareAndSet(currentMax, executionTime)) {
                    break;
                }
                currentMax = stats.maxTime.get();
            }
            
            // 更新最小执行时间
            long currentMin = stats.minTime.get();
            if (currentMin == 0 || executionTime < currentMin) {
                stats.minTime.set(executionTime);
            }
            
            return stats;
        });
    }

    /**
     * 标准化SQL（去掉参数值）
     */
    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        
        // 简单的SQL标准化：替换参数占位符
        return sql.replaceAll("'[^']*'", "?")
                 .replaceAll("\\d+", "?")
                 .replaceAll("\\s+", " ")
                 .trim();
    }

    /**
     * 捕获调用栈
     */
    private String captureStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        
        // 跳过前几个无关的栈帧
        for (int i = 3; i < Math.min(stackTrace.length, 8); i++) {
            StackTraceElement element = stackTrace[i];
            sb.append(element.getClassName())
              .append(".")
              .append(element.getMethodName())
              .append("(")
              .append(element.getFileName())
              .append(":")
              .append(element.getLineNumber())
              .append(")\n");
        }
        
        return sb.toString();
    }

    /**
     * 获取慢查询TOP N
     *
     * @param topN 前N个
     * @return 慢查询统计列表
     */
    public List<SlowQueryStats> getTopSlowQueries(int topN) {
        return slowQueryStatsMap.values().stream()
                .sorted((a, b) -> Long.compare(b.totalTime.get(), a.totalTime.get()))
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 获取最近的慢查询记录
     *
     * @param limit 记录数限制
     * @return 慢查询记录列表
     */
    public List<SlowQueryRecord> getRecentSlowQueries(int limit) {
        return slowQueries.stream()
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取慢查询总数
     *
     * @return 慢查询总数
     */
    public long getTotalSlowQueryCount() {
        return totalSlowQueryCount.get();
    }

    /**
     * 获取慢查询阈值
     *
     * @return 阈值（毫秒）
     */
    public long getSlowQueryThreshold() {
        return slowQueryThreshold;
    }

    /**
     * 设置慢查询阈值
     *
     * @param thresholdMs 阈值（毫秒）
     */
    public void setSlowQueryThreshold(long thresholdMs) {
        this.slowQueryThreshold = thresholdMs;
        logger.info("慢查询阈值设置为: {}ms", thresholdMs);
    }

    /**
     * 生成慢查询报告
     *
     * @return 慢查询报告
     */
    public SlowQueryReport generateReport() {
        SlowQueryReport report = new SlowQueryReport();
        report.totalSlowQueries = totalSlowQueryCount.get();
        report.slowQueryThreshold = slowQueryThreshold;
        report.reportTime = LocalDateTime.now();
        report.topSlowQueries = getTopSlowQueries(10);
        report.recentSlowQueries = getRecentSlowQueries(20);
        
        return report;
    }

    /**
     * 定期生成慢查询报告
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void generatePeriodicReport() {
        try {
            if (totalSlowQueryCount.get() == 0) {
                return;
            }

            SlowQueryReport report = generateReport();
            logger.info("慢查询报告: 总数={}, 阈值={}ms, TOP慢查询数={}", 
                    report.totalSlowQueries, report.slowQueryThreshold, report.topSlowQueries.size());

            // 如果慢查询过多，发出警告
            if (report.totalSlowQueries > 100) {
                logger.warn("慢查询数量过多: {}, 建议检查SQL性能", report.totalSlowQueries);
            }

        } catch (Exception e) {
            logger.error("生成慢查询报告失败", e);
        }
    }

    /**
     * 清理过期数据
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void cleanupExpiredData() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            
            // 清理过期的慢查询记录
            slowQueries.removeIf(record -> record.timestamp.isBefore(cutoffTime));
            
            logger.debug("清理过期慢查询数据完成");

        } catch (Exception e) {
            logger.error("清理过期慢查询数据失败", e);
        }
    }

    /**
     * 重置统计数据
     */
    public void resetStats() {
        slowQueries.clear();
        slowQueryStatsMap.clear();
        totalSlowQueryCount.set(0);
        logger.info("慢查询统计数据已重置");
    }

    /**
     * 慢查询记录
     */
    public static class SlowQueryRecord {
        public String sql;
        public long executionTime;
        public String parameters;
        public LocalDateTime timestamp;
        public String stackTrace;

        @Override
        public String toString() {
            return "SlowQueryRecord{" +
                    "sql='" + sql + '\'' +
                    ", executionTime=" + executionTime +
                    ", parameters='" + parameters + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * 慢查询统计信息
     */
    public static class SlowQueryStats {
        public String sqlPattern;
        public AtomicLong count = new AtomicLong(0);
        public AtomicLong totalTime = new AtomicLong(0);
        public AtomicLong maxTime = new AtomicLong(0);
        public AtomicLong minTime = new AtomicLong(0);

        public long getAverageTime() {
            long c = count.get();
            return c > 0 ? totalTime.get() / c : 0;
        }

        @Override
        public String toString() {
            return "SlowQueryStats{" +
                    "sqlPattern='" + sqlPattern + '\'' +
                    ", count=" + count.get() +
                    ", totalTime=" + totalTime.get() +
                    ", maxTime=" + maxTime.get() +
                    ", minTime=" + minTime.get() +
                    ", averageTime=" + getAverageTime() +
                    '}';
        }
    }

    /**
     * 慢查询报告
     */
    public static class SlowQueryReport {
        public long totalSlowQueries;
        public long slowQueryThreshold;
        public LocalDateTime reportTime;
        public List<SlowQueryStats> topSlowQueries;
        public List<SlowQueryRecord> recentSlowQueries;

        @Override
        public String toString() {
            return "SlowQueryReport{" +
                    "totalSlowQueries=" + totalSlowQueries +
                    ", slowQueryThreshold=" + slowQueryThreshold +
                    ", reportTime=" + reportTime +
                    ", topSlowQueries=" + topSlowQueries.size() +
                    ", recentSlowQueries=" + recentSlowQueries.size() +
                    '}';
        }
    }
}