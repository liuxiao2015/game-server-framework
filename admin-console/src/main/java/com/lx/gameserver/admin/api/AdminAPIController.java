/*
 * 文件名: AdminAPIController.java
 * 用途: 管理后台API控制器
 * 实现内容:
 *   - RESTful API接口定义
 *   - 统一响应格式
 *   - 异常处理
 *   - API文档注解
 *   - 权限验证集成
 *   - 请求日志记录
 * 技术选型:
 *   - Spring Web MVC (REST框架)
 *   - SpringDoc OpenAPI (API文档)
 *   - Spring Security (权限验证)
 *   - Validation (参数验证)
 * 依赖关系: 提供HTTP API接口，依赖各业务服务
 */
package com.lx.gameserver.admin.api;

import com.lx.gameserver.admin.auth.AuthenticationService;
import com.lx.gameserver.admin.config.ConfigurationManager;
import com.lx.gameserver.admin.core.AdminContext;
import com.lx.gameserver.admin.game.PlayerManagement;
import com.lx.gameserver.admin.monitor.MetricsCollector;
import com.lx.gameserver.admin.monitor.ServiceMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理后台API控制器
 * <p>
 * 提供管理后台的RESTful API接口，包括认证、监控、配置、
 * 玩家管理等功能。支持标准的HTTP状态码和统一的响应格式，
 * 集成权限验证和API文档生成。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@RestController
@RequestMapping("/admin/api")
@Validated
@Tag(name = "管理后台API", description = "游戏服务器管理后台的API接口")
public class AdminAPIController {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ServiceMonitor serviceMonitor;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private ConfigurationManager configurationManager;

    @Autowired
    private PlayerManagement playerManagement;

    @Autowired
    private AdminContext adminContext;

    // ==================== 认证相关API ====================

    /**
     * 用户登录
     */
    @PostMapping("/auth/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码进行登录认证")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        
        AuthenticationService.AuthenticationResult result = authenticationService.authenticate(
            request.getUsername(), request.getPassword(), clientIp);
        
        if (result.isSuccess()) {
            LoginResponse response = new LoginResponse();
            response.setAccessToken(result.getAccessToken());
            response.setRefreshToken(result.getRefreshToken());
            response.setExpiresIn(7200L); // 2小时
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, result.getMessage()));
        }
    }

    /**
     * 刷新Token
     */
    @PostMapping("/auth/refresh")
    @Operation(summary = "刷新访问Token", description = "使用刷新Token获取新的访问Token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        
        AuthenticationService.AuthenticationResult result = authenticationService.refreshToken(
            request.getRefreshToken());
        
        if (result.isSuccess()) {
            LoginResponse response = new LoginResponse();
            response.setAccessToken(result.getAccessToken());
            response.setRefreshToken(result.getRefreshToken());
            response.setExpiresIn(7200L);
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, result.getMessage()));
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/auth/logout")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "用户登出", description = "退出登录并清除Token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorization) {
        
        String token = extractToken(authorization);
        AdminContext.UserContext user = AdminContext.getCurrentUser();
        
        if (user != null) {
            authenticationService.logout(token, user.getUsername());
        }
        
        return ResponseEntity.ok(ApiResponse.success(null, "登出成功"));
    }

    // ==================== 监控相关API ====================

    /**
     * 获取系统健康状态
     */
    @GetMapping("/monitor/health")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取系统健康状态", description = "获取整个系统的健康状态概览")
    public ResponseEntity<ApiResponse<ServiceMonitor.SystemHealthStatus>> getSystemHealth() {
        ServiceMonitor.SystemHealthStatus status = serviceMonitor.getSystemHealthStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * 获取服务列表
     */
    @GetMapping("/monitor/services")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取监控服务列表", description = "获取所有被监控的服务信息")
    public ResponseEntity<ApiResponse<List<ServiceMonitor.ServiceInfo>>> getServices() {
        List<ServiceMonitor.ServiceInfo> services = serviceMonitor.getAllServices();
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    /**
     * 获取指标概览
     */
    @GetMapping("/monitor/metrics/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取指标概览", description = "获取系统指标的概览信息")
    public ResponseEntity<ApiResponse<MetricsCollector.MetricsOverview>> getMetricsOverview() {
        MetricsCollector.MetricsOverview overview = metricsCollector.getMetricsOverview();
        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    /**
     * 获取指标历史数据
     */
    @GetMapping("/monitor/metrics/{metricName}/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取指标历史数据", description = "获取指定指标的历史数据")
    public ResponseEntity<ApiResponse<List<MetricsCollector.MetricRecord>>> getMetricHistory(
            @Parameter(description = "指标名称") @PathVariable String metricName,
            @Parameter(description = "时间范围(分钟)") @RequestParam(defaultValue = "60") int duration) {
        
        List<MetricsCollector.MetricRecord> history = metricsCollector.getMetricHistory(metricName, duration);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    // ==================== 配置管理API ====================

    /**
     * 获取配置值
     */
    @GetMapping("/config/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取配置值", description = "根据配置键获取配置值")
    public ResponseEntity<ApiResponse<String>> getConfig(
            @Parameter(description = "配置键") @PathVariable String key) {
        
        String value = configurationManager.getConfig(key);
        if (value != null) {
            return ResponseEntity.ok(ApiResponse.success(value));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "配置不存在"));
        }
    }

    /**
     * 设置配置值
     */
    @PostMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "设置配置值", description = "设置或更新配置值")
    public ResponseEntity<ApiResponse<String>> setConfig(
            @Valid @RequestBody ConfigRequest request) {
        
        AdminContext.UserContext user = AdminContext.getCurrentUser();
        String operator = user != null ? user.getUsername() : "system";
        
        String configId = configurationManager.setConfig(
            request.getKey(), 
            request.getValue(), 
            request.getDescription(), 
            operator,
            request.getGroup(),
            request.isRequiresApproval()
        );
        
        return ResponseEntity.ok(ApiResponse.success(configId, "配置设置成功"));
    }

    // ==================== 玩家管理API ====================

    /**
     * 搜索玩家
     */
    @PostMapping("/players/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "搜索玩家", description = "根据条件搜索玩家信息")
    public ResponseEntity<ApiResponse<PlayerManagement.PlayerSearchResult>> searchPlayers(
            @Valid @RequestBody PlayerManagement.PlayerSearchCriteria criteria) {
        
        PlayerManagement.PlayerSearchResult result = playerManagement.searchPlayers(criteria);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取玩家信息
     */
    @GetMapping("/players/{playerId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取玩家信息", description = "根据玩家ID获取详细信息")
    public ResponseEntity<ApiResponse<PlayerManagement.PlayerInfo>> getPlayer(
            @Parameter(description = "玩家ID") @PathVariable Long playerId) {
        
        PlayerManagement.PlayerInfo player = playerManagement.getPlayerInfo(playerId);
        if (player != null) {
            return ResponseEntity.ok(ApiResponse.success(player));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "玩家不存在"));
        }
    }

    /**
     * 封禁玩家
     */
    @PostMapping("/players/{playerId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "封禁玩家", description = "封禁指定玩家账号")
    public ResponseEntity<ApiResponse<Void>> banPlayer(
            @Parameter(description = "玩家ID") @PathVariable Long playerId,
            @Valid @RequestBody BanPlayerRequest request) {
        
        AdminContext.UserContext user = AdminContext.getCurrentUser();
        String operator = user != null ? user.getUsername() : "system";
        
        PlayerManagement.PlayerOperationResult result = playerManagement.banPlayer(
            playerId, request.getReason(), request.getDuration(), operator);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(null, result.getMessage()));
        } else {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, result.getMessage()));
        }
    }

    /**
     * 解封玩家
     */
    @PostMapping("/players/{playerId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "解封玩家", description = "解除玩家账号封禁")
    public ResponseEntity<ApiResponse<Void>> unbanPlayer(
            @Parameter(description = "玩家ID") @PathVariable Long playerId) {
        
        AdminContext.UserContext user = AdminContext.getCurrentUser();
        String operator = user != null ? user.getUsername() : "system";
        
        PlayerManagement.PlayerOperationResult result = playerManagement.unbanPlayer(playerId, operator);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(null, result.getMessage()));
        } else {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, result.getMessage()));
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 从Authorization头提取Token
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    // ==================== 请求/响应数据类 ====================

    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        
        @NotBlank(message = "密码不能为空")
        private String password;

        // Getter和Setter方法
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RefreshTokenRequest {
        @NotBlank(message = "刷新Token不能为空")
        private String refreshToken;

        // Getter和Setter方法
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private Long expiresIn;

        // Getter和Setter方法
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public Long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }
    }

    public static class ConfigRequest {
        @NotBlank(message = "配置键不能为空")
        private String key;
        
        @NotBlank(message = "配置值不能为空")
        private String value;
        
        private String description;
        private String group;
        private boolean requiresApproval = false;

        // Getter和Setter方法
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public boolean isRequiresApproval() { return requiresApproval; }
        public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    }

    public static class BanPlayerRequest {
        @NotBlank(message = "封禁原因不能为空")
        private String reason;
        
        private Integer duration; // 小时，null表示永久

        // Getter和Setter方法
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
    }

    /**
     * 统一API响应格式
     */
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;
        private LocalDateTime timestamp;

        private ApiResponse(int code, String message, T data) {
            this.code = code;
            this.message = message;
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }

        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(200, "成功", data);
        }

        public static <T> ApiResponse<T> success(T data, String message) {
            return new ApiResponse<>(200, message, data);
        }

        public static <T> ApiResponse<T> error(int code, String message) {
            return new ApiResponse<>(code, message, null);
        }

        // Getter方法
        public int getCode() { return code; }
        public String getMessage() { return message; }
        public T getData() { return data; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}