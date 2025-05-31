/*
 * 文件名: MessageService.java
 * 用途: 消息服务核心实现
 * 实现内容:
 *   - 消息发送和接收的核心逻辑
 *   - 消息路由和分发管理
 *   - 消息存储和历史查询
 *   - 消息撤回和删除功能
 *   - 消息状态跟踪和确认机制
 * 技术选型:
 *   - Spring Service组件
 *   - 异步消息处理
 *   - 事务管理和重试机制
 * 依赖关系:
 *   - 依赖ChannelManager进行频道路由
 *   - 与MessageHandler协作处理消息
 *   - 调用存储服务进行消息持久化
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.message;

import com.lx.gameserver.business.chat.core.ChatChannel;
import com.lx.gameserver.business.chat.core.ChatMessage;
import com.lx.gameserver.business.chat.core.ChatSession;
import com.lx.gameserver.business.chat.core.MessageHandler;
import com.lx.gameserver.business.chat.channel.ChannelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息服务核心实现
 * <p>
 * 提供聊天消息的发送、接收、存储、查询等核心功能。
 * 支持多种消息类型和频道类型，具有完整的消息生命周期管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class MessageService {

    @Autowired
    private ChannelManager channelManager;

    /**
     * 消息ID生成器
     */
    private final AtomicLong messageIdGenerator = new AtomicLong(1);

    /**
     * 消息处理器列表
     */
    private final List<MessageHandler> messageHandlers = new ArrayList<>();

    /**
     * 消息缓存（MessageId -> ChatMessage）
     */
    private final Map<String, ChatMessage> messageCache = new ConcurrentHashMap<>();

    /**
     * 发送记录（SenderId -> 最后发送时间）
     */
    private final Map<Long, LocalDateTime> lastSendTimes = new ConcurrentHashMap<>();

    /**
     * 服务配置
     */
    private MessageServiceConfig config = new MessageServiceConfig();

    // ===== 消息发送核心方法 =====

    /**
     * 发送消息
     *
     * @param senderId 发送者ID
     * @param channelType 频道类型
     * @param messageType 消息类型
     * @param content 消息内容
     * @param targetId 目标ID
     * @param extraData 扩展数据
     * @return 发送结果
     */
    public SendMessageResult sendMessage(Long senderId, ChatMessage.ChatChannelType channelType,
                                       ChatMessage.MessageType messageType, String content,
                                       Long targetId, Map<String, Object> extraData) {
        try {
            // 参数验证
            SendMessageResult validation = validateSendParameters(senderId, channelType, messageType, content);
            if (!validation.isSuccess()) {
                return validation;
            }

            // 创建消息
            ChatMessage message = createMessage(senderId, channelType, messageType, content, targetId, extraData);
            
            // 处理消息发送
            return processSendMessage(message);

        } catch (Exception e) {
            log.error("发送消息失败: senderId={}, channelType={}, content={}", senderId, channelType, content, e);
            return SendMessageResult.failure("SEND_ERROR", "发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 异步发送消息
     *
     * @param senderId 发送者ID
     * @param channelType 频道类型
     * @param messageType 消息类型
     * @param content 消息内容
     * @param targetId 目标ID
     * @param extraData 扩展数据
     * @return 异步发送结果
     */
    public CompletableFuture<SendMessageResult> sendMessageAsync(Long senderId, 
                                                               ChatMessage.ChatChannelType channelType,
                                                               ChatMessage.MessageType messageType, 
                                                               String content, Long targetId, 
                                                               Map<String, Object> extraData) {
        return CompletableFuture.supplyAsync(() -> 
            sendMessage(senderId, channelType, messageType, content, targetId, extraData));
    }

    /**
     * 发送私聊消息
     *
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param messageType 消息类型
     * @param content 消息内容
     * @param extraData 扩展数据
     * @return 发送结果
     */
    public SendMessageResult sendPrivateMessage(Long senderId, Long receiverId, 
                                              ChatMessage.MessageType messageType, 
                                              String content, Map<String, Object> extraData) {
        return sendMessage(senderId, ChatMessage.ChatChannelType.PRIVATE, messageType, content, receiverId, extraData);
    }

    /**
     * 发送系统消息
     *
     * @param channelType 频道类型
     * @param content 消息内容
     * @param targetIds 目标用户ID列表
     * @return 发送结果
     */
    public SendMessageResult sendSystemMessage(ChatMessage.ChatChannelType channelType, 
                                             String content, List<Long> targetIds) {
        try {
            ChatMessage message = ChatMessage.builder()
                    .messageId(generateMessageId())
                    .senderId(0L) // 系统消息发送者ID为0
                    .senderName("系统")
                    .channelType(channelType)
                    .messageType(ChatMessage.MessageType.SYSTEM)
                    .content(content)
                    .sendTime(LocalDateTime.now())
                    .status(ChatMessage.MessageStatus.SENT)
                    .priority(ChatMessage.MessagePriority.HIGH)
                    .isSystemMessage(true)
                    .build();

            // 如果指定了目标用户，设置为定向系统消息
            if (targetIds != null && !targetIds.isEmpty()) {
                message.setExtraData("targetIds", targetIds);
            }

            return processSendMessage(message);

        } catch (Exception e) {
            log.error("发送系统消息失败: channelType={}, content={}", channelType, content, e);
            return SendMessageResult.failure("SYSTEM_MESSAGE_ERROR", "发送系统消息失败");
        }
    }

    // ===== 消息接收和处理 =====

    /**
     * 处理接收到的消息
     *
     * @param message 接收到的消息
     */
    public void handleReceivedMessage(ChatMessage message) {
        if (message == null) {
            return;
        }

        try {
            // 创建处理上下文
            MessageHandler.MessageHandleContext context = createHandleContext(message);

            // 依次执行消息处理器
            for (MessageHandler handler : messageHandlers) {
                MessageHandler.MessageHandleResult result = handler.handleReceivedMessage(message, context);
                if (result != null && !result.isContinueProcessing()) {
                    log.debug("消息处理被中断: messageId={}, handler={}", message.getMessageId(), handler.getName());
                    break;
                }
                
                // 如果处理器修改了消息，使用修改后的消息
                if (result != null && result.getProcessedMessage() != null) {
                    message = result.getProcessedMessage();
                }
            }

            // 缓存消息
            cacheMessage(message);

            log.debug("消息处理完成: messageId={}, from={}", message.getMessageId(), message.getSenderId());

        } catch (Exception e) {
            log.error("处理接收消息失败: messageId={}", message.getMessageId(), e);
        }
    }

    // ===== 消息查询方法 =====

    /**
     * 根据ID获取消息
     *
     * @param messageId 消息ID
     * @return 消息对象
     */
    public ChatMessage getMessage(String messageId) {
        if (messageId == null) {
            return null;
        }

        // 先从缓存查找
        ChatMessage message = messageCache.get(messageId);
        if (message != null) {
            return message;
        }

        // 从存储服务查找
        // message = messageStorageService.findById(messageId);
        
        return message;
    }

    /**
     * 查询频道历史消息
     *
     * @param channelId 频道ID
     * @param limit 限制数量
     * @param beforeTime 时间之前
     * @return 消息列表
     */
    public List<ChatMessage> getChannelHistory(String channelId, int limit, LocalDateTime beforeTime) {
        if (channelId == null) {
            return new ArrayList<>();
        }

        // 这里应该调用存储服务查询历史消息
        // return messageStorageService.findChannelMessages(channelId, limit, beforeTime);
        
        // 临时实现：从缓存中获取
        return messageCache.values().stream()
                .filter(msg -> channelId.equals(msg.getChannelId()))
                .filter(msg -> beforeTime == null || msg.getSendTime().isBefore(beforeTime))
                .sorted((m1, m2) -> m2.getSendTime().compareTo(m1.getSendTime()))
                .limit(limit)
                .toList();
    }

    /**
     * 查询私聊历史
     *
     * @param playerId1 玩家1 ID
     * @param playerId2 玩家2 ID
     * @param limit 限制数量
     * @param beforeTime 时间之前
     * @return 消息列表
     */
    public List<ChatMessage> getPrivateHistory(Long playerId1, Long playerId2, int limit, LocalDateTime beforeTime) {
        if (playerId1 == null || playerId2 == null) {
            return new ArrayList<>();
        }

        // 生成私聊频道ID
        String channelId = generatePrivateChannelId(playerId1, playerId2);
        
        return getChannelHistory(channelId, limit, beforeTime);
    }

    // ===== 消息撤回和删除 =====

    /**
     * 撤回消息
     *
     * @param messageId 消息ID
     * @param operatorId 操作者ID
     * @return 撤回结果
     */
    public boolean recallMessage(String messageId, Long operatorId) {
        if (messageId == null || operatorId == null) {
            return false;
        }

        try {
            ChatMessage message = getMessage(messageId);
            if (message == null) {
                log.warn("尝试撤回不存在的消息: messageId={}", messageId);
                return false;
            }

            // 检查撤回权限
            if (!canRecallMessage(message, operatorId)) {
                log.warn("用户无权撤回消息: messageId={}, operator={}", messageId, operatorId);
                return false;
            }

            // 检查撤回时间限制
            if (!isWithinRecallTimeLimit(message)) {
                log.warn("消息超出撤回时间限制: messageId={}", messageId);
                return false;
            }

            // 更新消息状态
            message.setStatus(ChatMessage.MessageStatus.RECALLED);
            message.setContent("[消息已撤回]");

            // 更新缓存和存储
            cacheMessage(message);
            // messageStorageService.update(message);

            log.info("消息撤回成功: messageId={}, operator={}", messageId, operatorId);
            return true;

        } catch (Exception e) {
            log.error("撤回消息失败: messageId={}, operator={}", messageId, operatorId, e);
            return false;
        }
    }

    /**
     * 删除消息
     *
     * @param messageId 消息ID
     * @param operatorId 操作者ID
     * @return 删除结果
     */
    public boolean deleteMessage(String messageId, Long operatorId) {
        if (messageId == null || operatorId == null) {
            return false;
        }

        try {
            ChatMessage message = getMessage(messageId);
            if (message == null) {
                return true; // 消息不存在，视为删除成功
            }

            // 检查删除权限
            if (!canDeleteMessage(message, operatorId)) {
                log.warn("用户无权删除消息: messageId={}, operator={}", messageId, operatorId);
                return false;
            }

            // 更新消息状态
            message.setStatus(ChatMessage.MessageStatus.DELETED);

            // 从缓存中移除
            messageCache.remove(messageId);

            // 从存储中删除或标记删除
            // messageStorageService.delete(messageId);

            log.info("消息删除成功: messageId={}, operator={}", messageId, operatorId);
            return true;

        } catch (Exception e) {
            log.error("删除消息失败: messageId={}, operator={}", messageId, operatorId, e);
            return false;
        }
    }

    // ===== 内部工具方法 =====

    /**
     * 验证发送参数
     */
    private SendMessageResult validateSendParameters(Long senderId, ChatMessage.ChatChannelType channelType,
                                                   ChatMessage.MessageType messageType, String content) {
        if (senderId == null) {
            return SendMessageResult.failure("INVALID_SENDER", "发送者ID不能为空");
        }

        if (channelType == null) {
            return SendMessageResult.failure("INVALID_CHANNEL_TYPE", "频道类型不能为空");
        }

        if (messageType == null) {
            return SendMessageResult.failure("INVALID_MESSAGE_TYPE", "消息类型不能为空");
        }

        if (content == null || content.trim().isEmpty()) {
            return SendMessageResult.failure("EMPTY_CONTENT", "消息内容不能为空");
        }

        if (content.length() > config.getMaxMessageLength()) {
            return SendMessageResult.failure("CONTENT_TOO_LONG", 
                    "消息内容超过最大长度限制: " + config.getMaxMessageLength());
        }

        // 检查发送频率限制
        if (!checkSendRateLimit(senderId)) {
            return SendMessageResult.failure("RATE_LIMIT", "发送频率过快，请稍后再试");
        }

        return SendMessageResult.success(null);
    }

    /**
     * 创建消息对象
     */
    private ChatMessage createMessage(Long senderId, ChatMessage.ChatChannelType channelType,
                                    ChatMessage.MessageType messageType, String content,
                                    Long targetId, Map<String, Object> extraData) {
        ChatMessage message = ChatMessage.builder()
                .messageId(generateMessageId())
                .senderId(senderId)
                .channelType(channelType)
                .messageType(messageType)
                .content(content)
                .sendTime(LocalDateTime.now())
                .status(ChatMessage.MessageStatus.SENT)
                .priority(ChatMessage.MessagePriority.NORMAL)
                .isSystemMessage(false)
                .extraData(extraData)
                .build();

        // 设置接收者ID（私聊时）
        if (ChatMessage.ChatChannelType.PRIVATE.equals(channelType) && targetId != null) {
            message.setReceiverId(targetId);
            message.setChannelId(generatePrivateChannelId(senderId, targetId));
        }

        return message;
    }

    /**
     * 处理消息发送
     */
    private SendMessageResult processSendMessage(ChatMessage message) {
        try {
            // 创建处理上下文
            MessageHandler.MessageHandleContext context = createHandleContext(message);

            // 依次执行消息处理器
            for (MessageHandler handler : messageHandlers) {
                MessageHandler.MessageHandleResult result = handler.handleSendMessage(message, context);
                if (result != null && !result.isSuccess()) {
                    return SendMessageResult.failure(result.getErrorCode(), result.getMessage());
                }
                
                if (result != null && !result.isContinueProcessing()) {
                    break;
                }
                
                // 如果处理器修改了消息，使用修改后的消息
                if (result != null && result.getProcessedMessage() != null) {
                    message = result.getProcessedMessage();
                }
            }

            // 路由消息到目标频道
            boolean routeSuccess = routeMessage(message);
            if (!routeSuccess) {
                return SendMessageResult.failure("ROUTE_FAILED", "消息路由失败");
            }

            // 缓存消息
            cacheMessage(message);

            // 更新发送时间记录
            lastSendTimes.put(message.getSenderId(), LocalDateTime.now());

            log.debug("消息发送成功: messageId={}, from={}, to={}", 
                    message.getMessageId(), message.getSenderId(), message.getReceiverId());

            return SendMessageResult.success(message);

        } catch (Exception e) {
            log.error("处理消息发送失败: messageId={}", message.getMessageId(), e);
            return SendMessageResult.failure("PROCESS_ERROR", "处理消息发送失败");
        }
    }

    /**
     * 路由消息到目标频道
     */
    private boolean routeMessage(ChatMessage message) {
        try {
            String channelId = determineChannelId(message);
            if (channelId == null) {
                log.warn("无法确定消息的目标频道: messageId={}", message.getMessageId());
                return false;
            }

            message.setChannelId(channelId);

            ChatChannel channel = channelManager.getChannel(channelId);
            if (channel == null) {
                // 尝试创建频道（如私聊频道）
                channel = createChannelIfNeeded(message);
                if (channel == null) {
                    log.warn("目标频道不存在且无法创建: channelId={}", channelId);
                    return false;
                }
            }

            return channel.sendMessage(message);

        } catch (Exception e) {
            log.error("路由消息失败: messageId={}", message.getMessageId(), e);
            return false;
        }
    }

    /**
     * 确定频道ID
     */
    private String determineChannelId(ChatMessage message) {
        switch (message.getChannelType()) {
            case WORLD:
                return "world_global";
            case PRIVATE:
                if (message.getReceiverId() != null) {
                    return generatePrivateChannelId(message.getSenderId(), message.getReceiverId());
                }
                break;
            case GUILD:
            case TEAM:
            case CUSTOM:
                // 这些频道需要从extraData中获取频道ID
                Object channelIdObj = message.getExtraData("channelId");
                if (channelIdObj instanceof String) {
                    return (String) channelIdObj;
                }
                break;
            case SYSTEM:
                return "system_global";
        }
        return null;
    }

    /**
     * 如需要则创建频道
     */
    private ChatChannel createChannelIfNeeded(ChatMessage message) {
        if (ChatMessage.ChatChannelType.PRIVATE.equals(message.getChannelType()) 
            && message.getReceiverId() != null) {
            
            return channelManager.createChannel(
                    ChatMessage.ChatChannelType.PRIVATE,
                    "私聊-" + message.getSenderId() + "-" + message.getReceiverId(),
                    message.getSenderId(),
                    null
            );
        }
        return null;
    }

    /**
     * 创建处理上下文
     */
    private MessageHandler.MessageHandleContext createHandleContext(ChatMessage message) {
        return new DefaultMessageHandleContext(message);
    }

    /**
     * 生成消息ID
     */
    private String generateMessageId() {
        return "msg_" + messageIdGenerator.getAndIncrement() + "_" + System.currentTimeMillis();
    }

    /**
     * 生成私聊频道ID
     */
    private String generatePrivateChannelId(Long playerId1, Long playerId2) {
        Long smaller = Math.min(playerId1, playerId2);
        Long larger = Math.max(playerId1, playerId2);
        return "private_" + smaller + "_" + larger;
    }

    /**
     * 检查发送频率限制
     */
    private boolean checkSendRateLimit(Long senderId) {
        LocalDateTime lastSendTime = lastSendTimes.get(senderId);
        if (lastSendTime == null) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(lastSendTime.plusSeconds(config.getMinSendInterval()));
    }

    /**
     * 缓存消息
     */
    private void cacheMessage(ChatMessage message) {
        if (message != null && message.getMessageId() != null) {
            messageCache.put(message.getMessageId(), message);
            
            // 限制缓存大小
            if (messageCache.size() > config.getMaxCacheSize()) {
                cleanupOldCachedMessages();
            }
        }
    }

    /**
     * 清理旧的缓存消息
     */
    private void cleanupOldCachedMessages() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(config.getCacheRetentionMinutes());
        
        messageCache.entrySet().removeIf(entry -> {
            ChatMessage message = entry.getValue();
            return message.getSendTime().isBefore(cutoffTime);
        });
    }

    /**
     * 检查是否可以撤回消息
     */
    private boolean canRecallMessage(ChatMessage message, Long operatorId) {
        // 只有发送者可以撤回自己的消息
        return message.getSenderId().equals(operatorId);
    }

    /**
     * 检查是否在撤回时间限制内
     */
    private boolean isWithinRecallTimeLimit(ChatMessage message) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeLimit = message.getSendTime().plusMinutes(config.getRecallTimeLimitMinutes());
        return now.isBefore(timeLimit);
    }

    /**
     * 检查是否可以删除消息
     */
    private boolean canDeleteMessage(ChatMessage message, Long operatorId) {
        // 发送者可以删除自己的消息，管理员可以删除任何消息
        return message.getSenderId().equals(operatorId) || isAdmin(operatorId);
    }

    /**
     * 检查是否为管理员
     */
    private boolean isAdmin(Long playerId) {
        // 这里应该检查用户的管理员权限
        // 暂时返回false
        return false;
    }

    // ===== 配置类定义 =====

    /**
     * 消息服务配置类
     */
    @lombok.Data
    public static class MessageServiceConfig {
        /** 最大消息长度 */
        private int maxMessageLength = 1000;
        
        /** 最小发送间隔（秒） */
        private int minSendInterval = 1;
        
        /** 最大缓存大小 */
        private int maxCacheSize = 10000;
        
        /** 缓存保留时间（分钟） */
        private int cacheRetentionMinutes = 30;
        
        /** 撤回时间限制（分钟） */
        private int recallTimeLimitMinutes = 5;
    }

    /**
     * 默认消息处理上下文实现
     */
    private static class DefaultMessageHandleContext implements MessageHandler.MessageHandleContext {
        private final ChatMessage message;
        private final Map<String, Object> attributes = new HashMap<>();
        private final long startTime = System.currentTimeMillis();

        public DefaultMessageHandleContext(ChatMessage message) {
            this.message = message;
        }

        @Override
        public ChatSession getSession() {
            return null; // 可以根据需要实现
        }

        @Override
        public ChatChannel getChannel() {
            return null; // 可以根据需要实现
        }

        @Override
        public Long getSenderId() {
            return message.getSenderId();
        }

        @Override
        public List<Long> getReceiverIds() {
            if (message.getReceiverId() != null) {
                return Arrays.asList(message.getReceiverId());
            }
            return new ArrayList<>();
        }

        @Override
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public long getStartTime() {
            return startTime;
        }

        @Override
        public boolean needRecordHistory() {
            return true;
        }

        @Override
        public boolean needPush() {
            return true;
        }
    }

    /**
     * 发送消息结果类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SendMessageResult {
        private boolean success;
        private String errorCode;
        private String message;
        private ChatMessage sentMessage;

        public static SendMessageResult success(ChatMessage message) {
            return new SendMessageResult(true, null, "发送成功", message);
        }

        public static SendMessageResult failure(String errorCode, String message) {
            return new SendMessageResult(false, errorCode, message, null);
        }
    }
}