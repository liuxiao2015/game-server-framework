/*
 * 文件名: DatabaseAutoConfiguration.java
 * 用途: 数据库自动配置类
 * 实现内容:
 *   - 启用数据库相关的自动配置
 *   - 配置包扫描路径
 *   - 导入相关配置类
 * 技术选型:
 *   - Spring Boot自动配置
 *   - MyBatis Plus集成
 *   - 组件扫描
 * 依赖关系:
 *   - 集成所有数据库相关配置
 *   - 被启动类引用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 数据库自动配置类
 * <p>
 * 统一配置数据库相关的所有组件，包括数据源、MyBatis Plus、
 * 切面等功能模块。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnProperty(prefix = "game.database", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
    "com.lx.gameserver.frame.db.datasource",
    "com.lx.gameserver.frame.db.base"
})
@MapperScan(basePackages = "com.lx.gameserver.**.mapper")
@Import({
    DataSourceConfig.class,
    MyBatisPlusConfig.class
})
public class DatabaseAutoConfiguration {
    
    // 这个类主要用于组装和启用数据库相关配置
    // 具体的配置实现在各个专门的配置类中
}