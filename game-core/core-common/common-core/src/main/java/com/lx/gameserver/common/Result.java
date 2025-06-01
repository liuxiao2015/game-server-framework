/*
 * 文件名: Result.java
 * 用途: 通用接口返回结果封装类
 * 实现内容:
 *   - 统一封装接口返回结果
 *   - 包含状态码、消息和数据
 *   - 支持泛型和链式构建
 *   - 提供便捷的成功和失败构造方法
 * 技术选型:
 *   - 使用泛型支持不同数据类型
 *   - 支持Builder模式和链式调用
 *   - 集成ErrorCode枚举
 * 依赖关系:
 *   - 依赖ErrorCode枚举类
 *   - 被所有需要返回结果的接口使用
 */
package com.lx.gameserver.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 通用接口返回结果封装类
 * <p>
 * 统一封装所有接口的返回结果，包含状态码、消息和数据。
 * 支持泛型，可以封装任意类型的数据。提供便捷的静态方法
 * 来构造成功和失败的结果。
 * </p>
 *
 * @param <T> 数据类型
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /**
     * 状态码
     */
    @JsonProperty("code")
    private int code;

    /**
     * 返回消息
     */
    @JsonProperty("message")
    private String message;

    /**
     * 返回数据
     */
    @JsonProperty("data")
    private T data;

    /**
     * 时间戳
     */
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * 默认构造函数
     */
    public Result() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 构造函数
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     */
    public Result(int code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码枚举
     * @param data      数据
     */
    public Result(ErrorCode errorCode, T data) {
        this(errorCode.getCode(), errorCode.getMessage(), data);
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码枚举
     */
    public Result(ErrorCode errorCode) {
        this(errorCode, null);
    }

    // ===== 静态构造方法 =====

    /**
     * 构造成功结果
     *
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.SUCCESS);
    }

    /**
     * 构造成功结果（带数据）
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS, data);
    }

    /**
     * 构造成功结果（带消息和数据）
     *
     * @param message 消息
     * @param data    数据
     * @param <T>     数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 构造失败结果
     *
     * @param errorCode 错误码
     * @param <T>       数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode);
    }

    /**
     * 构造失败结果（带数据）
     *
     * @param errorCode 错误码
     * @param data      数据
     * @param <T>       数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(ErrorCode errorCode, T data) {
        return new Result<>(errorCode, data);
    }

    /**
     * 构造失败结果（自定义消息）
     *
     * @param code    状态码
     * @param message 消息
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 构造失败结果（自定义消息和数据）
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> error(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /**
     * 构造系统错误结果
     *
     * @param <T> 数据类型
     * @return 系统错误结果
     */
    public static <T> Result<T> systemError() {
        return error(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 构造系统错误结果（带消息）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 系统错误结果
     */
    public static <T> Result<T> systemError(String message) {
        return error(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }

    /**
     * 构造参数错误结果
     *
     * @param <T> 数据类型
     * @return 参数错误结果
     */
    public static <T> Result<T> paramError() {
        return error(ErrorCode.PARAM_INVALID);
    }

    /**
     * 构造参数错误结果（带消息）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 参数错误结果
     */
    public static <T> Result<T> paramError(String message) {
        return error(ErrorCode.PARAM_INVALID.getCode(), message);
    }

    // ===== 链式设置方法 =====

    /**
     * 设置状态码
     *
     * @param code 状态码
     * @return 当前对象
     */
    public Result<T> code(int code) {
        this.code = code;
        return this;
    }

    /**
     * 设置消息
     *
     * @param message 消息
     * @return 当前对象
     */
    public Result<T> message(String message) {
        this.message = message;
        return this;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     * @return 当前对象
     */
    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

    // ===== 判断方法 =====

    /**
     * 判断是否成功
     *
     * @return 如果成功返回true，否则返回false
     */
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }

    /**
     * 判断是否失败
     *
     * @return 如果失败返回true，否则返回false
     */
    public boolean isError() {
        return !isSuccess();
    }

    /**
     * 判断是否有数据
     *
     * @return 如果有数据返回true，否则返回false
     */
    public boolean hasData() {
        return data != null;
    }

    // ===== Getter/Setter 方法 =====

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置状态码
     *
     * @param code 状态码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取消息
     *
     * @return 消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置消息
     *
     * @param message 消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 获取时间戳
     *
     * @return 时间戳
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * 设置时间戳
     *
     * @param timestamp 时间戳
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    // ===== 重写方法 =====

    /**
     * 判断对象是否相等
     *
     * @param obj 比较对象
     * @return 如果相等返回true，否则返回false
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Result<?> result = (Result<?>) obj;
        return code == result.code &&
                Objects.equals(message, result.message) &&
                Objects.equals(data, result.data) &&
                Objects.equals(timestamp, result.timestamp);
    }

    /**
     * 计算哈希值
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(code, message, data, timestamp);
    }

    /**
     * 转换为字符串
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return String.format("Result{code=%d, message='%s', data=%s, timestamp='%s'}",
                code, message, data, timestamp);
    }
}