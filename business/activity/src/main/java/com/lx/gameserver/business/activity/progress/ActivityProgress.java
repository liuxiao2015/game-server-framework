/*
 * 文件名: ActivityProgress.java
 * 用途: 活动进度实体
 * 实现内容:
 *   - 玩家活动进度数据存储
 *   - 进度数据的Map结构管理
 *   - 完成状态和时间记录
 *   - 自定义数据扩展支持
 * 技术选型:
 *   - 使用Map存储进度数据
 *   - 线程安全的数据访问
 *   - JSON序列化支持
 *   - 时间戳记录
 * 依赖关系:
 *   - 被ProgressTracker使用
 *   - 被活动服务使用
 *   - 与数据库实体映射
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.progress;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动进度实体
 * <p>
 * 记录玩家在特定活动中的进度信息，包括进度数据、完成状态、
 * 更新时间等。支持自定义数据扩展和并发访问。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityProgress {
    
    /** 进度记录ID */
    private Long progressId;
    
    /** 玩家ID */
    private Long playerId;
    
    /** 活动ID */
    private Long activityId;
    
    /** 进度数据（键值对形式存储各种进度） */
    private Map<String, Long> progressData = new ConcurrentHashMap<>();
    
    /** 完成状态（是否达到活动完成条件） */
    private Boolean completed = false;
    
    /** 完成百分比（0-100） */
    private Double completionPercentage = 0.0;
    
    /** 当前阶段或里程碑 */
    private String currentMilestone;
    
    /** 已完成的里程碑列表 */
    private Map<String, Long> completedMilestones = new ConcurrentHashMap<>();
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /** 最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completionTime;
    
    /** 自定义数据（用于存储活动特定的额外信息） */
    private Map<String, Object> customData = new ConcurrentHashMap<>();
    
    /** 版本号（用于乐观锁） */
    private Long version = 0L;
    
    /** 是否已重置 */
    private Boolean reset = false;
    
    /** 重置次数 */
    private Integer resetCount = 0;
    
    /**
     * 构造函数
     *
     * @param playerId   玩家ID
     * @param activityId 活动ID
     */
    public ActivityProgress(Long playerId, Long activityId) {
        this.playerId = playerId;
        this.activityId = activityId;
        this.createTime = LocalDateTime.now();
        this.updateTime = this.createTime;
    }
    
    /**
     * 获取指定进度键的值
     *
     * @param key 进度键
     * @return 进度值，如果不存在返回0
     */
    public Long getProgress(String key) {
        return progressData.getOrDefault(key, 0L);
    }
    
    /**
     * 设置进度值
     *
     * @param key   进度键
     * @param value 进度值
     */
    public void setProgress(String key, Long value) {
        if (key != null && value != null) {
            progressData.put(key, value);
            updateModifyTime();
        }
    }
    
    /**
     * 增加进度值
     *
     * @param key       进度键
     * @param increment 增量
     * @return 更新后的进度值
     */
    public Long addProgress(String key, Long increment) {
        if (key == null || increment == null) {
            return getProgress(key);
        }
        
        Long newValue = progressData.merge(key, increment, Long::sum);
        updateModifyTime();
        return newValue;
    }
    
    /**
     * 检查是否达到指定进度
     *
     * @param key    进度键
     * @param target 目标值
     * @return 是否达到目标
     */
    public boolean hasReached(String key, Long target) {
        if (key == null || target == null) {
            return false;
        }
        return getProgress(key) >= target;
    }
    
    /**
     * 计算指定进度的完成百分比
     *
     * @param key    进度键
     * @param target 目标值
     * @return 完成百分比（0-100）
     */
    public double calculatePercentage(String key, Long target) {
        if (key == null || target == null || target <= 0) {
            return 0.0;
        }
        
        Long current = getProgress(key);
        return Math.min(100.0, (current.doubleValue() / target.doubleValue()) * 100.0);
    }
    
    /**
     * 标记里程碑完成
     *
     * @param milestone     里程碑名称
     * @param completedTime 完成时间戳
     */
    public void completeMilestone(String milestone, Long completedTime) {
        if (milestone != null && completedTime != null) {
            completedMilestones.put(milestone, completedTime);
            this.currentMilestone = milestone;
            updateModifyTime();
        }
    }
    
    /**
     * 检查里程碑是否已完成
     *
     * @param milestone 里程碑名称
     * @return 是否已完成
     */
    public boolean isMilestoneCompleted(String milestone) {
        return milestone != null && completedMilestones.containsKey(milestone);
    }
    
    /**
     * 获取里程碑完成时间
     *
     * @param milestone 里程碑名称
     * @return 完成时间戳，如果未完成返回null
     */
    public Long getMilestoneCompletionTime(String milestone) {
        return completedMilestones.get(milestone);
    }
    
    /**
     * 设置自定义数据
     *
     * @param key   键
     * @param value 值
     */
    public void setCustomData(String key, Object value) {
        if (key != null) {
            customData.put(key, value);
            updateModifyTime();
        }
    }
    
    /**
     * 获取自定义数据
     *
     * @param key 键
     * @return 值
     */
    public Object getCustomData(String key) {
        return customData.get(key);
    }
    
    /**
     * 获取自定义数据（指定类型）
     *
     * @param key  键
     * @param type 类型
     * @param <T>  泛型类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomData(String key, Class<T> type) {
        Object value = customData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 重置进度
     */
    public void resetProgress() {
        progressData.clear();
        completedMilestones.clear();
        completed = false;
        completionPercentage = 0.0;
        currentMilestone = null;
        completionTime = null;
        reset = true;
        resetCount++;
        updateModifyTime();
    }
    
    /**
     * 标记为完成
     */
    public void markCompleted() {
        this.completed = true;
        this.completionPercentage = 100.0;
        this.completionTime = LocalDateTime.now();
        updateModifyTime();
    }
    
    /**
     * 更新完成百分比
     *
     * @param percentage 百分比（0-100）
     */
    public void updateCompletionPercentage(Double percentage) {
        if (percentage != null) {
            this.completionPercentage = Math.max(0.0, Math.min(100.0, percentage));
            
            // 如果达到100%，自动标记为完成
            if (this.completionPercentage >= 100.0 && !completed) {
                markCompleted();
            }
            
            updateModifyTime();
        }
    }
    
    /**
     * 克隆进度对象
     *
     * @return 克隆的进度对象
     */
    public ActivityProgress clone() {
        ActivityProgress cloned = new ActivityProgress();
        cloned.progressId = this.progressId;
        cloned.playerId = this.playerId;
        cloned.activityId = this.activityId;
        cloned.completed = this.completed;
        cloned.completionPercentage = this.completionPercentage;
        cloned.currentMilestone = this.currentMilestone;
        cloned.createTime = this.createTime;
        cloned.updateTime = this.updateTime;
        cloned.completionTime = this.completionTime;
        cloned.version = this.version;
        cloned.reset = this.reset;
        cloned.resetCount = this.resetCount;
        
        // 深拷贝Map数据
        cloned.progressData = new ConcurrentHashMap<>(this.progressData);
        cloned.completedMilestones = new ConcurrentHashMap<>(this.completedMilestones);
        cloned.customData = new ConcurrentHashMap<>(this.customData);
        
        return cloned;
    }
    
    /**
     * 合并其他进度数据
     *
     * @param other 其他进度对象
     */
    public void merge(ActivityProgress other) {
        if (other == null || !this.playerId.equals(other.playerId) || 
            !this.activityId.equals(other.activityId)) {
            return;
        }
        
        // 合并进度数据（取较大值）
        other.progressData.forEach((key, value) -> {
            this.progressData.merge(key, value, Long::max);
        });
        
        // 合并里程碑数据
        other.completedMilestones.forEach((milestone, time) -> {
            if (!this.completedMilestones.containsKey(milestone)) {
                this.completedMilestones.put(milestone, time);
            }
        });
        
        // 合并自定义数据
        other.customData.forEach(this.customData::putIfAbsent);
        
        // 更新状态
        if (other.completed && !this.completed) {
            this.completed = true;
            this.completionTime = other.completionTime;
        }
        
        // 更新完成百分比（取较大值）
        if (other.completionPercentage > this.completionPercentage) {
            this.completionPercentage = other.completionPercentage;
        }
        
        updateModifyTime();
    }
    
    /**
     * 更新修改时间
     */
    private void updateModifyTime() {
        this.updateTime = LocalDateTime.now();
        this.version++;
    }
    
    /**
     * 获取进度摘要信息
     *
     * @return 摘要信息
     */
    public ProgressSummary getSummary() {
        ProgressSummary summary = new ProgressSummary();
        summary.playerId = this.playerId;
        summary.activityId = this.activityId;
        summary.completed = this.completed;
        summary.completionPercentage = this.completionPercentage;
        summary.currentMilestone = this.currentMilestone;
        summary.totalProgress = progressData.values().stream().mapToLong(Long::longValue).sum();
        summary.milestoneCount = completedMilestones.size();
        summary.updateTime = this.updateTime;
        
        return summary;
    }
    
    /**
     * 验证数据完整性
     *
     * @return 验证结果
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        
        if (playerId == null) {
            result.addError("玩家ID不能为空");
        }
        
        if (activityId == null) {
            result.addError("活动ID不能为空");
        }
        
        if (completionPercentage < 0 || completionPercentage > 100) {
            result.addError("完成百分比必须在0-100之间");
        }
        
        if (completed && completionTime == null) {
            result.addWarning("已完成但缺少完成时间");
        }
        
        if (resetCount < 0) {
            result.addError("重置次数不能为负数");
        }
        
        return result;
    }
    
    /**
     * 进度摘要信息
     */
    public static class ProgressSummary {
        public Long playerId;
        public Long activityId;
        public Boolean completed;
        public Double completionPercentage;
        public String currentMilestone;
        public Long totalProgress;
        public Integer milestoneCount;
        public LocalDateTime updateTime;
    }
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean valid = true;
        private final StringBuilder errors = new StringBuilder();
        private final StringBuilder warnings = new StringBuilder();
        
        public void addError(String error) {
            this.valid = false;
            if (errors.length() > 0) {
                errors.append("; ");
            }
            errors.append(error);
        }
        
        public void addWarning(String warning) {
            if (warnings.length() > 0) {
                warnings.append("; ");
            }
            warnings.append(warning);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrors() {
            return errors.toString();
        }
        
        public String getWarnings() {
            return warnings.toString();
        }
    }
    
    @Override
    public String toString() {
        return String.format("ActivityProgress{playerId=%d, activityId=%d, completed=%s, percentage=%.1f%%}", 
                playerId, activityId, completed, completionPercentage);
    }
}