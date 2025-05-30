/*
 * 文件名: FeignConfig.java
 * 用途: OpenFeign全局配置类
 * 实现内容:
 *   - 配置编码器/解码器（Jackson）
 *   - 配置错误解码器（自定义异常处理）
 *   - 配置日志级别（NONE/BASIC/HEADERS/FULL）
 *   - 配置重试机制
 *   - 配置压缩（请求/响应GZIP压缩）
 *   - 配置超时设置（连接超时、读取超时）
 * 技术选型:
 *   - Spring Cloud OpenFeign
 *   - Jackson JSON处理
 *   - 自定义错误解码器
 *   - GZIP压缩支持
 * 依赖关系:
 *   - 与RpcProperties配置集成
 *   - 被Feign客户端使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lx.gameserver.frame.rpc.exception.FeignErrorDecoder;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OpenFeign全局配置类
 * <p>
 * 提供Feign客户端的全局配置，包括编码器、解码器、错误处理、
 * 重试机制、超时设置等。所有Feign客户端都会使用这些配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnClass(feign.Feign.class)
@ConditionalOnProperty(prefix = "game.rpc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RpcProperties.class)
public class FeignConfig {

    @Autowired
    private RpcProperties rpcProperties;

    /**
     * 配置Jackson编码器
     * <p>
     * 使用Jackson进行JSON序列化，支持Java 8时间API
     * </p>
     *
     * @return Feign编码器
     */
    @Bean
    public Encoder feignEncoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册Java时间模块
        objectMapper.registerModule(new JavaTimeModule());
        
        // 禁用将日期写为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 允许空字符串转为null
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        return new JacksonEncoder(objectMapper);
    }

    /**
     * 配置Jackson解码器
     * <p>
     * 使用Jackson进行JSON反序列化，支持Java 8时间API
     * </p>
     *
     * @return Feign解码器
     */
    @Bean
    public Decoder feignDecoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册Java时间模块
        objectMapper.registerModule(new JavaTimeModule());
        
        // 禁用将日期写为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 允许空字符串转为null
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        return new JacksonDecoder(objectMapper);
    }

    /**
     * 配置错误解码器
     * <p>
     * 自定义错误处理，将HTTP错误响应转换为业务异常
     * </p>
     *
     * @return 错误解码器
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * 配置Feign日志级别
     * <p>
     * 根据配置设置不同的日志级别：
     * - NONE: 不记录日志（默认）
     * - BASIC: 记录请求方法、URL、响应状态码和执行时间
     * - HEADERS: 记录BASIC信息和请求响应头
     * - FULL: 记录请求和响应的所有信息
     * </p>
     *
     * @return Feign日志级别
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        String loggerLevel = rpcProperties.getFeign().getClient().getDefaultConfig().getLoggerLevel();
        
        return switch (loggerLevel.toUpperCase()) {
            case "BASIC" -> Logger.Level.BASIC;
            case "HEADERS" -> Logger.Level.HEADERS;
            case "FULL" -> Logger.Level.FULL;
            default -> Logger.Level.NONE;
        };
    }

    /**
     * 配置请求选项
     * <p>
     * 设置连接超时和读取超时
     * </p>
     *
     * @return 请求选项
     */
    @Bean
    public Request.Options requestOptions() {
        Duration connectTimeout = rpcProperties.getFeign().getClient().getDefaultConfig().getConnectTimeout();
        Duration readTimeout = rpcProperties.getFeign().getClient().getDefaultConfig().getReadTimeout();
        
        return new Request.Options(
            connectTimeout.toMillis(), TimeUnit.MILLISECONDS,
            readTimeout.toMillis(), TimeUnit.MILLISECONDS,
            true // followRedirects
        );
    }

    /**
     * 配置重试策略
     * <p>
     * 使用指数退避算法进行重试
     * </p>
     *
     * @return 重试器
     */
    @Bean
    public Retryer feignRetryer() {
        RpcProperties.FeignProperties.RetryProperties retryConfig = rpcProperties.getFeign().getRetry();
        
        if (retryConfig.getMaxAttempts() <= 1) {
            // 不进行重试
            return Retryer.NEVER_RETRY;
        }
        
        long period = retryConfig.getBackoff().getDelay().toMillis();
        long maxPeriod = retryConfig.getBackoff().getMaxDelay().toMillis();
        int maxAttempts = retryConfig.getMaxAttempts();
        
        return new Retryer.Default(period, maxPeriod, maxAttempts);
    }

    /**
     * 创建自定义ObjectMapper Bean
     * <p>
     * 提供统一的JSON处理配置，供其他组件使用
     * </p>
     *
     * @return ObjectMapper实例
     */
    @Bean("rpcObjectMapper")
    public ObjectMapper rpcObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册Java时间模块
        objectMapper.registerModule(new JavaTimeModule());
        
        // 禁用将日期写为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 允许空字符串转为null
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        // 忽略null值字段
        objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        
        return objectMapper;
    }
}