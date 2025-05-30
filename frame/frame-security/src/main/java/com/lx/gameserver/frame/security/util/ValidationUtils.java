/*
 * 文件名: ValidationUtils.java
 * 用途: 验证工具类
 * 实现内容:
 *   - 输入验证
 *   - 参数净化
 *   - 路径验证
 *   - 文件类型验证
 *   - 大小限制验证
 * 技术选型:
 *   - 正则表达式
 *   - 白名单策略
 *   - MIME类型检查
 * 依赖关系:
 *   - 被各安全模块使用
 *   - 提供通用验证功能
 */
package com.lx.gameserver.frame.security.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 验证工具类
 * <p>
 * 提供各种输入验证、参数净化和安全检查方法，
 * 用于防止SQL注入、XSS攻击、路径遍历等安全问题。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class ValidationUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private ValidationUtils() {
        throw new IllegalStateException("工具类不应该被实例化");
    }
    
    /**
     * 用户名正则表达式（字母、数字、下划线，4-20个字符）
     */
    private static final Pattern USERNAME_PATTERN = 
            Pattern.compile("^[a-zA-Z0-9_]{4,20}$");
    
    /**
     * 邮箱正则表达式
     */
    private static final Pattern EMAIL_PATTERN = 
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    /**
     * 手机号正则表达式（中国）
     */
    private static final Pattern PHONE_PATTERN = 
            Pattern.compile("^1[3-9]\\d{9}$");
    
    /**
     * 路径遍历检测正则表达式
     */
    private static final Pattern PATH_TRAVERSAL_PATTERN = 
            Pattern.compile("\\.\\.[\\\\/]|[\\\\/]\\.\\.");
    
    /**
     * SQL注入检测正则表达式
     */
    private static final Pattern SQL_INJECTION_PATTERN = 
            Pattern.compile("(?i)(select|update|delete|insert|drop|alter|exec|union|create|where)\\s");
    
    /**
     * 常见脚本标签检测正则表达式（XSS防护）
     */
    private static final Pattern XSS_PATTERN = 
            Pattern.compile("<script|javascript:|on\\w+\\s*=|style\\s*=.*\\bexpression\\b|<iframe");
    
    /**
     * 允许的文件类型（MIME类型）
     */
    private static final Set<String> ALLOWED_FILE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain", "text/csv"
    ));
    
    /**
     * 检查用户名格式是否有效
     *
     * @param username 用户名
     * @return 如果有效返回true，否则返回false
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * 检查邮箱格式是否有效
     *
     * @param email 邮箱地址
     * @return 如果有效返回true，否则返回false
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * 检查手机号格式是否有效（中国）
     *
     * @param phone 手机号
     * @return 如果有效返回true，否则返回false
     */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    
    /**
     * 检查密码强度是否足够
     * 至少8个字符，包含大小写字母、数字和特殊字符
     *
     * @param password 密码
     * @return 如果满足要求返回true，否则返回false
     */
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecial;
    }
    
    /**
     * 检查输入是否可能包含SQL注入攻击
     *
     * @param input 用户输入
     * @return 如果安全返回true，如果可能包含SQL注入返回false
     */
    public static boolean isSqlSafe(String input) {
        return input == null || !SQL_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * 检查输入是否可能包含XSS攻击
     *
     * @param input 用户输入
     * @return 如果安全返回true，如果可能包含XSS攻击返回false
     */
    public static boolean isXssSafe(String input) {
        return input == null || !XSS_PATTERN.matcher(input).find();
    }
    
    /**
     * 检查路径是否安全（无路径遍历）
     *
     * @param path 路径字符串
     * @return 如果安全返回true，否则返回false
     */
    public static boolean isPathSafe(String path) {
        return path != null && !PATH_TRAVERSAL_PATTERN.matcher(path).find();
    }
    
    /**
     * 检查文件路径是否在指定目录内（防止目录遍历）
     *
     * @param basePath 基础目录
     * @param filePath 文件路径
     * @return 如果安全返回true，否则返回false
     */
    public static boolean isWithinBaseDir(String basePath, String filePath) {
        if (basePath == null || filePath == null) {
            return false;
        }
        
        try {
            Path base = Paths.get(basePath).normalize().toAbsolutePath();
            Path file = Paths.get(filePath).normalize().toAbsolutePath();
            
            return file.startsWith(base);
        } catch (Exception e) {
            log.warn("检查文件路径时出错: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查文件类型是否允许
     *
     * @param file 上传的文件
     * @return 如果允许返回true，否则返回false
     */
    public static boolean isAllowedFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }
        
        return ALLOWED_FILE_TYPES.contains(contentType);
    }
    
    /**
     * 校验文件大小是否在限制范围内
     *
     * @param file 上传的文件
     * @param maxSizeInBytes 最大文件大小（字节）
     * @return 如果在限制范围内返回true，否则返回false
     */
    public static boolean isValidFileSize(MultipartFile file, long maxSizeInBytes) {
        return file != null && !file.isEmpty() && file.getSize() <= maxSizeInBytes;
    }
    
    /**
     * 对输入进行HTML编码以防止XSS攻击
     *
     * @param input 用户输入
     * @return HTML编码后的字符串
     */
    public static String encodeHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }
    
    /**
     * 清理用户输入，去除潜在的XSS攻击
     *
     * @param input 用户输入
     * @return 清理后的字符串
     */
    public static String sanitizeUserInput(String input) {
        if (input == null) {
            return null;
        }
        
        // 移除所有HTML标签
        String sanitized = input.replaceAll("<[^>]*>", "");
        
        // 移除JavaScript伪协议
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        
        // 移除事件属性
        sanitized = sanitized.replaceAll("(?i)\\s*on\\w+\\s*=\\s*\"[^\"]*\"", "");
        
        return sanitized;
    }
    
    /**
     * 安全地处理文件名（防止目录遍历和特殊字符）
     *
     * @param filename 原始文件名
     * @return 安全的文件名
     */
    public static String sanitizeFilename(String filename) {
        if (StringUtils.isBlank(filename)) {
            return "unnamed";
        }
        
        // 删除路径分隔符
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // 删除控制字符
        sanitized = sanitized.replaceAll("[\\x00-\\x1F]", "");
        
        // 限制长度
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        
        return sanitized;
    }
    
    /**
     * 检查字符串是否是合法的UTF-8编码
     *
     * @param input 待检查的字符串
     * @return 如果是有效的UTF-8编码返回true，否则返回false
     */
    public static boolean isValidUtf8(String input) {
        if (input == null) {
            return false;
        }
        
        try {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            new String(bytes, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}