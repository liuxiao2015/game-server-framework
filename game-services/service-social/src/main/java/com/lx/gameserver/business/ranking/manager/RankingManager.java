/*
 * 文件名: RankingManager.java
 * 用途: 排行榜管理器核心实现
 * 实现内容:
 *   - 排行榜注册和注销管理
 *   - 排行榜实例管理和生命周期控制
 *   - 全局排行榜配置和监控
 *   - 排行榜服务路由和调度
 * 技术选型:
 *   - 使用Spring管理Bean生命周期
 *   - 集成定时任务调度
 *   - 支持多线程安全访问
 * 依赖关系:
 *   - 管理所有排行榜实例
 *   - 被API层和业务层调用
 *   - 依赖存储和缓存组件
 */
package com.lx.gameserver.business.ranking.manager;

import com.lx.gameserver.business.ranking.core.Ranking;
import com.lx.gameserver.business.ranking.core.RankingType;
import com.lx.gameserver.business.ranking.core.RankingScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 排行榜管理器
 * <p>
 * 负责所有排行榜的注册、管理、生命周期控制和路由。
 * 提供线程安全的排行榜访问和管理功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class RankingManager implements InitializingBean, DisposableBean {

    /**
     * 排行榜实例存储
     * Key: 排行榜ID, Value: 排行榜实例
     */
    private final Map<String, Ranking> rankings = new ConcurrentHashMap<>();

    /**
     * 类型索引
     * Key: 排行榜类型, Value: 排行榜ID集合
     */
    private final Map<RankingType, Set<String>> typeIndex = new ConcurrentHashMap<>();

    /**
     * 范围索引
     * Key: 排行榜范围, Value: 排行榜ID集合
     */
    private final Map<RankingScope, Set<String>> scopeIndex = new ConcurrentHashMap<>();

    /**
     * 读写锁，保护索引操作
     */
    private final ReadWriteLock indexLock = new ReentrantReadWriteLock();

    /**
     * 管理器状态
     */
    private volatile boolean initialized = false;

    /**
     * 注册排行榜
     *
     * @param ranking 排行榜实例
     * @throws IllegalArgumentException 如果排行榜无效
     * @throws IllegalStateException    如果排行榜ID已存在
     */
    public void registerRanking(Ranking ranking) {
        if (ranking == null) {
            throw new IllegalArgumentException("排行榜实例不能为null");
        }
        
        if (!ranking.isConfigValid()) {
            throw new IllegalArgumentException("排行榜配置无效: " + ranking.getRankingId());
        }

        String rankingId = ranking.getRankingId();
        
        indexLock.writeLock().lock();
        try {
            if (rankings.containsKey(rankingId)) {
                throw new IllegalStateException("排行榜ID已存在: " + rankingId);
            }
            
            // 注册排行榜实例
            rankings.put(rankingId, ranking);
            
            // 更新类型索引
            RankingType type = ranking.getRankingType();
            typeIndex.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(rankingId);
            
            // 更新范围索引
            RankingScope scope = ranking.getRankingScope();
            scopeIndex.computeIfAbsent(scope, k -> ConcurrentHashMap.newKeySet()).add(rankingId);
            
            log.info("排行榜注册成功: {}", rankingId);
            
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 注销排行榜
     *
     * @param rankingId 排行榜ID
     * @return 是否注销成功
     */
    public boolean unregisterRanking(String rankingId) {
        if (rankingId == null || rankingId.trim().isEmpty()) {
            return false;
        }

        indexLock.writeLock().lock();
        try {
            Ranking ranking = rankings.remove(rankingId);
            if (ranking != null) {
                // 停止排行榜
                if (ranking.isRunning()) {
                    ranking.stop();
                }
                
                // 更新类型索引
                RankingType type = ranking.getRankingType();
                Set<String> typeRankings = typeIndex.get(type);
                if (typeRankings != null) {
                    typeRankings.remove(rankingId);
                    if (typeRankings.isEmpty()) {
                        typeIndex.remove(type);
                    }
                }
                
                // 更新范围索引
                RankingScope scope = ranking.getRankingScope();
                Set<String> scopeRankings = scopeIndex.get(scope);
                if (scopeRankings != null) {
                    scopeRankings.remove(rankingId);
                    if (scopeRankings.isEmpty()) {
                        scopeIndex.remove(scope);
                    }
                }
                
                log.info("排行榜注销成功: {}", rankingId);
                return true;
            }
            
            return false;
            
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 获取排行榜实例
     *
     * @param rankingId 排行榜ID
     * @return 排行榜实例，如果不存在返回null
     */
    public Ranking getRanking(String rankingId) {
        if (rankingId == null || rankingId.trim().isEmpty()) {
            return null;
        }
        return rankings.get(rankingId);
    }

    /**
     * 检查排行榜是否存在
     *
     * @param rankingId 排行榜ID
     * @return 是否存在
     */
    public boolean hasRanking(String rankingId) {
        return rankings.containsKey(rankingId);
    }

    /**
     * 获取所有排行榜ID
     *
     * @return 排行榜ID集合
     */
    public Set<String> getAllRankingIds() {
        return new HashSet<>(rankings.keySet());
    }

    /**
     * 获取所有排行榜实例
     *
     * @return 排行榜实例集合
     */
    public Collection<Ranking> getAllRankings() {
        return new ArrayList<>(rankings.values());
    }

    /**
     * 根据类型获取排行榜
     *
     * @param type 排行榜类型
     * @return 排行榜实例列表
     */
    public List<Ranking> getRankingsByType(RankingType type) {
        if (type == null) {
            return Collections.emptyList();
        }

        indexLock.readLock().lock();
        try {
            Set<String> rankingIds = typeIndex.get(type);
            if (rankingIds == null || rankingIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            return rankingIds.stream()
                    .map(rankings::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                    
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * 根据范围获取排行榜
     *
     * @param scope 排行榜范围
     * @return 排行榜实例列表
     */
    public List<Ranking> getRankingsByScope(RankingScope scope) {
        if (scope == null) {
            return Collections.emptyList();
        }

        indexLock.readLock().lock();
        try {
            Set<String> rankingIds = scopeIndex.get(scope);
            if (rankingIds == null || rankingIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            return rankingIds.stream()
                    .map(rankings::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                    
        } finally {
            indexLock.readLock().unlock();
        }
    }

    /**
     * 启动所有排行榜
     */
    public void startAllRankings() {
        log.info("启动所有排行榜，共{}个", rankings.size());
        
        for (Ranking ranking : rankings.values()) {
            try {
                if (!ranking.isRunning() && ranking.isEnabled()) {
                    ranking.start();
                }
            } catch (Exception e) {
                log.error("启动排行榜失败: " + ranking.getRankingId(), e);
            }
        }
        
        log.info("所有排行榜启动完成");
    }

    /**
     * 停止所有排行榜
     */
    public void stopAllRankings() {
        log.info("停止所有排行榜，共{}个", rankings.size());
        
        for (Ranking ranking : rankings.values()) {
            try {
                if (ranking.isRunning()) {
                    ranking.stop();
                }
            } catch (Exception e) {
                log.error("停止排行榜失败: " + ranking.getRankingId(), e);
            }
        }
        
        log.info("所有排行榜停止完成");
    }

    /**
     * 重启排行榜
     *
     * @param rankingId 排行榜ID
     * @return 是否重启成功
     */
    public boolean restartRanking(String rankingId) {
        Ranking ranking = getRanking(rankingId);
        if (ranking == null) {
            log.warn("排行榜不存在: {}", rankingId);
            return false;
        }
        
        try {
            if (ranking.isRunning()) {
                ranking.stop();
            }
            ranking.start();
            log.info("排行榜重启成功: {}", rankingId);
            return true;
        } catch (Exception e) {
            log.error("排行榜重启失败: " + rankingId, e);
            return false;
        }
    }

    /**
     * 获取管理器统计信息
     *
     * @return 统计信息
     */
    public ManagerStats getStats() {
        int totalCount = rankings.size();
        int runningCount = (int) rankings.values().stream()
                .filter(Ranking::isRunning)
                .count();
        int enabledCount = (int) rankings.values().stream()
                .filter(Ranking::isEnabled)
                .count();
        
        return new ManagerStats(totalCount, runningCount, enabledCount, 
                               typeIndex.size(), scopeIndex.size());
    }

    /**
     * 检查健康状态
     *
     * @return 是否健康
     */
    public boolean isHealthy() {
        if (!initialized) {
            return false;
        }
        
        // 检查是否有启用但未运行的排行榜
        long problematicCount = rankings.values().stream()
                .filter(ranking -> ranking.isEnabled() && !ranking.isRunning())
                .count();
                
        return problematicCount == 0;
    }

    /**
     * 获取支持的排行榜类型
     *
     * @return 排行榜类型集合
     */
    public Set<RankingType> getSupportedTypes() {
        return new HashSet<>(typeIndex.keySet());
    }

    /**
     * 获取支持的排行榜范围
     *
     * @return 排行榜范围集合
     */
    public Set<RankingScope> getSupportedScopes() {
        return new HashSet<>(scopeIndex.keySet());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化排行榜管理器");
        initialized = true;
    }

    @Override
    public void destroy() throws Exception {
        log.info("销毁排行榜管理器");
        stopAllRankings();
        rankings.clear();
        typeIndex.clear();
        scopeIndex.clear();
        initialized = false;
    }

    /**
     * 管理器统计信息
     */
    public static class ManagerStats {
        private final int totalRankings;
        private final int runningRankings;
        private final int enabledRankings;
        private final int supportedTypes;
        private final int supportedScopes;

        public ManagerStats(int totalRankings, int runningRankings, int enabledRankings,
                           int supportedTypes, int supportedScopes) {
            this.totalRankings = totalRankings;
            this.runningRankings = runningRankings;
            this.enabledRankings = enabledRankings;
            this.supportedTypes = supportedTypes;
            this.supportedScopes = supportedScopes;
        }

        public int getTotalRankings() { return totalRankings; }
        public int getRunningRankings() { return runningRankings; }
        public int getEnabledRankings() { return enabledRankings; }
        public int getSupportedTypes() { return supportedTypes; }
        public int getSupportedScopes() { return supportedScopes; }

        @Override
        public String toString() {
            return String.format("ManagerStats{total=%d, running=%d, enabled=%d, types=%d, scopes=%d}",
                               totalRankings, runningRankings, enabledRankings, supportedTypes, supportedScopes);
        }
    }
}