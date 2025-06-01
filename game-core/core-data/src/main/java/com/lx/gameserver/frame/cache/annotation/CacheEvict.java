/*
 * 文件名: CacheEvict.java
 * 用途: 缓存清除注解
 * 实现内容:
 *   - 方法级缓存清除注解定义
 *   - 支持单个清除和批量清除
 *   - 条件清除和清除时机配置
 *   - 异步清除和异常处理
 *   - 全部清除功能
 * 技术选型:
 *   - Spring AOP注解
 *   - SpEL表达式支持
 *   - 自定义清除策略
 *   - 运行时注解处理
 * 依赖关系:
 *   - 被CacheAspect使用
 *   - 提供声明式缓存清除
 *   - 支持Spring Cache抽象
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存清除注解
 * <p>
 * 用于方法级缓存清除，当方法被调用时，会根据配置清除相应的缓存项。
 * 支持单个清除、批量清除、条件清除等多种清除策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvict {

    /**
     * 缓存名称
     * <p>
     * 指定要清除的缓存名称。如果不指定，将使用默认的缓存名称。
     * 支持SpEL表达式。
     * </p>
     *
     * @return 缓存名称数组
     */
    String[] value() default {};

    /**
     * 缓存名称（别名）
     * <p>
     * 与value属性功能相同，提供更清晰的语义。
     * </p>
     *
     * @return 缓存名称数组
     */
    String[] cacheNames() default {};

    /**
     * 缓存键
     * <p>
     * 用于生成要清除的缓存键的SpEL表达式。如果不指定且allEntries为false，
     * 将使用默认的键生成策略。
     * </p>
     * <p>
     * 示例：
     * - "#id" - 清除指定id的缓存
     * - "#user.id" - 清除指定用户id的缓存
     * - "'user:' + #id" - 清除指定前缀和id组合的缓存
     * </p>
     *
     * @return 缓存键表达式
     */
    String key() default "";

    /**
     * 键生成器
     * <p>
     * 指定自定义的键生成器bean名称。与key属性互斥，如果同时指定，key属性优先。
     * </p>
     *
     * @return 键生成器bean名称
     */
    String keyGenerator() default "";

    /**
     * 缓存管理器
     * <p>
     * 指定要使用的缓存管理器bean名称。如果不指定，将使用默认的缓存管理器。
     * </p>
     *
     * @return 缓存管理器bean名称
     */
    String cacheManager() default "";

    /**
     * 缓存解析器
     * <p>
     * 指定自定义的缓存解析器bean名称。与cacheManager属性互斥。
     * </p>
     *
     * @return 缓存解析器bean名称
     */
    String cacheResolver() default "";

    /**
     * 清除条件
     * <p>
     * 用于决定是否进行缓存清除的SpEL表达式。只有当表达式求值为true时才会清除缓存。
     * 可以使用方法参数、返回值等进行条件判断。
     * </p>
     * <p>
     * 示例：
     * - "#id > 0" - 只有当id参数大于0时才清除
     * - "#result == true" - 只有当方法返回true时才清除
     * - "#user != null && #user.active" - 只有当用户存在且活跃时才清除
     * </p>
     *
     * @return 清除条件表达式
     */
    String condition() default "";

    /**
     * 是否清除所有条目
     * <p>
     * 当设置为true时，会清除指定缓存中的所有条目，而不是特定的键。
     * 此时key和keyGenerator属性将被忽略。
     * </p>
     *
     * @return 是否清除所有条目
     */
    boolean allEntries() default false;

    /**
     * 清除时机
     * <p>
     * 指定缓存清除的时机：
     * - BEFORE: 在方法执行前清除（默认）
     * - AFTER: 在方法执行后清除
     * - BOTH: 在方法执行前后都清除
     * </p>
     *
     * @return 清除时机
     */
    EvictTiming timing() default EvictTiming.AFTER;

    /**
     * 缓存级别
     * <p>
     * 指定要清除的缓存级别，用于多级缓存场景。
     * - L1: 只清除本地缓存
     * - L2: 只清除分布式缓存
     * - ALL: 清除所有级别（默认）
     * </p>
     *
     * @return 缓存级别
     */
    CacheLevel level() default CacheLevel.ALL;

    /**
     * 是否异步清除
     * <p>
     * 当设置为true时，缓存清除操作将异步执行，不会阻塞方法的执行。
     * 这对于提高性能很有帮助，特别是在清除大量缓存时。
     * </p>
     *
     * @return 是否异步清除
     */
    boolean async() default false;

    /**
     * 异常处理策略
     * <p>
     * 指定当缓存清除操作发生异常时的处理策略。
     * - IGNORE: 忽略异常，继续执行方法
     * - LOG: 记录异常日志，继续执行方法
     * - THROW: 抛出异常，中断方法执行
     * </p>
     *
     * @return 异常处理策略
     */
    ExceptionHandling exceptionHandling() default ExceptionHandling.LOG;

    /**
     * 批量清除键
     * <p>
     * 用于批量清除的键列表SpEL表达式。表达式的结果应该是一个键的集合。
     * 当指定此属性时，key属性将被忽略。
     * </p>
     * <p>
     * 示例：
     * - "#ids" - 清除ids参数中所有键对应的缓存
     * - "#user.friendIds" - 清除用户朋友ID列表对应的缓存
     * </p>
     *
     * @return 批量清除键表达式
     */
    String keys() default "";

    /**
     * 键前缀
     * <p>
     * 指定要清除的键前缀。所有以此前缀开头的缓存键都会被清除。
     * 支持SpEL表达式。
     * </p>
     * <p>
     * 示例：
     * - "user:" - 清除所有以"user:"开头的缓存
     * - "'dept:' + #deptId + ':'" - 清除指定部门的所有缓存
     * </p>
     *
     * @return 键前缀表达式
     */
    String keyPrefix() default "";

    /**
     * 描述信息
     * <p>
     * 对缓存清除配置的描述信息，主要用于文档和调试目的。
     * </p>
     *
     * @return 描述信息
     */
    String description() default "";

    /**
     * 清除时机枚举
     */
    enum EvictTiming {
        /**
         * 方法执行前清除
         */
        BEFORE("方法执行前清除"),

        /**
         * 方法执行后清除
         */
        AFTER("方法执行后清除"),

        /**
         * 方法执行前后都清除
         */
        BOTH("方法执行前后都清除");

        private final String description;

        EvictTiming(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 缓存级别枚举
     */
    enum CacheLevel {
        /**
         * 仅本地缓存
         */
        L1("仅本地缓存"),

        /**
         * 仅分布式缓存
         */
        L2("仅分布式缓存"),

        /**
         * 所有级别缓存
         */
        ALL("所有级别缓存");

        private final String description;

        CacheLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 异常处理策略枚举
     */
    enum ExceptionHandling {
        /**
         * 忽略异常
         */
        IGNORE("忽略异常"),

        /**
         * 记录异常日志
         */
        LOG("记录异常日志"),

        /**
         * 抛出异常
         */
        THROW("抛出异常");

        private final String description;

        ExceptionHandling(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}