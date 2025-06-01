/*
 * 文件名: GameServiceInstance.java
 * 用途: 游戏服务实例扩展
 * 实现内容:
 *   - 添加游戏特定元数据（服务器ID、区服信息）
 *   - 负载信息（在线人数、CPU、内存）
 *   - 服务版本信息
 *   - 服务标签（用于灰度发布）
 * 技术选型:
 *   - 实现Spring Cloud ServiceInstance接口
 *   - 扩展游戏相关属性
 *   - 支持动态更新
 * 依赖关系:
 *   - 实现ServiceInstance接口
 *   - 被负载均衡器使用
 * 作者: liuxiao2015
 * 日期: 2025-01-13
 */
package com.lx.gameserver.frame.rpc.discovery;

import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 游戏服务实例扩展类
 * <p>
 * 在标准ServiceInstance基础上添加游戏相关的元数据，
 * 包括服务器负载、在线玩家数、版本信息等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-01-13
 */
public class GameServiceInstance implements ServiceInstance {

    /**
     * 服务ID
     */
    private String serviceId;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private int port;

    /**
     * 是否使用HTTPS
     */
    private boolean secure;

    /**
     * 元数据
     */
    private Map<String, String> metadata = new HashMap<>();

    /**
     * 游戏服务器ID
     */
    private String serverId;

    /**
     * 区服ID
     */
    private String zoneId;

    /**
     * 服务器类型（gateway/login/game/chat等）
     */
    private String serverType;

    /**
     * 在线玩家数
     */
    private int onlinePlayerCount;

    /**
     * 最大玩家数
     */
    private int maxPlayerCount;

    /**
     * CPU使用率（百分比）
     */
    private double cpuUsage;

    /**
     * 内存使用率（百分比）
     */
    private double memoryUsage;

    /**
     * 服务版本
     */
    private String version;

    /**
     * 服务标签（用于灰度发布）
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * 权重（用于负载均衡）
     */
    private int weight = 100;

    /**
     * 服务状态
     */
    private ServiceStatus status = ServiceStatus.UP;

    /**
     * 最后更新时间
     */
    private long lastUpdateTime;

    /**
     * 服务状态枚举
     */
    public enum ServiceStatus {
        UP("UP"),
        DOWN("DOWN"),
        OUT_OF_SERVICE("OUT_OF_SERVICE"),
        STARTING("STARTING"),
        STOPPING("STOPPING");

        private final String value;

        ServiceStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 默认构造函数
     */
    public GameServiceInstance() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 构造函数
     *
     * @param serviceId 服务ID
     * @param host      主机地址
     * @param port      端口号
     */
    public GameServiceInstance(String serviceId, String host, int port) {
        this();
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.instanceId = host + ":" + port;
    }

    /**
     * 构造函数
     *
     * @param serviceId  服务ID
     * @param instanceId 实例ID
     * @param host       主机地址
     * @param port       端口号
     * @param secure     是否安全连接
     */
    public GameServiceInstance(String serviceId, String instanceId, String host, int port, boolean secure) {
        this();
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
    }

    // ===== ServiceInstance 接口实现 =====

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public URI getUri() {
        String scheme = secure ? "https" : "http";
        return URI.create(String.format("%s://%s:%d", scheme, host, port));
    }

    @Override
    public Map<String, String> getMetadata() {
        // 将游戏相关信息添加到元数据中
        Map<String, String> allMetadata = new HashMap<>(metadata);
        
        if (serverId != null) allMetadata.put("serverId", serverId);
        if (zoneId != null) allMetadata.put("zoneId", zoneId);
        if (serverType != null) allMetadata.put("serverType", serverType);
        if (version != null) allMetadata.put("version", version);
        
        allMetadata.put("onlinePlayerCount", String.valueOf(onlinePlayerCount));
        allMetadata.put("maxPlayerCount", String.valueOf(maxPlayerCount));
        allMetadata.put("cpuUsage", String.valueOf(cpuUsage));
        allMetadata.put("memoryUsage", String.valueOf(memoryUsage));
        allMetadata.put("weight", String.valueOf(weight));
        allMetadata.put("status", status.getValue());
        allMetadata.put("lastUpdateTime", String.valueOf(lastUpdateTime));
        
        // 添加标签
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            allMetadata.put("tag." + tag.getKey(), tag.getValue());
        }
        
        return allMetadata;
    }

    // ===== 游戏扩展属性的 Getter/Setter =====

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
        updateTimestamp();
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
        updateTimestamp();
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
        updateTimestamp();
    }

    public int getOnlinePlayerCount() {
        return onlinePlayerCount;
    }

    public void setOnlinePlayerCount(int onlinePlayerCount) {
        this.onlinePlayerCount = onlinePlayerCount;
        updateTimestamp();
    }

    public int getMaxPlayerCount() {
        return maxPlayerCount;
    }

    public void setMaxPlayerCount(int maxPlayerCount) {
        this.maxPlayerCount = maxPlayerCount;
        updateTimestamp();
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
        updateTimestamp();
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
        updateTimestamp();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        updateTimestamp();
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
        updateTimestamp();
    }

    public void addTag(String key, String value) {
        this.tags.put(key, value);
        updateTimestamp();
    }

    public void removeTag(String key) {
        this.tags.remove(key);
        updateTimestamp();
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
        updateTimestamp();
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
        updateTimestamp();
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    // ===== 基础属性的 Setter =====

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
        updateTimestamp();
    }

    public void removeMetadata(String key) {
        this.metadata.remove(key);
        updateTimestamp();
    }

    // ===== 工具方法 =====

    /**
     * 更新时间戳
     */
    private void updateTimestamp() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 检查实例是否可用
     */
    public boolean isAvailable() {
        return status == ServiceStatus.UP;
    }

    /**
     * 检查是否匹配标签
     */
    public boolean matchesTag(String key, String value) {
        return Objects.equals(tags.get(key), value);
    }

    /**
     * 检查是否匹配版本
     */
    public boolean matchesVersion(String targetVersion) {
        return Objects.equals(version, targetVersion);
    }

    /**
     * 获取负载率（在线人数/最大人数）
     */
    public double getLoadRatio() {
        if (maxPlayerCount <= 0) {
            return 0.0;
        }
        return (double) onlinePlayerCount / maxPlayerCount;
    }

    /**
     * 检查是否高负载
     */
    public boolean isHighLoad() {
        return cpuUsage > 80.0 || memoryUsage > 80.0 || getLoadRatio() > 0.8;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameServiceInstance that = (GameServiceInstance) o;
        return Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(instanceId, that.instanceId) &&
                Objects.equals(host, that.host) &&
                port == that.port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, instanceId, host, port);
    }

    @Override
    public String toString() {
        return "GameServiceInstance{" +
                "serviceId='" + serviceId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", serverId='" + serverId + '\'' +
                ", serverType='" + serverType + '\'' +
                ", onlinePlayerCount=" + onlinePlayerCount +
                ", maxPlayerCount=" + maxPlayerCount +
                ", cpuUsage=" + cpuUsage +
                ", memoryUsage=" + memoryUsage +
                ", status=" + status +
                '}';
    }
}