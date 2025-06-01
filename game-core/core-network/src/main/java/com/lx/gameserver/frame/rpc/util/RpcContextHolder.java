/*
 * 文件名: RpcContextHolder.java
 * 用途: RPC上下文持有者
 * 实现内容:
 *   - 请求上下文传递
 *   - 用户信息传递
 *   - 自定义属性
 *   - 线程安全
 * 技术选型:
 *   - ThreadLocal线程本地变量
 *   - Map存储上下文信息
 *   - 自动清理机制
 * 依赖关系:
 *   - 独立工具类
 *   - 被所有RPC调用使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.util;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RPC上下文持有者
 * <p>
 * 使用ThreadLocal存储RPC调用的上下文信息，包括请求ID、
 * 追踪ID、用户信息、自定义属性等。支持跨方法调用传递上下文。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class RpcContextHolder {

    /**
     * 上下文存储
     */
    private static final ThreadLocal<RpcContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * RPC上下文类
     */
    public static class RpcContext {
        private String requestId;
        private String traceId;
        private String userId;
        private String username;
        private String clientIp;
        private String userAgent;
        private LocalDateTime startTime;
        private Map<String, Object> attributes;

        public RpcContext() {
            this.requestId = UUID.randomUUID().toString();
            this.traceId = UUID.randomUUID().toString().replace("-", "");
            this.startTime = LocalDateTime.now();
            this.attributes = new HashMap<>();
        }

        // Getter and Setter methods
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

        /**
         * 设置属性
         */
        public void setAttribute(String key, Object value) {
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(key, value);
        }

        /**
         * 获取属性
         */
        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String key) {
            return attributes != null ? (T) attributes.get(key) : null;
        }

        /**
         * 移除属性
         */
        public Object removeAttribute(String key) {
            return attributes != null ? attributes.remove(key) : null;
        }

        /**
         * 清空属性
         */
        public void clearAttributes() {
            if (attributes != null) {
                attributes.clear();
            }
        }

        @Override
        public String toString() {
            return "RpcContext{" +
                    "requestId='" + requestId + '\'' +
                    ", traceId='" + traceId + '\'' +
                    ", userId='" + userId + '\'' +
                    ", username='" + username + '\'' +
                    ", clientIp='" + clientIp + '\'' +
                    ", startTime=" + startTime +
                    ", attributesCount=" + (attributes != null ? attributes.size() : 0) +
                    '}';
        }
    }

    // ===== 静态方法 =====

    /**
     * 获取当前上下文
     */
    public static RpcContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取当前上下文（如果不存在则创建）
     */
    public static RpcContext getOrCreateContext() {
        RpcContext context = CONTEXT_HOLDER.get();
        if (context == null) {
            context = new RpcContext();
            CONTEXT_HOLDER.set(context);
        }
        return context;
    }

    /**
     * 设置上下文
     */
    public static void setContext(RpcContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 清除上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取请求ID
     */
    public static String getRequestId() {
        RpcContext context = getContext();
        return context != null ? context.getRequestId() : null;
    }

    /**
     * 设置请求ID
     */
    public static void setRequestId(String requestId) {
        getOrCreateContext().setRequestId(requestId);
    }

    /**
     * 获取追踪ID
     */
    public static String getTraceId() {
        RpcContext context = getContext();
        return context != null ? context.getTraceId() : null;
    }

    /**
     * 设置追踪ID
     */
    public static void setTraceId(String traceId) {
        getOrCreateContext().setTraceId(traceId);
    }

    /**
     * 获取用户ID
     */
    public static String getUserId() {
        RpcContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 设置用户ID
     */
    public static void setUserId(String userId) {
        getOrCreateContext().setUserId(userId);
    }

    /**
     * 获取用户名
     */
    public static String getUsername() {
        RpcContext context = getContext();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 设置用户名
     */
    public static void setUsername(String username) {
        getOrCreateContext().setUsername(username);
    }

    /**
     * 获取客户端IP
     */
    public static String getClientIp() {
        RpcContext context = getContext();
        return context != null ? context.getClientIp() : null;
    }

    /**
     * 设置客户端IP
     */
    public static void setClientIp(String clientIp) {
        getOrCreateContext().setClientIp(clientIp);
    }

    /**
     * 获取用户代理
     */
    public static String getUserAgent() {
        RpcContext context = getContext();
        return context != null ? context.getUserAgent() : null;
    }

    /**
     * 设置用户代理
     */
    public static void setUserAgent(String userAgent) {
        getOrCreateContext().setUserAgent(userAgent);
    }

    /**
     * 获取开始时间
     */
    public static LocalDateTime getStartTime() {
        RpcContext context = getContext();
        return context != null ? context.getStartTime() : null;
    }

    /**
     * 设置属性
     */
    public static void setAttribute(String key, Object value) {
        getOrCreateContext().setAttribute(key, value);
    }

    /**
     * 获取属性
     */
    public static <T> T getAttribute(String key) {
        RpcContext context = getContext();
        return context != null ? context.getAttribute(key) : null;
    }

    /**
     * 获取属性（带默认值）
     */
    public static <T> T getAttribute(String key, T defaultValue) {
        T value = getAttribute(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取属性（Optional包装）
     */
    public static <T> Optional<T> getAttributeOptional(String key) {
        return Optional.ofNullable(getAttribute(key));
    }

    /**
     * 移除属性
     */
    public static Object removeAttribute(String key) {
        RpcContext context = getContext();
        return context != null ? context.removeAttribute(key) : null;
    }

    /**
     * 清空所有属性
     */
    public static void clearAttributes() {
        RpcContext context = getContext();
        if (context != null) {
            context.clearAttributes();
        }
    }

    /**
     * 检查是否存在上下文
     */
    public static boolean hasContext() {
        return getContext() != null;
    }

    /**
     * 获取上下文的所有属性
     */
    public static Map<String, Object> getAllAttributes() {
        RpcContext context = getContext();
        return context != null ? new HashMap<>(context.getAttributes()) : new HashMap<>();
    }

    /**
     * 创建新的上下文
     */
    public static RpcContext createContext() {
        RpcContext context = new RpcContext();
        setContext(context);
        return context;
    }

    /**
     * 复制上下文
     */
    public static RpcContext copyContext() {
        RpcContext current = getContext();
        if (current == null) {
            return createContext();
        }

        RpcContext newContext = new RpcContext();
        newContext.setRequestId(current.getRequestId());
        newContext.setTraceId(current.getTraceId());
        newContext.setUserId(current.getUserId());
        newContext.setUsername(current.getUsername());
        newContext.setClientIp(current.getClientIp());
        newContext.setUserAgent(current.getUserAgent());
        newContext.setStartTime(current.getStartTime());

        if (current.getAttributes() != null) {
            newContext.setAttributes(new HashMap<>(current.getAttributes()));
        }

        return newContext;
    }

    /**
     * 执行带上下文的操作
     */
    public static <T> T executeWithContext(RpcContext context, ContextualSupplier<T> supplier) {
        RpcContext oldContext = getContext();
        try {
            setContext(context);
            return supplier.get();
        } finally {
            if (oldContext != null) {
                setContext(oldContext);
            } else {
                clearContext();
            }
        }
    }

    /**
     * 执行带上下文的操作（无返回值）
     */
    public static void executeWithContext(RpcContext context, ContextualRunnable runnable) {
        RpcContext oldContext = getContext();
        try {
            setContext(context);
            runnable.run();
        } finally {
            if (oldContext != null) {
                setContext(oldContext);
            } else {
                clearContext();
            }
        }
    }

    /**
     * 上下文供应者接口
     */
    @FunctionalInterface
    public interface ContextualSupplier<T> {
        T get();
    }

    /**
     * 上下文运行接口
     */
    @FunctionalInterface
    public interface ContextualRunnable {
        void run();
    }
}