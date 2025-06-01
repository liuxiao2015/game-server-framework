/*
 * 文件名: GameUserDetails.java
 * 用途: 游戏用户认证信息
 * 实现内容:
 *   - 玩家ID与角色信息
 *   - 权限集合管理
 *   - 登录设备信息
 *   - 会话状态追踪
 *   - 账号安全等级
 * 技术选型:
 *   - 实现Spring Security UserDetails接口
 *   - 自定义扩展游戏用户属性
 * 依赖关系:
 *   - 被GameAuthenticationManager使用
 *   - 被各种AuthenticationProvider使用
 */
package com.lx.gameserver.frame.security.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 游戏用户认证信息
 * <p>
 * 包含玩家的身份认证信息、权限集合、角色信息以及
 * 游戏特有的会话状态和设备信息等。作为游戏安全认证
 * 系统的核心实体类，用于构建和验证用户身份。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Getter
@ToString(exclude = {"password"})
public class GameUserDetails implements UserDetails, CredentialsContainer {

    /**
     * 用户ID（数据库主键）
     */
    private final Long id;
    
    /**
     * 玩家UID（游戏内唯一标识，可能与数据库主键不同）
     */
    private final String playerId;
    
    /**
     * 用户名
     */
    private final String username;
    
    /**
     * 密码
     */
    @JsonIgnore
    private String password;
    
    /**
     * 账号是否过期
     */
    private final boolean accountNonExpired;
    
    /**
     * 账号是否锁定
     */
    private final boolean accountNonLocked;
    
    /**
     * 凭据是否过期
     */
    private final boolean credentialsNonExpired;
    
    /**
     * 账号是否启用
     */
    private final boolean enabled;
    
    /**
     * 权限集合
     */
    private final Set<GrantedAuthority> authorities;
    
    /**
     * 玩家角色信息
     */
    private final String role;
    
    /**
     * 玩家VIP等级
     */
    private final int vipLevel;
    
    /**
     * 玩家游戏等级
     */
    private final int gameLevel;
    
    /**
     * 玩家区服ID
     */
    private final String serverId;
    
    /**
     * 登录设备信息
     */
    private final String deviceInfo;
    
    /**
     * 设备唯一标识
     */
    private final String deviceId;
    
    /**
     * 登录IP地址
     */
    private final String ipAddress;
    
    /**
     * 最后登录时间
     */
    private final LocalDateTime lastLoginTime;
    
    /**
     * 会话状态（在线、离线、游戏中等）
     */
    private String sessionStatus;
    
    /**
     * 安全等级（标准、高风险、可信等）
     */
    private final String securityLevel;
    
    /**
     * 额外属性
     */
    private final Map<String, Object> attributes;

    @Builder
    public GameUserDetails(
            Long id, 
            String playerId, 
            String username, 
            String password, 
            boolean accountNonExpired,
            boolean accountNonLocked, 
            boolean credentialsNonExpired, 
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities, 
            String role, 
            int vipLevel, 
            int gameLevel,
            String serverId, 
            String deviceInfo, 
            String deviceId, 
            String ipAddress,
            LocalDateTime lastLoginTime, 
            String sessionStatus, 
            String securityLevel,
            Map<String, Object> attributes) {
        
        this.id = id;
        this.playerId = playerId;
        this.username = username;
        this.password = password;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;
        this.authorities = authorities != null ? new HashSet<>(authorities) : new HashSet<>();
        this.role = role;
        this.vipLevel = vipLevel;
        this.gameLevel = gameLevel;
        this.serverId = serverId;
        this.deviceInfo = deviceInfo;
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.lastLoginTime = lastLoginTime;
        this.sessionStatus = sessionStatus;
        this.securityLevel = securityLevel;
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }
    
    /**
     * 设置会话状态
     *
     * @param sessionStatus 新的会话状态
     */
    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }
    
    /**
     * 添加一个用户权限
     *
     * @param authority 权限字符串
     */
    public void addAuthority(String authority) {
        this.authorities.add(new SimpleGrantedAuthority(authority));
    }
    
    /**
     * 添加多个用户权限
     *
     * @param authorities 权限字符串集合
     */
    public void addAuthorities(Collection<String> authorities) {
        authorities.forEach(authority -> this.authorities.add(new SimpleGrantedAuthority(authority)));
    }
    
    /**
     * 获取属性值
     *
     * @param key 属性键
     * @return 属性值，不存在则返回null
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    /**
     * 设置属性值
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public String getUserId() {
        return this.id != null ? this.id.toString() : null;
    }

    /**
     * 创建游戏用户明细构建器
     *
     * @return 构建器
     */
    public static GameUserDetailsBuilder builder() {
        return new GameUserDetailsBuilder();
    }
}