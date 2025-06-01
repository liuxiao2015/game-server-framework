/*
 * 文件名: ActorSystemTest.java
 * 用途: Actor系统核心功能测试
 * 内容: 
 *   - 测试Actor配置基本功能
 *   - 验证核心接口存在性
 *   - 测试基本消息功能
 * 技术选型: 
 *   - JUnit 5测试框架
 * 依赖关系: 
 *   - 测试frame-actor模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.frame.actor.core;

import com.lx.gameserver.frame.actor.config.ActorSystemConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actor系统核心功能测试
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@DisplayName("Actor系统核心功能测试")
class ActorSystemTest {
    
    @Test
    @DisplayName("测试Actor系统配置构建器")
    void testActorSystemConfigBuilder() {
        ActorSystemConfig config = ActorSystemConfig.builder("test-system")
                .build();
        
        assertNotNull(config);
        assertEquals("test-system", config.getSystemName());
    }
    
    @Test
    @DisplayName("测试默认配置")
    void testDefaultConfig() {
        ActorSystemConfig defaultConfig = ActorSystemConfig.defaultConfig("default-system");
        
        assertNotNull(defaultConfig);
        assertEquals("default-system", defaultConfig.getSystemName());
    }
    
    @Test
    @DisplayName("测试Actor消息基类")
    void testAbstractMessage() {
        TestMessage message = new TestMessage("Hello, Actor!");
        assertNotNull(message);
        assertEquals("Hello, Actor!", message.getContent());
        assertTrue(message.getTimestamp() > 0);
    }
    
    @Test
    @DisplayName("测试Actor类和接口存在性")
    void testActorClassesExist() {
        // 验证核心Actor接口存在
        assertFalse(Actor.class.isInterface()); // Actor是抽象类
        assertTrue(ActorSystem.class.isInterface());
        assertTrue(ActorRef.class.isInterface());
        assertTrue(ActorContext.class.isInterface());
        assertTrue(Message.class.isInterface());
        
        // 验证ActorProps类存在（这是一个类而不是接口）
        assertFalse(ActorProps.class.isInterface());
        assertNotNull(ActorProps.class);
    }
    
    @Test
    @DisplayName("测试配置属性")
    void testConfigProperties() {
        ActorSystemConfig config = ActorSystemConfig.builder("property-test")
                .build();
        
        // 测试基本属性
        assertNotNull(config.getSystemName());
        assertNotNull(config.getDispatcherConfig());
        assertNotNull(config.getMailboxConfig());
        assertNotNull(config.getSupervisionConfig());
    }
    
    // 测试消息类
    public static class TestMessage extends AbstractMessage {
        private final String content;
        
        public TestMessage(String content) {
            this.content = content;
        }
        
        public String getContent() {
            return content;
        }
    }
}