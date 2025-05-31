/*
 * 文件名: AlipayChannel.java
 * 用途: 支付宝支付渠道实现
 * 实现内容:
 *   - 支付宝支付完整集成实现
 *   - 支持APP支付、H5支付、PC支付
 *   - 签名验证机制和安全处理
 *   - 异步通知处理和订单查询
 *   - 退款功能和退款查询实现
 * 技术选型:
 *   - 支付宝官方SDK
 *   - RSA签名验证
 *   - JSON数据处理
 *   - 异步回调处理
 * 依赖关系:
 *   - 继承ChannelAdapter基类
 *   - 集成支付宝SDK
 *   - 依赖加密签名组件
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.channel;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lx.gameserver.business.payment.core.PaymentContext;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import okhttp3.Request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 支付宝支付渠道实现
 * <p>
 * 实现支付宝的完整支付功能，包括APP支付、H5支付、PC支付等多种支付方式，
 * 支持订单查询、退款处理、异步通知验证等核心功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class AlipayChannel extends ChannelAdapter {

    private final AlipayClient alipayClient;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String privateKey;
    private final String publicKey;
    private final String gatewayUrl;

    /**
     * 构造函数
     */
    public AlipayChannel(Map<String, Object> config) {
        super(config);
        
        this.appId = getConfigValue("appId", "");
        this.privateKey = getConfigValue("privateKey", "");
        this.publicKey = getConfigValue("publicKey", "");
        this.gatewayUrl = getConfigValue("gatewayUrl", "https://openapi.alipay.com/gateway.do");
        
        this.alipayClient = new DefaultAlipayClient(
                gatewayUrl, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
        this.objectMapper = new ObjectMapper();
        
        logger.info("支付宝支付渠道初始化完成，应用ID: {}", maskAppId(appId));
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.ALIPAY;
    }

    @Override
    public PaymentMethod[] getSupportedMethods() {
        return new PaymentMethod[]{
                PaymentMethod.APP,
                PaymentMethod.H5,
                PaymentMethod.PC,
                PaymentMethod.QR_CODE
        };
    }

    @Override
    public CompletableFuture<PaymentResult> createPayment(PaymentContext paymentContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("创建支付宝支付订单: {}", paymentContext.getOrderId());
                
                PaymentMethod method = PaymentMethod.valueOf(paymentContext.getPaymentMethod().toUpperCase());
                
                return switch (method) {
                    case APP -> createAppPayment(paymentContext);
                    case H5 -> createH5Payment(paymentContext);
                    case PC -> createPcPayment(paymentContext);
                    case QR_CODE -> createQrCodePayment(paymentContext);
                    default -> throw new IllegalArgumentException("不支持的支付方式: " + method);
                };
                
            } catch (Exception e) {
                logger.error("创建支付宝支付订单失败: {}", paymentContext.getOrderId(), e);
                return buildPaymentResult(false, null, null, "CREATE_FAILED", e.getMessage());
            }
        });
    }

    /**
     * 创建APP支付
     */
    private PaymentResult createAppPayment(PaymentContext context) throws AlipayApiException {
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(context.getOrderId());
        model.setSubject(context.getProductName());
        model.setTotalAmount(formatAmount(context.getOrderAmountInFen()));
        model.setBody(context.getOrderDesc());
        model.setTimeoutExpress(getOrderTimeoutMinutes() + "m");
        model.setProductCode("QUICK_MSECURITY_PAY");
        
        request.setBizModel(model);
        request.setNotifyUrl(context.getNotifyUrl());
        
        AlipayTradeAppPayResponse response = alipayClient.sdkExecute(request);
        
        if (response.isSuccess()) {
            logger.info("支付宝APP支付创建成功: {}", context.getOrderId());
            return buildPaymentResult(true, context.getOrderId(), response.getBody(), null, null);
        } else {
            logger.warn("支付宝APP支付创建失败: {} -> {}", context.getOrderId(), response.getSubMsg());
            return buildPaymentResult(false, null, null, response.getCode(), response.getSubMsg());
        }
    }

    /**
     * 创建H5支付
     */
    private PaymentResult createH5Payment(PaymentContext context) throws AlipayApiException {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(context.getOrderId());
        model.setSubject(context.getProductName());
        model.setTotalAmount(formatAmount(context.getOrderAmountInFen()));
        model.setBody(context.getOrderDesc());
        model.setTimeoutExpress(getOrderTimeoutMinutes() + "m");
        model.setProductCode("QUICK_WAP_WAY");
        
        request.setBizModel(model);
        request.setNotifyUrl(context.getNotifyUrl());
        request.setReturnUrl(context.getReturnUrl());
        
        AlipayTradeWapPayResponse response = alipayClient.pageExecute(request);
        
        if (response.isSuccess()) {
            logger.info("支付宝H5支付创建成功: {}", context.getOrderId());
            return buildPaymentResult(true, context.getOrderId(), response.getBody(), null, null);
        } else {
            logger.warn("支付宝H5支付创建失败: {} -> {}", context.getOrderId(), response.getSubMsg());
            return buildPaymentResult(false, null, null, response.getCode(), response.getSubMsg());
        }
    }

    /**
     * 创建PC支付
     */
    private PaymentResult createPcPayment(PaymentContext context) throws AlipayApiException {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(context.getOrderId());
        model.setSubject(context.getProductName());
        model.setTotalAmount(formatAmount(context.getOrderAmountInFen()));
        model.setBody(context.getOrderDesc());
        model.setTimeoutExpress(getOrderTimeoutMinutes() + "m");
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        
        request.setBizModel(model);
        request.setNotifyUrl(context.getNotifyUrl());
        request.setReturnUrl(context.getReturnUrl());
        
        AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
        
        if (response.isSuccess()) {
            logger.info("支付宝PC支付创建成功: {}", context.getOrderId());
            return buildPaymentResult(true, context.getOrderId(), response.getBody(), null, null);
        } else {
            logger.warn("支付宝PC支付创建失败: {} -> {}", context.getOrderId(), response.getSubMsg());
            return buildPaymentResult(false, null, null, response.getCode(), response.getSubMsg());
        }
    }

    /**
     * 创建扫码支付
     */
    private PaymentResult createQrCodePayment(PaymentContext context) throws AlipayApiException {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(context.getOrderId());
        model.setSubject(context.getProductName());
        model.setTotalAmount(formatAmount(context.getOrderAmountInFen()));
        model.setBody(context.getOrderDesc());
        model.setTimeoutExpress(getOrderTimeoutMinutes() + "m");
        
        request.setBizModel(model);
        request.setNotifyUrl(context.getNotifyUrl());
        
        AlipayTradePrecreateResponse response = alipayClient.execute(request);
        
        if (response.isSuccess()) {
            logger.info("支付宝扫码支付创建成功: {}", context.getOrderId());
            return buildPaymentResult(true, context.getOrderId(), response.getQrCode(), null, null);
        } else {
            logger.warn("支付宝扫码支付创建失败: {} -> {}", context.getOrderId(), response.getSubMsg());
            return buildPaymentResult(false, null, null, response.getCode(), response.getSubMsg());
        }
    }

    @Override
    public CompletableFuture<QueryResult> queryPayment(String orderId, String channelOrderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("查询支付宝订单状态: {}", orderId);
                
                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
                
                AlipayTradeQueryModel model = new AlipayTradeQueryModel();
                model.setOutTradeNo(orderId);
                if (channelOrderId != null) {
                    model.setTradeNo(channelOrderId);
                }
                
                request.setBizModel(model);
                
                AlipayTradeQueryResponse response = alipayClient.execute(request);
                
                if (response.isSuccess()) {
                    PaymentOrder.OrderStatus status = parseTradeStatus(response.getTradeStatus());
                    BigDecimal paidAmount = response.getTotalAmount() != null ? 
                            new BigDecimal(response.getTotalAmount()) : BigDecimal.ZERO;
                    LocalDateTime payTime = response.getSendPayDate() != null ? 
                            response.getSendPayDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null;
                    
                    logger.info("查询支付宝订单成功: {} -> {}", orderId, status);
                    return buildQueryResult(status, response.getTradeNo(), paidAmount, payTime);
                } else {
                    logger.warn("查询支付宝订单失败: {} -> {}", orderId, response.getSubMsg());
                    return buildQueryResult(PaymentOrder.OrderStatus.FAILED, null, BigDecimal.ZERO, null);
                }
                
            } catch (Exception e) {
                logger.error("查询支付宝订单异常: {}", orderId, e);
                return buildQueryResult(PaymentOrder.OrderStatus.FAILED, null, BigDecimal.ZERO, null);
            }
        });
    }

    @Override
    public CompletableFuture<RefundResult> refund(String orderId, String refundId, 
                                                BigDecimal refundAmount, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("申请支付宝退款: {} -> {}", orderId, refundAmount);
                
                AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
                
                AlipayTradeRefundModel model = new AlipayTradeRefundModel();
                model.setOutTradeNo(orderId);
                model.setRefundAmount(refundAmount.toString());
                model.setRefundReason(reason);
                model.setOutRequestNo(refundId);
                
                request.setBizModel(model);
                
                AlipayTradeRefundResponse response = alipayClient.execute(request);
                
                if (response.isSuccess()) {
                    BigDecimal actualRefundAmount = response.getRefundFee() != null ? 
                            new BigDecimal(response.getRefundFee()) : refundAmount;
                    
                    logger.info("支付宝退款成功: {} -> {}", orderId, actualRefundAmount);
                    return buildRefundResult(true, refundId, actualRefundAmount, null, null);
                } else {
                    logger.warn("支付宝退款失败: {} -> {}", orderId, response.getSubMsg());
                    return buildRefundResult(false, null, BigDecimal.ZERO, response.getCode(), response.getSubMsg());
                }
                
            } catch (Exception e) {
                logger.error("申请支付宝退款异常: {}", orderId, e);
                return buildRefundResult(false, null, BigDecimal.ZERO, "REFUND_ERROR", e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<RefundResult> queryRefund(String refundId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("查询支付宝退款状态: {}", refundId);
                
                AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
                
                AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
                model.setOutRequestNo(refundId);
                
                request.setBizModel(model);
                
                AlipayTradeFastpayRefundQueryResponse response = alipayClient.execute(request);
                
                if (response.isSuccess()) {
                    BigDecimal refundAmount = response.getRefundAmount() != null ? 
                            new BigDecimal(response.getRefundAmount()) : BigDecimal.ZERO;
                    
                    logger.info("查询支付宝退款成功: {} -> {}", refundId, refundAmount);
                    return buildRefundResult(true, refundId, refundAmount, null, null);
                } else {
                    logger.warn("查询支付宝退款失败: {} -> {}", refundId, response.getSubMsg());
                    return buildRefundResult(false, null, BigDecimal.ZERO, response.getCode(), response.getSubMsg());
                }
                
            } catch (Exception e) {
                logger.error("查询支付宝退款异常: {}", refundId, e);
                return buildRefundResult(false, null, BigDecimal.ZERO, "QUERY_ERROR", e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> closeOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("关闭支付宝订单: {}", orderId);
                
                AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
                
                AlipayTradeCloseModel model = new AlipayTradeCloseModel();
                model.setOutTradeNo(orderId);
                
                request.setBizModel(model);
                
                AlipayTradeCloseResponse response = alipayClient.execute(request);
                
                if (response.isSuccess()) {
                    logger.info("关闭支付宝订单成功: {}", orderId);
                    return true;
                } else {
                    logger.warn("关闭支付宝订单失败: {} -> {}", orderId, response.getSubMsg());
                    return false;
                }
                
            } catch (Exception e) {
                logger.error("关闭支付宝订单异常: {}", orderId, e);
                return false;
            }
        });
    }

    @Override
    public CallbackResult handleCallback(Map<String, Object> callbackData) {
        try {
            logger.info("处理支付宝回调通知");
            
            // 验证签名
            if (!verifyCallback(callbackData)) {
                logger.warn("支付宝回调签名验证失败");
                return buildCallbackResult(false, null, null, null, null, null);
            }
            
            String orderId = (String) callbackData.get("out_trade_no");
            String tradeStatus = (String) callbackData.get("trade_status");
            String totalAmount = (String) callbackData.get("total_amount");
            String gmtPayment = (String) callbackData.get("gmt_payment");
            
            PaymentOrder.OrderStatus status = parseTradeStatus(tradeStatus);
            BigDecimal amount = totalAmount != null ? new BigDecimal(totalAmount) : BigDecimal.ZERO;
            LocalDateTime payTime = parseDateTime(gmtPayment);
            
            logger.info("支付宝回调处理成功: {} -> {}", orderId, status);
            return buildCallbackResult(true, orderId, status, amount, payTime, callbackData);
            
        } catch (Exception e) {
            logger.error("处理支付宝回调异常", e);
            return buildCallbackResult(false, null, null, null, null, null);
        }
    }

    @Override
    public boolean verifyCallback(Map<String, Object> callbackData) {
        try {
            Map<String, String> params = new HashMap<>();
            for (Map.Entry<String, Object> entry : callbackData.entrySet()) {
                if (entry.getValue() != null) {
                    params.put(entry.getKey(), entry.getValue().toString());
                }
            }
            
            return AlipaySignature.rsaCheckV1(params, publicKey, "UTF-8", "RSA2");
            
        } catch (AlipayApiException e) {
            logger.error("支付宝回调签名验证异常", e);
            return false;
        }
    }

    @Override
    protected boolean doHealthCheck() {
        try {
            // 执行一个简单的查询来检查连接
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo("HEALTH_CHECK_" + System.currentTimeMillis());
            request.setBizModel(model);
            
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            
            // 即使查询不到订单，只要网络通信正常就认为健康
            channelStatus = ChannelStatus.NORMAL;
            return true;
            
        } catch (Exception e) {
            logger.error("支付宝健康检查失败", e);
            channelStatus = ChannelStatus.ERROR;
            return false;
        }
    }

    @Override
    protected void addChannelHeaders(Request.Builder builder) {
        builder.addHeader("X-Alipay-AppId", appId);
    }

    /**
     * 解析交易状态
     */
    private PaymentOrder.OrderStatus parseTradeStatus(String tradeStatus) {
        if (tradeStatus == null) {
            return PaymentOrder.OrderStatus.PENDING;
        }
        
        return switch (tradeStatus) {
            case "WAIT_BUYER_PAY" -> PaymentOrder.OrderStatus.PENDING;
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> PaymentOrder.OrderStatus.PAID;
            case "TRADE_CLOSED" -> PaymentOrder.OrderStatus.CLOSED;
            default -> PaymentOrder.OrderStatus.FAILED;
        };
    }

    /**
     * 解析日期时间
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", dateTimeStr, e);
            return null;
        }
    }

    /**
     * 脱敏应用ID
     */
    private String maskAppId(String appId) {
        if (appId == null || appId.length() <= 8) {
            return "****";
        }
        return appId.substring(0, 4) + "****" + appId.substring(appId.length() - 4);
    }
}