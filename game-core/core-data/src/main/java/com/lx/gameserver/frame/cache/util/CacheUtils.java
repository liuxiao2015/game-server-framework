/*
 * 文件名: CacheUtils.java
 * 用途: 缓存工具类
 * 实现内容:
 *   - 键值转换工具
 *   - 过期时间计算
 *   - 容量计算辅助
 *   - 性能测试工具
 *   - 调试和诊断工具
 * 技术选型:
 *   - 静态工具方法
 *   - 泛型支持
 *   - 性能优化
 *   - 线程安全
 * 依赖关系:
 *   - 被缓存组件使用
 *   - 提供通用工具方法
 *   - 无外部依赖
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.util;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 缓存工具类
 * <p>
 * 提供缓存相关的通用工具方法，包括键值转换、时间计算、
 * 容量管理、性能测试等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class CacheUtils {

    private static final Logger logger = LoggerFactory.getLogger(CacheUtils.class);

    /**
     * 默认日期时间格式
     */
    private static final DateTimeFormatter DEFAULT_DATETIME_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 内存管理Bean
     */
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    /**
     * 键名模式（字母、数字、下划线、冒号、点）
     */
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_:.\\-]+$");

    /**
     * 私有构造函数，防止实例化
     */
    private CacheUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ================ 键值转换工具 ================

    /**
     * 安全地转换对象为字符串键
     *
     * @param obj 对象
     * @return 字符串键
     */
    public static String safeToString(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return (String) obj;
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        // 对于复杂对象，使用类名和哈希码
        return obj.getClass().getSimpleName() + "@" + Integer.toHexString(obj.hashCode());
    }

    /**
     * 验证键名的有效性
     *
     * @param key 键名
     * @return 是否有效
     */
    public static boolean isValidKey(String key) {
        return key != null && !key.trim().isEmpty() && VALID_KEY_PATTERN.matcher(key).matches();
    }

    /**
     * 规范化键名
     *
     * @param key 原始键名
     * @return 规范化后的键名
     */
    public static String normalizeKey(String key) {
        if (key == null) {
            return "null";
        }
        
        // 去除首尾空格，转换为小写
        key = key.trim().toLowerCase();
        
        // 替换非法字符为下划线
        key = key.replaceAll("[^a-zA-Z0-9_:.\\-]", "_");
        
        // 去除连续的下划线
        key = key.replaceAll("_+", "_");
        
        // 去除首尾下划线
        key = key.replaceAll("^_+|_+$", "");
        
        return key.isEmpty() ? "empty" : key;
    }

    /**
     * 组合多个键片段
     *
     * @param separator 分隔符
     * @param segments  键片段
     * @return 组合后的键
     */
    public static String combineKeys(String separator, Object... segments) {
        if (segments == null || segments.length == 0) {
            return "";
        }
        
        StringJoiner joiner = new StringJoiner(separator);
        for (Object segment : segments) {
            if (segment != null) {
                joiner.add(safeToString(segment));
            }
        }
        
        return joiner.toString();
    }

    // ================ 时间计算工具 ================

    /**
     * 计算过期时间
     *
     * @param duration 持续时间
     * @return 过期时间点
     */
    public static Instant calculateExpiration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return null;
        }
        
        return Instant.now().plus(duration);
    }

    /**
     * 计算剩余时间
     *
     * @param expiration 过期时间
     * @return 剩余时间，如果已过期返回零
     */
    public static Duration calculateRemainingTime(Instant expiration) {
        if (expiration == null) {
            return Duration.ZERO;
        }
        
        Duration remaining = Duration.between(Instant.now(), expiration);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * 检查是否已过期
     *
     * @param expiration 过期时间
     * @return 是否已过期
     */
    public static boolean isExpired(Instant expiration) {
        return expiration != null && Instant.now().isAfter(expiration);
    }

    /**
     * 添加随机抖动到过期时间（防止缓存雪崩）
     *
     * @param duration    基础时间
     * @param jitterRatio 抖动比例（0.0-1.0）
     * @return 添加抖动后的时间
     */
    public static Duration addJitter(Duration duration, double jitterRatio) {
        if (duration == null || jitterRatio <= 0) {
            return duration;
        }
        
        long baseMillis = duration.toMillis();
        long jitterMillis = (long) (baseMillis * jitterRatio * ThreadLocalRandom.current().nextDouble());
        
        return Duration.ofMillis(baseMillis + jitterMillis);
    }

    /**
     * 格式化持续时间为可读字符串
     *
     * @param duration 持续时间
     * @return 格式化字符串
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "null";
        }
        
        if (duration.isZero()) {
            return "0ms";
        }
        
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long millis = duration.toMillis() % 1000;
        
        StringBuilder sb = new StringBuilder();
        
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s ");
        if (millis > 0 && totalSeconds == 0) sb.append(millis).append("ms");
        
        return sb.toString().trim();
    }

    // ================ 容量计算工具 ================

    /**
     * 计算字符串占用的近似内存大小（字节）
     *
     * @param str 字符串
     * @return 内存大小（字节）
     */
    public static long calculateStringSize(String str) {
        if (str == null) {
            return 0;
        }
        
        // Java中char是2字节，再加上对象头开销
        return str.length() * 2L + 40; // 40字节是大概的对象头开销
    }

    /**
     * 格式化字节大小为可读字符串
     *
     * @param bytes 字节数
     * @return 格式化字符串
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int unitIndex = -1;
        
        do {
            size /= 1024.0;
            unitIndex++;
        } while (size >= 1024 && unitIndex < units.length - 1);
        
        return String.format("%.2f%s", size, units[unitIndex]);
    }

    /**
     * 获取当前JVM内存使用情况
     *
     * @return 内存使用信息
     */
    public static MemoryInfo getMemoryInfo() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        return new MemoryInfo(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax()
        );
    }

    /**
     * 计算建议的缓存容量
     *
     * @param availableMemory 可用内存（字节）
     * @param memoryRatio     内存使用比例（0.0-1.0）
     * @param avgEntrySize    平均条目大小（字节）
     * @return 建议的缓存容量
     */
    public static long calculateRecommendedCapacity(long availableMemory, double memoryRatio, long avgEntrySize) {
        if (availableMemory <= 0 || memoryRatio <= 0 || avgEntrySize <= 0) {
            return 1000; // 默认容量
        }
        
        long usableMemory = (long) (availableMemory * memoryRatio);
        return Math.max(100, usableMemory / avgEntrySize);
    }

    // ================ 性能测试工具 ================

    /**
     * 测试缓存性能
     *
     * @param cache       缓存实例
     * @param operationCount 操作次数
     * @param keyGenerator   键生成器
     * @param valueGenerator 值生成器
     * @return 性能测试结果
     */
    public static PerformanceResult testCachePerformance(Cache<CacheKey, Object> cache,
                                                        int operationCount,
                                                        Function<Integer, String> keyGenerator,
                                                        Function<Integer, Object> valueGenerator) {
        
        logger.info("开始缓存性能测试，操作次数: {}", operationCount);
        
        // 预热
        for (int i = 0; i < Math.min(1000, operationCount / 10); i++) {
            String key = keyGenerator.apply(i);
            Object value = valueGenerator.apply(i);
            cache.put(CacheKey.of(key), value);
        }
        
        long startTime = System.nanoTime();
        
        // 写入测试
        long writeStartTime = System.nanoTime();
        for (int i = 0; i < operationCount; i++) {
            String key = keyGenerator.apply(i);
            Object value = valueGenerator.apply(i);
            cache.put(CacheKey.of(key), value);
        }
        long writeTime = System.nanoTime() - writeStartTime;
        
        // 读取测试
        long readStartTime = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < operationCount; i++) {
            String key = keyGenerator.apply(i);
            Object value = cache.get(CacheKey.of(key));
            if (value != null) {
                hits++;
            }
        }
        long readTime = System.nanoTime() - readStartTime;
        
        long totalTime = System.nanoTime() - startTime;
        
        PerformanceResult result = new PerformanceResult(
            operationCount,
            writeTime,
            readTime,
            totalTime,
            hits,
            cache.size()
        );
        
        logger.info("缓存性能测试完成: {}", result);
        return result;
    }

    /**
     * 异步测试缓存性能
     *
     * @param cache          缓存实例
     * @param operationCount 操作次数
     * @param keyGenerator   键生成器
     * @param valueGenerator 值生成器
     * @return 异步性能测试结果
     */
    public static CompletableFuture<PerformanceResult> testCachePerformanceAsync(
            Cache<CacheKey, Object> cache,
            int operationCount,
            Function<Integer, String> keyGenerator,
            Function<Integer, Object> valueGenerator) {
        
        return CompletableFuture.supplyAsync(() -> 
            testCachePerformance(cache, operationCount, keyGenerator, valueGenerator));
    }

    // ================ 调试工具 ================

    /**
     * 打印缓存状态信息
     *
     * @param cache 缓存实例
     */
    public static void printCacheStatus(Cache<CacheKey, Object> cache) {
        logger.info("=== 缓存状态信息 ===");
        logger.info("缓存名称: {}", cache.getName());
        logger.info("缓存大小: {}", cache.size());
        logger.info("是否为空: {}", cache.isEmpty());
        
        Cache.CacheStatistics stats = cache.getStatistics();
        if (stats != null) {
            logger.info("请求总数: {}", stats.getRequestCount());
            logger.info("命中次数: {}", stats.getHitCount());
            logger.info("未命中次数: {}", stats.getMissCount());
            logger.info("命中率: {:.2f}%", stats.getHitRate() * 100);
            logger.info("平均加载时间: {}ns", stats.getAverageLoadTime());
        }
        
        logger.info("==================");
    }

    /**
     * 生成缓存诊断报告
     *
     * @param cache 缓存实例
     * @return 诊断报告
     */
    public static String generateDiagnosticReport(Cache<CacheKey, Object> cache) {
        StringBuilder report = new StringBuilder();
        report.append("缓存诊断报告\n");
        report.append("============\n");
        report.append(String.format("缓存名称: %s\n", cache.getName()));
        report.append(String.format("当前时间: %s\n", DEFAULT_DATETIME_FORMAT.format(LocalDateTime.now())));
        report.append(String.format("缓存大小: %d\n", cache.size()));
        report.append(String.format("是否为空: %s\n", cache.isEmpty()));
        
        Cache.CacheStatistics stats = cache.getStatistics();
        if (stats != null) {
            report.append("\n统计信息:\n");
            report.append(String.format("  请求总数: %d\n", stats.getRequestCount()));
            report.append(String.format("  命中次数: %d\n", stats.getHitCount()));
            report.append(String.format("  未命中次数: %d\n", stats.getMissCount()));
            report.append(String.format("  命中率: %.2f%%\n", stats.getHitRate() * 100));
            report.append(String.format("  未命中率: %.2f%%\n", stats.getMissRate() * 100));
            report.append(String.format("  平均加载时间: %.2fms\n", stats.getAverageLoadTime() / 1_000_000.0));
        }
        
        MemoryInfo memInfo = getMemoryInfo();
        report.append("\n内存使用:\n");
        report.append(String.format("  堆内存使用: %s / %s\n", 
            formatBytes(memInfo.getHeapUsed()), formatBytes(memInfo.getHeapMax())));
        report.append(String.format("  非堆内存使用: %s / %s\n", 
            formatBytes(memInfo.getNonHeapUsed()), formatBytes(memInfo.getNonHeapMax())));
        
        return report.toString();
    }

    // ================ 内部类定义 ================

    /**
     * 内存使用信息
     */
    public static class MemoryInfo {
        private final long heapUsed;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapMax;

        public MemoryInfo(long heapUsed, long heapMax, long nonHeapUsed, long nonHeapMax) {
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
        }

        public long getHeapUsed() { return heapUsed; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapMax() { return nonHeapMax; }
        
        public double getHeapUsageRatio() {
            return heapMax > 0 ? (double) heapUsed / heapMax : 0.0;
        }
        
        public double getNonHeapUsageRatio() {
            return nonHeapMax > 0 ? (double) nonHeapUsed / nonHeapMax : 0.0;
        }
    }

    /**
     * 性能测试结果
     */
    public static class PerformanceResult {
        private final int operationCount;
        private final long writeTimeNanos;
        private final long readTimeNanos;
        private final long totalTimeNanos;
        private final int hitCount;
        private final long finalCacheSize;

        public PerformanceResult(int operationCount, long writeTimeNanos, long readTimeNanos,
                               long totalTimeNanos, int hitCount, long finalCacheSize) {
            this.operationCount = operationCount;
            this.writeTimeNanos = writeTimeNanos;
            this.readTimeNanos = readTimeNanos;
            this.totalTimeNanos = totalTimeNanos;
            this.hitCount = hitCount;
            this.finalCacheSize = finalCacheSize;
        }

        public int getOperationCount() { return operationCount; }
        public long getWriteTimeNanos() { return writeTimeNanos; }
        public long getReadTimeNanos() { return readTimeNanos; }
        public long getTotalTimeNanos() { return totalTimeNanos; }
        public int getHitCount() { return hitCount; }
        public long getFinalCacheSize() { return finalCacheSize; }
        
        public double getWriteOpsPerSecond() {
            return operationCount / (writeTimeNanos / 1_000_000_000.0);
        }
        
        public double getReadOpsPerSecond() {
            return operationCount / (readTimeNanos / 1_000_000_000.0);
        }
        
        public double getHitRate() {
            return operationCount > 0 ? (double) hitCount / operationCount : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "PerformanceResult{ops=%d, writeOps/s=%.0f, readOps/s=%.0f, hitRate=%.2f%%, cacheSize=%d}",
                operationCount, getWriteOpsPerSecond(), getReadOpsPerSecond(), 
                getHitRate() * 100, finalCacheSize);
        }
    }
}