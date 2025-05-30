/*
 * 文件名: ECSConfig.java
 * 用途: ECS配置类
 * 实现内容:
 *   - ECS配置类
 *   - 世界配置
 *   - 系统配置
 *   - 性能配置
 *   - 调试配置
 * 技术选型:
 *   - Builder模式支持配置构建
 *   - 默认值机制简化使用
 *   - 验证机制确保配置有效
 * 依赖关系:
 *   - 被World和各个子系统使用
 *   - 提供统一的配置管理
 *   - 支持配置文件和代码配置
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.config;

import lombok.Data;

/**
 * ECS配置类
 * <p>
 * 提供ECS系统的完整配置选项，包括世界配置、系统配置、性能配置等。
 * 支持通过Builder模式构建配置，提供合理的默认值。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
public class ECSConfig {
    
    /**
     * 世界配置
     */
    private WorldConfig world = new WorldConfig();
    
    /**
     * 系统配置
     */
    private SystemConfig system = new SystemConfig();
    
    /**
     * 优化配置
     */
    private OptimizationConfig optimization = new OptimizationConfig();
    
    /**
     * 持久化配置
     */
    private PersistenceConfig persistence = new PersistenceConfig();
    
    /**
     * 调试配置
     */
    private DebugConfig debug = new DebugConfig();
    
    /**
     * 世界配置
     */
    @Data
    public static class WorldConfig {
        /** 初始实体容量 */
        private int initialEntityCapacity = 10000;
        
        /** 组件池大小 */
        private int componentPoolSize = 1000;
        
        /** 是否启用调试 */
        private boolean enableDebug = false;
        
        /** 最大实体数量 */
        private int maxEntityCount = 100000;
        
        /** 实体ID回收启用 */
        private boolean enableEntityIdRecycling = true;
    }
    
    /**
     * 系统配置
     */
    @Data
    public static class SystemConfig {
        /** 并行处理阈值 */
        private int parallelThreshold = 100;
        
        /** 批处理大小 */
        private int batchSize = 1000;
        
        /** 是否启用性能分析 */
        private boolean enableProfiling = false;
        
        /** 系统更新线程数 */
        private int updateThreads = Runtime.getRuntime().availableProcessors();
        
        /** 是否启用系统依赖检查 */
        private boolean enableDependencyCheck = true;
    }
    
    /**
     * 优化配置
     */
    @Data
    public static class OptimizationConfig {
        /** 是否使用对象池 */
        private boolean useObjectPooling = true;
        
        /** 是否缓存查询 */
        private boolean cacheQueries = true;
        
        /** 内存对齐字节数 */
        private int memoryAlignment = 64;
        
        /** 是否启用SIMD优化 */
        private boolean enableSIMD = false;
        
        /** 是否启用预取优化 */
        private boolean enablePrefetching = true;
    }
    
    /**
     * 持久化配置
     */
    @Data
    public static class PersistenceConfig {
        /** 是否启用快照 */
        private boolean enableSnapshots = true;
        
        /** 快照间隔（秒） */
        private int snapshotInterval = 300;
        
        /** 是否启用压缩 */
        private boolean enableCompression = true;
        
        /** 快照格式 */
        private SnapshotFormat snapshotFormat = SnapshotFormat.BINARY;
        
        /** 是否启用增量快照 */
        private boolean enableIncrementalSnapshots = true;
    }
    
    /**
     * 调试配置
     */
    @Data
    public static class DebugConfig {
        /** 是否启用详细日志 */
        private boolean enableVerboseLogging = false;
        
        /** 是否启用性能监控 */
        private boolean enablePerformanceMonitoring = false;
        
        /** 是否启用内存监控 */
        private boolean enableMemoryMonitoring = false;
        
        /** 是否启用实体统计 */
        private boolean enableEntityStatistics = false;
        
        /** 统计输出间隔（秒） */
        private int statisticsInterval = 60;
    }
    
    /**
     * 快照格式枚举
     */
    public enum SnapshotFormat {
        /** 二进制格式 */
        BINARY,
        /** JSON格式 */
        JSON,
        /** 自定义格式 */
        CUSTOM
    }
    
    /**
     * 默认构造函数
     */
    public ECSConfig() {
        // 使用默认配置
    }
    
    /**
     * 创建构建器
     *
     * @return 配置构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 创建开发环境配置
     *
     * @return 开发环境配置
     */
    public static ECSConfig development() {
        return builder()
                .worldInitialEntityCapacity(1000)
                .systemEnableProfiling(true)
                .debugEnableVerboseLogging(true)
                .debugEnablePerformanceMonitoring(true)
                .build();
    }
    
    /**
     * 创建生产环境配置
     *
     * @return 生产环境配置
     */
    public static ECSConfig production() {
        return builder()
                .worldInitialEntityCapacity(50000)
                .systemParallelThreshold(500)
                .optimizationUseObjectPooling(true)
                .optimizationCacheQueries(true)
                .persistenceEnableSnapshots(true)
                .persistenceEnableCompression(true)
                .build();
    }
    
    /**
     * 创建测试环境配置
     *
     * @return 测试环境配置
     */
    public static ECSConfig testing() {
        return builder()
                .worldInitialEntityCapacity(100)
                .systemEnableProfiling(false)
                .debugEnableEntityStatistics(true)
                .persistenceEnableSnapshots(false)
                .build();
    }
    
    /**
     * 验证配置有效性
     *
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (world.initialEntityCapacity <= 0) {
            throw new IllegalArgumentException("初始实体容量必须大于0");
        }
        
        if (world.maxEntityCount < world.initialEntityCapacity) {
            throw new IllegalArgumentException("最大实体数量不能小于初始容量");
        }
        
        if (system.parallelThreshold <= 0) {
            throw new IllegalArgumentException("并行处理阈值必须大于0");
        }
        
        if (system.batchSize <= 0) {
            throw new IllegalArgumentException("批处理大小必须大于0");
        }
        
        if (system.updateThreads <= 0) {
            throw new IllegalArgumentException("更新线程数必须大于0");
        }
        
        if (optimization.memoryAlignment <= 0 || (optimization.memoryAlignment & (optimization.memoryAlignment - 1)) != 0) {
            throw new IllegalArgumentException("内存对齐必须是2的幂次方");
        }
        
        if (persistence.snapshotInterval <= 0) {
            throw new IllegalArgumentException("快照间隔必须大于0");
        }
        
        if (debug.statisticsInterval <= 0) {
            throw new IllegalArgumentException("统计间隔必须大于0");
        }
    }
    
    /**
     * 配置构建器
     */
    public static class Builder {
        private final ECSConfig config = new ECSConfig();
        
        // World配置方法
        public Builder worldInitialEntityCapacity(int capacity) {
            config.world.initialEntityCapacity = capacity;
            return this;
        }
        
        public Builder worldComponentPoolSize(int size) {
            config.world.componentPoolSize = size;
            return this;
        }
        
        public Builder worldEnableDebug(boolean enable) {
            config.world.enableDebug = enable;
            return this;
        }
        
        public Builder worldMaxEntityCount(int maxCount) {
            config.world.maxEntityCount = maxCount;
            return this;
        }
        
        // System配置方法
        public Builder systemParallelThreshold(int threshold) {
            config.system.parallelThreshold = threshold;
            return this;
        }
        
        public Builder systemBatchSize(int size) {
            config.system.batchSize = size;
            return this;
        }
        
        public Builder systemEnableProfiling(boolean enable) {
            config.system.enableProfiling = enable;
            return this;
        }
        
        public Builder systemUpdateThreads(int threads) {
            config.system.updateThreads = threads;
            return this;
        }
        
        // Optimization配置方法
        public Builder optimizationUseObjectPooling(boolean use) {
            config.optimization.useObjectPooling = use;
            return this;
        }
        
        public Builder optimizationCacheQueries(boolean cache) {
            config.optimization.cacheQueries = cache;
            return this;
        }
        
        public Builder optimizationMemoryAlignment(int alignment) {
            config.optimization.memoryAlignment = alignment;
            return this;
        }
        
        // Persistence配置方法
        public Builder persistenceEnableSnapshots(boolean enable) {
            config.persistence.enableSnapshots = enable;
            return this;
        }
        
        public Builder persistenceSnapshotInterval(int interval) {
            config.persistence.snapshotInterval = interval;
            return this;
        }
        
        public Builder persistenceEnableCompression(boolean enable) {
            config.persistence.enableCompression = enable;
            return this;
        }
        
        public Builder persistenceSnapshotFormat(SnapshotFormat format) {
            config.persistence.snapshotFormat = format;
            return this;
        }
        
        // Debug配置方法
        public Builder debugEnableVerboseLogging(boolean enable) {
            config.debug.enableVerboseLogging = enable;
            return this;
        }
        
        public Builder debugEnablePerformanceMonitoring(boolean enable) {
            config.debug.enablePerformanceMonitoring = enable;
            return this;
        }
        
        public Builder debugEnableEntityStatistics(boolean enable) {
            config.debug.enableEntityStatistics = enable;
            return this;
        }
        
        public Builder debugStatisticsInterval(int interval) {
            config.debug.statisticsInterval = interval;
            return this;
        }
        
        /**
         * 构建配置
         *
         * @return ECS配置实例
         */
        public ECSConfig build() {
            config.validate();
            return config;
        }
    }
}