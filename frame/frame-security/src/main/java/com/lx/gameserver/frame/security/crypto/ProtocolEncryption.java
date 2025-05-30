/*
 * 文件名: ProtocolEncryption.java
 * 用途: 通信协议加密
 * 实现内容:
 *   - 握手协议实现
 *   - 会话密钥协商
 *   - 消息加解密
 *   - 完整性校验
 *   - 重放攻击防护
 * 技术选型:
 *   - Diffie-Hellman密钥交换
 *   - AES对称加密
 *   - HMAC消息认证
 * 依赖关系:
 *   - 被网络模块使用
 *   - 使用GameCryptoService
 */
package com.lx.gameserver.frame.security.crypto;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通信协议加密
 * <p>
 * 提供游戏客户端与服务器之间通信协议的加密，包括握手协议、
 * 会话密钥协商、消息加解密、完整性校验和重放攻击防护。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class ProtocolEncryption {

    /**
     * GCM认证标签长度（位）
     */
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * GCM IV长度（字节）
     */
    private static final int GCM_IV_LENGTH = 12;
    
    /**
     * 消息计数器长度（字节）
     */
    private static final int COUNTER_LENGTH = 8;
    
    /**
     * 会话密钥缓存
     * Key: 会话ID
     * Value: 会话密钥
     */
    private final Map<String, SessionKeyInfo> sessionKeyCache;
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 加密服务
     */
    private final GameCryptoService cryptoService;
    
    /**
     * 服务器密钥对（用于密钥交换）
     */
    private KeyPair serverKeyPair;
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param cryptoService 加密服务
     */
    public ProtocolEncryption(SecurityProperties securityProperties, GameCryptoService cryptoService) {
        this.securityProperties = securityProperties;
        this.cryptoService = cryptoService;
        this.sessionKeyCache = new ConcurrentHashMap<>();
        
        // 初始化服务器密钥对
        initServerKeyPair();
        
        log.info("协议加密服务初始化完成");
    }
    
    /**
     * 初始化服务器密钥对（用于密钥交换）
     */
    private void initServerKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256); // P-256曲线
            serverKeyPair = keyPairGenerator.generateKeyPair();
            log.debug("服务器ECDH密钥对生成完成");
        } catch (Exception e) {
            log.error("生成服务器密钥对失败", e);
        }
    }
    
    /**
     * 处理客户端握手请求，生成服务器握手响应
     *
     * @param clientHello 客户端握手请求（包含客户端公钥）
     * @return 服务器握手响应（包含服务器公钥和会话ID）
     */
    public ServerHandshakeResponse handleClientHandshake(ClientHandshakeRequest clientHello) {
        try {
            // 解码客户端公钥
            byte[] clientPublicKeyBytes = Base64.getDecoder().decode(clientHello.getClientPublicKey());
            PublicKey clientPublicKey = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(clientPublicKeyBytes));
            
            // 使用ECDH生成共享密钥
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(serverKeyPair.getPrivate());
            keyAgreement.doPhase(clientPublicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // 生成会话ID
            String sessionId = UUID.randomUUID().toString();
            
            // 生成会话密钥（使用SHA-256导出密钥）
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] sessionKeyBytes = sha256.digest(sharedSecret);
            SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, "AES");
            
            // 缓存会话密钥信息
            SessionKeyInfo sessionKeyInfo = new SessionKeyInfo(sessionKey, 0L);
            sessionKeyCache.put(sessionId, sessionKeyInfo);
            
            // 构建服务器响应
            String serverPublicKeyBase64 = Base64.getEncoder().encodeToString(
                    serverKeyPair.getPublic().getEncoded());
            
            log.debug("握手完成，生成会话ID: {}", sessionId);
            
            return new ServerHandshakeResponse(
                    sessionId,
                    serverPublicKeyBase64,
                    clientHello.getClientNonce(),
                    generateServerNonce()
            );
            
        } catch (Exception e) {
            log.error("处理客户端握手失败", e);
            throw new RuntimeException("握手失败", e);
        }
    }
    
    /**
     * 生成服务器随机数
     *
     * @return Base64编码的服务器随机数
     */
    private String generateServerNonce() {
        byte[] nonce = cryptoService.generateRandomBytes(16);
        return Base64.getEncoder().encodeToString(nonce);
    }
    
    /**
     * 加密消息
     *
     * @param sessionId 会话ID
     * @param plaintext 明文消息
     * @return 加密后的消息
     */
    public EncryptedMessage encryptMessage(String sessionId, byte[] plaintext) {
        // 获取会话密钥
        SessionKeyInfo keyInfo = sessionKeyCache.get(sessionId);
        if (keyInfo == null) {
            log.warn("会话密钥不存在: {}", sessionId);
            throw new IllegalStateException("会话不存在或已过期");
        }
        
        try {
            // 获取并递增消息计数器
            long counter = keyInfo.incrementCounter();
            
            // 生成IV（结合会话ID和计数器）
            byte[] iv = generateIv(sessionId, counter);
            
            // 创建AES-GCM加密器
            SecretKey sessionKey = keyInfo.getSessionKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, gcmParameterSpec);
            
            // 将计数器作为关联数据添加到GCM中（防重放）
            byte[] counterBytes = longToBytes(counter);
            cipher.updateAAD(counterBytes);
            
            // 执行加密
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            // 构建加密消息
            return new EncryptedMessage(
                    sessionId,
                    counter,
                    Base64.getEncoder().encodeToString(ciphertext)
            );
            
        } catch (Exception e) {
            log.error("加密消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("消息加密失败", e);
        }
    }
    
    /**
     * 解密消息
     *
     * @param encryptedMessage 加密消息
     * @return 解密后的明文
     */
    public byte[] decryptMessage(EncryptedMessage encryptedMessage) {
        String sessionId = encryptedMessage.getSessionId();
        long counter = encryptedMessage.getCounter();
        
        // 获取会话密钥
        SessionKeyInfo keyInfo = sessionKeyCache.get(sessionId);
        if (keyInfo == null) {
            log.warn("会话密钥不存在: {}", sessionId);
            throw new IllegalStateException("会话不存在或已过期");
        }
        
        // 检查消息计数器（防重放）
        if (counter <= keyInfo.getLastReceivedCounter()) {
            log.warn("检测到消息重放攻击: sessionId={}, counter={}, lastReceived={}",
                    sessionId, counter, keyInfo.getLastReceivedCounter());
            throw new SecurityException("检测到消息重放");
        }
        
        try {
            // 解码密文
            byte[] ciphertext = Base64.getDecoder().decode(encryptedMessage.getCiphertext());
            
            // 生成IV（结合会话ID和计数器）
            byte[] iv = generateIv(sessionId, counter);
            
            // 创建AES-GCM解密器
            SecretKey sessionKey = keyInfo.getSessionKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, gcmParameterSpec);
            
            // 将计数器作为关联数据添加到GCM中
            byte[] counterBytes = longToBytes(counter);
            cipher.updateAAD(counterBytes);
            
            // 执行解密
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            // 更新最后接收的计数器
            keyInfo.setLastReceivedCounter(counter);
            
            return plaintext;
            
        } catch (Exception e) {
            log.error("解密消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("消息解密失败", e);
        }
    }
    
    /**
     * 生成IV（初始化向量）
     * 
     * @param sessionId 会话ID
     * @param counter 消息计数器
     * @return IV字节数组
     */
    private byte[] generateIv(String sessionId, long counter) throws NoSuchAlgorithmException {
        // 使用会话ID生成确定性IV基础
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(sessionId.getBytes());
        byte[] hash = md.digest();
        
        // 将消息计数器与哈希结合
        ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH);
        buffer.put(hash, 0, GCM_IV_LENGTH - COUNTER_LENGTH); // 取哈希的前4字节
        buffer.putLong(counter); // 加入8字节的计数器
        
        return buffer.array();
    }
    
    /**
     * 将长整型转换为字节数组
     *
     * @param value 长整型值
     * @return 字节数组
     */
    private byte[] longToBytes(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }
    
    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        sessionKeyCache.remove(sessionId);
        log.debug("已移除会话: {}", sessionId);
    }
    
    /**
     * 会话密钥信息
     */
    private static class SessionKeyInfo {
        /**
         * 会话密钥
         */
        private final SecretKey sessionKey;
        
        /**
         * 发送消息计数器
         */
        private long counter;
        
        /**
         * 最后接收的消息计数器
         */
        private long lastReceivedCounter;
        
        /**
         * 构造函数
         *
         * @param sessionKey 会话密钥
         * @param initialCounter 初始计数器值
         */
        public SessionKeyInfo(SecretKey sessionKey, long initialCounter) {
            this.sessionKey = sessionKey;
            this.counter = initialCounter;
            this.lastReceivedCounter = initialCounter - 1;
        }
        
        /**
         * 获取会话密钥
         *
         * @return 会话密钥
         */
        public SecretKey getSessionKey() {
            return sessionKey;
        }
        
        /**
         * 递增并获取计数器
         *
         * @return 递增后的计数器值
         */
        public synchronized long incrementCounter() {
            return ++counter;
        }
        
        /**
         * 获取最后接收的消息计数器
         *
         * @return 最后接收的消息计数器
         */
        public long getLastReceivedCounter() {
            return lastReceivedCounter;
        }
        
        /**
         * 设置最后接收的消息计数器
         *
         * @param lastReceivedCounter 最后接收的消息计数器
         */
        public void setLastReceivedCounter(long lastReceivedCounter) {
            this.lastReceivedCounter = lastReceivedCounter;
        }
    }
    
    /**
     * 客户端握手请求
     */
    public static class ClientHandshakeRequest {
        private final String clientPublicKey;
        private final String clientNonce;
        private final String clientInfo;
        
        public ClientHandshakeRequest(String clientPublicKey, String clientNonce, String clientInfo) {
            this.clientPublicKey = clientPublicKey;
            this.clientNonce = clientNonce;
            this.clientInfo = clientInfo;
        }
        
        public String getClientPublicKey() {
            return clientPublicKey;
        }
        
        public String getClientNonce() {
            return clientNonce;
        }
        
        public String getClientInfo() {
            return clientInfo;
        }
    }
    
    /**
     * 服务器握手响应
     */
    public static class ServerHandshakeResponse {
        private final String sessionId;
        private final String serverPublicKey;
        private final String clientNonce;
        private final String serverNonce;
        
        public ServerHandshakeResponse(String sessionId, String serverPublicKey, 
                                     String clientNonce, String serverNonce) {
            this.sessionId = sessionId;
            this.serverPublicKey = serverPublicKey;
            this.clientNonce = clientNonce;
            this.serverNonce = serverNonce;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public String getServerPublicKey() {
            return serverPublicKey;
        }
        
        public String getClientNonce() {
            return clientNonce;
        }
        
        public String getServerNonce() {
            return serverNonce;
        }
    }
    
    /**
     * 加密消息
     */
    public static class EncryptedMessage {
        private final String sessionId;
        private final long counter;
        private final String ciphertext;
        
        public EncryptedMessage(String sessionId, long counter, String ciphertext) {
            this.sessionId = sessionId;
            this.counter = counter;
            this.ciphertext = ciphertext;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public long getCounter() {
            return counter;
        }
        
        public String getCiphertext() {
            return ciphertext;
        }
    }
}