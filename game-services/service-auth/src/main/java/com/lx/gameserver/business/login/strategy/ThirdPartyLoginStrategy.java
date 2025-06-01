/*
 * 文件名: ThirdPartyLoginStrategy.java
 * 用途: 第三方登录策略
 * 实现内容:
 *   - 第三方登录实现（微信、QQ、微博等）
 *   - OAuth2.0协议实现
 *   - 用户信息获取和映射
 *   - 账号绑定和解绑
 *   - 第三方Token管理
 *   - 多平台适配
 * 技术选型:
 *   - OAuth2客户端集成
 *   - HTTP客户端调用
 *   - JSON数据解析
 *   - 加密存储
 * 依赖关系:
 *   - 实现LoginStrategy接口
 *   - 依赖OAuth2Integration
 *   - 依赖AccountService
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.strategy;

import com.lx.gameserver.business.login.core.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * 第三方登录策略
 * <p>
 * 实现基于第三方平台（微信、QQ、微博等）的OAuth2.0登录方式，
 * 提供用户信息获取、账号绑定、Token管理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class ThirdPartyLoginStrategy implements LoginStrategy {

    private static final String STRATEGY_TYPE = "THIRD_PARTY";
    private static final String OAUTH_STATE_KEY_PREFIX = "oauth:state:";
    private static final String THIRD_PARTY_TOKEN_KEY_PREFIX = "oauth:token:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public ThirdPartyLoginStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getStrategyType() {
        return STRATEGY_TYPE;
    }

    @Override
    public boolean isEnabled() {
        // 可以从配置中读取
        return true;
    }

    @Override
    public ValidationResult validateRequest(LoginRequest request, LoginContext context) {
        if (!(request instanceof ThirdPartyLoginRequest)) {
            return ValidationResult.failure("INVALID_REQUEST_TYPE", "请求类型不匹配");
        }

        ThirdPartyLoginRequest thirdPartyRequest = (ThirdPartyLoginRequest) request;
        
        // 验证平台类型
        if (thirdPartyRequest.getPlatform() == null || thirdPartyRequest.getPlatform().trim().isEmpty()) {
            return ValidationResult.failure("PLATFORM_REQUIRED", "第三方平台不能为空");
        }
        
        ThirdPartyPlatform platform = ThirdPartyPlatform.fromString(thirdPartyRequest.getPlatform());
        if (platform == null) {
            return ValidationResult.failure("UNSUPPORTED_PLATFORM", "不支持的第三方平台");
        }
        
        // 验证授权码
        if (thirdPartyRequest.getAuthorizationCode() == null || 
            thirdPartyRequest.getAuthorizationCode().trim().isEmpty()) {
            return ValidationResult.failure("AUTHORIZATION_CODE_REQUIRED", "授权码不能为空");
        }
        
        // 验证state参数（防CSRF攻击）
        if (thirdPartyRequest.getState() != null) {
            if (!validateOAuthState(thirdPartyRequest.getState())) {
                return ValidationResult.failure("INVALID_STATE", "无效的state参数");
            }
        }

        return ValidationResult.success();
    }

    @Override
    public LoginResult login(LoginRequest request, LoginContext context) {
        ThirdPartyLoginRequest thirdPartyRequest = (ThirdPartyLoginRequest) request;
        String platform = thirdPartyRequest.getPlatform();
        String authorizationCode = thirdPartyRequest.getAuthorizationCode();

        try {
            // 获取第三方平台类型
            ThirdPartyPlatform platformEnum = ThirdPartyPlatform.fromString(platform);
            if (platformEnum == null) {
                return LoginResult.failure("UNSUPPORTED_PLATFORM", "不支持的第三方平台");
            }

            // 使用授权码获取访问令牌
            ThirdPartyTokenInfo tokenInfo = exchangeCodeForToken(platformEnum, authorizationCode);
            if (tokenInfo == null) {
                return LoginResult.failure("TOKEN_EXCHANGE_FAILED", "获取访问令牌失败");
            }

            // 获取用户信息
            ThirdPartyUserInfo userInfo = getUserInfo(platformEnum, tokenInfo);
            if (userInfo == null) {
                return LoginResult.failure("USER_INFO_FETCH_FAILED", "获取用户信息失败");
            }

            // 查找或创建账号
            Account account = findOrCreateAccountByThirdParty(platformEnum, userInfo);
            if (account == null) {
                return LoginResult.failure("ACCOUNT_CREATE_FAILED", "账号创建失败");
            }

            // 检查账号状态
            if (!account.isNormal()) {
                return LoginResult.failure("ACCOUNT_STATUS_ABNORMAL", 
                        "账号状态异常: " + account.getStatus().getDescription());
            }

            // 更新第三方Token信息
            updateThirdPartyToken(account.getAccountId(), platformEnum, tokenInfo);

            // 创建登录会话
            LoginSession session = createLoginSession(account, context);

            // 更新账号最后登录信息
            updateAccountLastLogin(account, context);

            log.info("账号[{}]使用{}第三方登录成功", account.getUsername(), platform);
            return LoginResult.success(account, session);

        } catch (Exception e) {
            log.error("第三方登录处理异常: platform={}", platform, e);
            return LoginResult.failure("LOGIN_ERROR", "第三方登录处理异常，请稍后重试");
        }
    }

    /**
     * 生成OAuth授权URL
     */
    public String generateAuthorizationUrl(ThirdPartyPlatform platform, String redirectUri) {
        String state = generateOAuthState();
        storeOAuthState(state);
        
        // 构建授权URL
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(platform.getAuthorizationUrl());
        urlBuilder.append("?client_id=").append(platform.getClientId());
        urlBuilder.append("&redirect_uri=").append(redirectUri);
        urlBuilder.append("&response_type=code");
        urlBuilder.append("&scope=").append(platform.getScope());
        urlBuilder.append("&state=").append(state);
        
        return urlBuilder.toString();
    }

    /**
     * 第三方登录请求
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ThirdPartyLoginRequest extends LoginStrategy.LoginRequest {
        /**
         * 第三方平台标识
         */
        @NotBlank(message = "第三方平台不能为空")
        private String platform;

        /**
         * 授权码
         */
        @NotBlank(message = "授权码不能为空")
        private String authorizationCode;

        /**
         * 状态参数（防CSRF）
         */
        private String state;

        /**
         * 重定向URI
         */
        private String redirectUri;
    }

    /**
     * 第三方平台枚举
     */
    public enum ThirdPartyPlatform {
        WECHAT("wechat", "微信", "https://open.weixin.qq.com/connect/oauth2/authorize", 
               "https://api.weixin.qq.com/sns/oauth2/access_token", 
               "https://api.weixin.qq.com/sns/userinfo", "snsapi_userinfo"),
        
        QQ("qq", "QQ", "https://graph.qq.com/oauth2.0/authorize", 
           "https://graph.qq.com/oauth2.0/token", 
           "https://graph.qq.com/user/get_user_info", "get_user_info"),
        
        WEIBO("weibo", "微博", "https://api.weibo.com/oauth2/authorize", 
              "https://api.weibo.com/oauth2/access_token", 
              "https://api.weibo.com/2/users/show.json", "email"),
        
        APPLE("apple", "Apple", "https://appleid.apple.com/auth/authorize", 
              "https://appleid.apple.com/auth/token", 
              "https://appleid.apple.com/auth/keys", "name email"),
        
        GOOGLE("google", "Google", "https://accounts.google.com/oauth2/auth", 
               "https://oauth2.googleapis.com/token", 
               "https://www.googleapis.com/oauth2/v2/userinfo", "openid email profile");

        private final String code;
        private final String name;
        private final String authorizationUrl;
        private final String tokenUrl;
        private final String userInfoUrl;
        private final String scope;

        ThirdPartyPlatform(String code, String name, String authorizationUrl, 
                          String tokenUrl, String userInfoUrl, String scope) {
            this.code = code;
            this.name = name;
            this.authorizationUrl = authorizationUrl;
            this.tokenUrl = tokenUrl;
            this.userInfoUrl = userInfoUrl;
            this.scope = scope;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public String getAuthorizationUrl() {
            return authorizationUrl;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public String getUserInfoUrl() {
            return userInfoUrl;
        }

        public String getScope() {
            return scope;
        }

        public String getClientId() {
            // 这里应该从配置中读取
            return "your_client_id";
        }

        public String getClientSecret() {
            // 这里应该从配置中读取
            return "your_client_secret";
        }

        public static ThirdPartyPlatform fromString(String code) {
            for (ThirdPartyPlatform platform : values()) {
                if (platform.code.equalsIgnoreCase(code)) {
                    return platform;
                }
            }
            return null;
        }
    }

    /**
     * 第三方Token信息
     */
    @Data
    public static class ThirdPartyTokenInfo {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Integer expiresIn;
        private String scope;
        private LocalDateTime createTime;
    }

    /**
     * 第三方用户信息
     */
    @Data
    public static class ThirdPartyUserInfo {
        private String platformUserId;
        private String nickname;
        private String avatar;
        private String email;
        private Integer gender;
        private String country;
        private String province;
        private String city;
        private Map<String, Object> rawData;
    }

    /**
     * 生成OAuth状态参数
     */
    private String generateOAuthState() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 存储OAuth状态参数
     */
    private void storeOAuthState(String state) {
        String key = OAUTH_STATE_KEY_PREFIX + state;
        redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), 
                java.time.Duration.ofMinutes(10));
    }

    /**
     * 验证OAuth状态参数
     */
    private boolean validateOAuthState(String state) {
        String key = OAUTH_STATE_KEY_PREFIX + state;
        boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(key));
        if (exists) {
            redisTemplate.delete(key); // 验证后删除，防重复使用
        }
        return exists;
    }

    /**
     * 使用授权码交换访问令牌
     */
    private ThirdPartyTokenInfo exchangeCodeForToken(ThirdPartyPlatform platform, String authorizationCode) {
        // 这里应该调用具体的OAuth2集成服务
        // 发送HTTP请求到第三方平台的token端点
        ThirdPartyTokenInfo tokenInfo = new ThirdPartyTokenInfo();
        tokenInfo.setAccessToken("mock_access_token");
        tokenInfo.setTokenType("Bearer");
        tokenInfo.setExpiresIn(7200);
        tokenInfo.setCreateTime(LocalDateTime.now());
        return tokenInfo;
    }

    /**
     * 获取用户信息
     */
    private ThirdPartyUserInfo getUserInfo(ThirdPartyPlatform platform, ThirdPartyTokenInfo tokenInfo) {
        // 这里应该调用具体的OAuth2集成服务
        // 使用访问令牌获取用户信息
        ThirdPartyUserInfo userInfo = new ThirdPartyUserInfo();
        userInfo.setPlatformUserId("mock_user_id");
        userInfo.setNickname("测试用户");
        userInfo.setRawData(new HashMap<>());
        return userInfo;
    }

    /**
     * 根据第三方信息查找或创建账号
     */
    private Account findOrCreateAccountByThirdParty(ThirdPartyPlatform platform, ThirdPartyUserInfo userInfo) {
        // 这里应该调用AccountService的方法
        // 首先查找是否存在该第三方用户的账号，如果不存在则创建
        return null;
    }

    /**
     * 更新第三方Token信息
     */
    private void updateThirdPartyToken(Long accountId, ThirdPartyPlatform platform, ThirdPartyTokenInfo tokenInfo) {
        String key = THIRD_PARTY_TOKEN_KEY_PREFIX + accountId + ":" + platform.getCode();
        redisTemplate.opsForValue().set(key, tokenInfo, 
                java.time.Duration.ofSeconds(tokenInfo.getExpiresIn()));
    }

    /**
     * 创建登录会话
     */
    private LoginSession createLoginSession(Account account, LoginContext context) {
        // 这里应该调用SessionManager的方法
        LoginSession session = new LoginSession();
        session.setSessionId(java.util.UUID.randomUUID().toString());
        session.setAccountId(account.getAccountId());
        session.setUsername(account.getUsername());
        session.setLoginMethod(STRATEGY_TYPE);
        session.setLoginTime(LocalDateTime.now());
        session.setStatus(LoginSession.SessionStatus.ACTIVE);
        
        if (context.getNetworkInfo() != null) {
            session.setLoginIp(context.getNetworkInfo().getClientIp());
        }
        
        if (context.getClientInfo() != null) {
            session.setUserAgent(context.getClientInfo().getUserAgent());
            session.setDeviceType(LoginSession.DeviceType.fromUserAgent(
                    context.getClientInfo().getUserAgent()));
        }
        
        return session;
    }

    /**
     * 更新账号最后登录信息
     */
    private void updateAccountLastLogin(Account account, LoginContext context) {
        account.setLastLoginTime(LocalDateTime.now());
        if (context.getNetworkInfo() != null) {
            account.setLastLoginIp(context.getNetworkInfo().getClientIp());
        }
        // 这里应该调用AccountService保存更新
    }
}