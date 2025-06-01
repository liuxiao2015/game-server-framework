/*
 * 文件名: FeignErrorDecoder.java
 * 用途: Feign错误解码器
 * 实现内容:
 *   - HTTP状态码映射
 *   - 业务错误解析
 *   - 异常转换
 *   - 错误日志记录
 * 技术选型:
 *   - Feign ErrorDecoder接口
 *   - 自定义异常体系
 *   - JSON错误响应解析
 * 依赖关系:
 *   - 与RpcException异常体系集成
 *   - 被FeignConfig使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lx.gameserver.common.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Feign错误解码器
 * <p>
 * 负责将Feign调用中的HTTP错误响应转换为具体的业务异常。
 * 支持多种错误格式的解析，提供详细的错误信息。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger logger = LoggerFactory.getLogger(FeignErrorDecoder.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorDecoder defaultErrorDecoder = new Default();

    /**
     * 解码错误响应
     *
     * @param methodKey 方法标识
     * @param response  错误响应
     * @return 异常对象
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus httpStatus = HttpStatus.valueOf(response.status());
        String errorMessage = String.format("调用服务失败: %s, 状态码: %d", methodKey, response.status());
        
        try {
            // 读取响应体
            String responseBody = getResponseBody(response);
            logger.error("Feign调用失败: method={}, status={}, body={}", methodKey, response.status(), responseBody);
            
            // 解析错误响应
            RpcErrorInfo errorInfo = parseErrorResponse(responseBody);
            if (errorInfo != null) {
                errorMessage = errorInfo.getMessage();
            }
            
            // 根据HTTP状态码返回相应异常
            return switch (httpStatus) {
                case BAD_REQUEST -> new RpcException(
                    ErrorCode.PARAM_INVALID.getCode(), 
                    "请求参数错误: " + errorMessage, 
                    RpcException.RpcErrorType.BUSINESS
                );
                case UNAUTHORIZED -> new RpcException(
                    ErrorCode.USER_NOT_LOGIN.getCode(), 
                    "认证失败: " + errorMessage, 
                    RpcException.RpcErrorType.BUSINESS
                );
                case FORBIDDEN -> new RpcException(
                    ErrorCode.PERMISSION_DENIED.getCode(), 
                    "权限不足: " + errorMessage, 
                    RpcException.RpcErrorType.BUSINESS
                );
                case NOT_FOUND -> new RpcException(
                    ErrorCode.DATA_NOT_FOUND.getCode(), 
                    "资源不存在: " + errorMessage, 
                    RpcException.RpcErrorType.BUSINESS
                );
                case METHOD_NOT_ALLOWED -> new RpcException(
                    ErrorCode.METHOD_NOT_SUPPORTED.getCode(), 
                    "方法不允许: " + errorMessage, 
                    RpcException.RpcErrorType.BUSINESS
                );
                case INTERNAL_SERVER_ERROR -> new RpcException(
                    ErrorCode.SYSTEM_ERROR.getCode(), 
                    "服务内部错误: " + errorMessage, 
                    RpcException.RpcErrorType.SYSTEM
                );
                case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> new RpcException(
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), 
                    "服务不可用: " + errorMessage, 
                    RpcException.RpcErrorType.NETWORK
                );
                default -> {
                    if (httpStatus.is4xxClientError()) {
                        yield new RpcException(
                            ErrorCode.PARAM_INVALID.getCode(), 
                            "客户端错误: " + errorMessage, 
                            RpcException.RpcErrorType.BUSINESS
                        );
                    } else if (httpStatus.is5xxServerError()) {
                        yield new RpcException(
                            ErrorCode.SYSTEM_ERROR.getCode(), 
                            "服务器错误: " + errorMessage, 
                            RpcException.RpcErrorType.SYSTEM
                        );
                    } else {
                        yield new RpcException(
                            ErrorCode.SYSTEM_ERROR.getCode(), 
                            "未知错误: " + errorMessage, 
                            RpcException.RpcErrorType.SYSTEM
                        );
                    }
                }
            };
            
        } catch (Exception e) {
            logger.error("解析错误响应失败: methodKey={}, status={}", methodKey, response.status(), e);
            return new RpcException(
                ErrorCode.SYSTEM_ERROR.getCode(), 
                "解析错误响应失败: " + e.getMessage(), 
                RpcException.RpcErrorType.SYSTEM
            );
        }
    }

    /**
     * 读取响应体内容
     *
     * @param response HTTP响应
     * @return 响应体字符串
     */
    private String getResponseBody(Response response) {
        try {
            if (response.body() != null) {
                InputStream inputStream = response.body().asInputStream();
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("读取响应体失败", e);
        }
        return "";
    }

    /**
     * 解析错误响应
     *
     * @param responseBody 响应体
     * @return 错误信息
     */
    private RpcErrorInfo parseErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            // 尝试解析标准格式
            if (jsonNode.has("code") && jsonNode.has("message")) {
                return new RpcErrorInfo(
                    jsonNode.get("code").asInt(),
                    jsonNode.get("message").asText()
                );
            }
            
            // 尝试解析Spring Boot错误格式
            if (jsonNode.has("error") && jsonNode.has("message")) {
                return new RpcErrorInfo(
                    500,
                    jsonNode.get("message").asText()
                );
            }
            
            // 其他格式，返回原始内容
            return new RpcErrorInfo(500, responseBody);
            
        } catch (Exception e) {
            logger.debug("解析JSON响应失败，使用原始响应: {}", e.getMessage());
            return new RpcErrorInfo(500, responseBody);
        }
    }

    /**
     * RPC错误信息
     */
    private static class RpcErrorInfo {
        private final int code;
        private final String message;

        public RpcErrorInfo(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}