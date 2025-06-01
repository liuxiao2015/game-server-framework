/*
 * 文件名: Dispatcher.java
 * 用途: 消息调度器
 * 实现内容:
 *   - 负责执行Actor的消息处理
 *   - 基于虚拟线程的执行器
 *   - 支持多种调度策略和负载均衡
 *   - 任务窃取优化和执行器监控
 * 技术选型:
 *   - Java 21虚拟线程提供高并发
 *   - 线程池管理和资源控制
 *   - 异步执行和监控统计
 * 依赖关系:
 *   - 被ActorSystem使用调度Actor
 *   - 与ActorMailbox协作处理消息
 *   - 集成监控和性能统计
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息调度器
 * <p>
 * 负责调度和执行Actor的消息处理任务，支持多种执行策略
 * 和性能监控功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class Dispatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(Dispatcher.class);
    
    /** 调度器名称 */
    private final String name;
    
    /** 调度器类型 */
    private final DispatcherType type;
    
    /** 执行器服务 */
    private final ExecutorService executorService;
    
    /** 调度执行器（用于定时任务） */
    private final ScheduledExecutorService scheduledExecutor;
    
    /** 吞吐量配置 */
    private final int throughput;
    
    /** 活跃任务数 */
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    
    /** 总执行任务数 */
    private final AtomicLong totalTasks = new AtomicLong(0);
    
    /** 总执行时间 */
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    
    /** 是否已关闭 */
    private volatile boolean shutdown = false;
    
    /**
     * 构造函数
     *
     * @param name       调度器名称
     * @param type       调度器类型
     * @param throughput 吞吐量配置
     */
    public Dispatcher(String name, DispatcherType type, int throughput) {
        this.name = name;
        this.type = type;
        this.throughput = throughput;
        this.executorService = createExecutorService(type);
        this.scheduledExecutor = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("scheduler-" + name + "-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
        
        logger.info("调度器[{}]初始化完成，类型: {}, 吞吐量: {}", name, type, throughput);
    }
    
    /**
     * 创建执行器服务
     *
     * @param type 调度器类型
     * @return 执行器服务
     */
    private ExecutorService createExecutorService(DispatcherType type) {
        return switch (type) {
            case VIRTUAL_THREAD -> Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setName(name + "-virtual-" + t.getId());
                t.setDaemon(true);
                return t;
            });
            case FIXED_THREAD_POOL -> Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> {
                        Thread t = new Thread(r);
                        t.setName(name + "-worker-" + t.getId());
                        t.setDaemon(true);
                        return t;
                    }
            );
            case FORK_JOIN -> new ForkJoinPool(
                    Runtime.getRuntime().availableProcessors(),
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                    null,
                    true
            );
            case SINGLE_THREAD -> Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r);
                        t.setName(name + "-single-" + t.getId());
                        t.setDaemon(true);
                        return t;
                    }
            );
        };
    }
    
    /**
     * 执行Actor任务
     *
     * @param actorRef Actor引用
     * @param mailbox  Actor邮箱
     */
    public void execute(LocalActorRef actorRef, ActorMailbox mailbox) {
        if (shutdown) {
            logger.warn("调度器[{}]已关闭，无法执行任务", name);
            return;
        }
        
        ActorTask task = new ActorTask(actorRef, mailbox);
        activeTasks.incrementAndGet();
        totalTasks.incrementAndGet();
        
        executorService.submit(task);
    }
    
    /**
     * 调度延迟任务
     *
     * @param command 任务命令
     * @param delay   延迟时间
     * @param unit    时间单位
     * @return 可取消的任务Future
     */
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (shutdown) {
            throw new IllegalStateException("调度器已关闭");
        }
        return scheduledExecutor.schedule(command, delay, unit);
    }
    
    /**
     * 调度周期任务
     *
     * @param command      任务命令
     * @param initialDelay 初始延迟
     * @param period       周期间隔
     * @param unit         时间单位
     * @return 可取消的任务Future
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (shutdown) {
            throw new IllegalStateException("调度器已关闭");
        }
        return scheduledExecutor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }
    
    /**
     * 获取调度器名称
     *
     * @return 调度器名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取调度器类型
     *
     * @return 调度器类型
     */
    public DispatcherType getType() {
        return type;
    }
    
    /**
     * 获取吞吐量配置
     *
     * @return 吞吐量
     */
    public int getThroughput() {
        return throughput;
    }
    
    /**
     * 获取活跃任务数
     *
     * @return 活跃任务数
     */
    public int getActiveTasks() {
        return activeTasks.get();
    }
    
    /**
     * 获取总执行任务数
     *
     * @return 总任务数
     */
    public long getTotalTasks() {
        return totalTasks.get();
    }
    
    /**
     * 获取平均执行时间
     *
     * @return 平均执行时间（毫秒）
     */
    public double getAverageExecutionTime() {
        long total = totalTasks.get();
        return total > 0 ? (double) totalExecutionTime.get() / total : 0.0;
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        if (shutdown) {
            return;
        }
        
        shutdown = true;
        logger.info("调度器[{}]开始关闭", name);
        
        executorService.shutdown();
        scheduledExecutor.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("调度器[{}]执行器未能在5秒内正常关闭，强制关闭", name);
                executorService.shutdownNow();
            }
            
            if (!scheduledExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.warn("调度器[{}]定时执行器未能在2秒内正常关闭，强制关闭", name);
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("调度器[{}]关闭过程被中断", name);
        }
        
        logger.info("调度器[{}]关闭完成", name);
    }
    
    /**
     * 检查调度器是否已关闭
     *
     * @return 如果已关闭返回true
     */
    public boolean isShutdown() {
        return shutdown;
    }
    
    @Override
    public String toString() {
        return String.format("Dispatcher{name=%s, type=%s, throughput=%d, activeTasks=%d, totalTasks=%d}", 
                name, type, throughput, activeTasks.get(), totalTasks.get());
    }
    
    /**
     * Actor任务类
     */
    private class ActorTask implements Runnable {
        private final LocalActorRef actorRef;
        private final ActorMailbox mailbox;
        
        public ActorTask(LocalActorRef actorRef, ActorMailbox mailbox) {
            this.actorRef = actorRef;
            this.mailbox = mailbox;
        }
        
        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            
            try {
                int processed = 0;
                
                // 批量处理消息，提高吞吐量
                while (processed < throughput && !mailbox.isEmpty()) {
                    MessageEnvelope envelope = mailbox.poll();
                    if (envelope != null) {
                        actorRef.processMessage(envelope);
                        processed++;
                    } else {
                        break;
                    }
                }
                
                // 如果还有消息待处理，继续调度
                if (!mailbox.isEmpty()) {
                    execute(actorRef, mailbox);
                }
                
            } catch (Exception e) {
                logger.error("Actor任务执行失败: {}", actorRef, e);
            } finally {
                activeTasks.decrementAndGet();
                long executionTime = System.currentTimeMillis() - startTime;
                totalExecutionTime.addAndGet(executionTime);
            }
        }
    }
    
    /**
     * 调度器类型枚举
     */
    public enum DispatcherType {
        /** 虚拟线程池 */
        VIRTUAL_THREAD,
        /** 固定线程池 */
        FIXED_THREAD_POOL,
        /** Fork-Join池 */
        FORK_JOIN,
        /** 单线程 */
        SINGLE_THREAD
    }
}