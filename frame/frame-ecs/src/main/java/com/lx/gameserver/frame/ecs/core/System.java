/*
 * 文件名: System.java
 * 用途: ECS系统基类定义
 * 实现内容:
 *   - 系统基类定义
 *   - 系统优先级设置
 *   - 系统依赖管理
 *   - 查询条件定义（Query）
 *   - 批处理支持
 *   - 多线程安全
 * 技术选型:
 *   - 抽象基类模式提供统一接口
 *   - 优先级调度支持系统执行顺序
 *   - 依赖注入机制管理系统关系
 * 依赖关系:
 *   - ECS系统的逻辑处理单元
 *   - 被SystemManager管理和调度
 *   - 通过World访问实体和组件
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.core;

import com.lx.gameserver.frame.ecs.query.EntityQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECS系统接口
 * <p>
 * 系统是ECS架构中的逻辑处理单元，负责处理具有特定组件组合的实体。
 * 系统包含游戏逻辑，但不直接持有数据，通过查询获取需要处理的实体。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface System {
    
    /**
     * 获取系统ID
     *
     * @return 系统ID
     */
    long getId();
    
    /**
     * 获取系统名称
     *
     * @return 系统名称
     */
    String getName();
    
    /**
     * 获取系统优先级
     *
     * @return 优先级（数值越小优先级越高）
     */
    int getPriority();
    
    /**
     * 获取系统依赖列表
     *
     * @return 依赖的系统类列表
     */
    Set<Class<? extends System>> getDependencies();
    
    /**
     * 检查系统是否启用
     *
     * @return 如果启用返回true
     */
    boolean isEnabled();
    
    /**
     * 设置系统启用状态
     *
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);
    
    /**
     * 初始化系统
     *
     * @param world ECS世界
     */
    void initialize(World world);
    
    /**
     * 更新系统
     *
     * @param deltaTime 时间增量（秒）
     */
    void update(float deltaTime);
    
    /**
     * 销毁系统
     */
    void destroy();
    
    /**
     * 检查系统是否需要更新
     *
     * @param deltaTime 时间增量
     * @return 如果需要更新返回true
     */
    default boolean shouldUpdate(float deltaTime) {
        return isEnabled();
    }
    
    /**
     * 获取系统统计信息
     *
     * @return 统计信息
     */
    SystemStatistics getStatistics();
}

/**
 * 系统基类
 * <p>
 * 提供系统的基础实现，包含优先级管理、依赖管理、统计功能等。
 * 建议所有自定义系统继承此类以获得标准功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
abstract class AbstractSystem implements System {
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractSystem.class);
    
    /**
     * 系统ID生成器
     */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    
    /**
     * 系统ID
     */
    @EqualsAndHashCode.Include
    private final long id;
    
    /**
     * 系统名称
     */
    private final String name;
    
    /**
     * 系统优先级
     */
    private final int priority;
    
    /**
     * 系统依赖
     */
    private final Set<Class<? extends System>> dependencies;
    
    /**
     * 是否启用
     */
    private volatile boolean enabled = true;
    
    /**
     * ECS世界引用
     */
    protected World world;
    
    /**
     * 系统统计信息
     */
    private final SystemStatistics statistics;
    
    /**
     * 系统状态
     */
    private volatile SystemState state = SystemState.CREATED;
    
    /**
     * 系统状态枚举
     */
    public enum SystemState {
        CREATED,
        INITIALIZED,
        RUNNING,
        PAUSED,
        DESTROYED
    }
    
    /**
     * 系统优先级常量
     */
    public static final class Priority {
        /** 最高优先级 */
        public static final int HIGHEST = 0;
        /** 高优先级 */
        public static final int HIGH = 100;
        /** 正常优先级 */
        public static final int NORMAL = 500;
        /** 低优先级 */
        public static final int LOW = 800;
        /** 最低优先级 */
        public static final int LOWEST = 1000;
        
        private Priority() {}
    }
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     */
    protected AbstractSystem(String name, int priority) {
        this(name, priority, Collections.emptySet());
    }
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param dependencies 系统依赖
     */
    protected AbstractSystem(String name, int priority, Set<Class<? extends System>> dependencies) {
        this.id = ID_GENERATOR.getAndIncrement();
        this.name = Objects.requireNonNull(name, "系统名称不能为null");
        this.priority = priority;
        this.dependencies = new HashSet<>(dependencies);
        this.statistics = new SystemStatistics();
    }
    
    @Override
    public long getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public Set<Class<? extends System>> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }
    
    @Override
    public boolean isEnabled() {
        return enabled && state == SystemState.RUNNING;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.debug("系统 {} 启用状态设置为: {}", name, enabled);
    }
    
    @Override
    public void initialize(World world) {
        if (state != SystemState.CREATED) {
            throw new IllegalStateException("系统只能在CREATED状态下初始化");
        }
        
        this.world = Objects.requireNonNull(world, "World不能为null");
        
        try {
            onInitialize();
            state = SystemState.INITIALIZED;
            state = SystemState.RUNNING;
            logger.debug("系统 {} 初始化完成", name);
        } catch (Exception e) {
            logger.error("系统 {} 初始化失败", name, e);
            throw new RuntimeException("系统初始化失败", e);
        }
    }
    
    @Override
    public void update(float deltaTime) {
        if (!shouldUpdate(deltaTime)) {
            return;
        }
        
        long startTime = java.lang.System.nanoTime();
        
        try {
            statistics.incrementUpdateCount();
            onUpdate(deltaTime);
        } catch (Exception e) {
            statistics.incrementErrorCount();
            logger.error("系统 {} 更新时发生错误", name, e);
            throw e;
        } finally {
            long endTime = java.lang.System.nanoTime();
            statistics.addUpdateTime(endTime - startTime);
        }
    }
    
    @Override
    public void destroy() {
        if (state == SystemState.DESTROYED) {
            return;
        }
        
        try {
            onDestroy();
            state = SystemState.DESTROYED;
            logger.debug("系统 {} 销毁完成", name);
        } catch (Exception e) {
            logger.error("系统 {} 销毁时发生错误", name, e);
        }
    }
    
    @Override
    public SystemStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 获取系统状态
     *
     * @return 系统状态
     */
    public SystemState getState() {
        return state;
    }
    
    /**
     * 暂停系统
     */
    public void pause() {
        if (state == SystemState.RUNNING) {
            state = SystemState.PAUSED;
            logger.debug("系统 {} 已暂停", name);
        }
    }
    
    /**
     * 恢复系统
     */
    public void resume() {
        if (state == SystemState.PAUSED) {
            state = SystemState.RUNNING;
            logger.debug("系统 {} 已恢复", name);
        }
    }
    
    /**
     * 添加依赖系统
     *
     * @param systemClass 依赖的系统类
     */
    protected void addDependency(Class<? extends System> systemClass) {
        dependencies.add(systemClass);
    }
    
    /**
     * 创建实体查询
     *
     * @return 实体查询构建器
     */
    protected EntityQuery.Builder createQuery() {
        return world.createQuery();
    }
    
    /**
     * 子类实现：系统初始化
     */
    protected abstract void onInitialize();
    
    /**
     * 子类实现：系统更新
     *
     * @param deltaTime 时间增量
     */
    protected abstract void onUpdate(float deltaTime);
    
    /**
     * 子类实现：系统销毁
     */
    protected void onDestroy() {
        // 默认空实现
    }
    
    @Override
    public String toString() {
        return "System{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", priority=" + priority +
                ", enabled=" + enabled +
                ", state=" + state +
                '}';
    }
}

/**
 * 系统统计信息
 * <p>
 * 记录系统的运行统计数据，用于性能监控和调试。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
class SystemStatistics {
    
    /**
     * 更新次数
     */
    private volatile long updateCount = 0;
    
    /**
     * 总更新时间（纳秒）
     */
    private volatile long totalUpdateTime = 0;
    
    /**
     * 最小更新时间（纳秒）
     */
    private volatile long minUpdateTime = Long.MAX_VALUE;
    
    /**
     * 最大更新时间（纳秒）
     */
    private volatile long maxUpdateTime = 0;
    
    /**
     * 错误次数
     */
    private volatile long errorCount = 0;
    
    /**
     * 最后更新时间戳
     */
    private volatile long lastUpdateTime = 0;
    
    /**
     * 增加更新次数
     */
    public void incrementUpdateCount() {
        updateCount++;
        lastUpdateTime = java.lang.System.currentTimeMillis();
    }
    
    /**
     * 添加更新时间
     *
     * @param updateTime 更新时间（纳秒）
     */
    public void addUpdateTime(long updateTime) {
        totalUpdateTime += updateTime;
        minUpdateTime = Math.min(minUpdateTime, updateTime);
        maxUpdateTime = Math.max(maxUpdateTime, updateTime);
    }
    
    /**
     * 增加错误次数
     */
    public void incrementErrorCount() {
        errorCount++;
    }
    
    /**
     * 获取平均更新时间（纳秒）
     *
     * @return 平均更新时间
     */
    public double getAverageUpdateTime() {
        return updateCount > 0 ? (double) totalUpdateTime / updateCount : 0.0;
    }
    
    /**
     * 获取平均更新时间（毫秒）
     *
     * @return 平均更新时间
     */
    public double getAverageUpdateTimeMs() {
        return getAverageUpdateTime() / 1_000_000.0;
    }
    
    /**
     * 获取最小更新时间（毫秒）
     *
     * @return 最小更新时间
     */
    public double getMinUpdateTimeMs() {
        return minUpdateTime == Long.MAX_VALUE ? 0.0 : minUpdateTime / 1_000_000.0;
    }
    
    /**
     * 获取最大更新时间（毫秒）
     *
     * @return 最大更新时间
     */
    public double getMaxUpdateTimeMs() {
        return maxUpdateTime / 1_000_000.0;
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        updateCount = 0;
        totalUpdateTime = 0;
        minUpdateTime = Long.MAX_VALUE;
        maxUpdateTime = 0;
        errorCount = 0;
        lastUpdateTime = 0;
    }
}