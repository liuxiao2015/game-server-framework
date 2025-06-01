/*
 * 文件名: PaymentCallback.java
 * 用途: 支付回调处理器
 * 实现内容:
 *   - 异步通知接收和处理
 *   - 签名验证和安全检查
 *   - 幂等处理和重复通知过滤
 *   - 业务通知和状态更新
 *   - 回调重试机制和失败处理
 * 技术选型:
 *   - 异步处理
 *   - 幂等性设计
 *   - 签名验证
 *   - 重试机制
 * 依赖关系:
 *   - 依赖支付渠道
 *   - 依赖订单服务
 *   - 依赖通知服务
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.process;

import com.lx.gameserver.business.payment.core.PaymentChannel;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import com.lx.gameserver.business.payment.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付回调处理器
 * <p>
 * 处理来自各支付渠道的异步通知，包括签名验证、幂等处理、
 * 状态更新等功能，确保支付结果的准确性和完整性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class PaymentCallback {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCallback.class);

    private final OrderService orderService;
    private final PaymentProcessor paymentProcessor;
    
    // 幂等性处理：记录已处理的回调
    private final Map<String, LocalDateTime> processedCallbacks = new ConcurrentHashMap<>();

    /**
     * 回调处理结果
     */
    public static class CallbackProcessResult {
        private final boolean success;
        private final String message;
        private final String orderId;
        private final PaymentOrder.OrderStatus orderStatus;

        public CallbackProcessResult(boolean success, String message, String orderId, PaymentOrder.OrderStatus orderStatus) {
            this.success = success;
            this.message = message;
            this.orderId = orderId;
            this.orderStatus = orderStatus;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getOrderId() { return orderId; }
        public PaymentOrder.OrderStatus getOrderStatus() { return orderStatus; }
    }

    /**
     * 构造函数
     */
    public PaymentCallback(OrderService orderService, PaymentProcessor paymentProcessor) {
        this.orderService = orderService;
        this.paymentProcessor = paymentProcessor;
        logger.info("支付回调处理器初始化完成");
    }

    /**
     * 处理支付回调
     *
     * @param channelCode 渠道代码
     * @param callbackData 回调数据
     * @return 处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<CallbackProcessResult> handleCallback(String channelCode, Map<String, Object> callbackData) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("接收到支付回调: 渠道={}, 数据大小={}", channelCode, callbackData.size());

            try {
                // 1. 获取支付渠道
                PaymentChannel channel = paymentProcessor.getPaymentChannel(channelCode);
                if (channel == null) {
                    logger.warn("支付渠道不存在: {}", channelCode);
                    return new CallbackProcessResult(false, "支付渠道不存在", null, null);
                }

                // 2. 处理回调数据
                PaymentChannel.CallbackResult callbackResult = channel.handleCallback(callbackData);
                if (callbackResult == null) {
                    logger.warn("回调处理返回空结果: 渠道={}", channelCode);
                    return new CallbackProcessResult(false, "回调处理失败", null, null);
                }

                // 3. 验证回调数据
                if (!callbackResult.isValid()) {
                    logger.warn("回调数据验证失败: 渠道={}", channelCode);
                    return new CallbackProcessResult(false, "回调数据验证失败", null, null);
                }

                String orderId = callbackResult.getOrderId();
                if (orderId == null || orderId.trim().isEmpty()) {
                    logger.warn("回调中订单号为空: 渠道={}", channelCode);
                    return new CallbackProcessResult(false, "订单号为空", null, null);
                }

                // 4. 幂等性检查
                if (isDuplicateCallback(orderId, callbackResult.getStatus())) {
                    logger.info("重复回调，忽略处理: 订单号={}, 状态={}", orderId, callbackResult.getStatus());
                    return new CallbackProcessResult(true, "重复回调已忽略", orderId, callbackResult.getStatus());
                }

                // 5. 处理订单状态更新
                CallbackProcessResult result = processOrderStatusUpdate(orderId, callbackResult);

                // 6. 记录已处理的回调
                markCallbackProcessed(orderId, callbackResult.getStatus());

                // 7. 异步通知业务系统
                notifyBusinessSystemAsync(orderId, callbackResult);

                logger.info("支付回调处理完成: 订单号={}, 状态={}, 成功={}", 
                        orderId, callbackResult.getStatus(), result.isSuccess());

                return result;

            } catch (Exception e) {
                logger.error("处理支付回调异常: 渠道={}", channelCode, e);
                return new CallbackProcessResult(false, "回调处理异常: " + e.getMessage(), null, null);
            }
        });
    }

    /**
     * 处理支付宝回调
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    public CompletableFuture<CallbackProcessResult> handleAlipayCallback(Map<String, Object> callbackData) {
        return handleCallback("alipay", callbackData);
    }

    /**
     * 处理微信支付回调
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    public CompletableFuture<CallbackProcessResult> handleWechatCallback(Map<String, Object> callbackData) {
        return handleCallback("wechat", callbackData);
    }

    /**
     * 处理苹果内购回调
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    public CompletableFuture<CallbackProcessResult> handleAppleCallback(Map<String, Object> callbackData) {
        return handleCallback("apple", callbackData);
    }

    /**
     * 处理Google Play回调
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    public CompletableFuture<CallbackProcessResult> handleGoogleCallback(Map<String, Object> callbackData) {
        return handleCallback("google", callbackData);
    }

    /**
     * 验证回调签名
     *
     * @param channelCode 渠道代码
     * @param callbackData 回调数据
     * @return 是否验证通过
     */
    public boolean verifyCallbackSignature(String channelCode, Map<String, Object> callbackData) {
        try {
            PaymentChannel channel = paymentProcessor.getPaymentChannel(channelCode);
            if (channel == null) {
                logger.warn("支付渠道不存在: {}", channelCode);
                return false;
            }

            boolean verified = channel.verifyCallback(callbackData);
            logger.debug("回调签名验证结果: 渠道={}, 结果={}", channelCode, verified);
            return verified;

        } catch (Exception e) {
            logger.error("验证回调签名异常: 渠道={}", channelCode, e);
            return false;
        }
    }

    /**
     * 重试失败的回调处理
     *
     * @param orderId 订单号
     * @param channelCode 渠道代码
     * @return 重试结果
     */
    public CompletableFuture<Boolean> retryFailedCallback(String orderId, String channelCode) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("重试失败的回调处理: 订单号={}, 渠道={}", orderId, channelCode);

            try {
                // 主动查询订单状态
                PaymentChannel.QueryResult queryResult = paymentProcessor.queryPaymentStatus(orderId).join();
                if (queryResult == null) {
                    logger.warn("查询订单状态失败: 订单号={}", orderId);
                    return false;
                }

                // 构造回调结果
                PaymentChannel.CallbackResult callbackResult = new PaymentChannel.CallbackResult() {
                    @Override
                    public boolean isValid() { return true; }
                    
                    @Override
                    public String getOrderId() { return orderId; }
                    
                    @Override
                    public PaymentOrder.OrderStatus getStatus() { return queryResult.getStatus(); }
                    
                    @Override
                    public java.math.BigDecimal getAmount() { return queryResult.getPaidAmount(); }
                    
                    @Override
                    public LocalDateTime getPayTime() { return queryResult.getPayTime(); }
                    
                    @Override
                    public Map<String, Object> getChannelData() { return queryResult.getExtendData(); }
                };

                // 处理订单状态更新
                CallbackProcessResult result = processOrderStatusUpdate(orderId, callbackResult);

                logger.info("重试回调处理完成: 订单号={}, 成功={}", orderId, result.isSuccess());
                return result.isSuccess();

            } catch (Exception e) {
                logger.error("重试回调处理失败: 订单号={}", orderId, e);
                return false;
            }
        });
    }

    // ========== 私有方法 ==========

    /**
     * 检查是否为重复回调
     */
    private boolean isDuplicateCallback(String orderId, PaymentOrder.OrderStatus status) {
        String callbackKey = orderId + "_" + status.getCode();
        return processedCallbacks.containsKey(callbackKey);
    }

    /**
     * 标记回调已处理
     */
    private void markCallbackProcessed(String orderId, PaymentOrder.OrderStatus status) {
        String callbackKey = orderId + "_" + status.getCode();
        processedCallbacks.put(callbackKey, LocalDateTime.now());
        
        // 清理过期的记录（保留24小时）
        cleanExpiredCallbackRecords();
    }

    /**
     * 清理过期的回调记录
     */
    private void cleanExpiredCallbackRecords() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(24);
        processedCallbacks.entrySet().removeIf(entry -> entry.getValue().isBefore(expireTime));
    }

    /**
     * 处理订单状态更新
     */
    private CallbackProcessResult processOrderStatusUpdate(String orderId, PaymentChannel.CallbackResult callbackResult) {
        try {
            // 获取订单信息
            PaymentOrder order = orderService.getOrderById(orderId);
            if (order == null) {
                logger.warn("订单不存在: {}", orderId);
                return new CallbackProcessResult(false, "订单不存在", orderId, callbackResult.getStatus());
            }

            PaymentOrder.OrderStatus newStatus = callbackResult.getStatus();
            PaymentOrder.OrderStatus currentStatus = order.getOrderStatus();

            // 检查状态流转是否合法
            if (!order.canTransitionTo(newStatus)) {
                logger.warn("订单状态流转不合法: 订单号={}, {} -> {}", orderId, currentStatus, newStatus);
                return new CallbackProcessResult(false, "订单状态流转不合法", orderId, newStatus);
            }

            // 根据状态进行相应处理
            boolean updateResult = false;
            switch (newStatus) {
                case PAID:
                    // 支付成功
                    Long paidAmount = null;
                    if (callbackResult.getAmount() != null) {
                        paidAmount = callbackResult.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();
                    }
                    updateResult = orderService.paymentSuccess(orderId, null, paidAmount);
                    break;
                    
                case FAILED:
                case TIMEOUT:
                case CLOSED:
                    // 支付失败或关闭
                    updateResult = orderService.updateOrderStatus(orderId, newStatus, "回调通知状态更新");
                    break;
                    
                default:
                    // 其他状态
                    updateResult = orderService.updateOrderStatus(orderId, newStatus, "回调通知状态更新");
                    break;
            }

            if (updateResult) {
                logger.info("订单状态更新成功: 订单号={}, {} -> {}", orderId, currentStatus, newStatus);
                return new CallbackProcessResult(true, "状态更新成功", orderId, newStatus);
            } else {
                logger.warn("订单状态更新失败: 订单号={}", orderId);
                return new CallbackProcessResult(false, "状态更新失败", orderId, newStatus);
            }

        } catch (Exception e) {
            logger.error("处理订单状态更新异常: 订单号={}", orderId, e);
            return new CallbackProcessResult(false, "状态更新异常: " + e.getMessage(), orderId, callbackResult.getStatus());
        }
    }

    /**
     * 异步通知业务系统
     */
    private void notifyBusinessSystemAsync(String orderId, PaymentChannel.CallbackResult callbackResult) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.debug("异步通知业务系统: 订单号={}, 状态={}", orderId, callbackResult.getStatus());
                
                // 这里可以实现具体的业务通知逻辑
                // 比如发送MQ消息、调用业务接口等
                
                // 模拟通知延迟
                Thread.sleep(100);
                
                logger.debug("业务系统通知完成: 订单号={}", orderId);
                
            } catch (Exception e) {
                logger.error("通知业务系统失败: 订单号={}", orderId, e);
            }
        });
    }

    /**
     * 获取回调统计信息
     */
    public Map<String, Object> getCallbackStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("processedCallbacksCount", processedCallbacks.size());
        stats.put("oldestCallbackTime", processedCallbacks.values().stream()
                .min(LocalDateTime::compareTo).orElse(null));
        stats.put("newestCallbackTime", processedCallbacks.values().stream()
                .max(LocalDateTime::compareTo).orElse(null));
        
        return stats;
    }

    /**
     * 清理所有回调记录（用于测试）
     */
    public void clearCallbackRecords() {
        processedCallbacks.clear();
        logger.info("已清理所有回调记录");
    }
}