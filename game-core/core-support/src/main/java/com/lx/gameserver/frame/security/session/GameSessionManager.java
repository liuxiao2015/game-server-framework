/*
 * 文件名: GameSessionManager.java
 * 用途: 游戏会话管理器
 * 实现内容:
 *   - 分布式会话存储
 *   - 会话生命周期管理
 *   - 并发会话控制
 *   - 会话劫持防护
 *   - 会话状态同步
 * 技术选型:
 *   - Redis分布式会话存储
 *   - Spring Session集成
 *   - 会话安全机制
 * 依赖关系:
 *   - 与Spring Security集成
 *   - 依赖Redis缓存
 */
package com.lx.gameserver.frame.security.session;

import com.lx.gameserver.frame.security.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 游戏会话管理器
 * <p>
 * 负责管理游戏会话的完整生命周期，包括会话创建、验证、更新、销毁等，
 * 支持分布式会话存储、并发控制、安全防护等高级功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class GameSessionManager {
    
    /**
     * 会话存储前缀
     */
    private static final String SESSION_PREFIX = "game:session:";
    
    /**
     * 用户会话映射前缀
     */
    private static final String USER_SESSION_PREFIX = "user:sessions:";
    
    /**
     * 会话心跳前缀
     */
    private static final String SESSION_HEARTBEAT_PREFIX = "session:heartbeat:";
    
    /**
     * 默认会话超时时间（秒）
     */
    private static final int DEFAULT_SESSION_TIMEOUT = 3600; // 1小时
    
    /**
     * 最大并发会话数
     */
    private static final int MAX_CONCURRENT_SESSIONS = 3;
    
    @Autowired
    private SecurityProperties securityProperties;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 本地会话缓存（用于没有Redis的环境）
     */
    private final Map<String, GameSession> localSessions = new ConcurrentHashMap<>();

    /**
     * 创建游戏会话
     *
     * @param userId 用户ID
     * @param deviceInfo 设备信息
     * @return 会话信息
     */
    public GameSession createSession(String userId, Map<String, String> deviceInfo) {
        if (!StringUtils.hasText(userId)) {
            log.warn("创建会话失败：用户ID为空");
            return null;
        }
        
        try {
            // 检查并发会话限制
            if (!checkConcurrentSessionLimit(userId)) {
                log.warn("用户 {} 并发会话数量超限", userId);
                return null;
            }
            
            // 生成会话ID
            String sessionId = generateSessionId();
            
            // 创建会话对象
            GameSession session = GameSession.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .createTime(Instant.now())
                    .lastAccessTime(Instant.now())
                    .deviceInfo(deviceInfo != null ? new HashMap<>(deviceInfo) : new HashMap<>())
                    .status(SessionStatus.ACTIVE)
                    .build();
            
            // 保存会话
            saveSession(session);
            
            // 建立用户会话映射
            addUserSessionMapping(userId, sessionId);
            
            log.info("创建会话成功: sessionId={}, userId={}", sessionId, userId);
            return session;
            
        } catch (Exception e) {
            log.error("创建会话失败: userId=" + userId, e);
            return null;
        }
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    @Nullable
    public GameSession getSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        
        try {
            if (redisTemplate != null) {
                // 从Redis获取会话
                Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(SESSION_PREFIX + sessionId);
                if (sessionData.isEmpty()) {
                    return null;
                }
                
                return mapToSession(sessionData);
            } else {
                // 从本地缓存获取
                return localSessions.get(sessionId);
            }
            
        } catch (Exception e) {
            log.error("获取会话失败: sessionId=" + sessionId, e);
            return null;
        }
    }

    /**
     * 验证会话有效性
     *
     * @param sessionId 会话ID
     * @return 是否有效
     */
    public boolean validateSession(String sessionId) {
        GameSession session = getSession(sessionId);
        if (session == null) {
            return false;
        }
        
        // 检查会话状态
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }
        
        // 检查会话是否过期
        Instant now = Instant.now();
        Duration inactive = Duration.between(session.getLastAccessTime(), now);
        if (inactive.getSeconds() > DEFAULT_SESSION_TIMEOUT) {
            log.debug("会话已过期: sessionId={}, inactive={}s", sessionId, inactive.getSeconds());
            expireSession(sessionId);
            return false;
        }
        
        return true;
    }

    /**
     * 更新会话访问时间
     *
     * @param sessionId 会话ID
     */
    public void touchSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        
        try {
            GameSession session = getSession(sessionId);
            if (session != null) {
                session.setLastAccessTime(Instant.now());
                saveSession(session);
                
                // 更新心跳时间
                updateHeartbeat(sessionId);
            }
            
        } catch (Exception e) {
            log.error("更新会话访问时间失败: sessionId=" + sessionId, e);
        }
    }

    /**
     * 销毁会话
     *
     * @param sessionId 会话ID
     */
    public void destroySession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        
        try {
            GameSession session = getSession(sessionId);
            if (session != null) {
                // 移除用户会话映射
                removeUserSessionMapping(session.getUserId(), sessionId);
                
                // 删除会话数据
                if (redisTemplate != null) {
                    redisTemplate.delete(SESSION_PREFIX + sessionId);
                    redisTemplate.delete(SESSION_HEARTBEAT_PREFIX + sessionId);
                } else {
                    localSessions.remove(sessionId);
                }
                
                log.info("销毁会话: sessionId={}, userId={}", sessionId, session.getUserId());
            }
            
        } catch (Exception e) {
            log.error("销毁会话失败: sessionId=" + sessionId, e);
        }
    }

    /**
     * 获取用户的活跃会话
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<GameSession> getUserActiveSessions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        
        try {
            Set<String> sessionIds = getUserSessionIds(userId);
            List<GameSession> activeSessions = new ArrayList<>();
            
            for (String sessionId : sessionIds) {
                GameSession session = getSession(sessionId);
                if (session != null && validateSession(sessionId)) {
                    activeSessions.add(session);
                }
            }
            
            return activeSessions;
            
        } catch (Exception e) {
            log.error("获取用户活跃会话失败: userId=" + userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 强制下线用户的所有会话
     *
     * @param userId 用户ID
     */
    public void forceLogoutUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        
        try {
            Set<String> sessionIds = getUserSessionIds(userId);
            for (String sessionId : sessionIds) {
                destroySession(sessionId);
            }
            
            log.info("强制下线用户: userId={}, sessionCount={}", userId, sessionIds.size());
            
        } catch (Exception e) {
            log.error("强制下线用户失败: userId=" + userId, e);
        }
    }

    /**
     * 检查会话是否存在劫持风险
     *
     * @param sessionId 会话ID
     * @param currentDeviceInfo 当前设备信息
     * @return 是否存在劫持风险
     */
    public boolean detectSessionHijacking(String sessionId, Map<String, String> currentDeviceInfo) {
        if (!StringUtils.hasText(sessionId) || currentDeviceInfo == null) {
            return false;
        }
        
        try {
            GameSession session = getSession(sessionId);
            if (session == null) {
                return true; // 会话不存在，视为风险
            }
            
            Map<String, String> originalDeviceInfo = session.getDeviceInfo();
            if (originalDeviceInfo == null || originalDeviceInfo.isEmpty()) {
                return false; // 没有原始设备信息，无法检测
            }
            
            // 检查关键设备特征是否发生变化
            String[] criticalKeys = {"userAgent", "screenResolution", "timezone", "ip"};
            for (String key : criticalKeys) {
                String original = originalDeviceInfo.get(key);
                String current = currentDeviceInfo.get(key);
                
                if (StringUtils.hasText(original) && StringUtils.hasText(current) && 
                    !original.equals(current)) {
                    
                    // IP地址变化需要特别检查（可能是正常的网络切换）
                    if ("ip".equals(key)) {
                        if (!isSameIpSegment(original, current)) {
                            log.warn("检测到会话劫持风险 - IP段变化: sessionId={}, originalIp={}, currentIp={}", 
                                    sessionId, original, current);
                            return true;
                        }
                    } else {
                        log.warn("检测到会话劫持风险 - {}变化: sessionId={}, original={}, current={}", 
                                key, sessionId, original, current);
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("检测会话劫持失败: sessionId=" + sessionId, e);
            return true; // 发生异常时保守处理，视为风险
        }
    }

    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        log.debug("开始清理过期会话");
        
        try {
            if (redisTemplate != null) {
                // Redis环境下的过期会话清理
                Set<String> keys = redisTemplate.keys(SESSION_PREFIX + "*");
                if (keys != null) {
                    for (String key : keys) {
                        String sessionId = key.substring(SESSION_PREFIX.length());
                        if (!validateSession(sessionId)) {
                            destroySession(sessionId);
                        }
                    }
                }
            } else {
                // 本地缓存环境下的过期会话清理
                Iterator<Map.Entry<String, GameSession>> iterator = localSessions.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, GameSession> entry = iterator.next();
                    if (!validateSession(entry.getKey())) {
                        iterator.remove();
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("清理过期会话失败", e);
        }
    }

    /**
     * 检查并发会话限制
     *
     * @param userId 用户ID
     * @return 是否可以创建新会话
     */
    private boolean checkConcurrentSessionLimit(String userId) {
        List<GameSession> activeSessions = getUserActiveSessions(userId);
        if (activeSessions.size() >= MAX_CONCURRENT_SESSIONS) {
            // 清理最旧的会话
            activeSessions.sort(Comparator.comparing(GameSession::getLastAccessTime));
            destroySession(activeSessions.get(0).getSessionId());
        }
        return true;
    }

    /**
     * 生成会话ID
     *
     * @return 会话ID
     */
    private String generateSessionId() {
        return "GSESSION_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 保存会话
     *
     * @param session 会话对象
     */
    private void saveSession(GameSession session) {
        if (redisTemplate != null) {
            Map<String, Object> sessionData = sessionToMap(session);
            String sessionKey = SESSION_PREFIX + session.getSessionId();
            
            redisTemplate.opsForHash().putAll(sessionKey, sessionData);
            redisTemplate.expire(sessionKey, Duration.ofSeconds(DEFAULT_SESSION_TIMEOUT + 300)); // 加5分钟缓冲
        } else {
            localSessions.put(session.getSessionId(), session);
        }
    }

    /**
     * 添加用户会话映射
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    private void addUserSessionMapping(String userId, String sessionId) {
        if (redisTemplate != null) {
            String userSessionKey = USER_SESSION_PREFIX + userId;
            redisTemplate.opsForSet().add(userSessionKey, sessionId);
            redisTemplate.expire(userSessionKey, Duration.ofSeconds(DEFAULT_SESSION_TIMEOUT + 300));
        }
    }

    /**
     * 移除用户会话映射
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    private void removeUserSessionMapping(String userId, String sessionId) {
        if (redisTemplate != null) {
            String userSessionKey = USER_SESSION_PREFIX + userId;
            redisTemplate.opsForSet().remove(userSessionKey, sessionId);
        }
    }

    /**
     * 获取用户会话ID列表
     *
     * @param userId 用户ID
     * @return 会话ID集合
     */
    private Set<String> getUserSessionIds(String userId) {
        if (redisTemplate != null) {
            String userSessionKey = USER_SESSION_PREFIX + userId;
            Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionKey);
            if (sessionIds != null) {
                Set<String> result = new HashSet<>();
                for (Object sessionId : sessionIds) {
                    if (sessionId instanceof String) {
                        result.add((String) sessionId);
                    }
                }
                return result;
            }
        }
        return Collections.emptySet();
    }

    /**
     * 更新心跳时间
     *
     * @param sessionId 会话ID
     */
    private void updateHeartbeat(String sessionId) {
        if (redisTemplate != null) {
            String heartbeatKey = SESSION_HEARTBEAT_PREFIX + sessionId;
            redisTemplate.opsForValue().set(heartbeatKey, Instant.now().toString(), 
                    Duration.ofSeconds(DEFAULT_SESSION_TIMEOUT));
        }
    }

    /**
     * 使会话过期
     *
     * @param sessionId 会话ID
     */
    private void expireSession(String sessionId) {
        GameSession session = getSession(sessionId);
        if (session != null) {
            session.setStatus(SessionStatus.EXPIRED);
            saveSession(session);
        }
    }

    /**
     * 检查是否为相同IP段
     *
     * @param ip1 IP地址1
     * @param ip2 IP地址2
     * @return 是否为相同IP段
     */
    private boolean isSameIpSegment(String ip1, String ip2) {
        if (!StringUtils.hasText(ip1) || !StringUtils.hasText(ip2)) {
            return false;
        }
        
        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");
        
        if (parts1.length >= 3 && parts2.length >= 3) {
            return parts1[0].equals(parts2[0]) && parts1[1].equals(parts2[1]) && parts1[2].equals(parts2[2]);
        }
        
        return false;
    }

    /**
     * 会话对象转Map
     *
     * @param session 会话对象
     * @return Map表示
     */
    private Map<String, Object> sessionToMap(GameSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", session.getSessionId());
        map.put("userId", session.getUserId());
        map.put("createTime", session.getCreateTime().toString());
        map.put("lastAccessTime", session.getLastAccessTime().toString());
        map.put("status", session.getStatus().name());
        map.put("deviceInfo", session.getDeviceInfo());
        return map;
    }

    /**
     * Map转会话对象
     *
     * @param map Map表示
     * @return 会话对象
     */
    private GameSession mapToSession(Map<Object, Object> map) {
        return GameSession.builder()
                .sessionId((String) map.get("sessionId"))
                .userId((String) map.get("userId"))
                .createTime(Instant.parse((String) map.get("createTime")))
                .lastAccessTime(Instant.parse((String) map.get("lastAccessTime")))
                .status(SessionStatus.valueOf((String) map.get("status")))
                .deviceInfo((Map<String, String>) map.get("deviceInfo"))
                .build();
    }

    /**
     * 游戏会话实体类
     */
    @lombok.Builder
    @lombok.Data
    public static class GameSession {
        /**
         * 会话ID
         */
        private String sessionId;
        
        /**
         * 用户ID
         */
        private String userId;
        
        /**
         * 创建时间
         */
        private Instant createTime;
        
        /**
         * 最后访问时间
         */
        private Instant lastAccessTime;
        
        /**
         * 会话状态
         */
        private SessionStatus status;
        
        /**
         * 设备信息
         */
        private Map<String, String> deviceInfo;
    }

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        /**
         * 活跃状态
         */
        ACTIVE,
        
        /**
         * 已过期
         */
        EXPIRED,
        
        /**
         * 已注销
         */
        LOGGED_OUT,
        
        /**
         * 被强制下线
         */
        FORCED_LOGOUT,
        
        /**
         * 疑似劫持
         */
        SUSPECTED_HIJACKED
    }
}