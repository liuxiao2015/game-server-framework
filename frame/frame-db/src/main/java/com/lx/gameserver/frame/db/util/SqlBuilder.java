/*
 * 文件名: SqlBuilder.java
 * 用途: 安全的动态SQL构建器
 * 实现内容:
 *   - 安全的动态SQL构建器
 *   - 支持复杂查询条件组合
 *   - 防止SQL注入
 *   - 生成的SQL便于调试
 * 技术选型:
 *   - 建造者模式
 *   - 参数化查询
 *   - SQL关键字白名单
 * 依赖关系:
 *   - 被业务代码使用
 *   - 防止SQL注入攻击
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 安全的动态SQL构建器
 * <p>
 * 提供安全的SQL动态构建功能，防止SQL注入攻击。
 * 支持复杂的查询条件组合和排序。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class SqlBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SqlBuilder.class);

    /**
     * SQL关键字白名单
     */
    private static final Set<String> ALLOWED_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER", "BY", "GROUP", 
            "HAVING", "LIMIT", "OFFSET", "JOIN", "LEFT", "RIGHT", "INNER", 
            "OUTER", "ON", "AS", "ASC", "DESC", "COUNT", "SUM", "AVG", 
            "MAX", "MIN", "DISTINCT", "UNION", "ALL"
    );

    /**
     * 字段名验证正则
     */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 表名验证正则
     */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final StringBuilder sql = new StringBuilder();
    private final List<Object> parameters = new ArrayList<>();
    private final Map<String, Object> namedParameters = new HashMap<>();

    private SqlBuilder() {
    }

    /**
     * 创建SELECT查询构建器
     *
     * @param fields 查询字段
     * @return SQL构建器
     */
    public static SqlBuilder select(String... fields) {
        SqlBuilder builder = new SqlBuilder();
        builder.sql.append("SELECT ");
        
        if (fields == null || fields.length == 0) {
            builder.sql.append("*");
        } else {
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) {
                    builder.sql.append(", ");
                }
                builder.sql.append(builder.validateField(fields[i]));
            }
        }
        
        return builder;
    }

    /**
     * 添加FROM子句
     *
     * @param tableName 表名
     * @return SQL构建器
     */
    public SqlBuilder from(String tableName) {
        sql.append(" FROM ").append(validateTable(tableName));
        return this;
    }

    /**
     * 添加WHERE子句
     *
     * @param condition 条件
     * @return SQL构建器
     */
    public SqlBuilder where(String condition) {
        sql.append(" WHERE ").append(condition);
        return this;
    }

    /**
     * 添加AND条件
     *
     * @param condition 条件
     * @return SQL构建器
     */
    public SqlBuilder and(String condition) {
        sql.append(" AND ").append(condition);
        return this;
    }

    /**
     * 添加OR条件
     *
     * @param condition 条件
     * @return SQL构建器
     */
    public SqlBuilder or(String condition) {
        sql.append(" OR ").append(condition);
        return this;
    }

    /**
     * 添加等值条件
     *
     * @param field 字段名
     * @param value 值
     * @return SQL构建器
     */
    public SqlBuilder eq(String field, Object value) {
        sql.append(validateField(field)).append(" = ?");
        parameters.add(value);
        return this;
    }

    /**
     * 添加不等值条件
     *
     * @param field 字段名
     * @param value 值
     * @return SQL构建器
     */
    public SqlBuilder ne(String field, Object value) {
        sql.append(validateField(field)).append(" != ?");
        parameters.add(value);
        return this;
    }

    /**
     * 添加大于条件
     *
     * @param field 字段名
     * @param value 值
     * @return SQL构建器
     */
    public SqlBuilder gt(String field, Object value) {
        sql.append(validateField(field)).append(" > ?");
        parameters.add(value);
        return this;
    }

    /**
     * 添加大于等于条件
     *
     * @param field 字段名
     * @param value 值
     * @return SQL构建器
     */
    public SqlBuilder gte(String field, Object value) {
        sql.append(validateField(field)).append(" >= ?");
        parameters.add(value);
        return this;
    }

    /**
     * 添加小于条件
     *
     * @param field 字段名
     * @param value 值
     * @return SQL构建器
     */
    public SqlBuilder lt(String field, Object value) {
        sql.append(validateField(field)).append(" < ?");
        parameters.add(value);
        return this;
    }

    /**
     * 添加小于等于条件
     *
     * @param field 字段名
     * @param value 值
     * @return SQL构建器
     */
    public SqlBuilder lte(String field, Object value) {
        sql.append(validateField(field)).append(" <= ?");
        parameters.add(value);
        return this;
    }

    /**
     * 添加LIKE条件
     *
     * @param field 字段名
     * @param pattern 模式
     * @return SQL构建器
     */
    public SqlBuilder like(String field, String pattern) {
        sql.append(validateField(field)).append(" LIKE ?");
        parameters.add(pattern);
        return this;
    }

    /**
     * 添加IN条件
     *
     * @param field 字段名
     * @param values 值列表
     * @return SQL构建器
     */
    public SqlBuilder in(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("IN条件的值列表不能为空");
        }
        
        sql.append(validateField(field)).append(" IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        parameters.addAll(values);
        return this;
    }

    /**
     * 添加BETWEEN条件
     *
     * @param field 字段名
     * @param start 开始值
     * @param end 结束值
     * @return SQL构建器
     */
    public SqlBuilder between(String field, Object start, Object end) {
        sql.append(validateField(field)).append(" BETWEEN ? AND ?");
        parameters.add(start);
        parameters.add(end);
        return this;
    }

    /**
     * 添加IS NULL条件
     *
     * @param field 字段名
     * @return SQL构建器
     */
    public SqlBuilder isNull(String field) {
        sql.append(validateField(field)).append(" IS NULL");
        return this;
    }

    /**
     * 添加IS NOT NULL条件
     *
     * @param field 字段名
     * @return SQL构建器
     */
    public SqlBuilder isNotNull(String field) {
        sql.append(validateField(field)).append(" IS NOT NULL");
        return this;
    }

    /**
     * 添加ORDER BY子句
     *
     * @param field 排序字段
     * @param desc 是否降序
     * @return SQL构建器
     */
    public SqlBuilder orderBy(String field, boolean desc) {
        if (!sql.toString().contains(" ORDER BY ")) {
            sql.append(" ORDER BY ");
        } else {
            sql.append(", ");
        }
        sql.append(validateField(field));
        if (desc) {
            sql.append(" DESC");
        }
        return this;
    }

    /**
     * 添加ORDER BY子句（升序）
     *
     * @param field 排序字段
     * @return SQL构建器
     */
    public SqlBuilder orderBy(String field) {
        return orderBy(field, false);
    }

    /**
     * 添加GROUP BY子句
     *
     * @param fields 分组字段
     * @return SQL构建器
     */
    public SqlBuilder groupBy(String... fields) {
        sql.append(" GROUP BY ");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(validateField(fields[i]));
        }
        return this;
    }

    /**
     * 添加HAVING子句
     *
     * @param condition 条件
     * @return SQL构建器
     */
    public SqlBuilder having(String condition) {
        sql.append(" HAVING ").append(condition);
        return this;
    }

    /**
     * 添加LIMIT子句
     *
     * @param limit 限制数量
     * @return SQL构建器
     */
    public SqlBuilder limit(int limit) {
        sql.append(" LIMIT ?");
        parameters.add(limit);
        return this;
    }

    /**
     * 添加LIMIT和OFFSET子句
     *
     * @param limit 限制数量
     * @param offset 偏移量
     * @return SQL构建器
     */
    public SqlBuilder limit(int limit, int offset) {
        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);
        return this;
    }

    /**
     * 添加自定义SQL片段（不安全，谨慎使用）
     *
     * @param sqlFragment SQL片段
     * @return SQL构建器
     */
    public SqlBuilder appendUnsafe(String sqlFragment) {
        logger.warn("使用不安全的SQL片段: {}", sqlFragment);
        sql.append(" ").append(sqlFragment);
        return this;
    }

    /**
     * 验证字段名
     */
    private String validateField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        
        String trimmedField = field.trim();
        if (!FIELD_NAME_PATTERN.matcher(trimmedField).matches()) {
            throw new IllegalArgumentException("非法的字段名: " + field);
        }
        
        return trimmedField;
    }

    /**
     * 验证表名
     */
    private String validateTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        
        String trimmedTable = tableName.trim();
        if (!TABLE_NAME_PATTERN.matcher(trimmedTable).matches()) {
            throw new IllegalArgumentException("非法的表名: " + tableName);
        }
        
        return trimmedTable;
    }

    /**
     * 构建SQL字符串
     *
     * @return SQL字符串
     */
    public String build() {
        return sql.toString();
    }

    /**
     * 获取参数列表
     *
     * @return 参数列表
     */
    public List<Object> getParameters() {
        return new ArrayList<>(parameters);
    }

    /**
     * 获取参数数组
     *
     * @return 参数数组
     */
    public Object[] getParameterArray() {
        return parameters.toArray();
    }

    /**
     * 获取带参数的完整SQL信息
     *
     * @return SQL执行信息
     */
    public SqlExecutionInfo buildWithParameters() {
        return new SqlExecutionInfo(build(), getParameters());
    }

    /**
     * 清理构建器
     */
    public void clear() {
        sql.setLength(0);
        parameters.clear();
        namedParameters.clear();
    }

    @Override
    public String toString() {
        return "SqlBuilder{" +
                "sql='" + sql.toString() + '\'' +
                ", parameterCount=" + parameters.size() +
                '}';
    }

    /**
     * SQL执行信息
     */
    public static class SqlExecutionInfo {
        private final String sql;
        private final List<Object> parameters;

        public SqlExecutionInfo(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = new ArrayList<>(parameters);
        }

        public String getSql() {
            return sql;
        }

        public List<Object> getParameters() {
            return new ArrayList<>(parameters);
        }

        public Object[] getParameterArray() {
            return parameters.toArray();
        }

        @Override
        public String toString() {
            return "SqlExecutionInfo{" +
                    "sql='" + sql + '\'' +
                    ", parameters=" + parameters +
                    '}';
        }
    }
}