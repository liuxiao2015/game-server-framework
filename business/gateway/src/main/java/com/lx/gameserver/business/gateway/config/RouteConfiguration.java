/*
 * 文件名: RouteConfiguration.java
 * 用途: 路由规则配置
 * 实现内容:
 *   - 路由规则配置管理
 *   - 动态路由支持（基于Nacos）
 *   - 路由优先级设置
 *   - 路由断言配置
 *   - 路由过滤器链配置
 *   - 路由元数据管理
 * 技术选型:
 *   - Spring Cloud Gateway
 *   - Nacos配置中心
 *   - 动态路由刷新
 * 依赖关系:
 *   - 与DynamicRouteService协作
 *   - 被GatewayConfiguration使用
 *   - 集成Nacos配置监听
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.net.URI;
import java.util.*;

/**
 * 路由规则配置类
 * <p>
 * 提供路由规则的配置和管理功能，支持动态路由、路由优先级、
 * 路由断言和过滤器链配置。支持基于Nacos的动态路由刷新。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Configuration
public class RouteConfiguration {

    /**
     * 路由配置属性类
     */
    @Data
    @ConfigurationProperties(prefix = "game.gateway.route")
    public static class RouteProperties {
        
        /**
         * 是否启用动态路由
         */
        private boolean dynamicEnabled = true;
        
        /**
         * 路由刷新间隔（秒）
         */
        private int refreshInterval = 30;
        
        /**
         * 默认路由超时时间（毫秒）
         */
        private long defaultTimeout = 5000;
        
        /**
         * 默认重试次数
         */
        private int defaultRetries = 3;
        
        /**
         * 路由规则配置列表
         */
        private List<RouteRule> rules = new ArrayList<>();
    }

    /**
     * 路由规则配置类
     */
    @Data
    public static class RouteRule {
        
        /**
         * 路由ID
         */
        private String id;
        
        /**
         * 路由URI
         */
        private String uri;
        
        /**
         * 路由优先级（数值越小优先级越高）
         */
        private int order = 0;
        
        /**
         * 路径断言
         */
        private List<String> paths = new ArrayList<>();
        
        /**
         * 方法断言
         */
        private List<String> methods = new ArrayList<>();
        
        /**
         * 请求头断言
         */
        private Map<String, String> headers = new HashMap<>();
        
        /**
         * 查询参数断言
         */
        private Map<String, String> params = new HashMap<>();
        
        /**
         * 过滤器配置
         */
        private List<FilterConfig> filters = new ArrayList<>();
        
        /**
         * 元数据
         */
        private Map<String, Object> metadata = new HashMap<>();
        
        /**
         * 是否启用
         */
        private boolean enabled = true;
        
        /**
         * 超时时间（毫秒）
         */
        private Long timeout;
        
        /**
         * 重试次数
         */
        private Integer retries;
    }

    /**
     * 过滤器配置类
     */
    @Data
    public static class FilterConfig {
        
        /**
         * 过滤器名称
         */
        private String name;
        
        /**
         * 过滤器参数
         */
        private Map<String, Object> args = new HashMap<>();
        
        /**
         * 过滤器顺序
         */
        private int order = 0;
    }

    /**
     * 路由配置属性Bean
     *
     * @return 路由配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "game.gateway.route")
    public RouteProperties routeProperties() {
        return new RouteProperties();
    }

    /**
     * 默认路由规则构建器
     * <p>
     * 提供常用的路由规则模板，简化路由配置。
     * </p>
     *
     * @return 路由规则构建器
     */
    @Bean
    public RouteRuleBuilder routeRuleBuilder() {
        return new RouteRuleBuilder();
    }

    /**
     * 路由规则构建器
     */
    public static class RouteRuleBuilder {

        /**
         * 创建游戏服务路由规则
         *
         * @param serviceId 服务ID
         * @param pathPrefix 路径前缀
         * @return 路由规则
         */
        public RouteRule buildGameServiceRoute(String serviceId, String pathPrefix) {
            RouteRule rule = new RouteRule();
            rule.setId(serviceId + "-route");
            rule.setUri("lb://" + serviceId);
            rule.setPaths(Arrays.asList(pathPrefix + "/**"));
            rule.setOrder(100);
            
            // 添加默认过滤器
            FilterConfig stripPrefixFilter = new FilterConfig();
            stripPrefixFilter.setName("StripPrefix");
            stripPrefixFilter.setArgs(Map.of("parts", 2));
            rule.getFilters().add(stripPrefixFilter);
            
            FilterConfig requestHeaderFilter = new FilterConfig();
            requestHeaderFilter.setName("AddRequestHeader");
            requestHeaderFilter.setArgs(Map.of(
                "name", "X-Service-Source",
                "value", "gateway"
            ));
            rule.getFilters().add(requestHeaderFilter);
            
            return rule;
        }

        /**
         * 创建认证服务路由规则
         *
         * @return 路由规则
         */
        public RouteRule buildAuthServiceRoute() {
            RouteRule rule = new RouteRule();
            rule.setId("auth-service-route");
            rule.setUri("lb://auth-service");
            rule.setPaths(Arrays.asList("/api/auth/**"));
            rule.setOrder(50); // 高优先级
            
            // 添加认证相关过滤器
            FilterConfig authFilter = new FilterConfig();
            authFilter.setName("AuthenticationFilter");
            authFilter.setArgs(Map.of("skipPaths", Arrays.asList("/api/auth/login", "/api/auth/register")));
            rule.getFilters().add(authFilter);
            
            return rule;
        }

        /**
         * 创建WebSocket路由规则
         *
         * @return 路由规则
         */
        public RouteRule buildWebSocketRoute() {
            RouteRule rule = new RouteRule();
            rule.setId("websocket-route");
            rule.setUri("lb://game-service");
            rule.setPaths(Arrays.asList("/ws/**"));
            rule.setOrder(10); // 最高优先级
            
            // WebSocket特殊配置
            rule.getMetadata().put("websocket", true);
            rule.getMetadata().put("upgradeRequired", true);
            
            return rule;
        }

        /**
         * 创建管理接口路由规则
         *
         * @return 路由规则
         */
        public RouteRule buildAdminRoute() {
            RouteRule rule = new RouteRule();
            rule.setId("admin-route");
            rule.setUri("lb://admin-service");
            rule.setPaths(Arrays.asList("/admin/**"));
            rule.setOrder(200); // 低优先级
            
            // 添加管理员权限过滤器
            FilterConfig adminFilter = new FilterConfig();
            adminFilter.setName("AuthorizationFilter");
            adminFilter.setArgs(Map.of("requiredRole", "ADMIN"));
            rule.getFilters().add(adminFilter);
            
            return rule;
        }
    }

    /**
     * 路由定义转换器
     * <p>
     * 将路由规则配置转换为Gateway的RouteDefinition。
     * </p>
     */
    public static class RouteDefinitionConverter {

        /**
         * 转换路由规则为路由定义
         *
         * @param rule 路由规则
         * @return 路由定义
         */
        public static RouteDefinition convert(RouteRule rule) {
            if (!rule.isEnabled()) {
                return null;
            }

            RouteDefinition definition = new RouteDefinition();
            definition.setId(rule.getId());
            
            try {
                definition.setUri(URI.create(rule.getUri()));
            } catch (Exception e) {
                log.error("路由URI配置错误: {}", rule.getUri(), e);
                return null;
            }
            
            definition.setOrder(rule.getOrder());
            definition.setMetadata(rule.getMetadata());

            // 设置断言
            List<PredicateDefinition> predicates = new ArrayList<>();
            
            // 路径断言
            if (!rule.getPaths().isEmpty()) {
                PredicateDefinition pathPredicate = new PredicateDefinition();
                pathPredicate.setName("Path");
                pathPredicate.setArgs(Map.of("patterns", String.join(",", rule.getPaths())));
                predicates.add(pathPredicate);
            }
            
            // 方法断言
            if (!rule.getMethods().isEmpty()) {
                PredicateDefinition methodPredicate = new PredicateDefinition();
                methodPredicate.setName("Method");
                methodPredicate.setArgs(Map.of("methods", String.join(",", rule.getMethods())));
                predicates.add(methodPredicate);
            }
            
            // 请求头断言
            for (Map.Entry<String, String> header : rule.getHeaders().entrySet()) {
                PredicateDefinition headerPredicate = new PredicateDefinition();
                headerPredicate.setName("Header");
                headerPredicate.setArgs(Map.of(
                    "header", header.getKey(),
                    "regexp", header.getValue()
                ));
                predicates.add(headerPredicate);
            }
            
            // 查询参数断言
            for (Map.Entry<String, String> param : rule.getParams().entrySet()) {
                PredicateDefinition paramPredicate = new PredicateDefinition();
                paramPredicate.setName("Query");
                paramPredicate.setArgs(Map.of(
                    "param", param.getKey(),
                    "regexp", param.getValue()
                ));
                predicates.add(paramPredicate);
            }
            
            definition.setPredicates(predicates);

            // 设置过滤器
            List<FilterDefinition> filters = new ArrayList<>();
            for (FilterConfig filterConfig : rule.getFilters()) {
                FilterDefinition filterDefinition = new FilterDefinition();
                filterDefinition.setName(filterConfig.getName());
                filterDefinition.setArgs(new HashMap<String, String>(filterConfig.getArgs().size()));
                for (Map.Entry<String, Object> entry : filterConfig.getArgs().entrySet()) {
                    filterDefinition.getArgs().put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                filters.add(filterDefinition);
            }
            definition.setFilters(filters);

            return definition;
        }
    }

    /**
     * 路由验证器
     * <p>
     * 验证路由规则配置的有效性。
     * </p>
     */
    public static class RouteValidator {

        /**
         * 验证路由规则
         *
         * @param rule 路由规则
         * @return 验证结果
         */
        public static ValidationResult validate(RouteRule rule) {
            ValidationResult result = new ValidationResult();
            
            if (rule.getId() == null || rule.getId().trim().isEmpty()) {
                result.addError("路由ID不能为空");
            }
            
            if (rule.getUri() == null || rule.getUri().trim().isEmpty()) {
                result.addError("路由URI不能为空");
            } else {
                try {
                    URI.create(rule.getUri());
                } catch (Exception e) {
                    result.addError("路由URI格式错误: " + rule.getUri());
                }
            }
            
            if (rule.getPaths().isEmpty() && rule.getMethods().isEmpty() && 
                rule.getHeaders().isEmpty() && rule.getParams().isEmpty()) {
                result.addWarning("路由没有配置任何断言条件");
            }
            
            return result;
        }
    }

    /**
     * 验证结果类
     */
    @Data
    public static class ValidationResult {
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
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
    }
}