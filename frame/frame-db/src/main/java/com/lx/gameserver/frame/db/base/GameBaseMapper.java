/*
 * 文件名: BaseMapper.java
 * 用途: 数据访问基础Mapper接口
 * 实现内容:
 *   - 继承MyBatis Plus的BaseMapper提供基础CRUD操作
 *   - 扩展批量插入方法，支持大批量数据处理
 *   - 扩展物理删除方法，绕过逻辑删除机制
 *   - 扩展强制更新方法，忽略乐观锁控制
 *   - 扩展包含已删除数据的查询方法
 * 技术选型:
 *   - MyBatis Plus BaseMapper
 *   - 自定义SQL方法扩展
 *   - 泛型设计支持所有实体
 * 依赖关系:
 *   - 被所有业务Mapper继承
 *   - 配合BaseEntity使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.base;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 游戏数据访问基础Mapper接口
 * <p>
 * 继承MyBatis Plus的BaseMapper，在此基础上扩展常用的数据操作方法。
 * 提供批量操作、物理删除、强制更新等功能，满足游戏业务的特殊需求。
 * </p>
 *
 * @param <T> 实体类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface GameBaseMapper<T> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {

    /**
     * 批量插入数据
     * <p>
     * 使用MySQL的INSERT INTO ... VALUES (),(),() 语法，
     * 提高大批量数据插入的性能。
     * </p>
     *
     * @param entityList 实体列表
     * @return 插入的记录数
     */
    int insertBatch(@Param("list") List<T> entityList);

    /**
     * 批量插入或更新数据
     * <p>
     * 使用MySQL的ON DUPLICATE KEY UPDATE语法，
     * 实现批量的插入或更新操作。
     * </p>
     *
     * @param entityList 实体列表
     * @return 影响的记录数
     */
    int insertOrUpdateBatch(@Param("list") List<T> entityList);

    /**
     * 物理删除记录
     * <p>
     * 绕过逻辑删除机制，直接从数据库中删除记录。
     * 谨慎使用，建议仅在数据清理或测试环境中使用。
     * </p>
     *
     * @param id 主键ID
     * @return 删除的记录数
     */
    int deletePhysically(@Param("id") Long id);

    /**
     * 根据条件物理删除记录
     * <p>
     * 批量物理删除满足条件的记录。
     * </p>
     *
     * @param columnMap 条件列值对
     * @return 删除的记录数
     */
    int deletePhysicallyByMap(@Param("cm") java.util.Map<String, Object> columnMap);

    /**
     * 强制更新记录
     * <p>
     * 忽略乐观锁控制，强制更新记录。
     * 在特殊业务场景下使用，如管理员强制修改数据。
     * </p>
     *
     * @param entity 实体对象
     * @return 更新的记录数
     */
    int forceUpdate(@Param("entity") T entity);

    /**
     * 查询所有记录（包括已删除）
     * <p>
     * 查询包含逻辑删除记录在内的所有数据，
     * 用于数据恢复或审计场景。
     * </p>
     *
     * @return 所有记录列表
     */
    List<T> selectAllWithDeleted();

    /**
     * 根据ID查询记录（包括已删除）
     * <p>
     * 查询指定ID的记录，即使该记录已被逻辑删除。
     * </p>
     *
     * @param id 主键ID
     * @return 实体对象，如果不存在则返回null
     */
    T selectByIdWithDeleted(@Param("id") Long id);

    /**
     * 根据条件查询记录（包括已删除）
     * <p>
     * 根据指定条件查询记录，包含已逻辑删除的数据。
     * </p>
     *
     * @param columnMap 条件列值对
     * @return 匹配的记录列表
     */
    List<T> selectByMapWithDeleted(@Param("cm") java.util.Map<String, Object> columnMap);

    /**
     * 恢复逻辑删除的记录
     * <p>
     * 将逻辑删除的记录恢复为正常状态。
     * </p>
     *
     * @param id 主键ID
     * @return 恢复的记录数
     */
    int restore(@Param("id") Long id);

    /**
     * 批量恢复逻辑删除的记录
     * <p>
     * 批量恢复多个逻辑删除的记录。
     * </p>
     *
     * @param idList 主键ID列表
     * @return 恢复的记录数
     */
    int restoreBatch(@Param("idList") List<Long> idList);
}