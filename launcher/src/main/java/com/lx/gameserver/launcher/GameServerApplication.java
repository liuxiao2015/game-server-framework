package com.lx.gameserver.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 游戏服务器启动类
 * <p>
 * 作为游戏服务器框架的入口点，负责初始化和启动Spring Boot应用
 * </p>
 *
 * @author Liu Xiao
 */
@SpringBootApplication
public class GameServerApplication {

    /**
     * 应用程序入口点
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GameServerApplication.class, args);
    }
}