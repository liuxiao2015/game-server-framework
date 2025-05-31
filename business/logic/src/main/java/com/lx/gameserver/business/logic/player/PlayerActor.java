/*
 * 文件名: PlayerActor.java
 * 用途: 玩家Actor实现示例
 * 实现内容:
 *   - 玩家相关消息处理和状态管理
 *   - 定时任务和与其他Actor交互
 *   - 错误处理和监督机制集成
 *   - 玩家行为日志和性能监控
 *   - 并发安全的玩家数据操作
 * 技术选型:
 *   - 继承Actor基类实现消息处理
 *   - 状态机模式管理玩家状态
 *   - 定时器实现周期性任务
 *   - 事件发布机制同步状态
 * 依赖关系:
 *   - 与Player实体协作进行数据管理
 *   - 被PlayerManager创建和管理
 *   - 与其他Actor进行消息通信
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.player;

import com.lx.gameserver.frame.actor.core.Actor;
import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Receive;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家Actor实现示例
 * <p>
 * 展示如何使用Actor模型处理玩家相关的业务逻辑，
 * 包括消息处理、状态管理、定时任务等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
public class PlayerActor extends Actor {

    /**
     * 玩家消息基类
     */
    public static abstract class PlayerMessage {
        private final Long playerId;
        private final LocalDateTime timestamp;

        public PlayerMessage(Long playerId) {
            this.playerId = playerId;
            this.timestamp = LocalDateTime.now();
        }

        public Long getPlayerId() {
            return playerId;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 玩家登录消息
     */
    public static class PlayerLoginMessage extends PlayerMessage {
        private final String sessionId;
        private final String clientInfo;

        public PlayerLoginMessage(Long playerId, String sessionId, String clientInfo) {
            super(playerId);
            this.sessionId = sessionId;
            this.clientInfo = clientInfo;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getClientInfo() {
            return clientInfo;
        }
    }

    /**
     * 玩家登出消息
     */
    public static class PlayerLogoutMessage extends PlayerMessage {
        private final String reason;

        public PlayerLogoutMessage(Long playerId, String reason) {
            super(playerId);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 玩家状态更新消息
     */
    public static class UpdatePlayerStateMessage extends PlayerMessage {
        private final Player.PlayerState newState;

        public UpdatePlayerStateMessage(Long playerId, Player.PlayerState newState) {
            super(playerId);
            this.newState = newState;
        }

        public Player.PlayerState getNewState() {
            return newState;
        }
    }

    /**
     * 玩家数据同步消息
     */
    public static class SyncPlayerDataMessage extends PlayerMessage {
        public SyncPlayerDataMessage(Long playerId) {
            super(playerId);
        }
    }

    /**
     * 玩家奖励消息
     */
    public static class GrantRewardMessage extends PlayerMessage {
        private final long coins;
        private final long diamonds;
        private final long experience;
        private final String reason;

        public GrantRewardMessage(Long playerId, long coins, long diamonds, long experience, String reason) {
            super(playerId);
            this.coins = coins;
            this.diamonds = diamonds;
            this.experience = experience;
            this.reason = reason;
        }

        public long getCoins() {
            return coins;
        }

        public long getDiamonds() {
            return diamonds;
        }

        public long getExperience() {
            return experience;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 发送消息给玩家
     */
    public static class SendMessageToPlayerMessage extends PlayerMessage {
        private final Object message;

        public SendMessageToPlayerMessage(Long playerId, Object message) {
            super(playerId);
            this.message = message;
        }

        public Object getMessage() {
            return message;
        }
    }

    /**
     * 定时器消息
     */
    public static class PlayerTimerMessage extends PlayerMessage {
        private final String timerType;

        public PlayerTimerMessage(Long playerId, String timerType) {
            super(playerId);
            this.timerType = timerType;
        }

        public String getTimerType() {
            return timerType;
        }
    }

    /** 玩家ID */
    private Long playerId;

    /** 玩家数据 */
    private Player player;

    /** 会话ID */
    private String sessionId;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;

    /** 消息计数器 */
    private final Map<String, Long> messageCounters = new ConcurrentHashMap<>();

    /** 定时器引用 */
    private final Map<String, Object> timers = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param playerId 玩家ID
     * @param player   玩家数据
     */
    public PlayerActor(Long playerId, Player player) {
        this.playerId = playerId;
        this.player = player;
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * Actor启动前回调
     */
    @Override
    protected void preStart() throws Exception {
        super.preStart();
        log.info("玩家Actor {} 启动，玩家ID: {}", getActorId(), playerId);

        // 启动定时保存任务
        scheduleDataSync();

        // 启动心跳检查
        scheduleHeartbeat();
    }

    /**
     * Actor停止后回调
     */
    @Override
    protected void postStop() throws Exception {
        super.postStop();
        log.info("玩家Actor {} 停止，玩家ID: {}", getActorId(), playerId);

        // 保存玩家数据
        savePlayerData();

        // 清理定时器
        timers.clear();
    }

    /**
     * Actor重启前回调
     */
    @Override
    protected void preRestart(Throwable reason, Object message) throws Exception {
        super.preRestart(reason, message);
        log.warn("玩家Actor {} 重启，原因: {}, 消息: {}", getActorId(), reason.getMessage(), message);

        // 保存当前状态
        savePlayerData();
    }

    /**
     * Actor重启后回调
     */
    @Override
    protected void postRestart(Throwable reason) throws Exception {
        super.postRestart(reason);
        log.info("玩家Actor {} 重启完成，玩家ID: {}", getActorId(), playerId);

        // 重新启动定时任务
        scheduleDataSync();
        scheduleHeartbeat();
    }

    /**
     * 创建消息接收器
     */
    @Override
    protected Receive createReceive() {
        return receiveBuilder()
                .match(PlayerLoginMessage.class, this::handlePlayerLogin)
                .match(PlayerLogoutMessage.class, this::handlePlayerLogout)
                .match(UpdatePlayerStateMessage.class, this::handleUpdatePlayerState)
                .match(SyncPlayerDataMessage.class, this::handleSyncPlayerData)
                .match(GrantRewardMessage.class, this::handleGrantReward)
                .match(SendMessageToPlayerMessage.class, this::handleSendMessageToPlayer)
                .match(PlayerTimerMessage.class, this::handlePlayerTimer)
                .matchAny(this::handleUnknownMessage)
                .build();
    }

    /**
     * 处理玩家登录消息
     */
    private void handlePlayerLogin(PlayerLoginMessage message) {
        log.info("处理玩家 {} 登录，会话: {}", message.getPlayerId(), message.getSessionId());

        try {
            this.sessionId = message.getSessionId();
            this.lastActiveTime = LocalDateTime.now();

            // 更新玩家状态
            if (player != null) {
                player.recordLogin(sessionId);
                incrementMessageCounter("login");
            }

            // 发送登录成功响应
            getSender().tell(new PlayerLoginResponse(true, "登录成功"), getSelf());

            log.info("玩家 {} 登录处理完成", message.getPlayerId());

        } catch (Exception e) {
            log.error("处理玩家 {} 登录失败", message.getPlayerId(), e);
            getSender().tell(new PlayerLoginResponse(false, "登录失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理玩家登出消息
     */
    private void handlePlayerLogout(PlayerLogoutMessage message) {
        log.info("处理玩家 {} 登出，原因: {}", message.getPlayerId(), message.getReason());

        try {
            // 更新玩家状态
            if (player != null) {
                player.recordLogout();
                incrementMessageCounter("logout");
            }

            // 保存数据
            savePlayerData();

            // 清理会话
            this.sessionId = null;

            // 发送登出成功响应
            getSender().tell(new PlayerLogoutResponse(true, "登出成功"), getSelf());

            // 停止Actor
            getContext().stop(getSelf());

            log.info("玩家 {} 登出处理完成", message.getPlayerId());

        } catch (Exception e) {
            log.error("处理玩家 {} 登出失败", message.getPlayerId(), e);
            getSender().tell(new PlayerLogoutResponse(false, "登出失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理玩家状态更新消息
     */
    private void handleUpdatePlayerState(UpdatePlayerStateMessage message) {
        log.debug("更新玩家 {} 状态为 {}", message.getPlayerId(), message.getNewState());

        try {
            if (player != null) {
                Player.PlayerState oldState = player.getCurrentState();
                if (player.updateState(message.getNewState())) {
                    incrementMessageCounter("state_update");
                    log.info("玩家 {} 状态从 {} 变更为 {}", message.getPlayerId(), oldState, message.getNewState());
                }
            }

            getSender().tell(new StateUpdateResponse(true, "状态更新成功"), getSelf());

        } catch (Exception e) {
            log.error("更新玩家 {} 状态失败", message.getPlayerId(), e);
            getSender().tell(new StateUpdateResponse(false, "状态更新失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理玩家数据同步消息
     */
    private void handleSyncPlayerData(SyncPlayerDataMessage message) {
        log.debug("同步玩家 {} 数据", message.getPlayerId());

        try {
            savePlayerData();
            incrementMessageCounter("sync");
            getSender().tell(new SyncDataResponse(true, "数据同步成功"), getSelf());

        } catch (Exception e) {
            log.error("同步玩家 {} 数据失败", message.getPlayerId(), e);
            getSender().tell(new SyncDataResponse(false, "数据同步失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理发放奖励消息
     */
    private void handleGrantReward(GrantRewardMessage message) {
        log.info("发放奖励给玩家 {}，金币: {}, 钻石: {}, 经验: {}, 原因: {}",
                message.getPlayerId(), message.getCoins(), message.getDiamonds(),
                message.getExperience(), message.getReason());

        try {
            boolean leveledUp = false;

            if (player != null) {
                // 发放金币
                if (message.getCoins() > 0) {
                    player.addCoins(message.getCoins());
                }

                // 发放钻石
                if (message.getDiamonds() > 0) {
                    player.addDiamonds(message.getDiamonds());
                }

                // 发放经验
                if (message.getExperience() > 0) {
                    leveledUp = player.addExperience(message.getExperience());
                }

                incrementMessageCounter("reward");
            }

            getSender().tell(new RewardResponse(true, "奖励发放成功", leveledUp), getSelf());

        } catch (Exception e) {
            log.error("发放奖励给玩家 {} 失败", message.getPlayerId(), e);
            getSender().tell(new RewardResponse(false, "奖励发放失败: " + e.getMessage(), false), getSelf());
        }
    }

    /**
     * 处理发送消息给玩家
     */
    private void handleSendMessageToPlayer(SendMessageToPlayerMessage message) {
        log.debug("发送消息给玩家 {}: {}", message.getPlayerId(), message.getMessage().getClass().getSimpleName());

        try {
            // 这里可以通过网络连接发送消息给客户端
            // 目前只记录日志
            incrementMessageCounter("send_message");
            log.info("向玩家 {} 发送消息: {}", message.getPlayerId(), message.getMessage());

        } catch (Exception e) {
            log.error("发送消息给玩家 {} 失败", message.getPlayerId(), e);
        }
    }

    /**
     * 处理定时器消息
     */
    private void handlePlayerTimer(PlayerTimerMessage message) {
        String timerType = message.getTimerType();
        log.debug("处理玩家 {} 定时器: {}", message.getPlayerId(), timerType);

        try {
            switch (timerType) {
                case "data_sync":
                    savePlayerData();
                    scheduleDataSync(); // 重新调度
                    break;
                case "heartbeat":
                    checkHeartbeat();
                    scheduleHeartbeat(); // 重新调度
                    break;
                default:
                    log.warn("未知的定时器类型: {}", timerType);
                    break;
            }
        } catch (Exception e) {
            log.error("处理玩家 {} 定时器 {} 失败", message.getPlayerId(), timerType, e);
        }
    }

    /**
     * 处理未知消息
     */
    private void handleUnknownMessage(Object message) {
        log.warn("玩家Actor {} 收到未知消息: {}", getActorId(), message.getClass().getSimpleName());
        incrementMessageCounter("unknown");
    }

    /**
     * 调度数据同步任务
     */
    private void scheduleDataSync() {
        if (getContext() != null) {
            Object timer = getContext().getSystem().scheduler().scheduleOnce(
                    Duration.ofMinutes(5),
                    getSelf(),
                    new PlayerTimerMessage(playerId, "data_sync"),
                    getContext().getDispatcher(),
                    getSelf()
            );
            timers.put("data_sync", timer);
        }
    }

    /**
     * 调度心跳检查任务
     */
    private void scheduleHeartbeat() {
        if (getContext() != null) {
            Object timer = getContext().getSystem().scheduler().scheduleOnce(
                    Duration.ofMinutes(1),
                    getSelf(),
                    new PlayerTimerMessage(playerId, "heartbeat"),
                    getContext().getDispatcher(),
                    getSelf()
            );
            timers.put("heartbeat", timer);
        }
    }

    /**
     * 检查心跳
     */
    private void checkHeartbeat() {
        if (lastActiveTime != null) {
            Duration inactiveTime = Duration.between(lastActiveTime, LocalDateTime.now());
            if (inactiveTime.toMinutes() > 30) {
                log.warn("玩家 {} 长时间未活跃，准备断开连接", playerId);
                // 可以发送超时消息或直接断开连接
            }
        }
    }

    /**
     * 保存玩家数据
     */
    private void savePlayerData() {
        if (player != null) {
            try {
                // 这里应该调用PlayerService保存数据
                // playerService.savePlayer(player);
                log.debug("保存玩家 {} 数据", playerId);
            } catch (Exception e) {
                log.error("保存玩家 {} 数据失败", playerId, e);
            }
        }
    }

    /**
     * 增加消息计数器
     */
    private void incrementMessageCounter(String messageType) {
        messageCounters.merge(messageType, 1L, Long::sum);
    }

    /**
     * 获取玩家统计信息
     */
    public Map<String, Object> getPlayerStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("playerId", playerId);
        stats.put("sessionId", sessionId);
        stats.put("lastActiveTime", lastActiveTime);
        stats.put("messageCounters", new ConcurrentHashMap<>(messageCounters));
        if (player != null) {
            stats.put("playerState", player.getCurrentState());
            stats.put("playerLevel", player.getLevel());
        }
        return stats;
    }

    // 响应消息类
    public static class PlayerLoginResponse {
        private final boolean success;
        private final String message;

        public PlayerLoginResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class PlayerLogoutResponse {
        private final boolean success;
        private final String message;

        public PlayerLogoutResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class StateUpdateResponse {
        private final boolean success;
        private final String message;

        public StateUpdateResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class SyncDataResponse {
        private final boolean success;
        private final String message;

        public SyncDataResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class RewardResponse {
        private final boolean success;
        private final String message;
        private final boolean leveledUp;

        public RewardResponse(boolean success, String message, boolean leveledUp) {
            this.success = success;
            this.message = message;
            this.leveledUp = leveledUp;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public boolean isLeveledUp() { return leveledUp; }
    }
}