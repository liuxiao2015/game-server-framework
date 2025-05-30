/*
 * 文件名: RouterConfig.java
 * 用途: 路由器配置类
 * 实现内容:
 *   - 路由器配置参数定义
 *   - Routee数量和监督策略配置
 *   - 调度器和动态伸缩配置
 *   - 建造者模式提供灵活配置
 * 技术选型:
 *   - 不可变对象设计
 *   - 建造者模式
 *   - 配置验证机制
 * 依赖关系:
 *   - 被Router创建时使用
 *   - 与监督策略集成
 *   - 支持动态配置更新
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.supervision.SupervisorStrategy;

/**
 * 路由器配置类
 * <p>
 * 定义了创建和配置路由器所需的所有参数。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class RouterConfig {
    
    /** 路由策略类型 */
    private final RouterType routerType;
    
    /** 初始routee数量 */
    private final int initialRoutees;
    
    /** 最大routee数量 */
    private final int maxRoutees;
    
    /** 最小routee数量 */
    private final int minRoutees;
    
    /** 监督策略 */
    private final SupervisorStrategy supervisorStrategy;
    
    /** 调度器名称 */
    private final String dispatcher;
    
    /** 是否启用动态伸缩 */
    private final boolean enableAutoScaling;
    
    /** 扩容阈值（消息队列长度） */
    private final int scaleUpThreshold;
    
    /** 缩容阈值（空闲时间，秒） */
    private final int scaleDownThreshold;
    
    /**
     * 私有构造函数
     *
     * @param builder 构建器
     */
    private RouterConfig(Builder builder) {
        this.routerType = builder.routerType;
        this.initialRoutees = builder.initialRoutees;
        this.maxRoutees = builder.maxRoutees;
        this.minRoutees = builder.minRoutees;
        this.supervisorStrategy = builder.supervisorStrategy;
        this.dispatcher = builder.dispatcher;
        this.enableAutoScaling = builder.enableAutoScaling;
        this.scaleUpThreshold = builder.scaleUpThreshold;
        this.scaleDownThreshold = builder.scaleDownThreshold;
    }
    
    /**
     * 创建默认配置
     *
     * @param routerType 路由器类型
     * @return 默认配置
     */
    public static RouterConfig create(RouterType routerType) {
        return new Builder(routerType).build();
    }
    
    /**
     * 创建构建器
     *
     * @param routerType 路由器类型
     * @return 构建器实例
     */
    public static Builder builder(RouterType routerType) {
        return new Builder(routerType);
    }
    
    // Getters
    public RouterType getRouterType() {
        return routerType;
    }
    
    public int getInitialRoutees() {
        return initialRoutees;
    }
    
    public int getMaxRoutees() {
        return maxRoutees;
    }
    
    public int getMinRoutees() {
        return minRoutees;
    }
    
    public SupervisorStrategy getSupervisorStrategy() {
        return supervisorStrategy;
    }
    
    public String getDispatcher() {
        return dispatcher;
    }
    
    public boolean isEnableAutoScaling() {
        return enableAutoScaling;
    }
    
    public int getScaleUpThreshold() {
        return scaleUpThreshold;
    }
    
    public int getScaleDownThreshold() {
        return scaleDownThreshold;
    }
    
    @Override
    public String toString() {
        return String.format("RouterConfig{type=%s, routees=%d-%d-%d, autoScaling=%s}", 
                routerType, minRoutees, initialRoutees, maxRoutees, enableAutoScaling);
    }
    
    /**
     * 路由器类型枚举
     */
    public enum RouterType {
        /** 轮询路由 */
        ROUND_ROBIN,
        /** 随机路由 */
        RANDOM,
        /** 一致性哈希路由 */
        CONSISTENT_HASH,
        /** 广播路由 */
        BROADCAST,
        /** 分散收集路由 */
        SCATTER_GATHER,
        /** 最小邮箱路由 */
        SMALLEST_MAILBOX,
        /** 自定义路由 */
        CUSTOM
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private final RouterType routerType;
        private int initialRoutees = 1;
        private int maxRoutees = 10;
        private int minRoutees = 1;
        private SupervisorStrategy supervisorStrategy = new SupervisorStrategy.OneForOneStrategy();
        private String dispatcher = "default";
        private boolean enableAutoScaling = false;
        private int scaleUpThreshold = 100;
        private int scaleDownThreshold = 300;
        
        private Builder(RouterType routerType) {
            this.routerType = routerType;
        }
        
        public Builder withInitialRoutees(int initialRoutees) {
            this.initialRoutees = Math.max(1, initialRoutees);
            return this;
        }
        
        public Builder withMaxRoutees(int maxRoutees) {
            this.maxRoutees = Math.max(1, maxRoutees);
            return this;
        }
        
        public Builder withMinRoutees(int minRoutees) {
            this.minRoutees = Math.max(1, minRoutees);
            return this;
        }
        
        public Builder withSupervisorStrategy(SupervisorStrategy supervisorStrategy) {
            this.supervisorStrategy = supervisorStrategy;
            return this;
        }
        
        public Builder withDispatcher(String dispatcher) {
            this.dispatcher = dispatcher;
            return this;
        }
        
        public Builder withAutoScaling(boolean enable) {
            this.enableAutoScaling = enable;
            return this;
        }
        
        public Builder withScaleUpThreshold(int threshold) {
            this.scaleUpThreshold = Math.max(1, threshold);
            return this;
        }
        
        public Builder withScaleDownThreshold(int threshold) {
            this.scaleDownThreshold = Math.max(1, threshold);
            return this;
        }
        
        public RouterConfig build() {
            // 验证配置
            if (minRoutees > maxRoutees) {
                throw new IllegalArgumentException("minRoutees不能大于maxRoutees");
            }
            if (initialRoutees < minRoutees || initialRoutees > maxRoutees) {
                throw new IllegalArgumentException("initialRoutees必须在min和max之间");
            }
            
            return new RouterConfig(this);
        }
    }
}