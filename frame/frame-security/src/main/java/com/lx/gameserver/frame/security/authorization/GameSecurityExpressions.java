/*
 * 文件名: GameSecurityExpressions.java
 * 用途: 游戏安全表达式
 * 实现内容:
 *   - 自定义SpEL表达式方法
 *   - 等级限制表达式
 *   - VIP权限表达式
 *   - 时间限制表达式
 *   - 区服限制表达式
 * 技术选型:
 *   - Spring Security SpEL
 *   - 方法权限注解支持
 * 依赖关系:
 *   - 被@PreAuthorize等注解使用
 *   - 用于声明式权限控制
 */
package com.lx.gameserver.frame.security.authorization;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 游戏安全表达式
 * <p>
 * 提供一系列游戏专用的安全表达式方法，用于在注解中
 * 声明权限控制规则，如等级限制、VIP权限、时间限制等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class GameSecurityExpressions {

    /**
     * 检查是否满足游戏等级要求
     *
     * @param minLevel 最低等级要求
     * @return 是否满足等级要求
     */
    public boolean hasGameLevel(int minLevel) {
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            return false;
        }
        
        boolean result = user.getGameLevel() >= minLevel;
        if (!result) {
            log.debug("等级检查失败: 当前={}, 要求={}", user.getGameLevel(), minLevel);
        }
        return result;
    }
    
    /**
     * 检查是否满足VIP等级要求
     *
     * @param minVipLevel 最低VIP等级要求
     * @return 是否满足VIP等级要求
     */
    public boolean hasVipLevel(int minVipLevel) {
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            return false;
        }
        
        boolean result = user.getVipLevel() >= minVipLevel;
        if (!result) {
            log.debug("VIP等级检查失败: 当前={}, 要求={}", user.getVipLevel(), minVipLevel);
        }
        return result;
    }
    
    /**
     * 检查是否在指定时间范围内
     *
     * @param startHour 开始小时(0-23)
     * @param endHour 结束小时(0-23)
     * @return 是否在时间范围内
     */
    public boolean isTimeBetween(int startHour, int endHour) {
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(startHour, 0);
        LocalTime end = LocalTime.of(endHour, 0);
        
        boolean result = !now.isBefore(start) && now.isBefore(end);
        if (!result) {
            log.debug("时间范围检查失败: 当前={}, 范围={}-{}", now, start, end);
        }
        return result;
    }
    
    /**
     * 检查是否在活动日
     *
     * @param days 活动日（星期几，1-7代表周一到周日）
     * @return 是否为活动日
     */
    public boolean isActiveDay(Integer... days) {
        if (days == null || days.length == 0) {
            return false;
        }
        
        Set<Integer> activeDays = new HashSet<>(Arrays.asList(days));
        int today = LocalDate.now().getDayOfWeek().getValue(); // 1-7
        
        boolean result = activeDays.contains(today);
        if (!result) {
            log.debug("活动日检查失败: 今天={}, 活动日={}", today, activeDays);
        }
        return result;
    }
    
    /**
     * 检查是否在指定区服列表中
     *
     * @param serverIds 区服ID列表
     * @return 是否在指定区服中
     */
    public boolean isInServers(String... serverIds) {
        if (serverIds == null || serverIds.length == 0) {
            return false;
        }
        
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            return false;
        }
        
        String userServerId = user.getServerId();
        boolean result = Arrays.asList(serverIds).contains(userServerId);
        if (!result) {
            log.debug("区服检查失败: 当前={}, 允许={}", userServerId, Arrays.toString(serverIds));
        }
        return result;
    }
    
    /**
     * 检查是否有指定权限
     *
     * @param permission 权限标识
     * @return 是否有权限
     */
    public boolean hasGamePermission(String permission) {
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            return false;
        }
        
        boolean result = user.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(permission));
        
        if (!result) {
            log.debug("权限检查失败: {} 缺少权限 {}", user.getUsername(), permission);
        }
        return result;
    }
    
    /**
     * 检查是否是指定角色的拥有者
     *
     * @param characterId 角色ID
     * @return 是否是拥有者
     */
    public boolean isCharacterOwner(String characterId) {
        if (characterId == null) {
            return false;
        }
        
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            return false;
        }
        
        // 这里简化处理，假设角色ID与玩家ID匹配即为拥有者
        // 实际项目中应该查询数据库或调用专门的服务
        boolean result = characterId.equals(user.getPlayerId());
        if (!result) {
            log.debug("角色所有权检查失败: 用户={}, 角色ID={}", user.getPlayerId(), characterId);
        }
        return result;
    }
    
    /**
     * 检查功能是否已解锁
     *
     * @param featureId 功能ID
     * @return 是否已解锁
     */
    public boolean isFeatureUnlocked(String featureId) {
        // 实际项目中应该查询玩家的功能解锁状态
        // 这里仅作示例实现
        
        GameUserDetails user = getCurrentGameUser();
        if (user == null) {
            return false;
        }
        
        // 假设功能有不同的解锁条件
        boolean result = switch (featureId) {
            case "pvp" -> user.getGameLevel() >= 10;
            case "guild" -> user.getGameLevel() >= 20;
            case "raid" -> user.getGameLevel() >= 30;
            case "auction" -> user.getGameLevel() >= 25;
            default -> false;
        };
        
        if (!result) {
            log.debug("功能解锁检查失败: 用户={}, 功能={}", user.getUsername(), featureId);
        }
        return result;
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