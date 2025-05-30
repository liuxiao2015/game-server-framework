/*
 * 文件名: GamePermissionEvaluator.java
 * 用途: 游戏权限评估器
 * 实现内容:
 *   - 细粒度权限评估
 *   - 方法级权限控制
 *   - 数据级权限控制
 *   - 表达式权限支持
 *   - 上下文感知权限
 * 技术选型:
 *   - Spring Security PermissionEvaluator
 *   - SpEL表达式支持
 *   - 游戏特化权限逻辑
 * 依赖关系:
 *   - 被GameAccessDecisionManager使用
 *   - 用于@PreAuthorize注解权限检查
 */
package com.lx.gameserver.frame.security.authorization;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 游戏权限评估器
 * <p>
 * 提供细粒度的游戏权限评估，支持方法级权限控制和
 * 数据级权限控制，能够根据上下文和游戏状态动态评估权限。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class GamePermissionEvaluator {

    /**
     * 权限缓存 - 用于提高权限检查性能
     * Key: 用户ID + 权限标识
     * Value: 是否有权限
     */
    private final Map<String, Boolean> permissionCache = new ConcurrentHashMap<>();

    /**
     * 检查高级权限
     *
     * @param authentication 认证信息
     * @param targetObject 目标对象
     * @param attributes 配置属性
     * @return 如果有权限返回true，否则返回false
     */
    public boolean hasAdvancedPermission(Authentication authentication, Object targetObject, 
                                     Collection<ConfigAttribute> attributes) {
        if (authentication == null || !authentication.isAuthenticated() || 
            !(authentication.getPrincipal() instanceof GameUserDetails)) {
            return false;
        }

        GameUserDetails userDetails = (GameUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getId();
        
        // 收集用户所有权限
        Set<String> userPermissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
                
        if (CollectionUtils.isEmpty(userPermissions)) {
            return false;
        }
        
        // 检查每个属性是否满足
        for (ConfigAttribute attribute : attributes) {
            String permission = attribute.getAttribute();
            if (permission == null) {
                continue;
            }
            
            // 检查缓存
            String cacheKey = userId + ":" + permission;
            Boolean cachedResult = permissionCache.get(cacheKey);
            if (cachedResult != null) {
                if (cachedResult) {
                    log.debug("使用缓存权限检查结果: 有权限访问 {}", permission);
                    return true;
                }
                continue;
            }
            
            // 检查用户是否有此权限
            if (userPermissions.contains(permission)) {
                log.debug("权限验证通过: {}", permission);
                permissionCache.put(cacheKey, true);
                return true;
            }
            
            // 检查权限继承关系
            if (hasInheritedPermission(userPermissions, permission)) {
                log.debug("继承权限验证通过: {}", permission);
                permissionCache.put(cacheKey, true);
                return true;
            }
            
            // 缓存无权限结果
            permissionCache.put(cacheKey, false);
        }
        
        return false;
    }
    
    /**
     * 检查是否有对象的特定权限
     *
     * @param authentication 认证信息
     * @param targetId 目标对象ID
     * @param targetType 目标对象类型
     * @param permission 权限名称
     * @return 如果有权限返回true，否则返回false
     */
    public boolean hasPermission(Authentication authentication, Serializable targetId, 
                               String targetType, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        // 获取用户信息
        if (!(authentication.getPrincipal() instanceof GameUserDetails)) {
            return false;
        }
        
        GameUserDetails userDetails = (GameUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getId();
        
        // 构建缓存键
        String cacheKey = userId + ":" + targetType + ":" + targetId + ":" + permission;
        
        // 检查缓存
        Boolean cachedResult = permissionCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // 根据不同对象类型检查权限
        boolean hasPermission = switch (targetType.toLowerCase()) {
            case "item" -> checkItemPermission(userDetails, targetId, permission);
            case "character" -> checkCharacterPermission(userDetails, targetId, permission);
            case "guild" -> checkGuildPermission(userDetails, targetId, permission);
            default -> false;
        };
        
        // 缓存结果
        permissionCache.put(cacheKey, hasPermission);
        
        return hasPermission;
    }
    
    /**
     * 清除用户权限缓存
     *
     * @param userId 用户ID
     */
    public void clearPermissionCache(Long userId) {
        if (userId == null) {
            return;
        }
        
        String prefix = userId + ":";
        permissionCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("已清除用户权限缓存: {}", userId);
    }
    
    /**
     * 检查是否有继承的权限
     *
     * @param userPermissions 用户权限集合
     * @param requiredPermission 所需权限
     * @return 如果有权限返回true，否则返回false
     */
    private boolean hasInheritedPermission(Set<String> userPermissions, String requiredPermission) {
        // 此处可以实现权限继承关系检查
        // 例如：
        // - admin 角色继承了所有权限
        // - moderator 角色继承了部分权限
        
        // 管理员拥有所有权限
        if (userPermissions.contains("ROLE_ADMIN")) {
            return true;
        }
        
        // 特定权限继承实现示例
        if (requiredPermission.startsWith("game:read:") && userPermissions.contains("game:read:all")) {
            return true;
        }
        
        if (requiredPermission.startsWith("game:write:") && userPermissions.contains("game:write:all")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查物品权限
     */
    private boolean checkItemPermission(GameUserDetails user, Serializable itemId, String permission) {
        // 根据物品ID和权限类型检查用户是否有权限
        // 实际项目中应该查询数据库或调用相关服务
        
        // 示例实现
        return "use".equals(permission) || "view".equals(permission);
    }
    
    /**
     * 检查角色权限
     */
    private boolean checkCharacterPermission(GameUserDetails user, Serializable characterId, String permission) {
        // 实际项目中应该查询数据库或调用相关服务验证用户是否拥有该角色
        
        // 示例实现 - 判断是否是自己的角色
        String playerId = user.getPlayerId();
        String charId = characterId.toString();
        
        // 用户可以访问自己的角色
        if (playerId.equals(charId)) {
            return true;
        }
        
        // 其他控制逻辑
        return false;
    }
    
    /**
     * 检查公会权限
     */
    private boolean checkGuildPermission(GameUserDetails user, Serializable guildId, String permission) {
        // 实际项目中应该查询公会成员关系和权限等级
        
        // 示例实现 - 简单判断
        return "view".equals(permission);
    }
}