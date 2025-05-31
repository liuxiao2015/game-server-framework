/*
 * 文件名: DataPersistence.java
 * 用途: 数据持久化接口
 * 实现内容:
 *   - 数据保存策略和加载机制
 *   - 数据版本管理和增量更新
 *   - 容错处理和数据恢复
 *   - 批量操作和事务支持
 *   - 缓存集成和性能优化
 * 技术选型:
 *   - 接口抽象定义持久化规范
 *   - 支持多种存储后端
 *   - 版本控制和数据迁移
 *   - 异步操作和批量处理
 * 依赖关系:
 *   - 被各个数据存储模块实现
 *   - 与缓存系统集成优化性能
 *   - 支持事务和一致性保证
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.extension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 数据持久化接口
 * <p>
 * 定义了游戏数据持久化的标准接口，支持保存、加载、
 * 查询、事务等功能，提供灵活的数据存储抽象。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface DataPersistence {

    /**
     * 数据操作类型
     */
    enum OperationType {
        /** 创建 */
        CREATE,
        /** 读取 */
        READ,
        /** 更新 */
        UPDATE,
        /** 删除 */
        DELETE,
        /** 批量操作 */
        BATCH
    }

    /**
     * 数据一致性级别
     */
    enum ConsistencyLevel {
        /** 最终一致性 */
        EVENTUAL,
        /** 弱一致性 */
        WEAK,
        /** 强一致性 */
        STRONG,
        /** 线性一致性 */
        LINEARIZABLE
    }

    /**
     * 事务隔离级别
     */
    enum IsolationLevel {
        /** 读未提交 */
        READ_UNCOMMITTED,
        /** 读已提交 */
        READ_COMMITTED,
        /** 可重复读 */
        REPEATABLE_READ,
        /** 串行化 */
        SERIALIZABLE
    }

    /**
     * 数据版本信息
     */
    interface DataVersion {
        /**
         * 获取版本号
         */
        long getVersion();

        /**
         * 获取创建时间
         */
        LocalDateTime getCreateTime();

        /**
         * 获取修改时间
         */
        LocalDateTime getModifyTime();

        /**
         * 获取修改者
         */
        String getModifier();

        /**
         * 获取校验和
         */
        String getChecksum();
    }

    /**
     * 查询条件
     */
    interface QueryCondition {
        /**
         * 添加等于条件
         */
        QueryCondition eq(String field, Object value);

        /**
         * 添加不等于条件
         */
        QueryCondition ne(String field, Object value);

        /**
         * 添加大于条件
         */
        QueryCondition gt(String field, Object value);

        /**
         * 添加大于等于条件
         */
        QueryCondition gte(String field, Object value);

        /**
         * 添加小于条件
         */
        QueryCondition lt(String field, Object value);

        /**
         * 添加小于等于条件
         */
        QueryCondition lte(String field, Object value);

        /**
         * 添加包含条件
         */
        QueryCondition in(String field, Collection<?> values);

        /**
         * 添加不包含条件
         */
        QueryCondition notIn(String field, Collection<?> values);

        /**
         * 添加模糊匹配条件
         */
        QueryCondition like(String field, String pattern);

        /**
         * 添加范围条件
         */
        QueryCondition between(String field, Object start, Object end);

        /**
         * 添加空值条件
         */
        QueryCondition isNull(String field);

        /**
         * 添加非空条件
         */
        QueryCondition isNotNull(String field);

        /**
         * 逻辑与
         */
        QueryCondition and(QueryCondition other);

        /**
         * 逻辑或
         */
        QueryCondition or(QueryCondition other);

        /**
         * 逻辑非
         */
        QueryCondition not();

        /**
         * 排序
         */
        QueryCondition orderBy(String field, boolean ascending);

        /**
         * 限制结果数量
         */
        QueryCondition limit(int count);

        /**
         * 偏移量
         */
        QueryCondition offset(int offset);
    }

    /**
     * 分页结果
     */
    interface PageResult<T> {
        /**
         * 获取数据列表
         */
        List<T> getData();

        /**
         * 获取总数量
         */
        long getTotalCount();

        /**
         * 获取页码
         */
        int getPageNumber();

        /**
         * 获取页大小
         */
        int getPageSize();

        /**
         * 获取总页数
         */
        int getTotalPages();

        /**
         * 是否有下一页
         */
        boolean hasNext();

        /**
         * 是否有上一页
         */
        boolean hasPrevious();
    }

    /**
     * 事务上下文
     */
    interface TransactionContext {
        /**
         * 获取事务ID
         */
        String getTransactionId();

        /**
         * 获取隔离级别
         */
        IsolationLevel getIsolationLevel();

        /**
         * 是否只读事务
         */
        boolean isReadOnly();

        /**
         * 获取超时时间（毫秒）
         */
        long getTimeout();

        /**
         * 提交事务
         */
        void commit();

        /**
         * 回滚事务
         */
        void rollback();

        /**
         * 设置回滚点
         */
        String setSavepoint(String name);

        /**
         * 回滚到指定回滚点
         */
        void rollbackToSavepoint(String savepoint);

        /**
         * 释放回滚点
         */
        void releaseSavepoint(String savepoint);
    }

    // ========== 基本CRUD操作 ==========

    /**
     * 保存数据
     *
     * @param table 表名/集合名
     * @param key   主键
     * @param data  数据对象
     * @param <T>   数据类型
     * @return 是否保存成功
     */
    <T> boolean save(String table, String key, T data);

    /**
     * 保存数据（异步）
     *
     * @param table 表名/集合名
     * @param key   主键
     * @param data  数据对象
     * @param <T>   数据类型
     * @return 异步结果
     */
    <T> CompletableFuture<Boolean> saveAsync(String table, String key, T data);

    /**
     * 加载数据
     *
     * @param table     表名/集合名
     * @param key       主键
     * @param dataClass 数据类型
     * @param <T>       数据类型
     * @return 数据对象
     */
    <T> Optional<T> load(String table, String key, Class<T> dataClass);

    /**
     * 加载数据（异步）
     *
     * @param table     表名/集合名
     * @param key       主键
     * @param dataClass 数据类型
     * @param <T>       数据类型
     * @return 异步结果
     */
    <T> CompletableFuture<Optional<T>> loadAsync(String table, String key, Class<T> dataClass);

    /**
     * 更新数据
     *
     * @param table 表名/集合名
     * @param key   主键
     * @param data  数据对象
     * @param <T>   数据类型
     * @return 是否更新成功
     */
    <T> boolean update(String table, String key, T data);

    /**
     * 更新数据（异步）
     *
     * @param table 表名/集合名
     * @param key   主键
     * @param data  数据对象
     * @param <T>   数据类型
     * @return 异步结果
     */
    <T> CompletableFuture<Boolean> updateAsync(String table, String key, T data);

    /**
     * 删除数据
     *
     * @param table 表名/集合名
     * @param key   主键
     * @return 是否删除成功
     */
    boolean delete(String table, String key);

    /**
     * 删除数据（异步）
     *
     * @param table 表名/集合名
     * @param key   主键
     * @return 异步结果
     */
    CompletableFuture<Boolean> deleteAsync(String table, String key);

    /**
     * 检查数据是否存在
     *
     * @param table 表名/集合名
     * @param key   主键
     * @return 是否存在
     */
    boolean exists(String table, String key);

    // ========== 批量操作 ==========

    /**
     * 批量保存
     *
     * @param table 表名/集合名
     * @param data  数据映射
     * @param <T>   数据类型
     * @return 成功保存的数量
     */
    <T> int batchSave(String table, Map<String, T> data);

    /**
     * 批量加载
     *
     * @param table     表名/集合名
     * @param keys      主键列表
     * @param dataClass 数据类型
     * @param <T>       数据类型
     * @return 数据映射
     */
    <T> Map<String, T> batchLoad(String table, Collection<String> keys, Class<T> dataClass);

    /**
     * 批量更新
     *
     * @param table 表名/集合名
     * @param data  数据映射
     * @param <T>   数据类型
     * @return 成功更新的数量
     */
    <T> int batchUpdate(String table, Map<String, T> data);

    /**
     * 批量删除
     *
     * @param table 表名/集合名
     * @param keys  主键列表
     * @return 成功删除的数量
     */
    int batchDelete(String table, Collection<String> keys);

    // ========== 查询操作 ==========

    /**
     * 创建查询条件
     *
     * @return 查询条件构建器
     */
    QueryCondition createCondition();

    /**
     * 查询数据
     *
     * @param table     表名/集合名
     * @param condition 查询条件
     * @param dataClass 数据类型
     * @param <T>       数据类型
     * @return 查询结果
     */
    <T> List<T> query(String table, QueryCondition condition, Class<T> dataClass);

    /**
     * 查询单个数据
     *
     * @param table     表名/集合名
     * @param condition 查询条件
     * @param dataClass 数据类型
     * @param <T>       数据类型
     * @return 查询结果
     */
    <T> Optional<T> queryOne(String table, QueryCondition condition, Class<T> dataClass);

    /**
     * 分页查询
     *
     * @param table      表名/集合名
     * @param condition  查询条件
     * @param pageNumber 页码（从1开始）
     * @param pageSize   页大小
     * @param dataClass  数据类型
     * @param <T>        数据类型
     * @return 分页结果
     */
    <T> PageResult<T> queryPage(String table, QueryCondition condition, int pageNumber, int pageSize, Class<T> dataClass);

    /**
     * 计数查询
     *
     * @param table     表名/集合名
     * @param condition 查询条件
     * @return 数据数量
     */
    long count(String table, QueryCondition condition);

    // ========== 事务操作 ==========

    /**
     * 开始事务
     *
     * @return 事务上下文
     */
    TransactionContext beginTransaction();

    /**
     * 开始事务
     *
     * @param isolationLevel 隔离级别
     * @param readOnly       是否只读
     * @param timeoutMs      超时时间（毫秒）
     * @return 事务上下文
     */
    TransactionContext beginTransaction(IsolationLevel isolationLevel, boolean readOnly, long timeoutMs);

    /**
     * 在事务中执行操作
     *
     * @param operation 操作函数
     * @param <T>       返回类型
     * @return 操作结果
     */
    <T> T executeInTransaction(Function<TransactionContext, T> operation);

    /**
     * 在事务中执行操作（异步）
     *
     * @param operation 操作函数
     * @param <T>       返回类型
     * @return 异步结果
     */
    <T> CompletableFuture<T> executeInTransactionAsync(Function<TransactionContext, T> operation);

    // ========== 版本控制 ==========

    /**
     * 获取数据版本
     *
     * @param table 表名/集合名
     * @param key   主键
     * @return 版本信息
     */
    Optional<DataVersion> getVersion(String table, String key);

    /**
     * 保存数据（带版本检查）
     *
     * @param table           表名/集合名
     * @param key             主键
     * @param data            数据对象
     * @param expectedVersion 期望版本
     * @param <T>             数据类型
     * @return 是否保存成功
     */
    <T> boolean saveWithVersion(String table, String key, T data, long expectedVersion);

    /**
     * 获取数据历史版本
     *
     * @param table     表名/集合名
     * @param key       主键
     * @param version   版本号
     * @param dataClass 数据类型
     * @param <T>       数据类型
     * @return 历史数据
     */
    <T> Optional<T> getHistoryVersion(String table, String key, long version, Class<T> dataClass);

    /**
     * 获取数据版本列表
     *
     * @param table 表名/集合名
     * @param key   主键
     * @return 版本列表
     */
    List<DataVersion> getVersionHistory(String table, String key);

    // ========== 增量更新 ==========

    /**
     * 增量更新字段
     *
     * @param table  表名/集合名
     * @param key    主键
     * @param field  字段名
     * @param value  字段值
     * @return 是否更新成功
     */
    boolean updateField(String table, String key, String field, Object value);

    /**
     * 增量更新多个字段
     *
     * @param table  表名/集合名
     * @param key    主键
     * @param fields 字段映射
     * @return 是否更新成功
     */
    boolean updateFields(String table, String key, Map<String, Object> fields);

    /**
     * 数值字段增减
     *
     * @param table 表名/集合名
     * @param key   主键
     * @param field 字段名
     * @param delta 变化量
     * @return 更新后的值
     */
    Optional<Number> incrementField(String table, String key, String field, Number delta);

    // ========== 缓存集成 ==========

    /**
     * 启用缓存
     *
     * @param table 表名/集合名
     * @param ttl   缓存过期时间（秒）
     * @return 是否启用成功
     */
    boolean enableCache(String table, int ttl);

    /**
     * 禁用缓存
     *
     * @param table 表名/集合名
     * @return 是否禁用成功
     */
    boolean disableCache(String table);

    /**
     * 清空缓存
     *
     * @param table 表名/集合名
     * @return 是否清空成功
     */
    boolean clearCache(String table);

    /**
     * 刷新缓存
     *
     * @param table 表名/集合名
     * @param key   主键
     * @return 是否刷新成功
     */
    boolean refreshCache(String table, String key);

    // ========== 数据迁移 ==========

    /**
     * 迁移数据
     *
     * @param fromTable 源表
     * @param toTable   目标表
     * @param condition 迁移条件
     * @return 迁移数量
     */
    int migrateData(String fromTable, String toTable, QueryCondition condition);

    /**
     * 导出数据
     *
     * @param table     表名/集合名
     * @param condition 导出条件
     * @param format    导出格式
     * @return 导出结果
     */
    String exportData(String table, QueryCondition condition, String format);

    /**
     * 导入数据
     *
     * @param table 表名/集合名
     * @param data  导入数据
     * @param format 数据格式
     * @return 导入数量
     */
    int importData(String table, String data, String format);

    // ========== 监控和统计 ==========

    /**
     * 获取统计信息
     *
     * @param table 表名/集合名
     * @return 统计信息
     */
    Map<String, Object> getStatistics(String table);

    /**
     * 获取性能指标
     *
     * @return 性能指标
     */
    Map<String, Number> getMetrics();

    /**
     * 重置统计信息
     */
    void resetStatistics();

    // ========== 健康检查 ==========

    /**
     * 检查连接状态
     *
     * @return 是否连接正常
     */
    boolean isConnected();

    /**
     * 执行健康检查
     *
     * @return 健康状态
     */
    Map<String, Object> healthCheck();

    /**
     * 修复数据
     *
     * @param table 表名/集合名
     * @return 修复结果
     */
    Map<String, Object> repairData(String table);

    // ========== 配置管理 ==========

    /**
     * 获取配置
     *
     * @return 配置信息
     */
    Map<String, Object> getConfiguration();

    /**
     * 更新配置
     *
     * @param config 新配置
     * @return 是否更新成功
     */
    boolean updateConfiguration(Map<String, Object> config);

    /**
     * 重载配置
     *
     * @return 是否重载成功
     */
    boolean reloadConfiguration();

    // ========== 生命周期管理 ==========

    /**
     * 初始化
     */
    void initialize();

    /**
     * 关闭连接
     */
    void close();

    /**
     * 检查是否已关闭
     *
     * @return 是否已关闭
     */
    boolean isClosed();
}