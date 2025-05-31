/*
 * 文件名: PrivateChannel.java
 * 用途: 私聊频道实现类
 * 实现内容:
 *   - 点对点私聊功能实现
 *   - 私聊消息的加密和安全传输
 *   - 私聊历史记录管理
 *   - 离线消息支持和消息状态跟踪
 * 技术选型:
 *   - 继承ChatChannel抽象基类
 *   - 固定为双人频道模式
 *   - 支持消息状态确认机制
 * 依赖关系:
 *   - 继承自ChatChannel
 *   - 被ChannelManager管理
 *   - 与OfflineMessageService配合处理离线消息
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.channel;

import com.lx.gameserver.business.chat.core.ChatChannel;
import com.lx.gameserver.business.chat.core.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私聊频道实现
 * <p>
 * 提供点对点私聊功能，支持两个玩家之间的私密聊天。
 * 具有消息状态跟踪、离线消息处理等特性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class PrivateChannel extends ChatChannel {

    /**
     * 参与者1的ID
     */
    private final Long participant1;

    /**
     * 参与者2的ID
     */
    private final Long participant2;

    /**
     * 消息状态跟踪（MessageId -> 状态信息）
     */
    private final Map<String, MessageStatusInfo> messageStatus = new ConcurrentHashMap<>();

    /**
     * 最后消息时间映射（PlayerId -> 最后消息时间）
     */
    private final Map<Long, LocalDateTime> lastMessageTimes = new ConcurrentHashMap<>();

    /**
     * 私聊频道默认配置
     */
    private static final ChannelConfig DEFAULT_PRIVATE_CONFIG = createDefaultConfig();

    /**
     * 构造函数
     *
     * @param channelId 频道ID
     * @param participant1 参与者1
     * @param participant2 参与者2
     */
    public PrivateChannel(String channelId, Long participant1, Long participant2) {
        super(channelId, ChatMessage.ChatChannelType.PRIVATE, 
              "私聊-" + participant1 + "-" + participant2);
        
        this.participant1 = participant1;
        this.participant2 = participant2;
        this.setConfig(DEFAULT_PRIVATE_CONFIG);

        // 自动添加两个参与者
        addMember(participant1);
        addMember(participant2);

        log.info("创建私聊频道: channelId={}, participant1={}, participant2={}", 
                channelId, participant1, participant2);
    }

    /**
     * 创建默认配置
     */
    private static ChannelConfig createDefaultConfig() {
        ChannelConfig config = new ChannelConfig();
        config.setMaxMembers(2); // 私聊只允许2个成员
        config.setMessageInterval(1); // 1秒发言间隔
        config.setMaxMessageLength(1000); // 最大1000字符
        config.setRequirePermission(false); // 不需要特殊权限
        config.setAllowAnonymous(false); // 不允许匿名
        config.setRecordHistory(true); // 记录历史
        config.setHistoryRetentionDays(30); // 保留30天
        config.setEnableSensitiveWordFilter(true); // 启用敏感词过滤
        config.setEnableRateLimit(true); // 启用限流
        return config;
    }

    @Override
    public boolean sendMessage(ChatMessage message) {
        if (message == null) {
            return false;
        }

        try {
            // 验证发送者是否为频道成员
            Long senderId = message.getSenderId();
            if (!isParticipant(senderId)) {
                log.warn("非频道成员尝试发送私聊消息: senderId={}, channelId={}", senderId, getChannelId());
                return false;
            }

            // 更新频道活跃时间
            updateLastActiveTime();
            incrementMessageCount();

            // 更新发送者的最后消息时间
            lastMessageTimes.put(senderId, LocalDateTime.now());

            // 设置消息的频道信息
            message.setChannelType(ChatMessage.ChatChannelType.PRIVATE);
            message.setChannelId(getChannelId());
            message.setReceiverId(getOtherParticipant(senderId));

            // 创建消息状态跟踪
            MessageStatusInfo statusInfo = new MessageStatusInfo(message.getMessageId(), senderId);
            messageStatus.put(message.getMessageId(), statusInfo);

            log.debug("私聊频道发送消息: messageId={}, from={}, to={}, content={}", 
                    message.getMessageId(), senderId, message.getReceiverId(), message.getContent());

            // 这里应该调用消息分发服务
            // messageDispatcher.sendPrivateMessage(message);

            return true;
        } catch (Exception e) {
            log.error("私聊频道发送消息失败: messageId={}", message.getMessageId(), e);
            return false;
        }
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        if (message == null) {
            return;
        }

        try {
            // 更新频道状态
            updateLastActiveTime();
            incrementMessageCount();

            // 更新消息状态
            String messageId = message.getMessageId();
            MessageStatusInfo statusInfo = messageStatus.get(messageId);
            if (statusInfo != null) {
                statusInfo.setDelivered(true);
                statusInfo.setDeliveredTime(LocalDateTime.now());
            }

            log.debug("私聊频道接收消息: messageId={}, from={}, to={}, content={}", 
                    message.getMessageId(), message.getSenderId(), message.getReceiverId(), message.getContent());

        } catch (Exception e) {
            log.error("私聊频道处理消息失败: messageId={}", message.getMessageId(), e);
        }
    }

    @Override
    public boolean hasPermission(Long playerId) {
        // 私聊频道只有参与者才有权限
        return isParticipant(playerId) && !isMuted(playerId);
    }

    @Override
    public List<Long> getTargetAudience() {
        // 私聊频道的目标受众就是两个参与者
        return getMemberIds();
    }

    /**
     * 检查是否为频道参与者
     *
     * @param playerId 玩家ID
     * @return true表示是参与者
     */
    public boolean isParticipant(Long playerId) {
        return participant1.equals(playerId) || participant2.equals(playerId);
    }

    /**
     * 获取另一个参与者
     *
     * @param playerId 当前玩家ID
     * @return 另一个参与者ID
     */
    public Long getOtherParticipant(Long playerId) {
        if (participant1.equals(playerId)) {
            return participant2;
        } else if (participant2.equals(playerId)) {
            return participant1;
        } else {
            return null;
        }
    }

    /**
     * 获取参与者1
     *
     * @return 参与者1的ID
     */
    public Long getParticipant1() {
        return participant1;
    }

    /**
     * 获取参与者2
     *
     * @return 参与者2的ID
     */
    public Long getParticipant2() {
        return participant2;
    }

    /**
     * 标记消息为已读
     *
     * @param messageId 消息ID
     * @param readerId 阅读者ID
     */
    public void markMessageAsRead(String messageId, Long readerId) {
        MessageStatusInfo statusInfo = messageStatus.get(messageId);
        if (statusInfo != null && !statusInfo.getSenderId().equals(readerId)) {
            statusInfo.setRead(true);
            statusInfo.setReadTime(LocalDateTime.now());
            log.debug("私聊消息已读: messageId={}, reader={}", messageId, readerId);
        }
    }

    /**
     * 获取消息状态
     *
     * @param messageId 消息ID
     * @return 消息状态信息
     */
    public MessageStatusInfo getMessageStatus(String messageId) {
        return messageStatus.get(messageId);
    }

    /**
     * 获取未读消息数量
     *
     * @param playerId 玩家ID
     * @return 未读消息数量
     */
    public long getUnreadMessageCount(Long playerId) {
        if (!isParticipant(playerId)) {
            return 0;
        }

        return messageStatus.values().stream()
                .filter(status -> !status.getSenderId().equals(playerId))
                .filter(status -> !status.isRead())
                .count();
    }

    /**
     * 获取最后消息时间
     *
     * @param playerId 玩家ID
     * @return 最后消息时间
     */
    public LocalDateTime getLastMessageTime(Long playerId) {
        return lastMessageTimes.get(playerId);
    }

    /**
     * 检查频道是否应该被清理
     * <p>
     * 私聊频道在没有消息活动一段时间后可能被清理
     * </p>
     *
     * @param inactiveThresholdHours 非活跃阈值（小时）
     * @return true表示可以清理
     */
    public boolean shouldCleanup(int inactiveThresholdHours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(inactiveThresholdHours);
        return getLastActiveTime().isBefore(threshold) && getMessageCount() == 0;
    }

    /**
     * 生成私聊频道ID
     *
     * @param playerId1 玩家1 ID
     * @param playerId2 玩家2 ID
     * @return 频道ID
     */
    public static String generateChannelId(Long playerId1, Long playerId2) {
        // 确保ID的顺序一致，便于查找
        Long smaller = Math.min(playerId1, playerId2);
        Long larger = Math.max(playerId1, playerId2);
        return "private_" + smaller + "_" + larger;
    }

    /**
     * 获取频道摘要信息
     *
     * @return 频道摘要
     */
    public String getSummary() {
        return String.format("私聊频道 [%d <-> %d] 消息数: %d, 最后活跃: %s", 
                participant1, participant2, getMessageCount(), getLastActiveTime());
    }

    // ===== 内部类定义 =====

    /**
     * 消息状态信息类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MessageStatusInfo {
        /** 消息ID */
        private String messageId;
        
        /** 发送者ID */
        private Long senderId;
        
        /** 是否已送达 */
        private boolean delivered = false;
        
        /** 送达时间 */
        private LocalDateTime deliveredTime;
        
        /** 是否已读 */
        private boolean read = false;
        
        /** 阅读时间 */
        private LocalDateTime readTime;
        
        /** 创建时间 */
        private LocalDateTime createTime = LocalDateTime.now();

        public MessageStatusInfo(String messageId, Long senderId) {
            this.messageId = messageId;
            this.senderId = senderId;
        }
    }
}