/*
 * 文件名: PluginManager.java
 * 用途: 插件管理器实现
 * 实现内容:
 *   - 插件生命周期管理
 *   - 插件加载和卸载机制
 *   - 插件依赖关系管理
 *   - 热插拔功能支持
 *   - 插件间通信机制
 *   - 插件版本控制管理
 * 技术选型:
 *   - Java ClassLoader (动态类加载)
 *   - Spring Context (依赖注入)
 *   - 反射机制 (动态实例化)
 *   - ConcurrentHashMap (线程安全Map)
 * 依赖关系: 核心插件管理组件，被AdminApplication和各模块使用
 */
package com.lx.gameserver.admin.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 插件管理器
 * <p>
 * 负责管理所有插件的生命周期，包括加载、启动、停止、卸载等操作。
 * 支持插件的热插拔、依赖管理、版本控制和通信机制。
 * 提供插件发现、注册、调用等核心功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Component
public class PluginManager {

    /** Spring应用上下文 */
    @Autowired
    private ApplicationContext applicationContext;

    /** 环境配置 */
    @Autowired
    private Environment environment;

    /** 已加载的插件集合 */
    private final Map<String, PluginInfo> loadedPlugins = new ConcurrentHashMap<>();

    /** 插件类加载器映射 */
    private final Map<String, ClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();

    /** 插件依赖关系图 */
    private final Map<String, List<String>> pluginDependencies = new ConcurrentHashMap<>();

    /** 插件监听器列表 */
    private final List<PluginListener> pluginListeners = new CopyOnWriteArrayList<>();

    /** 插件目录路径 */
    private String pluginDirectory;

    /** 是否启用热加载 */
    private boolean hotReloadEnabled;

    /** 读写锁，保证插件操作的线程安全 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 初始化插件管理器
     */
    @PostConstruct
    public void init() {
        log.info("初始化插件管理器...");
        
        // 读取配置
        loadConfiguration();
        
        // 创建插件目录
        createPluginDirectory();
        
        // 自动扫描和加载插件
        if (environment.getProperty("admin.plugins.auto-load", Boolean.class, true)) {
            scanAndLoadPlugins();
        }
        
        log.info("插件管理器初始化完成，已加载 {} 个插件", loadedPlugins.size());
    }

    /**
     * 关闭插件管理器
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭插件管理器...");
        
        // 停止所有插件
        List<String> pluginNames = new ArrayList<>(loadedPlugins.keySet());
        Collections.reverse(pluginNames); // 反向停止
        
        for (String pluginName : pluginNames) {
            try {
                unloadPlugin(pluginName);
            } catch (Exception e) {
                log.error("卸载插件 {} 失败", pluginName, e);
            }
        }
        
        // 清理资源
        pluginClassLoaders.clear();
        pluginDependencies.clear();
        
        log.info("插件管理器关闭完成");
    }

    /**
     * 加载插件
     *
     * @param pluginPath 插件路径
     * @return 加载成功返回true，否则返回false
     */
    public boolean loadPlugin(String pluginPath) {
        lock.writeLock().lock();
        try {
            return doLoadPlugin(pluginPath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 卸载插件
     *
     * @param pluginName 插件名称
     * @return 卸载成功返回true，否则返回false
     */
    public boolean unloadPlugin(String pluginName) {
        lock.writeLock().lock();
        try {
            return doUnloadPlugin(pluginName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 重新加载插件
     *
     * @param pluginName 插件名称
     * @return 重新加载成功返回true，否则返回false
     */
    public boolean reloadPlugin(String pluginName) {
        lock.writeLock().lock();
        try {
            PluginInfo pluginInfo = loadedPlugins.get(pluginName);
            if (pluginInfo == null) {
                log.warn("插件 {} 未加载，无法重新加载", pluginName);
                return false;
            }
            
            String pluginPath = pluginInfo.getPluginPath();
            
            // 先卸载
            if (!doUnloadPlugin(pluginName)) {
                return false;
            }
            
            // 再加载
            return doLoadPlugin(pluginPath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取已加载的插件列表
     *
     * @return 插件信息列表
     */
    public List<PluginInfo> getLoadedPlugins() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(loadedPlugins.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取插件信息
     *
     * @param pluginName 插件名称
     * @return 插件信息，如果不存在返回null
     */
    public PluginInfo getPluginInfo(String pluginName) {
        lock.readLock().lock();
        try {
            return loadedPlugins.get(pluginName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 检查插件是否已加载
     *
     * @param pluginName 插件名称
     * @return 如果已加载返回true，否则返回false
     */
    public boolean isPluginLoaded(String pluginName) {
        lock.readLock().lock();
        try {
            return loadedPlugins.containsKey(pluginName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 添加插件监听器
     *
     * @param listener 插件监听器
     */
    public void addPluginListener(PluginListener listener) {
        pluginListeners.add(listener);
    }

    /**
     * 移除插件监听器
     *
     * @param listener 插件监听器
     */
    public void removePluginListener(PluginListener listener) {
        pluginListeners.remove(listener);
    }

    /**
     * 扫描并加载插件
     */
    private void scanAndLoadPlugins() {
        File pluginDir = new File(pluginDirectory);
        if (!pluginDir.exists() || !pluginDir.isDirectory()) {
            log.warn("插件目录不存在: {}", pluginDirectory);
            return;
        }
        
        File[] pluginFiles = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (pluginFiles == null) {
            return;
        }
        
        for (File pluginFile : pluginFiles) {
            try {
                loadPlugin(pluginFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("加载插件文件 {} 失败", pluginFile.getName(), e);
            }
        }
    }

    /**
     * 执行插件加载
     */
    private boolean doLoadPlugin(String pluginPath) {
        try {
            File pluginFile = new File(pluginPath);
            if (!pluginFile.exists()) {
                log.error("插件文件不存在: {}", pluginPath);
                return false;
            }
            
            // 读取插件信息
            PluginInfo pluginInfo = readPluginInfo(pluginFile);
            if (pluginInfo == null) {
                return false;
            }
            
            // 检查是否已加载
            if (loadedPlugins.containsKey(pluginInfo.getName())) {
                log.warn("插件 {} 已经加载", pluginInfo.getName());
                return false;
            }
            
            // 检查依赖
            if (!checkDependencies(pluginInfo)) {
                log.error("插件 {} 依赖检查失败", pluginInfo.getName());
                return false;
            }
            
            // 创建类加载器
            URLClassLoader classLoader = createPluginClassLoader(pluginFile);
            
            // 加载插件主类
            Class<?> pluginClass = classLoader.loadClass(pluginInfo.getMainClass());
            AdminModule plugin = (AdminModule) pluginClass.getDeclaredConstructor().newInstance();
            
            // 初始化插件
            AdminContext context = applicationContext.getBean(AdminContext.class);
            plugin.initialize(context);
            plugin.start();
            
            // 注册插件
            pluginInfo.setPlugin(plugin);
            pluginInfo.setPluginPath(pluginPath);
            loadedPlugins.put(pluginInfo.getName(), pluginInfo);
            pluginClassLoaders.put(pluginInfo.getName(), classLoader);
            
            // 通知监听器
            notifyPluginLoaded(pluginInfo);
            
            log.info("插件 {} 加载成功", pluginInfo.getName());
            return true;
            
        } catch (Exception e) {
            log.error("加载插件 {} 失败", pluginPath, e);
            return false;
        }
    }

    /**
     * 执行插件卸载
     */
    private boolean doUnloadPlugin(String pluginName) {
        try {
            PluginInfo pluginInfo = loadedPlugins.get(pluginName);
            if (pluginInfo == null) {
                log.warn("插件 {} 未加载", pluginName);
                return false;
            }
            
            // 停止插件
            AdminModule plugin = pluginInfo.getPlugin();
            if (plugin != null) {
                plugin.stop();
                plugin.destroy();
            }
            
            // 关闭类加载器
            ClassLoader classLoader = pluginClassLoaders.get(pluginName);
            if (classLoader instanceof URLClassLoader) {
                ((URLClassLoader) classLoader).close();
            }
            
            // 移除插件
            loadedPlugins.remove(pluginName);
            pluginClassLoaders.remove(pluginName);
            pluginDependencies.remove(pluginName);
            
            // 通知监听器
            notifyPluginUnloaded(pluginInfo);
            
            log.info("插件 {} 卸载成功", pluginName);
            return true;
            
        } catch (Exception e) {
            log.error("卸载插件 {} 失败", pluginName, e);
            return false;
        }
    }

    /**
     * 读取插件信息
     */
    private PluginInfo readPluginInfo(File pluginFile) {
        try (JarFile jarFile = new JarFile(pluginFile)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                log.error("插件文件 {} 缺少Manifest", pluginFile.getName());
                return null;
            }
            
            String name = manifest.getMainAttributes().getValue("Plugin-Name");
            String version = manifest.getMainAttributes().getValue("Plugin-Version");
            String mainClass = manifest.getMainAttributes().getValue("Plugin-Main-Class");
            String description = manifest.getMainAttributes().getValue("Plugin-Description");
            String dependencies = manifest.getMainAttributes().getValue("Plugin-Dependencies");
            
            if (name == null || mainClass == null) {
                log.error("插件文件 {} 缺少必要的Manifest属性", pluginFile.getName());
                return null;
            }
            
            PluginInfo pluginInfo = new PluginInfo();
            pluginInfo.setName(name);
            pluginInfo.setVersion(version != null ? version : "1.0.0");
            pluginInfo.setMainClass(mainClass);
            pluginInfo.setDescription(description != null ? description : "");
            
            // 解析依赖
            if (dependencies != null && !dependencies.trim().isEmpty()) {
                List<String> deps = Arrays.asList(dependencies.split(","));
                pluginDependencies.put(name, deps);
            }
            
            return pluginInfo;
            
        } catch (Exception e) {
            log.error("读取插件信息失败: {}", pluginFile.getName(), e);
            return null;
        }
    }

    /**
     * 创建插件类加载器
     */
    private URLClassLoader createPluginClassLoader(File pluginFile) throws Exception {
        URL[] urls = {pluginFile.toURI().toURL()};
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    /**
     * 检查插件依赖
     */
    private boolean checkDependencies(PluginInfo pluginInfo) {
        List<String> dependencies = pluginDependencies.get(pluginInfo.getName());
        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }
        
        for (String dependency : dependencies) {
            if (!loadedPlugins.containsKey(dependency.trim())) {
                log.error("插件 {} 依赖的插件 {} 未加载", pluginInfo.getName(), dependency);
                return false;
            }
        }
        
        return true;
    }

    /**
     * 加载配置
     */
    private void loadConfiguration() {
        pluginDirectory = environment.getProperty("admin.plugins.directory", "plugins");
        hotReloadEnabled = environment.getProperty("admin.plugins.hot-reload", Boolean.class, false);
        
        log.debug("插件配置: directory={}, hotReload={}", pluginDirectory, hotReloadEnabled);
    }

    /**
     * 创建插件目录
     */
    private void createPluginDirectory() {
        File dir = new File(pluginDirectory);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("创建插件目录: {}", pluginDirectory);
            } else {
                log.warn("创建插件目录失败: {}", pluginDirectory);
            }
        }
    }

    /**
     * 通知插件加载
     */
    private void notifyPluginLoaded(PluginInfo pluginInfo) {
        for (PluginListener listener : pluginListeners) {
            try {
                listener.onPluginLoaded(pluginInfo);
            } catch (Exception e) {
                log.error("通知插件加载监听器失败", e);
            }
        }
    }

    /**
     * 通知插件卸载
     */
    private void notifyPluginUnloaded(PluginInfo pluginInfo) {
        for (PluginListener listener : pluginListeners) {
            try {
                listener.onPluginUnloaded(pluginInfo);
            } catch (Exception e) {
                log.error("通知插件卸载监听器失败", e);
            }
        }
    }

    /**
     * 插件信息
     */
    public static class PluginInfo {
        private String name;
        private String version;
        private String description;
        private String mainClass;
        private String pluginPath;
        private AdminModule plugin;
        private long loadTime;

        // Getter和Setter方法
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMainClass() { return mainClass; }
        public void setMainClass(String mainClass) { this.mainClass = mainClass; }
        public String getPluginPath() { return pluginPath; }
        public void setPluginPath(String pluginPath) { this.pluginPath = pluginPath; }
        public AdminModule getPlugin() { return plugin; }
        public void setPlugin(AdminModule plugin) { this.plugin = plugin; this.loadTime = System.currentTimeMillis(); }
        public long getLoadTime() { return loadTime; }
    }

    /**
     * 插件监听器接口
     */
    public interface PluginListener {
        /**
         * 插件加载完成
         */
        void onPluginLoaded(PluginInfo pluginInfo);

        /**
         * 插件卸载完成
         */
        void onPluginUnloaded(PluginInfo pluginInfo);
    }
}