/*
 * 文件名: IActivityService.java
 * 用途: 活动服务接口定义
 * 实现内容:
 *   - 定义活动管理和配置接口
 *   - 定义活动进度和奖励发放接口
 *   - 定义活动参与和完成状态接口
 *   - 支持多种活动类型和奖励机制
 * 技术选型:
 *   - 使用Java接口定义服务规范
 *   - 集成Result和PageResult通用返回类型
 *   - 支持定时活动和事件驱动活动
 * 依赖关系:
 *   - 依赖common-core的Result和PageResult
 *   - 被activity-service模块实现
 *   - 被需要活动功能的模块调用
 */
package com.lx.gameserver.api;

import com.lx.gameserver.common.Result;
import com.lx.gameserver.common.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 活动服务接口
 * <p>
 * 定义了活动系统的所有核心功能，包括活动管理、进度跟踪、
 * 奖励发放、条件判断等。支持多种活动类型和奖励机制，
 * 提供灵活的活动配置和实时的进度更新。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public interface IActivityService {

    // ===== 活动管理接口 =====

    /**
     * 创建活动
     *
     * @param activityConfig 活动配置
     * @return 创建结果，包含活动ID
     */
    Result<Long> createActivity(ActivityConfig activityConfig);

    /**
     * 更新活动配置
     *
     * @param activityId     活动ID
     * @param activityConfig 新的活动配置
     * @return 更新结果
     */
    Result<Void> updateActivity(Long activityId, ActivityConfig activityConfig);

    /**
     * 删除活动
     *
     * @param activityId 活动ID
     * @return 删除结果
     */
    Result<Void> deleteActivity(Long activityId);

    /**
     * 启动活动
     *
     * @param activityId 活动ID
     * @return 启动结果
     */
    Result<Void> startActivity(Long activityId);

    /**
     * 停止活动
     *
     * @param activityId 活动ID
     * @param reason     停止原因
     * @return 停止结果
     */
    Result<Void> stopActivity(Long activityId, String reason);

    /**
     * 获取活动配置
     *
     * @param activityId 活动ID
     * @return 活动配置
     */
    Result<ActivityConfig> getActivityConfig(Long activityId);

    /**
     * 获取所有活动列表
     *
     * @param status   活动状态过滤（可选）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 活动列表分页结果
     */
    PageResult<ActivityInfo> getAllActivities(ActivityStatus status, Integer pageNum, Integer pageSize);

    // ===== 玩家活动接口 =====

    /**
     * 获取玩家可参与的活动列表
     *
     * @param playerId 玩家ID
     * @return 可参与的活动列表
     */
    Result<List<PlayerActivityInfo>> getPlayerActivities(Long playerId);

    /**
     * 玩家参与活动
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 参与结果
     */
    Result<Void> joinActivity(Long playerId, Long activityId);

    /**
     * 获取玩家活动进度
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 活动进度
     */
    Result<ActivityProgress> getPlayerActivityProgress(Long playerId, Long activityId);

    /**
     * 批量获取玩家活动进度
     *
     * @param playerId    玩家ID
     * @param activityIds 活动ID列表
     * @return 活动进度映射
     */
    Result<Map<Long, ActivityProgress>> getPlayerActivitiesProgress(Long playerId, List<Long> activityIds);

    /**
     * 更新玩家活动进度
     *
     * @param playerId    玩家ID
     * @param activityId  活动ID
     * @param progressKey 进度键
     * @param increment   增量
     * @param eventData   事件数据
     * @return 更新结果，包含新的进度信息
     */
    Result<ProgressUpdateResult> updateActivityProgress(Long playerId, Long activityId, String progressKey, 
                                                       Long increment, Map<String, Object> eventData);

    /**
     * 检查活动任务完成状态
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param taskId     任务ID
     * @return 任务完成状态
     */
    Result<Boolean> checkTaskCompletion(Long playerId, Long activityId, Long taskId);

    // ===== 奖励发放接口 =====

    /**
     * 领取活动奖励
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param rewardId   奖励ID
     * @return 领取结果，包含奖励详情
     */
    Result<RewardResult> claimActivityReward(Long playerId, Long activityId, Long rewardId);

    /**
     * 批量领取活动奖励
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param rewardIds  奖励ID列表
     * @return 批量领取结果
     */
    Result<List<RewardResult>> claimActivityRewards(Long playerId, Long activityId, List<Long> rewardIds);

    /**
     * 检查奖励是否可领取
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param rewardId   奖励ID
     * @return 是否可领取
     */
    Result<Boolean> canClaimReward(Long playerId, Long activityId, Long rewardId);

    /**
     * 获取玩家已领取的奖励列表
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 已领取的奖励ID列表
     */
    Result<List<Long>> getClaimedRewards(Long playerId, Long activityId);

    /**
     * 自动发放活动奖励
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param reason     发放原因
     * @return 自动发放结果
     */
    Result<List<RewardResult>> autoGrantActivityRewards(Long playerId, Long activityId, String reason);

    // ===== 活动任务接口 =====

    /**
     * 获取活动任务列表
     *
     * @param activityId 活动ID
     * @return 活动任务列表
     */
    Result<List<ActivityTask>> getActivityTasks(Long activityId);

    /**
     * 获取玩家活动任务状态
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 玩家任务状态列表
     */
    Result<List<PlayerTaskStatus>> getPlayerTaskStatus(Long playerId, Long activityId);

    /**
     * 完成活动任务
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param taskId     任务ID
     * @return 完成结果
     */
    Result<TaskCompleteResult> completeTask(Long playerId, Long activityId, Long taskId);

    /**
     * 重置玩家任务进度
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param taskId     任务ID
     * @return 重置结果
     */
    Result<Void> resetTaskProgress(Long playerId, Long activityId, Long taskId);

    // ===== 活动排行榜接口 =====

    /**
     * 获取活动排行榜
     *
     * @param activityId 活动ID
     * @param rankType   排行榜类型
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 活动排行榜分页结果
     */
    PageResult<ActivityRankEntry> getActivityRanking(Long activityId, String rankType, 
                                                    Integer pageNum, Integer pageSize);

    /**
     * 获取玩家在活动中的排名
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param rankType   排行榜类型
     * @return 玩家排名信息
     */
    Result<PlayerActivityRank> getPlayerActivityRank(Long playerId, Long activityId, String rankType);

    /**
     * 更新活动排行榜数据
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @param rankType   排行榜类型
     * @param score      分数
     * @return 更新结果
     */
    Result<Void> updateActivityRankScore(Long playerId, Long activityId, String rankType, Long score);

    // ===== 活动统计接口 =====

    /**
     * 获取活动统计信息
     *
     * @param activityId 活动ID
     * @return 活动统计信息
     */
    Result<ActivityStatistics> getActivityStatistics(Long activityId);

    /**
     * 获取玩家活动统计
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     * @return 玩家活动统计
     */
    Result<PlayerActivityStatistics> getPlayerActivityStatistics(Long playerId, Long activityId);

    /**
     * 获取活动参与度报告
     *
     * @param activityId 活动ID
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @return 参与度报告
     */
    Result<ActivityParticipationReport> getParticipationReport(Long activityId, Long startTime, Long endTime);

    // ===== 活动事件接口 =====

    /**
     * 触发活动事件
     *
     * @param playerId  玩家ID
     * @param eventType 事件类型
     * @param eventData 事件数据
     * @return 触发结果
     */
    Result<Void> triggerActivityEvent(Long playerId, String eventType, Map<String, Object> eventData);

    /**
     * 注册活动事件监听器
     *
     * @param activityId 活动ID
     * @param eventType  事件类型
     * @param listener   监听器配置
     * @return 注册结果
     */
    Result<Void> registerEventListener(Long activityId, String eventType, Map<String, Object> listener);

    // ===== 内部数据结构定义 =====

    /**
     * 活动状态枚举
     */
    enum ActivityStatus {
        /** 未开始 */
        NOT_STARTED,
        /** 进行中 */
        ACTIVE,
        /** 已结束 */
        ENDED,
        /** 已暂停 */
        PAUSED,
        /** 已取消 */
        CANCELLED
    }

    /**
     * 活动类型枚举
     */
    enum ActivityType {
        /** 签到活动 */
        SIGN_IN,
        /** 充值活动 */
        RECHARGE,
        /** 消费活动 */
        CONSUMPTION,
        /** 任务活动 */
        TASK,
        /** 竞赛活动 */
        COMPETITION,
        /** 限时活动 */
        LIMITED_TIME,
        /** 节日活动 */
        FESTIVAL,
        /** 新手活动 */
        NEWBIE
    }

    /**
     * 奖励类型枚举
     */
    enum RewardType {
        /** 道具奖励 */
        ITEM,
        /** 货币奖励 */
        CURRENCY,
        /** 经验奖励 */
        EXPERIENCE,
        /** 称号奖励 */
        TITLE,
        /** VIP经验奖励 */
        VIP_EXP
    }

    /**
     * 活动配置
     */
    class ActivityConfig {
        /** 活动ID */
        private Long activityId;
        /** 活动名称 */
        private String activityName;
        /** 活动描述 */
        private String description;
        /** 活动类型 */
        private ActivityType activityType;
        /** 开始时间 */
        private Long startTime;
        /** 结束时间 */
        private Long endTime;
        /** 参与条件 */
        private Map<String, Object> conditions;
        /** 活动参数 */
        private Map<String, Object> parameters;
        /** 奖励配置 */
        private List<RewardConfig> rewards;
        /** 任务配置 */
        private List<TaskConfig> tasks;
        /** 是否可重复参与 */
        private Boolean repeatable;
        /** 最大参与人数 */
        private Integer maxParticipants;

        // Getters and Setters
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public ActivityType getActivityType() { return activityType; }
        public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public Map<String, Object> getConditions() { return conditions; }
        public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public List<RewardConfig> getRewards() { return rewards; }
        public void setRewards(List<RewardConfig> rewards) { this.rewards = rewards; }
        public List<TaskConfig> getTasks() { return tasks; }
        public void setTasks(List<TaskConfig> tasks) { this.tasks = tasks; }
        public Boolean getRepeatable() { return repeatable; }
        public void setRepeatable(Boolean repeatable) { this.repeatable = repeatable; }
        public Integer getMaxParticipants() { return maxParticipants; }
        public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    }

    /**
     * 奖励配置
     */
    class RewardConfig {
        /** 奖励ID */
        private Long rewardId;
        /** 奖励类型 */
        private RewardType rewardType;
        /** 奖励项目ID */
        private Integer itemId;
        /** 奖励数量 */
        private Long quantity;
        /** 领取条件 */
        private Map<String, Object> claimConditions;
        /** 奖励描述 */
        private String description;

        // Getters and Setters
        public Long getRewardId() { return rewardId; }
        public void setRewardId(Long rewardId) { this.rewardId = rewardId; }
        public RewardType getRewardType() { return rewardType; }
        public void setRewardType(RewardType rewardType) { this.rewardType = rewardType; }
        public Integer getItemId() { return itemId; }
        public void setItemId(Integer itemId) { this.itemId = itemId; }
        public Long getQuantity() { return quantity; }
        public void setQuantity(Long quantity) { this.quantity = quantity; }
        public Map<String, Object> getClaimConditions() { return claimConditions; }
        public void setClaimConditions(Map<String, Object> claimConditions) { this.claimConditions = claimConditions; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * 任务配置
     */
    class TaskConfig {
        /** 任务ID */
        private Long taskId;
        /** 任务名称 */
        private String taskName;
        /** 任务描述 */
        private String description;
        /** 任务类型 */
        private String taskType;
        /** 完成条件 */
        private Map<String, Object> completionConditions;
        /** 任务奖励 */
        private List<RewardConfig> rewards;
        /** 前置任务 */
        private List<Long> prerequisites;

        // Getters and Setters
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public Map<String, Object> getCompletionConditions() { return completionConditions; }
        public void setCompletionConditions(Map<String, Object> completionConditions) { 
            this.completionConditions = completionConditions; 
        }
        public List<RewardConfig> getRewards() { return rewards; }
        public void setRewards(List<RewardConfig> rewards) { this.rewards = rewards; }
        public List<Long> getPrerequisites() { return prerequisites; }
        public void setPrerequisites(List<Long> prerequisites) { this.prerequisites = prerequisites; }
    }

    /**
     * 活动信息
     */
    class ActivityInfo {
        /** 活动ID */
        private Long activityId;
        /** 活动名称 */
        private String activityName;
        /** 活动类型 */
        private ActivityType activityType;
        /** 活动状态 */
        private ActivityStatus status;
        /** 开始时间 */
        private Long startTime;
        /** 结束时间 */
        private Long endTime;
        /** 参与人数 */
        private Integer participantCount;
        /** 创建时间 */
        private Long createTime;

        // Getters and Setters
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }
        public ActivityType getActivityType() { return activityType; }
        public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
        public ActivityStatus getStatus() { return status; }
        public void setStatus(ActivityStatus status) { this.status = status; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public Integer getParticipantCount() { return participantCount; }
        public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }

    /**
     * 玩家活动信息
     */
    class PlayerActivityInfo {
        /** 活动ID */
        private Long activityId;
        /** 活动名称 */
        private String activityName;
        /** 活动状态 */
        private ActivityStatus status;
        /** 玩家参与状态 */
        private String participationStatus;
        /** 完成进度百分比 */
        private Double completionPercentage;
        /** 可领取奖励数 */
        private Integer availableRewards;
        /** 剩余时间 */
        private Long remainingTime;

        // Getters and Setters
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }
        public ActivityStatus getStatus() { return status; }
        public void setStatus(ActivityStatus status) { this.status = status; }
        public String getParticipationStatus() { return participationStatus; }
        public void setParticipationStatus(String participationStatus) { this.participationStatus = participationStatus; }
        public Double getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(Double completionPercentage) { this.completionPercentage = completionPercentage; }
        public Integer getAvailableRewards() { return availableRewards; }
        public void setAvailableRewards(Integer availableRewards) { this.availableRewards = availableRewards; }
        public Long getRemainingTime() { return remainingTime; }
        public void setRemainingTime(Long remainingTime) { this.remainingTime = remainingTime; }
    }

    /**
     * 活动进度
     */
    class ActivityProgress {
        /** 活动ID */
        private Long activityId;
        /** 玩家ID */
        private Long playerId;
        /** 进度数据 */
        private Map<String, Long> progressData;
        /** 完成的任务 */
        private List<Long> completedTasks;
        /** 已领取的奖励 */
        private List<Long> claimedRewards;
        /** 最后更新时间 */
        private Long lastUpdateTime;

        // Getters and Setters
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Map<String, Long> getProgressData() { return progressData; }
        public void setProgressData(Map<String, Long> progressData) { this.progressData = progressData; }
        public List<Long> getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(List<Long> completedTasks) { this.completedTasks = completedTasks; }
        public List<Long> getClaimedRewards() { return claimedRewards; }
        public void setClaimedRewards(List<Long> claimedRewards) { this.claimedRewards = claimedRewards; }
        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }

    /**
     * 进度更新结果
     */
    class ProgressUpdateResult {
        /** 更新是否成功 */
        private Boolean success;
        /** 新的进度值 */
        private Long newProgress;
        /** 新完成的任务 */
        private List<Long> newlyCompletedTasks;
        /** 可领取的新奖励 */
        private List<Long> availableRewards;

        // Getters and Setters
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Long getNewProgress() { return newProgress; }
        public void setNewProgress(Long newProgress) { this.newProgress = newProgress; }
        public List<Long> getNewlyCompletedTasks() { return newlyCompletedTasks; }
        public void setNewlyCompletedTasks(List<Long> newlyCompletedTasks) { this.newlyCompletedTasks = newlyCompletedTasks; }
        public List<Long> getAvailableRewards() { return availableRewards; }
        public void setAvailableRewards(List<Long> availableRewards) { this.availableRewards = availableRewards; }
    }

    /**
     * 奖励结果
     */
    class RewardResult {
        /** 奖励ID */
        private Long rewardId;
        /** 奖励类型 */
        private RewardType rewardType;
        /** 物品ID */
        private Integer itemId;
        /** 数量 */
        private Long quantity;
        /** 发放是否成功 */
        private Boolean success;
        /** 发放时间 */
        private Long grantTime;

        // Getters and Setters
        public Long getRewardId() { return rewardId; }
        public void setRewardId(Long rewardId) { this.rewardId = rewardId; }
        public RewardType getRewardType() { return rewardType; }
        public void setRewardType(RewardType rewardType) { this.rewardType = rewardType; }
        public Integer getItemId() { return itemId; }
        public void setItemId(Integer itemId) { this.itemId = itemId; }
        public Long getQuantity() { return quantity; }
        public void setQuantity(Long quantity) { this.quantity = quantity; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Long getGrantTime() { return grantTime; }
        public void setGrantTime(Long grantTime) { this.grantTime = grantTime; }
    }

    /**
     * 活动任务
     */
    class ActivityTask {
        /** 任务ID */
        private Long taskId;
        /** 任务名称 */
        private String taskName;
        /** 任务描述 */
        private String description;
        /** 任务类型 */
        private String taskType;
        /** 完成条件 */
        private Map<String, Object> completionConditions;
        /** 任务奖励 */
        private List<RewardConfig> rewards;
        /** 排序权重 */
        private Integer sortOrder;

        // Getters and Setters
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public Map<String, Object> getCompletionConditions() { return completionConditions; }
        public void setCompletionConditions(Map<String, Object> completionConditions) { 
            this.completionConditions = completionConditions; 
        }
        public List<RewardConfig> getRewards() { return rewards; }
        public void setRewards(List<RewardConfig> rewards) { this.rewards = rewards; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    /**
     * 玩家任务状态
     */
    class PlayerTaskStatus {
        /** 任务ID */
        private Long taskId;
        /** 完成状态 */
        private Boolean completed;
        /** 当前进度 */
        private Map<String, Long> currentProgress;
        /** 目标进度 */
        private Map<String, Long> targetProgress;
        /** 完成时间 */
        private Long completionTime;
        /** 奖励是否已领取 */
        private Boolean rewardClaimed;

        // Getters and Setters
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public Boolean getCompleted() { return completed; }
        public void setCompleted(Boolean completed) { this.completed = completed; }
        public Map<String, Long> getCurrentProgress() { return currentProgress; }
        public void setCurrentProgress(Map<String, Long> currentProgress) { this.currentProgress = currentProgress; }
        public Map<String, Long> getTargetProgress() { return targetProgress; }
        public void setTargetProgress(Map<String, Long> targetProgress) { this.targetProgress = targetProgress; }
        public Long getCompletionTime() { return completionTime; }
        public void setCompletionTime(Long completionTime) { this.completionTime = completionTime; }
        public Boolean getRewardClaimed() { return rewardClaimed; }
        public void setRewardClaimed(Boolean rewardClaimed) { this.rewardClaimed = rewardClaimed; }
    }

    /**
     * 任务完成结果
     */
    class TaskCompleteResult {
        /** 任务ID */
        private Long taskId;
        /** 完成是否成功 */
        private Boolean success;
        /** 获得的奖励 */
        private List<RewardResult> rewards;
        /** 完成时间 */
        private Long completionTime;

        // Getters and Setters
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public List<RewardResult> getRewards() { return rewards; }
        public void setRewards(List<RewardResult> rewards) { this.rewards = rewards; }
        public Long getCompletionTime() { return completionTime; }
        public void setCompletionTime(Long completionTime) { this.completionTime = completionTime; }
    }

    /**
     * 活动排行榜条目
     */
    class ActivityRankEntry {
        /** 排名 */
        private Integer rank;
        /** 玩家ID */
        private Long playerId;
        /** 玩家名称 */
        private String playerName;
        /** 分数 */
        private Long score;
        /** 更新时间 */
        private Long updateTime;

        // Getters and Setters
        public Integer getRank() { return rank; }
        public void setRank(Integer rank) { this.rank = rank; }
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
    }

    /**
     * 玩家活动排名
     */
    class PlayerActivityRank {
        /** 玩家ID */
        private Long playerId;
        /** 当前排名 */
        private Integer currentRank;
        /** 分数 */
        private Long score;
        /** 最高排名 */
        private Integer bestRank;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Integer getCurrentRank() { return currentRank; }
        public void setCurrentRank(Integer currentRank) { this.currentRank = currentRank; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Integer getBestRank() { return bestRank; }
        public void setBestRank(Integer bestRank) { this.bestRank = bestRank; }
    }

    /**
     * 活动统计信息
     */
    class ActivityStatistics {
        /** 活动ID */
        private Long activityId;
        /** 总参与人数 */
        private Integer totalParticipants;
        /** 完成人数 */
        private Integer completedParticipants;
        /** 奖励发放总数 */
        private Integer totalRewardsGranted;
        /** 平均完成度 */
        private Double averageCompletion;
        /** 最后统计时间 */
        private Long lastStatisticsTime;

        // Getters and Setters
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Integer getTotalParticipants() { return totalParticipants; }
        public void setTotalParticipants(Integer totalParticipants) { this.totalParticipants = totalParticipants; }
        public Integer getCompletedParticipants() { return completedParticipants; }
        public void setCompletedParticipants(Integer completedParticipants) { this.completedParticipants = completedParticipants; }
        public Integer getTotalRewardsGranted() { return totalRewardsGranted; }
        public void setTotalRewardsGranted(Integer totalRewardsGranted) { this.totalRewardsGranted = totalRewardsGranted; }
        public Double getAverageCompletion() { return averageCompletion; }
        public void setAverageCompletion(Double averageCompletion) { this.averageCompletion = averageCompletion; }
        public Long getLastStatisticsTime() { return lastStatisticsTime; }
        public void setLastStatisticsTime(Long lastStatisticsTime) { this.lastStatisticsTime = lastStatisticsTime; }
    }

    /**
     * 玩家活动统计
     */
    class PlayerActivityStatistics {
        /** 玩家ID */
        private Long playerId;
        /** 活动ID */
        private Long activityId;
        /** 参与时间 */
        private Long participationTime;
        /** 完成任务数 */
        private Integer completedTasks;
        /** 领取奖励数 */
        private Integer claimedRewards;
        /** 在线时长（活动期间） */
        private Long activeTime;

        // Getters and Setters
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getParticipationTime() { return participationTime; }
        public void setParticipationTime(Long participationTime) { this.participationTime = participationTime; }
        public Integer getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(Integer completedTasks) { this.completedTasks = completedTasks; }
        public Integer getClaimedRewards() { return claimedRewards; }
        public void setClaimedRewards(Integer claimedRewards) { this.claimedRewards = claimedRewards; }
        public Long getActiveTime() { return activeTime; }
        public void setActiveTime(Long activeTime) { this.activeTime = activeTime; }
    }

    /**
     * 活动参与度报告
     */
    class ActivityParticipationReport {
        /** 活动ID */
        private Long activityId;
        /** 报告时间范围 */
        private Long startTime;
        private Long endTime;
        /** 新增参与人数 */
        private Integer newParticipants;
        /** 活跃参与人数 */
        private Integer activeParticipants;
        /** 完成率 */
        private Double completionRate;
        /** 每日参与数据 */
        private Map<String, Integer> dailyParticipation;

        // Getters and Setters
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public Integer getNewParticipants() { return newParticipants; }
        public void setNewParticipants(Integer newParticipants) { this.newParticipants = newParticipants; }
        public Integer getActiveParticipants() { return activeParticipants; }
        public void setActiveParticipants(Integer activeParticipants) { this.activeParticipants = activeParticipants; }
        public Double getCompletionRate() { return completionRate; }
        public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
        public Map<String, Integer> getDailyParticipation() { return dailyParticipation; }
        public void setDailyParticipation(Map<String, Integer> dailyParticipation) { this.dailyParticipation = dailyParticipation; }
    }
}