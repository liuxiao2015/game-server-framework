/*
 * 文件名: GameTaskScheduler.java
 * 用途: 游戏任务调度器
 * 实现内容:
 *   - 支持延迟任务、周期任务、Cron表达式任务
 *   - schedule()：延迟执行任务
 *   - scheduleAtFixedRate()：固定频率执行
 *   - scheduleWithFixedDelay()：固定延迟执行
 *   - scheduleCron()：支持Cron表达式
 *   - 任务取消和状态查询
 * 技术选型:
 *   - ScheduledExecutorService + 虚拟线程
 *   - Cron表达式解析
 *   - 任务状态管理和监控
 * 依赖关系:
 *   - 依赖ExecutorManager获取调度执行器
 *   - 管理GameTask生命周期
 *   - 提供定时任务统一调度（如：活动刷新、排行榜结算）
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.scheduler;

import com.lx.gameserver.frame.concurrent.executor.ExecutorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 游戏任务调度器
 * <p>
 * 支持延迟任务、周期任务、Cron表达式任务的统一调度管理器。
 * 为游戏定时任务提供统一调度能力，如活动刷新、排行榜结算等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
public class GameTaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GameTaskScheduler.class);

    /**
     * 执行器管理器
     */
    @Autowired
    private ExecutorManager executorManager;

    /**
     * 调度执行器
     */
    private ScheduledExecutorService scheduledExecutor;

    /**
     * 调度任务注册表
     */
    private final Map<Long, ScheduledTaskInfo> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 任务ID生成器
     */
    private final AtomicLong taskIdGenerator = new AtomicLong(0);

    /**
     * Cron表达式验证正则
     */
    private static final Pattern CRON_PATTERN = Pattern.compile(
        "^\\s*($|#|\\w+\\s*=|(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?(?:,(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?)*)\\s+(\\?|\\*|(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?(?:,(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?)*)\\s+(\\?|\\*|(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?(?:,(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?)*|\\?|\\*|(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?(?:,(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?)*)\\s+(\\?|\\*|(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?(?:,(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?)*|\\?|\\*|(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?(?:,(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?)*)(|\\s)+(\\?|\\*|(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?(?:,(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?)*))$"
    );

    /**
     * 调度任务信息
     */
    public static class ScheduledTaskInfo {
        private final long taskId;
        private final String taskName;
        private final ScheduleType scheduleType;
        private final String scheduleExpression;
        private final long createTime;
        private volatile ScheduledFuture<?> future;
        private volatile boolean cancelled = false;
        private volatile int executionCount = 0;
        private volatile long lastExecutionTime;
        private volatile Throwable lastException;

        public ScheduledTaskInfo(long taskId, String taskName, ScheduleType scheduleType, String scheduleExpression) {
            this.taskId = taskId;
            this.taskName = taskName;
            this.scheduleType = scheduleType;
            this.scheduleExpression = scheduleExpression;
            this.createTime = System.currentTimeMillis();
        }

        // Getter和Setter方法
        public long getTaskId() { return taskId; }
        public String getTaskName() { return taskName; }
        public ScheduleType getScheduleType() { return scheduleType; }
        public String getScheduleExpression() { return scheduleExpression; }
        public long getCreateTime() { return createTime; }
        public ScheduledFuture<?> getFuture() { return future; }
        public void setFuture(ScheduledFuture<?> future) { this.future = future; }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        public int getExecutionCount() { return executionCount; }
        public void incrementExecutionCount() { this.executionCount++; }
        public long getLastExecutionTime() { return lastExecutionTime; }
        public void setLastExecutionTime(long lastExecutionTime) { this.lastExecutionTime = lastExecutionTime; }
        public Throwable getLastException() { return lastException; }
        public void setLastException(Throwable lastException) { this.lastException = lastException; }
    }

    /**
     * 调度类型枚举
     */
    public enum ScheduleType {
        /**
         * 一次性延迟任务
         */
        ONCE,
        /**
         * 固定频率重复任务
         */
        FIXED_RATE,
        /**
         * 固定延迟重复任务
         */
        FIXED_DELAY,
        /**
         * Cron表达式任务
         */
        CRON
    }

    /**
     * 初始化调度器
     */
    @PostConstruct
    public void initialize() {
        this.scheduledExecutor = executorManager.getScheduledExecutor();
        if (this.scheduledExecutor == null) {
            throw new IllegalStateException("无法获取调度执行器");
        }
        
        logger.info("游戏任务调度器初始化完成");
    }

    /**
     * 延迟执行任务
     *
     * @param task  任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return 调度任务ID
     */
    public long schedule(Runnable task, long delay, TimeUnit unit) {
        return schedule(task, "schedule-" + taskIdGenerator.incrementAndGet(), delay, unit);
    }

    /**
     * 延迟执行任务
     *
     * @param task     任务
     * @param taskName 任务名称
     * @param delay    延迟时间
     * @param unit     时间单位
     * @return 调度任务ID
     */
    public long schedule(Runnable task, String taskName, long delay, TimeUnit unit) {
        long taskId = taskIdGenerator.incrementAndGet();
        String expression = String.format("delay=%d %s", delay, unit.name());
        
        ScheduledTaskInfo taskInfo = new ScheduledTaskInfo(taskId, taskName, ScheduleType.ONCE, expression);
        
        Runnable wrappedTask = wrapTask(task, taskInfo);
        ScheduledFuture<?> future = scheduledExecutor.schedule(wrappedTask, delay, unit);
        
        taskInfo.setFuture(future);
        scheduledTasks.put(taskId, taskInfo);
        
        logger.debug("调度延迟任务[{}], 延迟:{}ms", taskName, unit.toMillis(delay));
        return taskId;
    }

    /**
     * 固定频率执行任务
     *
     * @param task         任务
     * @param initialDelay 初始延迟
     * @param period       执行周期
     * @param unit         时间单位
     * @return 调度任务ID
     */
    public long scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduleAtFixedRate(task, "fixed-rate-" + taskIdGenerator.incrementAndGet(), 
                                 initialDelay, period, unit);
    }

    /**
     * 固定频率执行任务
     *
     * @param task         任务
     * @param taskName     任务名称
     * @param initialDelay 初始延迟
     * @param period       执行周期
     * @param unit         时间单位
     * @return 调度任务ID
     */
    public long scheduleAtFixedRate(Runnable task, String taskName, long initialDelay, long period, TimeUnit unit) {
        long taskId = taskIdGenerator.incrementAndGet();
        String expression = String.format("fixedRate: initialDelay=%d, period=%d %s", 
                                         initialDelay, period, unit.name());
        
        ScheduledTaskInfo taskInfo = new ScheduledTaskInfo(taskId, taskName, ScheduleType.FIXED_RATE, expression);
        
        Runnable wrappedTask = wrapTask(task, taskInfo);
        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(wrappedTask, initialDelay, period, unit);
        
        taskInfo.setFuture(future);
        scheduledTasks.put(taskId, taskInfo);
        
        logger.debug("调度固定频率任务[{}], 初始延迟:{}ms, 周期:{}ms", 
                    taskName, unit.toMillis(initialDelay), unit.toMillis(period));
        return taskId;
    }

    /**
     * 固定延迟执行任务
     *
     * @param task         任务
     * @param initialDelay 初始延迟
     * @param delay        执行间隔
     * @param unit         时间单位
     * @return 调度任务ID
     */
    public long scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithFixedDelay(task, "fixed-delay-" + taskIdGenerator.incrementAndGet(), 
                                    initialDelay, delay, unit);
    }

    /**
     * 固定延迟执行任务
     *
     * @param task         任务
     * @param taskName     任务名称
     * @param initialDelay 初始延迟
     * @param delay        执行间隔
     * @param unit         时间单位
     * @return 调度任务ID
     */
    public long scheduleWithFixedDelay(Runnable task, String taskName, long initialDelay, long delay, TimeUnit unit) {
        long taskId = taskIdGenerator.incrementAndGet();
        String expression = String.format("fixedDelay: initialDelay=%d, delay=%d %s", 
                                         initialDelay, delay, unit.name());
        
        ScheduledTaskInfo taskInfo = new ScheduledTaskInfo(taskId, taskName, ScheduleType.FIXED_DELAY, expression);
        
        Runnable wrappedTask = wrapTask(task, taskInfo);
        ScheduledFuture<?> future = scheduledExecutor.scheduleWithFixedDelay(wrappedTask, initialDelay, delay, unit);
        
        taskInfo.setFuture(future);
        scheduledTasks.put(taskId, taskInfo);
        
        logger.debug("调度固定延迟任务[{}], 初始延迟:{}ms, 间隔:{}ms", 
                    taskName, unit.toMillis(initialDelay), unit.toMillis(delay));
        return taskId;
    }

    /**
     * Cron表达式调度任务（简化版实现）
     *
     * @param task           任务
     * @param cronExpression Cron表达式
     * @return 调度任务ID
     */
    public long scheduleCron(Runnable task, String cronExpression) {
        return scheduleCron(task, "cron-" + taskIdGenerator.incrementAndGet(), cronExpression);
    }

    /**
     * Cron表达式调度任务（简化版实现）
     *
     * @param task           任务
     * @param taskName       任务名称
     * @param cronExpression Cron表达式
     * @return 调度任务ID
     */
    public long scheduleCron(Runnable task, String taskName, String cronExpression) {
        if (!isValidCronExpression(cronExpression)) {
            throw new IllegalArgumentException("无效的Cron表达式: " + cronExpression);
        }

        long taskId = taskIdGenerator.incrementAndGet();
        ScheduledTaskInfo taskInfo = new ScheduledTaskInfo(taskId, taskName, ScheduleType.CRON, cronExpression);
        
        // 简化的Cron实现：解析常见的Cron表达式
        long nextExecutionDelay = calculateNextExecutionDelay(cronExpression);
        
        Runnable cronTask = new Runnable() {
            @Override
            public void run() {
                try {
                    // 执行实际任务
                    wrapTask(task, taskInfo).run();
                    
                    // 计算下次执行时间并重新调度
                    long nextDelay = calculateNextExecutionDelay(cronExpression);
                    if (nextDelay > 0 && !taskInfo.isCancelled()) {
                        ScheduledFuture<?> nextFuture = scheduledExecutor.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
                        taskInfo.setFuture(nextFuture);
                    }
                } catch (Exception e) {
                    logger.error("Cron任务[{}]执行异常", taskName, e);
                    taskInfo.setLastException(e);
                }
            }
        };
        
        ScheduledFuture<?> future = scheduledExecutor.schedule(cronTask, nextExecutionDelay, TimeUnit.MILLISECONDS);
        taskInfo.setFuture(future);
        scheduledTasks.put(taskId, taskInfo);
        
        logger.debug("调度Cron任务[{}], 表达式:{}, 下次执行延迟:{}ms", 
                    taskName, cronExpression, nextExecutionDelay);
        return taskId;
    }

    /**
     * 包装任务，添加监控和异常处理
     */
    private Runnable wrapTask(Runnable task, ScheduledTaskInfo taskInfo) {
        return () -> {
            long startTime = System.currentTimeMillis();
            try {
                task.run();
                taskInfo.incrementExecutionCount();
                taskInfo.setLastExecutionTime(startTime);
                logger.debug("调度任务[{}]执行完成，耗时:{}ms", 
                           taskInfo.getTaskName(), System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                taskInfo.setLastException(e);
                logger.error("调度任务[{}]执行失败，耗时:{}ms", 
                           taskInfo.getTaskName(), System.currentTimeMillis() - startTime, e);
            }
        };
    }

    /**
     * 取消调度任务
     *
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    public boolean cancelTask(long taskId) {
        ScheduledTaskInfo taskInfo = scheduledTasks.get(taskId);
        if (taskInfo == null) {
            return false;
        }

        taskInfo.setCancelled(true);
        ScheduledFuture<?> future = taskInfo.getFuture();
        boolean cancelled = future != null && future.cancel(false);
        
        if (cancelled) {
            scheduledTasks.remove(taskId);
            logger.info("调度任务[{}]已取消", taskInfo.getTaskName());
        }
        
        return cancelled;
    }

    /**
     * 获取任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    public String getTaskStatus(long taskId) {
        ScheduledTaskInfo taskInfo = scheduledTasks.get(taskId);
        if (taskInfo == null) {
            return "任务不存在";
        }

        ScheduledFuture<?> future = taskInfo.getFuture();
        String status = future != null ? 
            (future.isDone() ? "已完成" : (future.isCancelled() ? "已取消" : "运行中")) : "未知";

        return String.format("Task[id=%d, name=%s, type=%s, status=%s, executions=%d, lastExec=%s]",
            taskInfo.getTaskId(), taskInfo.getTaskName(), taskInfo.getScheduleType(), status,
            taskInfo.getExecutionCount(), 
            taskInfo.getLastExecutionTime() > 0 ? 
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(taskInfo.getLastExecutionTime()), 
                                      ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "从未执行");
    }

    /**
     * 获取所有任务状态
     */
    public List<String> getAllTaskStatus() {
        List<String> statusList = new ArrayList<>();
        for (ScheduledTaskInfo taskInfo : scheduledTasks.values()) {
            statusList.add(getTaskStatus(taskInfo.getTaskId()));
        }
        return statusList;
    }

    /**
     * 验证Cron表达式（简化版）
     */
    private boolean isValidCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }
        
        // 简单的验证逻辑，支持标准的6段式Cron表达式
        String[] parts = cronExpression.trim().split("\\s+");
        return parts.length == 6;
    }

    /**
     * 计算下次执行延迟（简化版Cron解析）
     */
    private long calculateNextExecutionDelay(String cronExpression) {
        // 这里是简化的实现，实际项目中建议使用专业的Cron库如Quartz
        // 暂时返回固定的1分钟延迟作为示例
        return 60000; // 60秒
    }

    /**
     * 获取调度器状态
     */
    public String getSchedulerStatus() {
        return String.format("GameTaskScheduler[activeTasks=%d, totalScheduled=%d]",
            scheduledTasks.size(), taskIdGenerator.get());
    }

    /**
     * 关闭调度器
     */
    @PreDestroy
    public void shutdown() {
        logger.info("游戏任务调度器开始关闭...");
        
        // 取消所有任务
        for (ScheduledTaskInfo taskInfo : scheduledTasks.values()) {
            ScheduledFuture<?> future = taskInfo.getFuture();
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }
        }
        
        scheduledTasks.clear();
        logger.info("游戏任务调度器已关闭");
    }
}