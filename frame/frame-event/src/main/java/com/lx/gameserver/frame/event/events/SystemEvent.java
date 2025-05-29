/*
 * 文件名: SystemEvent.java
 * 用途: 系统事件
 * 实现内容:
 *   - 定义系统启动、关闭、维护等事件
 *   - 提供系统状态变化的事件通知
 *   - 支持系统配置和状态的事件传递
 * 技术选型:
 *   - 继承GameEvent基类
 *   - 静态内部类设计
 *   - 不可变对象模式
 * 依赖关系:
 *   - 继承GameEvent
 *   - 被系统管理模块使用
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.events;

import com.lx.gameserver.frame.event.core.EventPriority;
import com.lx.gameserver.frame.event.core.GameEvent;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 系统事件
 * <p>
 * 定义系统级别的各种事件，包括启动、关闭、维护等。
 * 使用静态内部类组织不同类型的系统事件。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class SystemEvent extends GameEvent {
    
    /** 服务器ID */
    private final String serverId;
    
    /**
     * 构造函数
     *
     * @param serverId 服务器ID
     * @param source   事件来源
     * @param priority 事件优先级
     */
    protected SystemEvent(String serverId, String source, EventPriority priority) {
        super(source, priority);
        this.serverId = Objects.requireNonNull(serverId, "服务器ID不能为空");
    }
    
    /**
     * 获取服务器ID
     *
     * @return 服务器ID
     */
    public String getServerId() {
        return serverId;
    }
    
    @Override
    public boolean isValid() {
        return super.isValid() && serverId != null && !serverId.isEmpty();
    }
    
    /**
     * 系统启动事件
     */
    public static class SystemStartupEvent extends SystemEvent {
        
        /** 启动时间 */
        private final LocalDateTime startupTime;
        
        /** 启动模式 */
        private final String startupMode;
        
        /** 系统版本 */
        private final String version;
        
        /** 启动配置 */
        private final Map<String, Object> config;
        
        /**
         * 构造函数
         *
         * @param serverId    服务器ID
         * @param startupMode 启动模式
         * @param version     系统版本
         * @param config      启动配置
         * @param source      事件来源
         */
        public SystemStartupEvent(String serverId, String startupMode, String version, 
                                 Map<String, Object> config, String source) {
            super(serverId, source, EventPriority.HIGHEST);
            this.startupTime = LocalDateTime.now();
            this.startupMode = Objects.requireNonNull(startupMode, "启动模式不能为空");
            this.version = Objects.requireNonNull(version, "系统版本不能为空");
            this.config = config != null ? Map.copyOf(config) : Map.of();
        }
        
        public LocalDateTime getStartupTime() {
            return startupTime;
        }
        
        public String getStartupMode() {
            return startupMode;
        }
        
        public String getSystemVersion() {
            return version;
        }
        
        public Map<String, Object> getConfig() {
            return config;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "serverId", getServerId(),
                    "startupTime", startupTime.toString(),
                    "startupMode", startupMode,
                    "version", version,
                    "config", config
            );
        }
        
        @Override
        public String toString() {
            return String.format("SystemStartupEvent{serverId='%s', startupMode='%s', version='%s'}", 
                    getServerId(), startupMode, version);
        }
    }
    
    /**
     * 系统关闭事件
     */
    public static class SystemShutdownEvent extends SystemEvent {
        
        /** 关闭原因 */
        private final ShutdownReason reason;
        
        /** 关闭时间 */
        private final LocalDateTime shutdownTime;
        
        /** 是否优雅关闭 */
        private final boolean graceful;
        
        /** 运行时长（毫秒） */
        private final long runningDuration;
        
        /**
         * 构造函数
         *
         * @param serverId        服务器ID
         * @param reason          关闭原因
         * @param graceful        是否优雅关闭
         * @param runningDuration 运行时长
         * @param source          事件来源
         */
        public SystemShutdownEvent(String serverId, ShutdownReason reason, boolean graceful, 
                                  long runningDuration, String source) {
            super(serverId, source, EventPriority.HIGHEST);
            this.reason = Objects.requireNonNull(reason, "关闭原因不能为空");
            this.shutdownTime = LocalDateTime.now();
            this.graceful = graceful;
            this.runningDuration = Math.max(0, runningDuration);
        }
        
        public ShutdownReason getReason() {
            return reason;
        }
        
        public LocalDateTime getShutdownTime() {
            return shutdownTime;
        }
        
        public boolean isGraceful() {
            return graceful;
        }
        
        public long getRunningDuration() {
            return runningDuration;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "serverId", getServerId(),
                    "reason", reason.name(),
                    "shutdownTime", shutdownTime.toString(),
                    "graceful", graceful,
                    "runningDuration", runningDuration
            );
        }
        
        @Override
        public String toString() {
            return String.format("SystemShutdownEvent{serverId='%s', reason=%s, graceful=%s}", 
                    getServerId(), reason, graceful);
        }
    }
    
    /**
     * 系统维护事件
     */
    public static class SystemMaintenanceEvent extends SystemEvent {
        
        /** 维护类型 */
        private final MaintenanceType maintenanceType;
        
        /** 维护开始时间 */
        private final LocalDateTime startTime;
        
        /** 预计结束时间 */
        private final LocalDateTime estimatedEndTime;
        
        /** 维护说明 */
        private final String description;
        
        /** 是否影响服务 */
        private final boolean serviceAffected;
        
        /**
         * 构造函数
         *
         * @param serverId          服务器ID
         * @param maintenanceType   维护类型
         * @param estimatedEndTime  预计结束时间
         * @param description       维护说明
         * @param serviceAffected   是否影响服务
         * @param source            事件来源
         */
        public SystemMaintenanceEvent(String serverId, MaintenanceType maintenanceType, 
                                     LocalDateTime estimatedEndTime, String description, 
                                     boolean serviceAffected, String source) {
            super(serverId, source, EventPriority.HIGHEST);
            this.maintenanceType = Objects.requireNonNull(maintenanceType, "维护类型不能为空");
            this.startTime = LocalDateTime.now();
            this.estimatedEndTime = estimatedEndTime;
            this.description = description != null ? description : "";
            this.serviceAffected = serviceAffected;
        }
        
        public MaintenanceType getMaintenanceType() {
            return maintenanceType;
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
        
        public LocalDateTime getEstimatedEndTime() {
            return estimatedEndTime;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isServiceAffected() {
            return serviceAffected;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "serverId", getServerId(),
                    "maintenanceType", maintenanceType.name(),
                    "startTime", startTime.toString(),
                    "estimatedEndTime", estimatedEndTime != null ? estimatedEndTime.toString() : "",
                    "description", description,
                    "serviceAffected", serviceAffected
            );
        }
        
        @Override
        public String toString() {
            return String.format("SystemMaintenanceEvent{serverId='%s', type=%s, serviceAffected=%s}", 
                    getServerId(), maintenanceType, serviceAffected);
        }
    }
    
    /**
     * 系统配置变更事件
     */
    public static class SystemConfigChangeEvent extends SystemEvent {
        
        /** 配置键 */
        private final String configKey;
        
        /** 旧值 */
        private final Object oldValue;
        
        /** 新值 */
        private final Object newValue;
        
        /** 变更时间 */
        private final LocalDateTime changeTime;
        
        /** 变更操作者 */
        private final String operator;
        
        /**
         * 构造函数
         *
         * @param serverId  服务器ID
         * @param configKey 配置键
         * @param oldValue  旧值
         * @param newValue  新值
         * @param operator  操作者
         * @param source    事件来源
         */
        public SystemConfigChangeEvent(String serverId, String configKey, Object oldValue, 
                                      Object newValue, String operator, String source) {
            super(serverId, source, EventPriority.HIGH);
            this.configKey = Objects.requireNonNull(configKey, "配置键不能为空");
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.changeTime = LocalDateTime.now();
            this.operator = operator != null ? operator : "system";
        }
        
        public String getConfigKey() {
            return configKey;
        }
        
        public Object getOldValue() {
            return oldValue;
        }
        
        public Object getNewValue() {
            return newValue;
        }
        
        public LocalDateTime getChangeTime() {
            return changeTime;
        }
        
        public String getOperator() {
            return operator;
        }
        
        @Override
        public Object getPayload() {
            return Map.of(
                    "serverId", getServerId(),
                    "configKey", configKey,
                    "oldValue", oldValue != null ? oldValue.toString() : "",
                    "newValue", newValue != null ? newValue.toString() : "",
                    "changeTime", changeTime.toString(),
                    "operator", operator
            );
        }
        
        @Override
        public String toString() {
            return String.format("SystemConfigChangeEvent{serverId='%s', configKey='%s', operator='%s'}", 
                    getServerId(), configKey, operator);
        }
    }
    
    /**
     * 关闭原因枚举
     */
    public enum ShutdownReason {
        /** 正常关闭 */
        NORMAL("正常关闭"),
        
        /** 维护关闭 */
        MAINTENANCE("维护关闭"),
        
        /** 系统异常 */
        ERROR("系统异常"),
        
        /** 资源不足 */
        RESOURCE_EXHAUSTED("资源不足"),
        
        /** 强制关闭 */
        FORCED("强制关闭"),
        
        /** 版本更新 */
        UPDATE("版本更新");
        
        private final String description;
        
        ShutdownReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 维护类型枚举
     */
    public enum MaintenanceType {
        /** 计划维护 */
        SCHEDULED("计划维护"),
        
        /** 紧急维护 */
        EMERGENCY("紧急维护"),
        
        /** 热修复 */
        HOTFIX("热修复"),
        
        /** 版本更新 */
        VERSION_UPDATE("版本更新"),
        
        /** 数据库维护 */
        DATABASE("数据库维护"),
        
        /** 网络维护 */
        NETWORK("网络维护");
        
        private final String description;
        
        MaintenanceType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}