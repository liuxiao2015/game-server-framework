/*
 * 文件名: LogicPlugin.java
 * 用途: 逻辑插件接口
 * 实现内容:
 *   - 插件生命周期管理接口
 *   - 插件配置和依赖管理
 *   - 热插拔支持和版本控制
 *   - 插件通信和事件处理
 *   - 插件元数据和权限管理
 * 技术选型:
 *   - 接口抽象定义插件规范
 *   - SPI机制支持插件发现
 *   - 类加载器实现插件隔离
 *   - 事件总线支持插件通信
 * 依赖关系:
 *   - 被具体插件实现类继承
 *   - 与LogicContext集成进行插件管理
 *   - 支持插件间的相互依赖
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.extension;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 逻辑插件接口
 * <p>
 * 定义了逻辑插件的标准接口，支持插件的生命周期管理、
 * 配置管理、依赖处理、热插拔等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface LogicPlugin {

    /**
     * 插件状态枚举
     */
    enum PluginState {
        /** 未加载 */
        UNLOADED,
        /** 已加载 */
        LOADED,
        /** 初始化中 */
        INITIALIZING,
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
        /** 卸载中 */
        UNLOADING,
        /** 错误状态 */
        ERROR
    }

    /**
     * 插件优先级
     */
    enum PluginPriority {
        /** 最高优先级 */
        HIGHEST(1),
        /** 高优先级 */
        HIGH(2),
        /** 普通优先级 */
        NORMAL(3),
        /** 低优先级 */
        LOW(4),
        /** 最低优先级 */
        LOWEST(5);

        private final int value;

        PluginPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 插件信息
     */
    interface PluginInfo {
        /**
         * 获取插件ID
         */
        String getId();

        /**
         * 获取插件名称
         */
        String getName();

        /**
         * 获取插件版本
         */
        String getVersion();

        /**
         * 获取插件描述
         */
        String getDescription();

        /**
         * 获取插件作者
         */
        String getAuthor();

        /**
         * 获取插件主页
         */
        String getHomepage();

        /**
         * 获取插件优先级
         */
        PluginPriority getPriority();

        /**
         * 获取最小游戏版本要求
         */
        String getMinGameVersion();

        /**
         * 获取最大游戏版本要求
         */
        String getMaxGameVersion();

        /**
         * 获取插件依赖
         */
        List<String> getDependencies();

        /**
         * 获取软依赖（可选依赖）
         */
        List<String> getSoftDependencies();

        /**
         * 获取插件冲突
         */
        List<String> getConflicts();

        /**
         * 获取插件标签
         */
        List<String> getTags();

        /**
         * 获取扩展属性
         */
        Map<String, Object> getExtendedProperties();
    }

    // ========== 核心方法 ==========

    /**
     * 获取插件信息
     *
     * @return 插件信息
     */
    PluginInfo getPluginInfo();

    /**
     * 获取插件状态
     *
     * @return 当前状态
     */
    PluginState getState();

    /**
     * 加载插件
     * <p>
     * 在插件被添加到系统时调用，用于初始化插件的基础资源。
     * </p>
     *
     * @param context 插件上下文
     * @throws Exception 加载异常
     */
    void load(PluginContext context) throws Exception;

    /**
     * 初始化插件
     * <p>
     * 在所有依赖插件加载完成后调用，用于初始化插件的业务逻辑。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    void initialize() throws Exception;

    /**
     * 启动插件
     * <p>
     * 在系统启动时调用，用于启动插件的服务和功能。
     * </p>
     *
     * @throws Exception 启动异常
     */
    void start() throws Exception;

    /**
     * 停止插件
     * <p>
     * 在系统关闭或插件被禁用时调用，用于停止插件的服务。
     * </p>
     *
     * @throws Exception 停止异常
     */
    void stop() throws Exception;

    /**
     * 卸载插件
     * <p>
     * 在插件被移除时调用，用于释放插件的所有资源。
     * </p>
     *
     * @throws Exception 卸载异常
     */
    void unload() throws Exception;

    // ========== 配置管理 ==========

    /**
     * 获取插件配置
     *
     * @return 插件配置
     */
    default Properties getConfig() {
        return new Properties();
    }

    /**
     * 重载配置
     * <p>
     * 在配置文件发生变化时调用，用于重新加载插件配置。
     * </p>
     *
     * @param config 新配置
     * @throws Exception 重载异常
     */
    default void reloadConfig(Properties config) throws Exception {
        // 默认实现：不支持配置重载
    }

    /**
     * 验证配置
     * <p>
     * 验证插件配置是否有效。
     * </p>
     *
     * @param config 配置
     * @return 是否有效
     */
    default boolean validateConfig(Properties config) {
        return true;
    }

    // ========== 生命周期回调 ==========

    /**
     * 插件加载前回调
     */
    default void beforeLoad() {
        // 默认实现：空操作
    }

    /**
     * 插件加载后回调
     */
    default void afterLoad() {
        // 默认实现：空操作
    }

    /**
     * 插件启动前回调
     */
    default void beforeStart() {
        // 默认实现：空操作
    }

    /**
     * 插件启动后回调
     */
    default void afterStart() {
        // 默认实现：空操作
    }

    /**
     * 插件停止前回调
     */
    default void beforeStop() {
        // 默认实现：空操作
    }

    /**
     * 插件停止后回调
     */
    default void afterStop() {
        // 默认实现：空操作
    }

    /**
     * 插件卸载前回调
     */
    default void beforeUnload() {
        // 默认实现：空操作
    }

    /**
     * 插件卸载后回调
     */
    default void afterUnload() {
        // 默认实现：空操作
    }

    // ========== 事件处理 ==========

    /**
     * 处理插件事件
     * <p>
     * 处理来自系统或其他插件的事件。
     * </p>
     *
     * @param event 事件对象
     */
    default void handleEvent(Object event) {
        // 默认实现：忽略事件
    }

    /**
     * 处理插件消息
     * <p>
     * 处理来自其他插件的消息。
     * </p>
     *
     * @param sourcePlugin 发送方插件ID
     * @param message      消息内容
     * @return 处理结果
     */
    default Object handleMessage(String sourcePlugin, Object message) {
        // 默认实现：返回null
        return null;
    }

    // ========== 健康检查 ==========

    /**
     * 检查插件健康状态
     *
     * @return 是否健康
     */
    default boolean isHealthy() {
        return getState() == PluginState.RUNNING;
    }

    /**
     * 获取健康检查详情
     *
     * @return 健康检查详情
     */
    default Map<String, Object> getHealthDetails() {
        return Map.of(
                "state", getState().name(),
                "healthy", isHealthy()
        );
    }

    // ========== 统计信息 ==========

    /**
     * 获取插件统计信息
     *
     * @return 统计信息
     */
    default Map<String, Object> getStatistics() {
        return Map.of(
                "pluginId", getPluginInfo().getId(),
                "state", getState().name(),
                "version", getPluginInfo().getVersion()
        );
    }

    /**
     * 获取插件指标
     *
     * @return 指标信息
     */
    default Map<String, Number> getMetrics() {
        return Map.of();
    }

    // ========== 权限管理 ==========

    /**
     * 获取插件所需权限
     *
     * @return 权限列表
     */
    default List<String> getRequiredPermissions() {
        return List.of();
    }

    /**
     * 检查是否有指定权限
     *
     * @param permission 权限名称
     * @return 是否有权限
     */
    default boolean hasPermission(String permission) {
        return true; // 默认实现：总是返回true
    }

    // ========== 热更新支持 ==========

    /**
     * 是否支持热更新
     *
     * @return 是否支持热更新
     */
    default boolean supportsHotReload() {
        return false;
    }

    /**
     * 执行热更新
     *
     * @param newVersion 新版本插件
     * @throws Exception 热更新异常
     */
    default void hotReload(LogicPlugin newVersion) throws Exception {
        throw new UnsupportedOperationException("插件不支持热更新");
    }

    /**
     * 获取热更新兼容版本
     *
     * @return 兼容版本列表
     */
    default List<String> getHotReloadCompatibleVersions() {
        return List.of();
    }

    // ========== 资源管理 ==========

    /**
     * 获取插件资源路径
     *
     * @param resourceName 资源名称
     * @return 资源路径
     */
    default String getResourcePath(String resourceName) {
        return null;
    }

    /**
     * 获取插件数据目录
     *
     * @return 数据目录路径
     */
    default String getDataDirectory() {
        return "plugins/" + getPluginInfo().getId();
    }

    /**
     * 获取插件配置文件路径
     *
     * @return 配置文件路径
     */
    default String getConfigFile() {
        return getDataDirectory() + "/config.properties";
    }

    // ========== 调试支持 ==========

    /**
     * 是否为调试模式
     *
     * @return 是否为调试模式
     */
    default boolean isDebugMode() {
        return false;
    }

    /**
     * 设置调试模式
     *
     * @param debug 是否启用调试
     */
    default void setDebugMode(boolean debug) {
        // 默认实现：空操作
    }

    /**
     * 获取调试信息
     *
     * @return 调试信息
     */
    default Map<String, Object> getDebugInfo() {
        return Map.of();
    }

    // ========== 插件通信 ==========

    /**
     * 发送消息给其他插件
     *
     * @param targetPlugin 目标插件ID
     * @param message      消息内容
     * @return 发送结果
     */
    default boolean sendMessage(String targetPlugin, Object message) {
        return false; // 默认实现：发送失败
    }

    /**
     * 广播消息给所有插件
     *
     * @param message 消息内容
     */
    default void broadcastMessage(Object message) {
        // 默认实现：空操作
    }

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @return 是否订阅成功
     */
    default boolean subscribeEvent(String eventType) {
        return false;
    }

    /**
     * 取消订阅事件
     *
     * @param eventType 事件类型
     * @return 是否取消成功
     */
    default boolean unsubscribeEvent(String eventType) {
        return false;
    }

    /**
     * 发布事件
     *
     * @param event 事件对象
     */
    default void publishEvent(Object event) {
        // 默认实现：空操作
    }
}