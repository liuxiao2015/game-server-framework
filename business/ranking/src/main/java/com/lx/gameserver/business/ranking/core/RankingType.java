/*
 * 文件名: RankingType.java
 * 用途: 排行榜类型枚举定义
 * 实现内容:
 *   - 定义所有支持的排行榜类型
 *   - 提供类型描述和扩展属性
 *   - 支持自定义排行榜类型扩展
 *   - 类型验证和查询功能
 * 技术选型:
 *   - 使用Java枚举实现类型安全
 *   - 支持动态属性配置
 *   - 提供类型转换和验证方法
 * 依赖关系:
 *   - 被排行榜核心组件使用
 *   - 被配置管理模块引用
 *   - 被API接口层调用
 */
package com.lx.gameserver.business.ranking.core;

/**
 * 排行榜类型枚举
 * <p>
 * 定义了游戏服务器支持的所有排行榜类型，包括基础排行榜
 * 和扩展排行榜。每个类型包含描述信息和默认配置参数。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public enum RankingType {

    /**
     * 等级排行榜
     * 按玩家等级进行排名，等级越高排名越前
     */
    LEVEL("等级排行榜", "按玩家等级进行排名", true, 100),

    /**
     * 战力排行榜
     * 按玩家战力值进行排名，战力越高排名越前
     */
    POWER("战力排行榜", "按玩家战力值进行排名", true, 100),

    /**
     * 竞技场排行榜
     * 按竞技场积分进行排名，积分越高排名越前
     */
    ARENA("竞技场排行榜", "按竞技场积分进行排名", true, 100),

    /**
     * 公会排行榜
     * 按公会等级和活跃度进行排名
     */
    GUILD("公会排行榜", "按公会等级和活跃度进行排名", true, 50),

    /**
     * 成就排行榜
     * 按玩家获得的成就点数进行排名
     */
    ACHIEVEMENT("成就排行榜", "按玩家获得的成就点数进行排名", true, 100),

    /**
     * 财富排行榜
     * 按玩家财富总值进行排名
     */
    WEALTH("财富排行榜", "按玩家财富总值进行排名", false, 100),

    /**
     * 充值排行榜
     * 按玩家充值金额进行排名
     */
    RECHARGE("充值排行榜", "按玩家充值金额进行排名", false, 100),

    /**
     * 消费排行榜
     * 按玩家消费金额进行排名
     */
    CONSUMPTION("消费排行榜", "按玩家消费金额进行排名", false, 100),

    /**
     * 每日活跃排行榜
     * 按每日活跃度进行排名
     */
    DAILY_ACTIVE("每日活跃排行榜", "按每日活跃度进行排名", true, 100),

    /**
     * 自定义排行榜
     * 用户自定义的排行榜类型，需要额外配置
     */
    CUSTOM("自定义排行榜", "用户自定义的排行榜类型", true, 100);

    /**
     * 排行榜名称
     */
    private final String name;

    /**
     * 排行榜描述
     */
    private final String description;

    /**
     * 是否启用
     */
    private final boolean enabled;

    /**
     * 默认榜单大小
     */
    private final int defaultSize;

    /**
     * 构造函数
     *
     * @param name        排行榜名称
     * @param description 排行榜描述
     * @param enabled     是否启用
     * @param defaultSize 默认榜单大小
     */
    RankingType(String name, String description, boolean enabled, int defaultSize) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.defaultSize = defaultSize;
    }

    /**
     * 获取排行榜名称
     *
     * @return 排行榜名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取排行榜描述
     *
     * @return 排行榜描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取默认榜单大小
     *
     * @return 默认榜单大小
     */
    public int getDefaultSize() {
        return defaultSize;
    }

    /**
     * 根据名称查找排行榜类型
     *
     * @param name 排行榜类型名称
     * @return 排行榜类型，如果不存在返回null
     */
    public static RankingType fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        for (RankingType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查排行榜类型是否有效
     *
     * @param type 排行榜类型
     * @return 是否有效
     */
    public static boolean isValid(RankingType type) {
        return type != null && type.enabled;
    }

    /**
     * 获取所有启用的排行榜类型
     *
     * @return 启用的排行榜类型数组
     */
    public static RankingType[] getEnabledTypes() {
        return java.util.Arrays.stream(values())
                .filter(type -> type.enabled)
                .toArray(RankingType[]::new);
    }

    /**
     * 是否为系统预定义类型
     *
     * @return 是否为系统预定义类型
     */
    public boolean isPredefined() {
        return this != CUSTOM;
    }

    /**
     * 是否支持赛季模式
     *
     * @return 是否支持赛季模式
     */
    public boolean supportsSeason() {
        return this == ARENA || this == POWER || this == ACHIEVEMENT;
    }

    /**
     * 是否需要实时更新
     *
     * @return 是否需要实时更新
     */
    public boolean requiresRealTimeUpdate() {
        return this == ARENA || this == POWER || this == LEVEL;
    }
}