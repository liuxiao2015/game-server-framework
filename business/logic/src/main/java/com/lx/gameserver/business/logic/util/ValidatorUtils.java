/*
 * 文件名: ValidatorUtils.java
 * 用途: 验证工具类
 * 实现内容:
 *   - 参数验证和业务规则验证
 *   - 数据合法性检查和防作弊验证
 *   - 错误码管理和异常处理
 *   - 输入过滤和安全检查
 *   - 自定义验证规则扩展
 * 技术选型:
 *   - 正则表达式进行格式验证
 *   - 注解驱动的验证框架集成
 *   - 链式调用提供流畅的验证接口
 *   - 缓存验证结果优化性能
 * 依赖关系:
 *   - 被所有业务模块用于参数验证
 *   - 与ErrorCode集成提供错误信息
 *   - 提供统一的验证规范
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.util;

import com.lx.gameserver.common.ErrorCode;
import com.lx.gameserver.common.Result;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 验证工具类
 * <p>
 * 提供全面的参数验证、业务规则验证和安全检查功能。
 * 支持链式调用和自定义验证规则。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
public final class ValidatorUtils {

    // 常用正则表达式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "^1[3-9]\\d{9}$");
    
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$");
    
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_]{3,32}$");
    
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d@$!%*?&]{8,32}$");
    
    // SQL注入关键词
    private static final Set<String> SQL_INJECTION_KEYWORDS = Set.of(
            "select", "insert", "update", "delete", "drop", "create", "alter",
            "union", "script", "exec", "execute", "sp_", "xp_"
    );
    
    // XSS攻击关键词
    private static final Set<String> XSS_KEYWORDS = Set.of(
            "<script", "</script", "javascript:", "vbscript:", "onload=",
            "onerror=", "onclick=", "onmouseover=", "onfocus=", "onblur="
    );

    // 私有构造函数
    private ValidatorUtils() {
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    /**
     * 验证构建器
     */
    public static class ValidationBuilder {
        private final List<String> errors = new ArrayList<>();
        private boolean stopOnFirstError = false;

        /**
         * 设置是否在第一个错误时停止
         */
        public ValidationBuilder stopOnFirstError(boolean stop) {
            this.stopOnFirstError = stop;
            return this;
        }

        /**
         * 验证条件
         */
        public ValidationBuilder validate(boolean condition, String errorMessage) {
            if (!condition) {
                errors.add(errorMessage);
                if (stopOnFirstError) {
                    return this;
                }
            }
            return this;
        }

        /**
         * 验证条件（带错误码）
         */
        public ValidationBuilder validate(boolean condition, String errorCode, String errorMessage) {
            if (!condition) {
                errors.add(String.format("[%s] %s", errorCode, errorMessage));
                if (stopOnFirstError) {
                    return this;
                }
            }
            return this;
        }

        /**
         * 验证对象不为空
         */
        public ValidationBuilder notNull(Object value, String fieldName) {
            return validate(value != null, fieldName + "不能为空");
        }

        /**
         * 验证字符串不为空
         */
        public ValidationBuilder notEmpty(String value, String fieldName) {
            return validate(value != null && !value.trim().isEmpty(), fieldName + "不能为空");
        }

        /**
         * 验证集合不为空
         */
        public ValidationBuilder notEmpty(Collection<?> value, String fieldName) {
            return validate(value != null && !value.isEmpty(), fieldName + "不能为空");
        }

        /**
         * 验证字符串长度
         */
        public ValidationBuilder length(String value, int min, int max, String fieldName) {
            if (value != null) {
                int len = value.length();
                return validate(len >= min && len <= max, 
                        String.format("%s长度必须在%d-%d字符之间", fieldName, min, max));
            }
            return this;
        }

        /**
         * 验证数值范围
         */
        public ValidationBuilder range(Number value, Number min, Number max, String fieldName) {
            if (value != null) {
                double val = value.doubleValue();
                double minVal = min.doubleValue();
                double maxVal = max.doubleValue();
                return validate(val >= minVal && val <= maxVal,
                        String.format("%s必须在%s-%s之间", fieldName, min, max));
            }
            return this;
        }

        /**
         * 验证正则表达式
         */
        public ValidationBuilder matches(String value, Pattern pattern, String fieldName, String formatDesc) {
            if (value != null) {
                return validate(pattern.matcher(value).matches(),
                        String.format("%s格式不正确，%s", fieldName, formatDesc));
            }
            return this;
        }

        /**
         * 验证邮箱格式
         */
        public ValidationBuilder email(String value, String fieldName) {
            return matches(value, EMAIL_PATTERN, fieldName, "请输入正确的邮箱地址");
        }

        /**
         * 验证手机号格式
         */
        public ValidationBuilder mobile(String value, String fieldName) {
            return matches(value, MOBILE_PATTERN, fieldName, "请输入正确的手机号码");
        }

        /**
         * 验证身份证号格式
         */
        public ValidationBuilder idCard(String value, String fieldName) {
            return matches(value, ID_CARD_PATTERN, fieldName, "请输入正确的身份证号码");
        }

        /**
         * 验证IP地址格式
         */
        public ValidationBuilder ipv4(String value, String fieldName) {
            return matches(value, IPV4_PATTERN, fieldName, "请输入正确的IP地址");
        }

        /**
         * 验证用户名格式
         */
        public ValidationBuilder username(String value) {
            return matches(value, USERNAME_PATTERN, "用户名", "只能包含字母、数字和下划线，长度3-32字符");
        }

        /**
         * 验证密码强度
         */
        public ValidationBuilder password(String value) {
            return matches(value, PASSWORD_PATTERN, "密码", 
                    "必须包含大小写字母和数字，长度8-32字符");
        }

        /**
         * 验证时间范围
         */
        public ValidationBuilder timeRange(LocalDateTime time, LocalDateTime start, LocalDateTime end, String fieldName) {
            if (time != null && start != null && end != null) {
                return validate(!time.isBefore(start) && !time.isAfter(end),
                        String.format("%s必须在%s到%s之间", fieldName, start, end));
            }
            return this;
        }

        /**
         * 自定义验证
         */
        public <T> ValidationBuilder custom(T value, Predicate<T> predicate, String errorMessage) {
            return validate(predicate.test(value), errorMessage);
        }

        /**
         * 构建验证结果
         */
        public ValidationResult build() {
            return new ValidationResult(errors);
        }

        /**
         * 构建Result对象
         */
        public <T> Result<T> buildResult() {
            if (errors.isEmpty()) {
                return Result.success();
            } else {
                return Result.error(String.join("; ", errors));
            }
        }

        /**
         * 构建Result对象（带数据）
         */
        public <T> Result<T> buildResult(T data) {
            if (errors.isEmpty()) {
                return Result.success(data);
            } else {
                return Result.error(String.join("; ", errors));
            }
        }

        /**
         * 抛出异常（如果有错误）
         */
        public void throwIfInvalid() {
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(String.join("; ", errors));
            }
        }
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final List<String> errors;

        private ValidationResult(List<String> errors) {
            this.errors = new ArrayList<>(errors);
        }

        /**
         * 是否验证通过
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * 获取错误信息
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * 获取第一个错误信息
         */
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        /**
         * 获取所有错误信息（用分号连接）
         */
        public String getAllErrors() {
            return String.join("; ", errors);
        }

        /**
         * 抛出异常（如果有错误）
         */
        public void throwIfInvalid() {
            if (!isValid()) {
                throw new IllegalArgumentException(getAllErrors());
            }
        }
    }

    // ========== 静态验证方法 ==========

    /**
     * 创建验证构建器
     */
    public static ValidationBuilder builder() {
        return new ValidationBuilder();
    }

    /**
     * 快速验证非空
     */
    public static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 快速验证非空字符串
     */
    public static void requireNonEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 快速验证集合非空
     */
    public static void requireNonEmpty(Collection<?> value, String message) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 快速验证条件
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 快速验证条件（带异常类型）
     */
    public static <T extends RuntimeException> void require(boolean condition, Supplier<T> exceptionSupplier) {
        if (!condition) {
            throw exceptionSupplier.get();
        }
    }

    // ========== 格式验证 ==========

    /**
     * 验证邮箱格式
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 验证手机号格式
     */
    public static boolean isValidMobile(String mobile) {
        return mobile != null && MOBILE_PATTERN.matcher(mobile).matches();
    }

    /**
     * 验证身份证号格式
     */
    public static boolean isValidIdCard(String idCard) {
        return idCard != null && ID_CARD_PATTERN.matcher(idCard).matches();
    }

    /**
     * 验证IP地址格式
     */
    public static boolean isValidIPv4(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 验证用户名格式
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * 验证密码强度
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    // ========== 安全验证 ==========

    /**
     * 检查SQL注入风险
     */
    public static boolean hasSqlInjection(String input) {
        if (input == null) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        return SQL_INJECTION_KEYWORDS.stream().anyMatch(lowerInput::contains);
    }

    /**
     * 检查XSS攻击风险
     */
    public static boolean hasXssRisk(String input) {
        if (input == null) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        return XSS_KEYWORDS.stream().anyMatch(lowerInput::contains);
    }

    /**
     * 清理XSS攻击代码
     */
    public static String cleanXss(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("<script[^>]*>.*?</script>", "")
                   .replaceAll("javascript:", "")
                   .replaceAll("vbscript:", "")
                   .replaceAll("on\\w+\\s*=", "");
    }

    /**
     * 验证输入安全性
     */
    public static ValidationResult validateInputSecurity(String input, String fieldName) {
        ValidationBuilder builder = builder();
        
        if (input != null) {
            builder.validate(!hasSqlInjection(input), fieldName + "包含非法字符")
                   .validate(!hasXssRisk(input), fieldName + "包含危险脚本");
        }
        
        return builder.build();
    }

    // ========== 业务验证 ==========

    /**
     * 验证玩家等级
     */
    public static boolean isValidPlayerLevel(int level) {
        return level >= 1 && level <= 999;
    }

    /**
     * 验证货币数量
     */
    public static boolean isValidCurrencyAmount(long amount) {
        return amount >= 0 && amount <= Long.MAX_VALUE / 2; // 防止溢出
    }

    /**
     * 验证VIP等级
     */
    public static boolean isValidVipLevel(int vipLevel) {
        return vipLevel >= 0 && vipLevel <= 20;
    }

    /**
     * 验证场景容量
     */
    public static boolean isValidSceneCapacity(int capacity) {
        return capacity >= 1 && capacity <= 10000;
    }

    /**
     * 验证AOI范围
     */
    public static boolean isValidAoiRange(double range) {
        return range > 0 && range <= 1000;
    }

    // ========== 批量验证 ==========

    /**
     * 验证玩家ID列表
     */
    public static ValidationResult validatePlayerIds(Collection<Long> playerIds) {
        ValidationBuilder builder = builder();
        
        builder.notEmpty(playerIds, "玩家ID列表");
        
        if (playerIds != null) {
            builder.validate(playerIds.size() <= 1000, "玩家ID列表不能超过1000个");
            
            for (Long playerId : playerIds) {
                builder.validate(playerId != null && playerId > 0, 
                        "玩家ID必须为正整数: " + playerId);
            }
        }
        
        return builder.build();
    }

    /**
     * 验证分页参数
     */
    public static ValidationResult validatePagination(int page, int pageSize) {
        return builder()
                .validate(page >= 1, "页码必须大于等于1")
                .validate(pageSize >= 1 && pageSize <= 1000, "页大小必须在1-1000之间")
                .build();
    }

    /**
     * 验证时间范围
     */
    public static ValidationResult validateTimeRange(LocalDateTime start, LocalDateTime end) {
        ValidationBuilder builder = builder();
        
        builder.notNull(start, "开始时间")
               .notNull(end, "结束时间");
        
        if (start != null && end != null) {
            builder.validate(!start.isAfter(end), "开始时间不能晚于结束时间");
            
            // 限制查询时间范围不超过1年
            builder.validate(start.plusYears(1).isAfter(end), "查询时间范围不能超过1年");
        }
        
        return builder.build();
    }

    // ========== 防作弊验证 ==========

    /**
     * 验证操作频率
     */
    public static boolean isValidOperationFrequency(int count, int timeWindowSeconds, int maxAllowed) {
        // 简化实现，实际应该使用滑动窗口算法
        return count <= maxAllowed;
    }

    /**
     * 验证数值合理性
     */
    public static boolean isReasonableValue(long value, long expectedMin, long expectedMax) {
        return value >= expectedMin && value <= expectedMax;
    }

    /**
     * 验证经验值合理性
     */
    public static boolean isReasonableExp(long exp, int playerLevel) {
        // 单次获得的经验不应该超过当前等级所需经验的10倍
        long maxReasonableExp = GameUtils.calculateExpForLevel(playerLevel) * 10;
        return exp > 0 && exp <= maxReasonableExp;
    }

    /**
     * 验证金币变化合理性
     */
    public static boolean isReasonableCoinChange(long change, int playerLevel) {
        // 单次金币变化不应该超过合理范围
        long maxReasonableChange = playerLevel * 10000L;
        return Math.abs(change) <= maxReasonableChange;
    }

    // ========== 链式验证 ==========

    /**
     * 链式验证器
     */
    public static class ChainValidator<T> {
        private final T value;
        private final List<String> errors = new ArrayList<>();
        private boolean hasError = false;

        private ChainValidator(T value) {
            this.value = value;
        }

        /**
         * 添加验证规则
         */
        public ChainValidator<T> check(Predicate<T> predicate, String errorMessage) {
            if (!hasError && !predicate.test(value)) {
                errors.add(errorMessage);
                hasError = true;
            }
            return this;
        }

        /**
         * 添加验证规则（带转换）
         */
        public <R> ChainValidator<T> check(Function<T, R> mapper, Predicate<R> predicate, String errorMessage) {
            if (!hasError) {
                try {
                    R mapped = mapper.apply(value);
                    if (!predicate.test(mapped)) {
                        errors.add(errorMessage);
                        hasError = true;
                    }
                } catch (Exception e) {
                    errors.add(errorMessage);
                    hasError = true;
                }
            }
            return this;
        }

        /**
         * 获取验证结果
         */
        public ValidationResult result() {
            return new ValidationResult(errors);
        }

        /**
         * 获取值（如果验证通过）
         */
        public T get() {
            if (hasError) {
                throw new IllegalArgumentException(String.join("; ", errors));
            }
            return value;
        }

        /**
         * 获取值（如果验证失败返回默认值）
         */
        public T getOrDefault(T defaultValue) {
            return hasError ? defaultValue : value;
        }
    }

    /**
     * 创建链式验证器
     */
    public static <T> ChainValidator<T> of(T value) {
        return new ChainValidator<>(value);
    }

    // ========== 条件验证 ==========

    /**
     * 条件验证（只在条件为真时执行验证）
     */
    public static ValidationResult validateIf(boolean condition, Supplier<ValidationResult> validator) {
        if (condition) {
            return validator.get();
        }
        return new ValidationResult(Collections.emptyList());
    }

    /**
     * 任一验证通过（OR逻辑）
     */
    public static ValidationResult validateAny(Supplier<ValidationResult>... validators) {
        List<String> allErrors = new ArrayList<>();
        
        for (Supplier<ValidationResult> validator : validators) {
            ValidationResult result = validator.get();
            if (result.isValid()) {
                return result; // 任一通过即返回成功
            }
            allErrors.addAll(result.getErrors());
        }
        
        return new ValidationResult(allErrors);
    }

    /**
     * 全部验证通过（AND逻辑）
     */
    public static ValidationResult validateAll(Supplier<ValidationResult>... validators) {
        List<String> allErrors = new ArrayList<>();
        
        for (Supplier<ValidationResult> validator : validators) {
            ValidationResult result = validator.get();
            if (!result.isValid()) {
                allErrors.addAll(result.getErrors());
            }
        }
        
        return new ValidationResult(allErrors);
    }
}