# 游戏服务器活动模块 (Game Server Activity Module)

## 模块概述

活动模块是游戏服务器的核心业务模块之一，提供灵活、可扩展的游戏活动管理框架。该模块支持各类游戏活动（限时活动、周期活动、节日活动、运营活动等），通过抽象的活动模板和策略模式，使开发者只需实现具体活动子类即可快速上线新活动。

## 核心特性

- **🎯 灵活的活动类型支持**: 支持每日、每周、限时、节日、运营等多种活动类型
- **⚡ 高性能并发处理**: 支持百个活动并发运行，实时进度更新
- **🔧 可扩展架构**: 基于模板方法模式，易于扩展新的活动类型
- **📊 完整的生命周期管理**: 从创建到销毁的完整活动生命周期控制
- **🎁 灵活的奖励系统**: 支持多种奖励类型和复杂的发放规则
- **📈 实时进度追踪**: 高效的进度计算和里程碑检查
- **🚀 热更新支持**: 支持配置热更新和动态活动调整
- **📱 完整的管理界面**: 提供管理员和客户端API

## 技术架构

### 核心技术栈
- **Java 17**: 使用现代Java特性和API
- **Spring Boot 3.2+**: 微服务框架
- **MyBatis Plus**: 数据访问层
- **Redis**: 分布式缓存
- **Caffeine**: 本地缓存
- **Spring Scheduler**: 任务调度
- **Jackson**: JSON处理

### 设计模式
- **模板方法模式**: Activity基类和具体活动实现
- **策略模式**: 不同的进度计算和奖励计算策略
- **工厂模式**: ActivityFactory负责活动实例创建
- **观察者模式**: 活动事件发布和订阅
- **单例模式**: 各种管理器组件

## 模块结构

```
business/activity/
├── src/main/java/com/lx/gameserver/business/activity/
│   ├── core/                    # 核心抽象层
│   │   ├── Activity.java               # 活动基类（抽象类）
│   │   ├── ActivityType.java           # 活动类型枚举
│   │   ├── ActivityContext.java        # 活动上下文
│   │   └── ActivityLifecycle.java      # 活动生命周期接口
│   ├── manager/                 # 管理层
│   │   ├── ActivityManager.java        # 活动管理器
│   │   ├── ActivityScheduler.java      # 活动调度器
│   │   └── ActivityFactory.java        # 活动工厂
│   ├── template/                # 活动模板层
│   │   ├── BaseActivity.java           # 活动基础实现类
│   │   ├── ActivityTemplate.java       # 活动模板接口
│   │   ├── SignInActivity.java         # 签到活动模板
│   │   ├── TaskActivity.java           # 任务活动模板
│   │   ├── ExchangeActivity.java       # 兑换活动模板
│   │   ├── RankActivity.java           # 排行榜活动模板
│   │   ├── CollectActivity.java        # 收集活动模板
│   │   └── LotteryActivity.java        # 抽奖活动模板
│   ├── progress/                # 进度管理层
│   │   ├── ActivityProgress.java       # 活动进度实体
│   │   ├── ProgressTracker.java        # 进度追踪器
│   │   └── ProgressCalculator.java     # 进度计算器接口
│   ├── reward/                  # 奖励系统层
│   │   ├── ActivityReward.java         # 活动奖励实体
│   │   ├── RewardService.java          # 奖励服务
│   │   └── RewardCalculator.java       # 奖励计算器
│   ├── storage/                 # 数据存储层
│   │   ├── ActivityRepository.java     # 活动数据访问层
│   │   └── ActivityCache.java          # 活动缓存服务
│   ├── impl/                    # 具体实现层
│   │   ├── DailySignInActivity.java    # 每日签到活动
│   │   ├── WeeklyTaskActivity.java     # 每周任务活动
│   │   └── LimitedTimeEventActivity.java # 限时活动
│   ├── event/                   # 事件系统层
│   │   ├── ActivityEvent.java          # 活动事件基类
│   │   ├── ActivityEventBus.java       # 活动事件总线
│   │   ├── ActivityStartEvent.java     # 活动开始事件
│   │   ├── ActivityEndEvent.java       # 活动结束事件
│   │   ├── ProgressUpdateEvent.java    # 进度更新事件
│   │   ├── RewardClaimEvent.java       # 奖励领取事件
│   │   └── MilestoneReachEvent.java    # 里程碑达成事件
│   ├── monitor/                 # 监控统计层
│   │   ├── ActivityMetrics.java        # 活动指标统计
│   │   └── ActivityAnalytics.java      # 活动分析服务
│   ├── admin/                   # 管理接口层
│   │   ├── ActivityManagementController.java # 活动管理接口
│   │   └── ActivityMonitorController.java    # 活动监控接口
│   ├── api/                     # 客户端接口层
│   │   ├── ActivityController.java     # 活动客户端接口
│   │   └── ActivityDTO.java            # 活动数据传输对象
│   └── config/                  # 配置支持层
│       └── ActivityConfig.java         # 活动配置类
├── src/main/resources/
│   └── application-activity.yml        # 活动模块配置
└── src/test/java/
    └── com/lx/gameserver/business/activity/
        └── ActivityModuleTest.java     # 活动模块测试
```

## 快速开始

### 1. 依赖配置

在项目的 `pom.xml` 中添加活动模块依赖：

```xml
<dependency>
    <groupId>com.lx.gameserver</groupId>
    <artifactId>business-activity</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加活动模块配置：

```yaml
game:
  activity:
    # 全局配置
    global:
      max-concurrent-activities: 20
      default-timezone: "Asia/Shanghai"
      auto-cleanup-days: 30
    
    # 调度配置
    scheduler:
      core-pool-size: 4
      max-pool-size: 8
      check-interval: 60s
    
    # 缓存配置
    cache:
      enabled: true
      expire-time: 10m
      max-size: 1000
    
    # 存储配置
    storage:
      type: mysql
      partition-by: month
      archive-after-days: 90
    
    # 奖励配置
    reward:
      max-retry-times: 3
      claim-expire-days: 7
      batch-size: 100
    
    # 监控配置
    monitor:
      enabled: true
      report-interval: 5m
      metrics-retention: 7d
```

### 3. 基本使用

#### 创建自定义活动

```java
@Component
public class CustomSignInActivity extends BaseActivity {
    
    public CustomSignInActivity() {
        super();
    }
    
    @Override
    protected void doBaseInitialize(ActivityContext context) throws Exception {
        // 初始化签到活动
        setConfig("signInDays", 7);
        setConfig("resetTime", "00:00:00");
    }
    
    @Override
    protected void doBaseStart(ActivityContext context) throws Exception {
        // 启动签到活动
        log.info("签到活动开始: {}", getActivityName());
    }
    
    @Override
    protected void doBaseUpdate(ActivityContext context, long deltaTime) throws Exception {
        // 更新签到活动状态
        checkDailyReset();
    }
    
    @Override
    protected void doBaseEnd(ActivityContext context, String reason) throws Exception {
        // 结束签到活动
        log.info("签到活动结束: {}, 原因: {}", getActivityName(), reason);
    }
    
    @Override
    protected ProgressCalculationResult doCalculateProgress(ActivityContext context, 
                                                          Long playerId, 
                                                          String actionType, 
                                                          Map<String, Object> actionData) {
        if ("sign_in".equals(actionType)) {
            Map<String, Long> changes = new HashMap<>();
            changes.put("signInDays", 1L);
            return ProgressCalculationResult.success(changes);
        }
        return ProgressCalculationResult.failure("不支持的行为类型");
    }
    
    @Override
    protected RewardCalculationResult doCalculateReward(ActivityContext context, 
                                                       Long playerId, 
                                                       String milestone, 
                                                       Map<String, Object> baseReward) {
        Map<String, Object> rewards = new HashMap<>();
        
        // 根据签到天数计算奖励
        int signInDays = Integer.parseInt(milestone);
        rewards.put("gold", signInDays * 100L);
        rewards.put("exp", signInDays * 50L);
        
        return RewardCalculationResult.success(rewards);
    }
    
    private void checkDailyReset() {
        // 检查是否需要每日重置
        // 实现逻辑...
    }
}
```

#### 注册和管理活动

```java
@Service
public class ActivityService {
    
    @Autowired
    private ActivityManager activityManager;
    
    @Autowired
    private ActivityFactory activityFactory;
    
    @Autowired
    private ProgressTracker progressTracker;
    
    public void createSignInActivity() {
        // 注册活动类型
        activityFactory.registerActivityType(ActivityType.SIGN_IN, CustomSignInActivity.class);
        
        // 创建活动配置
        ActivityFactory.ActivityConfig config = new ActivityFactory.ActivityConfig();
        config.activityId = 1L;
        config.activityName = "每日签到";
        config.activityType = ActivityType.SIGN_IN;
        config.startTime = System.currentTimeMillis();
        config.endTime = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L; // 30天后
        
        // 创建活动实例
        Activity activity = activityFactory.createActivity(config);
        
        // 注册到管理器
        activityManager.registerActivity(activity);
        
        // 启动活动
        activityManager.startActivity(1L);
    }
    
    public void playerSignIn(Long playerId) {
        // 更新玩家签到进度
        progressTracker.updateProgress(playerId, 1L, "sign_in", 1L);
    }
}
```

## API 使用说明

### 核心接口

#### 1. 活动管理

```java
// 获取活动列表
List<Activity> activities = activityManager.getAllActivities();

// 按类型获取活动
List<Activity> dailyActivities = activityManager.getActivitiesByType(ActivityType.DAILY);

// 按状态获取活动
List<Activity> activeActivities = activityManager.getActiveActivities();

// 启动/停止活动
activityManager.startActivity(activityId);
activityManager.stopActivity(activityId, "admin_stop");
```

#### 2. 进度管理

```java
// 初始化玩家进度
ActivityProgress progress = progressTracker.initializeProgress(playerId, activityId);

// 更新进度
ProgressTracker.ProgressUpdateResult result = progressTracker.updateProgress(
    playerId, activityId, "score", 100L);

// 查询进度
ActivityProgress current = progressTracker.getProgress(playerId, activityId);

// 批量查询
Map<Long, ActivityProgress> batchProgress = progressTracker.getBatchProgress(
    playerId, Arrays.asList(1L, 2L, 3L));
```

#### 3. 奖励管理

```java
// 创建奖励
ActivityReward reward = new ActivityReward(activityId, 
    ActivityReward.RewardType.CURRENCY, 1001, 1000L);

// 设置领取条件
reward.setClaimCondition("min_level", 10);
reward.setClaimCondition("required_progress", Map.of("score", 100L));

// 验证奖励
ActivityReward.ValidationResult validation = reward.validate();
```

## 扩展指南

### 开发新活动类型

1. **继承BaseActivity**：所有自定义活动都应该继承 `BaseActivity`
2. **实现抽象方法**：实现必要的生命周期方法和模板方法
3. **配置活动参数**：在初始化方法中设置活动特定的配置
4. **注册活动类型**：使用 `ActivityFactory.registerActivityType()` 注册
5. **测试验证流程**：编写单元测试验证活动逻辑

### 自定义组件开发

#### 自定义进度计算器

```java
@Component
public class CustomProgressCalculator implements ProgressCalculator {
    
    @Override
    public List<ActivityType> getSupportedActivityTypes() {
        return Arrays.asList(ActivityType.CUSTOM);
    }
    
    @Override
    public CalculationResult calculateProgress(CalculationContext context) {
        // 实现自定义计算逻辑
        Map<String, Long> changes = new HashMap<>();
        changes.put("custom_progress", calculateCustomProgress(context));
        
        return CalculationResult.success(changes, 100.0);
    }
    
    // 其他方法实现...
}
```

#### 自定义奖励类型

```java
public enum CustomRewardType {
    SPECIAL_ITEM("special_item", "特殊道具"),
    BUFF("buff", "增益效果"),
    UNLOCK("unlock", "解锁内容");
    
    // 实现细节...
}
```

#### 自定义事件处理

```java
@EventListener
public class CustomActivityEventHandler {
    
    @EventListener
    public void handleActivityStart(ActivityStartEvent event) {
        // 处理活动开始事件
        log.info("活动开始: {}", event.getActivityId());
    }
    
    @EventListener
    public void handleProgressUpdate(ProgressUpdateEvent event) {
        // 处理进度更新事件
        checkMilestones(event.getProgress());
    }
}
```

## 性能优化

### 缓存策略

1. **多级缓存**：本地缓存 + Redis分布式缓存
2. **缓存预热**：系统启动时预加载热点数据
3. **异步更新**：使用异步机制更新缓存
4. **缓存穿透保护**：使用布隆过滤器防止缓存穿透

### 并发优化

1. **读写分离**：使用读写锁分离读写操作
2. **无锁编程**：使用 CAS 操作减少锁竞争
3. **批量操作**：支持批量进度更新和奖励发放
4. **异步处理**：使用事件驱动的异步处理

### 数据库优化

1. **分表分库**：按时间或玩家ID分表
2. **索引优化**：合理设计数据库索引
3. **归档策略**：定期归档历史数据
4. **连接池优化**：合理配置数据库连接池

## 监控和运维

### 监控指标

- 活动参与人数和完成率
- 进度更新频率和响应时间
- 奖励发放成功率
- 缓存命中率和性能指标
- 异常错误率和报警

### 运维工具

- 活动管理后台
- 实时监控大屏
- 数据分析报表
- 配置热更新工具
- 问题诊断工具

## 常见问题

### Q: 如何处理活动时间跨时区问题？
A: 使用统一的时区配置，并在客户端进行时区转换显示。

### Q: 活动进度更新频率很高，如何优化性能？
A: 使用批量更新、异步处理和缓存策略来优化性能。

### Q: 如何保证奖励发放的一致性？
A: 使用事务处理和幂等性设计，确保奖励不会重复发放。

### Q: 活动配置如何支持热更新？
A: 使用配置中心和事件通知机制实现配置的动态更新。

## 更新日志

### v1.0.0 (2025-01-13)
- 初始版本发布
- 完整的活动管理框架
- 支持多种活动类型
- 完善的进度追踪和奖励系统
- 基础的监控和管理功能

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证，详情请参见 [LICENSE](LICENSE) 文件。

## 联系方式

- 项目维护者：liuxiao2015
- 邮箱：liuxiao2015@example.com
- 项目地址：https://github.com/liuxiao2015/game-server-framework