/*
 * 文件名: BaseRanking.java
 * 用途: 排行榜基础实现类
 * 实现内容:
 *   - 提供排行榜的通用CRUD操作实现
 *   - 封装Redis存储操作和缓存管理
 *   - 实现事件通知和日志记录
 *   - 提供模板方法供子类扩展
 * 技术选型:
 *   - 继承抽象Ranking类
 *   - 集成Redis存储和本地缓存
 *   - 支持异步操作和批量优化
 * 依赖关系:
 *   - 继承Ranking抽象类
 *   - 依赖RankingStorage存储接口
 *   - 被具体排行榜实现类继承
 */
package com.lx.gameserver.business.ranking.impl;

import com.lx.gameserver.business.ranking.core.Ranking;
import com.lx.gameserver.business.ranking.core.RankingEntry;
import com.lx.gameserver.business.ranking.core.RankingType;
import com.lx.gameserver.business.ranking.storage.RankingStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排行榜基础实现类
 * <p>
 * 提供排行榜的通用实现，包括基本的CRUD操作、缓存管理、
 * 事件通知等功能。具体的排行榜类型可以继承此类并重写
 * 相关方法以实现特定的业务逻辑。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public abstract class BaseRanking extends Ranking {

    @Autowired
    protected RankingStorage storage;

    /**
     * 本地缓存（避免频繁查询Redis）
     */
    private final Map<Long, CacheEntry> localCache = new ConcurrentHashMap<>();

    /**
     * 缓存过期时间（毫秒）
     */
    private long cacheExpireTime = 30000; // 30秒

    /**
     * 最大缓存大小
     */
    private int maxCacheSize = 1000;

    /**
     * 构造函数
     *
     * @param rankingId   排行榜ID
     * @param rankingName 排行榜名称
     * @param rankingType 排行榜类型
     */
    protected BaseRanking(String rankingId, String rankingName, RankingType rankingType) {
        super(rankingId, rankingName, rankingType);
    }

    // ===== 公共操作方法 =====

    /**
     * 提交分数
     *
     * @param entityId 实体ID
     * @param score    分数
     * @return 操作结果
     */
    public CompletableFuture<SubmitResult> submitScore(Long entityId, Long score) {
        return submitScore(entityId, score, null);
    }

    /**
     * 提交分数（带额外数据）
     *
     * @param entityId  实体ID
     * @param score     分数
     * @param extraData 额外数据
     * @return 操作结果
     */
    public CompletableFuture<SubmitResult> submitScore(Long entityId, Long score, Map<String, Object> extraData) {
        if (!isRunning() || !enabled) {
            return CompletableFuture.completedFuture(
                SubmitResult.failure("排行榜未启用或未运行"));
        }

        if (entityId == null || score == null) {
            return CompletableFuture.completedFuture(
                SubmitResult.failure("参数不能为空"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证实体是否有效
                Map<String, Object> entityData = getEntityInfo(entityId);
                if (!isEntityValid(entityId, entityData)) {
                    return SubmitResult.failure("实体无效");
                }

                // 计算最终分数
                Long finalScore = calculateScore(entityId, entityData);
                if (finalScore == null) {
                    finalScore = score;
                }

                // 获取旧的排行榜条目
                RankingEntry oldEntry = getCurrentEntry(entityId);

                // 更新分数
                boolean success = storage.setScore(rankingId, entityId, finalScore);
                if (!success) {
                    return SubmitResult.failure("存储更新失败");
                }

                // 获取新的排行榜条目
                RankingEntry newEntry = buildRankingEntry(entityId, finalScore, entityData);
                
                // 清除缓存
                clearEntityCache(entityId);

                // 触发排名变化事件
                onRankChanged(oldEntry, newEntry);

                // 更新统计
                updateLastUpdateTime();

                log.debug("分数提交成功: entityId={}, score={}, finalScore={}", 
                         entityId, score, finalScore);

                return SubmitResult.success("分数提交成功", newEntry);

            } catch (Exception e) {
                log.error("分数提交失败: entityId=" + entityId + ", score=" + score, e);
                return SubmitResult.failure("分数提交异常: " + e.getMessage());
            }
        });
    }

    /**
     * 增加分数
     *
     * @param entityId  实体ID
     * @param increment 增量
     * @return 操作结果
     */
    public CompletableFuture<SubmitResult> incrementScore(Long entityId, Long increment) {
        if (!isRunning() || !enabled) {
            return CompletableFuture.completedFuture(
                SubmitResult.failure("排行榜未启用或未运行"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取旧的排行榜条目
                RankingEntry oldEntry = getCurrentEntry(entityId);

                // 增加分数
                Long newScore = storage.incrementScore(rankingId, entityId, increment);
                if (newScore == null) {
                    return SubmitResult.failure("分数增加失败");
                }

                // 获取实体信息
                Map<String, Object> entityData = getEntityInfo(entityId);
                
                // 构建新的排行榜条目
                RankingEntry newEntry = buildRankingEntry(entityId, newScore, entityData);
                
                // 清除缓存
                clearEntityCache(entityId);

                // 触发排名变化事件
                onRankChanged(oldEntry, newEntry);

                // 更新统计
                updateLastUpdateTime();

                return SubmitResult.success("分数增加成功", newEntry);

            } catch (Exception e) {
                log.error("分数增加失败: entityId=" + entityId + ", increment=" + increment, e);
                return SubmitResult.failure("分数增加异常: " + e.getMessage());
            }
        });
    }

    /**
     * 获取前N名
     *
     * @param topN 前N名
     * @return 排行榜条目列表
     */
    public List<RankingEntry> getTopEntries(int topN) {
        if (!isRunning() || topN <= 0) {
            return Collections.emptyList();
        }

        try {
            List<RankingEntry> entries = storage.getTopEntries(rankingId, Math.min(topN, capacity));
            
            // 补充实体信息
            enrichEntries(entries);
            
            // 增加查询计数
            incrementQueryCount();
            
            return entries;
            
        } catch (Exception e) {
            log.error("获取前N名失败: topN=" + topN, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定范围的排名
     *
     * @param start 起始排名
     * @param end   结束排名
     * @return 排行榜条目列表
     */
    public List<RankingEntry> getRangeEntries(int start, int end) {
        if (!isRunning() || start <= 0 || end < start) {
            return Collections.emptyList();
        }

        try {
            List<RankingEntry> entries = storage.getRangeEntries(rankingId, start, end);
            
            // 补充实体信息
            enrichEntries(entries);
            
            // 增加查询计数
            incrementQueryCount();
            
            return entries;
            
        } catch (Exception e) {
            log.error("获取范围排名失败: start=" + start + ", end=" + end, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取实体排名信息
     *
     * @param entityId 实体ID
     * @return 排行榜条目
     */
    public RankingEntry getEntityRank(Long entityId) {
        if (!isRunning() || entityId == null) {
            return null;
        }

        // 先检查缓存
        CacheEntry cacheEntry = localCache.get(entityId);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            return cacheEntry.entry.copy();
        }

        try {
            Long score = storage.getScore(rankingId, entityId);
            if (score == null) {
                return null;
            }

            Integer rank = storage.getRank(rankingId, entityId);
            Map<String, Object> entityData = getEntityInfo(entityId);
            
            RankingEntry entry = buildRankingEntry(entityId, score, entityData);
            entry.setRank(rank);
            
            // 更新缓存
            updateCache(entityId, entry);
            
            // 增加查询计数
            incrementQueryCount();
            
            return entry;
            
        } catch (Exception e) {
            log.error("获取实体排名失败: entityId=" + entityId, e);
            return null;
        }
    }

    /**
     * 获取实体周围的排名
     *
     * @param entityId 实体ID
     * @param range    周围范围
     * @return 排行榜条目列表
     */
    public List<RankingEntry> getSurroundingEntries(Long entityId, int range) {
        if (!isRunning() || entityId == null || range <= 0) {
            return Collections.emptyList();
        }

        try {
            List<RankingEntry> entries = storage.getSurroundingEntries(rankingId, entityId, range);
            
            // 补充实体信息
            enrichEntries(entries);
            
            // 增加查询计数
            incrementQueryCount();
            
            return entries;
            
        } catch (Exception e) {
            log.error("获取周围排名失败: entityId=" + entityId + ", range=" + range, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取排行榜总数
     *
     * @return 总数
     */
    public long getCount() {
        if (!isRunning()) {
            return 0;
        }

        try {
            return storage.getCount(rankingId);
        } catch (Exception e) {
            log.error("获取排行榜总数失败", e);
            return 0;
        }
    }

    /**
     * 清空排行榜
     *
     * @return 是否成功
     */
    public boolean clear() {
        if (!isRunning()) {
            return false;
        }

        try {
            boolean success = storage.clear(rankingId);
            if (success) {
                // 清空本地缓存
                localCache.clear();
                log.info("排行榜清空成功: {}", rankingId);
            }
            return success;
        } catch (Exception e) {
            log.error("清空排行榜失败: " + rankingId, e);
            return false;
        }
    }

    // ===== 缓存管理 =====

    /**
     * 更新缓存
     */
    private void updateCache(Long entityId, RankingEntry entry) {
        if (localCache.size() >= maxCacheSize) {
            // 清理过期缓存
            cleanExpiredCache();
            
            // 如果还是满了，清理最老的缓存
            if (localCache.size() >= maxCacheSize) {
                clearOldestCache();
            }
        }

        localCache.put(entityId, new CacheEntry(entry.copy(), System.currentTimeMillis()));
    }

    /**
     * 清除实体缓存
     */
    private void clearEntityCache(Long entityId) {
        localCache.remove(entityId);
    }

    /**
     * 清理过期缓存
     */
    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        localCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > cacheExpireTime);
    }

    /**
     * 清理最老的缓存
     */
    private void clearOldestCache() {
        long oldestTime = Long.MAX_VALUE;
        Long oldestKey = null;
        
        for (Map.Entry<Long, CacheEntry> entry : localCache.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            localCache.remove(oldestKey);
        }
    }

    // ===== 工具方法 =====

    /**
     * 获取当前排行榜条目
     */
    private RankingEntry getCurrentEntry(Long entityId) {
        return getEntityRank(entityId);
    }

    /**
     * 构建排行榜条目
     */
    private RankingEntry buildRankingEntry(Long entityId, Long score, Map<String, Object> entityData) {
        RankingEntry entry = new RankingEntry(entityId, null, score);
        
        // 设置实体信息
        if (entityData != null) {
            entry.setEntityName((String) entityData.get("name"));
            entry.setEntityLevel((Integer) entityData.get("level"));
            entry.setEntityAvatar((String) entityData.get("avatar"));
            entry.setServerId((Integer) entityData.get("serverId"));
            entry.setServerName((String) entityData.get("serverName"));
        }
        
        entry.setUpdateTime(LocalDateTime.now());
        return entry;
    }

    /**
     * 补充条目信息
     */
    private void enrichEntries(List<RankingEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (RankingEntry entry : entries) {
            try {
                Map<String, Object> entityData = getEntityInfo(entry.getEntityId());
                if (entityData != null) {
                    entry.setEntityName((String) entityData.get("name"));
                    entry.setEntityLevel((Integer) entityData.get("level"));
                    entry.setEntityAvatar((String) entityData.get("avatar"));
                    entry.setServerId((Integer) entityData.get("serverId"));
                    entry.setServerName((String) entityData.get("serverName"));
                }
            } catch (Exception e) {
                log.warn("补充实体信息失败: entityId=" + entry.getEntityId(), e);
            }
        }
    }

    // ===== 抽象方法默认实现 =====

    @Override
    protected void doInitialize() {
        log.info("初始化排行榜: {}", rankingName);
        
        // 清理缓存
        localCache.clear();
        
        // 可以在这里进行排行榜特定的初始化逻辑
        onInitialized();
    }

    @Override
    protected void doDestroy() {
        log.info("销毁排行榜: {}", rankingName);
        
        // 清理缓存
        localCache.clear();
        
        // 可以在这里进行排行榜特定的清理逻辑
        onDestroyed();
    }

    @Override
    protected void onRankChanged(RankingEntry oldEntry, RankingEntry newEntry) {
        // 默认只记录日志，子类可以重写实现具体的事件处理
        if (oldEntry == null && newEntry != null) {
            log.debug("新上榜: entityId={}, rank={}, score={}", 
                     newEntry.getEntityId(), newEntry.getRank(), newEntry.getScore());
        } else if (oldEntry != null && newEntry != null) {
            log.debug("排名变化: entityId={}, oldRank={}, newRank={}, oldScore={}, newScore={}", 
                     newEntry.getEntityId(), 
                     oldEntry.getRank(), newEntry.getRank(),
                     oldEntry.getScore(), newEntry.getScore());
        }
        
        // 子类可以重写此方法处理排名变化事件
        handleRankChangeEvent(oldEntry, newEntry);
    }

    // ===== 扩展点方法 =====

    /**
     * 初始化完成回调
     * 子类可以重写此方法进行特定的初始化操作
     */
    protected void onInitialized() {
        // 默认空实现
    }

    /**
     * 销毁完成回调
     * 子类可以重写此方法进行特定的清理操作
     */
    protected void onDestroyed() {
        // 默认空实现
    }

    /**
     * 处理排名变化事件
     * 子类可以重写此方法处理特定的排名变化逻辑
     *
     * @param oldEntry 旧条目
     * @param newEntry 新条目
     */
    protected void handleRankChangeEvent(RankingEntry oldEntry, RankingEntry newEntry) {
        // 默认空实现
    }

    // ===== 内部类 =====

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final RankingEntry entry;
        final long timestamp;

        CacheEntry(RankingEntry entry, long timestamp) {
            this.entry = entry;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000; // 30秒过期
        }
    }

    /**
     * 提交结果
     */
    public static class SubmitResult {
        private final boolean success;
        private final String message;
        private final RankingEntry entry;

        private SubmitResult(boolean success, String message, RankingEntry entry) {
            this.success = success;
            this.message = message;
            this.entry = entry;
        }

        public static SubmitResult success(String message, RankingEntry entry) {
            return new SubmitResult(true, message, entry);
        }

        public static SubmitResult failure(String message) {
            return new SubmitResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public RankingEntry getEntry() { return entry; }
    }
}