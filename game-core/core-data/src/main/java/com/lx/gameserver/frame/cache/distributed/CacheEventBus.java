/*
 * 文件名: CacheEventBus.java
 * 用途: 缓存事件总线
 * 实现内容:
 *   - 缓存失效事件处理
 *   - 缓存更新事件通知
 *   - 过期事件处理
 *   - 订阅发布机制
 *   - 集群广播支持
 * 技术选型:
 *   - 观察者模式事件通知
 *   - Redis发布订阅
 *   - 异步事件处理
 *   - 事件过滤和路由
 * 依赖关系:
 *   - 与缓存实现集成
 *   - 依赖Redis消息机制
 *   - 提供事件驱动能力
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.distributed;

import com.lx.gameserver.frame.cache.core.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.ChannelTopic;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 缓存事件总线
 * <p>
 * 基于Redis发布订阅机制实现的缓存事件总线，支持缓存事件的
 * 发布、订阅、过滤和路由功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class CacheEventBus {

    private static final Logger logger = LoggerFactory.getLogger(CacheEventBus.class);

    /**
     * 缓存事件频道前缀
     */
    private static final String CACHE_EVENT_CHANNEL = "cache:events:";

    /**
     * Redis模板
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis消息监听器容器
     */
    private final RedisMessageListenerContainer listenerContainer;

    /**
     * 事件监听器映射
     */
    private final Map<String, Set<CacheEventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 事件过滤器
     */
    private final Map<String, Predicate<CacheEvent>> filters = new ConcurrentHashMap<>();

    /**
     * 事件处理执行器
     */
    private final ExecutorService eventExecutor;

    /**
     * 是否启用集群广播
     */
    private final boolean clusterBroadcastEnabled;

    /**
     * 统计信息
     */
    private final AtomicLong publishedEvents = new AtomicLong(0);
    private final AtomicLong receivedEvents = new AtomicLong(0);
    private final AtomicLong processedEvents = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param redisTemplate      Redis模板
     * @param listenerContainer  消息监听器容器
     * @param config            配置
     */
    public CacheEventBus(RedisTemplate<String, Object> redisTemplate,
                        RedisMessageListenerContainer listenerContainer,
                        EventBusConfig config) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.clusterBroadcastEnabled = config.isClusterBroadcastEnabled();
        
        this.eventExecutor = Executors.newFixedThreadPool(
            config.getEventThreads(),
            r -> {
                Thread t = new Thread(r, "cache-event-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
        );

        logger.info("初始化缓存事件总线，集群广播: {}, 处理线程: {}", 
            clusterBroadcastEnabled, config.getEventThreads());
    }

    /**
     * 发布缓存事件
     *
     * @param event 缓存事件
     */
    public void publishEvent(CacheEvent event) {
        try {
            publishedEvents.incrementAndGet();
            
            // 本地处理
            processEventLocally(event);
            
            // 集群广播
            if (clusterBroadcastEnabled) {
                broadcastEvent(event);
            }
            
        } catch (Exception e) {
            failedEvents.incrementAndGet();
            logger.error("发布缓存事件失败: {}", event, e);
        }
    }

    /**
     * 异步发布缓存事件
     *
     * @param event 缓存事件
     * @return 异步结果
     */
    public CompletableFuture<Void> publishEventAsync(CacheEvent event) {
        return CompletableFuture.runAsync(() -> publishEvent(event), eventExecutor);
    }

    /**
     * 订阅缓存事件
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     */
    public void subscribe(CacheEventType eventType, CacheEventListener listener) {
        subscribe(eventType.name(), listener);
    }

    /**
     * 订阅缓存事件
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     */
    public void subscribe(String eventType, CacheEventListener listener) {
        listeners.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(listener);
        
        // 如果是集群模式，需要订阅Redis频道
        if (clusterBroadcastEnabled) {
            subscribeRedisChannel(eventType);
        }
        
        logger.debug("添加事件监听器: {}", eventType);
    }

    /**
     * 取消订阅
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     */
    public void unsubscribe(CacheEventType eventType, CacheEventListener listener) {
        unsubscribe(eventType.name(), listener);
    }

    /**
     * 取消订阅
     *
     * @param eventType 事件类型
     * @param listener  事件监听器
     */
    public void unsubscribe(String eventType, CacheEventListener listener) {
        Set<CacheEventListener> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            if (eventListeners.isEmpty()) {
                listeners.remove(eventType);
            }
        }
        
        logger.debug("移除事件监听器: {}", eventType);
    }

    /**
     * 添加事件过滤器
     *
     * @param eventType 事件类型
     * @param filter    过滤器
     */
    public void addFilter(String eventType, Predicate<CacheEvent> filter) {
        filters.put(eventType, filter);
        logger.debug("添加事件过滤器: {}", eventType);
    }

    /**
     * 移除事件过滤器
     *
     * @param eventType 事件类型
     */
    public void removeFilter(String eventType) {
        filters.remove(eventType);
        logger.debug("移除事件过滤器: {}", eventType);
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public EventBusStatistics getStatistics() {
        return new EventBusStatistics(
            publishedEvents.get(),
            receivedEvents.get(),
            processedEvents.get(),
            failedEvents.get(),
            listeners.size()
        );
    }

    /**
     * 本地处理事件
     */
    private void processEventLocally(CacheEvent event) {
        eventExecutor.submit(() -> {
            try {
                processedEvents.incrementAndGet();
                notifyListeners(event);
            } catch (Exception e) {
                failedEvents.incrementAndGet();
                logger.error("本地处理缓存事件失败: {}", event, e);
            }
        });
    }

    /**
     * 广播事件到集群
     */
    private void broadcastEvent(CacheEvent event) {
        try {
            String channel = CACHE_EVENT_CHANNEL + event.getEventType();
            redisTemplate.convertAndSend(channel, event);
            
            logger.debug("广播缓存事件: {} -> {}", event.getEventType(), channel);
        } catch (Exception e) {
            logger.error("广播缓存事件失败: {}", event, e);
        }
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(CacheEvent event) {
        String eventType = event.getEventType();
        Set<CacheEventListener> eventListeners = listeners.get(eventType);
        
        if (eventListeners != null) {
            // 应用过滤器
            Predicate<CacheEvent> filter = filters.get(eventType);
            if (filter != null && !filter.test(event)) {
                return;
            }
            
            // 通知监听器
            for (CacheEventListener listener : eventListeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    logger.warn("通知缓存事件监听器失败: {}", eventType, e);
                }
            }
        }
    }

    /**
     * 订阅Redis频道
     */
    private void subscribeRedisChannel(String eventType) {
        String channel = CACHE_EVENT_CHANNEL + eventType;
        Topic topic = new ChannelTopic(channel);
        
        MessageListener messageListener = new CacheEventMessageListener();
        listenerContainer.addMessageListener(messageListener, topic);
        
        logger.debug("订阅Redis频道: {}", channel);
    }

    /**
     * Redis消息监听器
     */
    private class CacheEventMessageListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                receivedEvents.incrementAndGet();
                
                // 反序列化事件
                Object eventObj = redisTemplate.getValueSerializer().deserialize(message.getBody());
                if (eventObj instanceof CacheEvent) {
                    CacheEvent event = (CacheEvent) eventObj;
                    processEventLocally(event);
                }
                
            } catch (Exception e) {
                failedEvents.incrementAndGet();
                logger.error("处理Redis缓存事件消息失败", e);
            }
        }
    }

    /**
     * 创建缓存事件
     */
    public static CacheEvent createPutEvent(String cacheName, Object key, Object value) {
        return new CacheEvent(CacheEventType.PUT, cacheName, key, value, null);
    }

    public static CacheEvent createRemoveEvent(String cacheName, Object key, Object oldValue) {
        return new CacheEvent(CacheEventType.REMOVE, cacheName, key, null, oldValue);
    }

    public static CacheEvent createEvictEvent(String cacheName, Object key, Object oldValue) {
        return new CacheEvent(CacheEventType.EVICT, cacheName, key, null, oldValue);
    }

    public static CacheEvent createExpireEvent(String cacheName, Object key, Object oldValue) {
        return new CacheEvent(CacheEventType.EXPIRE, cacheName, key, null, oldValue);
    }

    public static CacheEvent createClearEvent(String cacheName) {
        return new CacheEvent(CacheEventType.CLEAR, cacheName, null, null, null);
    }

    /**
     * 缓存事件类型
     */
    public enum CacheEventType {
        PUT,     // 缓存写入
        REMOVE,  // 缓存移除
        EVICT,   // 缓存逐出
        EXPIRE,  // 缓存过期
        CLEAR,   // 缓存清空
        UPDATE   // 缓存更新
    }

    /**
     * 缓存事件
     */
    public static class CacheEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String eventType;
        private final String cacheName;
        private final Object key;
        private final Object newValue;
        private final Object oldValue;
        private final Instant timestamp;
        private final String nodeId;

        public CacheEvent(CacheEventType eventType, String cacheName, Object key, 
                         Object newValue, Object oldValue) {
            this.eventType = eventType.name();
            this.cacheName = cacheName;
            this.key = key;
            this.newValue = newValue;
            this.oldValue = oldValue;
            this.timestamp = Instant.now();
            this.nodeId = generateNodeId();
        }

        public String getEventType() { return eventType; }
        public String getCacheName() { return cacheName; }
        public Object getKey() { return key; }
        public Object getNewValue() { return newValue; }
        public Object getOldValue() { return oldValue; }
        public Instant getTimestamp() { return timestamp; }
        public String getNodeId() { return nodeId; }

        private static String generateNodeId() {
            // 简单的节点ID生成，实际应用中可以使用更复杂的策略
            return System.getProperty("node.id", "unknown");
        }

        @Override
        public String toString() {
            return String.format("CacheEvent{type=%s, cache=%s, key=%s, timestamp=%s}", 
                eventType, cacheName, key, timestamp);
        }
    }

    /**
     * 缓存事件监听器接口
     */
    @FunctionalInterface
    public interface CacheEventListener {
        void onEvent(CacheEvent event);
    }

    /**
     * 事件总线统计信息
     */
    public static class EventBusStatistics {
        private final long publishedEvents;
        private final long receivedEvents;
        private final long processedEvents;
        private final long failedEvents;
        private final int listenerCount;

        public EventBusStatistics(long publishedEvents, long receivedEvents, 
                                long processedEvents, long failedEvents, int listenerCount) {
            this.publishedEvents = publishedEvents;
            this.receivedEvents = receivedEvents;
            this.processedEvents = processedEvents;
            this.failedEvents = failedEvents;
            this.listenerCount = listenerCount;
        }

        public long getPublishedEvents() { return publishedEvents; }
        public long getReceivedEvents() { return receivedEvents; }
        public long getProcessedEvents() { return processedEvents; }
        public long getFailedEvents() { return failedEvents; }
        public int getListenerCount() { return listenerCount; }
        
        public double getSuccessRate() {
            long totalEvents = publishedEvents + receivedEvents;
            return totalEvents > 0 ? (double) processedEvents / totalEvents : 0.0;
        }
        
        public double getFailureRate() {
            long totalEvents = publishedEvents + receivedEvents;
            return totalEvents > 0 ? (double) failedEvents / totalEvents : 0.0;
        }
    }

    /**
     * 事件总线配置
     */
    public static class EventBusConfig {
        private boolean clusterBroadcastEnabled = true;
        private int eventThreads = 4;
        private Duration eventTimeout = Duration.ofSeconds(30);

        public boolean isClusterBroadcastEnabled() { return clusterBroadcastEnabled; }
        public void setClusterBroadcastEnabled(boolean clusterBroadcastEnabled) { 
            this.clusterBroadcastEnabled = clusterBroadcastEnabled; 
        }

        public int getEventThreads() { return eventThreads; }
        public void setEventThreads(int eventThreads) { this.eventThreads = eventThreads; }

        public Duration getEventTimeout() { return eventTimeout; }
        public void setEventTimeout(Duration eventTimeout) { this.eventTimeout = eventTimeout; }
    }
}