/*
 * 文件名: Account.java
 * 用途: 账号实体定义
 * 实现内容:
 *   - 账号核心信息定义
 *   - 账号唯一标识管理
 *   - 账号状态和类型管理
 *   - 账号安全等级设定
 *   - 账号时间信息记录
 * 技术选型:
 *   - JPA实体注解
 *   - MyBatis Plus实体
 *   - JSON序列化支持
 *   - 数据校验注解
 * 依赖关系:
 *   - 被各种服务层使用
 *   - 数据库映射实体
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.core;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 账号实体
 * <p>
 * 定义游戏账号的核心信息，包括唯一标识、状态管理、安全等级等。
 * 支持多种登录方式和账号类型，提供完整的账号生命周期管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("account")
public class Account {

    /**
     * 账号ID（主键）
     */
    @TableId(value = "account_id", type = IdType.ASSIGN_ID)
    private Long accountId;

    /**
     * 用户名（登录名）
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度必须在3-32个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @TableField("username")
    private String username;

    /**
     * 手机号
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @TableField("mobile")
    private String mobile;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @TableField("email")
    private String email;

    /**
     * 密码哈希（不返回给前端）
     */
    @JsonIgnore
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 账号状态
     */
    @TableField("status")
    private AccountStatus status;

    /**
     * 账号类型
     */
    @TableField("account_type")
    private AccountType accountType;

    /**
     * 安全等级
     */
    @TableField("security_level")
    private SecurityLevel securityLevel;

    /**
     * 昵称
     */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    @TableField("nickname")
    private String nickname;

    /**
     * 头像URL
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 性别（0-未知，1-男，2-女）
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 生日
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("birthday")
    private LocalDateTime birthday;

    /**
     * 注册IP
     */
    @TableField("register_ip")
    private String registerIp;

    /**
     * 注册时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 版本号（乐观锁）
     */
    @Version
    @TableField("version")
    private Integer version;

    /**
     * 账号状态枚举
     */
    public enum AccountStatus {
        /**
         * 正常
         */
        NORMAL(1, "正常"),
        
        /**
         * 冻结
         */
        FROZEN(2, "冻结"),
        
        /**
         * 封禁
         */
        BANNED(3, "封禁"),
        
        /**
         * 注销
         */
        CANCELLED(4, "注销");

        private final int code;
        private final String description;

        AccountStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static AccountStatus fromCode(int code) {
            for (AccountStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("未知的账号状态代码: " + code);
        }
    }

    /**
     * 账号类型枚举
     */
    public enum AccountType {
        /**
         * 普通账号
         */
        NORMAL(1, "普通账号"),
        
        /**
         * 游客账号
         */
        GUEST(2, "游客账号"),
        
        /**
         * 第三方账号
         */
        THIRD_PARTY(3, "第三方账号");

        private final int code;
        private final String description;

        AccountType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static AccountType fromCode(int code) {
            for (AccountType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("未知的账号类型代码: " + code);
        }
    }

    /**
     * 安全等级枚举
     */
    public enum SecurityLevel {
        /**
         * 低级
         */
        LOW(1, "低级"),
        
        /**
         * 中级
         */
        MEDIUM(2, "中级"),
        
        /**
         * 高级
         */
        HIGH(3, "高级"),
        
        /**
         * 专业级
         */
        PROFESSIONAL(4, "专业级");

        private final int code;
        private final String description;

        SecurityLevel(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static SecurityLevel fromCode(int code) {
            for (SecurityLevel level : values()) {
                if (level.code == code) {
                    return level;
                }
            }
            throw new IllegalArgumentException("未知的安全等级代码: " + code);
        }
    }

    /**
     * 检查账号是否正常
     */
    public boolean isNormal() {
        return AccountStatus.NORMAL.equals(this.status);
    }

    /**
     * 检查账号是否被冻结
     */
    public boolean isFrozen() {
        return AccountStatus.FROZEN.equals(this.status);
    }

    /**
     * 检查账号是否被封禁
     */
    public boolean isBanned() {
        return AccountStatus.BANNED.equals(this.status);
    }

    /**
     * 检查账号是否已注销
     */
    public boolean isCancelled() {
        return AccountStatus.CANCELLED.equals(this.status);
    }

    /**
     * 检查是否为游客账号
     */
    public boolean isGuest() {
        return AccountType.GUEST.equals(this.accountType);
    }

    /**
     * 检查是否为第三方账号
     */
    public boolean isThirdParty() {
        return AccountType.THIRD_PARTY.equals(this.accountType);
    }
}