/*
 * 文件名: ActivityModuleTest.java
 * 用途: 活动模块集成测试
 * 实现内容:
 *   - 测试活动模块的核心功能
 *   - 验证各组件的集成和协作
 *   - 测试活动生命周期管理
 *   - 验证进度追踪和奖励系统
 * 技术选型:
 *   - JUnit 5测试框架
 *   - Spring Boot Test
 *   - Mockito测试工具
 *   - 集成测试方法
 * 依赖关系:
 *   - 测试活动核心组件
 *   - 验证模块间协作
 *   - 保证代码质量
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.activity;

import com.lx.gameserver.business.activity.core.*;
import com.lx.gameserver.business.activity.manager.*;
import com.lx.gameserver.business.activity.progress.*;
import com.lx.gameserver.business.activity.reward.*;
import com.lx.gameserver.business.activity.template.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 活动模块集成测试
 * <p>
 * 测试活动模块的核心功能，验证各组件的集成和协作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class ActivityModuleTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityModuleTest.class);
    
    private ActivityManager activityManager;
    private ActivityFactory activityFactory;
    private ProgressTracker progressTracker;
    
    @BeforeEach
    public void setUp() {
        logger.info("初始化活动模块测试环境");
        
        activityManager = new ActivityManager();
        activityFactory = new ActivityFactory();
        progressTracker = new ProgressTracker();
    }
    
    @Test
    @DisplayName("测试活动类型枚举")
    public void testActivityType() {
        logger.info("测试活动类型枚举功能");
        
        // 测试活动类型基本功能
        ActivityType dailyType = ActivityType.DAILY;
        assertNotNull(dailyType, "活动类型不能为空");
        assertEquals("daily", dailyType.getCode(), "活动类型代码应该正确");
        assertEquals("每日活动", dailyType.getName(), "活动类型名称应该正确");
        assertTrue(dailyType.isPeriodic(), "每日活动应该是周期性的");
        
        // 测试类型查找
        ActivityType foundType = ActivityType.fromCode("daily");
        assertEquals(dailyType, foundType, "根据代码查找的类型应该匹配");
        
        // 测试类型存在性检查
        assertTrue(ActivityType.exists("daily"), "每日活动类型应该存在");
        assertFalse(ActivityType.exists("invalid"), "无效类型不应该存在");
        
        logger.info("活动类型枚举测试通过");
    }
    
    @Test
    @DisplayName("测试活动上下文")
    public void testActivityContext() {
        logger.info("测试活动上下文功能");
        
        // 创建活动上下文
        ActivityContext context = new ActivityContext(1L, 1001L, ActivityType.DAILY);
        
        // 测试基本属性
        assertEquals(Long.valueOf(1L), context.getActivityId(), "活动ID应该正确");
        assertEquals(Long.valueOf(1001L), context.getPlayerId(), "玩家ID应该正确");
        assertEquals(ActivityType.DAILY, context.getActivityType(), "活动类型应该正确");
        
        // 测试参数设置和获取
        context.setParameter("test_param", "test_value");
        assertEquals("test_value", context.getParameter("test_param"), "参数值应该正确");
        assertEquals("test_value", context.getParameter("test_param", String.class), "类型化获取应该正确");
        assertEquals("default", context.getParameter("nonexistent", "default"), "默认值应该正确");
        
        // 测试进度数据
        context.setProgress("score", 100L);
        assertEquals(Long.valueOf(100L), context.getProgress("score", Long.class), "进度数据应该正确");
        
        // 测试扩展数据
        context.setExtension("custom_data", Map.of("key", "value"));
        assertNotNull(context.getExtension("custom_data"), "扩展数据应该存在");
        
        logger.info("活动上下文测试通过");
    }
    
    @Test
    @DisplayName("测试活动管理器")
    public void testActivityManager() {
        logger.info("测试活动管理器功能");
        
        // 创建测试活动
        TestActivity activity = new TestActivity(1L, "测试活动", ActivityType.DAILY);
        
        // 测试活动注册
        boolean registered = activityManager.registerActivity(activity);
        assertTrue(registered, "活动注册应该成功");
        
        // 测试活动获取
        Activity retrieved = activityManager.getActivity(1L);
        assertNotNull(retrieved, "应该能获取到注册的活动");
        assertEquals(activity.getActivityId(), retrieved.getActivityId(), "获取的活动ID应该匹配");
        
        // 测试按类型获取活动
        var dailyActivities = activityManager.getActivitiesByType(ActivityType.DAILY);
        assertFalse(dailyActivities.isEmpty(), "应该能获取到每日活动");
        
        // 测试统计信息
        var stats = activityManager.getStats();
        assertEquals(1, stats.totalActivities, "总活动数应该为1");
        
        // 测试活动注销
        boolean unregistered = activityManager.unregisterActivity(1L);
        assertTrue(unregistered, "活动注销应该成功");
        
        logger.info("活动管理器测试通过");
    }
    
    @Test
    @DisplayName("测试活动进度")
    public void testActivityProgress() {
        logger.info("测试活动进度功能");
        
        // 创建活动进度
        ActivityProgress progress = new ActivityProgress(1001L, 1L);
        
        // 测试基本属性
        assertEquals(Long.valueOf(1001L), progress.getPlayerId(), "玩家ID应该正确");
        assertEquals(Long.valueOf(1L), progress.getActivityId(), "活动ID应该正确");
        assertFalse(progress.getCompleted(), "初始状态应该未完成");
        assertEquals(0.0, progress.getCompletionPercentage(), "初始完成度应该为0");
        
        // 测试进度设置
        progress.setProgress("score", 50L);
        assertEquals(Long.valueOf(50L), progress.getProgress("score"), "进度值应该正确");
        
        // 测试进度增加
        Long newScore = progress.addProgress("score", 25L);
        assertEquals(Long.valueOf(75L), newScore, "增加后的进度应该正确");
        
        // 测试进度检查
        assertTrue(progress.hasReached("score", 50L), "应该达到目标进度");
        assertFalse(progress.hasReached("score", 100L), "不应该达到更高的目标");
        
        // 测试百分比计算
        double percentage = progress.calculatePercentage("score", 100L);
        assertEquals(75.0, percentage, 0.01, "百分比计算应该正确");
        
        // 测试里程碑完成
        progress.completeMilestone("milestone1", System.currentTimeMillis());
        assertTrue(progress.isMilestoneCompleted("milestone1"), "里程碑应该已完成");
        
        // 测试数据验证
        var validation = progress.validate();
        assertTrue(validation.isValid(), "进度数据应该有效");
        
        logger.info("活动进度测试通过");
    }
    
    @Test
    @DisplayName("测试进度追踪器")
    public void testProgressTracker() {
        logger.info("测试进度追踪器功能");
        
        Long playerId = 1001L;
        Long activityId = 1L;
        
        // 测试进度初始化
        ActivityProgress progress = progressTracker.initializeProgress(playerId, activityId);
        assertNotNull(progress, "进度初始化应该成功");
        assertEquals(playerId, progress.getPlayerId(), "玩家ID应该正确");
        
        // 测试进度更新
        var updateResult = progressTracker.updateProgress(playerId, activityId, "score", 10L);
        assertTrue(updateResult.success, "进度更新应该成功");
        assertEquals(Long.valueOf(0L), updateResult.oldValue, "旧值应该为0");
        assertEquals(Long.valueOf(10L), updateResult.newValue, "新值应该为10");
        
        // 测试进度查询
        ActivityProgress retrieved = progressTracker.getProgress(playerId, activityId);
        assertNotNull(retrieved, "应该能查询到进度");
        assertEquals(Long.valueOf(10L), retrieved.getProgress("score"), "查询的进度值应该正确");
        
        // 测试进度重置
        boolean resetResult = progressTracker.resetProgress(playerId, activityId);
        assertTrue(resetResult, "进度重置应该成功");
        
        logger.info("进度追踪器测试通过");
    }
    
    @Test
    @DisplayName("测试活动奖励")
    public void testActivityReward() {
        logger.info("测试活动奖励功能");
        
        // 创建活动奖励
        ActivityReward reward = new ActivityReward(1L, ActivityReward.RewardType.CURRENCY, 1001, 1000L);
        
        // 测试基本属性
        assertEquals(Long.valueOf(1L), reward.getActivityId(), "活动ID应该正确");
        assertEquals(ActivityReward.RewardType.CURRENCY, reward.getRewardType(), "奖励类型应该正确");
        assertEquals(Integer.valueOf(1001), reward.getItemId(), "道具ID应该正确");
        assertEquals(Long.valueOf(1000L), reward.getQuantity(), "数量应该正确");
        
        // 测试领取条件
        reward.setClaimCondition("min_level", 10);
        assertEquals(10, reward.getClaimCondition("min_level"), "领取条件应该正确");
        assertTrue(reward.hasClaimCondition("min_level"), "应该包含指定条件");
        
        // 测试奖励状态
        assertTrue(reward.isValid(), "奖励应该有效");
        assertTrue(reward.isEffective(), "奖励应该已生效");
        assertFalse(reward.isExpired(), "奖励不应该过期");
        
        // 测试奖励权重
        double weight = reward.getWeight();
        assertTrue(weight > 0, "奖励权重应该大于0");
        
        // 测试数据验证
        var validation = reward.validate();
        assertTrue(validation.isValid(), "奖励数据应该有效");
        
        logger.info("活动奖励测试通过");
    }
    
    @Test
    @DisplayName("测试活动工厂")
    public void testActivityFactory() {
        logger.info("测试活动工厂功能");
        
        // 测试活动类型注册
        boolean registered = activityFactory.registerActivityType(ActivityType.DAILY, TestActivity.class);
        assertTrue(registered, "活动类型注册应该成功");
        
        // 测试类型查询
        assertTrue(activityFactory.isRegistered(ActivityType.DAILY), "活动类型应该已注册");
        assertEquals(TestActivity.class, activityFactory.getActivityClass(ActivityType.DAILY), "活动类应该匹配");
        
        // 测试配置验证
        ActivityFactory.ActivityConfig config = new ActivityFactory.ActivityConfig();
        config.activityId = 1L;
        config.activityName = "测试活动";
        config.activityType = ActivityType.DAILY;
        
        var validation = activityFactory.validateConfig(config);
        assertTrue(validation.isValid(), "配置应该有效");
        
        // 测试工厂统计
        var stats = activityFactory.getStats();
        assertTrue(stats.registeredTypeCount > 0, "应该有注册的活动类型");
        
        logger.info("活动工厂测试通过");
    }
    
    /**
     * 测试活动实现类
     */
    private static class TestActivity extends BaseActivity {
        
        public TestActivity() {
            super();
        }
        
        public TestActivity(Long activityId, String activityName, ActivityType activityType) {
            super(activityId, activityName, activityType);
        }
        
        @Override
        protected void doBaseInitialize(ActivityContext context) throws Exception {
            logger.debug("初始化测试活动: {}", getActivityName());
        }
        
        @Override
        protected void doBaseStart(ActivityContext context) throws Exception {
            logger.debug("启动测试活动: {}", getActivityName());
        }
        
        @Override
        protected void doBaseUpdate(ActivityContext context, long deltaTime) throws Exception {
            logger.debug("更新测试活动: {}, deltaTime: {}", getActivityName(), deltaTime);
        }
        
        @Override
        protected void doBaseEnd(ActivityContext context, String reason) throws Exception {
            logger.debug("结束测试活动: {}, reason: {}", getActivityName(), reason);
        }
        
        @Override
        protected ProgressCalculationResult doCalculateProgress(ActivityContext context, 
                                                              Long playerId, 
                                                              String actionType, 
                                                              Map<String, Object> actionData) {
            Map<String, Long> changes = new HashMap<>();
            changes.put("score", 10L);
            return ProgressCalculationResult.success(changes);
        }
        
        @Override
        protected RewardCalculationResult doCalculateReward(ActivityContext context, 
                                                           Long playerId, 
                                                           String milestone, 
                                                           Map<String, Object> baseReward) {
            Map<String, Object> rewards = new HashMap<>();
            rewards.put("currency", 100L);
            return RewardCalculationResult.success(rewards);
        }
    }
    
    @Test
    @DisplayName("测试模块集成")
    public void testModuleIntegration() {
        logger.info("测试活动模块集成功能");
        
        try {
            // 创建并注册活动
            TestActivity activity = new TestActivity(1L, "集成测试活动", ActivityType.DAILY);
            activityManager.registerActivity(activity);
            
            // 初始化活动
            ActivityContext context = new ActivityContext(1L, 1001L, ActivityType.DAILY);
            activity.initialize(context);
            
            // 启动活动
            activity.start(context);
            assertTrue(activity.isRunning(), "活动应该正在运行");
            
            // 初始化进度
            ActivityProgress progress = progressTracker.initializeProgress(1001L, 1L);
            assertNotNull(progress, "进度应该初始化成功");
            
            // 更新进度
            progressTracker.updateProgress(1001L, 1L, "score", 50L);
            
            // 验证进度
            ActivityProgress updated = progressTracker.getProgress(1001L, 1L);
            assertEquals(Long.valueOf(50L), updated.getProgress("score"), "进度应该更新正确");
            
            // 结束活动
            activity.end(context, "test_complete");
            assertTrue(activity.isFinished(), "活动应该已结束");
            
            logger.info("模块集成测试通过");
            
        } catch (Exception e) {
            logger.error("模块集成测试失败", e);
            fail("模块集成测试不应该抛出异常: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("测试异常处理")
    public void testExceptionHandling() {
        logger.info("测试异常处理功能");
        
        // 测试空参数处理
        assertThrows(IllegalArgumentException.class, () -> {
            progressTracker.initializeProgress(null, null);
        }, "空参数应该抛出异常");
        
        // 测试无效活动操作
        ActivityProgress nonExistent = progressTracker.getProgress(999L, 999L);
        assertNull(nonExistent, "不存在的进度应该返回null");
        
        // 测试重复注册
        TestActivity activity1 = new TestActivity(1L, "活动1", ActivityType.DAILY);
        TestActivity activity2 = new TestActivity(1L, "活动2", ActivityType.DAILY);
        
        assertTrue(activityManager.registerActivity(activity1), "第一次注册应该成功");
        assertFalse(activityManager.registerActivity(activity2), "重复注册应该失败");
        
        logger.info("异常处理测试通过");
    }
}