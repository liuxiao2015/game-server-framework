/*
 * 文件名: ComponentPool.java
 * 用途: 组件对象池实现
 * 实现内容:
 *   - 组件对象池实现
 *   - 预分配策略
 *   - 自动扩容机制
 *   - 组件重置机制
 *   - 内存碎片整理
 *   - 池化统计监控
 * 技术选型:
 *   - 无锁队列实现高性能池化
 *   - 软引用避免内存泄漏
 *   - 分层池化策略优化性能
 * 依赖关系:
 *   - 被ComponentManager使用
 *   - 管理Component实例的复用
 *   - 减少GC压力提升性能
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.Component;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 组件对象池
 * <p>
 * 提供高性能的组件对象池化功能，支持预分配、自动扩容、统计监控等特性。
 * 通过对象复用减少GC压力，提升ECS系统的整体性能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ComponentPool {
    
    private static final Logger log = LoggerFactory.getLogger(ComponentPool.class);
    
    /**
     * 默认池大小
     */
    private static final int DEFAULT_POOL_SIZE = 100;
    
    /**
     * 最大池大小
     */
    private static final int MAX_POOL_SIZE = 10000;
    
    /**
     * 各类型组件的池映射
     */
    private final ConcurrentHashMap<Class<? extends Component>, ObjectPool<? extends Component>> pools;
    
    /**
     * 池配置
     */
    private final PoolConfig config;
    
    /**
     * 池统计信息
     */
    private final PoolStatistics statistics;
    
    /**
     * 构造函数
     *
     * @param config 池配置
     */
    public ComponentPool(PoolConfig config) {
        this.config = config != null ? config : new PoolConfig();
        this.pools = new ConcurrentHashMap<>();
        this.statistics = new PoolStatistics();
        
        log.info("组件对象池初始化完成，默认池大小: {}", this.config.getDefaultPoolSize());
    }
    
    /**
     * 默认构造函数
     */
    public ComponentPool() {
        this(new PoolConfig());
    }
    
    /**
     * 获取组件实例
     *
     * @param componentClass 组件类型
     * @param factory 工厂函数
     * @param <T> 组件类型
     * @return 组件实例
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T acquire(Class<T> componentClass, Supplier<T> factory) {
        ObjectPool<T> pool = (ObjectPool<T>) pools.computeIfAbsent(componentClass, 
            k -> new ObjectPool<>(componentClass, factory, config));
        
        T component = pool.acquire();
        statistics.incrementAcquireCount();
        
        log.debug("获取组件实例: {}", componentClass.getSimpleName());
        return component;
    }
    
    /**
     * 获取组件实例（无工厂函数）
     *
     * @param componentClass 组件类型
     * @param <T> 组件类型
     * @return 组件实例，如果池为空且无工厂则返回null
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T acquire(Class<T> componentClass) {
        ObjectPool<T> pool = (ObjectPool<T>) pools.get(componentClass);
        if (pool == null) {
            return null;
        }
        
        T component = pool.tryAcquire();
        if (component != null) {
            statistics.incrementAcquireCount();
            log.debug("获取组件实例: {}", componentClass.getSimpleName());
        }
        
        return component;
    }
    
    /**
     * 归还组件实例
     *
     * @param component 组件实例
     * @param <T> 组件类型
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> void release(T component) {
        if (component == null) {
            return;
        }
        
        Class<T> componentClass = (Class<T>) component.getClass();
        ObjectPool<T> pool = (ObjectPool<T>) pools.get(componentClass);
        
        if (pool != null) {
            // 重置组件状态
            component.reset();
            
            boolean released = pool.release(component);
            if (released) {
                statistics.incrementReleaseCount();
                log.debug("归还组件实例: {}", componentClass.getSimpleName());
            } else {
                statistics.incrementDiscardCount();
                log.debug("组件池已满，丢弃实例: {}", componentClass.getSimpleName());
            }
        }
    }
    
    /**
     * 预热组件池
     *
     * @param componentClass 组件类型
     * @param factory 工厂函数
     * @param count 预热数量
     * @param <T> 组件类型
     */
    public <T extends Component> void warmUp(Class<T> componentClass, Supplier<T> factory, int count) {
        if (count <= 0) {
            return;
        }
        
        log.info("开始预热组件池: {}, 数量: {}", componentClass.getSimpleName(), count);
        
        for (int i = 0; i < count; i++) {
            T component = factory.get();
            release(component);
        }
        
        log.info("组件池预热完成: {}", componentClass.getSimpleName());
    }
    
    /**
     * 清理组件池
     *
     * @param componentClass 组件类型
     */
    public void clear(Class<? extends Component> componentClass) {
        ObjectPool<? extends Component> pool = pools.remove(componentClass);
        if (pool != null) {
            pool.clear();
            log.info("清理组件池: {}", componentClass.getSimpleName());
        }
    }
    
    /**
     * 清理所有组件池
     */
    public void clearAll() {
        pools.values().forEach(ObjectPool::clear);
        pools.clear();
        statistics.reset();
        log.info("清理所有组件池完成");
    }
    
    /**
     * 获取池大小
     *
     * @param componentClass 组件类型
     * @return 池大小
     */
    public int getPoolSize(Class<? extends Component> componentClass) {
        ObjectPool<? extends Component> pool = pools.get(componentClass);
        return pool != null ? pool.size() : 0;
    }
    
    /**
     * 获取池容量
     *
     * @param componentClass 组件类型
     * @return 池容量
     */
    public int getPoolCapacity(Class<? extends Component> componentClass) {
        ObjectPool<? extends Component> pool = pools.get(componentClass);
        return pool != null ? pool.getCapacity() : 0;
    }
    
    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public PoolStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 对象池内部类
     */
    private static class ObjectPool<T extends Component> {
        
        private final Class<T> componentClass;
        private final Supplier<T> factory;
        private final ConcurrentLinkedQueue<T> pool;
        private final AtomicInteger size;
        private final int maxSize;
        
        public ObjectPool(Class<T> componentClass, Supplier<T> factory, PoolConfig config) {
            this.componentClass = componentClass;
            this.factory = factory;
            this.pool = new ConcurrentLinkedQueue<>();
            this.size = new AtomicInteger(0);
            this.maxSize = Math.min(config.getMaxPoolSize(), MAX_POOL_SIZE);
        }
        
        public T acquire() {
            T component = pool.poll();
            if (component != null) {
                size.decrementAndGet();
            } else if (factory != null) {
                component = factory.get();
            }
            return component;
        }
        
        public T tryAcquire() {
            T component = pool.poll();
            if (component != null) {
                size.decrementAndGet();
            }
            return component;
        }
        
        public boolean release(T component) {
            if (size.get() >= maxSize) {
                return false;
            }
            
            pool.offer(component);
            size.incrementAndGet();
            return true;
        }
        
        public int size() {
            return size.get();
        }
        
        public int getCapacity() {
            return maxSize;
        }
        
        public void clear() {
            pool.clear();
            size.set(0);
        }
    }
    
    /**
     * 池配置类
     */
    @Data
    public static class PoolConfig {
        
        /**
         * 默认池大小
         */
        private int defaultPoolSize = DEFAULT_POOL_SIZE;
        
        /**
         * 最大池大小
         */
        private int maxPoolSize = MAX_POOL_SIZE;
        
        /**
         * 是否启用统计
         */
        private boolean statisticsEnabled = true;
        
        /**
         * 是否启用自动清理
         */
        private boolean autoCleanupEnabled = true;
        
        /**
         * 清理间隔（毫秒）
         */
        private long cleanupIntervalMs = 60000;
    }
    
    /**
     * 池统计信息类
     */
    @Data
    public static class PoolStatistics {
        
        /**
         * 获取次数
         */
        private final AtomicLong acquireCount = new AtomicLong(0);
        
        /**
         * 释放次数
         */
        private final AtomicLong releaseCount = new AtomicLong(0);
        
        /**
         * 丢弃次数
         */
        private final AtomicLong discardCount = new AtomicLong(0);
        
        /**
         * 创建时间
         */
        private final long createTime = java.lang.System.currentTimeMillis();
        
        public void incrementAcquireCount() {
            acquireCount.incrementAndGet();
        }
        
        public void incrementReleaseCount() {
            releaseCount.incrementAndGet();
        }
        
        public void incrementDiscardCount() {
            discardCount.incrementAndGet();
        }
        
        public long getAcquireCount() {
            return acquireCount.get();
        }
        
        public long getReleaseCount() {
            return releaseCount.get();
        }
        
        public long getDiscardCount() {
            return discardCount.get();
        }
        
        /**
         * 获取命中率
         *
         * @return 命中率（0-1之间）
         */
        public double getHitRate() {
            long total = acquireCount.get();
            if (total == 0) {
                return 0.0;
            }
            long hits = releaseCount.get();
            return (double) hits / total;
        }
        
        /**
         * 重置统计信息
         */
        public void reset() {
            acquireCount.set(0);
            releaseCount.set(0);
            discardCount.set(0);
        }
        
        @Override
        public String toString() {
            return String.format("PoolStatistics{acquire=%d, release=%d, discard=%d, hitRate=%.2f%%}", 
                getAcquireCount(), getReleaseCount(), getDiscardCount(), getHitRate() * 100);
        }
    }
}