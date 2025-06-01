/*
 * 文件名: CacheConfig.java
 * 用途: 缓存配置
 * 实现内容:
 *   - 统一的缓存配置参数
 *   - 容量限制和过期策略配置
 *   - 淘汰策略和持久化配置
 *   - 监控和性能配置
 *   - 分布式缓存配置
 * 技术选型:
 *   - Java 17记录类型
 *   - 不可变配置对象
 *   - 建造者模式
 *   - 枚举类型定义策略
 * 依赖关系:
 *   - 被Cache实现使用
 *   - 提供配置参数管理
 *   - 支持动态配置更新
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.core;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 缓存配置
 * <p>
 * 提供缓存的统一配置管理，包括容量限制、过期策略、淘汰策略、
 * 持久化配置、监控配置等。使用不可变对象设计确保配置安全性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class CacheConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认最大容量
     */
    public static final long DEFAULT_MAX_SIZE = 10000L;

    /**
     * 默认写后过期时间
     */
    public static final Duration DEFAULT_EXPIRE_AFTER_WRITE = Duration.ofMinutes(30);

    /**
     * 默认访问后过期时间
     */
    public static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(10);

    /**
     * 缓存名称
     */
    private final String name;

    /**
     * 最大容量
     */
    private final long maxSize;

    /**
     * 写后过期时间
     */
    private final Duration expireAfterWrite;

    /**
     * 访问后过期时间
     */
    private final Duration expireAfterAccess;

    /**
     * 刷新时间
     */
    private final Duration refreshAfterWrite;

    /**
     * 淘汰策略
     */
    private final EvictionPolicy evictionPolicy;

    /**
     * 过期策略
     */
    private final ExpirationPolicy expirationPolicy;

    /**
     * 是否启用统计
     */
    private final boolean statisticsEnabled;

    /**
     * 是否启用异步操作
     */
    private final boolean asyncEnabled;

    /**
     * 是否启用压缩
     */
    private final boolean compressionEnabled;

    /**
     * 序列化器类型
     */
    private final String serializerType;

    /**
     * 扩展属性
     */
    private final Map<String, Object> properties;

    /**
     * 私有构造函数
     */
    private CacheConfig(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "缓存名称不能为null");
        this.maxSize = builder.maxSize;
        this.expireAfterWrite = builder.expireAfterWrite;
        this.expireAfterAccess = builder.expireAfterAccess;
        this.refreshAfterWrite = builder.refreshAfterWrite;
        this.evictionPolicy = builder.evictionPolicy;
        this.expirationPolicy = builder.expirationPolicy;
        this.statisticsEnabled = builder.statisticsEnabled;
        this.asyncEnabled = builder.asyncEnabled;
        this.compressionEnabled = builder.compressionEnabled;
        this.serializerType = builder.serializerType;
        this.properties = builder.properties != null ? 
            Collections.unmodifiableMap(new HashMap<>(builder.properties)) : 
            Collections.emptyMap();
    }

    /**
     * 创建默认配置
     *
     * @param name 缓存名称
     * @return 默认配置
     */
    public static CacheConfig defaultConfig(String name) {
        return builder(name).build();
    }

    /**
     * 创建构建器
     *
     * @param name 缓存名称
     * @return 构建器
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * 获取缓存名称
     *
     * @return 缓存名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取最大容量
     *
     * @return 最大容量
     */
    public long getMaxSize() {
        return maxSize;
    }

    /**
     * 获取写后过期时间
     *
     * @return 写后过期时间
     */
    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    /**
     * 获取访问后过期时间
     *
     * @return 访问后过期时间
     */
    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    /**
     * 获取刷新时间
     *
     * @return 刷新时间
     */
    public Duration getRefreshAfterWrite() {
        return refreshAfterWrite;
    }

    /**
     * 获取淘汰策略
     *
     * @return 淘汰策略
     */
    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    /**
     * 获取过期策略
     *
     * @return 过期策略
     */
    public ExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    /**
     * 是否启用统计
     *
     * @return 如果启用统计返回true
     */
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    /**
     * 是否启用异步操作
     *
     * @return 如果启用异步操作返回true
     */
    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    /**
     * 是否启用压缩
     *
     * @return 如果启用压缩返回true
     */
    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    /**
     * 获取序列化器类型
     *
     * @return 序列化器类型
     */
    public String getSerializerType() {
        return serializerType;
    }

    /**
     * 获取扩展属性
     *
     * @return 扩展属性映射（不可修改）
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * 获取扩展属性值
     *
     * @param key 属性键
     * @param <T> 值类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key) {
        return (T) properties.get(key);
    }

    /**
     * 获取扩展属性值，如果不存在则返回默认值
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 属性值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        return (T) properties.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CacheConfig that = (CacheConfig) obj;
        return maxSize == that.maxSize &&
               statisticsEnabled == that.statisticsEnabled &&
               asyncEnabled == that.asyncEnabled &&
               compressionEnabled == that.compressionEnabled &&
               Objects.equals(name, that.name) &&
               Objects.equals(expireAfterWrite, that.expireAfterWrite) &&
               Objects.equals(expireAfterAccess, that.expireAfterAccess) &&
               Objects.equals(refreshAfterWrite, that.refreshAfterWrite) &&
               evictionPolicy == that.evictionPolicy &&
               expirationPolicy == that.expirationPolicy &&
               Objects.equals(serializerType, that.serializerType) &&
               Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, maxSize, expireAfterWrite, expireAfterAccess, 
                refreshAfterWrite, evictionPolicy, expirationPolicy, 
                statisticsEnabled, asyncEnabled, compressionEnabled, 
                serializerType, properties);
    }

    @Override
    public String toString() {
        return String.format("CacheConfig{name='%s', maxSize=%d, evictionPolicy=%s, " +
                "statisticsEnabled=%s, asyncEnabled=%s}", 
                name, maxSize, evictionPolicy, statisticsEnabled, asyncEnabled);
    }

    /**
     * 淘汰策略枚举
     */
    public enum EvictionPolicy {
        /**
         * 最近最少使用
         */
        LRU("LRU", "最近最少使用"),

        /**
         * 最不经常使用
         */
        LFU("LFU", "最不经常使用"),

        /**
         * 先进先出
         */
        FIFO("FIFO", "先进先出"),

        /**
         * 随机淘汰
         */
        RANDOM("RANDOM", "随机淘汰"),

        /**
         * 不淘汰
         */
        NONE("NONE", "不淘汰");

        private final String code;
        private final String description;

        EvictionPolicy(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 过期策略枚举
     */
    public enum ExpirationPolicy {
        /**
         * 固定过期时间
         */
        FIXED("FIXED", "固定过期时间"),

        /**
         * 滑动过期时间
         */
        SLIDING("SLIDING", "滑动过期时间"),

        /**
         * 懒惰删除
         */
        LAZY("LAZY", "懒惰删除"),

        /**
         * 定期清理
         */
        PERIODIC("PERIODIC", "定期清理"),

        /**
         * 不过期
         */
        NEVER("NEVER", "不过期");

        private final String code;
        private final String description;

        ExpirationPolicy(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 配置构建器
     */
    public static class Builder {
        private final String name;
        private long maxSize = DEFAULT_MAX_SIZE;
        private Duration expireAfterWrite = DEFAULT_EXPIRE_AFTER_WRITE;
        private Duration expireAfterAccess = DEFAULT_EXPIRE_AFTER_ACCESS;
        private Duration refreshAfterWrite;
        private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
        private ExpirationPolicy expirationPolicy = ExpirationPolicy.FIXED;
        private boolean statisticsEnabled = true;
        private boolean asyncEnabled = false;
        private boolean compressionEnabled = false;
        private String serializerType = "json";
        private Map<String, Object> properties;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "缓存名称不能为null");
        }

        /**
         * 设置最大容量
         *
         * @param maxSize 最大容量
         * @return 构建器
         */
        public Builder maxSize(long maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        /**
         * 设置写后过期时间
         *
         * @param expireAfterWrite 写后过期时间
         * @return 构建器
         */
        public Builder expireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
            return this;
        }

        /**
         * 设置访问后过期时间
         *
         * @param expireAfterAccess 访问后过期时间
         * @return 构建器
         */
        public Builder expireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
            return this;
        }

        /**
         * 设置刷新时间
         *
         * @param refreshAfterWrite 刷新时间
         * @return 构建器
         */
        public Builder refreshAfterWrite(Duration refreshAfterWrite) {
            this.refreshAfterWrite = refreshAfterWrite;
            return this;
        }

        /**
         * 设置淘汰策略
         *
         * @param evictionPolicy 淘汰策略
         * @return 构建器
         */
        public Builder evictionPolicy(EvictionPolicy evictionPolicy) {
            this.evictionPolicy = evictionPolicy;
            return this;
        }

        /**
         * 设置过期策略
         *
         * @param expirationPolicy 过期策略
         * @return 构建器
         */
        public Builder expirationPolicy(ExpirationPolicy expirationPolicy) {
            this.expirationPolicy = expirationPolicy;
            return this;
        }

        /**
         * 设置是否启用统计
         *
         * @param statisticsEnabled 是否启用统计
         * @return 构建器
         */
        public Builder statisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
            return this;
        }

        /**
         * 设置是否启用异步操作
         *
         * @param asyncEnabled 是否启用异步操作
         * @return 构建器
         */
        public Builder asyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
            return this;
        }

        /**
         * 设置是否启用压缩
         *
         * @param compressionEnabled 是否启用压缩
         * @return 构建器
         */
        public Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /**
         * 设置序列化器类型
         *
         * @param serializerType 序列化器类型
         * @return 构建器
         */
        public Builder serializerType(String serializerType) {
            this.serializerType = serializerType;
            return this;
        }

        /**
         * 设置扩展属性
         *
         * @param properties 扩展属性
         * @return 构建器
         */
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * 添加扩展属性
         *
         * @param key   属性键
         * @param value 属性值
         * @return 构建器
         */
        public Builder property(String key, Object value) {
            if (this.properties == null) {
                this.properties = new HashMap<>();
            }
            this.properties.put(key, value);
            return this;
        }

        /**
         * 构建配置
         *
         * @return 缓存配置
         */
        public CacheConfig build() {
            return new CacheConfig(this);
        }
    }
}