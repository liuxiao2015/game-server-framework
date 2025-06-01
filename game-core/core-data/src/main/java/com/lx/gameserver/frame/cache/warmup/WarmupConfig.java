/*
 * 文件名: WarmupConfig.java
 * 用途: 预热配置
 * 实现内容:
 *   - 预热参数配置
 *   - 并行度和批次配置
 *   - 时间间隔配置
 *   - 启动预热配置
 *   - 默认配置提供
 * 技术选型:
 *   - 不可变配置对象
 *   - 建造者模式
 *   - 时间Duration类型
 *   - 参数验证
 * 依赖关系:
 *   - 被CacheWarmer使用
 *   - 提供预热配置管理
 *   - 支持外部配置注入
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.warmup;

import java.time.Duration;
import java.util.Objects;

/**
 * 预热配置
 * <p>
 * 提供缓存预热的各种配置参数，包括并行度、批次大小、时间间隔等。
 * 使用不可变对象设计确保配置的线程安全性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class WarmupConfig {

    /**
     * 是否启用启动预热
     */
    private final boolean startupWarmup;

    /**
     * 启动延迟
     */
    private final Duration startupDelay;

    /**
     * 并行线程数
     */
    private final int parallelThreads;

    /**
     * 批次大小
     */
    private final int batchSize;

    /**
     * 批次间延迟
     */
    private final Duration batchDelay;

    /**
     * 预热间隔
     */
    private final Duration warmupInterval;

    /**
     * 最大预热时间
     */
    private final Duration maxWarmupTime;

    /**
     * 是否启用进度监控
     */
    private final boolean progressMonitoring;

    /**
     * 构造函数
     */
    private WarmupConfig(Builder builder) {
        this.startupWarmup = builder.startupWarmup;
        this.startupDelay = builder.startupDelay;
        this.parallelThreads = builder.parallelThreads;
        this.batchSize = builder.batchSize;
        this.batchDelay = builder.batchDelay;
        this.warmupInterval = builder.warmupInterval;
        this.maxWarmupTime = builder.maxWarmupTime;
        this.progressMonitoring = builder.progressMonitoring;
    }

    /**
     * 创建默认配置
     *
     * @return 默认配置
     */
    public static WarmupConfig defaultConfig() {
        return builder().build();
    }

    /**
     * 创建构建器
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 是否启用启动预热
     *
     * @return 是否启用
     */
    public boolean isStartupWarmup() {
        return startupWarmup;
    }

    /**
     * 获取启动延迟
     *
     * @return 启动延迟
     */
    public Duration getStartupDelay() {
        return startupDelay;
    }

    /**
     * 获取并行线程数
     *
     * @return 并行线程数
     */
    public int getParallelThreads() {
        return parallelThreads;
    }

    /**
     * 获取批次大小
     *
     * @return 批次大小
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 获取批次间延迟
     *
     * @return 批次间延迟
     */
    public Duration getBatchDelay() {
        return batchDelay;
    }

    /**
     * 获取预热间隔
     *
     * @return 预热间隔
     */
    public Duration getWarmupInterval() {
        return warmupInterval;
    }

    /**
     * 获取最大预热时间
     *
     * @return 最大预热时间
     */
    public Duration getMaxWarmupTime() {
        return maxWarmupTime;
    }

    /**
     * 是否启用进度监控
     *
     * @return 是否启用
     */
    public boolean isProgressMonitoring() {
        return progressMonitoring;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        WarmupConfig that = (WarmupConfig) obj;
        return startupWarmup == that.startupWarmup &&
               parallelThreads == that.parallelThreads &&
               batchSize == that.batchSize &&
               progressMonitoring == that.progressMonitoring &&
               Objects.equals(startupDelay, that.startupDelay) &&
               Objects.equals(batchDelay, that.batchDelay) &&
               Objects.equals(warmupInterval, that.warmupInterval) &&
               Objects.equals(maxWarmupTime, that.maxWarmupTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startupWarmup, startupDelay, parallelThreads, batchSize,
                           batchDelay, warmupInterval, maxWarmupTime, progressMonitoring);
    }

    @Override
    public String toString() {
        return "WarmupConfig{" +
               "startupWarmup=" + startupWarmup +
               ", startupDelay=" + startupDelay +
               ", parallelThreads=" + parallelThreads +
               ", batchSize=" + batchSize +
               ", batchDelay=" + batchDelay +
               ", warmupInterval=" + warmupInterval +
               ", maxWarmupTime=" + maxWarmupTime +
               ", progressMonitoring=" + progressMonitoring +
               '}';
    }

    /**
     * 构建器
     */
    public static class Builder {
        private boolean startupWarmup = true;
        private Duration startupDelay = Duration.ofSeconds(5);
        private int parallelThreads = Runtime.getRuntime().availableProcessors();
        private int batchSize = 1000;
        private Duration batchDelay = Duration.ofMillis(10);
        private Duration warmupInterval = Duration.ofHours(1);
        private Duration maxWarmupTime = Duration.ofMinutes(30);
        private boolean progressMonitoring = true;

        /**
         * 设置是否启用启动预热
         *
         * @param startupWarmup 是否启用
         * @return 构建器
         */
        public Builder startupWarmup(boolean startupWarmup) {
            this.startupWarmup = startupWarmup;
            return this;
        }

        /**
         * 设置启动延迟
         *
         * @param startupDelay 启动延迟
         * @return 构建器
         */
        public Builder startupDelay(Duration startupDelay) {
            this.startupDelay = Objects.requireNonNull(startupDelay);
            return this;
        }

        /**
         * 设置并行线程数
         *
         * @param parallelThreads 并行线程数
         * @return 构建器
         */
        public Builder parallelThreads(int parallelThreads) {
            if (parallelThreads <= 0) {
                throw new IllegalArgumentException("并行线程数必须大于0");
            }
            this.parallelThreads = parallelThreads;
            return this;
        }

        /**
         * 设置批次大小
         *
         * @param batchSize 批次大小
         * @return 构建器
         */
        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("批次大小必须大于0");
            }
            this.batchSize = batchSize;
            return this;
        }

        /**
         * 设置批次间延迟
         *
         * @param batchDelay 批次间延迟
         * @return 构建器
         */
        public Builder batchDelay(Duration batchDelay) {
            this.batchDelay = batchDelay;
            return this;
        }

        /**
         * 设置预热间隔
         *
         * @param warmupInterval 预热间隔
         * @return 构建器
         */
        public Builder warmupInterval(Duration warmupInterval) {
            this.warmupInterval = warmupInterval;
            return this;
        }

        /**
         * 设置最大预热时间
         *
         * @param maxWarmupTime 最大预热时间
         * @return 构建器
         */
        public Builder maxWarmupTime(Duration maxWarmupTime) {
            this.maxWarmupTime = Objects.requireNonNull(maxWarmupTime);
            return this;
        }

        /**
         * 设置是否启用进度监控
         *
         * @param progressMonitoring 是否启用
         * @return 构建器
         */
        public Builder progressMonitoring(boolean progressMonitoring) {
            this.progressMonitoring = progressMonitoring;
            return this;
        }

        /**
         * 构建配置
         *
         * @return 预热配置
         */
        public WarmupConfig build() {
            return new WarmupConfig(this);
        }
    }
}