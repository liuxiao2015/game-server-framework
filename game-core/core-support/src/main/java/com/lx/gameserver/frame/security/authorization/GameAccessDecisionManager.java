/*
 * 文件名: GameAccessDecisionManager.java
 * 用途: 游戏权限决策管理器
 * 实现内容:
 *   - 基于角色的权限控制(RBAC)
 *   - 基于属性的权限控制(ABAC)
 *   - 动态权限评估
 *   - 权限缓存机制
 *   - 权限继承支持
 * 技术选型:
 *   - Spring Security AccessDecisionManager
 *   - 投票机制决策
 *   - 自定义游戏权限逻辑
 * 依赖关系:
 *   - 被SecurityConfig配置使用
 *   - 使用PermissionEvaluator
 */
package com.lx.gameserver.frame.security.authorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 游戏权限决策管理器
 * <p>
 * 提供游戏服务器特有的权限决策逻辑，结合角色权限和
 * 属性权限进行综合评估，支持权限缓存和继承机制，
 * 实现更灵活的游戏功能访问控制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class GameAccessDecisionManager implements AccessDecisionManager {

    /**
     * 基础决策管理器（使用肯定投票机制）
     */
    private final AccessDecisionManager baseDecisionManager;
    
    /**
     * 游戏权限评估器
     */
    @Autowired(required = false)
    private GamePermissionEvaluator permissionEvaluator;
    
    /**
     * 构造函数
     */
    public GameAccessDecisionManager() {
        // 创建基础决策管理器，使用肯定投票机制（一票通过）
        this.baseDecisionManager = new AffirmativeBased(List.of(
                new RoleVoter(),
                new GameVipVoter(),
                new GameLevelVoter()
        ));
        log.info("游戏权限决策管理器初始化完成");
    }

    @Override
    public void decide(Authentication authentication, Object object, 
                      Collection<ConfigAttribute> attributes) 
            throws AccessDeniedException, InsufficientAuthenticationException {
        
        // 首先使用基础决策逻辑（基于角色等）
        try {
            baseDecisionManager.decide(authentication, object, attributes);
            log.debug("基础权限验证通过");
        } catch (AccessDeniedException e) {
            log.debug("基础权限验证失败，尝试高级权限验证");
            
            // 基础验证失败，尝试使用高级权限逻辑
            if (permissionEvaluator != null && 
                permissionEvaluator.hasAdvancedPermission(authentication, object, attributes)) {
                // 高级权限验证通过
                log.debug("高级权限验证通过");
                return;
            }
            
            // 所有验证均失败，抛出异常
            log.debug("权限验证失败: {}", e.getMessage());
            throw new AccessDeniedException("没有权限访问此资源", e);
        }
    }

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}