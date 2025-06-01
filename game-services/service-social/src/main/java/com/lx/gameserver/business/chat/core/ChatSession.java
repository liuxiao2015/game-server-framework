/*
 * 文件名: ChatSession.java
 * 用途: 聊天会话管理类
 * 实现内容:
 *   - 聊天会话的生命周期管理
 *   - 会话参与者管理和状态跟踪
 *   - 未读消息计数和最后消息时间
 *   - 会话类型定义和会话配置
 *   - 会话数据的持久化支持
 * 技术选型:
 *   - 使用ConcurrentHashMap保证线程安全
 *   - 支持会话扩展属性
 *   - 提供会话状态监听机制
 * 依赖关系:
 *   - 与ConnectionManager配合管理用户会话
 *   - 为MessageService提供会话上下文
 *   - 支持离线消息服务的会话恢复
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天会话管理类
 * <p>
 * 管理聊天会话的完整生命周期，包含会话创建、参与者管理、
 * 消息状态跟踪、未读计数等功能。支持多种会话类型，
 * 包括私聊、群聊、频道聊天等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（全局唯一）
     */
    private String sessionId;

    /**
     * 会话类型
     */
    private SessionType sessionType;

    /**
     * 关联的频道ID（如果适用）
     */
    private String channelId;

    /**
     * 会话创建者ID
     */
    private Long creatorId;

    /**
     * 会话名称
     */
    private String sessionName;

    /**
     * 会话描述
     */
    private String description;

    /**
     * 会话状态
     */
    private SessionStatus status;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 最后消息时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;

    /**
     * 最后活跃时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;

    /**
     * 会话过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /**
     * 参与者列表（PlayerId -> 参与信息）
     */
    private final Map<Long, ParticipantInfo> participants = new ConcurrentHashMap<>();

    /**
     * 未读消息计数（PlayerId -> 未读数量）
     */
    private final Map<Long, AtomicLong> unreadCounts = new ConcurrentHashMap<>();

    /**
     * 最后读取时间（PlayerId -> 读取时间）
     */
    private final Map<Long, LocalDateTime> lastReadTimes = new ConcurrentHashMap<>();

    /**
     * 会话扩展属性
     */
    private Map<String, Object> properties;

    /**
     * 会话配置
     */
    private SessionConfig config;

    /**
     * 消息总数
     */
    private final AtomicLong totalMessageCount = new AtomicLong(0);

    /**
     * 会话类型枚举
     */
    public enum SessionType {
        /** 私聊会话 */
        PRIVATE("private", "私聊"),
        /** 群聊会话 */
        GROUP("group", "群聊"),
        /** 频道会话 */
        CHANNEL("channel", "频道"),
        /** 系统会话 */
        SYSTEM("system", "系统"),
        /** 临时会话 */
        TEMPORARY("temporary", "临时");

        private final String code;
        private final String description;

        SessionType(String code, String description) {
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
     * 会话状态枚举
     */
    public enum SessionStatus {
        /** 活跃状态 */
        ACTIVE("active", "活跃"),
        /** 暂停状态 */
        PAUSED("paused", "暂停"),
        /** 已结束 */
        ENDED("ended", "已结束"),
        /** 已删除 */
        DELETED("deleted", "已删除");

        private final String code;
        private final String description;

        SessionStatus(String code, String description) {
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
     * 参与者信息类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 玩家ID */
        private Long playerId;
        
        /** 玩家昵称 */
        private String playerName;
        
        /** 加入时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime joinTime;
        
        /** 最后活跃时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastActiveTime;
        
        /** 角色类型 */
        private ParticipantRole role;
        
        /** 是否在线 */
        private Boolean online;
        
        /** 扩展信息 */
        private Map<String, Object> extraInfo;
    }

    /**
     * 参与者角色枚举
     */
    public enum ParticipantRole {
        /** 创建者 */
        CREATOR("creator", "创建者"),
        /** 管理员 */
        ADMIN("admin", "管理员"),
        /** 普通成员 */
        MEMBER("member", "成员"),
        /** 访客 */
        GUEST("guest", "访客");

        private final String code;
        private final String description;

        ParticipantRole(String code, String description) {
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
     * 会话配置类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 最大参与者数量 */
        private Integer maxParticipants = 100;
        
        /** 消息保留天数 */
        private Integer messageRetentionDays = 30;
        
        /** 是否允许邀请其他人 */
        private Boolean allowInvite = true;
        
        /** 是否允许匿名参与 */
        private Boolean allowAnonymous = false;
        
        /** 是否记录消息历史 */
        private Boolean recordHistory = true;
        
        /** 是否启用消息加密 */
        private Boolean enableEncryption = false;
        
        /** 会话图标URL */
        private String iconUrl;
        
        /** 会话背景图URL */
        private String backgroundUrl;
        
        /** 扩展配置 */
        private Map<String, Object> extraConfig;
    }

    /**
     * 构造函数
     *
     * @param sessionId 会话ID
     * @param sessionType 会话类型
     * @param creatorId 创建者ID
     */
    public ChatSession(String sessionId, SessionType sessionType, Long creatorId) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.creatorId = creatorId;
        this.status = SessionStatus.ACTIVE;
        this.createTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.config = new SessionConfig();
        this.properties = new HashMap<>();
    }

    // ===== 参与者管理方法 =====

    /**
     * 添加参与者
     *
     * @param playerId 玩家ID
     * @param playerName 玩家昵称
     * @param role 角色
     * @return 添加结果
     */
    public boolean addParticipant(Long playerId, String playerName, ParticipantRole role) {
        if (playerId == null) {
            return false;
        }

        // 检查是否已存在
        if (participants.containsKey(playerId)) {
            log.debug("玩家 {} 已在会话 {} 中", playerId, sessionId);
            return true;
        }

        // 检查人数限制
        if (config.maxParticipants != null && participants.size() >= config.maxParticipants) {
            log.warn("会话 {} 已达到最大参与者数量限制: {}", sessionId, config.maxParticipants);
            return false;
        }

        ParticipantInfo info = ParticipantInfo.builder()
                .playerId(playerId)
                .playerName(playerName)
                .joinTime(LocalDateTime.now())
                .lastActiveTime(LocalDateTime.now())
                .role(role != null ? role : ParticipantRole.MEMBER)
                .online(true)
                .extraInfo(new HashMap<>())
                .build();

        participants.put(playerId, info);
        unreadCounts.put(playerId, new AtomicLong(0));
        lastReadTimes.put(playerId, LocalDateTime.now());

        log.info("玩家 {} 加入会话 {}, 当前参与者数: {}", playerId, sessionId, participants.size());
        return true;
    }

    /**
     * 移除参与者
     *
     * @param playerId 玩家ID
     * @return 移除结果
     */
    public boolean removeParticipant(Long playerId) {
        if (playerId == null) {
            return false;
        }

        ParticipantInfo info = participants.remove(playerId);
        if (info != null) {
            unreadCounts.remove(playerId);
            lastReadTimes.remove(playerId);
            log.info("玩家 {} 离开会话 {}, 当前参与者数: {}", playerId, sessionId, participants.size());
            return true;
        }

        return false;
    }

    /**
     * 检查是否为参与者
     *
     * @param playerId 玩家ID
     * @return true表示是参与者
     */
    public boolean isParticipant(Long playerId) {
        return playerId != null && participants.containsKey(playerId);
    }

    /**
     * 获取参与者信息
     *
     * @param playerId 玩家ID
     * @return 参与者信息
     */
    public ParticipantInfo getParticipant(Long playerId) {
        return participants.get(playerId);
    }

    /**
     * 获取所有参与者ID
     *
     * @return 参与者ID列表
     */
    public List<Long> getParticipantIds() {
        return new ArrayList<>(participants.keySet());
    }

    /**
     * 获取参与者数量
     *
     * @return 参与者数量
     */
    public int getParticipantCount() {
        return participants.size();
    }

    // ===== 未读消息管理 =====

    /**
     * 增加未读消息数量
     *
     * @param playerId 玩家ID
     * @param count 增加数量
     */
    public void incrementUnreadCount(Long playerId, int count) {
        if (playerId != null && count > 0) {
            unreadCounts.computeIfAbsent(playerId, k -> new AtomicLong(0))
                    .addAndGet(count);
        }
    }

    /**
     * 获取未读消息数量
     *
     * @param playerId 玩家ID
     * @return 未读消息数量
     */
    public long getUnreadCount(Long playerId) {
        if (playerId == null) {
            return 0;
        }
        AtomicLong count = unreadCounts.get(playerId);
        return count != null ? count.get() : 0;
    }

    /**
     * 标记消息为已读
     *
     * @param playerId 玩家ID
     * @param readTime 阅读时间
     */
    public void markAsRead(Long playerId, LocalDateTime readTime) {
        if (playerId != null) {
            AtomicLong count = unreadCounts.get(playerId);
            if (count != null) {
                count.set(0);
            }
            lastReadTimes.put(playerId, readTime != null ? readTime : LocalDateTime.now());
        }
    }

    /**
     * 获取最后阅读时间
     *
     * @param playerId 玩家ID
     * @return 最后阅读时间
     */
    public LocalDateTime getLastReadTime(Long playerId) {
        return lastReadTimes.get(playerId);
    }

    // ===== 会话状态管理 =====

    /**
     * 更新最后消息时间
     */
    public void updateLastMessageTime() {
        this.lastMessageTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * 增加消息总数
     */
    public void incrementMessageCount() {
        totalMessageCount.incrementAndGet();
    }

    /**
     * 获取消息总数
     *
     * @return 消息总数
     */
    public long getTotalMessageCount() {
        return totalMessageCount.get();
    }

    /**
     * 检查会话是否活跃
     *
     * @return true表示活跃
     */
    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(status);
    }

    /**
     * 检查会话是否已过期
     *
     * @return true表示已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 结束会话
     */
    public void endSession() {
        this.status = SessionStatus.ENDED;
        log.info("会话 {} 已结束", sessionId);
    }

    /**
     * 删除会话
     */
    public void deleteSession() {
        this.status = SessionStatus.DELETED;
        log.info("会话 {} 已删除", sessionId);
    }

    // ===== 扩展属性管理 =====

    /**
     * 获取扩展属性
     *
     * @param key 属性键
     * @return 属性值
     */
    public Object getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }

    /**
     * 设置扩展属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void setProperty(String key, Object value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }

    /**
     * 移除扩展属性
     *
     * @param key 属性键
     */
    public void removeProperty(String key) {
        if (properties != null) {
            properties.remove(key);
        }
    }
}