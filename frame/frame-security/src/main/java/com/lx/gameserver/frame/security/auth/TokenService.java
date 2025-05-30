/*
 * 文件名: TokenService.java
 * 用途: JWT Token生成与验证服务
 * 实现内容:
 *   - 生成Access Token和Refresh Token
 *   - Token验证与解析
 *   - Token黑名单管理
 *   - 令牌自动续期
 *   - 多设备登录控制
 * 技术选型:
 *   - JJWT库
 *   - Redis分布式Token存储
 *   - 分布式黑名单
 * 依赖关系:
 *   - 被GameAuthenticationManager使用
 *   - 使用SecurityProperties配置
 */
package com.lx.gameserver.frame.security.auth;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.crypto.GameCryptoService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token服务
 * <p>
 * 提供JWT Token的生成、验证、解析、管理等功能，
 * 支持双令牌（访问令牌和刷新令牌）机制，以及Token黑名单、
 * 自动续期、多设备登录控制等高级功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class TokenService {
    
    /**
     * Token类型 - 访问令牌
     */
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    
    /**
     * Token类型 - 刷新令牌
     */
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";
    
    /**
     * 令牌黑名单前缀
     */
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    
    /**
     * 用户令牌前缀
     */
    private static final String USER_TOKEN_PREFIX = "user:tokens:";
    
    private final SecurityProperties securityProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GameCryptoService cryptoService;
    
    /**
     * JWT签名密钥
     */
    private Key signingKey;

    public TokenService(SecurityProperties securityProperties, 
                       @Nullable RedisTemplate<String, Object> redisTemplate,
                       @Nullable GameCryptoService cryptoService) {
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
        this.cryptoService = cryptoService;
        this.initSigningKey();
    }
    
    /**
     * 初始化签名密钥
     */
    private void initSigningKey() {
        String secret = securityProperties.getAuth().getJwt().getSecret();
        if (!StringUtils.hasText(secret)) {
            log.warn("JWT密钥未配置，使用临时随机密钥");
            secret = UUID.randomUUID().toString();
        }
        
        // 如果加密服务可用，则对密钥进行额外加密处理
        if (cryptoService != null) {
            try {
                secret = cryptoService.hashString(secret);
            } catch (Exception e) {
                log.warn("使用加密服务加密JWT密钥失败，使用原始密钥", e);
            }
        }
        
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        log.info("JWT签名密钥初始化完成");
    }
    
    /**
     * 生成访问令牌
     *
     * @param userDetails 用户详情
     * @return JWT访问令牌
     */
    public String generateAccessToken(GameUserDetails userDetails) {
        Duration validity = securityProperties.getAuth().getJwt().getAccessTokenValidity();
        
        // 设置JWT声明
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userDetails.getUsername());
        claims.put("playerId", userDetails.getPlayerId());
        claims.put("role", userDetails.getRole());
        claims.put("type", TOKEN_TYPE_ACCESS);
        claims.put("deviceId", userDetails.getDeviceId());
        
        return generateToken(claims, validity);
    }
    
    /**
     * 生成刷新令牌
     *
     * @param userDetails 用户详情
     * @return JWT刷新令牌
     */
    public String generateRefreshToken(GameUserDetails userDetails) {
        Duration validity = securityProperties.getAuth().getJwt().getRefreshTokenValidity();
        
        // 设置JWT声明
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userDetails.getUsername());
        claims.put("type", TOKEN_TYPE_REFRESH);
        claims.put("deviceId", userDetails.getDeviceId());
        
        String token = generateToken(claims, validity);
        
        // 如果Redis可用，则存储刷新令牌关联
        storeUserToken(userDetails.getUsername(), userDetails.getDeviceId(), token);
        
        return token;
    }
    
    /**
     * 通用令牌生成方法
     *
     * @param claims JWT声明
     * @param validity 有效期
     * @return JWT令牌
     */
    private String generateToken(Map<String, Object> claims, Duration validity) {
        Instant now = Instant.now();
        Instant expiryDate = now.plus(validity);
        
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 验证令牌
     *
     * @param token JWT令牌
     * @return 如果令牌有效返回true，否则返回false
     */
    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        
        // 检查令牌是否在黑名单中
        if (isTokenBlacklisted(token)) {
            log.debug("令牌已被加入黑名单");
            return false;
        }
        
        try {
            Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.debug("无效的JWT签名: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.debug("JWT令牌已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.debug("不支持的JWT令牌: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT声明字符串为空: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 获取令牌中的声明
     *
     * @param token JWT令牌
     * @return 声明信息
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * 从令牌中提取用户名
     *
     * @param token JWT令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("无法从令牌中获取用户名", e);
            return null;
        }
    }
    
    /**
     * 吊销令牌（加入黑名单）
     *
     * @param token JWT令牌
     */
    public void revokeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        
        try {
            // 获取令牌过期时间，用于计算黑名单存储时间
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            String username = claims.getSubject();
            
            // 清除用户设备令牌关联
            String deviceId = (String) claims.get("deviceId");
            if (StringUtils.hasText(deviceId)) {
                removeUserToken(username, deviceId);
            }
            
            // 将令牌加入黑名单
            if (redisTemplate != null) {
                String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
                long ttl = (expiration.getTime() - System.currentTimeMillis()) / 1000;
                if (ttl > 0) {
                    redisTemplate.opsForValue().set(blacklistKey, true, ttl, TimeUnit.SECONDS);
                    log.debug("令牌已加入黑名单，有效期: {}秒", ttl);
                }
            }
        } catch (ExpiredJwtException e) {
            // 令牌已过期，无需加入黑名单
            log.debug("令牌已过期，不需加入黑名单");
        } catch (Exception e) {
            log.error("吊销令牌失败", e);
        }
    }
    
    /**
     * 检查令牌是否在黑名单中
     *
     * @param token JWT令牌
     * @return 如果在黑名单中返回true，否则返回false
     */
    public boolean isTokenBlacklisted(String token) {
        if (redisTemplate == null || !StringUtils.hasText(token)) {
            return false;
        }
        
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
        Boolean isBlacklisted = (Boolean) redisTemplate.opsForValue().get(blacklistKey);
        return Boolean.TRUE.equals(isBlacklisted);
    }
    
    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @param userDetails 用户详情
     * @return 新的访问令牌，如果刷新失败则返回null
     */
    public String refreshAccessToken(String refreshToken, GameUserDetails userDetails) {
        if (!validateToken(refreshToken)) {
            log.debug("刷新令牌无效或已过期");
            return null;
        }
        
        try {
            Claims claims = getClaimsFromToken(refreshToken);
            String tokenType = (String) claims.get("type");
            
            // 验证是否是刷新令牌
            if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                log.debug("无效的刷新令牌类型: {}", tokenType);
                return null;
            }
            
            // 验证刷新令牌是否属于该用户
            String username = claims.getSubject();
            if (!username.equals(userDetails.getUsername())) {
                log.debug("刷新令牌用户不匹配: {} vs {}", username, userDetails.getUsername());
                return null;
            }
            
            // 生成新的访问令牌
            return generateAccessToken(userDetails);
            
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            return null;
        }
    }
    
    /**
     * 存储用户设备令牌关联
     *
     * @param username 用户名
     * @param deviceId 设备ID
     * @param token 令牌
     */
    private void storeUserToken(String username, String deviceId, String token) {
        if (redisTemplate == null || !StringUtils.hasText(username) || !StringUtils.hasText(deviceId)) {
            return;
        }
        
        try {
            // 构建键
            String userTokenKey = USER_TOKEN_PREFIX + username;
            
            // 检查多设备登录策略
            if (!securityProperties.getAuth().isMultiDeviceLogin()) {
                // 如果不允许多设备登录，清除该用户的所有现有令牌
                Map<Object, Object> existingTokens = redisTemplate.opsForHash().entries(userTokenKey);
                for (Object existingDeviceId : existingTokens.keySet()) {
                    if (!deviceId.equals(existingDeviceId.toString())) {
                        // 将其他设备的令牌加入黑名单
                        String existingToken = existingTokens.get(existingDeviceId).toString();
                        revokeToken(existingToken);
                    }
                }
            }
            
            // 存储令牌与设备关联
            Duration validity = securityProperties.getAuth().getJwt().getRefreshTokenValidity();
            redisTemplate.opsForHash().put(userTokenKey, deviceId, token);
            redisTemplate.expire(userTokenKey, validity.getSeconds(), TimeUnit.SECONDS);
            
            log.debug("已存储用户Token关联: 用户={}, 设备={}", username, deviceId);
        } catch (Exception e) {
            log.error("存储用户Token关联失败", e);
        }
    }
    
    /**
     * 移除用户设备令牌关联
     *
     * @param username 用户名
     * @param deviceId 设备ID
     */
    private void removeUserToken(String username, String deviceId) {
        if (redisTemplate == null || !StringUtils.hasText(username) || !StringUtils.hasText(deviceId)) {
            return;
        }
        
        try {
            String userTokenKey = USER_TOKEN_PREFIX + username;
            redisTemplate.opsForHash().delete(userTokenKey, deviceId);
            log.debug("已移除用户Token关联: 用户={}, 设备={}", username, deviceId);
        } catch (Exception e) {
            log.error("移除用户Token关联失败", e);
        }
    }
}