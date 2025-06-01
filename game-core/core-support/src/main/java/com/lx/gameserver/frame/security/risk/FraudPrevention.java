/*
 * 文件名: FraudPrevention.java
 * 用途: 欺诈预防系统
 * 实现内容:
 *   - 盗号检测
 *   - 虚假充值检测
 *   - 恶意退款检测
 *   - 账号交易检测
 *   - 自动冻结机制
 * 技术选型:
 *   - 多维度风险模型
 *   - 规则引擎
 *   - 行为模式分析
 * 依赖关系:
 *   - 使用RiskAssessment
 *   - 使用AnomalyDetection
 */
package com.lx.gameserver.frame.security.risk;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.protection.BlacklistManager;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 欺诈预防系统
 * <p>
 * 提供全面的欺诈行为检测与防范功能，包括盗号检测、
 * 虚假充值检测、恶意退款检测和账号交易检测等，支持自动冻结机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class FraudPrevention {
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 风险评估器
     */
    private final RiskAssessment riskAssessment;
    
    /**
     * 异常检测器
     */
    private final AnomalyDetection anomalyDetection;
    
    /**
     * 黑名单管理器
     */
    private final BlacklistManager blacklistManager;
    
    /**
     * 账号冻结记录
     * Key: 账号ID
     * Value: 冻结信息
     */
    private final Map<String, FreezeRecord> frozenAccounts = new ConcurrentHashMap<>();
    
    /**
     * 检测规则
     * Key: 规则ID
     * Value: 检测规则
     */
    private final Map<String, FraudDetectionRule> detectionRules = new HashMap<>();
    
    /**
     * 检测阈值
     */
    private float detectionThreshold = 75.0f;
    
    /**
     * 冻结时长（小时）
     */
    private int freezeDurationHours = 24;
    
    /**
     * 是否自动冻结
     */
    private boolean autoFreezeEnabled = true;
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param riskAssessment 风险评估器
     * @param anomalyDetection 异常检测器
     * @param blacklistManager 黑名单管理器
     */
    @Autowired
    public FraudPrevention(SecurityProperties securityProperties,
                         RiskAssessment riskAssessment,
                         AnomalyDetection anomalyDetection,
                         BlacklistManager blacklistManager) {
        this.securityProperties = securityProperties;
        this.riskAssessment = riskAssessment;
        this.anomalyDetection = anomalyDetection;
        this.blacklistManager = blacklistManager;
        
        // 初始化检测规则
        initDetectionRules();
        
        // 启动清理任务
        startCleanupTask();
        
        log.info("欺诈预防系统初始化完成");
    }
    
    /**
     * 初始化检测规则
     */
    private void initDetectionRules() {
        // 盗号检测规则
        addDetectionRule("account:theft", "盗号检测", (details, context) -> {
            float score = 0;
            
            // 检查IP变更
            if (context.containsKey("ipChanged") && (boolean) context.get("ipChanged")) {
                score += 30;
            }
            
            // 检查设备变更
            if (context.containsKey("deviceChanged") && (boolean) context.get("deviceChanged")) {
                score += 30;
            }
            
            // 检查异常行为
            if (context.containsKey("abnormalBehavior") && (boolean) context.get("abnormalBehavior")) {
                score += 40;
            }
            
            // 检查密码重置
            if (context.containsKey("passwordReset") && (boolean) context.get("passwordReset")) {
                score += 20;
            }
            
            // 检查敏感操作
            if (context.containsKey("sensitiveOperations")) {
                int operations = (int) context.get("sensitiveOperations");
                score += Math.min(operations * 10, 40);
            }
            
            return Math.min(score, 100);
        });
        
        // 虚假充值检测规则
        addDetectionRule("payment:fake", "虚假充值检测", (details, context) -> {
            float score = 0;
            
            // 检查失败的付款尝试次数
            if (context.containsKey("failedAttempts")) {
                int attempts = (int) context.get("failedAttempts");
                score += Math.min(attempts * 15, 60);
            }
            
            // 检查支付方式异常变更
            if (context.containsKey("paymentMethodChanges")) {
                int changes = (int) context.get("paymentMethodChanges");
                score += Math.min(changes * 20, 50);
            }
            
            // 检查异常金额
            if (context.containsKey("unusualAmount") && (boolean) context.get("unusualAmount")) {
                score += 30;
            }
            
            // 检查高风险支付渠道
            if (context.containsKey("highRiskChannel") && (boolean) context.get("highRiskChannel")) {
                score += 40;
            }
            
            return Math.min(score, 100);
        });
        
        // 恶意退款检测规则
        addDetectionRule("payment:refund", "恶意退款检测", (details, context) -> {
            float score = 0;
            
            // 检查退款频率
            if (context.containsKey("refundFrequency")) {
                int frequency = (int) context.get("refundFrequency");
                score += Math.min(frequency * 20, 70);
            }
            
            // 检查消费后快速退款
            if (context.containsKey("quickRefund") && (boolean) context.get("quickRefund")) {
                score += 40;
            }
            
            // 检查消费后资源已用尽
            if (context.containsKey("resourcesUsed") && (boolean) context.get("resourcesUsed")) {
                score += 50;
            }
            
            // 检查用户退款历史
            if (context.containsKey("refundHistory")) {
                int history = (int) context.get("refundHistory");
                score += Math.min(history * 15, 50);
            }
            
            return Math.min(score, 100);
        });
        
        // 账号交易检测规则
        addDetectionRule("account:trading", "账号交易检测", (details, context) -> {
            float score = 0;
            
            // 检查资源异常迁移
            if (context.containsKey("resourceTransfer")) {
                int transferAmount = (int) context.get("resourceTransfer");
                score += Math.min(transferAmount / 10, 70);
            }
            
            // 检查账号信息变更
            if (context.containsKey("infoChanges") && (boolean) context.get("infoChanges")) {
                score += 30;
            }
            
            // 检查交易相关聊天内容
            if (context.containsKey("tradingChat") && (boolean) context.get("tradingChat")) {
                score += 40;
            }
            
            // 检查账号使用模式变化
            if (context.containsKey("usageChange") && (boolean) context.get("usageChange")) {
                score += 50;
            }
            
            return Math.min(score, 100);
        });
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    cleanupExpiredFreezes();
                } catch (Exception e) {
                    log.error("清理过期冻结记录失败", e);
                }
            }
        }, 3600000, 3600000); // 每小时执行一次
    }
    
    /**
     * 清理过期的冻结记录
     */
    private void cleanupExpiredFreezes() {
        Instant now = Instant.now();
        
        frozenAccounts.entrySet().removeIf(entry -> {
            FreezeRecord record = entry.getValue();
            if (record.getExpiryTime() != null && record.getExpiryTime().isBefore(now)) {
                log.info("账号冻结已过期，自动解冻: accountId={}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 添加检测规则
     *
     * @param ruleId 规则ID
     * @param ruleName 规则名称
     * @param evaluator 评估函数
     */
    public void addDetectionRule(String ruleId, String ruleName, BiFunction<Map<String, Object>, Map<String, Object>, Float> evaluator) {
        detectionRules.put(ruleId, new FraudDetectionRule(ruleId, ruleName, evaluator));
        log.debug("添加欺诈检测规则: id={}, name={}", ruleId, ruleName);
    }
    
    /**
     * 检测账号盗号风险
     *
     * @param accountId 账号ID
     * @param ip 当前IP
     * @param deviceId 当前设备ID
     * @param details 详细信息
     * @return 检测结果
     */
    public FraudDetectionResult detectAccountTheft(String accountId, String ip, String deviceId, Map<String, Object> details) {
        // 检查账号是否已冻结
        if (isAccountFrozen(accountId)) {
            return createResult("account:theft", accountId, 100, "账号已被冻结", true);
        }
        
        try {
            // 准备评估上下文
            Map<String, Object> context = new HashMap<>();
            
            // 获取账号历史登录信息（这里简化实现，实际应从数据库获取）
            String lastIp = (String) details.getOrDefault("lastIp", "");
            String lastDeviceId = (String) details.getOrDefault("lastDeviceId", "");
            
            // 检查IP和设备变更
            boolean ipChanged = !lastIp.isEmpty() && !lastIp.equals(ip);
            boolean deviceChanged = !lastDeviceId.isEmpty() && !lastDeviceId.equals(deviceId);
            
            context.put("ipChanged", ipChanged);
            context.put("deviceChanged", deviceChanged);
            context.put("abnormalBehavior", details.getOrDefault("abnormalBehavior", false));
            context.put("passwordReset", details.getOrDefault("passwordReset", false));
            context.put("sensitiveOperations", details.getOrDefault("sensitiveOperations", 0));
            
            // 使用风险评估引擎
            if (riskAssessment != null) {
                RiskAssessment.RiskResult riskResult = riskAssessment.assessLoginRisk(
                        accountId, ip, (String) details.getOrDefault("geoLocation", ""),
                        deviceId, (String) details.getOrDefault("userAgent", ""));
                
                if (riskResult.getScore() > 70) {
                    context.put("highRiskLogin", true);
                }
            }
            
            // 执行盗号检测规则
            FraudDetectionRule rule = detectionRules.get("account:theft");
            if (rule == null) {
                return createResult("account:theft", accountId, 0, "规则不存在", false);
            }
            
            float score = rule.evaluate(details, context);
            boolean detected = score >= detectionThreshold;
            
            // 记录检测结果
            if (detected) {
                log.warn("检测到潜在盗号: accountId={}, score={}, ip={}, deviceId={}",
                        accountId, score, ip, deviceId);
                
                // 如果启用自动冻结，则冻结账号
                if (autoFreezeEnabled) {
                    freezeAccount(accountId, "盗号风险", Duration.ofHours(freezeDurationHours));
                }
            }
            
            return createResult("account:theft", accountId, score, detected ? "检测到盗号风险" : "未检测到盗号风险", detected);
            
        } catch (Exception e) {
            log.error("盗号检测失败: accountId={}", accountId, e);
            return createResult("account:theft", accountId, 0, "检测过程出错: " + e.getMessage(), false);
        }
    }
    
    /**
     * 检测虚假充值
     *
     * @param accountId 账号ID
     * @param paymentId 支付ID
     * @param amount 金额
     * @param details 详细信息
     * @return 检测结果
     */
    public FraudDetectionResult detectFakePayment(String accountId, String paymentId, 
                                              double amount, Map<String, Object> details) {
        // 检查账号是否已冻结
        if (isAccountFrozen(accountId)) {
            return createResult("payment:fake", accountId, 100, "账号已被冻结", true);
        }
        
        try {
            // 准备评估上下文
            Map<String, Object> context = new HashMap<>();
            context.put("failedAttempts", details.getOrDefault("failedAttempts", 0));
            context.put("paymentMethodChanges", details.getOrDefault("paymentMethodChanges", 0));
            context.put("unusualAmount", details.getOrDefault("unusualAmount", false));
            context.put("highRiskChannel", details.getOrDefault("highRiskChannel", false));
            
            // 使用风险评估引擎
            if (riskAssessment != null) {
                String ip = (String) details.getOrDefault("ip", "");
                String recipient = (String) details.getOrDefault("recipient", "");
                
                RiskAssessment.RiskResult riskResult = riskAssessment.assessTransactionRisk(
                        accountId, paymentId, amount, recipient, ip);
                
                if (riskResult.getScore() > 70) {
                    context.put("highRiskTransaction", true);
                }
            }
            
            // 执行虚假充值检测规则
            FraudDetectionRule rule = detectionRules.get("payment:fake");
            if (rule == null) {
                return createResult("payment:fake", accountId, 0, "规则不存在", false);
            }
            
            float score = rule.evaluate(details, context);
            boolean detected = score >= detectionThreshold;
            
            // 记录检测结果
            if (detected) {
                log.warn("检测到潜在虚假充值: accountId={}, paymentId={}, amount={}, score={}",
                        accountId, paymentId, amount, score);
                
                // 如果启用自动冻结，则冻结账号
                if (autoFreezeEnabled) {
                    freezeAccount(accountId, "虚假充值风险", Duration.ofHours(freezeDurationHours));
                }
            }
            
            return createResult("payment:fake", accountId, score, 
                    detected ? "检测到虚假充值风险" : "未检测到虚假充值风险", detected);
            
        } catch (Exception e) {
            log.error("虚假充值检测失败: accountId={}, paymentId={}", accountId, paymentId, e);
            return createResult("payment:fake", accountId, 0, "检测过程出错: " + e.getMessage(), false);
        }
    }
    
    /**
     * 检测恶意退款
     *
     * @param accountId 账号ID
     * @param refundId 退款ID
     * @param paymentId 原支付ID
     * @param details 详细信息
     * @return 检测结果
     */
    public FraudDetectionResult detectMaliciousRefund(String accountId, String refundId, 
                                                 String paymentId, Map<String, Object> details) {
        // 检查账号是否已冻结
        if (isAccountFrozen(accountId)) {
            return createResult("payment:refund", accountId, 100, "账号已被冻结", true);
        }
        
        try {
            // 准备评估上下文
            Map<String, Object> context = new HashMap<>();
            context.put("refundFrequency", details.getOrDefault("refundFrequency", 0));
            context.put("quickRefund", details.getOrDefault("quickRefund", false));
            context.put("resourcesUsed", details.getOrDefault("resourcesUsed", false));
            context.put("refundHistory", details.getOrDefault("refundHistory", 0));
            
            // 执行恶意退款检测规则
            FraudDetectionRule rule = detectionRules.get("payment:refund");
            if (rule == null) {
                return createResult("payment:refund", accountId, 0, "规则不存在", false);
            }
            
            float score = rule.evaluate(details, context);
            boolean detected = score >= detectionThreshold;
            
            // 记录检测结果
            if (detected) {
                log.warn("检测到潜在恶意退款: accountId={}, refundId={}, paymentId={}, score={}",
                        accountId, refundId, paymentId, score);
                
                // 如果启用自动冻结，则冻结账号
                if (autoFreezeEnabled && score > 85) { // 更高阈值用于退款检测
                    freezeAccount(accountId, "恶意退款风险", Duration.ofHours(freezeDurationHours));
                }
            }
            
            return createResult("payment:refund", accountId, score, 
                    detected ? "检测到恶意退款风险" : "未检测到恶意退款风险", detected);
            
        } catch (Exception e) {
            log.error("恶意退款检测失败: accountId={}, refundId={}", accountId, refundId, e);
            return createResult("payment:refund", accountId, 0, "检测过程出错: " + e.getMessage(), false);
        }
    }
    
    /**
     * 检测账号交易
     *
     * @param accountId 账号ID
     * @param details 详细信息
     * @return 检测结果
     */
    public FraudDetectionResult detectAccountTrading(String accountId, Map<String, Object> details) {
        // 检查账号是否已冻结
        if (isAccountFrozen(accountId)) {
            return createResult("account:trading", accountId, 100, "账号已被冻结", true);
        }
        
        try {
            // 准备评估上下文
            Map<String, Object> context = new HashMap<>();
            context.put("resourceTransfer", details.getOrDefault("resourceTransfer", 0));
            context.put("infoChanges", details.getOrDefault("infoChanges", false));
            context.put("tradingChat", details.getOrDefault("tradingChat", false));
            context.put("usageChange", details.getOrDefault("usageChange", false));
            
            // 使用行为分析器检测行为异常
            if (anomalyDetection != null) {
                float anomalyScore = anomalyDetection.detectBehaviorAnomaly(
                        accountId, "trade", details);
                
                if (anomalyScore > 70) {
                    context.put("abnormalTrading", true);
                }
            }
            
            // 执行账号交易检测规则
            FraudDetectionRule rule = detectionRules.get("account:trading");
            if (rule == null) {
                return createResult("account:trading", accountId, 0, "规则不存在", false);
            }
            
            float score = rule.evaluate(details, context);
            boolean detected = score >= detectionThreshold;
            
            // 记录检测结果
            if (detected) {
                log.warn("检测到潜在账号交易: accountId={}, score={}",
                        accountId, score);
                
                // 如果启用自动冻结，则冻结账号
                if (autoFreezeEnabled) {
                    freezeAccount(accountId, "账号交易风险", Duration.ofHours(freezeDurationHours));
                }
            }
            
            return createResult("account:trading", accountId, score, 
                    detected ? "检测到账号交易风险" : "未检测到账号交易风险", detected);
            
        } catch (Exception e) {
            log.error("账号交易检测失败: accountId={}", accountId, e);
            return createResult("account:trading", accountId, 0, "检测过程出错: " + e.getMessage(), false);
        }
    }
    
    /**
     * 冻结账号
     *
     * @param accountId 账号ID
     * @param reason 原因
     * @param duration 冻结时长
     * @return 是否冻结成功
     */
    public boolean freezeAccount(String accountId, String reason, Duration duration) {
        try {
            Instant now = Instant.now();
            Instant expiryTime = duration != null ? now.plus(duration) : null;
            
            // 记录冻结信息
            FreezeRecord record = new FreezeRecord(
                    accountId,
                    reason,
                    now,
                    expiryTime,
                    "system"
            );
            
            frozenAccounts.put(accountId, record);
            
            // 添加到黑名单
            if (blacklistManager != null) {
                blacklistManager.addToAccountBlacklist(accountId, duration);
            }
            
            log.info("账号已冻结: accountId={}, reason={}, duration={}",
                    accountId, reason, duration);
            
            return true;
        } catch (Exception e) {
            log.error("冻结账号失败: accountId={}", accountId, e);
            return false;
        }
    }
    
    /**
     * 解冻账号
     *
     * @param accountId 账号ID
     * @param operatorId 操作人ID
     * @param reason 原因
     * @return 是否解冻成功
     */
    public boolean unfreezeAccount(String accountId, String operatorId, String reason) {
        try {
            // 移除冻结记录
            FreezeRecord record = frozenAccounts.remove(accountId);
            if (record == null) {
                return false; // 账号未被冻结
            }
            
            // 从黑名单移除
            if (blacklistManager != null) {
                blacklistManager.removeFromAccountBlacklist(accountId);
            }
            
            log.info("账号已解冻: accountId={}, operator={}, reason={}",
                    accountId, operatorId, reason);
            
            return true;
        } catch (Exception e) {
            log.error("解冻账号失败: accountId={}", accountId, e);
            return false;
        }
    }
    
    /**
     * 检查账号是否被冻结
     *
     * @param accountId 账号ID
     * @return 是否被冻结
     */
    public boolean isAccountFrozen(String accountId) {
        FreezeRecord record = frozenAccounts.get(accountId);
        if (record == null) {
            return false;
        }
        
        // 检查是否永久冻结或者冻结期未过
        return record.getExpiryTime() == null ||
                record.getExpiryTime().isAfter(Instant.now());
    }
    
    /**
     * 获取账号冻结信息
     *
     * @param accountId 账号ID
     * @return 冻结记录，如果未冻结则返回null
     */
    @Nullable
    public FreezeRecord getFreezeRecord(String accountId) {
        return frozenAccounts.get(accountId);
    }
    
    /**
     * 设置检测阈值
     *
     * @param threshold 阈值（0-100）
     */
    public void setDetectionThreshold(float threshold) {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("检测阈值必须在0-100范围内");
        }
        this.detectionThreshold = threshold;
        log.info("检测阈值已设置为: {}", threshold);
    }
    
    /**
     * 设置冻结时长
     *
     * @param hours 小时数
     */
    public void setFreezeDurationHours(int hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("冻结时长必须大于0");
        }
        this.freezeDurationHours = hours;
        log.info("冻结时长已设置为: {}小时", hours);
    }
    
    /**
     * 设置是否自动冻结
     *
     * @param enabled 是否启用
     */
    public void setAutoFreezeEnabled(boolean enabled) {
        this.autoFreezeEnabled = enabled;
        log.info("自动冻结功能: {}", enabled ? "已启用" : "已禁用");
    }
    
    /**
     * 创建检测结果
     *
     * @param type 检测类型
     * @param accountId 账号ID
     * @param score 评分
     * @param message 消息
     * @param detected 是否检测到
     * @return 检测结果
     */
    private FraudDetectionResult createResult(String type, String accountId, 
                                         float score, String message, boolean detected) {
        return FraudDetectionResult.builder()
                .type(type)
                .accountId(accountId)
                .score(score)
                .message(message)
                .detected(detected)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 欺诈检测规则
     */
    private static class FraudDetectionRule {
        /**
         * 规则ID
         */
        private final String ruleId;
        
        /**
         * 规则名称
         */
        private final String ruleName;
        
        /**
         * 评估函数
         */
        private final BiFunction<Map<String, Object>, Map<String, Object>, Float> evaluator;
        
        /**
         * 构造函数
         *
         * @param ruleId 规则ID
         * @param ruleName 规则名称
         * @param evaluator 评估函数
         */
        public FraudDetectionRule(String ruleId, String ruleName, 
                             BiFunction<Map<String, Object>, Map<String, Object>, Float> evaluator) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.evaluator = evaluator;
        }
        
        /**
         * 评估规则
         *
         * @param details 详细信息
         * @param context 上下文信息
         * @return 评分（0-100）
         */
        public float evaluate(Map<String, Object> details, Map<String, Object> context) {
            try {
                return evaluator.apply(details, context);
            } catch (Exception e) {
                log.error("规则评估失败: id={}", ruleId, e);
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
     * 冻结记录
     */
    @Data
    public static class FreezeRecord {
        /**
         * 账号ID
         */
        private final String accountId;
        
        /**
         * 冻结原因
         */
        private final String reason;
        
        /**
         * 冻结时间
         */
        private final Instant freezeTime;
        
        /**
         * 过期时间（如果为null则永久冻结）
         */
        @Nullable
        private final Instant expiryTime;
        
        /**
         * 操作人
         */
        private final String operator;
    }
    
    /**
     * 欺诈检测结果
     */
    @Data
    @Builder
    public static class FraudDetectionResult {
        /**
         * 检测类型
         */
        private final String type;
        
        /**
         * 账号ID
         */
        private final String accountId;
        
        /**
         * 评分
         */
        private final float score;
        
        /**
         * 消息
         */
        private final String message;
        
        /**
         * 是否检测到
         */
        private final boolean detected;
        
        /**
         * 时间戳
         */
        private final Instant timestamp;
    }
}