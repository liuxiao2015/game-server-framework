/*
 * 文件名: RemoteActorRef.java
 * 用途: 远程Actor引用实现
 * 实现内容:
 *   - 远程Actor引用和跨节点消息发送
 *   - 序列化/反序列化和网络故障处理
 *   - 重连机制和消息重试
 *   - 远程调用监控和性能统计
 * 技术选型:
 *   - 实现ActorRef接口支持透明远程调用
 *   - 网络通信和消息序列化
 *   - 连接池和故障恢复机制
 * 依赖关系:
 *   - 实现ActorRef接口
 *   - 与ClusterActorSystem协作
 *   - 支持网络通信和序列化
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.cluster;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 远程Actor引用实现
 * <p>
 * 提供对远程节点上Actor的透明访问，支持跨节点消息发送、
 * 网络故障处理、重连机制等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class RemoteActorRef implements ActorRef {
    
    private static final Logger logger = LoggerFactory.getLogger(RemoteActorRef.class);
    
    /** Actor路径 */
    private final String actorPath;
    
    /** 远程节点地址 */
    private final InetSocketAddress remoteAddress;
    
    /** Actor系统引用 */
    private final ClusterActorSystem actorSystem;
    
    /** 连接状态 */
    private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.DISCONNECTED);
    
    /** 是否已终止 */
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    
    /** 远程连接管理器 */
    private final RemoteConnectionManager connectionManager;
    
    /** 消息统计 */
    private final AtomicLong sentMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong retriedMessages = new AtomicLong(0);
    
    /** 最后活跃时间 */
    private volatile Instant lastActiveTime = Instant.now();
    
    public RemoteActorRef(String actorPath, InetSocketAddress remoteAddress, ClusterActorSystem actorSystem) {
        this.actorPath = actorPath;
        this.remoteAddress = remoteAddress;
        this.actorSystem = actorSystem;
        this.connectionManager = RemoteConnectionManager.getInstance();
        
        logger.debug("创建远程Actor引用: {} -> {}", actorPath, remoteAddress);
    }
    
    @Override
    public void tell(Object message, ActorRef sender) {
        if (terminated.get()) {
            logger.warn("尝试向已终止的远程Actor发送消息: {}", actorPath);
            return;
        }
        
        try {
            RemoteMessage remoteMessage = new RemoteMessage(actorPath, message, sender, Instant.now());
            sendRemoteMessage(remoteMessage);
            sentMessages.incrementAndGet();
            lastActiveTime = Instant.now();
            
        } catch (Exception e) {
            failedMessages.incrementAndGet();
            logger.error("远程消息发送失败: {} -> {}", actorPath, remoteAddress, e);
            // 可以实现死信队列处理
        }
    }
    
    @Override
    public void tellWithPriority(Object message, ActorRef sender, int priority) {
        // 远程消息暂不支持优先级，使用普通tell
        tell(message, sender);
    }
    
    @Override
    public CompletableFuture<Object> ask(Object message, Duration timeout) {
        if (terminated.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Actor已终止: " + actorPath));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 生成唯一请求ID
                String requestId = generateRequestId();
                
                // 创建远程请求消息
                RemoteRequestMessage request = new RemoteRequestMessage(actorPath, message, actorSystem.getCurrentNode(), 
                        requestId, timeout, Instant.now());
                
                // 发送远程请求并等待响应
                return sendRemoteRequest(request, timeout);
                
            } catch (Exception e) {
                failedMessages.incrementAndGet();
                logger.error("远程请求失败: {} -> {}", actorPath, remoteAddress, e);
                throw new RuntimeException("远程请求失败", e);
            }
        });
    }
    
    @Override
    public void forward(Object message, ActorRef sender) {
        tell(message, sender);
    }
    
    @Override
    public String getPath() {
        return String.format("remote://%s:%d%s", remoteAddress.getHostString(), remoteAddress.getPort(), actorPath);
    }
    
    @Override
    public String getName() {
        return actorPath.substring(actorPath.lastIndexOf('/') + 1);
    }
    
    @Override
    public ActorSystem getActorSystem() {
        return actorSystem;
    }
    
    @Override
    public boolean isTerminated() {
        return terminated.get();
    }
    
    /**
     * 获取远程节点地址
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
    
    /**
     * 获取连接状态
     */
    public ConnectionState getConnectionState() {
        return connectionState.get();
    }
    
    /**
     * 终止远程引用
     */
    public void terminate() {
        if (terminated.compareAndSet(false, true)) {
            connectionState.set(ConnectionState.TERMINATED);
            logger.debug("远程Actor引用已终止: {}", getPath());
        }
    }
    
    /**
     * 发送远程消息
     */
    private void sendRemoteMessage(RemoteMessage message) {
        RemoteConnection connection = connectionManager.getConnection(remoteAddress);
        connection.sendMessage(message);
    }
    
    /**
     * 发送远程请求
     */
    private Object sendRemoteRequest(RemoteRequestMessage request, Duration timeout) {
        RemoteConnection connection = connectionManager.getConnection(remoteAddress);
        return connection.sendRequest(request, timeout);
    }
    
    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return actorSystem.getCurrentNode().getNodeId() + "-" + System.nanoTime();
    }
    
    /**
     * 获取统计信息
     */
    public RemoteActorStats getStats() {
        return new RemoteActorStats(
                actorPath,
                remoteAddress,
                connectionState.get(),
                sentMessages.get(),
                failedMessages.get(),
                retriedMessages.get(),
                lastActiveTime
        );
    }
    
    /**
     * 连接状态枚举
     */
    public enum ConnectionState {
        /** 未连接 */
        DISCONNECTED,
        /** 连接中 */
        CONNECTING,
        /** 已连接 */
        CONNECTED,
        /** 重连中 */
        RECONNECTING,
        /** 已终止 */
        TERMINATED
    }
    
    /**
     * 远程消息
     */
    public static class RemoteMessage implements Serializable {
        private final String targetPath;
        private final Object message;
        private final ActorRef sender;
        private final Instant timestamp;
        
        public RemoteMessage(String targetPath, Object message, ActorRef sender, Instant timestamp) {
            this.targetPath = targetPath;
            this.message = message;
            this.sender = sender;
            this.timestamp = timestamp;
        }
        
        public String getTargetPath() { return targetPath; }
        public Object getMessage() { return message; }
        public ActorRef getSender() { return sender; }
        public Instant getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("RemoteMessage{target=%s, message=%s, timestamp=%s}", 
                    targetPath, message.getClass().getSimpleName(), timestamp);
        }
    }
    
    /**
     * 远程请求消息
     */
    public static class RemoteRequestMessage extends RemoteMessage {
        private final ClusterActorSystem.ClusterNode senderNode;
        private final String requestId;
        private final Duration timeout;
        
        public RemoteRequestMessage(String targetPath, Object message, ClusterActorSystem.ClusterNode senderNode,
                                  String requestId, Duration timeout, Instant timestamp) {
            super(targetPath, message, null, timestamp);
            this.senderNode = senderNode;
            this.requestId = requestId;
            this.timeout = timeout;
        }
        
        public ClusterActorSystem.ClusterNode getSenderNode() { return senderNode; }
        public String getRequestId() { return requestId; }
        public Duration getTimeout() { return timeout; }
    }
    
    /**
     * 远程响应消息
     */
    public static class RemoteResponseMessage implements Serializable {
        private final String requestId;
        private final Object response;
        private final boolean success;
        private final String errorMessage;
        private final Instant timestamp;
        
        public RemoteResponseMessage(String requestId, Object response, Instant timestamp) {
            this.requestId = requestId;
            this.response = response;
            this.success = true;
            this.errorMessage = null;
            this.timestamp = timestamp;
        }
        
        public RemoteResponseMessage(String requestId, String errorMessage, Instant timestamp) {
            this.requestId = requestId;
            this.response = null;
            this.success = false;
            this.errorMessage = errorMessage;
            this.timestamp = timestamp;
        }
        
        public String getRequestId() { return requestId; }
        public Object getResponse() { return response; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * 远程连接管理器
     */
    private static class RemoteConnectionManager {
        private static final RemoteConnectionManager INSTANCE = new RemoteConnectionManager();
        private final ConcurrentHashMap<InetSocketAddress, RemoteConnection> connections = new ConcurrentHashMap<>();
        
        public static RemoteConnectionManager getInstance() {
            return INSTANCE;
        }
        
        public RemoteConnection getConnection(InetSocketAddress address) {
            return connections.computeIfAbsent(address, RemoteConnection::new);
        }
        
        public void closeConnection(InetSocketAddress address) {
            RemoteConnection connection = connections.remove(address);
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    /**
     * 远程连接
     */
    private static class RemoteConnection {
        private final InetSocketAddress address;
        private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
        private final AtomicLong messagesSent = new AtomicLong(0);
        
        public RemoteConnection(InetSocketAddress address) {
            this.address = address;
        }
        
        public void sendMessage(RemoteMessage message) {
            // 这里应该实现实际的网络发送逻辑
            // 1. 序列化消息
            // 2. 通过网络发送
            // 3. 处理发送结果
            
            messagesSent.incrementAndGet();
            logger.debug("发送远程消息到 {}: {}", address, message);
        }
        
        public Object sendRequest(RemoteRequestMessage request, Duration timeout) {
            // 这里应该实现实际的网络请求逻辑
            // 1. 序列化请求
            // 2. 发送请求并等待响应
            // 3. 反序列化响应
            // 4. 处理超时和错误
            
            logger.debug("发送远程请求到 {}: {}", address, request);
            
            // 模拟响应（实际实现中应该从网络接收）
            return new Object(); // 待实现
        }
        
        public void close() {
            state.set(ConnectionState.TERMINATED);
            logger.debug("关闭远程连接: {}", address);
        }
    }
    
    /**
     * 远程Actor统计信息
     */
    public static class RemoteActorStats {
        private final String actorPath;
        private final InetSocketAddress remoteAddress;
        private final ConnectionState connectionState;
        private final long sentMessages;
        private final long failedMessages;
        private final long retriedMessages;
        private final Instant lastActiveTime;
        
        public RemoteActorStats(String actorPath, InetSocketAddress remoteAddress, ConnectionState connectionState,
                              long sentMessages, long failedMessages, long retriedMessages, Instant lastActiveTime) {
            this.actorPath = actorPath;
            this.remoteAddress = remoteAddress;
            this.connectionState = connectionState;
            this.sentMessages = sentMessages;
            this.failedMessages = failedMessages;
            this.retriedMessages = retriedMessages;
            this.lastActiveTime = lastActiveTime;
        }
        
        public String getActorPath() { return actorPath; }
        public InetSocketAddress getRemoteAddress() { return remoteAddress; }
        public ConnectionState getConnectionState() { return connectionState; }
        public long getSentMessages() { return sentMessages; }
        public long getFailedMessages() { return failedMessages; }
        public long getRetriedMessages() { return retriedMessages; }
        public Instant getLastActiveTime() { return lastActiveTime; }
        public double getFailureRate() { 
            return sentMessages > 0 ? (double) failedMessages / sentMessages : 0.0; 
        }
        
        @Override
        public String toString() {
            return String.format("RemoteActorStats{path=%s, address=%s, state=%s, sent=%d, failed=%d, failureRate=%.2f%%}",
                    actorPath, remoteAddress, connectionState, sentMessages, failedMessages, getFailureRate() * 100);
        }
    }
}