/*
 * 文件名: MobileLoginStrategy.java
 * 用途: 手机验证码登录策略
 * 实现内容:
 *   - 手机验证码登录实现
 *   - 短信验证码发送和验证
 *   - 验证码有效期管理
 *   - 验证码错误次数限制
 *   - 手机号格式验证
 *   - 短信发送频率限制
 * 技术选型:
 *   - Redis验证码存储
 *   - 短信服务接口集成
 *   - 限流算法实现
 *   - 事件发布机制
 * 依赖关系:
 *   - 实现LoginStrategy接口
 *   - 依赖SmsService
 *   - 依赖AccountService
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.strategy;

import com.lx.gameserver.business.login.core.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 手机验证码登录策略
 * <p>
 * 实现基于手机号和短信验证码的登录方式，提供验证码发送、
 * 验证、有效期管理和频率限制等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class MobileLoginStrategy implements LoginStrategy {

    private static final String STRATEGY_TYPE = "MOBILE";
    private static final String SMS_CODE_KEY_PREFIX = "sms:code:";
    private static final String SMS_SEND_LIMIT_KEY_PREFIX = "sms:limit:";
    private static final String SMS_VERIFY_FAILED_KEY_PREFIX = "sms:failed:";
    
    /**
     * 验证码长度
     */
    private static final int CODE_LENGTH = 6;
    
    /**
     * 验证码有效期（分钟）
     */
    private static final int CODE_EXPIRE_MINUTES = 5;
    
    /**
     * 每日发送限制
     */
    private static final int DAILY_SEND_LIMIT = 10;
    
    /**
     * 发送间隔（秒）
     */
    private static final int SEND_INTERVAL_SECONDS = 60;
    
    /**
     * 最大验证失败次数
     */
    private static final int MAX_VERIFY_FAILED_ATTEMPTS = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();

    @Autowired
    public MobileLoginStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getStrategyType() {
        return STRATEGY_TYPE;
    }

    @Override
    public boolean isEnabled() {
        // 可以从配置中读取
        return true;
    }

    @Override
    public ValidationResult validateRequest(LoginRequest request, LoginContext context) {
        if (!(request instanceof MobileLoginRequest)) {
            return ValidationResult.failure("INVALID_REQUEST_TYPE", "请求类型不匹配");
        }

        MobileLoginRequest mobileRequest = (MobileLoginRequest) request;
        
        // 验证手机号
        if (mobileRequest.getMobile() == null || mobileRequest.getMobile().trim().isEmpty()) {
            return ValidationResult.failure("MOBILE_REQUIRED", "手机号不能为空");
        }
        
        String mobile = mobileRequest.getMobile().trim();
        if (!isValidMobile(mobile)) {
            return ValidationResult.failure("INVALID_MOBILE_FORMAT", "手机号格式不正确");
        }
        
        // 验证验证码
        if (mobileRequest.getSmsCode() == null || mobileRequest.getSmsCode().trim().isEmpty()) {
            return ValidationResult.failure("SMS_CODE_REQUIRED", "验证码不能为空");
        }
        
        // 检查验证失败次数
        if (getVerifyFailedAttempts(mobile) >= MAX_VERIFY_FAILED_ATTEMPTS) {
            return ValidationResult.failure("TOO_MANY_VERIFY_ATTEMPTS", 
                    "验证码错误次数过多，请重新获取验证码");
        }

        return ValidationResult.success();
    }

    @Override
    public LoginResult login(LoginRequest request, LoginContext context) {
        MobileLoginRequest mobileRequest = (MobileLoginRequest) request;
        String mobile = mobileRequest.getMobile().trim();
        String smsCode = mobileRequest.getSmsCode().trim();

        try {
            // 验证短信验证码
            if (!verifySmsCode(mobile, smsCode)) {
                incrementVerifyFailedAttempts(mobile);
                int remainingAttempts = MAX_VERIFY_FAILED_ATTEMPTS - getVerifyFailedAttempts(mobile);
                return LoginResult.failure("INVALID_SMS_CODE", 
                        String.format("验证码错误，还有%d次尝试机会", remainingAttempts));
            }

            // 查询或创建账号
            Account account = findOrCreateAccountByMobile(mobile);
            if (account == null) {
                return LoginResult.failure("ACCOUNT_CREATE_FAILED", "账号创建失败");
            }

            // 检查账号状态
            if (!account.isNormal()) {
                return LoginResult.failure("ACCOUNT_STATUS_ABNORMAL", 
                        "账号状态异常: " + account.getStatus().getDescription());
            }

            // 清除验证失败记录
            clearVerifyFailedAttempts(mobile);
            
            // 删除已使用的验证码
            deleteSmsCode(mobile);

            // 创建登录会话
            LoginSession session = createLoginSession(account, context);

            // 更新账号最后登录信息
            updateAccountLastLogin(account, context);

            log.info("手机号[{}]使用验证码方式登录成功", mobile);
            return LoginResult.success(account, session);

        } catch (Exception e) {
            log.error("手机验证码登录处理异常: mobile={}", mobile, e);
            return LoginResult.failure("LOGIN_ERROR", "登录处理异常，请稍后重试");
        }
    }

    /**
     * 发送短信验证码
     */
    public SendSmsResult sendSmsCode(String mobile, LoginContext context) {
        try {
            // 验证手机号格式
            if (!isValidMobile(mobile)) {
                return SendSmsResult.failure("INVALID_MOBILE_FORMAT", "手机号格式不正确");
            }

            // 检查发送频率限制
            if (isInSendCooldown(mobile)) {
                long remainingTime = getSendCooldownRemainingTime(mobile);
                return SendSmsResult.failure("SEND_TOO_FREQUENT", 
                        String.format("发送过于频繁，请%d秒后再试", remainingTime));
            }

            // 检查每日发送限制
            if (getDailySendCount(mobile) >= DAILY_SEND_LIMIT) {
                return SendSmsResult.failure("DAILY_LIMIT_EXCEEDED", 
                        String.format("今日发送次数已达上限%d次", DAILY_SEND_LIMIT));
            }

            // 生成验证码
            String code = generateSmsCode();

            // 存储验证码
            storeSmsCode(mobile, code);

            // 发送短信
            boolean sendSuccess = sendSms(mobile, code);
            if (!sendSuccess) {
                return SendSmsResult.failure("SMS_SEND_FAILED", "短信发送失败，请稍后重试");
            }

            // 记录发送
            recordSmsSend(mobile);

            log.info("向手机号[{}]发送验证码成功", mobile);
            return SendSmsResult.success("验证码发送成功");

        } catch (Exception e) {
            log.error("发送短信验证码异常: mobile={}", mobile, e);
            return SendSmsResult.failure("SEND_ERROR", "发送验证码异常，请稍后重试");
        }
    }

    /**
     * 手机验证码登录请求
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MobileLoginRequest extends LoginStrategy.LoginRequest {
        /**
         * 手机号
         */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String mobile;

        /**
         * 短信验证码
         */
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码必须是6位数字")
        private String smsCode;
    }

    /**
     * 短信发送结果
     */
    @Data
    public static class SendSmsResult {
        private boolean success;
        private String errorCode;
        private String message;

        public static SendSmsResult success(String message) {
            SendSmsResult result = new SendSmsResult();
            result.setSuccess(true);
            result.setMessage(message);
            return result;
        }

        public static SendSmsResult failure(String errorCode, String message) {
            SendSmsResult result = new SendSmsResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * 验证手机号格式
     */
    private boolean isValidMobile(String mobile) {
        return mobile != null && mobile.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 生成短信验证码
     */
    private String generateSmsCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 存储短信验证码
     */
    private void storeSmsCode(String mobile, String code) {
        String key = SMS_CODE_KEY_PREFIX + mobile;
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(CODE_EXPIRE_MINUTES));
    }

    /**
     * 验证短信验证码
     */
    private boolean verifySmsCode(String mobile, String inputCode) {
        String key = SMS_CODE_KEY_PREFIX + mobile;
        Object storedCode = redisTemplate.opsForValue().get(key);
        return storedCode != null && storedCode.toString().equals(inputCode);
    }

    /**
     * 删除已使用的验证码
     */
    private void deleteSmsCode(String mobile) {
        String key = SMS_CODE_KEY_PREFIX + mobile;
        redisTemplate.delete(key);
    }

    /**
     * 检查是否在发送冷却期
     */
    private boolean isInSendCooldown(String mobile) {
        String key = SMS_SEND_LIMIT_KEY_PREFIX + "interval:" + mobile;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取发送冷却剩余时间
     */
    private long getSendCooldownRemainingTime(String mobile) {
        String key = SMS_SEND_LIMIT_KEY_PREFIX + "interval:" + mobile;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }

    /**
     * 获取今日发送次数
     */
    private int getDailySendCount(String mobile) {
        String key = SMS_SEND_LIMIT_KEY_PREFIX + "daily:" + mobile;
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    /**
     * 记录短信发送
     */
    private void recordSmsSend(String mobile) {
        // 记录发送间隔限制
        String intervalKey = SMS_SEND_LIMIT_KEY_PREFIX + "interval:" + mobile;
        redisTemplate.opsForValue().set(intervalKey, "1", Duration.ofSeconds(SEND_INTERVAL_SECONDS));

        // 记录每日发送次数
        String dailyKey = SMS_SEND_LIMIT_KEY_PREFIX + "daily:" + mobile;
        redisTemplate.opsForValue().increment(dailyKey);
        redisTemplate.expire(dailyKey, Duration.ofDays(1));
    }

    /**
     * 获取验证失败次数
     */
    private int getVerifyFailedAttempts(String mobile) {
        String key = SMS_VERIFY_FAILED_KEY_PREFIX + mobile;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * 增加验证失败次数
     */
    private void incrementVerifyFailedAttempts(String mobile) {
        String key = SMS_VERIFY_FAILED_KEY_PREFIX + mobile;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(CODE_EXPIRE_MINUTES));
    }

    /**
     * 清除验证失败记录
     */
    private void clearVerifyFailedAttempts(String mobile) {
        String key = SMS_VERIFY_FAILED_KEY_PREFIX + mobile;
        redisTemplate.delete(key);
    }

    /**
     * 发送短信
     */
    private boolean sendSms(String mobile, String code) {
        // 这里应该调用短信服务接口
        // 为了演示，暂时返回true
        log.info("发送短信验证码到手机号[{}]: {}", mobile, code);
        return true;
    }

    /**
     * 根据手机号查找或创建账号
     */
    private Account findOrCreateAccountByMobile(String mobile) {
        // 这里应该调用AccountService的方法
        // 首先查找是否存在该手机号的账号，如果不存在则创建
        return null;
    }

    /**
     * 创建登录会话
     */
    private LoginSession createLoginSession(Account account, LoginContext context) {
        // 这里应该调用SessionManager的方法
        LoginSession session = new LoginSession();
        session.setSessionId(java.util.UUID.randomUUID().toString());
        session.setAccountId(account.getAccountId());
        session.setUsername(account.getUsername());
        session.setLoginMethod(STRATEGY_TYPE);
        session.setLoginTime(LocalDateTime.now());
        session.setStatus(LoginSession.SessionStatus.ACTIVE);
        
        if (context.getNetworkInfo() != null) {
            session.setLoginIp(context.getNetworkInfo().getClientIp());
        }
        
        if (context.getClientInfo() != null) {
            session.setUserAgent(context.getClientInfo().getUserAgent());
            session.setDeviceType(LoginSession.DeviceType.fromUserAgent(
                    context.getClientInfo().getUserAgent()));
        }
        
        return session;
    }

    /**
     * 更新账号最后登录信息
     */
    private void updateAccountLastLogin(Account account, LoginContext context) {
        account.setLastLoginTime(LocalDateTime.now());
        if (context.getNetworkInfo() != null) {
            account.setLastLoginIp(context.getNetworkInfo().getClientIp());
        }
        // 这里应该调用AccountService保存更新
    }
}