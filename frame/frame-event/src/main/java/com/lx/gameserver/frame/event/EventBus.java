/*
 * 文件名: EventBus.java
 * 用途: 事件总线接口
 * 实现内容:
 *   - 定义事件总线的标准操作
 *   - 事件发布、订阅、注销功能
 *   - 支持同步和异步事件处理
 * 技术选型:
 *   - 接口抽象设计
 *   - 泛型支持
 *   - 观察者模式
 * 依赖关系:
 *   - 被DisruptorEventBus实现
 *   - 被EventBusManager使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event;

import com.lx.gameserver.frame.event.core.GameEvent;
import com.lx.gameserver.frame.event.core.EventHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 事件总线接口
 * <p>
 * 定义事件总线的标准操作，支持事件的发布、订阅和管理。
 * 支持多种事件总线实现的切换，提供统一的API接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface EventBus {
    
    /**
     * 发布单个事件
     * <p>
     * 异步发布事件到事件总线，不阻塞调用线程。
     * 事件将被分发给所有注册的处理器。
     * </p>
     *
     * @param event 要发布的事件，不能为null
     * @return 发布是否成功
     */
    boolean publish(GameEvent event);
    
    /**
     * 同步发布事件
     * <p>
     * 同步发布事件，等待所有处理器处理完成后返回。
     * 适用于需要确保事件处理完成的场景。
     * </p>
     *
     * @param event 要发布的事件，不能为null
     * @return 异步结果Future
     */
    CompletableFuture<Void> publishSync(GameEvent event);
    
    /**
     * 批量发布事件
     * <p>
     * 批量发布多个事件，提高吞吐量。
     * 适用于需要发布大量事件的场景。
     * </p>
     *
     * @param events 事件列表，不能为null或空
     * @return 发布成功的事件数量
     */
    int publishBatch(List<GameEvent> events);
    
    /**
     * 注册事件处理器
     * <p>
     * 注册处理器来处理指定类型的事件。
     * 同一个事件类型可以注册多个处理器。
     * </p>
     *
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 注册是否成功
     */
    <T extends GameEvent> boolean register(Class<T> eventClass, EventHandler<T> handler);
    
    /**
     * 注册事件处理器（带名称）
     * <p>
     * 注册带名称的处理器，便于管理和监控。
     * </p>
     *
     * @param eventClass  事件类型
     * @param handler     处理器
     * @param handlerName 处理器名称
     * @param <T>         事件类型参数
     * @return 注册是否成功
     */
    <T extends GameEvent> boolean register(Class<T> eventClass, EventHandler<T> handler, String handlerName);
    
    /**
     * 注销事件处理器
     * <p>
     * 注销指定的事件处理器，停止接收事件。
     * </p>
     *
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 注销是否成功
     */
    <T extends GameEvent> boolean unregister(Class<T> eventClass, EventHandler<T> handler);
    
    /**
     * 注销所有处理器
     * <p>
     * 注销指定事件类型的所有处理器。
     * </p>
     *
     * @param eventClass 事件类型
     * @param <T>        事件类型参数
     * @return 注销的处理器数量
     */
    <T extends GameEvent> int unregisterAll(Class<T> eventClass);
    
    /**
     * 获取注册的处理器数量
     *
     * @param eventClass 事件类型
     * @param <T>        事件类型参数
     * @return 处理器数量
     */
    <T extends GameEvent> int getHandlerCount(Class<T> eventClass);
    
    /**
     * 判断是否正在运行
     *
     * @return 如果正在运行返回true
     */
    boolean isRunning();
    
    /**
     * 获取事件总线名称
     *
     * @return 事件总线名称
     */
    String getName();
    
    /**
     * 启动事件总线
     *
     * @return 启动是否成功
     */
    boolean start();
    
    /**
     * 关闭事件总线
     * <p>
     * 优雅关闭事件总线，等待正在处理的事件完成。
     * </p>
     */
    void shutdown();
    
    /**
     * 强制关闭事件总线
     * <p>
     * 立即关闭事件总线，不等待正在处理的事件。
     * </p>
     */
    void shutdownNow();
}