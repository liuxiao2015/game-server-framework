/*
 * 文件名: RunMode.java
 * 用途: 游戏服务器运行模式枚举定义
 * 内容: 
 *   - 定义开发模式（DEV）和生产模式（NORMAL）
 *   - 提供模式解析和转换功能
 *   - 支持从字符串解析运行模式
 * 技术选型: 
 *   - Java 21 枚举特性
 *   - 字符串匹配和解析
 * 依赖关系: 
 *   - 无外部依赖
 *   - 被ModeDetector和Bootstrap类使用
 */
package com.lx.gameserver.launcher;

/**
 * 游戏服务器运行模式枚举
 * <p>
 * 定义游戏服务器支持的运行模式，包括开发模式和生产模式。
 * 每种模式对应不同的配置文件和运行环境设置。
 * </p>
 *
 * @author Liu Xiao
 * @version 1.0.0
 * @since 2025-05-28
 */
public enum RunMode {
    
    /**
     * 开发模式
     * <p>
     * 用于本地开发和测试环境，配置简化的数据库和缓存设置。
     * 默认使用内嵌Redis和单体MySQL数据库。
     * </p>
     */
    DEV("dev", "开发模式"),
    
    /**
     * 生产模式
     * <p>
     * 用于生产环境，配置高可用的数据库集群和Redis集群。
     * 适用于正式部署的游戏服务器。
     * </p>
     */
    NORMAL("normal", "生产模式");
    
    /**
     * 模式标识码
     */
    private final String code;
    
    /**
     * 模式描述
     */
    private final String description;
    
    /**
     * 构造函数
     *
     * @param code        模式标识码
     * @param description 模式描述
     */
    RunMode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 获取模式标识码
     *
     * @return 模式标识码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 获取模式描述
     *
     * @return 模式描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 从字符串解析运行模式
     * <p>
     * 支持不区分大小写的模式名称解析，如果解析失败则返回默认的开发模式。
     * </p>
     *
     * @param mode 模式字符串
     * @return 对应的运行模式，解析失败时返回DEV模式
     */
    public static RunMode fromString(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return DEV;
        }
        
        String normalizedMode = mode.trim().toLowerCase();
        for (RunMode runMode : values()) {
            if (runMode.code.equals(normalizedMode)) {
                return runMode;
            }
        }
        
        // 如果没有匹配到，返回默认的开发模式
        return DEV;
    }
    
    /**
     * 检查是否为开发模式
     *
     * @return 如果是开发模式返回true，否则返回false
     */
    public boolean isDev() {
        return this == DEV;
    }
    
    /**
     * 检查是否为生产模式
     *
     * @return 如果是生产模式返回true，否则返回false
     */
    public boolean isNormal() {
        return this == NORMAL;
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s)", code, description);
    }
}