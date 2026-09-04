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
3. **M3 项目域**：project-service 项目/页面 CRUD + 前端工作台
4. **M4 生成器域**：generator-service 组件定义 + 生成/导出 + 前端编辑器三栏
5. **M5 联调验收**：端到端流程 + 响应式 + 全量测试通过

## 10. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-09-04 | 初版，确立微服务架构与核心需求 |
