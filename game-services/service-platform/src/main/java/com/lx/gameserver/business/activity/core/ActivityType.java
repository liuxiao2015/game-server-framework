/*
 * 文件名: ActivityType.java
 * 用途: 活动类型枚举定义
 * 实现内容:
 *   - 定义所有支持的活动类型
 *   - 提供类型描述和扩展支持
 *   - 支持活动类型查询和验证
 *   - 包含类型分组和优先级
 * 技术选型:
 *   - Java枚举类型
 *   - 支持扩展属性
 *   - 提供工具方法
 * 依赖关系:
 *   - 被Activity基类使用
 *   - 被活动管理器使用
 *   - 被活动工厂使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity.core;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 活动类型枚举
 * <p>
 * 定义游戏中所有支持的活动类型，每种类型具有不同的特性和行为。
 * 支持类型扩展和动态查询，为活动系统提供灵活的类型管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public enum ActivityType {
    
    /** 每日活动 - 每天重置的活动 */
    DAILY("daily", "每日活动", "每天重置的活动", 1),
    
    /** 每周活动 - 每周重置的活动 */
    WEEKLY("weekly", "每周活动", "每周重置的活动", 2),
    
    /** 限时活动 - 有明确开始和结束时间的活动 */
    LIMITED_TIME("limited_time", "限时活动", "有明确开始和结束时间的活动", 5),
    
    /** 节日活动 - 特定节日期间的活动 */
    FESTIVAL("festival", "节日活动", "特定节日期间的活动", 6),
    
    /** 运营活动 - 运营策划的特殊活动 */
    OPERATIONAL("operational", "运营活动", "运营策划的特殊活动", 7),
    
    /** 常驻活动 - 长期存在的活动 */
    PERMANENT("permanent", "常驻活动", "长期存在的活动", 3),
    
    /** 签到活动 - 日常签到类活动 */
    SIGN_IN("sign_in", "签到活动", "日常签到类活动", 1),
    
    /** 任务活动 - 完成任务获得奖励的活动 */
    TASK("task", "任务活动", "完成任务获得奖励的活动", 4),
    
    /** 充值活动 - 充值相关的活动 */
    RECHARGE("recharge", "充值活动", "充值相关的活动", 8),
    
    /** 消费活动 - 消费道具或货币的活动 */
    CONSUMPTION("consumption", "消费活动", "消费道具或货币的活动", 6),
    
    /** 竞赛活动 - 玩家之间竞争的活动 */
    COMPETITION("competition", "竞赛活动", "玩家之间竞争的活动", 9),
    
    /** 新手活动 - 新玩家专属活动 */
    NEWBIE("newbie", "新手活动", "新玩家专属活动", 10),
    
    /** 收集活动 - 收集指定物品的活动 */
    COLLECT("collect", "收集活动", "收集指定物品的活动", 5),
    
    /** 抽奖活动 - 随机抽奖类活动 */
    LOTTERY("lottery", "抽奖活动", "随机抽奖类活动", 7),
    
    /** 兑换活动 - 物品兑换类活动 */
    EXCHANGE("exchange", "兑换活动", "物品兑换类活动", 4),
    
    /** 排行榜活动 - 基于排行榜的活动 */
    RANK("rank", "排行榜活动", "基于排行榜的活动", 8);
    
    /** 活动类型代码 */
    private final String code;
    
    /** 活动类型名称 */
    private final String name;
    
    /** 活动类型描述 */
    private final String description;
    
    /** 活动优先级（数字越大优先级越高） */
    private final int priority;
    
    /** 类型映射缓存 */
    private static final Map<String, ActivityType> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(ActivityType::getCode, type -> type));
    
    /**
     * 构造函数
     *
     * @param code        活动类型代码
     * @param name        活动类型名称
     * @param description 活动类型描述
     * @param priority    活动优先级
     */
    ActivityType(String code, String name, String description, int priority) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.priority = priority;
    }
    
    /**
     * 根据代码获取活动类型
     *
     * @param code 活动类型代码
     * @return 活动类型，如果不存在返回null
     */
    public static ActivityType fromCode(String code) {
        return CODE_MAP.get(code);
    }
    
    /**
     * 检查活动类型是否存在
     *
     * @param code 活动类型代码
     * @return 是否存在
     */
    public static boolean exists(String code) {
        return CODE_MAP.containsKey(code);
    }
    
    /**
     * 获取所有活动类型代码
     *
     * @return 活动类型代码数组
     */
    public static String[] getAllCodes() {
        return CODE_MAP.keySet().toArray(new String[0]);
    }
    
    /**
     * 检查是否为周期性活动
     *
     * @return 是否为周期性活动
     */
    public boolean isPeriodic() {
        return this == DAILY || this == WEEKLY || this == SIGN_IN;
    }
    
    /**
     * 检查是否为限时活动
     *
     * @return 是否为限时活动
     */
    public boolean isTimeLimited() {
        return this == LIMITED_TIME || this == FESTIVAL || this == OPERATIONAL;
    }
    
    /**
     * 检查是否为常驻活动
     *
     * @return 是否为常驻活动
     */
    public boolean isPermanent() {
        return this == PERMANENT;
    }
    
    /**
     * 检查是否为竞争性活动
     *
     * @return 是否为竞争性活动
     */
    public boolean isCompetitive() {
        return this == COMPETITION || this == RANK;
    }
    
    /**
     * 获取活动类型代码
     *
     * @return 活动类型代码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 获取活动类型名称
     *
     * @return 活动类型名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取活动类型描述
     *
     * @return 活动类型描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取活动优先级
     *
     * @return 活动优先级
     */
    public int getPriority() {
        return priority;
    }
}