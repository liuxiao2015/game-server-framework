# 游戏服务器统一网关 (Game Server Gateway)

## 概述

游戏服务器统一网关是基于Spring Cloud Gateway构建的企业级API网关，作为所有客户端请求的唯一入口，提供生产级的网关功能。支持高并发（10万QPS）、动态路由、协议转换、限流熔断、安全防护、监控告警等企业级特性。

## 技术架构

### 核心技术栈

- **Spring Boot 3.2+** - 应用框架
- **Spring Cloud Gateway** - API网关核心
- **Spring WebFlux** - 响应式编程框架
- **Netty** - 高性能网络通信
- **Redis** - 分布式缓存和限流存储
- **Nacos** - 服务发现和配置中心
- **Resilience4j** - 限流熔断组件
- **JWT** - 身份认证
- **Micrometer** - 指标监控

### 模块结构

```
business/gateway/
├── src/main/java/com/lx/gameserver/business/gateway/
│   ├── config/          # 配置模块
│   │   ├── GatewayConfiguration.java      # Gateway核心配置
│   │   ├── RouteConfiguration.java        # 路由规则配置
│   │   ├── SecurityConfiguration.java     # 安全配置
│   │   └── RateLimitConfiguration.java    # 限流配置
│   ├── protocol/        # 协议转换模块
│   │   ├── ProtocolConverter.java         # 协议转换接口
│   │   ├── GameProtocolHandler.java       # 游戏协议处理器
│   │   └── GameWebSocketHandler.java      # WebSocket处理器
│   ├── route/           # 路由管理模块
│   │   └── DynamicRouteService.java       # 动态路由服务
│   ├── filter/          # 过滤器模块
│   │   ├── AuthenticationFilter.java     # 认证过滤器
│   │   └── RequestLoggingFilter.java     # 日志过滤器
│   ├── auth/            # 认证鉴权模块
│   │   └── TokenValidator.java           # Token验证服务
│   └── monitor/         # 监控模块
│       └── GatewayMetrics.java           # 指标收集器
└── src/main/resources/
    ├── application.yml                    # 基础配置
    └── application-gateway-template.yml  # 生产配置模板
```

## 核心功能

### 1. 动态路由管理

- **路由CRUD操作**: 支持运行时动态添加、修改、删除路由规则
- **版本控制**: 路由配置版本管理，支持回滚
- **灰度发布**: 支持基于百分比的灰度路由
- **负载均衡**: 多种负载均衡算法（轮询、随机、权重等）

```java
// 添加路由示例
RouteRule gameRoute = routeRuleBuilder.buildGameServiceRoute("game-service", "/api/game");
dynamicRouteService.addRoute(gameRoute);

// 灰度发布示例
dynamicRouteService.grayReleaseRoute("game-service", 20); // 20%流量
```

### 2. 协议转换

- **多协议支持**: HTTP、WebSocket、TCP协议互转
- **消息格式**: 支持JSON、Protobuf、自定义二进制协议
- **协议升级**: 支持协议版本管理和平滑升级

```java
// 协议转换示例
@Component
public class GameProtocolHandler implements ProtocolConverter<byte[], Object> {
    // 支持JSON、Protobuf、Binary协议的转换
}
```

### 3. 分布式限流

- **多维度限流**: 支持IP、用户、API等多个维度
- **算法支持**: 令牌桶、滑动窗口算法
- **分布式实现**: 基于Redis+Lua脚本的高性能限流
- **动态调整**: 支持运行时动态调整限流规则

```yaml
# 限流配置示例
game:
  gateway:
    rate-limit:
      enabled: true
      default-qps: 100
      api-limits:
        "/api/game/login":
          requests-per-second: 10
          burst-capacity: 20
```

### 4. 安全防护

- **JWT认证**: 支持JWT Token的验证和刷新
- **权限控制**: 基于角色的访问控制(RBAC)
- **IP访问控制**: 白名单/黑名单机制
- **防重放攻击**: 基于时间戳和随机数的防重放
- **CORS支持**: 跨域资源共享配置

```yaml
# 安全配置示例
game:
  gateway:
    security:
      jwt:
        secret: ${JWT_SECRET}
        expiration: 7200
      white-list:
        - /api/auth/login
        - /api/public/**
```

### 5. 监控告警

- **性能指标**: QPS、响应时间、错误率等核心指标
- **实时监控**: 支持Prometheus指标导出
- **告警机制**: 基于阈值的自动告警
- **链路追踪**: 请求链路跟踪和性能分析

```java
// 指标收集示例
@Component
public class GatewayMetrics {
    public void recordRequest(String method, String path, int statusCode, long responseTime);
    public double getCurrentQPS();
    public double getErrorRate();
}
```

### 6. WebSocket支持

- **连接管理**: WebSocket连接的生命周期管理
- **心跳保活**: 自动心跳检测和连接保活
- **消息路由**: WebSocket消息到HTTP服务的路由转发
- **会话管理**: 分布式会话状态管理

```java
// WebSocket处理示例
@Component
public class GameWebSocketHandler implements WebSocketHandler {
    // 支持WebSocket连接管理、心跳保活、消息路由
}
```

## 性能特性

### 高并发支持

- **目标QPS**: 支持10万+并发请求
- **响应式架构**: 基于WebFlux的非阻塞IO
- **连接池**: 优化的HTTP客户端连接池配置
- **内存优化**: 高效的内存使用和垃圾回收优化

### 性能配置

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000
        response-timeout: 10000
        pool:
          max-connections: 1000
          max-idle-time: 30s
```

## 部署配置

### 环境要求

- Java 21+
- Redis 6.0+
- Nacos 2.0+
- 内存: 建议4GB+
- CPU: 建议4核+

### 配置参数

主要的环境变量配置:

```bash
# Nacos配置
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=public

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT配置
JWT_SECRET=your-jwt-secret-key

# 告警配置
ALERT_WEBHOOK_URL=your-alert-webhook-url
```

### 启动方式

```bash
# 开发环境
mvn spring-boot:run

# 生产环境
java -jar business-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

## 使用示例

### 1. 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: game-server
          uri: lb://game-server
          predicates:
            - Path=/api/game/**
          filters:
            - StripPrefix=2
            - name: RequestRateLimiter
              args:
                rate-limiter: "#{@redisRateLimiter}"
                key-resolver: "#{@ipKeyResolver}"
```

### 2. 动态路由管理

```java
// 通过REST API管理路由
POST /admin/routes
{
  "id": "new-service",
  "uri": "lb://new-service",
  "paths": ["/api/new/**"],
  "enabled": true
}
```

### 3. 监控指标查看

```bash
# 查看指标
curl http://localhost:8080/actuator/metrics/gateway.requests

# 查看健康状态
curl http://localhost:8080/actuator/health
```

## 监控面板

网关提供丰富的监控指标：

- **请求指标**: 总请求数、成功率、错误率
- **性能指标**: 平均响应时间、P99响应时间
- **限流指标**: 限流次数、限流率
- **熔断指标**: 熔断次数、熔断状态
- **系统指标**: JVM内存、GC、线程数

## 扩展开发

### 自定义过滤器

```java
@Component
public class CustomFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 自定义逻辑
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -1;
    }
}
```

### 自定义协议转换器

```java
@Component
public class CustomProtocolConverter implements ProtocolConverter<CustomInput, CustomOutput> {
    @Override
    public Mono<CustomOutput> convert(CustomInput source, ConversionContext context) {
        // 自定义协议转换逻辑
        return Mono.just(convertedOutput);
    }
}
```

## 最佳实践

1. **合理配置连接池**: 根据业务量调整HTTP客户端连接池大小
2. **监控关键指标**: 重点监控QPS、响应时间、错误率
3. **设置合理的限流阈值**: 根据下游服务能力设置限流规则
4. **定期清理缓存**: 设置合理的缓存过期时间
5. **使用链路追踪**: 启用分布式追踪便于问题排查

## 故障排查

常见问题及解决方案：

1. **路由不生效**: 检查路由规则配置和服务注册状态
2. **限流过于严格**: 调整限流配置或检查Redis连接
3. **认证失败**: 检查JWT配置和Token有效性
4. **性能问题**: 查看监控指标，调整连接池和JVM参数
5. **内存泄漏**: 检查WebSocket连接是否正常释放

## 版本说明

当前版本: 1.0.0-SNAPSHOT

主要特性:
- ✅ 基础网关功能
- ✅ 动态路由管理
- ✅ 协议转换支持
- ✅ 分布式限流
- ✅ 安全认证
- ✅ 监控告警
- ✅ WebSocket支持
- 🔄 管理界面 (规划中)
- 🔄 更多协议支持 (规划中)

## 联系方式

如有问题或建议，请联系开发团队或提交Issue。