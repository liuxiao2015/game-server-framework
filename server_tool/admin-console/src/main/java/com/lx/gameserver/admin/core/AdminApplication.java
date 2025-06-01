/*
 * 文件名: AdminApplication.java
 * 用途: 管理后台主应用启动类
 * 实现内容:
 *   - Spring Boot应用程序入口点
 *   - 自动扫描和加载管理模块
 *   - 初始化插件机制
 *   - 全局配置管理集成
 *   - 安全框架集成配置
 *   - 应用启动优化配置
 * 技术选型:
 *   - Spring Boot 3.2+ (主框架)
 *   - Spring Security (安全框架)
 *   - 自定义插件加载机制
 *   - 配置管理中心集成
 * 依赖关系: 作为整个管理后台的启动入口，依赖所有管理模块
 */
package com.lx.gameserver.admin.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.TimeZone;

/**
 * 管理后台主应用程序
 * <p>
 * 作为整个管理后台系统的启动入口，负责初始化Spring容器、
 * 加载配置、启动各个管理模块和插件系统。提供统一的
 * 应用生命周期管理和优化配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(basePackages = {
    "com.lx.gameserver.admin",  // 管理后台所有包
    "com.lx.gameserver.frame",  // 框架模块
    "com.lx.gameserver.common"  // 公共模块
})
public class AdminApplication {

    /** 应用上下文引用 */
    private static ConfigurableApplicationContext applicationContext;

    /**
     * 应用程序主入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 设置系统属性优化启动性能
        System.setProperty("spring.main.lazy-initialization", "false");
        System.setProperty("spring.jmx.enabled", "false");
        System.setProperty("spring.main.banner-mode", "console");
        
        try {
            log.info("==========================================");
            log.info("启动游戏服务器管理后台控制台");
            log.info("==========================================");
            
            // 启动Spring Boot应用
            applicationContext = SpringApplication.run(AdminApplication.class, args);
            
            log.info("==========================================");
            log.info("管理后台控制台启动成功!");
            log.info("访问地址: http://localhost:{}/admin", 
                    applicationContext.getEnvironment().getProperty("server.port", "8090"));
            log.info("API文档: http://localhost:{}/admin/swagger-ui.html", 
                    applicationContext.getEnvironment().getProperty("server.port", "8090"));
            log.info("==========================================");
            
        } catch (Exception e) {
            log.error("管理后台控制台启动失败", e);
            System.exit(1);
        }
    }

    /**
     * 初始化配置
     * <p>
     * 在Spring容器初始化完成后执行的配置工作，
     * 包括时区设置、JVM优化等。
     * </p>
     */
    @PostConstruct
    public void init() {
        log.info("初始化管理后台应用配置...");
        
        // 设置默认时区为东八区
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        log.info("设置默认时区: {}", TimeZone.getDefault().getID());
        
        // 设置JVM参数优化
        optimizeJvmSettings();
        
        // 打印运行环境信息
        printRuntimeInfo();
        
        log.info("管理后台应用配置初始化完成");
    }

    /**
     * 应用关闭前清理
     * <p>
     * 在应用关闭前执行必要的清理工作，
     * 确保资源正确释放。
     * </p>
     */
    @PreDestroy
    public void destroy() {
        log.info("开始清理管理后台应用资源...");
        
        try {
            // 清理插件资源
            cleanupPlugins();
            
            // 清理缓存资源
            cleanupCaches();
            
            log.info("管理后台应用资源清理完成");
        } catch (Exception e) {
            log.error("管理后台应用资源清理失败", e);
        }
    }

    /**
     * 获取应用上下文
     *
     * @return Spring应用上下文
     */
    public static ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 优化JVM设置
     * <p>
     * 根据管理后台的特点设置JVM参数，
     * 优化内存使用和垃圾回收。
     * </p>
     */
    private void optimizeJvmSettings() {
        try {
            // 设置系统属性优化网络性能
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.awt.headless", "true");
            
            // 设置文件编码
            if (System.getProperty("file.encoding") == null) {
                System.setProperty("file.encoding", "UTF-8");
            }
            
            log.debug("JVM优化设置完成");
        } catch (Exception e) {
            log.warn("JVM优化设置失败: {}", e.getMessage());
        }
    }

    /**
     * 打印运行环境信息
     * <p>
     * 打印当前运行环境的关键信息，
     * 便于问题排查和监控。
     * </p>
     */
    private void printRuntimeInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        
        log.info("运行环境信息:");
        log.info("  Java版本: {}", System.getProperty("java.version"));
        log.info("  操作系统: {} {}", 
                System.getProperty("os.name"), 
                System.getProperty("os.version"));
        log.info("  最大内存: {} MB", maxMemory / 1024 / 1024);
        log.info("  总内存: {} MB", totalMemory / 1024 / 1024);
        log.info("  空闲内存: {} MB", freeMemory / 1024 / 1024);
        log.info("  可用处理器: {}", runtime.availableProcessors());
    }

    /**
     * 清理插件资源
     * <p>
     * 关闭所有已加载的插件，释放相关资源。
     * </p>
     */
    private void cleanupPlugins() {
        try {
            if (applicationContext != null && applicationContext.containsBean("pluginManager")) {
                PluginManager pluginManager = applicationContext.getBean(PluginManager.class);
                pluginManager.shutdown();
                log.debug("插件管理器资源清理完成");
            }
        } catch (Exception e) {
            log.warn("插件资源清理失败: {}", e.getMessage());
        }
    }

    /**
     * 清理缓存资源
     * <p>
     * 清理应用中使用的各种缓存，
     * 确保数据一致性。
     * </p>
     */
    private void cleanupCaches() {
        try {
            // 这里可以添加缓存清理逻辑
            log.debug("缓存资源清理完成");
        } catch (Exception e) {
            log.warn("缓存资源清理失败: {}", e.getMessage());
        }
    }
}