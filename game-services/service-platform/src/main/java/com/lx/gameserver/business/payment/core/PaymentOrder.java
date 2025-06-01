/*
 * 文件名: PaymentOrder.java
 * 用途: 支付订单实体类
 * 实现内容:
 *   - 支付订单核心数据模型
 *   - 订单状态机设计和流转
 *   - 订单号生成策略和防重机制
 *   - 订单超时管理和锁机制
 *   - 订单数据加密存储支持
 * 技术选型:
 *   - MyBatis Plus注解映射
 *   - 状态机模式
 *   - 加密存储字段
 *   - 乐观锁机制
 * 依赖关系:
 *   - 被订单服务管理
 *   - 被支付渠道使用
 *   - 依赖加密工具
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.core;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付订单实体
 * <p>
 * 支付订单的核心数据模型，包含订单的完整生命周期信息，
 * 支持订单状态流转、加密存储、并发控制等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("payment_order")
public class PaymentOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单状态枚举
     */
    public enum OrderStatus {
        /** 待支付 */
        PENDING("PENDING", "待支付"),
        /** 支付中 */
        PAYING("PAYING", "支付中"),
        /** 已支付 */
        PAID("PAID", "已支付"),
        /** 已退款 */
        REFUNDED("REFUNDED", "已退款"),
        /** 部分退款 */
        PARTIAL_REFUNDED("PARTIAL_REFUNDED", "部分退款"),
        /** 已关闭 */
        CLOSED("CLOSED", "已关闭"),
        /** 已超时 */
        TIMEOUT("TIMEOUT", "已超时"),
        /** 支付失败 */
        FAILED("FAILED", "支付失败");

        private final String code;
        private final String name;

        OrderStatus(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        /**
         * 检查状态流转是否合法
         */
        public boolean canTransitionTo(OrderStatus target) {
            return switch (this) {
                case PENDING -> target == PAYING || target == CLOSED || target == TIMEOUT || target == FAILED;
                case PAYING -> target == PAID || target == FAILED || target == TIMEOUT;
                case PAID -> target == REFUNDED || target == PARTIAL_REFUNDED;
                case PARTIAL_REFUNDED -> target == REFUNDED;
                case REFUNDED, CLOSED, TIMEOUT, FAILED -> false; // 终态不能再转换
            };
        }
    }

    /**
     * 订单类型枚举
     */
    public enum OrderType {
        /** 商品购买 */
        PRODUCT("PRODUCT", "商品购买"),
        /** 充值 */
        RECHARGE("RECHARGE", "充值"),
        /** 服务费 */
        SERVICE("SERVICE", "服务费"),
        /** 其他 */
        OTHER("OTHER", "其他");

        private final String code;
        private final String name;

        OrderType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 订单号（业务主键）
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 商品ID
     */
    @TableField("product_id")
    private String productId;

    /**
     * 商品名称
     */
    @TableField("product_name")
    private String productName;

    /**
     * 订单类型
     */
    @TableField("order_type")
    private OrderType orderType;

    /**
     * 订单金额（分）
     */
    @TableField("order_amount")
    private Long orderAmount;

    /**
     * 实际支付金额（分）
     */
    @TableField("paid_amount")
    private Long paidAmount;

    /**
     * 货币类型
     */
    @TableField("currency")
    private String currency;

    /**
     * 支付渠道
     */
    @TableField("payment_channel")
    private String paymentChannel;

    /**
     * 支付方式
     */
    @TableField("payment_method")
    private String paymentMethod;

    /**
     * 渠道订单号
     */
    @TableField("channel_order_id")
    private String channelOrderId;

    /**
     * 订单状态
     */
    @TableField("order_status")
    private OrderStatus orderStatus;

    /**
     * 订单标题
     */
    @TableField("order_title")
    private String orderTitle;

    /**
     * 订单描述
     */
    @TableField("order_desc")
    private String orderDesc;

    /**
     * 回调URL
     */
    @TableField("notify_url")
    private String notifyUrl;

    /**
     * 返回URL
     */
    @TableField("return_url")
    private String returnUrl;

    /**
     * 客户端IP
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * 用户设备信息（加密存储）
     */
    @TableField("device_info")
    private String deviceInfo;

    /**
     * 订单过期时间
     */
    @TableField("expire_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /**
     * 支付完成时间
     */
    @TableField("pay_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    /**
     * 业务扩展信息（JSON格式）
     */
    @TableField("business_data")
    private String businessData;

    /**
     * 渠道扩展信息（JSON格式）
     */
    @TableField("channel_data")
    private String channelData;

    /**
     * 风控信息（JSON格式）
     */
    @TableField("risk_data")
    private String riskData;

    /**
     * 备注信息
     */
    @TableField("remark")
    private String remark;

    /**
     * 版本号（乐观锁）
     */
    @Version
    @TableField("version")
    private Integer version;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 逻辑删除标识
     */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

    // ========== 业务方法 ==========

    /**
     * 检查订单是否已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查订单是否可以支付
     */
    public boolean canPay() {
        return orderStatus == OrderStatus.PENDING && !isExpired();
    }

    /**
     * 检查订单是否可以退款
     */
    public boolean canRefund() {
        return orderStatus == OrderStatus.PAID || orderStatus == OrderStatus.PARTIAL_REFUNDED;
    }

    /**
     * 检查订单是否可以关闭
     */
    public boolean canClose() {
        return orderStatus == OrderStatus.PENDING || orderStatus == OrderStatus.PAYING;
    }

    /**
     * 获取订单金额（元）
     */
    public BigDecimal getOrderAmountInYuan() {
        return orderAmount != null ? BigDecimal.valueOf(orderAmount).divide(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
    }

    /**
     * 获取实际支付金额（元）
     */
    public BigDecimal getPaidAmountInYuan() {
        return paidAmount != null ? BigDecimal.valueOf(paidAmount).divide(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
    }

    /**
     * 设置订单金额（元）
     */
    public void setOrderAmountInYuan(BigDecimal amount) {
        this.orderAmount = amount != null ? amount.multiply(BigDecimal.valueOf(100)).longValue() : null;
    }

    /**
     * 设置实际支付金额（元）
     */
    public void setPaidAmountInYuan(BigDecimal amount) {
        this.paidAmount = amount != null ? amount.multiply(BigDecimal.valueOf(100)).longValue() : null;
    }

    /**
     * 获取剩余有效时间（分钟）
     */
    public long getRemainingMinutes() {
        if (expireTime == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expireTime)) {
            return 0;
        }
        return java.time.Duration.between(now, expireTime).toMinutes();
    }

    /**
     * 检查状态流转是否合法
     */
    public boolean canTransitionTo(OrderStatus targetStatus) {
        return orderStatus.canTransitionTo(targetStatus);
    }

    /**
     * 是否为终态
     */
    public boolean isFinalStatus() {
        return orderStatus == OrderStatus.PAID || 
               orderStatus == OrderStatus.REFUNDED || 
               orderStatus == OrderStatus.CLOSED || 
               orderStatus == OrderStatus.TIMEOUT || 
               orderStatus == OrderStatus.FAILED;
    }

    /**
     * 获取订单状态显示名称
     */
    public String getStatusName() {
        return orderStatus != null ? orderStatus.getName() : "";
    }

    /**
     * 获取订单类型显示名称
     */
    public String getTypeName() {
        return orderType != null ? orderType.getName() : "";
    }

    @Override
    public String toString() {
        return "PaymentOrder{" +
                "orderId='" + orderId + '\'' +
                ", userId=" + userId +
                ", productId='" + productId + '\'' +
                ", orderAmount=" + orderAmount +
                ", orderStatus=" + orderStatus +
                ", paymentChannel='" + paymentChannel + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}