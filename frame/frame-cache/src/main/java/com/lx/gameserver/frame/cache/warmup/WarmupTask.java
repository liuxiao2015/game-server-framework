/*
 * 文件名: WarmupTask.java
 * 用途: 预热任务
 * 实现内容:
 *   - 预热任务定义和执行
 *   - 任务优先级和调度
 *   - 数据供应器和加载逻辑
 *   - 失败重试和错误处理
 *   - 任务进度跟踪
 * 技术选型:
 *   - 函数式接口设计
 *   - 优先级队列支持
 *   - 建造者模式
 *   - 异常处理机制
 * 依赖关系:
 *   - 被CacheWarmer执行
 *   - 提供数据加载接口
 *   - 支持自定义任务逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.warmup;

import com.lx.gameserver.frame.cache.core.CacheKey;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 预热任务
 * <p>
 * 表示一个缓存预热任务，包含任务的基本信息、优先级、数据供应器等。
 * 通过任务的组合和调度，实现灵活的缓存预热策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class WarmupTask {

    /**
     * 任务名称
     */
    private final String taskName;

    /**
     * 缓存名称
     */
    private final String cacheName;

    /**
     * 任务优先级
     */
    private final Priority priority;

    /**
     * 数据供应器
     */
    private final Supplier<Map<CacheKey, Object>> dataSupplier;

    /**
     * 创建时间
     */
    private final Instant createTime;

    /**
     * 计划执行时间
     */
    private final Instant scheduledTime;

    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 超时时间（毫秒）
     */
    private final long timeoutMillis;

    /**
     * 任务描述
     */
    private final String description;

    /**
     * 构造函数
     */
    private WarmupTask(Builder builder) {
        this.taskName = builder.taskName;
        this.cacheName = builder.cacheName;
        this.priority = builder.priority;
        this.dataSupplier = builder.dataSupplier;
        this.createTime = builder.createTime;
        this.scheduledTime = builder.scheduledTime;
        this.maxRetries = builder.maxRetries;
        this.timeoutMillis = builder.timeoutMillis;
        this.description = builder.description;
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
     * 获取任务名称
     *
     * @return 任务名称
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * 获取缓存名称
     *
     * @return 缓存名称
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * 获取任务优先级
     *
     * @return 优先级
     */
    public Priority getPriority() {
        return priority;
    }

    /**
     * 获取数据供应器
     *
     * @return 数据供应器
     */
    public Supplier<Map<CacheKey, Object>> getDataSupplier() {
        return dataSupplier;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public Instant getCreateTime() {
        return createTime;
    }

    /**
     * 获取计划执行时间
     *
     * @return 计划执行时间
     */
    public Instant getScheduledTime() {
        return scheduledTime;
    }

    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取超时时间
     *
     * @return 超时时间（毫秒）
     */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * 获取任务描述
     *
     * @return 任务描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 检查任务是否到期执行
     *
     * @return 是否到期
     */
    public boolean isDue() {
        return Instant.now().isAfter(scheduledTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        WarmupTask that = (WarmupTask) obj;
        return Objects.equals(taskName, that.taskName) &&
               Objects.equals(cacheName, that.cacheName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskName, cacheName);
    }

    @Override
    public String toString() {
        return "WarmupTask{" +
               "taskName='" + taskName + '\'' +
               ", cacheName='" + cacheName + '\'' +
               ", priority=" + priority +
               ", createTime=" + createTime +
               ", scheduledTime=" + scheduledTime +
               ", description='" + description + '\'' +
               '}';
    }

    /**
     * 任务优先级
     */
    public enum Priority {
        /**
         * 低优先级
         */
        LOW(1, "低优先级"),

        /**
         * 普通优先级
         */
        NORMAL(2, "普通优先级"),

        /**
         * 高优先级
         */
        HIGH(3, "高优先级"),

        /**
         * 紧急优先级
         */
        URGENT(4, "紧急优先级");

        private final int level;
        private final String description;

        Priority(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 构建器
     */
    public static class Builder {
        private String taskName;
        private String cacheName;
        private Priority priority = Priority.NORMAL;
        private Supplier<Map<CacheKey, Object>> dataSupplier;
        private Instant createTime = Instant.now();
        private Instant scheduledTime = Instant.now();
        private int maxRetries = 3;
        private long timeoutMillis = 60000; // 60秒
        private String description = "";

        /**
         * 设置任务名称
         *
         * @param taskName 任务名称
         * @return 构建器
         */
        public Builder taskName(String taskName) {
            this.taskName = Objects.requireNonNull(taskName, "任务名称不能为null");
            return this;
        }

        /**
         * 设置缓存名称
         *
         * @param cacheName 缓存名称
         * @return 构建器
         */
        public Builder cacheName(String cacheName) {
            this.cacheName = Objects.requireNonNull(cacheName, "缓存名称不能为null");
            return this;
        }

        /**
         * 设置任务优先级
         *
         * @param priority 优先级
         * @return 构建器
         */
        public Builder priority(Priority priority) {
            this.priority = Objects.requireNonNull(priority, "优先级不能为null");
            return this;
        }

        /**
         * 设置数据供应器
         *
         * @param dataSupplier 数据供应器
         * @return 构建器
         */
        public Builder dataSupplier(Supplier<Map<CacheKey, Object>> dataSupplier) {
            this.dataSupplier = Objects.requireNonNull(dataSupplier, "数据供应器不能为null");
            return this;
        }

        /**
         * 设置创建时间
         *
         * @param createTime 创建时间
         * @return 构建器
         */
        public Builder createTime(Instant createTime) {
            this.createTime = Objects.requireNonNull(createTime, "创建时间不能为null");
            return this;
        }

        /**
         * 设置计划执行时间
         *
         * @param scheduledTime 计划执行时间
         * @return 构建器
         */
        public Builder scheduledTime(Instant scheduledTime) {
            this.scheduledTime = Objects.requireNonNull(scheduledTime, "计划执行时间不能为null");
            return this;
        }

        /**
         * 设置最大重试次数
         *
         * @param maxRetries 最大重试次数
         * @return 构建器
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("最大重试次数不能为负数");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * 设置超时时间
         *
         * @param timeoutMillis 超时时间（毫秒）
         * @return 构建器
         */
        public Builder timeoutMillis(long timeoutMillis) {
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("超时时间必须大于0");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * 设置任务描述
         *
         * @param description 任务描述
         * @return 构建器
         */
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        /**
         * 构建任务
         *
         * @return 预热任务
         */
        public WarmupTask build() {
            Objects.requireNonNull(taskName, "任务名称不能为null");
            Objects.requireNonNull(cacheName, "缓存名称不能为null");
            Objects.requireNonNull(dataSupplier, "数据供应器不能为null");
            
            return new WarmupTask(this);
        }
    }
}