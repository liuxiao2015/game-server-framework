/*
 * 数据库模块集成测试
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
package com.lx.gameserver.frame.db;

import com.lx.gameserver.frame.db.base.BaseEntity;
import com.lx.gameserver.frame.db.config.DatabaseProperties;
import com.lx.gameserver.frame.db.datasource.DataSourceContextHolder;
import com.lx.gameserver.frame.db.security.EncryptionHandler;
import com.lx.gameserver.frame.db.security.SensitiveDataHandler;
import com.lx.gameserver.frame.db.monitor.DatabaseHealthCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库模块集成测试类
 */
public class DatabaseModuleTest {

    private EncryptionHandler encryptionHandler;
    private SensitiveDataHandler sensitiveDataHandler;
    private DatabaseProperties databaseProperties;

    @BeforeEach
    void setUp() {
        encryptionHandler = new EncryptionHandler();
        sensitiveDataHandler = new SensitiveDataHandler();
        databaseProperties = new DatabaseProperties();
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        DataSourceContextHolder.clear();
    }

    @Test
    void testEncryptionHandler() {
        // 测试加密解密功能
        encryptionHandler.setEncryptionEnabled(true); // 手动启用加密
        encryptionHandler.updateMasterKey("testKey123"); // 设置测试密钥
        
        String originalText = "sensitive_password_123";
        String encrypted = encryptionHandler.encrypt(originalText);
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);

        String decrypted = encryptionHandler.decrypt(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    void testSensitiveDataHandler() {
        // 测试脱敏功能
        String phoneNumber = "13812345678";
        String maskedPhone = sensitiveDataHandler.maskData(phoneNumber, 
            com.lx.gameserver.frame.db.security.Sensitive.SensitiveType.MOBILE_PHONE);
        
        assertNotNull(maskedPhone);
        assertNotEquals(phoneNumber, maskedPhone);
        assertTrue(maskedPhone.contains("*"));
    }

    @Test
    void testDataSourceContextHolder() {
        // 确保测试开始时清空状态
        DataSourceContextHolder.clear();
        DataSourceContextHolder.cancelForceMaster(); // 确保没有强制主库
        
        // 测试数据源上下文切换
        // 当栈为空且没有强制主库时，应该返回默认的MASTER
        assertEquals(DataSourceContextHolder.DataSourceType.MASTER, DataSourceContextHolder.getDataSourceType());

        DataSourceContextHolder.setDataSourceType(DataSourceContextHolder.DataSourceType.SLAVE);
        assertEquals(DataSourceContextHolder.DataSourceType.SLAVE, DataSourceContextHolder.getDataSourceType());

        DataSourceContextHolder.clear();
        // 清空后应该返回默认的MASTER
        assertEquals(DataSourceContextHolder.DataSourceType.MASTER, DataSourceContextHolder.getDataSourceType());
    }

    @Test
    void testBaseEntityFields() {
        // 测试基础实体字段
        TestEntity entity = new TestEntity();
        
        entity.setId(1L);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setCreateBy(1L);
        entity.setUpdateBy(1L);
        entity.setVersion(1);
        entity.setDeleted(0);
        entity.setRemark("测试实体");

        assertNotNull(entity.getId());
        assertNotNull(entity.getCreateTime());
        assertNotNull(entity.getUpdateTime());
        assertNotNull(entity.getCreateBy());
        assertNotNull(entity.getUpdateBy());
        assertNotNull(entity.getVersion());
        assertNotNull(entity.getDeleted());
        assertNotNull(entity.getRemark());
    }

    @Test
    void testDatabaseProperties() {
        // 测试数据库配置属性
        databaseProperties.setEnabled(true);
        assertTrue(databaseProperties.isEnabled());

        DatabaseProperties.DataSourceConfig masterConfig = new DatabaseProperties.DataSourceConfig();
        masterConfig.setUrl("jdbc:h2:mem:testdb");
        masterConfig.setUsername("sa");
        masterConfig.setPassword("");
        masterConfig.setDriverClassName("org.h2.Driver");
        
        databaseProperties.setMaster(masterConfig);
        assertNotNull(databaseProperties.getMaster());
        assertEquals("jdbc:h2:mem:testdb", databaseProperties.getMaster().getUrl());
    }

    /**
     * 测试实体类
     */
    private static class TestEntity extends BaseEntity {
        // 继承BaseEntity的所有字段
    }
}