#!/bin/bash

# =============================================================================
# 游戏服务器框架代码生成器
# =============================================================================
# 功能: 快速生成常用的代码模板和文件结构
# 支持: Entity、Service、Controller、Test等代码生成
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
readonly TEMPLATES_DIR="$SCRIPT_DIR/templates"

# 生成配置
GENERATE_TYPE=""
CLASS_NAME=""
PACKAGE_NAME="com.lx.gameserver"
MODULE_NAME=""
AUTHOR="${USER:-developer}"

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
游戏服务器框架代码生成器

用法: $0 <type> <class-name> [选项]

生成类型:
  entity          生成实体类
  service         生成服务类
  controller      生成控制器类
  repository      生成仓储类
  dto             生成DTO类
  test            生成测试类
  module          生成完整模块

选项:
  --package <package>     包名 [默认: com.lx.gameserver]
  --module <module>       模块名
  --author <author>       作者名 [默认: 当前用户]
  --help                  显示此帮助信息

示例:
  $0 entity Player --module business
  $0 service PlayerService --package com.lx.gameserver.business
  $0 controller PlayerController --module business
  $0 module player --package com.lx.gameserver.business

EOF
}

# 解析命令行参数
parse_args() {
    if [[ $# -lt 2 ]]; then
        show_help
        exit 1
    fi
    
    GENERATE_TYPE="$1"
    CLASS_NAME="$2"
    shift 2
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --package)
                PACKAGE_NAME="$2"
                shift 2
                ;;
            --module)
                MODULE_NAME="$2"
                shift 2
                ;;
            --author)
                AUTHOR="$2"
                shift 2
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

# 创建模板目录
create_templates() {
    mkdir -p "$TEMPLATES_DIR"
    
    # 创建Entity模板
    cat > "$TEMPLATES_DIR/Entity.java.template" << 'EOF'
package {{PACKAGE_NAME}}.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * {{CLASS_NAME}}实体类
 *
 * @author {{AUTHOR}}
 * @since {{DATE}}
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class {{CLASS_NAME}} {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 更新者
     */
    private String updatedBy;

    /**
     * 是否删除（0：未删除，1：已删除）
     */
    private Boolean deleted = false;
}
EOF

    # 创建Service模板
    cat > "$TEMPLATES_DIR/Service.java.template" << 'EOF'
package {{PACKAGE_NAME}}.service;

import {{PACKAGE_NAME}}.entity.{{ENTITY_NAME}};
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * {{CLASS_NAME}}服务类
 *
 * @author {{AUTHOR}}
 * @since {{DATE}}
 */
@Slf4j
@Service
public class {{CLASS_NAME}} {

    /**
     * 根据ID查询{{ENTITY_NAME}}
     *
     * @param id 主键ID
     * @return {{ENTITY_NAME}}对象
     */
    public {{ENTITY_NAME}} getById(Long id) {
        log.debug("查询{{ENTITY_NAME}}, id: {}", id);
        // TODO: 实现查询逻辑
        return null;
    }

    /**
     * 保存{{ENTITY_NAME}}
     *
     * @param entity {{ENTITY_NAME}}对象
     * @return 保存后的{{ENTITY_NAME}}对象
     */
    public {{ENTITY_NAME}} save({{ENTITY_NAME}} entity) {
        log.debug("保存{{ENTITY_NAME}}: {}", entity);
        // TODO: 实现保存逻辑
        return entity;
    }

    /**
     * 根据ID删除{{ENTITY_NAME}}
     *
     * @param id 主键ID
     */
    public void deleteById(Long id) {
        log.debug("删除{{ENTITY_NAME}}, id: {}", id);
        // TODO: 实现删除逻辑
    }
}
EOF

    # 创建Controller模板
    cat > "$TEMPLATES_DIR/Controller.java.template" << 'EOF'
package {{PACKAGE_NAME}}.controller;

import {{PACKAGE_NAME}}.entity.{{ENTITY_NAME}};
import {{PACKAGE_NAME}}.service.{{SERVICE_NAME}};
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;

/**
 * {{CLASS_NAME}}控制器
 *
 * @author {{AUTHOR}}
 * @since {{DATE}}
 */
@Slf4j
@RestController
@RequestMapping("/api/{{LOWER_ENTITY_NAME}}")
public class {{CLASS_NAME}} {

    @Autowired
    private {{SERVICE_NAME}} {{LOWER_SERVICE_NAME}};

    /**
     * 根据ID查询{{ENTITY_NAME}}
     *
     * @param id 主键ID
     * @return {{ENTITY_NAME}}对象
     */
    @GetMapping("/{id}")
    public {{ENTITY_NAME}} getById(@PathVariable Long id) {
        log.debug("查询{{ENTITY_NAME}}, id: {}", id);
        return {{LOWER_SERVICE_NAME}}.getById(id);
    }

    /**
     * 创建{{ENTITY_NAME}}
     *
     * @param entity {{ENTITY_NAME}}对象
     * @return 创建后的{{ENTITY_NAME}}对象
     */
    @PostMapping
    public {{ENTITY_NAME}} create(@RequestBody {{ENTITY_NAME}} entity) {
        log.debug("创建{{ENTITY_NAME}}: {}", entity);
        return {{LOWER_SERVICE_NAME}}.save(entity);
    }

    /**
     * 更新{{ENTITY_NAME}}
     *
     * @param id 主键ID
     * @param entity {{ENTITY_NAME}}对象
     * @return 更新后的{{ENTITY_NAME}}对象
     */
    @PutMapping("/{id}")
    public {{ENTITY_NAME}} update(@PathVariable Long id, @RequestBody {{ENTITY_NAME}} entity) {
        log.debug("更新{{ENTITY_NAME}}, id: {}, entity: {}", id, entity);
        entity.setId(id);
        return {{LOWER_SERVICE_NAME}}.save(entity);
    }

    /**
     * 删除{{ENTITY_NAME}}
     *
     * @param id 主键ID
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        log.debug("删除{{ENTITY_NAME}}, id: {}", id);
        {{LOWER_SERVICE_NAME}}.deleteById(id);
    }
}
EOF

    # 创建Test模板
    cat > "$TEMPLATES_DIR/Test.java.template" << 'EOF'
package {{PACKAGE_NAME}};

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {{CLASS_NAME}}测试类
 *
 * @author {{AUTHOR}}
 * @since {{DATE}}
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class {{CLASS_NAME}}Test {

    @BeforeEach
    void setUp() {
        log.debug("测试初始化");
        // TODO: 初始化测试数据
    }

    @AfterEach
    void tearDown() {
        log.debug("测试清理");
        // TODO: 清理测试数据
    }

    @Test
    void testExample() {
        log.debug("执行示例测试");
        // TODO: 实现测试逻辑
        assertTrue(true, "示例测试");
    }
}
EOF
}

# 替换模板变量
replace_template_vars() {
    local template_file="$1"
    local output_file="$2"
    
    # 计算相关名称
    local entity_name
    local service_name
    local lower_entity_name
    local lower_service_name
    
    if [[ "$GENERATE_TYPE" == "service" ]]; then
        entity_name="${CLASS_NAME%Service}"
        service_name="$CLASS_NAME"
    elif [[ "$GENERATE_TYPE" == "controller" ]]; then
        entity_name="${CLASS_NAME%Controller}"
        service_name="${entity_name}Service"
    else
        entity_name="$CLASS_NAME"
        service_name="${CLASS_NAME}Service"
    fi
    
    lower_entity_name=$(echo "$entity_name" | sed 's/\(.\)/\L\1/')
    lower_service_name=$(echo "$service_name" | sed 's/\(.\)/\L\1/')
    
    # 替换变量
    sed \
        -e "s/{{PACKAGE_NAME}}/$PACKAGE_NAME/g" \
        -e "s/{{CLASS_NAME}}/$CLASS_NAME/g" \
        -e "s/{{ENTITY_NAME}}/$entity_name/g" \
        -e "s/{{SERVICE_NAME}}/$service_name/g" \
        -e "s/{{LOWER_ENTITY_NAME}}/$lower_entity_name/g" \
        -e "s/{{LOWER_SERVICE_NAME}}/$lower_service_name/g" \
        -e "s/{{AUTHOR}}/$AUTHOR/g" \
        -e "s/{{DATE}}/$(date +%Y-%m-%d)/g" \
        "$template_file" > "$output_file"
}

# 生成Entity
generate_entity() {
    log_info "生成Entity: $CLASS_NAME"
    
    local output_dir="$PROJECT_ROOT"
    if [[ -n "$MODULE_NAME" ]]; then
        output_dir="$PROJECT_ROOT/$MODULE_NAME"
    fi
    
    # 创建包目录
    local package_dir="$output_dir/src/main/java/${PACKAGE_NAME//./\/}/entity"
    mkdir -p "$package_dir"
    
    # 生成文件
    local output_file="$package_dir/${CLASS_NAME}.java"
    replace_template_vars "$TEMPLATES_DIR/Entity.java.template" "$output_file"
    
    log_success "Entity生成完成: $output_file"
}

# 生成Service
generate_service() {
    log_info "生成Service: $CLASS_NAME"
    
    local output_dir="$PROJECT_ROOT"
    if [[ -n "$MODULE_NAME" ]]; then
        output_dir="$PROJECT_ROOT/$MODULE_NAME"
    fi
    
    # 创建包目录
    local package_dir="$output_dir/src/main/java/${PACKAGE_NAME//./\/}/service"
    mkdir -p "$package_dir"
    
    # 生成文件
    local output_file="$package_dir/${CLASS_NAME}.java"
    replace_template_vars "$TEMPLATES_DIR/Service.java.template" "$output_file"
    
    log_success "Service生成完成: $output_file"
}

# 生成Controller
generate_controller() {
    log_info "生成Controller: $CLASS_NAME"
    
    local output_dir="$PROJECT_ROOT"
    if [[ -n "$MODULE_NAME" ]]; then
        output_dir="$PROJECT_ROOT/$MODULE_NAME"
    fi
    
    # 创建包目录
    local package_dir="$output_dir/src/main/java/${PACKAGE_NAME//./\/}/controller"
    mkdir -p "$package_dir"
    
    # 生成文件
    local output_file="$package_dir/${CLASS_NAME}.java"
    replace_template_vars "$TEMPLATES_DIR/Controller.java.template" "$output_file"
    
    log_success "Controller生成完成: $output_file"
}

# 生成Test
generate_test() {
    log_info "生成Test: $CLASS_NAME"
    
    local output_dir="$PROJECT_ROOT"
    if [[ -n "$MODULE_NAME" ]]; then
        output_dir="$PROJECT_ROOT/$MODULE_NAME"
    fi
    
    # 创建测试包目录
    local package_dir="$output_dir/src/test/java/${PACKAGE_NAME//./\/}"
    mkdir -p "$package_dir"
    
    # 生成文件
    local output_file="$package_dir/${CLASS_NAME}Test.java"
    replace_template_vars "$TEMPLATES_DIR/Test.java.template" "$output_file"
    
    log_success "Test生成完成: $output_file"
}

# 生成完整模块
generate_module() {
    log_info "生成Module: $CLASS_NAME"
    
    local module_dir="$PROJECT_ROOT/$CLASS_NAME"
    local capitalized_name="$(echo "$CLASS_NAME" | sed 's/\(.\)/\U\1/')"
    
    # 创建模块目录结构
    mkdir -p "$module_dir/src/main/java/${PACKAGE_NAME//./\/}/$CLASS_NAME"/{entity,service,controller,repository}
    mkdir -p "$module_dir/src/test/java/${PACKAGE_NAME//./\/}/$CLASS_NAME"
    mkdir -p "$module_dir/src/main/resources"
    
    # 生成pom.xml
    cat > "$module_dir/pom.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.lx.gameserver</groupId>
        <artifactId>game-server-framework</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>$CLASS_NAME</artifactId>
    <name>Game Server Framework - ${capitalized_name} Module</name>
    <description>${capitalized_name}模块</description>

    <dependencies>
        <dependency>
            <groupId>com.lx.gameserver</groupId>
            <artifactId>common-core</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
EOF

    # 生成README.md
    cat > "$module_dir/README.md" << EOF
# ${capitalized_name}模块

## 概述

${capitalized_name}模块提供了...功能。

## 功能特性

- [ ] 特性1
- [ ] 特性2
- [ ] 特性3

## 快速开始

### 依赖引入

\`\`\`xml
<dependency>
    <groupId>com.lx.gameserver</groupId>
    <artifactId>$CLASS_NAME</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
\`\`\`

### 使用示例

\`\`\`java
// TODO: 添加使用示例
\`\`\`

## API文档

// TODO: 添加API文档

## 配置说明

// TODO: 添加配置说明
EOF

    log_success "Module生成完成: $module_dir"
}

# 主函数
main() {
    log_info "开始代码生成..."
    
    # 解析参数
    parse_args "$@"
    
    # 创建模板
    create_templates
    
    # 根据类型生成代码
    case "$GENERATE_TYPE" in
        entity)
            generate_entity
            ;;
        service)
            generate_service
            ;;
        controller)
            generate_controller
            ;;
        test)
            generate_test
            ;;
        module)
            generate_module
            ;;
        *)
            log_error "不支持的生成类型: $GENERATE_TYPE"
            show_help
            exit 1
            ;;
    esac
    
    log_success "代码生成完成！"
}

# 脚本入口
main "$@"