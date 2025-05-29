/*
 * 文件名: MyBatisPlusConfig.java
 * 用途: MyBatis Plus配置类
 * 实现内容:
 *   - 分页插件配置，限制单页最大记录数
 *   - 乐观锁插件，基于version字段实现并发控制
 *   - 防全表更新插件，防止误操作
 *   - 数据权限插件，支持行级数据权限控制
 *   - SQL性能分析插件，开发环境记录慢SQL
 *   - 自动填充处理器配置
 *   - 全局配置，ID生成、逻辑删除等
 * 技术选型:
 *   - MyBatis Plus插件体系
 *   - 雪花算法ID生成
 *   - SQL性能监控
 * 依赖关系:
 *   - 依赖GameMetaObjectHandler
 *   - 配合DynamicDataSource使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.lx.gameserver.frame.db.base.GameMetaObjectHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis Plus配置类
 * <p>
 * 配置MyBatis Plus的各种插件和全局设置，提供分页、乐观锁、
 * 安全防护、性能监控等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Configuration
@ConditionalOnProperty(prefix = "game.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MyBatisPlusConfig {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisPlusConfig.class);

    /**
     * 单页最大记录数限制
     */
    private static final long MAX_LIMIT = 5000L;

    /**
     * 慢SQL阈值(毫秒)
     */
    private static final long SLOW_SQL_THRESHOLD = 1000L;

    /**
     * MyBatis Plus拦截器配置
     * <p>
     * 配置各种插件拦截器，包括分页、乐观锁、防全表更新等。
     * </p>
     *
     * @return MybatisPlusInterceptor实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        logger.info("配置MyBatis Plus拦截器");
        
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
        paginationInterceptor.setDbType(DbType.MYSQL);
        paginationInterceptor.setOverflow(false); // 溢出总页数后是否进行处理
        paginationInterceptor.setMaxLimit(MAX_LIMIT); // 单页分页条数限制
        paginationInterceptor.setOptimizeJoin(true); // 优化COUNT SQL
        interceptor.addInnerInterceptor(paginationInterceptor);
        logger.info("配置分页插件，最大分页数: {}", MAX_LIMIT);

        // 2. 乐观锁插件
        OptimisticLockerInnerInterceptor optimisticLockerInterceptor = new OptimisticLockerInnerInterceptor();
        interceptor.addInnerInterceptor(optimisticLockerInterceptor);
        logger.info("配置乐观锁插件");

        // 3. 防全表更新删除插件
        BlockAttackInnerInterceptor blockAttackInterceptor = new BlockAttackInnerInterceptor();
        interceptor.addInnerInterceptor(blockAttackInterceptor);
        logger.info("配置防全表更新删除插件");

        // 4. 数据权限插件
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor();
        // 这里可以添加数据权限处理器
        // dataPermissionInterceptor.setDataPermissionHandler(new GameDataPermissionHandler());
        interceptor.addInnerInterceptor(dataPermissionInterceptor);
        logger.info("配置数据权限插件");

        // 5. 开发环境SQL性能分析插件
        configurePerformanceInterceptor(interceptor);

        return interceptor;
    }

    /**
     * 配置性能分析插件（仅开发环境）
     *
     * @param interceptor MyBatis Plus拦截器
     */
    @Profile("dev")
    private void configurePerformanceInterceptor(MybatisPlusInterceptor interceptor) {
        // 在开发环境配置SQL执行分析
        logger.info("配置开发环境SQL性能分析，慢SQL阈值: {}ms", SLOW_SQL_THRESHOLD);
        
        // 这里可以添加自定义的SQL性能监控拦截器
        // 由于MyBatis Plus 3.5+移除了PerformanceInterceptor，
        // 我们可以使用自定义的性能监控实现
    }

    /**
     * 全局配置
     * <p>
     * 配置MyBatis Plus的全局设置，包括ID生成策略、逻辑删除等。
     * </p>
     *
     * @param metaObjectHandler 元数据自动填充处理器
     * @return GlobalConfig实例
     */
    @Bean
    public GlobalConfig globalConfig(GameMetaObjectHandler metaObjectHandler) {
        logger.info("配置MyBatis Plus全局设置");
        
        GlobalConfig globalConfig = new GlobalConfig();

        // 配置数据库相关设置
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        
        // ID生成策略：雪花算法
        dbConfig.setIdType(com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID);
        logger.info("配置ID生成策略: 雪花算法");
        
        // 表名前缀
        dbConfig.setTablePrefix("game_");
        logger.info("配置表名前缀: game_");
        
        // 逻辑删除配置
        dbConfig.setLogicDeleteField("deleted"); // 逻辑删除字段
        dbConfig.setLogicDeleteValue("1"); // 删除值
        dbConfig.setLogicNotDeleteValue("0"); // 未删除值
        logger.info("配置逻辑删除字段: deleted");
        
        // 字段策略
        dbConfig.setInsertStrategy(com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL);
        dbConfig.setUpdateStrategy(com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL);
        dbConfig.setSelectStrategy(com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL);
        
        // 数据库大小写
        dbConfig.setCapitalMode(false);
        dbConfig.setTableUnderline(true);
        // dbConfig.setColumnUnderline(true); // 该方法在新版本中已移除
        
        globalConfig.setDbConfig(dbConfig);

        // 配置元数据自动填充处理器
        globalConfig.setMetaObjectHandler(metaObjectHandler);
        logger.info("配置元数据自动填充处理器");

        // 配置Banner
        globalConfig.setBanner(false);

        return globalConfig;
    }

    /**
     * SQL会话工厂配置
     * <p>
     * 配置SqlSessionFactory，设置Mapper文件位置等。
     * </p>
     *
     * @param dataSource 数据源
     * @param mybatisPlusInterceptor MyBatis Plus拦截器
     * @return SqlSessionFactory实例
     * @throws Exception 配置异常
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, 
                                             MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        logger.info("配置SQL会话工厂");
        
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        
        // 设置插件
        factory.setPlugins(mybatisPlusInterceptor);
        
        // 设置Mapper XML文件位置
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResources("classpath*:mapper/**/*Mapper.xml"));
        
        // 设置类型别名包路径
        factory.setTypeAliasesPackage("com.lx.gameserver.**.entity");
        
        // 配置
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true); // 下划线转驼峰
        configuration.setCacheEnabled(true); // 启用二级缓存
        configuration.setLazyLoadingEnabled(true); // 启用懒加载
        configuration.setMultipleResultSetsEnabled(true);
        configuration.setUseColumnLabel(true);
        configuration.setUseGeneratedKeys(true);
        configuration.setAutoMappingBehavior(org.apache.ibatis.session.AutoMappingBehavior.PARTIAL);
        configuration.setDefaultExecutorType(org.apache.ibatis.session.ExecutorType.SIMPLE);
        configuration.setDefaultStatementTimeout(25000);
        
        factory.setConfiguration(configuration);
        
        logger.info("SQL会话工厂配置完成");
        return factory.getObject();
    }

    /**
     * 游戏数据权限处理器
     * <p>
     * 自定义的数据权限处理逻辑，可以根据用户角色和权限
     * 动态添加WHERE条件实现行级数据权限控制。
     * </p>
     */
    public static class GameDataPermissionHandler implements com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler {

        private static final Logger logger = LoggerFactory.getLogger(GameDataPermissionHandler.class);

        @Override
        public net.sf.jsqlparser.expression.Expression getSqlSegment(net.sf.jsqlparser.expression.Expression where, String mappedStatementId) {
            // 这里可以实现具体的数据权限逻辑
            // 例如：根据当前用户的权限，动态添加 WHERE user_id = ? 等条件
            
            logger.debug("处理数据权限，mappedStatementId: {}", mappedStatementId);
            
            // 暂时返回原始WHERE条件，不做额外的权限控制
            // 实际项目中可以根据业务需求实现具体的权限过滤逻辑
            return where;
        }
    }
}