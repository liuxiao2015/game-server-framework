/*
 * 文件名: GameActorSystem.java
 * 用途: 游戏Actor系统核心实现
 * 实现内容:
 *   - Actor系统核心类，管理所有Actor
 *   - Actor注册表和根Actor管理
 *   - 消息调度器和配置管理
 *   - 扩展机制和优雅关闭支持
 * 技术选型:
 *   - 单例模式管理Actor系统
 *   - 高性能并发Map存储Actor
 *   - 虚拟线程池提供高并发
 * 依赖关系:
 *   - 管理所有LocalActorRef
 *   - 与Dispatcher和ActorMailbox协作
 *   - 提供Actor创建和生命周期管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.system;

import com.lx.gameserver.frame.actor.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 游戏Actor系统实现
 * <p>
 * Actor系统的核心实现，负责管理所有Actor的生命周期、
 * 消息调度、配置管理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class GameActorSystem implements ActorSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(GameActorSystem.class);
    
    /** 系统名称 */
    private final String name;
    
    /** Actor注册表 */
    private final ConcurrentHashMap<String, LocalActorRef> actors = new ConcurrentHashMap<>();
    
    /** 消息调度器 */
    private final Dispatcher dispatcher;
    
    /** 死信队列Actor */
    private final ActorRef deadLettersActor;
    
    /** 系统是否已启动 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    
    /** 系统是否正在关闭 */
    private final AtomicBoolean terminating = new AtomicBoolean(false);
    
    /** Actor ID生成器 */
    private final AtomicLong actorIdGenerator = new AtomicLong(0);
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     */
    public GameActorSystem(String name) {
        this.name = name;
        this.dispatcher = new Dispatcher("default", Dispatcher.DispatcherType.VIRTUAL_THREAD, 100);
        
        // 创建死信队列Actor
        this.deadLettersActor = createDeadLettersActor();
        
        logger.info("Actor系统[{}]初始化完成", name);
    }
    
    /**
     * 创建死信队列Actor
     *
     * @return 死信队列Actor引用
     */
    private ActorRef createDeadLettersActor() {
        ActorMailbox deadLettersMailbox = new ActorMailbox(ActorMailbox.MailboxType.UNBOUNDED, 0);
        LocalActorRef deadLettersRef = new LocalActorRef(
                "/system/deadLetters", 
                "deadLetters", 
                deadLettersMailbox, 
                this, 
                dispatcher
        );
        
        // 创建死信处理Actor
        DeadLettersActor deadLettersActorInstance = new DeadLettersActor();
        deadLettersRef.setActor(deadLettersActorInstance);
        
        actors.put(deadLettersRef.getPath(), deadLettersRef);
        deadLettersRef.start();
        
        return deadLettersRef;
    }
    
    /**
     * 启动Actor系统
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            logger.info("Actor系统[{}]启动", name);
        }
    }
    
    /**
     * 创建Actor
     *
     * @param props Actor属性
     * @param path  Actor路径
     * @return Actor引用
     */
    public ActorRef actorOf(ActorProps props, String path) {
        if (terminating.get()) {
            throw new IllegalStateException("Actor系统正在关闭，无法创建新Actor");
        }
        
        if (actors.containsKey(path)) {
            throw new IllegalArgumentException("Actor路径已存在: " + path);
        }
        
        // 解析Actor名称
        String name = extractNameFromPath(path);
        
        // 创建邮箱
        ActorMailbox.MailboxType mailboxType = parseMailboxType(props.getMailboxType());
        ActorMailbox mailbox = new ActorMailbox(mailboxType, props.getMailboxCapacity());
        
        // 创建Actor引用
        LocalActorRef actorRef = new LocalActorRef(path, name, mailbox, this, dispatcher);
        
        // 创建Actor实例
        Actor actor = createActorInstance(props);
        actorRef.setActor(actor);
        
        // 注册Actor
        actors.put(path, actorRef);
        
        // 启动Actor
        actorRef.start();
        
        logger.debug("创建Actor: {}", path);
        return actorRef;
    }
    
    /**
     * 在根路径创建Actor
     *
     * @param props Actor属性
     * @param name  Actor名称
     * @return Actor引用
     */
    public ActorRef actorOfUser(ActorProps props, String name) {
        String path = "/user/" + name;
        return actorOf(props, path);
    }
    
    /**
     * 停止Actor
     *
     * @param actorRef Actor引用
     */
    public void stop(ActorRef actorRef) {
        if (actorRef instanceof LocalActorRef localRef) {
            actors.remove(localRef.getPath());
            localRef.stop();
            logger.debug("停止Actor: {}", localRef.getPath());
        }
    }
    
    /**
     * 根据路径获取Actor引用
     *
     * @param path Actor路径
     * @return Actor引用，如果不存在返回null
     */
    public ActorRef getActorRef(String path) {
        return actors.get(path);
    }
    
    /**
     * 获取调度器
     *
     * @return 消息调度器
     */
    public Dispatcher getDispatcher() {
        return dispatcher;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public ActorRef deadLetters() {
        return deadLettersActor;
    }
    
    /**
     * 优雅关闭Actor系统
     */
    public void terminate() {
        if (terminating.compareAndSet(false, true)) {
            logger.info("Actor系统[{}]开始关闭", name);
            
            // 停止所有Actor
            actors.values().forEach(this::stop);
            actors.clear();
            
            // 关闭调度器
            dispatcher.shutdown();
            
            logger.info("Actor系统[{}]关闭完成", name);
        }
    }
    
    /**
     * 检查系统是否正在终止
     *
     * @return 如果正在终止返回true
     */
    public boolean isTerminating() {
        return terminating.get();
    }
    
    /**
     * 获取系统统计信息
     *
     * @return 统计信息字符串
     */
    public String getSystemStats() {
        return String.format("ActorSystem[%s] - Actors: %d, Dispatcher: %s", 
                name, actors.size(), dispatcher.toString());
    }
    
    /**
     * 创建Actor实例
     *
     * @param props Actor属性
     * @return Actor实例
     */
    private Actor createActorInstance(ActorProps props) {
        try {
            if (props.getActorSupplier() != null) {
                return props.getActorSupplier().get();
            } else if (props.getActorClass() != null) {
                Class<? extends Actor> actorClass = props.getActorClass();
                Object[] args = props.getArgs();
                
                if (args.length == 0) {
                    return actorClass.getDeclaredConstructor().newInstance();
                } else {
                    // 查找匹配的构造函数
                    Constructor<? extends Actor> constructor = findMatchingConstructor(actorClass, args);
                    return constructor.newInstance(args);
                }
            } else {
                throw new IllegalArgumentException("Actor类或供应商必须提供");
            }
        } catch (Exception e) {
            throw new RuntimeException("创建Actor实例失败", e);
        }
    }
    
    /**
     * 查找匹配的构造函数
     *
     * @param actorClass Actor类
     * @param args       构造参数
     * @return 匹配的构造函数
     */
    @SuppressWarnings("unchecked")
    private Constructor<? extends Actor> findMatchingConstructor(Class<? extends Actor> actorClass, Object[] args) {
        Constructor<?>[] constructors = actorClass.getDeclaredConstructors();
        
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length == args.length) {
                boolean matches = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (args[i] != null && !paramTypes[i].isAssignableFrom(args[i].getClass())) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return (Constructor<? extends Actor>) constructor;
                }
            }
        }
        
        throw new IllegalArgumentException("找不到匹配的构造函数");
    }
    
    /**
     * 从路径提取Actor名称
     *
     * @param path Actor路径
     * @return Actor名称
     */
    private String extractNameFromPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
    
    /**
     * 解析邮箱类型
     *
     * @param mailboxType 邮箱类型字符串
     * @return 邮箱类型枚举
     */
    private ActorMailbox.MailboxType parseMailboxType(String mailboxType) {
        return switch (mailboxType.toLowerCase()) {
            case "bounded" -> ActorMailbox.MailboxType.BOUNDED;
            case "priority-unbounded" -> ActorMailbox.MailboxType.PRIORITY_UNBOUNDED;
            case "priority-bounded" -> ActorMailbox.MailboxType.PRIORITY_BOUNDED;
            default -> ActorMailbox.MailboxType.UNBOUNDED;
        };
    }
    
    /**
     * 死信处理Actor
     */
    private static class DeadLettersActor extends Actor {
        
        @Override
        public Receive createReceive() {
            return Receive.matchAny(message -> {
                logger.warn("死信消息: {}", message);
                return null;
            });
        }
    }
}