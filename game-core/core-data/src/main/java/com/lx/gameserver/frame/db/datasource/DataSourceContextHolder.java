/*
 * 文件名: DataSourceContextHolder.java
 * 用途: 数据源上下文持有者
 * 实现内容:
 *   - 使用ThreadLocal存储当前线程的数据源类型
 *   - 提供设置、获取、清除数据源的方法
 *   - 支持数据源切换的嵌套调用
 *   - 提供强制路由到主库的功能
 * 技术选型:
 *   - ThreadLocal线程本地存储
 *   - 枚举定义数据源类型
 *   - 栈结构支持嵌套调用
 * 依赖关系:
 *   - 被DynamicDataSource使用
 *   - 被DataSourceAspect使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 数据源上下文持有者
 * <p>
 * 使用ThreadLocal管理当前线程的数据源选择，支持动态切换数据源。
 * 提供栈结构支持嵌套的数据源切换场景。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public final class DataSourceContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceContextHolder.class);

    /**
     * 数据源类型枚举
     */
    public enum DataSourceType {
        /**
         * 主库（写库）
         */
        MASTER,
        
        /**
         * 从库（读库）
         */
        SLAVE
    }

    /**
     * 存储当前线程的数据源类型栈
     * 使用栈结构支持嵌套的数据源切换
     */
    private static final ThreadLocal<Deque<DataSourceType>> CONTEXT_HOLDER = 
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 强制使用主库标识
     * 在事务中或需要强一致性的场景下使用
     */
    private static final ThreadLocal<Boolean> FORCE_MASTER = 
            ThreadLocal.withInitial(() -> false);

    /**
     * 设置数据源类型
     * <p>
     * 将指定的数据源类型压入栈顶，成为当前活跃的数据源。
     * </p>
     *
     * @param dataSourceType 数据源类型
     */
    public static void setDataSourceType(DataSourceType dataSourceType) {
        if (dataSourceType == null) {
            logger.warn("尝试设置null数据源类型，将被忽略");
            return;
        }

        Deque<DataSourceType> stack = CONTEXT_HOLDER.get();
        stack.push(dataSourceType);
        
        logger.debug("设置数据源类型: {}, 栈深度: {}", dataSourceType, stack.size());
    }

    /**
     * 获取当前数据源类型
     * <p>
     * 如果设置了强制使用主库，则直接返回MASTER。
     * 否则返回栈顶的数据源类型，如果栈为空则返回MASTER作为默认值。
     * </p>
     *
     * @return 当前数据源类型
     */
    public static DataSourceType getDataSourceType() {
        // 如果强制使用主库，直接返回主库
        if (Boolean.TRUE.equals(FORCE_MASTER.get())) {
            return DataSourceType.MASTER;
        }

        Deque<DataSourceType> stack = CONTEXT_HOLDER.get();
        DataSourceType dataSourceType = stack.isEmpty() ? DataSourceType.MASTER : stack.peek();
        
        logger.debug("获取数据源类型: {}, 栈深度: {}", dataSourceType, stack.size());
        return dataSourceType;
    }

    /**
     * 移除当前数据源类型
     * <p>
     * 从栈顶移除一个数据源类型，恢复到上一层的数据源设置。
     * 用于嵌套数据源切换场景的清理工作。
     * </p>
     */
    public static void removeDataSourceType() {
        Deque<DataSourceType> stack = CONTEXT_HOLDER.get();
        if (!stack.isEmpty()) {
            DataSourceType removed = stack.pop();
            logger.debug("移除数据源类型: {}, 剩余栈深度: {}", removed, stack.size());
        } else {
            logger.debug("数据源类型栈为空，无需移除");
        }
    }

    /**
     * 清除当前线程的所有数据源上下文
     * <p>
     * 清空栈中的所有数据源类型，重置强制主库标识。
     * 通常在请求结束或线程销毁时调用。
     * </p>
     */
    public static void clear() {
        Deque<DataSourceType> stack = CONTEXT_HOLDER.get();
        int stackSize = stack.size();
        stack.clear();
        FORCE_MASTER.remove();
        CONTEXT_HOLDER.remove();
        
        logger.debug("清除数据源上下文，清除了{}个数据源类型", stackSize);
    }

    /**
     * 设置强制使用主库
     * <p>
     * 在事务中或需要强一致性读写的场景下，
     * 强制所有数据库操作都路由到主库。
     * </p>
     */
    public static void forceMaster() {
        FORCE_MASTER.set(true);
        logger.debug("设置强制使用主库");
    }

    /**
     * 取消强制使用主库
     * <p>
     * 恢复正常的读写分离路由策略。
     * </p>
     */
    public static void cancelForceMaster() {
        FORCE_MASTER.set(false);
        logger.debug("取消强制使用主库");
    }

    /**
     * 检查是否强制使用主库
     *
     * @return 如果强制使用主库返回true，否则返回false
     */
    public static boolean isForceMaster() {
        return Boolean.TRUE.equals(FORCE_MASTER.get());
    }

    /**
     * 获取当前栈的深度
     * <p>
     * 用于调试和监控嵌套层次。
     * </p>
     *
     * @return 栈的深度
     */
    public static int getStackDepth() {
        return CONTEXT_HOLDER.get().size();
    }

    /**
     * 私有构造函数，防止实例化
     */
    private DataSourceContextHolder() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }
}