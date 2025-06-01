# 游戏服务器管理后台控制台 (Admin Console)

## 项目简介

游戏服务器框架的统一后台管理系统，提供Web可视化的管理界面，实现对整个游戏服务器的监控、配置、运维、数据分析等全方位管理功能。

## 技术架构

### 核心技术栈

- **后端框架**: Spring Boot 3.2+ + Spring Security 6+
- **前端框架**: Vue 3 + TypeScript + Element Plus
- **数据库**: MySQL 8.0+
- **缓存**: Redis + Caffeine
- **认证**: JWT Token
- **监控**: Actuator + Micrometer + Prometheus
- **任务调度**: Quartz
- **API文档**: SpringDoc OpenAPI 3
- **构建工具**: Maven 3.8+
- **Java版本**: Java 17+

### 模块结构

```
admin-console/
├── src/main/java/com/lx/gameserver/admin/
│   ├── core/                   # 核心模块
│   │   ├── AdminApplication.java      # 主应用类
│   │   ├── AdminContext.java          # 上下文管理
│   │   ├── AdminModule.java           # 模块接口
│   │   └── PluginManager.java         # 插件管理器
│   ├── auth/                   # 权限认证
│   │   ├── AuthenticationService.java # 认证服务
│   │   └── AuthorizationService.java  # 授权服务
│   ├── monitor/                # 监控模块
│   ├── config/                 # 配置管理
│   ├── ops/                    # 运维管理
│   ├── game/                   # 游戏管理
│   ├── analytics/              # 数据分析
│   ├── alert/                  # 告警系统
│   ├── audit/                  # 审计日志
│   ├── system/                 # 系统管理
│   ├── api/                    # API网关
│   └── extension/              # 扩展插件
├── src/main/resources/
│   ├── application-admin.yml   # 配置文件
│   └── static/                 # 静态资源
└── src/test/java/              # 测试代码
```

## 核心功能

### 1. 管理平台核心 (core)

- **AdminApplication**: Spring Boot主应用类，模块扫描加载，插件机制初始化
- **AdminContext**: 管理平台上下文，服务注册中心集成，权限和租户管理
- **AdminModule**: 管理模块接口，模块生命周期管理，菜单和API注册
- **PluginManager**: 插件管理器，热插拔支持，依赖管理，版本控制

### 2. 权限管理系统 (auth)

- **AuthenticationService**: 身份认证服务，多种登录方式，JWT管理，会话管理
- **AuthorizationService**: 权限授权服务，RBAC模型，动态权限控制
- **UserManagement**: 用户管理，用户CRUD，角色分配，部门管理
- **RolePermissionManagement**: 角色权限管理，权限分配，权限继承

### 3. 服务监控模块 (monitor)

- **ServiceMonitor**: 服务监控，健康检查，状态展示，依赖拓扑
- **MetricsCollector**: 指标采集器，JVM指标，业务指标，时序数据
- **RealTimeMonitor**: 实时监控，WebSocket推送，仪表盘展示
- **LogAggregator**: 日志聚合，日志收集，搜索分析，ELK集成

### 4. 配置管理中心 (config)

- **ConfigurationManager**: 配置管理器，版本控制，热更新，回滚
- **DynamicConfig**: 动态配置，游戏参数，功能开关，灰度配置
- **ConfigTemplate**: 配置模板，模板管理，环境差异化

### 5. 运维管理模块 (ops)

- **ServerManagement**: 服务器管理，服务启停，部署，版本管理
- **DatabaseManagement**: 数据库管理，备份恢复，SQL执行，性能分析
- **CacheManagement**: 缓存管理，缓存查看，清理，预热，策略调整
- **TaskScheduler**: 任务调度，定时任务，执行历史，分布式调度

### 6. 游戏管理功能 (game)

- **PlayerManagement**: 玩家管理，查询，封禁解封，数据修改
- **GameDataManagement**: 游戏数据管理，道具管理，货币管理
- **ActivityManagement**: 活动管理，创建编辑，上下架，监控分析
- **AnnouncementSystem**: 公告系统，发布，定时，跑马灯，多语言

### 7. 数据分析平台 (analytics)

- **DataAnalytics**: 数据分析引擎，实时离线分析，多维分析
- **BusinessMetrics**: 业务指标，DAU/MAU统计，留存付费分析
- **ReportGenerator**: 报表生成器，日周月报，自定义报表
- **DataVisualization**: 数据可视化，大屏展示，实时图表

### 8. 告警管理系统 (alert)

- **AlertManager**: 告警管理器，规则配置，级别定义，路由抑制
- **AlertChannel**: 告警渠道，邮件短信，钉钉微信，电话Webhook
- **IncidentManagement**: 事件管理，创建升级，处理追踪

### 9. 审计日志系统 (audit)

- **AuditLogger**: 审计日志记录，操作访问日志，敏感操作记录
- **AuditQuery**: 审计查询，多条件查询，操作回放，审计报告
- **ComplianceCheck**: 合规检查，安全审计，自动化检查

### 10. 系统管理功能 (system)

- **SystemSettings**: 系统设置，全局参数，安全设置，维护模式
- **BackupRestore**: 备份恢复，自动手动备份，增量备份
- **SecurityCenter**: 安全中心，安全扫描，漏洞检测，访问控制

### 11. API管理网关 (api)

- **AdminAPIGateway**: 管理API网关，路由，认证授权，限流控制
- **OpenAPI**: 开放API，第三方接入，OAuth2认证，调用统计
- **WebSocketGateway**: WebSocket网关，实时推送，订阅管理

### 12. 扩展开发支持 (extension)

- **AdminPlugin**: 管理插件接口，生命周期，菜单API扩展
- **CustomWidget**: 自定义组件，组件注册，配置绑定
- **SDKGenerator**: SDK生成器，多语言支持，文档生成

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 编译运行

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run

# 打包应用
mvn clean package
```

### 配置说明

主要配置文件：`src/main/resources/application-admin.yml`

```yaml
# 服务器配置
server:
  port: 8090
  servlet:
    context-path: /admin

# 数据源配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/game_admin
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}

# 管理后台配置
admin:
  console:
    security:
      jwt:
        secret: ${JWT_SECRET}
        expiration: 7200
    plugins:
      auto-load: true
      directory: "plugins"
```

### 访问地址

- 管理后台: http://localhost:8090/admin
- API文档: http://localhost:8090/admin/swagger-ui.html
- 健康检查: http://localhost:8090/admin/actuator/health

## 开发指南

### 模块开发

实现 `AdminModule` 接口创建新的管理模块：

```java
public class CustomAdminModule extends AbstractAdminModule {
    @Override
    public String getModuleName() {
        return "custom-module";
    }
    
    @Override
    protected void doInitialize() throws Exception {
        // 初始化逻辑
    }
    
    @Override
    protected void doStart() throws Exception {
        // 启动逻辑
    }
}
```

### 插件开发

创建插件继承 `AdminModule` 接口，并在 `META-INF/MANIFEST.MF` 中配置：

```
Plugin-Name: example-plugin
Plugin-Version: 1.0.0
Plugin-Main-Class: com.example.ExamplePlugin
Plugin-Description: 示例插件
Plugin-Dependencies: auth,monitor
```

### API开发

使用标准的Spring Boot Controller开发API：

```java
@RestController
@RequestMapping("/admin/api/example")
public class ExampleController {
    
    @GetMapping("/list")
    public ResponseEntity<?> list() {
        // API逻辑
    }
}
```

## 部署指南

### Docker部署

```dockerfile
FROM openjdk:17-jre
COPY target/admin-console-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 配置文件

生产环境建议使用外部配置文件：

```bash
java -jar admin-console.jar --spring.config.location=classpath:/application.yml,/opt/config/admin.yml
```

## 监控运维

### 健康检查

- 应用健康检查: `/admin/actuator/health`
- 指标监控: `/admin/actuator/metrics`
- Prometheus指标: `/admin/actuator/prometheus`

### 日志管理

默认日志路径: `logs/admin-console.log`

支持日志级别动态调整：
```bash
curl -X POST "http://localhost:8090/admin/actuator/loggers/com.lx.gameserver.admin" \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel": "DEBUG"}'
```

## 安全说明

### 认证授权

- 支持JWT Token认证
- 基于RBAC的权限模型
- 支持单点登录(SSO)
- API访问控制

### 安全特性

- CSRF防护
- XSS防护  
- SQL注入防护
- 访问频率限制
- IP黑白名单

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交代码
4. 创建Pull Request

## 许可证

本项目采用 Apache License 2.0 许可证。