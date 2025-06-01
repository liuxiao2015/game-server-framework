/*
 * 文件名: BatchOperationUtil.java
 * 用途: 批量操作工具类
 * 实现内容:
 *   - 大批量数据分批处理
 *   - 支持并行处理提高效率
 *   - 自动处理批次失败
 *   - 进度跟踪和日志
 * 技术选型:
 *   - 分批处理算法
 *   - CompletableFuture并行处理
 *   - 回调函数模式
 * 依赖关系:
 *   - 被业务代码使用
 *   - 提高批量操作性能
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 批量操作工具类
 * <p>
 * 提供高效的批量数据处理功能，支持分批、并行、重试等特性。
 * 适用于大批量数据的插入、更新、删除等操作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class BatchOperationUtil {

    private static final Logger logger = LoggerFactory.getLogger(BatchOperationUtil.class);

    /**
     * 默认批次大小
     */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    /**
     * 默认线程池大小
     */
    private static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * 默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 线程池
     */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            DEFAULT_THREAD_POOL_SIZE, 
            r -> {
                Thread thread = new Thread(r, "batch-operation-" + System.currentTimeMillis());
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 批量处理数据
     *
     * @param dataList 数据列表
     * @param processor 批次处理器
     * @param batchSize 批次大小
     * @param <T> 数据类型
     * @return 处理结果
     */
    public static <T> BatchResult<T> batchProcess(List<T> dataList, 
                                                 Function<List<T>, Integer> processor, 
                                                 int batchSize) {
        return batchProcess(dataList, processor, batchSize, false, 0, null);
    }

    /**
     * 批量处理数据（支持并行）
     *
     * @param dataList 数据列表
     * @param processor 批次处理器
     * @param batchSize 批次大小
     * @param parallel 是否并行处理
     * @param <T> 数据类型
     * @return 处理结果
     */
    public static <T> BatchResult<T> batchProcess(List<T> dataList, 
                                                 Function<List<T>, Integer> processor, 
                                                 int batchSize, 
                                                 boolean parallel) {
        return batchProcess(dataList, processor, batchSize, parallel, 0, null);
    }

    /**
     * 批量处理数据（完整版本）
     *
     * @param dataList 数据列表
     * @param processor 批次处理器
     * @param batchSize 批次大小
     * @param parallel 是否并行处理
     * @param retryCount 重试次数
     * @param progressCallback 进度回调
     * @param <T> 数据类型
     * @return 处理结果
     */
    public static <T> BatchResult<T> batchProcess(List<T> dataList, 
                                                 Function<List<T>, Integer> processor, 
                                                 int batchSize, 
                                                 boolean parallel,
                                                 int retryCount,
                                                 Consumer<BatchProgress> progressCallback) {
        
        if (dataList == null || dataList.isEmpty()) {
            return new BatchResult<>(0, 0, 0, Collections.emptyList(), 0);
        }

        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }

        long startTime = System.currentTimeMillis();
        List<List<T>> batches = splitIntoBatches(dataList, batchSize);
        BatchResult<T> result = new BatchResult<>(batches.size(), 0, 0, new ArrayList<>(), 0);

        logger.info("开始批量处理: 总数据={}, 批次数={}, 批次大小={}, 并行={}", 
                dataList.size(), batches.size(), batchSize, parallel);

        if (parallel) {
            result = processInParallel(batches, processor, retryCount, progressCallback);
        } else {
            result = processSequentially(batches, processor, retryCount, progressCallback);
        }

        long endTime = System.currentTimeMillis();
        result.executionTime = endTime - startTime;

        logger.info("批量处理完成: 成功批次={}, 失败批次={}, 处理记录数={}, 耗时={}ms", 
                result.successBatches, result.failedBatchCount, result.processedRecords, result.executionTime);

        return result;
    }

    /**
     * 顺序处理批次
     */
    private static <T> BatchResult<T> processSequentially(List<List<T>> batches, 
                                                         Function<List<T>, Integer> processor,
                                                         int retryCount,
                                                         Consumer<BatchProgress> progressCallback) {
        
        BatchResult<T> result = new BatchResult<>(batches.size(), 0, 0, new ArrayList<>(), 0);
        
        for (int i = 0; i < batches.size(); i++) {
            List<T> batch = batches.get(i);
            BatchProgress progress = new BatchProgress(i + 1, batches.size(), batch.size());
            
            try {
                int processedCount = processBatchWithRetry(batch, processor, retryCount);
                result.successBatches++;
                result.processedRecords += processedCount;
                progress.success = true;
                progress.processedCount = processedCount;
                
            } catch (Exception e) {
                result.failedBatchCount++;
                result.failedBatches.add(new BatchError<>(i, batch, e));
                progress.success = false;
                progress.error = e;
                logger.error("批次{}处理失败: {}", i, e.getMessage(), e);
            }
            
            if (progressCallback != null) {
                progressCallback.accept(progress);
            }
        }
        
        return result;
    }

    /**
     * 并行处理批次
     */
    private static <T> BatchResult<T> processInParallel(List<List<T>> batches, 
                                                       Function<List<T>, Integer> processor,
                                                       int retryCount,
                                                       Consumer<BatchProgress> progressCallback) {
        
        BatchResult<T> result = new BatchResult<>(batches.size(), 0, 0, new ArrayList<>(), 0);
        List<CompletableFuture<BatchProcessResult<T>>> futures = new ArrayList<>();
        
        // 提交所有批次任务
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            final List<T> batch = batches.get(i);
            
            CompletableFuture<BatchProcessResult<T>> future = CompletableFuture.supplyAsync(() -> {
                BatchProgress progress = new BatchProgress(batchIndex + 1, batches.size(), batch.size());
                
                try {
                    int processedCount = processBatchWithRetry(batch, processor, retryCount);
                    progress.success = true;
                    progress.processedCount = processedCount;
                    
                    if (progressCallback != null) {
                        progressCallback.accept(progress);
                    }
                    
                    return new BatchProcessResult<>(batchIndex, true, processedCount, null, null);
                    
                } catch (Exception e) {
                    progress.success = false;
                    progress.error = e;
                    logger.error("批次{}处理失败: {}", batchIndex, e.getMessage(), e);
                    
                    if (progressCallback != null) {
                        progressCallback.accept(progress);
                    }
                    
                    return new BatchProcessResult<>(batchIndex, false, 0, batch, e);
                }
            }, EXECUTOR);
            
            futures.add(future);
        }
        
        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // 收集结果
            for (CompletableFuture<BatchProcessResult<T>> future : futures) {
                BatchProcessResult<T> batchResult = future.get();
                if (batchResult.success) {
                    result.successBatches++;
                    result.processedRecords += batchResult.processedCount;
                } else {
                    result.failedBatchCount++;
                    result.failedBatches.add(new BatchError<>(batchResult.batchIndex, batchResult.batch, batchResult.error));
                }
            }
            
        } catch (TimeoutException e) {
            logger.error("批量处理超时", e);
            throw new RuntimeException("批量处理超时", e);
        } catch (Exception e) {
            logger.error("并行处理异常", e);
            throw new RuntimeException("并行处理异常", e);
        }
        
        return result;
    }

    /**
     * 带重试的批次处理
     */
    private static <T> int processBatchWithRetry(List<T> batch, 
                                               Function<List<T>, Integer> processor, 
                                               int retryCount) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                return processor.apply(batch);
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryCount) {
                    logger.warn("批次处理失败，第{}次重试: {}", attempt + 1, e.getMessage());
                    try {
                        Thread.sleep(1000 * (attempt + 1)); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("批次处理失败，已重试" + retryCount + "次", lastException);
    }

    /**
     * 将数据列表分割为批次
     */
    private static <T> List<List<T>> splitIntoBatches(List<T> dataList, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        
        for (int i = 0; i < dataList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, dataList.size());
            batches.add(new ArrayList<>(dataList.subList(i, endIndex)));
        }
        
        return batches;
    }

    /**
     * 批量插入工具方法
     *
     * @param dataList 数据列表
     * @param insertFunction 插入函数
     * @param <T> 数据类型
     * @return 处理结果
     */
    public static <T> BatchResult<T> batchInsert(List<T> dataList, 
                                               Function<List<T>, Integer> insertFunction) {
        return batchProcess(dataList, insertFunction, DEFAULT_BATCH_SIZE, true, 2, null);
    }

    /**
     * 批量更新工具方法
     *
     * @param dataList 数据列表
     * @param updateFunction 更新函数
     * @param <T> 数据类型
     * @return 处理结果
     */
    public static <T> BatchResult<T> batchUpdate(List<T> dataList, 
                                               Function<List<T>, Integer> updateFunction) {
        return batchProcess(dataList, updateFunction, DEFAULT_BATCH_SIZE, true, 2, null);
    }

    /**
     * 批量删除工具方法
     *
     * @param dataList 数据列表
     * @param deleteFunction 删除函数
     * @param <T> 数据类型
     * @return 处理结果
     */
    public static <T> BatchResult<T> batchDelete(List<T> dataList, 
                                               Function<List<T>, Integer> deleteFunction) {
        return batchProcess(dataList, deleteFunction, DEFAULT_BATCH_SIZE, false, 1, null);
    }

    /**
     * 批量处理结果
     */
    public static class BatchResult<T> {
        public final int totalBatches;
        public int successBatches;
        public int failedBatchCount;
        public final List<BatchError<T>> failedBatches;
        public int processedRecords;
        public long executionTime;

        public BatchResult(int totalBatches, int successBatches, int failedBatchCount, 
                          List<BatchError<T>> failedBatches, int processedRecords) {
            this.totalBatches = totalBatches;
            this.successBatches = successBatches;
            this.failedBatchCount = failedBatchCount;
            this.failedBatches = failedBatches;
            this.processedRecords = processedRecords;
        }

        public boolean isAllSuccess() {
            return failedBatchCount == 0;
        }

        public double getSuccessRate() {
            return totalBatches > 0 ? (double) successBatches / totalBatches : 0.0;
        }

        @Override
        public String toString() {
            return "BatchResult{" +
                    "totalBatches=" + totalBatches +
                    ", successBatches=" + successBatches +
                    ", failedBatchCount=" + failedBatchCount +
                    ", processedRecords=" + processedRecords +
                    ", executionTime=" + executionTime +
                    ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
                    '}';
        }
    }

    /**
     * 批次错误信息
     */
    public static class BatchError<T> {
        public final int batchIndex;
        public final List<T> batch;
        public final Exception error;

        public BatchError(int batchIndex, List<T> batch, Exception error) {
            this.batchIndex = batchIndex;
            this.batch = batch;
            this.error = error;
        }

        @Override
        public String toString() {
            return "BatchError{" +
                    "batchIndex=" + batchIndex +
                    ", batchSize=" + (batch != null ? batch.size() : 0) +
                    ", error=" + error.getMessage() +
                    '}';
        }
    }

    /**
     * 批次进度信息
     */
    public static class BatchProgress {
        public final int currentBatch;
        public final int totalBatches;
        public final int batchSize;
        public boolean success;
        public int processedCount;
        public Exception error;

        public BatchProgress(int currentBatch, int totalBatches, int batchSize) {
            this.currentBatch = currentBatch;
            this.totalBatches = totalBatches;
            this.batchSize = batchSize;
        }

        public double getProgressPercentage() {
            return totalBatches > 0 ? (double) currentBatch / totalBatches * 100 : 0.0;
        }

        @Override
        public String toString() {
            return "BatchProgress{" +
                    "currentBatch=" + currentBatch +
                    ", totalBatches=" + totalBatches +
                    ", batchSize=" + batchSize +
                    ", success=" + success +
                    ", processedCount=" + processedCount +
                    ", progress=" + String.format("%.1f%%", getProgressPercentage()) +
                    '}';
        }
    }

    /**
     * 批次处理结果（内部使用）
     */
    private static class BatchProcessResult<T> {
        public final int batchIndex;
        public final boolean success;
        public final int processedCount;
        public final List<T> batch;
        public final Exception error;

        public BatchProcessResult(int batchIndex, boolean success, int processedCount, 
                                List<T> batch, Exception error) {
            this.batchIndex = batchIndex;
            this.success = success;
            this.processedCount = processedCount;
            this.batch = batch;
            this.error = error;
        }
    }
}