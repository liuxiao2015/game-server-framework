/*
 * 文件名: SmallestMailboxRouter.java
 * 用途: 最小邮箱路由器
 * 实现内容:
 *   - 根据邮箱队列长度选择路由目标
 *   - 负载均衡和性能优化
 *   - 邮箱监控和统计
 *   - 故障检测和恢复
 * 技术选型:
 *   - 实时邮箱监控和选择
 *   - 缓存机制提高选择性能
 *   - 原子操作保证线程安全
 * 依赖关系:
 *   - 继承Router基类
 *   - 与ActorMailbox集成监控
 *   - 支持负载均衡路由
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.routing;

import com.lx.gameserver.frame.actor.core.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 最小邮箱路由器
 * <p>
 * 选择当前邮箱队列长度最小的Actor作为路由目标，
 * 实现基于负载的智能路由。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class SmallestMailboxRouter extends Router {
    
    private static final Logger logger = LoggerFactory.getLogger(SmallestMailboxRouter.class);
    
    /** 邮箱大小缓存 */
    private final ConcurrentHashMap<String, MailboxInfo> mailboxCache = new ConcurrentHashMap<>();
    
    /** 缓存更新间隔 */
    private final Duration cacheUpdateInterval;
    
    /** 选择统计 */
    private final AtomicLong selectionCount = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    
    /** 负载均衡阈值 */
    private final int loadBalanceThreshold;
    
    public SmallestMailboxRouter(String name, RouterConfig config, Duration cacheUpdateInterval, int loadBalanceThreshold) {
        super(name, config);
        this.cacheUpdateInterval = cacheUpdateInterval;
        this.loadBalanceThreshold = loadBalanceThreshold;
        
        logger.info("最小邮箱路由器[{}]初始化完成，缓存更新间隔: {}, 负载均衡阈值: {}", 
                name, cacheUpdateInterval, loadBalanceThreshold);
    }
    
    public SmallestMailboxRouter(String name, RouterConfig config) {
        this(name, config, Duration.ofMillis(100), 10);
    }
    
    @Override
    protected RouteResult selectRoutees(Object message, ActorRef sender) {
        List<ActorRef> routees = getRoutees();
        
        if (routees.isEmpty()) {
            recordRouteFailure();
            return new RouteResult("没有可用的路由目标");
        }
        
        if (routees.size() == 1) {
            recordRouteSuccess();
            return new RouteResult(routees);
        }
        
        // 选择邮箱最小的Actor
        ActorRef selected = selectSmallestMailbox(routees);
        
        if (selected != null) {
            recordRouteSuccess();
            selectionCount.incrementAndGet();
            return new RouteResult(List.of(selected));
        } else {
            recordRouteFailure();
            return new RouteResult("未能选择到合适的路由目标");
        }
    }
    
    /**
     * 选择邮箱最小的Actor
     */
    private ActorRef selectSmallestMailbox(List<ActorRef> routees) {
        ActorRef selected = null;
        int smallestMailboxSize = Integer.MAX_VALUE;
        
        // 更新缓存并选择最小邮箱
        for (ActorRef routee : routees) {
            if (routee.isTerminated()) {
                continue;
            }
            
            int mailboxSize = getMailboxSize(routee);
            
            if (mailboxSize < smallestMailboxSize) {
                smallestMailboxSize = mailboxSize;
                selected = routee;
                
                // 如果找到空邮箱，直接返回
                if (mailboxSize == 0) {
                    break;
                }
            }
        }
        
        // 负载均衡检查
        if (selected != null && smallestMailboxSize > loadBalanceThreshold) {
            logger.debug("最小邮箱路由器[{}]检测到高负载，最小邮箱大小: {}", getName(), smallestMailboxSize);
        }
        
        return selected;
    }
    
    /**
     * 获取Actor的邮箱大小
     */
    private int getMailboxSize(ActorRef routee) {
        String actorPath = routee.getPath();
        MailboxInfo cachedInfo = mailboxCache.get(actorPath);
        Instant now = Instant.now();
        
        // 检查缓存是否有效
        if (cachedInfo != null && cachedInfo.isValid(now, cacheUpdateInterval)) {
            cacheHitCount.incrementAndGet();
            return cachedInfo.getMailboxSize();
        }
        
        // 缓存无效，获取实际邮箱大小
        cacheMissCount.incrementAndGet();
        int actualSize = getActualMailboxSize(routee);
        
        // 更新缓存
        mailboxCache.put(actorPath, new MailboxInfo(actualSize, now));
        
        return actualSize;
    }
    
    /**
     * 获取实际的邮箱大小
     */
    private int getActualMailboxSize(ActorRef routee) {
        // 这里需要与ActorMailbox集成来获取实际的邮箱大小
        // 在实际实现中，应该通过ActorSystem或其他机制来获取
        
        // 模拟邮箱大小获取
        try {
            // 这是一个简化的实现，实际应该调用Actor的邮箱监控接口
            return simulateMailboxSize(routee);
        } catch (Exception e) {
            logger.warn("获取Actor邮箱大小失败: {}, 使用默认值", routee.getPath(), e);
            return loadBalanceThreshold; // 返回阈值作为默认值
        }
    }
    
    /**
     * 模拟邮箱大小（实际实现中应该替换为真实的邮箱大小获取）
     */
    private int simulateMailboxSize(ActorRef routee) {
        // 这是一个模拟实现，实际应该与ActorMailbox集成
        return Math.abs(routee.getPath().hashCode()) % 20;
    }
    
    /**
     * 清理过期的缓存条目
     */
    public void cleanupCache() {
        Instant now = Instant.now();
        Duration expireTime = cacheUpdateInterval.multipliedBy(5); // 5倍更新间隔后过期
        
        mailboxCache.entrySet().removeIf(entry -> 
                !entry.getValue().isValid(now, expireTime));
        
        logger.debug("最小邮箱路由器[{}]缓存清理完成，剩余条目: {}", getName(), mailboxCache.size());
    }
    
    /**
     * 获取负载分布
     */
    public Map<String, Integer> getLoadDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        
        for (ActorRef routee : getRoutees()) {
            if (!routee.isTerminated()) {
                int mailboxSize = getMailboxSize(routee);
                distribution.put(routee.getPath(), mailboxSize);
            }
        }
        
        return distribution;
    }
    
    /**
     * 获取负载统计
     */
    public LoadStats getLoadStats() {
        Map<String, Integer> distribution = getLoadDistribution();
        
        if (distribution.isEmpty()) {
            return new LoadStats(0, 0, 0, 0.0);
        }
        
        int min = distribution.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = distribution.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double avg = distribution.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        return new LoadStats(min, max, distribution.size(), avg);
    }
    
    /**
     * 获取路由统计
     */
    public RoutingStats getRoutingStats() {
        return new RoutingStats(
                selectionCount.get(),
                cacheHitCount.get(),
                cacheMissCount.get(),
                mailboxCache.size()
        );
    }
    
    @Override
    public void removeRoutee(ActorRef routee) {
        super.removeRoutee(routee);
        mailboxCache.remove(routee.getPath());
    }
    
    /**
     * 邮箱信息缓存
     */
    private static class MailboxInfo {
        private final int mailboxSize;
        private final Instant timestamp;
        
        public MailboxInfo(int mailboxSize, Instant timestamp) {
            this.mailboxSize = mailboxSize;
            this.timestamp = timestamp;
        }
        
        public int getMailboxSize() {
            return mailboxSize;
        }
        
        public boolean isValid(Instant now, Duration validDuration) {
            return Duration.between(timestamp, now).compareTo(validDuration) <= 0;
        }
    }
    
    /**
     * 负载统计
     */
    public static class LoadStats {
        private final int minLoad;
        private final int maxLoad;
        private final int routeeCount;
        private final double averageLoad;
        
        public LoadStats(int minLoad, int maxLoad, int routeeCount, double averageLoad) {
            this.minLoad = minLoad;
            this.maxLoad = maxLoad;
            this.routeeCount = routeeCount;
            this.averageLoad = averageLoad;
        }
        
        public int getMinLoad() { return minLoad; }
        public int getMaxLoad() { return maxLoad; }
        public int getRouteeCount() { return routeeCount; }
        public double getAverageLoad() { return averageLoad; }
        public int getLoadSpread() { return maxLoad - minLoad; }
        public boolean isBalanced() { return getLoadSpread() <= 5; } // 认为差距小于5为平衡
        
        @Override
        public String toString() {
            return String.format("LoadStats{min=%d, max=%d, avg=%.1f, spread=%d, balanced=%s, routees=%d}",
                    minLoad, maxLoad, averageLoad, getLoadSpread(), isBalanced(), routeeCount);
        }
    }
    
    /**
     * 路由统计
     */
    public static class RoutingStats {
        private final long selectionCount;
        private final long cacheHits;
        private final long cacheMisses;
        private final int cacheSize;
        
        public RoutingStats(long selectionCount, long cacheHits, long cacheMisses, int cacheSize) {
            this.selectionCount = selectionCount;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheSize = cacheSize;
        }
        
        public long getSelectionCount() { return selectionCount; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public int getCacheSize() { return cacheSize; }
        public double getCacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("RoutingStats{selections=%d, cacheHits=%d, cacheMisses=%d, hitRate=%.1f%%, cacheSize=%d}",
                    selectionCount, cacheHits, cacheMisses, getCacheHitRate() * 100, cacheSize);
        }
    }
    
    @Override
    public String toString() {
        return String.format("SmallestMailboxRouter{name=%s, routees=%d, cacheSize=%d, threshold=%d}",
                getName(), getRouteeCount(), mailboxCache.size(), loadBalanceThreshold);
    }
}