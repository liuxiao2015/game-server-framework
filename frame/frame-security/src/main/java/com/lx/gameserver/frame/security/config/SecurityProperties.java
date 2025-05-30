/*
 * 文件名: SecurityProperties.java
 * 用途: 游戏安全配置属性类
 * 实现内容:
 *   - 认证配置：JWT设置、多设备登录、登录尝试次数
 *   - 加密配置：加密算法、密钥大小、是否启用协议加密
 *   - 防护配置：限流设置、黑名单策略
 *   - 审计配置：审计日志存储方式、保留时间
 *   - 风控配置：风险检测阈值、自动处罚设置
 * 技术选型:
 *   - Spring Boot @ConfigurationProperties
 *   - 嵌套配置类结构
 *   - 类型安全的配置绑定
 * 依赖关系:
 *   - 被SecurityConfig使用
 *   - 配置各安全模块的行为
 */
package com.lx.gameserver.frame.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 游戏服务器安全配置属性
 * <p>
 * 提供安全模块的所有配置项，支持从配置文件加载和动态调整，
 * 包括认证、加密、防护、审计和风控等各个方面的配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@Component
@ConfigurationProperties(prefix = "game.security")
public class SecurityProperties {

    /**
     * 认证配置
     */
    @NestedConfigurationProperty
    private AuthProperties auth = new AuthProperties();

    /**
     * 加密配置
     */
    @NestedConfigurationProperty
    private CryptoProperties crypto = new CryptoProperties();

    /**
     * 防作弊配置
     */
    @NestedConfigurationProperty
    private AntiCheatProperties antiCheat = new AntiCheatProperties();

    /**
     * 限流配置
     */
    @NestedConfigurationProperty
    private RateLimitProperties rateLimit = new RateLimitProperties();

    /**
     * 审计配置
     */
    @NestedConfigurationProperty
    private AuditProperties audit = new AuditProperties();

    /**
     * 认证配置属性
     */
    @Data
    public static class AuthProperties {
        /**
         * JWT配置
         */
        private JwtProperties jwt = new JwtProperties();

        /**
         * 是否允许多设备同时登录
         */
        private boolean multiDeviceLogin = false;

        /**
         * 最大登录尝试次数
         */
        private int maxLoginAttempts = 5;

        /**
         * 账号锁定时长
         */
        private Duration lockDuration = Duration.ofMinutes(30);

        /**
         * JWT配置属性
         */
        @Data
        public static class JwtProperties {
            /**
             * JWT密钥，用于签名和验证
             */
            private String secret;

            /**
             * 访问令牌有效期
             */
            private Duration accessTokenValidity = Duration.ofHours(2);

            /**
             * 刷新令牌有效期
             */
            private Duration refreshTokenValidity = Duration.ofDays(7);
        }
    }

    /**
     * 加密配置属性
     */
    @Data
    public static class CryptoProperties {
        /**
         * 加密算法
         */
        private String algorithm = "AES256";

        /**
         * RSA密钥大小
         */
        private int rsaKeySize = 2048;

        /**
         * 是否启用协议加密
         */
        private boolean enableProtocolEncryption = true;
    }

    /**
     * 防作弊配置属性
     */
    @Data
    public static class AntiCheatProperties {
        /**
         * 是否启用防作弊系统
         */
        private boolean enable = true;

        /**
         * 作弊检测阈值
         */
        private double detectionThreshold = 0.8;

        /**
         * 是否自动封禁
         */
        private boolean autoBan = true;

        /**
         * 封禁时长
         */
        private Duration banDuration = Duration.ofHours(24);
    }

    /**
     * 限流配置属性
     */
    @Data
    public static class RateLimitProperties {
        /**
         * API请求限流(每秒请求数)
         */
        private int apiQps = 100;

        /**
         * 登录请求限流(每秒请求数)
         */
        private int loginQps = 10;

        /**
         * 是否启用分布式限流
         */
        private boolean enableDistributed = true;
    }

    /**
     * 审计配置属性
     */
    @Data
    public static class AuditProperties {
        /**
         * 是否启用审计
         */
        private boolean enable = true;

        /**
         * 审计存储方式（database, elasticsearch, file）
         */
        private String storage = "database";

        /**
         * 审计日志保留天数
         */
        private int retentionDays = 90;
    }
}