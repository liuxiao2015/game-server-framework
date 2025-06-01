/*
 * 文件名: CacheLoader.java
 * 用途: 缓存加载器
 * 实现内容:
 *   - 定义缓存加载的标准接口
 *   - 支持懒加载和预加载机制
 *   - 批量加载和异步加载支持
 *   - 加载失败处理和重试机制
 *   - 刷新策略和缓存更新
 * 技术选型:
 *   - 函数式接口设计
 *   - CompletableFuture异步支持
 *   - 异常处理机制
 *   - 泛型类型安全
 * 依赖关系:
 *   - 被LocalCache使用
 *   - 提供数据加载功能
 *   - 支持缓存预热
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.local;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 缓存加载器
 * <p>
 * 定义缓存数据加载的标准接口，支持懒加载、批量加载、异步加载等功能。
 * 当缓存未命中时，通过加载器从数据源获取数据并存储到缓存中。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@FunctionalInterface
public interface CacheLoader<K, V> {

    /**
     * 加载缓存值
     * <p>
     * 当缓存中不存在指定键时，通过此方法从数据源加载值。
     * 实现类应该确保此方法的线程安全性。
     * </p>
     *
     * @param key 缓存键
     * @return 缓存值
     * @throws Exception 加载失败时抛出异常
     */
    V load(K key) throws Exception;

    /**
     * 批量加载缓存值
     * <p>
     * 默认实现：循环调用单个加载方法。
     * 子类可以重写此方法以提供更高效的批量加载实现。
     * </p>
     *
     * @param keys 缓存键集合
     * @return 键值对映射
     * @throws Exception 加载失败时抛出异常
     */
    default Map<K, V> loadAll(Collection<K> keys) throws Exception {
        Map<K, V> result = new java.util.LinkedHashMap<>();
        for (K key : keys) {
            try {
                V value = load(key);
                if (value != null) {
                    result.put(key, value);
                }
            } catch (Exception e) {
                // 记录错误但继续处理其他键
                throw new RuntimeException("批量加载失败，键: " + key, e);
            }
        }
        return result;
    }

    /**
     * 异步加载缓存值
     * <p>
     * 默认实现：将同步加载包装为异步执行。
     * 子类可以重写此方法以提供真正的异步加载实现。
     * </p>
     *
     * @param key 缓存键
     * @return 缓存值的异步结果
     */
    default CompletableFuture<V> loadAsync(K key) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return load(key);
            } catch (Exception e) {
                throw new RuntimeException("异步加载失败", e);
            }
        });
    }

    /**
     * 异步批量加载缓存值
     * <p>
     * 默认实现：将同步批量加载包装为异步执行。
     * 子类可以重写此方法以提供真正的异步批量加载实现。
     * </p>
     *
     * @param keys 缓存键集合
     * @return 键值对映射的异步结果
     */
    default CompletableFuture<Map<K, V>> loadAllAsync(Collection<K> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadAll(keys);
            } catch (Exception e) {
                throw new RuntimeException("异步批量加载失败", e);
            }
        });
    }

    /**
     * 重新加载缓存值
     * <p>
     * 用于刷新已存在的缓存值。默认实现调用load方法。
     * 子类可以重写此方法以提供不同的刷新逻辑。
     * </p>
     *
     * @param key      缓存键
     * @param oldValue 旧值
     * @return 新的缓存值
     * @throws Exception 重新加载失败时抛出异常
     */
    default V reload(K key, V oldValue) throws Exception {
        return load(key);
    }

    /**
     * 异步重新加载缓存值
     * <p>
     * 默认实现：将同步重新加载包装为异步执行。
     * 子类可以重写此方法以提供真正的异步重新加载实现。
     * </p>
     *
     * @param key      缓存键
     * @param oldValue 旧值
     * @return 新缓存值的异步结果
     */
    default CompletableFuture<V> reloadAsync(K key, V oldValue) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return reload(key, oldValue);
            } catch (Exception e) {
                throw new RuntimeException("异步重新加载失败", e);
            }
        });
    }

    /**
     * 创建简单的缓存加载器
     *
     * @param loader 加载函数
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存加载器
     */
    static <K, V> CacheLoader<K, V> of(java.util.function.Function<K, V> loader) {
        return loader::apply;
    }

    /**
     * 创建可能抛出异常的缓存加载器
     *
     * @param loader 加载函数
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存加载器
     */
    static <K, V> CacheLoader<K, V> ofThrowing(ThrowingFunction<K, V> loader) {
        return loader::apply;
    }

    /**
     * 创建带重试机制的缓存加载器
     *
     * @param loader       原始加载器
     * @param maxRetries   最大重试次数
     * @param retryDelayMs 重试延迟毫秒数
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 带重试的缓存加载器
     */
    static <K, V> CacheLoader<K, V> withRetry(CacheLoader<K, V> loader, int maxRetries, long retryDelayMs) {
        return new RetryingCacheLoader<>(loader, maxRetries, retryDelayMs);
    }

    /**
     * 创建带超时的缓存加载器
     *
     * @param loader    原始加载器
     * @param timeoutMs 超时毫秒数
     * @param <K>       键类型
     * @param <V>       值类型
     * @return 带超时的缓存加载器
     */
    static <K, V> CacheLoader<K, V> withTimeout(CacheLoader<K, V> loader, long timeoutMs) {
        return new TimeoutCacheLoader<>(loader, timeoutMs);
    }

    /**
     * 可能抛出异常的函数接口
     *
     * @param <K> 键类型
     * @param <V> 值类型
     */
    @FunctionalInterface
    interface ThrowingFunction<K, V> {
        V apply(K key) throws Exception;
    }

    /**
     * 带重试机制的缓存加载器实现
     */
    class RetryingCacheLoader<K, V> implements CacheLoader<K, V> {
        private final CacheLoader<K, V> delegate;
        private final int maxRetries;
        private final long retryDelayMs;

        public RetryingCacheLoader(CacheLoader<K, V> delegate, int maxRetries, long retryDelayMs) {
            this.delegate = delegate;
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        @Override
        public V load(K key) throws Exception {
            Exception lastException = null;
            for (int i = 0; i <= maxRetries; i++) {
                try {
                    return delegate.load(key);
                } catch (Exception e) {
                    lastException = e;
                    if (i < maxRetries) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("加载被中断", ie);
                        }
                    }
                }
            }
            throw new RuntimeException("重试" + maxRetries + "次后仍然失败", lastException);
        }
    }

    /**
     * 带超时的缓存加载器实现
     */
    class TimeoutCacheLoader<K, V> implements CacheLoader<K, V> {
        private final CacheLoader<K, V> delegate;
        private final long timeoutMs;

        public TimeoutCacheLoader(CacheLoader<K, V> delegate, long timeoutMs) {
            this.delegate = delegate;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public V load(K key) throws Exception {
            return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return delegate.load(key);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .get();
        }
    }
}