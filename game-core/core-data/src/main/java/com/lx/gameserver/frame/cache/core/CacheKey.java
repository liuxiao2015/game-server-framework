/*
 * 文件名: CacheKey.java
 * 用途: 缓存键抽象
 * 实现内容:
 *   - 提供缓存键的标准实现
 *   - 支持命名空间和键生成策略
 *   - 版本控制和序列化支持
 *   - 正确的equals和hashCode实现
 *   - 键的前缀和后缀支持
 * 技术选型:
 *   - Java 17记录类型
 *   - 不可变对象设计
 *   - 序列化支持
 *   - 建造者模式
 * 依赖关系:
 *   - 被Cache接口使用
 *   - 提供键生成和管理功能
 *   - 支持分布式缓存的键空间管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * 缓存键抽象
 * <p>
 * 提供缓存键的标准实现，支持命名空间、版本控制、序列化等功能。
 * 通过不可变设计确保键的一致性和线程安全性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class CacheKey implements Serializable, Comparable<CacheKey> {

    private static final long serialVersionUID = 1L;

    /**
     * 默认命名空间
     */
    private static final String DEFAULT_NAMESPACE = "default";

    /**
     * 键分隔符
     */
    private static final String KEY_SEPARATOR = ":";

    /**
     * 命名空间
     */
    private final String namespace;

    /**
     * 键名称
     */
    private final String key;

    /**
     * 版本号
     */
    private final String version;

    /**
     * 完整键名（缓存的）
     */
    private final String fullKey;

    /**
     * 哈希码（缓存的）
     */
    private final int hashCode;

    /**
     * 私有构造函数
     *
     * @param namespace 命名空间
     * @param key       键名称
     * @param version   版本号
     */
    private CacheKey(String namespace, String key, String version) {
        this.namespace = Objects.requireNonNull(namespace, "命名空间不能为null");
        this.key = Objects.requireNonNull(key, "键名称不能为null");
        this.version = version;
        this.fullKey = buildFullKey();
        this.hashCode = this.fullKey.hashCode();
    }

    /**
     * 创建缓存键
     *
     * @param key 键名称
     * @return 缓存键
     */
    public static CacheKey of(String key) {
        return new CacheKey(DEFAULT_NAMESPACE, key, null);
    }

    /**
     * 创建带命名空间的缓存键
     *
     * @param namespace 命名空间
     * @param key       键名称
     * @return 缓存键
     */
    public static CacheKey of(String namespace, String key) {
        return new CacheKey(namespace, key, null);
    }

    /**
     * 创建带版本的缓存键
     *
     * @param namespace 命名空间
     * @param key       键名称
     * @param version   版本号
     * @return 缓存键
     */
    public static CacheKey of(String namespace, String key, String version) {
        return new CacheKey(namespace, key, version);
    }

    /**
     * 从完整键字符串解析缓存键
     *
     * @param fullKey 完整键字符串
     * @return 缓存键
     */
    public static CacheKey parse(String fullKey) {
        if (fullKey == null || fullKey.trim().isEmpty()) {
            throw new IllegalArgumentException("完整键不能为空");
        }

        String[] parts = fullKey.split(KEY_SEPARATOR);
        switch (parts.length) {
            case 1:
                return of(parts[0]);
            case 2:
                return of(parts[0], parts[1]);
            case 3:
                return of(parts[0], parts[1], parts[2]);
            default:
                // 如果有更多部分，将后面的部分合并为key
                String namespace = parts[0];
                String key = String.join(KEY_SEPARATOR, 
                    java.util.Arrays.copyOfRange(parts, 1, parts.length - 1));
                String version = parts[parts.length - 1];
                return of(namespace, key, version);
        }
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
     * 构建完整键
     */
    private String buildFullKey() {
        StringBuilder builder = new StringBuilder();
        builder.append(namespace);
        builder.append(KEY_SEPARATOR);
        builder.append(key);
        if (version != null && !version.trim().isEmpty()) {
            builder.append(KEY_SEPARATOR);
            builder.append(version);
        }
        return builder.toString();
    }

    /**
     * 获取命名空间
     *
     * @return 命名空间
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * 获取键名称
     *
     * @return 键名称
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取版本号
     *
     * @return 版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取完整键
     *
     * @return 完整键
     */
    public String getFullKey() {
        return fullKey;
    }

    /**
     * 检查是否有版本
     *
     * @return 如果有版本返回true
     */
    public boolean hasVersion() {
        return version != null && !version.trim().isEmpty();
    }

    /**
     * 创建新版本的键
     *
     * @param newVersion 新版本号
     * @return 新的缓存键
     */
    public CacheKey withVersion(String newVersion) {
        return new CacheKey(namespace, key, newVersion);
    }

    /**
     * 创建新命名空间的键
     *
     * @param newNamespace 新命名空间
     * @return 新的缓存键
     */
    public CacheKey withNamespace(String newNamespace) {
        return new CacheKey(newNamespace, key, version);
    }

    /**
     * 创建子键
     *
     * @param subKey 子键名称
     * @return 新的缓存键
     */
    public CacheKey subKey(String subKey) {
        String newKey = key + KEY_SEPARATOR + subKey;
        return new CacheKey(namespace, newKey, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CacheKey cacheKey = (CacheKey) obj;
        return Objects.equals(fullKey, cacheKey.fullKey);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return fullKey;
    }

    @Override
    public int compareTo(CacheKey other) {
        if (other == null) {
            return 1;
        }
        return this.fullKey.compareTo(other.fullKey);
    }

    /**
     * 缓存键构建器
     */
    public static class Builder {
        private String namespace = DEFAULT_NAMESPACE;
        private String key;
        private String version;

        /**
         * 设置命名空间
         *
         * @param namespace 命名空间
         * @return 构建器
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * 设置键名称
         *
         * @param key 键名称
         * @return 构建器
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * 设置版本号
         *
         * @param version 版本号
         * @return 构建器
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * 构建缓存键
         *
         * @return 缓存键
         */
        public CacheKey build() {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("键名称不能为空");
            }
            return new CacheKey(namespace, key, version);
        }
    }
}