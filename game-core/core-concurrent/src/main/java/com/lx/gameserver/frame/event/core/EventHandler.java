/*
 * 文件名: EventHandler.java
 * 用途: 事件处理器接口
 * 实现内容:
 *   - 定义事件处理的标准接口
 *   - 支持泛型事件类型
 *   - 提供函数式接口支持
 * 技术选型:
 *   - 泛型接口设计
 *   - 函数式接口标记
 * 依赖关系:
 *   - 被EventBus用于事件处理器注册
 *   - 被业务模块实现具体处理逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.core;

/**
 * 事件处理器接口
 * <p>
 * 定义事件处理的标准接口，所有事件处理器必须实现此接口。
 * 支持泛型，可以处理特定类型的事件。
 * 使用函数式接口标记，支持Lambda表达式。
 * </p>
 *
 * @param <T> 事件类型，必须继承自GameEvent
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@FunctionalInterface
public interface EventHandler<T extends GameEvent> {
    
    /**
     * 处理事件
     * <p>
     * 事件处理的核心方法，具体的业务逻辑在此实现。
     * 实现时应当注意异常处理，避免影响其他事件处理器。
     * </p>
     *
     * @param event 要处理的事件，不能为null
     * @throws Exception 处理异常，会被事件总线捕获并记录
     */
    void handle(T event) throws Exception;
    
    /**
     * 获取处理器名称
     * <p>
     * 默认实现返回类名，用于日志记录和监控。
     * 子类可以重写此方法提供更有意义的名称。
     * </p>
     *
     * @return 处理器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 判断是否支持异步处理
     * <p>
     * 默认支持异步处理，子类可以重写此方法。
     * 如果返回false，事件将在事件总线线程中同步处理。
     * </p>
     *
     * @return 如果支持异步处理返回true，否则返回false
     */
    default boolean supportAsync() {
        return true;
    }
    
    /**
     * 获取处理器执行顺序
     * <p>
     * 当同一个事件有多个处理器时，按此值排序执行。
     * 数值越小，执行顺序越靠前。默认为0。
     * </p>
     *
     * @return 执行顺序值
     */
    default int getOrder() {
        return 0;
    }
}