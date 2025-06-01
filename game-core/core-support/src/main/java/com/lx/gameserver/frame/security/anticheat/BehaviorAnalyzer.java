/*
 * 文件名: BehaviorAnalyzer.java
 * 用途: 玩家行为分析
 * 实现内容:
 *   - 正常行为建模
 *   - 异常行为识别
 *   - 行为评分系统
 *   - 风险等级评估
 *   - 自动处罚机制
 * 技术选型:
 *   - 统计分析模型
 *   - 时序模式分析
 *   - 异常检测算法
 * 依赖关系:
 *   - 被CheatDetector使用
 *   - 使用BlacklistManager
 */
package com.lx.gameserver.frame.security.anticheat;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.protection.BlacklistManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 玩家行为分析
 * <p>
 * 分析玩家行为模式，建立正常行为模型并识别异常行为，
 * 通过行为评分系统评估玩家风险，实现更精确的作弊检测。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorAnalyzer {

    /**
     * 安全配置属性
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 黑名单管理器
     */
    private final BlacklistManager blacklistManager;
    
    /**
     * 玩家行为记录
     * Key: 玩家ID
     * Value: 行为记录列表
     */
    private final Map<String, List<PlayerBehavior>> playerBehaviors = new ConcurrentHashMap<>();
    
    /**
     * 玩家行为模型
     * Key: 玩家ID
     * Value: 行为模型
     */
    private final Map<String, BehaviorModel> playerModels = new ConcurrentHashMap<>();
    
    /**
     * 玩家风险评分
     * Key: 玩家ID
     * Value: 风险评分
     */
    private final Map<String, Float> riskScores = new ConcurrentHashMap<>();
    
    /**
     * 行为类型定义
     */
    private static final List<String> BEHAVIOR_TYPES = Arrays.asList(
            "login", "logout", "move", "attack", "chat", "trade", "loot", 
            "purchase", "quest", "craft", "skillUse", "death", "respawn"
    );
    
    /**
     * 每种行为类型的正常速率（每分钟）
     */
    private static final Map<String, Float> NORMAL_RATES = new HashMap<>();
    
    /**
     * 行为模式定义
     */
    private static final Map<String, BehaviorPattern> BEHAVIOR_PATTERNS = new HashMap<>();
    
    /**
     * 单个玩家保存的最大行为记录数
     */
    private static final int MAX_BEHAVIORS_PER_PLAYER = 1000;
    
    /**
     * 风险评分阈值（高于此值触发处罚）
     */
    private static final float RISK_THRESHOLD = 80.0f;
    
    /**
     * 异常行为检测器列表
     */
    private final List<AnomalyDetector> anomalyDetectors = new ArrayList<>();
    
    /**
     * 静态初始化
     */
    static {
        // 初始化正常行为速率
        NORMAL_RATES.put("move", 60.0f);     // 每分钟60次移动
        NORMAL_RATES.put("attack", 30.0f);   // 每分钟30次攻击
        NORMAL_RATES.put("chat", 5.0f);      // 每分钟5次聊天
        NORMAL_RATES.put("loot", 20.0f);     // 每分钟20次拾取
        NORMAL_RATES.put("trade", 2.0f);     // 每分钟2次交易
        NORMAL_RATES.put("skillUse", 15.0f); // 每分钟15次技能使用
        
        // 初始化行为模式
        BEHAVIOR_PATTERNS.put("farming", new BehaviorPattern(
                "farming",
                Map.of("move", 40.0f, "attack", 30.0f, "loot", 25.0f, "skillUse", 15.0f),
                0.7f // 匹配容差
        ));
        
        BEHAVIOR_PATTERNS.put("trading", new BehaviorPattern(
                "trading",
                Map.of("move", 20.0f, "chat", 10.0f, "trade", 5.0f),
                0.7f
        ));
        
        BEHAVIOR_PATTERNS.put("questing", new BehaviorPattern(
                "questing",
                Map.of("move", 50.0f, "attack", 20.0f, "loot", 15.0f, "quest", 5.0f),
                0.7f
        ));
        
        BEHAVIOR_PATTERNS.put("bot", new BehaviorPattern(
                "bot",
                Map.of("move", 60.0f, "attack", 40.0f, "loot", 40.0f, "skillUse", 30.0f),
                0.9f
        ));
    }
    
    /**
     * 初始化方法
     */
    public void init() {
        // 注册异常检测器
        registerAnomalyDetectors();
        
        // 启动清理任务（每小时清理一次过期数据）
        Timer cleanupTimer = new Timer(true);
        cleanupTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                cleanupOldData();
            }
        }, 3600000, 3600000);
        
        log.info("行为分析器初始化完成，已注册{}个异常检测器", anomalyDetectors.size());
    }
    
    /**
     * 注册异常检测器
     */
    private void registerAnomalyDetectors() {
        // 移动速度异常检测
        anomalyDetectors.add(new AnomalyDetector(
                "高频移动检测",
                (playerId, type, data) -> {
                    if ("move".equals(type)) {
                        float rate = calculateBehaviorRate(playerId, "move", Duration.ofMinutes(1));
                        return rate > NORMAL_RATES.get("move") * 1.5f ? 
                                (rate / NORMAL_RATES.get("move") - 1) * 50.0f : 0.0f;
                    }
                    return 0.0f;
                }
        ));
        
        // 完美规律行为检测（机器人特征）
        anomalyDetectors.add(new AnomalyDetector(
                "规律行为检测",
                (playerId, type, data) -> {
                    List<PlayerBehavior> behaviors = getPlayerBehaviors(playerId);
                    if (behaviors.size() < 20) {
                        return 0.0f;
                    }
                    
                    // 获取相同类型的行为
                    List<PlayerBehavior> sameTypeBehaviors = behaviors.stream()
                            .filter(b -> b.getType().equals(type))
                            .sorted(Comparator.comparing(PlayerBehavior::getTimestamp))
                            .collect(Collectors.toList());
                    
                    if (sameTypeBehaviors.size() < 10) {
                        return 0.0f;
                    }
                    
                    // 计算时间间隔的标准差
                    List<Long> intervals = new ArrayList<>();
                    for (int i = 1; i < sameTypeBehaviors.size(); i++) {
                        long interval = sameTypeBehaviors.get(i).getTimestamp().toEpochMilli() - 
                                sameTypeBehaviors.get(i-1).getTimestamp().toEpochMilli();
                        intervals.add(interval);
                    }
                    
                    double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
                    double variance = intervals.stream()
                            .mapToDouble(i -> Math.pow(i - mean, 2))
                            .average().orElse(0);
                    double stdDev = Math.sqrt(variance);
                    
                    // 如果标准差很小，说明行为非常规律（可能是机器人）
                    if (stdDev < mean * 0.1) {
                        return 70.0f;
                    } else if (stdDev < mean * 0.2) {
                        return 40.0f;
                    }
                    
                    return 0.0f;
                }
        ));
        
        // 异常模式匹配
        anomalyDetectors.add(new AnomalyDetector(
                "机器人模式匹配",
                (playerId, type, data) -> {
                    // 获取玩家行为模型
                    BehaviorModel model = getOrCreatePlayerModel(playerId);
                    
                    // 检查是否匹配机器人模式
                    BehaviorPattern botPattern = BEHAVIOR_PATTERNS.get("bot");
                    float matchScore = calculatePatternMatchScore(model, botPattern);
                    
                    return matchScore > 0.85f ? matchScore * 80.0f : 0.0f;
                }
        ));
        
        // 不可能的动作序列检测
        anomalyDetectors.add(new AnomalyDetector(
                "不可能动作序列",
                (playerId, type, data) -> {
                    List<PlayerBehavior> behaviors = getPlayerBehaviors(playerId);
                    if (behaviors.size() < 3) {
                        return 0.0f;
                    }
                    
                    // 获取最近的两个行为
                    List<PlayerBehavior> recent = behaviors.stream()
                            .sorted(Comparator.comparing(PlayerBehavior::getTimestamp).reversed())
                            .limit(3)
                            .collect(Collectors.toList());
                    
                    // 检查不可能的序列，例如：
                    // - 死亡后立即攻击（没有重生）
                    // - 在不同位置快速连续交易
                    if ("death".equals(recent.get(1).getType()) && 
                            ("attack".equals(recent.get(0).getType()) || "skillUse".equals(recent.get(0).getType()))) {
                        return 90.0f;
                    }
                    
                    // 检查快速远距离移动
                    if ("move".equals(recent.get(0).getType()) && "move".equals(recent.get(1).getType())) {
                        Map<String, Object> pos1 = recent.get(0).getData();
                        Map<String, Object> pos2 = recent.get(1).getData();
                        
                        if (pos1.containsKey("x") && pos1.containsKey("y") && 
                                pos2.containsKey("x") && pos2.containsKey("y")) {
                            
                            double x1 = ((Number) pos1.get("x")).doubleValue();
                            double y1 = ((Number) pos1.get("y")).doubleValue();
                            double x2 = ((Number) pos2.get("x")).doubleValue();
                            double y2 = ((Number) pos2.get("y")).doubleValue();
                            
                            double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                            double timeDiff = recent.get(0).getTimestamp().toEpochMilli() - 
                                    recent.get(1).getTimestamp().toEpochMilli();
                            
                            // 计算速度（单位/秒）
                            double speed = distance / (timeDiff / 1000.0);
                            double maxSpeed = pos1.containsKey("maxSpeed") ? 
                                    ((Number) pos1.get("maxSpeed")).doubleValue() : 10.0;
                                    
                            if (speed > maxSpeed * 2) {
                                return Math.min((float) (speed / maxSpeed) * 30.0f, 90.0f);
                            }
                        }
                    }
                    
                    return 0.0f;
                }
        ));
        
        // 多设备同时在线检测
        anomalyDetectors.add(new AnomalyDetector(
                "多设备登录检测",
                (playerId, type, data) -> {
                    if ("login".equals(type) && data.containsKey("deviceId")) {
                        String deviceId = data.get("deviceId").toString();
                        List<PlayerBehavior> logins = getPlayerBehaviors(playerId).stream()
                                .filter(b -> "login".equals(b.getType()) && !b.getData().containsKey("logout"))
                                .filter(b -> b.getTimestamp().isAfter(Instant.now().minus(Duration.ofHours(1))))
                                .collect(Collectors.toList());
                        
                        // 获取不同的设备ID数量
                        Set<String> devices = new HashSet<>();
                        for (PlayerBehavior login : logins) {
                            if (login.getData().containsKey("deviceId")) {
                                devices.add(login.getData().get("deviceId").toString());
                            }
                        }
                        
                        // 如果有多个设备同时登录，可能是账号共享或自动化脚本
                        if (devices.size() > 1) {
                            return Math.min(devices.size() * 30.0f, 90.0f);
                        }
                    }
                    return 0.0f;
                }
        ));
    }
    
    /**
     * 记录玩家行为
     *
     * @param playerId 玩家ID
     * @param behaviorType 行为类型
     * @param data 行为数据
     */
    public void recordBehavior(String playerId, String behaviorType, Map<String, Object> data) {
        if (playerId == null || behaviorType == null) {
            return;
        }
        
        try {
            // 创建行为记录
            PlayerBehavior behavior = new PlayerBehavior(playerId, behaviorType, 
                    data != null ? new HashMap<>(data) : new HashMap<>(), Instant.now());
            
            // 获取并更新玩家行为列表
            List<PlayerBehavior> behaviors = playerBehaviors.computeIfAbsent(
                    playerId, k -> Collections.synchronizedList(new ArrayList<>()));
            
            behaviors.add(behavior);
            
            // 限制记录数量
            if (behaviors.size() > MAX_BEHAVIORS_PER_PLAYER) {
                behaviors.sort(Comparator.comparing(PlayerBehavior::getTimestamp));
                behaviors.remove(0); // 移除最旧的记录
            }
            
            // 更新玩家行为模型
            updatePlayerModel(playerId, behavior);
            
            log.trace("记录玩家行为: {} {} {}", playerId, behaviorType, data);
        } catch (Exception e) {
            log.error("记录玩家行为失败: {}", playerId, e);
        }
    }
    
    /**
     * 分析玩家行为
     *
     * @param playerId 玩家ID
     * @param behaviorType 行为类型
     * @param data 行为数据
     * @return 异常评分（0-100）
     */
    public float analyzePlayerBehavior(String playerId, String behaviorType, Map<String, Object> data) {
        if (playerId == null || behaviorType == null) {
            return 0;
        }
        
        try {
            // 记录行为
            recordBehavior(playerId, behaviorType, data);
            
            // 应用所有检测器
            float maxScore = 0;
            for (AnomalyDetector detector : anomalyDetectors) {
                float score = detector.detect(playerId, behaviorType, data);
                if (score > maxScore) {
                    maxScore = score;
                }
                
                // 如果某个检测器返回高分，立即返回
                if (score > 90) {
                    log.debug("玩家行为分析触发高风险警报: 玩家={}, 类型={}, 检测器={}, 评分={}",
                            playerId, behaviorType, detector.getName(), score);
                    updatePlayerRiskScore(playerId, score);
                    return score;
                }
            }
            
            // 更新风险评分
            updatePlayerRiskScore(playerId, maxScore);
            
            // 检查是否需要触发处罚
            if (getPlayerRiskScore(playerId) > RISK_THRESHOLD) {
                handleHighRiskPlayer(playerId);
            }
            
            return maxScore;
        } catch (Exception e) {
            log.error("分析玩家行为失败: {}", playerId, e);
            return 0;
        }
    }
    
    /**
     * 更新玩家行为模型
     *
     * @param playerId 玩家ID
     * @param behavior 新的行为
     */
    private void updatePlayerModel(String playerId, PlayerBehavior behavior) {
        BehaviorModel model = getOrCreatePlayerModel(playerId);
        
        // 更新行为频率
        model.getBehaviorFrequency().compute(behavior.getType(), (k, v) -> {
            if (v == null) {
                return 1;
            }
            return v + 1;
        });
        
        // 更新行为模式
        List<PlayerBehavior> recentBehaviors = getPlayerBehaviors(playerId).stream()
                .filter(b -> b.getTimestamp().isAfter(Instant.now().minus(Duration.ofMinutes(10))))
                .collect(Collectors.toList());
        
        // 计算每种行为类型在最近10分钟内的频率
        Map<String, Float> rateMap = new HashMap<>();
        for (String type : BEHAVIOR_TYPES) {
            long count = recentBehaviors.stream()
                    .filter(b -> type.equals(b.getType()))
                    .count();
            rateMap.put(type, count / 10.0f);  // 每分钟频率
        }
        model.setRecentRates(rateMap);
        
        // 更新最后行为时间
        model.setLastActivityTime(behavior.getTimestamp());
    }
    
    /**
     * 获取或创建玩家行为模型
     *
     * @param playerId 玩家ID
     * @return 行为模型
     */
    private BehaviorModel getOrCreatePlayerModel(String playerId) {
        return playerModels.computeIfAbsent(playerId, k -> new BehaviorModel());
    }
    
    /**
     * 获取玩家行为列表
     *
     * @param playerId 玩家ID
     * @return 行为列表
     */
    private List<PlayerBehavior> getPlayerBehaviors(String playerId) {
        return playerBehaviors.getOrDefault(playerId, Collections.emptyList());
    }
    
    /**
     * 计算某种行为的频率
     *
     * @param playerId 玩家ID
     * @param behaviorType 行为类型
     * @param window 时间窗口
     * @return 每分钟频率
     */
    private float calculateBehaviorRate(String playerId, String behaviorType, Duration window) {
        List<PlayerBehavior> behaviors = getPlayerBehaviors(playerId);
        Instant cutoff = Instant.now().minus(window);
        
        long count = behaviors.stream()
                .filter(b -> b.getType().equals(behaviorType))
                .filter(b -> b.getTimestamp().isAfter(cutoff))
                .count();
        
        // 转换为每分钟的频率
        return count * 60.0f / window.getSeconds();
    }
    
    /**
     * 计算模式匹配分数
     *
     * @param model 玩家行为模型
     * @param pattern 行为模式
     * @return 匹配分数（0-1）
     */
    private float calculatePatternMatchScore(BehaviorModel model, BehaviorPattern pattern) {
        Map<String, Float> playerRates = model.getRecentRates();
        Map<String, Float> patternRates = pattern.getRates();
        
        if (playerRates.isEmpty()) {
            return 0;
        }
        
        float totalDiff = 0;
        int count = 0;
        
        for (String type : patternRates.keySet()) {
            float patternRate = patternRates.get(type);
            float playerRate = playerRates.getOrDefault(type, 0.0f);
            
            if (patternRate > 0) {
                float diff = Math.abs(playerRate - patternRate) / patternRate;
                totalDiff += diff;
                count++;
            }
        }
        
        if (count == 0) {
            return 0;
        }
        
        float avgDiff = totalDiff / count;
        return Math.max(0, 1 - avgDiff);
    }
    
    /**
     * 更新玩家风险评分
     *
     * @param playerId 玩家ID
     * @param score 本次评分
     */
    private void updatePlayerRiskScore(String playerId, float score) {
        riskScores.compute(playerId, (k, oldScore) -> {
            if (oldScore == null) {
                return score;
            }
            
            // 使用加权平均值，新分数权重0.3
            return oldScore * 0.7f + score * 0.3f;
        });
    }
    
    /**
     * 获取玩家风险评分
     *
     * @param playerId 玩家ID
     * @return 风险评分
     */
    public float getPlayerRiskScore(String playerId) {
        return riskScores.getOrDefault(playerId, 0.0f);
    }
    
    /**
     * 处理高风险玩家
     *
     * @param playerId 玩家ID
     */
    private void handleHighRiskPlayer(String playerId) {
        float score = getPlayerRiskScore(playerId);
        log.warn("检测到高风险玩家: playerId={}, 风险评分={}", playerId, score);
        
        // 风险等级处理
        if (score > 90) {
            // 极高风险 - 自动封禁
            blacklistManager.addToAccountBlacklist(playerId, Duration.ofHours(24));
            log.info("已自动封禁高风险玩家: playerId={}, 时长=24小时", playerId);
        } else if (score > 80) {
            // 高风险 - 标记并监控
            // 实际项目中可以实现更多监控逻辑
            log.info("已标记高风险玩家: playerId={}", playerId);
        }
    }
    
    /**
     * 清理过期数据
     */
    private void cleanupOldData() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        
        // 清理过期的行为记录
        for (Map.Entry<String, List<PlayerBehavior>> entry : playerBehaviors.entrySet()) {
            entry.getValue().removeIf(b -> b.getTimestamp().isBefore(cutoff));
        }
        
        // 清理长时间不活跃的玩家模型
        playerModels.entrySet().removeIf(entry -> {
            BehaviorModel model = entry.getValue();
            return model.getLastActivityTime() != null && 
                   model.getLastActivityTime().isBefore(Instant.now().minus(Duration.ofDays(7)));
        });
        
        log.debug("已清理过期行为数据");
    }
    
    /**
     * 异常检测器
     */
    @RequiredArgsConstructor
    private static class AnomalyDetector {
        /**
         * 检测器名称
         */
        private final String name;
        
        /**
         * 检测函数
         */
        private final AnomalyDetectionFunction detectionFunction;
        
        /**
         * 执行检测
         *
         * @param playerId 玩家ID
         * @param behaviorType 行为类型
         * @param data 行为数据
         * @return 异常评分（0-100）
         */
        public float detect(String playerId, String behaviorType, Map<String, Object> data) {
            try {
                return detectionFunction.detect(playerId, behaviorType, data);
            } catch (Exception e) {
                log.error("异常检测器执行失败: {}", name, e);
                return 0;
            }
        }
        
        /**
         * 获取检测器名称
         *
         * @return 名称
         */
        public String getName() {
            return name;
        }
    }
    
    /**
     * 异常检测函数接口
     */
    @FunctionalInterface
    private interface AnomalyDetectionFunction {
        /**
         * 检测异常
         *
         * @param playerId 玩家ID
         * @param behaviorType 行为类型
         * @param data 行为数据
         * @return 异常评分（0-100）
         */
        float detect(String playerId, String behaviorType, Map<String, Object> data);
    }
    
    /**
     * 玩家行为记录
     */
    @Data
    public static class PlayerBehavior {
        /**
         * 玩家ID
         */
        private final String playerId;
        
        /**
         * 行为类型
         */
        private final String type;
        
        /**
         * 行为数据
         */
        private final Map<String, Object> data;
        
        /**
         * 时间戳
         */
        private final Instant timestamp;
    }
    
    /**
     * 玩家行为模型
     */
    @Data
    public static class BehaviorModel {
        /**
         * 行为频率统计
         */
        private final Map<String, Integer> behaviorFrequency = new ConcurrentHashMap<>();
        
        /**
         * 最近行为频率（每分钟）
         */
        private Map<String, Float> recentRates = new ConcurrentHashMap<>();
        
        /**
         * 最后活动时间
         */
        private Instant lastActivityTime;
    }
    
    /**
     * 行为模式定义
     */
    @Data
    public static class BehaviorPattern {
        /**
         * 模式名称
         */
        private final String name;
        
        /**
         * 行为频率特征（每分钟）
         */
        private final Map<String, Float> rates;
        
        /**
         * 匹配容差（0-1）
         */
        private final float tolerance;
    }
}