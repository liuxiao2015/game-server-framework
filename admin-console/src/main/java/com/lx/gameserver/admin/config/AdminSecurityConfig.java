/*
 * 文件名: AdminSecurityConfig.java
 * 用途: 管理后台安全配置
 * 实现内容:
 *   - Spring Security基础配置
 *   - 认证管理器配置
 *   - 密码编码器配置
 *   - 测试环境安全配置
 * 技术选型:
 *   - Spring Security 6+
 *   - BCrypt密码加密
 *   - JWT Token认证
 * 依赖关系: 为AuthenticationService提供安全组件配置
 */
package com.lx.gameserver.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 管理后台安全配置
 * <p>
 * 配置Spring Security的基本安全策略，包括认证管理器、
 * 密码编码器、用户详情服务等核心组件。为测试环境
 * 提供简化的安全配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    /**
     * 密码编码器Bean
     * <p>
     * 使用BCrypt算法进行密码加密，提供安全的密码存储。
     * </p>
     *
     * @return BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器Bean
     * <p>
     * 配置Spring Security的认证管理器，用于处理用户认证。
     * </p>
     *
     * @param config 认证配置
     * @return 认证管理器
     * @throws Exception 配置异常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 测试环境用户详情服务
     * <p>
     * 为测试环境提供内存中的用户存储，包含默认的测试用户。
     * </p>
     *
     * @return 用户详情服务
     */
    @Bean
    @Profile("test")
    public UserDetailsService testUserDetailsService() {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin123"))
            .authorities("ROLE_ADMIN")
            .build();

        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder().encode("user123"))
            .authorities("ROLE_USER")
            .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    /**
     * 测试环境安全过滤器链
     * <p>
     * 为测试环境配置简化的安全策略，禁用CSRF和会话管理。
     * </p>
     *
     * @param http HTTP安全配置
     * @return 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    @Profile("test")
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/admin/health", "/admin/info").permitAll()
                .requestMatchers("/admin/api/auth/login").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * 生产环境安全过滤器链
     * <p>
     * 为生产环境配置完整的安全策略，包括CSRF防护、会话管理等。
     * </p>
     *
     * @param http HTTP安全配置
     * @return 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    @Profile("!test")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/admin/api/**")
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/admin/health", "/admin/info").permitAll()
                .requestMatchers("/admin/api/auth/login", "/admin/api/auth/refresh").permitAll()
                .requestMatchers("/admin/assets/**", "/admin/swagger-ui/**", "/admin/api-docs/**").permitAll()
                .requestMatchers("/admin/api/**").authenticated()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}