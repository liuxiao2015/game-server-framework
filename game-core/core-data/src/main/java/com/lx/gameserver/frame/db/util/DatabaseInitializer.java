/*
 * 文件名: DatabaseInitializer.java
 * 用途: 数据库初始化工具
 * 实现内容:
 *   - 开发环境自动建表
 *   - 初始化测试数据
 *   - 数据库版本管理
 *   - 支持多环境配置
 * 技术选型:
 *   - Spring Boot启动初始化
 *   - Flyway数据库版本管理
 *   - H2内存数据库支持
 * 依赖关系:
 *   - 被Spring Boot自动配置调用
 *   - 配合DataSourceConfig使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库初始化工具
 * <p>
 * 负责开发环境的数据库初始化工作，包括自动建表、插入测试数据等。
 * 支持检查表是否存在，避免重复创建。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Component
@ConditionalOnProperty(prefix = "game.database.init", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 应用启动完成后执行数据库初始化
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        logger.info("开始初始化数据库...");
        
        try {
            // 检查和创建基础表
            createBaseTables();
            
            // 插入测试数据
            insertTestData();
            
            logger.info("数据库初始化完成");
        } catch (Exception e) {
            logger.error("数据库初始化失败", e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    /**
     * 创建基础表
     */
    private void createBaseTables() throws SQLException, IOException {
        List<String> tables = List.of(
            "t_audit_log",
            "t_user",
            "t_player"
        );

        for (String tableName : tables) {
            if (!tableExists(tableName)) {
                createTable(tableName);
                logger.info("创建表: {}", tableName);
            } else {
                logger.debug("表已存在: {}", tableName);
            }
        }
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * 创建表
     */
    private void createTable(String tableName) throws IOException {
        Resource resource = new ClassPathResource("sql/schema/" + tableName + ".sql");
        if (resource.exists()) {
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(sql);
        } else {
            logger.warn("表创建脚本不存在: {}", resource.getFilename());
        }
    }

    /**
     * 插入测试数据
     */
    private void insertTestData() {
        try {
            // 检查是否已有测试数据
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user", Integer.class);
            if (count != null && count > 0) {
                logger.debug("测试数据已存在，跳过初始化");
                return;
            }

            // 插入测试用户数据
            insertTestUsers();
            
            logger.info("测试数据初始化完成");
        } catch (Exception e) {
            logger.warn("测试数据初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 插入测试用户数据
     */
    private void insertTestUsers() {
        List<String> userSqls = List.of(
            "INSERT INTO t_user (id, username, password, email, create_time, update_time, create_by, update_by, version, deleted, remark) " +
            "VALUES (1, 'admin', 'admin123', 'admin@game.com', NOW(), NOW(), 1, 1, 1, 0, '管理员账号')",
            
            "INSERT INTO t_user (id, username, password, email, create_time, update_time, create_by, update_by, version, deleted, remark) " +
            "VALUES (2, 'player1', 'player123', 'player1@game.com', NOW(), NOW(), 1, 1, 1, 0, '测试玩家1')",
            
            "INSERT INTO t_user (id, username, password, email, create_time, update_time, create_by, update_by, version, deleted, remark) " +
            "VALUES (3, 'player2', 'player123', 'player2@game.com', NOW(), NOW(), 1, 1, 1, 0, '测试玩家2')"
        );

        for (String sql : userSqls) {
            try {
                jdbcTemplate.update(sql);
            } catch (Exception e) {
                logger.debug("插入测试数据失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取数据库版本信息
     */
    public String getDatabaseVersion() {
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (SQLException e) {
            logger.error("获取数据库版本失败", e);
            return "Unknown";
        }
    }

    /**
     * 清理测试数据
     */
    public void cleanupTestData() {
        logger.info("清理测试数据...");
        
        List<String> cleanupSqls = List.of(
            "DELETE FROM t_audit_log WHERE create_by IN (1, 2, 3)",
            "DELETE FROM t_user WHERE id IN (1, 2, 3)"
        );

        for (String sql : cleanupSqls) {
            try {
                int count = jdbcTemplate.update(sql);
                logger.debug("清理数据: {} 条记录", count);
            } catch (Exception e) {
                logger.warn("清理数据失败: {}", e.getMessage());
            }
        }
        
        logger.info("测试数据清理完成");
    }
}