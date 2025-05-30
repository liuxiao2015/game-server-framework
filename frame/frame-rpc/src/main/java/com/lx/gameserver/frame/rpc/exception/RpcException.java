/*
 * 文件名: RpcException.java
 * 用途: RPC异常基类
 * 实现内容:
 *   - 错误码体系
 *   - 异常分类（网络、业务、系统）
 *   - 异常链传递
 *   - 国际化支持
 * 技术选型:
 *   - 继承RuntimeException
 *   - 自定义错误码和错误类型
 *   - 支持异常链传递
 * 依赖关系:
 *   - 继承自RuntimeException
 *   - 被所有RPC相关异常使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.exception;

/**
 * RPC异常基类
 * <p>
 * 定义RPC调用过程中可能发生的各种异常类型，包括网络异常、
 * 业务异常和系统异常。提供错误码和错误类型分类。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class RpcException extends RuntimeException {

    /**
     * 错误码
     */
    private final int errorCode;

    /**
     * 错误类型
     */
    private final RpcErrorType errorType;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 方法名称
     */
    private String methodName;

    /**
     * RPC错误类型枚举
     */
    public enum RpcErrorType {
        /**
         * 网络异常：网络连接、超时等
         */
        NETWORK("网络异常"),
        
        /**
         * 业务异常：业务逻辑错误、参数错误等
         */
        BUSINESS("业务异常"),
        
        /**
         * 系统异常：系统内部错误、配置错误等
         */
        SYSTEM("系统异常");

        private final String description;

        RpcErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param errorType 错误类型
     */
    public RpcException(int errorCode, String message, RpcErrorType errorType) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param errorType 错误类型
     * @param cause     原因异常
     */
    public RpcException(int errorCode, String message, RpcErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    /**
     * 构造函数
     *
     * @param errorCode   错误码
     * @param message     错误消息
     * @param errorType   错误类型
     * @param serviceName 服务名称
     * @param methodName  方法名称
     */
    public RpcException(int errorCode, String message, RpcErrorType errorType, 
                       String serviceName, String methodName) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.serviceName = serviceName;
        this.methodName = methodName;
    }

    /**
     * 构造函数
     *
     * @param errorCode   错误码
     * @param message     错误消息
     * @param errorType   错误类型
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param cause       原因异常
     */
    public RpcException(int errorCode, String message, RpcErrorType errorType, 
                       String serviceName, String methodName, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.serviceName = serviceName;
        this.methodName = methodName;
    }

    // ===== 静态工厂方法 =====

    /**
     * 创建网络异常
     *
     * @param message 错误消息
     * @return RPC异常
     */
    public static RpcException networkError(String message) {
        return new RpcException(10001, message, RpcErrorType.NETWORK);
    }

    /**
     * 创建网络异常
     *
     * @param message 错误消息
     * @param cause   原因异常
     * @return RPC异常
     */
    public static RpcException networkError(String message, Throwable cause) {
        return new RpcException(10001, message, RpcErrorType.NETWORK, cause);
    }

    /**
     * 创建业务异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @return RPC异常
     */
    public static RpcException businessError(int errorCode, String message) {
        return new RpcException(errorCode, message, RpcErrorType.BUSINESS);
    }

    /**
     * 创建业务异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因异常
     * @return RPC异常
     */
    public static RpcException businessError(int errorCode, String message, Throwable cause) {
        return new RpcException(errorCode, message, RpcErrorType.BUSINESS, cause);
    }

    /**
     * 创建系统异常
     *
     * @param message 错误消息
     * @return RPC异常
     */
    public static RpcException systemError(String message) {
        return new RpcException(50001, message, RpcErrorType.SYSTEM);
    }

    /**
     * 创建系统异常
     *
     * @param message 错误消息
     * @param cause   原因异常
     * @return RPC异常
     */
    public static RpcException systemError(String message, Throwable cause) {
        return new RpcException(50001, message, RpcErrorType.SYSTEM, cause);
    }

    /**
     * 创建超时异常
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param timeout     超时时间（毫秒）
     * @return RPC异常
     */
    public static RpcException timeoutError(String serviceName, String methodName, long timeout) {
        String message = String.format("服务调用超时: %s.%s, 超时时间: %dms", serviceName, methodName, timeout);
        return new RpcException(10002, message, RpcErrorType.NETWORK, serviceName, methodName);
    }

    /**
     * 创建连接异常
     *
     * @param serviceName 服务名称
     * @param cause       原因异常
     * @return RPC异常
     */
    public static RpcException connectionError(String serviceName, Throwable cause) {
        String message = String.format("连接服务失败: %s", serviceName);
        return new RpcException(10003, message, RpcErrorType.NETWORK, serviceName, null, cause);
    }

    /**
     * 创建服务不可用异常
     *
     * @param serviceName 服务名称
     * @return RPC异常
     */
    public static RpcException serviceUnavailable(String serviceName) {
        String message = String.format("服务不可用: %s", serviceName);
        return new RpcException(10004, message, RpcErrorType.NETWORK, serviceName, null);
    }

    // ===== Getter 方法 =====

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误类型
     *
     * @return 错误类型
     */
    public RpcErrorType getErrorType() {
        return errorType;
    }

    /**
     * 获取服务名称
     *
     * @return 服务名称
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 设置服务名称
     *
     * @param serviceName 服务名称
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * 获取方法名称
     *
     * @return 方法名称
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 设置方法名称
     *
     * @param methodName 方法名称
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 判断是否为网络异常
     *
     * @return true表示网络异常
     */
    public boolean isNetworkError() {
        return errorType == RpcErrorType.NETWORK;
    }

    /**
     * 判断是否为业务异常
     *
     * @return true表示业务异常
     */
    public boolean isBusinessError() {
        return errorType == RpcErrorType.BUSINESS;
    }

    /**
     * 判断是否为系统异常
     *
     * @return true表示系统异常
     */
    public boolean isSystemError() {
        return errorType == RpcErrorType.SYSTEM;
    }

    /**
     * 获取完整的错误信息
     *
     * @return 错误信息
     */
    public String getFullMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.getDescription()).append("]");
        sb.append(" 错误码: ").append(errorCode);
        
        if (serviceName != null) {
            sb.append(", 服务: ").append(serviceName);
        }
        
        if (methodName != null) {
            sb.append(", 方法: ").append(methodName);
        }
        
        sb.append(", 消息: ").append(getMessage());
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return "RpcException{" +
                "errorCode=" + errorCode +
                ", errorType=" + errorType +
                ", serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}