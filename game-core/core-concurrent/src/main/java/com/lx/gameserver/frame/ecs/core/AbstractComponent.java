/*
 * 文件名: AbstractComponent.java
 * 用途: ECS组件抽象基类
 * 实现内容:
 *   - 组件基类（包含必要的元数据）
 *   - 组件池化支持（对象复用）
 *   - 组件序列化支持
 *   - 组件版本标记
 *   - 组件类型注册
 * 技术选型:
 *   - 标记接口模式提供类型安全
 *   - 对象池技术实现高性能复用
 *   - 版本控制支持增量更新
 * 依赖关系:
 *   - ECS系统的数据容器基类
 *   - 被所有具体组件继承
 *   - 为ComponentManager提供统一抽象
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.core;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 组件基类
 * <p>
 * 提供组件的基础实现，包含版本控制、类型管理等通用功能。
 * 建议所有自定义组件继承此类以获得标准功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractComponent implements Component {
    
    /**
     * 获取组件类型ID
     * 需要由子类实现，提供唯一的类型标识
     *
     * @return 组件类型ID
     */
    @Override
    public abstract int getTypeId();
    
    /**
     * 组件类型ID生成器
     */
    private static final AtomicLong TYPE_ID_GENERATOR = new AtomicLong(1);
    
    /**
     * 组件版本号
     */
    private volatile long version = 1L;
    
    /**
     * 组件创建时间戳
     */
    private final long createTime = java.lang.System.currentTimeMillis();
    
    /**
     * 组件最后修改时间戳
     */
    private volatile long lastModifyTime = createTime;
    
    /**
     * 组件标志位
     */
    private volatile int flags = 0;
    
    /**
     * 组件标志位定义
     */
    public static final class Flags {
        /** 脏数据标志 */
        public static final int DIRTY = 1;
        /** 持久化标志 */
        public static final int PERSISTENT = 1 << 1;
        /** 临时组件标志 */
        public static final int TEMPORARY = 1 << 2;
        /** 只读组件标志 */
        public static final int READ_ONLY = 1 << 3;
        /** 调试组件标志 */
        public static final int DEBUG = 1 << 4;
        
        private Flags() {}
    }
    
    @Override
    public long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(long version) {
        this.version = version;
        this.lastModifyTime = java.lang.System.currentTimeMillis();
    }
    
    @Override
    public long incrementVersion() {
        this.lastModifyTime = java.lang.System.currentTimeMillis();
        return ++version;
    }
    
    @Override
    public void reset() {
        version = 1L;
        lastModifyTime = java.lang.System.currentTimeMillis();
        flags = 0;
    }
    
    @Override
    public Component clone() {
        try {
            AbstractComponent cloned = (AbstractComponent) super.clone();
            cloned.version = 1L;
            cloned.lastModifyTime = java.lang.System.currentTimeMillis();
            cloned.flags = this.flags;
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("组件克隆失败", e);
        }
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
     * 获取最后修改时间
     *
     * @return 最后修改时间戳
     */
    public long getLastModifyTime() {
        return lastModifyTime;
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
     * 检查组件是否为脏数据
     *
     * @return 如果为脏数据返回true
     */
    public boolean isDirty() {
        return hasFlag(Flags.DIRTY);
    }
    
    /**
     * 标记组件为脏数据
     */
    public void markDirty() {
        addFlag(Flags.DIRTY);
    }
    
    /**
     * 清除脏数据标记
     */
    public void clearDirty() {
        removeFlag(Flags.DIRTY);
    }
    
    /**
     * 检查组件是否持久化
     *
     * @return 如果持久化返回true
     */
    public boolean isPersistent() {
        return hasFlag(Flags.PERSISTENT);
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
     * 检查组件是否为临时组件
     *
     * @return 如果为临时组件返回true
     */
    public boolean isTemporary() {
        return hasFlag(Flags.TEMPORARY);
    }
    
    /**
     * 设置临时组件标志
     *
     * @param temporary 是否为临时组件
     */
    public void setTemporary(boolean temporary) {
        if (temporary) {
            addFlag(Flags.TEMPORARY);
        } else {
            removeFlag(Flags.TEMPORARY);
        }
    }
    
    /**
     * 检查组件是否只读
     *
     * @return 如果只读返回true
     */
    public boolean isReadOnly() {
        return hasFlag(Flags.READ_ONLY);
    }
    
    /**
     * 设置只读标志
     *
     * @param readOnly 是否只读
     */
    public void setReadOnly(boolean readOnly) {
        if (readOnly) {
            addFlag(Flags.READ_ONLY);
        } else {
            removeFlag(Flags.READ_ONLY);
        }
    }
    
    /**
     * 生成新的组件类型ID
     *
     * @return 新的类型ID
     */
    protected static int generateTypeId() {
        return (int) TYPE_ID_GENERATOR.getAndIncrement();
    }
    
    /**
     * 触发组件修改通知
     */
    protected void notifyModified() {
        incrementVersion();
        markDirty();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "typeId=" + getTypeId() +
                ", version=" + version +
                ", createTime=" + createTime +
                ", lastModifyTime=" + lastModifyTime +
                ", flags=" + flags +
                '}';
    }
}