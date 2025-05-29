/*
 * 文件名: CacheKeyGenerator.java
 * 用途: 缓存键生成器
 * 实现内容:
 *   - 默认键生成策略实现
 *   - 自定义键生成策略支持
 *   - 参数组合和哈希生成
 *   - 冲突避免和性能优化
 *   - 类型安全的键生成
 * 技术选型:
 *   - 反射API和参数处理
 *   - 哈希算法和字符串处理
 *   - 泛型和类型安全
 *   - 缓存友好的设计
 * 依赖关系:
 *   - 被Cache实现使用
 *   - 提供键生成功能
 *   - 支持注解驱动
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 缓存键生成器
 * <p>
 * 提供多种缓存键生成策略，包括默认策略、自定义策略等。
 * 确保生成的键具有唯一性、可读性和性能优化特性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheKeyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CacheKeyGenerator.class);

    /**
     * 默认分隔符
     */
    private static final String DEFAULT_SEPARATOR = ":";

    /**
     * 空参数占位符
     */
    private static final String EMPTY_PARAMS = "NO_PARAMS";

    /**
     * null值占位符
     */
    private static final String NULL_VALUE = "NULL";

    /**
     * 生成键的最大长度
     */
    private static final int MAX_KEY_LENGTH = 250;

    /**
     * 键生成策略接口
     */
    public interface KeyGenerator {
        /**
         * 生成缓存键
         *
         * @param target     目标对象
         * @param method     方法
         * @param parameters 参数
         * @return 缓存键
         */
        String generate(Object target, Method method, Object... parameters);
    }

    /**
     * 默认键生成器
     */
    public static class DefaultKeyGenerator implements KeyGenerator {

        @Override
        public String generate(Object target, Method method, Object... parameters) {
            if (parameters == null || parameters.length == 0) {
                return generateMethodKey(target, method, EMPTY_PARAMS);
            }

            // 构建参数字符串
            StringJoiner joiner = new StringJoiner(DEFAULT_SEPARATOR);
            for (Object param : parameters) {
                joiner.add(objectToString(param));
            }

            String paramString = joiner.toString();
            return generateMethodKey(target, method, paramString);
        }

        /**
         * 生成方法键
         */
        private String generateMethodKey(Object target, Method method, String paramString) {
            String className = target.getClass().getSimpleName();
            String methodName = method.getName();
            
            String key = className + DEFAULT_SEPARATOR + methodName + DEFAULT_SEPARATOR + paramString;
            
            // 如果键太长，使用哈希
            if (key.length() > MAX_KEY_LENGTH) {
                String hashSuffix = hashString(paramString);
                key = className + DEFAULT_SEPARATOR + methodName + DEFAULT_SEPARATOR + hashSuffix;
            }
            
            return key;
        }
    }

    /**
     * 简单键生成器（只使用参数）
     */
    public static class SimpleKeyGenerator implements KeyGenerator {

        @Override
        public String generate(Object target, Method method, Object... parameters) {
            if (parameters == null || parameters.length == 0) {
                return EMPTY_PARAMS;
            }

            if (parameters.length == 1) {
                return objectToString(parameters[0]);
            }

            StringJoiner joiner = new StringJoiner(DEFAULT_SEPARATOR);
            for (Object param : parameters) {
                joiner.add(objectToString(param));
            }

            String key = joiner.toString();
            
            // 如果键太长，使用哈希
            if (key.length() > MAX_KEY_LENGTH) {
                return hashString(key);
            }
            
            return key;
        }
    }

    /**
     * 哈希键生成器
     */
    public static class HashKeyGenerator implements KeyGenerator {

        @Override
        public String generate(Object target, Method method, Object... parameters) {
            String className = target.getClass().getName();
            String methodName = method.getName();
            String paramString = Arrays.toString(parameters);
            
            String combined = className + "." + methodName + "(" + paramString + ")";
            return hashString(combined);
        }
    }

    /**
     * 分层键生成器
     */
    public static class HierarchicalKeyGenerator implements KeyGenerator {
        private final String namespace;

        public HierarchicalKeyGenerator(String namespace) {
            this.namespace = Objects.requireNonNull(namespace, "命名空间不能为null");
        }

        @Override
        public String generate(Object target, Method method, Object... parameters) {
            String className = target.getClass().getSimpleName();
            String methodName = method.getName();
            
            StringJoiner keyBuilder = new StringJoiner(DEFAULT_SEPARATOR);
            keyBuilder.add(namespace);
            keyBuilder.add(className);
            keyBuilder.add(methodName);
            
            if (parameters != null && parameters.length > 0) {
                StringJoiner paramBuilder = new StringJoiner("_");
                for (Object param : parameters) {
                    paramBuilder.add(objectToString(param));
                }
                String paramString = paramBuilder.toString();
                
                if (paramString.length() > 50) {
                    paramString = hashString(paramString);
                }
                
                keyBuilder.add(paramString);
            }
            
            return keyBuilder.toString();
        }
    }

    /**
     * 自定义键生成器
     */
    public static class CustomKeyGenerator implements KeyGenerator {
        private final String pattern;
        private final String separator;

        public CustomKeyGenerator(String pattern) {
            this(pattern, DEFAULT_SEPARATOR);
        }

        public CustomKeyGenerator(String pattern, String separator) {
            this.pattern = Objects.requireNonNull(pattern, "模式不能为null");
            this.separator = Objects.requireNonNull(separator, "分隔符不能为null");
        }

        @Override
        public String generate(Object target, Method method, Object... parameters) {
            String key = pattern;
            
            // 替换占位符
            key = key.replace("{class}", target.getClass().getSimpleName());
            key = key.replace("{method}", method.getName());
            key = key.replace("{fullClass}", target.getClass().getName());
            
            if (parameters != null && parameters.length > 0) {
                for (int i = 0; i < parameters.length; i++) {
                    key = key.replace("{" + i + "}", objectToString(parameters[i]));
                }
                
                StringJoiner allParams = new StringJoiner(separator);
                for (Object param : parameters) {
                    allParams.add(objectToString(param));
                }
                key = key.replace("{params}", allParams.toString());
            } else {
                key = key.replace("{params}", EMPTY_PARAMS);
                // 替换所有的 {数字} 占位符
                key = key.replaceAll("\\{\\d+\\}", "");
            }
            
            return key;
        }
    }

    /**
     * 对象转字符串
     */
    private static String objectToString(Object obj) {
        if (obj == null) {
            return NULL_VALUE;
        }
        
        if (obj instanceof String) {
            return (String) obj;
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        if (obj instanceof byte[]) {
            return hashBytes((byte[]) obj);
        }
        
        if (obj.getClass().isArray()) {
            return Arrays.deepToString((Object[]) obj);
        }
        
        // 对于复杂对象，使用hashCode以避免键过长
        String str = obj.toString();
        if (str.length() > 50) {
            return obj.getClass().getSimpleName() + "@" + obj.hashCode();
        }
        
        return str;
    }

    /**
     * 字符串哈希
     */
    private static String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("MD5算法不可用，使用hashCode替代", e);
            return "hash_" + Math.abs(input.hashCode());
        }
    }

    /**
     * 字节数组哈希
     */
    private static String hashBytes(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("MD5算法不可用，使用hashCode替代", e);
            return "bytes_" + Arrays.hashCode(bytes);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 创建默认键生成器
     */
    public static KeyGenerator defaultGenerator() {
        return new DefaultKeyGenerator();
    }

    /**
     * 创建简单键生成器
     */
    public static KeyGenerator simpleGenerator() {
        return new SimpleKeyGenerator();
    }

    /**
     * 创建哈希键生成器
     */
    public static KeyGenerator hashGenerator() {
        return new HashKeyGenerator();
    }

    /**
     * 创建分层键生成器
     */
    public static KeyGenerator hierarchicalGenerator(String namespace) {
        return new HierarchicalKeyGenerator(namespace);
    }

    /**
     * 创建自定义键生成器
     */
    public static KeyGenerator customGenerator(String pattern) {
        return new CustomKeyGenerator(pattern);
    }

    /**
     * 创建自定义键生成器（带分隔符）
     */
    public static KeyGenerator customGenerator(String pattern, String separator) {
        return new CustomKeyGenerator(pattern, separator);
    }

    /**
     * 验证键的有效性
     */
    public static boolean isValidKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        if (key.length() > MAX_KEY_LENGTH) {
            return false;
        }
        
        // 检查是否包含不安全的字符
        return !key.matches(".*[\\s\\n\\r\\t].*");
    }

    /**
     * 清理键字符串
     */
    public static String cleanKey(String key) {
        if (key == null) {
            return "";
        }
        
        // 移除空白字符
        key = key.replaceAll("\\s+", "_");
        
        // 限制长度
        if (key.length() > MAX_KEY_LENGTH) {
            key = key.substring(0, MAX_KEY_LENGTH - 32) + "_" + hashString(key);
        }
        
        return key;
    }

    /**
     * 生成临时键
     */
    public static String generateTempKey() {
        return "temp_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }

    /**
     * 生成唯一键
     */
    public static String generateUniqueKey(String prefix) {
        return prefix + "_" + System.nanoTime() + "_" + hashString(Thread.currentThread().toString());
    }
}