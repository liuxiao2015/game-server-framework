/*
 * 文件名: CacheTemplate.java
 * 用途: 缓存操作模板
 * 实现内容:
 *   - 简化缓存操作API
 *   - 类型安全的缓存操作
 *   - 统一异常处理
 *   - 默认行为配置
 *   - 扩展点支持
 * 技术选型:
 *   - 模板方法模式
 *   - 泛型类型安全
 *   - CompletableFuture异步支持
 *   - 策略模式扩展
 * 依赖关系:
 *   - 封装Cache接口
 *   - 提供便捷操作方法
 *   - 与配置系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.template;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 缓存操作模板
 * <p>
 * 提供简化的缓存操作API，封装常用的缓存操作模式，
 * 提供类型安全和异常处理功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheTemplate {

    private static final Logger logger = LoggerFactory.getLogger(CacheTemplate.class);

    /**
     * 缓存实例
     */
    private final Cache<CacheKey, Object> cache;

    /**
     * 默认过期时间
     */
    private final Duration defaultExpiration;

    /**
     * 是否启用空值缓存
     */
    private final boolean cacheNullValues;

    /**
     * 异常处理策略
     */
    private final ExceptionHandler exceptionHandler;

    /**
     * 构造函数
     *
     * @param cache 缓存实例
     */
    public CacheTemplate(Cache<CacheKey, Object> cache) {
        this(cache, Duration.ofMinutes(30), false, new DefaultExceptionHandler());
    }

    /**
     * 构造函数
     *
     * @param cache             缓存实例
     * @param defaultExpiration 默认过期时间
     * @param cacheNullValues   是否缓存空值
     * @param exceptionHandler  异常处理器
     */
    public CacheTemplate(Cache<CacheKey, Object> cache, Duration defaultExpiration,
                        boolean cacheNullValues, ExceptionHandler exceptionHandler) {
        this.cache = cache;
        this.defaultExpiration = defaultExpiration;
        this.cacheNullValues = cacheNullValues;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * 获取缓存值
     *
     * @param key  缓存键
     * @param type 值类型
     * @param <T>  类型参数
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return execute("get", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            Object value = cache.get(cacheKey);
            return castValue(value, type);
        });
    }

    /**
     * 获取缓存值，如果不存在则加载
     *
     * @param key    缓存键
     * @param type   值类型
     * @param loader 加载器
     * @param <T>    类型参数
     * @return 缓存值
     */
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        return get(key, type, loader, defaultExpiration);
    }

    /**
     * 获取缓存值，如果不存在则加载并指定过期时间
     *
     * @param key        缓存键
     * @param type       值类型
     * @param loader     加载器
     * @param expiration 过期时间
     * @param <T>        类型参数
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, Supplier<T> loader, Duration expiration) {
        return execute("get-with-loader", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            
            return (T) cache.get(cacheKey, k -> {
                T value = loader.get();
                
                // 检查是否缓存空值
                if (value == null && !cacheNullValues) {
                    return null;
                }
                
                return value;
            });
        });
    }

    /**
     * 异步获取缓存值
     *
     * @param key  缓存键
     * @param type 值类型
     * @param <T>  类型参数
     * @return 异步结果
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getAsync(String key, Class<T> type) {
        return executeAsync("getAsync", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            return cache.getAsync(cacheKey)
                .thenApply(value -> castValue(value, type));
        });
    }

    /**
     * 异步获取缓存值，如果不存在则加载
     *
     * @param key    缓存键
     * @param type   值类型
     * @param loader 异步加载器
     * @param <T>    类型参数
     * @return 异步结果
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getAsync(String key, Class<T> type, 
                                           Supplier<CompletableFuture<T>> loader) {
        return executeAsync("getAsync-with-loader", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            
            return cache.getAsync(cacheKey, k -> {
                return loader.get().thenApply(value -> {
                    if (value == null && !cacheNullValues) {
                        return null;
                    }
                    return (Object) value;
                });
            }).thenApply(value -> castValue(value, type));
        });
    }

    /**
     * 存储缓存值
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        put(key, value, defaultExpiration);
    }

    /**
     * 存储缓存值并指定过期时间
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param expiration 过期时间
     */
    public void put(String key, Object value, Duration expiration) {
        execute("put", key, () -> {
            if (value == null && !cacheNullValues) {
                return null;
            }
            
            CacheKey cacheKey = CacheKey.of(key);
            cache.put(cacheKey, value, expiration);
            return null;
        });
    }

    /**
     * 异步存储缓存值
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 异步结果
     */
    public CompletableFuture<Void> putAsync(String key, Object value) {
        return putAsync(key, value, defaultExpiration);
    }

    /**
     * 异步存储缓存值并指定过期时间
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param expiration 过期时间
     * @return 异步结果
     */
    public CompletableFuture<Void> putAsync(String key, Object value, Duration expiration) {
        return executeAsync("putAsync", key, () -> {
            if (value == null && !cacheNullValues) {
                return CompletableFuture.completedFuture(null);
            }
            
            CacheKey cacheKey = CacheKey.of(key);
            return cache.putAsync(cacheKey, value, expiration);
        });
    }

    /**
     * 仅当键不存在时存储
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 是否成功存储
     */
    public boolean putIfAbsent(String key, Object value) {
        return putIfAbsent(key, value, defaultExpiration);
    }

    /**
     * 仅当键不存在时存储并指定过期时间
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param expiration 过期时间
     * @return 是否成功存储
     */
    public boolean putIfAbsent(String key, Object value, Duration expiration) {
        return execute("putIfAbsent", key, () -> {
            if (value == null && !cacheNullValues) {
                return false;
            }
            
            CacheKey cacheKey = CacheKey.of(key);
            return cache.putIfAbsent(cacheKey, value, expiration);
        });
    }

    /**
     * 移除缓存项
     *
     * @param key  缓存键
     * @param type 值类型
     * @param <T>  类型参数
     * @return 被移除的值
     */
    @SuppressWarnings("unchecked")
    public <T> T remove(String key, Class<T> type) {
        return execute("remove", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            Object value = cache.remove(cacheKey);
            return castValue(value, type);
        });
    }

    /**
     * 异步移除缓存项
     *
     * @param key  缓存键
     * @param type 值类型
     * @param <T>  类型参数
     * @return 异步结果
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> removeAsync(String key, Class<T> type) {
        return executeAsync("removeAsync", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            return cache.removeAsync(cacheKey)
                .thenApply(value -> castValue(value, type));
        });
    }

    /**
     * 检查键是否存在
     *
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        return execute("hasKey", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            return cache.containsKey(cacheKey);
        });
    }

    /**
     * 刷新缓存项
     *
     * @param key 缓存键
     */
    public void refresh(String key) {
        execute("refresh", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            cache.refresh(cacheKey);
            return null;
        });
    }

    /**
     * 异步刷新缓存项
     *
     * @param key 缓存键
     * @return 异步结果
     */
    public CompletableFuture<Void> refreshAsync(String key) {
        return executeAsync("refreshAsync", key, () -> {
            CacheKey cacheKey = CacheKey.of(key);
            return cache.refreshAsync(cacheKey);
        });
    }

    /**
     * 批量获取
     *
     * @param keys 缓存键集合
     * @param type 值类型
     * @param <T>  类型参数
     * @return 键值映射
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> multiGet(Collection<String> keys, Class<T> type) {
        return execute("multiGet", keys.toString(), () -> {
            Set<CacheKey> cacheKeys = new HashSet<>();
            for (String key : keys) {
                cacheKeys.add(CacheKey.of(key));
            }
            
            Map<CacheKey, Object> values = cache.getAll(cacheKeys);
            Map<String, T> result = new HashMap<>();
            
            for (Map.Entry<CacheKey, Object> entry : values.entrySet()) {
                String originalKey = entry.getKey().getKey();
                T value = castValue(entry.getValue(), type);
                if (value != null || cacheNullValues) {
                    result.put(originalKey, value);
                }
            }
            
            return result;
        });
    }

    /**
     * 批量存储
     *
     * @param keyValueMap 键值映射
     */
    public void multiPut(Map<String, Object> keyValueMap) {
        execute("multiPut", keyValueMap.keySet().toString(), () -> {
            Map<CacheKey, Object> cacheMap = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : keyValueMap.entrySet()) {
                Object value = entry.getValue();
                if (value != null || cacheNullValues) {
                    cacheMap.put(CacheKey.of(entry.getKey()), value);
                }
            }
            
            cache.putAll(cacheMap);
            return null;
        });
    }

    /**
     * 批量移除
     *
     * @param keys 缓存键集合
     */
    public void multiRemove(Collection<String> keys) {
        execute("multiRemove", keys.toString(), () -> {
            Set<CacheKey> cacheKeys = new HashSet<>();
            for (String key : keys) {
                cacheKeys.add(CacheKey.of(key));
            }
            
            cache.removeAll(cacheKeys);
            return null;
        });
    }

    /**
     * 清空缓存
     */
    public void clear() {
        execute("clear", "all", () -> {
            cache.clear();
            return null;
        });
    }

    /**
     * 获取缓存大小
     *
     * @return 缓存大小
     */
    public long size() {
        return execute("size", "all", () -> cache.size());
    }

    /**
     * 获取底层缓存实例
     *
     * @return 缓存实例
     */
    public Cache<CacheKey, Object> getCache() {
        return cache;
    }

    /**
     * 执行操作并处理异常
     */
    private <T> T execute(String operation, String key, Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            return exceptionHandler.handleException(operation, key, e);
        }
    }

    /**
     * 异步执行操作并处理异常
     */
    private <T> CompletableFuture<T> executeAsync(String operation, String key, 
                                                 Supplier<CompletableFuture<T>> action) {
        try {
            return action.get().exceptionally(throwable -> 
                exceptionHandler.handleException(operation, key, (Exception) throwable));
        } catch (Exception e) {
            T result = exceptionHandler.handleException(operation, key, e);
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * 类型转换
     */
    @SuppressWarnings("unchecked")
    private <T> T castValue(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        // 可以在这里添加更多的类型转换逻辑
        throw new ClassCastException("Cannot cast " + value.getClass() + " to " + type);
    }

    /**
     * 异常处理器接口
     */
    public interface ExceptionHandler {
        /**
         * 处理异常
         *
         * @param operation 操作名称
         * @param key       缓存键
         * @param exception 异常
         * @param <T>       返回类型
         * @return 默认值或处理结果
         */
        <T> T handleException(String operation, String key, Exception exception);
    }

    /**
     * 默认异常处理器
     */
    public static class DefaultExceptionHandler implements ExceptionHandler {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T handleException(String operation, String key, Exception exception) {
            logger.error("缓存操作异常: operation={}, key={}", operation, key, exception);
            return null; // 返回null作为默认值
        }
    }

    /**
     * 抛出异常的异常处理器
     */
    public static class ThrowingExceptionHandler implements ExceptionHandler {
        @Override
        public <T> T handleException(String operation, String key, Exception exception) {
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            } else {
                throw new RuntimeException("缓存操作失败: " + operation, exception);
            }
        }
    }

    /**
     * 缓存模板构建器
     */
    public static class Builder {
        private Cache<CacheKey, Object> cache;
        private Duration defaultExpiration = Duration.ofMinutes(30);
        private boolean cacheNullValues = false;
        private ExceptionHandler exceptionHandler = new DefaultExceptionHandler();

        public Builder cache(Cache<CacheKey, Object> cache) {
            this.cache = cache;
            return this;
        }

        public Builder defaultExpiration(Duration defaultExpiration) {
            this.defaultExpiration = defaultExpiration;
            return this;
        }

        public Builder cacheNullValues(boolean cacheNullValues) {
            this.cacheNullValues = cacheNullValues;
            return this;
        }

        public Builder exceptionHandler(ExceptionHandler exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public CacheTemplate build() {
            Objects.requireNonNull(cache, "Cache cannot be null");
            return new CacheTemplate(cache, defaultExpiration, cacheNullValues, exceptionHandler);
        }
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }
}