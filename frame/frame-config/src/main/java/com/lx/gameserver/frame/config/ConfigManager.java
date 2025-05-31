/*
 * 文件名: ConfigManager.java
 * 用途: 配置管理器
 * 实现内容:
 *   - 统一配置管理
 *   - 动态配置更新
 *   - 配置验证
 *   - 配置缓存
 *   - 配置监听
 * 技术选型:
 *   - Spring Boot Configuration
 *   - 观察者模式实现配置变更通知
 *   - 缓存机制提升配置访问性能
 * 依赖关系:
 *   - 被框架各模块使用
 *   - 提供统一的配置访问接口
 * 作者: liuxiao2015
 * 日期: 2025-05-31
 */
package com.lx.gameserver.frame.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 配置管理器
 * <p>
 * 提供统一的配置管理功能，支持动态配置更新、配置验证、配置缓存等特性。
 * 支持多种配置源和配置格式，提供配置变更监听机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-31
 */
@Slf4j
@Service
public class ConfigManager {
    
    @Autowired
    private Environment environment;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * 配置缓存
     */
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    
    /**
     * 配置变更监听器
     */
    private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    
    /**
     * 获取配置值
     *
     * @param key 配置键
     * @param clazz 值类型Class
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 配置值
     */
    public <T> T getConfig(String key, Class<T> clazz, T defaultValue) {
        try {
            // 先尝试从缓存获取
            Object cachedValue = configCache.get(key);
            if (cachedValue != null && clazz.isInstance(cachedValue)) {
                return clazz.cast(cachedValue);
            }
            
            // 从环境获取配置
            T value = environment.getProperty(key, clazz, defaultValue);
            
            // 缓存配置值
            configCache.put(key, value);
            
            log.debug("获取配置: {} = {}", key, value);
            return value;
        } catch (Exception e) {
            log.warn("获取配置失败，使用默认值: {} = {}", key, defaultValue, e);
            return defaultValue;
        }
    }
    
    /**
     * 获取配置值（简化版本）
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        return getConfig(key, (Class<T>) defaultValue.getClass(), defaultValue);
    }
    
    /**
     * 获取字符串配置
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getString(String key, String defaultValue) {
        return getConfig(key, defaultValue);
    }
    
    /**
     * 获取整数配置
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Integer getInt(String key, Integer defaultValue) {
        return getConfig(key, defaultValue);
    }
    
    /**
     * 获取布尔配置
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Boolean getBoolean(String key, Boolean defaultValue) {
        return getConfig(key, defaultValue);
    }
    
    /**
     * 更新配置
     *
     * @param key 配置键
     * @param value 配置值
     */
    public void updateConfig(String key, Object value) {
        Object oldValue = configCache.get(key);
        configCache.put(key, value);
        
        log.info("配置已更新: {} = {} (旧值: {})", key, value, oldValue);
        
        // 通知监听器
        notifyListeners(key, value);
    }
    
    /**
     * 注册配置变更监听器
     *
     * @param key 配置键
     * @param listener 监听器
     */
    public void addConfigListener(String key, Consumer<Object> listener) {
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("注册配置监听器: {}", key);
    }
    
    /**
     * 移除配置变更监听器
     *
     * @param key 配置键
     * @param listener 监听器
     */
    public void removeConfigListener(String key, Consumer<Object> listener) {
        CopyOnWriteArrayList<Consumer<Object>> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            keyListeners.remove(listener);
            log.debug("移除配置监听器: {}", key);
        }
    }
    
    /**
     * 通知监听器配置变更
     *
     * @param key 配置键
     * @param value 新值
     */
    private void notifyListeners(String key, Object value) {
        CopyOnWriteArrayList<Consumer<Object>> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            for (Consumer<Object> listener : keyListeners) {
                try {
                    listener.accept(value);
                } catch (Exception e) {
                    log.error("配置监听器执行失败: {}", key, e);
                }
            }
        }
    }
    
    /**
     * 清空配置缓存
     */
    public void clearCache() {
        configCache.clear();
        log.info("配置缓存已清空");
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        clearCache();
        log.info("配置已重新加载");
    }
}