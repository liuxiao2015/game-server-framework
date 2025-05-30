/*
 * 文件名: GameAuthenticationManager.java
 * 用途: 游戏认证管理器
 * 实现内容:
 *   - 多种认证方式支持
 *   - 认证流程编排
 *   - 认证结果缓存
 *   - 防暴力破解机制
 *   - 异常认证行为检测
 * 技术选型:
 *   - Spring Security认证扩展
 *   - 多策略认证流程
 *   - 基于Caffeine的本地缓存
 *   - 基于Redis的分布式锁
 * 依赖关系:
 *   - 使用多种AuthenticationProvider
 *   - 与TokenService配合
 */
package com.lx.gameserver.frame.security.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lx.gameserver.frame.security.config.SecurityProperties;
import com.lx.gameserver.frame.security.risk.AnomalyDetection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游戏认证管理器
 * <p>
 * 提供游戏服务器特有的认证管理功能，支持多种认证方式，
 * 包括账号密码、Token令牌、第三方平台认证等。实现认证
 * 流程编排、认证结果缓存、防暴力破解和异常检测等高级功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class GameAuthenticationManager implements AuthenticationManager {

    private final SecurityProperties securityProperties;
    private final Map<Class<?>, AuthenticationProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    private final Cache<Object, Object> authenticationCache;
    
    @Autowired(required = false)
    private TokenService tokenService;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    private AnomalyDetection anomalyDetection;
    
    /**
     * 登录尝试锁定前缀（Redis键）
     */
    private static final String LOGIN_ATTEMPT_LOCK_PREFIX = "auth:login:lock:";
    
    /**
     * 构造函数
     *
     * @param securityProperties 安全配置属性
     */
    public GameAuthenticationManager(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        
        // 初始化认证缓存
        this.authenticationCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
        
        log.info("游戏认证管理器初始化完成");
    }
    
    /**
     * 注册认证提供者
     *
     * @param provider 认证提供者
     */
    public void registerProvider(AuthenticationProvider provider) {
        Assert.notNull(provider, "认证提供者不能为空");
        
        Class<?> supportedType = getSupportedAuthenticationClass(provider);
        if (supportedType != null) {
            providers.put(supportedType, provider);
            log.info("注册认证提供者: {} 支持类型: {}", provider.getClass().getSimpleName(), supportedType.getSimpleName());
        } else {
            log.warn("无法确定认证提供者支持的类型: {}", provider.getClass().getSimpleName());
        }
    }
    
    /**
     * 获取认证提供者支持的认证类型
     */
    private Class<?> getSupportedAuthenticationClass(AuthenticationProvider provider) {
        try {
            // 通常认证提供者会实现supports(Class<?> authentication)方法
            // 遍历常见的认证类型，找到提供者支持的类型
            List<Class<?>> commonTypes = Arrays.asList(
                    UsernamePasswordAuthenticationToken.class,
                    RememberMeAuthenticationToken.class,
                    AnonymousAuthenticationToken.class);
            
            for (Class<?> type : commonTypes) {
                if (provider.supports(type)) {
                    return type;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("获取认证提供者支持的类型时出错", e);
            return null;
        }
    }
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Class<?> authClass = authentication.getClass();
        Object authCacheKey = generateCacheKey(authentication);
        
        // 尝试从缓存获取认证结果
        Authentication cachedAuth = (Authentication) authenticationCache.getIfPresent(authCacheKey);
        if (cachedAuth != null) {
            log.debug("从缓存获取认证结果: {}", authentication.getName());
            return cachedAuth;
        }
        
        // 获取认证提供者
        AuthenticationProvider provider = providers.get(authClass);
        if (provider == null) {
            log.warn("无法找到支持{}的认证提供者", authClass.getSimpleName());
            throw new AuthenticationServiceException("不支持的认证类型");
        }
        
        // 检查账号是否被锁定
        String username = authentication.getName();
        if (isAccountLocked(username)) {
            long lockRemaining = getAccountLockRemaining(username);
            log.warn("账号已锁定: {} 剩余锁定时间: {}秒", username, lockRemaining);
            throw new LockedException("账号已被锁定，请稍后再试");
        }
        
        // 执行认证前检查
        preAuthenticationCheck(authentication);
        
        try {
            // 执行认证
            Authentication result = provider.authenticate(authentication);
            
            // 认证成功后的处理
            postAuthenticationSuccess(authentication);
            
            // 缓存认证结果
            authenticationCache.put(authCacheKey, result);
            
            // 清除失败尝试次数
            resetFailedAttempts(username);
            
            return result;
        } catch (AuthenticationException e) {
            // 认证失败后的处理
            postAuthenticationFailure(authentication, e);
            throw e;
        }
    }
    
    /**
     * 生成认证缓存键
     *
     * @param authentication 认证信息
     * @return 缓存键
     */
    private Object generateCacheKey(Authentication authentication) {
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            return "UP:" + authentication.getName() + ":" + Objects.hash(authentication.getCredentials());
        } else {
            return authentication.getClass().getSimpleName() + ":" + authentication.getName();
        }
    }
    
    /**
     * 认证前检查
     *
     * @param authentication 认证信息
     * @throws AuthenticationException 认证异常
     */
    private void preAuthenticationCheck(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String ip = extractIpAddress(authentication);
        String deviceId = extractDeviceId(authentication);
        
        // 异常行为检测
        if (anomalyDetection != null) {
            try {
                if (anomalyDetection.detectLoginAnomaly(username, ip, deviceId)) {
                    log.warn("检测到异常登录行为: {} IP: {} 设备: {}", username, ip, deviceId);
                    throw new AuthenticationServiceException("检测到异常登录行为");
                }
            } catch (Exception e) {
                log.error("异常登录检测失败", e);
            }
        }
        
        // 防止过多尝试
        int maxAttempts = securityProperties.getAuth().getMaxLoginAttempts();
        AtomicInteger attempts = loginAttempts.computeIfAbsent(username, k -> new AtomicInteger(0));
        int currentAttempts = attempts.get();
        
        if (currentAttempts >= maxAttempts) {
            // 达到最大尝试次数，锁定账号
            lockAccount(username);
            log.warn("账号登录尝试次数过多，已锁定: {}", username);
            throw new LockedException("登录尝试次数过多，账号已被锁定");
        }
    }
    
    /**
     * 认证成功后处理
     *
     * @param authentication 认证信息
     */
    private void postAuthenticationSuccess(Authentication authentication) {
        // 可以在这里添加额外的认证成功处理逻辑
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("认证成功: {}", authentication.getName());
    }
    
    /**
     * 认证失败后处理
     *
     * @param authentication 认证信息
     * @param exception 认证异常
     */
    private void postAuthenticationFailure(Authentication authentication, AuthenticationException exception) {
        String username = authentication.getName();
        
        // 记录失败尝试次数
        AtomicInteger attempts = loginAttempts.computeIfAbsent(username, k -> new AtomicInteger(0));
        int currentAttempts = attempts.incrementAndGet();
        int maxAttempts = securityProperties.getAuth().getMaxLoginAttempts();
        
        log.warn("认证失败: {} 失败次数: {}/{} 原因: {}", 
                username, currentAttempts, maxAttempts, exception.getMessage());
        
        // 发布认证失败事件（可以在这里添加异步事件发布）
    }
    
    /**
     * 锁定账号
     *
     * @param username 用户名
     */
    private void lockAccount(String username) {
        long lockDuration = securityProperties.getAuth().getLockDuration().toSeconds();
        
        if (redisTemplate != null) {
            // 分布式环境下使用Redis锁定账号
            String lockKey = LOGIN_ATTEMPT_LOCK_PREFIX + username;
            redisTemplate.opsForValue().set(lockKey, true, lockDuration, TimeUnit.SECONDS);
        } else {
            // 本地环境下使用内存锁定账号
            loginAttempts.put(username, new AtomicInteger(Integer.MAX_VALUE));
            
            // 启动一个定时任务来解锁账号
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    resetFailedAttempts(username);
                }
            }, lockDuration * 1000);
        }
    }
    
    /**
     * 检查账号是否被锁定
     *
     * @param username 用户名
     * @return 如果锁定返回true，否则返回false
     */
    private boolean isAccountLocked(String username) {
        if (redisTemplate != null) {
            String lockKey = LOGIN_ATTEMPT_LOCK_PREFIX + username;
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } else {
            AtomicInteger attempts = loginAttempts.get(username);
            return attempts != null && attempts.get() >= Integer.MAX_VALUE;
        }
    }
    
    /**
     * 获取账号剩余锁定时间（秒）
     *
     * @param username 用户名
     * @return 剩余锁定时间（秒）
     */
    private long getAccountLockRemaining(String username) {
        if (redisTemplate != null) {
            String lockKey = LOGIN_ATTEMPT_LOCK_PREFIX + username;
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            return ttl != null ? ttl : 0;
        }
        return 0; // 本地环境无法精确获取剩余时间
    }
    
    /**
     * 重置失败尝试次数
     *
     * @param username 用户名
     */
    private void resetFailedAttempts(String username) {
        loginAttempts.remove(username);
        
        if (redisTemplate != null) {
            String lockKey = LOGIN_ATTEMPT_LOCK_PREFIX + username;
            redisTemplate.delete(lockKey);
        }
    }
    
    /**
     * 从认证对象中提取IP地址
     *
     * @param authentication 认证信息
     * @return IP地址，如果无法提取则返回null
     */
    private String extractIpAddress(Authentication authentication) {
        if (authentication.getDetails() instanceof Map) {
            return (String) ((Map<?, ?>) authentication.getDetails()).get("remoteAddress");
        }
        return null;
    }
    
    /**
     * 从认证对象中提取设备ID
     *
     * @param authentication 认证信息
     * @return 设备ID，如果无法提取则返回null
     */
    private String extractDeviceId(Authentication authentication) {
        if (authentication.getDetails() instanceof Map) {
            return (String) ((Map<?, ?>) authentication.getDetails()).get("deviceId");
        }
        return null;
    }
    
    /**
     * 通过用户名进行认证
     *
     * @param username 用户名
     * @param password 密码
     * @param deviceId 设备ID
     * @param ipAddress IP地址
     * @return 认证结果
     * @throws AuthenticationException 认证失败异常
     */
    public Authentication authenticateByUsername(String username, String password, 
                                              @Nullable String deviceId, @Nullable String ipAddress) 
            throws AuthenticationException {
        
        // 创建认证凭证
        UsernamePasswordAuthenticationToken authRequest = 
                new UsernamePasswordAuthenticationToken(username, password);
        
        // 设置附加信息
        Map<String, String> details = new HashMap<>();
        if (deviceId != null) details.put("deviceId", deviceId);
        if (ipAddress != null) details.put("remoteAddress", ipAddress);
        authRequest.setDetails(details);
        
        // 执行认证
        return authenticate(authRequest);
    }
    
    /**
     * 通过Token进行认证
     *
     * @param token 访问令牌
     * @return 认证结果，如果认证失败则返回null
     */
    public Authentication authenticateByToken(String token) {
        if (tokenService == null) {
            log.warn("TokenService未配置，无法完成Token认证");
            return null;
        }
        
        try {
            // 验证Token有效性
            if (!tokenService.validateToken(token)) {
                return null;
            }
            
            // 获取用户名
            String username = tokenService.getUsernameFromToken(token);
            if (username == null) {
                return null;
            }
            
            // 创建已认证的认证对象
            // 注意：此处需要根据实际情况获取用户详细信息和权限
            // 这里只是简单示例，实际项目中应该从用户服务获取完整信息
            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            
            return authentication;
        } catch (Exception e) {
            log.error("Token认证失败", e);
            return null;
        }
    }
}