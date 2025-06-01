#!/bin/bash

# =============================================================================
# 游戏服务器框架 Docker 容器启动脚本
# =============================================================================
# 功能: 容器启动时的初始化和应用启动
# =============================================================================

set -e

# 日志函数
log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] INFO: $1"
}

log_warning() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARNING: $1"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $1"
}

# 等待数据库连接
wait_for_database() {
    if [[ -n "$DB_HOST" ]]; then
        log_info "等待数据库连接: $DB_HOST:${DB_PORT:-3306}"
        
        local max_attempts=30
        local attempt=1
        
        while [[ $attempt -le $max_attempts ]]; do
            if nc -z "$DB_HOST" "${DB_PORT:-3306}" 2>/dev/null; then
                log_info "数据库连接成功"
                return 0
            fi
            
            log_info "等待数据库连接... (尝试 $attempt/$max_attempts)"
            sleep 2
            ((attempt++))
        done
        
        log_error "数据库连接超时"
        exit 1
    fi
}

# 等待Redis连接
wait_for_redis() {
    if [[ -n "$REDIS_HOST" ]]; then
        log_info "等待Redis连接: $REDIS_HOST:${REDIS_PORT:-6379}"
        
        local max_attempts=30
        local attempt=1
        
        while [[ $attempt -le $max_attempts ]]; do
            if nc -z "$REDIS_HOST" "${REDIS_PORT:-6379}" 2>/dev/null; then
                log_info "Redis连接成功"
                return 0
            fi
            
            log_info "等待Redis连接... (尝试 $attempt/$max_attempts)"
            sleep 2
            ((attempt++))
        done
        
        log_warning "Redis连接超时，继续启动..."
    fi
}

# 初始化配置
init_config() {
    log_info "初始化配置..."
    
    # 动态生成配置文件
    cat > /app/config/application-docker.yml << EOF
spring:
  profiles:
    active: docker
  
  # 数据源配置
  datasource:
    url: jdbc:mysql://\${DB_HOST:mysql}:\${DB_PORT:3306}/\${DB_NAME:gameserver}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: \${DB_USERNAME:gameserver}
    password: \${DB_PASSWORD:gameserver123}
    driver-class-name: com.mysql.cj.jdbc.Driver
    
  # JPA配置
  jpa:
    hibernate:
      ddl-auto: \${DDL_AUTO:update}
    show-sql: \${SHOW_SQL:false}
    
  # Redis配置
  redis:
    host: \${REDIS_HOST:redis}
    port: \${REDIS_PORT:6379}
    password: \${REDIS_PASSWORD:}
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0

# 游戏服务器配置
game:
  server:
    name: \${SERVER_NAME:docker-gameserver}
    port: \${SERVER_PORT:8080}
    
  # 网络配置
  network:
    tcp:
      port: \${TCP_PORT:9090}
      boss-threads: \${TCP_BOSS_THREADS:2}
      worker-threads: \${TCP_WORKER_THREADS:8}
    websocket:
      port: \${WS_PORT:9091}
      path: /ws
      
# 管理端点
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true

# 日志配置
logging:
  level:
    com.lx.gameserver: \${LOG_LEVEL:INFO}
    org.springframework: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"
  file:
    name: /app/logs/application.log
    max-size: 100MB
    max-history: 7
EOF

    log_info "配置文件生成完成"
}

# 预热JVM
warm_up_jvm() {
    log_info "JVM预热..."
    
    # 设置JVM参数
    export JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/./urandom"
    
    # 显示JVM信息
    log_info "JVM版本: $(java -version 2>&1 | head -1)"
    log_info "JVM内存设置: $JAVA_OPTS"
}

# 健康检查
health_check() {
    local max_attempts=60
    local attempt=1
    
    log_info "等待应用启动..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if curl -f http://localhost:8090/actuator/health >/dev/null 2>&1; then
            log_info "应用启动成功"
            return 0
        fi
        
        if [[ $((attempt % 10)) -eq 0 ]]; then
            log_info "等待应用启动... (尝试 $attempt/$max_attempts)"
        fi
        
        sleep 1
        ((attempt++))
    done
    
    log_warning "应用启动检查超时"
    return 1
}

# 优雅关闭处理
graceful_shutdown() {
    log_info "接收到关闭信号，开始优雅关闭..."
    
    if [[ -n "$APP_PID" ]]; then
        # 发送TERM信号
        kill -TERM "$APP_PID" 2>/dev/null || true
        
        # 等待进程结束
        local wait_time=0
        while kill -0 "$APP_PID" 2>/dev/null && [[ $wait_time -lt 30 ]]; do
            sleep 1
            ((wait_time++))
        done
        
        # 如果进程仍然存在，强制杀死
        if kill -0 "$APP_PID" 2>/dev/null; then
            log_warning "强制关闭应用"
            kill -KILL "$APP_PID" 2>/dev/null || true
        fi
    fi
    
    log_info "应用已关闭"
    exit 0
}

# 主函数
main() {
    log_info "启动游戏服务器框架容器..."
    
    # 设置信号处理
    trap graceful_shutdown TERM INT
    
    # 等待依赖服务
    wait_for_database
    wait_for_redis
    
    # 初始化配置
    init_config
    
    # JVM预热
    warm_up_jvm
    
    # 启动应用
    log_info "启动Java应用..."
    java $JAVA_OPTS -jar /app/app.jar &
    APP_PID=$!
    
    # 后台健康检查
    health_check &
    
    # 等待应用进程
    wait $APP_PID
    
    log_info "应用进程已退出"
}

# 脚本入口
main "$@"