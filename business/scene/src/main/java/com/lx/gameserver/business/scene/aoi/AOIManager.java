/*
 * 文件名: AOIManager.java
 * 用途: AOI管理器核心实现
 * 实现内容:
 *   - AOI系统的核心管理和协调
 *   - 实体进入和离开视野的事件处理
 *   - 视野订阅和广播机制
 *   - 批量AOI更新和性能优化
 *   - AOI算法选择和参数调优
 * 技术选型:
 *   - 策略模式支持多种AOI算法
 *   - 事件驱动架构处理视野变化
 *   - 定时任务进行批量更新
 *   - 缓存机制优化查询性能
 * 依赖关系:
 *   - 管理AOIGrid进行空间索引
 *   - 与AOIEntity协作进行视野计算
 *   - 被Scene调用进行AOI管理
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.aoi;

import com.lx.gameserver.business.scene.core.Scene;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOI管理器
 * <p>
 * 实现AOI（Area of Interest）系统的核心管理功能，
 * 包括视野管理、事件处理、批量更新等。支持九宫格
 * 和十字链表等多种AOI算法，提供高性能的视野计算。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class AOIManager {

    /** AOI网格 */
    private final AOIGrid aoiGrid;

    /** AOI配置 */
    private final AOIConfig config;

    /** 注册的AOI实体 */
    private final ConcurrentHashMap<Long, AOIEntity> entities = new ConcurrentHashMap<>();

    /** 实体关注列表（谁在关注这个实体） */
    private final ConcurrentHashMap<Long, Set<Long>> watchers = new ConcurrentHashMap<>();

    /** 实体观察列表（这个实体在关注谁） */
    private final ConcurrentHashMap<Long, Set<Long>> watching = new ConcurrentHashMap<>();

    /** AOI事件队列 */
    private final BlockingQueue<AOIEventTask> eventQueue = new LinkedBlockingQueue<>();

    /** 更新任务队列 */
    private final BlockingQueue<UpdateTask> updateQueue = new LinkedBlockingQueue<>();

    /** 定时任务执行器 */
    private ScheduledExecutorService scheduledExecutor;

    /** 事件处理线程池 */
    private ExecutorService eventExecutor;

    /** AOI统计信息 */
    private final AOIStatistics statistics = new AOIStatistics();

    /** 是否正在运行 */
    private volatile boolean running = false;

    /**
     * AOI配置
     */
    @Data
    public static class AOIConfig {
        /** 网格大小 */
        private float gridSize = 100.0f;
        /** 默认视野范围 */
        private double defaultViewRange = 150.0;
        /** 更新间隔（毫秒） */
        private long updateInterval = 200;
        /** 批量处理大小 */
        private int batchSize = 100;
        /** 是否启用性能优化 */
        private boolean optimizationEnabled = true;
        /** 事件队列大小 */
        private int eventQueueSize = 10000;
        /** 线程池大小 */
        private int threadPoolSize = 2;
        /** 是否异步处理事件 */
        private boolean asyncEventProcessing = true;

        public AOIConfig() {}

        public AOIConfig(float gridSize, double defaultViewRange) {
            this.gridSize = gridSize;
            this.defaultViewRange = defaultViewRange;
        }
    }

    /**
     * AOI事件任务
     */
    private static class AOIEventTask {
        private final AOIEntity.AOIEventType eventType;
        private final Long sourceEntityId;
        private final Long targetEntityId;
        private final Object eventData;
        private final long timestamp;

        public AOIEventTask(AOIEntity.AOIEventType eventType, Long sourceEntityId, 
                           Long targetEntityId, Object eventData) {
            this.eventType = eventType;
            this.sourceEntityId = sourceEntityId;
            this.targetEntityId = targetEntityId;
            this.eventData = eventData;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public AOIEntity.AOIEventType getEventType() { return eventType; }
        public Long getSourceEntityId() { return sourceEntityId; }
        public Long getTargetEntityId() { return targetEntityId; }
        public Object getEventData() { return eventData; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 更新任务
     */
    private static class UpdateTask {
        private final Long entityId;
        private final Scene.Position newPosition;
        private final long timestamp;

        public UpdateTask(Long entityId, Scene.Position newPosition) {
            this.entityId = entityId;
            this.newPosition = newPosition;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public Long getEntityId() { return entityId; }
        public Scene.Position getNewPosition() { return newPosition; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * AOI统计信息
     */
    @Data
    public static class AOIStatistics {
        private final AtomicLong totalEntities = new AtomicLong(0);
        private final AtomicLong totalUpdates = new AtomicLong(0);
        private final AtomicLong totalEvents = new AtomicLong(0);
        private final AtomicLong enterEvents = new AtomicLong(0);
        private final AtomicLong leaveEvents = new AtomicLong(0);
        private volatile double avgEntitiesPerGrid = 0.0;
        private volatile long avgUpdateTime = 0;
        private volatile LocalDateTime lastUpdateTime;

        public void incrementTotalUpdates() { totalUpdates.incrementAndGet(); }
        public void incrementTotalEvents() { totalEvents.incrementAndGet(); }
        public void incrementEnterEvents() { enterEvents.incrementAndGet(); }
        public void incrementLeaveEvents() { leaveEvents.incrementAndGet(); }

        public long getTotalEntities() { return totalEntities.get(); }
        public long getTotalUpdates() { return totalUpdates.get(); }
        public long getTotalEvents() { return totalEvents.get(); }
        public long getEnterEvents() { return enterEvents.get(); }
        public long getLeaveEvents() { return leaveEvents.get(); }
    }

    /**
     * 构造函数
     *
     * @param config AOI配置
     * @param sceneBounds 场景边界
     */
    public AOIManager(AOIConfig config, Scene.Position minBounds, Scene.Position maxBounds) {
        this.config = config != null ? config : new AOIConfig();
        this.aoiGrid = new AOIGrid(this.config.getGridSize(), minBounds, maxBounds);
        
        log.info("AOI管理器创建完成: gridSize={}, bounds=[{},{}]", 
                this.config.getGridSize(), minBounds, maxBounds);
    }

    /**
     * 启动AOI管理器
     */
    public void start() {
        if (running) {
            log.warn("AOI管理器已经在运行中");
            return;
        }

        log.info("启动AOI管理器");
        
        // 初始化线程池
        scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "AOIManager-Scheduler");
            t.setDaemon(true);
            return t;
        });

        if (config.isAsyncEventProcessing()) {
            eventExecutor = Executors.newFixedThreadPool(config.getThreadPoolSize(), r -> {
                Thread t = new Thread(r, "AOIManager-Event");
                t.setDaemon(true);
                return t;
            });

            // 启动事件处理线程
            for (int i = 0; i < config.getThreadPoolSize(); i++) {
                eventExecutor.submit(this::processEvents);
            }
        }

        // 启动定时更新任务
        scheduledExecutor.scheduleAtFixedRate(
                this::processUpdateBatch,
                config.getUpdateInterval(),
                config.getUpdateInterval(),
                TimeUnit.MILLISECONDS
        );

        // 启动统计更新任务
        scheduledExecutor.scheduleAtFixedRate(
                this::updateStatistics,
                10, 10, TimeUnit.SECONDS
        );

        running = true;
        log.info("AOI管理器启动完成");
    }

    /**
     * 停止AOI管理器
     */
    public void stop() {
        if (!running) {
            return;
        }

        log.info("停止AOI管理器");
        running = false;

        // 关闭线程池
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (eventExecutor != null) {
            eventExecutor.shutdown();
            try {
                if (!eventExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    eventExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                eventExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("AOI管理器停止完成");
    }

    // ========== 实体注册管理 ==========

    /**
     * 注册AOI实体
     *
     * @param entity AOI实体
     * @return 是否注册成功
     */
    public boolean registerEntity(AOIEntity entity) {
        if (entity == null || entity.getEntityId() == null) {
            return false;
        }

        try {
            Long entityId = entity.getEntityId();
            
            // 注册实体
            entities.put(entityId, entity);
            watchers.put(entityId, ConcurrentHashMap.newKeySet());
            watching.put(entityId, ConcurrentHashMap.newKeySet());
            
            // 添加到网格
            Scene.Position position = entity.getPosition();
            if (position != null) {
                aoiGrid.addEntity(entityId, position);
            }
            
            statistics.totalEntities.incrementAndGet();
            
            log.debug("AOI实体注册成功: entityId={}, type={}", 
                     entityId, entity.getEntityType());
            return true;
            
        } catch (Exception e) {
            log.error("AOI实体注册失败: entityId={}", entity.getEntityId(), e);
            return false;
        }
    }

    /**
     * 注销AOI实体
     *
     * @param entityId 实体ID
     * @return 是否注销成功
     */
    public boolean unregisterEntity(Long entityId) {
        if (entityId == null) {
            return false;
        }

        try {
            AOIEntity entity = entities.remove(entityId);
            if (entity == null) {
                return false;
            }

            // 从网格移除
            aoiGrid.removeEntity(entityId);

            // 清理观察关系
            Set<Long> entityWatchers = watchers.remove(entityId);
            Set<Long> entityWatching = watching.remove(entityId);

            // 通知其他实体
            if (entityWatchers != null) {
                for (Long watcherId : entityWatchers) {
                    AOIEntity watcher = entities.get(watcherId);
                    if (watcher != null) {
                        triggerAOIEvent(AOIEntity.AOIEventType.LEAVE_AOI, watcher, entity, null);
                        watching.computeIfAbsent(watcherId, k -> ConcurrentHashMap.newKeySet()).remove(entityId);
                    }
                }
            }

            if (entityWatching != null) {
                for (Long watcheeId : entityWatching) {
                    watchers.computeIfAbsent(watcheeId, k -> ConcurrentHashMap.newKeySet()).remove(entityId);
                }
            }

            statistics.totalEntities.decrementAndGet();
            
            log.debug("AOI实体注销成功: entityId={}", entityId);
            return true;
            
        } catch (Exception e) {
            log.error("AOI实体注销失败: entityId={}", entityId, e);
            return false;
        }
    }

    // ========== 位置更新 ==========

    /**
     * 更新实体位置
     *
     * @param entityId 实体ID
     * @param newPosition 新位置
     * @return 是否更新成功
     */
    public boolean updateEntityPosition(Long entityId, Scene.Position newPosition) {
        if (entityId == null || newPosition == null) {
            return false;
        }

        // 异步处理更新
        if (config.isAsyncEventProcessing()) {
            return updateQueue.offer(new UpdateTask(entityId, newPosition));
        } else {
            return processPositionUpdate(entityId, newPosition);
        }
    }

    /**
     * 处理位置更新
     */
    private boolean processPositionUpdate(Long entityId, Scene.Position newPosition) {
        try {
            AOIEntity entity = entities.get(entityId);
            if (entity == null) {
                return false;
            }

            Scene.Position oldPosition = entity.getPosition();
            
            // 更新网格位置
            aoiGrid.updateEntity(entityId, newPosition);
            
            // 通知实体位置更新
            entity.onPositionUpdated(oldPosition, newPosition);
            
            // 重新计算AOI
            recalculateAOI(entity);
            
            statistics.incrementTotalUpdates();
            statistics.setLastUpdateTime(LocalDateTime.now());
            
            return true;
            
        } catch (Exception e) {
            log.error("处理位置更新失败: entityId={}", entityId, e);
            return false;
        }
    }

    /**
     * 重新计算AOI
     */
    private void recalculateAOI(AOIEntity entity) {
        if (entity == null || !entity.isAOIActive()) {
            return;
        }

        Long entityId = entity.getEntityId();
        Set<Long> currentWatching = watching.get(entityId);
        if (currentWatching == null) {
            return;
        }

        // 获取当前视野范围内的实体
        Set<Long> newWatching = findEntitiesInView(entity);
        
        // 计算进入和离开的实体
        Set<Long> entering = new HashSet<>(newWatching);
        entering.removeAll(currentWatching);
        
        Set<Long> leaving = new HashSet<>(currentWatching);
        leaving.removeAll(newWatching);
        
        // 处理进入事件
        for (Long targetId : entering) {
            AOIEntity target = entities.get(targetId);
            if (target != null) {
                addWatchRelation(entityId, targetId);
                triggerAOIEvent(AOIEntity.AOIEventType.ENTER_AOI, entity, target, null);
            }
        }
        
        // 处理离开事件
        for (Long targetId : leaving) {
            AOIEntity target = entities.get(targetId);
            if (target != null) {
                removeWatchRelation(entityId, targetId);
                triggerAOIEvent(AOIEntity.AOIEventType.LEAVE_AOI, entity, target, null);
            }
        }
    }

    /**
     * 查找视野范围内的实体
     */
    private Set<Long> findEntitiesInView(AOIEntity entity) {
        Set<Long> result = new HashSet<>();
        
        Scene.Position position = entity.getPosition();
        if (position == null) {
            return result;
        }

        // 获取九宫格内的实体
        AOIGrid.GridCoordinate gridCoord = aoiGrid.worldToGrid(position);
        Set<Long> candidates = aoiGrid.getEntitiesInNineGrid(gridCoord);
        
        // 过滤在视野范围内的实体
        for (Long candidateId : candidates) {
            if (candidateId.equals(entity.getEntityId())) {
                continue;
            }
            
            AOIEntity candidate = entities.get(candidateId);
            if (candidate != null && entity.canSee(candidate)) {
                result.add(candidateId);
            }
        }
        
        return result;
    }

    // ========== 观察关系管理 ==========

    /**
     * 添加观察关系
     */
    private void addWatchRelation(Long watcherId, Long watcheeId) {
        watching.computeIfAbsent(watcherId, k -> ConcurrentHashMap.newKeySet()).add(watcheeId);
        watchers.computeIfAbsent(watcheeId, k -> ConcurrentHashMap.newKeySet()).add(watcherId);
    }

    /**
     * 移除观察关系
     */
    private void removeWatchRelation(Long watcherId, Long watcheeId) {
        Set<Long> watcherWatching = watching.get(watcherId);
        if (watcherWatching != null) {
            watcherWatching.remove(watcheeId);
        }
        
        Set<Long> watcheeWatchers = watchers.get(watcheeId);
        if (watcheeWatchers != null) {
            watcheeWatchers.remove(watcherId);
        }
    }

    // ========== 事件处理 ==========

    /**
     * 触发AOI事件
     */
    private void triggerAOIEvent(AOIEntity.AOIEventType eventType, AOIEntity source, 
                                AOIEntity target, Object eventData) {
        if (config.isAsyncEventProcessing()) {
            AOIEventTask task = new AOIEventTask(eventType, source.getEntityId(), 
                                               target.getEntityId(), eventData);
            eventQueue.offer(task);
        } else {
            processAOIEvent(eventType, source, target, eventData);
        }
    }

    /**
     * 处理AOI事件
     */
    private void processAOIEvent(AOIEntity.AOIEventType eventType, AOIEntity source, 
                               AOIEntity target, Object eventData) {
        try {
            if (source != null && source.needsAOIEvents()) {
                source.triggerAOIEvent(eventType, target, eventData);
            }
            
            statistics.incrementTotalEvents();
            
            switch (eventType) {
                case ENTER_AOI -> statistics.incrementEnterEvents();
                case LEAVE_AOI -> statistics.incrementLeaveEvents();
            }
            
        } catch (Exception e) {
            log.error("处理AOI事件失败: eventType={}, source={}, target={}", 
                     eventType, source != null ? source.getEntityId() : null, 
                     target != null ? target.getEntityId() : null, e);
        }
    }

    /**
     * 事件处理循环
     */
    private void processEvents() {
        while (running) {
            try {
                AOIEventTask task = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    AOIEntity source = entities.get(task.getSourceEntityId());
                    AOIEntity target = entities.get(task.getTargetEntityId());
                    processAOIEvent(task.getEventType(), source, target, task.getEventData());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理AOI事件异常", e);
            }
        }
    }

    /**
     * 批量处理更新
     */
    private void processUpdateBatch() {
        try {
            List<UpdateTask> batch = new ArrayList<>();
            updateQueue.drainTo(batch, config.getBatchSize());
            
            for (UpdateTask task : batch) {
                processPositionUpdate(task.getEntityId(), task.getNewPosition());
            }
            
            if (!batch.isEmpty()) {
                log.debug("处理AOI更新批次: {}", batch.size());
            }
            
        } catch (Exception e) {
            log.error("批量处理AOI更新失败", e);
        }
    }

    // ========== 查询方法 ==========

    /**
     * 获取实体观察者列表
     *
     * @param entityId 实体ID
     * @return 观察者ID集合
     */
    public Set<Long> getEntityWatchers(Long entityId) {
        Set<Long> result = watchers.get(entityId);
        return result != null ? new HashSet<>(result) : new HashSet<>();
    }

    /**
     * 获取实体观察列表
     *
     * @param entityId 实体ID
     * @return 被观察者ID集合
     */
    public Set<Long> getEntityWatching(Long entityId) {
        Set<Long> result = watching.get(entityId);
        return result != null ? new HashSet<>(result) : new HashSet<>();
    }

    /**
     * 获取指定位置周围的实体
     *
     * @param position 位置
     * @param range 范围
     * @return 实体ID集合
     */
    public Set<Long> getEntitiesNearPosition(Scene.Position position, double range) {
        return aoiGrid.getEntitiesInCircle(position, range);
    }

    /**
     * 广播消息给观察者
     *
     * @param entityId 实体ID
     * @param message 消息
     * @param filter 过滤器
     */
    public void broadcastToWatchers(Long entityId, Object message, 
                                  java.util.function.Predicate<AOIEntity> filter) {
        Set<Long> watcherIds = getEntityWatchers(entityId);
        
        for (Long watcherId : watcherIds) {
            AOIEntity watcher = entities.get(watcherId);
            if (watcher != null && (filter == null || filter.test(watcher))) {
                // 这里应该通过消息系统发送消息
                log.debug("广播消息给观察者: from={}, to={}, message={}", 
                         entityId, watcherId, message.getClass().getSimpleName());
            }
        }
    }

    // ========== 统计和监控 ==========

    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        try {
            AOIGrid.GridStatistics gridStats = aoiGrid.getStatistics();
            statistics.setAvgEntitiesPerGrid(gridStats.getAvgEntitiesPerCell());
            
            // 可以添加更多统计信息
            log.debug("AOI统计: entities={}, events={}, avgPerGrid={:.2f}", 
                     statistics.getTotalEntities(), statistics.getTotalEvents(), 
                     statistics.getAvgEntitiesPerGrid());
            
        } catch (Exception e) {
            log.error("更新AOI统计信息失败", e);
        }
    }

    /**
     * 获取AOI统计信息
     *
     * @return 统计信息
     */
    public AOIStatistics getStatistics() {
        return statistics;
    }

    /**
     * 获取详细统计信息
     *
     * @return 详细统计信息
     */
    public Map<String, Object> getDetailedStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntities", statistics.getTotalEntities());
        stats.put("totalUpdates", statistics.getTotalUpdates());
        stats.put("totalEvents", statistics.getTotalEvents());
        stats.put("enterEvents", statistics.getEnterEvents());
        stats.put("leaveEvents", statistics.getLeaveEvents());
        stats.put("avgEntitiesPerGrid", statistics.getAvgEntitiesPerGrid());
        stats.put("eventQueueSize", eventQueue.size());
        stats.put("updateQueueSize", updateQueue.size());
        stats.put("gridStatistics", aoiGrid.getStatistics());
        stats.put("running", running);
        return stats;
    }

    /**
     * 清理AOI管理器
     */
    public void clear() {
        entities.clear();
        watchers.clear();
        watching.clear();
        eventQueue.clear();
        updateQueue.clear();
        aoiGrid.clear();
        
        statistics.totalEntities.set(0);
        
        log.info("AOI管理器已清理");
    }

    // ========== 配置管理 ==========

    /**
     * 获取AOI配置
     *
     * @return AOI配置
     */
    public AOIConfig getConfig() {
        return config;
    }

    /**
     * 获取AOI网格
     *
     * @return AOI网格
     */
    public AOIGrid getAOIGrid() {
        return aoiGrid;
    }

    /**
     * 是否正在运行
     *
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public String toString() {
        return String.format("AOIManager{entities=%d, running=%s, gridSize=%.1f}", 
                statistics.getTotalEntities(), running, config.getGridSize());
    }
}