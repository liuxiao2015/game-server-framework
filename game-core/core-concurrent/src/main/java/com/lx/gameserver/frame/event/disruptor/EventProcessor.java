/*
 * 文件名: EventProcessor.java
 * 用途: 事件处理器
 * 实现内容:
 *   - 从RingBuffer中消费事件并分发给对应处理器
 *   - 支持批量处理优化
 *   - 异常处理和性能监控
 * 技术选型:
 *   - Disruptor EventHandler实现
 *   - 异步处理模式
 *   - 异常隔离机制
 * 依赖关系:
 *   - 被DisruptorEventBus使用
 *   - 处理EventWrapper事件
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lx.gameserver.frame.event.core.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件处理器
 * <p>
 * 从Disruptor RingBuffer中消费事件，并分发给注册的处理器。
 * 支持批量处理优化和异常隔离，确保单个事件处理失败不影响整体。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class EventProcessor implements EventHandler<EventWrapper> {
    
    private static final Logger logger = LoggerFactory.getLogger(EventProcessor.class);
    
    /** 事件处理器注册表 */
    private final Map<Class<?>, List<com.lx.gameserver.frame.event.core.EventHandler<?>>> handlerMap;
    
    /** 事件处理统计 */
    private volatile long processedCount = 0;
    private volatile long errorCount = 0;
    private volatile long lastProcessTime = 0;
    
    /** 批量处理的事件缓存 */
    private final List<EventWrapper> batchBuffer = new ArrayList<>();
    private static final int DEFAULT_BATCH_SIZE = 100;
    
    /**
     * 构造函数
     */
    public EventProcessor() {
        this.handlerMap = new ConcurrentHashMap<>();
    }
    
    /**
     * 构造函数
     *
     * @param handlerMap 事件处理器注册表
     */
    public EventProcessor(Map<Class<?>, List<com.lx.gameserver.frame.event.core.EventHandler<?>>> handlerMap) {
        this.handlerMap = handlerMap != null ? handlerMap : new ConcurrentHashMap<>();
    }
    
    @Override
    public void onEvent(EventWrapper wrapper, long sequence, boolean endOfBatch) throws Exception {
        if (wrapper == null || !wrapper.isValid()) {
            logger.warn("接收到无效的事件包装器: {}", wrapper);
            return;
        }
        
        try {
            // 标记处理开始
            wrapper.markProcessStart();
            
            // 添加到批量处理缓存
            batchBuffer.add(wrapper);
            
            // 如果是批次结束或缓存已满，执行批量处理
            if (endOfBatch || batchBuffer.size() >= DEFAULT_BATCH_SIZE) {
                processBatch();
            }
            
            lastProcessTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            errorCount++;
            logger.error("处理事件时发生异常: eventId={}, eventType={}", 
                    wrapper.getEvent().getEventId(), wrapper.getEvent().getEventType(), e);
        }
    }
    
    /**
     * 批量处理事件
     */
    private void processBatch() {
        if (batchBuffer.isEmpty()) {
            return;
        }
        
        // 按事件类型分组
        Map<Class<?>, List<EventWrapper>> eventGroups = new HashMap<>();
        for (EventWrapper wrapper : batchBuffer) {
            GameEvent event = wrapper.getEvent();
            Class<?> eventClass = event.getClass();
            
            eventGroups.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(wrapper);
        }
        
        // 分别处理每种事件类型
        for (Map.Entry<Class<?>, List<EventWrapper>> entry : eventGroups.entrySet()) {
            Class<?> eventClass = entry.getKey();
            List<EventWrapper> wrappers = entry.getValue();
            
            processEventGroup(eventClass, wrappers);
        }
        
        // 清空缓存
        batchBuffer.clear();
        processedCount += batchBuffer.size();
    }
    
    /**
     * 处理同类型事件组
     *
     * @param eventClass 事件类型
     * @param wrappers   事件包装器列表
     */
    @SuppressWarnings("unchecked")
    private void processEventGroup(Class<?> eventClass, List<EventWrapper> wrappers) {
        // 获取注册的处理器
        List<com.lx.gameserver.frame.event.core.EventHandler<?>> handlers = findHandlers(eventClass);
        if (handlers.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("事件类型 {} 没有注册处理器", eventClass.getSimpleName());
            }
            return;
        }
        
        // 按优先级排序处理器
        handlers.sort(Comparator.comparingInt(com.lx.gameserver.frame.event.core.EventHandler::getOrder));
        
        // 依次调用处理器
        for (com.lx.gameserver.frame.event.core.EventHandler handler : handlers) {
            for (EventWrapper wrapper : wrappers) {
                processEvent(handler, wrapper.getEvent());
                wrapper.markProcessed();
            }
        }
    }
    
    /**
     * 查找事件处理器
     *
     * @param eventClass 事件类型
     * @return 处理器列表
     */
    private List<com.lx.gameserver.frame.event.core.EventHandler<?>> findHandlers(Class<?> eventClass) {
        List<com.lx.gameserver.frame.event.core.EventHandler<?>> handlers = new ArrayList<>();
        
        // 精确匹配
        List<com.lx.gameserver.frame.event.core.EventHandler<?>> exactHandlers = handlerMap.get(eventClass);
        if (exactHandlers != null) {
            handlers.addAll(exactHandlers);
        }
        
        // 父类匹配
        Class<?> superClass = eventClass.getSuperclass();
        while (superClass != null && !superClass.equals(Object.class)) {
            List<com.lx.gameserver.frame.event.core.EventHandler<?>> superHandlers = handlerMap.get(superClass);
            if (superHandlers != null) {
                handlers.addAll(superHandlers);
            }
            superClass = superClass.getSuperclass();
        }
        
        return handlers;
    }
    
    /**
     * 处理单个事件
     *
     * @param handler 事件处理器
     * @param event   游戏事件
     */
    @SuppressWarnings("unchecked")
    private void processEvent(com.lx.gameserver.frame.event.core.EventHandler handler, GameEvent event) {
        try {
            long startTime = System.currentTimeMillis();
            handler.handle(event);
            
            if (logger.isDebugEnabled()) {
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("事件处理完成: eventType={}, handler={}, duration={}ms", 
                        event.getEventType(), handler.getName(), duration);
            }
            
        } catch (Exception e) {
            errorCount++;
            logger.error("处理器执行失败: eventType={}, handler={}, eventId={}", 
                    event.getEventType(), handler.getName(), event.getEventId(), e);
        }
    }
    
    /**
     * 注册事件处理器
     *
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     */
    public <T extends GameEvent> void register(Class<T> eventClass, 
                                              com.lx.gameserver.frame.event.core.EventHandler<T> handler) {
        handlerMap.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(handler);
        
        if (logger.isDebugEnabled()) {
            logger.debug("注册事件处理器: eventType={}, handler={}", 
                    eventClass.getSimpleName(), handler.getName());
        }
    }
    
    /**
     * 注销事件处理器
     *
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 是否注销成功
     */
    public <T extends GameEvent> boolean unregister(Class<T> eventClass, 
                                                   com.lx.gameserver.frame.event.core.EventHandler<T> handler) {
        List<com.lx.gameserver.frame.event.core.EventHandler<?>> handlers = handlerMap.get(eventClass);
        if (handlers != null) {
            boolean removed = handlers.remove(handler);
            if (removed && logger.isDebugEnabled()) {
                logger.debug("注销事件处理器: eventType={}, handler={}", 
                        eventClass.getSimpleName(), handler.getName());
            }
            return removed;
        }
        return false;
    }
    
    /**
     * 获取处理统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return String.format("EventProcessor{processedCount=%d, errorCount=%d, lastProcessTime=%d}",
                processedCount, errorCount, lastProcessTime);
    }
    
    /**
     * 获取已处理事件数量
     *
     * @return 已处理事件数量
     */
    public long getProcessedCount() {
        return processedCount;
    }
    
    /**
     * 获取错误事件数量
     *
     * @return 错误事件数量
     */
    public long getErrorCount() {
        return errorCount;
    }
}