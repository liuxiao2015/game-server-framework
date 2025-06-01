/*
 * 文件名: Component.java
 * 用途: ECS组件接口和基类定义
 * 实现内容:
 *   - 组件接口定义
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
 *   - ECS系统的数据容器接口
 *   - 被所有具体组件实现
 *   - 为ComponentManager提供统一抽象
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.core;

import java.io.Serializable;

/**
 * ECS组件接口
 * <p>
 * 组件是ECS架构中的数据容器，包含了实体的属性和状态数据。
 * 组件应该是纯数据对象，不包含业务逻辑。
 * 所有组件都应该实现此接口以获得ECS系统的支持。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface Component extends Serializable, Cloneable {
    
    /**
     * 获取组件类型ID
     *
     * @return 组件类型ID
     */
    int getTypeId();
    
    /**
     * 获取组件版本号
     *
     * @return 版本号
     */
    long getVersion();
    
    /**
     * 设置组件版本号
     *
     * @param version 版本号
     */
    void setVersion(long version);
    
    /**
     * 增加版本号
     *
     * @return 新版本号
     */
    long incrementVersion();
    
    /**
     * 重置组件状态（用于对象池复用）
     */
    void reset();
    
    /**
     * 克隆组件
     *
     * @return 组件副本
     */
    Component clone();
    
    /**
     * 验证组件数据有效性
     *
     * @return 如果数据有效返回true
     */
    default boolean isValid() {
        return true;
    }
    
    /**
     * 获取组件大小（用于内存统计）
     *
     * @return 组件大小（字节）
     */
    default int getSize() {
        return 0;
    }
}