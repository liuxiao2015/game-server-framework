/*
 * 会话管理测试
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
package com.lx.gameserver.frame.network;

import com.lx.gameserver.frame.network.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会话管理测试类
 */
public class SessionTest {

    private SessionManager sessionManager;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        SessionManager.SessionManagerConfig config = 
            new SessionManager.DefaultSessionManagerConfig(1000, 5000, 1000, false); // 5秒超时
        sessionManager = new SessionManager(config);
        
        sessionStore = new SessionStore.MemorySessionStore();
        sessionManager.setSessionStore(sessionStore);
    }

    @AfterEach
    void tearDown() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (sessionStore != null) {
            sessionStore.close();
        }
    }

    @Test
    void testSessionCreationAndBasicOperations() {
        // 创建会话
        Session session = sessionManager.createSession("user1", Session.SessionType.USER, null);
        
        assertNotNull(session);
        assertNotNull(session.getId());
        assertEquals("user1", session.getUserId());
        assertEquals(Session.SessionType.USER, session.getType());
        assertEquals(Session.SessionState.ACTIVE, session.getState());
        assertTrue(session.isValid());
        assertTrue(session.isActive());

        // 验证会话已注册
        assertEquals(1, sessionManager.getActiveSessionCount());
        assertEquals(session, sessionManager.getSession(session.getId()));

        // 测试会话属性
        session.setAttribute("level", 10);
        session.setAttribute("gold", 1000);
        session.setAttribute("name", "Player1");

        assertEquals(10, session.getAttribute("level"));
        assertEquals(1000, session.getAttribute("gold"));
        assertEquals("Player1", session.getAttribute("name"));
        assertEquals("default", session.getAttribute("nonexistent", "default"));
        assertTrue(session.hasAttribute("level"));
        assertFalse(session.hasAttribute("nonexistent"));

        // 测试属性移除
        assertEquals(10, session.removeAttribute("level"));
        assertNull(session.getAttribute("level"));
        assertFalse(session.hasAttribute("level"));

        // 销毁会话
        assertTrue(sessionManager.destroySession(session.getId()));
        assertEquals(0, sessionManager.getActiveSessionCount());
        assertNull(sessionManager.getSession(session.getId()));
    }

    @Test
    void testUserSessionManagement() {
        // 为同一用户创建多个会话
        Session session1 = sessionManager.createSession("user1", Session.SessionType.USER, null);
        Session session2 = sessionManager.createSession("user1", Session.SessionType.USER, null);
        Session session3 = sessionManager.createSession("user2", Session.SessionType.USER, null);

        assertEquals(3, sessionManager.getActiveSessionCount());

        // 根据用户ID查找会话
        assertEquals(2, sessionManager.getSessionsByUser("user1").size());
        assertEquals(1, sessionManager.getSessionsByUser("user2").size());
        assertEquals(0, sessionManager.getSessionsByUser("user3").size());

        // 销毁用户的所有会话
        assertEquals(2, sessionManager.destroyUserSessions("user1"));
        assertEquals(1, sessionManager.getActiveSessionCount());
        assertEquals(0, sessionManager.getSessionsByUser("user1").size());
    }

    @Test
    void testSessionListener() throws InterruptedException {
        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger invalidatedCount = new AtomicInteger(0);
        AtomicInteger attributeChangedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3); // 创建、属性变更、无效化

        Session.SessionListener listener = new Session.SessionListener() {
            @Override
            public void onSessionCreated(Session session) {
                createdCount.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onSessionInvalidated(Session session) {
                invalidatedCount.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onAttributeChanged(Session session, String key, Object oldValue, Object newValue) {
                attributeChangedCount.incrementAndGet();
                latch.countDown();
            }
        };

        // 添加全局监听器
        sessionManager.addGlobalSessionListener(listener);

        // 创建会话
        Session session = sessionManager.createSession("user1", Session.SessionType.USER, null);
        
        // 修改属性
        session.setAttribute("test", "value");
        
        // 销毁会话
        sessionManager.destroySession(session.getId());

        // 等待事件通知
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(1, createdCount.get());
        assertTrue(invalidatedCount.get() >= 1); // 可能会触发多次无效化事件
        assertTrue(attributeChangedCount.get() >= 1); // 可能有多个属性变更事件
    }

    @Test
    void testSessionExpiration() throws InterruptedException {
        // 创建会话并设置很短的超时时间
        Session session = sessionManager.createSession("user1", Session.SessionType.USER, null);
        session.setTimeoutMillis(100); // 100毫秒超时

        assertFalse(session.isExpired());
        assertTrue(session.isActive());

        // 等待会话过期
        Thread.sleep(200);

        assertTrue(session.isExpired());
        assertFalse(session.isActive());
        assertEquals(Session.SessionState.EXPIRED, session.getState());

        // 清理过期会话
        int cleanedCount = sessionManager.cleanupExpiredSessions();
        assertEquals(1, cleanedCount);
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    void testSessionStore() throws Exception {
        // 创建会话
        Session session = sessionManager.createSession("user1", Session.SessionType.USER, null);
        session.setAttribute("data", "test data");

        // 验证存储
        assertTrue(sessionStore.existsSession(session.getId()));
        assertEquals(1, sessionStore.getSessionCount());

        // 加载会话
        Session loadedSession = sessionStore.loadSession(session.getId());
        assertNotNull(loadedSession);
        assertEquals(session.getId(), loadedSession.getId());
        assertEquals("user1", loadedSession.getUserId());

        // 删除会话
        assertTrue(sessionStore.deleteSession(session.getId()));
        assertFalse(sessionStore.existsSession(session.getId()));
        assertNull(sessionStore.loadSession(session.getId()));
    }

    @Test
    void testSessionState() {
        Session session = sessionManager.createSession("user1", Session.SessionType.USER, null);
        
        // 初始状态应该是ACTIVE
        assertEquals(Session.SessionState.ACTIVE, session.getState());
        assertTrue(session.isActive());
        assertTrue(session.isValid());

        // 设置为IDLE状态
        session.setState(Session.SessionState.IDLE);
        assertEquals(Session.SessionState.IDLE, session.getState());
        assertFalse(session.isActive());
        assertTrue(session.isValid());

        // 访问会话应该重新激活
        session.updateLastAccessTime();
        assertEquals(Session.SessionState.ACTIVE, session.getState());
        assertTrue(session.isActive());

        // 使会话无效
        session.invalidate();
        assertEquals(Session.SessionState.INVALID, session.getState());
        assertFalse(session.isActive());
        assertFalse(session.isValid());
    }
}