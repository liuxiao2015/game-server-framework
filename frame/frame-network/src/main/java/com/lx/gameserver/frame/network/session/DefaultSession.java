/*
 * 文件名: DefaultSession.java
 * 用途: 默认会话实现
 * 实现内容:
 *   - Session接口的标准实现
 *   - 线程安全的属性存储
 *   - 会话状态管理和超时检查
 *   - 事件通知机制
 *   - 连接关联和消息发送
 * 技术选型:
 *   - ConcurrentHashMap线程安全属性存储
 *   - 原子操作和volatile字段
 *   - 事件监听器模式
 *   - 时间戳基础的超时检查
 * 依赖关系:
 *   - 实现Session接口
 *   - 与Connection协作
 *   - 被SessionManager管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.session;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认会话实现
 * <p>
 * 提供Session接口的标准实现，支持线程安全的属性管理、
 * 状态跟踪和事件通知。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class DefaultSession implements Session {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSession.class);

    /**
     * 会话ID
     */
    private final String id;

    /**
     * 用户ID
     */
    private volatile String userId;

    /**
     * 会话类型
     */
    private volatile SessionType type;

    /**
     * 会话状态
     */
    private volatile SessionState state;

    /**
     * 关联的连接
     */
    private volatile Connection connection;

    /**
     * 创建时间
     */
    private final LocalDateTime createTime;

    /**
     * 最后访问时间
     */
    private volatile LocalDateTime lastAccessTime;

    /**
     * 超时时间（毫秒）
     */
    private volatile long timeoutMillis;

    /**
     * 会话属性
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 会话监听器
     */
    private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 构造函数
     *
     * @param id   会话ID
     * @param userId 用户ID
     * @param type 会话类型
     */
    public DefaultSession(String id, String userId, SessionType type) {
        this.id = id;
        this.userId = userId;
        this.type = type != null ? type : SessionType.GUEST;
        this.state = SessionState.CREATING;
        this.createTime = LocalDateTime.now();
        this.lastAccessTime = this.createTime;
        this.timeoutMillis = 30 * 60 * 1000; // 默认30分钟超时

        // 设置为活跃状态
        setState(SessionState.ACTIVE);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public void setUserId(String userId) {
        String oldUserId = this.userId;
        this.userId = userId;
        
        // 通知属性变更
        notifyAttributeChanged("userId", oldUserId, userId);
    }

    @Override
    public SessionType getType() {
        return type;
    }

    @Override
    public void setType(SessionType type) {
        SessionType oldType = this.type;
        this.type = type != null ? type : SessionType.GUEST;
        
        // 通知属性变更
        notifyAttributeChanged("type", oldType, this.type);
    }

    @Override
    public SessionState getState() {
        return state;
    }

    @Override
    public void setState(SessionState state) {
        if (state == null) {
            return;
        }

        SessionState oldState = this.state;
        this.state = state;

        // 根据状态变化通知相应事件
        switch (state) {
            case ACTIVE:
                if (oldState != SessionState.ACTIVE) {
                    notifySessionActivated();
                }
                break;
            case IDLE:
                if (oldState == SessionState.ACTIVE) {
                    notifySessionPassivated();
                }
                break;
            case EXPIRED:
                if (oldState != SessionState.EXPIRED) {
                    notifySessionExpired();
                }
                break;
            case INVALID:
                if (oldState != SessionState.INVALID) {
                    notifySessionInvalidated();
                }
                break;
        }
    }

    @Override
    public boolean isValid() {
        return state != SessionState.INVALID && state != SessionState.EXPIRED;
    }

    @Override
    public boolean isActive() {
        return state == SessionState.ACTIVE && !isExpired();
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
        updateLastAccessTime();
    }

    @Override
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public LocalDateTime getLastAccessTime() {
        return lastAccessTime;
    }

    @Override
    public void updateLastAccessTime() {
        this.lastAccessTime = LocalDateTime.now();
        
        // 如果会话处于空闲状态，重新激活
        if (state == SessionState.IDLE) {
            setState(SessionState.ACTIVE);
        }
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = Math.max(0, timeoutMillis);
    }

    @Override
    public boolean isExpired() {
        if (timeoutMillis <= 0) {
            return false; // 永不超时
        }

        long elapsedMillis = java.time.Duration.between(lastAccessTime, LocalDateTime.now()).toMillis();
        boolean expired = elapsedMillis > timeoutMillis;
        
        if (expired && state != SessionState.EXPIRED) {
            setState(SessionState.EXPIRED);
        }
        
        return expired;
    }

    @Override
    public Object setAttribute(String key, Object value) {
        if (key == null) {
            return null;
        }

        Object oldValue;
        if (value != null) {
            oldValue = attributes.put(key, value);
        } else {
            oldValue = attributes.remove(key);
        }

        // 通知属性变更
        notifyAttributeChanged(key, oldValue, value);

        updateLastAccessTime();
        return oldValue;
    }

    @Override
    public Object getAttribute(String key) {
        if (key == null) {
            return null;
        }

        updateLastAccessTime();
        return attributes.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        if (key == null) {
            return defaultValue;
        }

        updateLastAccessTime();
        Object value = attributes.get(key);
        
        if (value == null) {
            return defaultValue;
        }

        try {
            return (T) value;
        } catch (ClassCastException e) {
            logger.warn("会话属性类型转换失败: {} -> {}", key, defaultValue.getClass(), e);
            return defaultValue;
        }
    }

    @Override
    public Object removeAttribute(String key) {
        if (key == null) {
            return null;
        }

        Object oldValue = attributes.remove(key);
        
        // 通知属性变更
        if (oldValue != null) {
            notifyAttributeChanged(key, oldValue, null);
        }

        updateLastAccessTime();
        return oldValue;
    }

    @Override
    public Map<String, Object> getAttributes() {
        updateLastAccessTime();
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public void clearAttributes() {
        Map<String, Object> oldAttributes = new ConcurrentHashMap<>(attributes);
        attributes.clear();
        
        // 通知所有属性变更
        oldAttributes.forEach((key, value) -> notifyAttributeChanged(key, value, null));
        
        updateLastAccessTime();
    }

    @Override
    public boolean hasAttribute(String key) {
        updateLastAccessTime();
        return key != null && attributes.containsKey(key);
    }

    @Override
    public CompletableFuture<Void> send(Object message) {
        if (connection != null && connection.isActive()) {
            updateLastAccessTime();
            return connection.send(message);
        } else {
            return CompletableFuture.failedFuture(
                new IllegalStateException("会话没有有效的连接: " + id));
        }
    }

    @Override
    public void sendSync(Object message) throws Exception {
        if (connection != null && connection.isActive()) {
            updateLastAccessTime();
            connection.sendSync(message);
        } else {
            throw new IllegalStateException("会话没有有效的连接: " + id);
        }
    }

    @Override
    public void invalidate() {
        setState(SessionState.INVALID);
        
        // 清空属性
        clearAttributes();
        
        // 断开连接
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                logger.warn("关闭会话连接失败: {}", id, e);
            }
            connection = null;
        }
        
        logger.debug("会话已无效化: {}", id);
    }

    @Override
    public void addSessionListener(SessionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知会话激活
     */
    private void notifySessionActivated() {
        listeners.forEach(listener -> {
            try {
                listener.onSessionActivated(this);
            } catch (Exception e) {
                logger.warn("通知会话激活事件失败", e);
            }
        });
    }

    /**
     * 通知会话钝化
     */
    private void notifySessionPassivated() {
        listeners.forEach(listener -> {
            try {
                listener.onSessionPassivated(this);
            } catch (Exception e) {
                logger.warn("通知会话钝化事件失败", e);
            }
        });
    }

    /**
     * 通知会话过期
     */
    private void notifySessionExpired() {
        listeners.forEach(listener -> {
            try {
                listener.onSessionExpired(this);
            } catch (Exception e) {
                logger.warn("通知会话过期事件失败", e);
            }
        });
    }

    /**
     * 通知会话无效化
     */
    private void notifySessionInvalidated() {
        listeners.forEach(listener -> {
            try {
                listener.onSessionInvalidated(this);
            } catch (Exception e) {
                logger.warn("通知会话无效化事件失败", e);
            }
        });
    }

    /**
     * 通知属性变更
     */
    private void notifyAttributeChanged(String key, Object oldValue, Object newValue) {
        listeners.forEach(listener -> {
            try {
                listener.onAttributeChanged(this, key, oldValue, newValue);
            } catch (Exception e) {
                logger.warn("通知会话属性变更事件失败", e);
            }
        });
    }

    @Override
    public String toString() {
        return String.format("DefaultSession{id='%s', userId='%s', type=%s, state=%s, createTime=%s, lastAccessTime=%s}", 
                           id, userId, type, state, createTime, lastAccessTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DefaultSession that = (DefaultSession) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}