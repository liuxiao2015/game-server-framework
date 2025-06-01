/*
 * 文件名: ValidationUtils.java
 * 用途: 统一验证工具类 - 简化版
 * 实现内容:
 *   - 统一的参数验证功能
 *   - 支持多种验证类型
 *   - 标准化错误信息处理
 *   - 准备Java 21 Pattern Matching优化
 * 技术选型:
 *   - 纯Java实现，无外部依赖
 *   - 统一异常处理
 * 依赖关系:
 *   - 替代各模块分散的验证工具
 *   - 提供统一验证接口
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.common.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 统一验证工具类
 * <p>
 * 提供游戏服务器框架通用的验证功能，包括参数验证、
 * 格式验证、业务规则验证等。简化版实现，
 * 为升级到Java 21的新特性做准备。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
public final class ValidationUtils {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

    /**
     * 常用正则表达式模式
     */
    private static final Map<String, Pattern> PATTERNS = Map.of(
            "email", Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"),
            "phone", Pattern.compile("^1[3-9]\\d{9}$"),
            "username", Pattern.compile("^[a-zA-Z0-9_]{3,20}$"),
            "password", Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d@$!%*?&]{8,20}$"),
            "playerId", Pattern.compile("^[1-9]\\d{6,18}$"),
            "ipv4", Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
    );

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private ValidationUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ===== 基础验证方法 =====

    /**
     * 验证字符串是否为空或空白
     * <p>
     * Java 17兼容实现，使用传统的条件判断
     * 在Java 21环境下可以使用Pattern Matching优化
     * </p>
     *
     * @param value 待验证字符串
     * @param name  字段名称
     * @return 验证结果
     */
    public static ValidationResult validateNotBlank(String value, String name) {
        if (value == null) {
            return ValidationResult.failure(name + "不能为空");
        }
        if (value.isBlank()) {
            return ValidationResult.failure(name + "不能为空白");
        }
        return ValidationResult.success();
    }

    /**
     * 验证对象是否为空
     *
     * @param value 待验证对象
     * @param name  字段名称
     * @return 验证结果
     */
    public static ValidationResult validateNotNull(Object value, String name) {
        return value == null ? 
                ValidationResult.failure(name + "不能为空") : 
                ValidationResult.success();
    }

    /**
     * 验证数值范围
     *
     * @param value 待验证数值
     * @param min   最小值（包含）
     * @param max   最大值（包含）
     * @param name  字段名称
     * @return 验证结果
     */
    public static ValidationResult validateRange(Number value, Number min, Number max, String name) {
        if (value == null) {
            return ValidationResult.failure(name + "不能为空");
        }

        double val = value.doubleValue();
        double minVal = min.doubleValue();
        double maxVal = max.doubleValue();

        if (val < minVal || val > maxVal) {
            return ValidationResult.failure(String.format("%s必须在%s到%s之间", name, min, max));
        }

        return ValidationResult.success();
    }

    /**
     * 验证字符串长度
     *
     * @param value     待验证字符串
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @param name      字段名称
     * @return 验证结果
     */
    public static ValidationResult validateLength(String value, int minLength, int maxLength, String name) {
        if (value == null) {
            return ValidationResult.failure(name + "不能为空");
        }

        int length = value.length();
        if (length < minLength || length > maxLength) {
            return ValidationResult.failure(String.format("%s长度必须在%d到%d之间", name, minLength, maxLength));
        }

        return ValidationResult.success();
    }

    // ===== 格式验证方法 =====

    /**
     * 验证正则表达式模式
     *
     * @param value   待验证字符串
     * @param pattern 正则表达式模式
     * @param name    字段名称
     * @return 验证结果
     */
    public static ValidationResult validatePattern(String value, Pattern pattern, String name) {
        if (value == null) {
            return ValidationResult.failure(name + "不能为空");
        }

        if (!pattern.matcher(value).matches()) {
            return ValidationResult.failure(name + "格式不正确");
        }

        return ValidationResult.success();
    }

    /**
     * 验证预定义格式
     *
     * @param value      待验证字符串
     * @param formatType 格式类型
     * @param name       字段名称
     * @return 验证结果
     */
    public static ValidationResult validateFormat(String value, String formatType, String name) {
        Pattern pattern = PATTERNS.get(formatType.toLowerCase());
        if (pattern == null) {
            return ValidationResult.failure("不支持的格式类型: " + formatType);
        }

        return validatePattern(value, pattern, name);
    }

    /**
     * 验证邮箱格式
     *
     * @param email 邮箱地址
     * @return 验证结果
     */
    public static ValidationResult validateEmail(String email) {
        return validateFormat(email, "email", "邮箱地址");
    }

    /**
     * 验证手机号格式
     *
     * @param phone 手机号
     * @return 验证结果
     */
    public static ValidationResult validatePhone(String phone) {
        return validateFormat(phone, "phone", "手机号");
    }

    /**
     * 验证用户名格式
     *
     * @param username 用户名
     * @return 验证结果
     */
    public static ValidationResult validateUsername(String username) {
        return validateFormat(username, "username", "用户名");
    }

    /**
     * 验证密码强度
     *
     * @param password 密码
     * @return 验证结果
     */
    public static ValidationResult validatePassword(String password) {
        return validateFormat(password, "password", "密码");
    }

    /**
     * 验证玩家ID格式
     *
     * @param playerId 玩家ID
     * @return 验证结果
     */
    public static ValidationResult validatePlayerId(String playerId) {
        return validateFormat(playerId, "playerId", "玩家ID");
    }

    // ===== 业务验证方法 =====

    /**
     * 验证集合不为空且不包含空元素
     *
     * @param collection 集合
     * @param name       字段名称
     * @return 验证结果
     */
    public static ValidationResult validateCollectionNotEmpty(Collection<?> collection, String name) {
        if (collection == null || collection.isEmpty()) {
            return ValidationResult.failure(name + "不能为空");
        }

        if (collection.stream().anyMatch(Objects::isNull)) {
            return ValidationResult.failure(name + "不能包含空元素");
        }

        return ValidationResult.success();
    }

    /**
     * 验证Map不为空且不包含空键值
     *
     * @param map  Map对象
     * @param name 字段名称
     * @return 验证结果
     */
    public static ValidationResult validateMapNotEmpty(Map<?, ?> map, String name) {
        if (map == null || map.isEmpty()) {
            return ValidationResult.failure(name + "不能为空");
        }

        if (map.containsKey(null) || map.containsValue(null)) {
            return ValidationResult.failure(name + "不能包含空键或空值");
        }

        return ValidationResult.success();
    }

    // ===== 组合验证方法 =====

    /**
     * 组合多个验证结果
     *
     * @param results 验证结果数组
     * @return 组合后的验证结果
     */
    public static ValidationResult combine(ValidationResult... results) {
        List<String> allErrors = new ArrayList<>();
        
        for (ValidationResult result : results) {
            if (!result.isValid()) {
                allErrors.addAll(result.getErrors());
            }
        }

        return allErrors.isEmpty() ? 
                ValidationResult.success() : 
                ValidationResult.failure(allErrors);
    }

    /**
     * 验证结果类 - 使用常规类实现，准备升级到Record
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final List<String> errors;

        private ValidationResult(boolean isValid, List<String> errors) {
            this.isValid = isValid;
            this.errors = new ArrayList<>(errors);
        }
        
        /**
         * 创建成功的验证结果
         *
         * @return 成功的验证结果
         */
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        /**
         * 创建失败的验证结果
         *
         * @param error 错误信息
         * @return 失败的验证结果
         */
        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error));
        }

        /**
         * 创建失败的验证结果
         *
         * @param errors 错误信息列表
         * @return 失败的验证结果
         */
        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, new ArrayList<>(errors));
        }

        /**
         * 获取验证是否成功
         *
         * @return 验证是否成功
         */
        public boolean isValid() {
            return isValid;
        }

        /**
         * 获取错误信息列表
         *
         * @return 错误信息列表
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * 获取第一个错误信息
         *
         * @return 第一个错误信息，如果没有错误则返回null
         */
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        /**
         * 获取所有错误信息的字符串表示
         *
         * @return 错误信息字符串
         */
        public String getErrorMessage() {
            return String.join("; ", errors);
        }

        /**
         * 抛出验证异常（如果验证失败）
         *
         * @throws ValidationException 验证异常
         */
        public void throwIfInvalid() throws ValidationException {
            if (!isValid) {
                throw new ValidationException(getErrorMessage());
            }
        }
    }

    /**
     * 验证异常
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}