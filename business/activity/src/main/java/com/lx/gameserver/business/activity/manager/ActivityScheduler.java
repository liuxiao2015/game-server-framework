/*
 * 文件名: ActivityScheduler.java
 * 用途: 活动调度器
 * 实现内容:
 *   - 定时任务管理和执行
 *   - 活动开启/关闭调度
 *   - 周期活动调度处理
 *   - 动态调度调整和日志记录
 * 技术选型:
 *   - Spring Task Scheduler
 *   - Quartz调度框架
 *   - 线程池管理
 *   - 异常处理机制
 * 依赖关系:
 *   - 依赖ActivityManager
 *   - 被活动服务使用
 *   - 集成Spring调度框架
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.manager;

import com.lx.gameserver.business.activity.core.Activity;
import com.lx.gameserver.business.activity.core.ActivityContext;
import com.lx.gameserver.business.activity.core.ActivityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 活动调度器
 * <p>
 * 负责活动的定时调度管理，包括定时启动、结束、状态检查等。
 * 支持周期性活动的自动重置和多种调度策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Service
public class ActivityScheduler {
    
    @Autowired
    private ActivityManager activityManager;
    
    @Autowired
    private TaskScheduler taskScheduler;
    
    /** 调度任务存储 */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    /** 活动更新任务存储 */
    private final Map<Long, ScheduledFuture<?>> updateTasks = new ConcurrentHashMap<>();
    
    /** 调度器线程池 */
    private ScheduledExecutorService schedulerExecutor;
    
    /** 更新线程池 */
    private ScheduledExecutorService updateExecutor;
    
    /** 调度器配置 */
    private SchedulerConfig config = new SchedulerConfig();
    
    /** 调度器启动状态 */
    private volatile boolean started = false;
    
    /**
     * 初始化调度器
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化活动调度器");
        
        // 创建调度线程池
        schedulerExecutor = Executors.newScheduledThreadPool(
                config.corePoolSize, 
                r -> new Thread(r, "activity-scheduler-" + System.currentTimeMillis())
        );
        
        // 创建更新线程池
        updateExecutor = Executors.newScheduledThreadPool(
                config.updatePoolSize, 
                r -> new Thread(r, "activity-update-" + System.currentTimeMillis())
        );
        
        started = true;
        
        log.info("活动调度器初始化完成");
    }
    
    /**
     * 销毁调度器
     */
    @PreDestroy
    public void destroy() {
        log.info("销毁活动调度器");
        
        started = false;
        
        // 取消所有调度任务
        cancelAllTasks();
        
        // 关闭线程池
        shutdownExecutor(schedulerExecutor, "scheduler");
        shutdownExecutor(updateExecutor, "update");
        
        log.info("活动调度器销毁完成");
    }
    
    /**
     * 调度活动
     *
     * @param activity 活动实例
     * @return 调度是否成功
     */
    public boolean scheduleActivity(Activity activity) {
        if (activity == null || activity.getActivityId() == null) {
            log.warn("无法调度空活动或缺少ID的活动");
            return false;
        }
        
        if (!started) {
            log.warn("调度器未启动，无法调度活动: {}", activity.getActivityId());
            return false;
        }
        
        try {
            // 取消已存在的调度
            cancelActivitySchedule(activity.getActivityId());
            
            // 调度开始任务
            scheduleStartTask(activity);
            
            // 调度结束任务
            scheduleEndTask(activity);
            
            // 调度更新任务
            scheduleUpdateTask(activity);
            
            log.info("成功调度活动: {} (ID: {})", activity.getActivityName(), activity.getActivityId());
            return true;
            
        } catch (Exception e) {
            log.error("调度活动失败: {} (ID: {})", activity.getActivityName(), activity.getActivityId(), e);
            return false;
        }
    }
    
    /**
     * 取消活动调度
     *
     * @param activityId 活动ID
     */
    public void cancelActivitySchedule(Long activityId) {
        if (activityId == null) {
            return;
        }
        
        // 取消调度任务
        ScheduledFuture<?> scheduledTask = scheduledTasks.remove(activityId);
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
            log.debug("取消活动调度任务: {}", activityId);
        }
        
        // 取消更新任务
        ScheduledFuture<?> updateTask = updateTasks.remove(activityId);
        if (updateTask != null && !updateTask.isDone()) {
            updateTask.cancel(false);
            log.debug("取消活动更新任务: {}", activityId);
        }
    }
    
    /**
     * 手动触发活动开始
     *
     * @param activityId 活动ID
     * @return 触发是否成功
     */
    public boolean triggerActivityStart(Long activityId) {
        Activity activity = activityManager.getActivity(activityId);
        if (activity == null) {
            log.warn("要触发开始的活动不存在: {}", activityId);
            return false;
        }
        
        return activityManager.startActivity(activityId);
    }
    
    /**
     * 手动触发活动结束
     *
     * @param activityId 活动ID
     * @param reason     结束原因
     * @return 触发是否成功
     */
    public boolean triggerActivityEnd(Long activityId, String reason) {
        Activity activity = activityManager.getActivity(activityId);
        if (activity == null) {
            log.warn("要触发结束的活动不存在: {}", activityId);
            return false;
        }
        
        return activityManager.stopActivity(activityId, reason);
    }
    
    /**
     * 定时状态检查任务
     */
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void scheduledStatusCheck() {
        if (!started) {
            return;
        }
        
        try {
            log.debug("执行定时活动状态检查");
            activityManager.performStatusCheck();
            
            // 清理已完成的任务
            cleanupCompletedTasks();
            
        } catch (Exception e) {
            log.error("定时状态检查失败", e);
        }
    }
    
    /**
     * 周期性活动重置任务
     */
    @Scheduled(cron = "0 0 0 * * ?") // 每天00:00执行
    public void dailyActivityReset() {
        if (!started) {
            return;
        }
        
        log.info("执行每日活动重置");
        
        try {
            resetPeriodicActivities(ActivityType.DAILY);
        } catch (Exception e) {
            log.error("每日活动重置失败", e);
        }
    }
    
    /**
     * 周重置任务
     */
    @Scheduled(cron = "0 0 0 ? * MON") // 每周一00:00执行
    public void weeklyActivityReset() {
        if (!started) {
            return;
        }
        
        log.info("执行每周活动重置");
        
        try {
            resetPeriodicActivities(ActivityType.WEEKLY);
        } catch (Exception e) {
            log.error("每周活动重置失败", e);
        }
    }
    
    /**
     * 获取调度器状态
     *
     * @return 调度器状态
     */
    public SchedulerStatus getStatus() {
        SchedulerStatus status = new SchedulerStatus();
        status.started = this.started;
        status.scheduledTaskCount = scheduledTasks.size();
        status.updateTaskCount = updateTasks.size();
        status.config = this.config;
        
        return status;
    }
    
    /**
     * 更新调度器配置
     *
     * @param config 新配置
     */
    public void updateConfig(SchedulerConfig config) {
        if (config == null) {
            return;
        }
        
        this.config = config;
        log.info("更新调度器配置: {}", config);
    }
    
    // ===== 私有方法 =====
    
    /**
     * 调度开始任务
     *
     * @param activity 活动实例
     */
    private void scheduleStartTask(Activity activity) {
        if (activity.getStartTime() == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long startTime = activity.getStartTime();
        
        if (startTime <= currentTime) {
            // 立即执行
            schedulerExecutor.execute(() -> {
                try {
                    activityManager.startActivity(activity.getActivityId());
                } catch (Exception e) {
                    log.error("执行活动开始任务失败: {} (ID: {})", 
                            activity.getActivityName(), activity.getActivityId(), e);
                }
            });
        } else {
            // 延迟执行
            long delay = startTime - currentTime;
            ScheduledFuture<?> future = schedulerExecutor.schedule(() -> {
                try {
                    activityManager.startActivity(activity.getActivityId());
                } catch (Exception e) {
                    log.error("执行活动开始任务失败: {} (ID: {})", 
                            activity.getActivityName(), activity.getActivityId(), e);
                }
            }, delay, TimeUnit.MILLISECONDS);
            
            scheduledTasks.put(activity.getActivityId(), future);
            
            log.debug("调度活动开始任务: {} (ID: {}), 延迟: {}ms", 
                    activity.getActivityName(), activity.getActivityId(), delay);
        }
    }
    
    /**
     * 调度结束任务
     *
     * @param activity 活动实例
     */
    private void scheduleEndTask(Activity activity) {
        if (activity.getEndTime() == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long endTime = activity.getEndTime();
        
        if (endTime <= currentTime) {
            // 立即执行
            schedulerExecutor.execute(() -> {
                try {
                    activityManager.stopActivity(activity.getActivityId(), "time_expired");
                } catch (Exception e) {
                    log.error("执行活动结束任务失败: {} (ID: {})", 
                            activity.getActivityName(), activity.getActivityId(), e);
                }
            });
        } else {
            // 延迟执行
            long delay = endTime - currentTime;
            ScheduledFuture<?> future = schedulerExecutor.schedule(() -> {
                try {
                    activityManager.stopActivity(activity.getActivityId(), "time_expired");
                } catch (Exception e) {
                    log.error("执行活动结束任务失败: {} (ID: {})", 
                            activity.getActivityName(), activity.getActivityId(), e);
                }
            }, delay, TimeUnit.MILLISECONDS);
            
            // 使用复合key来区分开始和结束任务
            scheduledTasks.put(activity.getActivityId() + 1000000000L, future);
            
            log.debug("调度活动结束任务: {} (ID: {}), 延迟: {}ms", 
                    activity.getActivityName(), activity.getActivityId(), delay);
        }
    }
    
    /**
     * 调度更新任务
     *
     * @param activity 活动实例
     */
    private void scheduleUpdateTask(Activity activity) {
        if (!activity.isRunning()) {
            return;
        }
        
        ScheduledFuture<?> future = updateExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (activity.isRunning()) {
                    ActivityContext context = new ActivityContext(
                            activity.getActivityId(), null, activity.getActivityType());
                    activity.update(context, config.updateInterval);
                }
            } catch (Exception e) {
                log.error("执行活动更新任务失败: {} (ID: {})", 
                        activity.getActivityName(), activity.getActivityId(), e);
            }
        }, config.updateInterval, config.updateInterval, TimeUnit.MILLISECONDS);
        
        updateTasks.put(activity.getActivityId(), future);
        
        log.debug("调度活动更新任务: {} (ID: {}), 间隔: {}ms", 
                activity.getActivityName(), activity.getActivityId(), config.updateInterval);
    }
    
    /**
     * 重置周期性活动
     *
     * @param activityType 活动类型
     */
    private void resetPeriodicActivities(ActivityType activityType) {
        if (!activityType.isPeriodic()) {
            return;
        }
        
        activityManager.getActivitiesByType(activityType).forEach(activity -> {
            try {
                ActivityContext context = new ActivityContext(
                        activity.getActivityId(), null, activity.getActivityType());
                activity.reset(context);
                
                log.info("重置周期性活动: {} (ID: {})", 
                        activity.getActivityName(), activity.getActivityId());
                        
            } catch (Exception e) {
                log.error("重置周期性活动失败: {} (ID: {})", 
                        activity.getActivityName(), activity.getActivityId(), e);
            }
        });
    }
    
    /**
     * 清理已完成的任务
     */
    private void cleanupCompletedTasks() {
        // 清理调度任务
        scheduledTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
        
        // 清理更新任务
        updateTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
    }
    
    /**
     * 取消所有任务
     */
    private void cancelAllTasks() {
        // 取消所有调度任务
        scheduledTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        scheduledTasks.clear();
        
        // 取消所有更新任务
        updateTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        updateTasks.clear();
    }
    
    /**
     * 关闭线程池
     *
     * @param executor 线程池
     * @param name     线程池名称
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("强制关闭{}线程池", name);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("{}线程池关闭被中断", name);
            }
        }
    }
    
    /**
     * 调度器配置
     */
    public static class SchedulerConfig {
        /** 核心线程池大小 */
        public int corePoolSize = 4;
        
        /** 更新线程池大小 */
        public int updatePoolSize = 8;
        
        /** 更新间隔（毫秒） */
        public long updateInterval = 60000; // 1分钟
        
        /** 状态检查间隔（毫秒） */
        public long checkInterval = 60000; // 1分钟
        
        @Override
        public String toString() {
            return String.format("SchedulerConfig{corePoolSize=%d, updatePoolSize=%d, updateInterval=%d, checkInterval=%d}", 
                    corePoolSize, updatePoolSize, updateInterval, checkInterval);
        }
    }
    
    /**
     * 调度器状态
     */
    public static class SchedulerStatus {
        public boolean started;
        public int scheduledTaskCount;
        public int updateTaskCount;
        public SchedulerConfig config;
    }
}