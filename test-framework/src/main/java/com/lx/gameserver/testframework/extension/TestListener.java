/*
 * 文件名: TestListener.java
 * 用途: 测试框架监听器接口
 * 内容: 
 *   - 测试生命周期事件监听
 *   - 测试结果处理
 *   - 自定义行为扩展
 *   - 插件集成支持
 *   - 事件通知机制
 * 技术选型: 
 *   - Java 21 SPI机制
 *   - 观察者模式
 *   - 事件驱动架构
 * 依赖关系: 
 *   - 被TestFramework调用
 *   - 依赖测试结果数据
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework.extension;

import com.lx.gameserver.testframework.core.TestFramework;
import com.lx.gameserver.testframework.core.TestCase;

/**
 * 测试框架监听器接口
 * <p>
 * 定义测试执行过程中的事件监听接口，支持自定义的
 * 测试生命周期处理和结果处理逻辑。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
public interface TestListener {
    
    /**
     * 测试开始前回调
     */
    default void onTestsStarted() {
        // 默认空实现
    }
    
    /**
     * 测试完成后回调
     * 
     * @param summary 测试汇总结果
     */
    default void onTestsCompleted(TestFramework.TestSummary summary) {
        // 默认空实现
    }
    
    /**
     * 测试出错时回调
     * 
     * @param error 错误信息
     */
    default void onTestsError(Exception error) {
        // 默认空实现
    }
    
    /**
     * 单个测试用例开始前回调
     * 
     * @param testCase 测试用例
     */
    default void onTestCaseStarted(TestCase testCase) {
        // 默认空实现
    }
    
    /**
     * 单个测试用例完成后回调
     * 
     * @param testCase 测试用例
     * @param result 测试结果
     */
    default void onTestCaseCompleted(TestCase testCase, TestCase.TestResult result) {
        // 默认空实现
    }
    
    /**
     * 单个测试用例失败时回调
     * 
     * @param testCase 测试用例
     * @param result 测试结果
     */
    default void onTestCaseFailed(TestCase testCase, TestCase.TestResult result) {
        // 默认空实现
    }
    
    /**
     * 测试套件开始前回调
     * 
     * @param suiteName 套件名称
     */
    default void onTestSuiteStarted(String suiteName) {
        // 默认空实现
    }
    
    /**
     * 测试套件完成后回调
     * 
     * @param suiteName 套件名称
     * @param result 套件结果
     */
    default void onTestSuiteCompleted(String suiteName, Object result) {
        // 默认空实现
    }
    
    /**
     * 获取监听器优先级
     * <p>数值越小优先级越高</p>
     * 
     * @return 优先级
     */
    default int getPriority() {
        return 1000; // 默认优先级
    }
    
    /**
     * 检查是否启用此监听器
     * 
     * @return 是否启用
     */
    default boolean isEnabled() {
        return true; // 默认启用
    }
}