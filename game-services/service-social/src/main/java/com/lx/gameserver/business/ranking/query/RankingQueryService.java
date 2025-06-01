/*
 * 文件名: RankingQueryService.java
 * 用途: 排行榜查询服务实现
 * 实现内容:
 *   - 提供统一的排行榜查询接口
 *   - 支持多种查询方式和过滤条件
 *   - 集成缓存优化查询性能
 *   - 提供分页和聚合查询功能
 * 技术选型:
 *   - 使用Spring Service架构
 *   - 集成多级缓存策略
 *   - 支持异步查询操作
 * 依赖关系:
 *   - 依赖排行榜管理器
 *   - 被API控制器调用
 *   - 集成缓存管理组件
 */
package com.lx.gameserver.business.ranking.query;

import com.lx.gameserver.business.ranking.core.Ranking;
import com.lx.gameserver.business.ranking.core.RankingEntry;
import com.lx.gameserver.business.ranking.core.RankingType;
import com.lx.gameserver.business.ranking.core.RankingScope;
import com.lx.gameserver.business.ranking.manager.RankingManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 排行榜查询服务
 * <p>
 * 提供统一的排行榜查询接口，支持多种查询方式、过滤条件
 * 和性能优化。封装复杂的查询逻辑，为上层提供简洁的API。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class RankingQueryService {

    @Autowired
    private RankingManager rankingManager;

    /**
     * 查询结果缓存
     */
    private final Map<String, CachedResult> queryCache = new ConcurrentHashMap<>();

    /**
     * 缓存过期时间（毫秒）
     */
    private long cacheExpireTime = 60000; // 1分钟

    /**
     * 最大缓存大小
     */
    private int maxCacheSize = 500;

    // ===== 基础查询接口 =====

    /**
     * 获取排行榜前N名
     *
     * @param rankingId 排行榜ID
     * @param topN      前N名
     * @return 排行榜条目列表
     */
    public List<RankingEntry> getTopEntries(String rankingId, int topN) {
        if (rankingId == null || topN <= 0) {
            return Collections.emptyList();
        }

        try {
            Ranking ranking = rankingManager.getRanking(rankingId);
            if (ranking == null) {
                log.warn("排行榜不存在: {}", rankingId);
                return Collections.emptyList();
            }

            // 检查缓存
            String cacheKey = buildCacheKey("top", rankingId, String.valueOf(topN));
            CachedResult cached = getFromCache(cacheKey);
            if (cached != null) {
                return cached.getEntries();
            }

            // 查询数据
            List<RankingEntry> entries = ranking.getTopEntries(topN);
            
            // 缓存结果
            putToCache(cacheKey, entries);
            
            return entries;
            
        } catch (Exception e) {
            log.error("获取前N名失败: rankingId=" + rankingId + ", topN=" + topN, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定范围的排名
     *
     * @param rankingId 排行榜ID
     * @param start     起始排名
     * @param end       结束排名
     * @return 排行榜条目列表
     */
    public List<RankingEntry> getRangeEntries(String rankingId, int start, int end) {
        if (rankingId == null || start <= 0 || end < start) {
            return Collections.emptyList();
        }

        try {
            Ranking ranking = rankingManager.getRanking(rankingId);
            if (ranking == null) {
                log.warn("排行榜不存在: {}", rankingId);
                return Collections.emptyList();
            }

            // 检查缓存
            String cacheKey = buildCacheKey("range", rankingId, start + "-" + end);
            CachedResult cached = getFromCache(cacheKey);
            if (cached != null) {
                return cached.getEntries();
            }

            // 查询数据
            List<RankingEntry> entries = ranking.getRangeEntries(start, end);
            
            // 缓存结果
            putToCache(cacheKey, entries);
            
            return entries;
            
        } catch (Exception e) {
            log.error("获取范围排名失败: rankingId=" + rankingId + ", start=" + start + ", end=" + end, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取实体排名信息
     *
     * @param rankingId 排行榜ID
     * @param entityId  实体ID
     * @return 排行榜条目
     */
    public RankingEntry getEntityRank(String rankingId, Long entityId) {
        if (rankingId == null || entityId == null) {
            return null;
        }

        try {
            Ranking ranking = rankingManager.getRanking(rankingId);
            if (ranking == null) {
                log.warn("排行榜不存在: {}", rankingId);
                return null;
            }

            return ranking.getEntityRank(entityId);
            
        } catch (Exception e) {
            log.error("获取实体排名失败: rankingId=" + rankingId + ", entityId=" + entityId, e);
            return null;
        }
    }

    /**
     * 获取实体周围的排名
     *
     * @param rankingId 排行榜ID
     * @param entityId  实体ID
     * @param range     周围范围
     * @return 排行榜条目列表
     */
    public List<RankingEntry> getSurroundingEntries(String rankingId, Long entityId, int range) {
        if (rankingId == null || entityId == null || range <= 0) {
            return Collections.emptyList();
        }

        try {
            Ranking ranking = rankingManager.getRanking(rankingId);
            if (ranking == null) {
                log.warn("排行榜不存在: {}", rankingId);
                return Collections.emptyList();
            }

            return ranking.getSurroundingEntries(entityId, range);
            
        } catch (Exception e) {
            log.error("获取周围排名失败: rankingId=" + rankingId + ", entityId=" + entityId + ", range=" + range, e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量获取实体排名
     *
     * @param rankingId 排行榜ID
     * @param entityIds 实体ID集合
     * @return 排名映射
     */
    public Map<Long, RankingEntry> batchGetEntityRanks(String rankingId, Set<Long> entityIds) {
        Map<Long, RankingEntry> results = new HashMap<>();
        
        if (rankingId == null || entityIds == null || entityIds.isEmpty()) {
            return results;
        }

        try {
            Ranking ranking = rankingManager.getRanking(rankingId);
            if (ranking == null) {
                log.warn("排行榜不存在: {}", rankingId);
                return results;
            }

            for (Long entityId : entityIds) {
                try {
                    RankingEntry entry = ranking.getEntityRank(entityId);
                    if (entry != null) {
                        results.put(entityId, entry);
                    }
                } catch (Exception e) {
                    log.warn("获取单个实体排名失败: entityId=" + entityId, e);
                }
            }
            
        } catch (Exception e) {
            log.error("批量获取实体排名失败: rankingId=" + rankingId, e);
        }

        return results;
    }

    // ===== 高级查询接口 =====

    /**
     * 分页获取排行榜
     *
     * @param rankingId 排行榜ID
     * @param pageNum   页码（从1开始）
     * @param pageSize  每页大小
     * @return 分页结果
     */
    public PageResult<RankingEntry> getPagedEntries(String rankingId, int pageNum, int pageSize) {
        if (rankingId == null || pageNum <= 0 || pageSize <= 0) {
            return PageResult.empty();
        }

        try {
            int start = (pageNum - 1) * pageSize + 1;
            int end = pageNum * pageSize;
            
            List<RankingEntry> entries = getRangeEntries(rankingId, start, end);
            
            // 获取总数
            Ranking ranking = rankingManager.getRanking(rankingId);
            long total = ranking != null ? ranking.getCount() : 0;
            
            return new PageResult<>(entries, pageNum, pageSize, total);
            
        } catch (Exception e) {
            log.error("分页获取排行榜失败: rankingId=" + rankingId + ", pageNum=" + pageNum + ", pageSize=" + pageSize, e);
            return PageResult.empty();
        }
    }

    /**
     * 根据类型获取排行榜列表
     *
     * @param rankingType 排行榜类型
     * @param topN        每个排行榜的前N名
     * @return 排行榜映射
     */
    public Map<String, List<RankingEntry>> getRankingsByType(RankingType rankingType, int topN) {
        Map<String, List<RankingEntry>> results = new HashMap<>();
        
        if (rankingType == null || topN <= 0) {
            return results;
        }

        try {
            List<Ranking> rankings = rankingManager.getRankingsByType(rankingType);
            
            for (Ranking ranking : rankings) {
                if (ranking.isRunning() && ranking.isEnabled()) {
                    List<RankingEntry> entries = getTopEntries(ranking.getRankingId(), topN);
                    results.put(ranking.getRankingId(), entries);
                }
            }
            
        } catch (Exception e) {
            log.error("根据类型获取排行榜失败: rankingType=" + rankingType, e);
        }

        return results;
    }

    /**
     * 根据范围获取排行榜列表
     *
     * @param rankingScope 排行榜范围
     * @param topN         每个排行榜的前N名
     * @return 排行榜映射
     */
    public Map<String, List<RankingEntry>> getRankingsByScope(RankingScope rankingScope, int topN) {
        Map<String, List<RankingEntry>> results = new HashMap<>();
        
        if (rankingScope == null || topN <= 0) {
            return results;
        }

        try {
            List<Ranking> rankings = rankingManager.getRankingsByScope(rankingScope);
            
            for (Ranking ranking : rankings) {
                if (ranking.isRunning() && ranking.isEnabled()) {
                    List<RankingEntry> entries = getTopEntries(ranking.getRankingId(), topN);
                    results.put(ranking.getRankingId(), entries);
                }
            }
            
        } catch (Exception e) {
            log.error("根据范围获取排行榜失败: rankingScope=" + rankingScope, e);
        }

        return results;
    }

    /**
     * 搜索实体排名
     *
     * @param rankingId  排行榜ID
     * @param entityName 实体名称（模糊搜索）
     * @param maxResults 最大结果数
     * @return 排行榜条目列表
     */
    public List<RankingEntry> searchEntities(String rankingId, String entityName, int maxResults) {
        if (rankingId == null || entityName == null || entityName.trim().isEmpty() || maxResults <= 0) {
            return Collections.emptyList();
        }

        try {
            // 获取较多的数据进行过滤（简化实现，实际应该在存储层优化）
            List<RankingEntry> allEntries = getTopEntries(rankingId, Math.min(maxResults * 10, 1000));
            
            String searchName = entityName.trim().toLowerCase();
            
            return allEntries.stream()
                .filter(entry -> entry.getEntityName() != null && 
                               entry.getEntityName().toLowerCase().contains(searchName))
                .limit(maxResults)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("搜索实体排名失败: rankingId=" + rankingId + ", entityName=" + entityName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取排行榜统计信息
     *
     * @param rankingId 排行榜ID
     * @return 统计信息
     */
    public RankingStatistics getRankingStatistics(String rankingId) {
        if (rankingId == null) {
            return null;
        }

        try {
            Ranking ranking = rankingManager.getRanking(rankingId);
            if (ranking == null) {
                return null;
            }

            long totalCount = ranking.getCount();
            long updateCount = ranking.getUpdateCount();
            long queryCount = ranking.getQueryCount();
            
            // 获取前几名的分数信息
            List<RankingEntry> topEntries = getTopEntries(rankingId, 3);
            Long maxScore = null;
            if (!topEntries.isEmpty()) {
                maxScore = topEntries.get(0).getScore();
            }

            return new RankingStatistics(rankingId, ranking.getRankingName(), 
                                       totalCount, maxScore, updateCount, queryCount);
            
        } catch (Exception e) {
            log.error("获取排行榜统计失败: rankingId=" + rankingId, e);
            return null;
        }
    }

    // ===== 异步查询接口 =====

    /**
     * 异步获取前N名
     *
     * @param rankingId 排行榜ID
     * @param topN      前N名
     * @return 异步结果
     */
    public CompletableFuture<List<RankingEntry>> getTopEntriesAsync(String rankingId, int topN) {
        return CompletableFuture.supplyAsync(() -> getTopEntries(rankingId, topN));
    }

    /**
     * 异步分页查询
     *
     * @param rankingId 排行榜ID
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 异步结果
     */
    public CompletableFuture<PageResult<RankingEntry>> getPagedEntriesAsync(String rankingId, int pageNum, int pageSize) {
        return CompletableFuture.supplyAsync(() -> getPagedEntries(rankingId, pageNum, pageSize));
    }

    // ===== 缓存管理 =====

    /**
     * 清空查询缓存
     *
     * @param rankingId 排行榜ID，null表示清空所有缓存
     */
    public void clearQueryCache(String rankingId) {
        if (rankingId == null) {
            queryCache.clear();
            log.info("清空所有查询缓存");
        } else {
            queryCache.entrySet().removeIf(entry -> entry.getKey().contains(rankingId));
            log.info("清空排行榜查询缓存: {}", rankingId);
        }
    }

    /**
     * 获取缓存统计
     *
     * @return 缓存统计信息
     */
    public CacheStatistics getCacheStatistics() {
        cleanExpiredCache();
        
        int totalCached = queryCache.size();
        int hitCount = queryCache.values().stream()
            .mapToInt(CachedResult::getHitCount)
            .sum();
            
        return new CacheStatistics(totalCached, hitCount, maxCacheSize);
    }

    // ===== 工具方法 =====

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String operation, String rankingId, String params) {
        return operation + ":" + rankingId + ":" + params;
    }

    /**
     * 从缓存获取结果
     */
    private CachedResult getFromCache(String cacheKey) {
        CachedResult cached = queryCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheExpireTime)) {
            cached.incrementHitCount();
            return cached;
        }
        
        // 移除过期缓存
        if (cached != null) {
            queryCache.remove(cacheKey);
        }
        
        return null;
    }

    /**
     * 放入缓存
     */
    private void putToCache(String cacheKey, List<RankingEntry> entries) {
        // 控制缓存大小
        if (queryCache.size() >= maxCacheSize) {
            cleanExpiredCache();
            
            if (queryCache.size() >= maxCacheSize) {
                // 移除最老的缓存
                String oldestKey = queryCache.entrySet().stream()
                    .min(Map.Entry.comparingByValue(Comparator.comparing(CachedResult::getCreateTime)))
                    .map(Map.Entry::getKey)
                    .orElse(null);
                    
                if (oldestKey != null) {
                    queryCache.remove(oldestKey);
                }
            }
        }

        queryCache.put(cacheKey, new CachedResult(entries));
    }

    /**
     * 清理过期缓存
     */
    private void cleanExpiredCache() {
        queryCache.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(cacheExpireTime));
    }

    // ===== 内部类 =====

    /**
     * 缓存结果
     */
    private static class CachedResult {
        private final List<RankingEntry> entries;
        private final long createTime;
        private int hitCount = 0;

        public CachedResult(List<RankingEntry> entries) {
            this.entries = new ArrayList<>(entries);
            this.createTime = System.currentTimeMillis();
        }

        public List<RankingEntry> getEntries() {
            // 返回副本，避免外部修改
            return new ArrayList<>(entries);
        }

        public long getCreateTime() { return createTime; }
        public int getHitCount() { return hitCount; }
        public void incrementHitCount() { hitCount++; }

        public boolean isExpired(long expireTime) {
            return System.currentTimeMillis() - createTime > expireTime;
        }
    }

    /**
     * 分页结果
     */
    public static class PageResult<T> {
        private final List<T> data;
        private final int pageNum;
        private final int pageSize;
        private final long total;
        private final int totalPages;

        public PageResult(List<T> data, int pageNum, int pageSize, long total) {
            this.data = data != null ? data : Collections.emptyList();
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            this.total = total;
            this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        }

        public static <T> PageResult<T> empty() {
            return new PageResult<>(Collections.emptyList(), 1, 10, 0);
        }

        public List<T> getData() { return data; }
        public int getPageNum() { return pageNum; }
        public int getPageSize() { return pageSize; }
        public long getTotal() { return total; }
        public int getTotalPages() { return totalPages; }
        public boolean hasNext() { return pageNum < totalPages; }
        public boolean hasPrev() { return pageNum > 1; }
    }

    /**
     * 排行榜统计信息
     */
    public static class RankingStatistics {
        private final String rankingId;
        private final String rankingName;
        private final long totalCount;
        private final Long maxScore;
        private final long updateCount;
        private final long queryCount;

        public RankingStatistics(String rankingId, String rankingName, long totalCount, 
                               Long maxScore, long updateCount, long queryCount) {
            this.rankingId = rankingId;
            this.rankingName = rankingName;
            this.totalCount = totalCount;
            this.maxScore = maxScore;
            this.updateCount = updateCount;
            this.queryCount = queryCount;
        }

        public String getRankingId() { return rankingId; }
        public String getRankingName() { return rankingName; }
        public long getTotalCount() { return totalCount; }
        public Long getMaxScore() { return maxScore; }
        public long getUpdateCount() { return updateCount; }
        public long getQueryCount() { return queryCount; }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        private final int totalCached;
        private final int totalHits;
        private final int maxCacheSize;

        public CacheStatistics(int totalCached, int totalHits, int maxCacheSize) {
            this.totalCached = totalCached;
            this.totalHits = totalHits;
            this.maxCacheSize = maxCacheSize;
        }

        public int getTotalCached() { return totalCached; }
        public int getTotalHits() { return totalHits; }
        public int getMaxCacheSize() { return maxCacheSize; }
        public double getUsageRate() { 
            return maxCacheSize > 0 ? (double) totalCached / maxCacheSize : 0.0; 
        }
    }
}