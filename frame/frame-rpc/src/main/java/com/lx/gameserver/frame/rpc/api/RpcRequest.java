/*
 * 文件名: RpcRequest.java
 * 用途: 统一RPC请求模型
 * 实现内容:
 *   - 请求ID、时间戳
 *   - 分页、排序支持
 *   - 扩展字段预留
 *   - 请求上下文信息
 * 技术选型:
 *   - 序列化支持
 *   - 泛型设计
 *   - 链式调用
 * 依赖关系:
 *   - 独立的请求模型
 *   - 被所有RPC接口使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一RPC请求模型
 * <p>
 * 定义RPC调用的统一请求结构，包含请求标识、分页参数、
 * 排序参数、扩展字段等。支持泛型，可以承载任意类型的业务数据。
 * </p>
 *
 * @param <T> 业务数据类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcRequest<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求ID（唯一标识）
     */
    @JsonProperty("requestId")
    private String requestId;

    /**
     * 请求时间戳
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    /**
     * 追踪ID（用于调用链追踪）
     */
    @JsonProperty("traceId")
    private String traceId;

    /**
     * 用户ID（可选）
     */
    @JsonProperty("userId")
    private String userId;

    /**
     * 业务数据
     */
    @JsonProperty("data")
    private T data;

    /**
     * 分页参数
     */
    @JsonProperty("pagination")
    private PaginationParam pagination;

    /**
     * 排序参数
     */
    @JsonProperty("sorting")
    private SortingParam sorting;

    /**
     * 扩展字段
     */
    @JsonProperty("extensions")
    private Map<String, Object> extensions;

    /**
     * 默认构造函数
     */
    public RpcRequest() {
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.extensions = new HashMap<>();
    }

    /**
     * 构造函数
     *
     * @param data 业务数据
     */
    public RpcRequest(T data) {
        this();
        this.data = data;
    }

    /**
     * 分页参数类
     */
    public static class PaginationParam implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 页码（从1开始）
         */
        @JsonProperty("pageNum")
        private int pageNum = 1;

        /**
         * 页大小
         */
        @JsonProperty("pageSize")
        private int pageSize = 10;

        /**
         * 是否需要总数
         */
        @JsonProperty("needTotal")
        private boolean needTotal = true;

        public PaginationParam() {}

        public PaginationParam(int pageNum, int pageSize) {
            this.pageNum = pageNum;
            this.pageSize = pageSize;
        }

        public PaginationParam(int pageNum, int pageSize, boolean needTotal) {
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            this.needTotal = needTotal;
        }

        // Getter and Setter methods
        public int getPageNum() { return pageNum; }
        public void setPageNum(int pageNum) { this.pageNum = pageNum; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public boolean isNeedTotal() { return needTotal; }
        public void setNeedTotal(boolean needTotal) { this.needTotal = needTotal; }
    }

    /**
     * 排序参数类
     */
    public static class SortingParam implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 排序字段
         */
        @JsonProperty("sortBy")
        private String sortBy;

        /**
         * 排序方向（ASC/DESC）
         */
        @JsonProperty("sortOrder")
        private String sortOrder = "ASC";

        public SortingParam() {}

        public SortingParam(String sortBy) {
            this.sortBy = sortBy;
        }

        public SortingParam(String sortBy, String sortOrder) {
            this.sortBy = sortBy;
            this.sortOrder = sortOrder;
        }

        // Getter and Setter methods
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    }

    // ===== 链式设置方法 =====

    /**
     * 设置追踪ID
     */
    public RpcRequest<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * 设置用户ID
     */
    public RpcRequest<T> userId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * 设置分页参数
     */
    public RpcRequest<T> pagination(int pageNum, int pageSize) {
        this.pagination = new PaginationParam(pageNum, pageSize);
        return this;
    }

    /**
     * 设置分页参数
     */
    public RpcRequest<T> pagination(PaginationParam pagination) {
        this.pagination = pagination;
        return this;
    }

    /**
     * 设置排序参数
     */
    public RpcRequest<T> sorting(String sortBy, String sortOrder) {
        this.sorting = new SortingParam(sortBy, sortOrder);
        return this;
    }

    /**
     * 设置排序参数
     */
    public RpcRequest<T> sorting(SortingParam sorting) {
        this.sorting = sorting;
        return this;
    }

    /**
     * 添加扩展字段
     */
    public RpcRequest<T> extension(String key, Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    // ===== 静态工厂方法 =====

    /**
     * 创建请求
     */
    public static <T> RpcRequest<T> of(T data) {
        return new RpcRequest<>(data);
    }

    /**
     * 创建空请求
     */
    public static RpcRequest<Void> empty() {
        return new RpcRequest<>();
    }

    // ===== Getter/Setter 方法 =====

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public PaginationParam getPagination() { return pagination; }
    public void setPagination(PaginationParam pagination) { this.pagination = pagination; }
    public SortingParam getSorting() { return sorting; }
    public void setSorting(SortingParam sorting) { this.sorting = sorting; }
    public Map<String, Object> getExtensions() { return extensions; }
    public void setExtensions(Map<String, Object> extensions) { this.extensions = extensions; }

    @Override
    public String toString() {
        return "RpcRequest{" +
                "requestId='" + requestId + '\'' +
                ", timestamp=" + timestamp +
                ", traceId='" + traceId + '\'' +
                ", userId='" + userId + '\'' +
                ", data=" + data +
                ", pagination=" + pagination +
                ", sorting=" + sorting +
                ", extensions=" + extensions +
                '}';
    }
}