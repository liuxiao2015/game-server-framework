/*
 * 文件名: ArchetypeBuilder.java
 * 用途: ECS原型构建器扩展
 * 实现内容:
 *   - 链式API设计
 *   - 组件预设值配置
 *   - 原型验证机制
 *   - 批量创建优化
 *   - 构建缓存支持
 * 技术选型:
 *   - 流式API提供更好的用户体验
 *   - 验证器模式确保原型正确性
 *   - 缓存机制提高构建性能
 * 依赖关系:
 *   - 扩展Archetype.Builder功能
 *   - 被EntityFactory使用进行实体创建
 *   - 提供更强大的原型构建能力
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.archetype;

import com.lx.gameserver.frame.ecs.core.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * ECS原型构建器扩展
 * <p>
 * 提供更强大的原型构建功能，包括链式API、验证机制、批量创建优化等。
 * 支持复杂的原型配置和构建缓存，提高开发效率和运行性能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class ArchetypeBuilder {
    
    /**
     * 构建缓存
     */
    private static final Map<String, Archetype> BUILD_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 验证器列表
     */
    private static final List<Predicate<Archetype.Builder>> VALIDATORS = new ArrayList<>();
    
    /**
     * 组件工厂
     */
    private final Map<Class<? extends Component>, Supplier<? extends Component>> componentFactories = new HashMap<>();
    
    /**
     * 组件配置器
     */
    private final Map<Class<? extends Component>, Consumer<Component>> componentConfigurators = new HashMap<>();
    
    /**
     * 内部构建器
     */
    private final Archetype.Builder builder;
    
    /**
     * 是否启用缓存
     */
    private boolean cacheEnabled = true;
    
    /**
     * 是否启用验证
     */
    private boolean validationEnabled = true;
    
    /**
     * 构造函数
     */
    private ArchetypeBuilder() {
        this.builder = new Archetype.Builder();
    }
    
    /**
     * 构造函数（指定名称）
     */
    private ArchetypeBuilder(String name) {
        this.builder = new Archetype.Builder().name(name);
    }
    
    /**
     * 创建构建器
     */
    public static ArchetypeBuilder create() {
        return new ArchetypeBuilder();
    }
    
    /**
     * 创建构建器（指定名称）
     */
    public static ArchetypeBuilder create(String name) {
        return new ArchetypeBuilder(name);
    }
    
    /**
     * 设置原型名称
     */
    public ArchetypeBuilder name(String name) {
        builder.name(name);
        return this;
    }
    
    /**
     * 设置原型描述
     */
    public ArchetypeBuilder description(String description) {
        builder.description(description);
        return this;
    }
    
    /**
     * 设置父原型
     */
    public ArchetypeBuilder parent(Archetype parent) {
        builder.parent(parent);
        return this;
    }
    
    /**
     * 设置父原型（通过名称）
     */
    public ArchetypeBuilder parent(String parentName) {
        builder.parent(parentName);
        return this;
    }
    
    /**
     * 添加组件类型
     */
    public ArchetypeBuilder component(Class<? extends Component> componentType) {
        builder.component(componentType);
        return this;
    }
    
    /**
     * 添加多个组件类型
     */
    @SafeVarargs
    public final ArchetypeBuilder components(Class<? extends Component>... componentTypes) {
        builder.components(componentTypes);
        return this;
    }
    
    /**
     * 添加组件集合
     */
    public ArchetypeBuilder components(Collection<Class<? extends Component>> componentTypes) {
        for (Class<? extends Component> type : componentTypes) {
            builder.component(type);
        }
        return this;
    }
    
    /**
     * 设置组件默认值
     */
    public <T extends Component> ArchetypeBuilder defaultComponent(Class<T> componentType, T component) {
        builder.defaultComponent(componentType, component);
        return this;
    }
    
    /**
     * 设置组件工厂
     */
    public <T extends Component> ArchetypeBuilder componentFactory(Class<T> componentType, 
                                                                   Supplier<T> factory) {
        componentFactories.put(componentType, factory);
        builder.component(componentType);
        return this;
    }
    
    /**
     * 设置组件配置器
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> ArchetypeBuilder componentConfigurator(Class<T> componentType, 
                                                                         Consumer<T> configurator) {
        componentConfigurators.put(componentType, (Consumer<Component>) configurator);
        return this;
    }
    
    /**
     * 添加带工厂的组件
     */
    public <T extends Component> ArchetypeBuilder componentWithFactory(Class<T> componentType, 
                                                                        Supplier<T> factory) {
        T component = factory.get();
        builder.defaultComponent(componentType, component);
        return this;
    }
    
    /**
     * 添加带配置的组件
     */
    public <T extends Component> ArchetypeBuilder componentWithConfig(Class<T> componentType, 
                                                                       Supplier<T> factory,
                                                                       Consumer<T> configurator) {
        T component = factory.get();
        configurator.accept(component);
        builder.defaultComponent(componentType, component);
        return this;
    }
    
    /**
     * 设置参数
     */
    public ArchetypeBuilder parameter(String key, Object value) {
        builder.parameter(key, value);
        return this;
    }
    
    /**
     * 设置多个参数
     */
    public ArchetypeBuilder parameters(Map<String, Object> parameters) {
        builder.parameters(parameters);
        return this;
    }
    
    /**
     * 设置数值参数
     */
    public ArchetypeBuilder numericParameter(String key, Number value) {
        builder.parameter(key, value);
        return this;
    }
    
    /**
     * 设置字符串参数
     */
    public ArchetypeBuilder stringParameter(String key, String value) {
        builder.parameter(key, value);
        return this;
    }
    
    /**
     * 设置布尔参数
     */
    public ArchetypeBuilder booleanParameter(String key, boolean value) {
        builder.parameter(key, value);
        return this;
    }
    
    /**
     * 设置是否可继承
     */
    public ArchetypeBuilder inheritable(boolean inheritable) {
        builder.inheritable(inheritable);
        return this;
    }
    
    /**
     * 设置为可继承
     */
    public ArchetypeBuilder inheritable() {
        return inheritable(true);
    }
    
    /**
     * 设置为不可继承
     */
    public ArchetypeBuilder notInheritable() {
        return inheritable(false);
    }
    
    /**
     * 启用缓存
     */
    public ArchetypeBuilder enableCache() {
        this.cacheEnabled = true;
        return this;
    }
    
    /**
     * 禁用缓存
     */
    public ArchetypeBuilder disableCache() {
        this.cacheEnabled = false;
        return this;
    }
    
    /**
     * 启用验证
     */
    public ArchetypeBuilder enableValidation() {
        this.validationEnabled = true;
        return this;
    }
    
    /**
     * 禁用验证
     */
    public ArchetypeBuilder disableValidation() {
        this.validationEnabled = false;
        return this;
    }
    
    /**
     * 条件性添加组件
     */
    public ArchetypeBuilder componentIf(boolean condition, Class<? extends Component> componentType) {
        if (condition) {
            component(componentType);
        }
        return this;
    }
    
    /**
     * 条件性设置参数
     */
    public ArchetypeBuilder parameterIf(boolean condition, String key, Object value) {
        if (condition) {
            parameter(key, value);
        }
        return this;
    }
    
    /**
     * 复制现有原型的配置
     */
    public ArchetypeBuilder copyFrom(Archetype archetype) {
        // 复制组件类型
        for (Class<? extends Component> componentType : archetype.getComponentTypes()) {
            builder.component(componentType);
        }
        
        // 复制默认组件
        for (Map.Entry<Class<? extends Component>, Component> entry : 
             archetype.getDefaultComponents().entrySet()) {
            Component originalComponent = entry.getValue();
            Component clonedComponent;
            
            try {
                // 尝试克隆组件
                clonedComponent = originalComponent.clone();
            } catch (Exception e) {
                log.warn("无法克隆组件: {}", entry.getKey().getSimpleName(), e);
                builder.component(entry.getKey());
                continue;
            }
            
            // 使用未检查的转换来处理泛型问题
            @SuppressWarnings("unchecked")
            Class<Component> componentType = (Class<Component>) entry.getKey();
            builder.defaultComponent(componentType, clonedComponent);
        }
        
        // 复制参数
        builder.parameters(archetype.getParameters());
        
        return this;
    }
    
    /**
     * 构建原型
     */
    public Archetype build() {
        // 应用组件工厂
        applyComponentFactories();
        
        // 验证
        if (validationEnabled) {
            validate();
        }
        
        // 检查缓存
        String cacheKey = generateCacheKey();
        if (cacheEnabled && BUILD_CACHE.containsKey(cacheKey)) {
            log.debug("从缓存中获取原型: {}", cacheKey);
            return BUILD_CACHE.get(cacheKey);
        }
        
        // 构建原型
        Archetype archetype = builder.build();
        
        // 缓存结果
        if (cacheEnabled) {
            BUILD_CACHE.put(cacheKey, archetype);
            log.debug("原型已缓存: {}", cacheKey);
        }
        
        return archetype;
    }
    
    /**
     * 构建并注册原型
     */
    public Archetype buildAndRegister() {
        Archetype archetype = build();
        Archetype.register(archetype);
        return archetype;
    }
    
    /**
     * 应用组件工厂
     */
    @SuppressWarnings("unchecked")
    private void applyComponentFactories() {
        for (Map.Entry<Class<? extends Component>, Supplier<? extends Component>> entry : 
             componentFactories.entrySet()) {
            
            Class<? extends Component> componentType = entry.getKey();
            Component component = entry.getValue().get();
            
            // 应用配置器
            @SuppressWarnings("unchecked")
            Consumer<Component> configurator = (Consumer<Component>) componentConfigurators.get(componentType);
            if (configurator != null) {
                configurator.accept(component);
            }
            
            // 使用未检查的转换来处理泛型问题
            @SuppressWarnings("unchecked")
            Class<Component> componentClass = (Class<Component>) componentType;
            builder.defaultComponent(componentClass, component);
        }
    }
    
    /**
     * 验证构建器
     */
    private void validate() {
        for (Predicate<Archetype.Builder> validator : VALIDATORS) {
            if (!validator.test(builder)) {
                throw new IllegalStateException("原型验证失败");
            }
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey() {
        // 简化实现，实际应该包含所有配置信息
        return String.valueOf(builder.hashCode());
    }
    
    /**
     * 添加全局验证器
     */
    public static void addValidator(Predicate<Archetype.Builder> validator) {
        VALIDATORS.add(validator);
    }
    
    /**
     * 清除验证器
     */
    public static void clearValidators() {
        VALIDATORS.clear();
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        BUILD_CACHE.clear();
    }
    
    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return BUILD_CACHE.size();
    }
}