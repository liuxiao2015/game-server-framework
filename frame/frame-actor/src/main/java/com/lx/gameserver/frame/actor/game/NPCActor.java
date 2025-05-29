/*
 * 文件名: NPCActor.java
 * 用途: NPC Actor基类模板
 * 实现内容:
 *   - NPC Actor基类实现
 *   - AI行为支持和状态机集成
 *   - 交互处理和重生机制
 *   - NPC特定的游戏逻辑
 * 技术选型:
 *   - 继承GameActor基类
 *   - 状态机模式管理NPC行为
 *   - AI决策树集成
 * 依赖关系:
 *   - 继承GameActor基类
 *   - 与AI系统和场景系统集成
 *   - 支持NPC配置和行为定制
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.game;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NPC Actor基类
 * <p>
 * 管理游戏中的NPC行为，包括AI决策、交互处理、重生等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class NPCActor extends GameActor {
    
    private static final Logger npcLogger = LoggerFactory.getLogger(NPCActor.class);
    
    /** NPC配置信息 */
    protected final NPCConfig npcConfig;
    
    /** NPC当前状态 */
    protected NPCState npcState = NPCState.IDLE;
    
    /** NPC位置信息 */
    protected PlayerActor.Position position;
    protected PlayerActor.Position spawnPosition;
    
    /** AI相关属性 */
    protected AIBehaviorType currentBehavior = AIBehaviorType.PASSIVE;
    protected ActorRef currentTarget;
    protected Instant lastActionTime;
    
    /** 交互玩家列表 */
    protected final List<ActorRef> interactingPlayers = new CopyOnWriteArrayList<>();
    
    /** 重生相关 */
    protected boolean canRespawn = true;
    protected Duration respawnDelay = Duration.ofSeconds(30);
    protected Instant deathTime;
    
    /** 移动相关 */
    protected double moveSpeed = 1.0;
    protected double detectionRange = 10.0;
    protected double attackRange = 2.0;
    
    /**
     * 构造函数
     *
     * @param npcId     NPC ID
     * @param npcConfig NPC配置
     */
    protected NPCActor(Long npcId, NPCConfig npcConfig) {
        super(npcId, GameEntityType.NPC);
        this.npcConfig = npcConfig;
        this.position = npcConfig.getSpawnPosition();
        this.spawnPosition = npcConfig.getSpawnPosition();
        this.lastActionTime = Instant.now();
    }
    
    @Override
    public Receive createReceive() {
        return Receive.receiveBuilder()
                .match(NPCInteractRequest.class, msg -> { handleInteract(msg); return null; })
                .match(NPCAttackRequest.class, msg -> { handleAttack(msg); return null; })
                .match(NPCMoveCommand.class, msg -> { handleMove(msg); return null; })
                .match(NPCSpawnCommand.class, msg -> { handleSpawn(msg); return null; })
                .match(NPCDespawnCommand.class, msg -> { handleDespawn(msg); return null; })
                .match(NPCBehaviorChange.class, msg -> { handleBehaviorChange(msg); return null; })
                .match(PlayerDetected.class, msg -> { handlePlayerDetected(msg); return null; })
                .match(PlayerLeft.class, msg -> { handlePlayerLeft(msg); return null; })
                .match(AITickMessage.class, msg -> { handleAITick(msg); return null; })
                .match(RespawnCommand.class, msg -> { handleRespawn(msg); return null; })
                .match(GameMessage.class, msg -> { handleGameMessage(msg); return null; })
                .match(GameStateUpdate.class, msg -> { handleStateUpdate(msg); return null; })
                .match(GameCommand.class, msg -> { handleGameCommand(msg); return null; })
                .match(SaveRequest.class, msg -> { performSave(); return null; })
                .match(HeartbeatRequest.class, msg -> { handleHeartbeat(msg); return null; })
                .matchAny(msg -> { handleOtherMessage(msg); return null; })
                .build();
    }
    
    @Override
    protected void onGameStart() {
        super.onGameStart();
        npcLogger.info("NPC[{}:{}]启动，配置: {}", entityType, entityId, npcConfig.getName());
        
        // 启动AI循环
        startAILoop();
        
        // 设置初始状态
        npcState = NPCState.ALIVE;
        updateState("npcState", npcState);
        updateState("position", position);
        
        // 执行NPC特定的启动逻辑
        onNPCSpawn();
    }
    
    @Override
    protected void onGameStop() {
        super.onGameStop();
        npcLogger.info("NPC[{}:{}]停止", entityType, entityId);
        
        // 清理交互玩家
        interactingPlayers.clear();
        
        // 执行NPC特定的停止逻辑
        onNPCDespawn();
    }
    
    /**
     * 处理交互请求
     *
     * @param request 交互请求
     */
    protected void handleInteract(NPCInteractRequest request) {
        if (npcState != NPCState.ALIVE) {
            return;
        }
        
        ActorRef player = request.getPlayerRef();
        String interactionType = request.getInteractionType();
        
        try {
            if (!interactingPlayers.contains(player)) {
                interactingPlayers.add(player);
            }
            
            // 处理交互逻辑
            processInteraction(player, interactionType, request.getData());
            
            lastActionTime = Instant.now();
            npcLogger.debug("NPC[{}]与玩家[{}]交互: {}", entityId, player, interactionType);
            
        } catch (Exception e) {
            npcLogger.error("NPC[{}]处理交互失败", entityId, e);
        }
    }
    
    /**
     * 处理攻击请求
     *
     * @param request 攻击请求
     */
    protected void handleAttack(NPCAttackRequest request) {
        if (npcState != NPCState.ALIVE) {
            return;
        }
        
        ActorRef attacker = request.getAttackerRef();
        int damage = request.getDamage();
        
        try {
            // 处理受到攻击
            processAttack(attacker, damage);
            
            // 设置目标和行为
            if (currentBehavior != AIBehaviorType.AGGRESSIVE) {
                currentTarget = attacker;
                currentBehavior = AIBehaviorType.AGGRESSIVE;
            }
            
            lastActionTime = Instant.now();
            npcLogger.debug("NPC[{}]受到攻击，伤害: {}", entityId, damage);
            
        } catch (Exception e) {
            npcLogger.error("NPC[{}]处理攻击失败", entityId, e);
        }
    }
    
    /**
     * 处理移动命令
     *
     * @param command 移动命令
     */
    protected void handleMove(NPCMoveCommand command) {
        if (npcState != NPCState.ALIVE) {
            return;
        }
        
        PlayerActor.Position targetPosition = command.getTargetPosition();
        
        if (isValidMove(position, targetPosition)) {
            position = targetPosition;
            updateState("position", position);
            
            // 广播移动事件
            broadcastMove(targetPosition);
            
            lastActionTime = Instant.now();
            npcLogger.debug("NPC[{}]移动到位置: {}", entityId, targetPosition);
        }
    }
    
    /**
     * 处理生成命令
     *
     * @param command 生成命令
     */
    protected void handleSpawn(NPCSpawnCommand command) {
        if (npcState == NPCState.ALIVE) {
            return;
        }
        
        // 重置状态
        npcState = NPCState.ALIVE;
        position = spawnPosition;
        currentTarget = null;
        currentBehavior = AIBehaviorType.PASSIVE;
        deathTime = null;
        
        updateState("npcState", npcState);
        updateState("position", position);
        
        // 执行生成逻辑
        onNPCSpawn();
        
        npcLogger.info("NPC[{}]重新生成", entityId);
    }
    
    /**
     * 处理消失命令
     *
     * @param command 消失命令
     */
    protected void handleDespawn(NPCDespawnCommand command) {
        if (npcState == NPCState.DEAD) {
            return;
        }
        
        npcState = NPCState.DEAD;
        deathTime = Instant.now();
        currentTarget = null;
        interactingPlayers.clear();
        
        updateState("npcState", npcState);
        
        // 执行消失逻辑
        onNPCDespawn();
        
        // 如果可以重生，调度重生任务
        if (canRespawn) {
            scheduleRespawn();
        }
        
        npcLogger.info("NPC[{}]死亡消失", entityId);
    }
    
    /**
     * 处理行为变更
     *
     * @param change 行为变更
     */
    protected void handleBehaviorChange(NPCBehaviorChange change) {
        AIBehaviorType newBehavior = change.getNewBehavior();
        
        if (currentBehavior != newBehavior) {
            AIBehaviorType oldBehavior = currentBehavior;
            currentBehavior = newBehavior;
            
            onBehaviorChanged(oldBehavior, newBehavior);
            
            npcLogger.debug("NPC[{}]行为变更: {} -> {}", entityId, oldBehavior, newBehavior);
        }
    }
    
    /**
     * 处理玩家检测
     *
     * @param detected 玩家检测事件
     */
    protected void handlePlayerDetected(PlayerDetected detected) {
        ActorRef player = detected.getPlayerRef();
        double distance = detected.getDistance();
        
        if (distance <= detectionRange) {
            onPlayerEnterRange(player, distance);
        }
    }
    
    /**
     * 处理玩家离开
     *
     * @param left 玩家离开事件
     */
    protected void handlePlayerLeft(PlayerLeft left) {
        ActorRef player = left.getPlayerRef();
        
        interactingPlayers.remove(player);
        
        if (player.equals(currentTarget)) {
            currentTarget = null;
            currentBehavior = AIBehaviorType.PASSIVE;
        }
        
        onPlayerLeaveRange(player);
    }
    
    /**
     * 处理AI循环
     *
     * @param tick AI循环消息
     */
    protected void handleAITick(AITickMessage tick) {
        if (npcState != NPCState.ALIVE) {
            return;
        }
        
        try {
            // 执行AI决策
            performAIDecision();
            
        } catch (Exception e) {
            npcLogger.error("NPC[{}]AI决策异常", entityId, e);
        }
    }
    
    /**
     * 处理重生命令
     *
     * @param command 重生命令
     */
    protected void handleRespawn(RespawnCommand command) {
        if (npcState == NPCState.DEAD && canRespawn) {
            handleSpawn(new NPCSpawnCommand());
        }
    }
    
    @Override
    protected void handleGameMessage(GameMessage message) {
        processNPCGameMessage(message);
    }
    
    @Override
    protected void executeGameCommand(GameCommand command) {
        npcLogger.debug("NPC[{}]执行游戏命令: {}", entityId, command.getCommandType());
    }
    
    @Override
    protected void saveGameState() {
        saveNPCData();
    }
    
    /**
     * 启动AI循环
     */
    private void startAILoop() {
        // 每秒执行一次AI决策
        schedule(Duration.ofSeconds(1), Duration.ofSeconds(1), new AITickMessage());
    }
    
    /**
     * 调度重生
     */
    private void scheduleRespawn() {
        scheduleOnce(respawnDelay, new RespawnCommand());
    }
    
    // 抽象方法，由具体实现类提供
    protected abstract void onNPCSpawn();
    protected abstract void onNPCDespawn();
    protected abstract void processInteraction(ActorRef player, String interactionType, Object data);
    protected abstract void processAttack(ActorRef attacker, int damage);
    protected abstract boolean isValidMove(PlayerActor.Position from, PlayerActor.Position to);
    protected abstract void broadcastMove(PlayerActor.Position newPosition);
    protected abstract void performAIDecision();
    protected abstract void onBehaviorChanged(AIBehaviorType oldBehavior, AIBehaviorType newBehavior);
    protected abstract void onPlayerEnterRange(ActorRef player, double distance);
    protected abstract void onPlayerLeaveRange(ActorRef player);
    protected abstract void processNPCGameMessage(GameMessage message);
    protected abstract void saveNPCData();
    
    // Getters and Setters
    public NPCConfig getNpcConfig() {
        return npcConfig;
    }
    
    public NPCState getNpcState() {
        return npcState;
    }
    
    public PlayerActor.Position getPosition() {
        return position;
    }
    
    public AIBehaviorType getCurrentBehavior() {
        return currentBehavior;
    }
    
    public ActorRef getCurrentTarget() {
        return currentTarget;
    }
    
    public List<ActorRef> getInteractingPlayers() {
        return List.copyOf(interactingPlayers);
    }
    
    /**
     * NPC状态枚举
     */
    public enum NPCState {
        IDLE, ALIVE, DEAD, SPAWNING, DESPAWNING
    }
    
    /**
     * AI行为类型枚举
     */
    public enum AIBehaviorType {
        PASSIVE, AGGRESSIVE, DEFENSIVE, PATROL, GUARD
    }
    
    /**
     * NPC配置类
     */
    public static class NPCConfig {
        private final String name;
        private final int npcType;
        private final PlayerActor.Position spawnPosition;
        private final int level;
        private final int maxHealth;
        
        public NPCConfig(String name, int npcType, PlayerActor.Position spawnPosition, int level, int maxHealth) {
            this.name = name;
            this.npcType = npcType;
            this.spawnPosition = spawnPosition;
            this.level = level;
            this.maxHealth = maxHealth;
        }
        
        public String getName() { return name; }
        public int getNpcType() { return npcType; }
        public PlayerActor.Position getSpawnPosition() { return spawnPosition; }
        public int getLevel() { return level; }
        public int getMaxHealth() { return maxHealth; }
    }
    
    // 消息类定义
    public static class NPCInteractRequest extends GameMessage {
        private final ActorRef playerRef;
        private final String interactionType;
        private final Object data;
        
        public NPCInteractRequest(ActorRef playerRef, String interactionType, Object data) {
            this.playerRef = playerRef;
            this.interactionType = interactionType;
            this.data = data;
        }
        
        public ActorRef getPlayerRef() { return playerRef; }
        public String getInteractionType() { return interactionType; }
        public Object getData() { return data; }
    }
    
    public static class NPCAttackRequest extends GameMessage {
        private final ActorRef attackerRef;
        private final int damage;
        
        public NPCAttackRequest(ActorRef attackerRef, int damage) {
            this.attackerRef = attackerRef;
            this.damage = damage;
        }
        
        public ActorRef getAttackerRef() { return attackerRef; }
        public int getDamage() { return damage; }
    }
    
    public static class NPCMoveCommand extends GameCommand {
        private final PlayerActor.Position targetPosition;
        
        public NPCMoveCommand(PlayerActor.Position targetPosition) {
            super("MOVE");
            this.targetPosition = targetPosition;
        }
        
        public PlayerActor.Position getTargetPosition() { return targetPosition; }
    }
    
    public static class NPCSpawnCommand extends GameCommand {
        public NPCSpawnCommand() {
            super("SPAWN");
        }
    }
    
    public static class NPCDespawnCommand extends GameCommand {
        public NPCDespawnCommand() {
            super("DESPAWN");
        }
    }
    
    public static class NPCBehaviorChange extends GameMessage {
        private final AIBehaviorType newBehavior;
        
        public NPCBehaviorChange(AIBehaviorType newBehavior) {
            this.newBehavior = newBehavior;
        }
        
        public AIBehaviorType getNewBehavior() { return newBehavior; }
    }
    
    public static class PlayerDetected extends GameMessage {
        private final ActorRef playerRef;
        private final double distance;
        
        public PlayerDetected(ActorRef playerRef, double distance) {
            this.playerRef = playerRef;
            this.distance = distance;
        }
        
        public ActorRef getPlayerRef() { return playerRef; }
        public double getDistance() { return distance; }
    }
    
    public static class PlayerLeft extends GameMessage {
        private final ActorRef playerRef;
        
        public PlayerLeft(ActorRef playerRef) {
            this.playerRef = playerRef;
        }
        
        public ActorRef getPlayerRef() { return playerRef; }
    }
    
    public static class AITickMessage extends GameMessage {
    }
    
    public static class RespawnCommand extends GameCommand {
        public RespawnCommand() {
            super("RESPAWN");
        }
    }
}