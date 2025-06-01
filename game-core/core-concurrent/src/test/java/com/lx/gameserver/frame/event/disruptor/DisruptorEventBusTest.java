/*
 * 文件名: DisruptorEventBusTest.java
 * 用途: Disruptor事件总线单元测试
 * 实现内容:
 *   - 测试事件发布和处理功能
 *   - 验证事件处理器注册
 *   - 测试批量事件处理
 * 技术选型:
 *   - JUnit 5测试框架
 *   - Mockito模拟框架
 * 依赖关系:
 *   - 测试DisruptorEventBus
 *   - 测试GameEvent和相关类
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.event.disruptor;

import com.lx.gameserver.frame.event.core.EventPriority;
import com.lx.gameserver.frame.event.core.GameEvent;
import com.lx.gameserver.frame.event.core.EventHandler;
import com.lx.gameserver.frame.event.events.PlayerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disruptor事件总线单元测试
 * <p>
 * 测试事件总线的基本功能，包括事件发布、处理器注册、异常处理等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
class DisruptorEventBusTest {
    
    private DisruptorEventBus eventBus;
    
    @BeforeEach
    void setUp() {
        eventBus = new DisruptorEventBus("TestEventBus");
        eventBus.initialize();
    }
    
    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.shutdown();
        }
    }
    
    @Test
    void testEventBusInitialization() {
        assertTrue(eventBus.isRunning(), "事件总线应该正在运行");
        assertEquals("TestEventBus", eventBus.getName(), "事件总线名称应该正确");
    }
    
    @Test
    void testPublishEvent() throws InterruptedException {
        // 创建测试事件
        TestGameEvent testEvent = new TestGameEvent("test-source");
        
        // 注册事件处理器
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        EventHandler<TestGameEvent> handler = event -> {
            processedCount.incrementAndGet();
            latch.countDown();
        };
        
        eventBus.register(TestGameEvent.class, handler);
        
        // 发布事件
        boolean published = eventBus.publish(testEvent);
        assertTrue(published, "事件发布应该成功");
        
        // 等待事件处理完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "事件应该在5秒内处理完成");
        assertEquals(1, processedCount.get(), "事件应该被处理一次");
    }
    
    @Test
    void testPlayerLoginEvent() throws InterruptedException {
        // 创建玩家登录事件
        PlayerEvent.PlayerLoginEvent loginEvent = new PlayerEvent.PlayerLoginEvent(
                12345L, "192.168.1.100", "iPhone 15", "AppStore", "PlayerService"
        );
        
        // 注册事件处理器
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        EventHandler<PlayerEvent.PlayerLoginEvent> handler = event -> {
            assertEquals(12345L, event.getPlayerId());
            assertEquals("192.168.1.100", event.getLoginIp());
            assertEquals("iPhone 15", event.getDeviceInfo());
            processedCount.incrementAndGet();
            latch.countDown();
        };
        
        eventBus.register(PlayerEvent.PlayerLoginEvent.class, handler);
        
        // 发布事件
        boolean published = eventBus.publish(loginEvent);
        assertTrue(published, "玩家登录事件发布应该成功");
        
        // 等待事件处理完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "事件应该在5秒内处理完成");
        assertEquals(1, processedCount.get(), "事件应该被处理一次");
    }
    
    @Test
    void testMultipleHandlers() throws InterruptedException {
        // 创建测试事件
        TestGameEvent testEvent = new TestGameEvent("test-source");
        
        // 注册多个事件处理器
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger totalProcessed = new AtomicInteger(0);
        
        for (int i = 0; i < 3; i++) {
            final int handlerIndex = i;
            EventHandler<TestGameEvent> handler = event -> {
                totalProcessed.incrementAndGet();
                latch.countDown();
            };
            
            eventBus.register(TestGameEvent.class, handler);
        }
        
        // 发布事件
        boolean published = eventBus.publish(testEvent);
        assertTrue(published, "事件发布应该成功");
        
        // 等待所有处理器处理完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "所有处理器应该在5秒内处理完成");
        assertEquals(3, totalProcessed.get(), "事件应该被3个处理器处理");
    }
    
    @Test
    void testPublishNullEvent() {
        boolean published = eventBus.publish(null);
        assertFalse(published, "发布空事件应该失败");
    }
    
    @Test
    void testEventBusShutdown() {
        assertTrue(eventBus.isRunning(), "事件总线应该正在运行");
        
        eventBus.shutdown();
        assertFalse(eventBus.isRunning(), "关闭后事件总线应该停止运行");
    }
    
    /**
     * 测试用的简单游戏事件
     */
    private static class TestGameEvent extends GameEvent {
        
        public TestGameEvent(String source) {
            super(source, EventPriority.NORMAL);
        }
        
        public TestGameEvent(String source, EventPriority priority) {
            super(source, priority);
        }
    }
}