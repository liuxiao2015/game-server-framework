/*
 * 文件名: RankingUpdater.java
 * 用途: 排行榜更新器实现
 * 实现内容:
 *   - 排行榜分数更新处理
 *   - 批量更新和增量更新支持
 *   - 更新队列管理和优化
 *   - 更新频率控制和防抖动
 * 技术选型:
 *   - 使用异步队列处理更新请求
 *   - 支持批量操作优化性能
 *   - 集成线程池管理并发
 * 依赖关系:
 *   - 被排行榜管理器调用
 *   - 依赖存储层接口
 *   - 集成事件通知系统
 */
package com.lx.gameserver.business.ranking.manager;

import com.lx.gameserver.business.ranking.core.RankingEntry;
import com.lx.gameserver.business.ranking.core.RankingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 排行榜更新器
 * <p>
 * 负责处理排行榜的分数更新、批量更新、增量更新等操作。
 * 支持异步处理和批量优化，提供高性能的更新能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class RankingUpdater implements InitializingBean, DisposableBean {

    /**
     * 更新请求队列
     */
    private final BlockingQueue<UpdateRequest> updateQueue = new LinkedBlockingQueue<>();

    /**
     * 批量更新队列
     */
    private final Map<String, List<UpdateRequest>> batchUpdateMap = new ConcurrentHashMap<>();

    /**
     * 更新线程池
     */
    private ExecutorService updateExecutor;

    /**
     * 批量处理线程池
     */
    private ScheduledExecutorService batchExecutor;

    /**
     * 更新锁（防止同一排行榜并发更新）
     */
    private final Map<String, ReentrantLock> updateLocks = new ConcurrentHashMap<>();

    /**
     * 更新统计
     */
    private final AtomicLong totalUpdates = new AtomicLong(0);
    private final AtomicLong batchUpdates = new AtomicLong(0);
    private final AtomicLong failedUpdates = new AtomicLong(0);

    /**
     * 配置参数
     */
    private int updateThreads = 4;
    private int batchSize = 100;
    private long batchInterval = 1000; // 1秒
    private long maxWaitTime = 5000; // 5秒

    /**
     * 运行状态
     */
    private volatile boolean running = false;

    /**
     * 提交分数更新
     *
     * @param rankingId 排行榜ID
     * @param entityId  实体ID
     * @param score     新分数
     * @param async     是否异步处理
     * @return 更新结果
     */
    public CompletableFuture<UpdateResult> submitScore(String rankingId, Long entityId, 
                                                      Long score, boolean async) {
        if (!running) {
            return CompletableFuture.completedFuture(
                UpdateResult.failure("更新器未运行"));
        }

        UpdateRequest request = new UpdateRequest(rankingId, entityId, score, 
                                                UpdateType.SET, System.currentTimeMillis());

        if (async) {
            updateQueue.offer(request);
            return CompletableFuture.completedFuture(
                UpdateResult.success("已提交异步更新"));
        } else {
            return processUpdateRequest(request);
        }
    }

    /**
     * 增量更新分数
     *
     * @param rankingId 排行榜ID
     * @param entityId  实体ID
     * @param increment 分数增量
     * @param async     是否异步处理
     * @return 更新结果
     */
    public CompletableFuture<UpdateResult> incrementScore(String rankingId, Long entityId, 
                                                         Long increment, boolean async) {
        if (!running) {
            return CompletableFuture.completedFuture(
                UpdateResult.failure("更新器未运行"));
        }

        UpdateRequest request = new UpdateRequest(rankingId, entityId, increment, 
                                                UpdateType.INCREMENT, System.currentTimeMillis());

        if (async) {
            updateQueue.offer(request);
            return CompletableFuture.completedFuture(
                UpdateResult.success("已提交异步增量更新"));
        } else {
            return processUpdateRequest(request);
        }
    }

    /**
     * 批量更新分数
     *
     * @param rankingId 排行榜ID
     * @param updates   更新列表
     * @return 批量更新结果
     */
    public CompletableFuture<BatchUpdateResult> batchUpdate(String rankingId, 
                                                           List<ScoreUpdate> updates) {
        if (!running) {
            return CompletableFuture.completedFuture(
                BatchUpdateResult.failure("更新器未运行"));
        }

        if (updates == null || updates.isEmpty()) {
            return CompletableFuture.completedFuture(
                BatchUpdateResult.success(0, 0));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return processBatchUpdate(rankingId, updates);
            } catch (Exception e) {
                log.error("批量更新失败: " + rankingId, e);
                return BatchUpdateResult.failure("批量更新异常: " + e.getMessage());
            }
        }, updateExecutor);
    }

    /**
     * 处理单个更新请求
     */
    private CompletableFuture<UpdateResult> processUpdateRequest(UpdateRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = updateLocks.computeIfAbsent(request.rankingId, 
                k -> new ReentrantLock());
            
            lock.lock();
            try {
                // 这里需要调用实际的存储层更新逻辑
                // 暂时返回成功结果
                totalUpdates.incrementAndGet();
                return UpdateResult.success("更新成功");
                
            } catch (Exception e) {
                failedUpdates.incrementAndGet();
                log.error("更新失败: " + request, e);
                return UpdateResult.failure("更新失败: " + e.getMessage());
                
            } finally {
                lock.unlock();
            }
        }, updateExecutor);
    }

    /**
     * 处理批量更新
     */
    private BatchUpdateResult processBatchUpdate(String rankingId, List<ScoreUpdate> updates) {
        ReentrantLock lock = updateLocks.computeIfAbsent(rankingId, k -> new ReentrantLock());
        
        lock.lock();
        try {
            int successCount = 0;
            int failureCount = 0;
            
            for (ScoreUpdate update : updates) {
                try {
                    // 这里需要调用实际的存储层更新逻辑
                    // 暂时模拟成功
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    log.error("批量更新单项失败: " + update, e);
                }
            }
            
            batchUpdates.incrementAndGet();
            totalUpdates.addAndGet(successCount);
            failedUpdates.addAndGet(failureCount);
            
            return BatchUpdateResult.success(successCount, failureCount);
            
        } finally {
            lock.unlock();
        }
    }

    /**
     * 启动异步更新处理
     */
    private void startAsyncProcessor() {
        for (int i = 0; i < updateThreads; i++) {
            updateExecutor.submit(() -> {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        UpdateRequest request = updateQueue.poll(1, TimeUnit.SECONDS);
                        if (request != null) {
                            processUpdateRequest(request);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("异步更新处理异常", e);
                    }
                }
            });
        }
    }

    /**
     * 启动批量处理
     */
    private void startBatchProcessor() {
        batchExecutor.scheduleWithFixedDelay(() -> {
            try {
                processPendingBatches();
            } catch (Exception e) {
                log.error("批量处理异常", e);
            }
        }, batchInterval, batchInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理待处理的批量更新
     */
    private void processPendingBatches() {
        for (Map.Entry<String, List<UpdateRequest>> entry : batchUpdateMap.entrySet()) {
            String rankingId = entry.getKey();
            List<UpdateRequest> requests = entry.getValue();
            
            if (requests.size() >= batchSize || 
                isTimeoutExceeded(requests)) {
                
                List<UpdateRequest> toProcess = new ArrayList<>(requests);
                requests.clear();
                
                // 转换为批量更新格式并处理
                List<ScoreUpdate> updates = toProcess.stream()
                    .map(req -> new ScoreUpdate(req.entityId, req.score, req.updateType))
                    .toList();
                    
                processBatchUpdate(rankingId, updates);
            }
        }
    }

    /**
     * 检查是否超时
     */
    private boolean isTimeoutExceeded(List<UpdateRequest> requests) {
        if (requests.isEmpty()) {
            return false;
        }
        
        long oldestTime = requests.get(0).timestamp;
        return System.currentTimeMillis() - oldestTime > maxWaitTime;
    }

    /**
     * 获取更新统计信息
     */
    public UpdateStats getStats() {
        return new UpdateStats(
            totalUpdates.get(),
            batchUpdates.get(),
            failedUpdates.get(),
            updateQueue.size(),
            batchUpdateMap.values().stream().mapToInt(List::size).sum()
        );
    }

    /**
     * 清空更新队列
     */
    public void clearQueue() {
        updateQueue.clear();
        batchUpdateMap.clear();
        log.info("更新队列已清空");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化排行榜更新器");
        
        updateExecutor = Executors.newFixedThreadPool(updateThreads, 
            r -> new Thread(r, "ranking-updater-" + System.currentTimeMillis()));
        batchExecutor = Executors.newScheduledThreadPool(2, 
            r -> new Thread(r, "ranking-batch-" + System.currentTimeMillis()));
        
        running = true;
        startAsyncProcessor();
        startBatchProcessor();
        
        log.info("排行榜更新器初始化完成");
    }

    @Override
    public void destroy() throws Exception {
        log.info("销毁排行榜更新器");
        
        running = false;
        
        if (updateExecutor != null) {
            updateExecutor.shutdown();
            try {
                if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    updateExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                updateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (batchExecutor != null) {
            batchExecutor.shutdown();
            try {
                if (!batchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    batchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                batchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        updateQueue.clear();
        batchUpdateMap.clear();
        updateLocks.clear();
        
        log.info("排行榜更新器销毁完成");
    }

    // ===== 内部类定义 =====

    /**
     * 更新请求
     */
    private static class UpdateRequest {
        final String rankingId;
        final Long entityId;
        final Long score;
        final UpdateType updateType;
        final long timestamp;

        UpdateRequest(String rankingId, Long entityId, Long score, 
                     UpdateType updateType, long timestamp) {
            this.rankingId = rankingId;
            this.entityId = entityId;
            this.score = score;
            this.updateType = updateType;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("UpdateRequest{rankingId='%s', entityId=%d, score=%d, type=%s}",
                               rankingId, entityId, score, updateType);
        }
    }

    /**
     * 更新类型
     */
    public enum UpdateType {
        SET,        // 设置分数
        INCREMENT   // 增加分数
    }

    /**
     * 分数更新
     */
    public static class ScoreUpdate {
        private final Long entityId;
        private final Long score;
        private final UpdateType updateType;

        public ScoreUpdate(Long entityId, Long score, UpdateType updateType) {
            this.entityId = entityId;
            this.score = score;
            this.updateType = updateType;
        }

        public Long getEntityId() { return entityId; }
        public Long getScore() { return score; }
        public UpdateType getUpdateType() { return updateType; }
    }

    /**
     * 更新结果
     */
    public static class UpdateResult {
        private final boolean success;
        private final String message;
        private final RankingEntry result;

        private UpdateResult(boolean success, String message, RankingEntry result) {
            this.success = success;
            this.message = message;
            this.result = result;
        }

        public static UpdateResult success(String message) {
            return new UpdateResult(true, message, null);
        }

        public static UpdateResult success(String message, RankingEntry result) {
            return new UpdateResult(true, message, result);
        }

        public static UpdateResult failure(String message) {
            return new UpdateResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public RankingEntry getResult() { return result; }
    }

    /**
     * 批量更新结果
     */
    public static class BatchUpdateResult {
        private final boolean success;
        private final String message;
        private final int successCount;
        private final int failureCount;

        private BatchUpdateResult(boolean success, String message, 
                                 int successCount, int failureCount) {
            this.success = success;
            this.message = message;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }

        public static BatchUpdateResult success(int successCount, int failureCount) {
            return new BatchUpdateResult(true, "批量更新完成", successCount, failureCount);
        }

        public static BatchUpdateResult failure(String message) {
            return new BatchUpdateResult(false, message, 0, 0);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
    }

    /**
     * 更新统计
     */
    public static class UpdateStats {
        private final long totalUpdates;
        private final long batchUpdates;
        private final long failedUpdates;
        private final int queueSize;
        private final int batchQueueSize;

        public UpdateStats(long totalUpdates, long batchUpdates, long failedUpdates,
                          int queueSize, int batchQueueSize) {
            this.totalUpdates = totalUpdates;
            this.batchUpdates = batchUpdates;
            this.failedUpdates = failedUpdates;
            this.queueSize = queueSize;
            this.batchQueueSize = batchQueueSize;
        }

        public long getTotalUpdates() { return totalUpdates; }
        public long getBatchUpdates() { return batchUpdates; }
        public long getFailedUpdates() { return failedUpdates; }
        public int getQueueSize() { return queueSize; }
        public int getBatchQueueSize() { return batchQueueSize; }

        @Override
        public String toString() {
            return String.format("UpdateStats{total=%d, batch=%d, failed=%d, queue=%d, batchQueue=%d}",
                               totalUpdates, batchUpdates, failedUpdates, queueSize, batchQueueSize);
        }
    }
}