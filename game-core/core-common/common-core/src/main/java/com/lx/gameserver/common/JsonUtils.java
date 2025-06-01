/*
 * 文件名: JsonUtils.java
 * 用途: JSON序列化和反序列化工具类
 * 实现内容:
 *   - 基于Jackson提供JSON转换功能
 *   - 支持对象与JSON字符串互转
 *   - 支持泛型和复杂对象类型
 *   - 提供异常安全的转换方法
 * 技术选型:
 *   - 使用Jackson作为JSON处理框架
 *   - 支持多种日期格式和时区
 *   - 配置容错和安全选项
 *   - 支持自定义序列化规则
 * 依赖关系:
 *   - 依赖Jackson JSON处理库
 *   - 被需要JSON处理的模块使用
 */
package com.lx.gameserver.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * JSON序列化和反序列化工具类
 * <p>
 * 基于Jackson提供高性能的JSON处理功能，支持对象与JSON字符串的互相转换。
 * 配置了合理的默认选项，包括日期格式、时区、空值处理等，同时提供
 * 异常安全的转换方法，避免因格式错误导致的程序崩溃。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public final class JsonUtils {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    /**
     * Jackson ObjectMapper实例
     */
    private static final ObjectMapper OBJECT_MAPPER;

    /**
     * 紧凑格式的ObjectMapper（不包含空值）
     */
    private static final ObjectMapper COMPACT_MAPPER;

    /**
     * 美化格式的ObjectMapper
     */
    private static final ObjectMapper PRETTY_MAPPER;

    /**
     * 默认日期格式
     */
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private JsonUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // 静态初始化ObjectMapper
    static {
        OBJECT_MAPPER = createObjectMapper();
        COMPACT_MAPPER = createCompactMapper();
        PRETTY_MAPPER = createPrettyMapper();
        logger.info("JsonUtils初始化完成");
    }

    /**
     * 创建标准ObjectMapper
     *
     * @return ObjectMapper实例
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册Java 8时间模块
        mapper.registerModule(new JavaTimeModule());
        
        // 配置日期格式
        mapper.setDateFormat(new SimpleDateFormat(DEFAULT_DATE_FORMAT));
        mapper.setTimeZone(TimeZone.getTimeZone(GameConstants.DEFAULT_TIMEZONE));
        
        // 配置序列化选项
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // 配置反序列化选项
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        // 配置JSON解析器
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        
        return mapper;
    }

    /**
     * 创建紧凑格式ObjectMapper
     *
     * @return ObjectMapper实例
     */
    private static ObjectMapper createCompactMapper() {
        ObjectMapper mapper = createObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /**
     * 创建美化格式ObjectMapper
     *
     * @return ObjectMapper实例
     */
    private static ObjectMapper createPrettyMapper() {
        ObjectMapper mapper = createObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    // ===== 对象转JSON方法 =====

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 要转换的对象
     * @return JSON字符串，转换失败返回null
     */
    public static String toJson(Object obj) {
        return toJson(obj, false);
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj    要转换的对象
     * @param pretty 是否美化格式
     * @return JSON字符串，转换失败返回null
     */
    public static String toJson(Object obj, boolean pretty) {
        if (obj == null) {
            return null;
        }
        
        try {
            ObjectMapper mapper = pretty ? PRETTY_MAPPER : OBJECT_MAPPER;
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("对象转JSON失败: {}", obj.getClass().getSimpleName(), e);
            return null;
        }
    }

    /**
     * 将对象转换为紧凑格式JSON字符串（不包含null值）
     *
     * @param obj 要转换的对象
     * @return JSON字符串，转换失败返回null
     */
    public static String toCompactJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            return COMPACT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("对象转紧凑JSON失败: {}", obj.getClass().getSimpleName(), e);
            return null;
        }
    }

    /**
     * 将对象转换为美化格式JSON字符串
     *
     * @param obj 要转换的对象
     * @return JSON字符串，转换失败返回null
     */
    public static String toPrettyJson(Object obj) {
        return toJson(obj, true);
    }

    /**
     * 将对象转换为JSON字节数组
     *
     * @param obj 要转换的对象
     * @return JSON字节数组，转换失败返回null
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            logger.error("对象转JSON字节数组失败: {}", obj.getClass().getSimpleName(), e);
            return null;
        }
    }

    // ===== JSON转对象方法 =====

    /**
     * 将JSON字符串转换为指定类型的对象
     *
     * @param json  JSON字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 转换后的对象，转换失败返回null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty() || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON转对象失败，目标类型: {}, JSON: {}", clazz.getSimpleName(), json, e);
            return null;
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象（使用TypeReference）
     *
     * @param json          JSON字符串
     * @param typeReference 类型引用
     * @param <T>           泛型类型
     * @return 转换后的对象，转换失败返回null
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.trim().isEmpty() || typeReference == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("JSON转对象失败，类型引用: {}, JSON: {}", typeReference.getType(), json, e);
            return null;
        }
    }

    /**
     * 将JSON字节数组转换为指定类型的对象
     *
     * @param jsonBytes JSON字节数组
     * @param clazz     目标类型
     * @param <T>       泛型类型
     * @return 转换后的对象，转换失败返回null
     */
    public static <T> T fromJson(byte[] jsonBytes, Class<T> clazz) {
        if (jsonBytes == null || jsonBytes.length == 0 || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(jsonBytes, clazz);
        } catch (IOException e) {
            logger.error("JSON字节数组转对象失败，目标类型: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    /**
     * 从输入流读取JSON并转换为对象
     *
     * @param inputStream 输入流
     * @param clazz       目标类型
     * @param <T>         泛型类型
     * @return 转换后的对象，转换失败返回null
     */
    public static <T> T fromJson(InputStream inputStream, Class<T> clazz) {
        if (inputStream == null || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (IOException e) {
            logger.error("从输入流读取JSON转对象失败，目标类型: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    // ===== 便捷转换方法 =====

    /**
     * 将JSON字符串转换为Map
     *
     * @param json JSON字符串
     * @return Map对象，转换失败返回null
     */
    public static Map<String, Object> toMap(String json) {
        return fromJson(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 将JSON字符串转换为List
     *
     * @param json JSON字符串
     * @param <T>  列表元素类型
     * @return List对象，转换失败返回null
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> toList(String json) {
        return fromJson(json, new TypeReference<List<T>>() {});
    }

    /**
     * 将JSON字符串转换为指定类型的List
     *
     * @param json      JSON字符串
     * @param itemClass 列表元素类型
     * @param <T>       列表元素类型
     * @return List对象，转换失败返回null
     */
    public static <T> List<T> toList(String json, Class<T> itemClass) {
        if (json == null || json.trim().isEmpty() || itemClass == null) {
            return null;
        }
        
        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametricType(List.class, itemClass);
            return OBJECT_MAPPER.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            logger.error("JSON转List失败，元素类型: {}, JSON: {}", itemClass.getSimpleName(), json, e);
            return null;
        }
    }

    // ===== JSON节点操作方法 =====

    /**
     * 解析JSON字符串为JsonNode
     *
     * @param json JSON字符串
     * @return JsonNode对象，解析失败返回null
     */
    public static JsonNode parseTree(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error("JSON解析为树结构失败: {}", json, e);
            return null;
        }
    }

    /**
     * 创建空的ObjectNode
     *
     * @return ObjectNode对象
     */
    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    /**
     * 创建空的ArrayNode
     *
     * @return ArrayNode对象
     */
    public static ArrayNode createArrayNode() {
        return OBJECT_MAPPER.createArrayNode();
    }

    // ===== 对象复制方法 =====

    /**
     * 深度复制对象（通过JSON转换）
     *
     * @param obj   源对象
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 复制后的对象，复制失败返回null
     */
    public static <T> T deepCopy(Object obj, Class<T> clazz) {
        if (obj == null || clazz == null) {
            return null;
        }
        
        try {
            String json = OBJECT_MAPPER.writeValueAsString(obj);
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("对象深度复制失败，源类型: {}, 目标类型: {}", 
                    obj.getClass().getSimpleName(), clazz.getSimpleName(), e);
            return null;
        }
    }

    // ===== 验证方法 =====

    /**
     * 验证字符串是否为有效的JSON格式
     *
     * @param json 待验证的JSON字符串
     * @return 如果是有效JSON返回true，否则返回false
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 验证字符串是否为有效的JSON对象格式
     *
     * @param json 待验证的JSON字符串
     * @return 如果是有效JSON对象返回true，否则返回false
     */
    public static boolean isValidJsonObject(String json) {
        JsonNode node = parseTree(json);
        return node != null && node.isObject();
    }

    /**
     * 验证字符串是否为有效的JSON数组格式
     *
     * @param json 待验证的JSON字符串
     * @return 如果是有效JSON数组返回true，否则返回false
     */
    public static boolean isValidJsonArray(String json) {
        JsonNode node = parseTree(json);
        return node != null && node.isArray();
    }

    // ===== 配置和工具方法 =====

    /**
     * 获取默认ObjectMapper实例
     *
     * @return ObjectMapper实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * 获取紧凑格式ObjectMapper实例
     *
     * @return ObjectMapper实例
     */
    public static ObjectMapper getCompactMapper() {
        return COMPACT_MAPPER;
    }

    /**
     * 获取美化格式ObjectMapper实例
     *
     * @return ObjectMapper实例
     */
    public static ObjectMapper getPrettyMapper() {
        return PRETTY_MAPPER;
    }

    /**
     * 格式化JSON字符串（美化输出）
     *
     * @param json 原始JSON字符串
     * @return 格式化后的JSON字符串，格式化失败返回原字符串
     */
    public static String formatJson(String json) {
        JsonNode node = parseTree(json);
        if (node == null) {
            return json;
        }
        
        try {
            return PRETTY_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            logger.error("JSON格式化失败: {}", json, e);
            return json;
        }
    }

    /**
     * 压缩JSON字符串（移除空白字符）
     *
     * @param json 原始JSON字符串
     * @return 压缩后的JSON字符串，压缩失败返回原字符串
     */
    public static String compressJson(String json) {
        JsonNode node = parseTree(json);
        if (node == null) {
            return json;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            logger.error("JSON压缩失败: {}", json, e);
            return json;
        }
    }
}