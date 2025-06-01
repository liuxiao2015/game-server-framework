/*
 * 文件名: PaymentTransaction.java
 * 用途: 支付事务管理实体
 * 实现内容:
 *   - 支付事务核心数据模型
 *   - 分布式事务支持和管理
 *   - 事务补偿机制实现
 *   - 幂等性保证和重复检查
 *   - 事务日志记录和回滚处理
 * 技术选型:
 *   - MyBatis Plus注解映射
 *   - 分布式事务模式
 *   - 补偿事务机制
 *   - 幂等性设计
 * 依赖关系:
 *   - 与PaymentOrder关联
 *   - 被事务管理器使用
 *   - 支持事务日志记录
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.core;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付事务实体
 * <p>
 * 管理支付过程中的事务状态，支持分布式事务、补偿机制、
 * 幂等性保证等功能，确保支付数据的一致性和可靠性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("payment_transaction")
public class PaymentTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事务状态枚举
     */
    public enum TransactionStatus {
        /** 初始化 */
        INIT("INIT", "初始化"),
        /** 进行中 */
        PROCESSING("PROCESSING", "进行中"),
        /** 已提交 */
        COMMITTED("COMMITTED", "已提交"),
        /** 已回滚 */
        ROLLBACK("ROLLBACK", "已回滚"),
        /** 补偿中 */
        COMPENSATING("COMPENSATING", "补偿中"),
        /** 补偿成功 */
        COMPENSATED("COMPENSATED", "补偿成功"),
        /** 补偿失败 */
        COMPENSATION_FAILED("COMPENSATION_FAILED", "补偿失败"),
        /** 超时 */
        TIMEOUT("TIMEOUT", "超时"),
        /** 失败 */
        FAILED("FAILED", "失败");

        private final String code;
        private final String name;

        TransactionStatus(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        /**
         * 检查状态流转是否合法
         */
        public boolean canTransitionTo(TransactionStatus target) {
            return switch (this) {
                case INIT -> target == PROCESSING || target == FAILED;
                case PROCESSING -> target == COMMITTED || target == ROLLBACK || target == TIMEOUT || target == FAILED;
                case COMMITTED -> target == COMPENSATING;
                case ROLLBACK -> false; // 回滚是终态
                case COMPENSATING -> target == COMPENSATED || target == COMPENSATION_FAILED;
                case COMPENSATED, COMPENSATION_FAILED, TIMEOUT, FAILED -> false; // 终态
            };
        }

        /**
         * 是否为终态
         */
        public boolean isFinalStatus() {
            return this == COMMITTED || this == ROLLBACK || this == COMPENSATED || 
                   this == COMPENSATION_FAILED || this == TIMEOUT || this == FAILED;
        }
    }

    /**
     * 事务类型枚举
     */
    public enum TransactionType {
        /** 支付事务 */
        PAYMENT("PAYMENT", "支付事务"),
        /** 退款事务 */
        REFUND("REFUND", "退款事务"),
        /** 查询事务 */
        QUERY("QUERY", "查询事务"),
        /** 关闭事务 */
        CLOSE("CLOSE", "关闭事务"),
        /** 补偿事务 */
        COMPENSATION("COMPENSATION", "补偿事务");

        private final String code;
        private final String name;

        TransactionType(String code, String name) {
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
     * 事务ID（业务主键）
     */
    @TableField("transaction_id")
    private String transactionId;

    /**
     * 关联订单号
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 事务类型
     */
    @TableField("transaction_type")
    private TransactionType transactionType;

    /**
     * 事务状态
     */
    @TableField("transaction_status")
    private TransactionStatus transactionStatus;

    /**
     * 支付渠道
     */
    @TableField("payment_channel")
    private String paymentChannel;

    /**
     * 渠道事务ID
     */
    @TableField("channel_transaction_id")
    private String channelTransactionId;

    /**
     * 幂等性键
     */
    @TableField("idempotent_key")
    private String idempotentKey;

    /**
     * 父事务ID
     */
    @TableField("parent_transaction_id")
    private String parentTransactionId;

    /**
     * 事务开始时间
     */
    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 事务结束时间
     */
    @TableField("end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 事务超时时间
     */
    @TableField("timeout_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timeoutTime;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    @TableField("max_retry_count")
    private Integer maxRetryCount;

    /**
     * 错误码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 事务上下文（JSON格式）
     */
    @TableField("context_data")
    private String contextData;

    /**
     * 请求参数（JSON格式）
     */
    @TableField("request_data")
    private String requestData;

    /**
     * 响应结果（JSON格式）
     */
    @TableField("response_data")
    private String responseData;

    /**
     * 补偿参数（JSON格式）
     */
    @TableField("compensation_data")
    private String compensationData;

    /**
     * 事务日志（JSON格式）
     */
    @TableField("transaction_log")
    private String transactionLog;

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
     * 检查事务是否已超时
     */
    public boolean isTimeout() {
        return timeoutTime != null && LocalDateTime.now().isAfter(timeoutTime);
    }

    /**
     * 检查是否需要重试
     */
    public boolean canRetry() {
        return retryCount != null && maxRetryCount != null && retryCount < maxRetryCount;
    }

    /**
     * 检查事务是否可以提交
     */
    public boolean canCommit() {
        return transactionStatus == TransactionStatus.PROCESSING;
    }

    /**
     * 检查事务是否可以回滚
     */
    public boolean canRollback() {
        return transactionStatus == TransactionStatus.PROCESSING;
    }

    /**
     * 检查事务是否可以补偿
     */
    public boolean canCompensate() {
        return transactionStatus == TransactionStatus.COMMITTED;
    }

    /**
     * 检查状态流转是否合法
     */
    public boolean canTransitionTo(TransactionStatus targetStatus) {
        return transactionStatus.canTransitionTo(targetStatus);
    }

    /**
     * 是否为终态
     */
    public boolean isFinalStatus() {
        return transactionStatus.isFinalStatus();
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    /**
     * 获取事务执行时长（毫秒）
     */
    public long getDurationMillis() {
        if (startTime == null) {
            return 0;
        }
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }

    /**
     * 获取剩余超时时间（毫秒）
     */
    public long getRemainingTimeoutMillis() {
        if (timeoutTime == null) {
            return Long.MAX_VALUE;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(timeoutTime)) {
            return 0;
        }
        return java.time.Duration.between(now, timeoutTime).toMillis();
    }

    /**
     * 设置事务超时
     */
    public void setTimeout(int timeoutSeconds) {
        this.timeoutTime = LocalDateTime.now().plusSeconds(timeoutSeconds);
    }

    /**
     * 开始事务
     */
    public void start() {
        this.startTime = LocalDateTime.now();
        this.transactionStatus = TransactionStatus.PROCESSING;
    }

    /**
     * 提交事务
     */
    public void commit() {
        this.endTime = LocalDateTime.now();
        this.transactionStatus = TransactionStatus.COMMITTED;
    }

    /**
     * 回滚事务
     */
    public void rollback(String errorCode, String errorMessage) {
        this.endTime = LocalDateTime.now();
        this.transactionStatus = TransactionStatus.ROLLBACK;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 事务失败
     */
    public void fail(String errorCode, String errorMessage) {
        this.endTime = LocalDateTime.now();
        this.transactionStatus = TransactionStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 获取事务状态显示名称
     */
    public String getStatusName() {
        return transactionStatus != null ? transactionStatus.getName() : "";
    }

    /**
     * 获取事务类型显示名称
     */
    public String getTypeName() {
        return transactionType != null ? transactionType.getName() : "";
    }

    @Override
    public String toString() {
        return "PaymentTransaction{" +
                "transactionId='" + transactionId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", transactionType=" + transactionType +
                ", transactionStatus=" + transactionStatus +
                ", paymentChannel='" + paymentChannel + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}