/*
 * 文件名: GroupSystem.java
 * 用途: 分组处理系统基类
 * 实现内容:
 *   - 分组处理系统基类
 *   - 实体自动分组功能
 *   - 分组规则定义和管理
 *   - 分组并行处理支持
 * 技术选型:
 *   - 函数式接口支持自定义分组规则
 *   - 并行流处理提升性能
 *   - 动态分组适应运行时变化
 * 依赖关系:
 *   - 继承IteratingSystem
 *   - 为需要分组处理的系统提供基础
 *   - 支持各种分组策略
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.core.Component;
import com.lx.gameserver.frame.ecs.core.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分组处理系统
 * <p>
 * 将符合条件的实体按照指定规则分组，然后按组进行处理。
 * 支持动态分组、并行处理等功能。
 * </p>
 *
 * @param <K> 分组键类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class GroupSystem<K> extends IteratingSystem {
    
    /**
     * 分组键提取器
     */
    private final Function<Entity, K> groupKeyExtractor;
    
    /**
     * 分组结果缓存
     */
    private final Map<K, List<Entity>> groupCache;
    
    /**
     * 是否启用并行处理
     */
    private boolean enableParallelProcessing = false;
    
    /**
     * 并行处理阈值
     */
    private int parallelThreshold = 100;
    
    /**
     * 分组缓存版本
     */
    private long groupCacheVersion = 0;
    
    /**
     * 最大分组数量
     */
    private int maxGroups = 1000;
    
    /**
     * 分组处理顺序比较器
     */
    private Comparator<K> groupOrderComparator;
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param groupKeyExtractor 分组键提取器
     * @param requiredComponents 必须包含的组件
     */
    @SafeVarargs
    protected GroupSystem(String name, int priority, Function<Entity, K> groupKeyExtractor,
                         Class<? extends Component>... requiredComponents) {
        super(name, priority, requiredComponents);
        this.groupKeyExtractor = Objects.requireNonNull(groupKeyExtractor, "Group key extractor cannot be null");
        this.groupCache = new ConcurrentHashMap<>();
    }
    
    @Override
    protected void onSystemInitialize() {
        super.onSystemInitialize();
        // 初始化分组相关配置
        onGroupSystemInitialize();
    }
    
    @Override
    protected void onUpdate(float deltaTime) {
        // 获取符合条件的实体
        Collection<Entity> entities = getEntityQuery().execute();
        
        if (entities.isEmpty()) {
            return;
        }
        
        // 分组实体
        Map<K, List<Entity>> groups = groupEntities(entities);
        
        if (groups.isEmpty()) {
            return;
        }
        
        // 处理分组
        processGroups(groups, deltaTime);
    }
    
    /**
     * 获取实体查询（临时实现）
     */
    private Object getEntityQuery() {
        return new Object() {
            public Collection<Entity> execute() {
                return new ArrayList<>();
            }
        };
    }
    
    /**
     * 对实体进行分组
     *
     * @param entities 实体集合
     * @return 分组结果
     */
    private Map<K, List<Entity>> groupEntities(Collection<Entity> entities) {
        long startTime = java.lang.System.nanoTime();
        
        Map<K, List<Entity>> groups;
        
        if (enableParallelProcessing && entities.size() >= parallelThreshold) {
            // 并行分组
            groups = entities.parallelStream()
                    .filter(this::shouldProcessEntity)
                    .collect(Collectors.groupingBy(
                            groupKeyExtractor,
                            Collectors.toList()
                    ));
        } else {
            // 串行分组
            groups = entities.stream()
                    .filter(this::shouldProcessEntity)
                    .collect(Collectors.groupingBy(
                            groupKeyExtractor,
                            Collectors.toList()
                    ));
        }
        
        // 检查分组数量限制
        if (groups.size() > maxGroups) {
            // 如果分组过多，可以选择合并或限制
            groups = limitGroups(groups);
        }
        
        long endTime = java.lang.System.nanoTime();
        long groupTime = (endTime - startTime) / 1_000_000; // 转换为毫秒
        
        // 更新缓存
        updateGroupCache(groups);
        
        // 记录分组统计信息
        onGroupingCompleted(entities.size(), groups.size(), groupTime);
        
        return groups;
    }
    
    /**
     * 限制分组数量
     *
     * @param groups 原始分组
     * @return 限制后的分组
     */
    private Map<K, List<Entity>> limitGroups(Map<K, List<Entity>> groups) {
        // 按组大小排序，保留最大的几个组
        return groups.entrySet().stream()
                .sorted(Map.Entry.<K, List<Entity>>comparingByValue(
                        (list1, list2) -> Integer.compare(list2.size(), list1.size())
                ))
                .limit(maxGroups)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
    
    /**
     * 更新分组缓存
     *
     * @param groups 分组结果
     */
    private void updateGroupCache(Map<K, List<Entity>> groups) {
        groupCache.clear();
        groupCache.putAll(groups);
        groupCacheVersion++;
    }
    
    /**
     * 处理所有分组
     *
     * @param groups 分组结果
     * @param deltaTime 时间增量
     */
    protected void processGroups(Map<K, List<Entity>> groups, float deltaTime) {
        // 获取分组处理顺序
        List<K> groupKeys = getSortedGroupKeys(groups.keySet());
        
        if (enableParallelProcessing && groups.size() >= parallelThreshold / 10) {
            // 并行处理分组
            groupKeys.parallelStream().forEach(groupKey -> {
                List<Entity> groupEntities = groups.get(groupKey);
                if (groupEntities != null && !groupEntities.isEmpty()) {
                    processGroup(groupKey, groupEntities, deltaTime);
                }
            });
        } else {
            // 串行处理分组
            for (K groupKey : groupKeys) {
                List<Entity> groupEntities = groups.get(groupKey);
                if (groupEntities != null && !groupEntities.isEmpty()) {
                    processGroup(groupKey, groupEntities, deltaTime);
                }
            }
        }
    }
    
    /**
     * 获取排序后的分组键列表
     *
     * @param groupKeys 分组键集合
     * @return 排序后的分组键列表
     */
    private List<K> getSortedGroupKeys(Set<K> groupKeys) {
        List<K> keys = new ArrayList<>(groupKeys);
        
        if (groupOrderComparator != null) {
            keys.sort(groupOrderComparator);
        } else {
            // 默认排序（如果键类型支持比较）
            try {
                keys.sort(null);
            } catch (ClassCastException e) {
                // 如果不支持比较，保持原顺序
            }
        }
        
        return keys;
    }
    
    /**
     * 处理单个分组
     *
     * @param groupKey 分组键
     * @param groupEntities 分组中的实体列表
     * @param deltaTime 时间增量
     */
    protected abstract void processGroup(K groupKey, List<Entity> groupEntities, float deltaTime);
    
    /**
     * 处理分组中的单个实体
     *
     * @param groupKey 分组键
     * @param entity 实体
     * @param entityIndex 实体在分组中的索引
     * @param deltaTime 时间增量
     */
    protected void processEntityInGroup(K groupKey, Entity entity, int entityIndex, float deltaTime) {
        // 默认调用原来的processEntity方法
        processEntity(entity, deltaTime);
    }
    
    /**
     * 分组系统初始化回调
     */
    protected void onGroupSystemInitialize() {
        // 默认空实现
    }
    
    /**
     * 分组完成回调
     *
     * @param entityCount 实体总数
     * @param groupCount 分组数量
     * @param groupTime 分组耗时（毫秒）
     */
    protected void onGroupingCompleted(int entityCount, int groupCount, long groupTime) {
        // 默认空实现，子类可重写以记录统计信息
    }
    
    /**
     * 获取当前分组数量
     *
     * @return 分组数量
     */
    public int getCurrentGroupCount() {
        return groupCache.size();
    }
    
    /**
     * 获取指定分组的实体数量
     *
     * @param groupKey 分组键
     * @return 实体数量
     */
    public int getGroupEntityCount(K groupKey) {
        List<Entity> entities = groupCache.get(groupKey);
        return entities != null ? entities.size() : 0;
    }
    
    /**
     * 获取所有分组键
     *
     * @return 分组键集合
     */
    public Set<K> getGroupKeys() {
        return new HashSet<>(groupCache.keySet());
    }
    
    /**
     * 清理分组缓存
     */
    public void clearGroupCache() {
        groupCache.clear();
        groupCacheVersion = 0;
    }
    
    /**
     * 设置是否启用并行处理
     *
     * @param enableParallelProcessing 是否启用并行处理
     */
    public void setEnableParallelProcessing(boolean enableParallelProcessing) {
        this.enableParallelProcessing = enableParallelProcessing;
    }
    
    /**
     * 设置并行处理阈值
     *
     * @param parallelThreshold 并行处理阈值
     */
    public void setParallelThreshold(int parallelThreshold) {
        this.parallelThreshold = Math.max(1, parallelThreshold);
    }
    
    /**
     * 设置最大分组数量
     *
     * @param maxGroups 最大分组数量
     */
    public void setMaxGroups(int maxGroups) {
        this.maxGroups = Math.max(1, maxGroups);
    }
    
    /**
     * 设置分组处理顺序比较器
     *
     * @param groupOrderComparator 分组处理顺序比较器
     */
    public void setGroupOrderComparator(Comparator<K> groupOrderComparator) {
        this.groupOrderComparator = groupOrderComparator;
    }
    
    /**
     * 获取分组键提取器
     *
     * @return 分组键提取器
     */
    public Function<Entity, K> getGroupKeyExtractor() {
        return groupKeyExtractor;
    }
    
    /**
     * 检查是否启用并行处理
     *
     * @return 如果启用并行处理返回true
     */
    public boolean isParallelProcessingEnabled() {
        return enableParallelProcessing;
    }
    
    /**
     * 获取并行处理阈值
     *
     * @return 并行处理阈值
     */
    public int getParallelThreshold() {
        return parallelThreshold;
    }
    
    /**
     * 获取最大分组数量
     *
     * @return 最大分组数量
     */
    public int getMaxGroups() {
        return maxGroups;
    }
    
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // GroupSystem不直接使用父类的processEntity方法
        // 而是通过processGroup和processEntityInGroup处理
        // 这里提供一个默认实现以防直接调用
        K groupKey = groupKeyExtractor.apply(entity);
        processEntityInGroup(groupKey, entity, 0, deltaTime);
    }
}