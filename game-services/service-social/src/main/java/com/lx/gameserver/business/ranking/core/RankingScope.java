/*
 * 文件名: RankingScope.java
 * 用途: 排行榜范围枚举定义
 * 实现内容:
 *   - 定义排行榜的适用范围
 *   - 支持全服、单服、区域、公会等范围
 *   - 提供范围验证和查询功能
 *   - 支持自定义范围扩展
 * 技术选型:
 *   - 使用Java枚举实现类型安全
 *   - 支持范围层级管理
 *   - 提供范围匹配和验证方法
 * 依赖关系:
 *   - 被排行榜核心组件使用
 *   - 被查询服务模块引用
 *   - 被配置管理模块调用
 */
package com.lx.gameserver.business.ranking.core;

/**
 * 排行榜范围枚举
 * <p>
 * 定义了排行榜的适用范围，支持不同级别的排行榜统计。
 * 范围从全服到好友，可以满足各种业务需求。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public enum RankingScope {

    /**
     * 全服排行榜
     * 所有服务器玩家参与的排行榜
     */
    GLOBAL("全服", "所有服务器玩家参与的排行榜", 1),

    /**
     * 单服排行榜
     * 单个服务器内玩家参与的排行榜
     */
    SERVER("单服", "单个服务器内玩家参与的排行榜", 2),

    /**
     * 区域排行榜
     * 特定区域内玩家参与的排行榜
     */
    REGION("区域", "特定区域内玩家参与的排行榜", 3),

    /**
     * 公会内排行榜
     * 公会成员内部的排行榜
     */
    GUILD("公会内", "公会成员内部的排行榜", 4),

    /**
     * 好友间排行榜
     * 好友之间的排行榜
     */
    FRIEND("好友间", "好友之间的排行榜", 5),

    /**
     * 战队内排行榜
     * 战队成员内部的排行榜
     */
    TEAM("战队内", "战队成员内部的排行榜", 4),

    /**
     * 跨服排行榜
     * 跨服务器的特定排行榜
     */
    CROSS_SERVER("跨服", "跨服务器的特定排行榜", 1),

    /**
     * 自定义范围
     * 用户自定义的排行榜范围
     */
    CUSTOM("自定义", "用户自定义的排行榜范围", 10);

    /**
     * 范围名称
     */
    private final String name;

    /**
     * 范围描述
     */
    private final String description;

    /**
     * 范围级别（数字越小范围越大）
     */
    private final int level;

    /**
     * 构造函数
     *
     * @param name        范围名称
     * @param description 范围描述
     * @param level       范围级别
     */
    RankingScope(String name, String description, int level) {
        this.name = name;
        this.description = description;
        this.level = level;
    }

    /**
     * 获取范围名称
     *
     * @return 范围名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取范围描述
     *
     * @return 范围描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取范围级别
     *
     * @return 范围级别
     */
    public int getLevel() {
        return level;
    }

    /**
     * 根据名称查找排行榜范围
     *
     * @param name 范围名称
     * @return 排行榜范围，如果不存在返回null
     */
    public static RankingScope fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        for (RankingScope scope : values()) {
            if (scope.name().equalsIgnoreCase(name.trim())) {
                return scope;
            }
        }
        return null;
    }

    /**
     * 检查范围是否有效
     *
     * @param scope 排行榜范围
     * @return 是否有效
     */
    public static boolean isValid(RankingScope scope) {
        return scope != null;
    }

    /**
     * 比较范围大小
     *
     * @param other 另一个范围
     * @return 比较结果：负数表示当前范围更大，正数表示更小，0表示相等
     */
    public int compareLevel(RankingScope other) {
        if (other == null) {
            return -1;
        }
        return Integer.compare(this.level, other.level);
    }

    /**
     * 判断当前范围是否包含另一个范围
     *
     * @param other 另一个范围
     * @return 是否包含
     */
    public boolean contains(RankingScope other) {
        return other != null && this.level <= other.level;
    }

    /**
     * 是否为全局范围
     *
     * @return 是否为全局范围
     */
    public boolean isGlobal() {
        return this == GLOBAL || this == CROSS_SERVER;
    }

    /**
     * 是否为服务器级别范围
     *
     * @return 是否为服务器级别范围
     */
    public boolean isServerLevel() {
        return this == SERVER || this == REGION;
    }

    /**
     * 是否为组织级别范围
     *
     * @return 是否为组织级别范围
     */
    public boolean isOrganizationLevel() {
        return this == GUILD || this == TEAM;
    }

    /**
     * 是否为个人级别范围
     *
     * @return 是否为个人级别范围
     */
    public boolean isPersonalLevel() {
        return this == FRIEND;
    }

    /**
     * 获取默认缓存时间（秒）
     *
     * @return 缓存时间
     */
    public int getDefaultCacheTime() {
        switch (this) {
            case GLOBAL:
            case CROSS_SERVER:
                return 300; // 5分钟
            case SERVER:
            case REGION:
                return 180; // 3分钟
            case GUILD:
            case TEAM:
                return 120; // 2分钟
            case FRIEND:
                return 60;  // 1分钟
            default:
                return 300; // 默认5分钟
        }
    }

    /**
     * 获取建议的榜单大小
     *
     * @return 建议的榜单大小
     */
    public int getSuggestedSize() {
        switch (this) {
            case GLOBAL:
            case CROSS_SERVER:
                return 1000;
            case SERVER:
            case REGION:
                return 500;
            case GUILD:
            case TEAM:
                return 100;
            case FRIEND:
                return 50;
            default:
                return 100;
        }
    }
}