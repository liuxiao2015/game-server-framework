/*
 * 文件名: PlayerPowerRanking.java
 * 用途: 玩家战力排行榜实现
 * 实现内容:
 *   - 基于玩家战力值的排行榜
 *   - 支持战力变化的实时更新
 *   - 提供战力榜特定的查询功能
 *   - 支持赛季模式和历史记录
 * 技术选型:
 *   - 继承BaseRanking基础实现
 *   - 直接使用战力值作为分数
 *   - 支持战力变化事件通知
 * 依赖关系:
 *   - 继承BaseRanking类
 *   - 需要玩家数据服务提供战力信息
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
 * 玩家战力排行榜
 * <p>
 * 基于玩家战力值进行排名的排行榜实现。战力值越高排名越靠前，
 * 支持实时战力变化更新和赛季重置功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class PlayerPowerRanking extends BaseRanking {

    /**
     * 战力榜上榜最低战力要求
     */
    private static final long MIN_POWER_REQUIREMENT = 1000L;

    /**
     * 战力变化通知阈值（战力变化超过此值才通知）
     */
    private static final long POWER_CHANGE_THRESHOLD = 10000L;

    public PlayerPowerRanking() {
        super("player_power", "玩家战力排行榜", RankingType.POWER);
        
        // 设置排行榜配置
        setRankingScope(RankingScope.SERVER);
        setCapacity(500); // 战力榜通常容量较小
        setDescending(true); // 战力越高排名越前
        setUpdateStrategy(UpdateStrategy.REALTIME);
        setHistoryEnabled(true);
        setSeasonEnabled(true); // 支持赛季模式
        
        log.info("创建玩家战力排行榜");
    }

    /**
     * 计算玩家战力分数
     * 直接使用战力值作为分数
     *
     * @param playerId   玩家ID
     * @param playerData 玩家数据
     * @return 战力值
     */
    @Override
    protected Long calculateScore(Long playerId, Map<String, Object> playerData) {
        if (playerData == null) {
            log.warn("玩家数据为空: playerId={}", playerId);
            return 0L;
        }

        try {
            Long power = (Long) playerData.get("power");
            
            if (power == null || power < 0) {
                log.warn("玩家战力无效: playerId={}, power={}", playerId, power);
                return 0L;
            }

            log.debug("计算玩家战力分数: playerId={}, power={}", playerId, power);
            return power;
            
        } catch (Exception e) {
            log.error("计算玩家战力分数失败: playerId=" + playerId, e);
            return 0L;
        }
    }

    /**
     * 获取玩家信息
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
            playerInfo.put("level", 30 + (playerId % 70)); // 模拟等级30-99
            playerInfo.put("power", 10000L + playerId * 1000 + (playerId % 50000)); // 模拟战力
            playerInfo.put("avatar", "avatar_" + (playerId % 20) + ".png");
            playerInfo.put("serverId", 1001);
            playerInfo.put("serverName", "测试服务器");
            playerInfo.put("vipLevel", playerId % 15);
            playerInfo.put("guildId", playerId % 100 == 0 ? null : playerId / 10);
            playerInfo.put("guildName", playerId % 100 == 0 ? null : "公会" + (playerId / 10));
            playerInfo.put("profession", (int)(playerId % 5) + 1); // 职业1-5
            playerInfo.put("lastLoginTime", System.currentTimeMillis() - (playerId % 86400000));
            
            log.debug("获取玩家信息: playerId={}, power={}", playerId, playerInfo.get("power"));
            
        } catch (Exception e) {
            log.error("获取玩家信息失败: playerId=" + playerId, e);
        }
        
        return playerInfo;
    }

    /**
     * 验证玩家是否有效
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
            Long power = (Long) playerData.get("power");
            
            // 检查战力是否有效
            if (power == null || power < 0) {
                log.warn("玩家战力无效，不符合上榜条件: playerId={}, power={}", playerId, power);
                return false;
            }

            // 检查战力是否达到上榜要求
            if (power < MIN_POWER_REQUIREMENT) {
                log.debug("玩家战力过低，不符合上榜条件: playerId={}, power={}, required={}", 
                         playerId, power, MIN_POWER_REQUIREMENT);
                return false;
            }

            // 检查玩家等级（战力榜通常要求一定等级）
            Integer level = (Integer) playerData.get("level");
            if (level == null || level < 20) {
                log.debug("玩家等级过低，不符合战力榜条件: playerId={}, level={}", playerId, level);
                return false;
            }

            return true;
            
        } catch (Exception e) {
            log.error("验证玩家有效性失败: playerId=" + playerId, e);
            return false;
        }
    }

    /**
     * 根据战力范围获取排名
     *
     * @param minPower 最小战力
     * @param maxPower 最大战力
     * @return 排行榜条目列表
     */
    public java.util.List<com.lx.gameserver.business.ranking.core.RankingEntry> getEntriesByPowerRange(long minPower, long maxPower) {
        if (!isRunning() || minPower > maxPower || minPower < 0) {
            return java.util.Collections.emptyList();
        }

        try {
            return storage.getEntriesByScoreRange(rankingId, minPower, maxPower);
        } catch (Exception e) {
            log.error("根据战力范围查询失败: minPower=" + minPower + ", maxPower=" + maxPower, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 获取战力区间的玩家数量
     *
     * @param minPower 最小战力
     * @param maxPower 最大战力
     * @return 玩家数量
     */
    public int getPlayerCountByPowerRange(long minPower, long maxPower) {
        if (!isRunning() || minPower > maxPower || minPower < 0) {
            return 0;
        }

        try {
            return storage.getEntriesByScoreRange(rankingId, minPower, maxPower).size();
        } catch (Exception e) {
            log.error("获取战力区间玩家数量失败: minPower=" + minPower + ", maxPower=" + maxPower, e);
            return 0;
        }
    }

    /**
     * 获取指定职业的战力排名
     *
     * @param profession 职业ID
     * @param topN      前N名
     * @return 排行榜条目列表
     */
    public java.util.List<com.lx.gameserver.business.ranking.core.RankingEntry> getTopByProfession(int profession, int topN) {
        if (!isRunning() || profession <= 0 || topN <= 0) {
            return java.util.Collections.emptyList();
        }

        try {
            // 获取更多数据然后过滤（简化实现，实际应该在存储层优化）
            java.util.List<com.lx.gameserver.business.ranking.core.RankingEntry> allEntries = getTopEntries(Math.min(topN * 5, capacity));
            
            return allEntries.stream()
                .filter(entry -> {
                    Map<String, Object> entityInfo = getEntityInfo(entry.getEntityId());
                    Integer playerProfession = (Integer) entityInfo.get("profession");
                    return playerProfession != null && playerProfession == profession;
                })
                .limit(topN)
                .collect(java.util.stream.Collectors.toList());
                
        } catch (Exception e) {
            log.error("获取职业战力排名失败: profession=" + profession + ", topN=" + topN, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 获取战力统计信息
     *
     * @return 战力统计
     */
    public PowerStatistics getPowerStatistics() {
        if (!isRunning()) {
            return new PowerStatistics(0, 0L, 0L, 0.0);
        }

        try {
            com.lx.gameserver.business.ranking.storage.RankingStorage.ScoreStatistics stats = storage.getScoreStatistics(rankingId);
            
            return new PowerStatistics(
                stats.getCount(),
                stats.getMinScore(),
                stats.getMaxScore(),
                stats.getAvgScore()
            );
            
        } catch (Exception e) {
            log.error("获取战力统计信息失败", e);
            return new PowerStatistics(0, 0L, 0L, 0.0);
        }
    }

    @Override
    protected void onInitialized() {
        log.info("玩家战力排行榜初始化完成: {}", rankingName);
        
        // 设置战力榜特定配置
        setExtraConfig("minPowerRequirement", MIN_POWER_REQUIREMENT);
        setExtraConfig("powerChangeThreshold", POWER_CHANGE_THRESHOLD);
        setExtraConfig("supportProfessionRanking", true);
    }

    @Override
    protected void onDestroyed() {
        log.info("玩家战力排行榜销毁完成: {}", rankingName);
    }

    @Override
    protected void handleRankChangeEvent(com.lx.gameserver.business.ranking.core.RankingEntry oldEntry, 
                                       com.lx.gameserver.business.ranking.core.RankingEntry newEntry) {
        super.handleRankChangeEvent(oldEntry, newEntry);
        
        // 处理战力排行榜特定的排名变化事件
        if (newEntry != null) {
            long newPower = newEntry.getScore();
            long oldPower = oldEntry != null ? oldEntry.getScore() : 0L;
            long powerChange = newPower - oldPower;
            
            log.debug("玩家战力排名变化: playerId={}, oldPower={}, newPower={}, change={}, rank={}", 
                     newEntry.getEntityId(), oldPower, newPower, powerChange, newEntry.getRank());
            
            // 如果战力变化较大，发送特殊通知
            if (Math.abs(powerChange) >= POWER_CHANGE_THRESHOLD) {
                log.info("玩家战力大幅变化: playerId={}, powerChange={}, newRank={}", 
                        newEntry.getEntityId(), powerChange, newEntry.getRank());
                
                // 这里可以发送特殊通知、记录日志、触发成就等
                handleSignificantPowerChange(newEntry, powerChange);
            }
            
            // 检查是否进入前10名
            if (newEntry.getRank() != null && newEntry.getRank() <= 10) {
                if (oldEntry == null || oldEntry.getRank() == null || oldEntry.getRank() > 10) {
                    log.info("玩家进入战力前10名: playerId={}, power={}, rank={}", 
                            newEntry.getEntityId(), newPower, newEntry.getRank());
                    
                    handleTopRankAchievement(newEntry);
                }
            }
        }
    }

    /**
     * 处理战力大幅变化事件
     *
     * @param entry       排行榜条目
     * @param powerChange 战力变化值
     */
    private void handleSignificantPowerChange(com.lx.gameserver.business.ranking.core.RankingEntry entry, long powerChange) {
        // 可以在这里实现：
        // 1. 发送系统通知
        // 2. 记录战力变化日志
        // 3. 检查是否有异常（防作弊）
        // 4. 更新玩家成就
        // 5. 通知公会成员等
    }

    /**
     * 处理进入前排名成就
     *
     * @param entry 排行榜条目
     */
    private void handleTopRankAchievement(com.lx.gameserver.business.ranking.core.RankingEntry entry) {
        // 可以在这里实现：
        // 1. 发放排名奖励
        // 2. 更新成就系统
        // 3. 全服公告
        // 4. 记录荣誉等
    }

    /**
     * 更新玩家战力
     * 提供便捷的战力更新方法
     *
     * @param playerId 玩家ID
     * @param power    新战力值
     * @return 更新结果
     */
    public java.util.concurrent.CompletableFuture<SubmitResult> updatePlayerPower(Long playerId, long power) {
        if (playerId == null || power < 0) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                SubmitResult.failure("参数无效"));
        }

        // 创建包含战力的额外数据
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("power", power);
        extraData.put("updateTime", System.currentTimeMillis());
        
        return submitScore(playerId, power, extraData);
    }

    /**
     * 战力统计信息
     */
    public static class PowerStatistics {
        private final long playerCount;
        private final Long minPower;
        private final Long maxPower;
        private final Double avgPower;

        public PowerStatistics(long playerCount, Long minPower, Long maxPower, Double avgPower) {
            this.playerCount = playerCount;
            this.minPower = minPower;
            this.maxPower = maxPower;
            this.avgPower = avgPower;
        }

        public long getPlayerCount() { return playerCount; }
        public Long getMinPower() { return minPower; }
        public Long getMaxPower() { return maxPower; }
        public Double getAvgPower() { return avgPower; }

        @Override
        public String toString() {
            return String.format("PowerStatistics{count=%d, min=%d, max=%d, avg=%.2f}",
                               playerCount, minPower, maxPower, avgPower);
        }
    }
}