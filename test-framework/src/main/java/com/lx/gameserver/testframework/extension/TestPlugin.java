/*
 * 文件名: TestPlugin.java
 * 用途: 测试框架插件接口
 * 内容: 
 *   - 插件生命周期管理
 *   - 插件信息定义
 *   - 插件配置管理
 *   - 插件注册和通信机制
 *   - 扩展点定义
 * 技术选型: 
 *   - Java 21 SPI机制
 *   - 插件模式设计
 *   - 生命周期管理
 * 依赖关系: 
 *   - 被TestFramework调用
 *   - 依赖TestContext进行环境访问
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.extension;

import com.lx.gameserver.testframework.core.TestContext;

/**
 * 测试框架插件接口
 * <p>
 * 定义测试框架插件的标准接口，支持插件的生命周期管理、
 * 配置管理和扩展功能。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
public interface TestPlugin {
    
    /**
     * 获取插件信息
     * 
     * @return 插件信息
     */
    PluginInfo getPluginInfo();
    
    /**
     * 加载插件
     * 
     * @param context 测试上下文
     * @throws Exception 加载异常
     */
    void load(TestContext context) throws Exception;
    
    /**
     * 初始化插件
     * 
     * @throws Exception 初始化异常
     */
    default void initialize() throws Exception {
        // 默认空实现
    }
    
    /**
     * 启动插件
     * 
     * @throws Exception 启动异常
     */
    default void start() throws Exception {
        // 默认空实现
    }
    
    /**
     * 停止插件
     * 
     * @throws Exception 停止异常
     */
    default void stop() throws Exception {
        // 默认空实现
    }
    
    /**
     * 卸载插件
     * 
     * @throws Exception 卸载异常
     */
    default void unload() throws Exception {
        // 默认空实现
    }
    
    /**
     * 检查插件是否兼容
     * 
     * @param frameworkVersion 框架版本
     * @return 是否兼容
     */
    default boolean isCompatible(String frameworkVersion) {
        return true; // 默认兼容
    }
    
    /**
     * 获取插件配置
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @param <T> 配置值类型
     * @return 配置值
     */
    default <T> T getConfig(String key, T defaultValue) {
        return defaultValue; // 默认返回默认值
    }
    
    /**
     * 设置插件配置
     * 
     * @param key 配置键
     * @param value 配置值
     */
    default void setConfig(String key, Object value) {
        // 默认空实现
    }
    
    /**
     * 插件信息接口
     */
    interface PluginInfo {
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
         * 获取插件作者
         * 
         * @return 插件作者
         */
        default String getAuthor() {
            return "Unknown";
        }
        
        /**
         * 获取插件主页
         * 
         * @return 插件主页
         */
        default String getHomepage() {
            return "";
        }
        
        /**
         * 获取最低框架版本要求
         * 
         * @return 最低框架版本
         */
        default String getMinFrameworkVersion() {
            return "1.0.0";
        }
        
        /**
         * 获取插件依赖
         * 
         * @return 依赖插件列表
         */
        default String[] getDependencies() {
            return new String[0];
        }
    }
}