/*
 * 文件名: EntityActor.java
 * 用途: 游戏实体Actor基类
 * 实现内容:
 *   - 游戏实体Actor基类实现
 *   - 位置管理和属性管理
 *   - 组件系统支持和事件发布
 *   - 通用实体功能抽象
 * 技术选型:
 *   - 继承GameActor基类
 *   - 组件化架构设计
 *   - 事件驱动的属性管理
 * 依赖关系:
 *   - 继承GameActor基类
 *   - 被具体实体类型继承
 *   - 与组件系统和事件系统集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.game;

import com.lx.gameserver.frame.actor.core.ActorRef;
import com.lx.gameserver.frame.actor.core.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏实体Actor基类
 * <p>
 * 为游戏中的各种实体提供通用功能，包括位置管理、
 * 属性管理、组件系统等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public abstract class EntityActor extends GameActor {
    
    protected static final Logger entityLogger = LoggerFactory.getLogger(EntityActor.class);
    
    /** 实体名称 */
    protected String entityName;
    
    /** 实体位置 */
    protected PlayerActor.Position position;
    
    /** 实体朝向 */
    protected float direction;
    
    /** 实体属性映射 */
    protected final ConcurrentHashMap<String, EntityAttribute> attributes = new ConcurrentHashMap<>();
    
    /** 实体组件映射 */
    protected final ConcurrentHashMap<String, EntityComponent> components = new ConcurrentHashMap<>();
    
    /** 事件监听器 */
    protected final ConcurrentHashMap<String, ActorRef> eventListeners = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     *
     * @param entityId   实体ID
     * @param entityType 实体类型
     * @param entityName 实体名称
     */
    protected EntityActor(Long entityId, GameEntityType entityType, String entityName) {
        super(entityId, entityType);
        this.entityName = entityName;
    }
    
    @Override
    public Receive createReceive() {
        return Receive.receiveBuilder()
                .match(UpdatePositionRequest.class, msg -> { handleUpdatePosition(msg); return null; })
                .match(UpdateAttributeRequest.class, msg -> { handleUpdateAttribute(msg); return null; })
                .match(GetAttributeRequest.class, msg -> { handleGetAttribute(msg); return null; })
                .match(AddComponentRequest.class, msg -> { handleAddComponent(msg); return null; })
                .match(RemoveComponentRequest.class, msg -> { handleRemoveComponent(msg); return null; })
                .match(GetComponentRequest.class, msg -> { handleGetComponent(msg); return null; })
                .match(PublishEventRequest.class, msg -> { handlePublishEvent(msg); return null; })
                .match(SubscribeEventRequest.class, msg -> { handleSubscribeEvent(msg); return null; })
                .match(UnsubscribeEventRequest.class, msg -> { handleUnsubscribeEvent(msg); return null; })
                .match(EntityInteractionRequest.class, msg -> { handleEntityInteraction(msg); return null; })
                .match(GameMessage.class, msg -> { handleGameMessage(msg); return null; })
                .match(GameStateUpdate.class, msg -> { handleStateUpdate(msg); return null; })
                .match(GameCommand.class, msg -> { handleGameCommand(msg); return null; })
                .match(SaveRequest.class, msg -> { performSave(); return null; })
                .match(HeartbeatRequest.class, msg -> { handleHeartbeat(msg); return null; })
                .matchAny(msg -> { handleOtherMessage(msg); return null; })
                .build();
    }
    
    /**
     * 处理位置更新请求
     *
     * @param request 位置更新请求
     */
    protected void handleUpdatePosition(UpdatePositionRequest request) {
        PlayerActor.Position newPosition = request.getNewPosition();
        float newDirection = request.getDirection();
        
        if (isValidPosition(newPosition)) {
            PlayerActor.Position oldPosition = this.position;
            this.position = newPosition;
            this.direction = newDirection;
            
            updateState("position", position);
            updateState("direction", direction);
            
            // 发布位置变更事件
            publishEvent(new EntityPositionChangedEvent(entityId, oldPosition, newPosition));
            
            entityLogger.debug("实体[{}:{}]位置更新: {}", entityType, entityId, newPosition);
        }
    }
    
    /**
     * 处理属性更新请求
     *
     * @param request 属性更新请求
     */
    protected void handleUpdateAttribute(UpdateAttributeRequest request) {
        String attributeName = request.getAttributeName();
        Object newValue = request.getNewValue();
        
        EntityAttribute oldAttribute = attributes.get(attributeName);
        Object oldValue = oldAttribute != null ? oldAttribute.getValue() : null;
        
        // 更新属性
        attributes.put(attributeName, new EntityAttribute(attributeName, newValue));
        updateState("attr_" + attributeName, newValue);
        
        // 发布属性变更事件
        publishEvent(new EntityAttributeChangedEvent(entityId, attributeName, oldValue, newValue));
        
        entityLogger.debug("实体[{}:{}]属性更新: {} = {}", entityType, entityId, attributeName, newValue);
    }
    
    /**
     * 处理获取属性请求
     *
     * @param request 获取属性请求
     */
    protected void handleGetAttribute(GetAttributeRequest request) {
        String attributeName = request.getAttributeName();
        EntityAttribute attribute = attributes.get(attributeName);
        
        Object value = attribute != null ? attribute.getValue() : null;
        
        if (getSender() != null && !getSender().equals(ActorRef.noSender())) {
            getSender().tell(new GetAttributeResponse(attributeName, value), getSelf());
        }
    }
    
    /**
     * 处理添加组件请求
     *
     * @param request 添加组件请求
     */
    protected void handleAddComponent(AddComponentRequest request) {
        String componentName = request.getComponentName();
        EntityComponent component = request.getComponent();
        
        components.put(componentName, component);
        
        // 初始化组件
        component.initialize(this);
        
        // 发布组件添加事件
        publishEvent(new EntityComponentAddedEvent(entityId, componentName, component));
        
        entityLogger.debug("实体[{}:{}]添加组件: {}", entityType, entityId, componentName);
    }
    
    /**
     * 处理移除组件请求
     *
     * @param request 移除组件请求
     */
    protected void handleRemoveComponent(RemoveComponentRequest request) {
        String componentName = request.getComponentName();
        EntityComponent component = components.remove(componentName);
        
        if (component != null) {
            // 销毁组件
            component.destroy();
            
            // 发布组件移除事件
            publishEvent(new EntityComponentRemovedEvent(entityId, componentName));
            
            entityLogger.debug("实体[{}:{}]移除组件: {}", entityType, entityId, componentName);
        }
    }
    
    /**
     * 处理获取组件请求
     *
     * @param request 获取组件请求
     */
    protected void handleGetComponent(GetComponentRequest request) {
        String componentName = request.getComponentName();
        EntityComponent component = components.get(componentName);
        
        if (getSender() != null && !getSender().equals(ActorRef.noSender())) {
            getSender().tell(new GetComponentResponse(componentName, component), getSelf());
        }
    }
    
    /**
     * 处理发布事件请求
     *
     * @param request 发布事件请求
     */
    protected void handlePublishEvent(PublishEventRequest request) {
        publishEvent(request.getEvent());
    }
    
    /**
     * 处理订阅事件请求
     *
     * @param request 订阅事件请求
     */
    protected void handleSubscribeEvent(SubscribeEventRequest request) {
        String eventType = request.getEventType();
        ActorRef listener = request.getListener();
        
        eventListeners.put(eventType + ":" + listener.getPath(), listener);
        
        entityLogger.debug("实体[{}:{}]事件订阅: {} -> {}", entityType, entityId, eventType, listener);
    }
    
    /**
     * 处理取消订阅事件请求
     *
     * @param request 取消订阅事件请求
     */
    protected void handleUnsubscribeEvent(UnsubscribeEventRequest request) {
        String eventType = request.getEventType();
        ActorRef listener = request.getListener();
        
        eventListeners.remove(eventType + ":" + listener.getPath());
        
        entityLogger.debug("实体[{}:{}]取消事件订阅: {} -> {}", entityType, entityId, eventType, listener);
    }
    
    /**
     * 处理实体交互请求
     *
     * @param request 实体交互请求
     */
    protected void handleEntityInteraction(EntityInteractionRequest request) {
        ActorRef interactor = request.getInteractor();
        String interactionType = request.getInteractionType();
        Object data = request.getData();
        
        try {
            processEntityInteraction(interactor, interactionType, data);
        } catch (Exception e) {
            entityLogger.error("实体[{}:{}]处理交互失败", entityType, entityId, e);
        }
    }
    
    @Override
    protected void handleGameMessage(GameMessage message) {
        processEntityGameMessage(message);
    }
    
    @Override
    protected void executeGameCommand(GameCommand command) {
        entityLogger.debug("实体[{}:{}]执行游戏命令: {}", entityType, entityId, command.getCommandType());
    }
    
    @Override
    protected void saveGameState() {
        saveEntityData();
    }
    
    /**
     * 发布事件到所有订阅者
     *
     * @param event 事件对象
     */
    protected void publishEvent(EntityEvent event) {
        String eventType = event.getEventType();
        
        eventListeners.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(eventType + ":"))
                .forEach(entry -> {
                    try {
                        entry.getValue().tell(event, getSelf());
                    } catch (Exception e) {
                        entityLogger.error("发布事件失败: {} -> {}", event, entry.getValue(), e);
                    }
                });
        
        entityLogger.debug("实体[{}:{}]发布事件: {}", entityType, entityId, eventType);
    }
    
    /**
     * 获取属性值
     *
     * @param attributeName 属性名称
     * @param <T>           属性类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attributeName) {
        EntityAttribute attribute = attributes.get(attributeName);
        return attribute != null ? (T) attribute.getValue() : null;
    }
    
    /**
     * 获取组件
     *
     * @param componentName 组件名称
     * @param <T>           组件类型
     * @return 组件实例
     */
    @SuppressWarnings("unchecked")
    public <T extends EntityComponent> T getComponent(String componentName) {
        return (T) components.get(componentName);
    }
    
    // 抽象方法，由具体实现类提供
    protected abstract boolean isValidPosition(PlayerActor.Position position);
    protected abstract void processEntityInteraction(ActorRef interactor, String interactionType, Object data);
    protected abstract void processEntityGameMessage(GameMessage message);
    protected abstract void saveEntityData();
    
    // Getters and Setters
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public PlayerActor.Position getPosition() {
        return position;
    }
    
    public float getDirection() {
        return direction;
    }
    
    public Map<String, EntityAttribute> getAttributes() {
        return Map.copyOf(attributes);
    }
    
    public Map<String, EntityComponent> getComponents() {
        return Map.copyOf(components);
    }
    
    /**
     * 实体属性类
     */
    public static class EntityAttribute {
        private final String name;
        private final Object value;
        
        public EntityAttribute(String name, Object value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public Object getValue() { return value; }
        
        @Override
        public String toString() {
            return String.format("EntityAttribute{name=%s, value=%s}", name, value);
        }
    }
    
    /**
     * 实体组件抽象类
     */
    public static abstract class EntityComponent {
        protected EntityActor owner;
        
        public void initialize(EntityActor owner) {
            this.owner = owner;
            onInitialize();
        }
        
        public void destroy() {
            onDestroy();
            this.owner = null;
        }
        
        protected abstract void onInitialize();
        protected abstract void onDestroy();
        
        public EntityActor getOwner() {
            return owner;
        }
    }
    
    /**
     * 实体事件基类
     */
    public static abstract class EntityEvent extends GameMessage {
        protected final Long entityId;
        
        protected EntityEvent(Long entityId) {
            this.entityId = entityId;
        }
        
        public Long getEntityId() {
            return entityId;
        }
        
        public abstract String getEventType();
    }
    
    // 具体事件类
    public static class EntityPositionChangedEvent extends EntityEvent {
        private final PlayerActor.Position oldPosition;
        private final PlayerActor.Position newPosition;
        
        public EntityPositionChangedEvent(Long entityId, PlayerActor.Position oldPosition, PlayerActor.Position newPosition) {
            super(entityId);
            this.oldPosition = oldPosition;
            this.newPosition = newPosition;
        }
        
        @Override
        public String getEventType() {
            return "POSITION_CHANGED";
        }
        
        public PlayerActor.Position getOldPosition() { return oldPosition; }
        public PlayerActor.Position getNewPosition() { return newPosition; }
    }
    
    public static class EntityAttributeChangedEvent extends EntityEvent {
        private final String attributeName;
        private final Object oldValue;
        private final Object newValue;
        
        public EntityAttributeChangedEvent(Long entityId, String attributeName, Object oldValue, Object newValue) {
            super(entityId);
            this.attributeName = attributeName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        
        @Override
        public String getEventType() {
            return "ATTRIBUTE_CHANGED";
        }
        
        public String getAttributeName() { return attributeName; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
    }
    
    public static class EntityComponentAddedEvent extends EntityEvent {
        private final String componentName;
        private final EntityComponent component;
        
        public EntityComponentAddedEvent(Long entityId, String componentName, EntityComponent component) {
            super(entityId);
            this.componentName = componentName;
            this.component = component;
        }
        
        @Override
        public String getEventType() {
            return "COMPONENT_ADDED";
        }
        
        public String getComponentName() { return componentName; }
        public EntityComponent getComponent() { return component; }
    }
    
    public static class EntityComponentRemovedEvent extends EntityEvent {
        private final String componentName;
        
        public EntityComponentRemovedEvent(Long entityId, String componentName) {
            super(entityId);
            this.componentName = componentName;
        }
        
        @Override
        public String getEventType() {
            return "COMPONENT_REMOVED";
        }
        
        public String getComponentName() { return componentName; }
    }
    
    // 消息类定义
    public static class UpdatePositionRequest extends GameMessage {
        private final PlayerActor.Position newPosition;
        private final float direction;
        
        public UpdatePositionRequest(PlayerActor.Position newPosition, float direction) {
            this.newPosition = newPosition;
            this.direction = direction;
        }
        
        public PlayerActor.Position getNewPosition() { return newPosition; }
        public float getDirection() { return direction; }
    }
    
    public static class UpdateAttributeRequest extends GameMessage {
        private final String attributeName;
        private final Object newValue;
        
        public UpdateAttributeRequest(String attributeName, Object newValue) {
            this.attributeName = attributeName;
            this.newValue = newValue;
        }
        
        public String getAttributeName() { return attributeName; }
        public Object getNewValue() { return newValue; }
    }
    
    public static class GetAttributeRequest extends GameMessage {
        private final String attributeName;
        
        public GetAttributeRequest(String attributeName) {
            this.attributeName = attributeName;
        }
        
        public String getAttributeName() { return attributeName; }
    }
    
    public static class GetAttributeResponse extends GameMessage {
        private final String attributeName;
        private final Object value;
        
        public GetAttributeResponse(String attributeName, Object value) {
            this.attributeName = attributeName;
            this.value = value;
        }
        
        public String getAttributeName() { return attributeName; }
        public Object getValue() { return value; }
    }
    
    public static class AddComponentRequest extends GameMessage {
        private final String componentName;
        private final EntityComponent component;
        
        public AddComponentRequest(String componentName, EntityComponent component) {
            this.componentName = componentName;
            this.component = component;
        }
        
        public String getComponentName() { return componentName; }
        public EntityComponent getComponent() { return component; }
    }
    
    public static class RemoveComponentRequest extends GameMessage {
        private final String componentName;
        
        public RemoveComponentRequest(String componentName) {
            this.componentName = componentName;
        }
        
        public String getComponentName() { return componentName; }
    }
    
    public static class GetComponentRequest extends GameMessage {
        private final String componentName;
        
        public GetComponentRequest(String componentName) {
            this.componentName = componentName;
        }
        
        public String getComponentName() { return componentName; }
    }
    
    public static class GetComponentResponse extends GameMessage {
        private final String componentName;
        private final EntityComponent component;
        
        public GetComponentResponse(String componentName, EntityComponent component) {
            this.componentName = componentName;
            this.component = component;
        }
        
        public String getComponentName() { return componentName; }
        public EntityComponent getComponent() { return component; }
    }
    
    public static class PublishEventRequest extends GameMessage {
        private final EntityEvent event;
        
        public PublishEventRequest(EntityEvent event) {
            this.event = event;
        }
        
        public EntityEvent getEvent() { return event; }
    }
    
    public static class SubscribeEventRequest extends GameMessage {
        private final String eventType;
        private final ActorRef listener;
        
        public SubscribeEventRequest(String eventType, ActorRef listener) {
            this.eventType = eventType;
            this.listener = listener;
        }
        
        public String getEventType() { return eventType; }
        public ActorRef getListener() { return listener; }
    }
    
    public static class UnsubscribeEventRequest extends GameMessage {
        private final String eventType;
        private final ActorRef listener;
        
        public UnsubscribeEventRequest(String eventType, ActorRef listener) {
            this.eventType = eventType;
            this.listener = listener;
        }
        
        public String getEventType() { return eventType; }
        public ActorRef getListener() { return listener; }
    }
    
    public static class EntityInteractionRequest extends GameMessage {
        private final ActorRef interactor;
        private final String interactionType;
        private final Object data;
        
        public EntityInteractionRequest(ActorRef interactor, String interactionType, Object data) {
            this.interactor = interactor;
            this.interactionType = interactionType;
            this.data = data;
        }
        
        public ActorRef getInteractor() { return interactor; }
        public String getInteractionType() { return interactionType; }
        public Object getData() { return data; }
    }
}