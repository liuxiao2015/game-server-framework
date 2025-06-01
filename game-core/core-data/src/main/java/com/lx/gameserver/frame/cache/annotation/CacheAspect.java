/*
 * 文件名: CacheAspect.java
 * 用途: 缓存切面
 * 实现内容:
 *   - AOP实现缓存注解处理
 *   - @Cacheable、@CacheEvict、@CachePut注解解析
 *   - 缓存操作的统一处理
 *   - 异常处理和性能监控
 *   - SpEL表达式解析和键生成
 * 技术选型:
 *   - Spring AOP
 *   - AspectJ注解
 *   - SpEL表达式引擎
 *   - 反射API
 * 依赖关系:
 *   - 处理缓存注解
 *   - 与缓存管理器集成
 *   - 提供声明式缓存功能
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.cache.annotation;

import com.lx.gameserver.frame.cache.annotation.CacheEvict.EvictTiming;
import com.lx.gameserver.frame.cache.core.Cache;
import com.lx.gameserver.frame.cache.core.CacheKey;
import com.lx.gameserver.frame.cache.local.LocalCacheManager;
import com.lx.gameserver.frame.cache.util.CacheKeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 缓存切面
 * <p>
 * 实现基于注解的声明式缓存功能，通过AOP拦截带有缓存注解的方法，
 * 自动进行缓存的读取、写入和清除操作。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Aspect
@Component
public class CacheAspect {

    private static final Logger logger = LoggerFactory.getLogger(CacheAspect.class);

    /**
     * SpEL表达式解析器
     */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 本地缓存管理器
     */
    @Autowired
    private LocalCacheManager cacheManager;

    /**
     * 处理@Cacheable注解
     *
     * @param joinPoint 连接点
     * @param cacheable 缓存注解
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(cacheable)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        
        // 检查条件
        if (!evaluateCondition(cacheable.condition(), method, args)) {
            logger.debug("缓存条件不满足，直接执行方法: {}", method.getName());
            return joinPoint.proceed();
        }

        // 生成缓存键
        CacheKey cacheKey = generateCacheKey(cacheable.key(), cacheable.keyGenerator(),
                method, args, cacheable.value());

        // 获取缓存
        Cache<CacheKey, Object> cache = getCache(cacheable.value());
        
        // 检查缓存
        Object cachedValue = cache.get(cacheKey);
        if (cachedValue != null) {
            logger.debug("缓存命中: method={}, key={}", method.getName(), cacheKey);
            return cachedValue;
        }

        // 检查unless条件（执行前）
        if (evaluateCondition(cacheable.unless(), method, args)) {
            logger.debug("unless条件满足，不使用缓存: {}", method.getName());
            return joinPoint.proceed();
        }

        // 执行方法
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            
            // 检查unless条件（执行后）
            if (!evaluateUnlessCondition(cacheable.unless(), method, args, result)) {
                // 存入缓存
                Duration expireTime = Duration.ofSeconds(cacheable.expireTime());
                if (cacheable.expireTime() > 0) {
                    cache.put(cacheKey, result, expireTime);
                } else {
                    cache.put(cacheKey, result);
                }
                
                long elapsedTime = System.currentTimeMillis() - startTime;
                logger.debug("缓存存储: method={}, key={}, elapsed={}ms", 
                    method.getName(), cacheKey, String.valueOf(elapsedTime));
            }
            
            return result;
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.error("方法执行失败: method={}, elapsed={}ms", method.getName(), elapsedTime, e);
            throw e;
        }
    }

    /**
     * 处理@CacheEvict注解
     *
     * @param joinPoint  连接点
     * @param cacheEvict 缓存清除注解
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(cacheEvict)")
    public Object handleCacheEvict(ProceedingJoinPoint joinPoint, CacheEvict cacheEvict) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        // 检查条件
        if (!evaluateCondition(cacheEvict.condition(), method, args)) {
            logger.debug("缓存清除条件不满足，直接执行方法: {}", method.getName());
            return joinPoint.proceed();
        }

        Object result = null;
        Exception methodException = null;

        // 根据timing决定执行顺序
        if (cacheEvict.timing() == CacheEvict.EvictTiming.BEFORE || 
            cacheEvict.timing() == CacheEvict.EvictTiming.BOTH) {
            performEviction(cacheEvict, method, args, null);
        }

        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            methodException = e;
            if (cacheEvict.timing() == CacheEvict.EvictTiming.BEFORE || 
                cacheEvict.timing() == CacheEvict.EvictTiming.BOTH) {
                // 如果是方法执行前清除，且方法执行失败，则抛出异常
                throw e;
            }
        }

        // 方法执行后清除缓存
        if (cacheEvict.timing() == CacheEvict.EvictTiming.AFTER || 
            cacheEvict.timing() == CacheEvict.EvictTiming.BOTH) {
            performEviction(cacheEvict, method, args, result);
        }

        if (methodException != null) {
            throw methodException;
        }

        return result;
    }

    /**
     * 处理@CachePut注解
     *
     * @param joinPoint 连接点
     * @param cachePut  缓存更新注解
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(cachePut)")
    public Object handleCachePut(ProceedingJoinPoint joinPoint, CachePut cachePut) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        // 检查条件
        if (!evaluateCondition(cachePut.condition(), method, args)) {
            logger.debug("缓存更新条件不满足，直接执行方法: {}", method.getName());
            return joinPoint.proceed();
        }

        // 执行方法
        long startTime = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.error("方法执行失败: method={}, elapsed={}ms", method.getName(), elapsedTime, e);
            throw e;
        }

        // 检查unless条件
        if (!evaluateUnlessCondition(cachePut.unless(), method, args, result)) {
            // 生成缓存键
            CacheKey cacheKey = generateCacheKey(cachePut.key(), cachePut.keyGenerator(),
                    method, args, cachePut.value());

            // 更新缓存
            Cache<CacheKey, Object> cache = getCache(cachePut.value());
            Duration expireTime = Duration.ofSeconds(cachePut.expireTime());
            if (cachePut.expireTime() > 0) {
                cache.put(cacheKey, result, expireTime);
            } else {
                cache.put(cacheKey, result);
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.debug("缓存更新: method={}, key={}, elapsed={}ms", 
                method.getName(), cacheKey, String.valueOf(elapsedTime));
        }

        return result;
    }

    /**
     * 执行缓存清除
     */
    private void performEviction(CacheEvict cacheEvict, Method method, Object[] args, Object result) {
        // 生成缓存键
        if (cacheEvict.allEntries()) {
            // 清除所有条目
            for (String cacheName : cacheEvict.value()) {
                Cache<CacheKey, Object> cache = getCache(cacheName);
                cache.clear();
                logger.debug("清除所有缓存: cache={}, method={}", cacheName, method.getName());
            }
        } else {
            // 清除指定条目
            CacheKey cacheKey = generateCacheKey(cacheEvict.key(), cacheEvict.keyGenerator(),
                    method, args, cacheEvict.value());
            for (String cacheName : cacheEvict.value()) {
                Cache<CacheKey, Object> cache = getCache(cacheName);
                cache.remove(cacheKey);
                logger.debug("清除缓存条目: cache={}, key={}, method={}", 
                    cacheName, cacheKey, method.getName());
            }
        }
    }

    /**
     * 生成缓存键
     */
    private CacheKey generateCacheKey(String keyExpression, String keyGeneratorName,
                                     Method method, Object[] args, String[] cacheNames) {
        if (StringUtils.hasText(keyExpression)) {
            // 使用SpEL表达式生成键
            EvaluationContext context = createEvaluationContext(method, args);
            Expression expression = expressionParser.parseExpression(keyExpression);
            Object keyValue = expression.getValue(context);
            return CacheKey.of(String.valueOf(keyValue));
        } else if (StringUtils.hasText(keyGeneratorName)) {
            // 使用指定的键生成器
            // 这里简化处理，实际应该从Spring容器中获取指定的键生成器
            CacheKeyGenerator.KeyGenerator generator = new CacheKeyGenerator.DefaultKeyGenerator();
            String keyStr = generator.generate(null, method, args);
            return CacheKey.of(keyStr);
        } else {
            // 使用默认键生成器
            CacheKeyGenerator.KeyGenerator generator = new CacheKeyGenerator.DefaultKeyGenerator();
            String keyStr = generator.generate(null, method, args);
            return CacheKey.of(keyStr);
        }
    }

    /**
     * 创建SpEL表达式上下文
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 添加方法参数
        String[] paramNames = getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            if (i < paramNames.length) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        
        // 添加方法信息
        context.setVariable("method", method);
        context.setVariable("target", method.getDeclaringClass());
        
        return context;
    }

    /**
     * 获取方法参数名
     */
    private String[] getParameterNames(Method method) {
        // 这里简化处理，实际应该使用参数名发现器
        String[] names = new String[method.getParameterCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = "arg" + i;
        }
        return names;
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String conditionExpression, Method method, Object[] args) {
        if (!StringUtils.hasText(conditionExpression)) {
            return true;
        }
        
        try {
            EvaluationContext context = createEvaluationContext(method, args);
            Expression expression = expressionParser.parseExpression(conditionExpression);
            Boolean result = expression.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            logger.warn("条件表达式评估失败: {}", conditionExpression, e);
            return false;
        }
    }

    /**
     * 评估unless条件表达式
     */
    private boolean evaluateUnlessCondition(String unlessExpression, Method method, Object[] args, Object result) {
        if (!StringUtils.hasText(unlessExpression)) {
            return false;
        }
        
        try {
            EvaluationContext context = createEvaluationContext(method, args);
            context.setVariable("result", result);
            Expression expression = expressionParser.parseExpression(unlessExpression);
            Boolean unlessResult = expression.getValue(context, Boolean.class);
            return unlessResult != null && unlessResult;
        } catch (Exception e) {
            logger.warn("unless表达式评估失败: {}", unlessExpression, e);
            return false;
        }
    }

    /**
     * 解析持续时间
     */
    private Duration parseDuration(String durationStr) {
        if (!StringUtils.hasText(durationStr)) {
            return null;
        }
        
        try {
            return Duration.parse(durationStr);
        } catch (Exception e) {
            logger.warn("持续时间解析失败: {}", durationStr, e);
            return null;
        }
    }

    /**
     * 获取缓存实例
     */
    private Cache<CacheKey, Object> getCache(String cacheName) {
        if (!StringUtils.hasText(cacheName)) {
            cacheName = "default";
        }
        return cacheManager.getCache(cacheName);
    }

    /**
     * 获取缓存实例（多个缓存名）
     */
    private Cache<CacheKey, Object> getCache(String[] cacheNames) {
        if (cacheNames == null || cacheNames.length == 0) {
            return getCache("default");
        }
        return getCache(cacheNames[0]);
    }
}