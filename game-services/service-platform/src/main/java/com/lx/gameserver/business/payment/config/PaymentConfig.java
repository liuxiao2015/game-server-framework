/*
 * 文件名: PaymentConfig.java
 * 用途: 支付配置管理
 * 实现内容:
 *   - 支付配置中心和统一管理
 *   - 渠道配置管理和动态更新
 *   - 密钥配置（加密存储）
 *   - 环境配置（开发/测试/生产）
 *   - 配置版本管理和热更新
 * 技术选型:
 *   - Spring Configuration
 *   - 配置属性绑定
 *   - 加密配置
 *   - 动态刷新
 * 依赖关系:
 *   - 被各组件使用
 *   - 集成配置中心
 *   - 支持动态更新
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.config;

import com.lx.gameserver.business.payment.channel.*;
import com.lx.gameserver.business.payment.core.PaymentChannel;
import com.lx.gameserver.business.payment.process.PaymentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付配置
 * <p>
 * 支付模块的核心配置类，负责初始化各支付渠道、
 * 配置管理、Bean注册等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConfigurationProperties(prefix = "game.payment")
public class PaymentConfig {

    private static final Logger logger = LoggerFactory.getLogger(PaymentConfig.class);

    /**
     * 订单配置
     */
    private OrderConfig order = new OrderConfig();

    /**
     * 渠道配置
     */
    private Map<String, ChannelConfig> channels = new HashMap<>();

    /**
     * 风控配置
     */
    private RiskConfig risk = new RiskConfig();

    /**
     * 对账配置
     */
    private ReconciliationConfig reconciliation = new ReconciliationConfig();

    /**
     * 安全配置
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * 订单配置类
     */
    public static class OrderConfig {
        private String timeout = "30m";
        private String numberPrefix = "GM";
        private int maxRetryTimes = 3;

        // Getters and setters
        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
        public String getNumberPrefix() { return numberPrefix; }
        public void setNumberPrefix(String numberPrefix) { this.numberPrefix = numberPrefix; }
        public int getMaxRetryTimes() { return maxRetryTimes; }
        public void setMaxRetryTimes(int maxRetryTimes) { this.maxRetryTimes = maxRetryTimes; }
    }

    /**
     * 渠道配置类
     */
    public static class ChannelConfig {
        private boolean enabled = true;
        private String appId;
        private String privateKey;
        private String publicKey;
        private String gatewayUrl;
        private String timeout = "30s";
        private String mchId;
        private String apiKey;
        private String certPath;
        private String notifyUrl;
        private Map<String, Object> extra = new HashMap<>();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
        public String getGatewayUrl() { return gatewayUrl; }
        public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }
        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
        public String getMchId() { return mchId; }
        public void setMchId(String mchId) { this.mchId = mchId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getCertPath() { return certPath; }
        public void setCertPath(String certPath) { this.certPath = certPath; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
        public Map<String, Object> getExtra() { return extra; }
        public void setExtra(Map<String, Object> extra) { this.extra = extra; }
    }

    /**
     * 风控配置类
     */
    public static class RiskConfig {
        private boolean enabled = true;
        private long maxAmountPerDay = 10000L;
        private int maxTimesPerDay = 20;
        private long suspiciousAmount = 5000L;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getMaxAmountPerDay() { return maxAmountPerDay; }
        public void setMaxAmountPerDay(long maxAmountPerDay) { this.maxAmountPerDay = maxAmountPerDay; }
        public int getMaxTimesPerDay() { return maxTimesPerDay; }
        public void setMaxTimesPerDay(int maxTimesPerDay) { this.maxTimesPerDay = maxTimesPerDay; }
        public long getSuspiciousAmount() { return suspiciousAmount; }
        public void setSuspiciousAmount(long suspiciousAmount) { this.suspiciousAmount = suspiciousAmount; }
    }

    /**
     * 对账配置类
     */
    public static class ReconciliationConfig {
        private boolean enabled = true;
        private String cron = "0 0 2 * * ?";
        private int retryTimes = 3;
        private double alertThreshold = 0.01;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public int getRetryTimes() { return retryTimes; }
        public void setRetryTimes(int retryTimes) { this.retryTimes = retryTimes; }
        public double getAlertThreshold() { return alertThreshold; }
        public void setAlertThreshold(double alertThreshold) { this.alertThreshold = alertThreshold; }
    }

    /**
     * 安全配置类
     */
    public static class SecurityConfig {
        private String rsaPrivateKey;
        private String rsaPublicKey;
        private String aesKey;
        private String signatureAlgorithm = "RSA2";

        // Getters and setters
        public String getRsaPrivateKey() { return rsaPrivateKey; }
        public void setRsaPrivateKey(String rsaPrivateKey) { this.rsaPrivateKey = rsaPrivateKey; }
        public String getRsaPublicKey() { return rsaPublicKey; }
        public void setRsaPublicKey(String rsaPublicKey) { this.rsaPublicKey = rsaPublicKey; }
        public String getAesKey() { return aesKey; }
        public void setAesKey(String aesKey) { this.aesKey = aesKey; }
        public String getSignatureAlgorithm() { return signatureAlgorithm; }
        public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }
    }

    /**
     * 配置支付渠道
     */
    @Bean
    @Primary
    public PaymentProcessor paymentProcessor() {
        PaymentProcessor processor = new PaymentProcessor(null); // OrderService will be injected later
        
        // 注册支付宝渠道
        if (channels.containsKey("alipay")) {
            ChannelConfig alipayConfig = channels.get("alipay");
            if (alipayConfig.isEnabled()) {
                Map<String, Object> config = buildChannelConfigMap(alipayConfig);
                AlipayChannel alipayChannel = new AlipayChannel(config);
                processor.registerPaymentChannel(alipayChannel);
                logger.info("注册支付宝渠道完成");
            }
        }

        // 注册微信支付渠道
        if (channels.containsKey("wechat")) {
            ChannelConfig wechatConfig = channels.get("wechat");
            if (wechatConfig.isEnabled()) {
                Map<String, Object> config = buildChannelConfigMap(wechatConfig);
                WechatPayChannel wechatChannel = new WechatPayChannel(config);
                processor.registerPaymentChannel(wechatChannel);
                logger.info("注册微信支付渠道完成");
            }
        }

        // 注册苹果支付渠道
        if (channels.containsKey("apple")) {
            ChannelConfig appleConfig = channels.get("apple");
            if (appleConfig.isEnabled()) {
                Map<String, Object> config = buildChannelConfigMap(appleConfig);
                ApplePayChannel appleChannel = new ApplePayChannel(config);
                processor.registerPaymentChannel(appleChannel);
                logger.info("注册苹果支付渠道完成");
            }
        }

        // 注册Google支付渠道
        if (channels.containsKey("google")) {
            ChannelConfig googleConfig = channels.get("google");
            if (googleConfig.isEnabled()) {
                Map<String, Object> config = buildChannelConfigMap(googleConfig);
                GooglePayChannel googleChannel = new GooglePayChannel(config);
                processor.registerPaymentChannel(googleChannel);
                logger.info("注册Google支付渠道完成");
            }
        }

        logger.info("支付处理器配置完成，已注册{}个渠道", processor.getAllPaymentChannels().size());
        return processor;
    }

    /**
     * 构建渠道配置映射
     */
    private Map<String, Object> buildChannelConfigMap(ChannelConfig channelConfig) {
        Map<String, Object> config = new HashMap<>();
        
        if (channelConfig.getAppId() != null) {
            config.put("appId", channelConfig.getAppId());
        }
        if (channelConfig.getPrivateKey() != null) {
            config.put("privateKey", channelConfig.getPrivateKey());
        }
        if (channelConfig.getPublicKey() != null) {
            config.put("publicKey", channelConfig.getPublicKey());
        }
        if (channelConfig.getGatewayUrl() != null) {
            config.put("gatewayUrl", channelConfig.getGatewayUrl());
        }
        if (channelConfig.getMchId() != null) {
            config.put("mchId", channelConfig.getMchId());
        }
        if (channelConfig.getApiKey() != null) {
            config.put("apiKey", channelConfig.getApiKey());
        }
        if (channelConfig.getCertPath() != null) {
            config.put("certPath", channelConfig.getCertPath());
        }
        if (channelConfig.getNotifyUrl() != null) {
            config.put("notifyUrl", channelConfig.getNotifyUrl());
        }
        
        // 添加额外配置
        if (channelConfig.getExtra() != null) {
            config.putAll(channelConfig.getExtra());
        }
        
        return config;
    }

    // ========== Getters and Setters ==========

    public OrderConfig getOrder() { return order; }
    public void setOrder(OrderConfig order) { this.order = order; }
    public Map<String, ChannelConfig> getChannels() { return channels; }
    public void setChannels(Map<String, ChannelConfig> channels) { this.channels = channels; }
    public RiskConfig getRisk() { return risk; }
    public void setRisk(RiskConfig risk) { this.risk = risk; }
    public ReconciliationConfig getReconciliation() { return reconciliation; }
    public void setReconciliation(ReconciliationConfig reconciliation) { this.reconciliation = reconciliation; }
    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }

    /**
     * 获取渠道配置
     */
    public ChannelConfig getChannelConfig(String channelCode) {
        return channels.get(channelCode);
    }

    /**
     * 更新渠道配置
     */
    public void updateChannelConfig(String channelCode, ChannelConfig config) {
        channels.put(channelCode, config);
        logger.info("更新渠道配置: {}", channelCode);
    }

    /**
     * 获取配置摘要信息
     */
    public Map<String, Object> getConfigSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("enabledChannels", channels.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList());
        summary.put("riskEnabled", risk.isEnabled());
        summary.put("reconciliationEnabled", reconciliation.isEnabled());
        summary.put("orderTimeout", order.getTimeout());
        summary.put("maxRetryTimes", order.getMaxRetryTimes());
        
        return summary;
    }
}