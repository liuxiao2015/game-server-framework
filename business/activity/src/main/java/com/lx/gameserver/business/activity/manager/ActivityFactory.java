/*
 * 文件名: ActivityFactory.java
 * 用途: 活动工厂
 * 实现内容:
 *   - 活动实例创建和类型注册
 *   - 配置解析和依赖注入
 *   - 活动缓存和生命周期管理
 *   - 支持插件化活动扩展
 * 技术选型:
 *   - Spring Factory模式
 *   - 反射机制动态创建
 *   - JSON配置解析
 *   - 缓存优化
 * 依赖关系:
 *   - 依赖Activity核心类
 *   - 被ActivityManager使用
 *   - 集成Spring容器
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.manager;

import com.lx.gameserver.business.activity.core.Activity;
import com.lx.gameserver.business.activity.core.ActivityContext;
import com.lx.gameserver.business.activity.core.ActivityType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动工厂
 * <p>
 * 负责活动实例的创建、配置解析、类型注册等。
 * 支持动态活动类型注册和插件化扩展。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Service
public class ActivityFactory {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /** 活动类型注册表 */
    private final Map<ActivityType, Class<? extends Activity>> activityRegistry = new ConcurrentHashMap<>();
    
    /** 活动实例缓存 */
    private final Map<String, Activity> activityCache = new ConcurrentHashMap<>();
    
    /** 配置模板缓存 */
    private final Map<ActivityType, JsonNode> configTemplates = new ConcurrentHashMap<>();
    
    /** 是否启用缓存 */
    private boolean cacheEnabled = true;
    
    /** 缓存大小限制 */
    private int maxCacheSize = 1000;
    
    /**
     * 初始化工厂
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化活动工厂");
        
        // 注册默认活动类型
        registerDefaultActivityTypes();
        
        // 加载配置模板
        loadConfigTemplates();
        
        log.info("活动工厂初始化完成，注册活动类型数: {}", activityRegistry.size());
    }
    
    /**
     * 创建活动实例
     *
     * @param config 活动配置
     * @return 活动实例
     */
    public Activity createActivity(ActivityConfig config) {
        if (config == null || config.activityType == null) {
            log.warn("无法创建活动：配置为空或缺少活动类型");
            return null;
        }
        
        try {
            // 检查缓存
            String cacheKey = generateCacheKey(config);
            if (cacheEnabled && activityCache.containsKey(cacheKey)) {
                Activity cachedActivity = activityCache.get(cacheKey);
                if (cachedActivity != null) {
                    log.debug("从缓存获取活动实例: {}", cacheKey);
                    return cloneActivity(cachedActivity, config);
                }
            }
            
            // 创建新实例
            Activity activity = createNewActivity(config);
            if (activity == null) {
                return null;
            }
            
            // 配置活动属性
            configureActivity(activity, config);
            
            // 初始化活动
            initializeActivity(activity, config);
            
            // 添加到缓存
            if (cacheEnabled && activityCache.size() < maxCacheSize) {
                activityCache.put(cacheKey, activity);
            }
            
            log.info("成功创建活动实例: {} (Type: {}, ID: {})", 
                    activity.getActivityName(), activity.getActivityType(), activity.getActivityId());
            
            return activity;
            
        } catch (Exception e) {
            log.error("创建活动实例失败: {}", config, e);
            return null;
        }
    }
    
    /**
     * 注册活动类型
     *
     * @param activityType  活动类型
     * @param activityClass 活动类
     * @return 注册是否成功
     */
    public boolean registerActivityType(ActivityType activityType, Class<? extends Activity> activityClass) {
        if (activityType == null || activityClass == null) {
            log.warn("无法注册活动类型：参数为空");
            return false;
        }
        
        // 检查类是否有效
        if (!Activity.class.isAssignableFrom(activityClass)) {
            log.warn("无法注册活动类型：类不继承自Activity - {}", activityClass.getName());
            return false;
        }
        
        activityRegistry.put(activityType, activityClass);
        log.info("成功注册活动类型: {} -> {}", activityType, activityClass.getSimpleName());
        
        return true;
    }
    
    /**
     * 注销活动类型
     *
     * @param activityType 活动类型
     */
    public void unregisterActivityType(ActivityType activityType) {
        if (activityType == null) {
            return;
        }
        
        Class<? extends Activity> removedClass = activityRegistry.remove(activityType);
        if (removedClass != null) {
            log.info("成功注销活动类型: {} -> {}", activityType, removedClass.getSimpleName());
            
            // 清理相关缓存
            clearCacheByType(activityType);
        }
    }
    
    /**
     * 获取注册的活动类型
     *
     * @param activityType 活动类型
     * @return 活动类
     */
    public Class<? extends Activity> getActivityClass(ActivityType activityType) {
        return activityRegistry.get(activityType);
    }
    
    /**
     * 检查活动类型是否已注册
     *
     * @param activityType 活动类型
     * @return 是否已注册
     */
    public boolean isRegistered(ActivityType activityType) {
        return activityRegistry.containsKey(activityType);
    }
    
    /**
     * 获取所有注册的活动类型
     *
     * @return 活动类型集合
     */
    public Map<ActivityType, Class<? extends Activity>> getAllRegisteredTypes() {
        return new ConcurrentHashMap<>(activityRegistry);
    }
    
    /**
     * 解析配置文件创建活动
     *
     * @param configJson 配置JSON
     * @return 活动实例
     */
    public Activity createActivityFromJson(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            log.warn("无法从JSON创建活动：配置为空");
            return null;
        }
        
        try {
            JsonNode configNode = objectMapper.readTree(configJson);
            ActivityConfig config = parseConfig(configNode);
            
            return createActivity(config);
            
        } catch (Exception e) {
            log.error("从JSON创建活动失败: {}", configJson, e);
            return null;
        }
    }
    
    /**
     * 获取配置模板
     *
     * @param activityType 活动类型
     * @return 配置模板JSON
     */
    public JsonNode getConfigTemplate(ActivityType activityType) {
        return configTemplates.get(activityType);
    }
    
    /**
     * 验证活动配置
     *
     * @param config 活动配置
     * @return 验证结果
     */
    public ValidationResult validateConfig(ActivityConfig config) {
        ValidationResult result = new ValidationResult();
        
        if (config == null) {
            result.addError("配置不能为空");
            return result;
        }
        
        // 验证必填字段
        if (config.activityId == null) {
            result.addError("活动ID不能为空");
        }
        
        if (config.activityName == null || config.activityName.trim().isEmpty()) {
            result.addError("活动名称不能为空");
        }
        
        if (config.activityType == null) {
            result.addError("活动类型不能为空");
        } else if (!isRegistered(config.activityType)) {
            result.addError("活动类型未注册: " + config.activityType);
        }
        
        // 验证时间配置
        if (config.startTime != null && config.endTime != null && 
            config.startTime >= config.endTime) {
            result.addError("开始时间不能大于等于结束时间");
        }
        
        // 验证参与人数配置
        if (config.maxParticipants != null && config.maxParticipants <= 0) {
            result.addError("最大参与人数必须大于0");
        }
        
        return result;
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        activityCache.clear();
        log.info("已清理活动工厂缓存");
    }
    
    /**
     * 获取工厂统计信息
     *
     * @return 统计信息
     */
    public FactoryStats getStats() {
        FactoryStats stats = new FactoryStats();
        stats.registeredTypeCount = activityRegistry.size();
        stats.cacheSize = activityCache.size();
        stats.cacheEnabled = this.cacheEnabled;
        stats.maxCacheSize = this.maxCacheSize;
        
        return stats;
    }
    
    // ===== 私有方法 =====
    
    /**
     * 注册默认活动类型
     */
    private void registerDefaultActivityTypes() {
        // 这里可以注册默认的活动类型
        // 具体的活动实现类将在impl包中定义
        log.debug("注册默认活动类型完成");
    }
    
    /**
     * 加载配置模板
     */
    private void loadConfigTemplates() {
        // 加载各种活动类型的配置模板
        for (ActivityType type : ActivityType.values()) {
            try {
                JsonNode template = createDefaultTemplate(type);
                configTemplates.put(type, template);
            } catch (Exception e) {
                log.warn("加载活动类型配置模板失败: {}", type, e);
            }
        }
        
        log.debug("加载配置模板完成，模板数量: {}", configTemplates.size());
    }
    
    /**
     * 创建默认配置模板
     *
     * @param activityType 活动类型
     * @return 配置模板
     */
    private JsonNode createDefaultTemplate(ActivityType activityType) {
        // 创建基础模板结构
        Map<String, Object> template = new ConcurrentHashMap<>();
        template.put("activityType", activityType.getCode());
        template.put("priority", activityType.getPriority());
        template.put("repeatable", activityType.isPeriodic());
        
        // 根据类型添加特定配置
        switch (activityType) {
            case DAILY:
                template.put("resetTime", "00:00:00");
                break;
            case WEEKLY:
                template.put("resetDay", "MONDAY");
                template.put("resetTime", "00:00:00");
                break;
            case LIMITED_TIME:
                template.put("duration", 86400000); // 24小时
                break;
            default:
                break;
        }
        
        return objectMapper.valueToTree(template);
    }
    
    /**
     * 创建新活动实例
     *
     * @param config 活动配置
     * @return 活动实例
     */
    private Activity createNewActivity(ActivityConfig config) throws Exception {
        Class<? extends Activity> activityClass = activityRegistry.get(config.activityType);
        if (activityClass == null) {
            log.warn("未找到活动类型对应的实现类: {}", config.activityType);
            return null;
        }
        
        // 使用Spring容器创建实例（支持依赖注入）
        try {
            return applicationContext.getBean(activityClass);
        } catch (Exception e) {
            // 如果Spring容器中没有注册，则使用反射创建
            log.debug("Spring容器中未找到活动类，使用反射创建: {}", activityClass.getSimpleName());
            return activityClass.getDeclaredConstructor().newInstance();
        }
    }
    
    /**
     * 配置活动属性
     *
     * @param activity 活动实例
     * @param config   活动配置
     */
    private void configureActivity(Activity activity, ActivityConfig config) {
        activity.setActivityId(config.activityId);
        activity.setActivityName(config.activityName);
        activity.setDescription(config.description);
        activity.setActivityType(config.activityType);
        activity.setStartTime(config.startTime);
        activity.setEndTime(config.endTime);
        activity.setPriority(config.priority);
        activity.setRepeatable(config.repeatable);
        activity.setMaxParticipants(config.maxParticipants);
        
        // 设置配置数据
        if (config.configData != null) {
            config.configData.forEach(activity::setConfig);
        }
    }
    
    /**
     * 初始化活动
     *
     * @param activity 活动实例
     * @param config   活动配置
     */
    private void initializeActivity(Activity activity, ActivityConfig config) throws Exception {
        ActivityContext context = new ActivityContext(
                config.activityId, null, config.activityType);
        
        // 设置上下文参数
        if (config.parameters != null) {
            config.parameters.forEach(context::setParameter);
        }
        
        activity.initialize(context);
    }
    
    /**
     * 克隆活动实例
     *
     * @param original 原始活动
     * @param config   新配置
     * @return 克隆的活动
     */
    private Activity cloneActivity(Activity original, ActivityConfig config) throws Exception {
        Activity cloned = original.getClass().getDeclaredConstructor().newInstance();
        
        // 复制基本属性
        cloned.setActivityType(original.getActivityType());
        cloned.setPriority(original.getPriority());
        cloned.setRepeatable(original.getRepeatable());
        
        // 应用新配置
        configureActivity(cloned, config);
        
        return cloned;
    }
    
    /**
     * 生成缓存键
     *
     * @param config 活动配置
     * @return 缓存键
     */
    private String generateCacheKey(ActivityConfig config) {
        return String.format("%s_%d_%s", 
                config.activityType.getCode(), 
                config.priority != null ? config.priority : 0,
                config.repeatable != null ? config.repeatable : false);
    }
    
    /**
     * 按类型清理缓存
     *
     * @param activityType 活动类型
     */
    private void clearCacheByType(ActivityType activityType) {
        String typePrefix = activityType.getCode() + "_";
        activityCache.entrySet().removeIf(entry -> entry.getKey().startsWith(typePrefix));
    }
    
    /**
     * 解析配置
     *
     * @param configNode 配置节点
     * @return 活动配置
     */
    private ActivityConfig parseConfig(JsonNode configNode) throws Exception {
        ActivityConfig config = objectMapper.treeToValue(configNode, ActivityConfig.class);
        
        // 验证配置
        ValidationResult validation = validateConfig(config);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("配置验证失败: " + validation.getErrors());
        }
        
        return config;
    }
    
    /**
     * 活动配置类
     */
    public static class ActivityConfig {
        public Long activityId;
        public String activityName;
        public String description;
        public ActivityType activityType;
        public Long startTime;
        public Long endTime;
        public Integer priority = 0;
        public Boolean repeatable = false;
        public Integer maxParticipants;
        public Map<String, Object> configData;
        public Map<String, Object> parameters;
        
        @Override
        public String toString() {
            return String.format("ActivityConfig{id=%d, name='%s', type=%s}", 
                    activityId, activityName, activityType);
        }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private boolean valid = true;
        private final StringBuilder errors = new StringBuilder();
        
        public void addError(String error) {
            this.valid = false;
            if (errors.length() > 0) {
                errors.append("; ");
            }
            errors.append(error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrors() {
            return errors.toString();
        }
    }
    
    /**
     * 工厂统计信息
     */
    public static class FactoryStats {
        public int registeredTypeCount;
        public int cacheSize;
        public boolean cacheEnabled;
        public int maxCacheSize;
    }
}