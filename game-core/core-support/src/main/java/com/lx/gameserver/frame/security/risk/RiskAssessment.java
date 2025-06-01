/*
 * 文件名: RiskAssessment.java
 * 用途: 风险评估引擎
 * 实现内容:
 *   - 玩家风险评分
 *   - 交易风险评估
 *   - 账号风险评估
 *   - 行为风险评估
 *   - 综合风险模型
 * 技术选型:
 *   - 多维度风险模型
 *   - 评分卡系统
 *   - 阈值控制
 * 依赖关系:
 *   - 被安全模块使用
 *   - 使用AnomalyDetection
 */
package com.lx.gameserver.frame.security.risk;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 风险评估引擎
 * <p>
 * 提供全面的风险评估能力，包括玩家风险评分、交易风险评估、
 * 账号风险评估和行为风险评估等，支持综合风险模型和自定义规则。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class RiskAssessment {

    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 异常检测器
     */
    @Nullable
    private final AnomalyDetection anomalyDetection;

    /**
     * 风险评分缓存
     * Key: 评估目标ID
     * Value: 风险评分信息
     */
    private final Map<String, RiskScore> riskScoreCache = new ConcurrentHashMap<>();

    /**
     * 风险规则登记表
     * Key: 规则ID
     * Value: 风险规则
     */
    private final Map<String, RiskRule> riskRules = new HashMap<>();

    /**
     * 风险阈值（0-100）
     */
    private int riskThreshold = 75;

    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param anomalyDetection 异常检测器
     */
    @Autowired
    public RiskAssessment(SecurityProperties securityProperties,
                        @Nullable AnomalyDetection anomalyDetection) {
        this.securityProperties = securityProperties;
        this.anomalyDetection = anomalyDetection;
        
        // 初始化风险规则
        initRiskRules();
        
        log.info("风险评估引擎初始化完成");
    }

    /**
     * 初始化风险规则
     */
    private void initRiskRules() {
        // 登录风险规则
        addRiskRule("login:ip_change", "IP变更风险", 
                data -> {
                    String lastLoginIp = (String) data.get("lastLoginIp");
                    String currentIp = (String) data.get("currentIp");
                    if (lastLoginIp != null && currentIp != null && !lastLoginIp.equals(currentIp)) {
                        return 30.0f; // IP变更基础分
                    }
                    return 0.0f;
                });
        
        // 登录地理位置风险规则
        addRiskRule("login:geo_change", "地理位置变更风险", 
                data -> {
                    String lastGeo = (String) data.get("lastGeo");
                    String currentGeo = (String) data.get("currentGeo");
                    if (lastGeo != null && currentGeo != null && !lastGeo.equals(currentGeo)) {
                        return 40.0f; // 地理位置变更基础分
                    }
                    return 0.0f;
                });
        
        // 账号年龄风险规则
        addRiskRule("account:age", "账号年龄风险", 
                data -> {
                    Integer accountAgeDays = (Integer) data.get("accountAgeDays");
                    if (accountAgeDays != null) {
                        if (accountAgeDays < 1) return 50.0f;
                        if (accountAgeDays < 7) return 30.0f;
                        if (accountAgeDays < 30) return 15.0f;
                    }
                    return 0.0f;
                });
        
        // 交易金额风险规则
        addRiskRule("transaction:amount", "交易金额风险", 
                data -> {
                    Number amount = (Number) data.get("amount");
                    Number userAvgAmount = (Number) data.get("userAvgAmount");
                    if (amount != null && userAvgAmount != null) {
                        float ratio = amount.floatValue() / userAvgAmount.floatValue();
                        if (ratio > 10) return 80.0f;
                        if (ratio > 5) return 50.0f;
                        if (ratio > 2) return 20.0f;
                    }
                    return 0.0f;
                });
        
        // 交易频率风险规则
        addRiskRule("transaction:frequency", "交易频率风险", 
                data -> {
                    Integer txCount1h = (Integer) data.get("txCount1h");
                    if (txCount1h != null) {
                        if (txCount1h > 20) return 70.0f;
                        if (txCount1h > 10) return 40.0f;
                        if (txCount1h > 5) return 20.0f;
                    }
                    return 0.0f;
                });
        
        // 设备指纹风险规则
        addRiskRule("device:fingerprint", "设备指纹风险", 
                data -> {
                    Boolean knownDevice = (Boolean) data.get("knownDevice");
                    if (knownDevice != null && !knownDevice) {
                        return 40.0f;
                    }
                    return 0.0f;
                });
        
        // 行为模式风险规则
        addRiskRule("behavior:pattern", "行为模式风险", 
                data -> {
                    Float anomalyScore = (Float) data.get("anomalyScore");
                    if (anomalyScore != null) {
                        return anomalyScore;
                    }
                    return 0.0f;
                });
    }

    /**
     * 添加风险规则
     *
     * @param ruleId 规则ID
     * @param ruleName 规则名称
     * @param ruleFunction 规则评分函数
     */
    public void addRiskRule(String ruleId, String ruleName, Function<Map<String, Object>, Float> ruleFunction) {
        riskRules.put(ruleId, new RiskRule(ruleId, ruleName, ruleFunction));
        log.debug("添加风险规则: id={}, name={}", ruleId, ruleName);
    }

    /**
     * 移除风险规则
     *
     * @param ruleId 规则ID
     */
    public void removeRiskRule(String ruleId) {
        riskRules.remove(ruleId);
        log.debug("移除风险规则: id={}", ruleId);
    }

    /**
     * 评估登录风险
     *
     * @param userId 用户ID
     * @param ip IP地址
     * @param geoLocation 地理位置
     * @param deviceId 设备ID
     * @param userAgent 用户代理
     * @return 风险评估结果
     */
    public RiskResult assessLoginRisk(String userId, String ip, String geoLocation,
                                   String deviceId, String userAgent) {
        // 准备评估数据
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("currentIp", ip);
        data.put("currentGeo", geoLocation);
        data.put("deviceId", deviceId);
        data.put("userAgent", userAgent);
        
        // 获取历史登录数据
        RiskScore lastScore = riskScoreCache.get("login:" + userId);
        if (lastScore != null) {
            data.put("lastLoginIp", lastScore.getContext().get("currentIp"));
            data.put("lastGeo", lastScore.getContext().get("currentGeo"));
            data.put("lastDeviceId", lastScore.getContext().get("deviceId"));
            data.put("knownDevice", deviceId.equals(lastScore.getContext().get("deviceId")));
        } else {
            data.put("knownDevice", false);
        }
        
        // 如果有异常检测器，获取异常评分
        if (anomalyDetection != null) {
            float anomalyScore = anomalyDetection.detectLoginAnomaly(userId, ip, deviceId, userAgent);
            data.put("anomalyScore", anomalyScore);
        }
        
        // 查询账号年龄
        // 这里简单模拟，实际项目中应该从数据库查询
        data.put("accountAgeDays", 30);
        
        // 执行风险评估
        return assessRisk("login", userId, data);
    }

    /**
     * 评估交易风险
     *
     * @param userId 用户ID
     * @param transactionId 交易ID
     * @param amount 交易金额
     * @param recipient 接收方
     * @param ip IP地址
     * @return 风险评估结果
     */
    public RiskResult assessTransactionRisk(String userId, String transactionId,
                                         double amount, String recipient, String ip) {
        // 准备评估数据
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("transactionId", transactionId);
        data.put("amount", amount);
        data.put("recipient", recipient);
        data.put("currentIp", ip);
        
        // 模拟用户平均交易金额
        data.put("userAvgAmount", 100.0);
        
        // 模拟近期交易频率
        data.put("txCount1h", 3);
        
        // 如果有异常检测器，获取交易异常评分
        if (anomalyDetection != null) {
            float anomalyScore = anomalyDetection.detectTransactionAnomaly(userId, amount, recipient);
            data.put("anomalyScore", anomalyScore);
        }
        
        // 执行风险评估
        return assessRisk("transaction", transactionId, data);
    }

    /**
     * 评估账号风险
     *
     * @param userId 用户ID
     * @param context 上下文数据
     * @return 风险评估结果
     */
    public RiskResult assessAccountRisk(String userId, Map<String, Object> context) {
        Map<String, Object> data = new HashMap<>(context);
        data.put("userId", userId);
        
        // 如果有异常检测器，获取账号异常评分
        if (anomalyDetection != null) {
            float anomalyScore = anomalyDetection.detectAccountAnomaly(userId);
            data.put("anomalyScore", anomalyScore);
        }
        
        // 执行风险评估
        return assessRisk("account", userId, data);
    }

    /**
     * 评估行为风险
     *
     * @param userId 用户ID
     * @param behaviorType 行为类型
     * @param context 上下文数据
     * @return 风险评估结果
     */
    public RiskResult assessBehaviorRisk(String userId, String behaviorType,
                                      Map<String, Object> context) {
        Map<String, Object> data = new HashMap<>(context);
        data.put("userId", userId);
        data.put("behaviorType", behaviorType);
        
        // 如果有异常检测器，获取行为异常评分
        if (anomalyDetection != null) {
            float anomalyScore = anomalyDetection.detectBehaviorAnomaly(userId, behaviorType, context);
            data.put("anomalyScore", anomalyScore);
        }
        
        // 执行风险评估
        return assessRisk("behavior:" + behaviorType, userId, data);
    }

    /**
     * 通用风险评估方法
     *
     * @param category 风险类别
     * @param targetId 目标ID
     * @param data 评估数据
     * @return 风险评估结果
     */
    public RiskResult assessRisk(String category, String targetId, Map<String, Object> data) {
        String riskKey = category + ":" + targetId;
        
        // 执行各项风险规则评估
        float totalScore = 0.0f;
        Map<String, Float> ruleScores = new HashMap<>();
        
        for (RiskRule rule : riskRules.values()) {
            // 只评估适用于当前类别的规则
            if (rule.getRuleId().startsWith(category.split(":")[0])) {
                float score = rule.evaluate(data);
                if (score > 0) {
                    ruleScores.put(rule.getRuleId(), score);
                    totalScore = Math.max(totalScore, score);
                }
            }
        }
        
        // 计算综合风险评分
        float finalScore = totalScore;
        
        // 判断风险级别
        RiskLevel riskLevel = getRiskLevel(finalScore);
        
        // 缓存风险评分
        RiskScore riskScore = new RiskScore(
                riskKey,
                finalScore,
                riskLevel,
                Instant.now(),
                data
        );
        riskScoreCache.put(riskKey, riskScore);
        
        // 构建风险评估结果
        return RiskResult.builder()
                .riskKey(riskKey)
                .score(finalScore)
                .level(riskLevel)
                .ruleScores(ruleScores)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 根据风险分数获取风险级别
     *
     * @param score 风险分数
     * @return 风险级别
     */
    private RiskLevel getRiskLevel(float score) {
        if (score >= 80) {
            return RiskLevel.HIGH;
        } else if (score >= 50) {
            return RiskLevel.MEDIUM;
        } else if (score >= 20) {
            return RiskLevel.LOW;
        } else {
            return RiskLevel.MINIMAL;
        }
    }

    /**
     * 获取目标的风险评分
     *
     * @param category 风险类别
     * @param targetId 目标ID
     * @return 风险评分，如果不存在返回null
     */
    @Nullable
    public RiskScore getRiskScore(String category, String targetId) {
        String riskKey = category + ":" + targetId;
        return riskScoreCache.get(riskKey);
    }

    /**
     * 清除过期的风险评分
     *
     * @param expiryDuration 过期时长
     */
    public void clearExpiredScores(Duration expiryDuration) {
        Instant cutoff = Instant.now().minus(expiryDuration);
        
        riskScoreCache.entrySet().removeIf(entry -> 
                entry.getValue().getTimestamp().isBefore(cutoff));
        
        log.debug("已清理过期风险评分记录");
    }

    /**
     * 设置风险阈值
     *
     * @param threshold 阈值（0-100）
     */
    public void setRiskThreshold(int threshold) {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("风险阈值必须在0-100范围内");
        }
        this.riskThreshold = threshold;
        log.info("风险阈值已设置为: {}", threshold);
    }

    /**
     * 获取风险阈值
     *
     * @return 风险阈值
     */
    public int getRiskThreshold() {
        return riskThreshold;
    }

    /**
     * 风险规则
     */
    private static class RiskRule {
        /**
         * 规则ID
         */
        private final String ruleId;
        
        /**
         * 规则名称
         */
        private final String ruleName;
        
        /**
         * 规则评分函数
         */
        private final Function<Map<String, Object>, Float> ruleFunction;

        /**
         * 构造函数
         *
         * @param ruleId 规则ID
         * @param ruleName 规则名称
         * @param ruleFunction 规则评分函数
         */
        public RiskRule(String ruleId, String ruleName, Function<Map<String, Object>, Float> ruleFunction) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.ruleFunction = ruleFunction;
        }

        /**
         * 评估风险规则
         *
         * @param data 评估数据
         * @return 风险评分
         */
        public float evaluate(Map<String, Object> data) {
            try {
                return ruleFunction.apply(data);
            } catch (Exception e) {
                log.error("规则评估失败: ruleId={}", ruleId, e);
                return 0.0f;
            }
        }

        /**
         * 获取规则ID
         *
         * @return 规则ID
         */
        public String getRuleId() {
            return ruleId;
        }

        /**
         * 获取规则名称
         *
         * @return 规则名称
         */
        public String getRuleName() {
            return ruleName;
        }
    }

    /**
     * 风险评分
     */
    @Data
    public static class RiskScore {
        /**
         * 风险键
         */
        private final String riskKey;
        
        /**
         * 风险评分
         */
        private final float score;
        
        /**
         * 风险级别
         */
        private final RiskLevel level;
        
        /**
         * 评分时间戳
         */
        private final Instant timestamp;
        
        /**
         * 上下文数据
         */
        private final Map<String, Object> context;
    }

    /**
     * 风险评估结果
     */
    @Data
    @Builder
    public static class RiskResult {
        /**
         * 风险键
         */
        private final String riskKey;
        
        /**
         * 风险评分
         */
        private final float score;
        
        /**
         * 风险级别
         */
        private final RiskLevel level;
        
        /**
         * 规则评分
         */
        private final Map<String, Float> ruleScores;
        
        /**
         * 评估时间戳
         */
        private final Instant timestamp;
        
        /**
         * 是否需要进一步审查
         */
        @Builder.Default
        private final boolean requiresReview = false;
        
        /**
         * 是否自动拒绝
         *
         * @return 如果风险级别为高返回true，否则返回false
         */
        public boolean isAutoReject() {
            return level == RiskLevel.HIGH;
        }
    }

    /**
     * 风险级别
     */
    public enum RiskLevel {
        /**
         * 最小风险
         */
        MINIMAL,
        
        /**
         * 低风险
         */
        LOW,
        
        /**
         * 中等风险
         */
        MEDIUM,
        
        /**
         * 高风险
         */
        HIGH
    }
}