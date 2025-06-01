/*
 * 文件名: ErrorCode.java
 * 用途: 游戏服务器统一错误码枚举定义
 * 实现内容:
 *   - 定义系统级和业务级错误码
 *   - 提供错误码到错误信息的映射
 *   - 支持国际化错误信息
 *   - 便于前后端统一错误处理
 * 技术选型:
 *   - Java枚举类型确保类型安全
 *   - 支持错误码分类管理
 *   - 提供便捷的错误码查询方法
 * 依赖关系:
 *   - 无外部依赖，作为基础错误码定义
 *   - 被其他模块引用用于错误处理
 */
package com.lx.gameserver.common;

/**
 * 游戏服务器统一错误码枚举
 * <p>
 * 统一定义系统和业务错误码，便于前后端联调和错误处理。
 * 错误码分为几个区间：
 * - 0: 成功
 * - 1000-1999: 系统级错误
 * - 2000-2999: 用户相关错误
 * - 3000-3999: 游戏业务错误
 * - 4000-4999: 网络通信错误
 * - 5000-5999: 数据库相关错误
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public enum ErrorCode {

    // ===== 成功状态 =====
    /**
     * 操作成功
     */
    SUCCESS(0, "操作成功"),

    // ===== 系统级错误 1000-1999 =====
    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(1000, "系统内部错误"),
    
    /**
     * 参数校验失败
     */
    PARAM_INVALID(1001, "参数校验失败"),
    
    /**
     * 方法不支持
     */
    METHOD_NOT_SUPPORTED(1002, "方法不支持"),
    
    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(1003, "服务不可用"),
    
    /**
     * 请求频率过高
     */
    RATE_LIMIT_EXCEEDED(1004, "请求频率过高"),
    
    /**
     * 配置错误
     */
    CONFIG_ERROR(1005, "配置错误"),

    // ===== 用户相关错误 2000-2999 =====
    /**
     * 用户未登录
     */
    USER_NOT_LOGIN(2000, "用户未登录"),
    
    /**
     * 用户不存在
     */
    USER_NOT_EXIST(2001, "用户不存在"),
    
    /**
     * 用户名或密码错误
     */
    USER_LOGIN_FAILED(2002, "用户名或密码错误"),
    
    /**
     * 用户已被禁用
     */
    USER_DISABLED(2003, "用户已被禁用"),
    
    /**
     * 权限不足
     */
    PERMISSION_DENIED(2004, "权限不足"),
    
    /**
     * 用户名已存在
     */
    USERNAME_EXISTS(2005, "用户名已存在"),
    
    /**
     * 密码强度不够
     */
    PASSWORD_TOO_WEAK(2006, "密码强度不够"),
    
    /**
     * 验证码错误
     */
    CAPTCHA_ERROR(2007, "验证码错误"),
    
    /**
     * 用户已在线
     */
    USER_ALREADY_ONLINE(2008, "用户已在线"),

    // ===== 游戏业务错误 3000-3999 =====
    /**
     * 角色不存在
     */
    PLAYER_NOT_EXIST(3000, "角色不存在"),
    
    /**
     * 角色名已存在
     */
    PLAYER_NAME_EXISTS(3001, "角色名已存在"),
    
    /**
     * 等级不足
     */
    LEVEL_NOT_ENOUGH(3002, "等级不足"),
    
    /**
     * 金币不足
     */
    GOLD_NOT_ENOUGH(3003, "金币不足"),
    
    /**
     * 道具不足
     */
    ITEM_NOT_ENOUGH(3004, "道具不足"),
    
    /**
     * 背包已满
     */
    INVENTORY_FULL(3005, "背包已满"),
    
    /**
     * 不在同一场景
     */
    NOT_IN_SAME_SCENE(3006, "不在同一场景"),
    
    /**
     * 场景已满员
     */
    SCENE_FULL(3007, "场景已满员"),
    
    /**
     * 聊天被禁言
     */
    CHAT_MUTED(3008, "聊天被禁言"),
    
    /**
     * 功能尚未开放
     */
    FEATURE_NOT_OPEN(3009, "功能尚未开放"),

    // ===== 网络通信错误 4000-4999 =====
    /**
     * 网络连接超时
     */
    NETWORK_TIMEOUT(4000, "网络连接超时"),
    
    /**
     * 消息格式错误
     */
    MESSAGE_FORMAT_ERROR(4001, "消息格式错误"),
    
    /**
     * 消息序列号错误
     */
    MESSAGE_SEQ_ERROR(4002, "消息序列号错误"),
    
    /**
     * 连接已断开
     */
    CONNECTION_CLOSED(4003, "连接已断开"),
    
    /**
     * 协议版本不匹配
     */
    PROTOCOL_VERSION_MISMATCH(4004, "协议版本不匹配"),

    // ===== 数据库相关错误 5000-5999 =====
    /**
     * 数据库连接失败
     */
    DATABASE_CONNECTION_FAILED(5000, "数据库连接失败"),
    
    /**
     * 数据保存失败
     */
    DATA_SAVE_FAILED(5001, "数据保存失败"),
    
    /**
     * 数据不存在
     */
    DATA_NOT_FOUND(5002, "数据不存在"),
    
    /**
     * 数据已存在
     */
    DATA_ALREADY_EXISTS(5003, "数据已存在"),
    
    /**
     * 数据版本冲突
     */
    DATA_VERSION_CONFLICT(5004, "数据版本冲突"),
    
    /**
     * 缓存操作失败
     */
    CACHE_OPERATION_FAILED(5005, "缓存操作失败");

    /**
     * 错误码
     */
    private final int code;
    
    /**
     * 错误信息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param code    错误码
     * @param message 错误信息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 判断是否为成功状态
     *
     * @return 如果是成功状态返回true，否则返回false
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 判断是否为系统错误
     *
     * @return 如果是系统错误返回true，否则返回false
     */
    public boolean isSystemError() {
        return code >= 1000 && code < 2000;
    }

    /**
     * 判断是否为用户相关错误
     *
     * @return 如果是用户相关错误返回true，否则返回false
     */
    public boolean isUserError() {
        return code >= 2000 && code < 3000;
    }

    /**
     * 判断是否为业务错误
     *
     * @return 如果是业务错误返回true，否则返回false
     */
    public boolean isBusinessError() {
        return code >= 3000 && code < 4000;
    }

    /**
     * 根据错误码查找对应的ErrorCode枚举
     *
     * @param code 错误码
     * @return 对应的ErrorCode枚举，如果未找到则返回null
     */
    public static ErrorCode valueOf(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }

    /**
     * 格式化错误信息
     *
     * @param args 格式化参数
     * @return 格式化后的错误信息
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }

    /**
     * 返回字符串表示
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return String.format("ErrorCode{code=%d, message='%s'}", code, message);
    }
}