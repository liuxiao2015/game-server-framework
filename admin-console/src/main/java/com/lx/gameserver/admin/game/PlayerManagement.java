/*
 * 文件名: PlayerManagement.java
 * 用途: 玩家管理服务实现
 * 实现内容:
 *   - 玩家查询和搜索功能
 *   - 账号封禁和解封操作
 *   - 玩家数据修改管理
 *   - 邮件发送功能
 *   - 批量操作支持
 *   - 操作审计日志
 * 技术选型:
 *   - Spring Data JPA (数据访问)
 *   - Redis (数据缓存)
 *   - Spring Event (事件通知)
 *   - 分页查询优化
 * 依赖关系: 被游戏管理模块使用，依赖审计和通知系统
 */
package com.lx.gameserver.admin.game;

import com.lx.gameserver.admin.core.AdminContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 玩家管理服务
 * <p>
 * 提供完整的玩家管理功能，包括玩家查询、账号管理、数据修改、
 * 邮件发送等操作。支持批量操作和详细的操作审计，
 * 确保玩家数据的安全性和操作的可追溯性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@Service
public class PlayerManagement {

    /** 管理平台上下文 */
    @Autowired
    private AdminContext adminContext;

    /** 事件发布器 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** Redis模板 */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** 玩家数据缓存 */
    private final Map<Long, PlayerInfo> playerCache = new ConcurrentHashMap<>();

    /** 封禁记录缓存 */
    private final Map<Long, List<BanRecord>> banRecords = new ConcurrentHashMap<>();

    /** 邮件发送队列 */
    private final Map<String, MailTask> mailQueue = new ConcurrentHashMap<>();

    /** Redis键前缀 */
    private static final String REDIS_PLAYER_PREFIX = "admin:player:";
    private static final String REDIS_BAN_PREFIX = "admin:ban:";
    private static final String REDIS_MAIL_PREFIX = "admin:mail:";

    /**
     * 初始化玩家管理服务
     */
    @PostConstruct
    public void init() {
        log.info("初始化玩家管理服务...");
        
        // 加载基础数据
        loadCachedData();
        
        log.info("玩家管理服务初始化完成");
    }

    /**
     * 查询玩家信息
     *
     * @param playerId 玩家ID
     * @return 玩家信息
     */
    public PlayerInfo getPlayerInfo(Long playerId) {
        // 先从缓存获取
        PlayerInfo player = playerCache.get(playerId);
        if (player != null) {
            return player;
        }
        
        // 从Redis获取
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_PLAYER_PREFIX + playerId;
                player = (PlayerInfo) redisTemplate.opsForValue().get(redisKey);
                if (player != null) {
                    playerCache.put(playerId, player);
                    return player;
                }
            } catch (Exception e) {
                log.warn("从Redis获取玩家信息失败: {}", e.getMessage());
            }
        }
        
        // 从数据库获取（这里简化为模拟数据）
        player = loadPlayerFromDatabase(playerId);
        if (player != null) {
            playerCache.put(playerId, player);
            cachePlayerToRedis(player);
        }
        
        return player;
    }

    /**
     * 搜索玩家
     *
     * @param criteria 搜索条件
     * @return 搜索结果
     */
    public PlayerSearchResult searchPlayers(PlayerSearchCriteria criteria) {
        List<PlayerInfo> results = new ArrayList<>();
        
        // 根据搜索条件执行查询
        if (criteria.getPlayerId() != null) {
            PlayerInfo player = getPlayerInfo(criteria.getPlayerId());
            if (player != null) {
                results.add(player);
            }
        } else if (criteria.getPlayerName() != null) {
            results = searchByPlayerName(criteria.getPlayerName());
        } else if (criteria.getEmail() != null) {
            results = searchByEmail(criteria.getEmail());
        } else {
            results = searchByGeneral(criteria);
        }
        
        // 应用过滤条件
        if (criteria.getStatus() != null) {
            results = results.stream()
                .filter(p -> p.getStatus() == criteria.getStatus())
                .toList();
        }
        
        if (criteria.getMinLevel() != null) {
            results = results.stream()
                .filter(p -> p.getLevel() >= criteria.getMinLevel())
                .toList();
        }
        
        if (criteria.getMaxLevel() != null) {
            results = results.stream()
                .filter(p -> p.getLevel() <= criteria.getMaxLevel())
                .toList();
        }
        
        // 分页处理
        int total = results.size();
        int offset = criteria.getPage() * criteria.getSize();
        int limit = Math.min(offset + criteria.getSize(), total);
        
        if (offset < total) {
            results = results.subList(offset, limit);
        } else {
            results = Collections.emptyList();
        }
        
        return new PlayerSearchResult(results, total, criteria.getPage(), criteria.getSize());
    }

    /**
     * 封禁玩家
     *
     * @param playerId 玩家ID
     * @param reason 封禁原因
     * @param duration 封禁时长(小时)，null表示永久封禁
     * @param operator 操作人
     * @return 操作结果
     */
    public PlayerOperationResult banPlayer(Long playerId, String reason, Integer duration, String operator) {
        try {
            PlayerInfo player = getPlayerInfo(playerId);
            if (player == null) {
                return PlayerOperationResult.failure("玩家不存在");
            }
            
            if (player.getStatus() == PlayerStatus.BANNED) {
                return PlayerOperationResult.failure("玩家已被封禁");
            }
            
            // 创建封禁记录
            LocalDateTime expireTime = duration != null ? 
                LocalDateTime.now().plusHours(duration) : null;
            
            BanRecord banRecord = new BanRecord(
                UUID.randomUUID().toString(),
                playerId,
                reason,
                operator,
                LocalDateTime.now(),
                expireTime,
                BanStatus.ACTIVE
            );
            
            // 更新玩家状态
            player.setStatus(PlayerStatus.BANNED);
            player.setBanReason(reason);
            player.setBanExpireTime(expireTime);
            
            // 保存记录
            saveBanRecord(banRecord);
            updatePlayerInfo(player);
            
            // 发布事件
            eventPublisher.publishEvent(new PlayerBannedEvent(playerId, reason, duration, operator));
            
            log.info("玩家封禁成功: playerId={}, reason={}, duration={}h, operator={}", 
                    playerId, reason, duration, operator);
            
            return PlayerOperationResult.success("玩家封禁成功");
            
        } catch (Exception e) {
            log.error("封禁玩家失败: playerId={}", playerId, e);
            return PlayerOperationResult.failure("封禁操作失败: " + e.getMessage());
        }
    }

    /**
     * 解封玩家
     *
     * @param playerId 玩家ID
     * @param operator 操作人
     * @return 操作结果
     */
    public PlayerOperationResult unbanPlayer(Long playerId, String operator) {
        try {
            PlayerInfo player = getPlayerInfo(playerId);
            if (player == null) {
                return PlayerOperationResult.failure("玩家不存在");
            }
            
            if (player.getStatus() != PlayerStatus.BANNED) {
                return PlayerOperationResult.failure("玩家未被封禁");
            }
            
            // 更新玩家状态
            player.setStatus(PlayerStatus.ACTIVE);
            player.setBanReason(null);
            player.setBanExpireTime(null);
            
            // 更新封禁记录
            List<BanRecord> records = banRecords.get(playerId);
            if (records != null) {
                records.stream()
                    .filter(r -> r.getStatus() == BanStatus.ACTIVE)
                    .forEach(r -> {
                        r.setStatus(BanStatus.LIFTED);
                        r.setLiftOperator(operator);
                        r.setLiftTime(LocalDateTime.now());
                    });
            }
            
            // 保存更新
            updatePlayerInfo(player);
            
            // 发布事件
            eventPublisher.publishEvent(new PlayerUnbannedEvent(playerId, operator));
            
            log.info("玩家解封成功: playerId={}, operator={}", playerId, operator);
            
            return PlayerOperationResult.success("玩家解封成功");
            
        } catch (Exception e) {
            log.error("解封玩家失败: playerId={}", playerId, e);
            return PlayerOperationResult.failure("解封操作失败: " + e.getMessage());
        }
    }

    /**
     * 修改玩家数据
     *
     * @param playerId 玩家ID
     * @param updates 更新数据
     * @param operator 操作人
     * @return 操作结果
     */
    public PlayerOperationResult updatePlayerData(Long playerId, Map<String, Object> updates, String operator) {
        try {
            PlayerInfo player = getPlayerInfo(playerId);
            if (player == null) {
                return PlayerOperationResult.failure("玩家不存在");
            }
            
            // 记录原始数据
            Map<String, Object> originalData = new HashMap<>();
            
            // 应用更新
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String field = entry.getKey();
                Object newValue = entry.getValue();
                
                switch (field) {
                    case "level":
                        originalData.put(field, player.getLevel());
                        player.setLevel((Integer) newValue);
                        break;
                    case "experience":
                        originalData.put(field, player.getExperience());
                        player.setExperience((Long) newValue);
                        break;
                    case "gold":
                        originalData.put(field, player.getGold());
                        player.setGold((Long) newValue);
                        break;
                    case "diamond":
                        originalData.put(field, player.getDiamond());
                        player.setDiamond((Long) newValue);
                        break;
                    case "nickname":
                        originalData.put(field, player.getNickname());
                        player.setNickname((String) newValue);
                        break;
                    default:
                        log.warn("不支持的更新字段: {}", field);
                        break;
                }
            }
            
            // 保存更新
            updatePlayerInfo(player);
            
            // 发布事件
            eventPublisher.publishEvent(new PlayerDataUpdatedEvent(playerId, originalData, updates, operator));
            
            log.info("玩家数据更新成功: playerId={}, updates={}, operator={}", 
                    playerId, updates, operator);
            
            return PlayerOperationResult.success("玩家数据更新成功");
            
        } catch (Exception e) {
            log.error("更新玩家数据失败: playerId={}", playerId, e);
            return PlayerOperationResult.failure("数据更新失败: " + e.getMessage());
        }
    }

    /**
     * 发送邮件给玩家
     *
     * @param mailRequest 邮件请求
     * @return 操作结果
     */
    public PlayerOperationResult sendMail(PlayerMailRequest mailRequest) {
        try {
            String mailId = UUID.randomUUID().toString();
            MailTask mailTask = new MailTask(
                mailId,
                mailRequest.getRecipients(),
                mailRequest.getTitle(),
                mailRequest.getContent(),
                mailRequest.getAttachments(),
                mailRequest.getSender(),
                LocalDateTime.now(),
                MailStatus.PENDING
            );
            
            // 添加到发送队列
            mailQueue.put(mailId, mailTask);
            
            // 异步发送
            processMail(mailTask);
            
            log.info("邮件发送任务创建成功: mailId={}, recipients={}", 
                    mailId, mailRequest.getRecipients().size());
            
            return PlayerOperationResult.success("邮件发送任务已创建，ID: " + mailId);
            
        } catch (Exception e) {
            log.error("创建邮件发送任务失败", e);
            return PlayerOperationResult.failure("邮件发送失败: " + e.getMessage());
        }
    }

    /**
     * 批量操作玩家
     *
     * @param playerIds 玩家ID列表
     * @param operation 操作类型
     * @param parameters 操作参数
     * @param operator 操作人
     * @return 批量操作结果
     */
    public BatchPlayerOperationResult batchOperation(List<Long> playerIds, 
                                                   PlayerOperation operation,
                                                   Map<String, Object> parameters, 
                                                   String operator) {
        BatchPlayerOperationResult result = new BatchPlayerOperationResult();
        
        for (Long playerId : playerIds) {
            try {
                PlayerOperationResult opResult = executeOperation(playerId, operation, parameters, operator);
                if (opResult.isSuccess()) {
                    result.addSuccess(playerId, opResult.getMessage());
                } else {
                    result.addFailure(playerId, opResult.getMessage());
                }
            } catch (Exception e) {
                result.addFailure(playerId, e.getMessage());
                log.error("批量操作失败: playerId={}, operation={}", playerId, operation, e);
            }
        }
        
        return result;
    }

    /**
     * 执行单个操作
     */
    private PlayerOperationResult executeOperation(Long playerId, PlayerOperation operation, 
                                                 Map<String, Object> parameters, String operator) {
        switch (operation) {
            case BAN:
                String reason = (String) parameters.get("reason");
                Integer duration = (Integer) parameters.get("duration");
                return banPlayer(playerId, reason, duration, operator);
            
            case UNBAN:
                return unbanPlayer(playerId, operator);
            
            case UPDATE_DATA:
                @SuppressWarnings("unchecked")
                Map<String, Object> updates = (Map<String, Object>) parameters.get("updates");
                return updatePlayerData(playerId, updates, operator);
            
            default:
                return PlayerOperationResult.failure("不支持的操作类型: " + operation);
        }
    }

    /**
     * 处理邮件发送
     */
    private void processMail(MailTask mailTask) {
        // 异步处理邮件发送
        new Thread(() -> {
            try {
                // 模拟邮件发送过程
                Thread.sleep(1000);
                
                // 更新状态
                mailTask.setStatus(MailStatus.SENT);
                mailTask.setSentTime(LocalDateTime.now());
                
                log.info("邮件发送完成: mailId={}", mailTask.getMailId());
                
            } catch (Exception e) {
                mailTask.setStatus(MailStatus.FAILED);
                mailTask.setErrorMessage(e.getMessage());
                log.error("邮件发送失败: mailId={}", mailTask.getMailId(), e);
            }
        }, "MailSender-" + mailTask.getMailId()).start();
    }

    /**
     * 从数据库加载玩家信息（模拟）
     */
    private PlayerInfo loadPlayerFromDatabase(Long playerId) {
        // 模拟数据库查询
        return new PlayerInfo(
            playerId,
            "Player" + playerId,
            "player" + playerId + "@example.com",
            1 + (int) (playerId % 100),
            playerId * 100,
            playerId * 1000,
            playerId * 10,
            PlayerStatus.ACTIVE,
            LocalDateTime.now().minusDays(playerId % 365),
            LocalDateTime.now().minusHours(playerId % 24)
        );
    }

    /**
     * 根据玩家名称搜索
     */
    private List<PlayerInfo> searchByPlayerName(String playerName) {
        // 模拟搜索逻辑
        return playerCache.values().stream()
            .filter(p -> p.getNickname().contains(playerName))
            .toList();
    }

    /**
     * 根据邮箱搜索
     */
    private List<PlayerInfo> searchByEmail(String email) {
        // 模拟搜索逻辑
        return playerCache.values().stream()
            .filter(p -> p.getEmail().contains(email))
            .toList();
    }

    /**
     * 通用搜索
     */
    private List<PlayerInfo> searchByGeneral(PlayerSearchCriteria criteria) {
        // 模拟通用搜索逻辑
        return new ArrayList<>(playerCache.values());
    }

    /**
     * 保存封禁记录
     */
    private void saveBanRecord(BanRecord banRecord) {
        banRecords.computeIfAbsent(banRecord.getPlayerId(), k -> new ArrayList<>())
                  .add(banRecord);
        
        // 缓存到Redis
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_BAN_PREFIX + banRecord.getPlayerId();
                redisTemplate.opsForList().leftPush(redisKey, banRecord);
                redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("缓存封禁记录到Redis失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 更新玩家信息
     */
    private void updatePlayerInfo(PlayerInfo player) {
        playerCache.put(player.getPlayerId(), player);
        cachePlayerToRedis(player);
    }

    /**
     * 缓存玩家信息到Redis
     */
    private void cachePlayerToRedis(PlayerInfo player) {
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_PLAYER_PREFIX + player.getPlayerId();
                redisTemplate.opsForValue().set(redisKey, player, 1, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("缓存玩家信息到Redis失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 加载缓存数据
     */
    private void loadCachedData() {
        // 模拟加载一些测试数据
        for (long i = 1; i <= 100; i++) {
            PlayerInfo player = loadPlayerFromDatabase(i);
            playerCache.put(i, player);
        }
    }

    // 枚举和数据类定义
    public enum PlayerStatus {
        ACTIVE, BANNED, INACTIVE, DELETED
    }

    public enum BanStatus {
        ACTIVE, LIFTED, EXPIRED
    }

    public enum MailStatus {
        PENDING, SENT, FAILED
    }

    public enum PlayerOperation {
        BAN, UNBAN, UPDATE_DATA, SEND_MAIL
    }

    // 数据类定义（省略getter/setter方法）
    public static class PlayerInfo {
        private Long playerId;
        private String nickname;
        private String email;
        private Integer level;
        private Long experience;
        private Long gold;
        private Long diamond;
        private PlayerStatus status;
        private LocalDateTime createTime;
        private LocalDateTime lastLoginTime;
        private String banReason;
        private LocalDateTime banExpireTime;

        public PlayerInfo(Long playerId, String nickname, String email, Integer level, Long experience,
                         Long gold, Long diamond, PlayerStatus status, LocalDateTime createTime, LocalDateTime lastLoginTime) {
            this.playerId = playerId;
            this.nickname = nickname;
            this.email = email;
            this.level = level;
            this.experience = experience;
            this.gold = gold;
            this.diamond = diamond;
            this.status = status;
            this.createTime = createTime;
            this.lastLoginTime = lastLoginTime;
        }

        // Getter和Setter方法
        public Long getPlayerId() { return playerId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getEmail() { return email; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Long getExperience() { return experience; }
        public void setExperience(Long experience) { this.experience = experience; }
        public Long getGold() { return gold; }
        public void setGold(Long gold) { this.gold = gold; }
        public Long getDiamond() { return diamond; }
        public void setDiamond(Long diamond) { this.diamond = diamond; }
        public PlayerStatus getStatus() { return status; }
        public void setStatus(PlayerStatus status) { this.status = status; }
        public LocalDateTime getCreateTime() { return createTime; }
        public LocalDateTime getLastLoginTime() { return lastLoginTime; }
        public String getBanReason() { return banReason; }
        public void setBanReason(String banReason) { this.banReason = banReason; }
        public LocalDateTime getBanExpireTime() { return banExpireTime; }
        public void setBanExpireTime(LocalDateTime banExpireTime) { this.banExpireTime = banExpireTime; }
    }

    public static class PlayerSearchCriteria {
        private Long playerId;
        private String playerName;
        private String email;
        private PlayerStatus status;
        private Integer minLevel;
        private Integer maxLevel;
        private int page = 0;
        private int size = 20;

        // Getter和Setter方法
        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public PlayerStatus getStatus() { return status; }
        public void setStatus(PlayerStatus status) { this.status = status; }
        public Integer getMinLevel() { return minLevel; }
        public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
        public Integer getMaxLevel() { return maxLevel; }
        public void setMaxLevel(Integer maxLevel) { this.maxLevel = maxLevel; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }

    public static class PlayerSearchResult {
        private List<PlayerInfo> players;
        private int total;
        private int page;
        private int size;

        public PlayerSearchResult(List<PlayerInfo> players, int total, int page, int size) {
            this.players = players;
            this.total = total;
            this.page = page;
            this.size = size;
        }

        // Getter方法
        public List<PlayerInfo> getPlayers() { return players; }
        public int getTotal() { return total; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public int getTotalPages() { return (total + size - 1) / size; }
    }

    public static class PlayerOperationResult {
        private boolean success;
        private String message;

        private PlayerOperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static PlayerOperationResult success(String message) {
            return new PlayerOperationResult(true, message);
        }

        public static PlayerOperationResult failure(String message) {
            return new PlayerOperationResult(false, message);
        }

        // Getter方法
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class BatchPlayerOperationResult {
        private Map<Long, String> successResults = new HashMap<>();
        private Map<Long, String> failureResults = new HashMap<>();

        public void addSuccess(Long playerId, String message) {
            successResults.put(playerId, message);
        }

        public void addFailure(Long playerId, String message) {
            failureResults.put(playerId, message);
        }

        // Getter方法
        public Map<Long, String> getSuccessResults() { return successResults; }
        public Map<Long, String> getFailureResults() { return failureResults; }
        public int getSuccessCount() { return successResults.size(); }
        public int getFailureCount() { return failureResults.size(); }
    }

    public static class BanRecord {
        private String banId;
        private Long playerId;
        private String reason;
        private String operator;
        private LocalDateTime banTime;
        private LocalDateTime expireTime;
        private BanStatus status;
        private String liftOperator;
        private LocalDateTime liftTime;

        public BanRecord(String banId, Long playerId, String reason, String operator,
                        LocalDateTime banTime, LocalDateTime expireTime, BanStatus status) {
            this.banId = banId;
            this.playerId = playerId;
            this.reason = reason;
            this.operator = operator;
            this.banTime = banTime;
            this.expireTime = expireTime;
            this.status = status;
        }

        // Getter和Setter方法
        public String getBanId() { return banId; }
        public Long getPlayerId() { return playerId; }
        public String getReason() { return reason; }
        public String getOperator() { return operator; }
        public LocalDateTime getBanTime() { return banTime; }
        public LocalDateTime getExpireTime() { return expireTime; }
        public BanStatus getStatus() { return status; }
        public void setStatus(BanStatus status) { this.status = status; }
        public String getLiftOperator() { return liftOperator; }
        public void setLiftOperator(String liftOperator) { this.liftOperator = liftOperator; }
        public LocalDateTime getLiftTime() { return liftTime; }
        public void setLiftTime(LocalDateTime liftTime) { this.liftTime = liftTime; }
    }

    public static class PlayerMailRequest {
        private List<Long> recipients;
        private String title;
        private String content;
        private List<MailAttachment> attachments;
        private String sender;

        // Getter和Setter方法
        public List<Long> getRecipients() { return recipients; }
        public void setRecipients(List<Long> recipients) { this.recipients = recipients; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<MailAttachment> getAttachments() { return attachments; }
        public void setAttachments(List<MailAttachment> attachments) { this.attachments = attachments; }
        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }
    }

    public static class MailAttachment {
        private String itemId;
        private Integer quantity;

        public MailAttachment(String itemId, Integer quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        // Getter方法
        public String getItemId() { return itemId; }
        public Integer getQuantity() { return quantity; }
    }

    public static class MailTask {
        private String mailId;
        private List<Long> recipients;
        private String title;
        private String content;
        private List<MailAttachment> attachments;
        private String sender;
        private LocalDateTime createTime;
        private MailStatus status;
        private LocalDateTime sentTime;
        private String errorMessage;

        public MailTask(String mailId, List<Long> recipients, String title, String content,
                       List<MailAttachment> attachments, String sender, LocalDateTime createTime, MailStatus status) {
            this.mailId = mailId;
            this.recipients = recipients;
            this.title = title;
            this.content = content;
            this.attachments = attachments;
            this.sender = sender;
            this.createTime = createTime;
            this.status = status;
        }

        // Getter和Setter方法
        public String getMailId() { return mailId; }
        public List<Long> getRecipients() { return recipients; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public List<MailAttachment> getAttachments() { return attachments; }
        public String getSender() { return sender; }
        public LocalDateTime getCreateTime() { return createTime; }
        public MailStatus getStatus() { return status; }
        public void setStatus(MailStatus status) { this.status = status; }
        public LocalDateTime getSentTime() { return sentTime; }
        public void setSentTime(LocalDateTime sentTime) { this.sentTime = sentTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    // 事件类定义
    public static class PlayerBannedEvent {
        private Long playerId;
        private String reason;
        private Integer duration;
        private String operator;
        private LocalDateTime timestamp;

        public PlayerBannedEvent(Long playerId, String reason, Integer duration, String operator) {
            this.playerId = playerId;
            this.reason = reason;
            this.duration = duration;
            this.operator = operator;
            this.timestamp = LocalDateTime.now();
        }

        // Getter方法
        public Long getPlayerId() { return playerId; }
        public String getReason() { return reason; }
        public Integer getDuration() { return duration; }
        public String getOperator() { return operator; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class PlayerUnbannedEvent {
        private Long playerId;
        private String operator;
        private LocalDateTime timestamp;

        public PlayerUnbannedEvent(Long playerId, String operator) {
            this.playerId = playerId;
            this.operator = operator;
            this.timestamp = LocalDateTime.now();
        }

        // Getter方法
        public Long getPlayerId() { return playerId; }
        public String getOperator() { return operator; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class PlayerDataUpdatedEvent {
        private Long playerId;
        private Map<String, Object> originalData;
        private Map<String, Object> updates;
        private String operator;
        private LocalDateTime timestamp;

        public PlayerDataUpdatedEvent(Long playerId, Map<String, Object> originalData,
                                    Map<String, Object> updates, String operator) {
            this.playerId = playerId;
            this.originalData = originalData;
            this.updates = updates;
            this.operator = operator;
            this.timestamp = LocalDateTime.now();
        }

        // Getter方法
        public Long getPlayerId() { return playerId; }
        public Map<String, Object> getOriginalData() { return originalData; }
        public Map<String, Object> getUpdates() { return updates; }
        public String getOperator() { return operator; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}