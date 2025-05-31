/*
 * 文件名: PlayerService.java
 * 用途: 玩家服务接口
 * 实现内容:
 *   - 玩家数据的CRUD操作接口
 *   - 玩家创建和属性修改服务
 *   - 数据查询和跨服务调用
 *   - 事务处理和数据一致性保证
 *   - 批量操作和性能优化接口
 * 技术选型:
 *   - 接口抽象定义服务规范
 *   - 支持事务管理和并发控制
 *   - 分页查询和条件查询
 *   - 缓存集成和性能优化
 * 依赖关系:
 *   - 被PlayerManager和其他模块调用
 *   - 与数据访问层协作
 *   - 提供统一的玩家数据服务
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.player;

import com.lx.gameserver.common.PageResult;
import com.lx.gameserver.common.Result;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 玩家服务接口
 * <p>
 * 定义了玩家数据管理的核心接口，包括玩家的创建、查询、
 * 更新、删除等操作。提供事务支持和批量操作能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface PlayerService {

    /**
     * 玩家查询条件
     */
    class PlayerQuery {
        private String username;
        private String nickname;
        private Integer minLevel;
        private Integer maxLevel;
        private Player.PlayerState state;
        private Long guildId;
        private LocalDateTime createTimeStart;
        private LocalDateTime createTimeEnd;
        private LocalDateTime lastLoginTimeStart;
        private LocalDateTime lastLoginTimeEnd;
        private Boolean banned;
        private Integer vipLevel;
        private String orderBy = "createTime";
        private String orderDirection = "DESC";

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        
        public Integer getMinLevel() { return minLevel; }
        public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
        
        public Integer getMaxLevel() { return maxLevel; }
        public void setMaxLevel(Integer maxLevel) { this.maxLevel = maxLevel; }
        
        public Player.PlayerState getState() { return state; }
        public void setState(Player.PlayerState state) { this.state = state; }
        
        public Long getGuildId() { return guildId; }
        public void setGuildId(Long guildId) { this.guildId = guildId; }
        
        public LocalDateTime getCreateTimeStart() { return createTimeStart; }
        public void setCreateTimeStart(LocalDateTime createTimeStart) { this.createTimeStart = createTimeStart; }
        
        public LocalDateTime getCreateTimeEnd() { return createTimeEnd; }
        public void setCreateTimeEnd(LocalDateTime createTimeEnd) { this.createTimeEnd = createTimeEnd; }
        
        public LocalDateTime getLastLoginTimeStart() { return lastLoginTimeStart; }
        public void setLastLoginTimeStart(LocalDateTime lastLoginTimeStart) { this.lastLoginTimeStart = lastLoginTimeStart; }
        
        public LocalDateTime getLastLoginTimeEnd() { return lastLoginTimeEnd; }
        public void setLastLoginTimeEnd(LocalDateTime lastLoginTimeEnd) { this.lastLoginTimeEnd = lastLoginTimeEnd; }
        
        public Boolean getBanned() { return banned; }
        public void setBanned(Boolean banned) { this.banned = banned; }
        
        public Integer getVipLevel() { return vipLevel; }
        public void setVipLevel(Integer vipLevel) { this.vipLevel = vipLevel; }
        
        public String getOrderBy() { return orderBy; }
        public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
        
        public String getOrderDirection() { return orderDirection; }
        public void setOrderDirection(String orderDirection) { this.orderDirection = orderDirection; }
    }

    /**
     * 玩家创建请求
     */
    class CreatePlayerRequest {
        private String username;
        private String nickname;
        private Player.Gender gender = Player.Gender.UNKNOWN;
        private String avatarUrl;
        private Map<String, Object> extendedAttributes;

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        
        public Player.Gender getGender() { return gender; }
        public void setGender(Player.Gender gender) { this.gender = gender; }
        
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        
        public Map<String, Object> getExtendedAttributes() { return extendedAttributes; }
        public void setExtendedAttributes(Map<String, Object> extendedAttributes) { this.extendedAttributes = extendedAttributes; }
    }

    // ========== 基础CRUD操作 ==========

    /**
     * 创建玩家
     *
     * @param request 创建请求
     * @return 创建结果
     */
    Result<Player> createPlayer(CreatePlayerRequest request);

    /**
     * 根据ID获取玩家
     *
     * @param playerId 玩家ID
     * @return 玩家信息
     */
    Player getPlayerById(Long playerId);

    /**
     * 根据用户名获取玩家
     *
     * @param username 用户名
     * @return 玩家信息
     */
    Optional<Player> getPlayerByUsername(String username);

    /**
     * 保存玩家
     *
     * @param player 玩家对象
     * @return 操作结果
     */
    Result<Player> savePlayer(Player player);

    /**
     * 更新玩家
     *
     * @param player 玩家对象
     * @return 操作结果
     */
    Result<Player> updatePlayer(Player player);

    /**
     * 删除玩家（软删除）
     *
     * @param playerId 玩家ID
     * @return 操作结果
     */
    Result<Void> deletePlayer(Long playerId);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    // ========== 查询操作 ==========

    /**
     * 分页查询玩家
     *
     * @param query    查询条件
     * @param page     页码（从1开始）
     * @param pageSize 页大小
     * @return 分页结果
     */
    PageResult<Player> queryPlayers(PlayerQuery query, int page, int pageSize);

    /**
     * 根据ID列表批量获取玩家
     *
     * @param playerIds 玩家ID列表
     * @return 玩家列表
     */
    List<Player> getPlayersByIds(List<Long> playerIds);

    /**
     * 根据公会ID获取玩家列表
     *
     * @param guildId 公会ID
     * @return 玩家列表
     */
    List<Player> getPlayersByGuildId(Long guildId);

    /**
     * 获取等级排行榜
     *
     * @param limit 限制数量
     * @return 玩家列表
     */
    List<Player> getLevelRanking(int limit);

    /**
     * 获取财富排行榜
     *
     * @param limit 限制数量
     * @return 玩家列表
     */
    List<Player> getWealthRanking(int limit);

    /**
     * 搜索玩家（模糊查询）
     *
     * @param keyword  关键词
     * @param page     页码
     * @param pageSize 页大小
     * @return 搜索结果
     */
    PageResult<Player> searchPlayers(String keyword, int page, int pageSize);

    // ========== 属性操作 ==========

    /**
     * 更新玩家基础属性
     *
     * @param playerId 玩家ID
     * @param nickname 昵称
     * @param gender   性别
     * @param avatarUrl 头像URL
     * @return 操作结果
     */
    Result<Void> updatePlayerProfile(Long playerId, String nickname, Player.Gender gender, String avatarUrl);

    /**
     * 增加玩家经验
     *
     * @param playerId   玩家ID
     * @param experience 经验值
     * @return 操作结果（包含是否升级信息）
     */
    Result<Map<String, Object>> addPlayerExperience(Long playerId, long experience);

    /**
     * 更新玩家金币
     *
     * @param playerId 玩家ID
     * @param amount   金币数量（可为负数）
     * @return 操作结果
     */
    Result<Void> updatePlayerCoins(Long playerId, long amount);

    /**
     * 更新玩家钻石
     *
     * @param playerId 玩家ID
     * @param amount   钻石数量（可为负数）
     * @return 操作结果
     */
    Result<Void> updatePlayerDiamonds(Long playerId, long amount);

    /**
     * 更新玩家体力
     *
     * @param playerId 玩家ID
     * @param energy   体力值
     * @return 操作结果
     */
    Result<Void> updatePlayerEnergy(Long playerId, int energy);

    /**
     * 更新玩家VIP等级
     *
     * @param playerId 玩家ID
     * @param vipLevel VIP等级
     * @return 操作结果
     */
    Result<Void> updatePlayerVipLevel(Long playerId, int vipLevel);

    /**
     * 设置玩家扩展属性
     *
     * @param playerId 玩家ID
     * @param key      属性键
     * @param value    属性值
     * @return 操作结果
     */
    Result<Void> setPlayerExtendedAttribute(Long playerId, String key, Object value);

    /**
     * 获取玩家扩展属性
     *
     * @param playerId 玩家ID
     * @param key      属性键
     * @return 属性值
     */
    Object getPlayerExtendedAttribute(Long playerId, String key);

    // ========== 状态管理 ==========

    /**
     * 更新玩家状态
     *
     * @param playerId 玩家ID
     * @param state    新状态
     * @return 操作结果
     */
    Result<Void> updatePlayerState(Long playerId, Player.PlayerState state);

    /**
     * 封禁玩家
     *
     * @param playerId    玩家ID
     * @param duration    封禁时长
     * @param reason      封禁原因
     * @param operatorId  操作者ID
     * @return 操作结果
     */
    Result<Void> banPlayer(Long playerId, java.time.Duration duration, String reason, Long operatorId);

    /**
     * 解封玩家
     *
     * @param playerId   玩家ID
     * @param operatorId 操作者ID
     * @return 操作结果
     */
    Result<Void> unbanPlayer(Long playerId, Long operatorId);

    /**
     * 更新玩家场景
     *
     * @param playerId 玩家ID
     * @param sceneId  场景ID
     * @return 操作结果
     */
    Result<Void> updatePlayerScene(Long playerId, Long sceneId);

    /**
     * 更新玩家公会
     *
     * @param playerId 玩家ID
     * @param guildId  公会ID
     * @return 操作结果
     */
    Result<Void> updatePlayerGuild(Long playerId, Long guildId);

    // ========== 批量操作 ==========

    /**
     * 批量更新玩家状态
     *
     * @param playerIds 玩家ID列表
     * @param state     新状态
     * @return 操作结果
     */
    Result<Integer> batchUpdatePlayerState(List<Long> playerIds, Player.PlayerState state);

    /**
     * 批量保存玩家
     *
     * @param players 玩家列表
     * @return 操作结果
     */
    Result<Integer> batchSavePlayers(List<Player> players);

    /**
     * 批量发放奖励
     *
     * @param playerIds 玩家ID列表
     * @param coins     金币数量
     * @param diamonds  钻石数量
     * @param experience 经验值
     * @return 操作结果
     */
    Result<Integer> batchGrantRewards(List<Long> playerIds, long coins, long diamonds, long experience);

    // ========== 统计查询 ==========

    /**
     * 获取玩家总数
     *
     * @return 玩家总数
     */
    long getTotalPlayerCount();

    /**
     * 获取今日新增玩家数
     *
     * @return 新增玩家数
     */
    long getTodayNewPlayerCount();

    /**
     * 获取在线玩家数
     *
     * @return 在线玩家数
     */
    long getOnlinePlayerCount();

    /**
     * 获取活跃玩家数（7天内登录过）
     *
     * @return 活跃玩家数
     */
    long getActivePlayerCount();

    /**
     * 获取玩家等级分布
     *
     * @return 等级分布映射（等级范围 -> 玩家数量）
     */
    Map<String, Long> getPlayerLevelDistribution();

    /**
     * 获取玩家注册趋势（最近30天）
     *
     * @return 注册趋势映射（日期 -> 注册数量）
     */
    Map<String, Long> getPlayerRegistrationTrend();

    // ========== 事务操作 ==========

    /**
     * 转账操作（玩家间金币转移）
     *
     * @param fromPlayerId 转出玩家ID
     * @param toPlayerId   转入玩家ID
     * @param amount       金币数量
     * @param reason       转账原因
     * @return 操作结果
     */
    Result<Void> transferCoins(Long fromPlayerId, Long toPlayerId, long amount, String reason);

    /**
     * 消费操作（扣除金币和钻石）
     *
     * @param playerId 玩家ID
     * @param coins    金币数量
     * @param diamonds 钻石数量
     * @param reason   消费原因
     * @return 操作结果
     */
    Result<Void> consumeResources(Long playerId, long coins, long diamonds, String reason);

    /**
     * 奖励操作（发放金币、钻石、经验）
     *
     * @param playerId   玩家ID
     * @param coins      金币数量
     * @param diamonds   钻石数量
     * @param experience 经验值
     * @param reason     奖励原因
     * @return 操作结果（包含升级信息）
     */
    Result<Map<String, Object>> grantRewards(Long playerId, long coins, long diamonds, long experience, String reason);
}