/*
 * 文件名: DeviceAuthProvider.java
 * 用途: 设备指纹认证提供者
 * 实现内容:
 *   - 设备指纹收集与验证
 *   - 设备风险评估
 *   - 设备信誉管理
 *   - 设备绑定关系验证
 * 技术选型:
 *   - Spring Security AuthenticationProvider
 *   - 设备指纹识别技术
 *   - 风险评分机制
 * 依赖关系:
 *   - 被GameAuthenticationManager使用
 *   - 使用UserDetailsService获取用户
 *   - 依赖DeviceBinding服务
 */
package com.lx.gameserver.frame.security.auth.provider;

import com.lx.gameserver.frame.security.auth.GameUserDetails;
import com.lx.gameserver.frame.security.risk.AnomalyDetection;
import com.lx.gameserver.frame.security.session.DeviceBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备指纹认证提供者
 * <p>
 * 实现基于设备指纹的认证机制，通过收集和分析设备特征信息，
 * 验证用户设备的真实性和可信度，提供额外的安全保障。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class DeviceAuthProvider implements AuthenticationProvider {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired(required = false)
    private DeviceBinding deviceBinding;

    @Autowired(required = false)
    private AnomalyDetection anomalyDetection;

    /**
     * 设备风险阈值（0-100，越高要求越严格）
     */
    private static final int DEVICE_RISK_THRESHOLD = 75;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 获取认证信息
        String username = authentication.getName();
        Map<String, String> deviceInfo = extractDeviceInfo(authentication);

        if (deviceInfo == null || deviceInfo.isEmpty()) {
            log.debug("设备信息为空");
            throw new BadCredentialsException("缺少设备信息");
        }

        log.debug("处理设备认证: {}, 设备ID: {}", username, deviceInfo.get("deviceId"));

        // 加载用户信息
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            log.debug("用户不存在: {}", username);
            throw new BadCredentialsException("用户不存在");
        }

        // 验证设备绑定关系
        if (!verifyDeviceBinding(username, deviceInfo)) {
            log.warn("设备绑定验证失败: {} 设备ID: {}", username, deviceInfo.get("deviceId"));
            throw new BadCredentialsException("设备未授权");
        }

        // 评估设备风险
        int riskScore = assessDeviceRisk(username, deviceInfo);
        if (riskScore > DEVICE_RISK_THRESHOLD) {
            log.warn("设备风险评分过高: {} 分数: {}", username, riskScore);
            throw new BadCredentialsException("设备安全风险过高");
        }

        // 构造已认证的认证对象
        UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        
        // 设置附加信息
        Map<String, Object> details = new HashMap<>(deviceInfo);
        details.put("authenticationType", "DEVICE");
        details.put("deviceRiskScore", riskScore);
        result.setDetails(details);

        // 如果是GameUserDetails，可以更新会话状态
        if (userDetails instanceof GameUserDetails) {
            ((GameUserDetails) userDetails).setSessionStatus("DEVICE_VERIFIED");
            ((GameUserDetails) userDetails).setAttribute("deviceVerified", true);
        }

        log.debug("设备认证成功: {} 设备ID: {}", username, deviceInfo.get("deviceId"));
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 从认证对象中提取设备信息
     *
     * @param authentication 认证信息
     * @return 设备信息映射，如果不可用则返回null
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractDeviceInfo(Authentication authentication) {
        if (authentication.getDetails() instanceof Map) {
            return (Map<String, String>) authentication.getDetails();
        }
        return new HashMap<>();
    }

    /**
     * 验证用户与设备绑定关系
     *
     * @param username 用户名
     * @param deviceInfo 设备信息
     * @return 如果绑定关系有效返回true，否则返回false
     */
    private boolean verifyDeviceBinding(String username, Map<String, String> deviceInfo) {
        if (deviceBinding == null) {
            // 如果设备绑定服务不可用，放行所有请求
            log.debug("设备绑定服务不可用，跳过验证");
            return true;
        }

        String deviceId = deviceInfo.get("deviceId");
        if (!StringUtils.hasText(deviceId)) {
            log.debug("设备ID为空");
            return false;
        }

        return deviceBinding.verifyBinding(username, deviceId);
    }

    /**
     * 评估设备风险得分
     *
     * @param username 用户名
     * @param deviceInfo 设备信息
     * @return 风险评分（0-100，越高风险越大）
     */
    private int assessDeviceRisk(String username, Map<String, String> deviceInfo) {
        if (anomalyDetection == null) {
            // 如果异常检测服务不可用，默认低风险
            return 0;
        }

        try {
            String deviceId = deviceInfo.get("deviceId");
            String ipAddress = deviceInfo.get("remoteAddress");
            String userAgent = deviceInfo.get("userAgent");
            String osInfo = deviceInfo.get("osInfo");
            
            return anomalyDetection.assessDeviceRisk(username, deviceId, ipAddress, userAgent, osInfo);
        } catch (Exception e) {
            log.error("设备风险评估失败", e);
            // 发生错误时，返回中等风险级别
            return 50;
        }
    }
}