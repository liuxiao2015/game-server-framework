/*
 * 文件名: CertificateManager.java
 * 用途: 证书管理器
 * 实现内容:
 *   - SSL证书管理
 *   - 证书链验证
 *   - 证书更新提醒
 *   - 自签名证书支持
 *   - 证书吊销检查
 * 技术选型:
 *   - Java证书API
 *   - SSL证书处理
 *   - X.509证书标准
 * 依赖关系:
 *   - 被KeyManager使用
 *   - 被安全网络组件使用
 */
package com.lx.gameserver.frame.security.keystore;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 证书管理器
 * <p>
 * 提供SSL证书的管理、验证、更新和吊销检查功能，
 * 支持自签名证书生成和证书链验证，确保通信安全。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class CertificateManager {

    /**
     * 证书库文件路径
     */
    @Value("${game.security.certs.path:keystore/game-certs.jks}")
    private String certificatePath;
    
    /**
     * 证书库密码
     */
    @Value("${game.security.certs.password:changeit}")
    private String certificatePassword;
    
    /**
     * 证书缓存
     * Key: 证书别名
     * Value: 证书对象
     */
    private final Map<String, X509Certificate> certificateCache = new ConcurrentHashMap<>();
    
    /**
     * 到期证书集合
     * Key: 证书别名
     * Value: 到期信息
     */
    private final Map<String, ExpirationInfo> expirationWarnings = new ConcurrentHashMap<>();
    
    /**
     * 即将到期天数阈值
     */
    private static final int EXPIRATION_WARNING_DAYS = 30;
    
    /**
     * 初始化证书管理器
     */
    @PostConstruct
    public void init() {
        try {
            // 确保证书目录存在
            ensureCertificateDirectoryExists();
            
            // 确保证书库文件存在
            if (!certificateStoreExists()) {
                createCertificateStore();
            }
            
            // 加载并验证证书
            loadAndValidateCertificates();
            
            log.info("证书管理器初始化完成");
        } catch (Exception e) {
            log.error("初始化证书管理器失败", e);
        }
    }
    
    /**
     * 确保证书目录存在
     */
    private void ensureCertificateDirectoryExists() {
        File file = new File(certificatePath);
        File directory = file.getParentFile();
        
        if (directory != null && !directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                log.info("已创建证书目录: {}", directory.getAbsolutePath());
            } else {
                log.warn("无法创建证书目录: {}", directory.getAbsolutePath());
            }
        }
    }
    
    /**
     * 检查证书库文件是否存在
     *
     * @return 如果存在返回true，否则返回false
     */
    private boolean certificateStoreExists() {
        File file = new File(certificatePath);
        return file.exists() && file.isFile();
    }
    
    /**
     * 创建证书库文件
     */
    private void createCertificateStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, certificatePassword.toCharArray());
        
        try (FileOutputStream fos = new FileOutputStream(certificatePath)) {
            keyStore.store(fos, certificatePassword.toCharArray());
        }
        
        log.info("已创建新的证书库文件: {}", certificatePath);
    }
    
    /**
     * 加载证书库
     *
     * @return 证书库
     */
    private KeyStore loadCertificateStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        
        try (FileInputStream fis = new FileInputStream(certificatePath)) {
            keyStore.load(fis, certificatePassword.toCharArray());
        }
        
        return keyStore;
    }
    
    /**
     * 加载并验证证书
     */
    private void loadAndValidateCertificates() throws Exception {
        KeyStore keyStore = loadCertificateStore();
        
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            
            if (keyStore.isCertificateEntry(alias)) {
                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    
                    // 缓存证书
                    certificateCache.put(alias, x509Cert);
                    
                    // 检查有效期
                    checkCertificateExpiration(alias, x509Cert);
                }
            }
        }
        
        log.info("已加载 {} 个证书", certificateCache.size());
    }
    
    /**
     * 检查证书有效期
     *
     * @param alias 证书别名
     * @param cert X.509证书
     */
    private void checkCertificateExpiration(String alias, X509Certificate cert) {
        try {
            cert.checkValidity();
            
            // 计算剩余有效期
            Date notAfter = cert.getNotAfter();
            Instant expirationDate = notAfter.toInstant();
            Instant now = Instant.now();
            
            long daysRemaining = ChronoUnit.DAYS.between(now, expirationDate);
            
            if (daysRemaining <= EXPIRATION_WARNING_DAYS) {
                // 即将到期
                ExpirationInfo info = new ExpirationInfo(alias, notAfter, daysRemaining);
                expirationWarnings.put(alias, info);
                
                log.warn("证书即将到期: 别名={}, 剩余天数={}, 到期日期={}",
                        alias, daysRemaining, notAfter);
            }
        } catch (CertificateExpiredException e) {
            // 已过期
            log.error("证书已过期: 别名={}", alias);
            ExpirationInfo info = new ExpirationInfo(alias, cert.getNotAfter(), 0);
            expirationWarnings.put(alias, info);
        } catch (CertificateNotYetValidException e) {
            // 尚未生效
            log.warn("证书尚未生效: 别名={}", alias);
        }
    }
    
    /**
     * 定时检查证书有效期
     */
    @Scheduled(fixedRate = 24, timeUnit = TimeUnit.HOURS)
    public void scheduledCertificateCheck() {
        try {
            log.info("开始定时证书有效期检查");
            
            // 清除旧的警告
            expirationWarnings.clear();
            
            // 重新加载并检查证书
            loadAndValidateCertificates();
            
            // 报告到期证书
            if (!expirationWarnings.isEmpty()) {
                log.warn("发现 {} 个即将到期或已过期的证书", expirationWarnings.size());
                
                // TODO: 实现告警机制，如发送邮件通知
            }
        } catch (Exception e) {
            log.error("定时证书检查失败", e);
        }
    }
    
    /**
     * 生成自签名证书
     *
     * @param alias 证书别名
     * @param cn 通用名称
     * @param validDays 有效期（天）
     * @param keySize 密钥大小（位）
     * @return X.509证书
     */
    public X509Certificate generateSelfSignedCertificate(String alias, String cn, int validDays, int keySize)
            throws Exception {
        
        // 使用外部命令生成自签名证书（keytool）
        String tempKeystorePath = "keystore/temp_" + System.currentTimeMillis() + ".jks";
        String tempKeystorePassword = "temppass";
        
        File tempFile = new File(tempKeystorePath);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        
        // 确保父目录存在
        if (tempFile.getParentFile() != null) {
            tempFile.getParentFile().mkdirs();
        }
        
        // 构建keytool命令
        String[] command = {
                "keytool",
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", String.valueOf(keySize),
                "-sigalg", "SHA256withRSA",
                "-dname", "CN=" + cn + ", OU=Game, O=GameServer, L=City, ST=State, C=CN",
                "-validity", String.valueOf(validDays),
                "-keystore", tempKeystorePath,
                "-storepass", tempKeystorePassword,
                "-keypass", tempKeystorePassword
        };
        
        // 执行命令
        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                StringBuilder error = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
                
                throw new Exception("生成证书失败: " + error.toString());
            }
        }
        
        // 从临时证书库加载证书
        KeyStore tempKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(tempKeystorePath)) {
            tempKeyStore.load(fis, tempKeystorePassword.toCharArray());
        }
        
        // 获取证书
        Certificate cert = tempKeyStore.getCertificate(alias);
        if (!(cert instanceof X509Certificate)) {
            throw new Exception("不是X.509证书");
        }
        
        X509Certificate x509Cert = (X509Certificate) cert;
        
        // 获取私钥
        Key key = tempKeyStore.getKey(alias, tempKeystorePassword.toCharArray());
        if (!(key instanceof PrivateKey)) {
            throw new Exception("无法获取私钥");
        }
        
        // 保存到正式证书库
        importCertificateWithPrivateKey(alias, x509Cert, (PrivateKey) key);
        
        // 删除临时文件
        new File(tempKeystorePath).delete();
        
        log.info("已生成自签名证书: alias={}, CN={}, 有效期={}天", alias, cn, validDays);
        
        return x509Cert;
    }
    
    /**
     * 导入带私钥的证书
     *
     * @param alias 证书别名
     * @param cert X.509证书
     * @param privateKey 私钥
     */
    public void importCertificateWithPrivateKey(String alias, X509Certificate cert, PrivateKey privateKey)
            throws Exception {
        
        KeyStore keyStore = loadCertificateStore();
        
        // 存储证书和私钥
        keyStore.setKeyEntry(
                alias,
                privateKey,
                certificatePassword.toCharArray(),
                new Certificate[] { cert }
        );
        
        try (FileOutputStream fos = new FileOutputStream(certificatePath)) {
            keyStore.store(fos, certificatePassword.toCharArray());
        }
        
        // 更新缓存
        certificateCache.put(alias, cert);
        
        // 检查有效期
        checkCertificateExpiration(alias, cert);
        
        log.info("已导入证书和私钥: {}", alias);
    }
    
    /**
     * 导入证书
     *
     * @param alias 证书别名
     * @param certData 证书数据
     * @return X.509证书
     */
    public X509Certificate importCertificate(String alias, byte[] certData) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certData));
        
        KeyStore keyStore = loadCertificateStore();
        keyStore.setCertificateEntry(alias, cert);
        
        try (FileOutputStream fos = new FileOutputStream(certificatePath)) {
            keyStore.store(fos, certificatePassword.toCharArray());
        }
        
        // 更新缓存
        certificateCache.put(alias, cert);
        
        // 检查有效期
        checkCertificateExpiration(alias, cert);
        
        log.info("已导入证书: {}", alias);
        
        return cert;
    }
    
    /**
     * 导出证书
     *
     * @param alias 证书别名
     * @return 证书数据，如果不存在则返回null
     */
    public byte[] exportCertificate(String alias) throws Exception {
        X509Certificate cert = getCertificate(alias);
        if (cert == null) {
            return null;
        }
        
        return cert.getEncoded();
    }
    
    /**
     * 获取证书
     *
     * @param alias 证书别名
     * @return X.509证书，如果不存在则返回null
     */
    public X509Certificate getCertificate(String alias) throws Exception {
        // 检查缓存
        X509Certificate cachedCert = certificateCache.get(alias);
        if (cachedCert != null) {
            return cachedCert;
        }
        
        KeyStore keyStore = loadCertificateStore();
        if (keyStore.containsAlias(alias)) {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) cert;
                
                // 更新缓存
                certificateCache.put(alias, x509Cert);
                
                return x509Cert;
            }
        }
        
        return null;
    }
    
    /**
     * 验证证书链
     *
     * @param cert 待验证的证书
     * @param trustedCerts 信任的证书集合
     * @return 如果验证通过返回true，否则返回false
     */
    public boolean verifyCertificateChain(X509Certificate cert, Set<X509Certificate> trustedCerts) {
        try {
            // 创建证书信任集
            Set<TrustAnchor> trustAnchors = new HashSet<>();
            for (X509Certificate trustedCert : trustedCerts) {
                trustAnchors.add(new TrustAnchor(trustedCert, null));
            }
            
            // 创建证书路径验证参数
            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false); // 禁用吊销检查，简化实现
            
            // 创建证书路径验证器
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            
            // 创建证书路径
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certList = new ArrayList<>();
            certList.add(cert);
            CertPath certPath = cf.generateCertPath(certList);
            
            // 验证证书路径
            validator.validate(certPath, params);
            
            return true;
        } catch (Exception e) {
            log.error("证书链验证失败", e);
            return false;
        }
    }
    
    /**
     * 检查证书是否已吊销（简化实现）
     *
     * @param cert X.509证书
     * @param crlUrl CRL URL
     * @return 如果已吊销返回true，否则返回false
     */
    public boolean isCertificateRevoked(X509Certificate cert, String crlUrl) {
        try {
            // 下载CRL
            URL url = new URL(crlUrl);
            try (InputStream crlStream = url.openStream()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509CRL crl = (X509CRL) cf.generateCRL(crlStream);
                
                // 检查证书是否在CRL中
                return crl.isRevoked(cert);
            }
        } catch (Exception e) {
            log.error("检查证书吊销状态失败", e);
            return false; // 出错时假设未吊销
        }
    }
    
    /**
     * 删除证书
     *
     * @param alias 证书别名
     * @return 是否删除成功
     */
    public boolean deleteCertificate(String alias) throws Exception {
        KeyStore keyStore = loadCertificateStore();
        
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias);
            
            try (FileOutputStream fos = new FileOutputStream(certificatePath)) {
                keyStore.store(fos, certificatePassword.toCharArray());
            }
            
            // 从缓存中移除
            certificateCache.remove(alias);
            expirationWarnings.remove(alias);
            
            log.info("已删除证书: {}", alias);
            return true;
        }
        
        return false;
    }
    
    /**
     * 列出所有证书
     *
     * @return 证书信息映射，Key为别名，Value为证书信息
     */
    public Map<String, CertificateInfo> listCertificates() throws Exception {
        KeyStore keyStore = loadCertificateStore();
        Map<String, CertificateInfo> result = new HashMap<>();
        
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isCertificateEntry(alias)) {
                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    
                    String subject = x509Cert.getSubjectX500Principal().getName();
                    String issuer = x509Cert.getIssuerX500Principal().getName();
                    Date notBefore = x509Cert.getNotBefore();
                    Date notAfter = x509Cert.getNotAfter();
                    
                    CertificateInfo info = new CertificateInfo(
                            alias, subject, issuer, notBefore, notAfter);
                    result.put(alias, info);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取即将到期的证书
     *
     * @return 到期信息映射，Key为别名，Value为到期信息
     */
    public Map<String, ExpirationInfo> getExpiringCertificates() {
        return new HashMap<>(expirationWarnings);
    }
    
    /**
     * 清除证书缓存
     */
    public void clearCertificateCache() {
        certificateCache.clear();
        log.info("已清除证书缓存");
    }
    
    /**
     * 证书信息
     */
    @Data
    public static class CertificateInfo {
        /**
         * 别名
         */
        private final String alias;
        
        /**
         * 主题
         */
        private final String subject;
        
        /**
         * 颁发者
         */
        private final String issuer;
        
        /**
         * 生效日期
         */
        private final Date notBefore;
        
        /**
         * 过期日期
         */
        private final Date notAfter;
    }
    
    /**
     * 到期信息
     */
    @Data
    public static class ExpirationInfo {
        /**
         * 证书别名
         */
        private final String alias;
        
        /**
         * 过期日期
         */
        private final Date expirationDate;
        
        /**
         * 剩余天数
         */
        private final long daysRemaining;
    }
}