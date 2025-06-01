# 游戏服务器支付模块 (Game Server Payment Module)

## 模块概述

payment 模块是游戏服务器的生产级支付服务，提供安全、可靠、可扩展的支付解决方案。该模块支持多支付渠道接入（支付宝、微信支付、苹果支付、Google支付等），实现统一的支付抽象层，包含订单管理、支付流程、退款处理、对账系统、风控机制等核心功能。

## 技术架构

### 核心技术栈
- **Java 21**: 使用最新的Java特性和API
- **Spring Boot 3.2+**: 微服务框架
- **MyBatis Plus**: 数据访问层
- **Redis**: 分布式缓存
- **支付渠道SDK**: 支付宝、微信支付等官方SDK
- **OkHttp**: HTTP客户端
- **Jackson**: JSON处理

### 模块结构

```
business/payment/
├── src/main/java/com/lx/gameserver/business/payment/
│   ├── core/              # 核心抽象层
│   │   ├── PaymentChannel.java        # 支付渠道接口
│   │   ├── PaymentOrder.java          # 支付订单实体
│   │   ├── PaymentTransaction.java    # 支付事务实体
│   │   └── PaymentContext.java        # 支付上下文
│   ├── channel/           # 支付渠道实现
│   │   ├── ChannelAdapter.java        # 渠道适配器基类
│   │   ├── AlipayChannel.java         # 支付宝支付
│   │   ├── WechatPayChannel.java      # 微信支付
│   │   ├── ApplePayChannel.java       # 苹果内购
│   │   └── GooglePayChannel.java      # Google Play支付
│   ├── order/             # 订单管理
│   │   ├── OrderService.java          # 订单服务
│   │   ├── OrderNumberGenerator.java  # 订单号生成器
│   │   ├── OrderValidator.java        # 订单验证器
│   │   ├── OrderRepository.java       # 订单数据访问接口
│   │   └── OrderRepositoryImpl.java   # 订单数据访问实现
│   ├── process/           # 支付处理
│   │   ├── PaymentProcessor.java      # 支付处理器
│   │   ├── PaymentCallback.java       # 回调处理器
│   │   └── PaymentNotifier.java       # 支付通知服务
│   └── config/            # 配置管理
│       └── PaymentConfig.java         # 支付配置
├── src/main/resources/
│   └── application.yml                # 配置文件
├── src/test/java/
│   └── PaymentModuleTest.java         # 集成测试
└── docs/
    └── database-schema.md             # 数据库设计
```

## 核心功能

### 1. 支付渠道支持
- **支付宝**: APP支付、H5支付、PC支付、扫码支付
- **微信支付**: APP支付、H5支付、小程序支付、扫码支付
- **苹果支付**: 内购支付、票据验证、订阅管理
- **Google支付**: 内购支付、令牌验证、订阅管理

### 2. 订单管理
- 分布式订单号生成（雪花算法优化版）
- 订单状态机管理
- 订单超时处理
- 防重复下单
- 订单数据加密存储

### 3. 支付流程
- 统一支付接口
- 渠道路由选择
- 异步回调处理
- 支付结果通知
- 幂等性保证

### 4. 安全机制
- 数据加密存储（AES-256）
- 签名验证（RSA2）
- 防重放攻击
- 敏感数据脱敏
- 访问控制

### 5. 监控告警
- 支付指标监控
- 交易成功率统计
- 渠道可用性监控
- 异常告警机制

## 快速开始

### 1. 配置文件

```yaml
game:
  payment:
    # 订单配置
    order:
      timeout: 30m
      number-prefix: "GM"
      max-retry-times: 3
    
    # 渠道配置
    channels:
      alipay:
        enabled: true
        app-id: ${ALIPAY_APP_ID}
        private-key: ${ALIPAY_PRIVATE_KEY}
        public-key: ${ALIPAY_PUBLIC_KEY}
        gateway-url: https://openapi.alipay.com/gateway.do
        
      wechat:
        enabled: true
        app-id: ${WECHAT_APP_ID}
        mch-id: ${WECHAT_MCH_ID}
        api-key: ${WECHAT_API_KEY}
```

### 2. 创建支付订单

```java
@Autowired
private PaymentProcessor paymentProcessor;

// 构建支付上下文
PaymentContext context = PaymentContext.builder()
    .userId(12345L)
    .productId("PRODUCT_001")
    .productName("游戏道具")
    .orderAmount(new BigDecimal("9.99"))
    .paymentChannel("alipay")
    .paymentMethod("app")
    .clientIp("192.168.1.100")
    .notifyUrl("https://api.game.com/payment/notify")
    .build();

// 处理支付
CompletableFuture<PaymentProcessResult> result = paymentProcessor.processPayment(context);
```

### 3. 处理支付回调

```java
@Autowired
private PaymentCallback paymentCallback;

@PostMapping("/notify/alipay")
public String handleAlipayNotify(@RequestParam Map<String, Object> params) {
    PaymentCallback.CallbackProcessResult result = paymentCallback.handleAlipayCallback(params).join();
    return result.isSuccess() ? "success" : "fail";
}
```

## 数据库设计

### 核心表结构

1. **payment_order**: 支付订单表
2. **payment_transaction**: 支付事务表
3. **refund_order**: 退款订单表
4. **reconciliation_record**: 对账记录表
5. **risk_event**: 风控事件表
6. **payment_channel**: 支付渠道表

详细的数据库设计请参考 [database-schema.md](docs/database-schema.md)

## 性能指标

- **TPS**: > 10,000
- **响应时间**: < 200ms
- **可用性**: > 99.99%
- **支付成功率**: > 99%

## 安全要求

- **PCI DSS合规**: 满足支付卡行业数据安全标准
- **数据加密**: 全程AES-256加密
- **签名验证**: RSA2048签名算法
- **防攻击**: 防SQL注入、XSS、重放攻击
- **审计日志**: 完整的操作审计记录

## 测试

### 运行测试

```bash
mvn test -pl business/payment
```

### 测试覆盖

- 订单号生成测试
- 订单验证测试
- 状态流转测试
- 金额转换测试
- 性能测试

## 部署说明

### 环境变量

```bash
# 支付宝配置
export ALIPAY_APP_ID=your_app_id
export ALIPAY_PRIVATE_KEY=your_private_key
export ALIPAY_PUBLIC_KEY=your_public_key

# 微信支付配置
export WECHAT_APP_ID=your_app_id
export WECHAT_MCH_ID=your_mch_id
export WECHAT_API_KEY=your_api_key

# 数据库配置
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
```

### 分布式部署

1. **服务注册**: 使用Nacos注册中心
2. **负载均衡**: 支持多实例部署
3. **配置中心**: 支持动态配置更新
4. **分库分表**: 按用户ID分库分表

## 监控

### 关键指标

- 支付订单总数
- 支付成功率
- 平均响应时间
- 渠道可用性
- 异常事件数量

### 告警规则

- 支付成功率 < 95%
- 响应时间 > 5秒
- 渠道不可用
- 大额交易异常

## 最佳实践

1. **幂等性**: 所有支付接口都支持幂等调用
2. **重试机制**: 自动重试失败的支付请求
3. **降级策略**: 支持渠道降级和熔断
4. **数据一致性**: 使用分布式事务保证数据一致性
5. **安全防护**: 多层安全防护机制

## 版本历史

- **v1.0.0**: 初始版本，支持支付宝、微信支付、苹果支付、Google支付

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交代码
4. 发起 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证。

## 联系方式

- 作者: liuxiao2015
- 邮箱: 请通过GitHub联系

---

*本文档由 liuxiao2015 编写，最后更新于 2025-01-13*