/*
 * 文件名: BaseActivity.java
 * 用途: 活动基础实现类
 * 实现内容:
 *   - 活动基础功能的默认实现
 *   - 通用属性管理和生命周期处理
 *   - 基础生命周期实现和事件触发
 *   - 通用方法封装和日志记录
 * 技术选型:
 *   - 继承Activity抽象类
 *   - 实现ActivityTemplate接口
 *   - 集成Spring事件发布
 *   - 异常处理和日志记录
 * 依赖关系:
 *   - 继承Activity核心类
 *   - 实现ActivityTemplate接口
 *   - 被具体活动类继承
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.template;

import com.lx.gameserver.business.activity.core.Activity;
import com.lx.gameserver.business.activity.core.ActivityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动基础实现类
 * <p>
 * 提供活动的基础功能实现，包含通用的属性管理、生命周期处理、
 * 事件发布等功能。具体的活动类可以继承此类并实现特定的业务逻辑。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
public abstract class BaseActivity extends Activity implements ActivityTemplate {
    
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;
    
    /** 活动统计数据 */
    protected final Map<String, Object> statistics = new ConcurrentHashMap<>();
    
    /** 活动事件监听器 */
    protected final Map<String, Object> eventListeners = new ConcurrentHashMap<>();
    
    /** 默认参与条件 */
    protected Map<String, Object> defaultConditions = new HashMap<>();
    
    /** 默认奖励配置 */
    protected Map<String, Object> defaultRewards = new HashMap<>();
    
    /**
     * 默认构造函数
     */
    public BaseActivity() {
        super();
        initializeDefaults();
    }
    
    /**
     * 构造函数
     *
     * @param activityId   活动ID
     * @param activityName 活动名称
     * @param activityType 活动类型
     */
    public BaseActivity(Long activityId, String activityName, 
                       com.lx.gameserver.business.activity.core.ActivityType activityType) {
        super(activityId, activityName, activityType);
        initializeDefaults();
    }
    
    // ===== 生命周期实现 =====
    
    @Override
    protected void doInitialize(ActivityContext context) throws Exception {
        log.info("初始化基础活动: {}", getActivityName());
        
        // 初始化统计数据
        initializeStatistics();
        
        // 加载活动配置
        loadActivityConfig(context);
        
        // 初始化默认条件和奖励
        initializeDefaultsFromConfig();
        
        // 调用子类初始化
        doBaseInitialize(context);
        
        log.info("基础活动初始化完成: {}", getActivityName());
    }
    
    @Override
    protected void doStart(ActivityContext context) throws Exception {
        log.info("启动基础活动: {}", getActivityName());
        
        // 重置统计数据
        resetStatistics();
        
        // 发布活动开始事件
        publishActivityEvent("activity.started", createEventData(context));
        
        // 调用子类启动逻辑
        doBaseStart(context);
        
        // 更新统计
        incrementStatistic("start_count");
        
        log.info("基础活动启动完成: {}", getActivityName());
    }
    
    @Override
    protected void doUpdate(ActivityContext context, long deltaTime) throws Exception {
        // 更新统计数据
        updateStatistics(deltaTime);
        
        // 调用子类更新逻辑
        doBaseUpdate(context, deltaTime);
        
        // 检查活动完成条件
        checkCompletionConditions(context);
    }
    
    @Override
    protected void doEnd(ActivityContext context, String reason) throws Exception {
        log.info("结束基础活动: {}, 原因: {}", getActivityName(), reason);
        
        // 发布活动结束事件
        Map<String, Object> eventData = createEventData(context);
        eventData.put("endReason", reason);
        publishActivityEvent("activity.ended", eventData);
        
        // 调用子类结束逻辑
        doBaseEnd(context, reason);
        
        // 更新统计
        incrementStatistic("end_count");
        
        log.info("基础活动结束完成: {}", getActivityName());
    }
    
    @Override
    protected void doReset(ActivityContext context) throws Exception {
        log.info("重置基础活动: {}", getActivityName());
        
        // 重置统计数据
        resetStatistics();
        
        // 发布重置事件
        publishActivityEvent("activity.reset", createEventData(context));
        
        // 调用子类重置逻辑
        doBaseReset(context);
        
        log.info("基础活动重置完成: {}", getActivityName());
    }
    
    // ===== ActivityTemplate实现 =====
    
    @Override
    public ParticipationCheckResult checkParticipationConditions(ActivityContext context, 
                                                               Long playerId, 
                                                               Map<String, Object> conditions) {
        try {
            // 检查基础条件
            if (conditions == null) {
                conditions = defaultConditions;
            }
            
            // 检查活动状态
            if (!isRunning()) {
                return ParticipationCheckResult.deny("活动未在进行中");
            }
            
            // 检查参与人数限制
            if (getMaxParticipants() != null && 
                getCurrentParticipants() >= getMaxParticipants()) {
                return ParticipationCheckResult.deny("活动参与人数已达上限");
            }
            
            // 调用子类检查逻辑
            return doCheckParticipationConditions(context, playerId, conditions);
            
        } catch (Exception e) {
            log.error("检查参与条件失败: playerId={}, activityId={}", 
                    playerId, getActivityId(), e);
            return ParticipationCheckResult.deny("参与条件检查异常");
        }
    }
    
    @Override
    public ProgressCalculationResult calculateProgress(ActivityContext context, 
                                                     Long playerId, 
                                                     String actionType, 
                                                     Map<String, Object> actionData) {
        try {
            // 调用子类进度计算逻辑
            ProgressCalculationResult result = doCalculateProgress(context, playerId, actionType, actionData);
            
            // 更新统计
            incrementStatistic("progress_calculations");
            
            return result;
            
        } catch (Exception e) {
            log.error("计算进度失败: playerId={}, actionType={}, activityId={}", 
                    playerId, actionType, getActivityId(), e);
            return ProgressCalculationResult.failure("进度计算异常");
        }
    }
    
    @Override
    public RewardCalculationResult calculateReward(ActivityContext context, 
                                                 Long playerId, 
                                                 String milestone, 
                                                 Map<String, Object> baseReward) {
        try {
            // 使用默认奖励配置
            if (baseReward == null) {
                baseReward = defaultRewards;
            }
            
            // 调用子类奖励计算逻辑
            RewardCalculationResult result = doCalculateReward(context, playerId, milestone, baseReward);
            
            // 更新统计
            incrementStatistic("reward_calculations");
            
            return result;
            
        } catch (Exception e) {
            log.error("计算奖励失败: playerId={}, milestone={}, activityId={}", 
                    playerId, milestone, getActivityId(), e);
            return RewardCalculationResult.failure("奖励计算异常");
        }
    }
    
    @Override
    public ValidationResult validateData(ActivityContext context, 
                                       Long playerId, 
                                       String operationType, 
                                       Map<String, Object> data) {
        try {
            // 基础数据验证
            if (data == null || data.isEmpty()) {
                return ValidationResult.invalid("数据不能为空");
            }
            
            // 调用子类验证逻辑
            return doValidateData(context, playerId, operationType, data);
            
        } catch (Exception e) {
            log.error("数据验证失败: playerId={}, operationType={}, activityId={}", 
                    playerId, operationType, getActivityId(), e);
            return ValidationResult.invalid("数据验证异常");
        }
    }
    
    @Override
    public ActivityDisplayInfo getDisplayInfo(ActivityContext context, Long playerId) {
        try {
            ActivityDisplayInfo info = new ActivityDisplayInfo();
            
            // 设置基础信息
            info.setTitle(getActivityName());
            info.setDescription(getDescription());
            
            // 设置时间信息
            Map<String, Object> timeInfo = new HashMap<>();
            timeInfo.put("startTime", getStartTime());
            timeInfo.put("endTime", getEndTime());
            timeInfo.put("currentTime", System.currentTimeMillis());
            info.setTimeInfo(timeInfo);
            
            // 调用子类获取详细信息
            enrichDisplayInfo(context, playerId, info);
            
            return info;
            
        } catch (Exception e) {
            log.error("获取显示信息失败: playerId={}, activityId={}", 
                    playerId, getActivityId(), e);
            return new ActivityDisplayInfo();
        }
    }
    
    // ===== 子类可覆盖的方法 =====
    
    /**
     * 子类基础初始化
     *
     * @param context 活动上下文
     * @throws Exception 初始化异常
     */
    protected abstract void doBaseInitialize(ActivityContext context) throws Exception;
    
    /**
     * 子类基础启动
     *
     * @param context 活动上下文
     * @throws Exception 启动异常
     */
    protected abstract void doBaseStart(ActivityContext context) throws Exception;
    
    /**
     * 子类基础更新
     *
     * @param context   活动上下文
     * @param deltaTime 时间间隔
     * @throws Exception 更新异常
     */
    protected abstract void doBaseUpdate(ActivityContext context, long deltaTime) throws Exception;
    
    /**
     * 子类基础结束
     *
     * @param context 活动上下文
     * @param reason  结束原因
     * @throws Exception 结束异常
     */
    protected abstract void doBaseEnd(ActivityContext context, String reason) throws Exception;
    
    /**
     * 子类基础重置
     *
     * @param context 活动上下文
     * @throws Exception 重置异常
     */
    protected void doBaseReset(ActivityContext context) throws Exception {
        // 默认空实现
    }
    
    /**
     * 子类参与条件检查
     *
     * @param context    活动上下文
     * @param playerId   玩家ID
     * @param conditions 参与条件
     * @return 检查结果
     */
    protected ParticipationCheckResult doCheckParticipationConditions(ActivityContext context, 
                                                                     Long playerId, 
                                                                     Map<String, Object> conditions) {
        return ParticipationCheckResult.allow();
    }
    
    /**
     * 子类进度计算
     *
     * @param context    活动上下文
     * @param playerId   玩家ID
     * @param actionType 行为类型
     * @param actionData 行为数据
     * @return 计算结果
     */
    protected abstract ProgressCalculationResult doCalculateProgress(ActivityContext context, 
                                                                   Long playerId, 
                                                                   String actionType, 
                                                                   Map<String, Object> actionData);
    
    /**
     * 子类奖励计算
     *
     * @param context    活动上下文
     * @param playerId   玩家ID
     * @param milestone  里程碑
     * @param baseReward 基础奖励
     * @return 计算结果
     */
    protected abstract RewardCalculationResult doCalculateReward(ActivityContext context, 
                                                               Long playerId, 
                                                               String milestone, 
                                                               Map<String, Object> baseReward);
    
    /**
     * 子类数据验证
     *
     * @param context       活动上下文
     * @param playerId      玩家ID
     * @param operationType 操作类型
     * @param data          数据
     * @return 验证结果
     */
    protected ValidationResult doValidateData(ActivityContext context, 
                                            Long playerId, 
                                            String operationType, 
                                            Map<String, Object> data) {
        return ValidationResult.valid();
    }
    
    /**
     * enrichDisplayInfo 方法实现
     *
     * @param context  活动上下文
     * @param playerId 玩家ID
     * @param info     显示信息
     */
    protected void enrichDisplayInfo(ActivityContext context, Long playerId, ActivityDisplayInfo info) {
        // 默认空实现，子类可以覆盖
    }
    
    // ===== 工具方法 =====
    
    /**
     * 初始化默认设置
     */
    private void initializeDefaults() {
        // 初始化默认参与条件
        defaultConditions.put("minLevel", 1);
        defaultConditions.put("maxLevel", Integer.MAX_VALUE);
        
        // 初始化默认奖励
        defaultRewards.put("type", "experience");
        defaultRewards.put("amount", 100);
    }
    
    /**
     * 初始化统计数据
     */
    private void initializeStatistics() {
        statistics.put("start_count", 0L);
        statistics.put("end_count", 0L);
        statistics.put("update_count", 0L);
        statistics.put("progress_calculations", 0L);
        statistics.put("reward_calculations", 0L);
        statistics.put("total_runtime", 0L);
        statistics.put("last_update_time", System.currentTimeMillis());
    }
    
    /**
     * 重置统计数据
     */
    protected void resetStatistics() {
        statistics.replaceAll((key, value) -> {
            if ("last_update_time".equals(key)) {
                return System.currentTimeMillis();
            }
            return 0L;
        });
    }
    
    /**
     * 更新统计数据
     *
     * @param deltaTime 时间间隔
     */
    private void updateStatistics(long deltaTime) {
        incrementStatistic("update_count");
        incrementStatistic("total_runtime", deltaTime);
        statistics.put("last_update_time", System.currentTimeMillis());
    }
    
    /**
     * 增加统计计数
     *
     * @param key 统计键
     */
    protected void incrementStatistic(String key) {
        incrementStatistic(key, 1L);
    }
    
    /**
     * 增加统计值
     *
     * @param key   统计键
     * @param value 增加值
     */
    protected void incrementStatistic(String key, long value) {
        statistics.merge(key, value, (oldValue, newValue) -> 
                (Long) oldValue + (Long) newValue);
    }
    
    /**
     * 加载活动配置
     *
     * @param context 活动上下文
     */
    protected void loadActivityConfig(ActivityContext context) {
        // 从上下文或配置文件加载活动特定配置
        // 子类可以覆盖此方法
    }
    
    /**
     * 从配置初始化默认值
     */
    protected void initializeDefaultsFromConfig() {
        // 从活动配置中读取默认条件和奖励
        Object conditionsConfig = getConfig("defaultConditions");
        if (conditionsConfig instanceof Map) {
            defaultConditions.putAll((Map<String, Object>) conditionsConfig);
        }
        
        Object rewardsConfig = getConfig("defaultRewards");
        if (rewardsConfig instanceof Map) {
            defaultRewards.putAll((Map<String, Object>) rewardsConfig);
        }
    }
    
    /**
     * 检查完成条件
     *
     * @param context 活动上下文
     */
    protected void checkCompletionConditions(ActivityContext context) {
        // 子类可以覆盖此方法来检查特定的完成条件
    }
    
    /**
     * 发布活动事件
     *
     * @param eventType 事件类型
     * @param eventData 事件数据
     */
    protected void publishActivityEvent(String eventType, Map<String, Object> eventData) {
        if (eventPublisher != null) {
            try {
                ActivityEvent event = new ActivityEvent(this, eventType, eventData);
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.error("发布活动事件失败: eventType={}, activityId={}", 
                        eventType, getActivityId(), e);
            }
        }
    }
    
    /**
     * 创建事件数据
     *
     * @param context 活动上下文
     * @return 事件数据
     */
    protected Map<String, Object> createEventData(ActivityContext context) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("activityId", getActivityId());
        eventData.put("activityName", getActivityName());
        eventData.put("activityType", getActivityType());
        eventData.put("timestamp", System.currentTimeMillis());
        
        if (context != null) {
            eventData.put("playerId", context.getPlayerId());
        }
        
        return eventData;
    }
    
    /**
     * 获取统计数据
     *
     * @return 统计数据副本
     */
    public Map<String, Object> getStatistics() {
        return new HashMap<>(statistics);
    }
    
    /**
     * 活动事件类
     */
    public static class ActivityEvent {
        private final Object source;
        private final String eventType;
        private final Map<String, Object> eventData;
        private final long timestamp;
        
        public ActivityEvent(Object source, String eventType, Map<String, Object> eventData) {
            this.source = source;
            this.eventType = eventType;
            this.eventData = eventData;
            this.timestamp = System.currentTimeMillis();
        }
        
        public Object getSource() { return source; }
        public String getEventType() { return eventType; }
        public Map<String, Object> getEventData() { return eventData; }
        public long getTimestamp() { return timestamp; }
    }
}