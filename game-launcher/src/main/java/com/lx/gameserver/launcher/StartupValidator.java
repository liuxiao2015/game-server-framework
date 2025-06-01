/*
 * 文件名: StartupValidator.java
 * 用途: 启动前验证器
 * 内容: 
 *   - 端口冲突检测
 *   - 依赖检查验证
 *   - 资源可用性检查
 *   - 环境配置验证
 * 技术选型: 
 *   - Java网络编程API
 *   - Spring Environment配置
 *   - 并发检测机制
 * 依赖关系: 
 *   - 在Bootstrap启动前执行
 *   - 为ServiceManager提供验证服务
 */
package com.lx.gameserver.launcher;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 启动前验证器
 * <p>
 * 在服务启动前进行各种预检查，确保启动环境符合要求：
 * 1. 端口可用性检查
 * 2. 依赖服务连通性检查
 * 3. 资源文件存在性检查
 * 4. 配置参数有效性检查
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-06-01
 */
@Component
public class StartupValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupValidator.class);
    
    /**
     * 执行启动前验证
     *
     * @param environment Spring环境配置
     * @return 验证结果
     */
    public ValidationResult validateStartupEnvironment(Environment environment) {
        logger.info("开始执行启动前环境验证...");
        
        ValidationResult result = new ValidationResult();
        
        try {
            // 1. 端口冲突检测
            validatePorts(environment, result);
            
            // 2. 依赖服务检查
            validateDependencies(environment, result);
            
            // 3. 资源可用性检查
            validateResources(result);
            
            // 4. 配置有效性检查
            validateConfiguration(environment, result);
            
        } catch (Exception e) {
            logger.error("启动验证过程中发生异常", e);
            result.addError("验证过程异常: " + e.getMessage());
        }
        
        if (result.hasErrors()) {
            logger.error("启动验证失败，发现 {} 个错误", result.getErrors().size());
            result.getErrors().forEach(error -> logger.error("  - {}", error));
        } else {
            logger.info("启动验证通过，环境检查完成");
        }
        
        return result;
    }
    
    /**
     * 验证端口可用性
     */
    private void validatePorts(Environment environment, ValidationResult result) {
        logger.info("检查端口可用性...");
        
        // 获取需要检查的端口
        List<Integer> portsToCheck = new ArrayList<>();
        
        // 主服务端口
        String serverPort = environment.getProperty("server.port", "8080");
        portsToCheck.add(Integer.parseInt(serverPort));
        
        // 管理端口
        String managementPort = environment.getProperty("management.server.port", "8081");
        if (!managementPort.equals(serverPort)) {
            portsToCheck.add(Integer.parseInt(managementPort));
        }
        
        // RPC端口
        String rpcPort = environment.getProperty("dubbo.protocol.port", "20880");
        portsToCheck.add(Integer.parseInt(rpcPort));
        
        // 网关端口
        String gatewayPort = environment.getProperty("game.gateway.port", "9000");
        portsToCheck.add(Integer.parseInt(gatewayPort));
        
        for (Integer port : portsToCheck) {
            if (!isPortAvailable(port)) {
                result.addError(String.format("端口 %d 已被占用", port));
            } else {
                logger.debug("端口 {} 可用", port);
            }
        }
    }
    
    /**
     * 验证依赖服务连通性
     */
    private void validateDependencies(Environment environment, ValidationResult result) {
        logger.info("检查依赖服务连通性...");
        
        // Redis连通性检查
        validateRedisConnection(environment, result);
        
        // 数据库连通性检查
        validateDatabaseConnection(environment, result);
    }
    
    /**
     * 验证Redis连接
     */
    private void validateRedisConnection(Environment environment, ValidationResult result) {
        String redisHost = environment.getProperty("spring.data.redis.host", "localhost");
        String redisPortStr = environment.getProperty("spring.data.redis.port", "6379");
        
        try {
            int redisPort = Integer.parseInt(redisPortStr);
            if (!isHostReachable(redisHost, redisPort, 3000)) {
                result.addWarning(String.format("Redis服务 %s:%d 无法连接，将使用本地缓存", redisHost, redisPort));
            } else {
                logger.debug("Redis连接正常: {}:{}", redisHost, redisPort);
            }
        } catch (NumberFormatException e) {
            result.addError("Redis端口配置无效: " + redisPortStr);
        }
    }
    
    /**
     * 验证数据库连接
     */
    private void validateDatabaseConnection(Environment environment, ValidationResult result) {
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        if (jdbcUrl != null) {
            // 从JDBC URL解析主机和端口进行连通性检查
            if (jdbcUrl.contains("mysql://")) {
                String[] parts = jdbcUrl.split("//")[1].split("/")[0].split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 3306;
                
                if (!isHostReachable(host, port, 5000)) {
                    result.addWarning(String.format("数据库服务 %s:%d 无法连接，部分功能可能受限", host, port));
                } else {
                    logger.debug("数据库连接正常: {}:{}", host, port);
                }
            }
        }
    }
    
    /**
     * 验证资源可用性
     */
    private void validateResources(ValidationResult result) {
        logger.info("检查资源文件...");
        
        // 检查关键配置文件
        String[] configFiles = {
            "application.yml",
            "application-dev.yml",
            "logback-spring.xml"
        };
        
        for (String configFile : configFiles) {
            if (getClass().getClassLoader().getResource(configFile) == null) {
                result.addWarning("配置文件不存在: " + configFile);
            }
        }
    }
    
    /**
     * 验证配置有效性
     */
    private void validateConfiguration(Environment environment, ValidationResult result) {
        logger.info("检查配置参数...");
        
        // 检查应用名称
        String appName = environment.getProperty("spring.application.name");
        if (appName == null || appName.trim().isEmpty()) {
            result.addError("应用名称未配置");
        }
        
        // 检查JVM内存配置
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024;
        if (maxMemoryMB < 512) {
            result.addWarning(String.format("JVM最大内存较小: %dMB，建议至少512MB", maxMemoryMB));
        }
    }
    
    /**
     * 检查端口是否可用
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(false);
            serverSocket.bind(new InetSocketAddress(port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 检查主机是否可达
     */
    private boolean isHostReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public boolean isValid() {
            return !hasErrors();
        }
    }
}