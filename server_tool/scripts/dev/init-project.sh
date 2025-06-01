#!/bin/bash

# =============================================================================
# 游戏服务器框架项目初始化脚本
# =============================================================================
# 功能: 初始化项目开发环境，下载依赖，配置数据库，生成配置文件
# 支持: Linux, macOS, Windows (通过WSL)
# 作者: liuxiao2015
# 版本: 1.0.0
# =============================================================================

set -e  # 遇到错误时退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_DIR="$PROJECT_ROOT/config-templates"
LOCAL_CONFIG_DIR="$HOME/.gameserver/config"

log_info "项目根目录: $PROJECT_ROOT"

# 检查必要的工具
check_tools() {
    log_info "检查必要工具..."
    
    local missing_tools=()
    
    if ! command -v java &> /dev/null; then
        missing_tools+=("java")
    fi
    
    if ! command -v mvn &> /dev/null; then
        missing_tools+=("maven")
    fi
    
    if ! command -v git &> /dev/null; then
        missing_tools+=("git")
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "缺少必要工具: ${missing_tools[*]}"
        log_info "请先运行 ./scripts/dev/setup-dev-env.sh 安装开发环境"
        exit 1
    fi
    
    log_success "工具检查通过"
}

# 清理之前的构建
clean_build() {
    log_info "清理之前的构建产物..."
    
    cd "$PROJECT_ROOT"
    mvn clean -q || {
        log_warning "Maven clean 失败，继续..."
    }
    
    # 清理IDE生成的文件
    find . -name "*.iml" -delete 2>/dev/null || true
    find . -name ".idea" -type d -exec rm -rf {} + 2>/dev/null || true
    find . -name ".vscode" -type d -exec rm -rf {} + 2>/dev/null || true
    
    log_success "构建产物清理完成"
}

# 下载依赖
download_dependencies() {
    log_info "下载项目依赖..."
    
    cd "$PROJECT_ROOT"
    
    # 并行下载依赖，提高速度
    mvn dependency:go-offline -T 4 -q || {
        log_warning "部分依赖下载失败，尝试在线模式..."
        mvn compile -T 4 -q || {
            log_error "依赖下载失败"
            exit 1
        }
    }
    
    log_success "依赖下载完成"
}

# 生成配置文件
generate_config() {
    log_info "生成配置文件..."
    
    # 创建配置目录
    mkdir -p "$LOCAL_CONFIG_DIR"
    
    # 生成application-local.yml
    cat > "$LOCAL_CONFIG_DIR/application-local.yml" << EOF
# 本地开发环境配置
spring:
  profiles:
    active: local
  
  # 数据源配置
  datasource:
    url: jdbc:h2:mem:gameserver;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: 
    
  # H2数据库控制台
  h2:
    console:
      enabled: true
      path: /h2-console
      
  # JPA配置
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
  # Redis配置（可选）
  redis:
    host: localhost
    port: 6379
    password: 
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        
# 游戏服务器配置
game:
  server:
    name: local-dev-server
    port: 8080
    
  # 网络配置
  network:
    tcp:
      port: 9090
      boss-threads: 1
      worker-threads: 4
    websocket:
      port: 9091
      path: /ws
      
  # 缓存配置
  cache:
    type: caffeine
    caffeine:
      maximum-size: 10000
      expire-after-write: 30m
      
  # 日志配置
  logging:
    level: debug
    file: \${user.home}/.gameserver/logs/gameserver.log
    
# 管理端点
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      
# 日志配置
logging:
  level:
    com.lx.gameserver: DEBUG
    org.springframework: INFO
    org.hibernate: INFO
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"
  file:
    name: \${user.home}/.gameserver/logs/application.log
EOF

    log_success "配置文件生成完成: $LOCAL_CONFIG_DIR/application-local.yml"
}

# 生成启动脚本
generate_start_scripts() {
    log_info "生成启动脚本..."
    
    # 生成开发启动脚本
    cat > "$PROJECT_ROOT/start-dev.sh" << 'EOF'
#!/bin/bash

# 开发环境快速启动脚本
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "启动游戏服务器框架开发环境..."

# 设置JVM参数
export JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication"

# 设置Spring配置文件位置
export SPRING_CONFIG_LOCATION="classpath:/,file:$HOME/.gameserver/config/"

# 启动应用
mvn spring-boot:run -pl admin-console \
    -Dspring.profiles.active=local \
    -Dspring.config.additional-location=$HOME/.gameserver/config/ \
    "$@"
EOF

    chmod +x "$PROJECT_ROOT/start-dev.sh"
    
    # 生成Windows启动脚本
    cat > "$PROJECT_ROOT/start-dev.bat" << 'EOF'
@echo off
setlocal

set PROJECT_ROOT=%~dp0
cd /d "%PROJECT_ROOT%"

echo 启动游戏服务器框架开发环境...

:: 设置JVM参数
set JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication

:: 设置Spring配置文件位置
set SPRING_CONFIG_LOCATION=classpath:/,file:%USERPROFILE%\.gameserver\config\

:: 启动应用
mvn spring-boot:run -pl admin-console ^
    -Dspring.profiles.active=local ^
    -Dspring.config.additional-location=%USERPROFILE%\.gameserver\config\ ^
    %*
EOF

    log_success "启动脚本生成完成"
}

# 初始化数据库
init_database() {
    log_info "初始化数据库结构..."
    
    # 创建数据库初始化脚本目录
    mkdir -p "$LOCAL_CONFIG_DIR/db"
    
    # 生成基础数据库结构
    cat > "$LOCAL_CONFIG_DIR/db/schema.sql" << 'EOF'
-- 游戏服务器框架基础数据库结构

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_time TIMESTAMP NULL,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status)
);

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    status TINYINT DEFAULT 1,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- 游戏配置表
CREATE TABLE IF NOT EXISTS game_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'STRING',
    description VARCHAR(200),
    environment VARCHAR(20) DEFAULT 'ALL',
    version INT DEFAULT 1,
    status TINYINT DEFAULT 1,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_key (config_key),
    INDEX idx_env (environment),
    INDEX idx_status (status)
);

-- 系统日志表
CREATE TABLE IF NOT EXISTS system_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    result TINYINT DEFAULT 1 COMMENT '结果：0-失败，1-成功',
    message TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_created_time (created_time),
    INDEX idx_result (result)
);
EOF

    # 生成测试数据
    cat > "$LOCAL_CONFIG_DIR/db/data.sql" << 'EOF'
-- 插入默认角色
INSERT IGNORE INTO roles (role_name, description) VALUES 
('ADMIN', '系统管理员'),
('USER', '普通用户'),
('GAME_MASTER', '游戏管理员');

-- 插入默认用户 (密码: admin123)
INSERT IGNORE INTO users (username, password, email, status) VALUES 
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8ioctMcZHO7LS8PF8MU6dMUSTTItze', 'admin@gameserver.com', 1);

-- 给admin用户分配管理员角色
INSERT IGNORE INTO user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM users u, roles r 
WHERE u.username = 'admin' AND r.role_name = 'ADMIN';

-- 插入默认配置
INSERT IGNORE INTO game_configs (config_key, config_value, config_type, description, environment) VALUES 
('game.server.max_players', '1000', 'INTEGER', '服务器最大玩家数', 'ALL'),
('game.server.maintenance', 'false', 'BOOLEAN', '维护模式', 'ALL'),
('game.feature.chat_enabled', 'true', 'BOOLEAN', '聊天功能开关', 'ALL'),
('game.feature.pvp_enabled', 'true', 'BOOLEAN', 'PVP功能开关', 'ALL'),
('game.economy.daily_bonus', '100', 'INTEGER', '每日奖励金币', 'ALL');
EOF

    log_success "数据库初始化脚本生成完成"
}

# 生成开发工具脚本
generate_dev_tools() {
    log_info "生成开发工具脚本..."
    
    mkdir -p "$PROJECT_ROOT/tools/dev"
    
    # 生成代码格式化脚本
    cat > "$PROJECT_ROOT/tools/dev/format-code.sh" << 'EOF'
#!/bin/bash
# 代码格式化工具

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "格式化Java代码..."

# 使用Maven的formatter插件格式化代码
mvn formatter:format -q

# 使用Maven的import organize插件整理导入
mvn impsort:sort -q

echo "代码格式化完成"
EOF

    chmod +x "$PROJECT_ROOT/tools/dev/format-code.sh"
    
    # 生成依赖检查脚本
    cat > "$PROJECT_ROOT/tools/dev/check-dependencies.sh" << 'EOF'
#!/bin/bash
# 依赖检查工具

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "检查项目依赖..."

# 检查过期依赖
echo "=== 检查过期依赖 ==="
mvn versions:display-dependency-updates

# 检查插件更新
echo "=== 检查插件更新 ==="
mvn versions:display-plugin-updates

# 检查依赖冲突
echo "=== 检查依赖冲突 ==="
mvn dependency:analyze

echo "依赖检查完成"
EOF

    chmod +x "$PROJECT_ROOT/tools/dev/check-dependencies.sh"
    
    log_success "开发工具脚本生成完成"
}

# 验证初始化
verify_init() {
    log_info "验证项目初始化..."
    
    local errors=0
    
    # 检查配置文件
    if [[ ! -f "$LOCAL_CONFIG_DIR/application-local.yml" ]]; then
        log_error "本地配置文件未生成"
        ((errors++))
    fi
    
    # 检查启动脚本
    if [[ ! -f "$PROJECT_ROOT/start-dev.sh" ]]; then
        log_error "启动脚本未生成"
        ((errors++))
    fi
    
    # 尝试编译项目
    cd "$PROJECT_ROOT"
    if ! mvn compile -q -T 4; then
        log_error "项目编译失败"
        ((errors++))
    fi
    
    if [[ $errors -eq 0 ]]; then
        log_success "项目初始化验证通过"
        return 0
    else
        log_error "项目初始化验证失败，发现 $errors 个问题"
        return 1
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
游戏服务器框架项目初始化脚本

用法: $0 [选项]

选项:
    -h, --help          显示帮助信息
    -c, --clean         强制清理并重新初始化
    -s, --skip-deps     跳过依赖下载
    --no-verify         跳过初始化验证

示例:
    $0                  # 标准初始化
    $0 --clean          # 清理并重新初始化
    $0 --skip-deps      # 跳过依赖下载

EOF
}

# 主函数
main() {
    local CLEAN_INIT=false
    local SKIP_DEPS=false
    local NO_VERIFY=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -c|--clean)
                CLEAN_INIT=true
                shift
                ;;
            -s|--skip-deps)
                SKIP_DEPS=true
                shift
                ;;
            --no-verify)
                NO_VERIFY=true
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    log_info "开始项目初始化..."
    
    # 检查工具
    check_tools
    
    # 清理构建（如果需要）
    if [[ "$CLEAN_INIT" == true ]]; then
        clean_build
    fi
    
    # 下载依赖
    if [[ "$SKIP_DEPS" != true ]]; then
        download_dependencies
    fi
    
    # 生成配置文件
    generate_config
    
    # 生成启动脚本
    generate_start_scripts
    
    # 初始化数据库
    init_database
    
    # 生成开发工具
    generate_dev_tools
    
    # 验证初始化
    if [[ "$NO_VERIFY" != true ]]; then
        if verify_init; then
            log_success "项目初始化完成！"
            echo
            log_info "接下来你可以："
            log_info "1. 运行 ./start-dev.sh 启动开发服务器"
            log_info "2. 访问 http://localhost:8090 打开管理控制台"
            log_info "3. 运行 ./scripts/dev/run-local.sh 启动完整本地环境"
            echo
            log_info "配置文件位置: $LOCAL_CONFIG_DIR"
            log_info "数据库控制台: http://localhost:8090/h2-console"
        else
            log_error "项目初始化失败"
            exit 1
        fi
    else
        log_success "项目初始化完成（跳过验证）"
    fi
}

# 脚本入口
main "$@"