/*
 * 文件名: IChatService.java
 * 用途: 聊天服务接口定义
 * 实现内容:
 *   - 定义聊天消息发送和接收接口
 *   - 定义聊天历史查询和管理接口
 *   - 定义禁言和聊天管理接口
 *   - 支持多种聊天频道和消息类型
 * 技术选型:
 *   - 使用Java接口定义服务规范
 *   - 集成Result和PageResult通用返回类型
 *   - 支持异步消息处理和回调
 * 依赖关系:
 *   - 依赖common-core的Result和PageResult
 *   - 被chat-service模块实现
 *   - 被需要聊天功能的模块调用
 */
package com.lx.gameserver.api;

import com.lx.gameserver.common.Result;
import com.lx.gameserver.common.PageResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天服务接口
 * <p>
 * 定义了聊天系统的所有核心功能，包括消息发送、接收、历史查询、
 * 禁言管理等。支持多种聊天频道（世界、公会、私聊等）和消息类型
 * （文本、表情、图片等），提供完整的聊天体验。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public interface IChatService {

    // ===== 消息发送接口 =====

    /**
     * 发送聊天消息
     *
     * @param senderId    发送者ID
     * @param channel     聊天频道
     * @param messageType 消息类型
     * @param content     消息内容
     * @param targetId    目标ID（私聊时为对方ID，公会聊天时为公会ID）
     * @param extraData   扩展数据（表情、富文本等）
     * @return 发送结果，包含消息ID
     */
    Result<SendMessageResult> sendMessage(Long senderId, ChatChannel channel, MessageType messageType,
                                         String content, Long targetId, Map<String, String> extraData);

    /**
     * 异步发送聊天消息
     *
     * @param senderId    发送者ID
     * @param channel     聊天频道
     * @param messageType 消息类型
     * @param content     消息内容
     * @param targetId    目标ID
     * @param extraData   扩展数据
     * @return 发送结果的Future
     */
    CompletableFuture<Result<SendMessageResult>> sendMessageAsync(Long senderId, ChatChannel channel, 
                                                                 MessageType messageType, String content, 
                                                                 Long targetId, Map<String, String> extraData);

    /**
     * 发送系统消息
     *
     * @param channel   聊天频道
     * @param content   消息内容
     * @param targetIds 目标玩家ID列表（为空表示发送给所有人）
     * @return 发送结果
     */
    Result<Void> sendSystemMessage(ChatChannel channel, String content, List<Long> targetIds);

    /**
     * 发送公告消息
     *
     * @param title     公告标题
     * @param content   公告内容
     * @param targetIds 目标玩家ID列表（为空表示全服公告）
     * @return 发送结果
     */
    Result<Void> sendAnnouncement(String title, String content, List<Long> targetIds);

    /**
     * 发送私聊消息
     *
     * @param senderId    发送者ID
     * @param receiverId  接收者ID
     * @param messageType 消息类型
     * @param content     消息内容
     * @param extraData   扩展数据
     * @return 发送结果
     */
    Result<SendMessageResult> sendPrivateMessage(Long senderId, Long receiverId, MessageType messageType,
                                                String content, Map<String, String> extraData);

    // ===== 消息接收和查询接口 =====

    /**
     * 获取聊天历史记录
     *
     * @param playerId  玩家ID
     * @param channel   聊天频道
     * @param targetId  目标ID（私聊时为对方ID）
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 聊天历史分页结果
     */
    PageResult<ChatMessage> getChatHistory(Long playerId, ChatChannel channel, Long targetId,
                                          int pageNum, int pageSize, Long startTime, Long endTime);

    /**
     * 获取最新聊天消息
     *
     * @param playerId 玩家ID
     * @param channel  聊天频道
     * @param targetId 目标ID
     * @param count    消息数量
     * @return 最新消息列表
     */
    Result<List<ChatMessage>> getLatestMessages(Long playerId, ChatChannel channel, Long targetId, int count);

    /**
     * 获取未读私聊消息
     *
     * @param playerId 玩家ID
     * @return 未读私聊消息列表
     */
    Result<List<ChatMessage>> getUnreadPrivateMessages(Long playerId);

    /**
     * 获取私聊会话列表
     *
     * @param playerId 玩家ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 私聊会话分页列表
     */
    PageResult<PrivateChatSession> getPrivateChatSessions(Long playerId, int pageNum, int pageSize);

    /**
     * 标记消息为已读
     *
     * @param playerId  玩家ID
     * @param messageId 消息ID
     * @return 操作结果
     */
    Result<Void> markMessageAsRead(Long playerId, Long messageId);

    /**
     * 批量标记消息为已读
     *
     * @param playerId   玩家ID
     * @param messageIds 消息ID列表
     * @return 操作结果
     */
    Result<Void> markMessagesAsRead(Long playerId, List<Long> messageIds);

    /**
     * 标记会话为已读
     *
     * @param playerId   玩家ID
     * @param partnerId  对方玩家ID
     * @return 操作结果
     */
    Result<Void> markSessionAsRead(Long playerId, Long partnerId);

    // ===== 禁言管理接口 =====

    /**
     * 禁言玩家
     *
     * @param operatorId   操作者ID
     * @param targetId     目标玩家ID
     * @param channel      禁言频道
     * @param duration     禁言时长（秒，0表示永久禁言）
     * @param reason       禁言原因
     * @return 禁言结果
     */
    Result<MuteResult> mutePlayer(Long operatorId, Long targetId, ChatChannel channel, 
                                 int duration, String reason);

    /**
     * 解除禁言
     *
     * @param operatorId 操作者ID
     * @param targetId   目标玩家ID
     * @param channel    解禁频道
     * @param reason     解禁原因
     * @return 解禁结果
     */
    Result<Void> unmutePlayer(Long operatorId, Long targetId, ChatChannel channel, String reason);

    /**
     * 检查玩家是否被禁言
     *
     * @param playerId 玩家ID
     * @param channel  聊天频道
     * @return 禁言状态
     */
    Result<MuteStatus> checkMuteStatus(Long playerId, ChatChannel channel);

    /**
     * 获取禁言列表
     *
     * @param channel  聊天频道（可选，为空则获取所有频道）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 禁言记录分页列表
     */
    PageResult<MuteRecord> getMuteList(ChatChannel channel, int pageNum, int pageSize);

    /**
     * 获取玩家禁言历史
     *
     * @param playerId 玩家ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 禁言历史分页列表
     */
    PageResult<MuteRecord> getPlayerMuteHistory(Long playerId, int pageNum, int pageSize);

    // ===== 聊天管理接口 =====

    /**
     * 删除聊天消息
     *
     * @param operatorId 操作者ID
     * @param messageId  消息ID
     * @param reason     删除原因
     * @return 删除结果
     */
    Result<Void> deleteMessage(Long operatorId, Long messageId, String reason);

    /**
     * 撤回消息（玩家主动撤回）
     *
     * @param playerId  玩家ID
     * @param messageId 消息ID
     * @return 撤回结果
     */
    Result<Void> recallMessage(Long playerId, Long messageId);

    /**
     * 设置聊天频道状态
     *
     * @param channel 聊天频道
     * @param enabled 是否启用
     * @return 设置结果
     */
    Result<Void> setChatChannelStatus(ChatChannel channel, boolean enabled);

    /**
     * 获取聊天频道状态
     *
     * @param channel 聊天频道
     * @return 频道状态
     */
    Result<Boolean> getChatChannelStatus(ChatChannel channel);

    /**
     * 清理过期消息
     *
     * @param channel    聊天频道
     * @param retentionDays 保留天数
     * @return 清理结果，包含清理的消息数量
     */
    Result<Integer> cleanupExpiredMessages(ChatChannel channel, int retentionDays);

    // ===== 统计接口 =====

    /**
     * 获取聊天统计信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 聊天统计信息
     */
    Result<ChatStatistics> getChatStatistics(Long startTime, Long endTime);

    /**
     * 获取玩家聊天统计
     *
     * @param playerId  玩家ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 玩家聊天统计
     */
    Result<PlayerChatStatistics> getPlayerChatStatistics(Long playerId, Long startTime, Long endTime);

    // ===== 内部数据结构定义 =====

    /**
     * 聊天频道枚举
     */
    enum ChatChannel {
        /** 世界频道 */
        WORLD,
        /** 当前频道 */
        CURRENT,
        /** 公会频道 */
        GUILD,
        /** 队伍频道 */
        TEAM,
        /** 私聊频道 */
        PRIVATE,
        /** 系统频道 */
        SYSTEM,
        /** 喇叭频道 */
        TRUMPET
    }

    /**
     * 消息类型枚举
     */
    enum MessageType {
        /** 文本消息 */
        TEXT,
        /** 表情消息 */
        EMOJI,
        /** 图片消息 */
        IMAGE,
        /** 语音消息 */
        VOICE,
        /** 系统消息 */
        SYSTEM,
        /** 物品链接 */
        ITEM_LINK,
        /** 玩家链接 */
        PLAYER_LINK,
        /** 坐标链接 */
        LOCATION_LINK
    }

    /**
     * 发送消息结果
     */
    class SendMessageResult {
        /** 消息ID */
        private Long messageId;
        /** 发送时间 */
        private Long sendTime;
        /** 是否成功 */
        private Boolean success;

        // Getters and Setters
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public Long getSendTime() { return sendTime; }
        public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
    }

    /**
     * 聊天消息
     */
    class ChatMessage {
        /** 消息ID */
        private Long messageId;
        /** 发送者ID */
        private Long senderId;
        /** 发送者昵称 */
        private String senderName;
        /** 发送者头像 */
        private String senderAvatar;
        /** 聊天频道 */
        private ChatChannel channel;
        /** 消息类型 */
        private MessageType messageType;
        /** 消息内容 */
        private String content;
        /** 目标ID */
        private Long targetId;
        /** 发送时间 */
        private Long sendTime;
        /** 是否已读 */
        private Boolean read;
        /** 扩展数据 */
        private Map<String, String> extraData;

        // Getters and Setters
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
        public String getSenderAvatar() { return senderAvatar; }
        public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }
        public ChatChannel getChannel() { return channel; }
        public void setChannel(ChatChannel channel) { this.channel = channel; }
        public MessageType getMessageType() { return messageType; }
        public void setMessageType(MessageType messageType) { this.messageType = messageType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
        public Long getSendTime() { return sendTime; }
        public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
        public Boolean getRead() { return read; }
        public void setRead(Boolean read) { this.read = read; }
        public Map<String, String> getExtraData() { return extraData; }
        public void setExtraData(Map<String, String> extraData) { this.extraData = extraData; }
    }

    /**
     * 私聊会话
     */
    class PrivateChatSession {
        /** 对方玩家ID */
        private Long partnerId;
        /** 对方玩家昵称 */
        private String partnerName;
        /** 对方玩家头像 */
        private String partnerAvatar;
        /** 最后一条消息 */
        private ChatMessage lastMessage;
        /** 未读消息数 */
        private Integer unreadCount;
        /** 最后更新时间 */
        private Long lastUpdateTime;

        // Getters and Setters
        public Long getPartnerId() { return partnerId; }
        public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
        public String getPartnerName() { return partnerName; }
        public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
        public String getPartnerAvatar() { return partnerAvatar; }
        public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }
        public ChatMessage getLastMessage() { return lastMessage; }
        public void setLastMessage(ChatMessage lastMessage) { this.lastMessage = lastMessage; }
        public Integer getUnreadCount() { return unreadCount; }
        public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }

    /**
     * 禁言结果
     */
    class MuteResult {
        /** 禁言是否成功 */
        private Boolean success;
        /** 禁言结束时间 */
        private Long muteEndTime;
        /** 禁言记录ID */
        private Long muteRecordId;

        // Getters and Setters
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Long getMuteEndTime() { return muteEndTime; }
        public void setMuteEndTime(Long muteEndTime) { this.muteEndTime = muteEndTime; }
        public Long getMuteRecordId() { return muteRecordId; }
        public void setMuteRecordId(Long muteRecordId) { this.muteRecordId = muteRecordId; }
    }

    /**
     * 禁言状态
     */
    class MuteStatus {
        /** 是否被禁言 */
        private Boolean muted;
        /** 禁言结束时间 */
        private Long muteEndTime;
        /** 禁言原因 */
        private String reason;

        // Getters and Setters
        public Boolean getMuted() { return muted; }
        public void setMuted(Boolean muted) { this.muted = muted; }
        public Long getMuteEndTime() { return muteEndTime; }
        public void setMuteEndTime(Long muteEndTime) { this.muteEndTime = muteEndTime; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * 禁言记录
     */
    class MuteRecord {
        /** 记录ID */
        private Long recordId;
        /** 被禁言玩家ID */
        private Long playerId;
        /** 被禁言玩家名称 */
        private String playerName;
        /** 操作者ID */
        private Long operatorId;
        /** 操作者名称 */
        private String operatorName;
        /** 禁言频道 */
        private ChatChannel channel;
        /** 禁言开始时间 */
        private Long muteStartTime;
        /** 禁言结束时间 */
        private Long muteEndTime;
        /** 禁言原因 */
        private String reason;
        /** 禁言状态 */
        private String status;

        // Getters and Setters
        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Long getOperatorId() { return operatorId; }
        public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
        public ChatChannel getChannel() { return channel; }
        public void setChannel(ChatChannel channel) { this.channel = channel; }
        public Long getMuteStartTime() { return muteStartTime; }
        public void setMuteStartTime(Long muteStartTime) { this.muteStartTime = muteStartTime; }
        public Long getMuteEndTime() { return muteEndTime; }
        public void setMuteEndTime(Long muteEndTime) { this.muteEndTime = muteEndTime; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * 聊天统计信息
     */
    class ChatStatistics {
        /** 总消息数 */
        private Long totalMessages;
        /** 各频道消息数统计 */
        private Map<ChatChannel, Long> channelMessageCounts;
        /** 活跃用户数 */
        private Integer activeUsers;
        /** 平均消息长度 */
        private Double averageMessageLength;

        // Getters and Setters
        public Long getTotalMessages() { return totalMessages; }
        public void setTotalMessages(Long totalMessages) { this.totalMessages = totalMessages; }
        public Map<ChatChannel, Long> getChannelMessageCounts() { return channelMessageCounts; }
        public void setChannelMessageCounts(Map<ChatChannel, Long> channelMessageCounts) { 
            this.channelMessageCounts = channelMessageCounts; 
        }
        public Integer getActiveUsers() { return activeUsers; }
        public void setActiveUsers(Integer activeUsers) { this.activeUsers = activeUsers; }
        public Double getAverageMessageLength() { return averageMessageLength; }
        public void setAverageMessageLength(Double averageMessageLength) { 
            this.averageMessageLength = averageMessageLength; 
        }
    }

    /**
     * 玩家聊天统计
     */
    class PlayerChatStatistics {
        /** 发送消息数 */
        private Integer messagesSent;
        /** 接收消息数 */
        private Integer messagesReceived;
        /** 各频道发送数统计 */
        private Map<ChatChannel, Integer> channelSentCounts;
        /** 最活跃频道 */
        private ChatChannel mostActiveChannel;

        // Getters and Setters
        public Integer getMessagesSent() { return messagesSent; }
        public void setMessagesSent(Integer messagesSent) { this.messagesSent = messagesSent; }
        public Integer getMessagesReceived() { return messagesReceived; }
        public void setMessagesReceived(Integer messagesReceived) { this.messagesReceived = messagesReceived; }
        public Map<ChatChannel, Integer> getChannelSentCounts() { return channelSentCounts; }
        public void setChannelSentCounts(Map<ChatChannel, Integer> channelSentCounts) { 
            this.channelSentCounts = channelSentCounts; 
        }
        public ChatChannel getMostActiveChannel() { return mostActiveChannel; }
        public void setMostActiveChannel(ChatChannel mostActiveChannel) { this.mostActiveChannel = mostActiveChannel; }
    }
}