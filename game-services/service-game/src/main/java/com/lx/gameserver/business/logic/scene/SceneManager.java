/*
 * 文件名: SceneManager.java
 * 用途: 场景管理器
 * 实现内容:
 *   - 场景的创建销毁和生命周期管理
 *   - 场景调度和负载均衡
 *   - 动态扩容和场景监控
 *   - 场景间切换和数据同步
 *   - 多线程安全的场景操作
 * 技术选型:
 *   - ConcurrentHashMap实现线程安全的场景缓存
 *   - 定时任务进行场景维护和监控
 *   - 负载均衡算法优化场景分配
 *   - 事件驱动架构处理场景事件
 * 依赖关系:
 *   - 管理Scene实例的创建和销毁
 *   - 与PlayerManager协作进行玩家分配
 *   - 被LogicServer和其他模块调用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.scene;

import com.lx.gameserver.business.logic.core.LogicContext;
import com.lx.gameserver.business.logic.player.Player;
import com.lx.gameserver.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 场景管理器
 * <p>
 * 负责管理所有游戏场景的创建、销毁、调度和监控。
 * 提供场景负载均衡、动态扩容和性能优化功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class SceneManager {

    /**
     * 场景模板
     */
    public static class SceneTemplate {
        private Scene.SceneType sceneType;
        private String templateName;
        private int defaultCapacity;
        private double defaultAoiRange;
        private Map<String, Object> defaultData;
        private Class<? extends Scene> sceneClass;

        // Getters and setters
        public Scene.SceneType getSceneType() { return sceneType; }
        public void setSceneType(Scene.SceneType sceneType) { this.sceneType = sceneType; }
        
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        
        public int getDefaultCapacity() { return defaultCapacity; }
        public void setDefaultCapacity(int defaultCapacity) { this.defaultCapacity = defaultCapacity; }
        
        public double getDefaultAoiRange() { return defaultAoiRange; }
        public void setDefaultAoiRange(double defaultAoiRange) { this.defaultAoiRange = defaultAoiRange; }
        
        public Map<String, Object> getDefaultData() { return defaultData; }
        public void setDefaultData(Map<String, Object> defaultData) { this.defaultData = defaultData; }
        
        public Class<? extends Scene> getSceneClass() { return sceneClass; }
        public void setSceneClass(Class<? extends Scene> sceneClass) { this.sceneClass = sceneClass; }
    }

    /**
     * 场景事件
     */
    public static abstract class SceneManagerEvent {
        private final LocalDateTime timestamp = LocalDateTime.now();
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 场景创建事件
     */
    public static class SceneCreatedEvent extends SceneManagerEvent {
        private final Long sceneId;
        private final Scene.SceneType sceneType;

        public SceneCreatedEvent(Long sceneId, Scene.SceneType sceneType) {
            this.sceneId = sceneId;
            this.sceneType = sceneType;
        }

        public Long getSceneId() { return sceneId; }
        public Scene.SceneType getSceneType() { return sceneType; }
    }

    /**
     * 场景销毁事件
     */
    public static class SceneDestroyedEvent extends SceneManagerEvent {
        private final Long sceneId;
        private final String reason;

        public SceneDestroyedEvent(Long sceneId, String reason) {
            this.sceneId = sceneId;
            this.reason = reason;
        }

        public Long getSceneId() { return sceneId; }
        public String getReason() { return reason; }
    }

    /** 场景存储 */
    private final ConcurrentHashMap<Long, Scene> scenes = new ConcurrentHashMap<>();

    /** 场景模板注册表 */
    private final Map<String, SceneTemplate> sceneTemplates = new ConcurrentHashMap<>();

    /** 场景类型索引 */
    private final Map<Scene.SceneType, Set<Long>> sceneTypeIndex = new ConcurrentHashMap<>();

    /** 场景ID生成器 */
    private final AtomicLong sceneIdGenerator = new AtomicLong(1);

    /** 场景计数器 */
    private final AtomicInteger sceneCount = new AtomicInteger(0);

    /** 总玩家数 */
    private final AtomicInteger totalPlayerCount = new AtomicInteger(0);

    /** 维护执行器 */
    private ScheduledExecutorService maintenanceExecutor;

    /** 监控执行器 */
    private ScheduledExecutorService monitorExecutor;

    /** 逻辑上下文 */
    @Autowired
    private LogicContext logicContext;

    /** 事件发布器 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 配置参数 */
    private int maxScenes = 100;
    private int defaultCapacity = 500;
    private double defaultAoiRange = 100.0;
    private int maintenanceInterval = 600; // 10分钟
    private int monitorInterval = 60; // 1分钟
    private int idleTimeout = 1800; // 30分钟

    /**
     * 初始化管理器
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化场景管理器...");

        // 从配置获取参数
        loadConfiguration();

        // 注册默认场景模板
        registerDefaultTemplates();

        // 启动维护任务
        startMaintenanceTasks();

        log.info("场景管理器初始化完成，最大场景数: {}, 默认容量: {}, AOI范围: {}",
                maxScenes, defaultCapacity, defaultAoiRange);
    }

    /**
     * 销毁管理器
     */
    @PreDestroy
    public void destroy() {
        log.info("销毁场景管理器...");

        // 停止维护任务
        stopMaintenanceTasks();

        // 销毁所有场景
        destroyAllScenes();

        log.info("场景管理器已销毁");
    }

    /**
     * 创建场景
     */
    public Result<Scene> createScene(Scene.SceneType sceneType, String sceneName) {
        return createScene(sceneType, sceneName, null);
    }

    /**
     * 创建场景
     */
    public Result<Scene> createScene(Scene.SceneType sceneType, String sceneName, String templateName) {
        if (sceneCount.get() >= maxScenes) {
            return Result.error("场景数量已达上限: " + maxScenes);
        }

        try {
            Long sceneId = sceneIdGenerator.getAndIncrement();
            
            // 获取场景模板
            SceneTemplate template = templateName != null ? 
                    sceneTemplates.get(templateName) : 
                    getDefaultTemplate(sceneType);

            // 创建场景实例
            Scene scene = createSceneInstance(sceneId, sceneName, sceneType, template);
            if (scene == null) {
                return Result.error("创建场景实例失败");
            }

            // 初始化场景
            scene.initialize();

            // 注册场景
            scenes.put(sceneId, scene);
            sceneTypeIndex.computeIfAbsent(sceneType, k -> ConcurrentHashMap.newKeySet()).add(sceneId);
            sceneCount.incrementAndGet();

            // 发布事件
            publishEvent(new SceneCreatedEvent(sceneId, sceneType));

            log.info("创建场景成功: {} (ID: {}, 类型: {})", sceneName, sceneId, sceneType);
            return Result.success(scene);

        } catch (Exception e) {
            log.error("创建场景失败: {}", sceneName, e);
            return Result.error("创建场景失败: " + e.getMessage());
        }
    }

    /**
     * 销毁场景
     */
    public Result<Void> destroyScene(Long sceneId, String reason) {
        if (sceneId == null) {
            return Result.error("场景ID不能为空");
        }

        Scene scene = scenes.remove(sceneId);
        if (scene == null) {
            return Result.error("场景不存在: " + sceneId);
        }

        try {
            // 从索引中移除
            Set<Long> typeScenes = sceneTypeIndex.get(scene.getSceneType());
            if (typeScenes != null) {
                typeScenes.remove(sceneId);
            }

            // 更新统计
            totalPlayerCount.addAndGet(-scene.getPlayerCount());
            sceneCount.decrementAndGet();

            // 销毁场景
            scene.destroy();

            // 发布事件
            publishEvent(new SceneDestroyedEvent(sceneId, reason));

            log.info("销毁场景: {} (原因: {})", sceneId, reason);
            return Result.success();

        } catch (Exception e) {
            log.error("销毁场景 {} 失败", sceneId, e);
            return Result.error("销毁场景失败: " + e.getMessage());
        }
    }

    /**
     * 获取场景
     */
    public Scene getScene(Long sceneId) {
        return sceneId != null ? scenes.get(sceneId) : null;
    }

    /**
     * 获取所有场景
     */
    public List<Scene> getAllScenes() {
        return new ArrayList<>(scenes.values());
    }

    /**
     * 按类型获取场景
     */
    public List<Scene> getScenesByType(Scene.SceneType sceneType) {
        Set<Long> sceneIds = sceneTypeIndex.get(sceneType);
        if (sceneIds == null || sceneIds.isEmpty()) {
            return new ArrayList<>();
        }

        return sceneIds.stream()
                .map(scenes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 查找最佳场景（负载均衡）
     */
    public Scene findBestScene(Scene.SceneType sceneType) {
        List<Scene> candidateScenes = getScenesByType(sceneType).stream()
                .filter(scene -> scene.isRunning() && !scene.isFull())
                .collect(Collectors.toList());

        if (candidateScenes.isEmpty()) {
            // 尝试创建新场景
            Result<Scene> result = createScene(sceneType, "auto_" + sceneType.name().toLowerCase() + "_" + System.currentTimeMillis());
            return result.isSuccess() ? result.getData() : null;
        }

        // 选择玩家数最少的场景
        return candidateScenes.stream()
                .min(Comparator.comparingInt(Scene::getPlayerCount))
                .orElse(null);
    }

    /**
     * 玩家进入场景
     */
    public Result<Void> playerEnterScene(Long sceneId, Player player, Scene.Position position) {
        Scene scene = getScene(sceneId);
        if (scene == null) {
            return Result.error("场景不存在: " + sceneId);
        }

        if (!scene.isRunning()) {
            return Result.error("场景未运行: " + sceneId);
        }

        boolean success = scene.playerEnter(player, position);
        if (success) {
            totalPlayerCount.incrementAndGet();
            return Result.success();
        } else {
            return Result.error("玩家进入场景失败");
        }
    }

    /**
     * 玩家离开场景
     */
    public Result<Void> playerLeaveScene(Long sceneId, Long playerId, String reason) {
        Scene scene = getScene(sceneId);
        if (scene == null) {
            return Result.error("场景不存在: " + sceneId);
        }

        boolean success = scene.playerLeave(playerId, reason);
        if (success) {
            totalPlayerCount.decrementAndGet();
            return Result.success();
        } else {
            return Result.error("玩家离开场景失败");
        }
    }

    /**
     * 玩家切换场景
     */
    public Result<Void> playerSwitchScene(Long fromSceneId, Long toSceneId, Player player, Scene.Position position) {
        // 从原场景离开
        if (fromSceneId != null) {
            Result<Void> leaveResult = playerLeaveScene(fromSceneId, player.getPlayerId(), "切换场景");
            if (!leaveResult.isSuccess()) {
                log.warn("玩家 {} 离开场景 {} 失败: {}", player.getPlayerId(), fromSceneId, leaveResult.getMessage());
            }
        }

        // 进入新场景
        Result<Void> enterResult = playerEnterScene(toSceneId, player, position);
        if (!enterResult.isSuccess()) {
            log.error("玩家 {} 进入场景 {} 失败: {}", player.getPlayerId(), toSceneId, enterResult.getMessage());
            
            // 如果进入失败，尝试回到原场景
            if (fromSceneId != null) {
                playerEnterScene(fromSceneId, player, position);
            }
        }

        return enterResult;
    }

    /**
     * 广播消息给所有场景
     */
    public void broadcastToAllScenes(Object message) {
        for (Scene scene : scenes.values()) {
            try {
                scene.broadcast(message);
            } catch (Exception e) {
                log.warn("向场景 {} 广播消息失败", scene.getSceneId(), e);
            }
        }
    }

    /**
     * 广播消息给指定类型的场景
     */
    public void broadcastToSceneType(Scene.SceneType sceneType, Object message) {
        List<Scene> targetScenes = getScenesByType(sceneType);
        for (Scene scene : targetScenes) {
            try {
                scene.broadcast(message);
            } catch (Exception e) {
                log.warn("向场景 {} 广播消息失败", scene.getSceneId(), e);
            }
        }
    }

    /**
     * 注册场景模板
     */
    public void registerSceneTemplate(String templateName, SceneTemplate template) {
        if (templateName != null && template != null) {
            sceneTemplates.put(templateName, template);
            log.info("注册场景模板: {}", templateName);
        }
    }

    /**
     * 获取场景统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalScenes", sceneCount.get());
        stats.put("totalPlayers", totalPlayerCount.get());
        stats.put("maxScenes", maxScenes);

        // 按类型统计
        Map<String, Integer> typeStats = new HashMap<>();
        for (Map.Entry<Scene.SceneType, Set<Long>> entry : sceneTypeIndex.entrySet()) {
            typeStats.put(entry.getKey().name(), entry.getValue().size());
        }
        stats.put("scenesByType", typeStats);

        // 性能统计
        stats.put("averagePlayersPerScene", sceneCount.get() > 0 ? 
                (double) totalPlayerCount.get() / sceneCount.get() : 0.0);

        return stats;
    }

    /**
     * 加载配置
     */
    private void loadConfiguration() {
        if (logicContext != null) {
            maxScenes = logicContext.getProperty("game.logic.scene.max-scenes", Integer.class, 100);
            defaultCapacity = logicContext.getProperty("game.logic.scene.scene-capacity", Integer.class, 500);
            defaultAoiRange = logicContext.getProperty("game.logic.scene.aoi-range", Double.class, 100.0);
            maintenanceInterval = logicContext.getProperty("game.logic.scene.maintenance-interval", Integer.class, 600);
            monitorInterval = logicContext.getProperty("game.logic.scene.monitor-interval", Integer.class, 60);
            idleTimeout = logicContext.getProperty("game.logic.scene.idle-timeout", Integer.class, 1800);
        }
    }

    /**
     * 注册默认场景模板
     */
    private void registerDefaultTemplates() {
        // 城镇模板
        SceneTemplate townTemplate = new SceneTemplate();
        townTemplate.setSceneType(Scene.SceneType.TOWN);
        townTemplate.setTemplateName("default_town");
        townTemplate.setDefaultCapacity(1000);
        townTemplate.setDefaultAoiRange(150.0);
        townTemplate.setSceneClass(DefaultScene.class);
        registerSceneTemplate("default_town", townTemplate);

        // 副本模板
        SceneTemplate dungeonTemplate = new SceneTemplate();
        dungeonTemplate.setSceneType(Scene.SceneType.DUNGEON);
        dungeonTemplate.setTemplateName("default_dungeon");
        dungeonTemplate.setDefaultCapacity(10);
        dungeonTemplate.setDefaultAoiRange(50.0);
        dungeonTemplate.setSceneClass(DefaultScene.class);
        registerSceneTemplate("default_dungeon", dungeonTemplate);

        // 野外模板
        SceneTemplate fieldTemplate = new SceneTemplate();
        fieldTemplate.setSceneType(Scene.SceneType.FIELD);
        fieldTemplate.setTemplateName("default_field");
        fieldTemplate.setDefaultCapacity(200);
        fieldTemplate.setDefaultAoiRange(200.0);
        fieldTemplate.setSceneClass(DefaultScene.class);
        registerSceneTemplate("default_field", fieldTemplate);
    }

    /**
     * 获取默认模板
     */
    private SceneTemplate getDefaultTemplate(Scene.SceneType sceneType) {
        String templateName = "default_" + sceneType.name().toLowerCase();
        return sceneTemplates.get(templateName);
    }

    /**
     * 创建场景实例
     */
    private Scene createSceneInstance(Long sceneId, String sceneName, Scene.SceneType sceneType, SceneTemplate template) {
        try {
            // 这里简化实现，使用默认场景类
            Scene scene = new DefaultScene(sceneId, sceneName, sceneType);
            
            if (template != null) {
                scene.setCapacity(template.getDefaultCapacity());
                scene.setAoiRange(template.getDefaultAoiRange());
                
                if (template.getDefaultData() != null) {
                    for (Map.Entry<String, Object> entry : template.getDefaultData().entrySet()) {
                        scene.setSceneData(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                scene.setCapacity(defaultCapacity);
                scene.setAoiRange(defaultAoiRange);
            }
            
            return scene;
            
        } catch (Exception e) {
            log.error("创建场景实例失败", e);
            return null;
        }
    }

    /**
     * 启动维护任务
     */
    private void startMaintenanceTasks() {
        // 启动维护任务
        maintenanceExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "scene-maintenance");
            t.setDaemon(true);
            return t;
        });

        maintenanceExecutor.scheduleWithFixedDelay(
                this::performMaintenance,
                maintenanceInterval,
                maintenanceInterval,
                TimeUnit.SECONDS
        );

        // 启动监控任务
        monitorExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "scene-monitor");
            t.setDaemon(true);
            return t;
        });

        monitorExecutor.scheduleWithFixedDelay(
                this::performMonitoring,
                monitorInterval,
                monitorInterval,
                TimeUnit.SECONDS
        );

        log.info("启动场景维护任务，维护间隔: {}秒, 监控间隔: {}秒", maintenanceInterval, monitorInterval);
    }

    /**
     * 停止维护任务
     */
    private void stopMaintenanceTasks() {
        if (maintenanceExecutor != null && !maintenanceExecutor.isShutdown()) {
            maintenanceExecutor.shutdown();
            try {
                if (!maintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    maintenanceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                maintenanceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdown();
            try {
                if (!monitorExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 执行维护
     */
    private void performMaintenance() {
        try {
            List<Long> toDestroy = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (Scene scene : scenes.values()) {
                // 检查空闲场景
                if (scene.isEmpty() && 
                    scene.getLastUpdateTime() != null &&
                    Duration.between(scene.getLastUpdateTime(), now).getSeconds() > idleTimeout) {
                    
                    toDestroy.add(scene.getSceneId());
                }
            }

            // 销毁空闲场景
            for (Long sceneId : toDestroy) {
                destroyScene(sceneId, "空闲超时");
            }

            if (!toDestroy.isEmpty()) {
                log.info("维护任务销毁 {} 个空闲场景", toDestroy.size());
            }

        } catch (Exception e) {
            log.error("执行场景维护失败", e);
        }
    }

    /**
     * 执行监控
     */
    private void performMonitoring() {
        try {
            // 更新统计信息
            int totalPlayers = 0;
            for (Scene scene : scenes.values()) {
                totalPlayers += scene.getPlayerCount();
            }
            totalPlayerCount.set(totalPlayers);

            // 性能监控
            if (log.isDebugEnabled()) {
                Map<String, Object> stats = getStatistics();
                log.debug("场景监控 - 场景数: {}, 玩家数: {}, 平均负载: {:.2f}",
                        stats.get("totalScenes"), stats.get("totalPlayers"), stats.get("averagePlayersPerScene"));
            }

        } catch (Exception e) {
            log.error("执行场景监控失败", e);
        }
    }

    /**
     * 销毁所有场景
     */
    private void destroyAllScenes() {
        List<Long> sceneIds = new ArrayList<>(scenes.keySet());
        for (Long sceneId : sceneIds) {
            try {
                destroyScene(sceneId, "管理器销毁");
            } catch (Exception e) {
                log.error("销毁场景 {} 失败", sceneId, e);
            }
        }
    }

    /**
     * 发布事件
     */
    private void publishEvent(Object event) {
        try {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            } else if (logicContext != null) {
                logicContext.publishEvent(event);
            }
        } catch (Exception e) {
            log.warn("发布场景事件失败: {}", event.getClass().getSimpleName(), e);
        }
    }

    /**
     * 默认场景实现
     */
    private static class DefaultScene extends Scene {
        
        public DefaultScene(Long sceneId, String sceneName, SceneType sceneType) {
            super(sceneId, sceneName, sceneType);
        }

        @Override
        protected void doInitialize() throws Exception {
            log.debug("初始化默认场景: {}", getSceneId());
        }

        @Override
        protected void doDestroy() throws Exception {
            log.debug("销毁默认场景: {}", getSceneId());
        }

        @Override
        protected void onPlayerEnter(Player player, Position position) {
            log.debug("玩家 {} 进入场景 {}", player.getPlayerId(), getSceneId());
        }

        @Override
        protected void onPlayerLeave(Player player, String reason) {
            log.debug("玩家 {} 离开场景 {}，原因: {}", player.getPlayerId(), getSceneId(), reason);
        }

        @Override
        protected void onPlayerPositionUpdate(Long playerId, Position oldPosition, Position newPosition) {
            log.debug("玩家 {} 在场景 {} 中位置更新", playerId, getSceneId());
        }

        @Override
        protected void onEntityEnter(Long entityId, Position position) {
            log.debug("实体 {} 进入场景 {}", entityId, getSceneId());
        }

        @Override
        protected void onEntityLeave(Long entityId) {
            log.debug("实体 {} 离开场景 {}", entityId, getSceneId());
        }
    }
}