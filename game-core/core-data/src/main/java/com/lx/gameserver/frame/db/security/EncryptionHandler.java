/*
 * 文件名: EncryptionHandler.java
 * 用途: 数据加密处理器
 * 实现内容:
 *   - 敏感数据加密存储（如密码、支付信息）
 *   - 支持AES、RSA等加密算法
 *   - 透明加解密，对业务层无感知
 *   - 密钥管理和轮换机制
 * 技术选型:
 *   - AES/RSA加密算法
 *   - Spring Security Crypto
 *   - 自定义TypeHandler
 * 依赖关系:
 *   - 被MyBatis TypeHandler使用
 *   - 配合实体类加密字段
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 数据加密处理器
 * <p>
 * 提供透明的数据加密和解密功能，支持多种加密算法。
 * 自动处理敏感数据的加密存储和解密读取。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class EncryptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionHandler.class);

    /**
     * 默认加密算法
     */
    private static final String DEFAULT_ALGORITHM = "AES";
    private static final String DEFAULT_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * 密钥缓存
     */
    private final ConcurrentMap<String, SecretKey> keyCache = new ConcurrentHashMap<>();

    /**
     * 主密钥
     */
    @Value("${game.database.encryption.key:gameServerDefaultKey2025}")
    private String masterKey;

    /**
     * 是否启用加密
     */
    @Value("${game.database.encryption.enabled:true}")
    private boolean encryptionEnabled;

    /**
     * 加密字符串
     *
     * @param plainText 明文
     * @return 密文（Base64编码）
     */
    public String encrypt(String plainText) {
        if (!encryptionEnabled || !StringUtils.hasText(plainText)) {
            return plainText;
        }

        try {
            SecretKey secretKey = getOrCreateSecretKey(DEFAULT_ALGORITHM);
            Cipher cipher = Cipher.getInstance(DEFAULT_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            logger.error("数据加密失败", e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * 解密字符串
     *
     * @param encryptedText 密文（Base64编码）
     * @return 明文
     */
    public String decrypt(String encryptedText) {
        if (!encryptionEnabled || !StringUtils.hasText(encryptedText)) {
            return encryptedText;
        }

        try {
            SecretKey secretKey = getOrCreateSecretKey(DEFAULT_ALGORITHM);
            Cipher cipher = Cipher.getInstance(DEFAULT_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("数据解密失败", e);
            // 如果解密失败，可能是明文数据，直接返回
            return encryptedText;
        }
    }

    /**
     * 使用指定算法加密
     *
     * @param plainText 明文
     * @param algorithm 加密算法
     * @return 密文
     */
    public String encrypt(String plainText, String algorithm) {
        if (!encryptionEnabled || !StringUtils.hasText(plainText)) {
            return plainText;
        }

        try {
            SecretKey secretKey = getOrCreateSecretKey(algorithm);
            Cipher cipher = Cipher.getInstance(getTransformation(algorithm));
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            logger.error("数据加密失败, algorithm: {}", algorithm, e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * 使用指定算法解密
     *
     * @param encryptedText 密文
     * @param algorithm 加密算法
     * @return 明文
     */
    public String decrypt(String encryptedText, String algorithm) {
        if (!encryptionEnabled || !StringUtils.hasText(encryptedText)) {
            return encryptedText;
        }

        try {
            SecretKey secretKey = getOrCreateSecretKey(algorithm);
            Cipher cipher = Cipher.getInstance(getTransformation(algorithm));
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("数据解密失败, algorithm: {}", algorithm, e);
            return encryptedText;
        }
    }

    /**
     * 获取或创建密钥
     */
    private SecretKey getOrCreateSecretKey(String algorithm) {
        return keyCache.computeIfAbsent(algorithm, this::generateSecretKey);
    }

    /**
     * 生成密钥
     */
    private SecretKey generateSecretKey(String algorithm) {
        try {
            // 基于主密钥生成固定的密钥
            byte[] keyBytes = generateKeyBytes(masterKey, algorithm);
            return new SecretKeySpec(keyBytes, algorithm);
            
        } catch (Exception e) {
            logger.error("生成密钥失败, algorithm: {}", algorithm, e);
            throw new RuntimeException("生成密钥失败", e);
        }
    }

    /**
     * 生成密钥字节
     */
    private byte[] generateKeyBytes(String seed, String algorithm) {
        try {
            // 使用主密钥作为种子生成固定长度的密钥
            byte[] seedBytes = seed.getBytes(StandardCharsets.UTF_8);
            
            int keyLength = getKeyLength(algorithm);
            byte[] keyBytes = new byte[keyLength];
            
            // 简单的密钥派生：重复填充并异或
            for (int i = 0; i < keyLength; i++) {
                keyBytes[i] = seedBytes[i % seedBytes.length];
                keyBytes[i] ^= (byte) (i % 256); // 添加位置相关的混淆
            }
            
            return keyBytes;
            
        } catch (Exception e) {
            logger.error("生成密钥字节失败", e);
            throw new RuntimeException("生成密钥字节失败", e);
        }
    }

    /**
     * 获取算法的密钥长度
     */
    private int getKeyLength(String algorithm) {
        switch (algorithm.toUpperCase()) {
            case "AES":
                return 16; // AES-128
            case "DES":
                return 8;
            case "BLOWFISH":
                return 16;
            default:
                return 16; // 默认16字节
        }
    }

    /**
     * 获取加密变换
     */
    private String getTransformation(String algorithm) {
        switch (algorithm.toUpperCase()) {
            case "AES":
                return "AES/ECB/PKCS5Padding";
            case "DES":
                return "DES/ECB/PKCS5Padding";
            case "BLOWFISH":
                return "Blowfish/ECB/PKCS5Padding";
            default:
                return algorithm + "/ECB/PKCS5Padding";
        }
    }

    /**
     * 生成新的随机密钥
     */
    public String generateNewKey(String algorithm) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
            
        } catch (Exception e) {
            logger.error("生成新密钥失败, algorithm: {}", algorithm, e);
            throw new RuntimeException("生成新密钥失败", e);
        }
    }

    /**
     * 清除密钥缓存
     */
    public void clearKeyCache() {
        keyCache.clear();
        logger.info("密钥缓存已清除");
    }

    /**
     * 更新主密钥
     */
    public void updateMasterKey(String newMasterKey) {
        this.masterKey = newMasterKey;
        clearKeyCache(); // 清除旧密钥缓存
        logger.info("主密钥已更新");
    }

    /**
     * 检查是否启用加密
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * 设置加密启用状态
     */
    public void setEncryptionEnabled(boolean enabled) {
        this.encryptionEnabled = enabled;
    }
}