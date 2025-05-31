/*
 * 文件名: CaptchaService.java
 * 用途: 验证码服务
 * 实现内容:
 *   - 图形验证码生成
 *   - 滑块验证码支持
 *   - 行为验证码分析
 *   - 验证码缓存管理
 *   - 防机器人检测
 * 技术选型:
 *   - Java Graphics2D图形生成
 *   - Redis验证码缓存
 *   - 行为模式分析
 * 依赖关系:
 *   - 依赖Redis缓存
 *   - 被登录认证模块使用
 */
package com.lx.gameserver.frame.security.integration;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务
 * <p>
 * 提供多种类型的验证码生成和验证功能，包括图形验证码、
 * 滑块验证码、行为验证码等，用于防止机器人攻击。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Service
public class CaptchaService {
    
    /**
     * 验证码缓存前缀
     */
    private static final String CAPTCHA_PREFIX = "captcha:";
    
    /**
     * 行为验证码前缀
     */
    private static final String BEHAVIOR_PREFIX = "behavior:";
    
    /**
     * 滑块验证码前缀
     */
    private static final String SLIDER_PREFIX = "slider:";
    
    /**
     * 默认验证码长度
     */
    private static final int DEFAULT_CODE_LENGTH = 4;
    
    /**
     * 默认图片宽度
     */
    private static final int DEFAULT_WIDTH = 120;
    
    /**
     * 默认图片高度
     */
    private static final int DEFAULT_HEIGHT = 40;
    
    /**
     * 验证码有效期（分钟）
     */
    private static final int CAPTCHA_EXPIRE_MINUTES = 5;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 本地验证码缓存（用于没有Redis的环境）
     */
    private final Map<String, CaptchaInfo> localCache = new HashMap<>();

    /**
     * 生成图形验证码
     *
     * @param sessionId 会话ID
     * @return 验证码结果
     */
    public CaptchaResult generateImageCaptcha(String sessionId) {
        return generateImageCaptcha(sessionId, DEFAULT_CODE_LENGTH, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 生成图形验证码
     *
     * @param sessionId 会话ID
     * @param length 验证码长度
     * @param width 图片宽度
     * @param height 图片高度
     * @return 验证码结果
     */
    public CaptchaResult generateImageCaptcha(String sessionId, int length, int width, int height) {
        if (!StringUtils.hasText(sessionId)) {
            log.warn("生成验证码失败：会话ID为空");
            return null;
        }
        
        try {
            // 生成验证码字符串
            String code = generateRandomCode(length);
            
            // 创建图片
            BufferedImage image = createCaptchaImage(code, width, height);
            
            // 转换为字节数组
            byte[] imageBytes = imageToBytes(image);
            
            // 缓存验证码
            cacheCaptcha(sessionId, code, CaptchaType.IMAGE);
            
            log.debug("生成图形验证码成功: sessionId={}, code={}", sessionId, code);
            
            return CaptchaResult.builder()
                    .sessionId(sessionId)
                    .type(CaptchaType.IMAGE)
                    .imageData(imageBytes)
                    .build();
            
        } catch (Exception e) {
            log.error("生成图形验证码失败: sessionId=" + sessionId, e);
            return null;
        }
    }

    /**
     * 生成滑块验证码
     *
     * @param sessionId 会话ID
     * @return 验证码结果
     */
    public CaptchaResult generateSliderCaptcha(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            log.warn("生成滑块验证码失败：会话ID为空");
            return null;
        }
        
        try {
            // 生成背景图片和滑块位置
            int backgroundWidth = 300;
            int backgroundHeight = 150;
            int sliderSize = 50;
            
            // 随机生成滑块位置
            int targetX = ThreadLocalRandom.current().nextInt(sliderSize, backgroundWidth - sliderSize);
            int targetY = ThreadLocalRandom.current().nextInt(sliderSize, backgroundHeight - sliderSize);
            
            // 创建背景图片
            BufferedImage backgroundImage = createBackgroundImage(backgroundWidth, backgroundHeight);
            
            // 创建滑块图片
            BufferedImage sliderImage = createSliderImage(backgroundImage, targetX, targetY, sliderSize);
            
            // 在背景图上创建滑块缺口
            createSliderGap(backgroundImage, targetX, targetY, sliderSize);
            
            // 转换为字节数组
            byte[] backgroundBytes = imageToBytes(backgroundImage);
            byte[] sliderBytes = imageToBytes(sliderImage);
            
            // 缓存滑块信息
            SliderInfo sliderInfo = SliderInfo.builder()
                    .targetX(targetX)
                    .targetY(targetY)
                    .sliderSize(sliderSize)
                    .tolerance(5) // 允许5像素误差
                    .build();
            
            cacheSliderInfo(sessionId, sliderInfo);
            
            log.debug("生成滑块验证码成功: sessionId={}, targetX={}, targetY={}", sessionId, targetX, targetY);
            
            return CaptchaResult.builder()
                    .sessionId(sessionId)
                    .type(CaptchaType.SLIDER)
                    .imageData(backgroundBytes)
                    .sliderData(sliderBytes)
                    .build();
            
        } catch (Exception e) {
            log.error("生成滑块验证码失败: sessionId=" + sessionId, e);
            return null;
        }
    }

    /**
     * 验证图形验证码
     *
     * @param sessionId 会话ID
     * @param inputCode 用户输入的验证码
     * @return 是否验证通过
     */
    public boolean verifyImageCaptcha(String sessionId, String inputCode) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(inputCode)) {
            return false;
        }
        
        try {
            CaptchaInfo cachedInfo = getCachedCaptcha(sessionId);
            if (cachedInfo == null || cachedInfo.getType() != CaptchaType.IMAGE) {
                log.debug("验证码不存在或类型不匹配: sessionId={}", sessionId);
                return false;
            }
            
            // 验证码比较（忽略大小写）
            boolean isValid = inputCode.equalsIgnoreCase(cachedInfo.getCode());
            
            if (isValid) {
                // 验证成功后删除验证码
                removeCachedCaptcha(sessionId);
                log.debug("图形验证码验证成功: sessionId={}", sessionId);
            } else {
                log.debug("图形验证码验证失败: sessionId={}, input={}, expected={}", 
                         sessionId, inputCode, cachedInfo.getCode());
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("验证图形验证码失败: sessionId=" + sessionId, e);
            return false;
        }
    }

    /**
     * 验证滑块验证码
     *
     * @param sessionId 会话ID
     * @param userX 用户滑动的X坐标
     * @param userY 用户滑动的Y坐标
     * @return 是否验证通过
     */
    public boolean verifySliderCaptcha(String sessionId, int userX, int userY) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        
        try {
            SliderInfo sliderInfo = getCachedSliderInfo(sessionId);
            if (sliderInfo == null) {
                log.debug("滑块验证码不存在: sessionId={}", sessionId);
                return false;
            }
            
            // 检查坐标是否在容忍范围内
            boolean isXValid = Math.abs(userX - sliderInfo.getTargetX()) <= sliderInfo.getTolerance();
            boolean isYValid = Math.abs(userY - sliderInfo.getTargetY()) <= sliderInfo.getTolerance();
            boolean isValid = isXValid && isYValid;
            
            if (isValid) {
                // 验证成功后删除验证码
                removeCachedSliderInfo(sessionId);
                log.debug("滑块验证码验证成功: sessionId={}", sessionId);
            } else {
                log.debug("滑块验证码验证失败: sessionId={}, userX={}, userY={}, targetX={}, targetY={}", 
                         sessionId, userX, userY, sliderInfo.getTargetX(), sliderInfo.getTargetY());
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("验证滑块验证码失败: sessionId=" + sessionId, e);
            return false;
        }
    }

    /**
     * 记录行为验证码数据
     *
     * @param sessionId 会话ID
     * @param behaviorData 行为数据
     */
    public void recordBehaviorData(String sessionId, BehaviorData behaviorData) {
        if (!StringUtils.hasText(sessionId) || behaviorData == null) {
            return;
        }
        
        try {
            String behaviorKey = BEHAVIOR_PREFIX + sessionId;
            if (redisTemplate != null) {
                redisTemplate.opsForList().rightPush(behaviorKey, behaviorData);
                redisTemplate.expire(behaviorKey, Duration.ofMinutes(CAPTCHA_EXPIRE_MINUTES));
            }
            
            log.debug("记录行为数据: sessionId={}, type={}", sessionId, behaviorData.getType());
            
        } catch (Exception e) {
            log.error("记录行为数据失败: sessionId=" + sessionId, e);
        }
    }

    /**
     * 验证行为验证码
     *
     * @param sessionId 会话ID
     * @return 是否通过行为验证
     */
    public boolean verifyBehaviorCaptcha(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        
        try {
            String behaviorKey = BEHAVIOR_PREFIX + sessionId;
            List<BehaviorData> behaviorList = new ArrayList<>();
            
            if (redisTemplate != null) {
                List<Object> rawData = redisTemplate.opsForList().range(behaviorKey, 0, -1);
                if (rawData != null) {
                    for (Object data : rawData) {
                        if (data instanceof BehaviorData) {
                            behaviorList.add((BehaviorData) data);
                        }
                    }
                }
            }
            
            if (behaviorList.isEmpty()) {
                log.debug("无行为数据: sessionId={}", sessionId);
                return false;
            }
            
            // 分析行为模式
            boolean isHuman = analyzeBehaviorPattern(behaviorList);
            
            if (isHuman) {
                // 验证成功后删除行为数据
                if (redisTemplate != null) {
                    redisTemplate.delete(behaviorKey);
                }
                log.debug("行为验证通过: sessionId={}", sessionId);
            } else {
                log.debug("行为验证失败: sessionId={}", sessionId);
            }
            
            return isHuman;
            
        } catch (Exception e) {
            log.error("验证行为验证码失败: sessionId=" + sessionId, e);
            return false;
        }
    }

    /**
     * 清理过期的验证码
     */
    public void cleanupExpiredCaptchas() {
        try {
            if (redisTemplate == null) {
                // 清理本地缓存中的过期验证码
                long now = System.currentTimeMillis();
                localCache.entrySet().removeIf(entry -> 
                    now - entry.getValue().getCreateTime() > CAPTCHA_EXPIRE_MINUTES * 60 * 1000);
            }
            // Redis的过期策略会自动清理过期数据
            
        } catch (Exception e) {
            log.error("清理过期验证码失败", e);
        }
    }

    /**
     * 生成随机验证码
     *
     * @param length 长度
     * @return 验证码字符串
     */
    private String generateRandomCode(int length) {
        String chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // 避免易混淆字符
        StringBuilder code = new StringBuilder();
        Random random = ThreadLocalRandom.current();
        
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return code.toString();
    }

    /**
     * 创建验证码图片
     *
     * @param code 验证码字符串
     * @param width 图片宽度
     * @param height 图片高度
     * @return 验证码图片
     */
    private BufferedImage createCaptchaImage(String code, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 填充背景
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, width, height);
        
        // 添加噪点
        Random random = ThreadLocalRandom.current();
        for (int i = 0; i < 50; i++) {
            g2d.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            g2d.fillOval(random.nextInt(width), random.nextInt(height), 2, 2);
        }
        
        // 绘制验证码字符
        Font font = new Font("Arial", Font.BOLD, 24);
        g2d.setFont(font);
        
        int charWidth = width / code.length();
        for (int i = 0; i < code.length(); i++) {
            // 随机颜色
            g2d.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            
            // 随机位置和角度
            int x = i * charWidth + random.nextInt(10);
            int y = height / 2 + random.nextInt(10);
            
            // 旋转字符
            double angle = (random.nextDouble() - 0.5) * 0.5;
            g2d.rotate(angle, x, y);
            g2d.drawString(String.valueOf(code.charAt(i)), x, y);
            g2d.rotate(-angle, x, y);
        }
        
        // 添加干扰线
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            g2d.drawLine(random.nextInt(width), random.nextInt(height), 
                        random.nextInt(width), random.nextInt(height));
        }
        
        g2d.dispose();
        return image;
    }

    /**
     * 创建背景图片
     *
     * @param width 宽度
     * @param height 高度
     * @return 背景图片
     */
    private BufferedImage createBackgroundImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // 渐变背景
        GradientPaint gradient = new GradientPaint(0, 0, Color.LIGHT_GRAY, width, height, Color.WHITE);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);
        
        // 添加纹理
        Random random = ThreadLocalRandom.current();
        for (int i = 0; i < 100; i++) {
            g2d.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255), 50));
            g2d.fillOval(random.nextInt(width), random.nextInt(height), 
                        random.nextInt(10), random.nextInt(10));
        }
        
        g2d.dispose();
        return image;
    }

    /**
     * 创建滑块图片
     *
     * @param backgroundImage 背景图片
     * @param x X坐标
     * @param y Y坐标
     * @param size 滑块大小
     * @return 滑块图片
     */
    private BufferedImage createSliderImage(BufferedImage backgroundImage, int x, int y, int size) {
        BufferedImage sliderImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = sliderImage.createGraphics();
        
        // 复制背景图片的对应区域
        g2d.drawImage(backgroundImage.getSubimage(x, y, size, size), 0, 0, null);
        
        // 添加滑块边框
        g2d.setColor(Color.BLUE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(0, 0, size - 1, size - 1);
        
        g2d.dispose();
        return sliderImage;
    }

    /**
     * 在背景图上创建滑块缺口
     *
     * @param backgroundImage 背景图片
     * @param x X坐标
     * @param y Y坐标
     * @param size 缺口大小
     */
    private void createSliderGap(BufferedImage backgroundImage, int x, int y, int size) {
        Graphics2D g2d = backgroundImage.createGraphics();
        
        // 创建缺口
        g2d.setColor(Color.GRAY);
        g2d.fillRect(x, y, size, size);
        
        // 添加缺口边框
        g2d.setColor(Color.DARK_GRAY);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(x, y, size, size);
        
        g2d.dispose();
    }

    /**
     * 图片转字节数组
     *
     * @param image 图片
     * @return 字节数组
     * @throws IOException IO异常
     */
    private byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * 缓存验证码
     *
     * @param sessionId 会话ID
     * @param code 验证码
     * @param type 类型
     */
    private void cacheCaptcha(String sessionId, String code, CaptchaType type) {
        String captchaKey = CAPTCHA_PREFIX + sessionId;
        CaptchaInfo captchaInfo = CaptchaInfo.builder()
                .code(code)
                .type(type)
                .createTime(System.currentTimeMillis())
                .build();
        
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(captchaKey, captchaInfo, Duration.ofMinutes(CAPTCHA_EXPIRE_MINUTES));
        } else {
            localCache.put(sessionId, captchaInfo);
        }
    }

    /**
     * 获取缓存的验证码
     *
     * @param sessionId 会话ID
     * @return 验证码信息
     */
    @Nullable
    private CaptchaInfo getCachedCaptcha(String sessionId) {
        String captchaKey = CAPTCHA_PREFIX + sessionId;
        
        if (redisTemplate != null) {
            Object cached = redisTemplate.opsForValue().get(captchaKey);
            return cached instanceof CaptchaInfo ? (CaptchaInfo) cached : null;
        } else {
            return localCache.get(sessionId);
        }
    }

    /**
     * 删除缓存的验证码
     *
     * @param sessionId 会话ID
     */
    private void removeCachedCaptcha(String sessionId) {
        String captchaKey = CAPTCHA_PREFIX + sessionId;
        
        if (redisTemplate != null) {
            redisTemplate.delete(captchaKey);
        } else {
            localCache.remove(sessionId);
        }
    }

    /**
     * 缓存滑块信息
     *
     * @param sessionId 会话ID
     * @param sliderInfo 滑块信息
     */
    private void cacheSliderInfo(String sessionId, SliderInfo sliderInfo) {
        String sliderKey = SLIDER_PREFIX + sessionId;
        
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(sliderKey, sliderInfo, Duration.ofMinutes(CAPTCHA_EXPIRE_MINUTES));
        }
    }

    /**
     * 获取缓存的滑块信息
     *
     * @param sessionId 会话ID
     * @return 滑块信息
     */
    @Nullable
    private SliderInfo getCachedSliderInfo(String sessionId) {
        String sliderKey = SLIDER_PREFIX + sessionId;
        
        if (redisTemplate != null) {
            Object cached = redisTemplate.opsForValue().get(sliderKey);
            return cached instanceof SliderInfo ? (SliderInfo) cached : null;
        }
        
        return null;
    }

    /**
     * 删除缓存的滑块信息
     *
     * @param sessionId 会话ID
     */
    private void removeCachedSliderInfo(String sessionId) {
        String sliderKey = SLIDER_PREFIX + sessionId;
        
        if (redisTemplate != null) {
            redisTemplate.delete(sliderKey);
        }
    }

    /**
     * 分析行为模式
     *
     * @param behaviorList 行为数据列表
     * @return 是否为人类行为
     */
    private boolean analyzeBehaviorPattern(List<BehaviorData> behaviorList) {
        if (behaviorList.size() < 3) {
            return false; // 行为数据太少
        }
        
        // 简单的行为分析逻辑
        long totalTime = 0;
        int moveCount = 0;
        int clickCount = 0;
        
        for (BehaviorData data : behaviorList) {
            totalTime += data.getTimestamp();
            if ("mousemove".equals(data.getType())) {
                moveCount++;
            } else if ("click".equals(data.getType())) {
                clickCount++;
            }
        }
        
        // 检查是否有足够的鼠标移动和点击
        boolean hasEnoughMovement = moveCount >= 5;
        boolean hasClick = clickCount >= 1;
        boolean timeReasonable = totalTime > 1000 && totalTime < 30000; // 1-30秒
        
        return hasEnoughMovement && hasClick && timeReasonable;
    }

    /**
     * 验证码类型枚举
     */
    public enum CaptchaType {
        IMAGE, SLIDER, BEHAVIOR
    }

    /**
     * 验证码信息
     */
    @Data
    @Builder
    private static class CaptchaInfo {
        private String code;
        private CaptchaType type;
        private long createTime;
    }

    /**
     * 滑块信息
     */
    @Data
    @Builder
    private static class SliderInfo {
        private int targetX;
        private int targetY;
        private int sliderSize;
        private int tolerance;
    }

    /**
     * 验证码结果
     */
    @Data
    @Builder
    public static class CaptchaResult {
        private String sessionId;
        private CaptchaType type;
        private byte[] imageData;
        private byte[] sliderData;
    }

    /**
     * 行为数据
     */
    @Data
    @Builder
    public static class BehaviorData {
        private String type; // mousemove, click, keypress等
        private int x;
        private int y;
        private long timestamp;
        private Map<String, Object> extraData;
    }
}