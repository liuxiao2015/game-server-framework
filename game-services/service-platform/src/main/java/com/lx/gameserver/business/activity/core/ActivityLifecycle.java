/*
 * 文件名: ActivityLifecycle.java
 * 用途: 活动生命周期接口
 * 实现内容:
 *   - 定义活动生命周期的标准方法
 *   - 包含初始化、开始、更新、结束等阶段
 *   - 支持重置和销毁操作
 *   - 提供生命周期事件钩子
 * 技术选型:
 *   - Java接口定义
 *   - 支持默认方法实现
 *   - 异常处理机制
 * 依赖关系:
 *   - 被Activity基类实现
 *   - 被活动管理器调用
 *   - 被活动调度器使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.core;

/**
 * 活动生命周期接口
 * <p>
 * 定义活动从创建到销毁的完整生命周期管理方法。
 * 所有活动实现类都应该实现此接口，以确保统一的生命周期管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface ActivityLifecycle {
    
    /**
     * 初始化方法
     * <p>
     * 在活动创建后立即调用，用于初始化活动的基本配置和数据。
     * 此方法只会被调用一次。
     * </p>
     *
     * @param context 活动上下文
     * @throws Exception 初始化异常
     */
    void initialize(ActivityContext context) throws Exception;
    
    /**
     * 开始方法
     * <p>
     * 在活动开始时调用，用于执行活动开始的逻辑。
     * 可能会被多次调用（如活动重启）。
     * </p>
     *
     * @param context 活动上下文
     * @throws Exception 开始异常
     */
    void start(ActivityContext context) throws Exception;
    
    /**
     * 更新方法
     * <p>
     * 在活动运行期间定期调用，用于更新活动状态和处理业务逻辑。
     * 调用频率由活动调度器决定。
     * </p>
     *
     * @param context 活动上下文
     * @param deltaTime 距离上次更新的时间间隔（毫秒）
     * @throws Exception 更新异常
     */
    void update(ActivityContext context, long deltaTime) throws Exception;
    
    /**
     * 结束方法
     * <p>
     * 在活动结束时调用，用于执行活动结束的清理和收尾工作。
     * 可能会被多次调用（如活动重启）。
     * </p>
     *
     * @param context 活动上下文
     * @param reason  结束原因
     * @throws Exception 结束异常
     */
    void end(ActivityContext context, String reason) throws Exception;
    
    /**
     * 重置方法
     * <p>
     * 重置活动状态到初始状态，通常用于周期性活动的重置。
     * 默认实现为空，子类可以覆盖此方法。
     * </p>
     *
     * @param context 活动上下文
     * @throws Exception 重置异常
     */
    default void reset(ActivityContext context) throws Exception {
        // 默认实现为空，子类可以覆盖
    }
    
    /**
     * 销毁方法
     * <p>
     * 在活动彻底销毁前调用，用于释放资源和清理数据。
     * 此方法只会被调用一次，调用后活动对象将不再可用。
     * </p>
     *
     * @param context 活动上下文
     * @throws Exception 销毁异常
     */
    default void destroy(ActivityContext context) throws Exception {
        // 默认实现为空，子类可以覆盖
    }
    
    /**
     * 暂停方法
     * <p>
     * 暂停活动执行，保持活动状态但停止更新。
     * 默认实现为空，子类可以覆盖此方法。
     * </p>
     *
     * @param context 活动上下文
     * @throws Exception 暂停异常
     */
    default void pause(ActivityContext context) throws Exception {
        // 默认实现为空，子类可以覆盖
    }
    
    /**
     * 恢复方法
     * <p>
     * 从暂停状态恢复活动执行。
     * 默认实现为空，子类可以覆盖此方法。
     * </p>
     *
     * @param context 活动上下文
     * @throws Exception 恢复异常
     */
    default void resume(ActivityContext context) throws Exception {
        // 默认实现为空，子类可以覆盖
    }
    
    /**
     * 错误处理方法
     * <p>
     * 当活动执行过程中发生错误时调用。
     * 默认实现为空，子类可以覆盖此方法进行自定义错误处理。
     * </p>
     *
     * @param context 活动上下文
     * @param error   错误信息
     * @throws Exception 错误处理异常
     */
    default void onError(ActivityContext context, Throwable error) throws Exception {
        // 默认实现为空，子类可以覆盖
    }
    
    /**
     * 获取生命周期状态
     * <p>
     * 返回当前活动的生命周期状态。
     * 默认实现返回UNKNOWN，子类应该覆盖此方法。
     * </p>
     *
     * @return 生命周期状态
     */
    default LifecycleState getLifecycleState() {
        return LifecycleState.UNKNOWN;
    }
    
    /**
     * 生命周期状态枚举
     */
    enum LifecycleState {
        /** 未知状态 */
        UNKNOWN("unknown", "未知状态"),
        
        /** 已创建但未初始化 */
        CREATED("created", "已创建"),
        
        /** 已初始化但未开始 */
        INITIALIZED("initialized", "已初始化"),
        
        /** 运行中 */
        RUNNING("running", "运行中"),
        
        /** 已暂停 */
        PAUSED("paused", "已暂停"),
        
        /** 已结束 */
        ENDED("ended", "已结束"),
        
        /** 已销毁 */
        DESTROYED("destroyed", "已销毁"),
        
        /** 错误状态 */
        ERROR("error", "错误状态");
        
        /** 状态代码 */
        private final String code;
        
        /** 状态描述 */
        private final String description;
        
        /**
         * 构造函数
         *
         * @param code        状态代码
         * @param description 状态描述
         */
        LifecycleState(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        /**
         * 获取状态代码
         *
         * @return 状态代码
         */
        public String getCode() {
            return code;
        }
        
        /**
         * 获取状态描述
         *
         * @return 状态描述
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * 检查是否为活跃状态
         *
         * @return 是否为活跃状态
         */
        public boolean isActive() {
            return this == RUNNING;
        }
        
        /**
         * 检查是否为终止状态
         *
         * @return 是否为终止状态
         */
        public boolean isTerminated() {
            return this == ENDED || this == DESTROYED || this == ERROR;
        }
    }
}