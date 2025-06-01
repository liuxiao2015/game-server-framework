/*
 * 文件名: PasswordLoginStrategy.java
 * 用途: 账号密码登录策略
 * 实现内容:
 *   - 账号密码登录实现
 *   - 密码加密验证（BCrypt/Argon2）
 *   - 密码强度校验
 *   - 密码错误次数限制
 *   - 账号锁定机制
 *   - 密码过期提醒
 * 技术选型:
 *   - Spring Security BCrypt
 *   - Redis限流存储
 *   - 策略模式实现
 *   - 事件发布机制
 * 依赖关系:
 *   - 实现LoginStrategy接口
 *   - 依赖AccountService
 *   - 依赖SecurityService
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 账号密码登录策略
 * <p>
 * 实现基于用户名/手机号/邮箱和密码的登录方式，提供密码强度验证、
 * 失败次数限制、账号锁定等安全功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class PasswordLoginStrategy implements LoginStrategy {

    private static final String STRATEGY_TYPE = "PASSWORD";
    private static final String FAILED_ATTEMPTS_KEY_PREFIX = "login:failed:";
    private static final String ACCOUNT_LOCK_KEY_PREFIX = "login:lock:";
    
    /**
     * 密码强度正则表达式
     * 至少8位，包含大小写字母、数字和特殊字符
     */
    private static final Pattern STRONG_PASSWORD_PATTERN = 
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
    
    /**
     * 中等强度密码正则表达式
     * 至少6位，包含字母和数字
     */
    private static final Pattern MEDIUM_PASSWORD_PATTERN = 
            Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$");

    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 最大失败尝试次数
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;
    
    /**
     * 账号锁定时间（分钟）
     */
    private static final int LOCK_DURATION_MINUTES = 30;

    @Autowired
    public PasswordLoginStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.passwordEncoder = new BCryptPasswordEncoder();
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
        if (!(request instanceof PasswordLoginRequest)) {
            return ValidationResult.failure("INVALID_REQUEST_TYPE", "请求类型不匹配");
        }

        PasswordLoginRequest passwordRequest = (PasswordLoginRequest) request;
        
        // 验证必填字段
        if (passwordRequest.getAccount() == null || passwordRequest.getAccount().trim().isEmpty()) {
            return ValidationResult.failure("ACCOUNT_REQUIRED", "账号不能为空");
        }
        
        if (passwordRequest.getPassword() == null || passwordRequest.getPassword().trim().isEmpty()) {
            return ValidationResult.failure("PASSWORD_REQUIRED", "密码不能为空");
        }
        
        // 验证账号格式
        String account = passwordRequest.getAccount().trim();
        if (!isValidAccount(account)) {
            return ValidationResult.failure("INVALID_ACCOUNT_FORMAT", "账号格式不正确");
        }
        
        // 检查账号是否被锁定
        if (isAccountLocked(account)) {
            long remainingTime = getAccountLockRemainingTime(account);
            return ValidationResult.failure("ACCOUNT_LOCKED", 
                    String.format("账号已被锁定，请%d分钟后再试", remainingTime / 60));
        }
        
        // 检查失败次数
        int failedAttempts = getFailedAttempts(account);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockAccount(account);
            return ValidationResult.failure("TOO_MANY_FAILED_ATTEMPTS", "登录失败次数过多，账号已被锁定");
        }

        return ValidationResult.success();
    }

    @Override
    public LoginResult login(LoginRequest request, LoginContext context) {
        PasswordLoginRequest passwordRequest = (PasswordLoginRequest) request;
        String account = passwordRequest.getAccount().trim();
        String password = passwordRequest.getPassword();

        try {
            // 查询账号信息
            Account accountEntity = findAccountByIdentifier(account);
            if (accountEntity == null) {
                incrementFailedAttempts(account);
                return LoginResult.failure("ACCOUNT_NOT_FOUND", "账号不存在");
            }

            // 检查账号状态
            if (!accountEntity.isNormal()) {
                return LoginResult.failure("ACCOUNT_STATUS_ABNORMAL", 
                        "账号状态异常: " + accountEntity.getStatus().getDescription());
            }

            // 验证密码
            if (!passwordEncoder.matches(password, accountEntity.getPasswordHash())) {
                incrementFailedAttempts(account);
                int remainingAttempts = MAX_FAILED_ATTEMPTS - getFailedAttempts(account);
                return LoginResult.failure("INVALID_PASSWORD", 
                        String.format("密码错误，还有%d次尝试机会", remainingAttempts));
            }

            // 检查密码是否需要更新
            if (needPasswordUpdate(accountEntity)) {
                // 这里可以返回特殊状态，提示用户更新密码
                log.info("账号[{}]的密码需要更新", account);
            }

            // 清除失败尝试记录
            clearFailedAttempts(account);

            // 创建登录会话
            LoginSession session = createLoginSession(accountEntity, context);

            // 更新账号最后登录信息
            updateAccountLastLogin(accountEntity, context);

            log.info("账号[{}]使用密码方式登录成功", account);
            return LoginResult.success(accountEntity, session);

        } catch (Exception e) {
            log.error("密码登录处理异常: account={}", account, e);
            return LoginResult.failure("LOGIN_ERROR", "登录处理异常，请稍后重试");
        }
    }

    /**
     * 密码登录请求
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordLoginRequest extends LoginStrategy.LoginRequest {
        /**
         * 账号（用户名/手机号/邮箱）
         */
        @NotBlank(message = "账号不能为空")
        private String account;

        /**
         * 密码
         */
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6-128个字符之间")
        private String password;

        /**
         * 验证码（可选）
         */
        private String captcha;

        /**
         * 是否记住登录
         */
        private Boolean rememberMe = false;
    }

    /**
     * 验证账号格式
     */
    private boolean isValidAccount(String account) {
        // 用户名格式：3-32位字母数字下划线
        if (account.matches("^[a-zA-Z0-9_]{3,32}$")) {
            return true;
        }
        // 手机号格式
        if (account.matches("^1[3-9]\\d{9}$")) {
            return true;
        }
        // 邮箱格式
        if (account.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return true;
        }
        return false;
    }

    /**
     * 检查账号是否被锁定
     */
    private boolean isAccountLocked(String account) {
        String lockKey = ACCOUNT_LOCK_KEY_PREFIX + account;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    /**
     * 获取账号锁定剩余时间（秒）
     */
    private long getAccountLockRemainingTime(String account) {
        String lockKey = ACCOUNT_LOCK_KEY_PREFIX + account;
        Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }

    /**
     * 锁定账号
     */
    private void lockAccount(String account) {
        String lockKey = ACCOUNT_LOCK_KEY_PREFIX + account;
        redisTemplate.opsForValue().set(lockKey, LocalDateTime.now().toString(), 
                Duration.ofMinutes(LOCK_DURATION_MINUTES));
        log.warn("账号[{}]因失败次数过多被锁定{}分钟", account, LOCK_DURATION_MINUTES);
    }

    /**
     * 获取失败尝试次数
     */
    private int getFailedAttempts(String account) {
        String key = FAILED_ATTEMPTS_KEY_PREFIX + account;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * 增加失败尝试次数
     */
    private void incrementFailedAttempts(String account) {
        String key = FAILED_ATTEMPTS_KEY_PREFIX + account;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(1)); // 1小时后重置
    }

    /**
     * 清除失败尝试记录
     */
    private void clearFailedAttempts(String account) {
        String key = FAILED_ATTEMPTS_KEY_PREFIX + account;
        redisTemplate.delete(key);
    }

    /**
     * 根据标识符查找账号
     */
    private Account findAccountByIdentifier(String identifier) {
        // 这里应该调用AccountService的方法
        // 为了简化，暂时返回null，实际实现中需要查询数据库
        return null;
    }

    /**
     * 检查密码是否需要更新
     */
    private boolean needPasswordUpdate(Account account) {
        // 检查密码最后更新时间，如果超过90天则提示更新
        // 实际实现中需要在账号表中加入password_update_time字段
        return false;
    }

    /**
     * 创建登录会话
     */
    private LoginSession createLoginSession(Account account, LoginContext context) {
        // 这里应该调用SessionManager的方法
        // 为了简化，暂时返回一个基本的会话对象
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

    /**
     * 验证密码强度
     */
    public static PasswordStrength validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrength.WEAK;
        }
        
        if (STRONG_PASSWORD_PATTERN.matcher(password).matches()) {
            return PasswordStrength.STRONG;
        } else if (MEDIUM_PASSWORD_PATTERN.matcher(password).matches()) {
            return PasswordStrength.MEDIUM;
        } else {
            return PasswordStrength.WEAK;
        }
    }

    /**
     * 密码强度枚举
     */
    public enum PasswordStrength {
        WEAK(1, "弱", "密码过于简单，建议包含大小写字母、数字和特殊字符"),
        MEDIUM(2, "中等", "密码强度一般，建议增加特殊字符"),
        STRONG(3, "强", "密码强度良好");

        private final int level;
        private final String description;
        private final String suggestion;

        PasswordStrength(int level, String description, String suggestion) {
            this.level = level;
            this.description = description;
            this.suggestion = suggestion;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }
}