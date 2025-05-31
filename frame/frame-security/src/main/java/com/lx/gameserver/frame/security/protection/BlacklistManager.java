/*
 * 文件名: BlacklistManager.java
 * 用途: 黑名单管理
 * 实现内容:
 *   - IP黑名单
 *   - 设备黑名单
 *   - 账号黑名单
 *   - 自动封禁机制
 *   - 黑名单同步
 * 技术选型:
 *   - 本地缓存与Redis存储
 *   - CIDR IP地址范围匹配
 *   - 定时清理与加载
 * 依赖关系:
 *   - 被安全过滤器使用
 *   - 被风险控制模块使用
 */
package com.lx.gameserver.frame.security.protection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 黑名单管理
 * <p>
 * 提供IP、设备和账号黑名单管理功能，支持手动和自动封禁，
 * 以及黑名单条目的定时清理和定期持久化。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class BlacklistManager {
    
    /**
     * IP黑名单
     * Key: IP地址
     * Value: 过期时间（null表示永久）
     */
    private final Map<String, Instant> ipBlacklist = new ConcurrentHashMap<>();
    
    /**
     * IP地址段黑名单（CIDR格式）
     */
    private final List<CidrRange> ipRangeBlacklist = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 设备黑名单
     * Key: 设备ID
     * Value: 过期时间（null表示永久）
     */
    private final Map<String, Instant> deviceBlacklist = new ConcurrentHashMap<>();
    
    /**
     * 账号黑名单
     * Key: 用户名或ID
     * Value: 过期时间（null表示永久）
     */
    private final Map<String, Instant> accountBlacklist = new ConcurrentHashMap<>();
    
    /**
     * Redis客户端（可选，用于分布式同步）
     */
    @Nullable
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Redis键前缀
     */
    private static final String BLACKLIST_IP_KEY = "blacklist:ip";
    private static final String BLACKLIST_IP_RANGE_KEY = "blacklist:ip:range";
    private static final String BLACKLIST_DEVICE_KEY = "blacklist:device";
    private static final String BLACKLIST_ACCOUNT_KEY = "blacklist:account";
    
    /**
     * 构造函数
     *
     * @param redisTemplate Redis客户端
     */
    public BlacklistManager(@Nullable RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("黑名单管理器初始化完成");
    }
    
    /**
     * 初始化，加载黑名单数据
     */
    @PostConstruct
    public void init() {
        try {
            loadBlacklists();
            log.info("黑名单数据加载完成");
        } catch (Exception e) {
            log.error("加载黑名单数据失败", e);
        }
    }
    
    /**
     * 加载黑名单数据
     */
    private void loadBlacklists() {
        if (redisTemplate == null) {
            log.warn("Redis不可用，无法加载黑名单数据");
            return;
        }
        
        try {
            // 加载IP黑名单
            Map<Object, Object> ipEntries = redisTemplate.opsForHash().entries(BLACKLIST_IP_KEY);
            for (Map.Entry<Object, Object> entry : ipEntries.entrySet()) {
                String ip = entry.getKey().toString();
                Object value = entry.getValue();
                if (value instanceof Long) {
                    long timestamp = (Long) value;
                    if (timestamp > 0) {
                        ipBlacklist.put(ip, Instant.ofEpochMilli(timestamp));
                    } else {
                        ipBlacklist.put(ip, null); // 永久
                    }
                }
            }
            
            // 加载IP段黑名单
            List<Object> ipRanges = redisTemplate.opsForList().range(BLACKLIST_IP_RANGE_KEY, 0, -1);
            if (ipRanges != null) {
                for (Object range : ipRanges) {
                    if (range instanceof String) {
                        try {
                            ipRangeBlacklist.add(new CidrRange((String) range));
                        } catch (Exception e) {
                            log.warn("无效的CIDR格式: {}", range, e);
                        }
                    }
                }
            }
            
            // 加载设备黑名单
            Map<Object, Object> deviceEntries = redisTemplate.opsForHash().entries(BLACKLIST_DEVICE_KEY);
            for (Map.Entry<Object, Object> entry : deviceEntries.entrySet()) {
                String deviceId = entry.getKey().toString();
                Object value = entry.getValue();
                if (value instanceof Long) {
                    long timestamp = (Long) value;
                    if (timestamp > 0) {
                        deviceBlacklist.put(deviceId, Instant.ofEpochMilli(timestamp));
                    } else {
                        deviceBlacklist.put(deviceId, null); // 永久
                    }
                }
            }
            
            // 加载账号黑名单
            Map<Object, Object> accountEntries = redisTemplate.opsForHash().entries(BLACKLIST_ACCOUNT_KEY);
            for (Map.Entry<Object, Object> entry : accountEntries.entrySet()) {
                String account = entry.getKey().toString();
                Object value = entry.getValue();
                if (value instanceof Long) {
                    long timestamp = (Long) value;
                    if (timestamp > 0) {
                        accountBlacklist.put(account, Instant.ofEpochMilli(timestamp));
                    } else {
                        accountBlacklist.put(account, null); // 永久
                    }
                }
            }
            
            log.info("已加载黑名单数据: IP={}, IP段={}, 设备={}, 账号={}",
                    ipBlacklist.size(), ipRangeBlacklist.size(), deviceBlacklist.size(), accountBlacklist.size());
            
        } catch (Exception e) {
            log.error("从Redis加载黑名单失败", e);
        }
    }
    
    /**
     * 保存黑名单数据到Redis
     */
    private void saveBlacklists() {
        if (redisTemplate == null) {
            log.warn("Redis不可用，无法保存黑名单数据");
            return;
        }
        
        try {
            // 保存IP黑名单
            Map<String, Object> ipEntries = new HashMap<>();
            ipBlacklist.forEach((ip, expiry) -> 
                    ipEntries.put(ip, expiry != null ? expiry.toEpochMilli() : 0L));
            redisTemplate.opsForHash().putAll(BLACKLIST_IP_KEY, ipEntries);
            
            // 保存IP段黑名单
            redisTemplate.delete(BLACKLIST_IP_RANGE_KEY);
            if (!ipRangeBlacklist.isEmpty()) {
                List<String> ranges = new ArrayList<>();
                for (CidrRange range : ipRangeBlacklist) {
                    ranges.add(range.toString());
                }
                redisTemplate.opsForList().rightPushAll(BLACKLIST_IP_RANGE_KEY, ranges.toArray());
            }
            
            // 保存设备黑名单
            Map<String, Object> deviceEntries = new HashMap<>();
            deviceBlacklist.forEach((deviceId, expiry) -> 
                    deviceEntries.put(deviceId, expiry != null ? expiry.toEpochMilli() : 0L));
            redisTemplate.opsForHash().putAll(BLACKLIST_DEVICE_KEY, deviceEntries);
            
            // 保存账号黑名单
            Map<String, Object> accountEntries = new HashMap<>();
            accountBlacklist.forEach((account, expiry) -> 
                    accountEntries.put(account, expiry != null ? expiry.toEpochMilli() : 0L));
            redisTemplate.opsForHash().putAll(BLACKLIST_ACCOUNT_KEY, accountEntries);
            
            log.debug("黑名单数据已保存到Redis");
            
        } catch (Exception e) {
            log.error("保存黑名单数据到Redis失败", e);
        }
    }
    
    /**
     * 添加IP到黑名单
     *
     * @param ip IP地址
     * @param duration 封禁时长，null表示永久
     * @return 是否添加成功
     */
    public boolean addToIpBlacklist(String ip, @Nullable Duration duration) {
        try {
            Instant expiry = duration != null ? Instant.now().plus(duration) : null;
            ipBlacklist.put(ip, expiry);
            
            log.info("IP已添加到黑名单: {}, 过期时间: {}", 
                    ip, expiry != null ? expiry : "永久");
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForHash().put(
                        BLACKLIST_IP_KEY, ip, expiry != null ? expiry.toEpochMilli() : 0L);
            }
            
            return true;
        } catch (Exception e) {
            log.error("添加IP到黑名单失败: {}", ip, e);
            return false;
        }
    }
    
    /**
     * 添加IP段到黑名单
     *
     * @param cidr CIDR格式的IP段（如192.168.1.0/24）
     * @return 是否添加成功
     */
    public boolean addToIpRangeBlacklist(String cidr) {
        try {
            CidrRange range = new CidrRange(cidr);
            ipRangeBlacklist.add(range);
            
            log.info("IP段已添加到黑名单: {}", cidr);
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForList().rightPush(BLACKLIST_IP_RANGE_KEY, cidr);
            }
            
            return true;
        } catch (Exception e) {
            log.error("添加IP段到黑名单失败: {}", cidr, e);
            return false;
        }
    }
    
    /**
     * 添加设备到黑名单
     *
     * @param deviceId 设备ID
     * @param duration 封禁时长，null表示永久
     * @return 是否添加成功
     */
    public boolean addToDeviceBlacklist(String deviceId, @Nullable Duration duration) {
        try {
            Instant expiry = duration != null ? Instant.now().plus(duration) : null;
            deviceBlacklist.put(deviceId, expiry);
            
            log.info("设备已添加到黑名单: {}, 过期时间: {}", 
                    deviceId, expiry != null ? expiry : "永久");
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForHash().put(
                        BLACKLIST_DEVICE_KEY, deviceId, expiry != null ? expiry.toEpochMilli() : 0L);
            }
            
            return true;
        } catch (Exception e) {
            log.error("添加设备到黑名单失败: {}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 添加账号到黑名单
     *
     * @param account 账号（用户名或ID）
     * @param duration 封禁时长，null表示永久
     * @return 是否添加成功
     */
    public boolean addToAccountBlacklist(String account, @Nullable Duration duration) {
        try {
            Instant expiry = duration != null ? Instant.now().plus(duration) : null;
            accountBlacklist.put(account, expiry);
            
            log.info("账号已添加到黑名单: {}, 过期时间: {}", 
                    account, expiry != null ? expiry : "永久");
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForHash().put(
                        BLACKLIST_ACCOUNT_KEY, account, expiry != null ? expiry.toEpochMilli() : 0L);
            }
            
            return true;
        } catch (Exception e) {
            log.error("添加账号到黑名单失败: {}", account, e);
            return false;
        }
    }
    
    /**
     * 从IP黑名单移除
     *
     * @param ip IP地址
     * @return 是否移除成功
     */
    public boolean removeFromIpBlacklist(String ip) {
        try {
            ipBlacklist.remove(ip);
            
            log.info("IP已从黑名单移除: {}", ip);
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForHash().delete(BLACKLIST_IP_KEY, ip);
            }
            
            return true;
        } catch (Exception e) {
            log.error("从IP黑名单移除失败: {}", ip, e);
            return false;
        }
    }
    
    /**
     * 从IP段黑名单移除
     *
     * @param cidr CIDR格式的IP段
     * @return 是否移除成功
     */
    public boolean removeFromIpRangeBlacklist(String cidr) {
        try {
            ipRangeBlacklist.removeIf(range -> range.toString().equals(cidr));
            
            log.info("IP段已从黑名单移除: {}", cidr);
            
            // 由于列表操作较复杂，直接重新保存整个列表
            if (redisTemplate != null) {
                saveBlacklists();
            }
            
            return true;
        } catch (Exception e) {
            log.error("从IP段黑名单移除失败: {}", cidr, e);
            return false;
        }
    }
    
    /**
     * 从设备黑名单移除
     *
     * @param deviceId 设备ID
     * @return 是否移除成功
     */
    public boolean removeFromDeviceBlacklist(String deviceId) {
        try {
            deviceBlacklist.remove(deviceId);
            
            log.info("设备已从黑名单移除: {}", deviceId);
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForHash().delete(BLACKLIST_DEVICE_KEY, deviceId);
            }
            
            return true;
        } catch (Exception e) {
            log.error("从设备黑名单移除失败: {}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 从账号黑名单移除
     *
     * @param account 账号
     * @return 是否移除成功
     */
    public boolean removeFromAccountBlacklist(String account) {
        try {
            accountBlacklist.remove(account);
            
            log.info("账号已从黑名单移除: {}", account);
            
            // 同步到Redis
            if (redisTemplate != null) {
                redisTemplate.opsForHash().delete(BLACKLIST_ACCOUNT_KEY, account);
            }
            
            return true;
        } catch (Exception e) {
            log.error("从账号黑名单移除失败: {}", account, e);
            return false;
        }
    }
    
    /**
     * 检查IP是否在黑名单中
     *
     * @param ip IP地址
     * @return 如果在黑名单中返回true，否则返回false
     */
    public boolean isIpBlacklisted(String ip) {
        try {
            // 检查IP地址
            Instant expiry = ipBlacklist.get(ip);
            if (expiry != null) {
                // 检查是否过期
                if (expiry.isBefore(Instant.now())) {
                    // 已过期，从黑名单移除
                    removeFromIpBlacklist(ip);
                    return false;
                }
                return true;
            }
            
            // 检查IP段
            InetAddress ipAddress = InetAddress.getByName(ip);
            for (CidrRange range : ipRangeBlacklist) {
                if (range.contains(ipAddress)) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("检查IP黑名单状态失败: {}", ip, e);
            return false;
        }
    }
    
    /**
     * 检查设备是否在黑名单中
     *
     * @param deviceId 设备ID
     * @return 如果在黑名单中返回true，否则返回false
     */
    public boolean isDeviceBlacklisted(String deviceId) {
        try {
            Instant expiry = deviceBlacklist.get(deviceId);
            if (expiry != null) {
                // 检查是否过期
                if (expiry.isBefore(Instant.now())) {
                    // 已过期，从黑名单移除
                    removeFromDeviceBlacklist(deviceId);
                    return false;
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("检查设备黑名单状态失败: {}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 检查账号是否在黑名单中
     *
     * @param account 账号
     * @return 如果在黑名单中返回true，否则返回false
     */
    public boolean isAccountBlacklisted(String account) {
        try {
            Instant expiry = accountBlacklist.get(account);
            if (expiry != null) {
                // 检查是否过期
                if (expiry.isBefore(Instant.now())) {
                    // 已过期，从黑名单移除
                    removeFromAccountBlacklist(account);
                    return false;
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("检查账号黑名单状态失败: {}", account, e);
            return false;
        }
    }
    
    /**
     * 定期清理过期的黑名单条目
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 每小时执行一次
    public void cleanupExpiredEntries() {
        try {
            log.info("开始清理过期的黑名单条目");
            Instant now = Instant.now();
            int ipCount = 0, deviceCount = 0, accountCount = 0;
            
            // 清理过期的IP黑名单
            for (Iterator<Map.Entry<String, Instant>> it = ipBlacklist.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Instant> entry = it.next();
                if (entry.getValue() != null && entry.getValue().isBefore(now)) {
                    it.remove();
                    ipCount++;
                }
            }
            
            // 清理过期的设备黑名单
            for (Iterator<Map.Entry<String, Instant>> it = deviceBlacklist.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Instant> entry = it.next();
                if (entry.getValue() != null && entry.getValue().isBefore(now)) {
                    it.remove();
                    deviceCount++;
                }
            }
            
            // 清理过期的账号黑名单
            for (Iterator<Map.Entry<String, Instant>> it = accountBlacklist.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Instant> entry = it.next();
                if (entry.getValue() != null && entry.getValue().isBefore(now)) {
                    it.remove();
                    accountCount++;
                }
            }
            
            log.info("已清理过期黑名单条目: IP={}, 设备={}, 账号={}", ipCount, deviceCount, accountCount);
            
            // 保存更新后的黑名单
            if (ipCount > 0 || deviceCount > 0 || accountCount > 0) {
                saveBlacklists();
            }
            
        } catch (Exception e) {
            log.error("清理过期黑名单条目失败", e);
        }
    }
    
    /**
     * 定期同步黑名单数据
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 每5分钟执行一次
    public void syncBlacklists() {
        try {
            if (redisTemplate != null) {
                saveBlacklists();
                log.debug("黑名单数据同步完成");
            }
        } catch (Exception e) {
            log.error("同步黑名单数据失败", e);
        }
    }
    
    /**
     * CIDR范围工具类
     */
    private static class CidrRange {
        private final InetAddress networkAddress;
        private final InetAddress subnetMask;
        private final int prefixLength;
        private final String cidrNotation;
        
        /**
         * 构造函数
         *
         * @param cidr CIDR表示法（如192.168.1.0/24）
         */
        public CidrRange(String cidr) throws Exception {
            this.cidrNotation = cidr;
            
            // 解析CIDR
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("无效的CIDR格式: " + cidr);
            }
            
            // 网络地址
            networkAddress = InetAddress.getByName(parts[0]);
            
            // 前缀长度
            prefixLength = Integer.parseInt(parts[1]);
            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException("无效的前缀长度: " + prefixLength);
            }
            
            // 计算子网掩码
            int mask = 0xffffffff << (32 - prefixLength);
            byte[] subnetMaskBytes = new byte[4];
            subnetMaskBytes[0] = (byte) (mask >>> 24);
            subnetMaskBytes[1] = (byte) (mask >>> 16);
            subnetMaskBytes[2] = (byte) (mask >>> 8);
            subnetMaskBytes[3] = (byte) mask;
            subnetMask = InetAddress.getByAddress(subnetMaskBytes);
        }
        
        /**
         * 检查IP地址是否在此范围内
         *
         * @param ip IP地址
         * @return 如果在范围内返回true，否则返回false
         */
        public boolean contains(InetAddress ip) {
            if (ip == null) {
                return false;
            }
            
            byte[] ipBytes = ip.getAddress();
            byte[] networkBytes = networkAddress.getAddress();
            byte[] maskBytes = subnetMask.getAddress();
            
            if (ipBytes.length != networkBytes.length) {
                return false;
            }
            
            for (int i = 0; i < ipBytes.length; i++) {
                if ((ipBytes[i] & maskBytes[i]) != (networkBytes[i] & maskBytes[i])) {
                    return false;
                }
            }
            
            return true;
        }
        
        @Override
        public String toString() {
            return cidrNotation;
        }
    }
}