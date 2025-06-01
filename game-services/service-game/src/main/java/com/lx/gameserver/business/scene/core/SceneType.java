/*
 * 文件名: SceneType.java
 * 用途: 场景类型枚举定义
 * 实现内容:
 *   - 定义游戏中所有支持的场景类型
 *   - 提供场景类型的基本属性和特性
 *   - 支持类型扩展和动态查询
 *   - 为场景系统提供灵活的类型管理
 * 技术选型:
 *   - 枚举类型保证类型安全
 *   - 静态工厂方法支持动态查询
 *   - 属性配置支持场景特性定义
 * 依赖关系:
 *   - 被Scene基类使用进行类型标识
 *   - 被SceneManager使用进行场景分类管理
 *   - 被SceneFactory使用进行场景创建
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.core;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景类型枚举
 * <p>
 * 定义游戏中所有支持的场景类型，每种类型具有不同的特性和行为。
 * 支持类型扩展和动态查询，为场景系统提供灵活的类型管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public enum SceneType {
    
    /** 主城场景 - 安全区域，提供基础服务 */
    MAIN_CITY("main_city", "主城", "安全的城镇区域，提供各种NPC服务", 1000, true, false),
    
    /** 野外场景 - PVP区域，自由战斗 */
    FIELD("field", "野外", "开放的野外区域，允许PVP战斗", 200, false, true),
    
    /** 副本场景 - PVE内容，团队挑战 */
    DUNGEON("dungeon", "副本", "副本挑战区域，提供PVE内容", 50, false, false),
    
    /** 战场场景 - 大型PVP，阵营对战 */
    BATTLEFIELD("battlefield", "战场", "大型PVP战场，阵营对战", 200, false, true),
    
    /** 竞技场场景 - 小规模PVP，竞技对战 */
    ARENA("arena", "竞技场", "竞技场PVP，技能对战", 20, false, true),
    
    /** 实例化场景 - 私人空间，个人副本 */
    INSTANCE("instance", "实例场景", "实例化私人空间", 10, true, false);

    /** 场景类型代码 */
    private final String code;
    
    /** 场景类型名称 */
    private final String name;
    
    /** 场景类型描述 */
    private final String description;
    
    /** 默认最大人数 */
    private final int defaultMaxPlayers;
    
    /** 是否为安全区域 */
    private final boolean safeZone;
    
    /** 是否允许PVP */
    private final boolean pvpEnabled;

    /** 代码到类型的映射 */
    private static final Map<String, SceneType> CODE_MAP = new ConcurrentHashMap<>();

    static {
        // 初始化代码映射
        for (SceneType type : SceneType.values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    /**
     * 构造函数
     *
     * @param code 场景类型代码
     * @param name 场景类型名称  
     * @param description 场景类型描述
     * @param defaultMaxPlayers 默认最大玩家数
     * @param safeZone 是否为安全区域
     * @param pvpEnabled 是否允许PVP
     */
    SceneType(String code, String name, String description, int defaultMaxPlayers, boolean safeZone, boolean pvpEnabled) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.defaultMaxPlayers = defaultMaxPlayers;
        this.safeZone = safeZone;
        this.pvpEnabled = pvpEnabled;
    }

    /**
     * 获取场景类型代码
     *
     * @return 场景类型代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取场景类型名称
     *
     * @return 场景类型名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取场景类型描述
     *
     * @return 场景类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取默认最大玩家数
     *
     * @return 默认最大玩家数
     */
    public int getDefaultMaxPlayers() {
        return defaultMaxPlayers;
    }

    /**
     * 是否为安全区域
     *
     * @return 是否为安全区域
     */
    public boolean isSafeZone() {
        return safeZone;
    }

    /**
     * 是否允许PVP
     *
     * @return 是否允许PVP
     */
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    /**
     * 根据代码获取场景类型
     *
     * @param code 场景类型代码
     * @return 场景类型，如果不存在返回null
     */
    public static SceneType fromCode(String code) {
        return CODE_MAP.get(code);
    }

    /**
     * 检查场景类型是否存在
     *
     * @param code 场景类型代码
     * @return 是否存在
     */
    public static boolean exists(String code) {
        return CODE_MAP.containsKey(code);
    }

    /**
     * 获取所有场景类型代码
     *
     * @return 场景类型代码数组
     */
    public static String[] getAllCodes() {
        return CODE_MAP.keySet().toArray(new String[0]);
    }

    /**
     * 检查是否为PVP场景
     *
     * @return 是否为PVP场景
     */
    public boolean isPvpScene() {
        return pvpEnabled;
    }

    /**
     * 检查是否为副本类型场景
     *
     * @return 是否为副本类型场景
     */
    public boolean isDungeonType() {
        return this == DUNGEON || this == INSTANCE;
    }

    /**
     * 检查是否为开放世界场景
     *
     * @return 是否为开放世界场景
     */
    public boolean isOpenWorld() {
        return this == MAIN_CITY || this == FIELD;
    }

    /**
     * 检查是否为竞技类场景
     *
     * @return 是否为竞技类场景
     */
    public boolean isCompetitive() {
        return this == ARENA || this == BATTLEFIELD;
    }

    @Override
    public String toString() {
        return String.format("SceneType{code='%s', name='%s', maxPlayers=%d, safeZone=%s, pvp=%s}",
                code, name, defaultMaxPlayers, safeZone, pvpEnabled);
    }
}