/*
 * 文件名: Sensitive.java
 * 用途: 数据脱敏注解
 * 实现内容:
 *   - 标记需要脱敏的字段
 *   - 定义脱敏类型和策略
 *   - 支持自定义脱敏规则
 *   - 查询时自动脱敏，写入时保持原值
 * 技术选型:
 *   - Java注解机制
 *   - 枚举定义脱敏类型
 *   - 反射和AOP处理
 * 依赖关系:
 *   - 被数据脱敏处理器使用
 *   - 配合实体类字段使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据脱敏注解
 * <p>
 * 用于标记需要进行数据脱敏的字段，支持多种脱敏类型和策略。
 * 在数据查询时自动对敏感信息进行脱敏处理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏类型
     */
    SensitiveType type();

    /**
     * 自定义脱敏规则（当type为CUSTOM时使用）
     */
    String customRule() default "";

    /**
     * 是否启用脱敏（默认启用）
     */
    boolean enabled() default true;

    /**
     * 脱敏类型枚举
     */
    enum SensitiveType {
        /**
         * 手机号脱敏：保留前3位和后4位，中间用*代替
         * 示例：138****1234
         */
        MOBILE_PHONE,

        /**
         * 身份证号脱敏：保留前6位和后4位，中间用*代替
         * 示例：123456********1234
         */
        ID_CARD,

        /**
         * 银行卡号脱敏：保留前4位和后4位，中间用*代替
         * 示例：1234 **** **** 5678
         */
        BANK_CARD,

        /**
         * 邮箱地址脱敏：保留@前的第一个字符和@后的域名
         * 示例：u***@example.com
         */
        EMAIL,

        /**
         * 姓名脱敏：保留第一个字符，其余用*代替
         * 示例：张**
         */
        NAME,

        /**
         * 地址脱敏：保留前6个字符，其余用*代替
         * 示例：北京市朝阳区***
         */
        ADDRESS,

        /**
         * 密码脱敏：全部用*代替
         * 示例：******
         */
        PASSWORD,

        /**
         * IP地址脱敏：保留前两段，后两段用*代替
         * 示例：192.168.*.*
         */
        IP_ADDRESS,

        /**
         * 自定义脱敏规则
         */
        CUSTOM
    }
}