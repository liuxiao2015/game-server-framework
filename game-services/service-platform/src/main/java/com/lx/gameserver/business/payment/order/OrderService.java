/*
 * 文件名: OrderService.java
 * 用途: 订单服务核心实现
 * 实现内容:
 *   - 订单服务核心业务逻辑
 *   - 订单创建和防重复下单机制
 *   - 订单状态流转和生命周期管理
 *   - 订单查询（多维度）和统计分析
 *   - 订单超时处理和自动化流程
 * 技术选型:
 *   - Spring Service层
 *   - Redis分布式锁
 *   - 事务管理
 *   - 异步处理
 * 依赖关系:
 *   - 依赖OrderRepository数据访问
 *   - 依赖OrderNumberGenerator
 *   - 依赖OrderValidator验证器
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.order;

import com.lx.gameserver.business.payment.core.PaymentContext;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单服务
 * <p>
 * 提供订单的完整生命周期管理，包括创建、查询、状态更新、
 * 超时处理等功能，确保订单数据的一致性和完整性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderValidator orderValidator;
    
    // 订单创建锁（防重复下单）
    private final Map<String, Object> orderCreateLocks = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public OrderService(OrderRepository orderRepository,
                       OrderNumberGenerator orderNumberGenerator,
                       OrderValidator orderValidator) {
        this.orderRepository = orderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.orderValidator = orderValidator;
    }

    /**
     * 创建支付订单
     *
     * @param paymentContext 支付上下文
     * @return 支付订单
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder createOrder(PaymentContext paymentContext) {
        logger.info("创建支付订单开始: 用户ID={}, 商品ID={}", 
                paymentContext.getUserId(), paymentContext.getProductId());

        try {
            // 验证上下文数据
            orderValidator.validateContext(paymentContext);

            // 生成订单号
            String orderId = orderNumberGenerator.generateOrderNumber(paymentContext);
            paymentContext = PaymentContext.builder()
                    .orderId(orderId)
                    .userId(paymentContext.getUserId())
                    .productId(paymentContext.getProductId())
                    .productName(paymentContext.getProductName())
                    .orderAmount(paymentContext.getOrderAmount())
                    .currency(paymentContext.getCurrency())
                    .orderTitle(paymentContext.getOrderTitle())
                    .orderDesc(paymentContext.getOrderDesc())
                    .paymentChannel(paymentContext.getPaymentChannel())
                    .paymentMethod(paymentContext.getPaymentMethod())
                    .notifyUrl(paymentContext.getNotifyUrl())
                    .returnUrl(paymentContext.getReturnUrl())
                    .expireTime(paymentContext.getExpireTime())
                    .openId(paymentContext.getOpenId())
                    .userAccount(paymentContext.getUserAccount())
                    .userName(paymentContext.getUserName())
                    .userEmail(paymentContext.getUserEmail())
                    .userPhone(paymentContext.getUserPhone())
                    .clientIp(paymentContext.getClientIp())
                    .deviceType(paymentContext.getDeviceType())
                    .deviceId(paymentContext.getDeviceId())
                    .deviceModel(paymentContext.getDeviceModel())
                    .osVersion(paymentContext.getOsVersion())
                    .appVersion(paymentContext.getAppVersion())
                    .userAgent(paymentContext.getUserAgent())
                    .screenResolution(paymentContext.getScreenResolution())
                    .networkType(paymentContext.getNetworkType())
                    .carrier(paymentContext.getCarrier())
                    .countryCode(paymentContext.getCountryCode())
                    .province(paymentContext.getProvince())
                    .city(paymentContext.getCity())
                    .longitude(paymentContext.getLongitude())
                    .latitude(paymentContext.getLatitude())
                    .riskScore(paymentContext.getRiskScore())
                    .riskLevel(paymentContext.getRiskLevel())
                    .suspicious(paymentContext.getSuspicious())
                    .antiFraudResult(paymentContext.getAntiFraudResult())
                    .deviceFingerprint(paymentContext.getDeviceFingerprint())
                    .businessScene(paymentContext.getBusinessScene())
                    .promotionChannel(paymentContext.getPromotionChannel())
                    .couponId(paymentContext.getCouponId())
                    .discountAmount(paymentContext.getDiscountAmount())
                    .businessData(paymentContext.getBusinessData())
                    .requestTime(paymentContext.getRequestTime())
                    .traceId(paymentContext.getTraceId())
                    .sessionId(paymentContext.getSessionId())
                    .requestSource(paymentContext.getRequestSource())
                    .apiVersion(paymentContext.getApiVersion())
                    .channelData(paymentContext.getChannelData())
                    .internalData(paymentContext.getInternalData())
                    .build();

            // 防重复下单检查
            String lockKey = generateLockKey(paymentContext.getUserId(), paymentContext.getProductId());
            Object lock = orderCreateLocks.computeIfAbsent(lockKey, k -> new Object());
            
            synchronized (lock) {
                try {
                    // 检查是否存在未支付的相同订单
                    PaymentOrder existingOrder = checkDuplicateOrder(paymentContext);
                    if (existingOrder != null) {
                        logger.warn("发现重复订单: {}", existingOrder.getOrderId());
                        return existingOrder;
                    }

                    // 创建订单对象
                    PaymentOrder order = buildOrderFromContext(paymentContext);

                    // 验证订单数据
                    orderValidator.validateOrder(order);

                    // 保存订单
                    PaymentOrder savedOrder = orderRepository.save(order);

                    logger.info("支付订单创建成功: 订单号={}, 金额={}", 
                            savedOrder.getOrderId(), savedOrder.getOrderAmountInYuan());

                    return savedOrder;

                } finally {
                    // 清理锁
                    orderCreateLocks.remove(lockKey);
                }
            }

        } catch (Exception e) {
            logger.error("创建支付订单失败: 用户ID={}, 商品ID={}", 
                    paymentContext.getUserId(), paymentContext.getProductId(), e);
            throw new RuntimeException("创建订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据订单号查询订单
     *
     * @param orderId 订单号
     * @return 支付订单
     */
    public PaymentOrder getOrderById(String orderId) {
        logger.debug("查询订单: {}", orderId);
        
        PaymentOrder order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            logger.warn("订单不存在: {}", orderId);
        }
        
        return order;
    }

    /**
     * 根据用户ID查询订单列表
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 订单列表
     */
    public List<PaymentOrder> getOrdersByUserId(Long userId, int limit) {
        logger.debug("查询用户订单: 用户ID={}, 限制={}", userId, limit);
        return orderRepository.findByUserIdOrderByCreateTimeDesc(userId, limit);
    }

    /**
     * 根据状态查询订单列表
     *
     * @param status 订单状态
     * @param limit 限制数量
     * @return 订单列表
     */
    public List<PaymentOrder> getOrdersByStatus(PaymentOrder.OrderStatus status, int limit) {
        logger.debug("查询状态订单: 状态={}, 限制={}", status, limit);
        return orderRepository.findByOrderStatusOrderByCreateTimeDesc(status, limit);
    }

    /**
     * 更新订单状态
     *
     * @param orderId 订单号
     * @param newStatus 新状态
     * @param remark 备注
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderStatus(String orderId, PaymentOrder.OrderStatus newStatus, String remark) {
        logger.info("更新订单状态: 订单号={}, 新状态={}", orderId, newStatus);

        try {
            PaymentOrder order = orderRepository.findByOrderId(orderId);
            if (order == null) {
                logger.warn("订单不存在: {}", orderId);
                return false;
            }

            // 检查状态流转是否合法
            if (!order.canTransitionTo(newStatus)) {
                logger.warn("订单状态流转不合法: {} -> {}", order.getOrderStatus(), newStatus);
                return false;
            }

            // 更新状态
            order.setOrderStatus(newStatus);
            order.setRemark(remark);
            order.setUpdateTime(LocalDateTime.now());

            // 如果是支付成功，设置支付时间
            if (newStatus == PaymentOrder.OrderStatus.PAID) {
                order.setPayTime(LocalDateTime.now());
            }

            orderRepository.update(order);

            logger.info("订单状态更新成功: 订单号={}, 状态={}", orderId, newStatus);
            return true;

        } catch (Exception e) {
            logger.error("更新订单状态失败: 订单号={}", orderId, e);
            return false;
        }
    }

    /**
     * 支付成功处理
     *
     * @param orderId 订单号
     * @param channelOrderId 渠道订单号
     * @param paidAmount 实际支付金额
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean paymentSuccess(String orderId, String channelOrderId, Long paidAmount) {
        logger.info("订单支付成功处理: 订单号={}, 渠道订单号={}, 支付金额={}分", 
                orderId, channelOrderId, paidAmount);

        try {
            PaymentOrder order = orderRepository.findByOrderId(orderId);
            if (order == null) {
                logger.warn("订单不存在: {}", orderId);
                return false;
            }

            // 检查订单状态
            if (!order.canTransitionTo(PaymentOrder.OrderStatus.PAID)) {
                logger.warn("订单状态不允许支付: {} -> {}", order.getOrderStatus(), PaymentOrder.OrderStatus.PAID);
                return false;
            }

            // 更新订单信息
            order.setOrderStatus(PaymentOrder.OrderStatus.PAID);
            order.setChannelOrderId(channelOrderId);
            order.setPaidAmount(paidAmount);
            order.setPayTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());

            orderRepository.update(order);

            logger.info("订单支付成功处理完成: 订单号={}", orderId);
            return true;

        } catch (Exception e) {
            logger.error("订单支付成功处理失败: 订单号={}", orderId, e);
            return false;
        }
    }

    /**
     * 关闭订单
     *
     * @param orderId 订单号
     * @param reason 关闭原因
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(String orderId, String reason) {
        logger.info("关闭订单: 订单号={}, 原因={}", orderId, reason);

        try {
            PaymentOrder order = orderRepository.findByOrderId(orderId);
            if (order == null) {
                logger.warn("订单不存在: {}", orderId);
                return false;
            }

            // 检查订单是否可以关闭
            if (!order.canClose()) {
                logger.warn("订单状态不允许关闭: {}", order.getOrderStatus());
                return false;
            }

            // 更新订单状态
            order.setOrderStatus(PaymentOrder.OrderStatus.CLOSED);
            order.setRemark(reason);
            order.setUpdateTime(LocalDateTime.now());

            orderRepository.update(order);

            logger.info("订单关闭成功: 订单号={}", orderId);
            return true;

        } catch (Exception e) {
            logger.error("关闭订单失败: 订单号={}", orderId, e);
            return false;
        }
    }

    /**
     * 处理超时订单
     *
     * @return 处理数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int handleTimeoutOrders() {
        logger.info("开始处理超时订单");

        try {
            List<PaymentOrder> timeoutOrders = orderRepository.findTimeoutOrders(LocalDateTime.now());
            int processedCount = 0;

            for (PaymentOrder order : timeoutOrders) {
                try {
                    if (updateOrderStatus(order.getOrderId(), PaymentOrder.OrderStatus.TIMEOUT, "订单超时自动关闭")) {
                        processedCount++;
                        logger.info("订单超时处理成功: {}", order.getOrderId());
                    }
                } catch (Exception e) {
                    logger.error("处理超时订单失败: {}", order.getOrderId(), e);
                }
            }

            logger.info("超时订单处理完成: 总数={}, 成功={}", timeoutOrders.size(), processedCount);
            return processedCount;

        } catch (Exception e) {
            logger.error("处理超时订单异常", e);
            return 0;
        }
    }

    /**
     * 异步处理超时订单
     */
    public void handleTimeoutOrdersAsync() {
        CompletableFuture.runAsync(this::handleTimeoutOrders)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("异步处理超时订单失败", throwable);
                    }
                });
    }

    /**
     * 获取订单统计信息
     *
     * @param userId 用户ID（可选）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息
     */
    public Map<String, Object> getOrderStatistics(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("查询订单统计: 用户ID={}, 开始时间={}, 结束时间={}", userId, startTime, endTime);
        return orderRepository.getOrderStatistics(userId, startTime, endTime);
    }

    // ========== 私有方法 ==========

    /**
     * 生成锁键
     */
    private String generateLockKey(Long userId, String productId) {
        return String.format("order_create_lock:%d:%s", userId, productId);
    }

    /**
     * 检查重复订单
     */
    private PaymentOrder checkDuplicateOrder(PaymentContext context) {
        return orderRepository.findPendingOrderByUserAndProduct(
                context.getUserId(), context.getProductId(), LocalDateTime.now().minusMinutes(30));
    }

    /**
     * 从上下文构建订单对象
     */
    private PaymentOrder buildOrderFromContext(PaymentContext context) {
        PaymentOrder order = new PaymentOrder();
        
        order.setOrderId(context.getOrderId());
        order.setUserId(context.getUserId());
        order.setProductId(context.getProductId());
        order.setProductName(context.getProductName());
        order.setOrderType(PaymentOrder.OrderType.PRODUCT); // 默认商品类型
        order.setOrderAmount(context.getOrderAmountInFen());
        order.setCurrency(context.getCurrency());
        order.setPaymentChannel(context.getPaymentChannel());
        order.setPaymentMethod(context.getPaymentMethod());
        order.setOrderStatus(PaymentOrder.OrderStatus.PENDING);
        order.setOrderTitle(context.getOrderTitle());
        order.setOrderDesc(context.getOrderDesc());
        order.setNotifyUrl(context.getNotifyUrl());
        order.setReturnUrl(context.getReturnUrl());
        order.setClientIp(context.getClientIp());
        
        // 设置过期时间
        if (context.getExpireTime() != null) {
            order.setExpireTime(context.getExpireTime());
        } else {
            order.setExpireTime(LocalDateTime.now().plusMinutes(30)); // 默认30分钟超时
        }

        // 设置设备信息
        if (context.getDeviceType() != null) {
            order.setDeviceInfo(context.getDeviceType().getCode());
        }

        // 设置扩展信息
        try {
            if (context.getBusinessData() != null && !context.getBusinessData().isEmpty()) {
                order.setBusinessData(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(context.getBusinessData()));
            }
            
            if (context.getChannelData() != null && !context.getChannelData().isEmpty()) {
                order.setChannelData(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(context.getChannelData()));
            }
        } catch (Exception e) {
            logger.warn("序列化扩展数据失败", e);
        }

        return order;
    }
}