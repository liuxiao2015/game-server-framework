/*
 * 文件名: InventoryComponent.java
 * 用途: 背包组件实现
 * 实现内容:
 *   - 背包物品管理
 *   - 物品添加/移除/查找
 *   - 背包容量管理
 *   - 物品堆叠支持
 *   - 背包排序和整理
 * 技术选型:
 *   - 数组结构存储物品槽位
 *   - 哈希表优化物品查找
 *   - 策略模式支持不同排序方式
 * 依赖关系:
 *   - 实现Component接口
 *   - 被背包系统使用
 *   - 与物品系统协同工作
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.ecs.component;

import com.lx.gameserver.frame.ecs.core.AbstractComponent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 背包组件
 * <p>
 * 管理游戏角色的物品背包，支持物品存储、查找、堆叠等功能。
 * 支持多种背包类型和自定义容量。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class InventoryComponent extends AbstractComponent {
    
    /**
     * 组件类型ID
     */
    public static final int TYPE_ID = 2;
    
    @Override
    public int getTypeId() {
        return TYPE_ID;
    }
    
    /**
     * 背包类型枚举
     */
    public enum InventoryType {
        /** 主背包 */
        MAIN(1, "主背包", 50),
        /** 装备背包 */
        EQUIPMENT(2, "装备背包", 20),
        /** 任务背包 */
        QUEST(3, "任务背包", 30),
        /** 材料背包 */
        MATERIALS(4, "材料背包", 100),
        /** 临时背包 */
        TEMPORARY(5, "临时背包", 20);
        
        private final int id;
        private final String displayName;
        private final int defaultCapacity;
        
        InventoryType(int id, String displayName, int defaultCapacity) {
            this.id = id;
            this.displayName = displayName;
            this.defaultCapacity = defaultCapacity;
        }
        
        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getDefaultCapacity() { return defaultCapacity; }
    }
    
    /**
     * 物品槽位
     */
    public static class ItemSlot {
        private int itemId;
        private int quantity;
        private Map<String, Object> properties;
        private boolean locked;
        
        public ItemSlot() {
            this.itemId = 0;
            this.quantity = 0;
            this.properties = new HashMap<>();
            this.locked = false;
        }
        
        public ItemSlot(int itemId, int quantity) {
            this();
            this.itemId = itemId;
            this.quantity = quantity;
        }
        
        public boolean isEmpty() {
            return itemId <= 0 || quantity <= 0;
        }
        
        public boolean canStackWith(int otherItemId) {
            return !isEmpty() && this.itemId == otherItemId;
        }
        
        public void clear() {
            this.itemId = 0;
            this.quantity = 0;
            this.properties.clear();
            this.locked = false;
        }
        
        // Getters and Setters
        public int getItemId() { return itemId; }
        public void setItemId(int itemId) { this.itemId = itemId; }
        
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        
        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }
        
        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }
        
        @Override
        public String toString() {
            return "ItemSlot{" +
                    "itemId=" + itemId +
                    ", quantity=" + quantity +
                    ", locked=" + locked +
                    '}';
        }
    }
    
    /**
     * 物品添加结果
     */
    public static class AddItemResult {
        private final boolean success;
        private final int addedQuantity;
        private final int remainingQuantity;
        private final List<Integer> affectedSlots;
        private final String errorMessage;
        
        public AddItemResult(boolean success, int addedQuantity, int remainingQuantity, 
                           List<Integer> affectedSlots, String errorMessage) {
            this.success = success;
            this.addedQuantity = addedQuantity;
            this.remainingQuantity = remainingQuantity;
            this.affectedSlots = affectedSlots != null ? new ArrayList<>(affectedSlots) : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
        
        public static AddItemResult success(int addedQuantity, List<Integer> affectedSlots) {
            return new AddItemResult(true, addedQuantity, 0, affectedSlots, null);
        }
        
        public static AddItemResult partial(int addedQuantity, int remainingQuantity, List<Integer> affectedSlots) {
            return new AddItemResult(false, addedQuantity, remainingQuantity, affectedSlots, "背包空间不足");
        }
        
        public static AddItemResult failure(String errorMessage) {
            return new AddItemResult(false, 0, 0, Collections.emptyList(), errorMessage);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public int getAddedQuantity() { return addedQuantity; }
        public int getRemainingQuantity() { return remainingQuantity; }
        public List<Integer> getAffectedSlots() { return affectedSlots; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 背包类型
     */
    private final InventoryType inventoryType;
    
    /**
     * 背包容量
     */
    private int capacity;
    
    /**
     * 物品槽位数组
     */
    private final ItemSlot[] slots;
    
    /**
     * 物品ID到槽位的映射（优化查找）
     */
    private final Map<Integer, Set<Integer>> itemSlotMap;
    
    /**
     * 最大堆叠数量映射
     */
    private final Map<Integer, Integer> maxStackSizes;
    
    /**
     * 是否自动整理
     */
    private boolean autoSort = false;
    
    /**
     * 构造函数
     *
     * @param inventoryType 背包类型
     */
    public InventoryComponent(InventoryType inventoryType) {
        this(inventoryType, inventoryType.getDefaultCapacity());
    }
    
    /**
     * 构造函数
     *
     * @param inventoryType 背包类型
     * @param capacity 背包容量
     */
    public InventoryComponent(InventoryType inventoryType, int capacity) {
        this.inventoryType = inventoryType;
        this.capacity = Math.max(1, capacity);
        this.slots = new ItemSlot[this.capacity];
        this.itemSlotMap = new ConcurrentHashMap<>();
        this.maxStackSizes = new ConcurrentHashMap<>();
        
        // 初始化槽位
        for (int i = 0; i < this.capacity; i++) {
            slots[i] = new ItemSlot();
        }
        
        // 设置默认堆叠大小
        initializeDefaultStackSizes();
    }
    
    /**
     * 初始化默认堆叠大小
     */
    private void initializeDefaultStackSizes() {
        // 可以根据物品类型设置不同的堆叠大小
        // 这里设置一些默认值
        maxStackSizes.put(0, 1); // 默认不可堆叠
    }
    
    /**
     * 设置物品最大堆叠数量
     *
     * @param itemId 物品ID
     * @param maxStackSize 最大堆叠数量
     */
    public void setMaxStackSize(int itemId, int maxStackSize) {
        maxStackSizes.put(itemId, Math.max(1, maxStackSize));
    }
    
    /**
     * 获取物品最大堆叠数量
     *
     * @param itemId 物品ID
     * @return 最大堆叠数量
     */
    public int getMaxStackSize(int itemId) {
        return maxStackSizes.getOrDefault(itemId, 1);
    }
    
    /**
     * 添加物品
     *
     * @param itemId 物品ID
     * @param quantity 数量
     * @return 添加结果
     */
    public AddItemResult addItem(int itemId, int quantity) {
        return addItem(itemId, quantity, null);
    }
    
    /**
     * 添加物品（带属性）
     *
     * @param itemId 物品ID
     * @param quantity 数量
     * @param properties 物品属性
     * @return 添加结果
     */
    public AddItemResult addItem(int itemId, int quantity, Map<String, Object> properties) {
        if (itemId <= 0 || quantity <= 0) {
            return AddItemResult.failure("无效的物品ID或数量");
        }
        
        List<Integer> affectedSlots = new ArrayList<>();
        int remainingQuantity = quantity;
        int maxStackSize = getMaxStackSize(itemId);
        
        // 首先尝试堆叠到现有槽位
        Set<Integer> existingSlots = itemSlotMap.get(itemId);
        if (existingSlots != null) {
            for (Integer slotIndex : new ArrayList<>(existingSlots)) {
                if (remainingQuantity <= 0) break;
                
                ItemSlot slot = slots[slotIndex];
                if (slot.isLocked() || slot.isEmpty() || !slot.canStackWith(itemId)) {
                    continue;
                }
                
                int canAdd = maxStackSize - slot.getQuantity();
                if (canAdd > 0) {
                    int toAdd = Math.min(canAdd, remainingQuantity);
                    slot.setQuantity(slot.getQuantity() + toAdd);
                    remainingQuantity -= toAdd;
                    affectedSlots.add(slotIndex);
                }
            }
        }
        
        // 如果还有剩余，寻找空槽位
        if (remainingQuantity > 0) {
            for (int i = 0; i < capacity; i++) {
                if (remainingQuantity <= 0) break;
                
                ItemSlot slot = slots[i];
                if (slot.isLocked() || !slot.isEmpty()) {
                    continue;
                }
                
                int toAdd = Math.min(maxStackSize, remainingQuantity);
                slot.setItemId(itemId);
                slot.setQuantity(toAdd);
                if (properties != null) {
                    slot.getProperties().putAll(properties);
                }
                
                remainingQuantity -= toAdd;
                affectedSlots.add(i);
                
                // 更新物品槽位映射
                itemSlotMap.computeIfAbsent(itemId, k -> ConcurrentHashMap.newKeySet()).add(i);
            }
        }
        
        // 自动整理
        if (autoSort && !affectedSlots.isEmpty()) {
            sortInventory();
        }
        
        incrementVersion();
        
        int addedQuantity = quantity - remainingQuantity;
        if (remainingQuantity == 0) {
            return AddItemResult.success(addedQuantity, affectedSlots);
        } else if (addedQuantity > 0) {
            return AddItemResult.partial(addedQuantity, remainingQuantity, affectedSlots);
        } else {
            return AddItemResult.failure("背包已满");
        }
    }
    
    /**
     * 移除物品
     *
     * @param itemId 物品ID
     * @param quantity 数量
     * @return 实际移除的数量
     */
    public int removeItem(int itemId, int quantity) {
        if (itemId <= 0 || quantity <= 0) {
            return 0;
        }
        
        int removedQuantity = 0;
        Set<Integer> existingSlots = itemSlotMap.get(itemId);
        
        if (existingSlots != null) {
            Iterator<Integer> iterator = existingSlots.iterator();
            while (iterator.hasNext() && removedQuantity < quantity) {
                int slotIndex = iterator.next();
                ItemSlot slot = slots[slotIndex];
                
                if (slot.isLocked() || slot.getItemId() != itemId) {
                    iterator.remove();
                    continue;
                }
                
                int canRemove = Math.min(slot.getQuantity(), quantity - removedQuantity);
                slot.setQuantity(slot.getQuantity() - canRemove);
                removedQuantity += canRemove;
                
                if (slot.getQuantity() <= 0) {
                    slot.clear();
                    iterator.remove();
                }
            }
        }
        
        if (removedQuantity > 0) {
            incrementVersion();
        }
        
        return removedQuantity;
    }
    
    /**
     * 获取物品数量
     *
     * @param itemId 物品ID
     * @return 物品总数量
     */
    public int getItemCount(int itemId) {
        Set<Integer> existingSlots = itemSlotMap.get(itemId);
        if (existingSlots == null) {
            return 0;
        }
        
        int totalCount = 0;
        for (Integer slotIndex : existingSlots) {
            ItemSlot slot = slots[slotIndex];
            if (slot.getItemId() == itemId && !slot.isEmpty()) {
                totalCount += slot.getQuantity();
            }
        }
        
        return totalCount;
    }
    
    /**
     * 检查是否有指定物品
     *
     * @param itemId 物品ID
     * @param quantity 数量
     * @return 如果有足够数量返回true
     */
    public boolean hasItem(int itemId, int quantity) {
        return getItemCount(itemId) >= quantity;
    }
    
    /**
     * 获取指定槽位的物品
     *
     * @param slotIndex 槽位索引
     * @return 物品槽位，如果索引无效返回null
     */
    public ItemSlot getSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= capacity) {
            return null;
        }
        return slots[slotIndex];
    }
    
    /**
     * 获取所有非空槽位
     *
     * @return 非空槽位列表
     */
    public List<ItemSlot> getNonEmptySlots() {
        List<ItemSlot> result = new ArrayList<>();
        for (ItemSlot slot : slots) {
            if (!slot.isEmpty()) {
                result.add(slot);
            }
        }
        return result;
    }
    
    /**
     * 查找物品槽位
     *
     * @param predicate 查找条件
     * @return 符合条件的槽位列表
     */
    public List<ItemSlot> findSlots(Predicate<ItemSlot> predicate) {
        List<ItemSlot> result = new ArrayList<>();
        for (ItemSlot slot : slots) {
            if (predicate.test(slot)) {
                result.add(slot);
            }
        }
        return result;
    }
    
    /**
     * 获取空槽位数量
     *
     * @return 空槽位数量
     */
    public int getEmptySlotCount() {
        int emptyCount = 0;
        for (ItemSlot slot : slots) {
            if (slot.isEmpty()) {
                emptyCount++;
            }
        }
        return emptyCount;
    }
    
    /**
     * 整理背包
     */
    public void sortInventory() {
        // 收集所有非空物品
        List<ItemSlot> nonEmptySlots = new ArrayList<>();
        for (ItemSlot slot : slots) {
            if (!slot.isEmpty() && !slot.isLocked()) {
                nonEmptySlots.add(new ItemSlot(slot.getItemId(), slot.getQuantity()));
                nonEmptySlots.get(nonEmptySlots.size() - 1).getProperties().putAll(slot.getProperties());
            }
        }
        
        // 排序（按物品ID）
        nonEmptySlots.sort(Comparator.comparingInt(ItemSlot::getItemId));
        
        // 清空所有非锁定槽位
        itemSlotMap.clear();
        for (int i = 0; i < capacity; i++) {
            if (!slots[i].isLocked()) {
                slots[i].clear();
            }
        }
        
        // 重新放置物品
        int slotIndex = 0;
        for (ItemSlot item : nonEmptySlots) {
            // 跳过锁定的槽位
            while (slotIndex < capacity && slots[slotIndex].isLocked()) {
                slotIndex++;
            }
            
            if (slotIndex >= capacity) {
                break;
            }
            
            slots[slotIndex].setItemId(item.getItemId());
            slots[slotIndex].setQuantity(item.getQuantity());
            slots[slotIndex].getProperties().putAll(item.getProperties());
            
            // 更新映射
            itemSlotMap.computeIfAbsent(item.getItemId(), k -> ConcurrentHashMap.newKeySet()).add(slotIndex);
            
            slotIndex++;
        }
        
        incrementVersion();
    }
    
    /**
     * 清空背包
     */
    public void clearInventory() {
        for (ItemSlot slot : slots) {
            if (!slot.isLocked()) {
                slot.clear();
            }
        }
        itemSlotMap.clear();
        incrementVersion();
    }
    
    @Override
    public void reset() {
        clearInventory();
        autoSort = false;
        maxStackSizes.clear();
        initializeDefaultStackSizes();
        super.reset();
    }
    
    @Override
    public InventoryComponent clone() {
        InventoryComponent cloned = new InventoryComponent(inventoryType, capacity);
        
        // 复制槽位
        for (int i = 0; i < capacity; i++) {
            ItemSlot originalSlot = slots[i];
            ItemSlot clonedSlot = cloned.slots[i];
            
            clonedSlot.setItemId(originalSlot.getItemId());
            clonedSlot.setQuantity(originalSlot.getQuantity());
            clonedSlot.setLocked(originalSlot.isLocked());
            clonedSlot.getProperties().putAll(originalSlot.getProperties());
            
            // 更新映射
            if (!clonedSlot.isEmpty()) {
                cloned.itemSlotMap.computeIfAbsent(clonedSlot.getItemId(), 
                        k -> ConcurrentHashMap.newKeySet()).add(i);
            }
        }
        
        // 复制其他属性
        cloned.autoSort = this.autoSort;
        cloned.maxStackSizes.putAll(this.maxStackSizes);
        cloned.setVersion(getVersion());
        
        return cloned;
    }
    
    // Getters and Setters
    public InventoryType getInventoryType() { return inventoryType; }
    public int getCapacity() { return capacity; }
    public boolean isAutoSort() { return autoSort; }
    public void setAutoSort(boolean autoSort) { this.autoSort = autoSort; }
    
    @Override
    public int getSize() {
        return capacity * 32 + // estimated slot size
               itemSlotMap.size() * 16; // estimated mapping size
    }
    
    @Override
    public String toString() {
        int usedSlots = capacity - getEmptySlotCount();
        return "InventoryComponent{" +
                "type=" + inventoryType +
                ", capacity=" + capacity +
                ", used=" + usedSlots +
                ", version=" + getVersion() +
                '}';
    }
}