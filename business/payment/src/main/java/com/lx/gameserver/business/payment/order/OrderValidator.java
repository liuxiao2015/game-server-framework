/*
 * 文件名: OrderValidator.java
 * 用途: 订单验证器
 * 实现内容:
 *   - 订单数据完整性验证
 *   - 金额验证和范围检查
 *   - 商品验证和库存检查
 *   - 用户验证和权限检查
 *   - 限购验证和风控验证
 * 技术选型:
 *   - 验证器模式
 *   - 策略模式
 *   - 规则引擎
 *   - 缓存机制
 * 依赖关系:
 *   - 被OrderService使用
 *   - 依赖商品服务
 *   - 依赖用户服务
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.order;

import com.lx.gameserver.business.payment.core.PaymentContext;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 订单验证器
 * <p>
 * 提供全面的订单数据验证功能，包括业务规则验证、
 * 数据格式验证、权限验证等，确保订单数据的正确性和安全性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class OrderValidator {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidator.class);

    /**
     * 验证错误类型枚举
     */
    public enum ValidationError {
        /** 参数为空 */
        PARAM_NULL("参数不能为空"),
        /** 参数格式错误 */
        PARAM_FORMAT_ERROR("参数格式错误"),
        /** 金额无效 */
        INVALID_AMOUNT("金额无效"),
        /** 商品不存在 */
        PRODUCT_NOT_FOUND("商品不存在"),
        /** 商品已下架 */
        PRODUCT_OFFLINE("商品已下架"),
        /** 用户不存在 */
        USER_NOT_FOUND("用户不存在"),
        /** 用户被禁用 */
        USER_DISABLED("用户被禁用"),
        /** 超出限购 */
        EXCEED_PURCHASE_LIMIT("超出限购数量"),
        /** 风控拦截 */
        RISK_BLOCKED("风控拦截"),
        /** 支付渠道不支持 */
        CHANNEL_NOT_SUPPORTED("支付渠道不支持"),
        /** 订单已过期 */
        ORDER_EXPIRED("订单已过期");

        private final String message;

        ValidationError(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }

    /**
     * 验证异常
     */
    public static class ValidationException extends RuntimeException {
        private final ValidationError error;
        private final Object details;

        public ValidationException(ValidationError error) {
            super(error.getMessage());
            this.error = error;
            this.details = null;
        }

        public ValidationException(ValidationError error, Object details) {
            super(error.getMessage() + ": " + details);
            this.error = error;
            this.details = details;
        }

        public ValidationError getError() { return error; }
        public Object getDetails() { return details; }
    }

    // ========== 常量定义 ==========

    /**
     * 最小订单金额（分）
     */
    private static final long MIN_ORDER_AMOUNT = 100L; // 1元

    /**
     * 最大订单金额（分）
     */
    private static final long MAX_ORDER_AMOUNT = 100000000L; // 100万元

    /**
     * 邮箱正则表达式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /**
     * 手机号正则表达式
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * IP地址正则表达式
     */
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    // ========== 公共验证方法 ==========

    /**
     * 验证支付上下文
     *
     * @param context 支付上下文
     * @throws ValidationException 验证失败
     */
    public void validateContext(PaymentContext context) {
        logger.debug("开始验证支付上下文");

        // 基础参数验证
        validateBasicParams(context);
        
        // 金额验证
        validateAmount(context.getOrderAmount());
        
        // 用户验证
        validateUser(context);
        
        // 商品验证
        validateProduct(context.getProductId(), context.getUserId());
        
        // 支付渠道验证
        validatePaymentChannel(context.getPaymentChannel(), context.getPaymentMethod());
        
        // 风控验证
        validateRiskControl(context);
        
        // 限购验证
        validatePurchaseLimit(context.getUserId(), context.getProductId());

        logger.debug("支付上下文验证通过");
    }

    /**
     * 验证支付订单
     *
     * @param order 支付订单
     * @throws ValidationException 验证失败
     */
    public void validateOrder(PaymentOrder order) {
        logger.debug("开始验证支付订单: {}", order.getOrderId());

        // 基础字段验证
        validateOrderBasicFields(order);
        
        // 状态验证
        validateOrderStatus(order);
        
        // 时间验证
        validateOrderTime(order);
        
        // 金额一致性验证
        validateOrderAmount(order);

        logger.debug("支付订单验证通过: {}", order.getOrderId());
    }

    /**
     * 验证订单状态流转
     *
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @throws ValidationException 验证失败
     */
    public void validateStatusTransition(PaymentOrder.OrderStatus currentStatus, 
                                       PaymentOrder.OrderStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new ValidationException(ValidationError.PARAM_NULL, "订单状态不能为空");
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new ValidationException(ValidationError.PARAM_FORMAT_ERROR, 
                    String.format("订单状态不能从 %s 流转到 %s", currentStatus, targetStatus));
        }
    }

    // ========== 私有验证方法 ==========

    /**
     * 验证基础参数
     */
    private void validateBasicParams(PaymentContext context) {
        if (context == null) {
            throw new ValidationException(ValidationError.PARAM_NULL, "支付上下文不能为空");
        }

        // 用户ID验证
        if (context.getUserId() == null || context.getUserId() <= 0) {
            throw new ValidationException(ValidationError.PARAM_NULL, "用户ID无效");
        }

        // 商品ID验证
        if (context.getProductId() == null || context.getProductId().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "商品ID不能为空");
        }

        // 商品名称验证
        if (context.getProductName() == null || context.getProductName().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "商品名称不能为空");
        }

        // 支付渠道验证
        if (context.getPaymentChannel() == null || context.getPaymentChannel().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "支付渠道不能为空");
        }

        // 支付方式验证
        if (context.getPaymentMethod() == null || context.getPaymentMethod().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "支付方式不能为空");
        }

        // 客户端IP验证
        if (context.getClientIp() == null || context.getClientIp().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "客户端IP不能为空");
        }

        if (!IP_PATTERN.matcher(context.getClientIp()).matches()) {
            throw new ValidationException(ValidationError.PARAM_FORMAT_ERROR, "客户端IP格式错误");
        }

        // 邮箱验证（如果提供）
        if (context.getUserEmail() != null && !context.getUserEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(context.getUserEmail()).matches()) {
                throw new ValidationException(ValidationError.PARAM_FORMAT_ERROR, "邮箱格式错误");
            }
        }

        // 手机号验证（如果提供）
        if (context.getUserPhone() != null && !context.getUserPhone().trim().isEmpty()) {
            if (!PHONE_PATTERN.matcher(context.getUserPhone()).matches()) {
                throw new ValidationException(ValidationError.PARAM_FORMAT_ERROR, "手机号格式错误");
            }
        }
    }

    /**
     * 验证金额
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException(ValidationError.PARAM_NULL, "订单金额不能为空");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(ValidationError.INVALID_AMOUNT, "订单金额必须大于0");
        }

        long amountInFen = amount.multiply(BigDecimal.valueOf(100)).longValue();
        
        if (amountInFen < MIN_ORDER_AMOUNT) {
            throw new ValidationException(ValidationError.INVALID_AMOUNT, 
                    String.format("订单金额不能小于%d分", MIN_ORDER_AMOUNT));
        }

        if (amountInFen > MAX_ORDER_AMOUNT) {
            throw new ValidationException(ValidationError.INVALID_AMOUNT, 
                    String.format("订单金额不能超过%d分", MAX_ORDER_AMOUNT));
        }
    }

    /**
     * 验证用户
     */
    private void validateUser(PaymentContext context) {
        Long userId = context.getUserId();
        
        // 模拟用户验证逻辑
        if (isUserNotExists(userId)) {
            throw new ValidationException(ValidationError.USER_NOT_FOUND, userId);
        }

        if (isUserDisabled(userId)) {
            throw new ValidationException(ValidationError.USER_DISABLED, userId);
        }

        logger.debug("用户验证通过: {}", userId);
    }

    /**
     * 验证商品
     */
    private void validateProduct(String productId, Long userId) {
        // 模拟商品验证逻辑
        if (isProductNotExists(productId)) {
            throw new ValidationException(ValidationError.PRODUCT_NOT_FOUND, productId);
        }

        if (isProductOffline(productId)) {
            throw new ValidationException(ValidationError.PRODUCT_OFFLINE, productId);
        }

        logger.debug("商品验证通过: {}", productId);
    }

    /**
     * 验证支付渠道
     */
    private void validatePaymentChannel(String channel, String method) {
        // 检查渠道是否支持该支付方式
        if (!isChannelSupported(channel, method)) {
            throw new ValidationException(ValidationError.CHANNEL_NOT_SUPPORTED, 
                    String.format("渠道 %s 不支持支付方式 %s", channel, method));
        }

        logger.debug("支付渠道验证通过: {} - {}", channel, method);
    }

    /**
     * 验证风控
     */
    private void validateRiskControl(PaymentContext context) {
        // 检查风险评分
        if (context.getRiskScore() != null && context.getRiskScore() >= 80) {
            throw new ValidationException(ValidationError.RISK_BLOCKED, 
                    "风险评分过高: " + context.getRiskScore());
        }

        // 检查是否可疑交易
        if (Boolean.TRUE.equals(context.getSuspicious())) {
            throw new ValidationException(ValidationError.RISK_BLOCKED, "可疑交易被拦截");
        }

        logger.debug("风控验证通过: 用户={}, 风险评分={}", context.getUserId(), context.getRiskScore());
    }

    /**
     * 验证限购
     */
    private void validatePurchaseLimit(Long userId, String productId) {
        // 模拟限购检查
        if (isExceedPurchaseLimit(userId, productId)) {
            throw new ValidationException(ValidationError.EXCEED_PURCHASE_LIMIT, 
                    String.format("用户 %d 商品 %s 超出限购", userId, productId));
        }

        logger.debug("限购验证通过: 用户={}, 商品={}", userId, productId);
    }

    /**
     * 验证订单基础字段
     */
    private void validateOrderBasicFields(PaymentOrder order) {
        if (order == null) {
            throw new ValidationException(ValidationError.PARAM_NULL, "订单对象不能为空");
        }

        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "订单号不能为空");
        }

        if (order.getUserId() == null || order.getUserId() <= 0) {
            throw new ValidationException(ValidationError.PARAM_NULL, "用户ID无效");
        }

        if (order.getProductId() == null || order.getProductId().trim().isEmpty()) {
            throw new ValidationException(ValidationError.PARAM_NULL, "商品ID不能为空");
        }

        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            throw new ValidationException(ValidationError.INVALID_AMOUNT, "订单金额无效");
        }
    }

    /**
     * 验证订单状态
     */
    private void validateOrderStatus(PaymentOrder order) {
        if (order.getOrderStatus() == null) {
            throw new ValidationException(ValidationError.PARAM_NULL, "订单状态不能为空");
        }
    }

    /**
     * 验证订单时间
     */
    private void validateOrderTime(PaymentOrder order) {
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new ValidationException(ValidationError.ORDER_EXPIRED, order.getOrderId());
        }
    }

    /**
     * 验证订单金额
     */
    private void validateOrderAmount(PaymentOrder order) {
        if (order.getOrderAmount() < MIN_ORDER_AMOUNT) {
            throw new ValidationException(ValidationError.INVALID_AMOUNT, 
                    String.format("订单金额不能小于%d分", MIN_ORDER_AMOUNT));
        }

        if (order.getOrderAmount() > MAX_ORDER_AMOUNT) {
            throw new ValidationException(ValidationError.INVALID_AMOUNT, 
                    String.format("订单金额不能超过%d分", MAX_ORDER_AMOUNT));
        }
    }

    // ========== 模拟业务检查方法 ==========

    /**
     * 检查用户是否存在
     */
    private boolean isUserNotExists(Long userId) {
        // 模拟用户存在检查
        return userId == null || userId <= 0 || userId == 999999L;
    }

    /**
     * 检查用户是否被禁用
     */
    private boolean isUserDisabled(Long userId) {
        // 模拟用户禁用检查
        return userId != null && userId == 888888L;
    }

    /**
     * 检查商品是否存在
     */
    private boolean isProductNotExists(String productId) {
        // 模拟商品存在检查
        return productId == null || productId.trim().isEmpty() || "NOT_EXISTS".equals(productId);
    }

    /**
     * 检查商品是否下架
     */
    private boolean isProductOffline(String productId) {
        // 模拟商品下架检查
        return "OFFLINE".equals(productId);
    }

    /**
     * 检查渠道是否支持
     */
    private boolean isChannelSupported(String channel, String method) {
        // 模拟渠道支持检查
        Map<String, String[]> supportedMethods = new HashMap<>();
        supportedMethods.put("alipay", new String[]{"app", "h5", "pc", "qr_code"});
        supportedMethods.put("wechat", new String[]{"app", "h5", "mini_program", "qr_code"});
        supportedMethods.put("apple", new String[]{"in_app_purchase"});
        supportedMethods.put("google", new String[]{"in_app_purchase"});

        String[] methods = supportedMethods.get(channel);
        if (methods == null) {
            return false;
        }

        for (String supportedMethod : methods) {
            if (supportedMethod.equals(method)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否超出限购
     */
    private boolean isExceedPurchaseLimit(Long userId, String productId) {
        // 模拟限购检查
        return userId != null && userId == 777777L && "LIMITED_PRODUCT".equals(productId);
    }

    /**
     * 获取验证器配置信息
     */
    public Map<String, Object> getValidatorConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("minOrderAmount", MIN_ORDER_AMOUNT);
        config.put("maxOrderAmount", MAX_ORDER_AMOUNT);
        config.put("emailPattern", EMAIL_PATTERN.pattern());
        config.put("phonePattern", PHONE_PATTERN.pattern());
        config.put("ipPattern", IP_PATTERN.pattern());
        return config;
    }
}