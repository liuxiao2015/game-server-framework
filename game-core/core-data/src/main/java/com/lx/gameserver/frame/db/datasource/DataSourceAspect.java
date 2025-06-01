/*
 * 文件名: DataSourceAspect.java
 * 用途: 数据源切面处理器
 * 实现内容:
 *   - 拦截@Transactional注解，只读事务使用从库
 *   - 拦截@DataSource注解，支持手动指定数据源
 *   - 拦截Mapper方法，根据方法名自动判断读写
 *   - 事务中的查询强制使用主库，保证数据一致性
 *   - 支持多数据源嵌套切换的场景
 * 技术选型:
 *   - Spring AOP切面编程
 *   - 注解驱动的数据源路由
 *   - 方法名模式匹配
 * 依赖关系:
 *   - 依赖DataSourceContextHolder管理数据源上下文
 *   - 与DynamicDataSource配合使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.db.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据源切面处理器
 * <p>
 * 通过AOP拦截数据库访问方法，自动进行读写分离路由。
 * 支持注解驱动和方法名模式匹配两种路由方式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Aspect
@Component
@Order(1) // 确保在事务切面之前执行
public class DataSourceAspect {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceAspect.class);

    /**
     * 读操作方法名前缀
     */
    private static final Set<String> READ_METHOD_PREFIXES = new HashSet<>(Arrays.asList(
            "select", "get", "find", "query", "list", "count", "exists", "check"
    ));

    /**
     * 写操作方法名前缀
     */
    private static final Set<String> WRITE_METHOD_PREFIXES = new HashSet<>(Arrays.asList(
            "insert", "add", "create", "save", "update", "modify", "edit", 
            "delete", "remove", "drop", "truncate", "batch"
    ));

    /**
     * 定义Mapper方法切点
     */
    @Pointcut("execution(* com.lx.gameserver..mapper..*.*(..))")
    public void mapperMethods() {}

    /**
     * 定义Service方法切点
     */
    @Pointcut("execution(* com.lx.gameserver..service..*.*(..))")
    public void serviceMethods() {}

    /**
     * 拦截Mapper和Service方法，进行数据源路由
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    @Around("mapperMethods() || serviceMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        
        logger.debug("拦截方法: {}.{}", method.getDeclaringClass().getSimpleName(), methodName);

        // 检查是否强制使用主库
        if (DataSourceContextHolder.isForceMaster()) {
            logger.debug("强制使用主库，跳过数据源路由");
            return joinPoint.proceed();
        }

        // 1. 优先检查@DataSource注解
        DataSource dataSourceAnnotation = AnnotationUtils.findAnnotation(method, DataSource.class);
        if (dataSourceAnnotation == null) {
            dataSourceAnnotation = AnnotationUtils.findAnnotation(method.getDeclaringClass(), DataSource.class);
        }

        DataSourceContextHolder.DataSourceType targetDataSource = null;
        
        if (dataSourceAnnotation != null) {
            // 注解指定的数据源
            targetDataSource = dataSourceAnnotation.value();
            logger.debug("使用注解指定数据源: {}", targetDataSource);
        } else {
            // 2. 检查是否在事务中
            if (isInTransaction(method)) {
                // 事务中强制使用主库，保证数据一致性
                targetDataSource = DataSourceContextHolder.DataSourceType.MASTER;
                logger.debug("事务中强制使用主库");
            } else {
                // 3. 根据方法名自动判断
                targetDataSource = determineDataSourceByMethodName(methodName);
                logger.debug("根据方法名确定数据源: {} -> {}", methodName, targetDataSource);
            }
        }

        // 设置数据源
        DataSourceContextHolder.setDataSourceType(targetDataSource);
        
        try {
            return joinPoint.proceed();
        } finally {
            // 方法执行完毕后移除数据源设置
            DataSourceContextHolder.removeDataSourceType();
            logger.debug("移除数据源设置: {}", targetDataSource);
        }
    }

    /**
     * 根据方法名确定数据源类型
     *
     * @param methodName 方法名
     * @return 数据源类型
     */
    private DataSourceContextHolder.DataSourceType determineDataSourceByMethodName(String methodName) {
        String lowerMethodName = methodName.toLowerCase();
        
        // 检查是否为读操作
        for (String prefix : READ_METHOD_PREFIXES) {
            if (lowerMethodName.startsWith(prefix)) {
                return DataSourceContextHolder.DataSourceType.SLAVE;
            }
        }
        
        // 检查是否为写操作
        for (String prefix : WRITE_METHOD_PREFIXES) {
            if (lowerMethodName.startsWith(prefix)) {
                return DataSourceContextHolder.DataSourceType.MASTER;
            }
        }
        
        // 默认使用主库
        logger.warn("无法通过方法名判断数据源类型，使用主库: {}", methodName);
        return DataSourceContextHolder.DataSourceType.MASTER;
    }

    /**
     * 检查方法是否在事务中
     *
     * @param method 方法
     * @return 是否在事务中
     */
    private boolean isInTransaction(Method method) {
        // 检查方法上的@Transactional注解
        Transactional methodTransactional = AnnotationUtils.findAnnotation(method, Transactional.class);
        if (methodTransactional != null) {
            // 如果是只读事务，仍然可以使用从库
            return !methodTransactional.readOnly();
        }
        
        // 检查类上的@Transactional注解
        Transactional classTransactional = AnnotationUtils.findAnnotation(method.getDeclaringClass(), Transactional.class);
        if (classTransactional != null) {
            return !classTransactional.readOnly();
        }
        
        return false;
    }

    /**
     * 数据源注解
     * <p>
     * 用于手动指定方法或类使用的数据源类型。
     * </p>
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface DataSource {
        
        /**
         * 数据源类型
         *
         * @return 数据源类型
         */
        DataSourceContextHolder.DataSourceType value() default DataSourceContextHolder.DataSourceType.MASTER;
    }
}