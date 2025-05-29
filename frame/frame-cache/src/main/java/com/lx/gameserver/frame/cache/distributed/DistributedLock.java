/*
 * 文件名: DistributedLock.java
 * 用途: 分布式锁
 * 实现内容:
 *   - Redis分布式锁实现
 *   - 锁超时机制
 *   - 可重入锁支持
 *   - 公平锁实现
 *   - 锁监控和统计
 * 技术选型:
 *   - Redis SET命令实现
 *   - Lua脚本保证原子性
 *   - 看门狗机制自动续期
 *   - CompletableFuture异步支持
 * 依赖关系:
 *   - 依赖Redis连接池
 *   - 被缓存组件使用
 *   - 与监控系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式锁
 * <p>
 * 基于Redis实现的分布式锁，支持锁超时、可重入、公平性等特性。
 * 使用Lua脚本保证操作的原子性，支持自动续期机制。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class DistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLock.class);

    /**
     * 锁键前缀
     */
    private static final String LOCK_PREFIX = "distributed:lock:";

    /**
     * 重入锁计数前缀
     */
    private static final String REENTRANT_PREFIX = "distributed:reentrant:";

    /**
     * 公平锁队列前缀
     */
    private static final String FAIR_QUEUE_PREFIX = "distributed:fair:";

    /**
     * 默认锁超时时间
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 默认等待时间
     */
    private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(10);

    /**
     * 看门狗续期间隔
     */
    private static final Duration WATCHDOG_INTERVAL = Duration.ofSeconds(10);

    /**
     * Redis模板
     */
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 锁名称
     */
    private final String lockName;

    /**
     * 锁键
     */
    private final String lockKey;

    /**
     * 锁值（UUID + 线程ID）
     */
    private final String lockValue;

    /**
     * 锁超时时间
     */
    private final Duration timeout;

    /**
     * 是否支持重入
     */
    private final boolean reentrant;

    /**
     * 是否公平锁
     */
    private final boolean fair;

    /**
     * 重入计数
     */
    private final AtomicInteger reentrantCount = new AtomicInteger(0);

    /**
     * 看门狗任务
     */
    private volatile ScheduledFuture<?> watchdogTask;

    /**
     * 看门狗执行器
     */
    private static final ScheduledExecutorService watchdogExecutor = 
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "distributed-lock-watchdog");
            t.setDaemon(true);
            return t;
        });

    /**
     * 统计信息
     */
    private static final AtomicLong lockAttempts = new AtomicLong(0);
    private static final AtomicLong lockSuccesses = new AtomicLong(0);
    private static final AtomicLong lockFailures = new AtomicLong(0);
    private static final AtomicLong unlockOperations = new AtomicLong(0);

    /**
     * Lua脚本：获取锁
     */
    private static final String ACQUIRE_SCRIPT = 
        "if redis.call('get', KEYS[1]) == false then " +
        "  redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2]) " +
        "  return 1 " +
        "elseif redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  redis.call('pexpire', KEYS[1], ARGV[2]) " +
        "  return 1 " +
        "else " +
        "  return 0 " +
        "end";

    /**
     * Lua脚本：释放锁
     */
    private static final String RELEASE_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    /**
     * Lua脚本：续期锁
     */
    private static final String RENEW_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
        "else " +
        "  return 0 " +
        "end";

    /**
     * 构造函数
     *
     * @param redisTemplate Redis模板
     * @param lockName      锁名称
     */
    public DistributedLock(RedisTemplate<String, String> redisTemplate, String lockName) {
        this(redisTemplate, lockName, DEFAULT_TIMEOUT, false, false);
    }

    /**
     * 构造函数
     *
     * @param redisTemplate Redis模板
     * @param lockName      锁名称
     * @param timeout       锁超时时间
     * @param reentrant     是否支持重入
     * @param fair          是否公平锁
     */
    public DistributedLock(RedisTemplate<String, String> redisTemplate, String lockName, 
                          Duration timeout, boolean reentrant, boolean fair) {
        this.redisTemplate = redisTemplate;
        this.lockName = lockName;
        this.lockKey = LOCK_PREFIX + lockName;
        this.lockValue = generateLockValue();
        this.timeout = timeout;
        this.reentrant = reentrant;
        this.fair = fair;
    }

    /**
     * 尝试获取锁
     *
     * @return 是否成功获取锁
     */
    public boolean tryLock() {
        return tryLock(Duration.ZERO);
    }

    /**
     * 尝试获取锁并等待指定时间
     *
     * @param waitTime 等待时间
     * @return 是否成功获取锁
     */
    public boolean tryLock(Duration waitTime) {
        lockAttempts.incrementAndGet();
        
        try {
            if (fair) {
                return tryFairLock(waitTime);
            } else {
                return tryUnfairLock(waitTime);
            }
        } catch (Exception e) {
            lockFailures.incrementAndGet();
            logger.error("获取分布式锁失败: {}", lockName, e);
            return false;
        }
    }

    /**
     * 异步尝试获取锁
     *
     * @return 异步结果
     */
    public CompletableFuture<Boolean> tryLockAsync() {
        return tryLockAsync(Duration.ZERO);
    }

    /**
     * 异步尝试获取锁并等待指定时间
     *
     * @param waitTime 等待时间
     * @return 异步结果
     */
    public CompletableFuture<Boolean> tryLockAsync(Duration waitTime) {
        return CompletableFuture.supplyAsync(() -> tryLock(waitTime));
    }

    /**
     * 释放锁
     *
     * @return 是否成功释放锁
     */
    public boolean unlock() {
        unlockOperations.incrementAndGet();
        
        try {
            if (reentrant) {
                return unlockReentrant();
            } else {
                return unlockSimple();
            }
        } catch (Exception e) {
            logger.error("释放分布式锁失败: {}", lockName, e);
            return false;
        } finally {
            // 停止看门狗
            stopWatchdog();
        }
    }

    /**
     * 检查是否持有锁
     *
     * @return 是否持有锁
     */
    public boolean isLocked() {
        try {
            String value = redisTemplate.opsForValue().get(lockKey);
            return lockValue.equals(value);
        } catch (Exception e) {
            logger.error("检查锁状态失败: {}", lockName, e);
            return false;
        }
    }

    /**
     * 获取锁信息
     *
     * @return 锁信息
     */
    public LockInfo getLockInfo() {
        try {
            String value = redisTemplate.opsForValue().get(lockKey);
            Long ttl = redisTemplate.getExpire(lockKey);
            
            return new LockInfo(
                lockName,
                value != null,
                lockValue.equals(value),
                ttl != null ? Duration.ofSeconds(ttl) : null,
                reentrantCount.get()
            );
        } catch (Exception e) {
            logger.error("获取锁信息失败: {}", lockName, e);
            return new LockInfo(lockName, false, false, null, 0);
        }
    }

    /**
     * 尝试非公平锁
     */
    private boolean tryUnfairLock(Duration waitTime) {
        Instant deadline = waitTime.isZero() ? Instant.now() : Instant.now().plus(waitTime);
        
        do {
            if (acquireLock()) {
                lockSuccesses.incrementAndGet();
                startWatchdog();
                return true;
            }
            
            if (waitTime.isZero()) {
                break;
            }
            
            // 短暂等待后重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
        } while (Instant.now().isBefore(deadline));
        
        lockFailures.incrementAndGet();
        return false;
    }

    /**
     * 尝试公平锁
     */
    private boolean tryFairLock(Duration waitTime) {
        String queueKey = FAIR_QUEUE_PREFIX + lockName;
        String queueValue = lockValue;
        
        try {
            // 加入等待队列
            redisTemplate.opsForList().leftPush(queueKey, queueValue);
            redisTemplate.expire(queueKey, timeout.plusMinutes(1));
            
            Instant deadline = waitTime.isZero() ? Instant.now() : Instant.now().plus(waitTime);
            
            do {
                // 检查是否轮到自己
                String first = redisTemplate.opsForList().index(queueKey, -1);
                if (queueValue.equals(first) && acquireLock()) {
                    // 获取锁成功，从队列中移除
                    redisTemplate.opsForList().rightPop(queueKey);
                    lockSuccesses.incrementAndGet();
                    startWatchdog();
                    return true;
                }
                
                if (waitTime.isZero()) {
                    break;
                }
                
                // 等待
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
            } while (Instant.now().isBefore(deadline));
            
            // 获取锁失败，从队列中移除
            redisTemplate.opsForList().remove(queueKey, 1, queueValue);
            lockFailures.incrementAndGet();
            return false;
            
        } catch (Exception e) {
            logger.error("公平锁获取失败: {}", lockName, e);
            lockFailures.incrementAndGet();
            return false;
        }
    }

    /**
     * 获取锁
     */
    private boolean acquireLock() {
        if (reentrant && isLocked()) {
            reentrantCount.incrementAndGet();
            return true;
        }
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, 
            Collections.singletonList(lockKey), 
            lockValue, 
            String.valueOf(timeout.toMillis()));
        
        if (result != null && result == 1) {
            reentrantCount.set(1);
            return true;
        }
        
        return false;
    }

    /**
     * 释放简单锁
     */
    private boolean unlockSimple() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, 
            Collections.singletonList(lockKey), 
            lockValue);
        
        return result != null && result == 1;
    }

    /**
     * 释放重入锁
     */
    private boolean unlockReentrant() {
        if (!isLocked()) {
            return false;
        }
        
        int count = reentrantCount.decrementAndGet();
        if (count > 0) {
            return true; // 还有重入层次，不释放锁
        }
        
        return unlockSimple();
    }

    /**
     * 启动看门狗
     */
    private void startWatchdog() {
        if (watchdogTask != null && !watchdogTask.isDone()) {
            return;
        }
        
        watchdogTask = watchdogExecutor.scheduleAtFixedRate(
            this::renewLock,
            WATCHDOG_INTERVAL.toMillis(),
            WATCHDOG_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        logger.debug("启动锁看门狗: {}", lockName);
    }

    /**
     * 停止看门狗
     */
    private void stopWatchdog() {
        if (watchdogTask != null && !watchdogTask.isDone()) {
            watchdogTask.cancel(false);
            logger.debug("停止锁看门狗: {}", lockName);
        }
    }

    /**
     * 续期锁
     */
    private void renewLock() {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, 
                Collections.singletonList(lockKey), 
                lockValue, 
                String.valueOf(timeout.toMillis()));
            
            if (result == null || result != 1) {
                logger.warn("锁续期失败，可能已被其他进程释放: {}", lockName);
                stopWatchdog();
            } else {
                logger.debug("锁续期成功: {}", lockName);
            }
        } catch (Exception e) {
            logger.error("锁续期异常: {}", lockName, e);
        }
    }

    /**
     * 生成锁值
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public static LockStatistics getStatistics() {
        return new LockStatistics(
            lockAttempts.get(),
            lockSuccesses.get(),
            lockFailures.get(),
            unlockOperations.get()
        );
    }

    /**
     * 锁信息
     */
    public static class LockInfo {
        private final String lockName;
        private final boolean exists;
        private final boolean heldByCurrentThread;
        private final Duration ttl;
        private final int reentrantCount;

        public LockInfo(String lockName, boolean exists, boolean heldByCurrentThread, 
                       Duration ttl, int reentrantCount) {
            this.lockName = lockName;
            this.exists = exists;
            this.heldByCurrentThread = heldByCurrentThread;
            this.ttl = ttl;
            this.reentrantCount = reentrantCount;
        }

        public String getLockName() { return lockName; }
        public boolean isExists() { return exists; }
        public boolean isHeldByCurrentThread() { return heldByCurrentThread; }
        public Duration getTtl() { return ttl; }
        public int getReentrantCount() { return reentrantCount; }
    }

    /**
     * 锁统计信息
     */
    public static class LockStatistics {
        private final long lockAttempts;
        private final long lockSuccesses;
        private final long lockFailures;
        private final long unlockOperations;

        public LockStatistics(long lockAttempts, long lockSuccesses, 
                            long lockFailures, long unlockOperations) {
            this.lockAttempts = lockAttempts;
            this.lockSuccesses = lockSuccesses;
            this.lockFailures = lockFailures;
            this.unlockOperations = unlockOperations;
        }

        public long getLockAttempts() { return lockAttempts; }
        public long getLockSuccesses() { return lockSuccesses; }
        public long getLockFailures() { return lockFailures; }
        public long getUnlockOperations() { return unlockOperations; }
        
        public double getSuccessRate() {
            return lockAttempts > 0 ? (double) lockSuccesses / lockAttempts : 0.0;
        }
        
        public double getFailureRate() {
            return lockAttempts > 0 ? (double) lockFailures / lockAttempts : 0.0;
        }
    }

    /**
     * 分布式锁工厂
     */
    public static class DistributedLockFactory {
        private final RedisTemplate<String, String> redisTemplate;
        private final Duration defaultTimeout;
        private final boolean defaultReentrant;
        private final boolean defaultFair;

        public DistributedLockFactory(RedisTemplate<String, String> redisTemplate) {
            this(redisTemplate, DEFAULT_TIMEOUT, false, false);
        }

        public DistributedLockFactory(RedisTemplate<String, String> redisTemplate,
                                    Duration defaultTimeout, boolean defaultReentrant, boolean defaultFair) {
            this.redisTemplate = redisTemplate;
            this.defaultTimeout = defaultTimeout;
            this.defaultReentrant = defaultReentrant;
            this.defaultFair = defaultFair;
        }

        public DistributedLock createLock(String lockName) {
            return new DistributedLock(redisTemplate, lockName, defaultTimeout, defaultReentrant, defaultFair);
        }

        public DistributedLock createLock(String lockName, Duration timeout) {
            return new DistributedLock(redisTemplate, lockName, timeout, defaultReentrant, defaultFair);
        }

        public DistributedLock createReentrantLock(String lockName) {
            return new DistributedLock(redisTemplate, lockName, defaultTimeout, true, defaultFair);
        }

        public DistributedLock createFairLock(String lockName) {
            return new DistributedLock(redisTemplate, lockName, defaultTimeout, defaultReentrant, true);
        }
    }
}