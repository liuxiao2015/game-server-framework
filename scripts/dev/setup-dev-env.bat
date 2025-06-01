@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM 游戏服务器框架开发环境一键安装脚本 - Windows版本
REM =============================================================================
REM 功能: 自动检查和安装开发环境所需的所有工具和依赖
REM 支持: Windows 10/11
REM 作者: liuxiao2015
REM 版本: 1.0.0
REM =============================================================================

:: 设置控制台代码页为UTF-8
chcp 65001 >nul

:: 颜色定义（通过PowerShell实现）
set "RED=[31m"
set "GREEN=[32m"
set "YELLOW=[33m"
set "BLUE=[34m"
set "NC=[0m"

:: 日志函数
:log_info
echo [94m[INFO][0m %~1
exit /b

:log_success
echo [92m[SUCCESS][0m %~1
exit /b

:log_warning
echo [93m[WARNING][0m %~1
exit /b

:log_error
echo [91m[ERROR][0m %~1
exit /b

:: 检查管理员权限
:check_admin
net session >nul 2>&1
if %errorLevel% == 0 (
    call :log_info "检测到管理员权限"
) else (
    call :log_warning "建议以管理员身份运行以获得最佳体验"
)
exit /b

:: 检查Java版本
:check_java
call :log_info "检查Java环境..."

java -version >nul 2>&1
if %errorLevel% == 0 (
    for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set JAVA_VERSION=%%g
        set JAVA_VERSION=!JAVA_VERSION:"=!
    )
    
    :: 提取主版本号
    for /f "delims=." %%a in ("!JAVA_VERSION!") do set JAVA_MAJOR=%%a
    if "!JAVA_MAJOR!" == "1" (
        for /f "tokens=2 delims=." %%b in ("!JAVA_VERSION!") do set JAVA_MAJOR=%%b
    )
    
    if !JAVA_MAJOR! geq 17 (
        call :log_success "Java !JAVA_VERSION! 已安装"
        exit /b 0
    ) else (
        call :log_warning "Java版本过低: !JAVA_VERSION!，需要Java 17+"
    )
) else (
    call :log_warning "Java未安装"
)
exit /b 1

:: 安装Java
:install_java
call :log_info "安装Java 21..."

:: 检查是否有包管理器
where choco >nul 2>&1
if %errorLevel% == 0 (
    call :log_info "使用Chocolatey安装Java..."
    choco install openjdk21 -y
) else (
    where winget >nul 2>&1
    if %errorLevel% == 0 (
        call :log_info "使用winget安装Java..."
        winget install Microsoft.OpenJDK.21
    ) else (
        call :log_warning "未检测到包管理器，请手动安装Java 21"
        call :log_info "下载地址: https://adoptium.net/"
        echo.
        echo 请下载并安装Java 21，然后重新运行此脚本
        pause
        exit /b 1
    )
)

call :log_success "Java安装完成"
exit /b

:: 检查Maven
:check_maven
call :log_info "检查Maven环境..."

mvn -version >nul 2>&1
if %errorLevel% == 0 (
    for /f "tokens=3" %%g in ('mvn -version ^| findstr "Apache Maven"') do (
        call :log_success "Maven %%g 已安装"
        exit /b 0
    )
) else (
    call :log_warning "Maven未安装"
)
exit /b 1

:: 安装Maven
:install_maven
call :log_info "安装Maven..."

where choco >nul 2>&1
if %errorLevel% == 0 (
    choco install maven -y
) else (
    where winget >nul 2>&1
    if %errorLevel% == 0 (
        winget install Apache.Maven
    ) else (
        call :log_warning "未检测到包管理器，请手动安装Maven"
        call :log_info "下载地址: https://maven.apache.org/download.cgi"
        echo.
        echo 请下载并安装Maven，然后重新运行此脚本
        pause
        exit /b 1
    )
)

call :log_success "Maven安装完成"
exit /b

:: 检查Git
:check_git
call :log_info "检查Git环境..."

git --version >nul 2>&1
if %errorLevel% == 0 (
    for /f "tokens=3" %%g in ('git --version') do (
        call :log_success "Git %%g 已安装"
        exit /b 0
    )
) else (
    call :log_warning "Git未安装"
)
exit /b 1

:: 安装Git
:install_git
call :log_info "安装Git..."

where choco >nul 2>&1
if %errorLevel% == 0 (
    choco install git -y
) else (
    where winget >nul 2>&1
    if %errorLevel% == 0 (
        winget install Git.Git
    ) else (
        call :log_warning "未检测到包管理器，请手动安装Git"
        call :log_info "下载地址: https://git-scm.com/download/win"
        echo.
        echo 请下载并安装Git，然后重新运行此脚本
        pause
        exit /b 1
    )
)

call :log_success "Git安装完成"
exit /b

:: 检查Docker
:check_docker
call :log_info "检查Docker环境..."

docker --version >nul 2>&1
if %errorLevel% == 0 (
    for /f "tokens=3" %%g in ('docker --version') do (
        set DOCKER_VERSION=%%g
        set DOCKER_VERSION=!DOCKER_VERSION:,=!
        call :log_success "Docker !DOCKER_VERSION! 已安装"
        exit /b 0
    )
) else (
    call :log_warning "Docker未安装或未启动"
)
exit /b 1

:: 安装Docker
:install_docker
call :log_info "安装Docker Desktop..."

where choco >nul 2>&1
if %errorLevel% == 0 (
    choco install docker-desktop -y
) else (
    where winget >nul 2>&1
    if %errorLevel% == 0 (
        winget install Docker.DockerDesktop
    ) else (
        call :log_warning "请手动下载并安装Docker Desktop"
        call :log_info "下载地址: https://docs.docker.com/desktop/windows/install/"
        start https://docs.docker.com/desktop/windows/install/
        pause
    )
)

call :log_success "Docker安装完成（可能需要重启）"
exit /b

:: 配置Git
:configure_git
call :log_info "配置Git..."

git config --global user.name >nul 2>&1
if %errorLevel% neq 0 (
    set /p GIT_USERNAME="请输入Git用户名: "
    git config --global user.name "!GIT_USERNAME!"
)

git config --global user.email >nul 2>&1
if %errorLevel% neq 0 (
    set /p GIT_EMAIL="请输入Git邮箱: "
    git config --global user.email "!GIT_EMAIL!"
)

:: 设置常用Git配置
git config --global init.defaultBranch main
git config --global core.autocrlf true
git config --global core.editor notepad
git config --global pull.rebase false

call :log_success "Git配置完成"
exit /b

:: 创建开发目录结构
:create_dev_structure
call :log_info "创建开发目录结构..."

if not exist "%USERPROFILE%\.gameserver" mkdir "%USERPROFILE%\.gameserver"
if not exist "%USERPROFILE%\.gameserver\logs" mkdir "%USERPROFILE%\.gameserver\logs"
if not exist "%USERPROFILE%\.gameserver\config" mkdir "%USERPROFILE%\.gameserver\config"
if not exist "%USERPROFILE%\.gameserver\data" mkdir "%USERPROFILE%\.gameserver\data"
if not exist "%USERPROFILE%\.gameserver\tools" mkdir "%USERPROFILE%\.gameserver\tools"

call :log_success "开发目录结构创建完成"
exit /b

:: 验证安装
:verify_installation
call :log_info "验证安装..."

set errors=0

call :check_java
if %errorLevel% neq 0 (
    call :log_error "Java验证失败"
    set /a errors+=1
)

call :check_maven
if %errorLevel% neq 0 (
    call :log_error "Maven验证失败"
    set /a errors+=1
)

call :check_git
if %errorLevel% neq 0 (
    call :log_error "Git验证失败"
    set /a errors+=1
)

if !errors! == 0 (
    call :log_success "所有组件验证通过！"
    exit /b 0
) else (
    call :log_error "验证发现 !errors! 个问题"
    exit /b 1
)

:: 显示帮助信息
:show_help
echo 游戏服务器框架开发环境安装脚本 - Windows版本
echo.
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo     /h, /help          显示帮助信息
echo     /v, /verify        仅验证环境，不安装
echo     /skip-docker       跳过Docker安装
echo     /force             强制重新安装所有组件
echo.
echo 示例:
echo     %~nx0                  # 完整安装
echo     %~nx0 /verify          # 仅验证环境
echo     %~nx0 /skip-docker     # 跳过Docker安装
echo.
exit /b

:: 主函数
:main
set VERIFY_ONLY=false
set SKIP_DOCKER=false
set FORCE_INSTALL=false

:: 解析命令行参数
:parse_args
if "%~1"=="" goto start_install
if /i "%~1"=="/h" goto show_help_and_exit
if /i "%~1"=="/help" goto show_help_and_exit
if /i "%~1"=="/v" set VERIFY_ONLY=true
if /i "%~1"=="/verify" set VERIFY_ONLY=true
if /i "%~1"=="/skip-docker" set SKIP_DOCKER=true
if /i "%~1"=="/force" set FORCE_INSTALL=true
shift
goto parse_args

:show_help_and_exit
call :show_help
exit /b 0

:start_install
call :log_info "开始游戏服务器框架开发环境安装..."

:: 检查管理员权限
call :check_admin

if "%VERIFY_ONLY%"=="true" (
    call :verify_installation
    exit /b !errorLevel!
)

:: 检查并安装Java
call :check_java
if %errorLevel% neq 0 (
    call :install_java
) else if "%FORCE_INSTALL%"=="true" (
    call :install_java
)

:: 检查并安装Maven
call :check_maven
if %errorLevel% neq 0 (
    call :install_maven
) else if "%FORCE_INSTALL%"=="true" (
    call :install_maven
)

:: 检查并安装Git
call :check_git
if %errorLevel% neq 0 (
    call :install_git
) else if "%FORCE_INSTALL%"=="true" (
    call :install_git
)

:: 配置Git
call :configure_git

:: 安装Docker（可选）
if not "%SKIP_DOCKER%"=="true" (
    call :check_docker
    if %errorLevel% neq 0 (
        echo.
        set /p INSTALL_DOCKER="是否安装Docker Desktop? [y/N]: "
        if /i "!INSTALL_DOCKER!"=="y" call :install_docker
    ) else if "%FORCE_INSTALL%"=="true" (
        call :install_docker
    )
)

:: 创建开发目录结构
call :create_dev_structure

:: 验证安装
call :verify_installation
if %errorLevel% == 0 (
    call :log_success "开发环境安装完成！"
    echo.
    call :log_info "下一步:"
    call :log_info "1. 重启命令提示符以使环境变量生效"
    call :log_info "2. 运行项目初始化: scripts\dev\init-project.bat"
    call :log_info "3. 启动本地开发环境: scripts\dev\run-local.bat"
    echo.
    pause
) else (
    call :log_error "开发环境安装失败，请检查错误信息"
    pause
    exit /b 1
)

exit /b 0

:: 脚本入口
call :main %*