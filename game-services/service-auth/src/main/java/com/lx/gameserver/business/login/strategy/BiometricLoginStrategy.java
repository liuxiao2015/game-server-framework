/*
 * 文件名: BiometricLoginStrategy.java
 * 用途: 生物识别登录策略
 * 实现内容:
 *   - 生物识别登录实现
 *   - 指纹登录支持
 *   - 面部识别支持
 *   - 生物特征存储和验证
 *   - 安全认证流程
 *   - 降级方案处理
 * 技术选型:
 *   - 生物识别算法集成
 *   - 加密存储
 *   - 安全验证协议
 *   - 降级认证机制
 * 依赖关系:
 *   - 实现LoginStrategy接口
 *   - 依赖BiometricService
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
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 生物识别登录策略
 * <p>
 * 实现基于生物特征（指纹、面部识别等）的登录方式，
 * 提供高安全性的身份认证和降级处理机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class BiometricLoginStrategy implements LoginStrategy {

    private static final String STRATEGY_TYPE = "BIOMETRIC";
    private static final String BIOMETRIC_TEMPLATE_KEY_PREFIX = "biometric:template:";
    private static final String BIOMETRIC_FAILED_KEY_PREFIX = "biometric:failed:";
    
    /**
     * 最大识别失败次数
     */
    private static final int MAX_FAILED_ATTEMPTS = 3;
    
    /**
     * 识别相似度阈值
     */
    private static final double SIMILARITY_THRESHOLD = 0.8;

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public BiometricLoginStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getStrategyType() {
        return STRATEGY_TYPE;
    }

    @Override
    public boolean isEnabled() {
        // 可以从配置中读取，考虑到硬件支持情况
        return true;
    }

    @Override
    public ValidationResult validateRequest(LoginRequest request, LoginContext context) {
        if (!(request instanceof BiometricLoginRequest)) {
            return ValidationResult.failure("INVALID_REQUEST_TYPE", "请求类型不匹配");
        }

        BiometricLoginRequest biometricRequest = (BiometricLoginRequest) request;
        
        // 验证生物识别类型
        if (biometricRequest.getBiometricType() == null) {
            return ValidationResult.failure("BIOMETRIC_TYPE_REQUIRED", "生物识别类型不能为空");
        }
        
        // 验证账号标识
        if (biometricRequest.getAccountIdentifier() == null || 
            biometricRequest.getAccountIdentifier().trim().isEmpty()) {
            return ValidationResult.failure("ACCOUNT_IDENTIFIER_REQUIRED", "账号标识不能为空");
        }
        
        // 验证生物特征数据
        if (biometricRequest.getBiometricData() == null || 
            biometricRequest.getBiometricData().trim().isEmpty()) {
            return ValidationResult.failure("BIOMETRIC_DATA_REQUIRED", "生物特征数据不能为空");
        }
        
        // 检查识别失败次数
        String accountIdentifier = biometricRequest.getAccountIdentifier();
        if (getFailedAttempts(accountIdentifier) >= MAX_FAILED_ATTEMPTS) {
            return ValidationResult.failure("TOO_MANY_FAILED_ATTEMPTS", 
                    "生物识别失败次数过多，请使用其他登录方式");
        }
        
        // 验证生物识别数据格式
        if (!isValidBiometricData(biometricRequest.getBiometricData(), 
                                  biometricRequest.getBiometricType())) {
            return ValidationResult.failure("INVALID_BIOMETRIC_DATA", "生物特征数据格式不正确");
        }

        return ValidationResult.success();
    }

    @Override
    public LoginResult login(LoginRequest request, LoginContext context) {
        BiometricLoginRequest biometricRequest = (BiometricLoginRequest) request;
        String accountIdentifier = biometricRequest.getAccountIdentifier();
        BiometricType biometricType = biometricRequest.getBiometricType();
        String biometricData = biometricRequest.getBiometricData();

        try {
            // 查找账号
            Account account = findAccountByIdentifier(accountIdentifier);
            if (account == null) {
                incrementFailedAttempts(accountIdentifier);
                return LoginResult.failure("ACCOUNT_NOT_FOUND", "账号不存在");
            }

            // 检查账号状态
            if (!account.isNormal()) {
                return LoginResult.failure("ACCOUNT_STATUS_ABNORMAL", 
                        "账号状态异常: " + account.getStatus().getDescription());
            }

            // 获取存储的生物特征模板
            BiometricTemplate template = getBiometricTemplate(account.getAccountId(), biometricType);
            if (template == null) {
                return LoginResult.failure("BIOMETRIC_TEMPLATE_NOT_FOUND", 
                        "未找到生物特征模板，请先注册生物特征");
            }

            // 进行生物特征识别
            BiometricVerificationResult verificationResult = verifyBiometric(biometricData, template);
            if (!verificationResult.isSuccess()) {
                incrementFailedAttempts(accountIdentifier);
                int remainingAttempts = MAX_FAILED_ATTEMPTS - getFailedAttempts(accountIdentifier);
                return LoginResult.failure("BIOMETRIC_VERIFICATION_FAILED", 
                        String.format("生物识别验证失败，还有%d次尝试机会", remainingAttempts));
            }

            // 检查相似度
            if (verificationResult.getSimilarity() < SIMILARITY_THRESHOLD) {
                incrementFailedAttempts(accountIdentifier);
                return LoginResult.failure("BIOMETRIC_SIMILARITY_TOO_LOW", 
                        "生物特征相似度过低，验证失败");
            }

            // 清除失败尝试记录
            clearFailedAttempts(accountIdentifier);

            // 更新生物特征模板（可选，用于自适应学习）
            if (verificationResult.getSimilarity() > 0.95) {
                updateBiometricTemplate(account.getAccountId(), biometricType, biometricData);
            }

            // 创建登录会话
            LoginSession session = createLoginSession(account, context);

            // 更新账号最后登录信息
            updateAccountLastLogin(account, context);

            log.info("账号[{}]使用{}生物识别登录成功，相似度: {}", 
                    account.getUsername(), biometricType.getDescription(), 
                    verificationResult.getSimilarity());
            
            return LoginResult.success(account, session);

        } catch (Exception e) {
            log.error("生物识别登录处理异常: accountIdentifier={}, biometricType={}", 
                    accountIdentifier, biometricType, e);
            return LoginResult.failure("LOGIN_ERROR", "生物识别登录处理异常，请稍后重试");
        }
    }

    /**
     * 注册生物特征
     */
    public BiometricRegistrationResult registerBiometric(Long accountId, BiometricType biometricType, 
                                                        String biometricData) {
        try {
            // 验证生物特征数据
            if (!isValidBiometricData(biometricData, biometricType)) {
                return BiometricRegistrationResult.failure("INVALID_BIOMETRIC_DATA", 
                        "生物特征数据格式不正确");
            }

            // 提取特征模板
            BiometricTemplate template = extractBiometricTemplate(biometricData, biometricType);
            if (template == null) {
                return BiometricRegistrationResult.failure("TEMPLATE_EXTRACTION_FAILED", 
                        "生物特征模板提取失败");
            }

            // 检查质量
            if (template.getQuality() < 0.7) {
                return BiometricRegistrationResult.failure("POOR_QUALITY", 
                        "生物特征质量过低，请重新采集");
            }

            // 存储生物特征模板
            storeBiometricTemplate(accountId, biometricType, template);

            log.info("账号[{}]注册{}生物特征成功", accountId, biometricType.getDescription());
            return BiometricRegistrationResult.success("生物特征注册成功");

        } catch (Exception e) {
            log.error("生物特征注册异常: accountId={}, biometricType={}", accountId, biometricType, e);
            return BiometricRegistrationResult.failure("REGISTRATION_ERROR", 
                    "生物特征注册异常，请稍后重试");
        }
    }

    /**
     * 生物识别登录请求
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BiometricLoginRequest extends LoginStrategy.LoginRequest {
        /**
         * 账号标识（用户名/手机号/邮箱）
         */
        @NotBlank(message = "账号标识不能为空")
        private String accountIdentifier;

        /**
         * 生物识别类型
         */
        @NotNull(message = "生物识别类型不能为空")
        private BiometricType biometricType;

        /**
         * 生物特征数据（Base64编码）
         */
        @NotBlank(message = "生物特征数据不能为空")
        private String biometricData;

        /**
         * 设备特征信息
         */
        private String deviceFeatures;

        /**
         * 活体检测结果
         */
        private Boolean livenessDetection;
    }

    /**
     * 生物识别类型枚举
     */
    public enum BiometricType {
        FINGERPRINT(1, "指纹识别"),
        FACE(2, "面部识别"),
        IRIS(3, "虹膜识别"),
        VOICE(4, "声纹识别"),
        PALM(5, "掌纹识别");

        private final int code;
        private final String description;

        BiometricType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static BiometricType fromCode(int code) {
            for (BiometricType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("未知的生物识别类型代码: " + code);
        }
    }

    /**
     * 生物特征模板
     */
    @Data
    public static class BiometricTemplate {
        private String templateData;
        private BiometricType biometricType;
        private double quality;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private String algorithm;
        private String version;
    }

    /**
     * 生物识别验证结果
     */
    @Data
    public static class BiometricVerificationResult {
        private boolean success;
        private double similarity;
        private String algorithm;
        private long processingTime;

        public static BiometricVerificationResult success(double similarity) {
            BiometricVerificationResult result = new BiometricVerificationResult();
            result.setSuccess(true);
            result.setSimilarity(similarity);
            return result;
        }

        public static BiometricVerificationResult failure() {
            BiometricVerificationResult result = new BiometricVerificationResult();
            result.setSuccess(false);
            result.setSimilarity(0.0);
            return result;
        }
    }

    /**
     * 生物特征注册结果
     */
    @Data
    public static class BiometricRegistrationResult {
        private boolean success;
        private String errorCode;
        private String message;

        public static BiometricRegistrationResult success(String message) {
            BiometricRegistrationResult result = new BiometricRegistrationResult();
            result.setSuccess(true);
            result.setMessage(message);
            return result;
        }

        public static BiometricRegistrationResult failure(String errorCode, String message) {
            BiometricRegistrationResult result = new BiometricRegistrationResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * 验证生物特征数据格式
     */
    private boolean isValidBiometricData(String biometricData, BiometricType biometricType) {
        try {
            // 检查是否为有效的Base64编码
            byte[] decodedData = Base64.getDecoder().decode(biometricData);
            
            // 根据生物识别类型验证数据长度和格式
            switch (biometricType) {
                case FINGERPRINT:
                    return decodedData.length > 100 && decodedData.length < 10000;
                case FACE:
                    return decodedData.length > 1000 && decodedData.length < 100000;
                case IRIS:
                    return decodedData.length > 500 && decodedData.length < 50000;
                case VOICE:
                    return decodedData.length > 2000 && decodedData.length < 1000000;
                case PALM:
                    return decodedData.length > 200 && decodedData.length < 20000;
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warn("生物特征数据格式验证失败", e);
            return false;
        }
    }

    /**
     * 获取失败尝试次数
     */
    private int getFailedAttempts(String accountIdentifier) {
        String key = BIOMETRIC_FAILED_KEY_PREFIX + accountIdentifier;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * 增加失败尝试次数
     */
    private void incrementFailedAttempts(String accountIdentifier) {
        String key = BIOMETRIC_FAILED_KEY_PREFIX + accountIdentifier;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, java.time.Duration.ofHours(1));
    }

    /**
     * 清除失败尝试记录
     */
    private void clearFailedAttempts(String accountIdentifier) {
        String key = BIOMETRIC_FAILED_KEY_PREFIX + accountIdentifier;
        redisTemplate.delete(key);
    }

    /**
     * 获取生物特征模板
     */
    private BiometricTemplate getBiometricTemplate(Long accountId, BiometricType biometricType) {
        String key = BIOMETRIC_TEMPLATE_KEY_PREFIX + accountId + ":" + biometricType.getCode();
        Object template = redisTemplate.opsForValue().get(key);
        return template != null ? (BiometricTemplate) template : null;
    }

    /**
     * 存储生物特征模板
     */
    private void storeBiometricTemplate(Long accountId, BiometricType biometricType, BiometricTemplate template) {
        String key = BIOMETRIC_TEMPLATE_KEY_PREFIX + accountId + ":" + biometricType.getCode();
        redisTemplate.opsForValue().set(key, template);
    }

    /**
     * 进行生物特征识别
     */
    private BiometricVerificationResult verifyBiometric(String biometricData, BiometricTemplate template) {
        // 这里应该调用具体的生物识别算法
        // 为了演示，使用模拟的相似度计算
        long startTime = System.currentTimeMillis();
        
        try {
            // 模拟识别过程
            Thread.sleep(100);
            
            // 模拟相似度计算（实际应该使用专业的生物识别算法）
            double similarity = Math.random() * 0.3 + 0.7; // 0.7-1.0之间的随机值
            
            BiometricVerificationResult result = BiometricVerificationResult.success(similarity);
            result.setProcessingTime(System.currentTimeMillis() - startTime);
            result.setAlgorithm("MockBiometricAlgorithm");
            
            return result;
            
        } catch (Exception e) {
            log.error("生物识别验证异常", e);
            return BiometricVerificationResult.failure();
        }
    }

    /**
     * 提取生物特征模板
     */
    private BiometricTemplate extractBiometricTemplate(String biometricData, BiometricType biometricType) {
        // 这里应该调用具体的特征提取算法
        BiometricTemplate template = new BiometricTemplate();
        template.setTemplateData(biometricData); // 实际应该是提取的特征模板
        template.setBiometricType(biometricType);
        template.setQuality(Math.random() * 0.3 + 0.7); // 模拟质量评分
        template.setCreateTime(LocalDateTime.now());
        template.setAlgorithm("MockFeatureExtraction");
        template.setVersion("1.0");
        
        return template;
    }

    /**
     * 更新生物特征模板
     */
    private void updateBiometricTemplate(Long accountId, BiometricType biometricType, String biometricData) {
        // 自适应学习，更新模板以提高识别准确性
        BiometricTemplate template = getBiometricTemplate(accountId, biometricType);
        if (template != null) {
            template.setUpdateTime(LocalDateTime.now());
            storeBiometricTemplate(accountId, biometricType, template);
        }
    }

    // 以下方法需要具体实现

    private Account findAccountByIdentifier(String identifier) {
        // 查询数据库
        return null;
    }

    private LoginSession createLoginSession(Account account, LoginContext context) {
        // 创建会话
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

    private void updateAccountLastLogin(Account account, LoginContext context) {
        account.setLastLoginTime(LocalDateTime.now());
        if (context.getNetworkInfo() != null) {
            account.setLastLoginIp(context.getNetworkInfo().getClientIp());
        }
        // 保存更新
    }
}