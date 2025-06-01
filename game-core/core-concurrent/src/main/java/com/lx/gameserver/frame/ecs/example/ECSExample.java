/*
 * 文件名: ECSExample.java
 * 用途: ECS系统使用示例
 * 实现内容:
 *   - 创建游戏角色示例
 *   - 实现基础系统示例
 *   - 组件组合示例
 *   - 系统交互示例
 * 技术选型:
 *   - 演示ECS核心概念
 *   - 提供完整使用流程
 *   - 展示最佳实践
 * 依赖关系:
 *   - 使用ECS框架的所有核心功能
 *   - 提供学习和参考示例
 *   - 验证框架设计正确性
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.example;

import com.lx.gameserver.frame.ecs.component.HealthComponent;
import com.lx.gameserver.frame.ecs.component.PositionComponent;
import com.lx.gameserver.frame.ecs.component.VelocityComponent;
import com.lx.gameserver.frame.ecs.config.ECSConfig;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.ecs.core.World;
import com.lx.gameserver.frame.ecs.system.IntervalSystem;
import com.lx.gameserver.frame.ecs.system.IteratingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ECS系统使用示例
 * <p>
 * 展示如何使用ECS框架创建一个简单的游戏场景，
 * 包含玩家、NPC、物品等实体以及相关的系统。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ECSExample {
    
    private static final Logger logger = LoggerFactory.getLogger(ECSExample.class);
    
    public static void main(String[] args) {
        logger.info("开始ECS系统示例演示");
        
        // 1. 创建ECS世界
        ECSConfig config = ECSConfig.development();
        World world = new World();
        world.initialize();
        
        // 2. 注册系统
        world.registerSystem(new SimpleMovementSystem());
        world.registerSystem(new HealthRegenerationSystem());
        world.registerSystem(new GameStatisticsSystem());
        
        // 3. 创建游戏实体
        createPlayer(world);
        createNPCs(world, 5);
        createItems(world, 10);
        
        // 4. 运行游戏循环
        runGameLoop(world, 5.0f); // 运行5秒
        
        // 5. 清理资源
        world.destroy();
        logger.info("ECS系统示例演示结束");
    }
    
    /**
     * 创建玩家实体
     */
    private static Entity createPlayer(World world) {
        Entity player = world.createEntity();
        player.addTag(Entity.Tags.PLAYER);
        
        // 添加位置组件
        PositionComponent position = new PositionComponent(0, 0, 0);
        world.addComponent(player.getId(), position);
        
        // 添加速度组件
        VelocityComponent velocity = new VelocityComponent(5, 0, 0);
        world.addComponent(player.getId(), velocity);
        
        // 添加生命值组件
        HealthComponent health = new HealthComponent(100, 50);
        health.setHealthRegenRate(2.0f); // 每秒回复2点生命值
        world.addComponent(player.getId(), health);
        
        logger.info("创建玩家实体: {}", player.getId());
        return player;
    }
    
    /**
     * 创建NPC实体
     */
    private static void createNPCs(World world, int count) {
        for (int i = 0; i < count; i++) {
            Entity npc = world.createEntity();
            npc.addTag(Entity.Tags.NPC);
            
            // 随机位置
            float x = (float) (Math.random() * 100 - 50);
            float y = (float) (Math.random() * 100 - 50);
            world.addComponent(npc.getId(), new PositionComponent(x, y, 0));
            
            // 随机移动
            float vx = (float) (Math.random() * 4 - 2);
            float vy = (float) (Math.random() * 4 - 2);
            world.addComponent(npc.getId(), new VelocityComponent(vx, vy, 0));
            
            // 生命值
            world.addComponent(npc.getId(), new HealthComponent(50 + i * 10));
            
            logger.debug("创建NPC实体: {} 位置({}, {})", npc.getId(), x, y);
        }
    }
    
    /**
     * 创建物品实体
     */
    private static void createItems(World world, int count) {
        for (int i = 0; i < count; i++) {
            Entity item = world.createEntity();
            item.addTag(Entity.Tags.ITEM);
            
            // 随机位置
            float x = (float) (Math.random() * 200 - 100);
            float y = (float) (Math.random() * 200 - 100);
            world.addComponent(item.getId(), new PositionComponent(x, y, 0));
            
            logger.debug("创建物品实体: {} 位置({}, {})", item.getId(), x, y);
        }
    }
    
    /**
     * 运行游戏循环
     */
    private static void runGameLoop(World world, float duration) {
        logger.info("开始游戏循环，持续时间: {}秒", duration);
        
        float deltaTime = 0.016f; // 60 FPS
        float totalTime = 0;
        int frameCount = 0;
        
        while (totalTime < duration) {
            // 更新世界
            world.update(deltaTime);
            
            totalTime += deltaTime;
            frameCount++;
            
            // 每秒输出一次统计信息
            if (frameCount % 60 == 0) {
                logger.info("游戏时间: {:.1f}s, 帧数: {}, 实体数: {}", 
                    totalTime, frameCount, world.getEntityCount());
            }
            
            // 模拟帧延迟
            try {
                Thread.sleep(16); // 约60 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        logger.info("游戏循环结束，总帧数: {}", frameCount);
    }
    
    /**
     * 简单移动系统
     */
    static class SimpleMovementSystem extends IteratingSystem {
        
        public SimpleMovementSystem() {
            super("SimpleMovementSystem", Priority.NORMAL, 
                  PositionComponent.class, VelocityComponent.class);
        }
        
        @Override
        protected void onSystemInitialize() {
            logger.info("初始化简单移动系统");
        }
        
        @Override
        protected void processEntity(Entity entity, float deltaTime) {
            PositionComponent position = world.getComponent(entity.getId(), PositionComponent.class);
            VelocityComponent velocity = world.getComponent(entity.getId(), VelocityComponent.class);
            
            if (position != null && velocity != null) {
                // 简单的位置更新
                float newX = position.getX() + velocity.getVelocityX() * deltaTime;
                float newY = position.getY() + velocity.getVelocityY() * deltaTime;
                position.setPosition(newX, newY);
                
                // 边界检查（简单的反弹）
                if (newX < -100 || newX > 100) {
                    velocity.setVelocityX(-velocity.getVelocityX());
                }
                if (newY < -100 || newY > 100) {
                    velocity.setVelocityY(-velocity.getVelocityY());
                }
            }
        }
    }
    
    /**
     * 生命值恢复系统
     */
    static class HealthRegenerationSystem extends IteratingSystem {
        
        public HealthRegenerationSystem() {
            super("HealthRegenerationSystem", Priority.LOW, HealthComponent.class);
        }
        
        @Override
        protected void onSystemInitialize() {
            logger.info("初始化生命值恢复系统");
        }
        
        @Override
        protected void processEntity(Entity entity, float deltaTime) {
            HealthComponent health = world.getComponent(entity.getId(), HealthComponent.class);
            
            if (health != null && !health.isDead()) {
                health.update(deltaTime);
            }
        }
    }
    
    /**
     * 游戏统计系统
     */
    static class GameStatisticsSystem extends IntervalSystem {
        
        public GameStatisticsSystem() {
            super("GameStatisticsSystem", Priority.LOWEST, 2.0f); // 每2秒执行一次
        }
        
        @Override
        protected void onSystemInitialize() {
            logger.info("初始化游戏统计系统");
        }
        
        @Override
        protected void onIntervalUpdate() {
            // 统计各类实体数量
            int playerCount = 0;
            int npcCount = 0;
            int itemCount = 0;
            
            for (Entity entity : world.getAllEntities()) {
                if (entity.hasTag(Entity.Tags.PLAYER)) {
                    playerCount++;
                } else if (entity.hasTag(Entity.Tags.NPC)) {
                    npcCount++;
                } else if (entity.hasTag(Entity.Tags.ITEM)) {
                    itemCount++;
                }
            }
            
            logger.info("游戏统计 - 玩家: {}, NPC: {}, 物品: {}, 总计: {}", 
                playerCount, npcCount, itemCount, world.getEntityCount());
        }
    }
}