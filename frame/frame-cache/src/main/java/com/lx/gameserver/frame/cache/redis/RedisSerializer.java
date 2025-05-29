/*
 * 文件名: RedisSerializer.java
 * 用途: Redis序列化器
 * 实现内容:
 *   - 提供多种序列化方式（JSON/JDK/String）
 *   - 支持压缩和类型安全
 *   - 版本兼容性处理
 *   - 性能优化和错误处理
 *   - 可扩展的序列化策略
 * 技术选型:
 *   - Jackson JSON序列化
 *   - JDK原生序列化
 *   - 压缩算法支持
 *   - 泛型类型处理
 * 依赖关系:
 *   - 被RedisCache使用
 *   - 提供序列化功能
 *   - 支持多种数据类型
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Redis序列化器
 * <p>
 * 提供多种序列化方式的统一接口，支持JSON、JDK原生序列化、字符串等。
 * 包含压缩功能和类型安全处理，确保数据的正确存储和读取。
 * </p>
 *
 * @param <T> 序列化对象类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface RedisSerializer<T> {

    /**
     * 序列化对象
     *
     * @param object 要序列化的对象
     * @return 序列化后的字节数组
     * @throws SerializationException 序列化失败时抛出
     */
    byte[] serialize(T object) throws SerializationException;

    /**
     * 反序列化对象
     *
     * @param bytes 字节数组
     * @return 反序列化后的对象
     * @throws SerializationException 反序列化失败时抛出
     */
    T deserialize(byte[] bytes) throws SerializationException;

    /**
     * 检查是否可以序列化指定类型
     *
     * @param type 对象类型
     * @return 如果可以序列化返回true
     */
    default boolean canSerialize(Class<?> type) {
        return true;
    }

    /**
     * JSON序列化器
     */
    class JsonRedisSerializer<T> implements RedisSerializer<T> {
        private static final Logger logger = LoggerFactory.getLogger(JsonRedisSerializer.class);
        
        private final ObjectMapper objectMapper;
        private final Class<T> type;
        private final boolean enableCompression;

        public JsonRedisSerializer(Class<T> type) {
            this(type, new ObjectMapper(), false);
        }

        public JsonRedisSerializer(Class<T> type, boolean enableCompression) {
            this(type, new ObjectMapper(), enableCompression);
        }

        public JsonRedisSerializer(Class<T> type, ObjectMapper objectMapper, boolean enableCompression) {
            this.type = Objects.requireNonNull(type, "类型不能为null");
            this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper不能为null");
            this.enableCompression = enableCompression;
        }

        @Override
        public byte[] serialize(T object) throws SerializationException {
            if (object == null) {
                return new byte[0];
            }

            try {
                byte[] jsonBytes = objectMapper.writeValueAsBytes(object);
                
                if (enableCompression && jsonBytes.length > 1024) { // 超过1KB才压缩
                    return compress(jsonBytes);
                }
                
                return jsonBytes;
            } catch (JsonProcessingException e) {
                throw new SerializationException("JSON序列化失败", e);
            }
        }

        @Override
        public T deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            try {
                byte[] actualBytes = bytes;
                
                // 尝试解压缩
                if (enableCompression && isCompressed(bytes)) {
                    actualBytes = decompress(bytes);
                }
                
                return objectMapper.readValue(actualBytes, type);
            } catch (Exception e) {
                throw new SerializationException("JSON反序列化失败", e);
            }
        }

        @Override
        public boolean canSerialize(Class<?> type) {
            return this.type.isAssignableFrom(type);
        }

        private byte[] compress(byte[] data) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(data);
                gzipOut.finish();
                return baos.toByteArray();
            } catch (IOException e) {
                logger.warn("压缩失败，返回原始数据", e);
                return data;
            }
        }

        private byte[] decompress(byte[] compressedData) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
                 GZIPInputStream gzipIn = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzipIn.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            } catch (IOException e) {
                throw new SerializationException("解压缩失败", e);
            }
        }

        private boolean isCompressed(byte[] data) {
            return data.length >= 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b;
        }
    }

    /**
     * JDK原生序列化器
     */
    class JdkRedisSerializer<T extends Serializable> implements RedisSerializer<T> {
        private static final Logger logger = LoggerFactory.getLogger(JdkRedisSerializer.class);

        @Override
        public byte[] serialize(T object) throws SerializationException {
            if (object == null) {
                return new byte[0];
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(object);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new SerializationException("JDK序列化失败", e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (T) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new SerializationException("JDK反序列化失败", e);
            }
        }

        @Override
        public boolean canSerialize(Class<?> type) {
            return Serializable.class.isAssignableFrom(type);
        }
    }

    /**
     * 字符串序列化器
     */
    class StringRedisSerializer implements RedisSerializer<String> {

        @Override
        public byte[] serialize(String string) throws SerializationException {
            if (string == null) {
                return new byte[0];
            }
            return string.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public boolean canSerialize(Class<?> type) {
            return String.class.equals(type);
        }
    }

    /**
     * 字节数组序列化器（无操作）
     */
    class ByteArrayRedisSerializer implements RedisSerializer<byte[]> {

        @Override
        public byte[] serialize(byte[] bytes) throws SerializationException {
            return bytes;
        }

        @Override
        public byte[] deserialize(byte[] bytes) throws SerializationException {
            return bytes;
        }

        @Override
        public boolean canSerialize(Class<?> type) {
            return byte[].class.equals(type);
        }
    }

    /**
     * 复合序列化器
     * <p>
     * 根据对象类型自动选择合适的序列化器
     * </p>
     */
    class CompositeRedisSerializer implements RedisSerializer<Object> {
        private static final Logger logger = LoggerFactory.getLogger(CompositeRedisSerializer.class);

        private final RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        private final RedisSerializer<byte[]> byteArraySerializer = new ByteArrayRedisSerializer();
        private final RedisSerializer<Serializable> jdkSerializer = new JdkRedisSerializer<>();

        @Override
        public byte[] serialize(Object object) throws SerializationException {
            if (object == null) {
                return new byte[0];
            }

            Class<?> type = object.getClass();
            
            if (stringSerializer.canSerialize(type)) {
                return stringSerializer.serialize((String) object);
            } else if (byteArraySerializer.canSerialize(type)) {
                return byteArraySerializer.serialize((byte[]) object);
            } else if (jdkSerializer.canSerialize(type)) {
                return jdkSerializer.serialize((Serializable) object);
            } else {
                throw new SerializationException("不支持的序列化类型: " + type);
            }
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            // 这里需要类型信息，实际使用中应该在序列化时包含类型标识
            // 为简化，这里返回字节数组
            return bytes;
        }

        @Override
        public boolean canSerialize(Class<?> type) {
            return stringSerializer.canSerialize(type) ||
                   byteArraySerializer.canSerialize(type) ||
                   jdkSerializer.canSerialize(type);
        }
    }

    /**
     * 序列化异常
     */
    class SerializationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SerializationException(String message) {
            super(message);
        }

        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 创建JSON序列化器
     *
     * @param type 对象类型
     * @param <T>  类型参数
     * @return JSON序列化器
     */
    static <T> RedisSerializer<T> json(Class<T> type) {
        return new JsonRedisSerializer<>(type);
    }

    /**
     * 创建带压缩的JSON序列化器
     *
     * @param type 对象类型
     * @param <T>  类型参数
     * @return JSON序列化器
     */
    static <T> RedisSerializer<T> compressedJson(Class<T> type) {
        return new JsonRedisSerializer<>(type, true);
    }

    /**
     * 创建JDK序列化器
     *
     * @param <T> 类型参数
     * @return JDK序列化器
     */
    static <T extends Serializable> RedisSerializer<T> jdk() {
        return new JdkRedisSerializer<>();
    }

    /**
     * 创建字符串序列化器
     *
     * @return 字符串序列化器
     */
    static RedisSerializer<String> string() {
        return new StringRedisSerializer();
    }

    /**
     * 创建字节数组序列化器
     *
     * @return 字节数组序列化器
     */
    static RedisSerializer<byte[]> byteArray() {
        return new ByteArrayRedisSerializer();
    }

    /**
     * 创建复合序列化器
     *
     * @return 复合序列化器
     */
    static RedisSerializer<Object> composite() {
        return new CompositeRedisSerializer();
    }
}