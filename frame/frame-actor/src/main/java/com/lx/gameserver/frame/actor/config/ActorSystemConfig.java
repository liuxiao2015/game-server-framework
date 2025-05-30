/*
 * 文件名: ActorSystemConfig.java
 * 用途: Actor系统配置管理
 * 实现内容:
 *   - 系统级配置和调度器配置
 *   - 邮箱配置和监督策略配置
 *   - 集群配置和持久化配置
 *   - 配置验证和热更新支持
 * 技术选型:
 *   - Builder模式提供灵活配置
 *   - 配置验证和默认值管理
 *   - 支持外部配置文件加载
 * 依赖关系:
 *   - 被ActorSystem使用
 *   - 与各模块配置集成
 *   - 支持配置中心和环境变量
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.actor.config;

import com.lx.gameserver.frame.actor.cluster.ActorSharding;
import com.lx.gameserver.frame.actor.cluster.ClusterActorSystem;
import com.lx.gameserver.frame.actor.monitor.ActorMonitor;
import com.lx.gameserver.frame.actor.monitor.MessageTracer;
import com.lx.gameserver.frame.actor.persistence.ActorPersistence;
import com.lx.gameserver.frame.actor.persistence.ActorRecovery;
import com.lx.gameserver.frame.actor.system.ActorMailbox;
import com.lx.gameserver.frame.actor.system.Dispatcher;
import com.lx.gameserver.frame.actor.supervision.SupervisorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;

/**
 * Actor系统配置管理
 * <p>
 * 提供Actor系统的完整配置管理，包括系统级配置、
 * 各模块配置、验证和热更新等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public class ActorSystemConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorSystemConfig.class);
    
    /** 系统名称 */
    private final String systemName;
    
    /** 调度器配置 */
    private final DispatcherConfig dispatcherConfig;
    
    /** 邮箱配置 */
    private final MailboxConfig mailboxConfig;
    
    /** 监督策略配置 */
    private final SupervisionConfig supervisionConfig;
    
    /** 集群配置 */
    private final ClusterConfig clusterConfig;
    
    /** 持久化配置 */
    private final PersistenceConfig persistenceConfig;
    
    /** 监控配置 */
    private final MonitoringConfig monitoringConfig;
    
    /** 性能配置 */
    private final PerformanceConfig performanceConfig;
    
    /** 安全配置 */
    private final SecurityConfig securityConfig;
    
    /** 扩展配置 */
    private final Map<String, Object> extensions;
    
    private ActorSystemConfig(Builder builder) {
        this.systemName = builder.systemName;
        this.dispatcherConfig = builder.dispatcherConfig;
        this.mailboxConfig = builder.mailboxConfig;
        this.supervisionConfig = builder.supervisionConfig;
        this.clusterConfig = builder.clusterConfig;
        this.persistenceConfig = builder.persistenceConfig;
        this.monitoringConfig = builder.monitoringConfig;
        this.performanceConfig = builder.performanceConfig;
        this.securityConfig = builder.securityConfig;
        this.extensions = Collections.unmodifiableMap(new HashMap<>(builder.extensions));
        
        validate();
        logger.info("Actor系统配置创建完成: {}", systemName);
    }
    
    /**
     * 创建默认配置
     */
    public static ActorSystemConfig defaultConfig(String systemName) {
        return builder(systemName).build();
    }
    
    /**
     * 创建配置构建器
     */
    public static Builder builder(String systemName) {
        return new Builder(systemName);
    }
    
    /**
     * 从配置文件加载
     */
    public static ActorSystemConfig fromProperties(String systemName, Properties properties) {
        Builder builder = builder(systemName);
        
        // 解析调度器配置
        String dispatcherType = properties.getProperty("actor.dispatcher.type", "virtual-thread");
        int parallelismMin = Integer.parseInt(properties.getProperty("actor.dispatcher.parallelism-min", "8"));
        int parallelismMax = Integer.parseInt(properties.getProperty("actor.dispatcher.parallelism-max", "64"));
        int throughput = Integer.parseInt(properties.getProperty("actor.dispatcher.throughput", "100"));
        
        builder.dispatcher(DispatcherConfig.builder()
                .type(Dispatcher.DispatcherType.valueOf(dispatcherType.toUpperCase().replace("-", "_")))
                .parallelismMin(parallelismMin)
                .parallelismMax(parallelismMax)
                .throughput(throughput)
                .build());
        
        // 解析邮箱配置
        String mailboxType = properties.getProperty("actor.mailbox.type", "bounded");
        int capacity = Integer.parseInt(properties.getProperty("actor.mailbox.capacity", "10000"));
        Duration pushTimeout = Duration.parse("PT" + properties.getProperty("actor.mailbox.push-timeout", "10ms"));
        
        builder.mailbox(MailboxConfig.builder()
                .type(ActorMailbox.MailboxType.valueOf(mailboxType.toUpperCase()))
                .capacity(capacity)
                .pushTimeout(pushTimeout)
                .build());
        
        // 解析监督配置
        String strategy = properties.getProperty("actor.supervisor.strategy", "one-for-one");
        int maxRetries = Integer.parseInt(properties.getProperty("actor.supervisor.max-retries", "3"));
        Duration withinTime = Duration.parse("PT" + properties.getProperty("actor.supervisor.within-time", "1m"));
        
        builder.supervision(SupervisionConfig.builder()
                .strategy(strategy)
                .maxRetries(maxRetries)
                .withinTime(withinTime)
                .build());
        
        // 解析集群配置
        boolean clusterEnabled = Boolean.parseBoolean(properties.getProperty("actor.cluster.enabled", "false"));
        if (clusterEnabled) {
            String nodeId = properties.getProperty("actor.cluster.node-id", systemName + "-" + UUID.randomUUID().toString().substring(0, 8));
            String bindHost = properties.getProperty("actor.cluster.bind-host", "127.0.0.1");
            int bindPort = Integer.parseInt(properties.getProperty("actor.cluster.bind-port", "2551"));
            
            builder.cluster(ClusterConfig.builder()
                    .enabled(true)
                    .nodeId(nodeId)
                    .bindAddress(new InetSocketAddress(bindHost, bindPort))
                    .build());
        }
        
        // 解析监控配置
        boolean metricsEnabled = Boolean.parseBoolean(properties.getProperty("actor.metrics.enabled", "true"));
        Duration exportInterval = Duration.parse("PT" + properties.getProperty("actor.metrics.export-interval", "10s"));
        
        builder.monitoring(MonitoringConfig.builder()
                .metricsEnabled(metricsEnabled)
                .exportInterval(exportInterval)
                .build());
        
        return builder.build();
    }
    
    /**
     * 验证配置
     */
    private void validate() {
        if (systemName == null || systemName.trim().isEmpty()) {
            throw new IllegalArgumentException("系统名称不能为空");
        }
        
        if (dispatcherConfig == null) {
            throw new IllegalArgumentException("调度器配置不能为空");
        }
        
        if (mailboxConfig == null) {
            throw new IllegalArgumentException("邮箱配置不能为空");
        }
        
        // 验证集群配置
        if (clusterConfig.isEnabled()) {
            if (clusterConfig.getNodeId() == null) {
                throw new IllegalArgumentException("集群模式下节点ID不能为空");
            }
            if (clusterConfig.getBindAddress() == null) {
                throw new IllegalArgumentException("集群模式下绑定地址不能为空");
            }
        }
        
        logger.debug("Actor系统配置验证通过");
    }
    
    // Getters
    public String getSystemName() { return systemName; }
    public DispatcherConfig getDispatcherConfig() { return dispatcherConfig; }
    public MailboxConfig getMailboxConfig() { return mailboxConfig; }
    public SupervisionConfig getSupervisionConfig() { return supervisionConfig; }
    public ClusterConfig getClusterConfig() { return clusterConfig; }
    public PersistenceConfig getPersistenceConfig() { return persistenceConfig; }
    public MonitoringConfig getMonitoringConfig() { return monitoringConfig; }
    public PerformanceConfig getPerformanceConfig() { return performanceConfig; }
    public SecurityConfig getSecurityConfig() { return securityConfig; }
    public Map<String, Object> getExtensions() { return extensions; }
    
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, Class<T> type) {
        Object value = extensions.get(key);
        return type.isInstance(value) ? (T) value : null;
    }
    
    /**
     * 配置构建器
     */
    public static class Builder {
        private final String systemName;
        private DispatcherConfig dispatcherConfig = DispatcherConfig.defaultConfig();
        private MailboxConfig mailboxConfig = MailboxConfig.defaultConfig();
        private SupervisionConfig supervisionConfig = SupervisionConfig.defaultConfig();
        private ClusterConfig clusterConfig = ClusterConfig.defaultConfig();
        private PersistenceConfig persistenceConfig = PersistenceConfig.defaultConfig();
        private MonitoringConfig monitoringConfig = MonitoringConfig.defaultConfig();
        private PerformanceConfig performanceConfig = PerformanceConfig.defaultConfig();
        private SecurityConfig securityConfig = SecurityConfig.defaultConfig();
        private final Map<String, Object> extensions = new HashMap<>();
        
        private Builder(String systemName) {
            this.systemName = systemName;
        }
        
        public Builder dispatcher(DispatcherConfig config) {
            this.dispatcherConfig = config;
            return this;
        }
        
        public Builder mailbox(MailboxConfig config) {
            this.mailboxConfig = config;
            return this;
        }
        
        public Builder supervision(SupervisionConfig config) {
            this.supervisionConfig = config;
            return this;
        }
        
        public Builder cluster(ClusterConfig config) {
            this.clusterConfig = config;
            return this;
        }
        
        public Builder persistence(PersistenceConfig config) {
            this.persistenceConfig = config;
            return this;
        }
        
        public Builder monitoring(MonitoringConfig config) {
            this.monitoringConfig = config;
            return this;
        }
        
        public Builder performance(PerformanceConfig config) {
            this.performanceConfig = config;
            return this;
        }
        
        public Builder security(SecurityConfig config) {
            this.securityConfig = config;
            return this;
        }
        
        public Builder extension(String key, Object value) {
            this.extensions.put(key, value);
            return this;
        }
        
        public ActorSystemConfig build() {
            return new ActorSystemConfig(this);
        }
    }
    
    /**
     * 调度器配置
     */
    public static class DispatcherConfig {
        private final Dispatcher.DispatcherType type;
        private final int parallelismMin;
        private final int parallelismMax;
        private final int throughput;
        private final Duration keepAliveTime;
        private final boolean allowCoreThreadTimeout;
        
        private DispatcherConfig(Builder builder) {
            this.type = builder.type;
            this.parallelismMin = builder.parallelismMin;
            this.parallelismMax = builder.parallelismMax;
            this.throughput = builder.throughput;
            this.keepAliveTime = builder.keepAliveTime;
            this.allowCoreThreadTimeout = builder.allowCoreThreadTimeout;
        }
        
        public static DispatcherConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private Dispatcher.DispatcherType type = Dispatcher.DispatcherType.VIRTUAL_THREAD;
            private int parallelismMin = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
            private int parallelismMax = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
            private int throughput = 100;
            private Duration keepAliveTime = Duration.ofSeconds(60);
            private boolean allowCoreThreadTimeout = false;
            
            public Builder type(Dispatcher.DispatcherType type) {
                this.type = type;
                return this;
            }
            
            public Builder parallelismMin(int parallelismMin) {
                this.parallelismMin = parallelismMin;
                return this;
            }
            
            public Builder parallelismMax(int parallelismMax) {
                this.parallelismMax = parallelismMax;
                return this;
            }
            
            public Builder throughput(int throughput) {
                this.throughput = throughput;
                return this;
            }
            
            public Builder keepAliveTime(Duration keepAliveTime) {
                this.keepAliveTime = keepAliveTime;
                return this;
            }
            
            public Builder allowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
                this.allowCoreThreadTimeout = allowCoreThreadTimeout;
                return this;
            }
            
            public DispatcherConfig build() {
                return new DispatcherConfig(this);
            }
        }
        
        // Getters
        public Dispatcher.DispatcherType getType() { return type; }
        public int getParallelismMin() { return parallelismMin; }
        public int getParallelismMax() { return parallelismMax; }
        public int getThroughput() { return throughput; }
        public Duration getKeepAliveTime() { return keepAliveTime; }
        public boolean isAllowCoreThreadTimeout() { return allowCoreThreadTimeout; }
    }
    
    /**
     * 邮箱配置
     */
    public static class MailboxConfig {
        private final ActorMailbox.MailboxType type;
        private final int capacity;
        private final Duration pushTimeout;
        private final boolean enablePriority;
        private final boolean enableBackpressure;
        
        private MailboxConfig(Builder builder) {
            this.type = builder.type;
            this.capacity = builder.capacity;
            this.pushTimeout = builder.pushTimeout;
            this.enablePriority = builder.enablePriority;
            this.enableBackpressure = builder.enableBackpressure;
        }
        
        public static MailboxConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private ActorMailbox.MailboxType type = ActorMailbox.MailboxType.BOUNDED;
            private int capacity = 10000;
            private Duration pushTimeout = Duration.ofMillis(10);
            private boolean enablePriority = false;
            private boolean enableBackpressure = true;
            
            public Builder type(ActorMailbox.MailboxType type) {
                this.type = type;
                return this;
            }
            
            public Builder capacity(int capacity) {
                this.capacity = capacity;
                return this;
            }
            
            public Builder pushTimeout(Duration pushTimeout) {
                this.pushTimeout = pushTimeout;
                return this;
            }
            
            public Builder enablePriority(boolean enablePriority) {
                this.enablePriority = enablePriority;
                return this;
            }
            
            public Builder enableBackpressure(boolean enableBackpressure) {
                this.enableBackpressure = enableBackpressure;
                return this;
            }
            
            public MailboxConfig build() {
                return new MailboxConfig(this);
            }
        }
        
        // Getters
        public ActorMailbox.MailboxType getType() { return type; }
        public int getCapacity() { return capacity; }
        public Duration getPushTimeout() { return pushTimeout; }
        public boolean isEnablePriority() { return enablePriority; }
        public boolean isEnableBackpressure() { return enableBackpressure; }
    }
    
    /**
     * 监督配置
     */
    public static class SupervisionConfig {
        private final String strategy;
        private final int maxRetries;
        private final Duration withinTime;
        private final Duration escalationTimeout;
        private final boolean logFailures;
        
        private SupervisionConfig(Builder builder) {
            this.strategy = builder.strategy;
            this.maxRetries = builder.maxRetries;
            this.withinTime = builder.withinTime;
            this.escalationTimeout = builder.escalationTimeout;
            this.logFailures = builder.logFailures;
        }
        
        public static SupervisionConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String strategy = "one-for-one";
            private int maxRetries = 3;
            private Duration withinTime = Duration.ofMinutes(1);
            private Duration escalationTimeout = Duration.ofSeconds(30);
            private boolean logFailures = true;
            
            public Builder strategy(String strategy) {
                this.strategy = strategy;
                return this;
            }
            
            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }
            
            public Builder withinTime(Duration withinTime) {
                this.withinTime = withinTime;
                return this;
            }
            
            public Builder escalationTimeout(Duration escalationTimeout) {
                this.escalationTimeout = escalationTimeout;
                return this;
            }
            
            public Builder logFailures(boolean logFailures) {
                this.logFailures = logFailures;
                return this;
            }
            
            public SupervisionConfig build() {
                return new SupervisionConfig(this);
            }
        }
        
        // Getters
        public String getStrategy() { return strategy; }
        public int getMaxRetries() { return maxRetries; }
        public Duration getWithinTime() { return withinTime; }
        public Duration getEscalationTimeout() { return escalationTimeout; }
        public boolean isLogFailures() { return logFailures; }
    }
    
    /**
     * 集群配置
     */
    public static class ClusterConfig {
        private final boolean enabled;
        private final String nodeId;
        private final InetSocketAddress bindAddress;
        private final Set<String> roles;
        private final List<InetSocketAddress> seedNodes;
        private final ActorSharding.ShardingConfig shardingConfig;
        private final int heartbeatInterval;
        private final int nodeTimeout;
        
        private ClusterConfig(Builder builder) {
            this.enabled = builder.enabled;
            this.nodeId = builder.nodeId;
            this.bindAddress = builder.bindAddress;
            this.roles = Collections.unmodifiableSet(new HashSet<>(builder.roles));
            this.seedNodes = Collections.unmodifiableList(new ArrayList<>(builder.seedNodes));
            this.shardingConfig = builder.shardingConfig;
            this.heartbeatInterval = builder.heartbeatInterval;
            this.nodeTimeout = builder.nodeTimeout;
        }
        
        public static ClusterConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean enabled = false;
            private String nodeId;
            private InetSocketAddress bindAddress;
            private final Set<String> roles = new HashSet<>();
            private final List<InetSocketAddress> seedNodes = new ArrayList<>();
            private ActorSharding.ShardingConfig shardingConfig = ActorSharding.ShardingConfig.defaultConfig();
            private int heartbeatInterval = 10;
            private int nodeTimeout = 30;
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder nodeId(String nodeId) {
                this.nodeId = nodeId;
                return this;
            }
            
            public Builder bindAddress(InetSocketAddress bindAddress) {
                this.bindAddress = bindAddress;
                return this;
            }
            
            public Builder role(String role) {
                this.roles.add(role);
                return this;
            }
            
            public Builder roles(Set<String> roles) {
                this.roles.addAll(roles);
                return this;
            }
            
            public Builder seedNode(InetSocketAddress seedNode) {
                this.seedNodes.add(seedNode);
                return this;
            }
            
            public Builder seedNodes(List<InetSocketAddress> seedNodes) {
                this.seedNodes.addAll(seedNodes);
                return this;
            }
            
            public Builder sharding(ActorSharding.ShardingConfig shardingConfig) {
                this.shardingConfig = shardingConfig;
                return this;
            }
            
            public Builder heartbeatInterval(int heartbeatInterval) {
                this.heartbeatInterval = heartbeatInterval;
                return this;
            }
            
            public Builder nodeTimeout(int nodeTimeout) {
                this.nodeTimeout = nodeTimeout;
                return this;
            }
            
            public ClusterConfig build() {
                return new ClusterConfig(this);
            }
        }
        
        // Getters
        public boolean isEnabled() { return enabled; }
        public String getNodeId() { return nodeId; }
        public InetSocketAddress getBindAddress() { return bindAddress; }
        public Set<String> getRoles() { return roles; }
        public List<InetSocketAddress> getSeedNodes() { return seedNodes; }
        public ActorSharding.ShardingConfig getShardingConfig() { return shardingConfig; }
        public int getHeartbeatInterval() { return heartbeatInterval; }
        public int getNodeTimeout() { return nodeTimeout; }
    }
    
    /**
     * 持久化配置
     */
    public static class PersistenceConfig {
        private final boolean enabled;
        private final String storeType;
        private final ActorPersistence.PersistenceConfig persistenceConfig;
        private final ActorRecovery.RecoveryConfig recoveryConfig;
        private final Map<String, Object> storeSettings;
        
        private PersistenceConfig(Builder builder) {
            this.enabled = builder.enabled;
            this.storeType = builder.storeType;
            this.persistenceConfig = builder.persistenceConfig;
            this.recoveryConfig = builder.recoveryConfig;
            this.storeSettings = Collections.unmodifiableMap(new HashMap<>(builder.storeSettings));
        }
        
        public static PersistenceConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean enabled = false;
            private String storeType = "memory";
            private ActorPersistence.PersistenceConfig persistenceConfig = ActorPersistence.PersistenceConfig.defaultConfig();
            private ActorRecovery.RecoveryConfig recoveryConfig = ActorRecovery.RecoveryConfig.defaultConfig();
            private final Map<String, Object> storeSettings = new HashMap<>();
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder storeType(String storeType) {
                this.storeType = storeType;
                return this;
            }
            
            public Builder persistenceConfig(ActorPersistence.PersistenceConfig persistenceConfig) {
                this.persistenceConfig = persistenceConfig;
                return this;
            }
            
            public Builder recoveryConfig(ActorRecovery.RecoveryConfig recoveryConfig) {
                this.recoveryConfig = recoveryConfig;
                return this;
            }
            
            public Builder storeSetting(String key, Object value) {
                this.storeSettings.put(key, value);
                return this;
            }
            
            public PersistenceConfig build() {
                return new PersistenceConfig(this);
            }
        }
        
        // Getters
        public boolean isEnabled() { return enabled; }
        public String getStoreType() { return storeType; }
        public ActorPersistence.PersistenceConfig getPersistenceConfig() { return persistenceConfig; }
        public ActorRecovery.RecoveryConfig getRecoveryConfig() { return recoveryConfig; }
        public Map<String, Object> getStoreSettings() { return storeSettings; }
    }
    
    /**
     * 监控配置
     */
    public static class MonitoringConfig {
        private final boolean metricsEnabled;
        private final Duration exportInterval;
        private final ActorMonitor.MonitorConfig monitorConfig;
        private final MessageTracer.MessageTraceConfig traceConfig;
        private final boolean jmxEnabled;
        private final String metricsPrefix;
        
        private MonitoringConfig(Builder builder) {
            this.metricsEnabled = builder.metricsEnabled;
            this.exportInterval = builder.exportInterval;
            this.monitorConfig = builder.monitorConfig;
            this.traceConfig = builder.traceConfig;
            this.jmxEnabled = builder.jmxEnabled;
            this.metricsPrefix = builder.metricsPrefix;
        }
        
        public static MonitoringConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean metricsEnabled = true;
            private Duration exportInterval = Duration.ofSeconds(10);
            private ActorMonitor.MonitorConfig monitorConfig = ActorMonitor.MonitorConfig.defaultConfig();
            private MessageTracer.MessageTraceConfig traceConfig = MessageTracer.MessageTraceConfig.defaultConfig();
            private boolean jmxEnabled = false;
            private String metricsPrefix = "actor";
            
            public Builder metricsEnabled(boolean metricsEnabled) {
                this.metricsEnabled = metricsEnabled;
                return this;
            }
            
            public Builder exportInterval(Duration exportInterval) {
                this.exportInterval = exportInterval;
                return this;
            }
            
            public Builder monitorConfig(ActorMonitor.MonitorConfig monitorConfig) {
                this.monitorConfig = monitorConfig;
                return this;
            }
            
            public Builder traceConfig(MessageTracer.MessageTraceConfig traceConfig) {
                this.traceConfig = traceConfig;
                return this;
            }
            
            public Builder jmxEnabled(boolean jmxEnabled) {
                this.jmxEnabled = jmxEnabled;
                return this;
            }
            
            public Builder metricsPrefix(String metricsPrefix) {
                this.metricsPrefix = metricsPrefix;
                return this;
            }
            
            public MonitoringConfig build() {
                return new MonitoringConfig(this);
            }
        }
        
        // Getters
        public boolean isMetricsEnabled() { return metricsEnabled; }
        public Duration getExportInterval() { return exportInterval; }
        public ActorMonitor.MonitorConfig getMonitorConfig() { return monitorConfig; }
        public MessageTracer.MessageTraceConfig getTraceConfig() { return traceConfig; }
        public boolean isJmxEnabled() { return jmxEnabled; }
        public String getMetricsPrefix() { return metricsPrefix; }
    }
    
    /**
     * 性能配置
     */
    public static class PerformanceConfig {
        private final boolean enableOptimizations;
        private final int batchSize;
        private final Duration processingTimeout;
        private final boolean enableJitCompilation;
        private final int memoryPoolSize;
        
        private PerformanceConfig(Builder builder) {
            this.enableOptimizations = builder.enableOptimizations;
            this.batchSize = builder.batchSize;
            this.processingTimeout = builder.processingTimeout;
            this.enableJitCompilation = builder.enableJitCompilation;
            this.memoryPoolSize = builder.memoryPoolSize;
        }
        
        public static PerformanceConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean enableOptimizations = true;
            private int batchSize = 100;
            private Duration processingTimeout = Duration.ofSeconds(30);
            private boolean enableJitCompilation = true;
            private int memoryPoolSize = 1024;
            
            public Builder enableOptimizations(boolean enableOptimizations) {
                this.enableOptimizations = enableOptimizations;
                return this;
            }
            
            public Builder batchSize(int batchSize) {
                this.batchSize = batchSize;
                return this;
            }
            
            public Builder processingTimeout(Duration processingTimeout) {
                this.processingTimeout = processingTimeout;
                return this;
            }
            
            public Builder enableJitCompilation(boolean enableJitCompilation) {
                this.enableJitCompilation = enableJitCompilation;
                return this;
            }
            
            public Builder memoryPoolSize(int memoryPoolSize) {
                this.memoryPoolSize = memoryPoolSize;
                return this;
            }
            
            public PerformanceConfig build() {
                return new PerformanceConfig(this);
            }
        }
        
        // Getters
        public boolean isEnableOptimizations() { return enableOptimizations; }
        public int getBatchSize() { return batchSize; }
        public Duration getProcessingTimeout() { return processingTimeout; }
        public boolean isEnableJitCompilation() { return enableJitCompilation; }
        public int getMemoryPoolSize() { return memoryPoolSize; }
    }
    
    /**
     * 安全配置
     */
    public static class SecurityConfig {
        private final boolean enableSecurity;
        private final boolean enableEncryption;
        private final boolean enableAuthentication;
        private final boolean enableAuthorization;
        private final String securityProvider;
        
        private SecurityConfig(Builder builder) {
            this.enableSecurity = builder.enableSecurity;
            this.enableEncryption = builder.enableEncryption;
            this.enableAuthentication = builder.enableAuthentication;
            this.enableAuthorization = builder.enableAuthorization;
            this.securityProvider = builder.securityProvider;
        }
        
        public static SecurityConfig defaultConfig() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean enableSecurity = false;
            private boolean enableEncryption = false;
            private boolean enableAuthentication = false;
            private boolean enableAuthorization = false;
            private String securityProvider = "default";
            
            public Builder enableSecurity(boolean enableSecurity) {
                this.enableSecurity = enableSecurity;
                return this;
            }
            
            public Builder enableEncryption(boolean enableEncryption) {
                this.enableEncryption = enableEncryption;
                return this;
            }
            
            public Builder enableAuthentication(boolean enableAuthentication) {
                this.enableAuthentication = enableAuthentication;
                return this;
            }
            
            public Builder enableAuthorization(boolean enableAuthorization) {
                this.enableAuthorization = enableAuthorization;
                return this;
            }
            
            public Builder securityProvider(String securityProvider) {
                this.securityProvider = securityProvider;
                return this;
            }
            
            public SecurityConfig build() {
                return new SecurityConfig(this);
            }
        }
        
        // Getters
        public boolean isEnableSecurity() { return enableSecurity; }
        public boolean isEnableEncryption() { return enableEncryption; }
        public boolean isEnableAuthentication() { return enableAuthentication; }
        public boolean isEnableAuthorization() { return enableAuthorization; }
        public String getSecurityProvider() { return securityProvider; }
    }
    
    @Override
    public String toString() {
        return String.format("ActorSystemConfig{name=%s, dispatcher=%s, cluster.enabled=%s}",
                systemName, dispatcherConfig.getType(), clusterConfig.isEnabled());
    }
}