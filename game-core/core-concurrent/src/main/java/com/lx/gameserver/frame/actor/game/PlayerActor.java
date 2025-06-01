/*
 * 文件名: PlayerActor.java
 * 用途: 玩家Actor基类模板
 * 实现内容:
 *   - 玩家Actor基类实现
 *   - 玩家状态管理和消息分发
 *   - 移动、战斗、聊天等功能处理
 *   - 定时保存和离线处理
 *   - 会话管理
 * 技术选型:
 *   - 继承GameActor基类
 *   - 状态机模式管理玩家状态
 *   - 事件驱动的消息处理
 * 依赖关系:
 *   - 继承GameActor基类
 *   - 与游戏世界和场景系统集成
 *   - 支持玩家数据持久化
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家Actor基类
 * <p>
 * 管理单个玩家的状态和行为，包括移动、战斗、聊天等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class PlayerActor extends GameActor {
    
    private static final Logger playerLogger = LoggerFactory.getLogger(PlayerActor.class);
    
    /** 玩家基本信息 */
    protected String playerName;
    protected int level;
    protected long experience;
    protected int vipLevel;
    
    /** 玩家位置信息 */
    protected Position position;
    protected String currentScene;
    
    /** 玩家状态 */
    protected PlayerState playerState = PlayerState.OFFLINE;
    
    /** 会话信息 */
    protected ActorRef sessionRef;
    protected Instant lastLoginTime;
    protected Instant lastLogoutTime;
    
    /** 离线消息队列 */
    protected final ConcurrentHashMap<String, Object> offlineMessages = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     *
     * @param playerId 玩家ID
     */
    protected PlayerActor(Long playerId) {
        super(playerId, GameEntityType.PLAYER);
    }
    
    @Override
    public Receive createReceive() {
        return Receive.receiveBuilder()
                .match(PlayerLoginRequest.class, msg -> { handleLogin(msg); return null; })
                .match(PlayerLogoutRequest.class, msg -> { handleLogout(msg); return null; })
                .match(PlayerMoveRequest.class, msg -> { handleMove(msg); return null; })
                .match(PlayerChatMessage.class, msg -> { handleChat(msg); return null; })
                .match(PlayerBattleCommand.class, msg -> { handleBattle(msg); return null; })
                .match(PlayerItemCommand.class, msg -> { handleItem(msg); return null; })
                .match(PlayerSkillCommand.class, msg -> { handleSkill(msg); return null; })
                .match(PlayerQuestCommand.class, msg -> { handleQuest(msg); return null; })
                .match(PlayerSocialCommand.class, msg -> { handleSocial(msg); return null; })
                .match(OfflineMessageRequest.class, msg -> { handleOfflineMessage(msg); return null; })
                .match(GameMessage.class, msg -> { handleGameMessage(msg); return null; })
                .match(GameStateUpdate.class, msg -> { handleStateUpdate(msg); return null; })
                .match(GameCommand.class, msg -> { handleGameCommand(msg); return null; })
                .match(SaveRequest.class, msg -> { performSave(); return null; })
                .match(HeartbeatRequest.class, msg -> { handleHeartbeat(msg); return null; })
                .match(ShutdownRequest.class, msg -> { handleLogout(new PlayerLogoutRequest("系统关闭")); return null; })
                .matchAny(msg -> { handleOtherMessage(msg); return null; })
                .build();
    }
    
    /**
     * 处理玩家登录
     *
     * @param request 登录请求
     */
    protected void handleLogin(PlayerLoginRequest request) {
        playerLogger.info("玩家[{}]登录请求", entityId);
        
        try {
            // 加载玩家数据
            loadPlayerData();
            
            // 设置会话
            sessionRef = request.getSessionRef();
            lastLoginTime = Instant.now();
            playerState = PlayerState.ONLINE;
            setOnline(true);
            
            // 执行登录逻辑
            onPlayerLogin(request);
            
            // 发送登录成功响应
            if (sessionRef != null) {
                sessionRef.tell(new PlayerLoginResponse(true, "登录成功"), getSelf());
            }
            
            // 处理离线消息
            processOfflineMessages();
            
            playerLogger.info("玩家[{}]登录成功", entityId);
            
        } catch (Exception e) {
            playerLogger.error("玩家[{}]登录失败", entityId, e);
            
            if (sessionRef != null) {
                sessionRef.tell(new PlayerLoginResponse(false, "登录失败: " + e.getMessage()), getSelf());
            }
        }
    }
    
    /**
     * 处理玩家登出
     *
     * @param request 登出请求
     */
    protected void handleLogout(PlayerLogoutRequest request) {
        playerLogger.info("玩家[{}]登出请求: {}", entityId, request.getReason());
        
        try {
            // 执行登出逻辑
            onPlayerLogout(request);
            
            // 保存玩家数据
            performSave();
            
            // 更新状态
            lastLogoutTime = Instant.now();
            playerState = PlayerState.OFFLINE;
            setOnline(false);
            
            // 发送登出响应
            if (sessionRef != null) {
                sessionRef.tell(new PlayerLogoutResponse(true, "登出成功"), getSelf());
                sessionRef = null;
            }
            
            playerLogger.info("玩家[{}]登出成功", entityId);
            
        } catch (Exception e) {
            playerLogger.error("玩家[{}]登出异常", entityId, e);
        }
    }
    
    /**
     * 处理玩家移动
     *
     * @param request 移动请求
     */
    protected void handleMove(PlayerMoveRequest request) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        Position newPosition = request.getTargetPosition();
        if (isValidMove(position, newPosition)) {
            position = newPosition;
            updateState("position", position);
            
            // 广播移动事件
            broadcastMove(newPosition);
            
            playerLogger.debug("玩家[{}]移动到位置: {}", entityId, newPosition);
        }
    }
    
    /**
     * 处理聊天消息
     *
     * @param message 聊天消息
     */
    protected void handleChat(PlayerChatMessage message) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        try {
            processChatMessage(message);
        } catch (Exception e) {
            playerLogger.error("处理聊天消息失败[{}]", entityId, e);
        }
    }
    
    /**
     * 处理战斗命令
     *
     * @param command 战斗命令
     */
    protected void handleBattle(PlayerBattleCommand command) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        try {
            processBattleCommand(command);
        } catch (Exception e) {
            playerLogger.error("处理战斗命令失败[{}]", entityId, e);
        }
    }
    
    /**
     * 处理物品命令
     *
     * @param command 物品命令
     */
    protected void handleItem(PlayerItemCommand command) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        try {
            processItemCommand(command);
        } catch (Exception e) {
            playerLogger.error("处理物品命令失败[{}]", entityId, e);
        }
    }
    
    /**
     * 处理技能命令
     *
     * @param command 技能命令
     */
    protected void handleSkill(PlayerSkillCommand command) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        try {
            processSkillCommand(command);
        } catch (Exception e) {
            playerLogger.error("处理技能命令失败[{}]", entityId, e);
        }
    }
    
    /**
     * 处理任务命令
     *
     * @param command 任务命令
     */
    protected void handleQuest(PlayerQuestCommand command) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        try {
            processQuestCommand(command);
        } catch (Exception e) {
            playerLogger.error("处理任务命令失败[{}]", entityId, e);
        }
    }
    
    /**
     * 处理社交命令
     *
     * @param command 社交命令
     */
    protected void handleSocial(PlayerSocialCommand command) {
        if (playerState != PlayerState.ONLINE) {
            return;
        }
        
        try {
            processSocialCommand(command);
        } catch (Exception e) {
            playerLogger.error("处理社交命令失败[{}]", entityId, e);
        }
    }
    
    /**
     * 处理离线消息请求
     *
     * @param request 离线消息请求
     */
    protected void handleOfflineMessage(OfflineMessageRequest request) {
        offlineMessages.put(request.getMessageId(), request.getContent());
    }
    
    @Override
    protected void handleGameMessage(GameMessage message) {
        // 如果玩家离线，将消息保存为离线消息
        if (playerState == PlayerState.OFFLINE) {
            storeOfflineMessage(message);
            return;
        }
        
        // 处理在线游戏消息
        processOnlineGameMessage(message);
    }
    
    @Override
    protected void executeGameCommand(GameCommand command) {
        // 根据命令类型分发到具体处理方法
        playerLogger.debug("执行游戏命令[{}]: {}", entityId, command.getCommandType());
    }
    
    @Override
    protected void saveGameState() {
        // 保存玩家数据到数据库
        savePlayerData();
    }
    
    // 抽象方法，由具体实现类提供
    protected abstract void loadPlayerData();
    protected abstract void savePlayerData();
    protected abstract void onPlayerLogin(PlayerLoginRequest request);
    protected abstract void onPlayerLogout(PlayerLogoutRequest request);
    protected abstract boolean isValidMove(Position from, Position to);
    protected abstract void broadcastMove(Position newPosition);
    protected abstract void processChatMessage(PlayerChatMessage message);
    protected abstract void processBattleCommand(PlayerBattleCommand command);
    protected abstract void processItemCommand(PlayerItemCommand command);
    protected abstract void processSkillCommand(PlayerSkillCommand command);
    protected abstract void processQuestCommand(PlayerQuestCommand command);
    protected abstract void processSocialCommand(PlayerSocialCommand command);
    protected abstract void processOnlineGameMessage(GameMessage message);
    
    /**
     * 存储离线消息
     *
     * @param message 消息
     */
    private void storeOfflineMessage(GameMessage message) {
        String messageId = "offline_" + System.currentTimeMillis();
        offlineMessages.put(messageId, message);
        playerLogger.debug("存储离线消息[{}]: {}", entityId, message);
    }
    
    /**
     * 处理离线消息
     */
    private void processOfflineMessages() {
        if (offlineMessages.isEmpty()) {
            return;
        }
        
        playerLogger.info("处理玩家[{}]的{}条离线消息", entityId, offlineMessages.size());
        
        offlineMessages.values().forEach(message -> {
            try {
                if (message instanceof GameMessage gameMessage) {
                    processOnlineGameMessage(gameMessage);
                }
            } catch (Exception e) {
                playerLogger.error("处理离线消息失败[{}]", entityId, e);
            }
        });
        
        offlineMessages.clear();
    }
    
    // Getters and Setters
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public long getExperience() {
        return experience;
    }
    
    public void setExperience(long experience) {
        this.experience = experience;
    }
    
    public Position getPosition() {
        return position;
    }
    
    public void setPosition(Position position) {
        this.position = position;
    }
    
    public PlayerState getPlayerState() {
        return playerState;
    }
    
    public ActorRef getSessionRef() {
        return sessionRef;
    }
    
    /**
     * 玩家状态枚举
     */
    public enum PlayerState {
        OFFLINE, ONLINE, BUSY, AWAY, BATTLE
    }
    
    /**
     * 位置类
     */
    public static class Position {
        private final double x;
        private final double y;
        private final double z;
        
        public Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        
        @Override
        public String toString() {
            return String.format("Position(%.2f, %.2f, %.2f)", x, y, z);
        }
    }
    
    // 消息类定义
    public static class PlayerLoginRequest extends GameMessage {
        private final ActorRef sessionRef;
        private final String token;
        
        public PlayerLoginRequest(ActorRef sessionRef, String token) {
            this.sessionRef = sessionRef;
            this.token = token;
        }
        
        public ActorRef getSessionRef() { return sessionRef; }
        public String getToken() { return token; }
    }
    
    public static class PlayerLoginResponse extends GameMessage {
        private final boolean success;
        private final String message;
        
        public PlayerLoginResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    public static class PlayerLogoutRequest extends GameMessage {
        private final String reason;
        
        public PlayerLogoutRequest(String reason) {
            this.reason = reason;
        }
        
        public String getReason() { return reason; }
    }
    
    public static class PlayerLogoutResponse extends GameMessage {
        private final boolean success;
        private final String message;
        
        public PlayerLogoutResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    public static class PlayerMoveRequest extends GameMessage {
        private final Position targetPosition;
        
        public PlayerMoveRequest(Position targetPosition) {
            this.targetPosition = targetPosition;
        }
        
        public Position getTargetPosition() { return targetPosition; }
    }
    
    public static class PlayerChatMessage extends GameMessage {
        private final String content;
        private final String channel;
        
        public PlayerChatMessage(String content, String channel) {
            this.content = content;
            this.channel = channel;
        }
        
        public String getContent() { return content; }
        public String getChannel() { return channel; }
    }
    
    public static class PlayerBattleCommand extends GameCommand {
        public PlayerBattleCommand(String commandType) {
            super(commandType);
        }
    }
    
    public static class PlayerItemCommand extends GameCommand {
        public PlayerItemCommand(String commandType) {
            super(commandType);
        }
    }
    
    public static class PlayerSkillCommand extends GameCommand {
        public PlayerSkillCommand(String commandType) {
            super(commandType);
        }
    }
    
    public static class PlayerQuestCommand extends GameCommand {
        public PlayerQuestCommand(String commandType) {
            super(commandType);
        }
    }
    
    public static class PlayerSocialCommand extends GameCommand {
        public PlayerSocialCommand(String commandType) {
            super(commandType);
        }
    }
    
    public static class OfflineMessageRequest extends GameMessage {
        private final String messageId;
        private final Object content;
        
        public OfflineMessageRequest(String messageId, Object content) {
            this.messageId = messageId;
            this.content = content;
        }
        
        public String getMessageId() { return messageId; }
        public Object getContent() { return content; }
    }
}