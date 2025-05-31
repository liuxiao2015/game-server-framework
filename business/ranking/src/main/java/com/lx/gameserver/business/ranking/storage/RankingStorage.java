/*
 * 文件名: RankingStorage.java
 * 用途: 排行榜存储接口定义
 * 实现内容:
 *   - 定义排行榜数据存储的标准接口
 *   - 支持分数设置、增加、删除等基本操作
 *   - 提供排名查询和范围查询功能
 *   - 支持批量操作和事务处理
 * 技术选型:
 *   - 使用接口定义规范存储行为
 *   - 支持多种存储实现（Redis、数据库等）
 *   - 提供异步操作支持
 * 依赖关系:
 *   - 被存储实现类实现
 *   - 被排行榜核心组件调用
 *   - 被更新器和查询服务使用
 */
package com.lx.gameserver.business.ranking.storage;

import com.lx.gameserver.business.ranking.core.RankingEntry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 排行榜存储接口
 * <p>
 * 定义了排行榜数据存储的标准接口，包括基本的CRUD操作、
 * 排名查询、批量操作等。支持同步和异步两种操作模式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface RankingStorage {

    // ===== 基础操作接口 =====

    /**
     * 设置实体分数
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @param score      分数
     * @return 是否操作成功
     */
    boolean setScore(String rankingKey, Long entityId, Long score);

    /**
     * 增加实体分数
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @param increment  增量分数
     * @return 操作后的新分数
     */
    Long incrementScore(String rankingKey, Long entityId, Long increment);

    /**
     * 获取实体分数
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @return 实体分数，如果不存在返回null
     */
    Long getScore(String rankingKey, Long entityId);

    /**
     * 获取实体排名
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @return 实体排名（从1开始），如果不存在返回null
     */
    Integer getRank(String rankingKey, Long entityId);

    /**
     * 删除实体
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @return 是否删除成功
     */
    boolean removeEntity(String rankingKey, Long entityId);

    /**
     * 检查实体是否存在
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @return 是否存在
     */
    boolean existsEntity(String rankingKey, Long entityId);

    // ===== 批量操作接口 =====

    /**
     * 批量设置分数
     *
     * @param rankingKey 排行榜键
     * @param scores     分数映射（实体ID -> 分数）
     * @return 操作成功的数量
     */
    int batchSetScores(String rankingKey, Map<Long, Long> scores);

    /**
     * 批量增加分数
     *
     * @param rankingKey 排行榜键
     * @param increments 增量映射（实体ID -> 增量）
     * @return 操作结果映射（实体ID -> 新分数）
     */
    Map<Long, Long> batchIncrementScores(String rankingKey, Map<Long, Long> increments);

    /**
     * 批量获取分数
     *
     * @param rankingKey 排行榜键
     * @param entityIds  实体ID集合
     * @return 分数映射（实体ID -> 分数）
     */
    Map<Long, Long> batchGetScores(String rankingKey, Set<Long> entityIds);

    /**
     * 批量获取排名
     *
     * @param rankingKey 排行榜键
     * @param entityIds  实体ID集合
     * @return 排名映射（实体ID -> 排名）
     */
    Map<Long, Integer> batchGetRanks(String rankingKey, Set<Long> entityIds);

    /**
     * 批量删除实体
     *
     * @param rankingKey 排行榜键
     * @param entityIds  实体ID集合
     * @return 删除成功的数量
     */
    int batchRemoveEntities(String rankingKey, Set<Long> entityIds);

    // ===== 查询接口 =====

    /**
     * 获取前N名
     *
     * @param rankingKey 排行榜键
     * @param topN       前N名
     * @return 排行榜条目列表
     */
    List<RankingEntry> getTopEntries(String rankingKey, int topN);

    /**
     * 获取指定范围的排名
     *
     * @param rankingKey 排行榜键
     * @param start      起始排名（从1开始）
     * @param end        结束排名（包含）
     * @return 排行榜条目列表
     */
    List<RankingEntry> getRangeEntries(String rankingKey, int start, int end);

    /**
     * 获取实体周围的排名
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @param range      周围范围（前后各多少名）
     * @return 排行榜条目列表
     */
    List<RankingEntry> getSurroundingEntries(String rankingKey, Long entityId, int range);

    /**
     * 根据分数范围查询
     *
     * @param rankingKey 排行榜键
     * @param minScore   最小分数（包含）
     * @param maxScore   最大分数（包含）
     * @return 排行榜条目列表
     */
    List<RankingEntry> getEntriesByScoreRange(String rankingKey, Long minScore, Long maxScore);

    /**
     * 获取排行榜总数量
     *
     * @param rankingKey 排行榜键
     * @return 总数量
     */
    long getCount(String rankingKey);

    /**
     * 获取分数统计信息
     *
     * @param rankingKey 排行榜键
     * @return 统计信息
     */
    ScoreStatistics getScoreStatistics(String rankingKey);

    // ===== 管理接口 =====

    /**
     * 清空排行榜
     *
     * @param rankingKey 排行榜键
     * @return 是否清空成功
     */
    boolean clear(String rankingKey);

    /**
     * 删除排行榜
     *
     * @param rankingKey 排行榜键
     * @return 是否删除成功
     */
    boolean delete(String rankingKey);

    /**
     * 检查排行榜是否存在
     *
     * @param rankingKey 排行榜键
     * @return 是否存在
     */
    boolean exists(String rankingKey);

    /**
     * 重命名排行榜
     *
     * @param oldKey 旧键名
     * @param newKey 新键名
     * @return 是否重命名成功
     */
    boolean rename(String oldKey, String newKey);

    /**
     * 设置排行榜过期时间
     *
     * @param rankingKey 排行榜键
     * @param seconds    过期时间（秒）
     * @return 是否设置成功
     */
    boolean expire(String rankingKey, long seconds);

    /**
     * 获取排行榜剩余过期时间
     *
     * @param rankingKey 排行榜键
     * @return 剩余时间（秒），-1表示永不过期，-2表示不存在
     */
    long getTtl(String rankingKey);

    // ===== 异步操作接口 =====

    /**
     * 异步设置分数
     *
     * @param rankingKey 排行榜键
     * @param entityId   实体ID
     * @param score      分数
     * @return 异步结果
     */
    CompletableFuture<Boolean> setScoreAsync(String rankingKey, Long entityId, Long score);

    /**
     * 异步获取前N名
     *
     * @param rankingKey 排行榜键
     * @param topN       前N名
     * @return 异步结果
     */
    CompletableFuture<List<RankingEntry>> getTopEntriesAsync(String rankingKey, int topN);

    /**
     * 异步批量操作
     *
     * @param rankingKey 排行榜键
     * @param scores     分数映射
     * @return 异步结果
     */
    CompletableFuture<Integer> batchSetScoresAsync(String rankingKey, Map<Long, Long> scores);

    // ===== 事务支持 =====

    /**
     * 开始事务
     *
     * @return 事务对象
     */
    StorageTransaction beginTransaction();

    /**
     * 在事务中执行操作
     *
     * @param transaction 事务对象
     * @param operations  操作列表
     * @return 是否执行成功
     */
    boolean executeInTransaction(StorageTransaction transaction, List<StorageOperation> operations);

    // ===== 内部类定义 =====

    /**
     * 分数统计信息
     */
    class ScoreStatistics {
        private final long count;
        private final Long minScore;
        private final Long maxScore;
        private final Double avgScore;
        private final Long totalScore;

        public ScoreStatistics(long count, Long minScore, Long maxScore, 
                              Double avgScore, Long totalScore) {
            this.count = count;
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.avgScore = avgScore;
            this.totalScore = totalScore;
        }

        public long getCount() { return count; }
        public Long getMinScore() { return minScore; }
        public Long getMaxScore() { return maxScore; }
        public Double getAvgScore() { return avgScore; }
        public Long getTotalScore() { return totalScore; }

        @Override
        public String toString() {
            return String.format("ScoreStatistics{count=%d, min=%d, max=%d, avg=%.2f, total=%d}",
                               count, minScore, maxScore, avgScore, totalScore);
        }
    }

    /**
     * 存储事务接口
     */
    interface StorageTransaction {
        /**
         * 提交事务
         * @return 是否提交成功
         */
        boolean commit();

        /**
         * 回滚事务
         * @return 是否回滚成功
         */
        boolean rollback();

        /**
         * 检查事务是否活跃
         * @return 是否活跃
         */
        boolean isActive();
    }

    /**
     * 存储操作接口
     */
    interface StorageOperation {
        /**
         * 执行操作
         * @param storage 存储实例
         * @return 操作结果
         */
        Object execute(RankingStorage storage);

        /**
         * 获取操作类型
         * @return 操作类型
         */
        String getOperationType();
    }
}