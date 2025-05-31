/*
 * 文件名: PluginContext.java
 * 用途: 插件上下文接口
 * 实现内容:
 *   - 插件运行时环境和服务访问
 *   - 系统资源和组件的统一访问接口
 *   - 插件间通信和事件处理
 *   - 配置管理和日志记录
 *   - 权限控制和安全管理
 * 技术选型:
 *   - 接口抽象提供插件环境
 *   - 服务定位模式访问系统服务
 *   - 事件总线支持插件通信
 *   - 权限框架保证安全性
 * 依赖关系:
 *   - 被LogicPlugin使用获取系统服务
 *   - 与LogicContext集成提供统一接口
 *   - 支持插件的资源访问和管理
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.extension;

import java.util.Properties;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * 插件上下文接口
 * <p>
 * 提供插件运行时所需的环境和服务访问接口，
 * 包括系统服务、配置管理、事件处理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface PluginContext {

    // ========== 基本信息 ==========

    /**
     * 获取插件ID
     *
     * @return 插件ID
     */
    String getPluginId();

    /**
     * 获取游戏版本
     *
     * @return 游戏版本
     */
    String getGameVersion();

    /**
     * 获取系统属性
     *
     * @param key 属性键
     * @return 属性值
     */
    String getSystemProperty(String key);

    /**
     * 获取系统属性
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @return 属性值
     */
    String getSystemProperty(String key, String defaultValue);

    /**
     * 获取环境变量
     *
     * @param name 变量名
     * @return 变量值
     */
    String getEnvironmentVariable(String name);

    // ========== 服务访问 ==========

    /**
     * 获取服务
     *
     * @param serviceClass 服务类型
     * @param <T>          服务类型
     * @return 服务实例
     */
    <T> Optional<T> getService(Class<T> serviceClass);

    /**
     * 获取服务
     *
     * @param serviceName 服务名称
     * @param <T>         服务类型
     * @return 服务实例
     */
    <T> Optional<T> getService(String serviceName);

    /**
     * 获取所有服务
     *
     * @param serviceClass 服务类型
     * @param <T>          服务类型
     * @return 服务实例列表
     */
    <T> List<T> getServices(Class<T> serviceClass);

    /**
     * 注册服务
     *
     * @param serviceClass 服务类型
     * @param service      服务实例
     * @param <T>          服务类型
     * @return 是否注册成功
     */
    <T> boolean registerService(Class<T> serviceClass, T service);

    /**
     * 注册服务
     *
     * @param serviceName 服务名称
     * @param service     服务实例
     * @return 是否注册成功
     */
    boolean registerService(String serviceName, Object service);

    /**
     * 取消注册服务
     *
     * @param serviceClass 服务类型
     * @param <T>          服务类型
     * @return 是否取消成功
     */
    <T> boolean unregisterService(Class<T> serviceClass);

    /**
     * 取消注册服务
     *
     * @param serviceName 服务名称
     * @return 是否取消成功
     */
    boolean unregisterService(String serviceName);

    // ========== 插件管理 ==========

    /**
     * 获取插件
     *
     * @param pluginId 插件ID
     * @return 插件实例
     */
    Optional<LogicPlugin> getPlugin(String pluginId);

    /**
     * 获取所有插件
     *
     * @return 插件ID集合
     */
    Set<String> getPluginIds();

    /**
     * 获取插件状态
     *
     * @param pluginId 插件ID
     * @return 插件状态
     */
    LogicPlugin.PluginState getPluginState(String pluginId);

    /**
     * 检查插件是否启用
     *
     * @param pluginId 插件ID
     * @return 是否启用
     */
    boolean isPluginEnabled(String pluginId);

    /**
     * 获取插件依赖
     *
     * @param pluginId 插件ID
     * @return 依赖插件ID列表
     */
    List<String> getPluginDependencies(String pluginId);

    /**
     * 检查插件依赖是否满足
     *
     * @param pluginId 插件ID
     * @return 是否满足依赖
     */
    boolean arePluginDependenciesSatisfied(String pluginId);

    // ========== 配置管理 ==========

    /**
     * 获取插件配置
     *
     * @return 插件配置
     */
    Properties getPluginConfig();

    /**
     * 获取插件配置
     *
     * @param pluginId 插件ID
     * @return 插件配置
     */
    Properties getPluginConfig(String pluginId);

    /**
     * 保存插件配置
     *
     * @param config 配置对象
     * @return 是否保存成功
     */
    boolean savePluginConfig(Properties config);

    /**
     * 重载插件配置
     *
     * @return 是否重载成功
     */
    boolean reloadPluginConfig();

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    String getConfigValue(String key);

    /**
     * 获取配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getConfigValue(String key, String defaultValue);

    /**
     * 设置配置值
     *
     * @param key   配置键
     * @param value 配置值
     */
    void setConfigValue(String key, String value);

    // ========== 事件处理 ==========

    /**
     * 发布事件
     *
     * @param event 事件对象
     */
    void publishEvent(Object event);

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     * @return 是否订阅成功
     */
    boolean subscribeEvent(Class<?> eventType, Object listener);

    /**
     * 订阅事件
     *
     * @param eventType 事件类型名称
     * @param listener  事件监听器
     * @return 是否订阅成功
     */
    boolean subscribeEvent(String eventType, Object listener);

    /**
     * 取消订阅事件
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     * @return 是否取消成功
     */
    boolean unsubscribeEvent(Class<?> eventType, Object listener);

    /**
     * 取消订阅事件
     *
     * @param eventType 事件类型名称
     * @param listener  事件监听器
     * @return 是否取消成功
     */
    boolean unsubscribeEvent(String eventType, Object listener);

    // ========== 插件通信 ==========

    /**
     * 发送消息给插件
     *
     * @param targetPluginId 目标插件ID
     * @param message        消息内容
     * @return 发送结果
     */
    Object sendMessage(String targetPluginId, Object message);

    /**
     * 广播消息给所有插件
     *
     * @param message 消息内容
     */
    void broadcastMessage(Object message);

    /**
     * 广播消息给指定插件
     *
     * @param targetPluginIds 目标插件ID列表
     * @param message         消息内容
     */
    void broadcastMessage(List<String> targetPluginIds, Object message);

    // ========== 日志记录 ==========

    /**
     * 记录调试日志
     *
     * @param message 日志消息
     */
    void debug(String message);

    /**
     * 记录调试日志
     *
     * @param format 格式字符串
     * @param args   参数
     */
    void debug(String format, Object... args);

    /**
     * 记录信息日志
     *
     * @param message 日志消息
     */
    void info(String message);

    /**
     * 记录信息日志
     *
     * @param format 格式字符串
     * @param args   参数
     */
    void info(String format, Object... args);

    /**
     * 记录警告日志
     *
     * @param message 日志消息
     */
    void warn(String message);

    /**
     * 记录警告日志
     *
     * @param format 格式字符串
     * @param args   参数
     */
    void warn(String format, Object... args);

    /**
     * 记录错误日志
     *
     * @param message 日志消息
     * @param throwable 异常
     */
    void error(String message, Throwable throwable);

    /**
     * 记录错误日志
     *
     * @param format 格式字符串
     * @param throwable 异常
     * @param args   参数
     */
    void error(String format, Throwable throwable, Object... args);

    // ========== 资源管理 ==========

    /**
     * 获取插件数据目录
     *
     * @return 数据目录路径
     */
    Path getDataDirectory();

    /**
     * 获取插件资源目录
     *
     * @return 资源目录路径
     */
    Path getResourceDirectory();

    /**
     * 获取插件资源
     *
     * @param resourceName 资源名称
     * @return 资源输入流
     */
    Optional<InputStream> getResource(String resourceName);

    /**
     * 创建数据文件
     *
     * @param fileName 文件名
     * @return 文件输出流
     */
    Optional<OutputStream> createDataFile(String fileName);

    /**
     * 读取数据文件
     *
     * @param fileName 文件名
     * @return 文件输入流
     */
    Optional<InputStream> readDataFile(String fileName);

    /**
     * 删除数据文件
     *
     * @param fileName 文件名
     * @return 是否删除成功
     */
    boolean deleteDataFile(String fileName);

    /**
     * 列出数据文件
     *
     * @return 文件名列表
     */
    List<String> listDataFiles();

    // ========== 权限管理 ==========

    /**
     * 检查权限
     *
     * @param permission 权限名称
     * @return 是否有权限
     */
    boolean hasPermission(String permission);

    /**
     * 请求权限
     *
     * @param permission 权限名称
     * @param reason     请求原因
     * @return 是否授权成功
     */
    boolean requestPermission(String permission, String reason);

    /**
     * 获取已授权权限
     *
     * @return 权限列表
     */
    Set<String> getGrantedPermissions();

    // ========== 调度服务 ==========

    /**
     * 调度任务
     *
     * @param task  任务
     * @param delay 延迟时间（毫秒）
     * @return 任务ID
     */
    String scheduleTask(Runnable task, long delay);

    /**
     * 调度周期任务
     *
     * @param task         任务
     * @param initialDelay 初始延迟（毫秒）
     * @param period       周期（毫秒）
     * @return 任务ID
     */
    String scheduleRepeatingTask(Runnable task, long initialDelay, long period);

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    boolean cancelTask(String taskId);

    // ========== 数据存储 ==========

    /**
     * 存储数据
     *
     * @param key   键
     * @param value 值
     */
    void storeData(String key, Object value);

    /**
     * 获取数据
     *
     * @param key 键
     * @return 值
     */
    Object retrieveData(String key);

    /**
     * 获取数据
     *
     * @param key         键
     * @param targetClass 目标类型
     * @param <T>         数据类型
     * @return 值
     */
    <T> Optional<T> retrieveData(String key, Class<T> targetClass);

    /**
     * 删除数据
     *
     * @param key 键
     * @return 是否删除成功
     */
    boolean removeData(String key);

    /**
     * 清空数据
     */
    void clearData();

    /**
     * 获取所有数据键
     *
     * @return 键集合
     */
    Set<String> getDataKeys();

    // ========== 监控和统计 ==========

    /**
     * 记录指标
     *
     * @param metricName 指标名称
     * @param value      指标值
     */
    void recordMetric(String metricName, Number value);

    /**
     * 增加计数器
     *
     * @param counterName 计数器名称
     */
    void incrementCounter(String counterName);

    /**
     * 增加计数器
     *
     * @param counterName 计数器名称
     * @param delta       增量
     */
    void incrementCounter(String counterName, long delta);

    /**
     * 获取指标值
     *
     * @param metricName 指标名称
     * @return 指标值
     */
    Optional<Number> getMetric(String metricName);

    /**
     * 获取所有指标
     *
     * @return 指标映射
     */
    Map<String, Number> getAllMetrics();

    // ========== 工具方法 ==========

    /**
     * 获取当前时间戳
     *
     * @return 时间戳（毫秒）
     */
    long getCurrentTimestamp();

    /**
     * 生成唯一ID
     *
     * @return 唯一ID
     */
    String generateUniqueId();

    /**
     * 格式化字符串
     *
     * @param template 模板
     * @param args     参数
     * @return 格式化结果
     */
    String formatString(String template, Object... args);

    /**
     * 解析JSON
     *
     * @param json        JSON字符串
     * @param targetClass 目标类型
     * @param <T>         数据类型
     * @return 解析结果
     */
    <T> Optional<T> parseJson(String json, Class<T> targetClass);

    /**
     * 转换为JSON
     *
     * @param object 对象
     * @return JSON字符串
     */
    Optional<String> toJson(Object object);

    // ========== 生命周期回调注册 ==========

    /**
     * 注册插件启动回调
     *
     * @param callback 回调函数
     */
    void onPluginStart(Runnable callback);

    /**
     * 注册插件停止回调
     *
     * @param callback 回调函数
     */
    void onPluginStop(Runnable callback);

    /**
     * 注册插件重载回调
     *
     * @param callback 回调函数
     */
    void onPluginReload(Runnable callback);

    /**
     * 注册系统关闭回调
     *
     * @param callback 回调函数
     */
    void onSystemShutdown(Runnable callback);
}