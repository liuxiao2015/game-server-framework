/*
 * 文件名: GameEventListener.java
 * 用途: 游戏事件监听器
 * 实现内容:
 *   - 游戏事件类型定义和监听器注册
 *   - 事件处理和优先级控制
 *   - 异常处理和错误恢复
 *   - 事件过滤和条件处理
 *   - 性能监控和统计功能
 * 技术选型:
 *   - 观察者模式实现事件监听
 *   - 注解驱动的事件处理
 *   - 异步事件处理支持
 *   - 事件总线集成
 * 依赖关系:
 *   - 被各个业务模块实现进行事件处理
 *   - 与EventBus集成提供事件分发
 *   - 支持插件和模块的事件扩展
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 游戏事件监听器接口
 * <p>
 * 定义了游戏事件处理的标准接口，支持事件的监听、
 * 处理、过滤和优先级控制等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface GameEventListener {

    /**
     * 事件处理优先级
     */
    enum EventPriority {
        /** 最高优先级 */
        HIGHEST(1),
        /** 高优先级 */
        HIGH(2),
        /** 普通优先级 */
        NORMAL(3),
        /** 低优先级 */
        LOW(4),
        /** 最低优先级 */
        LOWEST(5);

        private final int value;

        EventPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 事件处理结果
     */
    enum EventResult {
        /** 继续处理 */
        CONTINUE,
        /** 停止处理 */
        STOP,
        /** 取消事件 */
        CANCEL
    }

    /**
     * 事件处理注解
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface EventHandler {
        /**
         * 事件优先级
         */
        EventPriority priority() default EventPriority.NORMAL;

        /**
         * 是否异步处理
         */
        boolean async() default false;

        /**
         * 事件过滤条件（SpEL表达式）
         */
        String condition() default "";

        /**
         * 是否忽略取消的事件
         */
        boolean ignoreCancelled() default false;
    }

    /**
     * 游戏事件基类
     */
    abstract class GameEvent {
        /** 事件ID */
        private final String eventId;
        /** 事件类型 */
        private final String eventType;
        /** 事件时间戳 */
        private final LocalDateTime timestamp;
        /** 事件源 */
        private final Object source;
        /** 是否已取消 */
        private boolean cancelled = false;
        /** 事件数据 */
        private final Map<String, Object> data;

        protected GameEvent(String eventType, Object source, Map<String, Object> data) {
            this.eventId = generateEventId();
            this.eventType = eventType;
            this.source = source;
            this.timestamp = LocalDateTime.now();
            this.data = data != null ? data : Map.of();
        }

        private String generateEventId() {
            return System.currentTimeMillis() + "_" + System.nanoTime();
        }

        // Getters
        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Object getSource() { return source; }
        public boolean isCancelled() { return cancelled; }
        public Map<String, Object> getData() { return data; }

        /**
         * 取消事件
         */
        public void cancel() {
            this.cancelled = true;
        }

        /**
         * 获取事件数据
         */
        @SuppressWarnings("unchecked")
        public <T> T getData(String key) {
            return (T) data.get(key);
        }

        /**
         * 获取事件数据
         */
        @SuppressWarnings("unchecked")
        public <T> T getData(String key, T defaultValue) {
            return (T) data.getOrDefault(key, defaultValue);
        }
    }

    // ========== 系统事件 ==========

    /**
     * 系统启动事件
     */
    class SystemStartEvent extends GameEvent {
        public SystemStartEvent() {
            super("system.start", "system", Map.of());
        }
    }

    /**
     * 系统关闭事件
     */
    class SystemShutdownEvent extends GameEvent {
        public SystemShutdownEvent() {
            super("system.shutdown", "system", Map.of());
        }
    }

    /**
     * 模块加载事件
     */
    class ModuleLoadEvent extends GameEvent {
        public ModuleLoadEvent(String moduleName) {
            super("module.load", "module", Map.of("moduleName", moduleName));
        }

        public String getModuleName() {
            return getData("moduleName");
        }
    }

    /**
     * 模块卸载事件
     */
    class ModuleUnloadEvent extends GameEvent {
        public ModuleUnloadEvent(String moduleName) {
            super("module.unload", "module", Map.of("moduleName", moduleName));
        }

        public String getModuleName() {
            return getData("moduleName");
        }
    }

    // ========== 玩家事件 ==========

    /**
     * 玩家登录事件
     */
    class PlayerLoginEvent extends GameEvent {
        public PlayerLoginEvent(Long playerId, String sessionId) {
            super("player.login", "player", Map.of(
                    "playerId", playerId,
                    "sessionId", sessionId
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getSessionId() {
            return getData("sessionId");
        }
    }

    /**
     * 玩家登出事件
     */
    class PlayerLogoutEvent extends GameEvent {
        public PlayerLogoutEvent(Long playerId, String reason) {
            super("player.logout", "player", Map.of(
                    "playerId", playerId,
                    "reason", reason
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getReason() {
            return getData("reason");
        }
    }

    /**
     * 玩家升级事件
     */
    class PlayerLevelUpEvent extends GameEvent {
        public PlayerLevelUpEvent(Long playerId, int oldLevel, int newLevel) {
            super("player.levelup", "player", Map.of(
                    "playerId", playerId,
                    "oldLevel", oldLevel,
                    "newLevel", newLevel
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public int getOldLevel() {
            return getData("oldLevel");
        }

        public int getNewLevel() {
            return getData("newLevel");
        }
    }

    /**
     * 玩家属性变更事件
     */
    class PlayerAttributeChangeEvent extends GameEvent {
        public PlayerAttributeChangeEvent(Long playerId, String attribute, Object oldValue, Object newValue) {
            super("player.attribute.change", "player", Map.of(
                    "playerId", playerId,
                    "attribute", attribute,
                    "oldValue", oldValue,
                    "newValue", newValue
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getAttribute() {
            return getData("attribute");
        }

        public Object getOldValue() {
            return getData("oldValue");
        }

        public Object getNewValue() {
            return getData("newValue");
        }
    }

    // ========== 场景事件 ==========

    /**
     * 玩家进入场景事件
     */
    class PlayerEnterSceneEvent extends GameEvent {
        public PlayerEnterSceneEvent(Long playerId, Long sceneId) {
            super("scene.player.enter", "scene", Map.of(
                    "playerId", playerId,
                    "sceneId", sceneId
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public Long getSceneId() {
            return getData("sceneId");
        }
    }

    /**
     * 玩家离开场景事件
     */
    class PlayerLeaveSceneEvent extends GameEvent {
        public PlayerLeaveSceneEvent(Long playerId, Long sceneId, String reason) {
            super("scene.player.leave", "scene", Map.of(
                    "playerId", playerId,
                    "sceneId", sceneId,
                    "reason", reason
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public Long getSceneId() {
            return getData("sceneId");
        }

        public String getReason() {
            return getData("reason");
        }
    }

    /**
     * 场景创建事件
     */
    class SceneCreateEvent extends GameEvent {
        public SceneCreateEvent(Long sceneId, String sceneType) {
            super("scene.create", "scene", Map.of(
                    "sceneId", sceneId,
                    "sceneType", sceneType
            ));
        }

        public Long getSceneId() {
            return getData("sceneId");
        }

        public String getSceneType() {
            return getData("sceneType");
        }
    }

    /**
     * 场景销毁事件
     */
    class SceneDestroyEvent extends GameEvent {
        public SceneDestroyEvent(Long sceneId, String reason) {
            super("scene.destroy", "scene", Map.of(
                    "sceneId", sceneId,
                    "reason", reason
            ));
        }

        public Long getSceneId() {
            return getData("sceneId");
        }

        public String getReason() {
            return getData("reason");
        }
    }

    // ========== 游戏事件 ==========

    /**
     * 战斗开始事件
     */
    class BattleStartEvent extends GameEvent {
        public BattleStartEvent(String battleId, java.util.List<Long> participants) {
            super("battle.start", "battle", Map.of(
                    "battleId", battleId,
                    "participants", participants
            ));
        }

        public String getBattleId() {
            return getData("battleId");
        }

        @SuppressWarnings("unchecked")
        public java.util.List<Long> getParticipants() {
            return getData("participants");
        }
    }

    /**
     * 战斗结束事件
     */
    class BattleEndEvent extends GameEvent {
        public BattleEndEvent(String battleId, String result, java.util.List<Long> winners) {
            super("battle.end", "battle", Map.of(
                    "battleId", battleId,
                    "result", result,
                    "winners", winners
            ));
        }

        public String getBattleId() {
            return getData("battleId");
        }

        public String getResult() {
            return getData("result");
        }

        @SuppressWarnings("unchecked")
        public java.util.List<Long> getWinners() {
            return getData("winners");
        }
    }

    /**
     * 物品获得事件
     */
    class ItemObtainEvent extends GameEvent {
        public ItemObtainEvent(Long playerId, String itemId, int quantity, String source) {
            super("item.obtain", "item", Map.of(
                    "playerId", playerId,
                    "itemId", itemId,
                    "quantity", quantity,
                    "source", source
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getItemId() {
            return getData("itemId");
        }

        public int getQuantity() {
            return getData("quantity");
        }

        public String getSource() {
            return getData("source");
        }
    }

    /**
     * 物品使用事件
     */
    class ItemUseEvent extends GameEvent {
        public ItemUseEvent(Long playerId, String itemId, int quantity) {
            super("item.use", "item", Map.of(
                    "playerId", playerId,
                    "itemId", itemId,
                    "quantity", quantity
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getItemId() {
            return getData("itemId");
        }

        public int getQuantity() {
            return getData("quantity");
        }
    }

    // ========== 经济事件 ==========

    /**
     * 货币变更事件
     */
    class CurrencyChangeEvent extends GameEvent {
        public CurrencyChangeEvent(Long playerId, String currencyType, long oldAmount, long newAmount, String reason) {
            super("currency.change", "economy", Map.of(
                    "playerId", playerId,
                    "currencyType", currencyType,
                    "oldAmount", oldAmount,
                    "newAmount", newAmount,
                    "reason", reason
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getCurrencyType() {
            return getData("currencyType");
        }

        public long getOldAmount() {
            return getData("oldAmount");
        }

        public long getNewAmount() {
            return getData("newAmount");
        }

        public String getReason() {
            return getData("reason");
        }
    }

    /**
     * 交易事件
     */
    class TradeEvent extends GameEvent {
        public TradeEvent(Long playerId, String tradeType, String itemId, int quantity, long cost) {
            super("trade", "economy", Map.of(
                    "playerId", playerId,
                    "tradeType", tradeType,
                    "itemId", itemId,
                    "quantity", quantity,
                    "cost", cost
            ));
        }

        public Long getPlayerId() {
            return getData("playerId");
        }

        public String getTradeType() {
            return getData("tradeType");
        }

        public String getItemId() {
            return getData("itemId");
        }

        public int getQuantity() {
            return getData("quantity");
        }

        public long getCost() {
            return getData("cost");
        }
    }

    // ========== 核心接口方法 ==========

    /**
     * 处理事件
     *
     * @param event 事件对象
     * @return 处理结果
     */
    EventResult handleEvent(GameEvent event);

    /**
     * 获取监听器名称
     *
     * @return 监听器名称
     */
    default String getListenerName() {
        return getClass().getSimpleName();
    }

    /**
     * 获取监听器优先级
     *
     * @return 优先级
     */
    default EventPriority getPriority() {
        return EventPriority.NORMAL;
    }

    /**
     * 是否支持异步处理
     *
     * @return 是否支持异步
     */
    default boolean supportsAsync() {
        return false;
    }

    /**
     * 异步处理事件
     *
     * @param event 事件对象
     * @return 异步处理结果
     */
    default CompletableFuture<EventResult> handleEventAsync(GameEvent event) {
        return CompletableFuture.supplyAsync(() -> handleEvent(event));
    }

    /**
     * 检查是否感兴趣的事件
     *
     * @param eventType 事件类型
     * @return 是否感兴趣
     */
    default boolean isInterestedIn(String eventType) {
        return true;
    }

    /**
     * 检查是否感兴趣的事件
     *
     * @param eventClass 事件类
     * @return 是否感兴趣
     */
    default boolean isInterestedIn(Class<? extends GameEvent> eventClass) {
        return true;
    }

    /**
     * 事件过滤
     *
     * @param event 事件对象
     * @return 是否通过过滤
     */
    default boolean filter(GameEvent event) {
        return true;
    }

    /**
     * 处理异常
     *
     * @param event     事件对象
     * @param throwable 异常
     */
    default void handleException(GameEvent event, Throwable throwable) {
        // 默认实现：记录日志
        System.err.println("事件处理异常: " + event.getEventType() + ", 监听器: " + getListenerName());
        throwable.printStackTrace();
    }

    /**
     * 监听器启用检查
     *
     * @return 是否启用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    default Map<String, Object> getStatistics() {
        return Map.of(
                "listenerName", getListenerName(),
                "priority", getPriority().name(),
                "supportsAsync", supportsAsync(),
                "enabled", isEnabled()
        );
    }
}