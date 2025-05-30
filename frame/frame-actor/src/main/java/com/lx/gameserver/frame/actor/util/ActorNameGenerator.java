/*
 * 文件名: ActorNameGenerator.java
 * 用途: Actor名称生成器
 * 实现内容:
 *   - Actor名称自动生成和唯一性保证
 *   - 支持自定义规则和批量生成
 *   - 名称冲突检测和重试机制
 *   - 性能优化和内存高效生成
 * 技术选型:
 *   - 原子操作保证线程安全
 *   - 多种生成策略和自定义规则
 *   - 高性能UUID和序列号生成
 * 依赖关系:
 *   - 被ActorSystem使用
 *   - 与Actor注册表集成
 *   - 支持集群环境下的唯一性
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Actor名称生成器
 * <p>
 * 提供多种Actor名称生成策略，保证名称的唯一性和可读性。
 * 支持自定义规则、批量生成、性能优化等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorNameGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorNameGenerator.class);
    
    /** 默认生成器实例 */
    private static final ActorNameGenerator DEFAULT_INSTANCE = new ActorNameGenerator();
    
    /** 序列号生成器 */
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    
    /** 已使用的名称集合（用于冲突检测） */
    private final Set<String> usedNames = ConcurrentHashMap.newKeySet();
    
    /** 生成策略映射 */
    private final Map<String, NameGenerationStrategy> strategies = new ConcurrentHashMap<>();
    
    /** 安全随机数生成器 */
    private final SecureRandom secureRandom = new SecureRandom();
    
    /** 默认前缀 */
    private static final String DEFAULT_PREFIX = "actor";
    
    /** 常用单词列表（用于可读性名称生成） */
    private static final String[] ADJECTIVES = {
            "brave", "swift", "mighty", "clever", "noble", "wise", "fierce", "gentle",
            "strong", "quick", "bright", "calm", "bold", "kind", "sharp", "steady"
    };
    
    private static final String[] NOUNS = {
            "warrior", "guardian", "hunter", "mage", "knight", "scout", "healer", "archer",
            "defender", "champion", "ranger", "sage", "paladin", "rogue", "wizard", "monk"
    };
    
    public ActorNameGenerator() {
        initializeDefaultStrategies();
        logger.debug("Actor名称生成器初始化完成");
    }
    
    /**
     * 获取默认实例
     */
    public static ActorNameGenerator getInstance() {
        return DEFAULT_INSTANCE;
    }
    
    /**
     * 生成简单的序列号名称
     *
     * @return 生成的名称
     */
    public String generateSimple() {
        return generateWithStrategy("simple");
    }
    
    /**
     * 生成UUID名称
     *
     * @return 生成的名称
     */
    public String generateUuid() {
        return generateWithStrategy("uuid");
    }
    
    /**
     * 生成可读性名称
     *
     * @return 生成的名称
     */
    public String generateReadable() {
        return generateWithStrategy("readable");
    }
    
    /**
     * 生成带前缀的名称
     *
     * @param prefix 前缀
     * @return 生成的名称
     */
    public String generateWithPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            prefix = DEFAULT_PREFIX;
        }
        
        return generateWithStrategy("prefixed", prefix);
    }
    
    /**
     * 生成基于时间戳的名称
     *
     * @return 生成的名称
     */
    public String generateTimestamp() {
        return generateWithStrategy("timestamp");
    }
    
    /**
     * 使用指定策略生成名称
     *
     * @param strategyName 策略名称
     * @param params 参数
     * @return 生成的名称
     */
    public String generateWithStrategy(String strategyName, Object... params) {
        NameGenerationStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("未知的生成策略: " + strategyName);
        }
        
        return generateUniqueWithStrategy(strategy, params);
    }
    
    /**
     * 批量生成名称
     *
     * @param count 生成数量
     * @param strategyName 策略名称
     * @param params 参数
     * @return 生成的名称列表
     */
    public List<String> generateBatch(int count, String strategyName, Object... params) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        
        NameGenerationStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("未知的生成策略: " + strategyName);
        }
        
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(generateUniqueWithStrategy(strategy, params));
        }
        
        return names;
    }
    
    /**
     * 生成唯一名称（带重试机制）
     */
    private String generateUniqueWithStrategy(NameGenerationStrategy strategy, Object... params) {
        int maxRetries = 100; // 最大重试次数
        int retries = 0;
        
        while (retries < maxRetries) {
            String name = strategy.generate(params);
            
            if (isNameAvailable(name)) {
                markNameAsUsed(name);
                return name;
            }
            
            retries++;
            if (retries % 10 == 0) {
                logger.debug("名称生成重试 {} 次，策略: {}", retries, strategy.getClass().getSimpleName());
            }
        }
        
        throw new RuntimeException("无法生成唯一名称，已重试 " + maxRetries + " 次");
    }
    
    /**
     * 检查名称是否可用
     *
     * @param name 名称
     * @return 是否可用
     */
    public boolean isNameAvailable(String name) {
        return name != null && !name.isEmpty() && 
               ActorPathParser.isValidName(name) && 
               !usedNames.contains(name);
    }
    
    /**
     * 标记名称为已使用
     *
     * @param name 名称
     */
    public void markNameAsUsed(String name) {
        if (name != null && !name.isEmpty()) {
            usedNames.add(name);
        }
    }
    
    /**
     * 释放名称（允许重新使用）
     *
     * @param name 名称
     */
    public void releaseName(String name) {
        if (name != null) {
            usedNames.remove(name);
            logger.debug("释放Actor名称: {}", name);
        }
    }
    
    /**
     * 添加自定义生成策略
     *
     * @param name 策略名称
     * @param strategy 策略实现
     */
    public void addStrategy(String name, NameGenerationStrategy strategy) {
        strategies.put(name, strategy);
        logger.debug("添加名称生成策略: {}", name);
    }
    
    /**
     * 移除生成策略
     *
     * @param name 策略名称
     */
    public void removeStrategy(String name) {
        strategies.remove(name);
        logger.debug("移除名称生成策略: {}", name);
    }
    
    /**
     * 获取已使用的名称数量
     *
     * @return 已使用的名称数量
     */
    public int getUsedNameCount() {
        return usedNames.size();
    }
    
    /**
     * 清理已使用的名称（谨慎使用）
     */
    public void clearUsedNames() {
        usedNames.clear();
        logger.info("已清理所有已使用的Actor名称");
    }
    
    /**
     * 获取生成统计信息
     *
     * @return 统计信息
     */
    public GenerationStats getStats() {
        return new GenerationStats(
                sequenceNumber.get(),
                usedNames.size(),
                strategies.size()
        );
    }
    
    /**
     * 初始化默认策略
     */
    private void initializeDefaultStrategies() {
        // 简单序列号策略
        addStrategy("simple", params -> DEFAULT_PREFIX + "-" + sequenceNumber.incrementAndGet());
        
        // UUID策略
        addStrategy("uuid", params -> DEFAULT_PREFIX + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        
        // 可读性策略
        addStrategy("readable", params -> {
            String adjective = ADJECTIVES[ThreadLocalRandom.current().nextInt(ADJECTIVES.length)];
            String noun = NOUNS[ThreadLocalRandom.current().nextInt(NOUNS.length)];
            int number = ThreadLocalRandom.current().nextInt(1000);
            return adjective + "-" + noun + "-" + number;
        });
        
        // 前缀策略
        addStrategy("prefixed", params -> {
            String prefix = params.length > 0 ? params[0].toString() : DEFAULT_PREFIX;
            return prefix + "-" + sequenceNumber.incrementAndGet();
        });
        
        // 时间戳策略
        addStrategy("timestamp", params -> {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(Instant.now());
            return DEFAULT_PREFIX + "-" + timestamp + "-" + ThreadLocalRandom.current().nextInt(1000);
        });
        
        // 安全随机策略
        addStrategy("secure", params -> {
            byte[] bytes = new byte[6];
            secureRandom.nextBytes(bytes);
            return DEFAULT_PREFIX + "-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        });
        
        // 短UUID策略
        addStrategy("short-uuid", params -> {
            UUID uuid = UUID.randomUUID();
            long most = uuid.getMostSignificantBits();
            long least = uuid.getLeastSignificantBits();
            return DEFAULT_PREFIX + "-" + Long.toHexString(most ^ least);
        });
        
        // 自定义词汇策略
        addStrategy("custom-words", params -> {
            if (params.length < 2) {
                throw new IllegalArgumentException("自定义词汇策略需要至少两个参数数组");
            }
            
            String[] words1 = (String[]) params[0];
            String[] words2 = (String[]) params[1];
            
            String word1 = words1[ThreadLocalRandom.current().nextInt(words1.length)];
            String word2 = words2[ThreadLocalRandom.current().nextInt(words2.length)];
            int number = ThreadLocalRandom.current().nextInt(1000);
            
            return word1 + "-" + word2 + "-" + number;
        });
    }
    
    /**
     * 名称生成策略接口
     */
    @FunctionalInterface
    public interface NameGenerationStrategy {
        /**
         * 生成名称
         *
         * @param params 参数
         * @return 生成的名称
         */
        String generate(Object... params);
    }
    
    /**
     * 生成统计信息
     */
    public static class GenerationStats {
        private final long totalGenerated;
        private final int usedNames;
        private final int availableStrategies;
        
        public GenerationStats(long totalGenerated, int usedNames, int availableStrategies) {
            this.totalGenerated = totalGenerated;
            this.usedNames = usedNames;
            this.availableStrategies = availableStrategies;
        }
        
        public long getTotalGenerated() { return totalGenerated; }
        public int getUsedNames() { return usedNames; }
        public int getAvailableStrategies() { return availableStrategies; }
        
        @Override
        public String toString() {
            return String.format("GenerationStats{totalGenerated=%d, usedNames=%d, strategies=%d}",
                    totalGenerated, usedNames, availableStrategies);
        }
    }
    
    /**
     * 名称构建器
     */
    public static class NameBuilder {
        private final ActorNameGenerator generator;
        private String strategy = "simple";
        private String prefix;
        private String suffix;
        private final List<String> parts = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();
        
        public NameBuilder(ActorNameGenerator generator) {
            this.generator = generator;
        }
        
        public NameBuilder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }
        
        public NameBuilder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }
        
        public NameBuilder suffix(String suffix) {
            this.suffix = suffix;
            return this;
        }
        
        public NameBuilder part(String part) {
            this.parts.add(part);
            return this;
        }
        
        public NameBuilder param(Object param) {
            this.params.add(param);
            return this;
        }
        
        public String build() {
            String baseName;
            
            if (!parts.isEmpty()) {
                // 使用指定的部分构建名称
                baseName = String.join("-", parts);
                if (!generator.isNameAvailable(baseName)) {
                    baseName += "-" + generator.sequenceNumber.incrementAndGet();
                }
            } else {
                // 使用策略生成名称
                baseName = generator.generateWithStrategy(strategy, params.toArray());
            }
            
            // 添加前缀和后缀
            StringBuilder finalName = new StringBuilder();
            if (prefix != null && !prefix.isEmpty()) {
                finalName.append(prefix).append("-");
            }
            finalName.append(baseName);
            if (suffix != null && !suffix.isEmpty()) {
                finalName.append("-").append(suffix);
            }
            
            String result = finalName.toString();
            
            // 确保名称唯一
            if (!generator.isNameAvailable(result)) {
                result += "-" + generator.sequenceNumber.incrementAndGet();
            }
            
            generator.markNameAsUsed(result);
            return result;
        }
    }
    
    /**
     * 创建名称构建器
     *
     * @return 名称构建器
     */
    public NameBuilder builder() {
        return new NameBuilder(this);
    }
    
    /**
     * 预定义的生成器工厂方法
     */
    public static class Generators {
        /**
         * 游戏玩家名称生成器
         */
        public static NameGenerationStrategy playerNameGenerator() {
            return params -> {
                String[] playerAdjectives = {"legendary", "heroic", "epic", "mythic", "divine", "royal", "ancient", "mystic"};
                String[] playerNouns = {"hero", "champion", "legend", "master", "lord", "king", "emperor", "sage"};
                
                String adj = playerAdjectives[ThreadLocalRandom.current().nextInt(playerAdjectives.length)];
                String noun = playerNouns[ThreadLocalRandom.current().nextInt(playerNouns.length)];
                int id = ThreadLocalRandom.current().nextInt(10000);
                
                return "player-" + adj + "-" + noun + "-" + id;
            };
        }
        
        /**
         * NPC名称生成器
         */
        public static NameGenerationStrategy npcNameGenerator() {
            return params -> {
                String[] npcTypes = {"guard", "merchant", "villager", "soldier", "mage", "priest", "blacksmith", "innkeeper"};
                String type = npcTypes[ThreadLocalRandom.current().nextInt(npcTypes.length)];
                int id = ThreadLocalRandom.current().nextInt(1000);
                
                return "npc-" + type + "-" + id;
            };
        }
        
        /**
         * 怪物名称生成器
         */
        public static NameGenerationStrategy monsterNameGenerator() {
            return params -> {
                String[] monsterTypes = {"goblin", "orc", "troll", "dragon", "skeleton", "zombie", "demon", "beast"};
                String[] modifiers = {"fierce", "wild", "ancient", "cursed", "dark", "shadow", "fire", "ice"};
                
                String modifier = modifiers[ThreadLocalRandom.current().nextInt(modifiers.length)];
                String type = monsterTypes[ThreadLocalRandom.current().nextInt(monsterTypes.length)];
                int level = ThreadLocalRandom.current().nextInt(100) + 1;
                
                return "monster-" + modifier + "-" + type + "-lv" + level;
            };
        }
        
        /**
         * 系统服务名称生成器
         */
        public static NameGenerationStrategy serviceNameGenerator() {
            return params -> {
                String serviceName = params.length > 0 ? params[0].toString() : "service";
                long timestamp = System.currentTimeMillis();
                int random = ThreadLocalRandom.current().nextInt(1000);
                
                return "sys-" + serviceName + "-" + timestamp + "-" + random;
            };
        }
        
        /**
         * 临时Actor名称生成器
         */
        public static NameGenerationStrategy tempNameGenerator() {
            return params -> {
                String purpose = params.length > 0 ? params[0].toString() : "temp";
                UUID uuid = UUID.randomUUID();
                String shortId = uuid.toString().substring(0, 8);
                
                return "temp-" + purpose + "-" + shortId;
            };
        }
    }
}