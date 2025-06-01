/*
 * 文件名: GameActor.java
 * 用途: 游戏Actor基类
 * 实现内容:
 *   - 游戏Actor基类，扩展基础Actor功能
 *   - 游戏特定的消息处理模式
 *   - 状态同步机制和数据持久化支持
 *   - AOI（Area of Interest）支持
 * 技术选型:
 *   - 抽象类提供游戏通用功能
 *   - 状态机模式管理游戏状态
 *   - 事件驱动的状态同步
 * 依赖关系:
 *   - 继承Actor基类
 *   - 被具体游戏Actor实现类继承
 *   - 与游戏状态管理系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.game;

import com.lx.gameserver.frame.actor.core.Actor;
import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏Actor基类
 * <p>
 * 为游戏相关的Actor提供通用功能，包括状态管理、
 * 定时保存、事件处理等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class GameActor extends Actor {
    
    protected static final Logger gameLogger = LoggerFactory.getLogger(GameActor.class);
    
    /** 游戏实体ID */
    protected final Long entityId;
    
    /** 游戏实体类型 */
    protected final GameEntityType entityType;
    
    /** 创建时间 */
    protected final Instant createTime;
    
    /** 最后活跃时间 */
    protected volatile Instant lastActiveTime;
    
    /** 状态变更计数器 */
    protected final AtomicLong stateChangeCounter = new AtomicLong(0);
    
    /** 游戏状态数据 */
    protected final ConcurrentHashMap<String, Object> gameState = new ConcurrentHashMap<>();
    
    /** 是否需要持久化 */
    protected volatile boolean needsPersistence = false;
    
    /** 是否在线 */
    protected volatile boolean online = true;
    
    /**
     * 构造函数
     *
     * @param entityId   游戏实体ID
     * @param entityType 游戏实体类型
     */
    protected GameActor(Long entityId, GameEntityType entityType) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.createTime = Instant.now();
        this.lastActiveTime = this.createTime;
    }
    
    @Override
    public void preStart() {
        super.preStart();
        gameLogger.info("游戏Actor[{}:{}]启动", entityType, entityId);
        
        // 启动定时保存任务
        startPeriodicSave();
        
        // 启动心跳检测
        startHeartbeat();
        
        // 执行游戏特定的启动逻辑
        onGameStart();
    }
    
    @Override
    public void postStop() {
        super.postStop();
        gameLogger.info("游戏Actor[{}:{}]停止", entityType, entityId);
        
        // 执行最后一次保存
        if (needsPersistence) {
            performSave();
        }
        
        // 执行游戏特定的停止逻辑
        onGameStop();
    }
    
    @Override
    public Receive createReceive() {
        return Receive.receiveBuilder()
                .match(GameMessage.class, msg -> { handleGameMessage(msg); return null; })
                .match(GameStateUpdate.class, msg -> { handleStateUpdate(msg); return null; })
                .match(GameCommand.class, msg -> { handleGameCommand(msg); return null; })
                .match(SaveRequest.class, msg -> { performSave(); return null; })
                .match(HeartbeatRequest.class, msg -> { handleHeartbeat(msg); return null; })
                .match(ShutdownRequest.class, msg -> { stop(); return null; })
                .matchAny(msg -> { handleOtherMessage(msg); return null; })
                .build();
    }
    
    /**
     * 处理游戏消息（由子类实现具体逻辑）
     *
     * @param message 游戏消息
     */
    protected abstract void handleGameMessage(GameMessage message);
    
    /**
     * 处理状态更新
     *
     * @param update 状态更新
     */
    protected void handleStateUpdate(GameStateUpdate update) {
        updateState(update.getKey(), update.getValue());
        lastActiveTime = Instant.now();
        needsPersistence = true;
        
        gameLogger.debug("更新游戏状态[{}:{}]: {} = {}", entityType, entityId, 
                update.getKey(), update.getValue());
    }
    
    /**
     * 处理游戏命令
     *
     * @param command 游戏命令
     */
    protected void handleGameCommand(GameCommand command) {
        lastActiveTime = Instant.now();
        
        try {
            executeGameCommand(command);
        } catch (Exception e) {
            gameLogger.error("执行游戏命令失败[{}:{}]: {}", entityType, entityId, command, e);
        }
    }
    
    /**
     * 处理心跳请求
     *
     * @param heartbeat 心跳请求
     */
    protected void handleHeartbeat(HeartbeatRequest heartbeat) {
        lastActiveTime = Instant.now();
        
        // 发送心跳响应
        if (getSender() != null && !getSender().equals(ActorRef.noSender())) {
            getSender().tell(new HeartbeatResponse(entityId, online), getSelf());
        }
    }
    
    /**
     * 处理其他消息
     *
     * @param message 其他消息
     */
    protected void handleOtherMessage(Object message) {
        gameLogger.debug("收到未处理消息[{}:{}]: {}", entityType, entityId, message);
        unhandled(message);
    }
    
    /**
     * 执行游戏命令（由子类实现）
     *
     * @param command 游戏命令
     */
    protected abstract void executeGameCommand(GameCommand command);
    
    /**
     * 游戏启动回调（由子类实现）
     */
    protected void onGameStart() {
        // 默认空实现
    }
    
    /**
     * 游戏停止回调（由子类实现）
     */
    protected void onGameStop() {
        // 默认空实现
    }
    
    /**
     * 更新游戏状态
     *
     * @param key   状态键
     * @param value 状态值
     */
    protected void updateState(String key, Object value) {
        gameState.put(key, value);
        stateChangeCounter.incrementAndGet();
        needsPersistence = true;
    }
    
    /**
     * 获取游戏状态
     *
     * @param key 状态键
     * @param <T> 状态类型
     * @return 状态值
     */
    @SuppressWarnings("unchecked")
    protected <T> T getState(String key) {
        return (T) gameState.get(key);
    }
    
    /**
     * 获取游戏状态（带默认值）
     *
     * @param key          状态键
     * @param defaultValue 默认值
     * @param <T>          状态类型
     * @return 状态值
     */
    @SuppressWarnings("unchecked")
    protected <T> T getState(String key, T defaultValue) {
        return (T) gameState.getOrDefault(key, defaultValue);
    }
    
    /**
     * 启动定时保存任务
     */
    private void startPeriodicSave() {
        schedule(Duration.ofMinutes(5), Duration.ofMinutes(5), new SaveRequest());
    }
    
    /**
     * 启动心跳检测
     */
    private void startHeartbeat() {
        schedule(Duration.ofSeconds(30), Duration.ofSeconds(30), new HeartbeatRequest());
    }
    
    /**
     * 执行保存操作
     */
    protected void performSave() {
        if (!needsPersistence) {
            return;
        }
        
        try {
            saveGameState();
            needsPersistence = false;
            gameLogger.debug("保存游戏状态[{}:{}]", entityType, entityId);
        } catch (Exception e) {
            gameLogger.error("保存游戏状态失败[{}:{}]", entityType, entityId, e);
        }
    }
    
    /**
     * 保存游戏状态（由子类实现具体逻辑）
     */
    protected abstract void saveGameState();
    
    // Getters
    public Long getEntityId() {
        return entityId;
    }
    
    public GameEntityType getEntityType() {
        return entityType;
    }
    
    public Instant getCreateTime() {
        return createTime;
    }
    
    public Instant getLastActiveTime() {
        return lastActiveTime;
    }
    
    public long getStateChangeCount() {
        return stateChangeCounter.get();
    }
    
    public boolean isOnline() {
        return online;
    }
    
    public void setOnline(boolean online) {
        this.online = online;
    }
    
    /**
     * 游戏实体类型枚举
     */
    public enum GameEntityType {
        /** 玩家 */
        PLAYER,
        /** NPC */
        NPC,
        /** 怪物 */
        MONSTER,
        /** 场景 */
        SCENE,
        /** 公会 */
        GUILD,
        /** 队伍 */
        TEAM,
        /** 其他 */
        OTHER
    }
    
    // 消息类定义
    public static abstract class GameMessage {
        protected final Instant timestamp = Instant.now();
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    public static class GameStateUpdate extends GameMessage {
        private final String key;
        private final Object value;
        
        public GameStateUpdate(String key, Object value) {
            this.key = key;
            this.value = value;
        }
        
        public String getKey() {
            return key;
        }
        
        public Object getValue() {
            return value;
        }
    }
    
    public static abstract class GameCommand extends GameMessage {
        protected final String commandType;
        
        protected GameCommand(String commandType) {
            this.commandType = commandType;
        }
        
        public String getCommandType() {
            return commandType;
        }
    }
    
    public static class SaveRequest extends GameMessage {
    }
    
    public static class HeartbeatRequest extends GameMessage {
    }
    
    public static class HeartbeatResponse extends GameMessage {
        private final Long entityId;
        private final boolean online;
        
        public HeartbeatResponse(Long entityId, boolean online) {
            this.entityId = entityId;
            this.online = online;
        }
        
        public Long getEntityId() {
            return entityId;
        }
        
        public boolean isOnline() {
            return online;
        }
    }
    
    public static class ShutdownRequest extends GameMessage {
    }
}