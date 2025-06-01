/*
 * 文件名: SensitiveDataHandler.java
 * 用途: 数据脱敏处理器
 * 实现内容:
 *   - 实现各种类型的数据脱敏算法
 *   - 支持自定义脱敏规则
 *   - 提供脱敏和还原方法
 *   - 查询结果自动脱敏处理
 * 技术选型:
 *   - 策略模式实现不同脱敏算法
 *   - 反射处理注解字段
 *   - 正则表达式匹配
 * 依赖关系:
 *   - 使用Sensitive注解
 *   - 被MyBatis结果处理器调用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * 敏感数据处理器
 * <p>
 * 提供统一的数据脱敏处理功能，支持多种内置脱敏类型和自定义规则。
 * 自动处理标记了@Sensitive注解的字段。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class SensitiveDataHandler {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveDataHandler.class);

    /**
     * 手机号正则表达式
     */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 身份证号正则表达式
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dX]$");

    /**
     * 银行卡号正则表达式
     */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{13,19}$");

    /**
     * 邮箱正则表达式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    /**
     * IP地址正则表达式
     */
    private static final Pattern IP_PATTERN = Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    /**
     * 处理对象中的敏感数据
     *
     * @param obj 待处理的对象
     * @return 脱敏后的对象
     */
    public Object handleSensitiveData(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            Class<?> clazz = obj.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                Sensitive sensitive = field.getAnnotation(Sensitive.class);
                if (sensitive != null && sensitive.enabled()) {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    
                    if (value instanceof String) {
                        String maskedValue = maskSensitiveData((String) value, sensitive);
                        field.set(obj, maskedValue);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("处理敏感数据时发生异常", e);
        }

        return obj;
    }

    /**
     * 脱敏数据（公共方法）
     *
     * @param data 原始数据
     * @param type 脱敏类型
     * @return 脱敏后的数据
     */
    public String maskData(String data, Sensitive.SensitiveType type) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        try {
            switch (type) {
                case MOBILE_PHONE:
                    return maskMobilePhone(data);
                case ID_CARD:
                    return maskIdCard(data);
                case BANK_CARD:
                    return maskBankCard(data);
                case EMAIL:
                    return maskEmail(data);
                case NAME:
                    return maskName(data);
                case ADDRESS:
                    return maskAddress(data);
                case PASSWORD:
                    return maskPassword(data);
                case IP_ADDRESS:
                    return maskIpAddress(data);
                default:
                    return data;
            }
        } catch (Exception e) {
            logger.warn("脱敏数据失败，返回原始数据: {}", e.getMessage());
            return data;
        }
    }

    /**
     * 脱敏敏感数据
     *
     * @param data 原始数据
     * @param sensitive 脱敏注解
     * @return 脱敏后的数据
     */
    public String maskSensitiveData(String data, Sensitive sensitive) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        try {
            switch (sensitive.type()) {
                case MOBILE_PHONE:
                    return maskMobilePhone(data);
                case ID_CARD:
                    return maskIdCard(data);
                case BANK_CARD:
                    return maskBankCard(data);
                case EMAIL:
                    return maskEmail(data);
                case NAME:
                    return maskName(data);
                case ADDRESS:
                    return maskAddress(data);
                case PASSWORD:
                    return maskPassword(data);
                case IP_ADDRESS:
                    return maskIpAddress(data);
                case CUSTOM:
                    return maskCustom(data, sensitive.customRule());
                default:
                    return data;
            }
        } catch (Exception e) {
            logger.warn("脱敏数据失败，返回原始数据: {}", e.getMessage());
            return data;
        }
    }

    /**
     * 手机号脱敏
     * 格式：138****1234
     */
    private String maskMobilePhone(String mobile) {
        if (mobile == null || !MOBILE_PATTERN.matcher(mobile).matches()) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    /**
     * 身份证号脱敏
     * 格式：123456********1234
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || !ID_CARD_PATTERN.matcher(idCard).matches()) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }

    /**
     * 银行卡号脱敏
     * 格式：1234 **** **** 5678
     */
    private String maskBankCard(String bankCard) {
        if (bankCard == null || !BANK_CARD_PATTERN.matcher(bankCard).matches()) {
            return bankCard;
        }
        
        String prefix = bankCard.substring(0, 4);
        String suffix = bankCard.substring(bankCard.length() - 4);
        int middleLength = bankCard.length() - 8;
        String middle = "*".repeat(Math.max(middleLength, 4));
        
        return prefix + " " + middle.replaceAll("(.{4})", "$1 ").trim() + " " + suffix;
    }

    /**
     * 邮箱脱敏
     * 格式：u***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return email;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        
        String prefix = email.substring(0, 1);
        String suffix = email.substring(atIndex);
        return prefix + "***" + suffix;
    }

    /**
     * 姓名脱敏
     * 格式：张**
     */
    private String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        
        String prefix = name.substring(0, 1);
        String suffix = "*".repeat(name.length() - 1);
        return prefix + suffix;
    }

    /**
     * 地址脱敏
     * 格式：北京市朝阳区***
     */
    private String maskAddress(String address) {
        if (address == null || address.length() <= 6) {
            return address;
        }
        
        String prefix = address.substring(0, 6);
        String suffix = "*".repeat(Math.min(address.length() - 6, 10));
        return prefix + suffix;
    }

    /**
     * 密码脱敏
     * 格式：******
     */
    private String maskPassword(String password) {
        if (password == null) {
            return password;
        }
        return "*".repeat(Math.min(password.length(), 8));
    }

    /**
     * IP地址脱敏
     * 格式：192.168.*.*
     */
    private String maskIpAddress(String ip) {
        if (ip == null || !IP_PATTERN.matcher(ip).matches()) {
            return ip;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return ip;
    }

    /**
     * 自定义脱敏
     */
    private String maskCustom(String data, String rule) {
        if (data == null || rule == null || rule.isEmpty()) {
            return data;
        }
        
        try {
            // 简单的自定义规则实现：支持保留前N位和后N位
            // 格式：keep_prefix:3,keep_suffix:4
            if (rule.contains("keep_prefix") && rule.contains("keep_suffix")) {
                int prefixLength = extractNumber(rule, "keep_prefix");
                int suffixLength = extractNumber(rule, "keep_suffix");
                
                if (data.length() <= prefixLength + suffixLength) {
                    return data;
                }
                
                String prefix = data.substring(0, prefixLength);
                String suffix = data.substring(data.length() - suffixLength);
                int maskLength = data.length() - prefixLength - suffixLength;
                String mask = "*".repeat(maskLength);
                
                return prefix + mask + suffix;
            }
            
            // 如果规则不匹配，返回原数据
            logger.warn("不支持的自定义脱敏规则: {}", rule);
            return data;
            
        } catch (Exception e) {
            logger.warn("自定义脱敏规则执行失败: {}", e.getMessage());
            return data;
        }
    }

    /**
     * 从规则字符串中提取数字
     */
    private int extractNumber(String rule, String key) {
        try {
            String[] parts = rule.split(",");
            for (String part : parts) {
                if (part.trim().startsWith(key + ":")) {
                    return Integer.parseInt(part.split(":")[1].trim());
                }
            }
        } catch (Exception e) {
            logger.warn("提取规则数字失败: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 检查字符串是否需要脱敏
     *
     * @param data 数据
     * @param type 脱敏类型
     * @return 是否需要脱敏
     */
    public boolean needsMasking(String data, Sensitive.SensitiveType type) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        switch (type) {
            case MOBILE_PHONE:
                return MOBILE_PATTERN.matcher(data).matches();
            case ID_CARD:
                return ID_CARD_PATTERN.matcher(data).matches();
            case BANK_CARD:
                return BANK_CARD_PATTERN.matcher(data).matches();
            case EMAIL:
                return EMAIL_PATTERN.matcher(data).matches();
            case IP_ADDRESS:
                return IP_PATTERN.matcher(data).matches();
            case NAME:
            case ADDRESS:
            case PASSWORD:
            case CUSTOM:
                return true;
            default:
                return false;
        }
    }
}