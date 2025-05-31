/*
 * 文件名: CheatDetector.java
 * 用途: 作弊检测引擎
 * 实现内容:
 *   - 行为模式分析
 *   - 异常数据检测
 *   - 统计异常识别
 *   - 机器学习检测
 *   - 实时告警机制
 * 技术选型:
 *   - 统计分析算法
 *   - 异常检测规则引擎
 *   - 行为模式匹配
 * 依赖关系:
 *   - 被游戏逻辑模块使用
 *   - 使用风控系统
 */
package com.lx.gameserver.frame.security.anticheat;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.protection.BlacklistManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 作弊检测引擎
 * <p>
 * 提供游戏中的作弊行为检测功能，包括行为模式分析、
 * 异常数据检测、统计分析和实时告警等，是游戏防作弊的核心组件。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class CheatDetector {

    /**
     * 玩家行为历史
     * Key: 玩家ID
     * Value: 行为历史记录
     */
    private final Map<String, Queue<PlayerAction>> playerActionHistory = new ConcurrentHashMap<>();
    
    /**
     * 玩家异常评分
     * Key: 玩家ID
     * Value: 异常评分（0-100）
     */
    private final Map<String, Float> playerAnomalyScores = new ConcurrentHashMap<>();
    
    /**
     * 检测规则列表
     */
    private final List<CheatDetectionRule> detectionRules = new ArrayList<>();
    
    /**
     * 黑名单管理器
     */
    private final BlacklistManager blacklistManager;
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 游戏验证器
     */
    @Nullable
    private final GameValidator gameValidator;
    
    /**
     * 行为分析器
     */
    @Nullable
    private final BehaviorAnalyzer behaviorAnalyzer;
    
    /**
     * 作弊检测阈值
     */
    private volatile float detectionThreshold;
    
    /**
     * 是否启用自动封禁
     */
    private volatile boolean autoBanEnabled;
    
    /**
     * 自动封禁时长
     */
    private volatile Duration banDuration;
    
    /**
     * 历史行为记录最大条数
     */
    private static final int MAX_ACTION_HISTORY = 1000;
    
    /**
     * 构造函数
     *
     * @param blacklistManager 黑名单管理器
     * @param securityProperties 安全配置
     * @param gameValidator 游戏验证器（可选）
     * @param behaviorAnalyzer 行为分析器（可选）
     */
    @Autowired
    public CheatDetector(BlacklistManager blacklistManager, 
                      SecurityProperties securityProperties,
                      @Nullable GameValidator gameValidator,
                      @Nullable BehaviorAnalyzer behaviorAnalyzer) {
        this.blacklistManager = blacklistManager;
        this.securityProperties = securityProperties;
        this.gameValidator = gameValidator;
        this.behaviorAnalyzer = behaviorAnalyzer;
        
        // 从配置初始化参数
        this.detectionThreshold = (float) securityProperties.getAntiCheat().getDetectionThreshold();
        this.autoBanEnabled = securityProperties.getAntiCheat().isAutoBan();
        this.banDuration = securityProperties.getAntiCheat().getBanDuration();
        
        log.info("作弊检测引擎初始化完成");
    }
    
    /**
     * 初始化检测规则
     */
    @PostConstruct
    public void initRules() {
        log.info("初始化作弊检测规则");
        
        // 移动速度规则
        detectionRules.add(new CheatDetectionRule("移动速度异常", 
            (playerId, actionType, data) -> {
                if ("move".equals(actionType) && data.containsKey("speed")) {
                    float speed = ((Number) data.get("speed")).floatValue();
                    float maxSpeed = ((Number) data.getOrDefault("maxSpeed", 10.0f)).floatValue();
                    return speed > maxSpeed * 1.1f ? (speed / maxSpeed - 1) * 100 : 0;
                }
                return 0;
            }));
        
        // 攻击频率规则
        detectionRules.add(new CheatDetectionRule("攻击频率异常", 
            (playerId, actionType, data) -> {
                if ("attack".equals(actionType)) {
                    Queue<PlayerAction> history = getPlayerActionHistory(playerId);
                    long attackCount = history.stream()
                            .filter(a -> "attack".equals(a.getActionType()))
                            .filter(a -> a.getTimestamp().isAfter(Instant.now().minusSeconds(5)))
                            .count();
                    
                    float attackSpeed = ((Number) data.getOrDefault("attackSpeed", 1.0f)).floatValue();
                    float maxAttacksIn5Sec = 5 * attackSpeed;
                    
                    return attackCount > maxAttacksIn5Sec ? 
                            (attackCount / maxAttacksIn5Sec - 1) * 100 : 0;
                }
                return 0;
            }));
        
        // 资源获取异常规则
        detectionRules.add(new CheatDetectionRule("资源获取异常", 
            (playerId, actionType, data) -> {
                if ("getResource".equals(actionType) && data.containsKey("amount")) {
                    float amount = ((Number) data.get("amount")).floatValue();
                    float expectedAmount = ((Number) data.getOrDefault("expected", 0.0f)).floatValue();
                    
                    if (expectedAmount > 0 && amount > expectedAmount * 1.5f) {
                        return Math.min((amount / expectedAmount - 1) * 100, 100);
                    }
                }
                return 0;
            }));
        
        // 经验获取异常规则
        detectionRules.add(new CheatDetectionRule("经验获取异常", 
            (playerId, actionType, data) -> {
                if ("gainExp".equals(actionType) && data.containsKey("amount")) {
                    float amount = ((Number) data.get("amount")).floatValue();
                    float maxPerAction = ((Number) data.getOrDefault("maxPerAction", 1000.0f)).floatValue();
                    
                    if (amount > maxPerAction * 2) {
                        return Math.min((amount / maxPerAction - 1) * 50, 100);
                    }
                }
                return 0;
            }));
        
        // 瞬间传送规则
        detectionRules.add(new CheatDetectionRule("瞬间传送检测", 
            (playerId, actionType, data) -> {
                if ("move".equals(actionType) && data.containsKey("position") && data.containsKey("lastPosition")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Float> position = (Map<String, Float>) data.get("position");
                    @SuppressWarnings("unchecked")
                    Map<String, Float> lastPosition = (Map<String, Float>) data.get("lastPosition");
                    
                    if (position != null && lastPosition != null) {
                        float dx = position.get("x") - lastPosition.get("x");
                        float dy = position.get("y") - lastPosition.get("y");
                        float dz = position.get("z") - lastPosition.get("z");
                        
                        float distance = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                        float timeDelta = ((Number) data.getOrDefault("timeDelta", 1.0f)).floatValue();
                        float maxDistance = ((Number) data.getOrDefault("maxDistance", 50.0f)).floatValue();
                        
                        if (distance > maxDistance && timeDelta < 1.0) {
                            return Math.min((distance / maxDistance) * 100, 100);
                        }
                    }
                }
                return 0;
            }));
        
        // 技能冷却绕过检测
        detectionRules.add(new CheatDetectionRule("技能冷却绕过", 
            (playerId, actionType, data) -> {
                if ("useSkill".equals(actionType) && data.containsKey("skillId")) {
                    String skillId = data.get("skillId").toString();
                    Queue<PlayerAction> history = getPlayerActionHistory(playerId);
                    
                    Optional<PlayerAction> lastSkillUse = history.stream()
                            .filter(a -> "useSkill".equals(a.getActionType()))
                            .filter(a -> {
                                Object actionSkillId = a.getData().get("skillId");
                                return actionSkillId != null && actionSkillId.toString().equals(skillId);
                            })
                            .filter(a -> !a.getData().equals(data)) // 排除当前动作
                            .max(Comparator.comparing(PlayerAction::getTimestamp));
                    
                    if (lastSkillUse.isPresent()) {
                        float cooldown = ((Number) data.getOrDefault("cooldown", 5.0f)).floatValue();
                        Instant lastTime = lastSkillUse.get().getTimestamp();
                        float actualCooldown = (Instant.now().toEpochMilli() - lastTime.toEpochMilli()) / 1000.0f;
                        
                        if (actualCooldown < cooldown * 0.9f) {
                            return Math.min((1 - actualCooldown / cooldown) * 150, 100);
                        }
                    }
                }
                return 0;
            }));
        
        log.info("作弊检测规则初始化完成，共 {} 条规则", detectionRules.size());
    }
    
    /**
     * 处理玩家行为
     *
     * @param playerId 玩家ID
     * @param actionType 行为类型
     * @param data 相关数据
     * @return 检测结果
     */
    public CheatDetectionResult detectCheat(String playerId, String actionType, Map<String, Object> data) {
        if (playerId == null || actionType == null) {
            return new CheatDetectionResult(false, 0, "无效参数");
        }
        
        try {
            // 记录行为
            recordPlayerAction(playerId, actionType, data);
            
            // 检查玩家是否已被确认作弊
            if (isConfirmedCheater(playerId)) {
                return new CheatDetectionResult(true, 100, "已确认的作弊玩家");
            }
            
            // 执行基本验证（如果有GameValidator）
            float validationScore = 0;
            if (gameValidator != null) {
                validationScore = gameValidator.validate(playerId, actionType, data);
                if (validationScore >= detectionThreshold) {
                    handleCheater(playerId, "游戏数据验证失败", validationScore);
                    return new CheatDetectionResult(true, validationScore, "游戏数据验证失败");
                }
            }
            
            // 检查行为模式（如果有BehaviorAnalyzer）
            float behaviorScore = 0;
            if (behaviorAnalyzer != null) {
                behaviorScore = behaviorAnalyzer.analyzePlayerBehavior(playerId, actionType, data);
                if (behaviorScore >= detectionThreshold) {
                    handleCheater(playerId, "行为模式异常", behaviorScore);
                    return new CheatDetectionResult(true, behaviorScore, "行为模式异常");
                }
            }
            
            // 应用检测规则
            float highestScore = 0;
            String detectedReason = null;
            
            for (CheatDetectionRule rule : detectionRules) {
                float score = rule.apply(playerId, actionType, data);
                if (score > highestScore) {
                    highestScore = score;
                    detectedReason = rule.getName();
                }
                
                // 如果超过阈值，立即处理
                if (score >= detectionThreshold) {
                    handleCheater(playerId, detectedReason, score);
                    return new CheatDetectionResult(true, score, detectedReason);
                }
            }
            
            // 计算综合异常评分
            float combinedScore = Math.max(Math.max(highestScore, validationScore), behaviorScore);
            updatePlayerAnomalyScore(playerId, combinedScore);
            
            // 如果综合评分超过阈值，判定为作弊
            if (getPlayerAnomalyScore(playerId) >= detectionThreshold) {
                handleCheater(playerId, "综合异常行为", getPlayerAnomalyScore(playerId));
                return new CheatDetectionResult(true, getPlayerAnomalyScore(playerId), "综合异常行为");
            }
            
            return new CheatDetectionResult(false, combinedScore, null);
            
        } catch (Exception e) {
            log.error("作弊检测过程中出错: playerId={}, actionType={}", playerId, actionType, e);
            return new CheatDetectionResult(false, 0, "检测过程出错");
        }
    }
    
    /**
     * 记录玩家行为
     *
     * @param playerId 玩家ID
     * @param actionType 行为类型
     * @param data 相关数据
     */
    private void recordPlayerAction(String playerId, String actionType, Map<String, Object> data) {
        Queue<PlayerAction> history = playerActionHistory.computeIfAbsent(
                playerId, k -> new ConcurrentLinkedQueue<>());
        
        // 添加新行为
        PlayerAction action = new PlayerAction(playerId, actionType, data);
        history.add(action);
        
        // 限制历史记录大小
        while (history.size() > MAX_ACTION_HISTORY) {
            history.poll();
        }
    }
    
    /**
     * 获取玩家行为历史
     *
     * @param playerId 玩家ID
     * @return 行为历史队列
     */
    private Queue<PlayerAction> getPlayerActionHistory(String playerId) {
        return playerActionHistory.getOrDefault(playerId, new ConcurrentLinkedQueue<>());
    }
    
    /**
     * 更新玩家异常评分
     *
     * @param playerId 玩家ID
     * @param score 新的评分
     */
    private void updatePlayerAnomalyScore(String playerId, float score) {
        if (score <= 0) {
            // 如果当前分数为0，则缓慢降低现有分数
            playerAnomalyScores.compute(playerId, (k, v) -> {
                if (v == null || v < 1) {
                    return 0f;
                }
                return Math.max(0, v - 0.5f); // 缓慢降低
            });
        } else {
            // 否则根据新分数更新
            playerAnomalyScores.compute(playerId, (k, v) -> {
                float current = v == null ? 0 : v;
                // 使用加权平均值，新分数权重0.3
                return current * 0.7f + score * 0.3f;
            });
        }
    }
    
    /**
     * 获取玩家异常评分
     *
     * @param playerId 玩家ID
     * @return 异常评分
     */
    public float getPlayerAnomalyScore(String playerId) {
        return playerAnomalyScores.getOrDefault(playerId, 0f);
    }
    
    /**
     * 检查玩家是否已被确认作弊
     *
     * @param playerId 玩家ID
     * @return 如果是确认作弊返回true，否则返回false
     */
    public boolean isConfirmedCheater(String playerId) {
        return blacklistManager.isAccountBlacklisted(playerId);
    }
    
    /**
     * 处理作弊玩家
     *
     * @param playerId 玩家ID
     * @param reason 原因
     * @param score 异常评分
     */
    private void handleCheater(String playerId, String reason, float score) {
        log.warn("检测到作弊玩家: playerId={}, 原因={}, 评分={}", playerId, reason, score);
        
        // 更新玩家异常评分
        playerAnomalyScores.put(playerId, 100f);
        
        // 如果启用了自动封禁，则加入黑名单
        if (autoBanEnabled) {
            blacklistManager.addToAccountBlacklist(playerId, banDuration);
            log.info("已自动封禁作弊玩家: playerId={}, 时长={}", playerId, banDuration);
            
            // 这里可以调用其他服务通知玩家或记录日志等
        }
        
        // TODO: 实现实时告警机制，如发送通知给管理员、记录详细日志等
    }
    
    /**
     * 手动确认玩家作弊
     *
     * @param playerId 玩家ID
     * @param reason 原因
     * @param adminId 管理员ID
     * @param duration 封禁时长，null表示永久
     * @return 是否成功
     */
    public boolean manualMarkCheater(String playerId, String reason, String adminId, @Nullable Duration duration) {
        try {
            log.info("管理员手动标记作弊玩家: playerId={}, 原因={}, 管理员={}", playerId, reason, adminId);
            
            // 设置玩家异常评分为最高
            playerAnomalyScores.put(playerId, 100f);
            
            // 加入黑名单
            boolean result = blacklistManager.addToAccountBlacklist(playerId, duration);
            
            // TODO: 记录管理操作日志
            
            return result;
        } catch (Exception e) {
            log.error("手动标记作弊玩家失败", e);
            return false;
        }
    }
    
    /**
     * 清除玩家作弊记录
     *
     * @param playerId 玩家ID
     * @param adminId 管理员ID
     * @param reason 原因
     * @return 是否成功
     */
    public boolean clearCheaterRecord(String playerId, String adminId, String reason) {
        try {
            log.info("管理员清除作弊记录: playerId={}, 原因={}, 管理员={}", playerId, reason, adminId);
            
            // 重置玩家异常评分
            playerAnomalyScores.remove(playerId);
            
            // 从黑名单移除
            boolean result = blacklistManager.removeFromAccountBlacklist(playerId);
            
            // 清除行为历史
            playerActionHistory.remove(playerId);
            
            // TODO: 记录管理操作日志
            
            return result;
        } catch (Exception e) {
            log.error("清除作弊记录失败", e);
            return false;
        }
    }
    
    /**
     * 设置作弊检测阈值
     *
     * @param threshold 阈值（0-100）
     */
    public void setDetectionThreshold(float threshold) {
        this.detectionThreshold = threshold;
        log.info("作弊检测阈值已设置为: {}", threshold);
    }
    
    /**
     * 设置自动封禁是否启用
     *
     * @param enabled 是否启用
     */
    public void setAutoBanEnabled(boolean enabled) {
        this.autoBanEnabled = enabled;
        log.info("自动封禁功能: {}", enabled ? "已启用" : "已禁用");
    }
    
    /**
     * 设置自动封禁时长
     *
     * @param duration 封禁时长
     */
    public void setAutoBanDuration(Duration duration) {
        this.banDuration = duration;
        log.info("自动封禁时长已设置为: {}", duration);
    }
    
    /**
     * 玩家行为记录
     */
    @Data
    public static class PlayerAction {
        /**
         * 玩家ID
         */
        private final String playerId;
        
        /**
         * 行为类型
         */
        private final String actionType;
        
        /**
         * 行为数据
         */
        private final Map<String, Object> data;
        
        /**
         * 时间戳
         */
        private final Instant timestamp;
        
        /**
         * 构造函数
         *
         * @param playerId 玩家ID
         * @param actionType 行为类型
         * @param data 行为数据
         */
        public PlayerAction(String playerId, String actionType, Map<String, Object> data) {
            this.playerId = playerId;
            this.actionType = actionType;
            this.data = new HashMap<>(data);
            this.timestamp = Instant.now();
        }
    }
    
    /**
     * 作弊检测规则
     */
    public static class CheatDetectionRule {
        /**
         * 规则名称
         */
        private final String name;
        
        /**
         * 规则处理器
         */
        private final RuleProcessor processor;
        
        /**
         * 构造函数
         *
         * @param name 规则名称
         * @param processor 规则处理器
         */
        public CheatDetectionRule(String name, RuleProcessor processor) {
            this.name = name;
            this.processor = processor;
        }
        
        /**
         * 获取规则名称
         *
         * @return 规则名称
         */
        public String getName() {
            return name;
        }
        
        /**
         * 应用规则
         *
         * @param playerId 玩家ID
         * @param actionType 行为类型
         * @param data 行为数据
         * @return 异常评分（0-100）
         */
        public float apply(String playerId, String actionType, Map<String, Object> data) {
            try {
                return processor.process(playerId, actionType, data);
            } catch (Exception e) {
                log.error("应用规则出错: rule={}, playerId={}", name, playerId, e);
                return 0;
            }
        }
    }
    
    /**
     * 规则处理器接口
     */
    @FunctionalInterface
    public interface RuleProcessor {
        /**
         * 处理规则
         *
         * @param playerId 玩家ID
         * @param actionType 行为类型
         * @param data 行为数据
         * @return 异常评分（0-100）
         */
        float process(String playerId, String actionType, Map<String, Object> data);
    }
    
    /**
     * 作弊检测结果
     */
    @Data
    public static class CheatDetectionResult {
        /**
         * 是否检测到作弊
         */
        private final boolean detected;
        
        /**
         * 异常评分（0-100）
         */
        private final float anomalyScore;
        
        /**
         * 原因
         */
        private final String reason;
        
        /**
         * 构造函数
         *
         * @param detected 是否检测到作弊
         * @param anomalyScore 异常评分
         * @param reason 原因
         */
        public CheatDetectionResult(boolean detected, float anomalyScore, String reason) {
            this.detected = detected;
            this.anomalyScore = anomalyScore;
            this.reason = reason;
        }
    }
}