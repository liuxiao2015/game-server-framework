/*
 * 文件名: Archetype.java
 * 用途: ECS实体原型定义
 * 实现内容:
 *   - 实体原型定义（组件组合模板）
 *   - 原型继承支持
 *   - 原型参数化
 *   - 原型注册表
 *   - 组件预设值管理
 * 技术选型:
 *   - 位掩码技术实现高效组件匹配
 *   - 原型继承链支持复用
 *   - 参数化模板支持灵活配置
 * 依赖关系:
 *   - 依赖Component接口定义组件类型
 *   - 被EntityFactory用于批量创建实体
 *   - 提供实体创建的模板和配置
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.archetype;

import com.lx.gameserver.frame.ecs.core.Component;
import lombok.Getter;
import lombok.ToString;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECS实体原型
 * <p>
 * 原型定义了一组组件的组合模板，用于批量创建具有相同组件结构的实体。
 * 支持原型继承、参数化配置和组件预设值，提高实体创建效率和代码复用性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Getter
@ToString
public class Archetype {
    
    /**
     * 原型ID生成器
     */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    
    /**
     * 原型注册表
     */
    private static final Map<String, Archetype> REGISTRY = new ConcurrentHashMap<>();
    
    /**
     * 原型ID
     */
    private final long id;
    
    /**
     * 原型名称
     */
    private final String name;
    
    /**
     * 原型描述
     */
    private final String description;
    
    /**
     * 父原型（支持继承）
     */
    private final Archetype parent;
    
    /**
     * 组件类型集合
     */
    private final Set<Class<? extends Component>> componentTypes;
    
    /**
     * 组件预设值
     */
    private final Map<Class<? extends Component>, Component> defaultComponents;
    
    /**
     * 原型参数
     */
    private final Map<String, Object> parameters;
    
    /**
     * 组件位掩码（用于快速匹配）
     */
    private final long componentMask;
    
    /**
     * 创建时间
     */
    private final long createTime;
    
    /**
     * 是否可继承
     */
    private final boolean inheritable;
    
    /**
     * 构造函数
     */
    private Archetype(Builder builder) {
        this.id = ID_GENERATOR.getAndIncrement();
        this.name = builder.name;
        this.description = builder.description;
        this.parent = builder.parent;
        this.componentTypes = Collections.unmodifiableSet(new HashSet<>(builder.componentTypes));
        this.defaultComponents = Collections.unmodifiableMap(new HashMap<>(builder.defaultComponents));
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
        this.componentMask = calculateComponentMask();
        this.createTime = System.currentTimeMillis();
        this.inheritable = builder.inheritable;
    }
    
    /**
     * 计算组件位掩码
     */
    private long calculateComponentMask() {
        long mask = 0L;
        for (Class<? extends Component> componentType : getAllComponentTypes()) {
            // 这里简化处理，实际应该使用ComponentManager的类型ID
            mask |= (1L << Math.abs(componentType.hashCode() % 64));
        }
        return mask;
    }
    
    /**
     * 获取所有组件类型（包括继承的）
     */
    public Set<Class<? extends Component>> getAllComponentTypes() {
        Set<Class<? extends Component>> allTypes = new HashSet<>(componentTypes);
        if (parent != null) {
            allTypes.addAll(parent.getAllComponentTypes());
        }
        return allTypes;
    }
    
    /**
     * 获取所有默认组件（包括继承的）
     */
    public Map<Class<? extends Component>, Component> getAllDefaultComponents() {
        Map<Class<? extends Component>, Component> allDefaults = new HashMap<>();
        if (parent != null) {
            allDefaults.putAll(parent.getAllDefaultComponents());
        }
        allDefaults.putAll(defaultComponents);
        return allDefaults;
    }
    
    /**
     * 获取所有参数（包括继承的）
     */
    public Map<String, Object> getAllParameters() {
        Map<String, Object> allParams = new HashMap<>();
        if (parent != null) {
            allParams.putAll(parent.getAllParameters());
        }
        allParams.putAll(parameters);
        return allParams;
    }
    
    /**
     * 检查是否包含指定组件类型
     */
    public boolean hasComponent(Class<? extends Component> componentType) {
        return getAllComponentTypes().contains(componentType);
    }
    
    /**
     * 获取指定组件的默认值
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getDefaultComponent(Class<T> componentType) {
        return (T) getAllDefaultComponents().get(componentType);
    }
    
    /**
     * 获取参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        T value = (T) getAllParameters().get(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 检查是否与另一个原型兼容
     */
    public boolean isCompatibleWith(Archetype other) {
        if (other == null) {
            return false;
        }
        
        Set<Class<? extends Component>> thisTypes = getAllComponentTypes();
        Set<Class<? extends Component>> otherTypes = other.getAllComponentTypes();
        
        // 检查是否有共同的组件类型
        for (Class<? extends Component> type : thisTypes) {
            if (otherTypes.contains(type)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 注册原型
     */
    public static void register(Archetype archetype) {
        REGISTRY.put(archetype.getName(), archetype);
    }
    
    /**
     * 获取注册的原型
     */
    public static Archetype get(String name) {
        return REGISTRY.get(name);
    }
    
    /**
     * 获取所有注册的原型
     */
    public static Collection<Archetype> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
    
    /**
     * 清空注册表（仅用于测试）
     */
    public static void clearRegistry() {
        REGISTRY.clear();
    }
    
    /**
     * 原型构建器
     */
    public static class Builder {
        private String name;
        private String description = "";
        private Archetype parent;
        private final Set<Class<? extends Component>> componentTypes = new HashSet<>();
        private final Map<Class<? extends Component>, Component> defaultComponents = new HashMap<>();
        private final Map<String, Object> parameters = new HashMap<>();
        private boolean inheritable = true;
        
        /**
         * 设置原型名称
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        /**
         * 设置原型描述
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        /**
         * 设置父原型
         */
        public Builder parent(Archetype parent) {
            this.parent = parent;
            return this;
        }
        
        /**
         * 设置父原型（通过名称）
         */
        public Builder parent(String parentName) {
            this.parent = REGISTRY.get(parentName);
            return this;
        }
        
        /**
         * 添加组件类型
         */
        public Builder component(Class<? extends Component> componentType) {
            this.componentTypes.add(componentType);
            return this;
        }
        
        /**
         * 添加多个组件类型
         */
        @SafeVarargs
        public final Builder components(Class<? extends Component>... componentTypes) {
            this.componentTypes.addAll(Arrays.asList(componentTypes));
            return this;
        }
        
        /**
         * 设置组件默认值
         */
        public <T extends Component> Builder defaultComponent(Class<T> componentType, T component) {
            this.componentTypes.add(componentType);
            this.defaultComponents.put(componentType, component);
            return this;
        }
        
        /**
         * 设置参数
         */
        public Builder parameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }
        
        /**
         * 设置多个参数
         */
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }
        
        /**
         * 设置是否可继承
         */
        public Builder inheritable(boolean inheritable) {
            this.inheritable = inheritable;
            return this;
        }
        
        /**
         * 构建原型
         */
        public Archetype build() {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("原型名称不能为空");
            }
            
            if (parent != null && !parent.isInheritable()) {
                throw new IllegalArgumentException("父原型不允许被继承");
            }
            
            return new Archetype(this);
        }
        
        /**
         * 构建并注册原型
         */
        public Archetype buildAndRegister() {
            Archetype archetype = build();
            register(archetype);
            return archetype;
        }
    }
    
    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 创建构建器（指定名称）
     */
    public static Builder builder(String name) {
        return new Builder().name(name);
    }
}