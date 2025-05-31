/*
 * 文件名: Ranking.java
 * 用途: 排行榜基类抽象定义
 * 实现内容:
 *   - 定义排行榜的核心抽象方法
 *   - 提供排行榜的基础属性和配置
 *   - 定义生命周期管理方法
 *   - 支持扩展和自定义实现
 * 技术选型:
 *   - 使用抽象类提供框架结构
 *   - 支持模板方法模式
 *   - 集成Spring生命周期管理
 * 依赖关系:
 *   - 被具体排行榜实现类继承
 *   - 被排行榜管理器调用
 *   - 依赖排行榜存储接口
 */
package com.lx.gameserver.business.ranking.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 排行榜基类（抽象类）
 * <p>
 * 定义了排行榜系统的核心框架，包括排行榜的基本属性、
 * 生命周期管理、抽象方法定义等。所有具体的排行榜实现
 * 都需要继承此类并实现抽象方法。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Data
@Slf4j
public abstract class Ranking implements InitializingBean, DisposableBean {

    /**
     * 排行榜ID
     */
    protected String rankingId;

    /**
     * 排行榜名称
     */
    protected String rankingName;

    /**
     * 排行榜描述
     */
    protected String description;

    /**
     * 排行榜类型
     */
    protected RankingType rankingType;

    /**
     * 排行榜范围
     */
    protected RankingScope rankingScope;

    /**
     * 排序规则（true:降序，false:升序）
     */
    protected boolean descending = true;

    /**
     * 榜单容量（TOP N）
     */
    protected int capacity = 100;

    /**
     * 是否启用
     */
    protected boolean enabled = true;

    /**
     * 更新策略（REALTIME:实时，BATCH:批量，SCHEDULED:定时）
     */
    protected UpdateStrategy updateStrategy = UpdateStrategy.REALTIME;

    /**
     * 更新间隔（毫秒）
     */
    protected long updateInterval = 60000; // 1分钟

    /**
     * 是否支持历史记录
     */
    protected boolean historyEnabled = true;

    /**
     * 是否支持赛季模式
     */
    protected boolean seasonEnabled = false;

    /**
     * 创建时间
     */
    protected LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    protected LocalDateTime lastUpdateTime;

    /**
     * 扩展配置
     */
    protected Map<String, Object> extraConfig;

    /**
     * 运行状态
     */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 更新计数器
     */
    protected final AtomicLong updateCount = new AtomicLong(0);

    /**
     * 查询计数器
     */
    protected final AtomicLong queryCount = new AtomicLong(0);

    /**
     * 更新策略枚举
     */
    public enum UpdateStrategy {
        /** 实时更新 */
        REALTIME,
        /** 批量更新 */
        BATCH,
        /** 定时更新 */
        SCHEDULED
    }

    /**
     * 排序规则枚举
     */
    public enum SortOrder {
        /** 升序 */
        ASC,
        /** 降序 */
        DESC
    }

    /**
     * 构造函数
     *
     * @param rankingId   排行榜ID
     * @param rankingName 排行榜名称
     * @param rankingType 排行榜类型
     */
    protected Ranking(String rankingId, String rankingName, RankingType rankingType) {
        this.rankingId = rankingId;
        this.rankingName = rankingName;
        this.rankingType = rankingType;
        this.rankingScope = RankingScope.SERVER; // 默认服务器范围
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = this.createTime;
    }

    // ===== 抽象方法定义 =====

    /**
     * 计算实体分数
     * 不同类型的排行榜需要实现不同的分数计算逻辑
     *
     * @param entityId   实体ID
     * @param entityData 实体数据
     * @return 计算出的分数
     */
    protected abstract Long calculateScore(Long entityId, Map<String, Object> entityData);

    /**
     * 获取实体信息
     * 获取实体的基本信息，如名称、等级、头像等
     *
     * @param entityId 实体ID
     * @return 实体信息
     */
    protected abstract Map<String, Object> getEntityInfo(Long entityId);

    /**
     * 验证实体是否有效
     * 检查实体是否符合上榜条件
     *
     * @param entityId   实体ID
     * @param entityData 实体数据
     * @return 是否有效
     */
    protected abstract boolean isEntityValid(Long entityId, Map<String, Object> entityData);

    /**
     * 处理排名变化事件
     * 当排名发生变化时的回调处理
     *
     * @param oldEntry 旧的排行榜条目
     * @param newEntry 新的排行榜条目
     */
    protected abstract void onRankChanged(RankingEntry oldEntry, RankingEntry newEntry);

    /**
     * 自定义初始化逻辑
     * 子类可以重写此方法实现自定义的初始化逻辑
     */
    protected abstract void doInitialize();

    /**
     * 自定义销毁逻辑
     * 子类可以重写此方法实现自定义的清理逻辑
     */
    protected abstract void doDestroy();

    // ===== 公共方法定义 =====

    /**
     * 获取前N名
     *
     * @param topN 前N名
     * @return 排行榜条目列表
     */
    public abstract List<RankingEntry> getTopEntries(int topN);

    /**
     * 获取指定范围的排名
     *
     * @param start 起始排名
     * @param end   结束排名
     * @return 排行榜条目列表
     */
    public abstract List<RankingEntry> getRangeEntries(int start, int end);

    /**
     * 获取实体排名信息
     *
     * @param entityId 实体ID
     * @return 排行榜条目
     */
    public abstract RankingEntry getEntityRank(Long entityId);

    /**
     * 获取实体周围的排名
     *
     * @param entityId 实体ID
     * @param range    周围范围
     * @return 排行榜条目列表
     */
    public abstract List<RankingEntry> getSurroundingEntries(Long entityId, int range);

    /**
     * 获取排行榜总数
     *
     * @return 总数
     */
    public abstract long getCount();

    /**
     * 启动排行榜
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("启动排行榜: {}", rankingName);
            try {
                doInitialize();
                log.info("排行榜启动成功: {}", rankingName);
            } catch (Exception e) {
                running.set(false);
                log.error("排行榜启动失败: " + rankingName, e);
                throw new RuntimeException("排行榜启动失败", e);
            }
        }
    }

    /**
     * 停止排行榜
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("停止排行榜: {}", rankingName);
            try {
                doDestroy();
                log.info("排行榜停止成功: {}", rankingName);
            } catch (Exception e) {
                log.error("排行榜停止失败: " + rankingName, e);
            }
        }
    }

    /**
     * 检查排行榜是否运行中
     *
     * @return 是否运行中
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 更新最后更新时间
     */
    protected void updateLastUpdateTime() {
        this.lastUpdateTime = LocalDateTime.now();
        this.updateCount.incrementAndGet();
    }

    /**
     * 增加查询计数
     */
    protected void incrementQueryCount() {
        this.queryCount.incrementAndGet();
    }

    /**
     * 获取更新次数
     *
     * @return 更新次数
     */
    public long getUpdateCount() {
        return updateCount.get();
    }

    /**
     * 获取查询次数
     *
     * @return 查询次数
     */
    public long getQueryCount() {
        return queryCount.get();
    }

    /**
     * 设置扩展配置
     *
     * @param key   配置键
     * @param value 配置值
     */
    public void setExtraConfig(String key, Object value) {
        if (this.extraConfig == null) {
            this.extraConfig = new java.util.HashMap<>();
        }
        this.extraConfig.put(key, value);
    }

    /**
     * 获取扩展配置
     *
     * @param key 配置键
     * @return 配置值
     */
    public Object getExtraConfig(String key) {
        return this.extraConfig != null ? this.extraConfig.get(key) : null;
    }

    /**
     * 获取扩展配置（带默认值）
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtraConfig(String key, T defaultValue) {
        Object value = getExtraConfig(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 获取排序规则
     *
     * @return 排序规则
     */
    public SortOrder getSortOrder() {
        return descending ? SortOrder.DESC : SortOrder.ASC;
    }

    /**
     * 设置排序规则
     *
     * @param sortOrder 排序规则
     */
    public void setSortOrder(SortOrder sortOrder) {
        this.descending = (sortOrder == SortOrder.DESC);
    }

    /**
     * 检查配置是否有效
     *
     * @return 是否有效
     */
    public boolean isConfigValid() {
        return rankingId != null && !rankingId.trim().isEmpty()
               && rankingName != null && !rankingName.trim().isEmpty()
               && rankingType != null
               && capacity > 0;
    }

    // ===== Spring生命周期方法 =====

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!isConfigValid()) {
            throw new IllegalStateException("排行榜配置无效: " + rankingId);
        }
        start();
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }

    @Override
    public String toString() {
        return String.format("Ranking{id='%s', name='%s', type=%s, scope=%s, capacity=%d, enabled=%s}", 
                           rankingId, rankingName, rankingType, rankingScope, capacity, enabled);
    }
}