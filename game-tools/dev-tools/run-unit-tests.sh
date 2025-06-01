#!/bin/bash

# =============================================================================
# 游戏服务器框架单元测试执行脚本
# =============================================================================
# 功能: 执行项目的所有单元测试，生成测试报告和覆盖率报告
# 支持: Linux/macOS/Windows (通过WSL)
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
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly REPORTS_DIR="$PROJECT_ROOT/target/test-reports"

# 测试配置
TEST_PROFILE="${TEST_PROFILE:-test}"
PARALLEL_THREADS="${PARALLEL_THREADS:-1C}"
COVERAGE_ENABLED="${COVERAGE_ENABLED:-true}"
GENERATE_REPORTS="${GENERATE_REPORTS:-true}"

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
游戏服务器框架单元测试执行脚本

用法: $0 [选项]

选项:
  --profile <profile>     测试配置 (test|dev) [默认: test]
  --threads <count>       并行测试线程数 [默认: 1C]
  --no-coverage          禁用代码覆盖率
  --no-reports           禁用测试报告生成
  --module <module>       仅测试指定模块
  --test <test>           仅运行指定测试类
  --clean                 清理后测试
  --help                  显示此帮助信息

环境变量:
  TEST_PROFILE            测试配置
  PARALLEL_THREADS        并行线程数
  COVERAGE_ENABLED        是否启用覆盖率
  MAVEN_OPTS             Maven JVM参数

示例:
  $0                              # 运行所有单元测试
  $0 --module common              # 测试common模块
  $0 --test PlayerServiceTest     # 运行指定测试
  $0 --no-coverage --threads 4    # 4线程并行，无覆盖率

EOF
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --profile)
                TEST_PROFILE="$2"
                shift 2
                ;;
            --threads)
                PARALLEL_THREADS="$2"
                shift 2
                ;;
            --no-coverage)
                COVERAGE_ENABLED="false"
                shift
                ;;
            --no-reports)
                GENERATE_REPORTS="false"
                shift
                ;;
            --module)
                TEST_MODULE="$2"
                shift 2
                ;;
            --test)
                TEST_CLASS="$2"
                shift 2
                ;;
            --clean)
                CLEAN_BUILD="true"
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 检查环境
check_environment() {
    log_info "检查测试环境..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        log_error "Java未安装或不在PATH中"
        exit 1
    fi
    
    # 检查Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven未安装或不在PATH中"
        exit 1
    fi
    
    log_success "环境检查通过"
}

# 准备测试环境
prepare_test_environment() {
    log_info "准备测试环境..."
    
    # 创建报告目录
    mkdir -p "$REPORTS_DIR"
    
    # 清理构建
    if [[ "${CLEAN_BUILD:-false}" == "true" ]]; then
        log_info "清理构建..."
        mvn clean -q
    fi
    
    # 编译测试代码
    log_info "编译测试代码..."
    mvn test-compile -q -P "$TEST_PROFILE"
    
    log_success "测试环境准备完成"
}

# 运行单元测试
run_unit_tests() {
    log_info "运行单元测试..."
    
    local maven_args=()
    maven_args+=("-T" "$PARALLEL_THREADS")
    maven_args+=("-P" "$TEST_PROFILE")
    
    # 添加测试相关参数
    if [[ "$COVERAGE_ENABLED" == "true" ]]; then
        maven_args+=("-Djacoco.skip=false")
    else
        maven_args+=("-Djacoco.skip=true")
    fi
    
    # 模块过滤
    if [[ -n "${TEST_MODULE:-}" ]]; then
        maven_args+=("-pl" "$TEST_MODULE")
    fi
    
    # 测试类过滤
    if [[ -n "${TEST_CLASS:-}" ]]; then
        maven_args+=("-Dtest=$TEST_CLASS")
    fi
    
    # 执行测试
    local start_time
    start_time=$(date +%s)
    
    if mvn "${maven_args[@]}" test; then
        local end_time
        end_time=$(date +%s)
        local duration=$((end_time - start_time))
        log_success "单元测试完成，耗时: ${duration}秒"
    else
        log_error "单元测试失败"
        return 1
    fi
}

# 生成测试报告
generate_test_reports() {
    if [[ "$GENERATE_REPORTS" != "true" ]]; then
        log_warning "跳过测试报告生成"
        return 0
    fi
    
    log_info "生成测试报告..."
    
    # 生成Surefire报告
    if mvn surefire-report:report-only -q; then
        log_success "Surefire报告生成完成"
    else
        log_warning "Surefire报告生成失败"
    fi
    
    # 生成JaCoCo报告
    if [[ "$COVERAGE_ENABLED" == "true" ]]; then
        if mvn jacoco:report -q; then
            log_success "JaCoCo覆盖率报告生成完成"
        else
            log_warning "JaCoCo报告生成失败"
        fi
    fi
}

# 收集测试结果
collect_test_results() {
    log_info "收集测试结果..."
    
    local total_tests=0
    local failed_tests=0
    local skipped_tests=0
    local error_tests=0
    
    # 解析测试结果
    while IFS= read -r -d '' file; do
        if [[ -f "$file" ]]; then
            local tests
            local failures
            local errors
            local skipped
            
            tests=$(grep -o 'tests="[0-9]*"' "$file" | grep -o '[0-9]*' || echo "0")
            failures=$(grep -o 'failures="[0-9]*"' "$file" | grep -o '[0-9]*' || echo "0")
            errors=$(grep -o 'errors="[0-9]*"' "$file" | grep -o '[0-9]*' || echo "0")
            skipped=$(grep -o 'skipped="[0-9]*"' "$file" | grep -o '[0-9]*' || echo "0")
            
            total_tests=$((total_tests + tests))
            failed_tests=$((failed_tests + failures))
            error_tests=$((error_tests + errors))
            skipped_tests=$((skipped_tests + skipped))
        fi
    done < <(find "$PROJECT_ROOT" -name "TEST-*.xml" -print0)
    
    # 生成汇总报告
    local summary_file="$REPORTS_DIR/test-summary.txt"
    {
        echo "===== 单元测试汇总报告 ====="
        echo "测试时间: $(date)"
        echo "测试配置: $TEST_PROFILE"
        echo ""
        echo "测试统计:"
        echo "  总测试数: $total_tests"
        echo "  成功测试: $((total_tests - failed_tests - error_tests - skipped_tests))"
        echo "  失败测试: $failed_tests"
        echo "  错误测试: $error_tests"
        echo "  跳过测试: $skipped_tests"
        echo ""
        
        if [[ "$COVERAGE_ENABLED" == "true" ]]; then
            echo "代码覆盖率:"
            if [[ -f "$PROJECT_ROOT/target/site/jacoco/index.html" ]]; then
                # 提取覆盖率信息
                local coverage
                coverage=$(grep -o 'Total[^%]*%' "$PROJECT_ROOT/target/site/jacoco/index.html" | tail -1 || echo "未知")
                echo "  总覆盖率: $coverage"
            else
                echo "  覆盖率报告未生成"
            fi
            echo ""
        fi
        
        if [[ $failed_tests -gt 0 || $error_tests -gt 0 ]]; then
            echo "失败测试列表:"
            find "$PROJECT_ROOT" -name "TEST-*.xml" -exec grep -l 'failures="[1-9]"' {} \; -o -name "TEST-*.xml" -exec grep -l 'errors="[1-9]"' {} \; | while read -r file; do
                local class_name
                class_name=$(basename "$file" .xml | sed 's/TEST-//')
                echo "  - $class_name"
            done
            echo ""
        fi
        
        echo "测试状态: $([[ $failed_tests -eq 0 && $error_tests -eq 0 ]] && echo "通过" || echo "失败")"
    } > "$summary_file"
    
    log_success "测试结果收集完成: $summary_file"
    
    # 显示汇总信息
    echo
    echo "===== 测试结果汇总 ====="
    echo "总测试数: $total_tests"
    echo "成功: $((total_tests - failed_tests - error_tests - skipped_tests))"
    echo "失败: $failed_tests"
    echo "错误: $error_tests"
    echo "跳过: $skipped_tests"
    echo
    
    # 返回测试状态
    if [[ $failed_tests -gt 0 || $error_tests -gt 0 ]]; then
        return 1
    else
        return 0
    fi
}

# 复制报告文件
copy_reports() {
    log_info "复制测试报告..."
    
    # 复制Surefire报告
    if [[ -d "$PROJECT_ROOT/target/surefire-reports" ]]; then
        cp -r "$PROJECT_ROOT/target/surefire-reports" "$REPORTS_DIR/" 2>/dev/null || true
    fi
    
    # 复制站点报告
    if [[ -d "$PROJECT_ROOT/target/site" ]]; then
        cp -r "$PROJECT_ROOT/target/site" "$REPORTS_DIR/" 2>/dev/null || true
    fi
    
    # 收集所有模块的报告
    find "$PROJECT_ROOT" -name "surefire-reports" -type d | while read -r dir; do
        local module_name
        module_name=$(basename "$(dirname "$(dirname "$dir")")")
        mkdir -p "$REPORTS_DIR/modules/$module_name"
        cp -r "$dir"/* "$REPORTS_DIR/modules/$module_name/" 2>/dev/null || true
    done
    
    log_success "报告文件复制完成"
}

# 清理资源
cleanup() {
    log_info "清理测试资源..."
    
    # 停止可能启动的测试服务
    pkill -f "spring.profiles.active=test" 2>/dev/null || true
    
    # 清理临时文件
    find "$PROJECT_ROOT" -name "*.log" -path "*/target/*" -delete 2>/dev/null || true
    
    log_success "资源清理完成"
}

# 主函数
main() {
    local exit_code=0
    
    log_info "开始执行单元测试..."
    
    # 解析参数
    parse_args "$@"
    
    # 进入项目根目录
    cd "$PROJECT_ROOT"
    
    # 执行测试流程
    check_environment
    prepare_test_environment
    
    if run_unit_tests; then
        log_success "单元测试执行成功"
    else
        log_error "单元测试执行失败"
        exit_code=1
    fi
    
    generate_test_reports
    copy_reports
    
    if collect_test_results; then
        log_success "所有测试通过"
    else
        log_error "存在失败的测试"
        exit_code=1
    fi
    
    cleanup
    
    log_info "测试报告位置: $REPORTS_DIR"
    
    exit $exit_code
}

# 脚本入口
main "$@"