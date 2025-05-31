/*
 * 文件名: LoginSession.java
 * 用途: 登录会话实体
 * 实现内容:
 *   - 登录会话信息定义
 *   - 会话ID全局唯一性管理
 *   - Token信息存储和管理
 *   - 设备信息记录和验证
 *   - 会话状态生命周期管理
 * 技术选型:
 *   - Redis存储支持
 *   - JSON序列化
 *   - 过期时间管理
 *   - 分布式会话
 * 依赖关系:
 *   - 被SessionManager管理
 *   - 关联Account实体
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 登录会话实体
 * <p>
 * 表示一个用户的登录会话信息，包含会话ID、Token、设备信息、
 * 登录位置等关键信息，支持多设备登录和会话状态管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LoginSession {

    /**
     * 会话ID（全局唯一）
     */
    private String sessionId;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 访问Token
     */
    private String accessToken;

    /**
     * 刷新Token
     */
    private String refreshToken;

    /**
     * Token类型（通常是Bearer）
     */
    private String tokenType = "Bearer";

    /**
     * Token过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    /**
     * 刷新Token过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refreshExpiresAt;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备类型
     */
    private DeviceType deviceType;

    /**
     * 设备名称
     */
    private String deviceName;

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
     * 登录IP地址
     */
    private String loginIp;

    /**
     * 登录地理位置
     */
    private String loginLocation;

    /**
     * 登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;

    /**
     * 最后活跃时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;

    /**
     * 会话状态
     */
    private SessionStatus status;

    /**
     * 登录方式
     */
    private String loginMethod;

    /**
     * 用户代理信息
     */
    private String userAgent;

    /**
     * 是否记住登录
     */
    private Boolean rememberMe;

    /**
     * 会话扩展属性
     */
    private Map<String, Object> attributes;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        /**
         * PC端
         */
        PC(1, "PC"),
        
        /**
         * 手机端
         */
        MOBILE(2, "Mobile"),
        
        /**
         * 平板端
         */
        TABLET(3, "Tablet"),
        
        /**
         * Web端
         */
        WEB(4, "Web"),
        
        /**
         * 其他
         */
        OTHER(99, "Other");

        private final int code;
        private final String description;

        DeviceType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static DeviceType fromCode(int code) {
            for (DeviceType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return OTHER;
        }

        public static DeviceType fromUserAgent(String userAgent) {
            if (userAgent == null || userAgent.isEmpty()) {
                return OTHER;
            }
            
            String lowerUA = userAgent.toLowerCase();
            if (lowerUA.contains("mobile") || lowerUA.contains("android") || lowerUA.contains("iphone")) {
                return MOBILE;
            } else if (lowerUA.contains("tablet") || lowerUA.contains("ipad")) {
                return TABLET;
            } else if (lowerUA.contains("mozilla") || lowerUA.contains("chrome") || lowerUA.contains("firefox")) {
                return WEB;
            } else {
                return PC;
            }
        }
    }

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        /**
         * 活跃
         */
        ACTIVE(1, "活跃"),
        
        /**
         * 过期
         */
        EXPIRED(2, "过期"),
        
        /**
         * 注销
         */
        LOGOUT(3, "注销"),
        
        /**
         * 被踢出
         */
        KICKED(4, "被踢出"),
        
        /**
         * 异常
         */
        ABNORMAL(5, "异常");

        private final int code;
        private final String description;

        SessionStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static SessionStatus fromCode(int code) {
            for (SessionStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("未知的会话状态代码: " + code);
        }
    }

    /**
     * 检查会话是否活跃
     */
    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(this.status);
    }

    /**
     * 检查会话是否过期
     */
    public boolean isExpired() {
        return SessionStatus.EXPIRED.equals(this.status) || 
               (expiresAt != null && LocalDateTime.now().isAfter(expiresAt));
    }

    /**
     * 检查刷新Token是否过期
     */
    public boolean isRefreshTokenExpired() {
        return refreshExpiresAt != null && LocalDateTime.now().isAfter(refreshExpiresAt);
    }

    /**
     * 检查是否需要刷新Token
     */
    public boolean needRefresh() {
        if (expiresAt == null) {
            return false;
        }
        // 如果距离过期时间少于30分钟，则需要刷新
        return LocalDateTime.now().plusMinutes(30).isAfter(expiresAt);
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 标记会话为过期
     */
    public void markExpired() {
        this.status = SessionStatus.EXPIRED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 标记会话为注销
     */
    public void markLogout() {
        this.status = SessionStatus.LOGOUT;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 标记会话为被踢出
     */
    public void markKicked() {
        this.status = SessionStatus.KICKED;
        this.updateTime = LocalDateTime.now();
    }
}