/*
 * 文件名: GameWorld.java
 * 用途: 游戏世界管理
 * 实现内容:
 *   - 游戏世界的统一管理和调度
 *   - 场景管理和玩家管理
 *   - 实体管理和时间管理
 *   - 世界事件处理和状态同步
 *   - 世界级别的游戏逻辑协调
 * 技术选型:
 *   - ECS架构进行实体管理
 *   - Actor模型处理并发逻辑
 *   - 事件驱动架构进行状态同步
 *   - 时间轮算法进行定时任务
 * 依赖关系:
 *   - 集成ECS World进行实体管理
 *   - 与场景管理器和玩家管理器协作
 *   - 被LogicServer管理和调度
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.core;

import com.lx.gameserver.frame.ecs.core.World;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.event.EventBus;
import com.lx.gameserver.business.logic.player.PlayerManager;
import com.lx.gameserver.business.logic.scene.SceneManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.Set;

/**
 * 游戏世界管理
 * <p>
 * 作为游戏世界的顶层管理器，负责协调所有游戏对象的
 * 创建、更新和销毁。管理全局时间、处理世界级事件，
 * 并提供统一的世界状态查询接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
@Getter
public class GameWorld {

    /**
     * 世界状态枚举
     */
    public enum WorldState {
        /** 初始化中 */
        INITIALIZING,
        /** 运行中 */
        RUNNING,
        /** 暂停中 */
        PAUSED,
        /** 停止中 */
        STOPPING,
        /** 已停止 */
        STOPPED,
        /** 错误状态 */
        ERROR
    }

    /**
     * 世界事件基类
     */
    public static abstract class WorldEvent {
        private final LocalDateTime timestamp = LocalDateTime.now();
        private final String worldId;

        public WorldEvent(String worldId) {
            this.worldId = worldId;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getWorldId() {
            return worldId;
        }
    }

    /**
     * 世界启动事件
     */
    public static class WorldStartedEvent extends WorldEvent {
        public WorldStartedEvent(String worldId) {
            super(worldId);
        }
    }

    /**
     * 世界停止事件
     */
    public static class WorldStoppedEvent extends WorldEvent {
        public WorldStoppedEvent(String worldId) {
            super(worldId);
        }
    }

    /**
     * 时间tick事件
     */
    public static class WorldTickEvent extends WorldEvent {
        private final long tickCount;
        private final Duration deltaTime;

        public WorldTickEvent(String worldId, long tickCount, Duration deltaTime) {
            super(worldId);
            this.tickCount = tickCount;
            this.deltaTime = deltaTime;
        }

        public long getTickCount() {
            return tickCount;
        }

        public Duration getDeltaTime() {
            return deltaTime;
        }
    }

    /** 世界ID */
    private final String worldId;

    /** 世界状态 */
    private volatile WorldState state = WorldState.INITIALIZING;

    /** 世界启动时间 */
    private volatile LocalDateTime startTime;

    /** 最后更新时间 */
    private volatile LocalDateTime lastUpdateTime;

    /** Tick计数器 */
    private final AtomicLong tickCount = new AtomicLong(0);

    /** 世界更新间隔（毫秒） */
    private final long updateInterval;

    /** 运行标志 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** ECS世界 */
    @Autowired(required = false)
    private World ecsWorld;

    /** 事件总线 */
    @Autowired(required = false)
    private EventBus eventBus;

    /** 逻辑上下文 */
    @Autowired
    private LogicContext logicContext;

    /** 玩家管理器 */
    @Autowired(required = false)
    private PlayerManager playerManager;

    /** 场景管理器 */
    @Autowired(required = false)
    private SceneManager sceneManager;

    /** 世界更新执行器 */
    private ScheduledExecutorService updateExecutor;

    /** 实体计数器 */
    private final AtomicLong entityCounter = new AtomicLong(0);

    /** 活跃实体集合 */
    private final Set<Long> activeEntities = ConcurrentHashMap.newKeySet();

    /** 世界数据 */
    private final Map<String, Object> worldData = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public GameWorld() {
        this.worldId = "world-" + System.currentTimeMillis();
        // 默认50ms更新一次（20 TPS）
        this.updateInterval = 50;
    }

    /**
     * 初始化游戏世界
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化游戏世界: {}", worldId);

        try {
            // 初始化ECS世界
            if (ecsWorld != null) {
                ecsWorld.initialize();
            }

            // 初始化世界数据
            initializeWorldData();

            // 设置状态
            state = WorldState.RUNNING;
            startTime = LocalDateTime.now();
            lastUpdateTime = startTime;

            log.info("游戏世界 {} 初始化完成", worldId);

        } catch (Exception e) {
            state = WorldState.ERROR;
            log.error("游戏世界 {} 初始化失败", worldId, e);
            throw new RuntimeException("游戏世界初始化失败", e);
        }
    }

    /**
     * 启动游戏世界
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("游戏世界 {} 已经启动", worldId);
            return;
        }

        log.info("启动游戏世界: {}", worldId);

        try {
            // 创建更新执行器
            updateExecutor = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "world-update-" + worldId);
                t.setDaemon(true);
                return t;
            });

            // 启动世界更新循环
            updateExecutor.scheduleAtFixedRate(
                    this::update,
                    updateInterval,
                    updateInterval,
                    TimeUnit.MILLISECONDS
            );

            // 发布世界启动事件
            publishEvent(new WorldStartedEvent(worldId));

            log.info("游戏世界 {} 启动完成", worldId);

        } catch (Exception e) {
            running.set(false);
            state = WorldState.ERROR;
            log.error("游戏世界 {} 启动失败", worldId, e);
            throw new RuntimeException("游戏世界启动失败", e);
        }
    }

    /**
     * 停止游戏世界
     */
    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            log.info("游戏世界 {} 已经停止或未启动", worldId);
            return;
        }

        log.info("停止游戏世界: {}", worldId);
        state = WorldState.STOPPING;

        try {
            // 停止更新循环
            if (updateExecutor != null && !updateExecutor.isShutdown()) {
                updateExecutor.shutdown();
                if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    updateExecutor.shutdownNow();
                }
            }

            // 清理所有实体
            cleanupEntities();

            // 停止ECS世界
            if (ecsWorld != null) {
                ecsWorld.shutdown();
            }

            // 发布世界停止事件
            publishEvent(new WorldStoppedEvent(worldId));

            state = WorldState.STOPPED;
            log.info("游戏世界 {} 已停止", worldId);

        } catch (Exception e) {
            state = WorldState.ERROR;
            log.error("游戏世界 {} 停止失败", worldId, e);
        }
    }

    /**
     * 世界更新
     */
    private void update() {
        try {
            LocalDateTime now = LocalDateTime.now();
            Duration deltaTime = Duration.between(lastUpdateTime, now);
            lastUpdateTime = now;

            long currentTick = tickCount.incrementAndGet();

            // 更新ECS世界
            if (ecsWorld != null && ecsWorld.isRunning()) {
                ecsWorld.update(deltaTime);
            }

            // 发布Tick事件
            publishEvent(new WorldTickEvent(worldId, currentTick, deltaTime));

        } catch (Exception e) {
            log.error("游戏世界 {} 更新失败", worldId, e);
        }
    }

    /**
     * 初始化世界数据
     */
    private void initializeWorldData() {
        // 设置基础世界参数
        worldData.put("created_time", LocalDateTime.now());
        worldData.put("tick_rate", 1000 / updateInterval);
        worldData.put("max_entities", 100000);
        worldData.put("version", "1.0.0");
    }

    /**
     * 创建实体
     *
     * @return 实体ID
     */
    public long createEntity() {
        if (ecsWorld != null) {
            Entity entity = ecsWorld.createEntity();
            long entityId = entity.getId();
            activeEntities.add(entityId);
            entityCounter.incrementAndGet();
            return entityId;
        } else {
            // 如果没有ECS世界，使用简单的ID生成
            long entityId = entityCounter.incrementAndGet();
            activeEntities.add(entityId);
            return entityId;
        }
    }

    /**
     * 销毁实体
     *
     * @param entityId 实体ID
     */
    public void destroyEntity(long entityId) {
        if (activeEntities.remove(entityId)) {
            if (ecsWorld != null) {
                ecsWorld.destroyEntity(entityId);
            }
            log.debug("销毁实体: {}", entityId);
        }
    }

    /**
     * 检查实体是否存在
     *
     * @param entityId 实体ID
     * @return 是否存在
     */
    public boolean entityExists(long entityId) {
        return activeEntities.contains(entityId);
    }

    /**
     * 清理所有实体
     */
    private void cleanupEntities() {
        log.info("清理世界 {} 中的 {} 个实体", worldId, activeEntities.size());
        
        for (Long entityId : activeEntities) {
            if (ecsWorld != null) {
                ecsWorld.destroyEntity(entityId);
            }
        }
        
        activeEntities.clear();
        entityCounter.set(0);
    }

    /**
     * 设置世界数据
     *
     * @param key   键
     * @param value 值
     */
    public void setWorldData(String key, Object value) {
        worldData.put(key, value);
    }

    /**
     * 获取世界数据
     *
     * @param key 键
     * @return 值
     */
    public Object getWorldData(String key) {
        return worldData.get(key);
    }

    /**
     * 获取世界数据
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          数据类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getWorldData(String key, T defaultValue) {
        Object value = worldData.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 暂停世界
     */
    public void pause() {
        if (state == WorldState.RUNNING) {
            state = WorldState.PAUSED;
            log.info("游戏世界 {} 已暂停", worldId);
        }
    }

    /**
     * 恢复世界
     */
    public void resume() {
        if (state == WorldState.PAUSED) {
            state = WorldState.RUNNING;
            lastUpdateTime = LocalDateTime.now();
            log.info("游戏世界 {} 已恢复", worldId);
        }
    }

    /**
     * 发布事件
     *
     * @param event 事件
     */
    private void publishEvent(Object event) {
        try {
            if (eventBus != null) {
                eventBus.publish(event);
            } else if (logicContext != null) {
                logicContext.publishEvent(event);
            }
        } catch (Exception e) {
            log.warn("发布世界事件失败: {}", event.getClass().getSimpleName(), e);
        }
    }

    /**
     * 获取运行时间
     *
     * @return 运行时间
     */
    public Duration getUptime() {
        return startTime != null ? Duration.between(startTime, LocalDateTime.now()) : Duration.ZERO;
    }

    /**
     * 获取实体数量
     *
     * @return 实体数量
     */
    public int getEntityCount() {
        return activeEntities.size();
    }

    /**
     * 获取TPS（每秒Tick数）
     *
     * @return TPS
     */
    public double getTPS() {
        return 1000.0 / updateInterval;
    }

    /**
     * 检查世界是否运行中
     *
     * @return 是否运行中
     */
    public boolean isRunning() {
        return running.get() && state == WorldState.RUNNING;
    }

    /**
     * 检查世界是否暂停
     *
     * @return 是否暂停
     */
    public boolean isPaused() {
        return state == WorldState.PAUSED;
    }

    /**
     * 获取世界统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("worldId", worldId);
        stats.put("state", state.name());
        stats.put("uptime", getUptime().toMillis());
        stats.put("tickCount", tickCount.get());
        stats.put("entityCount", getEntityCount());
        stats.put("tps", getTPS());
        stats.put("startTime", startTime);
        stats.put("lastUpdateTime", lastUpdateTime);
        return stats;
    }
}