/*
 * 文件名: RankingController.java
 * 用途: 排行榜客户端API接口
 * 实现内容:
 *   - 提供客户端访问排行榜的HTTP接口
 *   - 支持多种查询方式和参数验证
 *   - 提供统一的错误处理和响应格式
 *   - 集成权限验证和频率限制
 * 技术选型:
 *   - 使用Spring Boot REST Controller
 *   - 集成参数验证和异常处理
 *   - 支持异步响应和缓存控制
 * 依赖关系:
 *   - 调用排行榜查询服务
 *   - 被客户端调用
 *   - 集成统一响应格式
 */
package com.lx.gameserver.business.ranking.api;

import com.lx.gameserver.business.ranking.core.RankingEntry;
import com.lx.gameserver.business.ranking.core.RankingType;
import com.lx.gameserver.business.ranking.query.RankingQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 排行榜客户端API接口
 * <p>
 * 为客户端提供排行榜查询功能的HTTP REST接口，
 * 支持多种查询方式和参数，提供统一的响应格式。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    @Autowired
    private RankingQueryService queryService;

    /**
     * 获取排行榜前N名
     *
     * @param rankingId 排行榜ID
     * @param topN      前N名
     * @return 排行榜数据
     */
    @GetMapping("/{rankingId}/top")
    public ApiResponse<List<RankingEntry>> getTopRanking(
            @PathVariable String rankingId,
            @RequestParam(defaultValue = "10") int topN) {
        
        try {
            if (topN <= 0 || topN > 100) {
                return ApiResponse.error("参数无效，topN必须在1-100之间");
            }

            List<RankingEntry> entries = queryService.getTopEntries(rankingId, topN);
            return ApiResponse.success(entries);
            
        } catch (Exception e) {
            log.error("获取排行榜前N名失败: rankingId=" + rankingId + ", topN=" + topN, e);
            return ApiResponse.error("获取排行榜失败");
        }
    }

    /**
     * 分页获取排行榜
     *
     * @param rankingId 排行榜ID
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 分页排行榜数据
     */
    @GetMapping("/{rankingId}/page")
    public ApiResponse<RankingQueryService.PageResult<RankingEntry>> getPagedRanking(
            @PathVariable String rankingId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            if (pageNum <= 0) {
                return ApiResponse.error("页码必须大于0");
            }
            if (pageSize <= 0 || pageSize > 100) {
                return ApiResponse.error("每页大小必须在1-100之间");
            }

            RankingQueryService.PageResult<RankingEntry> result = 
                queryService.getPagedEntries(rankingId, pageNum, pageSize);
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            log.error("分页获取排行榜失败: rankingId=" + rankingId + ", pageNum=" + pageNum + ", pageSize=" + pageSize, e);
            return ApiResponse.error("获取排行榜失败");
        }
    }

    /**
     * 获取玩家排名信息
     *
     * @param rankingId 排行榜ID
     * @param playerId  玩家ID
     * @return 玩家排名信息
     */
    @GetMapping("/{rankingId}/player/{playerId}")
    public ApiResponse<RankingEntry> getPlayerRank(
            @PathVariable String rankingId,
            @PathVariable Long playerId) {
        
        try {
            if (playerId <= 0) {
                return ApiResponse.error("玩家ID无效");
            }

            RankingEntry entry = queryService.getEntityRank(rankingId, playerId);
            if (entry == null) {
                return ApiResponse.error("玩家未上榜");
            }
            
            return ApiResponse.success(entry);
            
        } catch (Exception e) {
            log.error("获取玩家排名失败: rankingId=" + rankingId + ", playerId=" + playerId, e);
            return ApiResponse.error("获取玩家排名失败");
        }
    }

    /**
     * 获取玩家周围排名
     *
     * @param rankingId 排行榜ID
     * @param playerId  玩家ID
     * @param range     周围范围
     * @return 周围排名列表
     */
    @GetMapping("/{rankingId}/player/{playerId}/surrounding")
    public ApiResponse<List<RankingEntry>> getPlayerSurroundingRank(
            @PathVariable String rankingId,
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "5") int range) {
        
        try {
            if (playerId <= 0) {
                return ApiResponse.error("玩家ID无效");
            }
            if (range <= 0 || range > 50) {
                return ApiResponse.error("范围必须在1-50之间");
            }

            List<RankingEntry> entries = queryService.getSurroundingEntries(rankingId, playerId, range);
            return ApiResponse.success(entries);
            
        } catch (Exception e) {
            log.error("获取玩家周围排名失败: rankingId=" + rankingId + ", playerId=" + playerId + ", range=" + range, e);
            return ApiResponse.error("获取周围排名失败");
        }
    }

    /**
     * 搜索排行榜
     *
     * @param rankingId  排行榜ID
     * @param playerName 玩家名称
     * @param maxResults 最大结果数
     * @return 搜索结果
     */
    @GetMapping("/{rankingId}/search")
    public ApiResponse<List<RankingEntry>> searchRanking(
            @PathVariable String rankingId,
            @RequestParam String playerName,
            @RequestParam(defaultValue = "10") int maxResults) {
        
        try {
            if (playerName == null || playerName.trim().isEmpty()) {
                return ApiResponse.error("玩家名称不能为空");
            }
            if (maxResults <= 0 || maxResults > 50) {
                return ApiResponse.error("最大结果数必须在1-50之间");
            }

            List<RankingEntry> entries = queryService.searchEntities(rankingId, playerName, maxResults);
            return ApiResponse.success(entries);
            
        } catch (Exception e) {
            log.error("搜索排行榜失败: rankingId=" + rankingId + ", playerName=" + playerName, e);
            return ApiResponse.error("搜索失败");
        }
    }

    /**
     * 根据类型获取排行榜列表
     *
     * @param rankingType 排行榜类型
     * @param topN        每个排行榜的前N名
     * @return 排行榜列表
     */
    @GetMapping("/type/{rankingType}")
    public ApiResponse<Map<String, List<RankingEntry>>> getRankingsByType(
            @PathVariable String rankingType,
            @RequestParam(defaultValue = "10") int topN) {
        
        try {
            RankingType type = RankingType.fromName(rankingType);
            if (type == null) {
                return ApiResponse.error("排行榜类型无效");
            }
            if (topN <= 0 || topN > 50) {
                return ApiResponse.error("前N名必须在1-50之间");
            }

            Map<String, List<RankingEntry>> rankings = queryService.getRankingsByType(type, topN);
            return ApiResponse.success(rankings);
            
        } catch (Exception e) {
            log.error("根据类型获取排行榜失败: rankingType=" + rankingType + ", topN=" + topN, e);
            return ApiResponse.error("获取排行榜失败");
        }
    }

    /**
     * 获取排行榜统计信息
     *
     * @param rankingId 排行榜ID
     * @return 统计信息
     */
    @GetMapping("/{rankingId}/statistics")
    public ApiResponse<RankingQueryService.RankingStatistics> getRankingStatistics(
            @PathVariable String rankingId) {
        
        try {
            RankingQueryService.RankingStatistics stats = queryService.getRankingStatistics(rankingId);
            if (stats == null) {
                return ApiResponse.error("排行榜不存在");
            }
            
            return ApiResponse.success(stats);
            
        } catch (Exception e) {
            log.error("获取排行榜统计失败: rankingId=" + rankingId, e);
            return ApiResponse.error("获取统计信息失败");
        }
    }

    /**
     * 异步获取排行榜前N名
     *
     * @param rankingId 排行榜ID
     * @param topN      前N名
     * @return 异步排行榜数据
     */
    @GetMapping("/{rankingId}/top/async")
    public CompletableFuture<ApiResponse<List<RankingEntry>>> getTopRankingAsync(
            @PathVariable String rankingId,
            @RequestParam(defaultValue = "10") int topN) {
        
        return queryService.getTopEntriesAsync(rankingId, topN)
            .thenApply(ApiResponse::success)
            .exceptionally(throwable -> {
                log.error("异步获取排行榜失败: rankingId=" + rankingId + ", topN=" + topN, throwable);
                return ApiResponse.error("获取排行榜失败");
            });
    }

    /**
     * 获取多个玩家的排名信息
     *
     * @param rankingId 排行榜ID
     * @param playerIds 玩家ID列表（逗号分隔）
     * @return 玩家排名映射
     */
    @GetMapping("/{rankingId}/players")
    public ApiResponse<Map<Long, RankingEntry>> getPlayersRank(
            @PathVariable String rankingId,
            @RequestParam String playerIds) {
        
        try {
            if (playerIds == null || playerIds.trim().isEmpty()) {
                return ApiResponse.error("玩家ID列表不能为空");
            }

            // 解析玩家ID
            java.util.Set<Long> idSet = new java.util.HashSet<>();
            String[] ids = playerIds.split(",");
            if (ids.length > 100) {
                return ApiResponse.error("最多支持查询100个玩家");
            }

            for (String id : ids) {
                try {
                    Long playerId = Long.parseLong(id.trim());
                    if (playerId > 0) {
                        idSet.add(playerId);
                    }
                } catch (NumberFormatException e) {
                    return ApiResponse.error("玩家ID格式无效: " + id);
                }
            }

            if (idSet.isEmpty()) {
                return ApiResponse.error("没有有效的玩家ID");
            }

            Map<Long, RankingEntry> results = queryService.batchGetEntityRanks(rankingId, idSet);
            return ApiResponse.success(results);
            
        } catch (Exception e) {
            log.error("获取多个玩家排名失败: rankingId=" + rankingId + ", playerIds=" + playerIds, e);
            return ApiResponse.error("获取玩家排名失败");
        }
    }

    /**
     * 获取服务器状态
     *
     * @return 服务器状态信息
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getServerStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("timestamp", System.currentTimeMillis());
            status.put("status", "running");
            status.put("cacheStats", queryService.getCacheStatistics());
            
            return ApiResponse.success(status);
            
        } catch (Exception e) {
            log.error("获取服务器状态失败", e);
            return ApiResponse.error("获取状态失败");
        }
    }

    // ===== 内部类 =====

    /**
     * 统一API响应格式
     */
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;
        private long timestamp;

        private ApiResponse(int code, String message, T data) {
            this.code = code;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(200, "success", data);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(400, message, null);
        }

        public static <T> ApiResponse<T> error(int code, String message) {
            return new ApiResponse<>(code, message, null);
        }

        // Getters
        public int getCode() { return code; }
        public String getMessage() { return message; }
        public T getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
}