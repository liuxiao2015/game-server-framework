/*
 * 文件名: ChatModuleTest.java
 * 用途: 聊天模块集成测试
 * 实现内容:
 *   - 聊天模块核心功能的集成测试
 *   - 消息发送和接收的端到端测试
 *   - 频道管理和会话管理测试
 *   - 消息过滤和安全功能测试
 * 技术选型:
 *   - JUnit 5测试框架
 *   - Spring Boot Test支持
 *   - 模拟数据和断言验证
 * 依赖关系:
 *   - 测试所有聊天模块组件
 *   - 验证组件间集成正确性
 *   - 确保核心功能的可靠性
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat;

import com.lx.gameserver.business.chat.channel.ChannelManager;
import com.lx.gameserver.business.chat.channel.PrivateChannel;
import com.lx.gameserver.business.chat.channel.WorldChannel;
import com.lx.gameserver.business.chat.core.ChatChannel;
import com.lx.gameserver.business.chat.core.ChatMessage;
import com.lx.gameserver.business.chat.core.ChatSession;
import com.lx.gameserver.business.chat.message.MessageFilter;
import com.lx.gameserver.business.chat.message.MessageService;
import com.lx.gameserver.business.chat.config.ChatConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天模块集成测试类
 * <p>
 * 提供聊天模块的完整功能测试，验证各组件的正确性和集成效果。
 * 包含消息处理、频道管理、会话管理等核心功能的测试用例。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ChatModuleTest {

    private ChannelManager channelManager;
    private MessageService messageService;
    private MessageFilter messageFilter;
    private ChatConfig chatConfig;

    @BeforeEach
    public void setUp() {
        // 初始化测试组件
        channelManager = new ChannelManager();
        messageService = new MessageService();
        messageFilter = new MessageFilter();
        chatConfig = new ChatConfig();
    }

    // ===== 消息相关测试 =====

    @Test
    @DisplayName("测试聊天消息创建和基本属性")
    public void testChatMessageCreation() {
        // 创建测试消息
        ChatMessage message = ChatMessage.builder()
                .messageId("test_msg_001")
                .senderId(12345L)
                .senderName("TestUser")
                .channelType(ChatMessage.ChatChannelType.WORLD)
                .messageType(ChatMessage.MessageType.TEXT)
                .content("Hello, World!")
                .sendTime(LocalDateTime.now())
                .status(ChatMessage.MessageStatus.SENT)
                .priority(ChatMessage.MessagePriority.NORMAL)
                .isSystemMessage(false)
                .build();

        // 验证基本属性
        assertNotNull(message);
        assertEquals("test_msg_001", message.getMessageId());
        assertEquals(12345L, message.getSenderId());
        assertEquals("TestUser", message.getSenderName());
        assertEquals(ChatMessage.ChatChannelType.WORLD, message.getChannelType());
        assertEquals(ChatMessage.MessageType.TEXT, message.getMessageType());
        assertEquals("Hello, World!", message.getContent());
        assertEquals(ChatMessage.MessageStatus.SENT, message.getStatus());
        assertEquals(ChatMessage.MessagePriority.NORMAL, message.getPriority());
        assertFalse(message.isSystemMsg());

        // 测试消息长度
        assertEquals(13, message.getContentLength());

        // 测试非私聊消息
        assertFalse(message.isPrivateMessage());

        // 测试扩展数据
        message.setExtraData("key1", "value1");
        assertEquals("value1", message.getExtraData("key1"));
    }

    @Test
    @DisplayName("测试私聊消息属性")
    public void testPrivateChatMessage() {
        ChatMessage privateMessage = ChatMessage.builder()
                .messageId("private_msg_001")
                .senderId(12345L)
                .receiverId(67890L)
                .channelType(ChatMessage.ChatChannelType.PRIVATE)
                .messageType(ChatMessage.MessageType.TEXT)
                .content("Private message")
                .sendTime(LocalDateTime.now())
                .build();

        assertTrue(privateMessage.isPrivateMessage());
        assertEquals(67890L, privateMessage.getReceiverId());
    }

    @Test
    @DisplayName("测试系统消息属性")
    public void testSystemMessage() {
        ChatMessage systemMessage = ChatMessage.builder()
                .messageId("system_msg_001")
                .senderId(0L)
                .senderName("系统")
                .channelType(ChatMessage.ChatChannelType.SYSTEM)
                .messageType(ChatMessage.MessageType.SYSTEM)
                .content("系统公告")
                .isSystemMessage(true)
                .build();

        assertTrue(systemMessage.isSystemMsg());
        assertEquals(0L, systemMessage.getSenderId());
        assertEquals("系统", systemMessage.getSenderName());
    }

    // ===== 频道管理测试 =====

    @Test
    @DisplayName("测试世界频道创建和基本功能")
    public void testWorldChannelCreation() {
        WorldChannel worldChannel = new WorldChannel("world_test", "测试世界频道");

        assertNotNull(worldChannel);
        assertEquals("world_test", worldChannel.getChannelId());
        assertEquals("测试世界频道", worldChannel.getChannelName());
        assertEquals(ChatMessage.ChatChannelType.WORLD, worldChannel.getChannelType());
        assertTrue(worldChannel.isActive());
        assertTrue(worldChannel.isWorldChannel());

        // 测试添加成员
        assertTrue(worldChannel.addMember(12345L));
        assertTrue(worldChannel.isMember(12345L));
        assertEquals(1, worldChannel.getMemberCount());

        // 测试权限检查
        assertTrue(worldChannel.hasPermission(12345L));

        // 测试移除成员
        assertTrue(worldChannel.removeMember(12345L));
        assertFalse(worldChannel.isMember(12345L));
        assertEquals(0, worldChannel.getMemberCount());
    }

    @Test
    @DisplayName("测试私聊频道创建和功能")
    public void testPrivateChannelCreation() {
        Long player1 = 12345L;
        Long player2 = 67890L;
        String channelId = PrivateChannel.generateChannelId(player1, player2);
        
        PrivateChannel privateChannel = new PrivateChannel(channelId, player1, player2);

        assertNotNull(privateChannel);
        assertEquals(channelId, privateChannel.getChannelId());
        assertEquals(ChatMessage.ChatChannelType.PRIVATE, privateChannel.getChannelType());
        assertEquals(player1, privateChannel.getParticipant1());
        assertEquals(player2, privateChannel.getParticipant2());

        // 验证两个参与者都已加入
        assertTrue(privateChannel.isParticipant(player1));
        assertTrue(privateChannel.isParticipant(player2));
        assertEquals(2, privateChannel.getMemberCount());

        // 测试获取另一个参与者
        assertEquals(player2, privateChannel.getOtherParticipant(player1));
        assertEquals(player1, privateChannel.getOtherParticipant(player2));

        // 测试权限检查
        assertTrue(privateChannel.hasPermission(player1));
        assertTrue(privateChannel.hasPermission(player2));
        assertFalse(privateChannel.hasPermission(99999L));
    }

    @Test
    @DisplayName("测试频道管理器功能")
    public void testChannelManager() {
        // 创建世界频道
        ChatChannel worldChannel = channelManager.createChannel(
                ChatMessage.ChatChannelType.WORLD,
                "测试世界频道",
                null,
                null
        );
        
        assertNotNull(worldChannel);
        assertTrue(channelManager.channelExists(worldChannel.getChannelId()));

        // 测试用户加入频道
        Long playerId = 12345L;
        assertTrue(channelManager.joinChannel(playerId, worldChannel.getChannelId()));
        
        List<String> userChannels = channelManager.getUserChannels(playerId);
        assertTrue(userChannels.contains(worldChannel.getChannelId()));

        // 测试用户离开频道
        assertTrue(channelManager.leaveChannel(playerId, worldChannel.getChannelId()));
        userChannels = channelManager.getUserChannels(playerId);
        assertFalse(userChannels.contains(worldChannel.getChannelId()));

        // 测试销毁频道
        assertTrue(channelManager.destroyChannel(worldChannel.getChannelId()));
        assertFalse(channelManager.channelExists(worldChannel.getChannelId()));
    }

    // ===== 会话管理测试 =====

    @Test
    @DisplayName("测试聊天会话创建和管理")
    public void testChatSessionCreation() {
        String sessionId = "session_test_001";
        Long creatorId = 12345L;
        
        ChatSession session = new ChatSession(sessionId, ChatSession.SessionType.PRIVATE, creatorId);

        assertNotNull(session);
        assertEquals(sessionId, session.getSessionId());
        assertEquals(ChatSession.SessionType.PRIVATE, session.getSessionType());
        assertEquals(creatorId, session.getCreatorId());
        assertTrue(session.isActive());
        assertFalse(session.isExpired());

        // 测试添加参与者
        assertTrue(session.addParticipant(12345L, "User1", ChatSession.ParticipantRole.CREATOR));
        assertTrue(session.addParticipant(67890L, "User2", ChatSession.ParticipantRole.MEMBER));
        
        assertEquals(2, session.getParticipantCount());
        assertTrue(session.isParticipant(12345L));
        assertTrue(session.isParticipant(67890L));

        // 测试未读消息管理
        session.incrementUnreadCount(67890L, 3);
        assertEquals(3, session.getUnreadCount(67890L));
        
        session.markAsRead(67890L, LocalDateTime.now());
        assertEquals(0, session.getUnreadCount(67890L));

        // 测试移除参与者
        assertTrue(session.removeParticipant(67890L));
        assertEquals(1, session.getParticipantCount());
        assertFalse(session.isParticipant(67890L));
    }

    // ===== 消息过滤测试 =====

    @Test
    @DisplayName("测试消息过滤器基本功能")
    public void testMessageFilter() {
        // 测试支持的消息类型
        assertTrue(messageFilter.supports(ChatMessage.MessageType.TEXT));
        assertTrue(messageFilter.supports(ChatMessage.MessageType.EMOJI));

        // 测试处理器名称和优先级
        assertEquals("MessageFilter", messageFilter.getName());
        assertEquals(10, messageFilter.getPriority());

        // 测试敏感词添加
        messageFilter.addSensitiveWord("测试敏感词");
        
        // 测试白名单用户
        Long whitelistUserId = 99999L;
        messageFilter.addWhitelistUser(whitelistUserId);
        
        // 测试垃圾信息模式
        messageFilter.addSpamPattern(".*广告.*");

        // 验证统计信息
        MessageFilter.FilterStatistics stats = messageFilter.getStatistics();
        assertNotNull(stats);
    }

    @Test
    @DisplayName("测试消息过滤逻辑")
    public void testMessageFiltering() {
        // 创建测试消息
        ChatMessage message = ChatMessage.builder()
                .messageId("filter_test_001")
                .senderId(12345L)
                .messageType(ChatMessage.MessageType.TEXT)
                .content("这是一条正常的消息")
                .build();

        // 创建模拟处理上下文
        TestMessageHandleContext context = new TestMessageHandleContext();

        // 测试正常消息通过
        MessageFilter.FilterResult result = messageFilter.filterMessage(message, context);
        assertEquals(MessageFilter.FilterResult.PASS, result);
    }

    // ===== 配置测试 =====

    @Test
    @DisplayName("测试聊天配置")
    public void testChatConfig() {
        assertNotNull(chatConfig);
        assertNotNull(chatConfig.getConnection());
        assertNotNull(chatConfig.getChannels());
        assertNotNull(chatConfig.getMessage());
        assertNotNull(chatConfig.getSecurity());
        assertNotNull(chatConfig.getStorage());
        assertNotNull(chatConfig.getMonitor());

        // 测试配置验证
        assertTrue(chatConfig.validate());

        // 测试获取频道配置
        ChatConfig.ChannelConfig worldConfig = chatConfig.getChannelConfig("world");
        assertNotNull(worldConfig);

        // 测试配置摘要
        String summary = chatConfig.getConfigSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("ChatConfig"));

        // 测试启用的频道类型
        List<String> enabledChannels = chatConfig.getEnabledChannelTypes();
        assertNotNull(enabledChannels);
        assertFalse(enabledChannels.isEmpty());
    }

    // ===== 辅助测试类 =====

    /**
     * 测试用的消息处理上下文
     */
    private static class TestMessageHandleContext implements MessageFilter.MessageHandleContext {
        
        @Override
        public ChatSession getSession() {
            return null;
        }

        @Override
        public ChatChannel getChannel() {
            return null;
        }

        @Override
        public Long getSenderId() {
            return 12345L;
        }

        @Override
        public List<Long> getReceiverIds() {
            return List.of();
        }

        @Override
        public Object getAttribute(String key) {
            return null;
        }

        @Override
        public void setAttribute(String key, Object value) {
            // 测试实现
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public long getStartTime() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean needRecordHistory() {
            return true;
        }

        @Override
        public boolean needPush() {
            return true;
        }
    }
}