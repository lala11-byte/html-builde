---
name: "project-architecture"
description: "项目架构文档：微服务拓扑、4 服务详细架构、调用链、数据流、数据库设计。触发词：架构图、微服务、调用链、数据流、项目结构总览、onboarding、跨服务修改、理解项目全貌。不触发：写代码（用对应 dev-agent）、具体规范（用 project-conventions）。"
---

# 项目架构文档（project-architecture）

## 1. 项目概述

**在线 HTML 生成工具** —— 微服务架构，前后端分离，AI 驱动的网站生成引擎。

## 2. 总体架构图

```text
                           ┌──────────────────────┐
        Vue 3 前端 ─────────►│  gateway-service     │ 端口 8080
                           │  (统一入口、鉴权、转发) │
                           └──────────┬───────────┘
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           ▼                          ▼                          ▼
   ┌───────────────┐        ┌───────────────┐        ┌──────────────────┐
   │  user-service │        │project-service│        │generator-service │
   │   端口 8081   │        │   端口 8082   │        │   端口 8083      │
   │   user_db     │        │  project_db   │        │  generator_db    │
   └───────────────┘        └───────────────┘        └──────────────────┘
           │                          │                          │
           └──────────────────────────┴──────────────────────────┘
                                      │
                               Nacos (127.0.0.1:8848)
                              注册中心 + 配置中心
```

## 3. 微服务详细架构

### 3.1 gateway-service（端口 8080）

**职责**：对外唯一入口、JWT 鉴权、路由转发、限流

**调用链**：
```
HTTP Request (前端)
    → JwtAuthFilter (白名单放行 / JWT 校验)
        ├── 白名单路径：/api/v1/auth/**、/api/v1/generator/progress/**、/api/v1/generator/download/**
        ├── 校验通过 → 注入 X-User-Id / X-User-Name 请求头 → 转发到下游
        └── 校验失败 → 返回 Result(401, "未登录")，HTTP 200
    → 路由转发（Nacos 服务发现）
        ├── /api/v1/auth/** → user-service
        ├── /api/v1/projects/** → project-service
        ├── /api/v1/generator/** → generator-service
        └── 下游不可用 → 返回 Result(5003, "服务暂不可用") 降级响应
    → GlobalExceptionHandler → 统一捕获异常 → Result
```

**关键组件**：
| 组件 | 路径 | 作用 |
|------|------|------|
| JwtAuthFilter | `gateway/filter/JwtAuthFilter.java` | JWT 校验 + 白名单 |
| RouteConfig | `gateway/config/RouteConfig.java` | 路由规则定义 |
| GatewayApplication | `gateway/GatewayServiceApplication.java` | 启动类 |

### 3.2 user-service（端口 8081）

**职责**：用户注册/登录、JWT 签发/刷新、用户信息管理

**模块结构**：
```
htmlbuilder-user-service/
├── src/main/java/com/htmlbuilder/user/
│   ├── UserServiceApplication.java    # 启动类
│   ├── controller/
│   │   └── AuthController.java        # 注册/登录/刷新
│   ├── service/
│   │   ├── AuthService.java           # 接口
│   │   └── impl/
│   │       └── AuthServiceImpl.java   # 业务逻辑
│   ├── entity/
│   │   └── User.java                  # 用户实体
│   ├── mapper/
│   │   └── UserMapper.java            # MyBatis-Plus Mapper
│   ├── dto/
│   │   ├── RegisterRequest.java       # 注册入参
│   │   └── LoginRequest.java          # 登录入参
│   └── vo/
│       ├── UserVO.java                # 用户出参
│       └── TokenVO.java               # Token 出参
├── src/main/resources/
│   ├── application.yml                # 配置
│   └── db/init.sql                    # 数据库建表脚本
└── src/test/java/                     # 测试
```

**调用链**：
```
AuthController
    ├── POST /auth/register → authService.register(dto)
    │       → 校验用户名唯一性 → BCrypt 加密密码 → 保存User → 签发JWT → 返回TokenVO
    ├── POST /auth/login → authService.login(dto)
    │       → 查询User → BCrypt 验证密码 → 签发JWT → 返回TokenVO
    └── POST /auth/refresh → authService.refresh(token)
            → 验证refreshToken → 签发新token对 → 返回TokenVO
```

**核心依赖**：
- `common` 模块：`Result<T>`、`BusinessException`、`JwtUtil`
- 独立 MySQL 库：`user_db`

### 3.3 project-service（端口 8082）

**职责**：项目 CRUD、页面 CRUD、组件树 JSON 存储

**模块结构**：
```
htmlbuilder-project-service/
├── src/main/java/com/htmlbuilder/project/
│   ├── ProjectServiceApplication.java # 启动类
│   ├── controller/
│   │   └── ProjectController.java     # 项目/页面 CRUD
│   ├── service/
│   │   ├── ProjectService.java        # 接口
│   │   └── impl/
│   │       └── ProjectServiceImpl.java
│   ├── entity/
│   │   ├── Project.java               # @TableName("project")
│   │   └── Page.java                  # @TableName("page")
│   ├── mapper/
│   │   ├── ProjectMapper.java         # @Mapper extends BaseMapper
│   │   └── PageMapper.java            # @Mapper extends BaseMapper
│   ├── dto/
│   │   ├── CreateProjectDTO.java
│   │   ├── UpdateProjectDTO.java
│   │   ├── CreatePageDTO.java
│   │   └── UpdatePageDTO.java
│   ├── vo/
│   │   ├── ProjectVO.java
│   │   └── PageVO.java
│   └── config/
│       └── MyBatisPlusConfig.java     # 分页插件
├── src/main/resources/
│   ├── application.yml
│   └── db/init.sql                    # project + page 建表
└── src/test/java/
    ├── ProjectServiceTest.java        # Service 单测
    └── ProjectControllerTest.java     # Controller 接口测试
```

**调用链**：
```
ProjectController
    ├── POST /projects → projectService.createProject(userId, dto)
    │       → 创建 Project 实体 → projectMapper.insert() → 返回 ProjectVO
    ├── GET /projects → projectService.listProjects(userId, page, size)
    │       → projectMapper.selectPage(page, wrapper) → 返回 PageVO<ProjectVO>
    ├── PUT /projects/{id} → projectService.updateProject(userId, id, dto)
    │       → 校验归属 → projectMapper.updateById() → 返回 ProjectVO
    ├── DELETE /projects/{id} → projectService.deleteProject(userId, id)
    │       → 校验归属 → pageMapper.deleteByProjectId() → projectMapper.deleteById()
    ├── POST /projects/{pid}/pages → projectService.createPage(pid, dto)
    │       → 创建 Page 实体 → pageMapper.insert() → 返回 PageVO
    ├── GET /projects/{pid}/pages → projectService.listPages(pid, page, size)
    │       → pageMapper.selectPage(page, wrapper) → 返回 PageVO<PageVO>
    ├── PUT /projects/{pid}/pages/{pageId} → projectService.updatePage(pid, pageId, dto)
    │       → 校验归属 → pageMapper.updateById() → 返回 PageVO
    └── DELETE /projects/{pid}/pages/{pageId} → projectService.deletePage(pid, pageId)
            → 校验归属 → pageMapper.deleteById()
```

**关联删除**：删除项目时级联删除其下所有页面（`PageMapper.deleteByProjectId()`）

### 3.4 generator-service（端口 8083）

**职责**：AI Agent 驱动的网站生成引擎（核心功能）

**模块结构**：
```
htmlbuilder-generator-service/
├── src/main/java/com/htmlbuilder/generator/
│   ├── GeneratorServiceApplication.java  # 启动类
│   ├── controller/
│   │   └── GeneratorController.java      # 4个接口
│   ├── service/
│   │   └── GeneratorService.java         # 任务管理 + SSE 推送
│   ├── agent/
│   │   ├── DeepSeekChatModel.java        # AI 模型供应商（双模式）
│   │   ├── AgentOrchestrator.java        # 6步编排器
│   │   └── tool/
│   │       ├── FileSystemTool.java       # 文件系统操作
│   │       ├── DatabaseSchemaTool.java   # SQL 构建
│   │       ├── WebContentTool.java       # HTML/CSS/JS 构建
│   │       └── PackageTool.java          # 项目打包配置
│   ├── entity/
│   │   └── GenerationTask.java           # @TableName("generation_task")
│   ├── mapper/
│   │   └── GenerationTaskMapper.java     # @Mapper extends BaseMapper
│   ├── dto/
│   │   └── GenerateRequest.java          # 入参：prompt
│   ├── vo/
│   │   └── TaskVO.java                   # 出参：id/status/progress/downloadUrl
│   └── config/
│       ├── AgentConfig.java              # DeepSeek API 配置
│       └── MyBatisPlusConfig.java        # 分页插件
├── src/main/resources/
│   ├── application.yml                   # 配置 + DeepSeek API Key
│   └── db/init.sql                       # generation_task 建表
└── src/test/java/
    └── GeneratorServiceApplicationTest.java  # 上下文加载测试
```

**完整调用链**：
```
前端 Editor.vue
    │
    ├── submitGenerate(prompt) → POST /api/v1/generator/generate
    │       → GeneratorController.generate()
    │           → generatorService.submitTask(userId, prompt)
    │               → 创建 GenerationTask(status=PENDING) → taskMapper.insert()
    │               → 返回 taskId
    │
    ├── EventSource → GET /api/v1/generator/progress/{taskId}
    │       → GeneratorController.progress()
    │           → generatorService.executeTaskAsync(taskId, emitter)
    │               → 线程池异步执行：
    │                   ├── taskMapper.selectById(taskId) → 更新 status=RUNNING
    │                   ├── progressCallback = (msg) → emitter.send("progress", msg)
    │                   └── orchestrator.execute(prompt, outputDir, progressCallback)
    │                           │
    │                           ├── Step 1: planProject(prompt)
    │                           │       → chatModel.chat(strictPrompt) [非流式, maxTokens=4096]
    │                           │       → 输出 JSON: {siteName, pages[], tables[], features[]}
    │                           │
    │                           ├── Step 2: createProjectStructure(projectDir)
    │                           │       → Files.createDirectories(public/css/, public/js/, ...)
    │                           │
    │                           ├── Step 3: generateFrontend(prompt, plan)
    │                           │       → chatModel.streamChat(strictPrompt) [流式, maxTokens=16384]
    │                           │       → BufferedReader 逐行解析 SSE 拼接完整输出
    │                           │       → 输出 ---FILE: 分隔的 HTML/CSS/JS
    │                           │       → writeFrontendFiles() → FileSystemTool.writeFile()
    │                           │
    │                           ├── Step 4: generateDatabase(prompt, plan)
    │                           │       → chatModel.streamChat() [流式, maxTokens=16384]
    │                           │       → 输出完整 SQL (建表 + 种子数据)
    │                           │       → writeDatabaseFiles() → FileSystemTool.writeFile()
    │                           │
    │                           ├── Step 5: generateBackend(prompt, plan, dbSchema)
    │                           │       → chatModel.streamChat() [流式, maxTokens=16384]
    │                           │       → 输出完整 server.js (Express + better-sqlite3)
    │                           │       → writeBackendFiles() → FileSystemTool.writeFile()
    │                           │
    │                           └── Step 6: generateProjectConfig(projectDir)
    │                                   → PackageTool.generatePackageJson() → writeFile()
    │                                   → PackageTool.generateGitignore() → writeFile()
    │                                   → writeFile("README.md")
    │                           │
    │                           └── 返回 projectDir 路径
    │                   │
    │                   ├── 更新 status=COMPLETED → 发送 complete SSE 事件
    │                   └── 异常时: 更新 status=FAILED → 发送 error SSE 事件
    │
    └── 下载 → GET /api/v1/generator/download/{taskId}
            → GeneratorController.download()
                → ZipUtil.pack(outputPath) → 流式输出 ZIP
```

**AI 模型调用策略**：
| 步骤 | 模型调用方式 | maxTokens | 超时 | 原因 |
|------|------------|-----------|------|------|
| 规划 | `chat()` 非流式 | 4096 | 120s | 输出短 JSON |
| 前端 | `streamChat()` 流式 | 16384 | 300s | 长 HTML/CSS/JS |
| 数据库 | `streamChat()` 流式 | 16384 | 300s | 长 SQL 脚本 |
| 后端 | `streamChat()` 流式 | 16384 | 300s | 长 server.js |

**Prompt 约束策略**（每个调用严格限定）：
- 明确标注"只输出 XXX，不要输出任何解释、说明、markdown 标记或代码块标记"
- 规划阶段：严格 JSON schema（siteName/pages/tables/features）
- 前端代码：`---FILE:` 分隔符，每个文件完整
- 数据库：完整 SQL 脚本（建表 + 种子数据）
- 后端代码：完整 server.js（Express + better-sqlite3 CRUD）

## 4. 数据流

```text
前端 (Vue 3)
  │
  │ 1. 用户操作
  ▼
Axios 请求 (api/request.js)
  │ 请求头自动携带 Authorization: Bearer <token>
  ▼
gateway (8080)
  │ JwtAuthFilter 校验 → 注入 X-User-Id/X-User-Name
  │ 路由转发到对应服务
  ▼
下游服务 (8081/8082/8083)
  │ Controller → Service → Mapper → MySQL
  │ 返回 Result<T>
  ▼
gateway → 前端 Axios 响应拦截器
  │ code=200 → 解包返回 data
  │ code≠200 → ElMessage 提示错误
  ▼
Vue 组件渲染
```

## 5. 数据库设计

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
CREATE TABLE IF NOT EXISTS generation_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  prompt TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  output_path VARCHAR(500),
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user (user_id)
);
```

## 6. 技术栈详情

| 层 | 技术 | 版本 |
|---|---|---|
| 前端框架 | Vue 3 (`<script setup>`) | 3.x |
| 构建工具 | Vite | 5.x |
| 路由 | Vue Router | 4.x |
| 状态管理 | Pinia | 2.x |
| HTTP 客户端 | Axios | 1.x |
| UI 组件库 | Element Plus | 2.x |
| 网关 | Spring Cloud Gateway | 4.x |
| 后端框架 | Spring Boot | 3.2.0 |
| 微服务治理 | Spring Cloud + Nacos | 2023.x |
| 数据访问 | MyBatis-Plus | 3.5.x |
| 数据库 | MySQL | 8.0 |
| 鉴权 | JWT (HS256) | jjwt 0.12.x |
| HTTP 客户端 | RestTemplate | Spring Boot 3 内置 |
| AI 模型 | DeepSeek-v4-pro | 流式 SSE 输出 |
| 生成项目依赖 | Express + better-sqlite3 | (由 AI 生成) |
| ZIP 打包 | zt-zip | 1.17 |
| 测试 | JUnit 5 / Mockito / MockMvc | |

## 7. Common 模块（跨服务共享）

| 类 | 路径 | 作用 |
|----|------|------|
| Result\<T\> | `htmlbuilder-common/.../result/Result.java` | 统一返回封装 |
| BusinessException | `htmlbuilder-common/.../exception/BusinessException.java` | 业务异常 |
| GlobalExceptionHandler | `htmlbuilder-common/.../exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` 全局异常处理 |
| JwtUtil | `htmlbuilder-common/.../util/JwtUtil.java` | JWT 签发/校验 |
| PageVO\<T\> | `htmlbuilder-common/.../vo/PageVO.java` | 分页 VO |

## 8. 前端路由

| 路由 | 组件 | 说明 |
|------|------|------|
| `/login` | Login.vue | 登录页 |
| `/register` | Register.vue | 注册页 |
| `/workspace` | Workspace.vue | 项目列表（工作台） |
| `/editor/:projectId/:pageId` | Editor.vue | 编辑器（组件库 + 画布 + 属性面板 + AI 生成） |

## 9. 前端 API 模块

| 模块 | 文件 | 接口 |
|------|------|------|
| 认证 | `api/auth.js` | login/register/refresh |
| 项目 | `api/project.js` | 项目 CRUD |
| 页面 | `api/page.js` | 页面 CRUD |
| 生成器 | `api/index.js` | submitGenerate/getTaskStatus |

## 10. 启动顺序

```bash
# 1. 启动 Nacos（127.0.0.1:8848）
# 2. 启动 MySQL（127.0.0.1:3306），创建 user_db/project_db/generator_db
# 3. 按顺序启动后端服务
cd backend
mvn spring-boot:run -pl htmlbuilder-gateway
mvn spring-boot:run -pl htmlbuilder-user-service
mvn spring-boot:run -pl htmlbuilder-project-service
mvn spring-boot:run -pl htmlbuilder-generator-service

# 4. 启动前端
cd frontend
npm install
npm run dev
```