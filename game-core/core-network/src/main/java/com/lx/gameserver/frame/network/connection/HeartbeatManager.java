/*
 * 文件名: HeartbeatManager.java
 * 用途: 心跳管理器
 * 实现内容:
 *   - 心跳消息发送和检测
 *   - 连接存活状态监控
 *   - 超时连接自动断开
 *   - 自适应心跳间隔调整
 *   - 心跳统计和监控
 * 技术选型:
 *   - 定时任务调度
 *   - 并发安全的连接管理
 *   - 可配置的心跳策略
 * 依赖关系:
 *   - 被ConnectionManager使用
 *   - 与Connection接口协作
 *   - 使用定时器执行心跳任务
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.connection;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 心跳管理器
 * <p>
 * 负责管理连接的心跳检测，包括定期发送心跳消息、检测连接超时、
 * 自动断开死连接等功能。支持自适应心跳间隔调整。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class HeartbeatManager {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatManager.class);

    // 心跳配置
    private final long heartbeatInterval;  // 心跳间隔（毫秒）
    private final long heartbeatTimeout;   // 心跳超时（毫秒）
    private final boolean autoAdaptive;    // 是否自适应调整
    
    // 连接心跳信息
    private final Map<String, HeartbeatInfo> connectionHeartbeats = new ConcurrentHashMap<>();
    
    // 定时器
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> timeoutCheckTask;
    
    // 统计信息
    private final AtomicLong totalHeartbeatsSent = new AtomicLong(0);
    private final AtomicLong totalHeartbeatsReceived = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);
    
    // 状态
    private volatile boolean started = false;

    /**
     * 心跳信息
     */
    private static class HeartbeatInfo {
        private final Connection connection;
        private volatile long lastSentTime;
        private volatile long lastReceivedTime;
        private volatile long currentInterval;
        private volatile int missedCount;
        
        public HeartbeatInfo(Connection connection, long interval) {
            this.connection = connection;
            this.currentInterval = interval;
            this.lastSentTime = System.currentTimeMillis();
            this.lastReceivedTime = System.currentTimeMillis();
            this.missedCount = 0;
        }
        
        public Connection getConnection() { return connection; }
        public long getLastSentTime() { return lastSentTime; }
        public void setLastSentTime(long time) { this.lastSentTime = time; }
        public long getLastReceivedTime() { return lastReceivedTime; }
        public void setLastReceivedTime(long time) { this.lastReceivedTime = time; }
        public long getCurrentInterval() { return currentInterval; }
        public void setCurrentInterval(long interval) { this.currentInterval = interval; }
        public int getMissedCount() { return missedCount; }
        public void incrementMissedCount() { this.missedCount++; }
        public void resetMissedCount() { this.missedCount = 0; }
    }

    /**
     * 心跳消息类型
     */
    public static class HeartbeatMessage {
        public static final String TYPE_PING = "PING";
        public static final String TYPE_PONG = "PONG";
        
        private final String type;
        private final long timestamp;
        private final String connectionId;
        
        public HeartbeatMessage(String type, long timestamp, String connectionId) {
            this.type = type;
            this.timestamp = timestamp;
            this.connectionId = connectionId;
        }
        
        public String getType() { return type; }
        public long getTimestamp() { return timestamp; }
        public String getConnectionId() { return connectionId; }
        
        @Override
        public String toString() {
            return String.format("HeartbeatMessage{type='%s', timestamp=%d, connectionId='%s'}", 
                               type, timestamp, connectionId);
        }
    }

    /**
     * 构造函数
     *
     * @param heartbeatInterval 心跳间隔（毫秒）
     * @param heartbeatTimeout  心跳超时（毫秒）
     * @param autoAdaptive      是否自适应调整
     */
    public HeartbeatManager(long heartbeatInterval, long heartbeatTimeout, boolean autoAdaptive) {
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatTimeout = heartbeatTimeout;
        this.autoAdaptive = autoAdaptive;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "HeartbeatManager-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        logger.info("心跳管理器创建，间隔: {}ms, 超时: {}ms, 自适应: {}", 
                   heartbeatInterval, heartbeatTimeout, autoAdaptive);
    }

    /**
     * 启动心跳管理器
     */
    public void start() {
        if (started) {
            return;
        }
        
        started = true;
        
        // 启动心跳发送任务
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeats, 
            heartbeatInterval, 
            heartbeatInterval, 
            TimeUnit.MILLISECONDS
        );
        
        // 启动超时检查任务
        timeoutCheckTask = scheduler.scheduleAtFixedRate(
            this::checkTimeouts, 
            heartbeatTimeout, 
            heartbeatTimeout / 2, 
            TimeUnit.MILLISECONDS
        );
        
        logger.info("心跳管理器启动成功");
    }

    /**
     * 停止心跳管理器
     */
    public void stop() {
        if (!started) {
            return;
        }
        
        started = false;
        
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        
        if (timeoutCheckTask != null) {
            timeoutCheckTask.cancel(false);
        }
        
        scheduler.shutdown();
        connectionHeartbeats.clear();
        
        logger.info("心跳管理器已停止");
    }

    /**
     * 添加连接心跳监控
     */
    public void addConnection(Connection connection) {
        if (!started || connection == null) {
            return;
        }
        
        HeartbeatInfo info = new HeartbeatInfo(connection, heartbeatInterval);
        connectionHeartbeats.put(connection.getId(), info);
        
        logger.debug("添加连接心跳监控: {}", connection.getId());
    }

    /**
     * 移除连接心跳监控
     */
    public void removeConnection(String connectionId) {
        if (connectionId == null) {
            return;
        }
        
        HeartbeatInfo removed = connectionHeartbeats.remove(connectionId);
        if (removed != null) {
            logger.debug("移除连接心跳监控: {}", connectionId);
        }
    }

    /**
     * 处理心跳响应
     */
    public void handleHeartbeatResponse(String connectionId, HeartbeatMessage message) {
        HeartbeatInfo info = connectionHeartbeats.get(connectionId);
        if (info == null) {
            return;
        }
        
        info.setLastReceivedTime(System.currentTimeMillis());
        info.resetMissedCount();
        totalHeartbeatsReceived.incrementAndGet();
        
        // 自适应调整心跳间隔
        if (autoAdaptive && HeartbeatMessage.TYPE_PONG.equals(message.getType())) {
            adjustHeartbeatInterval(info, message);
        }
        
        logger.debug("收到心跳响应: {}, RTT: {}ms", 
                    connectionId, System.currentTimeMillis() - message.getTimestamp());
    }

    /**
     * 发送心跳消息
     */
    private void sendHeartbeats() {
        long currentTime = System.currentTimeMillis();
        
        for (HeartbeatInfo info : connectionHeartbeats.values()) {
            try {
                if (currentTime - info.getLastSentTime() >= info.getCurrentInterval()) {
                    sendHeartbeat(info, currentTime);
                }
            } catch (Exception e) {
                logger.warn("发送心跳失败: {}", info.getConnection().getId(), e);
            }
        }
    }

    /**
     * 发送单个心跳
     */
    private void sendHeartbeat(HeartbeatInfo info, long currentTime) {
        Connection connection = info.getConnection();
        if (!connection.isActive()) {
            removeConnection(connection.getId());
            return;
        }
        
        HeartbeatMessage heartbeat = new HeartbeatMessage(
            HeartbeatMessage.TYPE_PING, 
            currentTime, 
            connection.getId()
        );
        
        connection.send(heartbeat).whenComplete((result, throwable) -> {
            if (throwable == null) {
                info.setLastSentTime(currentTime);
                totalHeartbeatsSent.incrementAndGet();
                logger.debug("发送心跳成功: {}", connection.getId());
            } else {
                logger.warn("发送心跳失败: {}", connection.getId(), throwable);
                info.incrementMissedCount();
            }
        });
    }

    /**
     * 检查超时连接
     */
    private void checkTimeouts() {
        long currentTime = System.currentTimeMillis();
        
        connectionHeartbeats.values().removeIf(info -> {
            long timeSinceLastReceived = currentTime - info.getLastReceivedTime();
            
            if (timeSinceLastReceived > heartbeatTimeout) {
                logger.warn("连接心跳超时，断开连接: {}, 超时时间: {}ms", 
                           info.getConnection().getId(), timeSinceLastReceived);
                
                // 断开超时连接
                info.getConnection().close();
                totalTimeouts.incrementAndGet();
                return true;
            }
            
            return false;
        });
    }

    /**
     * 自适应调整心跳间隔
     */
    private void adjustHeartbeatInterval(HeartbeatInfo info, HeartbeatMessage message) {
        long rtt = System.currentTimeMillis() - message.getTimestamp();
        long currentInterval = info.getCurrentInterval();
        
        // 根据RTT调整心跳间隔
        if (rtt < 100) {
            // RTT很小，可以增加心跳间隔
            long newInterval = Math.min(currentInterval * 11 / 10, heartbeatInterval * 2);
            info.setCurrentInterval(newInterval);
        } else if (rtt > 1000) {
            // RTT很大，减少心跳间隔
            long newInterval = Math.max(currentInterval * 9 / 10, heartbeatInterval / 2);
            info.setCurrentInterval(newInterval);
        }
        
        logger.debug("自适应调整心跳间隔: {}, RTT: {}ms, 新间隔: {}ms", 
                    info.getConnection().getId(), rtt, info.getCurrentInterval());
    }

    /**
     * 获取统计信息
     */
    public HeartbeatStatistics getStatistics() {
        return new HeartbeatStatistics(
            connectionHeartbeats.size(),
            totalHeartbeatsSent.get(),
            totalHeartbeatsReceived.get(),
            totalTimeouts.get(),
            heartbeatInterval,
            heartbeatTimeout
        );
    }

    /**
     * 心跳统计信息
     */
    public static class HeartbeatStatistics {
        private final int activeConnections;
        private final long totalSent;
        private final long totalReceived;
        private final long totalTimeouts;
        private final long interval;
        private final long timeout;
        
        public HeartbeatStatistics(int activeConnections, long totalSent, long totalReceived, 
                                 long totalTimeouts, long interval, long timeout) {
            this.activeConnections = activeConnections;
            this.totalSent = totalSent;
            this.totalReceived = totalReceived;
            this.totalTimeouts = totalTimeouts;
            this.interval = interval;
            this.timeout = timeout;
        }
        
        public int getActiveConnections() { return activeConnections; }
        public long getTotalSent() { return totalSent; }
        public long getTotalReceived() { return totalReceived; }
        public long getTotalTimeouts() { return totalTimeouts; }
        public long getInterval() { return interval; }
        public long getTimeout() { return timeout; }
        
        @Override
        public String toString() {
            return String.format("HeartbeatStats{connections=%d, sent=%d, received=%d, timeouts=%d, interval=%dms, timeout=%dms}",
                               activeConnections, totalSent, totalReceived, totalTimeouts, interval, timeout);
        }
    }

    /**
     * 是否已启动
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 获取监控的连接数
     */
    public int getConnectionCount() {
        return connectionHeartbeats.size();
    }
}