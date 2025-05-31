# 游戏服务器框架检查和测试工具

本目录包含用于检查和测试游戏服务器框架的工具集。

## 工具列表

### 1. 框架完整性评估工具
**文件**: `framework_assessment.py`  
**用途**: 自动化评估框架的模块完整性、编译状态、测试覆盖率等

**使用方法**:
```bash
python3 tools/framework_assessment.py /path/to/framework/root
```

**输出**:
- 详细评估报告 (Markdown格式)
- JSON格式数据报告
- 评估日志文件

### 2. 性能测试工具
**位置**: `framework-test/src/main/java/com/lx/gameserver/framework/test/performance/`  
**主要类**: `FrameworkPerformanceTester`  
**功能**:
- Actor系统性能测试
- RPC调用延迟测试  
- 网络性能测试
- 数据库操作性能测试

### 3. 集成测试工具
**位置**: `framework-test/src/main/java/com/lx/gameserver/framework/test/integration/`  
**主要类**: `FrameworkIntegrationTester`  
**功能**:
- 端到端业务流程测试
- 高负载场景测试
- 容错性测试

### 4. 健康监控工具
**位置**: `framework-test/src/main/java/com/lx/gameserver/framework/monitor/`  
**主要类**: `FrameworkHealthMonitor`  
**功能**:
- 实时健康状态监控
- JVM性能监控
- 业务指标监控
- 告警功能

## 快速开始

### 运行框架评估
```bash
# 1. 运行完整性评估
python3 tools/framework_assessment.py .

# 2. 查看评估报告
cat /tmp/framework_assessment_report.md
```

### 运行性能和集成测试
```bash
# 1. 编译测试模块
mvn clean compile -pl framework-test

# 2. 运行测试套件
cd framework-test
mvn spring-boot:run
```

### 启动健康监控
```bash
# 集成到应用中启动
java -jar your-application.jar --enable-health-monitoring
```

## 性能目标

### 核心性能指标
- **Actor消息处理**: >100万msg/s
- **RPC调用延迟**: <1ms
- **网络延迟99分位**: <10ms  
- **数据库操作TPS**: >10万ops/s

### 并发能力指标
- **Actor并发数量**: >10万个
- **同时连接数**: >5万个
- **登录风暴处理**: 1万用户同时登录

## 测试场景

### 集成测试场景
1. 玩家登录流程测试
2. 聊天系统流程测试
3. 支付流程测试
4. 登录风暴测试
5. 场景压力测试
6. 服务故障测试

### 验收标准
- 编译通过率: 100%
- 测试覆盖率: >80%
- 集成测试通过率: 100%
- 性能基准达标率: 100%
- 异常场景覆盖率: >90%

## 工具维护

这些工具会随着框架的发展持续更新。建议：
1. 定期运行评估工具检查框架状态
2. 在CI/CD中集成自动化测试
3. 监控工具应在生产环境中持续运行
4. 根据业务需求调整性能目标

## 问题反馈

如果在使用工具过程中遇到问题，请：
1. 检查工具依赖是否已安装
2. 确认框架模块编译状态
3. 查看工具输出的错误日志
4. 提交Issue到项目仓库

---

*工具集由AI助手创建，旨在提供全面的框架质量保障。*