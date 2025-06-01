/*
 * 文件名: ChannelManager.java
 * 用途: 聊天频道管理器
 * 实现内容:
 *   - 频道的创建、销毁和生命周期管理
 *   - 频道成员的加入、退出和权限管理
 *   - 频道列表维护和路由功能
 *   - 频道状态监控和性能统计
 *   - 频道配置的动态更新
 * 技术选型:
 *   - 使用ConcurrentHashMap保证线程安全
 *   - 基于Spring的事件机制
 *   - 支持多种频道类型的工厂模式
 * 依赖关系:
 *   - 管理所有ChatChannel实例
 *   - 被MessageService调用进行消息路由
 *   - 与ConnectionManager配合管理用户连接
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.channel;

import com.lx.gameserver.business.chat.core.ChatChannel;
import com.lx.gameserver.business.chat.core.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 聊天频道管理器
 * <p>
 * 负责管理所有聊天频道的生命周期，包括频道的创建、销毁、
 * 成员管理、路由分发等核心功能。提供统一的频道访问接口
 * 和状态监控能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class ChannelManager {

    /**
     * 频道存储映射（ChannelId -> ChatChannel）
     */
    private final Map<String, ChatChannel> channels = new ConcurrentHashMap<>();

    /**
     * 频道类型映射（ChannelType -> Set<ChannelId>）
     */
    private final Map<ChatMessage.ChatChannelType, Set<String>> channelsByType = new ConcurrentHashMap<>();

    /**
     * 用户频道映射（PlayerId -> Set<ChannelId>）
     */
    private final Map<Long, Set<String>> userChannels = new ConcurrentHashMap<>();

    /**
     * 频道统计信息
     */
    private final Map<String, ChannelStatistics> channelStats = new ConcurrentHashMap<>();

    /**
     * 频道ID生成器
     */
    private final AtomicLong channelIdGenerator = new AtomicLong(1);

    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 频道工厂映射
     */
    private final Map<ChatMessage.ChatChannelType, ChannelFactory> channelFactories = new HashMap<>();

    /**
     * 频道配置
     */
    private ChannelManagerConfig config;

    /**
     * 构造函数
     */
    public ChannelManager() {
        this.config = new ChannelManagerConfig();
        initializeChannelFactories();
        startScheduledTasks();
    }

    /**
     * 初始化频道工厂
     */
    private void initializeChannelFactories() {
        // 这里可以注册不同类型的频道工厂
        // channelFactories.put(ChatMessage.ChatChannelType.WORLD, new WorldChannelFactory());
        // channelFactories.put(ChatMessage.ChatChannelType.GUILD, new GuildChannelFactory());
        // 等等...
    }

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        // 定期清理非活跃频道
        scheduler.scheduleAtFixedRate(this::cleanupInactiveChannels, 
                config.getCleanupInterval(), config.getCleanupInterval(), TimeUnit.MINUTES);

        // 定期更新统计信息
        scheduler.scheduleAtFixedRate(this::updateStatistics, 
                1, 1, TimeUnit.MINUTES);
    }

    // ===== 频道管理核心方法 =====

    /**
     * 创建频道
     *
     * @param channelType 频道类型
     * @param channelName 频道名称
     * @param creatorId 创建者ID
     * @param config 频道配置
     * @return 创建的频道，如果失败则返回null
     */
    public ChatChannel createChannel(ChatMessage.ChatChannelType channelType, String channelName, 
                                   Long creatorId, ChatChannel.ChannelConfig config) {
        try {
            String channelId = generateChannelId(channelType);
            
            ChannelFactory factory = channelFactories.get(channelType);
            ChatChannel channel;
            
            if (factory != null) {
                channel = factory.createChannel(channelId, channelType, channelName);
            } else {
                // 使用默认实现
                channel = new DefaultChatChannel(channelId, channelType, channelName);
            }

            if (config != null) {
                channel.setConfig(config);
            }

            // 如果有创建者，添加为管理员
            if (creatorId != null) {
                channel.addMember(creatorId);
                channel.addAdministrator(creatorId);
            }

            // 注册频道
            registerChannel(channel);

            log.info("创建频道成功: channelId={}, type={}, name={}, creator={}", 
                    channelId, channelType, channelName, creatorId);
            
            return channel;
        } catch (Exception e) {
            log.error("创建频道失败: type={}, name={}, creator={}", channelType, channelName, creatorId, e);
            return null;
        }
    }

    /**
     * 销毁频道
     *
     * @param channelId 频道ID
     * @return 销毁结果
     */
    public boolean destroyChannel(String channelId) {
        if (channelId == null) {
            return false;
        }

        ChatChannel channel = channels.remove(channelId);
        if (channel == null) {
            log.warn("尝试销毁不存在的频道: {}", channelId);
            return false;
        }

        try {
            // 移除所有成员
            List<Long> members = channel.getMemberIds();
            for (Long memberId : members) {
                removeUserFromChannel(memberId, channelId);
            }

            // 关闭频道
            channel.close();

            // 从类型映射中移除
            Set<String> typeChannels = channelsByType.get(channel.getChannelType());
            if (typeChannels != null) {
                typeChannels.remove(channelId);
            }

            // 移除统计信息
            channelStats.remove(channelId);

            log.info("销毁频道成功: channelId={}, type={}", channelId, channel.getChannelType());
            return true;
        } catch (Exception e) {
            log.error("销毁频道失败: channelId={}", channelId, e);
            return false;
        }
    }

    /**
     * 获取频道
     *
     * @param channelId 频道ID
     * @return 频道实例，如果不存在则返回null
     */
    public ChatChannel getChannel(String channelId) {
        return channels.get(channelId);
    }

    /**
     * 检查频道是否存在
     *
     * @param channelId 频道ID
     * @return true表示存在
     */
    public boolean channelExists(String channelId) {
        return channels.containsKey(channelId);
    }

    // ===== 成员管理方法 =====

    /**
     * 用户加入频道
     *
     * @param playerId 玩家ID
     * @param channelId 频道ID
     * @return 加入结果
     */
    public boolean joinChannel(Long playerId, String channelId) {
        if (playerId == null || channelId == null) {
            return false;
        }

        ChatChannel channel = channels.get(channelId);
        if (channel == null) {
            log.warn("用户 {} 尝试加入不存在的频道: {}", playerId, channelId);
            return false;
        }

        if (!channel.isActive()) {
            log.warn("用户 {} 尝试加入非活跃频道: {}", playerId, channelId);
            return false;
        }

        boolean success = channel.addMember(playerId);
        if (success) {
            // 更新用户频道映射
            userChannels.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(channelId);
            
            // 更新统计信息
            ChannelStatistics stats = channelStats.computeIfAbsent(channelId, k -> new ChannelStatistics(channelId));
            stats.incrementJoinCount();
        }

        return success;
    }

    /**
     * 用户离开频道
     *
     * @param playerId 玩家ID
     * @param channelId 频道ID
     * @return 离开结果
     */
    public boolean leaveChannel(Long playerId, String channelId) {
        if (playerId == null || channelId == null) {
            return false;
        }

        ChatChannel channel = channels.get(channelId);
        if (channel == null) {
            return false;
        }

        boolean success = channel.removeMember(playerId);
        if (success) {
            removeUserFromChannel(playerId, channelId);
            
            // 更新统计信息
            ChannelStatistics stats = channelStats.get(channelId);
            if (stats != null) {
                stats.incrementLeaveCount();
            }
        }

        return success;
    }

    /**
     * 从用户频道映射中移除
     */
    private void removeUserFromChannel(Long playerId, String channelId) {
        Set<String> playerChannels = userChannels.get(playerId);
        if (playerChannels != null) {
            playerChannels.remove(channelId);
            if (playerChannels.isEmpty()) {
                userChannels.remove(playerId);
            }
        }
    }

    /**
     * 获取用户加入的所有频道
     *
     * @param playerId 玩家ID
     * @return 频道ID列表
     */
    public List<String> getUserChannels(Long playerId) {
        Set<String> channelIds = userChannels.get(playerId);
        return channelIds != null ? new ArrayList<>(channelIds) : new ArrayList<>();
    }

    /**
     * 获取用户在指定类型频道中的列表
     *
     * @param playerId 玩家ID
     * @param channelType 频道类型
     * @return 频道列表
     */
    public List<ChatChannel> getUserChannelsByType(Long playerId, ChatMessage.ChatChannelType channelType) {
        Set<String> userChannelIds = userChannels.get(playerId);
        if (userChannelIds == null || userChannelIds.isEmpty()) {
            return new ArrayList<>();
        }

        return userChannelIds.stream()
                .map(channels::get)
                .filter(Objects::nonNull)
                .filter(channel -> channelType.equals(channel.getChannelType()))
                .collect(Collectors.toList());
    }

    // ===== 频道查询方法 =====

    /**
     * 根据类型获取频道列表
     *
     * @param channelType 频道类型
     * @return 频道列表
     */
    public List<ChatChannel> getChannelsByType(ChatMessage.ChatChannelType channelType) {
        Set<String> channelIds = channelsByType.get(channelType);
        if (channelIds == null || channelIds.isEmpty()) {
            return new ArrayList<>();
        }

        return channelIds.stream()
                .map(channels::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃频道
     *
     * @return 活跃频道列表
     */
    public List<ChatChannel> getActiveChannels() {
        return channels.values().stream()
                .filter(ChatChannel::isActive)
                .collect(Collectors.toList());
    }

    /**
     * 获取频道统计信息
     *
     * @param channelId 频道ID
     * @return 统计信息
     */
    public ChannelStatistics getChannelStatistics(String channelId) {
        return channelStats.get(channelId);
    }

    /**
     * 获取所有频道统计信息
     *
     * @return 统计信息映射
     */
    public Map<String, ChannelStatistics> getAllChannelStatistics() {
        return new HashMap<>(channelStats);
    }

    // ===== 内部方法 =====

    /**
     * 注册频道
     */
    private void registerChannel(ChatChannel channel) {
        channels.put(channel.getChannelId(), channel);
        
        // 添加到类型映射
        channelsByType.computeIfAbsent(channel.getChannelType(), k -> ConcurrentHashMap.newKeySet())
                .add(channel.getChannelId());
        
        // 初始化统计信息
        channelStats.put(channel.getChannelId(), new ChannelStatistics(channel.getChannelId()));
    }

    /**
     * 生成频道ID
     */
    private String generateChannelId(ChatMessage.ChatChannelType channelType) {
        long id = channelIdGenerator.getAndIncrement();
        return channelType.getCode() + "_" + id + "_" + System.currentTimeMillis();
    }

    /**
     * 清理非活跃频道
     */
    private void cleanupInactiveChannels() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(config.getInactiveThresholdMinutes());
            List<String> toRemove = new ArrayList<>();

            for (ChatChannel channel : channels.values()) {
                if (channel.getLastActiveTime().isBefore(cutoffTime) && 
                    channel.getMemberCount() == 0 &&
                    !isSystemChannel(channel)) {
                    toRemove.add(channel.getChannelId());
                }
            }

            for (String channelId : toRemove) {
                destroyChannel(channelId);
                log.info("清理非活跃频道: {}", channelId);
            }

            if (!toRemove.isEmpty()) {
                log.info("本次清理了 {} 个非活跃频道", toRemove.size());
            }
        } catch (Exception e) {
            log.error("清理非活跃频道时发生异常", e);
        }
    }

    /**
     * 检查是否为系统频道（不应被自动清理）
     */
    private boolean isSystemChannel(ChatChannel channel) {
        return ChatMessage.ChatChannelType.WORLD.equals(channel.getChannelType()) ||
               ChatMessage.ChatChannelType.SYSTEM.equals(channel.getChannelType());
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        try {
            for (Map.Entry<String, ChatChannel> entry : channels.entrySet()) {
                String channelId = entry.getKey();
                ChatChannel channel = entry.getValue();
                
                ChannelStatistics stats = channelStats.get(channelId);
                if (stats != null) {
                    stats.updateCurrentMembers(channel.getMemberCount());
                    stats.updateMessageCount(channel.getMessageCount());
                    stats.setLastUpdateTime(LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            log.error("更新频道统计信息时发生异常", e);
        }
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ===== 内部类定义 =====

    /**
     * 频道工厂接口
     */
    public interface ChannelFactory {
        ChatChannel createChannel(String channelId, ChatMessage.ChatChannelType channelType, String channelName);
    }

    /**
     * 默认频道实现
     */
    private static class DefaultChatChannel extends ChatChannel {
        public DefaultChatChannel(String channelId, ChatMessage.ChatChannelType channelType, String channelName) {
            super(channelId, channelType, channelName);
        }

        @Override
        public boolean sendMessage(ChatMessage message) {
            // 基础实现
            return true;
        }

        @Override
        public void onMessageReceived(ChatMessage message) {
            // 基础实现
            updateLastActiveTime();
            incrementMessageCount();
        }

        @Override
        public boolean hasPermission(Long playerId) {
            return isMember(playerId) && !isMuted(playerId);
        }

        @Override
        public List<Long> getTargetAudience() {
            return getMemberIds();
        }
    }

    /**
     * 频道统计信息类
     */
    @lombok.Data
    public static class ChannelStatistics {
        private String channelId;
        private long totalMessages;
        private int currentMembers;
        private int maxMembers;
        private long joinCount;
        private long leaveCount;
        private LocalDateTime createTime;
        private LocalDateTime lastUpdateTime;

        public ChannelStatistics(String channelId) {
            this.channelId = channelId;
            this.createTime = LocalDateTime.now();
            this.lastUpdateTime = LocalDateTime.now();
        }

        public void incrementJoinCount() {
            this.joinCount++;
        }

        public void incrementLeaveCount() {
            this.leaveCount++;
        }

        public void updateCurrentMembers(int count) {
            this.currentMembers = count;
            if (count > maxMembers) {
                this.maxMembers = count;
            }
        }

        public void updateMessageCount(long count) {
            this.totalMessages = count;
        }
    }

    /**
     * 频道管理器配置类
     */
    @lombok.Data
    public static class ChannelManagerConfig {
        /** 清理任务间隔（分钟） */
        private int cleanupInterval = 30;
        
        /** 非活跃频道阈值（分钟） */
        private int inactiveThresholdMinutes = 60;
        
        /** 最大频道数量 */
        private int maxChannels = 10000;
        
        /** 是否启用统计 */
        private boolean enableStatistics = true;
    }
}