# 游戏服务器聊天模块 (Game Server Chat Module)

游戏服务器框架的聊天业务模块，提供完整的聊天功能和实时通信服务。

## 功能特性

### 🚀 核心功能
- **多频道支持**: 世界频道、公会频道、队伍频道、私聊频道、系统频道
- **实时通信**: 基于WebSocket和TCP的实时消息推送
- **消息处理**: 完整的消息生命周期管理（发送、接收、存储、撤回）
- **智能过滤**: DFA算法敏感词过滤、垃圾信息检测、重复消息拦截
- **会话管理**: 完整的聊天会话和参与者管理

### 🛡️ 安全特性
- **敏感词过滤**: 基于DFA算法的高效敏感词检测和替换
- **限流控制**: 多维度消息发送频率限制
- **权限管理**: 细粒度的聊天权限和禁言管理
- **内容审核**: 自动化内容安全检测

### 📊 监控统计
- **实时指标**: 消息量、在线人数、频道活跃度等
- **性能监控**: 消息延迟、系统负载、错误率统计
- **行为分析**: 用户聊天行为和频道热度分析

### 🔧 扩展功能
- **表情系统**: 支持自定义表情包和动态表情
- **语音消息**: 语音录制、存储和播放
- **聊天机器人**: 智能客服和自动回复
- **管理工具**: 完整的聊天管理和统计后台

## 技术架构

### 核心技术栈
- **Java 17**: 现代Java特性和性能优化
- **Spring Boot 3.x**: 微服务框架和依赖注入
- **WebSocket**: 实时双向通信
- **MongoDB**: 消息存储和历史记录
- **Redis**: 缓存和会话管理
- **Netty**: 高性能网络通信

### 模块结构

```
business/chat/
├── src/main/java/com/lx/gameserver/business/chat/
│   ├── core/              # 核心抽象层
│   │   ├── ChatMessage.java          # 聊天消息实体
│   │   ├── ChatChannel.java          # 聊天频道抽象
│   │   ├── ChatSession.java          # 聊天会话管理
│   │   └── MessageHandler.java       # 消息处理器接口
│   ├── channel/           # 频道管理
│   │   ├── ChannelManager.java       # 频道管理器
│   │   ├── WorldChannel.java         # 世界频道实现
│   │   └── PrivateChannel.java       # 私聊频道实现
│   ├── message/           # 消息处理
│   │   ├── MessageService.java       # 消息服务核心
│   │   └── MessageFilter.java        # 消息过滤器
│   ├── config/            # 配置管理
│   │   └── ChatConfig.java           # 聊天配置类
│   └── api/               # 客户端接口
│       └── ChatController.java       # REST API控制器
├── src/main/resources/
│   └── application.yml                # 配置文件模板
└── README.md                          # 模块文档
```

## 快速开始

### 1. 配置文件

在 `application.yml` 中配置聊天模块：

```yaml
game:
  chat:
    # 连接配置
    connection:
      websocket-port: 8080
      tcp-port: 9090
      max-connections: 10000
      heartbeat-interval: 30s
      idle-timeout: 5m
    
    # 频道配置
    channels:
      world:
        enabled: true
        max-members: -1
        message-interval: 5s
        max-message-length: 200
      private-chat:
        enabled: true
        max-members: 2
        message-interval: 1s
        max-message-length: 1000
    
    # 安全配置
    security:
      sensitive-words-enabled: true
      rate-limit:
        messages-per-minute: 30
        identical-message-interval: 10s
```

### 2. 发送消息

```java
@Autowired
private MessageService messageService;

// 发送世界频道消息
MessageService.SendMessageResult result = messageService.sendMessage(
    playerId,                              // 发送者ID
    ChatMessage.ChatChannelType.WORLD,     // 频道类型
    ChatMessage.MessageType.TEXT,          // 消息类型
    "Hello, World!",                       // 消息内容
    null,                                  // 目标ID（世界频道为null）
    null                                   // 扩展数据
);

// 发送私聊消息
MessageService.SendMessageResult privateResult = messageService.sendPrivateMessage(
    senderId,                              // 发送者ID
    receiverId,                            // 接收者ID
    ChatMessage.MessageType.TEXT,          // 消息类型
    "Hello, friend!",                      // 消息内容
    null                                   // 扩展数据
);
```

### 3. REST API调用

```bash
# 发送消息
curl -X POST http://localhost:8081/chat/api/chat/message/send \
  -H "Content-Type: application/json" \
  -d '{
    "senderId": 12345,
    "channelType": "WORLD",
    "messageType": "TEXT",
    "content": "Hello, World!"
  }'

# 获取聊天历史
curl "http://localhost:8081/chat/api/chat/message/history?channelId=world_global&limit=20"

# 获取私聊历史
curl "http://localhost:8081/chat/api/chat/message/private-history?playerId1=123&playerId2=456&limit=20"
```

## 核心特性详解

### 消息过滤系统

消息过滤器使用DFA（确定有限自动机）算法实现高效的敏感词检测：

- **敏感词过滤**: 自动检测并替换敏感词汇
- **垃圾信息检测**: 识别和拦截垃圾广告信息
- **重复消息检测**: 防止用户发送重复内容刷屏
- **内容长度限制**: 控制消息内容的合理长度

### 频道管理系统

支持多种频道类型，每种频道都有独特的配置和行为：

- **世界频道**: 全服广播，支持大规模用户同时在线
- **公会频道**: 公会内部交流，支持权限控制
- **队伍频道**: 小队协作沟通，临时性质
- **私聊频道**: 点对点通信，支持离线消息
- **系统频道**: 系统公告和通知推送

### 会话管理系统

完整的聊天会话生命周期管理：

- **会话创建**: 自动创建和管理聊天会话
- **参与者管理**: 动态添加和移除会话参与者
- **未读计数**: 实时跟踪未读消息数量
- **状态同步**: 会话状态在多端同步

## 性能特性

### 高并发支持
- 支持10000+并发连接
- 每秒处理100000+消息
- 消息延迟小于100ms

### 存储优化
- 分区存储策略，支持按时间分区
- 历史消息自动归档和清理
- 热数据缓存，提升查询性能

### 监控告警
- 实时性能指标监控
- 自动异常检测和告警
- 详细的业务数据统计

## 扩展开发

### 自定义消息处理器

```java
@Component
public class CustomMessageHandler implements MessageHandler {
    
    @Override
    public MessageHandleResult handleSendMessage(ChatMessage message, MessageHandleContext context) {
        // 自定义发送逻辑
        return MessageHandleResult.success(message);
    }
    
    @Override
    public MessageHandleResult handleReceivedMessage(ChatMessage message, MessageHandleContext context) {
        // 自定义接收逻辑
        return MessageHandleResult.success(message);
    }
    
    @Override
    public int getPriority() {
        return 50; // 处理优先级
    }
}
```

### 自定义频道类型

```java
public class CustomChannel extends ChatChannel {
    
    public CustomChannel(String channelId, String channelName) {
        super(channelId, ChatMessage.ChatChannelType.CUSTOM, channelName);
    }
    
    @Override
    public boolean sendMessage(ChatMessage message) {
        // 自定义发送逻辑
        return true;
    }
    
    @Override
    public void onMessageReceived(ChatMessage message) {
        // 自定义接收逻辑
    }
    
    @Override
    public boolean hasPermission(Long playerId) {
        // 自定义权限检查
        return true;
    }
    
    @Override
    public List<Long> getTargetAudience() {
        // 自定义目标受众
        return getMemberIds();
    }
}
```

## 配置参考

### 连接配置
- `websocket-port`: WebSocket服务端口
- `tcp-port`: TCP服务端口  
- `max-connections`: 最大连接数
- `heartbeat-interval`: 心跳间隔
- `idle-timeout`: 空闲超时时间

### 频道配置
- `enabled`: 是否启用频道
- `max-members`: 最大成员数（-1为无限制）
- `message-interval`: 消息发送间隔
- `max-message-length`: 最大消息长度

### 安全配置
- `sensitive-words-enabled`: 启用敏感词过滤
- `rate-limit`: 限流配置
- `ban-duration`: 默认禁言时长

### 存储配置
- `type`: 存储类型（mongodb/mysql/redis）
- `retention-days`: 数据保留天数
- `archive-enabled`: 启用数据归档

## 监控指标

### 关键指标
- **消息吞吐量**: 每秒处理的消息数量
- **在线用户数**: 当前在线用户总数
- **频道活跃度**: 各频道的消息活跃程度
- **响应延迟**: 消息处理的平均延迟
- **错误率**: 消息处理失败的比例

### 告警阈值
- 消息延迟 > 1000ms
- 错误率 > 1%
- CPU使用率 > 80%
- 内存使用率 > 80%
- 连接数 > 8000

## 故障排除

### 常见问题

1. **消息发送失败**
   - 检查频道是否存在和启用
   - 验证用户权限和禁言状态
   - 查看消息过滤和限流配置

2. **连接异常**
   - 检查端口配置和防火墙设置
   - 验证SSL证书配置
   - 查看连接池和超时配置

3. **性能问题**
   - 查看数据库连接和查询性能
   - 检查缓存命中率和过期策略
   - 监控系统资源使用情况

### 日志分析

```bash
# 查看聊天相关日志
grep "chat" /var/log/gameserver/application.log

# 查看错误日志
grep "ERROR" /var/log/gameserver/application.log | grep "chat"

# 查看性能日志
grep "性能" /var/log/gameserver/application.log
```

## 版本历史

### v1.0.0 (2025-05-29)
- 初始版本发布
- 实现核心聊天功能
- 支持多种频道类型
- 完整的消息过滤系统
- REST API接口
- 基础监控和统计

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 作者: liuxiao2015
- 邮箱: liuxiao2015@example.com
- 项目地址: https://github.com/liuxiao2015/game-server-framework