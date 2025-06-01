/*
 * 文件名: PerformanceOptimizationController.java
 * 用途: 性能优化REST API控制器
 * 内容:
 *   - 性能优化API接口
 *   - 优化报告查询
 *   - 实时优化触发
 * 技术选型:
 *   - Spring Web MVC
 *   - RESTful API设计
 * 依赖关系:
 *   - 依赖PerformanceOptimizationUtil
 */
package com.lx.gameserver.frame.monitor.optimization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能优化API控制器
 * <p>
 * 提供性能优化相关的REST API接口，支持手动触发优化、
 * 查询优化报告等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-14
 */
@Slf4j
@RestController
@RequestMapping("/api/optimization")
public class PerformanceOptimizationController {

    @Autowired
    private PerformanceOptimizationUtil optimizationUtil;

    /**
     * 触发全面性能优化
     */
    @PostMapping("/comprehensive")
    public ResponseEntity<Map<String, Object>> triggerComprehensiveOptimization() {
        log.info("收到全面性能优化请求");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            optimizationUtil.performComprehensiveOptimization();
            
            response.put("success", true);
            response.put("message", "全面性能优化已执行");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("执行全面性能优化失败", e);
            
            response.put("success", false);
            response.put("message", "性能优化执行失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取性能优化报告
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getOptimizationReport() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String report = optimizationUtil.getOptimizationReport();
            
            response.put("success", true);
            response.put("report", report);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取优化报告失败", e);
            
            response.put("success", false);
            response.put("message", "获取优化报告失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取优化建议
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getOptimizationSuggestions() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 这里可以添加优化建议逻辑
            response.put("success", true);
            response.put("suggestions", java.util.List.of(
                "考虑升级到Java 21以使用真正的虚拟线程",
                "启用ZGC垃圾回收器以获得更低的延迟",
                "调整线程池大小以匹配CPU核心数",
                "使用对象池减少GC压力",
                "启用透明大页内存支持"
            ));
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取优化建议失败", e);
            
            response.put("success", false);
            response.put("message", "获取优化建议失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}