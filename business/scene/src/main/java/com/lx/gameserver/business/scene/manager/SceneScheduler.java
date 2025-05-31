/*
 * 文件名: SceneScheduler.java
 * 用途: 场景调度器实现类
 * 实现内容:
 *   - 场景分配策略和负载监控
 *   - 动态扩缩容管理
 *   - 场景迁移和故障转移
 *   - 调度优化和性能监控
 *   - 服务器节点负载均衡
 * 技术选型:
 *   - ScheduledExecutorService实现定时调度
 *   - CompletableFuture支持异步操作
 *   - 策略模式实现多种分配策略
 *   - 观察者模式监控场景状态变化
 * 依赖关系:
 *   - 被SceneManager调用进行场景调度
 *   - 与场景实例协作进行状态监控
 *   - 依赖配置管理器获取调度参数
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.manager;

import com.lx.gameserver.business.scene.core.Scene;
import com.lx.gameserver.business.scene.core.SceneType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 场景调度器
 * <p>
 * 负责场景的分配策略、负载监控、动态扩缩容等调度功能。
 * 提供智能的场景分配算法，确保服务器资源的合理利用，
 * 支持场景迁移和故障转移机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class SceneScheduler {

    /** 场景调度信息 */
    private final ConcurrentHashMap<Long, SceneScheduleInfo> scheduleInfoMap = new ConcurrentHashMap<>();

    /** 场景负载信息 */
    private final ConcurrentHashMap<Long, SceneLoadInfo> loadInfoMap = new ConcurrentHashMap<>();

    /** 调度策略 */
    private ScheduleStrategy scheduleStrategy = ScheduleStrategy.BALANCED;

    /** 定时任务执行器 */
    private ScheduledExecutorService scheduledExecutor;

    /** 负载监控任务 */
    private ScheduledFuture<?> loadMonitorTask;

    /** 调度优化任务 */
    private ScheduledFuture<?> scheduleOptimizeTask;

    /** 扩缩容任务 */
    private ScheduledFuture<?> scalingTask;

    /** 调度统计信息 */
    private final ScheduleStatistics statistics = new ScheduleStatistics();

    /**
     * 场景调度信息
     */
    @Data
    public static class SceneScheduleInfo {
        /** 场景ID */
        private Long sceneId;
        /** 分配的服务器节点 */
        private String serverNode;
        /** 调度优先级 */
        private int priority;
        /** 分配时间 */
        private LocalDateTime assignTime;
        /** 最后活跃时间 */
        private LocalDateTime lastActiveTime;
        /** 调度状态 */
        private ScheduleStatus status;
        /** 调度标签 */
        private Map<String, String> tags;

        public SceneScheduleInfo() {
            this.assignTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
            this.status = ScheduleStatus.ASSIGNED;
            this.tags = new ConcurrentHashMap<>();
        }
    }

    /**
     * 场景负载信息
     */
    @Data
    public static class SceneLoadInfo {
        /** 场景ID */
        private Long sceneId;
        /** CPU使用率 */
        private double cpuUsage;
        /** 内存使用率 */
        private double memoryUsage;
        /** 网络带宽使用率 */
        private double networkUsage;
        /** 实体数量 */
        private int entityCount;
        /** 消息处理速率 */
        private double messageRate;
        /** Tick延迟 */
        private long tickDelay;
        /** 综合负载分数 */
        private double loadScore;
        /** 更新时间 */
        private LocalDateTime updateTime;

        public SceneLoadInfo() {
            this.updateTime = LocalDateTime.now();
        }

        /**
         * 计算负载分数
         */
        public void calculateLoadScore() {
            // 综合负载分数计算（0-100）
            this.loadScore = (cpuUsage * 0.3 + memoryUsage * 0.3 + networkUsage * 0.2 + 
                            (entityCount / 1000.0) * 0.1 + (tickDelay / 100.0) * 0.1) * 100;
            this.loadScore = Math.min(100.0, Math.max(0.0, loadScore));
        }
    }

    /**
     * 调度状态枚举
     */
    public enum ScheduleStatus {
        /** 已分配 */
        ASSIGNED,
        /** 运行中 */
        RUNNING,
        /** 迁移中 */
        MIGRATING,
        /** 暂停 */
        PAUSED,
        /** 故障 */
        FAILED,
        /** 已释放 */
        RELEASED
    }

    /**
     * 调度策略枚举
     */
    public enum ScheduleStrategy {
        /** 均衡策略 */
        BALANCED,
        /** 性能优先 */
        PERFORMANCE,
        /** 资源优先 */
        RESOURCE,
        /** 位置优先 */
        LOCALITY,
        /** 自定义策略 */
        CUSTOM
    }

    /**
     * 调度统计信息
     */
    @Data
    public static class ScheduleStatistics {
        private final AtomicLong totalScheduled = new AtomicLong(0);
        private final AtomicLong successfulScheduled = new AtomicLong(0);
        private final AtomicLong failedScheduled = new AtomicLong(0);
        private final AtomicLong totalMigrations = new AtomicLong(0);
        private final AtomicLong successfulMigrations = new AtomicLong(0);
        private volatile double avgLoadScore = 0.0;
        private volatile LocalDateTime lastOptimizeTime;

        public void incrementTotalScheduled() { totalScheduled.incrementAndGet(); }
        public void incrementSuccessfulScheduled() { successfulScheduled.incrementAndGet(); }
        public void incrementFailedScheduled() { failedScheduled.incrementAndGet(); }
        public void incrementTotalMigrations() { totalMigrations.incrementAndGet(); }
        public void incrementSuccessfulMigrations() { successfulMigrations.incrementAndGet(); }

        public long getTotalScheduled() { return totalScheduled.get(); }
        public long getSuccessfulScheduled() { return successfulScheduled.get(); }
        public long getFailedScheduled() { return failedScheduled.get(); }
        public long getTotalMigrations() { return totalMigrations.get(); }
        public long getSuccessfulMigrations() { return successfulMigrations.get(); }
    }

    @PostConstruct
    public void initialize() {
        log.info("初始化场景调度器");
        
        // 初始化线程池
        scheduledExecutor = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "SceneScheduler-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });

        // 启动负载监控任务
        startLoadMonitoring();
        
        // 启动调度优化任务
        startScheduleOptimization();
        
        // 启动扩缩容任务
        startAutoScaling();

        log.info("场景调度器初始化完成");
    }

    @PreDestroy
    public void destroy() {
        log.info("销毁场景调度器");
        
        // 停止所有任务
        if (loadMonitorTask != null) {
            loadMonitorTask.cancel(false);
        }
        if (scheduleOptimizeTask != null) {
            scheduleOptimizeTask.cancel(false);
        }
        if (scalingTask != null) {
            scalingTask.cancel(false);
        }

        // 关闭线程池
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("场景调度器销毁完成");
    }

    // ========== 场景注册和注销 ==========

    /**
     * 注册场景
     *
     * @param scene 场景实例
     * @return 是否注册成功
     */
    public boolean registerScene(Scene scene) {
        if (scene == null) {
            return false;
        }

        try {
            Long sceneId = scene.getSceneId();
            
            // 创建调度信息
            SceneScheduleInfo scheduleInfo = new SceneScheduleInfo();
            scheduleInfo.setSceneId(sceneId);
            scheduleInfo.setServerNode(getCurrentServerNode());
            scheduleInfo.setPriority(calculateScenePriority(scene));
            
            scheduleInfoMap.put(sceneId, scheduleInfo);
            
            // 创建负载信息
            SceneLoadInfo loadInfo = new SceneLoadInfo();
            loadInfo.setSceneId(sceneId);
            loadInfoMap.put(sceneId, loadInfo);
            
            statistics.incrementTotalScheduled();
            statistics.incrementSuccessfulScheduled();
            
            log.debug("场景调度注册成功: {}", sceneId);
            return true;
            
        } catch (Exception e) {
            log.error("场景调度注册失败: {}", scene.getSceneId(), e);
            statistics.incrementFailedScheduled();
            return false;
        }
    }

    /**
     * 注销场景
     *
     * @param sceneId 场景ID
     * @return 是否注销成功
     */
    public boolean unregisterScene(Long sceneId) {
        if (sceneId == null) {
            return false;
        }

        try {
            SceneScheduleInfo scheduleInfo = scheduleInfoMap.remove(sceneId);
            SceneLoadInfo loadInfo = loadInfoMap.remove(sceneId);
            
            if (scheduleInfo != null) {
                scheduleInfo.setStatus(ScheduleStatus.RELEASED);
                log.debug("场景调度注销成功: {}", sceneId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("场景调度注销失败: {}", sceneId, e);
            return false;
        }
    }

    // ========== 场景分配 ==========

    /**
     * 分配场景到最佳节点
     *
     * @param sceneType 场景类型
     * @param requirements 分配要求
     * @return 分配的服务器节点
     */
    public String allocateScene(SceneType sceneType, Map<String, Object> requirements) {
        try {
            String serverNode = switch (scheduleStrategy) {
                case BALANCED -> allocateByBalanced(sceneType, requirements);
                case PERFORMANCE -> allocateByPerformance(sceneType, requirements);
                case RESOURCE -> allocateByResource(sceneType, requirements);
                case LOCALITY -> allocateByLocality(sceneType, requirements);
                case CUSTOM -> allocateByCustom(sceneType, requirements);
            };
            
            log.debug("场景分配完成: type={}, node={}", sceneType, serverNode);
            return serverNode;
            
        } catch (Exception e) {
            log.error("场景分配失败: type={}", sceneType, e);
            return getCurrentServerNode(); // 返回当前节点作为fallback
        }
    }

    /**
     * 均衡分配策略
     */
    private String allocateByBalanced(SceneType sceneType, Map<String, Object> requirements) {
        // 根据当前负载情况选择最合适的节点
        List<String> availableNodes = getAvailableServerNodes();
        if (availableNodes.isEmpty()) {
            return getCurrentServerNode();
        }
        
        // 选择负载最低的节点
        return availableNodes.stream()
                .min(Comparator.comparingDouble(this::getNodeLoadScore))
                .orElse(getCurrentServerNode());
    }

    /**
     * 性能优先分配策略
     */
    private String allocateByPerformance(SceneType sceneType, Map<String, Object> requirements) {
        // 选择性能最强的节点
        List<String> availableNodes = getAvailableServerNodes();
        return availableNodes.stream()
                .max(Comparator.comparingDouble(this::getNodePerformanceScore))
                .orElse(getCurrentServerNode());
    }

    /**
     * 资源优先分配策略
     */
    private String allocateByResource(SceneType sceneType, Map<String, Object> requirements) {
        // 选择资源使用率最低的节点
        List<String> availableNodes = getAvailableServerNodes();
        return availableNodes.stream()
                .min(Comparator.comparingDouble(this::getNodeResourceUsage))
                .orElse(getCurrentServerNode());
    }

    /**
     * 位置优先分配策略
     */
    private String allocateByLocality(SceneType sceneType, Map<String, Object> requirements) {
        // 根据地理位置选择最近的节点
        String clientRegion = (String) requirements.get("region");
        if (clientRegion == null) {
            return getCurrentServerNode();
        }
        
        List<String> availableNodes = getAvailableServerNodes();
        return availableNodes.stream()
                .filter(node -> isNodeInRegion(node, clientRegion))
                .min(Comparator.comparingDouble(this::getNodeLoadScore))
                .orElse(getCurrentServerNode());
    }

    /**
     * 自定义分配策略
     */
    private String allocateByCustom(SceneType sceneType, Map<String, Object> requirements) {
        // 可以根据具体需求实现自定义分配逻辑
        return allocateByBalanced(sceneType, requirements);
    }

    // ========== 负载监控 ==========

    /**
     * 启动负载监控
     */
    private void startLoadMonitoring() {
        loadMonitorTask = scheduledExecutor.scheduleAtFixedRate(
                this::updateLoadInformation, 5, 10, TimeUnit.SECONDS);
        log.info("负载监控任务已启动");
    }

    /**
     * 更新负载信息
     */
    private void updateLoadInformation() {
        try {
            double totalLoadScore = 0.0;
            int activeScenes = 0;
            
            for (Map.Entry<Long, SceneLoadInfo> entry : loadInfoMap.entrySet()) {
                SceneLoadInfo loadInfo = entry.getValue();
                
                // 更新负载信息（这里应该从实际的监控系统获取数据）
                updateSceneLoadInfo(loadInfo);
                
                if (loadInfo.getLoadScore() > 0) {
                    totalLoadScore += loadInfo.getLoadScore();
                    activeScenes++;
                }
            }
            
            // 更新平均负载分数
            if (activeScenes > 0) {
                statistics.setAvgLoadScore(totalLoadScore / activeScenes);
            }
            
            log.debug("负载信息更新完成，活跃场景数: {}, 平均负载: {:.2f}", 
                     activeScenes, statistics.getAvgLoadScore());
            
        } catch (Exception e) {
            log.error("更新负载信息失败", e);
        }
    }

    /**
     * 更新场景负载信息
     */
    private void updateSceneLoadInfo(SceneLoadInfo loadInfo) {
        // 这里应该从实际的监控系统获取数据
        // 为了示例，使用模拟数据
        loadInfo.setCpuUsage(Math.random() * 100);
        loadInfo.setMemoryUsage(Math.random() * 100);
        loadInfo.setNetworkUsage(Math.random() * 100);
        loadInfo.setEntityCount((int) (Math.random() * 1000));
        loadInfo.setMessageRate(Math.random() * 1000);
        loadInfo.setTickDelay((long) (Math.random() * 100));
        loadInfo.setUpdateTime(LocalDateTime.now());
        
        // 计算负载分数
        loadInfo.calculateLoadScore();
    }

    // ========== 调度优化 ==========

    /**
     * 启动调度优化
     */
    private void startScheduleOptimization() {
        scheduleOptimizeTask = scheduledExecutor.scheduleAtFixedRate(
                this::optimizeScheduling, 30, 60, TimeUnit.SECONDS);
        log.info("调度优化任务已启动");
    }

    /**
     * 优化调度
     */
    private void optimizeScheduling() {
        try {
            // 检查是否需要场景迁移
            checkSceneMigration();
            
            // 优化场景分布
            optimizeSceneDistribution();
            
            statistics.setLastOptimizeTime(LocalDateTime.now());
            
            log.debug("调度优化完成");
            
        } catch (Exception e) {
            log.error("调度优化失败", e);
        }
    }

    /**
     * 检查场景迁移
     */
    private void checkSceneMigration() {
        for (Map.Entry<Long, SceneLoadInfo> entry : loadInfoMap.entrySet()) {
            SceneLoadInfo loadInfo = entry.getValue();
            
            // 如果负载过高，考虑迁移
            if (loadInfo.getLoadScore() > 80.0) {
                Long sceneId = entry.getKey();
                SceneScheduleInfo scheduleInfo = scheduleInfoMap.get(sceneId);
                
                if (scheduleInfo != null && scheduleInfo.getStatus() == ScheduleStatus.RUNNING) {
                    String targetNode = findBestTargetNode(scheduleInfo.getServerNode());
                    if (targetNode != null && !targetNode.equals(scheduleInfo.getServerNode())) {
                        // 触发场景迁移
                        triggerSceneMigration(sceneId, targetNode);
                    }
                }
            }
        }
    }

    /**
     * 优化场景分布
     */
    private void optimizeSceneDistribution() {
        // 分析当前场景分布
        Map<String, List<Long>> nodeSceneMap = new HashMap<>();
        
        for (SceneScheduleInfo scheduleInfo : scheduleInfoMap.values()) {
            String node = scheduleInfo.getServerNode();
            nodeSceneMap.computeIfAbsent(node, k -> new ArrayList<>()).add(scheduleInfo.getSceneId());
        }
        
        // 检查节点间负载均衡
        double avgScenesPerNode = scheduleInfoMap.size() / (double) nodeSceneMap.size();
        
        for (Map.Entry<String, List<Long>> entry : nodeSceneMap.entrySet()) {
            String node = entry.getKey();
            List<Long> scenes = entry.getValue();
            
            // 如果某个节点场景数量过多，考虑迁移部分场景
            if (scenes.size() > avgScenesPerNode * 1.5) {
                // 选择一些场景进行迁移
                int migrateCount = (int) (scenes.size() - avgScenesPerNode);
                for (int i = 0; i < Math.min(migrateCount, 2); i++) {
                    Long sceneId = scenes.get(i);
                    String targetNode = findBestTargetNode(node);
                    if (targetNode != null) {
                        triggerSceneMigration(sceneId, targetNode);
                    }
                }
            }
        }
    }

    /**
     * 查找最佳目标节点
     */
    private String findBestTargetNode(String currentNode) {
        List<String> availableNodes = getAvailableServerNodes();
        return availableNodes.stream()
                .filter(node -> !node.equals(currentNode))
                .min(Comparator.comparingDouble(this::getNodeLoadScore))
                .orElse(null);
    }

    /**
     * 触发场景迁移
     */
    private void triggerSceneMigration(Long sceneId, String targetNode) {
        try {
            SceneScheduleInfo scheduleInfo = scheduleInfoMap.get(sceneId);
            if (scheduleInfo == null) {
                return;
            }
            
            log.info("开始场景迁移: scene={}, from={}, to={}", 
                    sceneId, scheduleInfo.getServerNode(), targetNode);
            
            scheduleInfo.setStatus(ScheduleStatus.MIGRATING);
            statistics.incrementTotalMigrations();
            
            // 这里应该实现实际的迁移逻辑
            // 为了示例，直接更新节点信息
            CompletableFuture.runAsync(() -> {
                try {
                    // 模拟迁移过程
                    Thread.sleep(5000);
                    
                    scheduleInfo.setServerNode(targetNode);
                    scheduleInfo.setStatus(ScheduleStatus.RUNNING);
                    statistics.incrementSuccessfulMigrations();
                    
                    log.info("场景迁移完成: scene={}, to={}", sceneId, targetNode);
                    
                } catch (Exception e) {
                    log.error("场景迁移失败: scene={}", sceneId, e);
                    scheduleInfo.setStatus(ScheduleStatus.FAILED);
                }
            }, scheduledExecutor);
            
        } catch (Exception e) {
            log.error("触发场景迁移失败: scene={}", sceneId, e);
        }
    }

    // ========== 自动扩缩容 ==========

    /**
     * 启动自动扩缩容
     */
    private void startAutoScaling() {
        scalingTask = scheduledExecutor.scheduleAtFixedRate(
                this::performAutoScaling, 60, 120, TimeUnit.SECONDS);
        log.info("自动扩缩容任务已启动");
    }

    /**
     * 执行自动扩缩容
     */
    private void performAutoScaling() {
        try {
            // 分析当前负载情况
            double avgLoad = statistics.getAvgLoadScore();
            int totalScenes = scheduleInfoMap.size();
            
            // 扩容条件：平均负载过高
            if (avgLoad > 70.0 && totalScenes > 0) {
                log.info("检测到高负载，考虑扩容: avgLoad={:.2f}", avgLoad);
                // 这里可以触发新节点的启动或者场景的分散
            }
            
            // 缩容条件：平均负载过低
            if (avgLoad < 30.0 && totalScenes > 0) {
                log.info("检测到低负载，考虑缩容: avgLoad={:.2f}", avgLoad);
                // 这里可以触发场景的合并或者节点的释放
            }
            
        } catch (Exception e) {
            log.error("自动扩缩容失败", e);
        }
    }

    // ========== 工具方法 ==========

    /**
     * 计算场景优先级
     */
    private int calculateScenePriority(Scene scene) {
        // 根据场景类型和配置计算优先级
        return switch (scene.getSceneType()) {
            case MAIN_CITY -> 10;
            case BATTLEFIELD, ARENA -> 8;
            case DUNGEON -> 6;
            case FIELD -> 4;
            case INSTANCE -> 2;
        };
    }

    /**
     * 获取当前服务器节点
     */
    private String getCurrentServerNode() {
        // 这里应该返回实际的服务器节点标识
        return "node-" + System.getProperty("server.id", "default");
    }

    /**
     * 获取可用服务器节点列表
     */
    private List<String> getAvailableServerNodes() {
        // 这里应该从服务注册中心获取可用节点
        return Arrays.asList("node-1", "node-2", "node-3");
    }

    /**
     * 获取节点负载分数
     */
    private double getNodeLoadScore(String node) {
        // 这里应该从监控系统获取节点负载
        return Math.random() * 100;
    }

    /**
     * 获取节点性能分数
     */
    private double getNodePerformanceScore(String node) {
        // 这里应该从性能监控系统获取节点性能
        return Math.random() * 100;
    }

    /**
     * 获取节点资源使用率
     */
    private double getNodeResourceUsage(String node) {
        // 这里应该从资源监控系统获取节点资源使用率
        return Math.random() * 100;
    }

    /**
     * 检查节点是否在指定区域
     */
    private boolean isNodeInRegion(String node, String region) {
        // 这里应该实现实际的地理位置检查
        return true;
    }

    // ========== 查询方法 ==========

    /**
     * 获取场景调度信息
     *
     * @param sceneId 场景ID
     * @return 调度信息
     */
    public SceneScheduleInfo getSceneScheduleInfo(Long sceneId) {
        return scheduleInfoMap.get(sceneId);
    }

    /**
     * 获取场景负载信息
     *
     * @param sceneId 场景ID
     * @return 负载信息
     */
    public SceneLoadInfo getSceneLoadInfo(Long sceneId) {
        return loadInfoMap.get(sceneId);
    }

    /**
     * 获取调度统计信息
     *
     * @return 统计信息
     */
    public ScheduleStatistics getScheduleStatistics() {
        return statistics;
    }

    /**
     * 获取所有场景的调度信息
     *
     * @return 调度信息列表
     */
    public List<SceneScheduleInfo> getAllScheduleInfo() {
        return new ArrayList<>(scheduleInfoMap.values());
    }

    /**
     * 设置调度策略
     *
     * @param strategy 调度策略
     */
    public void setScheduleStrategy(ScheduleStrategy strategy) {
        this.scheduleStrategy = strategy;
        log.info("调度策略设置为: {}", strategy);
    }

    /**
     * 获取调度策略
     *
     * @return 当前调度策略
     */
    public ScheduleStrategy getScheduleStrategy() {
        return scheduleStrategy;
    }

    @Override
    public String toString() {
        return String.format("SceneScheduler{scenes=%d, strategy=%s, avgLoad=%.2f}", 
                scheduleInfoMap.size(), scheduleStrategy, statistics.getAvgLoadScore());
    }
}