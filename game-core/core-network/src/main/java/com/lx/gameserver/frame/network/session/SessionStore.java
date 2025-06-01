/*
 * 文件名: SessionStore.java
 * 用途: 会话存储接口
 * 实现内容:
 *   - 定义会话持久化的标准接口
 *   - 支持内存、Redis、数据库等多种存储
 *   - 会话同步和分布式支持
 *   - 过期清理和批量操作
 *   - 存储统计和监控
 * 技术选型:
 *   - 抽象接口设计，支持多种实现
 *   - 异步操作支持
 *   - 批量操作优化
 *   - 可插拔的序列化策略
 * 依赖关系:
 *   - 被SessionManager使用
 *   - 存储Session对象
 *   - 支持分布式部署
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.session;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 会话存储接口
 * <p>
 * 定义会话持久化的标准操作，支持多种存储实现。
 * 提供同步和异步操作，支持分布式部署场景。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface SessionStore {

    /**
     * 保存会话
     *
     * @param session 会话对象
     * @throws Exception 保存失败时抛出异常
     */
    void saveSession(Session session) throws Exception;

    /**
     * 异步保存会话
     *
     * @param session 会话对象
     * @return 保存结果的Future对象
     */
    CompletableFuture<Void> saveSessionAsync(Session session);

    /**
     * 加载会话
     *
     * @param sessionId 会话ID
     * @return 会话对象，如果不存在则返回null
     * @throws Exception 加载失败时抛出异常
     */
    Session loadSession(String sessionId) throws Exception;

    /**
     * 异步加载会话
     *
     * @param sessionId 会话ID
     * @return 会话对象的Future对象
     */
    CompletableFuture<Session> loadSessionAsync(String sessionId);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return true表示删除成功
     * @throws Exception 删除失败时抛出异常
     */
    boolean deleteSession(String sessionId) throws Exception;

    /**
     * 异步删除会话
     *
     * @param sessionId 会话ID
     * @return 删除结果的Future对象
     */
    CompletableFuture<Boolean> deleteSessionAsync(String sessionId);

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return true表示会话存在
     * @throws Exception 检查失败时抛出异常
     */
    boolean existsSession(String sessionId) throws Exception;

    /**
     * 获取用户的所有会话ID
     *
     * @param userId 用户ID
     * @return 会话ID列表
     * @throws Exception 获取失败时抛出异常
     */
    List<String> getUserSessionIds(String userId) throws Exception;

    /**
     * 批量保存会话
     *
     * @param sessions 会话集合
     * @return 保存成功的会话数
     * @throws Exception 保存失败时抛出异常
     */
    int saveSessions(Collection<Session> sessions) throws Exception;

    /**
     * 批量删除会话
     *
     * @param sessionIds 会话ID集合
     * @return 删除成功的会话数
     * @throws Exception 删除失败时抛出异常
     */
    int deleteSessions(Collection<String> sessionIds) throws Exception;

    /**
     * 清理过期会话
     *
     * @return 清理的会话数
     * @throws Exception 清理失败时抛出异常
     */
    int cleanupExpiredSessions() throws Exception;

    /**
     * 获取会话总数
     *
     * @return 会话总数
     * @throws Exception 获取失败时抛出异常
     */
    long getSessionCount() throws Exception;

    /**
     * 获取存储统计信息
     *
     * @return 统计信息映射
     * @throws Exception 获取失败时抛出异常
     */
    Map<String, Object> getStatistics() throws Exception;

    /**
     * 关闭存储器
     */
    void close();

    /**
     * 内存会话存储实现
     */
    class MemorySessionStore implements SessionStore {
        private final Map<String, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, java.util.Set<String>> userSessions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void saveSession(Session session) {
            if (session != null) {
                sessions.put(session.getId(), session);
                
                String userId = session.getUserId();
                if (userId != null) {
                    userSessions.computeIfAbsent(userId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                               .add(session.getId());
                }
            }
        }

        @Override
        public CompletableFuture<Void> saveSessionAsync(Session session) {
            return CompletableFuture.runAsync(() -> {
                try {
                    saveSession(session);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public Session loadSession(String sessionId) {
            return sessionId != null ? sessions.get(sessionId) : null;
        }

        @Override
        public CompletableFuture<Session> loadSessionAsync(String sessionId) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return loadSession(sessionId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public boolean deleteSession(String sessionId) {
            if (sessionId == null) {
                return false;
            }

            Session session = sessions.remove(sessionId);
            if (session != null) {
                String userId = session.getUserId();
                if (userId != null) {
                    java.util.Set<String> userSessionSet = userSessions.get(userId);
                    if (userSessionSet != null) {
                        userSessionSet.remove(sessionId);
                        if (userSessionSet.isEmpty()) {
                            userSessions.remove(userId);
                        }
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public CompletableFuture<Boolean> deleteSessionAsync(String sessionId) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return deleteSession(sessionId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public boolean existsSession(String sessionId) {
            return sessionId != null && sessions.containsKey(sessionId);
        }

        @Override
        public List<String> getUserSessionIds(String userId) {
            if (userId == null) {
                return java.util.Collections.emptyList();
            }
            
            java.util.Set<String> sessionIds = userSessions.get(userId);
            return sessionIds != null ? new java.util.ArrayList<>(sessionIds) : java.util.Collections.emptyList();
        }

        @Override
        public int saveSessions(Collection<Session> sessionCollection) {
            if (sessionCollection == null) {
                return 0;
            }
            
            int count = 0;
            for (Session session : sessionCollection) {
                try {
                    saveSession(session);
                    count++;
                } catch (Exception e) {
                    // 忽略保存失败的会话
                }
            }
            return count;
        }

        @Override
        public int deleteSessions(Collection<String> sessionIds) {
            if (sessionIds == null) {
                return 0;
            }
            
            int count = 0;
            for (String sessionId : sessionIds) {
                try {
                    if (deleteSession(sessionId)) {
                        count++;
                    }
                } catch (Exception e) {
                    // 忽略删除失败的会话
                }
            }
            return count;
        }

        @Override
        public int cleanupExpiredSessions() {
            List<String> expiredSessionIds = new java.util.ArrayList<>();
            
            for (Session session : sessions.values()) {
                if (session.isExpired() || !session.isValid()) {
                    expiredSessionIds.add(session.getId());
                }
            }
            
            return deleteSessions(expiredSessionIds);
        }

        @Override
        public long getSessionCount() {
            return sessions.size();
        }

        @Override
        public Map<String, Object> getStatistics() {
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("type", "memory");
            stats.put("sessionCount", sessions.size());
            stats.put("userCount", userSessions.size());
            stats.put("memoryUsage", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            return stats;
        }

        @Override
        public void close() {
            sessions.clear();
            userSessions.clear();
        }
    }
}