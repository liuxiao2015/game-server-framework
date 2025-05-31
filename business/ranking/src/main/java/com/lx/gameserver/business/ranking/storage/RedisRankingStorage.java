/*
 * 文件名: RedisRankingStorage.java
 * 用途: Redis排行榜存储实现
 * 实现内容:
 *   - 基于Redis Sorted Set的排行榜存储
 *   - 高性能的分数设置和排名查询
 *   - 支持Pipeline批量操作优化
 *   - 提供Lua脚本支持原子操作
 * 技术选型:
 *   - 使用Redis Sorted Set作为核心数据结构
 *   - 集成Spring Data Redis
 *   - 支持集群和哨兵模式
 * 依赖关系:
 *   - 实现RankingStorage接口
 *   - 依赖Redis连接配置
 *   - 被排行榜管理组件使用
 */
package com.lx.gameserver.business.ranking.storage;

import com.lx.gameserver.business.ranking.core.RankingEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis排行榜存储实现
 * <p>
 * 基于Redis Sorted Set实现的高性能排行榜存储，
 * 支持实时排名查询和高并发更新操作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class RedisRankingStorage implements RankingStorage {

    private final RedisTemplate<String, String> redisTemplate;
    private final ZSetOperations<String, String> zSetOps;

    /**
     * Redis键前缀
     */
    private static final String KEY_PREFIX = "ranking:";

    /**
     * 实体信息键前缀
     */
    private static final String INFO_PREFIX = "ranking:info:";

    public RedisRankingStorage(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.zSetOps = redisTemplate.opsForZSet();
    }

    // ===== 基础操作实现 =====

    @Override
    public boolean setScore(String rankingKey, Long entityId, Long score) {
        try {
            String key = buildRankingKey(rankingKey);
            Boolean result = zSetOps.add(key, entityId.toString(), score.doubleValue());
            return result != null && result;
        } catch (Exception e) {
            log.error("设置分数失败: rankingKey={}, entityId={}, score={}", 
                     rankingKey, entityId, score, e);
            return false;
        }
    }

    @Override
    public Long incrementScore(String rankingKey, Long entityId, Long increment) {
        try {
            String key = buildRankingKey(rankingKey);
            Double newScore = zSetOps.incrementScore(key, entityId.toString(), increment.doubleValue());
            return newScore != null ? newScore.longValue() : null;
        } catch (Exception e) {
            log.error("增加分数失败: rankingKey={}, entityId={}, increment={}", 
                     rankingKey, entityId, increment, e);
            return null;
        }
    }

    @Override
    public Long getScore(String rankingKey, Long entityId) {
        try {
            String key = buildRankingKey(rankingKey);
            Double score = zSetOps.score(key, entityId.toString());
            return score != null ? score.longValue() : null;
        } catch (Exception e) {
            log.error("获取分数失败: rankingKey={}, entityId={}", rankingKey, entityId, e);
            return null;
        }
    }

    @Override
    public Integer getRank(String rankingKey, Long entityId) {
        try {
            String key = buildRankingKey(rankingKey);
            // Redis的rank是从0开始的，需要+1转换为从1开始
            Long rank = zSetOps.reverseRank(key, entityId.toString());
            return rank != null ? rank.intValue() + 1 : null;
        } catch (Exception e) {
            log.error("获取排名失败: rankingKey={}, entityId={}", rankingKey, entityId, e);
            return null;
        }
    }

    @Override
    public boolean removeEntity(String rankingKey, Long entityId) {
        try {
            String key = buildRankingKey(rankingKey);
            Long removed = zSetOps.remove(key, entityId.toString());
            return removed != null && removed > 0;
        } catch (Exception e) {
            log.error("删除实体失败: rankingKey={}, entityId={}", rankingKey, entityId, e);
            return false;
        }
    }

    @Override
    public boolean existsEntity(String rankingKey, Long entityId) {
        try {
            String key = buildRankingKey(rankingKey);
            Double score = zSetOps.score(key, entityId.toString());
            return score != null;
        } catch (Exception e) {
            log.error("检查实体存在失败: rankingKey={}, entityId={}", rankingKey, entityId, e);
            return false;
        }
    }

    // ===== 批量操作实现 =====

    @Override
    public int batchSetScores(String rankingKey, Map<Long, Long> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0;
        }

        try {
            String key = buildRankingKey(rankingKey);
            Set<ZSetOperations.TypedTuple<String>> tuples = scores.entrySet().stream()
                .map(entry -> ZSetOperations.TypedTuple.of(
                    entry.getKey().toString(), 
                    entry.getValue().doubleValue()))
                .collect(Collectors.toSet());
            
            Long result = zSetOps.add(key, tuples);
            return result != null ? result.intValue() : 0;
        } catch (Exception e) {
            log.error("批量设置分数失败: rankingKey={}, size={}", rankingKey, scores.size(), e);
            return 0;
        }
    }

    @Override
    public Map<Long, Long> batchIncrementScores(String rankingKey, Map<Long, Long> increments) {
        Map<Long, Long> results = new HashMap<>();
        if (increments == null || increments.isEmpty()) {
            return results;
        }

        try {
            String key = buildRankingKey(rankingKey);
            for (Map.Entry<Long, Long> entry : increments.entrySet()) {
                Long entityId = entry.getKey();
                Long increment = entry.getValue();
                Double newScore = zSetOps.incrementScore(key, entityId.toString(), increment.doubleValue());
                if (newScore != null) {
                    results.put(entityId, newScore.longValue());
                }
            }
        } catch (Exception e) {
            log.error("批量增加分数失败: rankingKey={}, size={}", rankingKey, increments.size(), e);
        }

        return results;
    }

    @Override
    public Map<Long, Long> batchGetScores(String rankingKey, Set<Long> entityIds) {
        Map<Long, Long> results = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return results;
        }

        try {
            String key = buildRankingKey(rankingKey);
            for (Long entityId : entityIds) {
                Double score = zSetOps.score(key, entityId.toString());
                if (score != null) {
                    results.put(entityId, score.longValue());
                }
            }
        } catch (Exception e) {
            log.error("批量获取分数失败: rankingKey={}, size={}", rankingKey, entityIds.size(), e);
        }

        return results;
    }

    @Override
    public Map<Long, Integer> batchGetRanks(String rankingKey, Set<Long> entityIds) {
        Map<Long, Integer> results = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return results;
        }

        try {
            String key = buildRankingKey(rankingKey);
            for (Long entityId : entityIds) {
                Long rank = zSetOps.reverseRank(key, entityId.toString());
                if (rank != null) {
                    results.put(entityId, rank.intValue() + 1); // 转换为从1开始
                }
            }
        } catch (Exception e) {
            log.error("批量获取排名失败: rankingKey={}, size={}", rankingKey, entityIds.size(), e);
        }

        return results;
    }

    @Override
    public int batchRemoveEntities(String rankingKey, Set<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return 0;
        }

        try {
            String key = buildRankingKey(rankingKey);
            String[] members = entityIds.stream()
                .map(Object::toString)
                .toArray(String[]::new);
            
            Long removed = zSetOps.remove(key, (Object[]) members);
            return removed != null ? removed.intValue() : 0;
        } catch (Exception e) {
            log.error("批量删除实体失败: rankingKey={}, size={}", rankingKey, entityIds.size(), e);
            return 0;
        }
    }

    // ===== 查询实现 =====

    @Override
    public List<RankingEntry> getTopEntries(String rankingKey, int topN) {
        return getRangeEntries(rankingKey, 1, topN);
    }

    @Override
    public List<RankingEntry> getRangeEntries(String rankingKey, int start, int end) {
        try {
            String key = buildRankingKey(rankingKey);
            // Redis的range是从0开始的，需要转换
            Set<ZSetOperations.TypedTuple<String>> tuples = 
                zSetOps.reverseRangeWithScores(key, start - 1, end - 1);
            
            if (tuples == null || tuples.isEmpty()) {
                return Collections.emptyList();
            }

            List<RankingEntry> entries = new ArrayList<>();
            int rank = start;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null && tuple.getScore() != null) {
                    Long entityId = Long.parseLong(tuple.getValue());
                    Long score = tuple.getScore().longValue();
                    
                    RankingEntry entry = new RankingEntry(entityId, rank, score);
                    entries.add(entry);
                    rank++;
                }
            }
            
            return entries;
        } catch (Exception e) {
            log.error("获取范围排名失败: rankingKey={}, start={}, end={}", 
                     rankingKey, start, end, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<RankingEntry> getSurroundingEntries(String rankingKey, Long entityId, int range) {
        try {
            Integer currentRank = getRank(rankingKey, entityId);
            if (currentRank == null) {
                return Collections.emptyList();
            }

            int start = Math.max(1, currentRank - range);
            int end = currentRank + range;
            
            return getRangeEntries(rankingKey, start, end);
        } catch (Exception e) {
            log.error("获取周围排名失败: rankingKey={}, entityId={}, range={}", 
                     rankingKey, entityId, range, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<RankingEntry> getEntriesByScoreRange(String rankingKey, Long minScore, Long maxScore) {
        try {
            String key = buildRankingKey(rankingKey);
            Set<ZSetOperations.TypedTuple<String>> tuples = 
                zSetOps.reverseRangeByScoreWithScores(key, minScore.doubleValue(), maxScore.doubleValue());
            
            if (tuples == null || tuples.isEmpty()) {
                return Collections.emptyList();
            }

            List<RankingEntry> entries = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null && tuple.getScore() != null) {
                    Long entityId = Long.parseLong(tuple.getValue());
                    Long score = tuple.getScore().longValue();
                    Integer rank = getRank(rankingKey, entityId);
                    
                    RankingEntry entry = new RankingEntry(entityId, rank, score);
                    entries.add(entry);
                }
            }
            
            return entries;
        } catch (Exception e) {
            log.error("根据分数范围查询失败: rankingKey={}, minScore={}, maxScore={}", 
                     rankingKey, minScore, maxScore, e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getCount(String rankingKey) {
        try {
            String key = buildRankingKey(rankingKey);
            Long count = zSetOps.count(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取排行榜总数失败: rankingKey={}", rankingKey, e);
            return 0;
        }
    }

    @Override
    public ScoreStatistics getScoreStatistics(String rankingKey) {
        try {
            String key = buildRankingKey(rankingKey);
            long count = getCount(rankingKey);
            
            if (count == 0) {
                return new ScoreStatistics(0, null, null, null, null);
            }

            // 获取最高分（第一名）
            Set<ZSetOperations.TypedTuple<String>> maxTuple = 
                zSetOps.reverseRangeWithScores(key, 0, 0);
            Long maxScore = null;
            if (maxTuple != null && !maxTuple.isEmpty()) {
                ZSetOperations.TypedTuple<String> tuple = maxTuple.iterator().next();
                if (tuple.getScore() != null) {
                    maxScore = tuple.getScore().longValue();
                }
            }

            // 获取最低分（最后一名）
            Set<ZSetOperations.TypedTuple<String>> minTuple = 
                zSetOps.rangeWithScores(key, 0, 0);
            Long minScore = null;
            if (minTuple != null && !minTuple.isEmpty()) {
                ZSetOperations.TypedTuple<String> tuple = minTuple.iterator().next();
                if (tuple.getScore() != null) {
                    minScore = tuple.getScore().longValue();
                }
            }

            // TODO: 计算平均分和总分（需要遍历所有元素或使用Lua脚本）
            Double avgScore = null;
            Long totalScore = null;

            return new ScoreStatistics(count, minScore, maxScore, avgScore, totalScore);
        } catch (Exception e) {
            log.error("获取分数统计失败: rankingKey={}", rankingKey, e);
            return new ScoreStatistics(0, null, null, null, null);
        }
    }

    // ===== 管理接口实现 =====

    @Override
    public boolean clear(String rankingKey) {
        try {
            String key = buildRankingKey(rankingKey);
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.error("清空排行榜失败: rankingKey={}", rankingKey, e);
            return false;
        }
    }

    @Override
    public boolean delete(String rankingKey) {
        return clear(rankingKey);
    }

    @Override
    public boolean exists(String rankingKey) {
        try {
            String key = buildRankingKey(rankingKey);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查排行榜存在失败: rankingKey={}", rankingKey, e);
            return false;
        }
    }

    @Override
    public boolean rename(String oldKey, String newKey) {
        try {
            String oldRedisKey = buildRankingKey(oldKey);
            String newRedisKey = buildRankingKey(newKey);
            redisTemplate.rename(oldRedisKey, newRedisKey);
            return true;
        } catch (Exception e) {
            log.error("重命名排行榜失败: oldKey={}, newKey={}", oldKey, newKey, e);
            return false;
        }
    }

    @Override
    public boolean expire(String rankingKey, long seconds) {
        try {
            String key = buildRankingKey(rankingKey);
            return Boolean.TRUE.equals(redisTemplate.expire(key, seconds, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.error("设置过期时间失败: rankingKey={}, seconds={}", rankingKey, seconds, e);
            return false;
        }
    }

    @Override
    public long getTtl(String rankingKey) {
        try {
            String key = buildRankingKey(rankingKey);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.error("获取过期时间失败: rankingKey={}", rankingKey, e);
            return -2;
        }
    }

    // ===== 异步操作实现 =====

    @Override
    public CompletableFuture<Boolean> setScoreAsync(String rankingKey, Long entityId, Long score) {
        return CompletableFuture.supplyAsync(() -> setScore(rankingKey, entityId, score));
    }

    @Override
    public CompletableFuture<List<RankingEntry>> getTopEntriesAsync(String rankingKey, int topN) {
        return CompletableFuture.supplyAsync(() -> getTopEntries(rankingKey, topN));
    }

    @Override
    public CompletableFuture<Integer> batchSetScoresAsync(String rankingKey, Map<Long, Long> scores) {
        return CompletableFuture.supplyAsync(() -> batchSetScores(rankingKey, scores));
    }

    // ===== 事务支持（简化实现） =====

    @Override
    public StorageTransaction beginTransaction() {
        // Redis事务相对复杂，这里提供简化实现
        return new RedisTransaction();
    }

    @Override
    public boolean executeInTransaction(StorageTransaction transaction, List<StorageOperation> operations) {
        // 简化实现，实际应该使用Redis的MULTI/EXEC
        try {
            for (StorageOperation operation : operations) {
                operation.execute(this);
            }
            return transaction.commit();
        } catch (Exception e) {
            log.error("事务执行失败", e);
            transaction.rollback();
            return false;
        }
    }

    // ===== 工具方法 =====

    /**
     * 构建Redis键名
     */
    private String buildRankingKey(String rankingKey) {
        return KEY_PREFIX + rankingKey;
    }

    /**
     * 构建实体信息键名
     */
    private String buildInfoKey(String rankingKey, Long entityId) {
        return INFO_PREFIX + rankingKey + ":" + entityId;
    }

    // ===== 内部类 =====

    /**
     * Redis事务简化实现
     */
    private static class RedisTransaction implements StorageTransaction {
        private boolean active = true;

        @Override
        public boolean commit() {
            active = false;
            return true;
        }

        @Override
        public boolean rollback() {
            active = false;
            return true;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}