/*
 * 文件名: OrderRepository.java
 * 用途: 订单数据访问层接口
 * 实现内容:
 *   - 订单数据访问抽象接口
 *   - 分库分表支持和路由策略
 *   - 订单归档策略和生命周期管理
 *   - 热数据缓存和查询优化
 *   - 数据一致性保证和事务支持
 * 技术选型:
 *   - Repository模式
 *   - MyBatis Plus
 *   - 分库分表中间件
 *   - Redis缓存
 * 依赖关系:
 *   - 被OrderService调用
 *   - 依赖数据库和缓存
 *   - 支持分库分表
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.order;

import com.lx.gameserver.business.payment.core.PaymentOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单数据访问层接口
 * <p>
 * 定义订单数据的存储、查询、更新等操作接口，
 * 支持分库分表、缓存、统计等高级功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface OrderRepository {

    /**
     * 保存订单
     *
     * @param order 支付订单
     * @return 保存后的订单（包含生成的ID）
     */
    PaymentOrder save(PaymentOrder order);

    /**
     * 更新订单
     *
     * @param order 支付订单
     * @return 是否成功
     */
    boolean update(PaymentOrder order);

    /**
     * 根据订单号查询订单
     *
     * @param orderId 订单号
     * @return 支付订单
     */
    PaymentOrder findByOrderId(String orderId);

    /**
     * 根据渠道订单号查询订单
     *
     * @param channelOrderId 渠道订单号
     * @return 支付订单
     */
    PaymentOrder findByChannelOrderId(String channelOrderId);

    /**
     * 根据用户ID查询订单列表
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 订单列表
     */
    List<PaymentOrder> findByUserIdOrderByCreateTimeDesc(Long userId, int limit);

    /**
     * 根据状态查询订单列表
     *
     * @param status 订单状态
     * @param limit 限制数量
     * @return 订单列表
     */
    List<PaymentOrder> findByOrderStatusOrderByCreateTimeDesc(PaymentOrder.OrderStatus status, int limit);

    /**
     * 查询指定时间范围内的订单
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制数量
     * @return 订单列表
     */
    List<PaymentOrder> findByCreateTimeBetween(LocalDateTime startTime, LocalDateTime endTime, int limit);

    /**
     * 查询用户指定商品的待支付订单
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @param afterTime 创建时间阈值
     * @return 待支付订单
     */
    PaymentOrder findPendingOrderByUserAndProduct(Long userId, String productId, LocalDateTime afterTime);

    /**
     * 查询超时订单
     *
     * @param currentTime 当前时间
     * @return 超时订单列表
     */
    List<PaymentOrder> findTimeoutOrders(LocalDateTime currentTime);

    /**
     * 批量更新订单状态
     *
     * @param orderIds 订单号列表
     * @param newStatus 新状态
     * @param remark 备注
     * @return 更新数量
     */
    int batchUpdateStatus(List<String> orderIds, PaymentOrder.OrderStatus newStatus, String remark);

    /**
     * 删除订单（逻辑删除）
     *
     * @param orderId 订单号
     * @return 是否成功
     */
    boolean deleteByOrderId(String orderId);

    /**
     * 获取订单统计信息
     *
     * @param userId 用户ID（可选）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息
     */
    Map<String, Object> getOrderStatistics(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取用户订单数量
     *
     * @param userId 用户ID
     * @param status 订单状态（可选）
     * @return 订单数量
     */
    long countByUserId(Long userId, PaymentOrder.OrderStatus status);

    /**
     * 获取商品订单数量
     *
     * @param productId 商品ID
     * @param status 订单状态（可选）
     * @return 订单数量
     */
    long countByProductId(String productId, PaymentOrder.OrderStatus status);

    /**
     * 获取指定时间段的订单数量
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param status 订单状态（可选）
     * @return 订单数量
     */
    long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime, PaymentOrder.OrderStatus status);

    /**
     * 检查订单是否存在
     *
     * @param orderId 订单号
     * @return 是否存在
     */
    boolean existsByOrderId(String orderId);

    /**
     * 归档历史订单
     *
     * @param beforeTime 归档时间点
     * @return 归档数量
     */
    int archiveOrdersBefore(LocalDateTime beforeTime);

    /**
     * 清理已归档的订单
     *
     * @param beforeTime 清理时间点
     * @return 清理数量
     */
    int cleanArchivedOrdersBefore(LocalDateTime beforeTime);

    // ========== 缓存相关方法 ==========

    /**
     * 从缓存获取订单
     *
     * @param orderId 订单号
     * @return 支付订单
     */
    PaymentOrder getFromCache(String orderId);

    /**
     * 将订单放入缓存
     *
     * @param order 支付订单
     * @param expireSeconds 过期时间（秒）
     */
    void putToCache(PaymentOrder order, int expireSeconds);

    /**
     * 从缓存移除订单
     *
     * @param orderId 订单号
     */
    void removeFromCache(String orderId);

    /**
     * 刷新缓存
     *
     * @param orderId 订单号
     */
    void refreshCache(String orderId);

    // ========== 分库分表相关方法 ==========

    /**
     * 根据用户ID计算分库分表键
     *
     * @param userId 用户ID
     * @return 分库分表键
     */
    String calculateShardingKey(Long userId);

    /**
     * 获取订单所在的表名
     *
     * @param orderId 订单号
     * @return 表名
     */
    String getTableName(String orderId);

    /**
     * 获取订单所在的数据库名
     *
     * @param orderId 订单号
     * @return 数据库名
     */
    String getDatabaseName(String orderId);
}