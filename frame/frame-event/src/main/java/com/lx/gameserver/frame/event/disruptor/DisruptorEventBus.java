/*
 * 文件名: DisruptorEventBus.java
 * 用途: 基于Disruptor的高性能事件总线
 * 实现内容:
 *   - 提供高吞吐量、低延迟的事件发布订阅机制
 *   - 支持每秒百万级事件处理
 *   - 事件处理器注册和管理
 * 技术选型:
 *   - LMAX Disruptor 3.4.4
 *   - 无锁环形缓冲区
 *   - 多生产者模式
 * 依赖关系:
 *   - 依赖EventWrapper和EventProcessor
 *   - 实现EventBus接口
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.disruptor;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lx.gameserver.frame.event.EventBus;
import com.lx.gameserver.frame.event.core.GameEvent;
import com.lx.gameserver.frame.event.core.EventHandler;
import com.lx.gameserver.frame.event.monitor.EventMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Disruptor的高性能事件总线
 * <p>
 * 使用LMAX Disruptor提供高吞吐量、低延迟的事件发布订阅机制。
 * 支持每秒百万级事件处理，适用于高性能游戏服务器场景。
 * 采用无锁环形缓冲区和批量处理优化性能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
public class DisruptorEventBus implements EventBus {
    
    private static final Logger logger = LoggerFactory.getLogger(DisruptorEventBus.class);
    
    /** Ring Buffer大小，必须是2的幂次 */
    private static final int RING_BUFFER_SIZE = 65536;
    
    /** 事件总线名称 */
    private final String name;
    
    /** Disruptor实例 */
    private Disruptor<EventWrapper> disruptor;
    
    /** Ring Buffer */
    private RingBuffer<EventWrapper> ringBuffer;
    
    /** 事件处理器 */
    private EventProcessor eventProcessor;
    
    /** 事件指标收集器 */
    @Autowired(required = false)
    private EventMetrics eventMetrics;
    
    /** 运行状态 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** 发布统计 */
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    
    /**
     * 默认构造函数
     */
    public DisruptorEventBus() {
        this("DefaultEventBus");
    }
    
    /**
     * 构造函数
     *
     * @param name 事件总线名称
     */
    public DisruptorEventBus(String name) {
        this.name = name;
    }
    
    /**
     * 初始化事件总线
     */
    @PostConstruct
    public void initialize() {
        if (running.get()) {
            return;
        }
        
        try {
            // 事件工厂
            EventFactory<EventWrapper> factory = EventWrapper::new;
            
            // 线程工厂 - 使用普通线程工厂（Java 17不支持虚拟线程）
            ThreadFactory threadFactory = r -> {
                Thread t = new Thread(r, "event-processor-" + name + "-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            };
            
            // 创建Disruptor
            disruptor = new Disruptor<>(
                    factory,
                    RING_BUFFER_SIZE,
                    threadFactory,
                    ProducerType.MULTI,
                    new YieldingWaitStrategy()
            );
            
            // 创建事件处理器
            eventProcessor = new EventProcessor();
            
            // 设置事件处理器
            disruptor.handleEventsWith(eventProcessor);
            
            // 设置异常处理器
            disruptor.setDefaultExceptionHandler(new EventExceptionHandler());
            
            // 启动Disruptor
            disruptor.start();
            
            // 获取RingBuffer
            ringBuffer = disruptor.getRingBuffer();
            
            running.set(true);
            
            logger.info("Disruptor事件总线初始化完成: name={}, bufferSize={}", name, RING_BUFFER_SIZE);
            
        } catch (Exception e) {
            logger.error("Disruptor事件总线初始化失败", e);
            throw new RuntimeException("事件总线初始化失败", e);
        }
    }
    
    @Override
    public boolean publish(GameEvent event) {
        if (event == null) {
            logger.warn("尝试发布空事件");
            return false;
        }
        
        if (!running.get()) {
            logger.warn("事件总线未运行，无法发布事件: {}", event.getEventType());
            failedCount.incrementAndGet();
            return false;
        }
        
        try {
            // 获取下一个序列号
            long sequence = ringBuffer.next();
            
            try {
                // 获取事件包装器
                EventWrapper wrapper = ringBuffer.get(sequence);
                
                // 重置并设置事件
                wrapper.reset();
                wrapper.setEvent(event, sequence);
                
            } finally {
                // 发布事件
                ringBuffer.publish(sequence);
            }
            
            publishedCount.incrementAndGet();
            
            // 记录发布指标
            if (eventMetrics != null) {
                eventMetrics.recordPublish(event.getEventType());
            }
            
            if (logger.isDebugEnabled()) {
                logger.debug("事件发布成功: eventType={}, eventId={}, sequence={}", 
                        event.getEventType(), event.getEventId(), sequence);
            }
            
            return true;
            
        } catch (Exception e) {
            failedCount.incrementAndGet();
            
            // 记录错误指标
            if (eventMetrics != null) {
                eventMetrics.recordError(event.getEventType());
            }
            
            logger.error("发布事件失败: eventType={}, eventId={}", 
                    event.getEventType(), event.getEventId(), e);
            return false;
        }
    }
    
    @Override
    public CompletableFuture<Void> publishSync(GameEvent event) {
        // 对于Disruptor实现，同步发布就是异步发布
        // 因为Disruptor本身是异步处理的
        return CompletableFuture.runAsync(() -> publish(event));
    }
    
    @Override
    public int publishBatch(List<GameEvent> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        
        if (!running.get()) {
            logger.warn("事件总线未运行，无法批量发布事件");
            return 0;
        }
        
        int successCount = 0;
        
        try {
            // 批量获取序列号
            int batchSize = events.size();
            long endSequence = ringBuffer.next(batchSize);
            long startSequence = endSequence - batchSize + 1;
            
            try {
                // 批量设置事件
                for (int i = 0; i < batchSize; i++) {
                    long sequence = startSequence + i;
                    EventWrapper wrapper = ringBuffer.get(sequence);
                    
                    wrapper.reset();
                    wrapper.setEvent(events.get(i), sequence);
                    successCount++;
                }
                
            } finally {
                // 批量发布
                ringBuffer.publish(startSequence, endSequence);
            }
            
            publishedCount.addAndGet(successCount);
            
            if (logger.isDebugEnabled()) {
                logger.debug("批量事件发布成功: count={}, startSequence={}, endSequence={}", 
                        successCount, startSequence, endSequence);
            }
            
        } catch (Exception e) {
            failedCount.addAndGet(events.size() - successCount);
            logger.error("批量发布事件失败: totalCount={}, successCount={}", 
                    events.size(), successCount, e);
        }
        
        return successCount;
    }
    
    @Override
    public <T extends GameEvent> boolean register(Class<T> eventClass, EventHandler<T> handler) {
        if (eventClass == null || handler == null) {
            logger.warn("注册事件处理器参数无效: eventClass={}, handler={}", eventClass, handler);
            return false;
        }
        
        if (eventProcessor == null) {
            logger.warn("事件处理器未初始化，无法注册处理器");
            return false;
        }
        
        try {
            eventProcessor.register(eventClass, handler);
            
            logger.info("事件处理器注册成功: eventType={}, handler={}", 
                    eventClass.getSimpleName(), handler.getName());
            return true;
            
        } catch (Exception e) {
            logger.error("注册事件处理器失败: eventType={}, handler={}", 
                    eventClass.getSimpleName(), handler.getName(), e);
            return false;
        }
    }
    
    @Override
    public <T extends GameEvent> boolean register(Class<T> eventClass, EventHandler<T> handler, String handlerName) {
        // 对于简单实现，忽略handlerName参数
        return register(eventClass, handler);
    }
    
    @Override
    public <T extends GameEvent> boolean unregister(Class<T> eventClass, EventHandler<T> handler) {
        if (eventClass == null || handler == null) {
            logger.warn("注销事件处理器参数无效: eventClass={}, handler={}", eventClass, handler);
            return false;
        }
        
        if (eventProcessor == null) {
            logger.warn("事件处理器未初始化，无法注销处理器");
            return false;
        }
        
        try {
            boolean result = eventProcessor.unregister(eventClass, handler);
            
            if (result) {
                logger.info("事件处理器注销成功: eventType={}, handler={}", 
                        eventClass.getSimpleName(), handler.getName());
            } else {
                logger.warn("事件处理器注销失败，处理器未找到: eventType={}, handler={}", 
                        eventClass.getSimpleName(), handler.getName());
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("注销事件处理器失败: eventType={}, handler={}", 
                    eventClass.getSimpleName(), handler.getName(), e);
            return false;
        }
    }
    
    @Override
    public <T extends GameEvent> int unregisterAll(Class<T> eventClass) {
        // 简单实现暂不支持
        logger.warn("当前实现不支持批量注销功能");
        return 0;
    }
    
    @Override
    public <T extends GameEvent> int getHandlerCount(Class<T> eventClass) {
        // 简单实现暂不支持
        return 0;
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public boolean start() {
        if (running.get()) {
            return true;
        }
        
        initialize();
        return running.get();
    }
    
    @Override
    public void shutdown() {
        if (!running.get()) {
            return;
        }
        
        logger.info("开始关闭Disruptor事件总线: {}", name);
        
        try {
            running.set(false);
            
            if (disruptor != null) {
                disruptor.shutdown();
            }
            
            logger.info("Disruptor事件总线关闭完成: {}", name);
            
        } catch (Exception e) {
            logger.error("关闭Disruptor事件总线失败", e);
        }
    }
    
    @Override
    public void shutdownNow() {
        if (!running.get()) {
            return;
        }
        
        logger.info("开始强制关闭Disruptor事件总线: {}", name);
        
        try {
            running.set(false);
            
            if (disruptor != null) {
                disruptor.halt();
            }
            
            logger.info("Disruptor事件总线强制关闭完成: {}", name);
            
        } catch (Exception e) {
            logger.error("强制关闭Disruptor事件总线失败", e);
        }
    }
    
    /**
     * 销毁方法
     */
    @PreDestroy
    public void destroy() {
        shutdown();
    }
    
    /**
     * 获取发布统计信息
     *
     * @return 统计信息
     */
    public String getStatistics() {
        return String.format("DisruptorEventBus{name='%s', published=%d, failed=%d, running=%s}",
                name, publishedCount.get(), failedCount.get(), running.get());
    }
    
    /**
     * 事件异常处理器
     */
    private static class EventExceptionHandler implements ExceptionHandler<EventWrapper> {
        
        private static final Logger logger = LoggerFactory.getLogger(EventExceptionHandler.class);
        
        @Override
        public void handleEventException(Throwable ex, long sequence, EventWrapper event) {
            logger.error("处理事件时发生异常: sequence={}, event={}", sequence, event, ex);
        }
        
        @Override
        public void handleOnStartException(Throwable ex) {
            logger.error("事件处理器启动时发生异常", ex);
        }
        
        @Override
        public void handleOnShutdownException(Throwable ex) {
            logger.error("事件处理器关闭时发生异常", ex);
        }
    }
}