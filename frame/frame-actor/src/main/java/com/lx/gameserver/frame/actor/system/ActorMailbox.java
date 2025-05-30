/*
 * 文件名: ActorMailbox.java
 * 用途: Actor邮箱实现
 * 实现内容:
 *   - 每个Actor的消息队列实现
 *   - 基于高性能并发队列
 *   - 支持有界/无界邮箱和消息优先级
 *   - 邮箱监控和背压机制
 * 技术选型:
 *   - 使用Java 21的并发队列
 *   - 支持优先级队列排序
 *   - 原子操作保证线程安全
 * 依赖关系:
 *   - 与Dispatcher协作处理消息
 *   - 被ActorSystem管理
 *   - 支持消息路由和监控
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import com.lx.gameserver.frame.actor.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actor邮箱实现
 * <p>
 * 为每个Actor提供独立的消息队列，支持有界和无界邮箱、
 * 消息优先级排序、监控统计等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorMailbox {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorMailbox.class);
    
    /** 邮箱类型 */
    private final MailboxType type;
    
    /** 消息队列 */
    private final BlockingQueue<MessageEnvelope> messageQueue;
    
    /** 邮箱容量 */
    private final int capacity;
    
    /** 当前队列大小 */
    private final AtomicInteger currentSize = new AtomicInteger(0);
    
    /** 总接收消息数 */
    private final AtomicLong totalReceived = new AtomicLong(0);
    
    /** 总处理消息数 */
    private final AtomicLong totalProcessed = new AtomicLong(0);
    
    /** 是否已关闭 */
    private volatile boolean closed = false;
    
    /**
     * 构造函数
     *
     * @param type     邮箱类型
     * @param capacity 邮箱容量（无界邮箱忽略此参数）
     */
    public ActorMailbox(MailboxType type, int capacity) {
        this.type = type;
        this.capacity = capacity;
        this.messageQueue = createQueue(type, capacity);
    }
    
    /**
     * 创建消息队列
     *
     * @param type     邮箱类型
     * @param capacity 容量
     * @return 消息队列
     */
    private BlockingQueue<MessageEnvelope> createQueue(MailboxType type, int capacity) {
        return switch (type) {
            case UNBOUNDED -> new LinkedBlockingQueue<>();
            case BOUNDED -> new LinkedBlockingQueue<>(capacity);
            case PRIORITY_UNBOUNDED -> new PriorityBlockingQueue<>();
            case PRIORITY_BOUNDED -> new PriorityBlockingQueue<>(capacity);
        };
    }
    
    /**
     * 发送消息到邮箱
     *
     * @param envelope 消息信封
     * @return 是否成功发送
     */
    public boolean offer(MessageEnvelope envelope) {
        if (closed) {
            logger.warn("邮箱已关闭，无法接收消息: {}", envelope);
            return false;
        }
        
        if (isFull()) {
            logger.warn("邮箱已满，拒绝消息: {}", envelope);
            return false;
        }
        
        boolean success = messageQueue.offer(envelope);
        if (success) {
            currentSize.incrementAndGet();
            totalReceived.incrementAndGet();
        }
        
        return success;
    }
    
    /**
     * 从邮箱取出消息
     *
     * @return 消息信封，如果没有消息则返回null
     */
    public MessageEnvelope poll() {
        MessageEnvelope envelope = messageQueue.poll();
        if (envelope != null) {
            currentSize.decrementAndGet();
            totalProcessed.incrementAndGet();
        }
        return envelope;
    }
    
    /**
     * 阻塞取出消息
     *
     * @return 消息信封
     * @throws InterruptedException 中断异常
     */
    public MessageEnvelope take() throws InterruptedException {
        MessageEnvelope envelope = messageQueue.take();
        currentSize.decrementAndGet();
        totalProcessed.incrementAndGet();
        return envelope;
    }
    
    /**
     * 检查邮箱是否为空
     *
     * @return 如果为空返回true
     */
    public boolean isEmpty() {
        return messageQueue.isEmpty();
    }
    
    /**
     * 检查邮箱是否已满
     *
     * @return 如果已满返回true
     */
    public boolean isFull() {
        if (type == MailboxType.UNBOUNDED || type == MailboxType.PRIORITY_UNBOUNDED) {
            return false;
        }
        return currentSize.get() >= capacity;
    }
    
    /**
     * 获取当前队列大小
     *
     * @return 队列大小
     */
    public int size() {
        return currentSize.get();
    }
    
    /**
     * 获取邮箱容量
     *
     * @return 容量
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * 获取邮箱类型
     *
     * @return 邮箱类型
     */
    public MailboxType getType() {
        return type;
    }
    
    /**
     * 获取总接收消息数
     *
     * @return 总接收消息数
     */
    public long getTotalReceived() {
        return totalReceived.get();
    }
    
    /**
     * 获取总处理消息数
     *
     * @return 总处理消息数
     */
    public long getTotalProcessed() {
        return totalProcessed.get();
    }
    
    /**
     * 关闭邮箱
     */
    public void close() {
        closed = true;
        messageQueue.clear();
        logger.debug("邮箱已关闭，清空剩余消息");
    }
    
    /**
     * 检查邮箱是否已关闭
     *
     * @return 如果已关闭返回true
     */
    public boolean isClosed() {
        return closed;
    }
    
    @Override
    public String toString() {
        return String.format("ActorMailbox{type=%s, size=%d, capacity=%d, received=%d, processed=%d}", 
                type, currentSize.get(), capacity, totalReceived.get(), totalProcessed.get());
    }
    
    /**
     * 邮箱类型枚举
     */
    public enum MailboxType {
        /** 无界邮箱 */
        UNBOUNDED,
        /** 有界邮箱 */
        BOUNDED,
        /** 无界优先级邮箱 */
        PRIORITY_UNBOUNDED,
        /** 有界优先级邮箱 */
        PRIORITY_BOUNDED
    }
}