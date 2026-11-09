# WebCraft 前端

基于 Vue 3 + Vite + Element Plus 的在线 HTML 网站生成工具前端。

## 技术栈

- **Vue 3** (`<script setup>` SFC)
- **Vite** 构建工具
- **Vue Router** 路由管理
- **Element Plus** UI 组件库
- **Axios** HTTP 请求

## 项目结构

```
src/
├── api/              # API 请求封装（Axios 实例 + 拦截器）
│   ├── request.js    # 统一请求实例（baseURL、token 注入、Result 解包）
│   └── index.js      # 各业务 API 函数
├── router/
│   └── index.js      # 路由配置 + 登录守卫
├── views/
│   ├── Login.vue     # 登录页
│   ├── Register.vue  # 注册页
│   ├── Workspace.vue # 工作台（项目列表）
│   └── Editor.vue    # 编辑器（AI 生成 + 预览 + 本地运行）
├── App.vue           # 根组件
├── main.js           # 入口文件
└── style.css         # 全局样式
```

## 开发

```bash
npm install
npm run dev      # 启动开发服务器 → http://localhost:5173
npm run build    # 生产构建
npm run test     # 运行测试
```