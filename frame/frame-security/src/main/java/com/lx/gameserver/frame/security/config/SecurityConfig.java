/*
 * 文件名: SecurityConfig.java
 * 用途: Spring Security核心配置
 * 实现内容:
 *   - 安全过滤器链配置
 *   - 认证管理器与提供者配置
 *   - 访问决策管理器配置
 *   - 游戏自定义安全设置
 *   - CORS与会话配置
 * 技术选型:
 *   - Spring Security配置
 *   - 基于JWT的无状态认证
 *   - 自定义认证与授权规则
 * 依赖关系:
 *   - 使用SecurityProperties配置
 *   - 配置游戏认证与授权系统
 */
package com.lx.gameserver.frame.security.config;

import com.lx.gameserver.frame.security.auth.GameAuthenticationManager;
import com.lx.gameserver.frame.security.authorization.GameAccessDecisionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security核心配置类
 * <p>
 * 提供游戏服务器的安全防护配置，包括认证过滤器链、
 * 授权规则、CORS策略、Session管理和异常处理等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    /**
     * 配置API安全过滤链
     *
     * @param http HttpSecurity配置对象
     * @return 配置完成的SecurityFilterChain
     * @throws Exception 配置过程中的异常
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("配置API安全过滤器链");
        
        http
            // 禁用CSRF，游戏API通常使用token认证无需CSRF
            .csrf(csrf -> csrf.disable())
            // 配置会话管理，使用无状态会话
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 配置请求授权规则
            .authorizeHttpRequests(authorize -> authorize
                // 允许认证相关API无需认证访问
                .requestMatchers("/api/auth/**", "/api/public/**").permitAll()
                // 健康检查端点允许无认证访问
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // 其他所有请求需要认证
                .anyRequest().authenticated()
            );
            
        // 这里可添加自定义过滤器，如JWT过滤器等
        
        return http.build();
    }

    /**
     * 配置管理后台安全过滤链
     *
     * @param http HttpSecurity配置对象
     * @return 配置完成的SecurityFilterChain
     * @throws Exception 配置过程中的异常
     */
    @Bean
    @Order(2)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("配置管理后台安全过滤器链");
        
        http
            // 管理后台可以启用CSRF防护
            .securityMatcher("/admin/**")
            // 配置会话管理，管理后台使用有状态会话
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // 配置请求授权规则
            .authorizeHttpRequests(authorize -> authorize
                // 管理后台登录页允许无认证访问
                .requestMatchers("/admin/login").permitAll()
                // 管理后台静态资源允许无认证访问
                .requestMatchers("/admin/assets/**").permitAll()
                // 其他管理后台请求需要ADMIN角色
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )
            // 配置表单登录
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/doLogin")
                .defaultSuccessUrl("/admin/dashboard")
                .failureUrl("/admin/login?error=true")
            );
        
        return http.build();
    }

    /**
     * 配置CORS
     *
     * @return CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));  // 生产环境应限制为特定域名
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * 密码编码器
     *
     * @return BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     *
     * @param authenticationConfiguration Spring提供的认证配置
     * @return 认证管理器
     * @throws Exception 获取认证管理器失败时抛出
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 游戏认证管理器
     *
     * @return 游戏认证管理器
     */
    @Bean
    public GameAuthenticationManager gameAuthenticationManager() {
        return new GameAuthenticationManager(securityProperties);
    }

    /**
     * 游戏权限决策管理器
     *
     * @return 游戏权限决策管理器
     */
    @Bean
    public GameAccessDecisionManager gameAccessDecisionManager() {
        return new GameAccessDecisionManager();
    }
}