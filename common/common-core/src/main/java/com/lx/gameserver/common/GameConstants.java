/*
 * 文件名: GameConstants.java
 * 用途: 游戏服务器全局常量定义
 * 实现内容:
 *   - 定义游戏相关的全局常量
 *   - 包含系统配置、限制参数、默认值等
 *   - 提供统一的常量管理
 *   - 便于配置维护和调整
 * 技术选型:
 *   - 使用public static final定义常量
 *   - 按功能模块分组管理常量
 *   - 提供常量的详细注释说明
 * 依赖关系:
 *   - 无外部依赖，作为基础常量定义
 *   - 被所有需要使用常量的模块引用
 */
package com.lx.gameserver.common;

/**
 * 游戏服务器全局常量类
 * <p>
 * 统一管理游戏服务器中使用的各种常量，包括系统配置、
 * 游戏规则参数、限制值等。按功能模块分组便于维护。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public final class GameConstants {

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private GameConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    // ===== 系统配置常量 =====
    
    /**
     * 系统名称
     */
    public static final String SYSTEM_NAME = "游戏服务器框架";
    
    /**
     * 系统版本号
     */
    public static final String SYSTEM_VERSION = "1.0.0";
    
    /**
     * 默认字符编码
     */
    public static final String DEFAULT_CHARSET = "UTF-8";
    
    /**
     * 默认时区
     */
    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    // ===== 网络配置常量 =====
    
    /**
     * 默认服务器端口
     */
    public static final int DEFAULT_SERVER_PORT = 8080;
    
    /**
     * 最大连接数
     */
    public static final int MAX_CONNECTIONS = 10000;
    
    /**
     * 连接超时时间（毫秒）
     */
    public static final long CONNECTION_TIMEOUT = 30000L;
    
    /**
     * 心跳间隔（毫秒）
     */
    public static final long HEARTBEAT_INTERVAL = 30000L;
    
    /**
     * 最大消息大小（字节）
     */
    public static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB

    // ===== 玩家相关常量 =====
    
    /**
     * 最大同时在线玩家数
     */
    public static final int MAX_ONLINE_PLAYERS = 5000;
    
    /**
     * 玩家名称最小长度
     */
    public static final int PLAYER_NAME_MIN_LENGTH = 2;
    
    /**
     * 玩家名称最大长度
     */
    public static final int PLAYER_NAME_MAX_LENGTH = 16;
    
    /**
     * 密码最小长度
     */
    public static final int PASSWORD_MIN_LENGTH = 6;
    
    /**
     * 密码最大长度
     */
    public static final int PASSWORD_MAX_LENGTH = 32;
    
    /**
     * 玩家初始等级
     */
    public static final int PLAYER_INITIAL_LEVEL = 1;
    
    /**
     * 玩家最大等级
     */
    public static final int PLAYER_MAX_LEVEL = 100;
    
    /**
     * 玩家初始金币
     */
    public static final long PLAYER_INITIAL_GOLD = 1000L;
    
    /**
     * 玩家金币上限
     */
    public static final long PLAYER_MAX_GOLD = 999999999L;

    // ===== 背包道具常量 =====
    
    /**
     * 背包默认容量
     */
    public static final int INVENTORY_DEFAULT_SIZE = 50;
    
    /**
     * 背包最大容量
     */
    public static final int INVENTORY_MAX_SIZE = 200;
    
    /**
     * 道具默认堆叠上限
     */
    public static final int ITEM_DEFAULT_STACK_SIZE = 99;
    
    /**
     * 道具最大堆叠上限
     */
    public static final int ITEM_MAX_STACK_SIZE = 9999;
    
    /**
     * 装备最大强化等级
     */
    public static final int EQUIPMENT_MAX_ENHANCE_LEVEL = 15;

    // ===== 聊天系统常量 =====
    
    /**
     * 聊天消息最大长度
     */
    public static final int CHAT_MESSAGE_MAX_LENGTH = 200;
    
    /**
     * 聊天历史记录保存天数
     */
    public static final int CHAT_HISTORY_KEEP_DAYS = 7;
    
    /**
     * 世界聊天冷却时间（毫秒）
     */
    public static final long WORLD_CHAT_COOLDOWN = 10000L;
    
    /**
     * 私聊冷却时间（毫秒）
     */
    public static final long PRIVATE_CHAT_COOLDOWN = 1000L;
    
    /**
     * 禁言最大时长（小时）
     */
    public static final int MAX_MUTE_HOURS = 168; // 7天

    // ===== 场景相关常量 =====
    
    /**
     * 场景最大玩家容量
     */
    public static final int SCENE_MAX_PLAYERS = 100;
    
    /**
     * 场景视野范围（像素）
     */
    public static final int SCENE_VIEW_RANGE = 1000;
    
    /**
     * 玩家移动同步间隔（毫秒）
     */
    public static final long MOVEMENT_SYNC_INTERVAL = 100L;
    
    /**
     * 场景数据保存间隔（毫秒）
     */
    public static final long SCENE_SAVE_INTERVAL = 10000L;

    // ===== 排行榜常量 =====
    
    /**
     * 排行榜最大显示数量
     */
    public static final int RANK_MAX_SIZE = 100;
    
    /**
     * 排行榜更新间隔（毫秒）
     */
    public static final long RANK_UPDATE_INTERVAL = 60000L;
    
    /**
     * 排行榜数据保存天数
     */
    public static final int RANK_DATA_KEEP_DAYS = 30;

    // ===== 活动系统常量 =====
    
    /**
     * 活动最大参与次数
     */
    public static final int ACTIVITY_MAX_PARTICIPATE_COUNT = 10;
    
    /**
     * 活动奖励领取超时时间（小时）
     */
    public static final int ACTIVITY_REWARD_TIMEOUT_HOURS = 24;
    
    /**
     * 活动数据检查间隔（毫秒）
     */
    public static final long ACTIVITY_CHECK_INTERVAL = 60000L;

    // ===== 支付相关常量 =====
    
    /**
     * 支付订单超时时间（分钟）
     */
    public static final int PAYMENT_ORDER_TIMEOUT_MINUTES = 15;
    
    /**
     * 支付单笔最小金额（分）
     */
    public static final int PAYMENT_MIN_AMOUNT = 100; // 1元
    
    /**
     * 支付单笔最大金额（分）
     */
    public static final int PAYMENT_MAX_AMOUNT = 100000; // 1000元
    
    /**
     * 支付重试最大次数
     */
    public static final int PAYMENT_MAX_RETRY_COUNT = 3;

    // ===== 缓存相关常量 =====
    
    /**
     * 默认缓存过期时间（秒）
     */
    public static final long DEFAULT_CACHE_EXPIRE_SECONDS = 3600L; // 1小时
    
    /**
     * 玩家数据缓存过期时间（秒）
     */
    public static final long PLAYER_CACHE_EXPIRE_SECONDS = 1800L; // 30分钟
    
    /**
     * 聊天记录缓存过期时间（秒）
     */
    public static final long CHAT_CACHE_EXPIRE_SECONDS = 600L; // 10分钟
    
    /**
     * 排行榜缓存过期时间（秒）
     */
    public static final long RANK_CACHE_EXPIRE_SECONDS = 300L; // 5分钟

    // ===== 数据库相关常量 =====
    
    /**
     * 数据库连接池最大连接数
     */
    public static final int DB_MAX_POOL_SIZE = 50;
    
    /**
     * 数据库连接池最小连接数
     */
    public static final int DB_MIN_POOL_SIZE = 5;
    
    /**
     * 数据库查询超时时间（秒）
     */
    public static final int DB_QUERY_TIMEOUT_SECONDS = 30;
    
    /**
     * 批量操作最大数量
     */
    public static final int DB_BATCH_MAX_SIZE = 1000;

    // ===== 线程池相关常量 =====
    
    /**
     * IO线程池核心线程数
     */
    public static final int IO_THREAD_POOL_CORE_SIZE = 8;
    
    /**
     * IO线程池最大线程数
     */
    public static final int IO_THREAD_POOL_MAX_SIZE = 32;
    
    /**
     * 业务线程池核心线程数
     */
    public static final int BUSINESS_THREAD_POOL_CORE_SIZE = 16;
    
    /**
     * 业务线程池最大线程数
     */
    public static final int BUSINESS_THREAD_POOL_MAX_SIZE = 64;
    
    /**
     * 线程池队列容量
     */
    public static final int THREAD_POOL_QUEUE_CAPACITY = 1000;

    // ===== 时间相关常量 =====
    
    /**
     * 一秒的毫秒数
     */
    public static final long MILLIS_PER_SECOND = 1000L;
    
    /**
     * 一分钟的毫秒数
     */
    public static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    
    /**
     * 一小时的毫秒数
     */
    public static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    
    /**
     * 一天的毫秒数
     */
    public static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;
    
    /**
     * 一周的毫秒数
     */
    public static final long MILLIS_PER_WEEK = 7 * MILLIS_PER_DAY;

    // ===== 正则表达式常量 =====
    
    /**
     * 用户名正则表达式（2-16位字母数字中文）
     */
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9\\u4e00-\\u9fa5]{2,16}$";
    
    /**
     * 密码正则表达式（6-32位字母数字特殊字符）
     */
    public static final String PASSWORD_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{6,32}$";
    
    /**
     * 邮箱正则表达式
     */
    public static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    
    /**
     * 手机号正则表达式
     */
    public static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
}