/*
 * 文件名: IPlayerService.java
 * 用途: 玩家服务接口定义
 * 实现内容:
 *   - 定义玩家登录、注册、登出等核心操作
 *   - 定义玩家信息查询和更新接口
 *   - 定义玩家状态管理接口
 *   - 支持玩家数据的增删改查操作
 * 技术选型:
 *   - 使用Java接口定义服务规范
 *   - 集成Result通用返回类型
 *   - 支持异步操作和Future返回
 * 依赖关系:
 *   - 依赖common-core的Result和PageResult
 *   - 被player-service模块实现
 *   - 被其他需要玩家信息的服务调用
 */
package com.lx.gameserver.api;

import com.lx.gameserver.common.Result;
import com.lx.gameserver.common.PageResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家服务接口
 * <p>
 * 定义了玩家相关的所有核心业务操作，包括登录、注册、信息管理等。
 * 所有方法都返回Result或PageResult类型，确保统一的错误处理和响应格式。
 * 支持同步和异步两种调用方式，适应不同的业务场景需求。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public interface IPlayerService {

    // ===== 玩家认证相关接口 =====

    /**
     * 玩家登录
     *
     * @param username    用户名
     * @param password    密码（已加密）
     * @param deviceInfo  设备信息
     * @param clientIp    客户端IP地址
     * @return 登录结果，包含玩家信息和会话token
     */
    Result<PlayerLoginInfo> login(String username, String password, Map<String, String> deviceInfo, String clientIp);

    /**
     * 异步玩家登录
     *
     * @param username    用户名
     * @param password    密码（已加密）
     * @param deviceInfo  设备信息
     * @param clientIp    客户端IP地址
     * @return 登录结果的Future
     */
    CompletableFuture<Result<PlayerLoginInfo>> loginAsync(String username, String password, 
                                                         Map<String, String> deviceInfo, String clientIp);

    /**
     * 第三方登录
     *
     * @param loginType      登录类型（微信、QQ等）
     * @param thirdPartyToken 第三方token
     * @param deviceInfo     设备信息
     * @param clientIp       客户端IP地址
     * @return 登录结果
     */
    Result<PlayerLoginInfo> thirdPartyLogin(String loginType, String thirdPartyToken, 
                                           Map<String, String> deviceInfo, String clientIp);

    /**
     * 玩家注册
     *
     * @param username   用户名
     * @param password   密码（已加密）
     * @param email      邮箱（可选）
     * @param phone      手机号（可选）
     * @param inviteCode 邀请码（可选）
     * @param deviceInfo 设备信息
     * @return 注册结果，包含新创建的玩家ID
     */
    Result<Long> register(String username, String password, String email, 
                         String phone, String inviteCode, Map<String, String> deviceInfo);

    /**
     * 玩家登出
     *
     * @param playerId 玩家ID
     * @param reason   登出原因
     * @return 登出结果
     */
    Result<Void> logout(Long playerId, String reason);

    /**
     * 刷新会话token
     *
     * @param playerId 玩家ID
     * @param oldToken 旧的token
     * @return 新的token信息
     */
    Result<TokenInfo> refreshToken(Long playerId, String oldToken);

    /**
     * 验证会话token
     *
     * @param playerId 玩家ID
     * @param token    会话token
     * @return 验证结果
     */
    Result<Boolean> validateToken(Long playerId, String token);

    // ===== 玩家信息查询接口 =====

    /**
     * 根据玩家ID获取玩家基本信息
     *
     * @param playerId 玩家ID
     * @return 玩家基本信息
     */
    Result<PlayerInfo> getPlayerInfo(Long playerId);

    /**
     * 根据用户名查询玩家信息
     *
     * @param username 用户名
     * @return 玩家基本信息
     */
    Result<PlayerInfo> getPlayerInfoByUsername(String username);

    /**
     * 批量获取玩家基本信息
     *
     * @param playerIds 玩家ID列表
     * @return 玩家信息列表
     */
    Result<List<PlayerInfo>> getPlayerInfoBatch(List<Long> playerIds);

    /**
     * 获取玩家详细信息
     *
     * @param playerId 玩家ID
     * @return 玩家详细信息，包含敏感数据
     */
    Result<PlayerDetailInfo> getPlayerDetailInfo(Long playerId);

    /**
     * 搜索玩家
     *
     * @param keyword    搜索关键词（用户名或昵称）
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 搜索结果分页
     */
    PageResult<PlayerInfo> searchPlayers(String keyword, int pageNum, int pageSize);

    /**
     * 获取在线玩家列表
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 在线玩家分页列表
     */
    PageResult<PlayerInfo> getOnlinePlayers(int pageNum, int pageSize);

    // ===== 玩家信息更新接口 =====

    /**
     * 更新玩家基本信息
     *
     * @param playerId     玩家ID
     * @param updateFields 要更新的字段
     * @return 更新结果
     */
    Result<Void> updatePlayerInfo(Long playerId, Map<String, Object> updateFields);

    /**
     * 更新玩家昵称
     *
     * @param playerId 玩家ID
     * @param nickname 新昵称
     * @return 更新结果
     */
    Result<Void> updateNickname(Long playerId, String nickname);

    /**
     * 更新玩家头像
     *
     * @param playerId  玩家ID
     * @param avatarUrl 头像URL
     * @return 更新结果
     */
    Result<Void> updateAvatar(Long playerId, String avatarUrl);

    /**
     * 更新玩家签名
     *
     * @param playerId  玩家ID
     * @param signature 个人签名
     * @return 更新结果
     */
    Result<Void> updateSignature(Long playerId, String signature);

    /**
     * 修改密码
     *
     * @param playerId    玩家ID
     * @param oldPassword 旧密码（已加密）
     * @param newPassword 新密码（已加密）
     * @return 修改结果
     */
    Result<Void> changePassword(Long playerId, String oldPassword, String newPassword);

    // ===== 玩家状态管理接口 =====

    /**
     * 设置玩家在线状态
     *
     * @param playerId 玩家ID
     * @param online   是否在线
     * @return 设置结果
     */
    Result<Void> setPlayerOnlineStatus(Long playerId, boolean online);

    /**
     * 获取玩家在线状态
     *
     * @param playerId 玩家ID
     * @return 在线状态
     */
    Result<Boolean> getPlayerOnlineStatus(Long playerId);

    /**
     * 设置玩家状态
     *
     * @param playerId 玩家ID
     * @param status   玩家状态
     * @return 设置结果
     */
    Result<Void> setPlayerStatus(Long playerId, String status);

    /**
     * 批量获取玩家在线状态
     *
     * @param playerIds 玩家ID列表
     * @return 在线状态映射
     */
    Result<Map<Long, Boolean>> getPlayersOnlineStatus(List<Long> playerIds);

    // ===== 玩家数据管理接口 =====

    /**
     * 增加玩家经验
     *
     * @param playerId   玩家ID
     * @param experience 经验值
     * @param reason     增加原因
     * @return 操作结果，包含是否升级信息
     */
    Result<LevelUpInfo> addExperience(Long playerId, long experience, String reason);

    /**
     * 增加玩家金币
     *
     * @param playerId 玩家ID
     * @param gold     金币数量
     * @param reason   增加原因
     * @return 操作结果
     */
    Result<Void> addGold(Long playerId, long gold, String reason);

    /**
     * 扣除玩家金币
     *
     * @param playerId 玩家ID
     * @param gold     金币数量
     * @param reason   扣除原因
     * @return 操作结果
     */
    Result<Void> deductGold(Long playerId, long gold, String reason);

    /**
     * 增加钻石
     *
     * @param playerId 玩家ID
     * @param diamond  钻石数量
     * @param reason   增加原因
     * @return 操作结果
     */
    Result<Void> addDiamond(Long playerId, long diamond, String reason);

    /**
     * 扣除钻石
     *
     * @param playerId 玩家ID
     * @param diamond  钻石数量
     * @param reason   扣除原因
     * @return 操作结果
     */
    Result<Void> deductDiamond(Long playerId, long diamond, String reason);

    /**
     * 恢复玩家体力
     *
     * @param playerId 玩家ID
     * @param energy   体力值
     * @return 操作结果
     */
    Result<Void> restoreEnergy(Long playerId, int energy);

    /**
     * 消耗玩家体力
     *
     * @param playerId 玩家ID
     * @param energy   体力值
     * @param reason   消耗原因
     * @return 操作结果
     */
    Result<Void> consumeEnergy(Long playerId, int energy, String reason);

    // ===== 玩家统计接口 =====

    /**
     * 获取玩家总数
     *
     * @return 玩家总数
     */
    Result<Long> getTotalPlayerCount();

    /**
     * 获取当前在线玩家数
     *
     * @return 在线玩家数
     */
    Result<Integer> getOnlinePlayerCount();

    /**
     * 获取今日新增玩家数
     *
     * @return 今日新增玩家数
     */
    Result<Integer> getTodayNewPlayerCount();

    /**
     * 获取玩家等级分布统计
     *
     * @return 等级分布统计
     */
    Result<Map<Integer, Integer>> getPlayerLevelDistribution();

    // ===== 内部数据结构定义 =====

    /**
     * 玩家登录信息
     */
    class PlayerLoginInfo {
        /** 玩家基本信息 */
        private PlayerInfo playerInfo;
        /** 会话token */
        private String sessionToken;
        /** token过期时间 */
        private Long tokenExpireTime;
        /** 是否首次登录 */
        private Boolean firstLogin;
        /** 上次登录时间 */
        private Long lastLoginTime;

        // Getters and Setters
        public PlayerInfo getPlayerInfo() { return playerInfo; }
        public void setPlayerInfo(PlayerInfo playerInfo) { this.playerInfo = playerInfo; }
        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
        public Long getTokenExpireTime() { return tokenExpireTime; }
        public void setTokenExpireTime(Long tokenExpireTime) { this.tokenExpireTime = tokenExpireTime; }
        public Boolean getFirstLogin() { return firstLogin; }
        public void setFirstLogin(Boolean firstLogin) { this.firstLogin = firstLogin; }
        public Long getLastLoginTime() { return lastLoginTime; }
        public void setLastLoginTime(Long lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    }

    /**
     * 玩家基本信息
     */
    class PlayerInfo {
        /** 玩家ID */
        private Long playerId;
        /** 用户名 */
        private String username;
        /** 昵称 */
        private String nickname;
        /** 头像URL */
        private String avatarUrl;
        /** 等级 */
        private Integer level;
        /** 经验值 */
        private Long experience;
        /** 金币 */
        private Long gold;
        /** 钻石 */
        private Long diamond;
        /** 体力 */
        private Integer energy;
        /** 最大体力 */
        private Integer maxEnergy;
        /** VIP等级 */
        private Integer vipLevel;
        /** 创建时间 */
        private Long createTime;
        /** 最后登录时间 */
        private Long lastLoginTime;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Long getExperience() { return experience; }
        public void setExperience(Long experience) { this.experience = experience; }
        public Long getGold() { return gold; }
        public void setGold(Long gold) { this.gold = gold; }
        public Long getDiamond() { return diamond; }
        public void setDiamond(Long diamond) { this.diamond = diamond; }
        public Integer getEnergy() { return energy; }
        public void setEnergy(Integer energy) { this.energy = energy; }
        public Integer getMaxEnergy() { return maxEnergy; }
        public void setMaxEnergy(Integer maxEnergy) { this.maxEnergy = maxEnergy; }
        public Integer getVipLevel() { return vipLevel; }
        public void setVipLevel(Integer vipLevel) { this.vipLevel = vipLevel; }
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
        public Long getLastLoginTime() { return lastLoginTime; }
        public void setLastLoginTime(Long lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    }

    /**
     * 玩家详细信息（包含敏感数据）
     */
    class PlayerDetailInfo extends PlayerInfo {
        /** 邮箱 */
        private String email;
        /** 手机号 */
        private String phone;
        /** 注册IP */
        private String registerIp;
        /** 最后登录IP */
        private String lastLoginIp;
        /** 总在线时长（秒） */
        private Long totalOnlineTime;
        /** 账号状态 */
        private String accountStatus;

        // Getters and Setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getRegisterIp() { return registerIp; }
        public void setRegisterIp(String registerIp) { this.registerIp = registerIp; }
        public String getLastLoginIp() { return lastLoginIp; }
        public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }
        public Long getTotalOnlineTime() { return totalOnlineTime; }
        public void setTotalOnlineTime(Long totalOnlineTime) { this.totalOnlineTime = totalOnlineTime; }
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    }

    /**
     * Token信息
     */
    class TokenInfo {
        /** 新token */
        private String token;
        /** 过期时间 */
        private Long expireTime;

        // Getters and Setters
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public Long getExpireTime() { return expireTime; }
        public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }
    }

    /**
     * 升级信息
     */
    class LevelUpInfo {
        /** 是否升级 */
        private Boolean levelUp;
        /** 新等级 */
        private Integer newLevel;
        /** 当前经验 */
        private Long currentExp;
        /** 下级所需经验 */
        private Long nextLevelExp;

        // Getters and Setters
        public Boolean getLevelUp() { return levelUp; }
        public void setLevelUp(Boolean levelUp) { this.levelUp = levelUp; }
        public Integer getNewLevel() { return newLevel; }
        public void setNewLevel(Integer newLevel) { this.newLevel = newLevel; }
        public Long getCurrentExp() { return currentExp; }
        public void setCurrentExp(Long currentExp) { this.currentExp = currentExp; }
        public Long getNextLevelExp() { return nextLevelExp; }
        public void setNextLevelExp(Long nextLevelExp) { this.nextLevelExp = nextLevelExp; }
    }
}