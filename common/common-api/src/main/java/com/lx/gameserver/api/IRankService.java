/*
 * 文件名: IRankService.java
 * 用途: 排行榜服务接口定义
 * 实现内容:
 *   - 定义排行榜数据提交和查询接口
 *   - 定义多种排行榜类型和排序规则
 *   - 定义排行榜快照和历史记录接口
 *   - 支持实时排行榜和定时快照功能
 * 技术选型:
 *   - 使用Java接口定义服务规范
 *   - 集成Result和PageResult通用返回类型
 *   - 支持Redis排行榜和数据库持久化
 * 依赖关系:
 *   - 依赖common-core的Result和PageResult
 *   - 被rank-service模块实现
 *   - 被需要排行榜功能的模块调用
 */
package com.lx.gameserver.api;

import com.lx.gameserver.common.Result;
import com.lx.gameserver.common.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 排行榜服务接口
 * <p>
 * 定义了排行榜系统的所有核心功能，包括分数提交、排名查询、
 * 排行榜管理、快照创建等。支持多种排行榜类型和排序规则，
 * 提供实时和历史排行榜数据。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public interface IRankService {

    // ===== 分数提交接口 =====

    /**
     * 提交排行榜分数
     *
     * @param rankType 排行榜类型
     * @param playerId 玩家ID
     * @param score    分数
     * @param extraData 附加数据（可选）
     * @return 提交结果，包含当前排名
     */
    Result<SubmitScoreResult> submitScore(RankType rankType, Long playerId, Long score, 
                                         Map<String, Object> extraData);

    /**
     * 批量提交排行榜分数
     *
     * @param rankType 排行榜类型
     * @param scores   分数列表
     * @return 批量提交结果
     */
    Result<List<SubmitScoreResult>> batchSubmitScore(RankType rankType, List<ScoreEntry> scores);

    /**
     * 增加排行榜分数
     *
     * @param rankType 排行榜类型
     * @param playerId 玩家ID
     * @param increment 增加的分数
     * @param reason   增加原因
     * @return 操作结果，包含新的分数和排名
     */
    Result<SubmitScoreResult> incrementScore(RankType rankType, Long playerId, Long increment, String reason);

    /**
     * 移除玩家排行榜记录
     *
     * @param rankType 排行榜类型
     * @param playerId 玩家ID
     * @return 移除结果
     */
    Result<Void> removePlayerRank(RankType rankType, Long playerId);

    // ===== 排名查询接口 =====

    /**
     * 获取排行榜前N名
     *
     * @param rankType 排行榜类型
     * @param topN     前N名
     * @return 排行榜前N名列表
     */
    Result<List<RankEntry>> getTopRanks(RankType rankType, Integer topN);

    /**
     * 获取排行榜指定范围的排名
     *
     * @param rankType 排行榜类型
     * @param start    开始排名（从1开始）
     * @param end      结束排名
     * @return 指定范围的排行榜
     */
    Result<List<RankEntry>> getRankRange(RankType rankType, Integer start, Integer end);

    /**
     * 分页获取排行榜
     *
     * @param rankType 排行榜类型
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 排行榜分页结果
     */
    PageResult<RankEntry> getRankList(RankType rankType, Integer pageNum, Integer pageSize);

    /**
     * 获取玩家排名信息
     *
     * @param rankType 排行榜类型
     * @param playerId 玩家ID
     * @return 玩家排名信息
     */
    Result<PlayerRankInfo> getPlayerRank(RankType rankType, Long playerId);

    /**
     * 批量获取玩家排名信息
     *
     * @param rankType  排行榜类型
     * @param playerIds 玩家ID列表
     * @return 玩家排名信息映射
     */
    Result<Map<Long, PlayerRankInfo>> getPlayersRank(RankType rankType, List<Long> playerIds);

    /**
     * 获取玩家周围排名
     *
     * @param rankType 排行榜类型
     * @param playerId 玩家ID
     * @param range    周围范围（前后各多少名）
     * @return 玩家周围排名列表
     */
    Result<List<RankEntry>> getPlayerSurroundingRank(RankType rankType, Long playerId, Integer range);

    // ===== 排行榜管理接口 =====

    /**
     * 创建排行榜
     *
     * @param rankConfig 排行榜配置
     * @return 创建结果
     */
    Result<Void> createRankList(RankConfig rankConfig);

    /**
     * 更新排行榜配置
     *
     * @param rankType   排行榜类型
     * @param rankConfig 新的排行榜配置
     * @return 更新结果
     */
    Result<Void> updateRankConfig(RankType rankType, RankConfig rankConfig);

    /**
     * 删除排行榜
     *
     * @param rankType 排行榜类型
     * @return 删除结果
     */
    Result<Void> deleteRankList(RankType rankType);

    /**
     * 清空排行榜
     *
     * @param rankType 排行榜类型
     * @return 清空结果
     */
    Result<Void> clearRankList(RankType rankType);

    /**
     * 获取排行榜配置
     *
     * @param rankType 排行榜类型
     * @return 排行榜配置
     */
    Result<RankConfig> getRankConfig(RankType rankType);

    /**
     * 获取所有排行榜列表
     *
     * @return 排行榜列表
     */
    Result<List<RankInfo>> getAllRankLists();

    // ===== 排行榜快照接口 =====

    /**
     * 创建排行榜快照
     *
     * @param rankType     排行榜类型
     * @param snapshotName 快照名称
     * @param description  快照描述
     * @return 创建结果，包含快照ID
     */
    Result<Long> createRankSnapshot(RankType rankType, String snapshotName, String description);

    /**
     * 获取排行榜快照列表
     *
     * @param rankType 排行榜类型
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 快照列表分页结果
     */
    PageResult<RankSnapshot> getRankSnapshots(RankType rankType, Integer pageNum, Integer pageSize);

    /**
     * 获取快照数据
     *
     * @param snapshotId 快照ID
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 快照数据分页结果
     */
    PageResult<RankEntry> getSnapshotData(Long snapshotId, Integer pageNum, Integer pageSize);

    /**
     * 删除排行榜快照
     *
     * @param snapshotId 快照ID
     * @return 删除结果
     */
    Result<Void> deleteRankSnapshot(Long snapshotId);

    /**
     * 定时创建快照任务
     *
     * @param rankType  排行榜类型
     * @param cronExpr  定时表达式
     * @param taskName  任务名称
     * @return 创建结果，包含任务ID
     */
    Result<Long> scheduleSnapshotTask(RankType rankType, String cronExpr, String taskName);

    // ===== 排行榜统计接口 =====

    /**
     * 获取排行榜统计信息
     *
     * @param rankType 排行榜类型
     * @return 统计信息
     */
    Result<RankStatistics> getRankStatistics(RankType rankType);

    /**
     * 获取玩家历史排名变化
     *
     * @param rankType  排行榜类型
     * @param playerId  玩家ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 历史排名变化列表
     */
    Result<List<RankHistoryEntry>> getPlayerRankHistory(RankType rankType, Long playerId, 
                                                        Long startTime, Long endTime);

    /**
     * 获取排行榜变化趋势
     *
     * @param rankType  排行榜类型
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 排行榜变化趋势数据
     */
    Result<RankTrend> getRankTrend(RankType rankType, Long startTime, Long endTime);

    // ===== 赛季排行榜接口 =====

    /**
     * 创建赛季排行榜
     *
     * @param seasonConfig 赛季配置
     * @return 创建结果，包含赛季ID
     */
    Result<Long> createSeason(SeasonConfig seasonConfig);

    /**
     * 结束当前赛季
     *
     * @param rankType 排行榜类型
     * @return 结束结果
     */
    Result<Void> endCurrentSeason(RankType rankType);

    /**
     * 获取当前赛季信息
     *
     * @param rankType 排行榜类型
     * @return 当前赛季信息
     */
    Result<SeasonInfo> getCurrentSeason(RankType rankType);

    /**
     * 获取历史赛季列表
     *
     * @param rankType 排行榜类型
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 历史赛季分页列表
     */
    PageResult<SeasonInfo> getHistorySeasons(RankType rankType, Integer pageNum, Integer pageSize);

    /**
     * 获取赛季排行榜
     *
     * @param seasonId 赛季ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 赛季排行榜分页结果
     */
    PageResult<RankEntry> getSeasonRank(Long seasonId, Integer pageNum, Integer pageSize);

    // ===== 内部数据结构定义 =====

    /**
     * 排行榜类型枚举
     */
    enum RankType {
        /** 等级排行榜 */
        LEVEL,
        /** 战力排行榜 */
        POWER,
        /** 财富排行榜 */
        WEALTH,
        /** 经验排行榜 */
        EXPERIENCE,
        /** 公会排行榜 */
        GUILD,
        /** 竞技场排行榜 */
        ARENA,
        /** 副本排行榜 */
        DUNGEON,
        /** 活动排行榜 */
        ACTIVITY,
        /** 充值排行榜 */
        RECHARGE,
        /** 消费排行榜 */
        CONSUMPTION
    }

    /**
     * 排序类型枚举
     */
    enum SortType {
        /** 降序排列 */
        DESC,
        /** 升序排列 */
        ASC
    }

    /**
     * 分数条目
     */
    class ScoreEntry {
        /** 玩家ID */
        private Long playerId;
        /** 分数 */
        private Long score;
        /** 附加数据 */
        private Map<String, Object> extraData;

        // Constructors
        public ScoreEntry() {}

        public ScoreEntry(Long playerId, Long score) {
            this.playerId = playerId;
            this.score = score;
        }

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Map<String, Object> getExtraData() { return extraData; }
        public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }
    }

    /**
     * 提交分数结果
     */
    class SubmitScoreResult {
        /** 是否成功 */
        private Boolean success;
        /** 当前分数 */
        private Long currentScore;
        /** 当前排名 */
        private Integer currentRank;
        /** 之前排名 */
        private Integer previousRank;
        /** 排名变化 */
        private Integer rankChange;

        // Getters and Setters
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Long getCurrentScore() { return currentScore; }
        public void setCurrentScore(Long currentScore) { this.currentScore = currentScore; }
        public Integer getCurrentRank() { return currentRank; }
        public void setCurrentRank(Integer currentRank) { this.currentRank = currentRank; }
        public Integer getPreviousRank() { return previousRank; }
        public void setPreviousRank(Integer previousRank) { this.previousRank = previousRank; }
        public Integer getRankChange() { return rankChange; }
        public void setRankChange(Integer rankChange) { this.rankChange = rankChange; }
    }

    /**
     * 排行榜条目
     */
    class RankEntry {
        /** 排名 */
        private Integer rank;
        /** 玩家ID */
        private Long playerId;
        /** 玩家名称 */
        private String playerName;
        /** 玩家头像 */
        private String playerAvatar;
        /** 玩家等级 */
        private Integer playerLevel;
        /** 分数 */
        private Long score;
        /** 更新时间 */
        private Long updateTime;
        /** 附加数据 */
        private Map<String, Object> extraData;

        // Getters and Setters
        public Integer getRank() { return rank; }
        public void setRank(Integer rank) { this.rank = rank; }
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public String getPlayerAvatar() { return playerAvatar; }
        public void setPlayerAvatar(String playerAvatar) { this.playerAvatar = playerAvatar; }
        public Integer getPlayerLevel() { return playerLevel; }
        public void setPlayerLevel(Integer playerLevel) { this.playerLevel = playerLevel; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
        public Map<String, Object> getExtraData() { return extraData; }
        public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }
    }

    /**
     * 玩家排名信息
     */
    class PlayerRankInfo {
        /** 玩家ID */
        private Long playerId;
        /** 当前排名 */
        private Integer currentRank;
        /** 分数 */
        private Long score;
        /** 最高排名 */
        private Integer bestRank;
        /** 最高分数 */
        private Long bestScore;
        /** 更新时间 */
        private Long updateTime;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Integer getCurrentRank() { return currentRank; }
        public void setCurrentRank(Integer currentRank) { this.currentRank = currentRank; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Integer getBestRank() { return bestRank; }
        public void setBestRank(Integer bestRank) { this.bestRank = bestRank; }
        public Long getBestScore() { return bestScore; }
        public void setBestScore(Long bestScore) { this.bestScore = bestScore; }
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
    }

    /**
     * 排行榜配置
     */
    class RankConfig {
        /** 排行榜类型 */
        private RankType rankType;
        /** 排行榜名称 */
        private String rankName;
        /** 排行榜描述 */
        private String description;
        /** 排序类型 */
        private SortType sortType;
        /** 最大排名数 */
        private Integer maxRankCount;
        /** 是否启用 */
        private Boolean enabled;
        /** 更新频率（秒） */
        private Integer updateInterval;
        /** 是否支持赛季 */
        private Boolean seasonEnabled;
        /** 扩展配置 */
        private Map<String, Object> extraConfig;

        // Getters and Setters
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public String getRankName() { return rankName; }
        public void setRankName(String rankName) { this.rankName = rankName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public SortType getSortType() { return sortType; }
        public void setSortType(SortType sortType) { this.sortType = sortType; }
        public Integer getMaxRankCount() { return maxRankCount; }
        public void setMaxRankCount(Integer maxRankCount) { this.maxRankCount = maxRankCount; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getUpdateInterval() { return updateInterval; }
        public void setUpdateInterval(Integer updateInterval) { this.updateInterval = updateInterval; }
        public Boolean getSeasonEnabled() { return seasonEnabled; }
        public void setSeasonEnabled(Boolean seasonEnabled) { this.seasonEnabled = seasonEnabled; }
        public Map<String, Object> getExtraConfig() { return extraConfig; }
        public void setExtraConfig(Map<String, Object> extraConfig) { this.extraConfig = extraConfig; }
    }

    /**
     * 排行榜信息
     */
    class RankInfo {
        /** 排行榜类型 */
        private RankType rankType;
        /** 排行榜名称 */
        private String rankName;
        /** 总人数 */
        private Integer totalCount;
        /** 最后更新时间 */
        private Long lastUpdateTime;
        /** 是否启用 */
        private Boolean enabled;

        // Getters and Setters
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public String getRankName() { return rankName; }
        public void setRankName(String rankName) { this.rankName = rankName; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 排行榜快照
     */
    class RankSnapshot {
        /** 快照ID */
        private Long snapshotId;
        /** 排行榜类型 */
        private RankType rankType;
        /** 快照名称 */
        private String snapshotName;
        /** 快照描述 */
        private String description;
        /** 记录总数 */
        private Integer totalCount;
        /** 创建时间 */
        private Long createTime;

        // Getters and Setters
        public Long getSnapshotId() { return snapshotId; }
        public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public String getSnapshotName() { return snapshotName; }
        public void setSnapshotName(String snapshotName) { this.snapshotName = snapshotName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }

    /**
     * 排行榜统计信息
     */
    class RankStatistics {
        /** 排行榜类型 */
        private RankType rankType;
        /** 总参与人数 */
        private Integer totalParticipants;
        /** 最高分数 */
        private Long highestScore;
        /** 最低分数 */
        private Long lowestScore;
        /** 平均分数 */
        private Double averageScore;
        /** 今日新增人数 */
        private Integer todayNewParticipants;
        /** 最后更新时间 */
        private Long lastUpdateTime;

        // Getters and Setters
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public Integer getTotalParticipants() { return totalParticipants; }
        public void setTotalParticipants(Integer totalParticipants) { this.totalParticipants = totalParticipants; }
        public Long getHighestScore() { return highestScore; }
        public void setHighestScore(Long highestScore) { this.highestScore = highestScore; }
        public Long getLowestScore() { return lowestScore; }
        public void setLowestScore(Long lowestScore) { this.lowestScore = lowestScore; }
        public Double getAverageScore() { return averageScore; }
        public void setAverageScore(Double averageScore) { this.averageScore = averageScore; }
        public Integer getTodayNewParticipants() { return todayNewParticipants; }
        public void setTodayNewParticipants(Integer todayNewParticipants) { this.todayNewParticipants = todayNewParticipants; }
        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }

    /**
     * 排名历史记录条目
     */
    class RankHistoryEntry {
        /** 玩家ID */
        private Long playerId;
        /** 排名 */
        private Integer rank;
        /** 分数 */
        private Long score;
        /** 记录时间 */
        private Long recordTime;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Integer getRank() { return rank; }
        public void setRank(Integer rank) { this.rank = rank; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Long getRecordTime() { return recordTime; }
        public void setRecordTime(Long recordTime) { this.recordTime = recordTime; }
    }

    /**
     * 排行榜趋势数据
     */
    class RankTrend {
        /** 排行榜类型 */
        private RankType rankType;
        /** 趋势数据点 */
        private List<TrendPoint> trendPoints;
        /** 统计开始时间 */
        private Long startTime;
        /** 统计结束时间 */
        private Long endTime;

        // Getters and Setters
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public List<TrendPoint> getTrendPoints() { return trendPoints; }
        public void setTrendPoints(List<TrendPoint> trendPoints) { this.trendPoints = trendPoints; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
    }

    /**
     * 趋势数据点
     */
    class TrendPoint {
        /** 时间点 */
        private Long timestamp;
        /** 参与人数 */
        private Integer participantCount;
        /** 平均分数 */
        private Double averageScore;
        /** 最高分数 */
        private Long highestScore;

        // Getters and Setters
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public Integer getParticipantCount() { return participantCount; }
        public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
        public Double getAverageScore() { return averageScore; }
        public void setAverageScore(Double averageScore) { this.averageScore = averageScore; }
        public Long getHighestScore() { return highestScore; }
        public void setHighestScore(Long highestScore) { this.highestScore = highestScore; }
    }

    /**
     * 赛季配置
     */
    class SeasonConfig {
        /** 排行榜类型 */
        private RankType rankType;
        /** 赛季名称 */
        private String seasonName;
        /** 赛季描述 */
        private String description;
        /** 开始时间 */
        private Long startTime;
        /** 结束时间 */
        private Long endTime;
        /** 奖励配置 */
        private Map<String, Object> rewardConfig;

        // Getters and Setters
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public String getSeasonName() { return seasonName; }
        public void setSeasonName(String seasonName) { this.seasonName = seasonName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public Map<String, Object> getRewardConfig() { return rewardConfig; }
        public void setRewardConfig(Map<String, Object> rewardConfig) { this.rewardConfig = rewardConfig; }
    }

    /**
     * 赛季信息
     */
    class SeasonInfo {
        /** 赛季ID */
        private Long seasonId;
        /** 排行榜类型 */
        private RankType rankType;
        /** 赛季名称 */
        private String seasonName;
        /** 赛季状态 */
        private String status;
        /** 开始时间 */
        private Long startTime;
        /** 结束时间 */
        private Long endTime;
        /** 参与人数 */
        private Integer participantCount;

        // Getters and Setters
        public Long getSeasonId() { return seasonId; }
        public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
        public RankType getRankType() { return rankType; }
        public void setRankType(RankType rankType) { this.rankType = rankType; }
        public String getSeasonName() { return seasonName; }
        public void setSeasonName(String seasonName) { this.seasonName = seasonName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public Integer getParticipantCount() { return participantCount; }
        public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
    }
}