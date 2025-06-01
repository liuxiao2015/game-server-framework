/*
 * 文件名: SecurityUtils.java
 * 用途: 安全工具类
 * 实现内容:
 *   - 获取当前用户
 *   - 权限检查快捷方法
 *   - 加密解密快捷方法
 *   - IP地址工具
 *   - 安全随机数生成
 * 技术选型:
 *   - Spring Security集成
 *   - 工具方法封装
 * 依赖关系:
 *   - 被各模块使用
 *   - 使用Spring Security上下文
 */
package com.lx.gameserver.frame.security.util;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import com.lx.gameserver.frame.security.crypto.GameCryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 安全工具类
 * <p>
 * 提供游戏服务器常用的安全相关工具方法，包括获取当前用户、
 * 权限检查、加密解密、IP地址处理和安全随机数生成等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class SecurityUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private SecurityUtils() {
        throw new IllegalStateException("工具类不应该被实例化");
    }
    
    /**
     * IP地址正则表达式
     */
    private static final Pattern IP_PATTERN = 
            Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
    
    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM;
    
    static {
        SecureRandom secureRandom;
        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            log.warn("无法获取强安全随机数生成器，使用默认实现", e);
            secureRandom = new SecureRandom();
        }
        SECURE_RANDOM = secureRandom;
    }
    
    /**
     * 获取当前用户的游戏用户详情
     *
     * @return 当前游戏用户详情，如果未登录或不是游戏用户则返回null
     */
    @Nullable
    public static GameUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof GameUserDetails) {
            return (GameUserDetails) principal;
        }
        
        return null;
    }
    
    /**
     * 获取当前用户名
     *
     * @return 当前用户名，如果未登录则返回null
     */
    @Nullable
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }
    
    /**
     * 检查当前用户是否有指定权限
     *
     * @param permission 权限标识
     * @return 如果有权限返回true，否则返回false
     */
    public static boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
    
    /**
     * 获取客户端真实IP地址
     *
     * @return IP地址字符串
     */
    @Nullable
    public static String getClientIpAddress() {
        ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        
        HttpServletRequest request = attributes.getRequest();
        return getClientIpAddress(request);
    }
    
    /**
     * 从请求中获取客户端真实IP地址
     *
     * @param request HTTP请求
     * @return IP地址字符串
     */
    @Nullable
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // 可能是多个IP，取第一个
            int index = ip.indexOf(",");
            if (index > 0) {
                ip = ip.substring(0, index).trim();
            }
            return ip;
        }
        
        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("HTTP_CLIENT_IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 检查IP地址是否有效
     *
     * @param ip IP地址字符串
     * @return 如果有效返回true，否则返回false
     */
    public static boolean isValidIp(@Nullable String ip) {
        return ip != null && !ip.isEmpty() && !ip.equalsIgnoreCase("unknown") && 
                IP_PATTERN.matcher(ip).matches();
    }
    
    /**
     * 生成安全随机的UUID
     *
     * @return UUID字符串
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 生成安全随机的十六进制字符串
     *
     * @param length 字节长度（生成的字符串长度为length*2）
     * @return 十六进制字符串
     */
    public static String generateRandomHex(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        
        StringBuilder hex = new StringBuilder(length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        
        return hex.toString();
    }
    
    /**
     * 生成安全随机的Base64字符串
     *
     * @param length 字节长度
     * @return Base64编码的随机字符串
     */
    public static String generateRandomBase64(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * 生成安全随机数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机数
     */
    public static int generateRandomInt(int min, int max) {
        return SECURE_RANDOM.nextInt(max - min + 1) + min;
    }
    
    /**
     * 使用BCrypt哈希密码
     *
     * @param password 明文密码
     * @return 哈希后的密码
     */
    public static String hashPassword(String password) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode(password);
    }
    
    /**
     * 验证BCrypt密码
     *
     * @param rawPassword 明文密码
     * @param encodedPassword 哈希后的密码
     * @return 密码是否匹配
     */
    public static boolean verifyPassword(String rawPassword, String encodedPassword) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * 使用给定的加密服务加密文本
     *
     * @param plainText 明文
     * @param key 密钥
     * @param cryptoService 加密服务实例
     * @return 加密后的文本，如果发生异常则返回null
     */
    @Nullable
    public static String encrypt(String plainText, byte[] key, GameCryptoService cryptoService) {
        try {
            return cryptoService.encryptAES(plainText, key);
        } catch (Exception e) {
            log.error("加密失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 使用给定的加密服务解密文本
     *
     * @param encryptedText 密文
     * @param key 密钥
     * @param cryptoService 加密服务实例
     * @return 解密后的文本，如果发生异常则返回null
     */
    @Nullable
    public static String decrypt(String encryptedText, byte[] key, GameCryptoService cryptoService) {
        try {
            return cryptoService.decryptAES(encryptedText, key);
        } catch (Exception e) {
            log.error("解密失败: {}", e.getMessage(), e);
            return null;
        }
    }
}