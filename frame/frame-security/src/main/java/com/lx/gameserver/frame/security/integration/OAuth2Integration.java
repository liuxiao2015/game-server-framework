/*
 * 文件名: OAuth2Integration.java
 * 用途: OAuth2集成服务
 * 实现内容:
 *   - 第三方登录集成
 *   - 授权码模式支持
 *   - 令牌刷新机制
 *   - 用户信息映射
 *   - 多平台适配
 * 技术选型:
 *   - Spring Security OAuth2客户端
 *   - 支持微信、QQ、Google等平台
 *   - RESTful API集成
 * 依赖关系:
 *   - 被ThirdPartyAuthProvider使用
 *   - 依赖HTTP客户端
 */
package com.lx.gameserver.frame.security.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2集成服务
 * <p>
 * 提供第三方平台OAuth2认证集成功能，支持多种主流平台的
 * 授权码模式、令牌验证、用户信息获取等操作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class OAuth2Integration {
    
    @Autowired(required = false)
    private RestTemplate restTemplate;
    
    /**
     * 平台配置映射
     */
    private final Map<String, PlatformConfig> platformConfigs = new HashMap<>();
    
    /**
     * 构造函数，初始化平台配置
     */
    public OAuth2Integration() {
        initializePlatformConfigs();
        
        // 如果没有RestTemplate，创建一个默认的
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
        }
    }

    /**
     * 根据授权码获取访问令牌
     *
     * @param platform 平台名称
     * @param code 授权码
     * @param redirectUri 重定向URI
     * @return 访问令牌信息
     */
    public OAuth2TokenResponse getAccessToken(String platform, String code, String redirectUri) {
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(code)) {
            log.warn("获取访问令牌失败：参数无效");
            return null;
        }
        
        PlatformConfig config = platformConfigs.get(platform.toLowerCase());
        if (config == null) {
            log.warn("不支持的平台: {}", platform);
            return null;
        }
        
        try {
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", config.getClientId());
            params.add("client_secret", config.getClientSecret());
            params.add("code", code);
            if (StringUtils.hasText(redirectUri)) {
                params.add("redirect_uri", redirectUri);
            }
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // 发送请求
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    config.getTokenUrl(), request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return OAuth2TokenResponse.builder()
                        .accessToken((String) responseBody.get("access_token"))
                        .refreshToken((String) responseBody.get("refresh_token"))
                        .expiresIn(getIntValue(responseBody, "expires_in"))
                        .scope((String) responseBody.get("scope"))
                        .tokenType((String) responseBody.get("token_type"))
                        .build();
            } else {
                log.warn("获取访问令牌失败: platform={}, status={}", platform, response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("获取访问令牌异常: platform=" + platform, e);
            return null;
        }
    }

    /**
     * 验证访问令牌
     *
     * @param platform 平台名称
     * @param accessToken 访问令牌
     * @return 令牌是否有效
     */
    public boolean validateAccessToken(String platform, String accessToken) {
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(accessToken)) {
            return false;
        }
        
        PlatformConfig config = platformConfigs.get(platform.toLowerCase());
        if (config == null) {
            return false;
        }
        
        try {
            // 通过获取用户信息来验证令牌有效性
            UserInfo userInfo = getUserInfo(platform, accessToken);
            return userInfo != null && StringUtils.hasText(userInfo.getId());
            
        } catch (Exception e) {
            log.error("验证访问令牌异常: platform=" + platform, e);
            return false;
        }
    }

    /**
     * 获取用户信息
     *
     * @param platform 平台名称
     * @param accessToken 访问令牌
     * @return 用户信息
     */
    public UserInfo getUserInfo(String platform, String accessToken) {
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(accessToken)) {
            log.warn("获取用户信息失败：参数无效");
            return null;
        }
        
        PlatformConfig config = platformConfigs.get(platform.toLowerCase());
        if (config == null) {
            log.warn("不支持的平台: {}", platform);
            return null;
        }
        
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            
            HttpEntity<?> request = new HttpEntity<>(headers);
            
            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                    config.getUserInfoUrl(), HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return mapToUserInfo(platform, responseBody);
            } else {
                log.warn("获取用户信息失败: platform={}, status={}", platform, response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("获取用户信息异常: platform=" + platform, e);
            return null;
        }
    }

    /**
     * 刷新访问令牌
     *
     * @param platform 平台名称
     * @param refreshToken 刷新令牌
     * @return 新的令牌信息
     */
    public OAuth2TokenResponse refreshAccessToken(String platform, String refreshToken) {
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(refreshToken)) {
            log.warn("刷新访问令牌失败：参数无效");
            return null;
        }
        
        PlatformConfig config = platformConfigs.get(platform.toLowerCase());
        if (config == null) {
            log.warn("不支持的平台: {}", platform);
            return null;
        }
        
        try {
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("client_id", config.getClientId());
            params.add("client_secret", config.getClientSecret());
            params.add("refresh_token", refreshToken);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // 发送请求
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    config.getTokenUrl(), request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return OAuth2TokenResponse.builder()
                        .accessToken((String) responseBody.get("access_token"))
                        .refreshToken((String) responseBody.get("refresh_token"))
                        .expiresIn(getIntValue(responseBody, "expires_in"))
                        .scope((String) responseBody.get("scope"))
                        .tokenType((String) responseBody.get("token_type"))
                        .build();
            } else {
                log.warn("刷新访问令牌失败: platform={}, status={}", platform, response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("刷新访问令牌异常: platform=" + platform, e);
            return null;
        }
    }

    /**
     * 获取授权URL
     *
     * @param platform 平台名称
     * @param redirectUri 重定向URI
     * @param state 状态参数
     * @return 授权URL
     */
    public String getAuthorizationUrl(String platform, String redirectUri, String state) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        
        PlatformConfig config = platformConfigs.get(platform.toLowerCase());
        if (config == null) {
            return null;
        }
        
        StringBuilder url = new StringBuilder(config.getAuthUrl());
        url.append("?response_type=code");
        url.append("&client_id=").append(config.getClientId());
        
        if (StringUtils.hasText(redirectUri)) {
            url.append("&redirect_uri=").append(redirectUri);
        }
        
        if (StringUtils.hasText(config.getScope())) {
            url.append("&scope=").append(config.getScope());
        }
        
        if (StringUtils.hasText(state)) {
            url.append("&state=").append(state);
        }
        
        return url.toString();
    }

    /**
     * 初始化平台配置
     */
    private void initializePlatformConfigs() {
        // 微信平台配置
        platformConfigs.put("wechat", PlatformConfig.builder()
                .clientId("${oauth2.wechat.client-id:}")
                .clientSecret("${oauth2.wechat.client-secret:}")
                .authUrl("https://open.weixin.qq.com/connect/oauth2/authorize")
                .tokenUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .userInfoUrl("https://api.weixin.qq.com/sns/userinfo")
                .scope("snsapi_userinfo")
                .build());
        
        // QQ平台配置
        platformConfigs.put("qq", PlatformConfig.builder()
                .clientId("${oauth2.qq.client-id:}")
                .clientSecret("${oauth2.qq.client-secret:}")
                .authUrl("https://graph.qq.com/oauth2.0/authorize")
                .tokenUrl("https://graph.qq.com/oauth2.0/token")
                .userInfoUrl("https://graph.qq.com/user/get_user_info")
                .scope("get_user_info")
                .build());
        
        // Google平台配置
        platformConfigs.put("google", PlatformConfig.builder()
                .clientId("${oauth2.google.client-id:}")
                .clientSecret("${oauth2.google.client-secret:}")
                .authUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUrl("https://oauth2.googleapis.com/token")
                .userInfoUrl("https://www.googleapis.com/oauth2/v2/userinfo")
                .scope("openid profile email")
                .build());
        
        // GitHub平台配置
        platformConfigs.put("github", PlatformConfig.builder()
                .clientId("${oauth2.github.client-id:}")
                .clientSecret("${oauth2.github.client-secret:}")
                .authUrl("https://github.com/login/oauth/authorize")
                .tokenUrl("https://github.com/login/oauth/access_token")
                .userInfoUrl("https://api.github.com/user")
                .scope("user:email")
                .build());
    }

    /**
     * 映射为用户信息对象
     *
     * @param platform 平台名称
     * @param responseBody 响应体
     * @return 用户信息
     */
    private UserInfo mapToUserInfo(String platform, Map<String, Object> responseBody) {
        UserInfo.UserInfoBuilder builder = UserInfo.builder().platform(platform);
        
        switch (platform.toLowerCase()) {
            case "wechat":
                builder.id((String) responseBody.get("openid"))
                        .nickname((String) responseBody.get("nickname"))
                        .avatar((String) responseBody.get("headimgurl"))
                        .gender(getIntValue(responseBody, "sex"))
                        .country((String) responseBody.get("country"))
                        .province((String) responseBody.get("province"))
                        .city((String) responseBody.get("city"));
                break;
                
            case "qq":
                builder.id((String) responseBody.get("openid"))
                        .nickname((String) responseBody.get("nickname"))
                        .avatar((String) responseBody.get("figureurl_qq_2"))
                        .gender("男".equals(responseBody.get("gender")) ? 1 : 2);
                break;
                
            case "google":
                builder.id((String) responseBody.get("id"))
                        .nickname((String) responseBody.get("name"))
                        .avatar((String) responseBody.get("picture"))
                        .email((String) responseBody.get("email"));
                break;
                
            case "github":
                builder.id(String.valueOf(responseBody.get("id")))
                        .nickname((String) responseBody.get("login"))
                        .avatar((String) responseBody.get("avatar_url"))
                        .email((String) responseBody.get("email"));
                break;
                
            default:
                log.warn("未知平台类型: {}", platform);
                return null;
        }
        
        return builder.build();
    }

    /**
     * 获取整数值
     *
     * @param map 数据映射
     * @param key 键名
     * @return 整数值
     */
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 平台配置类
     */
    @lombok.Data
    @lombok.Builder
    private static class PlatformConfig {
        private String clientId;
        private String clientSecret;
        private String authUrl;
        private String tokenUrl;
        private String userInfoUrl;
        private String scope;
    }

    /**
     * OAuth2令牌响应类
     */
    @lombok.Data
    @lombok.Builder
    public static class OAuth2TokenResponse {
        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
        private String scope;
        private String tokenType;
    }

    /**
     * 用户信息类
     */
    @lombok.Data
    @lombok.Builder
    public static class UserInfo {
        private String platform;
        private String id;
        private String nickname;
        private String avatar;
        private String email;
        private Integer gender;
        private String country;
        private String province;
        private String city;
    }
}