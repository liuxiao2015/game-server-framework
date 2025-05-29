/*
 * 连接管理器和消息处理测试
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
package com.lx.gameserver.frame.network;

import com.lx.gameserver.frame.network.connection.ConnectionManager;
import com.lx.gameserver.frame.network.core.Connection;
import com.lx.gameserver.frame.network.message.Message;
import com.lx.gameserver.frame.network.message.MessageDispatcher;
import com.lx.gameserver.frame.network.message.MessageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网络模块组件测试类
 */
public class NetworkComponentTest {

    private ConnectionManager connectionManager;
    private MessageDispatcher messageDispatcher;

    @BeforeEach
    void setUp() {
        // 初始化连接管理器
        ConnectionManager.ConnectionManagerConfig config = 
            new ConnectionManager.DefaultConnectionManagerConfig(1000, 10);
        connectionManager = new ConnectionManager(config);

        // 初始化消息分发器
        MessageDispatcher.MessageDispatcherConfig dispatcherConfig = 
            new MessageDispatcher.DefaultMessageDispatcherConfig();
        messageDispatcher = new MessageDispatcher(dispatcherConfig);
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.closeAllConnections();
        }
        if (messageDispatcher != null) {
            messageDispatcher.shutdown();
        }
    }

    @Test
    void testConnectionManager() {
        // 创建模拟连接
        MockConnection connection1 = new MockConnection("conn1", "192.168.1.1", 8080);
        MockConnection connection2 = new MockConnection("conn2", "192.168.1.2", 8080);
        MockConnection connection3 = new MockConnection("conn3", "192.168.1.1", 8081);

        // 测试连接注册
        assertTrue(connectionManager.registerConnection(connection1));
        assertTrue(connectionManager.registerConnection(connection2));
        assertTrue(connectionManager.registerConnection(connection3));

        // 验证连接数
        assertEquals(3, connectionManager.getActiveConnectionCount());

        // 测试根据ID查找连接
        assertEquals(connection1, connectionManager.getConnection("conn1"));
        assertEquals(connection2, connectionManager.getConnection("conn2"));
        assertNull(connectionManager.getConnection("nonexistent"));

        // 测试根据IP查找连接
        assertEquals(2, connectionManager.getConnectionsByIp("192.168.1.1").size());
        assertEquals(1, connectionManager.getConnectionsByIp("192.168.1.2").size());

        // 测试分组管理
        assertTrue(connectionManager.addToGroup("group1", "conn1"));
        assertTrue(connectionManager.addToGroup("group1", "conn2"));
        assertTrue(connectionManager.addToGroup("group2", "conn3"));

        assertEquals(2, connectionManager.getGroupConnections("group1").size());
        assertEquals(1, connectionManager.getGroupConnections("group2").size());

        // 测试连接注销
        Connection removed = connectionManager.unregisterConnection("conn1");
        assertEquals(connection1, removed);
        assertEquals(2, connectionManager.getActiveConnectionCount());

        // 测试广播
        TestMessage testMessage = new TestMessage("broadcast test");
        connectionManager.broadcast(testMessage);

        // 验证IP连接统计
        Map<String, Integer> ipCounts = connectionManager.getIpConnectionCounts();
        assertEquals(1, ipCounts.get("192.168.1.1").intValue());
        assertEquals(1, ipCounts.get("192.168.1.2").intValue());
    }

    @Test
    void testMessageDispatcher() throws InterruptedException {
        AtomicInteger handleCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        // 注册消息处理器
        messageDispatcher.registerHandler(TestMessage.class, new MessageHandler<TestMessage>() {
            @Override
            public void handle(TestMessage message, Connection connection) {
                handleCount.incrementAndGet();
                latch.countDown();
            }
        });

        // 创建测试连接和消息
        MockConnection connection = new MockConnection("test", "127.0.0.1", 8080);
        TestMessage message1 = new TestMessage("test1");
        TestMessage message2 = new TestMessage("test2");

        // 同步分发消息
        messageDispatcher.dispatch(message1, connection);
        
        // 异步分发消息
        CompletableFuture<Void> future = messageDispatcher.dispatchAsync(message2, connection);

        // 等待处理完成
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));

        // 验证处理次数
        assertEquals(2, handleCount.get());
        assertEquals(2, messageDispatcher.getProcessedMessageCount());
        assertEquals(0, messageDispatcher.getFailedMessageCount());

        // 测试注销处理器
        messageDispatcher.unregisterHandler(TestMessage.class, 
            (MessageHandler<TestMessage>) (message, conn) -> handleCount.incrementAndGet());
        
        // 验证处理器数量
        assertEquals(1, messageDispatcher.getHandlerCount(TestMessage.class));
    }

    @Test
    void testMessageProperties() {
        TestMessage message = new TestMessage("test content");
        
        // 测试基础属性
        assertNotNull(message.getMessageId());
        assertEquals(Message.MessageType.BUSINESS, message.getMessageType());
        assertEquals(Message.MessagePriority.NORMAL, message.getPriority());
        assertNotNull(message.getCreateTime());

        // 测试扩展属性
        message.setProperty("userId", "12345")
               .setProperty("sessionId", "session-abc")
               .setProperty("priority", 100);

        assertEquals("12345", message.getProperty("userId"));
        assertEquals("session-abc", message.getProperty("sessionId"));
        assertEquals(100, message.getProperty("priority"));
        
        // 测试属性移除
        assertEquals("12345", message.removeProperty("userId"));
        assertNull(message.getProperty("userId"));
        
        // 测试属性检查
        assertTrue(message.hasProperty("sessionId"));
        assertFalse(message.hasProperty("userId"));

        // 测试链式调用
        message.setSourceId("client1")
               .setTargetId("server1")
               .setSequence(123)
               .setPriority(Message.MessagePriority.HIGH);

        assertEquals("client1", message.getSourceId());
        assertEquals("server1", message.getTargetId());
        assertEquals(123, message.getSequence());
        assertEquals(Message.MessagePriority.HIGH, message.getPriority());
    }

    /**
     * 测试消息实现类
     */
    private static class TestMessage extends Message {
        private String content;

        public TestMessage(String content) {
            super(MessageType.BUSINESS);
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return "TestMessage{content='" + content + "', id='" + getMessageId() + "'}";
        }
    }

    /**
     * 模拟连接实现
     */
    private static class MockConnection implements Connection {
        private final String id;
        private final SocketAddress localAddress;
        private final SocketAddress remoteAddress;
        private ConnectionState state = ConnectionState.CONNECTED;
        private final Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();
        private final LocalDateTime createTime = LocalDateTime.now();
        private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

        public MockConnection(String id, String remoteIp, int remotePort) {
            this.id = id;
            this.localAddress = new InetSocketAddress("127.0.0.1", 8080);
            this.remoteAddress = new InetSocketAddress(remoteIp, remotePort);
        }

        @Override
        public String getId() { return id; }

        @Override
        public ConnectionState getState() { return state; }

        @Override
        public boolean isActive() { return state == ConnectionState.CONNECTED; }

        @Override
        public SocketAddress getLocalAddress() { return localAddress; }

        @Override
        public SocketAddress getRemoteAddress() { return remoteAddress; }

        @Override
        public CompletableFuture<Void> send(Object message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendSync(Object message) throws Exception {
            // Mock implementation
        }

        @Override
        public CompletableFuture<Void> close() {
            state = ConnectionState.DISCONNECTED;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void forceClose() {
            state = ConnectionState.DISCONNECTED;
        }

        @Override
        public Object setAttribute(String key, Object value) {
            return attributes.put(key, value);
        }

        @Override
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public <T> T getAttribute(String key, T defaultValue) {
            Object value = attributes.get(key);
            return value != null ? (T) value : defaultValue;
        }

        @Override
        public Object removeAttribute(String key) {
            return attributes.remove(key);
        }

        @Override
        public Map<String, Object> getAttributes() {
            return java.util.Collections.unmodifiableMap(attributes);
        }

        @Override
        public void clearAttributes() {
            attributes.clear();
        }

        @Override
        public LocalDateTime getCreateTime() { return createTime; }

        @Override
        public LocalDateTime getLastActiveTime() { return lastActiveTime; }

        @Override
        public void updateLastActiveTime() {
            lastActiveTime = LocalDateTime.now();
        }

        @Override
        public long getSentBytes() { return 0; }

        @Override
        public long getReceivedBytes() { return 0; }

        @Override
        public long getSentMessages() { return 0; }

        @Override
        public long getReceivedMessages() { return 0; }

        @Override
        public void addConnectionListener(ConnectionListener listener) {
            // Mock implementation
        }

        @Override
        public void removeConnectionListener(ConnectionListener listener) {
            // Mock implementation
        }
    }
}