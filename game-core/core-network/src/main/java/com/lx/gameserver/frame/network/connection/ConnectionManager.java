/*
 * 文件名: ConnectionManager.java
 * 用途: 连接管理器
 * 实现内容:
 *   - 连接池维护和生命周期管理
 *   - 连接查找（ID查找、属性过滤）
 *   - 连接限制（IP限制、总数限制）
 *   - 连接分组管理和批量操作
 *   - 连接统计信息收集
 *   - 线程安全的连接操作
 * 技术选型:
 *   - ConcurrentHashMap高并发连接存储
 *   - 原子操作保证线程安全
 *   - 事件驱动的连接状态管理
 *   - 高效的连接查找和过滤
 * 依赖关系:
 *   - 管理Connection接口实例
 *   - 被NetworkServer和NetworkClient使用
 *   - 与ConnectionEventHandler协作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.connection;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 连接管理器
 * <p>
 * 提供连接的统一管理功能，包括连接注册、查找、分组、
 * 限制控制和统计信息收集。支持高并发场景下的安全操作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    /**
     * 连接存储 - 以连接ID为键的主索引
     */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    /**
     * IP连接计数 - 用于IP连接数限制
     */
    private final Map<String, AtomicInteger> ipConnectionCounts = new ConcurrentHashMap<>();

    /**
     * 连接分组 - 支持按组管理连接
     */
    private final Map<String, Set<String>> connectionGroups = new ConcurrentHashMap<>();

    /**
     * 配置参数
     */
    private final ConnectionManagerConfig config;

    /**
     * 统计信息
     */
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong rejectedConnections = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param config 连接管理器配置
     */
    public ConnectionManager(ConnectionManagerConfig config) {
        this.config = Objects.requireNonNull(config, "连接管理器配置不能为null");
        logger.info("初始化连接管理器，最大连接数: {}, 单IP最大连接数: {}", 
                   config.getMaxConnections(), config.getMaxConnectionsPerIp());
    }

    /**
     * 注册连接
     *
     * @param connection 要注册的连接
     * @return true表示注册成功，false表示注册失败
     */
    public boolean registerConnection(Connection connection) {
        if (connection == null) {
            logger.warn("尝试注册null连接");
            return false;
        }

        String connectionId = connection.getId();
        String remoteIp = getRemoteIp(connection);

        // 检查连接数限制
        if (!checkConnectionLimits(remoteIp)) {
            rejectedConnections.incrementAndGet();
            logger.warn("连接被拒绝，超出限制: {}, IP: {}", connectionId, remoteIp);
            return false;
        }

        // 注册连接
        Connection existing = connections.put(connectionId, connection);
        if (existing != null) {
            logger.warn("连接ID重复，覆盖现有连接: {}", connectionId);
        }

        // 更新IP连接计数
        if (remoteIp != null) {
            ipConnectionCounts.computeIfAbsent(remoteIp, k -> new AtomicInteger(0))
                             .incrementAndGet();
        }

        // 更新统计信息
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();

        // 添加连接监听器
        connection.addConnectionListener(new ConnectionEventListener(connectionId, remoteIp));

        logger.debug("连接注册成功: {}, IP: {}, 当前活跃连接数: {}", 
                    connectionId, remoteIp, activeConnections.get());
        return true;
    }

    /**
     * 注销连接
     *
     * @param connectionId 连接ID
     * @return 被注销的连接，如果不存在则返回null
     */
    public Connection unregisterConnection(String connectionId) {
        if (connectionId == null) {
            return null;
        }

        Connection connection = connections.remove(connectionId);
        if (connection != null) {
            String remoteIp = getRemoteIp(connection);

            // 更新IP连接计数
            if (remoteIp != null) {
                AtomicInteger count = ipConnectionCounts.get(remoteIp);
                if (count != null) {
                    int newCount = count.decrementAndGet();
                    if (newCount <= 0) {
                        ipConnectionCounts.remove(remoteIp);
                    }
                }
            }

            // 从所有分组中移除
            removeFromAllGroups(connectionId);

            // 更新统计信息
            activeConnections.decrementAndGet();

            logger.debug("连接注销成功: {}, IP: {}, 当前活跃连接数: {}", 
                        connectionId, remoteIp, activeConnections.get());
        }

        return connection;
    }

    /**
     * 根据ID查找连接
     *
     * @param connectionId 连接ID
     * @return 连接对象，如果不存在则返回null
     */
    public Connection getConnection(String connectionId) {
        return connectionId != null ? connections.get(connectionId) : null;
    }

    /**
     * 获取所有连接
     *
     * @return 连接列表的只读视图
     */
    public List<Connection> getAllConnections() {
        return Collections.unmodifiableList(new ArrayList<>(connections.values()));
    }

    /**
     * 根据条件查找连接
     *
     * @param predicate 过滤条件
     * @return 符合条件的连接列表
     */
    public List<Connection> findConnections(Predicate<Connection> predicate) {
        if (predicate == null) {
            return getAllConnections();
        }

        return connections.values().stream()
                         .filter(predicate)
                         .collect(Collectors.toList());
    }

    /**
     * 根据IP查找连接
     *
     * @param ip IP地址
     * @return 该IP的所有连接
     */
    public List<Connection> getConnectionsByIp(String ip) {
        if (ip == null) {
            return Collections.emptyList();
        }

        return findConnections(conn -> ip.equals(getRemoteIp(conn)));
    }

    /**
     * 根据属性查找连接
     *
     * @param attributeKey   属性键
     * @param attributeValue 属性值
     * @return 具有指定属性的连接列表
     */
    public List<Connection> getConnectionsByAttribute(String attributeKey, Object attributeValue) {
        if (attributeKey == null) {
            return Collections.emptyList();
        }

        return findConnections(conn -> 
            Objects.equals(attributeValue, conn.getAttribute(attributeKey)));
    }

    /**
     * 将连接添加到分组
     *
     * @param groupName    分组名称
     * @param connectionId 连接ID
     * @return true表示添加成功
     */
    public boolean addToGroup(String groupName, String connectionId) {
        if (groupName == null || connectionId == null) {
            return false;
        }

        if (!connections.containsKey(connectionId)) {
            logger.warn("连接不存在，无法添加到分组: {}", connectionId);
            return false;
        }

        connectionGroups.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet())
                       .add(connectionId);

        logger.debug("连接添加到分组: {} -> {}", connectionId, groupName);
        return true;
    }

    /**
     * 从分组中移除连接
     *
     * @param groupName    分组名称
     * @param connectionId 连接ID
     * @return true表示移除成功
     */
    public boolean removeFromGroup(String groupName, String connectionId) {
        if (groupName == null || connectionId == null) {
            return false;
        }

        Set<String> group = connectionGroups.get(groupName);
        if (group != null) {
            boolean removed = group.remove(connectionId);
            if (group.isEmpty()) {
                connectionGroups.remove(groupName);
            }
            
            if (removed) {
                logger.debug("连接从分组移除: {} <- {}", connectionId, groupName);
            }
            return removed;
        }

        return false;
    }

    /**
     * 获取分组中的所有连接
     *
     * @param groupName 分组名称
     * @return 分组中的连接列表
     */
    public List<Connection> getGroupConnections(String groupName) {
        if (groupName == null) {
            return Collections.emptyList();
        }

        Set<String> connectionIds = connectionGroups.get(groupName);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return connectionIds.stream()
                           .map(connections::get)
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList());
    }

    /**
     * 广播消息给所有连接
     *
     * @param message 要广播的消息
     * @return 发送成功的连接数
     */
    public int broadcast(Object message) {
        if (message == null) {
            return 0;
        }

        AtomicInteger successCount = new AtomicInteger(0);
        
        connections.values().parallelStream()
                  .filter(Connection::isActive)
                  .forEach(connection -> {
                      try {
                          connection.send(message).thenRun(() -> successCount.incrementAndGet());
                      } catch (Exception e) {
                          logger.warn("广播消息失败: {}", connection.getId(), e);
                      }
                  });

        logger.debug("广播消息完成，成功发送: {} / {}", successCount.get(), activeConnections.get());
        return successCount.get();
    }

    /**
     * 向分组广播消息
     *
     * @param groupName 分组名称
     * @param message   要广播的消息
     * @return 发送成功的连接数
     */
    public int broadcastToGroup(String groupName, Object message) {
        if (groupName == null || message == null) {
            return 0;
        }

        List<Connection> groupConnections = getGroupConnections(groupName);
        AtomicInteger successCount = new AtomicInteger(0);

        groupConnections.parallelStream()
                       .filter(Connection::isActive)
                       .forEach(connection -> {
                           try {
                               connection.send(message).thenRun(() -> successCount.incrementAndGet());
                           } catch (Exception e) {
                               logger.warn("分组广播消息失败: {}", connection.getId(), e);
                           }
                       });

        logger.debug("分组广播消息完成，分组: {}, 成功发送: {} / {}", 
                    groupName, successCount.get(), groupConnections.size());
        return successCount.get();
    }

    /**
     * 关闭所有连接
     */
    public void closeAllConnections() {
        logger.info("开始关闭所有连接，当前连接数: {}", activeConnections.get());

        connections.values().parallelStream()
                  .forEach(connection -> {
                      try {
                          connection.close();
                      } catch (Exception e) {
                          logger.warn("关闭连接失败: {}", connection.getId(), e);
                      }
                  });

        // 清空所有数据结构
        connections.clear();
        ipConnectionCounts.clear();
        connectionGroups.clear();
        activeConnections.set(0);

        logger.info("所有连接关闭完成");
    }

    /**
     * 获取当前活跃连接数
     *
     * @return 活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnections.intValue();
    }

    /**
     * 获取总连接数（包括历史连接）
     *
     * @return 总连接数
     */
    public long getTotalConnectionCount() {
        return totalConnections.get();
    }

    /**
     * 获取被拒绝的连接数
     *
     * @return 被拒绝的连接数
     */
    public long getRejectedConnectionCount() {
        return rejectedConnections.get();
    }

    /**
     * 获取IP连接数统计
     *
     * @return IP连接数映射的只读视图
     */
    public Map<String, Integer> getIpConnectionCounts() {
        return ipConnectionCounts.entrySet().stream()
                                .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().get()
                                ));
    }

    /**
     * 获取分组信息
     *
     * @return 分组信息映射的只读视图
     */
    public Map<String, Integer> getGroupInfo() {
        return connectionGroups.entrySet().stream()
                              .collect(Collectors.toMap(
                                  Map.Entry::getKey,
                                  entry -> entry.getValue().size()
                              ));
    }

    /**
     * 检查连接限制
     */
    private boolean checkConnectionLimits(String remoteIp) {
        // 检查总连接数限制
        if (activeConnections.get() >= config.getMaxConnections()) {
            logger.warn("达到最大连接数限制: {}", config.getMaxConnections());
            return false;
        }

        // 检查单IP连接数限制
        if (remoteIp != null && config.getMaxConnectionsPerIp() > 0) {
            AtomicInteger ipCount = ipConnectionCounts.get(remoteIp);
            if (ipCount != null && ipCount.get() >= config.getMaxConnectionsPerIp()) {
                logger.warn("IP连接数超限: {}, 当前: {}, 限制: {}", 
                           remoteIp, ipCount.get(), config.getMaxConnectionsPerIp());
                return false;
            }
        }

        return true;
    }

    /**
     * 获取远程IP地址
     */
    private String getRemoteIp(Connection connection) {
        SocketAddress remoteAddress = connection.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getAddress().getHostAddress();
        }
        return null;
    }

    /**
     * 从所有分组中移除连接
     */
    private void removeFromAllGroups(String connectionId) {
        connectionGroups.values().forEach(group -> group.remove(connectionId));
        connectionGroups.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 连接事件监听器
     */
    private class ConnectionEventListener implements Connection.ConnectionListener {
        private final String connectionId;
        private final String remoteIp;

        public ConnectionEventListener(String connectionId, String remoteIp) {
            this.connectionId = connectionId;
            this.remoteIp = remoteIp;
        }

        @Override
        public void onDisconnected(Connection connection) {
            // 自动注销连接
            unregisterConnection(connectionId);
        }

        @Override
        public void onException(Connection connection, Throwable cause) {
            logger.debug("连接异常: {}, IP: {}", connectionId, remoteIp, cause);
        }
    }

    /**
     * 连接管理器配置接口
     */
    public interface ConnectionManagerConfig {
        
        /**
         * 获取最大连接数
         *
         * @return 最大连接数
         */
        int getMaxConnections();

        /**
         * 获取单IP最大连接数
         *
         * @return 单IP最大连接数，0表示不限制
         */
        int getMaxConnectionsPerIp();

        /**
         * 获取连接清理间隔
         *
         * @return 清理间隔（毫秒）
         */
        long getCleanupInterval();

        /**
         * 是否启用统计信息
         *
         * @return true表示启用
         */
        boolean isStatisticsEnabled();
    }

    /**
     * 默认连接管理器配置
     */
    public static class DefaultConnectionManagerConfig implements ConnectionManagerConfig {
        private final int maxConnections;
        private final int maxConnectionsPerIp;
        private final long cleanupInterval;
        private final boolean statisticsEnabled;

        public DefaultConnectionManagerConfig(int maxConnections, int maxConnectionsPerIp) {
            this(maxConnections, maxConnectionsPerIp, 60000, true);
        }

        public DefaultConnectionManagerConfig(int maxConnections, int maxConnectionsPerIp, 
                                            long cleanupInterval, boolean statisticsEnabled) {
            this.maxConnections = maxConnections;
            this.maxConnectionsPerIp = maxConnectionsPerIp;
            this.cleanupInterval = cleanupInterval;
            this.statisticsEnabled = statisticsEnabled;
        }

        @Override
        public int getMaxConnections() {
            return maxConnections;
        }

        @Override
        public int getMaxConnectionsPerIp() {
            return maxConnectionsPerIp;
        }

        @Override
        public long getCleanupInterval() {
            return cleanupInterval;
        }

        @Override
        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }
    }
}