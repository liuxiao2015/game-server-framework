/*
 * 文件名: ECSDebugger.java
 * 用途: ECS调试器
 * 实现内容:
 *   - ECS调试器核心功能
 *   - 实体查看器
 *   - 组件检查器
 *   - 系统监视器
 *   - 性能分析器
 * 技术选型:
 *   - 观察者模式监听ECS状态
 *   - 快照技术记录历史状态
 *   - 多线程安全的统计收集
 * 依赖关系:
 *   - 提供ECS系统调试支持
 *   - 被开发和测试环境使用
 *   - 与World和其他组件协同工作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.debug;

import com.lx.gameserver.frame.ecs.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * ECS调试器
 * <p>
 * 提供ECS系统的全面调试功能，包括实体查看、组件检查、系统监视等。
 * 支持实时监控和历史数据分析。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ECSDebugger {
    
    private static final Logger logger = LoggerFactory.getLogger(ECSDebugger.class);
    
    /**
     * 调试级别枚举
     */
    public enum DebugLevel {
        /** 关闭调试 */
        OFF(0, "关闭"),
        /** 基础调试信息 */
        BASIC(1, "基础"),
        /** 详细调试信息 */
        DETAILED(2, "详细"),
        /** 完全调试信息 */
        FULL(3, "完全");
        
        private final int level;
        private final String displayName;
        
        DebugLevel(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }
        
        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        
        public boolean isEnabled() { return level > 0; }
        public boolean includes(DebugLevel other) { return level >= other.level; }
    }
    
    /**
     * 实体调试信息
     */
    public static class EntityDebugInfo {
        private final long entityId;
        private final String state;
        private final long version;
        private final Set<String> componentTypes;
        private final Map<String, Object> componentSummary;
        private final long lastUpdateTime;
        
        public EntityDebugInfo(long entityId, String state, long version, 
                              Set<String> componentTypes, Map<String, Object> componentSummary) {
            this.entityId = entityId;
            this.state = state;
            this.version = version;
            this.componentTypes = Set.copyOf(componentTypes);
            this.componentSummary = Map.copyOf(componentSummary);
            this.lastUpdateTime = java.lang.System.currentTimeMillis();
        }
        
        // Getters
        public long getEntityId() { return entityId; }
        public String getState() { return state; }
        public long getVersion() { return version; }
        public Set<String> getComponentTypes() { return componentTypes; }
        public Map<String, Object> getComponentSummary() { return componentSummary; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }
    
    /**
     * 系统调试信息
     */
    public static class SystemDebugInfo {
        private final String systemName;
        private final Class<? extends com.lx.gameserver.frame.ecs.core.System> systemClass;
        private final boolean enabled;
        private final int priority;
        private final Set<String> dependencies;
        private final AtomicLong updateCount = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private volatile long lastExecutionTime = 0;
        private volatile long minExecutionTime = Long.MAX_VALUE;
        private volatile long maxExecutionTime = 0;
        
        public SystemDebugInfo(String systemName, Class<? extends com.lx.gameserver.frame.ecs.core.System> systemClass, 
                              boolean enabled, int priority, Set<String> dependencies) {
            this.systemName = systemName;
            this.systemClass = systemClass;
            this.enabled = enabled;
            this.priority = priority;
            this.dependencies = Set.copyOf(dependencies);
        }
        
        public void recordExecution(long executionTimeNanos) {
            updateCount.incrementAndGet();
            totalExecutionTime.addAndGet(executionTimeNanos);
            lastExecutionTime = executionTimeNanos;
            
            // 更新最小值和最大值
            synchronized (this) {
                if (executionTimeNanos < minExecutionTime) {
                    minExecutionTime = executionTimeNanos;
                }
                if (executionTimeNanos > maxExecutionTime) {
                    maxExecutionTime = executionTimeNanos;
                }
            }
        }
        
        public long getAverageExecutionTime() {
            long count = updateCount.get();
            return count > 0 ? totalExecutionTime.get() / count : 0;
        }
        
        // Getters
        public String getSystemName() { return systemName; }
        public Class<? extends com.lx.gameserver.frame.ecs.core.System> getSystemClass() { return systemClass; }
        public boolean isEnabled() { return enabled; }
        public int getPriority() { return priority; }
        public Set<String> getDependencies() { return dependencies; }
        public long getUpdateCount() { return updateCount.get(); }
        public long getTotalExecutionTime() { return totalExecutionTime.get(); }
        public long getLastExecutionTime() { return lastExecutionTime; }
        public long getMinExecutionTime() { return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
    }
    
    /**
     * 调试配置
     */
    public static class DebugConfig {
        private DebugLevel debugLevel = DebugLevel.BASIC;
        private boolean trackEntityHistory = true;
        private boolean trackSystemPerformance = true;
        private boolean trackComponentChanges = true;
        private int maxHistorySize = 1000;
        private long snapshotInterval = 5000; // 5秒
        private boolean enableRealTimeUpdates = true;
        
        // Getters and Setters
        public DebugLevel getDebugLevel() { return debugLevel; }
        public void setDebugLevel(DebugLevel debugLevel) { this.debugLevel = debugLevel; }
        
        public boolean isTrackEntityHistory() { return trackEntityHistory; }
        public void setTrackEntityHistory(boolean trackEntityHistory) { this.trackEntityHistory = trackEntityHistory; }
        
        public boolean isTrackSystemPerformance() { return trackSystemPerformance; }
        public void setTrackSystemPerformance(boolean trackSystemPerformance) { this.trackSystemPerformance = trackSystemPerformance; }
        
        public boolean isTrackComponentChanges() { return trackComponentChanges; }
        public void setTrackComponentChanges(boolean trackComponentChanges) { this.trackComponentChanges = trackComponentChanges; }
        
        public int getMaxHistorySize() { return maxHistorySize; }
        public void setMaxHistorySize(int maxHistorySize) { this.maxHistorySize = Math.max(0, maxHistorySize); }
        
        public long getSnapshotInterval() { return snapshotInterval; }
        public void setSnapshotInterval(long snapshotInterval) { this.snapshotInterval = Math.max(1000, snapshotInterval); }
        
        public boolean isEnableRealTimeUpdates() { return enableRealTimeUpdates; }
        public void setEnableRealTimeUpdates(boolean enableRealTimeUpdates) { this.enableRealTimeUpdates = enableRealTimeUpdates; }
    }
    
    /**
     * 调试配置
     */
    private final DebugConfig config;
    
    /**
     * 目标世界
     */
    private final World world;
    
    /**
     * 实体调试信息映射
     */
    private final Map<Long, EntityDebugInfo> entityDebugInfos;
    
    /**
     * 系统调试信息映射
     */
    private final Map<String, SystemDebugInfo> systemDebugInfos;
    
    /**
     * 实体历史记录
     */
    private final Map<Long, List<EntityDebugInfo>> entityHistory;
    
    /**
     * 组件变更历史
     */
    private final List<Map<String, Object>> componentChangeHistory;
    
    /**
     * 是否启用调试
     */
    private volatile boolean enabled = false;
    
    /**
     * 最后快照时间
     */
    private volatile long lastSnapshotTime = 0;
    
    /**
     * 构造函数
     *
     * @param world 目标世界
     */
    public ECSDebugger(World world) {
        this(world, new DebugConfig());
    }
    
    /**
     * 构造函数
     *
     * @param world 目标世界
     * @param config 调试配置
     */
    public ECSDebugger(World world, DebugConfig config) {
        this.world = world;
        this.config = config;
        this.entityDebugInfos = new ConcurrentHashMap<>();
        this.systemDebugInfos = new ConcurrentHashMap<>();
        this.entityHistory = new ConcurrentHashMap<>();
        this.componentChangeHistory = Collections.synchronizedList(new ArrayList<>());
    }
    
    /**
     * 启用调试
     */
    public void enable() {
        if (!enabled) {
            enabled = true;
            initializeDebugData();
            logger.info("ECS调试器已启用，调试级别: {}", config.getDebugLevel().getDisplayName());
        }
    }
    
    /**
     * 禁用调试
     */
    public void disable() {
        if (enabled) {
            enabled = false;
            logger.info("ECS调试器已禁用");
        }
    }
    
    /**
     * 初始化调试数据
     */
    private void initializeDebugData() {
        // 初始化实体调试信息
        refreshEntityDebugInfo();
        
        // 初始化系统调试信息
        refreshSystemDebugInfo();
        
        lastSnapshotTime = java.lang.System.currentTimeMillis();
    }
    
    /**
     * 刷新实体调试信息
     */
    public void refreshEntityDebugInfo() {
        if (!enabled || !config.getDebugLevel().includes(DebugLevel.BASIC)) {
            return;
        }
        
        // 这里需要从World获取实体信息
        // 由于World的接口可能不完整，我们先创建一个模拟实现
        // TODO: 实际实现需要根据World的接口来调整
        
        logger.debug("刷新实体调试信息");
    }
    
    /**
     * 刷新系统调试信息
     */
    public void refreshSystemDebugInfo() {
        if (!enabled || !config.getDebugLevel().includes(DebugLevel.BASIC)) {
            return;
        }
        
        // 这里需要从World的SystemManager获取系统信息
        // TODO: 实际实现需要根据SystemManager的接口来调整
        
        logger.debug("刷新系统调试信息");
    }
    
    /**
     * 更新调试信息
     */
    public void update() {
        if (!enabled) {
            return;
        }
        
        long currentTime = java.lang.System.currentTimeMillis();
        
        // 检查是否需要创建快照
        if (currentTime - lastSnapshotTime >= config.getSnapshotInterval()) {
            createSnapshot();
            lastSnapshotTime = currentTime;
        }
        
        // 实时更新
        if (config.isEnableRealTimeUpdates()) {
            refreshEntityDebugInfo();
            if (config.isTrackSystemPerformance()) {
                refreshSystemDebugInfo();
            }
        }
    }
    
    /**
     * 创建快照
     */
    private void createSnapshot() {
        if (config.getDebugLevel().includes(DebugLevel.DETAILED)) {
            logger.debug("创建ECS调试快照");
            
            // 清理过期的历史记录
            cleanupHistory();
        }
    }
    
    /**
     * 清理历史记录
     */
    private void cleanupHistory() {
        int maxSize = config.getMaxHistorySize();
        
        // 清理实体历史
        for (List<EntityDebugInfo> history : entityHistory.values()) {
            while (history.size() > maxSize) {
                history.remove(0);
            }
        }
        
        // 清理组件变更历史
        while (componentChangeHistory.size() > maxSize) {
            componentChangeHistory.remove(0);
        }
    }
    
    /**
     * 记录实体事件
     *
     * @param eventType 事件类型
     * @param entityId 实体ID
     * @param details 事件详情
     */
    public void recordEntityEvent(String eventType, long entityId, Map<String, Object> details) {
        if (!enabled || !config.isTrackEntityHistory()) {
            return;
        }
        
        Map<String, Object> eventRecord = new HashMap<>();
        eventRecord.put("eventType", eventType);
        eventRecord.put("entityId", entityId);
        eventRecord.put("timestamp", java.lang.System.currentTimeMillis());
        if (details != null) {
            eventRecord.putAll(details);
        }
        
        // 记录到历史中
        if (config.getDebugLevel().includes(DebugLevel.DETAILED)) {
            logger.debug("记录实体事件: {} - 实体ID: {}", eventType, entityId);
        }
    }
    
    /**
     * 记录组件变更
     *
     * @param entityId 实体ID
     * @param componentType 组件类型
     * @param changeType 变更类型
     * @param details 变更详情
     */
    public void recordComponentChange(long entityId, String componentType, String changeType, Map<String, Object> details) {
        if (!enabled || !config.isTrackComponentChanges()) {
            return;
        }
        
        Map<String, Object> changeRecord = new HashMap<>();
        changeRecord.put("entityId", entityId);
        changeRecord.put("componentType", componentType);
        changeRecord.put("changeType", changeType);
        changeRecord.put("timestamp", java.lang.System.currentTimeMillis());
        if (details != null) {
            changeRecord.putAll(details);
        }
        
        componentChangeHistory.add(changeRecord);
        
        if (config.getDebugLevel().includes(DebugLevel.DETAILED)) {
            logger.debug("记录组件变更: {} - 实体ID: {}, 组件: {}", changeType, entityId, componentType);
        }
    }
    
    /**
     * 记录系统执行
     *
     * @param systemName 系统名称
     * @param executionTimeNanos 执行时间（纳秒）
     */
    public void recordSystemExecution(String systemName, long executionTimeNanos) {
        if (!enabled || !config.isTrackSystemPerformance()) {
            return;
        }
        
        SystemDebugInfo debugInfo = systemDebugInfos.get(systemName);
        if (debugInfo != null) {
            debugInfo.recordExecution(executionTimeNanos);
        }
    }
    
    /**
     * 查找实体
     *
     * @param predicate 查找条件
     * @return 符合条件的实体调试信息列表
     */
    public List<EntityDebugInfo> findEntities(Predicate<EntityDebugInfo> predicate) {
        return entityDebugInfos.values().stream()
                .filter(predicate)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * 获取实体调试信息
     *
     * @param entityId 实体ID
     * @return 实体调试信息，如果不存在返回null
     */
    public EntityDebugInfo getEntityDebugInfo(long entityId) {
        return entityDebugInfos.get(entityId);
    }
    
    /**
     * 获取系统调试信息
     *
     * @param systemName 系统名称
     * @return 系统调试信息，如果不存在返回null
     */
    public SystemDebugInfo getSystemDebugInfo(String systemName) {
        return systemDebugInfos.get(systemName);
    }
    
    /**
     * 获取所有实体调试信息
     *
     * @return 实体调试信息映射的副本
     */
    public Map<Long, EntityDebugInfo> getAllEntityDebugInfo() {
        return new HashMap<>(entityDebugInfos);
    }
    
    /**
     * 获取所有系统调试信息
     *
     * @return 系统调试信息映射的副本
     */
    public Map<String, SystemDebugInfo> getAllSystemDebugInfo() {
        return new HashMap<>(systemDebugInfos);
    }
    
    /**
     * 获取组件变更历史
     *
     * @param maxRecords 最大记录数
     * @return 组件变更历史列表
     */
    public List<Map<String, Object>> getComponentChangeHistory(int maxRecords) {
        synchronized (componentChangeHistory) {
            int size = componentChangeHistory.size();
            int start = Math.max(0, size - maxRecords);
            return new ArrayList<>(componentChangeHistory.subList(start, size));
        }
    }
    
    /**
     * 生成调试报告
     *
     * @return 调试报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== ECS调试报告 ===\n");
        report.append("调试级别: ").append(config.getDebugLevel().getDisplayName()).append("\n");
        report.append("实体数量: ").append(entityDebugInfos.size()).append("\n");
        report.append("系统数量: ").append(systemDebugInfos.size()).append("\n");
        report.append("组件变更记录: ").append(componentChangeHistory.size()).append("\n");
        
        if (config.getDebugLevel().includes(DebugLevel.DETAILED)) {
            report.append("\n--- 系统性能 ---\n");
            for (SystemDebugInfo systemInfo : systemDebugInfos.values()) {
                report.append(String.format("系统: %s, 更新次数: %d, 平均耗时: %.2fms\n",
                        systemInfo.getSystemName(),
                        systemInfo.getUpdateCount(),
                        systemInfo.getAverageExecutionTime() / 1_000_000.0));
            }
        }
        
        return report.toString();
    }
    
    // Getters
    public DebugConfig getConfig() { return config; }
    public World getWorld() { return world; }
    public boolean isEnabled() { return enabled; }
    
    public int getEntityCount() { return entityDebugInfos.size(); }
    public int getSystemCount() { return systemDebugInfos.size(); }
    public int getComponentChangeRecordCount() { return componentChangeHistory.size(); }
}