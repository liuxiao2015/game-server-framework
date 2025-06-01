/*
 * 文件名: HealthComponent.java
 * 用途: 生命值组件模板
 * 实现内容:
 *   - 生命值管理（当前/最大）
 *   - 护盾系统支持
 *   - 伤害和治疗处理
 *   - 生命值变化事件
 * 技术选型:
 *   - 数值安全边界检查
 *   - 事件通知机制
 *   - 百分比计算优化
 * 依赖关系:
 *   - 继承AbstractComponent
 *   - 被战斗系统、UI系统使用
 *   - 游戏中核心数值组件
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.AbstractComponent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生命值组件
 * <p>
 * 管理实体的生命值系统，包括当前生命值、最大生命值、护盾等。
 * 提供伤害处理、治疗处理和死亡检测功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HealthComponent extends AbstractComponent {
    
    /**
     * 组件类型ID
     */
    public static final int TYPE_ID = generateTypeId();
    
    /**
     * 当前生命值
     */
    private float currentHealth;
    
    /**
     * 最大生命值
     */
    private float maxHealth;
    
    /**
     * 当前护盾值
     */
    private float currentShield;
    
    /**
     * 最大护盾值
     */
    private float maxShield;
    
    /**
     * 生命值恢复速度（每秒恢复量）
     */
    private float healthRegenRate;
    
    /**
     * 护盾恢复速度（每秒恢复量）
     */
    private float shieldRegenRate;
    
    /**
     * 护盾恢复延迟（受到伤害后多久开始恢复护盾）
     */
    private float shieldRegenDelay = 3.0f;
    
    /**
     * 上次受到伤害的时间戳
     */
    private long lastDamageTime;
    
    /**
     * 是否无敌
     */
    private boolean invincible;
    
    /**
     * 无敌时间剩余（秒）
     */
    private float invincibilityTimeLeft;
    
    /**
     * 是否已死亡
     */
    private boolean dead;
    
    /**
     * 伤害减免百分比（0-1之间）
     */
    private float damageReduction;
    
    /**
     * 护盾吸收百分比（0-1之间，护盾能吸收多少伤害）
     */
    private float shieldAbsorption = 1.0f;
    
    /**
     * 默认构造函数
     */
    public HealthComponent() {
        this(100.0f);
    }
    
    /**
     * 构造函数
     *
     * @param maxHealth 最大生命值
     */
    public HealthComponent(float maxHealth) {
        this(maxHealth, 0.0f);
    }
    
    /**
     * 构造函数
     *
     * @param maxHealth 最大生命值
     * @param maxShield 最大护盾值
     */
    public HealthComponent(float maxHealth, float maxShield) {
        this.maxHealth = Math.max(0, maxHealth);
        this.currentHealth = this.maxHealth;
        this.maxShield = Math.max(0, maxShield);
        this.currentShield = this.maxShield;
        this.lastDamageTime = java.lang.System.currentTimeMillis();
    }
    
    @Override
    public int getTypeId() {
        return TYPE_ID;
    }
    
    @Override
    public void reset() {
        super.reset();
        currentHealth = 100.0f;
        maxHealth = 100.0f;
        currentShield = 0.0f;
        maxShield = 0.0f;
        healthRegenRate = 0.0f;
        shieldRegenRate = 0.0f;
        shieldRegenDelay = 3.0f;
        lastDamageTime = java.lang.System.currentTimeMillis();
        invincible = false;
        invincibilityTimeLeft = 0.0f;
        dead = false;
        damageReduction = 0.0f;
        shieldAbsorption = 1.0f;
    }
    
    /**
     * 设置最大生命值
     *
     * @param maxHealth 最大生命值
     */
    public void setMaxHealth(float maxHealth) {
        this.maxHealth = Math.max(0, maxHealth);
        // 如果当前生命值超过新的最大值，则调整
        if (currentHealth > this.maxHealth) {
            currentHealth = this.maxHealth;
        }
        notifyModified();
    }
    
    /**
     * 设置当前生命值
     *
     * @param currentHealth 当前生命值
     */
    public void setCurrentHealth(float currentHealth) {
        this.currentHealth = Math.max(0, Math.min(maxHealth, currentHealth));
        updateDeathState();
        notifyModified();
    }
    
    /**
     * 设置最大护盾值
     *
     * @param maxShield 最大护盾值
     */
    public void setMaxShield(float maxShield) {
        this.maxShield = Math.max(0, maxShield);
        // 如果当前护盾值超过新的最大值，则调整
        if (currentShield > this.maxShield) {
            currentShield = this.maxShield;
        }
        notifyModified();
    }
    
    /**
     * 设置当前护盾值
     *
     * @param currentShield 当前护盾值
     */
    public void setCurrentShield(float currentShield) {
        this.currentShield = Math.max(0, Math.min(maxShield, currentShield));
        notifyModified();
    }
    
    /**
     * 造成伤害
     *
     * @param damage 伤害值
     * @return 实际造成的伤害
     */
    public float takeDamage(float damage) {
        if (damage <= 0 || invincible || dead) {
            return 0.0f;
        }
        
        // 应用伤害减免
        float actualDamage = damage * (1.0f - damageReduction);
        float totalDamage = actualDamage;
        
        // 护盾先承受伤害
        if (currentShield > 0 && shieldAbsorption > 0) {
            float shieldDamage = Math.min(currentShield, actualDamage * shieldAbsorption);
            currentShield -= shieldDamage;
            actualDamage -= shieldDamage;
        }
        
        // 剩余伤害作用于生命值
        if (actualDamage > 0) {
            currentHealth -= actualDamage;
            if (currentHealth < 0) {
                currentHealth = 0;
            }
        }
        
        lastDamageTime = java.lang.System.currentTimeMillis();
        updateDeathState();
        notifyModified();
        
        return totalDamage;
    }
    
    /**
     * 治疗生命值
     *
     * @param healAmount 治疗量
     * @return 实际治疗量
     */
    public float heal(float healAmount) {
        if (healAmount <= 0 || dead) {
            return 0.0f;
        }
        
        float oldHealth = currentHealth;
        currentHealth = Math.min(maxHealth, currentHealth + healAmount);
        float actualHeal = currentHealth - oldHealth;
        
        if (actualHeal > 0) {
            notifyModified();
        }
        
        return actualHeal;
    }
    
    /**
     * 恢复护盾
     *
     * @param shieldAmount 护盾恢复量
     * @return 实际恢复量
     */
    public float restoreShield(float shieldAmount) {
        if (shieldAmount <= 0) {
            return 0.0f;
        }
        
        float oldShield = currentShield;
        currentShield = Math.min(maxShield, currentShield + shieldAmount);
        float actualRestore = currentShield - oldShield;
        
        if (actualRestore > 0) {
            notifyModified();
        }
        
        return actualRestore;
    }
    
    /**
     * 完全恢复生命值
     */
    public void fullHeal() {
        setCurrentHealth(maxHealth);
    }
    
    /**
     * 完全恢复护盾
     */
    public void fullShieldRestore() {
        setCurrentShield(maxShield);
    }
    
    /**
     * 完全恢复生命值和护盾
     */
    public void fullRestore() {
        fullHeal();
        fullShieldRestore();
    }
    
    /**
     * 设置无敌状态
     *
     * @param duration 无敌持续时间（秒）
     */
    public void setInvincible(float duration) {
        this.invincible = true;
        this.invincibilityTimeLeft = duration;
        notifyModified();
    }
    
    /**
     * 取消无敌状态
     */
    public void removeInvincibility() {
        this.invincible = false;
        this.invincibilityTimeLeft = 0.0f;
        notifyModified();
    }
    
    /**
     * 更新组件状态（每帧调用）
     *
     * @param deltaTime 时间增量（秒）
     */
    public void update(float deltaTime) {
        boolean modified = false;
        
        // 更新无敌时间
        if (invincible && invincibilityTimeLeft > 0) {
            invincibilityTimeLeft -= deltaTime;
            if (invincibilityTimeLeft <= 0) {
                invincible = false;
                invincibilityTimeLeft = 0;
                modified = true;
            }
        }
        
        // 生命值自然恢复
        if (healthRegenRate > 0 && currentHealth < maxHealth && !dead) {
            float oldHealth = currentHealth;
            currentHealth = Math.min(maxHealth, currentHealth + healthRegenRate * deltaTime);
            if (currentHealth != oldHealth) {
                modified = true;
            }
        }
        
        // 护盾自然恢复（需要延迟）
        if (shieldRegenRate > 0 && currentShield < maxShield) {
            long currentTime = java.lang.System.currentTimeMillis();
            if (currentTime - lastDamageTime >= shieldRegenDelay * 1000) {
                float oldShield = currentShield;
                currentShield = Math.min(maxShield, currentShield + shieldRegenRate * deltaTime);
                if (currentShield != oldShield) {
                    modified = true;
                }
            }
        }
        
        if (modified) {
            notifyModified();
        }
    }
    
    /**
     * 更新死亡状态
     */
    private void updateDeathState() {
        boolean wasDead = dead;
        dead = currentHealth <= 0;
        
        // 如果从活着变成死亡，可以在这里触发死亡事件
        if (!wasDead && dead) {
            // 触发死亡事件
        } else if (wasDead && !dead) {
            // 触发复活事件
        }
    }
    
    /**
     * 获取生命值百分比
     *
     * @return 生命值百分比（0-1之间）
     */
    public float getHealthPercentage() {
        return maxHealth > 0 ? currentHealth / maxHealth : 0.0f;
    }
    
    /**
     * 获取护盾百分比
     *
     * @return 护盾百分比（0-1之间）
     */
    public float getShieldPercentage() {
        return maxShield > 0 ? currentShield / maxShield : 0.0f;
    }
    
    /**
     * 获取总有效生命值（生命值+护盾）
     *
     * @return 总有效生命值
     */
    public float getTotalEffectiveHealth() {
        return currentHealth + currentShield;
    }
    
    /**
     * 获取最大总有效生命值
     *
     * @return 最大总有效生命值
     */
    public float getMaxTotalEffectiveHealth() {
        return maxHealth + maxShield;
    }
    
    /**
     * 检查是否满血满盾
     *
     * @return 如果满血满盾返回true
     */
    public boolean isFullHealth() {
        return Float.compare(currentHealth, maxHealth) == 0 && 
               Float.compare(currentShield, maxShield) == 0;
    }
    
    /**
     * 检查是否处于危险状态（生命值低于某个百分比）
     *
     * @param threshold 危险阈值（0-1之间）
     * @return 如果处于危险状态返回true
     */
    public boolean isInDanger(float threshold) {
        return getHealthPercentage() < threshold;
    }
    
    /**
     * 检查是否有护盾
     *
     * @return 如果有护盾返回true
     */
    public boolean hasShield() {
        return currentShield > 0;
    }
    
    /**
     * 瞬间击杀（忽略护盾和无敌）
     */
    public void instantKill() {
        currentHealth = 0;
        currentShield = 0;
        invincible = false;
        invincibilityTimeLeft = 0;
        updateDeathState();
        notifyModified();
    }
    
    @Override
    public boolean isValid() {
        return maxHealth >= 0 && currentHealth >= 0 && currentHealth <= maxHealth &&
               maxShield >= 0 && currentShield >= 0 && currentShield <= maxShield &&
               damageReduction >= 0 && damageReduction <= 1 &&
               shieldAbsorption >= 0 && shieldAbsorption <= 1;
    }
    
    @Override
    public int getSize() {
        return Float.BYTES * 9 + Long.BYTES + 3; // 9个float + 1个long + 3个boolean
    }
    
    @Override
    public String toString() {
        return String.format("Health{hp=%.1f/%.1f(%.0f%%), shield=%.1f/%.1f, dead=%s, invincible=%s}", 
            currentHealth, maxHealth, getHealthPercentage() * 100,
            currentShield, maxShield, dead, invincible);
    }
}