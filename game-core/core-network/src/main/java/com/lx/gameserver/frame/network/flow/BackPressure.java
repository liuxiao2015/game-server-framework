/*
 * 文件名: BackPressure.java
 * 用途: 背压处理器
 * 实现内容:
 *   - 缓冲区监控和水位管理
 *   - 动态调整和自适应控制
 *   - 拥塞控制和流量调节
 *   - 降级策略和告警机制
 *   - 背压统计和监控
 * 技术选型:
 *   - 水位线检测机制
 *   - 动态调整算法
 *   - 拥塞窗口控制
 * 依赖关系:
 *   - 被Connection使用
 *   - 与TrafficShaper协作
 *   - 支持多级降级策略
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 背压处理器
 * <p>
 * 监控系统负载和缓冲区状态，动态调整处理速度和策略，
 * 防止系统过载和资源耗尽。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class BackPressure {

    private static final Logger logger = LoggerFactory.getLogger(BackPressure.class);

    // 背压状态
    public enum PressureState {
        /** 正常状态 */
        NORMAL("正常", 0),
        /** 轻微压力 */
        LIGHT("轻微压力", 1),
        /** 中等压力 */
        MODERATE("中等压力", 2),
        /** 高压力 */
        HIGH("高压力", 3),
        /** 严重压力 */
        CRITICAL("严重压力", 4);

        private final String description;
        private final int level;

        PressureState(String description, int level) {
            this.description = description;
            this.level = level;
        }

        public String getDescription() { return description; }
        public int getLevel() { return level; }
    }

    // 降级策略
    public enum DegradationStrategy {
        /** 无降级 */
        NONE,
        /** 延迟处理 */
        DELAY,
        /** 丢弃低优先级 */
        DROP_LOW_PRIORITY,
        /** 丢弃部分请求 */
        DROP_PARTIAL,
        /** 拒绝新请求 */
        REJECT_NEW
    }

    // 配置参数
    private final BackPressureConfig config;
    
    // 状态管理
    private final AtomicReference<PressureState> currentState = new AtomicReference<>(PressureState.NORMAL);
    private final AtomicReference<DegradationStrategy> currentStrategy = new AtomicReference<>(DegradationStrategy.NONE);
    
    // 监控指标
    private final AtomicLong bufferSize = new AtomicLong(0);
    private final AtomicLong maxBufferSize = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong droppedRequests = new AtomicLong(0);
    private final AtomicLong delayedRequests = new AtomicLong(0);
    
    // 事件监听器
    private volatile Consumer<PressureStateChangeEvent> stateChangeListener;
    private volatile Consumer<DegradationEvent> degradationListener;

    /**
     * 背压配置
     */
    public static class BackPressureConfig {
        private final long lowWaterMark;     // 低水位线
        private final long highWaterMark;    // 高水位线
        private final long criticalMark;     // 临界水位线
        private final long checkInterval;    // 检查间隔（毫秒）
        private final boolean autoAdjust;    // 是否自动调整
        private final double adaptiveFactor; // 自适应因子

        public BackPressureConfig(long lowWaterMark, long highWaterMark, long criticalMark,
                                long checkInterval, boolean autoAdjust, double adaptiveFactor) {
            this.lowWaterMark = lowWaterMark;
            this.highWaterMark = highWaterMark;
            this.criticalMark = criticalMark;
            this.checkInterval = checkInterval;
            this.autoAdjust = autoAdjust;
            this.adaptiveFactor = adaptiveFactor;
        }

        public long getLowWaterMark() { return lowWaterMark; }
        public long getHighWaterMark() { return highWaterMark; }
        public long getCriticalMark() { return criticalMark; }
        public long getCheckInterval() { return checkInterval; }
        public boolean isAutoAdjust() { return autoAdjust; }
        public double getAdaptiveFactor() { return adaptiveFactor; }
    }

    /**
     * 状态变化事件
     */
    public static class PressureStateChangeEvent {
        private final PressureState oldState;
        private final PressureState newState;
        private final long timestamp;
        private final String reason;

        public PressureStateChangeEvent(PressureState oldState, PressureState newState, String reason) {
            this.oldState = oldState;
            this.newState = newState;
            this.timestamp = System.currentTimeMillis();
            this.reason = reason;
        }

        public PressureState getOldState() { return oldState; }
        public PressureState getNewState() { return newState; }
        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
    }

    /**
     * 降级事件
     */
    public static class DegradationEvent {
        private final DegradationStrategy strategy;
        private final String target;
        private final long timestamp;
        private final String reason;

        public DegradationEvent(DegradationStrategy strategy, String target, String reason) {
            this.strategy = strategy;
            this.target = target;
            this.timestamp = System.currentTimeMillis();
            this.reason = reason;
        }

        public DegradationStrategy getStrategy() { return strategy; }
        public String getTarget() { return target; }
        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
    }

    /**
     * 处理结果
     */
    public static class ProcessResult {
        private final boolean allowed;
        private final DegradationStrategy appliedStrategy;
        private final long suggestedDelay;
        private final String reason;

        public ProcessResult(boolean allowed, DegradationStrategy appliedStrategy, 
                           long suggestedDelay, String reason) {
            this.allowed = allowed;
            this.appliedStrategy = appliedStrategy;
            this.suggestedDelay = suggestedDelay;
            this.reason = reason;
        }

        public boolean isAllowed() { return allowed; }
        public DegradationStrategy getAppliedStrategy() { return appliedStrategy; }
        public long getSuggestedDelay() { return suggestedDelay; }
        public String getReason() { return reason; }
    }

    /**
     * 构造函数
     */
    public BackPressure(BackPressureConfig config) {
        this.config = config;
        this.maxBufferSize.set(config.getCriticalMark());
        
        logger.info("背压处理器初始化，低水位: {}, 高水位: {}, 临界: {}", 
                   config.getLowWaterMark(), config.getHighWaterMark(), config.getCriticalMark());
    }

    /**
     * 检查请求是否可以处理
     */
    public ProcessResult checkRequest(String requestId, int priority) {
        totalRequests.incrementAndGet();
        
        PressureState state = currentState.get();
        DegradationStrategy strategy = determineStrategy(state, priority);
        
        switch (strategy) {
            case NONE:
                return new ProcessResult(true, strategy, 0, "正常处理");
                
            case DELAY:
                delayedRequests.incrementAndGet();
                long delay = calculateDelay(state);
                return new ProcessResult(true, strategy, delay, "延迟处理");
                
            case DROP_LOW_PRIORITY:
                if (priority <= 1) { // 低优先级
                    droppedRequests.incrementAndGet();
                    fireDegradationEvent(strategy, requestId, "丢弃低优先级请求");
                    return new ProcessResult(false, strategy, 0, "丢弃低优先级请求");
                }
                return new ProcessResult(true, strategy, 0, "保留高优先级请求");
                
            case DROP_PARTIAL:
                // 随机丢弃部分请求
                if (Math.random() < getDropRate(state)) {
                    droppedRequests.incrementAndGet();
                    fireDegradationEvent(strategy, requestId, "随机丢弃请求");
                    return new ProcessResult(false, strategy, 0, "随机丢弃请求");
                }
                return new ProcessResult(true, strategy, 0, "随机保留请求");
                
            case REJECT_NEW:
                droppedRequests.incrementAndGet();
                fireDegradationEvent(strategy, requestId, "拒绝新请求");
                return new ProcessResult(false, strategy, 0, "拒绝新请求");
                
            default:
                return new ProcessResult(true, DegradationStrategy.NONE, 0, "默认处理");
        }
    }

    /**
     * 更新缓冲区大小
     */
    public void updateBufferSize(long newSize) {
        long oldSize = bufferSize.getAndSet(newSize);
        
        // 更新最大缓冲区大小
        maxBufferSize.updateAndGet(current -> Math.max(current, newSize));
        
        // 检查状态变化
        checkStateChange(oldSize, newSize);
    }

    /**
     * 检查状态变化
     */
    private void checkStateChange(long oldSize, long newSize) {
        PressureState oldState = currentState.get();
        PressureState newState = calculateState(newSize);
        
        if (oldState != newState) {
            currentState.set(newState);
            currentStrategy.set(getDefaultStrategy(newState));
            
            String reason = String.format("缓冲区大小变化: %d -> %d", oldSize, newSize);
            fireStateChangeEvent(oldState, newState, reason);
            
            logger.info("背压状态变化: {} -> {}, 原因: {}", 
                       oldState.getDescription(), newState.getDescription(), reason);
        }
    }

    /**
     * 计算当前状态
     */
    private PressureState calculateState(long currentSize) {
        double utilization = (double) currentSize / config.getCriticalMark();
        
        if (currentSize >= config.getCriticalMark()) {
            return PressureState.CRITICAL;
        } else if (currentSize >= config.getHighWaterMark()) {
            return PressureState.HIGH;
        } else if (utilization > 0.6) {
            return PressureState.MODERATE;
        } else if (utilization > 0.3) {
            return PressureState.LIGHT;
        } else {
            return PressureState.NORMAL;
        }
    }

    /**
     * 确定降级策略
     */
    private DegradationStrategy determineStrategy(PressureState state, int priority) {
        switch (state) {
            case NORMAL:
            case LIGHT:
                return DegradationStrategy.NONE;
                
            case MODERATE:
                return DegradationStrategy.DELAY;
                
            case HIGH:
                return priority <= 1 ? DegradationStrategy.DROP_LOW_PRIORITY : DegradationStrategy.DELAY;
                
            case CRITICAL:
                return DegradationStrategy.REJECT_NEW;
                
            default:
                return DegradationStrategy.NONE;
        }
    }

    /**
     * 获取默认策略
     */
    private DegradationStrategy getDefaultStrategy(PressureState state) {
        return determineStrategy(state, 2); // 使用中等优先级
    }

    /**
     * 计算延迟时间
     */
    private long calculateDelay(PressureState state) {
        switch (state) {
            case MODERATE:
                return 10; // 10ms
            case HIGH:
                return 50; // 50ms
            case CRITICAL:
                return 200; // 200ms
            default:
                return 0;
        }
    }

    /**
     * 获取丢弃率
     */
    private double getDropRate(PressureState state) {
        switch (state) {
            case MODERATE:
                return 0.1; // 10%
            case HIGH:
                return 0.3; // 30%
            case CRITICAL:
                return 0.7; // 70%
            default:
                return 0.0;
        }
    }

    /**
     * 触发状态变化事件
     */
    private void fireStateChangeEvent(PressureState oldState, PressureState newState, String reason) {
        Consumer<PressureStateChangeEvent> listener = stateChangeListener;
        if (listener != null) {
            try {
                listener.accept(new PressureStateChangeEvent(oldState, newState, reason));
            } catch (Exception e) {
                logger.warn("处理状态变化事件失败", e);
            }
        }
    }

    /**
     * 触发降级事件
     */
    private void fireDegradationEvent(DegradationStrategy strategy, String target, String reason) {
        Consumer<DegradationEvent> listener = degradationListener;
        if (listener != null) {
            try {
                listener.accept(new DegradationEvent(strategy, target, reason));
            } catch (Exception e) {
                logger.warn("处理降级事件失败", e);
            }
        }
    }

    /**
     * 设置状态变化监听器
     */
    public void setStateChangeListener(Consumer<PressureStateChangeEvent> listener) {
        this.stateChangeListener = listener;
    }

    /**
     * 设置降级事件监听器
     */
    public void setDegradationListener(Consumer<DegradationEvent> listener) {
        this.degradationListener = listener;
    }

    /**
     * 获取统计信息
     */
    public BackPressureStatistics getStatistics() {
        return new BackPressureStatistics(
            currentState.get(),
            currentStrategy.get(),
            bufferSize.get(),
            maxBufferSize.get(),
            totalRequests.get(),
            droppedRequests.get(),
            delayedRequests.get(),
            config
        );
    }

    /**
     * 背压统计信息
     */
    public static class BackPressureStatistics {
        private final PressureState currentState;
        private final DegradationStrategy currentStrategy;
        private final long currentBufferSize;
        private final long maxBufferSize;
        private final long totalRequests;
        private final long droppedRequests;
        private final long delayedRequests;
        private final BackPressureConfig config;

        public BackPressureStatistics(PressureState currentState, DegradationStrategy currentStrategy,
                                    long currentBufferSize, long maxBufferSize,
                                    long totalRequests, long droppedRequests, long delayedRequests,
                                    BackPressureConfig config) {
            this.currentState = currentState;
            this.currentStrategy = currentStrategy;
            this.currentBufferSize = currentBufferSize;
            this.maxBufferSize = maxBufferSize;
            this.totalRequests = totalRequests;
            this.droppedRequests = droppedRequests;
            this.delayedRequests = delayedRequests;
            this.config = config;
        }

        public PressureState getCurrentState() { return currentState; }
        public DegradationStrategy getCurrentStrategy() { return currentStrategy; }
        public long getCurrentBufferSize() { return currentBufferSize; }
        public long getMaxBufferSize() { return maxBufferSize; }
        public long getTotalRequests() { return totalRequests; }
        public long getDroppedRequests() { return droppedRequests; }
        public long getDelayedRequests() { return delayedRequests; }
        public BackPressureConfig getConfig() { return config; }
        
        public double getDropRate() {
            return totalRequests > 0 ? (double) droppedRequests / totalRequests : 0.0;
        }
        
        public double getDelayRate() {
            return totalRequests > 0 ? (double) delayedRequests / totalRequests : 0.0;
        }
        
        public double getBufferUtilization() {
            return config.getCriticalMark() > 0 ? (double) currentBufferSize / config.getCriticalMark() : 0.0;
        }

        @Override
        public String toString() {
            return String.format("BackPressureStats{state=%s, strategy=%s, buffer=%d/%d(%.1f%%), " +
                               "requests=%d, dropped=%d(%.1f%%), delayed=%d(%.1f%%)}",
                               currentState, currentStrategy, currentBufferSize, config.getCriticalMark(),
                               getBufferUtilization() * 100, totalRequests, droppedRequests,
                               getDropRate() * 100, delayedRequests, getDelayRate() * 100);
        }
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalRequests.set(0);
        droppedRequests.set(0);
        delayedRequests.set(0);
        maxBufferSize.set(bufferSize.get());
        logger.debug("背压统计信息已重置");
    }

    /**
     * 获取当前状态
     */
    public PressureState getCurrentState() {
        return currentState.get();
    }

    /**
     * 获取当前策略
     */
    public DegradationStrategy getCurrentStrategy() {
        return currentStrategy.get();
    }

    /**
     * 获取配置
     */
    public BackPressureConfig getConfig() {
        return config;
    }
}