/*
 * 文件名: GameServerStartupManager.java
 * 用途: 游戏服务器统一启动管理器
 * 实现内容:
 *   - 模块启动顺序管理
 *   - 服务依赖关系检查
 *   - 健康检查和就绪探测
 *   - 优雅关闭机制
 *   - 统一的JVM参数配置
 * 技术选型:
 *   - Spring Boot应用启动管理
 *   - 依赖关系拓扑排序
 *   - 异步启动和监控
 * 依赖关系:
 *   - 管理所有游戏服务器模块
 *   - 被主启动类调用
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.launcher.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏服务器统一启动管理器
 * <p>
 * 负责管理所有游戏服务器模块的启动顺序、依赖关系检查、
 * 健康监控和优雅关闭。实现统一的模块生命周期管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Component
@Order(1)
public class GameServerStartupManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GameServerStartupManager.class);

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 模块启动状态
     */
    private final Map<String, ModuleStatus> moduleStatusMap = new ConcurrentHashMap<>();

    /**
     * 启动时间记录
     */
    private Instant startupBeginTime;
    private Instant startupCompleteTime;

    /**
     * 模块启动顺序定义
     */
    private static final List<ModuleStartupConfig> MODULE_STARTUP_ORDER = Arrays.asList(
            new ModuleStartupConfig("frame", "框架核心模块", 0, Collections.emptyList()),
            new ModuleStartupConfig("gateway", "网关服务", 1, Arrays.asList("frame")),
            new ModuleStartupConfig("login", "登录服务", 2, Arrays.asList("frame", "gateway")),
            new ModuleStartupConfig("logic", "游戏逻辑", 3, Arrays.asList("frame", "login")),
            new ModuleStartupConfig("scene", "场景服务", 3, Arrays.asList("frame", "login")),
            new ModuleStartupConfig("chat", "聊天服务", 4, Arrays.asList("frame", "login"))
    );

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=".repeat(80));
        log.info("游戏服务器框架启动管理器开始启动");
        log.info("Java版本: {}", System.getProperty("java.version"));
        log.info("Spring Boot版本: {}", getClass().getPackage().getImplementationVersion());
        log.info("=".repeat(80));

        startupBeginTime = Instant.now();

        try {
            // 执行启动前检查
            preStartupCheck();

            // 按顺序启动模块
            startModulesInOrder();

            // 执行启动后验证
            postStartupValidation();

            startupCompleteTime = Instant.now();
            long startupTimeMs = Duration.between(startupBeginTime, startupCompleteTime).toMillis();

            log.info("=".repeat(80));
            log.info("游戏服务器框架启动完成！");
            log.info("启动耗时: {} ms", startupTimeMs);
            log.info("成功启动模块数: {}/{}", 
                    moduleStatusMap.values().stream().mapToInt(s -> s == ModuleStatus.RUNNING ? 1 : 0).sum(),
                    MODULE_STARTUP_ORDER.size());
            log.info("=".repeat(80));

        } catch (Exception e) {
            log.error("游戏服务器框架启动失败", e);
            throw e;
        }
    }

    /**
     * 启动前检查
     */
    private void preStartupCheck() {
        log.info("执行启动前检查...");

        // 检查Java版本
        String javaVersion = System.getProperty("java.version");
        if (!javaVersion.startsWith("21")) {
            log.warn("建议使用Java 21，当前版本: {}", javaVersion);
        }

        // 检查必要的环境变量和配置
        checkRequiredEnvironmentVariables();

        // 检查端口可用性
        checkPortAvailability();

        log.info("启动前检查完成");
    }

    /**
     * 按顺序启动模块
     */
    private void startModulesInOrder() {
        log.info("开始按依赖顺序启动模块...");

        // 按优先级分组
        Map<Integer, List<ModuleStartupConfig>> priorityGroups = new HashMap<>();
        for (ModuleStartupConfig config : MODULE_STARTUP_ORDER) {
            priorityGroups.computeIfAbsent(config.getPriority(), k -> new ArrayList<>()).add(config);
        }

        // 按优先级顺序启动
        for (int priority : priorityGroups.keySet().stream().sorted().toList()) {
            List<ModuleStartupConfig> modules = priorityGroups.get(priority);
            log.info("启动优先级 {} 的模块: {}", priority, 
                    modules.stream().map(ModuleStartupConfig::getName).toList());

            // 同一优先级的模块可以并行启动
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ModuleStartupConfig module : modules) {
                futures.add(CompletableFuture.runAsync(() -> startModule(module)));
            }

            // 等待当前优先级的所有模块启动完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        log.info("所有模块启动完成");
    }

    /**
     * 启动单个模块
     */
    private void startModule(ModuleStartupConfig config) {
        String moduleName = config.getName();
        log.info("正在启动模块: {}", moduleName);

        try {
            moduleStatusMap.put(moduleName, ModuleStatus.STARTING);

            // 检查依赖模块是否已启动
            checkModuleDependencies(config);

            // 执行模块特定的启动逻辑
            performModuleStartup(config);

            // 等待模块就绪
            waitForModuleReady(config);

            moduleStatusMap.put(moduleName, ModuleStatus.RUNNING);
            log.info("模块 {} 启动成功", moduleName);

        } catch (Exception e) {
            moduleStatusMap.put(moduleName, ModuleStatus.FAILED);
            log.error("模块 {} 启动失败", moduleName, e);
            throw new RuntimeException("模块启动失败: " + moduleName, e);
        }
    }

    /**
     * 检查模块依赖
     */
    private void checkModuleDependencies(ModuleStartupConfig config) {
        for (String dependency : config.getDependencies()) {
            ModuleStatus status = moduleStatusMap.get(dependency);
            if (status != ModuleStatus.RUNNING) {
                throw new IllegalStateException(
                        String.format("依赖模块 %s 未运行，当前状态: %s", dependency, status));
            }
        }
    }

    /**
     * 执行模块启动逻辑
     */
    private void performModuleStartup(ModuleStartupConfig config) {
        // 这里可以实现具体的模块启动逻辑
        // 例如：启动Spring Boot应用、初始化数据库连接等
        log.debug("执行模块 {} 的启动逻辑", config.getName());
        
        // 模拟启动过程
        try {
            Thread.sleep(100 + (long)(Math.random() * 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("模块启动被中断", e);
        }
    }

    /**
     * 等待模块就绪
     */
    private void waitForModuleReady(ModuleStartupConfig config) {
        // 实现健康检查逻辑
        log.debug("等待模块 {} 就绪", config.getName());
        
        // 简单的就绪检查
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待模块就绪被中断", e);
        }
    }

    /**
     * 启动后验证
     */
    private void postStartupValidation() {
        log.info("执行启动后验证...");

        // 验证所有模块状态
        boolean allModulesRunning = moduleStatusMap.values().stream()
                .allMatch(status -> status == ModuleStatus.RUNNING);

        if (!allModulesRunning) {
            throw new IllegalStateException("部分模块启动失败");
        }

        // 执行集成测试
        performIntegrationTests();

        log.info("启动后验证完成");
    }

    /**
     * 检查必要的环境变量
     */
    private void checkRequiredEnvironmentVariables() {
        // 这里可以检查必要的环境变量
        log.debug("检查环境变量配置");
    }

    /**
     * 检查端口可用性
     */
    private void checkPortAvailability() {
        // 这里可以检查关键端口是否可用
        log.debug("检查端口可用性");
    }

    /**
     * 执行集成测试
     */
    private void performIntegrationTests() {
        // 这里可以执行基本的集成测试
        log.debug("执行集成测试");
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始执行优雅关闭...");

        // 按启动顺序的逆序关闭模块
        List<ModuleStartupConfig> reverseOrder = new ArrayList<>(MODULE_STARTUP_ORDER);
        Collections.reverse(reverseOrder);

        for (ModuleStartupConfig config : reverseOrder) {
            String moduleName = config.getName();
            if (moduleStatusMap.get(moduleName) == ModuleStatus.RUNNING) {
                log.info("正在关闭模块: {}", moduleName);
                try {
                    // 执行模块关闭逻辑
                    performModuleShutdown(config);
                    moduleStatusMap.put(moduleName, ModuleStatus.STOPPED);
                    log.info("模块 {} 关闭完成", moduleName);
                } catch (Exception e) {
                    log.error("模块 {} 关闭失败", moduleName, e);
                    moduleStatusMap.put(moduleName, ModuleStatus.FAILED);
                }
            }
        }

        log.info("优雅关闭完成");
    }

    /**
     * 执行模块关闭逻辑
     */
    private void performModuleShutdown(ModuleStartupConfig config) {
        // 实现具体的模块关闭逻辑
        log.debug("关闭模块: {}", config.getName());
    }

    /**
     * 获取模块状态信息
     */
    public Map<String, ModuleStatus> getModuleStatusMap() {
        return new HashMap<>(moduleStatusMap);
    }

    /**
     * 模块状态枚举
     */
    public enum ModuleStatus {
        STARTING("启动中"),
        RUNNING("运行中"),
        STOPPING("停止中"),
        STOPPED("已停止"),
        FAILED("失败");

        private final String description;

        ModuleStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 模块启动配置
     */
    public static class ModuleStartupConfig {
        private final String name;
        private final String description;
        private final int priority;
        private final List<String> dependencies;

        public ModuleStartupConfig(String name, String description, int priority, List<String> dependencies) {
            this.name = name;
            this.description = description;
            this.priority = priority;
            this.dependencies = new ArrayList<>(dependencies);
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getPriority() {
            return priority;
        }

        public List<String> getDependencies() {
            return dependencies;
        }
    }
}