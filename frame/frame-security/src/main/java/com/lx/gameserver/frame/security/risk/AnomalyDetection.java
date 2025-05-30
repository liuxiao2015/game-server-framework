/*
 * 文件名: AnomalyDetection.java
 * 用途: 异常检测系统
 * 实现内容:
 *   - 登录异常检测
 *   - 消费异常检测
 *   - 社交异常检测
 *   - 数据异常检测
 *   - 实时预警
 * 技术选型:
 *   - 统计模式检测
 *   - 轻量级机器学习算法
 *   - 阈值控制
 * 依赖关系:
 *   - 被RiskAssessment使用
 *   - 被FraudPrevention使用
 */
package com.lx.gameserver.frame.security.risk;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 异常检测系统
 * <p>
 * 提供各种异常行为的检测功能，包括登录异常、消费异常、
 * 社交行为异常和游戏数据异常等，支持实时预警和多维度分析。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class AnomalyDetection {

    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 用户IP历史
     * Key: 用户ID
     * Value: IP历史记录
     */
    private final Map<String, List<IPRecord>> userIpHistory = new ConcurrentHashMap<>();
    
    /**
     * 用户设备历史
     * Key: 用户ID
     * Value: 设备历史记录
     */
    private final Map<String, List<DeviceRecord>> userDeviceHistory = new ConcurrentHashMap<>();
    
    /**
     * 用户交易历史
     * Key: 用户ID
     * Value: 交易历史记录
     */
    private final Map<String, List<TransactionRecord>> userTransactionHistory = new ConcurrentHashMap<>();
    
    /**
     * 用户行为历史
     * Key: 用户ID-行为类型
     * Value: 行为历史记录
     */
    private final Map<String, List<BehaviorRecord>> userBehaviorHistory = new ConcurrentHashMap<>();
    
    /**
     * 异常计数
     * Key: 用户ID-异常类型
     * Value: 计数器
     */
    private final Map<String, AtomicInteger> anomalyCounters = new ConcurrentHashMap<>();
    
    /**
     * 异常阈值
     * Key: 异常类型
     * Value: 阈值
     */
    private final Map<String, Float> anomalyThresholds = new HashMap<>();
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     */
    @Autowired
    public AnomalyDetection(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        
        // 初始化异常阈值
        initAnomalyThresholds();
        
        // 启动定期清理任务
        startCleanupTask();
        
        log.info("异常检测系统初始化完成");
    }
    
    /**
     * 初始化异常阈值
     */
    private void initAnomalyThresholds() {
        // 登录异常阈值
        anomalyThresholds.put("login:ip_change", 70.0f);
        anomalyThresholds.put("login:device_change", 60.0f);
        anomalyThresholds.put("login:geo_distance", 80.0f);
        
        // 交易异常阈值
        anomalyThresholds.put("transaction:amount", 75.0f);
        anomalyThresholds.put("transaction:frequency", 65.0f);
        anomalyThresholds.put("transaction:new_recipient", 50.0f);
        
        // 账号异常阈值
        anomalyThresholds.put("account:multi_login", 85.0f);
        anomalyThresholds.put("account:info_change", 60.0f);
        
        // 行为异常阈值
        anomalyThresholds.put("behavior:chat", 70.0f);
        anomalyThresholds.put("behavior:trade", 75.0f);
        anomalyThresholds.put("behavior:movement", 60.0f);
    }
    
    /**
     * 启动定期清理任务
     */
    private void startCleanupTask() {
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    cleanupOldRecords();
                } catch (Exception e) {
                    log.error("清理旧记录失败", e);
                }
            }
        }, 3600000, 3600000); // 每小时执行一次
    }
    
    /**
     * 清理旧记录
     */
    private void cleanupOldRecords() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        
        // 清理IP历史
        userIpHistory.forEach((userId, records) -> {
            records.removeIf(record -> record.getTimestamp().isBefore(cutoff));
        });
        
        // 清理设备历史
        userDeviceHistory.forEach((userId, records) -> {
            records.removeIf(record -> record.getTimestamp().isBefore(cutoff));
        });
        
        // 清理交易历史
        userTransactionHistory.forEach((userId, records) -> {
            records.removeIf(record -> record.getTimestamp().isBefore(cutoff));
        });
        
        // 清理行为历史
        userBehaviorHistory.forEach((key, records) -> {
            records.removeIf(record -> record.getTimestamp().isBefore(cutoff));
        });
        
        log.debug("已清理30天前的历史记录");
    }
    
    /**
     * 检测登录异常
     *
     * @param userId 用户ID
     * @param ip IP地址
     * @param deviceId 设备ID
     * @param userAgent 用户代理
     * @return 异常评分（0-100）
     */
    public float detectLoginAnomaly(String userId, String ip, String deviceId, String userAgent) {
        if (userId == null || ip == null) {
            return 0;
        }
        
        try {
            float anomalyScore = 0;
            
            // 记录IP信息
            IPRecord ipRecord = new IPRecord(userId, ip, null, Instant.now());
            List<IPRecord> ipHistory = userIpHistory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
            
            // 检查IP变更
            if (!ipHistory.isEmpty()) {
                IPRecord lastRecord = ipHistory.get(ipHistory.size() - 1);
                if (!lastRecord.getIp().equals(ip)) {
                    // IP发生变化，评估异常程度
                    float ipChangeScore = calculateIpChangeScore(userId, ip, lastRecord.getIp());
                    anomalyScore = Math.max(anomalyScore, ipChangeScore);
                    
                    if (ipChangeScore > anomalyThresholds.getOrDefault("login:ip_change", 70.0f)) {
                        logAnomaly(userId, "login:ip_change", "IP变更异常", 
                                Map.of("oldIp", lastRecord.getIp(), "newIp", ip));
                        incrementAnomalyCounter(userId, "login:ip_change");
                    }
                }
            }
            
            // 添加当前记录到历史
            ipHistory.add(ipRecord);
            
            // 记录设备信息
            DeviceRecord deviceRecord = new DeviceRecord(userId, deviceId, userAgent, Instant.now());
            List<DeviceRecord> deviceHistory = userDeviceHistory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
            
            // 检查设备变更
            if (!deviceHistory.isEmpty()) {
                DeviceRecord lastRecord = deviceHistory.get(deviceHistory.size() - 1);
                if (!lastRecord.getDeviceId().equals(deviceId)) {
                    // 设备发生变化，评估异常程度
                    float deviceChangeScore = calculateDeviceChangeScore(userId, deviceId, lastRecord.getDeviceId());
                    anomalyScore = Math.max(anomalyScore, deviceChangeScore);
                    
                    if (deviceChangeScore > anomalyThresholds.getOrDefault("login:device_change", 60.0f)) {
                        logAnomaly(userId, "login:device_change", "设备变更异常", 
                                Map.of("oldDevice", lastRecord.getDeviceId(), "newDevice", deviceId));
                        incrementAnomalyCounter(userId, "login:device_change");
                    }
                }
            }
            
            // 添加当前记录到历史
            deviceHistory.add(deviceRecord);
            
            // 检查多设备并发登录
            if (detectConcurrentLogins(userId)) {
                anomalyScore = Math.max(anomalyScore, 85.0f);
                logAnomaly(userId, "account:multi_login", "多设备并发登录", 
                        Map.of("ip", ip, "deviceId", deviceId));
                incrementAnomalyCounter(userId, "account:multi_login");
            }
            
            return anomalyScore;
        } catch (Exception e) {
            log.error("检测登录异常时出错: userId={}", userId, e);
            return 0;
        }
    }
    
    /**
     * 计算IP变更评分
     *
     * @param userId 用户ID
     * @param newIp 新IP
     * @param oldIp 旧IP
     * @return 异常评分
     */
    private float calculateIpChangeScore(String userId, String newIp, String oldIp) {
        // 简化实现，实际项目中应该检查IP地理位置、IP信誉度等
        
        // 检查历史上是否使用过此IP
        List<IPRecord> ipHistory = userIpHistory.get(userId);
        boolean usedBefore = ipHistory.stream()
                .anyMatch(record -> record.getIp().equals(newIp));
        
        if (usedBefore) {
            return 20.0f; // 曾经用过的IP，较低风险
        }
        
        // 简单根据IP地址的差异进行评分
        String[] oldParts = oldIp.split("\\.");
        String[] newParts = newIp.split("\\.");
        
        // 计算IP地址前缀匹配程度
        int matchingParts = 0;
        for (int i = 0; i < Math.min(oldParts.length, newParts.length); i++) {
            if (oldParts[i].equals(newParts[i])) {
                matchingParts++;
            } else {
                break;
            }
        }
        
        switch (matchingParts) {
            case 3: return 30.0f; // 同一子网
            case 2: return 50.0f; // 同一网段
            case 1: return 70.0f; // 同一大类网络
            default: return 85.0f; // 完全不同的网络
        }
    }
    
    /**
     * 计算设备变更评分
     *
     * @param userId 用户ID
     * @param newDeviceId 新设备ID
     * @param oldDeviceId 旧设备ID
     * @return 异常评分
     */
    private float calculateDeviceChangeScore(String userId, String newDeviceId, String oldDeviceId) {
        // 检查历史上是否使用过此设备
        List<DeviceRecord> deviceHistory = userDeviceHistory.get(userId);
        boolean usedBefore = deviceHistory.stream()
                .anyMatch(record -> record.getDeviceId().equals(newDeviceId));
        
        if (usedBefore) {
            return 30.0f; // 曾经用过的设备，较低风险
        }
        
        // 检查设备变更频率
        if (deviceHistory.size() >= 3) {
            long distinctDevices = deviceHistory.stream()
                    .map(DeviceRecord::getDeviceId)
                    .distinct()
                    .count();
            
            // 如果用户在短时间内使用了多个不同的设备，可能是异常行为
            if (distinctDevices >= 3) {
                return 80.0f;
            }
        }
        
        return 60.0f; // 默认评分
    }
    
    /**
     * 检测并发登录
     *
     * @param userId 用户ID
     * @return 是否存在并发登录
     */
    private boolean detectConcurrentLogins(String userId) {
        // 实际项目中应该检查活跃会话，此处简化实现
        return false;
    }
    
    /**
     * 检测交易异常
     *
     * @param userId 用户ID
     * @param amount 交易金额
     * @param recipient 接收方
     * @return 异常评分
     */
    public float detectTransactionAnomaly(String userId, double amount, String recipient) {
        if (userId == null || amount <= 0) {
            return 0;
        }
        
        try {
            float anomalyScore = 0;
            
            // 记录交易信息
            TransactionRecord transaction = new TransactionRecord(
                    userId, amount, recipient, Instant.now());
            List<TransactionRecord> transactions = userTransactionHistory.computeIfAbsent(
                    userId, k -> Collections.synchronizedList(new ArrayList<>()));
            
            // 检查交易金额异常
            float amountScore = calculateAmountAnomalyScore(userId, amount);
            anomalyScore = Math.max(anomalyScore, amountScore);
            
            if (amountScore > anomalyThresholds.getOrDefault("transaction:amount", 75.0f)) {
                logAnomaly(userId, "transaction:amount", "交易金额异常", 
                        Map.of("amount", amount));
                incrementAnomalyCounter(userId, "transaction:amount");
            }
            
            // 检查交易频率异常
            float frequencyScore = calculateTransactionFrequencyScore(userId);
            anomalyScore = Math.max(anomalyScore, frequencyScore);
            
            if (frequencyScore > anomalyThresholds.getOrDefault("transaction:frequency", 65.0f)) {
                logAnomaly(userId, "transaction:frequency", "交易频率异常", 
                        Map.of("count", transactions.size()));
                incrementAnomalyCounter(userId, "transaction:frequency");
            }
            
            // 检查新接收方
            float recipientScore = calculateNewRecipientScore(userId, recipient);
            anomalyScore = Math.max(anomalyScore, recipientScore);
            
            if (recipientScore > anomalyThresholds.getOrDefault("transaction:new_recipient", 50.0f)) {
                logAnomaly(userId, "transaction:new_recipient", "新交易对象", 
                        Map.of("recipient", recipient));
                incrementAnomalyCounter(userId, "transaction:new_recipient");
            }
            
            // 添加当前记录到历史
            transactions.add(transaction);
            
            return anomalyScore;
        } catch (Exception e) {
            log.error("检测交易异常时出错: userId={}", userId, e);
            return 0;
        }
    }
    
    /**
     * 计算金额异常评分
     *
     * @param userId 用户ID
     * @param amount 交易金额
     * @return 异常评分
     */
    private float calculateAmountAnomalyScore(String userId, double amount) {
        List<TransactionRecord> transactions = userTransactionHistory.get(userId);
        if (transactions == null || transactions.isEmpty()) {
            // 没有历史交易记录，无法比较
            return 50.0f;
        }
        
        // 计算用户历史平均交易金额
        double avgAmount = transactions.stream()
                .mapToDouble(TransactionRecord::getAmount)
                .average()
                .orElse(0);
        
        if (avgAmount <= 0) {
            return 50.0f;
        }
        
        // 计算当前交易金额与历史平均值的比例
        double ratio = amount / avgAmount;
        
        if (ratio > 10) {
            return 90.0f; // 大幅超过历史平均
        } else if (ratio > 5) {
            return 75.0f; // 显著超过历史平均
        } else if (ratio > 2) {
            return 50.0f; // 轻微超过历史平均
        }
        
        return 0.0f; // 正常范围
    }
    
    /**
     * 计算交易频率评分
     *
     * @param userId 用户ID
     * @return 异常评分
     */
    private float calculateTransactionFrequencyScore(String userId) {
        List<TransactionRecord> transactions = userTransactionHistory.get(userId);
        if (transactions == null || transactions.isEmpty()) {
            return 0.0f;
        }
        
        // 计算最近1小时的交易次数
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        long recentCount = transactions.stream()
                .filter(tx -> tx.getTimestamp().isAfter(oneHourAgo))
                .count();
        
        if (recentCount > 20) {
            return 90.0f; // 极高频率
        } else if (recentCount > 10) {
            return 70.0f; // 高频率
        } else if (recentCount > 5) {
            return 50.0f; // 稍高频率
        }
        
        return 0.0f; // 正常频率
    }
    
    /**
     * 计算新接收方评分
     *
     * @param userId 用户ID
     * @param recipient 接收方
     * @return 异常评分
     */
    private float calculateNewRecipientScore(String userId, String recipient) {
        List<TransactionRecord> transactions = userTransactionHistory.get(userId);
        if (transactions == null || transactions.isEmpty()) {
            return 50.0f; // 没有历史记录，中等风险
        }
        
        // 检查是否之前交易过的对象
        boolean knownRecipient = transactions.stream()
                .anyMatch(tx -> recipient.equals(tx.getRecipient()));
        
        return knownRecipient ? 0.0f : 50.0f;
    }
    
    /**
     * 检测账号异常
     *
     * @param userId 用户ID
     * @return 异常评分
     */
    public float detectAccountAnomaly(String userId) {
        if (userId == null) {
            return 0;
        }
        
        try {
            float anomalyScore = 0;
            
            // 检查账号多设备登录
            int deviceCount = getDistinctDeviceCount(userId);
            if (deviceCount > 3) {
                float score = Math.min(deviceCount * 10.0f, 90.0f);
                anomalyScore = Math.max(anomalyScore, score);
                
                if (score > anomalyThresholds.getOrDefault("account:multi_login", 85.0f)) {
                    logAnomaly(userId, "account:multi_login", "多设备登录", 
                            Map.of("deviceCount", deviceCount));
                    incrementAnomalyCounter(userId, "account:multi_login");
                }
            }
            
            // 检查IP变更频率
            int ipChangeCount = getIpChangeCount(userId);
            if (ipChangeCount > 2) {
                float score = Math.min(ipChangeCount * 15.0f, 90.0f);
                anomalyScore = Math.max(anomalyScore, score);
                
                if (score > anomalyThresholds.getOrDefault("login:ip_change", 70.0f)) {
                    logAnomaly(userId, "login:ip_change", "频繁IP变更", 
                            Map.of("changeCount", ipChangeCount));
                    incrementAnomalyCounter(userId, "login:ip_change");
                }
            }
            
            return anomalyScore;
        } catch (Exception e) {
            log.error("检测账号异常时出错: userId={}", userId, e);
            return 0;
        }
    }
    
    /**
     * 获取用户使用的不同设备数
     *
     * @param userId 用户ID
     * @return 设备数
     */
    private int getDistinctDeviceCount(String userId) {
        List<DeviceRecord> devices = userDeviceHistory.get(userId);
        if (devices == null || devices.isEmpty()) {
            return 0;
        }
        
        // 获取最近24小时使用的不同设备
        Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));
        return (int) devices.stream()
                .filter(dev -> dev.getTimestamp().isAfter(oneDayAgo))
                .map(DeviceRecord::getDeviceId)
                .distinct()
                .count();
    }
    
    /**
     * 获取IP变更次数
     *
     * @param userId 用户ID
     * @return IP变更次数
     */
    private int getIpChangeCount(String userId) {
        List<IPRecord> ipHistory = userIpHistory.get(userId);
        if (ipHistory == null || ipHistory.size() <= 1) {
            return 0;
        }
        
        // 获取最近24小时内的IP变更次数
        Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));
        List<IPRecord> recentHistory = ipHistory.stream()
                .filter(ip -> ip.getTimestamp().isAfter(oneDayAgo))
                .collect(Collectors.toList());
        
        if (recentHistory.size() <= 1) {
            return 0;
        }
        
        int changes = 0;
        String lastIp = null;
        
        for (IPRecord record : recentHistory) {
            if (lastIp != null && !lastIp.equals(record.getIp())) {
                changes++;
            }
            lastIp = record.getIp();
        }
        
        return changes;
    }
    
    /**
     * 检测行为异常
     *
     * @param userId 用户ID
     * @param behaviorType 行为类型
     * @param context 上下文数据
     * @return 异常评分
     */
    public float detectBehaviorAnomaly(String userId, String behaviorType, Map<String, Object> context) {
        if (userId == null || behaviorType == null) {
            return 0;
        }
        
        try {
            String behaviorKey = userId + ":" + behaviorType;
            
            // 记录行为
            BehaviorRecord record = new BehaviorRecord(
                    userId, behaviorType, new HashMap<>(context), Instant.now());
            List<BehaviorRecord> behaviors = userBehaviorHistory.computeIfAbsent(
                    behaviorKey, k -> Collections.synchronizedList(new ArrayList<>()));
            behaviors.add(record);
            
            // 根据不同的行为类型执行特定的检测逻辑
            float anomalyScore = 0;
            switch (behaviorType) {
                case "chat":
                    anomalyScore = detectChatAnomaly(userId, context);
                    break;
                case "trade":
                    anomalyScore = detectTradeAnomaly(userId, context);
                    break;
                case "movement":
                    anomalyScore = detectMovementAnomaly(userId, context);
                    break;
                default:
                    anomalyScore = detectGenericBehaviorAnomaly(userId, behaviorType, context);
            }
            
            // 如果异常评分超过阈值，记录异常
            if (anomalyScore > anomalyThresholds.getOrDefault("behavior:" + behaviorType, 70.0f)) {
                logAnomaly(userId, "behavior:" + behaviorType, behaviorType + "行为异常", context);
                incrementAnomalyCounter(userId, "behavior:" + behaviorType);
            }
            
            return anomalyScore;
        } catch (Exception e) {
            log.error("检测行为异常时出错: userId={}, behaviorType={}", userId, behaviorType, e);
            return 0;
        }
    }
    
    /**
     * 检测聊天异常
     *
     * @param userId 用户ID
     * @param context 上下文数据
     * @return 异常评分
     */
    private float detectChatAnomaly(String userId, Map<String, Object> context) {
        String content = (String) context.get("content");
        if (content == null) {
            return 0;
        }
        
        // 检查敏感词
        if (containsSensitiveWords(content)) {
            return 80.0f;
        }
        
        // 检查消息频率
        String behaviorKey = userId + ":chat";
        List<BehaviorRecord> chatHistory = userBehaviorHistory.get(behaviorKey);
        if (chatHistory != null && chatHistory.size() > 1) {
            int messageCount = 0;
            Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
            
            for (BehaviorRecord record : chatHistory) {
                if (record.getTimestamp().isAfter(oneMinuteAgo)) {
                    messageCount++;
                }
            }
            
            // 每分钟超过20条消息可能是刷屏
            if (messageCount > 20) {
                return 70.0f;
            }
        }
        
        return 0;
    }
    
    /**
     * 检测交易异常
     *
     * @param userId 用户ID
     * @param context 上下文数据
     * @return 异常评分
     */
    private float detectTradeAnomaly(String userId, Map<String, Object> context) {
        // 简化实现，实际项目应根据游戏内交易的具体规则检测
        return 0;
    }
    
    /**
     * 检测移动异常
     *
     * @param userId 用户ID
     * @param context 上下文数据
     * @return 异常评分
     */
    private float detectMovementAnomaly(String userId, Map<String, Object> context) {
        // 检查速度异常
        if (context.containsKey("speed") && context.containsKey("maxSpeed")) {
            double speed = ((Number) context.get("speed")).doubleValue();
            double maxSpeed = ((Number) context.get("maxSpeed")).doubleValue();
            
            if (speed > maxSpeed * 1.5) {
                return 80.0f;
            } else if (speed > maxSpeed * 1.2) {
                return 50.0f;
            }
        }
        
        return 0;
    }
    
    /**
     * 检测通用行为异常
     *
     * @param userId 用户ID
     * @param behaviorType 行为类型
     * @param context 上下文数据
     * @return 异常评分
     */
    private float detectGenericBehaviorAnomaly(String userId, String behaviorType, Map<String, Object> context) {
        // 检查行为频率
        String behaviorKey = userId + ":" + behaviorType;
        List<BehaviorRecord> behaviorHistory = userBehaviorHistory.get(behaviorKey);
        if (behaviorHistory != null && behaviorHistory.size() > 1) {
            int count = 0;
            Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
            
            for (BehaviorRecord record : behaviorHistory) {
                if (record.getTimestamp().isAfter(oneMinuteAgo)) {
                    count++;
                }
            }
            
            // 频率异常检测（简化实现，实际应基于行为类型定义合理的阈值）
            if (count > 100) {
                return 80.0f;
            } else if (count > 60) {
                return 50.0f;
            } else if (count > 30) {
                return 30.0f;
            }
        }
        
        return 0;
    }
    
    /**
     * 检查敏感词（简化实现）
     *
     * @param content 内容
     * @return 是否包含敏感词
     */
    private boolean containsSensitiveWords(String content) {
        // 实际项目中应该使用敏感词库或正则表达式进行检测
        String[] sensitiveWords = {"hack", "cheat", "bug", "exploit"};
        String lowerContent = content.toLowerCase();
        
        for (String word : sensitiveWords) {
            if (lowerContent.contains(word)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 记录异常
     *
     * @param userId 用户ID
     * @param anomalyType 异常类型
     * @param description 描述
     * @param data 数据
     */
    @Async
    public void logAnomaly(String userId, String anomalyType, String description, Map<String, Object> data) {
        log.warn("检测到异常: userId={}, type={}, description={}, data={}",
                userId, anomalyType, description, data);
        
        // 实际项目中应该将异常记录保存到数据库或发送到消息队列
    }
    
    /**
     * 增加异常计数
     *
     * @param userId 用户ID
     * @param anomalyType 异常类型
     */
    private void incrementAnomalyCounter(String userId, String anomalyType) {
        String key = userId + ":" + anomalyType;
        anomalyCounters.computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
    }
    
    /**
     * 获取异常计数
     *
     * @param userId 用户ID
     * @param anomalyType 异常类型
     * @return 异常计数
     */
    public int getAnomalyCount(String userId, String anomalyType) {
        String key = userId + ":" + anomalyType;
        AtomicInteger counter = anomalyCounters.get(key);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 重置异常计数
     *
     * @param userId 用户ID
     * @param anomalyType 异常类型
     */
    public void resetAnomalyCount(String userId, String anomalyType) {
        String key = userId + ":" + anomalyType;
        AtomicInteger counter = anomalyCounters.get(key);
        if (counter != null) {
            counter.set(0);
        }
    }
    
    /**
     * IP记录
     */
    private static class IPRecord {
        private final String userId;
        private final String ip;
        private final String geoLocation;
        private final Instant timestamp;
        
        public IPRecord(String userId, String ip, String geoLocation, Instant timestamp) {
            this.userId = userId;
            this.ip = ip;
            this.geoLocation = geoLocation;
            this.timestamp = timestamp;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getIp() {
            return ip;
        }
        
        public String getGeoLocation() {
            return geoLocation;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 设备记录
     */
    private static class DeviceRecord {
        private final String userId;
        private final String deviceId;
        private final String userAgent;
        private final Instant timestamp;
        
        public DeviceRecord(String userId, String deviceId, String userAgent, Instant timestamp) {
            this.userId = userId;
            this.deviceId = deviceId;
            this.userAgent = userAgent;
            this.timestamp = timestamp;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getDeviceId() {
            return deviceId;
        }
        
        public String getUserAgent() {
            return userAgent;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 交易记录
     */
    private static class TransactionRecord {
        private final String userId;
        private final double amount;
        private final String recipient;
        private final Instant timestamp;
        
        public TransactionRecord(String userId, double amount, String recipient, Instant timestamp) {
            this.userId = userId;
            this.amount = amount;
            this.recipient = recipient;
            this.timestamp = timestamp;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public double getAmount() {
            return amount;
        }
        
        public String getRecipient() {
            return recipient;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 行为记录
     */
    private static class BehaviorRecord {
        private final String userId;
        private final String behaviorType;
        private final Map<String, Object> data;
        private final Instant timestamp;
        
        public BehaviorRecord(String userId, String behaviorType, Map<String, Object> data, Instant timestamp) {
            this.userId = userId;
            this.behaviorType = behaviorType;
            this.data = data;
            this.timestamp = timestamp;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getBehaviorType() {
            return behaviorType;
        }
        
        public Map<String, Object> getData() {
            return data;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
}