/*
 * 文件名: GameValidator.java
 * 用途: 游戏数据验证器
 * 实现内容:
 *   - 移动速度验证
 *   - 攻击频率验证
 *   - 数值合法性验证
 *   - 时序逻辑验证
 *   - 状态机验证
 * 技术选型:
 *   - 物理规则验证
 *   - 游戏逻辑规则
 *   - 自定义校验函数
 * 依赖关系:
 *   - 被CheatDetector使用
 *   - 被游戏逻辑模块使用
 */
package com.lx.gameserver.frame.security.anticheat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 游戏数据验证器
 * <p>
 * 提供游戏数据的合法性验证功能，包括移动速度、攻击频率、
 * 数值范围、时序逻辑和状态转换等方面的验证，是防作弊系统的基础组件。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class GameValidator {

    /**
     * 玩家上次位置信息
     * Key: 玩家ID
     * Value: 位置信息
     */
    private final Map<String, PositionData> lastPositions = new ConcurrentHashMap<>();
    
    /**
     * 玩家上次攻击时间
     * Key: 玩家ID-技能ID
     * Value: 时间戳
     */
    private final Map<String, Instant> lastAttackTimes = new ConcurrentHashMap<>();
    
    /**
     * 玩家当前状态
     * Key: 玩家ID
     * Value: 状态名称
     */
    private final Map<String, String> playerStates = new ConcurrentHashMap<>();
    
    /**
     * 状态转换规则
     * Key: 当前状态-目标状态
     * Value: 转换规则验证器
     */
    private final Map<String, BiPredicate<String, Map<String, Object>>> stateTransitionRules = new HashMap<>();
    
    /**
     * 构造函数，初始化状态转换规则
     */
    public GameValidator() {
        initStateTransitionRules();
        log.info("游戏数据验证器初始化完成");
    }
    
    /**
     * 初始化状态转换规则
     */
    private void initStateTransitionRules() {
        // 示例：从站立到跑动的转换规则
        stateTransitionRules.put("stand-run", (playerId, data) -> {
            // 允许从站立到跑动
            return true;
        });
        
        // 从跑动到跳跃的转换规则
        stateTransitionRules.put("run-jump", (playerId, data) -> {
            // 允许从跑动到跳跃
            return true;
        });
        
        // 从跳跃到站立的转换规则
        stateTransitionRules.put("jump-stand", (playerId, data) -> {
            // 只有在着陆时才允许从跳跃到站立
            return data.containsKey("grounded") && Boolean.TRUE.equals(data.get("grounded"));
        });
        
        // 从任何状态到死亡的转换规则
        stateTransitionRules.put("any-dead", (playerId, data) -> {
            // 只有当生命值为0时才允许转换到死亡状态
            return data.containsKey("hp") && ((Number) data.get("hp")).intValue() <= 0;
        });
    }
    
    /**
     * 验证玩家行为数据
     *
     * @param playerId 玩家ID
     * @param actionType 行为类型
     * @param data 行为数据
     * @return 异常评分（0-100），0表示正常
     */
    public float validate(String playerId, String actionType, Map<String, Object> data) {
        try {
            switch (actionType) {
                case "move":
                    return validateMovement(playerId, data);
                case "attack":
                    return validateAttack(playerId, data);
                case "loot":
                    return validateLoot(playerId, data);
                case "useItem":
                    return validateUseItem(playerId, data);
                case "stateChange":
                    return validateStateChange(playerId, data);
                default:
                    // 默认验证通过
                    return 0;
            }
        } catch (Exception e) {
            log.error("验证玩家数据时出错: playerId={}, actionType={}", playerId, actionType, e);
            return 50; // 出错时返回中等异常分数
        }
    }
    
    /**
     * 验证移动数据
     *
     * @param playerId 玩家ID
     * @param data 移动数据
     * @return 异常评分
     */
    @SuppressWarnings("unchecked")
    private float validateMovement(String playerId, Map<String, Object> data) {
        if (!data.containsKey("position") || !data.containsKey("timestamp")) {
            return 50; // 缺少必要数据
        }
        
        Map<String, Number> position = (Map<String, Number>) data.get("position");
        long timestamp = ((Number) data.get("timestamp")).longValue();
        float speed = data.containsKey("speed") ? ((Number) data.get("speed")).floatValue() : 0;
        
        // 获取上次位置数据
        PositionData lastPos = lastPositions.get(playerId);
        if (lastPos != null) {
            // 计算两点距离
            float dx = position.get("x").floatValue() - lastPos.x;
            float dy = position.get("y").floatValue() - lastPos.y;
            float dz = position.get("z").floatValue() - lastPos.z;
            float distance = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
            
            // 计算时间差（秒）
            float timeDelta = (timestamp - lastPos.timestamp) / 1000.0f;
            if (timeDelta <= 0) {
                return 75; // 时间倒流，明显异常
            }
            
            // 计算实际速度
            float actualSpeed = distance / timeDelta;
            
            // 获取最大允许速度
            float maxSpeed = data.containsKey("maxSpeed") ? 
                    ((Number) data.get("maxSpeed")).floatValue() : 10.0f;
            
            // 检查速度异常
            if (actualSpeed > maxSpeed * 1.5f) {
                log.debug("检测到异常移动速度: playerId={}, 实际速度={}, 最大速度={}, 距离={}, 时间差={}秒",
                        playerId, actualSpeed, maxSpeed, distance, timeDelta);
                
                // 计算异常评分（超速程度）
                return Math.min((actualSpeed / maxSpeed - 1) * 100, 100);
            }
            
            // 检查瞬间移动
            if (distance > 50 && timeDelta < 1.0f) {
                log.debug("检测到可能的瞬间移动: playerId={}, 距离={}, 时间差={}秒", 
                        playerId, distance, timeDelta);
                return 80;
            }
        }
        
        // 更新最后位置
        lastPositions.put(playerId, new PositionData(
                position.get("x").floatValue(),
                position.get("y").floatValue(),
                position.get("z").floatValue(),
                timestamp
        ));
        
        return 0; // 验证通过
    }
    
    /**
     * 验证攻击数据
     *
     * @param playerId 玩家ID
     * @param data 攻击数据
     * @return 异常评分
     */
    private float validateAttack(String playerId, Map<String, Object> data) {
        if (!data.containsKey("skillId") || !data.containsKey("timestamp")) {
            return 50; // 缺少必要数据
        }
        
        String skillId = data.get("skillId").toString();
        long timestamp = ((Number) data.get("timestamp")).longValue();
        float cooldown = data.containsKey("cooldown") ? 
                ((Number) data.get("cooldown")).floatValue() : 1.0f;
        
        // 检查技能冷却
        String key = playerId + ":" + skillId;
        Instant lastAttackTime = lastAttackTimes.get(key);
        if (lastAttackTime != null) {
            Duration elapsed = Duration.between(lastAttackTime, Instant.ofEpochMilli(timestamp));
            float elapsedSeconds = elapsed.toMillis() / 1000.0f;
            
            if (elapsedSeconds < cooldown * 0.9f) {
                log.debug("检测到技能冷却绕过: playerId={}, skillId={}, 实际冷却={}秒, 要求冷却={}秒",
                        playerId, skillId, elapsedSeconds, cooldown);
                
                // 计算异常评分（冷却绕过程度）
                return Math.min((1 - elapsedSeconds / cooldown) * 150, 100);
            }
        }
        
        // 更新最后攻击时间
        lastAttackTimes.put(key, Instant.ofEpochMilli(timestamp));
        
        // 检查攻击伤害
        if (data.containsKey("damage")) {
            float damage = ((Number) data.get("damage")).floatValue();
            float maxDamage = data.containsKey("maxDamage") ? 
                    ((Number) data.get("maxDamage")).floatValue() : 1000.0f;
            
            if (damage > maxDamage * 1.2f) {
                log.debug("检测到异常攻击伤害: playerId={}, skillId={}, 伤害={}, 最大伤害={}",
                        playerId, skillId, damage, maxDamage);
                
                // 计算异常评分（超伤害程度）
                return Math.min((damage / maxDamage - 1) * 100, 100);
            }
        }
        
        return 0; // 验证通过
    }
    
    /**
     * 验证拾取数据
     *
     * @param playerId 玩家ID
     * @param data 拾取数据
     * @return 异常评分
     */
    @SuppressWarnings("unchecked")
    private float validateLoot(String playerId, Map<String, Object> data) {
        if (!data.containsKey("itemId") || !data.containsKey("position")) {
            return 50; // 缺少必要数据
        }
        
        // 获取物品和玩家位置
        String itemId = data.get("itemId").toString();
        Map<String, Number> itemPosition = (Map<String, Number>) data.get("position");
        Map<String, Number> playerPosition = (Map<String, Number>) data.getOrDefault("playerPosition", 
                lastPositions.containsKey(playerId) ? Map.of(
                        "x", lastPositions.get(playerId).x,
                        "y", lastPositions.get(playerId).y,
                        "z", lastPositions.get(playerId).z
                ) : null);
        
        // 如果无法获取玩家位置，则无法验证
        if (playerPosition == null) {
            return 0;
        }
        
        // 计算物品与玩家的距离
        float dx = itemPosition.get("x").floatValue() - playerPosition.get("x").floatValue();
        float dy = itemPosition.get("y").floatValue() - playerPosition.get("y").floatValue();
        float dz = itemPosition.get("z").floatValue() - playerPosition.get("z").floatValue();
        float distance = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        // 获取最大拾取距离
        float maxLootDistance = data.containsKey("maxLootDistance") ? 
                ((Number) data.get("maxLootDistance")).floatValue() : 5.0f;
        
        // 检查拾取距离
        if (distance > maxLootDistance * 1.5f) {
            log.debug("检测到远距离拾取: playerId={}, itemId={}, 距离={}, 最大拾取距离={}",
                    playerId, itemId, distance, maxLootDistance);
            
            // 计算异常评分（超距离程度）
            return Math.min((distance / maxLootDistance - 1) * 100, 100);
        }
        
        return 0; // 验证通过
    }
    
    /**
     * 验证物品使用数据
     *
     * @param playerId 玩家ID
     * @param data 物品使用数据
     * @return 异常评分
     */
    private float validateUseItem(String playerId, Map<String, Object> data) {
        if (!data.containsKey("itemId")) {
            return 50; // 缺少必要数据
        }
        
        String itemId = data.get("itemId").toString();
        
        // 检查物品所有权
        boolean hasItem = data.containsKey("hasItem") ? (Boolean) data.get("hasItem") : true;
        if (!hasItem) {
            log.debug("检测到使用未拥有的物品: playerId={}, itemId={}", playerId, itemId);
            return 100; // 明确的作弊行为
        }
        
        // 检查物品使用条件
        boolean meetsCondition = data.containsKey("meetsCondition") ? 
                (Boolean) data.get("meetsCondition") : true;
        if (!meetsCondition) {
            log.debug("检测到使用不满足条件的物品: playerId={}, itemId={}", playerId, itemId);
            return 80;
        }
        
        // 检查物品使用次数
        if (data.containsKey("usageCount") && data.containsKey("maxUsage")) {
            int usageCount = ((Number) data.get("usageCount")).intValue();
            int maxUsage = ((Number) data.get("maxUsage")).intValue();
            
            if (usageCount > maxUsage) {
                log.debug("检测到超过使用次数的物品: playerId={}, itemId={}, 使用次数={}, 最大次数={}",
                        playerId, itemId, usageCount, maxUsage);
                return 90;
            }
        }
        
        return 0; // 验证通过
    }
    
    /**
     * 验证状态变化
     *
     * @param playerId 玩家ID
     * @param data 状态数据
     * @return 异常评分
     */
    private float validateStateChange(String playerId, Map<String, Object> data) {
        if (!data.containsKey("newState")) {
            return 50; // 缺少必要数据
        }
        
        String currentState = playerStates.getOrDefault(playerId, "stand");
        String newState = data.get("newState").toString();
        
        // 检查状态转换是否合法
        String transitionKey = currentState + "-" + newState;
        String anyTransitionKey = "any-" + newState;
        
        BiPredicate<String, Map<String, Object>> rule = stateTransitionRules.get(transitionKey);
        if (rule == null) {
            rule = stateTransitionRules.get(anyTransitionKey);
        }
        
        if (rule != null && !rule.test(playerId, data)) {
            log.debug("检测到非法状态转换: playerId={}, 从{}到{}", playerId, currentState, newState);
            return 70;
        }
        
        // 更新玩家状态
        playerStates.put(playerId, newState);
        
        return 0; // 验证通过
    }
    
    /**
     * 清除玩家数据
     *
     * @param playerId 玩家ID
     */
    public void clearPlayerData(String playerId) {
        lastPositions.remove(playerId);
        playerStates.remove(playerId);
        
        // 清除攻击记录
        lastAttackTimes.keySet().removeIf(key -> key.startsWith(playerId + ":"));
    }
    
    /**
     * 位置数据内部类
     */
    private static class PositionData {
        final float x;
        final float y;
        final float z;
        final long timestamp;
        
        /**
         * 构造函数
         */
        public PositionData(float x, float y, float z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}