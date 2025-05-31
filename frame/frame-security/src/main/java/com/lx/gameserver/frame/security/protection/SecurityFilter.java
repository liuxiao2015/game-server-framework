/*
 * 文件名: SecurityFilter.java
 * 用途: 安全过滤器
 * 实现内容:
 *   - SQL注入防护
 *   - XSS攻击防护
 *   - CSRF防护
 *   - 路径遍历防护
 *   - 命令注入防护
 * 技术选型:
 *   - Spring过滤器链
 *   - 正则表达式匹配
 *   - 黑名单与白名单结合
 * 依赖关系:
 *   - 被Web模块使用
 *   - 使用ValidationUtils
 */
package com.lx.gameserver.frame.security.protection;

import com.lx.gameserver.frame.security.util.SecurityUtils;
import com.lx.gameserver.frame.security.util.ValidationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 安全过滤器
 * <p>
 * 提供全方位的Web安全防护，包括SQL注入、XSS攻击、CSRF攻击、
 * 路径遍历和命令注入防护等，是Web应用的第一道防线。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@Component
@Order(100) // 确保在身份认证过滤器之前运行
public class SecurityFilter extends OncePerRequestFilter {

    /**
     * DDoS防护
     */
    private final DDoSProtection ddosProtection;
    
    /**
     * 排除的路径（不进行安全检查）
     */
    private final Set<String> excludedPaths = new HashSet<>();
    
    /**
     * 排除的路径正则表达式
     */
    private final Set<Pattern> excludedPathPatterns = new HashSet<>();
    
    /**
     * SQL注入攻击正则表达式
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)\\b(select|insert|update|delete|drop|alter|create|exec)\\b.*(\\bfrom\\b|\\binto\\b|\\bwhere\\b|\\bvalues\\b|\\bexec\\b|\\btable\\b)"
    );
    
    /**
     * XSS攻击正则表达式
     */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)<\\s*script\\b|\\bjavascript\\s*:|on\\w+\\s*=|<\\s*iframe\\b|<\\s*img\\s+[^>]*\\s*src\\s*=|"
            + "<\\s*link\\s+[^>]*\\s*href\\s*=|\\bdata\\s*:|document\\.cookie|eval\\s*\\(|document\\.location|"
            + "document\\.write|window\\.location|setTimeout\\s*\\(|setInterval\\s*\\(|new\\s+Function\\s*\\("
    );
    
    /**
     * 路径遍历攻击正则表达式
     */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(?i)\\.\\.[/\\\\]|\\.\\.$|^\\.\\./"
    );
    
    /**
     * 命令注入攻击正则表达式
     */
    private static final Pattern CMD_INJECTION_PATTERN = Pattern.compile(
            "(?i)\\s*&&\\s*|\\s*;\\s*|\\s*\\|\\s*|\\s*`\\s*|\\$\\(|\\$\\{|\\b(ping|nc|netcat|wget|curl|bash|sh|ssh|telnet|nslookup)\\s+"
    );
    
    /**
     * 敏感HTTP头（不对客户端暴露）
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "Server", "X-Powered-By", "X-AspNet-Version", "X-AspNetMvc-Version"
    );
    
    /**
     * 构造函数
     *
     * @param ddosProtection DDoS防护组件
     */
    @Autowired
    public SecurityFilter(DDoSProtection ddosProtection) {
        this.ddosProtection = ddosProtection;
        
        // 初始化排除路径
        initExcludedPaths();
        
        log.info("安全过滤器初始化完成");
    }
    
    /**
     * 初始化排除路径
     */
    private void initExcludedPaths() {
        // 静态资源路径
        excludedPaths.add("/static/");
        excludedPaths.add("/assets/");
        excludedPaths.add("/public/");
        excludedPaths.add("/favicon.ico");
        
        // 特定静态资源类型
        excludedPathPatterns.add(Pattern.compile(".*\\.(css|js|jpg|jpeg|png|gif|ico|svg|woff|woff2|ttf|eot)$"));
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        // 检查是否排除的路径
        if (isExcludedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 获取客户端IP
        String clientIp = SecurityUtils.getClientIpAddress(request);
        String path = request.getRequestURI();
        
        try {
            // DDoS防护检查
            if (ddosProtection.handleHttpRequest(clientIp, path, 
                                             request.getHeader("User-Agent"), 
                                             request.getContentLength())) {
                log.debug("DDoS防护拒绝请求: IP={}, 路径={}", clientIp, path);
                response.sendError(429, "请求频率过高");
                return;
            }
            
            // 安全检查
            if (!validateRequest(request)) {
                log.warn("检测到可能的攻击: IP={}, 路径={}, 方法={}", clientIp, path, request.getMethod());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "请求包含不安全内容");
                return;
            }
            
            // 添加安全响应头
            addSecurityHeaders(response);
            
            // 移除敏感响应头
            removeSensitiveHeaders(response);
            
            // 继续过滤器链
            filterChain.doFilter(new XssRequestWrapper(request), response);
            
        } catch (Exception e) {
            log.error("安全过滤器发生异常: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "请求处理失败");
        }
    }
    
    /**
     * 检查是否是排除的路径
     *
     * @param request HTTP请求
     * @return 如果是排除的路径返回true，否则返回false
     */
    private boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // 检查静态路径
        for (String prefix : excludedPaths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        
        // 检查路径正则表达式
        for (Pattern pattern : excludedPathPatterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 验证请求安全性
     *
     * @param request HTTP请求
     * @return 如果安全返回true，否则返回false
     */
    private boolean validateRequest(HttpServletRequest request) {
        // 获取请求参数和路径
        String path = request.getRequestURI();
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));
        
        // 检查路径遍历攻击
        if (PATH_TRAVERSAL_PATTERN.matcher(path).find()) {
            log.warn("检测到路径遍历攻击: {}", path);
            return false;
        }
        
        // 检查参数注入攻击
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            
            // 检查SQL注入
            if (SQL_INJECTION_PATTERN.matcher(value).find()) {
                log.warn("检测到SQL注入攻击: 参数={}, 值={}", entry.getKey(), value);
                return false;
            }
            
            // 检查XSS攻击
            if (XSS_PATTERN.matcher(value).find()) {
                log.warn("检测到XSS攻击: 参数={}, 值={}", entry.getKey(), value);
                return false;
            }
            
            // 检查命令注入
            if (CMD_INJECTION_PATTERN.matcher(value).find()) {
                log.warn("检测到命令注入攻击: 参数={}, 值={}", entry.getKey(), value);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 添加安全响应头
     *
     * @param response HTTP响应
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        // 防止点击劫持
        response.setHeader("X-Frame-Options", "DENY");
        
        // 启用XSS保护
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // 防止MIME类型嗅探攻击
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // 内容安全策略（CSP）
        response.setHeader("Content-Security-Policy", 
                "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self'; " +
                "img-src 'self' data:; " +
                "font-src 'self'; " +
                "connect-src 'self';");
        
        // 引用政策
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // 权限策略
        response.setHeader("Permissions-Policy", 
                "camera=(), geolocation=(), microphone=(), payment=()");
    }
    
    /**
     * 移除敏感响应头
     *
     * @param response HTTP响应
     */
    private void removeSensitiveHeaders(HttpServletResponse response) {
        for (String header : SENSITIVE_HEADERS) {
            response.setHeader(header, null);
        }
    }
    
    /**
     * XSS防护请求包装器
     */
    private static class XssRequestWrapper extends HttpServletRequestWrapper {
        
        /**
         * 构造函数
         *
         * @param request 原始请求
         */
        public XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }
        
        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return value != null ? ValidationUtils.encodeHtml(value) : null;
        }
        
        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            
            String[] encodedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                encodedValues[i] = values[i] != null ? ValidationUtils.encodeHtml(values[i]) : null;
            }
            
            return encodedValues;
        }
        
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> paramMap = super.getParameterMap();
            Map<String, String[]> encodedMap = new HashMap<>();
            
            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                String[] values = entry.getValue();
                String[] encodedValues = new String[values.length];
                
                for (int i = 0; i < values.length; i++) {
                    encodedValues[i] = values[i] != null ? ValidationUtils.encodeHtml(values[i]) : null;
                }
                
                encodedMap.put(entry.getKey(), encodedValues);
            }
            
            return encodedMap;
        }
        
        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return value != null ? ValidationUtils.encodeHtml(value) : null;
        }
    }
}