/*
 * 文件名: RoundRobinRouter.java
 * 用途: 轮询路由策略实现
 * 实现内容:
 *   - 轮询路由算法实现
 *   - 均匀分发消息到所有routee
 *   - 线程安全的计数器管理
 *   - 自动跳过已终止的Actor
 * 技术选型:
 *   - 原子计数器保证线程安全
 *   - 取模运算实现轮询
 *   - 自动故障转移机制
 * 依赖关系:
 *   - 继承Router抽象类
 *   - 实现具体路由策略
 *   - 支持动态routee管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询路由器
 * <p>
 * 按照轮询方式将消息均匀分发给所有可用的routee。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class RoundRobinRouter extends Router {
    
    /** 当前索引 */
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    /**
     * 构造函数
     *
     * @param name 路由器名称
     */
    public RoundRobinRouter(String name) {
        super(name);
    }
    
    /**
     * 构造函数（默认名称）
     */
    public RoundRobinRouter() {
        super("RoundRobinRouter");
    }
    
    @Override
    public RouteResult route(Object message, ActorRef sender) {
        List<ActorRef> availableRoutees = getAvailableRoutees();
        
        if (availableRoutees.isEmpty()) {
            recordRouteFailure();
            return new RouteResult("没有可用的routee");
        }
        
        // 获取下一个routee
        ActorRef selectedRoutee = selectNextRoutee(availableRoutees);
        if (selectedRoutee == null) {
            recordRouteFailure();
            return new RouteResult("所有routee都已终止");
        }
        
        recordRouteSuccess();
        return new RouteResult(List.of(selectedRoutee));
    }
    
    /**
     * 选择下一个routee
     *
     * @param availableRoutees 可用的routee列表
     * @return 选中的routee
     */
    private ActorRef selectNextRoutee(List<ActorRef> availableRoutees) {
        if (availableRoutees.isEmpty()) {
            return null;
        }
        
        int size = availableRoutees.size();
        int attempts = 0;
        
        // 最多尝试size次，避免无限循环
        while (attempts < size) {
            int index = currentIndex.getAndIncrement() % size;
            ActorRef routee = availableRoutees.get(index);
            
            if (!routee.isTerminated()) {
                return routee;
            }
            
            attempts++;
        }
        
        return null;
    }
    
    /**
     * 重置索引
     */
    public void resetIndex() {
        currentIndex.set(0);
        logger.debug("重置轮询索引");
    }
    
    /**
     * 获取当前索引
     *
     * @return 当前索引
     */
    public int getCurrentIndex() {
        return currentIndex.get();
    }
}