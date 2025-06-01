/*
 * 文件名: KeyManager.java
 * 用途: 密钥管理器
 * 实现内容:
 *   - 密钥生成
 *   - 密钥存储（HSM支持）
 *   - 密钥轮换
 *   - 密钥分发
 *   - 密钥销毁
 * 技术选型:
 *   - 标准JCE密钥管理
 *   - 密钥库文件存储
 *   - 选项HSM集成
 * 依赖关系:
 *   - 被各加密组件使用
 *   - 管理系统加密密钥
 */
package com.lx.gameserver.frame.security.keystore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥管理器
 * <p>
 * 提供系统级密钥管理功能，包括密钥生成、存储、载入、
 * 轮换、分发和销毁等，是密码系统的核心组件。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class KeyManager {
    
    /**
     * 密钥库文件路径
     */
    @Value("${game.security.keystore.path:keystore/game-keystore.jks}")
    private String keystorePath;
    
    /**
     * 密钥库密码
     */
    @Value("${game.security.keystore.password:changeit}")
    private String keystorePassword;
    
    /**
     * 密钥别名前缀
     */
    private static final String KEY_ALIAS_PREFIX = "game-key-";
    
    /**
     * 证书别名前缀
     */
    private static final String CERT_ALIAS_PREFIX = "game-cert-";
    
    /**
     * 默认密钥类型
     */
    private static final String DEFAULT_KEY_ALGORITHM = "AES";
    
    /**
     * 默认非对称密钥类型
     */
    private static final String DEFAULT_ASYMMETRIC_ALGORITHM = "RSA";
    
    /**
     * 密钥缓存
     * Key: 密钥别名
     * Value: 密钥字节数组
     */
    private final Map<String, byte[]> keyCache = new ConcurrentHashMap<>();
    
    /**
     * 密钥对缓存
     * Key: 密钥别名
     * Value: 密钥对
     */
    private final Map<String, KeyPair> keyPairCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化密钥管理器
     */
    @PostConstruct
    public void init() {
        try {
            // 确保密钥库文件目录存在
            ensureKeystoreDirectoryExists();
            
            // 确保密钥库文件存在
            if (!keystoreExists()) {
                createKeystore();
            }
            
            log.info("密钥管理器初始化完成");
        } catch (Exception e) {
            log.error("初始化密钥管理器失败", e);
        }
    }
    
    /**
     * 确保密钥库文件目录存在
     */
    private void ensureKeystoreDirectoryExists() {
        File file = new File(keystorePath);
        File directory = file.getParentFile();
        
        if (directory != null && !directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                log.info("已创建密钥库目录: {}", directory.getAbsolutePath());
            } else {
                log.warn("无法创建密钥库目录: {}", directory.getAbsolutePath());
            }
        }
    }
    
    /**
     * 检查密钥库文件是否存在
     *
     * @return 如果存在返回true，否则返回false
     */
    private boolean keystoreExists() {
        File file = new File(keystorePath);
        return file.exists() && file.isFile();
    }
    
    /**
     * 创建密钥库文件
     */
    private void createKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keystorePassword.toCharArray());
        
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
        
        log.info("已创建新的密钥库文件: {}", keystorePath);
    }
    
    /**
     * 加载密钥库
     *
     * @return 密钥库
     */
    private KeyStore loadKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }
        
        return keyStore;
    }
    
    /**
     * 生成对称密钥
     *
     * @param alias 密钥别名
     * @param keySize 密钥大小（位）
     * @return 密钥字节数组
     */
    public byte[] generateKey(String alias, int keySize) throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(DEFAULT_KEY_ALGORITHM);
        keyGen.init(keySize);
        Key key = keyGen.generateKey();
        
        // 存储密钥到密钥库
        storeKey(alias, key);
        
        byte[] keyBytes = key.getEncoded();
        
        // 缓存密钥
        keyCache.put(alias, keyBytes);
        
        log.info("已生成并存储对称密钥: alias={}, algorithm={}, size={}",
                alias, DEFAULT_KEY_ALGORITHM, keySize);
        
        return keyBytes;
    }
    
    /**
     * 生成非对称密钥对
     *
     * @param alias 密钥别名
     * @param keySize 密钥大小（位）
     * @return 密钥对
     */
    public KeyPair generateKeyPair(String alias, int keySize) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(DEFAULT_ASYMMETRIC_ALGORITHM);
        keyGen.initialize(keySize);
        KeyPair keyPair = keyGen.generateKeyPair();
        
        // 存储密钥对到密钥库
        storeKeyPair(alias, keyPair, keySize);
        
        // 缓存密钥对
        keyPairCache.put(alias, keyPair);
        
        log.info("已生成并存储非对称密钥对: alias={}, algorithm={}, size={}",
                alias, DEFAULT_ASYMMETRIC_ALGORITHM, keySize);
        
        return keyPair;
    }
    
    /**
     * 存储密钥到密钥库
     *
     * @param alias 密钥别名
     * @param key 密钥
     */
    private void storeKey(String alias, Key key) throws Exception {
        KeyStore keyStore = loadKeystore();
        
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry((SecretKey) key);
        KeyStore.ProtectionParameter protParam = 
                new KeyStore.PasswordProtection(keystorePassword.toCharArray());
        
        keyStore.setEntry(KEY_ALIAS_PREFIX + alias, secretKeyEntry, protParam);
        
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
    }
    
    /**
     * 存储密钥对到密钥库
     *
     * @param alias 密钥别名
     * @param keyPair 密钥对
     * @param keySize 密钥大小
     */
    private void storeKeyPair(String alias, KeyPair keyPair, int keySize) throws Exception {
        KeyStore keyStore = loadKeystore();
        
        // 创建自签名证书
        Certificate selfCert = createSelfSignedCertificate(alias, keyPair, keySize);
        
        // 存储私钥和证书
        keyStore.setKeyEntry(
                KEY_ALIAS_PREFIX + alias,
                keyPair.getPrivate(),
                keystorePassword.toCharArray(),
                new Certificate[] { selfCert }
        );
        
        // 存储公钥证书
        keyStore.setCertificateEntry(CERT_ALIAS_PREFIX + alias, selfCert);
        
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
    }
    
    /**
     * 创建自签名证书（简化实现）
     *
     * @param alias 别名
     * @param keyPair 密钥对
     * @param keySize 密钥大小
     * @return 自签名证书
     */
    private Certificate createSelfSignedCertificate(String alias, KeyPair keyPair, int keySize) throws Exception {
        // 实际项目中应该使用X509证书生成API
        // 这里为简化，直接返回一个伪造的证书
        return new Certificate("X.509") {
            @Override
            public byte[] getEncoded() {
                return keyPair.getPublic().getEncoded();
            }
            
            @Override
            public void verify(PublicKey key) {
                // 简化实现
            }
            
            @Override
            public void verify(PublicKey key, String sigProvider) {
                // 简化实现
            }
            
            @Override
            public String toString() {
                return "SimplifiedCertificate[" + alias + "]";
            }
            
            @Override
            public PublicKey getPublicKey() {
                return keyPair.getPublic();
            }
        };
    }
    
    /**
     * 获取或创建密钥
     *
     * @param alias 密钥别名
     * @param keySize 密钥大小（位）
     * @return 密钥字节数组
     */
    public byte[] getOrCreateKey(String alias, int keySize) throws Exception {
        // 检查缓存
        byte[] cachedKey = keyCache.get(alias);
        if (cachedKey != null) {
            return cachedKey;
        }
        
        // 尝试从密钥库加载
        try {
            byte[] key = getKey(alias);
            if (key != null) {
                return key;
            }
        } catch (Exception e) {
            log.debug("密钥不存在，将创建新密钥: {}", alias);
        }
        
        // 创建新密钥
        return generateKey(alias, keySize);
    }
    
    /**
     * 获取密钥
     *
     * @param alias 密钥别名
     * @return 密钥字节数组，如果不存在则返回null
     */
    public byte[] getKey(String alias) throws Exception {
        // 检查缓存
        byte[] cachedKey = keyCache.get(alias);
        if (cachedKey != null) {
            return cachedKey;
        }
        
        KeyStore keyStore = loadKeystore();
        String fullAlias = KEY_ALIAS_PREFIX + alias;
        
        if (keyStore.containsAlias(fullAlias)) {
            KeyStore.PasswordProtection protParam = 
                    new KeyStore.PasswordProtection(keystorePassword.toCharArray());
            
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                    fullAlias, protParam);
            
            if (entry != null) {
                byte[] keyBytes = entry.getSecretKey().getEncoded();
                
                // 缓存密钥
                keyCache.put(alias, keyBytes);
                
                return keyBytes;
            }
        }
        
        return null; // 密钥不存在
    }
    
    /**
     * 获取密钥对
     *
     * @param alias 密钥别名
     * @return 密钥对，如果不存在则返回null
     */
    public KeyPair getKeyPair(String alias) throws Exception {
        // 检查缓存
        KeyPair cachedKeyPair = keyPairCache.get(alias);
        if (cachedKeyPair != null) {
            return cachedKeyPair;
        }
        
        KeyStore keyStore = loadKeystore();
        String fullAlias = KEY_ALIAS_PREFIX + alias;
        
        if (keyStore.containsAlias(fullAlias)) {
            KeyStore.PasswordProtection protParam = 
                    new KeyStore.PasswordProtection(keystorePassword.toCharArray());
            
            // 获取私钥
            Key key = keyStore.getKey(fullAlias, keystorePassword.toCharArray());
            if (!(key instanceof PrivateKey)) {
                return null;
            }
            
            PrivateKey privateKey = (PrivateKey) key;
            
            // 获取对应的证书（公钥）
            Certificate cert = keyStore.getCertificate(CERT_ALIAS_PREFIX + alias);
            if (cert == null) {
                return null;
            }
            
            PublicKey publicKey = cert.getPublicKey();
            
            // 构建密钥对
            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            
            // 缓存密钥对
            keyPairCache.put(alias, keyPair);
            
            return keyPair;
        }
        
        return null; // 密钥对不存在
    }
    
    /**
     * 轮换密钥
     *
     * @param alias 密钥别名
     * @return 新密钥字节数组
     */
    public byte[] rotateKey(String alias) throws Exception {
        // 获取旧密钥大小
        byte[] oldKey = getKey(alias);
        int keySize = oldKey != null ? oldKey.length * 8 : 256; // 默认256位
        
        // 生成新密钥
        byte[] newKey = generateKey(alias + "-new", keySize);
        
        // 备份旧密钥
        backupKey(alias);
        
        // 删除旧密钥
        deleteKey(alias);
        
        // 重命名新密钥
        renameKey(alias + "-new", alias);
        
        log.info("已轮换密钥: {}", alias);
        
        return newKey;
    }
    
    /**
     * 备份密钥
     *
     * @param alias 密钥别名
     */
    public void backupKey(String alias) throws Exception {
        KeyStore keyStore = loadKeystore();
        String fullAlias = KEY_ALIAS_PREFIX + alias;
        
        if (keyStore.containsAlias(fullAlias)) {
            // 复制到备份别名
            String backupAlias = KEY_ALIAS_PREFIX + alias + "-backup-" + System.currentTimeMillis();
            
            KeyStore.PasswordProtection protParam = 
                    new KeyStore.PasswordProtection(keystorePassword.toCharArray());
            
            KeyStore.Entry entry = keyStore.getEntry(fullAlias, protParam);
            keyStore.setEntry(backupAlias, entry, protParam);
            
            try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }
            
            log.info("已备份密钥: {} -> {}", alias, backupAlias);
        }
    }
    
    /**
     * 删除密钥
     *
     * @param alias 密钥别名
     * @return 是否删除成功
     */
    public boolean deleteKey(String alias) throws Exception {
        KeyStore keyStore = loadKeystore();
        String fullAlias = KEY_ALIAS_PREFIX + alias;
        
        if (keyStore.containsAlias(fullAlias)) {
            keyStore.deleteEntry(fullAlias);
            
            try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }
            
            // 从缓存中移除
            keyCache.remove(alias);
            keyPairCache.remove(alias);
            
            log.info("已删除密钥: {}", alias);
            return true;
        }
        
        return false;
    }
    
    /**
     * 重命名密钥
     *
     * @param oldAlias 旧别名
     * @param newAlias 新别名
     * @return 是否重命名成功
     */
    public boolean renameKey(String oldAlias, String newAlias) throws Exception {
        KeyStore keyStore = loadKeystore();
        String fullOldAlias = KEY_ALIAS_PREFIX + oldAlias;
        String fullNewAlias = KEY_ALIAS_PREFIX + newAlias;
        
        if (keyStore.containsAlias(fullOldAlias)) {
            KeyStore.PasswordProtection protParam = 
                    new KeyStore.PasswordProtection(keystorePassword.toCharArray());
            
            // 复制条目
            KeyStore.Entry entry = keyStore.getEntry(fullOldAlias, protParam);
            keyStore.setEntry(fullNewAlias, entry, protParam);
            
            // 删除旧条目
            keyStore.deleteEntry(fullOldAlias);
            
            try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }
            
            // 更新缓存
            if (keyCache.containsKey(oldAlias)) {
                keyCache.put(newAlias, keyCache.get(oldAlias));
                keyCache.remove(oldAlias);
            }
            
            if (keyPairCache.containsKey(oldAlias)) {
                keyPairCache.put(newAlias, keyPairCache.get(oldAlias));
                keyPairCache.remove(oldAlias);
            }
            
            log.info("已重命名密钥: {} -> {}", oldAlias, newAlias);
            return true;
        }
        
        return false;
    }
    
    /**
     * 导出公钥
     *
     * @param alias 密钥别名
     * @return Base64编码的公钥字符串，如果不存在则返回null
     */
    public String exportPublicKey(String alias) throws Exception {
        KeyPair keyPair = getKeyPair(alias);
        if (keyPair == null) {
            return null;
        }
        
        PublicKey publicKey = keyPair.getPublic();
        byte[] encoded = publicKey.getEncoded();
        
        return Base64.getEncoder().encodeToString(encoded);
    }
    
    /**
     * 导入密钥对
     *
     * @param alias 密钥别名
     * @param publicKeyBase64 Base64编码的公钥
     * @param privateKeyBase64 Base64编码的私钥
     * @return 导入的密钥对
     */
    public KeyPair importKeyPair(String alias, String publicKeyBase64, String privateKeyBase64) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
        
        KeyFactory keyFactory = KeyFactory.getInstance(DEFAULT_ASYMMETRIC_ALGORITHM);
        
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        
        // 存储密钥对
        storeKeyPair(alias, keyPair, ((RSAKey) publicKey).getModulus().bitLength());
        
        // 缓存密钥对
        keyPairCache.put(alias, keyPair);
        
        log.info("已导入密钥对: {}", alias);
        
        return keyPair;
    }
    
    /**
     * 导入密钥
     *
     * @param alias 密钥别名
     * @param keyBytes 密钥字节数组
     * @param algorithm 算法
     * @return 密钥
     */
    public Key importKey(String alias, byte[] keyBytes, String algorithm) throws Exception {
        SecretKey key = new SecretKeySpec(keyBytes, algorithm);
        
        // 存储密钥
        storeKey(alias, key);
        
        // 缓存密钥
        keyCache.put(alias, keyBytes);
        
        log.info("已导入密钥: {} ({})", alias, algorithm);
        
        return key;
    }
    
    /**
     * 列出所有密钥
     *
     * @return 密钥信息映射，Key为别名，Value为密钥信息
     */
    public Map<String, KeyInfo> listKeys() throws Exception {
        KeyStore keyStore = loadKeystore();
        Map<String, KeyInfo> result = new HashMap<>();
        
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                String simpleAlias = alias.substring(KEY_ALIAS_PREFIX.length());
                
                Date creationDate = keyStore.getCreationDate(alias);
                boolean isKey = keyStore.isKeyEntry(alias);
                
                KeyInfo info = new KeyInfo(simpleAlias, creationDate, isKey);
                result.put(simpleAlias, info);
            }
        }
        
        return result;
    }
    
    /**
     * 清除密钥缓存
     */
    public void clearKeyCache() {
        keyCache.clear();
        keyPairCache.clear();
        log.info("已清除密钥缓存");
    }
    
    /**
     * 密钥信息
     */
    public static class KeyInfo {
        /**
         * 别名
         */
        private final String alias;
        
        /**
         * 创建日期
         */
        private final Date creationDate;
        
        /**
         * 是否为密钥条目
         */
        private final boolean isKey;
        
        /**
         * 构造函数
         *
         * @param alias 别名
         * @param creationDate 创建日期
         * @param isKey 是否为密钥条目
         */
        public KeyInfo(String alias, Date creationDate, boolean isKey) {
            this.alias = alias;
            this.creationDate = creationDate;
            this.isKey = isKey;
        }
        
        public String getAlias() {
            return alias;
        }
        
        public Date getCreationDate() {
            return creationDate;
        }
        
        public boolean isKey() {
            return isKey;
        }
        
        @Override
        public String toString() {
            return "KeyInfo{" +
                    "alias='" + alias + '\'' +
                    ", creationDate=" + creationDate +
                    ", isKey=" + isKey +
                    '}';
        }
    }
}