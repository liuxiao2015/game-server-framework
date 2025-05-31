/*
 * 文件名: PaymentContext.java
 * 用途: 支付上下文类
 * 实现内容:
 *   - 支付请求参数封装和管理
 *   - 支付环境信息收集
 *   - 用户设备信息记录
 *   - 风控数据收集和存储
 *   - 链路追踪信息管理
 * 技术选型:
 *   - 建造者模式
 *   - 数据校验注解
 *   - JSON序列化支持
 *   - 链路追踪集成
 * 依赖关系:
 *   - 被支付渠道使用
 *   - 被支付处理器传递
 *   - 集成链路追踪组件
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付上下文
 * <p>
 * 封装支付请求的完整上下文信息，包括订单信息、用户信息、
 * 设备信息、环境信息、风控数据等，为支付处理提供完整的数据支持。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@Builder
public class PaymentContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        /** Android */
        ANDROID("android", "Android"),
        /** iOS */
        IOS("ios", "iOS"),
        /** Web */
        WEB("web", "Web"),
        /** H5 */
        H5("h5", "H5"),
        /** 小程序 */
        MINI_PROGRAM("mini", "小程序"),
        /** PC */
        PC("pc", "PC"),
        /** 其他 */
        OTHER("other", "其他");

        private final String code;
        private final String name;

        DeviceType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    // ========== 订单基本信息 ==========

    /**
     * 订单号
     */
    private String orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private String productId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 订单金额（元）
     */
    private BigDecimal orderAmount;

    /**
     * 货币类型
     */
    @Builder.Default
    private String currency = "CNY";

    /**
     * 订单标题
     */
    private String orderTitle;

    /**
     * 订单描述
     */
    private String orderDesc;

    // ========== 支付信息 ==========

    /**
     * 支付渠道
     */
    private String paymentChannel;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 回调URL
     */
    private String notifyUrl;

    /**
     * 返回URL
     */
    private String returnUrl;

    /**
     * 订单过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    // ========== 用户信息 ==========

    /**
     * 用户openId（微信等平台）
     */
    private String openId;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户邮箱
     */
    private String userEmail;

    /**
     * 用户手机号
     */
    private String userPhone;

    // ========== 设备信息 ==========

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 设备类型
     */
    private DeviceType deviceType;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备型号
     */
    private String deviceModel;

    /**
     * 操作系统
     */
    private String osVersion;

    /**
     * 应用版本
     */
    private String appVersion;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 屏幕分辨率
     */
    private String screenResolution;

    /**
     * 网络类型
     */
    private String networkType;

    /**
     * 运营商
     */
    private String carrier;

    // ========== 地理位置信息 ==========

    /**
     * 国家码
     */
    private String countryCode;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    // ========== 风控信息 ==========

    /**
     * 风险评分
     */
    private Integer riskScore;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 是否可疑交易
     */
    @Builder.Default
    private Boolean suspicious = false;

    /**
     * 反欺诈结果
     */
    private String antiFraudResult;

    /**
     * 设备指纹
     */
    private String deviceFingerprint;

    // ========== 业务扩展信息 ==========

    /**
     * 业务场景
     */
    private String businessScene;

    /**
     * 推广渠道
     */
    private String promotionChannel;

    /**
     * 优惠券ID
     */
    private String couponId;

    /**
     * 优惠金额（元）
     */
    private BigDecimal discountAmount;

    /**
     * 业务扩展数据
     */
    @Builder.Default
    private Map<String, Object> businessData = new HashMap<>();

    // ========== 技术信息 ==========

    /**
     * 请求时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime requestTime = LocalDateTime.now();

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 请求来源
     */
    private String requestSource;

    /**
     * API版本
     */
    private String apiVersion;

    /**
     * 渠道扩展数据
     */
    @Builder.Default
    private Map<String, Object> channelData = new HashMap<>();

    /**
     * 内部扩展数据
     */
    @Builder.Default
    @JsonIgnore
    private Map<String, Object> internalData = new HashMap<>();

    // ========== 业务方法 ==========

    /**
     * 获取订单金额（分）
     */
    public Long getOrderAmountInFen() {
        return orderAmount != null ? orderAmount.multiply(BigDecimal.valueOf(100)).longValue() : null;
    }

    /**
     * 获取优惠金额（分）
     */
    public Long getDiscountAmountInFen() {
        return discountAmount != null ? discountAmount.multiply(BigDecimal.valueOf(100)).longValue() : null;
    }

    /**
     * 获取实际支付金额（元）
     */
    public BigDecimal getActualPayAmount() {
        BigDecimal actual = orderAmount;
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            actual = actual.subtract(discountAmount);
        }
        return actual.max(BigDecimal.ZERO);
    }

    /**
     * 获取实际支付金额（分）
     */
    public Long getActualPayAmountInFen() {
        return getActualPayAmount().multiply(BigDecimal.valueOf(100)).longValue();
    }

    /**
     * 设置业务扩展数据
     */
    public PaymentContext putBusinessData(String key, Object value) {
        if (businessData == null) {
            businessData = new HashMap<>();
        }
        businessData.put(key, value);
        return this;
    }

    /**
     * 获取业务扩展数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getBusinessData(String key, Class<T> type) {
        if (businessData == null) {
            return null;
        }
        Object value = businessData.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * 设置渠道扩展数据
     */
    public PaymentContext putChannelData(String key, Object value) {
        if (channelData == null) {
            channelData = new HashMap<>();
        }
        channelData.put(key, value);
        return this;
    }

    /**
     * 获取渠道扩展数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getChannelData(String key, Class<T> type) {
        if (channelData == null) {
            return null;
        }
        Object value = channelData.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * 设置内部扩展数据
     */
    public PaymentContext putInternalData(String key, Object value) {
        if (internalData == null) {
            internalData = new HashMap<>();
        }
        internalData.put(key, value);
        return this;
    }

    /**
     * 获取内部扩展数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getInternalData(String key, Class<T> type) {
        if (internalData == null) {
            return null;
        }
        Object value = internalData.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * 获取设备类型名称
     */
    public String getDeviceTypeName() {
        return deviceType != null ? deviceType.getName() : "";
    }

    /**
     * 是否为移动端
     */
    public boolean isMobileDevice() {
        return deviceType == DeviceType.ANDROID || deviceType == DeviceType.IOS || deviceType == DeviceType.H5;
    }

    /**
     * 是否为高风险交易
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 80;
    }

    /**
     * 是否需要人工审核
     */
    public boolean needManualReview() {
        return suspicious || isHighRisk() || (orderAmount != null && orderAmount.compareTo(BigDecimal.valueOf(5000)) > 0);
    }

    /**
     * 校验上下文数据
     */
    public boolean isValid() {
        return orderId != null && !orderId.trim().isEmpty() &&
               userId != null && userId > 0 &&
               productId != null && !productId.trim().isEmpty() &&
               orderAmount != null && orderAmount.compareTo(BigDecimal.ZERO) > 0 &&
               paymentChannel != null && !paymentChannel.trim().isEmpty() &&
               paymentMethod != null && !paymentMethod.trim().isEmpty() &&
               clientIp != null && !clientIp.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "PaymentContext{" +
                "orderId='" + orderId + '\'' +
                ", userId=" + userId +
                ", productId='" + productId + '\'' +
                ", orderAmount=" + orderAmount +
                ", paymentChannel='" + paymentChannel + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", deviceType=" + deviceType +
                ", requestTime=" + requestTime +
                '}';
    }
}