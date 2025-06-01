/*
 * 文件名: SceneActor.java
 * 用途: 场景Actor示例
 * 实现内容:
 *   - 场景相关消息处理和实体管理
 *   - 实体进入离开和广播消息
 *   - 定时逻辑和场景交互
 *   - 性能优化和并发处理
 *   - 场景状态同步和监控
 * 技术选型:
 *   - 继承Actor基类实现消息处理
 *   - 定时器实现场景定时逻辑
 *   - 批量处理优化性能
 *   - 事件发布机制同步状态
 * 依赖关系:
 *   - 与Scene实体协作进行场景管理
 *   - 被SceneManager创建和管理
 *   - 与PlayerActor等其他Actor通信
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.scene;

import com.lx.gameserver.frame.actor.core.Actor;
import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Receive;
import com.lx.gameserver.business.logic.player.Player;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景Actor示例
 * <p>
 * 展示如何使用Actor模型处理场景相关的业务逻辑，
 * 包括实体管理、消息广播、定时任务等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
public class SceneActor extends Actor {

    /**
     * 场景消息基类
     */
    public static abstract class SceneMessage {
        private final Long sceneId;
        private final LocalDateTime timestamp;

        public SceneMessage(Long sceneId) {
            this.sceneId = sceneId;
            this.timestamp = LocalDateTime.now();
        }

        public Long getSceneId() {
            return sceneId;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 玩家进入场景消息
     */
    public static class PlayerEnterMessage extends SceneMessage {
        private final Player player;
        private final Scene.Position position;

        public PlayerEnterMessage(Long sceneId, Player player, Scene.Position position) {
            super(sceneId);
            this.player = player;
            this.position = position;
        }

        public Player getPlayer() {
            return player;
        }

        public Scene.Position getPosition() {
            return position;
        }
    }

    /**
     * 玩家离开场景消息
     */
    public static class PlayerLeaveMessage extends SceneMessage {
        private final Long playerId;
        private final String reason;

        public PlayerLeaveMessage(Long sceneId, Long playerId, String reason) {
            super(sceneId);
            this.playerId = playerId;
            this.reason = reason;
        }

        public Long getPlayerId() {
            return playerId;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 玩家位置更新消息
     */
    public static class PlayerMoveMessage extends SceneMessage {
        private final Long playerId;
        private final Scene.Position position;

        public PlayerMoveMessage(Long sceneId, Long playerId, Scene.Position position) {
            super(sceneId);
            this.playerId = playerId;
            this.position = position;
        }

        public Long getPlayerId() {
            return playerId;
        }

        public Scene.Position getPosition() {
            return position;
        }
    }

    /**
     * 场景广播消息
     */
    public static class BroadcastMessage extends SceneMessage {
        private final Object message;
        private final java.util.function.Predicate<Player> filter;

        public BroadcastMessage(Long sceneId, Object message) {
            this(sceneId, message, null);
        }

        public BroadcastMessage(Long sceneId, Object message, java.util.function.Predicate<Player> filter) {
            super(sceneId);
            this.message = message;
            this.filter = filter;
        }

        public Object getMessage() {
            return message;
        }

        public java.util.function.Predicate<Player> getFilter() {
            return filter;
        }
    }

    /**
     * 范围广播消息
     */
    public static class RangeBroadcastMessage extends SceneMessage {
        private final Scene.Position center;
        private final double range;
        private final Object message;

        public RangeBroadcastMessage(Long sceneId, Scene.Position center, double range, Object message) {
            super(sceneId);
            this.center = center;
            this.range = range;
            this.message = message;
        }

        public Scene.Position getCenter() {
            return center;
        }

        public double getRange() {
            return range;
        }

        public Object getMessage() {
            return message;
        }
    }

    /**
     * 场景定时器消息
     */
    public static class SceneTimerMessage extends SceneMessage {
        private final String timerType;

        public SceneTimerMessage(Long sceneId, String timerType) {
            super(sceneId);
            this.timerType = timerType;
        }

        public String getTimerType() {
            return timerType;
        }
    }

    /**
     * 查询场景信息消息
     */
    public static class QuerySceneInfoMessage extends SceneMessage {
        public QuerySceneInfoMessage(Long sceneId) {
            super(sceneId);
        }
    }

    /**
     * 实体创建消息
     */
    public static class CreateEntityMessage extends SceneMessage {
        private final String entityType;
        private final Scene.Position position;
        private final Map<String, Object> properties;

        public CreateEntityMessage(Long sceneId, String entityType, Scene.Position position, Map<String, Object> properties) {
            super(sceneId);
            this.entityType = entityType;
            this.position = position;
            this.properties = properties;
        }

        public String getEntityType() {
            return entityType;
        }

        public Scene.Position getPosition() {
            return position;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }
    }

    /**
     * 实体销毁消息
     */
    public static class DestroyEntityMessage extends SceneMessage {
        private final Long entityId;

        public DestroyEntityMessage(Long sceneId, Long entityId) {
            super(sceneId);
            this.entityId = entityId;
        }

        public Long getEntityId() {
            return entityId;
        }
    }

    /** 场景ID */
    private final Long sceneId;

    /** 场景对象 */
    private Scene scene;

    /** 消息计数器 */
    private final Map<String, Long> messageCounters = new ConcurrentHashMap<>();

    /** 定时器引用 */
    private final Map<String, Object> timers = new ConcurrentHashMap<>();

    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;

    /** 性能统计 */
    private final Map<String, Object> performanceStats = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public SceneActor(Long sceneId, Scene scene) {
        this.sceneId = sceneId;
        this.scene = scene;
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * Actor启动前回调
     */
    @Override
    protected void preStart() throws Exception {
        super.preStart();
        log.info("场景Actor {} 启动，场景ID: {}", getActorId(), sceneId);

        // 启动定时任务
        scheduleSceneUpdate();
        schedulePerformanceMonitor();
    }

    /**
     * Actor停止后回调
     */
    @Override
    protected void postStop() throws Exception {
        super.postStop();
        log.info("场景Actor {} 停止，场景ID: {}", getActorId(), sceneId);

        // 清理定时器
        timers.clear();
    }

    /**
     * Actor重启前回调
     */
    @Override
    protected void preRestart(Throwable reason, Object message) throws Exception {
        super.preRestart(reason, message);
        log.warn("场景Actor {} 重启，原因: {}, 消息: {}", getActorId(), reason.getMessage(), message);
    }

    /**
     * Actor重启后回调
     */
    @Override
    protected void postRestart(Throwable reason) throws Exception {
        super.postRestart(reason);
        log.info("场景Actor {} 重启完成，场景ID: {}", getActorId(), sceneId);

        // 重新启动定时任务
        scheduleSceneUpdate();
        schedulePerformanceMonitor();
    }

    /**
     * 创建消息接收器
     */
    @Override
    protected Receive createReceive() {
        return receiveBuilder()
                .match(PlayerEnterMessage.class, this::handlePlayerEnter)
                .match(PlayerLeaveMessage.class, this::handlePlayerLeave)
                .match(PlayerMoveMessage.class, this::handlePlayerMove)
                .match(BroadcastMessage.class, this::handleBroadcast)
                .match(RangeBroadcastMessage.class, this::handleRangeBroadcast)
                .match(SceneTimerMessage.class, this::handleSceneTimer)
                .match(QuerySceneInfoMessage.class, this::handleQuerySceneInfo)
                .match(CreateEntityMessage.class, this::handleCreateEntity)
                .match(DestroyEntityMessage.class, this::handleDestroyEntity)
                .matchAny(this::handleUnknownMessage)
                .build();
    }

    /**
     * 处理玩家进入消息
     */
    private void handlePlayerEnter(PlayerEnterMessage message) {
        log.info("处理玩家 {} 进入场景 {}", message.getPlayer().getPlayerId(), message.getSceneId());

        try {
            if (scene != null) {
                boolean success = scene.playerEnter(message.getPlayer(), message.getPosition());
                incrementMessageCounter("player_enter");
                
                // 发送响应
                getSender().tell(new PlayerEnterResponse(success, success ? "进入成功" : "进入失败"), getSelf());
                
                if (success) {
                    // 向其他玩家广播玩家进入事件
                    broadcastPlayerEnterEvent(message.getPlayer());
                }
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("处理玩家 {} 进入场景失败", message.getPlayer().getPlayerId(), e);
            getSender().tell(new PlayerEnterResponse(false, "进入失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理玩家离开消息
     */
    private void handlePlayerLeave(PlayerLeaveMessage message) {
        log.info("处理玩家 {} 离开场景 {}，原因: {}", message.getPlayerId(), message.getSceneId(), message.getReason());

        try {
            if (scene != null) {
                boolean success = scene.playerLeave(message.getPlayerId(), message.getReason());
                incrementMessageCounter("player_leave");
                
                // 发送响应
                getSender().tell(new PlayerLeaveResponse(success, success ? "离开成功" : "离开失败"), getSelf());
                
                if (success) {
                    // 向其他玩家广播玩家离开事件
                    broadcastPlayerLeaveEvent(message.getPlayerId(), message.getReason());
                }
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("处理玩家 {} 离开场景失败", message.getPlayerId(), e);
            getSender().tell(new PlayerLeaveResponse(false, "离开失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理玩家移动消息
     */
    private void handlePlayerMove(PlayerMoveMessage message) {
        log.debug("处理玩家 {} 在场景 {} 中移动", message.getPlayerId(), message.getSceneId());

        try {
            if (scene != null) {
                scene.updatePlayerPosition(message.getPlayerId(), message.getPosition());
                incrementMessageCounter("player_move");
                
                // 广播位置更新给范围内的玩家
                broadcastPositionUpdate(message.getPlayerId(), message.getPosition());
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("处理玩家 {} 移动失败", message.getPlayerId(), e);
        }
    }

    /**
     * 处理广播消息
     */
    private void handleBroadcast(BroadcastMessage message) {
        log.debug("处理场景 {} 广播消息", message.getSceneId());

        try {
            if (scene != null) {
                scene.broadcast(message.getMessage(), message.getFilter());
                incrementMessageCounter("broadcast");
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("处理场景 {} 广播消息失败", message.getSceneId(), e);
        }
    }

    /**
     * 处理范围广播消息
     */
    private void handleRangeBroadcast(RangeBroadcastMessage message) {
        log.debug("处理场景 {} 范围广播消息", message.getSceneId());

        try {
            if (scene != null) {
                scene.broadcastInRange(message.getCenter(), message.getRange(), message.getMessage());
                incrementMessageCounter("range_broadcast");
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("处理场景 {} 范围广播消息失败", message.getSceneId(), e);
        }
    }

    /**
     * 处理场景定时器消息
     */
    private void handleSceneTimer(SceneTimerMessage message) {
        String timerType = message.getTimerType();
        log.debug("处理场景 {} 定时器: {}", message.getSceneId(), timerType);

        try {
            switch (timerType) {
                case "scene_update":
                    performSceneUpdate();
                    scheduleSceneUpdate(); // 重新调度
                    break;
                case "performance_monitor":
                    performPerformanceMonitor();
                    schedulePerformanceMonitor(); // 重新调度
                    break;
                default:
                    log.warn("未知的定时器类型: {}", timerType);
                    break;
            }
        } catch (Exception e) {
            log.error("处理场景 {} 定时器 {} 失败", message.getSceneId(), timerType, e);
        }
    }

    /**
     * 处理查询场景信息消息
     */
    private void handleQuerySceneInfo(QuerySceneInfoMessage message) {
        log.debug("处理查询场景 {} 信息", message.getSceneId());

        try {
            Map<String, Object> sceneInfo = new HashMap<>();
            
            if (scene != null) {
                sceneInfo = scene.getStatistics();
            }
            
            // 添加Actor统计信息
            sceneInfo.put("actorId", getActorId());
            sceneInfo.put("messageCounters", new HashMap<>(messageCounters));
            sceneInfo.put("lastActiveTime", lastActiveTime);
            sceneInfo.put("performanceStats", new HashMap<>(performanceStats));
            
            incrementMessageCounter("query_info");
            getSender().tell(new SceneInfoResponse(true, sceneInfo), getSelf());

        } catch (Exception e) {
            log.error("查询场景 {} 信息失败", message.getSceneId(), e);
            getSender().tell(new SceneInfoResponse(false, Map.of("error", e.getMessage())), getSelf());
        }
    }

    /**
     * 处理创建实体消息
     */
    private void handleCreateEntity(CreateEntityMessage message) {
        log.debug("处理在场景 {} 中创建实体", message.getSceneId());

        try {
            // 简化实现：生成实体ID并添加到场景
            Long entityId = System.currentTimeMillis() + (long) (Math.random() * 1000);
            
            if (scene != null) {
                boolean success = scene.entityEnter(entityId, message.getPosition());
                incrementMessageCounter("create_entity");
                
                getSender().tell(new CreateEntityResponse(success, entityId, 
                        success ? "创建成功" : "创建失败"), getSelf());
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("创建实体失败", e);
            getSender().tell(new CreateEntityResponse(false, null, "创建失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理销毁实体消息
     */
    private void handleDestroyEntity(DestroyEntityMessage message) {
        log.debug("处理销毁场景 {} 中的实体 {}", message.getSceneId(), message.getEntityId());

        try {
            if (scene != null) {
                boolean success = scene.entityLeave(message.getEntityId());
                incrementMessageCounter("destroy_entity");
                
                getSender().tell(new DestroyEntityResponse(success, 
                        success ? "销毁成功" : "销毁失败"), getSelf());
            }

            updateActiveTime();

        } catch (Exception e) {
            log.error("销毁实体 {} 失败", message.getEntityId(), e);
            getSender().tell(new DestroyEntityResponse(false, "销毁失败: " + e.getMessage()), getSelf());
        }
    }

    /**
     * 处理未知消息
     */
    private void handleUnknownMessage(Object message) {
        log.warn("场景Actor {} 收到未知消息: {}", getActorId(), message.getClass().getSimpleName());
        incrementMessageCounter("unknown");
    }

    /**
     * 调度场景更新任务
     */
    private void scheduleSceneUpdate() {
        if (getContext() != null) {
            Object timer = getContext().getSystem().scheduler().scheduleOnce(
                    Duration.ofSeconds(5),
                    getSelf(),
                    new SceneTimerMessage(sceneId, "scene_update"),
                    getContext().getDispatcher(),
                    getSelf()
            );
            timers.put("scene_update", timer);
        }
    }

    /**
     * 调度性能监控任务
     */
    private void schedulePerformanceMonitor() {
        if (getContext() != null) {
            Object timer = getContext().getSystem().scheduler().scheduleOnce(
                    Duration.ofMinutes(1),
                    getSelf(),
                    new SceneTimerMessage(sceneId, "performance_monitor"),
                    getContext().getDispatcher(),
                    getSelf()
            );
            timers.put("performance_monitor", timer);
        }
    }

    /**
     * 执行场景更新
     */
    private void performSceneUpdate() {
        if (scene != null && scene.isRunning()) {
            // 执行场景定时逻辑
            log.debug("执行场景 {} 定时更新", sceneId);
            
            // 这里可以添加场景的定时逻辑，如：
            // - NPC AI更新
            // - 环境效果更新
            // - 资源刷新
            // - 状态检查等
        }
    }

    /**
     * 执行性能监控
     */
    private void performPerformanceMonitor() {
        try {
            // 收集性能统计信息
            performanceStats.put("messageProcessed", messageCounters.values().stream().mapToLong(Long::longValue).sum());
            performanceStats.put("lastActiveTime", lastActiveTime);
            
            if (scene != null) {
                performanceStats.put("playerCount", scene.getPlayerCount());
                performanceStats.put("entityCount", scene.getEntityCount());
            }
            
            performanceStats.put("timestamp", LocalDateTime.now());
            
            log.debug("场景 {} 性能统计: {}", sceneId, performanceStats);
            
        } catch (Exception e) {
            log.error("执行场景 {} 性能监控失败", sceneId, e);
        }
    }

    /**
     * 广播玩家进入事件
     */
    private void broadcastPlayerEnterEvent(Player player) {
        if (scene != null) {
            Map<String, Object> event = Map.of(
                    "type", "player_enter",
                    "playerId", player.getPlayerId(),
                    "playerName", player.getDisplayName(),
                    "timestamp", LocalDateTime.now()
            );
            
            // 排除自己
            scene.broadcast(event, p -> !p.getPlayerId().equals(player.getPlayerId()));
        }
    }

    /**
     * 广播玩家离开事件
     */
    private void broadcastPlayerLeaveEvent(Long playerId, String reason) {
        if (scene != null) {
            Map<String, Object> event = Map.of(
                    "type", "player_leave",
                    "playerId", playerId,
                    "reason", reason,
                    "timestamp", LocalDateTime.now()
            );
            
            scene.broadcast(event);
        }
    }

    /**
     * 广播位置更新
     */
    private void broadcastPositionUpdate(Long playerId, Scene.Position position) {
        if (scene != null) {
            Map<String, Object> event = Map.of(
                    "type", "position_update",
                    "playerId", playerId,
                    "position", position,
                    "timestamp", LocalDateTime.now()
            );
            
            // 只广播给范围内的玩家
            scene.broadcastInRange(position, scene.getAoiRange(), event);
        }
    }

    /**
     * 增加消息计数器
     */
    private void incrementMessageCounter(String messageType) {
        messageCounters.merge(messageType, 1L, Long::sum);
    }

    /**
     * 更新活跃时间
     */
    private void updateActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }

    // ========== 响应消息类 ==========

    public static class PlayerEnterResponse {
        private final boolean success;
        private final String message;

        public PlayerEnterResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class PlayerLeaveResponse {
        private final boolean success;
        private final String message;

        public PlayerLeaveResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class SceneInfoResponse {
        private final boolean success;
        private final Map<String, Object> sceneInfo;

        public SceneInfoResponse(boolean success, Map<String, Object> sceneInfo) {
            this.success = success;
            this.sceneInfo = sceneInfo;
        }

        public boolean isSuccess() { return success; }
        public Map<String, Object> getSceneInfo() { return sceneInfo; }
    }

    public static class CreateEntityResponse {
        private final boolean success;
        private final Long entityId;
        private final String message;

        public CreateEntityResponse(boolean success, Long entityId, String message) {
            this.success = success;
            this.entityId = entityId;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public Long getEntityId() { return entityId; }
        public String getMessage() { return message; }
    }

    public static class DestroyEntityResponse {
        private final boolean success;
        private final String message;

        public DestroyEntityResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}