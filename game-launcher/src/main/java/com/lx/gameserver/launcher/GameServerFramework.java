/*
 * 文件名: GameServerFramework.java
 * 用途: 游戏服务器框架统一初始化管理器
 * 内容: 
 *   - 统一管理所有框架模块的初始化
 *   - 提供框架生命周期管理
 *   - 集成配置管理和监控能力
 *   - 支持模块间依赖管理和有序启动
 * 技术选型: 
 *   - Spring框架集成
 *   - 模块化启动顺序管理
 *   - 统一异常处理和监控
 * 依赖关系: 
 *   - 协调所有frame模块的初始化
 *   - 与ServiceManager协作进行服务管理
 *   - 提供统一的框架入口点
 * 作者: liuxiao2015
 * 日期: 2025-05-31
 */
package com.lx.gameserver.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 游戏服务器框架统一管理器
 * <p>
 * 作为整个游戏服务器框架的核心协调器，负责：
 * 1. 统一初始化所有框架模块
 * 2. 管理模块间的依赖关系
 * 3. 提供框架级别的配置管理
 * 4. 集成监控和健康检查
 * 5. 协调优雅启停流程
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-31
 */
@Component
public class GameServerFramework implements ApplicationListener<ContextRefreshedEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(GameServerFramework.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Autowired
    ApplicationContext applicationContext;
    
    @Autowired(required = false)
    ServiceManager serviceManager;
    
    /** 框架状态 */
    private volatile FrameworkStatus status = FrameworkStatus.INITIALIZING;
    
    /** 框架启动时间 */
    private LocalDateTime startupTime;
    
    /** 已初始化的模块 */
    private final Map<String, FrameworkModule> initializedModules = new ConcurrentHashMap<>();
    
    /** 模块初始化执行器 */
    private ExecutorService initializationExecutor;
    
    /** 框架配置 */
    private FrameworkConfig frameworkConfig;
    
    /**
     * 框架状态枚举
     */
    public enum FrameworkStatus {
        INITIALIZING("初始化中"),
        STARTING("启动中"),
        RUNNING("运行中"),
        STOPPING("停止中"),
        STOPPED("已停止"),
        ERROR("错误状态");
        
        private final String description;
        
        FrameworkStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 框架模块接口
     */
    public interface FrameworkModule {
        /**
         * 获取模块名称
         */
        String getModuleName();
        
        /**
         * 获取模块依赖
         */
        default List<String> getDependencies() {
            return Collections.emptyList();
        }
        
        /**
         * 初始化模块
         */
        void initialize() throws Exception;
        
        /**
         * 启动模块
         */
        void start() throws Exception;
        
        /**
         * 停止模块
         */
        void stop() throws Exception;
        
        /**
         * 获取模块状态
         */
        default String getStatus() {
            return "运行中";
        }
        
        /**
         * 获取模块优先级（数值越小优先级越高）
         */
        default int getPriority() {
            return 500;
        }
    }
    
    /**
     * 框架配置类
     */
    public static class FrameworkConfig {
        private boolean enableVirtualThreads = true;
        private boolean enableMetrics = true;
        private boolean enableHealthCheck = true;
        private int initializationTimeout = 60; // 秒
        private int shutdownTimeout = 30; // 秒
        private String logLevel = "INFO";
        
        // Getters and setters
        public boolean isEnableVirtualThreads() { return enableVirtualThreads; }
        public void setEnableVirtualThreads(boolean enableVirtualThreads) { this.enableVirtualThreads = enableVirtualThreads; }
        
        public boolean isEnableMetrics() { return enableMetrics; }
        public void setEnableMetrics(boolean enableMetrics) { this.enableMetrics = enableMetrics; }
        
        public boolean isEnableHealthCheck() { return enableHealthCheck; }
        public void setEnableHealthCheck(boolean enableHealthCheck) { this.enableHealthCheck = enableHealthCheck; }
        
        public int getInitializationTimeout() { return initializationTimeout; }
        public void setInitializationTimeout(int initializationTimeout) { this.initializationTimeout = initializationTimeout; }
        
        public int getShutdownTimeout() { return shutdownTimeout; }
        public void setShutdownTimeout(int shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
        
        public String getLogLevel() { return logLevel; }
        public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    }
    
    /**
     * 初始化框架
     */
    @PostConstruct
    public void initialize() {
        startupTime = LocalDateTime.now();
        status = FrameworkStatus.INITIALIZING;
        
        logger.info("=".repeat(80));
        logger.info("游戏服务器框架启动开始");
        logger.info("启动时间: {}", startupTime.format(TIME_FORMATTER));
        logger.info("=".repeat(80));
        
        try {
            // 初始化框架配置
            initializeConfig();
            
            // 初始化执行器
            initializeExecutor();
            
            logger.info("游戏服务器框架初始化完成");
            
        } catch (Exception e) {
            status = FrameworkStatus.ERROR;
            logger.error("游戏服务器框架初始化失败", e);
            throw new RuntimeException("框架初始化失败", e);
        }
    }
    
    /**
     * Spring容器刷新完成后启动框架
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == this.applicationContext) {
            startFramework();
        }
    }
    
    /**
     * 启动框架
     */
    public void startFramework() {
        if (status != FrameworkStatus.INITIALIZING) {
            logger.warn("框架状态不正确，无法启动: {}", status);
            return;
        }
        
        status = FrameworkStatus.STARTING;
        logger.info("开始启动游戏服务器框架模块...");
        
        try {
            // 发现并初始化所有框架模块
            discoverAndInitializeModules();
            
            // 启动所有模块
            startAllModules();
            
            status = FrameworkStatus.RUNNING;
            logger.info("=".repeat(80));
            logger.info("游戏服务器框架启动完成");
            logger.info("已初始化模块数量: {}", initializedModules.size());
            logger.info("框架状态: {}", status.getDescription());
            logger.info("启动耗时: {} ms", java.time.Duration.between(startupTime, LocalDateTime.now()).toMillis());
            logger.info("=".repeat(80));
            
        } catch (Exception e) {
            status = FrameworkStatus.ERROR;
            logger.error("游戏服务器框架启动失败", e);
            throw new RuntimeException("框架启动失败", e);
        }
    }
    
    /**
     * 关闭框架
     */
    @PreDestroy
    public void shutdown() {
        if (status == FrameworkStatus.STOPPED || status == FrameworkStatus.STOPPING) {
            return;
        }
        
        status = FrameworkStatus.STOPPING;
        logger.info("开始关闭游戏服务器框架...");
        
        try {
            // 停止所有模块
            stopAllModules();
            
            // 关闭执行器
            if (initializationExecutor != null) {
                initializationExecutor.shutdown();
                try {
                    if (!initializationExecutor.awaitTermination(frameworkConfig.getShutdownTimeout(), TimeUnit.SECONDS)) {
                        initializationExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    initializationExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            status = FrameworkStatus.STOPPED;
            logger.info("游戏服务器框架关闭完成");
            
        } catch (Exception e) {
            logger.error("游戏服务器框架关闭异常", e);
        }
    }
    
    /**
     * 初始化配置
     */
    private void initializeConfig() {
        frameworkConfig = new FrameworkConfig();
        
        // 可以从配置文件或环境变量中加载配置
        // 这里使用默认配置
        logger.info("框架配置初始化完成: 虚拟线程={}, 监控={}, 健康检查={}", 
                frameworkConfig.isEnableVirtualThreads(),
                frameworkConfig.isEnableMetrics(),
                frameworkConfig.isEnableHealthCheck());
    }
    
    /**
     * 初始化执行器
     */
    private void initializeExecutor() {
        if (frameworkConfig.isEnableVirtualThreads()) {
            // Java 17没有虚拟线程，使用线程池替代
            initializationExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "Framework-VirtPool-Thread");
                t.setDaemon(true);
                return t;
            });
            logger.info("使用增强线程池模拟虚拟线程进行模块初始化");
        } else {
            initializationExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "Framework-Init-Thread");
                t.setDaemon(true);
                return t;
            });
            logger.info("使用平台线程执行器进行模块初始化");
        }
    }
    
    /**
     * 发现并初始化所有框架模块
     */
    private void discoverAndInitializeModules() {
        logger.info("开始发现框架模块...");
        
        // 从Spring容器中查找所有实现了FrameworkModule接口的Bean
        Map<String, FrameworkModule> moduleBeansMap = applicationContext.getBeansOfType(FrameworkModule.class);
        
        if (moduleBeansMap.isEmpty()) {
            logger.warn("未发现任何框架模块");
            return;
        }
        
        // 按优先级排序模块
        List<FrameworkModule> sortedModules = moduleBeansMap.values().stream()
                .sorted(Comparator.comparingInt(FrameworkModule::getPriority))
                .toList();
        
        logger.info("发现 {} 个框架模块，开始按优先级初始化", sortedModules.size());
        
        // 初始化每个模块
        for (FrameworkModule module : sortedModules) {
            try {
                logger.info("初始化模块: {} (优先级: {})", module.getModuleName(), module.getPriority());
                module.initialize();
                initializedModules.put(module.getModuleName(), module);
                logger.info("模块 {} 初始化成功", module.getModuleName());
            } catch (Exception e) {
                logger.error("模块 {} 初始化失败", module.getModuleName(), e);
                throw new RuntimeException("模块初始化失败: " + module.getModuleName(), e);
            }
        }
        
        logger.info("所有框架模块初始化完成");
    }
    
    /**
     * 启动所有模块
     */
    private void startAllModules() {
        logger.info("开始启动所有框架模块...");
        
        List<CompletableFuture<Void>> startupFutures = new ArrayList<>();
        
        for (FrameworkModule module : initializedModules.values()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("启动模块: {}", module.getModuleName());
                    module.start();
                    logger.debug("模块 {} 启动成功", module.getModuleName());
                } catch (Exception e) {
                    logger.error("模块 {} 启动失败", module.getModuleName(), e);
                    throw new RuntimeException("模块启动失败: " + module.getModuleName(), e);
                }
            }, initializationExecutor);
            
            startupFutures.add(future);
        }
        
        // 等待所有模块启动完成
        try {
            CompletableFuture.allOf(startupFutures.toArray(new CompletableFuture[0]))
                    .get(frameworkConfig.getInitializationTimeout(), TimeUnit.SECONDS);
            logger.info("所有框架模块启动完成");
        } catch (Exception e) {
            logger.error("框架模块启动超时或失败", e);
            throw new RuntimeException("框架模块启动失败", e);
        }
    }
    
    /**
     * 停止所有模块
     */
    private void stopAllModules() {
        logger.info("开始停止所有框架模块...");
        
        // 按相反顺序停止模块
        List<FrameworkModule> modules = new ArrayList<>(initializedModules.values());
        Collections.reverse(modules);
        
        for (FrameworkModule module : modules) {
            try {
                logger.debug("停止模块: {}", module.getModuleName());
                module.stop();
                logger.debug("模块 {} 停止成功", module.getModuleName());
            } catch (Exception e) {
                logger.error("模块 {} 停止失败", module.getModuleName(), e);
                // 继续停止其他模块，不抛出异常
            }
        }
        
        logger.info("所有框架模块停止完成");
    }
    
    // ===== 公共API方法 =====
    
    /**
     * 获取框架状态
     */
    public FrameworkStatus getStatus() {
        return status;
    }
    
    /**
     * 获取框架启动时间
     */
    public LocalDateTime getStartupTime() {
        return startupTime;
    }
    
    /**
     * 获取已初始化的模块数量
     */
    public int getInitializedModuleCount() {
        return initializedModules.size();
    }
    
    /**
     * 获取所有模块的状态
     */
    public Map<String, String> getAllModuleStatus() {
        Map<String, String> statusMap = new HashMap<>();
        for (Map.Entry<String, FrameworkModule> entry : initializedModules.entrySet()) {
            try {
                statusMap.put(entry.getKey(), entry.getValue().getStatus());
            } catch (Exception e) {
                statusMap.put(entry.getKey(), "状态检查失败: " + e.getMessage());
            }
        }
        return statusMap;
    }
    
    /**
     * 获取框架配置
     */
    public FrameworkConfig getFrameworkConfig() {
        return frameworkConfig;
    }
    
    /**
     * 获取框架运行信息
     */
    public Map<String, Object> getFrameworkInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("状态", status.getDescription());
        info.put("启动时间", startupTime != null ? startupTime.format(TIME_FORMATTER) : "未启动");
        info.put("模块数量", initializedModules.size());
        info.put("配置", frameworkConfig);
        
        if (startupTime != null) {
            long uptimeMs = java.time.Duration.between(startupTime, LocalDateTime.now()).toMillis();
            info.put("运行时长(ms)", uptimeMs);
        }
        
        return info;
    }
    
    /**
     * 获取已初始化的模块
     */
    public Map<String, FrameworkModule> getInitializedModules() {
        return Collections.unmodifiableMap(initializedModules);
    }
    
    /**
     * 获取模块状态（别名方法，与getAllModuleStatus一致）
     */
    public Map<String, String> getModuleStatus() {
        return getAllModuleStatus();
    }
}