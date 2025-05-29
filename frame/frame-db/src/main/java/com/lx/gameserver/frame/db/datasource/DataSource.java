/*
 * 文件名: DataSource.java
 * 用途: 数据源路由注解
 * 实现内容:
 *   - 标记方法使用指定的数据源
 *   - 支持主库/从库强制路由
 *   - 支持按名称指定具体数据源
 *   - 配合AOP切面实现动态数据源切换
 * 技术选型:
 *   - Java注解机制
 *   - Spring AOP拦截
 *   - ThreadLocal数据源绑定
 * 依赖关系:
 *   - 被DataSourceAspect拦截处理
 *   - 配合DynamicDataSource使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.datasource;

import java.lang.annotation.*;

/**
 * 数据源路由注解
 * <p>
 * 用于标记方法需要使用指定的数据源。支持强制路由到主库或从库，
 * 也可以按名称指定具体的数据源实例。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataSource {

    /**
     * 数据源类型
     *
     * @return 数据源类型
     */
    DataSourceType value() default DataSourceType.AUTO;

    /**
     * 指定数据源名称
     * <p>
     * 当type为NAMED时，指定具体的数据源Bean名称。
     * </p>
     *
     * @return 数据源名称
     */
    String name() default "";

    /**
     * 是否强制使用指定数据源
     * <p>
     * 为true时，即使在事务中也使用指定的数据源；
     * 为false时，在事务中会优先使用主库保证数据一致性。
     * </p>
     *
     * @return 是否强制使用
     */
    boolean force() default false;

    /**
     * 数据源类型枚举
     */
    enum DataSourceType {
        /**
         * 自动选择：根据方法名和事务状态自动判断
         */
        AUTO,

        /**
         * 强制使用主库
         */
        MASTER,

        /**
         * 强制使用从库（负载均衡）
         */
        SLAVE,

        /**
         * 使用指定名称的数据源
         */
        NAMED
    }
}