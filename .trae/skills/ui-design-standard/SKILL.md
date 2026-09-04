---
name: "ui-design-standard"
description: "Defines UI design rules for the HTML generator tool: design tokens, color palette, typography, spacing, component styles. Invoke when designing new pages/components or adjusting any visual style of the tool itself."
---

# UI 设计规范（ui-design-standard）

约束**生成器工具自身**的视觉风格，保证整体一致。生成器"导出给用户"的页面样式由组件模板自行决定，不受本文件颜色值约束（但结构约束见 html-output-standard）。

## 触发时机

- 新增/修改生成器界面的任何视觉样式
- 新增面板、按钮、表单等 UI 组件时
- 发觉页面风格不一致时

## 设计 Token（必须定义在 `css/style.css` 的 `:root` 中，所有样式只允许引用变量）

```css
:root {
  /* 主色：科技蓝，辅助青色 */
  --color-primary: #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-accent: #06b6d4;
  /* 中性色 */
  --color-bg: #f8fafc;
  --color-surface: #ffffff;
  --color-border: #e2e8f0;
  --color-text: #0f172a;
  --color-text-secondary: #64748b;
  /* 语义色 */
  --color-success: #16a34a;
  --color-warning: #d97706;
  --color-danger: #dc2626;
  /* 字体 */
  --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Microsoft YaHei", sans-serif;
  --font-mono: Consolas, Monaco, "Courier New", monospace;
  /* 间距（8px 网格） */
  --space-1: 4px;  --space-2: 8px;  --space-3: 16px;
  --space-4: 24px; --space-5: 32px; --space-6: 48px;
  /* 圆角与阴影 */
  --radius-sm: 4px; --radius-md: 8px; --radius-lg: 12px;
  --shadow-md: 0 2px 8px rgba(15, 23, 42, 0.08);
  --shadow-lg: 0 8px 24px rgba(15, 23, 42, 0.12);
}
```

## 布局与排版

- 生成器采用经典三栏布局：左侧组件库（约 240px）｜中间画布/预览（自适应）｜右侧属性面板（约 280px）
- 正文字号 14px，标题层级 20/16/14px；行高 ≥ 1.5
- 间距只用 token 中定义的档位，禁止随意写 `margin: 13px`

## 组件风格（统一规则）

- 基础控件（按钮/输入框/下拉/弹窗）优先使用 Element Plus，用 token 覆盖其主题变量；自定义组件同样遵循 token
- **按钮**：主按钮实心主色、次按钮白底描边、危险按钮红底；高度 36px，圆角 `--radius-md`
- **输入框/下拉**：白底、`--color-border` 描边，聚焦时主色描边 + 淡主色外发光
- **面板/卡片**：白底 `--color-surface`、圆角 `--radius-lg`、阴影 `--shadow-md`
- **可拖拽组件项**：hover 时边框变主色 + 轻微上浮（translateY(-1px)）

## 交互反馈

- 所有可点击元素必须有 hover/active 态和 `cursor: pointer`
- 耗时操作（导出、复制）必须有 loading 或成功 toast 反馈
- 过渡动画统一 150ms ease

## 禁止事项

- 禁止硬编码颜色值（必须走 token）
- 禁止混用多种圆角/阴影/字号档位
- 禁止使用衬线字体、艺术字体作为 UI 主字体
