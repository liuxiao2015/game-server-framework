/*
 * 文件名: Session.java
 * 用途: 会话抽象
 * 实现内容:
 *   - 游戏会话的抽象定义
 *   - 会话ID和状态管理
 *   - 会话属性存储和访问
 *   - 关联连接的管理
 *   - 会话超时处理
 *   - 会话事件通知机制
 * 技术选型:
 *   - 接口抽象设计，支持多种实现
 *   - 线程安全的属性存储
 *   - 事件驱动的状态管理
 *   - 灵活的超时策略
 * 依赖关系:
 *   - 与Connection接口关联
 *   - 被SessionManager管理
 *   - 支持SessionStore持久化
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.session;

import com.lx.gameserver.frame.network.core.Connection;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 会话接口
 * <p>
 * 定义游戏会话的标准操作，提供会话状态管理、属性存储、
 * 连接关联和超时处理等核心功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface Session {

    /**
     * 会话状态枚举
     */
    enum SessionState {
        /** 创建中 */
        CREATING("创建中"),
        
        /** 活跃状态 */
        ACTIVE("活跃"),
        
        /** 空闲状态 */
        IDLE("空闲"),
        
        /** 过期状态 */
        EXPIRED("过期"),
        
        /** 无效状态 */
        INVALID("无效");

        private final String description;

        SessionState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 会话类型枚举
     */
    enum SessionType {
        /** 游客会话 */
        GUEST("游客"),
        
        /** 用户会话 */
        USER("用户"),
        
        /** 管理员会话 */
        ADMIN("管理员"),
        
        /** 系统会话 */
        SYSTEM("系统");

        private final String description;

        SessionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 获取会话ID
     *
     * @return 会话唯一标识
     */
    String getId();

    /**
     * 获取用户ID
     *
     * @return 用户ID，游客会话可能为null
     */
    String getUserId();

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    void setUserId(String userId);

    /**
     * 获取会话类型
     *
     * @return 会话类型
     */
    SessionType getType();

    /**
     * 设置会话类型
     *
     * @param type 会话类型
     */
    void setType(SessionType type);

    /**
     * 获取会话状态
     *
     * @return 会话状态
     */
    SessionState getState();

    /**
     * 设置会话状态
     *
     * @param state 会话状态
     */
    void setState(SessionState state);

    /**
     * 检查会话是否有效
     *
     * @return true表示会话有效
     */
    boolean isValid();

    /**
     * 检查会话是否活跃
     *
     * @return true表示会话活跃
     */
    boolean isActive();

    /**
     * 获取关联的连接
     *
     * @return 连接对象，可能为null
     */
    Connection getConnection();

    /**
     * 设置关联的连接
     *
     * @param connection 连接对象
     */
    void setConnection(Connection connection);

    /**
     * 获取会话创建时间
     *
     * @return 创建时间
     */
    LocalDateTime getCreateTime();

    /**
     * 获取最后访问时间
     *
     * @return 最后访问时间
     */
    LocalDateTime getLastAccessTime();

    /**
     * 更新最后访问时间
     */
    void updateLastAccessTime();

    /**
     * 获取会话超时时间（毫秒）
     *
     * @return 超时时间，0表示永不超时
     */
    long getTimeoutMillis();

    /**
     * 设置会话超时时间
     *
     * @param timeoutMillis 超时时间（毫秒），0表示永不超时
     */
    void setTimeoutMillis(long timeoutMillis);

    /**
     * 检查会话是否超时
     *
     * @return true表示会话已超时
     */
    boolean isExpired();

    /**
     * 设置会话属性
     *
     * @param key   属性键
     * @param value 属性值
     * @return 之前的属性值
     */
    Object setAttribute(String key, Object value);

    /**
     * 获取会话属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回null
     */
    Object getAttribute(String key);

    /**
     * 获取会话属性（带默认值）
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @param <T>          属性值类型
     * @return 属性值或默认值
     */
    <T> T getAttribute(String key, T defaultValue);

    /**
     * 移除会话属性
     *
     * @param key 属性键
     * @return 被移除的属性值
     */
    Object removeAttribute(String key);

    /**
     * 获取所有会话属性
     *
     * @return 属性映射的只读视图
     */
    Map<String, Object> getAttributes();

    /**
     * 清空所有会话属性
     */
    void clearAttributes();

    /**
     * 检查是否有指定属性
     *
     * @param key 属性键
     * @return true表示存在该属性
     */
    boolean hasAttribute(String key);

    /**
     * 发送消息给会话关联的连接
     *
     * @param message 要发送的消息
     * @return 发送结果的Future对象
     */
    CompletableFuture<Void> send(Object message);

    /**
     * 同步发送消息
     *
     * @param message 要发送的消息
     * @throws Exception 发送失败时抛出异常
     */
    void sendSync(Object message) throws Exception;

    /**
     * 使会话无效
     */
    void invalidate();

    /**
     * 添加会话监听器
     *
     * @param listener 会话监听器
     */
    void addSessionListener(SessionListener listener);

    /**
     * 移除会话监听器
     *
     * @param listener 会话监听器
     */
    void removeSessionListener(SessionListener listener);

    /**
     * 会话监听器接口
     */
    interface SessionListener {

        /**
         * 会话创建事件
         *
         * @param session 会话对象
         */
        default void onSessionCreated(Session session) {
        }

        /**
         * 会话激活事件
         *
         * @param session 会话对象
         */
        default void onSessionActivated(Session session) {
        }

        /**
         * 会话钝化事件
         *
         * @param session 会话对象
         */
        default void onSessionPassivated(Session session) {
        }

        /**
         * 会话过期事件
         *
         * @param session 会话对象
         */
        default void onSessionExpired(Session session) {
        }

        /**
         * 会话无效化事件
         *
         * @param session 会话对象
         */
        default void onSessionInvalidated(Session session) {
        }

        /**
         * 会话属性变更事件
         *
         * @param session 会话对象
         * @param key     属性键
         * @param oldValue 旧值
         * @param newValue 新值
         */
        default void onAttributeChanged(Session session, String key, Object oldValue, Object newValue) {
        }
    }
}