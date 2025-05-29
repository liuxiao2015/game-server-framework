/*
 * 文件名: AuditLog.java
 * 用途: 审计日志实体类
 * 实现内容:
 *   - 记录数据变更操作的详细信息
 *   - 包含表名、操作类型、实体ID、变更前后数据
 *   - 记录操作人信息、操作时间、客户端IP
 *   - 支持按时间、操作人、表名查询
 * 技术选型:
 *   - MyBatis Plus实体注解
 *   - JSON序列化存储变更数据
 *   - 枚举定义操作类型
 * 依赖关系:
 *   - 继承BaseEntity
 *   - 被DataAuditInterceptor使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.audit;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lx.gameserver.frame.db.base.BaseEntity;

/**
 * 审计日志实体
 * <p>
 * 记录所有数据变更操作的详细信息，包括操作类型、变更前后数据、
 * 操作人、操作时间等，用于数据审计和问题追踪。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@TableName("t_audit_log")
public class AuditLog extends BaseEntity {

    /**
     * 表名
     */
    @TableField("table_name")
    private String tableName;

    /**
     * 操作类型
     */
    @TableField("operation_type")
    private OperationType operationType;

    /**
     * 实体ID
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * 变更前数据（JSON格式）
     */
    @TableField("old_data")
    private String oldData;

    /**
     * 变更后数据（JSON格式）
     */
    @TableField("new_data")
    private String newData;

    /**
     * 客户端IP地址
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * 用户代理信息
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 请求URI
     */
    @TableField("request_uri")
    private String requestUri;

    /**
     * 操作耗时（毫秒）
     */
    @TableField("duration")
    private Long duration;

    /**
     * 是否为敏感操作
     */
    @TableField("is_sensitive")
    private Boolean isSensitive;

    /**
     * 操作描述
     */
    @TableField("operation_desc")
    private String operationDesc;

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        /**
         * 插入操作
         */
        INSERT("INSERT", "插入"),

        /**
         * 更新操作
         */
        UPDATE("UPDATE", "更新"),

        /**
         * 删除操作
         */
        DELETE("DELETE", "删除"),

        /**
         * 查询操作
         */
        SELECT("SELECT", "查询"),

        /**
         * 批量操作
         */
        BATCH("BATCH", "批量操作");

        private final String code;
        private final String description;

        OperationType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    // Getters and Setters

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getOldData() {
        return oldData;
    }

    public void setOldData(String oldData) {
        this.oldData = oldData;
    }

    public String getNewData() {
        return newData;
    }

    public void setNewData(String newData) {
        this.newData = newData;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Boolean getIsSensitive() {
        return isSensitive;
    }

    public void setIsSensitive(Boolean isSensitive) {
        this.isSensitive = isSensitive;
    }

    public String getOperationDesc() {
        return operationDesc;
    }

    public void setOperationDesc(String operationDesc) {
        this.operationDesc = operationDesc;
    }

    @Override
    public String toString() {
        return "AuditLog{" +
                "tableName='" + tableName + '\'' +
                ", operationType=" + operationType +
                ", entityId='" + entityId + '\'' +
                ", oldData='" + oldData + '\'' +
                ", newData='" + newData + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", requestUri='" + requestUri + '\'' +
                ", duration=" + duration +
                ", isSensitive=" + isSensitive +
                ", operationDesc='" + operationDesc + '\'' +
                "} " + super.toString();
    }
}