/*
 * 文件名: DeviceLoginStrategy.java
 * 用途: 设备登录策略（游客模式）
 * 实现内容:
 *   - 设备登录实现（游客模式）
 *   - 设备唯一标识生成和验证
 *   - 设备信息采集和存储
 *   - 设备绑定管理
 *   - 游客账号升级
 *   - 设备更换处理
 * 技术选型:
 *   - 设备指纹算法
 *   - UUID生成策略
 *   - 加密存储
 *   - Redis缓存
 * 依赖关系:
 *   - 实现LoginStrategy接口
 *   - 依赖DeviceService
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 设备登录策略
 * <p>
 * 实现基于设备唯一标识的游客登录方式，提供设备绑定、
 * 游客账号升级、设备更换等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class DeviceLoginStrategy implements LoginStrategy {

    private static final String STRATEGY_TYPE = "DEVICE";
    private static final String DEVICE_ACCOUNT_KEY_PREFIX = "device:account:";
    private static final String DEVICE_INFO_KEY_PREFIX = "device:info:";
    private static final String GUEST_PREFIX = "guest_";

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public DeviceLoginStrategy(RedisTemplate<String, Object> redisTemplate) {
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
        if (!(request instanceof DeviceLoginRequest)) {
            return ValidationResult.failure("INVALID_REQUEST_TYPE", "请求类型不匹配");
        }

        DeviceLoginRequest deviceRequest = (DeviceLoginRequest) request;
        
        // 验证设备ID
        if (deviceRequest.getDeviceId() == null || deviceRequest.getDeviceId().trim().isEmpty()) {
            return ValidationResult.failure("DEVICE_ID_REQUIRED", "设备ID不能为空");
        }
        
        // 验证设备信息
        if (!isValidDeviceInfo(deviceRequest, context)) {
            return ValidationResult.failure("INVALID_DEVICE_INFO", "设备信息不完整");
        }

        return ValidationResult.success();
    }

    @Override
    public LoginResult login(LoginRequest request, LoginContext context) {
        DeviceLoginRequest deviceRequest = (DeviceLoginRequest) request;
        String deviceId = deviceRequest.getDeviceId().trim();

        try {
            // 生成设备指纹
            String deviceFingerprint = generateDeviceFingerprint(deviceRequest, context);

            // 查找或创建设备账号
            Account account = findOrCreateDeviceAccount(deviceId, deviceFingerprint, context);
            if (account == null) {
                return LoginResult.failure("ACCOUNT_CREATE_FAILED", "设备账号创建失败");
            }

            // 检查账号状态
            if (!account.isNormal()) {
                return LoginResult.failure("ACCOUNT_STATUS_ABNORMAL", 
                        "账号状态异常: " + account.getStatus().getDescription());
            }

            // 更新设备信息
            updateDeviceInfo(deviceId, deviceRequest, context);

            // 创建登录会话
            LoginSession session = createLoginSession(account, context);

            // 更新账号最后登录信息
            updateAccountLastLogin(account, context);

            log.info("设备[{}]使用设备登录成功，账号[{}]", deviceId, account.getUsername());
            return LoginResult.success(account, session);

        } catch (Exception e) {
            log.error("设备登录处理异常: deviceId={}", deviceId, e);
            return LoginResult.failure("LOGIN_ERROR", "设备登录处理异常，请稍后重试");
        }
    }

    /**
     * 游客账号升级
     */
    public UpgradeResult upgradeGuestAccount(Long accountId, String username, String password, String mobile) {
        try {
            // 查找游客账号
            Account guestAccount = findAccountById(accountId);
            if (guestAccount == null) {
                return UpgradeResult.failure("ACCOUNT_NOT_FOUND", "游客账号不存在");
            }

            if (!guestAccount.isGuest()) {
                return UpgradeResult.failure("NOT_GUEST_ACCOUNT", "不是游客账号");
            }

            // 检查用户名是否已存在
            if (isUsernameExists(username)) {
                return UpgradeResult.failure("USERNAME_EXISTS", "用户名已存在");
            }

            // 检查手机号是否已存在
            if (mobile != null && isMobileExists(mobile)) {
                return UpgradeResult.failure("MOBILE_EXISTS", "手机号已存在");
            }

            // 升级账号
            guestAccount.setUsername(username);
            guestAccount.setPasswordHash(encryptPassword(password));
            guestAccount.setMobile(mobile);
            guestAccount.setAccountType(Account.AccountType.NORMAL);
            guestAccount.setUpdateTime(LocalDateTime.now());

            // 保存账号
            saveAccount(guestAccount);

            log.info("游客账号[{}]升级为正式账号[{}]成功", accountId, username);
            return UpgradeResult.success("账号升级成功");

        } catch (Exception e) {
            log.error("游客账号升级异常: accountId={}", accountId, e);
            return UpgradeResult.failure("UPGRADE_ERROR", "账号升级异常，请稍后重试");
        }
    }

    /**
     * 设备登录请求
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeviceLoginRequest extends LoginStrategy.LoginRequest {
        /**
         * 设备唯一标识
         */
        @NotBlank(message = "设备ID不能为空")
        private String deviceId;

        /**
         * 设备名称
         */
        private String deviceName;

        /**
         * 设备型号
         */
        private String deviceModel;

        /**
         * 设备品牌
         */
        private String deviceBrand;

        /**
         * 操作系统
         */
        private String operatingSystem;

        /**
         * 操作系统版本
         */
        private String osVersion;

        /**
         * 应用版本
         */
        private String appVersion;

        /**
         * 设备序列号
         */
        private String serialNumber;

        /**
         * MAC地址
         */
        private String macAddress;

        /**
         * 设备指纹相关信息
         */
        private String additionalFingerprint;
    }

    /**
     * 升级结果
     */
    @Data
    public static class UpgradeResult {
        private boolean success;
        private String errorCode;
        private String message;

        public static UpgradeResult success(String message) {
            UpgradeResult result = new UpgradeResult();
            result.setSuccess(true);
            result.setMessage(message);
            return result;
        }

        public static UpgradeResult failure(String errorCode, String message) {
            UpgradeResult result = new UpgradeResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * 验证设备信息
     */
    private boolean isValidDeviceInfo(DeviceLoginRequest request, LoginContext context) {
        // 检查基本设备信息
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            return false;
        }

        // 检查上下文中的设备指纹信息
        if (context.getDeviceFingerprint() == null) {
            return false;
        }

        return true;
    }

    /**
     * 生成设备指纹
     */
    private String generateDeviceFingerprint(DeviceLoginRequest request, LoginContext context) {
        StringBuilder fingerprint = new StringBuilder();
        
        // 基本设备信息
        fingerprint.append(request.getDeviceId()).append("|");
        fingerprint.append(nullSafe(request.getDeviceModel())).append("|");
        fingerprint.append(nullSafe(request.getDeviceBrand())).append("|");
        fingerprint.append(nullSafe(request.getOperatingSystem())).append("|");
        fingerprint.append(nullSafe(request.getOsVersion())).append("|");
        
        // MAC地址
        if (request.getMacAddress() != null) {
            fingerprint.append(request.getMacAddress()).append("|");
        }
        
        // 序列号
        if (request.getSerialNumber() != null) {
            fingerprint.append(request.getSerialNumber()).append("|");
        }
        
        // 上下文中的设备指纹信息
        LoginContext.DeviceFingerprint deviceFp = context.getDeviceFingerprint();
        if (deviceFp != null) {
            fingerprint.append(nullSafe(deviceFp.getCpuInfo())).append("|");
            fingerprint.append(nullSafe(deviceFp.getMemoryInfo())).append("|");
            fingerprint.append(nullSafe(deviceFp.getNetworkInterface())).append("|");
        }
        
        // 生成哈希
        return sha256(fingerprint.toString());
    }

    /**
     * 查找或创建设备账号
     */
    private Account findOrCreateDeviceAccount(String deviceId, String deviceFingerprint, LoginContext context) {
        // 首先查找是否存在设备账号
        Account account = findAccountByDeviceId(deviceId);
        if (account != null) {
            // 验证设备指纹
            if (validateDeviceFingerprint(deviceId, deviceFingerprint)) {
                return account;
            } else {
                log.warn("设备[{}]指纹验证失败，可能存在安全风险", deviceId);
                // 可以选择拒绝登录或要求额外验证
                return account;
            }
        }

        // 创建新的游客账号
        account = new Account();
        account.setAccountId(generateAccountId());
        account.setUsername(GUEST_PREFIX + System.currentTimeMillis());
        account.setAccountType(Account.AccountType.GUEST);
        account.setStatus(Account.AccountStatus.NORMAL);
        account.setSecurityLevel(Account.SecurityLevel.LOW);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        
        if (context.getNetworkInfo() != null) {
            account.setRegisterIp(context.getNetworkInfo().getClientIp());
            account.setLastLoginIp(context.getNetworkInfo().getClientIp());
        }
        account.setLastLoginTime(LocalDateTime.now());

        // 保存账号
        saveAccount(account);

        // 绑定设备和账号
        bindDeviceToAccount(deviceId, account.getAccountId(), deviceFingerprint);

        return account;
    }

    /**
     * 更新设备信息
     */
    private void updateDeviceInfo(String deviceId, DeviceLoginRequest request, LoginContext context) {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(deviceId);
        deviceInfo.setDeviceName(request.getDeviceName());
        deviceInfo.setDeviceModel(request.getDeviceModel());
        deviceInfo.setDeviceBrand(request.getDeviceBrand());
        deviceInfo.setOperatingSystem(request.getOperatingSystem());
        deviceInfo.setOsVersion(request.getOsVersion());
        deviceInfo.setAppVersion(request.getAppVersion());
        deviceInfo.setLastActiveTime(LocalDateTime.now());

        String key = DEVICE_INFO_KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, deviceInfo);
    }

    /**
     * 设备信息类
     */
    @Data
    public static class DeviceInfo {
        private String deviceId;
        private String deviceName;
        private String deviceModel;
        private String deviceBrand;
        private String operatingSystem;
        private String osVersion;
        private String appVersion;
        private LocalDateTime lastActiveTime;
    }

    /**
     * 工具方法
     */
    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("SHA-256哈希计算异常", e);
            return input; // 降级处理
        }
    }

    private Long generateAccountId() {
        return System.currentTimeMillis();
    }

    // 以下方法需要具体实现，连接到数据库和其他服务

    private Account findAccountByDeviceId(String deviceId) {
        // 查询数据库
        return null;
    }

    private boolean validateDeviceFingerprint(String deviceId, String fingerprint) {
        // 验证设备指纹
        return true;
    }

    private Account findAccountById(Long accountId) {
        // 查询数据库
        return null;
    }

    private boolean isUsernameExists(String username) {
        // 查询数据库
        return false;
    }

    private boolean isMobileExists(String mobile) {
        // 查询数据库
        return false;
    }

    private String encryptPassword(String password) {
        // 密码加密
        return password;
    }

    private void saveAccount(Account account) {
        // 保存到数据库
    }

    private void bindDeviceToAccount(String deviceId, Long accountId, String fingerprint) {
        String key = DEVICE_ACCOUNT_KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, accountId);
    }

    private LoginSession createLoginSession(Account account, LoginContext context) {
        // 创建会话
        LoginSession session = new LoginSession();
        session.setSessionId(UUID.randomUUID().toString());
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