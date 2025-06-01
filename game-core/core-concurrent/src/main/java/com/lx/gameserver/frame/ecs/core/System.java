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