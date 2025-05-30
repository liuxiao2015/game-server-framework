/*
 * 文件名: ActorProps.java
 * 用途: Actor属性配置类
 * 实现内容:
 *   - 定义Actor创建时的配置参数
 *   - 支持Actor类型、构造参数、调度器配置
 *   - 支持监督策略和邮箱配置
 * 技术选型:
 *   - 不可变对象设计保证线程安全
 *   - 建造者模式提供灵活配置
 *   - 支持函数式创建方式
 * 依赖关系:
 *   - 被ActorContext用于创建Actor
 *   - 与调度器和邮箱配置集成
 *   - 支持监督策略配置
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.core;

import com.lx.gameserver.frame.actor.supervision.SupervisorStrategy;
import java.util.function.Supplier;

/**
 * Actor属性配置类
 * <p>
 * 定义了创建Actor所需的所有配置信息，包括Actor类、
 * 构造参数、调度器、邮箱等配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class ActorProps {
    
    private final Class<? extends Actor> actorClass;
    private final Supplier<Actor> actorSupplier;
    private final Object[] args;
    private final String dispatcher;
    private final String mailboxType;
    private final SupervisorStrategy supervisorStrategy;
    private final int mailboxCapacity;
    
    private ActorProps(Builder builder) {
        this.actorClass = builder.actorClass;
        this.actorSupplier = builder.actorSupplier;
        this.args = builder.args != null ? builder.args.clone() : new Object[0];
        this.dispatcher = builder.dispatcher;
        this.mailboxType = builder.mailboxType;
        this.supervisorStrategy = builder.supervisorStrategy;
        this.mailboxCapacity = builder.mailboxCapacity;
    }
    
    /**
     * 创建基于类的Props
     *
     * @param actorClass Actor类
     * @param args       构造参数
     * @return Props实例
     */
    public static ActorProps create(Class<? extends Actor> actorClass, Object... args) {
        return new Builder(actorClass).withArgs(args).build();
    }
    
    /**
     * 创建基于Supplier的Props
     *
     * @param actorSupplier Actor供应商
     * @return Props实例
     */
    public static ActorProps create(Supplier<Actor> actorSupplier) {
        return new Builder(actorSupplier).build();
    }
    
    /**
     * 创建构建器
     *
     * @param actorClass Actor类
     * @return 构建器实例
     */
    public static Builder builder(Class<? extends Actor> actorClass) {
        return new Builder(actorClass);
    }
    
    /**
     * 创建构建器
     *
     * @param actorSupplier Actor供应商
     * @return 构建器实例
     */
    public static Builder builder(Supplier<Actor> actorSupplier) {
        return new Builder(actorSupplier);
    }
    
    // Getters
    public Class<? extends Actor> getActorClass() {
        return actorClass;
    }
    
    public Supplier<Actor> getActorSupplier() {
        return actorSupplier;
    }
    
    public Object[] getArgs() {
        return args.clone();
    }
    
    public String getDispatcher() {
        return dispatcher;
    }
    
    public String getMailboxType() {
        return mailboxType;
    }
    
    public SupervisorStrategy getSupervisorStrategy() {
        return supervisorStrategy;
    }
    
    public int getMailboxCapacity() {
        return mailboxCapacity;
    }
    
    /**
     * 创建新的Props，使用不同的调度器
     *
     * @param dispatcher 调度器名称
     * @return 新的Props实例
     */
    public ActorProps withDispatcher(String dispatcher) {
        return new Builder(this).withDispatcher(dispatcher).build();
    }
    
    /**
     * 创建新的Props，使用不同的邮箱类型
     *
     * @param mailboxType 邮箱类型
     * @return 新的Props实例
     */
    public ActorProps withMailbox(String mailboxType) {
        return new Builder(this).withMailbox(mailboxType).build();
    }
    
    /**
     * 建造者类
     */
    public static class Builder {
        private Class<? extends Actor> actorClass;
        private Supplier<Actor> actorSupplier;
        private Object[] args = new Object[0];
        private String dispatcher = "default";
        private String mailboxType = "unbounded";
        private SupervisorStrategy supervisorStrategy;
        private int mailboxCapacity = 1000;
        
        private Builder(Class<? extends Actor> actorClass) {
            this.actorClass = actorClass;
        }
        
        private Builder(Supplier<Actor> actorSupplier) {
            this.actorSupplier = actorSupplier;
        }
        
        private Builder(ActorProps props) {
            this.actorClass = props.actorClass;
            this.actorSupplier = props.actorSupplier;
            this.args = props.args.clone();
            this.dispatcher = props.dispatcher;
            this.mailboxType = props.mailboxType;
            this.supervisorStrategy = props.supervisorStrategy;
            this.mailboxCapacity = props.mailboxCapacity;
        }
        
        public Builder withArgs(Object... args) {
            this.args = args != null ? args.clone() : new Object[0];
            return this;
        }
        
        public Builder withDispatcher(String dispatcher) {
            this.dispatcher = dispatcher;
            return this;
        }
        
        public Builder withMailbox(String mailboxType) {
            this.mailboxType = mailboxType;
            return this;
        }
        
        public Builder withMailboxCapacity(int capacity) {
            this.mailboxCapacity = capacity;
            return this;
        }
        
        public Builder withSupervisorStrategy(SupervisorStrategy strategy) {
            this.supervisorStrategy = strategy;
            return this;
        }
        
        public ActorProps build() {
            if (actorClass == null && actorSupplier == null) {
                throw new IllegalArgumentException("Either actorClass or actorSupplier must be provided");
            }
            return new ActorProps(this);
        }
    }
}