/*
 * 文件名: PaymentChannel.java
 * 用途: 支付渠道抽象接口
 * 实现内容:
 *   - 定义统一的支付渠道接口规范
 *   - 支持下单、查询、退款、关闭等核心操作
 *   - 异步回调处理机制
 *   - 渠道状态监控和配置管理
 *   - 渠道路由策略支持
 * 技术选型:
 *   - 抽象接口设计
 *   - 异步回调模式
 *   - 状态机管理
 *   - 策略模式
 * 依赖关系:
 *   - 被具体支付渠道实现
 *   - 被支付处理器调用
 *   - 依赖PaymentOrder和PaymentContext
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.core;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 支付渠道接口
 * <p>
 * 定义所有支付渠道必须实现的统一接口，包括支付下单、查询、
 * 退款、关闭等核心功能。所有支付渠道实现都需要遵循此接口规范。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface PaymentChannel {

    /**
     * 支付渠道枚举
     */
    enum ChannelType {
        /** 支付宝 */
        ALIPAY("alipay", "支付宝"),
        /** 微信支付 */
        WECHAT_PAY("wechat", "微信支付"),
        /** 苹果支付 */
        APPLE_PAY("apple", "苹果支付"),
        /** Google支付 */
        GOOGLE_PAY("google", "Google支付"),
        /** 银联支付 */
        UNION_PAY("unionpay", "银联支付"),
        /** PayPal */
        PAYPAL("paypal", "PayPal");

        private final String code;
        private final String name;

        ChannelType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 支付方式枚举
     */
    enum PaymentMethod {
        /** APP支付 */
        APP("app", "APP支付"),
        /** H5支付 */
        H5("h5", "H5支付"),
        /** PC网页支付 */
        PC("pc", "PC网页支付"),
        /** 小程序支付 */
        MINI_PROGRAM("mini", "小程序支付"),
        /** 扫码支付 */
        QR_CODE("qr", "扫码支付"),
        /** 内购支付 */
        IN_APP_PURCHASE("iap", "内购支付");

        private final String code;
        private final String name;

        PaymentMethod(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 渠道状态枚举
     */
    enum ChannelStatus {
        /** 正常 */
        NORMAL("normal", "正常"),
        /** 维护中 */
        MAINTENANCE("maintenance", "维护中"),
        /** 已禁用 */
        DISABLED("disabled", "已禁用"),
        /** 异常 */
        ERROR("error", "异常");

        private final String code;
        private final String name;

        ChannelStatus(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 支付结果
     */
    interface PaymentResult {
        /** 是否成功 */
        boolean isSuccess();
        /** 渠道订单号 */
        String getChannelOrderId();
        /** 支付URL或参数 */
        String getPaymentData();
        /** 错误码 */
        String getErrorCode();
        /** 错误信息 */
        String getErrorMessage();
        /** 扩展信息 */
        Map<String, Object> getExtendData();
    }

    /**
     * 查询结果
     */
    interface QueryResult {
        /** 支付状态 */
        PaymentOrder.OrderStatus getStatus();
        /** 渠道订单号 */
        String getChannelOrderId();
        /** 实际支付金额 */
        BigDecimal getPaidAmount();
        /** 支付时间 */
        LocalDateTime getPayTime();
        /** 扩展信息 */
        Map<String, Object> getExtendData();
    }

    /**
     * 退款结果
     */
    interface RefundResult {
        /** 是否成功 */
        boolean isSuccess();
        /** 渠道退款单号 */
        String getChannelRefundId();
        /** 退款金额 */
        BigDecimal getRefundAmount();
        /** 错误码 */
        String getErrorCode();
        /** 错误信息 */
        String getErrorMessage();
        /** 扩展信息 */
        Map<String, Object> getExtendData();
    }

    /**
     * 回调结果
     */
    interface CallbackResult {
        /** 是否验证通过 */
        boolean isValid();
        /** 订单号 */
        String getOrderId();
        /** 支付状态 */
        PaymentOrder.OrderStatus getStatus();
        /** 支付金额 */
        BigDecimal getAmount();
        /** 支付时间 */
        LocalDateTime getPayTime();
        /** 渠道数据 */
        Map<String, Object> getChannelData();
    }

    /**
     * 获取渠道类型
     *
     * @return 渠道类型
     */
    ChannelType getChannelType();

    /**
     * 获取支持的支付方式
     *
     * @return 支持的支付方式列表
     */
    PaymentMethod[] getSupportedMethods();

    /**
     * 获取渠道状态
     *
     * @return 渠道状态
     */
    ChannelStatus getChannelStatus();

    /**
     * 检查渠道是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 创建支付订单
     *
     * @param paymentContext 支付上下文
     * @return 支付结果
     */
    CompletableFuture<PaymentResult> createPayment(PaymentContext paymentContext);

    /**
     * 查询支付状态
     *
     * @param orderId 订单号
     * @param channelOrderId 渠道订单号
     * @return 查询结果
     */
    CompletableFuture<QueryResult> queryPayment(String orderId, String channelOrderId);

    /**
     * 申请退款
     *
     * @param orderId 原订单号
     * @param refundId 退款单号
     * @param refundAmount 退款金额
     * @param reason 退款原因
     * @return 退款结果
     */
    CompletableFuture<RefundResult> refund(String orderId, String refundId, 
                                         BigDecimal refundAmount, String reason);

    /**
     * 查询退款状态
     *
     * @param refundId 退款单号
     * @return 退款结果
     */
    CompletableFuture<RefundResult> queryRefund(String refundId);

    /**
     * 关闭订单
     *
     * @param orderId 订单号
     * @return 是否成功
     */
    CompletableFuture<Boolean> closeOrder(String orderId);

    /**
     * 处理异步回调
     *
     * @param callbackData 回调数据
     * @return 回调处理结果
     */
    CallbackResult handleCallback(Map<String, Object> callbackData);

    /**
     * 验证回调签名
     *
     * @param callbackData 回调数据
     * @return 是否验证通过
     */
    boolean verifyCallback(Map<String, Object> callbackData);

    /**
     * 获取渠道配置
     *
     * @return 渠道配置
     */
    Map<String, Object> getChannelConfig();

    /**
     * 更新渠道配置
     *
     * @param config 新配置
     */
    void updateChannelConfig(Map<String, Object> config);

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    CompletableFuture<Boolean> healthCheck();

    /**
     * 获取支持的最小金额（分）
     *
     * @return 最小金额
     */
    default long getMinAmount() {
        return 1L; // 默认1分
    }

    /**
     * 获取支持的最大金额（分）
     *
     * @return 最大金额
     */
    default long getMaxAmount() {
        return 100000000L; // 默认1000万分
    }

    /**
     * 是否支持部分退款
     *
     * @return 是否支持
     */
    default boolean supportPartialRefund() {
        return true;
    }

    /**
     * 获取订单有效期（分钟）
     *
     * @return 有效期
     */
    default int getOrderTimeoutMinutes() {
        return 30; // 默认30分钟
    }
}