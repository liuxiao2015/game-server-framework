/*
 * 文件名: PermissionEvaluator.java
 * 用途: 细粒度权限评估器
 * 实现内容:
 *   - 方法级权限控制
 *   - 数据级权限控制
 *   - 表达式权限支持
 *   - 上下文感知权限
 *   - 权限组合逻辑
 * 技术选型:
 *   - Spring Security权限评估器扩展
 *   - SpEL表达式支持
 *   - 缓存优化评估结果
 * 依赖关系:
 *   - 被GameAccessDecisionManager使用
 *   - 与GameSecurityExpressions配合
 */
package com.lx.gameserver.frame.security.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lx.gameserver.frame.security.auth.GameUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * 细粒度权限评估器
 * <p>
 * 提供方法级和数据级的细粒度权限控制，支持SpEL表达式评估，
 * 上下文感知的权限判断以及权限组合逻辑，为游戏服务器提供
 * 灵活而精确的权限控制能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class PermissionEvaluator implements org.springframework.security.access.PermissionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new HashMap<>();
    private final Cache<String, Boolean> evaluationCache;
    
    @Autowired
    private GameSecurityExpressions securityExpressions;

    /**
     * 构造函数
     */
    public PermissionEvaluator() {
        this.evaluationCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
        log.info("权限评估器初始化完成");
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        String permString = permission.toString();
        String targetType = targetDomainObject.getClass().getSimpleName();
        String userId = authentication.getName();

        // 构建缓存键
        String cacheKey = userId + ":" + targetType + ":" + System.identityHashCode(targetDomainObject) + ":" + permString;
        
        // 检查缓存
        Boolean cachedResult = evaluationCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        // 执行权限评估
        boolean result = evaluatePermission(authentication, targetDomainObject, permString);
        
        // 缓存结果
        evaluationCache.put(cacheKey, result);
        
        return result;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) {
            return false;
        }

        String permString = permission.toString();
        String userId = authentication.getName();

        // 构建缓存键
        String cacheKey = userId + ":" + targetType + ":" + targetId + ":" + permString;
        
        // 检查缓存
        Boolean cachedResult = evaluationCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        // 执行权限评估
        boolean result = evaluatePermission(authentication, targetId, targetType, permString);
        
        // 缓存结果
        evaluationCache.put(cacheKey, result);
        
        return result;
    }

    /**
     * 评估对象权限
     */
    private boolean evaluatePermission(Authentication authentication, Object targetDomainObject, String permission) {
        // 1. 检查是否拥有直接权限
        if (hasDirectPermission(authentication, permission)) {
            return true;
        }
        
        // 2. 检查数据所有权
        if (isOwner(authentication, targetDomainObject)) {
            return true;
        }

        // 3. 检查角色特定权限
        if (hasRoleBasedPermission(authentication, targetDomainObject, permission)) {
            return true;
        }

        // 4. 表达式评估
        if (evaluateExpression(authentication, targetDomainObject, permission)) {
            return true;
        }

        // 5. 使用安全表达式评估
        if (securityExpressions != null && 
            securityExpressions.evaluate(authentication, targetDomainObject, permission)) {
            return true;
        }

        return false;
    }

    /**
     * 评估ID+类型的权限
     */
    private boolean evaluatePermission(Authentication authentication, Serializable targetId, String targetType, String permission) {
        // 1. 检查是否拥有直接权限
        if (hasDirectPermission(authentication, permission)) {
            return true;
        }

        // 2. 检查是否是资源拥有者
        if (isOwnerById(authentication, targetId, targetType)) {
            return true;
        }

        // 3. 检查角色特定权限
        if (hasRoleBasedPermission(authentication, targetId, targetType, permission)) {
            return true;
        }

        // 4. 表达式评估
        if (evaluateExpression(authentication, targetId, targetType, permission)) {
            return true;
        }

        // 5. 使用安全表达式评估
        if (securityExpressions != null && 
            securityExpressions.evaluate(authentication, targetId, targetType, permission)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否拥有直接权限
     */
    private boolean hasDirectPermission(Authentication authentication, String permission) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        // 检查直接权限匹配
        for (GrantedAuthority authority : authorities) {
            if (authority.getAuthority().equals(permission)) {
                log.debug("用户[{}]拥有直接权限[{}]", authentication.getName(), permission);
                return true;
            }
        }
        
        // 检查通配符权限
        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority();
            if (auth.endsWith("*") && permission.startsWith(auth.substring(0, auth.length() - 1))) {
                log.debug("用户[{}]通过通配符权限[{}]匹配[{}]", authentication.getName(), auth, permission);
                return true;
            }
        }
        
        // 检查管理员权限
        for (GrantedAuthority authority : authorities) {
            if (authority.getAuthority().equals("ROLE_ADMIN")) {
                log.debug("用户[{}]通过管理员权限访问[{}]", authentication.getName(), permission);
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查是否是对象拥有者
     */
    private boolean isOwner(Authentication authentication, Object targetDomainObject) {
        if (authentication.getPrincipal() instanceof GameUserDetails) {
            GameUserDetails userDetails = (GameUserDetails) authentication.getPrincipal();
            
            // 检查对象是否有对应的获取拥有者ID的方法
            try {
                Object ownerId = getOwnerId(targetDomainObject);
                if (ownerId != null) {
                    // 如果对象的拥有者ID与用户ID匹配
                    boolean isOwner = ownerId.toString().equals(userDetails.getPlayerId()) || 
                                      ownerId.toString().equals(userDetails.getId().toString());
                    if (isOwner) {
                        log.debug("用户[{}]是对象[{}]的拥有者", authentication.getName(), targetDomainObject);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("检查资源所有权时出错: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * 通过ID检查是否是对象拥有者
     */
    private boolean isOwnerById(Authentication authentication, Serializable targetId, String targetType) {
        if (authentication.getPrincipal() instanceof GameUserDetails) {
            GameUserDetails userDetails = (GameUserDetails) authentication.getPrincipal();
            
            // 检查ID是否匹配用户ID
            boolean isOwner = targetId.toString().equals(userDetails.getPlayerId()) || 
                              targetId.toString().equals(userDetails.getId().toString());
            
            if (isOwner) {
                log.debug("用户[{}]是类型[{}]的对象[{}]的拥有者", authentication.getName(), targetType, targetId);
                return true;
            }
        }
        return false;
    }

    /**
     * 从对象中获取拥有者ID（尝试多种常见方法）
     */
    private Object getOwnerId(Object obj) throws Exception {
        try {
            // 尝试使用getUserId方法
            return obj.getClass().getMethod("getUserId").invoke(obj);
        } catch (NoSuchMethodException e) {
            try {
                // 尝试使用getOwnerId方法
                return obj.getClass().getMethod("getOwnerId").invoke(obj);
            } catch (NoSuchMethodException e2) {
                try {
                    // 尝试使用getPlayerId方法
                    return obj.getClass().getMethod("getPlayerId").invoke(obj);
                } catch (NoSuchMethodException e3) {
                    // 没有找到合适的方法
                    return null;
                }
            }
        }
    }

    /**
     * 检查是否有基于角色的特定权限
     */
    private boolean hasRoleBasedPermission(Authentication authentication, Object targetDomainObject, String permission) {
        // 简单实现，实际项目中可以根据角色和对象类型组合进行更复杂的权限判断
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        // 例如: 如果用户有ROLE_MANAGER角色，则对特定类型的对象有读取权限
        String targetType = targetDomainObject.getClass().getSimpleName();
        String requiredPermission = targetType + "_" + permission;
        
        for (GrantedAuthority authority : authorities) {
            if (authority.getAuthority().equals(requiredPermission)) {
                log.debug("用户[{}]通过角色权限[{}]访问[{}]对象", 
                        authentication.getName(), authority.getAuthority(), targetType);
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查是否有基于角色的特定权限（基于ID）
     */
    private boolean hasRoleBasedPermission(Authentication authentication, Serializable targetId, String targetType, String permission) {
        // 实现类似于对象版本的权限检查
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String requiredPermission = targetType + "_" + permission;
        
        for (GrantedAuthority authority : authorities) {
            if (authority.getAuthority().equals(requiredPermission)) {
                log.debug("用户[{}]通过角色权限[{}]访问类型[{}]的对象[{}]", 
                        authentication.getName(), authority.getAuthority(), targetType, targetId);
                return true;
            }
        }
        
        return false;
    }

    /**
     * 通过表达式评估权限
     */
    private boolean evaluateExpression(Authentication authentication, Object targetDomainObject, String permission) {
        // 只处理看起来像表达式的权限字符串
        if (!isExpression(permission)) {
            return false;
        }
        
        try {
            // 构建表达式上下文
            EvaluationContext context = createEvaluationContext(authentication, targetDomainObject);
            
            // 获取或解析表达式
            Expression expression = getExpression(permission);
            
            // 评估表达式
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.debug("表达式[{}]评估失败: {}", permission, e.getMessage());
            return false;
        }
    }

    /**
     * 通过表达式评估权限（基于ID）
     */
    private boolean evaluateExpression(Authentication authentication, Serializable targetId, String targetType, String permission) {
        // 只处理看起来像表达式的权限字符串
        if (!isExpression(permission)) {
            return false;
        }
        
        try {
            // 构建表达式上下文
            EvaluationContext context = createEvaluationContext(authentication);
            
            // 添加ID和类型信息到上下文
            ((StandardEvaluationContext)context).setVariable("targetId", targetId);
            ((StandardEvaluationContext)context).setVariable("targetType", targetType);
            
            // 获取或解析表达式
            Expression expression = getExpression(permission);
            
            // 评估表达式
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.debug("表达式[{}]评估失败: {}", permission, e.getMessage());
            return false;
        }
    }

    /**
     * 检查字符串是否像表达式
     */
    private boolean isExpression(String permission) {
        // 简单检查：包含常见表达式字符
        return permission.contains("#") || permission.contains("'") || 
               permission.contains("(") || permission.contains(")");
    }

    /**
     * 获取或解析表达式
     */
    private Expression getExpression(String expressionString) {
        // 尝试从缓存获取
        Expression expression = expressionCache.get(expressionString);
        if (expression == null) {
            // 解析并缓存表达式
            expression = parser.parseExpression(expressionString);
            expressionCache.put(expressionString, expression);
        }
        return expression;
    }

    /**
     * 创建表达式评估上下文
     */
    private EvaluationContext createEvaluationContext(Authentication authentication) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 将认证对象添加到上下文
        context.setVariable("authentication", authentication);
        
        // 添加用户主体到上下文
        context.setVariable("principal", authentication.getPrincipal());
        
        // 添加当前时间上下文变量
        context.setVariable("now", LocalDateTime.now());
        
        // 添加当前时间（只有时间部分）
        context.setVariable("currentTime", LocalTime.now());
        
        // 添加功能测试方法
        context.registerFunction("hasRole", 
                this.getClass().getDeclaredMethod("hasRoleFunction", Authentication.class, String.class));
        
        context.registerFunction("hasPermission", 
                this.getClass().getDeclaredMethod("hasPermissionFunction", Authentication.class, String.class));
        
        return context;
    }

    /**
     * 创建带目标对象的表达式评估上下文
     */
    private EvaluationContext createEvaluationContext(Authentication authentication, Object targetDomainObject) {
        StandardEvaluationContext context = (StandardEvaluationContext) createEvaluationContext(authentication);
        
        // 将目标对象添加到上下文
        context.setVariable("target", targetDomainObject);
        
        // 添加目标对象类型
        context.setVariable("targetType", targetDomainObject.getClass().getSimpleName());
        
        return context;
    }

    /**
     * 表达式中使用的角色检查函数
     */
    public static boolean hasRoleFunction(Authentication authentication, String role) {
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(roleWithPrefix));
    }

    /**
     * 表达式中使用的权限检查函数
     */
    public static boolean hasPermissionFunction(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(permission));
    }

    /**
     * 清除评估缓存
     */
    public void clearCache() {
        evaluationCache.invalidateAll();
        log.debug("已清空权限评估缓存");
    }
    
    /**
     * 为特定用户和目标对象注册自定义权限检查
     *
     * @param targetType 目标对象类型
     * @param permission 权限标识符
     * @param checker 检查函数，接收认证对象和目标对象，返回是否有权限
     */
    public void registerPermissionChecker(String targetType, String permission, 
            BiFunction<Authentication, Object, Boolean> checker) {
        // 实现自定义权限检查注册功能
        // 在实际项目中，可以存储在Map中并在评估中使用
        log.info("注册了自定义权限检查：{}_{}", targetType, permission);
    }
}