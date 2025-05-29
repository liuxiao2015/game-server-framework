/*
 * 文件名: WarmupStrategy.java
 * 用途: 预热策略接口
 * 实现内容:
 *   - 定义预热策略的标准接口
 *   - 支持全量、增量、热点预热
 *   - 智能预热和自定义策略
 *   - 预热数据源管理
 *   - 策略执行和监控
 * 技术选型:
 *   - Java 17 接口定义
 *   - 策略模式实现
 *   - 函数式接口支持
 *   - 异步执行支持
 * 依赖关系:
 *   - 被CacheWarmer使用
 *   - 提供预热策略抽象
 *   - 支持多种预热模式
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.warmup;

import com.lx.gameserver.frame.cache.core.CacheKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 预热策略接口
 * <p>
 * 定义了缓存预热策略的标准接口，支持多种预热模式和自定义预热逻辑。
 * 通过策略模式实现不同场景下的预热需求。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface WarmupStrategy {

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 获取策略描述
     *
     * @return 策略描述
     */
    String getDescription();

    /**
     * 判断是否支持指定缓存
     *
     * @param cacheName 缓存名称
     * @return 是否支持
     */
    boolean supports(String cacheName);

    /**
     * 生成预热任务
     *
     * @param cacheName 缓存名称
     * @return 预热任务列表
     */
    List<WarmupTask> generateWarmupTasks(String cacheName);

    /**
     * 生成预热数据
     *
     * @param cacheName 缓存名称
     * @return 预热数据
     */
    Map<CacheKey, Object> generateWarmupData(String cacheName);

    /**
     * 异步生成预热数据
     *
     * @param cacheName 缓存名称
     * @return 预热数据的Future
     */
    CompletableFuture<Map<CacheKey, Object>> generateWarmupDataAsync(String cacheName);

    /**
     * 获取预热优先级
     *
     * @param cacheName 缓存名称
     * @return 优先级
     */
    WarmupTask.Priority getPriority(String cacheName);

    /**
     * 估算预热数据量
     *
     * @param cacheName 缓存名称
     * @return 预计数据量
     */
    long estimateDataSize(String cacheName);

    /**
     * 获取下次预热时间
     *
     * @param cacheName 缓存名称
     * @return 下次预热时间
     */
    Instant getNextWarmupTime(String cacheName);

    /**
     * 是否需要定期预热
     *
     * @param cacheName 缓存名称
     * @return 是否需要定期预热
     */
    boolean requiresPeriodicWarmup(String cacheName);

    /**
     * 预热前的准备工作
     *
     * @param cacheName 缓存名称
     */
    void beforeWarmup(String cacheName);

    /**
     * 预热后的清理工作
     *
     * @param cacheName    缓存名称
     * @param warmupResult 预热结果
     */
    void afterWarmup(String cacheName, WarmupResult warmupResult);

    /**
     * 验证预热数据
     *
     * @param cacheName 缓存名称
     * @param data      预热数据
     * @return 验证结果
     */
    boolean validateWarmupData(String cacheName, Map<CacheKey, Object> data);

    /**
     * 获取预热统计信息
     *
     * @return 统计信息
     */
    WarmupStatistics getStatistics();

    /**
     * 重置策略状态
     */
    void reset();

    /**
     * 创建策略实例
     *
     * @param strategyName 策略名称
     * @return 策略实例
     */
    static WarmupStrategy create(String strategyName) {
        return switch (strategyName.toUpperCase()) {
            case "FULL" -> new FullWarmupStrategy();
            case "INCREMENTAL" -> new IncrementalWarmupStrategy();
            case "HOTSPOT" -> new HotspotWarmupStrategy();
            case "SMART" -> new SmartWarmupStrategy();
            default -> throw new IllegalArgumentException("不支持的预热策略: " + strategyName);
        };
    }

    /**
     * 预热策略类型
     */
    enum StrategyType {
        /**
         * 全量预热
         */
        FULL("全量预热", "预热所有数据"),

        /**
         * 增量预热
         */
        INCREMENTAL("增量预热", "只预热新增或变更的数据"),

        /**
         * 热点预热
         */
        HOTSPOT("热点预热", "只预热热点访问数据"),

        /**
         * 智能预热
         */
        SMART("智能预热", "基于访问模式智能预热");

        private final String name;
        private final String description;

        StrategyType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }
}

/**
 * 全量预热策略实现
 */
class FullWarmupStrategy implements WarmupStrategy {

    @Override
    public String getName() {
        return "FULL";
    }

    @Override
    public String getDescription() {
        return "全量预热策略，预热所有数据";
    }

    @Override
    public boolean supports(String cacheName) {
        return true; // 支持所有缓存
    }

    @Override
    public List<WarmupTask> generateWarmupTasks(String cacheName) {
        return List.of(
            WarmupTask.builder()
                .taskName("full-warmup-" + cacheName)
                .cacheName(cacheName)
                .priority(WarmupTask.Priority.NORMAL)
                .dataSupplier(() -> generateWarmupData(cacheName))
                .description("全量预热任务")
                .build()
        );
    }

    @Override
    public Map<CacheKey, Object> generateWarmupData(String cacheName) {
        // 简化实现，实际应该从数据源加载所有数据
        return Map.of();
    }

    @Override
    public CompletableFuture<Map<CacheKey, Object>> generateWarmupDataAsync(String cacheName) {
        return CompletableFuture.supplyAsync(() -> generateWarmupData(cacheName));
    }

    @Override
    public WarmupTask.Priority getPriority(String cacheName) {
        return WarmupTask.Priority.NORMAL;
    }

    @Override
    public long estimateDataSize(String cacheName) {
        return 10000; // 估计值
    }

    @Override
    public Instant getNextWarmupTime(String cacheName) {
        return Instant.now().plusSeconds(3600); // 1小时后
    }

    @Override
    public boolean requiresPeriodicWarmup(String cacheName) {
        return true;
    }

    @Override
    public void beforeWarmup(String cacheName) {
        // 预热前准备
    }

    @Override
    public void afterWarmup(String cacheName, WarmupResult warmupResult) {
        // 预热后清理
    }

    @Override
    public boolean validateWarmupData(String cacheName, Map<CacheKey, Object> data) {
        return data != null;
    }

    @Override
    public WarmupStatistics getStatistics() {
        return new WarmupStatistics();
    }

    @Override
    public void reset() {
        // 重置状态
    }
}

/**
 * 增量预热策略实现
 */
class IncrementalWarmupStrategy extends FullWarmupStrategy {

    @Override
    public String getName() {
        return "INCREMENTAL";
    }

    @Override
    public String getDescription() {
        return "增量预热策略，只预热新增或变更的数据";
    }

    @Override
    public WarmupTask.Priority getPriority(String cacheName) {
        return WarmupTask.Priority.HIGH;
    }

    @Override
    public long estimateDataSize(String cacheName) {
        return 1000; // 增量数据较少
    }
}

/**
 * 热点预热策略实现
 */
class HotspotWarmupStrategy extends FullWarmupStrategy {

    @Override
    public String getName() {
        return "HOTSPOT";
    }

    @Override
    public String getDescription() {
        return "热点预热策略，只预热热点访问数据";
    }

    @Override
    public WarmupTask.Priority getPriority(String cacheName) {
        return WarmupTask.Priority.HIGH;
    }

    @Override
    public long estimateDataSize(String cacheName) {
        return 500; // 热点数据更少
    }
}

/**
 * 智能预热策略实现
 */
class SmartWarmupStrategy extends FullWarmupStrategy {

    @Override
    public String getName() {
        return "SMART";
    }

    @Override
    public String getDescription() {
        return "智能预热策略，基于访问模式智能预热";
    }

    @Override
    public WarmupTask.Priority getPriority(String cacheName) {
        return WarmupTask.Priority.URGENT;
    }

    @Override
    public long estimateDataSize(String cacheName) {
        return 2000; // 智能选择的数据量
    }
}