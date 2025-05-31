# 游戏服务器场景模块 (Game Server Scene Module)

## 模块概述

场景模块是游戏服务器的核心组件之一，负责管理游戏世界中的各类场景（主城、副本、战场、野外等）。该模块基于Actor模型实现高性能的场景处理，支持AOI（Area of Interest）视野管理、实体管理、场景切换、动态负载均衡等核心功能。

## 技术特性

- **高性能**: 基于Actor模型的并发处理，支持单场景500+玩家
- **可扩展**: 灵活的场景类型注册机制，支持自定义场景实现
- **高可用**: 支持动态负载均衡、故障转移和优雅降级
- **实时性**: 高效的AOI系统，位置更新延迟控制在100ms内

## 模块结构

```
business/scene/
├── src/main/java/com/lx/gameserver/business/scene/
│   ├── core/                    # 核心抽象层
│   │   ├── Scene.java              # 场景基类
│   │   ├── SceneType.java          # 场景类型枚举
│   │   ├── SceneEntity.java        # 场景实体基类
│   │   └── SceneConfig.java        # 场景配置类
│   ├── manager/                 # 管理层
│   │   ├── SceneManager.java       # 场景管理器
│   │   ├── SceneFactory.java       # 场景工厂
│   │   └── SceneScheduler.java     # 场景调度器
│   ├── aoi/                     # AOI系统
│   │   ├── AOIManager.java         # AOI管理器
│   │   ├── AOIEntity.java          # AOI实体接口
│   │   └── AOIGrid.java            # AOI网格实现
│   ├── impl/                    # 场景实现
│   │   ├── MainCityScene.java      # 主城场景
│   │   ├── DungeonScene.java       # 副本场景
│   │   └── BattlefieldScene.java   # 战场场景
│   ├── util/                    # 工具类
│   │   └── SceneUtils.java         # 场景工具类
│   └── config/                  # 配置管理
│       └── SceneServiceConfig.java # 服务配置
└── src/main/resources/
    └── application-scene.yml       # 配置文件模板
```

## 快速开始

### 1. 基础配置

在application.yml中添加场景配置：

```yaml
game:
  scene:
    manager:
      max-scenes: 1000
      load-balance-strategy: "least-loaded"
    aoi:
      grid-size: 100.0
      view-distance: 200.0
    monitor:
      enable: true
```

### 2. 创建场景

```java
@Autowired
private SceneManager sceneManager;

// 创建主城场景
Scene mainCity = sceneManager.createScene(
    SceneType.MAIN_CITY, 
    "新手村", 
    SceneConfig.createDefault(SceneType.MAIN_CITY)
);
```

### 3. 实体管理

```java
// 添加实体到场景
Scene.Position spawnPos = new Scene.Position(100, 0, 100);
scene.addEntity(entityId, spawnPos);

// 更新实体位置
Scene.Position newPos = new Scene.Position(120, 0, 110);
scene.updateEntityPosition(entityId, newPos);
```

### 4. 自定义场景

```java
public class CustomScene extends Scene {
    
    public CustomScene(Long sceneId, String sceneName, 
                      SceneType sceneType, SceneConfig config) {
        super(sceneId, sceneName, sceneType, config);
    }
    
    @Override
    protected void onCreate() throws Exception {
        // 场景初始化逻辑
    }
    
    @Override
    protected void onTick(long deltaTime) {
        // 场景更新逻辑
    }
    
    // 实现其他抽象方法...
}

// 注册自定义场景
sceneFactory.registerSceneType(SceneType.CUSTOM, CustomScene.class);
```

## 核心组件

### 场景管理器 (SceneManager)

- 场景生命周期管理
- 负载均衡和调度
- 动态扩缩容
- 性能监控

### AOI系统

- 九宫格空间索引
- 高效的视野计算
- 批量事件处理
- 内存优化

### 场景调度器 (SceneScheduler)

- 多种分配策略
- 故障转移
- 性能监控
- 自动优化

## 性能指标

- 单场景支持: 500+ 实体
- 位置更新延迟: < 100ms
- AOI更新频率: 5Hz
- 内存使用: < 1GB (1000个场景)
- CPU使用: < 50% (正常负载)

## 配置说明

### 场景管理配置

- `max-scenes`: 最大场景数量
- `scene-pool-size`: 场景池大小
- `load-balance-strategy`: 负载均衡策略

### AOI配置

- `grid-size`: 网格大小
- `view-distance`: 视野距离
- `update-interval`: 更新间隔

### 性能配置

- `entity-pool-size`: 实体池大小
- `message-batch-size`: 消息批处理大小
- `use-native-memory`: 是否使用本地内存

## 监控指标

- 场景数量统计
- 实体分布统计
- AOI性能指标
- 内存使用情况
- 消息处理速率

## 最佳实践

### 1. 场景设计

- 合理设置场景容量
- 优化场景更新逻辑
- 避免在tick中进行重计算

### 2. AOI优化

- 合理设置网格大小
- 减少不必要的位置更新
- 使用批量操作

### 3. 性能调优

- 监控关键指标
- 合理配置线程池
- 启用性能优化选项

## 扩展开发

### 添加新场景类型

1. 定义场景类型枚举
2. 实现场景具体类
3. 注册到场景工厂
4. 配置场景参数

### 自定义AOI算法

1. 实现AOI接口
2. 注册到AOI管理器
3. 配置算法参数

## 故障排查

### 常见问题

1. **场景创建失败**
   - 检查配置参数
   - 验证场景类型注册
   - 查看错误日志

2. **AOI性能问题**
   - 调整网格大小
   - 检查实体分布
   - 优化更新频率

3. **内存使用过高**
   - 启用对象池化
   - 减少场景数量
   - 优化实体管理

## 版本历史

- v1.0.0: 初始版本，基础功能实现
- 支持主城、副本、战场场景
- 完整的AOI系统
- 性能监控和调优

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证。

## 联系方式

- 项目维护者：liuxiao2015
- 技术支持：请提交 Issue