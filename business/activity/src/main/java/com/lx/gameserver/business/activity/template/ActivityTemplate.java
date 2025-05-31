/*
 * 文件名: ActivityTemplate.java
 * 用途: 活动模板接口
 * 实现内容:
 *   - 定义活动模板的标准方法
 *   - 参与条件检查和进度计算
 *   - 奖励计算和数据验证
 *   - 模板方法模式支持
 * 技术选型:
 *   - Java接口定义
 *   - 泛型支持
 *   - 异常处理
 * 依赖关系:
 *   - 被活动模板类实现
 *   - 与ActivityContext协作
 *   - 被活动管理器使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.template;

import com.lx.gameserver.business.activity.core.ActivityContext;

import java.util.Map;

/**
 * 活动模板接口
 * <p>
 * 定义活动模板的标准行为和方法，为不同类型的活动提供
 * 统一的参与条件检查、进度计算、奖励计算等功能接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface ActivityTemplate {
    
    /**
     * 检查参与条件
     * <p>
     * 验证玩家是否满足参与此活动的条件，如等级、VIP等级、
     * 前置任务完成情况等。
     * </p>
     *
     * @param context    活动上下文
     * @param playerId   玩家ID
     * @param conditions 参与条件配置
     * @return 检查结果
     */
    ParticipationCheckResult checkParticipationConditions(ActivityContext context, 
                                                         Long playerId, 
                                                         Map<String, Object> conditions);
    
    /**
     * 计算活动进度
     * <p>
     * 根据玩家的游戏行为和活动规则，计算当前的活动进度。
     * 支持多种进度类型和计算方式。
     * </p>
     *
     * @param context     活动上下文
     * @param playerId    玩家ID
     * @param actionType  行为类型
     * @param actionData  行为数据
     * @return 进度计算结果
     */
    ProgressCalculationResult calculateProgress(ActivityContext context, 
                                              Long playerId, 
                                              String actionType, 
                                              Map<String, Object> actionData);
    
    /**
     * 计算奖励
     * <p>
     * 根据玩家的活动进度和完成情况，计算应该获得的奖励。
     * 支持基础奖励、额外奖励、VIP加成等。
     * </p>
     *
     * @param context    活动上下文
     * @param playerId   玩家ID
     * @param milestone  里程碑或阶段
     * @param baseReward 基础奖励配置
     * @return 奖励计算结果
     */
    RewardCalculationResult calculateReward(ActivityContext context, 
                                          Long playerId, 
                                          String milestone, 
                                          Map<String, Object> baseReward);
    
    /**
     * 验证活动数据
     * <p>
     * 验证活动相关数据的合法性和完整性，防止作弊和异常数据。
     * </p>
     *
     * @param context      活动上下文
     * @param playerId     玩家ID
     * @param operationType 操作类型
     * @param data         待验证数据
     * @return 验证结果
     */
    ValidationResult validateData(ActivityContext context, 
                                Long playerId, 
                                String operationType, 
                                Map<String, Object> data);
    
    /**
     * 获取活动显示信息
     * <p>
     * 获取活动在客户端显示的信息，包括名称、描述、进度、
     * 奖励预览等。
     * </p>
     *
     * @param context  活动上下文
     * @param playerId 玩家ID
     * @return 显示信息
     */
    ActivityDisplayInfo getDisplayInfo(ActivityContext context, Long playerId);
    
    /**
     * 处理活动事件
     * <p>
     * 处理与活动相关的游戏事件，如任务完成、物品获得等。
     * 默认实现为空，子类可以覆盖。
     * </p>
     *
     * @param context   活动上下文
     * @param playerId  玩家ID
     * @param eventType 事件类型
     * @param eventData 事件数据
     * @return 处理结果
     */
    default EventProcessResult processEvent(ActivityContext context, 
                                          Long playerId, 
                                          String eventType, 
                                          Map<String, Object> eventData) {
        return EventProcessResult.success("默认处理完成");
    }
    
    /**
     * 重置活动状态
     * <p>
     * 重置玩家的活动状态，通常用于周期性活动的重置。
     * 默认实现为空，子类可以覆盖。
     * </p>
     *
     * @param context  活动上下文
     * @param playerId 玩家ID
     * @return 重置结果
     */
    default ResetResult resetPlayerState(ActivityContext context, Long playerId) {
        return ResetResult.success("默认重置完成");
    }
    
    /**
     * 参与条件检查结果
     */
    class ParticipationCheckResult {
        private final boolean allowed;
        private final String reason;
        private final Map<String, Object> requirements;
        
        private ParticipationCheckResult(boolean allowed, String reason, Map<String, Object> requirements) {
            this.allowed = allowed;
            this.reason = reason;
            this.requirements = requirements;
        }
        
        public static ParticipationCheckResult allow() {
            return new ParticipationCheckResult(true, null, null);
        }
        
        public static ParticipationCheckResult deny(String reason) {
            return new ParticipationCheckResult(false, reason, null);
        }
        
        public static ParticipationCheckResult deny(String reason, Map<String, Object> requirements) {
            return new ParticipationCheckResult(false, reason, requirements);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public Map<String, Object> getRequirements() { return requirements; }
    }
    
    /**
     * 进度计算结果
     */
    class ProgressCalculationResult {
        private final boolean success;
        private final Map<String, Long> progressChanges;
        private final String[] completedMilestones;
        private final String message;
        
        private ProgressCalculationResult(boolean success, Map<String, Long> progressChanges, 
                                        String[] completedMilestones, String message) {
            this.success = success;
            this.progressChanges = progressChanges;
            this.completedMilestones = completedMilestones;
            this.message = message;
        }
        
        public static ProgressCalculationResult success(Map<String, Long> progressChanges) {
            return new ProgressCalculationResult(true, progressChanges, null, null);
        }
        
        public static ProgressCalculationResult success(Map<String, Long> progressChanges, 
                                                       String[] completedMilestones) {
            return new ProgressCalculationResult(true, progressChanges, completedMilestones, null);
        }
        
        public static ProgressCalculationResult failure(String message) {
            return new ProgressCalculationResult(false, null, null, message);
        }
        
        public boolean isSuccess() { return success; }
        public Map<String, Long> getProgressChanges() { return progressChanges; }
        public String[] getCompletedMilestones() { return completedMilestones; }
        public String getMessage() { return message; }
    }
    
    /**
     * 奖励计算结果
     */
    class RewardCalculationResult {
        private final boolean success;
        private final Map<String, Object> rewards;
        private final Map<String, Object> bonuses;
        private final String message;
        
        private RewardCalculationResult(boolean success, Map<String, Object> rewards, 
                                      Map<String, Object> bonuses, String message) {
            this.success = success;
            this.rewards = rewards;
            this.bonuses = bonuses;
            this.message = message;
        }
        
        public static RewardCalculationResult success(Map<String, Object> rewards) {
            return new RewardCalculationResult(true, rewards, null, null);
        }
        
        public static RewardCalculationResult success(Map<String, Object> rewards, 
                                                    Map<String, Object> bonuses) {
            return new RewardCalculationResult(true, rewards, bonuses, null);
        }
        
        public static RewardCalculationResult failure(String message) {
            return new RewardCalculationResult(false, null, null, message);
        }
        
        public boolean isSuccess() { return success; }
        public Map<String, Object> getRewards() { return rewards; }
        public Map<String, Object> getBonuses() { return bonuses; }
        public String getMessage() { return message; }
    }
    
    /**
     * 数据验证结果
     */
    class ValidationResult {
        private final boolean valid;
        private final String[] errors;
        private final String[] warnings;
        
        private ValidationResult(boolean valid, String[] errors, String[] warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult invalid(String... errors) {
            return new ValidationResult(false, errors, null);
        }
        
        public static ValidationResult validWithWarnings(String... warnings) {
            return new ValidationResult(true, null, warnings);
        }
        
        public boolean isValid() { return valid; }
        public String[] getErrors() { return errors; }
        public String[] getWarnings() { return warnings; }
    }
    
    /**
     * 活动显示信息
     */
    class ActivityDisplayInfo {
        private String title;
        private String description;
        private Map<String, Object> progressInfo;
        private Map<String, Object> rewardPreview;
        private Map<String, Object> timeInfo;
        private Map<String, Object> requirements;
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Map<String, Object> getProgressInfo() { return progressInfo; }
        public void setProgressInfo(Map<String, Object> progressInfo) { this.progressInfo = progressInfo; }
        
        public Map<String, Object> getRewardPreview() { return rewardPreview; }
        public void setRewardPreview(Map<String, Object> rewardPreview) { this.rewardPreview = rewardPreview; }
        
        public Map<String, Object> getTimeInfo() { return timeInfo; }
        public void setTimeInfo(Map<String, Object> timeInfo) { this.timeInfo = timeInfo; }
        
        public Map<String, Object> getRequirements() { return requirements; }
        public void setRequirements(Map<String, Object> requirements) { this.requirements = requirements; }
    }
    
    /**
     * 事件处理结果
     */
    class EventProcessResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> resultData;
        
        private EventProcessResult(boolean success, String message, Map<String, Object> resultData) {
            this.success = success;
            this.message = message;
            this.resultData = resultData;
        }
        
        public static EventProcessResult success(String message) {
            return new EventProcessResult(true, message, null);
        }
        
        public static EventProcessResult success(String message, Map<String, Object> resultData) {
            return new EventProcessResult(true, message, resultData);
        }
        
        public static EventProcessResult failure(String message) {
            return new EventProcessResult(false, message, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getResultData() { return resultData; }
    }
    
    /**
     * 重置结果
     */
    class ResetResult {
        private final boolean success;
        private final String message;
        
        private ResetResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static ResetResult success(String message) {
            return new ResetResult(true, message);
        }
        
        public static ResetResult failure(String message) {
            return new ResetResult(false, message);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}