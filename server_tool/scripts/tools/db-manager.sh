#!/bin/bash

# =============================================================================
# 游戏服务器框架数据库工具脚本
# =============================================================================
# 功能: 数据库管理工具，包括迁移、备份、恢复等操作
# 支持: MySQL, PostgreSQL
# 作者: liuxiao2015
# 版本: 1.0.0
# =============================================================================

set -euo pipefail

# 颜色定义
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# 配置变量
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 数据库配置
DB_TYPE="${DB_TYPE:-mysql}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-gameserver}"
DB_USER="${DB_USER:-gameserver}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"

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

# 显示帮助信息
show_help() {
    cat << EOF
游戏服务器框架数据库工具

用法: $0 <command> [选项]

命令:
  migrate             执行数据库迁移
  backup              备份数据库
  restore <file>      恢复数据库
  init                初始化数据库
  status              检查数据库状态
  clean               清理数据库
  seed                导入种子数据

选项:
  --type <type>       数据库类型 (mysql|postgresql) [默认: mysql]
  --host <host>       数据库主机 [默认: localhost]
  --port <port>       数据库端口 [默认: 3306]
  --database <name>   数据库名 [默认: gameserver]
  --user <user>       数据库用户 [默认: gameserver]
  --password <pass>   数据库密码
  --backup-dir <dir>  备份目录 [默认: ./backups]
  --help              显示此帮助信息

环境变量:
  DB_TYPE             数据库类型
  DB_HOST             数据库主机
  DB_PORT             数据库端口
  DB_NAME             数据库名
  DB_USER             数据库用户
  DB_PASSWORD         数据库密码

示例:
  $0 init                                 # 初始化数据库
  $0 migrate                              # 执行迁移
  $0 backup                               # 备份数据库
  $0 restore backup-20231201.sql         # 恢复数据库
  $0 status --host prod-db.example.com   # 检查生产数据库状态

EOF
}

# 解析命令行参数
parse_args() {
    if [[ $# -lt 1 ]]; then
        show_help
        exit 1
    fi
    
    COMMAND="$1"
    shift
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --type)
                DB_TYPE="$2"
                shift 2
                ;;
            --host)
                DB_HOST="$2"
                shift 2
                ;;
            --port)
                DB_PORT="$2"
                shift 2
                ;;
            --database)
                DB_NAME="$2"
                shift 2
                ;;
            --user)
                DB_USER="$2"
                shift 2
                ;;
            --password)
                DB_PASSWORD="$2"
                shift 2
                ;;
            --backup-dir)
                BACKUP_DIR="$2"
                shift 2
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                if [[ "$COMMAND" == "restore" && -z "${RESTORE_FILE:-}" ]]; then
                    RESTORE_FILE="$1"
                    shift
                else
                    log_error "未知参数: $1"
                    show_help
                    exit 1
                fi
                ;;
        esac
    done
}

# 检查数据库工具
check_db_tools() {
    case "$DB_TYPE" in
        mysql)
            if ! command -v mysql &> /dev/null; then
                log_error "MySQL客户端未安装"
                exit 1
            fi
            if ! command -v mysqldump &> /dev/null; then
                log_error "mysqldump未安装"
                exit 1
            fi
            ;;
        postgresql)
            if ! command -v psql &> /dev/null; then
                log_error "PostgreSQL客户端未安装"
                exit 1
            fi
            if ! command -v pg_dump &> /dev/null; then
                log_error "pg_dump未安装"
                exit 1
            fi
            ;;
        *)
            log_error "不支持的数据库类型: $DB_TYPE"
            exit 1
            ;;
    esac
}

# 构建数据库连接字符串
build_connection() {
    case "$DB_TYPE" in
        mysql)
            MYSQL_OPTS="-h$DB_HOST -P$DB_PORT -u$DB_USER"
            if [[ -n "$DB_PASSWORD" ]]; then
                MYSQL_OPTS="$MYSQL_OPTS -p$DB_PASSWORD"
            fi
            ;;
        postgresql)
            export PGHOST="$DB_HOST"
            export PGPORT="$DB_PORT"
            export PGUSER="$DB_USER"
            export PGDATABASE="$DB_NAME"
            if [[ -n "$DB_PASSWORD" ]]; then
                export PGPASSWORD="$DB_PASSWORD"
            fi
            ;;
    esac
}

# 检查数据库连接
check_connection() {
    log_info "检查数据库连接..."
    
    case "$DB_TYPE" in
        mysql)
            if mysql $MYSQL_OPTS -e "SELECT 1;" >/dev/null 2>&1; then
                log_success "MySQL连接成功"
            else
                log_error "MySQL连接失败"
                exit 1
            fi
            ;;
        postgresql)
            if psql -c "SELECT 1;" >/dev/null 2>&1; then
                log_success "PostgreSQL连接成功"
            else
                log_error "PostgreSQL连接失败"
                exit 1
            fi
            ;;
    esac
}

# 检查数据库状态
check_status() {
    log_info "检查数据库状态..."
    
    case "$DB_TYPE" in
        mysql)
            echo "MySQL服务器信息:"
            mysql $MYSQL_OPTS -e "
                SELECT 
                    VERSION() as 'MySQL版本',
                    @@hostname as '主机名',
                    @@port as '端口',
                    @@datadir as '数据目录';
            "
            
            echo -e "\n数据库列表:"
            mysql $MYSQL_OPTS -e "SHOW DATABASES;"
            
            if mysql $MYSQL_OPTS -e "USE $DB_NAME;" 2>/dev/null; then
                echo -e "\n数据库 '$DB_NAME' 表列表:"
                mysql $MYSQL_OPTS $DB_NAME -e "SHOW TABLES;"
            else
                log_warning "数据库 '$DB_NAME' 不存在"
            fi
            ;;
        postgresql)
            echo "PostgreSQL服务器信息:"
            psql -c "
                SELECT 
                    version() as \"PostgreSQL版本\",
                    current_database() as \"当前数据库\",
                    current_user as \"当前用户\";
            "
            
            echo -e "\n数据库列表:"
            psql -l
            
            if psql -c "\\dt" 2>/dev/null; then
                echo -e "\n数据库 '$DB_NAME' 表列表:"
                psql -c "\\dt"
            else
                log_warning "数据库 '$DB_NAME' 不存在或无表"
            fi
            ;;
    esac
}

# 初始化数据库
init_database() {
    log_info "初始化数据库..."
    
    case "$DB_TYPE" in
        mysql)
            # 创建数据库（如果不存在）
            mysql $MYSQL_OPTS -e "
                CREATE DATABASE IF NOT EXISTS $DB_NAME 
                CHARACTER SET utf8mb4 
                COLLATE utf8mb4_unicode_ci;
            "
            
            # 执行初始化脚本
            if [[ -f "$PROJECT_ROOT/sql/mysql/init.sql" ]]; then
                log_info "执行MySQL初始化脚本..."
                mysql $MYSQL_OPTS $DB_NAME < "$PROJECT_ROOT/sql/mysql/init.sql"
            fi
            ;;
        postgresql)
            # 创建数据库（如果不存在）
            psql -c "
                SELECT 1 FROM pg_database WHERE datname='$DB_NAME'
            " | grep -q 1 || psql -c "CREATE DATABASE $DB_NAME;"
            
            # 执行初始化脚本
            if [[ -f "$PROJECT_ROOT/sql/postgresql/init.sql" ]]; then
                log_info "执行PostgreSQL初始化脚本..."
                psql < "$PROJECT_ROOT/sql/postgresql/init.sql"
            fi
            ;;
    esac
    
    log_success "数据库初始化完成"
}

# 执行数据库迁移
migrate_database() {
    log_info "执行数据库迁移..."
    
    # 使用Flyway或Liquibase执行迁移
    if command -v flyway &> /dev/null; then
        flyway -url="jdbc:$DB_TYPE://$DB_HOST:$DB_PORT/$DB_NAME" \
               -user="$DB_USER" \
               -password="$DB_PASSWORD" \
               migrate
    elif [[ -f "$PROJECT_ROOT/pom.xml" ]]; then
        # 使用Maven插件执行迁移
        cd "$PROJECT_ROOT"
        mvn flyway:migrate \
            -Dflyway.url="jdbc:$DB_TYPE://$DB_HOST:$DB_PORT/$DB_NAME" \
            -Dflyway.user="$DB_USER" \
            -Dflyway.password="$DB_PASSWORD"
    else
        log_warning "未找到数据库迁移工具，跳过迁移"
    fi
    
    log_success "数据库迁移完成"
}

# 备份数据库
backup_database() {
    log_info "备份数据库..."
    
    # 创建备份目录
    mkdir -p "$BACKUP_DIR"
    
    local backup_file="$BACKUP_DIR/backup-$(date +%Y%m%d-%H%M%S).sql"
    
    case "$DB_TYPE" in
        mysql)
            mysqldump $MYSQL_OPTS \
                --single-transaction \
                --routines \
                --triggers \
                --lock-tables=false \
                $DB_NAME > "$backup_file"
            ;;
        postgresql)
            pg_dump \
                --no-password \
                --verbose \
                --clean \
                --no-acl \
                --no-owner \
                "$DB_NAME" > "$backup_file"
            ;;
    esac
    
    # 压缩备份文件
    gzip "$backup_file"
    backup_file="${backup_file}.gz"
    
    log_success "数据库备份完成: $backup_file"
    echo "备份文件: $backup_file"
    echo "备份大小: $(du -h "$backup_file" | cut -f1)"
}

# 恢复数据库
restore_database() {
    if [[ -z "${RESTORE_FILE:-}" ]]; then
        log_error "请指定要恢复的备份文件"
        exit 1
    fi
    
    if [[ ! -f "$RESTORE_FILE" ]]; then
        log_error "备份文件不存在: $RESTORE_FILE"
        exit 1
    fi
    
    log_info "恢复数据库从: $RESTORE_FILE"
    
    # 确认操作
    read -p "这将覆盖现有数据，确认继续? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "操作已取消"
        exit 0
    fi
    
    # 解压文件（如果需要）
    local restore_file="$RESTORE_FILE"
    if [[ "$RESTORE_FILE" == *.gz ]]; then
        restore_file="/tmp/$(basename "$RESTORE_FILE" .gz)"
        gunzip -c "$RESTORE_FILE" > "$restore_file"
    fi
    
    case "$DB_TYPE" in
        mysql)
            mysql $MYSQL_OPTS $DB_NAME < "$restore_file"
            ;;
        postgresql)
            psql < "$restore_file"
            ;;
    esac
    
    # 清理临时文件
    if [[ "$restore_file" != "$RESTORE_FILE" ]]; then
        rm -f "$restore_file"
    fi
    
    log_success "数据库恢复完成"
}

# 清理数据库
clean_database() {
    log_info "清理数据库..."
    
    # 确认操作
    read -p "这将删除所有数据，确认继续? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "操作已取消"
        exit 0
    fi
    
    case "$DB_TYPE" in
        mysql)
            mysql $MYSQL_OPTS $DB_NAME -e "
                SET FOREIGN_KEY_CHECKS = 0;
                SET @tables = NULL;
                SELECT GROUP_CONCAT(table_name) INTO @tables
                FROM information_schema.tables
                WHERE table_schema = '$DB_NAME';
                SET @tables = CONCAT('DROP TABLE IF EXISTS ', @tables);
                PREPARE stmt FROM @tables;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                SET FOREIGN_KEY_CHECKS = 1;
            "
            ;;
        postgresql)
            psql -c "
                DROP SCHEMA public CASCADE;
                CREATE SCHEMA public;
                GRANT ALL ON SCHEMA public TO $DB_USER;
                GRANT ALL ON SCHEMA public TO public;
            "
            ;;
    esac
    
    log_success "数据库清理完成"
}

# 导入种子数据
seed_database() {
    log_info "导入种子数据..."
    
    local seed_file="$PROJECT_ROOT/sql/$DB_TYPE/seed.sql"
    if [[ ! -f "$seed_file" ]]; then
        log_warning "种子数据文件不存在: $seed_file"
        return 0
    fi
    
    case "$DB_TYPE" in
        mysql)
            mysql $MYSQL_OPTS $DB_NAME < "$seed_file"
            ;;
        postgresql)
            psql < "$seed_file"
            ;;
    esac
    
    log_success "种子数据导入完成"
}

# 主函数
main() {
    log_info "游戏服务器框架数据库工具 v1.0.0"
    
    # 解析参数
    parse_args "$@"
    
    # 检查工具
    check_db_tools
    
    # 构建连接
    build_connection
    
    # 执行命令
    case "$COMMAND" in
        status)
            check_connection
            check_status
            ;;
        init)
            check_connection
            init_database
            ;;
        migrate)
            check_connection
            migrate_database
            ;;
        backup)
            check_connection
            backup_database
            ;;
        restore)
            check_connection
            restore_database
            ;;
        clean)
            check_connection
            clean_database
            ;;
        seed)
            check_connection
            seed_database
            ;;
        *)
            log_error "未知命令: $COMMAND"
            show_help
            exit 1
            ;;
    esac
    
    log_success "操作完成！"
}

# 脚本入口
main "$@"