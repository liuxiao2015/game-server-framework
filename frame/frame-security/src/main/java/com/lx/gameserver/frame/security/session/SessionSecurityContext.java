/*
 * 文件名: SessionSecurityContext.java
 * 用途: 会话安全上下文
 * 实现内容:
 *   - 用户身份信息管理
 *   - 权限信息缓存
 *   - 会话加密密钥管理
 *   - 安全级别标记
 *   - 上下文传播机制
 * 技术选型:
 *   - Spring Security SecurityContext扩展
 *   - ThreadLocal上下文传播
 *   - 会话级密钥管理
 * 依赖关系:
 *   - 与Spring Security集成
 *   - 被认证和授权模块使用
 */
package com.lx.gameserver.frame.security.session;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话安全上下文
 * <p>
 * 提供会话级别的安全上下文管理，包括用户身份信息、权限缓存、
 * 会话密钥、安全等级等，支持上下文在不同线程间的安全传播。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class SessionSecurityContext {
    
    /**
     * 线程本地存储的会话上下文
     */
    private static final ThreadLocal<SessionContext> CONTEXT_HOLDER = new ThreadLocal<>();
    
    /**
     * 全局会话上下文缓存
     */
    private static final Map<String, SessionContext> SESSION_CONTEXTS = new ConcurrentHashMap<>();

    /**
     * 设置当前会话上下文
     *
     * @param sessionId 会话ID
     * @param userDetails 用户详情
     * @param authorities 权限列表
     */
    public static void setContext(String sessionId, GameUserDetails userDetails, 
                                 Collection<? extends GrantedAuthority> authorities) {
        if (!StringUtils.hasText(sessionId) || userDetails == null) {
            log.warn("设置会话上下文失败：参数无效");
            return;
        }
        
        try {
            SessionContext context = SessionContext.builder()
                    .sessionId(sessionId)
                    .userId(userDetails.getUserId())
                    .username(userDetails.getUsername())
                    .userDetails(userDetails)
                    .authorities(new HashSet<>(authorities))
                    .securityLevel(determineSecurityLevel(userDetails))
                    .createTime(Instant.now())
                    .lastAccessTime(Instant.now())
                    .sessionKey(generateSessionKey())
                    .attributes(new ConcurrentHashMap<>())
                    .build();
            
            // 设置到线程本地存储
            CONTEXT_HOLDER.set(context);
            
            // 缓存到全局会话上下文
            SESSION_CONTEXTS.put(sessionId, context);
            
            log.debug("设置会话上下文成功: sessionId={}, userId={}", sessionId, userDetails.getUserId());
            
        } catch (Exception e) {
            log.error("设置会话上下文失败: sessionId=" + sessionId, e);
        }
    }

    /**
     * 获取当前会话上下文
     *
     * @return 会话上下文
     */
    public static SessionContext getCurrentContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 根据会话ID获取上下文
     *
     * @param sessionId 会话ID
     * @return 会话上下文
     */
    public static SessionContext getContext(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return SESSION_CONTEXTS.get(sessionId);
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID
     */
    public static String getCurrentUserId() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名
     */
    public static String getCurrentUsername() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 获取当前会话ID
     *
     * @return 会话ID
     */
    public static String getCurrentSessionId() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getSessionId() : null;
    }

    /**
     * 获取当前用户详情
     *
     * @return 用户详情
     */
    public static GameUserDetails getCurrentUserDetails() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getUserDetails() : null;
    }

    /**
     * 获取当前用户权限
     *
     * @return 权限集合
     */
    public static Set<GrantedAuthority> getCurrentAuthorities() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getAuthorities() : Collections.emptySet();
    }

    /**
     * 检查当前用户是否拥有指定权限
     *
     * @param authority 权限名称
     * @return 是否拥有权限
     */
    public static boolean hasAuthority(String authority) {
        if (!StringUtils.hasText(authority)) {
            return false;
        }
        
        Set<GrantedAuthority> authorities = getCurrentAuthorities();
        return authorities.stream()
                .anyMatch(auth -> authority.equals(auth.getAuthority()));
    }

    /**
     * 检查当前用户是否拥有任一指定权限
     *
     * @param authorities 权限名称数组
     * @return 是否拥有任一权限
     */
    public static boolean hasAnyAuthority(String... authorities) {
        if (authorities == null || authorities.length == 0) {
            return false;
        }
        
        for (String authority : authorities) {
            if (hasAuthority(authority)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前用户是否拥有指定角色
     *
     * @param role 角色名称
     * @return 是否拥有角色
     */
    public static boolean hasRole(String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        
        String roleAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return hasAuthority(roleAuthority);
    }

    /**
     * 获取当前会话密钥
     *
     * @return 会话密钥
     */
    public static String getCurrentSessionKey() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getSessionKey() : null;
    }

    /**
     * 获取当前安全级别
     *
     * @return 安全级别
     */
    public static SecurityLevel getCurrentSecurityLevel() {
        SessionContext context = getCurrentContext();
        return context != null ? context.getSecurityLevel() : SecurityLevel.NORMAL;
    }

    /**
     * 设置会话属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public static void setAttribute(String key, Object value) {
        SessionContext context = getCurrentContext();
        if (context != null && StringUtils.hasText(key)) {
            context.getAttributes().put(key, value);
        }
    }

    /**
     * 获取会话属性
     *
     * @param key 属性键
     * @return 属性值
     */
    public static Object getAttribute(String key) {
        SessionContext context = getCurrentContext();
        if (context != null && StringUtils.hasText(key)) {
            return context.getAttributes().get(key);
        }
        return null;
    }

    /**
     * 移除会话属性
     *
     * @param key 属性键
     */
    public static void removeAttribute(String key) {
        SessionContext context = getCurrentContext();
        if (context != null && StringUtils.hasText(key)) {
            context.getAttributes().remove(key);
        }
    }

    /**
     * 更新最后访问时间
     */
    public static void updateLastAccessTime() {
        SessionContext context = getCurrentContext();
        if (context != null) {
            context.setLastAccessTime(Instant.now());
        }
    }

    /**
     * 升级安全级别
     *
     * @param newLevel 新的安全级别
     */
    public static void upgradeSecurityLevel(SecurityLevel newLevel) {
        SessionContext context = getCurrentContext();
        if (context != null && newLevel != null) {
            SecurityLevel currentLevel = context.getSecurityLevel();
            if (newLevel.getLevel() > currentLevel.getLevel()) {
                context.setSecurityLevel(newLevel);
                log.info("升级安全级别: sessionId={}, from={}, to={}", 
                        context.getSessionId(), currentLevel, newLevel);
            }
        }
    }

    /**
     * 清理当前上下文
     */
    public static void clearContext() {
        SessionContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            log.debug("清理会话上下文: sessionId={}", context.getSessionId());
        }
        CONTEXT_HOLDER.remove();
    }

    /**
     * 清理指定会话的上下文
     *
     * @param sessionId 会话ID
     */
    public static void clearContext(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            SESSION_CONTEXTS.remove(sessionId);
            log.debug("清理会话上下文: sessionId={}", sessionId);
        }
    }

    /**
     * 传播上下文到当前线程
     *
     * @param sessionId 会话ID
     */
    public static void propagateContext(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            SessionContext context = SESSION_CONTEXTS.get(sessionId);
            if (context != null) {
                CONTEXT_HOLDER.set(context);
                log.debug("传播会话上下文: sessionId={}", sessionId);
            }
        }
    }

    /**
     * 从Spring Security上下文同步
     */
    public static void syncFromSpringSecurityContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof GameUserDetails) {
                GameUserDetails userDetails = (GameUserDetails) authentication.getPrincipal();
                String sessionId = getCurrentSessionId();
                
                if (StringUtils.hasText(sessionId)) {
                    setContext(sessionId, userDetails, authentication.getAuthorities());
                }
            }
            
        } catch (Exception e) {
            log.error("从Spring Security上下文同步失败", e);
        }
    }

    /**
     * 确定安全级别
     *
     * @param userDetails 用户详情
     * @return 安全级别
     */
    private static SecurityLevel determineSecurityLevel(GameUserDetails userDetails) {
        if (userDetails == null) {
            return SecurityLevel.NORMAL;
        }
        
        // 根据用户角色和属性确定安全级别
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                String auth = authority.getAuthority();
                if ("ROLE_ADMIN".equals(auth) || "ROLE_SUPER_ADMIN".equals(auth)) {
                    return SecurityLevel.HIGH;
                }
                if ("ROLE_VIP".equals(auth) || "ROLE_PREMIUM".equals(auth)) {
                    return SecurityLevel.ELEVATED;
                }
            }
        }
        
        return SecurityLevel.NORMAL;
    }

    /**
     * 生成会话密钥
     *
     * @return 会话密钥
     */
    private static String generateSessionKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 会话上下文实体类
     */
    @Data
    @Builder
    public static class SessionContext {
        /**
         * 会话ID
         */
        private String sessionId;
        
        /**
         * 用户ID
         */
        private String userId;
        
        /**
         * 用户名
         */
        private String username;
        
        /**
         * 用户详情
         */
        private GameUserDetails userDetails;
        
        /**
         * 权限集合
         */
        private Set<GrantedAuthority> authorities;
        
        /**
         * 安全级别
         */
        private SecurityLevel securityLevel;
        
        /**
         * 创建时间
         */
        private Instant createTime;
        
        /**
         * 最后访问时间
         */
        private Instant lastAccessTime;
        
        /**
         * 会话密钥
         */
        private String sessionKey;
        
        /**
         * 会话属性
         */
        private Map<String, Object> attributes;
    }

    /**
     * 安全级别枚举
     */
    public enum SecurityLevel {
        /**
         * 普通级别
         */
        NORMAL(1, "普通"),
        
        /**
         * 提升级别
         */
        ELEVATED(2, "提升"),
        
        /**
         * 高级别
         */
        HIGH(3, "高级"),
        
        /**
         * 最高级别
         */
        CRITICAL(4, "关键");
        
        private final int level;
        private final String description;
        
        SecurityLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getDescription() {
            return description;
        }
    }
}