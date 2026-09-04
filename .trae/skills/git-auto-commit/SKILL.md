---
name: "git-auto-commit"
description: "Automatically commits all changes to git and pushes to remote at the end of every response. Invoke after ANY code change, file creation, or modification is completed."
---

# Git 自动提交并推送（git-auto-commit）

**硬性规则：每次回答结束、完成一轮工作后，必须将当前所有变更提交到 git 并推送到远程仓库。**

## 触发时机

- 每次回答结束前（已完成文件创建/修改/删除等操作）
- 用户明确要求提交时
- 一个里程碑或功能点完成后

## 提交规则

1. 使用 `git add -A` 暂存所有变更
2. 提交信息使用中文，简洁描述本轮做了什么，格式：`<动词>：<内容>`
   - 如：`创建 M1 基础设施骨架与测试`
   - 如：`新增 git-auto-commit skill 约束`
   - 如：`修复 BusinessExceptionTest 类名冲突`
3. 如果有未跟踪的敏感文件（.env、credentials、node_modules），提交前先检查并排除
4. **提交后必须 push 到远程仓库**

## 推送规则

1. 如果远程仓库已配置（`git remote -v` 有 origin），提交后自动执行 `git push`
2. 如果 push 失败（如远程有冲突），先 `git pull --rebase` 再 push
3. 如果未配置远程仓库，跳过 push 并在提交信息中提示用户

## 执行方式

```bash
git add -A
git commit -m "<本轮变更摘要>"
git push
```

## 禁止事项

- 禁止在 commit message 中写无意义的 "update"、"fix"、"WIP"
- 禁止提交 node_modules、.idea、dist、target 等构建产物目录（通过 .gitignore 排除）
- 禁止在未完成当前任务时提前提交
- 禁止使用 `git push --force` 或 `git push --force-with-lease`