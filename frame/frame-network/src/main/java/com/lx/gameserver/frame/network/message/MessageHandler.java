/*
 * 文件名: MessageHandler.java
 * 用途: 消息处理器接口
 * 实现内容:
 *   - 定义消息处理的标准接口
 *   - 支持前置处理、业务处理、后置处理
 *   - 异常处理机制
 *   - 处理器链模式支持
 *   - 泛型支持确保类型安全
 * 技术选型:
 *   - 函数式接口设计
 *   - 泛型约束确保类型安全
 *   - 默认方法提供可选实现
 *   - 链式处理模式
 * 依赖关系:
 *   - 被MessageDispatcher使用
 *   - 与Connection接口协作
 *   - 处理Message及其子类
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.message;

import com.lx.gameserver.frame.network.core.Connection;

/**
 * 消息处理器接口
 * <p>
 * 定义消息处理的标准接口，支持泛型约束确保类型安全。
 * 提供前置处理、业务处理、后置处理的完整生命周期。
 * </p>
 *
 * @param <T> 处理的消息类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@FunctionalInterface
public interface MessageHandler<T> {

    /**
     * 处理消息
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @throws Exception 处理失败时抛出异常
     */
    void handle(T message, Connection connection) throws Exception;

    /**
     * 前置处理
     * <p>
     * 在业务处理之前执行，可用于参数验证、权限检查等。
     * </p>
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @return true表示继续处理，false表示中断处理
     * @throws Exception 处理失败时抛出异常
     */
    default boolean preHandle(T message, Connection connection) throws Exception {
        return true;
    }

    /**
     * 后置处理
     * <p>
     * 在业务处理之后执行，可用于清理资源、记录日志等。
     * </p>
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @throws Exception 处理失败时抛出异常
     */
    default void postHandle(T message, Connection connection) throws Exception {
        // 默认空实现
    }

    /**
     * 异常处理
     * <p>
     * 当处理过程中发生异常时调用。
     * </p>
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @param cause      异常原因
     * @throws Exception 处理失败时抛出异常
     */
    default void handleException(T message, Connection connection, Throwable cause) throws Exception {
        throw new Exception("消息处理失败", cause);
    }

    /**
     * 检查是否支持处理指定消息
     *
     * @param message 消息对象
     * @return true表示支持处理
     */
    default boolean supports(Object message) {
        return true;
    }

    /**
     * 获取处理器优先级
     * <p>
     * 数值越小优先级越高，用于处理器排序。
     * </p>
     *
     * @return 优先级，默认为0
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}

/**
 * 消息异常处理器接口
 */
@FunctionalInterface
interface MessageExceptionHandler {
    
    /**
     * 处理消息处理过程中的异常
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @param cause      异常原因
     */
    void handleException(Object message, Connection connection, Throwable cause);
}

/**
 * 消息拦截器接口
 */
interface MessageInterceptor {
    
    /**
     * 前置拦截
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @return true表示继续处理，false表示中断处理
     * @throws Exception 处理失败时抛出异常
     */
    default boolean preHandle(Object message, Connection connection) throws Exception {
        return true;
    }

    /**
     * 后置拦截
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @throws Exception 处理失败时抛出异常
     */
    default void postHandle(Object message, Connection connection) throws Exception {
        // 默认空实现
    }

    /**
     * 完成后拦截（无论成功还是失败都会调用）
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @param cause      异常原因，如果处理成功则为null
     * @throws Exception 处理失败时抛出异常
     */
    default void afterCompletion(Object message, Connection connection, Throwable cause) throws Exception {
        // 默认空实现
    }

    /**
     * 获取拦截器优先级
     *
     * @return 优先级，数值越小优先级越高
     */
    default int getOrder() {
        return 0;
    }
}