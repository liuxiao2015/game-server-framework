/*
 * 文件名: AdminContext.java
 * 用途: 管理平台上下文管理器
 * 实现内容:
 *   - 管理平台全局上下文管理
 *   - 服务注册中心集成
 *   - 配置中心集成
 *   - 权限上下文管理
 *   - 租户上下文管理
 *   - 插件上下文管理
 * 技术选型:
 *   - Spring Context (上下文管理)
 *   - ThreadLocal (线程本地存储)
 *   - ConcurrentHashMap (并发安全Map)
 *   - Spring Security (安全上下文)
 * 依赖关系: 被所有管理模块依赖，提供统一的上下文访问接口
 */
package com.lx.gameserver.admin.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理平台上下文
 * <p>
 * 作为管理平台的全局上下文管理器，提供对各种上下文信息的
 * 统一访问，包括用户上下文、权限上下文、租户上下文、
 * 插件上下文等。支持线程安全的上下文切换和管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Component
@Getter
public class AdminContext {

    /** 初始化标志 */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Spring应用上下文 */
    @Autowired
    private ApplicationContext applicationContext;

    /** 环境配置 */
    @Autowired
    private Environment environment;

    /** 事件发布器 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 插件管理器 */
    @Autowired(required = false)
    private PluginManager pluginManager;

    /** 全局共享数据存储 */
    private final Map<String, Object> globalAttributes = new ConcurrentHashMap<>();

    /** 线程本地用户上下文 */
    private static final ThreadLocal<UserContext> USER_CONTEXT = new ThreadLocal<>();

    /** 线程本地权限上下文 */
    private static final ThreadLocal<PermissionContext> PERMISSION_CONTEXT = new ThreadLocal<>();

    /** 线程本地租户上下文 */
    private static final ThreadLocal<TenantContext> TENANT_CONTEXT = new ThreadLocal<>();

    /** 线程本地请求上下文 */
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new ThreadLocal<>();

    /**
     * 初始化上下文
     */
    @PostConstruct
    public void init() {
        if (initialized.compareAndSet(false, true)) {
            log.info("初始化管理平台上下文...");
            
            // 初始化全局配置
            initGlobalConfiguration();
            
            // 注册关闭钩子
            registerShutdownHook();
            
            log.info("管理平台上下文初始化完成");
        }
    }

    /**
     * 获取当前用户上下文
     *
     * @return 用户上下文，如果不存在则返回null
     */
    public static UserContext getCurrentUser() {
        return USER_CONTEXT.get();
    }

    /**
     * 设置当前用户上下文
     *
     * @param userContext 用户上下文
     */
    public static void setCurrentUser(UserContext userContext) {
        USER_CONTEXT.set(userContext);
    }

    /**
     * 获取当前权限上下文
     *
     * @return 权限上下文，如果不存在则返回null
     */
    public static PermissionContext getCurrentPermission() {
        return PERMISSION_CONTEXT.get();
    }

    /**
     * 设置当前权限上下文
     *
     * @param permissionContext 权限上下文
     */
    public static void setCurrentPermission(PermissionContext permissionContext) {
        PERMISSION_CONTEXT.set(permissionContext);
    }

    /**
     * 获取当前租户上下文
     *
     * @return 租户上下文，如果不存在则返回null
     */
    public static TenantContext getCurrentTenant() {
        return TENANT_CONTEXT.get();
    }

    /**
     * 设置当前租户上下文
     *
     * @param tenantContext 租户上下文
     */
    public static void setCurrentTenant(TenantContext tenantContext) {
        TENANT_CONTEXT.set(tenantContext);
    }

    /**
     * 获取当前请求上下文
     *
     * @return 请求上下文，如果不存在则返回null
     */
    public static RequestContext getCurrentRequest() {
        return REQUEST_CONTEXT.get();
    }

    /**
     * 设置当前请求上下文
     *
     * @param requestContext 请求上下文
     */
    public static void setCurrentRequest(RequestContext requestContext) {
        REQUEST_CONTEXT.set(requestContext);
    }

    /**
     * 清理当前线程的所有上下文
     */
    public static void clearContext() {
        USER_CONTEXT.remove();
        PERMISSION_CONTEXT.remove();
        TENANT_CONTEXT.remove();
        REQUEST_CONTEXT.remove();
    }

    /**
     * 获取全局属性
     *
     * @param key 属性键
     * @return 属性值
     */
    public Object getGlobalAttribute(String key) {
        return globalAttributes.get(key);
    }

    /**
     * 设置全局属性
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setGlobalAttribute(String key, Object value) {
        globalAttributes.put(key, value);
    }

    /**
     * 移除全局属性
     *
     * @param key 属性键
     * @return 被移除的属性值
     */
    public Object removeGlobalAttribute(String key) {
        return globalAttributes.remove(key);
    }

    /**
     * 获取当前认证信息
     *
     * @return Spring Security认证信息
     */
    public Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 检查是否已认证
     *
     * @return 如果已认证返回true，否则返回false
     */
    public boolean isAuthenticated() {
        Authentication auth = getCurrentAuthentication();
        return auth != null && auth.isAuthenticated();
    }

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    public String getProperty(String key) {
        return environment.getProperty(key);
    }

    /**
     * 获取配置值，带默认值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getProperty(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    /**
     * 发布应用事件
     *
     * @param event 事件对象
     */
    public void publishEvent(Object event) {
        eventPublisher.publishEvent(event);
    }

    /**
     * 初始化全局配置
     */
    private void initGlobalConfiguration() {
        // 设置应用信息
        setGlobalAttribute("app.name", "游戏服务器管理后台");
        setGlobalAttribute("app.version", "1.0.0");
        setGlobalAttribute("app.startTime", System.currentTimeMillis());
        
        // 设置运行环境信息
        setGlobalAttribute("runtime.javaVersion", System.getProperty("java.version"));
        setGlobalAttribute("runtime.osName", System.getProperty("os.name"));
        setGlobalAttribute("runtime.maxMemory", Runtime.getRuntime().maxMemory());
        
        log.debug("全局配置初始化完成");
    }

    /**
     * 注册应用关闭钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("执行管理平台上下文清理...");
            
            // 清理所有线程本地变量
            clearAllThreadLocalVariables();
            
            // 清理全局属性
            globalAttributes.clear();
            
            log.info("管理平台上下文清理完成");
        }, "AdminContext-Shutdown-Thread"));
    }

    /**
     * 清理所有线程本地变量
     */
    private void clearAllThreadLocalVariables() {
        try {
            clearContext();
        } catch (Exception e) {
            log.warn("清理线程本地变量失败: {}", e.getMessage());
        }
    }

    /**
     * 用户上下文信息
     */
    public static class UserContext {
        @Getter
        private final Long userId;
        @Getter
        private final String username;
        @Getter
        private final String nickname;
        @Getter
        private final String email;
        @Getter
        private final Map<String, Object> attributes;

        public UserContext(Long userId, String username, String nickname, String email) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.email = email;
            this.attributes = new ConcurrentHashMap<>();
        }
    }

    /**
     * 权限上下文信息
     */
    public static class PermissionContext {
        @Getter
        private final Map<String, Boolean> permissions;
        @Getter
        private final Map<String, Object> dataScopes;

        public PermissionContext() {
            this.permissions = new ConcurrentHashMap<>();
            this.dataScopes = new ConcurrentHashMap<>();
        }

        public boolean hasPermission(String permission) {
            return permissions.getOrDefault(permission, false);
        }

        public void grantPermission(String permission) {
            permissions.put(permission, true);
        }

        public void revokePermission(String permission) {
            permissions.put(permission, false);
        }
    }

    /**
     * 租户上下文信息
     */
    public static class TenantContext {
        @Getter
        private final String tenantId;
        @Getter
        private final String tenantName;
        @Getter
        private final Map<String, Object> tenantConfig;

        public TenantContext(String tenantId, String tenantName) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
            this.tenantConfig = new ConcurrentHashMap<>();
        }
    }

    /**
     * 请求上下文信息
     */
    public static class RequestContext {
        @Getter
        private final String requestId;
        @Getter
        private final String clientIp;
        @Getter
        private final String userAgent;
        @Getter
        private final long startTime;
        @Getter
        private final Map<String, Object> attributes;

        public RequestContext(String requestId, String clientIp, String userAgent) {
            this.requestId = requestId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.startTime = System.currentTimeMillis();
            this.attributes = new ConcurrentHashMap<>();
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }
    }
}