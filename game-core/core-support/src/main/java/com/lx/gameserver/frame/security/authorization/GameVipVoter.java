/*
 * 文件名: GameVipVoter.java
 * 用途: 游戏VIP权限投票器
 * 实现内容:
 *   - 根据VIP等级判断权限
 *   - 支持VIP等级要求注解
 *   - 权限属性解析
 * 技术选型:
 *   - Spring Security AccessDecisionVoter
 *   - 自定义权限判断逻辑
 * 依赖关系:
 *   - 被GameAccessDecisionManager使用
 *   - 用于VIP功能权限控制
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
 * 游戏VIP权限投票器
 * <p>
 * 根据用户的VIP等级判断是否有权限访问受限资源，
 * 支持基于VIP等级的细粒度权限控制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class GameVipVoter implements AccessDecisionVoter<Object> {

    /**
     * VIP权限属性前缀
     */
    private static final String VIP_ATTRIBUTE_PREFIX = "VIP_LEVEL_";
    
    /**
     * VIP权限属性匹配模式
     */
    private static final Pattern VIP_PATTERN = Pattern.compile("VIP_LEVEL_(\\d+)");

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return attribute.getAttribute() != null && 
               attribute.getAttribute().startsWith(VIP_ATTRIBUTE_PREFIX);
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
        int userVipLevel = userDetails.getVipLevel();
        
        // 检查是否有任何VIP级别要求
        boolean hasVipRequirement = false;
        int highestVipRequired = 0;
        
        // 遍历所有权限属性
        for (ConfigAttribute attribute : attributes) {
            String attr = attribute.getAttribute();
            if (attr != null && attr.startsWith(VIP_ATTRIBUTE_PREFIX)) {
                hasVipRequirement = true;
                
                // 解析所需的VIP等级
                Matcher matcher = VIP_PATTERN.matcher(attr);
                if (matcher.matches()) {
                    try {
                        int requiredVipLevel = Integer.parseInt(matcher.group(1));
                        highestVipRequired = Math.max(highestVipRequired, requiredVipLevel);
                    } catch (NumberFormatException e) {
                        log.warn("无效的VIP级别格式: {}", attr);
                    }
                }
            }
        }
        
        // 如果没有VIP要求，弃权
        if (!hasVipRequirement) {
            return ACCESS_ABSTAIN;
        }
        
        // 判断用户VIP等级是否满足要求
        if (userVipLevel >= highestVipRequired) {
            log.debug("VIP权限验证通过: 用户等级={}, 要求等级={}", userVipLevel, highestVipRequired);
            return ACCESS_GRANTED;
        } else {
            log.debug("VIP权限验证失败: 用户等级={}, 要求等级={}", userVipLevel, highestVipRequired);
            return ACCESS_DENIED;
        }
    }
}