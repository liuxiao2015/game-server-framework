# 支付系统数据库设计

## 数据库表结构

### 1. payment_order - 支付订单表

```sql
CREATE TABLE `payment_order` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` VARCHAR(64) NOT NULL COMMENT '订单号',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `product_id` VARCHAR(64) NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称',
  `order_type` VARCHAR(20) NOT NULL DEFAULT 'PRODUCT' COMMENT '订单类型',
  `order_amount` BIGINT(20) NOT NULL COMMENT '订单金额（分）',
  `paid_amount` BIGINT(20) DEFAULT NULL COMMENT '实际支付金额（分）',
  `currency` VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '货币类型',
  `payment_channel` VARCHAR(20) NOT NULL COMMENT '支付渠道',
  `payment_method` VARCHAR(20) NOT NULL COMMENT '支付方式',
  `channel_order_id` VARCHAR(100) DEFAULT NULL COMMENT '渠道订单号',
  `order_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态',
  `order_title` VARCHAR(100) DEFAULT NULL COMMENT '订单标题',
  `order_desc` VARCHAR(500) DEFAULT NULL COMMENT '订单描述',
  `notify_url` VARCHAR(500) DEFAULT NULL COMMENT '回调URL',
  `return_url` VARCHAR(500) DEFAULT NULL COMMENT '返回URL',
  `client_ip` VARCHAR(64) NOT NULL COMMENT '客户端IP',
  `device_info` VARCHAR(200) DEFAULT NULL COMMENT '设备信息',
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `business_data` TEXT DEFAULT NULL COMMENT '业务扩展数据',
  `channel_data` TEXT DEFAULT NULL COMMENT '渠道扩展数据',
  `risk_data` TEXT DEFAULT NULL COMMENT '风控数据',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `version` INT(11) NOT NULL DEFAULT 1 COMMENT '版本号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建者',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新者',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_channel_order_id` (`channel_order_id`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_payment_channel` (`payment_channel`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';
```

### 核心设计原则

1. **数据一致性**: 使用乐观锁机制保证并发更新安全
2. **性能优化**: 合理设计索引，支持高频查询场景
3. **扩展性**: 使用JSON字段存储扩展数据
4. **安全性**: 敏感数据加密存储
5. **可追溯**: 完整的审计字段和软删除机制

### 分库分表策略

- **分库**: 按用户ID取模分库（8库）
- **分表**: 按时间分表（按月分表）
- **路由**: 使用一致性哈希算法

### 性能指标要求

- **TPS**: > 10,000
- **响应时间**: < 200ms
- **可用性**: > 99.99%