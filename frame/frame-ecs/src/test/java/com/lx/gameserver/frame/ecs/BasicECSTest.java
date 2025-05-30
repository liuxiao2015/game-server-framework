/*
 * 文件名: BasicECSTest.java
 * 用途: ECS系统基础功能测试
 * 实现内容:
 *   - 测试实体创建和销毁
 *   - 测试组件添加和移除
 *   - 测试系统注册和更新
 *   - 测试移动系统功能
 * 技术选型:
 *   - JUnit 5测试框架
 *   - 模拟游戏循环
 *   - 性能基准测试
 * 依赖关系:
 *   - 测试ECS核心功能
 *   - 验证系统正确性
 *   - 提供使用示例
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs;

import com.lx.gameserver.frame.ecs.component.HealthComponent;
import com.lx.gameserver.frame.ecs.component.PositionComponent;
import com.lx.gameserver.frame.ecs.component.VelocityComponent;
import com.lx.gameserver.frame.ecs.core.Entity;
import com.lx.gameserver.frame.ecs.core.World;
import com.lx.gameserver.frame.ecs.system.MovementSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ECS系统基础功能测试
 */
public class BasicECSTest {
    
    private World world;
    
    @BeforeEach
    public void setUp() {
        world = new World();
        world.initialize();
    }
    
    @Test
    public void testEntityCreation() {
        // 创建实体
        Entity entity = world.createEntity();
        
        assertNotNull(entity);
        assertTrue(entity.getId() > 0);
        assertTrue(entity.isActive());
        assertFalse(entity.isDestroyed());
        
        // 检查世界中是否包含该实体
        assertTrue(world.hasEntity(entity.getId()));
        assertEquals(1, world.getEntityCount());
    }
    
    @Test
    public void testComponentManagement() {
        // 创建实体
        Entity entity = world.createEntity();
        
        // 添加位置组件
        PositionComponent position = new PositionComponent(10, 20, 30);
        world.addComponent(entity.getId(), position);
        
        // 检查组件是否添加成功
        assertTrue(world.hasComponent(entity.getId(), PositionComponent.class));
        
        // 获取组件并验证数据
        PositionComponent retrievedPosition = world.getComponent(entity.getId(), PositionComponent.class);
        assertNotNull(retrievedPosition);
        assertEquals(10, retrievedPosition.getX(), 0.001f);
        assertEquals(20, retrievedPosition.getY(), 0.001f);
        assertEquals(30, retrievedPosition.getZ(), 0.001f);
        
        // 移除组件
        PositionComponent removedPosition = world.removeComponent(entity.getId(), PositionComponent.class);
        assertNotNull(removedPosition);
        assertFalse(world.hasComponent(entity.getId(), PositionComponent.class));
    }
    
    @Test
    public void testSystemRegistration() {
        // 注册移动系统
        MovementSystem movementSystem = new MovementSystem();
        world.registerSystem(movementSystem);
        
        // 检查系统是否注册成功
        MovementSystem retrievedSystem = world.getSystem(MovementSystem.class);
        assertNotNull(retrievedSystem);
        assertSame(movementSystem, retrievedSystem);
    }
    
    @Test
    public void testMovementSystem() {
        // 注册移动系统
        MovementSystem movementSystem = new MovementSystem();
        world.registerSystem(movementSystem);
        
        // 创建移动实体
        Entity entity = world.createEntity();
        
        // 添加位置和速度组件
        PositionComponent position = new PositionComponent(0, 0, 0);
        VelocityComponent velocity = new VelocityComponent(10, 5, 0);
        
        world.addComponent(entity.getId(), position);
        world.addComponent(entity.getId(), velocity);
        
        // 记录初始位置
        float initialX = position.getX();
        float initialY = position.getY();
        
        // 更新世界（模拟1秒）
        float deltaTime = 1.0f;
        world.update(deltaTime);
        
        // 检查位置是否更新
        assertEquals(initialX + 10, position.getX(), 0.001f);
        assertEquals(initialY + 5, position.getY(), 0.001f);
    }
    
    @Test
    public void testHealthComponent() {
        // 创建实体
        Entity entity = world.createEntity();
        
        // 添加生命值组件
        HealthComponent health = new HealthComponent(100, 50);
        world.addComponent(entity.getId(), health);
        
        // 测试初始状态
        assertEquals(100, health.getCurrentHealth(), 0.001f);
        assertEquals(100, health.getMaxHealth(), 0.001f);
        assertEquals(50, health.getCurrentShield(), 0.001f);
        assertFalse(health.isDead());
        
        // 测试受到伤害
        float damage = health.takeDamage(30);
        assertEquals(30, damage, 0.001f);
        assertEquals(20, health.getCurrentShield(), 0.001f); // 护盾先承受伤害
        assertEquals(100, health.getCurrentHealth(), 0.001f); // 生命值不变
        
        // 测试治疗
        float heal = health.heal(10);
        assertEquals(0, heal, 0.001f); // 满血无法治疗
        
        // 测试致命伤害
        health.takeDamage(150); // 超过总血量
        assertTrue(health.isDead());
        assertEquals(0, health.getCurrentHealth(), 0.001f);
        assertEquals(0, health.getCurrentShield(), 0.001f);
    }
    
    @Test
    public void testEntityDestruction() {
        // 创建实体
        Entity entity = world.createEntity();
        long entityId = entity.getId();
        
        // 添加组件
        world.addComponent(entityId, new PositionComponent());
        
        // 销毁实体
        world.destroyEntity(entityId);
        
        // 更新世界以处理待删除实体
        world.update(0.016f);
        
        // 检查实体是否被销毁
        assertFalse(world.hasEntity(entityId));
        assertEquals(0, world.getEntityCount());
        
        // 检查组件是否被清理
        assertFalse(world.hasComponent(entityId, PositionComponent.class));
    }
    
    @Test
    public void testPerformance() {
        // 性能测试：创建大量实体
        int entityCount = 10000;
        long startTime = java.lang.System.nanoTime();
        
        // 创建实体和组件
        for (int i = 0; i < entityCount; i++) {
            Entity entity = world.createEntity();
            world.addComponent(entity.getId(), new PositionComponent(i, i, 0));
            world.addComponent(entity.getId(), new VelocityComponent(1, 1, 0));
        }
        
        long endTime = java.lang.System.nanoTime();
        long creationTime = (endTime - startTime) / 1_000_000; // 转换为毫秒
        
        System.out.printf("创建 %d 个实体用时: %d ms%n", entityCount, creationTime);
        
        // 检查实体数量
        assertEquals(entityCount, world.getEntityCount());
        
        // 注册移动系统
        MovementSystem movementSystem = new MovementSystem();
        world.registerSystem(movementSystem);
        
        // 性能测试：系统更新
        startTime = java.lang.System.nanoTime();
        world.update(0.016f);
        endTime = java.lang.System.nanoTime();
        
        long updateTime = (endTime - startTime) / 1_000_000; // 转换为毫秒
        System.out.printf("更新 %d 个实体用时: %d ms%n", entityCount, updateTime);
        
        // 验证性能要求（更新时间应该在合理范围内）
        assertTrue(updateTime < 100, "更新时间过长: " + updateTime + "ms");
    }
}