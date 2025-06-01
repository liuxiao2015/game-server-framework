/*
 * 文件名: ConnectionEventHandler.java
 * 用途: 连接事件处理器
 * 实现内容:
 *   - 连接生命周期事件处理
 *   - 连接建立和断开事件
 *   - 异常事件处理和恢复
 *   - 空闲事件检测和处理
 *   - 自定义事件扩展支持
 * 技术选型:
 *   - 观察者模式实现事件通知
 *   - 异步事件处理机制
 *   - 线程安全的事件分发
 * 依赖关系:
 *   - 被ConnectionManager使用
 *   - 与Connection接口协作
 *   - 支持事件监听器注册
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.connection;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 连接事件处理器
 * <p>
 * 负责处理连接相关的各种事件，包括连接建立、断开、异常等。
 * 支持事件监听器注册和异步事件处理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ConnectionEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionEventHandler.class);

    private final List<ConnectionEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService eventExecutor;
    private volatile boolean started = false;

    /**
     * 连接事件监听器接口
     */
    public interface ConnectionEventListener {
        
        /**
         * 连接建立事件
         *
         * @param connection 连接对象
         */
        default void onConnectionEstablished(Connection connection) {}

        /**
         * 连接断开事件
         *
         * @param connection 连接对象
         * @param reason     断开原因
         */
        default void onConnectionClosed(Connection connection, String reason) {}

        /**
         * 连接异常事件
         *
         * @param connection 连接对象
         * @param cause      异常原因
         */
        default void onConnectionException(Connection connection, Throwable cause) {}

        /**
         * 连接空闲事件
         *
         * @param connection 连接对象
         * @param idleType   空闲类型
         * @param idleTime   空闲时间（毫秒）
         */
        default void onConnectionIdle(Connection connection, IdleType idleType, long idleTime) {}

        /**
         * 连接状态变化事件
         *
         * @param connection 连接对象
         * @param oldState   旧状态
         * @param newState   新状态
         */
        default void onConnectionStateChanged(Connection connection, 
                                            Connection.ConnectionState oldState, 
                                            Connection.ConnectionState newState) {}

        /**
         * 自定义事件
         *
         * @param connection 连接对象
         * @param eventType  事件类型
         * @param eventData  事件数据
         */
        default void onCustomEvent(Connection connection, String eventType, Object eventData) {}
    }

    /**
     * 空闲类型枚举
     */
    public enum IdleType {
        /** 读空闲 */
        READER_IDLE,
        /** 写空闲 */
        WRITER_IDLE,
        /** 全空闲 */
        ALL_IDLE
    }

    /**
     * 构造函数
     */
    public ConnectionEventHandler() {
        this.eventExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ConnectionEventHandler-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动事件处理器
     */
    public void start() {
        if (started) {
            return;
        }
        
        started = true;
        logger.info("连接事件处理器启动成功，监听器数量: {}", listeners.size());
    }

    /**
     * 停止事件处理器
     */
    public void stop() {
        if (!started) {
            return;
        }
        
        started = false;
        eventExecutor.shutdown();
        logger.info("连接事件处理器已停止");
    }

    /**
     * 添加事件监听器
     */
    public void addListener(ConnectionEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
            logger.debug("添加连接事件监听器: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * 移除事件监听器
     */
    public void removeListener(ConnectionEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            logger.debug("移除连接事件监听器: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * 清空所有监听器
     */
    public void clearListeners() {
        listeners.clear();
        logger.debug("清空所有连接事件监听器");
    }

    /**
     * 触发连接建立事件
     */
    public void fireConnectionEstablished(Connection connection) {
        if (!started) {
            return;
        }
        
        logger.debug("触发连接建立事件: {}", connection.getId());
        
        eventExecutor.execute(() -> {
            for (ConnectionEventListener listener : listeners) {
                try {
                    listener.onConnectionEstablished(connection);
                } catch (Exception e) {
                    logger.warn("处理连接建立事件失败，监听器: {}", 
                              listener.getClass().getSimpleName(), e);
                }
            }
        });
    }

    /**
     * 触发连接断开事件
     */
    public void fireConnectionClosed(Connection connection, String reason) {
        if (!started) {
            return;
        }
        
        logger.debug("触发连接断开事件: {}, 原因: {}", connection.getId(), reason);
        
        eventExecutor.execute(() -> {
            for (ConnectionEventListener listener : listeners) {
                try {
                    listener.onConnectionClosed(connection, reason);
                } catch (Exception e) {
                    logger.warn("处理连接断开事件失败，监听器: {}", 
                              listener.getClass().getSimpleName(), e);
                }
            }
        });
    }

    /**
     * 触发连接异常事件
     */
    public void fireConnectionException(Connection connection, Throwable cause) {
        if (!started) {
            return;
        }
        
        logger.debug("触发连接异常事件: {}", connection.getId(), cause);
        
        eventExecutor.execute(() -> {
            for (ConnectionEventListener listener : listeners) {
                try {
                    listener.onConnectionException(connection, cause);
                } catch (Exception e) {
                    logger.warn("处理连接异常事件失败，监听器: {}", 
                              listener.getClass().getSimpleName(), e);
                }
            }
        });
    }

    /**
     * 触发连接空闲事件
     */
    public void fireConnectionIdle(Connection connection, IdleType idleType, long idleTime) {
        if (!started) {
            return;
        }
        
        logger.debug("触发连接空闲事件: {}, 类型: {}, 时间: {}ms", 
                    connection.getId(), idleType, idleTime);
        
        eventExecutor.execute(() -> {
            for (ConnectionEventListener listener : listeners) {
                try {
                    listener.onConnectionIdle(connection, idleType, idleTime);
                } catch (Exception e) {
                    logger.warn("处理连接空闲事件失败，监听器: {}", 
                              listener.getClass().getSimpleName(), e);
                }
            }
        });
    }

    /**
     * 触发连接状态变化事件
     */
    public void fireConnectionStateChanged(Connection connection, 
                                         Connection.ConnectionState oldState, 
                                         Connection.ConnectionState newState) {
        if (!started) {
            return;
        }
        
        logger.debug("触发连接状态变化事件: {}, {} -> {}", 
                    connection.getId(), oldState, newState);
        
        eventExecutor.execute(() -> {
            for (ConnectionEventListener listener : listeners) {
                try {
                    listener.onConnectionStateChanged(connection, oldState, newState);
                } catch (Exception e) {
                    logger.warn("处理连接状态变化事件失败，监听器: {}", 
                              listener.getClass().getSimpleName(), e);
                }
            }
        });
    }

    /**
     * 触发自定义事件
     */
    public void fireCustomEvent(Connection connection, String eventType, Object eventData) {
        if (!started) {
            return;
        }
        
        logger.debug("触发自定义事件: {}, 类型: {}", connection.getId(), eventType);
        
        eventExecutor.execute(() -> {
            for (ConnectionEventListener listener : listeners) {
                try {
                    listener.onCustomEvent(connection, eventType, eventData);
                } catch (Exception e) {
                    logger.warn("处理自定义事件失败，监听器: {}, 事件类型: {}", 
                              listener.getClass().getSimpleName(), eventType, e);
                }
            }
        });
    }

    /**
     * 获取监听器数量
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * 是否已启动
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 获取事件执行器信息
     */
    public String getExecutorInfo() {
        return String.format("EventExecutor[shutdown=%s]", eventExecutor.isShutdown());
    }
}