/*
 * 文件名: WebSocketHandler.java
 * 用途: WebSocket连接管理
 * 实现内容:
 *   - WebSocket连接建立和断开处理
 *   - 心跳保活机制
 *   - 消息路由转发
 *   - 连接状态管理
 *   - 断线重连支持
 * 技术选型:
 *   - Spring WebFlux WebSocket
 *   - Reactor响应式编程
 *   - 连接池管理
 * 依赖关系:
 *   - 与ProtocolConverter集成
 *   - 与Gateway路由协作
 *   - 集成认证鉴权
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.gateway.protocol;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket连接处理器
 * <p>
 * 处理WebSocket连接的生命周期管理，包括连接建立、消息处理、
 * 心跳保活、断线重连等功能。提供高性能的WebSocket通信能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {

    private final GameProtocolHandler protocolHandler;
    private final Map<String, WebSocketSessionInfo> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sessionCounter = new AtomicLong(0);
    
    // 心跳配置
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(5);
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = generateSessionId();
        WebSocketSessionInfo sessionInfo = createSessionInfo(session, sessionId);
        
        // 注册会话
        sessions.put(sessionId, sessionInfo);
        
        log.info("WebSocket连接建立: sessionId={}, remoteAddress={}", 
            sessionId, session.getHandshakeInfo().getRemoteAddress());
        
        // 处理接收消息的流
        Flux<WebSocketMessage> receiveFlux = session.receive()
            .doOnNext(message -> handleIncomingMessage(sessionInfo, message))
            .doOnError(error -> handleSessionError(sessionInfo, error))
            .doFinally(signalType -> handleSessionClose(sessionInfo, signalType));
        
        // 处理发送消息的流
        Flux<WebSocketMessage> sendFlux = sessionInfo.getOutgoingSink().asFlux()
            .map(data -> session.textMessage(data))
            .mergeWith(createHeartbeatFlux(session));
        
        // 合并接收和发送流
        return session.send(sendFlux)
            .and(receiveFlux.then())
            .doOnTerminate(() -> cleanupSession(sessionId));
    }

    /**
     * 处理接收到的消息
     *
     * @param sessionInfo 会话信息
     * @param message WebSocket消息
     */
    private void handleIncomingMessage(WebSocketSessionInfo sessionInfo, WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            sessionInfo.updateLastActivity();
            
            log.debug("收到WebSocket消息: sessionId={}, payload={}", 
                sessionInfo.getSessionId(), payload);
            
            // 检查是否为心跳消息
            if (isHeartbeatMessage(payload)) {
                handleHeartbeat(sessionInfo);
                return;
            }
            
            // 协议转换和消息处理
            processGameMessage(sessionInfo, payload)
                .subscribe(
                    result -> log.debug("消息处理完成: {}", result),
                    error -> log.error("消息处理失败", error)
                );
                
        } catch (Exception e) {
            log.error("处理WebSocket消息异常: sessionId={}", sessionInfo.getSessionId(), e);
            sendErrorMessage(sessionInfo, "消息处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理游戏消息
     *
     * @param sessionInfo 会话信息
     * @param payload 消息载荷
     * @return 处理结果
     */
    private Mono<String> processGameMessage(WebSocketSessionInfo sessionInfo, String payload) {
        return Mono.fromCallable(() -> payload.getBytes())
            .flatMap(bytes -> {
                ConversionContextImpl context = new ConversionContextImpl();
                context.setSourceProtocol("WEBSOCKET");
                context.setTargetProtocol("GAME");
                context.setAttribute("session.id", sessionInfo.getSessionId());
                context.setAttribute("client.ip", sessionInfo.getClientIp());
                
                return protocolHandler.convert(bytes, context);
            })
            .map(result -> {
                // 这里可以进一步处理转换后的游戏消息
                // 例如路由到具体的游戏服务
                return "消息处理成功: " + result.toString();
            });
    }

    /**
     * 创建心跳消息流
     *
     * @param session WebSocket会话
     * @return 心跳消息流
     */
    private Flux<WebSocketMessage> createHeartbeatFlux(WebSocketSession session) {
        return Flux.interval(HEARTBEAT_INTERVAL)
            .map(tick -> session.textMessage("{\"type\":\"heartbeat\",\"timestamp\":" + 
                System.currentTimeMillis() + "}"));
    }

    /**
     * 检查是否为心跳消息
     *
     * @param payload 消息载荷
     * @return 是否为心跳消息
     */
    private boolean isHeartbeatMessage(String payload) {
        return payload != null && payload.contains("\"type\":\"heartbeat\"");
    }

    /**
     * 处理心跳消息
     *
     * @param sessionInfo 会话信息
     */
    private void handleHeartbeat(WebSocketSessionInfo sessionInfo) {
        sessionInfo.updateLastActivity();
        log.debug("收到心跳消息: sessionId={}", sessionInfo.getSessionId());
        
        // 回复心跳确认
        String heartbeatAck = "{\"type\":\"heartbeat_ack\",\"timestamp\":" + 
            System.currentTimeMillis() + "}";
        sendMessage(sessionInfo, heartbeatAck);
    }

    /**
     * 发送消息到客户端
     *
     * @param sessionInfo 会话信息
     * @param message 消息内容
     */
    public void sendMessage(WebSocketSessionInfo sessionInfo, String message) {
        try {
            sessionInfo.getOutgoingSink().tryEmitNext(message);
        } catch (Exception e) {
            log.error("发送WebSocket消息失败: sessionId={}", sessionInfo.getSessionId(), e);
        }
    }

    /**
     * 发送错误消息
     *
     * @param sessionInfo 会话信息
     * @param error 错误信息
     */
    private void sendErrorMessage(WebSocketSessionInfo sessionInfo, String error) {
        String errorMessage = "{\"type\":\"error\",\"message\":\"" + error + 
            "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        sendMessage(sessionInfo, errorMessage);
    }

    /**
     * 处理会话错误
     *
     * @param sessionInfo 会话信息
     * @param error 错误信息
     */
    private void handleSessionError(WebSocketSessionInfo sessionInfo, Throwable error) {
        log.error("WebSocket会话错误: sessionId={}", sessionInfo.getSessionId(), error);
        sessionInfo.setErrorCount(sessionInfo.getErrorCount() + 1);
        
        // 如果错误次数过多，关闭连接
        if (sessionInfo.getErrorCount() > 10) {
            log.warn("WebSocket会话错误次数过多，关闭连接: sessionId={}", sessionInfo.getSessionId());
            closeSession(sessionInfo.getSessionId());
        }
    }

    /**
     * 处理会话关闭
     *
     * @param sessionInfo 会话信息
     * @param signalType 信号类型
     */
    private void handleSessionClose(WebSocketSessionInfo sessionInfo, reactor.core.publisher.SignalType signalType) {
        log.info("WebSocket连接关闭: sessionId={}, signalType={}", 
            sessionInfo.getSessionId(), signalType);
        sessionInfo.setStatus(SessionStatus.CLOSED);
    }

    /**
     * 生成会话ID
     *
     * @return 会话ID
     */
    private String generateSessionId() {
        return "ws_" + System.currentTimeMillis() + "_" + sessionCounter.incrementAndGet();
    }

    /**
     * 创建会话信息
     *
     * @param session WebSocket会话
     * @param sessionId 会话ID
     * @return 会话信息
     */
    private WebSocketSessionInfo createSessionInfo(WebSocketSession session, String sessionId) {
        WebSocketSessionInfo sessionInfo = new WebSocketSessionInfo();
        sessionInfo.setSessionId(sessionId);
        sessionInfo.setSession(session);
        sessionInfo.setClientIp(getClientIp(session));
        sessionInfo.setUserAgent(getUserAgent(session));
        sessionInfo.setCreateTime(LocalDateTime.now());
        sessionInfo.setLastActivityTime(LocalDateTime.now());
        sessionInfo.setStatus(SessionStatus.CONNECTED);
        sessionInfo.setOutgoingSink(Sinks.many().unicast().onBackpressureBuffer());
        
        return sessionInfo;
    }

    /**
     * 获取客户端IP
     *
     * @param session WebSocket会话
     * @return 客户端IP
     */
    private String getClientIp(WebSocketSession session) {
        return session.getHandshakeInfo().getRemoteAddress() != null ?
            session.getHandshakeInfo().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * 获取用户代理
     *
     * @param session WebSocket会话
     * @return 用户代理
     */
    private String getUserAgent(WebSocketSession session) {
        return session.getHandshakeInfo().getHeaders().getFirst("User-Agent");
    }

    /**
     * 清理会话
     *
     * @param sessionId 会话ID
     */
    private void cleanupSession(String sessionId) {
        WebSocketSessionInfo sessionInfo = sessions.remove(sessionId);
        if (sessionInfo != null) {
            sessionInfo.getOutgoingSink().tryEmitComplete();
            log.info("WebSocket会话清理完成: sessionId={}", sessionId);
        }
    }

    /**
     * 关闭指定会话
     *
     * @param sessionId 会话ID
     */
    public void closeSession(String sessionId) {
        WebSocketSessionInfo sessionInfo = sessions.get(sessionId);
        if (sessionInfo != null && sessionInfo.getSession().isOpen()) {
            sessionInfo.getSession().close()
                .subscribe(
                    unused -> log.info("WebSocket会话已关闭: sessionId={}", sessionId),
                    error -> log.error("关闭WebSocket会话失败: sessionId={}", sessionId, error)
                );
        }
    }

    /**
     * 获取所有活跃会话
     *
     * @return 活跃会话映射
     */
    public Map<String, WebSocketSessionInfo> getActiveSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    /**
     * 获取会话统计信息
     *
     * @return 会话统计
     */
    public SessionStats getSessionStats() {
        SessionStats stats = new SessionStats();
        stats.setTotalSessions(sessions.size());
        stats.setConnectedSessions((int) sessions.values().stream()
            .filter(s -> s.getStatus() == SessionStatus.CONNECTED)
            .count());
        return stats;
    }

    /**
     * WebSocket会话信息
     */
    @Data
    public static class WebSocketSessionInfo {
        private String sessionId;
        private WebSocketSession session;
        private String clientIp;
        private String userAgent;
        private String userId;
        private LocalDateTime createTime;
        private LocalDateTime lastActivityTime;
        private SessionStatus status;
        private int errorCount;
        private Sinks.Many<String> outgoingSink;
        
        public void updateLastActivity() {
            this.lastActivityTime = LocalDateTime.now();
        }
    }

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        CONNECTING, CONNECTED, DISCONNECTING, CLOSED, ERROR
    }

    /**
     * 会话统计信息
     */
    @Data
    public static class SessionStats {
        private int totalSessions;
        private int connectedSessions;
        private int disconnectedSessions;
        private int errorSessions;
    }

    /**
     * 转换上下文实现
     */
    private static class ConversionContextImpl implements ProtocolConverter.ConversionContext {
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private String sourceProtocol;
        private String targetProtocol;

        @Override
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public Map<String, Object> getAttributes() {
            return new ConcurrentHashMap<>(attributes);
        }

        @Override
        public String getSourceProtocol() {
            return sourceProtocol;
        }

        public void setSourceProtocol(String sourceProtocol) {
            this.sourceProtocol = sourceProtocol;
        }

        @Override
        public String getTargetProtocol() {
            return targetProtocol;
        }

        public void setTargetProtocol(String targetProtocol) {
            this.targetProtocol = targetProtocol;
        }

        @Override
        public ProtocolConverter.ClientInfo getClientInfo() {
            return new ClientInfoImpl();
        }

        private class ClientInfoImpl implements ProtocolConverter.ClientInfo {
            @Override
            public String getClientIp() {
                return (String) getAttribute("client.ip");
            }

            @Override
            public String getUserAgent() {
                return (String) getAttribute("user.agent");
            }

            @Override
            public String getSessionId() {
                return (String) getAttribute("session.id");
            }

            @Override
            public String getUserId() {
                return (String) getAttribute("user.id");
            }
        }
    }
}