/*
 * 文件名: Router.java
 * 用途: 路由器抽象类和接口
 * 实现内容:
 *   - 路由器抽象类定义
 *   - 管理多个routee（目标Actor）
 *   - 消息分发策略抽象
 *   - 动态routee管理和路由监控
 * 技术选型:
 *   - 抽象类设计提供基础实现
 *   - 策略模式支持不同路由算法
 *   - 并发安全的routee管理
 * 依赖关系:
 *   - 与ActorRef协作进行消息路由
 *   - 被具体路由策略实现
 *   - 支持动态负载均衡
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由器抽象类
 * <p>
 * 定义了路由器的基本行为，包括routee管理和消息路由功能。
 * 子类需要实现具体的路由策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class Router {
    
    protected static final Logger logger = LoggerFactory.getLogger(Router.class);
    
    /** 路由器名称 */
    protected final String name;
    
    /** Routee列表 */
    protected final List<ActorRef> routees = new CopyOnWriteArrayList<>();
    
    /** 消息计数器 */
    protected final AtomicLong messageCount = new AtomicLong(0);
    
    /** 路由失败计数 */
    protected final AtomicLong routeFailureCount = new AtomicLong(0);
    
    /**
     * 构造函数
     *
     * @param name 路由器名称
     */
    protected Router(String name) {
        this.name = name;
    }
    
    /**
     * 路由消息（抽象方法，由子类实现具体策略）
     *
     * @param message 要路由的消息
     * @param sender  发送者引用
     * @return 路由结果
     */
    public abstract RouteResult route(Object message, ActorRef sender);
    
    /**
     * 添加routee
     *
     * @param actorRef routee Actor引用
     */
    public void addRoutee(ActorRef actorRef) {
        if (actorRef != null && !routees.contains(actorRef)) {
            routees.add(actorRef);
            logger.debug("路由器[{}]添加routee: {}", name, actorRef);
            onRouteesChanged();
        }
    }

    /**
     * 移除routee
     *
     * @param actorRef routee Actor引用
     */
    public void removeRoutee(ActorRef actorRef) {
        if (routees.remove(actorRef)) {
            logger.debug("路由器[{}]移除routee: {}", name, actorRef);
            onRouteesChanged();
        }
    }
    
    /**
     * 获取所有routee
     *
     * @return routee列表的副本
     */
    public List<ActorRef> getRoutees() {
        return List.copyOf(routees);
    }
    
    /**
     * 获取routee数量
     *
     * @return routee数量
     */
    public int getRouteeCount() {
        return routees.size();
    }
    
    /**
     * 检查是否有可用的routee
     *
     * @return 如果有可用的routee返回true
     */
    public boolean hasRoutees() {
        return !routees.isEmpty();
    }
    
    /**
     * 过滤可用的routee
     *
     * @return 可用的routee列表
     */
    protected List<ActorRef> getAvailableRoutees() {
        return routees.stream()
                .filter(actorRef -> !actorRef.isTerminated())
                .toList();
    }
    
    /**
     * 获取路由器名称
     *
     * @return 路由器名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取消息计数
     *
     * @return 消息计数
     */
    public long getMessageCount() {
        return messageCount.get();
    }
    
    /**
     * 获取路由失败计数
     *
     * @return 路由失败计数
     */
    public long getRouteFailureCount() {
        return routeFailureCount.get();
    }
    
    /**
     * 记录路由成功
     */
    protected void recordRouteSuccess() {
        messageCount.incrementAndGet();
    }
    
    /**
     * 记录路由失败
     */
    protected void recordRouteFailure() {
        routeFailureCount.incrementAndGet();
        messageCount.incrementAndGet();
    }
    
    /**
     * routee变化时的回调方法
     * 子类可以重写此方法来响应routee的变化
     */
    protected void onRouteesChanged() {
        // 默认实现为空，子类可以重写
        logger.debug("路由器[{}]的routee发生变化，当前数量: {}", name, routees.size());
    }

    /**
     * 获取路由统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format("Router[%s] - Routees: %d, Messages: %d, Failures: %d", 
                name, routees.size(), messageCount.get(), routeFailureCount.get());
    }
    
    @Override
    public String toString() {
        return String.format("Router{name=%s, routees=%d}", name, routees.size());
    }
    
    /**
     * 路由结果类
     */
    public static class RouteResult {
        private final List<ActorRef> targetActors;
        private final boolean success;
        private final String failureReason;
        
        /**
         * 成功路由结果
         *
         * @param targetActors 目标Actor列表
         */
        public RouteResult(List<ActorRef> targetActors) {
            this.targetActors = targetActors;
            this.success = true;
            this.failureReason = null;
        }
        
        /**
         * 失败路由结果
         *
         * @param failureReason 失败原因
         */
        public RouteResult(String failureReason) {
            this.targetActors = List.of();
            this.success = false;
            this.failureReason = failureReason;
        }
        
        /**
         * 获取目标Actor列表
         *
         * @return 目标Actor列表
         */
        public List<ActorRef> getTargetActors() {
            return targetActors;
        }
        
        /**
         * 检查路由是否成功
         *
         * @return 如果成功返回true
         */
        public boolean isSuccess() {
            return success;
        }
        
        /**
         * 获取失败原因
         *
         * @return 失败原因
         */
        public String getFailureReason() {
            return failureReason;
        }
        
        /**
         * 检查是否有目标Actor
         *
         * @return 如果有目标Actor返回true
         */
        public boolean hasTargets() {
            return !targetActors.isEmpty();
        }
        
        /**
         * 获取目标Actor数量
         *
         * @return 目标Actor数量
         */
        public int getTargetCount() {
            return targetActors.size();
        }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("RouteResult{success=true, targets=%d}", targetActors.size());
            } else {
                return String.format("RouteResult{success=false, reason=%s}", failureReason);
            }
        }
    }
}