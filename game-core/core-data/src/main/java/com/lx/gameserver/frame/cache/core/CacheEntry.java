/*
 * 文件名: CacheEntry.java
 * 用途: 缓存条目
 * 实现内容:
 *   - 缓存项的完整信息封装
 *   - 包含值、创建时间、过期时间等元数据
 *   - 访问统计和版本信息
 *   - 支持元数据扩展
 *   - 过期检查和状态管理
 * 技术选型:
 *   - Java 17不可变类设计
 *   - 泛型类型安全
 *   - 时间API使用Instant
 *   - 可扩展的元数据Map
 * 依赖关系:
 *   - 被Cache实现类使用
 *   - 提供缓存项的完整信息
 *   - 支持缓存统计和监控
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存条目
 * <p>
 * 封装缓存项的完整信息，包括缓存值、创建时间、过期时间、
 * 访问统计等元数据。提供过期检查和状态管理功能。
 * </p>
 *
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class CacheEntry<V> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 缓存键
     */
    private final CacheKey key;

    /**
     * 缓存值
     */
    private final V value;

    /**
     * 创建时间
     */
    private final Instant createTime;

    /**
     * 过期时间（可选）
     */
    private final Instant expireTime;

    /**
     * 最后访问时间
     */
    private volatile Instant lastAccessTime;

    /**
     * 访问次数
     */
    private final AtomicLong accessCount;

    /**
     * 版本号
     */
    private final long version;

    /**
     * 元数据
     */
    private final Map<String, Object> metadata;

    /**
     * 私有构造函数
     */
    private CacheEntry(Builder<V> builder) {
        this.key = Objects.requireNonNull(builder.key, "缓存键不能为null");
        this.value = builder.value;
        this.createTime = builder.createTime != null ? builder.createTime : Instant.now();
        this.expireTime = builder.expireTime;
        this.lastAccessTime = this.createTime;
        this.accessCount = new AtomicLong(0);
        this.version = builder.version;
        this.metadata = builder.metadata != null ? 
            Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : 
            Collections.emptyMap();
    }

    /**
     * 创建缓存条目
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param <V>   值类型
     * @return 缓存条目
     */
    public static <V> CacheEntry<V> of(CacheKey key, V value) {
        return new Builder<V>().key(key).value(value).build();
    }

    /**
     * 创建带过期时间的缓存条目
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param expireTime 过期时间
     * @param <V>        值类型
     * @return 缓存条目
     */
    public static <V> CacheEntry<V> of(CacheKey key, V value, Instant expireTime) {
        return new Builder<V>().key(key).value(value).expireTime(expireTime).build();
    }

    /**
     * 创建构建器
     *
     * @param <V> 值类型
     * @return 构建器
     */
    public static <V> Builder<V> builder() {
        return new Builder<>();
    }

    /**
     * 获取缓存键
     *
     * @return 缓存键
     */
    public CacheKey getKey() {
        return key;
    }

    /**
     * 获取缓存值
     *
     * @return 缓存值
     */
    public V getValue() {
        return value;
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
     * 获取过期时间
     *
     * @return 过期时间，可能为null
     */
    public Instant getExpireTime() {
        return expireTime;
    }

    /**
     * 获取最后访问时间
     *
     * @return 最后访问时间
     */
    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * 获取访问次数
     *
     * @return 访问次数
     */
    public long getAccessCount() {
        return accessCount.get();
    }

    /**
     * 获取版本号
     *
     * @return 版本号
     */
    public long getVersion() {
        return version;
    }

    /**
     * 获取元数据
     *
     * @return 元数据映射（不可修改）
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 获取元数据值
     *
     * @param key 元数据键
     * @param <T> 值类型
     * @return 元数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    /**
     * 获取元数据值，如果不存在则返回默认值
     *
     * @param key          元数据键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 元数据值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        return (T) metadata.getOrDefault(key, defaultValue);
    }

    /**
     * 检查是否过期
     *
     * @return 如果过期返回true
     */
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    /**
     * 检查在指定时间是否过期
     *
     * @param now 检查时间
     * @return 如果过期返回true
     */
    public boolean isExpired(Instant now) {
        return expireTime != null && now.isAfter(expireTime);
    }

    /**
     * 检查是否有过期时间
     *
     * @return 如果有过期时间返回true
     */
    public boolean hasExpireTime() {
        return expireTime != null;
    }

    /**
     * 获取剩余生存时间（毫秒）
     *
     * @return 剩余生存时间，如果没有过期时间返回-1
     */
    public long getTimeToLiveMillis() {
        if (expireTime == null) {
            return -1;
        }
        long ttl = expireTime.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, ttl);
    }

    /**
     * 记录访问
     *
     * @return 新的访问次数
     */
    public long recordAccess() {
        this.lastAccessTime = Instant.now();
        return accessCount.incrementAndGet();
    }

    /**
     * 创建新的条目副本，更新值
     *
     * @param newValue 新值
     * @return 新的缓存条目
     */
    public CacheEntry<V> withValue(V newValue) {
        return new Builder<V>()
            .key(this.key)
            .value(newValue)
            .createTime(this.createTime)
            .expireTime(this.expireTime)
            .version(this.version + 1)
            .metadata(new HashMap<>(this.metadata))
            .build();
    }

    /**
     * 创建新的条目副本，更新过期时间
     *
     * @param newExpireTime 新过期时间
     * @return 新的缓存条目
     */
    public CacheEntry<V> withExpireTime(Instant newExpireTime) {
        return new Builder<V>()
            .key(this.key)
            .value(this.value)
            .createTime(this.createTime)
            .expireTime(newExpireTime)
            .version(this.version)
            .metadata(new HashMap<>(this.metadata))
            .build();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CacheEntry<?> that = (CacheEntry<?>) obj;
        return version == that.version &&
               Objects.equals(key, that.key) &&
               Objects.equals(value, that.value) &&
               Objects.equals(createTime, that.createTime) &&
               Objects.equals(expireTime, that.expireTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, createTime, expireTime, version);
    }

    @Override
    public String toString() {
        return String.format("CacheEntry{key=%s, value=%s, createTime=%s, expireTime=%s, " +
                "lastAccessTime=%s, accessCount=%d, version=%d}", 
                key, value, createTime, expireTime, lastAccessTime, accessCount.get(), version);
    }

    /**
     * 缓存条目构建器
     *
     * @param <V> 值类型
     */
    public static class Builder<V> {
        private CacheKey key;
        private V value;
        private Instant createTime;
        private Instant expireTime;
        private long version = 1;
        private Map<String, Object> metadata;

        /**
         * 设置缓存键
         *
         * @param key 缓存键
         * @return 构建器
         */
        public Builder<V> key(CacheKey key) {
            this.key = key;
            return this;
        }

        /**
         * 设置缓存值
         *
         * @param value 缓存值
         * @return 构建器
         */
        public Builder<V> value(V value) {
            this.value = value;
            return this;
        }

        /**
         * 设置创建时间
         *
         * @param createTime 创建时间
         * @return 构建器
         */
        public Builder<V> createTime(Instant createTime) {
            this.createTime = createTime;
            return this;
        }

        /**
         * 设置过期时间
         *
         * @param expireTime 过期时间
         * @return 构建器
         */
        public Builder<V> expireTime(Instant expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        /**
         * 设置版本号
         *
         * @param version 版本号
         * @return 构建器
         */
        public Builder<V> version(long version) {
            this.version = version;
            return this;
        }

        /**
         * 设置元数据
         *
         * @param metadata 元数据
         * @return 构建器
         */
        public Builder<V> metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * 添加元数据
         *
         * @param key   元数据键
         * @param value 元数据值
         * @return 构建器
         */
        public Builder<V> addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * 构建缓存条目
         *
         * @return 缓存条目
         */
        public CacheEntry<V> build() {
            return new CacheEntry<>(this);
        }
    }
}