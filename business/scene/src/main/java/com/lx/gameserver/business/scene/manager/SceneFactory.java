/*
 * 文件名: SceneFactory.java
 * 用途: 场景工厂实现类
 * 实现内容:
 *   - 场景实例创建和初始化
 *   - 场景类型注册和管理
 *   - 配置加载和验证
 *   - 依赖注入和组件装配
 *   - 场景模板管理和缓存
 * 技术选型:
 *   - 工厂模式提供统一的创建接口
 *   - Spring依赖注入管理组件生命周期
 *   - 反射机制支持动态类型创建
 *   - 缓存机制优化场景创建性能
 * 依赖关系:
 *   - 被SceneManager调用进行场景创建
 *   - 依赖具体场景实现类进行实例化
 *   - 与配置管理器协作加载场景配置
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.manager;

import com.lx.gameserver.business.scene.core.Scene;
import com.lx.gameserver.business.scene.core.SceneConfig;
import com.lx.gameserver.business.scene.core.SceneType;
import com.lx.gameserver.business.scene.impl.MainCityScene;
import com.lx.gameserver.business.scene.impl.DungeonScene;
import com.lx.gameserver.business.scene.impl.BattlefieldScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 场景工厂
 * <p>
 * 负责创建各种类型的场景实例，提供统一的场景创建接口。
 * 支持场景类型注册、配置加载、依赖注入等功能。
 * 通过工厂模式屏蔽具体场景实现的创建细节。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class SceneFactory {

    /** 场景类型注册表 */
    private final Map<SceneType, Class<? extends Scene>> sceneTypeRegistry = new ConcurrentHashMap<>();

    /** 场景构造器供应商注册表 */
    private final Map<SceneType, Supplier<Scene>> sceneSupplierRegistry = new ConcurrentHashMap<>();

    /** 场景配置模板缓存 */
    private final Map<SceneType, SceneConfig> configTemplateCache = new ConcurrentHashMap<>();

    /** 默认构造器参数类型 */
    private static final Class<?>[] DEFAULT_CONSTRUCTOR_PARAMS = {
        Long.class, String.class, SceneType.class, SceneConfig.class
    };

    @PostConstruct
    public void initialize() {
        log.info("初始化场景工厂");
        
        // 注册默认场景类型
        registerDefaultSceneTypes();
        
        // 加载配置模板
        loadConfigTemplates();
        
        log.info("场景工厂初始化完成，已注册场景类型: {}", sceneTypeRegistry.size());
    }

    /**
     * 注册默认场景类型
     */
    private void registerDefaultSceneTypes() {
        try {
            // 注册主城场景
            registerSceneType(SceneType.MAIN_CITY, MainCityScene.class);
            
            // 注册副本场景
            registerSceneType(SceneType.DUNGEON, DungeonScene.class);
            
            // 注册战场场景
            registerSceneType(SceneType.BATTLEFIELD, BattlefieldScene.class);
            
            // 注册野外场景（使用主城场景作为默认实现）
            registerSceneType(SceneType.FIELD, MainCityScene.class);
            
            // 注册竞技场场景（使用战场场景作为默认实现）
            registerSceneType(SceneType.ARENA, BattlefieldScene.class);
            
            // 注册实例场景（使用副本场景作为默认实现）
            registerSceneType(SceneType.INSTANCE, DungeonScene.class);
            
            log.info("默认场景类型注册完成");
            
        } catch (Exception e) {
            log.error("注册默认场景类型失败", e);
        }
    }

    /**
     * 加载配置模板
     */
    private void loadConfigTemplates() {
        try {
            for (SceneType type : SceneType.values()) {
                SceneConfig template = SceneConfig.createDefault(type);
                configTemplateCache.put(type, template);
            }
            
            log.info("场景配置模板加载完成");
            
        } catch (Exception e) {
            log.error("加载场景配置模板失败", e);
        }
    }

    // ========== 场景创建方法 ==========

    /**
     * 创建场景实例
     *
     * @param sceneId 场景ID
     * @param sceneType 场景类型
     * @param sceneName 场景名称
     * @param config 场景配置
     * @return 场景实例
     */
    public Scene createScene(Long sceneId, SceneType sceneType, String sceneName, SceneConfig config) {
        try {
            log.debug("开始创建场景: type={}, name={}, id={}", sceneType, sceneName, sceneId);
            
            // 验证参数
            if (sceneId == null || sceneType == null || sceneName == null) {
                log.error("创建场景参数无效: sceneId={}, sceneType={}, sceneName={}", 
                         sceneId, sceneType, sceneName);
                return null;
            }
            
            // 使用默认配置如果没有提供
            if (config == null) {
                config = getConfigTemplate(sceneType);
            }
            
            // 验证配置
            if (!config.validate()) {
                log.error("场景配置验证失败: type={}, name={}", sceneType, sceneName);
                return null;
            }
            
            // 优先使用供应商创建
            if (sceneSupplierRegistry.containsKey(sceneType)) {
                Scene scene = createSceneBySupplier(sceneType, sceneId, sceneName, config);
                if (scene != null) {
                    return scene;
                }
            }
            
            // 使用反射创建
            Scene scene = createSceneByReflection(sceneType, sceneId, sceneName, config);
            if (scene != null) {
                log.debug("场景创建成功: type={}, name={}, id={}", sceneType, sceneName, sceneId);
                return scene;
            }
            
            log.error("场景创建失败: type={}, name={}, id={}", sceneType, sceneName, sceneId);
            return null;
            
        } catch (Exception e) {
            log.error("创建场景异常: type={}, name={}, id={}", sceneType, sceneName, sceneId, e);
            return null;
        }
    }

    /**
     * 通过供应商创建场景
     */
    private Scene createSceneBySupplier(SceneType sceneType, Long sceneId, String sceneName, SceneConfig config) {
        try {
            Supplier<Scene> supplier = sceneSupplierRegistry.get(sceneType);
            if (supplier != null) {
                Scene scene = supplier.get();
                if (scene != null) {
                    // 设置基础属性（假设Scene有相应的setter方法或构造后初始化方法）
                    initializeSceneProperties(scene, sceneId, sceneName, sceneType, config);
                    return scene;
                }
            }
        } catch (Exception e) {
            log.warn("通过供应商创建场景失败: type={}", sceneType, e);
        }
        return null;
    }

    /**
     * 通过反射创建场景
     */
    private Scene createSceneByReflection(SceneType sceneType, Long sceneId, String sceneName, SceneConfig config) {
        try {
            Class<? extends Scene> sceneClass = sceneTypeRegistry.get(sceneType);
            if (sceneClass == null) {
                log.error("未注册的场景类型: {}", sceneType);
                return null;
            }
            
            // 尝试使用默认构造器
            Constructor<? extends Scene> constructor = findConstructor(sceneClass);
            if (constructor != null) {
                Object[] args = {sceneId, sceneName, sceneType, config};
                return constructor.newInstance(args);
            }
            
            log.error("未找到适合的构造器: class={}", sceneClass.getName());
            return null;
            
        } catch (Exception e) {
            log.error("通过反射创建场景失败: type={}", sceneType, e);
            return null;
        }
    }

    /**
     * 查找合适的构造器
     */
    private Constructor<? extends Scene> findConstructor(Class<? extends Scene> sceneClass) {
        try {
            // 尝试标准构造器
            return sceneClass.getDeclaredConstructor(DEFAULT_CONSTRUCTOR_PARAMS);
        } catch (NoSuchMethodException e) {
            log.debug("未找到标准构造器，尝试其他构造器: class={}", sceneClass.getName());
            
            // 尝试查找其他可用的构造器
            Constructor<?>[] constructors = sceneClass.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (paramTypes.length == 4 && 
                    paramTypes[0].isAssignableFrom(Long.class) &&
                    paramTypes[1].isAssignableFrom(String.class) &&
                    paramTypes[2].isAssignableFrom(SceneType.class) &&
                    paramTypes[3].isAssignableFrom(SceneConfig.class)) {
                    @SuppressWarnings("unchecked")
                    Constructor<? extends Scene> sceneConstructor = (Constructor<? extends Scene>) constructor;
                    return sceneConstructor;
                }
            }
        }
        return null;
    }

    /**
     * 初始化场景属性
     */
    private void initializeSceneProperties(Scene scene, Long sceneId, String sceneName, 
                                         SceneType sceneType, SceneConfig config) {
        // 这里应该设置场景的基础属性
        // 由于Scene类的属性可能是final的，这个方法可能需要根据实际实现调整
        log.debug("初始化场景属性: id={}, name={}, type={}", sceneId, sceneName, sceneType);
    }

    // ========== 类型注册方法 ==========

    /**
     * 注册场景类型
     *
     * @param sceneType 场景类型
     * @param sceneClass 场景实现类
     */
    public void registerSceneType(SceneType sceneType, Class<? extends Scene> sceneClass) {
        if (sceneType == null || sceneClass == null) {
            log.warn("注册场景类型参数无效: type={}, class={}", sceneType, sceneClass);
            return;
        }
        
        sceneTypeRegistry.put(sceneType, sceneClass);
        log.info("注册场景类型: {} -> {}", sceneType, sceneClass.getSimpleName());
    }

    /**
     * 注册场景供应商
     *
     * @param sceneType 场景类型
     * @param supplier 场景供应商
     */
    public void registerSceneSupplier(SceneType sceneType, Supplier<Scene> supplier) {
        if (sceneType == null || supplier == null) {
            log.warn("注册场景供应商参数无效: type={}, supplier={}", sceneType, supplier);
            return;
        }
        
        sceneSupplierRegistry.put(sceneType, supplier);
        log.info("注册场景供应商: {}", sceneType);
    }

    /**
     * 注销场景类型
     *
     * @param sceneType 场景类型
     */
    public void unregisterSceneType(SceneType sceneType) {
        if (sceneType == null) {
            return;
        }
        
        sceneTypeRegistry.remove(sceneType);
        sceneSupplierRegistry.remove(sceneType);
        log.info("注销场景类型: {}", sceneType);
    }

    /**
     * 检查场景类型是否已注册
     *
     * @param sceneType 场景类型
     * @return 是否已注册
     */
    public boolean isSceneTypeRegistered(SceneType sceneType) {
        return sceneTypeRegistry.containsKey(sceneType) || sceneSupplierRegistry.containsKey(sceneType);
    }

    /**
     * 获取已注册的场景类型
     *
     * @return 已注册的场景类型集合
     */
    public java.util.Set<SceneType> getRegisteredSceneTypes() {
        java.util.Set<SceneType> types = new java.util.HashSet<>(sceneTypeRegistry.keySet());
        types.addAll(sceneSupplierRegistry.keySet());
        return types;
    }

    // ========== 配置模板方法 ==========

    /**
     * 获取配置模板
     *
     * @param sceneType 场景类型
     * @return 配置模板
     */
    public SceneConfig getConfigTemplate(SceneType sceneType) {
        SceneConfig template = configTemplateCache.get(sceneType);
        if (template != null) {
            // 返回配置副本以避免修改原模板
            return createConfigCopy(template);
        }
        
        // 如果没有缓存的模板，创建默认配置
        return SceneConfig.createDefault(sceneType);
    }

    /**
     * 设置配置模板
     *
     * @param sceneType 场景类型
     * @param config 配置模板
     */
    public void setConfigTemplate(SceneType sceneType, SceneConfig config) {
        if (sceneType == null || config == null) {
            log.warn("设置配置模板参数无效: type={}, config={}", sceneType, config);
            return;
        }
        
        if (!config.validate()) {
            log.warn("配置模板验证失败: type={}", sceneType);
            return;
        }
        
        configTemplateCache.put(sceneType, config);
        log.info("设置配置模板: {}", sceneType);
    }

    /**
     * 创建配置副本
     */
    private SceneConfig createConfigCopy(SceneConfig original) {
        // 这里应该实现深拷贝逻辑
        // 为了简化，先返回新的builder配置
        return SceneConfig.builder()
                .templateId(original.getTemplateId())
                .version(original.getVersion())
                .maxEntities(original.getMaxEntities())
                .maxPlayers(original.getMaxPlayers())
                .lifeTime(original.getLifeTime())
                .emptyTimeout(original.getEmptyTimeout())
                .mapConfig(original.getMapConfig())
                .spawnPoints(new java.util.ArrayList<>(original.getSpawnPoints()))
                .portals(new java.util.ArrayList<>(original.getPortals()))
                .npcs(new java.util.ArrayList<>(original.getNpcs()))
                .monsterConfig(original.getMonsterConfig())
                .aoiConfig(original.getAoiConfig())
                .rules(original.getRules())
                .performanceConfig(original.getPerformanceConfig())
                .extensions(new java.util.HashMap<>(original.getExtensions()))
                .customConfig(new java.util.HashMap<>(original.getCustomConfig()))
                .build();
    }

    // ========== 批量创建方法 ==========

    /**
     * 批量创建场景
     *
     * @param sceneType 场景类型
     * @param namePrefix 场景名称前缀
     * @param count 创建数量
     * @param config 场景配置
     * @return 创建的场景列表
     */
    public java.util.List<Scene> createScenes(SceneType sceneType, String namePrefix, 
                                            int count, SceneConfig config) {
        java.util.List<Scene> scenes = new java.util.ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String sceneName = namePrefix + "_" + (i + 1);
            Long sceneId = System.currentTimeMillis() + i; // 简单的ID生成策略
            
            Scene scene = createScene(sceneId, sceneType, sceneName, config);
            if (scene != null) {
                scenes.add(scene);
            } else {
                log.warn("批量创建场景失败: type={}, name={}", sceneType, sceneName);
            }
        }
        
        log.info("批量创建场景完成: type={}, 成功/总数={}/{}", sceneType, scenes.size(), count);
        return scenes;
    }

    // ========== 统计和信息方法 ==========

    /**
     * 获取工厂统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getFactoryStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("registeredTypes", sceneTypeRegistry.size());
        stats.put("registeredSuppliers", sceneSupplierRegistry.size());
        stats.put("configTemplates", configTemplateCache.size());
        stats.put("supportedTypes", getRegisteredSceneTypes());
        return stats;
    }

    @Override
    public String toString() {
        return String.format("SceneFactory{types=%d, suppliers=%d, templates=%d}", 
                sceneTypeRegistry.size(), sceneSupplierRegistry.size(), configTemplateCache.size());
    }
}