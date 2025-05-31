/*
 * 文件名: AccountValidator.java
 * 用途: 账号验证器
 * 实现内容:
 *   - 用户名规则验证
 *   - 密码复杂度验证
 *   - 手机号/邮箱验证
 *   - 敏感词过滤
 *   - 黑名单检查
 * 技术选型:
 *   - 正则表达式验证
 *   - 敏感词算法
 *   - 数据校验注解
 * 依赖关系:
 *   - 被AccountService使用
 *   - 被登录策略使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.account;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * 账号验证器
 * <p>
 * 提供账号相关的各种验证功能，包括格式验证、规则检查、
 * 敏感词过滤等，确保账号数据的合规性和安全性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class AccountValidator {

    // 用户名正则：3-32位字母数字下划线
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    
    // 手机号正则
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    
    // 邮箱正则
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    // 强密码正则：至少8位，包含大小写字母、数字和特殊字符
    private static final Pattern STRONG_PASSWORD_PATTERN = 
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
    
    // 中等密码正则：至少6位，包含字母和数字
    private static final Pattern MEDIUM_PASSWORD_PATTERN = 
            Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$");

    // 敏感词列表（实际应该从数据库或配置文件加载）
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>();
    
    // 黑名单用户名（实际应该从数据库加载）
    private static final Set<String> BLACKLIST_USERNAMES = new HashSet<>();

    static {
        // 初始化敏感词
        SENSITIVE_WORDS.add("admin");
        SENSITIVE_WORDS.add("administrator");
        SENSITIVE_WORDS.add("root");
        SENSITIVE_WORDS.add("system");
        
        // 初始化黑名单用户名
        BLACKLIST_USERNAMES.add("admin");
        BLACKLIST_USERNAMES.add("administrator");
        BLACKLIST_USERNAMES.add("root");
        BLACKLIST_USERNAMES.add("system");
        BLACKLIST_USERNAMES.add("guest");
    }

    /**
     * 验证注册信息
     */
    public ValidationResult validateRegistration(AccountService.AccountRegistrationRequest request) {
        // 验证用户名
        if (request.getUsername() != null) {
            ValidationResult usernameResult = validateUsername(request.getUsername());
            if (!usernameResult.isValid()) {
                return usernameResult;
            }
        }

        // 验证密码
        if (request.getPassword() != null) {
            ValidationResult passwordResult = validatePassword(request.getPassword());
            if (!passwordResult.isValid()) {
                return passwordResult;
            }
        }

        // 验证手机号
        if (request.getMobile() != null) {
            ValidationResult mobileResult = validateMobile(request.getMobile());
            if (!mobileResult.isValid()) {
                return mobileResult;
            }
        }

        // 验证邮箱
        if (request.getEmail() != null) {
            ValidationResult emailResult = validateEmail(request.getEmail());
            if (!emailResult.isValid()) {
                return emailResult;
            }
        }

        // 验证昵称
        if (request.getNickname() != null) {
            ValidationResult nicknameResult = validateNickname(request.getNickname());
            if (!nicknameResult.isValid()) {
                return nicknameResult;
            }
        }

        return ValidationResult.success();
    }

    /**
     * 验证更新信息
     */
    public ValidationResult validateUpdate(AccountService.AccountUpdateRequest request) {
        // 验证昵称
        if (request.getNickname() != null) {
            ValidationResult nicknameResult = validateNickname(request.getNickname());
            if (!nicknameResult.isValid()) {
                return nicknameResult;
            }
        }

        return ValidationResult.success();
    }

    /**
     * 验证用户名
     */
    public ValidationResult validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return ValidationResult.failure("USERNAME_EMPTY", "用户名不能为空");
        }

        username = username.trim();

        // 检查长度和格式
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return ValidationResult.failure("USERNAME_INVALID_FORMAT", 
                    "用户名只能包含字母、数字和下划线，长度3-32位");
        }

        // 检查是否为纯数字
        if (username.matches("^\\d+$")) {
            return ValidationResult.failure("USERNAME_ALL_DIGITS", "用户名不能为纯数字");
        }

        // 检查黑名单
        if (BLACKLIST_USERNAMES.contains(username.toLowerCase())) {
            return ValidationResult.failure("USERNAME_BLACKLISTED", "该用户名不允许使用");
        }

        // 检查敏感词
        if (containsSensitiveWord(username)) {
            return ValidationResult.failure("USERNAME_SENSITIVE", "用户名包含敏感词");
        }

        return ValidationResult.success();
    }

    /**
     * 验证密码
     */
    public ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.failure("PASSWORD_EMPTY", "密码不能为空");
        }

        // 检查长度
        if (password.length() < 6) {
            return ValidationResult.failure("PASSWORD_TOO_SHORT", "密码长度至少6位");
        }

        if (password.length() > 128) {
            return ValidationResult.failure("PASSWORD_TOO_LONG", "密码长度不能超过128位");
        }

        // 检查强度
        if (!MEDIUM_PASSWORD_PATTERN.matcher(password).matches()) {
            return ValidationResult.failure("PASSWORD_TOO_WEAK", 
                    "密码强度过低，至少包含字母和数字");
        }

        // 检查常见弱密码
        if (isCommonWeakPassword(password)) {
            return ValidationResult.failure("PASSWORD_TOO_COMMON", "密码过于简单，请使用更复杂的密码");
        }

        return ValidationResult.success();
    }

    /**
     * 验证手机号
     */
    public ValidationResult validateMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            return ValidationResult.failure("MOBILE_EMPTY", "手机号不能为空");
        }

        mobile = mobile.trim();

        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            return ValidationResult.failure("MOBILE_INVALID_FORMAT", "手机号格式不正确");
        }

        return ValidationResult.success();
    }

    /**
     * 验证邮箱
     */
    public ValidationResult validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.failure("EMAIL_EMPTY", "邮箱不能为空");
        }

        email = email.trim();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ValidationResult.failure("EMAIL_INVALID_FORMAT", "邮箱格式不正确");
        }

        // 检查邮箱长度
        if (email.length() > 254) {
            return ValidationResult.failure("EMAIL_TOO_LONG", "邮箱地址过长");
        }

        return ValidationResult.success();
    }

    /**
     * 验证昵称
     */
    public ValidationResult validateNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return ValidationResult.failure("NICKNAME_EMPTY", "昵称不能为空");
        }

        nickname = nickname.trim();

        // 检查长度
        if (nickname.length() > 64) {
            return ValidationResult.failure("NICKNAME_TOO_LONG", "昵称长度不能超过64个字符");
        }

        // 检查敏感词
        if (containsSensitiveWord(nickname)) {
            return ValidationResult.failure("NICKNAME_SENSITIVE", "昵称包含敏感词");
        }

        return ValidationResult.success();
    }

    /**
     * 检查是否包含敏感词
     */
    private boolean containsSensitiveWord(String text) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerText.contains(sensitiveWord.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否为常见弱密码
     */
    private boolean isCommonWeakPassword(String password) {
        String lowerPassword = password.toLowerCase();
        
        // 常见弱密码列表
        String[] weakPasswords = {
            "123456", "password", "123456789", "12345678", "12345",
            "1234567", "1234567890", "qwerty", "abc123", "password123"
        };

        for (String weakPassword : weakPasswords) {
            if (lowerPassword.equals(weakPassword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 验证结果类
     */
    @Data
    public static class ValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;

        public static ValidationResult success() {
            ValidationResult result = new ValidationResult();
            result.setValid(true);
            return result;
        }

        public static ValidationResult failure(String errorCode, String errorMessage) {
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setErrorCode(errorCode);
            result.setErrorMessage(errorMessage);
            return result;
        }
    }
}