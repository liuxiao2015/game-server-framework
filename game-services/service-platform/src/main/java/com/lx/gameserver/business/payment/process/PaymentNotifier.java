/*
 * 文件名: PaymentNotifier.java
 * 用途: 支付通知服务
 * 实现内容:
 *   - 游戏服务通知和业务系统集成
 *   - 通知队列管理和消息可靠性
 *   - 通知重试机制和失败处理
 *   - 通知状态追踪和监控
 *   - 通知超时处理和异常恢复
 * 技术选型:
 *   - 异步消息队列
 *   - 重试机制
 *   - 状态追踪
 *   - 监控告警
 * 依赖关系:
 *   - 被回调处理器调用
 *   - 依赖消息队列
 *   - 集成业务系统
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.process;

import com.lx.gameserver.business.payment.core.PaymentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支付通知服务
 * <p>
 * 负责向游戏业务系统发送支付结果通知，包括队列管理、
 * 重试机制、状态追踪等功能，确保通知的可靠性和及时性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class PaymentNotifier {

    private static final Logger logger = LoggerFactory.getLogger(PaymentNotifier.class);

    /**
     * 通知类型枚举
     */
    public enum NotificationType {
        /** 支付成功 */
        PAYMENT_SUCCESS("payment_success", "支付成功"),
        /** 支付失败 */
        PAYMENT_FAILED("payment_failed", "支付失败"),
        /** 支付超时 */
        PAYMENT_TIMEOUT("payment_timeout", "支付超时"),
        /** 订单关闭 */
        ORDER_CLOSED("order_closed", "订单关闭"),
        /** 退款成功 */
        REFUND_SUCCESS("refund_success", "退款成功"),
        /** 退款失败 */
        REFUND_FAILED("refund_failed", "退款失败");

        private final String code;
        private final String name;

        NotificationType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 通知状态枚举
     */
    public enum NotificationStatus {
        /** 待发送 */
        PENDING("pending", "待发送"),
        /** 发送中 */
        SENDING("sending", "发送中"),
        /** 发送成功 */
        SUCCESS("success", "发送成功"),
        /** 发送失败 */
        FAILED("failed", "发送失败"),
        /** 已超时 */
        TIMEOUT("timeout", "已超时");

        private final String code;
        private final String name;

        NotificationStatus(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 通知消息
     */
    public static class NotificationMessage {
        private final String id;
        private final String orderId;
        private final NotificationType type;
        private final Map<String, Object> data;
        private final String notifyUrl;
        private final LocalDateTime createTime;
        private volatile NotificationStatus status;
        private volatile int retryCount;
        private volatile LocalDateTime lastRetryTime;
        private volatile String errorMessage;

        public NotificationMessage(String id, String orderId, NotificationType type, 
                                 Map<String, Object> data, String notifyUrl) {
            this.id = id;
            this.orderId = orderId;
            this.type = type;
            this.data = data;
            this.notifyUrl = notifyUrl;
            this.createTime = LocalDateTime.now();
            this.status = NotificationStatus.PENDING;
            this.retryCount = 0;
        }

        // Getters and setters
        public String getId() { return id; }
        public String getOrderId() { return orderId; }
        public NotificationType getType() { return type; }
        public Map<String, Object> getData() { return data; }
        public String getNotifyUrl() { return notifyUrl; }
        public LocalDateTime getCreateTime() { return createTime; }
        public NotificationStatus getStatus() { return status; }
        public void setStatus(NotificationStatus status) { this.status = status; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public LocalDateTime getLastRetryTime() { return lastRetryTime; }
        public void setLastRetryTime(LocalDateTime lastRetryTime) { this.lastRetryTime = lastRetryTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    // ========== 实例变量 ==========

    /**
     * 通知队列
     */
    private final Queue<NotificationMessage> notificationQueue = new ConcurrentLinkedQueue<>();

    /**
     * 正在处理的通知
     */
    private final Map<String, NotificationMessage> processingNotifications = new ConcurrentHashMap<>();

    /**
     * 失败的通知（等待重试）
     */
    private final Queue<NotificationMessage> retryQueue = new ConcurrentLinkedQueue<>();

    /**
     * 线程池
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 调度器（用于重试和清理）
     */
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);

    /**
     * 统计计数器
     */
    private final AtomicLong totalNotifications = new AtomicLong(0);
    private final AtomicLong successNotifications = new AtomicLong(0);
    private final AtomicLong failedNotifications = new AtomicLong(0);

    /**
     * 配置参数
     */
    private final int maxRetryCount = 5;
    private final long retryIntervalMillis = 30000; // 30秒
    private final long notificationTimeoutMillis = 60000; // 60秒

    /**
     * 构造函数
     */
    public PaymentNotifier() {
        logger.info("支付通知服务初始化完成");
        
        // 启动通知处理线程
        startNotificationProcessor();
        
        // 启动重试处理
        startRetryProcessor();
        
        // 启动清理任务
        startCleanupTask();
    }

    /**
     * 发送支付成功通知
     *
     * @param order 支付订单
     * @param notifyUrl 通知URL
     * @return 通知ID
     */
    public CompletableFuture<String> notifyPaymentSuccess(PaymentOrder order, String notifyUrl) {
        Map<String, Object> data = buildPaymentSuccessData(order);
        return sendNotification(order.getOrderId(), NotificationType.PAYMENT_SUCCESS, data, notifyUrl);
    }

    /**
     * 发送支付失败通知
     *
     * @param order 支付订单
     * @param notifyUrl 通知URL
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return 通知ID
     */
    public CompletableFuture<String> notifyPaymentFailed(PaymentOrder order, String notifyUrl, 
                                                       String errorCode, String errorMessage) {
        Map<String, Object> data = buildPaymentFailedData(order, errorCode, errorMessage);
        return sendNotification(order.getOrderId(), NotificationType.PAYMENT_FAILED, data, notifyUrl);
    }

    /**
     * 发送退款通知
     *
     * @param orderId 订单号
     * @param refundId 退款单号
     * @param refundAmount 退款金额
     * @param notifyUrl 通知URL
     * @param success 是否成功
     * @return 通知ID
     */
    public CompletableFuture<String> notifyRefund(String orderId, String refundId, Long refundAmount, 
                                                String notifyUrl, boolean success) {
        Map<String, Object> data = buildRefundData(orderId, refundId, refundAmount, success);
        NotificationType type = success ? NotificationType.REFUND_SUCCESS : NotificationType.REFUND_FAILED;
        return sendNotification(orderId, type, data, notifyUrl);
    }

    /**
     * 发送通知
     *
     * @param orderId 订单号
     * @param type 通知类型
     * @param data 通知数据
     * @param notifyUrl 通知URL
     * @return 通知ID
     */
    public CompletableFuture<String> sendNotification(String orderId, NotificationType type, 
                                                    Map<String, Object> data, String notifyUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 生成通知ID
                String notificationId = generateNotificationId(orderId, type);
                
                // 创建通知消息
                NotificationMessage message = new NotificationMessage(notificationId, orderId, type, data, notifyUrl);
                
                // 加入队列
                notificationQueue.offer(message);
                totalNotifications.incrementAndGet();
                
                logger.info("通知已加入队列: ID={}, 订单号={}, 类型={}", notificationId, orderId, type.getName());
                return notificationId;
                
            } catch (Exception e) {
                logger.error("发送通知失败: 订单号={}, 类型={}", orderId, type, e);
                throw new RuntimeException("发送通知失败", e);
            }
        });
    }

    /**
     * 查询通知状态
     *
     * @param notificationId 通知ID
     * @return 通知消息
     */
    public NotificationMessage getNotificationStatus(String notificationId) {
        // 先查找正在处理的通知
        NotificationMessage message = processingNotifications.get(notificationId);
        if (message != null) {
            return message;
        }

        // 查找队列中的通知
        return notificationQueue.stream()
                .filter(msg -> notificationId.equals(msg.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 重发通知
     *
     * @param notificationId 通知ID
     * @return 是否成功
     */
    public CompletableFuture<Boolean> resendNotification(String notificationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NotificationMessage message = getNotificationStatus(notificationId);
                if (message == null) {
                    logger.warn("通知不存在: {}", notificationId);
                    return false;
                }

                // 重置状态
                message.setStatus(NotificationStatus.PENDING);
                message.setRetryCount(0);
                message.setErrorMessage(null);

                // 重新加入队列
                notificationQueue.offer(message);

                logger.info("通知已重新加入队列: ID={}", notificationId);
                return true;

            } catch (Exception e) {
                logger.error("重发通知失败: ID={}", notificationId, e);
                return false;
            }
        });
    }

    // ========== 私有方法 ==========

    /**
     * 启动通知处理器
     */
    private void startNotificationProcessor() {
        for (int i = 0; i < 5; i++) {
            executorService.submit(this::processNotifications);
        }
    }

    /**
     * 处理通知队列
     */
    private void processNotifications() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                NotificationMessage message = notificationQueue.poll();
                if (message == null) {
                    Thread.sleep(1000); // 队列为空时等待
                    continue;
                }

                processNotification(message);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("处理通知队列异常", e);
            }
        }
    }

    /**
     * 处理单个通知
     */
    private void processNotification(NotificationMessage message) {
        try {
            logger.debug("开始处理通知: ID={}, 订单号={}", message.getId(), message.getOrderId());

            // 标记为处理中
            message.setStatus(NotificationStatus.SENDING);
            processingNotifications.put(message.getId(), message);

            // 发送通知
            boolean success = sendNotificationRequest(message);

            if (success) {
                // 成功
                message.setStatus(NotificationStatus.SUCCESS);
                successNotifications.incrementAndGet();
                logger.info("通知发送成功: ID={}, 订单号={}", message.getId(), message.getOrderId());
            } else {
                // 失败，检查是否需要重试
                handleNotificationFailure(message);
            }

        } catch (Exception e) {
            logger.error("处理通知异常: ID={}", message.getId(), e);
            message.setErrorMessage(e.getMessage());
            handleNotificationFailure(message);
        } finally {
            // 移除处理中的记录
            processingNotifications.remove(message.getId());
        }
    }

    /**
     * 发送通知请求
     */
    private boolean sendNotificationRequest(NotificationMessage message) {
        try {
            // 模拟HTTP请求发送通知
            logger.debug("发送HTTP通知: URL={}, 数据={}", message.getNotifyUrl(), message.getData());

            // 简化的HTTP请求模拟
            if (message.getNotifyUrl() == null || message.getNotifyUrl().trim().isEmpty()) {
                return false;
            }

            // 模拟网络延迟
            Thread.sleep(100 + (int)(Math.random() * 500));

            // 模拟成功率（90%）
            return Math.random() < 0.9;

        } catch (Exception e) {
            logger.error("发送通知请求异常: ID={}", message.getId(), e);
            return false;
        }
    }

    /**
     * 处理通知失败
     */
    private void handleNotificationFailure(NotificationMessage message) {
        message.setRetryCount(message.getRetryCount() + 1);
        message.setLastRetryTime(LocalDateTime.now());

        if (message.getRetryCount() < maxRetryCount) {
            // 加入重试队列
            message.setStatus(NotificationStatus.PENDING);
            retryQueue.offer(message);
            logger.warn("通知发送失败，已加入重试队列: ID={}, 重试次数={}", 
                    message.getId(), message.getRetryCount());
        } else {
            // 超过最大重试次数
            message.setStatus(NotificationStatus.FAILED);
            failedNotifications.incrementAndGet();
            logger.error("通知发送失败，已达最大重试次数: ID={}", message.getId());
        }
    }

    /**
     * 启动重试处理器
     */
    private void startRetryProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                processRetryQueue();
            } catch (Exception e) {
                logger.error("处理重试队列异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 处理重试队列
     */
    private void processRetryQueue() {
        NotificationMessage message;
        while ((message = retryQueue.poll()) != null) {
            // 检查重试间隔
            if (message.getLastRetryTime() != null) {
                long elapsed = java.time.Duration.between(message.getLastRetryTime(), LocalDateTime.now()).toMillis();
                if (elapsed < retryIntervalMillis) {
                    // 还未到重试时间，放回队列
                    retryQueue.offer(message);
                    break;
                }
            }

            // 重新加入处理队列
            notificationQueue.offer(message);
            logger.debug("重试通知: ID={}, 重试次数={}", message.getId(), message.getRetryCount());
        }
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                cleanupExpiredNotifications();
            } catch (Exception e) {
                logger.error("清理过期通知异常", e);
            }
        }, 300, 300, TimeUnit.SECONDS); // 5分钟执行一次
    }

    /**
     * 清理过期通知
     */
    private void cleanupExpiredNotifications() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(24);
        
        // 清理正在处理的过期通知
        processingNotifications.entrySet().removeIf(entry -> 
                entry.getValue().getCreateTime().isBefore(expireTime));

        logger.debug("清理过期通知完成");
    }

    /**
     * 生成通知ID
     */
    private String generateNotificationId(String orderId, NotificationType type) {
        return String.format("NOTIFY_%s_%s_%d", orderId, type.getCode().toUpperCase(), System.currentTimeMillis());
    }

    /**
     * 构建支付成功通知数据
     */
    private Map<String, Object> buildPaymentSuccessData(PaymentOrder order) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("order_id", order.getOrderId());
        data.put("user_id", order.getUserId());
        data.put("product_id", order.getProductId());
        data.put("order_amount", order.getOrderAmount());
        data.put("paid_amount", order.getPaidAmount());
        data.put("currency", order.getCurrency());
        data.put("payment_channel", order.getPaymentChannel());
        data.put("channel_order_id", order.getChannelOrderId());
        data.put("pay_time", order.getPayTime());
        data.put("notify_type", NotificationType.PAYMENT_SUCCESS.getCode());
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    /**
     * 构建支付失败通知数据
     */
    private Map<String, Object> buildPaymentFailedData(PaymentOrder order, String errorCode, String errorMessage) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("order_id", order.getOrderId());
        data.put("user_id", order.getUserId());
        data.put("product_id", order.getProductId());
        data.put("order_amount", order.getOrderAmount());
        data.put("currency", order.getCurrency());
        data.put("payment_channel", order.getPaymentChannel());
        data.put("error_code", errorCode);
        data.put("error_message", errorMessage);
        data.put("notify_type", NotificationType.PAYMENT_FAILED.getCode());
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    /**
     * 构建退款通知数据
     */
    private Map<String, Object> buildRefundData(String orderId, String refundId, Long refundAmount, boolean success) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("order_id", orderId);
        data.put("refund_id", refundId);
        data.put("refund_amount", refundAmount);
        data.put("success", success);
        data.put("notify_type", success ? NotificationType.REFUND_SUCCESS.getCode() : NotificationType.REFUND_FAILED.getCode());
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    /**
     * 获取通知统计信息
     */
    public Map<String, Object> getNotificationStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalNotifications", totalNotifications.get());
        stats.put("successNotifications", successNotifications.get());
        stats.put("failedNotifications", failedNotifications.get());
        stats.put("pendingNotifications", notificationQueue.size());
        stats.put("retryNotifications", retryQueue.size());
        stats.put("processingNotifications", processingNotifications.size());
        
        double successRate = totalNotifications.get() > 0 ? 
                (double) successNotifications.get() / totalNotifications.get() : 0.0;
        stats.put("successRate", successRate);
        
        return stats;
    }

    /**
     * 关闭通知服务
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            scheduledExecutor.shutdown();
            
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            logger.info("支付通知服务已关闭");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
    }
}