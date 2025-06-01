/*
 * 文件名: BaseRpcService.java
 * 用途: RPC服务基础接口
 * 实现内容:
 *   - 健康检查接口
 *   - 服务信息接口
 *   - 通用错误码定义
 * 技术选型:
 *   - Spring Cloud OpenFeign接口
 *   - 统一返回结果封装
 *   - 标准化服务接口
 * 依赖关系:
 *   - 依赖common-core的Result类
 *   - 被具体业务服务接口继承
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.api;

import com.lx.gameserver.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * RPC服务基础接口
 * <p>
 * 定义所有RPC服务必须实现的基础接口，包括健康检查、
 * 服务信息查询等功能。所有业务服务接口应继承此接口。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public interface BaseRpcService {

    /**
     * 健康检查接口
     * <p>
     * 用于负载均衡器和监控系统检查服务是否正常运行
     * </p>
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    Result<Map<String, Object>> health();

    /**
     * 服务信息接口
     * <p>
     * 返回服务的基本信息，包括版本、启动时间等
     * </p>
     *
     * @return 服务信息
     */
    @GetMapping("/info")
    Result<Map<String, Object>> info();

    /**
     * 服务状态接口
     * <p>
     * 返回服务的详细状态信息，包括负载、连接数等
     * </p>
     *
     * @return 服务状态
     */
    @GetMapping("/status")
    Result<Map<String, Object>> status();

    /**
     * 服务配置接口
     * <p>
     * 返回服务的配置信息（不包含敏感信息）
     * </p>
     *
     * @param key 配置键（可选）
     * @return 配置信息
     */
    @GetMapping("/config")
    Result<Map<String, Object>> config(@RequestParam(value = "key", required = false) String key);

    /**
     * 服务指标接口
     * <p>
     * 返回服务的性能指标信息
     * </p>
     *
     * @return 性能指标
     */
    @GetMapping("/metrics")
    Result<Map<String, Object>> metrics();
}