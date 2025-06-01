# Git 使用规范

## 1. 概述

本文档规定了游戏服务器框架项目的Git工作流程和规范，确保代码管理的规范性和协作的高效性。

## 2. 分支管理策略

### 2.1 分支模型

采用基于**Git Flow**的简化分支模型：

```
main (主分支)
├── develop (开发分支)
│   ├── feature/user-system (功能分支)
│   ├── feature/inventory-system (功能分支)
│   └── feature/chat-system (功能分支)
├── release/v1.1.0 (发布分支)
└── hotfix/critical-bug-fix (热修复分支)
```

### 2.2 分支类型和命名规范

#### 2.2.1 主要分支

**main分支**
- 作用：生产环境代码，始终保持可部署状态
- 命名：`main`
- 规则：
  - 只能通过Pull Request合并代码
  - 需要通过所有CI/CD检查
  - 需要至少2名开发者审查

**develop分支**
- 作用：开发环境代码，集成最新功能
- 命名：`develop`
- 规则：
  - 所有功能分支的目标分支
  - 定期合并到release分支

#### 2.2.2 临时分支

**功能分支 (Feature Branches)**
```bash
# 命名格式：feature/<功能描述>
feature/user-authentication
feature/player-inventory
feature/guild-system
feature/payment-integration

# 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/user-authentication

# 完成后合并到develop
git checkout develop
git pull origin develop
git merge --no-ff feature/user-authentication
git push origin develop
git branch -d feature/user-authentication
```

**发布分支 (Release Branches)**
```bash
# 命名格式：release/v<版本号>
release/v1.0.0
release/v1.1.0
release/v2.0.0

# 创建发布分支
git checkout develop
git pull origin develop
git checkout -b release/v1.1.0

# 发布完成后
git checkout main
git merge --no-ff release/v1.1.0
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin main --tags

git checkout develop
git merge --no-ff release/v1.1.0
git push origin develop
```

**热修复分支 (Hotfix Branches)**
```bash
# 命名格式：hotfix/<问题描述>
hotfix/critical-memory-leak
hotfix/security-vulnerability
hotfix/payment-error

# 创建热修复分支
git checkout main
git pull origin main
git checkout -b hotfix/critical-memory-leak

# 修复完成后
git checkout main
git merge --no-ff hotfix/critical-memory-leak
git tag -a v1.0.1 -m "Hotfix version 1.0.1"
git push origin main --tags

git checkout develop
git merge --no-ff hotfix/critical-memory-leak
git push origin develop
```

## 3. 提交规范

### 3.1 提交消息格式

采用[Conventional Commits](https://www.conventionalcommits.org/)规范：

```
<类型>([范围]): <描述>

[可选的正文]

[可选的脚注]
```

#### 3.1.1 提交类型

- `feat`: 新功能
- `fix`: 修复Bug
- `docs`: 文档更新
- `style`: 代码格式修改（不影响代码运行）
- `refactor`: 代码重构
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动
- `ci`: CI/CD相关
- `build`: 构建系统或外部依赖的变更

#### 3.1.2 提交示例

**功能开发**
```bash
feat(user): 添加用户注册功能

- 实现用户注册API
- 添加邮箱验证功能
- 集成短信验证服务

Closes #123
```

**Bug修复**
```bash
fix(inventory): 修复物品堆叠数量错误

修复了当物品堆叠超过最大数量时，
系统未正确处理的问题。

Fixes #456
```

**文档更新**
```bash
docs(api): 更新用户API文档

添加了新的认证方式说明和错误码定义
```

**重构**
```bash
refactor(database): 优化数据库连接池配置

- 调整连接池大小
- 优化连接超时设置
- 改进连接健康检查
```

### 3.2 提交最佳实践

#### 3.2.1 提交频率
- 每个逻辑单元一个提交
- 避免一次提交包含多个不相关的修改
- 经常提交，避免单次提交过大

```bash
# 好的提交序列
git commit -m "feat(user): 添加用户模型定义"
git commit -m "feat(user): 实现用户注册服务"
git commit -m "feat(user): 添加用户注册API端点"
git commit -m "test(user): 添加用户注册测试用例"

# 不好的提交
git commit -m "添加用户功能" # 包含了模型、服务、API、测试等多个内容
```

#### 3.2.2 提交前检查
```bash
# 使用pre-commit hook进行检查
#!/bin/sh
# .git/hooks/pre-commit

# 代码格式检查
mvn spotless:check
if [ $? -ne 0 ]; then
    echo "代码格式检查失败，请运行 mvn spotless:apply 修复"
    exit 1
fi

# 单元测试
mvn test
if [ $? -ne 0 ]; then
    echo "单元测试失败，请修复后再提交"
    exit 1
fi

echo "提交前检查通过"
```

## 4. Pull Request 规范

### 4.1 PR创建流程

1. **创建功能分支**
```bash
git checkout develop
git pull origin develop
git checkout -b feature/new-feature
```

2. **开发和提交**
```bash
# 开发过程中的提交
git add .
git commit -m "feat(module): 实现基础功能"
git push origin feature/new-feature
```

3. **创建Pull Request**
- 在GitHub/GitLab上创建PR
- 填写详细的PR描述
- 指定审查者
- 关联相关Issue

### 4.2 PR模板

```markdown
## 📋 变更类型
- [ ] 新功能 (feature)
- [ ] Bug修复 (fix)
- [ ] 文档更新 (docs)
- [ ] 代码重构 (refactor)
- [ ] 性能优化 (perf)
- [ ] 测试相关 (test)

## 📝 变更描述
简要描述这个PR解决的问题和实现的功能。

## 🔗 相关Issue
- Closes #123
- Related to #456

## 🧪 测试说明
- [ ] 已添加单元测试
- [ ] 已添加集成测试
- [ ] 已进行手动测试
- [ ] 测试覆盖率保持在80%以上

### 测试用例
1. 测试用例1描述
2. 测试用例2描述

## 📸 截图/演示
如果有UI变更，请提供截图或演示视频。

## ⚠️ 破坏性变更
- [ ] 此PR包含破坏性变更
- [ ] 已更新CHANGELOG.md
- [ ] 已更新相关文档

### 破坏性变更说明
如果有破坏性变更，请详细说明：
- 影响的API或功能
- 迁移指南
- 兼容性说明

## 📋 检查清单
- [ ] 代码遵循项目编码规范
- [ ] 已添加或更新相关文档
- [ ] 所有测试通过
- [ ] 代码已进行自我审查
- [ ] 已考虑性能影响
- [ ] 已考虑安全性

## 📚 其他信息
其他需要审查者注意的信息。
```

### 4.3 代码审查规范

#### 4.3.1 审查者职责
- 检查代码质量和规范性
- 验证功能实现的正确性
- 评估性能和安全性影响
- 确保测试覆盖率充分

#### 4.3.2 审查清单

**功能性检查**
- [ ] 代码实现符合需求
- [ ] 边界条件处理正确
- [ ] 错误处理完善
- [ ] 业务逻辑正确

**代码质量检查**
- [ ] 命名规范清晰
- [ ] 代码结构合理
- [ ] 无重复代码
- [ ] 注释充分准确

**测试检查**
- [ ] 单元测试充分
- [ ] 集成测试覆盖
- [ ] 测试用例有意义
- [ ] 测试数据合理

**性能和安全检查**
- [ ] 无明显性能问题
- [ ] 数据库查询优化
- [ ] 输入验证充分
- [ ] 无安全漏洞

#### 4.3.3 审查反馈示例

**建设性反馈**
```markdown
💡 **建议**: 这里可以使用Builder模式来简化对象创建：

```java
// 当前实现
Player player = new Player();
player.setUsername(username);
player.setEmail(email);
player.setLevel(1);

// 建议改为
Player player = Player.builder()
    .username(username)
    .email(email)
    .level(1)
    .build();
```

🐛 **问题**: 这里缺少空值检查，可能导致NPE：

```java
// 第42行
String upperName = username.toUpperCase(); // 如果username为null会抛异常

// 建议修改为
String upperName = username != null ? username.toUpperCase() : "";
```

✅ **认可**: 错误处理做得很好，日志记录也很详细！
```

## 5. 版本标签规范

### 5.1 版本号格式

采用[语义化版本](https://semver.org/)规范：`MAJOR.MINOR.PATCH`

- `MAJOR`: 主版本号，不兼容的API修改
- `MINOR`: 次版本号，向下兼容的功能性新增
- `PATCH`: 修订号，向下兼容的问题修正

### 5.2 标签示例

```bash
# 功能发布
git tag -a v1.1.0 -m "Release version 1.1.0

新功能:
- 添加用户系统
- 实现物品系统
- 集成支付功能

Bug修复:
- 修复内存泄漏问题
- 解决并发安全问题"

# 热修复发布
git tag -a v1.0.1 -m "Hotfix version 1.0.1

修复了关键的安全漏洞"

# 推送标签
git push origin --tags
```

### 5.3 预发布版本

```bash
# Alpha版本（内部测试）
v1.1.0-alpha.1
v1.1.0-alpha.2

# Beta版本（公开测试）
v1.1.0-beta.1
v1.1.0-beta.2

# 候选版本（准备发布）
v1.1.0-rc.1
v1.1.0-rc.2
```

## 6. Git 工作流程示例

### 6.1 日常开发流程

```bash
# 1. 更新本地develop分支
git checkout develop
git pull origin develop

# 2. 创建功能分支
git checkout -b feature/user-login

# 3. 开发过程中的提交
git add src/main/java/com/lx/gameserver/user/
git commit -m "feat(user): 添加用户登录验证逻辑"

git add src/test/java/com/lx/gameserver/user/
git commit -m "test(user): 添加用户登录测试用例"

# 4. 推送到远程
git push origin feature/user-login

# 5. 创建Pull Request
# 在GitHub/GitLab界面创建PR

# 6. 代码审查和修改
# 根据审查意见修改代码

git add .
git commit -m "fix(user): 修复登录验证逻辑问题"
git push origin feature/user-login

# 7. 合并到develop
# PR通过审查后，使用Squash and merge
```

### 6.2 发布流程

```bash
# 1. 创建发布分支
git checkout develop
git pull origin develop
git checkout -b release/v1.1.0

# 2. 更新版本号和变更日志
# 修改pom.xml中的版本号
# 更新CHANGELOG.md

git add pom.xml CHANGELOG.md
git commit -m "chore(release): 准备v1.1.0发布"

# 3. 推送发布分支
git push origin release/v1.1.0

# 4. 创建发布PR到main
# 审查通过后合并

# 5. 创建标签
git checkout main
git pull origin main
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin --tags

# 6. 合并回develop
git checkout develop
git merge --no-ff release/v1.1.0
git push origin develop

# 7. 删除发布分支
git branch -d release/v1.1.0
git push origin --delete release/v1.1.0
```

## 7. 常见问题和解决方案

### 7.1 提交冲突解决

```bash
# 1. 拉取最新代码
git checkout develop
git pull origin develop

# 2. 变基功能分支
git checkout feature/my-feature
git rebase develop

# 3. 解决冲突
# 编辑冲突文件
git add <解决冲突的文件>
git rebase --continue

# 4. 强制推送（小心使用）
git push origin feature/my-feature --force-with-lease
```

### 7.2 撤销提交

```bash
# 撤销最后一次提交（保留更改）
git reset --soft HEAD~1

# 撤销最后一次提交（不保留更改）
git reset --hard HEAD~1

# 撤销已推送的提交
git revert <commit-hash>
git push origin <branch-name>
```

### 7.3 清理分支

```bash
# 查看所有分支
git branch -a

# 删除本地已合并的分支
git branch --merged | grep -v "\*\|main\|develop" | xargs -n 1 git branch -d

# 删除远程跟踪分支
git remote prune origin

# 删除远程分支
git push origin --delete feature/old-feature
```

## 8. Git 工具推荐

### 8.1 命令行工具

**Git别名配置**
```bash
# ~/.gitconfig
[alias]
    st = status
    co = checkout
    br = branch
    ci = commit
    df = diff
    lg = log --oneline --graph --all --decorate
    unstage = reset HEAD --
    last = log -1 HEAD
    visual = !gitk
```

**有用的Git命令**
```bash
# 查看美化的提交历史
git log --oneline --graph --all --decorate

# 查看文件修改历史
git log -p <file>

# 查看某次提交的详细信息
git show <commit-hash>

# 查看分支合并图
git log --graph --pretty=oneline --abbrev-commit
```

### 8.2 GUI工具

- **SourceTree**: 免费的Git GUI客户端
- **GitKraken**: 功能强大的Git客户端
- **VS Code Git**: 内置Git支持
- **IntelliJ IDEA Git**: IDE集成Git工具

## 9. 团队协作最佳实践

### 9.1 沟通规范

- 在PR中详细描述变更内容
- 及时响应代码审查反馈
- 使用Issue跟踪问题和功能请求
- 在提交消息中关联相关Issue

### 9.2 冲突预防

- 经常同步develop分支
- 保持功能分支小而专注
- 及时合并已完成的功能
- 避免长时间的功能分支

### 9.3 质量保证

- 提交前进行自我代码审查
- 确保所有测试通过
- 遵循编码规范
- 及时更新文档

## 10. 持续改进

本Git规范会根据团队实践和反馈持续优化。建议：

1. 定期回顾Git工作流程的效果
2. 收集团队反馈和建议
3. 学习业界最佳实践
4. 根据项目发展调整规范

---

*最后更新时间: 2025-01-01*  
*维护人员: 开发团队*