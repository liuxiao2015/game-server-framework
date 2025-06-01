/*
 * 文件名: Cacheable.java
 * 用途: 缓存注解
 * 实现内容:
 *   - 方法级缓存注解定义
 *   - 支持条件缓存和键生成
 *   - 过期时间和缓存名称配置
 *   - 自定义键生成器支持
 *   - 异常处理配置
 * 技术选型:
 *   - Spring AOP注解
 *   - SpEL表达式支持
 *   - 自定义属性配置
 *   - 运行时注解处理
 * 依赖关系:
 *   - 被CacheAspect使用
 *   - 提供声明式缓存功能
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
 * 缓存注解
 * <p>
 * 用于方法级缓存，当方法被调用时，首先检查缓存中是否存在对应的值。
 * 如果存在则直接返回缓存值，否则执行方法并将结果存入缓存。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /**
     * 缓存名称
     * <p>
     * 指定要使用的缓存名称。如果不指定，将使用默认的缓存名称。
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
     * 用于生成缓存键的SpEL表达式。如果不指定，将使用默认的键生成策略。
     * 可以使用方法参数、返回值等作为键的组成部分。
     * </p>
     * <p>
     * 示例：
     * - "#id" - 使用名为id的参数作为键
     * - "#user.id" - 使用user参数的id属性作为键
     * - "'user:' + #id" - 使用字符串前缀和参数组合作为键
     * - "#root.methodName + ':' + #id" - 使用方法名和参数组合作为键
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
     * 缓存条件
     * <p>
     * 用于决定是否进行缓存的SpEL表达式。只有当表达式求值为true时才会进行缓存。
     * 可以使用方法参数、返回值等进行条件判断。
     * </p>
     * <p>
     * 示例：
     * - "#id > 0" - 只有当id参数大于0时才缓存
     * - "#user != null" - 只有当user参数不为null时才缓存
     * - "#result.size() > 0" - 只有当返回结果不为空时才缓存
     * </p>
     *
     * @return 缓存条件表达式
     */
    String condition() default "";

    /**
     * 排除条件
     * <p>
     * 用于决定是否排除缓存的SpEL表达式。当表达式求值为true时不会进行缓存。
     * 与condition的区别在于，unless可以使用方法的返回值。
     * </p>
     * <p>
     * 示例：
     * - "#result == null" - 当返回值为null时不缓存
     * - "#result.size() == 0" - 当返回集合为空时不缓存
     * - "#result.error != null" - 当返回结果包含错误时不缓存
     * </p>
     *
     * @return 排除条件表达式
     */
    String unless() default "";

    /**
     * 是否同步执行
     * <p>
     * 当设置为true时，多个线程同时请求相同键的缓存时，只有一个线程会执行方法，
     * 其他线程会等待该线程的执行结果。这可以防止缓存击穿问题。
     * </p>
     *
     * @return 是否同步执行
     */
    boolean sync() default false;

    /**
     * 过期时间（秒）
     * <p>
     * 指定缓存项的过期时间，单位为秒。如果设置为-1，表示永不过期。
     * 如果设置为0，使用缓存的默认过期时间。
     * </p>
     *
     * @return 过期时间（秒）
     */
    long expireTime() default 0;

    /**
     * 过期时间表达式
     * <p>
     * 用于动态计算过期时间的SpEL表达式，表达式的结果应该是一个表示秒数的数字。
     * 如果同时指定了expireTime和expireTimeExpression，后者优先。
     * </p>
     * <p>
     * 示例：
     * - "300" - 固定300秒过期
     * - "#user.vipLevel * 3600" - 根据用户VIP等级动态计算过期时间
     * - "T(java.time.Duration).ofHours(1).toSeconds()" - 使用Duration类计算
     * </p>
     *
     * @return 过期时间表达式
     */
    String expireTimeExpression() default "";

    /**
     * 缓存级别
     * <p>
     * 指定缓存级别，用于多级缓存场景。
     * - L1: 只缓存到本地缓存
     * - L2: 只缓存到分布式缓存
     * - ALL: 缓存到所有级别（默认）
     * </p>
     *
     * @return 缓存级别
     */
    CacheLevel level() default CacheLevel.ALL;

    /**
     * 是否异步缓存
     * <p>
     * 当设置为true时，缓存操作将异步执行，不会阻塞方法的执行。
     * 这对于提高性能很有帮助，但需要注意缓存一致性问题。
     * </p>
     *
     * @return 是否异步缓存
     */
    boolean async() default false;

    /**
     * 异常处理策略
     * <p>
     * 指定当缓存操作发生异常时的处理策略。
     * - IGNORE: 忽略异常，继续执行方法
     * - LOG: 记录异常日志，继续执行方法
     * - THROW: 抛出异常，中断方法执行
     * </p>
     *
     * @return 异常处理策略
     */
    ExceptionHandling exceptionHandling() default ExceptionHandling.LOG;

    /**
     * 描述信息
     * <p>
     * 对缓存配置的描述信息，主要用于文档和调试目的。
     * </p>
     *
     * @return 描述信息
     */
    String description() default "";

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