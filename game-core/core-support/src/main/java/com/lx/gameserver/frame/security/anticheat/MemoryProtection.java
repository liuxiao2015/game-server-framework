/*
 * 文件名: MemoryProtection.java
 * 用途: 内存保护机制
 * 实现内容:
 *   - 关键数据加密
 *   - 内存完整性检查
 *   - 反调试检测
 *   - 进程保护
 *   - 代码混淆支持
 * 技术选型:
 *   - XOR加密
 *   - 哈希校验
 *   - 反调试技术
 * 依赖关系:
 *   - 被游戏核心模块使用
 *   - 使用GameCryptoService
 */
package com.lx.gameserver.frame.security.anticheat;

import com.lx.gameserver.frame.security.crypto.GameCryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * 内存保护机制
 * <p>
 * 提供对内存中重要数据的保护功能，包括数据加密、完整性校验、
 * 反调试检测和进程监控，防止通过内存修改进行作弊。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class MemoryProtection {

    /**
     * 加密服务
     */
    private final GameCryptoService cryptoService;

    /**
     * 内存校验器映射
     * Key: 对象标识
     * Value: 校验器
     */
    private final Map<String, MemoryValidator> validators = new ConcurrentHashMap<>();

    /**
     * 加密键值对管理
     * Key: 数据键名
     * Value: 加密数据
     */
    private final Map<String, EncryptedValue> encryptedValues = new ConcurrentHashMap<>();

    /**
     * 内存校验间隔（毫秒）
     */
    private static final long VALIDATION_INTERVAL = 5000;

    /**
     * 随机数生成器
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 全局加密密钥（运行时生成）
     */
    private final byte[] globalKey;

    /**
     * 构造函数
     *
     * @param cryptoService 加密服务
     */
    @Autowired
    public MemoryProtection(@Nullable GameCryptoService cryptoService) {
        this.cryptoService = cryptoService;

        // 生成全局加密密钥
        globalKey = new byte[32]; // 256位密钥
        random.nextBytes(globalKey);

        // 启动校验任务
        startValidationTask();

        log.info("内存保护机制初始化完成");
    }

    /**
     * 启动校验任务
     */
    private void startValidationTask() {
        Thread validationThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 检查调试器
                    if (isDebugged()) {
                        log.warn("检测到调试工具！");
                        triggerDefense("debug_detected");
                    }

                    // 执行内存校验
                    validateAll();

                    Thread.sleep(VALIDATION_INTERVAL + (random.nextInt(1000) - 500));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("内存保护校验任务出错", e);
                }
            }
        }, "MemoryProtection-Validator");

        validationThread.setDaemon(true);
        validationThread.start();
    }

    /**
     * 注册需要保护的对象
     *
     * @param id 对象标识符
     * @param object 需保护的对象
     * @return 是否注册成功
     */
    public boolean registerProtectedObject(String id, Object object) {
        if (id == null || object == null) {
            return false;
        }

        try {
            byte[] initialHash = calculateObjectHash(object);
            MemoryValidator validator = new MemoryValidator(id, object, initialHash);
            validators.put(id, validator);
            log.debug("已注册受保护对象: {}", id);
            return true;
        } catch (Exception e) {
            log.error("注册受保护对象失败: {}", id, e);
            return false;
        }
    }

    /**
     * 注销受保护的对象
     *
     * @param id 对象标识符
     */
    public void unregisterProtectedObject(String id) {
        validators.remove(id);
        log.debug("已注销受保护对象: {}", id);
    }

    /**
     * 存储加密的整数值
     *
     * @param key 键名
     * @param value 整数值
     */
    public void storeEncryptedInt(String key, int value) {
        try {
            // 生成随机XOR密钥
            byte[] keyBytes = new byte[4];
            random.nextBytes(keyBytes);

            // 应用XOR加密
            int encryptedValue = value ^ ByteBuffer.wrap(keyBytes).getInt();

            // 计算数据校验和
            CRC32 crc32 = new CRC32();
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putInt(value);
            buffer.putInt(encryptedValue);
            crc32.update(buffer.array());
            long checksum = crc32.getValue();

            // 存储加密值
            EncryptedValue encrypted = new EncryptedValue(
                    encryptedValue,
                    keyBytes,
                    checksum,
                    value
            );
            encryptedValues.put(key, encrypted);

            log.trace("已存储加密整数值: {}", key);
        } catch (Exception e) {
            log.error("存储加密整数失败: {}", key, e);
        }
    }

    /**
     * 获取解密的整数值
     *
     * @param key 键名
     * @param defaultValue 默认值（如果键不存在）
     * @return 解密后的整数值，如果检测到篡改则返回默认值
     */
    public int getDecryptedInt(String key, int defaultValue) {
        try {
            EncryptedValue encrypted = encryptedValues.get(key);
            if (encrypted == null) {
                return defaultValue;
            }

            // 使用XOR密钥解密
            int decrypted = encrypted.value ^ ByteBuffer.wrap(encrypted.keyBytes).getInt();

            // 验证校验和
            CRC32 crc32 = new CRC32();
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putInt(decrypted);
            buffer.putInt(encrypted.value);
            crc32.update(buffer.array());
            long checksum = crc32.getValue();

            // 检查校验和和原始值是否匹配
            if (checksum != encrypted.checksum || decrypted != encrypted.originalValue) {
                log.warn("检测到加密整数值被篡改: {}", key);
                triggerDefense("tampered_value");
                return defaultValue;
            }

            return decrypted;
        } catch (Exception e) {
            log.error("获取解密整数失败: {}", key, e);
            return defaultValue;
        }
    }

    /**
     * 存储加密的对象
     *
     * @param key 键名
     * @param value 对象值
     */
    public void storeEncryptedObject(String key, Object value) {
        if (cryptoService == null) {
            log.warn("加密服务不可用，无法加密存储对象");
            return;
        }

        try {
            // 序列化和加密对象
            byte[] serialized = serializeObject(value);
            byte[] encryptedData = encryptData(serialized);

            // 计算原始对象的哈希
            byte[] originalHash = calculateObjectHash(value);

            // 存储加密对象
            EncryptedObject encryptedObject = new EncryptedObject(encryptedData, originalHash);
            encryptedObjects.put(key, encryptedObject);

            log.debug("已存储加密对象: {}", key);
        } catch (Exception e) {
            log.error("存储加密对象失败: {}", key, e);
        }
    }

    /**
     * 获取解密的对象
     *
     * @param key 键名
     * @param <T> 对象类型
     * @return 解密后的对象，如果检测到篡改或发生异常则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getDecryptedObject(String key) {
        if (cryptoService == null) {
            log.warn("加密服务不可用，无法解密对象");
            return null;
        }

        try {
            EncryptedObject encryptedObject = encryptedObjects.get(key);
            if (encryptedObject == null) {
                return null;
            }

            // 解密对象
            byte[] decryptedData = decryptData(encryptedObject.encryptedData);
            T object = (T) deserializeObject(decryptedData);

            // 验证对象哈希
            byte[] currentHash = calculateObjectHash(object);
            if (!Arrays.equals(currentHash, encryptedObject.originalHash)) {
                log.warn("检测到加密对象被篡改: {}", key);
                triggerDefense("tampered_object");
                return null;
            }

            return object;
        } catch (Exception e) {
            log.error("获取解密对象失败: {}", key, e);
            return null;
        }
    }

    /**
     * 加密数据
     *
     * @param data 原始数据
     * @return 加密后的数据
     */
    private byte[] encryptData(byte[] data) throws Exception {
        // 使用XOR加密（简单示例，实际应使用更强的加密方式）
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ globalKey[i % globalKey.length]);
        }
        return result;
    }

    /**
     * 解密数据
     *
     * @param encryptedData 加密数据
     * @return 解密后的数据
     */
    private byte[] decryptData(byte[] encryptedData) throws Exception {
        // XOR解密（与加密相同）
        byte[] result = new byte[encryptedData.length];
        for (int i = 0; i < encryptedData.length; i++) {
            result[i] = (byte) (encryptedData[i] ^ globalKey[i % globalKey.length]);
        }
        return result;
    }

    /**
     * 序列化对象（简化实现）
     */
    private byte[] serializeObject(Object object) throws IOException {
        // 实际项目中应使用适当的序列化框架
        return object.toString().getBytes();
    }

    /**
     * 反序列化对象（简化实现）
     */
    private Object deserializeObject(byte[] data) throws IOException, ClassNotFoundException {
        // 实际项目中应使用适当的序列化框架
        return new String(data);
    }

    /**
     * 加密对象存储
     * Key: 对象键名
     * Value: 加密对象
     */
    private final Map<String, EncryptedObject> encryptedObjects = new ConcurrentHashMap<>();

    /**
     * 校验所有注册的对象
     */
    private void validateAll() {
        for (Map.Entry<String, MemoryValidator> entry : validators.entrySet()) {
            try {
                MemoryValidator validator = entry.getValue();
                if (!validator.validate()) {
                    log.warn("检测到内存被修改: {}", entry.getKey());
                    triggerDefense("memory_modified");
                }
            } catch (Exception e) {
                log.error("内存校验出错", e);
            }
        }
    }

    /**
     * 计算对象哈希
     *
     * @param object 对象
     * @return 哈希值
     */
    private byte[] calculateObjectHash(Object object) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // 对象为null或基本类型时的处理
            if (object == null) {
                return digest.digest("null".getBytes());
            }
            
            // 简化实现，实际项目应使用更可靠的方法计算对象哈希
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            
            if (object instanceof Collection) {
                // 集合类型
                Collection<?> collection = (Collection<?>) object;
                for (Object item : collection) {
                    if (item != null) {
                        output.write(item.toString().getBytes());
                    }
                }
            } else if (object instanceof Map) {
                // Map类型
                Map<?, ?> map = (Map<?, ?>) object;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        output.write(entry.getKey().toString().getBytes());
                    }
                    if (entry.getValue() != null) {
                        output.write(entry.getValue().toString().getBytes());
                    }
                }
            } else {
                // 其他类型
                output.write(object.toString().getBytes());
            }
            
            return digest.digest(output.toByteArray());
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("计算对象哈希失败", e);
            return new byte[0];
        }
    }

    /**
     * 检测是否处于调试状态
     *
     * @return 是否被调试
     */
    private boolean isDebugged() {
        // 尝试检测Java调试器
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
            return server.isRegistered(objectName);
        } catch (JMException e) {
            // 忽略异常
            return false;
        }
    }

    /**
     * 触发防御机制
     *
     * @param reason 触发原因
     */
    private void triggerDefense(String reason) {
        log.warn("触发内存保护防御机制: {}", reason);
        
        // 实际项目中可以实现更多的防御措施，例如：
        // 1. 通知服务器
        // 2. 记录详细日志
        // 3. 触发程序终止
        // 4. 清除敏感数据
    }

    /**
     * 内存校验器
     */
    private static class MemoryValidator {
        /**
         * 对象标识
         */
        private final String id;
        
        /**
         * 被保护的对象
         */
        private final Object object;
        
        /**
         * 初始哈希值
         */
        private final byte[] initialHash;
        
        /**
         * 构造函数
         *
         * @param id 对象标识
         * @param object 被保护的对象
         * @param initialHash 初始哈希值
         */
        public MemoryValidator(String id, Object object, byte[] initialHash) {
            this.id = id;
            this.object = object;
            this.initialHash = initialHash;
        }
        
        /**
         * 验证对象是否被修改
         *
         * @return 如果没有修改返回true，否则返回false
         */
        public boolean validate() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] currentHash = digest.digest(object.toString().getBytes());
                return Arrays.equals(initialHash, currentHash);
            } catch (NoSuchAlgorithmException e) {
                return false;
            }
        }
    }

    /**
     * 加密整数值
     */
    private static class EncryptedValue {
        /**
         * 加密后的值
         */
        private final int value;
        
        /**
         * XOR密钥
         */
        private final byte[] keyBytes;
        
        /**
         * 校验和
         */
        private final long checksum;
        
        /**
         * 原始值（为验证用）
         */
        private final int originalValue;
        
        /**
         * 构造函数
         */
        public EncryptedValue(int value, byte[] keyBytes, long checksum, int originalValue) {
            this.value = value;
            this.keyBytes = keyBytes;
            this.checksum = checksum;
            this.originalValue = originalValue;
        }
    }

    /**
     * 加密对象
     */
    private static class EncryptedObject {
        /**
         * 加密后的数据
         */
        private final byte[] encryptedData;
        
        /**
         * 原始对象哈希
         */
        private final byte[] originalHash;
        
        /**
         * 构造函数
         */
        public EncryptedObject(byte[] encryptedData, byte[] originalHash) {
            this.encryptedData = encryptedData;
            this.originalHash = originalHash;
        }
    }
}