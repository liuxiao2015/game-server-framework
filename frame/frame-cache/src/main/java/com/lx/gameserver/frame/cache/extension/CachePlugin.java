/*
 * 文件名: CachePlugin.java
 * 用途: 缓存插件接口
 * 实现内容:
 *   - 缓存插件生命周期管理
 *   - 拦截器模式支持
 *   - 自定义实现扩展
 *   - 插件配置加载
 *   - 插件依赖管理
 * 技术选型:
 *   - 插件接口定义
 *   - 生命周期回调
 *   - 拦截器链模式
 *   - SPI机制支持
 * 依赖关系:
 *   - 被CacheProvider管理
 *   - 扩展缓存功能
 *   - 与配置系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.extension;

import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheKey;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 缓存插件接口
 * <p>
 * 定义缓存插件的标准接口，支持生命周期管理、拦截器功能
 * 和自定义扩展。插件可以在缓存操作的各个阶段进行拦截和处理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface CachePlugin {

    /**
     * 获取插件名称
     *
     * @return 插件名称
     */
    String getName();

    /**
     * 获取插件版本
     *
     * @return 插件版本
     */
    String getVersion();

    /**
     * 获取插件描述
     *
     * @return 插件描述
     */
    String getDescription();

    /**
     * 获取插件优先级（数值越小优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 获取插件依赖
     *
     * @return 依赖的插件名称集合
     */
    default String[] getDependencies() {
        return new String[0];
    }

    /**
     * 插件初始化
     *
     * @param context 插件上下文
     */
    void initialize(PluginContext context);

    /**
     * 插件启动
     */
    void start();

    /**
     * 插件停止
     */
    void stop();

    /**
     * 插件销毁
     */
    void destroy();

    /**
     * 检查插件是否已启动
     *
     * @return 是否已启动
     */
    boolean isStarted();

    /**
     * 获取前置拦截器
     *
     * @return 前置拦截器，如果不需要则返回null
     */
    default CacheInterceptor getBeforeInterceptor() {
        return null;
    }

    /**
     * 获取后置拦截器
     *
     * @return 后置拦截器，如果不需要则返回null
     */
    default CacheInterceptor getAfterInterceptor() {
        return null;
    }

    /**
     * 获取异常拦截器
     *
     * @return 异常拦截器，如果不需要则返回null
     */
    default CacheExceptionInterceptor getExceptionInterceptor() {
        return null;
    }

    /**
     * 插件上下文接口
     */
    interface PluginContext {
        /**
         * 获取缓存实例
         *
         * @param cacheName 缓存名称
         * @return 缓存实例
         */
        Cache<CacheKey, Object> getCache(String cacheName);

        /**
         * 获取所有缓存实例
         *
         * @return 缓存实例映射
         */
        Map<String, Cache<CacheKey, Object>> getAllCaches();

        /**
         * 获取配置属性
         *
         * @param key 配置键
         * @return 配置值
         */
        String getProperty(String key);

        /**
         * 获取配置属性（带默认值）
         *
         * @param key          配置键
         * @param defaultValue 默认值
         * @return 配置值
         */
        String getProperty(String key, String defaultValue);

        /**
         * 获取所有配置属性
         *
         * @return 配置属性映射
         */
        Map<String, String> getAllProperties();

        /**
         * 注册事件监听器
         *
         * @param eventType 事件类型
         * @param listener  监听器
         */
        void addEventListener(String eventType, Object listener);

        /**
         * 取消注册事件监听器
         *
         * @param eventType 事件类型
         * @param listener  监听器
         */
        void removeEventListener(String eventType, Object listener);
    }

    /**
     * 缓存拦截器接口
     */
    interface CacheInterceptor {
        /**
         * 拦截GET操作
         *
         * @param cacheName 缓存名称
         * @param key       缓存键
         * @param value     缓存值（可能为null）
         * @return 处理后的值，返回null表示继续原有流程
         */
        default Object interceptGet(String cacheName, CacheKey key, Object value) {
            return value;
        }

        /**
         * 拦截PUT操作
         *
         * @param cacheName 缓存名称
         * @param key       缓存键
         * @param value     缓存值
         * @return 处理后的值
         */
        default Object interceptPut(String cacheName, CacheKey key, Object value) {
            return value;
        }

        /**
         * 拦截REMOVE操作
         *
         * @param cacheName 缓存名称
         * @param key       缓存键
         * @param oldValue  原始值
         * @return 是否继续执行移除操作
         */
        default boolean interceptRemove(String cacheName, CacheKey key, Object oldValue) {
            return true;
        }

        /**
         * 拦截CLEAR操作
         *
         * @param cacheName 缓存名称
         * @return 是否继续执行清空操作
         */
        default boolean interceptClear(String cacheName) {
            return true;
        }
    }

    /**
     * 缓存异常拦截器接口
     */
    interface CacheExceptionInterceptor {
        /**
         * 拦截异常
         *
         * @param operation 操作类型
         * @param cacheName 缓存名称
         * @param key       缓存键（可能为null）
         * @param exception 异常
         * @return 处理后的值，如果返回非null则表示处理了异常
         */
        Object interceptException(String operation, String cacheName, CacheKey key, Exception exception);
    }

    /**
     * 插件状态枚举
     */
    enum PluginState {
        CREATED,     // 已创建
        INITIALIZED, // 已初始化
        STARTED,     // 已启动
        STOPPED,     // 已停止
        DESTROYED    // 已销毁
    }

    /**
     * 抽象插件基类
     */
    abstract class AbstractCachePlugin implements CachePlugin {
        private PluginState state = PluginState.CREATED;
        private PluginContext context;

        @Override
        public final void initialize(PluginContext context) {
            if (state != PluginState.CREATED) {
                throw new IllegalStateException("Plugin can only be initialized once");
            }
            
            this.context = context;
            doInitialize(context);
            this.state = PluginState.INITIALIZED;
        }

        @Override
        public final void start() {
            if (state != PluginState.INITIALIZED && state != PluginState.STOPPED) {
                throw new IllegalStateException("Plugin must be initialized before starting");
            }
            
            doStart();
            this.state = PluginState.STARTED;
        }

        @Override
        public final void stop() {
            if (state != PluginState.STARTED) {
                return;
            }
            
            doStop();
            this.state = PluginState.STOPPED;
        }

        @Override
        public final void destroy() {
            if (state == PluginState.DESTROYED) {
                return;
            }
            
            if (state == PluginState.STARTED) {
                stop();
            }
            
            doDestroy();
            this.state = PluginState.DESTROYED;
        }

        @Override
        public final boolean isStarted() {
            return state == PluginState.STARTED;
        }

        /**
         * 获取插件状态
         *
         * @return 插件状态
         */
        public final PluginState getState() {
            return state;
        }

        /**
         * 获取插件上下文
         *
         * @return 插件上下文
         */
        protected final PluginContext getContext() {
            return context;
        }

        /**
         * 子类实现初始化逻辑
         *
         * @param context 插件上下文
         */
        protected abstract void doInitialize(PluginContext context);

        /**
         * 子类实现启动逻辑
         */
        protected abstract void doStart();

        /**
         * 子类实现停止逻辑
         */
        protected abstract void doStop();

        /**
         * 子类实现销毁逻辑
         */
        protected abstract void doDestroy();
    }

    /**
     * 简单插件实现
     */
    class SimpleCachePlugin extends AbstractCachePlugin {
        private final String name;
        private final String version;
        private final String description;
        private final int priority;
        private final String[] dependencies;

        public SimpleCachePlugin(String name, String version, String description) {
            this(name, version, description, 100, new String[0]);
        }

        public SimpleCachePlugin(String name, String version, String description, 
                               int priority, String[] dependencies) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.priority = priority;
            this.dependencies = dependencies;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public String[] getDependencies() {
            return dependencies.clone();
        }

        @Override
        protected void doInitialize(PluginContext context) {
            // 默认空实现
        }

        @Override
        protected void doStart() {
            // 默认空实现
        }

        @Override
        protected void doStop() {
            // 默认空实现
        }

        @Override
        protected void doDestroy() {
            // 默认空实现
        }
    }
}