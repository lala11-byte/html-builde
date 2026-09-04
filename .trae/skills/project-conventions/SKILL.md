---
name: "project-conventions"
description: "Defines microservice architecture (4 services + Nacos + MySQL per service), tech stack, layered structure, code style, annotation specs, and module internal call chains. Invoke when creating new files, organizing code, or unsure where to place new code."
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
| generator-service | 8083 | generator_db | AI Agent 驱动的网站生成引擎 |

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
    src/main/java/com/htmlbuilder/generator/
      agent/                     # AI Agent 层（模型+编排器+工具）
      controller/                # 控制器
      service/                   # 业务服务
      mapper/                    # MyBatis-Plus Mapper
      entity/                    # 实体
      dto/                       # 入参 DTO
      vo/                        # 出参 VO
      config/                    # 配置类
    src/main/resources/application.yml
    src/main/resources/db/       # 该服务库的 SQL 脚本（init.sql）
    src/test/java/               # JUnit 5 测试

docs/
  requirements.md                # 需求文档（开发前必须更新，见 development-workflow）
```

## 分层与命名约定

- 每个后端服务内部严格分层：Controller（参数校验 + 调 Service）→ Service（业务逻辑）→ Mapper（MyBatis-Plus 数据访问）；禁止在 Controller 写业务逻辑、禁止跨层调用
- Java 类名 UpperCamelCase + 层级后缀：`XxxController`/`XxxService`/`XxxServiceImpl`/`XxxMapper`/`XxxDTO`/`XxxVO`（entity 无后缀）
- API 路径：统一 `/api/v1/` 前缀 + 资源名复数 kebab-case（如 `/api/v1/projects/{id}/pages`）
- Vue 组件文件 PascalCase；JS 变量/函数 camelCase；常量 UPPER_SNAKE_CASE
- 数据库：表名/字段名 snake_case；每表含 `id BIGINT` 主键、`created_at`、`updated_at`；表结构变更必须同步 `*/resources/db/init.sql` SQL 脚本
- 微服务间共享契约放在 `htmlbuilder-api-contract`，禁止服务间直接引用对方的 entity/内部类

---

## 注解规范（各层必须使用的注解）

### Controller 层

```java
@RestController                     // 必须
@RequestMapping("/api/v1/xxx")      // 必须，资源名复数 kebab-case
@Slf4j                              // 建议
public class XxxController {

    @Autowired
    private XxxService xxxService;  // 注入接口，不注入实现类

    @PostMapping                    // POST 请求
    public Result<XxxVO> create(@Valid @RequestBody XxxDTO dto) {
        // @Valid 触发参数校验
        // 返回 Result<T> 封装
        return Result.success(xxxService.create(dto));
    }

    @GetMapping("/{id}")            // GET 请求 + 路径参数
    public Result<XxxVO> getById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(xxxService.getById(id, userId));
    }

    @GetMapping
    public Result<PageVO<XxxVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(xxxService.list(page, size));
    }
}
```

### Service 层

```java
// 接口：不需要注解
public interface XxxService {
    XxxVO create(XxxDTO dto);
}

// 实现：必须 @Service
@Service
@Slf4j
public class XxxServiceImpl implements XxxService {

    @Autowired
    private XxxMapper xxxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)  // 写操作加事务
    public XxxVO create(XxxDTO dto) {
        Xxx entity = new Xxx();
        BeanUtils.copyProperties(dto, entity);
        xxxMapper.insert(entity);
        return convertToVO(entity);
    }

    private XxxVO convertToVO(Xxx entity) {
        XxxVO vo = new XxxVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
```

### Entity 层

```java
@Data
@TableName("xxx_table")                    // 必须，映射数据库表名
public class XxxEntity {
    @TableId(type = IdType.AUTO)           // 必须，自增主键
    private Long id;

    private String name;                   // 默认映射同名字段

    @TableField("db_column_name")          // 字段名不一致时使用
    private String dbColumnName;

    @TableField(fill = FieldFill.INSERT)           // 插入时自动填充
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)    // 插入和更新时自动填充
    private LocalDateTime updatedAt;
}
```

### Mapper 层

```java
@Mapper                                    // 必须，否则 MyBatis 无法扫描
public interface XxxMapper extends BaseMapper<XxxEntity> {
    // 自定义查询
    @Select("SELECT * FROM xxx_table WHERE name = #{name}")
    XxxEntity selectByName(@Param("name") String name);
}
```

### DTO 层（入参）

```java
@Data
public class XxxDTO {
    @NotBlank(message = "名称不能为空")
    @Size(min = 3, max = 50, message = "名称长度 3-50 位")
    private String name;

    @NotNull(message = "类型不能为空")
    private Integer type;
}
```

### VO 层（出参）

```java
@Data
public class XxxVO {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
```

### Config 层

```java
@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 全局异常处理器（common 模块）

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail(500, "服务器内部错误");
    }
}
```

### 启动类

```java
@SpringBootApplication
@EnableDiscoveryClient                     // 注册到 Nacos
@MapperScan("com.htmlbuilder.xxx.mapper")   // 可选，或用 @Mapper 注解
public class XxxServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(XxxServiceApplication.class, args);
    }
}
```

### 注解层级汇总速查表

| 层 | 必须注解 | 可选注解 |
|----|---------|---------|
| **Controller** | `@RestController` `@RequestMapping` | `@Slf4j` |
| **Controller 方法** | `@PostMapping`/`@GetMapping`/`@PutMapping`/`@DeleteMapping` | `@Valid` `@RequestHeader` |
| **Service 接口** | 无 | — |
| **ServiceImpl** | `@Service` | `@Slf4j` `@Transactional` |
| **Entity** | `@TableName` `@TableId` | `@TableField` `@Data` |
| **Mapper** | `@Mapper` | — |
| **Config** | `@Configuration` | — |
| **DTO** | `@Data` | `@NotBlank` `@Size` `@NotNull` 等校验注解 |
| **VO** | `@Data` | — |
| **ExceptionHandler** | `@RestControllerAdvice` | `@Slf4j` |
| **启动类** | `@SpringBootApplication` `@EnableDiscoveryClient` | `@MapperScan` |

---

## 模块内部调用关系（各服务详细调用链）

### gateway-service 调用链

```
HTTP Request (前端)
  → JwtAuthFilter.filter()
    ├── WHITE_PATHS 匹配 → 放行
    └── 非白名单 → 校验 Authorization: Bearer <token>
        ├── 校验通过 → 注入 X-User-Id / X-User-Name 请求头 → 转发
        └── 校验失败 → Result(401, "未登录"), HTTP 200
  → RouteConfig 路由转发
    ├── /api/v1/auth/** → user-service:8081
    ├── /api/v1/projects/** → project-service:8082
    ├── /api/v1/generator/** → generator-service:8083
    └── 下游不可用 → 降级 Result(5003, "服务暂不可用")
```

### user-service 调用链

```
AuthController
  ├── POST /auth/register → authService.register(dto)
  │     → UserMapper.selectByUsername() → 校验唯一性
  │     → BCrypt.encode(password) → UserMapper.insert(user)
  │     → JwtUtil.generateToken(userId, username) → 返回 TokenVO
  │
  ├── POST /auth/login → authService.login(dto)
  │     → UserMapper.selectByUsername() → BCrypt.matches(password)
  │     → JwtUtil.generateToken() → 返回 TokenVO
  │
  └── POST /auth/refresh → authService.refresh(token)
        → JwtUtil.validateToken() → JwtUtil.generateToken() → 返回 TokenVO
```

### project-service 调用链

```
ProjectController
  ├── POST /projects → projectService.createProject(userId, dto)
  │     → ProjectMapper.insert(project) → 返回 ProjectVO
  │
  ├── GET /projects → projectService.listProjects(userId, page, size)
  │     → ProjectMapper.selectPage(page, wrapper) → 返回 PageVO<ProjectVO>
  │
  ├── PUT /projects/{id} → projectService.updateProject(userId, id, dto)
  │     → ProjectMapper.selectById(id) → 校验归属 → ProjectMapper.updateById()
  │
  ├── DELETE /projects/{id} → projectService.deleteProject(userId, id)
  │     → ProjectMapper.selectById(id) → 校验归属
  │     → PageMapper.deleteByProjectId(id) → ProjectMapper.deleteById(id)
  │
  ├── POST /projects/{pid}/pages → projectService.createPage(pid, dto)
  │     → PageMapper.insert(page) → 返回 PageVO
  │
  ├── GET /projects/{pid}/pages → projectService.listPages(pid, page, size)
  │     → PageMapper.selectPage(page, wrapper) → 返回 PageVO<PageVO>
  │
  ├── PUT /projects/{pid}/pages/{pageId} → projectService.updatePage(...)
  │     → PageMapper.selectById(pageId) → 校验归属 → PageMapper.updateById()
  │
  └── DELETE /projects/{pid}/pages/{pageId} → projectService.deletePage(...)
        → PageMapper.selectById(pageId) → 校验归属 → PageMapper.deleteById()
```

### generator-service 调用链

```
GeneratorController
  ├── POST /generator/generate → generatorService.submitTask(userId, prompt)
  │     → GenerationTaskMapper.insert(task) → generator_db.generation_task
  │     → 返回 TaskVO(taskId, status=PENDING)
  │
  ├── GET /generator/progress/{taskId} → generatorService.executeTaskAsync(taskId, emitter)
  │     → ExecutorService.submit(() -> {
  │         taskMapper.selectById(taskId) → 更新 status=RUNNING
  │         orchestrator.execute(prompt, outputDir, progressCallback)
  │           → Step 1: planProject()
  │           │     → chatModel.chat() [非流式, maxTokens=4096]
  │           │
  │           → Step 2: createProjectStructure()
  │           │     → Files.createDirectories()
  │           │
  │           → Step 3: generateFrontend()
  │           │     → chatModel.streamChat() [流式, maxTokens=16384]
  │           │     → CountDownLatch 同步等待完整输出
  │           │     → FileSystemTool.writeFile() 写入 public/
  │           │
  │           → Step 4: generateDatabase()
  │           │     → chatModel.streamChat() [流式, maxTokens=16384]
  │           │     → FileSystemTool.writeFile() 写入 database/init.sql
  │           │
  │           → Step 5: generateBackend()
  │           │     → chatModel.streamChat() [流式, maxTokens=16384]
  │           │     → FileSystemTool.writeFile() 写入 server.js
  │           │
  │           → Step 6: generateProjectConfig()
  │                 → PackageTool.generatePackageJson() → writeFile()
  │                 → PackageTool.generateGitignore() → writeFile()
  │         taskMapper.updateById(task) → status=COMPLETED
  │         emitter.send(complete event)
  │       })
  │
  ├── GET /generator/download/{taskId} → generatorService.getOutputPath(taskId)
  │     → ZipUtil.pack(outputPath) → 流式输出 ZIP
  │
  └── GET /generator/tasks/{taskId} → generatorService.getTask(taskId)
        → taskMapper.selectById(taskId) → 返回 TaskVO
```

### 调用链关键规则

1. **Controller → Service**：`@Autowired` 注入接口，不注入实现类
2. **Service → Mapper**：`@Autowired` 注入 Mapper 接口
3. **Service → 外部依赖**：通过构造函数注入（如 `AgentOrchestrator(DeepSeekChatModel)`）
4. **跨服务调用**：通过 OpenFeign 接口（放在 `api-contract` 模块）
5. **异步任务**：通过 `ExecutorService` 线程池
6. **SSE 推送**：`SseEmitter` 在 Controller 创建，传给 Service 使用

---

## 数据库初始化规范

### init.sql 要求

每个服务必须在 `src/main/resources/db/init.sql` 中维护建表脚本：

```sql
-- 数据库初始化脚本
-- 执行方式：mysql -u root -p <db_name> < init.sql

CREATE TABLE IF NOT EXISTS xxx (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_name (name)
);
```

### 表结构变更流程

1. 修改对应服务的 `db/init.sql`
2. 如果是新增字段，同时更新 Entity 类
3. 在本地数据库执行变更
4. 更新 `requirements.md` 涉及表说明

---

## 其他硬性约定

- 前端所有请求走 `api/request.js` 统一实例（含 Result 解包拦截器），禁止在组件内直接创建 Axios 请求
- 后端所有对外接口必须返回 `Result<T>`，禁止返回裸 entity/Map
- 敏感配置（数据库密码、JWT 密钥）用 Nacos Config 或环境变量，不得入库
- 第三方依赖只通过 npm/Maven 坐标引入，禁止拷贝源码进仓库
- 调试代码（console.log、注释代码块）提交前必须删除
- 数据库表结构变更必须同步 `*/resources/db/init.sql`