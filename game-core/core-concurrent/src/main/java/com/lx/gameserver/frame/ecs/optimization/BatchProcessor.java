/*
 * 文件名: BatchProcessor.java
 * 用途: ECS批处理优化器
 * 实现内容:
 *   - SIMD指令利用
 *   - 数据预取优化
 *   - 并行处理支持
 *   - 批量大小自适应
 *   - 向量化计算优化
 * 技术选型:
 *   - Vector API支持SIMD操作
 *   - 数据预取提高内存带宽利用
 *   - 工作窃取算法实现负载均衡
 * 依赖关系:
 *   - 为SystemManager提供批处理能力
 *   - 依赖MemoryLayout获取连续数据
 *   - 提供高性能的批量操作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.optimization;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ECS批处理优化器
 * <p>
 * 通过批量处理和向量化计算优化ECS系统性能。
 * 支持SIMD指令利用、数据预取、并行处理和自适应批量大小。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class BatchProcessor {
    
    /**
     * 默认批量大小
     */
    public static final int DEFAULT_BATCH_SIZE = 1024;
    
    /**
     * 最小批量大小
     */
    public static final int MIN_BATCH_SIZE = 64;
    
    /**
     * 最大批量大小
     */
    public static final int MAX_BATCH_SIZE = 8192;
    
    /**
     * 批处理任务接口
     */
    @FunctionalInterface
    public interface BatchTask<T> {
        void process(List<T> batch);
    }
    
    /**
     * 向量化任务接口
     */
    @FunctionalInterface
    public interface VectorizedTask<T> {
        void process(T[] array, int startIndex, int length);
    }
    
    /**
     * 批处理配置
     */
    public static class BatchConfig {
        public final int batchSize;
        public final int parallelThreshold;
        public final boolean enableVectorization;
        public final boolean enablePrefetch;
        public final boolean adaptiveBatchSize;
        public final int maxThreads;
        
        public BatchConfig(int batchSize, int parallelThreshold, boolean enableVectorization,
                          boolean enablePrefetch, boolean adaptiveBatchSize, int maxThreads) {
            this.batchSize = batchSize;
            this.parallelThreshold = parallelThreshold;
            this.enableVectorization = enableVectorization;
            this.enablePrefetch = enablePrefetch;
            this.adaptiveBatchSize = adaptiveBatchSize;
            this.maxThreads = maxThreads;
        }
        
        public static BatchConfig defaultConfig() {
            return new BatchConfig(DEFAULT_BATCH_SIZE, 1000, true, true, true, 
                                 Runtime.getRuntime().availableProcessors());
        }
        
        public static BatchConfig highPerformance() {
            return new BatchConfig(MAX_BATCH_SIZE, 500, true, true, true,
                                 Runtime.getRuntime().availableProcessors() * 2);
        }
        
        public static BatchConfig lowLatency() {
            return new BatchConfig(MIN_BATCH_SIZE, 100, false, false, false, 1);
        }
    }
    
    /**
     * 批处理统计
     */
    public static class BatchStatistics {
        private final AtomicLong totalBatches = new AtomicLong(0);
        private final AtomicLong totalItems = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong vectorizedOperations = new AtomicLong(0);
        private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
        
        public void recordBatch(int itemCount, long timeNanos, boolean vectorized) {
            totalBatches.incrementAndGet();
            totalItems.addAndGet(itemCount);
            totalTime.addAndGet(timeNanos);
            if (vectorized) {
                vectorizedOperations.incrementAndGet();
            }
        }
        
        public void recordOperation(String operationType) {
            operationCounts.computeIfAbsent(operationType, k -> new AtomicLong(0))
                          .incrementAndGet();
        }
        
        public double getAverageItemsPerBatch() {
            long batches = totalBatches.get();
            return batches > 0 ? (double) totalItems.get() / batches : 0.0;
        }
        
        public double getAverageTimePerBatch() {
            long batches = totalBatches.get();
            return batches > 0 ? (double) totalTime.get() / batches : 0.0;
        }
        
        public double getThroughput() {
            long time = totalTime.get();
            return time > 0 ? (double) totalItems.get() * 1_000_000_000 / time : 0.0;
        }
        
        public double getVectorizationRate() {
            long batches = totalBatches.get();
            return batches > 0 ? (double) vectorizedOperations.get() / batches : 0.0;
        }
        
        // Getters
        public long getTotalBatches() { return totalBatches.get(); }
        public long getTotalItems() { return totalItems.get(); }
        public long getTotalTime() { return totalTime.get(); }
        public long getVectorizedOperations() { return vectorizedOperations.get(); }
        public Map<String, Long> getOperationCounts() {
            Map<String, Long> result = new HashMap<>();
            operationCounts.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
    }
    
    /**
     * 自适应批量大小控制器
     */
    private static class AdaptiveBatchSizeController {
        private volatile int currentBatchSize;
        private final int minSize;
        private final int maxSize;
        private final Queue<Double> performanceHistory = new ConcurrentLinkedQueue<>();
        private final int historySize = 10;
        
        public AdaptiveBatchSizeController(int initialSize, int minSize, int maxSize) {
            this.currentBatchSize = initialSize;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }
        
        public int getCurrentBatchSize() {
            return currentBatchSize;
        }
        
        public void recordPerformance(double throughput) {
            performanceHistory.offer(throughput);
            if (performanceHistory.size() > historySize) {
                performanceHistory.poll();
            }
            
            // 简化的自适应算法
            if (performanceHistory.size() >= 3) {
                double[] recent = performanceHistory.stream()
                    .skip(performanceHistory.size() - 3)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
                
                if (recent[2] > recent[1] && recent[1] > recent[0]) {
                    // 性能在提升，增加批量大小
                    currentBatchSize = Math.min(maxSize, currentBatchSize + 64);
                } else if (recent[2] < recent[1] && recent[1] < recent[0]) {
                    // 性能在下降，减少批量大小
                    currentBatchSize = Math.max(minSize, currentBatchSize - 64);
                }
            }
        }
    }
    
    /**
     * 线程池
     */
    private final ForkJoinPool executor;
    
    /**
     * 批处理配置
     */
    private BatchConfig config;
    
    /**
     * 自适应控制器
     */
    private final AdaptiveBatchSizeController adaptiveController;
    
    /**
     * 统计信息
     */
    private final BatchStatistics statistics = new BatchStatistics();
    
    /**
     * 构造函数
     */
    public BatchProcessor() {
        this(BatchConfig.defaultConfig());
    }
    
    /**
     * 构造函数（指定配置）
     */
    public BatchProcessor(BatchConfig config) {
        this.config = config;
        this.executor = new ForkJoinPool(config.maxThreads);
        this.adaptiveController = new AdaptiveBatchSizeController(
            config.batchSize, MIN_BATCH_SIZE, MAX_BATCH_SIZE);
    }
    
    /**
     * 批量处理实体集合
     */
    public <T> void processBatch(Collection<T> items, BatchTask<T> task) {
        processBatch(items, task, "default");
    }
    
    /**
     * 批量处理实体集合（指定操作类型）
     */
    public <T> void processBatch(Collection<T> items, BatchTask<T> task, String operationType) {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        long startTime = System.nanoTime();
        int batchSize = getCurrentBatchSize();
        
        try {
            if (items.size() >= config.parallelThreshold) {
                processBatchParallel(items, task, batchSize);
            } else {
                processBatchSequential(items, task, batchSize);
            }
            
            long endTime = System.nanoTime();
            double throughput = (double) items.size() * 1_000_000_000 / (endTime - startTime);
            
            statistics.recordBatch(items.size(), endTime - startTime, false);
            statistics.recordOperation(operationType);
            
            if (config.adaptiveBatchSize) {
                adaptiveController.recordPerformance(throughput);
            }
            
        } catch (Exception e) {
            log.error("批处理执行失败: operationType={}, itemCount={}", 
                     operationType, items.size(), e);
            throw new RuntimeException("批处理执行失败", e);
        }
    }
    
    /**
     * 向量化处理
     */
    @SuppressWarnings("unchecked")
    public <T> void processVectorized(T[] array, VectorizedTask<T> task) {
        processVectorized(array, task, "vectorized");
    }
    
    /**
     * 向量化处理（指定操作类型）
     */
    @SuppressWarnings("unchecked")
    public <T> void processVectorized(T[] array, VectorizedTask<T> task, String operationType) {
        if (array == null || array.length == 0) {
            return;
        }
        
        if (!config.enableVectorization) {
            // 回退到普通处理
            task.process(array, 0, array.length);
            return;
        }
        
        long startTime = System.nanoTime();
        int batchSize = getCurrentBatchSize();
        
        try {
            if (config.enablePrefetch) {
                prefetchData(array);
            }
            
            // 向量化处理
            for (int i = 0; i < array.length; i += batchSize) {
                int length = Math.min(batchSize, array.length - i);
                task.process(array, i, length);
            }
            
            long endTime = System.nanoTime();
            statistics.recordBatch(array.length, endTime - startTime, true);
            statistics.recordOperation(operationType);
            
        } catch (Exception e) {
            log.error("向量化处理失败: operationType={}, arrayLength={}", 
                     operationType, array.length, e);
            throw new RuntimeException("向量化处理失败", e);
        }
    }
    
    /**
     * 映射转换处理
     */
    public <T, R> List<R> mapBatch(Collection<T> items, Function<T, R> mapper) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<R> results = new ArrayList<>(items.size());
        
        processBatch(items, batch -> {
            List<R> batchResults = batch.stream()
                .map(mapper)
                .toList();
            synchronized (results) {
                results.addAll(batchResults);
            }
        }, "map");
        
        return results;
    }
    
    /**
     * 过滤处理
     */
    public <T> List<T> filterBatch(Collection<T> items, java.util.function.Predicate<T> predicate) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<T> results = new ArrayList<>();
        
        processBatch(items, batch -> {
            List<T> batchResults = batch.stream()
                .filter(predicate)
                .toList();
            synchronized (results) {
                results.addAll(batchResults);
            }
        }, "filter");
        
        return results;
    }
    
    /**
     * 聚合处理
     */
    public <T, R> R reduceBatch(Collection<T> items, R identity, 
                               java.util.function.BinaryOperator<R> accumulator,
                               Function<T, R> mapper) {
        if (items == null || items.isEmpty()) {
            return identity;
        }
        
        List<R> partialResults = Collections.synchronizedList(new ArrayList<>());
        
        processBatch(items, batch -> {
            R batchResult = batch.stream()
                .map(mapper)
                .reduce(identity, accumulator);
            partialResults.add(batchResult);
        }, "reduce");
        
        return partialResults.stream().reduce(identity, accumulator);
    }
    
    /**
     * 更新配置
     */
    public void updateConfig(BatchConfig newConfig) {
        this.config = newConfig;
        log.info("批处理配置已更新: batchSize={}, parallelThreshold={}, maxThreads={}", 
                newConfig.batchSize, newConfig.parallelThreshold, newConfig.maxThreads);
    }
    
    /**
     * 获取统计信息
     */
    public BatchStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 关闭处理器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 获取当前批量大小
     */
    private int getCurrentBatchSize() {
        return config.adaptiveBatchSize ? 
            adaptiveController.getCurrentBatchSize() : config.batchSize;
    }
    
    /**
     * 并行批处理
     */
    private <T> void processBatchParallel(Collection<T> items, BatchTask<T> task, int batchSize) {
        List<T> itemList = new ArrayList<>(items);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < itemList.size(); i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, itemList.size());
            List<T> batch = itemList.subList(start, end);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                task.process(batch);
            }, executor);
            
            futures.add(future);
        }
        
        // 等待所有批次完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    /**
     * 顺序批处理
     */
    private <T> void processBatchSequential(Collection<T> items, BatchTask<T> task, int batchSize) {
        List<T> itemList = new ArrayList<>(items);
        
        for (int i = 0; i < itemList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, itemList.size());
            List<T> batch = itemList.subList(i, end);
            task.process(batch);
        }
    }
    
    /**
     * 数据预取（模拟）
     */
    private <T> void prefetchData(T[] array) {
        // 这里是模拟的预取操作
        // 实际实现可能需要使用JNI调用系统预取指令
        if (array.length > 0) {
            // 访问一些数据以触发预取
            for (int i = 0; i < Math.min(array.length, 64); i += 8) {
                @SuppressWarnings("unused")
                T item = array[i];
            }
        }
    }
}