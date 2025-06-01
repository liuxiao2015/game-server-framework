/*
 * 文件名: MockFactory.java
 * 用途: Mock对象工厂
 * 内容: 
 *   - Mock对象创建和管理
 *   - 服务Mock生成
 *   - 数据Mock生成
 *   - 行为Mock定义
 *   - Spy对象支持
 *   - Verify验证机制
 * 技术选型: 
 *   - Mockito框架
 *   - 反射API
 *   - 代理模式
 *   - 工厂模式
 * 依赖关系: 
 *   - 依赖Mockito框架
 *   - 被UnitTestRunner使用
 *   - 与TestContext集成
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.unit;

import com.lx.gameserver.testframework.core.TestContext;
import lombok.extern.slf4j.Slf4j;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

/**
 * Mock对象工厂
 * <p>
 * 提供Mock对象的创建、配置和管理功能，支持服务Mock、
 * 数据Mock、行为定义和验证机制。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@Component
public class MockFactory {
    
    /**
     * 已创建的Mock对象
     */
    private final Map<String, Object> mockObjects;
    
    /**
     * Spy对象
     */
    private final Map<String, Object> spyObjects;
    
    /**
     * 静态Mock对象
     */
    private final Map<Class<?>, MockedStatic<?>> staticMocks;
    
    /**
     * 测试上下文
     */
    private TestContext testContext;
    
    /**
     * 构造函数
     */
    public MockFactory() {
        this.mockObjects = new ConcurrentHashMap<>();
        this.spyObjects = new ConcurrentHashMap<>();
        this.staticMocks = new ConcurrentHashMap<>();
    }
    
    /**
     * 初始化Mock工厂
     * 
     * @param context 测试上下文
     */
    public void initialize(TestContext context) {
        this.testContext = context;
        log.debug("Mock工厂初始化完成");
    }
    
    /**
     * 创建Mock对象
     * 
     * @param clazz 要Mock的类
     * @param <T> 类型
     * @return Mock对象
     */
    public <T> T createMock(Class<T> clazz) {
        return createMock(clazz, clazz.getSimpleName());
    }
    
    /**
     * 创建Mock对象（带名称）
     * 
     * @param clazz 要Mock的类
     * @param name Mock对象名称
     * @param <T> 类型
     * @return Mock对象
     */
    @SuppressWarnings("unchecked")
    public <T> T createMock(Class<T> clazz, String name) {
        if (clazz == null) {
            throw new IllegalArgumentException("要Mock的类不能为空");
        }
        
        log.debug("创建Mock对象: {} ({})", clazz.getSimpleName(), name);
        
        T mockObject = mock(clazz, name);
        mockObjects.put(name, mockObject);
        
        // 注册到测试上下文
        if (testContext != null) {
            testContext.registerMockService(name, mockObject);
        }
        
        return mockObject;
    }
    
    /**
     * 创建Spy对象
     * 
     * @param realObject 真实对象
     * @param <T> 类型
     * @return Spy对象
     */
    public <T> T createSpy(T realObject) {
        return createSpy(realObject, realObject.getClass().getSimpleName() + "Spy");
    }
    
    /**
     * 创建Spy对象（带名称）
     * 
     * @param realObject 真实对象
     * @param name Spy对象名称
     * @param <T> 类型
     * @return Spy对象
     */
    @SuppressWarnings("unchecked")
    public <T> T createSpy(T realObject, String name) {
        if (realObject == null) {
            throw new IllegalArgumentException("真实对象不能为空");
        }
        
        log.debug("创建Spy对象: {} ({})", realObject.getClass().getSimpleName(), name);
        
        T spyObject = spy(realObject);
        spyObjects.put(name, spyObject);
        
        // 注册到测试上下文
        if (testContext != null) {
            testContext.registerMockService(name, spyObject);
        }
        
        return spyObject;
    }
    
    /**
     * 创建静态Mock
     * 
     * @param clazz 要Mock的类
     * @param <T> 类型
     * @return 静态Mock对象
     */
    public <T> MockedStatic<T> createStaticMock(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("要Mock的类不能为空");
        }
        
        log.debug("创建静态Mock对象: {}", clazz.getSimpleName());
        
        MockedStatic<T> staticMock = mockStatic(clazz);
        staticMocks.put(clazz, staticMock);
        
        return staticMock;
    }
    
    /**
     * 获取Mock对象
     * 
     * @param name Mock对象名称
     * @param <T> 类型
     * @return Mock对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getMock(String name) {
        return (T) mockObjects.get(name);
    }
    
    /**
     * 获取Spy对象
     * 
     * @param name Spy对象名称
     * @param <T> 类型
     * @return Spy对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getSpy(String name) {
        return (T) spyObjects.get(name);
    }
    
    /**
     * 获取静态Mock对象
     * 
     * @param clazz 类
     * @param <T> 类型
     * @return 静态Mock对象
     */
    @SuppressWarnings("unchecked")
    public <T> MockedStatic<T> getStaticMock(Class<T> clazz) {
        return (MockedStatic<T>) staticMocks.get(clazz);
    }
    
    /**
     * 配置Mock行为 - 当调用方法时返回值
     * 
     * @param methodCall 方法调用
     * @param returnValue 返回值
     * @param <T> 返回值类型
     */
    public <T> void whenThenReturn(T methodCall, T returnValue) {
        when(methodCall).thenReturn(returnValue);
        log.debug("配置Mock行为: {} -> {}", methodCall, returnValue);
    }
    
    /**
     * 配置Mock行为 - 当调用方法时抛出异常
     * 
     * @param methodCall 方法调用
     * @param exception 异常
     * @param <T> 返回值类型
     */
    public <T> void whenThenThrow(T methodCall, Throwable exception) {
        when(methodCall).thenThrow(exception);
        log.debug("配置Mock异常行为: {} -> {}", methodCall, exception.getClass().getSimpleName());
    }
    
    /**
     * 验证方法调用
     * 
     * @param mockObject Mock对象
     * @param <T> 类型
     * @return 验证对象
     */
    public <T> T verify(T mockObject) {
        return Mockito.verify(mockObject);
    }
    
    /**
     * 验证方法调用次数
     * 
     * @param mockObject Mock对象
     * @param times 调用次数
     * @param <T> 类型
     * @return 验证对象
     */
    public <T> T verify(T mockObject, int times) {
        return Mockito.verify(mockObject, times(times));
    }
    
    /**
     * 验证从未调用
     * 
     * @param mockObject Mock对象
     * @param <T> 类型
     * @return 验证对象
     */
    public <T> T verifyNever(T mockObject) {
        return Mockito.verify(mockObject, never());
    }
    
    /**
     * 验证至少调用一次
     * 
     * @param mockObject Mock对象
     * @param <T> 类型
     * @return 验证对象
     */
    public <T> T verifyAtLeastOnce(T mockObject) {
        return Mockito.verify(mockObject, atLeastOnce());
    }
    
    /**
     * 验证最多调用次数
     * 
     * @param mockObject Mock对象
     * @param maxTimes 最大调用次数
     * @param <T> 类型
     * @return 验证对象
     */
    public <T> T verifyAtMost(T mockObject, int maxTimes) {
        return Mockito.verify(mockObject, atMost(maxTimes));
    }
    
    /**
     * 验证没有更多交互
     * 
     * @param mockObjects Mock对象数组
     */
    public void verifyNoMoreInteractions(Object... mockObjects) {
        Mockito.verifyNoMoreInteractions(mockObjects);
    }
    
    /**
     * 重置Mock对象
     * 
     * @param mockObjects Mock对象数组
     */
    public void reset(Object... mockObjects) {
        Mockito.reset(mockObjects);
        log.debug("重置Mock对象: {}", Arrays.toString(mockObjects));
    }
    
    /**
     * 重置所有Mock对象
     */
    public void resetAll() {
        log.debug("重置所有Mock对象...");
        
        // 重置普通Mock对象
        if (!mockObjects.isEmpty()) {
            Object[] mocks = mockObjects.values().toArray();
            Mockito.reset(mocks);
        }
        
        // 重置Spy对象
        if (!spyObjects.isEmpty()) {
            Object[] spies = spyObjects.values().toArray();
            Mockito.reset(spies);
        }
        
        log.debug("所有Mock对象重置完成");
    }
    
    /**
     * 清理Mock工厂
     */
    public void cleanup() {
        log.debug("清理Mock工厂...");
        
        try {
            // 关闭静态Mock
            staticMocks.values().forEach(MockedStatic::close);
            staticMocks.clear();
            
            // 清理对象引用
            mockObjects.clear();
            spyObjects.clear();
            
            // 清理测试上下文中的Mock服务
            if (testContext != null) {
                mockObjects.keySet().forEach(testContext::removeMockService);
                spyObjects.keySet().forEach(testContext::removeMockService);
            }
            
            log.debug("Mock工厂清理完成");
            
        } catch (Exception e) {
            log.error("清理Mock工厂失败", e);
        }
    }
    
    /**
     * 获取所有Mock对象
     * 
     * @return Mock对象映射
     */
    public Map<String, Object> getAllMocks() {
        return Collections.unmodifiableMap(mockObjects);
    }
    
    /**
     * 获取所有Spy对象
     * 
     * @return Spy对象映射
     */
    public Map<String, Object> getAllSpies() {
        return Collections.unmodifiableMap(spyObjects);
    }
    
    /**
     * 获取所有静态Mock对象
     * 
     * @return 静态Mock对象映射
     */
    public Map<Class<?>, MockedStatic<?>> getAllStaticMocks() {
        return Collections.unmodifiableMap(staticMocks);
    }
    
    /**
     * 检查是否存在Mock对象
     * 
     * @param name Mock对象名称
     * @return 是否存在
     */
    public boolean hasMock(String name) {
        return mockObjects.containsKey(name);
    }
    
    /**
     * 检查是否存在Spy对象
     * 
     * @param name Spy对象名称
     * @return 是否存在
     */
    public boolean hasSpy(String name) {
        return spyObjects.containsKey(name);
    }
    
    /**
     * 检查是否存在静态Mock对象
     * 
     * @param clazz 类
     * @return 是否存在
     */
    public boolean hasStaticMock(Class<?> clazz) {
        return staticMocks.containsKey(clazz);
    }
}