/*
 * 文件名: Player.java
 * 用途: 玩家实体类
 * 实现内容:
 *   - 玩家的基础属性和扩展属性管理
 *   - 玩家状态管理和生命周期控制
 *   - 数据持久化和事件发布
 *   - 玩家行为记录和统计信息
 *   - 线程安全的属性访问和修改
 * 技术选型:
 *   - JPA实体映射进行数据持久化
 *   - JSON序列化支持扩展属性
 *   - 事件发布机制同步状态变更
 *   - 线程安全的并发访问控制
 * 依赖关系:
 *   - 被PlayerManager管理和调度
 *   - 与PlayerActor协作处理逻辑
 *   - 集成ECS组件系统
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lx.gameserver.common.JsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 玩家实体类
 * <p>
 * 表示游戏中的玩家对象，包含玩家的基础信息、游戏属性、
 * 状态管理等功能。支持扩展属性和事件发布机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "player",
       indexes = {
           @Index(name = "idx_player_username", columnList = "username"),
           @Index(name = "idx_player_level", columnList = "level"),
           @Index(name = "idx_player_guild_id", columnList = "guildId"),
           @Index(name = "idx_player_last_login", columnList = "lastLoginTime")
       })
public class Player {

    /**
     * 玩家状态枚举
     */
    public enum PlayerState {
        /** 离线 */
        OFFLINE,
        /** 在线 */
        ONLINE,
        /** 忙碌 */
        BUSY,
        /** 战斗中 */
        IN_BATTLE,
        /** 交易中 */
        IN_TRADE,
        /** 被封禁 */
        BANNED,
        /** 已删除 */
        DELETED
    }

    /**
     * 玩家性别枚举
     */
    public enum Gender {
        /** 男性 */
        MALE,
        /** 女性 */
        FEMALE,
        /** 未知 */
        UNKNOWN
    }

    /** 玩家ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long playerId;

    /** 用户名 */
    @Column(nullable = false, unique = true, length = 32)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度必须在3-32字符之间")
    private String username;

    /** 昵称 */
    @Column(nullable = false, length = 64)
    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 64, message = "昵称长度必须在1-64字符之间")
    private String nickname;

    /** 等级 */
    @Column(nullable = false)
    @Min(value = 1, message = "等级不能小于1")
    @Max(value = 999, message = "等级不能超过999")
    private Integer level = 1;

    /** 经验值 */
    @Column(nullable = false)
    @Min(value = 0, message = "经验值不能为负数")
    private Long experience = 0L;

    /** 金币 */
    @Column(nullable = false)
    @Min(value = 0, message = "金币不能为负数")
    private Long coins = 0L;

    /** 钻石 */
    @Column(nullable = false)
    @Min(value = 0, message = "钻石不能为负数")
    private Long diamonds = 0L;

    /** 体力 */
    @Column(nullable = false)
    @Min(value = 0, message = "体力不能为负数")
    private Integer energy = 100;

    /** 最大体力 */
    @Column(nullable = false)
    @Min(value = 1, message = "最大体力不能小于1")
    private Integer maxEnergy = 100;

    /** VIP等级 */
    @Column(nullable = false)
    @Min(value = 0, message = "VIP等级不能为负数")
    private Integer vipLevel = 0;

    /** 性别 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender = Gender.UNKNOWN;

    /** 头像URL */
    @Column(length = 255)
    private String avatarUrl;

    /** 玩家状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerState state = PlayerState.OFFLINE;

    /** 当前场景ID */
    @Column
    private Long currentSceneId;

    /** 公会ID */
    @Column
    private Long guildId;

    /** 注册时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 最后登录时间 */
    @Column
    private LocalDateTime lastLoginTime;

    /** 最后登出时间 */
    @Column
    private LocalDateTime lastLogoutTime;

    /** 在线时长（秒） */
    @Column(nullable = false)
    @Min(value = 0, message = "在线时长不能为负数")
    private Long onlineTime = 0L;

    /** 登录次数 */
    @Column(nullable = false)
    @Min(value = 0, message = "登录次数不能为负数")
    private Integer loginCount = 0;

    /** 是否被封禁 */
    @Column(nullable = false)
    private Boolean banned = false;

    /** 封禁到期时间 */
    @Column
    private LocalDateTime banExpireTime;

    /** 封禁原因 */
    @Column(length = 500)
    private String banReason;

    /** 扩展属性（JSON格式） */
    @Column(columnDefinition = "TEXT")
    private String extendedAttributes;

    /** 备注 */
    @Column(length = 500)
    private String remarks;

    /** 最后更新时间 */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    /** 版本号（乐观锁） */
    @Version
    private Integer version;

    /** 运行时扩展属性（不持久化） */
    @Transient
    @JsonIgnore
    private final Map<String, Object> runtimeAttributes = new ConcurrentHashMap<>();

    /** 当前登录会话ID */
    @Transient
    @JsonIgnore
    private String sessionId;

    /** 上次状态变更时间 */
    @Transient
    @JsonIgnore
    private volatile LocalDateTime lastStateChangeTime = LocalDateTime.now();

    /** 状态原子引用 */
    @Transient
    @JsonIgnore
    private final AtomicReference<PlayerState> atomicState = new AtomicReference<>(PlayerState.OFFLINE);

    /**
     * JPA生命周期回调：保存前
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
        
        // 设置默认值
        if (level == null) level = 1;
        if (experience == null) experience = 0L;
        if (coins == null) coins = 0L;
        if (diamonds == null) diamonds = 0L;
        if (energy == null) energy = 100;
        if (maxEnergy == null) maxEnergy = 100;
        if (vipLevel == null) vipLevel = 0;
        if (onlineTime == null) onlineTime = 0L;
        if (loginCount == null) loginCount = 0;
        if (banned == null) banned = false;
        if (state == null) state = PlayerState.OFFLINE;
        if (gender == null) gender = Gender.UNKNOWN;
        
        // 同步原子状态
        atomicState.set(state);
    }

    /**
     * JPA生命周期回调：更新前
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        
        // 同步原子状态
        atomicState.set(state);
    }

    /**
     * JPA生命周期回调：加载后
     */
    @PostLoad
    protected void onLoad() {
        // 同步原子状态
        atomicState.set(state != null ? state : PlayerState.OFFLINE);
        lastStateChangeTime = updateTime != null ? updateTime : LocalDateTime.now();
    }

    /**
     * 原子更新玩家状态
     *
     * @param newState 新状态
     * @return 是否更新成功
     */
    public boolean updateState(PlayerState newState) {
        if (newState == null) {
            return false;
        }

        PlayerState oldState = atomicState.getAndSet(newState);
        if (oldState != newState) {
            this.state = newState;
            this.lastStateChangeTime = LocalDateTime.now();
            log.debug("玩家 {} 状态从 {} 变更为 {}", playerId, oldState, newState);
            return true;
        }
        return false;
    }

    /**
     * 获取当前状态（线程安全）
     *
     * @return 当前状态
     */
    public PlayerState getCurrentState() {
        return atomicState.get();
    }

    /**
     * 检查是否在线
     *
     * @return 是否在线
     */
    public boolean isOnline() {
        return getCurrentState() == PlayerState.ONLINE;
    }

    /**
     * 检查是否被封禁
     *
     * @return 是否被封禁
     */
    public boolean isBanned() {
        if (banned != null && banned) {
            // 检查封禁是否过期
            if (banExpireTime != null && LocalDateTime.now().isAfter(banExpireTime)) {
                // 封禁已过期，自动解封
                banned = false;
                banExpireTime = null;
                banReason = null;
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 封禁玩家
     *
     * @param duration 封禁时长
     * @param reason   封禁原因
     */
    public void ban(java.time.Duration duration, String reason) {
        this.banned = true;
        this.banExpireTime = duration != null ? LocalDateTime.now().plus(duration) : null;
        this.banReason = reason;
        updateState(PlayerState.BANNED);
        log.info("玩家 {} 被封禁，原因: {}, 到期时间: {}", playerId, reason, banExpireTime);
    }

    /**
     * 解封玩家
     */
    public void unban() {
        this.banned = false;
        this.banExpireTime = null;
        this.banReason = null;
        if (getCurrentState() == PlayerState.BANNED) {
            updateState(PlayerState.OFFLINE);
        }
        log.info("玩家 {} 已解封", playerId);
    }

    /**
     * 增加经验值
     *
     * @param exp 经验值
     * @return 是否升级
     */
    public boolean addExperience(long exp) {
        if (exp <= 0) {
            return false;
        }

        int oldLevel = this.level;
        this.experience += exp;

        // 检查是否升级（简化升级逻辑）
        long expForNextLevel = getExpForLevel(this.level + 1);
        while (this.experience >= expForNextLevel && this.level < 999) {
            this.experience -= expForNextLevel;
            this.level++;
            expForNextLevel = getExpForLevel(this.level + 1);
        }

        boolean leveledUp = this.level > oldLevel;
        if (leveledUp) {
            log.info("玩家 {} 升级到 {} 级", playerId, this.level);
        }

        return leveledUp;
    }

    /**
     * 获取指定等级所需经验值
     *
     * @param level 等级
     * @return 所需经验值
     */
    private long getExpForLevel(int level) {
        // 简化的经验计算公式
        return level * 100L;
    }

    /**
     * 增加金币
     *
     * @param amount 金币数量
     * @return 是否成功
     */
    public boolean addCoins(long amount) {
        if (amount <= 0) {
            return false;
        }
        this.coins += amount;
        return true;
    }

    /**
     * 消耗金币
     *
     * @param amount 金币数量
     * @return 是否成功
     */
    public boolean consumeCoins(long amount) {
        if (amount <= 0 || this.coins < amount) {
            return false;
        }
        this.coins -= amount;
        return true;
    }

    /**
     * 增加钻石
     *
     * @param amount 钻石数量
     * @return 是否成功
     */
    public boolean addDiamonds(long amount) {
        if (amount <= 0) {
            return false;
        }
        this.diamonds += amount;
        return true;
    }

    /**
     * 消耗钻石
     *
     * @param amount 钻石数量
     * @return 是否成功
     */
    public boolean consumeDiamonds(long amount) {
        if (amount <= 0 || this.diamonds < amount) {
            return false;
        }
        this.diamonds -= amount;
        return true;
    }

    /**
     * 恢复体力
     *
     * @param amount 体力数量
     * @return 实际恢复的体力
     */
    public int restoreEnergy(int amount) {
        if (amount <= 0) {
            return 0;
        }

        int oldEnergy = this.energy;
        this.energy = Math.min(this.energy + amount, this.maxEnergy);
        return this.energy - oldEnergy;
    }

    /**
     * 消耗体力
     *
     * @param amount 体力数量
     * @return 是否成功
     */
    public boolean consumeEnergy(int amount) {
        if (amount <= 0 || this.energy < amount) {
            return false;
        }
        this.energy -= amount;
        return true;
    }

    /**
     * 设置扩展属性
     *
     * @param key   键
     * @param value 值
     */
    public void setExtendedAttribute(String key, Object value) {
        Map<String, Object> attributes = getExtendedAttributesMap();
        attributes.put(key, value);
        updateExtendedAttributesJson(attributes);
    }

    /**
     * 获取扩展属性
     *
     * @param key 键
     * @return 值
     */
    public Object getExtendedAttribute(String key) {
        Map<String, Object> attributes = getExtendedAttributesMap();
        return attributes.get(key);
    }

    /**
     * 获取扩展属性
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtendedAttribute(String key, T defaultValue) {
        Object value = getExtendedAttribute(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 移除扩展属性
     *
     * @param key 键
     * @return 被移除的值
     */
    public Object removeExtendedAttribute(String key) {
        Map<String, Object> attributes = getExtendedAttributesMap();
        Object removed = attributes.remove(key);
        if (removed != null) {
            updateExtendedAttributesJson(attributes);
        }
        return removed;
    }

    /**
     * 获取扩展属性Map
     */
    private Map<String, Object> getExtendedAttributesMap() {
        if (extendedAttributes == null || extendedAttributes.trim().isEmpty()) {
            return new ConcurrentHashMap<>();
        }

        try {
            return JsonUtils.fromJson(extendedAttributes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析扩展属性失败，玩家ID: {}", playerId, e);
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * 更新扩展属性JSON
     */
    private void updateExtendedAttributesJson(Map<String, Object> attributes) {
        try {
            this.extendedAttributes = JsonUtils.toJson(attributes);
        } catch (Exception e) {
            log.error("序列化扩展属性失败，玩家ID: {}", playerId, e);
        }
    }

    /**
     * 设置运行时属性
     *
     * @param key   键
     * @param value 值
     */
    public void setRuntimeAttribute(String key, Object value) {
        runtimeAttributes.put(key, value);
    }

    /**
     * 获取运行时属性
     *
     * @param key 键
     * @return 值
     */
    public Object getRuntimeAttribute(String key) {
        return runtimeAttributes.get(key);
    }

    /**
     * 获取运行时属性
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getRuntimeAttribute(String key, T defaultValue) {
        Object value = runtimeAttributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 移除运行时属性
     *
     * @param key 键
     * @return 被移除的值
     */
    public Object removeRuntimeAttribute(String key) {
        return runtimeAttributes.remove(key);
    }

    /**
     * 记录登录
     */
    public void recordLogin(String sessionId) {
        this.sessionId = sessionId;
        this.lastLoginTime = LocalDateTime.now();
        this.loginCount = (this.loginCount != null ? this.loginCount : 0) + 1;
        updateState(PlayerState.ONLINE);
        log.info("玩家 {} 登录，会话ID: {}", playerId, sessionId);
    }

    /**
     * 记录登出
     */
    public void recordLogout() {
        LocalDateTime logoutTime = LocalDateTime.now();
        
        // 计算本次在线时长
        if (lastLoginTime != null) {
            long sessionTime = java.time.Duration.between(lastLoginTime, logoutTime).getSeconds();
            this.onlineTime = (this.onlineTime != null ? this.onlineTime : 0L) + sessionTime;
        }
        
        this.lastLogoutTime = logoutTime;
        this.sessionId = null;
        updateState(PlayerState.OFFLINE);
        
        log.info("玩家 {} 登出，累计在线时长: {} 秒", playerId, onlineTime);
    }

    /**
     * 获取显示名称
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return nickname != null && !nickname.trim().isEmpty() ? nickname : username;
    }

    /**
     * 获取当前等级进度百分比
     *
     * @return 进度百分比 (0-100)
     */
    public double getLevelProgress() {
        if (level >= 999) {
            return 100.0;
        }
        
        long expForCurrentLevel = getExpForLevel(level);
        long expForNextLevel = getExpForLevel(level + 1);
        long totalExpNeeded = expForNextLevel - expForCurrentLevel;
        
        if (totalExpNeeded <= 0) {
            return 100.0;
        }
        
        return Math.min(100.0, (double) experience / totalExpNeeded * 100.0);
    }

    @Override
    public String toString() {
        return String.format("Player{id=%d, username='%s', nickname='%s', level=%d, state=%s}",
                playerId, username, nickname, level, getCurrentState());
    }
}