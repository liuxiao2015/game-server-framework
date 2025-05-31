/*
 * 文件名: Activity.java
 * 用途: 活动基类（抽象类）
 * 实现内容:
 *   - 定义活动的基本属性和行为
 *   - 实现活动生命周期管理
 *   - 提供活动状态管理
 *   - 实现模板方法模式
 * 技术选型:
 *   - 抽象类设计
 *   - 模板方法模式
 *   - 枚举状态管理
 *   - JSON配置支持
 * 依赖关系:
 *   - 实现ActivityLifecycle接口
 *   - 被具体活动类继承
 *   - 被活动管理器使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.core;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 活动基类
 * <p>
 * 定义了活动系统的核心抽象，包含活动的基本属性、状态管理、
 * 生命周期控制等。采用模板方法模式，为具体活动实现提供框架。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Data
public abstract class Activity implements ActivityLifecycle {
    
    /** 活动ID */
    protected Long activityId;
    
    /** 活动名称 */
    protected String activityName;
    
    /** 活动描述 */
    protected String description;
    
    /** 活动类型 */
    protected ActivityType activityType;
    
    /** 开始时间（时间戳） */
    protected Long startTime;
    
    /** 结束时间（时间戳） */
    protected Long endTime;
    
    /** 活动状态 */
    protected final AtomicReference<ActivityStatus> status = new AtomicReference<>(ActivityStatus.NOT_STARTED);
    
    /** 生命周期状态 */
    protected final AtomicReference<LifecycleState> lifecycleState = new AtomicReference<>(LifecycleState.CREATED);
    
    /** 活动配置数据 */
    protected final Map<String, Object> configData = new ConcurrentHashMap<>();
    
    /** 活动创建时间 */
    protected Long createTime;
    
    /** 活动最后更新时间 */
    protected Long lastUpdateTime;
    
    /** 活动优先级 */
    protected Integer priority = 0;
    
    /** 是否可重复参与 */
    protected Boolean repeatable = false;
    
    /** 最大参与人数 */
    protected Integer maxParticipants;
    
    /** 当前参与人数 */
    protected Integer currentParticipants = 0;
    
    /**
     * 活动状态枚举
     */
    public enum ActivityStatus {
        /** 未开始 */
        NOT_STARTED("not_started", "未开始"),
        
        /** 进行中 */
        ACTIVE("active", "进行中"),
        
        /** 已暂停 */
        PAUSED("paused", "已暂停"),
        
        /** 已结束 */
        ENDED("ended", "已结束"),
        
        /** 已取消 */
        CANCELLED("cancelled", "已取消");
        
        private final String code;
        private final String description;
        
        ActivityStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
        
        public boolean isActive() { return this == ACTIVE; }
        public boolean isFinished() { return this == ENDED || this == CANCELLED; }
    }
    
    /**
     * 构造函数
     */
    public Activity() {
        this.createTime = System.currentTimeMillis();
        this.lastUpdateTime = this.createTime;
    }
    
    /**
     * 构造函数
     *
     * @param activityId   活动ID
     * @param activityName 活动名称
     * @param activityType 活动类型
     */
    public Activity(Long activityId, String activityName, ActivityType activityType) {
        this();
        this.activityId = activityId;
        this.activityName = activityName;
        this.activityType = activityType;
    }
    
    // ===== 生命周期实现 =====
    
    @Override
    public final void initialize(ActivityContext context) throws Exception {
        log.info("初始化活动: {} (ID: {})", activityName, activityId);
        
        try {
            // 设置生命周期状态
            lifecycleState.set(LifecycleState.INITIALIZED);
            
            // 调用子类初始化逻辑
            doInitialize(context);
            
            // 更新时间
            updateLastModifyTime();
            
            log.info("活动初始化完成: {} (ID: {})", activityName, activityId);
        } catch (Exception e) {
            lifecycleState.set(LifecycleState.ERROR);
            log.error("活动初始化失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public final void start(ActivityContext context) throws Exception {
        log.info("开始活动: {} (ID: {})", activityName, activityId);
        
        try {
            // 检查活动是否可以开始
            checkCanStart();
            
            // 设置状态
            status.set(ActivityStatus.ACTIVE);
            lifecycleState.set(LifecycleState.RUNNING);
            
            // 调用子类开始逻辑
            doStart(context);
            
            // 触发开始事件
            onStart(context);
            
            // 更新时间
            updateLastModifyTime();
            
            log.info("活动开始完成: {} (ID: {})", activityName, activityId);
        } catch (Exception e) {
            status.set(ActivityStatus.CANCELLED);
            lifecycleState.set(LifecycleState.ERROR);
            log.error("活动开始失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public final void update(ActivityContext context, long deltaTime) throws Exception {
        try {
            // 检查活动状态
            if (!status.get().isActive()) {
                return;
            }
            
            // 检查时间有效性
            checkTimeValidity();
            
            // 调用子类更新逻辑
            doUpdate(context, deltaTime);
            
            // 更新时间
            updateLastModifyTime();
            
        } catch (Exception e) {
            log.error("活动更新失败: {} (ID: {})", activityName, activityId, e);
            onError(context, e);
            throw e;
        }
    }
    
    @Override
    public final void end(ActivityContext context, String reason) throws Exception {
        log.info("结束活动: {} (ID: {}), 原因: {}", activityName, activityId, reason);
        
        try {
            // 设置状态
            if ("cancelled".equals(reason)) {
                status.set(ActivityStatus.CANCELLED);
            } else {
                status.set(ActivityStatus.ENDED);
            }
            lifecycleState.set(LifecycleState.ENDED);
            
            // 调用子类结束逻辑
            doEnd(context, reason);
            
            // 触发结束事件
            onEnd(context, reason);
            
            // 更新时间
            updateLastModifyTime();
            
            log.info("活动结束完成: {} (ID: {})", activityName, activityId);
        } catch (Exception e) {
            lifecycleState.set(LifecycleState.ERROR);
            log.error("活动结束失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public void reset(ActivityContext context) throws Exception {
        log.info("重置活动: {} (ID: {})", activityName, activityId);
        
        try {
            // 重置状态
            status.set(ActivityStatus.NOT_STARTED);
            lifecycleState.set(LifecycleState.INITIALIZED);
            currentParticipants = 0;
            
            // 调用子类重置逻辑
            doReset(context);
            
            // 更新时间
            updateLastModifyTime();
            
            log.info("活动重置完成: {} (ID: {})", activityName, activityId);
        } catch (Exception e) {
            log.error("活动重置失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public void destroy(ActivityContext context) throws Exception {
        log.info("销毁活动: {} (ID: {})", activityName, activityId);
        
        try {
            // 设置状态
            lifecycleState.set(LifecycleState.DESTROYED);
            
            // 调用子类销毁逻辑
            doDestroy(context);
            
            // 清理数据
            configData.clear();
            
            log.info("活动销毁完成: {} (ID: {})", activityName, activityId);
        } catch (Exception e) {
            log.error("活动销毁失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public void pause(ActivityContext context) throws Exception {
        log.info("暂停活动: {} (ID: {})", activityName, activityId);
        
        try {
            status.set(ActivityStatus.PAUSED);
            lifecycleState.set(LifecycleState.PAUSED);
            
            doPause(context);
            updateLastModifyTime();
            
        } catch (Exception e) {
            log.error("活动暂停失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public void resume(ActivityContext context) throws Exception {
        log.info("恢复活动: {} (ID: {})", activityName, activityId);
        
        try {
            status.set(ActivityStatus.ACTIVE);
            lifecycleState.set(LifecycleState.RUNNING);
            
            doResume(context);
            updateLastModifyTime();
            
        } catch (Exception e) {
            log.error("活动恢复失败: {} (ID: {})", activityName, activityId, e);
            throw e;
        }
    }
    
    @Override
    public LifecycleState getLifecycleState() {
        return lifecycleState.get();
    }
    
    // ===== 模板方法（子类实现） =====
    
    /**
     * 子类初始化逻辑
     *
     * @param context 活动上下文
     * @throws Exception 初始化异常
     */
    protected abstract void doInitialize(ActivityContext context) throws Exception;
    
    /**
     * 子类开始逻辑
     *
     * @param context 活动上下文
     * @throws Exception 开始异常
     */
    protected abstract void doStart(ActivityContext context) throws Exception;
    
    /**
     * 子类更新逻辑
     *
     * @param context   活动上下文
     * @param deltaTime 时间间隔
     * @throws Exception 更新异常
     */
    protected abstract void doUpdate(ActivityContext context, long deltaTime) throws Exception;
    
    /**
     * 子类结束逻辑
     *
     * @param context 活动上下文
     * @param reason  结束原因
     * @throws Exception 结束异常
     */
    protected abstract void doEnd(ActivityContext context, String reason) throws Exception;
    
    /**
     * 子类重置逻辑
     *
     * @param context 活动上下文
     * @throws Exception 重置异常
     */
    protected void doReset(ActivityContext context) throws Exception {
        // 默认空实现
    }
    
    /**
     * 子类销毁逻辑
     *
     * @param context 活动上下文
     * @throws Exception 销毁异常
     */
    protected void doDestroy(ActivityContext context) throws Exception {
        // 默认空实现
    }
    
    /**
     * 子类暂停逻辑
     *
     * @param context 活动上下文
     * @throws Exception 暂停异常
     */
    protected void doPause(ActivityContext context) throws Exception {
        // 默认空实现
    }
    
    /**
     * 子类恢复逻辑
     *
     * @param context 活动上下文
     * @throws Exception 恢复异常
     */
    protected void doResume(ActivityContext context) throws Exception {
        // 默认空实现
    }
    
    // ===== 生命周期事件钩子 =====
    
    /**
     * 活动开始事件
     *
     * @param context 活动上下文
     */
    protected void onStart(ActivityContext context) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 活动结束事件
     *
     * @param context 活动上下文
     * @param reason  结束原因
     */
    protected void onEnd(ActivityContext context, String reason) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 活动错误事件
     *
     * @param context 活动上下文
     * @param error   错误信息
     */
    protected void onError(ActivityContext context, Throwable error) {
        log.error("活动执行出错: {} (ID: {})", activityName, activityId, error);
        lifecycleState.set(LifecycleState.ERROR);
    }
    
    // ===== 工具方法 =====
    
    /**
     * 检查活动是否可以开始
     *
     * @throws Exception 检查异常
     */
    protected void checkCanStart() throws Exception {
        long currentTime = System.currentTimeMillis();
        
        if (startTime != null && currentTime < startTime) {
            throw new IllegalStateException("活动尚未到开始时间");
        }
        
        if (endTime != null && currentTime > endTime) {
            throw new IllegalStateException("活动已过结束时间");
        }
        
        if (lifecycleState.get() != LifecycleState.INITIALIZED) {
            throw new IllegalStateException("活动未正确初始化");
        }
    }
    
    /**
     * 检查时间有效性
     */
    protected void checkTimeValidity() {
        long currentTime = System.currentTimeMillis();
        
        if (endTime != null && currentTime > endTime) {
            log.info("活动时间已到期，自动结束: {} (ID: {})", activityName, activityId);
            status.set(ActivityStatus.ENDED);
        }
    }
    
    /**
     * 更新最后修改时间
     */
    protected void updateLastModifyTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 设置配置数据
     *
     * @param key   配置键
     * @param value 配置值
     */
    public void setConfig(String key, Object value) {
        configData.put(key, value);
        updateLastModifyTime();
    }
    
    /**
     * 获取配置数据
     *
     * @param key 配置键
     * @return 配置值
     */
    public Object getConfig(String key) {
        return configData.get(key);
    }
    
    /**
     * 获取配置数据（指定类型）
     *
     * @param key  配置键
     * @param type 数据类型
     * @param <T>  泛型类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, Class<T> type) {
        Object value = configData.get(key);
        if (value == null) {
            return null;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("配置数据类型转换失败, key: {}, expectedType: {}, actualType: {}", 
                    key, type.getSimpleName(), value.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * 获取当前活动状态
     *
     * @return 活动状态
     */
    public ActivityStatus getStatus() {
        return status.get();
    }
    
    /**
     * 检查活动是否正在运行
     *
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return status.get().isActive() && lifecycleState.get().isActive();
    }
    
    /**
     * 检查活动是否已结束
     *
     * @return 是否已结束
     */
    public boolean isFinished() {
        return status.get().isFinished() || lifecycleState.get().isTerminated();
    }
    
    /**
     * 增加参与人数
     *
     * @return 当前参与人数
     */
    public synchronized int incrementParticipants() {
        if (maxParticipants != null && currentParticipants >= maxParticipants) {
            throw new IllegalStateException("活动参与人数已达上限");
        }
        return ++currentParticipants;
    }
    
    /**
     * 减少参与人数
     *
     * @return 当前参与人数
     */
    public synchronized int decrementParticipants() {
        if (currentParticipants > 0) {
            currentParticipants--;
        }
        return currentParticipants;
    }
}