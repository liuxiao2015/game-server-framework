/*
 * 文件名: SessionManager.java
 * 用途: 会话管理器
 * 实现内容:
 *   - 会话创建和销毁管理
 *   - 会话查找和统计
 *   - 会话持久化支持
 *   - 分布式会话管理
 *   - 会话迁移和同步
 *   - 会话清理和过期处理
 * 技术选型:
 *   - ConcurrentHashMap高并发会话存储
 *   - 定时任务进行过期清理
 *   - 事件驱动的会话状态管理
 *   - 可插拔的SessionStore支持
 * 依赖关系:
 *   - 管理Session接口实例
 *   - 使用SessionStore进行持久化
 *   - 与Connection协作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.session;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 会话管理器
 * <p>
 * 提供会话的统一管理功能，包括会话创建、查找、持久化、
 * 清理等操作。支持分布式部署和会话迁移。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    /**
     * 会话存储 - 以会话ID为键
     */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * 用户会话映射 - 以用户ID为键
     */
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * 会话存储器
     */
    private volatile SessionStore sessionStore;

    /**
     * 会话监听器列表
     */
    private final List<Session.SessionListener> globalListeners = new CopyOnWriteArrayList<>();

    /**
     * 会话清理定时任务
     */
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * 配置参数
     */
    private final SessionManagerConfig config;

    /**
     * 统计信息
     */
    private final AtomicLong totalSessions = new AtomicLong(0);
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong expiredSessions = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param config 会话管理器配置
     */
    public SessionManager(SessionManagerConfig config) {
        this.config = Objects.requireNonNull(config, "会话管理器配置不能为null");
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "session-cleanup");
            thread.setDaemon(true);
            return thread;
        });

        // 启动定期清理任务
        if (config.getCleanupInterval() > 0) {
            cleanupExecutor.scheduleWithFixedDelay(
                this::cleanupExpiredSessions,
                config.getCleanupInterval(),
                config.getCleanupInterval(),
                TimeUnit.MILLISECONDS
            );
        }

        logger.info("初始化会话管理器，最大会话数: {}, 默认超时: {}ms, 清理间隔: {}ms",
                   config.getMaxSessions(), config.getDefaultTimeoutMillis(), config.getCleanupInterval());
    }

    /**
     * 设置会话存储器
     *
     * @param sessionStore 会话存储器
     */
    public void setSessionStore(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
        logger.info("设置会话存储器: {}", sessionStore != null ? sessionStore.getClass().getSimpleName() : "null");
    }

    /**
     * 创建新会话
     *
     * @param connection 关联的连接
     * @return 新创建的会话
     */
    public Session createSession(Connection connection) {
        return createSession(null, Session.SessionType.GUEST, connection);
    }

    /**
     * 创建新会话
     *
     * @param userId     用户ID
     * @param type       会话类型
     * @param connection 关联的连接
     * @return 新创建的会话
     */
    public Session createSession(String userId, Session.SessionType type, Connection connection) {
        // 检查会话数限制
        if (sessions.size() >= config.getMaxSessions()) {
            throw new IllegalStateException("会话数已达上限: " + config.getMaxSessions());
        }

        // 创建会话
        DefaultSession session = new DefaultSession(generateSessionId(), userId, type);
        session.setConnection(connection);
        session.setTimeoutMillis(config.getDefaultTimeoutMillis());

        // 添加全局监听器
        globalListeners.forEach(session::addSessionListener);

        // 注册会话
        sessions.put(session.getId(), session);

        // 更新用户会话映射
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                       .add(session.getId());
        }

        // 持久化会话
        if (sessionStore != null) {
            try {
                sessionStore.saveSession(session);
            } catch (Exception e) {
                logger.warn("会话持久化失败: {}", session.getId(), e);
            }
        }

        // 更新统计信息
        totalSessions.incrementAndGet();
        activeSessions.incrementAndGet();

        // 通知会话创建
        notifySessionCreated(session);

        logger.debug("创建会话: {}, 用户: {}, 类型: {}", session.getId(), userId, type);
        return session;
    }

    /**
     * 根据ID获取会话
     *
     * @param sessionId 会话ID
     * @return 会话对象，如果不存在则返回null
     */
    public Session getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        Session session = sessions.get(sessionId);
        if (session != null && session.isValid()) {
            session.updateLastAccessTime();
            return session;
        }

        // 尝试从存储器加载
        if (sessionStore != null && session == null) {
            try {
                session = sessionStore.loadSession(sessionId);
                if (session != null && session.isValid()) {
                    sessions.put(sessionId, session);
                    activeSessions.incrementAndGet();
                    return session;
                }
            } catch (Exception e) {
                logger.warn("从存储器加载会话失败: {}", sessionId, e);
            }
        }

        return null;
    }

    /**
     * 根据用户ID获取会话列表
     *
     * @param userId 用户ID
     * @return 用户的所有有效会话
     */
    public List<Session> getSessionsByUser(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        Set<String> sessionIds = userSessions.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sessionIds.stream()
                         .map(this::getSession)
                         .filter(Objects::nonNull)
                         .filter(Session::isValid)
                         .collect(Collectors.toList());
    }

    /**
     * 根据连接获取会话
     *
     * @param connection 连接对象
     * @return 会话对象，如果不存在则返回null
     */
    public Session getSessionByConnection(Connection connection) {
        if (connection == null) {
            return null;
        }

        return sessions.values().stream()
                      .filter(session -> connection.equals(session.getConnection()))
                      .filter(Session::isValid)
                      .findFirst()
                      .orElse(null);
    }

    /**
     * 根据条件查找会话
     *
     * @param predicate 过滤条件
     * @return 符合条件的会话列表
     */
    public List<Session> findSessions(Predicate<Session> predicate) {
        if (predicate == null) {
            return getAllSessions();
        }

        return sessions.values().stream()
                      .filter(predicate)
                      .filter(Session::isValid)
                      .collect(Collectors.toList());
    }

    /**
     * 获取所有有效会话
     *
     * @return 会话列表
     */
    public List<Session> getAllSessions() {
        return sessions.values().stream()
                      .filter(Session::isValid)
                      .collect(Collectors.toList());
    }

    /**
     * 销毁会话
     *
     * @param sessionId 会话ID
     * @return true表示销毁成功
     */
    public boolean destroySession(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        Session session = sessions.remove(sessionId);
        if (session != null) {
            // 从用户会话映射中移除
            String userId = session.getUserId();
            if (userId != null) {
                Set<String> userSessionSet = userSessions.get(userId);
                if (userSessionSet != null) {
                    userSessionSet.remove(sessionId);
                    if (userSessionSet.isEmpty()) {
                        userSessions.remove(userId);
                    }
                }
            }

            // 使会话无效
            session.invalidate();

            // 从存储器删除
            if (sessionStore != null) {
                try {
                    sessionStore.deleteSession(sessionId);
                } catch (Exception e) {
                    logger.warn("从存储器删除会话失败: {}", sessionId, e);
                }
            }

            // 更新统计信息
            activeSessions.decrementAndGet();

            // 通知会话销毁
            notifySessionInvalidated(session);

            logger.debug("销毁会话: {}", sessionId);
            return true;
        }

        return false;
    }

    /**
     * 销毁用户的所有会话
     *
     * @param userId 用户ID
     * @return 销毁的会话数
     */
    public int destroyUserSessions(String userId) {
        if (userId == null) {
            return 0;
        }

        List<Session> userSessionList = getSessionsByUser(userId);
        int destroyedCount = 0;

        for (Session session : userSessionList) {
            if (destroySession(session.getId())) {
                destroyedCount++;
            }
        }

        logger.debug("销毁用户会话: {}, 数量: {}", userId, destroyedCount);
        return destroyedCount;
    }

    /**
     * 清理过期会话
     *
     * @return 清理的会话数
     */
    public int cleanupExpiredSessions() {
        int cleanedCount = 0;
        List<String> expiredSessionIds = new ArrayList<>();

        // 查找过期会话
        for (Session session : sessions.values()) {
            if (session.isExpired() || !session.isValid()) {
                expiredSessionIds.add(session.getId());
            }
        }

        // 清理过期会话
        for (String sessionId : expiredSessionIds) {
            if (destroySession(sessionId)) {
                cleanedCount++;
                expiredSessions.incrementAndGet();
            }
        }

        if (cleanedCount > 0) {
            logger.debug("清理过期会话: {} 个", cleanedCount);
        }

        return cleanedCount;
    }

    /**
     * 添加全局会话监听器
     *
     * @param listener 会话监听器
     */
    public void addGlobalSessionListener(Session.SessionListener listener) {
        if (listener != null) {
            globalListeners.add(listener);
            
            // 为现有会话添加监听器
            sessions.values().forEach(session -> session.addSessionListener(listener));
        }
    }

    /**
     * 移除全局会话监听器
     *
     * @param listener 会话监听器
     */
    public void removeGlobalSessionListener(Session.SessionListener listener) {
        globalListeners.remove(listener);
        
        // 从现有会话移除监听器
        sessions.values().forEach(session -> session.removeSessionListener(listener));
    }

    /**
     * 获取当前活跃会话数
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return (int) activeSessions.get();
    }

    /**
     * 获取总会话数（包括历史）
     *
     * @return 总会话数
     */
    public long getTotalSessionCount() {
        return totalSessions.get();
    }

    /**
     * 获取过期会话数
     *
     * @return 过期会话数
     */
    public long getExpiredSessionCount() {
        return expiredSessions.get();
    }

    /**
     * 获取用户会话统计
     *
     * @return 用户会话数映射
     */
    public Map<String, Integer> getUserSessionCounts() {
        return userSessions.entrySet().stream()
                          .collect(Collectors.toMap(
                              Map.Entry::getKey,
                              entry -> entry.getValue().size()
                          ));
    }

    /**
     * 关闭会话管理器
     */
    public void shutdown() {
        logger.info("关闭会话管理器");

        // 关闭清理任务
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }

        // 销毁所有会话
        List<String> sessionIds = new ArrayList<>(sessions.keySet());
        for (String sessionId : sessionIds) {
            destroySession(sessionId);
        }

        logger.info("会话管理器关闭完成，处理会话总数: {}", totalSessions.get());
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 通知会话创建
     */
    private void notifySessionCreated(Session session) {
        globalListeners.forEach(listener -> {
            try {
                listener.onSessionCreated(session);
            } catch (Exception e) {
                logger.warn("通知会话创建事件失败", e);
            }
        });
    }

    /**
     * 通知会话无效化
     */
    private void notifySessionInvalidated(Session session) {
        globalListeners.forEach(listener -> {
            try {
                listener.onSessionInvalidated(session);
            } catch (Exception e) {
                logger.warn("通知会话无效化事件失败", e);
            }
        });
    }

    /**
     * 会话管理器配置接口
     */
    public interface SessionManagerConfig {
        
        /**
         * 获取最大会话数
         */
        int getMaxSessions();

        /**
         * 获取默认超时时间（毫秒）
         */
        long getDefaultTimeoutMillis();

        /**
         * 获取清理间隔（毫秒）
         */
        long getCleanupInterval();

        /**
         * 是否启用分布式会话
         */
        boolean isDistributedEnabled();
    }

    /**
     * 默认会话管理器配置
     */
    public static class DefaultSessionManagerConfig implements SessionManagerConfig {
        private final int maxSessions;
        private final long defaultTimeoutMillis;
        private final long cleanupInterval;
        private final boolean distributedEnabled;

        public DefaultSessionManagerConfig() {
            this(10000, 30 * 60 * 1000, 60 * 1000, false); // 1万会话，30分钟超时，1分钟清理间隔
        }

        public DefaultSessionManagerConfig(int maxSessions, long defaultTimeoutMillis, 
                                         long cleanupInterval, boolean distributedEnabled) {
            this.maxSessions = maxSessions;
            this.defaultTimeoutMillis = defaultTimeoutMillis;
            this.cleanupInterval = cleanupInterval;
            this.distributedEnabled = distributedEnabled;
        }

        @Override
        public int getMaxSessions() { return maxSessions; }

        @Override
        public long getDefaultTimeoutMillis() { return defaultTimeoutMillis; }

        @Override
        public long getCleanupInterval() { return cleanupInterval; }

        @Override
        public boolean isDistributedEnabled() { return distributedEnabled; }
    }
}