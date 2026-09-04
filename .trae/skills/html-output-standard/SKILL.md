---
name: "html-output-standard"
description: "HTML 导出规范：导出产物必须单文件自包含、语义化标签、内联 CSS/JS、响应式。触发词：导出 HTML、复制代码、生成器输出、HTML 模板、export、单文件、语义标签、DOCTYPE。不触发：生成器自身 UI 样式、后端代码生成。"
---

# HTML 输出规范（html-output-standard）

本项目是一个**在线 HTML 生成工具**（用户在网页上可视化操作并导出 HTML 代码）。凡是生成、拼装或导出给用户的 HTML 代码，必须遵守本规范。

> 注意：生成器自身是 Vue 3 应用，但**导出产物必须是原生 HTML**，与 Vue、构建工具、后端完全无关，须能脱离一切环境独立运行。

## 触发时机

- 编写/修改 HTML 生成逻辑、模板、代码拼装函数时
- 修改「导出 HTML」「复制代码」功能时
- 为生成器新增可输出的元素/组件类型时

## 硬性规则

1. **单文件自包含**：导出的 HTML 必须能双击在浏览器中直接打开运行。
   - CSS 必须内联（`<style>` 标签），JS 必须内联（`<script>` 标签）
   - 禁止引用本地相对路径的外部 css/js 文件
   - 如需图片，使用稳定的在线 URL 或 Data URL，禁止引用用户本地路径
2. **完整文档结构**：必须包含 `<!DOCTYPE html>`、`<html lang>`、`<meta charset>`、`<meta name="viewport">`、`<title>`。
3. **语义化标签**：优先使用 `header / nav / main / section / article / footer / h1-h6`，禁止全 `div` 堆砌；每个页面有且仅有一个 `<h1>`。

## 代码质量要求

- 缩进统一 2 空格；属性使用双引号
- class 命名使用 kebab-case，见名知义（如 `hero-section`、`card-list`）
- 生成的代码必须是**格式化**的（用户导出后可直接阅读、二次编辑）
- 禁止生成内联 `style=""` 堆砌样式（除个别动态计算值），样式统一走 class
- 禁止生成 `document.write`、废弃标签（`<center>`、`<font>` 等）

## 响应式与可访问性（生成代码必须满足）

- 布局使用 Flex/Grid，移动端不横向溢出
- 图片必须有 `alt`；表单控件必须有关联 `<label>`
- 交互元素用 `<button>`/`<a>`，禁止用 div 模拟按钮

## 导出前校验（生成器逻辑需内置）

- HTML 标签正确闭合、无重复 id
- 无空 `<style>`/`<script>` 块、无 `undefined`/`null`/占位文本泄漏到输出
