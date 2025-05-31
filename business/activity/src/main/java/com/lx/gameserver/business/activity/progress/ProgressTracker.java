/*
 * 文件名: ProgressTracker.java
 * 用途: 进度追踪器
 * 实现内容:
 *   - 活动进度的初始化和管理
 *   - 进度更新和查询操作
 *   - 进度重置和批量操作
 *   - 进度变化事件发布
 * 技术选型:
 *   - Spring Service组件
 *   - 事务管理支持
 *   - 缓存集成
 *   - 异步事件处理
 * 依赖关系:
 *   - 使用ActivityProgress实体
 *   - 被活动服务调用
 *   - 集成存储层
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 进度追踪器
 * <p>
 * 负责玩家活动进度的全生命周期管理，包括初始化、更新、查询、重置等操作。
 * 支持批量操作、事件发布和缓存管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Service
public class ProgressTracker {
    
    // 注入的依赖（实际项目中需要注入）
    // @Autowired
    // private ActivityProgressRepository progressRepository;
    
    // @Autowired
    // private ApplicationEventPublisher eventPublisher;
    
    /** 内存缓存（生产环境建议使用Redis） */
    private final Map<String, ActivityProgress> progressCache = new ConcurrentHashMap<>();
    
    /** 读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /** 批量操作缓冲区 */
    private final Map<String, ProgressUpdateOperation> batchBuffer = new ConcurrentHashMap<>();
    
    /** 缓存配置 */
    private final CacheConfig cacheConfig = new CacheConfig();
    
    /**
     * 初始化玩家活动进度
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 初始化的进度对象
     */
    @Transactional
    public ActivityProgress initializeProgress(Long playerId, Long activityId) {
        if (playerId == null || activityId == null) {
            throw new IllegalArgumentException("玩家ID和活动ID不能为空");
        }
        
        String cacheKey = generateCacheKey(playerId, activityId);
        
        lock.writeLock().lock();
        try {
            // 检查是否已存在
            ActivityProgress existing = getProgress(playerId, activityId);
            if (existing != null) {
                log.debug("玩家活动进度已存在: playerId={}, activityId={}", playerId, activityId);
                return existing;
            }
            
            // 创建新的进度记录
            ActivityProgress progress = new ActivityProgress(playerId, activityId);
            
            // 保存到存储层（模拟）
            saveProgressToStorage(progress);
            
            // 添加到缓存
            progressCache.put(cacheKey, progress);
            
            // 发布初始化事件
            publishProgressEvent("progress.initialized", progress, null);
            
            log.info("成功初始化玩家活动进度: playerId={}, activityId={}", playerId, activityId);
            return progress;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 更新活动进度
     *
     * @param playerId    玩家ID
     * @param activityId  活动ID
     * @param progressKey 进度键
     * @param increment   增量
     * @return 更新结果
     */
    @Transactional
    public ProgressUpdateResult updateProgress(Long playerId, Long activityId, 
                                             String progressKey, Long increment) {
        return updateProgress(playerId, activityId, progressKey, increment, null);
    }
    
    /**
     * 更新活动进度（带额外数据）
     *
     * @param playerId    玩家ID
     * @param activityId  活动ID
     * @param progressKey 进度键
     * @param increment   增量
     * @param extraData   额外数据
     * @return 更新结果
     */
    @Transactional
    public ProgressUpdateResult updateProgress(Long playerId, Long activityId, 
                                             String progressKey, Long increment,
                                             Map<String, Object> extraData) {
        if (playerId == null || activityId == null || progressKey == null || increment == null) {
            return ProgressUpdateResult.failure("参数不能为空");
        }
        
        lock.writeLock().lock();
        try {
            // 获取或初始化进度
            ActivityProgress progress = getOrInitializeProgress(playerId, activityId);
            
            // 记录更新前的值
            Long oldValue = progress.getProgress(progressKey);
            
            // 更新进度
            Long newValue = progress.addProgress(progressKey, increment);
            
            // 处理额外数据
            if (extraData != null) {
                extraData.forEach(progress::setCustomData);
            }
            
            // 保存到存储层
            saveProgressToStorage(progress);
            
            // 更新缓存
            String cacheKey = generateCacheKey(playerId, activityId);
            progressCache.put(cacheKey, progress);
            
            // 创建更新结果
            ProgressUpdateResult result = new ProgressUpdateResult();
            result.success = true;
            result.oldValue = oldValue;
            result.newValue = newValue;
            result.increment = increment;
            result.progressKey = progressKey;
            
            // 发布更新事件
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("progressKey", progressKey);
            eventData.put("oldValue", oldValue);
            eventData.put("newValue", newValue);
            eventData.put("increment", increment);
            publishProgressEvent("progress.updated", progress, eventData);
            
            log.debug("成功更新活动进度: playerId={}, activityId={}, key={}, old={}, new={}", 
                    playerId, activityId, progressKey, oldValue, newValue);
            
            return result;
            
        } catch (Exception e) {
            log.error("更新活动进度失败: playerId={}, activityId={}, key={}", 
                    playerId, activityId, progressKey, e);
            return ProgressUpdateResult.failure("更新失败: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 查询活动进度
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 进度对象
     */
    public ActivityProgress getProgress(Long playerId, Long activityId) {
        if (playerId == null || activityId == null) {
            return null;
        }
        
        String cacheKey = generateCacheKey(playerId, activityId);
        
        lock.readLock().lock();
        try {
            // 先从缓存获取
            ActivityProgress cached = progressCache.get(cacheKey);
            if (cached != null) {
                return cached.clone(); // 返回副本
            }
            
            // 从存储层加载
            ActivityProgress progress = loadProgressFromStorage(playerId, activityId);
            if (progress != null) {
                // 添加到缓存
                progressCache.put(cacheKey, progress);
                return progress.clone();
            }
            
            return null;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 批量查询活动进度
     *
     * @param playerId    玩家ID
     * @param activityIds 活动ID列表
     * @return 进度映射
     */
    public Map<Long, ActivityProgress> getBatchProgress(Long playerId, List<Long> activityIds) {
        if (playerId == null || activityIds == null || activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<Long, ActivityProgress> result = new HashMap<>();
        
        for (Long activityId : activityIds) {
            ActivityProgress progress = getProgress(playerId, activityId);
            if (progress != null) {
                result.put(activityId, progress);
            }
        }
        
        return result;
    }
    
    /**
     * 重置活动进度
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 重置是否成功
     */
    @Transactional
    public boolean resetProgress(Long playerId, Long activityId) {
        if (playerId == null || activityId == null) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            ActivityProgress progress = getProgress(playerId, activityId);
            if (progress == null) {
                log.warn("要重置的进度不存在: playerId={}, activityId={}", playerId, activityId);
                return false;
            }
            
            // 备份重置前的数据
            ActivityProgress backup = progress.clone();
            
            // 执行重置
            progress.resetProgress();
            
            // 保存到存储层
            saveProgressToStorage(progress);
            
            // 更新缓存
            String cacheKey = generateCacheKey(playerId, activityId);
            progressCache.put(cacheKey, progress);
            
            // 发布重置事件
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("backup", backup);
            publishProgressEvent("progress.reset", progress, eventData);
            
            log.info("成功重置活动进度: playerId={}, activityId={}", playerId, activityId);
            return true;
            
        } catch (Exception e) {
            log.error("重置活动进度失败: playerId={}, activityId={}", playerId, activityId, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 批量重置活动进度
     *
     * @param playerIds   玩家ID列表
     * @param activityIds 活动ID列表
     * @return 重置结果统计
     */
    @Transactional
    public BatchOperationResult batchResetProgress(List<Long> playerIds, List<Long> activityIds) {
        if (playerIds == null || activityIds == null || playerIds.isEmpty() || activityIds.isEmpty()) {
            return new BatchOperationResult(0, 0, "参数不能为空");
        }
        
        BatchOperationResult result = new BatchOperationResult();
        
        for (Long playerId : playerIds) {
            for (Long activityId : activityIds) {
                try {
                    if (resetProgress(playerId, activityId)) {
                        result.successCount++;
                    } else {
                        result.failureCount++;
                    }
                } catch (Exception e) {
                    result.failureCount++;
                    log.error("批量重置进度失败: playerId={}, activityId={}", playerId, activityId, e);
                }
            }
        }
        
        log.info("批量重置活动进度完成: 成功={}, 失败={}", result.successCount, result.failureCount);
        return result;
    }
    
    /**
     * 获取玩家所有活动进度
     *
     * @param playerId 玩家ID
     * @return 进度列表
     */
    public List<ActivityProgress> getPlayerAllProgress(Long playerId) {
        if (playerId == null) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            // 从缓存获取
            List<ActivityProgress> cached = progressCache.values().stream()
                    .filter(progress -> playerId.equals(progress.getPlayerId()))
                    .map(ActivityProgress::clone)
                    .collect(Collectors.toList());
            
            if (!cached.isEmpty()) {
                return cached;
            }
            
            // 从存储层加载
            return loadPlayerProgressFromStorage(playerId);
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 清理过期进度缓存
     */
    public void cleanExpiredCache() {
        if (!cacheConfig.enableExpire) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();
            long expireThreshold = currentTime - cacheConfig.expireTimeMs;
            
            progressCache.entrySet().removeIf(entry -> {
                ActivityProgress progress = entry.getValue();
                return progress.getUpdateTime().isBefore(
                        LocalDateTime.now().minusSeconds(cacheConfig.expireTimeMs / 1000));
            });
            
            log.debug("清理过期进度缓存完成，当前缓存大小: {}", progressCache.size());
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    public CacheStats getCacheStats() {
        lock.readLock().lock();
        try {
            CacheStats stats = new CacheStats();
            stats.cacheSize = progressCache.size();
            stats.maxCacheSize = cacheConfig.maxSize;
            stats.hitCount = 0; // 实际项目中需要统计
            stats.missCount = 0; // 实际项目中需要统计
            stats.hitRate = 0.0; // 实际项目中需要计算
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===== 私有方法 =====
    
    /**
     * 获取或初始化进度
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 进度对象
     */
    private ActivityProgress getOrInitializeProgress(Long playerId, Long activityId) {
        ActivityProgress progress = getProgress(playerId, activityId);
        if (progress == null) {
            progress = initializeProgress(playerId, activityId);
        }
        return progress;
    }
    
    /**
     * 生成缓存键
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 缓存键
     */
    private String generateCacheKey(Long playerId, Long activityId) {
        return String.format("progress:%d:%d", playerId, activityId);
    }
    
    /**
     * 保存进度到存储层（模拟实现）
     *
     * @param progress 进度对象
     */
    private void saveProgressToStorage(ActivityProgress progress) {
        // 实际项目中这里应该调用Repository保存到数据库
        log.debug("保存进度到存储层: playerId={}, activityId={}", 
                progress.getPlayerId(), progress.getActivityId());
    }
    
    /**
     * 从存储层加载进度（模拟实现）
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 进度对象
     */
    private ActivityProgress loadProgressFromStorage(Long playerId, Long activityId) {
        // 实际项目中这里应该调用Repository从数据库加载
        log.debug("从存储层加载进度: playerId={}, activityId={}", playerId, activityId);
        return null;
    }
    
    /**
     * 从存储层加载玩家所有进度（模拟实现）
     *
     * @param playerId 玩家ID
     * @return 进度列表
     */
    private List<ActivityProgress> loadPlayerProgressFromStorage(Long playerId) {
        // 实际项目中这里应该调用Repository从数据库加载
        log.debug("从存储层加载玩家所有进度: playerId={}", playerId);
        return Collections.emptyList();
    }
    
    /**
     * 发布进度事件
     *
     * @param eventType 事件类型
     * @param progress  进度对象
     * @param eventData 事件数据
     */
    private void publishProgressEvent(String eventType, ActivityProgress progress, Map<String, Object> eventData) {
        try {
            // 实际项目中这里应该发布Spring事件
            log.debug("发布进度事件: type={}, playerId={}, activityId={}", 
                    eventType, progress.getPlayerId(), progress.getActivityId());
        } catch (Exception e) {
            log.error("发布进度事件失败: type={}", eventType, e);
        }
    }
    
    /**
     * 进度更新结果
     */
    public static class ProgressUpdateResult {
        public boolean success;
        public String message;
        public String progressKey;
        public Long oldValue;
        public Long newValue;
        public Long increment;
        
        public static ProgressUpdateResult failure(String message) {
            ProgressUpdateResult result = new ProgressUpdateResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }
    
    /**
     * 批量操作结果
     */
    public static class BatchOperationResult {
        public int successCount;
        public int failureCount;
        public String message;
        
        public BatchOperationResult() {}
        
        public BatchOperationResult(int successCount, int failureCount, String message) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        public int cacheSize;
        public int maxCacheSize;
        public long hitCount;
        public long missCount;
        public double hitRate;
    }
    
    /**
     * 缓存配置
     */
    private static class CacheConfig {
        boolean enableExpire = true;
        long expireTimeMs = 30 * 60 * 1000; // 30分钟
        int maxSize = 10000;
    }
    
    /**
     * 进度更新操作
     */
    private static class ProgressUpdateOperation {
        Long playerId;
        Long activityId;
        String progressKey;
        Long increment;
        Map<String, Object> extraData;
        long timestamp;
    }
}