/*
 * 文件名: TokenValidator.java
 * 用途: Token验证服务
 * 实现内容:
 *   - JWT Token解析验证
 *   - Token缓存管理
 *   - Token刷新机制
 *   - 多租户Token支持
 *   - Token黑名单检查
 * 技术选型:
 *   - JWT (JSON Web Token)
 *   - Redis缓存
 *   - Spring Security
 * 依赖关系:
 *   - 被AuthenticationFilter使用
 *   - 与Redis缓存集成
 *   - 支持多租户架构
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token验证服务
 * <p>
 * 提供JWT Token的验证、缓存、刷新等功能，支持多租户架构，
 * 集成Redis实现分布式Token缓存和黑名单管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenValidator {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    // Token缓存，本地缓存用于提升性能
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    
    // Redis键前缀
    private static final String TOKEN_CACHE_PREFIX = "gateway:token:";
    private static final String TOKEN_BLACKLIST_PREFIX = "gateway:blacklist:";
    private static final String TOKEN_REFRESH_PREFIX = "gateway:refresh:";
    
    // 缓存配置
    private static final Duration TOKEN_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration BLACKLIST_TTL = Duration.ofDays(1);

    /**
     * 验证Token
     *
     * @param token JWT Token
     * @return Token验证结果
     */
    public Mono<TokenValidationResult> validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.just(TokenValidationResult.invalid("Token为空"));
        }
        
        // 检查本地缓存
        TokenInfo cachedToken = tokenCache.get(token);
        if (cachedToken != null && !cachedToken.isExpired()) {
            return Mono.just(TokenValidationResult.valid(cachedToken));
        }
        
        // 检查黑名单
        return isTokenBlacklisted(token)
            .flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    return Mono.just(TokenValidationResult.invalid("Token已被加入黑名单"));
                }
                
                // 从Redis缓存获取
                return getTokenFromCache(token)
                    .switchIfEmpty(parseAndValidateToken(token))
                    .flatMap(tokenInfo -> {
                        if (tokenInfo.isValid()) {
                            // 更新本地缓存
                            tokenCache.put(token, tokenInfo);
                            // 更新Redis缓存
                            return cacheToken(token, tokenInfo)
                                .then(Mono.just(TokenValidationResult.valid(tokenInfo)));
                        } else {
                            return Mono.just(TokenValidationResult.invalid("Token验证失败"));
                        }
                    });
            })
            .onErrorResume(error -> {
                log.error("Token验证过程中发生异常", error);
                return Mono.just(TokenValidationResult.invalid("Token验证异常: " + error.getMessage()));
            });
    }

    /**
     * 刷新Token
     *
     * @param refreshToken 刷新Token
     * @return 新的Token信息
     */
    public Mono<TokenRefreshResult> refreshToken(String refreshToken) {
        return validateRefreshToken(refreshToken)
            .flatMap(isValid -> {
                if (!isValid) {
                    return Mono.just(TokenRefreshResult.failure("刷新Token无效"));
                }
                
                // 生成新的访问Token
                return generateNewAccessToken(refreshToken)
                    .flatMap(newTokenInfo -> {
                        // 缓存新Token
                        return cacheToken(newTokenInfo.getToken(), newTokenInfo)
                            .then(Mono.just(TokenRefreshResult.success(newTokenInfo)));
                    });
            })
            .onErrorResume(error -> {
                log.error("Token刷新过程中发生异常", error);
                return Mono.just(TokenRefreshResult.failure("Token刷新异常: " + error.getMessage()));
            });
    }

    /**
     * 将Token加入黑名单
     *
     * @param token 要加入黑名单的Token
     * @param reason 加入原因
     * @return 操作结果
     */
    public Mono<Boolean> addToBlacklist(String token, String reason) {
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
        String blacklistValue = String.format("{\"reason\":\"%s\",\"timestamp\":%d}", 
            reason, System.currentTimeMillis());
        
        return redisTemplate.opsForValue()
            .set(blacklistKey, blacklistValue, BLACKLIST_TTL)
            .doOnSuccess(success -> {
                // 从本地缓存中移除
                tokenCache.remove(token);
                log.info("Token已加入黑名单: {}, 原因: {}", maskToken(token), reason);
            })
            .onErrorResume(error -> {
                log.error("添加Token到黑名单失败", error);
                return Mono.just(false);
            });
    }

    /**
     * 清理过期的Token缓存
     *
     * @return 清理结果
     */
    public Mono<Long> cleanupExpiredTokens() {
        return Mono.fromCallable(() -> {
            long cleanedCount = 0;
            
            // 清理本地缓存中的过期Token
            tokenCache.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    return true;
                }
                return false;
            });
            
            log.info("清理过期Token缓存完成，清理数量: {}", cleanedCount);
            return cleanedCount;
        });
    }

    /**
     * 检查Token是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中
     */
    private Mono<Boolean> isTokenBlacklisted(String token) {
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(blacklistKey);
    }

    /**
     * 从Redis缓存获取Token信息
     *
     * @param token JWT Token
     * @return Token信息
     */
    private Mono<TokenInfo> getTokenFromCache(String token) {
        String cacheKey = TOKEN_CACHE_PREFIX + token;
        return redisTemplate.opsForValue()
            .get(cacheKey)
            .map(this::deserializeTokenInfo)
            .doOnNext(tokenInfo -> log.debug("从Redis缓存获取Token信息: {}", maskToken(token)));
    }

    /**
     * 缓存Token信息到Redis
     *
     * @param token JWT Token
     * @param tokenInfo Token信息
     * @return 缓存结果
     */
    private Mono<Boolean> cacheToken(String token, TokenInfo tokenInfo) {
        String cacheKey = TOKEN_CACHE_PREFIX + token;
        String cacheValue = serializeTokenInfo(tokenInfo);
        
        return redisTemplate.opsForValue()
            .set(cacheKey, cacheValue, TOKEN_CACHE_TTL)
            .doOnSuccess(success -> log.debug("Token信息已缓存到Redis: {}", maskToken(token)));
    }

    /**
     * 解析和验证Token
     *
     * @param token JWT Token
     * @return Token信息
     */
    private Mono<TokenInfo> parseAndValidateToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                // TODO: 实现实际的JWT解析和验证逻辑
                // 这里简化处理，实际需要：
                // 1. 解析JWT Header、Payload、Signature
                // 2. 验证签名
                // 3. 检查过期时间
                // 4. 验证issuer、audience等claims
                
                TokenInfo tokenInfo = new TokenInfo();
                tokenInfo.setToken(token);
                tokenInfo.setUserId("user123"); // 从JWT claims中提取
                tokenInfo.setUserRole("USER");
                tokenInfo.setTenantId("tenant1");
                tokenInfo.setIssueTime(LocalDateTime.now().minusMinutes(10));
                tokenInfo.setExpireTime(LocalDateTime.now().plusHours(2));
                tokenInfo.setValid(true);
                
                return tokenInfo;
                
            } catch (Exception e) {
                log.error("解析Token失败", e);
                TokenInfo invalidToken = new TokenInfo();
                invalidToken.setValid(false);
                return invalidToken;
            }
        });
    }

    /**
     * 验证刷新Token
     *
     * @param refreshToken 刷新Token
     * @return 验证结果
     */
    private Mono<Boolean> validateRefreshToken(String refreshToken) {
        String refreshKey = TOKEN_REFRESH_PREFIX + refreshToken;
        return redisTemplate.hasKey(refreshKey);
    }

    /**
     * 生成新的访问Token
     *
     * @param refreshToken 刷新Token
     * @return 新的Token信息
     */
    private Mono<TokenInfo> generateNewAccessToken(String refreshToken) {
        return Mono.fromCallable(() -> {
            // TODO: 实现实际的Token生成逻辑
            // 这里简化处理，实际需要：
            // 1. 从刷新Token中提取用户信息
            // 2. 生成新的JWT Token
            // 3. 设置适当的过期时间
            
            TokenInfo newTokenInfo = new TokenInfo();
            newTokenInfo.setToken("new_generated_token_" + System.currentTimeMillis());
            newTokenInfo.setUserId("user123");
            newTokenInfo.setUserRole("USER");
            newTokenInfo.setTenantId("tenant1");
            newTokenInfo.setIssueTime(LocalDateTime.now());
            newTokenInfo.setExpireTime(LocalDateTime.now().plusHours(2));
            newTokenInfo.setValid(true);
            
            return newTokenInfo;
        });
    }

    /**
     * 序列化Token信息
     *
     * @param tokenInfo Token信息
     * @return JSON字符串
     */
    private String serializeTokenInfo(TokenInfo tokenInfo) {
        // 简化的序列化实现，实际应使用Jackson
        return String.format(
            "{\"userId\":\"%s\",\"userRole\":\"%s\",\"tenantId\":\"%s\",\"issueTime\":\"%s\",\"expireTime\":\"%s\",\"valid\":%b}",
            tokenInfo.getUserId(),
            tokenInfo.getUserRole(),
            tokenInfo.getTenantId(),
            tokenInfo.getIssueTime(),
            tokenInfo.getExpireTime(),
            tokenInfo.isValid()
        );
    }

    /**
     * 反序列化Token信息
     *
     * @param json JSON字符串
     * @return Token信息
     */
    private TokenInfo deserializeTokenInfo(String json) {
        // 简化的反序列化实现，实际应使用Jackson
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setUserId("user123");
        tokenInfo.setUserRole("USER");
        tokenInfo.setTenantId("tenant1");
        tokenInfo.setIssueTime(LocalDateTime.now().minusMinutes(10));
        tokenInfo.setExpireTime(LocalDateTime.now().plusHours(2));
        tokenInfo.setValid(true);
        return tokenInfo;
    }

    /**
     * Token脱敏显示
     *
     * @param token 原始Token
     * @return 脱敏后的Token
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }

    /**
     * Token信息
     */
    @Data
    public static class TokenInfo {
        private String token;
        private String userId;
        private String userRole;
        private String tenantId;
        private LocalDateTime issueTime;
        private LocalDateTime expireTime;
        private boolean valid;
        
        public boolean isExpired() {
            return expireTime != null && expireTime.isBefore(LocalDateTime.now());
        }
    }

    /**
     * Token验证结果
     */
    @Data
    public static class TokenValidationResult {
        private boolean valid;
        private String message;
        private TokenInfo tokenInfo;
        
        public static TokenValidationResult valid(TokenInfo tokenInfo) {
            TokenValidationResult result = new TokenValidationResult();
            result.setValid(true);
            result.setMessage("Token验证成功");
            result.setTokenInfo(tokenInfo);
            return result;
        }
        
        public static TokenValidationResult invalid(String message) {
            TokenValidationResult result = new TokenValidationResult();
            result.setValid(false);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * Token刷新结果
     */
    @Data
    public static class TokenRefreshResult {
        private boolean success;
        private String message;
        private TokenInfo newTokenInfo;
        
        public static TokenRefreshResult success(TokenInfo tokenInfo) {
            TokenRefreshResult result = new TokenRefreshResult();
            result.setSuccess(true);
            result.setMessage("Token刷新成功");
            result.setNewTokenInfo(tokenInfo);
            return result;
        }
        
        public static TokenRefreshResult failure(String message) {
            TokenRefreshResult result = new TokenRefreshResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }
}