/*
 * 文件名: Entity.java
 * 用途: ECS核心实体类实现
 * 实现内容:
 *   - 轻量级实体设计（仅包含ID和元数据）
 *   - 高性能ID生成策略
 *   - 实体版本控制（用于并发修改检测）
 *   - 实体标签系统
 *   - 实体原型支持
 *   - 实体生命周期管理
 * 技术选型:
 *   - 无锁ID生成器提供高性能
 *   - 位掩码技术实现高效标签系统
 *   - 版本号机制支持并发安全
 * 依赖关系:
 *   - ECS系统的核心数据结构
 *   - 被World、ComponentManager等依赖
 *   - 提供实体的唯一标识和元数据管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.core;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECS实体类
 * <p>
 * 实体是ECS架构中的核心概念，代表游戏世界中的一个唯一对象。
 * 实体本身不包含数据和行为，仅作为组件的容器和唯一标识。
 * 采用轻量级设计，确保高性能的实体管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public final class Entity {
    
    /**
     * 实体ID生成器
     */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    
    /**
     * 无效实体ID
     */
    public static final long INVALID_ID = 0L;
    
    /**
     * 实体ID（唯一标识）
     */
    @EqualsAndHashCode.Include
    private final long id;
    
    /**
     * 实体版本号（用于并发修改检测）
     */
    private volatile long version;
    
    /**
     * 实体标签位掩码（用于快速分组和查询）
     */
    private volatile long tags;
    
    /**
     * 实体原型ID（用于批量创建优化）
     */
    private volatile long archetypeId;
    
    /**
     * 实体状态标志位
     */
    private volatile int flags;
    
    /**
     * 实体状态枚举
     */
    public enum State {
        /** 活跃状态 */
        ACTIVE(0),
        /** 已销毁状态 */
        DESTROYED(1),
        /** 待删除状态 */
        PENDING_REMOVAL(2);
        
        private final int value;
        
        State(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 实体标志位
     */
    public static final class Flags {
        /** 活跃标志 */
        public static final int ACTIVE = 1;
        /** 已销毁标志 */
        public static final int DESTROYED = 1 << 1;
        /** 待删除标志 */
        public static final int PENDING_REMOVAL = 1 << 2;
        /** 持久化标志 */
        public static final int PERSISTENT = 1 << 3;
        /** 调试标志 */
        public static final int DEBUG = 1 << 4;
        
        private Flags() {}
    }
    
    /**
     * 预定义标签
     */
    public static final class Tags {
        /** 玩家标签 */
        public static final long PLAYER = 1L;
        /** NPC标签 */
        public static final long NPC = 1L << 1;
        /** 道具标签 */
        public static final long ITEM = 1L << 2;
        /** 建筑标签 */
        public static final long BUILDING = 1L << 3;
        /** 技能标签 */
        public static final long SKILL = 1L << 4;
        /** Buff标签 */
        public static final long BUFF = 1L << 5;
        /** 临时对象标签 */
        public static final long TEMPORARY = 1L << 6;
        /** 静态对象标签 */
        public static final long STATIC = 1L << 7;
        
        private Tags() {}
    }
    
    /**
     * 私有构造函数
     */
    private Entity(long id) {
        this.id = id;
        this.version = 1L;
        this.tags = 0L;
        this.archetypeId = 0L;
        this.flags = Flags.ACTIVE;
    }
    
    /**
     * 创建新实体
     *
     * @return 新实体实例
     */
    public static Entity create() {
        return new Entity(ID_GENERATOR.getAndIncrement());
    }
    
    /**
     * 创建带指定ID的实体（用于反序列化）
     *
     * @param id 实体ID
     * @return 实体实例
     */
    public static Entity createWithId(long id) {
        if (id <= INVALID_ID) {
            throw new IllegalArgumentException("实体ID必须大于0");
        }
        
        // 确保ID生成器不会产生重复ID
        long currentMax = ID_GENERATOR.get();
        if (id >= currentMax) {
            ID_GENERATOR.compareAndSet(currentMax, id + 1);
        }
        
        return new Entity(id);
    }
    
    /**
     * 获取实体ID
     *
     * @return 实体ID
     */
    public long getId() {
        return id;
    }
    
    /**
     * 获取实体版本号
     *
     * @return 版本号
     */
    public long getVersion() {
        return version;
    }
    
    /**
     * 增加版本号（用于标记修改）
     *
     * @return 新版本号
     */
    public long incrementVersion() {
        return ++version;
    }
    
    /**
     * 设置版本号（用于反序列化）
     *
     * @param version 版本号
     */
    public void setVersion(long version) {
        this.version = version;
    }
    
    /**
     * 获取标签
     *
     * @return 标签位掩码
     */
    public long getTags() {
        return tags;
    }
    
    /**
     * 设置标签
     *
     * @param tags 标签位掩码
     */
    public void setTags(long tags) {
        this.tags = tags;
        incrementVersion();
    }
    
    /**
     * 添加标签
     *
     * @param tag 标签位掩码
     */
    public void addTag(long tag) {
        this.tags |= tag;
        incrementVersion();
    }
    
    /**
     * 移除标签
     *
     * @param tag 标签位掩码
     */
    public void removeTag(long tag) {
        this.tags &= ~tag;
        incrementVersion();
    }
    
    /**
     * 检查是否包含标签
     *
     * @param tag 标签位掩码
     * @return 如果包含返回true
     */
    public boolean hasTag(long tag) {
        return (tags & tag) != 0;
    }
    
    /**
     * 检查是否包含所有标签
     *
     * @param tags 标签位掩码
     * @return 如果包含所有标签返回true
     */
    public boolean hasAllTags(long tags) {
        return (this.tags & tags) == tags;
    }
    
    /**
     * 检查是否包含任意标签
     *
     * @param tags 标签位掩码
     * @return 如果包含任意标签返回true
     */
    public boolean hasAnyTag(long tags) {
        return (this.tags & tags) != 0;
    }
    
    /**
     * 获取原型ID
     *
     * @return 原型ID
     */
    public long getArchetypeId() {
        return archetypeId;
    }
    
    /**
     * 设置原型ID
     *
     * @param archetypeId 原型ID
     */
    public void setArchetypeId(long archetypeId) {
        this.archetypeId = archetypeId;
        incrementVersion();
    }
    
    /**
     * 获取标志位
     *
     * @return 标志位
     */
    public int getFlags() {
        return flags;
    }
    
    /**
     * 设置标志位
     *
     * @param flags 标志位
     */
    public void setFlags(int flags) {
        this.flags = flags;
        incrementVersion();
    }
    
    /**
     * 添加标志位
     *
     * @param flag 标志位
     */
    public void addFlag(int flag) {
        this.flags |= flag;
        incrementVersion();
    }
    
    /**
     * 移除标志位
     *
     * @param flag 标志位
     */
    public void removeFlag(int flag) {
        this.flags &= ~flag;
        incrementVersion();
    }
    
    /**
     * 检查是否包含标志位
     *
     * @param flag 标志位
     * @return 如果包含返回true
     */
    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }
    
    /**
     * 检查实体是否活跃
     *
     * @return 如果活跃返回true
     */
    public boolean isActive() {
        return hasFlag(Flags.ACTIVE) && !hasFlag(Flags.DESTROYED);
    }
    
    /**
     * 检查实体是否已销毁
     *
     * @return 如果已销毁返回true
     */
    public boolean isDestroyed() {
        return hasFlag(Flags.DESTROYED);
    }
    
    /**
     * 检查实体是否待删除
     *
     * @return 如果待删除返回true
     */
    public boolean isPendingRemoval() {
        return hasFlag(Flags.PENDING_REMOVAL);
    }
    
    /**
     * 检查实体是否持久化
     *
     * @return 如果持久化返回true
     */
    public boolean isPersistent() {
        return hasFlag(Flags.PERSISTENT);
    }
    
    /**
     * 标记实体为活跃状态
     */
    public void activate() {
        addFlag(Flags.ACTIVE);
        removeFlag(Flags.DESTROYED | Flags.PENDING_REMOVAL);
    }
    
    /**
     * 标记实体为销毁状态
     */
    public void destroy() {
        addFlag(Flags.DESTROYED);
        removeFlag(Flags.ACTIVE);
    }
    
    /**
     * 标记实体为待删除状态
     */
    public void markForRemoval() {
        addFlag(Flags.PENDING_REMOVAL);
    }
    
    /**
     * 设置持久化标志
     *
     * @param persistent 是否持久化
     */
    public void setPersistent(boolean persistent) {
        if (persistent) {
            addFlag(Flags.PERSISTENT);
        } else {
            removeFlag(Flags.PERSISTENT);
        }
    }
    
    /**
     * 获取当前ID生成器的值
     *
     * @return 当前ID生成器的值
     */
    public static long getCurrentIdGeneratorValue() {
        return ID_GENERATOR.get();
    }
    
    /**
     * 重置ID生成器（仅用于测试）
     */
    public static void resetIdGenerator() {
        ID_GENERATOR.set(1);
    }
    
    /**
     * 检查实体ID是否有效
     *
     * @param id 实体ID
     * @return 如果有效返回true
     */
    public static boolean isValidId(long id) {
        return id > INVALID_ID;
    }
}