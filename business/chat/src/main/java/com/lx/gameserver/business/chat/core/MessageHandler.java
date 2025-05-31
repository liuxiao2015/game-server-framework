/*
 * 文件名: MessageHandler.java
 * 用途: 消息处理器接口定义
 * 实现内容:
 *   - 定义消息处理的标准接口
 *   - 支持消息接收、发送、过滤和转发处理
 *   - 提供扩展处理器的统一规范
 *   - 支持异步消息处理和回调机制
 * 技术选型:
 *   - 策略模式和责任链模式
 *   - 支持CompletableFuture异步处理
 *   - 提供处理器优先级和过滤条件
 * 依赖关系:
 *   - 被MessageService和各种具体处理器实现
 *   - 与ChatChannel和ChatSession配合工作
 *   - 为扩展功能提供插件化支持
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.core;

import java.util.concurrent.CompletableFuture;

/**
 * 消息处理器接口
 * <p>
 * 定义了消息处理的标准规范，包含消息接收、发送、过滤、转发等
 * 核心处理逻辑。支持异步处理和扩展处理器，为聊天系统提供
 * 灵活的消息处理机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface MessageHandler {

    /**
     * 处理接收到的消息
     * <p>
     * 当系统接收到消息时调用此方法进行处理，包括消息验证、
     * 过滤、转换等预处理操作。
     * </p>
     *
     * @param message 接收到的消息
     * @param context 处理上下文
     * @return 处理结果
     */
    MessageHandleResult handleReceivedMessage(ChatMessage message, MessageHandleContext context);

    /**
     * 处理发送消息
     * <p>
     * 当系统需要发送消息时调用此方法，包括消息封装、
     * 路由选择、发送策略等处理。
     * </p>
     *
     * @param message 要发送的消息
     * @param context 处理上下文
     * @return 处理结果
     */
    MessageHandleResult handleSendMessage(ChatMessage message, MessageHandleContext context);

    /**
     * 异步处理接收到的消息
     *
     * @param message 接收到的消息
     * @param context 处理上下文
     * @return 异步处理结果
     */
    default CompletableFuture<MessageHandleResult> handleReceivedMessageAsync(ChatMessage message, MessageHandleContext context) {
        return CompletableFuture.supplyAsync(() -> handleReceivedMessage(message, context));
    }

    /**
     * 异步处理发送消息
     *
     * @param message 要发送的消息
     * @param context 处理上下文
     * @return 异步处理结果
     */
    default CompletableFuture<MessageHandleResult> handleSendMessageAsync(ChatMessage message, MessageHandleContext context) {
        return CompletableFuture.supplyAsync(() -> handleSendMessage(message, context));
    }

    /**
     * 消息过滤处理
     * <p>
     * 对消息进行过滤检查，包括敏感词过滤、内容审核、
     * 权限检查等。
     * </p>
     *
     * @param message 要过滤的消息
     * @param context 处理上下文
     * @return 过滤结果
     */
    default FilterResult filterMessage(ChatMessage message, MessageHandleContext context) {
        return FilterResult.PASS;
    }

    /**
     * 消息转发处理
     * <p>
     * 处理消息的转发逻辑，包括转发目标选择、转发策略、
     * 转发权限检查等。
     * </p>
     *
     * @param message 要转发的消息
     * @param targets 转发目标
     * @param context 处理上下文
     * @return 转发结果
     */
    default ForwardResult forwardMessage(ChatMessage message, java.util.List<Long> targets, MessageHandleContext context) {
        return ForwardResult.builder()
                .success(false)
                .message("转发功能未实现")
                .build();
    }

    /**
     * 检查是否支持处理指定类型的消息
     *
     * @param messageType 消息类型
     * @return true表示支持
     */
    default boolean supports(ChatMessage.MessageType messageType) {
        return true;
    }

    /**
     * 获取处理器优先级
     * <p>
     * 数值越小优先级越高，默认为100
     * </p>
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 消息处理上下文
     */
    interface MessageHandleContext {
        /**
         * 获取会话信息
         *
         * @return 聊天会话
         */
        ChatSession getSession();

        /**
         * 获取频道信息
         *
         * @return 聊天频道
         */
        ChatChannel getChannel();

        /**
         * 获取发送者信息
         *
         * @return 发送者ID
         */
        Long getSenderId();

        /**
         * 获取接收者信息
         *
         * @return 接收者ID列表
         */
        java.util.List<Long> getReceiverIds();

        /**
         * 获取上下文属性
         *
         * @param key 属性键
         * @return 属性值
         */
        Object getAttribute(String key);

        /**
         * 设置上下文属性
         *
         * @param key 属性键
         * @param value 属性值
         */
        void setAttribute(String key, Object value);

        /**
         * 获取所有属性
         *
         * @return 属性映射
         */
        java.util.Map<String, Object> getAttributes();

        /**
         * 获取处理开始时间
         *
         * @return 开始时间（毫秒）
         */
        long getStartTime();

        /**
         * 检查是否需要记录历史
         *
         * @return true表示需要记录
         */
        boolean needRecordHistory();

        /**
         * 检查是否需要推送
         *
         * @return true表示需要推送
         */
        boolean needPush();
    }

    /**
     * 消息处理结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class MessageHandleResult {
        /** 处理是否成功 */
        private boolean success;

        /** 处理后的消息 */
        private ChatMessage processedMessage;

        /** 结果消息 */
        private String message;

        /** 错误代码 */
        private String errorCode;

        /** 是否继续处理 */
        private boolean continueProcessing;

        /** 扩展结果数据 */
        private java.util.Map<String, Object> resultData;

        /**
         * 创建成功结果
         *
         * @param processedMessage 处理后的消息
         * @return 成功结果
         */
        public static MessageHandleResult success(ChatMessage processedMessage) {
            return MessageHandleResult.builder()
                    .success(true)
                    .processedMessage(processedMessage)
                    .continueProcessing(true)
                    .build();
        }

        /**
         * 创建失败结果
         *
         * @param errorCode 错误代码
         * @param message 错误消息
         * @return 失败结果
         */
        public static MessageHandleResult failure(String errorCode, String message) {
            return MessageHandleResult.builder()
                    .success(false)
                    .errorCode(errorCode)
                    .message(message)
                    .continueProcessing(false)
                    .build();
        }
    }

    /**
     * 过滤结果枚举
     */
    enum FilterResult {
        /** 通过 */
        PASS("pass", "通过"),
        /** 拒绝 */
        REJECT("reject", "拒绝"),
        /** 替换 */
        REPLACE("replace", "替换"),
        /** 警告 */
        WARNING("warning", "警告");

        private final String code;
        private final String description;

        FilterResult(String code, String description) {
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
     * 转发结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class ForwardResult {
        /** 转发是否成功 */
        private boolean success;

        /** 成功转发的目标数量 */
        private int successCount;

        /** 失败转发的目标数量 */
        private int failureCount;

        /** 结果消息 */
        private String message;

        /** 失败的目标列表 */
        private java.util.List<Long> failedTargets;

        /** 扩展结果数据 */
        private java.util.Map<String, Object> resultData;
    }
}