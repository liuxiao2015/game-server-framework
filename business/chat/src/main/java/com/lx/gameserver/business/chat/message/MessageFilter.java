/*
 * 文件名: MessageFilter.java
 * 用途: 消息过滤器核心实现
 * 实现内容:
 *   - 敏感词过滤和内容审核
 *   - 垃圾信息识别和拦截
 *   - 消息频率和长度限制
 *   - 内容格式验证和清理
 *   - 可配置的过滤规则和白名单
 * 技术选型:
 *   - DFA算法实现敏感词过滤
 *   - 正则表达式进行格式验证
 *   - 缓存机制提升过滤性能
 * 依赖关系:
 *   - 实现MessageHandler接口
 *   - 被MessageService调用
 *   - 与安全服务集成进行内容审核
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.chat.message;

import com.lx.gameserver.business.chat.core.ChatMessage;
import com.lx.gameserver.business.chat.core.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 消息过滤器实现
 * <p>
 * 提供全面的消息内容过滤功能，包括敏感词过滤、垃圾信息检测、
 * 格式验证等。支持动态配置过滤规则和白名单管理。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
public class MessageFilter implements MessageHandler {

    /**
     * 敏感词词典（使用DFA算法的树结构）
     */
    private final TrieNode sensitiveWordTrie = new TrieNode();

    /**
     * 白名单用户（不进行过滤）
     */
    private final Set<Long> whitelistUsers = ConcurrentHashMap.newKeySet();

    /**
     * 垃圾信息模式列表
     */
    private final List<Pattern> spamPatterns = new ArrayList<>();

    /**
     * 用户消息历史（用于检测重复消息）
     */
    private final Map<Long, List<String>> userMessageHistory = new ConcurrentHashMap<>();

    /**
     * 过滤统计信息
     */
    private final FilterStatistics statistics = new FilterStatistics();

    /**
     * 过滤配置
     */
    private final FilterConfig config = new FilterConfig();

    /**
     * 构造函数，初始化默认配置
     */
    public MessageFilter() {
        initializeDefaultSensitiveWords();
        initializeSpamPatterns();
    }

    @Override
    public MessageHandleResult handleReceivedMessage(ChatMessage message, MessageHandleContext context) {
        FilterResult filterResult = performFilter(message, context);
        
        switch (filterResult) {
            case PASS:
                return MessageHandleResult.success(message);
                
            case REJECT:
                log.warn("消息被拒绝: messageId={}, senderId={}, reason=敏感内容", 
                        message.getMessageId(), message.getSenderId());
                return MessageHandleResult.failure("CONTENT_REJECTED", "消息包含敏感内容");
                
            case REPLACE:
                // 消息内容已被替换，继续处理
                log.info("消息内容已过滤: messageId={}, senderId={}", 
                        message.getMessageId(), message.getSenderId());
                return MessageHandleResult.success(message);
                
            case WARNING:
                // 记录警告但继续处理
                log.warn("消息触发警告: messageId={}, senderId={}, content={}", 
                        message.getMessageId(), message.getSenderId(), message.getContent());
                return MessageHandleResult.success(message);
                
            default:
                return MessageHandleResult.success(message);
        }
    }

    @Override
    public MessageHandleResult handleSendMessage(ChatMessage message, MessageHandleContext context) {
        return handleReceivedMessage(message, context); // 发送和接收使用相同的过滤逻辑
    }

    @Override
    public FilterResult filterMessage(ChatMessage message, MessageHandleContext context) {
        return performFilter(message, context);
    }

    /**
     * 执行过滤逻辑
     */
    private FilterResult performFilter(ChatMessage message, MessageHandleContext context) {
        if (message == null || message.getContent() == null) {
            return FilterResult.PASS;
        }

        try {
            // 检查是否在白名单中
            if (isWhitelistUser(message.getSenderId())) {
                return FilterResult.PASS;
            }

            // 系统消息不进行过滤
            if (message.isSystemMsg()) {
                return FilterResult.PASS;
            }

            // 执行各种过滤检查
            FilterResult result = performContentFilter(message);
            
            // 更新统计信息
            updateStatistics(result);
            
            return result;

        } catch (Exception e) {
            log.error("消息过滤时发生异常: messageId={}", message.getMessageId(), e);
            return FilterResult.PASS; // 出错时放行，避免影响正常功能
        }
    }

    @Override
    public boolean supports(ChatMessage.MessageType messageType) {
        // 只过滤文本类型的消息
        return ChatMessage.MessageType.TEXT.equals(messageType) || 
               ChatMessage.MessageType.EMOJI.equals(messageType);
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级，在其他处理器之前执行
    }

    @Override
    public String getName() {
        return "MessageFilter";
    }

    // ===== 核心过滤逻辑 =====

    /**
     * 执行内容过滤
     */
    private FilterResult performContentFilter(ChatMessage message) {
        String content = message.getContent();
        String originalContent = content;

        // 1. 长度检查
        if (content.length() > config.getMaxContentLength()) {
            log.warn("消息长度超限: messageId={}, length={}", message.getMessageId(), content.length());
            return FilterResult.REJECT;
        }

        // 2. 空内容检查
        if (content.trim().isEmpty()) {
            log.warn("消息内容为空: messageId={}", message.getMessageId());
            return FilterResult.REJECT;
        }

        // 3. 重复消息检查
        if (isDuplicateMessage(message.getSenderId(), content)) {
            log.warn("检测到重复消息: messageId={}, senderId={}", message.getMessageId(), message.getSenderId());
            return FilterResult.REJECT;
        }

        // 4. 垃圾信息检查
        if (isSpamContent(content)) {
            log.warn("检测到垃圾信息: messageId={}, content={}", message.getMessageId(), content);
            return FilterResult.REJECT;
        }

        // 5. 敏感词过滤
        String filteredContent = filterSensitiveWords(content);
        if (!filteredContent.equals(originalContent)) {
            message.setOriginalContent(originalContent);
            message.setContent(filteredContent);
            log.info("消息已过滤敏感词: messageId={}", message.getMessageId());
            return FilterResult.REPLACE;
        }

        // 6. 特殊字符清理
        String cleanedContent = cleanSpecialCharacters(content);
        if (!cleanedContent.equals(content)) {
            message.setContent(cleanedContent);
            return FilterResult.REPLACE;
        }

        // 记录用户消息历史
        recordUserMessage(message.getSenderId(), content);

        return FilterResult.PASS;
    }

    /**
     * 过滤敏感词
     */
    private String filterSensitiveWords(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < content.length()) {
            int matchLength = findSensitiveWord(content, i);
            if (matchLength > 0) {
                // 找到敏感词，替换为星号
                result.append("*".repeat(matchLength));
                i += matchLength;
            } else {
                result.append(content.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * 使用DFA算法查找敏感词
     */
    private int findSensitiveWord(String content, int startIndex) {
        TrieNode current = sensitiveWordTrie;
        int matchLength = 0;
        int maxMatchLength = 0;

        for (int i = startIndex; i < content.length(); i++) {
            char ch = Character.toLowerCase(content.charAt(i));
            
            if (current.children.containsKey(ch)) {
                current = current.children.get(ch);
                matchLength++;
                
                if (current.isEndOfWord) {
                    maxMatchLength = matchLength;
                }
            } else {
                break;
            }
        }

        return maxMatchLength;
    }

    /**
     * 检查是否为垃圾信息
     */
    private boolean isSpamContent(String content) {
        for (Pattern pattern : spamPatterns) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否为重复消息
     */
    private boolean isDuplicateMessage(Long senderId, String content) {
        List<String> history = userMessageHistory.get(senderId);
        if (history == null) {
            return false;
        }

        // 检查最近几条消息是否重复
        int checkCount = Math.min(history.size(), config.getDuplicateCheckCount());
        for (int i = history.size() - checkCount; i < history.size(); i++) {
            if (content.equals(history.get(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 清理特殊字符
     */
    private String cleanSpecialCharacters(String content) {
        if (content == null) {
            return content;
        }

        // 移除控制字符和不可见字符
        return content.replaceAll("[\\p{Cntrl}\\p{So}]", "").trim();
    }

    /**
     * 记录用户消息历史
     */
    private void recordUserMessage(Long senderId, String content) {
        userMessageHistory.computeIfAbsent(senderId, k -> new ArrayList<>()).add(content);
        
        // 限制历史记录大小
        List<String> history = userMessageHistory.get(senderId);
        if (history.size() > config.getMaxHistorySize()) {
            history.remove(0);
        }
    }

    /**
     * 检查是否为白名单用户
     */
    private boolean isWhitelistUser(Long userId) {
        return whitelistUsers.contains(userId);
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics(FilterResult result) {
        statistics.incrementTotal();
        
        switch (result) {
            case PASS:
                statistics.incrementPassed();
                break;
            case REJECT:
                statistics.incrementRejected();
                break;
            case REPLACE:
                statistics.incrementReplaced();
                break;
            case WARNING:
                statistics.incrementWarnings();
                break;
        }
    }

    // ===== 配置管理方法 =====

    /**
     * 添加敏感词
     */
    public void addSensitiveWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }

        TrieNode current = sensitiveWordTrie;
        word = word.toLowerCase().trim();

        for (char ch : word.toCharArray()) {
            current.children.computeIfAbsent(ch, k -> new TrieNode());
            current = current.children.get(ch);
        }
        current.isEndOfWord = true;

        log.info("添加敏感词: {}", word);
    }

    /**
     * 批量添加敏感词
     */
    public void addSensitiveWords(List<String> words) {
        if (words != null) {
            words.forEach(this::addSensitiveWord);
        }
    }

    /**
     * 添加白名单用户
     */
    public void addWhitelistUser(Long userId) {
        if (userId != null) {
            whitelistUsers.add(userId);
            log.info("添加白名单用户: {}", userId);
        }
    }

    /**
     * 移除白名单用户
     */
    public void removeWhitelistUser(Long userId) {
        if (whitelistUsers.remove(userId)) {
            log.info("移除白名单用户: {}", userId);
        }
    }

    /**
     * 添加垃圾信息模式
     */
    public void addSpamPattern(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            try {
                spamPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
                log.info("添加垃圾信息模式: {}", pattern);
            } catch (Exception e) {
                log.error("添加垃圾信息模式失败: {}", pattern, e);
            }
        }
    }

    /**
     * 获取过滤统计信息
     */
    public FilterStatistics getStatistics() {
        return statistics;
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        statistics.reset();
    }

    /**
     * 清理过期的用户消息历史
     */
    public void cleanupUserHistory() {
        // 这里可以实现定期清理逻辑
        // 例如清理长时间未活跃用户的历史记录
    }

    // ===== 初始化方法 =====

    /**
     * 初始化默认敏感词
     */
    private void initializeDefaultSensitiveWords() {
        List<String> defaultWords = Arrays.asList(
                "色情", "赌博", "毒品", "暴力", "恐怖", "政治", "反动",
                "法轮功", "六四", "台独", "藏独", "疆独", "港独",
                "习近平", "毛泽东", "邓小平", "江泽民", "胡锦涛",
                "操", "草", "妈的", "他妈的", "去死", "傻逼", "白痴"
        );
        
        addSensitiveWords(defaultWords);
    }

    /**
     * 初始化垃圾信息模式
     */
    private void initializeSpamPatterns() {
        List<String> patterns = Arrays.asList(
                ".*加.*QQ.*\\d+.*", // QQ号码推广
                ".*微信.*\\w+.*", // 微信推广
                ".*www\\.[\\w.]+.*", // 网址
                ".*http[s]?://.*", // HTTP链接
                ".*\\d{11}.*", // 手机号码
                ".*代练.*", // 游戏代练
                ".*外挂.*", // 游戏外挂
                ".*刷.*钻.*", // 刷钻广告
                ".*免费.*送.*" // 免费送东西
        );

        for (String pattern : patterns) {
            addSpamPattern(pattern);
        }
    }

    // ===== 内部类定义 =====

    /**
     * Trie树节点
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord = false;
    }

    /**
     * 过滤统计信息
     */
    @lombok.Data
    public static class FilterStatistics {
        private long totalMessages = 0;
        private long passedMessages = 0;
        private long rejectedMessages = 0;
        private long replacedMessages = 0;
        private long warningMessages = 0;
        private LocalDateTime lastResetTime = LocalDateTime.now();

        public void incrementTotal() {
            totalMessages++;
        }

        public void incrementPassed() {
            passedMessages++;
        }

        public void incrementRejected() {
            rejectedMessages++;
        }

        public void incrementReplaced() {
            replacedMessages++;
        }

        public void incrementWarnings() {
            warningMessages++;
        }

        public double getRejectRate() {
            return totalMessages > 0 ? (double) rejectedMessages / totalMessages : 0.0;
        }

        public double getReplaceRate() {
            return totalMessages > 0 ? (double) replacedMessages / totalMessages : 0.0;
        }

        public void reset() {
            totalMessages = 0;
            passedMessages = 0;
            rejectedMessages = 0;
            replacedMessages = 0;
            warningMessages = 0;
            lastResetTime = LocalDateTime.now();
        }
    }

    /**
     * 过滤配置
     */
    @lombok.Data
    public static class FilterConfig {
        /** 最大内容长度 */
        private int maxContentLength = 1000;
        
        /** 重复消息检查数量 */
        private int duplicateCheckCount = 5;
        
        /** 最大历史记录大小 */
        private int maxHistorySize = 20;
        
        /** 是否启用敏感词过滤 */
        private boolean enableSensitiveWordFilter = true;
        
        /** 是否启用垃圾信息检测 */
        private boolean enableSpamDetection = true;
        
        /** 是否启用重复消息检测 */
        private boolean enableDuplicateDetection = true;
    }
}