/*
 * 文件名: MemoryLayout.java
 * 用途: ECS内存布局优化器
 * 实现内容:
 *   - 组件数据连续存储
 *   - 缓存行对齐
 *   - 热数据分离
 *   - 内存池管理
 *   - 数据局部性优化
 * 技术选型:
 *   - 数据导向设计(DOD)提高缓存性能
 *   - 内存对齐优化缓存命中率
 *   - 分块存储支持并行处理
 * 依赖关系:
 *   - 为ComponentManager提供内存优化
 *   - 被SystemScheduler用于数据访问优化
 *   - 提供高性能的数据存储布局
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.optimization;

import com.lx.gameserver.frame.ecs.core.Component;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECS内存布局优化器
 * <p>
 * 通过数据导向设计优化内存布局，提高缓存命中率和数据访问性能。
 * 支持组件数据的连续存储、缓存行对齐和热数据分离。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class MemoryLayout {
    
    /**
     * 默认缓存行大小（字节）
     */
    public static final int DEFAULT_CACHE_LINE_SIZE = 64;
    
    /**
     * 默认块大小
     */
    public static final int DEFAULT_CHUNK_SIZE = 1024;
    
    /**
     * 内存块定义
     */
    public static class MemoryChunk {
        private final ByteBuffer buffer;
        private final int capacity;
        private final int elementSize;
        private final Class<? extends Component> componentType;
        private int used;
        
        public MemoryChunk(Class<? extends Component> componentType, int elementSize, int capacity) {
            this.componentType = componentType;
            this.elementSize = elementSize;
            this.capacity = capacity;
            // 按缓存行对齐分配内存
            int totalSize = alignToCacheLine(elementSize * capacity);
            this.buffer = ByteBuffer.allocateDirect(totalSize);
            this.used = 0;
        }
        
        public boolean hasSpace() {
            return used < capacity;
        }
        
        public int allocate() {
            if (!hasSpace()) {
                return -1;
            }
            return used++;
        }
        
        public void deallocate(int index) {
            // 简化实现，实际应该维护空闲列表
            if (index == used - 1) {
                used--;
            }
        }
        
        public ByteBuffer getBuffer() {
            return buffer;
        }
        
        public int getElementSize() {
            return elementSize;
        }
        
        public int getUsed() {
            return used;
        }
        
        public int getCapacity() {
            return capacity;
        }
        
        public Class<? extends Component> getComponentType() {
            return componentType;
        }
    }
    
    /**
     * 内存池配置
     */
    public static class PoolConfig {
        public final int initialChunks;
        public final int maxChunks;
        public final int chunkSize;
        public final int cacheLineSize;
        public final boolean enableHotDataSeparation;
        
        public PoolConfig(int initialChunks, int maxChunks, int chunkSize, 
                         int cacheLineSize, boolean enableHotDataSeparation) {
            this.initialChunks = initialChunks;
            this.maxChunks = maxChunks;
            this.chunkSize = chunkSize;
            this.cacheLineSize = cacheLineSize;
            this.enableHotDataSeparation = enableHotDataSeparation;
        }
        
        public static PoolConfig defaultConfig() {
            return new PoolConfig(4, 64, DEFAULT_CHUNK_SIZE, 
                                DEFAULT_CACHE_LINE_SIZE, true);
        }
    }
    
    /**
     * 内存统计信息
     */
    public static class MemoryStatistics {
        private final AtomicLong totalAllocated = new AtomicLong(0);
        private final AtomicLong totalUsed = new AtomicLong(0);
        private final AtomicLong chunkCount = new AtomicLong(0);
        private final AtomicLong cacheHits = new AtomicLong(0);
        private final AtomicLong cacheMisses = new AtomicLong(0);
        
        public void recordAllocation(long bytes) {
            totalAllocated.addAndGet(bytes);
        }
        
        public void recordUsage(long bytes) {
            totalUsed.addAndGet(bytes);
        }
        
        public void recordChunkCreation() {
            chunkCount.incrementAndGet();
        }
        
        public void recordCacheHit() {
            cacheHits.incrementAndGet();
        }
        
        public void recordCacheMiss() {
            cacheMisses.incrementAndGet();
        }
        
        public double getUtilizationRate() {
            long allocated = totalAllocated.get();
            return allocated > 0 ? (double) totalUsed.get() / allocated : 0.0;
        }
        
        public double getCacheHitRate() {
            long total = cacheHits.get() + cacheMisses.get();
            return total > 0 ? (double) cacheHits.get() / total : 0.0;
        }
        
        // Getters
        public long getTotalAllocated() { return totalAllocated.get(); }
        public long getTotalUsed() { return totalUsed.get(); }
        public long getChunkCount() { return chunkCount.get(); }
        public long getCacheHits() { return cacheHits.get(); }
        public long getCacheMisses() { return cacheMisses.get(); }
    }
    
    /**
     * 组件内存池
     */
    private final Map<Class<? extends Component>, List<MemoryChunk>> componentPools = 
        new ConcurrentHashMap<>();
    
    /**
     * 池配置
     */
    private final Map<Class<? extends Component>, PoolConfig> poolConfigs = 
        new ConcurrentHashMap<>();
    
    /**
     * 热数据存储
     */
    private final Map<Class<? extends Component>, MemoryChunk> hotDataChunks = 
        new ConcurrentHashMap<>();
    
    /**
     * 冷数据存储
     */
    private final Map<Class<? extends Component>, List<MemoryChunk>> coldDataChunks = 
        new ConcurrentHashMap<>();
    
    /**
     * 内存统计
     */
    private final MemoryStatistics statistics = new MemoryStatistics();
    
    /**
     * 默认配置
     */
    private PoolConfig defaultConfig = PoolConfig.defaultConfig();
    
    /**
     * 配置组件内存池
     */
    public void configurePool(Class<? extends Component> componentType, PoolConfig config) {
        poolConfigs.put(componentType, config);
        initializePool(componentType, config);
    }
    
    /**
     * 分配组件内存
     */
    public MemoryAllocation allocate(Class<? extends Component> componentType, boolean isHotData) {
        PoolConfig config = poolConfigs.getOrDefault(componentType, defaultConfig);
        
        if (config.enableHotDataSeparation && isHotData) {
            return allocateFromHotData(componentType, config);
        } else {
            return allocateFromColdData(componentType, config);
        }
    }
    
    /**
     * 释放组件内存
     */
    public void deallocate(MemoryAllocation allocation) {
        if (allocation == null) {
            return;
        }
        
        MemoryChunk chunk = allocation.getChunk();
        chunk.deallocate(allocation.getIndex());
        
        statistics.recordUsage(-allocation.getSize());
        
        log.debug("内存释放: componentType={}, index={}, size={}", 
                 chunk.getComponentType().getSimpleName(), 
                 allocation.getIndex(), allocation.getSize());
    }
    
    /**
     * 重新整理内存（碎片整理）
     */
    public void defragment(Class<? extends Component> componentType) {
        List<MemoryChunk> chunks = componentPools.get(componentType);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        
        long startTime = System.nanoTime();
        
        // 简化的碎片整理实现
        chunks.removeIf(chunk -> chunk.getUsed() == 0);
        
        log.info("内存碎片整理完成: componentType={}, time={}ns", 
                componentType.getSimpleName(), System.nanoTime() - startTime);
    }
    
    /**
     * 获取内存统计
     */
    public MemoryStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 获取组件内存使用情况
     */
    public Map<Class<? extends Component>, ComponentMemoryInfo> getComponentMemoryInfo() {
        Map<Class<? extends Component>, ComponentMemoryInfo> info = new HashMap<>();
        
        for (Map.Entry<Class<? extends Component>, List<MemoryChunk>> entry : 
             componentPools.entrySet()) {
            
            Class<? extends Component> componentType = entry.getKey();
            List<MemoryChunk> chunks = entry.getValue();
            
            int totalCapacity = chunks.stream().mapToInt(MemoryChunk::getCapacity).sum();
            int totalUsed = chunks.stream().mapToInt(MemoryChunk::getUsed).sum();
            long totalBytes = chunks.stream().mapToLong(c -> c.getCapacity() * c.getElementSize()).sum();
            
            info.put(componentType, new ComponentMemoryInfo(
                componentType, chunks.size(), totalCapacity, totalUsed, totalBytes
            ));
        }
        
        return info;
    }
    
    /**
     * 预热内存池
     */
    public void preWarm() {
        for (Map.Entry<Class<? extends Component>, PoolConfig> entry : poolConfigs.entrySet()) {
            initializePool(entry.getKey(), entry.getValue());
        }
        log.info("内存池预热完成");
    }
    
    /**
     * 清理内存池
     */
    public void cleanup() {
        componentPools.clear();
        hotDataChunks.clear();
        coldDataChunks.clear();
        log.info("内存池已清理");
    }
    
    /**
     * 设置默认配置
     */
    public void setDefaultConfig(PoolConfig config) {
        this.defaultConfig = config;
    }
    
    /**
     * 初始化内存池
     */
    private void initializePool(Class<? extends Component> componentType, PoolConfig config) {
        List<MemoryChunk> chunks = new ArrayList<>();
        int elementSize = estimateComponentSize(componentType);
        
        for (int i = 0; i < config.initialChunks; i++) {
            MemoryChunk chunk = new MemoryChunk(componentType, elementSize, config.chunkSize);
            chunks.add(chunk);
            statistics.recordAllocation(elementSize * config.chunkSize);
            statistics.recordChunkCreation();
        }
        
        componentPools.put(componentType, chunks);
        
        if (config.enableHotDataSeparation) {
            MemoryChunk hotChunk = new MemoryChunk(componentType, elementSize, config.chunkSize / 4);
            hotDataChunks.put(componentType, hotChunk);
            statistics.recordAllocation(elementSize * config.chunkSize / 4);
            statistics.recordChunkCreation();
        }
        
        log.info("内存池初始化完成: componentType={}, chunks={}, elementSize={}", 
                componentType.getSimpleName(), config.initialChunks, elementSize);
    }
    
    /**
     * 从热数据区分配
     */
    private MemoryAllocation allocateFromHotData(Class<? extends Component> componentType, 
                                                PoolConfig config) {
        MemoryChunk hotChunk = hotDataChunks.get(componentType);
        if (hotChunk != null && hotChunk.hasSpace()) {
            int index = hotChunk.allocate();
            statistics.recordUsage(hotChunk.getElementSize());
            statistics.recordCacheHit();
            return new MemoryAllocation(hotChunk, index, hotChunk.getElementSize());
        }
        
        // 热数据区满了，从冷数据区分配
        statistics.recordCacheMiss();
        return allocateFromColdData(componentType, config);
    }
    
    /**
     * 从冷数据区分配
     */
    private MemoryAllocation allocateFromColdData(Class<? extends Component> componentType, 
                                                 PoolConfig config) {
        List<MemoryChunk> chunks = componentPools.get(componentType);
        if (chunks == null) {
            initializePool(componentType, config);
            chunks = componentPools.get(componentType);
        }
        
        // 寻找有空间的块
        for (MemoryChunk chunk : chunks) {
            if (chunk.hasSpace()) {
                int index = chunk.allocate();
                statistics.recordUsage(chunk.getElementSize());
                return new MemoryAllocation(chunk, index, chunk.getElementSize());
            }
        }
        
        // 所有块都满了，创建新块
        if (chunks.size() < config.maxChunks) {
            int elementSize = estimateComponentSize(componentType);
            MemoryChunk newChunk = new MemoryChunk(componentType, elementSize, config.chunkSize);
            chunks.add(newChunk);
            statistics.recordAllocation(elementSize * config.chunkSize);
            statistics.recordChunkCreation();
            
            int index = newChunk.allocate();
            statistics.recordUsage(newChunk.getElementSize());
            return new MemoryAllocation(newChunk, index, newChunk.getElementSize());
        }
        
        throw new OutOfMemoryError("无法为组件分配内存: " + componentType.getSimpleName());
    }
    
    /**
     * 估算组件大小
     */
    private int estimateComponentSize(Class<? extends Component> componentType) {
        // 简化实现，实际应该通过反射分析字段大小
        return alignToCacheLine(64); // 假设平均组件大小为64字节
    }
    
    /**
     * 缓存行对齐
     */
    private static int alignToCacheLine(int size) {
        return ((size + DEFAULT_CACHE_LINE_SIZE - 1) / DEFAULT_CACHE_LINE_SIZE) * DEFAULT_CACHE_LINE_SIZE;
    }
    
    /**
     * 内存分配结果
     */
    public static class MemoryAllocation {
        private final MemoryChunk chunk;
        private final int index;
        private final int size;
        
        public MemoryAllocation(MemoryChunk chunk, int index, int size) {
            this.chunk = chunk;
            this.index = index;
            this.size = size;
        }
        
        public MemoryChunk getChunk() { return chunk; }
        public int getIndex() { return index; }
        public int getSize() { return size; }
    }
    
    /**
     * 组件内存信息
     */
    public static class ComponentMemoryInfo {
        public final Class<? extends Component> componentType;
        public final int chunkCount;
        public final int totalCapacity;
        public final int totalUsed;
        public final long totalBytes;
        
        public ComponentMemoryInfo(Class<? extends Component> componentType, 
                                  int chunkCount, int totalCapacity, 
                                  int totalUsed, long totalBytes) {
            this.componentType = componentType;
            this.chunkCount = chunkCount;
            this.totalCapacity = totalCapacity;
            this.totalUsed = totalUsed;
            this.totalBytes = totalBytes;
        }
        
        public double getUtilizationRate() {
            return totalCapacity > 0 ? (double) totalUsed / totalCapacity : 0.0;
        }
    }
}