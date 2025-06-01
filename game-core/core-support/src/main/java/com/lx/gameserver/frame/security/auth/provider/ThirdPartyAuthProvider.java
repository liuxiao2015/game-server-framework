/*
 * 文件名: ThirdPartyAuthProvider.java
 * 用途: 第三方平台认证提供者
 * 实现内容:
 *   - 第三方平台认证逻辑
 *   - OAuth2认证支持
 *   - 不同平台适配
 * 技术选型:
 *   - Spring Security AuthenticationProvider
 *   - OAuth2客户端
 * 依赖关系:
 *   - 被GameAuthenticationManager使用
 *   - 使用OAuth2集成服务
 */
package com.lx.gameserver.frame.security.auth.provider;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import com.lx.gameserver.frame.security.integration.OAuth2Integration;
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

import java.util.Map;

/**
 * 第三方平台认证提供者
 * <p>
 * 实现第三方平台账号认证，如微信、QQ、Google等平台的
 * OAuth2认证，支持多种第三方平台，自动适配和转换用户信息。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThirdPartyAuthProvider implements AuthenticationProvider {

    private final OAuth2Integration oAuth2Integration;
    private final UserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 预期认证信息中包含平台名称、授权码或令牌等信息
        Map<String, String> authInfo = (Map<String, String>) authentication.getDetails();
        if (authInfo == null) {
            log.debug("第三方认证信息为空");
            throw new BadCredentialsException("缺少第三方认证信息");
        }

        String platform = authInfo.get("platform");
        String code = authInfo.get("code");
        String token = authInfo.get("token");

        if (!StringUtils.hasText(platform)) {
            log.debug("平台名称为空");
            throw new BadCredentialsException("缺少平台名称");
        }

        try {
            // 根据平台类型选择不同的认证流程
            String userId;
            if (StringUtils.hasText(code)) {
                // 使用授权码方式
                userId = oAuth2Integration.authenticateWithCode(platform, code, authInfo);
            } else if (StringUtils.hasText(token)) {
                // 使用令牌方式
                userId = oAuth2Integration.authenticateWithToken(platform, token, authInfo);
            } else {
                log.debug("缺少授权码或令牌");
                throw new BadCredentialsException("缺少授权码或令牌");
            }

            if (!StringUtils.hasText(userId)) {
                log.debug("第三方认证失败，未获取到用户ID");
                throw new BadCredentialsException("第三方认证失败");
            }

            // 构建第三方平台用户名（平台前缀+用户ID）
            String username = platform + ":" + userId;

            // 加载或创建用户（在实际应用中，这里可能需要处理用户不存在的情况）
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (Exception e) {
                log.info("第三方用户首次登录，需要创建用户: {}", username);
                // 实际项目中应该在这里处理用户创建逻辑
                // 简化示例，抛出异常
                throw new BadCredentialsException("用户未注册");
            }

            // 构造已认证的认证对象
            UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // 如果是GameUserDetails，设置会话状态
            if (userDetails instanceof GameUserDetails) {
                ((GameUserDetails) userDetails).setSessionStatus("ONLINE");
                // 记录登录平台信息
                ((GameUserDetails) userDetails).setAttribute("loginPlatform", platform);
            }

            log.debug("第三方认证成功: {} 平台: {}", username, platform);
            return result;

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("第三方认证处理异常", e);
            throw new BadCredentialsException("第三方认证失败: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // 注意：实际项目中应该为第三方认证创建专门的认证类型
        // 这里简化为UsernamePasswordAuthenticationToken
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}