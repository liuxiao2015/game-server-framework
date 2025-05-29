/*
 * 文件名: ModeDetector.java
 * 用途: 游戏服务器运行模式检测工具类
 * 内容: 
 *   - 自动检测IDE运行环境
 *   - 解析命令行参数中的运行模式
 *   - 读取环境变量中的运行模式配置
 *   - 提供运行模式的优先级判断逻辑
 * 技术选型: 
 *   - Java 21 系统属性和环境变量API
 *   - 字符串处理和正则表达式
 *   - IDE检测算法
 * 依赖关系: 
 *   - 依赖RunMode枚举
 *   - 被Bootstrap类调用
 */
package com.lx.gameserver.launcher;

import java.util.Arrays;

/**
 * 运行模式检测工具类
 * <p>
 * 负责检测游戏服务器的运行环境和模式，支持多种模式配置方式：
 * 1. 命令行参数（--mode=dev/normal）
 * 2. 环境变量（RUN_MODE）
 * 3. IDE环境自动检测（默认dev模式）
 * 4. 配置文件设置
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public final class ModeDetector {
    
    /**
     * 命令行模式参数前缀
     */
    private static final String MODE_ARG_PREFIX = "--mode=";
    
    /**
     * 环境变量名称
     */
    private static final String ENV_RUN_MODE = "RUN_MODE";
    
    /**
     * IDE检测相关的系统属性和环境变量
     */
    private static final String[] IDE_INDICATORS = {
            "idea.test.cyclic.buffer.size",  // IntelliJ IDEA
            "eclipse.home.location",         // Eclipse
            "VSCODE_PID",                   // VS Code
            "netbeans.home"                 // NetBeans
    };
    
    /**
     * IDE检测相关的Java代理
     */
    private static final String[] IDE_AGENTS = {
            "idea_rt.jar",                  // IntelliJ IDEA
            "eclipse.jdt"                   // Eclipse JDT
    };
    
    /**
     * 私有构造函数，工具类不允许实例化
     */
    private ModeDetector() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }
    
    /**
     * 检测并解析运行模式
     * <p>
     * 按照以下优先级顺序检测运行模式：
     * 1. 命令行参数（--mode=dev/normal）
     * 2. 环境变量（RUN_MODE）
     * 3. IDE环境检测（IDE中默认为dev模式）
     * 4. 默认为dev模式
     * </p>
     *
     * @param args 命令行参数
     * @return 检测到的运行模式
     */
    public static RunMode detectMode(String[] args) {
        // 1. 首先检查命令行参数
        RunMode modeFromArgs = parseFromCommandLine(args);
        if (modeFromArgs != null) {
            return modeFromArgs;
        }
        
        // 2. 检查环境变量
        RunMode modeFromEnv = parseFromEnvironment();
        if (modeFromEnv != null) {
            return modeFromEnv;
        }
        
        // 3. 检测IDE环境
        if (isRunningInIde()) {
            return RunMode.DEV;
        }
        
        // 4. 默认返回开发模式
        return RunMode.DEV;
    }
    
    /**
     * 从命令行参数解析运行模式
     *
     * @param args 命令行参数数组
     * @return 解析到的运行模式，如果未找到则返回null
     */
    private static RunMode parseFromCommandLine(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        
        for (String arg : args) {
            if (arg != null && arg.startsWith(MODE_ARG_PREFIX)) {
                String mode = arg.substring(MODE_ARG_PREFIX.length());
                return RunMode.fromString(mode);
            }
        }
        
        return null;
    }
    
    /**
     * 从环境变量解析运行模式
     *
     * @return 解析到的运行模式，如果未找到则返回null
     */
    private static RunMode parseFromEnvironment() {
        String envMode = System.getenv(ENV_RUN_MODE);
        if (envMode != null && !envMode.trim().isEmpty()) {
            return RunMode.fromString(envMode);
        }
        
        return null;
    }
    
    /**
     * 检测是否在IDE中运行
     * <p>
     * 通过检查系统属性、环境变量和Java代理来判断是否在IDE环境中运行。
     * 这有助于在开发时自动切换到开发模式。
     * </p>
     *
     * @return 如果在IDE中运行返回true，否则返回false
     */
    public static boolean isRunningInIde() {
        // 检查IDE相关的系统属性和环境变量
        for (String indicator : IDE_INDICATORS) {
            if (System.getProperty(indicator) != null || System.getenv(indicator) != null) {
                return true;
            }
        }
        
        // 检查Java代理中是否包含IDE相关的jar
        String javaAgent = System.getProperty("java.class.path");
        if (javaAgent != null) {
            for (String agent : IDE_AGENTS) {
                if (javaAgent.contains(agent)) {
                    return true;
                }
            }
        }
        
        // 检查运行时管理信息
        try {
            String command = System.getProperty("sun.java.command");
            if (command != null && (command.contains("com.intellij") || 
                                  command.contains("org.eclipse") ||
                                  command.contains("org.netbeans"))) {
                return true;
            }
        } catch (Exception e) {
            // 忽略异常，继续其他检测
        }
        
        return false;
    }
    
    /**
     * 获取模式检测结果的详细信息
     *
     * @param args 命令行参数
     * @return 模式检测的详细信息字符串
     */
    public static String getDetectionInfo(String[] args) {
        StringBuilder info = new StringBuilder();
        info.append("运行模式检测信息:\n");
        
        // 命令行参数信息
        info.append("  命令行参数: ").append(Arrays.toString(args)).append("\n");
        RunMode modeFromArgs = parseFromCommandLine(args);
        info.append("  命令行模式: ").append(modeFromArgs != null ? modeFromArgs : "未指定").append("\n");
        
        // 环境变量信息
        String envMode = System.getenv(ENV_RUN_MODE);
        info.append("  环境变量 ").append(ENV_RUN_MODE).append(": ").append(envMode != null ? envMode : "未设置").append("\n");
        RunMode modeFromEnv = parseFromEnvironment();
        info.append("  环境变量模式: ").append(modeFromEnv != null ? modeFromEnv : "未解析").append("\n");
        
        // IDE检测信息
        boolean inIde = isRunningInIde();
        info.append("  IDE环境检测: ").append(inIde ? "是" : "否").append("\n");
        
        // 最终模式
        RunMode finalMode = detectMode(args);
        info.append("  最终运行模式: ").append(finalMode).append("\n");
        
        return info.toString();
    }
}