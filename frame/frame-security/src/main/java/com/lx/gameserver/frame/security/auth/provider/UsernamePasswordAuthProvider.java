/*
 * 文件名: UsernamePasswordAuthProvider.java
 * 用途: 账号密码认证提供者
 * 实现内容:
 *   - 用户名密码认证逻辑
 *   - 支持加密密码验证
 *   - 支持账号状态检查
 * 技术选型:
 *   - Spring Security AuthenticationProvider
 *   - BCrypt密码编码
 * 依赖关系:
 *   - 被GameAuthenticationManager使用
 *   - 使用UserDetailsService获取用户
 */
package com.lx.gameserver.frame.security.auth.provider;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 用户名密码认证提供者
 * <p>
 * 实现基于用户名和密码的标准认证，支持密码加密验证和
 * 账号状态检查，是最基础的认证方式之一。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class UsernamePasswordAuthProvider implements AuthenticationProvider {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 获取认证信息
        String username = authentication.getName();
        String password = (String) authentication.getCredentials();

        log.debug("处理用户名密码认证: {}", username);

        // 加载用户信息
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            log.debug("用户不存在: {}", username);
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 检查账号状态
        if (!userDetails.isEnabled()) {
            log.debug("账号已禁用: {}", username);
            throw new DisabledException("账号已被禁用");
        }

        if (!userDetails.isAccountNonLocked()) {
            log.debug("账号已锁定: {}", username);
            throw new LockedException("账号已被锁定");
        }

        if (!userDetails.isAccountNonExpired()) {
            log.debug("账号已过期: {}", username);
            throw new AccountExpiredException("账号已过期");
        }

        if (!userDetails.isCredentialsNonExpired()) {
            log.debug("密码已过期: {}", username);
            throw new CredentialsExpiredException("密码已过期，请修改密码");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            log.debug("密码不匹配: {}", username);
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 构造已认证的认证对象
        UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        result.setDetails(authentication.getDetails());

        // 如果是GameUserDetails，可以更新会话状态
        if (userDetails instanceof GameUserDetails) {
            ((GameUserDetails) userDetails).setSessionStatus("ONLINE");
        }

        log.debug("用户名密码认证成功: {}", username);
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}