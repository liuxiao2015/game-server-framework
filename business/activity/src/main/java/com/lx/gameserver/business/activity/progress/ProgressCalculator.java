/*
 * 文件名: ProgressCalculator.java
 * 用途: 进度计算器接口
 * 实现内容:
 *   - 定义不同活动类型的进度计算方法
 *   - 进度百分比和里程碑检查
 *   - 进度验证和自定义计算逻辑
 *   - 支持扩展的计算策略
 * 技术选型:
 *   - Java接口定义
 *   - 策略模式支持
 *   - 泛型和函数式接口
 *   - 异常处理机制
 * 依赖关系:
 *   - 与ActivityProgress协作
 *   - 被ProgressTracker使用
 *   - 被具体计算器实现
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.progress;

import com.lx.gameserver.business.activity.core.ActivityType;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 进度计算器接口
 * <p>
 * 定义活动进度计算的标准方法，支持不同活动类型的进度计算逻辑。
 * 提供进度百分比计算、里程碑检查、进度验证等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface ProgressCalculator {
    
    /**
     * 获取支持的活动类型
     *
     * @return 支持的活动类型列表
     */
    List<ActivityType> getSupportedActivityTypes();
    
    /**
     * 计算进度值
     * <p>
     * 根据玩家行为和活动规则计算当前进度值。
     * </p>
     *
     * @param context 计算上下文
     * @return 计算结果
     */
    CalculationResult calculateProgress(CalculationContext context);
    
    /**
     * 计算进度百分比
     * <p>
     * 计算当前进度相对于目标的完成百分比。
     * </p>
     *
     * @param currentProgress 当前进度
     * @param targetProgress  目标进度
     * @param activityType    活动类型
     * @return 百分比（0-100）
     */
    double calculatePercentage(ActivityProgress currentProgress, 
                             Map<String, Long> targetProgress, 
                             ActivityType activityType);
    
    /**
     * 检查里程碑完成情况
     * <p>
     * 检查当前进度是否达到了某个里程碑。
     * </p>
     *
     * @param progress   当前进度
     * @param milestones 里程碑配置
     * @return 里程碑检查结果
     */
    MilestoneCheckResult checkMilestones(ActivityProgress progress, 
                                       Map<String, Object> milestones);
    
    /**
     * 验证进度合法性
     * <p>
     * 验证进度数据是否合法，防止作弊和异常数据。
     * </p>
     *
     * @param progress     当前进度
     * @param actionData   行为数据
     * @param constraints  约束条件
     * @return 验证结果
     */
    ValidationResult validateProgress(ActivityProgress progress, 
                                    Map<String, Object> actionData, 
                                    Map<String, Object> constraints);
    
    /**
     * 预测进度完成时间
     * <p>
     * 根据当前进度和历史数据预测完成时间。
     * 默认实现返回null，子类可以覆盖。
     * </p>
     *
     * @param progress     当前进度
     * @param targetGoals  目标
     * @param playerData   玩家数据
     * @return 预测结果
     */
    default PredictionResult predictCompletionTime(ActivityProgress progress, 
                                                 Map<String, Long> targetGoals, 
                                                 Map<String, Object> playerData) {
        return null;
    }
    
    /**
     * 自定义计算逻辑
     * <p>
     * 支持活动特定的自定义计算逻辑。
     * 默认实现返回空结果，子类可以覆盖。
     * </p>
     *
     * @param calculationType 计算类型
     * @param inputData       输入数据
     * @return 计算结果
     */
    default CustomCalculationResult customCalculate(String calculationType, 
                                                   Map<String, Object> inputData) {
        return CustomCalculationResult.empty();
    }
    
    /**
     * 计算上下文
     */
    class CalculationContext {
        /** 玩家ID */
        private Long playerId;
        
        /** 活动ID */
        private Long activityId;
        
        /** 活动类型 */
        private ActivityType activityType;
        
        /** 当前进度 */
        private ActivityProgress currentProgress;
        
        /** 行为类型 */
        private String actionType;
        
        /** 行为数据 */
        private Map<String, Object> actionData;
        
        /** 活动配置 */
        private Map<String, Object> activityConfig;
        
        /** 玩家数据 */
        private Map<String, Object> playerData;
        
        /** 额外参数 */
        private Map<String, Object> extraParams;
        
        // 构造函数
        public CalculationContext() {}
        
        public CalculationContext(Long playerId, Long activityId, ActivityType activityType) {
            this.playerId = playerId;
            this.activityId = activityId;
            this.activityType = activityType;
        }
        
        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        
        public ActivityType getActivityType() { return activityType; }
        public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
        
        public ActivityProgress getCurrentProgress() { return currentProgress; }
        public void setCurrentProgress(ActivityProgress currentProgress) { this.currentProgress = currentProgress; }
        
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        
        public Map<String, Object> getActionData() { return actionData; }
        public void setActionData(Map<String, Object> actionData) { this.actionData = actionData; }
        
        public Map<String, Object> getActivityConfig() { return activityConfig; }
        public void setActivityConfig(Map<String, Object> activityConfig) { this.activityConfig = activityConfig; }
        
        public Map<String, Object> getPlayerData() { return playerData; }
        public void setPlayerData(Map<String, Object> playerData) { this.playerData = playerData; }
        
        public Map<String, Object> getExtraParams() { return extraParams; }
        public void setExtraParams(Map<String, Object> extraParams) { this.extraParams = extraParams; }
    }
    
    /**
     * 计算结果
     */
    class CalculationResult {
        private final boolean success;
        private final Map<String, Long> progressChanges;
        private final String[] newMilestones;
        private final double completionPercentage;
        private final String message;
        private final Map<String, Object> extraData;
        
        private CalculationResult(boolean success, Map<String, Long> progressChanges, 
                                String[] newMilestones, double completionPercentage, 
                                String message, Map<String, Object> extraData) {
            this.success = success;
            this.progressChanges = progressChanges;
            this.newMilestones = newMilestones;
            this.completionPercentage = completionPercentage;
            this.message = message;
            this.extraData = extraData;
        }
        
        public static CalculationResult success(Map<String, Long> progressChanges, 
                                              double completionPercentage) {
            return new CalculationResult(true, progressChanges, null, 
                    completionPercentage, null, null);
        }
        
        public static CalculationResult success(Map<String, Long> progressChanges, 
                                              String[] newMilestones, 
                                              double completionPercentage) {
            return new CalculationResult(true, progressChanges, newMilestones, 
                    completionPercentage, null, null);
        }
        
        public static CalculationResult success(Map<String, Long> progressChanges, 
                                              String[] newMilestones, 
                                              double completionPercentage,
                                              Map<String, Object> extraData) {
            return new CalculationResult(true, progressChanges, newMilestones, 
                    completionPercentage, null, extraData);
        }
        
        public static CalculationResult failure(String message) {
            return new CalculationResult(false, null, null, 0.0, message, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Map<String, Long> getProgressChanges() { return progressChanges; }
        public String[] getNewMilestones() { return newMilestones; }
        public double getCompletionPercentage() { return completionPercentage; }
        public String getMessage() { return message; }
        public Map<String, Object> getExtraData() { return extraData; }
    }
    
    /**
     * 里程碑检查结果
     */
    class MilestoneCheckResult {
        private final String[] reachedMilestones;
        private final Map<String, Object> milestoneData;
        private final String nextMilestone;
        private final double progressToNext;
        
        private MilestoneCheckResult(String[] reachedMilestones, 
                                   Map<String, Object> milestoneData,
                                   String nextMilestone, 
                                   double progressToNext) {
            this.reachedMilestones = reachedMilestones;
            this.milestoneData = milestoneData;
            this.nextMilestone = nextMilestone;
            this.progressToNext = progressToNext;
        }
        
        public static MilestoneCheckResult create(String[] reachedMilestones, 
                                                Map<String, Object> milestoneData,
                                                String nextMilestone, 
                                                double progressToNext) {
            return new MilestoneCheckResult(reachedMilestones, milestoneData, 
                    nextMilestone, progressToNext);
        }
        
        public static MilestoneCheckResult noMilestones() {
            return new MilestoneCheckResult(new String[0], null, null, 0.0);
        }
        
        // Getters
        public String[] getReachedMilestones() { return reachedMilestones; }
        public Map<String, Object> getMilestoneData() { return milestoneData; }
        public String getNextMilestone() { return nextMilestone; }
        public double getProgressToNext() { return progressToNext; }
        
        public boolean hasReachedMilestones() {
            return reachedMilestones != null && reachedMilestones.length > 0;
        }
    }
    
    /**
     * 验证结果
     */
    class ValidationResult {
        private final boolean valid;
        private final String[] violations;
        private final String[] warnings;
        private final Map<String, Object> validationData;
        
        private ValidationResult(boolean valid, String[] violations, String[] warnings,
                               Map<String, Object> validationData) {
            this.valid = valid;
            this.violations = violations;
            this.warnings = warnings;
            this.validationData = validationData;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null, null);
        }
        
        public static ValidationResult validWithWarnings(String... warnings) {
            return new ValidationResult(true, null, warnings, null);
        }
        
        public static ValidationResult invalid(String... violations) {
            return new ValidationResult(false, violations, null, null);
        }
        
        public static ValidationResult invalid(String[] violations, 
                                             Map<String, Object> validationData) {
            return new ValidationResult(false, violations, null, validationData);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String[] getViolations() { return violations; }
        public String[] getWarnings() { return warnings; }
        public Map<String, Object> getValidationData() { return validationData; }
    }
    
    /**
     * 预测结果
     */
    class PredictionResult {
        private final long estimatedCompletionTime;
        private final double confidence;
        private final String[] assumptions;
        private final Map<String, Object> predictionData;
        
        private PredictionResult(long estimatedCompletionTime, double confidence,
                               String[] assumptions, Map<String, Object> predictionData) {
            this.estimatedCompletionTime = estimatedCompletionTime;
            this.confidence = confidence;
            this.assumptions = assumptions;
            this.predictionData = predictionData;
        }
        
        public static PredictionResult create(long estimatedCompletionTime, double confidence) {
            return new PredictionResult(estimatedCompletionTime, confidence, null, null);
        }
        
        public static PredictionResult create(long estimatedCompletionTime, double confidence,
                                            String[] assumptions, Map<String, Object> predictionData) {
            return new PredictionResult(estimatedCompletionTime, confidence, 
                    assumptions, predictionData);
        }
        
        // Getters
        public long getEstimatedCompletionTime() { return estimatedCompletionTime; }
        public double getConfidence() { return confidence; }
        public String[] getAssumptions() { return assumptions; }
        public Map<String, Object> getPredictionData() { return predictionData; }
    }
    
    /**
     * 自定义计算结果
     */
    class CustomCalculationResult {
        private final boolean success;
        private final Object result;
        private final String message;
        private final Map<String, Object> metadata;
        
        private CustomCalculationResult(boolean success, Object result, String message,
                                      Map<String, Object> metadata) {
            this.success = success;
            this.result = result;
            this.message = message;
            this.metadata = metadata;
        }
        
        public static CustomCalculationResult success(Object result) {
            return new CustomCalculationResult(true, result, null, null);
        }
        
        public static CustomCalculationResult success(Object result, Map<String, Object> metadata) {
            return new CustomCalculationResult(true, result, null, metadata);
        }
        
        public static CustomCalculationResult failure(String message) {
            return new CustomCalculationResult(false, null, message, null);
        }
        
        public static CustomCalculationResult empty() {
            return new CustomCalculationResult(true, null, "无自定义计算", null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Object getResult() { return result; }
        public String getMessage() { return message; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        @SuppressWarnings("unchecked")
        public <T> T getResult(Class<T> type) {
            if (result != null && type.isInstance(result)) {
                return (T) result;
            }
            return null;
        }
    }
    
    /**
     * 计算策略枚举
     */
    enum CalculationStrategy {
        /** 累加策略 */
        ACCUMULATIVE("accumulative", "累加计算"),
        
        /** 最大值策略 */
        MAXIMUM("maximum", "取最大值"),
        
        /** 平均值策略 */
        AVERAGE("average", "平均值计算"),
        
        /** 加权平均策略 */
        WEIGHTED_AVERAGE("weighted_average", "加权平均"),
        
        /** 自定义策略 */
        CUSTOM("custom", "自定义计算");
        
        private final String code;
        private final String description;
        
        CalculationStrategy(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    /**
     * 工具方法 - 创建标准计算函数
     */
    static BiFunction<Long, Long, Long> createCalculationFunction(CalculationStrategy strategy) {
        return switch (strategy) {
            case ACCUMULATIVE -> Long::sum;
            case MAXIMUM -> Long::max;
            case AVERAGE -> (a, b) -> (a + b) / 2;
            default -> Long::sum; // 默认累加
        };
    }
}