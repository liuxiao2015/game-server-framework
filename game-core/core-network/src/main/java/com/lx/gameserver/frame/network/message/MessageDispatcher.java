/*
 * 文件名: MessageDispatcher.java
 * 用途: 消息分发器
 * 实现内容:
 *   - 消息注册和路由分发
 *   - 处理器映射和管理
 *   - 多线程模型（Virtual Threads支持）
 *   - 异步消息处理
 *   - 错误处理和恢复机制
 *   - 消息处理统计和监控
 * 技术选型:
 *   - ConcurrentHashMap高并发处理器存储
 *   - CompletableFuture异步处理
 *   - 线程池管理和Virtual Threads支持
 *   - 灵活的消息类型映射
 * 依赖关系:
 *   - 使用Message基类进行消息处理
 *   - 与MessageHandler协作
 *   - 被NetworkServer和NetworkClient使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.message;

import com.lx.gameserver.frame.network.core.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 消息分发器
 * <p>
 * 负责消息的注册、路由和分发处理。支持多种消息类型的处理器注册，
 * 提供异步处理能力和错误恢复机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class MessageDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);

    /**
     * 消息处理器映射 - 消息类型到处理器的映射
     */
    private final Map<Class<?>, List<MessageHandler<?>>> handlers = new ConcurrentHashMap<>();

    /**
     * 消息类型映射 - 支持消息ID到类型的映射
     */
    private final Map<Integer, Class<?>> messageTypeMapping = new ConcurrentHashMap<>();

    /**
     * 默认处理器 - 处理未注册的消息类型
     */
    private volatile MessageHandler<Object> defaultHandler;

    /**
     * 异常处理器
     */
    private volatile MessageExceptionHandler exceptionHandler;

    /**
     * 消息拦截器链
     */
    private final List<MessageInterceptor> interceptors = new CopyOnWriteArrayList<>();

    /**
     * 执行器服务
     */
    private final ExecutorService executorService;

    /**
     * 是否使用Virtual Threads
     */
    private final boolean useVirtualThreads;

    /**
     * 统计信息
     */
    private final AtomicLong processedMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param config 分发器配置
     */
    public MessageDispatcher(MessageDispatcherConfig config) {
        this.useVirtualThreads = config.isUseVirtualThreads();
        this.executorService = createExecutorService(config);
        
        logger.info("初始化消息分发器，使用Virtual Threads: {}, 工作线程数: {}", 
                   useVirtualThreads, config.getWorkerThreads());
    }

    /**
     * 注册消息处理器
     *
     * @param messageClass 消息类型
     * @param handler      消息处理器
     * @param <T>          消息类型参数
     */
    public <T> void registerHandler(Class<T> messageClass, MessageHandler<T> handler) {
        if (messageClass == null || handler == null) {
            throw new IllegalArgumentException("消息类型和处理器不能为null");
        }

        handlers.computeIfAbsent(messageClass, k -> new CopyOnWriteArrayList<>())
                .add(handler);

        logger.debug("注册消息处理器: {} -> {}", messageClass.getSimpleName(), handler.getClass().getSimpleName());
    }

    /**
     * 注册消息类型映射
     *
     * @param messageId    消息ID
     * @param messageClass 消息类型
     */
    public void registerMessageType(int messageId, Class<?> messageClass) {
        if (messageClass == null) {
            throw new IllegalArgumentException("消息类型不能为null");
        }

        Class<?> existing = messageTypeMapping.put(messageId, messageClass);
        if (existing != null) {
            logger.warn("消息ID重复注册: {} -> {} (覆盖: {})", 
                       messageId, messageClass.getSimpleName(), existing.getSimpleName());
        }

        logger.debug("注册消息类型映射: {} -> {}", messageId, messageClass.getSimpleName());
    }

    /**
     * 注销消息处理器
     *
     * @param messageClass 消息类型
     * @param handler      消息处理器
     * @param <T>          消息类型参数
     * @return true表示注销成功
     */
    public <T> boolean unregisterHandler(Class<T> messageClass, MessageHandler<T> handler) {
        if (messageClass == null || handler == null) {
            return false;
        }

        List<MessageHandler<?>> handlerList = handlers.get(messageClass);
        if (handlerList != null) {
            boolean removed = handlerList.remove(handler);
            if (handlerList.isEmpty()) {
                handlers.remove(messageClass);
            }
            
            if (removed) {
                logger.debug("注销消息处理器: {} -> {}", 
                           messageClass.getSimpleName(), handler.getClass().getSimpleName());
            }
            return removed;
        }

        return false;
    }

    /**
     * 设置默认处理器
     *
     * @param handler 默认处理器
     */
    public void setDefaultHandler(MessageHandler<Object> handler) {
        this.defaultHandler = handler;
        logger.debug("设置默认消息处理器: {}", 
                    handler != null ? handler.getClass().getSimpleName() : "null");
    }

    /**
     * 设置异常处理器
     *
     * @param handler 异常处理器
     */
    public void setExceptionHandler(MessageExceptionHandler handler) {
        this.exceptionHandler = handler;
        logger.debug("设置异常处理器: {}", 
                    handler != null ? handler.getClass().getSimpleName() : "null");
    }

    /**
     * 添加消息拦截器
     *
     * @param interceptor 拦截器
     */
    public void addInterceptor(MessageInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
            logger.debug("添加消息拦截器: {}", interceptor.getClass().getSimpleName());
        }
    }

    /**
     * 移除消息拦截器
     *
     * @param interceptor 拦截器
     * @return true表示移除成功
     */
    public boolean removeInterceptor(MessageInterceptor interceptor) {
        boolean removed = interceptors.remove(interceptor);
        if (removed) {
            logger.debug("移除消息拦截器: {}", interceptor.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * 分发消息（同步）
     *
     * @param message    消息对象
     * @param connection 连接对象
     */
    public void dispatch(Object message, Connection connection) {
        if (message == null) {
            logger.warn("尝试分发null消息");
            return;
        }

        long startTime = System.nanoTime();
        
        try {
            // 执行前置拦截器
            if (!executePreInterceptors(message, connection)) {
                logger.debug("消息被前置拦截器拒绝: {}", message.getClass().getSimpleName());
                return;
            }

            // 查找处理器并处理消息
            List<MessageHandler<?>> messageHandlers = findHandlers(message.getClass());
            if (messageHandlers.isEmpty() && defaultHandler == null) {
                logger.warn("没有找到消息处理器: {}", message.getClass().getSimpleName());
                return;
            }

            // 处理消息
            if (!messageHandlers.isEmpty()) {
                processWithHandlers(message, connection, messageHandlers);
            } else {
                processWithDefaultHandler(message, connection);
            }

            // 执行后置拦截器
            executePostInterceptors(message, connection);

            // 更新统计信息
            processedMessages.incrementAndGet();
            totalProcessingTime.addAndGet(System.nanoTime() - startTime);

        } catch (Exception e) {
            handleException(message, connection, e);
            failedMessages.incrementAndGet();
        }
    }

    /**
     * 分发消息（异步）
     *
     * @param message    消息对象
     * @param connection 连接对象
     * @return 处理结果的Future对象
     */
    public CompletableFuture<Void> dispatchAsync(Object message, Connection connection) {
        return CompletableFuture.runAsync(() -> dispatch(message, connection), executorService);
    }

    /**
     * 根据消息ID分发消息
     *
     * @param messageId  消息ID
     * @param data       消息数据
     * @param connection 连接对象
     * @return 处理结果的Future对象
     */
    public CompletableFuture<Void> dispatchById(int messageId, Object data, Connection connection) {
        Class<?> messageClass = messageTypeMapping.get(messageId);
        if (messageClass == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("未知的消息ID: " + messageId));
        }

        try {
            Object message = createMessage(messageClass, data);
            return dispatchAsync(message, connection);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 批量分发消息
     *
     * @param messages   消息列表
     * @param connection 连接对象
     * @return 处理结果的Future对象列表
     */
    public List<CompletableFuture<Void>> dispatchBatch(List<Object> messages, Connection connection) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream()
                      .map(message -> dispatchAsync(message, connection))
                      .toList();
    }

    /**
     * 获取已注册的消息类型
     *
     * @return 消息类型集合
     */
    public Set<Class<?>> getRegisteredMessageTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * 获取消息处理器数量
     *
     * @param messageClass 消息类型
     * @return 处理器数量
     */
    public int getHandlerCount(Class<?> messageClass) {
        List<MessageHandler<?>> handlerList = handlers.get(messageClass);
        return handlerList != null ? handlerList.size() : 0;
    }

    /**
     * 获取已处理的消息数
     *
     * @return 已处理的消息数
     */
    public long getProcessedMessageCount() {
        return processedMessages.get();
    }

    /**
     * 获取失败的消息数
     *
     * @return 失败的消息数
     */
    public long getFailedMessageCount() {
        return failedMessages.get();
    }

    /**
     * 获取平均处理时间（纳秒）
     *
     * @return 平均处理时间
     */
    public long getAverageProcessingTime() {
        long processed = processedMessages.get();
        return processed > 0 ? totalProcessingTime.get() / processed : 0;
    }

    /**
     * 关闭分发器
     */
    public void shutdown() {
        logger.info("关闭消息分发器");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("消息分发器未能在30秒内正常关闭，强制关闭");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            logger.warn("等待消息分发器关闭被中断");
        }

        logger.info("消息分发器关闭完成，已处理消息: {}, 失败消息: {}", 
                   processedMessages.get(), failedMessages.get());
    }

    /**
     * 创建执行器服务
     */
    private ExecutorService createExecutorService(MessageDispatcherConfig config) {
        if (useVirtualThreads) {
            // 使用Virtual Threads (Java 21+)
            // 注意：这里为了兼容Java 17，我们创建一个模拟的Virtual Thread执行器
            return Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r);
                thread.setName("message-dispatcher-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        } else {
            // 使用传统线程池
            return new ThreadPoolExecutor(
                config.getCoreThreads(),
                config.getMaxThreads(),
                config.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("message-dispatcher-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
        }
    }

    /**
     * 查找消息处理器
     */
    @SuppressWarnings("unchecked")
    private List<MessageHandler<?>> findHandlers(Class<?> messageClass) {
        List<MessageHandler<?>> result = new ArrayList<>();
        
        // 查找精确匹配的处理器
        List<MessageHandler<?>> exactHandlers = handlers.get(messageClass);
        if (exactHandlers != null) {
            result.addAll(exactHandlers);
        }

        // 查找父类和接口的处理器
        for (Map.Entry<Class<?>, List<MessageHandler<?>>> entry : handlers.entrySet()) {
            Class<?> handlerClass = entry.getKey();
            if (handlerClass != messageClass && handlerClass.isAssignableFrom(messageClass)) {
                result.addAll(entry.getValue());
            }
        }

        return result;
    }

    /**
     * 使用处理器列表处理消息
     */
    @SuppressWarnings("unchecked")
    private void processWithHandlers(Object message, Connection connection, 
                                   List<MessageHandler<?>> messageHandlers) {
        for (MessageHandler<?> handler : messageHandlers) {
            try {
                ((MessageHandler<Object>) handler).handle(message, connection);
            } catch (Exception e) {
                logger.warn("消息处理器执行失败: {}", handler.getClass().getSimpleName(), e);
                handleException(message, connection, e);
            }
        }
    }

    /**
     * 使用默认处理器处理消息
     */
    private void processWithDefaultHandler(Object message, Connection connection) {
        try {
            defaultHandler.handle(message, connection);
        } catch (Exception e) {
            logger.warn("默认消息处理器执行失败", e);
            handleException(message, connection, e);
        }
    }

    /**
     * 执行前置拦截器
     */
    private boolean executePreInterceptors(Object message, Connection connection) {
        for (MessageInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preHandle(message, connection)) {
                    return false;
                }
            } catch (Exception e) {
                logger.warn("前置拦截器执行失败: {}", interceptor.getClass().getSimpleName(), e);
                return false;
            }
        }
        return true;
    }

    /**
     * 执行后置拦截器
     */
    private void executePostInterceptors(Object message, Connection connection) {
        for (MessageInterceptor interceptor : interceptors) {
            try {
                interceptor.postHandle(message, connection);
            } catch (Exception e) {
                logger.warn("后置拦截器执行失败: {}", interceptor.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 处理异常
     */
    private void handleException(Object message, Connection connection, Exception e) {
        if (exceptionHandler != null) {
            try {
                exceptionHandler.handleException(message, connection, e);
            } catch (Exception ex) {
                logger.error("异常处理器执行失败", ex);
            }
        } else {
            logger.error("消息处理异常: {}", message.getClass().getSimpleName(), e);
        }
    }

    /**
     * 创建消息对象
     */
    private Object createMessage(Class<?> messageClass, Object data) throws Exception {
        // 简单实现：如果data就是目标类型的实例，直接返回
        if (messageClass.isInstance(data)) {
            return data;
        }
        
        // 否则尝试创建新实例（需要无参构造函数）
        return messageClass.getDeclaredConstructor().newInstance();
    }

    /**
     * 消息分发器配置接口
     */
    public interface MessageDispatcherConfig {
        int getCoreThreads();
        int getMaxThreads();
        int getWorkerThreads();
        long getKeepAliveTime();
        int getQueueCapacity();
        boolean isUseVirtualThreads();
    }

    /**
     * 默认消息分发器配置
     */
    public static class DefaultMessageDispatcherConfig implements MessageDispatcherConfig {
        private final int coreThreads;
        private final int maxThreads;
        private final long keepAliveTime;
        private final int queueCapacity;
        private final boolean useVirtualThreads;

        public DefaultMessageDispatcherConfig() {
            this(Runtime.getRuntime().availableProcessors(), 
                 Runtime.getRuntime().availableProcessors() * 2, 
                 60, 1000, true);
        }

        public DefaultMessageDispatcherConfig(int coreThreads, int maxThreads, 
                                            long keepAliveTime, int queueCapacity, 
                                            boolean useVirtualThreads) {
            this.coreThreads = coreThreads;
            this.maxThreads = maxThreads;
            this.keepAliveTime = keepAliveTime;
            this.queueCapacity = queueCapacity;
            this.useVirtualThreads = useVirtualThreads;
        }

        @Override
        public int getCoreThreads() { return coreThreads; }

        @Override
        public int getMaxThreads() { return maxThreads; }

        @Override
        public int getWorkerThreads() { return maxThreads; }

        @Override
        public long getKeepAliveTime() { return keepAliveTime; }

        @Override
        public int getQueueCapacity() { return queueCapacity; }

        @Override
        public boolean isUseVirtualThreads() { return useVirtualThreads; }
    }
}