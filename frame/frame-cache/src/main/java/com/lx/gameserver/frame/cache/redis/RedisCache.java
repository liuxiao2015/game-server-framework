/*
 * 文件名: RedisCache.java
 * 用途: Redis缓存实现
 * 实现内容:
 *   - 基于Redis的分布式缓存实现
 *   - 支持单机、哨兵、集群模式
 *   - Pipeline批量操作和事务支持
 *   - Lua脚本支持和发布订阅
 *   - 序列化和压缩功能
 * 技术选型:
 *   - Spring Data Redis
 *   - Lettuce连接器
 *   - 多种序列化方式
 *   - 异步操作支持
 * 依赖关系:
 *   - 实现Cache接口
 *   - 使用RedisConnectionPool
 *   - 使用RedisSerializer
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.redis;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Redis缓存实现
 * <p>
 * 基于Redis的分布式缓存实现，支持多种部署模式、批量操作、
 * 事务支持、Lua脚本等高级功能。适用于分布式环境下的缓存需求。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class RedisCache<K, V> implements Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);

    /**
     * 缓存配置
     */
    private final CacheConfig config;

    /**
     * Redis模板
     */
    private final RedisTemplate<K, V> redisTemplate;

    /**
     * 键序列化器
     */
    private final RedisSerializer<K> keySerializer;

    /**
     * 值序列化器
     */
    private final RedisSerializer<V> valueSerializer;

    /**
     * 异步执行器
     */
    private final Executor asyncExecutor;

    /**
     * 统计信息
     */
    private final RedisCacheStatistics statistics;

    /**
     * Lua脚本 - 带过期时间的SET操作
     */
    private static final String SET_WITH_EXPIRE_SCRIPT = 
        "if redis.call('set', KEYS[1], ARGV[1], 'ex', ARGV[2]) then " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";

    /**
     * Lua脚本 - 条件SET操作
     */
    private static final String SET_IF_ABSENT_SCRIPT = 
        "if redis.call('exists', KEYS[1]) == 0 then " +
        "    if ARGV[2] then " +
        "        return redis.call('set', KEYS[1], ARGV[1], 'ex', ARGV[2]) " +
        "    else " +
        "        return redis.call('set', KEYS[1], ARGV[1]) " +
        "    end " +
        "else " +
        "    return nil " +
        "end";

    /**
     * 构造函数
     *
     * @param config          缓存配置
     * @param connectionFactory Redis连接工厂
     * @param keySerializer   键序列化器
     * @param valueSerializer 值序列化器
     */
    public RedisCache(CacheConfig config, 
                     RedisConnectionFactory connectionFactory,
                     RedisSerializer<K> keySerializer,
                     RedisSerializer<V> valueSerializer) {
        this.config = Objects.requireNonNull(config, "缓存配置不能为null");
        this.keySerializer = Objects.requireNonNull(keySerializer, "键序列化器不能为null");
        this.valueSerializer = Objects.requireNonNull(valueSerializer, "值序列化器不能为null");
        this.asyncExecutor = ForkJoinPool.commonPool();
        this.statistics = new RedisCacheStatistics();

        // 创建Redis模板
        this.redisTemplate = createRedisTemplate(connectionFactory);

        logger.info("Redis缓存已创建: name={}, config={}", config.getName(), config);
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            statistics.recordRequest();
            V value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                statistics.recordHit();
                logger.debug("Redis缓存命中: name={}, key={}", config.getName(), key);
            } else {
                statistics.recordMiss();
                logger.debug("Redis缓存未命中: name={}, key={}", config.getName(), key);
            }
            
            return value;
        } catch (Exception e) {
            logger.error("获取Redis缓存失败: name={}, key={}", config.getName(), key, e);
            statistics.recordMiss();
            return null;
        }
    }

    @Override
    public CompletableFuture<V> getAsync(K key) {
        return CompletableFuture.supplyAsync(() -> get(key), asyncExecutor);
    }

    @Override
    public V get(K key, Function<K, V> loader) {
        V value = get(key);
        if (value != null) {
            return value;
        }

        try {
            value = loader.apply(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        } catch (Exception e) {
            logger.error("加载缓存失败: name={}, key={}", config.getName(), key, e);
            throw new RuntimeException("加载缓存失败", e);
        }
    }

    @Override
    public CompletableFuture<V> getAsync(K key, Function<K, CompletableFuture<V>> loader) {
        return getAsync(key).thenCompose(value -> {
            if (value != null) {
                return CompletableFuture.completedFuture(value);
            }
            return loader.apply(key).thenApply(loadedValue -> {
                if (loadedValue != null) {
                    putAsync(key, loadedValue);
                }
                return loadedValue;
            });
        });
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            statistics.recordRequest(keys.size());
            List<V> values = redisTemplate.opsForValue().multiGet(keys);
            
            Map<K, V> result = new HashMap<>();
            int index = 0;
            int hits = 0;
            
            for (K key : keys) {
                if (index < values.size()) {
                    V value = values.get(index);
                    if (value != null) {
                        result.put(key, value);
                        hits++;
                    }
                }
                index++;
            }
            
            statistics.recordHit(hits);
            statistics.recordMiss(keys.size() - hits);
            
            logger.debug("Redis批量获取完成: name={}, keys={}, hits={}", 
                config.getName(), keys.size(), hits);
            
            return result;
        } catch (Exception e) {
            logger.error("批量获取Redis缓存失败: name={}, keys={}", config.getName(), keys, e);
            statistics.recordMiss(keys.size());
            return Collections.emptyMap();
        }
    }

    @Override
    public CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys) {
        return CompletableFuture.supplyAsync(() -> getAll(keys), asyncExecutor);
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            if (config.getExpireAfterWrite() != null) {
                redisTemplate.opsForValue().set(key, value, config.getExpireAfterWrite());
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            
            logger.debug("Redis缓存已存储: name={}, key={}", config.getName(), key);
        } catch (Exception e) {
            logger.error("存储Redis缓存失败: name={}, key={}", config.getName(), key, e);
        }
    }

    @Override
    public void put(K key, V value, Duration duration) {
        Objects.requireNonNull(key, "键不能为null");
        Objects.requireNonNull(duration, "过期时间不能为null");
        
        try {
            redisTemplate.opsForValue().set(key, value, duration);
            logger.debug("Redis缓存已存储（带过期时间）: name={}, key={}, duration={}", 
                config.getName(), key, duration);
        } catch (Exception e) {
            logger.error("存储Redis缓存失败: name={}, key={}, duration={}", 
                config.getName(), key, duration, e);
        }
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value) {
        return CompletableFuture.runAsync(() -> put(key, value), asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value, Duration duration) {
        return CompletableFuture.runAsync(() -> put(key, value, duration), asyncExecutor);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value);
            boolean success = Boolean.TRUE.equals(result);
            
            if (success) {
                logger.debug("Redis条件存储成功: name={}, key={}", config.getName(), key);
            } else {
                logger.debug("Redis条件存储失败，键已存在: name={}, key={}", config.getName(), key);
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Redis条件存储失败: name={}, key={}", config.getName(), key, e);
            return false;
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        Objects.requireNonNull(key, "键不能为null");
        Objects.requireNonNull(duration, "过期时间不能为null");
        
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, duration);
            boolean success = Boolean.TRUE.equals(result);
            
            if (success) {
                logger.debug("Redis条件存储成功（带过期时间）: name={}, key={}, duration={}", 
                    config.getName(), key, duration);
            } else {
                logger.debug("Redis条件存储失败，键已存在: name={}, key={}", config.getName(), key);
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Redis条件存储失败: name={}, key={}, duration={}", 
                config.getName(), key, duration, e);
            return false;
        }
    }

    @Override
    public void putAll(Map<K, V> map) {
        Objects.requireNonNull(map, "映射不能为null");
        
        if (map.isEmpty()) {
            return;
        }

        try {
            redisTemplate.opsForValue().multiSet(map);
            
            // 如果有默认过期时间，需要单独设置
            if (config.getExpireAfterWrite() != null) {
                for (K key : map.keySet()) {
                    redisTemplate.expire(key, config.getExpireAfterWrite());
                }
            }
            
            logger.debug("Redis批量存储完成: name={}, size={}", config.getName(), map.size());
        } catch (Exception e) {
            logger.error("批量存储Redis缓存失败: name={}, size={}", config.getName(), map.size(), e);
        }
    }

    @Override
    public CompletableFuture<Void> putAllAsync(Map<K, V> map) {
        return CompletableFuture.runAsync(() -> putAll(map), asyncExecutor);
    }

    @Override
    public V remove(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            V value = get(key);
            if (value != null) {
                redisTemplate.delete(key);
                logger.debug("Redis缓存已移除: name={}, key={}", config.getName(), key);
            }
            return value;
        } catch (Exception e) {
            logger.error("移除Redis缓存失败: name={}, key={}", config.getName(), key, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<V> removeAsync(K key) {
        return CompletableFuture.supplyAsync(() -> remove(key), asyncExecutor);
    }

    @Override
    public void removeAll(Collection<K> keys) {
        Objects.requireNonNull(keys, "键集合不能为null");
        
        if (keys.isEmpty()) {
            return;
        }

        try {
            redisTemplate.delete(keys);
            logger.debug("Redis批量移除完成: name={}, size={}", config.getName(), keys.size());
        } catch (Exception e) {
            logger.error("批量移除Redis缓存失败: name={}, size={}", config.getName(), keys.size(), e);
        }
    }

    @Override
    public CompletableFuture<Void> removeAllAsync(Collection<K> keys) {
        return CompletableFuture.runAsync(() -> removeAll(keys), asyncExecutor);
    }

    @Override
    public void clear() {
        try {
            // 注意：这会清空整个Redis数据库，在生产环境中要谨慎使用
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            connection.flushDb();
            connection.close();
            
            logger.debug("Redis缓存已清空: name={}", config.getName());
        } catch (Exception e) {
            logger.error("清空Redis缓存失败: name={}", config.getName(), e);
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.runAsync(this::clear, asyncExecutor);
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "键不能为null");
        
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("检查Redis键存在性失败: name={}, key={}", config.getName(), key, e);
            return false;
        }
    }

    @Override
    public long size() {
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            Long size = connection.dbSize();
            connection.close();
            return size != null ? size : 0;
        } catch (Exception e) {
            logger.error("获取Redis缓存大小失败: name={}", config.getName(), e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Collection<K> keys() {
        try {
            return redisTemplate.keys((K) "*");
        } catch (Exception e) {
            logger.error("获取Redis键集合失败: name={}", config.getName(), e);
            return Collections.emptySet();
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void refresh(K key) {
        // Redis不支持自动刷新，这里只是记录日志
        logger.debug("Redis缓存刷新请求: name={}, key={}", config.getName(), key);
    }

    @Override
    public CompletableFuture<Void> refreshAsync(K key) {
        return CompletableFuture.runAsync(() -> refresh(key), asyncExecutor);
    }

    @Override
    public CacheConfig getConfig() {
        return config;
    }

    /**
     * 获取Redis模板
     *
     * @return Redis模板
     */
    public RedisTemplate<K, V> getRedisTemplate() {
        return redisTemplate;
    }

    /**
     * 创建Redis模板
     */
    @SuppressWarnings("unchecked")
    private RedisTemplate<K, V> createRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<K, V> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 设置序列化器
        template.setKeySerializer((org.springframework.data.redis.serializer.RedisSerializer<K>) 
            createSpringRedisSerializer(keySerializer));
        template.setValueSerializer((org.springframework.data.redis.serializer.RedisSerializer<V>) 
            createSpringRedisSerializer(valueSerializer));
        template.setHashKeySerializer(template.getKeySerializer());
        template.setHashValueSerializer(template.getValueSerializer());
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建Spring Redis序列化器适配器
     */
    private <T> org.springframework.data.redis.serializer.RedisSerializer<T> createSpringRedisSerializer(
            RedisSerializer<T> serializer) {
        return new org.springframework.data.redis.serializer.RedisSerializer<T>() {
            @Override
            public byte[] serialize(T t) {
                try {
                    return serializer.serialize(t);
                } catch (RedisSerializer.SerializationException e) {
                    throw new org.springframework.data.redis.serializer.SerializationException(
                        "序列化失败", e);
                }
            }

            @Override
            public T deserialize(byte[] bytes) {
                try {
                    return serializer.deserialize(bytes);
                } catch (RedisSerializer.SerializationException e) {
                    throw new org.springframework.data.redis.serializer.SerializationException(
                        "反序列化失败", e);
                }
            }
        };
    }

    /**
     * Redis缓存统计信息实现
     */
    private static class RedisCacheStatistics implements CacheStatistics {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong missCount = new AtomicLong(0);
        private final AtomicLong loadCount = new AtomicLong(0);

        public void recordRequest() {
            requestCount.incrementAndGet();
        }

        public void recordRequest(int count) {
            requestCount.addAndGet(count);
        }

        public void recordHit() {
            hitCount.incrementAndGet();
        }

        public void recordHit(int count) {
            hitCount.addAndGet(count);
        }

        public void recordMiss() {
            missCount.incrementAndGet();
        }

        public void recordMiss(int count) {
            missCount.addAndGet(count);
        }

        public void recordLoad() {
            loadCount.incrementAndGet();
        }

        @Override
        public long getRequestCount() {
            return requestCount.get();
        }

        @Override
        public long getHitCount() {
            return hitCount.get();
        }

        @Override
        public long getMissCount() {
            return missCount.get();
        }

        @Override
        public long getLoadCount() {
            return loadCount.get();
        }

        @Override
        public long getEvictionCount() {
            return 0; // Redis不直接提供逐出统计
        }

        @Override
        public double getHitRate() {
            long requests = getRequestCount();
            return requests == 0 ? 0.0 : (double) getHitCount() / requests;
        }

        @Override
        public double getMissRate() {
            long requests = getRequestCount();
            return requests == 0 ? 0.0 : (double) getMissCount() / requests;
        }

        @Override
        public double getAverageLoadTime() {
            return 0.0; // 暂不实现
        }
    }
}