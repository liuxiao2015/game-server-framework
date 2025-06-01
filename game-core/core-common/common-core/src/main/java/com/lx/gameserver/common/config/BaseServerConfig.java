/*
 * 文件名: BaseServerConfig.java
 * 用途: 服务器基础配置类 - 简化版
 * 实现内容:
 *   - 统一的服务器配置基类
 *   - 通用的服务器配置属性
 *   - 基础配置验证
 *   - 支持配置继承和扩展
 * 技术选型:
 *   - 纯Java实现，无外部依赖
 *   - 准备升级到Java 21
 * 依赖关系:
 *   - 被各业务模块配置类继承
 *   - 提供通用配置功能
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.common.config;

import java.time.Duration;

/**
 * 服务器基础配置类
 * <p>
 * 提供所有服务器共同的配置属性，包括服务器基础信息、
 * 性能配置、监控配置等。其他业务服务可以继承此类
 * 并添加自己特有的配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
public abstract class BaseServerConfig {

    /** 服务器配置 */
    private ServerInfo server = new ServerInfo();

    /** 性能配置 */
    private PerformanceConfig performance = new PerformanceConfig();

    /** 监控配置 */
    private MonitoringConfig monitoring = new MonitoringConfig();

    /**
     * 服务器基础信息配置
     */
    public static class ServerInfo {
        /** 服务器ID */
        private String id;

        /** 服务器名称 */
        private String name;

        /** 服务器类型 */
        private String type;

        /** 服务器版本 */
        private String version = "1.0.0";

        /** 服务器描述 */
        private String description = "";

        /** 维护模式 */
        private boolean maintenanceMode = false;

        /** 调试模式 */
        private boolean debugMode = false;

        /** 服务器容量限制 */
        private int capacity = 10000;

        /** 服务器端口 */
        private int port = 8080;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isMaintenanceMode() { return maintenanceMode; }
        public void setMaintenanceMode(boolean maintenanceMode) { this.maintenanceMode = maintenanceMode; }
        public boolean isDebugMode() { return debugMode; }
        public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    /**
     * 性能配置
     */
    public static class PerformanceConfig {
        /** 使用虚拟线程 */
        private boolean useVirtualThreads = true;

        /** 工作线程数 */
        private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;

        /** IO线程数 */
        private int ioThreads = Runtime.getRuntime().availableProcessors();

        /** 批处理大小 */
        private int batchSize = 100;

        /** 队列容量 */
        private int queueCapacity = 10000;

        /** 任务超时时间 */
        private Duration taskTimeout = Duration.ofSeconds(30);

        /** 垃圾回收优化 */
        private boolean gcOptimization = true;

        /** 内存预分配 */
        private boolean preAllocateMemory = true;

        // Getters and Setters
        public boolean isUseVirtualThreads() { return useVirtualThreads; }
        public void setUseVirtualThreads(boolean useVirtualThreads) { this.useVirtualThreads = useVirtualThreads; }
        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
        public int getIoThreads() { return ioThreads; }
        public void setIoThreads(int ioThreads) { this.ioThreads = ioThreads; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public Duration getTaskTimeout() { return taskTimeout; }
        public void setTaskTimeout(Duration taskTimeout) { this.taskTimeout = taskTimeout; }
        public boolean isGcOptimization() { return gcOptimization; }
        public void setGcOptimization(boolean gcOptimization) { this.gcOptimization = gcOptimization; }
        public boolean isPreAllocateMemory() { return preAllocateMemory; }
        public void setPreAllocateMemory(boolean preAllocateMemory) { this.preAllocateMemory = preAllocateMemory; }
    }

    /**
     * 监控配置
     */
    public static class MonitoringConfig {
        /** 启用监控 */
        private boolean enabled = true;

        /** 监控间隔 */
        private Duration interval = Duration.ofSeconds(30);

        /** 启用性能分析 */
        private boolean enableProfiling = false;

        /** 启用指标收集 */
        private boolean enableMetrics = true;

        /** 启用健康检查 */
        private boolean enableHealthCheck = true;

        /** 监控数据保留时间 */
        private Duration dataRetention = Duration.ofDays(7);

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public boolean isEnableProfiling() { return enableProfiling; }
        public void setEnableProfiling(boolean enableProfiling) { this.enableProfiling = enableProfiling; }
        public boolean isEnableMetrics() { return enableMetrics; }
        public void setEnableMetrics(boolean enableMetrics) { this.enableMetrics = enableMetrics; }
        public boolean isEnableHealthCheck() { return enableHealthCheck; }
        public void setEnableHealthCheck(boolean enableHealthCheck) { this.enableHealthCheck = enableHealthCheck; }
        public Duration getDataRetention() { return dataRetention; }
        public void setDataRetention(Duration dataRetention) { this.dataRetention = dataRetention; }
    }

    // Getters and Setters for main class
    public ServerInfo getServer() { return server; }
    public void setServer(ServerInfo server) { this.server = server; }
    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
    public MonitoringConfig getMonitoring() { return monitoring; }
    public void setMonitoring(MonitoringConfig monitoring) { this.monitoring = monitoring; }

    /**
     * 验证配置
     *
     * @return 验证结果
     */
    public boolean validate() {
        try {
            if (server.getCapacity() <= 0 || server.getPort() <= 0) {
                return false;
            }
            if (performance.getWorkerThreads() <= 0 || performance.getIoThreads() <= 0) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取服务器标识
     *
     * @return 服务器标识
     */
    public String getServerIdentifier() {
        return String.format("%s-%s", server.getType(), server.getId());
    }

    /**
     * 检查是否启用调试模式
     *
     * @return 是否启用调试模式
     */
    public boolean isDebugEnabled() {
        return server.isDebugMode();
    }

    /**
     * 检查是否为维护模式
     *
     * @return 是否为维护模式
     */
    public boolean isMaintenanceMode() {
        return server.isMaintenanceMode();
    }

    /**
     * 检查是否启用虚拟线程
     *
     * @return 是否启用虚拟线程
     */
    public boolean isVirtualThreadsEnabled() {
        return performance.isUseVirtualThreads();
    }

    /**
     * 检查是否启用监控
     *
     * @return 是否启用监控
     */
    public boolean isMonitoringEnabled() {
        return monitoring.isEnabled();
    }
}