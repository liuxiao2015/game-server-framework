/*
 * 文件名: BroadcastRouter.java
 * 用途: 广播路由策略实现
 * 实现内容:
 *   - 广播路由算法实现
 *   - 将消息发送到所有可用routee
 *   - 并发消息发送优化
 *   - 失败统计和监控
 * 技术选型:
 *   - 并行流处理提高性能
 *   - 批量消息发送
 *   - 故障隔离机制
 * 依赖关系:
 *   - 继承Router抽象类
 *   - 实现广播路由策略
 *   - 支持大规模routee广播
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;

import java.util.List;

/**
 * 广播路由器
 * <p>
 * 将消息广播发送给所有可用的routee。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class BroadcastRouter extends Router {
    
    /**
     * 构造函数
     *
     * @param name 路由器名称
     */
    public BroadcastRouter(String name) {
        super(name);
    }
    
    /**
     * 构造函数（默认名称）
     */
    public BroadcastRouter() {
        super("BroadcastRouter");
    }
    
    @Override
    public RouteResult route(Object message, ActorRef sender) {
        List<ActorRef> availableRoutees = getAvailableRoutees();
        
        if (availableRoutees.isEmpty()) {
            recordRouteFailure();
            return new RouteResult("没有可用的routee");
        }
        
        // 广播消息到所有可用的routee
        recordRouteSuccess();
        return new RouteResult(availableRoutees);
    }
}