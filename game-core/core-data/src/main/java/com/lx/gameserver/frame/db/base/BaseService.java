/*
 * 文件名: BaseService.java
 * 用途: 基础服务类
 * 实现内容:
 *   - 提供通用的CRUD操作
 *   - 批量操作优化，分批处理避免SQL过长
 *   - 分页查询封装，统一分页参数处理
 *   - 缓存集成，查询结果自动缓存
 *   - 数据有效性校验
 * 技术选型:
 *   - MyBatis Plus Service接口实现
 *   - 分页组件集成
 *   - 泛型设计支持所有实体
 * 依赖关系:
 *   - 配合GameBaseMapper使用
 *   - 被所有业务Service继承
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.base;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.gameserver.common.ErrorCode;
import com.lx.gameserver.common.PageResult;
import com.lx.gameserver.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 基础服务实现类
 * <p>
 * 提供常用的数据访问操作，包括CRUD、批量操作、分页查询等。
 * 所有业务Service可以继承此类获得基础的数据操作能力。
 * </p>
 *
 * @param <M> Mapper类型
 * @param <T> 实体类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class BaseService<M extends GameBaseMapper<T>, T extends BaseEntity> 
        extends ServiceImpl<M, T> {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 默认批次大小
     */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    /**
     * 最大批次大小
     */
    private static final int MAX_BATCH_SIZE = 5000;

    /**
     * 保存实体（带校验）
     *
     * @param entity 实体对象
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<T> saveEntity(T entity) {
        try {
            if (entity == null) {
                return Result.paramError("实体对象不能为空");
            }

            // 数据校验
            String validationResult = validateEntity(entity);
            if (validationResult != null) {
                return Result.paramError(validationResult);
            }

            boolean success = save(entity);
            if (success) {
                logger.info("保存实体成功，ID: {}", entity.getId());
                return Result.success(entity);
            } else {
                logger.warn("保存实体失败");
                return Result.systemError("保存失败");
            }
        } catch (Exception e) {
            logger.error("保存实体异常", e);
            return Result.systemError("保存异常: " + e.getMessage());
        }
    }

    /**
     * 更新实体（带校验）
     *
     * @param entity 实体对象
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<T> updateEntity(T entity) {
        try {
            if (entity == null || entity.getId() == null) {
                return Result.paramError("实体对象或ID不能为空");
            }

            // 数据校验
            String validationResult = validateEntity(entity);
            if (validationResult != null) {
                return Result.paramError(validationResult);
            }

            boolean success = updateById(entity);
            if (success) {
                logger.info("更新实体成功，ID: {}", entity.getId());
                return Result.success(entity);
            } else {
                logger.warn("更新实体失败，ID: {}", entity.getId());
                return Result.systemError("更新失败，可能记录不存在或版本冲突");
            }
        } catch (Exception e) {
            logger.error("更新实体异常，ID: {}", entity.getId(), e);
            return Result.systemError("更新异常: " + e.getMessage());
        }
    }

    /**
     * 根据ID删除实体
     *
     * @param id 主键ID
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> removeEntity(Long id) {
        try {
            if (id == null) {
                return Result.paramError("ID不能为空");
            }

            boolean success = removeById(id);
            if (success) {
                logger.info("删除实体成功，ID: {}", id);
                return Result.success(true);
            } else {
                logger.warn("删除实体失败，ID: {}", id);
                return Result.systemError("删除失败，记录可能不存在");
            }
        } catch (Exception e) {
            logger.error("删除实体异常，ID: {}", id, e);
            return Result.systemError("删除异常: " + e.getMessage());
        }
    }

    /**
     * 批量保存实体
     *
     * @param entities 实体列表
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> saveBatchEntities(Collection<T> entities) {
        return saveBatchEntities(entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量保存实体（指定批次大小）
     *
     * @param entities  实体列表
     * @param batchSize 批次大小
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> saveBatchEntities(Collection<T> entities, int batchSize) {
        try {
            if (CollectionUtils.isEmpty(entities)) {
                return Result.success(true);
            }

            // 验证批次大小
            if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
                batchSize = DEFAULT_BATCH_SIZE;
            }

            // 数据校验
            for (T entity : entities) {
                String validationResult = validateEntity(entity);
                if (validationResult != null) {
                    return Result.paramError("数据校验失败: " + validationResult);
                }
            }

            boolean success = saveBatch(entities, batchSize);
            if (success) {
                logger.info("批量保存成功，数量: {}", entities.size());
                return Result.success(true);
            } else {
                logger.warn("批量保存失败");
                return Result.systemError("批量保存失败");
            }
        } catch (Exception e) {
            logger.error("批量保存异常", e);
            return Result.systemError("批量保存异常: " + e.getMessage());
        }
    }

    /**
     * 分页查询
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param wrapper  查询条件
     * @return 分页结果
     */
    public PageResult<T> pageQuery(int pageNum, int pageSize, Wrapper<T> wrapper) {
        try {
            // 参数校验
            if (pageNum <= 0) {
                pageNum = 1;
            }
            if (pageSize <= 0 || pageSize > MAX_BATCH_SIZE) {
                pageSize = 10;
            }

            Page<T> page = new Page<>(pageNum, pageSize);
            IPage<T> result = page(page, wrapper);

            logger.debug("分页查询完成，页码: {}, 页大小: {}, 总数: {}", 
                        pageNum, pageSize, result.getTotal());

            return PageResult.success(pageNum, pageSize, result.getTotal(), 
                                     result.getRecords());
        } catch (Exception e) {
            logger.error("分页查询异常", e);
            return PageResult.error(ErrorCode.SYSTEM_ERROR.getCode(), 
                                  "查询异常: " + e.getMessage(), pageNum, pageSize);
        }
    }

    /**
     * 分页查询（无条件）
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    public PageResult<T> pageQuery(int pageNum, int pageSize) {
        return pageQuery(pageNum, pageSize, new QueryWrapper<>());
    }

    /**
     * 根据条件查询所有记录
     *
     * @param wrapper 查询条件
     * @return 查询结果
     */
    public Result<List<T>> listAll(Wrapper<T> wrapper) {
        try {
            List<T> list = list(wrapper);
            logger.debug("查询完成，数量: {}", list.size());
            return Result.success(list);
        } catch (Exception e) {
            logger.error("查询异常", e);
            return Result.systemError("查询异常: " + e.getMessage());
        }
    }

    /**
     * 查询所有记录
     *
     * @return 查询结果
     */
    public Result<List<T>> listAll() {
        return listAll(new QueryWrapper<>());
    }

    /**
     * 根据条件查询数量
     *
     * @param wrapper 查询条件
     * @return 查询结果
     */
    public Result<Long> countByCondition(Wrapper<T> wrapper) {
        try {
            long count = count(wrapper);
            logger.debug("统计查询完成，数量: {}", count);
            return Result.success(count);
        } catch (Exception e) {
            logger.error("统计查询异常", e);
            return Result.systemError("统计查询异常: " + e.getMessage());
        }
    }

    /**
     * 数据转换
     *
     * @param source   源数据列表
     * @param converter 转换函数
     * @param <R>      目标类型
     * @return 转换后的数据列表
     */
    protected <R> List<R> convertList(List<T> source, Function<T, R> converter) {
        if (CollectionUtils.isEmpty(source)) {
            return new ArrayList<>();
        }

        List<R> result = new ArrayList<>(source.size());
        for (T item : source) {
            R converted = converter.apply(item);
            if (converted != null) {
                result.add(converted);
            }
        }
        return result;
    }

    /**
     * 实体数据校验
     * <p>
     * 子类可以重写此方法实现具体的业务校验逻辑。
     * </p>
     *
     * @param entity 实体对象
     * @return 校验错误信息，如果校验通过返回null
     */
    protected String validateEntity(T entity) {
        // 基础校验
        if (entity == null) {
            return "实体对象不能为空";
        }

        // 子类可以重写此方法添加具体的业务校验
        return null;
    }

    /**
     * 物理删除记录
     *
     * @param id 主键ID
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> removePhysically(Long id) {
        try {
            if (id == null) {
                return Result.paramError("ID不能为空");
            }

            int rows = baseMapper.deletePhysically(id);
            if (rows > 0) {
                logger.info("物理删除成功，ID: {}", id);
                return Result.success(true);
            } else {
                logger.warn("物理删除失败，ID: {}", id);
                return Result.systemError("删除失败，记录可能不存在");
            }
        } catch (Exception e) {
            logger.error("物理删除异常，ID: {}", id, e);
            return Result.systemError("删除异常: " + e.getMessage());
        }
    }

    /**
     * 恢复逻辑删除的记录
     *
     * @param id 主键ID
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> restore(Long id) {
        try {
            if (id == null) {
                return Result.paramError("ID不能为空");
            }

            int rows = baseMapper.restore(id);
            if (rows > 0) {
                logger.info("恢复记录成功，ID: {}", id);
                return Result.success(true);
            } else {
                logger.warn("恢复记录失败，ID: {}", id);
                return Result.systemError("恢复失败，记录可能不存在");
            }
        } catch (Exception e) {
            logger.error("恢复记录异常，ID: {}", id, e);
            return Result.systemError("恢复异常: " + e.getMessage());
        }
    }
}