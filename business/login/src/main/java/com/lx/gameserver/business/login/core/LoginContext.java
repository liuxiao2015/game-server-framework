/*
 * 文件名: LoginContext.java
 * 用途: 登录上下文
 * 实现内容:
 *   - 登录过程中的上下文信息
 *   - 客户端信息收集和管理
 *   - 设备指纹信息记录
 *   - 网络环境分析
 *   - 风险评估数据收集
 * 技术选型:
 *   - 线程本地存储
 *   - JSON序列化
 *   - IP地理位置解析
 *   - 设备指纹算法
 * 依赖关系:
 *   - 被各种登录策略使用
 *   - 传递给风险评估模块
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.login.core;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * 登录上下文
 * <p>
 * 封装登录过程中的所有上下文信息，包括客户端信息、设备指纹、
 * 网络环境、风险评估数据等，为登录策略和安全检查提供全面的信息支持。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Data
@Builder
public class LoginContext {

    /**
     * 客户端信息
     */
    private ClientInfo clientInfo;

    /**
     * 设备指纹信息
     */
    private DeviceFingerprint deviceFingerprint;

    /**
     * 网络环境信息
     */
    private NetworkInfo networkInfo;

    /**
     * 风险评估数据
     */
    private RiskAssessmentData riskData;

    /**
     * 扩展参数
     */
    @Builder.Default
    private Map<String, Object> extraParams = new HashMap<>();

    /**
     * 创建时间
     */
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 客户端信息
     */
    @Data
    @Builder
    public static class ClientInfo {
        /**
         * 应用版本
         */
        private String appVersion;

        /**
         * 渠道标识
         */
        private String channel;

        /**
         * 平台标识（iOS、Android、Web等）
         */
        private String platform;

        /**
         * 操作系统
         */
        private String operatingSystem;

        /**
         * 操作系统版本
         */
        private String osVersion;

        /**
         * 设备型号
         */
        private String deviceModel;

        /**
         * 设备品牌
         */
        private String deviceBrand;

        /**
         * 屏幕分辨率
         */
        private String screenResolution;

        /**
         * 用户代理
         */
        private String userAgent;

        /**
         * 语言设置
         */
        private String language;

        /**
         * 时区
         */
        private String timezone;
    }

    /**
     * 设备指纹信息
     */
    @Data
    @Builder
    public static class DeviceFingerprint {
        /**
         * 设备唯一标识
         */
        private String deviceId;

        /**
         * 设备指纹哈希
         */
        private String fingerprintHash;

        /**
         * MAC地址
         */
        private String macAddress;

        /**
         * CPU信息
         */
        private String cpuInfo;

        /**
         * 内存信息
         */
        private String memoryInfo;

        /**
         * 存储信息
         */
        private String storageInfo;

        /**
         * 网络接口信息
         */
        private String networkInterface;

        /**
         * 安装的应用列表哈希
         */
        private String installedAppsHash;

        /**
         * 浏览器特征（Web端）
         */
        private String browserFeatures;

        /**
         * Canvas指纹（Web端）
         */
        private String canvasFingerprint;

        /**
         * 字体列表（Web端）
         */
        private String fontList;
    }

    /**
     * 网络环境信息
     */
    @Data
    @Builder
    public static class NetworkInfo {
        /**
         * 客户端IP地址
         */
        private String clientIp;

        /**
         * 真实IP地址（经过代理的情况）
         */
        private String realIp;

        /**
         * 网络类型（WiFi、4G、5G等）
         */
        private String networkType;

        /**
         * 运营商信息
         */
        private String carrier;

        /**
         * 地理位置信息
         */
        private GeoLocation geoLocation;

        /**
         * 是否使用代理
         */
        private Boolean usingProxy;

        /**
         * 是否使用VPN
         */
        private Boolean usingVpn;

        /**
         * 代理类型
         */
        private String proxyType;

        /**
         * DNS服务器
         */
        private String dnsServer;
    }

    /**
     * 地理位置信息
     */
    @Data
    @Builder
    public static class GeoLocation {
        /**
         * 国家
         */
        private String country;

        /**
         * 国家代码
         */
        private String countryCode;

        /**
         * 省份/州
         */
        private String province;

        /**
         * 城市
         */
        private String city;

        /**
         * 区县
         */
        private String district;

        /**
         * 经度
         */
        private Double longitude;

        /**
         * 纬度
         */
        private Double latitude;

        /**
         * ISP信息
         */
        private String isp;

        /**
         * ASN信息
         */
        private String asn;
    }

    /**
     * 风险评估数据
     */
    @Data
    @Builder
    public static class RiskAssessmentData {
        /**
         * 风险评分（0-100）
         */
        private Integer riskScore;

        /**
         * 风险等级
         */
        private RiskLevel riskLevel;

        /**
         * 异常登录标记
         */
        private Boolean abnormalLogin;

        /**
         * 异常原因列表
         */
        private java.util.List<String> abnormalReasons;

        /**
         * 设备信任度（0-100）
         */
        private Integer deviceTrustScore;

        /**
         * IP信任度（0-100）
         */
        private Integer ipTrustScore;

        /**
         * 行为信任度（0-100）
         */
        private Integer behaviorTrustScore;

        /**
         * 是否需要额外验证
         */
        private Boolean needExtraVerification;

        /**
         * 建议的验证方式
         */
        private java.util.List<String> suggestedVerificationMethods;
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        /**
         * 低风险
         */
        LOW(1, "低风险"),
        
        /**
         * 中风险
         */
        MEDIUM(2, "中风险"),
        
        /**
         * 高风险
         */
        HIGH(3, "高风险"),
        
        /**
         * 极高风险
         */
        CRITICAL(4, "极高风险");

        private final int code;
        private final String description;

        RiskLevel(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static RiskLevel fromScore(int score) {
            if (score <= 30) {
                return LOW;
            } else if (score <= 60) {
                return MEDIUM;
            } else if (score <= 85) {
                return HIGH;
            } else {
                return CRITICAL;
            }
        }
    }

    /**
     * 添加扩展参数
     */
    public void addExtraParam(String key, Object value) {
        if (extraParams == null) {
            extraParams = new HashMap<>();
        }
        extraParams.put(key, value);
    }

    /**
     * 获取扩展参数
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtraParam(String key, Class<T> type) {
        if (extraParams == null) {
            return null;
        }
        Object value = extraParams.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * 检查是否为高风险登录
     */
    public boolean isHighRisk() {
        return riskData != null && 
               (RiskLevel.HIGH.equals(riskData.getRiskLevel()) || 
                RiskLevel.CRITICAL.equals(riskData.getRiskLevel()));
    }

    /**
     * 检查是否需要额外验证
     */
    public boolean needExtraVerification() {
        return riskData != null && 
               Boolean.TRUE.equals(riskData.getNeedExtraVerification());
    }
}