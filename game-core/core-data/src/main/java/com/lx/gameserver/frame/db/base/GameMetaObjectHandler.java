/*
 * 文件名: GameMetaObjectHandler.java
 * 用途: MyBatis Plus元数据自动填充处理器
 * 实现内容:
 *   - 实现MetaObjectHandler接口，自动填充公共字段
 *   - 插入时自动填充创建时间、创建人、删除标识、版本号
 *   - 更新时自动填充更新时间、更新人
 *   - 从上下文获取当前用户信息
 * 技术选型:
 *   - MyBatis Plus MetaObjectHandler
 *   - ThreadLocal获取用户上下文
 *   - LocalDateTime时间处理
 * 依赖关系:
 *   - 配合BaseEntity使用
 *   - 需要用户上下文支持
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.base;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 游戏数据库元数据自动填充处理器
 * <p>
 * 实现MyBatis Plus的MetaObjectHandler接口，自动填充实体的公共字段。
 * 在数据插入和更新时，自动设置时间戳、操作人等信息。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
public class GameMetaObjectHandler implements MetaObjectHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameMetaObjectHandler.class);

    /**
     * 插入时自动填充
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        logger.debug("执行插入自动填充，实体类型: {}", metaObject.getOriginalObject().getClass().getName());

        // 填充创建时间
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());

        // 填充更新时间
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // 填充创建人ID
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            this.strictInsertFill(metaObject, "createBy", Long.class, currentUserId);
            this.strictInsertFill(metaObject, "updateBy", Long.class, currentUserId);
        }

        // 填充版本号
        this.strictInsertFill(metaObject, "version", Integer.class, 1);

        // 填充删除标识
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * 更新时自动填充
     *
     * @param metaObject 元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        logger.debug("执行更新自动填充，实体类型: {}", metaObject.getOriginalObject().getClass().getName());

        // 填充更新时间
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // 填充更新人ID
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId);
        }
    }

    /**
     * 获取当前用户ID
     * <p>
     * 从用户上下文中获取当前登录用户的ID。
     * 如果没有用户上下文或用户未登录，返回null。
     * </p>
     *
     * @return 当前用户ID，如果无法获取则返回null
     */
    private Long getCurrentUserId() {
        try {
            // 这里应该从用户上下文中获取当前用户ID
            // 由于游戏服务器的用户上下文可能在不同的模块中实现，
            // 此处使用简单的ThreadLocal实现，实际项目中需要替换为具体的用户上下文实现
            return UserContext.getCurrentUserId();
        } catch (Exception e) {
            logger.warn("获取当前用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 用户上下文类
     * <p>
     * 简单的用户上下文实现，实际项目中应该替换为具体的用户管理系统。
     * </p>
     */
    public static class UserContext {
        
        private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

        /**
         * 设置当前用户ID
         *
         * @param userId 用户ID
         */
        public static void setCurrentUserId(Long userId) {
            USER_ID_HOLDER.set(userId);
        }

        /**
         * 获取当前用户ID
         *
         * @return 用户ID
         */
        public static Long getCurrentUserId() {
            return USER_ID_HOLDER.get();
        }

        /**
         * 清除当前用户上下文
         */
        public static void clear() {
            USER_ID_HOLDER.remove();
        }
    }
}