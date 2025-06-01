/*
 * 文件名: EventListener.java
 * 用途: 事件监听注解
 * 实现内容:
 *   - 简化事件处理器注册
 *   - 支持注解配置事件监听
 *   - 提供监听器属性设置
 * 技术选型:
 *   - 注解驱动设计
 *   - Spring AOP集成
 *   - 反射机制
 * 依赖关系:
 *   - 被业务类使用
 *   - 需要配套注解处理器
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event;

import com.lx.gameserver.frame.event.core.GameEvent;

import java.lang.annotation.*;

/**
 * 事件监听注解
 * <p>
 * 用于标记事件监听方法，简化事件处理器的注册过程。
 * 支持配置监听的事件类型、是否异步处理、处理顺序等属性。
 * 通过Spring AOP机制自动注册事件处理器。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
    
    /**
     * 监听的事件类型
     * <p>
     * 指定该方法要监听的事件类型，支持多个事件类型。
     * 事件类型必须继承自GameEvent。
     * </p>
     *
     * @return 事件类型数组
     */
    Class<? extends GameEvent>[] value();
    
    /**
     * 是否异步处理
     * <p>
     * 指定该监听器是否支持异步处理。
     * 异步处理可以提高性能，但可能影响事件处理的顺序。
     * </p>
     *
     * @return 如果支持异步处理返回true，默认为true
     */
    boolean async() default true;
    
    /**
     * 处理顺序
     * <p>
     * 当同一个事件有多个监听器时，按此值排序执行。
     * 数值越小，执行顺序越靠前。
     * </p>
     *
     * @return 处理顺序值，默认为0
     */
    int order() default 0;
    
    /**
     * 监听器名称
     * <p>
     * 自定义监听器名称，用于日志记录和监控。
     * 如果不指定，将使用方法名作为监听器名称。
     * </p>
     *
     * @return 监听器名称，默认为空字符串
     */
    String name() default "";
    
    /**
     * 事件优先级过滤
     * <p>
     * 指定只监听特定优先级的事件。
     * 如果为空，则监听所有优先级的事件。
     * </p>
     *
     * @return 事件优先级数组，默认为空（监听所有优先级）
     */
    String[] priorities() default {};
    
    /**
     * 是否启用
     * <p>
     * 指定该监听器是否启用。
     * 可以通过此属性动态控制监听器的开关。
     * </p>
     *
     * @return 如果启用返回true，默认为true
     */
    boolean enabled() default true;
    
    /**
     * 条件表达式
     * <p>
     * 使用SpEL表达式定义监听条件。
     * 只有当表达式结果为true时，才会处理事件。
     * 表达式中可以使用事件对象的属性。
     * </p>
     *
     * @return SpEL条件表达式，默认为空（无条件）
     */
    String condition() default "";
    
    /**
     * 错误处理策略
     * <p>
     * 指定处理器执行异常时的处理策略。
     * </p>
     *
     * @return 错误处理策略，默认为记录日志
     */
    ErrorHandlingStrategy errorHandling() default ErrorHandlingStrategy.LOG;
    
    /**
     * 最大重试次数
     * <p>
     * 当处理器执行失败时的最大重试次数。
     * 设置为0表示不重试。
     * </p>
     *
     * @return 最大重试次数，默认为0
     */
    int maxRetries() default 0;
    
    /**
     * 重试延迟（毫秒）
     * <p>
     * 重试之间的延迟时间，单位为毫秒。
     * 只有当maxRetries大于0时才有效。
     * </p>
     *
     * @return 重试延迟时间，默认为1000毫秒
     */
    long retryDelay() default 1000L;
    
    /**
     * 错误处理策略枚举
     */
    enum ErrorHandlingStrategy {
        /** 记录日志并继续 */
        LOG("记录日志"),
        
        /** 忽略错误 */
        IGNORE("忽略错误"),
        
        /** 重新抛出异常 */
        RETHROW("重新抛出"),
        
        /** 发送错误事件 */
        SEND_ERROR_EVENT("发送错误事件");
        
        private final String description;
        
        ErrorHandlingStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}