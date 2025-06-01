# IntelliJ IDEA 配置模板

本目录包含了针对游戏服务器框架开发的 IntelliJ IDEA 配置模板。

## 配置文件说明

### 1. 代码风格配置 (code-style.xml)
- 基于Google Java Style
- 适配项目特定规范
- 统一缩进和格式化规则

### 2. 代码检查配置 (inspections.xml)
- 启用严格的代码检查
- 自定义检查规则
- 与项目质量标准对齐

### 3. 实时模板 (live-templates.xml)
- 常用代码模板
- 快速生成标准代码结构
- 提高开发效率

### 4. 运行配置 (run-configurations/)
- Spring Boot应用启动配置
- 单元测试运行配置
- 调试配置模板

## 使用方法

### 1. 导入代码风格
1. 打开 IntelliJ IDEA
2. 进入 `File -> Settings -> Editor -> Code Style -> Java`
3. 点击设置图标，选择 `Import Scheme -> IntelliJ IDEA code style XML`
4. 选择 `code-style.xml` 文件

### 2. 导入代码检查配置
1. 进入 `File -> Settings -> Editor -> Inspections`
2. 点击设置图标，选择 `Import Profile`
3. 选择 `inspections.xml` 文件

### 3. 导入实时模板
1. 进入 `File -> Settings -> Editor -> Live Templates`
2. 点击设置图标，选择 `Import`
3. 选择 `live-templates.xml` 文件

### 4. 配置运行配置
1. 复制 `run-configurations/` 目录下的文件到 `.idea/runConfigurations/`
2. 重启 IntelliJ IDEA 或刷新项目

## 推荐插件

以下插件可以提高开发体验：

### 必装插件
- **Lombok**: 简化Java代码
- **Spring Boot Assistant**: Spring Boot开发助手
- **Maven Helper**: Maven依赖管理
- **GitToolBox**: Git集成增强

### 推荐插件
- **SonarLint**: 代码质量检查
- **CheckStyle-IDEA**: 代码风格检查
- **SpotBugs**: Bug检测
- **Rainbow Brackets**: 彩虹括号
- **String Manipulation**: 字符串处理工具

### 数据库插件
- **Database Navigator**: 数据库管理
- **JPA Buddy**: JPA开发助手

### 文档插件
- **Markdown**: Markdown支持
- **PlantUML**: UML图表

## IDE设置优化

### JVM参数优化
在 `idea64.exe.vmoptions` 或 `idea.vmoptions` 中添加：
```
-Xms2g
-Xmx8g
-XX:+UseG1GC
-XX:+UseStringDeduplication
```

### 编码设置
- 文件编码: UTF-8
- 行分隔符: LF (Unix)
- BOM: 无BOM

### 构建设置
- 编译器: Eclipse Compiler for Java (ECJ)
- 构建工具: Maven
- 自动构建: 启用

## 团队协作

### 版本控制设置
- 忽略文件: 遵循 `.gitignore` 规则
- 换行符: 统一使用LF
- 文件监控: 启用VCS文件状态

### 代码审查
- 启用代码检查
- 配置TODO过滤器
- 设置警告级别

## 故障排查

### 常见问题

1. **Maven项目无法识别**
   - 检查Maven设置
   - 重新导入项目
   - 清理并重新构建

2. **代码风格不生效**
   - 确认已导入配置
   - 检查项目级别设置
   - 重启IDE

3. **插件无法安装**
   - 检查网络连接
   - 更新插件仓库
   - 手动下载安装

## 更新说明

配置文件会随着项目发展持续更新，请定期检查并更新本地配置。