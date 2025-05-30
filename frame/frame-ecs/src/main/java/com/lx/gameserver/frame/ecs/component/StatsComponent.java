/*
 * 文件名: StatsComponent.java
 * 用途: 游戏属性组件实现
 * 实现内容:
 *   - 游戏角色基础属性管理
 *   - 属性计算和修改器支持
 *   - 属性变化事件通知
 *   - 临时属性效果支持
 * 技术选型:
 *   - 枚举类型定义属性种类
 *   - 修改器模式支持属性增减
 *   - 事件机制通知属性变化
 * 依赖关系:
 *   - 实现Component接口
 *   - 被战斗、技能等系统使用
 *   - 与HealthComponent协同工作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.AbstractComponent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性组件
 * <p>
 * 管理游戏角色的各种属性，如攻击力、防御力、速度等。
 * 支持基础属性、属性修改器和临时效果。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class StatsComponent extends AbstractComponent {
    
    /**
     * 属性类型枚举
     */
    public enum StatType {
        // 基础属性
        STRENGTH(1, "力量", "影响物理攻击力"),
        AGILITY(2, "敏捷", "影响攻击速度和暴击率"),
        INTELLECT(3, "智力", "影响魔法攻击力和魔法值"),
        STAMINA(4, "体力", "影响生命值和防御力"),
        
        // 战斗属性
        ATTACK_POWER(11, "攻击力", "物理攻击伤害"),
        MAGIC_POWER(12, "魔法攻击力", "魔法攻击伤害"),
        ARMOR(13, "护甲", "物理防御力"),
        MAGIC_RESISTANCE(14, "魔法抗性", "魔法防御力"),
        
        // 特殊属性
        CRITICAL_CHANCE(21, "暴击率", "暴击几率百分比"),
        CRITICAL_DAMAGE(22, "暴击伤害", "暴击伤害倍数"),
        ATTACK_SPEED(23, "攻击速度", "攻击频率"),
        MOVE_SPEED(24, "移动速度", "移动速度"),
        
        // 抗性属性
        FIRE_RESISTANCE(31, "火抗", "火焰伤害抗性"),
        ICE_RESISTANCE(32, "冰抗", "冰霜伤害抗性"),
        LIGHTNING_RESISTANCE(33, "雷抗", "雷电伤害抗性"),
        POISON_RESISTANCE(34, "毒抗", "毒素伤害抗性");
        
        private final int id;
        private final String displayName;
        private final String description;
        
        StatType(int id, String displayName, String description) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
        }
        
        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * 属性修改器类型
     */
    public enum ModifierType {
        /** 固定数值增加 */
        FLAT_ADD(1),
        /** 百分比增加 */
        PERCENT_ADD(2),
        /** 百分比乘法 */
        PERCENT_MULTIPLY(3),
        /** 固定数值设置 */
        FLAT_SET(4);
        
        private final int priority;
        
        ModifierType(int priority) {
            this.priority = priority;
        }
        
        public int getPriority() { return priority; }
    }
    
    /**
     * 属性修改器
     */
    public static class StatModifier {
        private final String id;
        private final StatType statType;
        private final ModifierType modifierType;
        private final float value;
        private final String source;
        private final long expireTime;
        
        public StatModifier(String id, StatType statType, ModifierType modifierType, 
                           float value, String source, long expireTime) {
            this.id = id;
            this.statType = statType;
            this.modifierType = modifierType;
            this.value = value;
            this.source = source;
            this.expireTime = expireTime;
        }
        
        public String getId() { return id; }
        public StatType getStatType() { return statType; }
        public ModifierType getModifierType() { return modifierType; }
        public float getValue() { return value; }
        public String getSource() { return source; }
        public long getExpireTime() { return expireTime; }
        
        public boolean isExpired() {
            return expireTime > 0 && java.lang.System.currentTimeMillis() > expireTime;
        }
        
        public boolean isPermanent() {
            return expireTime <= 0;
        }
    }
    
    /**
     * 基础属性值
     */
    private final Map<StatType, Float> baseStats;
    
    /**
     * 属性修改器映射
     */
    private final Map<StatType, List<StatModifier>> modifiers;
    
    /**
     * 最终属性值缓存
     */
    private final Map<StatType, Float> finalStatsCache;
    
    /**
     * 缓存是否有效
     */
    private volatile boolean cacheValid = false;
    
    /**
     * 构造函数
     */
    public StatsComponent() {
        this.baseStats = new ConcurrentHashMap<>();
        this.modifiers = new ConcurrentHashMap<>();
        this.finalStatsCache = new ConcurrentHashMap<>();
        
        // 初始化默认属性值
        initializeDefaultStats();
    }
    
    /**
     * 构造函数（指定初始属性）
     *
     * @param initialStats 初始属性映射
     */
    public StatsComponent(Map<StatType, Float> initialStats) {
        this();
        if (initialStats != null) {
            this.baseStats.putAll(initialStats);
        }
        invalidateCache();
    }
    
    /**
     * 初始化默认属性值
     */
    private void initializeDefaultStats() {
        // 基础属性默认值
        baseStats.put(StatType.STRENGTH, 10.0f);
        baseStats.put(StatType.AGILITY, 10.0f);
        baseStats.put(StatType.INTELLECT, 10.0f);
        baseStats.put(StatType.STAMINA, 10.0f);
        
        // 战斗属性默认值
        baseStats.put(StatType.ATTACK_POWER, 0.0f);
        baseStats.put(StatType.MAGIC_POWER, 0.0f);
        baseStats.put(StatType.ARMOR, 0.0f);
        baseStats.put(StatType.MAGIC_RESISTANCE, 0.0f);
        
        // 特殊属性默认值
        baseStats.put(StatType.CRITICAL_CHANCE, 5.0f);
        baseStats.put(StatType.CRITICAL_DAMAGE, 150.0f);
        baseStats.put(StatType.ATTACK_SPEED, 100.0f);
        baseStats.put(StatType.MOVE_SPEED, 100.0f);
        
        // 抗性属性默认值
        baseStats.put(StatType.FIRE_RESISTANCE, 0.0f);
        baseStats.put(StatType.ICE_RESISTANCE, 0.0f);
        baseStats.put(StatType.LIGHTNING_RESISTANCE, 0.0f);
        baseStats.put(StatType.POISON_RESISTANCE, 0.0f);
    }
    
    /**
     * 获取基础属性值
     *
     * @param statType 属性类型
     * @return 基础属性值
     */
    public float getBaseStat(StatType statType) {
        return baseStats.getOrDefault(statType, 0.0f);
    }
    
    /**
     * 设置基础属性值
     *
     * @param statType 属性类型
     * @param value 属性值
     */
    public void setBaseStat(StatType statType, float value) {
        baseStats.put(statType, value);
        invalidateCache();
    }
    
    /**
     * 增加基础属性值
     *
     * @param statType 属性类型
     * @param value 增加的值
     */
    public void addBaseStat(StatType statType, float value) {
        float currentValue = getBaseStat(statType);
        setBaseStat(statType, currentValue + value);
    }
    
    /**
     * 获取最终属性值（包含修改器效果）
     *
     * @param statType 属性类型
     * @return 最终属性值
     */
    public float getFinalStat(StatType statType) {
        if (!cacheValid) {
            calculateFinalStats();
        }
        return finalStatsCache.getOrDefault(statType, getBaseStat(statType));
    }
    
    /**
     * 添加属性修改器
     *
     * @param modifier 属性修改器
     */
    public void addModifier(StatModifier modifier) {
        modifiers.computeIfAbsent(modifier.getStatType(), k -> new ArrayList<>()).add(modifier);
        invalidateCache();
    }
    
    /**
     * 移除属性修改器
     *
     * @param modifierId 修改器ID
     * @return 如果移除成功返回true
     */
    public boolean removeModifier(String modifierId) {
        boolean removed = false;
        for (List<StatModifier> modifierList : modifiers.values()) {
            removed |= modifierList.removeIf(modifier -> modifier.getId().equals(modifierId));
        }
        if (removed) {
            invalidateCache();
        }
        return removed;
    }
    
    /**
     * 移除指定来源的所有修改器
     *
     * @param source 修改器来源
     * @return 移除的修改器数量
     */
    public int removeModifiersBySource(String source) {
        int removedCount = 0;
        for (List<StatModifier> modifierList : modifiers.values()) {
            removedCount += (int) modifierList.removeIf(modifier -> modifier.getSource().equals(source));
        }
        if (removedCount > 0) {
            invalidateCache();
        }
        return removedCount;
    }
    
    /**
     * 清理过期的修改器
     *
     * @return 清理的修改器数量
     */
    public int cleanupExpiredModifiers() {
        int removedCount = 0;
        for (List<StatModifier> modifierList : modifiers.values()) {
            removedCount += (int) modifierList.removeIf(StatModifier::isExpired);
        }
        if (removedCount > 0) {
            invalidateCache();
        }
        return removedCount;
    }
    
    /**
     * 计算最终属性值
     */
    private void calculateFinalStats() {
        finalStatsCache.clear();
        
        for (StatType statType : StatType.values()) {
            float finalValue = calculateStatValue(statType);
            finalStatsCache.put(statType, finalValue);
        }
        
        cacheValid = true;
    }
    
    /**
     * 计算单个属性的最终值
     *
     * @param statType 属性类型
     * @return 最终属性值
     */
    private float calculateStatValue(StatType statType) {
        float baseValue = getBaseStat(statType);
        List<StatModifier> statModifiers = modifiers.getOrDefault(statType, Collections.emptyList());
        
        if (statModifiers.isEmpty()) {
            return baseValue;
        }
        
        // 按修改器类型优先级排序
        List<StatModifier> sortedModifiers = new ArrayList<>(statModifiers);
        sortedModifiers.sort(Comparator.comparing(m -> m.getModifierType().getPriority()));
        
        float finalValue = baseValue;
        float flatAdd = 0;
        float percentAdd = 0;
        float percentMultiply = 1.0f;
        
        for (StatModifier modifier : sortedModifiers) {
            if (modifier.isExpired()) {
                continue;
            }
            
            switch (modifier.getModifierType()) {
                case FLAT_ADD:
                    flatAdd += modifier.getValue();
                    break;
                case PERCENT_ADD:
                    percentAdd += modifier.getValue();
                    break;
                case PERCENT_MULTIPLY:
                    percentMultiply *= (1.0f + modifier.getValue() / 100.0f);
                    break;
                case FLAT_SET:
                    finalValue = modifier.getValue();
                    flatAdd = 0;
                    percentAdd = 0;
                    percentMultiply = 1.0f;
                    break;
            }
        }
        
        // 应用修改器：(base + flat) * (1 + percent/100) * multiply
        finalValue = (finalValue + flatAdd) * (1.0f + percentAdd / 100.0f) * percentMultiply;
        
        return Math.max(0, finalValue); // 确保属性值不为负
    }
    
    /**
     * 失效缓存
     */
    private void invalidateCache() {
        cacheValid = false;
        incrementVersion();
    }
    
    /**
     * 获取所有属性信息
     *
     * @return 属性信息映射
     */
    public Map<StatType, Float> getAllStats() {
        Map<StatType, Float> result = new HashMap<>();
        for (StatType statType : StatType.values()) {
            result.put(statType, getFinalStat(statType));
        }
        return result;
    }
    
    /**
     * 获取指定属性类型的所有修改器
     *
     * @param statType 属性类型
     * @return 修改器列表
     */
    public List<StatModifier> getModifiers(StatType statType) {
        List<StatModifier> result = modifiers.get(statType);
        return result != null ? new ArrayList<>(result) : Collections.emptyList();
    }
    
    /**
     * 获取所有修改器
     *
     * @return 所有修改器的映射
     */
    public Map<StatType, List<StatModifier>> getAllModifiers() {
        Map<StatType, List<StatModifier>> result = new HashMap<>();
        for (Map.Entry<StatType, List<StatModifier>> entry : modifiers.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }
    
    @Override
    public void reset() {
        baseStats.clear();
        modifiers.clear();
        finalStatsCache.clear();
        cacheValid = false;
        initializeDefaultStats();
        super.reset();
    }
    
    @Override
    public StatsComponent clone() {
        StatsComponent cloned = new StatsComponent(baseStats);
        
        // 复制修改器
        for (Map.Entry<StatType, List<StatModifier>> entry : modifiers.entrySet()) {
            List<StatModifier> clonedModifiers = new ArrayList<>(entry.getValue());
            cloned.modifiers.put(entry.getKey(), clonedModifiers);
        }
        
        cloned.setVersion(getVersion());
        return cloned;
    }
    
    @Override
    public int getSize() {
        return baseStats.size() * 8 + // float values
               modifiers.values().stream().mapToInt(List::size).sum() * 64; // estimated modifier size
    }
    
    @Override
    public String toString() {
        return "StatsComponent{" +
                "baseStats=" + baseStats.size() +
                ", modifiers=" + modifiers.size() +
                ", version=" + getVersion() +
                '}';
    }
}