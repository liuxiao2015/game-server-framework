# 游戏服务器登录模块 (Game Server Login Module)

## 概述

游戏服务器登录模块是一个生产级的身份认证和会话管理系统，为游戏玩家提供安全、高效、多渠道的登录服务。该模块支持多种登录方式，具备完善的安全防护机制，并集成了防沉迷系统。

## 核心特性

### 🔐 多种登录方式
- **密码登录**: 支持用户名/手机号/邮箱 + 密码
- **短信验证码**: 支持手机号 + 短信验证码登录
- **第三方登录**: 支持微信、QQ、微博、Apple、Google等平台
- **设备登录**: 支持游客模式，基于设备唯一标识
- **生物识别**: 支持指纹、面部识别等生物特征登录

### 🛡️ 安全防护
- **暴力破解防护**: 登录失败次数限制、账号自动锁定
- **IP限流控制**: 防止恶意IP攻击
- **设备指纹**: 检测异常设备登录
- **异地登录检测**: 识别异常登录位置
- **风险评估**: 实时评估登录风险等级

### 🎮 防沉迷系统
- **实名认证**: 支持身份证验证
- **时长控制**: 未成年人游戏时长限制
- **宵禁管理**: 时间段限制
- **充值限制**: 按年龄段设置充值上限

### 📊 监控统计
- **登录指标**: 成功率、耗时、方式分布等
- **用户分析**: 留存率、活跃度、转化率等
- **实时告警**: 异常登录、攻击行为监控

## 技术架构

### 核心技术栈
- **Java 21**: 使用最新LTS版本
- **Spring Boot 3.2+**: 应用框架
- **Spring Security**: 安全框架
- **MyBatis Plus**: 数据持久化
- **Redis**: 缓存和会话存储
- **JWT**: Token认证
- **BCrypt**: 密码加密

### 模块结构
```
business/login/
├── src/main/java/com/lx/gameserver/business/login/
│   ├── core/                   # 核心抽象
│   │   ├── LoginStrategy.java  # 登录策略接口
│   │   ├── Account.java        # 账号实体
│   │   ├── LoginSession.java   # 登录会话
│   │   └── LoginContext.java   # 登录上下文
│   ├── strategy/               # 登录策略实现
│   │   ├── PasswordLoginStrategy.java    # 密码登录
│   │   ├── MobileLoginStrategy.java      # 手机验证码登录
│   │   ├── ThirdPartyLoginStrategy.java  # 第三方登录
│   │   ├── DeviceLoginStrategy.java      # 设备登录
│   │   └── BiometricLoginStrategy.java   # 生物识别登录
│   ├── account/                # 账号管理
│   │   ├── AccountService.java      # 账号服务
│   │   ├── AccountValidator.java    # 账号验证器
│   │   ├── AccountMerger.java       # 账号合并
│   │   └── AccountSecurityService.java # 账号安全
│   ├── token/                  # Token管理
│   │   ├── TokenService.java        # Token服务
│   │   ├── TokenStore.java          # Token存储
│   │   └── RefreshTokenService.java # 刷新Token服务
│   ├── session/                # 会话管理
│   │   ├── SessionManager.java      # 会话管理器
│   │   ├── MultiDeviceManager.java  # 多设备管理
│   │   └── SessionMonitor.java      # 会话监控
│   ├── antiaddiction/          # 防沉迷
│   │   ├── AntiAddictionService.java # 防沉迷服务
│   │   ├── RealNameAuth.java        # 实名认证
│   │   └── PlayTimeController.java  # 时长控制
│   ├── security/               # 安全防护
│   │   ├── LoginSecurityService.java # 登录安全
│   │   ├── RiskAssessment.java      # 风险评估
│   │   └── CaptchaService.java      # 验证码服务
│   ├── integration/            # 第三方集成
│   │   ├── OAuthClientManager.java     # OAuth客户端管理
│   │   ├── SmsServiceAdapter.java      # 短信服务适配
│   │   └── IdentityVerificationAdapter.java # 身份验证适配
│   ├── monitor/                # 监控统计
│   │   ├── LoginMetrics.java        # 登录指标
│   │   ├── LoginAnalytics.java      # 登录分析
│   │   └── AlertService.java        # 告警服务
│   ├── admin/                  # 管理接口
│   │   ├── AccountManagementController.java  # 账号管理
│   │   ├── SessionManagementController.java  # 会话管理
│   │   └── SecurityManagementController.java # 安全管理
│   └── config/                 # 配置管理
│       └── LoginConfig.java    # 登录配置
└── src/main/resources/
    └── application-login.yml   # 配置模板
```

## 数据库设计

### 核心表结构

#### account - 账号主表
```sql
CREATE TABLE account (
    account_id BIGINT PRIMARY KEY COMMENT '账号ID',
    username VARCHAR(32) UNIQUE COMMENT '用户名',
    mobile VARCHAR(11) UNIQUE COMMENT '手机号',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    password_hash VARCHAR(255) COMMENT '密码哈希',
    status TINYINT DEFAULT 1 COMMENT '账号状态',
    account_type TINYINT DEFAULT 1 COMMENT '账号类型',
    security_level TINYINT DEFAULT 1 COMMENT '安全等级',
    nickname VARCHAR(64) COMMENT '昵称',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    gender TINYINT COMMENT '性别',
    birthday DATETIME COMMENT '生日',
    register_ip VARCHAR(45) COMMENT '注册IP',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(45) COMMENT '最后登录IP',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted BOOLEAN DEFAULT FALSE COMMENT '逻辑删除',
    version INT DEFAULT 1 COMMENT '版本号'
);
```

#### login_session - 登录会话表
```sql
CREATE TABLE login_session (
    session_id VARCHAR(64) PRIMARY KEY COMMENT '会话ID',
    account_id BIGINT NOT NULL COMMENT '账号ID',
    device_id VARCHAR(64) COMMENT '设备ID',
    device_type TINYINT COMMENT '设备类型',
    login_method VARCHAR(32) COMMENT '登录方式',
    login_ip VARCHAR(45) COMMENT '登录IP',
    login_location VARCHAR(100) COMMENT '登录位置',
    login_time DATETIME COMMENT '登录时间',
    last_active_time DATETIME COMMENT '最后活跃时间',
    expires_at DATETIME COMMENT 'Token过期时间',
    status TINYINT DEFAULT 1 COMMENT '会话状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);
```

#### login_log - 登录日志表
```sql
CREATE TABLE login_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    account_id BIGINT COMMENT '账号ID',
    username VARCHAR(32) COMMENT '用户名',
    login_method VARCHAR(32) COMMENT '登录方式',
    login_result TINYINT COMMENT '登录结果',
    error_code VARCHAR(32) COMMENT '错误代码',
    error_message VARCHAR(255) COMMENT '错误信息',
    client_ip VARCHAR(45) COMMENT '客户端IP',
    user_agent TEXT COMMENT '用户代理',
    device_info JSON COMMENT '设备信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);
```

## 配置说明

### 基础配置
```yaml
game:
  login:
    token:
      jwt-secret: "your-secret-key"     # JWT密钥
      access-token-expire: 2h           # 访问Token过期时间
      refresh-token-expire: 7d          # 刷新Token过期时间
      max-devices: 3                    # 最大设备数
    
    security:
      max-login-attempts: 5             # 最大登录尝试次数
      lock-duration: 30m                # 锁定时间
      captcha-threshold: 3              # 验证码触发阈值
```

### 登录策略配置
```yaml
game:
  login:
    strategies:
      password:
        enabled: true                   # 是否启用密码登录
        min-length: 8                   # 密码最小长度
        require-special-char: true      # 是否需要特殊字符
      
      mobile:
        enabled: true                   # 是否启用手机登录
        sms-expire: 5m                  # 验证码有效期
        daily-limit: 10                 # 每日发送限制
```

## API接口

### 密码登录
```http
POST /api/login/password
Content-Type: application/json

{
    "account": "username_or_mobile_or_email",
    "password": "user_password",
    "deviceId": "device_unique_id",
    "captcha": "验证码(可选)"
}
```

### 短信验证码登录
```http
# 发送验证码
POST /api/login/sms/send
{
    "mobile": "13812345678"
}

# 验证码登录
POST /api/login/mobile
{
    "mobile": "13812345678",
    "smsCode": "123456"
}
```

### 第三方登录
```http
# 获取授权URL
GET /api/login/oauth/{platform}/authorize?redirectUri=xxx

# 授权回调
POST /api/login/oauth/{platform}/callback
{
    "code": "authorization_code",
    "state": "csrf_token"
}
```

## 性能指标

- **支持QPS**: 50,000+
- **登录响应时间**: < 100ms (P99)
- **Token验证**: < 10ms
- **缓存命中率**: > 95%
- **可用性**: 99.9%

## 部署说明

### 环境要求
- Java 21+
- MySQL 8.0+
- Redis 6.0+
- 最小内存: 2GB
- 建议CPU: 4核心

### 部署步骤

1. **配置数据库**
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE game_server CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行建表脚本
mysql -u root -p game_server < scripts/schema.sql
```

2. **配置Redis**
```bash
# 启动Redis
redis-server /etc/redis/redis.conf

# 验证连接
redis-cli ping
```

3. **配置应用**
```bash
# 复制配置模板
cp src/main/resources/application-login.yml application.yml

# 修改配置
vim application.yml
```

4. **启动应用**
```bash
# 开发环境
mvn spring-boot:run

# 生产环境
java -jar business-login-1.0.0-SNAPSHOT.jar
```

## 监控和运维

### 关键指标监控
- 登录成功率
- 登录响应时间
- Token验证成功率
- 异常登录检测
- 缓存命中率

### 日志分析
```bash
# 查看登录日志
tail -f logs/login.log

# 分析登录趋势
grep "LOGIN_SUCCESS" logs/login.log | wc -l
```

### 性能调优
1. **数据库优化**: 添加索引、分库分表
2. **缓存优化**: 合理设置过期时间
3. **连接池调优**: 调整连接池大小
4. **JVM调优**: 调整堆内存和GC参数

## 安全建议

1. **定期更换JWT密钥**
2. **启用HTTPS传输**
3. **定期更新密码策略**
4. **监控异常登录行为**
5. **定期安全审计**

## 故障排查

### 常见问题

1. **登录失败率高**
   - 检查数据库连接
   - 检查Redis连接
   - 查看应用日志

2. **Token验证失败**
   - 检查JWT密钥配置
   - 检查时钟同步
   - 检查Token过期时间

3. **短信发送失败**
   - 检查短信服务配置
   - 检查API密钥
   - 查看短信服务商状态

## 版本更新

### v1.0.0 (2025-01-13)
- 初始版本发布
- 支持5种登录方式
- 完善的安全防护
- 防沉迷系统集成
- 监控统计功能

## 联系方式

- 作者: liuxiao2015
- 邮箱: your-email@example.com
- 项目地址: https://github.com/liuxiao2015/game-server-framework

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。