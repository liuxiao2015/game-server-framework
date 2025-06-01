/*
 * 文件名: ChannelAdapter.java
 * 用途: 支付渠道适配器基类
 * 实现内容:
 *   - 渠道适配器抽象基类实现
 *   - 通用HTTP客户端封装
 *   - 请求重试机制和超时处理
 *   - 统一日志记录和性能监控
 *   - 渠道通用配置管理
 * 技术选型:
 *   - OkHttp客户端
 *   - 重试机制设计
 *   - 监控指标收集
 *   - 模板方法模式
 * 依赖关系:
 *   - 被具体渠道实现继承
 *   - 集成HTTP客户端
 *   - 依赖监控组件
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.business.payment.channel;

import com.lx.gameserver.business.payment.core.PaymentChannel;
import com.lx.gameserver.business.payment.core.PaymentContext;
import com.lx.gameserver.business.payment.core.PaymentOrder;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 支付渠道适配器基类
 * <p>
 * 为所有支付渠道提供通用的基础功能，包括HTTP客户端、
 * 重试机制、监控统计、日志记录等公共能力。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public abstract class ChannelAdapter implements PaymentChannel {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * HTTP客户端
     */
    protected final OkHttpClient httpClient;

    /**
     * 渠道配置
     */
    protected final Map<String, Object> channelConfig;

    /**
     * 渠道状态
     */
    protected volatile ChannelStatus channelStatus = ChannelStatus.NORMAL;

    /**
     * 构造函数
     */
    protected ChannelAdapter(Map<String, Object> config) {
        this.channelConfig = new HashMap<>(config != null ? config : new HashMap<>());
        this.httpClient = createHttpClient();
        logger.info("初始化支付渠道适配器: {}", getChannelType().getName());
    }

    /**
     * 创建HTTP客户端
     */
    protected OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(this::addCommonHeaders)
                .addInterceptor(this::logRequest)
                .build();
    }

    /**
     * 添加通用请求头
     */
    protected Response addCommonHeaders(Interceptor.Chain chain) throws IOException {
        Request originalRequest = chain.request();
        Request.Builder builder = originalRequest.newBuilder()
                .addHeader("User-Agent", getUserAgent())
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Accept", "application/json")
                .addHeader("X-Request-Time", String.valueOf(System.currentTimeMillis()))
                .addHeader("X-Channel", getChannelType().getCode());

        // 添加渠道特定的请求头
        addChannelHeaders(builder);

        return chain.proceed(builder.build());
    }

    /**
     * 记录请求日志
     */
    protected Response logRequest(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        long startTime = System.currentTimeMillis();

        logger.debug("发起HTTP请求: {} {}", request.method(), request.url());

        try {
            Response response = chain.proceed(request);
            long duration = System.currentTimeMillis() - startTime;

            logger.info("HTTP请求完成: {} {} -> {} ({}ms)", 
                    request.method(), request.url(), response.code(), duration);

            // 记录性能指标
            recordHttpMetrics(request.method(), response.code(), duration);

            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("HTTP请求失败: {} {} ({}ms)", request.method(), request.url(), duration, e);
            
            // 记录错误指标
            recordHttpError(request.method(), e.getClass().getSimpleName());
            throw e;
        }
    }

    /**
     * 执行HTTP请求
     */
    protected CompletableFuture<String> executeRequest(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP请求失败: " + response.code() + " " + response.message());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new RuntimeException("响应体为空");
                }

                return body.string();
            } catch (IOException e) {
                throw new RuntimeException("HTTP请求异常", e);
            }
        });
    }

    /**
     * 执行带重试的HTTP请求
     */
    protected CompletableFuture<String> executeRequestWithRetry(Request request) {
        return executeRequestWithRetry(request, getMaxRetryTimes());
    }

    /**
     * 执行带重试的HTTP请求
     */
    protected CompletableFuture<String> executeRequestWithRetry(Request request, int maxRetries) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            
            for (int i = 0; i <= maxRetries; i++) {
                try {
                    return executeRequest(request).join();
                } catch (Exception e) {
                    lastException = e;
                    logger.warn("HTTP请求失败 (重试 {}/{}): {}", i, maxRetries, e.getMessage());
                    
                    if (i < maxRetries) {
                        try {
                            Thread.sleep(getRetryDelayMillis(i));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("请求被中断", ie);
                        }
                    }
                }
            }
            
            throw new RuntimeException("HTTP请求重试失败", lastException);
        });
    }

    /**
     * 构建POST请求
     */
    protected Request buildPostRequest(String url, String jsonBody) {
        RequestBody body = RequestBody.create(
                jsonBody, MediaType.get("application/json; charset=utf-8"));
        
        return new Request.Builder()
                .url(url)
                .post(body)
                .build();
    }

    /**
     * 构建GET请求
     */
    protected Request buildGetRequest(String url) {
        return new Request.Builder()
                .url(url)
                .get()
                .build();
    }

    // ========== 通用实现方法 ==========

    @Override
    public ChannelStatus getChannelStatus() {
        return channelStatus;
    }

    @Override
    public boolean isAvailable() {
        return channelStatus == ChannelStatus.NORMAL;
    }

    @Override
    public Map<String, Object> getChannelConfig() {
        return new HashMap<>(channelConfig);
    }

    @Override
    public void updateChannelConfig(Map<String, Object> config) {
        if (config != null) {
            this.channelConfig.putAll(config);
            logger.info("更新渠道配置: {}", getChannelType().getName());
        }
    }

    @Override
    public CompletableFuture<Boolean> healthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doHealthCheck();
            } catch (Exception e) {
                logger.error("渠道健康检查失败: {}", getChannelType().getName(), e);
                channelStatus = ChannelStatus.ERROR;
                return false;
            }
        });
    }

    // ========== 抽象方法和可重写方法 ==========

    /**
     * 执行健康检查
     */
    protected abstract boolean doHealthCheck();

    /**
     * 添加渠道特定的请求头
     */
    protected abstract void addChannelHeaders(Request.Builder builder);

    /**
     * 获取连接超时时间（秒）
     */
    protected int getConnectTimeoutSeconds() {
        return getConfigValue("connectTimeout", 10);
    }

    /**
     * 获取读取超时时间（秒）
     */
    protected int getReadTimeoutSeconds() {
        return getConfigValue("readTimeout", 30);
    }

    /**
     * 获取写入超时时间（秒）
     */
    protected int getWriteTimeoutSeconds() {
        return getConfigValue("writeTimeout", 30);
    }

    /**
     * 获取最大重试次数
     */
    protected int getMaxRetryTimes() {
        return getConfigValue("maxRetryTimes", 3);
    }

    /**
     * 获取重试延迟时间（毫秒）
     */
    protected long getRetryDelayMillis(int retryCount) {
        long baseDelay = getConfigValue("retryDelayMs", 1000);
        return baseDelay * (1L << retryCount); // 指数退避
    }

    /**
     * 获取User-Agent
     */
    protected String getUserAgent() {
        return "GameServer-Payment/1.0 (" + getChannelType().getCode() + ")";
    }

    /**
     * 获取配置值
     */
    @SuppressWarnings("unchecked")
    protected <T> T getConfigValue(String key, T defaultValue) {
        Object value = channelConfig.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 记录HTTP性能指标
     */
    protected void recordHttpMetrics(String method, int responseCode, long durationMs) {
        // TODO: 集成监控系统记录指标
        logger.debug("HTTP指标: {} -> {} ({}ms)", method, responseCode, durationMs);
    }

    /**
     * 记录HTTP错误指标
     */
    protected void recordHttpError(String method, String errorType) {
        // TODO: 集成监控系统记录错误
        logger.debug("HTTP错误: {} -> {}", method, errorType);
    }

    /**
     * 构建默认的支付结果
     */
    protected PaymentResult buildPaymentResult(boolean success, String channelOrderId, 
                                             String paymentData, String errorCode, String errorMessage) {
        return new PaymentResult() {
            @Override
            public boolean isSuccess() { return success; }
            
            @Override
            public String getChannelOrderId() { return channelOrderId; }
            
            @Override
            public String getPaymentData() { return paymentData; }
            
            @Override
            public String getErrorCode() { return errorCode; }
            
            @Override
            public String getErrorMessage() { return errorMessage; }
            
            @Override
            public Map<String, Object> getExtendData() { return new HashMap<>(); }
        };
    }

    /**
     * 构建默认的查询结果
     */
    protected QueryResult buildQueryResult(PaymentOrder.OrderStatus status, String channelOrderId,
                                         BigDecimal paidAmount, LocalDateTime payTime) {
        return new QueryResult() {
            @Override
            public PaymentOrder.OrderStatus getStatus() { return status; }
            
            @Override
            public String getChannelOrderId() { return channelOrderId; }
            
            @Override
            public BigDecimal getPaidAmount() { return paidAmount; }
            
            @Override
            public LocalDateTime getPayTime() { return payTime; }
            
            @Override
            public Map<String, Object> getExtendData() { return new HashMap<>(); }
        };
    }

    /**
     * 构建默认的退款结果
     */
    protected RefundResult buildRefundResult(boolean success, String channelRefundId,
                                           BigDecimal refundAmount, String errorCode, String errorMessage) {
        return new RefundResult() {
            @Override
            public boolean isSuccess() { return success; }
            
            @Override
            public String getChannelRefundId() { return channelRefundId; }
            
            @Override
            public BigDecimal getRefundAmount() { return refundAmount; }
            
            @Override
            public String getErrorCode() { return errorCode; }
            
            @Override
            public String getErrorMessage() { return errorMessage; }
            
            @Override
            public Map<String, Object> getExtendData() { return new HashMap<>(); }
        };
    }

    /**
     * 构建默认的回调结果
     */
    protected CallbackResult buildCallbackResult(boolean valid, String orderId, PaymentOrder.OrderStatus status,
                                                BigDecimal amount, LocalDateTime payTime, Map<String, Object> channelData) {
        return new CallbackResult() {
            @Override
            public boolean isValid() { return valid; }
            
            @Override
            public String getOrderId() { return orderId; }
            
            @Override
            public PaymentOrder.OrderStatus getStatus() { return status; }
            
            @Override
            public BigDecimal getAmount() { return amount; }
            
            @Override
            public LocalDateTime getPayTime() { return payTime; }
            
            @Override
            public Map<String, Object> getChannelData() { return channelData != null ? channelData : new HashMap<>(); }
        };
    }

    /**
     * 格式化金额（分转元）
     */
    protected String formatAmount(Long amountInFen) {
        if (amountInFen == null) {
            return "0.00";
        }
        return String.format("%.2f", amountInFen / 100.0);
    }

    /**
     * 解析金额（元转分）
     */
    protected Long parseAmount(String amountInYuan) {
        if (amountInYuan == null || amountInYuan.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Math.round(Double.parseDouble(amountInYuan) * 100);
        } catch (NumberFormatException e) {
            logger.warn("金额格式错误: {}", amountInYuan, e);
            return 0L;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "channelType=" + getChannelType() +
                ", status=" + channelStatus +
                '}';
    }
}