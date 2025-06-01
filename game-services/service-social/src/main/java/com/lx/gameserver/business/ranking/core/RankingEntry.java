/*
 * 文件名: RankingEntry.java
 * 用途: 排行榜条目数据结构
 * 实现内容:
 *   - 定义排行榜单条目的数据结构
 *   - 包含实体ID、排名、分数等核心信息
 *   - 支持附加信息和排名变化记录
 *   - 提供数据验证和比较功能
 * 技术选型:
 *   - 使用POJO类实现数据传输
 *   - 支持JSON序列化和反序列化
 *   - 实现Comparable接口支持排序
 * 依赖关系:
 *   - 被排行榜核心组件使用
 *   - 被存储和查询模块引用
 *   - 被API接口层传输
 */
package com.lx.gameserver.business.ranking.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 排行榜条目
 * <p>
 * 排行榜中的单个条目，包含实体的排名信息、分数、
 * 额外数据等。支持排名变化追踪和历史记录。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RankingEntry implements Comparable<RankingEntry> {

    /**
     * 实体ID（玩家ID、公会ID等）
     */
    private Long entityId;

    /**
     * 当前排名
     */
    private Integer rank;

    /**
     * 分数
     */
    private Long score;

    /**
     * 实体名称（玩家名称、公会名称等）
     */
    private String entityName;

    /**
     * 实体等级
     */
    private Integer entityLevel;

    /**
     * 实体头像或图标
     */
    private String entityAvatar;

    /**
     * 服务器ID
     */
    private Integer serverId;

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 上榜时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime entryTime;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 之前排名
     */
    private Integer previousRank;

    /**
     * 排名变化（正数表示上升，负数表示下降）
     */
    private Integer rankChange;

    /**
     * 最高历史排名
     */
    private Integer bestRank;

    /**
     * 最高历史分数
     */
    private Long bestScore;

    /**
     * 连续上榜天数
     */
    private Integer consecutiveDays;

    /**
     * 额外信息（JSON格式存储）
     */
    private Map<String, Object> extraData;

    /**
     * 是否为新上榜
     */
    private Boolean isNewEntry;

    /**
     * 版本号（用于乐观锁）
     */
    private Long version;

    /**
     * 构造函数（基础信息）
     *
     * @param entityId 实体ID
     * @param rank     排名
     * @param score    分数
     */
    public RankingEntry(Long entityId, Integer rank, Long score) {
        this.entityId = entityId;
        this.rank = rank;
        this.score = score;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 构造函数（完整信息）
     *
     * @param entityId   实体ID
     * @param rank       排名
     * @param score      分数
     * @param entityName 实体名称
     */
    public RankingEntry(Long entityId, Integer rank, Long score, String entityName) {
        this(entityId, rank, score);
        this.entityName = entityName;
    }

    /**
     * 更新排名信息
     *
     * @param newRank  新排名
     * @param newScore 新分数
     */
    public void updateRank(Integer newRank, Long newScore) {
        this.previousRank = this.rank;
        this.rank = newRank;
        this.score = newScore;
        this.updateTime = LocalDateTime.now();
        
        // 计算排名变化
        if (this.previousRank != null && newRank != null) {
            this.rankChange = this.previousRank - newRank; // 正数表示上升
        }
        
        // 更新最佳记录
        if (this.bestRank == null || (newRank != null && newRank < this.bestRank)) {
            this.bestRank = newRank;
        }
        if (this.bestScore == null || (newScore != null && newScore > this.bestScore)) {
            this.bestScore = newScore;
        }
    }

    /**
     * 设置额外数据
     *
     * @param key   键
     * @param value 值
     */
    public void setExtraData(String key, Object value) {
        if (this.extraData == null) {
            this.extraData = new java.util.HashMap<>();
        }
        this.extraData.put(key, value);
    }

    /**
     * 获取额外数据
     *
     * @param key 键
     * @return 值
     */
    public Object getExtraData(String key) {
        return this.extraData != null ? this.extraData.get(key) : null;
    }

    /**
     * 检查数据有效性
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return entityId != null && entityId > 0 
               && rank != null && rank > 0 
               && score != null && score >= 0;
    }

    /**
     * 是否有排名变化
     *
     * @return 是否有排名变化
     */
    public boolean hasRankChange() {
        return rankChange != null && rankChange != 0;
    }

    /**
     * 排名是否上升
     *
     * @return 排名是否上升
     */
    public boolean isRankUp() {
        return rankChange != null && rankChange > 0;
    }

    /**
     * 排名是否下降
     *
     * @return 排名是否下降
     */
    public boolean isRankDown() {
        return rankChange != null && rankChange < 0;
    }

    /**
     * 获取排名变化描述
     *
     * @return 排名变化描述
     */
    public String getRankChangeDescription() {
        if (rankChange == null || rankChange == 0) {
            return "无变化";
        } else if (rankChange > 0) {
            return "上升" + rankChange + "名";
        } else {
            return "下降" + Math.abs(rankChange) + "名";
        }
    }

    /**
     * 比较两个排行榜条目
     * 首先按分数降序，然后按更新时间升序
     *
     * @param other 另一个条目
     * @return 比较结果
     */
    @Override
    public int compareTo(RankingEntry other) {
        if (other == null) {
            return -1;
        }
        
        // 首先按分数降序排列
        int scoreCompare = Long.compare(other.score != null ? other.score : 0, 
                                       this.score != null ? this.score : 0);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        
        // 分数相同时按更新时间升序排列（早更新的排前面）
        if (this.updateTime != null && other.updateTime != null) {
            return this.updateTime.compareTo(other.updateTime);
        }
        
        // 最后按实体ID升序排列
        return Long.compare(this.entityId != null ? this.entityId : 0,
                           other.entityId != null ? other.entityId : 0);
    }

    /**
     * 创建副本
     *
     * @return 副本对象
     */
    public RankingEntry copy() {
        return RankingEntry.builder()
                .entityId(this.entityId)
                .rank(this.rank)
                .score(this.score)
                .entityName(this.entityName)
                .entityLevel(this.entityLevel)
                .entityAvatar(this.entityAvatar)
                .serverId(this.serverId)
                .serverName(this.serverName)
                .entryTime(this.entryTime)
                .updateTime(this.updateTime)
                .previousRank(this.previousRank)
                .rankChange(this.rankChange)
                .bestRank(this.bestRank)
                .bestScore(this.bestScore)
                .consecutiveDays(this.consecutiveDays)
                .extraData(this.extraData != null ? new java.util.HashMap<>(this.extraData) : null)
                .isNewEntry(this.isNewEntry)
                .version(this.version)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RankingEntry that = (RankingEntry) o;
        return Objects.equals(entityId, that.entityId) &&
               Objects.equals(rank, that.rank) &&
               Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, rank, score);
    }

    @Override
    public String toString() {
        return String.format("RankingEntry{entityId=%d, rank=%d, score=%d, entityName='%s'}", 
                           entityId, rank, score, entityName);
    }
}