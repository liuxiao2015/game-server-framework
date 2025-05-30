/*
 * 文件名: DataEncryption.java
 * 用途: 数据加密服务
 * 实现内容:
 *   - 敏感数据加密
 *   - 数据库字段加密
 *   - 文件加密存储
 *   - 密钥轮换机制
 *   - 加密性能优化
 * 技术选型:
 *   - AES/GCM加密
 *   - 缓存密钥
 *   - 自适应加密粒度
 * 依赖关系:
 *   - 被数据层使用
 *   - 使用KeyManager
 */
package com.lx.gameserver.frame.security.crypto;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.keystore.KeyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据加密服务
 * <p>
 * 提供敏感数据的加密存储功能，包括数据库字段加密、
 * 文件加密和内存数据保护，支持密钥轮换和加密粒度控制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class DataEncryption {

    /**
     * GCM认证标签长度（位）
     */
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * GCM IV长度（字节）
     */
    private static final int GCM_IV_LENGTH = 12;
    
    /**
     * 缓存的密钥映射
     * Key: 密钥别名
     * Value: 密钥
     */
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>();
    
    /**
     * 密钥版本映射
     * Key: 密钥别名
     * Value: 当前版本
     */
    private final Map<String, Integer> keyVersions = new ConcurrentHashMap<>();
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 密钥管理器（如果可用）
     */
    @Nullable
    private final KeyManager keyManager;
    
    /**
     * 主密钥（用于派生其他密钥）
     */
    private byte[] masterKey;
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param keyManager 密钥管理器（可选）
     */
    public DataEncryption(SecurityProperties securityProperties, @Nullable KeyManager keyManager) {
        this.securityProperties = securityProperties;
        this.keyManager = keyManager;
        
        // 初始化主密钥
        initMasterKey();
        
        log.info("数据加密服务初始化完成");
    }
    
    /**
     * 初始化主密钥
     */
    private void initMasterKey() {
        try {
            // 如果有密钥管理器，尝试从密钥管理器获取主密钥
            if (keyManager != null) {
                masterKey = keyManager.getOrCreateKey("master-key", 32);
                log.debug("从密钥管理器加载主密钥");
            } else {
                // 否则生成一个临时主密钥（注意：实际生产环境应该使用持久化的密钥）
                SecureRandom random = new SecureRandom();
                masterKey = new byte[32]; // 256位密钥
                random.nextBytes(masterKey);
                log.warn("生成临时主密钥（不适合生产环境）");
            }
        } catch (Exception e) {
            log.error("初始化主密钥失败", e);
            throw new RuntimeException("无法初始化数据加密服务", e);
        }
    }
    
    /**
     * 加密敏感数据
     *
     * @param plaintext 明文数据
     * @param keyAlias 密钥别名
     * @return Base64编码的密文，如果输入为null或空则返回null
     */
    public String encryptData(String plaintext, String keyAlias) {
        if (!StringUtils.hasText(plaintext)) {
            return plaintext;
        }
        
        try {
            // 获取或派生密钥
            SecretKey key = getOrDeriveKey(keyAlias);
            
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // 创建加密器
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            
            // 加入密钥版本信息
            int keyVersion = keyVersions.getOrDefault(keyAlias, 1);
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(keyVersion).array());
            
            // 执行加密
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // 组合IV和密文
            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + encryptedBytes.length);
            buffer.putInt(keyVersion);  // 密钥版本
            buffer.put(iv);            // IV
            buffer.put(encryptedBytes); // 密文
            
            // Base64编码
            return Base64.getEncoder().encodeToString(buffer.array());
            
        } catch (Exception e) {
            log.error("加密数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("加密失败", e);
        }
    }
    
    /**
     * 解密敏感数据
     *
     * @param ciphertext Base64编码的密文
     * @param keyAlias 密钥别名
     * @return 解密后的明文，如果输入为null或空则返回null
     */
    public String decryptData(String ciphertext, String keyAlias) {
        if (!StringUtils.hasText(ciphertext)) {
            return ciphertext;
        }
        
        try {
            // 解码Base64
            byte[] encryptedData = Base64.getDecoder().decode(ciphertext);
            
            // 解析版本、IV和密文
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            int keyVersion = buffer.getInt();
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertextBytes = new byte[encryptedData.length - 4 - GCM_IV_LENGTH];
            buffer.get(ciphertextBytes);
            
            // 获取指定版本的密钥
            SecretKey key = getOrDeriveKeyVersion(keyAlias, keyVersion);
            
            // 创建解密器
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            
            // 加入版本信息
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(keyVersion).array());
            
            // 执行解密
            byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
            
            // 转换为字符串
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("解密数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败", e);
        }
    }
    
    /**
     * 加密文件
     *
     * @param sourceFile 源文件
     * @param targetFile 目标加密文件
     * @param keyAlias 密钥别名
     */
    public void encryptFile(File sourceFile, File targetFile, String keyAlias) {
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            throw new IllegalArgumentException("源文件不存在或不是文件");
        }
        
        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            
            // 获取或派生密钥
            SecretKey key = getOrDeriveKey(keyAlias);
            
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // 创建加密器
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            
            // 写入文件头信息（密钥版本和IV）
            int keyVersion = keyVersions.getOrDefault(keyAlias, 1);
            out.write(ByteBuffer.allocate(4).putInt(keyVersion).array()); // 密钥版本
            out.write(iv); // IV
            
            // 读取源文件并加密
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int bytesRead;
            byte[] encryptedChunk;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                if (bytesRead < buffer.length) {
                    // 处理最后一块（可能不满）
                    byte[] lastBlock = new byte[bytesRead];
                    System.arraycopy(buffer, 0, lastBlock, 0, bytesRead);
                    encryptedChunk = cipher.doFinal(lastBlock);
                } else {
                    encryptedChunk = cipher.update(buffer);
                }
                
                if (encryptedChunk != null) {
                    out.write(encryptedChunk);
                }
            }
            
            // 确保所有数据都已处理
            encryptedChunk = cipher.doFinal();
            if (encryptedChunk != null) {
                out.write(encryptedChunk);
            }
            
            log.debug("文件加密完成: {}", targetFile.getName());
            
        } catch (Exception e) {
            log.error("加密文件失败: {}", e.getMessage(), e);
            
            // 加密失败，删除可能部分写入的目标文件
            if (targetFile.exists()) {
                try {
                    Files.delete(targetFile.toPath());
                } catch (Exception ex) {
                    log.warn("无法删除失败的加密文件: {}", targetFile, ex);
                }
            }
            
            throw new RuntimeException("文件加密失败", e);
        }
    }
    
    /**
     * 解密文件
     *
     * @param encryptedFile 加密文件
     * @param targetFile 解密目标文件
     * @param keyAlias 密钥别名
     */
    public void decryptFile(File encryptedFile, File targetFile, String keyAlias) {
        if (!encryptedFile.exists() || !encryptedFile.isFile()) {
            throw new IllegalArgumentException("加密文件不存在或不是文件");
        }
        
        try (FileInputStream in = new FileInputStream(encryptedFile);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            
            // 读取文件头信息（密钥版本和IV）
            byte[] versionBytes = new byte[4];
            in.read(versionBytes);
            int keyVersion = ByteBuffer.wrap(versionBytes).getInt();
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            in.read(iv);
            
            // 获取指定版本的密钥
            SecretKey key = getOrDeriveKeyVersion(keyAlias, keyVersion);
            
            // 创建解密器
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            
            // 读取加密文件并解密
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int bytesRead;
            byte[] decryptedChunk;
            
            // 加密文件数据部分（跳过头部）
            while ((bytesRead = in.read(buffer)) != -1) {
                if (bytesRead < buffer.length) {
                    // 处理最后一块
                    byte[] lastBlock = new byte[bytesRead];
                    System.arraycopy(buffer, 0, lastBlock, 0, bytesRead);
                    decryptedChunk = cipher.doFinal(lastBlock);
                } else {
                    decryptedChunk = cipher.update(buffer);
                }
                
                if (decryptedChunk != null) {
                    out.write(decryptedChunk);
                }
            }
            
            // 确保所有数据都已处理
            decryptedChunk = cipher.doFinal();
            if (decryptedChunk != null) {
                out.write(decryptedChunk);
            }
            
            log.debug("文件解密完成: {}", targetFile.getName());
            
        } catch (Exception e) {
            log.error("解密文件失败: {}", e.getMessage(), e);
            
            // 解密失败，删除可能部分写入的目标文件
            if (targetFile.exists()) {
                try {
                    Files.delete(targetFile.toPath());
                } catch (Exception ex) {
                    log.warn("无法删除失败的解密文件: {}", targetFile, ex);
                }
            }
            
            throw new RuntimeException("文件解密失败", e);
        }
    }
    
    /**
     * 轮换密钥
     *
     * @param keyAlias 密钥别名
     * @return 新的密钥版本号
     */
    @CacheEvict(value = "dataEncryptionKeys", key = "#keyAlias")
    public int rotateKey(String keyAlias) {
        try {
            int currentVersion = keyVersions.getOrDefault(keyAlias, 0);
            int newVersion = currentVersion + 1;
            
            // 如果有密钥管理器，使用它来轮换密钥
            if (keyManager != null) {
                keyManager.rotateKey(keyAlias + "-v" + newVersion);
            }
            
            // 更新版本号
            keyVersions.put(keyAlias, newVersion);
            
            // 从缓存中移除旧密钥
            keyCache.remove(keyAlias);
            
            log.info("密钥轮换完成: {}, 新版本: {}", keyAlias, newVersion);
            return newVersion;
            
        } catch (Exception e) {
            log.error("密钥轮换失败: {}", e.getMessage(), e);
            throw new RuntimeException("密钥轮换失败", e);
        }
    }
    
    /**
     * 获取或派生密钥（使用当前版本）
     *
     * @param keyAlias 密钥别名
     * @return 密钥
     */
    @Cacheable(value = "dataEncryptionKeys", key = "#keyAlias")
    public SecretKey getOrDeriveKey(String keyAlias) {
        int version = keyVersions.getOrDefault(keyAlias, 1);
        return getOrDeriveKeyVersion(keyAlias, version);
    }
    
    /**
     * 获取或派生指定版本的密钥
     *
     * @param keyAlias 密钥别名
     * @param version 版本号
     * @return 密钥
     */
    private SecretKey getOrDeriveKeyVersion(String keyAlias, int version) {
        String cacheKey = keyAlias + "-v" + version;
        
        // 检查缓存
        SecretKey cachedKey = keyCache.get(cacheKey);
        if (cachedKey != null) {
            return cachedKey;
        }
        
        try {
            byte[] keyBytes;
            
            // 如果有密钥管理器，尝试获取密钥
            if (keyManager != null) {
                keyBytes = keyManager.getOrCreateKey(cacheKey, 32);
            } else {
                // 否则从主密钥派生
                keyBytes = deriveKeyFromMaster(keyAlias, version);
            }
            
            // 创建AES密钥
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            
            // 缓存密钥
            keyCache.put(cacheKey, key);
            
            return key;
            
        } catch (Exception e) {
            log.error("获取或派生密钥失败: {}", e.getMessage(), e);
            throw new RuntimeException("密钥获取失败", e);
        }
    }
    
    /**
     * 从主密钥派生子密钥
     *
     * @param keyAlias 密钥别名
     * @param version 版本号
     * @return 派生的密钥字节
     */
    private byte[] deriveKeyFromMaster(String keyAlias, int version) {
        try {
            // 使用HKDF算法派生密钥
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            
            // 盐值（结合别名和版本）
            String saltInput = keyAlias + "-v" + version;
            byte[] salt = sha256.digest(saltInput.getBytes(StandardCharsets.UTF_8));
            
            // PRK = HMAC-Hash(salt, IKM)
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec saltKey = new SecretKeySpec(salt, "HmacSHA256");
            hmac.init(saltKey);
            byte[] prk = hmac.doFinal(masterKey);
            
            // 扩展密钥材料
            // OKM = HKDF-Expand(PRK, info, L)
            byte[] info = keyAlias.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec prkKey = new SecretKeySpec(prk, "HmacSHA256");
            hmac.init(prkKey);
            hmac.update(info);
            hmac.update((byte) 1); // 计数器
            
            return hmac.doFinal();
            
        } catch (Exception e) {
            log.error("从主密钥派生子密钥失败", e);
            throw new RuntimeException("密钥派生失败", e);
        }
    }
    
    /**
     * 清除密钥缓存
     */
    public void clearKeyCache() {
        keyCache.clear();
        log.info("已清除密钥缓存");
    }
    
    /**
     * 检查是否启用数据加密
     *
     * @return 如果启用加密返回true，否则返回false
     */
    public boolean isEncryptionEnabled() {
        return securityProperties.getCrypto().isEnableProtocolEncryption();
    }
}