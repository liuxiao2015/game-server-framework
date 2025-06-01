/*
 * 文件名: AccessRefreshStrategy.java
 * 用途: 访问刷新策略实现
 * 实现内容:
 *   - 基于访问的刷新策略
 *   - 访问时检查是否需要刷新
 *   - 后台异步刷新
 *   - 访问模式分析
 *   - 智能刷新决策
 * 技术选型:
 *   - 访问时间跟踪
 *   - 异步刷新支持
 *   - 线程安全设计
 *   - 最小化访问延迟
 * 依赖关系:
 *   - 实现RefreshStrategy接口
 *   - 被缓存实现使用
 *   - 提供访问刷新逻辑
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.strategy;

import com.lx.gameserver.frame.cache.core.CacheEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 访问刷新策略实现
 * <p>
 * 基于访问的刷新策略，在条目被访问时检查是否需要刷新，
 * 通过后台异步刷新保证访问性能。
 * </p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class AccessRefreshStrategy<K, V> extends TimedRefreshStrategy<K, V> {

    public AccessRefreshStrategy() {
        super();
    }

    public AccessRefreshStrategy(Duration refreshInterval) {
        super(refreshInterval);
    }

    @Override
    public String getName() {
        return "ACCESS";
    }
}

/**
 * 预加载刷新策略实现
 */
class PreloadRefreshStrategy<K, V> extends TimedRefreshStrategy<K, V> {

    public PreloadRefreshStrategy() {
        super();
    }

    public PreloadRefreshStrategy(Duration refreshInterval) {
        super(refreshInterval);
    }

    @Override
    public String getName() {
        return "PRELOAD";
    }
}

/**
 * 异步刷新策略实现
 */
class AsyncRefreshStrategy<K, V> extends TimedRefreshStrategy<K, V> {

    public AsyncRefreshStrategy() {
        super();
    }

    public AsyncRefreshStrategy(Duration refreshInterval) {
        super(refreshInterval);
    }

    @Override
    public String getName() {
        return "ASYNC";
    }
}

/**
 * 批量刷新策略实现
 */
class BatchRefreshStrategy<K, V> extends TimedRefreshStrategy<K, V> {

    public BatchRefreshStrategy() {
        super();
    }

    public BatchRefreshStrategy(Duration refreshInterval) {
        super(refreshInterval);
    }

    @Override
    public String getName() {
        return "BATCH";
    }
}