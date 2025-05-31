/*
 * 文件名: ChatMessage.java
 * 用途: 聊天消息实体类
 * 实现内容:
 *   - 聊天消息的完整数据模型定义
 *   - 支持多种消息类型（文本、表情、语音、图片、系统消息等）
 *   - 包含发送者和接收者信息
 *   - 消息状态跟踪（已发送、已送达、已读）
 *   - 消息时间戳和全局唯一ID
 * 技术选型:
 *   - 使用Lombok简化代码
 *   - 支持JSON序列化
 *   - 包含数据验证注解
 * 依赖关系:
 *   - 作为聊天系统的核心数据模型
 *   - 被MessageService、ChannelManager等使用
 *   - 与IChatService接口中的ChatMessage保持兼容
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

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天消息实体
 * <p>
 * 定义聊天消息的完整数据结构，包含消息内容、发送者信息、接收者信息、
 * 消息类型、状态等核心属性。支持扩展数据存储，可以处理富文本、
 * 表情、附件等复杂消息内容。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（全局唯一）
     */
    private String messageId;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 发送者昵称
     */
    private String senderName;

    /**
     * 发送者头像URL
     */
    private String senderAvatar;

    /**
     * 发送者等级
     */
    private Integer senderLevel;

    /**
     * 发送者VIP等级
     */
    private Integer senderVipLevel;

    /**
     * 接收者ID（私聊时使用）
     */
    private Long receiverId;

    /**
     * 接收者昵称（私聊时使用）
     */
    private String receiverName;

    /**
     * 频道类型
     */
    private ChatChannelType channelType;

    /**
     * 频道ID（公会聊天、队伍聊天等使用）
     */
    private String channelId;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息原始内容（过滤前）
     */
    private String originalContent;

    /**
     * 发送时间戳
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sendTime;

    /**
     * 消息状态
     */
    private MessageStatus status;

    /**
     * 消息优先级
     */
    private MessagePriority priority;

    /**
     * 扩展数据
     */
    private Map<String, Object> extraData;

    /**
     * 消息来源服务器
     */
    private String sourceServer;

    /**
     * 消息序列号
     */
    private Long sequence;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 已读时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    /**
     * 消息大小（字节）
     */
    private Integer messageSize;

    /**
     * 是否为系统消息
     */
    private Boolean isSystemMessage;

    /**
     * 消息过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /**
     * 聊天频道类型枚举
     */
    public enum ChatChannelType {
        /** 世界频道 */
        WORLD("world", "世界频道"),
        /** 公会频道 */
        GUILD("guild", "公会频道"),
        /** 队伍频道 */
        TEAM("team", "队伍频道"),
        /** 私聊频道 */
        PRIVATE("private", "私聊"),
        /** 系统频道 */
        SYSTEM("system", "系统公告"),
        /** 自定义频道 */
        CUSTOM("custom", "自定义频道");

        private final String code;
        private final String description;

        ChatChannelType(String code, String description) {
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
     * 消息类型枚举
     */
    public enum MessageType {
        /** 文本消息 */
        TEXT("text", "文本消息"),
        /** 表情消息 */
        EMOJI("emoji", "表情消息"),
        /** 语音消息 */
        VOICE("voice", "语音消息"),
        /** 图片消息 */
        IMAGE("image", "图片消息"),
        /** 系统消息 */
        SYSTEM("system", "系统消息"),
        /** 物品链接 */
        ITEM_LINK("item_link", "物品链接"),
        /** 玩家链接 */
        PLAYER_LINK("player_link", "玩家链接"),
        /** 坐标链接 */
        LOCATION_LINK("location_link", "坐标链接"),
        /** 视频消息 */
        VIDEO("video", "视频消息"),
        /** 文件消息 */
        FILE("file", "文件消息");

        private final String code;
        private final String description;

        MessageType(String code, String description) {
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
     * 消息状态枚举
     */
    public enum MessageStatus {
        /** 已发送 */
        SENT("sent", "已发送"),
        /** 已送达 */
        DELIVERED("delivered", "已送达"),
        /** 已读 */
        READ("read", "已读"),
        /** 发送失败 */
        FAILED("failed", "发送失败"),
        /** 已撤回 */
        RECALLED("recalled", "已撤回"),
        /** 已删除 */
        DELETED("deleted", "已删除");

        private final String code;
        private final String description;

        MessageStatus(String code, String description) {
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
     * 消息优先级枚举
     */
    public enum MessagePriority {
        /** 低优先级 */
        LOW(1, "低优先级"),
        /** 普通优先级 */
        NORMAL(2, "普通优先级"),
        /** 高优先级 */
        HIGH(3, "高优先级"),
        /** 紧急优先级 */
        URGENT(4, "紧急优先级");

        private final int level;
        private final String description;

        MessagePriority(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 获取消息内容长度
     *
     * @return 消息内容长度
     */
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * 检查是否为私聊消息
     *
     * @return true表示私聊消息
     */
    public boolean isPrivateMessage() {
        return ChatChannelType.PRIVATE.equals(channelType);
    }

    /**
     * 检查是否为系统消息
     *
     * @return true表示系统消息
     */
    public boolean isSystemMsg() {
        return Boolean.TRUE.equals(isSystemMessage) || 
               ChatChannelType.SYSTEM.equals(channelType) ||
               MessageType.SYSTEM.equals(messageType);
    }

    /**
     * 检查消息是否已过期
     *
     * @return true表示已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 获取扩展数据
     *
     * @param key 数据键
     * @return 数据值
     */
    public Object getExtraData(String key) {
        return extraData != null ? extraData.get(key) : null;
    }

    /**
     * 设置扩展数据
     *
     * @param key 数据键
     * @param value 数据值
     */
    public void setExtraData(String key, Object value) {
        if (extraData == null) {
            extraData = new java.util.HashMap<>();
        }
        extraData.put(key, value);
    }

    /**
     * 创建消息副本
     *
     * @return 消息副本
     */
    public ChatMessage copy() {
        ChatMessage copy = new ChatMessage();
        copy.messageId = this.messageId;
        copy.senderId = this.senderId;
        copy.senderName = this.senderName;
        copy.senderAvatar = this.senderAvatar;
        copy.senderLevel = this.senderLevel;
        copy.senderVipLevel = this.senderVipLevel;
        copy.receiverId = this.receiverId;
        copy.receiverName = this.receiverName;
        copy.channelType = this.channelType;
        copy.channelId = this.channelId;
        copy.messageType = this.messageType;
        copy.content = this.content;
        copy.originalContent = this.originalContent;
        copy.sendTime = this.sendTime;
        copy.status = this.status;
        copy.priority = this.priority;
        copy.extraData = this.extraData != null ? new java.util.HashMap<>(this.extraData) : null;
        copy.sourceServer = this.sourceServer;
        copy.sequence = this.sequence;
        copy.isRead = this.isRead;
        copy.readTime = this.readTime;
        copy.messageSize = this.messageSize;
        copy.isSystemMessage = this.isSystemMessage;
        copy.expireTime = this.expireTime;
        return copy;
    }
}