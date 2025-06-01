/*
 * 文件名: AdminModule.java
 * 用途: 管理模块接口定义
 * 实现内容:
 *   - 管理模块标准接口定义
 *   - 模块元数据管理
 *   - 菜单注册功能
 *   - API接口注册
 *   - 权限注册管理
 *   - 模块生命周期管理
 * 技术选型:
 *   - Java接口设计模式
 *   - 生命周期管理模式
 *   - 插件化架构设计
 *   - 元数据驱动架构
 * 依赖关系: 被所有管理模块实现，定义模块标准规范
 */
package com.lx.gameserver.admin.core;

import java.util.List;
import java.util.Map;

/**
 * 管理模块接口
 * <p>
 * 定义管理模块的标准接口，所有管理模块都必须实现此接口。
 * 提供模块的生命周期管理、元数据管理、菜单和API注册等功能。
 * 支持模块的动态加载和卸载。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
public interface AdminModule {

    /**
     * 模块状态枚举
     */
    enum ModuleState {
        /** 已创建 */
        CREATED,
        /** 已初始化 */
        INITIALIZED,
        /** 已启动 */
        STARTED,
        /** 已停止 */
        STOPPED,
        /** 已销毁 */
        DESTROYED,
        /** 错误状态 */
        ERROR
    }

    /**
     * 模块类型枚举
     */
    enum ModuleType {
        /** 核心模块 */
        CORE,
        /** 业务模块 */
        BUSINESS,
        /** 扩展模块 */
        EXTENSION,
        /** 插件模块 */
        PLUGIN
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
     * 获取模块类型
     *
     * @return 模块类型
     */
    ModuleType getModuleType();

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
     * 如配置加载、数据结构初始化等。
     * </p>
     *
     * @param context 管理平台上下文
     * @throws Exception 初始化异常
     */
    void initialize(AdminContext context) throws Exception;

    /**
     * 启动模块
     * <p>
     * 启动模块的核心功能，在所有依赖模块初始化完成后调用。
     * </p>
     *
     * @throws Exception 启动异常
     */
    void start() throws Exception;

    /**
     * 停止模块
     * <p>
     * 停止模块的运行，释放运行时资源。
     * </p>
     *
     * @throws Exception 停止异常
     */
    void stop() throws Exception;

    /**
     * 销毁模块
     * <p>
     * 完全销毁模块，释放所有资源。
     * </p>
     *
     * @throws Exception 销毁异常
     */
    void destroy() throws Exception;

    /**
     * 获取模块菜单
     * <p>
     * 返回模块在管理后台中的菜单定义。
     * </p>
     *
     * @return 菜单定义列表
     */
    default List<MenuDefinition> getMenus() {
        return List.of();
    }

    /**
     * 获取模块API定义
     * <p>
     * 返回模块提供的API接口定义。
     * </p>
     *
     * @return API定义列表
     */
    default List<ApiDefinition> getApis() {
        return List.of();
    }

    /**
     * 获取模块权限定义
     * <p>
     * 返回模块的权限定义，用于权限系统管理。
     * </p>
     *
     * @return 权限定义列表
     */
    default List<PermissionDefinition> getPermissions() {
        return List.of();
    }

    /**
     * 检查模块健康状态
     * <p>
     * 返回模块的健康检查结果。
     * </p>
     *
     * @return 健康状态信息
     */
    default HealthStatus getHealthStatus() {
        return new HealthStatus(true, "模块运行正常");
    }

    /**
     * 支持热重载
     * <p>
     * 返回模块是否支持热重载。
     * </p>
     *
     * @return 如果支持热重载返回true，否则返回false
     */
    default boolean supportsHotReload() {
        return false;
    }

    /**
     * 执行热重载
     * <p>
     * 执行模块的热重载操作。只有当supportsHotReload()
     * 返回true时才会调用此方法。
     * </p>
     *
     * @param newVersion 新版本号
     * @throws Exception 热重载异常
     */
    default void hotReload(String newVersion) throws Exception {
        throw new UnsupportedOperationException("模块 " + getModuleName() + " 不支持热重载");
    }

    /**
     * 获取模块优先级
     * <p>
     * 返回模块的启动优先级，数值越小优先级越高。
     * </p>
     *
     * @return 优先级数值
     */
    default int getPriority() {
        return 1000; // 默认优先级
    }

    /**
     * 菜单定义
     */
    class MenuDefinition {
        private String id;
        private String parentId;
        private String name;
        private String path;
        private String icon;
        private int order;
        private boolean visible;
        private String permission;

        // 构造函数、getter和setter省略...
    }

    /**
     * API定义
     */
    class ApiDefinition {
        private String path;
        private String method;
        private String description;
        private String permission;
        private boolean public_;

        // 构造函数、getter和setter省略...
    }

    /**
     * 权限定义
     */
    class PermissionDefinition {
        private String code;
        private String name;
        private String description;
        private String category;
        private boolean system;

        // 构造函数、getter和setter省略...
    }

    /**
     * 健康状态
     */
    class HealthStatus {
        private boolean healthy;
        private String message;
        private Map<String, Object> details;

        public HealthStatus(boolean healthy, String message) {
            this.healthy = healthy;
            this.message = message;
        }

        // getter和setter省略...
    }

    /**
     * 抽象模块基类
     * <p>
     * 提供模块的基础实现，简化具体模块的开发。
     * </p>
     */
    abstract class AbstractAdminModule implements AdminModule {
        protected ModuleState state = ModuleState.CREATED;
        protected AdminContext context;

        @Override
        public final ModuleState getState() {
            return state;
        }

        @Override
        public final void initialize(AdminContext context) throws Exception {
            this.context = context;
            state = ModuleState.INITIALIZED;
            doInitialize();
        }

        @Override
        public final void start() throws Exception {
            if (state != ModuleState.INITIALIZED) {
                throw new IllegalStateException("模块必须先初始化才能启动");
            }
            
            doStart();
            state = ModuleState.STARTED;
        }

        @Override
        public final void stop() throws Exception {
            if (state != ModuleState.STARTED) {
                return;
            }
            
            doStop();
            state = ModuleState.STOPPED;
        }

        @Override
        public final void destroy() throws Exception {
            doDestroy();
            state = ModuleState.DESTROYED;
        }

        /**
         * 执行初始化
         */
        protected abstract void doInitialize() throws Exception;

        /**
         * 执行启动
         */
        protected abstract void doStart() throws Exception;

        /**
         * 执行停止
         */
        protected abstract void doStop() throws Exception;

        /**
         * 执行销毁
         */
        protected abstract void doDestroy() throws Exception;
    }
}