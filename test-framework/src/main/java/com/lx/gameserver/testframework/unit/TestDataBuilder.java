/*
 * 文件名: TestDataBuilder.java
 * 用途: 测试数据构建器
 * 内容: 
 *   - 测试数据生成和构建
 *   - 实体数据生成
 *   - 随机数据生成
 *   - 边界数据生成
 *   - 数据模板支持
 *   - 批量数据生成
 * 技术选型: 
 *   - Java反射API
 *   - 随机数生成
 *   - Builder模式
 *   - 模板方法模式
 * 依赖关系: 
 *   - 被UnitTestRunner使用
 *   - 与TestContext集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.unit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 测试数据构建器
 * <p>
 * 提供测试数据的生成和构建功能，支持实体数据、随机数据、
 * 边界数据和批量数据生成。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@Component
public class TestDataBuilder {
    
    /**
     * 随机数生成器
     */
    private final Random random;
    
    /**
     * 数据模板缓存
     */
    private final Map<Class<?>, DataTemplate<?>> templateCache;
    
    /**
     * 构造函数
     */
    public TestDataBuilder() {
        this.random = ThreadLocalRandom.current();
        this.templateCache = new HashMap<>();
    }
    
    /**
     * 构建随机对象
     * 
     * @param clazz 对象类型
     * @param <T> 类型参数
     * @return 构建的对象
     */
    public <T> T buildRandom(Class<T> clazz) {
        return buildRandom(clazz, new BuilderOptions());
    }
    
    /**
     * 构建随机对象（带选项）
     * 
     * @param clazz 对象类型
     * @param options 构建选项
     * @param <T> 类型参数
     * @return 构建的对象
     */
    @SuppressWarnings("unchecked")
    public <T> T buildRandom(Class<T> clazz, BuilderOptions options) {
        try {
            log.debug("构建随机对象: {}", clazz.getSimpleName());
            
            // 检查是否有缓存的模板
            DataTemplate<T> template = (DataTemplate<T>) templateCache.get(clazz);
            if (template != null) {
                return template.build(this, options);
            }
            
            // 基本类型处理
            if (isPrimitiveOrWrapper(clazz)) {
                return (T) generatePrimitiveValue(clazz);
            }
            
            // 字符串类型
            if (clazz == String.class) {
                return (T) generateRandomString(options.getStringLength());
            }
            
            // 集合类型
            if (Collection.class.isAssignableFrom(clazz)) {
                return (T) generateRandomCollection(clazz, options);
            }
            
            // Map类型
            if (Map.class.isAssignableFrom(clazz)) {
                return (T) generateRandomMap(options);
            }
            
            // 数组类型
            if (clazz.isArray()) {
                return (T) generateRandomArray(clazz, options);
            }
            
            // 自定义对象
            return buildCustomObject(clazz, options);
            
        } catch (Exception e) {
            log.error("构建随机对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 构建边界数据对象
     * 
     * @param clazz 对象类型
     * @param <T> 类型参数
     * @return 边界数据对象
     */
    public <T> T buildBoundary(Class<T> clazz) {
        return buildBoundary(clazz, BoundaryType.MIN);
    }
    
    /**
     * 构建边界数据对象
     * 
     * @param clazz 对象类型
     * @param boundaryType 边界类型
     * @param <T> 类型参数
     * @return 边界数据对象
     */
    @SuppressWarnings("unchecked")
    public <T> T buildBoundary(Class<T> clazz, BoundaryType boundaryType) {
        try {
            log.debug("构建边界数据对象: {} ({})", clazz.getSimpleName(), boundaryType);
            
            // 基本类型处理
            if (isPrimitiveOrWrapper(clazz)) {
                return (T) generateBoundaryValue(clazz, boundaryType);
            }
            
            // 字符串类型
            if (clazz == String.class) {
                return (T) generateBoundaryString(boundaryType);
            }
            
            // 集合类型
            if (Collection.class.isAssignableFrom(clazz)) {
                return (T) generateBoundaryCollection(clazz, boundaryType);
            }
            
            // 其他类型使用随机构建
            return buildRandom(clazz);
            
        } catch (Exception e) {
            log.error("构建边界数据对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 批量构建对象
     * 
     * @param clazz 对象类型
     * @param count 数量
     * @param <T> 类型参数
     * @return 对象列表
     */
    public <T> List<T> buildList(Class<T> clazz, int count) {
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(buildRandom(clazz));
        }
        return result;
    }
    
    /**
     * 注册数据模板
     * 
     * @param clazz 类型
     * @param template 模板
     * @param <T> 类型参数
     */
    public <T> void registerTemplate(Class<T> clazz, DataTemplate<T> template) {
        templateCache.put(clazz, template);
        log.debug("注册数据模板: {}", clazz.getSimpleName());
    }
    
    /**
     * 生成随机字符串
     * 
     * @param length 长度
     * @return 随机字符串
     */
    public String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 生成随机数字
     * 
     * @param min 最小值
     * @param max 最大值
     * @return 随机数字
     */
    public int generateRandomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }
    
    /**
     * 生成随机长整数
     * 
     * @param min 最小值
     * @param max 最大值
     * @return 随机长整数
     */
    public long generateRandomLong(long min, long max) {
        return random.nextLong(max - min + 1) + min;
    }
    
    /**
     * 生成随机双精度数
     * 
     * @param min 最小值
     * @param max 最大值
     * @return 随机双精度数
     */
    public double generateRandomDouble(double min, double max) {
        return random.nextDouble() * (max - min) + min;
    }
    
    /**
     * 检查是否为基本类型或包装类型
     * 
     * @param clazz 类型
     * @return 是否为基本类型
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() || 
               clazz == Boolean.class || clazz == Byte.class || clazz == Character.class ||
               clazz == Short.class || clazz == Integer.class || clazz == Long.class ||
               clazz == Float.class || clazz == Double.class;
    }
    
    /**
     * 生成基本类型值
     * 
     * @param clazz 类型
     * @return 基本类型值
     */
    private Object generatePrimitiveValue(Class<?> clazz) {
        if (clazz == boolean.class || clazz == Boolean.class) {
            return random.nextBoolean();
        } else if (clazz == byte.class || clazz == Byte.class) {
            return (byte) random.nextInt(256);
        } else if (clazz == char.class || clazz == Character.class) {
            return (char) (random.nextInt(26) + 'a');
        } else if (clazz == short.class || clazz == Short.class) {
            return (short) random.nextInt(Short.MAX_VALUE + 1);
        } else if (clazz == int.class || clazz == Integer.class) {
            return random.nextInt();
        } else if (clazz == long.class || clazz == Long.class) {
            return random.nextLong();
        } else if (clazz == float.class || clazz == Float.class) {
            return random.nextFloat();
        } else if (clazz == double.class || clazz == Double.class) {
            return random.nextDouble();
        }
        return null;
    }
    
    /**
     * 生成边界值
     * 
     * @param clazz 类型
     * @param boundaryType 边界类型
     * @return 边界值
     */
    private Object generateBoundaryValue(Class<?> clazz, BoundaryType boundaryType) {
        if (clazz == int.class || clazz == Integer.class) {
            return switch (boundaryType) {
                case MIN -> Integer.MIN_VALUE;
                case MAX -> Integer.MAX_VALUE;
                case ZERO -> 0;
                case NEGATIVE -> -1;
                case POSITIVE -> 1;
            };
        } else if (clazz == long.class || clazz == Long.class) {
            return switch (boundaryType) {
                case MIN -> Long.MIN_VALUE;
                case MAX -> Long.MAX_VALUE;
                case ZERO -> 0L;
                case NEGATIVE -> -1L;
                case POSITIVE -> 1L;
            };
        }
        // 其他类型使用随机值
        return generatePrimitiveValue(clazz);
    }
    
    /**
     * 生成边界字符串
     * 
     * @param boundaryType 边界类型
     * @return 边界字符串
     */
    private String generateBoundaryString(BoundaryType boundaryType) {
        return switch (boundaryType) {
            case MIN -> "";
            case MAX -> generateRandomString(1000); // 长字符串
            case ZERO -> null;
            default -> generateRandomString(10);
        };
    }
    
    /**
     * 生成随机集合
     * 
     * @param clazz 集合类型
     * @param options 构建选项
     * @return 随机集合
     */
    @SuppressWarnings("unchecked")
    private Collection<Object> generateRandomCollection(Class<?> clazz, BuilderOptions options) {
        Collection<Object> collection;
        
        if (List.class.isAssignableFrom(clazz)) {
            collection = new ArrayList<>();
        } else if (Set.class.isAssignableFrom(clazz)) {
            collection = new HashSet<>();
        } else {
            collection = new ArrayList<>();
        }
        
        int size = random.nextInt(options.getCollectionMaxSize()) + 1;
        for (int i = 0; i < size; i++) {
            collection.add(generateRandomString(5));
        }
        
        return collection;
    }
    
    /**
     * 生成边界集合
     * 
     * @param clazz 集合类型
     * @param boundaryType 边界类型
     * @return 边界集合
     */
    @SuppressWarnings("unchecked")
    private Collection<Object> generateBoundaryCollection(Class<?> clazz, BoundaryType boundaryType) {
        Collection<Object> collection;
        
        if (List.class.isAssignableFrom(clazz)) {
            collection = new ArrayList<>();
        } else if (Set.class.isAssignableFrom(clazz)) {
            collection = new HashSet<>();
        } else {
            collection = new ArrayList<>();
        }
        
        if (boundaryType == BoundaryType.MIN) {
            // 空集合
            return collection;
        } else if (boundaryType == BoundaryType.MAX) {
            // 大集合
            for (int i = 0; i < 100; i++) {
                collection.add("item" + i);
            }
        } else {
            collection.add("single_item");
        }
        
        return collection;
    }
    
    /**
     * 生成随机Map
     * 
     * @param options 构建选项
     * @return 随机Map
     */
    private Map<String, Object> generateRandomMap(BuilderOptions options) {
        Map<String, Object> map = new HashMap<>();
        int size = random.nextInt(options.getCollectionMaxSize()) + 1;
        for (int i = 0; i < size; i++) {
            map.put("key" + i, "value" + i);
        }
        return map;
    }
    
    /**
     * 生成随机数组
     * 
     * @param clazz 数组类型
     * @param options 构建选项
     * @return 随机数组
     */
    private Object generateRandomArray(Class<?> clazz, BuilderOptions options) {
        Class<?> componentType = clazz.getComponentType();
        int length = random.nextInt(options.getCollectionMaxSize()) + 1;
        
        if (componentType == String.class) {
            String[] array = new String[length];
            for (int i = 0; i < length; i++) {
                array[i] = generateRandomString(5);
            }
            return array;
        } else if (componentType == int.class) {
            int[] array = new int[length];
            for (int i = 0; i < length; i++) {
                array[i] = random.nextInt();
            }
            return array;
        }
        
        return new Object[0];
    }
    
    /**
     * 构建自定义对象
     * 
     * @param clazz 对象类型
     * @param options 构建选项
     * @param <T> 类型参数
     * @return 自定义对象
     */
    private <T> T buildCustomObject(Class<T> clazz, BuilderOptions options) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();
        
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || 
                Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            
            field.setAccessible(true);
            Object value = generateFieldValue(field, options);
            if (value != null) {
                field.set(instance, value);
            }
        }
        
        return instance;
    }
    
    /**
     * 生成字段值
     * 
     * @param field 字段
     * @param options 构建选项
     * @return 字段值
     */
    private Object generateFieldValue(Field field, BuilderOptions options) {
        Class<?> fieldType = field.getType();
        
        if (fieldType == String.class) {
            return generateRandomString(options.getStringLength());
        } else if (fieldType == LocalDateTime.class) {
            return LocalDateTime.now().minusDays(random.nextInt(30));
        } else if (fieldType == LocalDate.class) {
            return LocalDate.now().minusDays(random.nextInt(30));
        } else if (isPrimitiveOrWrapper(fieldType)) {
            return generatePrimitiveValue(fieldType);
        }
        
        return null;
    }
    
    /**
     * 边界类型枚举
     */
    public enum BoundaryType {
        /** 最小值 */
        MIN,
        /** 最大值 */
        MAX,
        /** 零值 */
        ZERO,
        /** 负值 */
        NEGATIVE,
        /** 正值 */
        POSITIVE
    }
    
    /**
     * 构建选项
     */
    public static class BuilderOptions {
        private int stringLength = 10;
        private int collectionMaxSize = 5;
        private boolean nullAllowed = false;
        
        // Getters and Setters
        public int getStringLength() { return stringLength; }
        public BuilderOptions stringLength(int stringLength) { 
            this.stringLength = stringLength; 
            return this; 
        }
        
        public int getCollectionMaxSize() { return collectionMaxSize; }
        public BuilderOptions collectionMaxSize(int collectionMaxSize) { 
            this.collectionMaxSize = collectionMaxSize; 
            return this; 
        }
        
        public boolean isNullAllowed() { return nullAllowed; }
        public BuilderOptions nullAllowed(boolean nullAllowed) { 
            this.nullAllowed = nullAllowed; 
            return this; 
        }
    }
    
    /**
     * 数据模板接口
     * 
     * @param <T> 类型参数
     */
    @FunctionalInterface
    public interface DataTemplate<T> {
        /**
         * 构建对象
         * 
         * @param builder 数据构建器
         * @param options 构建选项
         * @return 构建的对象
         */
        T build(TestDataBuilder builder, BuilderOptions options);
    }
}