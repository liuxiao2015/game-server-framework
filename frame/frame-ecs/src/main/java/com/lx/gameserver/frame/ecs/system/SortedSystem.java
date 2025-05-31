/*
 * 文件名: SortedSystem.java
 * 用途: 排序处理系统基类
 * 实现内容:
 *   - 排序处理系统基类
 *   - 实体自动排序功能
 *   - 自定义排序规则支持
 *   - 排序优化和缓存机制
 * 技术选型:
 *   - 比较器模式支持自定义排序
 *   - 缓存机制优化排序性能
 *   - 增量排序减少计算开销
 * 依赖关系:
 *   - 继承IteratingSystem
 *   - 为需要排序处理的系统提供基础
 *   - 支持各种排序算法策略
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排序处理系统
 * <p>
 * 对符合条件的实体进行排序，然后按顺序处理。
 * 支持自定义排序规则和排序缓存优化。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class SortedSystem extends IteratingSystem {
    
    /**
     * 实体比较器
     */
    private final Comparator<Entity> entityComparator;
    
    /**
     * 排序结果缓存
     */
    private final Map<Long, List<Entity>> sortedEntitiesCache;
    
    /**
     * 缓存版本号
     */
    private long cacheVersion = 0;
    
    /**
     * 上次查询版本号
     */
    private long lastQueryVersion = -1;
    
    /**
     * 是否启用排序缓存
     */
    private boolean enableCache = true;
    
    /**
     * 排序间隔（毫秒）
     */
    private long sortInterval = 100;
    
    /**
     * 上次排序时间
     */
    private long lastSortTime = 0;
    
    /**
     * 是否使用增量排序
     */
    private boolean useIncrementalSort = true;
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param comparator 实体比较器
     * @param requiredComponents 必须包含的组件
     */
    @SafeVarargs
    protected SortedSystem(String name, int priority, Comparator<Entity> comparator, 
                          Class<? extends Component>... requiredComponents) {
        super(name, priority, requiredComponents);
        this.entityComparator = comparator != null ? comparator : this::defaultCompare;
        this.sortedEntitiesCache = new ConcurrentHashMap<>();
    }
    
    /**
     * 构造函数（使用默认比较器）
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param requiredComponents 必须包含的组件
     */
    @SafeVarargs
    protected SortedSystem(String name, int priority, Class<? extends Component>... requiredComponents) {
        this(name, priority, null, requiredComponents);
    }
    
    @Override
    protected void onSystemInitialize() {
        super.onSystemInitialize();
        // 初始化排序相关配置
        onSortSystemInitialize();
    }
    
    @Override
    protected void onUpdate(float deltaTime) {
        // 获取符合条件的实体
        Collection<Entity> entities = getEntities();
        
        if (entities.isEmpty()) {
            return;
        }
        
        // 检查是否需要重新排序
        long currentTime = java.lang.System.currentTimeMillis();
        boolean needsSort = shouldResort(entities, currentTime);
        
        List<Entity> sortedEntities;
        if (needsSort) {
            sortedEntities = sortEntities(entities, currentTime);
        } else {
            sortedEntities = getCachedSortedEntities(entities);
        }
        
        // 处理排序后的实体
        processSortedEntities(sortedEntities, deltaTime);
    }
    
    /**
     * 获取实体查询
     */
    private Object getEntityQuery() {
        // 这里需要访问父类的 entityQuery，可能需要调整访问权限
        // 暂时返回一个模拟对象
        return new Object() {
            public Collection<Entity> execute() {
                return new ArrayList<>();
            }
        };
    }
    
    /**
     * 检查是否需要重新排序
     *
     * @param entities 实体集合
     * @param currentTime 当前时间
     * @return 如果需要重新排序返回true
     */
    private boolean shouldResort(Collection<Entity> entities, long currentTime) {
        // 如果禁用缓存，总是重新排序
        if (!enableCache) {
            return true;
        }
        
        // 如果是第一次排序
        if (lastSortTime == 0) {
            return true;
        }
        
        // 如果超过排序间隔
        if (currentTime - lastSortTime >= sortInterval) {
            return true;
        }
        
        // 如果实体集合发生变化
        long currentHash = calculateEntitiesHash(entities);
        if (currentHash != cacheVersion) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 排序实体
     *
     * @param entities 实体集合
     * @param currentTime 当前时间
     * @return 排序后的实体列表
     */
    private List<Entity> sortEntities(Collection<Entity> entities, long currentTime) {
        List<Entity> entityList = new ArrayList<>(entities);
        
        // 执行排序
        long startTime = java.lang.System.nanoTime();
        
        if (useIncrementalSort && !sortedEntitiesCache.isEmpty()) {
            // 增量排序（如果之前有缓存）
            entityList = performIncrementalSort(entityList);
        } else {
            // 完整排序
            entityList.sort(entityComparator);
        }
        
        long endTime = java.lang.System.nanoTime();
        long sortTime = (endTime - startTime) / 1_000_000; // 转换为毫秒
        
        // 更新缓存
        if (enableCache) {
            cacheVersion = calculateEntitiesHash(entities);
            sortedEntitiesCache.put(cacheVersion, new ArrayList<>(entityList));
        }
        
        lastSortTime = currentTime;
        
        // 记录排序统计信息
        onSortCompleted(entityList.size(), sortTime);
        
        return entityList;
    }
    
    /**
     * 执行增量排序
     *
     * @param entities 实体列表
     * @return 排序后的实体列表
     */
    private List<Entity> performIncrementalSort(List<Entity> entities) {
        // 简化的增量排序实现
        // 实际实现中可以使用更高效的增量排序算法
        entities.sort(entityComparator);
        return entities;
    }
    
    /**
     * 获取缓存的排序结果
     *
     * @param entities 实体集合
     * @return 排序后的实体列表
     */
    private List<Entity> getCachedSortedEntities(Collection<Entity> entities) {
        long hash = calculateEntitiesHash(entities);
        List<Entity> cached = sortedEntitiesCache.get(hash);
        
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        
        // 如果缓存不存在，执行排序
        return sortEntities(entities, java.lang.System.currentTimeMillis());
    }
    
    /**
     * 计算实体集合的哈希值
     *
     * @param entities 实体集合
     * @return 哈希值
     */
    private long calculateEntitiesHash(Collection<Entity> entities) {
        long hash = 0;
        for (Entity entity : entities) {
            hash = hash * 31 + entity.getId();
        }
        return hash;
    }
    
    /**
     * 处理排序后的实体
     *
     * @param sortedEntities 排序后的实体列表
     * @param deltaTime 时间增量
     */
    protected void processSortedEntities(List<Entity> sortedEntities, float deltaTime) {
        // 按排序顺序处理实体
        for (int i = 0; i < sortedEntities.size(); i++) {
            Entity entity = sortedEntities.get(i);
            if (shouldProcessEntity(entity)) {
                processSortedEntity(entity, i, deltaTime);
            }
        }
    }
    
    /**
     * 处理单个排序后的实体
     *
     * @param entity 实体
     * @param sortIndex 排序索引
     * @param deltaTime 时间增量
     */
    protected abstract void processSortedEntity(Entity entity, int sortIndex, float deltaTime);
    
    /**
     * 默认实体比较方法（子类可重写）
     *
     * @param e1 实体1
     * @param e2 实体2
     * @return 比较结果
     */
    protected int defaultCompare(Entity e1, Entity e2) {
        return Long.compare(e1.getId(), e2.getId());
    }
    
    /**
     * 排序系统初始化回调
     */
    protected void onSortSystemInitialize() {
        // 默认空实现
    }
    
    /**
     * 排序完成回调
     *
     * @param entityCount 实体数量
     * @param sortTime 排序耗时（毫秒）
     */
    protected void onSortCompleted(int entityCount, long sortTime) {
        // 默认空实现，子类可重写以记录统计信息
    }
    
    /**
     * 清理排序缓存
     */
    public void clearSortCache() {
        sortedEntitiesCache.clear();
        cacheVersion = 0;
        lastSortTime = 0;
    }
    
    /**
     * 设置是否启用缓存
     *
     * @param enableCache 是否启用缓存
     */
    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
        if (!enableCache) {
            clearSortCache();
        }
    }
    
    /**
     * 设置排序间隔
     *
     * @param sortInterval 排序间隔（毫秒）
     */
    public void setSortInterval(long sortInterval) {
        this.sortInterval = Math.max(0, sortInterval);
    }
    
    /**
     * 设置是否使用增量排序
     *
     * @param useIncrementalSort 是否使用增量排序
     */
    public void setUseIncrementalSort(boolean useIncrementalSort) {
        this.useIncrementalSort = useIncrementalSort;
    }
    
    /**
     * 获取实体比较器
     *
     * @return 实体比较器
     */
    public Comparator<Entity> getEntityComparator() {
        return entityComparator;
    }
    
    /**
     * 检查是否启用缓存
     *
     * @return 如果启用缓存返回true
     */
    public boolean isCacheEnabled() {
        return enableCache;
    }
    
    /**
     * 获取排序间隔
     *
     * @return 排序间隔（毫秒）
     */
    public long getSortInterval() {
        return sortInterval;
    }
    
    /**
     * 检查是否使用增量排序
     *
     * @return 如果使用增量排序返回true
     */
    public boolean isUseIncrementalSort() {
        return useIncrementalSort;
    }
    
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // SortedSystem不直接使用父类的processEntity方法
        // 而是通过processSortedEntity处理
        throw new UnsupportedOperationException("SortedSystem should use processSortedEntity instead");
    }
}