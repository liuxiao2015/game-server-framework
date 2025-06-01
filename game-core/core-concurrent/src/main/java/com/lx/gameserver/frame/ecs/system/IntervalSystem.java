/*
 * 文件名: IntervalSystem.java
 * 用途: 定时执行系统基类
 * 实现内容:
 *   - 定时执行系统
 *   - 可配置执行间隔
 *   - 支持一次性和重复执行
 *   - 时间累积处理
 * 技术选型:
 *   - 时间累积算法
 *   - 高精度时间处理
 *   - 可配置执行策略
 * 依赖关系:
 *   - 继承AbstractSystem
 *   - 提供定时任务系统基础
 *   - 被具体定时系统继承
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.system;

import com.lx.gameserver.frame.ecs.core.AbstractSystem;

/**
 * 定时执行系统
 * <p>
 * 按照指定间隔执行系统逻辑，支持一次性和重复执行。
 * 提供精确的时间控制和执行策略配置。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class IntervalSystem extends AbstractSystem {
    
    /**
     * 执行间隔（秒）
     */
    private float interval;
    
    /**
     * 累积时间
     */
    private float accumulator;
    
    /**
     * 是否只执行一次
     */
    private boolean runOnce;
    
    /**
     * 是否已经执行过（用于一次性执行）
     */
    private boolean hasRun;
    
    /**
     * 是否立即执行第一次
     */
    private boolean executeImmediately;
    
    /**
     * 执行次数
     */
    private long executionCount;
    
    /**
     * 最大执行次数（0表示无限制）
     */
    private long maxExecutions;
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param interval 执行间隔（秒）
     */
    protected IntervalSystem(String name, int priority, float interval) {
        this(name, priority, interval, false);
    }
    
    /**
     * 构造函数
     *
     * @param name 系统名称
     * @param priority 系统优先级
     * @param interval 执行间隔（秒）
     * @param runOnce 是否只执行一次
     */
    protected IntervalSystem(String name, int priority, float interval, boolean runOnce) {
        super(name, priority);
        this.interval = Math.max(0, interval);
        this.runOnce = runOnce;
        this.accumulator = 0;
        this.hasRun = false;
        this.executeImmediately = false;
        this.executionCount = 0;
        this.maxExecutions = 0;
    }
    
    @Override
    protected void onInitialize() {
        // 重置状态
        accumulator = 0;
        hasRun = false;
        executionCount = 0;
        
        // 如果需要立即执行，设置累积时间
        if (executeImmediately) {
            accumulator = interval;
        }
        
        // 调用子类初始化
        onSystemInitialize();
    }
    
    @Override
    protected void onUpdate(float deltaTime) {
        // 如果是一次性执行且已经执行过，则跳过
        if (runOnce && hasRun) {
            return;
        }
        
        // 如果达到最大执行次数，则跳过
        if (maxExecutions > 0 && executionCount >= maxExecutions) {
            return;
        }
        
        // 累积时间
        accumulator += deltaTime;
        
        // 检查是否到达执行时间
        if (accumulator >= interval) {
            // 执行系统逻辑
            onIntervalUpdate();
            
            // 更新执行状态
            executionCount++;
            hasRun = true;
            
            // 重置累积时间（保留超出的时间）
            if (!runOnce) {
                accumulator -= interval;
                // 防止时间累积过多导致连续执行
                if (accumulator >= interval) {
                    accumulator = 0;
                }
            }
        }
    }
    
    /**
     * 定时执行的系统逻辑
     */
    protected abstract void onIntervalUpdate();
    
    /**
     * 系统初始化回调（子类实现）
     */
    protected void onSystemInitialize() {
        // 默认空实现
    }
    
    /**
     * 设置执行间隔
     *
     * @param interval 执行间隔（秒）
     */
    public void setInterval(float interval) {
        this.interval = Math.max(0, interval);
    }
    
    /**
     * 获取执行间隔
     *
     * @return 执行间隔
     */
    public float getInterval() {
        return interval;
    }
    
    /**
     * 设置是否只执行一次
     *
     * @param runOnce 是否只执行一次
     */
    public void setRunOnce(boolean runOnce) {
        this.runOnce = runOnce;
    }
    
    /**
     * 检查是否只执行一次
     *
     * @return 如果只执行一次返回true
     */
    public boolean isRunOnce() {
        return runOnce;
    }
    
    /**
     * 检查是否已经执行过
     *
     * @return 如果已经执行过返回true
     */
    public boolean hasRun() {
        return hasRun;
    }
    
    /**
     * 设置是否立即执行第一次
     *
     * @param executeImmediately 是否立即执行
     */
    public void setExecuteImmediately(boolean executeImmediately) {
        this.executeImmediately = executeImmediately;
        if (executeImmediately && accumulator < interval) {
            accumulator = interval;
        }
    }
    
    /**
     * 检查是否立即执行第一次
     *
     * @return 如果立即执行返回true
     */
    public boolean isExecuteImmediately() {
        return executeImmediately;
    }
    
    /**
     * 获取执行次数
     *
     * @return 执行次数
     */
    public long getExecutionCount() {
        return executionCount;
    }
    
    /**
     * 设置最大执行次数
     *
     * @param maxExecutions 最大执行次数（0表示无限制）
     */
    public void setMaxExecutions(long maxExecutions) {
        this.maxExecutions = Math.max(0, maxExecutions);
    }
    
    /**
     * 获取最大执行次数
     *
     * @return 最大执行次数
     */
    public long getMaxExecutions() {
        return maxExecutions;
    }
    
    /**
     * 获取当前累积时间
     *
     * @return 累积时间
     */
    public float getAccumulatedTime() {
        return accumulator;
    }
    
    /**
     * 获取下次执行剩余时间
     *
     * @return 剩余时间
     */
    public float getRemainingTime() {
        return Math.max(0, interval - accumulator);
    }
    
    /**
     * 获取执行进度（0-1之间）
     *
     * @return 执行进度
     */
    public float getProgress() {
        return interval > 0 ? Math.min(1.0f, accumulator / interval) : 1.0f;
    }
    
    /**
     * 重置系统状态
     */
    public void reset() {
        accumulator = 0;
        hasRun = false;
        executionCount = 0;
    }
    
    /**
     * 强制执行一次
     */
    public void forceExecute() {
        if (!isEnabled()) {
            return;
        }
        
        onIntervalUpdate();
        executionCount++;
        hasRun = true;
    }
    
    /**
     * 跳过当前周期
     */
    public void skipCycle() {
        accumulator = 0;
    }
    
    @Override
    public String toString() {
        return super.toString() + String.format(
            "{interval=%.2fs, progress=%.1f%%, executions=%d, remaining=%.2fs}",
            interval, getProgress() * 100, executionCount, getRemainingTime()
        );
    }
}