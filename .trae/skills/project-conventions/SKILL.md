---
name: "project-conventions"
description: "Defines microservice architecture (4 services + Nacos + MySQL per service), tech stack, layered structure, code style. Invoke when creating new files, organizing code, or unsure where to place new code."
---

# 项目结构与代码风格约定（project-conventions）

本项目是**在线 HTML 生成工具**，采用**微服务架构**，前后端分离。

## 技术栈（固定，不得擅自更换）

| 层 | 技术 |
|---|---|
| 前端 | Vue 3（`<script setup>`）、Vite、Vue Router、Pinia、Axios、Element Plus |
| 网关 | Spring Cloud Gateway、Nacos 服务发现 |
| 后端服务 | Java 17+、Spring Boot 3、Spring Cloud、Nacos Config、OpenFeign、Spring Validation |
| 数据访问 | MyBatis-Plus |
| 数据库 | MySQL 8（**每个微服务独立库**） |
| 鉴权 | JWT（user-service 签发，gateway 校验） |
| 通信 | RESTful JSON，所有接口返回统一 `Result` 封装（见 api-result-convention） |

## 微服务清单（固定 4 服务）

| 服务 | 端口 | 库 | 职责 |
|---|---|---|---|
| gateway-service | 8080 | — | 对外唯一入口、JWT 校验、路由转发、限流 |
| user-service | 8081 | user_db | 注册/登录/用户信息、JWT 签发 |
| project-service | 8082 | project_db | 项目/页面 CRUD、组件树存储 |
| generator-service | 8083 | generator_db | 组件定义、HTML 生成/导出 |

- 注册与配置中心：Nacos；服务名 `htmlbuilder-xxx-service`
- 服务间调用用 OpenFeign 接口（共享 `api-contract` 模块定义 Feign 接口与 DTO）
- gateway 校验后将 `userId`/`username` 注入下游请求头 `X-User-Id`/`X-User-Name`，下游禁止再重复校验 token

## 目录结构

```text
frontend/                        # Vue 3 前端
  src/
    api/                         # Axios 封装与接口定义（request.js + 按模块）
    assets/styles/               # 设计 token 与全局样式（见 ui-design-standard）
    components/                  # 通用组件（PascalCase）
    views/                       # 页面级组件
    stores/                      # Pinia 状态
    router/index.js
    App.vue / main.js

backend/                         # 后端多模块（Maven 聚合）
  pom.xml                        # 父 pom，统一版本管理
  htmlbuilder-common/            # 公共模块：Result、异常、工具类、常量
  htmlbuilder-api-contract/      # Feign 接口与跨服务 DTO，供各服务引用
  htmlbuilder-gateway/           # gateway-service
  htmlbuilder-user-service/      # user-service
  htmlbuilder-project-service/   # project-service
  htmlbuilder-generator-service/ # generator-service
    src/main/java/com/htmlbuilder/{common,controller,service,mapper,entity,dto,feign,config}
    src/main/resources/application.yml
    src/main/resources/db/        # 该服务库的 SQL 脚本
    src/test/java/                # JUnit 5 测试

docs/
  requirements.md                # 需求文档（开发前必须更新，见 development-workflow）
```

## 分层与命名约定

- 每个后端服务内部严格分层：Controller（参数校验 + 调 Service）→ Service（业务逻辑）→ Mapper（MyBatis-Plus 数据访问）；禁止在 Controller 写业务逻辑、禁止跨层调用
- Java 类名 UpperCamelCase + 层级后缀：`XxxController`/`XxxService`/`XxxServiceImpl`/`XxxMapper`/`XxxDTO`/`XxxVO`（entity 无后缀）
- API 路径：统一 `/api/v1/` 前缀 + 资源名复数 kebab-case（如 `/api/v1/projects/{id}/pages`）
- Vue 组件文件 PascalCase；JS 变量/函数 camelCase；常量 UPPER_SNAKE_CASE
- 数据库：表名/字段名 snake_case；每表含 `id BIGINT` 主键、`created_at`、`updated_at`；表结构变更必须同步 `*/resources/db/` SQL 脚本
- 微服务间共享契约放在 `htmlbuilder-api-contract`，禁止服务间直接引用对方的 entity/内部类

## 其他硬性约定

- 前端所有请求走 `api/request.js` 统一实例（含 Result 解包拦截器），禁止在组件内直接创建 Axios 请求
- 后端所有对外接口必须返回 `Result<T>`，禁止返回裸 entity/Map
- 敏感配置（数据库密码、JWT 密钥）用 Nacos Config 或环境变量，不得入库
- 第三方依赖只通过 npm/Maven 坐标引入，禁止拷贝源码进仓库
- 调试代码（console.log、注释代码块）提交前必须删除
