/*
 * 文件名: ActivityReward.java
 * 用途: 活动奖励实体
 * 实现内容:
 *   - 活动奖励的基本信息管理
 *   - 奖励类型和数量配置
 *   - 领取条件和限制处理
 *   - 奖励状态跟踪
 * 技术选型:
 *   - 实体类设计
 *   - 枚举类型管理
 *   - JSON序列化支持
 *   - 数据验证机制
 * 依赖关系:
 *   - 被RewardService使用
 *   - 与数据库实体映射
 *   - 被奖励计算器使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.reward;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 活动奖励实体
 * <p>
 * 定义活动奖励的基本信息，包括奖励类型、数量、领取条件等。
 * 支持多种奖励类型和复杂的领取限制条件。
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
public class ActivityReward {
    
    /** 奖励ID */
    private Long rewardId;
    
    /** 活动ID */
    private Long activityId;
    
    /** 奖励名称 */
    private String rewardName;
    
    /** 奖励描述 */
    private String description;
    
    /** 奖励类型 */
    private RewardType rewardType;
    
    /** 奖励项目ID（如道具ID、货币类型等） */
    private Integer itemId;
    
    /** 奖励数量 */
    private Long quantity;
    
    /** 奖励等级或品质 */
    private Integer quality;
    
    /** 领取条件 */
    private Map<String, Object> claimConditions = new HashMap<>();
    
    /** 领取限制 */
    private Map<String, Object> claimLimitations = new HashMap<>();
    
    /** 奖励优先级 */
    private Integer priority = 0;
    
    /** 是否可重复领取 */
    private Boolean repeatable = false;
    
    /** 奖励分组（用于批量操作） */
    private String rewardGroup;
    
    /** 奖励阶段或里程碑 */
    private String milestone;
    
    /** 生效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime effectiveTime;
    
    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    /** 奖励状态 */
    private RewardStatus status = RewardStatus.ACTIVE;
    
    /** 扩展属性 */
    private Map<String, Object> attributes = new HashMap<>();
    
    /** 版本号 */
    private Long version = 0L;
    
    /**
     * 奖励类型枚举
     */
    public enum RewardType {
        /** 道具奖励 */
        ITEM("item", "道具", "游戏道具奖励"),
        
        /** 货币奖励 */
        CURRENCY("currency", "货币", "游戏货币奖励"),
        
        /** 经验奖励 */
        EXPERIENCE("experience", "经验", "角色经验奖励"),
        
        /** VIP经验奖励 */
        VIP_EXP("vip_exp", "VIP经验", "VIP经验奖励"),
        
        /** 称号奖励 */
        TITLE("title", "称号", "游戏称号奖励"),
        
        /** 技能奖励 */
        SKILL("skill", "技能", "技能点数奖励"),
        
        /** 装备奖励 */
        EQUIPMENT("equipment", "装备", "装备奖励"),
        
        /** 宝石奖励 */
        GEM("gem", "宝石", "宝石奖励"),
        
        /** 卡牌奖励 */
        CARD("card", "卡牌", "卡牌奖励"),
        
        /** 礼包奖励 */
        PACKAGE("package", "礼包", "礼包奖励"),
        
        /** 自定义奖励 */
        CUSTOM("custom", "自定义", "自定义类型奖励");
        
        private final String code;
        private final String name;
        private final String description;
        
        RewardType(String code, String name, String description) {
            this.code = code;
            this.name = name;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        
        public static RewardType fromCode(String code) {
            for (RewardType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    /**
     * 奖励状态枚举
     */
    public enum RewardStatus {
        /** 有效 */
        ACTIVE("active", "有效"),
        
        /** 已禁用 */
        DISABLED("disabled", "已禁用"),
        
        /** 已过期 */
        EXPIRED("expired", "已过期"),
        
        /** 已删除 */
        DELETED("deleted", "已删除");
        
        private final String code;
        private final String description;
        
        RewardStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    /**
     * 构造函数
     *
     * @param activityId  活动ID
     * @param rewardType  奖励类型
     * @param itemId      道具ID
     * @param quantity    数量
     */
    public ActivityReward(Long activityId, RewardType rewardType, Integer itemId, Long quantity) {
        this.activityId = activityId;
        this.rewardType = rewardType;
        this.itemId = itemId;
        this.quantity = quantity;
        this.createTime = LocalDateTime.now();
        this.updateTime = this.createTime;
    }
    
    /**
     * 设置领取条件
     *
     * @param key   条件键
     * @param value 条件值
     */
    public void setClaimCondition(String key, Object value) {
        if (key != null && value != null) {
            claimConditions.put(key, value);
            updateModifyTime();
        }
    }
    
    /**
     * 获取领取条件
     *
     * @param key 条件键
     * @return 条件值
     */
    public Object getClaimCondition(String key) {
        return claimConditions.get(key);
    }
    
    /**
     * 检查是否有指定条件
     *
     * @param key 条件键
     * @return 是否存在
     */
    public boolean hasClaimCondition(String key) {
        return claimConditions.containsKey(key);
    }
    
    /**
     * 设置领取限制
     *
     * @param key   限制键
     * @param value 限制值
     */
    public void setClaimLimitation(String key, Object value) {
        if (key != null && value != null) {
            claimLimitations.put(key, value);
            updateModifyTime();
        }
    }
    
    /**
     * 获取领取限制
     *
     * @param key 限制键
     * @return 限制值
     */
    public Object getClaimLimitation(String key) {
        return claimLimitations.get(key);
    }
    
    /**
     * 设置扩展属性
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        if (key != null) {
            attributes.put(key, value);
            updateModifyTime();
        }
    }
    
    /**
     * 获取扩展属性
     *
     * @param key 属性键
     * @return 属性值
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    /**
     * 获取扩展属性（指定类型）
     *
     * @param key  属性键
     * @param type 类型
     * @param <T>  泛型类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 检查奖励是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        
        // 检查状态
        if (status != RewardStatus.ACTIVE) {
            return false;
        }
        
        // 检查生效时间
        if (effectiveTime != null && now.isBefore(effectiveTime)) {
            return false;
        }
        
        // 检查过期时间
        if (expireTime != null && now.isAfter(expireTime)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否已过期
     *
     * @return 是否已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }
    
    /**
     * 检查是否已生效
     *
     * @return 是否已生效
     */
    public boolean isEffective() {
        return effectiveTime == null || LocalDateTime.now().isAfter(effectiveTime);
    }
    
    /**
     * 获取奖励权重（用于排序）
     *
     * @return 权重值
     */
    public double getWeight() {
        // 基础权重
        double weight = priority != null ? priority : 0;
        
        // 根据奖励类型调整权重
        switch (rewardType) {
            case CURRENCY:
                weight += 1000;
                break;
            case EXPERIENCE:
                weight += 500;
                break;
            case ITEM:
                weight += 300;
                break;
            default:
                weight += 100;
                break;
        }
        
        // 根据数量调整权重
        if (quantity != null) {
            weight += Math.log(quantity + 1) * 10;
        }
        
        return weight;
    }
    
    /**
     * 克隆奖励对象
     *
     * @return 克隆的奖励对象
     */
    public ActivityReward clone() {
        ActivityReward cloned = new ActivityReward();
        cloned.rewardId = this.rewardId;
        cloned.activityId = this.activityId;
        cloned.rewardName = this.rewardName;
        cloned.description = this.description;
        cloned.rewardType = this.rewardType;
        cloned.itemId = this.itemId;
        cloned.quantity = this.quantity;
        cloned.quality = this.quality;
        cloned.priority = this.priority;
        cloned.repeatable = this.repeatable;
        cloned.rewardGroup = this.rewardGroup;
        cloned.milestone = this.milestone;
        cloned.effectiveTime = this.effectiveTime;
        cloned.expireTime = this.expireTime;
        cloned.createTime = this.createTime;
        cloned.updateTime = this.updateTime;
        cloned.status = this.status;
        cloned.version = this.version;
        
        // 深拷贝Map数据
        cloned.claimConditions = new HashMap<>(this.claimConditions);
        cloned.claimLimitations = new HashMap<>(this.claimLimitations);
        cloned.attributes = new HashMap<>(this.attributes);
        
        return cloned;
    }
    
    /**
     * 验证奖励数据
     *
     * @return 验证结果
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        
        if (activityId == null) {
            result.addError("活动ID不能为空");
        }
        
        if (rewardType == null) {
            result.addError("奖励类型不能为空");
        }
        
        if (quantity == null || quantity <= 0) {
            result.addError("奖励数量必须大于0");
        }
        
        if (priority != null && priority < 0) {
            result.addError("奖励优先级不能为负数");
        }
        
        if (effectiveTime != null && expireTime != null && 
            effectiveTime.isAfter(expireTime)) {
            result.addError("生效时间不能晚于过期时间");
        }
        
        // 检查特定类型的验证
        if (rewardType == RewardType.ITEM && itemId == null) {
            result.addError("道具奖励必须指定道具ID");
        }
        
        return result;
    }
    
    /**
     * 转换为显示信息
     *
     * @return 显示信息
     */
    public RewardDisplayInfo toDisplayInfo() {
        RewardDisplayInfo info = new RewardDisplayInfo();
        info.rewardId = this.rewardId;
        info.name = this.rewardName;
        info.description = this.description;
        info.type = this.rewardType.getName();
        info.quantity = this.quantity;
        info.quality = this.quality;
        info.icon = this.getAttribute("icon", String.class);
        info.rarity = this.getAttribute("rarity", String.class);
        
        return info;
    }
    
    /**
     * 更新修改时间
     */
    private void updateModifyTime() {
        this.updateTime = LocalDateTime.now();
        this.version++;
    }
    
    /**
     * 奖励显示信息
     */
    public static class RewardDisplayInfo {
        public Long rewardId;
        public String name;
        public String description;
        public String type;
        public Long quantity;
        public Integer quality;
        public String icon;
        public String rarity;
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
        return String.format("ActivityReward{id=%d, activityId=%d, type=%s, itemId=%d, quantity=%d}", 
                rewardId, activityId, rewardType, itemId, quantity);
    }
}