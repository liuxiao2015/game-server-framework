/*
 * 文件名: SystemScheduler.java
 * 用途: 系统调度优化器
 * 实现内容:
 *   - 系统调度优化
 *   - 依赖分析
 *   - 并行调度
 *   - 负载均衡
 *   - 调度策略可配
 * 技术选型:
 *   - 图算法分析系统依赖
 *   - 线程池实现并行调度
 *   - 动态负载均衡算法
 * 依赖关系:
 *   - 被SystemManager使用
 *   - 优化系统执行性能
 *   - 管理系统间的依赖关系
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.optimization;

import com.lx.gameserver.frame.ecs.core.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统调度器
 * <p>
 * 负责优化ECS系统的执行调度，支持并行执行、依赖管理和负载均衡。
 * 通过分析系统依赖关系，自动安排最优的执行顺序和并行策略。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class SystemScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemScheduler.class);
    
    /**
     * 调度策略枚举
     */
    public enum ScheduleStrategy {
        /** 串行执行 */
        SEQUENTIAL("串行执行", "按顺序逐个执行系统"),
        /** 简单并行 */
        SIMPLE_PARALLEL("简单并行", "无依赖的系统并行执行"),
        /** 流水线并行 */
        PIPELINE_PARALLEL("流水线并行", "基于依赖关系的流水线执行"),
        /** 动态负载均衡 */
        DYNAMIC_LOAD_BALANCE("动态负载均衡", "根据系统负载动态调度"),
        /** 自适应调度 */
        ADAPTIVE("自适应调度", "根据运行时性能自动选择策略");
        
        private final String displayName;
        private final String description;
        
        ScheduleStrategy(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * 系统执行节点
     */
    private static class SystemNode {
        private final System system;
        private final Set<SystemNode> dependencies = new HashSet<>();
        private final Set<SystemNode> dependents = new HashSet<>();
        private final AtomicInteger executionLevel = new AtomicInteger(-1);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicLong executionCount = new AtomicLong(0);
        private volatile boolean executing = false;
        
        public SystemNode(System system) {
            this.system = system;
        }
        
        public System getSystem() { return system; }
        public Set<SystemNode> getDependencies() { return dependencies; }
        public Set<SystemNode> getDependents() { return dependents; }
        
        public int getExecutionLevel() { return executionLevel.get(); }
        public void setExecutionLevel(int level) { executionLevel.set(level); }
        
        public long getAverageExecutionTime() {
            long count = executionCount.get();
            return count > 0 ? totalExecutionTime.get() / count : 0;
        }
        
        public void recordExecutionTime(long timeNanos) {
            totalExecutionTime.addAndGet(timeNanos);
            executionCount.incrementAndGet();
        }
        
        public boolean isExecuting() { return executing; }
        public void setExecuting(boolean executing) { this.executing = executing; }
        
        public boolean canExecute() {
            return !executing && dependencies.stream().noneMatch(SystemNode::isExecuting);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            SystemNode that = (SystemNode) obj;
            return Objects.equals(system.getClass(), that.system.getClass());
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(system.getClass());
        }
    }
    
    /**
     * 调度配置
     */
    public static class ScheduleConfig {
        private ScheduleStrategy strategy = ScheduleStrategy.SIMPLE_PARALLEL;
        private int maxThreads = Runtime.getRuntime().availableProcessors();
        private long maxExecutionTime = 16_000_000; // 16ms in nanoseconds
        private boolean enableProfiling = false;
        private boolean enableLoadBalancing = true;
        private float loadBalancingThreshold = 0.8f;
        private int adaptiveWindowSize = 100;
        
        // Getters and Setters
        public ScheduleStrategy getStrategy() { return strategy; }
        public void setStrategy(ScheduleStrategy strategy) { this.strategy = strategy; }
        
        public int getMaxThreads() { return maxThreads; }
        public void setMaxThreads(int maxThreads) { this.maxThreads = Math.max(1, maxThreads); }
        
        public long getMaxExecutionTime() { return maxExecutionTime; }
        public void setMaxExecutionTime(long maxExecutionTime) { this.maxExecutionTime = Math.max(0, maxExecutionTime); }
        
        public boolean isEnableProfiling() { return enableProfiling; }
        public void setEnableProfiling(boolean enableProfiling) { this.enableProfiling = enableProfiling; }
        
        public boolean isEnableLoadBalancing() { return enableLoadBalancing; }
        public void setEnableLoadBalancing(boolean enableLoadBalancing) { this.enableLoadBalancing = enableLoadBalancing; }
        
        public float getLoadBalancingThreshold() { return loadBalancingThreshold; }
        public void setLoadBalancingThreshold(float loadBalancingThreshold) { 
            this.loadBalancingThreshold = Math.max(0.1f, Math.min(1.0f, loadBalancingThreshold)); 
        }
        
        public int getAdaptiveWindowSize() { return adaptiveWindowSize; }
        public void setAdaptiveWindowSize(int adaptiveWindowSize) { 
            this.adaptiveWindowSize = Math.max(10, adaptiveWindowSize); 
        }
    }
    
    /**
     * 调度统计信息
     */
    public static class ScheduleStatistics {
        private final AtomicLong totalScheduleTime = new AtomicLong(0);
        private final AtomicLong scheduleCount = new AtomicLong(0);
        private final AtomicInteger parallelLevel = new AtomicInteger(0);
        private final AtomicInteger maxParallelLevel = new AtomicInteger(0);
        private final Map<String, Long> systemExecutionTimes = new ConcurrentHashMap<>();
        
        public void recordScheduleTime(long timeNanos) {
            totalScheduleTime.addAndGet(timeNanos);
            scheduleCount.incrementAndGet();
        }
        
        public void recordSystemTime(String systemName, long timeNanos) {
            systemExecutionTimes.put(systemName, timeNanos);
        }
        
        public void updateParallelLevel(int level) {
            parallelLevel.set(level);
            maxParallelLevel.updateAndGet(max -> Math.max(max, level));
        }
        
        public long getAverageScheduleTime() {
            long count = scheduleCount.get();
            return count > 0 ? totalScheduleTime.get() / count : 0;
        }
        
        public long getTotalScheduleTime() { return totalScheduleTime.get(); }
        public long getScheduleCount() { return scheduleCount.get(); }
        public int getCurrentParallelLevel() { return parallelLevel.get(); }
        public int getMaxParallelLevel() { return maxParallelLevel.get(); }
        public Map<String, Long> getSystemExecutionTimes() { return new HashMap<>(systemExecutionTimes); }
    }
    
    /**
     * 调度配置
     */
    private final ScheduleConfig config;
    
    /**
     * 线程池
     */
    private final ThreadPoolExecutor executor;
    
    /**
     * 系统节点映射
     */
    private final Map<Class<? extends System>, SystemNode> systemNodes;
    
    /**
     * 执行级别映射
     */
    private final Map<Integer, List<SystemNode>> executionLevels;
    
    /**
     * 调度统计
     */
    private final ScheduleStatistics statistics;
    
    /**
     * 是否已构建依赖图
     */
    private volatile boolean dependencyGraphBuilt = false;
    
    /**
     * 构造函数
     */
    public SystemScheduler() {
        this(new ScheduleConfig());
    }
    
    /**
     * 构造函数
     *
     * @param config 调度配置
     */
    public SystemScheduler(ScheduleConfig config) {
        this.config = config;
        this.systemNodes = new ConcurrentHashMap<>();
        this.executionLevels = new ConcurrentHashMap<>();
        this.statistics = new ScheduleStatistics();
        
        // 创建线程池
        this.executor = new ThreadPoolExecutor(
                config.getMaxThreads(),
                config.getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "ECS-SystemScheduler-" + r.hashCode());
                    t.setDaemon(true);
                    return t;
                }
        );
    }
    
    /**
     * 注册系统
     *
     * @param system 系统实例
     */
    public void registerSystem(System system) {
        SystemNode node = new SystemNode(system);
        systemNodes.put(system.getClass(), node);
        dependencyGraphBuilt = false;
        
        logger.debug("注册系统: {}", system.getClass().getSimpleName());
    }
    
    /**
     * 移除系统
     *
     * @param systemClass 系统类
     */
    public void unregisterSystem(Class<? extends System> systemClass) {
        SystemNode removed = systemNodes.remove(systemClass);
        if (removed != null) {
            // 清理依赖关系
            for (SystemNode node : systemNodes.values()) {
                node.getDependencies().remove(removed);
                node.getDependents().remove(removed);
            }
            dependencyGraphBuilt = false;
            
            logger.debug("移除系统: {}", systemClass.getSimpleName());
        }
    }
    
    /**
     * 构建依赖图
     */
    private void buildDependencyGraph() {
        if (dependencyGraphBuilt) {
            return;
        }
        
        // 清理现有依赖关系
        for (SystemNode node : systemNodes.values()) {
            node.getDependencies().clear();
            node.getDependents().clear();
        }
        
        // 构建依赖关系
        for (SystemNode node : systemNodes.values()) {
            System system = node.getSystem();
            for (Class<? extends System> depClass : system.getDependencies()) {
                SystemNode depNode = systemNodes.get(depClass);
                if (depNode != null) {
                    node.getDependencies().add(depNode);
                    depNode.getDependents().add(node);
                }
            }
        }
        
        // 检测循环依赖
        if (hasCyclicDependency()) {
            throw new IllegalStateException("检测到系统循环依赖");
        }
        
        // 计算执行级别
        calculateExecutionLevels();
        
        dependencyGraphBuilt = true;
        logger.debug("构建系统依赖图完成，共 {} 个系统，{} 个执行级别", 
                systemNodes.size(), executionLevels.size());
    }
    
    /**
     * 检测循环依赖
     *
     * @return 如果存在循环依赖返回true
     */
    private boolean hasCyclicDependency() {
        Set<SystemNode> visited = new HashSet<>();
        Set<SystemNode> recursionStack = new HashSet<>();
        
        for (SystemNode node : systemNodes.values()) {
            if (hasCyclicDependencyUtil(node, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 循环依赖检测辅助方法
     */
    private boolean hasCyclicDependencyUtil(SystemNode node, Set<SystemNode> visited, Set<SystemNode> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        
        if (visited.contains(node)) {
            return false;
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        for (SystemNode dependency : node.getDependencies()) {
            if (hasCyclicDependencyUtil(dependency, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    /**
     * 计算执行级别
     */
    private void calculateExecutionLevels() {
        executionLevels.clear();
        
        // 使用拓扑排序计算级别
        Queue<SystemNode> queue = new ArrayDeque<>();
        Map<SystemNode, Integer> inDegree = new HashMap<>();
        
        // 初始化入度
        for (SystemNode node : systemNodes.values()) {
            inDegree.put(node, node.getDependencies().size());
            if (node.getDependencies().isEmpty()) {
                queue.offer(node);
                node.setExecutionLevel(0);
            }
        }
        
        int currentLevel = 0;
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<SystemNode> currentLevelNodes = new ArrayList<>();
            
            for (int i = 0; i < levelSize; i++) {
                SystemNode node = queue.poll();
                currentLevelNodes.add(node);
                node.setExecutionLevel(currentLevel);
                
                // 更新依赖此节点的系统
                for (SystemNode dependent : node.getDependents()) {
                    int newInDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newInDegree);
                    
                    if (newInDegree == 0) {
                        queue.offer(dependent);
                    }
                }
            }
            
            executionLevels.put(currentLevel, currentLevelNodes);
            currentLevel++;
        }
    }
    
    /**
     * 执行系统调度
     *
     * @param deltaTime 时间增量
     */
    public void schedule(float deltaTime) {
        long startTime = java.lang.System.nanoTime();
        
        try {
            // 确保依赖图已构建
            buildDependencyGraph();
            
            // 根据策略执行调度
            switch (config.getStrategy()) {
                case SEQUENTIAL:
                    scheduleSequential(deltaTime);
                    break;
                case SIMPLE_PARALLEL:
                    scheduleSimpleParallel(deltaTime);
                    break;
                case PIPELINE_PARALLEL:
                    schedulePipelineParallel(deltaTime);
                    break;
                case DYNAMIC_LOAD_BALANCE:
                    scheduleDynamicLoadBalance(deltaTime);
                    break;
                case ADAPTIVE:
                    scheduleAdaptive(deltaTime);
                    break;
            }
        } catch (Exception e) {
            logger.error("系统调度执行失败", e);
        } finally {
            long endTime = java.lang.System.nanoTime();
            statistics.recordScheduleTime(endTime - startTime);
        }
    }
    
    /**
     * 串行调度
     */
    private void scheduleSequential(float deltaTime) {
        for (int level = 0; level < executionLevels.size(); level++) {
            List<SystemNode> levelNodes = executionLevels.get(level);
            if (levelNodes != null) {
                for (SystemNode node : levelNodes) {
                    executeSystem(node, deltaTime);
                }
            }
        }
    }
    
    /**
     * 简单并行调度
     */
    private void scheduleSimpleParallel(float deltaTime) {
        for (int level = 0; level < executionLevels.size(); level++) {
            List<SystemNode> levelNodes = executionLevels.get(level);
            if (levelNodes != null && !levelNodes.isEmpty()) {
                if (levelNodes.size() == 1) {
                    // 单个系统直接执行
                    executeSystem(levelNodes.get(0), deltaTime);
                } else {
                    // 多个系统并行执行
                    CountDownLatch latch = new CountDownLatch(levelNodes.size());
                    statistics.updateParallelLevel(levelNodes.size());
                    
                    for (SystemNode node : levelNodes) {
                        executor.submit(() -> {
                            try {
                                executeSystem(node, deltaTime);
                            } finally {
                                latch.countDown();
                            }
                        });
                    }
                    
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("系统并行执行被中断", e);
                    }
                }
            }
        }
    }
    
    /**
     * 流水线并行调度
     */
    private void schedulePipelineParallel(float deltaTime) {
        // 实现流水线并行逻辑
        // 这里可以实现更复杂的流水线调度算法
        scheduleSimpleParallel(deltaTime);
    }
    
    /**
     * 动态负载均衡调度
     */
    private void scheduleDynamicLoadBalance(float deltaTime) {
        // 根据系统的历史执行时间进行负载均衡
        List<SystemNode> allNodes = new ArrayList<>();
        for (List<SystemNode> levelNodes : executionLevels.values()) {
            allNodes.addAll(levelNodes);
        }
        
        // 按平均执行时间排序
        allNodes.sort(Comparator.comparingLong(SystemNode::getAverageExecutionTime).reversed());
        
        // 分配到不同线程
        List<List<SystemNode>> threadTasks = new ArrayList<>();
        for (int i = 0; i < config.getMaxThreads(); i++) {
            threadTasks.add(new ArrayList<>());
        }
        
        for (SystemNode node : allNodes) {
            // 找到当前负载最轻的线程
            int minLoadThread = 0;
            long minLoad = Long.MAX_VALUE;
            for (int i = 0; i < threadTasks.size(); i++) {
                long load = threadTasks.get(i).stream()
                        .mapToLong(SystemNode::getAverageExecutionTime)
                        .sum();
                if (load < minLoad) {
                    minLoad = load;
                    minLoadThread = i;
                }
            }
            threadTasks.get(minLoadThread).add(node);
        }
        
        // 执行任务
        CountDownLatch latch = new CountDownLatch(config.getMaxThreads());
        for (List<SystemNode> tasks : threadTasks) {
            executor.submit(() -> {
                try {
                    for (SystemNode node : tasks) {
                        if (node.canExecute()) {
                            executeSystem(node, deltaTime);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("动态负载均衡执行被中断", e);
        }
    }
    
    /**
     * 自适应调度
     */
    private void scheduleAdaptive(float deltaTime) {
        // 根据历史性能选择最优策略
        // 这里简化为选择简单并行
        scheduleSimpleParallel(deltaTime);
    }
    
    /**
     * 执行单个系统
     */
    private void executeSystem(SystemNode node, float deltaTime) {
        System system = node.getSystem();
        if (!system.isEnabled() || !system.shouldUpdate(deltaTime)) {
            return;
        }
        
        long startTime = java.lang.System.nanoTime();
        node.setExecuting(true);
        
        try {
            system.update(deltaTime);
        } catch (Exception e) {
            logger.error("系统 {} 执行失败", system.getClass().getSimpleName(), e);
        } finally {
            node.setExecuting(false);
            long endTime = java.lang.System.nanoTime();
            long executionTime = endTime - startTime;
            
            node.recordExecutionTime(executionTime);
            
            if (config.isEnableProfiling()) {
                statistics.recordSystemTime(system.getClass().getSimpleName(), executionTime);
            }
        }
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("系统调度器已关闭");
    }
    
    // Getters
    public ScheduleConfig getConfig() { return config; }
    public ScheduleStatistics getStatistics() { return statistics; }
    
    public int getRegisteredSystemCount() { return systemNodes.size(); }
    public int getExecutionLevelCount() { return executionLevels.size(); }
    
    public Map<Integer, List<String>> getExecutionLevelInfo() {
        Map<Integer, List<String>> result = new HashMap<>();
        for (Map.Entry<Integer, List<SystemNode>> entry : executionLevels.entrySet()) {
            List<String> systemNames = entry.getValue().stream()
                    .map(node -> node.getSystem().getClass().getSimpleName())
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            result.put(entry.getKey(), systemNames);
        }
        return result;
    }
}