/*
 * 文件名: SceneUtils.java
 * 用途: 场景工具类实现
 * 实现内容:
 *   - 场景相关的通用工具方法
 *   - 坐标转换和距离计算
 *   - 区域判断和视野计算
 *   - 地图工具和空间算法
 *   - 场景状态检查和验证
 * 技术选型:
 *   - 静态工具类设计便于调用
 *   - 数学算法优化性能
 *   - 缓存机制减少重复计算
 * 依赖关系:
 *   - 被各种场景类使用
 *   - 与Position坐标系统集成
 *   - 为AOI系统提供计算支持
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.util;

import com.lx.gameserver.business.scene.core.Scene;
import com.lx.gameserver.business.scene.core.SceneConfig;
import com.lx.gameserver.business.scene.aoi.AOIGrid;

import java.util.*;

/**
 * 场景工具类
 * <p>
 * 提供场景管理相关的通用工具方法，包括坐标转换、
 * 距离计算、区域判断、视野计算等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class SceneUtils {

    /** 防止实例化 */
    private SceneUtils() {}

    // ========== 坐标转换 ==========

    /**
     * 世界坐标转网格坐标
     *
     * @param worldPosition 世界坐标
     * @param gridSize 网格大小
     * @param minBounds 最小边界
     * @return 网格坐标
     */
    public static AOIGrid.GridCoordinate worldToGrid(Scene.Position worldPosition, 
                                                   float gridSize, Scene.Position minBounds) {
        if (worldPosition == null || minBounds == null) {
            return new AOIGrid.GridCoordinate(0, 0);
        }

        int gridX = (int) Math.floor((worldPosition.getX() - minBounds.getX()) / gridSize);
        int gridZ = (int) Math.floor((worldPosition.getZ() - minBounds.getZ()) / gridSize);

        return new AOIGrid.GridCoordinate(Math.max(0, gridX), Math.max(0, gridZ));
    }

    /**
     * 网格坐标转世界坐标
     *
     * @param gridCoord 网格坐标
     * @param gridSize 网格大小
     * @param minBounds 最小边界
     * @return 世界坐标
     */
    public static Scene.Position gridToWorld(AOIGrid.GridCoordinate gridCoord, 
                                           float gridSize, Scene.Position minBounds) {
        if (gridCoord == null || minBounds == null) {
            return new Scene.Position(0, 0, 0);
        }

        float worldX = minBounds.getX() + (gridCoord.getX() + 0.5f) * gridSize;
        float worldZ = minBounds.getZ() + (gridCoord.getZ() + 0.5f) * gridSize;

        return new Scene.Position(worldX, 0, worldZ);
    }

    // ========== 距离计算 ==========

    /**
     * 计算两点间的距离
     *
     * @param pos1 位置1
     * @param pos2 位置2
     * @return 距离
     */
    public static double calculateDistance(Scene.Position pos1, Scene.Position pos2) {
        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE;
        }
        return pos1.distanceTo(pos2);
    }

    /**
     * 计算两点间的距离（忽略Y轴）
     *
     * @param pos1 位置1
     * @param pos2 位置2
     * @return 距离
     */
    public static double calculateDistance2D(Scene.Position pos1, Scene.Position pos2) {
        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE;
        }

        double dx = pos1.getX() - pos2.getX();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * 计算曼哈顿距离
     *
     * @param pos1 位置1
     * @param pos2 位置2
     * @return 曼哈顿距离
     */
    public static double calculateManhattanDistance(Scene.Position pos1, Scene.Position pos2) {
        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE;
        }

        return Math.abs(pos1.getX() - pos2.getX()) + 
               Math.abs(pos1.getZ() - pos2.getZ());
    }

    // ========== 区域判断 ==========

    /**
     * 检查位置是否在矩形区域内
     *
     * @param position 位置
     * @param minBounds 最小边界
     * @param maxBounds 最大边界
     * @return 是否在区域内
     */
    public static boolean isInRectangle(Scene.Position position, 
                                      Scene.Position minBounds, Scene.Position maxBounds) {
        if (position == null || minBounds == null || maxBounds == null) {
            return false;
        }

        return position.getX() >= minBounds.getX() && position.getX() <= maxBounds.getX() &&
               position.getZ() >= minBounds.getZ() && position.getZ() <= maxBounds.getZ();
    }

    /**
     * 检查位置是否在圆形区域内
     *
     * @param position 位置
     * @param center 圆心
     * @param radius 半径
     * @return 是否在区域内
     */
    public static boolean isInCircle(Scene.Position position, Scene.Position center, double radius) {
        if (position == null || center == null) {
            return false;
        }

        return calculateDistance2D(position, center) <= radius;
    }

    /**
     * 检查位置是否在多边形区域内
     *
     * @param position 位置
     * @param polygon 多边形顶点列表
     * @return 是否在区域内
     */
    public static boolean isInPolygon(Scene.Position position, List<Scene.Position> polygon) {
        if (position == null || polygon == null || polygon.size() < 3) {
            return false;
        }

        int intersectCount = 0;
        int vertexCount = polygon.size();

        for (int i = 0; i < vertexCount; i++) {
            Scene.Position v1 = polygon.get(i);
            Scene.Position v2 = polygon.get((i + 1) % vertexCount);

            if (rayIntersectsSegment(position, v1, v2)) {
                intersectCount++;
            }
        }

        return (intersectCount % 2) == 1;
    }

    /**
     * 射线与线段相交检测
     */
    private static boolean rayIntersectsSegment(Scene.Position point, Scene.Position v1, Scene.Position v2) {
        if (v1.getZ() > point.getZ() != v2.getZ() > point.getZ()) {
            double intersectX = (v2.getX() - v1.getX()) * (point.getZ() - v1.getZ()) / 
                              (v2.getZ() - v1.getZ()) + v1.getX();
            return point.getX() < intersectX;
        }
        return false;
    }

    // ========== 视野计算 ==========

    /**
     * 计算视野范围内的位置列表
     *
     * @param center 中心位置
     * @param viewRange 视野范围
     * @param gridSize 网格大小
     * @return 位置列表
     */
    public static List<Scene.Position> calculateViewPositions(Scene.Position center, 
                                                            double viewRange, float gridSize) {
        List<Scene.Position> positions = new ArrayList<>();
        
        int gridRange = (int) Math.ceil(viewRange / gridSize);
        
        for (int dx = -gridRange; dx <= gridRange; dx++) {
            for (int dz = -gridRange; dz <= gridRange; dz++) {
                float x = center.getX() + dx * gridSize;
                float z = center.getZ() + dz * gridSize;
                Scene.Position pos = new Scene.Position(x, center.getY(), z);
                
                if (calculateDistance2D(center, pos) <= viewRange) {
                    positions.add(pos);
                }
            }
        }
        
        return positions;
    }

    /**
     * 计算九宫格位置
     *
     * @param center 中心位置
     * @param gridSize 网格大小
     * @return 九宫格位置列表
     */
    public static List<Scene.Position> calculateNineGridPositions(Scene.Position center, float gridSize) {
        List<Scene.Position> positions = new ArrayList<>();
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float x = center.getX() + dx * gridSize;
                float z = center.getZ() + dz * gridSize;
                positions.add(new Scene.Position(x, center.getY(), z));
            }
        }
        
        return positions;
    }

    // ========== 地图工具 ==========

    /**
     * 修正位置到地图边界内
     *
     * @param position 原始位置
     * @param mapConfig 地图配置
     * @return 修正后的位置
     */
    public static Scene.Position clampToMapBounds(Scene.Position position, 
                                                SceneConfig.MapConfig mapConfig) {
        if (position == null || mapConfig == null) {
            return position;
        }

        float x = Math.max(mapConfig.getMinX(), Math.min(mapConfig.getMaxX(), position.getX()));
        float z = Math.max(mapConfig.getMinZ(), Math.min(mapConfig.getMaxZ(), position.getZ()));
        
        return new Scene.Position(x, position.getY(), z, position.getRotation());
    }

    /**
     * 检查位置是否在地图边界内
     *
     * @param position 位置
     * @param mapConfig 地图配置
     * @return 是否在边界内
     */
    public static boolean isInMapBounds(Scene.Position position, SceneConfig.MapConfig mapConfig) {
        if (position == null || mapConfig == null) {
            return false;
        }

        return mapConfig.isValidPosition(position);
    }

    /**
     * 生成随机位置
     *
     * @param mapConfig 地图配置
     * @return 随机位置
     */
    public static Scene.Position generateRandomPosition(SceneConfig.MapConfig mapConfig) {
        if (mapConfig == null) {
            return new Scene.Position(0, 0, 0);
        }

        Random random = new Random();
        float x = mapConfig.getMinX() + random.nextFloat() * (mapConfig.getMaxX() - mapConfig.getMinX());
        float z = mapConfig.getMinZ() + random.nextFloat() * (mapConfig.getMaxZ() - mapConfig.getMinZ());
        
        return new Scene.Position(x, 0, z);
    }

    /**
     * 生成指定范围内的随机位置
     *
     * @param center 中心位置
     * @param radius 半径
     * @return 随机位置
     */
    public static Scene.Position generateRandomPositionInRadius(Scene.Position center, double radius) {
        if (center == null) {
            return new Scene.Position(0, 0, 0);
        }

        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * radius;
        
        float x = center.getX() + (float) (Math.cos(angle) * distance);
        float z = center.getZ() + (float) (Math.sin(angle) * distance);
        
        return new Scene.Position(x, center.getY(), z);
    }

    // ========== 方向计算 ==========

    /**
     * 计算两点间的方向角度
     *
     * @param from 起始位置
     * @param to 目标位置
     * @return 角度（弧度）
     */
    public static float calculateDirection(Scene.Position from, Scene.Position to) {
        if (from == null || to == null) {
            return 0.0f;
        }

        float dx = to.getX() - from.getX();
        float dz = to.getZ() - from.getZ();
        
        return (float) Math.atan2(dz, dx);
    }

    /**
     * 计算两点间的方向角度（度数）
     *
     * @param from 起始位置
     * @param to 目标位置
     * @return 角度（度数）
     */
    public static float calculateDirectionDegrees(Scene.Position from, Scene.Position to) {
        return (float) Math.toDegrees(calculateDirection(from, to));
    }

    /**
     * 根据方向和距离计算目标位置
     *
     * @param origin 起始位置
     * @param direction 方向（弧度）
     * @param distance 距离
     * @return 目标位置
     */
    public static Scene.Position calculatePositionByDirection(Scene.Position origin, 
                                                            float direction, double distance) {
        if (origin == null) {
            return new Scene.Position(0, 0, 0);
        }

        float x = origin.getX() + (float) (Math.cos(direction) * distance);
        float z = origin.getZ() + (float) (Math.sin(direction) * distance);
        
        return new Scene.Position(x, origin.getY(), z);
    }

    // ========== 路径计算 ==========

    /**
     * 计算直线路径点
     *
     * @param start 起始位置
     * @param end 结束位置
     * @param stepSize 步长
     * @return 路径点列表
     */
    public static List<Scene.Position> calculateLinePath(Scene.Position start, Scene.Position end, 
                                                        double stepSize) {
        List<Scene.Position> path = new ArrayList<>();
        
        if (start == null || end == null || stepSize <= 0) {
            return path;
        }

        double totalDistance = calculateDistance2D(start, end);
        if (totalDistance <= stepSize) {
            path.add(start);
            path.add(end);
            return path;
        }

        int steps = (int) Math.ceil(totalDistance / stepSize);
        float dx = (end.getX() - start.getX()) / steps;
        float dz = (end.getZ() - start.getZ()) / steps;

        for (int i = 0; i <= steps; i++) {
            float x = start.getX() + dx * i;
            float z = start.getZ() + dz * i;
            path.add(new Scene.Position(x, start.getY(), z));
        }

        return path;
    }

    // ========== 碰撞检测 ==========

    /**
     * 检查两个圆形区域是否碰撞
     *
     * @param pos1 位置1
     * @param radius1 半径1
     * @param pos2 位置2
     * @param radius2 半径2
     * @return 是否碰撞
     */
    public static boolean isCircleColliding(Scene.Position pos1, double radius1, 
                                          Scene.Position pos2, double radius2) {
        if (pos1 == null || pos2 == null) {
            return false;
        }

        double distance = calculateDistance2D(pos1, pos2);
        return distance <= (radius1 + radius2);
    }

    /**
     * 检查点是否在矩形内
     *
     * @param point 点位置
     * @param rectCenter 矩形中心
     * @param width 矩形宽度
     * @param height 矩形高度
     * @return 是否在矩形内
     */
    public static boolean isPointInRectangle(Scene.Position point, Scene.Position rectCenter, 
                                           float width, float height) {
        if (point == null || rectCenter == null) {
            return false;
        }

        float halfWidth = width / 2;
        float halfHeight = height / 2;

        return Math.abs(point.getX() - rectCenter.getX()) <= halfWidth &&
               Math.abs(point.getZ() - rectCenter.getZ()) <= halfHeight;
    }

    // ========== 场景状态检查 ==========

    /**
     * 检查场景是否过载
     *
     * @param scene 场景
     * @return 是否过载
     */
    public static boolean isSceneOverloaded(Scene scene) {
        if (scene == null) {
            return false;
        }

        int entityCount = scene.getAllEntities().size();
        int maxEntities = scene.getConfig().getMaxEntities();
        
        return entityCount > maxEntities * 0.9; // 90%负载算作过载
    }

    /**
     * 计算场景负载率
     *
     * @param scene 场景
     * @return 负载率（0-1之间）
     */
    public static double calculateSceneLoadRatio(Scene scene) {
        if (scene == null) {
            return 0.0;
        }

        int entityCount = scene.getAllEntities().size();
        int maxEntities = scene.getConfig().getMaxEntities();
        
        return maxEntities > 0 ? (double) entityCount / maxEntities : 0.0;
    }

    // ========== 格式化工具 ==========

    /**
     * 格式化位置信息
     *
     * @param position 位置
     * @return 格式化字符串
     */
    public static String formatPosition(Scene.Position position) {
        if (position == null) {
            return "null";
        }

        return String.format("(%.2f, %.2f, %.2f)", 
                           position.getX(), position.getY(), position.getZ());
    }

    /**
     * 格式化距离信息
     *
     * @param distance 距离
     * @return 格式化字符串
     */
    public static String formatDistance(double distance) {
        if (distance == Double.MAX_VALUE) {
            return "∞";
        }

        if (distance < 1000) {
            return String.format("%.2fm", distance);
        } else {
            return String.format("%.2fkm", distance / 1000);
        }
    }

    // ========== 数学工具 ==========

    /**
     * 角度标准化到0-2π范围
     *
     * @param angle 角度（弧度）
     * @return 标准化后的角度
     */
    public static float normalizeAngle(float angle) {
        while (angle < 0) {
            angle += 2 * Math.PI;
        }
        while (angle >= 2 * Math.PI) {
            angle -= 2 * Math.PI;
        }
        return angle;
    }

    /**
     * 线性插值
     *
     * @param from 起始值
     * @param to 结束值
     * @param t 插值参数（0-1）
     * @return 插值结果
     */
    public static float lerp(float from, float to, float t) {
        return from + (to - from) * Math.max(0, Math.min(1, t));
    }

    /**
     * 位置线性插值
     *
     * @param from 起始位置
     * @param to 结束位置
     * @param t 插值参数（0-1）
     * @return 插值位置
     */
    public static Scene.Position lerpPosition(Scene.Position from, Scene.Position to, float t) {
        if (from == null || to == null) {
            return from != null ? from : to;
        }

        float x = lerp(from.getX(), to.getX(), t);
        float y = lerp(from.getY(), to.getY(), t);
        float z = lerp(from.getZ(), to.getZ(), t);
        float rotation = lerp(from.getRotation(), to.getRotation(), t);

        return new Scene.Position(x, y, z, rotation);
    }
}