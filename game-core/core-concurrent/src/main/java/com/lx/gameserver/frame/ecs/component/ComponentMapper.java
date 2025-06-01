/*
 * 文件名: ComponentMapper.java
 * 用途: 组件快速访问器实现
 * 实现内容:
 *   - 组件快速访问器
 *   - 类型安全的组件获取
 *   - 批量组件访问
 *   - 组件存在性检查
 *   - 性能优化（缓存友好）
 * 技术选型:
 *   - 泛型确保类型安全
 *   - 缓存技术提升访问性能
 *   - 批量操作减少方法调用开销
 * 依赖关系:
 *   - 被System使用进行组件访问
 *   - 依赖World获取组件数据
 *   - 提供高性能的组件访问接口
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.ecs.core.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 组件映射器
 * <p>
 * 提供类型安全、高性能的组件访问功能。
 * 支持单个组件访问、批量访问、存在性检查等操作。
 * 通过缓存和优化技术提升访问性能。
 * </p>
 *
 * @param <T> 组件类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ComponentMapper<T extends Component> {
    
    /**
     * 组件类型
     */
    private final Class<T> componentClass;
    
    /**
     * ECS世界引用
     */
    private final World world;
    
    /**
     * 组件类型ID（用于性能优化）
     */
    private final int typeId;
    
    /**
     * 缓存的组件映射（可选，用于高频访问优化）
     */
    private final Map<Long, T> componentCache;
    
    /**
     * 是否启用缓存
     */
    private final boolean cacheEnabled;
    
    /**
     * 统计信息
     */
    private final MapperStatistics statistics;
    
    /**
     * 构造函数
     *
     * @param componentClass 组件类型
     * @param world ECS世界
     */
    public ComponentMapper(Class<T> componentClass, World world) {
        this(componentClass, world, false);
    }
    
    /**
     * 构造函数
     *
     * @param componentClass 组件类型
     * @param world ECS世界
     * @param cacheEnabled 是否启用缓存
     */
    public ComponentMapper(Class<T> componentClass, World world, boolean cacheEnabled) {
        this.componentClass = Objects.requireNonNull(componentClass, "组件类型不能为null");
        this.world = Objects.requireNonNull(world, "World不能为null");
        this.cacheEnabled = cacheEnabled;
        this.typeId = generateTypeId(componentClass);
        this.componentCache = cacheEnabled ? new ConcurrentHashMap<>() : null;
        this.statistics = new MapperStatistics();
    }
    
    /**
     * 获取实体的组件
     *
     * @param entity 实体
     * @return 组件实例，如果不存在返回null
     */
    public T get(Entity entity) {
        return get(entity.getId());
    }
    
    /**
     * 获取实体的组件
     *
     * @param entityId 实体ID
     * @return 组件实例，如果不存在返回null
     */
    public T get(long entityId) {
        statistics.incrementAccessCount();
        
        if (cacheEnabled) {
            T cached = componentCache.get(entityId);
            if (cached != null) {
                statistics.incrementCacheHitCount();
                return cached;
            }
        }
        
        T component = world.getComponent(entityId, componentClass);
        
        if (cacheEnabled && component != null) {
            componentCache.put(entityId, component);
        }
        
        return component;
    }
    
    /**
     * 检查实体是否有组件
     *
     * @param entity 实体
     * @return 如果有组件返回true
     */
    public boolean has(Entity entity) {
        return has(entity.getId());
    }
    
    /**
     * 检查实体是否有组件
     *
     * @param entityId 实体ID
     * @return 如果有组件返回true
     */
    public boolean has(long entityId) {
        statistics.incrementAccessCount();
        
        if (cacheEnabled && componentCache.containsKey(entityId)) {
            statistics.incrementCacheHitCount();
            return true;
        }
        
        return world.hasComponent(entityId, componentClass);
    }
    
    /**
     * 获取或创建组件
     *
     * @param entity 实体
     * @param factory 组件工厂
     * @return 组件实例
     */
    public T getOrCreate(Entity entity, ComponentFactory<T> factory) {
        return getOrCreate(entity.getId(), factory);
    }
    
    /**
     * 获取或创建组件
     *
     * @param entityId 实体ID
     * @param factory 组件工厂
     * @return 组件实例
     */
    public T getOrCreate(long entityId, ComponentFactory<T> factory) {
        T component = get(entityId);
        if (component == null) {
            component = factory.create();
            world.addComponent(entityId, component);
            
            if (cacheEnabled) {
                componentCache.put(entityId, component);
            }
        }
        return component;
    }
    
    /**
     * 批量获取组件
     *
     * @param entities 实体集合
     * @return 组件映射表
     */
    public Map<Long, T> getBatch(Collection<Entity> entities) {
        Map<Long, T> result = new HashMap<>();
        
        for (Entity entity : entities) {
            T component = get(entity.getId());
            if (component != null) {
                result.put(entity.getId(), component);
            }
        }
        
        return result;
    }
    
    /**
     * 批量获取组件（按实体ID）
     *
     * @param entityIds 实体ID集合
     * @return 组件映射表
     */
    public Map<Long, T> getBatchByIds(Collection<Long> entityIds) {
        Map<Long, T> result = new HashMap<>();
        
        for (Long entityId : entityIds) {
            T component = get(entityId);
            if (component != null) {
                result.put(entityId, component);
            }
        }
        
        return result;
    }
    
    /**
     * 遍历所有拥有该组件的实体
     *
     * @param consumer 消费者函数
     */
    public void forEach(Consumer<T> consumer) {
        Collection<Entity> entities = world.getAllEntities();
        
        for (Entity entity : entities) {
            T component = get(entity.getId());
            if (component != null) {
                consumer.accept(component);
            }
        }
    }
    
    /**
     * 遍历所有拥有该组件的实体（带实体参数）
     *
     * @param consumer 消费者函数
     */
    public void forEachWithEntity(EntityComponentConsumer<T> consumer) {
        Collection<Entity> entities = world.getAllEntities();
        
        for (Entity entity : entities) {
            T component = get(entity.getId());
            if (component != null) {
                consumer.accept(entity, component);
            }
        }
    }
    
    /**
     * 获取所有拥有该组件的实体
     *
     * @return 实体集合
     */
    public Collection<Entity> getEntities() {
        List<Entity> result = new ArrayList<>();
        Collection<Entity> entities = world.getAllEntities();
        
        for (Entity entity : entities) {
            if (has(entity.getId())) {
                result.add(entity);
            }
        }
        
        return result;
    }
    
    /**
     * 获取所有组件实例
     *
     * @return 组件集合
     */
    public Collection<T> getComponents() {
        List<T> result = new ArrayList<>();
        Collection<Entity> entities = world.getAllEntities();
        
        for (Entity entity : entities) {
            T component = get(entity.getId());
            if (component != null) {
                result.add(component);
            }
        }
        
        return result;
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        if (cacheEnabled && componentCache != null) {
            componentCache.clear();
            statistics.resetCacheStats();
        }
    }
    
    /**
     * 从缓存中移除实体
     *
     * @param entityId 实体ID
     */
    public void evictFromCache(long entityId) {
        if (cacheEnabled && componentCache != null) {
            componentCache.remove(entityId);
        }
    }
    
    /**
     * 获取组件类型
     *
     * @return 组件类型
     */
    public Class<T> getComponentClass() {
        return componentClass;
    }
    
    /**
     * 获取类型ID
     *
     * @return 类型ID
     */
    public int getTypeId() {
        return typeId;
    }
    
    /**
     * 是否启用缓存
     *
     * @return 如果启用缓存返回true
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    /**
     * 获取缓存大小
     *
     * @return 缓存大小
     */
    public int getCacheSize() {
        return cacheEnabled && componentCache != null ? componentCache.size() : 0;
    }
    
    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public MapperStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 生成类型ID
     *
     * @param componentClass 组件类型
     * @return 类型ID
     */
    private static int generateTypeId(Class<? extends Component> componentClass) {
        return componentClass.hashCode();
    }
    
    /**
     * 组件工厂接口
     *
     * @param <T> 组件类型
     */
    @FunctionalInterface
    public interface ComponentFactory<T extends Component> {
        /**
         * 创建组件实例
         *
         * @return 组件实例
         */
        T create();
    }
    
    /**
     * 实体组件消费者接口
     *
     * @param <T> 组件类型
     */
    @FunctionalInterface
    public interface EntityComponentConsumer<T extends Component> {
        /**
         * 消费实体和组件
         *
         * @param entity 实体
         * @param component 组件
         */
        void accept(Entity entity, T component);
    }
    
    /**
     * 映射器统计信息
     */
    public static class MapperStatistics {
        
        private long accessCount = 0;
        private long cacheHitCount = 0;
        private final long createTime = java.lang.System.currentTimeMillis();
        
        /**
         * 增加访问次数
         */
        public synchronized void incrementAccessCount() {
            accessCount++;
        }
        
        /**
         * 增加缓存命中次数
         */
        public synchronized void incrementCacheHitCount() {
            cacheHitCount++;
        }
        
        /**
         * 获取访问次数
         *
         * @return 访问次数
         */
        public synchronized long getAccessCount() {
            return accessCount;
        }
        
        /**
         * 获取缓存命中次数
         *
         * @return 缓存命中次数
         */
        public synchronized long getCacheHitCount() {
            return cacheHitCount;
        }
        
        /**
         * 获取缓存命中率
         *
         * @return 缓存命中率（0-1之间）
         */
        public synchronized double getCacheHitRate() {
            return accessCount > 0 ? (double) cacheHitCount / accessCount : 0.0;
        }
        
        /**
         * 获取创建时间
         *
         * @return 创建时间戳
         */
        public long getCreateTime() {
            return createTime;
        }
        
        /**
         * 重置缓存统计
         */
        public synchronized void resetCacheStats() {
            cacheHitCount = 0;
        }
        
        /**
         * 重置所有统计
         */
        public synchronized void reset() {
            accessCount = 0;
            cacheHitCount = 0;
        }
        
        @Override
        public synchronized String toString() {
            return String.format("MapperStatistics{access=%d, cacheHit=%d, hitRate=%.2f%%}", 
                accessCount, cacheHitCount, getCacheHitRate() * 100);
        }
    }
}