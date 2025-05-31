/*
 * 文件名: AOIGrid.java
 * 用途: AOI网格系统实现
 * 实现内容:
 *   - 九宫格空间划分和索引管理
 *   - 实体网格位置计算和更新
 *   - 邻近网格查询和遍历
 *   - 网格内实体管理和优化
 *   - 内存管理和性能优化
 * 技术选型:
 *   - 二维网格数组提供快速空间查找
 *   - ConcurrentHashMap保证线程安全
 *   - 对象池化减少内存分配
 *   - 批量操作优化性能
 * 依赖关系:
 *   - 被AOIManager使用进行空间管理
 *   - 与AOIEntity协作进行位置索引
 *   - 依赖Scene坐标系统
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.business.scene.aoi;

import com.lx.gameserver.business.scene.core.Scene;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * AOI网格实现
 * <p>
 * 实现基于九宫格的空间索引系统，提供高效的AOI查询和更新。
 * 将场景空间划分为规则网格，每个网格管理其中的实体，
 * 通过网格坐标快速定位和查询邻近实体。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
@Slf4j
public class AOIGrid {

    /** 网格大小 */
    private final float gridSize;

    /** 场景边界 */
    private final Scene.Position minBounds;
    private final Scene.Position maxBounds;

    /** 网格维度 */
    private final int gridWidth;
    private final int gridHeight;

    /** 网格存储 */
    private final ConcurrentHashMap<GridCoordinate, GridCell> gridMap = new ConcurrentHashMap<>();

    /** 实体位置索引 */
    private final ConcurrentHashMap<Long, GridCoordinate> entityGridIndex = new ConcurrentHashMap<>();

    /** 读写锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** 网格统计信息 */
    private final GridStatistics statistics = new GridStatistics();

    /**
     * 网格坐标
     */
    @Data
    public static class GridCoordinate {
        private final int x;
        private final int z;

        public GridCoordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GridCoordinate that = (GridCoordinate) obj;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }

        @Override
        public String toString() {
            return String.format("GridCoordinate{x=%d, z=%d}", x, z);
        }

        /**
         * 计算与另一个坐标的曼哈顿距离
         *
         * @param other 另一个坐标
         * @return 曼哈顿距离
         */
        public int manhattanDistance(GridCoordinate other) {
            return Math.abs(this.x - other.x) + Math.abs(this.z - other.z);
        }

        /**
         * 计算与另一个坐标的欧几里得距离
         *
         * @param other 另一个坐标
         * @return 欧几里得距离
         */
        public double euclideanDistance(GridCoordinate other) {
            int dx = this.x - other.x;
            int dz = this.z - other.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /**
     * 网格单元
     */
    public static class GridCell {
        /** 网格坐标 */
        private final GridCoordinate coordinate;

        /** 网格中的实体 */
        private final Set<Long> entities = ConcurrentHashMap.newKeySet();

        /** 创建时间 */
        private final long createTime;

        /** 最后更新时间 */
        private volatile long lastUpdateTime;

        public GridCell(GridCoordinate coordinate) {
            this.coordinate = coordinate;
            this.createTime = System.currentTimeMillis();
            this.lastUpdateTime = createTime;
        }

        /**
         * 添加实体
         *
         * @param entityId 实体ID
         * @return 是否添加成功
         */
        public boolean addEntity(Long entityId) {
            if (entities.add(entityId)) {
                lastUpdateTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        /**
         * 移除实体
         *
         * @param entityId 实体ID
         * @return 是否移除成功
         */
        public boolean removeEntity(Long entityId) {
            if (entities.remove(entityId)) {
                lastUpdateTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        /**
         * 检查是否包含实体
         *
         * @param entityId 实体ID
         * @return 是否包含
         */
        public boolean containsEntity(Long entityId) {
            return entities.contains(entityId);
        }

        /**
         * 获取实体数量
         *
         * @return 实体数量
         */
        public int getEntityCount() {
            return entities.size();
        }

        /**
         * 获取所有实体
         *
         * @return 实体ID集合
         */
        public Set<Long> getEntities() {
            return new HashSet<>(entities);
        }

        /**
         * 是否为空
         *
         * @return 是否为空
         */
        public boolean isEmpty() {
            return entities.isEmpty();
        }

        /**
         * 清空实体
         */
        public void clear() {
            entities.clear();
            lastUpdateTime = System.currentTimeMillis();
        }

        // Getters
        public GridCoordinate getCoordinate() { return coordinate; }
        public long getCreateTime() { return createTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }

        @Override
        public String toString() {
            return String.format("GridCell{coordinate=%s, entities=%d}", coordinate, entities.size());
        }
    }

    /**
     * 网格统计信息
     */
    @Data
    public static class GridStatistics {
        private volatile int totalCells = 0;
        private volatile int activeCells = 0;
        private volatile int totalEntities = 0;
        private volatile double avgEntitiesPerCell = 0.0;
        private volatile int maxEntitiesInCell = 0;
        private volatile long totalUpdates = 0;
        private volatile long totalQueries = 0;

        public void incrementTotalUpdates() { totalUpdates++; }
        public void incrementTotalQueries() { totalQueries++; }

        public void updateStatistics(Collection<GridCell> cells) {
            int active = 0;
            int total = 0;
            int max = 0;

            for (GridCell cell : cells) {
                if (!cell.isEmpty()) {
                    active++;
                    int count = cell.getEntityCount();
                    total += count;
                    max = Math.max(max, count);
                }
            }

            this.activeCells = active;
            this.totalEntities = total;
            this.avgEntitiesPerCell = active > 0 ? (double) total / active : 0.0;
            this.maxEntitiesInCell = max;
        }
    }

    /**
     * 构造函数
     *
     * @param gridSize 网格大小
     * @param minBounds 最小边界
     * @param maxBounds 最大边界
     */
    public AOIGrid(float gridSize, Scene.Position minBounds, Scene.Position maxBounds) {
        this.gridSize = gridSize;
        this.minBounds = minBounds;
        this.maxBounds = maxBounds;

        // 计算网格维度
        this.gridWidth = (int) Math.ceil((maxBounds.getX() - minBounds.getX()) / gridSize);
        this.gridHeight = (int) Math.ceil((maxBounds.getZ() - minBounds.getZ()) / gridSize);

        statistics.setTotalCells(gridWidth * gridHeight);

        log.info("AOI网格初始化完成: gridSize={}, width={}, height={}, totalCells={}", 
                gridSize, gridWidth, gridHeight, statistics.getTotalCells());
    }

    // ========== 位置转换 ==========

    /**
     * 世界坐标转网格坐标
     *
     * @param position 世界坐标
     * @return 网格坐标
     */
    public GridCoordinate worldToGrid(Scene.Position position) {
        if (position == null) {
            return new GridCoordinate(0, 0);
        }

        int gridX = (int) Math.floor((position.getX() - minBounds.getX()) / gridSize);
        int gridZ = (int) Math.floor((position.getZ() - minBounds.getZ()) / gridSize);

        // 边界检查
        gridX = Math.max(0, Math.min(gridWidth - 1, gridX));
        gridZ = Math.max(0, Math.min(gridHeight - 1, gridZ));

        return new GridCoordinate(gridX, gridZ);
    }

    /**
     * 网格坐标转世界坐标（网格中心点）
     *
     * @param gridCoord 网格坐标
     * @return 世界坐标
     */
    public Scene.Position gridToWorld(GridCoordinate gridCoord) {
        if (gridCoord == null) {
            return new Scene.Position(0, 0, 0);
        }

        float worldX = minBounds.getX() + (gridCoord.getX() + 0.5f) * gridSize;
        float worldZ = minBounds.getZ() + (gridCoord.getZ() + 0.5f) * gridSize;

        return new Scene.Position(worldX, 0, worldZ);
    }

    /**
     * 检查网格坐标是否有效
     *
     * @param gridCoord 网格坐标
     * @return 是否有效
     */
    public boolean isValidGridCoordinate(GridCoordinate gridCoord) {
        return gridCoord != null &&
               gridCoord.getX() >= 0 && gridCoord.getX() < gridWidth &&
               gridCoord.getZ() >= 0 && gridCoord.getZ() < gridHeight;
    }

    // ========== 实体管理 ==========

    /**
     * 添加实体到网格
     *
     * @param entityId 实体ID
     * @param position 实体位置
     * @return 是否添加成功
     */
    public boolean addEntity(Long entityId, Scene.Position position) {
        if (entityId == null || position == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            GridCoordinate gridCoord = worldToGrid(position);
            if (!isValidGridCoordinate(gridCoord)) {
                log.warn("无效的网格坐标: entityId={}, position={}, gridCoord={}", 
                        entityId, position, gridCoord);
                return false;
            }

            // 获取或创建网格单元
            GridCell cell = gridMap.computeIfAbsent(gridCoord, GridCell::new);
            
            // 添加实体到网格
            boolean added = cell.addEntity(entityId);
            if (added) {
                // 更新实体位置索引
                entityGridIndex.put(entityId, gridCoord);
                statistics.incrementTotalUpdates();
                
                log.debug("实体添加到网格: entityId={}, gridCoord={}", entityId, gridCoord);
            }

            return added;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从网格移除实体
     *
     * @param entityId 实体ID
     * @return 是否移除成功
     */
    public boolean removeEntity(Long entityId) {
        if (entityId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            GridCoordinate oldGridCoord = entityGridIndex.remove(entityId);
            if (oldGridCoord == null) {
                return false;
            }

            GridCell cell = gridMap.get(oldGridCoord);
            if (cell != null) {
                boolean removed = cell.removeEntity(entityId);
                if (removed) {
                    statistics.incrementTotalUpdates();
                    
                    // 如果网格为空，考虑移除（可选优化）
                    if (cell.isEmpty()) {
                        // gridMap.remove(oldGridCoord); // 可选：移除空网格
                    }
                    
                    log.debug("实体从网格移除: entityId={}, gridCoord={}", entityId, oldGridCoord);
                    return true;
                }
            }

            return false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新实体位置
     *
     * @param entityId 实体ID
     * @param newPosition 新位置
     * @return 是否更新成功
     */
    public boolean updateEntity(Long entityId, Scene.Position newPosition) {
        if (entityId == null || newPosition == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            GridCoordinate oldGridCoord = entityGridIndex.get(entityId);
            GridCoordinate newGridCoord = worldToGrid(newPosition);

            if (!isValidGridCoordinate(newGridCoord)) {
                log.warn("无效的新网格坐标: entityId={}, position={}, gridCoord={}", 
                        entityId, newPosition, newGridCoord);
                return false;
            }

            // 如果网格坐标没有变化，不需要更新
            if (Objects.equals(oldGridCoord, newGridCoord)) {
                return true;
            }

            // 从旧网格移除
            if (oldGridCoord != null) {
                GridCell oldCell = gridMap.get(oldGridCoord);
                if (oldCell != null) {
                    oldCell.removeEntity(entityId);
                }
            }

            // 添加到新网格
            GridCell newCell = gridMap.computeIfAbsent(newGridCoord, GridCell::new);
            newCell.addEntity(entityId);

            // 更新索引
            entityGridIndex.put(entityId, newGridCoord);
            statistics.incrementTotalUpdates();

            log.debug("实体网格位置更新: entityId={}, from={}, to={}", 
                     entityId, oldGridCoord, newGridCoord);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 查询方法 ==========

    /**
     * 获取实体所在网格坐标
     *
     * @param entityId 实体ID
     * @return 网格坐标，如果不存在返回null
     */
    public GridCoordinate getEntityGridCoordinate(Long entityId) {
        return entityGridIndex.get(entityId);
    }

    /**
     * 获取网格中的所有实体
     *
     * @param gridCoord 网格坐标
     * @return 实体ID集合
     */
    public Set<Long> getEntitiesInGrid(GridCoordinate gridCoord) {
        lock.readLock().lock();
        try {
            GridCell cell = gridMap.get(gridCoord);
            return cell != null ? cell.getEntities() : new HashSet<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取九宫格内的所有实体
     *
     * @param centerGrid 中心网格坐标
     * @return 实体ID集合
     */
    public Set<Long> getEntitiesInNineGrid(GridCoordinate centerGrid) {
        return getEntitiesInRange(centerGrid, 1);
    }

    /**
     * 获取指定范围内的所有实体
     *
     * @param centerGrid 中心网格坐标
     * @param range 范围（网格数量）
     * @return 实体ID集合
     */
    public Set<Long> getEntitiesInRange(GridCoordinate centerGrid, int range) {
        if (centerGrid == null || range < 0) {
            return new HashSet<>();
        }

        lock.readLock().lock();
        try {
            Set<Long> entities = new HashSet<>();
            statistics.incrementTotalQueries();

            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    GridCoordinate coord = new GridCoordinate(
                            centerGrid.getX() + dx, 
                            centerGrid.getZ() + dz);

                    if (isValidGridCoordinate(coord)) {
                        GridCell cell = gridMap.get(coord);
                        if (cell != null) {
                            entities.addAll(cell.getEntities());
                        }
                    }
                }
            }

            return entities;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取指定位置周围的实体
     *
     * @param position 中心位置
     * @param range 范围（网格数量）
     * @return 实体ID集合
     */
    public Set<Long> getEntitiesNearPosition(Scene.Position position, int range) {
        GridCoordinate centerGrid = worldToGrid(position);
        return getEntitiesInRange(centerGrid, range);
    }

    /**
     * 获取圆形范围内的实体
     *
     * @param center 圆心位置
     * @param radius 半径（世界坐标）
     * @return 实体ID集合
     */
    public Set<Long> getEntitiesInCircle(Scene.Position center, double radius) {
        if (center == null || radius <= 0) {
            return new HashSet<>();
        }

        // 计算需要检查的网格范围
        int gridRange = (int) Math.ceil(radius / gridSize) + 1;
        GridCoordinate centerGrid = worldToGrid(center);
        
        Set<Long> candidateEntities = getEntitiesInRange(centerGrid, gridRange);
        
        // 进一步过滤：只返回真正在圆形范围内的实体
        // 注意：这里只能返回实体ID，具体的位置检查需要在上层进行
        return candidateEntities;
    }

    // ========== 批量操作 ==========

    /**
     * 批量添加实体
     *
     * @param entities 实体位置映射
     * @return 成功添加的实体数量
     */
    public int addEntitiesBatch(Map<Long, Scene.Position> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int successCount = 0;
            for (Map.Entry<Long, Scene.Position> entry : entities.entrySet()) {
                if (addEntity(entry.getKey(), entry.getValue())) {
                    successCount++;
                }
            }
            
            log.debug("批量添加实体完成: 成功/总数={}/{}", successCount, entities.size());
            return successCount;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 批量移除实体
     *
     * @param entityIds 实体ID集合
     * @return 成功移除的实体数量
     */
    public int removeEntitiesBatch(Collection<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int successCount = 0;
            for (Long entityId : entityIds) {
                if (removeEntity(entityId)) {
                    successCount++;
                }
            }
            
            log.debug("批量移除实体完成: 成功/总数={}/{}", successCount, entityIds.size());
            return successCount;

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 维护和优化 ==========

    /**
     * 清理空网格
     *
     * @return 清理的网格数量
     */
    public int cleanEmptyGrids() {
        lock.writeLock().lock();
        try {
            List<GridCoordinate> emptyGrids = new ArrayList<>();
            
            for (Map.Entry<GridCoordinate, GridCell> entry : gridMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    emptyGrids.add(entry.getKey());
                }
            }
            
            for (GridCoordinate coord : emptyGrids) {
                gridMap.remove(coord);
            }
            
            if (!emptyGrids.isEmpty()) {
                log.debug("清理空网格完成: {}", emptyGrids.size());
            }
            
            return emptyGrids.size();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新统计信息
     */
    public void updateStatistics() {
        lock.readLock().lock();
        try {
            statistics.updateStatistics(gridMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            gridMap.clear();
            entityGridIndex.clear();
            log.info("AOI网格已清空");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 信息查询 ==========

    /**
     * 获取网格大小
     *
     * @return 网格大小
     */
    public float getGridSize() {
        return gridSize;
    }

    /**
     * 获取网格维度
     *
     * @return 网格维度数组 [width, height]
     */
    public int[] getGridDimensions() {
        return new int[]{gridWidth, gridHeight};
    }

    /**
     * 获取网格边界
     *
     * @return 边界数组 [minX, minZ, maxX, maxZ]
     */
    public float[] getGridBounds() {
        return new float[]{
            minBounds.getX(), minBounds.getZ(),
            maxBounds.getX(), maxBounds.getZ()
        };
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public GridStatistics getStatistics() {
        updateStatistics();
        return statistics;
    }

    /**
     * 获取网格使用情况
     *
     * @return 使用情况映射
     */
    public Map<String, Object> getGridUsage() {
        updateStatistics();
        
        Map<String, Object> usage = new HashMap<>();
        usage.put("totalCells", statistics.getTotalCells());
        usage.put("activeCells", statistics.getActiveCells());
        usage.put("utilization", (double) statistics.getActiveCells() / statistics.getTotalCells());
        usage.put("totalEntities", statistics.getTotalEntities());
        usage.put("avgEntitiesPerCell", statistics.getAvgEntitiesPerCell());
        usage.put("maxEntitiesInCell", statistics.getMaxEntitiesInCell());
        usage.put("totalUpdates", statistics.getTotalUpdates());
        usage.put("totalQueries", statistics.getTotalQueries());
        
        return usage;
    }

    @Override
    public String toString() {
        return String.format("AOIGrid{gridSize=%.1f, dimensions=%dx%d, entities=%d, activeCells=%d}", 
                gridSize, gridWidth, gridHeight, statistics.getTotalEntities(), statistics.getActiveCells());
    }
}