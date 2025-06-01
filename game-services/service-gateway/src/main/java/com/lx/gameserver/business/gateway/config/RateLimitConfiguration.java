/*
 * 文件名: RateLimitConfiguration.java
 * 用途: 限流配置
 * 实现内容:
 *   - Spring Cloud Gateway限流配置
 *   - 基于Redis的分布式限流
 *   - 多维度限流（IP、用户、API）
 *   - 限流算法配置（令牌桶、漏桶）
 *   - 限流规则动态调整
 *   - 限流告警配置
 * 技术选型:
 *   - Spring Cloud Gateway Redis RateLimiter
 *   - Redis + Lua脚本
 *   - 令牌桶算法
 * 依赖关系:
 *   - 依赖Redis集群
 *   - 与RateLimiterService协作
 *   - 集成监控告警
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流配置类
 * <p>
 * 提供多维度、多算法的限流配置，支持基于Redis的分布式限流，
 * 动态调整限流规则，集成监控告警功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RateLimitConfiguration {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * 限流配置属性
     */
    @Data
    @ConfigurationProperties(prefix = "game.gateway.rate-limit")
    public static class RateLimitProperties {
        
        /**
         * 是否启用限流
         */
        private boolean enabled = true;
        
        /**
         * 默认限流配置
         */
        private DefaultConfig defaultConfig = new DefaultConfig();
        
        /**
         * API级别限流配置
         */
        private Map<String, ApiLimitConfig> apiLimits = new HashMap<>();
        
        /**
         * 用户级别限流配置
         */
        private UserLimitConfig userLimit = new UserLimitConfig();
        
        /**
         * IP级别限流配置
         */
        private IpLimitConfig ipLimit = new IpLimitConfig();
        
        /**
         * 告警配置
         */
        private AlertConfig alert = new AlertConfig();
    }

    /**
     * 默认限流配置
     */
    @Data
    public static class DefaultConfig {
        
        /**
         * 每秒请求数
         */
        private int requestsPerSecond = 100;
        
        /**
         * 突发容量
         */
        private int burstCapacity = 200;
        
        /**
         * 补充速率
         */
        private int replenishRate = 100;
        
        /**
         * 限流算法：TOKEN_BUCKET, SLIDING_WINDOW
         */
        private String algorithm = "TOKEN_BUCKET";
    }

    /**
     * API级别限流配置
     */
    @Data
    public static class ApiLimitConfig {
        
        /**
         * API路径模式
         */
        private String pathPattern;
        
        /**
         * 每秒请求数
         */
        private int requestsPerSecond;
        
        /**
         * 突发容量
         */
        private int burstCapacity;
        
        /**
         * 补充速率
         */
        private int replenishRate;
        
        /**
         * 是否启用
         */
        private boolean enabled = true;
    }

    /**
     * 用户级别限流配置
     */
    @Data
    public static class UserLimitConfig {
        
        /**
         * 普通用户每秒请求数
         */
        private int normalUserRps = 50;
        
        /**
         * VIP用户每秒请求数
         */
        private int vipUserRps = 200;
        
        /**
         * 管理员用户每秒请求数
         */
        private int adminUserRps = 1000;
        
        /**
         * 用户等级映射
         */
        private Map<String, Integer> userLevelLimits = new HashMap<>();
    }

    /**
     * IP级别限流配置
     */
    @Data
    public static class IpLimitConfig {
        
        /**
         * 每个IP每秒请求数
         */
        private int requestsPerSecond = 10;
        
        /**
         * 突发容量
         */
        private int burstCapacity = 20;
        
        /**
         * IP白名单（不限流）
         */
        private List<String> whiteList = new ArrayList<>();
    }

    /**
     * 告警配置
     */
    @Data
    public static class AlertConfig {
        
        /**
         * 是否启用告警
         */
        private boolean enabled = true;
        
        /**
         * 限流阈值告警（触发限流次数超过此值时告警）
         */
        private int limitThreshold = 100;
        
        /**
         * 告警时间窗口（秒）
         */
        private int alertWindow = 300;
        
        /**
         * 告警间隔（秒）
         */
        private int alertInterval = 600;
    }

    /**
     * 限流配置属性Bean
     *
     * @return 限流配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "game.gateway.rate-limit")
    public RateLimitProperties rateLimitProperties() {
        return new RateLimitProperties();
    }

    /**
     * Redis限流器配置
     * <p>
     * 配置基于Redis的分布式限流器，使用令牌桶算法。
     * </p>
     *
     * @return Redis限流器
     */
    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter() {
        RateLimitProperties props = rateLimitProperties();
        DefaultConfig defaultConfig = props.getDefaultConfig();
        
        return new RedisRateLimiter(
            defaultConfig.getReplenishRate(),    // 补充速率
            defaultConfig.getBurstCapacity(),    // 突发容量
            1                                    // 请求的令牌数
        );
    }

    /**
     * API限流器配置
     * <p>
     * 为不同API配置不同的限流策略。
     * </p>
     *
     * @return API限流器映射
     */
    @Bean
    public Map<String, RedisRateLimiter> apiRateLimiters() {
        RateLimitProperties props = rateLimitProperties();
        Map<String, RedisRateLimiter> limiters = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, ApiLimitConfig> entry : props.getApiLimits().entrySet()) {
            ApiLimitConfig config = entry.getValue();
            if (config.isEnabled()) {
                RedisRateLimiter limiter = new RedisRateLimiter(
                    config.getReplenishRate(),
                    config.getBurstCapacity(),
                    1
                );
                limiters.put(entry.getKey(), limiter);
            }
        }
        
        return limiters;
    }

    /**
     * IP键解析器
     * <p>
     * 基于客户端IP进行限流的键解析器。
     * </p>
     *
     * @return IP键解析器
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return new KeyResolver() {
            @Override
            public Mono<String> resolve(ServerWebExchange exchange) {
                String clientIp = getClientIp(exchange);
                return Mono.just("ip:" + clientIp);
            }
            
            private String getClientIp(ServerWebExchange exchange) {
                String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                
                String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                
                return exchange.getRequest().getRemoteAddress() != null ? 
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
            }
        };
    }

    /**
     * 用户键解析器
     * <p>
     * 基于用户ID进行限流的键解析器。
     * </p>
     *
     * @return 用户键解析器
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            if (userId == null || userId.isEmpty()) {
                // 如果没有用户ID，回退到IP限流
                String clientIp = exchange.getRequest().getRemoteAddress() != null ? 
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
                return Mono.just("anonymous:" + clientIp);
            }
            return Mono.just("user:" + userId);
        };
    }

    /**
     * API键解析器
     * <p>
     * 基于API路径进行限流的键解析器。
     * </p>
     *
     * @return API键解析器
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();
            return Mono.just("api:" + method + ":" + path);
        };
    }

    /**
     * 组合键解析器
     * <p>
     * 组合多个维度进行限流的键解析器。
     * </p>
     *
     * @return 组合键解析器
     */
    @Bean
    public KeyResolver compositeKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            String path = exchange.getRequest().getPath().value();
            String clientIp = exchange.getRequest().getRemoteAddress() != null ? 
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
            
            StringBuilder keyBuilder = new StringBuilder();
            keyBuilder.append("composite:");
            
            if (userId != null && !userId.isEmpty()) {
                keyBuilder.append("user:").append(userId).append(":");
            }
            
            keyBuilder.append("api:").append(path).append(":");
            keyBuilder.append("ip:").append(clientIp);
            
            return Mono.just(keyBuilder.toString());
        };
    }

    /**
     * 自定义滑动窗口限流器
     * <p>
     * 基于滑动窗口算法的限流器实现。
     * </p>
     */
    public static class SlidingWindowRateLimiter {
        
        private final ReactiveRedisTemplate<String, String> redisTemplate;
        private final RedisScript<List> slidingWindowScript;
        private final Duration windowSize;
        private final int maxRequests;
        
        public SlidingWindowRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate,
                                       Duration windowSize, int maxRequests) {
            this.redisTemplate = redisTemplate;
            this.windowSize = windowSize;
            this.maxRequests = maxRequests;
            this.slidingWindowScript = createSlidingWindowScript();
        }
        
        /**
         * 检查是否允许请求
         *
         * @param key 限流键
         * @return 是否允许
         */
        public Mono<Boolean> isAllowed(String key) {
            long now = System.currentTimeMillis();
            long windowStart = now - windowSize.toMillis();
            
            return redisTemplate.execute(slidingWindowScript, 
                    Arrays.asList(key), 
                    Arrays.asList(String.valueOf(now), 
                            String.valueOf(windowStart), 
                            String.valueOf(maxRequests),
                            String.valueOf(windowSize.getSeconds())))
                .cast(List.class)
                .map(results -> {
                    Long count = (Long) results.get(0);
                    return count <= maxRequests;
                })
                .single();
        }
        
        private RedisScript<List> createSlidingWindowScript() {
            String script = """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window_start = tonumber(ARGV[2])
                local max_requests = tonumber(ARGV[3])
                local ttl = tonumber(ARGV[4])
                
                -- 清理过期记录
                redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
                
                -- 获取当前窗口内的请求数
                local current_count = redis.call('ZCARD', key)
                
                if current_count < max_requests then
                    -- 允许请求，记录时间戳
                    redis.call('ZADD', key, now, now)
                    redis.call('EXPIRE', key, ttl)
                    return {current_count + 1, 1}
                else
                    -- 拒绝请求
                    return {current_count, 0}
                end
                """;
            
            return RedisScript.of(script, List.class);
        }
    }

    /**
     * 滑动窗口限流器Bean
     *
     * @return 滑动窗口限流器
     */
    @Bean
    public SlidingWindowRateLimiter slidingWindowRateLimiter() {
        RateLimitProperties props = rateLimitProperties();
        return new SlidingWindowRateLimiter(
            redisTemplate,
            Duration.ofSeconds(1), // 1秒窗口
            props.getDefaultConfig().getRequestsPerSecond()
        );
    }

    /**
     * 限流统计服务
     * <p>
     * 提供限流统计和监控功能。
     * </p>
     */
    public static class RateLimitStatsService {
        
        private final ReactiveRedisTemplate<String, String> redisTemplate;
        private final Map<String, Long> limitCounters = new ConcurrentHashMap<>();
        
        public RateLimitStatsService(ReactiveRedisTemplate<String, String> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }
        
        /**
         * 记录限流事件
         *
         * @param key 限流键
         * @param limited 是否被限流
         */
        public void recordLimitEvent(String key, boolean limited) {
            if (limited) {
                String statsKey = "rate_limit_stats:" + key;
                long count = limitCounters.getOrDefault(statsKey, 0L) + 1;
                limitCounters.put(statsKey, count);
                
                // 异步更新Redis统计
                redisTemplate.opsForValue()
                    .increment(statsKey, 1)
                    .doOnNext(result -> log.debug("限流统计更新: {} = {}", statsKey, result))
                    .subscribe();
            }
        }
        
        /**
         * 获取限流统计
         *
         * @param key 限流键
         * @return 限流次数
         */
        public Mono<Long> getLimitStats(String key) {
            String statsKey = "rate_limit_stats:" + key;
            return redisTemplate.opsForValue().get(statsKey)
                .map(Long::valueOf)
                .defaultIfEmpty(0L);
        }
    }

    /**
     * 限流统计服务Bean
     *
     * @return 限流统计服务
     */
    @Bean
    public RateLimitStatsService rateLimitStatsService() {
        return new RateLimitStatsService(redisTemplate);
    }
}