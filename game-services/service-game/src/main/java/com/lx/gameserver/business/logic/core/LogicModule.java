/*
 * 文件名: LogicModule.java
 * 用途: 逻辑模块接口
 * 实现内容:
 *   - 逻辑模块的生命周期管理接口
 *   - 模块初始化、启动、停止流程定义
 *   - 模块依赖管理和健康检查
 *   - 热更新支持和模块间通信
 *   - 扩展性和插件化支持
 * 技术选型:
 *   - 接口抽象定义模块规范
 *   - 生命周期模式管理模块状态
 *   - 依赖注入支持模块协作
 *   - 事件机制支持模块通信
 * 依赖关系:
 *   - 被所有具体逻辑模块实现
 *   - 与LogicServer协作进行模块管理
 *   - 支持模块间的相互依赖
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.core;

import java.util.List;
import java.util.Map;

/**
 * 逻辑模块接口
 * <p>
 * 定义了逻辑模块的标准接口，包括生命周期管理、
 * 依赖处理、健康检查等功能。所有游戏逻辑模块
 * 都应该实现此接口以确保标准化管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface LogicModule {

    /**
     * 模块状态枚举
     */
    enum ModuleState {
        /** 未初始化 */
        UNINITIALIZED,
        /** 已初始化 */
        INITIALIZED,
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

    /**
     * 获取模块名称
     *
     * @return 模块名称
     */
    String getModuleName();

    /**
     * 获取模块版本
     *
     * @return 模块版本
     */
    String getModuleVersion();

    /**
     * 获取模块描述
     *
     * @return 模块描述
     */
    String getModuleDescription();

    /**
     * 获取模块状态
     *
     * @return 当前状态
     */
    ModuleState getState();

    /**
     * 获取模块依赖
     * <p>
     * 返回此模块依赖的其他模块名称列表。
     * 系统会确保依赖模块先于当前模块启动。
     * </p>
     *
     * @return 依赖模块名称列表
     */
    List<String> getDependencies();

    /**
     * 获取模块配置
     * <p>
     * 返回模块的配置参数，用于模块的个性化配置。
     * </p>
     *
     * @return 配置参数映射
     */
    Map<String, Object> getConfiguration();

    /**
     * 初始化模块
     * <p>
     * 在模块启动前调用，用于初始化模块的基础资源，
     * 如配置加载、数据结构初始化等。此时模块的
     * 依赖模块可能还未启动。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    void initialize() throws Exception;

    /**
     * 启动模块
     * <p>
     * 在所有依赖模块启动后调用，用于启动模块的
     * 业务逻辑，如启动线程、注册事件监听器等。
     * </p>
     *
     * @throws Exception 启动异常
     */
    void start() throws Exception;

    /**
     * 停止模块
     * <p>
     * 停止模块的业务逻辑，释放运行时资源。
     * 会在依赖此模块的其他模块停止后调用。
     * </p>
     *
     * @throws Exception 停止异常
     */
    void stop() throws Exception;

    /**
     * 销毁模块
     * <p>
     * 释放模块的所有资源，包括初始化时分配的资源。
     * 在所有模块停止后调用，用于最终清理。
     * </p>
     *
     * @throws Exception 销毁异常
     */
    void destroy() throws Exception;

    /**
     * 检查模块健康状态
     * <p>
     * 返回模块是否处于健康状态。健康检查会定期
     * 调用此方法来监控模块状态。
     * </p>
     *
     * @return 是否健康
     */
    boolean isHealthy();

    /**
     * 获取模块统计信息
     * <p>
     * 返回模块的运行统计信息，用于监控和调试。
     * </p>
     *
     * @return 统计信息映射
     */
    Map<String, Object> getStatistics();

    /**
     * 重载配置
     * <p>
     * 在运行时重新加载模块配置，支持热更新。
     * 模块应该尽可能支持配置的动态更新。
     * </p>
     *
     * @throws Exception 重载异常
     */
    default void reloadConfiguration() throws Exception {
        // 默认实现：不支持配置重载
        throw new UnsupportedOperationException("模块 " + getModuleName() + " 不支持配置重载");
    }

    /**
     * 处理模块消息
     * <p>
     * 处理来自其他模块的消息，支持模块间通信。
     * </p>
     *
     * @param fromModule 发送方模块名称
     * @param message    消息内容
     * @throws Exception 处理异常
     */
    default void handleMessage(String fromModule, Object message) throws Exception {
        // 默认实现：忽略消息
    }

    /**
     * 获取模块优先级
     * <p>
     * 返回模块的启动优先级，数值越小优先级越高。
     * 在相同依赖层级下，优先级高的模块会先启动。
     * </p>
     *
     * @return 优先级（默认为0）
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 是否支持热更新
     * <p>
     * 返回模块是否支持热更新。支持热更新的模块
     * 可以在运行时进行代码更新而不需要重启。
     * </p>
     *
     * @return 是否支持热更新
     */
    default boolean supportsHotReload() {
        return false;
    }

    /**
     * 执行热更新
     * <p>
     * 执行模块的热更新操作。只有当supportsHotReload()
     * 返回true时才会调用此方法。
     * </p>
     *
     * @param newVersion 新版本号
     * @throws Exception 热更新异常
     */
    default void hotReload(String newVersion) throws Exception {
        throw new UnsupportedOperationException("模块 " + getModuleName() + " 不支持热更新");
    }

    /**
     * 模块启动前回调
     * <p>
     * 在模块启动前调用，可以用于最后的准备工作。
     * </p>
     */
    default void beforeStart() {
        // 默认实现：空操作
    }

    /**
     * 模块启动后回调
     * <p>
     * 在模块启动后调用，可以用于启动后的初始化工作。
     * </p>
     */
    default void afterStart() {
        // 默认实现：空操作
    }

    /**
     * 模块停止前回调
     * <p>
     * 在模块停止前调用，可以用于停止前的清理工作。
     * </p>
     */
    default void beforeStop() {
        // 默认实现：空操作
    }

    /**
     * 模块停止后回调
     * <p>
     * 在模块停止后调用，可以用于停止后的清理工作。
     * </p>
     */
    default void afterStop() {
        // 默认实现：空操作
    }
}