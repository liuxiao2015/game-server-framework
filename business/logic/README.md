# 游戏服务器逻辑模块 (Game Server Logic Module)

## 模块概述

逻辑模块是游戏服务器的核心业务逻辑服务，提供游戏核心玩法的基础框架和通用功能实现。该模块作为游戏逻辑的承载层，集成框架层的各种能力（Actor、ECS、RPC等），为具体游戏逻辑开发提供规范的架构和丰富的工具支持。

## 技术架构

### 核心技术栈
- **Java 17**: 使用现代Java特性和API
- **Spring Boot 3.2+**: 微服务框架和依赖注入
- **Actor模型**: 高并发消息处理
- **ECS架构**: 实体组件系统
- **MyBatis Plus**: 数据访问层
- **Redis**: 分布式缓存
- **Caffeine**: 本地缓存

### 设计模式
- **Actor模式**: 并发消息处理和状态管理
- **模板方法模式**: 生命周期管理和扩展点
- **观察者模式**: 事件发布和订阅机制
- **策略模式**: 不同的算法和策略实现
- **工厂模式**: 对象创建和管理

## 模块结构

```
business/logic/
├── src/main/java/com/lx/gameserver/business/logic/
│   ├── core/                    # 核心基础设施
│   │   ├── LogicServer.java            # 逻辑服务器主类
│   │   ├── LogicContext.java           # 逻辑服务上下文
│   │   ├── GameWorld.java              # 游戏世界管理
│   │   └── LogicModule.java            # 逻辑模块接口
│   ├── config/                  # 配置管理
│   │   └── LogicConfig.java            # 逻辑服务配置
│   ├── player/                  # 玩家系统
│   │   ├── Player.java                 # 玩家实体类
│   │   ├── PlayerManager.java          # 玩家管理器
│   │   ├── PlayerActor.java            # 玩家Actor实现
│   │   └── PlayerService.java          # 玩家服务接口
│   ├── scene/                   # 场景系统
│   │   ├── Scene.java                  # 场景基类
│   │   ├── SceneManager.java           # 场景管理器
│   │   └── SceneActor.java             # 场景Actor实现
│   ├── util/                    # 工具类
│   │   ├── GameUtils.java              # 游戏工具类
│   │   ├── ValidatorUtils.java         # 验证工具类
│   │   └── SchedulerUtils.java         # 调度工具类
│   ├── extension/               # 扩展接口
│   │   ├── LogicPlugin.java            # 逻辑插件接口
│   │   ├── PluginContext.java          # 插件上下文
│   │   ├── GameEventListener.java      # 游戏事件监听器
│   │   └── DataPersistence.java        # 数据持久化接口
│   ├── battle/                  # 战斗系统框架（待实现）
│   ├── inventory/               # 背包系统框架（待实现）
│   ├── quest/                   # 任务系统框架（待实现）
│   ├── example/                 # 框架集成示例（待实现）
│   └── demo/                    # 功能演示示例（待实现）
└── src/main/resources/
    └── application-logic.yml           # 配置模板
```

## 核心特性

### 1. 服务器基础设施
- **LogicServer**: 逻辑服务器主类，提供完整的生命周期管理
- **LogicContext**: 全局上下文，统一访问各种服务和组件
- **GameWorld**: 游戏世界管理，协调所有游戏对象
- **LogicModule**: 模块化设计，支持插件式开发

### 2. 玩家系统
- **Player**: 完整的玩家实体，支持属性管理和状态控制
- **PlayerManager**: 玩家生命周期管理，支持并发安全操作
- **PlayerActor**: Actor模式的玩家逻辑处理
- **PlayerService**: 数据访问和业务逻辑接口

### 3. 场景系统
- **Scene**: 抽象场景基类，支持AOI和实体管理
- **SceneManager**: 场景调度和负载均衡
- **SceneActor**: 场景级别的消息处理

### 4. 工具类库
- **GameUtils**: 游戏开发常用工具方法
- **ValidatorUtils**: 数据验证和安全检查
- **SchedulerUtils**: 任务调度和定时器管理

### 5. 扩展机制
- **LogicPlugin**: 插件化开发支持
- **GameEventListener**: 事件驱动架构
- **DataPersistence**: 统一的数据访问抽象

## 配置说明

### 服务器配置
```yaml
game:
  logic:
    server:
      id: logic-1
      name: "逻辑服务器1"
      max-players: 5000
      tick-rate: 20
```

### Actor配置
```yaml
game:
  logic:
    actor:
      player-actor-dispatcher: "game-dispatcher"
      scene-actor-dispatcher: "scene-dispatcher"
      max-actors: 100000
```

### ECS配置
```yaml
game:
  logic:
    ecs:
      world-update-interval: 50ms
      entity-pool-size: 10000
      component-pool-size: 50000
```

### 场景配置
```yaml
game:
  logic:
    scene:
      max-scenes: 100
      scene-capacity: 500
      aoi-range: 100
```

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.lx.gameserver</groupId>
    <artifactId>business-logic</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 启动逻辑服务器

```java
@SpringBootApplication
public class LogicServerApplication {
    public static void main(String[] args) {
        LogicServer.start(args);
    }
}
```

### 3. 实现自定义逻辑模块

```java
@Component
public class CustomLogicModule implements LogicModule {
    @Override
    public String getModuleName() {
        return "custom-logic";
    }
    
    @Override
    public void initialize() throws Exception {
        // 模块初始化逻辑
    }
    
    @Override
    public void start() throws Exception {
        // 模块启动逻辑
    }
    
    // 其他方法实现...
}
```

## API 使用示例

### 玩家管理
```java
@Autowired
private PlayerManager playerManager;

// 玩家上线
Result<Player> result = playerManager.playerOnline(playerId, sessionId);

// 玩家下线
playerManager.playerOffline(playerId, "主动退出");

// 获取在线玩家
Player player = playerManager.getOnlinePlayer(playerId);
```

### 场景管理
```java
@Autowired
private SceneManager sceneManager;

// 创建场景
Result<Scene> result = sceneManager.createScene(SceneType.TOWN, "新手村");

// 玩家进入场景
sceneManager.playerEnterScene(sceneId, player, position);

// 场景广播
scene.broadcast("欢迎来到新手村!");
```

### 工具类使用
```java
// 随机数生成
int randomValue = GameUtils.randomInt(1, 100);

// 数据验证
ValidationResult result = ValidatorUtils.builder()
    .notEmpty(username, "用户名")
    .length(username, 3, 32, "用户名")
    .build();

// 任务调度
SchedulerUtils.schedule(() -> {
    // 定时任务逻辑
}, Duration.ofMinutes(5));
```

## 扩展开发

### 实现插件
```java
public class CustomPlugin implements LogicPlugin {
    @Override
    public PluginInfo getPluginInfo() {
        return new PluginInfoImpl("custom-plugin", "1.0.0", "自定义插件");
    }
    
    @Override
    public void load(PluginContext context) throws Exception {
        // 插件加载逻辑
    }
    
    // 其他方法实现...
}
```

### 事件监听
```java
@Component
public class CustomEventListener implements GameEventListener {
    @Override
    public EventResult handleEvent(GameEvent event) {
        if (event instanceof PlayerLoginEvent) {
            PlayerLoginEvent loginEvent = (PlayerLoginEvent) event;
            // 处理玩家登录事件
        }
        return EventResult.CONTINUE;
    }
}
```

## 性能特性

- **高并发**: 基于Actor模型，支持大量并发操作
- **低延迟**: 优化的消息处理和缓存机制
- **可扩展**: 模块化架构，支持水平扩展
- **高可用**: 完善的错误处理和恢复机制

## 监控和运维

### 健康检查
- 自动健康检查机制
- 组件状态监控
- 性能指标收集

### 日志管理
- 结构化日志输出
- 不同级别的日志控制
- 异常跟踪和诊断

### 配置管理
- 热配置重载
- 环境变量支持
- 配置验证机制

## 开发状态

### 已完成功能
- [x] 核心基础设施（LogicServer、LogicContext、GameWorld、LogicModule）
- [x] 玩家系统（Player、PlayerManager、PlayerActor、PlayerService）
- [x] 场景系统（Scene、SceneManager、SceneActor）
- [x] 工具类库（GameUtils、ValidatorUtils、SchedulerUtils）
- [x] 扩展接口（LogicPlugin、GameEventListener、DataPersistence）
- [x] 配置管理和模板

### 待实现功能
- [ ] 战斗系统框架（BattleContext、BattleUnit、BattleManager）
- [ ] 背包系统框架（Inventory、Item、InventoryService）
- [ ] 任务系统框架（Quest、QuestManager、QuestTracker）
- [ ] 框架集成示例（ActorExample、ECSExample、CacheExample、EventExample）
- [ ] 功能演示示例（LoginDemo、ChatDemo、MatchDemo、ShopDemo）
- [ ] 单元测试和集成测试
- [ ] 详细文档和教程

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证，详情请参见 [LICENSE](../../LICENSE) 文件。

## 联系方式

- 项目维护者：liuxiao2015
- 邮箱：liuxiao2015@example.com
- 项目地址：https://github.com/liuxiao2015/game-server-framework