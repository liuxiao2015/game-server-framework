/*
 * 文件名: TimeUtils.java
 * 用途: 时间和日期相关的工具方法
 * 实现内容:
 *   - 提供常用的时间格式化和解析方法
 *   - 支持时间计算和比较操作
 *   - 集成时区处理和转换
 *   - 游戏相关的时间逻辑支持
 * 技术选型:
 *   - 使用Java 8+时间API (LocalDateTime, ZonedDateTime等)
 *   - 支持多种时间格式和时区
 *   - 提供线程安全的时间操作
 * 依赖关系:
 *   - 无外部依赖，基于Java标准时间API
 *   - 被需要时间处理的模块使用
 */
package com.lx.gameserver.common;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * 时间和日期相关的工具类
 * <p>
 * 提供常用的时间操作方法，包括格式化、解析、计算、比较等。
 * 基于Java 8+的新时间API，线程安全且功能丰富。支持多种时间格式
 * 和时区处理，特别针对游戏服务器的时间需求进行了优化。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public final class TimeUtils {

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private TimeUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ===== 常用时间格式常量 =====

    /**
     * 标准日期时间格式：yyyy-MM-dd HH:mm:ss
     */
    public static final DateTimeFormatter STANDARD_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 标准日期格式：yyyy-MM-dd
     */
    public static final DateTimeFormatter STANDARD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 标准时间格式：HH:mm:ss
     */
    public static final DateTimeFormatter STANDARD_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 紧凑日期时间格式：yyyyMMddHHmmss
     */
    public static final DateTimeFormatter COMPACT_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 紧凑日期格式：yyyyMMdd
     */
    public static final DateTimeFormatter COMPACT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * ISO日期时间格式：yyyy-MM-dd'T'HH:mm:ss
     */
    public static final DateTimeFormatter ISO_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 中文日期时间格式：yyyy年MM月dd日 HH时mm分ss秒
     */
    public static final DateTimeFormatter CHINESE_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒");

    /**
     * 默认时区
     */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of(GameConstants.DEFAULT_TIMEZONE);

    // ===== 当前时间获取方法 =====

    /**
     * 获取当前时间戳（毫秒）
     *
     * @return 当前时间戳
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前时间戳（秒）
     *
     * @return 当前时间戳（秒）
     */
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取当前LocalDateTime
     *
     * @return 当前LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 获取当前ZonedDateTime（默认时区）
     *
     * @return 当前ZonedDateTime
     */
    public static ZonedDateTime nowWithZone() {
        return ZonedDateTime.now(DEFAULT_ZONE);
    }

    /**
     * 获取当前日期
     *
     * @return 当前LocalDate
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * 获取当前时间
     *
     * @return 当前LocalTime
     */
    public static LocalTime currentTime() {
        return LocalTime.now();
    }

    // ===== 格式化方法 =====

    /**
     * 格式化LocalDateTime为标准字符串格式
     *
     * @param dateTime LocalDateTime对象
     * @return 格式化后的字符串，如果参数为null返回空字符串
     */
    public static String format(LocalDateTime dateTime) {
        return format(dateTime, STANDARD_DATETIME_FORMAT);
    }

    /**
     * 格式化LocalDateTime为指定格式字符串
     *
     * @param dateTime LocalDateTime对象
     * @param formatter 格式化器
     * @return 格式化后的字符串，如果参数为null返回空字符串
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null || formatter == null) {
            return "";
        }
        return dateTime.format(formatter);
    }

    /**
     * 格式化LocalDate为标准字符串格式
     *
     * @param date LocalDate对象
     * @return 格式化后的字符串，如果参数为null返回空字符串
     */
    public static String format(LocalDate date) {
        return format(date, STANDARD_DATE_FORMAT);
    }

    /**
     * 格式化LocalDate为指定格式字符串
     *
     * @param date LocalDate对象
     * @param formatter 格式化器
     * @return 格式化后的字符串，如果参数为null返回空字符串
     */
    public static String format(LocalDate date, DateTimeFormatter formatter) {
        if (date == null || formatter == null) {
            return "";
        }
        return date.format(formatter);
    }

    /**
     * 格式化时间戳为标准字符串格式
     *
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的字符串
     */
    public static String format(long timestamp) {
        return format(timestampToLocalDateTime(timestamp));
    }

    /**
     * 格式化时间戳为指定格式字符串
     *
     * @param timestamp 时间戳（毫秒）
     * @param formatter 格式化器
     * @return 格式化后的字符串
     */
    public static String format(long timestamp, DateTimeFormatter formatter) {
        return format(timestampToLocalDateTime(timestamp), formatter);
    }

    // ===== 解析方法 =====

    /**
     * 解析标准格式的日期时间字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @return LocalDateTime对象，解析失败返回null
     */
    public static LocalDateTime parse(String dateTimeStr) {
        return parse(dateTimeStr, STANDARD_DATETIME_FORMAT);
    }

    /**
     * 解析指定格式的日期时间字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @param formatter   格式化器
     * @return LocalDateTime对象，解析失败返回null
     */
    public static LocalDateTime parse(String dateTimeStr, DateTimeFormatter formatter) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty() || formatter == null) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(dateTimeStr.trim(), formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 解析标准格式的日期字符串
     *
     * @param dateStr 日期字符串
     * @return LocalDate对象，解析失败返回null
     */
    public static LocalDate parseDate(String dateStr) {
        return parseDate(dateStr, STANDARD_DATE_FORMAT);
    }

    /**
     * 解析指定格式的日期字符串
     *
     * @param dateStr   日期字符串
     * @param formatter 格式化器
     * @return LocalDate对象，解析失败返回null
     */
    public static LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
        if (dateStr == null || dateStr.trim().isEmpty() || formatter == null) {
            return null;
        }
        
        try {
            return LocalDate.parse(dateStr.trim(), formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ===== 转换方法 =====

    /**
     * 时间戳转LocalDateTime
     *
     * @param timestamp 时间戳（毫秒）
     * @return LocalDateTime对象
     */
    public static LocalDateTime timestampToLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE);
    }

    /**
     * LocalDateTime转时间戳
     *
     * @param dateTime LocalDateTime对象
     * @return 时间戳（毫秒），如果参数为null返回0
     */
    public static long localDateTimeToTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    /**
     * Date转LocalDateTime
     *
     * @param date Date对象
     * @return LocalDateTime对象，如果参数为null返回null
     */
    public static LocalDateTime dateToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), DEFAULT_ZONE);
    }

    /**
     * LocalDateTime转Date
     *
     * @param dateTime LocalDateTime对象
     * @return Date对象，如果参数为null返回null
     */
    public static Date localDateTimeToDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(DEFAULT_ZONE).toInstant());
    }

    // ===== 时间计算方法 =====

    /**
     * 增加指定天数
     *
     * @param dateTime LocalDateTime对象
     * @param days     天数
     * @return 计算后的LocalDateTime，如果参数为null返回null
     */
    public static LocalDateTime addDays(LocalDateTime dateTime, long days) {
        return dateTime != null ? dateTime.plusDays(days) : null;
    }

    /**
     * 增加指定小时数
     *
     * @param dateTime LocalDateTime对象
     * @param hours    小时数
     * @return 计算后的LocalDateTime，如果参数为null返回null
     */
    public static LocalDateTime addHours(LocalDateTime dateTime, long hours) {
        return dateTime != null ? dateTime.plusHours(hours) : null;
    }

    /**
     * 增加指定分钟数
     *
     * @param dateTime LocalDateTime对象
     * @param minutes  分钟数
     * @return 计算后的LocalDateTime，如果参数为null返回null
     */
    public static LocalDateTime addMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime != null ? dateTime.plusMinutes(minutes) : null;
    }

    /**
     * 增加指定秒数
     *
     * @param dateTime LocalDateTime对象
     * @param seconds  秒数
     * @return 计算后的LocalDateTime，如果参数为null返回null
     */
    public static LocalDateTime addSeconds(LocalDateTime dateTime, long seconds) {
        return dateTime != null ? dateTime.plusSeconds(seconds) : null;
    }

    /**
     * 计算两个时间之间的天数差
     *
     * @param from 开始时间
     * @param to   结束时间
     * @return 天数差，如果参数为null返回0
     */
    public static long daysBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
    }

    /**
     * 计算两个时间之间的小时数差
     *
     * @param from 开始时间
     * @param to   结束时间
     * @return 小时数差，如果参数为null返回0
     */
    public static long hoursBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(from, to);
    }

    /**
     * 计算两个时间之间的分钟数差
     *
     * @param from 开始时间
     * @param to   结束时间
     * @return 分钟数差，如果参数为null返回0
     */
    public static long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(from, to);
    }

    /**
     * 计算两个时间之间的秒数差
     *
     * @param from 开始时间
     * @param to   结束时间
     * @return 秒数差，如果参数为null返回0
     */
    public static long secondsBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.SECONDS.between(from, to);
    }

    // ===== 特殊时间点获取方法 =====

    /**
     * 获取今天开始时间（00:00:00）
     *
     * @return 今天开始时间
     */
    public static LocalDateTime getTodayStart() {
        return LocalDate.now().atStartOfDay();
    }

    /**
     * 获取今天结束时间（23:59:59.999）
     *
     * @return 今天结束时间
     */
    public static LocalDateTime getTodayEnd() {
        return LocalDate.now().atTime(LocalTime.MAX);
    }

    /**
     * 获取指定日期的开始时间（00:00:00）
     *
     * @param date 指定日期
     * @return 开始时间，如果参数为null返回null
     */
    public static LocalDateTime getDateStart(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    /**
     * 获取指定日期的结束时间（23:59:59.999）
     *
     * @param date 指定日期
     * @return 结束时间，如果参数为null返回null
     */
    public static LocalDateTime getDateEnd(LocalDate date) {
        return date != null ? date.atTime(LocalTime.MAX) : null;
    }

    /**
     * 获取本周开始时间（周一00:00:00）
     *
     * @return 本周开始时间
     */
    public static LocalDateTime getThisWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    /**
     * 获取本周结束时间（周日23:59:59.999）
     *
     * @return 本周结束时间
     */
    public static LocalDateTime getThisWeekEnd() {
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.MAX);
    }

    /**
     * 获取本月开始时间（1号00:00:00）
     *
     * @return 本月开始时间
     */
    public static LocalDateTime getThisMonthStart() {
        return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
    }

    /**
     * 获取本月结束时间（最后一天23:59:59.999）
     *
     * @return 本月结束时间
     */
    public static LocalDateTime getThisMonthEnd() {
        return LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
    }

    // ===== 时间判断方法 =====

    /**
     * 判断是否是同一天
     *
     * @param dateTime1 时间1
     * @param dateTime2 时间2
     * @return 如果是同一天返回true，否则返回false
     */
    public static boolean isSameDay(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return dateTime1.toLocalDate().equals(dateTime2.toLocalDate());
    }

    /**
     * 判断是否是今天
     *
     * @param dateTime 时间
     * @return 如果是今天返回true，否则返回false
     */
    public static boolean isToday(LocalDateTime dateTime) {
        return isSameDay(dateTime, now());
    }

    /**
     * 判断是否是本周
     *
     * @param dateTime 时间
     * @return 如果是本周返回true，否则返回false
     */
    public static boolean isThisWeek(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime weekStart = getThisWeekStart();
        LocalDateTime weekEnd = getThisWeekEnd();
        return !dateTime.isBefore(weekStart) && !dateTime.isAfter(weekEnd);
    }

    /**
     * 判断是否是本月
     *
     * @param dateTime 时间
     * @return 如果是本月返回true，否则返回false
     */
    public static boolean isThisMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDate now = LocalDate.now();
        LocalDate target = dateTime.toLocalDate();
        return now.getYear() == target.getYear() && now.getMonth() == target.getMonth();
    }

    /**
     * 判断时间是否在指定范围内
     *
     * @param dateTime 待判断时间
     * @param start    开始时间
     * @param end      结束时间
     * @return 如果在范围内返回true，否则返回false
     */
    public static boolean isBetween(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        if (dateTime == null || start == null || end == null) {
            return false;
        }
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    // ===== 游戏时间相关方法 =====

    /**
     * 获取游戏服务器运行天数
     *
     * @param serverStartTime 服务器启动时间
     * @return 运行天数
     */
    public static long getServerRunDays(LocalDateTime serverStartTime) {
        return daysBetween(serverStartTime, now()) + 1;
    }

    /**
     * 计算活动剩余时间（秒）
     *
     * @param endTime 活动结束时间
     * @return 剩余时间（秒），如果已结束返回0
     */
    public static long getActivityRemainingSeconds(LocalDateTime endTime) {
        if (endTime == null || endTime.isBefore(now())) {
            return 0;
        }
        return secondsBetween(now(), endTime);
    }

    /**
     * 判断是否在维护时间段内
     *
     * @param maintenanceStart 维护开始时间
     * @param maintenanceEnd   维护结束时间
     * @return 如果在维护时间段内返回true，否则返回false
     */
    public static boolean isInMaintenanceTime(LocalTime maintenanceStart, LocalTime maintenanceEnd) {
        if (maintenanceStart == null || maintenanceEnd == null) {
            return false;
        }
        
        LocalTime now = LocalTime.now();
        
        // 跨天的维护时间（如23:00-05:00）
        if (maintenanceStart.isAfter(maintenanceEnd)) {
            return !now.isBefore(maintenanceStart) || !now.isAfter(maintenanceEnd);
        } else {
            // 同一天的维护时间（如02:00-06:00）
            return !now.isBefore(maintenanceStart) && !now.isAfter(maintenanceEnd);
        }
    }

    /**
     * 获取下次刷新时间（每日刷新）
     *
     * @param refreshHour 刷新小时（0-23）
     * @return 下次刷新时间
     */
    public static LocalDateTime getNextDailyRefreshTime(int refreshHour) {
        if (refreshHour < 0 || refreshHour > 23) {
            refreshHour = 0;
        }
        
        LocalDateTime now = now();
        LocalDateTime todayRefresh = now.toLocalDate().atTime(refreshHour, 0, 0);
        
        if (now.isBefore(todayRefresh)) {
            return todayRefresh;
        } else {
            return todayRefresh.plusDays(1);
        }
    }

    /**
     * 获取时间的友好显示格式
     *
     * @param dateTime 时间
     * @return 友好显示字符串
     */
    public static String getFriendlyTimeString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        
        LocalDateTime now = now();
        long seconds = secondsBetween(dateTime, now);
        
        if (seconds < 0) {
            // 未来时间
            seconds = -seconds;
            if (seconds < 60) {
                return seconds + "秒后";
            } else if (seconds < 3600) {
                return (seconds / 60) + "分钟后";
            } else if (seconds < 86400) {
                return (seconds / 3600) + "小时后";
            } else {
                return (seconds / 86400) + "天后";
            }
        } else {
            // 过去时间
            if (seconds < 60) {
                return seconds + "秒前";
            } else if (seconds < 3600) {
                return (seconds / 60) + "分钟前";
            } else if (seconds < 86400) {
                return (seconds / 3600) + "小时前";
            } else {
                long days = seconds / 86400;
                if (days < 30) {
                    return days + "天前";
                } else {
                    return format(dateTime, STANDARD_DATE_FORMAT);
                }
            }
        }
    }
}