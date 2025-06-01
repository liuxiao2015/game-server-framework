/*
 * 文件名: PlayerManager.java
 * 用途: 玩家管理器
 * 实现内容:
 *   - 玩家上线下线流程管理
 *   - 在线玩家维护和查找功能
 *   - 玩家数据加载和保存
 *   - 并发控制和线程安全
 *   - 玩家统计和监控功能
 * 技术选型:
 *   - ConcurrentHashMap实现线程安全的玩家缓存
 *   - 读写锁优化并发访问性能
 *   - 定时任务进行数据持久化
 *   - 事件发布机制同步状态变更
 * 依赖关系:
 *   - 管理Player实体的生命周期
 *   - 与PlayerService协作进行数据操作
 *   - 被LogicServer和其他模块调用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.logic.player;

import com.lx.gameserver.business.logic.core.LogicContext;
import com.lx.gameserver.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 玩家管理器
 * <p>
 * 负责管理所有在线玩家的生命周期，包括玩家上线、下线、
 * 数据同步等功能。提供高效的玩家查找和状态管理能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@Slf4j
@Component
public class PlayerManager {

    /**
     * 玩家事件基类
     */
    public static abstract class PlayerEvent {
        private final Long playerId;
        private final LocalDateTime timestamp;

        public PlayerEvent(Long playerId) {
            this.playerId = playerId;
            this.timestamp = LocalDateTime.now();
        }

        public Long getPlayerId() {
            return playerId;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 玩家上线事件
     */
    public static class PlayerOnlineEvent extends PlayerEvent {
        private final String sessionId;

        public PlayerOnlineEvent(Long playerId, String sessionId) {
            super(playerId);
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }
    }

    /**
     * 玩家下线事件
     */
    public static class PlayerOfflineEvent extends PlayerEvent {
        private final String reason;

        public PlayerOfflineEvent(Long playerId, String reason) {
            super(playerId);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 玩家状态变更事件
     */
    public static class PlayerStateChangedEvent extends PlayerEvent {
        private final Player.PlayerState oldState;
        private final Player.PlayerState newState;

        public PlayerStateChangedEvent(Long playerId, Player.PlayerState oldState, Player.PlayerState newState) {
            super(playerId);
            this.oldState = oldState;
            this.newState = newState;
        }

        public Player.PlayerState getOldState() {
            return oldState;
        }

        public Player.PlayerState getNewState() {
            return newState;
        }
    }

    /** 在线玩家缓存 */
    private final ConcurrentHashMap<Long, Player> onlinePlayers = new ConcurrentHashMap<>();

    /** 玩家会话映射 */
    private final ConcurrentHashMap<String, Long> sessionPlayerMap = new ConcurrentHashMap<>();

    /** 用户名到玩家ID映射 */
    private final ConcurrentHashMap<String, Long> usernamePlayerMap = new ConcurrentHashMap<>();

    /** 读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** 在线玩家数量 */
    private final AtomicInteger onlineCount = new AtomicInteger(0);

    /** 历史最高在线数 */
    private final AtomicInteger maxOnlineCount = new AtomicInteger(0);

    /** 总登录次数 */
    private final AtomicLong totalLoginCount = new AtomicLong(0);

    /** 数据保存执行器 */
    private ScheduledExecutorService saveExecutor;

    /** 清理执行器 */
    private ScheduledExecutorService cleanupExecutor;

    /** 逻辑上下文 */
    @Autowired
    private LogicContext logicContext;

    /** 玩家服务 */
    @Autowired(required = false)
    private PlayerService playerService;

    /** 事件发布器 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 最大在线玩家数 */
    private int maxPlayers = 5000;

    /** 数据保存间隔（秒） */
    private int saveInterval = 300; // 5分钟

    /** 离线清理间隔（秒） */
    private int cleanupInterval = 3600; // 1小时

    /**
     * 初始化管理器
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化玩家管理器...");

        // 从配置获取参数
        if (logicContext != null) {
            maxPlayers = logicContext.getProperty("game.logic.server.max-players", Integer.class, 5000);
            saveInterval = logicContext.getProperty("game.logic.player.save-interval", Integer.class, 300);
            cleanupInterval = logicContext.getProperty("game.logic.player.cleanup-interval", Integer.class, 3600);
        }

        // 启动定时保存
        startPeriodicSave();

        // 启动定时清理
        startPeriodicCleanup();

        log.info("玩家管理器初始化完成，最大玩家数: {}, 保存间隔: {}秒, 清理间隔: {}秒",
                maxPlayers, saveInterval, cleanupInterval);
    }

    /**
     * 销毁管理器
     */
    @PreDestroy
    public void destroy() {
        log.info("销毁玩家管理器...");

        // 停止定时任务
        stopScheduledTasks();

        // 保存所有在线玩家数据
        saveAllPlayers();

        // 清理缓存
        onlinePlayers.clear();
        sessionPlayerMap.clear();
        usernamePlayerMap.clear();

        log.info("玩家管理器已销毁");
    }

    /**
     * 玩家上线
     *
     * @param playerId  玩家ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    public Result<Player> playerOnline(Long playerId, String sessionId) {
        if (playerId == null || sessionId == null) {
            return Result.error("参数不能为空");
        }

        // 检查在线人数限制
        if (onlineCount.get() >= maxPlayers) {
            return Result.error("服务器已满，无法上线");
        }

        lock.writeLock().lock();
        try {
            // 检查玩家是否已在线
            Player existingPlayer = onlinePlayers.get(playerId);
            if (existingPlayer != null) {
                log.warn("玩家 {} 重复上线，原会话: {}, 新会话: {}", 
                        playerId, existingPlayer.getSessionId(), sessionId);
                
                // 踢掉原会话
                String oldSessionId = existingPlayer.getSessionId();
                if (oldSessionId != null) {
                    sessionPlayerMap.remove(oldSessionId);
                }
            }

            // 加载玩家数据
            Player player = loadPlayer(playerId);
            if (player == null) {
                return Result.error("玩家不存在");
            }

            // 检查封禁状态
            if (player.isBanned()) {
                return Result.error("玩家已被封禁");
            }

            // 记录上线
            player.recordLogin(sessionId);

            // 更新缓存
            onlinePlayers.put(playerId, player);
            sessionPlayerMap.put(sessionId, playerId);
            usernamePlayerMap.put(player.getUsername(), playerId);

            // 更新统计
            int currentOnline = onlineCount.incrementAndGet();
            totalLoginCount.incrementAndGet();
            maxOnlineCount.updateAndGet(max -> Math.max(max, currentOnline));

            // 发布事件
            publishEvent(new PlayerOnlineEvent(playerId, sessionId));

            log.info("玩家 {} 上线成功，当前在线人数: {}", playerId, currentOnline);
            return Result.success(player);

        } catch (Exception e) {
            log.error("玩家 {} 上线失败", playerId, e);
            return Result.error("上线失败: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 玩家下线
     *
     * @param playerId 玩家ID
     * @param reason   下线原因
     * @return 操作结果
     */
    public Result<Void> playerOffline(Long playerId, String reason) {
        if (playerId == null) {
            return Result.error("玩家ID不能为空");
        }

        lock.writeLock().lock();
        try {
            Player player = onlinePlayers.remove(playerId);
            if (player == null) {
                log.warn("玩家 {} 未在线，无法下线", playerId);
                return Result.error("玩家未在线");
            }

            // 记录下线
            player.recordLogout();

            // 清理会话
            String sessionId = player.getSessionId();
            if (sessionId != null) {
                sessionPlayerMap.remove(sessionId);
            }

            // 清理用户名映射
            usernamePlayerMap.remove(player.getUsername());

            // 保存玩家数据
            savePlayer(player);

            // 更新统计
            int currentOnline = onlineCount.decrementAndGet();

            // 发布事件
            publishEvent(new PlayerOfflineEvent(playerId, reason));

            log.info("玩家 {} 下线，原因: {}, 当前在线人数: {}", playerId, reason, currentOnline);
            return Result.success();

        } catch (Exception e) {
            log.error("玩家 {} 下线失败", playerId, e);
            return Result.error("下线失败: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 通过会话下线
     *
     * @param sessionId 会话ID
     * @param reason    下线原因
     * @return 操作结果
     */
    public Result<Void> playerOfflineBySession(String sessionId, String reason) {
        if (sessionId == null) {
            return Result.error("会话ID不能为空");
        }

        Long playerId = sessionPlayerMap.get(sessionId);
        if (playerId == null) {
            log.warn("会话 {} 未找到对应玩家", sessionId);
            return Result.error("会话无效");
        }

        return playerOffline(playerId, reason);
    }

    /**
     * 获取在线玩家
     *
     * @param playerId 玩家ID
     * @return 玩家对象
     */
    public Player getOnlinePlayer(Long playerId) {
        if (playerId == null) {
            return null;
        }

        lock.readLock().lock();
        try {
            return onlinePlayers.get(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 通过会话获取玩家
     *
     * @param sessionId 会话ID
     * @return 玩家对象
     */
    public Player getPlayerBySession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        Long playerId = sessionPlayerMap.get(sessionId);
        return playerId != null ? getOnlinePlayer(playerId) : null;
    }

    /**
     * 通过用户名获取玩家
     *
     * @param username 用户名
     * @return 玩家对象
     */
    public Player getPlayerByUsername(String username) {
        if (username == null) {
            return null;
        }

        Long playerId = usernamePlayerMap.get(username);
        return playerId != null ? getOnlinePlayer(playerId) : null;
    }

    /**
     * 检查玩家是否在线
     *
     * @param playerId 玩家ID
     * @return 是否在线
     */
    public boolean isPlayerOnline(Long playerId) {
        return playerId != null && onlinePlayers.containsKey(playerId);
    }

    /**
     * 获取所有在线玩家
     *
     * @return 在线玩家列表
     */
    public List<Player> getAllOnlinePlayers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(onlinePlayers.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取在线玩家ID列表
     *
     * @return 玩家ID列表
     */
    public Set<Long> getOnlinePlayerIds() {
        lock.readLock().lock();
        try {
            return new HashSet<>(onlinePlayers.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 按条件查找在线玩家
     *
     * @param predicate 查找条件
     * @return 符合条件的玩家列表
     */
    public List<Player> findOnlinePlayers(java.util.function.Predicate<Player> predicate) {
        lock.readLock().lock();
        try {
            return onlinePlayers.values().stream()
                    .filter(predicate)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 广播消息给所有在线玩家
     *
     * @param message 消息内容
     * @param filter  过滤条件
     */
    public void broadcastToOnlinePlayers(Object message, java.util.function.Predicate<Player> filter) {
        List<Player> targetPlayers = getAllOnlinePlayers();
        
        if (filter != null) {
            targetPlayers = targetPlayers.stream()
                    .filter(filter)
                    .collect(Collectors.toList());
        }

        for (Player player : targetPlayers) {
            try {
                // 这里可以通过Actor系统或其他方式发送消息
                // playerActorRef.tell(message, ActorRef.noSender());
                log.debug("向玩家 {} 发送消息: {}", player.getPlayerId(), message.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("向玩家 {} 发送消息失败", player.getPlayerId(), e);
            }
        }

        log.info("广播消息给 {} 个在线玩家", targetPlayers.size());
    }

    /**
     * 踢出玩家
     *
     * @param playerId 玩家ID
     * @param reason   踢出原因
     * @return 操作结果
     */
    public Result<Void> kickPlayer(Long playerId, String reason) {
        Player player = getOnlinePlayer(playerId);
        if (player == null) {
            return Result.error("玩家未在线");
        }

        log.info("踢出玩家 {}, 原因: {}", playerId, reason);
        return playerOffline(playerId, "被踢出: " + reason);
    }

    /**
     * 更新玩家状态
     *
     * @param playerId 玩家ID
     * @param newState 新状态
     * @return 操作结果
     */
    public Result<Void> updatePlayerState(Long playerId, Player.PlayerState newState) {
        Player player = getOnlinePlayer(playerId);
        if (player == null) {
            return Result.error("玩家未在线");
        }

        Player.PlayerState oldState = player.getCurrentState();
        if (player.updateState(newState)) {
            // 发布状态变更事件
            publishEvent(new PlayerStateChangedEvent(playerId, oldState, newState));
            log.debug("玩家 {} 状态从 {} 变更为 {}", playerId, oldState, newState);
            return Result.success();
        }

        return Result.error("状态更新失败");
    }

    /**
     * 加载玩家数据
     */
    private Player loadPlayer(Long playerId) {
        if (playerService != null) {
            try {
                return playerService.getPlayerById(playerId);
            } catch (Exception e) {
                log.error("从数据库加载玩家 {} 失败", playerId, e);
                return null;
            }
        }

        // 如果没有PlayerService，创建临时玩家对象
        log.warn("PlayerService未配置，创建临时玩家对象");
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setUsername("player_" + playerId);
        player.setNickname("玩家" + playerId);
        player.setCreateTime(LocalDateTime.now());
        return player;
    }

    /**
     * 保存玩家数据
     */
    private void savePlayer(Player player) {
        if (playerService != null && player != null) {
            try {
                playerService.savePlayer(player);
                log.debug("保存玩家 {} 数据成功", player.getPlayerId());
            } catch (Exception e) {
                log.error("保存玩家 {} 数据失败", player.getPlayerId(), e);
            }
        }
    }

    /**
     * 保存所有在线玩家数据
     */
    public void saveAllPlayers() {
        List<Player> players = getAllOnlinePlayers();
        log.info("开始保存 {} 个在线玩家数据", players.size());

        int successCount = 0;
        for (Player player : players) {
            try {
                savePlayer(player);
                successCount++;
            } catch (Exception e) {
                log.error("保存玩家 {} 数据失败", player.getPlayerId(), e);
            }
        }

        log.info("保存玩家数据完成，成功: {}, 总数: {}", successCount, players.size());
    }

    /**
     * 启动定时保存
     */
    private void startPeriodicSave() {
        saveExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "player-save");
            t.setDaemon(true);
            return t;
        });

        saveExecutor.scheduleWithFixedDelay(
                this::saveAllPlayers,
                saveInterval,
                saveInterval,
                TimeUnit.SECONDS
        );

        log.info("启动定时保存，间隔: {} 秒", saveInterval);
    }

    /**
     * 启动定时清理
     */
    private void startPeriodicCleanup() {
        cleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "player-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleWithFixedDelay(
                this::cleanupOfflinePlayers,
                cleanupInterval,
                cleanupInterval,
                TimeUnit.SECONDS
        );

        log.info("启动定时清理，间隔: {} 秒", cleanupInterval);
    }

    /**
     * 清理离线玩家
     */
    private void cleanupOfflinePlayers() {
        try {
            List<Long> toRemove = new ArrayList<>();
            LocalDateTime timeout = LocalDateTime.now().minus(Duration.ofMinutes(30));

            lock.writeLock().lock();
            try {
                for (Map.Entry<Long, Player> entry : onlinePlayers.entrySet()) {
                    Player player = entry.getValue();
                    if (player.getCurrentState() == Player.PlayerState.OFFLINE ||
                        (player.getLastLoginTime() != null && player.getLastLoginTime().isBefore(timeout))) {
                        toRemove.add(entry.getKey());
                    }
                }

                for (Long playerId : toRemove) {
                    Player player = onlinePlayers.remove(playerId);
                    if (player != null) {
                        sessionPlayerMap.remove(player.getSessionId());
                        usernamePlayerMap.remove(player.getUsername());
                        savePlayer(player);
                        onlineCount.decrementAndGet();
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }

            if (!toRemove.isEmpty()) {
                log.info("清理 {} 个离线玩家", toRemove.size());
            }

        } catch (Exception e) {
            log.error("清理离线玩家失败", e);
        }
    }

    /**
     * 停止定时任务
     */
    private void stopScheduledTasks() {
        if (saveExecutor != null && !saveExecutor.isShutdown()) {
            saveExecutor.shutdown();
            try {
                if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    saveExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                saveExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 发布事件
     */
    private void publishEvent(Object event) {
        try {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            } else if (logicContext != null) {
                logicContext.publishEvent(event);
            }
        } catch (Exception e) {
            log.warn("发布玩家事件失败: {}", event.getClass().getSimpleName(), e);
        }
    }

    /**
     * 获取在线人数
     *
     * @return 在线人数
     */
    public int getOnlineCount() {
        return onlineCount.get();
    }

    /**
     * 获取历史最高在线数
     *
     * @return 历史最高在线数
     */
    public int getMaxOnlineCount() {
        return maxOnlineCount.get();
    }

    /**
     * 获取总登录次数
     *
     * @return 总登录次数
     */
    public long getTotalLoginCount() {
        return totalLoginCount.get();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("onlineCount", getOnlineCount());
        stats.put("maxOnlineCount", getMaxOnlineCount());
        stats.put("totalLoginCount", getTotalLoginCount());
        stats.put("maxPlayers", maxPlayers);
        stats.put("saveInterval", saveInterval);
        stats.put("cleanupInterval", cleanupInterval);
        return stats;
    }
}