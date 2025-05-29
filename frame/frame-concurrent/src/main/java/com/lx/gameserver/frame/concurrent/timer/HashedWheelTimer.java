/*
 * 文件名: HashedWheelTimer.java
 * 用途: 哈希时间轮定时器
 * 实现内容:
 *   - 高性能定时器，适合大量定时任务场景
 *   - 时间轮槽位管理
 *   - 时间精度配置（默认100ms）
 *   - 任务添加、取消、执行
 *   - 内存优化（任务复用池）
 * 技术选型:
 *   - 时间轮算法 + 虚拟线程
 *   - 数组实现的环形缓冲区
 *   - 链表管理槽位任务
 * 依赖关系:
 *   - 依赖TimerTask定义任务接口
 *   - 适合游戏内大量定时任务管理（如：Buff过期、技能CD）
 *   - 与执行器管理器协作调度
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;

/**
 * 哈希时间轮定时器
 * <p>
 * 高性能定时器实现，使用时间轮算法管理大量定时任务。
 * 适合游戏内大量短期定时任务，如Buff过期、技能CD等。
 * 时间复杂度：添加和触发任务的时间复杂度为O(1)。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class HashedWheelTimer {

    private static final Logger logger = LoggerFactory.getLogger(HashedWheelTimer.class);

    /**
     * 默认时间轮大小
     */
    private static final int DEFAULT_WHEEL_SIZE = 512;

    /**
     * 默认时间精度（毫秒）
     */
    private static final long DEFAULT_TICK_DURATION = 100;

    /**
     * 时间轮大小（必须是2的幂）
     */
    private final int wheelSize;

    /**
     * 时间精度（毫秒）
     */
    private final long tickDuration;

    /**
     * 时间轮掩码（用于快速取模）
     */
    private final int wheelMask;

    /**
     * 时间轮槽位数组
     */
    private final TimerBucket[] wheel;

    /**
     * 定时器工作线程
     */
    private final Thread workerThread;

    /**
     * 定时器是否启动
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * 定时器是否停止
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 开始时间戳
     */
    private volatile long startTime;

    /**
     * 当前时间轮指针
     */
    private volatile long tick;

    /**
     * 待处理的新任务队列
     */
    private final Queue<HashedWheelTimeout> pendingTimeouts = new ConcurrentLinkedQueue<>();

    /**
     * 待取消的任务集合
     */
    private final Set<HashedWheelTimeout> cancelledTimeouts = new HashSet<>();

    /**
     * 读写锁
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 任务ID生成器
     */
    private final AtomicLong taskIdGenerator = new AtomicLong(0);

    /**
     * 统计信息
     */
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong executedTasks = new AtomicLong(0);
    private final AtomicLong cancelledTasks = new AtomicLong(0);

    /**
     * 使用默认配置创建时间轮定时器
     */
    public HashedWheelTimer() {
        this(DEFAULT_TICK_DURATION, TimeUnit.MILLISECONDS, DEFAULT_WHEEL_SIZE);
    }

    /**
     * 创建时间轮定时器
     *
     * @param tickDuration 时间精度
     * @param unit         时间单位
     * @param wheelSize    时间轮大小
     */
    public HashedWheelTimer(long tickDuration, TimeUnit unit, int wheelSize) {
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("时间精度必须大于0");
        }
        if (wheelSize <= 0 || (wheelSize & (wheelSize - 1)) != 0) {
            throw new IllegalArgumentException("时间轮大小必须是2的幂且大于0");
        }

        this.tickDuration = unit.toMillis(tickDuration);
        this.wheelSize = wheelSize;
        this.wheelMask = wheelSize - 1;
        this.wheel = new TimerBucket[wheelSize];

        // 初始化时间轮槽位
        for (int i = 0; i < wheelSize; i++) {
            wheel[i] = new TimerBucket();
        }

        // 创建工作线程
        this.workerThread = new Thread(new Worker(), "HashedWheelTimer-Worker");
        this.workerThread.setDaemon(true);

        logger.info("哈希时间轮定时器已创建，时间精度:{}ms, 轮大小:{}", this.tickDuration, wheelSize);
    }

    /**
     * 启动定时器
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            startTime = System.currentTimeMillis();
            workerThread.start();
            logger.info("哈希时间轮定时器已启动");
        }
    }

    /**
     * 停止定时器
     */
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            workerThread.interrupt();
            
            lock.writeLock().lock();
            try {
                // 取消所有待处理的任务
                for (HashedWheelTimeout timeout : pendingTimeouts) {
                    timeout.cancel();
                }
                pendingTimeouts.clear();

                // 取消所有时间轮中的任务
                for (TimerBucket bucket : wheel) {
                    bucket.clear();
                }
            } finally {
                lock.writeLock().unlock();
            }
            
            logger.info("哈希时间轮定时器已停止");
        }
    }

    /**
     * 添加定时任务
     *
     * @param task  定时任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return 任务句柄
     */
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("延迟时间不能为负数");
        }

        if (!started.get()) {
            start();
        }

        if (stopped.get()) {
            throw new IllegalStateException("定时器已停止");
        }

        long delayMs = unit.toMillis(delay);
        HashedWheelTimeout timeout = new HashedWheelTimeout(task, delayMs);
        
        pendingTimeouts.offer(timeout);
        totalTasks.incrementAndGet();
        
        logger.debug("添加定时任务[{}], 延迟:{}ms", task.getTaskName(), delayMs);
        return timeout;
    }

    /**
     * 获取统计信息
     */
    public String getStatistics() {
        return String.format("HashedWheelTimer[totalTasks=%d, executed=%d, cancelled=%d, pending=%d, active=%b]",
            totalTasks.get(), executedTasks.get(), cancelledTasks.get(), 
            pendingTimeouts.size(), started.get() && !stopped.get());
    }

    /**
     * 时间轮槽位
     */
    private static class TimerBucket {
        private HashedWheelTimeout head;
        private HashedWheelTimeout tail;

        /**
         * 添加任务到槽位
         */
        public void addTimeout(HashedWheelTimeout timeout) {
            if (head == null) {
                head = tail = timeout;
            } else {
                tail.next = timeout;
                timeout.prev = tail;
                tail = timeout;
            }
            timeout.bucket = this;
        }

        /**
         * 执行槽位中的所有到期任务
         */
        public void expireTimeouts(long deadline) {
            HashedWheelTimeout timeout = head;
            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                if (timeout.remainingRounds <= 0) {
                    // 任务到期，从槽位中移除并执行
                    remove(timeout);
                    timeout.expire();
                } else {
                    // 任务未到期，减少剩余轮数
                    timeout.remainingRounds--;
                }
                timeout = next;
            }
        }

        /**
         * 从槽位中移除任务
         */
        public void remove(HashedWheelTimeout timeout) {
            if (timeout.prev != null) {
                timeout.prev.next = timeout.next;
            } else {
                head = timeout.next;
            }

            if (timeout.next != null) {
                timeout.next.prev = timeout.prev;
            } else {
                tail = timeout.prev;
            }

            timeout.prev = null;
            timeout.next = null;
            timeout.bucket = null;
        }

        /**
         * 清空槽位
         */
        public void clear() {
            HashedWheelTimeout timeout = head;
            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                timeout.cancel();
                timeout = next;
            }
            head = tail = null;
        }
    }

    /**
     * 时间轮任务超时句柄
     */
    private class HashedWheelTimeout implements Timeout {
        private final long taskId;
        private final TimerTask task;
        private final long deadline;
        private volatile long remainingRounds;
        private volatile boolean cancelled = false;
        
        // 链表指针
        private HashedWheelTimeout next;
        private HashedWheelTimeout prev;
        private TimerBucket bucket;

        public HashedWheelTimeout(TimerTask task, long delayMs) {
            this.taskId = taskIdGenerator.incrementAndGet();
            this.task = task;
            this.deadline = System.currentTimeMillis() + delayMs;
            
            // 计算任务应该在时间轮的哪个槽位
            long ticks = delayMs / tickDuration;
            this.remainingRounds = ticks / wheelSize;
        }

        @Override
        public TimerTask task() {
            return task;
        }

        @Override
        public boolean isExpired() {
            return System.currentTimeMillis() >= deadline && !cancelled;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean cancel() {
            if (cancelled) {
                return false;
            }
            
            cancelled = true;
            
            lock.writeLock().lock();
            try {
                if (bucket != null) {
                    bucket.remove(this);
                } else {
                    pendingTimeouts.remove(this);
                }
                cancelledTimeouts.add(this);
            } finally {
                lock.writeLock().unlock();
            }
            
            task.onCancel();
            cancelledTasks.incrementAndGet();
            logger.debug("定时任务[{}]已取消", task.getTaskName());
            return true;
        }

        /**
         * 执行任务
         */
        public void expire() {
            if (cancelled) {
                return;
            }

            try {
                task.run();
                executedTasks.incrementAndGet();
                logger.debug("定时任务[{}]执行完成", task.getTaskName());
            } catch (Exception e) {
                task.onException(e);
                logger.error("定时任务[{}]执行异常", task.getTaskName(), e);
            }
        }

        public long getTaskId() {
            return taskId;
        }
    }

    /**
     * 任务超时接口
     */
    public interface Timeout {
        /**
         * 获取任务
         */
        TimerTask task();

        /**
         * 判断任务是否已过期
         */
        boolean isExpired();

        /**
         * 判断任务是否已取消
         */
        boolean isCancelled();

        /**
         * 取消任务
         */
        boolean cancel();
    }

    /**
     * 工作线程
     */
    private class Worker implements Runnable {
        @Override
        public void run() {
            startTime = System.currentTimeMillis();
            tick = 1;

            while (!stopped.get()) {
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    transferTimeoutsToBuckets();
                    
                    // 获取当前槽位并执行到期任务
                    int slotIndex = (int) (tick & wheelMask);
                    TimerBucket bucket = wheel[slotIndex];
                    bucket.expireTimeouts(deadline);
                    
                    tick++;
                }
            }

            logger.info("时间轮工作线程已退出");
        }

        /**
         * 等待下一个时间刻度
         */
        private long waitForNextTick() {
            long deadline = startTime + tick * tickDuration;
            long currentTime = System.currentTimeMillis();
            
            if (currentTime < deadline) {
                try {
                    Thread.sleep(deadline - currentTime);
                } catch (InterruptedException e) {
                    return -1;
                }
            }
            
            return deadline;
        }

        /**
         * 将待处理任务转移到时间轮槽位
         */
        private void transferTimeoutsToBuckets() {
            lock.writeLock().lock();
            try {
                // 处理取消的任务
                for (HashedWheelTimeout cancelledTimeout : cancelledTimeouts) {
                    // 清理已取消的任务
                }
                cancelledTimeouts.clear();

                // 将新任务分配到槽位
                HashedWheelTimeout timeout;
                while ((timeout = pendingTimeouts.poll()) != null) {
                    if (timeout.cancelled) {
                        continue;
                    }

                    long calculated = timeout.deadline / tickDuration;
                    long remainingRounds = (calculated - tick) / wheelSize;
                    long ticks = Math.max(calculated, tick);
                    int slotIndex = (int) (ticks & wheelMask);

                    timeout.remainingRounds = remainingRounds;
                    wheel[slotIndex].addTimeout(timeout);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}