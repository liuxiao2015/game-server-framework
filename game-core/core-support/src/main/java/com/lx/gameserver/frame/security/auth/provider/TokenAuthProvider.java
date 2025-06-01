/*
 * 文件名: TokenAuthProvider.java
 * 用途: Token认证提供者
 * 实现内容:
 *   - JWT Token验证
 *   - 从Token获取用户信息
 *   - Token有效性检查
 * 技术选型:
 *   - Spring Security AuthenticationProvider
 *   - JWT Token验证
 * 依赖关系:
 *   - 被GameAuthenticationManager使用
 *   - 使用TokenService验证Token
 */
package com.lx.gameserver.frame.security.auth.provider;

import com.lx.gameserver.frame.security.auth.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Token认证提供者
 * <p>
 * 实现基于JWT Token的认证，负责验证Token有效性，
 * 从Token中提取用户信息，是无状态认证的核心组件。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthProvider implements AuthenticationProvider {

    private final TokenService tokenService;
    private final UserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 获取Token（预期认证凭证中存储的是Token字符串）
        String token = (String) authentication.getCredentials();
        if (!StringUtils.hasText(token)) {
            log.debug("Token为空");
            throw new BadCredentialsException("无效的Token");
        }

        // 验证Token有效性
        if (!tokenService.validateToken(token)) {
            log.debug("无效的Token");
            throw new BadCredentialsException("无效或过期的Token");
        }

        try {
            // 从Token中获取用户信息
            Claims claims = tokenService.getClaimsFromToken(token);
            String username = claims.getSubject();
            String tokenType = (String) claims.get("type");

            // 检查是否是访问Token
            if (!"ACCESS".equals(tokenType)) {
                log.debug("非访问Token类型: {}", tokenType);
                throw new BadCredentialsException("无效的Token类型");
            }

            // 加载用户详情
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 创建已认证的认证对象
            UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // 设置详细信息
            result.setDetails(authentication.getDetails());

            log.debug("Token认证成功: {}", username);
            return result;

        } catch (Exception e) {
            log.error("Token认证处理失败", e);
            throw new BadCredentialsException("Token处理失败: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // 注意：由于JWT通常通过HTTP头传递，这里假设使用了自定义的Token认证类型
        // 实际项目中可能需要定义专门的JwtAuthenticationToken类
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}