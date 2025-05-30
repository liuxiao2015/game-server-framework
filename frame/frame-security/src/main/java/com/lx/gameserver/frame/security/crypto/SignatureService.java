/*
 * 文件名: SignatureService.java
 * 用途: 数字签名服务
 * 实现内容:
 *   - 请求签名验证
 *   - 数据完整性校验
 *   - 防篡改机制
 *   - 时间戳验证
 *   - 签名算法管理
 * 技术选型:
 *   - RSA/ECDSA数字签名
 *   - 消息摘要
 *   - HMAC验证
 * 依赖关系:
 *   - 被网络模块使用
 *   - 使用KeyManager
 */
package com.lx.gameserver.frame.security.crypto;

import com.lx.gameserver.frame.security.keystore.KeyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 数字签名服务
 * <p>
 * 提供数字签名生成与验证功能，用于确保数据完整性
 * 和防篡改，支持多种签名算法和时间戳验证机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class SignatureService {

    /**
     * 签名算法映射
     */
    private static final Map<String, String> ALGORITHMS = Map.of(
            "RSA", "SHA256withRSA",
            "ECDSA", "SHA256withECDSA",
            "HMAC", "HmacSHA256"
    );
    
    /**
     * 密钥缓存
     */
    private final Map<String, Key> keyCache = new ConcurrentHashMap<>();
    
    /**
     * 密钥管理器
     */
    @Nullable
    private final KeyManager keyManager;
    
    /**
     * 加密服务
     */
    private final GameCryptoService cryptoService;
    
    /**
     * 默认签名有效期（毫秒）
     */
    private static final long DEFAULT_SIGNATURE_TTL = TimeUnit.MINUTES.toMillis(5);
    
    /**
     * 默认时间偏差允许值（毫秒）
     */
    private static final long DEFAULT_TIME_SKEW_ALLOWANCE = TimeUnit.MINUTES.toMillis(1);
    
    /**
     * 构造函数
     *
     * @param keyManager 密钥管理器
     * @param cryptoService 加密服务
     */
    public SignatureService(@Nullable KeyManager keyManager, GameCryptoService cryptoService) {
        this.keyManager = keyManager;
        this.cryptoService = cryptoService;
        log.info("数字签名服务初始化完成");
    }
    
    /**
     * 使用私钥生成签名
     *
     * @param data 要签名的数据
     * @param privateKeyBase64 Base64编码的私钥
     * @param algorithm 签名算法（RSA, ECDSA）
     * @return Base64编码的签名
     */
    public String sign(byte[] data, String privateKeyBase64, String algorithm) {
        try {
            String actualAlgorithm = ALGORITHMS.getOrDefault(algorithm, "SHA256withRSA");
            
            // 解码私钥
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            
            // 创建签名实例
            Signature signature = Signature.getInstance(actualAlgorithm);
            signature.initSign(privateKey);
            signature.update(data);
            
            // 生成签名并Base64编码
            byte[] signedBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signedBytes);
            
        } catch (Exception e) {
            log.error("生成签名失败: {}", e.getMessage(), e);
            throw new RuntimeException("签名失败", e);
        }
    }
    
    /**
     * 使用公钥验证签名
     *
     * @param data 原始数据
     * @param signatureBase64 Base64编码的签名
     * @param publicKeyBase64 Base64编码的公钥
     * @param algorithm 签名算法（RSA, ECDSA）
     * @return 签名是否有效
     */
    public boolean verifySignature(byte[] data, String signatureBase64, String publicKeyBase64, String algorithm) {
        try {
            String actualAlgorithm = ALGORITHMS.getOrDefault(algorithm, "SHA256withRSA");
            
            // 解码公钥
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            // 解码签名
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            
            // 验证签名
            Signature signature = Signature.getInstance(actualAlgorithm);
            signature.initVerify(publicKey);
            signature.update(data);
            
            return signature.verify(signatureBytes);
            
        } catch (Exception e) {
            log.error("验证签名失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 使用HMAC生成签名
     *
     * @param data 要签名的数据
     * @param secretKey 密钥
     * @return Base64编码的签名
     */
    public String signWithHmac(byte[] data, String secretKey) {
        try {
            // 创建HMAC实例
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            
            // 计算HMAC
            byte[] hmacBytes = mac.doFinal(data);
            return Base64.getEncoder().encodeToString(hmacBytes);
            
        } catch (Exception e) {
            log.error("生成HMAC签名失败: {}", e.getMessage(), e);
            throw new RuntimeException("HMAC签名失败", e);
        }
    }
    
    /**
     * 验证HMAC签名
     *
     * @param data 原始数据
     * @param hmacBase64 Base64编码的HMAC值
     * @param secretKey 密钥
     * @return HMAC是否有效
     */
    public boolean verifyHmac(byte[] data, String hmacBase64, String secretKey) {
        try {
            String calculatedHmac = signWithHmac(data, secretKey);
            return calculatedHmac.equals(hmacBase64);
            
        } catch (Exception e) {
            log.error("验证HMAC签名失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 生成带时间戳的签名
     *
     * @param params 请求参数（会按字母顺序排序）
     * @param secretKey HMAC密钥
     * @return 签名结果，包含时间戳和签名
     */
    public SignedRequest signRequest(Map<String, String> params, String secretKey) {
        // 获取当前时间戳
        long timestamp = Instant.now().toEpochMilli();
        
        // 创建参数的副本，按字母顺序排序
        SortedMap<String, String> sortedParams = new TreeMap<>(params);
        sortedParams.put("timestamp", String.valueOf(timestamp));
        
        // 构建签名字符串
        StringBuilder stringToSign = new StringBuilder();
        sortedParams.forEach((key, value) -> 
                stringToSign.append(key).append('=').append(value).append('&'));
        
        // 移除最后一个 & 字符
        String signString = stringToSign.substring(0, stringToSign.length() - 1);
        
        // 计算签名
        String signature = signWithHmac(signString.getBytes(StandardCharsets.UTF_8), secretKey);
        
        return new SignedRequest(sortedParams, timestamp, signature);
    }
    
    /**
     * 验证请求签名
     *
     * @param params 请求参数（包含时间戳和签名）
     * @param signature 签名
     * @param secretKey HMAC密钥
     * @param ttl 签名有效期（毫秒），如果为null则使用默认值
     * @return 签名是否有效
     */
    public boolean verifyRequestSignature(Map<String, String> params, String signature, 
                                        String secretKey, @Nullable Long ttl) {
        // 检查时间戳
        String timestampStr = params.get("timestamp");
        if (!StringUtils.hasText(timestampStr)) {
            log.debug("缺少时间戳参数");
            return false;
        }
        
        try {
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = Instant.now().toEpochMilli();
            long validityPeriod = ttl != null ? ttl : DEFAULT_SIGNATURE_TTL;
            
            // 检查时间戳是否在有效期内
            if (Math.abs(currentTime - timestamp) > validityPeriod) {
                log.debug("请求超时或时间戳无效: current={}, request={}, diff={}ms", 
                        currentTime, timestamp, Math.abs(currentTime - timestamp));
                return false;
            }
            
            // 创建参数的副本，按字母顺序排序，但不包括签名本身
            SortedMap<String, String> sortedParams = new TreeMap<>(params);
            
            // 构建签名字符串
            StringBuilder stringToSign = new StringBuilder();
            sortedParams.forEach((key, value) -> 
                    stringToSign.append(key).append('=').append(value).append('&'));
            
            // 移除最后一个 & 字符
            String signString = stringToSign.substring(0, stringToSign.length() - 1);
            
            // 计算签名
            String calculatedSignature = signWithHmac(signString.getBytes(StandardCharsets.UTF_8), secretKey);
            
            // 验证签名
            boolean result = calculatedSignature.equals(signature);
            if (!result) {
                log.debug("签名验证失败: expected={}, actual={}", calculatedSignature, signature);
            }
            
            return result;
            
        } catch (NumberFormatException e) {
            log.debug("时间戳格式无效: {}", timestampStr);
            return false;
        } catch (Exception e) {
            log.error("验证请求签名失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 带签名的请求
     */
    public static class SignedRequest {
        private final Map<String, String> parameters;
        private final long timestamp;
        private final String signature;
        
        public SignedRequest(Map<String, String> parameters, long timestamp, String signature) {
            this.parameters = parameters;
            this.timestamp = timestamp;
            this.signature = signature;
        }
        
        public Map<String, String> getParameters() {
            return parameters;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getSignature() {
            return signature;
        }
    }
}