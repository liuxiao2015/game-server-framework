/*
 * 文件名: CachePut.java
 * 用途: 缓存更新注解
 * 实现内容:
 *   - 方法级缓存更新注解定义
 *   - 支持强制更新和条件更新
 *   - 返回值缓存和异常处理
 *   - 更新策略和过期时间配置
 *   - 异步更新功能
 * 技术选型:
 *   - Spring AOP注解
 *   - SpEL表达式支持
 *   - 自定义更新策略
 *   - 运行时注解处理
 * 依赖关系:
 *   - 被CacheAspect使用
 *   - 提供声明式缓存更新
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
 * 缓存更新注解
 * <p>
 * 用于方法级缓存更新，当方法被调用时，无论缓存中是否存在对应的值，
 * 都会执行方法并将结果更新到缓存中。这与@Cacheable的区别在于，
 * @CachePut总是会执行方法。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CachePut {

    /**
     * 缓存名称
     * <p>
     * 指定要更新的缓存名称。如果不指定，将使用默认的缓存名称。
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
     * 更新条件
     * <p>
     * 用于决定是否进行缓存更新的SpEL表达式。只有当表达式求值为true时才会更新缓存。
     * 可以使用方法参数、返回值等进行条件判断。
     * </p>
     * <p>
     * 示例：
     * - "#id > 0" - 只有当id参数大于0时才更新
     * - "#result != null" - 只有当返回值不为null时才更新
     * - "#user != null && #user.active" - 只有当用户存在且活跃时才更新
     * </p>
     *
     * @return 更新条件表达式
     */
    String condition() default "";

    /**
     * 排除条件
     * <p>
     * 用于决定是否排除缓存更新的SpEL表达式。当表达式求值为true时不会更新缓存。
     * 与condition的区别在于，unless可以使用方法的返回值。
     * </p>
     * <p>
     * 示例：
     * - "#result == null" - 当返回值为null时不更新
     * - "#result.size() == 0" - 当返回集合为空时不更新
     * - "#result.error != null" - 当返回结果包含错误时不更新
     * </p>
     *
     * @return 排除条件表达式
     */
    String unless() default "";

    /**
     * 缓存值表达式
     * <p>
     * 用于指定要缓存的值的SpEL表达式。默认情况下，会缓存方法的返回值。
     * 通过此属性可以缓存返回值的某个部分或进行转换。
     * </p>
     * <p>
     * 示例：
     * - "#result.data" - 只缓存返回结果的data字段
     * - "#result.toDTO()" - 缓存转换后的DTO对象
     * - "T(com.example.Utils).transform(#result)" - 使用工具类转换后缓存
     * </p>
     *
     * @return 缓存值表达式
     */
    String cacheValue() default "";

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
     * 更新策略
     * <p>
     * 指定缓存更新策略：
     * - REPLACE: 替换现有值（默认）
     * - MERGE: 合并现有值（需要自定义合并逻辑）
     * - UPDATE_IF_NEWER: 只有当新值更新时才更新
     * </p>
     *
     * @return 更新策略
     */
    UpdateStrategy updateStrategy() default UpdateStrategy.REPLACE;

    /**
     * 缓存级别
     * <p>
     * 指定缓存级别，用于多级缓存场景。
     * - L1: 只更新本地缓存
     * - L2: 只更新分布式缓存
     * - ALL: 更新所有级别（默认）
     * </p>
     *
     * @return 缓存级别
     */
    CacheLevel level() default CacheLevel.ALL;

    /**
     * 是否异步更新
     * <p>
     * 当设置为true时，缓存更新操作将异步执行，不会阻塞方法的执行。
     * 这对于提高性能很有帮助，但需要注意缓存一致性问题。
     * </p>
     *
     * @return 是否异步更新
     */
    boolean async() default false;

    /**
     * 异常处理策略
     * <p>
     * 指定当缓存更新操作发生异常时的处理策略。
     * - IGNORE: 忽略异常，继续执行方法
     * - LOG: 记录异常日志，继续执行方法
     * - THROW: 抛出异常，中断方法执行
     * </p>
     *
     * @return 异常处理策略
     */
    ExceptionHandling exceptionHandling() default ExceptionHandling.LOG;

    /**
     * 是否允许null值
     * <p>
     * 当设置为true时，允许缓存null值。当设置为false时，如果返回值为null，
     * 不会进行缓存操作。
     * </p>
     *
     * @return 是否允许null值
     */
    boolean allowNullValue() default true;

    /**
     * 描述信息
     * <p>
     * 对缓存更新配置的描述信息，主要用于文档和调试目的。
     * </p>
     *
     * @return 描述信息
     */
    String description() default "";

    /**
     * 更新策略枚举
     */
    enum UpdateStrategy {
        /**
         * 替换现有值
         */
        REPLACE("替换现有值"),

        /**
         * 合并现有值
         */
        MERGE("合并现有值"),

        /**
         * 只有当新值更新时才更新
         */
        UPDATE_IF_NEWER("只有当新值更新时才更新");

        private final String description;

        UpdateStrategy(String description) {
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