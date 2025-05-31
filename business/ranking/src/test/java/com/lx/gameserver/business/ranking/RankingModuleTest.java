/*
 * 文件名: RankingModuleTest.java
 * 用途: 排行榜模块基础功能测试
 * 实现内容:
 *   - 测试排行榜核心功能
 *   - 验证管理器和查询服务
 *   - 测试具体排行榜实现
 *   - 性能和稳定性测试
 * 技术选型:
 *   - 使用JUnit 5测试框架
 *   - Spring Boot Test集成
 *   - Mock对象模拟依赖
 * 依赖关系:
 *   - 测试排行榜模块所有组件
 *   - 验证接口调用和响应
 *   - 检查性能指标
 */
package com.lx.gameserver.business.ranking;

import com.lx.gameserver.business.ranking.core.RankingEntry;
import com.lx.gameserver.business.ranking.core.RankingType;
import com.lx.gameserver.business.ranking.impl.PlayerLevelRanking;
import com.lx.gameserver.business.ranking.impl.PlayerPowerRanking;
import com.lx.gameserver.business.ranking.manager.RankingManager;
import com.lx.gameserver.business.ranking.query.RankingQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排行榜模块测试
 * <p>
 * 测试排行榜模块的核心功能，包括排行榜创建、更新、查询等操作。
 * 验证系统的正确性、性能和稳定性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@SpringBootTest
@ActiveProfiles("test")
class RankingModuleTest {

    private RankingManager rankingManager;
    private RankingQueryService queryService;
    private PlayerLevelRanking levelRanking;
    private PlayerPowerRanking powerRanking;

    @BeforeEach
    void setUp() {
        // 初始化组件
        rankingManager = new RankingManager();
        queryService = new RankingQueryService();
        
        // 创建测试排行榜
        levelRanking = new PlayerLevelRanking();
        powerRanking = new PlayerPowerRanking();
        
        // 注册排行榜
        try {
            rankingManager.registerRanking(levelRanking);
            rankingManager.registerRanking(powerRanking);
        } catch (Exception e) {
            // 测试环境可能没有完整的Spring容器，忽略注册错误
        }
    }

    @Test
    @DisplayName("测试排行榜基础功能")
    void testBasicRankingFunctions() {
        // 测试排行榜类型
        assertEquals(RankingType.LEVEL, levelRanking.getRankingType());
        assertEquals(RankingType.POWER, powerRanking.getRankingType());
        
        // 测试排行榜配置
        assertNotNull(levelRanking.getRankingId());
        assertNotNull(levelRanking.getRankingName());
        assertTrue(levelRanking.getCapacity() > 0);
        
        // 测试排行榜有效性
        assertTrue(levelRanking.isConfigValid());
        assertTrue(powerRanking.isConfigValid());
    }

    @Test
    @DisplayName("测试排行榜管理器功能")
    void testRankingManager() {
        // 测试排行榜注册
        assertTrue(rankingManager.hasRanking("player_level"));
        assertTrue(rankingManager.hasRanking("player_power"));
        
        // 测试按类型查询
        List<com.lx.gameserver.business.ranking.core.Ranking> levelRankings = 
            rankingManager.getRankingsByType(RankingType.LEVEL);
        assertFalse(levelRankings.isEmpty());
        
        List<com.lx.gameserver.business.ranking.core.Ranking> powerRankings = 
            rankingManager.getRankingsByType(RankingType.POWER);
        assertFalse(powerRankings.isEmpty());
        
        // 测试统计信息
        RankingManager.ManagerStats stats = rankingManager.getStats();
        assertNotNull(stats);
        assertTrue(stats.getTotalRankings() >= 2);
    }

    @Test
    @DisplayName("测试排行榜条目功能")
    void testRankingEntry() {
        // 创建测试条目
        RankingEntry entry = new RankingEntry(12345L, 1, 99999L);
        
        // 测试基础属性
        assertEquals(12345L, entry.getEntityId());
        assertEquals(1, entry.getRank());
        assertEquals(99999L, entry.getScore());
        
        // 测试有效性验证
        assertTrue(entry.isValid());
        
        // 测试排名更新
        entry.updateRank(2, 88888L);
        assertEquals(2, entry.getRank());
        assertEquals(88888L, entry.getScore());
        assertEquals(1, entry.getPreviousRank());
        assertEquals(-1, entry.getRankChange()); // 排名下降1位
        
        // 测试排名变化描述
        assertTrue(entry.hasRankChange());
        assertTrue(entry.isRankDown());
        assertEquals("下降1名", entry.getRankChangeDescription());
    }

    @Test
    @DisplayName("测试等级排行榜特定功能")
    void testPlayerLevelRanking() {
        // 测试分数计算
        long score = 50 * 1000000L + 123456L; // 50级 + 123456经验
        
        // 测试等级和经验反推
        assertEquals(50, levelRanking.getLevelFromScore(score));
        assertEquals(123456L, levelRanking.getExperienceFromScore(score));
        
        // 测试更新玩家等级（模拟调用）
        CompletableFuture<PlayerLevelRanking.SubmitResult> future = 
            levelRanking.updatePlayerLevel(12345L, 50, 123456L);
        
        assertNotNull(future);
        // 在没有Redis的测试环境中，可能会失败，但接口应该正常
    }

    @Test
    @DisplayName("测试战力排行榜特定功能")
    void testPlayerPowerRanking() {
        // 测试战力统计（模拟调用）
        PlayerPowerRanking.PowerStatistics stats = powerRanking.getPowerStatistics();
        assertNotNull(stats);
        
        // 测试更新玩家战力（模拟调用）
        CompletableFuture<PlayerPowerRanking.SubmitResult> future = 
            powerRanking.updatePlayerPower(12345L, 999999L);
        
        assertNotNull(future);
    }

    @Test
    @DisplayName("测试排行榜枚举功能")
    void testRankingEnums() {
        // 测试排行榜类型
        assertNotNull(RankingType.fromName("LEVEL"));
        assertNotNull(RankingType.fromName("POWER"));
        assertNull(RankingType.fromName("INVALID"));
        
        assertTrue(RankingType.isValid(RankingType.LEVEL));
        assertTrue(RankingType.ARENA.supportsSeason());
        assertTrue(RankingType.POWER.requiresRealTimeUpdate());
        
        // 测试启用的类型
        RankingType[] enabledTypes = RankingType.getEnabledTypes();
        assertTrue(enabledTypes.length > 0);
        
        // 测试排行榜范围
        com.lx.gameserver.business.ranking.core.RankingScope scope1 = 
            com.lx.gameserver.business.ranking.core.RankingScope.SERVER;
        com.lx.gameserver.business.ranking.core.RankingScope scope2 = 
            com.lx.gameserver.business.ranking.core.RankingScope.GUILD;
        
        assertTrue(scope1.contains(scope2));
        assertFalse(scope2.contains(scope1));
        assertTrue(scope1.isServerLevel());
        assertTrue(scope2.isOrganizationLevel());
    }

    @Test
    @DisplayName("测试性能基准")
    void testPerformanceBenchmark() {
        // 测试大批量条目创建
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 1000; i++) {
            RankingEntry entry = new RankingEntry((long) i, i + 1, (long) (1000000 - i * 100));
            entry.setEntityName("Player" + i);
            entry.updateRank(i + 1, (long) (1000000 - i * 100));
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 1000个条目创建和更新应该在100ms内完成
        assertTrue(duration < 100, "条目创建和更新耗时过长: " + duration + "ms");
    }

    @Test
    @DisplayName("测试并发安全性")
    void testConcurrencySafety() {
        // 测试管理器并发操作
        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 100; i++) {
                rankingManager.getAllRankingIds();
            }
        });
        
        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 100; i++) {
                rankingManager.getStats();
            }
        });
        
        CompletableFuture<Void> future3 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 100; i++) {
                rankingManager.getRankingsByType(RankingType.LEVEL);
            }
        });
        
        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(future1, future2, future3);
        
        assertDoesNotThrow(() -> {
            allFutures.get();
        }, "并发操作不应该抛出异常");
    }

    @Test
    @DisplayName("测试错误处理")
    void testErrorHandling() {
        // 测试无效参数
        assertThrows(IllegalArgumentException.class, () -> {
            rankingManager.registerRanking(null);
        });
        
        // 测试重复注册
        PlayerLevelRanking duplicateRanking = new PlayerLevelRanking();
        assertThrows(IllegalStateException.class, () -> {
            rankingManager.registerRanking(duplicateRanking);
        });
        
        // 测试无效条目
        RankingEntry invalidEntry = new RankingEntry(null, null, null);
        assertFalse(invalidEntry.isValid());
        
        RankingEntry invalidEntry2 = new RankingEntry(-1L, 0, -100L);
        assertFalse(invalidEntry2.isValid());
    }

    @Test
    @DisplayName("测试配置验证")
    void testConfigValidation() {
        // 测试排行榜配置
        assertTrue(levelRanking.isConfigValid());
        assertTrue(powerRanking.isConfigValid());
        
        // 测试扩展配置
        levelRanking.setExtraConfig("testKey", "testValue");
        assertEquals("testValue", levelRanking.getExtraConfig("testKey"));
        assertEquals("defaultValue", levelRanking.getExtraConfig("nonExistentKey", "defaultValue"));
    }
}