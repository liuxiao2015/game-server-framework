/*
 * 文件名: PageResult.java
 * 用途: 分页查询结果封装类
 * 实现内容:
 *   - 封装分页查询的结果数据
 *   - 包含分页信息和数据列表
 *   - 支持泛型和链式构建
 *   - 提供分页计算相关方法
 * 技术选型:
 *   - 继承Result类复用基础功能
 *   - 添加分页相关字段和方法
 *   - 支持Builder模式构建
 * 依赖关系:
 *   - 继承Result基类
 *   - 被分页查询接口使用
 */
package com.lx.gameserver.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 分页查询结果封装类
 * <p>
 * 继承Result类，在基础返回结果的基础上增加分页相关信息。
 * 包含当前页码、每页大小、总记录数、总页数等分页信息，
 * 以及当前页的数据列表。
 * </p>
 *
 * @param <T> 数据类型
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResult<T> extends Result<List<T>> {

    /**
     * 当前页码（从1开始）
     */
    @JsonProperty("pageNum")
    private int pageNum;

    /**
     * 每页大小
     */
    @JsonProperty("pageSize")
    private int pageSize;

    /**
     * 总记录数
     */
    @JsonProperty("total")
    private long total;

    /**
     * 总页数
     */
    @JsonProperty("pages")
    private int pages;

    /**
     * 是否有上一页
     */
    @JsonProperty("hasPreviousPage")
    private boolean hasPreviousPage;

    /**
     * 是否有下一页
     */
    @JsonProperty("hasNextPage")
    private boolean hasNextPage;

    /**
     * 默认构造函数
     */
    public PageResult() {
        super();
    }

    /**
     * 构造函数
     *
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     * @param data     数据列表
     */
    public PageResult(int pageNum, int pageSize, long total, List<T> data) {
        super(ErrorCode.SUCCESS, data);
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.calculatePages();
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param pageNum   当前页码
     * @param pageSize  每页大小
     */
    public PageResult(ErrorCode errorCode, int pageNum, int pageSize) {
        super(errorCode);
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = 0;
        this.calculatePages();
    }

    // ===== 静态构造方法 =====

    /**
     * 构造成功的分页结果
     *
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     * @param data     数据列表
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> success(int pageNum, int pageSize, long total, List<T> data) {
        return new PageResult<>(pageNum, pageSize, total, data != null ? data : Collections.emptyList());
    }

    /**
     * 构造空的分页结果
     *
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(pageNum, pageSize, 0, Collections.emptyList());
    }

    /**
     * 构造失败的分页结果
     *
     * @param errorCode 错误码
     * @param pageNum   当前页码
     * @param pageSize  每页大小
     * @param <T>       数据类型
     * @return 失败分页结果
     */
    public static <T> PageResult<T> error(ErrorCode errorCode, int pageNum, int pageSize) {
        return new PageResult<>(errorCode, pageNum, pageSize);
    }

    /**
     * 构造失败的分页结果（自定义消息）
     *
     * @param code     状态码
     * @param message  错误消息
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      数据类型
     * @return 失败分页结果
     */
    public static <T> PageResult<T> error(int code, String message, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.pageNum = pageNum;
        result.pageSize = pageSize;
        result.total = 0;
        result.calculatePages();
        return result;
    }

    // ===== 分页计算方法 =====

    /**
     * 计算分页相关信息
     */
    private void calculatePages() {
        if (pageSize <= 0) {
            this.pages = 0;
            this.hasPreviousPage = false;
            this.hasNextPage = false;
            return;
        }

        this.pages = (int) Math.ceil((double) total / pageSize);
        this.hasPreviousPage = pageNum > 1;
        this.hasNextPage = pageNum < pages;
    }

    /**
     * 获取开始记录索引（从0开始）
     *
     * @return 开始记录索引
     */
    public int getStartIndex() {
        return Math.max(0, (pageNum - 1) * pageSize);
    }

    /**
     * 获取结束记录索引（从0开始，不包含）
     *
     * @return 结束记录索引
     */
    public int getEndIndex() {
        return Math.min((int) total, pageNum * pageSize);
    }

    /**
     * 获取当前页记录数
     *
     * @return 当前页记录数
     */
    public int getCurrentPageSize() {
        if (getData() == null) {
            return 0;
        }
        return getData().size();
    }

    /**
     * 判断是否为第一页
     *
     * @return 如果是第一页返回true，否则返回false
     */
    public boolean isFirstPage() {
        return pageNum == 1;
    }

    /**
     * 判断是否为最后一页
     *
     * @return 如果是最后一页返回true，否则返回false
     */
    public boolean isLastPage() {
        return pageNum == pages;
    }

    // ===== 链式设置方法 =====

    /**
     * 设置当前页码
     *
     * @param pageNum 当前页码
     * @return 当前对象
     */
    public PageResult<T> pageNum(int pageNum) {
        this.pageNum = pageNum;
        this.calculatePages();
        return this;
    }

    /**
     * 设置每页大小
     *
     * @param pageSize 每页大小
     * @return 当前对象
     */
    public PageResult<T> pageSize(int pageSize) {
        this.pageSize = pageSize;
        this.calculatePages();
        return this;
    }

    /**
     * 设置总记录数
     *
     * @param total 总记录数
     * @return 当前对象
     */
    public PageResult<T> total(long total) {
        this.total = total;
        this.calculatePages();
        return this;
    }

    // ===== Getter/Setter 方法 =====

    /**
     * 获取当前页码
     *
     * @return 当前页码
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * 设置当前页码
     *
     * @param pageNum 当前页码
     */
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
        this.calculatePages();
    }

    /**
     * 获取每页大小
     *
     * @return 每页大小
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页大小
     *
     * @param pageSize 每页大小
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        this.calculatePages();
    }

    /**
     * 获取总记录数
     *
     * @return 总记录数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 设置总记录数
     *
     * @param total 总记录数
     */
    public void setTotal(long total) {
        this.total = total;
        this.calculatePages();
    }

    /**
     * 获取总页数
     *
     * @return 总页数
     */
    public int getPages() {
        return pages;
    }

    /**
     * 是否有上一页
     *
     * @return 如果有上一页返回true，否则返回false
     */
    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    /**
     * 是否有下一页
     *
     * @return 如果有下一页返回true，否则返回false
     */
    public boolean isHasNextPage() {
        return hasNextPage;
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
        if (!super.equals(obj)) {
            return false;
        }
        PageResult<?> that = (PageResult<?>) obj;
        return pageNum == that.pageNum &&
                pageSize == that.pageSize &&
                total == that.total &&
                pages == that.pages &&
                hasPreviousPage == that.hasPreviousPage &&
                hasNextPage == that.hasNextPage;
    }

    /**
     * 计算哈希值
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pageNum, pageSize, total, pages, hasPreviousPage, hasNextPage);
    }

    /**
     * 转换为字符串
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return String.format(
                "PageResult{pageNum=%d, pageSize=%d, total=%d, pages=%d, " +
                        "hasPreviousPage=%s, hasNextPage=%s, code=%d, message='%s', dataSize=%d}",
                pageNum, pageSize, total, pages, hasPreviousPage, hasNextPage,
                getCode(), getMessage(), getCurrentPageSize());
    }
}