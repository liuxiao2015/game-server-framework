/*
 * 文件名: OrderRepositoryImpl.java
 * 用途: 订单数据访问层实现
 * 实现内容:
 *   - 订单数据访问具体实现
 *   - 内存存储模拟数据库操作
 *   - 缓存机制和查询优化
 *   - 统计功能和数据分析
 *   - 分库分表逻辑模拟
 * 技术选型:
 *   - 内存存储（演示用）
 *   - ConcurrentHashMap
 *   - 线程安全设计
 *   - 简化版分库分表
 * 依赖关系:
 *   - 实现OrderRepository接口
 *   - 独立的数据访问层
 *   - 可替换为真实数据库实现
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.order;

import com.lx.gameserver.business.payment.core.PaymentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 订单数据访问层实现
 * <p>
 * 基于内存存储的订单数据访问实现，用于演示和测试。
 * 生产环境应替换为基于数据库的实现。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepositoryImpl.class);

    /**
     * 订单存储（模拟数据库表）
     */
    private final Map<String, PaymentOrder> orderStore = new ConcurrentHashMap<>();

    /**
     * 订单缓存
     */
    private final Map<String, PaymentOrder> orderCache = new ConcurrentHashMap<>();

    /**
     * 缓存过期时间
     */
    private final Map<String, Long> cacheExpireTime = new ConcurrentHashMap<>();

    /**
     * ID生成器
     */
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public PaymentOrder save(PaymentOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("订单对象不能为空");
        }

        // 生成ID
        if (order.getId() == null) {
            order.setId(idGenerator.getAndIncrement());
        }

        // 设置创建时间
        if (order.getCreateTime() == null) {
            order.setCreateTime(LocalDateTime.now());
        }

        // 设置更新时间
        order.setUpdateTime(LocalDateTime.now());

        // 设置版本号
        if (order.getVersion() == null) {
            order.setVersion(1);
        }

        // 保存到存储
        orderStore.put(order.getOrderId(), cloneOrder(order));

        // 放入缓存
        putToCache(order, 3600); // 缓存1小时

        logger.debug("保存订单成功: {}", order.getOrderId());
        return cloneOrder(order);
    }

    @Override
    public boolean update(PaymentOrder order) {
        if (order == null || order.getOrderId() == null) {
            return false;
        }

        PaymentOrder existing = orderStore.get(order.getOrderId());
        if (existing == null) {
            logger.warn("更新的订单不存在: {}", order.getOrderId());
            return false;
        }

        // 乐观锁检查
        if (order.getVersion() != null && !order.getVersion().equals(existing.getVersion())) {
            logger.warn("订单版本冲突: {} 期望版本={}, 当前版本={}", 
                    order.getOrderId(), order.getVersion(), existing.getVersion());
            return false;
        }

        // 更新版本号
        order.setVersion((existing.getVersion() != null ? existing.getVersion() : 0) + 1);
        order.setUpdateTime(LocalDateTime.now());

        // 更新存储
        orderStore.put(order.getOrderId(), cloneOrder(order));

        // 更新缓存
        putToCache(order, 3600);

        logger.debug("更新订单成功: {}", order.getOrderId());
        return true;
    }

    @Override
    public PaymentOrder findByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return null;
        }

        // 先从缓存查找
        PaymentOrder cached = getFromCache(orderId);
        if (cached != null) {
            logger.debug("从缓存获取订单: {}", orderId);
            return cached;
        }

        // 从存储查找
        PaymentOrder order = orderStore.get(orderId);
        if (order != null) {
            // 放入缓存
            putToCache(order, 3600);
            logger.debug("从存储获取订单: {}", orderId);
            return cloneOrder(order);
        }

        logger.debug("订单不存在: {}", orderId);
        return null;
    }

    @Override
    public PaymentOrder findByChannelOrderId(String channelOrderId) {
        if (channelOrderId == null || channelOrderId.trim().isEmpty()) {
            return null;
        }

        return orderStore.values().stream()
                .filter(order -> channelOrderId.equals(order.getChannelOrderId()))
                .findFirst()
                .map(this::cloneOrder)
                .orElse(null);
    }

    @Override
    public List<PaymentOrder> findByUserIdOrderByCreateTimeDesc(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }

        return orderStore.values().stream()
                .filter(order -> userId.equals(order.getUserId()))
                .sorted((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime()))
                .limit(limit)
                .map(this::cloneOrder)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentOrder> findByOrderStatusOrderByCreateTimeDesc(PaymentOrder.OrderStatus status, int limit) {
        if (status == null || limit <= 0) {
            return Collections.emptyList();
        }

        return orderStore.values().stream()
                .filter(order -> status.equals(order.getOrderStatus()))
                .sorted((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime()))
                .limit(limit)
                .map(this::cloneOrder)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentOrder> findByCreateTimeBetween(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        if (startTime == null || endTime == null || limit <= 0) {
            return Collections.emptyList();
        }

        return orderStore.values().stream()
                .filter(order -> order.getCreateTime() != null &&
                        !order.getCreateTime().isBefore(startTime) &&
                        !order.getCreateTime().isAfter(endTime))
                .sorted((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime()))
                .limit(limit)
                .map(this::cloneOrder)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentOrder findPendingOrderByUserAndProduct(Long userId, String productId, LocalDateTime afterTime) {
        if (userId == null || productId == null || afterTime == null) {
            return null;
        }

        return orderStore.values().stream()
                .filter(order -> userId.equals(order.getUserId()) &&
                        productId.equals(order.getProductId()) &&
                        PaymentOrder.OrderStatus.PENDING.equals(order.getOrderStatus()) &&
                        order.getCreateTime() != null &&
                        order.getCreateTime().isAfter(afterTime))
                .findFirst()
                .map(this::cloneOrder)
                .orElse(null);
    }

    @Override
    public List<PaymentOrder> findTimeoutOrders(LocalDateTime currentTime) {
        if (currentTime == null) {
            return Collections.emptyList();
        }

        return orderStore.values().stream()
                .filter(order -> order.getExpireTime() != null &&
                        order.getExpireTime().isBefore(currentTime) &&
                        (PaymentOrder.OrderStatus.PENDING.equals(order.getOrderStatus()) ||
                         PaymentOrder.OrderStatus.PAYING.equals(order.getOrderStatus())))
                .map(this::cloneOrder)
                .collect(Collectors.toList());
    }

    @Override
    public int batchUpdateStatus(List<String> orderIds, PaymentOrder.OrderStatus newStatus, String remark) {
        if (orderIds == null || orderIds.isEmpty() || newStatus == null) {
            return 0;
        }

        int updatedCount = 0;
        for (String orderId : orderIds) {
            PaymentOrder order = orderStore.get(orderId);
            if (order != null) {
                order.setOrderStatus(newStatus);
                order.setRemark(remark);
                order.setUpdateTime(LocalDateTime.now());
                order.setVersion((order.getVersion() != null ? order.getVersion() : 0) + 1);
                
                orderStore.put(orderId, order);
                putToCache(order, 3600);
                updatedCount++;
            }
        }

        logger.debug("批量更新订单状态: 总数={}, 成功={}, 状态={}", orderIds.size(), updatedCount, newStatus);
        return updatedCount;
    }

    @Override
    public boolean deleteByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return false;
        }

        PaymentOrder order = orderStore.get(orderId);
        if (order != null) {
            order.setDeleted(true);
            order.setUpdateTime(LocalDateTime.now());
            orderStore.put(orderId, order);
            removeFromCache(orderId);
            
            logger.debug("逻辑删除订单: {}", orderId);
            return true;
        }

        return false;
    }

    @Override
    public Map<String, Object> getOrderStatistics(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> statistics = new HashMap<>();

        // 过滤条件
        var stream = orderStore.values().stream();
        
        if (userId != null) {
            stream = stream.filter(order -> userId.equals(order.getUserId()));
        }
        
        if (startTime != null && endTime != null) {
            stream = stream.filter(order -> order.getCreateTime() != null &&
                    !order.getCreateTime().isBefore(startTime) &&
                    !order.getCreateTime().isAfter(endTime));
        }

        List<PaymentOrder> filteredOrders = stream.collect(Collectors.toList());

        // 统计总数
        statistics.put("totalCount", filteredOrders.size());

        // 按状态统计
        Map<PaymentOrder.OrderStatus, Long> statusCount = filteredOrders.stream()
                .collect(Collectors.groupingBy(PaymentOrder::getOrderStatus, Collectors.counting()));
        statistics.put("statusCount", statusCount);

        // 金额统计
        long totalAmount = filteredOrders.stream()
                .mapToLong(order -> order.getOrderAmount() != null ? order.getOrderAmount() : 0L)
                .sum();
        statistics.put("totalAmount", totalAmount);

        // 已支付金额
        long paidAmount = filteredOrders.stream()
                .filter(order -> PaymentOrder.OrderStatus.PAID.equals(order.getOrderStatus()))
                .mapToLong(order -> order.getPaidAmount() != null ? order.getPaidAmount() : 0L)
                .sum();
        statistics.put("paidAmount", paidAmount);

        // 成功率
        long paidCount = filteredOrders.stream()
                .mapToLong(order -> PaymentOrder.OrderStatus.PAID.equals(order.getOrderStatus()) ? 1 : 0)
                .sum();
        double successRate = filteredOrders.size() > 0 ? (double) paidCount / filteredOrders.size() : 0.0;
        statistics.put("successRate", successRate);

        logger.debug("订单统计完成: 用户={}, 总数={}, 成功率={}", userId, filteredOrders.size(), successRate);
        return statistics;
    }

    @Override
    public long countByUserId(Long userId, PaymentOrder.OrderStatus status) {
        if (userId == null) {
            return 0;
        }

        return orderStore.values().stream()
                .filter(order -> userId.equals(order.getUserId()) &&
                        (status == null || status.equals(order.getOrderStatus())))
                .count();
    }

    @Override
    public long countByProductId(String productId, PaymentOrder.OrderStatus status) {
        if (productId == null) {
            return 0;
        }

        return orderStore.values().stream()
                .filter(order -> productId.equals(order.getProductId()) &&
                        (status == null || status.equals(order.getOrderStatus())))
                .count();
    }

    @Override
    public long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime, PaymentOrder.OrderStatus status) {
        if (startTime == null || endTime == null) {
            return 0;
        }

        return orderStore.values().stream()
                .filter(order -> order.getCreateTime() != null &&
                        !order.getCreateTime().isBefore(startTime) &&
                        !order.getCreateTime().isAfter(endTime) &&
                        (status == null || status.equals(order.getOrderStatus())))
                .count();
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        return orderId != null && orderStore.containsKey(orderId);
    }

    @Override
    public int archiveOrdersBefore(LocalDateTime beforeTime) {
        // 模拟归档操作
        logger.info("归档{}之前的订单", beforeTime);
        return 0; // 简化实现
    }

    @Override
    public int cleanArchivedOrdersBefore(LocalDateTime beforeTime) {
        // 模拟清理操作
        logger.info("清理{}之前的归档订单", beforeTime);
        return 0; // 简化实现
    }

    // ========== 缓存相关方法实现 ==========

    @Override
    public PaymentOrder getFromCache(String orderId) {
        if (orderId == null) {
            return null;
        }

        // 检查缓存是否过期
        Long expireTime = cacheExpireTime.get(orderId);
        if (expireTime != null && System.currentTimeMillis() > expireTime) {
            removeFromCache(orderId);
            return null;
        }

        PaymentOrder cached = orderCache.get(orderId);
        return cached != null ? cloneOrder(cached) : null;
    }

    @Override
    public void putToCache(PaymentOrder order, int expireSeconds) {
        if (order != null && order.getOrderId() != null) {
            orderCache.put(order.getOrderId(), cloneOrder(order));
            cacheExpireTime.put(order.getOrderId(), System.currentTimeMillis() + expireSeconds * 1000L);
        }
    }

    @Override
    public void removeFromCache(String orderId) {
        if (orderId != null) {
            orderCache.remove(orderId);
            cacheExpireTime.remove(orderId);
        }
    }

    @Override
    public void refreshCache(String orderId) {
        if (orderId != null) {
            PaymentOrder order = orderStore.get(orderId);
            if (order != null) {
                putToCache(order, 3600);
            }
        }
    }

    // ========== 分库分表相关方法实现 ==========

    @Override
    public String calculateShardingKey(Long userId) {
        if (userId == null) {
            return "default";
        }
        // 简单的分片策略：按用户ID取模
        return "shard_" + (userId % 8);
    }

    @Override
    public String getTableName(String orderId) {
        if (orderId == null) {
            return "payment_order";
        }
        // 简单的分表策略：按订单号哈希
        int tableIndex = Math.abs(orderId.hashCode()) % 4;
        return "payment_order_" + tableIndex;
    }

    @Override
    public String getDatabaseName(String orderId) {
        if (orderId == null) {
            return "payment_db";
        }
        // 简单的分库策略：按订单号哈希
        int dbIndex = Math.abs(orderId.hashCode()) % 2;
        return "payment_db_" + dbIndex;
    }

    // ========== 私有方法 ==========

    /**
     * 克隆订单对象（防止外部修改）
     */
    private PaymentOrder cloneOrder(PaymentOrder order) {
        if (order == null) {
            return null;
        }

        PaymentOrder cloned = new PaymentOrder();
        cloned.setId(order.getId());
        cloned.setOrderId(order.getOrderId());
        cloned.setUserId(order.getUserId());
        cloned.setProductId(order.getProductId());
        cloned.setProductName(order.getProductName());
        cloned.setOrderType(order.getOrderType());
        cloned.setOrderAmount(order.getOrderAmount());
        cloned.setPaidAmount(order.getPaidAmount());
        cloned.setCurrency(order.getCurrency());
        cloned.setPaymentChannel(order.getPaymentChannel());
        cloned.setPaymentMethod(order.getPaymentMethod());
        cloned.setChannelOrderId(order.getChannelOrderId());
        cloned.setOrderStatus(order.getOrderStatus());
        cloned.setOrderTitle(order.getOrderTitle());
        cloned.setOrderDesc(order.getOrderDesc());
        cloned.setNotifyUrl(order.getNotifyUrl());
        cloned.setReturnUrl(order.getReturnUrl());
        cloned.setClientIp(order.getClientIp());
        cloned.setDeviceInfo(order.getDeviceInfo());
        cloned.setExpireTime(order.getExpireTime());
        cloned.setPayTime(order.getPayTime());
        cloned.setBusinessData(order.getBusinessData());
        cloned.setChannelData(order.getChannelData());
        cloned.setRiskData(order.getRiskData());
        cloned.setRemark(order.getRemark());
        cloned.setVersion(order.getVersion());
        cloned.setCreateTime(order.getCreateTime());
        cloned.setUpdateTime(order.getUpdateTime());
        cloned.setCreateBy(order.getCreateBy());
        cloned.setUpdateBy(order.getUpdateBy());
        cloned.setDeleted(order.getDeleted());

        return cloned;
    }

    /**
     * 获取存储统计信息
     */
    public Map<String, Object> getStorageInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("totalOrders", orderStore.size());
        info.put("cachedOrders", orderCache.size());
        info.put("cacheHitRate", calculateCacheHitRate());
        return info;
    }

    /**
     * 计算缓存命中率（简化实现）
     */
    private double calculateCacheHitRate() {
        // 简化实现，实际应该记录命中和未命中次数
        return 0.85; // 假设85%命中率
    }
}