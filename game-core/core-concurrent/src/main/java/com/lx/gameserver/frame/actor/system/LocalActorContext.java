/*
 * 文件名: LocalActorContext.java
 * 用途: 本地Actor上下文实现
 * 实现内容:
 *   - ActorContext接口的本地实现
 *   - Actor层级管理和子Actor创建
 *   - 调度器访问和定时任务支持
 *   - 监督策略和行为切换
 * 技术选型:
 *   - 本地上下文实现
 *   - 与ActorSystem集成
 *   - 支持生命周期管理
 * 依赖关系:
 *   - 实现ActorContext接口
 *   - 与LocalActorRef和GameActorSystem协作
 *   - 支持监督和调度功能
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import com.lx.gameserver.frame.actor.core.*;
import com.lx.gameserver.frame.actor.supervision.SupervisorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地Actor上下文实现
 * <p>
 * ActorContext接口的本地实现，为Actor提供运行时环境
 * 和系统功能访问。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class LocalActorContext implements ActorContext {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalActorContext.class);
    
    /** Actor引用 */
    private final LocalActorRef actorRef;
    
    /** Actor系统 */
    private final GameActorSystem actorSystem;
    
    /** 子Actor列表 */
    private final List<ActorRef> children = new CopyOnWriteArrayList<>();
    
    /** 当前消息的发送者 */
    private volatile ActorRef currentSender;
    
    /** 监督策略 */
    private volatile SupervisorStrategy supervisorStrategy;
    
    /** 行为栈（用于become/unbecome） */
    private final List<Receive> behaviorStack = new CopyOnWriteArrayList<>();
    
    /** 子Actor计数器 */
    private final AtomicInteger childCounter = new AtomicInteger(0);
    
    /**
     * 构造函数
     *
     * @param actorRef    Actor引用
     * @param actorSystem Actor系统
     */
    public LocalActorContext(LocalActorRef actorRef, GameActorSystem actorSystem) {
        this.actorRef = actorRef;
        this.actorSystem = actorSystem;
    }
    
    @Override
    public ActorRef getSelf() {
        return actorRef;
    }
    
    @Override
    public ActorRef getParent() {
        // 从路径解析父Actor
        String path = actorRef.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            String parentPath = path.substring(0, lastSlash);
            return actorSystem.getActorRef(parentPath);
        }
        return null;
    }
    
    @Override
    public List<ActorRef> getChildren() {
        return List.copyOf(children);
    }
    
    @Override
    public ActorRef getChild(String name) {
        return children.stream()
                .filter(child -> name.equals(child.getName()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public ActorRef actorOf(ActorProps props, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Actor名称不能为空");
        }
        
        // 检查名称是否已存在
        if (getChild(name) != null) {
            throw new IllegalArgumentException("Actor名称已存在: " + name);
        }
        
        // 生成子Actor路径
        String childPath = actorRef.getPath() + "/" + name;
        
        // 创建子Actor
        ActorRef childRef = actorSystem.actorOf(props, childPath);
        children.add(childRef);
        
        logger.debug("创建子Actor: {}", childPath);
        return childRef;
    }
    
    @Override
    public ActorRef actorOf(ActorProps props) {
        // 自动生成名称
        String name = "child-" + childCounter.incrementAndGet();
        return actorOf(props, name);
    }
    
    @Override
    public void stop(ActorRef child) {
        if (children.remove(child)) {
            actorSystem.stop(child);
            logger.debug("停止子Actor: {}", child.getPath());
        }
    }
    
    @Override
    public void stop() {
        actorSystem.stop(actorRef);
    }
    
    @Override
    public ActorSystem getSystem() {
        return actorSystem;
    }
    
    @Override
    public ActorRef getSender() {
        return currentSender != null ? currentSender : ActorRef.noSender();
    }
    
    /**
     * 设置当前消息的发送者（内部使用）
     *
     * @param sender 发送者引用
     */
    public void setCurrentSender(ActorRef sender) {
        this.currentSender = sender;
    }
    
    @Override
    public Cancellable schedule(Duration delay, ActorRef receiver, Object message) {
        ScheduledFuture<?> future = actorSystem.getDispatcher().schedule(
                () -> receiver.tell(message, actorRef),
                delay.toMillis(),
                TimeUnit.MILLISECONDS
        );
        
        return new ScheduledCancellable(future);
    }
    
    @Override
    public Cancellable scheduleAtFixedRate(Duration initialDelay, Duration interval, ActorRef receiver, Object message) {
        ScheduledFuture<?> future = actorSystem.getDispatcher().scheduleAtFixedRate(
                () -> receiver.tell(message, actorRef),
                initialDelay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );
        
        return new ScheduledCancellable(future);
    }
    
    @Override
    public void setSupervisorStrategy(SupervisorStrategy strategy) {
        this.supervisorStrategy = strategy;
    }
    
    @Override
    public SupervisorStrategy getSupervisorStrategy() {
        return supervisorStrategy;
    }
    
    @Override
    public void watch(ActorRef actorRef) {
        // TODO: 实现Actor监视功能
        logger.debug("开始监视Actor: {}", actorRef);
    }
    
    @Override
    public void unwatch(ActorRef actorRef) {
        // TODO: 实现取消监视功能
        logger.debug("取消监视Actor: {}", actorRef);
    }
    
    @Override
    public void become(Receive behavior) {
        behaviorStack.add(behavior);
        logger.debug("切换到新行为，行为栈深度: {}", behaviorStack.size());
    }
    
    @Override
    public void unbecome() {
        if (!behaviorStack.isEmpty()) {
            behaviorStack.remove(behaviorStack.size() - 1);
            logger.debug("恢复前一个行为，行为栈深度: {}", behaviorStack.size());
        }
    }
    
    /**
     * 获取当前行为
     *
     * @return 当前行为，如果行为栈为空返回null
     */
    public Receive getCurrentBehavior() {
        return behaviorStack.isEmpty() ? null : behaviorStack.get(behaviorStack.size() - 1);
    }
    
    /**
     * 可取消任务的实现
     */
    private static class ScheduledCancellable implements Cancellable {
        private final ScheduledFuture<?> future;
        
        public ScheduledCancellable(ScheduledFuture<?> future) {
            this.future = future;
        }
        
        @Override
        public boolean cancel() {
            return future.cancel(false);
        }
        
        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }
    }
}