/*
 * 文件名: AIComponent.java
 * 用途: AI组件实现
 * 实现内容:
 *   - AI状态管理
 *   - AI行为树支持
 *   - AI决策逻辑
 *   - AI目标追踪
 *   - AI参数配置
 * 技术选型:
 *   - 状态机模式管理AI状态
 *   - 行为树实现复杂AI逻辑
 *   - 策略模式支持不同AI类型
 * 依赖关系:
 *   - 实现Component接口
 *   - 被AI系统使用
 *   - 与其他组件协同工作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.AbstractComponent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI组件
 * <p>
 * 管理游戏实体的AI行为，包括状态管理、目标追踪、决策逻辑等。
 * 支持多种AI类型和自定义行为。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class AIComponent extends AbstractComponent {
    
    /**
     * AI类型枚举
     */
    public enum AIType {
        /** 被动AI - 不主动攻击 */
        PASSIVE(1, "被动", "不会主动攻击玩家"),
        /** 主动AI - 主动攻击 */
        AGGRESSIVE(2, "主动", "会主动攻击附近的玩家"),
        /** 防御AI - 被攻击后反击 */
        DEFENSIVE(3, "防御", "被攻击后会反击"),
        /** 巡逻AI - 按路径巡逻 */
        PATROL(4, "巡逻", "按照预设路径巡逻"),
        /** 守卫AI - 守护特定区域 */
        GUARD(5, "守卫", "守护特定区域，驱逐入侵者"),
        /** 跟随AI - 跟随目标 */
        FOLLOW(6, "跟随", "跟随指定目标移动"),
        /** 逃跑AI - 遇到威胁时逃跑 */
        FLEE(7, "逃跑", "遇到威胁时会逃跑"),
        /** 自定义AI - 使用脚本控制 */
        CUSTOM(99, "自定义", "使用脚本控制的AI");
        
        private final int id;
        private final String displayName;
        private final String description;
        
        AIType(int id, String displayName, String description) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
        }
        
        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * AI状态枚举
     */
    public enum AIState {
        /** 空闲状态 */
        IDLE(1, "空闲"),
        /** 巡逻状态 */
        PATROL(2, "巡逻"),
        /** 追击状态 */
        CHASE(3, "追击"),
        /** 攻击状态 */
        ATTACK(4, "攻击"),
        /** 逃跑状态 */
        FLEE(5, "逃跑"),
        /** 返回状态 */
        RETURN(6, "返回"),
        /** 死亡状态 */
        DEAD(7, "死亡"),
        /** 眩晕状态 */
        STUNNED(8, "眩晕"),
        /** 睡眠状态 */
        SLEEP(9, "睡眠");
        
        private final int id;
        private final String displayName;
        
        AIState(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * AI行为接口
     */
    public interface AIBehavior {
        /**
         * 执行行为
         *
         * @param aiComponent AI组件
         * @param deltaTime 时间增量
         * @return 行为是否成功执行
         */
        boolean execute(AIComponent aiComponent, float deltaTime);
        
        /**
         * 检查行为是否可以执行
         *
         * @param aiComponent AI组件
         * @return 如果可以执行返回true
         */
        boolean canExecute(AIComponent aiComponent);
        
        /**
         * 获取行为优先级
         *
         * @return 优先级（数值越高优先级越高）
         */
        int getPriority();
        
        /**
         * 获取行为名称
         *
         * @return 行为名称
         */
        String getName();
    }
    
    /**
     * AI目标信息
     */
    public static class AITarget {
        private long entityId;
        private float lastKnownX;
        private float lastKnownY;
        private float lastKnownZ;
        private long lastSeenTime;
        private float threatLevel;
        private String targetType;
        
        public AITarget(long entityId, float x, float y, float z) {
            this.entityId = entityId;
            this.lastKnownX = x;
            this.lastKnownY = y;
            this.lastKnownZ = z;
            this.lastSeenTime = java.lang.System.currentTimeMillis();
            this.threatLevel = 1.0f;
            this.targetType = "unknown";
        }
        
        public void updatePosition(float x, float y, float z) {
            this.lastKnownX = x;
            this.lastKnownY = y;
            this.lastKnownZ = z;
            this.lastSeenTime = java.lang.System.currentTimeMillis();
        }
        
        public boolean isValid(long maxAge) {
            return java.lang.System.currentTimeMillis() - lastSeenTime <= maxAge;
        }
        
        // Getters and Setters
        public long getEntityId() { return entityId; }
        public float getLastKnownX() { return lastKnownX; }
        public float getLastKnownY() { return lastKnownY; }
        public float getLastKnownZ() { return lastKnownZ; }
        public long getLastSeenTime() { return lastSeenTime; }
        public float getThreatLevel() { return threatLevel; }
        public void setThreatLevel(float threatLevel) { this.threatLevel = threatLevel; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
    }
    
    /**
     * AI参数
     */
    public static class AIParameters {
        private float detectionRange = 10.0f;
        private float attackRange = 3.0f;
        private float chaseRange = 15.0f;
        private float returnRange = 20.0f;
        private float moveSpeed = 5.0f;
        private long targetMemoryTime = 10000; // 10秒
        private int maxTargets = 5;
        private float aggressiveness = 1.0f;
        private boolean canFly = false;
        private boolean canSwim = false;
        
        // Getters and Setters
        public float getDetectionRange() { return detectionRange; }
        public void setDetectionRange(float detectionRange) { this.detectionRange = Math.max(0, detectionRange); }
        
        public float getAttackRange() { return attackRange; }
        public void setAttackRange(float attackRange) { this.attackRange = Math.max(0, attackRange); }
        
        public float getChaseRange() { return chaseRange; }
        public void setChaseRange(float chaseRange) { this.chaseRange = Math.max(0, chaseRange); }
        
        public float getReturnRange() { return returnRange; }
        public void setReturnRange(float returnRange) { this.returnRange = Math.max(0, returnRange); }
        
        public float getMoveSpeed() { return moveSpeed; }
        public void setMoveSpeed(float moveSpeed) { this.moveSpeed = Math.max(0, moveSpeed); }
        
        public long getTargetMemoryTime() { return targetMemoryTime; }
        public void setTargetMemoryTime(long targetMemoryTime) { this.targetMemoryTime = Math.max(0, targetMemoryTime); }
        
        public int getMaxTargets() { return maxTargets; }
        public void setMaxTargets(int maxTargets) { this.maxTargets = Math.max(1, maxTargets); }
        
        public float getAggressiveness() { return aggressiveness; }
        public void setAggressiveness(float aggressiveness) { this.aggressiveness = Math.max(0, aggressiveness); }
        
        public boolean isCanFly() { return canFly; }
        public void setCanFly(boolean canFly) { this.canFly = canFly; }
        
        public boolean isCanSwim() { return canSwim; }
        public void setCanSwim(boolean canSwim) { this.canSwim = canSwim; }
    }
    
    /**
     * AI类型
     */
    private AIType aiType;
    
    /**
     * 当前AI状态
     */
    private AIState currentState;
    
    /**
     * 上一个AI状态
     */
    private AIState previousState;
    
    /**
     * 状态改变时间
     */
    private long stateChangeTime;
    
    /**
     * AI参数
     */
    private final AIParameters parameters;
    
    /**
     * 当前目标
     */
    private AITarget currentTarget;
    
    /**
     * 目标列表
     */
    private final Map<Long, AITarget> targets;
    
    /**
     * AI行为列表
     */
    private final List<AIBehavior> behaviors;
    
    /**
     * 是否启用AI
     */
    private boolean enabled = true;
    
    /**
     * 出生点坐标
     */
    private float spawnX;
    private float spawnY;
    private float spawnZ;
    
    /**
     * 巡逻路径点
     */
    private final List<float[]> patrolPoints;
    
    /**
     * 当前巡逻点索引
     */
    private int currentPatrolIndex = 0;
    
    /**
     * 自定义属性
     */
    private final Map<String, Object> customProperties;
    
    /**
     * 构造函数
     *
     * @param aiType AI类型
     */
    public AIComponent(AIType aiType) {
        this.aiType = aiType;
        this.currentState = AIState.IDLE;
        this.previousState = AIState.IDLE;
        this.stateChangeTime = java.lang.System.currentTimeMillis();
        this.parameters = new AIParameters();
        this.targets = new ConcurrentHashMap<>();
        this.behaviors = new ArrayList<>();
        this.patrolPoints = new ArrayList<>();
        this.customProperties = new ConcurrentHashMap<>();
    }
    
    /**
     * 构造函数（默认被动AI）
     */
    public AIComponent() {
        this(AIType.PASSIVE);
    }
    
    /**
     * 设置出生点
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    public void setSpawnPoint(float x, float y, float z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
    }
    
    /**
     * 添加巡逻点
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    public void addPatrolPoint(float x, float y, float z) {
        patrolPoints.add(new float[]{x, y, z});
    }
    
    /**
     * 获取下一个巡逻点
     *
     * @return 巡逻点坐标，如果没有巡逻点返回null
     */
    public float[] getNextPatrolPoint() {
        if (patrolPoints.isEmpty()) {
            return null;
        }
        
        currentPatrolIndex = (currentPatrolIndex + 1) % patrolPoints.size();
        return patrolPoints.get(currentPatrolIndex).clone();
    }
    
    /**
     * 获取当前巡逻点
     *
     * @return 当前巡逻点坐标，如果没有巡逻点返回null
     */
    public float[] getCurrentPatrolPoint() {
        if (patrolPoints.isEmpty()) {
            return null;
        }
        return patrolPoints.get(currentPatrolIndex).clone();
    }
    
    /**
     * 设置AI状态
     *
     * @param newState 新状态
     */
    public void setState(AIState newState) {
        if (currentState != newState) {
            this.previousState = this.currentState;
            this.currentState = newState;
            this.stateChangeTime = java.lang.System.currentTimeMillis();
            incrementVersion();
        }
    }
    
    /**
     * 添加目标
     *
     * @param target AI目标
     */
    public void addTarget(AITarget target) {
        if (targets.size() >= parameters.getMaxTargets()) {
            // 移除最旧的目标
            AITarget oldestTarget = targets.values().stream()
                    .min(Comparator.comparingLong(AITarget::getLastSeenTime))
                    .orElse(null);
            if (oldestTarget != null) {
                targets.remove(oldestTarget.getEntityId());
            }
        }
        
        targets.put(target.getEntityId(), target);
        
        // 如果没有当前目标，设置为当前目标
        if (currentTarget == null) {
            currentTarget = target;
        }
        
        incrementVersion();
    }
    
    /**
     * 移除目标
     *
     * @param entityId 实体ID
     */
    public void removeTarget(long entityId) {
        AITarget removed = targets.remove(entityId);
        if (removed != null) {
            if (currentTarget != null && currentTarget.getEntityId() == entityId) {
                // 选择下一个目标
                currentTarget = targets.values().stream()
                        .max(Comparator.comparingFloat(AITarget::getThreatLevel))
                        .orElse(null);
            }
            incrementVersion();
        }
    }
    
    /**
     * 清理过期目标
     */
    public void cleanupExpiredTargets() {
        long maxAge = parameters.getTargetMemoryTime();
        boolean removed = targets.entrySet().removeIf(entry -> !entry.getValue().isValid(maxAge));
        
        if (removed) {
            // 检查当前目标是否还有效
            if (currentTarget != null && !currentTarget.isValid(maxAge)) {
                currentTarget = targets.values().stream()
                        .max(Comparator.comparingFloat(AITarget::getThreatLevel))
                        .orElse(null);
            }
            incrementVersion();
        }
    }
    
    /**
     * 添加AI行为
     *
     * @param behavior AI行为
     */
    public void addBehavior(AIBehavior behavior) {
        behaviors.add(behavior);
        // 按优先级排序
        behaviors.sort(Comparator.comparingInt(AIBehavior::getPriority).reversed());
    }
    
    /**
     * 移除AI行为
     *
     * @param behaviorName 行为名称
     * @return 如果移除成功返回true
     */
    public boolean removeBehavior(String behaviorName) {
        return behaviors.removeIf(behavior -> behavior.getName().equals(behaviorName));
    }
    
    /**
     * 执行AI逻辑
     *
     * @param deltaTime 时间增量
     */
    public void update(float deltaTime) {
        if (!enabled) {
            return;
        }
        
        // 清理过期目标
        cleanupExpiredTargets();
        
        // 执行行为
        for (AIBehavior behavior : behaviors) {
            if (behavior.canExecute(this)) {
                if (behavior.execute(this, deltaTime)) {
                    break; // 如果行为执行成功，停止执行其他行为
                }
            }
        }
    }
    
    /**
     * 获取到出生点的距离
     *
     * @param currentX 当前X坐标
     * @param currentY 当前Y坐标
     * @param currentZ 当前Z坐标
     * @return 到出生点的距离
     */
    public float getDistanceToSpawn(float currentX, float currentY, float currentZ) {
        float dx = currentX - spawnX;
        float dy = currentY - spawnY;
        float dz = currentZ - spawnZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * 设置自定义属性
     *
     * @param key 属性名
     * @param value 属性值
     */
    public void setCustomProperty(String key, Object value) {
        customProperties.put(key, value);
        incrementVersion();
    }
    
    /**
     * 获取自定义属性
     *
     * @param key 属性名
     * @param defaultValue 默认值
     * @param <T> 属性类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomProperty(String key, T defaultValue) {
        Object value = customProperties.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    @Override
    public void reset() {
        currentState = AIState.IDLE;
        previousState = AIState.IDLE;
        stateChangeTime = java.lang.System.currentTimeMillis();
        currentTarget = null;
        targets.clear();
        behaviors.clear();
        patrolPoints.clear();
        currentPatrolIndex = 0;
        customProperties.clear();
        enabled = true;
        super.reset();
    }
    
    @Override
    public AIComponent clone() {
        AIComponent cloned = new AIComponent(aiType);
        cloned.currentState = this.currentState;
        cloned.previousState = this.previousState;
        cloned.stateChangeTime = this.stateChangeTime;
        cloned.enabled = this.enabled;
        cloned.spawnX = this.spawnX;
        cloned.spawnY = this.spawnY;
        cloned.spawnZ = this.spawnZ;
        cloned.currentPatrolIndex = this.currentPatrolIndex;
        
        // 复制参数
        cloned.parameters.setDetectionRange(this.parameters.getDetectionRange());
        cloned.parameters.setAttackRange(this.parameters.getAttackRange());
        cloned.parameters.setChaseRange(this.parameters.getChaseRange());
        cloned.parameters.setReturnRange(this.parameters.getReturnRange());
        cloned.parameters.setMoveSpeed(this.parameters.getMoveSpeed());
        cloned.parameters.setTargetMemoryTime(this.parameters.getTargetMemoryTime());
        cloned.parameters.setMaxTargets(this.parameters.getMaxTargets());
        cloned.parameters.setAggressiveness(this.parameters.getAggressiveness());
        cloned.parameters.setCanFly(this.parameters.isCanFly());
        cloned.parameters.setCanSwim(this.parameters.isCanSwim());
        
        // 复制目标（不包括当前目标引用，避免共享状态）
        for (AITarget target : targets.values()) {
            AITarget clonedTarget = new AITarget(target.getEntityId(), 
                    target.getLastKnownX(), target.getLastKnownY(), target.getLastKnownZ());
            clonedTarget.setThreatLevel(target.getThreatLevel());
            clonedTarget.setTargetType(target.getTargetType());
            cloned.targets.put(target.getEntityId(), clonedTarget);
        }
        
        // 复制巡逻点
        for (float[] point : patrolPoints) {
            cloned.patrolPoints.add(point.clone());
        }
        
        // 复制自定义属性
        cloned.customProperties.putAll(this.customProperties);
        
        // 注意：行为不复制，因为它们包含逻辑代码
        
        cloned.setVersion(getVersion());
        return cloned;
    }
    
    // Getters and Setters
    public AIType getAiType() { return aiType; }
    public void setAiType(AIType aiType) { this.aiType = aiType; incrementVersion(); }
    
    public AIState getCurrentState() { return currentState; }
    public AIState getPreviousState() { return previousState; }
    public long getStateChangeTime() { return stateChangeTime; }
    
    public AIParameters getParameters() { return parameters; }
    
    public AITarget getCurrentTarget() { return currentTarget; }
    public void setCurrentTarget(AITarget currentTarget) { this.currentTarget = currentTarget; incrementVersion(); }
    
    public Map<Long, AITarget> getTargets() { return new HashMap<>(targets); }
    
    public List<AIBehavior> getBehaviors() { return new ArrayList<>(behaviors); }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; incrementVersion(); }
    
    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getSpawnZ() { return spawnZ; }
    
    public List<float[]> getPatrolPoints() { 
        List<float[]> result = new ArrayList<>();
        for (float[] point : patrolPoints) {
            result.add(point.clone());
        }
        return result;
    }
    
    public int getCurrentPatrolIndex() { return currentPatrolIndex; }
    public void setCurrentPatrolIndex(int currentPatrolIndex) { 
        this.currentPatrolIndex = Math.max(0, currentPatrolIndex % Math.max(1, patrolPoints.size())); 
    }
    
    @Override
    public int getSize() {
        return 64 + // basic fields
               targets.size() * 48 + // targets
               behaviors.size() * 16 + // behaviors (references)
               patrolPoints.size() * 12 + // patrol points
               customProperties.size() * 32; // custom properties
    }
    
    @Override
    public String toString() {
        return "AIComponent{" +
                "type=" + aiType +
                ", state=" + currentState +
                ", targets=" + targets.size() +
                ", behaviors=" + behaviors.size() +
                ", enabled=" + enabled +
                ", version=" + getVersion() +
                '}';
    }
}