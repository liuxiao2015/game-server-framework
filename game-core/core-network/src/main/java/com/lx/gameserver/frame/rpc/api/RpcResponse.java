/*
 * 文件名: RpcResponse.java
 * 用途: 统一RPC响应模型
 * 实现内容:
 *   - 统一响应结构
 *   - 错误信息封装
 *   - 分页结果支持
 *   - 扩展字段预留
 * 技术选型:
 *   - 继承Result类
 *   - 泛型设计
 *   - 序列化支持
 * 依赖关系:
 *   - 继承common-core的Result类
 *   - 被所有RPC接口使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lx.gameserver.common.Result;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一RPC响应模型
 * <p>
 * 继承通用Result类，扩展RPC调用特有的响应信息，
 * 包括分页信息、追踪信息、性能指标等。
 * </p>
 *
 * @param <T> 响应数据类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcResponse<T> extends Result<T> {

    /**
     * 请求ID（与请求对应）
     */
    @JsonProperty("requestId")
    private String requestId;

    /**
     * 追踪ID
     */
    @JsonProperty("traceId")
    private String traceId;

    /**
     * 服务节点标识
     */
    @JsonProperty("serverId")
    private String serverId;

    /**
     * 处理耗时（毫秒）
     */
    @JsonProperty("processingTime")
    private Long processingTime;

    /**
     * 分页信息
     */
    @JsonProperty("pagination")
    private PaginationInfo pagination;

    /**
     * 扩展字段
     */
    @JsonProperty("extensions")
    private Map<String, Object> extensions;

    /**
     * 默认构造函数
     */
    public RpcResponse() {
        super();
        this.extensions = new HashMap<>();
    }

    /**
     * 构造函数
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     */
    public RpcResponse(int code, String message, T data) {
        super(code, message, data);
        this.extensions = new HashMap<>();
    }

    /**
     * 分页信息类
     */
    public static class PaginationInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 当前页码
         */
        @JsonProperty("pageNum")
        private int pageNum;

        /**
         * 页大小
         */
        @JsonProperty("pageSize")
        private int pageSize;

        /**
         * 总记录数
         */
        @JsonProperty("total")
        private long total;

        /**
         * 总页数
         */
        @JsonProperty("pages")
        private long pages;

        /**
         * 是否有下一页
         */
        @JsonProperty("hasNext")
        private boolean hasNext;

        /**
         * 是否有上一页
         */
        @JsonProperty("hasPrevious")
        private boolean hasPrevious;

        public PaginationInfo() {}

        public PaginationInfo(int pageNum, int pageSize, long total) {
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            this.total = total;
            this.pages = (total + pageSize - 1) / pageSize;
            this.hasNext = pageNum < pages;
            this.hasPrevious = pageNum > 1;
        }

        // Getter and Setter methods
        public int getPageNum() { return pageNum; }
        public void setPageNum(int pageNum) { this.pageNum = pageNum; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public long getPages() { return pages; }
        public void setPages(long pages) { this.pages = pages; }
        public boolean isHasNext() { return hasNext; }
        public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
        public boolean isHasPrevious() { return hasPrevious; }
        public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
    }

    // ===== 链式设置方法 =====

    /**
     * 设置请求ID
     */
    public RpcResponse<T> requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * 设置追踪ID
     */
    public RpcResponse<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * 设置服务节点ID
     */
    public RpcResponse<T> serverId(String serverId) {
        this.serverId = serverId;
        return this;
    }

    /**
     * 设置处理耗时
     */
    public RpcResponse<T> processingTime(Long processingTime) {
        this.processingTime = processingTime;
        return this;
    }

    /**
     * 设置分页信息
     */
    public RpcResponse<T> pagination(int pageNum, int pageSize, long total) {
        this.pagination = new PaginationInfo(pageNum, pageSize, total);
        return this;
    }

    /**
     * 设置分页信息
     */
    public RpcResponse<T> pagination(PaginationInfo pagination) {
        this.pagination = pagination;
        return this;
    }

    /**
     * 添加扩展字段
     */
    public RpcResponse<T> extension(String key, Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    // ===== 静态工厂方法 =====

    /**
     * 创建成功响应
     */
    public static <T> RpcResponse<T> success(T data) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(0);
        response.setMessage("操作成功");
        response.setData(data);
        return response;
    }

    /**
     * 创建成功响应（无数据）
     */
    public static RpcResponse<Void> success() {
        return success(null);
    }

    /**
     * 创建成功响应（带分页）
     */
    public static <T> RpcResponse<List<T>> success(List<T> data, int pageNum, int pageSize, long total) {
        RpcResponse<List<T>> response = success(data);
        response.pagination(pageNum, pageSize, total);
        return response;
    }

    /**
     * 创建错误响应
     */
    public static <T> RpcResponse<T> error(int code, String message) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    /**
     * 创建错误响应
     */
    public static <T> RpcResponse<T> error(String message) {
        return error(500, message);
    }

    /**
     * 从Result创建RpcResponse
     */
    public static <T> RpcResponse<T> from(Result<T> result) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(result.getCode());
        response.setMessage(result.getMessage());
        response.setData(result.getData());
        response.setTimestamp(result.getTimestamp());
        return response;
    }

    /**
     * 从请求创建响应
     */
    public static <T> RpcResponse<T> from(RpcRequest<?> request) {
        RpcResponse<T> response = new RpcResponse<>();
        if (request != null) {
            response.setRequestId(request.getRequestId());
            response.setTraceId(request.getTraceId());
        }
        return response;
    }

    // ===== Getter/Setter 方法 =====

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public Long getProcessingTime() { return processingTime; }
    public void setProcessingTime(Long processingTime) { this.processingTime = processingTime; }
    public PaginationInfo getPagination() { return pagination; }
    public void setPagination(PaginationInfo pagination) { this.pagination = pagination; }
    public Map<String, Object> getExtensions() { return extensions; }
    public void setExtensions(Map<String, Object> extensions) { this.extensions = extensions; }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "requestId='" + requestId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", serverId='" + serverId + '\'' +
                ", processingTime=" + processingTime +
                ", pagination=" + pagination +
                ", extensions=" + extensions +
                ", code=" + getCode() +
                ", message='" + getMessage() + '\'' +
                ", data=" + getData() +
                '}';
    }
}