/*
 * 文件名: GameThreadFactory.java
 * 用途: 游戏线程工厂
 * 实现内容:
 *   - 自定义线程创建，支持线程命名、优先级设置
 *   - 支持虚拟线程和平台线程的创建（当前为Java 17兼容版本）
 *   - 线程命名规范（如：game-thread-pool-1）
 *   - 异常处理器配置
 * 技术选型:
 *   - Java标准ThreadFactory接口
 *   - Thread.Builder API准备（Java 21特性的预留接口）
 *   - 统一线程管理和监控
 * 依赖关系:
 *   - 基于Java标准API，兼容Java 17+
 *   - 被VirtualThreadExecutor和其他执行器使用
 *   - 与ExecutorManager协作管理线程生命周期
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏线程工厂
 * <p>
 * 自定义线程创建，支持线程命名、优先级设置、异常处理器配置等。
 * 统一管理游戏服务器的线程创建，为将来升级Java 21做准备。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class GameThreadFactory implements ThreadFactory {

    private static final Logger logger = LoggerFactory.getLogger(GameThreadFactory.class);

    /**
     * 线程名前缀
     */
    private final String namePrefix;

    /**
     * 线程优先级
     */
    private final int priority;

    /**
     * 是否为守护线程
     */
    private final boolean daemon;

    /**
     * 线程组
     */
    private final ThreadGroup threadGroup;

    /**
     * 线程计数器
     */
    private final AtomicLong threadCounter = new AtomicLong(0);

    /**
     * 异常处理器
     */
    private final Thread.UncaughtExceptionHandler exceptionHandler;

    /**
     * 线程类型
     */
    private final ThreadType threadType;

    /**
     * 线程类型枚举
     */
    public enum ThreadType {
        /**
         * 平台线程（传统线程）
         */
        PLATFORM,
        /**
         * 虚拟线程（Java 21特性，当前模拟实现）
         */
        VIRTUAL
    }

    /**
     * 构造函数 - 使用默认配置
     *
     * @param namePrefix 线程名前缀
     */
    public GameThreadFactory(String namePrefix) {
        this(namePrefix, Thread.NORM_PRIORITY, true, ThreadType.PLATFORM);
    }

    /**
     * 构造函数 - 指定线程类型
     *
     * @param namePrefix 线程名前缀
     * @param threadType 线程类型
     */
    public GameThreadFactory(String namePrefix, ThreadType threadType) {
        this(namePrefix, Thread.NORM_PRIORITY, true, threadType);
    }

    /**
     * 构造函数 - 完整配置
     *
     * @param namePrefix 线程名前缀
     * @param priority   线程优先级
     * @param daemon     是否为守护线程
     * @param threadType 线程类型
     */
    public GameThreadFactory(String namePrefix, int priority, boolean daemon, ThreadType threadType) {
        this.namePrefix = namePrefix;
        this.priority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority));
        this.daemon = daemon;
        this.threadType = threadType;
        
        // 创建线程组
        SecurityManager securityManager = System.getSecurityManager();
        this.threadGroup = (securityManager != null) ? 
            securityManager.getThreadGroup() : Thread.currentThread().getThreadGroup();
        
        // 默认异常处理器
        this.exceptionHandler = new GameUncaughtExceptionHandler();
        
        logger.info("游戏线程工厂已创建，前缀:{}, 优先级:{}, 守护线程:{}, 类型:{}", 
                    namePrefix, priority, daemon, threadType);
    }

    @Override
    public Thread newThread(Runnable r) {
        // 生成线程名
        String threadName = namePrefix + "-" + threadCounter.incrementAndGet();
        
        // 创建线程（当前为Java 17兼容版本）
        Thread thread = createThread(r, threadName);
        
        // 配置线程属性
        configureThread(thread);
        
        logger.debug("创建新线程: {}", threadName);
        return thread;
    }

    /**
     * 创建线程
     */
    private Thread createThread(Runnable r, String threadName) {
        switch (threadType) {
            case VIRTUAL:
                // 当前Java 17版本使用平台线程模拟
                // 升级到Java 21后可以使用: Thread.ofVirtual().name(threadName).unstarted(r)
                logger.debug("创建虚拟线程(模拟): {}", threadName);
                return new Thread(threadGroup, r, threadName);
            
            case PLATFORM:
            default:
                return new Thread(threadGroup, r, threadName);
        }
    }

    /**
     * 配置线程属性
     */
    private void configureThread(Thread thread) {
        // 设置守护线程
        if (thread.isDaemon() != daemon) {
            thread.setDaemon(daemon);
        }
        
        // 设置优先级
        if (thread.getPriority() != priority) {
            thread.setPriority(priority);
        }
        
        // 设置异常处理器
        thread.setUncaughtExceptionHandler(exceptionHandler);
    }

    /**
     * 创建游戏平台线程工厂
     *
     * @param namePrefix 线程名前缀
     * @return 平台线程工厂
     */
    public static GameThreadFactory createPlatformThreadFactory(String namePrefix) {
        return new GameThreadFactory(namePrefix, ThreadType.PLATFORM);
    }

    /**
     * 创建游戏虚拟线程工厂（当前为模拟实现）
     *
     * @param namePrefix 线程名前缀
     * @return 虚拟线程工厂
     */
    public static GameThreadFactory createVirtualThreadFactory(String namePrefix) {
        return new GameThreadFactory(namePrefix, ThreadType.VIRTUAL);
    }

    /**
     * 创建高优先级线程工厂
     *
     * @param namePrefix 线程名前缀
     * @return 高优先级线程工厂
     */
    public static GameThreadFactory createHighPriorityThreadFactory(String namePrefix) {
        return new GameThreadFactory(namePrefix, Thread.MAX_PRIORITY, true, ThreadType.PLATFORM);
    }

    /**
     * 创建低优先级线程工厂
     *
     * @param namePrefix 线程名前缀
     * @return 低优先级线程工厂
     */
    public static GameThreadFactory createLowPriorityThreadFactory(String namePrefix) {
        return new GameThreadFactory(namePrefix, Thread.MIN_PRIORITY, true, ThreadType.PLATFORM);
    }

    // ===== Getter方法 =====

    public String getNamePrefix() {
        return namePrefix;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public ThreadType getThreadType() {
        return threadType;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public long getCreatedThreadCount() {
        return threadCounter.get();
    }

    /**
     * 游戏线程未捕获异常处理器
     */
    private static class GameUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        
        private static final Logger log = LoggerFactory.getLogger(GameUncaughtExceptionHandler.class);

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("线程[{}]发生未捕获异常", t.getName(), e);
            
            // 可以在这里添加更多的异常处理逻辑，如：
            // 1. 发送告警通知
            // 2. 记录异常统计
            // 3. 重启关键线程
            // 4. 触发故障转移
            
            // 对于严重异常，可能需要关闭整个应用
            if (e instanceof OutOfMemoryError || e instanceof StackOverflowError) {
                log.error("检测到严重异常，建议重启应用: {}", e.getClass().getSimpleName());
            }
        }
    }

    @Override
    public String toString() {
        return String.format("GameThreadFactory[prefix=%s, priority=%d, daemon=%b, type=%s, created=%d]",
            namePrefix, priority, daemon, threadType, threadCounter.get());
    }
}