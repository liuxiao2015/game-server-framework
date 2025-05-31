/*
 * 文件名: LoginStrategy.java
 * 用途: 登录策略接口
 * 实现内容:
 *   - 统一的登录策略接口定义
 *   - 登录参数验证和结果封装
 *   - 支持多种登录方式的策略模式
 *   - 策略注册和选择机制
 * 技术选型:
 *   - 策略设计模式
 *   - 泛型参数支持
 *   - Spring Bean注册
 * 依赖关系:
 *   - 被各种具体策略实现
 *   - 被LoginService使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.core;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 登录策略接口
 * <p>
 * 定义统一的登录策略接口，支持多种登录方式的策略模式实现。
 * 所有登录策略都必须实现此接口，以确保登录流程的一致性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface LoginStrategy {

    /**
     * 获取策略类型
     *
     * @return 策略类型标识
     */
    String getStrategyType();

    /**
     * 检查策略是否启用
     *
     * @return 如果策略启用返回true，否则返回false
     */
    boolean isEnabled();

    /**
     * 验证登录参数
     *
     * @param request 登录请求
     * @param context 登录上下文
     * @return 验证结果
     */
    ValidationResult validateRequest(LoginRequest request, LoginContext context);

    /**
     * 执行登录
     *
     * @param request 登录请求
     * @param context 登录上下文
     * @return 登录结果
     */
    LoginResult login(LoginRequest request, LoginContext context);

    /**
     * 登录请求基类
     */
    @Data
    abstract class LoginRequest {
        /**
         * 登录类型
         */
        private String loginType;
        
        /**
         * 客户端IP
         */
        private String clientIp;
        
        /**
         * 设备信息
         */
        private String deviceId;
        
        /**
         * 用户代理
         */
        private String userAgent;
        
        /**
         * 请求时间
         */
        private LocalDateTime requestTime;
        
        /**
         * 扩展参数
         */
        private Map<String, Object> extraParams;
    }

    /**
     * 验证结果
     */
    @Data
    class ValidationResult {
        /**
         * 验证是否通过
         */
        private boolean valid;
        
        /**
         * 错误代码
         */
        private String errorCode;
        
        /**
         * 错误消息
         */
        private String errorMessage;
        
        /**
         * 创建成功的验证结果
         */
        public static ValidationResult success() {
            ValidationResult result = new ValidationResult();
            result.setValid(true);
            return result;
        }
        
        /**
         * 创建失败的验证结果
         */
        public static ValidationResult failure(String errorCode, String errorMessage) {
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setErrorCode(errorCode);
            result.setErrorMessage(errorMessage);
            return result;
        }
    }

    /**
     * 登录结果
     */
    @Data
    class LoginResult {
        /**
         * 登录是否成功
         */
        private boolean success;
        
        /**
         * 错误代码
         */
        private String errorCode;
        
        /**
         * 错误消息
         */
        private String errorMessage;
        
        /**
         * 账号信息
         */
        private Account account;
        
        /**
         * 登录会话
         */
        private LoginSession session;
        
        /**
         * 是否需要进一步验证
         */
        private boolean needFurtherVerification;
        
        /**
         * 验证类型（如短信验证码、图形验证码等）
         */
        private String verificationType;
        
        /**
         * 扩展数据
         */
        private Map<String, Object> extraData;
        
        /**
         * 创建成功的登录结果
         */
        public static LoginResult success(Account account, LoginSession session) {
            LoginResult result = new LoginResult();
            result.setSuccess(true);
            result.setAccount(account);
            result.setSession(session);
            return result;
        }
        
        /**
         * 创建失败的登录结果
         */
        public static LoginResult failure(String errorCode, String errorMessage) {
            LoginResult result = new LoginResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setErrorMessage(errorMessage);
            return result;
        }
        
        /**
         * 创建需要进一步验证的结果
         */
        public static LoginResult needVerification(String verificationType) {
            LoginResult result = new LoginResult();
            result.setSuccess(false);
            result.setNeedFurtherVerification(true);
            result.setVerificationType(verificationType);
            return result;
        }
    }
}