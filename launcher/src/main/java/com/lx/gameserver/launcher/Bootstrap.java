/*
 * 文件名: Bootstrap.java
 * 用途: 游戏服务器框架的主启动类
 * 内容: 
 *   - 作为整个项目的启动入口点
 *   - 实现运行模式的自动检测和切换
 *   - 配置Spring Boot应用的启动参数
 *   - 输出启动信息和系统状态
 * 技术选型: 
 *   - Spring Boot 3.2+ 启动机制
 *   - Java 21 系统API
 *   - 运行模式检测和配置管理
 * 依赖关系: 
 *   - 依赖Spring Boot启动器
 *   - 使用RunMode和ModeDetector进行模式检测
 *   - 集成GameServerApplication进行应用启动
 */
package com.lx.gameserver.launcher;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 游戏服务器框架启动引导类
 * <p>
 * 作为整个游戏服务器框架的主入口，负责：
 * 1. 运行模式的自动检测（dev/normal）
 * 2. Spring Boot应用的配置和启动
 * 3. 启动信息的输出和展示
 * 4. 系统环境的初始化检查
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
@SpringBootApplication
public class Bootstrap {
    
    /**
     * 应用程序启动时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 应用启动入口点
     * <p>
     * 执行以下启动流程：
     * 1. 检测运行模式（命令行参数、环境变量、IDE检测）
     * 2. 配置Spring Boot应用参数
     * 3. 启动Spring Boot应用
     * 4. 输出启动成功信息
     * </p>
     *
     * @param args 命令行参数，支持--mode=dev/normal参数
     */
    public static void main(String[] args) {
        try {
            // 1. 输出启动开始信息
            printStartupHeader();
            
            // 2. 检测运行模式
            RunMode runMode = ModeDetector.detectMode(args);
            System.out.println("检测到运行模式: " + runMode);
            
            // 3. 输出模式检测详细信息（开发模式下）
            if (runMode.isDev()) {
                System.out.println("\n" + ModeDetector.getDetectionInfo(args));
            }
            
            // 4. 配置并启动Spring Boot应用
            SpringApplication app = createSpringApplication();
            configureApplication(app, runMode);
            
            // 5. 启动应用
            System.out.println("正在启动游戏服务器框架...");
            ConfigurableApplicationContext context = app.run(args);
            
            // 6. 输出启动成功信息
            printStartupSuccess(context, runMode);
            
        } catch (Exception e) {
            System.err.println("游戏服务器启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * 创建Spring Boot应用实例
     *
     * @return 配置好的SpringApplication实例
     */
    private static SpringApplication createSpringApplication() {
        SpringApplication app = new SpringApplication(Bootstrap.class);
        
        // 设置自定义banner
        app.setBannerMode(Banner.Mode.CONSOLE);
        
        return app;
    }
    
    /**
     * 根据运行模式配置Spring Boot应用
     *
     * @param app     SpringApplication实例
     * @param runMode 检测到的运行模式
     */
    private static void configureApplication(SpringApplication app, RunMode runMode) {
        // 设置活跃的配置文件
        System.setProperty("spring.profiles.active", runMode.getCode());
        
        // 根据运行模式设置不同的配置
        if (runMode.isDev()) {
            // 开发模式配置
            System.setProperty("spring.devtools.restart.enabled", "true");
            System.setProperty("logging.level.com.lx.gameserver", "DEBUG");
        } else {
            // 生产模式配置
            System.setProperty("spring.devtools.restart.enabled", "false");
            System.setProperty("logging.level.com.lx.gameserver", "INFO");
        }
        
        // 设置应用名称
        System.setProperty("spring.application.name", "game-server-framework");
    }
    
    /**
     * 输出启动头部信息
     */
    private static void printStartupHeader() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  游戏服务器框架 (Game Server Framework)");
        System.out.println("  版本: 1.0.0-SNAPSHOT");
        System.out.println("  作者: Liu Xiao");
        System.out.println("  启动时间: " + LocalDateTime.now().format(TIME_FORMATTER));
        System.out.println("=".repeat(80));
    }
    
    /**
     * 输出启动成功信息
     *
     * @param context 应用上下文
     * @param runMode 运行模式
     */
    private static void printStartupSuccess(ConfigurableApplicationContext context, RunMode runMode) {
        Environment env = context.getEnvironment();
        
        // 获取服务器端口
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  游戏服务器框架启动成功!");
        System.out.println("  运行模式: " + runMode);
        System.out.println("  服务端口: " + port);
        System.out.println("  访问路径: http://localhost:" + port + contextPath);
        System.out.println("  健康检查: http://localhost:" + port + contextPath + "/actuator/health");
        System.out.println("  启动完成时间: " + LocalDateTime.now().format(TIME_FORMATTER));
        
        // 输出关键配置信息
        printKeyConfigurations(env, runMode);
        
        System.out.println("=".repeat(80) + "\n");
    }
    
    /**
     * 输出关键配置信息
     *
     * @param env     环境配置
     * @param runMode 运行模式
     */
    private static void printKeyConfigurations(Environment env, RunMode runMode) {
        System.out.println("\n  关键配置信息:");
        
        // 数据库配置
        String datasourceUrl = env.getProperty("spring.datasource.url");
        if (datasourceUrl != null) {
            System.out.println("    数据库: " + datasourceUrl);
        }
        
        // Redis配置
        String redisHost = env.getProperty("spring.data.redis.host");
        String redisPort = env.getProperty("spring.data.redis.port");
        if (redisHost != null && redisPort != null) {
            System.out.println("    Redis: " + redisHost + ":" + redisPort);
        }
        
        // 日志级别
        String logLevel = env.getProperty("logging.level.com.lx.gameserver", "INFO");
        System.out.println("    日志级别: " + logLevel);
        
        // JVM信息
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        System.out.println("    JVM最大内存: " + maxMemory + "MB");
        System.out.println("    Java版本: " + System.getProperty("java.version"));
    }
}