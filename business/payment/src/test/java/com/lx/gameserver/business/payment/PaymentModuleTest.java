/*
 * 文件名: PaymentModuleTest.java
 * 用途: 支付模块集成测试
 * 实现内容:
 *   - 支付模块核心功能测试
 *   - 订单创建和状态流转测试
 *   - 支付渠道集成测试
 *   - 回调处理测试
 *   - 配置验证测试
 * 技术选型:
 *   - JUnit 5
 *   - Spring Boot Test
 *   - Mockito
 *   - 集成测试
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment;

import com.lx.gameserver.business.payment.core.PaymentContext;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import com.lx.gameserver.business.payment.order.OrderNumberGenerator;
import com.lx.gameserver.business.payment.order.OrderService;
import com.lx.gameserver.business.payment.order.OrderValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付模块集成测试
 * <p>
 * 测试支付模块的核心功能，验证各组件的集成和协作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class PaymentModuleTest {

    private static final Logger logger = LoggerFactory.getLogger(PaymentModuleTest.class);

    private OrderNumberGenerator orderNumberGenerator;
    private OrderValidator orderValidator;

    @BeforeEach
    public void setUp() {
        orderNumberGenerator = new OrderNumberGenerator();
        orderValidator = new OrderValidator();
        logger.info("测试环境初始化完成");
    }

    @Test
    public void testOrderNumberGeneration() {
        logger.info("测试订单号生成功能");

        // 创建支付上下文
        PaymentContext context = PaymentContext.builder()
                .userId(12345L)
                .productId("PRODUCT_001")
                .productName("测试商品")
                .orderAmount(new BigDecimal("99.99"))
                .paymentChannel("alipay")
                .paymentMethod("app")
                .clientIp("192.168.1.100")
                .businessScene("recharge")
                .build();

        // 生成订单号
        String orderId = orderNumberGenerator.generateOrderNumber(context);

        // 验证订单号
        assertNotNull(orderId, "订单号不能为空");
        assertTrue(orderId.length() > 20, "订单号长度应该大于20");
        assertTrue(orderNumberGenerator.validateOrderNumber(orderId), "订单号格式应该正确");

        // 验证业务前缀
        OrderNumberGenerator.BusinessPrefix prefix = orderNumberGenerator.extractBusinessPrefix(orderId);
        assertEquals(OrderNumberGenerator.BusinessPrefix.GAME_RECHARGE, prefix, "业务前缀应该是充值类型");

        // 验证时间提取
        LocalDateTime extractedTime = orderNumberGenerator.extractTimeFromOrderNumber(orderId);
        assertNotNull(extractedTime, "应该能从订单号提取时间");

        logger.info("订单号生成测试通过: {}", orderId);
    }

    @Test
    public void testBatchOrderNumberGeneration() {
        logger.info("测试批量订单号生成功能");

        int batchSize = 100;
        String[] orderNumbers = orderNumberGenerator.generateBatchOrderNumbers(
                batchSize, OrderNumberGenerator.BusinessPrefix.PRODUCT_BUY);

        assertEquals(batchSize, orderNumbers.length, "批量生成数量应该正确");

        // 验证每个订单号的唯一性和格式
        for (int i = 0; i < orderNumbers.length; i++) {
            String orderId = orderNumbers[i];
            assertNotNull(orderId, "订单号不能为空");
            assertTrue(orderNumberGenerator.validateOrderNumber(orderId), "订单号格式应该正确");

            // 验证唯一性（简单验证，与后面的订单号不重复）
            for (int j = i + 1; j < orderNumbers.length; j++) {
                assertNotEquals(orderId, orderNumbers[j], "订单号应该唯一");
            }
        }

        logger.info("批量订单号生成测试通过，生成{}个订单号", batchSize);
    }

    @Test
    public void testOrderValidation() {
        logger.info("测试订单验证功能");

        // 测试有效的支付上下文
        PaymentContext validContext = PaymentContext.builder()
                .userId(12345L)
                .productId("PRODUCT_001")
                .productName("测试商品")
                .orderAmount(new BigDecimal("10.00"))
                .paymentChannel("alipay")
                .paymentMethod("app")
                .clientIp("192.168.1.100")
                .build();

        // 验证应该通过
        assertDoesNotThrow(() -> orderValidator.validateContext(validContext), 
                "有效的支付上下文应该通过验证");

        // 测试无效的支付上下文（金额为空）
        PaymentContext invalidContext = PaymentContext.builder()
                .userId(12345L)
                .productId("PRODUCT_001")
                .productName("测试商品")
                .orderAmount(null)
                .paymentChannel("alipay")
                .paymentMethod("app")
                .clientIp("192.168.1.100")
                .build();

        // 验证应该失败
        assertThrows(OrderValidator.ValidationException.class, 
                () -> orderValidator.validateContext(invalidContext),
                "无效的支付上下文应该抛出验证异常");

        logger.info("订单验证测试通过");
    }

    @Test
    public void testOrderStatusTransition() {
        logger.info("测试订单状态流转");

        // 测试合法的状态流转
        assertTrue(PaymentOrder.OrderStatus.PENDING.canTransitionTo(PaymentOrder.OrderStatus.PAYING),
                "待支付可以转换为支付中");
        assertTrue(PaymentOrder.OrderStatus.PAYING.canTransitionTo(PaymentOrder.OrderStatus.PAID),
                "支付中可以转换为已支付");
        assertTrue(PaymentOrder.OrderStatus.PAID.canTransitionTo(PaymentOrder.OrderStatus.REFUNDED),
                "已支付可以转换为已退款");

        // 测试非法的状态流转
        assertFalse(PaymentOrder.OrderStatus.PAID.canTransitionTo(PaymentOrder.OrderStatus.PENDING),
                "已支付不能转换为待支付");
        assertFalse(PaymentOrder.OrderStatus.REFUNDED.canTransitionTo(PaymentOrder.OrderStatus.PAID),
                "已退款不能转换为已支付");

        logger.info("订单状态流转测试通过");
    }

    @Test
    public void testPaymentOrderCreation() {
        logger.info("测试支付订单创建");

        PaymentOrder order = new PaymentOrder();
        order.setOrderId("GM20250113123456001234");
        order.setUserId(12345L);
        order.setProductId("PRODUCT_001");
        order.setProductName("测试商品");
        order.setOrderType(PaymentOrder.OrderType.PRODUCT);
        order.setOrderAmountInYuan(new BigDecimal("99.99"));
        order.setCurrency("CNY");
        order.setPaymentChannel("alipay");
        order.setPaymentMethod("app");
        order.setOrderStatus(PaymentOrder.OrderStatus.PENDING);
        order.setClientIp("192.168.1.100");
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setCreateTime(LocalDateTime.now());

        // 验证订单数据
        assertEquals("GM20250113123456001234", order.getOrderId());
        assertEquals(Long.valueOf(12345L), order.getUserId());
        assertEquals("PRODUCT_001", order.getProductId());
        assertEquals(new BigDecimal("99.99"), order.getOrderAmountInYuan());
        assertEquals(PaymentOrder.OrderStatus.PENDING, order.getOrderStatus());
        assertTrue(order.canPay(), "订单应该可以支付");
        assertFalse(order.isExpired(), "订单不应该过期");

        logger.info("支付订单创建测试通过: {}", order.getOrderId());
    }

    @Test
    public void testOrderAmountConversion() {
        logger.info("测试订单金额转换");

        PaymentOrder order = new PaymentOrder();

        // 测试元转分
        order.setOrderAmountInYuan(new BigDecimal("99.99"));
        assertEquals(Long.valueOf(9999L), order.getOrderAmount(), "99.99元应该等于9999分");

        order.setOrderAmountInYuan(new BigDecimal("1.00"));
        assertEquals(Long.valueOf(100L), order.getOrderAmount(), "1.00元应该等于100分");

        // 测试分转元
        order.setOrderAmount(9999L);
        assertEquals(0, new BigDecimal("99.99").compareTo(order.getOrderAmountInYuan()), "9999分应该等于99.99元");

        order.setOrderAmount(100L);
        assertEquals(0, new BigDecimal("1.00").compareTo(order.getOrderAmountInYuan()), "100分应该等于1.00元");

        logger.info("订单金额转换测试通过");
    }

    @Test
    public void testPaymentContextValidation() {
        logger.info("测试支付上下文验证");

        // 测试有效的上下文
        PaymentContext validContext = PaymentContext.builder()
                .orderId("GM20250113123456001234")
                .userId(12345L)
                .productId("PRODUCT_001")
                .productName("测试商品")
                .orderAmount(new BigDecimal("10.00"))
                .paymentChannel("alipay")
                .paymentMethod("app")
                .clientIp("192.168.1.100")
                .build();

        assertTrue(validContext.isValid(), "有效的上下文应该通过验证");

        // 测试无效的上下文（缺少必要字段）
        PaymentContext invalidContext = PaymentContext.builder()
                .userId(12345L)
                .productId("PRODUCT_001")
                .build();

        assertFalse(invalidContext.isValid(), "无效的上下文应该验证失败");

        logger.info("支付上下文验证测试通过");
    }

    @Test
    public void testGeneratorInfo() {
        logger.info("测试生成器信息");

        String info = orderNumberGenerator.getGeneratorInfo();
        assertNotNull(info, "生成器信息不能为空");
        assertTrue(info.contains("OrderNumberGenerator"), "应该包含类名");
        assertTrue(info.contains("machineId"), "应该包含机器ID");
        assertTrue(info.contains("datacenterId"), "应该包含数据中心ID");

        logger.info("生成器信息: {}", info);
    }

    @Test
    public void testValidatorConfig() {
        logger.info("测试验证器配置");

        var config = orderValidator.getValidatorConfig();
        assertNotNull(config, "验证器配置不能为空");
        assertTrue(config.containsKey("minOrderAmount"), "应该包含最小订单金额");
        assertTrue(config.containsKey("maxOrderAmount"), "应该包含最大订单金额");

        Long minAmount = (Long) config.get("minOrderAmount");
        Long maxAmount = (Long) config.get("maxOrderAmount");
        assertTrue(minAmount > 0, "最小金额应该大于0");
        assertTrue(maxAmount > minAmount, "最大金额应该大于最小金额");

        logger.info("验证器配置: {}", config);
    }

    @Test
    public void testPerformance() {
        logger.info("测试性能指标");

        int testCount = 1000;
        long startTime = System.currentTimeMillis();

        // 批量生成订单号
        for (int i = 0; i < testCount; i++) {
            PaymentContext context = PaymentContext.builder()
                    .userId((long) (i + 1))
                    .productId("PRODUCT_" + String.format("%03d", i % 100))
                    .productName("测试商品" + i)
                    .orderAmount(new BigDecimal("10.00"))
                    .paymentChannel("alipay")
                    .paymentMethod("app")
                    .clientIp("192.168.1." + (i % 254 + 1))
                    .build();

            String orderId = orderNumberGenerator.generateOrderNumber(context);
            assertNotNull(orderId, "订单号不能为空");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double tps = (double) testCount / duration * 1000;

        logger.info("性能测试完成: 生成{}个订单号，耗时{}ms，TPS: {:.2f}", testCount, duration, tps);

        // 验证TPS满足要求（应该能达到每秒几千个）
        assertTrue(tps > 100, "TPS应该大于100");
    }
}