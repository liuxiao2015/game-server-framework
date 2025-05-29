/*
 * 文件名: AuditLogMapper.java
 * 用途: 审计日志数据访问接口
 * 实现内容:
 *   - 继承BaseMapper提供基础CRUD操作
 *   - 扩展按时间范围查询方法
 *   - 扩展按操作人查询方法
 *   - 扩展按表名查询方法
 * 技术选型:
 *   - MyBatis Plus BaseMapper
 *   - 自定义查询方法
 *   - 分页查询支持
 * 依赖关系:
 *   - 继承GameBaseMapper
 *   - 被DataAuditInterceptor使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.gameserver.frame.db.base.GameBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问接口
 * <p>
 * 提供审计日志的数据访问操作，支持多种查询条件和分页功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Mapper
public interface AuditLogMapper extends GameBaseMapper<AuditLog> {

    /**
     * 按时间范围查询审计日志
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 审计日志列表
     */
    @Select("SELECT * FROM t_audit_log WHERE create_time >= #{startTime} AND create_time <= #{endTime} ORDER BY create_time DESC")
    List<AuditLog> selectByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 按操作人查询审计日志
     *
     * @param createBy 操作人ID
     * @param page 分页参数
     * @return 分页结果
     */
    default IPage<AuditLog> selectByCreateBy(Long createBy, Page<AuditLog> page) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("create_by", createBy)
                   .orderByDesc("create_time");
        return selectPage(page, queryWrapper);
    }

    /**
     * 按表名查询审计日志
     *
     * @param tableName 表名
     * @param page 分页参数
     * @return 分页结果
     */
    default IPage<AuditLog> selectByTableName(String tableName, Page<AuditLog> page) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("table_name", tableName)
                   .orderByDesc("create_time");
        return selectPage(page, queryWrapper);
    }

    /**
     * 按操作类型查询审计日志
     *
     * @param operationType 操作类型
     * @param page 分页参数
     * @return 分页结果
     */
    default IPage<AuditLog> selectByOperationType(AuditLog.OperationType operationType, Page<AuditLog> page) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("operation_type", operationType)
                   .orderByDesc("create_time");
        return selectPage(page, queryWrapper);
    }

    /**
     * 查询敏感操作日志
     *
     * @param page 分页参数
     * @return 分页结果
     */
    default IPage<AuditLog> selectSensitiveOperations(Page<AuditLog> page) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_sensitive", true)
                   .orderByDesc("create_time");
        return selectPage(page, queryWrapper);
    }

    /**
     * 统计指定时间范围内的操作次数
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 操作次数
     */
    @Select("SELECT COUNT(*) FROM t_audit_log WHERE create_time >= #{startTime} AND create_time <= #{endTime}")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime, 
                         @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定用户的操作次数
     *
     * @param createBy 操作人ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 操作次数
     */
    @Select("SELECT COUNT(*) FROM t_audit_log WHERE create_by = #{createBy} AND create_time >= #{startTime} AND create_time <= #{endTime}")
    Long countByUser(@Param("createBy") Long createBy,
                    @Param("startTime") LocalDateTime startTime, 
                    @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定实体的变更历史
     *
     * @param tableName 表名
     * @param entityId 实体ID
     * @return 变更历史列表
     */
    default List<AuditLog> selectEntityHistory(String tableName, String entityId) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("table_name", tableName)
                   .eq("entity_id", entityId)
                   .orderByDesc("create_time");
        return selectList(queryWrapper);
    }

    /**
     * 清理过期的审计日志
     *
     * @param beforeTime 清理此时间之前的日志
     * @return 清理的记录数
     */
    default int cleanupExpiredLogs(LocalDateTime beforeTime) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.lt("create_time", beforeTime);
        return delete(queryWrapper);
    }
}