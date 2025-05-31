/*
 * 文件名: ActorPathParser.java
 * 用途: Actor路径解析器
 * 实现内容:
 *   - Actor路径解析和验证
 *   - 支持层级路径和通配符
 *   - 路径规范化和模式匹配
 *   - 路径组件提取和构建
 * 技术选型:
 *   - 正则表达式实现模式匹配
 *   - 缓存机制提高解析性能
 *   - 线程安全的路径操作
 * 依赖关系:
 *   - 被ActorSystem和Router使用
 *   - 支持路径查找和过滤
 *   - 与Actor选择器集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Actor路径解析器
 * <p>
 * 提供Actor路径的解析、验证、匹配等功能。
 * 支持层级路径、通配符匹配、路径规范化等特性。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorPathParser {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorPathParser.class);
    
    /** 路径分隔符 */
    public static final String PATH_SEPARATOR = "/";
    
    /** 通配符 */
    public static final String WILDCARD_SINGLE = "*";
    public static final String WILDCARD_MULTI = "**";
    
    /** 保留路径前缀 */
    public static final String USER_PATH_PREFIX = "/user";
    public static final String SYSTEM_PATH_PREFIX = "/system";
    public static final String TEMP_PATH_PREFIX = "/temp";
    public static final String REMOTE_PATH_PREFIX = "/remote";
    
    /** 路径验证正则表达式 */
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("^/.*");
    
    /** 解析结果缓存 */
    private static final ConcurrentHashMap<String, ParsedPath> parseCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();
    
    /** 最大缓存大小 */
    private static final int MAX_CACHE_SIZE = 10000;
    
    /**
     * 解析Actor路径
     *
     * @param path 路径字符串
     * @return 解析结果
     */
    public static ParsedPath parse(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        
        // 从缓存中获取
        ParsedPath cached = parseCache.get(path);
        if (cached != null) {
            return cached;
        }
        
        // 解析路径
        ParsedPath parsed = doParse(path);
        
        // 缓存结果（控制缓存大小）
        if (parseCache.size() < MAX_CACHE_SIZE) {
            parseCache.put(path, parsed);
        }
        
        return parsed;
    }
    
    /**
     * 验证路径是否有效
     *
     * @param path 路径字符串
     * @return 是否有效
     */
    public static boolean isValidPath(String path) {
        try {
            parse(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 验证名称是否有效
     *
     * @param name 名称字符串
     * @return 是否有效
     */
    public static boolean isValidName(String name) {
        return name != null && !name.isEmpty() && VALID_NAME_PATTERN.matcher(name).matches();
    }
    
    /**
     * 构建子路径
     *
     * @param parentPath 父路径
     * @param childName 子名称
     * @return 子路径
     */
    public static String buildChildPath(String parentPath, String childName) {
        if (!isValidName(childName)) {
            throw new IllegalArgumentException("无效的子名称: " + childName);
        }
        
        if (parentPath == null || parentPath.isEmpty()) {
            return PATH_SEPARATOR + childName;
        }
        
        if (parentPath.endsWith(PATH_SEPARATOR)) {
            return parentPath + childName;
        } else {
            return parentPath + PATH_SEPARATOR + childName;
        }
    }
    
    /**
     * 获取父路径
     *
     * @param path 子路径
     * @return 父路径，如果是根路径则返回null
     */
    public static String getParentPath(String path) {
        ParsedPath parsed = parse(path);
        if (parsed.getElements().size() <= 1) {
            return null;
        }
        
        List<String> parentElements = parsed.getElements().subList(0, parsed.getElements().size() - 1);
        return PATH_SEPARATOR + String.join(PATH_SEPARATOR, parentElements);
    }
    
    /**
     * 获取路径名称（最后一段）
     *
     * @param path 路径
     * @return 名称
     */
    public static String getName(String path) {
        ParsedPath parsed = parse(path);
        List<String> elements = parsed.getElements();
        return elements.isEmpty() ? "" : elements.get(elements.size() - 1);
    }
    
    /**
     * 路径匹配
     *
     * @param pattern 模式字符串（支持*和**通配符）
     * @param path 目标路径
     * @return 是否匹配
     */
    public static boolean matches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        
        // 获取编译后的正则表达式
        Pattern compiledPattern = getCompiledPattern(pattern);
        return compiledPattern.matcher(path).matches();
    }
    
    /**
     * 查找匹配的路径
     *
     * @param pattern 模式字符串
     * @param paths 路径集合
     * @return 匹配的路径列表
     */
    public static List<String> findMatches(String pattern, Collection<String> paths) {
        Pattern compiledPattern = getCompiledPattern(pattern);
        
        return paths.stream()
                .filter(path -> compiledPattern.matcher(path).matches())
                .collect(Collectors.toList());
    }
    
    /**
     * 规范化路径
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return PATH_SEPARATOR;
        }
        
        // 分割路径
        String[] parts = path.split(PATH_SEPARATOR);
        List<String> normalizedParts = new ArrayList<>();
        
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                // 跳过空部分和当前目录
                continue;
            } else if ("..".equals(part)) {
                // 父目录
                if (!normalizedParts.isEmpty()) {
                    normalizedParts.remove(normalizedParts.size() - 1);
                }
            } else {
                normalizedParts.add(part);
            }
        }
        
        if (normalizedParts.isEmpty()) {
            return PATH_SEPARATOR;
        }
        
        return PATH_SEPARATOR + String.join(PATH_SEPARATOR, normalizedParts);
    }
    
    /**
     * 检查是否为绝对路径
     *
     * @param path 路径
     * @return 是否为绝对路径
     */
    public static boolean isAbsolute(String path) {
        return path != null && ABSOLUTE_PATH_PATTERN.matcher(path).matches();
    }
    
    /**
     * 检查是否为用户路径
     *
     * @param path 路径
     * @return 是否为用户路径
     */
    public static boolean isUserPath(String path) {
        return path != null && path.startsWith(USER_PATH_PREFIX);
    }
    
    /**
     * 检查是否为系统路径
     *
     * @param path 路径
     * @return 是否为系统路径
     */
    public static boolean isSystemPath(String path) {
        return path != null && path.startsWith(SYSTEM_PATH_PREFIX);
    }
    
    /**
     * 检查是否为临时路径
     *
     * @param path 路径
     * @return 是否为临时路径
     */
    public static boolean isTempPath(String path) {
        return path != null && path.startsWith(TEMP_PATH_PREFIX);
    }
    
    /**
     * 检查是否为远程路径
     *
     * @param path 路径
     * @return 是否为远程路径
     */
    public static boolean isRemotePath(String path) {
        return path != null && path.startsWith(REMOTE_PATH_PREFIX);
    }
    
    /**
     * 获取路径深度
     *
     * @param path 路径
     * @return 深度
     */
    public static int getDepth(String path) {
        ParsedPath parsed = parse(path);
        return parsed.getElements().size();
    }
    
    /**
     * 检查是否为祖先路径
     *
     * @param ancestorPath 祖先路径
     * @param descendantPath 后代路径
     * @return 是否为祖先关系
     */
    public static boolean isAncestor(String ancestorPath, String descendantPath) {
        if (ancestorPath == null || descendantPath == null) {
            return false;
        }
        
        // 规范化路径
        String normalizedAncestor = normalize(ancestorPath);
        String normalizedDescendant = normalize(descendantPath);
        
        if (normalizedAncestor.equals(normalizedDescendant)) {
            return false; // 相同路径不算祖先关系
        }
        
        return normalizedDescendant.startsWith(normalizedAncestor + PATH_SEPARATOR) ||
               (normalizedAncestor.equals(PATH_SEPARATOR) && !normalizedDescendant.equals(PATH_SEPARATOR));
    }
    
    /**
     * 获取公共祖先路径
     *
     * @param paths 路径列表
     * @return 公共祖先路径
     */
    public static String getCommonAncestor(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return PATH_SEPARATOR;
        }
        
        if (paths.size() == 1) {
            return getParentPath(paths.get(0));
        }
        
        // 解析所有路径
        List<ParsedPath> parsedPaths = paths.stream()
                .map(ActorPathParser::parse)
                .collect(Collectors.toList());
        
        // 找到最短路径长度
        int minLength = parsedPaths.stream()
                .mapToInt(p -> p.getElements().size())
                .min()
                .orElse(0);
        
        // 找到公共前缀
        List<String> commonElements = new ArrayList<>();
        for (int i = 0; i < minLength; i++) {
            final int index = i; // Make index final for lambda
            final String element = parsedPaths.get(0).getElements().get(i);
            boolean allMatch = parsedPaths.stream()
                    .allMatch(p -> element.equals(p.getElements().get(index)));
            
            if (allMatch) {
                commonElements.add(element);
            } else {
                break;
            }
        }
        
        if (commonElements.isEmpty()) {
            return PATH_SEPARATOR;
        }
        
        return PATH_SEPARATOR + String.join(PATH_SEPARATOR, commonElements);
    }
    
    /**
     * 执行实际的路径解析
     */
    private static ParsedPath doParse(String path) {
        // 检查是否为绝对路径
        if (!isAbsolute(path)) {
            throw new IllegalArgumentException("路径必须是绝对路径: " + path);
        }
        
        // 规范化路径
        String normalizedPath = normalize(path);
        
        // 分割路径元素
        List<String> elements = new ArrayList<>();
        if (!normalizedPath.equals(PATH_SEPARATOR)) {
            String[] parts = normalizedPath.substring(1).split(PATH_SEPARATOR);
            for (String part : parts) {
                if (!part.isEmpty()) {
                    // 验证路径元素
                    if (!isValidName(part) && !isWildcard(part)) {
                        throw new IllegalArgumentException("无效的路径元素: " + part);
                    }
                    elements.add(part);
                }
            }
        }
        
        return new ParsedPath(normalizedPath, elements);
    }
    
    /**
     * 检查是否为通配符
     */
    private static boolean isWildcard(String element) {
        return WILDCARD_SINGLE.equals(element) || WILDCARD_MULTI.equals(element);
    }
    
    /**
     * 获取编译后的模式
     */
    private static Pattern getCompiledPattern(String pattern) {
        Pattern compiled = patternCache.get(pattern);
        if (compiled == null) {
            compiled = compilePattern(pattern);
            if (patternCache.size() < MAX_CACHE_SIZE) {
                patternCache.put(pattern, compiled);
            }
        }
        return compiled;
    }
    
    /**
     * 编译路径模式为正则表达式
     */
    private static Pattern compilePattern(String pattern) {
        StringBuilder regex = new StringBuilder();
        String[] parts = pattern.split(PATH_SEPARATOR);
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            if (i > 0) {
                regex.append(Pattern.quote(PATH_SEPARATOR));
            }
            
            if (part.isEmpty()) {
                // 跳过空部分
                continue;
            } else if (WILDCARD_MULTI.equals(part)) {
                // ** 匹配任意层级
                regex.append(".*");
            } else if (WILDCARD_SINGLE.equals(part)) {
                // * 匹配单层
                regex.append("[^/]*");
            } else if (part.contains(WILDCARD_SINGLE)) {
                // 部分通配符
                String escapedPart = Pattern.quote(part).replace("\\*", ".*");
                regex.append(escapedPart);
            } else {
                // 普通字符串
                regex.append(Pattern.quote(part));
            }
        }
        
        return Pattern.compile(regex.toString());
    }
    
    /**
     * 清理缓存
     */
    public static void clearCache() {
        parseCache.clear();
        patternCache.clear();
        logger.debug("Actor路径解析缓存已清理");
    }
    
    /**
     * 获取缓存统计
     */
    public static CacheStats getCacheStats() {
        return new CacheStats(parseCache.size(), patternCache.size(), MAX_CACHE_SIZE);
    }
    
    /**
     * 解析后的路径
     */
    public static class ParsedPath {
        private final String originalPath;
        private final List<String> elements;
        
        public ParsedPath(String originalPath, List<String> elements) {
            this.originalPath = originalPath;
            this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        }
        
        public String getOriginalPath() {
            return originalPath;
        }
        
        public List<String> getElements() {
            return elements;
        }
        
        public int getDepth() {
            return elements.size();
        }
        
        public String getName() {
            return elements.isEmpty() ? "" : elements.get(elements.size() - 1);
        }
        
        public boolean isRoot() {
            return elements.isEmpty();
        }
        
        public boolean hasWildcard() {
            return elements.stream().anyMatch(ActorPathParser::isWildcard);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParsedPath)) return false;
            ParsedPath that = (ParsedPath) o;
            return Objects.equals(originalPath, that.originalPath);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(originalPath);
        }
        
        @Override
        public String toString() {
            return String.format("ParsedPath{path='%s', elements=%s}", originalPath, elements);
        }
    }
    
    /**
     * 缓存统计
     */
    public static class CacheStats {
        private final int parseCacheSize;
        private final int patternCacheSize;
        private final int maxCacheSize;
        
        public CacheStats(int parseCacheSize, int patternCacheSize, int maxCacheSize) {
            this.parseCacheSize = parseCacheSize;
            this.patternCacheSize = patternCacheSize;
            this.maxCacheSize = maxCacheSize;
        }
        
        public int getParseCacheSize() { return parseCacheSize; }
        public int getPatternCacheSize() { return patternCacheSize; }
        public int getMaxCacheSize() { return maxCacheSize; }
        public double getParseCacheUsage() { return (double) parseCacheSize / maxCacheSize; }
        public double getPatternCacheUsage() { return (double) patternCacheSize / maxCacheSize; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{parseCache=%d/%d (%.1f%%), patternCache=%d/%d (%.1f%%)}",
                    parseCacheSize, maxCacheSize, getParseCacheUsage() * 100,
                    patternCacheSize, maxCacheSize, getPatternCacheUsage() * 100);
        }
    }
}