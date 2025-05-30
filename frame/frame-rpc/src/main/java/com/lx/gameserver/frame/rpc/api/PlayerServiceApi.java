/*
 * 文件名: PlayerServiceApi.java
 * 用途: 玩家服务接口示例
 * 实现内容:
 *   - 玩家基础操作接口
 *   - 继承BaseRpcService
 *   - 使用OpenFeign注解
 *   - 统一返回格式
 * 技术选型:
 *   - Spring Cloud OpenFeign
 *   - 声明式HTTP客户端
 *   - REST API设计
 * 依赖关系:
 *   - 继承BaseRpcService
 *   - 使用RpcRequest/RpcResponse
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.api;

import com.lx.gameserver.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 玩家服务接口
 * <p>
 * 定义玩家相关的RPC调用接口，包括玩家查询、创建、更新等操作。
 * 使用OpenFeign声明式客户端，支持负载均衡和熔断降级。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
@FeignClient(
    name = "player-service",
    path = "/api/player",
    fallbackFactory = PlayerServiceApiFallback.class
)
public interface PlayerServiceApi extends BaseRpcService {

    /**
     * 根据玩家ID获取玩家信息
     *
     * @param playerId 玩家ID
     * @return 玩家信息
     */
    @GetMapping("/{playerId}")
    RpcResponse<PlayerInfo> getPlayer(@PathVariable("playerId") String playerId);

    /**
     * 根据用户名获取玩家信息
     *
     * @param username 用户名
     * @return 玩家信息
     */
    @GetMapping("/by-username/{username}")
    RpcResponse<PlayerInfo> getPlayerByUsername(@PathVariable("username") String username);

    /**
     * 创建新玩家
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping
    RpcResponse<PlayerInfo> createPlayer(@RequestBody RpcRequest<CreatePlayerRequest> request);

    /**
     * 更新玩家信息
     *
     * @param playerId 玩家ID
     * @param request  更新请求
     * @return 更新结果
     */
    @PutMapping("/{playerId}")
    RpcResponse<PlayerInfo> updatePlayer(
        @PathVariable("playerId") String playerId,
        @RequestBody RpcRequest<UpdatePlayerRequest> request
    );

    /**
     * 删除玩家
     *
     * @param playerId 玩家ID
     * @return 删除结果
     */
    @DeleteMapping("/{playerId}")
    RpcResponse<Void> deletePlayer(@PathVariable("playerId") String playerId);

    /**
     * 分页查询玩家列表
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  搜索关键词（可选）
     * @return 玩家列表
     */
    @GetMapping
    RpcResponse<List<PlayerInfo>> getPlayers(
        @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(value = "keyword", required = false) String keyword
    );

    /**
     * 获取玩家统计信息
     *
     * @param playerId 玩家ID
     * @return 统计信息
     */
    @GetMapping("/{playerId}/stats")
    RpcResponse<Map<String, Object>> getPlayerStats(@PathVariable("playerId") String playerId);

    /**
     * 更新玩家在线状态
     *
     * @param playerId 玩家ID
     * @param online   是否在线
     * @return 更新结果
     */
    @PutMapping("/{playerId}/online")
    RpcResponse<Void> updateOnlineStatus(
        @PathVariable("playerId") String playerId,
        @RequestParam("online") boolean online
    );

    /**
     * 玩家信息数据模型
     */
    class PlayerInfo {
        private String playerId;
        private String username;
        private String nickname;
        private Integer level;
        private Long experience;
        private Long gold;
        private String avatar;
        private String status;
        private String lastLoginTime;
        private String createTime;
        private String updateTime;

        // Getter and Setter methods
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Long getExperience() { return experience; }
        public void setExperience(Long experience) { this.experience = experience; }
        public Long getGold() { return gold; }
        public void setGold(Long gold) { this.gold = gold; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getLastLoginTime() { return lastLoginTime; }
        public void setLastLoginTime(String lastLoginTime) { this.lastLoginTime = lastLoginTime; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
        public String getUpdateTime() { return updateTime; }
        public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    }

    /**
     * 创建玩家请求模型
     */
    class CreatePlayerRequest {
        private String username;
        private String nickname;
        private String avatar;

        // Getter and Setter methods
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    /**
     * 更新玩家请求模型
     */
    class UpdatePlayerRequest {
        private String nickname;
        private String avatar;
        private String status;

        // Getter and Setter methods
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}

/**
 * 玩家服务降级工厂
 */
interface PlayerServiceApiFallback {
    // TODO: 实现降级逻辑
}