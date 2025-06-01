/*
 * 文件名: TestFrameworkApplication.java
 * 用途: 测试框架启动器应用
 * 内容: 
 *   - 测试框架Spring Boot应用主类
 *   - 自动配置和组件扫描
 *   - 测试框架生命周期管理
 *   - 命令行接口支持
 * 技术选型: 
 *   - Spring Boot
 *   - 命令行接口
 *   - 自动配置
 * 依赖关系: 
 *   - 启动TestFramework核心
 *   - 集成所有测试模块
 * 作者: liuxiao2015
 * 日期: 2025-06-01
 */
package com.lx.gameserver.testframework;

import com.lx.gameserver.testframework.core.TestFramework;
import com.lx.gameserver.testframework.config.TestConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 测试框架启动器应用
 * <p>
 * Spring Boot应用主类，启动测试框架并提供命令行接口。
 * </p>
 * 
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-06-01
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(TestConfig.class)
public class TestFrameworkApplication implements CommandLineRunner {
    
    @Autowired
    private TestFramework testFramework;
    
    @Autowired
    private TestConfig testConfig;
    
    /**
     * 应用入口
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        log.info("启动游戏服务器测试框架...");
        
        try {
            SpringApplication application = new SpringApplication(TestFrameworkApplication.class);
            application.run(args);
        } catch (Exception e) {
            log.error("测试框架启动失败", e);
            System.exit(1);
        }
    }
    
    @Override
    public void run(String... args) throws Exception {
        log.info("测试框架应用启动完成");
        
        // 验证配置
        if (!testConfig.validate()) {
            log.error("测试框架配置验证失败");
            return;
        }
        
        // 初始化测试框架
        testFramework.initialize();
        
        // 处理命令行参数
        processCommandLineArgs(args);
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭测试框架...");
            testFramework.shutdown();
        }));
        
        log.info("测试框架就绪，可以开始执行测试");
    }
    
    /**
     * 处理命令行参数
     * 
     * @param args 命令行参数
     */
    private void processCommandLineArgs(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            switch (arg) {
                case "--help", "-h":
                    printUsage();
                    break;
                    
                case "--version", "-v":
                    printVersion();
                    break;
                    
                case "--config", "-c":
                    if (i + 1 < args.length) {
                        String configFile = args[++i];
                        log.info("使用配置文件: {}", configFile);
                    } else {
                        log.error("--config 参数需要指定配置文件路径");
                    }
                    break;
                    
                case "--run-tests":
                    if (i + 1 < args.length) {
                        String testSuite = args[++i];
                        runTestSuite(testSuite);
                    } else {
                        log.error("--run-tests 参数需要指定测试套件名称");
                    }
                    break;
                    
                case "--list-suites":
                    listTestSuites();
                    break;
                    
                default:
                    if (arg.startsWith("-")) {
                        log.warn("未知参数: {}", arg);
                    }
                    break;
            }
        }
    }
    
    /**
     * 打印使用说明
     */
    private void printUsage() {
        System.out.println("游戏服务器测试框架 v1.0.0");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  java -jar test-framework.jar [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  -h, --help           显示帮助信息");
        System.out.println("  -v, --version        显示版本信息");
        System.out.println("  -c, --config FILE    指定配置文件");
        System.out.println("  --run-tests SUITE    运行指定的测试套件");
        System.out.println("  --list-suites        列出所有可用的测试套件");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java -jar test-framework.jar --run-tests unit-tests");
        System.out.println("  java -jar test-framework.jar --config custom-config.yml");
    }
    
    /**
     * 打印版本信息
     */
    private void printVersion() {
        System.out.println("游戏服务器测试框架");
        System.out.println("版本: 1.0.0-SNAPSHOT");
        System.out.println("构建时间: 2025-06-01");
        System.out.println("作者: liuxiao2015");
    }
    
    /**
     * 运行测试套件
     * 
     * @param suiteName 测试套件名称
     */
    private void runTestSuite(String suiteName) {
        log.info("运行测试套件: {}", suiteName);
        
        try {
            if (testFramework.getTestSuites().containsKey(suiteName)) {
                testFramework.runTests(java.util.Set.of(suiteName))
                    .thenAccept(summary -> {
                        log.info("测试套件 {} 执行完成，成功率: {:.2f}%", 
                            suiteName, summary.getSuccessRate() * 100);
                    })
                    .exceptionally(throwable -> {
                        log.error("测试套件 {} 执行失败", suiteName, throwable);
                        return null;
                    });
            } else {
                log.error("找不到测试套件: {}", suiteName);
            }
        } catch (Exception e) {
            log.error("运行测试套件失败: {}", suiteName, e);
        }
    }
    
    /**
     * 列出所有测试套件
     */
    private void listTestSuites() {
        log.info("可用的测试套件:");
        
        var testSuites = testFramework.getTestSuites();
        if (testSuites.isEmpty()) {
            System.out.println("  (暂无注册的测试套件)");
        } else {
            testSuites.forEach((name, suite) -> {
                System.out.printf("  - %s (%s) - %d个测试用例%n", 
                    name, suite.getDescription(), suite.getTestCaseCount());
            });
        }
    }
}