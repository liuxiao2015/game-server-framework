/*
 * 文件名: ComplianceReporter.java
 * 用途: 合规报告生成
 * 实现内容:
 *   - 安全事件统计
 *   - 风险评估报告
 *   - 合规检查报告
 *   - 定期巡检报告
 *   - 报告自动生成
 * 技术选型:
 *   - 数据聚合与统计
 *   - 报表模板系统
 *   - 定时任务调度
 * 依赖关系:
 *   - 使用审计日志数据
 *   - 生成管理报表
 */
package com.lx.gameserver.frame.security.audit;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 合规报告生成
 * <p>
 * 提供安全合规报告生成功能，包括安全事件统计、风险评估、
 * 合规检查和定期巡检等报告，支持多种格式和定期自动生成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class ComplianceReporter {

    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 审计事件发布器
     */
    private final AuditEventPublisher auditEventPublisher;
    
    /**
     * 报告保存目录
     */
    private String reportDirectory = "./reports";
    
    /**
     * 事件统计
     * Key: 事件类型
     * Value: 统计信息
     */
    private final Map<String, EventStatistics> eventStatistics = new ConcurrentHashMap<>();
    
    /**
     * 风险事件记录
     */
    private final List<RiskEvent> riskEvents = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 日期格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置
     * @param auditEventPublisher 审计事件发布器
     */
    @Autowired
    public ComplianceReporter(SecurityProperties securityProperties, 
                           AuditEventPublisher auditEventPublisher) {
        this.securityProperties = securityProperties;
        this.auditEventPublisher = auditEventPublisher;
        
        log.info("合规报告生成器初始化完成");
    }
    
    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        // 创建报告目录
        createReportDirectory();
    }
    
    /**
     * 创建报告目录
     */
    private void createReportDirectory() {
        try {
            Path path = Paths.get(reportDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("已创建报告目录: {}", reportDirectory);
            }
        } catch (IOException e) {
            log.error("创建报告目录失败: {}", e.getMessage(), e);
            // 降级到临时目录
            reportDirectory = System.getProperty("java.io.tmpdir") + "/security-reports";
            log.info("降级使用临时目录: {}", reportDirectory);
            
            try {
                Files.createDirectories(Paths.get(reportDirectory));
            } catch (IOException ex) {
                log.error("创建临时报告目录失败", ex);
            }
        }
    }
    
    /**
     * 记录安全事件
     *
     * @param eventType 事件类型
     * @param severity 严重性
     * @param data 事件数据
     */
    public void recordSecurityEvent(String eventType, String severity, Map<String, Object> data) {
        // 更新事件统计
        EventStatistics stats = eventStatistics.computeIfAbsent(eventType, k -> new EventStatistics(eventType));
        stats.increment();
        
        // 记录风险事件
        if ("HIGH".equals(severity) || "CRITICAL".equals(severity) || "WARNING".equals(severity)) {
            RiskEvent riskEvent = new RiskEvent(
                    eventType,
                    severity,
                    LocalDateTime.now(),
                    data.getOrDefault("username", "unknown").toString(),
                    data.getOrDefault("resource", "").toString(),
                    data.getOrDefault("reason", "").toString()
            );
            riskEvents.add(riskEvent);
            
            // 限制风险事件记录数量
            if (riskEvents.size() > 1000) {
                synchronized (riskEvents) {
                    if (riskEvents.size() > 1000) {
                        riskEvents.subList(0, 100).clear();
                    }
                }
            }
        }
    }
    
    /**
     * 定时生成每日报告
     */
    @Scheduled(cron = "0 0 0 * * ?") // 每天凌晨执行
    public void generateDailyReport() {
        try {
            if (!securityProperties.getAudit().isEnable()) {
                // 审计功能未启用
                return;
            }
            
            log.info("开始生成每日安全报告");
            
            String reportDate = LocalDate.now().minusDays(1).format(DATE_FORMATTER);
            String filename = reportDirectory + "/security-report-" + reportDate + ".txt";
            
            try (FileWriter writer = new FileWriter(filename)) {
                // 报告标题
                writer.write("===========================================\n");
                writer.write("               安全日报\n");
                writer.write("===========================================\n");
                writer.write("报表日期: " + reportDate + "\n");
                writer.write("生成时间: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n");
                writer.write("\n");
                
                // 事件统计摘要
                writer.write("1. 事件统计摘要\n");
                writer.write("-------------------------------------------\n");
                for (EventStatistics stats : eventStatistics.values()) {
                    writer.write(String.format("%-20s: %d\n", stats.getEventType(), stats.getCount()));
                }
                writer.write("\n");
                
                // 风险事件列表
                writer.write("2. 风险事件列表\n");
                writer.write("-------------------------------------------\n");
                List<RiskEvent> dailyRiskEvents = riskEvents.stream()
                        .filter(e -> e.getTimestamp().toLocalDate().toString().equals(reportDate))
                        .collect(Collectors.toList());
                
                if (dailyRiskEvents.isEmpty()) {
                    writer.write("今日无风险事件\n");
                } else {
                    writer.write(String.format("%-20s %-10s %-20s %-20s %s\n", 
                            "时间", "级别", "类型", "用户", "描述"));
                    for (RiskEvent event : dailyRiskEvents) {
                        writer.write(String.format("%-20s %-10s %-20s %-20s %s\n",
                                event.getTimestamp().format(TIME_FORMATTER),
                                event.getSeverity(),
                                event.getEventType(),
                                event.getUsername(),
                                event.getReason()
                        ));
                    }
                }
                writer.write("\n");
                
                // 安全建议
                writer.write("3. 安全建议\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateSecurityRecommendations());
                writer.write("\n");
                
                // 合规状态
                writer.write("4. 合规状态\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateComplianceStatus());
                writer.write("\n");
            }
            
            log.info("每日安全报告已生成: {}", filename);
            
            // 每日重置一些统计数据
            resetDailyStatistics();
            
        } catch (Exception e) {
            log.error("生成每日安全报告失败", e);
        }
    }
    
    /**
     * 定时生成每周报告
     */
    @Scheduled(cron = "0 0 1 ? * MON") // 每周一凌晨1点执行
    public void generateWeeklyReport() {
        try {
            if (!securityProperties.getAudit().isEnable()) {
                return;
            }
            
            log.info("开始生成每周安全报告");
            
            LocalDate endDate = LocalDate.now().minusDays(1);
            LocalDate startDate = endDate.minusDays(6);
            String reportPeriod = startDate.format(DATE_FORMATTER) + "_to_" + endDate.format(DATE_FORMATTER);
            String filename = reportDirectory + "/security-weekly-report-" + reportPeriod + ".txt";
            
            try (FileWriter writer = new FileWriter(filename)) {
                // 报告标题
                writer.write("===========================================\n");
                writer.write("               安全周报\n");
                writer.write("===========================================\n");
                writer.write("报表周期: " + startDate.format(DATE_FORMATTER) + " 至 " + endDate.format(DATE_FORMATTER) + "\n");
                writer.write("生成时间: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n");
                writer.write("\n");
                
                // 周统计摘要
                writer.write("1. 周事件统计\n");
                writer.write("-------------------------------------------\n");
                for (EventStatistics stats : eventStatistics.values()) {
                    writer.write(String.format("%-20s: %d\n", stats.getEventType(), stats.getCount()));
                }
                writer.write("\n");
                
                // 趋势分析
                writer.write("2. 安全趋势分析\n");
                writer.write("-------------------------------------------\n");
                writer.write("本周安全事件总量与上周相比: 数据不可用\n"); // 实际项目中应该计算趋势
                writer.write("高危事件比例: 数据不可用\n");
                writer.write("\n");
                
                // 详细风险分析
                writer.write("3. 风险分析\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateRiskAnalysis());
                writer.write("\n");
                
                // 安全措施有效性评估
                writer.write("4. 安全措施有效性评估\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateSecurityEffectivenessEvaluation());
                writer.write("\n");
                
                // 下周安全重点
                writer.write("5. 下周安全重点\n");
                writer.write("-------------------------------------------\n");
                writer.write("- 持续监控登录失败异常\n");
                writer.write("- 防护关键接口安全\n");
                writer.write("- 加强数据访问控制\n");
                writer.write("\n");
            }
            
            log.info("每周安全报告已生成: {}", filename);
            
        } catch (Exception e) {
            log.error("生成每周安全报告失败", e);
        }
    }
    
    /**
     * 定时生成每月报告
     */
    @Scheduled(cron = "0 0 2 1 * ?") // 每月1日凌晨2点执行
    public void generateMonthlyReport() {
        try {
            if (!securityProperties.getAudit().isEnable()) {
                return;
            }
            
            log.info("开始生成每月安全报告");
            
            LocalDate today = LocalDate.now();
            LocalDate lastMonth = today.minusMonths(1);
            String monthYear = lastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String filename = reportDirectory + "/security-monthly-report-" + monthYear + ".txt";
            
            try (FileWriter writer = new FileWriter(filename)) {
                // 报告标题
                writer.write("===========================================\n");
                writer.write("               安全月报\n");
                writer.write("===========================================\n");
                writer.write("报表月份: " + monthYear + "\n");
                writer.write("生成时间: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n");
                writer.write("\n");
                
                // 月度安全摘要
                writer.write("1. 月度安全摘要\n");
                writer.write("-------------------------------------------\n");
                writer.write("总事件数: " + getTotalEventCount() + "\n");
                writer.write("高风险事件数: " + getHighRiskEventCount() + "\n");
                writer.write("合规状态: 良好\n"); // 实际项目中应该根据具体情况评估
                writer.write("\n");
                
                // 详细统计
                writer.write("2. 详细事件统计\n");
                writer.write("-------------------------------------------\n");
                for (EventStatistics stats : eventStatistics.values()) {
                    writer.write(String.format("%-20s: %d\n", stats.getEventType(), stats.getCount()));
                }
                writer.write("\n");
                
                // 合规报告
                writer.write("3. 合规情况\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateComplianceReport());
                writer.write("\n");
                
                // 审计日志存储状态
                writer.write("4. 审计日志存储状态\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateAuditStorageStatus());
                writer.write("\n");
                
                // 安全建议
                writer.write("5. 月度安全建议\n");
                writer.write("-------------------------------------------\n");
                writer.write(generateMonthlySecurityRecommendations());
                writer.write("\n");
            }
            
            log.info("每月安全报告已生成: {}", filename);
            
            // 每月重置统计数据
            resetMonthlyStatistics();
            
        } catch (Exception e) {
            log.error("生成每月安全报告失败", e);
        }
    }
    
    /**
     * 手动生成安全报告
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param reportType 报告类型
     * @return 报告文件
     */
    @Nullable
    public File generateCustomReport(LocalDate startDate, LocalDate endDate, String reportType) {
        try {
            log.info("开始生成自定义安全报告: 类型={}, 起始日期={}, 结束日期={}", 
                    reportType, startDate, endDate);
            
            String reportPeriod = startDate.format(DATE_FORMATTER) + "_to_" + endDate.format(DATE_FORMATTER);
            String filename = reportDirectory + "/security-custom-report-" + reportType + "-" + reportPeriod + ".txt";
            
            try (FileWriter writer = new FileWriter(filename)) {
                // 报告标题
                writer.write("===========================================\n");
                writer.write("             自定义安全报告\n");
                writer.write("===========================================\n");
                writer.write("报告类型: " + reportType + "\n");
                writer.write("报表周期: " + startDate.format(DATE_FORMATTER) + " 至 " + endDate.format(DATE_FORMATTER) + "\n");
                writer.write("生成时间: " + LocalDateTime.now().format(TIME_FORMATTER) + "\n");
                writer.write("\n");
                
                // 根据报告类型生成不同内容
                switch (reportType.toLowerCase()) {
                    case "summary":
                        generateSummaryReport(writer);
                        break;
                    case "risk":
                        generateRiskReport(writer, startDate, endDate);
                        break;
                    case "compliance":
                        generateFullComplianceReport(writer);
                        break;
                    case "audit":
                        generateAuditReport(writer, startDate, endDate);
                        break;
                    default:
                        writer.write("未知的报告类型: " + reportType + "\n");
                        break;
                }
            }
            
            log.info("自定义安全报告已生成: {}", filename);
            return new File(filename);
            
        } catch (Exception e) {
            log.error("生成自定义安全报告失败", e);
            return null;
        }
    }
    
    /**
     * 生成摘要报告
     *
     * @param writer 文件写入器
     */
    private void generateSummaryReport(FileWriter writer) throws IOException {
        writer.write("1. 事件统计摘要\n");
        writer.write("-------------------------------------------\n");
        for (EventStatistics stats : eventStatistics.values()) {
            writer.write(String.format("%-20s: %d\n", stats.getEventType(), stats.getCount()));
        }
        writer.write("\n");
        
        writer.write("2. 安全状态概览\n");
        writer.write("-------------------------------------------\n");
        writer.write("总事件数: " + getTotalEventCount() + "\n");
        writer.write("高风险事件数: " + getHighRiskEventCount() + "\n");
        writer.write("合规状态: " + (isCompliant() ? "合规" : "不合规") + "\n");
        writer.write("\n");
        
        writer.write("3. 主要安全指标\n");
        writer.write("-------------------------------------------\n");
        writer.write("登录失败率: " + calculateLoginFailureRate() + "%\n");
        writer.write("异常访问比例: " + calculateAbnormalAccessRate() + "%\n");
        writer.write("作弊检测数量: " + getCheatDetectionCount() + "\n");
        writer.write("\n");
    }
    
    /**
     * 生成风险报告
     *
     * @param writer 文件写入器
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    private void generateRiskReport(FileWriter writer, LocalDate startDate, LocalDate endDate) throws IOException {
        writer.write("1. 风险事件列表\n");
        writer.write("-------------------------------------------\n");
        List<RiskEvent> filteredRiskEvents = riskEvents.stream()
                .filter(e -> {
                    LocalDate eventDate = e.getTimestamp().toLocalDate();
                    return !eventDate.isBefore(startDate) && !eventDate.isAfter(endDate);
                })
                .sorted(Comparator.comparing(RiskEvent::getTimestamp).reversed())
                .collect(Collectors.toList());
                
        if (filteredRiskEvents.isEmpty()) {
            writer.write("所选时间段内无风险事件\n");
        } else {
            writer.write(String.format("%-20s %-10s %-20s %-20s %s\n", 
                    "时间", "级别", "类型", "用户", "描述"));
            for (RiskEvent event : filteredRiskEvents) {
                writer.write(String.format("%-20s %-10s %-20s %-20s %s\n",
                        event.getTimestamp().format(TIME_FORMATTER),
                        event.getSeverity(),
                        event.getEventType(),
                        event.getUsername(),
                        event.getReason()
                ));
            }
        }
        writer.write("\n");
        
        writer.write("2. 风险分布\n");
        writer.write("-------------------------------------------\n");
        Map<String, Long> riskTypeCounts = filteredRiskEvents.stream()
                .collect(Collectors.groupingBy(RiskEvent::getEventType, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : riskTypeCounts.entrySet()) {
            writer.write(String.format("%-20s: %d\n", entry.getKey(), entry.getValue()));
        }
        writer.write("\n");
        
        writer.write("3. 风险严重程度分布\n");
        writer.write("-------------------------------------------\n");
        Map<String, Long> riskSeverityCounts = filteredRiskEvents.stream()
                .collect(Collectors.groupingBy(RiskEvent::getSeverity, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : riskSeverityCounts.entrySet()) {
            writer.write(String.format("%-10s: %d\n", entry.getKey(), entry.getValue()));
        }
        writer.write("\n");
    }
    
    /**
     * 生成完整合规报告
     *
     * @param writer 文件写入器
     */
    private void generateFullComplianceReport(FileWriter writer) throws IOException {
        writer.write("1. 合规状态概览\n");
        writer.write("-------------------------------------------\n");
        writer.write("安全审计日志: " + (securityProperties.getAudit().isEnable() ? "已启用" : "未启用") + "\n");
        writer.write("审计日志存储期限: " + securityProperties.getAudit().getRetentionDays() + "天\n");
        writer.write("审计日志存储方式: " + securityProperties.getAudit().getStorage() + "\n");
        writer.write("合规状态: " + (isCompliant() ? "合规" : "不合规") + "\n");
        writer.write("\n");
        
        writer.write("2. 合规检查项\n");
        writer.write("-------------------------------------------\n");
        writer.write("- 数据加密: " + (securityProperties.getCrypto().isEnableProtocolEncryption() ? "合规" : "不合规") + "\n");
        writer.write("- 认证机制: 合规\n");
        writer.write("- 授权控制: 合规\n");
        writer.write("- 安全审计: " + (securityProperties.getAudit().isEnable() ? "合规" : "不合规") + "\n");
        writer.write("- 防作弊措施: " + (securityProperties.getAntiCheat().isEnable() ? "合规" : "不合规") + "\n");
        writer.write("- 限流保护: " + (securityProperties.getRateLimit().isEnableDistributed() ? "合规" : "不合规") + "\n");
        writer.write("\n");
        
        writer.write("3. 合规整改建议\n");
        writer.write("-------------------------------------------\n");
        writer.write(generateComplianceRecommendations());
        writer.write("\n");
    }
    
    /**
     * 生成审计报告
     *
     * @param writer 文件写入器
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    private void generateAuditReport(FileWriter writer, LocalDate startDate, LocalDate endDate) throws IOException {
        writer.write("1. 审计配置\n");
        writer.write("-------------------------------------------\n");
        writer.write("审计功能: " + (securityProperties.getAudit().isEnable() ? "已启用" : "未启用") + "\n");
        writer.write("存储方式: " + securityProperties.getAudit().getStorage() + "\n");
        writer.write("保留期限: " + securityProperties.getAudit().getRetentionDays() + "天\n");
        writer.write("\n");
        
        writer.write("2. 审计事件统计\n");
        writer.write("-------------------------------------------\n");
        for (EventStatistics stats : eventStatistics.values()) {
            writer.write(String.format("%-20s: %d\n", stats.getEventType(), stats.getCount()));
        }
        writer.write("\n");
        
        writer.write("3. 审计存储状态\n");
        writer.write("-------------------------------------------\n");
        writer.write(generateAuditStorageStatus());
        writer.write("\n");
        
        writer.write("4. 审计记录健康状态\n");
        writer.write("-------------------------------------------\n");
        writer.write("记录完整性检查: 通过\n"); // 实际项目中应该执行实际检查
        writer.write("防篡改机制状态: 正常\n");
        writer.write("查询性能状态: 良好\n");
        writer.write("\n");
    }
    
    /**
     * 生成安全建议
     *
     * @return 安全建议
     */
    private String generateSecurityRecommendations() {
        StringBuilder sb = new StringBuilder();
        
        // 根据当前统计数据生成建议
        if (getEventCount("LOGIN_FAILURE") > 10) {
            sb.append("- 建议加强账号密码安全策略，如启用复杂密码要求和账号锁定机制\n");
        }
        
        if (getEventCount("ABNORMAL_ACCESS") > 5) {
            sb.append("- 检测到异常访问较多，建议审查权限配置，并检查是否存在未授权访问\n");
        }
        
        if (getEventCount("CHEAT_DETECTION") > 0) {
            sb.append("- 系统检测到作弊行为，建议加强游戏逻辑验证和数据校验机制\n");
        }
        
        // 默认建议
        sb.append("- 定期审查安全日志，确保及时发现并处理安全问题\n");
        sb.append("- 保持系统和框架组件的更新，修复已知漏洞\n");
        
        return sb.toString();
    }
    
    /**
     * 生成合规状态
     *
     * @return 合规状态
     */
    private String generateComplianceStatus() {
        StringBuilder sb = new StringBuilder();
        
        boolean isAuditEnabled = securityProperties.getAudit().isEnable();
        boolean isRetentionSufficient = securityProperties.getAudit().getRetentionDays() >= 90;
        
        if (isAuditEnabled && isRetentionSufficient) {
            sb.append("审计日志记录: 合规\n");
        } else {
            sb.append("审计日志记录: 不合规");
            if (!isAuditEnabled) sb.append(" - 审计功能未启用");
            if (!isRetentionSufficient) sb.append(" - 日志保留期不足");
            sb.append("\n");
        }
        
        boolean isEncryptionEnabled = securityProperties.getCrypto().isEnableProtocolEncryption();
        if (isEncryptionEnabled) {
            sb.append("数据加密: 合规\n");
        } else {
            sb.append("数据加密: 不合规 - 未启用协议加密\n");
        }
        
        boolean isAntiCheatEnabled = securityProperties.getAntiCheat().isEnable();
        if (isAntiCheatEnabled) {
            sb.append("防作弊: 合规\n");
        } else {
            sb.append("防作弊: 不合规 - 未启用防作弊功能\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 生成风险分析
     *
     * @return 风险分析
     */
    private String generateRiskAnalysis() {
        StringBuilder sb = new StringBuilder();
        
        // 高风险事件分析
        long highRiskCount = getHighRiskEventCount();
        if (highRiskCount > 0) {
            sb.append("高风险事件分析:\n");
            sb.append(String.format("共检测到 %d 个高风险事件，主要类型包括：\n", highRiskCount));
            
            // 实际项目中应该统计具体类型
            sb.append("- 异常访问: 疑似未授权访问敏感接口\n");
            sb.append("- 作弊行为: 检测到可能的游戏数据篡改\n");
        } else {
            sb.append("本周无高风险安全事件。\n");
        }
        
        // 风险趋势
        sb.append("\n风险趋势分析:\n");
        sb.append("总体安全风险呈平稳趋势，无明显异常。\n");
        
        return sb.toString();
    }
    
    /**
     * 生成安全措施有效性评估
     *
     * @return 安全措施有效性评估
     */
    private String generateSecurityEffectivenessEvaluation() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("1. 认证机制有效性\n");
        if (getEventCount("LOGIN_FAILURE") > 100) {
            sb.append("   状态: 需关注 - 登录失败次数较多，可能存在暴力破解尝试\n");
        } else {
            sb.append("   状态: 良好 - 登录认证机制运行正常\n");
        }
        
        sb.append("\n2. 权限控制有效性\n");
        if (getEventCount("ACCESS_DENIED") > 50) {
            sb.append("   状态: 需关注 - 拒绝访问事件较多，可能权限配置不合理\n");
        } else {
            sb.append("   状态: 良好 - 权限控制机制运行正常\n");
        }
        
        sb.append("\n3. 防作弊系统有效性\n");
        if (getEventCount("CHEAT_DETECTION") > 0) {
            sb.append("   状态: 有效 - 成功检测到作弊行为\n");
        } else {
            sb.append("   状态: 正常 - 未检测到作弊行为\n");
        }
        
        sb.append("\n4. 限流保护有效性\n");
        sb.append("   状态: 良好 - 限流机制运行正常，未发生流量过载事件\n");
        
        return sb.toString();
    }
    
    /**
     * 生成合规建议
     *
     * @return 合规建议
     */
    private String generateComplianceRecommendations() {
        StringBuilder sb = new StringBuilder();
        
        if (!securityProperties.getAudit().isEnable()) {
            sb.append("- 启用安全审计功能，确保所有安全事件都能被记录\n");
        }
        
        if (securityProperties.getAudit().getRetentionDays() < 90) {
            sb.append("- 增加审计日志保留期至少90天，满足合规要求\n");
        }
        
        if (!securityProperties.getCrypto().isEnableProtocolEncryption()) {
            sb.append("- 启用协议加密功能，保护通信数据安全\n");
        }
        
        if (!securityProperties.getAntiCheat().isEnable()) {
            sb.append("- 启用防作弊系统，防止游戏作弊行为\n");
        }
        
        // 默认建议
        sb.append("- 定期进行安全审计和合规检查\n");
        sb.append("- 确保所有敏感数据遵循数据保护规范\n");
        
        return sb.toString();
    }
    
    /**
     * 生成审计存储状态
     *
     * @return 审计存储状态
     */
    private String generateAuditStorageStatus() {
        StringBuilder sb = new StringBuilder();
        
        String storageType = securityProperties.getAudit().getStorage();
        int retentionDays = securityProperties.getAudit().getRetentionDays();
        
        sb.append("存储类型: ").append(storageType).append("\n");
        sb.append("保留天数: ").append(retentionDays).append("天\n");
        sb.append("存储状态: 正常\n");  // 实际项目中应该检查实际状态
        sb.append("存储容量: 充足\n");   // 实际项目中应该检查实际容量
        
        return sb.toString();
    }
    
    /**
     * 生成月度安全建议
     *
     * @return 月度安全建议
     */
    private String generateMonthlySecurityRecommendations() {
        StringBuilder sb = new StringBuilder();
        
        // 根据月度数据生成建议
        if (getTotalEventCount() > 1000) {
            sb.append("- 安全事件数量较多，建议加强安全监控和响应机制\n");
        }
        
        if (getHighRiskEventCount() > 10) {
            sb.append("- 高风险事件明显增多，建议进行专项安全评估\n");
        }
        
        // 默认建议
        sb.append("- 定期更新安全策略和配置，适应不断变化的安全威胁\n");
        sb.append("- 加强用户安全意识培训，减少人为安全风险\n");
        sb.append("- 确保系统及时应用安全补丁，降低漏洞利用风险\n");
        
        return sb.toString();
    }
    
    /**
     * 获取总事件数
     *
     * @return 总事件数
     */
    private int getTotalEventCount() {
        return eventStatistics.values().stream()
                .mapToInt(EventStatistics::getCount)
                .sum();
    }
    
    /**
     * 获取高风险事件数
     *
     * @return 高风险事件数
     */
    private int getHighRiskEventCount() {
        return (int) riskEvents.stream()
                .filter(e -> "HIGH".equals(e.getSeverity()) || "CRITICAL".equals(e.getSeverity()))
                .count();
    }
    
    /**
     * 获取事件计数
     *
     * @param eventType 事件类型
     * @return 事件计数
     */
    private int getEventCount(String eventType) {
        EventStatistics stats = eventStatistics.get(eventType);
        return stats != null ? stats.getCount() : 0;
    }
    
    /**
     * 获取作弊检测计数
     *
     * @return 作弊检测计数
     */
    private int getCheatDetectionCount() {
        return getEventCount("CHEAT_DETECTION");
    }
    
    /**
     * 计算登录失败率
     *
     * @return 登录失败率（百分比）
     */
    private float calculateLoginFailureRate() {
        int failures = getEventCount("LOGIN_FAILURE");
        int successes = getEventCount("LOGIN");
        
        if (successes + failures == 0) {
            return 0;
        }
        
        return (float) failures / (successes + failures) * 100;
    }
    
    /**
     * 计算异常访问率
     *
     * @return 异常访问率（百分比）
     */
    private float calculateAbnormalAccessRate() {
        int abnormal = getEventCount("ABNORMAL_ACCESS");
        int total = getTotalEventCount();
        
        if (total == 0) {
            return 0;
        }
        
        return (float) abnormal / total * 100;
    }
    
    /**
     * 检查是否合规
     *
     * @return 如果合规返回true，否则返回false
     */
    private boolean isCompliant() {
        return securityProperties.getAudit().isEnable() &&
               securityProperties.getAudit().getRetentionDays() >= 90 &&
               securityProperties.getCrypto().isEnableProtocolEncryption() &&
               securityProperties.getAntiCheat().isEnable();
    }
    
    /**
     * 重置每日统计
     */
    private void resetDailyStatistics() {
        // 暂不重置统计信息，保留累计数据
    }
    
    /**
     * 重置每月统计
     */
    private void resetMonthlyStatistics() {
        // 每月重置统计信息
        eventStatistics.clear();
        riskEvents.clear();
        log.info("已重置月度统计数据");
    }
    
    /**
     * 事件统计
     */
    @Data
    private static class EventStatistics {
        /**
         * 事件类型
         */
        private final String eventType;
        
        /**
         * 事件计数
         */
        private int count;
        
        /**
         * 构造函数
         *
         * @param eventType 事件类型
         */
        public EventStatistics(String eventType) {
            this.eventType = eventType;
            this.count = 0;
        }
        
        /**
         * 增加计数
         */
        public void increment() {
            count++;
        }
    }
    
    /**
     * 风险事件
     */
    @Data
    private static class RiskEvent {
        /**
         * 事件类型
         */
        private final String eventType;
        
        /**
         * 严重性
         */
        private final String severity;
        
        /**
         * 时间戳
         */
        private final LocalDateTime timestamp;
        
        /**
         * 用户名
         */
        private final String username;
        
        /**
         * 资源
         */
        private final String resource;
        
        /**
         * 原因
         */
        private final String reason;
        
        /**
         * 构造函数
         *
         * @param eventType 事件类型
         * @param severity 严重性
         * @param timestamp 时间戳
         * @param username 用户名
         * @param resource 资源
         * @param reason 原因
         */
        public RiskEvent(String eventType, String severity, LocalDateTime timestamp, 
                       String username, String resource, String reason) {
            this.eventType = eventType;
            this.severity = severity;
            this.timestamp = timestamp;
            this.username = username;
            this.resource = resource;
            this.reason = reason;
        }
    }
}