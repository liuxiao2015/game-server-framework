/*
 * 文件名: ExecutorManager.java
 * 用途: 执行器管理器
 * 实现内容:
 *   - 统一管理各类执行器的生命周期
 *   - 执行器注册与获取（按名称/类型）
 *   - 优雅关闭所有执行器
 *   - 执行器健康检查
 * 技术选型:
 *   - Spring Bean管理 + Java 17并发API
 *   - 线程安全的执行器注册表
 *   - 生命周期管理和监控
 * 依赖关系:
 *   - 与Spring容器集成
 *   - 管理VirtualThreadExecutor和其他执行器
 *   - 提供全局执行器服务
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * 执行器管理器
 * <p>
 * 统一管理各类执行器的生命周期，提供全局执行器注册、获取、关闭功能。
 * 支持按名称和类型查找执行器，提供健康检查和监控功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Component
public class ExecutorManager {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorManager.class);

    /**
     * 执行器注册表
     */
    private final Map<String, ExecutorInfo> executors = new ConcurrentHashMap<>();

    /**
     * 管理器是否已初始化
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 管理器是否正在关闭
     */
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

    /**
     * 默认关闭超时时间（秒）
     */
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 30;

    /**
     * 执行器信息
     */
    public static class ExecutorInfo {
        private final String name;
        private final ExecutorService executor;
        private final ExecutorType type;
        private final long createTime;
        private volatile boolean healthy = true;

        public ExecutorInfo(String name, ExecutorService executor, ExecutorType type) {
            this.name = name;
            this.executor = executor;
            this.type = type;
            this.createTime = System.currentTimeMillis();
        }

        // Getter方法
        public String getName() { return name; }
        public ExecutorService getExecutor() { return executor; }
        public ExecutorType getType() { return type; }
        public long getCreateTime() { return createTime; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
    }

    /**
     * 执行器类型
     */
    public enum ExecutorType {
        /**
         * 虚拟线程执行器
         */
        VIRTUAL_THREAD,
        /**
         * 固定大小线程池
         */
        FIXED_THREAD_POOL,
        /**
         * 缓存线程池
         */
        CACHED_THREAD_POOL,
        /**
         * 单线程执行器
         */
        SINGLE_THREAD,
        /**
         * 定时任务执行器
         */
        SCHEDULED,
        /**
         * 自定义执行器
         */
        CUSTOM
    }

    /**
     * 初始化管理器
     */
    @PostConstruct
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.info("执行器管理器开始初始化...");
            
            // 创建默认执行器
            createDefaultExecutors();
            
            logger.info("执行器管理器初始化完成，已注册{}个执行器", executors.size());
        }
    }

    /**
     * 创建默认执行器
     */
    private void createDefaultExecutors() {
        // 创建默认虚拟线程执行器
        VirtualThreadExecutor defaultVirtualExecutor = new VirtualThreadExecutor(
            "default-virtual",
            GameThreadFactory.createVirtualThreadFactory("game-vt"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2
        );
        registerExecutor("default-virtual", defaultVirtualExecutor, ExecutorType.VIRTUAL_THREAD);

        // 创建IO密集型执行器
        VirtualThreadExecutor ioExecutor = new VirtualThreadExecutor(
            "io-intensive",
            GameThreadFactory.createVirtualThreadFactory("game-io"),
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 4
        );
        registerExecutor("io-intensive", ioExecutor, ExecutorType.VIRTUAL_THREAD);

        // 创建CPU密集型执行器
        ExecutorService cpuExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            GameThreadFactory.createPlatformThreadFactory("game-cpu")
        );
        registerExecutor("cpu-intensive", cpuExecutor, ExecutorType.FIXED_THREAD_POOL);

        // 创建定时任务执行器
        ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(
            4,
            GameThreadFactory.createPlatformThreadFactory("game-scheduled")
        );
        registerExecutor("scheduled", scheduledExecutor, ExecutorType.SCHEDULED);
    }

    /**
     * 注册执行器
     *
     * @param name     执行器名称
     * @param executor 执行器实例
     * @param type     执行器类型
     */
    public void registerExecutor(String name, ExecutorService executor, ExecutorType type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("执行器名称不能为空");
        }
        if (executor == null) {
            throw new IllegalArgumentException("执行器实例不能为null");
        }
        if (type == null) {
            throw new IllegalArgumentException("执行器类型不能为null");
        }

        ExecutorInfo info = new ExecutorInfo(name, executor, type);
        ExecutorInfo existing = executors.put(name, info);
        
        if (existing != null) {
            logger.warn("执行器[{}]已存在，将被替换", name);
            // 关闭被替换的执行器
            shutdownExecutor(existing);
        }
        
        logger.info("执行器[{}]注册成功，类型:{}", name, type);
    }

    /**
     * 获取执行器
     *
     * @param name 执行器名称
     * @return 执行器实例，不存在时返回null
     */
    public ExecutorService getExecutor(String name) {
        ExecutorInfo info = executors.get(name);
        return info != null ? info.getExecutor() : null;
    }

    /**
     * 获取虚拟线程执行器
     *
     * @param name 执行器名称
     * @return 虚拟线程执行器实例，不存在或类型不匹配时返回null
     */
    public VirtualThreadExecutor getVirtualThreadExecutor(String name) {
        ExecutorService executor = getExecutor(name);
        return executor instanceof VirtualThreadExecutor ? (VirtualThreadExecutor) executor : null;
    }

    /**
     * 获取定时任务执行器
     *
     * @param name 执行器名称
     * @return 定时任务执行器实例，不存在或类型不匹配时返回null
     */
    public ScheduledExecutorService getScheduledExecutor(String name) {
        ExecutorService executor = getExecutor(name);
        return executor instanceof ScheduledExecutorService ? (ScheduledExecutorService) executor : null;
    }

    /**
     * 获取默认执行器
     */
    public ExecutorService getDefaultExecutor() {
        return getExecutor("default-virtual");
    }

    /**
     * 获取IO密集型执行器
     */
    public ExecutorService getIoExecutor() {
        return getExecutor("io-intensive");
    }

    /**
     * 获取CPU密集型执行器
     */
    public ExecutorService getCpuExecutor() {
        return getExecutor("cpu-intensive");
    }

    /**
     * 获取定时任务执行器
     */
    public ScheduledExecutorService getScheduledExecutor() {
        return getScheduledExecutor("scheduled");
    }

    /**
     * 获取所有执行器名称
     */
    public List<String> getExecutorNames() {
        return new ArrayList<>(executors.keySet());
    }

    /**
     * 获取指定类型的执行器
     *
     * @param type 执行器类型
     * @return 执行器列表
     */
    public List<ExecutorService> getExecutorsByType(ExecutorType type) {
        return executors.values().stream()
            .filter(info -> info.getType() == type)
            .map(ExecutorInfo::getExecutor)
            .toList();
    }

    /**
     * 检查执行器是否存在
     *
     * @param name 执行器名称
     * @return 是否存在
     */
    public boolean hasExecutor(String name) {
        return executors.containsKey(name);
    }

    /**
     * 执行器健康检查
     */
    public void healthCheck() {
        logger.debug("开始执行器健康检查...");
        
        for (ExecutorInfo info : executors.values()) {
            try {
                ExecutorService executor = info.getExecutor();
                boolean healthy = !executor.isShutdown() && !executor.isTerminated();
                info.setHealthy(healthy);
                
                if (!healthy) {
                    logger.warn("执行器[{}]健康检查失败，状态异常", info.getName());
                }
            } catch (Exception e) {
                info.setHealthy(false);
                logger.error("执行器[{}]健康检查异常", info.getName(), e);
            }
        }
        
        logger.debug("执行器健康检查完成");
    }

    /**
     * 获取执行器状态信息
     */
    public List<String> getExecutorStatus() {
        List<String> statusList = new ArrayList<>();
        
        for (ExecutorInfo info : executors.values()) {
            ExecutorService executor = info.getExecutor();
            String status = String.format(
                "Executor[name=%s, type=%s, healthy=%b, shutdown=%b, terminated=%b, uptime=%ds]",
                info.getName(), info.getType(), info.isHealthy(),
                executor.isShutdown(), executor.isTerminated(),
                (System.currentTimeMillis() - info.getCreateTime()) / 1000
            );
            
            // 添加VirtualThreadExecutor的详细信息
            if (executor instanceof VirtualThreadExecutor) {
                VirtualThreadExecutor vte = (VirtualThreadExecutor) executor;
                status += String.format(" - %s", vte.getStatus());
            }
            
            statusList.add(status);
        }
        
        return statusList;
    }

    /**
     * 移除执行器
     *
     * @param name 执行器名称
     * @return 是否移除成功
     */
    public boolean removeExecutor(String name) {
        ExecutorInfo info = executors.remove(name);
        if (info != null) {
            shutdownExecutor(info);
            logger.info("执行器[{}]已移除", name);
            return true;
        }
        return false;
    }

    /**
     * 关闭指定执行器
     */
    private void shutdownExecutor(ExecutorInfo info) {
        try {
            ExecutorService executor = info.getExecutor();
            executor.shutdown();
            
            if (!executor.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                logger.warn("执行器[{}]在{}秒内未能优雅关闭，强制关闭", info.getName(), DEFAULT_SHUTDOWN_TIMEOUT);
                executor.shutdownNow();
            }
            
            logger.info("执行器[{}]已关闭", info.getName());
        } catch (Exception e) {
            logger.error("关闭执行器[{}]时发生异常", info.getName(), e);
        }
    }

    /**
     * 关闭管理器
     */
    @PreDestroy
    public void shutdown() {
        if (shutdownInProgress.compareAndSet(false, true)) {
            logger.info("执行器管理器开始关闭...");
            
            // 关闭所有执行器
            for (ExecutorInfo info : executors.values()) {
                shutdownExecutor(info);
            }
            
            executors.clear();
            logger.info("执行器管理器已关闭");
        }
    }

    /**
     * 获取管理器状态
     */
    public String getManagerStatus() {
        return String.format("ExecutorManager[initialized=%b, shutdownInProgress=%b, executorCount=%d]",
            initialized.get(), shutdownInProgress.get(), executors.size());
    }
}