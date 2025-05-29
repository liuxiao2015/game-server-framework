/*
 * 文件名: EventBusManager.java
 * 用途: 事件总线管理器
 * 实现内容:
 *   - 管理多个事件总线实例，支持按优先级分发
 *   - 提供全局事件总线访问和管理
 *   - 支持不同优先级事件的分离处理
 * 技术选型:
 *   - 单例模式
 *   - Spring组件管理
 *   - 策略模式
 * 依赖关系:
 *   - 管理DisruptorEventBus实例
 *   - 被业务模块使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event;

import com.lx.gameserver.frame.event.core.EventPriority;
import com.lx.gameserver.frame.event.core.GameEvent;
import com.lx.gameserver.frame.event.core.EventHandler;
import com.lx.gameserver.frame.event.disruptor.DisruptorEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 事件总线管理器
 * <p>
 * 管理多个事件总线实例，支持按优先级分发事件到不同的事件总线。
 * 提供全局事件总线访问和管理功能，支持高优先级事件优先处理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
public class EventBusManager {
    
    private static final Logger logger = LoggerFactory.getLogger(EventBusManager.class);
    
    /** 高优先级事件总线 */
    private EventBus highPriorityBus;
    
    /** 普通优先级事件总线 */
    private EventBus normalPriorityBus;
    
    /** 低优先级事件总线 */
    private EventBus lowPriorityBus;
    
    /** 事件总线映射表 */
    private final ConcurrentMap<EventPriority, EventBus> busMap = new ConcurrentHashMap<>();
    
    /** 默认事件总线 */
    private EventBus defaultBus;
    
    /**
     * 初始化事件总线管理器
     */
    @PostConstruct
    public void initialize() {
        try {
            // 创建高优先级事件总线
            highPriorityBus = new DisruptorEventBus("HighPriorityEventBus");
            highPriorityBus.start();
            
            // 创建普通优先级事件总线
            normalPriorityBus = new DisruptorEventBus("NormalPriorityEventBus");
            normalPriorityBus.start();
            
            // 创建低优先级事件总线
            lowPriorityBus = new DisruptorEventBus("LowPriorityEventBus");
            lowPriorityBus.start();
            
            // 配置映射关系
            busMap.put(EventPriority.HIGHEST, highPriorityBus);
            busMap.put(EventPriority.HIGH, highPriorityBus);
            busMap.put(EventPriority.NORMAL, normalPriorityBus);
            busMap.put(EventPriority.LOW, lowPriorityBus);
            busMap.put(EventPriority.LOWEST, lowPriorityBus);
            
            // 设置默认总线
            defaultBus = normalPriorityBus;
            
            logger.info("事件总线管理器初始化完成");
            
        } catch (Exception e) {
            logger.error("事件总线管理器初始化失败", e);
            throw new RuntimeException("事件总线管理器初始化失败", e);
        }
    }
    
    /**
     * 根据事件优先级发布事件
     *
     * @param event 游戏事件
     * @return 发布是否成功
     */
    public boolean publish(GameEvent event) {
        if (event == null) {
            logger.warn("尝试发布空事件");
            return false;
        }
        
        EventBus bus = selectBus(event.getPriority());
        return bus.publish(event);
    }
    
    /**
     * 同步发布事件
     *
     * @param event 游戏事件
     * @return 异步结果Future
     */
    public CompletableFuture<Void> publishSync(GameEvent event) {
        if (event == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("事件不能为空"));
        }
        
        EventBus bus = selectBus(event.getPriority());
        return bus.publishSync(event);
    }
    
    /**
     * 批量发布事件
     *
     * @param events 事件列表
     * @return 发布成功的事件数量
     */
    public int publishBatch(List<GameEvent> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        
        // 按优先级分组
        ConcurrentMap<EventPriority, List<GameEvent>> groupedEvents = new ConcurrentHashMap<>();
        for (GameEvent event : events) {
            EventPriority priority = event.getPriority();
            groupedEvents.computeIfAbsent(priority, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(event);
        }
        
        // 分别发布到对应的事件总线
        int totalSuccess = 0;
        for (ConcurrentMap.Entry<EventPriority, List<GameEvent>> entry : groupedEvents.entrySet()) {
            EventBus bus = selectBus(entry.getKey());
            totalSuccess += bus.publishBatch(entry.getValue());
        }
        
        return totalSuccess;
    }
    
    /**
     * 注册事件处理器到指定优先级的总线
     *
     * @param priority   事件优先级
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 注册是否成功
     */
    public <T extends GameEvent> boolean register(EventPriority priority, Class<T> eventClass, EventHandler<T> handler) {
        EventBus bus = selectBus(priority);
        return bus.register(eventClass, handler);
    }
    
    /**
     * 注册事件处理器到所有总线
     *
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 注册成功的总线数量
     */
    public <T extends GameEvent> int registerToAll(Class<T> eventClass, EventHandler<T> handler) {
        int successCount = 0;
        
        for (EventBus bus : busMap.values()) {
            if (bus.register(eventClass, handler)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 注销事件处理器
     *
     * @param priority   事件优先级
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 注销是否成功
     */
    public <T extends GameEvent> boolean unregister(EventPriority priority, Class<T> eventClass, EventHandler<T> handler) {
        EventBus bus = selectBus(priority);
        return bus.unregister(eventClass, handler);
    }
    
    /**
     * 从所有总线注销事件处理器
     *
     * @param eventClass 事件类型
     * @param handler    处理器
     * @param <T>        事件类型参数
     * @return 注销成功的总线数量
     */
    public <T extends GameEvent> int unregisterFromAll(Class<T> eventClass, EventHandler<T> handler) {
        int successCount = 0;
        
        for (EventBus bus : busMap.values()) {
            if (bus.unregister(eventClass, handler)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 根据优先级选择事件总线
     *
     * @param priority 事件优先级
     * @return 对应的事件总线
     */
    private EventBus selectBus(EventPriority priority) {
        if (priority == null) {
            return defaultBus;
        }
        
        EventBus bus = busMap.get(priority);
        return bus != null ? bus : defaultBus;
    }
    
    /**
     * 获取高优先级事件总线
     *
     * @return 高优先级事件总线
     */
    public EventBus getHighPriorityBus() {
        return highPriorityBus;
    }
    
    /**
     * 获取普通优先级事件总线
     *
     * @return 普通优先级事件总线
     */
    public EventBus getNormalPriorityBus() {
        return normalPriorityBus;
    }
    
    /**
     * 获取低优先级事件总线
     *
     * @return 低优先级事件总线
     */
    public EventBus getLowPriorityBus() {
        return lowPriorityBus;
    }
    
    /**
     * 获取默认事件总线
     *
     * @return 默认事件总线
     */
    public EventBus getDefaultBus() {
        return defaultBus;
    }
    
    /**
     * 获取指定优先级的事件总线
     *
     * @param priority 事件优先级
     * @return 对应的事件总线
     */
    public EventBus getBus(EventPriority priority) {
        return selectBus(priority);
    }
    
    /**
     * 获取所有事件总线的运行状态
     *
     * @return 运行状态信息
     */
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("EventBusManager Status:\n");
        
        for (ConcurrentMap.Entry<EventPriority, EventBus> entry : busMap.entrySet()) {
            EventBus bus = entry.getValue();
            status.append(String.format("  %s: %s (running: %s)\n", 
                    entry.getKey(), bus.getName(), bus.isRunning()));
        }
        
        return status.toString();
    }
    
    /**
     * 销毁事件总线管理器
     */
    @PreDestroy
    public void destroy() {
        logger.info("开始关闭事件总线管理器");
        
        try {
            if (highPriorityBus != null) {
                highPriorityBus.shutdown();
            }
            
            if (normalPriorityBus != null) {
                normalPriorityBus.shutdown();
            }
            
            if (lowPriorityBus != null) {
                lowPriorityBus.shutdown();
            }
            
            busMap.clear();
            
            logger.info("事件总线管理器关闭完成");
            
        } catch (Exception e) {
            logger.error("关闭事件总线管理器失败", e);
        }
    }
}