/*
 * 文件名: LogicContext.java
 * 用途: 逻辑服务上下文
 * 实现内容:
 *   - 逻辑服务全局组件访问和管理
 *   - 配置管理和服务发现
 *   - 事件总线和扩展点管理
 *   - 全局状态维护和共享数据
 *   - 组件注册和依赖注入支持
 * 技术选型:
 *   - Spring容器集成进行依赖管理
 *   - 线程安全的并发访问支持
 *   - 扩展点模式支持插件机制
 *   - 配置热更新和动态加载
 * 依赖关系:
 *   - 被LogicServer和各个模块使用
 *   - 集成所有框架组件的访问入口
 *   - 提供全局的服务定位功能
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.core;

import com.lx.gameserver.frame.actor.core.ActorSystem;
import com.lx.gameserver.frame.ecs.core.World;
import com.lx.gameserver.frame.cache.local.LocalCacheManager;
import com.lx.gameserver.frame.event.EventBus;
import com.lx.gameserver.business.logic.config.LogicConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 逻辑服务上下文
 * <p>
 * 作为逻辑服务的全局上下文，提供对各种框架组件的统一访问，
 * 管理配置信息、扩展点和全局状态。是整个逻辑服务的核心
 * 协调者和服务定位器。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@Getter
public class LogicContext {

    /** 初始化标志 */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Spring应用上下文 */
    @Autowired
    private ApplicationContext applicationContext;

    /** 环境配置 */
    @Autowired
    private Environment environment;

    /** 事件发布器 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 逻辑配置 */
    @Autowired
    private LogicConfig logicConfig;

    /** Actor系统 */
    @Autowired(required = false)
    private ActorSystem actorSystem;

    /** ECS世界 */
    @Autowired(required = false)
    private World ecsWorld;

    /** 缓存管理器 */
    @Autowired(required = false)
    private LocalCacheManager cacheManager;

    /** 事件总线 */
    @Autowired(required = false)
    private EventBus eventBus;

    /** 扩展点注册表 */
    private final Map<Class<?>, Object> extensionPoints = new ConcurrentHashMap<>();

    /** 全局共享数据 */
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();

    /** 组件提供者 */
    private final Map<Class<?>, Supplier<?>> componentProviders = new ConcurrentHashMap<>();

    /**
     * 初始化上下文
     */
    public void initialize() throws Exception {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("LogicContext已经初始化");
            return;
        }

        log.info("初始化LogicContext...");

        try {
            // 1. 初始化扩展点
            initializeExtensionPoints();

            // 2. 注册组件提供者
            registerComponentProviders();

            // 3. 初始化共享数据
            initializeSharedData();

            log.info("LogicContext初始化完成");

        } catch (Exception e) {
            initialized.set(false);
            log.error("LogicContext初始化失败", e);
            throw e;
        }
    }

    /**
     * 初始化扩展点
     */
    private void initializeExtensionPoints() {
        log.info("初始化扩展点...");
        
        // 注册默认扩展点
        // 这里可以根据需要注册各种扩展点
    }

    /**
     * 注册组件提供者
     */
    private void registerComponentProviders() {
        log.info("注册组件提供者...");

        // 注册Actor系统提供者
        if (actorSystem != null) {
            registerComponentProvider(ActorSystem.class, () -> actorSystem);
        }

        // 注册ECS世界提供者
        if (ecsWorld != null) {
            registerComponentProvider(World.class, () -> ecsWorld);
        }

        // 注册缓存管理器提供者
        if (cacheManager != null) {
            registerComponentProvider(CacheManager.class, () -> cacheManager);
        }

        // 注册事件总线提供者
        if (eventBus != null) {
            registerComponentProvider(EventBus.class, () -> eventBus);
        }
    }

    /**
     * 初始化共享数据
     */
    private void initializeSharedData() {
        log.info("初始化共享数据...");

        // 设置服务器ID
        String serverId = environment.getProperty("game.logic.server.id", "logic-1");
        setSharedData("serverId", serverId);

        // 设置服务器名称
        String serverName = environment.getProperty("game.logic.server.name", "逻辑服务器1");
        setSharedData("serverName", serverName);

        // 设置最大玩家数
        int maxPlayers = environment.getProperty("game.logic.server.max-players", Integer.class, 5000);
        setSharedData("maxPlayers", maxPlayers);
    }

    /**
     * 注册扩展点
     *
     * @param extensionType 扩展点类型
     * @param extension     扩展实现
     * @param <T>           扩展点类型
     */
    public <T> void registerExtensionPoint(Class<T> extensionType, T extension) {
        if (extension == null) {
            throw new IllegalArgumentException("扩展实现不能为空");
        }

        extensionPoints.put(extensionType, extension);
        log.info("注册扩展点: {} -> {}", extensionType.getSimpleName(), extension.getClass().getSimpleName());
    }

    /**
     * 获取扩展点
     *
     * @param extensionType 扩展点类型
     * @param <T>           扩展点类型
     * @return 扩展实现，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtensionPoint(Class<T> extensionType) {
        return (T) extensionPoints.get(extensionType);
    }

    /**
     * 移除扩展点
     *
     * @param extensionType 扩展点类型
     * @param <T>           扩展点类型
     * @return 被移除的扩展实现
     */
    @SuppressWarnings("unchecked")
    public <T> T removeExtensionPoint(Class<T> extensionType) {
        T removed = (T) extensionPoints.remove(extensionType);
        if (removed != null) {
            log.info("移除扩展点: {}", extensionType.getSimpleName());
        }
        return removed;
    }

    /**
     * 注册组件提供者
     *
     * @param componentType 组件类型
     * @param provider      组件提供者
     * @param <T>           组件类型
     */
    public <T> void registerComponentProvider(Class<T> componentType, Supplier<T> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("组件提供者不能为空");
        }

        componentProviders.put(componentType, provider);
        log.debug("注册组件提供者: {}", componentType.getSimpleName());
    }

    /**
     * 获取组件
     *
     * @param componentType 组件类型
     * @param <T>           组件类型
     * @return 组件实例，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> componentType) {
        // 首先尝试从Spring容器获取
        try {
            return applicationContext.getBean(componentType);
        } catch (Exception e) {
            // 如果Spring容器中没有，尝试从组件提供者获取
            Supplier<?> provider = componentProviders.get(componentType);
            if (provider != null) {
                return (T) provider.get();
            }
            
            log.warn("未找到组件: {}", componentType.getSimpleName());
            return null;
        }
    }

    /**
     * 设置共享数据
     *
     * @param key   键
     * @param value 值
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
        log.debug("设置共享数据: {} = {}", key, value);
    }

    /**
     * 获取共享数据
     *
     * @param key 键
     * @return 值，如果不存在返回null
     */
    public Object getSharedData(String key) {
        return sharedData.get(key);
    }

    /**
     * 获取共享数据
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          数据类型
     * @return 值，如果不存在返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key, T defaultValue) {
        Object value = sharedData.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 移除共享数据
     *
     * @param key 键
     * @return 被移除的值
     */
    public Object removeSharedData(String key) {
        Object removed = sharedData.remove(key);
        if (removed != null) {
            log.debug("移除共享数据: {}", key);
        }
        return removed;
    }

    /**
     * 发布事件
     *
     * @param event 事件
     */
    public void publishEvent(Object event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
            log.debug("发布事件: {}", event.getClass().getSimpleName());
        } else {
            log.warn("事件发布器未初始化，无法发布事件: {}", event.getClass().getSimpleName());
        }
    }

    /**
     * 获取配置属性
     *
     * @param key 配置键
     * @return 配置值
     */
    public String getProperty(String key) {
        return environment.getProperty(key);
    }

    /**
     * 获取配置属性
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getProperty(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    /**
     * 获取配置属性
     *
     * @param key        配置键
     * @param targetType 目标类型
     * @param <T>        目标类型
     * @return 配置值
     */
    public <T> T getProperty(String key, Class<T> targetType) {
        return environment.getProperty(key, targetType);
    }

    /**
     * 获取配置属性
     *
     * @param key          配置键
     * @param targetType   目标类型
     * @param defaultValue 默认值
     * @param <T>          目标类型
     * @return 配置值
     */
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return environment.getProperty(key, targetType, defaultValue);
    }

    /**
     * 检查上下文是否已初始化
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取服务器ID
     *
     * @return 服务器ID
     */
    public String getServerId() {
        return getSharedData("serverId", "logic-1");
    }

    /**
     * 获取服务器名称
     *
     * @return 服务器名称
     */
    public String getServerName() {
        return getSharedData("serverName", "逻辑服务器1");
    }

    /**
     * 获取最大玩家数
     *
     * @return 最大玩家数
     */
    public Integer getMaxPlayers() {
        return getSharedData("maxPlayers", 5000);
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        log.info("清理LogicContext资源...");
        
        extensionPoints.clear();
        sharedData.clear();
        componentProviders.clear();
        
        initialized.set(false);
    }
}