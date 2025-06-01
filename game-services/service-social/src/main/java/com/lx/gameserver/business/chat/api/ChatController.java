/*
 * 文件名: ChatController.java
 * 用途: 聊天REST API控制器
 * 实现内容:
 *   - 提供聊天相关的HTTP接口
 *   - 消息发送和历史查询API
 *   - 频道管理和会话操作API
 *   - 用户状态查询和设置API
 *   - 统一的错误处理和响应格式
 * 技术选型:
 *   - Spring MVC RestController
 *   - 统一响应格式封装
 *   - 参数验证和异常处理
 * 依赖关系:
 *   - 调用MessageService进行消息处理
 *   - 调用ChannelManager进行频道管理
 *   - 为前端和移动端提供HTTP接口
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.api;

import com.lx.gameserver.business.chat.channel.ChannelManager;
import com.lx.gameserver.business.chat.core.ChatMessage;
import com.lx.gameserver.business.chat.message.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天REST API控制器
 * <p>
 * 提供聊天系统的HTTP接口，包括消息发送、历史查询、
 * 频道管理等功能。支持标准的REST风格API。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ChannelManager channelManager;

    // ===== 消息相关接口 =====

    /**
     * 发送消息
     *
     * @param request 发送消息请求
     * @return 发送结果
     */
    @PostMapping("/message/send")
    public ApiResponse<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        try {
            // 参数验证
            if (request.getSenderId() == null) {
                return ApiResponse.error("INVALID_SENDER", "发送者ID不能为空");
            }
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                return ApiResponse.error("EMPTY_CONTENT", "消息内容不能为空");
            }

            // 解析频道类型和消息类型
            ChatMessage.ChatChannelType channelType = parseChannelType(request.getChannelType());
            ChatMessage.MessageType messageType = parseMessageType(request.getMessageType());

            // 发送消息
            MessageService.SendMessageResult result = messageService.sendMessage(
                    request.getSenderId(),
                    channelType,
                    messageType,
                    request.getContent(),
                    request.getTargetId(),
                    request.getExtraData()
            );

            if (result.isSuccess()) {
                SendMessageResponse response = new SendMessageResponse();
                response.setMessageId(result.getSentMessage().getMessageId());
                response.setSendTime(result.getSentMessage().getSendTime());
                response.setStatus("sent");
                return ApiResponse.success(response);
            } else {
                return ApiResponse.error(result.getErrorCode(), result.getMessage());
            }

        } catch (Exception e) {
            log.error("发送消息API异常: request={}", request, e);
            return ApiResponse.error("SEND_ERROR", "发送消息失败");
        }
    }

    /**
     * 获取聊天历史
     *
     * @param channelId 频道ID
     * @param limit 限制数量
     * @param beforeTime 时间之前（可选）
     * @return 历史消息列表
     */
    @GetMapping("/message/history")
    public ApiResponse<List<ChatMessage>> getChatHistory(
            @RequestParam String channelId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String beforeTime) {
        
        try {
            LocalDateTime beforeDateTime = null;
            if (beforeTime != null && !beforeTime.isEmpty()) {
                beforeDateTime = LocalDateTime.parse(beforeTime);
            }

            List<ChatMessage> messages = messageService.getChannelHistory(channelId, limit, beforeDateTime);
            return ApiResponse.success(messages);

        } catch (Exception e) {
            log.error("获取聊天历史API异常: channelId={}, limit={}", channelId, limit, e);
            return ApiResponse.error("HISTORY_ERROR", "获取聊天历史失败");
        }
    }

    /**
     * 获取私聊历史
     *
     * @param playerId1 玩家1 ID
     * @param playerId2 玩家2 ID
     * @param limit 限制数量
     * @param beforeTime 时间之前（可选）
     * @return 私聊历史列表
     */
    @GetMapping("/message/private-history")
    public ApiResponse<List<ChatMessage>> getPrivateHistory(
            @RequestParam Long playerId1,
            @RequestParam Long playerId2,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String beforeTime) {
        
        try {
            LocalDateTime beforeDateTime = null;
            if (beforeTime != null && !beforeTime.isEmpty()) {
                beforeDateTime = LocalDateTime.parse(beforeTime);
            }

            List<ChatMessage> messages = messageService.getPrivateHistory(playerId1, playerId2, limit, beforeDateTime);
            return ApiResponse.success(messages);

        } catch (Exception e) {
            log.error("获取私聊历史API异常: playerId1={}, playerId2={}, limit={}", playerId1, playerId2, limit, e);
            return ApiResponse.error("PRIVATE_HISTORY_ERROR", "获取私聊历史失败");
        }
    }

    /**
     * 撤回消息
     *
     * @param messageId 消息ID
     * @param operatorId 操作者ID
     * @return 撤回结果
     */
    @PostMapping("/message/{messageId}/recall")
    public ApiResponse<Void> recallMessage(
            @PathVariable String messageId,
            @RequestParam Long operatorId) {
        
        try {
            boolean success = messageService.recallMessage(messageId, operatorId);
            if (success) {
                return ApiResponse.success(null);
            } else {
                return ApiResponse.error("RECALL_FAILED", "撤回消息失败");
            }

        } catch (Exception e) {
            log.error("撤回消息API异常: messageId={}, operatorId={}", messageId, operatorId, e);
            return ApiResponse.error("RECALL_ERROR", "撤回消息失败");
        }
    }

    // ===== 频道相关接口 =====

    /**
     * 加入频道
     *
     * @param channelId 频道ID
     * @param playerId 玩家ID
     * @return 加入结果
     */
    @PostMapping("/channel/{channelId}/join")
    public ApiResponse<Void> joinChannel(
            @PathVariable String channelId,
            @RequestParam Long playerId) {
        
        try {
            boolean success = channelManager.joinChannel(playerId, channelId);
            if (success) {
                return ApiResponse.success(null);
            } else {
                return ApiResponse.error("JOIN_FAILED", "加入频道失败");
            }

        } catch (Exception e) {
            log.error("加入频道API异常: channelId={}, playerId={}", channelId, playerId, e);
            return ApiResponse.error("JOIN_ERROR", "加入频道失败");
        }
    }

    /**
     * 离开频道
     *
     * @param channelId 频道ID
     * @param playerId 玩家ID
     * @return 离开结果
     */
    @PostMapping("/channel/{channelId}/leave")
    public ApiResponse<Void> leaveChannel(
            @PathVariable String channelId,
            @RequestParam Long playerId) {
        
        try {
            boolean success = channelManager.leaveChannel(playerId, channelId);
            if (success) {
                return ApiResponse.success(null);
            } else {
                return ApiResponse.error("LEAVE_FAILED", "离开频道失败");
            }

        } catch (Exception e) {
            log.error("离开频道API异常: channelId={}, playerId={}", channelId, playerId, e);
            return ApiResponse.error("LEAVE_ERROR", "离开频道失败");
        }
    }

    /**
     * 获取用户频道列表
     *
     * @param playerId 玩家ID
     * @return 频道列表
     */
    @GetMapping("/channel/user/{playerId}")
    public ApiResponse<List<String>> getUserChannels(@PathVariable Long playerId) {
        try {
            List<String> channels = channelManager.getUserChannels(playerId);
            return ApiResponse.success(channels);

        } catch (Exception e) {
            log.error("获取用户频道列表API异常: playerId={}", playerId, e);
            return ApiResponse.error("GET_CHANNELS_ERROR", "获取频道列表失败");
        }
    }

    // ===== 状态查询接口 =====

    /**
     * 获取系统状态
     *
     * @return 系统状态信息
     */
    @GetMapping("/status")
    public ApiResponse<SystemStatus> getSystemStatus() {
        try {
            SystemStatus status = new SystemStatus();
            status.setOnline(true);
            status.setTimestamp(LocalDateTime.now());
            status.setVersion("1.0.0");
            
            // 可以添加更多状态信息
            // status.setActiveChannels(channelManager.getActiveChannelCount());
            // status.setOnlineUsers(connectionManager.getOnlineUserCount());
            
            return ApiResponse.success(status);

        } catch (Exception e) {
            log.error("获取系统状态API异常", e);
            return ApiResponse.error("STATUS_ERROR", "获取系统状态失败");
        }
    }

    // ===== 工具方法 =====

    /**
     * 解析频道类型
     */
    private ChatMessage.ChatChannelType parseChannelType(String channelType) {
        if (channelType == null) {
            return ChatMessage.ChatChannelType.WORLD;
        }
        
        try {
            return ChatMessage.ChatChannelType.valueOf(channelType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatMessage.ChatChannelType.WORLD;
        }
    }

    /**
     * 解析消息类型
     */
    private ChatMessage.MessageType parseMessageType(String messageType) {
        if (messageType == null) {
            return ChatMessage.MessageType.TEXT;
        }
        
        try {
            return ChatMessage.MessageType.valueOf(messageType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatMessage.MessageType.TEXT;
        }
    }

    // ===== 请求和响应类定义 =====

    /**
     * 发送消息请求
     */
    @lombok.Data
    public static class SendMessageRequest {
        private Long senderId;
        private String channelType;
        private String messageType;
        private String content;
        private Long targetId;
        private Map<String, Object> extraData;
    }

    /**
     * 发送消息响应
     */
    @lombok.Data
    public static class SendMessageResponse {
        private String messageId;
        private LocalDateTime sendTime;
        private String status;
    }

    /**
     * 系统状态
     */
    @lombok.Data
    public static class SystemStatus {
        private boolean online;
        private LocalDateTime timestamp;
        private String version;
        private Integer activeChannels;
        private Integer onlineUsers;
    }

    /**
     * 统一API响应格式
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String errorCode;
        private String message;
        private T data;
        private LocalDateTime timestamp;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = true;
            response.data = data;
            response.timestamp = LocalDateTime.now();
            return response;
        }

        public static <T> ApiResponse<T> error(String errorCode, String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = false;
            response.errorCode = errorCode;
            response.message = message;
            response.timestamp = LocalDateTime.now();
            return response;
        }
    }
}