/*
 * 文件名: LocalActorRef.java
 * 用途: 本地Actor引用实现
 * 实现内容:
 *   - ActorRef接口的本地实现
 *   - 消息发送和处理逻辑
 *   - Actor生命周期管理
 *   - 与邮箱和调度器集成
 * 技术选型:
 *   - 本地Actor引用实现
 *   - 异步消息处理机制
 *   - 与ActorSystem集成
 * 依赖关系:
 *   - 实现ActorRef接口
 *   - 与Actor、ActorMailbox、Dispatcher协作
 *   - 被ActorSystem管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import com.lx.gameserver.frame.actor.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地Actor引用实现
 * <p>
 * ActorRef接口的本地实现，负责消息发送、处理和
 * Actor生命周期管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LocalActorRef implements ActorRef {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalActorRef.class);
    
    /** Actor路径 */
    private final String path;
    
    /** Actor名称 */
    private final String name;
    
    /** Actor实例 */
    private final AtomicReference<Actor> actorInstance = new AtomicReference<>();
    
    /** Actor邮箱 */
    private final ActorMailbox mailbox;
    
    /** Actor系统引用 */
    private final GameActorSystem actorSystem;
    
    /** 消息调度器 */
    private final Dispatcher dispatcher;
    
    /** Actor上下文 */
    private final LocalActorContext context;
    
    /** Actor状态 */
    private volatile Actor.ActorState state = Actor.ActorState.CREATED;
    
    /** 待处理的ask请求 */
    private final ConcurrentHashMap<String, CompletableFuture<Object>> pendingAskRequests = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     *
     * @param path         Actor路径
     * @param name         Actor名称
     * @param mailbox      Actor邮箱
     * @param actorSystem  Actor系统
     * @param dispatcher   消息调度器
     */
    public LocalActorRef(String path, String name, ActorMailbox mailbox, 
                        GameActorSystem actorSystem, Dispatcher dispatcher) {
        this.path = path;
        this.name = name;
        this.mailbox = mailbox;
        this.actorSystem = actorSystem;
        this.dispatcher = dispatcher;
        this.context = new LocalActorContext(this, actorSystem);
    }
    
    /**
     * 设置Actor实例
     *
     * @param actor Actor实例
     */
    public void setActor(Actor actor) {
        if (actorInstance.compareAndSet(null, actor)) {
            actor.setContext(context);
            logger.debug("Actor实例设置完成: {}", path);
        } else {
            throw new IllegalStateException("Actor实例已设置");
        }
    }
    
    /**
     * 获取Actor实例
     *
     * @return Actor实例
     */
    public Actor getActor() {
        return actorInstance.get();
    }
    
    /**
     * 获取Actor邮箱
     *
     * @return Actor邮箱
     */
    public ActorMailbox getMailbox() {
        return mailbox;
    }
    
    /**
     * 获取Actor上下文
     *
     * @return Actor上下文
     */
    public LocalActorContext getContext() {
        return context;
    }
    
    @Override
    public void tell(Object message, ActorRef sender) {
        if (isTerminated()) {
            logger.warn("Actor[{}]已终止，消息被发送到死信队列: {}", path, message);
            actorSystem.deadLetters().tell(message, sender);
            return;
        }
        
        MessageEnvelope envelope = new MessageEnvelope(message, sender, this);
        
        if (!mailbox.offer(envelope)) {
            logger.warn("Actor[{}]邮箱已满，消息被发送到死信队列: {}", path, message);
            actorSystem.deadLetters().tell(message, sender);
            return;
        }
        
        // 调度消息处理
        dispatcher.execute(this, mailbox);
    }
    
    @Override
    public <T> CompletableFuture<T> ask(Object message, Duration timeout) {
        if (isTerminated()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Actor已终止: " + path));
        }
        
        // 创建ask请求消息
        String requestId = java.util.UUID.randomUUID().toString();
        AskRequest askRequest = new AskRequest(requestId, message);
        
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingAskRequests.put(requestId, future);
        
        // 设置超时处理
        dispatcher.schedule(() -> {
            CompletableFuture<Object> removed = pendingAskRequests.remove(requestId);
            if (removed != null && !removed.isDone()) {
                removed.completeExceptionally(
                    new java.util.concurrent.TimeoutException("Ask请求超时: " + timeout));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        
        // 发送消息
        tell(askRequest, ActorRef.noSender());
        
        return future.thenApply(result -> (T) result);
    }
    
    @Override
    public void forward(Object message, ActorRef target) {
        target.tell(message, this);
    }
    
    @Override
    public void tellWithPriority(Object message, ActorRef sender, int priority) {
        if (isTerminated()) {
            logger.warn("Actor[{}]已终止，消息被发送到死信队列: {}", path, message);
            actorSystem.deadLetters().tell(message, sender);
            return;
        }
        
        MessageEnvelope envelope = new MessageEnvelope(message, sender, this, priority);
        
        if (!mailbox.offer(envelope)) {
            logger.warn("Actor[{}]邮箱已满，消息被发送到死信队列: {}", path, message);
            actorSystem.deadLetters().tell(message, sender);
            return;
        }
        
        dispatcher.execute(this, mailbox);
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public ActorSystem getActorSystem() {
        return actorSystem;
    }
    
    @Override
    public boolean isTerminated() {
        return state == Actor.ActorState.STOPPED || state == Actor.ActorState.FAILED;
    }
    
    /**
     * 处理消息（由Dispatcher调用）
     *
     * @param envelope 消息信封
     */
    public void processMessage(MessageEnvelope envelope) {
        Actor actor = actorInstance.get();
        if (actor == null) {
            logger.error("Actor实例为null，无法处理消息: {}", envelope);
            return;
        }
        
        try {
            // 设置当前消息上下文
            context.setCurrentSender(envelope.getSender());
            
            Object message = envelope.getMessage();
            
            // 处理ask请求的响应
            if (message instanceof AskResponse response) {
                handleAskResponse(response);
                return;
            }
            
            // 处理ask请求
            if (message instanceof AskRequest askRequest) {
                handleAskRequest(askRequest, envelope.getSender());
                return;
            }
            
            // 处理普通消息
            Receive receive = actor.createReceive();
            boolean handled = receive.apply(message);
            
            if (!handled) {
                actor.unhandled(message);
            }
            
        } catch (Exception e) {
            logger.error("Actor[{}]处理消息失败: {}", path, envelope, e);
            handleActorException(e, envelope.getMessage());
        } finally {
            context.setCurrentSender(null);
        }
    }
    
    /**
     * 处理ask请求
     *
     * @param askRequest ask请求
     * @param sender     发送者
     */
    private void handleAskRequest(AskRequest askRequest, ActorRef sender) {
        Actor actor = actorInstance.get();
        if (actor == null) return;
        
        try {
            Receive receive = actor.createReceive();
            boolean handled = receive.apply(askRequest.getMessage());
            
            if (!handled) {
                // 发送未处理响应
                AskResponse response = new AskResponse(askRequest.getRequestId(), 
                    new UnsupportedOperationException("消息未处理: " + askRequest.getMessage()));
                sender.tell(response, this);
            }
            
        } catch (Exception e) {
            // 发送错误响应
            AskResponse response = new AskResponse(askRequest.getRequestId(), e);
            sender.tell(response, this);
        }
    }
    
    /**
     * 处理ask响应
     *
     * @param response ask响应
     */
    private void handleAskResponse(AskResponse response) {
        CompletableFuture<Object> future = pendingAskRequests.remove(response.getRequestId());
        if (future != null) {
            if (response.isSuccess()) {
                future.complete(response.getResult());
            } else {
                future.completeExceptionally(response.getException());
            }
        }
    }
    
    /**
     * 处理Actor异常
     *
     * @param exception 异常
     * @param message   导致异常的消息
     */
    private void handleActorException(Exception exception, Object message) {
        Actor actor = actorInstance.get();
        if (actor != null) {
            actor.supervisorStrategy(exception, message);
        }
        
        // 这里可以根据监督策略进行处理
        // 暂时只记录日志
        logger.error("Actor[{}]异常处理完成", path);
    }
    
    /**
     * 启动Actor
     */
    public void start() {
        if (state != Actor.ActorState.CREATED) {
            return;
        }
        
        Actor actor = actorInstance.get();
        if (actor != null) {
            state = Actor.ActorState.STARTING;
            try {
                actor.preStart();
                state = Actor.ActorState.RUNNING;
                actor.setState(state);
                logger.debug("Actor[{}]启动完成", path);
            } catch (Exception e) {
                state = Actor.ActorState.FAILED;
                actor.setState(state);
                logger.error("Actor[{}]启动失败", path, e);
            }
        }
    }
    
    /**
     * 停止Actor
     */
    public void stop() {
        if (isTerminated()) {
            return;
        }
        
        state = Actor.ActorState.STOPPING;
        Actor actor = actorInstance.get();
        if (actor != null) {
            actor.setState(state);
            try {
                actor.postStop();
            } catch (Exception e) {
                logger.error("Actor[{}]停止过程中发生异常", path, e);
            }
        }
        
        state = Actor.ActorState.STOPPED;
        if (actor != null) {
            actor.setState(state);
        }
        
        // 清理邮箱
        mailbox.close();
        
        // 取消所有待处理的ask请求
        pendingAskRequests.values().forEach(future -> 
            future.completeExceptionally(new IllegalStateException("Actor已停止")));
        pendingAskRequests.clear();
        
        logger.debug("Actor[{}]停止完成", path);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        LocalActorRef that = (LocalActorRef) obj;
        return path.equals(that.path);
    }
    
    @Override
    public int hashCode() {
        return path.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("Actor[%s]", path);
    }
    
    /**
     * Ask请求消息
     */
    private static class AskRequest {
        private final String requestId;
        private final Object message;
        
        public AskRequest(String requestId, Object message) {
            this.requestId = requestId;
            this.message = message;
        }
        
        public String getRequestId() {
            return requestId;
        }
        
        public Object getMessage() {
            return message;
        }
    }
    
    /**
     * Ask响应消息
     */
    private static class AskResponse {
        private final String requestId;
        private final Object result;
        private final Exception exception;
        
        public AskResponse(String requestId, Object result) {
            this.requestId = requestId;
            this.result = result;
            this.exception = null;
        }
        
        public AskResponse(String requestId, Exception exception) {
            this.requestId = requestId;
            this.result = null;
            this.exception = exception;
        }
        
        public String getRequestId() {
            return requestId;
        }
        
        public Object getResult() {
            return result;
        }
        
        public Exception getException() {
            return exception;
        }
        
        public boolean isSuccess() {
            return exception == null;
        }
    }
}