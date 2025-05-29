/*
 * 文件名: RandomRouter.java
 * 用途: 随机路由策略实现
 * 实现内容:
 *   - 随机路由算法实现
 *   - 随机选择可用的routee
 *   - 线程安全的随机数生成
 *   - 自动跳过已终止的Actor
 * 技术选型:
 *   - ThreadLocalRandom保证线程安全
 *   - 随机索引选择算法
 *   - 故障自动转移机制
 * 依赖关系:
 *   - 继承Router抽象类
 *   - 实现随机路由策略
 *   - 支持动态routee管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机路由器
 * <p>
 * 随机选择一个可用的routee来处理消息。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class RandomRouter extends Router {
    
    /**
     * 构造函数
     *
     * @param name 路由器名称
     */
    public RandomRouter(String name) {
        super(name);
    }
    
    /**
     * 构造函数（默认名称）
     */
    public RandomRouter() {
        super("RandomRouter");
    }
    
    @Override
    public RouteResult route(Object message, ActorRef sender) {
        List<ActorRef> availableRoutees = getAvailableRoutees();
        
        if (availableRoutees.isEmpty()) {
            recordRouteFailure();
            return new RouteResult("没有可用的routee");
        }
        
        // 随机选择routee
        ActorRef selectedRoutee = selectRandomRoutee(availableRoutees);
        if (selectedRoutee == null) {
            recordRouteFailure();
            return new RouteResult("所有routee都已终止");
        }
        
        recordRouteSuccess();
        return new RouteResult(List.of(selectedRoutee));
    }
    
    /**
     * 随机选择routee
     *
     * @param availableRoutees 可用的routee列表
     * @return 选中的routee
     */
    private ActorRef selectRandomRoutee(List<ActorRef> availableRoutees) {
        if (availableRoutees.isEmpty()) {
            return null;
        }
        
        int size = availableRoutees.size();
        int attempts = 0;
        
        // 最多尝试size次，避免无限循环
        while (attempts < size) {
            int randomIndex = ThreadLocalRandom.current().nextInt(size);
            ActorRef routee = availableRoutees.get(randomIndex);
            
            if (!routee.isTerminated()) {
                return routee;
            }
            
            attempts++;
        }
        
        return null;
    }
}