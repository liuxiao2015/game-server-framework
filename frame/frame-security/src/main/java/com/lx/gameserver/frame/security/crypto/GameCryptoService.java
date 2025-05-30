/*
 * 文件名: GameCryptoService.java
 * 用途: 游戏加密服务
 * 实现内容:
 *   - 对称加密（AES-256）
 *   - 非对称加密（RSA-2048）
 *   - 消息摘要（SHA-256）
 *   - 密钥管理
 *   - 加密算法选择
 * 技术选型:
 *   - Java加密扩展（JCE）
 *   - AES、RSA、SHA高强度算法
 *   - 安全随机数生成
 * 依赖关系:
 *   - 被框架各模块使用
 *   - 使用KeyManager管理密钥
 */
package com.lx.gameserver.frame.security.crypto;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.keystore.KeyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 游戏加密服务
 * <p>
 * 提供游戏服务器所需的各种加密、解密、签名和哈希功能，
 * 支持对称与非对称加密，以及消息摘要和密钥管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class GameCryptoService {

    /**
     * GCM认证标签长度（位）
     */
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * GCM IV长度（字节）
     */
    private static final int GCM_IV_LENGTH = 12;
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 密钥管理器（可选）
     */
    @Nullable
    private final KeyManager keyManager;
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param keyManager 密钥管理器
     */
    public GameCryptoService(SecurityProperties securityProperties, @Nullable KeyManager keyManager) {
        this.securityProperties = securityProperties;
        this.keyManager = keyManager;
        log.info("游戏加密服务初始化完成");
    }
    
    /**
     * 使用AES加密文本
     *
     * @param plainText 明文
     * @param key 密钥（16字节/128位，或24字节/192位，或32字节/256位）
     * @return Base64编码的密文
     */
    public String encryptAES(String plainText, byte[] key) throws Exception {
        if (plainText == null || key == null) {
            return null;
        }
        
        // 创建AES密钥
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        
        // 创建加密器，使用AES/GCM/NoPadding模式（更安全）
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        // 生成随机IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        // 初始化加密器
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
        
        // 执行加密
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        
        // 组合IV和密文
        byte[] encryptedData = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, encryptedData, 0, iv.length);
        System.arraycopy(cipherText, 0, encryptedData, iv.length, cipherText.length);
        
        // Base64编码
        return Base64.getEncoder().encodeToString(encryptedData);
    }
    
    /**
     * 使用AES解密文本
     *
     * @param encryptedText Base64编码的密文
     * @param key 密钥（与加密时相同）
     * @return 解密后的明文
     */
    public String decryptAES(String encryptedText, byte[] key) throws Exception {
        if (encryptedText == null || key == null) {
            return null;
        }
        
        // 解码Base64
        byte[] encryptedData = Base64.getDecoder().decode(encryptedText);
        
        // 提取IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[encryptedData.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, iv.length);
        System.arraycopy(encryptedData, iv.length, cipherText, 0, cipherText.length);
        
        // 创建AES密钥
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        
        // 创建解密器
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
        
        // 执行解密
        byte[] plainText = cipher.doFinal(cipherText);
        
        // 转换为字符串
        return new String(plainText, StandardCharsets.UTF_8);
    }
    
    /**
     * 使用RSA加密文本
     *
     * @param plainText 明文
     * @param publicKeyBase64 Base64编码的公钥
     * @return Base64编码的密文
     */
    public String encryptRSA(String plainText, String publicKeyBase64) throws Exception {
        if (plainText == null || publicKeyBase64 == null) {
            return null;
        }
        
        // 解码公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        
        // 创建加密器
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        
        // 执行加密（RSA加密数据长度有限制，需要分块）
        byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
        int maxBlockSize = 245; // RSA-2048的最大加密块大小
        int dataLength = data.length;
        int inputLen = dataLength;
        int offSet = 0;
        byte[] cache;
        int i = 0;
        byte[] resultBytes = new byte[getEncryptResultSize(publicKey, inputLen)];
        
        while (inputLen > 0) {
            if (inputLen > maxBlockSize) {
                cache = cipher.doFinal(data, offSet, maxBlockSize);
            } else {
                cache = cipher.doFinal(data, offSet, inputLen);
            }
            System.arraycopy(cache, 0, resultBytes, i * (publicKey.getModulus().bitLength() / 8), cache.length);
            i++;
            inputLen -= maxBlockSize;
            offSet += maxBlockSize;
        }
        
        // Base64编码
        return Base64.getEncoder().encodeToString(resultBytes);
    }
    
    /**
     * 计算RSA加密后的数据大小
     */
    private int getEncryptResultSize(Key key, int srcSize) {
        int blockSize = key.getModulus().bitLength() / 8;
        return (srcSize / 245 + (srcSize % 245 != 0 ? 1 : 0)) * blockSize;
    }
    
    /**
     * 使用RSA解密文本
     *
     * @param encryptedText Base64编码的密文
     * @param privateKeyBase64 Base64编码的私钥
     * @return 解密后的明文
     */
    public String decryptRSA(String encryptedText, String privateKeyBase64) throws Exception {
        if (encryptedText == null || privateKeyBase64 == null) {
            return null;
        }
        
        // 解码私钥
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        
        // 创建解密器
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        
        // 解码密文
        byte[] encryptedData = Base64.getDecoder().decode(encryptedText);
        
        // 执行解密（RSA解密也需要分块）
        int maxBlockSize = privateKey.getModulus().bitLength() / 8;
        int offSet = 0;
        int i = 0;
        byte[] cache;
        int inputLen = encryptedData.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        while (inputLen - offSet > 0) {
            if (inputLen - offSet > maxBlockSize) {
                cache = cipher.doFinal(encryptedData, offSet, maxBlockSize);
            } else {
                cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * maxBlockSize;
        }
        
        byte[] decryptedData = out.toByteArray();
        out.close();
        
        // 转换为字符串
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
    
    /**
     * 计算字符串的哈希值
     *
     * @param input 输入字符串
     * @return 哈希字符串（十六进制）
     */
    public String hashString(String input) throws NoSuchAlgorithmException {
        if (input == null) {
            return null;
        }
        
        // 创建消息摘要
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        
        // 转换为十六进制字符串
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    /**
     * 生成数字签名
     *
     * @param data 待签名数据
     * @param privateKeyBase64 Base64编码的私钥
     * @return Base64编码的签名
     */
    public String sign(byte[] data, String privateKeyBase64) throws Exception {
        if (data == null || privateKeyBase64 == null) {
            return null;
        }
        
        // 解码私钥
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        
        // 创建签名实例
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        
        // 生成签名
        byte[] signedBytes = signature.sign();
        
        // Base64编码
        return Base64.getEncoder().encodeToString(signedBytes);
    }
    
    /**
     * 验证数字签名
     *
     * @param data 原始数据
     * @param signatureBase64 Base64编码的签名
     * @param publicKeyBase64 Base64编码的公钥
     * @return 签名是否有效
     */
    public boolean verifySignature(byte[] data, String signatureBase64, String publicKeyBase64) throws Exception {
        if (data == null || signatureBase64 == null || publicKeyBase64 == null) {
            return false;
        }
        
        // 解码公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        
        // 解码签名
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        
        // 验证签名
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        
        return signature.verify(signatureBytes);
    }
    
    /**
     * 生成安全随机字节
     *
     * @param length 字节数
     * @return 随机字节数组
     */
    public byte[] generateRandomBytes(int length) {
        byte[] randomBytes = new byte[length];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);
        return randomBytes;
    }
    
    /**
     * 生成安全随机令牌
     *
     * @param length 令牌长度
     * @return Base64编码的随机令牌
     */
    public String generateRandomToken(int length) {
        byte[] randomBytes = generateRandomBytes(length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}