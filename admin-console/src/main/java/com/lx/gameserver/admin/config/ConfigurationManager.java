/*
 * 文件名: ConfigurationManager.java
 * 用途: 配置管理器实现
 * 实现内容:
 *   - 配置版本控制管理
 *   - 配置热更新机制
 *   - 配置回滚功能
 *   - 配置审核流程
 *   - 配置同步机制
 *   - 配置变更通知
 * 技术选型:
 *   - Spring Environment (配置管理)
 *   - Redis (配置缓存)
 *   - Spring Event (变更通知)
 *   - Jackson (JSON序列化)
 * 依赖关系: 被所有需要动态配置的模块使用
 */
package com.lx.gameserver.admin.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lx.gameserver.admin.core.AdminContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 配置管理器
 * <p>
 * 提供统一的配置管理功能，支持配置的版本控制、热更新、
 * 回滚等操作。集成配置审核流程和变更通知机制，
 * 确保配置变更的安全性和可追溯性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Service
public class ConfigurationManager {

    /** Spring环境配置 */
    @Autowired
    private ConfigurableEnvironment environment;

    /** 事件发布器 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** Redis模板 */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** 管理平台上下文 */
    @Autowired
    private AdminContext adminContext;

    /** JSON映射器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 动态配置存储 */
    private final Map<String, ConfigItem> dynamicConfigs = new ConcurrentHashMap<>();

    /** 配置变更监听器 */
    private final Map<String, List<ConfigChangeListener>> configListeners = new ConcurrentHashMap<>();

    /** 配置版本历史 */
    private final Map<String, List<ConfigVersion>> configVersions = new ConcurrentHashMap<>();

    /** 配置分组 */
    private final Map<String, Set<String>> configGroups = new ConcurrentHashMap<>();

    /** Redis键前缀 */
    private static final String REDIS_CONFIG_PREFIX = "admin:config:";
    private static final String REDIS_CONFIG_VERSION_PREFIX = "admin:config:version:";
    private static final String REDIS_CONFIG_LOCK_PREFIX = "admin:config:lock:";

    /** 配置审核状态 */
    public enum AuditStatus {
        DRAFT,      // 草稿
        PENDING,    // 待审核
        APPROVED,   // 已审核
        REJECTED,   // 已拒绝
        APPLIED     // 已应用
    }

    /**
     * 初始化配置管理器
     */
    @PostConstruct
    public void init() {
        log.info("初始化配置管理器...");
        
        // 初始化默认配置分组
        initDefaultConfigGroups();
        
        // 加载现有配置
        loadExistingConfigs();
        
        log.info("配置管理器初始化完成，管理 {} 个配置项", dynamicConfigs.size());
    }

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    public String getConfig(String key) {
        // 优先从动态配置获取
        ConfigItem item = dynamicConfigs.get(key);
        if (item != null && item.getStatus() == AuditStatus.APPLIED) {
            return item.getValue();
        }
        
        // 从Spring Environment获取
        return environment.getProperty(key);
    }

    /**
     * 获取配置值并转换为指定类型
     *
     * @param key 配置键
     * @param targetType 目标类型
     * @return 转换后的配置值
     */
    public <T> T getConfig(String key, Class<T> targetType) {
        String value = getConfig(key);
        if (value == null) {
            return null;
        }
        
        return environment.getConversionService().convert(value, targetType);
    }

    /**
     * 获取配置值，如果不存在则返回默认值
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getConfig(String key, String defaultValue) {
        String value = getConfig(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 设置配置值
     *
     * @param key 配置键
     * @param value 配置值
     * @param description 配置描述
     * @param operator 操作人
     * @return 配置项ID
     */
    public String setConfig(String key, String value, String description, String operator) {
        return setConfig(key, value, description, operator, null, false);
    }

    /**
     * 设置配置值（高级版本）
     *
     * @param key 配置键
     * @param value 配置值
     * @param description 配置描述
     * @param operator 操作人
     * @param group 配置分组
     * @param requiresApproval 是否需要审核
     * @return 配置项ID
     */
    public String setConfig(String key, String value, String description, String operator, 
                           String group, boolean requiresApproval) {
        String configId = UUID.randomUUID().toString();
        
        ConfigItem item = new ConfigItem(
            configId,
            key,
            value,
            description,
            group,
            operator,
            requiresApproval ? AuditStatus.PENDING : AuditStatus.APPLIED,
            LocalDateTime.now()
        );
        
        // 保存配置项
        dynamicConfigs.put(key, item);
        
        // 记录版本历史
        recordConfigVersion(key, item);
        
        // 如果不需要审核，直接应用
        if (!requiresApproval) {
            applyConfig(key, item);
        }
        
        // 发布配置变更事件
        eventPublisher.publishEvent(new ConfigChangedEvent(key, value, operator));
        
        log.info("设置配置: {} = {}, 操作人: {}, 状态: {}", key, value, operator, item.getStatus());
        return configId;
    }

    /**
     * 批量设置配置
     *
     * @param configs 配置映射
     * @param operator 操作人
     * @param group 配置分组
     * @return 操作结果
     */
    public BatchConfigResult batchSetConfig(Map<String, String> configs, String operator, String group) {
        BatchConfigResult result = new BatchConfigResult();
        
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            try {
                String configId = setConfig(entry.getKey(), entry.getValue(), 
                                          "批量设置", operator, group, false);
                result.addSuccess(entry.getKey(), configId);
            } catch (Exception e) {
                result.addFailure(entry.getKey(), e.getMessage());
                log.error("批量设置配置失败: {}", entry.getKey(), e);
            }
        }
        
        return result;
    }

    /**
     * 审核配置
     *
     * @param key 配置键
     * @param approved 是否通过
     * @param auditor 审核人
     * @param comment 审核意见
     * @return 审核结果
     */
    public boolean auditConfig(String key, boolean approved, String auditor, String comment) {
        ConfigItem item = dynamicConfigs.get(key);
        if (item == null || item.getStatus() != AuditStatus.PENDING) {
            return false;
        }
        
        if (approved) {
            item.setStatus(AuditStatus.APPROVED);
            item.setAuditor(auditor);
            item.setAuditComment(comment);
            item.setAuditTime(LocalDateTime.now());
            
            // 应用配置
            applyConfig(key, item);
            
            log.info("配置审核通过: {}, 审核人: {}", key, auditor);
        } else {
            item.setStatus(AuditStatus.REJECTED);
            item.setAuditor(auditor);
            item.setAuditComment(comment);
            item.setAuditTime(LocalDateTime.now());
            
            log.info("配置审核拒绝: {}, 审核人: {}, 原因: {}", key, auditor, comment);
        }
        
        // 发布审核事件
        eventPublisher.publishEvent(new ConfigAuditEvent(key, approved, auditor, comment));
        
        return true;
    }

    /**
     * 回滚配置到指定版本
     *
     * @param key 配置键
     * @param versionId 版本ID
     * @param operator 操作人
     * @return 回滚结果
     */
    public boolean rollbackConfig(String key, String versionId, String operator) {
        List<ConfigVersion> versions = configVersions.get(key);
        if (versions == null) {
            return false;
        }
        
        Optional<ConfigVersion> targetVersion = versions.stream()
            .filter(v -> v.getVersionId().equals(versionId))
            .findFirst();
        
        if (targetVersion.isEmpty()) {
            return false;
        }
        
        ConfigVersion version = targetVersion.get();
        setConfig(key, version.getValue(), "回滚到版本: " + versionId, operator);
        
        log.info("配置回滚: {} 回滚到版本 {}, 操作人: {}", key, versionId, operator);
        return true;
    }

    /**
     * 获取配置版本历史
     *
     * @param key 配置键
     * @return 版本历史列表
     */
    public List<ConfigVersion> getConfigVersions(String key) {
        return configVersions.getOrDefault(key, Collections.emptyList());
    }

    /**
     * 添加配置变更监听器
     *
     * @param key 配置键
     * @param listener 监听器
     */
    public void addConfigChangeListener(String key, ConfigChangeListener listener) {
        configListeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 移除配置变更监听器
     *
     * @param key 配置键
     * @param listener 监听器
     */
    public void removeConfigChangeListener(String key, ConfigChangeListener listener) {
        List<ConfigChangeListener> listeners = configListeners.get(key);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 获取配置分组
     *
     * @param group 分组名称
     * @return 配置键列表
     */
    public Set<String> getConfigGroup(String group) {
        return configGroups.getOrDefault(group, Collections.emptySet());
    }

    /**
     * 刷新所有配置
     */
    public void refreshAllConfigs() {
        log.info("刷新所有配置...");
        
        for (Map.Entry<String, ConfigItem> entry : dynamicConfigs.entrySet()) {
            ConfigItem item = entry.getValue();
            if (item.getStatus() == AuditStatus.APPLIED) {
                applyConfig(entry.getKey(), item);
            }
        }
        
        log.info("配置刷新完成");
    }

    /**
     * 应用配置
     */
    private void applyConfig(String key, ConfigItem item) {
        try {
            // 更新Spring Environment
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(key, item.getValue());
            
            MapPropertySource dynamicPropertySource = new MapPropertySource(
                "dynamic-config-" + key, propertyMap);
            environment.getPropertySources().addFirst(dynamicPropertySource);
            
            // 更新状态
            item.setStatus(AuditStatus.APPLIED);
            item.setAppliedTime(LocalDateTime.now());
            
            // 缓存到Redis
            cacheConfigToRedis(key, item);
            
            // 通知监听器
            notifyConfigListeners(key, item.getValue());
            
        } catch (Exception e) {
            log.error("应用配置失败: {}", key, e);
        }
    }

    /**
     * 记录配置版本
     */
    private void recordConfigVersion(String key, ConfigItem item) {
        String versionId = UUID.randomUUID().toString();
        ConfigVersion version = new ConfigVersion(
            versionId,
            key,
            item.getValue(),
            item.getOperator(),
            LocalDateTime.now()
        );
        
        configVersions.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(version);
        
        // 限制版本历史数量
        List<ConfigVersion> versions = configVersions.get(key);
        if (versions.size() > 50) { // 保留最近50个版本
            versions.remove(0);
        }
    }

    /**
     * 通知配置变更监听器
     */
    private void notifyConfigListeners(String key, String newValue) {
        List<ConfigChangeListener> listeners = configListeners.get(key);
        if (listeners != null) {
            for (ConfigChangeListener listener : listeners) {
                try {
                    listener.onConfigChanged(key, newValue);
                } catch (Exception e) {
                    log.error("通知配置变更监听器失败: {}", key, e);
                }
            }
        }
    }

    /**
     * 缓存配置到Redis
     */
    private void cacheConfigToRedis(String key, ConfigItem item) {
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_CONFIG_PREFIX + key;
                redisTemplate.opsForValue().set(redisKey, item, 24, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("缓存配置到Redis失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 初始化默认配置分组
     */
    private void initDefaultConfigGroups() {
        configGroups.put("database", new ConcurrentHashMap<String, Boolean>().keySet());
        configGroups.put("cache", new ConcurrentHashMap<String, Boolean>().keySet());
        configGroups.put("security", new ConcurrentHashMap<String, Boolean>().keySet());
        configGroups.put("monitor", new ConcurrentHashMap<String, Boolean>().keySet());
        configGroups.put("business", new ConcurrentHashMap<String, Boolean>().keySet());
    }

    /**
     * 加载现有配置
     */
    private void loadExistingConfigs() {
        // 这里可以从数据库或配置文件加载现有配置
        log.debug("加载现有配置完成");
    }

    /**
     * 配置项
     */
    public static class ConfigItem {
        private String id;
        private String key;
        private String value;
        private String description;
        private String group;
        private String operator;
        private AuditStatus status;
        private LocalDateTime createTime;
        private String auditor;
        private String auditComment;
        private LocalDateTime auditTime;
        private LocalDateTime appliedTime;

        public ConfigItem(String id, String key, String value, String description, String group,
                         String operator, AuditStatus status, LocalDateTime createTime) {
            this.id = id;
            this.key = key;
            this.value = value;
            this.description = description;
            this.group = group;
            this.operator = operator;
            this.status = status;
            this.createTime = createTime;
        }

        // Getter和Setter方法
        public String getId() { return id; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public String getDescription() { return description; }
        public String getGroup() { return group; }
        public String getOperator() { return operator; }
        public AuditStatus getStatus() { return status; }
        public void setStatus(AuditStatus status) { this.status = status; }
        public LocalDateTime getCreateTime() { return createTime; }
        public String getAuditor() { return auditor; }
        public void setAuditor(String auditor) { this.auditor = auditor; }
        public String getAuditComment() { return auditComment; }
        public void setAuditComment(String auditComment) { this.auditComment = auditComment; }
        public LocalDateTime getAuditTime() { return auditTime; }
        public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
        public LocalDateTime getAppliedTime() { return appliedTime; }
        public void setAppliedTime(LocalDateTime appliedTime) { this.appliedTime = appliedTime; }
    }

    /**
     * 配置版本
     */
    public static class ConfigVersion {
        private String versionId;
        private String key;
        private String value;
        private String operator;
        private LocalDateTime createTime;

        public ConfigVersion(String versionId, String key, String value, String operator, LocalDateTime createTime) {
            this.versionId = versionId;
            this.key = key;
            this.value = value;
            this.operator = operator;
            this.createTime = createTime;
        }

        // Getter方法
        public String getVersionId() { return versionId; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public String getOperator() { return operator; }
        public LocalDateTime getCreateTime() { return createTime; }
    }

    /**
     * 批量配置结果
     */
    public static class BatchConfigResult {
        private Map<String, String> successResults = new HashMap<>();
        private Map<String, String> failureResults = new HashMap<>();

        public void addSuccess(String key, String configId) {
            successResults.put(key, configId);
        }

        public void addFailure(String key, String error) {
            failureResults.put(key, error);
        }

        // Getter方法
        public Map<String, String> getSuccessResults() { return successResults; }
        public Map<String, String> getFailureResults() { return failureResults; }
        public int getSuccessCount() { return successResults.size(); }
        public int getFailureCount() { return failureResults.size(); }
    }

    /**
     * 配置变更监听器接口
     */
    public interface ConfigChangeListener {
        void onConfigChanged(String key, String newValue);
    }

    /**
     * 配置变更事件
     */
    public static class ConfigChangedEvent {
        private String key;
        private String value;
        private String operator;
        private LocalDateTime timestamp;

        public ConfigChangedEvent(String key, String value, String operator) {
            this.key = key;
            this.value = value;
            this.operator = operator;
            this.timestamp = LocalDateTime.now();
        }

        // Getter方法
        public String getKey() { return key; }
        public String getValue() { return value; }
        public String getOperator() { return operator; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * 配置审核事件
     */
    public static class ConfigAuditEvent {
        private String key;
        private boolean approved;
        private String auditor;
        private String comment;
        private LocalDateTime timestamp;

        public ConfigAuditEvent(String key, boolean approved, String auditor, String comment) {
            this.key = key;
            this.approved = approved;
            this.auditor = auditor;
            this.comment = comment;
            this.timestamp = LocalDateTime.now();
        }

        // Getter方法
        public String getKey() { return key; }
        public boolean isApproved() { return approved; }
        public String getAuditor() { return auditor; }
        public String getComment() { return comment; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}