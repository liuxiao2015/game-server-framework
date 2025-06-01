/*
 * 文件名: CacheProvider.java
 * 用途: 缓存提供者
 * 实现内容:
 *   - SPI机制自动发现
 *   - 缓存实现工厂
 *   - 优先级管理
 *   - 提供者注册机制
 *   - 配置驱动创建
 * 技术选型:
 *   - Java SPI机制
 *   - 工厂模式
 *   - 策略模式
 *   - 反射和类加载
 * 依赖关系:
 *   - 提供Cache实现
 *   - 被CacheManager使用
 *   - 与配置系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.extension;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheConfig;
import com.lx.gameserver.frame.cache.core.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 缓存提供者
 * <p>
 * 基于SPI机制的缓存提供者接口，支持自动发现、优先级管理
 * 和动态注册等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface CacheProvider {

    /**
     * 获取提供者名称
     *
     * @return 提供者名称
     */
    String getName();

    /**
     * 获取提供者类型
     *
     * @return 提供者类型
     */
    String getType();

    /**
     * 获取优先级（数值越小优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 检查是否支持指定配置
     *
     * @param config 缓存配置
     * @return 是否支持
     */
    boolean supports(CacheConfig config);

    /**
     * 创建缓存实例
     *
     * @param config 缓存配置
     * @return 缓存实例
     */
    Cache<CacheKey, Object> createCache(CacheConfig config);

    /**
     * 获取提供者信息
     *
     * @return 提供者信息
     */
    default ProviderInfo getProviderInfo() {
        return new ProviderInfo(getName(), getType(), getPriority(), getClass().getName());
    }

    /**
     * 缓存提供者管理器
     */
    class CacheProviderManager {
        private static final Logger logger = LoggerFactory.getLogger(CacheProviderManager.class);

        /**
         * 已注册的提供者
         */
        private static final Map<String, CacheProvider> providers = new ConcurrentHashMap<>();

        /**
         * 提供者工厂
         */
        private static final Map<String, Supplier<CacheProvider>> providerFactories = new ConcurrentHashMap<>();

        static {
            // 自动发现并注册SPI提供者
            discoverProviders();
        }

        /**
         * 注册缓存提供者
         *
         * @param provider 缓存提供者
         */
        public static void registerProvider(CacheProvider provider) {
            providers.put(provider.getName(), provider);
            logger.info("注册缓存提供者: {} ({})", provider.getName(), provider.getType());
        }

        /**
         * 注册缓存提供者工厂
         *
         * @param name    提供者名称
         * @param factory 提供者工厂
         */
        public static void registerProviderFactory(String name, Supplier<CacheProvider> factory) {
            providerFactories.put(name, factory);
            logger.info("注册缓存提供者工厂: {}", name);
        }

        /**
         * 取消注册缓存提供者
         *
         * @param name 提供者名称
         */
        public static void unregisterProvider(String name) {
            CacheProvider removed = providers.remove(name);
            if (removed != null) {
                logger.info("取消注册缓存提供者: {}", name);
            }
        }

        /**
         * 获取缓存提供者
         *
         * @param name 提供者名称
         * @return 缓存提供者，如果不存在则返回null
         */
        public static CacheProvider getProvider(String name) {
            CacheProvider provider = providers.get(name);
            if (provider == null) {
                // 尝试从工厂创建
                Supplier<CacheProvider> factory = providerFactories.get(name);
                if (factory != null) {
                    try {
                        provider = factory.get();
                        if (provider != null) {
                            registerProvider(provider);
                        }
                    } catch (Exception e) {
                        logger.error("创建缓存提供者失败: {}", name, e);
                    }
                }
            }
            return provider;
        }

        /**
         * 获取所有已注册的提供者
         *
         * @return 提供者集合
         */
        public static Collection<CacheProvider> getAllProviders() {
            return new ArrayList<>(providers.values());
        }

        /**
         * 根据类型获取提供者
         *
         * @param type 提供者类型
         * @return 提供者列表
         */
        public static List<CacheProvider> getProvidersByType(String type) {
            return providers.values().stream()
                .filter(p -> type.equals(p.getType()))
                .sorted(Comparator.comparingInt(CacheProvider::getPriority))
                .toList();
        }

        /**
         * 查找最适合的提供者
         *
         * @param config 缓存配置
         * @return 最适合的提供者，如果没有找到则返回null
         */
        public static CacheProvider findBestProvider(CacheConfig config) {
            return providers.values().stream()
                .filter(p -> p.supports(config))
                .min(Comparator.comparingInt(CacheProvider::getPriority))
                .orElse(null);
        }

        /**
         * 创建缓存实例
         *
         * @param config 缓存配置
         * @return 缓存实例
         * @throws IllegalArgumentException 如果没有找到合适的提供者
         */
        public static Cache<CacheKey, Object> createCache(CacheConfig config) {
            CacheProvider provider = findBestProvider(config);
            if (provider == null) {
                throw new IllegalArgumentException("没有找到支持配置的缓存提供者: " + config.getName());
            }
            
            logger.debug("使用提供者 {} 创建缓存: {}", provider.getName(), config.getName());
            return provider.createCache(config);
        }

        /**
         * 创建缓存实例（指定提供者）
         *
         * @param providerName 提供者名称
         * @param config       缓存配置
         * @return 缓存实例
         * @throws IllegalArgumentException 如果提供者不存在或不支持配置
         */
        public static Cache<CacheKey, Object> createCache(String providerName, CacheConfig config) {
            CacheProvider provider = getProvider(providerName);
            if (provider == null) {
                throw new IllegalArgumentException("缓存提供者不存在: " + providerName);
            }
            
            if (!provider.supports(config)) {
                throw new IllegalArgumentException("提供者不支持此配置: " + providerName);
            }
            
            logger.debug("使用指定提供者 {} 创建缓存: {}", providerName, config.getName());
            return provider.createCache(config);
        }

        /**
         * 获取提供者信息列表
         *
         * @return 提供者信息列表
         */
        public static List<ProviderInfo> getProviderInfos() {
            return providers.values().stream()
                .map(CacheProvider::getProviderInfo)
                .sorted(Comparator.comparingInt(ProviderInfo::getPriority))
                .toList();
        }

        /**
         * 自动发现SPI提供者
         */
        private static void discoverProviders() {
            try {
                ServiceLoader<CacheProvider> serviceLoader = ServiceLoader.load(CacheProvider.class);
                for (CacheProvider provider : serviceLoader) {
                    registerProvider(provider);
                }
                logger.info("通过SPI发现 {} 个缓存提供者", providers.size());
            } catch (Exception e) {
                logger.warn("SPI自动发现缓存提供者失败", e);
            }
        }
    }

    /**
     * 提供者信息
     */
    class ProviderInfo {
        private final String name;
        private final String type;
        private final int priority;
        private final String className;

        public ProviderInfo(String name, String type, int priority, String className) {
            this.name = name;
            this.type = type;
            this.priority = priority;
            this.className = className;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public int getPriority() { return priority; }
        public String getClassName() { return className; }

        @Override
        public String toString() {
            return String.format("ProviderInfo{name='%s', type='%s', priority=%d, class='%s'}", 
                name, type, priority, className);
        }
    }

    /**
     * 抽象缓存提供者基类
     */
    abstract class AbstractCacheProvider implements CacheProvider {
        private final String name;
        private final String type;
        private final int priority;

        protected AbstractCacheProvider(String name, String type) {
            this(name, type, 100);
        }

        protected AbstractCacheProvider(String name, String type, int priority) {
            this.name = name;
            this.type = type;
            this.priority = priority;
        }

        @Override
        public final String getName() {
            return name;
        }

        @Override
        public final String getType() {
            return type;
        }

        @Override
        public final int getPriority() {
            return priority;
        }
    }

    /**
     * 本地缓存提供者
     */
    class LocalCacheProvider extends AbstractCacheProvider {
        public LocalCacheProvider() {
            super("local-caffeine", "local", 10);
        }

        @Override
        public boolean supports(CacheConfig config) {
            // 检查是否为本地缓存配置
            String serializerType = config.getSerializerType();
            return "java".equalsIgnoreCase(serializerType) ||
                   "kryo".equalsIgnoreCase(serializerType);
        }

        @Override
        public Cache<CacheKey, Object> createCache(CacheConfig config) {
            // 这里应该创建实际的本地缓存实现
            throw new UnsupportedOperationException("需要实现本地缓存创建逻辑");
        }
    }

    /**
     * Redis缓存提供者
     */
    class RedisCacheProvider extends AbstractCacheProvider {
        public RedisCacheProvider() {
            super("redis", "distributed", 20);
        }

        @Override
        public boolean supports(CacheConfig config) {
            // 检查是否为Redis缓存配置
            String serializerType = config.getSerializerType();
            return "json".equalsIgnoreCase(serializerType) ||
                   "protobuf".equalsIgnoreCase(serializerType);
        }

        @Override
        public Cache<CacheKey, Object> createCache(CacheConfig config) {
            // 这里应该创建实际的Redis缓存实现
            throw new UnsupportedOperationException("需要实现Redis缓存创建逻辑");
        }
    }

    /**
     * 多级缓存提供者
     */
    class MultiLevelCacheProvider extends AbstractCacheProvider {
        public MultiLevelCacheProvider() {
            super("multi-level", "hybrid", 30);
        }

        @Override
        public boolean supports(CacheConfig config) {
            // 多级缓存支持所有配置
            return true;
        }

        @Override
        public Cache<CacheKey, Object> createCache(CacheConfig config) {
            // 这里应该创建实际的多级缓存实现
            throw new UnsupportedOperationException("需要实现多级缓存创建逻辑");
        }
    }
}