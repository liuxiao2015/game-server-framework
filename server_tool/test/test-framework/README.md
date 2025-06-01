# 游戏服务器测试框架 (Game Server Test Framework)

## 概述

游戏服务器测试框架是一个综合性的测试平台，为游戏服务器系统提供全方位的测试能力，包括单元测试、集成测试、性能测试、压力测试等。该框架设计为可扩展的架构，支持自动化测试、持续集成、测试报告生成等功能。

## 特性

### 🎯 核心功能
- **完整的测试生命周期管理** - 从测试初始化到结果汇总的全流程支持
- **多类型测试支持** - 单元测试、集成测试、性能测试、压力测试
- **游戏特定测试工具** - 针对游戏服务器的专用测试组件
- **插件化架构** - 支持自定义扩展和第三方集成
- **丰富的配置选项** - 灵活的测试环境和参数配置

### 🚀 技术特性
- **Java 21支持** - 使用最新的Java特性和API
- **Spring Boot集成** - 完整的Spring生态系统支持
- **Docker容器化** - 支持容器化的测试环境
- **并发执行** - 支持并行测试执行提升效率
- **实时监控** - 测试过程的实时监控和指标收集

## 模块结构

```
test-framework/
├── src/main/java/com/lx/gameserver/testframework/
│   ├── core/                    # 核心测试框架
│   │   ├── TestFramework.java          # 测试框架主类
│   │   ├── TestContext.java            # 测试上下文管理
│   │   ├── TestCase.java               # 测试用例基类
│   │   └── TestSuite.java              # 测试套件管理
│   ├── unit/                    # 单元测试框架
│   │   ├── UnitTestRunner.java         # 单元测试运行器
│   │   ├── MockFactory.java            # Mock对象工厂
│   │   └── TestDataBuilder.java        # 测试数据构建器
│   ├── integration/             # 集成测试框架
│   │   ├── IntegrationTestRunner.java  # 集成测试运行器
│   │   ├── ServiceContainer.java       # 服务容器管理
│   │   ├── TestScenario.java           # 测试场景
│   │   └── TestEnvironment.java        # 测试环境
│   ├── config/                  # 配置管理
│   │   └── TestConfig.java             # 测试配置类
│   ├── extension/               # 扩展接口
│   │   ├── TestPlugin.java             # 测试插件接口
│   │   └── TestListener.java           # 测试监听器接口
│   └── TestFrameworkApplication.java   # 应用主类
└── src/test/java/               # 测试代码
    ├── TestFrameworkSmokeTest.java     # 框架冒烟测试
    └── integration/
        └── ExampleIntegrationTest.java # 集成测试示例
```

## 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.lx.gameserver</groupId>
    <artifactId>test-framework</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### 2. 基础配置

创建 `application-test.yml` 配置文件：

```yaml
test:
  framework:
    base:
      parallel-execution: true
      max-parallel-tests: 10
      timeout: PT5M
      retry-count: 3
    
    environments:
      local:
        name: "本地测试环境"
        default-environment: true
        services:
          - name: redis
            image: redis:7-alpine
            port: 6379
          - name: mysql
            image: mysql:8.0
            port: 3306
            environment:
              MYSQL_ROOT_PASSWORD: test123
              MYSQL_DATABASE: testdb
```

### 3. 编写单元测试

```java
public class GameServiceTest extends TestCase {
    
    @Test
    public void testPlayerLogin() throws Exception {
        // 使用MockFactory创建Mock对象
        UserService mockUserService = getMockFactory().createMock(UserService.class);
        
        // 使用TestDataBuilder生成测试数据
        Player testPlayer = getTestDataBuilder().buildRandom(Player.class);
        
        // 执行测试逻辑
        // ...
        
        // 断言结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
}
```

### 4. 编写集成测试

```java
public class PlayerLoginScenario extends TestScenario {
    
    public PlayerLoginScenario() {
        super("player-login", "玩家登录集成测试");
        addDependency("redis");
        addDependency("mysql");
    }
    
    @Override
    protected void runScenario(TestEnvironment environment, TestContext context) throws Exception {
        // 执行集成测试逻辑
        // 1. 启动游戏服务
        // 2. 模拟玩家登录
        // 3. 验证登录结果
        // 4. 检查数据一致性
    }
}
```

### 5. 运行测试

```java
@SpringBootTest
public class GameServerIntegrationTest {
    
    @Autowired
    private TestFramework testFramework;
    
    @Test
    public void runAllTests() throws Exception {
        // 注册测试套件
        TestSuite suite = new TestSuite("游戏服务器测试");
        suite.addTestCase(new GameServiceTest("玩家登录测试"));
        
        testFramework.registerTestSuite(suite);
        
        // 运行测试
        CompletableFuture<TestSummary> result = testFramework.runAllTests();
        TestSummary summary = result.get();
        
        // 验证结果
        assertTrue(summary.getSuccessRate() > 0.9);
    }
}
```

## 核心组件详解

### TestFramework - 测试框架主类

- **功能**: 测试框架的核心协调器，管理测试套件的生命周期
- **特性**: 支持插件加载、监听器管理、并发执行
- **使用**: 作为测试的入口点，协调所有测试活动

### TestContext - 测试上下文

- **功能**: 管理测试过程中的环境配置、数据状态和资源分配
- **特性**: 线程安全、自动清理、配置管理
- **使用**: 在测试执行过程中传递环境信息和共享数据

### TestCase - 测试用例基类

- **功能**: 所有测试用例的基类，提供标准的测试生命周期
- **特性**: 超时控制、重试机制、断言工具、异常处理
- **使用**: 继承此类实现具体的测试逻辑

### TestSuite - 测试套件

- **功能**: 组织和管理一组相关的测试用例
- **特性**: 依赖管理、并行执行、结果聚合
- **使用**: 将相关测试用例组织成套件进行批量执行

## 高级特性

### 1. Mock对象支持

```java
// 创建Mock对象
GameService mockService = mockFactory.createMock(GameService.class);

// 配置Mock行为
mockFactory.whenThenReturn(mockService.getPlayer("test"), testPlayer);

// 验证Mock调用
mockFactory.verify(mockService).getPlayer("test");
```

### 2. 测试数据生成

```java
// 生成随机对象
Player randomPlayer = testDataBuilder.buildRandom(Player.class);

// 生成边界数据
Player minPlayer = testDataBuilder.buildBoundary(Player.class, BoundaryType.MIN);

// 批量生成数据
List<Player> players = testDataBuilder.buildList(Player.class, 100);
```

### 3. 集成测试环境

```java
// 启动测试服务
ServiceInstance redis = serviceContainer.startService("redis", environment);

// 执行测试场景
testRunner.runScenario("player-battle", testContext);

// 自动清理环境
// 框架会自动停止服务和清理资源
```

### 4. 插件扩展

```java
public class CustomTestPlugin implements TestPlugin {
    
    @Override
    public PluginInfo getPluginInfo() {
        return new PluginInfoImpl("custom-test-plugin", "1.0.0", "自定义测试插件");
    }
    
    @Override
    public void load(TestContext context) throws Exception {
        // 插件加载逻辑
    }
}
```

## 配置参考

### 基础配置

```yaml
test:
  framework:
    base:
      parallel-execution: true        # 是否启用并行执行
      max-parallel-tests: 10         # 最大并行测试数
      timeout: PT5M                  # 默认超时时间
      retry-count: 3                 # 重试次数
      continue-on-failure: true      # 失败后是否继续
```

### 环境配置

```yaml
test:
  framework:
    environments:
      local:
        name: "本地环境"
        default-environment: true
        services:
          - name: redis
            image: redis:7-alpine
            port: 6379
            startup-timeout: PT2M
            health-check-url: "redis://localhost:6379"
```

### 性能测试配置

```yaml
test:
  framework:
    performance:
      warm-up-duration: PT1M         # 预热时间
      test-duration: PT5M            # 测试持续时间
      ramp-up-time: PT30S            # 递增时间
      target-tps: 10000              # 目标TPS
      max-concurrency: 1000          # 最大并发数
```

## 最佳实践

### 1. 测试组织

- 按功能模块组织测试套件
- 使用有意义的测试名称和描述
- 合理设置测试依赖关系
- 避免测试间的相互干扰

### 2. 测试数据管理

- 使用TestDataBuilder生成测试数据
- 避免硬编码测试数据
- 注意测试数据的边界情况
- 及时清理测试数据

### 3. 环境隔离

- 每个测试使用独立的环境
- 避免共享可变状态
- 确保测试的可重复性
- 合理配置资源限制

### 4. 性能优化

- 启用并行执行提升效率
- 合理设置超时时间
- 使用Mock对象减少外部依赖
- 监控测试执行性能

## 故障排除

### 常见问题

1. **测试超时**
   - 检查网络连接
   - 调整超时配置
   - 优化测试逻辑

2. **Mock对象失效**
   - 确保Mock对象配置正确
   - 检查方法签名匹配
   - 验证Mock对象生命周期

3. **环境启动失败**
   - 检查Docker环境
   - 验证服务配置
   - 查看服务启动日志

4. **并发测试冲突**
   - 确保测试隔离性
   - 避免共享资源竞争
   - 合理设置并发数量

## 版本历史

- **v1.0.0** - 初始版本
  - 核心测试框架
  - 单元测试支持
  - 基础集成测试
  - 配置管理系统

## 贡献指南

欢迎提交Issue和Pull Request！

## 许可证

Apache License 2.0