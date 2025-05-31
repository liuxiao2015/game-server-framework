/*
 * 文件名: SecurityConfiguration.java
 * 用途: 网关安全配置
 * 实现内容:
 *   - Spring Security配置
 *   - JWT认证配置
 *   - 权限校验配置
 *   - 防重放攻击配置
 *   - IP白名单/黑名单配置
 *   - CORS跨域配置
 * 技术选型:
 *   - Spring Security
 *   - JWT Token认证
 *   - WebFlux响应式安全
 * 依赖关系:
 *   - 与TokenValidator协作
 *   - 被安全过滤器使用
 *   - 集成认证服务
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authorization.AuthorizationWebFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.*;

/**
 * 网关安全配置类
 * <p>
 * 提供全面的安全配置，包括JWT认证、权限校验、防重放攻击、
 * IP访问控制、CORS跨域等安全功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    /**
     * 安全配置属性
     */
    @Data
    @ConfigurationProperties(prefix = "game.gateway.security")
    public static class SecurityProperties {
        
        /**
         * JWT配置
         */
        private JwtConfig jwt = new JwtConfig();
        
        /**
         * CORS配置
         */
        private CorsConfig cors = new CorsConfig();
        
        /**
         * IP访问控制配置
         */
        private IpAccessConfig ipAccess = new IpAccessConfig();
        
        /**
         * 防重放攻击配置
         */
        private AntiReplayConfig antiReplay = new AntiReplayConfig();
        
        /**
         * 白名单路径（无需认证）
         */
        private List<String> whiteList = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register",
            "/api/public/**",
            "/actuator/health",
            "/favicon.ico"
        );
    }

    /**
     * JWT配置类
     */
    @Data
    public static class JwtConfig {
        
        /**
         * JWT签名密钥
         */
        private String secret = "game-server-jwt-secret-key-2025";
        
        /**
         * Token过期时间（秒）
         */
        private long expiration = 7200; // 2小时
        
        /**
         * Token刷新时间（秒）
         */
        private long refreshExpiration = 604800; // 7天
        
        /**
         * Token前缀
         */
        private String tokenPrefix = "Bearer ";
        
        /**
         * Token请求头名称
         */
        private String headerName = "Authorization";
        
        /**
         * 是否允许Token刷新
         */
        private boolean refreshEnabled = true;
    }

    /**
     * CORS配置类
     */
    @Data
    public static class CorsConfig {
        
        /**
         * 是否启用CORS
         */
        private boolean enabled = true;
        
        /**
         * 允许的源
         */
        private List<String> allowedOrigins = Arrays.asList("*");
        
        /**
         * 允许的方法
         */
        private List<String> allowedMethods = Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        );
        
        /**
         * 允许的请求头
         */
        private List<String> allowedHeaders = Arrays.asList("*");
        
        /**
         * 暴露的响应头
         */
        private List<String> exposedHeaders = Arrays.asList(
            "X-Total-Count", "X-Request-ID", "X-Response-Time"
        );
        
        /**
         * 是否允许凭证
         */
        private boolean allowCredentials = true;
        
        /**
         * 预检请求缓存时间（秒）
         */
        private long maxAge = 3600;
    }

    /**
     * IP访问控制配置类
     */
    @Data
    public static class IpAccessConfig {
        
        /**
         * 是否启用IP访问控制
         */
        private boolean enabled = false;
        
        /**
         * IP白名单
         */
        private List<String> whiteList = new ArrayList<>();
        
        /**
         * IP黑名单
         */
        private List<String> blackList = new ArrayList<>();
        
        /**
         * 是否启用动态黑名单
         */
        private boolean dynamicBlackListEnabled = true;
        
        /**
         * 黑名单同步间隔（秒）
         */
        private int blackListSyncInterval = 60;
    }

    /**
     * 防重放攻击配置类
     */
    @Data
    public static class AntiReplayConfig {
        
        /**
         * 是否启用防重放攻击
         */
        private boolean enabled = true;
        
        /**
         * 时间戳有效期（秒）
         */
        private long timestampValidityPeriod = 300; // 5分钟
        
        /**
         * 随机数缓存时间（秒）
         */
        private long nonceCacheTime = 300;
        
        /**
         * 签名算法
         */
        private String signatureAlgorithm = "HmacSHA256";
    }

    /**
     * 安全配置属性Bean
     *
     * @return 安全配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "game.gateway.security")
    public SecurityProperties securityProperties() {
        return new SecurityProperties();
    }

    /**
     * 安全过滤器链配置
     * <p>
     * 配置WebFlux安全过滤器链，包括认证、授权、CORS等。
     * </p>
     *
     * @param http ServerHttpSecurity配置对象
     * @return 安全过滤器链
     */
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        SecurityProperties securityProps = securityProperties();
        
        return http
                // 禁用CSRF（在API网关中通常不需要）
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                
                // 禁用表单登录和HTTP Basic认证
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                
                // 配置CORS
                .cors(cors -> {
                    if (securityProps.getCors().isEnabled()) {
                        cors.configurationSource(corsConfigurationSource());
                    } else {
                        cors.disable();
                    }
                })
                
                // 配置授权规则
                .authorizeExchange(exchanges -> {
                    // 白名单路径允许匿名访问
                    for (String path : securityProps.getWhiteList()) {
                        exchanges.pathMatchers(path).permitAll();
                    }
                    
                    // 健康检查端点允许访问
                    exchanges.pathMatchers("/actuator/**").permitAll();
                    
                    // WebSocket连接特殊处理
                    exchanges.pathMatchers("/ws/**").authenticated();
                    
                    // 管理接口需要管理员权限
                    exchanges.pathMatchers("/admin/**").hasRole("ADMIN");
                    
                    // 其他所有请求需要认证
                    exchanges.anyExchange().authenticated();
                })
                
                // 配置异常处理
                .exceptionHandling(exceptions -> {
                    exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            // 认证失败处理
                            log.warn("认证失败: {}", ex.getMessage());
                            exchange.getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            
                            String errorResponse = "{\"code\":401,\"message\":\"认证失败\",\"timestamp\":" + 
                                System.currentTimeMillis() + "}";
                            
                            org.springframework.core.io.buffer.DataBuffer buffer = 
                                exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes());
                            
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                        })
                        .accessDeniedHandler((exchange, denied) -> {
                            // 授权失败处理
                            log.warn("授权失败: {}", denied.getMessage());
                            exchange.getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.FORBIDDEN);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            
                            String errorResponse = "{\"code\":403,\"message\":\"权限不足\",\"timestamp\":" + 
                                System.currentTimeMillis() + "}";
                            
                            org.springframework.core.io.buffer.DataBuffer buffer = 
                                exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes());
                            
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                        });
                })
                
                .build();
    }

    /**
     * CORS配置源
     * <p>
     * 配置跨域访问策略，支持灵活的CORS配置。
     * </p>
     *
     * @return CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        SecurityProperties securityProps = securityProperties();
        CorsConfig corsConfig = securityProps.getCors();
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 设置允许的源
        if (corsConfig.getAllowedOrigins().contains("*")) {
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        } else {
            configuration.setAllowedOrigins(corsConfig.getAllowedOrigins());
        }
        
        // 设置允许的方法
        configuration.setAllowedMethods(corsConfig.getAllowedMethods());
        
        // 设置允许的请求头
        configuration.setAllowedHeaders(corsConfig.getAllowedHeaders());
        
        // 设置暴露的响应头
        configuration.setExposedHeaders(corsConfig.getExposedHeaders());
        
        // 设置是否允许凭证
        configuration.setAllowCredentials(corsConfig.isAllowCredentials());
        
        // 设置预检请求缓存时间
        configuration.setMaxAge(corsConfig.getMaxAge());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * IP访问控制服务
     * <p>
     * 提供IP白名单和黑名单的访问控制功能。
     * </p>
     */
    public static class IpAccessControlService {
        
        private final IpAccessConfig config;
        private final Set<String> whiteListCache = Collections.synchronizedSet(new HashSet<>());
        private final Set<String> blackListCache = Collections.synchronizedSet(new HashSet<>());
        
        public IpAccessControlService(IpAccessConfig config) {
            this.config = config;
            refreshCache();
        }
        
        /**
         * 检查IP是否允许访问
         *
         * @param clientIp 客户端IP
         * @return 是否允许访问
         */
        public boolean isAccessAllowed(String clientIp) {
            if (!config.isEnabled()) {
                return true;
            }
            
            // 检查黑名单
            if (blackListCache.contains(clientIp)) {
                return false;
            }
            
            // 检查白名单
            if (!whiteListCache.isEmpty() && !whiteListCache.contains(clientIp)) {
                return false;
            }
            
            return true;
        }
        
        /**
         * 刷新缓存
         */
        public void refreshCache() {
            whiteListCache.clear();
            whiteListCache.addAll(config.getWhiteList());
            
            blackListCache.clear();
            blackListCache.addAll(config.getBlackList());
        }
        
        /**
         * 添加到黑名单
         *
         * @param ip IP地址
         */
        public void addToBlackList(String ip) {
            blackListCache.add(ip);
        }
        
        /**
         * 从黑名单移除
         *
         * @param ip IP地址
         */
        public void removeFromBlackList(String ip) {
            blackListCache.remove(ip);
        }
    }

    /**
     * IP访问控制服务Bean
     *
     * @return IP访问控制服务
     */
    @Bean
    public IpAccessControlService ipAccessControlService() {
        return new IpAccessControlService(securityProperties().getIpAccess());
    }
}