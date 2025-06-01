/*
 * 文件名: StartupProgressTracker.java
 * 用途: 启动进度跟踪器
 * 内容: 
 *   - 启动阶段进度显示
 *   - 模块加载状态监控
 *   - 启动时间统计
 *   - 失败回滚机制
 * 技术选型: 
 *   - Java并发工具
 *   - 观察者模式
 *   - 状态机管理
 * 依赖关系: 
 *   - 与ServiceManager协作
 *   - 被Bootstrap使用
 */
package com.lx.gameserver.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动进度跟踪器
 * <p>
 * 跟踪和显示游戏服务器启动过程中的各个阶段进度：
 * 1. 环境验证阶段
 * 2. 框架模块加载阶段
 * 3. 服务启动阶段
 * 4. 健康检查阶段
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-06-01
 */
@Component
public class StartupProgressTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupProgressTracker.class);
    
    private final List<StartupStage> stages = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, StageProgress> stageProgress = new ConcurrentHashMap<>();
    private final AtomicInteger completedStages = new AtomicInteger(0);
    private Instant startupStartTime;
    private volatile boolean rollbackInitiated = false;
    
    /**
     * 启动阶段枚举
     */
    public enum StartupStage {
        ENVIRONMENT_VALIDATION("环境验证", 1),
        FRAMEWORK_INITIALIZATION("框架初始化", 2),
        MODULE_LOADING("模块加载", 3),
        SERVICE_STARTUP("服务启动", 4),
        HEALTH_CHECK("健康检查", 5);
        
        private final String description;
        private final int order;
        
        StartupStage(String description, int order) {
            this.description = description;
            this.order = order;
        }
        
        public String getDescription() {
            return description;
        }
        
        public int getOrder() {
            return order;
        }
    }
    
    /**
     * 阶段进度状态
     */
    public enum StageStatus {
        PENDING("等待中"),
        IN_PROGRESS("进行中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        ROLLED_BACK("已回滚");
        
        private final String description;
        
        StageStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 初始化进度跟踪器
     */
    public void initialize() {
        startupStartTime = Instant.now();
        stages.clear();
        stageProgress.clear();
        completedStages.set(0);
        rollbackInitiated = false;
        
        // 初始化所有启动阶段
        for (StartupStage stage : StartupStage.values()) {
            stages.add(stage);
            stageProgress.put(stage.name(), new StageProgress(stage));
        }
        
        logger.info("启动进度跟踪器已初始化，共 {} 个阶段", stages.size());
        printProgressHeader();
    }
    
    /**
     * 开始某个阶段
     */
    public void startStage(StartupStage stage) {
        StageProgress progress = stageProgress.get(stage.name());
        if (progress != null) {
            progress.start();
            logger.info("阶段开始: {} (阶段 {}/{})", stage.getDescription(), stage.getOrder(), stages.size());
            printCurrentProgress();
        }
    }
    
    /**
     * 完成某个阶段
     */
    public void completeStage(StartupStage stage) {
        StageProgress progress = stageProgress.get(stage.name());
        if (progress != null) {
            progress.complete();
            completedStages.incrementAndGet();
            logger.info("阶段完成: {} (耗时: {}ms)", stage.getDescription(), progress.getDurationMs());
            printCurrentProgress();
        }
    }
    
    /**
     * 标记某个阶段失败
     */
    public void failStage(StartupStage stage, String errorMessage) {
        StageProgress progress = stageProgress.get(stage.name());
        if (progress != null) {
            progress.fail(errorMessage);
            logger.error("阶段失败: {} - {}", stage.getDescription(), errorMessage);
            printCurrentProgress();
        }
    }
    
    /**
     * 更新阶段进度（百分比）
     */
    public void updateStageProgress(StartupStage stage, int percentage, String detail) {
        StageProgress progress = stageProgress.get(stage.name());
        if (progress != null) {
            progress.updateProgress(percentage, detail);
            if (percentage % 25 == 0) { // 每25%显示一次
                logger.debug("阶段进度: {} - {}% ({})", stage.getDescription(), percentage, detail);
            }
        }
    }
    
    /**
     * 启动失败回滚机制
     */
    public void initiateRollback(String reason) {
        if (rollbackInitiated) {
            return;
        }
        
        rollbackInitiated = true;
        logger.error("启动失败，开始回滚: {}", reason);
        
        // 按相反顺序回滚已完成的阶段
        List<StartupStage> completedStages = new ArrayList<>();
        for (StartupStage stage : StartupStage.values()) {
            StageProgress progress = stageProgress.get(stage.name());
            if (progress != null && progress.status == StageStatus.COMPLETED) {
                completedStages.add(0, stage); // 倒序添加
            }
        }
        
        for (StartupStage stage : completedStages) {
            rollbackStage(stage);
        }
        
        logger.info("启动回滚完成");
    }
    
    /**
     * 回滚单个阶段
     */
    private void rollbackStage(StartupStage stage) {
        StageProgress progress = stageProgress.get(stage.name());
        if (progress != null) {
            progress.rollback();
            logger.info("回滚阶段: {}", stage.getDescription());
            
            try {
                // 执行具体的回滚操作
                switch (stage) {
                    case SERVICE_STARTUP:
                        rollbackServices();
                        break;
                    case MODULE_LOADING:
                        rollbackModules();
                        break;
                    case FRAMEWORK_INITIALIZATION:
                        rollbackFramework();
                        break;
                    default:
                        // 其他阶段的回滚操作
                        break;
                }
            } catch (Exception e) {
                logger.error("回滚阶段 {} 时发生异常", stage.getDescription(), e);
            }
        }
    }
    
    /**
     * 回滚服务
     */
    private void rollbackServices() {
        logger.info("正在回滚服务...");
        // 这里可以添加具体的服务回滚逻辑
    }
    
    /**
     * 回滚模块
     */
    private void rollbackModules() {
        logger.info("正在回滚模块...");
        // 这里可以添加具体的模块回滚逻辑
    }
    
    /**
     * 回滚框架
     */
    private void rollbackFramework() {
        logger.info("正在回滚框架...");
        // 这里可以添加具体的框架回滚逻辑
    }
    
    /**
     * 获取总体进度百分比
     */
    public int getOverallProgress() {
        if (stages.isEmpty()) {
            return 0;
        }
        
        int totalProgress = 0;
        for (StartupStage stage : StartupStage.values()) {
            StageProgress progress = stageProgress.get(stage.name());
            if (progress != null) {
                if (progress.status == StageStatus.COMPLETED) {
                    totalProgress += 100;
                } else if (progress.status == StageStatus.IN_PROGRESS) {
                    totalProgress += progress.percentage;
                }
            }
        }
        
        return totalProgress / stages.size();
    }
    
    /**
     * 获取启动总耗时
     */
    public long getStartupDurationMs() {
        if (startupStartTime == null) {
            return 0;
        }
        return Duration.between(startupStartTime, Instant.now()).toMillis();
    }
    
    /**
     * 是否启动完成
     */
    public boolean isStartupCompleted() {
        return completedStages.get() == stages.size() && !rollbackInitiated;
    }
    
    /**
     * 是否启动失败
     */
    public boolean isStartupFailed() {
        for (StageProgress progress : stageProgress.values()) {
            if (progress.status == StageStatus.FAILED) {
                return true;
            }
        }
        return rollbackInitiated;
    }
    
    /**
     * 打印进度表头
     */
    private void printProgressHeader() {
        logger.info("==================== 启动进度跟踪 ====================");
    }
    
    /**
     * 打印当前进度
     */
    private void printCurrentProgress() {
        int overallProgress = getOverallProgress();
        long durationMs = getStartupDurationMs();
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("整体进度: %d%% | 耗时: %dms | ", overallProgress, durationMs));
        
        // 显示各阶段状态
        for (StartupStage stage : StartupStage.values()) {
            StageProgress progress = stageProgress.get(stage.name());
            if (progress != null) {
                String statusIcon = getStatusIcon(progress.status);
                sb.append(String.format("%s%s ", statusIcon, stage.getDescription()));
            }
        }
        
        logger.info(sb.toString());
        
        // 如果完成，打印总结
        if (isStartupCompleted()) {
            printCompletionSummary();
        }
    }
    
    /**
     * 获取状态图标
     */
    private String getStatusIcon(StageStatus status) {
        switch (status) {
            case PENDING:
                return "⏳";
            case IN_PROGRESS:
                return "🔄";
            case COMPLETED:
                return "✅";
            case FAILED:
                return "❌";
            case ROLLED_BACK:
                return "🔙";
            default:
                return "❓";
        }
    }
    
    /**
     * 打印完成总结
     */
    private void printCompletionSummary() {
        logger.info("==================== 启动完成总结 ====================");
        logger.info("总耗时: {}ms", getStartupDurationMs());
        logger.info("成功阶段: {}/{}", completedStages.get(), stages.size());
        
        for (StartupStage stage : StartupStage.values()) {
            StageProgress progress = stageProgress.get(stage.name());
            if (progress != null) {
                logger.info("  {} {}: {}ms", 
                    getStatusIcon(progress.status), 
                    stage.getDescription(), 
                    progress.getDurationMs());
            }
        }
        logger.info("=====================================================");
    }
    
    /**
     * 阶段进度类
     */
    private static class StageProgress {
        private final StartupStage stage;
        private volatile StageStatus status = StageStatus.PENDING;
        private volatile int percentage = 0;
        private volatile String detail = "";
        private volatile String errorMessage = "";
        private Instant startTime;
        private Instant endTime;
        
        public StageProgress(StartupStage stage) {
            this.stage = stage;
        }
        
        public void start() {
            this.status = StageStatus.IN_PROGRESS;
            this.startTime = Instant.now();
            this.percentage = 0;
        }
        
        public void complete() {
            this.status = StageStatus.COMPLETED;
            this.endTime = Instant.now();
            this.percentage = 100;
        }
        
        public void fail(String errorMessage) {
            this.status = StageStatus.FAILED;
            this.endTime = Instant.now();
            this.errorMessage = errorMessage;
        }
        
        public void rollback() {
            this.status = StageStatus.ROLLED_BACK;
        }
        
        public void updateProgress(int percentage, String detail) {
            this.percentage = Math.max(0, Math.min(100, percentage));
            this.detail = detail != null ? detail : "";
        }
        
        public long getDurationMs() {
            if (startTime == null) {
                return 0;
            }
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end).toMillis();
        }
    }
}