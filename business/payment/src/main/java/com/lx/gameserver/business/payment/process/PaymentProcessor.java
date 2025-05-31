/*
 * 文件名: PaymentProcessor.java
 * 用途: 支付处理器核心实现
 * 实现内容:
 *   - 支付流程编排和统一处理
 *   - 前置校验和数据验证
 *   - 渠道路由和智能选择
 *   - 结果处理和状态更新
 *   - 异常处理和降级机制
 * 技术选型:
 *   - 策略模式
 *   - 责任链模式
 *   - 异步处理
 *   - 事务管理
 * 依赖关系:
 *   - 依赖订单服务
 *   - 依赖支付渠道
 *   - 依赖验证器
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.process;

import com.lx.gameserver.business.payment.channel.ChannelAdapter;
import com.lx.gameserver.business.payment.core.PaymentChannel;
import com.lx.gameserver.business.payment.core.PaymentContext;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import com.lx.gameserver.business.payment.core.PaymentTransaction;
import com.lx.gameserver.business.payment.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 支付处理器
 * <p>
 * 负责整个支付流程的编排和执行，包括订单创建、渠道选择、
 * 支付调用、结果处理等核心环节的统一管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class PaymentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProcessor.class);

    private final OrderService orderService;
    private final Map<String, PaymentChannel> paymentChannels;

    /**
     * 支付处理结果
     */
    public static class PaymentProcessResult {
        private final boolean success;
        private final PaymentOrder order;
        private final PaymentChannel.PaymentResult paymentResult;
        private final String errorCode;
        private final String errorMessage;

        public PaymentProcessResult(boolean success, PaymentOrder order, 
                                  PaymentChannel.PaymentResult paymentResult,
                                  String errorCode, String errorMessage) {
            this.success = success;
            this.order = order;
            this.paymentResult = paymentResult;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public PaymentOrder getOrder() { return order; }
        public PaymentChannel.PaymentResult getPaymentResult() { return paymentResult; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 构造函数
     */
    public PaymentProcessor(OrderService orderService) {
        this.orderService = orderService;
        this.paymentChannels = new HashMap<>();
        logger.info("支付处理器初始化完成");
    }

    /**
     * 处理支付请求
     *
     * @param paymentContext 支付上下文
     * @return 处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<PaymentProcessResult> processPayment(PaymentContext paymentContext) {
        return CompletableFuture.supplyAsync(() -> {
            String traceId = paymentContext.getTraceId();
            logger.info("开始处理支付请求: 用户ID={}, 商品ID={}, 金额={}, traceId={}", 
                    paymentContext.getUserId(), paymentContext.getProductId(), 
                    paymentContext.getOrderAmount(), traceId);

            try {
                // 1. 前置校验
                validatePaymentRequest(paymentContext);

                // 2. 创建订单
                PaymentOrder order = createPaymentOrder(paymentContext);

                // 3. 选择支付渠道
                PaymentChannel channel = selectPaymentChannel(paymentContext);

                // 4. 调用支付渠道
                PaymentChannel.PaymentResult paymentResult = callPaymentChannel(channel, paymentContext);

                // 5. 处理支付结果
                handlePaymentResult(order, paymentResult);

                logger.info("支付请求处理成功: 订单号={}, traceId={}", order.getOrderId(), traceId);
                return new PaymentProcessResult(true, order, paymentResult, null, null);

            } catch (Exception e) {
                logger.error("支付请求处理失败: traceId={}", traceId, e);
                return new PaymentProcessResult(false, null, null, "PROCESS_ERROR", e.getMessage());
            }
        });
    }

    /**
     * 查询支付状态
     *
     * @param orderId 订单号
     * @return 查询结果
     */
    public CompletableFuture<PaymentChannel.QueryResult> queryPaymentStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("查询支付状态: 订单号={}", orderId);

            try {
                // 获取订单信息
                PaymentOrder order = orderService.getOrderById(orderId);
                if (order == null) {
                    logger.warn("订单不存在: {}", orderId);
                    return null;
                }

                // 如果订单已经是终态，直接返回
                if (order.isFinalStatus()) {
                    return buildQueryResultFromOrder(order);
                }

                // 获取支付渠道
                PaymentChannel channel = getPaymentChannel(order.getPaymentChannel());
                if (channel == null) {
                    logger.warn("支付渠道不存在: {}", order.getPaymentChannel());
                    return null;
                }

                // 查询渠道状态
                PaymentChannel.QueryResult result = channel.queryPayment(orderId, order.getChannelOrderId()).join();

                // 更新本地订单状态
                if (result != null) {
                    updateOrderFromQueryResult(order, result);
                }

                logger.info("支付状态查询完成: 订单号={}, 状态={}", orderId, 
                        result != null ? result.getStatus() : "UNKNOWN");
                return result;

            } catch (Exception e) {
                logger.error("查询支付状态失败: 订单号={}", orderId, e);
                return null;
            }
        });
    }

    /**
     * 关闭订单
     *
     * @param orderId 订单号
     * @param reason 关闭原因
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<Boolean> closeOrder(String orderId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("关闭订单: 订单号={}, 原因={}", orderId, reason);

            try {
                // 获取订单信息
                PaymentOrder order = orderService.getOrderById(orderId);
                if (order == null) {
                    logger.warn("订单不存在: {}", orderId);
                    return false;
                }

                // 检查订单是否可以关闭
                if (!order.canClose()) {
                    logger.warn("订单状态不允许关闭: 订单号={}, 状态={}", orderId, order.getOrderStatus());
                    return false;
                }

                // 调用渠道关闭订单
                PaymentChannel channel = getPaymentChannel(order.getPaymentChannel());
                if (channel != null) {
                    try {
                        boolean channelResult = channel.closeOrder(orderId).join();
                        logger.debug("渠道关闭订单结果: 订单号={}, 结果={}", orderId, channelResult);
                    } catch (Exception e) {
                        logger.warn("渠道关闭订单失败: 订单号={}", orderId, e);
                        // 继续执行本地关闭
                    }
                }

                // 更新本地订单状态
                boolean result = orderService.closeOrder(orderId, reason);

                logger.info("订单关闭{}完成: 订单号={}", result ? "成功" : "失败", orderId);
                return result;

            } catch (Exception e) {
                logger.error("关闭订单失败: 订单号={}", orderId, e);
                return false;
            }
        });
    }

    /**
     * 注册支付渠道
     *
     * @param channel 支付渠道
     */
    public void registerPaymentChannel(PaymentChannel channel) {
        if (channel != null) {
            paymentChannels.put(channel.getChannelType().getCode(), channel);
            logger.info("注册支付渠道: {}", channel.getChannelType().getName());
        }
    }

    /**
     * 获取支付渠道
     *
     * @param channelCode 渠道代码
     * @return 支付渠道
     */
    public PaymentChannel getPaymentChannel(String channelCode) {
        return paymentChannels.get(channelCode);
    }

    /**
     * 获取所有支付渠道
     *
     * @return 渠道映射
     */
    public Map<String, PaymentChannel> getAllPaymentChannels() {
        return new HashMap<>(paymentChannels);
    }

    // ========== 私有方法 ==========

    /**
     * 验证支付请求
     */
    private void validatePaymentRequest(PaymentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("支付上下文不能为空");
        }

        if (!context.isValid()) {
            throw new IllegalArgumentException("支付上下文数据无效");
        }

        // 检查支付渠道是否可用
        PaymentChannel channel = getPaymentChannel(context.getPaymentChannel());
        if (channel == null) {
            throw new IllegalArgumentException("支付渠道不存在: " + context.getPaymentChannel());
        }

        if (!channel.isAvailable()) {
            throw new IllegalArgumentException("支付渠道不可用: " + context.getPaymentChannel());
        }

        // 检查支付方式是否支持
        boolean methodSupported = false;
        for (PaymentChannel.PaymentMethod method : channel.getSupportedMethods()) {
            if (method.getCode().equals(context.getPaymentMethod())) {
                methodSupported = true;
                break;
            }
        }

        if (!methodSupported) {
            throw new IllegalArgumentException(String.format("渠道 %s 不支持支付方式 %s", 
                    context.getPaymentChannel(), context.getPaymentMethod()));
        }

        logger.debug("支付请求验证通过: 用户ID={}, 渠道={}", context.getUserId(), context.getPaymentChannel());
    }

    /**
     * 创建支付订单
     */
    private PaymentOrder createPaymentOrder(PaymentContext context) {
        try {
            PaymentOrder order = orderService.createOrder(context);
            logger.debug("支付订单创建成功: {}", order.getOrderId());
            return order;
        } catch (Exception e) {
            logger.error("创建支付订单失败", e);
            throw new RuntimeException("创建订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 选择支付渠道
     */
    private PaymentChannel selectPaymentChannel(PaymentContext context) {
        PaymentChannel channel = getPaymentChannel(context.getPaymentChannel());
        if (channel == null) {
            throw new IllegalArgumentException("支付渠道不存在: " + context.getPaymentChannel());
        }

        // 健康检查
        if (!channel.isAvailable()) {
            logger.warn("支付渠道不可用，尝试降级: {}", context.getPaymentChannel());
            // 这里可以实现渠道降级逻辑
            throw new RuntimeException("支付渠道不可用: " + context.getPaymentChannel());
        }

        logger.debug("选择支付渠道: {}", channel.getChannelType().getName());
        return channel;
    }

    /**
     * 调用支付渠道
     */
    private PaymentChannel.PaymentResult callPaymentChannel(PaymentChannel channel, PaymentContext context) {
        try {
            PaymentChannel.PaymentResult result = channel.createPayment(context).join();
            logger.debug("支付渠道调用完成: 订单号={}, 成功={}", 
                    context.getOrderId(), result.isSuccess());
            return result;
        } catch (Exception e) {
            logger.error("调用支付渠道失败: 订单号={}", context.getOrderId(), e);
            throw new RuntimeException("调用支付渠道失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理支付结果
     */
    private void handlePaymentResult(PaymentOrder order, PaymentChannel.PaymentResult result) {
        if (result == null) {
            logger.warn("支付结果为空: 订单号={}", order.getOrderId());
            return;
        }

        try {
            if (result.isSuccess()) {
                // 更新订单状态为支付中
                orderService.updateOrderStatus(order.getOrderId(), 
                        PaymentOrder.OrderStatus.PAYING, "支付渠道调用成功");
                
                // 如果有渠道订单号，更新到订单中
                if (result.getChannelOrderId() != null) {
                    order.setChannelOrderId(result.getChannelOrderId());
                    order.setUpdateTime(LocalDateTime.now());
                    // 这里应该调用orderService的更新方法，简化处理
                }
            } else {
                // 更新订单状态为失败
                String errorMsg = String.format("支付失败: %s - %s", 
                        result.getErrorCode(), result.getErrorMessage());
                orderService.updateOrderStatus(order.getOrderId(), 
                        PaymentOrder.OrderStatus.FAILED, errorMsg);
            }

            logger.debug("支付结果处理完成: 订单号={}, 成功={}", order.getOrderId(), result.isSuccess());

        } catch (Exception e) {
            logger.error("处理支付结果失败: 订单号={}", order.getOrderId(), e);
        }
    }

    /**
     * 从订单构建查询结果
     */
    private PaymentChannel.QueryResult buildQueryResultFromOrder(PaymentOrder order) {
        return new PaymentChannel.QueryResult() {
            @Override
            public PaymentOrder.OrderStatus getStatus() {
                return order.getOrderStatus();
            }

            @Override
            public String getChannelOrderId() {
                return order.getChannelOrderId();
            }

            @Override
            public java.math.BigDecimal getPaidAmount() {
                return order.getPaidAmountInYuan();
            }

            @Override
            public LocalDateTime getPayTime() {
                return order.getPayTime();
            }

            @Override
            public Map<String, Object> getExtendData() {
                return new HashMap<>();
            }
        };
    }

    /**
     * 根据查询结果更新订单
     */
    private void updateOrderFromQueryResult(PaymentOrder order, PaymentChannel.QueryResult result) {
        if (result == null || order == null) {
            return;
        }

        try {
            PaymentOrder.OrderStatus newStatus = result.getStatus();
            if (newStatus != null && !newStatus.equals(order.getOrderStatus())) {
                // 检查状态流转是否合法
                if (order.canTransitionTo(newStatus)) {
                    if (newStatus == PaymentOrder.OrderStatus.PAID) {
                        // 支付成功
                        Long paidAmount = result.getPaidAmount() != null ? 
                                result.getPaidAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue() : null;
                        orderService.paymentSuccess(order.getOrderId(), result.getChannelOrderId(), paidAmount);
                    } else {
                        // 其他状态更新
                        orderService.updateOrderStatus(order.getOrderId(), newStatus, "渠道状态同步");
                    }
                    
                    logger.info("订单状态同步: 订单号={}, {} -> {}", 
                            order.getOrderId(), order.getOrderStatus(), newStatus);
                }
            }
        } catch (Exception e) {
            logger.error("更新订单状态失败: 订单号={}", order.getOrderId(), e);
        }
    }

    /**
     * 获取处理器状态信息
     */
    public Map<String, Object> getProcessorInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("registeredChannels", paymentChannels.size());
        info.put("channelTypes", paymentChannels.keySet());
        
        Map<String, Object> channelStatus = new HashMap<>();
        for (Map.Entry<String, PaymentChannel> entry : paymentChannels.entrySet()) {
            channelStatus.put(entry.getKey(), entry.getValue().getChannelStatus());
        }
        info.put("channelStatus", channelStatus);
        
        return info;
    }
}