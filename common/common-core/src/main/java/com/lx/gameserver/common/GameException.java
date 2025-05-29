/*
 * 文件名: GameException.java
 * 用途: 游戏服务器统一异常基类
 * 实现内容:
 *   - 封装错误码和异常信息
 *   - 支持异常链和根因分析
 *   - 提供便捷的异常构造方法
 *   - 集成全局异常处理机制
 * 技术选型:
 *   - 继承RuntimeException支持非检查异常
 *   - 集成ErrorCode枚举
 *   - 支持异常消息格式化
 * 依赖关系:
 *   - 依赖ErrorCode枚举类
 *   - 被全局异常处理器使用
 */
package com.lx.gameserver.common;

/**
 * 游戏服务器统一异常基类
 * <p>
 * 继承RuntimeException，作为游戏服务器中所有业务异常的基类。
 * 携带错误码和详细错误信息，便于全局异常处理和错误码统一管理。
 * 支持异常链，可以包装其他异常。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public class GameException extends RuntimeException {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final ErrorCode errorCode;

    /**
     * 错误参数（用于格式化错误消息）
     */
    private final Object[] args;

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     */
    public GameException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param args      错误消息格式化参数
     */
    public GameException(ErrorCode errorCode, Object... args) {
        super(formatMessage(errorCode.getMessage(), args));
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param cause     原始异常
     */
    public GameException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param cause     原始异常
     * @param args      错误消息格式化参数
     */
    public GameException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(formatMessage(errorCode.getMessage(), args), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 构造函数（自定义错误消息）
     *
     * @param errorCode 错误码
     * @param message   自定义错误消息
     */
    public GameException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 构造函数（自定义错误消息）
     *
     * @param errorCode 错误码
     * @param message   自定义错误消息
     * @param cause     原始异常
     */
    public GameException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    // ===== 静态工厂方法 =====

    /**
     * 创建系统错误异常
     *
     * @return 系统错误异常
     */
    public static GameException systemError() {
        return new GameException(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 创建系统错误异常
     *
     * @param message 错误消息
     * @return 系统错误异常
     */
    public static GameException systemError(String message) {
        return new GameException(ErrorCode.SYSTEM_ERROR, message);
    }

    /**
     * 创建系统错误异常
     *
     * @param cause 原始异常
     * @return 系统错误异常
     */
    public static GameException systemError(Throwable cause) {
        return new GameException(ErrorCode.SYSTEM_ERROR, cause);
    }

    /**
     * 创建系统错误异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     * @return 系统错误异常
     */
    public static GameException systemError(String message, Throwable cause) {
        return new GameException(ErrorCode.SYSTEM_ERROR, message, cause);
    }

    /**
     * 创建参数错误异常
     *
     * @param paramName 参数名
     * @return 参数错误异常
     */
    public static GameException paramError(String paramName) {
        return new GameException(ErrorCode.PARAM_INVALID, "参数 " + paramName + " 无效");
    }

    /**
     * 创建参数错误异常
     *
     * @param paramName 参数名
     * @param value     参数值
     * @return 参数错误异常
     */
    public static GameException paramError(String paramName, Object value) {
        return new GameException(ErrorCode.PARAM_INVALID, 
                String.format("参数 %s 的值 %s 无效", paramName, value));
    }

    /**
     * 创建用户未登录异常
     *
     * @return 用户未登录异常
     */
    public static GameException userNotLogin() {
        return new GameException(ErrorCode.USER_NOT_LOGIN);
    }

    /**
     * 创建用户不存在异常
     *
     * @param userId 用户ID
     * @return 用户不存在异常
     */
    public static GameException userNotExist(Object userId) {
        return new GameException(ErrorCode.USER_NOT_EXIST, "用户 " + userId + " 不存在");
    }

    /**
     * 创建权限不足异常
     *
     * @return 权限不足异常
     */
    public static GameException permissionDenied() {
        return new GameException(ErrorCode.PERMISSION_DENIED);
    }

    /**
     * 创建权限不足异常
     *
     * @param resource 资源名称
     * @return 权限不足异常
     */
    public static GameException permissionDenied(String resource) {
        return new GameException(ErrorCode.PERMISSION_DENIED, "无权限访问资源: " + resource);
    }

    /**
     * 创建数据不存在异常
     *
     * @param dataType 数据类型
     * @param id       数据ID
     * @return 数据不存在异常
     */
    public static GameException dataNotFound(String dataType, Object id) {
        return new GameException(ErrorCode.DATA_NOT_FOUND, 
                String.format("%s[%s] 不存在", dataType, id));
    }

    /**
     * 创建业务异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @return 业务异常
     */
    public static GameException business(ErrorCode errorCode, String message) {
        return new GameException(errorCode, message);
    }

    // ===== 工具方法 =====

    /**
     * 格式化错误消息
     *
     * @param message 消息模板
     * @param args    格式化参数
     * @return 格式化后的消息
     */
    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(message, args);
        } catch (Exception e) {
            // 格式化失败时返回原始消息
            return message;
        }
    }

    /**
     * 获取完整的错误信息
     *
     * @return 完整错误信息
     */
    public String getFullMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s", errorCode.name(), getMessage()));
        
        if (getCause() != null) {
            sb.append(" <- ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }

    /**
     * 获取错误详情（包含堆栈信息）
     *
     * @return 错误详情
     */
    public String getErrorDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFullMessage());
        sb.append("\n错误码: ").append(errorCode.getCode());
        sb.append("\n错误类型: ").append(errorCode.name());
        
        if (args != null && args.length > 0) {
            sb.append("\n错误参数: ");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(args[i]);
            }
        }
        
        return sb.toString();
    }

    // ===== Getter 方法 =====

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误码的数值
     *
     * @return 错误码数值
     */
    public int getCode() {
        return errorCode.getCode();
    }

    /**
     * 获取错误参数
     *
     * @return 错误参数数组
     */
    public Object[] getArgs() {
        return args != null ? args.clone() : null;
    }

    /**
     * 判断是否为系统错误
     *
     * @return 如果是系统错误返回true，否则返回false
     */
    public boolean isSystemError() {
        return errorCode.isSystemError();
    }

    /**
     * 判断是否为用户相关错误
     *
     * @return 如果是用户相关错误返回true，否则返回false
     */
    public boolean isUserError() {
        return errorCode.isUserError();
    }

    /**
     * 判断是否为业务错误
     *
     * @return 如果是业务错误返回true，否则返回false
     */
    public boolean isBusinessError() {
        return errorCode.isBusinessError();
    }

    // ===== 重写方法 =====

    /**
     * 返回字符串表示
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return String.format("GameException{errorCode=%s, message='%s'}", 
                errorCode, getMessage());
    }
}