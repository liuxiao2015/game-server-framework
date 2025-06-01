#!/bin/bash

# =============================================================================
# 游戏服务器框架本地运行脚本
# =============================================================================
# 功能: 启动完整的本地开发环境，包括依赖服务和应用
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
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.dev.yml"

log_info "项目根目录: $PROJECT_ROOT"

# 检查必要工具
check_tools() {
    log_info "检查必要工具..."
    
    local missing_tools=()
    
    if ! command -v docker &> /dev/null; then
        missing_tools+=("docker")
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        missing_tools+=("docker-compose")
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "缺少必要工具: ${missing_tools[*]}"
        log_info "请先运行 ./scripts/dev/setup-dev-env.sh 安装开发环境"
        exit 1
    fi
    
    log_success "工具检查通过"
}

# 检查Docker状态
check_docker() {
    log_info "检查Docker状态..."
    
    if ! docker info &> /dev/null; then
        log_error "Docker未运行，请启动Docker Desktop"
        exit 1
    fi
    
    log_success "Docker运行正常"
}

# 拉取镜像
pull_images() {
    log_info "拉取必要的Docker镜像..."
    
    cd "$PROJECT_ROOT"
    
    # 拉取基础镜像
    docker-compose -f "$COMPOSE_FILE" pull --ignore-pull-failures || {
        log_warning "部分镜像拉取失败，将在启动时自动构建"
    }
    
    log_success "镜像拉取完成"
}

# 启动基础服务
start_infrastructure() {
    log_info "启动基础服务..."
    
    cd "$PROJECT_ROOT"
    
    # 启动数据库和缓存
    docker-compose -f "$COMPOSE_FILE" up -d mysql-dev redis-dev
    
    # 等待服务就绪
    log_info "等待MySQL启动..."
    timeout 120 bash -c 'until docker-compose -f '"$COMPOSE_FILE"' exec -T mysql-dev mysqladmin ping -h localhost -u root -proot123 2>/dev/null; do sleep 2; done' || {
        log_error "MySQL启动超时"
        show_logs "mysql-dev"
        exit 1
    }
    
    log_info "等待Redis启动..."
    timeout 60 bash -c 'until docker-compose -f '"$COMPOSE_FILE"' exec -T redis-dev redis-cli ping 2>/dev/null | grep -q PONG; do sleep 2; done' || {
        log_error "Redis启动超时"
        show_logs "redis-dev"
        exit 1
    }
    
    log_success "基础服务启动完成"
}

# 启动开发工具
start_dev_tools() {
    log_info "启动开发工具..."
    
    cd "$PROJECT_ROOT"
    
    # 启动管理工具
    docker-compose -f "$COMPOSE_FILE" up -d \
        phpmyadmin \
        redis-commander \
        adminer \
        mailhog \
        docs
    
    log_success "开发工具启动完成"
}

# 启动监控服务
start_monitoring() {
    log_info "启动监控服务..."
    
    cd "$PROJECT_ROOT"
    
    # 启动监控组件
    docker-compose -f "$COMPOSE_FILE" up -d \
        prometheus-dev \
        grafana-dev
    
    log_success "监控服务启动完成"
}

# 初始化数据库
init_database() {
    log_info "初始化数据库..."
    
    cd "$PROJECT_ROOT"
    
    # 检查数据库是否已初始化
    if docker-compose -f "$COMPOSE_FILE" exec -T mysql-dev mysql -u gameserver -pgameserver123 gameserver_dev -e "SHOW TABLES;" 2>/dev/null | grep -q "users"; then
        log_info "数据库已初始化，跳过"
        return 0
    fi
    
    # 执行数据库初始化脚本
    local config_dir="$HOME/.gameserver/config"
    if [[ -f "$config_dir/db/schema.sql" ]]; then
        log_info "执行数据库结构初始化..."
        docker-compose -f "$COMPOSE_FILE" exec -T mysql-dev mysql -u gameserver -pgameserver123 gameserver_dev < "$config_dir/db/schema.sql"
        
        if [[ -f "$config_dir/db/data.sql" ]]; then
            log_info "执行测试数据初始化..."
            docker-compose -f "$COMPOSE_FILE" exec -T mysql-dev mysql -u gameserver -pgameserver123 gameserver_dev < "$config_dir/db/data.sql"
        fi
        
        log_success "数据库初始化完成"
    else
        log_warning "未找到数据库初始化脚本，请先运行 ./scripts/dev/init-project.sh"
    fi
}

# 启动应用
start_application() {
    local mode=${1:-"container"}
    
    if [[ "$mode" == "container" ]]; then
        log_info "在容器中启动应用..."
        
        cd "$PROJECT_ROOT"
        
        # 构建并启动应用容器
        docker-compose -f "$COMPOSE_FILE" up -d --build gameserver-dev
        
        log_info "等待应用启动..."
        timeout 180 bash -c 'until curl -f http://localhost:18090/actuator/health 2>/dev/null; do sleep 5; done' || {
            log_error "应用启动超时"
            show_logs "gameserver-dev"
            exit 1
        }
        
        log_success "应用在容器中启动完成"
    else
        log_info "准备本地IDE启动环境..."
        
        # 只启动依赖服务，应用在IDE中启动
        log_info "依赖服务已启动，可在IDE中启动应用"
        log_info "IDE启动参数:"
        echo "  -Dspring.profiles.active=local,dev"
        echo "  -Dspring.config.additional-location=file:$HOME/.gameserver/config/"
        echo "  -Dserver.port=8080"
        echo "  -Dmanagement.server.port=8090"
    fi
}

# 显示服务状态
show_status() {
    log_info "服务状态:"
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" ps
    
    echo
    log_info "服务地址:"
    echo "  应用服务:      http://localhost:18000"
    echo "  管理控制台:    http://localhost:18090"
    echo "  健康检查:      http://localhost:18090/actuator/health"
    echo "  Swagger文档:   http://localhost:18000/swagger-ui.html"
    echo
    echo "  数据库管理:    http://localhost:18080 (phpMyAdmin)"
    echo "  Redis管理:     http://localhost:18081 (Redis Commander)"
    echo "  数据库工具:    http://localhost:18082 (Adminer)"
    echo "  邮件测试:      http://localhost:18025 (MailHog)"
    echo "  项目文档:      http://localhost:18083"
    echo
    echo "  监控面板:      http://localhost:13000 (Grafana admin/admin)"
    echo "  指标监控:      http://localhost:19000 (Prometheus)"
    echo
    echo "  数据库连接:    localhost:13306 (gameserver/gameserver123)"
    echo "  Redis连接:     localhost:16379"
}

# 显示日志
show_logs() {
    local service=${1:-"gameserver-dev"}
    local lines=${2:-50}
    
    log_info "显示 $service 服务日志 (最近 $lines 行):"
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" "$service"
}

# 停止服务
stop_services() {
    log_info "停止所有服务..."
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" down
    
    log_success "所有服务已停止"
}

# 清理环境
cleanup() {
    log_info "清理开发环境..."
    
    cd "$PROJECT_ROOT"
    
    # 停止并删除容器
    docker-compose -f "$COMPOSE_FILE" down -v
    
    # 清理未使用的镜像
    docker image prune -f
    
    log_success "环境清理完成"
}

# 重启服务
restart_service() {
    local service=${1:-"gameserver-dev"}
    
    log_info "重启 $service 服务..."
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" restart "$service"
    
    log_success "$service 服务重启完成"
}

# 查看帮助
show_help() {
    cat << EOF
游戏服务器框架本地运行脚本

用法: $0 [命令] [选项]

命令:
    start [模式]        启动开发环境
                        模式: container (默认) | ide
    stop               停止所有服务
    restart [服务]      重启指定服务
    status             显示服务状态
    logs [服务] [行数]   显示服务日志
    cleanup            清理环境
    help               显示帮助信息

选项:
    --no-pull          跳过镜像拉取
    --no-monitoring    跳过监控服务启动
    --no-tools         跳过开发工具启动

示例:
    $0                          # 启动完整开发环境
    $0 start container          # 在容器中启动应用
    $0 start ide                # 为IDE开发准备环境
    $0 logs gameserver-dev      # 查看应用日志
    $0 restart mysql-dev        # 重启数据库
    $0 stop                     # 停止所有服务

EOF
}

# 主函数
main() {
    local command="${1:-start}"
    local option="${2:-container}"
    local SKIP_PULL=false
    local SKIP_MONITORING=false
    local SKIP_TOOLS=false
    
    # 解析选项
    for arg in "$@"; do
        case $arg in
            --no-pull)
                SKIP_PULL=true
                ;;
            --no-monitoring)
                SKIP_MONITORING=true
                ;;
            --no-tools)
                SKIP_TOOLS=true
                ;;
        esac
    done
    
    case $command in
        start)
            log_info "启动游戏服务器框架开发环境..."
            
            check_tools
            check_docker
            
            if [[ "$SKIP_PULL" != true ]]; then
                pull_images
            fi
            
            start_infrastructure
            init_database
            
            if [[ "$SKIP_TOOLS" != true ]]; then
                start_dev_tools
            fi
            
            if [[ "$SKIP_MONITORING" != true ]]; then
                start_monitoring
            fi
            
            start_application "$option"
            
            show_status
            
            log_success "开发环境启动完成！"
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_service "$option"
            ;;
        status)
            show_status
            ;;
        logs)
            show_logs "$option" "${3:-50}"
            ;;
        cleanup)
            cleanup
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $command"
            show_help
            exit 1
            ;;
    esac
}

# 信号处理
trap 'log_warning "接收到中断信号，正在停止服务..."; stop_services; exit 0' INT TERM

# 脚本入口
main "$@"