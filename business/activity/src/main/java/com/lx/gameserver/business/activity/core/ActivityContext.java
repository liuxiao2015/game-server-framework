/*
 * 文件名: ActivityContext.java
 * 用途: 活动上下文类
 * 实现内容:
 *   - 维护活动执行的上下文信息
 *   - 包含当前玩家信息和活动参数
 *   - 管理活动进度数据和奖励信息
 *   - 提供扩展数据存储和访问
 * 技术选型:
 *   - 使用Map存储动态数据
 *   - 线程安全的数据访问
 *   - 支持数据类型转换
 * 依赖关系:
 *   - 被Activity基类使用
 *   - 被活动模板使用
 *   - 被进度追踪器使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动上下文
 * <p>
 * 活动执行过程中的上下文信息容器，包含玩家信息、活动参数、
 * 进度数据、奖励信息等。提供类型安全的数据访问和扩展支持。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Data
public class ActivityContext {
    
    /** 活动ID */
    private Long activityId;
    
    /** 玩家ID */
    private Long playerId;
    
    /** 玩家名称 */
    private String playerName;
    
    /** 玩家等级 */
    private Integer playerLevel;
    
    /** 玩家VIP等级 */
    private Integer vipLevel;
    
    /** 活动类型 */
    private ActivityType activityType;
    
    /** 活动参数 */
    private final Map<String, Object> parameters = new ConcurrentHashMap<>();
    
    /** 活动进度数据 */
    private final Map<String, Object> progressData = new ConcurrentHashMap<>();
    
    /** 奖励信息 */
    private final Map<String, Object> rewardInfo = new ConcurrentHashMap<>();
    
    /** 扩展数据存储 */
    private final Map<String, Object> extensions = new ConcurrentHashMap<>();
    
    /** 上下文创建时间 */
    private Long createTime;
    
    /** 上下文最后更新时间 */
    private Long updateTime;
    
    /**
     * 默认构造函数
     */
    public ActivityContext() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
    }
    
    /**
     * 构造函数
     *
     * @param activityId   活动ID
     * @param playerId     玩家ID
     * @param activityType 活动类型
     */
    public ActivityContext(Long activityId, Long playerId, ActivityType activityType) {
        this();
        this.activityId = activityId;
        this.playerId = playerId;
        this.activityType = activityType;
    }
    
    // ===== 参数访问方法 =====
    
    /**
     * 设置参数
     *
     * @param key   参数键
     * @param value 参数值
     */
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
        updateModifyTime();
    }
    
    /**
     * 获取参数
     *
     * @param key 参数键
     * @return 参数值
     */
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    /**
     * 获取参数（指定类型）
     *
     * @param key  参数键
     * @param type 参数类型
     * @param <T>  泛型类型
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("参数类型转换失败, key: {}, expectedType: {}, actualType: {}", 
                    key, type.getSimpleName(), value.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * 获取参数（带默认值）
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @param <T>          泛型类型
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("参数类型转换失败, key: {}, 返回默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }
    
    // ===== 进度数据访问方法 =====
    
    /**
     * 设置进度数据
     *
     * @param key   进度键
     * @param value 进度值
     */
    public void setProgress(String key, Object value) {
        progressData.put(key, value);
        updateModifyTime();
    }
    
    /**
     * 获取进度数据
     *
     * @param key 进度键
     * @return 进度值
     */
    public Object getProgress(String key) {
        return progressData.get(key);
    }
    
    /**
     * 获取进度数据（指定类型）
     *
     * @param key  进度键
     * @param type 数据类型
     * @param <T>  泛型类型
     * @return 进度值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProgress(String key, Class<T> type) {
        Object value = progressData.get(key);
        if (value == null) {
            return null;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("进度数据类型转换失败, key: {}, expectedType: {}, actualType: {}", 
                    key, type.getSimpleName(), value.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * 获取进度数据（带默认值）
     *
     * @param key          进度键
     * @param defaultValue 默认值
     * @param <T>          泛型类型
     * @return 进度值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProgress(String key, T defaultValue) {
        Object value = progressData.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("进度数据类型转换失败, key: {}, 返回默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }
    
    // ===== 奖励信息访问方法 =====
    
    /**
     * 设置奖励信息
     *
     * @param key   奖励键
     * @param value 奖励值
     */
    public void setReward(String key, Object value) {
        rewardInfo.put(key, value);
        updateModifyTime();
    }
    
    /**
     * 获取奖励信息
     *
     * @param key 奖励键
     * @return 奖励值
     */
    public Object getReward(String key) {
        return rewardInfo.get(key);
    }
    
    /**
     * 获取奖励信息（指定类型）
     *
     * @param key  奖励键
     * @param type 数据类型
     * @param <T>  泛型类型
     * @return 奖励值
     */
    @SuppressWarnings("unchecked")
    public <T> T getReward(String key, Class<T> type) {
        Object value = rewardInfo.get(key);
        if (value == null) {
            return null;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("奖励信息类型转换失败, key: {}, expectedType: {}, actualType: {}", 
                    key, type.getSimpleName(), value.getClass().getSimpleName());
            return null;
        }
    }
    
    // ===== 扩展数据访问方法 =====
    
    /**
     * 设置扩展数据
     *
     * @param key   扩展键
     * @param value 扩展值
     */
    public void setExtension(String key, Object value) {
        extensions.put(key, value);
        updateModifyTime();
    }
    
    /**
     * 获取扩展数据
     *
     * @param key 扩展键
     * @return 扩展值
     */
    public Object getExtension(String key) {
        return extensions.get(key);
    }
    
    /**
     * 获取扩展数据（指定类型）
     *
     * @param key  扩展键
     * @param type 数据类型
     * @param <T>  泛型类型
     * @return 扩展值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, Class<T> type) {
        Object value = extensions.get(key);
        if (value == null) {
            return null;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            log.warn("扩展数据类型转换失败, key: {}, expectedType: {}, actualType: {}", 
                    key, type.getSimpleName(), value.getClass().getSimpleName());
            return null;
        }
    }
    
    // ===== 工具方法 =====
    
    /**
     * 更新修改时间
     */
    private void updateModifyTime() {
        this.updateTime = System.currentTimeMillis();
    }
    
    /**
     * 清空所有数据
     */
    public void clear() {
        parameters.clear();
        progressData.clear();
        rewardInfo.clear();
        extensions.clear();
        updateModifyTime();
    }
    
    /**
     * 克隆上下文
     *
     * @return 新的上下文实例
     */
    public ActivityContext clone() {
        ActivityContext context = new ActivityContext(activityId, playerId, activityType);
        context.playerName = this.playerName;
        context.playerLevel = this.playerLevel;
        context.vipLevel = this.vipLevel;
        context.createTime = this.createTime;
        context.updateTime = this.updateTime;
        
        context.parameters.putAll(this.parameters);
        context.progressData.putAll(this.progressData);
        context.rewardInfo.putAll(this.rewardInfo);
        context.extensions.putAll(this.extensions);
        
        return context;
    }
}