/*
 * 文件名: SceneManager.java
 * 用途: 全局场景管理器实现
 * 实现内容:
 *   - 场景注册和注销管理
 *   - 场景创建和销毁控制
 *   - 场景查找和负载均衡
 *   - 场景调度和状态监控
 *   - 动态扩缩容和故障转移
 * 技术选型:
 *   - Spring Bean单例模式保证全局唯一
 *   - ConcurrentHashMap保证线程安全
 *   - 事件驱动架构处理场景状态变化
 *   - 定时任务监控场景健康状态
 *   - Actor模型集成处理场景消息
 * 依赖关系:
 *   - 依赖SceneFactory进行场景创建
 *   - 依赖SceneScheduler进行场景调度
 *   - 与SceneActor协作进行消息处理
 *   - 被上层服务调用进行场景操作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.manager;

import com.lx.gameserver.business.scene.core.Scene;
import com.lx.gameserver.business.scene.core.SceneConfig;
import com.lx.gameserver.business.scene.core.SceneType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 全局场景管理器
 * <p>
 * 负责管理所有游戏场景的生命周期，包括场景的创建、销毁、
 * 查找、负载均衡等功能。提供统一的场景管理接口，
 * 支持动态扩容和故障转移。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class SceneManager {

    /** 场景存储 */
    private final ConcurrentHashMap<Long, Scene> scenes = new ConcurrentHashMap<>();

    /** 场景类型索引 */
    private final ConcurrentHashMap<SceneType, Set<Long>> sceneTypeIndex = new ConcurrentHashMap<>();

    /** 场景名称索引 */
    private final ConcurrentHashMap<String, Long> sceneNameIndex = new ConcurrentHashMap<>();

    /** 场景ID生成器 */
    private final AtomicLong sceneIdGenerator = new AtomicLong(1);

    /** 场景统计信息 */
    private final ConcurrentHashMap<Long, SceneStatistics> sceneStatistics = new ConcurrentHashMap<>();

    /** 定时任务执行器 */
    private ScheduledExecutorService scheduledExecutor;

    /** 场景工厂 */
    @Autowired
    private SceneFactory sceneFactory;

    /** 场景调度器 */
    @Autowired
    private SceneScheduler sceneScheduler;

    /**
     * 场景统计信息
     */
    public static class SceneStatistics {
        private long createTime;
        private long totalPlayers;
        private long totalEntities;
        private long messageCount;
        private long tickCount;
        private double avgLoadTime;
        private LocalDateTime lastUpdateTime;

        // Getters and setters
        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }
        public long getTotalPlayers() { return totalPlayers; }
        public void setTotalPlayers(long totalPlayers) { this.totalPlayers = totalPlayers; }
        public long getTotalEntities() { return totalEntities; }
        public void setTotalEntities(long totalEntities) { this.totalEntities = totalEntities; }
        public long getMessageCount() { return messageCount; }
        public void setMessageCount(long messageCount) { this.messageCount = messageCount; }
        public long getTickCount() { return tickCount; }
        public void setTickCount(long tickCount) { this.tickCount = tickCount; }
        public double getAvgLoadTime() { return avgLoadTime; }
        public void setAvgLoadTime(double avgLoadTime) { this.avgLoadTime = avgLoadTime; }
        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }

    /**
     * 负载均衡策略枚举
     */
    public enum LoadBalanceStrategy {
        /** 轮询 */
        ROUND_ROBIN,
        /** 最少连接 */
        LEAST_CONNECTIONS,
        /** 最少负载 */
        LEAST_LOADED,
        /** 随机 */
        RANDOM
    }

    /** 当前负载均衡策略 */
    private LoadBalanceStrategy loadBalanceStrategy = LoadBalanceStrategy.LEAST_LOADED;

    /** 轮询计数器 */
    private final AtomicLong roundRobinCounter = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        log.info("初始化场景管理器");
        
        // 初始化定时任务执行器
        scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "SceneManager-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // 启动场景监控任务
        scheduledExecutor.scheduleAtFixedRate(this::monitorScenes, 10, 30, TimeUnit.SECONDS);
        
        // 启动场景清理任务
        scheduledExecutor.scheduleAtFixedRate(this::cleanupExpiredScenes, 60, 60, TimeUnit.SECONDS);

        // 初始化场景类型索引
        for (SceneType type : SceneType.values()) {
            sceneTypeIndex.put(type, ConcurrentHashMap.newKeySet());
        }

        log.info("场景管理器初始化完成");
    }

    @PreDestroy
    public void destroy() {
        log.info("销毁场景管理器");
        
        // 关闭定时任务
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

        // 销毁所有场景
        destroyAllScenes();

        log.info("场景管理器销毁完成");
    }

    // ========== 场景创建和销毁 ==========

    /**
     * 创建场景
     *
     * @param sceneType 场景类型
     * @param sceneName 场景名称
     * @param config 场景配置
     * @return 场景实例，创建失败返回null
     */
    public Scene createScene(SceneType sceneType, String sceneName, SceneConfig config) {
        try {
            // 生成场景ID
            Long sceneId = sceneIdGenerator.getAndIncrement();
            
            // 检查场景名称是否已存在
            if (sceneNameIndex.containsKey(sceneName)) {
                log.warn("场景名称 {} 已存在，无法创建", sceneName);
                return null;
            }

            // 使用工厂创建场景
            Scene scene = sceneFactory.createScene(sceneId, sceneType, sceneName, config);
            if (scene == null) {
                log.error("场景工厂创建场景失败: type={}, name={}", sceneType, sceneName);
                return null;
            }

            // 初始化场景
            scene.initialize();

            // 注册场景
            registerScene(scene);

            log.info("成功创建场景: {} (ID: {}, Type: {})", sceneName, sceneId, sceneType);
            return scene;

        } catch (Exception e) {
            log.error("创建场景失败: type={}, name={}", sceneType, sceneName, e);
            return null;
        }
    }

    /**
     * 创建场景（使用默认配置）
     *
     * @param sceneType 场景类型
     * @param sceneName 场景名称
     * @return 场景实例
     */
    public Scene createScene(SceneType sceneType, String sceneName) {
        SceneConfig config = SceneConfig.createDefault(sceneType);
        return createScene(sceneType, sceneName, config);
    }

    /**
     * 销毁场景
     *
     * @param sceneId 场景ID
     * @return 是否销毁成功
     */
    public boolean destroyScene(Long sceneId) {
        Scene scene = scenes.get(sceneId);
        if (scene == null) {
            log.warn("场景 {} 不存在，无法销毁", sceneId);
            return false;
        }

        try {
            // 从调度器中移除
            sceneScheduler.unregisterScene(sceneId);

            // 销毁场景
            scene.destroy();

            // 注销场景
            unregisterScene(sceneId);

            log.info("成功销毁场景: {} (ID: {})", scene.getSceneName(), sceneId);
            return true;

        } catch (Exception e) {
            log.error("销毁场景失败: {}", sceneId, e);
            return false;
        }
    }

    /**
     * 销毁所有场景
     */
    public void destroyAllScenes() {
        log.info("开始销毁所有场景，总数: {}", scenes.size());
        
        List<Long> sceneIds = new ArrayList<>(scenes.keySet());
        for (Long sceneId : sceneIds) {
            destroyScene(sceneId);
        }
        
        // 清理索引
        scenes.clear();
        sceneTypeIndex.values().forEach(Set::clear);
        sceneNameIndex.clear();
        sceneStatistics.clear();

        log.info("所有场景销毁完成");
    }

    // ========== 场景注册和注销 ==========

    /**
     * 注册场景
     *
     * @param scene 场景实例
     */
    private void registerScene(Scene scene) {
        Long sceneId = scene.getSceneId();
        
        // 添加到主存储
        scenes.put(sceneId, scene);
        
        // 添加到类型索引
        Set<Long> typeScenes = sceneTypeIndex.computeIfAbsent(scene.getSceneType(), 
                k -> ConcurrentHashMap.newKeySet());
        typeScenes.add(sceneId);
        
        // 添加到名称索引
        sceneNameIndex.put(scene.getSceneName(), sceneId);
        
        // 初始化统计信息
        SceneStatistics stats = new SceneStatistics();
        stats.setCreateTime(System.currentTimeMillis());
        stats.setLastUpdateTime(LocalDateTime.now());
        sceneStatistics.put(sceneId, stats);

        // 注册到调度器
        sceneScheduler.registerScene(scene);

        log.debug("场景 {} 注册完成", sceneId);
    }

    /**
     * 注销场景
     *
     * @param sceneId 场景ID
     */
    private void unregisterScene(Long sceneId) {
        Scene scene = scenes.remove(sceneId);
        if (scene != null) {
            // 从类型索引中移除
            Set<Long> typeScenes = sceneTypeIndex.get(scene.getSceneType());
            if (typeScenes != null) {
                typeScenes.remove(sceneId);
            }
            
            // 从名称索引中移除
            sceneNameIndex.remove(scene.getSceneName());
            
            // 移除统计信息
            sceneStatistics.remove(sceneId);

            log.debug("场景 {} 注销完成", sceneId);
        }
    }

    // ========== 场景查找 ==========

    /**
     * 根据ID获取场景
     *
     * @param sceneId 场景ID
     * @return 场景实例，不存在返回null
     */
    public Scene getScene(Long sceneId) {
        return scenes.get(sceneId);
    }

    /**
     * 根据名称获取场景
     *
     * @param sceneName 场景名称
     * @return 场景实例，不存在返回null
     */
    public Scene getSceneByName(String sceneName) {
        Long sceneId = sceneNameIndex.get(sceneName);
        return sceneId != null ? scenes.get(sceneId) : null;
    }

    /**
     * 获取所有场景
     *
     * @return 场景列表
     */
    public List<Scene> getAllScenes() {
        return new ArrayList<>(scenes.values());
    }

    /**
     * 按类型获取场景
     *
     * @param sceneType 场景类型
     * @return 场景列表
     */
    public List<Scene> getScenesByType(SceneType sceneType) {
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
     * 获取运行中的场景
     *
     * @return 运行中的场景列表
     */
    public List<Scene> getRunningScenes() {
        return scenes.values().stream()
                .filter(Scene::isRunning)
                .collect(Collectors.toList());
    }

    // ========== 负载均衡 ==========

    /**
     * 查找最佳场景（负载均衡）
     *
     * @param sceneType 场景类型
     * @return 最佳场景实例，如果没有可用场景返回null
     */
    public Scene findBestScene(SceneType sceneType) {
        List<Scene> availableScenes = getScenesByType(sceneType).stream()
                .filter(scene -> scene.isRunning() && !scene.isFull())
                .collect(Collectors.toList());

        if (availableScenes.isEmpty()) {
            return null;
        }

        return switch (loadBalanceStrategy) {
            case ROUND_ROBIN -> findSceneByRoundRobin(availableScenes);
            case LEAST_CONNECTIONS -> findSceneByLeastConnections(availableScenes);
            case LEAST_LOADED -> findSceneByLeastLoaded(availableScenes);
            case RANDOM -> findSceneByRandom(availableScenes);
        };
    }

    /**
     * 轮询方式查找场景
     */
    private Scene findSceneByRoundRobin(List<Scene> scenes) {
        if (scenes.isEmpty()) return null;
        int index = (int) (roundRobinCounter.getAndIncrement() % scenes.size());
        return scenes.get(index);
    }

    /**
     * 最少连接方式查找场景
     */
    private Scene findSceneByLeastConnections(List<Scene> scenes) {
        return scenes.stream()
                .min(Comparator.comparingInt(scene -> scene.getAllEntities().size()))
                .orElse(null);
    }

    /**
     * 最少负载方式查找场景
     */
    private Scene findSceneByLeastLoaded(List<Scene> scenes) {
        return scenes.stream()
                .min(Comparator.comparingDouble(this::calculateSceneLoad))
                .orElse(null);
    }

    /**
     * 随机方式查找场景
     */
    private Scene findSceneByRandom(List<Scene> scenes) {
        if (scenes.isEmpty()) return null;
        Random random = new Random();
        return scenes.get(random.nextInt(scenes.size()));
    }

    /**
     * 计算场景负载
     *
     * @param scene 场景
     * @return 负载值（0-1之间）
     */
    private double calculateSceneLoad(Scene scene) {
        int entityCount = scene.getAllEntities().size();
        int maxEntities = scene.getConfig().getMaxEntities();
        return (double) entityCount / maxEntities;
    }

    // ========== 监控和统计 ==========

    /**
     * 监控场景状态
     */
    private void monitorScenes() {
        try {
            for (Map.Entry<Long, Scene> entry : scenes.entrySet()) {
                Scene scene = entry.getValue();
                SceneStatistics stats = sceneStatistics.get(entry.getKey());
                
                if (stats != null) {
                    // 更新统计信息
                    Map<String, Object> sceneStats = scene.getStatistics();
                    stats.setTotalEntities((Long) sceneStats.get("entityCount"));
                    stats.setMessageCount((Long) sceneStats.get("messageCount"));
                    stats.setTickCount((Long) sceneStats.get("tickCount"));
                    stats.setLastUpdateTime(LocalDateTime.now());
                    
                    // 检查场景健康状态
                    checkSceneHealth(scene, stats);
                }
            }
            
            log.debug("场景监控完成，总场景数: {}", scenes.size());
            
        } catch (Exception e) {
            log.error("场景监控异常", e);
        }
    }

    /**
     * 检查场景健康状态
     */
    private void checkSceneHealth(Scene scene, SceneStatistics stats) {
        // 检查场景是否长时间无活动
        LocalDateTime lastUpdate = (LocalDateTime) scene.getStatistics().get("lastTickTime");
        if (lastUpdate != null && lastUpdate.isBefore(LocalDateTime.now().minusMinutes(5))) {
            log.warn("场景 {} 可能存在问题：长时间无Tick更新", scene.getSceneId());
        }
        
        // 检查场景是否为空且超时
        if (scene.isEmpty()) {
            long emptyTimeout = scene.getConfig().getEmptyTimeout();
            if (emptyTimeout > 0) {
                long emptyTime = System.currentTimeMillis() - stats.getCreateTime();
                if (emptyTime > emptyTimeout) {
                    log.info("场景 {} 空闲超时，准备销毁", scene.getSceneId());
                    // 可以在这里触发场景销毁
                }
            }
        }
    }

    /**
     * 清理过期场景
     */
    private void cleanupExpiredScenes() {
        try {
            List<Long> expiredScenes = new ArrayList<>();
            
            for (Map.Entry<Long, Scene> entry : scenes.entrySet()) {
                Scene scene = entry.getValue();
                
                // 检查场景生存时间
                long lifeTime = scene.getConfig().getLifeTime();
                if (lifeTime > 0) {
                    long createTime = scene.getCreateTime().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
                    if (System.currentTimeMillis() - createTime > lifeTime) {
                        expiredScenes.add(entry.getKey());
                    }
                }
                
                // 检查场景是否已关闭
                if (scene.getState() == Scene.SceneState.CLOSED) {
                    expiredScenes.add(entry.getKey());
                }
            }
            
            // 销毁过期场景
            for (Long sceneId : expiredScenes) {
                log.info("清理过期场景: {}", sceneId);
                destroyScene(sceneId);
            }
            
            if (!expiredScenes.isEmpty()) {
                log.info("清理过期场景完成，清理数量: {}", expiredScenes.size());
            }
            
        } catch (Exception e) {
            log.error("清理过期场景异常", e);
        }
    }

    /**
     * 获取场景统计信息
     *
     * @param sceneId 场景ID
     * @return 统计信息
     */
    public SceneStatistics getSceneStatistics(Long sceneId) {
        return sceneStatistics.get(sceneId);
    }

    /**
     * 获取全局统计信息
     *
     * @return 全局统计信息
     */
    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalScenes", scenes.size());
        stats.put("runningScenes", getRunningScenes().size());
        
        // 按类型统计
        Map<String, Integer> typeStats = new HashMap<>();
        for (SceneType type : SceneType.values()) {
            typeStats.put(type.getCode(), getScenesByType(type).size());
        }
        stats.put("scenesByType", typeStats);
        
        // 负载统计
        int totalEntities = scenes.values().stream()
                .mapToInt(scene -> scene.getAllEntities().size())
                .sum();
        stats.put("totalEntities", totalEntities);
        
        return stats;
    }

    // ========== 配置管理 ==========

    /**
     * 设置负载均衡策略
     *
     * @param strategy 负载均衡策略
     */
    public void setLoadBalanceStrategy(LoadBalanceStrategy strategy) {
        this.loadBalanceStrategy = strategy;
        log.info("负载均衡策略设置为: {}", strategy);
    }

    /**
     * 获取负载均衡策略
     *
     * @return 当前负载均衡策略
     */
    public LoadBalanceStrategy getLoadBalanceStrategy() {
        return loadBalanceStrategy;
    }

    @Override
    public String toString() {
        return String.format("SceneManager{scenes=%d, strategy=%s}", 
                scenes.size(), loadBalanceStrategy);
    }
}