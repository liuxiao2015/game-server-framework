/*
 * 文件名: DDoSProtection.java
 * 用途: DDoS攻击防护
 * 实现内容:
 *   - 流量清洗
 *   - CC攻击防护
 *   - SYN Flood防护
 *   - UDP Flood防护
 *   - 智能限流
 * 技术选型:
 *   - 滑动窗口统计
 *   - 基于IP的计数器
 *   - 机器学习异常检测
 * 依赖关系:
 *   - 被网络层过滤器使用
 *   - 使用BlacklistManager自动封禁
 */
package com.lx.gameserver.frame.security.protection;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DDoS攻击防护
 * <p>
 * 提供对分布式拒绝服务攻击的防护机制，包括流量清洗、
 * CC攻击防护、SYN/UDP Flood防护和智能限流等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class DDoSProtection {

    /**
     * 流量统计时间窗口（秒）
     */
    private static final int TRAFFIC_WINDOW = 10;
    
    /**
     * IP访问统计（滑动窗口）
     * Key: IP地址
     * Value: 时间窗口内的访问计数
     */
    private final Map<String, AccessStats> ipAccessStats = new ConcurrentHashMap<>();
    
    /**
     * 路径访问统计（滑动窗口）
     * Key: 路径
     * Value: 时间窗口内的访问计数
     */
    private final Map<String, AccessStats> pathAccessStats = new ConcurrentHashMap<>();
    
    /**
     * 可疑IP列表
     * Key: IP地址
     * Value: 怀疑程度（0-100）
     */
    private final Map<String, Integer> suspiciousIps = new ConcurrentHashMap<>();
    
    /**
     * 异常请求特征
     * Key: 特征标识（如User-Agent）
     * Value: 出现次数
     */
    private final Map<String, AtomicInteger> anomalyFeatures = new ConcurrentHashMap<>();
    
    /**
     * 黑名单管理器
     */
    private final BlacklistManager blacklistManager;
    
    /**
     * 限流控制器
     */
    private final RateLimiter rateLimiter;
    
    /**
     * 是否启用自动封禁
     */
    private volatile boolean autoBanEnabled = true;
    
    /**
     * 自动封禁阈值（请求/秒）
     */
    private volatile int autoBanThreshold = 1000;
    
    /**
     * 自动封禁时长
     */
    private volatile Duration autoBanDuration = Duration.ofHours(1);
    
    /**
     * 构造函数
     *
     * @param blacklistManager 黑名单管理器
     * @param rateLimiter 限流控制器
     */
    @Autowired
    public DDoSProtection(BlacklistManager blacklistManager, RateLimiter rateLimiter) {
        this.blacklistManager = blacklistManager;
        this.rateLimiter = rateLimiter;
        
        // 启动清理线程
        startCleanupTask();
        
        log.info("DDoS防护系统初始化完成");
    }
    
    /**
     * 启动定期清理任务
     */
    private void startCleanupTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DDoSProtectionCleaner");
            thread.setDaemon(true);
            return thread;
        });
        
        // 每分钟执行一次清理
        scheduler.scheduleAtFixedRate(this::cleanupStats, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 清理过时的统计数据
     */
    private void cleanupStats() {
        try {
            Instant now = Instant.now();
            
            // 清理过期的IP统计
            ipAccessStats.entrySet().removeIf(entry -> 
                    entry.getValue().getLastAccessTime().plusSeconds(TRAFFIC_WINDOW * 2).isBefore(now));
            
            // 清理过期的路径统计
            pathAccessStats.entrySet().removeIf(entry -> 
                    entry.getValue().getLastAccessTime().plusSeconds(TRAFFIC_WINDOW * 2).isBefore(now));
            
            // 减少可疑IP的怀疑程度
            suspiciousIps.entrySet().forEach(entry -> 
                    entry.setValue(Math.max(0, entry.getValue() - 5)));
            suspiciousIps.entrySet().removeIf(entry -> entry.getValue() <= 0);
            
            // 清理异常特征计数
            anomalyFeatures.entrySet().removeIf(entry -> entry.getValue().get() <= 5);
            
            log.debug("清理完成：IP统计={}, 路径统计={}, 可疑IP={}, 异常特征={}",
                    ipAccessStats.size(), pathAccessStats.size(), suspiciousIps.size(), anomalyFeatures.size());
            
        } catch (Exception e) {
            log.error("清理统计数据失败", e);
        }
    }
    
    /**
     * 处理HTTP请求
     *
     * @param ip 客户端IP
     * @param path 请求路径
     * @param userAgent 用户代理
     * @param contentLength 内容长度
     * @return 如果请求应被拒绝返回true，否则返回false
     */
    public boolean handleHttpRequest(String ip, String path, String userAgent, int contentLength) {
        try {
            // 检查是否在黑名单
            if (blacklistManager.isIpBlacklisted(ip)) {
                log.debug("拒绝来自黑名单IP的请求: {}", ip);
                return true;
            }
            
            // 记录IP访问统计
            AccessStats ipStats = ipAccessStats.computeIfAbsent(ip, k -> new AccessStats());
            ipStats.recordAccess();
            
            // 记录路径访问统计
            AccessStats pathStats = pathAccessStats.computeIfAbsent(path, k -> new AccessStats());
            pathStats.recordAccess();
            
            // CC攻击检测 - 检查IP访问频率
            if (ipStats.getRequestsPerSecond() > autoBanThreshold) {
                handleSuspiciousActivity(ip, "CC攻击", (long) ipStats.getRequestsPerSecond());
                return true;
            }
            
            // 检查可疑的User-Agent
            if (isSuspiciousUserAgent(userAgent)) {
                increaseSuspicionLevel(ip, 10);
                recordAnomalyFeature("suspicious-ua:" + userAgent.hashCode());
                
                // 可疑程度超过阈值时拒绝请求
                if (getSuspicionLevel(ip) > 80) {
                    handleSuspiciousActivity(ip, "可疑User-Agent", 0);
                    return true;
                }
            }
            
            // 检查内容长度异常（可能的Slowloris攻击）
            if (contentLength > 10 * 1024 * 1024) { // 10MB
                increaseSuspicionLevel(ip, 20);
                recordAnomalyFeature("large-content:" + (contentLength / (1024 * 1024)) + "MB");
                
                if (getSuspicionLevel(ip) > 60) {
                    handleSuspiciousActivity(ip, "异常大内容", contentLength);
                    return true;
                }
            }
            
            // 应用限流
            if (!rateLimiter.tryAcquire("http:" + path, ip, determineRateLimit(ip, path))) {
                log.debug("请求限流: IP={}, 路径={}", ip, path);
                increaseSuspicionLevel(ip, 5);
                return true;
            }
            
            // 默认通过
            return false;
            
        } catch (Exception e) {
            log.error("处理HTTP请求时出错", e);
            return false; // 出错时默认放行
        }
    }
    
    /**
     * 处理TCP连接
     *
     * @param ip 客户端IP
     * @param port 目标端口
     * @return 如果连接应被拒绝返回true，否则返回false
     */
    public boolean handleTcpConnection(String ip, int port) {
        try {
            // 检查是否在黑名单
            if (blacklistManager.isIpBlacklisted(ip)) {
                log.debug("拒绝来自黑名单IP的TCP连接: {}", ip);
                return true;
            }
            
            // 记录IP访问统计
            AccessStats ipStats = ipAccessStats.computeIfAbsent(ip, k -> new AccessStats());
            ipStats.recordAccess();
            
            // SYN Flood检测 - 检查连接频率
            if (ipStats.getRequestsPerSecond() > autoBanThreshold / 2) {
                handleSuspiciousActivity(ip, "SYN Flood", (long) ipStats.getRequestsPerSecond());
                return true;
            }
            
            // 应用限流
            if (!rateLimiter.tryAcquire("tcp:" + port, ip, 50)) {
                log.debug("TCP连接限流: IP={}, 端口={}", ip, port);
                increaseSuspicionLevel(ip, 5);
                return true;
            }
            
            // 默认通过
            return false;
            
        } catch (Exception e) {
            log.error("处理TCP连接时出错", e);
            return false; // 出错时默认放行
        }
    }
    
    /**
     * 处理UDP数据包
     *
     * @param ip 客户端IP
     * @param port 目标端口
     * @param packetSize 数据包大小
     * @return 如果数据包应被丢弃返回true，否则返回false
     */
    public boolean handleUdpPacket(String ip, int port, int packetSize) {
        try {
            // 检查是否在黑名单
            if (blacklistManager.isIpBlacklisted(ip)) {
                log.debug("丢弃来自黑名单IP的UDP包: {}", ip);
                return true;
            }
            
            // 记录IP访问统计
            AccessStats ipStats = ipAccessStats.computeIfAbsent(ip, k -> new AccessStats());
            ipStats.recordAccess();
            
            // UDP Flood检测
            if (ipStats.getRequestsPerSecond() > autoBanThreshold * 2) {
                handleSuspiciousActivity(ip, "UDP Flood", (long) ipStats.getRequestsPerSecond());
                return true;
            }
            
            // 检查数据包大小异常（可能的放大攻击）
            if (packetSize > 1500) { // 大于典型MTU
                increaseSuspicionLevel(ip, 15);
                recordAnomalyFeature("large-udp:" + packetSize + "B");
                
                if (getSuspicionLevel(ip) > 70) {
                    handleSuspiciousActivity(ip, "UDP放大攻击", packetSize);
                    return true;
                }
            }
            
            // 应用限流
            if (!rateLimiter.tryAcquire("udp:" + port, ip, 100)) {
                log.debug("UDP数据包限流: IP={}, 端口={}", ip, port);
                increaseSuspicionLevel(ip, 5);
                return true;
            }
            
            // 默认通过
            return false;
            
        } catch (Exception e) {
            log.error("处理UDP数据包时出错", e);
            return false; // 出错时默认放行
        }
    }
    
    /**
     * 处理可疑活动
     *
     * @param ip IP地址
     * @param reason 原因
     * @param metric 相关指标
     */
    private void handleSuspiciousActivity(String ip, String reason, long metric) {
        log.warn("检测到可疑活动: IP={}, 原因={}, 指标={}", ip, reason, metric);
        
        // 增加可疑程度
        increaseSuspicionLevel(ip, 25);
        
        // 记录异常特征
        recordAnomalyFeature(reason + ":" + ip);
        
        // 自动封禁
        if (autoBanEnabled && getSuspicionLevel(ip) >= 100) {
            blacklistManager.addToIpBlacklist(ip, autoBanDuration);
            log.info("已自动封禁IP: {}, 原因: {}, 时长: {}", ip, reason, autoBanDuration);
        }
    }
    
    /**
     * 增加IP的可疑程度
     *
     * @param ip IP地址
     * @param amount 增加数量
     */
    private void increaseSuspicionLevel(String ip, int amount) {
        suspiciousIps.compute(ip, (k, v) -> (v == null ? 0 : v) + amount);
    }
    
    /**
     * 获取IP的可疑程度
     *
     * @param ip IP地址
     * @return 可疑程度（0-100）
     */
    private int getSuspicionLevel(String ip) {
        return suspiciousIps.getOrDefault(ip, 0);
    }
    
    /**
     * 记录异常特征
     *
     * @param feature 特征标识
     */
    private void recordAnomalyFeature(String feature) {
        anomalyFeatures.computeIfAbsent(feature, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 检查是否可疑的User-Agent
     *
     * @param userAgent User-Agent字符串
     * @return 如果可疑返回true，否则返回false
     */
    private boolean isSuspiciousUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return true;
        }
        
        // 检查常见的恶意爬虫、攻击工具标识
        String lowerUserAgent = userAgent.toLowerCase();
        return lowerUserAgent.contains("nmap") ||
               lowerUserAgent.contains("sqlmap") ||
               lowerUserAgent.contains("nikto") ||
               lowerUserAgent.contains("burpsuite") ||
               lowerUserAgent.contains("masscan") ||
               userAgent.length() < 10 || // 过短的UA
               userAgent.matches(".*\\d{10,}.*"); // 包含过长数字序列
    }
    
    /**
     * 确定针对特定IP和路径的限流速率
     *
     * @param ip IP地址
     * @param path 请求路径
     * @return 每秒允许的请求数
     */
    private int determineRateLimit(String ip, String path) {
        // 基础限流速率
        int baseRate = 100;
        
        // 根据IP可疑程度调整限流速率
        int suspicionLevel = getSuspicionLevel(ip);
        if (suspicionLevel > 80) {
            return 5;
        } else if (suspicionLevel > 50) {
            return 20;
        }
        
        // 根据路径调整限流速率
        if (path.startsWith("/api/auth") || path.contains("login")) {
            return 10; // 登录API限制更严格
        } else if (path.contains("admin") || path.contains("management")) {
            return 20; // 管理接口限制较严格
        } else if (path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".js") || path.endsWith(".css")) {
            return 500; // 静态资源限制较宽松
        }
        
        return baseRate;
    }
    
    /**
     * 设置自动封禁是否启用
     *
     * @param enabled 是否启用
     */
    public void setAutoBanEnabled(boolean enabled) {
        this.autoBanEnabled = enabled;
        log.info("自动封禁功能: {}", enabled ? "已启用" : "已禁用");
    }
    
    /**
     * 设置自动封禁阈值
     *
     * @param threshold 每秒请求阈值
     */
    public void setAutoBanThreshold(int threshold) {
        this.autoBanThreshold = threshold;
        log.info("自动封禁阈值已设置为: {} 请求/秒", threshold);
    }
    
    /**
     * 设置自动封禁时长
     *
     * @param duration 封禁时长
     */
    public void setAutoBanDuration(Duration duration) {
        this.autoBanDuration = duration;
        log.info("自动封禁时长已设置为: {}", duration);
    }
    
    /**
     * 获取IP访问统计
     *
     * @param ip IP地址
     * @return 访问统计，如果不存在则返回null
     */
    public AccessStats getIpStats(String ip) {
        return ipAccessStats.get(ip);
    }
    
    /**
     * 获取路径访问统计
     *
     * @param path 请求路径
     * @return 访问统计，如果不存在则返回null
     */
    public AccessStats getPathStats(String path) {
        return pathAccessStats.get(path);
    }
    
    /**
     * 访问统计类
     */
    @Data
    public static class AccessStats {
        /**
         * 总请求数
         */
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        
        /**
         * 时间窗口中的请求计数
         * Key: 时间戳（秒）
         * Value: 请求数
         */
        private final Map<Long, AtomicInteger> windowedCounts = new ConcurrentHashMap<>();
        
        /**
         * 最后访问时间
         */
        private Instant lastAccessTime = Instant.now();
        
        /**
         * 记录一次访问
         */
        public void recordAccess() {
            totalRequests.incrementAndGet();
            lastAccessTime = Instant.now();
            
            // 记录滑动窗口计数
            long currentSecond = lastAccessTime.getEpochSecond();
            windowedCounts.computeIfAbsent(currentSecond, k -> new AtomicInteger(0)).incrementAndGet();
            
            // 清理旧数据
            long oldestSecond = currentSecond - TRAFFIC_WINDOW;
            windowedCounts.keySet().removeIf(k -> k < oldestSecond);
        }
        
        /**
         * 获取每秒请求数
         *
         * @return 每秒平均请求数
         */
        public double getRequestsPerSecond() {
            long currentSecond = Instant.now().getEpochSecond();
            long oldestSecond = currentSecond - TRAFFIC_WINDOW;
            
            int recentRequests = 0;
            for (Map.Entry<Long, AtomicInteger> entry : windowedCounts.entrySet()) {
                if (entry.getKey() >= oldestSecond) {
                    recentRequests += entry.getValue().get();
                }
            }
            
            return (double) recentRequests / TRAFFIC_WINDOW;
        }
    }
}