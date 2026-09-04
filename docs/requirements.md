# 在线 HTML 生成工具 需求文档

> 本文档为项目唯一需求源。按 development-workflow 约束，任何新功能开发前必须先更新本文档；测试先行中若发现需求遗漏，必须反向回写本文档后再实现。

## 1. 项目背景与目标

构建一个**在线 HTML 生成工具**：用户在网页上通过可视化拖拽/表单操作组装页面，实时预览，并能导出**单文件自包含的原生 HTML**（脱离 Vue、后端、构建工具独立运行）。

核心目标：
- 可视化生成页面，降低手写 HTML 门槛
- 导出产物符合 html-output-standard，可直接部署
- 支持项目/页面持久化，用户可登录管理自己的作品

## 2. 总体架构（微服务）

采用微服务架构，4 个业务/基础设施服务 + Nacos 注册与配置中心。

```text
                     ┌──────────────────┐
   前端 Vue 3 ───────►│  gateway-service │  统一入口、鉴权、转发
                     └─────────┬────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌───────────────┐      ┌───────────────┐      ┌─────────────────┐
│  user-service │      │ project-service│      │ generator-service│
│ 用户认证授权  │      │ 项目/页面管理  │      │ 组件定义/生成/导出│
└───────┬───────┘      └───────┬───────┘      └────────┬────────┘
        │                      │                       │
        ▼                      ▼                       ▼
   user_db              project_db              generator_db
   (MySQL 8)             (MySQL 8)               (MySQL 8)
```

服务间通过 OpenFeign 调用；所有对外接口经 gateway 转发，统一返回 `Result<T>`。

## 3. 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3（`<script setup>`）、Vite、Vue Router、Pinia、Axios、Element Plus |
| 网关 | Spring Cloud Gateway、Nacos 服务发现 |
| 后端服务 | Java 17+、Spring Boot 3、Spring Cloud、Nacos Config、OpenFeign、Spring Validation |
| 数据访问 | MyBatis-Plus |
| 数据库 | MySQL 8（每服务独立库） |
| 鉴权 | JWT（user-service 签发，gateway 校验） |
| 通信 | RESTful JSON，统一 `Result` 封装（见 api-result-convention） |

## 4. 微服务清单与职责

### 4.1 gateway-service（API 网关）

- 端口 8080，对外唯一入口；路由前缀 `/api/v1/**` 转发到对应服务
- JWT 校验：白名单（登录/注册）外的请求必须携带有效 token
- 鉴权通过后把 `userId`、`username` 注入下游请求头 `X-User-Id` / `X-User-Name`
- 全局异常转换为 `Result`；限流（基础令牌桶，按用户）

### 4.2 user-service（用户认证授权）

- 端口 8081；独立库 `user_db`
- 注册/登录/登出/刷新 token
- 用户信息查询/修改
- 签发与校验 JWT

### 4.3 project-service（项目与页面管理）

- 端口 8082；独立库 `project_db`
- 项目 CRUD（一个项目包含多个页面）
- 页面 CRUD（页面元数据 + 页面组件树 JSON）
- 通过 OpenFeign 调 user-service 校验用户存在性

### 4.4 generator-service（组件定义与生成导出）

- 端口 8083；独立库 `generator_db`
- 内置组件库定义管理（标题/段落/按钮/图片/容器等）
- 组件 → HTML 的渲染拼装（核心业务）
- 导出单文件 HTML（下载）/ 复制代码（返回字符串）
- 通过 OpenFeign 调 project-service 读取页面组件树以生成完整页面

## 5. 核心功能需求（按模块）

### 5.1 [用户] 用户注册登录

- 背景/目标：用户可注册账号并登录，以便管理自己的项目
- 输入：用户名（3-20 字符）、密码（8-32 位，含字母与数字）
- 输出：JWT（accessToken + refreshToken）
- 验收标准：
  - [ ] 注册成功返回用户 id 与 token；用户名重复时返回 1001
  - [ ] 登录成功返回 token；密码错误返回 1002
  - [ ] 登录后 5 分钟内不操作，刷新 token 仍可用
  - [ ] 所有接口返回 `Result<T>` 封装
- 涉及接口：`POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`
- 涉及表：`user`（id, username, password_hash, created_at, updated_at）

### 5.2 [项目] 项目与页面管理

- 背景/目标：用户可创建项目并在项目下管理多个页面
- 输入：项目名（1-50 字符）、页面标题
- 输出：项目/页面元数据
- 验收标准：
  - [ ] 可创建/重命名/删除项目；删除项目时级联删除其下页面
  - [ ] 可在项目下创建/重命名/删除/排序页面
  - [ ] 仅项目所有者可操作；非所有者访问返回 403
  - [ ] 分页查询项目列表（默认每页 10 条）
- 涉及接口：`/api/v1/projects`、`/api/v1/projects/{pid}/pages`
- 涉及表：`project`、`page`、`page_component`（组件树 JSON 存于 page.component_tree）

### 5.3 [生成器] 组件库与 HTML 生成导出

- 背景/目标：提供内置组件，用户在画布组装后导出单文件 HTML
- 输入：页面组件树 JSON（组件类型 + 属性 + 嵌套结构）
- 输出：自包含 HTML 字符串 / 文件下载流
- 验收标准：
  - [ ] 内置组件至少含：标题(H1-H3)、段落、按钮、图片、容器、分栏
  - [ ] 组件支持属性：样式类、文案、链接、对齐等
  - [ ] 画布拖拽/点击添加组件；选中后右侧属性面板可编辑
  - [ ] 属性修改实时反映到画布预览
  - [ ] 导出 HTML 符合 html-output-standard：可双击直接打开渲染正确
  - [ ] 复制到剪贴板的代码与下载文件内容一致
  - [ ] 导出过程对 `undefined`/占位符有兜底，不泄漏到产物
- 涉及接口：`GET /api/v1/components`、`POST /api/v1/generate/preview`、`POST /api/v1/generate/export`、`GET /api/v1/projects/{pid}/pages/{pageId}/export`
- 涉及表：`component_def`（组件定义元数据）

### 5.4 [网关] 统一入口与鉴权

- 背景/目标：前端只对接网关，鉴权统一在网关完成
- 输入：携带 `Authorization: Bearer <token>` 的请求
- 输出：转发到下游服务，或返回 401 `Result`
- 验收标准：
  - [ ] 白名单接口（注册/登录/刷新）无需 token
  - [ ] 非 token 或 token 失效返回 401 `Result`（HTTP 仍 200）
  - [ ] 鉴权后下游可通过请求头 `X-User-Id` 获取用户 id
  - [ ] 下游服务异常被网关统一捕获转 `Result`，不暴露堆栈

## 6. 前端需求（生成器主界面）

- 三栏布局（见 ui-design-standard）：左侧组件库（240px）｜中间画布/预览（自适应）｜右侧属性面板（280px）
- 顶栏：项目切换、页面切换、预览/编辑切换、导出/复制按钮
- 所有请求走 `api/request.js` 统一实例（拦截器解包 Result）
- 路由：登录、注册、工作台（项目列表）、编辑器（项目 id + 页面 id）
- 响应式：1024px 以上三栏；更窄时右侧面板可折叠抽屉

## 7. 非功能性需求

- 性能：导出 HTML 在组件数 ≤ 50 时 < 1s
- 安全：密码 BCrypt 加盐存储；JWT HS256；SQL 参数化（MyBatis-Plus）
- 可用性：服务注册到 Nacos，单服务挂掉不影响其他服务
- 可维护：每服务独立库，独立部署；接口契约通过 OpenFeign 接口定义共享

## 8. 数据库设计概要

### user_db
```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### project_db
```sql
CREATE TABLE project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user (user_id)
);

CREATE TABLE page (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  sort_order INT DEFAULT 0,
  component_tree JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_project (project_id)
);
```

### generator_db
```sql
CREATE TABLE component_def (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(30) NOT NULL UNIQUE,
  display_name VARCHAR(50) NOT NULL,
  category VARCHAR(30) NOT NULL,
  default_props JSON,
  schema JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 9. 开发里程碑

1. **M1 基础设施**：Nacos + 4 服务骨架 + gateway 路由 + 统一 Result + 全局异常

### M1 详细需求（基础设施）

- 背景/目标：建立 Maven 多模块骨架，各服务可独立启动并注册到 Nacos，gateway 统一路由转发，所有响应走统一 Result 封装，异常被全局处理器捕获
- 输入：—（无业务输入，纯基础设施）
- 输出：服务启动后健康检查可用；gateway 路由转发正确；Result 封装与异常处理覆盖所有服务

验收标准（逐条对应测试用例）：
- [ ] M1-1: common 模块 `Result.success(data)` 返回 `{code:200, message:"success", data:...}`；`Result.fail(code, msg)` 返回 `{code:xxx, message:"...", data:null}`；序列化后 JSON 不含多余字段
- [ ] M1-2: `BusinessException` 被全局 `@RestControllerAdvice` 捕获并转换为 `Result`（HTTP 200）
- [ ] M1-3: 参数校验失败（`@Valid` + DTO 注解）返回 code=400 + 可读 message，HTTP 200
- [ ] M1-4: 4 个服务各自启动成功并注册到 Nacos（服务名见 project-conventions）
- [ ] M1-5: gateway 路由 `/api/v1/auth/**` → user-service、`/api/v1/projects/**` → project-service、`/api/v1/generator/**` → generator-service 转发正常
- [ ] M1-6: 下游服务不可用时 gateway 返回 code=5003 `Result` 降级响应，不暴露堆栈
- [ ] M1-7: 每个服务的健康检查接口 `GET /actuator/health` 返回 HTTP 200
- 涉及服务：全部 4 服务 + common 模块 + api-contract 模块

2. **M2 用户域**：user-service 注册登录 + JWT + 网关鉴权

### M2 详细需求（用户认证与鉴权）

- 背景/目标：用户可注册账号并登录获取 JWT；gateway 统一校验 JWT 后把 userId 注入下游请求头；支持 token 刷新
- 输入：用户名（3-20 字符）、密码（8-32 位，必须含字母和数字）
- 输出：JWT（accessToken 有效期 2h + refreshToken 有效期 7d）

验收标准（逐条对应测试用例）：
- [ ] M2-1: 注册成功返回 `Result<UserVO>`，含 id、username；用户名重复返回 code=1001
- [ ] M2-2: 登录成功返回 `Result<TokenVO>`，含 accessToken、refreshToken；密码错误返回 code=1002
- [ ] M2-3: 密码 BCrypt 加密存储，数据库中不存明文
- [ ] M2-4: 刷新 token：用有效 refreshToken 换取新 accessToken + refreshToken
- [ ] M2-5: gateway 对 `/api/v1/auth/**`（注册/登录/刷新）放行，不校验 token
- [ ] M2-6: gateway 对其他路径无 token 或 token 过期/无效返回 `Result(401, "未登录")`，HTTP 200
- [ ] M2-7: gateway 鉴权后把 userId、username 注入 `X-User-Id`、`X-User-Name` 请求头转发给下游
- [ ] M2-8: 参数校验：用户名/密码不合规返回 code=400 + 可读 message
- 涉及接口：`POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`
- 涉及表：`user`（id, username, password_hash, created_at, updated_at）
- 涉及服务：user-service、gateway-service、common

3. **M3 项目域**：project-service 项目/页面 CRUD + 前端工作台

### M3 详细需求（项目与页面管理）

- 背景/目标：用户可创建项目，在项目下管理多个页面；前端工作台展示项目列表，支持进入编辑器
- 输入：项目名（1-50 字符）、页面标题（1-100 字符）
- 输出：项目/页面元数据

验收标准（逐条对应测试用例）：
- [ ] M3-1: 创建项目成功返回 `Result<ProjectVO>`；项目名重复不限制（同一用户不同项目可同名）
- [ ] M3-2: 分页查询项目列表（`GET /api/v1/projects?page=1&size=10`），默认按创建时间倒序，返回 `Result<PageVO<ProjectVO>>`
- [ ] M3-3: 重命名项目成功返回 `Result<ProjectVO>`；项目不存在返回 code=2001
- [ ] M3-4: 删除项目成功返回 `Result<null>`；删除项目时级联删除其下所有页面；项目不存在返回 code=2001
- [ ] M3-5: 在项目下创建页面成功返回 `Result<PageVO>`；项目不存在返回 code=2001
- [ ] M3-6: 分页查询项目下页面列表（`GET /api/v1/projects/{pid}/pages?page=1&size=20`），默认按 sort_order 升序，返回 `Result<PageVO<PageVO>>`
- [ ] M3-7: 重命名页面成功返回 `Result<PageVO>`；页面不存在返回 code=2002
- [ ] M3-8: 删除页面成功返回 `Result<null>`；页面不存在返回 code=2002
- [ ] M3-9: 仅项目所有者可操作项目及其页面；通过 `X-User-Id` 请求头校验；非所有者返回 code=403
- [ ] M3-10: 参数校验：项目名/页面标题为空或超长返回 code=400 + 可读 message
- [ ] M3-11: 前端工作台：项目列表展示（名称、页面数、更新时间），支持创建/重命名/删除项目，点击进入编辑器
- [ ] M3-12: 前端编辑器骨架：顶栏（项目名 + 页面切换 + 导出按钮）+ 三栏布局占位
- 涉及接口：`POST /api/v1/projects`、`GET /api/v1/projects`、`PUT /api/v1/projects/{id}`、`DELETE /api/v1/projects/{id}`、`POST /api/v1/projects/{pid}/pages`、`GET /api/v1/projects/{pid}/pages`、`PUT /api/v1/projects/{pid}/pages/{pageId}`、`DELETE /api/v1/projects/{pid}/pages/{pageId}`
- 涉及表：`project`（id, user_id, name, created_at, updated_at）、`page`（id, project_id, title, sort_order, component_tree, created_at, updated_at）
- 涉及服务：project-service、gateway-service
- 前端页面：`/workspace`（项目列表）、`/editor/:projectId/:pageId`（编辑器骨架）
4. **M4 生成器域**：AI Agent 驱动的网站生成引擎（DeepSeek + 工具调用）

### M4 详细需求（AI 生成器域）

- **背景/目标**：基于 DeepSeek-v4-flash 大模型 + LangChain4j Agent 框架，实现 AI 驱动的网站生成引擎。用户描述需求后，Agent 自动规划网站结构、生成 HTML/CSS/JS 代码、设计配套数据库，并打包为可直接运行的项目。
- **核心架构**：generator-service 内嵌 AI Agent，通过工具调用（Tool Calling）完成生成任务
- **输入**：用户需求描述（自然语言，如"生成一个博客网站，有文章列表和详情页"）
- **输出**：完整的可运行项目目录（含前端页面 + 后端 API + 数据库 + 启动脚本）

验收标准（逐条对应测试用例）：

**模型层：**
- [ ] M4-1: generator-service 集成 LangChain4j 框架，通过 `OpenAiChatModel` + `OpenAiStreamingChatModel`（OpenAI 兼容接口）调用 DeepSeek-v4-flash 自定义模型供应商
- [ ] M4-1a: `DeepSeekChatModel` 支持非流式调用（`chat()`，maxTokens=4096，用于短输出如规划 JSON）和流式调用（`streamChat()`，maxTokens=16384，用于长输出如 HTML/CSS/JS/后端代码）
- [ ] M4-1b: 流式调用通过 `StreamingResponseHandler` + `CountDownLatch` 同步等待完整输出，超时 300 秒，防止输出截断

**Agent 工具层：**
- [ ] M4-2: 定义 Agent 工具集：`FileSystemTool`（创建目录/文件）、`DatabaseSchemaTool`（生成 SQL 建表 + 种子数据）、`WebContentTool`（生成 HTML/CSS/JS 代码）、`PackageTool`（打包项目 + 生成启动脚本）
- [ ] M4-2a: 工具调用异常时返回友好错误信息（如 `ERROR: ...`），不暴露内部堆栈

**Prompt 约束层（核心要求）：**
- [ ] M4-3a: 每个 AI 调用使用的 Prompt 必须严格限定输出格式，明确标注"只输出 XXX，不要输出任何解释、说明、markdown 标记或代码块标记"
- [ ] M4-3b: 规划阶段 Prompt 要求输出严格 JSON 结构（siteName/pages/tables/features），禁止模型输出无关内容
- [ ] M4-3c: 前端代码生成 Prompt 要求用 `---FILE:` 分隔符输出每个文件，每个文件必须完整、不得使用省略号或注释占位符
- [ ] M4-3d: 数据库生成 Prompt 要求输出完整 SQL 脚本（建表 + 种子数据），不得截断
- [ ] M4-3e: 后端代码生成 Prompt 要求输出完整 server.js（Express + better-sqlite3 CRUD），参数化查询，不得截断

**生成编排层：**
- [ ] M4-3: Agent 接收用户需求后，自动规划网站结构并调用工具依次生成：目录结构 → 前端页面 → 数据库 → 后端 API → 打包配置
- [ ] M4-3f: 规划阶段使用非流式调用（输出短 JSON），代码生成阶段使用流式调用（maxTokens=16384），确保长代码不截断
- [ ] M4-3g: 每一步生成前通过 SSE 推送进度消息（如 `[规划] AI 正在分析需求...`），前端实时展示

**生成质量：**
- [ ] M4-4: 生成的网站前端包含至少 3 个页面（首页、列表页、详情页），响应式布局（Flexbox/Grid），现代 CSS 设计
- [ ] M4-5: 生成的网站配套 SQLite 数据库，包含至少 2 张关联表（外键） + 每表至少 3 条种子数据；生成对应的 Node.js Express 后端 API（完整 CRUD）
- [ ] M4-5a: 后端 API 使用 better-sqlite3 参数化查询（`db.prepare().run()`），返回 `{ success: true, data: ... }` JSON 格式
- [ ] M4-6: 生成的项目可直接运行：`npm install && npm start` 启动后浏览器可访问完整网站

**前端交互：**
- [ ] M4-7: 生成过程支持流式输出进度（SSE），前端通过 `EventSource` 实时展示生成日志，最大连接时间 10 分钟
- [ ] M4-8: 生成的项目目录通过 zt-zip 打包为 ZIP 压缩包，通过 `GET /api/v1/generator/download/{taskId}` 流式下载
- [ ] M4-9: 前端编辑器集成 AI 生成面板：用户输入需求 → 点击生成 → 深色终端风格进度日志 → 完成后下载按钮

**异常处理：**
- [ ] M4-10: Agent 工具调用异常时返回友好错误信息，不暴露内部堆栈；流式调用超时（300s）返回明确错误提示
- [ ] M4-11: 参数校验：需求描述为空时返回 code=400 + 可读 message
- [ ] M4-11a: 生成结果不存在或已被清理时，下载接口返回 code=2001 + 友好提示

- 涉及接口：`POST /api/v1/generator/generate`（触发生成）、`GET /api/v1/generator/progress/{taskId}`（SSE 进度）、`GET /api/v1/generator/download/{taskId}`（下载 ZIP）、`GET /api/v1/generator/tasks/{taskId}`（查询状态）
- 涉及表：`generation_task`（id, user_id, prompt, status, output_path, error_message, created_at, updated_at）
- 涉及服务：generator-service、gateway-service
- 前端页面：`/editor/:projectId/:pageId`（编辑器集成 AI 生成面板）

5. **M5 联调验收**：端到端流程 + 响应式 + 全量测试通过

## 10. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-09-04 | 初版，确立微服务架构与核心需求 |
| 2026-09-04 | M4 更新：AI Agent 驱动的网站生成引擎（DeepSeek + LangChain4j + 工具调用） |
| 2026-09-04 | M4 增强：流式传输（streaming）防止输出截断、maxTokens=16384、严格 Prompt 约束、4 层验收标准拆分 |
