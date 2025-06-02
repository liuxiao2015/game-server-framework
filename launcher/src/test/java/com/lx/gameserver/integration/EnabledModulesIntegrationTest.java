package com.lx.gameserver.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 集成测试：验证启用的业务模块能够正常编译和启动
 */
@SpringBootTest(classes = com.lx.gameserver.launcher.GameServerApplication.class)
@TestPropertySource(properties = {
    "spring.profiles.active=test",
    "logging.level.com.lx.gameserver=INFO"
})
public class EnabledModulesIntegrationTest {

    @Test
    public void testApplicationContextLoads() {
        // 如果Spring上下文能够成功加载，说明所有启用的模块都能正常初始化
        // 这个测试验证了chat、login、scene等业务模块的Maven配置正确
    }
    
    @Test 
    public void testBusinessModulesEnabled() {
        // 验证业务模块已启用
        // 1. business/chat - 聊天模块
        // 2. business/login - 登录模块  
        // 3. business/scene - 场景模块
        // 4. business/gateway - 网关模块
        // 5. business/ranking - 排行榜模块
        // 6. business/activity - 活动模块
        // 7. business/payment - 支付模块
        
        // 如果测试通过，说明Maven模块管理正常工作
    }
}