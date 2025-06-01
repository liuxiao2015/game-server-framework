/*
 * 文件名: ChatChannel.java
 * 用途: 聊天频道抽象基类
 * 实现内容:
 *   - 定义聊天频道的基础结构和通用行为
 *   - 频道类型定义和频道ID管理
 *   - 频道成员管理和权限控制
 *   - 频道配置和生命周期管理
 *   - 消息发送和接收的抽象接口
 * 技术选型:
 *   - 抽象类设计模式
 *   - 使用ConcurrentHashMap保证线程安全
 *   - 支持频道配置和扩展
 * 依赖关系:
 *   - 被具体频道实现类继承
 *   - 与ChannelManager协作管理频道
 *   - 为MessageService提供频道抽象
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.core;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天频道抽象基类
 * <p>
 * 定义了聊天频道的基础结构和通用行为，包含频道成员管理、
 * 权限控制、消息处理等核心功能。所有具体的频道类型都应该
 * 继承此抽象类并实现相应的抽象方法。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ChatChannel {

    /**
     * 频道ID
     */
    protected String channelId;

    /**
     * 频道类型
     */
    protected ChatMessage.ChatChannelType channelType;

    /**
     * 频道名称
     */
    protected String channelName;

    /**
     * 频道描述
     */
    protected String description;

    /**
     * 频道创建时间
     */
    protected LocalDateTime createTime;

    /**
     * 最后活跃时间
     */
    protected LocalDateTime lastActiveTime;

    /**
     * 频道状态
     */
    protected ChannelStatus status;

    /**
     * 频道配置
     */
    protected ChannelConfig config;

    /**
     * 频道成员集合（PlayerId -> 加入时间）
     */
    protected final Map<Long, LocalDateTime> members = new ConcurrentHashMap<>();

    /**
     * 频道管理员集合
     */
    protected final Set<Long> administrators = ConcurrentHashMap.newKeySet();

    /**
     * 被禁言的成员集合（PlayerId -> 禁言到期时间）
     */
    protected final Map<Long, LocalDateTime> mutedMembers = new ConcurrentHashMap<>();

    /**
     * 频道扩展属性
     */
    protected final Map<String, Object> properties = new ConcurrentHashMap<>();

    /**
     * 消息计数器
     */
    protected final AtomicLong messageCounter = new AtomicLong(0);

    /**
     * 频道状态枚举
     */
    public enum ChannelStatus {
        /** 活跃状态 */
        ACTIVE("active", "活跃"),
        /** 暂停状态 */
        PAUSED("paused", "暂停"),
        /** 关闭状态 */
        CLOSED("closed", "关闭"),
        /** 维护状态 */
        MAINTENANCE("maintenance", "维护中");

        private final String code;
        private final String description;

        ChannelStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 频道配置类
     */
    @Data
    public static class ChannelConfig {
        /** 最大成员数量（-1表示无限制） */
        private int maxMembers = -1;
        
        /** 发言间隔（秒） */
        private int messageInterval = 5;
        
        /** 最大消息长度 */
        private int maxMessageLength = 500;
        
        /** 是否需要权限才能发言 */
        private boolean requirePermission = false;
        
        /** 是否允许匿名用户 */
        private boolean allowAnonymous = false;
        
        /** 是否记录历史消息 */
        private boolean recordHistory = true;
        
        /** 历史消息保留天数 */
        private int historyRetentionDays = 7;
        
        /** 是否启用敏感词过滤 */
        private boolean enableSensitiveWordFilter = true;
        
        /** 是否启用限流 */
        private boolean enableRateLimit = true;
        
        /** 频道图标URL */
        private String iconUrl;
        
        /** 频道公告 */
        private String announcement;
        
        /** 扩展配置 */
        private Map<String, Object> extraConfig = new HashMap<>();
    }

    /**
     * 构造函数
     *
     * @param channelId 频道ID
     * @param channelType 频道类型
     * @param channelName 频道名称
     */
    protected ChatChannel(String channelId, ChatMessage.ChatChannelType channelType, String channelName) {
        this.channelId = channelId;
        this.channelType = channelType;
        this.channelName = channelName;
        this.createTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.status = ChannelStatus.ACTIVE;
        this.config = new ChannelConfig();
    }

    // ===== 抽象方法定义 =====

    /**
     * 发送消息到频道
     *
     * @param message 聊天消息
     * @return 发送结果
     */
    public abstract boolean sendMessage(ChatMessage message);

    /**
     * 处理接收到的消息
     *
     * @param message 聊天消息
     */
    public abstract void onMessageReceived(ChatMessage message);

    /**
     * 检查用户是否有发言权限
     *
     * @param playerId 玩家ID
     * @return true表示有权限
     */
    public abstract boolean hasPermission(Long playerId);

    /**
     * 获取频道的目标受众
     *
     * @return 目标玩家ID列表
     */
    public abstract List<Long> getTargetAudience();

    // ===== 成员管理方法 =====

    /**
     * 添加成员到频道
     *
     * @param playerId 玩家ID
     * @return 添加结果
     */
    public boolean addMember(Long playerId) {
        if (playerId == null) {
            return false;
        }

        // 检查频道容量
        if (config.maxMembers > 0 && members.size() >= config.maxMembers) {
            log.warn("频道 {} 已达到最大成员数量限制: {}", channelId, config.maxMembers);
            return false;
        }

        // 检查是否已存在
        if (members.containsKey(playerId)) {
            log.debug("玩家 {} 已在频道 {} 中", playerId, channelId);
            return true;
        }

        members.put(playerId, LocalDateTime.now());
        log.info("玩家 {} 加入频道 {}, 当前成员数: {}", playerId, channelId, members.size());
        return true;
    }

    /**
     * 从频道移除成员
     *
     * @param playerId 玩家ID
     * @return 移除结果
     */
    public boolean removeMember(Long playerId) {
        if (playerId == null) {
            return false;
        }

        LocalDateTime joinTime = members.remove(playerId);
        if (joinTime != null) {
            // 同时移除管理员和禁言状态
            administrators.remove(playerId);
            mutedMembers.remove(playerId);
            log.info("玩家 {} 离开频道 {}, 当前成员数: {}", playerId, channelId, members.size());
            return true;
        }

        return false;
    }

    /**
     * 检查是否为频道成员
     *
     * @param playerId 玩家ID
     * @return true表示是成员
     */
    public boolean isMember(Long playerId) {
        return playerId != null && members.containsKey(playerId);
    }

    /**
     * 获取成员数量
     *
     * @return 成员数量
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * 获取所有成员ID
     *
     * @return 成员ID列表
     */
    public List<Long> getMemberIds() {
        return new ArrayList<>(members.keySet());
    }

    // ===== 权限管理方法 =====

    /**
     * 添加管理员
     *
     * @param playerId 玩家ID
     */
    public void addAdministrator(Long playerId) {
        if (playerId != null && isMember(playerId)) {
            administrators.add(playerId);
            log.info("玩家 {} 被设置为频道 {} 的管理员", playerId, channelId);
        }
    }

    /**
     * 移除管理员
     *
     * @param playerId 玩家ID
     */
    public void removeAdministrator(Long playerId) {
        if (administrators.remove(playerId)) {
            log.info("玩家 {} 被移除频道 {} 的管理员权限", playerId, channelId);
        }
    }

    /**
     * 检查是否为管理员
     *
     * @param playerId 玩家ID
     * @return true表示是管理员
     */
    public boolean isAdministrator(Long playerId) {
        return playerId != null && administrators.contains(playerId);
    }

    // ===== 禁言管理方法 =====

    /**
     * 禁言用户
     *
     * @param playerId 玩家ID
     * @param muteUntil 禁言到期时间
     */
    public void muteUser(Long playerId, LocalDateTime muteUntil) {
        if (playerId != null && isMember(playerId)) {
            mutedMembers.put(playerId, muteUntil);
            log.info("玩家 {} 在频道 {} 被禁言至 {}", playerId, channelId, muteUntil);
        }
    }

    /**
     * 取消禁言
     *
     * @param playerId 玩家ID
     */
    public void unmuteUser(Long playerId) {
        if (mutedMembers.remove(playerId) != null) {
            log.info("玩家 {} 在频道 {} 的禁言被解除", playerId, channelId);
        }
    }

    /**
     * 检查用户是否被禁言
     *
     * @param playerId 玩家ID
     * @return true表示被禁言
     */
    public boolean isMuted(Long playerId) {
        if (playerId == null) {
            return false;
        }

        LocalDateTime muteUntil = mutedMembers.get(playerId);
        if (muteUntil == null) {
            return false;
        }

        // 检查是否已到期
        if (LocalDateTime.now().isAfter(muteUntil)) {
            mutedMembers.remove(playerId);
            return false;
        }

        return true;
    }

    // ===== 频道状态管理 =====

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * 增加消息计数
     */
    public void incrementMessageCount() {
        messageCounter.incrementAndGet();
    }

    /**
     * 获取消息总数
     *
     * @return 消息总数
     */
    public long getMessageCount() {
        return messageCounter.get();
    }

    /**
     * 检查频道是否活跃
     *
     * @return true表示活跃
     */
    public boolean isActive() {
        return ChannelStatus.ACTIVE.equals(status);
    }

    /**
     * 关闭频道
     */
    public void close() {
        this.status = ChannelStatus.CLOSED;
        log.info("频道 {} 已关闭", channelId);
    }

    /**
     * 获取频道属性
     *
     * @param key 属性键
     * @return 属性值
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * 设置频道属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
}