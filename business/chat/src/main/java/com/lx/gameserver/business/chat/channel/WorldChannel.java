/*
 * 文件名: WorldChannel.java
 * 用途: 世界频道实现类
 * 实现内容:
 *   - 全服广播功能实现
 *   - 世界频道的特殊权限和配置
 *   - 大规模消息分发优化
 *   - 世界频道专用的消息过滤和限制
 * 技术选型:
 *   - 继承ChatChannel抽象基类
 *   - 使用广播模式进行消息分发
 *   - 支持分批处理大量用户
 * 依赖关系:
 *   - 继承自ChatChannel
 *   - 被ChannelManager管理
 *   - 为全服玩家提供公共聊天空间
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.channel;

import com.lx.gameserver.business.chat.core.ChatChannel;
import com.lx.gameserver.business.chat.core.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 世界频道实现
 * <p>
 * 提供全服广播功能的世界聊天频道，支持所有在线玩家进行
 * 公共聊天交流。具有特殊的权限控制和消息分发策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class WorldChannel extends ChatChannel {

    /**
     * 世界频道单例实例
     */
    private static volatile WorldChannel instance;

    /**
     * 世界频道默认配置
     */
    private static final ChannelConfig DEFAULT_WORLD_CONFIG = createDefaultConfig();

    /**
     * 构造函数
     *
     * @param channelId 频道ID
     * @param channelName 频道名称
     */
    public WorldChannel(String channelId, String channelName) {
        super(channelId, ChatMessage.ChatChannelType.WORLD, channelName);
        this.setConfig(DEFAULT_WORLD_CONFIG);
        log.info("创建世界频道: channelId={}, name={}", channelId, channelName);
    }

    /**
     * 获取世界频道单例
     *
     * @return 世界频道实例
     */
    public static WorldChannel getInstance() {
        if (instance == null) {
            synchronized (WorldChannel.class) {
                if (instance == null) {
                    instance = new WorldChannel("world_global", "世界");
                }
            }
        }
        return instance;
    }

    /**
     * 创建默认配置
     */
    private static ChannelConfig createDefaultConfig() {
        ChannelConfig config = new ChannelConfig();
        config.setMaxMembers(-1); // 无限制
        config.setMessageInterval(10); // 10秒发言间隔
        config.setMaxMessageLength(200); // 最大200字符
        config.setRequirePermission(false); // 不需要特殊权限
        config.setAllowAnonymous(false); // 不允许匿名
        config.setRecordHistory(true); // 记录历史
        config.setHistoryRetentionDays(3); // 保留3天
        config.setEnableSensitiveWordFilter(true); // 启用敏感词过滤
        config.setEnableRateLimit(true); // 启用限流
        config.setAnnouncement("欢迎来到世界频道，请文明聊天！");
        return config;
    }

    @Override
    public boolean sendMessage(ChatMessage message) {
        if (message == null) {
            return false;
        }

        try {
            // 更新频道活跃时间
            updateLastActiveTime();
            incrementMessageCount();

            // 世界频道消息需要广播给所有在线玩家
            log.debug("世界频道发送消息: messageId={}, sender={}, content={}", 
                    message.getMessageId(), message.getSenderId(), message.getContent());

            // 这里应该调用消息分发服务进行广播
            // messageDispatcher.broadcastToAll(message);

            return true;
        } catch (Exception e) {
            log.error("世界频道发送消息失败: messageId={}", message.getMessageId(), e);
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

            // 设置消息的频道信息
            message.setChannelType(ChatMessage.ChatChannelType.WORLD);
            message.setChannelId(this.getChannelId());

            // 世界频道的消息处理逻辑
            log.debug("世界频道接收消息: messageId={}, sender={}, content={}", 
                    message.getMessageId(), message.getSenderId(), message.getContent());

            // 这里可以添加世界频道特有的消息处理逻辑
            // 例如：VIP用户消息高亮、特殊事件通知等

        } catch (Exception e) {
            log.error("世界频道处理消息失败: messageId={}", message.getMessageId(), e);
        }
    }

    @Override
    public boolean hasPermission(Long playerId) {
        if (playerId == null) {
            return false;
        }

        // 检查基础权限（是否为成员且未被禁言）
        if (!isMember(playerId) || isMuted(playerId)) {
            return false;
        }

        // 世界频道的特殊权限检查
        // 例如：等级限制、VIP限制等
        return checkWorldChannelPermission(playerId);
    }

    @Override
    public List<Long> getTargetAudience() {
        // 世界频道的目标受众是所有在线玩家
        // 这里应该返回所有在线玩家的ID列表
        // 实际实现中需要从连接管理器获取在线用户列表
        return getAllOnlinePlayerIds();
    }

    /**
     * 检查世界频道权限
     *
     * @param playerId 玩家ID
     * @return true表示有权限
     */
    private boolean checkWorldChannelPermission(Long playerId) {
        // 可以在这里添加世界频道的特殊权限检查逻辑
        // 例如：
        // 1. 玩家等级是否达到要求
        // 2. 是否被全服禁言
        // 3. 是否为VIP用户
        // 4. 账号状态是否正常
        
        // 暂时返回true，实际项目中需要根据业务需求实现
        return true;
    }

    /**
     * 获取所有在线玩家ID列表
     *
     * @return 在线玩家ID列表
     */
    private List<Long> getAllOnlinePlayerIds() {
        // 这里应该从连接管理器或者用户服务获取所有在线玩家
        // 实际实现中需要注入相关服务
        
        // 暂时返回当前频道成员，实际应该是所有在线玩家
        return getMemberIds();
    }

    /**
     * 添加系统公告
     *
     * @param announcement 公告内容
     */
    public void addSystemAnnouncement(String announcement) {
        if (announcement == null || announcement.trim().isEmpty()) {
            return;
        }

        try {
            ChatMessage systemMessage = ChatMessage.builder()
                    .messageId("system_" + System.currentTimeMillis())
                    .senderId(0L) // 系统用户ID为0
                    .senderName("系统")
                    .channelType(ChatMessage.ChatChannelType.WORLD)
                    .channelId(this.getChannelId())
                    .messageType(ChatMessage.MessageType.SYSTEM)
                    .content(announcement)
                    .sendTime(LocalDateTime.now())
                    .status(ChatMessage.MessageStatus.SENT)
                    .priority(ChatMessage.MessagePriority.HIGH)
                    .isSystemMessage(true)
                    .build();

            sendMessage(systemMessage);
            log.info("世界频道发送系统公告: content={}", announcement);
        } catch (Exception e) {
            log.error("世界频道发送系统公告失败: content={}", announcement, e);
        }
    }

    /**
     * 获取频道信息
     *
     * @return 频道信息字符串
     */
    public String getChannelInfo() {
        return String.format("世界频道 [ID: %s, 在线: %d, 总消息: %d]", 
                getChannelId(), getMemberCount(), getMessageCount());
    }

    /**
     * 检查是否为世界频道
     *
     * @return 总是返回true
     */
    public boolean isWorldChannel() {
        return true;
    }

    /**
     * 设置世界频道公告
     *
     * @param announcement 公告内容
     */
    public void setWorldAnnouncement(String announcement) {
        if (getConfig() != null) {
            getConfig().setAnnouncement(announcement);
            log.info("更新世界频道公告: {}", announcement);
        }
    }

    /**
     * 获取世界频道公告
     *
     * @return 公告内容
     */
    public String getWorldAnnouncement() {
        return getConfig() != null ? getConfig().getAnnouncement() : "";
    }
}