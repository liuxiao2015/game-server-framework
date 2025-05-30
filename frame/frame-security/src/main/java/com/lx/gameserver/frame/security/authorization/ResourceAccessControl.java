/*
 * 文件名: ResourceAccessControl.java
 * 用途: 资源访问控制
 * 实现内容:
 *   - API接口权限
 *   - 游戏功能权限
 *   - 数据访问权限
 *   - 操作频率限制
 *   - 资源配额管理
 * 技术选型:
 *   - Spring AOP拦截
 *   - 自定义注解
 *   - 缓存策略
 * 依赖关系:
 *   - 被Controller层、Service层使用
 *   - 与GamePermissionEvaluator配合
 */
package com.lx.gameserver.frame.security.authorization;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import com.lx.gameserver.frame.security.protection.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源访问控制
 * <p>
 * 提供对游戏资源的细粒度访问控制，包括API接口权限、
 * 游戏功能权限、数据访问权限等。结合AOP实现非侵入式
 * 权限控制，支持操作频率限制和资源配额管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ResourceAccessControl {

    /**
     * 游戏权限评估器
     */
    private final GamePermissionEvaluator permissionEvaluator;
    
    /**
     * 限流器
     */
    private final RateLimiter rateLimiter;
    
    /**
     * 用户资源配额缓存
     * Key: 用户ID + 资源类型
     * Value: 已使用配额
     */
    private final Map<String, Integer> quotaCache = new ConcurrentHashMap<>();
    
    /**
     * 资源配额定义
     * Key: 资源类型
     * Value: 最大配额
     */
    private final Map<String, Integer> resourceQuota = new HashMap<>();
    
    /**
     * 构造函数，初始化资源配额
     */
    {
        // 初始化默认资源配额
        resourceQuota.put("api.request", 1000);  // 每日API请求数
        resourceQuota.put("item.create", 100);   // 每日创建物品数
        resourceQuota.put("mail.send", 50);      // 每日发送邮件数
        resourceQuota.put("trade.initiate", 20); // 每日发起交易数
    }
    
    /**
     * 拦截需要功能权限控制的方法
     */
    @Around("@annotation(com.lx.gameserver.frame.security.authorization.annotation.RequiresGameFeature)")
    public Object checkFeatureAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        // 由于简化实现，这里省略了注解参数获取，实际项目中需要实现
        String featureId = "someFeature"; // 示例值
        int minLevel = 1; // 示例值
        
        // 获取当前用户信息
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            throw new AccessDeniedException("未认证的访问");
        }
        
        // 检查游戏等级要求
        if (user.getGameLevel() < minLevel) {
            log.debug("功能访问拒绝: 玩家等级不足 当前={}, 要求={}", user.getGameLevel(), minLevel);
            throw new AccessDeniedException("等级不足，无法访问该功能");
        }
        
        // 检查功能是否解锁
        if (!isFeatureUnlocked(user, featureId)) {
            log.debug("功能访问拒绝: 功能未解锁 玩家={}, 功能={}", user.getUsername(), featureId);
            throw new AccessDeniedException("功能未解锁");
        }
        
        // 功能访问前处理
        // 可以添加日志、计数等逻辑
        
        // 执行原方法
        return joinPoint.proceed();
    }
    
    /**
     * 拦截需要资源配额控制的方法
     */
    @Around("@annotation(com.lx.gameserver.frame.security.authorization.annotation.ResourceQuota)")
    public Object checkResourceQuota(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        // 由于简化实现，这里省略了注解参数获取，实际项目中需要实现
        String resourceType = "api.request"; // 示例值
        int amount = 1; // 示例值
        
        // 获取当前用户信息
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            throw new AccessDeniedException("未认证的访问");
        }
        
        // 检查资源配额
        if (!checkAndUpdateQuota(user.getId(), resourceType, amount)) {
            log.debug("资源配额超限: 用户={}, 资源类型={}", user.getUsername(), resourceType);
            throw new AccessDeniedException("超出资源配额限制");
        }
        
        // 执行原方法
        return joinPoint.proceed();
    }
    
    /**
     * 拦截需要限流控制的方法
     */
    @Around("@annotation(com.lx.gameserver.frame.security.authorization.annotation.RateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        // 由于简化实现，这里省略了注解参数获取，实际项目中需要实现
        String key = "default"; // 示例值
        int limit = 10; // 示例值
        
        // 获取当前用户信息
        GameUserDetails user = getCurrentGameUser();
        String userId = user != null ? user.getId().toString() : "anonymous";
        
        // 应用限流检查
        if (!rateLimiter.tryAcquire(key, userId, limit)) {
            log.debug("限流控制: 操作频率过高 用户={}, 资源={}", userId, key);
            throw new AccessDeniedException("操作频率过高，请稍后再试");
        }
        
        // 执行原方法
        return joinPoint.proceed();
    }
    
    /**
     * 检查并更新资源配额
     *
     * @param userId 用户ID
     * @param resourceType 资源类型
     * @param amount 使用数量
     * @return 是否在配额范围内
     */
    private boolean checkAndUpdateQuota(Long userId, String resourceType, int amount) {
        if (userId == null || resourceType == null || amount <= 0) {
            return false;
        }
        
        // 获取配额上限
        Integer maxQuota = resourceQuota.get(resourceType);
        if (maxQuota == null) {
            // 未定义配额的资源类型默认允许
            return true;
        }
        
        String quotaKey = userId + ":" + resourceType;
        
        // 获取已使用配额
        Integer usedQuota = quotaCache.getOrDefault(quotaKey, 0);
        
        // 检查是否超出配额
        if (usedQuota + amount > maxQuota) {
            return false;
        }
        
        // 更新配额使用
        quotaCache.put(quotaKey, usedQuota + amount);
        return true;
    }
    
    /**
     * 重置用户资源配额
     *
     * @param userId 用户ID
     */
    public void resetUserQuota(Long userId) {
        if (userId == null) {
            return;
        }
        
        String prefix = userId + ":";
        quotaCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("已重置用户资源配额: {}", userId);
    }
    
    /**
     * 检查功能是否已解锁
     *
     * @param user 用户信息
     * @param featureId 功能ID
     * @return 功能是否已解锁
     */
    private boolean isFeatureUnlocked(GameUserDetails user, String featureId) {
        // 实际项目中应该查询玩家的功能解锁状态
        // 这里仅作示例实现
        return switch (featureId) {
            case "pvp" -> user.getGameLevel() >= 10;
            case "guild" -> user.getGameLevel() >= 20;
            case "raid" -> user.getGameLevel() >= 30;
            case "auction" -> user.getGameLevel() >= 25;
            default -> false;
        };
    }
    
    /**
     * 获取当前游戏用户
     *
     * @return 当前游戏用户，如果不是游戏用户则返回null
     */
    private GameUserDetails getCurrentGameUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof GameUserDetails)) {
            return null;
        }
        
        return (GameUserDetails) principal;
    }
}