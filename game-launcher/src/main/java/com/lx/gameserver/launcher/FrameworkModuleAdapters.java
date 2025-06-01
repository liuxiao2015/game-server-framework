/*
 * 文件名: FrameworkModuleAdapters.java
 * 用途: 框架模块适配器集合
 * 内容: 
 *   - 为现有框架模块提供FrameworkModule接口适配
 *   - 统一不同模块的初始化接口
 *   - 提供模块状态查询和生命周期管理
 * 技术选型: 
 *   - 适配器模式
 *   - Spring组件注册
 *   - 模块化管理
 * 依赖关系: 
 *   - 适配具体的frame模块
 *   - 被GameServerFramework使用
 * 作者: liuxiao2015
 * 日期: 2025-05-31
 */
package com.lx.gameserver.launcher;

import com.lx.gameserver.frame.concurrent.executor.ExecutorManager;
import com.lx.gameserver.frame.concurrent.metrics.ConcurrentMetrics;
import com.lx.gameserver.frame.event.EventBus;
import com.lx.gameserver.frame.event.disruptor.DisruptorEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 并发模块适配器
 */
@Component
class ConcurrentModuleAdapter implements GameServerFramework.FrameworkModule {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentModuleAdapter.class);
    
    @Autowired(required = false)
    private ExecutorManager executorManager;
    
    @Autowired(required = false)
    private ConcurrentMetrics concurrentMetrics;
    
    private boolean initialized = false;
    private boolean started = false;
    
    @Override
    public String getModuleName() {
        return "concurrent";
    }
    
    @Override
    public int getPriority() {
        return 100; // 高优先级，其他模块可能依赖并发框架
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("初始化并发模块...");
        
        if (executorManager != null) {
            // ExecutorManager通常通过Spring自动初始化
            logger.info("ExecutorManager已通过Spring初始化");
        }
        
        if (concurrentMetrics != null) {
            logger.info("ConcurrentMetrics已通过Spring初始化");
        }
        
        initialized = true;
        logger.info("并发模块初始化完成");
    }
    
    @Override
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("模块未初始化");
        }
        
        logger.info("启动并发模块...");
        
        // 并发模块主要是提供服务，无需特殊启动操作
        started = true;
        logger.info("并发模块启动完成");
    }
    
    @Override
    public void stop() throws Exception {
        if (!started) {
            return;
        }
        
        logger.info("停止并发模块...");
        
        if (executorManager != null) {
            try {
                executorManager.shutdown();
                logger.info("ExecutorManager关闭完成");
            } catch (Exception e) {
                logger.error("ExecutorManager关闭失败", e);
            }
        }
        
        started = false;
        logger.info("并发模块停止完成");
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "未初始化";
        }
        if (!started) {
            return "已初始化未启动";
        }
        
        StringBuilder status = new StringBuilder("运行中");
        if (executorManager != null) {
            status.append(", ExecutorManager可用");
        }
        if (concurrentMetrics != null) {
            status.append(", 监控指标可用");
        }
        
        return status.toString();
    }
}

/**
 * 事件模块适配器
 */
@Component
class EventModuleAdapter implements GameServerFramework.FrameworkModule {
    
    private static final Logger logger = LoggerFactory.getLogger(EventModuleAdapter.class);
    
    private EventBus eventBus;
    private boolean initialized = false;
    private boolean started = false;
    
    @Override
    public String getModuleName() {
        return "event";
    }
    
    @Override
    public int getPriority() {
        return 200; // 中高优先级，很多模块可能需要事件总线
    }
    
    @Override
    public List<String> getDependencies() {
        return List.of("concurrent"); // 依赖并发模块
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("初始化事件模块...");
        
        // 创建事件总线实例
        eventBus = new DisruptorEventBus("GameServerEventBus");
        
        initialized = true;
        logger.info("事件模块初始化完成");
    }
    
    @Override
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("模块未初始化");
        }
        
        logger.info("启动事件模块...");
        
        // 事件总线已在构造时启动
        started = true;
        logger.info("事件模块启动完成");
    }
    
    @Override
    public void stop() throws Exception {
        if (!started) {
            return;
        }
        
        logger.info("停止事件模块...");
        
        if (eventBus != null) {
            try {
                eventBus.shutdown();
                logger.info("事件总线关闭完成");
            } catch (Exception e) {
                logger.error("事件总线关闭失败", e);
            }
        }
        
        started = false;
        logger.info("事件模块停止完成");
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "未初始化";
        }
        if (!started) {
            return "已初始化未启动";
        }
        
        return "运行中, EventBus可用";
    }
    
    /**
     * 获取事件总线实例（供其他组件使用）
     */
    public EventBus getEventBus() {
        return eventBus;
    }
}

/**
 * 网络模块适配器
 */
@Component
class NetworkModuleAdapter implements GameServerFramework.FrameworkModule {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkModuleAdapter.class);
    
    private boolean initialized = false;
    private boolean started = false;
    
    @Override
    public String getModuleName() {
        return "network";
    }
    
    @Override
    public int getPriority() {
        return 300; // 中等优先级
    }
    
    @Override
    public List<String> getDependencies() {
        return List.of("concurrent", "event"); // 依赖并发和事件模块
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("初始化网络模块...");
        
        // 网络模块相关的初始化
        // 这里可以初始化Netty相关配置
        
        initialized = true;
        logger.info("网络模块初始化完成");
    }
    
    @Override
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("模块未初始化");
        }
        
        logger.info("启动网络模块...");
        
        // 这里可以启动Netty服务器
        // 由于当前是框架层集成，具体的服务器启动留给业务层
        
        started = true;
        logger.info("网络模块启动完成");
    }
    
    @Override
    public void stop() throws Exception {
        if (!started) {
            return;
        }
        
        logger.info("停止网络模块...");
        
        // 关闭网络连接和服务器
        
        started = false;
        logger.info("网络模块停止完成");
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "未初始化";
        }
        if (!started) {
            return "已初始化未启动";
        }
        
        return "运行中";
    }
}

/**
 * 缓存模块适配器
 */
@Component
class CacheModuleAdapter implements GameServerFramework.FrameworkModule {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheModuleAdapter.class);
    
    private boolean initialized = false;
    private boolean started = false;
    
    @Override
    public String getModuleName() {
        return "cache";
    }
    
    @Override
    public int getPriority() {
        return 400; // 中等优先级
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("初始化缓存模块...");
        
        // 缓存模块相关的初始化
        // 这里可以初始化本地缓存和Redis配置
        
        initialized = true;
        logger.info("缓存模块初始化完成");
    }
    
    @Override
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("模块未初始化");
        }
        
        logger.info("启动缓存模块...");
        
        // 启动缓存相关服务
        
        started = true;
        logger.info("缓存模块启动完成");
    }
    
    @Override
    public void stop() throws Exception {
        if (!started) {
            return;
        }
        
        logger.info("停止缓存模块...");
        
        // 关闭缓存连接
        
        started = false;
        logger.info("缓存模块停止完成");
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "未初始化";
        }
        if (!started) {
            return "已初始化未启动";
        }
        
        return "运行中";
    }
}

/**
 * 数据库模块适配器
 */
@Component
class DatabaseModuleAdapter implements GameServerFramework.FrameworkModule {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseModuleAdapter.class);
    
    private boolean initialized = false;
    private boolean started = false;
    
    @Override
    public String getModuleName() {
        return "database";
    }
    
    @Override
    public int getPriority() {
        return 500; // 中等优先级
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("初始化数据库模块...");
        
        // 数据库模块相关的初始化
        
        initialized = true;
        logger.info("数据库模块初始化完成");
    }
    
    @Override
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("模块未初始化");
        }
        
        logger.info("启动数据库模块...");
        
        // 启动数据库连接池等
        
        started = true;
        logger.info("数据库模块启动完成");
    }
    
    @Override
    public void stop() throws Exception {
        if (!started) {
            return;
        }
        
        logger.info("停止数据库模块...");
        
        // 关闭数据库连接
        
        started = false;
        logger.info("数据库模块停止完成");
    }
    
    @Override
    public String getStatus() {
        if (!initialized) {
            return "未初始化";
        }
        if (!started) {
            return "已初始化未启动";
        }
        
        return "运行中";
    }
}