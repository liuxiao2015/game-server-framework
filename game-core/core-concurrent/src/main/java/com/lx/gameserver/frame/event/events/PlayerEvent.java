/*
 * 文件名: PlayerEvent.java
 * 用途: 玩家相关事件
 * 实现内容:
 *   - 定义玩家登录、登出、升级等事件
 *   - 提供玩家状态变化的事件通知
 *   - 支持玩家数据的事件传递
 * 技术选型:
 *   - 继承GameEvent基类
 *   - 静态内部类设计
 *   - 不可变对象模式
 * 依赖关系:
 *   - 继承GameEvent
 *   - 被玩家系统使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.events;

import com.lx.gameserver.frame.event.core.EventPriority;
import com.lx.gameserver.frame.event.core.GameEvent;

import java.util.Map;
import java.util.Objects;

/**
 * 玩家相关事件
 * <p>
 * 定义玩家系统中的各种事件，包括登录、登出、升级等。
 * 使用静态内部类组织不同类型的玩家事件。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class PlayerEvent extends GameEvent {
    
    /** 玩家ID */
    private final long playerId;
    
    /**
     * 构造函数
     *
     * @param playerId 玩家ID
     * @param source   事件来源
     * @param priority 事件优先级
     */
    protected PlayerEvent(long playerId, String source, EventPriority priority) {
        super(source, priority);
        this.playerId = playerId;
    }
    
    /**
     * 获取玩家ID
     *
     * @return 玩家ID
     */
    public long getPlayerId() {
        return playerId;
    }
    
    @Override
    public boolean isValid() {
        return super.isValid() && playerId > 0;
    }
    
    /**
     * 玩家登录事件
     */
    public static class PlayerLoginEvent extends PlayerEvent {
        
        /** 登录IP地址 */
        private final String loginIp;
        
        /** 登录时间戳 */
        private final long loginTime;
        
        /** 设备信息 */
        private final String deviceInfo;
        
        /** 登录渠道 */
        private final String channel;
        
        /**
         * 构造函数
         *
         * @param playerId   玩家ID
         * @param loginIp    登录IP
         * @param deviceInfo 设备信息
         * @param channel    登录渠道
         * @param source     事件来源
         */
        public PlayerLoginEvent(long playerId, String loginIp, String deviceInfo, String channel, String source) {
            super(playerId, source, EventPriority.HIGH);
            this.loginIp = Objects.requireNonNull(loginIp, "登录IP不能为空");
            this.loginTime = System.currentTimeMillis();
            this.deviceInfo = deviceInfo;
            this.channel = channel;
        }
        
        public String getLoginIp() {
            return loginIp;
        }
        
        public long getLoginTime() {
            return loginTime;
        }
        
        public String getDeviceInfo() {
            return deviceInfo;
        }
        
        public String getChannel() {
            return channel;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "playerId", getPlayerId(),
                    "loginIp", loginIp,
                    "loginTime", loginTime,
                    "deviceInfo", deviceInfo != null ? deviceInfo : "",
                    "channel", channel != null ? channel : ""
            );
        }
        
        @Override
        public String toString() {
            return String.format("PlayerLoginEvent{playerId=%d, loginIp='%s', deviceInfo='%s', channel='%s'}", 
                    getPlayerId(), loginIp, deviceInfo, channel);
        }
    }
    
    /**
     * 玩家登出事件
     */
    public static class PlayerLogoutEvent extends PlayerEvent {
        
        /** 登出原因 */
        private final LogoutReason reason;
        
        /** 在线时长（秒） */
        private final long onlineDuration;
        
        /** 登出时间戳 */
        private final long logoutTime;
        
        /**
         * 构造函数
         *
         * @param playerId       玩家ID
         * @param reason         登出原因
         * @param onlineDuration 在线时长
         * @param source         事件来源
         */
        public PlayerLogoutEvent(long playerId, LogoutReason reason, long onlineDuration, String source) {
            super(playerId, source, EventPriority.NORMAL);
            this.reason = Objects.requireNonNull(reason, "登出原因不能为空");
            this.onlineDuration = Math.max(0, onlineDuration);
            this.logoutTime = System.currentTimeMillis();
        }
        
        public LogoutReason getReason() {
            return reason;
        }
        
        public long getOnlineDuration() {
            return onlineDuration;
        }
        
        public long getLogoutTime() {
            return logoutTime;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "playerId", getPlayerId(),
                    "reason", reason.name(),
                    "onlineDuration", onlineDuration,
                    "logoutTime", logoutTime
            );
        }
        
        @Override
        public String toString() {
            return String.format("PlayerLogoutEvent{playerId=%d, reason=%s, onlineDuration=%d}", 
                    getPlayerId(), reason, onlineDuration);
        }
    }
    
    /**
     * 玩家升级事件
     */
    public static class PlayerLevelUpEvent extends PlayerEvent {
        
        /** 原等级 */
        private final int oldLevel;
        
        /** 新等级 */
        private final int newLevel;
        
        /** 升级时获得的经验 */
        private final long expGained;
        
        /** 升级奖励信息 */
        private final Map<String, Object> rewards;
        
        /**
         * 构造函数
         *
         * @param playerId  玩家ID
         * @param oldLevel  原等级
         * @param newLevel  新等级
         * @param expGained 获得经验
         * @param rewards   升级奖励
         * @param source    事件来源
         */
        public PlayerLevelUpEvent(long playerId, int oldLevel, int newLevel, long expGained, 
                                 Map<String, Object> rewards, String source) {
            super(playerId, source, EventPriority.HIGH);
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
            this.expGained = expGained;
            this.rewards = rewards != null ? Map.copyOf(rewards) : Map.of();
        }
        
        public int getOldLevel() {
            return oldLevel;
        }
        
        public int getNewLevel() {
            return newLevel;
        }
        
        public long getExpGained() {
            return expGained;
        }
        
        public Map<String, Object> getRewards() {
            return rewards;
        }
        
        public int getLevelIncrement() {
            return newLevel - oldLevel;
        }
        
        @Override
        public boolean isValid() {
            return super.isValid() && oldLevel > 0 && newLevel > oldLevel;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "playerId", getPlayerId(),
                    "oldLevel", oldLevel,
                    "newLevel", newLevel,
                    "expGained", expGained,
                    "rewards", rewards
            );
        }
        
        @Override
        public String toString() {
            return String.format("PlayerLevelUpEvent{playerId=%d, oldLevel=%d, newLevel=%d, expGained=%d}", 
                    getPlayerId(), oldLevel, newLevel, expGained);
        }
    }
    
    /**
     * 玩家经验获得事件
     */
    public static class PlayerExpGainEvent extends PlayerEvent {
        
        /** 获得的经验值 */
        private final long expAmount;
        
        /** 经验来源 */
        private final String expSource;
        
        /** 当前等级 */
        private final int currentLevel;
        
        /** 当前经验 */
        private final long currentExp;
        
        /**
         * 构造函数
         *
         * @param playerId     玩家ID
         * @param expAmount    经验值
         * @param expSource    经验来源
         * @param currentLevel 当前等级
         * @param currentExp   当前经验
         * @param source       事件来源
         */
        public PlayerExpGainEvent(long playerId, long expAmount, String expSource, 
                                 int currentLevel, long currentExp, String source) {
            super(playerId, source, EventPriority.NORMAL);
            this.expAmount = expAmount;
            this.expSource = Objects.requireNonNull(expSource, "经验来源不能为空");
            this.currentLevel = currentLevel;
            this.currentExp = currentExp;
        }
        
        public long getExpAmount() {
            return expAmount;
        }
        
        public String getExpSource() {
            return expSource;
        }
        
        public int getCurrentLevel() {
            return currentLevel;
        }
        
        public long getCurrentExp() {
            return currentExp;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "playerId", getPlayerId(),
                    "expAmount", expAmount,
                    "expSource", expSource,
                    "currentLevel", currentLevel,
                    "currentExp", currentExp
            );
        }
        
        @Override
        public String toString() {
            return String.format("PlayerExpGainEvent{playerId=%d, expAmount=%d, expSource='%s', currentLevel=%d}", 
                    getPlayerId(), expAmount, expSource, currentLevel);
        }
    }
    
    /**
     * 登出原因枚举
     */
    public enum LogoutReason {
        /** 正常登出 */
        NORMAL("正常登出"),
        
        /** 超时登出 */
        TIMEOUT("超时登出"),
        
        /** 被踢下线 */
        KICKED("被踢下线"),
        
        /** 网络断开 */
        NETWORK_ERROR("网络断开"),
        
        /** 服务器维护 */
        SERVER_MAINTENANCE("服务器维护"),
        
        /** 重复登录 */
        DUPLICATE_LOGIN("重复登录"),
        
        /** 异常登出 */
        ERROR("异常登出");
        
        private final String description;
        
        LogoutReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}