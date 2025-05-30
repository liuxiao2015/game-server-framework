/*
 * 文件名: SecurityAuditLogger.java
 * 用途: 安全审计日志
 * 实现内容:
 *   - 登录日志记录
 *   - 权限变更记录
 *   - 异常访问记录
 *   - 敏感操作记录
 *   - 日志防篡改
 * 技术选型:
 *   - 分层日志结构
 *   - 异步日志记录
 *   - 摘要签名防篡改
 * 依赖关系:
 *   - 被安全模块使用
 *   - 使用AuditEventPublisher
 */
package com.lx.gameserver.frame.security.audit;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.crypto.SignatureService;
import com.lx.gameserver.frame.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 安全审计日志
 * <p>
 * 提供安全相关事件的审计日志记录功能，包括用户登录、
 * 权限变更、异常访问和敏感操作等，支持防篡改和合规性追踪。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class SecurityAuditLogger {

    /**
     * 事件发布器
     */
    private final AuditEventPublisher eventPublisher;
    
    /**
     * 签名服务
     */
    @Nullable
    private final SignatureService signatureService;
    
    /**
     * 安全配置
     */
    private final SecurityProperties securityProperties;
    
    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());
    
    /**
     * 构造函数
     *
     * @param eventPublisher 事件发布器
     * @param signatureService 签名服务
     * @param securityProperties 安全配置
     */
    @Autowired
    public SecurityAuditLogger(AuditEventPublisher eventPublisher, 
                             @Nullable SignatureService signatureService,
                             SecurityProperties securityProperties) {
        this.eventPublisher = eventPublisher;
        this.signatureService = signatureService;
        this.securityProperties = securityProperties;
        
        log.info("安全审计日志记录器初始化完成");
    }
    
    /**
     * 记录用户登录
     *
     * @param username 用户名
     * @param success 是否成功
     * @param request HTTP请求
     * @param details 额外详情
     */
    @Async
    public CompletableFuture<Void> logLogin(String username, boolean success, 
                            @Nullable HttpServletRequest request, @Nullable Map<String, Object> details) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("event", "login");
            data.put("username", username);
            data.put("result", success ? "success" : "failure");
            data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
            
            if (request != null) {
                data.put("ip", SecurityUtils.getClientIpAddress(request));
                data.put("userAgent", request.getHeader("User-Agent"));
            }
            
            if (details != null) {
                data.putAll(details);
            }
            
            return logSecurityEvent("LOGIN", data);
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 记录用户注销
     *
     * @param username 用户名
     * @param sessionId 会话ID
     */
    @Async
    public CompletableFuture<Void> logLogout(String username, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", "logout");
        data.put("username", username);
        data.put("sessionId", sessionId);
        data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
        
        return logSecurityEvent("LOGOUT", data);
    }
    
    /**
     * 记录权限变更
     *
     * @param targetUsername 目标用户
     * @param permission 权限
     * @param grantOrRevoke true表示授予，false表示撤销
     * @param operatorUsername 操作人
     */
    @Async
    public CompletableFuture<Void> logPermissionChange(String targetUsername, String permission, 
                                    boolean grantOrRevoke, String operatorUsername) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", "permissionChange");
        data.put("targetUser", targetUsername);
        data.put("permission", permission);
        data.put("action", grantOrRevoke ? "grant" : "revoke");
        data.put("operator", operatorUsername);
        data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
        
        return logSecurityEvent("PERMISSION_CHANGE", data);
    }
    
    /**
     * 记录敏感操作
     *
     * @param operation 操作类型
     * @param target 操作目标
     * @param result 操作结果
     * @param details 详情
     */
    @Async
    public CompletableFuture<Void> logSensitiveOperation(String operation, String target, 
                                      String result, @Nullable Map<String, Object> details) {
        try {
            // 获取当前用户
            GameUserDetails user = SecurityUtils.getCurrentUser();
            String username = user != null ? user.getUsername() : "unknown";
            
            Map<String, Object> data = new HashMap<>();
            data.put("event", "sensitiveOperation");
            data.put("operation", operation);
            data.put("target", target);
            data.put("result", result);
            data.put("username", username);
            data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
            
            if (details != null) {
                data.putAll(details);
            }
            
            return logSecurityEvent("SENSITIVE_OPERATION", data);
        } catch (Exception e) {
            log.error("记录敏感操作日志失败", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 记录异常访问
     *
     * @param resource 资源
     * @param reason 原因
     * @param request HTTP请求
     */
    @Async
    public CompletableFuture<Void> logAbnormalAccess(String resource, String reason, HttpServletRequest request) {
        try {
            // 获取当前用户
            String username = SecurityUtils.getCurrentUsername();
            if (username == null) {
                username = "anonymous";
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("event", "abnormalAccess");
            data.put("resource", resource);
            data.put("reason", reason);
            data.put("username", username);
            data.put("ip", SecurityUtils.getClientIpAddress(request));
            data.put("method", request.getMethod());
            data.put("uri", request.getRequestURI());
            data.put("userAgent", request.getHeader("User-Agent"));
            data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
            
            return logSecurityEvent("ABNORMAL_ACCESS", data);
        } catch (Exception e) {
            log.error("记录异常访问日志失败", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 记录作弊检测
     *
     * @param playerId 玩家ID
     * @param reason 原因
     * @param score 异常评分
     * @param details 详情
     */
    @Async
    public CompletableFuture<Void> logCheatDetection(String playerId, String reason, float score, 
                                 @Nullable Map<String, Object> details) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("event", "cheatDetection");
            data.put("playerId", playerId);
            data.put("reason", reason);
            data.put("score", score);
            data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
            
            if (details != null) {
                data.putAll(details);
            }
            
            return logSecurityEvent("CHEAT_DETECTION", data);
        } catch (Exception e) {
            log.error("记录作弊检测日志失败", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 记录系统安全事件
     *
     * @param type 事件类型
     * @param message 消息
     * @param severity 严重程度
     */
    @Async
    public CompletableFuture<Void> logSystemSecurityEvent(String type, String message, String severity) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", "systemSecurity");
        data.put("type", type);
        data.put("message", message);
        data.put("severity", severity);
        data.put("timestamp", DATE_FORMATTER.format(Instant.now()));
        
        return logSecurityEvent("SYSTEM_SECURITY", data);
    }
    
    /**
     * 记录通用安全事件
     *
     * @param eventType 事件类型
     * @param data 事件数据
     * @return 异步任务
     */
    @Async
    public CompletableFuture<Void> logSecurityEvent(String eventType, Map<String, Object> data) {
        try {
            if (!securityProperties.getAudit().isEnable()) {
                // 审计功能未启用
                return CompletableFuture.completedFuture(null);
            }
            
            // 添加事件ID
            String eventId = UUID.randomUUID().toString();
            data.put("eventId", eventId);
            
            // 如果支持签名，添加防篡改签名
            if (signatureService != null) {
                try {
                    String dataJson = objectToJson(data);
                    String hmac = signatureService.signWithHmac(
                            dataJson.getBytes(), "audit-log-secret-key");
                    data.put("signature", hmac);
                } catch (Exception e) {
                    log.warn("生成审计日志签名失败", e);
                }
            }
            
            // 记录日志
            log.info("安全事件: type={}, id={}, data={}", eventType, eventId, data);
            
            // 发布事件
            eventPublisher.publishAuditEvent(eventType, data);
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("记录安全事件日志失败", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 对象转JSON字符串（简化实现）
     *
     * @param obj 对象
     * @return JSON字符串
     */
    private String objectToJson(Object obj) {
        return obj.toString();
    }
}