/*
 * 文件名: DynamicRouteService.java
 * 用途: 动态路由服务
 * 实现内容:
 *   - 动态路由规则CRUD操作
 *   - 路由刷新机制
 *   - 路由版本控制
 *   - 路由灰度发布
 *   - 路由回滚支持
 * 技术选型:
 *   - Spring Cloud Gateway RouteDefinitionRepository
 *   - Redis路由配置存储
 *   - 版本控制和回滚机制
 * 依赖关系:
 *   - 与RouteConfiguration协作
 *   - 集成Nacos配置中心
 *   - 支持运行时路由更新
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.route;

import com.lx.gameserver.business.gateway.config.RouteConfiguration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态路由服务
 * <p>
 * 提供动态路由管理功能，支持运行时路由规则的增删改查、
 * 版本控制、灰度发布和快速回滚等企业级路由管理能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteService implements RouteDefinitionRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final RouteConfiguration.RouteDefinitionConverter converter = new RouteConfiguration.RouteDefinitionConverter();
    private final RouteConfiguration.RouteValidator validator = new RouteConfiguration.RouteValidator();
    
    // 缓存路由定义
    private final Map<String, RouteDefinition> routeCache = new ConcurrentHashMap<>();
    
    // Redis键前缀
    private static final String ROUTE_KEY_PREFIX = "gateway:routes:";
    private static final String ROUTE_VERSION_KEY = "gateway:routes:version";
    private static final String ROUTE_HISTORY_KEY_PREFIX = "gateway:routes:history:";

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return loadRoutesFromRedis()
            .doOnNext(route -> routeCache.put(route.getId(), route))
            .doOnComplete(() -> log.debug("从Redis加载路由定义完成，共{}个路由", routeCache.size()));
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(this::saveRoute);
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(this::deleteRoute);
    }

    /**
     * 添加路由规则
     *
     * @param routeRule 路由规则
     * @return 操作结果
     */
    public Mono<RouteOperationResult> addRoute(RouteConfiguration.RouteRule routeRule) {
        return Mono.fromCallable(() -> {
            // 验证路由规则
            RouteConfiguration.ValidationResult validationResult = validator.validate(routeRule);
            if (validationResult.hasErrors()) {
                return RouteOperationResult.failure("路由规则验证失败: " + validationResult.getErrors());
            }
            
            // 转换为路由定义
            RouteDefinition routeDefinition = converter.convert(routeRule);
            if (routeDefinition == null) {
                return RouteOperationResult.failure("路由规则转换失败");
            }
            
            return RouteOperationResult.success("路由规则验证通过");
        })
        .flatMap(result -> {
            if (!result.isSuccess()) {
                return Mono.just(result);
            }
            
            RouteDefinition routeDefinition = converter.convert(routeRule);
            return saveRouteWithHistory(routeDefinition, "ADD")
                .map(saved -> RouteOperationResult.success("路由添加成功: " + routeDefinition.getId()));
        });
    }

    /**
     * 更新路由规则
     *
     * @param routeRule 路由规则
     * @return 操作结果
     */
    public Mono<RouteOperationResult> updateRoute(RouteConfiguration.RouteRule routeRule) {
        return Mono.fromCallable(() -> {
            RouteConfiguration.ValidationResult validationResult = validator.validate(routeRule);
            if (validationResult.hasErrors()) {
                return RouteOperationResult.failure("路由规则验证失败: " + validationResult.getErrors());
            }
            return RouteOperationResult.success("验证通过");
        })
        .flatMap(result -> {
            if (!result.isSuccess()) {
                return Mono.just(result);
            }
            
            RouteDefinition routeDefinition = converter.convert(routeRule);
            return saveRouteWithHistory(routeDefinition, "UPDATE")
                .map(saved -> RouteOperationResult.success("路由更新成功: " + routeDefinition.getId()));
        });
    }

    /**
     * 删除路由规则
     *
     * @param routeId 路由ID
     * @return 操作结果
     */
    public Mono<RouteOperationResult> removeRoute(String routeId) {
        return getRouteById(routeId)
            .flatMap(routeDefinition -> {
                // 保存删除历史
                return saveRouteHistory(routeDefinition, "DELETE")
                    .then(deleteRoute(routeId))
                    .then(Mono.just(RouteOperationResult.success("路由删除成功: " + routeId)));
            })
            .defaultIfEmpty(RouteOperationResult.failure("路由不存在: " + routeId));
    }

    /**
     * 启用路由
     *
     * @param routeId 路由ID
     * @return 操作结果
     */
    public Mono<RouteOperationResult> enableRoute(String routeId) {
        return updateRouteStatus(routeId, true)
            .map(success -> success ? 
                RouteOperationResult.success("路由启用成功: " + routeId) :
                RouteOperationResult.failure("路由启用失败: " + routeId));
    }

    /**
     * 禁用路由
     *
     * @param routeId 路由ID
     * @return 操作结果
     */
    public Mono<RouteOperationResult> disableRoute(String routeId) {
        return updateRouteStatus(routeId, false)
            .map(success -> success ? 
                RouteOperationResult.success("路由禁用成功: " + routeId) :
                RouteOperationResult.failure("路由禁用失败: " + routeId));
    }

    /**
     * 获取路由详情
     *
     * @param routeId 路由ID
     * @return 路由定义
     */
    public Mono<RouteDefinition> getRouteById(String routeId) {
        RouteDefinition cachedRoute = routeCache.get(routeId);
        if (cachedRoute != null) {
            return Mono.just(cachedRoute);
        }
        
        return redisTemplate.opsForValue()
            .get(ROUTE_KEY_PREFIX + routeId)
            .map(this::deserializeRoute)
            .doOnNext(route -> routeCache.put(routeId, route));
    }

    /**
     * 获取所有路由列表
     *
     * @return 路由列表
     */
    public Flux<RouteDefinition> getAllRoutes() {
        return getRouteDefinitions();
    }

    /**
     * 刷新路由
     *
     * @return 操作结果
     */
    public Mono<RouteOperationResult> refreshRoutes() {
        return Mono.fromRunnable(() -> {
            routeCache.clear();
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("路由刷新完成");
        })
        .then(Mono.just(RouteOperationResult.success("路由刷新成功")));
    }

    /**
     * 获取路由历史版本
     *
     * @param routeId 路由ID
     * @return 历史版本列表
     */
    public Flux<RouteHistory> getRouteHistory(String routeId) {
        return redisTemplate.opsForList()
            .range(ROUTE_HISTORY_KEY_PREFIX + routeId, 0, -1)
            .map(this::deserializeRouteHistory);
    }

    /**
     * 路由回滚
     *
     * @param routeId 路由ID
     * @param version 目标版本
     * @return 操作结果
     */
    public Mono<RouteOperationResult> rollbackRoute(String routeId, String version) {
        return getRouteHistory(routeId)
            .filter(history -> version.equals(history.getVersion()))
            .next()
            .flatMap(history -> {
                RouteDefinition routeDefinition = history.getRouteDefinition();
                return saveRouteWithHistory(routeDefinition, "ROLLBACK")
                    .map(saved -> RouteOperationResult.success("路由回滚成功: " + routeId + " -> " + version));
            })
            .defaultIfEmpty(RouteOperationResult.failure("未找到指定版本: " + version));
    }

    /**
     * 灰度发布路由
     *
     * @param routeId 路由ID
     * @param grayPercentage 灰度百分比
     * @return 操作结果
     */
    public Mono<RouteOperationResult> grayReleaseRoute(String routeId, int grayPercentage) {
        if (grayPercentage < 0 || grayPercentage > 100) {
            return Mono.just(RouteOperationResult.failure("灰度百分比必须在0-100之间"));
        }
        
        return getRouteById(routeId)
            .flatMap(routeDefinition -> {
                // 添加灰度发布元数据
                routeDefinition.getMetadata().put("gray.enabled", "true");
                routeDefinition.getMetadata().put("gray.percentage", String.valueOf(grayPercentage));
                
                return saveRouteWithHistory(routeDefinition, "GRAY_RELEASE")
                    .map(saved -> RouteOperationResult.success("灰度发布成功: " + routeId + ", 比例: " + grayPercentage + "%"));
            })
            .defaultIfEmpty(RouteOperationResult.failure("路由不存在: " + routeId));
    }

    /**
     * 保存路由（私有方法）
     *
     * @param routeDefinition 路由定义
     * @return 保存结果
     */
    private Mono<Void> saveRoute(RouteDefinition routeDefinition) {
        String routeJson = serializeRoute(routeDefinition);
        String routeKey = ROUTE_KEY_PREFIX + routeDefinition.getId();
        
        return redisTemplate.opsForValue()
            .set(routeKey, routeJson)
            .doOnSuccess(success -> {
                routeCache.put(routeDefinition.getId(), routeDefinition);
                log.info("路由保存成功: {}", routeDefinition.getId());
                // 发布路由刷新事件
                eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            })
            .then();
    }

    /**
     * 删除路由（私有方法）
     *
     * @param routeId 路由ID
     * @return 删除结果
     */
    private Mono<Void> deleteRoute(String routeId) {
        String routeKey = ROUTE_KEY_PREFIX + routeId;
        
        return redisTemplate.opsForValue()
            .delete(routeKey)
            .doOnSuccess(success -> {
                routeCache.remove(routeId);
                log.info("路由删除成功: {}", routeId);
                // 发布路由刷新事件
                eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            })
            .then();
    }

    /**
     * 保存路由并记录历史
     *
     * @param routeDefinition 路由定义
     * @param operation 操作类型
     * @return 保存结果
     */
    private Mono<Void> saveRouteWithHistory(RouteDefinition routeDefinition, String operation) {
        return saveRouteHistory(routeDefinition, operation)
            .then(saveRoute(routeDefinition));
    }

    /**
     * 保存路由历史
     *
     * @param routeDefinition 路由定义
     * @param operation 操作类型
     * @return 保存结果
     */
    private Mono<Void> saveRouteHistory(RouteDefinition routeDefinition, String operation) {
        RouteHistory history = new RouteHistory();
        history.setRouteId(routeDefinition.getId());
        history.setVersion(generateVersion());
        history.setOperation(operation);
        history.setRouteDefinition(routeDefinition);
        history.setTimestamp(LocalDateTime.now());
        
        String historyKey = ROUTE_HISTORY_KEY_PREFIX + routeDefinition.getId();
        String historyJson = serializeRouteHistory(history);
        
        return redisTemplate.opsForList()
            .leftPush(historyKey, historyJson)
            .then(redisTemplate.opsForList().trim(historyKey, 0, 99)) // 保留最近100个版本
            .then();
    }

    /**
     * 更新路由状态
     *
     * @param routeId 路由ID
     * @param enabled 是否启用
     * @return 更新结果
     */
    private Mono<Boolean> updateRouteStatus(String routeId, boolean enabled) {
        return getRouteById(routeId)
            .flatMap(routeDefinition -> {
                routeDefinition.getMetadata().put("enabled", String.valueOf(enabled));
                return saveRoute(routeDefinition).then(Mono.just(true));
            })
            .defaultIfEmpty(false);
    }

    /**
     * 从Redis加载路由
     *
     * @return 路由定义流
     */
    private Flux<RouteDefinition> loadRoutesFromRedis() {
        return redisTemplate.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(ROUTE_KEY_PREFIX + "*")
                .build())
            .flatMap(key -> redisTemplate.opsForValue().get(key))
            .map(this::deserializeRoute)
            .filter(Objects::nonNull);
    }

    /**
     * 序列化路由定义
     *
     * @param routeDefinition 路由定义
     * @return JSON字符串
     */
    private String serializeRoute(RouteDefinition routeDefinition) {
        // 简化的序列化实现，实际应使用Jackson或其他JSON库
        return routeDefinition.toString();
    }

    /**
     * 反序列化路由定义
     *
     * @param json JSON字符串
     * @return 路由定义
     */
    private RouteDefinition deserializeRoute(String json) {
        // 简化的反序列化实现，实际应使用Jackson或其他JSON库
        try {
            // TODO: 实现实际的反序列化逻辑
            return new RouteDefinition();
        } catch (Exception e) {
            log.error("反序列化路由定义失败", e);
            return null;
        }
    }

    /**
     * 序列化路由历史
     *
     * @param history 路由历史
     * @return JSON字符串
     */
    private String serializeRouteHistory(RouteHistory history) {
        return history.toString();
    }

    /**
     * 反序列化路由历史
     *
     * @param json JSON字符串
     * @return 路由历史
     */
    private RouteHistory deserializeRouteHistory(String json) {
        try {
            // TODO: 实现实际的反序列化逻辑
            return new RouteHistory();
        } catch (Exception e) {
            log.error("反序列化路由历史失败", e);
            return null;
        }
    }

    /**
     * 生成版本号
     *
     * @return 版本号
     */
    private String generateVersion() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * 路由操作结果
     */
    @Data
    public static class RouteOperationResult {
        private boolean success;
        private String message;
        private Object data;

        public static RouteOperationResult success(String message) {
            RouteOperationResult result = new RouteOperationResult();
            result.setSuccess(true);
            result.setMessage(message);
            return result;
        }

        public static RouteOperationResult failure(String message) {
            RouteOperationResult result = new RouteOperationResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * 路由历史记录
     */
    @Data
    public static class RouteHistory {
        private String routeId;
        private String version;
        private String operation;
        private RouteDefinition routeDefinition;
        private LocalDateTime timestamp;
    }
}