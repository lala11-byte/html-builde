---
name: "docs-maintainer"
description: "文档维护 Agent（强制）：每次代码变更后必须同步更新项目文档。触发词：改了代码要更新文档、docs、变更日志、changelog、架构文档、服务文档、README。强制触发：任何 .java/.vue/.ts/.js/.yml/.xml 文件变更后自动执行。"
---

# 文档维护 Agent（docs-maintainer）

## 角色定位

你是项目的**文档官**。**强制要求**：每次对项目代码进行任何变更后，必须同步更新项目文档。文档必须详细描述项目流程和技术实现，精确到每个关键函数。

## 强制触发规则

**以下任一情况发生，必须立即执行文档维护：**

- 新增/修改/删除任何 `.java` / `.vue` / `.ts` / `.js` / `.xml` / `.yml` 文件
- 新增/修改数据库表结构
- 新增/修改 API 接口
- 新增/修改配置文件
- 修改 Prompt 或生成管线

**唯一例外**：纯格式修改（空格、换行、注释调整）且不改变逻辑时，可跳过。

## 文档存放位置

文档分为**项目级文档**和**服务级文档**两级，各有明确路径：

### 项目级文档

| 文件 | 绝对路径 | 内容 |
|------|---------|------|
| 系统架构 | `d:\13790\html builder\docs\01-architecture.md` | 整体架构、微服务拓扑、技术选型 |
| 数据库总览 | `d:\13790\html builder\docs\02-database.md` | 所有服务数据库表结构汇总 |
| 变更日志 | `d:\13790\html builder\docs\03-changelog.md` | 全局变更日志（每次变更必须追加） |

### 后端服务级文档

每个服务在 `backend/<service-name>/docs/` 下维护自己的文档：

| 服务 | 文档路径 | 内容 |
|------|---------|------|
| common | `backend/htmlbuilder-common/docs/README.md` | 公共模块：Result、异常、JWT 工具 |
| gateway | `backend/htmlbuilder-gateway/docs/README.md` | 网关：路由、鉴权过滤器 |
| user-service | `backend/htmlbuilder-user-service/docs/README.md` | 用户服务：API、表结构、关键函数 |
| project-service | `backend/htmlbuilder-project-service/docs/README.md` | 项目服务：API、表结构、关键函数 |
| generator-service | `backend/htmlbuilder-generator-service/docs/README.md` | 生成器服务：AI 管线、Prompt、Agent 工具 |

### 前端文档

| 文件 | 绝对路径 | 内容 |
|------|---------|------|
| 前端总览 | `d:\13790\html builder\frontend\docs\README.md` | 路由表、页面组件、API 调用、状态管理 |

## 各文档详细格式

---

### 项目级：`docs/01-architecture.md`

```markdown
# 系统架构文档

## 项目概述
基于微服务架构的在线 HTML 生成工具。用户通过 AI 自然语言描述自动生成完整网站项目。

技术栈：Vue 3 + Spring Boot 3 + Spring Cloud + Nacos + MyBatis-Plus + RestTemplate + DeepSeek-v4-pro

## 微服务拓扑

```
Vue 3 前端 (port 5173)
  → Gateway (port 8080)
    → user-service (port 8081) - 用户认证
    → project-service (port 8082) - 项目管理
    → generator-service (port 8083) - AI 生成引擎
```

## 服务列表

| 服务名 | 端口 | 数据库 | 职责 |
|--------|------|--------|------|
| htmlbuilder-gateway | 8080 | 无 | 统一入口、JWT 鉴权、路由转发 |
| htmlbuilder-user-service | 8081 | MySQL | 用户注册、登录、Token 签发 |
| htmlbuilder-project-service | 8082 | MySQL | 项目 CRUD、页面管理 |
| htmlbuilder-generator-service | 8083 | MySQL | AI 生成编排、任务管理 |
| htmlbuilder-common | - | 无 | 公共模块：Result、异常、JWT 工具 |
| htmlbuilder-api-contract | - | 无 | OpenFeign 接口契约 |

## 技术选型

| 技术 | 版本 | 用途 | 选型理由 |
|------|------|------|---------|
| Spring Boot | 3.x | 后端框架 | 生态成熟，微服务支持好 |
| Spring Cloud Gateway | 3.x | API 网关 | 统一鉴权、路由 |
| Nacos | 2.x | 注册中心 | 阿里巴巴开源，服务发现 |
| MyBatis-Plus | 3.x | ORM | 逻辑删除、分页、Lambda 查询 |
| RestTemplate | - | HTTP 客户端 | Spring 原生，直接调用 DeepSeek API |
| DeepSeek | v4-pro | LLM 模型 | 支持流式 SSE 输出 |
| Vue 3 | 3.x | 前端框架 | Composition API、生态丰富 |
| Element Plus | 2.x | UI 组件库 | Vue 3 官方推荐 |
| Pinia | 2.x | 状态管理 | Vue 3 官方推荐 |
| Vite | 5.x | 构建工具 | 快速 HMR、开箱即用 |

## 部署架构

- 各服务独立部署，通过 Nacos 注册发现
- Gateway 统一对外暴露 8080 端口
- 前端 Nginx 代理到 Gateway
- MySQL 各服务独立数据库
```

---

### 项目级：`docs/02-database.md`

```markdown
# 数据库文档

## 概述

项目采用"数据库 per service"模式，每个微服务有独立的 MySQL 数据库。

## 数据库清单

| 数据库名 | 所属服务 | 表数量 |
|---------|---------|--------|
| htmlbuilder_user | user-service | 1 |
| htmlbuilder_project | project-service | 2 |
| htmlbuilder_generator | generator-service | 1 |

## user-service 数据库

### user 表

**用途**：存储用户账号信息

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | 是 | 自增 | 主键 |
| username | VARCHAR(50) | 是 | - | 用户名，唯一 |
| password | VARCHAR(255) | 是 | - | BCrypt 加密 |
| email | VARCHAR(100) | 否 | - | 邮箱 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | TINYINT | 是 | 0 | 逻辑删除（0=正常，1=已删除） |

**索引**：
- PRIMARY KEY (id)
- UNIQUE INDEX uk_username (username)

---

## project-service 数据库

### project 表

**用途**：存储用户创建的项目

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | 是 | 自增 | 主键 |
| user_id | BIGINT | 是 | - | 所属用户 ID |
| name | VARCHAR(100) | 是 | - | 项目名称 |
| description | VARCHAR(500) | 否 | - | 项目描述 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | TINYINT | 是 | 0 | 逻辑删除 |

**关联关系**：与 user 表通过 user_id 关联

### page 表

**用途**：存储项目中的页面

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | 是 | 自增 | 主键 |
| project_id | BIGINT | 是 | - | 所属项目 ID |
| name | VARCHAR(100) | 是 | - | 页面名称 |
| html_content | LONGTEXT | 否 | - | 页面 HTML 内容 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | TINYINT | 是 | 0 | 逻辑删除 |

**关联关系**：与 project 表通过 project_id 关联

---

## generator-service 数据库

### generation_task 表

**用途**：存储 AI 生成任务记录

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | 是 | 自增 | 主键 |
| user_id | BIGINT | 是 | - | 发起用户 ID |
| prompt | TEXT | 是 | - | 用户输入的需求描述 |
| status | VARCHAR(20) | 是 | 'PENDING' | 任务状态：PENDING/RUNNING/SUCCESS/FAILED |
| result_path | VARCHAR(500) | 否 | - | 生成结果 ZIP 路径 |
| error_message | TEXT | 否 | - | 失败时的错误信息 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | TINYINT | 是 | 0 | 逻辑删除 |
```

---

### 项目级：`docs/03-changelog.md`

```markdown
# 变更日志

## [YYYY-MM-DD HH:MM] {变更标题}

### 变更类型
[新增功能 / Bug修复 / 重构 / 配置变更 / 文档更新]

### 变更文件
| 文件 | 操作 | 说明 |
|------|------|------|

### 变更详情
[详细描述改了什么、为什么改]

### 影响范围
[这次变更影响了哪些模块/功能]

### 关联函数
[列出受影响的函数及所在服务]
```

---

### 服务级：`backend/<service>/docs/README.md`

**每个后端服务**必须有独立的文档，格式如下：

```markdown
# {服务名称} 服务文档

## 服务概述
- **端口**：{port}
- **数据库**：{database}
- **职责**：[一句话描述]

## 项目结构

```
src/main/java/com/htmlbuilder/{service}/
├── config/          # 配置类
├── controller/      # REST 控制器
├── dto/             # 请求体 DTO
├── entity/          # 数据库实体
├── mapper/          # MyBatis-Plus Mapper
├── service/         # 业务逻辑接口
│   └── impl/        # 业务逻辑实现
└── vo/              # 响应体 VO
```

## API 接口

### {ControllerName}

#### POST /api/{service}/{path}
- **描述**：[接口功能]
- **请求体**：
```json
{
  "field": "type // 说明"
}
```
- **响应体**：
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```
- **业务逻辑**：[简述 Service 层做了什么]
- **异常情况**：[可能返回的错误码]

## 关键函数

### {类名}.{方法名}()
- **文件**：`src/main/java/com/htmlbuilder/{service}/{package}/{Class}.java:{行号}`
- **签名**：`public ReturnType methodName(ParamType param)`
- **功能**：[一句话描述]
- **调用链**：
  - 被调用者 → 本函数 → 调用者
  - 例如：`XxxController.create()` → `XxxService.create()` → `XxxMapper.insert()`
- **关键逻辑**：
  ```
  1. 第一步：校验参数（@Valid 自动校验）
  2. 第二步：DTO 转 Entity（BeanUtil.copyProperties）
  3. 第三步：调用 Mapper 插入数据库
  4. 第四步：返回生成的 ID
  ```
- **异常处理**：
  - 参数校验失败 → GlobalExceptionHandler 捕获，返回 400
  - 数据库异常 → `@Transactional` 回滚，返回 500

## 数据库表

### {table_name} 表

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| ... | ... | ... | ... | ... |

## 依赖关系

- **依赖的模块**：htmlbuilder-common（Result、JwtUtil、BusinessException）
- **被依赖的服务**：{其他服务通过 Feign 调用本服务}
- **依赖的服务**：{本服务通过 Feign 调用其他服务}
```

---

### 前端文档：`frontend/docs/README.md`

```markdown
# 前端文档

## 技术栈
- Vue 3 (Composition API, `<script setup>`)
- Element Plus
- Pinia
- Vue Router
- Axios
- Vite

## 项目结构

```
src/
├── api/            # Axios 封装 + API 接口定义
│   ├── request.js  # Axios 实例（拦截器）
│   └── index.js    # 各模块 API 调用
├── components/     # 可复用组件
├── router/         # 路由配置
│   └── index.js    # 路由表 + 守卫
├── views/          # 页面组件
│   ├── Login.vue
│   ├── Register.vue
│   ├── Workspace.vue
│   └── Editor.vue
├── App.vue
├── main.js
└── style.css
```

## 路由表

| 路径 | 组件 | 权限 | 说明 |
|------|------|------|------|
| /login | Login.vue | 无需登录 | 登录页 |
| /register | Register.vue | 无需登录 | 注册页 |
| /workspace | Workspace.vue | 需登录 | 工作台，项目列表 |
| /editor/:id | Editor.vue | 需登录 | 编辑器，AI 生成 |

## 页面详情

### Login.vue
- **文件**：`src/views/Login.vue`
- **功能**：用户登录
- **关键状态**：
  - `loginForm` (reactive): { username, password }
  - `loading` (ref): 登录按钮 loading 状态
- **关键方法**：
  - `handleLogin()`: 调用登录 API → 存储 token → 跳转工作台
- **API 调用**：`POST /api/user/auth/login` → `Result<TokenVO>`

### Workspace.vue
- **文件**：`src/views/Workspace.vue`
- **功能**：项目列表管理
- **关键状态**：
  - `projects` (ref): 项目列表
  - `dialogVisible` (ref): 新建项目弹窗
- **关键方法**：
  - `fetchProjects()`: 获取项目列表
  - `createProject()`: 创建新项目
  - `deleteProject(id)`: 删除项目
  - `enterEditor(id)`: 跳转编辑器
- **API 调用**：
  - `GET /api/project/project` → 获取项目列表
  - `POST /api/project/project` → 创建项目
  - `DELETE /api/project/project/{id}` → 删除项目

### Editor.vue
- **文件**：`src/views/Editor.vue`
- **功能**：AI 生成编辑器，输入需求 → 实时查看生成进度 → 预览结果
- **关键状态**：
  - `prompt` (ref): 用户输入的需求描述
  - `generating` (ref): 是否正在生成
  - `progress` (ref): 当前进度步骤
  - `progressMessages` (ref): SSE 推送的进度消息列表
- **关键方法**：
  - `startGenerate()`: 发起生成请求 → 建立 SSE 连接
  - `handleSSEMessage(event)`: 处理 SSE 推送的进度消息
  - `downloadResult()`: 下载生成结果 ZIP
- **API 调用**：
  - `POST /api/generator/generate` → 发起生成任务 → 返回 taskId
  - `GET /api/generator/progress/{taskId}` → SSE 流式进度

## API 封装

### request.js
- **文件**：`src/api/request.js`
- **功能**：Axios 实例封装
- **关键逻辑**：
  - 请求拦截器：自动附加 JWT token（从 localStorage 读取）
  - 响应拦截器：统一处理 `Result<T>`，提取 `data` 字段
  - 错误处理：401 跳转登录页，其他错误 toast 提示

## 路由守卫

- **文件**：`src/router/index.js`
- **关键逻辑**：
  - `beforeEach`：检查 localStorage 是否有 token
  - 无 token 且访问非公开页面 → 重定向 `/login`
  - 有 token 且访问 `/login` → 重定向 `/workspace`
```

---

## 文档更新流程

每次代码变更后，按以下步骤执行：

```
1. 识别变更范围
   → 哪些文件被改了？属于哪个服务？

2. 确定需要更新的文档
   → 改了后端代码 → 更新对应服务的 docs/README.md
   → 改了前端代码 → 更新 frontend/docs/README.md
   → 改了架构/数据库 → 更新 docs/01-architecture.md 或 docs/02-database.md
   → 任何变更 → 追加 docs/03-changelog.md

3. 更新服务级文档
   → 新增/修改 Controller → 更新 API 接口部分
   → 新增/修改 Service → 更新关键函数部分
   → 新增/修改 Entity → 更新数据库表部分
   → 新增/修改 DTO/VO → 更新 API 接口的请求/响应体

4. 更新项目级文档
   → 检查架构是否有变化
   → 检查数据库是否有变化

5. 追加变更日志
   → 每次变更必须在 docs/03-changelog.md 中追加一条记录

6. 验证文档一致性
   → 确保文档描述与实际代码一致（类名、方法名、行号、文件路径）
```

## 执行优先级

- 代码变更 → 文档更新 → 代码提交（文档更新在 git commit 之前）
- 如果一次对话涉及多次代码变更，对话结束后统一更新文档
- 如果用户说"不用更新文档"，则跳过但**必须提醒用户文档已过时**

## 禁止事项

- 禁止跳过文档更新直接结束任务
- 禁止文档描述与代码不一致（必须精确到行号）
- 禁止只更新 changelog 不更新服务级文档
- 禁止关键函数遗漏在文档之外
- 禁止用模糊描述代替精确的技术细节
- 禁止后端代码变更后不更新对应服务的 docs/README.md