/*
 * 文件名: EventWrapper.java
 * 用途: 事件包装器
 * 实现内容:
 *   - 包装游戏事件用于Disruptor处理
 *   - 支持对象重用以减少GC压力
 *   - 提供事件序列号跟踪
 * 技术选型:
 *   - 可重用对象池模式
 *   - Disruptor事件模式
 * 依赖关系:
 *   - 被DisruptorEventBus使用
 *   - 包装GameEvent对象
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.disruptor;

import com.lx.gameserver.frame.event.core.GameEvent;

/**
 * 事件包装器
 * <p>
 * 用于在Disruptor RingBuffer中包装游戏事件。
 * 通过对象重用模式减少内存分配和GC压力。
 * 每个包装器实例会在RingBuffer中重复使用。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class EventWrapper {
    
    /** 实际的游戏事件 */
    private volatile GameEvent event;
    
    /** 事件在RingBuffer中的序列号 */
    private volatile long sequence;
    
    /** 事件发布时间戳，用于性能监控 */
    private volatile long publishTimestamp;
    
    /** 事件处理开始时间戳 */
    private volatile long processStartTimestamp;
    
    /** 是否已被处理的标记 */
    private volatile boolean processed;
    
    /**
     * 默认构造函数
     */
    public EventWrapper() {
        reset();
    }
    
    /**
     * 重置事件包装器
     * <p>
     * 用于对象重用，清除之前的状态。
     * 在每次事件发布前调用。
     * </p>
     */
    public void reset() {
        this.event = null;
        this.sequence = 0L;
        this.publishTimestamp = 0L;
        this.processStartTimestamp = 0L;
        this.processed = false;
    }
    
    /**
     * 设置事件
     *
     * @param event    游戏事件
     * @param sequence 序列号
     */
    public void setEvent(GameEvent event, long sequence) {
        this.event = event;
        this.sequence = sequence;
        this.publishTimestamp = System.currentTimeMillis();
        this.processed = false;
    }
    
    /**
     * 获取游戏事件
     *
     * @return 游戏事件
     */
    public GameEvent getEvent() {
        return event;
    }
    
    /**
     * 获取序列号
     *
     * @return 序列号
     */
    public long getSequence() {
        return sequence;
    }
    
    /**
     * 获取发布时间戳
     *
     * @return 发布时间戳
     */
    public long getPublishTimestamp() {
        return publishTimestamp;
    }
    
    /**
     * 标记处理开始
     */
    public void markProcessStart() {
        this.processStartTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 标记处理完成
     */
    public void markProcessed() {
        this.processed = true;
    }
    
    /**
     * 获取处理开始时间戳
     *
     * @return 处理开始时间戳
     */
    public long getProcessStartTimestamp() {
        return processStartTimestamp;
    }
    
    /**
     * 判断是否已处理
     *
     * @return 如果已处理返回true
     */
    public boolean isProcessed() {
        return processed;
    }
    
    /**
     * 获取事件在队列中等待的时间（毫秒）
     *
     * @return 等待时间
     */
    public long getWaitingTime() {
        if (processStartTimestamp == 0L || publishTimestamp == 0L) {
            return 0L;
        }
        return processStartTimestamp - publishTimestamp;
    }
    
    /**
     * 判断包装器是否有效
     *
     * @return 如果包含有效事件返回true
     */
    public boolean isValid() {
        return event != null && event.isValid();
    }
    
    @Override
    public String toString() {
        return String.format("EventWrapper{sequence=%d, event=%s, processed=%s}", 
                sequence, event, processed);
    }
}