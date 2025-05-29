/*
 * 文件名: VirtualThreadExecutorTest.java
 * 用途: 虚拟线程执行器单元测试
 * 实现内容:
 *   - 测试虚拟线程执行器基本功能
 *   - 验证任务提交和执行
 *   - 测试监控指标收集
 *   - 验证异常处理机制
 * 技术选型:
 *   - JUnit 5测试框架
 *   - 异步测试和超时控制
 * 依赖关系:
 *   - 测试VirtualThreadExecutor类
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟线程执行器单元测试
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class VirtualThreadExecutorTest {

    private VirtualThreadExecutor executor;
    private GameThreadFactory threadFactory;

    @BeforeEach
    void setUp() {
        threadFactory = new GameThreadFactory("test-vt");
        executor = new VirtualThreadExecutor("test-executor", threadFactory, 2, 4);
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @Timeout(10)
    void testBasicExecution() throws InterruptedException {
        // 测试基本任务执行
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        executor.execute(() -> {
            result.set(42);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(42, result.get());
    }

    @Test
    @Timeout(10)
    void testMultipleTaskExecution() throws InterruptedException {
        // 测试多任务并发执行
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(taskCount, counter.get());
    }

    @Test
    @Timeout(10)
    void testTaskSubmission() throws Exception {
        // 测试任务提交和获取结果
        String result = executor.submit(() -> "Hello World").get(5, TimeUnit.SECONDS);
        assertEquals("Hello World", result);
    }

    @Test
    @Timeout(10)
    void testExceptionHandling() throws InterruptedException {
        // 测试异常处理
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            try {
                throw new RuntimeException("Test exception");
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        // 验证失败任务计数
        assertTrue(executor.getFailedTaskCount() > 0);
    }

    @Test
    void testMetrics() throws InterruptedException {
        // 测试监控指标
        assertEquals("test-executor", executor.getName());
        
        CountDownLatch latch = new CountDownLatch(2);
        
        executor.execute(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        executor.execute(() -> {
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 等待任务完成统计
        Thread.sleep(200);
        
        assertTrue(executor.getSubmittedTaskCount() >= 2);
        assertTrue(executor.getCompletedTaskCount() >= 2);
        assertTrue(executor.getUpTime() > 0);
    }

    @Test
    void testShutdown() {
        // 测试优雅关闭
        assertFalse(executor.isShutdown());
        assertFalse(executor.isTerminated());

        executor.shutdown();
        
        assertTrue(executor.isShutdown());
        
        // 关闭后提交任务应该抛出异常
        assertThrows(Exception.class, () -> {
            executor.execute(() -> {});
        });
    }

    @Test
    void testStatus() {
        // 测试状态信息
        String status = executor.getStatus();
        assertNotNull(status);
        assertTrue(status.contains("test-executor"));
        assertTrue(status.contains("submitted="));
        assertTrue(status.contains("completed="));
    }
}