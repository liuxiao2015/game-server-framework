/*
 * 文件名: GameUtils.java
 * 用途: 游戏工具类
 * 实现内容:
 *   - 随机数生成和概率计算工具
 *   - 时间处理和距离计算工具
 *   - 游戏公式计算和数值处理
 *   - 字符串处理和数据格式化
 *   - 常用算法和数学计算
 * 技术选型:
 *   - ThreadLocalRandom实现线程安全的随机数生成
 *   - BigDecimal保证精确的数值计算
 *   - 缓存机制优化重复计算
 *   - 静态方法提供便捷的工具接口
 * 依赖关系:
 *   - 被所有业务模块广泛使用
 *   - 提供基础的数学和算法支持
 *   - 独立的工具类，无外部依赖
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 游戏工具类
 * <p>
 * 提供游戏开发中常用的工具方法，包括随机数生成、概率计算、
 * 时间处理、距离计算、公式计算等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
public final class GameUtils {

    /** 时间格式化器 */
    private static final DateTimeFormatter DEFAULT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /** 用户名验证正则 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    
    /** 昵称验证正则 */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9_]{1,64}$");
    
    /** 缓存随机数生成器 */
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    
    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 私有构造函数，防止实例化
    private GameUtils() {
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    // ========== 随机数相关 ==========

    /**
     * 生成随机整数 [min, max]
     */
    public static int randomInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("最小值不能大于最大值");
        }
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 生成随机长整数 [min, max]
     */
    public static long randomLong(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("最小值不能大于最大值");
        }
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /**
     * 生成随机浮点数 [min, max)
     */
    public static double randomDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("最小值不能大于最大值");
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * 生成随机布尔值
     */
    public static boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * 按概率返回布尔值
     *
     * @param probability 概率 (0.0 - 1.0)
     */
    public static boolean randomByProbability(double probability) {
        if (probability <= 0.0) {
            return false;
        }
        if (probability >= 1.0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /**
     * 按概率返回布尔值
     *
     * @param probability 概率 (0 - 10000，表示万分比)
     */
    public static boolean randomByProbability(int probability) {
        if (probability <= 0) {
            return false;
        }
        if (probability >= 10000) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(10000) < probability;
    }

    /**
     * 从列表中随机选择一个元素
     */
    public static <T> T randomChoice(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /**
     * 从数组中随机选择一个元素
     */
    @SafeVarargs
    public static <T> T randomChoice(T... array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    /**
     * 按权重随机选择
     *
     * @param items   选项列表
     * @param weights 权重列表
     */
    public static <T> T randomChoiceByWeight(List<T> items, List<Integer> weights) {
        if (items == null || weights == null || items.size() != weights.size() || items.isEmpty()) {
            return null;
        }

        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return null;
        }

        int randomValue = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (int i = 0; i < items.size(); i++) {
            currentWeight += weights.get(i);
            if (randomValue < currentWeight) {
                return items.get(i);
            }
        }

        return items.get(items.size() - 1);
    }

    /**
     * 生成随机字符串
     *
     * @param length 长度
     * @param chars  字符集
     */
    public static String randomString(int length, String chars) {
        if (length <= 0 || chars == null || chars.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成随机字母数字字符串
     */
    public static String randomAlphanumeric(int length) {
        return randomString(length, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
    }

    /**
     * 生成安全的随机字符串（用于密码、令牌等）
     */
    public static String secureRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ========== 数值计算相关 ==========

    /**
     * 精确加法
     */
    public static double add(double v1, double v2) {
        BigDecimal b1 = BigDecimal.valueOf(v1);
        BigDecimal b2 = BigDecimal.valueOf(v2);
        return b1.add(b2).doubleValue();
    }

    /**
     * 精确减法
     */
    public static double subtract(double v1, double v2) {
        BigDecimal b1 = BigDecimal.valueOf(v1);
        BigDecimal b2 = BigDecimal.valueOf(v2);
        return b1.subtract(b2).doubleValue();
    }

    /**
     * 精确乘法
     */
    public static double multiply(double v1, double v2) {
        BigDecimal b1 = BigDecimal.valueOf(v1);
        BigDecimal b2 = BigDecimal.valueOf(v2);
        return b1.multiply(b2).doubleValue();
    }

    /**
     * 精确除法
     */
    public static double divide(double v1, double v2, int scale) {
        if (v2 == 0) {
            throw new IllegalArgumentException("除数不能为0");
        }
        BigDecimal b1 = BigDecimal.valueOf(v1);
        BigDecimal b2 = BigDecimal.valueOf(v2);
        return b1.divide(b2, scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 四舍五入
     */
    public static double round(double value, int scale) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 限制数值范围
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 限制数值范围
     */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 限制数值范围
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 线性插值
     */
    public static double lerp(double start, double end, double t) {
        return start + (end - start) * clamp(t, 0.0, 1.0);
    }

    /**
     * 计算百分比
     */
    public static double percentage(double part, double total) {
        if (total == 0) {
            return 0.0;
        }
        return (part / total) * 100.0;
    }

    // ========== 距离和几何计算 ==========

    /**
     * 计算2D距离
     */
    public static double distance2D(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 计算3D距离
     */
    public static double distance3D(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 计算曼哈顿距离
     */
    public static double manhattanDistance(double x1, double y1, double x2, double y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1);
    }

    /**
     * 检查点是否在矩形内
     */
    public static boolean isPointInRectangle(double px, double py, double rx, double ry, double width, double height) {
        return px >= rx && px <= rx + width && py >= ry && py <= ry + height;
    }

    /**
     * 检查点是否在圆形内
     */
    public static boolean isPointInCircle(double px, double py, double cx, double cy, double radius) {
        return distance2D(px, py, cx, cy) <= radius;
    }

    /**
     * 计算角度（弧度）
     */
    public static double angle(double x1, double y1, double x2, double y2) {
        return Math.atan2(y2 - y1, x2 - x1);
    }

    /**
     * 角度转度数
     */
    public static double radiansToDegrees(double radians) {
        return radians * 180.0 / Math.PI;
    }

    /**
     * 度数转角度
     */
    public static double degreesToRadians(double degrees) {
        return degrees * Math.PI / 180.0;
    }

    // ========== 时间处理相关 ==========

    /**
     * 格式化时间
     */
    public static String formatTime(LocalDateTime time) {
        return time != null ? time.format(DEFAULT_TIME_FORMATTER) : "";
    }

    /**
     * 格式化时间
     */
    public static String formatTime(LocalDateTime time, String pattern) {
        if (time == null || pattern == null) {
            return "";
        }
        return time.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 格式化持续时间
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "0秒";
        }

        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分钟");
        }
        if (remainingSeconds > 0 || sb.length() == 0) {
            sb.append(remainingSeconds).append("秒");
        }

        return sb.toString();
    }

    /**
     * 检查时间是否在指定范围内
     */
    public static boolean isTimeInRange(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null || start == null || end == null) {
            return false;
        }
        return !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * 获取今天开始时间
     */
    public static LocalDateTime getTodayStart() {
        return LocalDateTime.now().toLocalDate().atStartOfDay();
    }

    /**
     * 获取今天结束时间
     */
    public static LocalDateTime getTodayEnd() {
        return LocalDateTime.now().toLocalDate().atTime(23, 59, 59, 999999999);
    }

    // ========== 字符串处理相关 ==========

    /**
     * 验证用户名格式
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * 验证昵称格式
     */
    public static boolean isValidNickname(String nickname) {
        return nickname != null && NICKNAME_PATTERN.matcher(nickname).matches();
    }

    /**
     * 字符串安全截取
     */
    public static String safeTruncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * 字符串安全截取（支持省略号）
     */
    public static String safeTruncate(String str, int maxLength, String suffix) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        int truncateLength = maxLength - (suffix != null ? suffix.length() : 0);
        if (truncateLength <= 0) {
            return suffix != null ? suffix : "";
        }
        return str.substring(0, truncateLength) + (suffix != null ? suffix : "");
    }

    /**
     * 掩码处理（如手机号、身份证号）
     */
    public static String maskString(String str, int startKeep, int endKeep, char maskChar) {
        if (str == null || str.length() <= startKeep + endKeep) {
            return str;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(str, 0, startKeep);
        for (int i = startKeep; i < str.length() - endKeep; i++) {
            sb.append(maskChar);
        }
        sb.append(str.substring(str.length() - endKeep));
        return sb.toString();
    }

    // ========== 游戏公式计算 ==========

    /**
     * 经验升级公式（线性增长）
     */
    public static long calculateExpForLevel(int level) {
        return level * 100L;
    }

    /**
     * 经验升级公式（指数增长）
     */
    public static long calculateExpForLevelExponential(int level, double base, double multiplier) {
        return Math.round(multiplier * Math.pow(base, level));
    }

    /**
     * 计算属性加成百分比
     */
    public static double calculateAttributeBonus(int baseValue, double bonusPercent) {
        return baseValue * (1.0 + bonusPercent / 100.0);
    }

    /**
     * 计算伤害衰减
     */
    public static double calculateDamageReduction(double damage, double defense) {
        if (defense <= 0) {
            return damage;
        }
        // 简单的伤害衰减公式：damage * (1 - defense / (defense + 100))
        return damage * (100.0 / (defense + 100.0));
    }

    /**
     * 计算暴击伤害
     */
    public static double calculateCriticalDamage(double baseDamage, double criticalMultiplier) {
        return baseDamage * criticalMultiplier;
    }

    /**
     * 计算复合增长
     */
    public static double calculateCompoundGrowth(double principal, double rate, int periods) {
        return principal * Math.pow(1.0 + rate, periods);
    }

    // ========== 集合工具 ==========

    /**
     * 安全获取列表元素
     */
    public static <T> T safeGet(List<T> list, int index, T defaultValue) {
        if (list == null || index < 0 || index >= list.size()) {
            return defaultValue;
        }
        return list.get(index);
    }

    /**
     * 安全获取Map值
     */
    public static <K, V> V safeGet(Map<K, V> map, K key, V defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * 列表分页
     */
    public static <T> List<T> paginate(List<T> list, int page, int pageSize) {
        if (list == null || list.isEmpty() || page < 1 || pageSize < 1) {
            return new ArrayList<>();
        }

        int start = (page - 1) * pageSize;
        if (start >= list.size()) {
            return new ArrayList<>();
        }

        int end = Math.min(start + pageSize, list.size());
        return new ArrayList<>(list.subList(start, end));
    }

    /**
     * 列表打乱
     */
    public static <T> void shuffle(List<T> list) {
        if (list != null && list.size() > 1) {
            Collections.shuffle(list, ThreadLocalRandom.current()::nextInt);
        }
    }

    // ========== 缓存工具 ==========

    /**
     * 带缓存的计算
     */
    @SuppressWarnings("unchecked")
    public static <T> T computeIfAbsent(String key, Supplier<T> supplier) {
        return (T) CACHE.computeIfAbsent(key, k -> supplier.get());
    }

    /**
     * 清理缓存
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return CACHE.size();
    }

    // ========== 性能工具 ==========

    /**
     * 执行时间测量
     */
    public static <T> T measureTime(Supplier<T> supplier, String operation) {
        long startTime = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            long endTime = System.nanoTime();
            double elapsedMs = (endTime - startTime) / 1_000_000.0;
            log.debug("操作 '{}' 耗时: {:.2f}ms", operation, elapsedMs);
        }
    }

    /**
     * 执行时间测量（无返回值）
     */
    public static void measureTime(Runnable runnable, String operation) {
        long startTime = System.nanoTime();
        try {
            runnable.run();
        } finally {
            long endTime = System.nanoTime();
            double elapsedMs = (endTime - startTime) / 1_000_000.0;
            log.debug("操作 '{}' 耗时: {:.2f}ms", operation, elapsedMs);
        }
    }

    // ========== 其他工具 ==========

    /**
     * 生成唯一ID
     */
    public static String generateUniqueId() {
        return System.currentTimeMillis() + "_" + randomAlphanumeric(8);
    }

    /**
     * 检查是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 检查是否非空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 空值合并
     */
    public static <T> T coalesce(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 安全转换为整数
     */
    public static int safeParseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全转换为长整数
     */
    public static long safeParseLong(String str, long defaultValue) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全转换为双精度浮点数
     */
    public static double safeParseDouble(String str, double defaultValue) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}