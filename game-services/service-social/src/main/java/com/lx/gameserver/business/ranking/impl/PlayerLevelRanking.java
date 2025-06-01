/*
 * 文件名: PlayerLevelRanking.java
 * 用途: 玩家等级排行榜实现
 * 实现内容:
 *   - 基于玩家等级的排行榜
 *   - 等级相同时按经验值排序
 *   - 支持等级变化的实时更新
 *   - 提供等级榜特定的查询功能
 * 技术选型:
 *   - 继承BaseRanking基础实现
 *   - 使用复合分数计算（等级*1000000+经验）
 *   - 支持玩家信息缓存
 * 依赖关系:
 *   - 继承BaseRanking类
 *   - 需要玩家数据服务提供玩家信息
 *   - 被排行榜管理器管理
 */
package com.lx.gameserver.business.ranking.impl;

import com.lx.gameserver.business.ranking.core.RankingType;
import com.lx.gameserver.business.ranking.core.RankingScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家等级排行榜
 * <p>
 * 基于玩家等级进行排名的排行榜实现。等级高的玩家排名靠前，
 * 等级相同时按经验值排序。支持实时等级变化更新。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class PlayerLevelRanking extends BaseRanking {

    /**
     * 经验值权重因子（确保等级差异大于经验值差异）
     */
    private static final long LEVEL_WEIGHT = 1000000L;

    public PlayerLevelRanking() {
        super("player_level", "玩家等级排行榜", RankingType.LEVEL);
        
        // 设置排行榜配置
        setRankingScope(RankingScope.SERVER);
        setCapacity(1000);
        setDescending(true); // 分数越高排名越前
        setUpdateStrategy(UpdateStrategy.REALTIME);
        setHistoryEnabled(true);
        setSeasonEnabled(false);
        
        log.info("创建玩家等级排行榜");
    }

    /**
     * 计算玩家等级分数
     * 分数 = 等级 * 1000000 + 经验值
     * 这样确保等级1的差异远大于经验值的差异
     *
     * @param playerId   玩家ID
     * @param playerData 玩家数据
     * @return 计算后的分数
     */
    @Override
    protected Long calculateScore(Long playerId, Map<String, Object> playerData) {
        if (playerData == null) {
            log.warn("玩家数据为空: playerId={}", playerId);
            return 0L;
        }

        try {
            Integer level = (Integer) playerData.get("level");
            Long experience = (Long) playerData.getOrDefault("experience", 0L);

            if (level == null || level <= 0) {
                log.warn("玩家等级无效: playerId={}, level={}", playerId, level);
                return 0L;
            }

            long score = level * LEVEL_WEIGHT + (experience != null ? experience : 0L);
            
            log.debug("计算玩家等级分数: playerId={}, level={}, exp={}, score={}", 
                     playerId, level, experience, score);
            
            return score;
            
        } catch (Exception e) {
            log.error("计算玩家等级分数失败: playerId=" + playerId, e);
            return 0L;
        }
    }

    /**
     * 获取玩家信息
     * 从玩家数据服务获取玩家的基本信息
     *
     * @param playerId 玩家ID
     * @return 玩家信息
     */
    @Override
    protected Map<String, Object> getEntityInfo(Long playerId) {
        // TODO: 这里应该调用实际的玩家数据服务
        // 现在提供模拟数据
        Map<String, Object> playerInfo = new HashMap<>();
        
        try {
            // 模拟从数据库或缓存中获取玩家信息
            playerInfo.put("name", "Player" + playerId);
            playerInfo.put("level", 50 + (playerId % 50)); // 模拟等级50-99
            playerInfo.put("experience", playerId * 1000 % 999999); // 模拟经验值
            playerInfo.put("avatar", "avatar_" + (playerId % 10) + ".png");
            playerInfo.put("serverId", 1001);
            playerInfo.put("serverName", "测试服务器");
            playerInfo.put("vipLevel", playerId % 10);
            playerInfo.put("guildId", playerId % 100 == 0 ? null : playerId / 10);
            playerInfo.put("lastLoginTime", System.currentTimeMillis() - (playerId % 86400000));
            
            log.debug("获取玩家信息: playerId={}, info={}", playerId, playerInfo);
            
        } catch (Exception e) {
            log.error("获取玩家信息失败: playerId=" + playerId, e);
        }
        
        return playerInfo;
    }

    /**
     * 验证玩家是否有效
     * 检查玩家是否符合上榜条件
     *
     * @param playerId   玩家ID
     * @param playerData 玩家数据
     * @return 是否有效
     */
    @Override
    protected boolean isEntityValid(Long playerId, Map<String, Object> playerData) {
        if (playerId == null || playerId <= 0) {
            return false;
        }

        if (playerData == null || playerData.isEmpty()) {
            log.warn("玩家数据为空，不符合上榜条件: playerId={}", playerId);
            return false;
        }

        try {
            Integer level = (Integer) playerData.get("level");
            
            // 检查等级是否有效
            if (level == null || level <= 0) {
                log.warn("玩家等级无效，不符合上榜条件: playerId={}, level={}", playerId, level);
                return false;
            }

            // 检查等级是否达到上榜要求（例如至少10级）
            if (level < 10) {
                log.debug("玩家等级过低，不符合上榜条件: playerId={}, level={}", playerId, level);
                return false;
            }

            // 可以添加更多验证条件，如：
            // - 是否被封号
            // - 是否在线时间足够
            // - 是否完成新手引导等

            return true;
            
        } catch (Exception e) {
            log.error("验证玩家有效性失败: playerId=" + playerId, e);
            return false;
        }
    }

    /**
     * 获取玩家等级（从分数反推）
     *
     * @param score 分数
     * @return 玩家等级
     */
    public int getLevelFromScore(long score) {
        return (int) (score / LEVEL_WEIGHT);
    }

    /**
     * 获取玩家经验（从分数反推）
     *
     * @param score 分数
     * @return 玩家经验
     */
    public long getExperienceFromScore(long score) {
        return score % LEVEL_WEIGHT;
    }

    /**
     * 根据等级范围获取排名
     *
     * @param minLevel 最小等级
     * @param maxLevel 最大等级
     * @return 排行榜条目列表
     */
    public java.util.List<com.lx.gameserver.business.ranking.core.RankingEntry> getEntriesByLevelRange(int minLevel, int maxLevel) {
        if (!isRunning() || minLevel > maxLevel || minLevel <= 0) {
            return java.util.Collections.emptyList();
        }

        try {
            long minScore = minLevel * LEVEL_WEIGHT;
            long maxScore = (maxLevel + 1) * LEVEL_WEIGHT - 1;
            
            return storage.getEntriesByScoreRange(rankingId, minScore, maxScore);
            
        } catch (Exception e) {
            log.error("根据等级范围查询失败: minLevel=" + minLevel + ", maxLevel=" + maxLevel, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 获取指定等级的玩家数量
     *
     * @param level 等级
     * @return 玩家数量
     */
    public int getPlayerCountByLevel(int level) {
        if (!isRunning() || level <= 0) {
            return 0;
        }

        try {
            long minScore = level * LEVEL_WEIGHT;
            long maxScore = (level + 1) * LEVEL_WEIGHT - 1;
            
            return storage.getEntriesByScoreRange(rankingId, minScore, maxScore).size();
            
        } catch (Exception e) {
            log.error("获取等级玩家数量失败: level=" + level, e);
            return 0;
        }
    }

    @Override
    protected void onInitialized() {
        log.info("玩家等级排行榜初始化完成: {}", rankingName);
        
        // 可以在这里进行特定的初始化操作
        // 例如：预加载热门玩家数据、设置定时任务等
    }

    @Override
    protected void onDestroyed() {
        log.info("玩家等级排行榜销毁完成: {}", rankingName);
        
        // 可以在这里进行特定的清理操作
    }

    @Override
    protected void handleRankChangeEvent(com.lx.gameserver.business.ranking.core.RankingEntry oldEntry, 
                                       com.lx.gameserver.business.ranking.core.RankingEntry newEntry) {
        super.handleRankChangeEvent(oldEntry, newEntry);
        
        // 处理等级排行榜特定的排名变化事件
        if (newEntry != null) {
            int level = getLevelFromScore(newEntry.getScore());
            long experience = getExperienceFromScore(newEntry.getScore());
            
            log.debug("玩家等级排名变化: playerId={}, level={}, experience={}, rank={}", 
                     newEntry.getEntityId(), level, experience, newEntry.getRank());
            
            // 可以在这里发送通知、更新成就等
            // 例如：
            // - 发送排名变化通知给玩家
            // - 检查是否达成排名相关成就
            // - 更新公会排名统计等
        }
    }

    /**
     * 更新玩家等级和经验
     * 提供便捷的等级更新方法
     *
     * @param playerId   玩家ID
     * @param level      新等级
     * @param experience 新经验值
     * @return 更新结果
     */
    public java.util.concurrent.CompletableFuture<SubmitResult> updatePlayerLevel(Long playerId, int level, long experience) {
        if (playerId == null || level <= 0 || experience < 0) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                SubmitResult.failure("参数无效"));
        }

        long score = level * LEVEL_WEIGHT + experience;
        
        // 创建包含等级和经验的额外数据
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("level", level);
        extraData.put("experience", experience);
        
        return submitScore(playerId, score, extraData);
    }
}