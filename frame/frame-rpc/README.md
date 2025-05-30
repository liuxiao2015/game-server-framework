# Frame-RPC 模块

## 概述

frame-rpc 模块是游戏服务器框架的轻量级RPC通信模块，基于 Spring Cloud OpenFeign 实现服务间的HTTP调用，结合 Spring Cloud LoadBalancer 实现客户端负载均衡。该模块为游戏微服务提供简单、高效、易维护的远程调用能力。

## 技术选型

- **Spring Cloud OpenFeign**: 声明式HTTP客户端
- **Spring Cloud LoadBalancer**: 客户端负载均衡
- **Resilience4j**: 熔断器和故障容错
- **Nacos**: 服务注册与发现（优先选择）
- **Micrometer**: 监控指标收集
- **Jackson**: JSON序列化/反序列化

## 核心功能

### 1. 服务发现与注册
- 支持 Nacos、Eureka、Consul 多种注册中心
- 自动服务注册和心跳保活
- 服务实例元数据管理
- 优雅上下线机制

### 2. 负载均衡
- 轮询（Round Robin）
- 随机（Random）
- 响应时间权重（Weighted Response Time）
- 最少连接数（Least Connections）
- 一致性哈希（Consistent Hash）

### 3. 熔断降级
- 基于失败率的熔断
- 基于慢调用率的熔断
- 自动降级处理
- 半开状态恢复

### 4. 调用链追踪
- 自动生成 TraceId 和 SpanId
- 跨服务调用链追踪
- 性能监控和日志记录

## 模块结构

```
frame-rpc/
├── src/main/java/com/lx/gameserver/frame/rpc/
│   ├── api/                    # 通用服务接口
│   │   ├── BaseRpcService.java     # RPC服务基础接口
│   │   ├── RpcRequest.java         # 统一请求模型
│   │   ├── RpcResponse.java        # 统一响应模型
│   │   └── PlayerServiceApi.java   # 玩家服务接口示例
│   ├── config/                 # 核心配置
│   │   ├── RpcProperties.java      # RPC配置属性
│   │   ├── FeignConfig.java        # Feign全局配置
│   │   ├── LoadBalancerConfig.java # 负载均衡配置
│   │   ├── CircuitBreakerConfig.java # 熔断器配置
│   │   └── RpcAutoConfiguration.java # 自动配置
│   ├── discovery/              # 服务发现
│   │   └── GameServiceInstance.java # 游戏服务实例扩展
│   ├── exception/              # 异常处理
│   │   ├── RpcException.java       # RPC异常基类
│   │   └── FeignErrorDecoder.java  # Feign错误解码器
│   └── util/                   # 工具类
│       └── RpcContextHolder.java   # RPC上下文持有者
├── src/main/resources/
│   ├── META-INF/
│   │   └── spring.factories        # Spring Boot自动配置
│   └── application-rpc-example.yml # 配置示例
└── src/test/java/                  # 测试代码
    └── com/lx/gameserver/frame/rpc/config/
        └── RpcPropertiesTest.java
```

## 快速开始

### 1. 引入依赖

在项目的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.lx.gameserver</groupId>
    <artifactId>frame-rpc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加RPC配置：

```yaml
game:
  rpc:
    enabled: true
    discovery:
      type: nacos
      server-addr: localhost:8848
      namespace: dev
    feign:
      compression:
        request:
          enabled: true
        response:
          enabled: true
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000
            logger-level: BASIC
    loadbalancer:
      strategy: weighted-response-time
    circuit-breaker:
      failure-rate-threshold: 50
      slow-call-rate-threshold: 50
```

### 3. 定义服务接口

```java
@FeignClient(name = "player-service", path = "/api/player")
public interface PlayerServiceApi extends BaseRpcService {
    
    @GetMapping("/{playerId}")
    RpcResponse<PlayerInfo> getPlayer(@PathVariable("playerId") String playerId);
    
    @PostMapping
    RpcResponse<PlayerInfo> createPlayer(@RequestBody RpcRequest<CreatePlayerRequest> request);
}
```

### 4. 使用服务

```java
@RestController
public class GameController {
    
    @Autowired
    private PlayerServiceApi playerService;
    
    @GetMapping("/player/{playerId}")
    public RpcResponse<PlayerInfo> getPlayer(@PathVariable String playerId) {
        return playerService.getPlayer(playerId);
    }
}
```

## 配置说明

### 服务发现配置

```yaml
game:
  rpc:
    discovery:
      type: nacos              # 服务发现类型
      server-addr: localhost:8848  # 服务器地址
      namespace: dev           # 命名空间
      username: nacos          # 用户名（可选）
      password: nacos          # 密码（可选）
      group: DEFAULT_GROUP     # 分组
```

### Feign客户端配置

```yaml
game:
  rpc:
    feign:
      compression:
        request:
          enabled: true        # 启用请求压缩
          min-request-size: 2048  # 最小压缩大小
        response:
          enabled: true        # 启用响应压缩
      client:
        config:
          default:
            connect-timeout: 5000    # 连接超时
            read-timeout: 10000      # 读取超时
            logger-level: BASIC      # 日志级别
```

### 负载均衡配置

```yaml
game:
  rpc:
    loadbalancer:
      strategy: weighted-response-time  # 负载均衡策略
      health-check:
        interval: 5s           # 健康检查间隔
        timeout: 3s            # 健康检查超时
```

### 熔断器配置

```yaml
game:
  rpc:
    circuit-breaker:
      failure-rate-threshold: 50          # 失败率阈值（%）
      slow-call-rate-threshold: 50        # 慢调用率阈值（%）
      slow-call-duration-threshold: 3s    # 慢调用时间阈值
      sliding-window-size: 100            # 滑动窗口大小
      minimum-number-of-calls: 20         # 最小调用次数
      wait-duration-in-open-state: 30s    # 熔断器打开状态持续时间
```

## 最佳实践

### 1. 接口设计

- 继承 `BaseRpcService` 接口
- 使用统一的 `RpcRequest`/`RpcResponse` 模型
- 合理设计接口粒度，避免过于细粒度的调用

### 2. 异常处理

- 使用 `RpcException` 统一异常体系
- 实现 `FallbackFactory` 提供降级逻辑
- 区分业务异常和系统异常

### 3. 性能优化

- 合理配置连接池和超时时间
- 启用请求/响应压缩
- 使用合适的负载均衡策略
- 开启熔断保护

### 4. 监控告警

- 集成监控指标收集
- 配置熔断状态告警
- 监控服务调用成功率和延迟

## 测试

运行测试：

```bash
mvn test -pl frame/frame-rpc
```

## 构建

编译模块：

```bash
mvn clean compile -pl frame/frame-rpc -am
```

## 版本历史

- **1.0.0-SNAPSHOT**: 初始版本
  - 基础RPC功能实现
  - OpenFeign集成
  - 负载均衡支持
  - 熔断降级机制
  - 配置属性管理

## 贡献

请参考项目根目录的贡献指南。

## 许可证

本项目采用 Apache 2.0 许可证。