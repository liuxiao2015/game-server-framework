/*
 * 文件名: AuditEventPublisher.java
 * 用途: 审计事件发布器
 * 实现内容:
 *   - 实时事件推送
 *   - 事件分类分级
 *   - 事件聚合分析
 *   - 告警触发机制
 *   - 事件存储优化
 * 技术选型:
 *   - Spring ApplicationEventPublisher
 *   - 事件驱动架构
 *   - 异步事件处理
 * 依赖关系:
 *   - 被SecurityAuditLogger使用
 *   - 使用Spring事件系统
 */
package com.lx.gameserver.frame.security.audit;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计事件发布器
 * <p>
 * 提供安全审计事件的发布、分发和处理机制，支持实时推送、
 * 事件分类、聚合分析和告警触发等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class AuditEventPublisher {

    /**
     * Spring事件发布器
     */
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 事件计数器
     * Key: 事件类型
     * Value: 计数
     */
    private final Map<String, AtomicInteger> eventCounter = new ConcurrentHashMap<>();
    
    /**
     * 事件分类映射
     * Key: 事件类型
     * Value: 事件类别
     */
    private final Map<String, String> eventCategories = new HashMap<>();
    
    /**
     * 事件级别映射
     * Key: 事件类型
     * Value: 事件级别（INFO, WARNING, CRITICAL）
     */
    private final Map<String, String> eventSeverities = new HashMap<>();
    
    /**
     * 构造函数
     *
     * @param eventPublisher Spring事件发布器
     * @param securityProperties 安全配置
     */
    public AuditEventPublisher(ApplicationEventPublisher eventPublisher, 
                             SecurityProperties securityProperties) {
        this.eventPublisher = eventPublisher;
        this.securityProperties = securityProperties;
        
        // 初始化事件分类和级别
        initEventMappings();
        
        log.info("审计事件发布器初始化完成");
    }
    
    /**
     * 初始化事件映射
     */
    private void initEventMappings() {
        // 初始化事件分类
        eventCategories.put("LOGIN", "认证");
        eventCategories.put("LOGOUT", "认证");
        eventCategories.put("LOGIN_FAILURE", "认证");
        eventCategories.put("PERMISSION_CHANGE", "授权");
        eventCategories.put("ACCESS_DENIED", "授权");
        eventCategories.put("SENSITIVE_OPERATION", "操作");
        eventCategories.put("ABNORMAL_ACCESS", "安全威胁");
        eventCategories.put("CHEAT_DETECTION", "安全威胁");
        eventCategories.put("SYSTEM_SECURITY", "系统安全");
        eventCategories.put("DATA_ACCESS", "数据访问");
        
        // 初始化事件级别
        eventSeverities.put("LOGIN", "INFO");
        eventSeverities.put("LOGOUT", "INFO");
        eventSeverities.put("LOGIN_FAILURE", "WARNING");
        eventSeverities.put("PERMISSION_CHANGE", "WARNING");
        eventSeverities.put("ACCESS_DENIED", "WARNING");
        eventSeverities.put("SENSITIVE_OPERATION", "WARNING");
        eventSeverities.put("ABNORMAL_ACCESS", "WARNING");
        eventSeverities.put("CHEAT_DETECTION", "WARNING");
        eventSeverities.put("SYSTEM_SECURITY", "CRITICAL");
        eventSeverities.put("DATA_ACCESS", "INFO");
    }
    
    /**
     * 发布审计事件
     *
     * @param eventType 事件类型
     * @param eventData 事件数据
     */
    @Async
    public void publishAuditEvent(String eventType, Map<String, Object> eventData) {
        if (!securityProperties.getAudit().isEnable()) {
            // 审计功能未启用
            return;
        }
        
        try {
            // 统计事件计数
            incrementEventCount(eventType);
            
            // 添加事件分类和级别信息
            Map<String, Object> enrichedData = new HashMap<>(eventData);
            enrichedData.put("category", getEventCategory(eventType));
            enrichedData.put("severity", getEventSeverity(eventType));
            
            // 创建事件对象
            AuditEvent event = new AuditEvent(this, eventType, enrichedData);
            
            // 发布事件
            eventPublisher.publishEvent(event);
            
            // 检查是否需要触发告警
            checkAlertThreshold(eventType, enrichedData);
            
            log.debug("已发布审计事件: type={}, category={}, severity={}",
                    eventType, enrichedData.get("category"), enrichedData.get("severity"));
            
        } catch (Exception e) {
            log.error("发布审计事件失败: type={}", eventType, e);
        }
    }
    
    /**
     * 增加事件计数
     *
     * @param eventType 事件类型
     * @return 当前计数
     */
    private int incrementEventCount(String eventType) {
        return eventCounter.computeIfAbsent(eventType, k -> new AtomicInteger(0))
                .incrementAndGet();
    }
    
    /**
     * 获取事件分类
     *
     * @param eventType 事件类型
     * @return 事件分类
     */
    private String getEventCategory(String eventType) {
        return eventCategories.getOrDefault(eventType, "其他");
    }
    
    /**
     * 获取事件级别
     *
     * @param eventType 事件类型
     * @return 事件级别
     */
    private String getEventSeverity(String eventType) {
        return eventSeverities.getOrDefault(eventType, "INFO");
    }
    
    /**
     * 检查告警阈值
     *
     * @param eventType 事件类型
     * @param eventData 事件数据
     */
    private void checkAlertThreshold(String eventType, Map<String, Object> eventData) {
        // 特定事件类型的告警逻辑
        switch (eventType) {
            case "LOGIN_FAILURE":
                // 检查登录失败次数是否达到阈值
                int count = getEventCount(eventType);
                if (count >= 10) {
                    triggerAlert("多次登录失败", "检测到短时间内多次登录失败尝试，可能存在密码破解风险", "HIGH");
                }
                break;
                
            case "ABNORMAL_ACCESS":
                // 异常访问直接触发告警
                String resource = (String) eventData.get("resource");
                String reason = (String) eventData.get("reason");
                triggerAlert("异常访问检测", "资源: " + resource + ", 原因: " + reason, "MEDIUM");
                break;
                
            case "CHEAT_DETECTION":
                // 作弊检测告警
                float score = ((Number) eventData.getOrDefault("score", 0)).floatValue();
                if (score > 80) {
                    String playerId = (String) eventData.get("playerId");
                    String cheatReason = (String) eventData.get("reason");
                    triggerAlert("高可信度作弊检测", "玩家: " + playerId + ", 原因: " + cheatReason + ", 评分: " + score, "HIGH");
                }
                break;
                
            case "SYSTEM_SECURITY":
                // 系统安全事件直接触发高优先级告警
                String message = (String) eventData.get("message");
                triggerAlert("系统安全事件", message, "CRITICAL");
                break;
        }
    }
    
    /**
     * 触发告警
     *
     * @param title 告警标题
     * @param content 告警内容
     * @param priority 优先级
     */
    private void triggerAlert(String title, String content, String priority) {
        // 在实际项目中，这里可以集成短信、邮件、webhook等告警方式
        log.warn("安全告警: 标题={}, 内容={}, 优先级={}", title, content, priority);
        
        // TODO: 实现实际的告警通知逻辑
    }
    
    /**
     * 获取事件计数
     *
     * @param eventType 事件类型
     * @return 事件计数
     */
    public int getEventCount(String eventType) {
        AtomicInteger counter = eventCounter.get(eventType);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 重置事件计数
     *
     * @param eventType 事件类型
     */
    public void resetEventCount(String eventType) {
        AtomicInteger counter = eventCounter.get(eventType);
        if (counter != null) {
            counter.set(0);
        }
    }
    
    /**
     * 重置所有事件计数
     */
    public void resetAllEventCounts() {
        eventCounter.forEach((type, counter) -> counter.set(0));
    }
    
    /**
     * 审计事件类
     */
    public static class AuditEvent {
        private final Object source;
        private final String type;
        private final Map<String, Object> data;
        
        /**
         * 构造函数
         *
         * @param source 事件源
         * @param type 事件类型
         * @param data 事件数据
         */
        public AuditEvent(Object source, String type, Map<String, Object> data) {
            this.source = source;
            this.type = type;
            this.data = data;
        }
        
        /**
         * 获取事件源
         *
         * @return 事件源
         */
        public Object getSource() {
            return source;
        }
        
        /**
         * 获取事件类型
         *
         * @return 事件类型
         */
        public String getType() {
            return type;
        }
        
        /**
         * 获取事件数据
         *
         * @return 事件数据
         */
        public Map<String, Object> getData() {
            return data;
        }
    }
}