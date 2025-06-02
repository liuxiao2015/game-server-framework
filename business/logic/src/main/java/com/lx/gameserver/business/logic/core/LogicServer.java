/*
 * 文件名: LogicServer.java
 * 用途: 逻辑服务器主类
 * 实现内容:
 *   - 逻辑服务器的启动和关闭流程
 *   - 框架组件集成（Actor、ECS、Cache等）
 *   - 服务生命周期管理和健康检查
 *   - 优雅启动和关闭机制
 *   - 组件初始化和依赖注入
 * 技术选型:
 *   - Spring Boot自动配置和生命周期管理
 *   - 多线程并发处理和虚拟线程支持
 *   - Actor系统和ECS架构集成
 *   - 分布式缓存和事件处理
 * 依赖关系:
 *   - 依赖LogicContext进行全局状态管理
 *   - 集成frame层的所有框架组件
 *   - 被具体游戏逻辑模块使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.core;

import com.lx.gameserver.frame.actor.core.ActorSystem;
import com.lx.gameserver.frame.ecs.core.World;
import com.lx.gameserver.frame.cache.local.LocalCacheManager;
import com.lx.gameserver.frame.event.EventBus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 逻辑服务器主类
 * <p>
 * 负责逻辑服务器的完整生命周期管理，包括启动初始化、
 * 组件集成、运行监控和优雅关闭等功能。集成框架层的
 * 各种能力，为游戏逻辑提供稳定的运行环境。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@Getter
public class LogicServer implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 服务器状态枚举
     */
    public enum ServerState {
        /** 初始化状态 */
        INITIALIZING,
        /** 启动中 */
        STARTING,
        /** 运行中 */
        RUNNING,
        /** 停止中 */
        STOPPING,
        /** 已停止 */
        STOPPED,
        /** 错误状态 */
        ERROR
    }

    /** 服务器状态 */
    private final AtomicReference<ServerState> serverState = new AtomicReference<>(ServerState.INITIALIZING);

    /** 服务器启动标志 */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /** 服务器启动时间 */
    private volatile LocalDateTime startTime;

    /** 逻辑上下文 */
    @Autowired
    private LogicContext logicContext;

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

    /** 应用上下文 */
    @Autowired
    private ApplicationContext applicationContext;

    /** 健康检查执行器 */
    private ScheduledExecutorService healthCheckExecutor;

    /** 逻辑模块注册表 */
    private final ConcurrentHashMap<String, LogicModule> modules = new ConcurrentHashMap<>();

    /**
     * 启动逻辑服务器
     *
     * @param args 启动参数
     * @return 应用上下文
     */
    public static ConfigurableApplicationContext start(String[] args) {
        log.info("正在启动逻辑服务器...");
        return SpringApplication.run(LogicServer.class, args);
    }

    /**
     * 应用启动完成事件处理
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            doStart();
        } catch (Exception e) {
            log.error("逻辑服务器启动失败", e);
            serverState.set(ServerState.ERROR);
            throw new RuntimeException("逻辑服务器启动失败", e);
        }
    }

    /**
     * 执行启动流程
     */
    private void doStart() throws Exception {
        if (!started.compareAndSet(false, true)) {
            log.warn("逻辑服务器已经启动");
            return;
        }

        serverState.set(ServerState.STARTING);
        startTime = LocalDateTime.now();

        log.info("开始初始化逻辑服务器组件...");

        // 1. 初始化逻辑上下文
        initializeLogicContext();

        // 2. 初始化框架组件
        initializeFrameworkComponents();

        // 3. 注册和启动逻辑模块
        registerAndStartModules();

        // 4. 启动健康检查
        startHealthCheck();

        // 5. 设置关闭钩子
        setupShutdownHook();

        serverState.set(ServerState.RUNNING);
        log.info("逻辑服务器启动完成，耗时: {}ms", 
                Duration.between(startTime, LocalDateTime.now()).toMillis());
    }

    /**
     * 初始化逻辑上下文
     */
    private void initializeLogicContext() throws Exception {
        log.info("初始化逻辑上下文...");
        if (logicContext != null) {
            logicContext.initialize();
        }
    }

    /**
     * 初始化框架组件
     */
    private void initializeFrameworkComponents() throws Exception {
        log.info("初始化框架组件...");

        // 初始化Actor系统
        if (actorSystem != null) {
            log.info("初始化Actor系统...");
            // Actor系统通常由Spring自动初始化
        }

        // 初始化ECS世界
        if (ecsWorld != null) {
            log.info("初始化ECS世界...");
            ecsWorld.initialize();
        }

        // 初始化缓存管理器
        if (cacheManager != null) {
            log.info("初始化缓存管理器...");
            // 缓存管理器通常由Spring自动初始化
        }

        // 初始化事件总线
        if (eventBus != null) {
            log.info("初始化事件总线...");
            // 事件总线通常由Spring自动初始化
        }
    }

    /**
     * 注册和启动逻辑模块
     */
    private void registerAndStartModules() throws Exception {
        log.info("注册和启动逻辑模块...");

        // 获取所有LogicModule Bean
        applicationContext.getBeansOfType(LogicModule.class)
                .values()
                .forEach(this::registerModule);

        // 启动所有模块
        for (LogicModule module : modules.values()) {
            try {
                module.start();
                log.info("模块 {} 启动成功", module.getModuleName());
            } catch (Exception e) {
                log.error("模块 {} 启动失败", module.getModuleName(), e);
                throw e;
            }
        }
    }

    /**
     * 注册逻辑模块
     *
     * @param module 逻辑模块
     */
    public void registerModule(LogicModule module) {
        String moduleName = module.getModuleName();
        if (modules.putIfAbsent(moduleName, module) == null) {
            log.info("注册逻辑模块: {}", moduleName);
            try {
                module.initialize();
            } catch (Exception e) {
                log.error("初始化模块 {} 失败", moduleName, e);
                modules.remove(moduleName);
                throw new RuntimeException("初始化模块失败: " + moduleName, e);
            }
        } else {
            log.warn("模块 {} 已存在，跳过注册", moduleName);
        }
    }

    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        log.info("启动健康检查...");
        healthCheckExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "health-check");
            t.setDaemon(true);
            return t;
        });

        healthCheckExecutor.scheduleWithFixedDelay(
                this::performHealthCheck,
                30, // 初始延迟30秒
                60, // 每60秒检查一次
                TimeUnit.SECONDS
        );
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        try {
            boolean healthy = true;

            // 检查Actor系统
            if (actorSystem != null && !isActorSystemHealthy()) {
                healthy = false;
                log.warn("Actor系统健康检查失败");
            }

            // 检查ECS世界
            if (ecsWorld != null && !isEcsWorldHealthy()) {
                healthy = false;
                log.warn("ECS世界健康检查失败");
            }

            // 检查逻辑模块
            for (LogicModule module : modules.values()) {
                if (!module.isHealthy()) {
                    healthy = false;
                    log.warn("模块 {} 健康检查失败", module.getModuleName());
                }
            }

            if (!healthy) {
                log.warn("逻辑服务器健康检查发现问题");
            }

        } catch (Exception e) {
            log.error("健康检查执行失败", e);
        }
    }

    /**
     * 检查Actor系统健康状态
     */
    private boolean isActorSystemHealthy() {
        // 简单的健康检查，可以根据需要扩展
        return true;
    }

    /**
     * 检查ECS世界健康状态
     */
    private boolean isEcsWorldHealthy() {
        // 简单的健康检查，可以根据需要扩展
        return ecsWorld.isRunning();
    }

    /**
     * 设置关闭钩子
     */
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，开始优雅关闭...");
            try {
                stop();
            } catch (Exception e) {
                log.error("关闭过程中发生错误", e);
            }
        }, "shutdown-hook"));

        // 监听Spring Context关闭事件
        applicationContext.addApplicationListener((ContextClosedEvent event) -> {
            log.info("Spring Context正在关闭，开始停止逻辑服务器...");
            try {
                stop();
            } catch (Exception e) {
                log.error("停止逻辑服务器失败", e);
            }
        });
    }

    /**
     * 停止逻辑服务器
     */
    @PreDestroy
    public void stop() throws Exception {
        if (!started.compareAndSet(true, false)) {
            log.info("逻辑服务器已经停止或未启动");
            return;
        }

        serverState.set(ServerState.STOPPING);
        log.info("开始停止逻辑服务器...");

        try {
            // 1. 停止健康检查
            stopHealthCheck();

            // 2. 停止逻辑模块
            stopModules();

            // 3. 停止框架组件
            stopFrameworkComponents();

            // 4. 清理资源
            cleanup();

            serverState.set(ServerState.STOPPED);
            log.info("逻辑服务器已停止");

        } catch (Exception e) {
            serverState.set(ServerState.ERROR);
            log.error("停止逻辑服务器失败", e);
            throw e;
        }
    }

    /**
     * 停止健康检查
     */
    private void stopHealthCheck() {
        if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
            log.info("停止健康检查...");
            healthCheckExecutor.shutdown();
            try {
                if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthCheckExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 停止逻辑模块
     */
    private void stopModules() throws Exception {
        log.info("停止逻辑模块...");
        for (LogicModule module : modules.values()) {
            try {
                module.stop();
                log.info("模块 {} 已停止", module.getModuleName());
            } catch (Exception e) {
                log.error("停止模块 {} 失败", module.getModuleName(), e);
            }
        }
        modules.clear();
    }

    /**
     * 停止框架组件
     */
    private void stopFrameworkComponents() throws Exception {
        log.info("停止框架组件...");

        // 停止ECS世界
        if (ecsWorld != null) {
            try {
                ecsWorld.shutdown();
                log.info("ECS世界已停止");
            } catch (Exception e) {
                log.error("停止ECS世界失败", e);
            }
        }

        // Actor系统和其他组件由Spring管理生命周期
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        log.info("清理资源...");
        // 清理其他资源
    }

    /**
     * 获取服务器运行时间
     *
     * @return 运行时间
     */
    public Duration getUptime() {
        return startTime != null ? Duration.between(startTime, LocalDateTime.now()) : Duration.ZERO;
    }

    /**
     * 检查服务器是否运行中
     *
     * @return 是否运行中
     */
    public boolean isRunning() {
        return serverState.get() == ServerState.RUNNING;
    }

    /**
     * 检查服务器是否健康
     *
     * @return 是否健康
     */
    public boolean isHealthy() {
        return isRunning() && modules.values().stream().allMatch(LogicModule::isHealthy);
    }

    /**
     * 获取模块信息
     *
     * @return 模块信息
     */
    public ConcurrentHashMap<String, LogicModule> getModules() {
        return new ConcurrentHashMap<>(modules);
    }
}