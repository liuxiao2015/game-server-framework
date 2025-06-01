/*
 * 网络模块基础测试
 * 验证核心抽象和配置的正确性
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
package com.lx.gameserver.frame.network;

import com.lx.gameserver.frame.network.config.NetworkConfig;
import com.lx.gameserver.frame.network.core.Connection;
import com.lx.gameserver.frame.network.core.Protocol;
import com.lx.gameserver.frame.network.message.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网络模块基础测试类
 */
public class NetworkModuleTest {

    @Test
    void testNetworkConfig() {
        // 测试默认配置
        NetworkConfig defaultConfig = NetworkConfig.defaultConfig();
        assertNotNull(defaultConfig);
        assertEquals(8080, defaultConfig.getServerPort());
        assertEquals(Protocol.ProtocolType.TCP, defaultConfig.getProtocolType());
        assertEquals(100000, defaultConfig.getMaxConnections());

        // 测试服务器配置
        NetworkConfig serverConfig = NetworkConfig.serverConfig(9090);
        assertEquals(9090, serverConfig.getServerPort());

        // 测试客户端配置
        NetworkConfig clientConfig = NetworkConfig.clientConfig();
        assertNotNull(clientConfig.getReconnectStrategy());

        // 测试构建器模式
        NetworkConfig customConfig = NetworkConfig.builder()
            .serverPort(8888)
            .maxConnections(50000)
            .compressionEnabled(false)
            .build();
        
        assertEquals(8888, customConfig.getServerPort());
        assertEquals(50000, customConfig.getMaxConnections());
        assertFalse(customConfig.isCompressionEnabled());
    }

    @Test
    void testConnectionState() {
        // 测试连接状态枚举
        Connection.ConnectionState[] states = Connection.ConnectionState.values();
        assertEquals(4, states.length);
        
        assertEquals("连接中", Connection.ConnectionState.CONNECTING.getDescription());
        assertEquals("已连接", Connection.ConnectionState.CONNECTED.getDescription());
        assertEquals("断开中", Connection.ConnectionState.DISCONNECTING.getDescription());
        assertEquals("已断开", Connection.ConnectionState.DISCONNECTED.getDescription());
    }

    @Test
    void testMessageType() {
        // 测试消息类型枚举
        Message.MessageType[] types = Message.MessageType.values();
        assertEquals(6, types.length);
        
        assertEquals("请求", Message.MessageType.REQUEST.getDescription());
        assertEquals("响应", Message.MessageType.RESPONSE.getDescription());
        assertEquals("通知", Message.MessageType.NOTIFICATION.getDescription());
        assertEquals("心跳", Message.MessageType.HEARTBEAT.getDescription());
        assertEquals("系统", Message.MessageType.SYSTEM.getDescription());
        assertEquals("业务", Message.MessageType.BUSINESS.getDescription());
    }

    @Test
    void testMessagePriority() {
        // 测试消息优先级
        Message.MessagePriority[] priorities = Message.MessagePriority.values();
        assertEquals(4, priorities.length);
        
        assertEquals(1, Message.MessagePriority.LOW.getLevel());
        assertEquals(5, Message.MessagePriority.NORMAL.getLevel());
        assertEquals(8, Message.MessagePriority.HIGH.getLevel());
        assertEquals(10, Message.MessagePriority.URGENT.getLevel());
    }

    @Test
    void testProtocolEnums() {
        // 测试协议类型
        Protocol.ProtocolType[] protocolTypes = Protocol.ProtocolType.values();
        assertEquals(4, protocolTypes.length);
        
        assertEquals("TCP", Protocol.ProtocolType.TCP.getCode());
        assertEquals("UDP", Protocol.ProtocolType.UDP.getCode());
        assertEquals("WebSocket", Protocol.ProtocolType.WEBSOCKET.getCode());
        assertEquals("HTTP", Protocol.ProtocolType.HTTP.getCode());

        // 测试消息格式
        Protocol.MessageFormat[] messageFormats = Protocol.MessageFormat.values();
        assertEquals(4, messageFormats.length);
        
        assertEquals("protobuf", Protocol.MessageFormat.PROTOBUF.getCode());
        assertEquals("json", Protocol.MessageFormat.JSON.getCode());
        assertEquals("custom-binary", Protocol.MessageFormat.CUSTOM_BINARY.getCode());
        assertEquals("xml", Protocol.MessageFormat.XML.getCode());
    }

    /**
     * 测试消息实现类
     */
    private static class TestMessage extends Message {
        private String content;

        public TestMessage() {
            super(MessageType.BUSINESS);
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @Test
    void testMessage() {
        // 创建测试消息
        TestMessage message = new TestMessage();
        message.setContent("测试消息");
        
        // 验证基础属性
        assertNotNull(message.getMessageId());
        assertEquals(Message.MessageType.BUSINESS, message.getMessageType());
        assertEquals(Message.MessagePriority.NORMAL, message.getPriority());
        assertNotNull(message.getCreateTime());
        assertEquals("测试消息", message.content);

        // 测试链式调用
        message.setPriority(Message.MessagePriority.HIGH)
               .setSourceId("client1")
               .setTargetId("server1")
               .setRoutingKey("test.route")
               .setSequence(123);

        assertEquals(Message.MessagePriority.HIGH, message.getPriority());
        assertEquals("client1", message.getSourceId());
        assertEquals("server1", message.getTargetId());
        assertEquals("test.route", message.getRoutingKey());
        assertEquals(123, message.getSequence());

        // 测试扩展属性
        message.setProperty("custom.field", "custom.value");
        assertEquals("custom.value", message.getProperty("custom.field"));
        assertEquals("default", message.getProperty("nonexistent", "default"));
        assertTrue(message.hasProperty("custom.field"));
        assertFalse(message.hasProperty("nonexistent"));

        // 测试属性移除
        Object removed = message.removeProperty("custom.field");
        assertEquals("custom.value", removed);
        assertFalse(message.hasProperty("custom.field"));

        // 测试摘要信息
        String digest = message.getDigest();
        assertNotNull(digest);
        assertTrue(digest.contains(message.getMessageId()));
    }

    @Test
    void testConfigValidation() {
        // 测试无效端口
        assertThrows(IllegalArgumentException.class, () -> 
            NetworkConfig.builder().serverPort(0).build());
        
        assertThrows(IllegalArgumentException.class, () -> 
            NetworkConfig.builder().serverPort(65536).build());

        // 测试写缓冲区配置
        assertThrows(IllegalArgumentException.class, () -> 
            NetworkConfig.builder()
                .writeBufferLow(2048)
                .writeBufferHigh(1024)
                .build());

        // 测试SSL配置
        assertThrows(IllegalArgumentException.class, () -> 
            NetworkConfig.builder()
                .sslEnabled(true)
                .build());
    }
}