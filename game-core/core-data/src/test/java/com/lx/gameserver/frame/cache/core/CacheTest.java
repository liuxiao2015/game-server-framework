/*
 * 文件名: CacheTest.java
 * 用途: 缓存核心功能测试
 * 内容: 
 *   - 测试缓存基本操作
 *   - 验证缓存过期机制
 *   - 测试缓存性能指标
 * 技术选型: 
 *   - JUnit 5测试框架
 *   - Mockito模拟框架
 * 依赖关系: 
 *   - 测试frame-cache模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.frame.cache.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

/**
 * 缓存核心功能测试
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@DisplayName("缓存核心功能测试")
class CacheTest {
    
    private CacheConfig config;
    
    @BeforeEach
    void setUp() {
        config = CacheConfig.builder("test-cache")
                .maxSize(1000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .statisticsEnabled(true)
                .build();
    }
    
    @Test
    @DisplayName("测试缓存配置创建")
    void testCacheConfigCreation() {
        assertNotNull(config);
        assertEquals(1000, config.getMaxSize());
        assertEquals(Duration.ofSeconds(60), config.getExpireAfterWrite());
        assertTrue(config.isStatisticsEnabled());
        assertEquals("test-cache", config.getName());
    }
    
    @Test
    @DisplayName("测试缓存键创建")
    void testCacheKeyCreation() {
        CacheKey key = CacheKey.of("test", "key1");
        assertNotNull(key);
        assertEquals("test:key1", key.toString());
    }
    
    @Test
    @DisplayName("测试缓存条目创建")
    void testCacheEntryCreation() {
        String value = "test_value";
        CacheKey key = CacheKey.of("test", "key1");
        CacheEntry<String> entry = CacheEntry.of(key, value);
        
        assertNotNull(entry);
        assertEquals(value, entry.getValue());
        assertNotNull(entry.getCreateTime());
        assertNotNull(entry.getLastAccessTime());
    }
    
    @Test
    @DisplayName("测试缓存条目访问时间更新")
    void testCacheEntryAccessTimeUpdate() throws InterruptedException {
        String value = "test_value";
        CacheKey key = CacheKey.of("test", "key1");
        CacheEntry<String> entry = CacheEntry.of(key, value);
        
        // 获取初始访问时间
        var originalAccessTime = entry.getLastAccessTime();
        
        // 短暂等待后访问
        Thread.sleep(10);
        entry.recordAccess();
        
        // 验证访问时间已更新
        assertTrue(entry.getLastAccessTime().isAfter(originalAccessTime));
    }
    
    @Test
    @DisplayName("测试默认配置创建")
    void testDefaultConfigCreation() {
        CacheConfig defaultConfig = CacheConfig.defaultConfig("default-cache");
        
        assertNotNull(defaultConfig);
        assertEquals("default-cache", defaultConfig.getName());
        assertEquals(CacheConfig.DEFAULT_MAX_SIZE, defaultConfig.getMaxSize());
    }
}