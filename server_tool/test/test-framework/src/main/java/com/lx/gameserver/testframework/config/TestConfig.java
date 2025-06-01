/*
 * 文件名: TestConfig.java
 * 用途: 测试框架配置管理
 * 内容: 
 *   - 测试环境配置
 *   - 服务配置管理
 *   - 数据源配置
 *   - 执行策略配置
 *   - 报告配置
 * 技术选型: 
 *   - Spring Boot配置
 *   - YAML配置文件
 *   - 环境变量支持
 *   - 配置验证
 * 依赖关系: 
 *   - 被TestFramework使用
 *   - 与Spring配置集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.*;

/**
 * 测试框架配置类
 * <p>
 * 定义测试框架的所有配置项，包括环境配置、服务配置、
 * 数据源配置、执行策略和报告配置。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Data
@Slf4j
@Validated
@Configuration
@ConfigurationProperties(prefix = "test.framework")
public class TestConfig {
    
    /**
     * 基础配置
     */
    @Valid
    private BaseConfig base = new BaseConfig();
    
    /**
     * 环境配置
     */
    @Valid
    private Map<String, EnvironmentConfig> environments = new HashMap<>();
    
    /**
     * 性能测试配置
     */
    @Valid
    private PerformanceConfig performance = new PerformanceConfig();
    
    /**
     * 压力测试配置
     */
    @Valid
    private StressConfig stress = new StressConfig();
    
    /**
     * 报告配置
     */
    @Valid
    private ReportConfig report = new ReportConfig();
    
    /**
     * 监控配置
     */
    @Valid
    private MonitorConfig monitor = new MonitorConfig();
    
    /**
     * 基础配置
     */
    @Data
    public static class BaseConfig {
        /**
         * 是否启用并行执行
         */
        private boolean parallelExecution = true;
        
        /**
         * 最大并行测试数
         */
        @Min(1)
        @Max(100)
        private int maxParallelTests = 10;
        
        /**
         * 默认超时时间
         */
        @NotNull
        private Duration timeout = Duration.ofMinutes(5);
        
        /**
         * 重试次数
         */
        @Min(0)
        @Max(10)
        private int retryCount = 3;
        
        /**
         * 失败后是否继续
         */
        private boolean continueOnFailure = true;
    }
    
    /**
     * 环境配置
     */
    @Data
    public static class EnvironmentConfig {
        /**
         * 环境名称
         */
        @NotBlank
        private String name;
        
        /**
         * 环境描述
         */
        private String description;
        
        /**
         * 服务配置列表
         */
        @Valid
        private List<ServiceConfig> services = new ArrayList<>();
        
        /**
         * 环境变量
         */
        private Map<String, String> variables = new HashMap<>();
        
        /**
         * 是否为默认环境
         */
        private boolean defaultEnvironment = false;
    }
    
    /**
     * 服务配置
     */
    @Data
    public static class ServiceConfig {
        /**
         * 服务名称
         */
        @NotBlank
        private String name;
        
        /**
         * Docker镜像
         */
        private String image;
        
        /**
         * 端口号
         */
        @Min(1)
        @Max(65535)
        private int port;
        
        /**
         * 主机地址
         */
        private String host = "localhost";
        
        /**
         * 环境变量
         */
        private Map<String, String> environment = new HashMap<>();
        
        /**
         * 启动超时时间
         */
        private Duration startupTimeout = Duration.ofMinutes(2);
        
        /**
         * 健康检查URL
         */
        private String healthCheckUrl;
    }
    
    /**
     * 性能测试配置
     */
    @Data
    public static class PerformanceConfig {
        /**
         * 预热时间
         */
        @NotNull
        private Duration warmUpDuration = Duration.ofMinutes(1);
        
        /**
         * 测试持续时间
         */
        @NotNull
        private Duration testDuration = Duration.ofMinutes(5);
        
        /**
         * 递增时间
         */
        @NotNull
        private Duration rampUpTime = Duration.ofSeconds(30);
        
        /**
         * 目标TPS
         */
        @Min(1)
        private int targetTps = 10000;
        
        /**
         * 最大并发数
         */
        @Min(1)
        private int maxConcurrency = 1000;
        
        /**
         * 采样间隔
         */
        @NotNull
        private Duration samplingInterval = Duration.ofSeconds(1);
    }
    
    /**
     * 压力测试配置
     */
    @Data
    public static class StressConfig {
        /**
         * 最大客户端数
         */
        @Min(1)
        private int maxClients = 10000;
        
        /**
         * 连接速率
         */
        @NotBlank
        private String connectionRate = "100/s";
        
        /**
         * 消息速率
         */
        @NotBlank
        private String messageRate = "1000/s";
        
        /**
         * 测试场景
         */
        private List<String> testScenarios = Arrays.asList("login-storm", "battle-stress");
        
        /**
         * 压力递增策略
         */
        private String rampUpStrategy = "linear";
        
        /**
         * 持续时间
         */
        @NotNull
        private Duration duration = Duration.ofMinutes(10);
    }
    
    /**
     * 报告配置
     */
    @Data
    public static class ReportConfig {
        /**
         * 报告格式
         */
        @NotBlank
        private String format = "html";
        
        /**
         * 是否包含日志
         */
        private boolean includeLogs = true;
        
        /**
         * 失败时是否截图
         */
        private boolean screenshotOnFailure = true;
        
        /**
         * 保留天数
         */
        @Min(1)
        private int retentionDays = 30;
        
        /**
         * 输出目录
         */
        private String outputDirectory = "target/test-reports";
        
        /**
         * 邮件通知配置
         */
        @Valid
        private EmailConfig email = new EmailConfig();
    }
    
    /**
     * 邮件配置
     */
    @Data
    public static class EmailConfig {
        /**
         * 是否启用邮件通知
         */
        private boolean enabled = false;
        
        /**
         * SMTP服务器
         */
        private String smtpHost;
        
        /**
         * SMTP端口
         */
        @Min(1)
        @Max(65535)
        private int smtpPort = 587;
        
        /**
         * 用户名
         */
        private String username;
        
        /**
         * 密码
         */
        private String password;
        
        /**
         * 发件人
         */
        private String from;
        
        /**
         * 收件人列表
         */
        private List<String> recipients = new ArrayList<>();
    }
    
    /**
     * 监控配置
     */
    @Data
    public static class MonitorConfig {
        /**
         * 指标收集间隔
         */
        @NotNull
        private Duration metricsInterval = Duration.ofSeconds(10);
        
        /**
         * 是否启用资源监控
         */
        private boolean resourceMonitor = true;
        
        /**
         * 告警阈值
         */
        @Valid
        private AlertThreshold alertThreshold = new AlertThreshold();
        
        /**
         * Prometheus配置
         */
        @Valid
        private PrometheusConfig prometheus = new PrometheusConfig();
    }
    
    /**
     * 告警阈值配置
     */
    @Data
    public static class AlertThreshold {
        /**
         * CPU使用率阈值（百分比）
         */
        @Min(0)
        @Max(100)
        private int cpu = 80;
        
        /**
         * 内存使用率阈值（百分比）
         */
        @Min(0)
        @Max(100)
        private int memory = 90;
        
        /**
         * 错误率阈值（百分比）
         */
        @Min(0)
        @Max(100)
        private int errorRate = 5;
        
        /**
         * 响应时间阈值（毫秒）
         */
        @Min(0)
        private long responseTime = 1000;
    }
    
    /**
     * Prometheus配置
     */
    @Data
    public static class PrometheusConfig {
        /**
         * 是否启用Prometheus
         */
        private boolean enabled = false;
        
        /**
         * Prometheus服务器地址
         */
        private String serverUrl = "http://localhost:9090";
        
        /**
         * 指标前缀
         */
        private String metricsPrefix = "test_framework";
        
        /**
         * 推送网关地址
         */
        private String pushGatewayUrl;
    }
    
    /**
     * 获取指定环境的配置
     * 
     * @param environmentName 环境名称
     * @return 环境配置
     */
    public EnvironmentConfig getEnvironment(String environmentName) {
        return environments.get(environmentName);
    }
    
    /**
     * 获取默认环境配置
     * 
     * @return 默认环境配置
     */
    public EnvironmentConfig getDefaultEnvironment() {
        return environments.values().stream()
            .filter(EnvironmentConfig::isDefaultEnvironment)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 验证配置
     * 
     * @return 验证结果
     */
    public boolean validate() {
        try {
            // 检查基础配置
            if (base.getMaxParallelTests() <= 0) {
                log.error("并行测试数必须大于0");
                return false;
            }
            
            // 检查环境配置
            if (environments.isEmpty()) {
                log.warn("未配置测试环境");
            }
            
            // 检查是否有默认环境
            long defaultCount = environments.values().stream()
                .mapToLong(env -> env.isDefaultEnvironment() ? 1 : 0)
                .sum();
            
            if (defaultCount > 1) {
                log.error("不能有多个默认环境");
                return false;
            }
            
            log.info("测试框架配置验证通过");
            return true;
            
        } catch (Exception e) {
            log.error("配置验证失败", e);
            return false;
        }
    }
}