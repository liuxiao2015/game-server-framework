/*
 * 文件名: IndexManager.java
 * 用途: 组件索引管理器
 * 实现内容:
 *   - 组件索引管理
 *   - 位图索引优化
 *   - 多条件索引
 *   - 索引更新策略
 *   - 索引重建机制
 * 技术选型:
 *   - 位图技术实现高效索引
 *   - 哈希表优化查找性能
 *   - 增量更新减少维护开销
 * 依赖关系:
 *   - 被EntityQuery使用
 *   - 优化组件查询性能
 *   - 与ComponentManager协同工作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.query;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 索引管理器
 * <p>
 * 负责管理组件的索引结构，提供高效的组件查询功能。
 * 使用位图和哈希表等数据结构优化查询性能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class IndexManager {
    
    private static final Logger logger = LoggerFactory.getLogger(IndexManager.class);
    
    /**
     * 索引类型枚举
     */
    public enum IndexType {
        /** 位图索引 */
        BITMAP("位图索引", "使用位图存储组件存在信息"),
        /** 哈希索引 */
        HASH("哈希索引", "使用哈希表存储组件映射"),
        /** 复合索引 */
        COMPOSITE("复合索引", "多个组件类型的组合索引"),
        /** 范围索引 */
        RANGE("范围索引", "支持范围查询的索引");
        
        private final String displayName;
        private final String description;
        
        IndexType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * 位图索引
     */
    private static class BitmapIndex {
        private final Class<? extends Component> componentType;
        private final BitSet bitSet;
        private final AtomicLong version = new AtomicLong(0);
        
        public BitmapIndex(Class<? extends Component> componentType) {
            this.componentType = componentType;
            this.bitSet = new BitSet();
        }
        
        public void set(long entityId, boolean value) {
            int bitIndex = (int) entityId;
            if (value) {
                bitSet.set(bitIndex);
            } else {
                bitSet.clear(bitIndex);
            }
            version.incrementAndGet();
        }
        
        public boolean get(long entityId) {
            return bitSet.get((int) entityId);
        }
        
        public BitSet getBitSet() {
            return (BitSet) bitSet.clone();
        }
        
        public long getVersion() {
            return version.get();
        }
        
        public void clear() {
            bitSet.clear();
            version.incrementAndGet();
        }
        
        public int cardinality() {
            return bitSet.cardinality();
        }
    }
    
    /**
     * 复合索引
     */
    private static class CompositeIndex {
        private final Set<Class<? extends Component>> componentTypes;
        private final Map<String, BitSet> indexMap;
        private final AtomicLong version = new AtomicLong(0);
        
        public CompositeIndex(Set<Class<? extends Component>> componentTypes) {
            this.componentTypes = Set.copyOf(componentTypes);
            this.indexMap = new ConcurrentHashMap<>();
        }
        
        public String generateKey(Map<Class<? extends Component>, Boolean> componentStates) {
            StringBuilder keyBuilder = new StringBuilder();
            for (Class<? extends Component> type : componentTypes) {
                Boolean state = componentStates.get(type);
                keyBuilder.append(state != null && state ? "1" : "0");
            }
            return keyBuilder.toString();
        }
        
        public BitSet getOrCreateBitSet(String key) {
            return indexMap.computeIfAbsent(key, k -> new BitSet());
        }
        
        public void updateEntity(long entityId, Map<Class<? extends Component>, Boolean> componentStates) {
            String key = generateKey(componentStates);
            BitSet bitSet = getOrCreateBitSet(key);
            bitSet.set((int) entityId);
            
            // 清理其他键中的该实体
            for (Map.Entry<String, BitSet> entry : indexMap.entrySet()) {
                if (!entry.getKey().equals(key)) {
                    entry.getValue().clear((int) entityId);
                }
            }
            
            version.incrementAndGet();
        }
        
        public BitSet query(String key) {
            BitSet result = indexMap.get(key);
            return result != null ? (BitSet) result.clone() : new BitSet();
        }
        
        public long getVersion() {
            return version.get();
        }
        
        public void clear() {
            indexMap.clear();
            version.incrementAndGet();
        }
    }
    
    /**
     * 索引统计信息
     */
    public static class IndexStatistics {
        private final AtomicLong queryCount = new AtomicLong(0);
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong updateCount = new AtomicLong(0);
        private final AtomicLong rebuildCount = new AtomicLong(0);
        private final AtomicLong totalQueryTime = new AtomicLong(0);
        
        public void recordQuery(long timeNanos, boolean hit) {
            queryCount.incrementAndGet();
            if (hit) {
                hitCount.incrementAndGet();
            }
            totalQueryTime.addAndGet(timeNanos);
        }
        
        public void recordUpdate() {
            updateCount.incrementAndGet();
        }
        
        public void recordRebuild() {
            rebuildCount.incrementAndGet();
        }
        
        public long getQueryCount() { return queryCount.get(); }
        public long getHitCount() { return hitCount.get(); }
        public long getUpdateCount() { return updateCount.get(); }
        public long getRebuildCount() { return rebuildCount.get(); }
        public long getAverageQueryTime() {
            long count = queryCount.get();
            return count > 0 ? totalQueryTime.get() / count : 0;
        }
        
        public double getHitRate() {
            long total = queryCount.get();
            return total > 0 ? (double) hitCount.get() / total : 0.0;
        }
    }
    
    /**
     * 单组件类型的位图索引
     */
    private final Map<Class<? extends Component>, BitmapIndex> bitmapIndexes;
    
    /**
     * 复合索引
     */
    private final Map<String, CompositeIndex> compositeIndexes;
    
    /**
     * 组件类型ID映射
     */
    private final Map<Class<? extends Component>, Integer> componentTypeIds;
    
    /**
     * 下一个组件类型ID
     */
    private final AtomicLong nextComponentTypeId = new AtomicLong(1);
    
    /**
     * 索引统计
     */
    private final IndexStatistics statistics;
    
    /**
     * 最大实体ID
     */
    private volatile long maxEntityId = 0;
    
    /**
     * 是否启用自动重建
     */
    private boolean autoRebuild = true;
    
    /**
     * 重建阈值
     */
    private int rebuildThreshold = 10000;
    
    /**
     * 构造函数
     */
    public IndexManager() {
        this.bitmapIndexes = new ConcurrentHashMap<>();
        this.compositeIndexes = new ConcurrentHashMap<>();
        this.componentTypeIds = new ConcurrentHashMap<>();
        this.statistics = new IndexStatistics();
    }
    
    /**
     * 注册组件类型
     *
     * @param componentType 组件类型
     * @return 组件类型ID
     */
    public int registerComponentType(Class<? extends Component> componentType) {
        return componentTypeIds.computeIfAbsent(componentType, 
                k -> (int) nextComponentTypeId.getAndIncrement());
    }
    
    /**
     * 获取组件类型ID
     *
     * @param componentType 组件类型
     * @return 组件类型ID，如果未注册返回-1
     */
    public int getComponentTypeId(Class<? extends Component> componentType) {
        return componentTypeIds.getOrDefault(componentType, -1);
    }
    
    /**
     * 创建单组件索引
     *
     * @param componentType 组件类型
     */
    public void createIndex(Class<? extends Component> componentType) {
        registerComponentType(componentType);
        bitmapIndexes.computeIfAbsent(componentType, BitmapIndex::new);
        
        logger.debug("创建组件索引: {}", componentType.getSimpleName());
    }
    
    /**
     * 创建复合索引
     *
     * @param componentTypes 组件类型集合
     * @return 复合索引键
     */
    public String createCompositeIndex(Set<Class<? extends Component>> componentTypes) {
        if (componentTypes.size() < 2) {
            throw new IllegalArgumentException("复合索引至少需要2个组件类型");
        }
        
        // 注册所有组件类型
        for (Class<? extends Component> type : componentTypes) {
            registerComponentType(type);
        }
        
        // 生成索引键
        List<String> typeNames = componentTypes.stream()
                .map(Class::getSimpleName)
                .sorted()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        String indexKey = String.join("_", typeNames);
        
        compositeIndexes.computeIfAbsent(indexKey, k -> new CompositeIndex(componentTypes));
        
        logger.debug("创建复合索引: {}", indexKey);
        return indexKey;
    }
    
    /**
     * 更新实体的组件索引
     *
     * @param entityId 实体ID
     * @param componentType 组件类型
     * @param hasComponent 是否拥有组件
     */
    public void updateEntityIndex(long entityId, Class<? extends Component> componentType, boolean hasComponent) {
        maxEntityId = Math.max(maxEntityId, entityId);
        
        // 更新单组件索引
        BitmapIndex bitmapIndex = bitmapIndexes.get(componentType);
        if (bitmapIndex != null) {
            bitmapIndex.set(entityId, hasComponent);
        }
        
        // 更新相关的复合索引
        updateCompositeIndexes(entityId);
        
        statistics.recordUpdate();
    }
    
    /**
     * 更新复合索引
     *
     * @param entityId 实体ID
     */
    private void updateCompositeIndexes(long entityId) {
        for (CompositeIndex compositeIndex : compositeIndexes.values()) {
            Map<Class<? extends Component>, Boolean> componentStates = new HashMap<>();
            
            for (Class<? extends Component> componentType : compositeIndex.componentTypes) {
                BitmapIndex bitmapIndex = bitmapIndexes.get(componentType);
                boolean hasComponent = bitmapIndex != null && bitmapIndex.get(entityId);
                componentStates.put(componentType, hasComponent);
            }
            
            compositeIndex.updateEntity(entityId, componentStates);
        }
    }
    
    /**
     * 查询拥有指定组件的实体
     *
     * @param componentType 组件类型
     * @return 实体ID的位图
     */
    public BitSet queryEntitiesWithComponent(Class<? extends Component> componentType) {
        long startTime = java.lang.System.nanoTime();
        
        try {
            BitmapIndex index = bitmapIndexes.get(componentType);
            if (index != null) {
                statistics.recordQuery(java.lang.System.nanoTime() - startTime, true);
                return index.getBitSet();
            } else {
                statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
                return new BitSet();
            }
        } catch (Exception e) {
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
            throw e;
        }
    }
    
    /**
     * 查询拥有所有指定组件的实体
     *
     * @param componentTypes 组件类型集合
     * @return 实体ID的位图
     */
    public BitSet queryEntitiesWithAllComponents(Set<Class<? extends Component>> componentTypes) {
        long startTime = java.lang.System.nanoTime();
        
        try {
            if (componentTypes.isEmpty()) {
                statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
                return new BitSet();
            }
            
            if (componentTypes.size() == 1) {
                Class<? extends Component> componentType = componentTypes.iterator().next();
                return queryEntitiesWithComponent(componentType);
            }
            
            // 尝试使用复合索引
            String indexKey = createCompositeIndexKey(componentTypes);
            CompositeIndex compositeIndex = compositeIndexes.get(indexKey);
            if (compositeIndex != null) {
                Map<Class<? extends Component>, Boolean> allTrue = new HashMap<>();
                for (Class<? extends Component> type : componentTypes) {
                    allTrue.put(type, true);
                }
                String queryKey = compositeIndex.generateKey(allTrue);
                statistics.recordQuery(java.lang.System.nanoTime() - startTime, true);
                return compositeIndex.query(queryKey);
            }
            
            // 回退到位图交集运算
            BitSet result = null;
            for (Class<? extends Component> componentType : componentTypes) {
                BitSet componentBitSet = queryEntitiesWithComponent(componentType);
                if (result == null) {
                    result = componentBitSet;
                } else {
                    result.and(componentBitSet);
                }
            }
            
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
            return result != null ? result : new BitSet();
        } catch (Exception e) {
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
            throw e;
        }
    }
    
    /**
     * 查询拥有任意指定组件的实体
     *
     * @param componentTypes 组件类型集合
     * @return 实体ID的位图
     */
    public BitSet queryEntitiesWithAnyComponent(Set<Class<? extends Component>> componentTypes) {
        long startTime = java.lang.System.nanoTime();
        
        try {
            if (componentTypes.isEmpty()) {
                statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
                return new BitSet();
            }
            
            BitSet result = new BitSet();
            for (Class<? extends Component> componentType : componentTypes) {
                BitSet componentBitSet = queryEntitiesWithComponent(componentType);
                result.or(componentBitSet);
            }
            
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, true);
            return result;
        } catch (Exception e) {
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
            throw e;
        }
    }
    
    /**
     * 查询不拥有指定组件的实体
     *
     * @param componentTypes 组件类型集合
     * @return 实体ID的位图
     */
    public BitSet queryEntitiesWithoutComponents(Set<Class<? extends Component>> componentTypes) {
        long startTime = java.lang.System.nanoTime();
        
        try {
            BitSet withComponents = queryEntitiesWithAnyComponent(componentTypes);
            
            // 创建全集位图（所有可能的实体ID）
            BitSet allEntities = new BitSet();
            allEntities.set(0, (int) maxEntityId + 1);
            
            // 计算补集
            allEntities.andNot(withComponents);
            
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, true);
            return allEntities;
        } catch (Exception e) {
            statistics.recordQuery(java.lang.System.nanoTime() - startTime, false);
            throw e;
        }
    }
    
    /**
     * 生成复合索引键
     */
    private String createCompositeIndexKey(Set<Class<? extends Component>> componentTypes) {
        List<String> typeNames = componentTypes.stream()
                .map(Class::getSimpleName)
                .sorted()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return String.join("_", typeNames);
    }
    
    /**
     * 移除实体的所有索引
     *
     * @param entityId 实体ID
     */
    public void removeEntityFromAllIndexes(long entityId) {
        // 从位图索引中移除
        for (BitmapIndex index : bitmapIndexes.values()) {
            index.set(entityId, false);
        }
        
        // 从复合索引中移除
        for (CompositeIndex compositeIndex : compositeIndexes.values()) {
            for (BitSet bitSet : compositeIndex.indexMap.values()) {
                bitSet.clear((int) entityId);
            }
            compositeIndex.version.incrementAndGet();
        }
        
        statistics.recordUpdate();
    }
    
    /**
     * 重建所有索引
     */
    public void rebuildAllIndexes() {
        logger.info("开始重建所有索引");
        
        // 清空所有索引
        for (BitmapIndex index : bitmapIndexes.values()) {
            index.clear();
        }
        
        for (CompositeIndex index : compositeIndexes.values()) {
            index.clear();
        }
        
        statistics.recordRebuild();
        
        logger.info("索引重建完成");
    }
    
    /**
     * 清理索引
     */
    public void clearIndexes() {
        bitmapIndexes.clear();
        compositeIndexes.clear();
        logger.info("清理所有索引");
    }
    
    /**
     * 获取索引信息
     *
     * @return 索引信息映射
     */
    public Map<String, Object> getIndexInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // 位图索引信息
        Map<String, Map<String, Object>> bitmapInfo = new HashMap<>();
        for (Map.Entry<Class<? extends Component>, BitmapIndex> entry : bitmapIndexes.entrySet()) {
            Map<String, Object> indexInfo = new HashMap<>();
            BitmapIndex index = entry.getValue();
            indexInfo.put("cardinality", index.cardinality());
            indexInfo.put("version", index.getVersion());
            bitmapInfo.put(entry.getKey().getSimpleName(), indexInfo);
        }
        info.put("bitmapIndexes", bitmapInfo);
        
        // 复合索引信息
        Map<String, Map<String, Object>> compositeInfo = new HashMap<>();
        for (Map.Entry<String, CompositeIndex> entry : compositeIndexes.entrySet()) {
            Map<String, Object> indexInfo = new HashMap<>();
            CompositeIndex index = entry.getValue();
            indexInfo.put("keyCount", index.indexMap.size());
            indexInfo.put("version", index.getVersion());
            indexInfo.put("componentTypes", index.componentTypes.stream()
                    .map(Class::getSimpleName)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
            compositeInfo.put(entry.getKey(), indexInfo);
        }
        info.put("compositeIndexes", compositeInfo);
        
        // 统计信息
        Map<String, Object> statsInfo = new HashMap<>();
        statsInfo.put("queryCount", statistics.getQueryCount());
        statsInfo.put("hitCount", statistics.getHitCount());
        statsInfo.put("hitRate", statistics.getHitRate());
        statsInfo.put("updateCount", statistics.getUpdateCount());
        statsInfo.put("rebuildCount", statistics.getRebuildCount());
        statsInfo.put("averageQueryTime", statistics.getAverageQueryTime());
        info.put("statistics", statsInfo);
        
        return info;
    }
    
    // Getters and Setters
    public IndexStatistics getStatistics() { return statistics; }
    
    public boolean isAutoRebuild() { return autoRebuild; }
    public void setAutoRebuild(boolean autoRebuild) { this.autoRebuild = autoRebuild; }
    
    public int getRebuildThreshold() { return rebuildThreshold; }
    public void setRebuildThreshold(int rebuildThreshold) { this.rebuildThreshold = Math.max(0, rebuildThreshold); }
    
    public long getMaxEntityId() { return maxEntityId; }
    
    public int getBitmapIndexCount() { return bitmapIndexes.size(); }
    public int getCompositeIndexCount() { return compositeIndexes.size(); }
    public int getRegisteredComponentTypeCount() { return componentTypeIds.size(); }
}