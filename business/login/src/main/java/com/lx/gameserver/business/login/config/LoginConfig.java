/*
 * 文件名: LoginConfig.java
 * 用途: 登录配置
 * 实现内容:
 *   - 登录策略配置管理
 *   - Token配置参数
 *   - 安全配置参数
 *   - 限流配置参数
 *   - 防沉迷配置参数
 * 技术选型:
 *   - Spring Boot Configuration Properties
 *   - YAML配置文件
 *   - 配置验证注解
 *   - 环境变量支持
 * 依赖关系:
 *   - 被各个服务组件使用
 *   - 支持配置热更新
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * 登录配置
 * <p>
 * 统一管理登录相关的所有配置参数，支持从配置文件、环境变量等
 * 多种方式加载，提供类型安全的配置访问。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@Component
@ConfigurationProperties(prefix = "game.login")
@Validated
public class LoginConfig {

    /**
     * Token配置
     */
    @NotNull
    private TokenConfig token = new TokenConfig();

    /**
     * 安全配置
     */
    @NotNull
    private SecurityConfig security = new SecurityConfig();

    /**
     * 登录策略配置
     */
    @NotNull
    private StrategyConfig strategies = new StrategyConfig();

    /**
     * 防沉迷配置
     */
    @NotNull
    private AntiAddictionConfig antiAddiction = new AntiAddictionConfig();

    /**
     * 监控配置
     */
    @NotNull
    private MonitorConfig monitor = new MonitorConfig();

    /**
     * Token配置类
     */
    @Data
    public static class TokenConfig {
        /**
         * JWT密钥
         */
        @NotBlank
        private String jwtSecret = "your-secret-key-here";

        /**
         * 访问Token过期时间
         */
        @NotNull
        private Duration accessTokenExpire = Duration.ofHours(2);

        /**
         * 刷新Token过期时间
         */
        @NotNull
        private Duration refreshTokenExpire = Duration.ofDays(7);

        /**
         * 最大设备数量
         */
        @Min(1)
        private int maxDevices = 3;

        /**
         * Token签发者
         */
        private String issuer = "game-server";

        /**
         * Token受众
         */
        private String audience = "game-client";
    }

    /**
     * 安全配置类
     */
    @Data
    public static class SecurityConfig {
        /**
         * 最大登录尝试次数
         */
        @Min(1)
        private int maxLoginAttempts = 5;

        /**
         * 锁定持续时间
         */
        @NotNull
        private Duration lockDuration = Duration.ofMinutes(30);

        /**
         * 验证码触发阈值
         */
        @Min(1)
        private int captchaThreshold = 3;

        /**
         * IP限流配置
         */
        private String ipRateLimit = "10/m";

        /**
         * 设备限流配置
         */
        private String deviceRateLimit = "5/m";

        /**
         * 密码最小长度
         */
        @Min(6)
        private int passwordMinLength = 8;

        /**
         * 密码是否需要特殊字符
         */
        private boolean passwordRequireSpecialChar = true;

        /**
         * 是否启用异地登录检测
         */
        private boolean enableLocationCheck = true;

        /**
         * 是否启用设备指纹验证
         */
        private boolean enableDeviceFingerprint = true;
    }

    /**
     * 策略配置类
     */
    @Data
    public static class StrategyConfig {
        /**
         * 密码登录配置
         */
        @NotNull
        private PasswordStrategyConfig password = new PasswordStrategyConfig();

        /**
         * 手机登录配置
         */
        @NotNull
        private MobileStrategyConfig mobile = new MobileStrategyConfig();

        /**
         * 第三方登录配置
         */
        @NotNull
        private Map<String, ThirdPartyStrategyConfig> thirdParty = new HashMap<>();

        /**
         * 设备登录配置
         */
        @NotNull
        private DeviceStrategyConfig device = new DeviceStrategyConfig();

        /**
         * 生物识别登录配置
         */
        @NotNull
        private BiometricStrategyConfig biometric = new BiometricStrategyConfig();
    }

    /**
     * 密码策略配置
     */
    @Data
    public static class PasswordStrategyConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 密码最小长度
         */
        @Min(6)
        private int minLength = 8;

        /**
         * 是否需要特殊字符
         */
        private boolean requireSpecialChar = true;

        /**
         * 密码过期天数
         */
        private int passwordExpireDays = 90;
    }

    /**
     * 手机策略配置
     */
    @Data
    public static class MobileStrategyConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 短信验证码过期时间
         */
        @NotNull
        private Duration smsExpire = Duration.ofMinutes(5);

        /**
         * 每日发送限制
         */
        @Min(1)
        private int dailyLimit = 10;

        /**
         * 发送间隔
         */
        @NotNull
        private Duration sendInterval = Duration.ofMinutes(1);
    }

    /**
     * 第三方策略配置
     */
    @Data
    public static class ThirdPartyStrategyConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 应用ID
         */
        @NotBlank
        private String appId;

        /**
         * 应用密钥
         */
        @NotBlank
        private String appSecret;

        /**
         * 回调URL
         */
        private String redirectUri;

        /**
         * 授权范围
         */
        private String scope;
    }

    /**
     * 设备策略配置
     */
    @Data
    public static class DeviceStrategyConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 是否允许游客登录
         */
        private boolean allowGuest = true;

        /**
         * 游客账号过期时间
         */
        @NotNull
        private Duration guestExpire = Duration.ofDays(30);

        /**
         * 是否启用设备指纹验证
         */
        private boolean enableFingerprint = true;
    }

    /**
     * 生物识别策略配置
     */
    @Data
    public static class BiometricStrategyConfig {
        /**
         * 是否启用
         */
        private boolean enabled = false;

        /**
         * 相似度阈值
         */
        private double similarityThreshold = 0.8;

        /**
         * 最大失败次数
         */
        @Min(1)
        private int maxFailedAttempts = 3;

        /**
         * 是否启用活体检测
         */
        private boolean enableLivenessDetection = true;
    }

    /**
     * 防沉迷配置类
     */
    @Data
    public static class AntiAddictionConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 是否需要实名认证
         */
        private boolean realNameRequired = true;

        /**
         * 未成年人游戏时长限制（分钟）
         */
        @Min(0)
        private int minorPlayTime = 90;

        /**
         * 宵禁开始时间
         */
        private String minorCurfewStart = "22:00";

        /**
         * 宵禁结束时间
         */
        private String minorCurfewEnd = "08:00";

        /**
         * 充值限制配置
         */
        @NotNull
        private Map<String, Integer> rechargeLimit = new HashMap<>();
    }

    /**
     * 监控配置类
     */
    @Data
    public static class MonitorConfig {
        /**
         * 是否启用指标收集
         */
        private boolean metricsEnabled = true;

        /**
         * 慢登录阈值
         */
        @NotNull
        private Duration slowLoginThreshold = Duration.ofSeconds(1);

        /**
         * 告警Webhook URL
         */
        private String alertWebhookUrl;

        /**
         * 是否启用分布式追踪
         */
        private boolean tracingEnabled = true;

        /**
         * 采样率
         */
        private double samplingRate = 0.1;
    }

    /**
     * 获取第三方平台配置
     */
    public ThirdPartyStrategyConfig getThirdPartyConfig(String platform) {
        return strategies.getThirdParty().get(platform);
    }

    /**
     * 检查策略是否启用
     */
    public boolean isStrategyEnabled(String strategy) {
        switch (strategy.toLowerCase()) {
            case "password":
                return strategies.getPassword().isEnabled();
            case "mobile":
                return strategies.getMobile().isEnabled();
            case "device":
                return strategies.getDevice().isEnabled();
            case "biometric":
                return strategies.getBiometric().isEnabled();
            default:
                return false;
        }
    }
}