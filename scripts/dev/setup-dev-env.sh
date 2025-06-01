#!/bin/bash

# =============================================================================
# 游戏服务器框架开发环境一键安装脚本
# =============================================================================
# 功能: 自动检查和安装开发环境所需的所有工具和依赖
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

# 检测操作系统
detect_os() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="linux"
        if command -v apt-get &> /dev/null; then
            PACKAGE_MANAGER="apt"
        elif command -v yum &> /dev/null; then
            PACKAGE_MANAGER="yum"
        elif command -v dnf &> /dev/null; then
            PACKAGE_MANAGER="dnf"
        else
            log_error "不支持的Linux发行版"
            exit 1
        fi
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
        if ! command -v brew &> /dev/null; then
            log_warning "Homebrew未安装，正在安装..."
            /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        fi
        PACKAGE_MANAGER="brew"
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        OS="windows"
        log_info "检测到Windows环境，建议使用WSL2"
    else
        log_error "不支持的操作系统: $OSTYPE"
        exit 1
    fi
    
    log_info "检测到操作系统: $OS"
}

# 检查命令是否存在
command_exists() {
    command -v "$1" &> /dev/null
}

# 检查Java版本
check_java() {
    log_info "检查Java环境..."
    
    if command_exists java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [[ "$JAVA_VERSION" == "1" ]]; then
            JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f2)
        fi
        
        if [[ "$JAVA_VERSION" -ge 17 ]]; then
            log_success "Java $JAVA_VERSION 已安装"
            return 0
        else
            log_warning "Java版本过低: $JAVA_VERSION，需要Java 17+"
        fi
    else
        log_warning "Java未安装"
    fi
    
    return 1
}

# 安装Java
install_java() {
    log_info "安装Java 21..."
    
    case $OS in
        "linux")
            case $PACKAGE_MANAGER in
                "apt")
                    sudo apt update
                    sudo apt install -y openjdk-21-jdk
                    ;;
                "yum"|"dnf")
                    sudo $PACKAGE_MANAGER install -y java-21-openjdk-devel
                    ;;
            esac
            ;;
        "macos")
            brew install openjdk@21
            sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
            ;;
        "windows")
            log_warning "请手动下载并安装Java 21: https://adoptium.net/"
            return 1
            ;;
    esac
    
    # 设置JAVA_HOME
    if [[ "$OS" != "windows" ]]; then
        JAVA_HOME_PATH=$(dirname $(dirname $(readlink -f $(which java))))
        echo "export JAVA_HOME=$JAVA_HOME_PATH" >> ~/.bashrc
        echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
        export JAVA_HOME=$JAVA_HOME_PATH
        log_success "Java安装完成，JAVA_HOME设置为: $JAVA_HOME_PATH"
    fi
}

# 检查Maven
check_maven() {
    log_info "检查Maven环境..."
    
    if command_exists mvn; then
        MAVEN_VERSION=$(mvn -version | head -n 1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
        log_success "Maven $MAVEN_VERSION 已安装"
        return 0
    else
        log_warning "Maven未安装"
        return 1
    fi
}

# 安装Maven
install_maven() {
    log_info "安装Maven..."
    
    case $OS in
        "linux")
            case $PACKAGE_MANAGER in
                "apt")
                    sudo apt install -y maven
                    ;;
                "yum"|"dnf")
                    sudo $PACKAGE_MANAGER install -y maven
                    ;;
            esac
            ;;
        "macos")
            brew install maven
            ;;
        "windows")
            log_warning "请手动下载并安装Maven: https://maven.apache.org/download.cgi"
            return 1
            ;;
    esac
    
    log_success "Maven安装完成"
}

# 检查Git
check_git() {
    log_info "检查Git环境..."
    
    if command_exists git; then
        GIT_VERSION=$(git --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
        log_success "Git $GIT_VERSION 已安装"
        return 0
    else
        log_warning "Git未安装"
        return 1
    fi
}

# 安装Git
install_git() {
    log_info "安装Git..."
    
    case $OS in
        "linux")
            case $PACKAGE_MANAGER in
                "apt")
                    sudo apt install -y git
                    ;;
                "yum"|"dnf")
                    sudo $PACKAGE_MANAGER install -y git
                    ;;
            esac
            ;;
        "macos")
            # macOS通常自带Git，如果没有则通过Xcode Command Line Tools安装
            xcode-select --install 2>/dev/null || true
            ;;
        "windows")
            log_warning "请手动下载并安装Git: https://git-scm.com/download/win"
            return 1
            ;;
    esac
    
    log_success "Git安装完成"
}

# 安装Docker
install_docker() {
    log_info "安装Docker..."
    
    case $OS in
        "linux")
            # 使用官方安装脚本
            curl -fsSL https://get.docker.com -o get-docker.sh
            sudo sh get-docker.sh
            sudo usermod -aG docker $USER
            rm get-docker.sh
            
            # 安装Docker Compose
            sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
            sudo chmod +x /usr/local/bin/docker-compose
            ;;
        "macos")
            log_warning "请手动下载并安装Docker Desktop: https://docs.docker.com/desktop/mac/install/"
            ;;
        "windows")
            log_warning "请手动下载并安装Docker Desktop: https://docs.docker.com/desktop/windows/install/"
            ;;
    esac
}

# 检查Docker
check_docker() {
    log_info "检查Docker环境..."
    
    if command_exists docker; then
        if docker --version &> /dev/null; then
            DOCKER_VERSION=$(docker --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
            log_success "Docker $DOCKER_VERSION 已安装"
            
            if command_exists docker-compose; then
                COMPOSE_VERSION=$(docker-compose --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
                log_success "Docker Compose $COMPOSE_VERSION 已安装"
            else
                log_warning "Docker Compose未安装"
                return 1
            fi
            return 0
        fi
    fi
    
    log_warning "Docker未安装或未启动"
    return 1
}

# 配置Git
configure_git() {
    log_info "配置Git..."
    
    if ! git config --global user.name &> /dev/null; then
        echo -n "请输入Git用户名: "
        read -r GIT_USERNAME
        git config --global user.name "$GIT_USERNAME"
    fi
    
    if ! git config --global user.email &> /dev/null; then
        echo -n "请输入Git邮箱: "
        read -r GIT_EMAIL
        git config --global user.email "$GIT_EMAIL"
    fi
    
    # 设置常用Git配置
    git config --global init.defaultBranch main
    git config --global core.autocrlf input
    git config --global core.editor nano
    git config --global pull.rebase false
    
    log_success "Git配置完成"
}

# 创建开发目录结构
create_dev_structure() {
    log_info "创建开发目录结构..."
    
    # 创建必要的目录
    mkdir -p ~/.gameserver/logs
    mkdir -p ~/.gameserver/config
    mkdir -p ~/.gameserver/data
    mkdir -p ~/.gameserver/tools
    
    log_success "开发目录结构创建完成"
}

# 安装开发工具
install_dev_tools() {
    log_info "安装开发工具..."
    
    case $OS in
        "linux")
            case $PACKAGE_MANAGER in
                "apt")
                    sudo apt install -y curl wget unzip tree htop
                    ;;
                "yum"|"dnf")
                    sudo $PACKAGE_MANAGER install -y curl wget unzip tree htop
                    ;;
            esac
            ;;
        "macos")
            brew install curl wget unzip tree htop
            ;;
    esac
    
    log_success "开发工具安装完成"
}

# 验证安装
verify_installation() {
    log_info "验证安装..."
    
    local errors=0
    
    if ! check_java; then
        log_error "Java验证失败"
        ((errors++))
    fi
    
    if ! check_maven; then
        log_error "Maven验证失败"
        ((errors++))
    fi
    
    if ! check_git; then
        log_error "Git验证失败"
        ((errors++))
    fi
    
    if [[ $errors -eq 0 ]]; then
        log_success "所有组件验证通过！"
        return 0
    else
        log_error "验证发现 $errors 个问题"
        return 1
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
游戏服务器框架开发环境安装脚本

用法: $0 [选项]

选项:
    -h, --help          显示帮助信息
    -v, --verify        仅验证环境，不安装
    --skip-docker       跳过Docker安装
    --force             强制重新安装所有组件

示例:
    $0                  # 完整安装
    $0 --verify         # 仅验证环境
    $0 --skip-docker    # 跳过Docker安装

EOF
}

# 主函数
main() {
    local VERIFY_ONLY=false
    local SKIP_DOCKER=false
    local FORCE_INSTALL=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verify)
                VERIFY_ONLY=true
                shift
                ;;
            --skip-docker)
                SKIP_DOCKER=true
                shift
                ;;
            --force)
                FORCE_INSTALL=true
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    log_info "开始游戏服务器框架开发环境安装..."
    
    # 检测操作系统
    detect_os
    
    if [[ "$VERIFY_ONLY" == true ]]; then
        verify_installation
        exit $?
    fi
    
    # 安装基础工具
    if [[ "$OS" != "windows" ]]; then
        install_dev_tools
    fi
    
    # 检查并安装Java
    if ! check_java || [[ "$FORCE_INSTALL" == true ]]; then
        install_java
    fi
    
    # 检查并安装Maven
    if ! check_maven || [[ "$FORCE_INSTALL" == true ]]; then
        install_maven
    fi
    
    # 检查并安装Git
    if ! check_git || [[ "$FORCE_INSTALL" == true ]]; then
        install_git
    fi
    
    # 配置Git
    configure_git
    
    # 安装Docker（可选）
    if [[ "$SKIP_DOCKER" != true ]]; then
        if ! check_docker || [[ "$FORCE_INSTALL" == true ]]; then
            echo -n "是否安装Docker? [y/N]: "
            read -r INSTALL_DOCKER
            if [[ "$INSTALL_DOCKER" =~ ^[Yy]$ ]]; then
                install_docker
            fi
        fi
    fi
    
    # 创建开发目录结构
    create_dev_structure
    
    # 验证安装
    if verify_installation; then
        log_success "开发环境安装完成！"
        echo
        log_info "下一步:"
        log_info "1. 重新加载shell配置: source ~/.bashrc"
        log_info "2. 运行项目初始化: ./scripts/dev/init-project.sh"
        log_info "3. 启动本地开发环境: ./scripts/dev/run-local.sh"
        
        if [[ "$OS" == "linux" ]] && command -v docker &> /dev/null; then
            log_warning "注意: 如果安装了Docker，请重新登录以使用户组权限生效"
        fi
    else
        log_error "开发环境安装失败，请检查错误信息"
        exit 1
    fi
}

# 脚本入口
main "$@"