/*
 * 文件名: GameLevelVoter.java
 * 用途: 游戏等级权限投票器
 * 实现内容:
 *   - 根据游戏等级判断权限
 *   - 等级限制功能控制
 *   - 特殊功能解锁
 * 技术选型:
 *   - Spring Security AccessDecisionVoter
 *   - 游戏等级权限判断
 * 依赖关系:
 *   - 被GameAccessDecisionManager使用
 *   - 用于游戏功能解锁控制
 */
package com.lx.gameserver.frame.security.authorization;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 游戏等级权限投票器
 * <p>
 * 根据玩家的游戏等级判断是否有权限访问某些游戏功能，
 * 用于实现游戏内功能的等级解锁限制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class GameLevelVoter implements AccessDecisionVoter<Object> {

    /**
     * 等级权限属性前缀
     */
    private static final String LEVEL_ATTRIBUTE_PREFIX = "GAME_LEVEL_";
    
    /**
     * 等级权限属性匹配模式
     */
    private static final Pattern LEVEL_PATTERN = Pattern.compile("GAME_LEVEL_(\\d+)");

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return attribute.getAttribute() != null && 
               attribute.getAttribute().startsWith(LEVEL_ATTRIBUTE_PREFIX);
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    public int vote(Authentication authentication, Object object, 
                   Collection<ConfigAttribute> attributes) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ACCESS_DENIED;
        }
        
        // 获取用户信息
        if (!(authentication.getPrincipal() instanceof GameUserDetails)) {
            // 非游戏用户，弃权
            return ACCESS_ABSTAIN;
        }
        
        GameUserDetails userDetails = (GameUserDetails) authentication.getPrincipal();
        int userGameLevel = userDetails.getGameLevel();
        
        // 检查是否有任何等级要求
        boolean hasLevelRequirement = false;
        int highestLevelRequired = 0;
        
        // 遍历所有权限属性
        for (ConfigAttribute attribute : attributes) {
            String attr = attribute.getAttribute();
            if (attr != null && attr.startsWith(LEVEL_ATTRIBUTE_PREFIX)) {
                hasLevelRequirement = true;
                
                // 解析所需的游戏等级
                Matcher matcher = LEVEL_PATTERN.matcher(attr);
                if (matcher.matches()) {
                    try {
                        int requiredLevel = Integer.parseInt(matcher.group(1));
                        highestLevelRequired = Math.max(highestLevelRequired, requiredLevel);
                    } catch (NumberFormatException e) {
                        log.warn("无效的游戏等级格式: {}", attr);
                    }
                }
            }
        }
        
        // 如果没有等级要求，弃权
        if (!hasLevelRequirement) {
            return ACCESS_ABSTAIN;
        }
        
        // 判断用户游戏等级是否满足要求
        if (userGameLevel >= highestLevelRequired) {
            log.debug("游戏等级权限验证通过: 玩家等级={}, 要求等级={}", userGameLevel, highestLevelRequired);
            return ACCESS_GRANTED;
        } else {
            log.debug("游戏等级权限验证失败: 玩家等级={}, 要求等级={}", userGameLevel, highestLevelRequired);
            return ACCESS_DENIED;
        }
    }
}