/*
 * 文件名: ActivityManager.java
 * 用途: 活动管理器
 * 实现内容:
 *   - 活动注册和注销管理
 *   - 活动调度和状态检查
 *   - 活动列表维护和查询
 *   - 活动优先级管理
 * 技术选型:
 *   - Spring Service组件
 *   - 线程安全的集合类
 *   - 定时任务调度
 *   - 事件发布机制
 * 依赖关系:
 *   - 依赖Activity核心类
 *   - 被ActivityScheduler使用
 *   - 被活动服务调用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.manager;

import com.lx.gameserver.business.activity.core.Activity;
import com.lx.gameserver.business.activity.core.ActivityContext;
import com.lx.gameserver.business.activity.core.ActivityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 活动管理器
 * <p>
 * 负责活动的全生命周期管理，包括注册、调度、状态维护等。
 * 提供线程安全的活动操作和查询接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Service
public class ActivityManager {
    
    /** 活动存储映射 */
    private final Map<Long, Activity> activities = new ConcurrentHashMap<>();
    
    /** 类型映射索引 */
    private final Map<ActivityType, Set<Long>> typeIndex = new ConcurrentHashMap<>();
    
    /** 状态映射索引 */
    private final Map<Activity.ActivityStatus, Set<Long>> statusIndex = new ConcurrentHashMap<>();
    
    /** 优先级排序列表 */
    private final List<Long> priorityList = new ArrayList<>();
    
    /** 读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /** 最大并发活动数 */
    private static final int MAX_CONCURRENT_ACTIVITIES = 20;
    
    /**
     * 注册活动
     *
     * @param activity 活动实例
     * @return 注册是否成功
     */
    public boolean registerActivity(Activity activity) {
        if (activity == null || activity.getActivityId() == null) {
            log.warn("无法注册空活动或缺少活动ID的活动");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            // 检查活动数量限制
            if (activities.size() >= MAX_CONCURRENT_ACTIVITIES) {
                log.warn("活动数量已达上限，无法注册新活动: {}", activity.getActivityId());
                return false;
            }
            
            // 检查是否已存在
            if (activities.containsKey(activity.getActivityId())) {
                log.warn("活动已存在，无法重复注册: {}", activity.getActivityId());
                return false;
            }
            
            // 注册活动
            activities.put(activity.getActivityId(), activity);
            
            // 更新索引
            updateTypeIndex(activity.getActivityType(), activity.getActivityId(), true);
            updateStatusIndex(activity.getStatus(), activity.getActivityId(), true);
            
            // 更新优先级列表
            updatePriorityList(activity);
            
            log.info("成功注册活动: {} (ID: {}, Type: {})", 
                    activity.getActivityName(), activity.getActivityId(), activity.getActivityType());
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 注销活动
     *
     * @param activityId 活动ID
     * @return 注销是否成功
     */
    public boolean unregisterActivity(Long activityId) {
        if (activityId == null) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            Activity activity = activities.remove(activityId);
            if (activity == null) {
                log.warn("要注销的活动不存在: {}", activityId);
                return false;
            }
            
            // 更新索引
            updateTypeIndex(activity.getActivityType(), activityId, false);
            updateStatusIndex(activity.getStatus(), activityId, false);
            
            // 从优先级列表中移除
            priorityList.remove(activityId);
            
            log.info("成功注销活动: {} (ID: {})", activity.getActivityName(), activityId);
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取活动
     *
     * @param activityId 活动ID
     * @return 活动实例
     */
    public Activity getActivity(Long activityId) {
        if (activityId == null) {
            return null;
        }
        
        lock.readLock().lock();
        try {
            return activities.get(activityId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有活动
     *
     * @return 活动列表
     */
    public List<Activity> getAllActivities() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(activities.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 根据类型获取活动
     *
     * @param activityType 活动类型
     * @return 活动列表
     */
    public List<Activity> getActivitiesByType(ActivityType activityType) {
        if (activityType == null) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            Set<Long> activityIds = typeIndex.get(activityType);
            if (activityIds == null || activityIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            return activityIds.stream()
                    .map(activities::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 根据状态获取活动
     *
     * @param status 活动状态
     * @return 活动列表
     */
    public List<Activity> getActivitiesByStatus(Activity.ActivityStatus status) {
        if (status == null) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            Set<Long> activityIds = statusIndex.get(status);
            if (activityIds == null || activityIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            return activityIds.stream()
                    .map(activities::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取活跃的活动
     *
     * @return 活跃活动列表
     */
    public List<Activity> getActiveActivities() {
        return getActivitiesByStatus(Activity.ActivityStatus.ACTIVE);
    }
    
    /**
     * 根据优先级获取活动
     *
     * @return 按优先级排序的活动列表
     */
    public List<Activity> getActivitiesByPriority() {
        lock.readLock().lock();
        try {
            return priorityList.stream()
                    .map(activities::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 启动活动
     *
     * @param activityId 活动ID
     * @return 启动是否成功
     */
    public boolean startActivity(Long activityId) {
        Activity activity = getActivity(activityId);
        if (activity == null) {
            log.warn("要启动的活动不存在: {}", activityId);
            return false;
        }
        
        try {
            ActivityContext context = createActivityContext(activity);
            activity.start(context);
            
            // 更新状态索引
            updateActivityStatus(activity, Activity.ActivityStatus.ACTIVE);
            
            log.info("成功启动活动: {} (ID: {})", activity.getActivityName(), activityId);
            return true;
            
        } catch (Exception e) {
            log.error("启动活动失败: {} (ID: {})", activity.getActivityName(), activityId, e);
            return false;
        }
    }
    
    /**
     * 停止活动
     *
     * @param activityId 活动ID
     * @param reason     停止原因
     * @return 停止是否成功
     */
    public boolean stopActivity(Long activityId, String reason) {
        Activity activity = getActivity(activityId);
        if (activity == null) {
            log.warn("要停止的活动不存在: {}", activityId);
            return false;
        }
        
        try {
            ActivityContext context = createActivityContext(activity);
            activity.end(context, reason);
            
            // 更新状态索引
            Activity.ActivityStatus newStatus = "cancelled".equals(reason) ? 
                    Activity.ActivityStatus.CANCELLED : Activity.ActivityStatus.ENDED;
            updateActivityStatus(activity, newStatus);
            
            log.info("成功停止活动: {} (ID: {}), 原因: {}", activity.getActivityName(), activityId, reason);
            return true;
            
        } catch (Exception e) {
            log.error("停止活动失败: {} (ID: {})", activity.getActivityName(), activityId, e);
            return false;
        }
    }
    
    /**
     * 暂停活动
     *
     * @param activityId 活动ID
     * @return 暂停是否成功
     */
    public boolean pauseActivity(Long activityId) {
        Activity activity = getActivity(activityId);
        if (activity == null) {
            log.warn("要暂停的活动不存在: {}", activityId);
            return false;
        }
        
        try {
            ActivityContext context = createActivityContext(activity);
            activity.pause(context);
            
            // 更新状态索引
            updateActivityStatus(activity, Activity.ActivityStatus.PAUSED);
            
            log.info("成功暂停活动: {} (ID: {})", activity.getActivityName(), activityId);
            return true;
            
        } catch (Exception e) {
            log.error("暂停活动失败: {} (ID: {})", activity.getActivityName(), activityId, e);
            return false;
        }
    }
    
    /**
     * 恢复活动
     *
     * @param activityId 活动ID
     * @return 恢复是否成功
     */
    public boolean resumeActivity(Long activityId) {
        Activity activity = getActivity(activityId);
        if (activity == null) {
            log.warn("要恢复的活动不存在: {}", activityId);
            return false;
        }
        
        try {
            ActivityContext context = createActivityContext(activity);
            activity.resume(context);
            
            // 更新状态索引
            updateActivityStatus(activity, Activity.ActivityStatus.ACTIVE);
            
            log.info("成功恢复活动: {} (ID: {})", activity.getActivityName(), activityId);
            return true;
            
        } catch (Exception e) {
            log.error("恢复活动失败: {} (ID: {})", activity.getActivityName(), activityId, e);
            return false;
        }
    }
    
    /**
     * 执行活动状态检查
     */
    public void performStatusCheck() {
        log.debug("开始执行活动状态检查");
        
        List<Activity> activities = getAllActivities();
        long currentTime = System.currentTimeMillis();
        
        for (Activity activity : activities) {
            try {
                checkActivityStatus(activity, currentTime);
            } catch (Exception e) {
                log.error("检查活动状态时发生异常: {} (ID: {})", 
                        activity.getActivityName(), activity.getActivityId(), e);
            }
        }
        
        log.debug("活动状态检查完成，共检查 {} 个活动", activities.size());
    }
    
    /**
     * 获取活动统计信息
     *
     * @return 统计信息
     */
    public ActivityManagerStats getStats() {
        lock.readLock().lock();
        try {
            ActivityManagerStats stats = new ActivityManagerStats();
            stats.totalActivities = activities.size();
            stats.activeActivities = getActivitiesByStatus(Activity.ActivityStatus.ACTIVE).size();
            stats.pausedActivities = getActivitiesByStatus(Activity.ActivityStatus.PAUSED).size();
            stats.endedActivities = getActivitiesByStatus(Activity.ActivityStatus.ENDED).size();
            stats.typeDistribution = new HashMap<>();
            
            for (Map.Entry<ActivityType, Set<Long>> entry : typeIndex.entrySet()) {
                stats.typeDistribution.put(entry.getKey(), entry.getValue().size());
            }
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===== 私有方法 =====
    
    /**
     * 更新类型索引
     *
     * @param activityType 活动类型
     * @param activityId   活动ID
     * @param add          是否添加
     */
    private void updateTypeIndex(ActivityType activityType, Long activityId, boolean add) {
        if (activityType == null || activityId == null) {
            return;
        }
        
        typeIndex.computeIfAbsent(activityType, k -> ConcurrentHashMap.newKeySet());
        
        if (add) {
            typeIndex.get(activityType).add(activityId);
        } else {
            typeIndex.get(activityType).remove(activityId);
        }
    }
    
    /**
     * 更新状态索引
     *
     * @param status     活动状态
     * @param activityId 活动ID
     * @param add        是否添加
     */
    private void updateStatusIndex(Activity.ActivityStatus status, Long activityId, boolean add) {
        if (status == null || activityId == null) {
            return;
        }
        
        statusIndex.computeIfAbsent(status, k -> ConcurrentHashMap.newKeySet());
        
        if (add) {
            statusIndex.get(status).add(activityId);
        } else {
            statusIndex.get(status).remove(activityId);
        }
    }
    
    /**
     * 更新优先级列表
     *
     * @param activity 活动实例
     */
    private void updatePriorityList(Activity activity) {
        priorityList.add(activity.getActivityId());
        
        // 按优先级排序
        priorityList.sort((id1, id2) -> {
            Activity a1 = activities.get(id1);
            Activity a2 = activities.get(id2);
            
            if (a1 == null || a2 == null) {
                return 0;
            }
            
            // 优先级高的排在前面
            int result = Integer.compare(a2.getPriority(), a1.getPriority());
            if (result == 0) {
                // 优先级相同时，按创建时间排序
                result = Long.compare(a1.getCreateTime(), a2.getCreateTime());
            }
            
            return result;
        });
    }
    
    /**
     * 更新活动状态
     *
     * @param activity  活动实例
     * @param newStatus 新状态
     */
    private void updateActivityStatus(Activity activity, Activity.ActivityStatus newStatus) {
        lock.writeLock().lock();
        try {
            Activity.ActivityStatus oldStatus = activity.getStatus();
            
            // 从旧状态索引中移除
            updateStatusIndex(oldStatus, activity.getActivityId(), false);
            
            // 添加到新状态索引
            updateStatusIndex(newStatus, activity.getActivityId(), true);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 检查单个活动状态
     *
     * @param activity    活动实例
     * @param currentTime 当前时间
     */
    private void checkActivityStatus(Activity activity, long currentTime) {
        if (activity == null) {
            return;
        }
        
        // 检查是否需要自动启动
        if (activity.getStatus() == Activity.ActivityStatus.NOT_STARTED && 
            activity.getStartTime() != null && 
            currentTime >= activity.getStartTime()) {
            
            log.info("活动到达开始时间，自动启动: {} (ID: {})", 
                    activity.getActivityName(), activity.getActivityId());
            startActivity(activity.getActivityId());
        }
        
        // 检查是否需要自动结束
        if (activity.getStatus() == Activity.ActivityStatus.ACTIVE && 
            activity.getEndTime() != null && 
            currentTime >= activity.getEndTime()) {
            
            log.info("活动到达结束时间，自动结束: {} (ID: {})", 
                    activity.getActivityName(), activity.getActivityId());
            stopActivity(activity.getActivityId(), "time_expired");
        }
    }
    
    /**
     * 创建活动上下文
     *
     * @param activity 活动实例
     * @return 活动上下文
     */
    private ActivityContext createActivityContext(Activity activity) {
        return new ActivityContext(activity.getActivityId(), null, activity.getActivityType());
    }
    
    /**
     * 活动管理器统计信息
     */
    public static class ActivityManagerStats {
        public int totalActivities;
        public int activeActivities;
        public int pausedActivities;
        public int endedActivities;
        public Map<ActivityType, Integer> typeDistribution;
    }
}