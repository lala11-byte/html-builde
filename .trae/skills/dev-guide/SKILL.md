---
name: "dev-guide"
description: "High-efficiency high-quality development guide for this project. Covers the complete development cycle, conventions, common pitfalls, debugging, and tooling. Invoke when starting new development, troubleshooting, or wanting to improve development efficiency."
---

# 高效高质量开发指南（dev-guide）

## 1. 快速开始

### 1.1 环境准备

```bash
# 必备环境
Java 17+     # java -version
Maven 3.8+   # mvn -v
Node.js 18+  # node -v
MySQL 8.0    # mysql -u root -p
Nacos 2.x    # 127.0.0.1:8848

# 数据库初始化
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4"
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS project_db CHARACTER SET utf8mb4"
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS generator_db CHARACTER SET utf8mb4"

# 启动后端（按顺序）
cd backend
mvn clean package -DskipTests
mvn spring-boot:run -pl htmlbuilder-gateway &
mvn spring-boot:run -pl htmlbuilder-user-service &
mvn spring-boot:run -pl htmlbuilder-project-service &
mvn spring-boot:run -pl htmlbuilder-generator-service &

# 启动前端
cd frontend
npm install
npm run dev
```

### 1.2 快速验证

```bash
# 后端编译
cd backend && mvn compile -q

# 后端测试
cd backend && mvn test -pl htmlbuilder-project-service

# 前端编译
cd frontend && npm run build

# 前端测试
cd frontend && npm run test
```

## 2. 开发流程（强制）

### 2.1 完整流程

```
需求文档(docs/requirements.md) → 测试先行 → 开发实现 → 验收回归 → 提交推送
```

### 2.2 需求文档模板

在 `docs/requirements.md` 中按以下模板写入：

```markdown
### [模块名] 功能名称

- 背景/目标：为什么要做这个功能
- 输入/输出：明确的数据格式
- 验收标准：(可逐条验证的列表)
  - [ ] 标准1...
  - [ ] 标准2...
- 涉及接口/表：
  - POST /api/v1/xxx/xxx
  - 表：xxx
```

### 2.3 测试先行

**后端测试模板**：
```java
@SpringBootTest
@AutoConfigureMockMvc
class XxxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private XxxService xxxService;

    @Test
    void shouldReturn400WhenParamInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/xxx")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
```

**前端测试模板**：
```javascript
import { describe, it, expect } from 'vitest'

describe('工具函数测试', () => {
  it('应正确处理空输入', () => {
    expect(myFunction('')).toBe(null)
  })
})
```

### 2.4 开发实现准则

- 编写最小实现使测试通过
- 不做需求文档之外的事
- 发现需求遗漏 → 先回写 requirements.md
- 不跳过需求文档直接写代码

## 3. 后端开发规范

### 3.1 分层调用链

```
Controller → Service(接口) → ServiceImpl → Mapper → MySQL
    ↑            ↑                            ↑
  参数校验    业务逻辑                    MyBatis-Plus
  调Service   调Mapper                   BaseMapper
```

**禁止事项**：
- Controller 中写业务逻辑
- 跨层调用（Controller 直接调 Mapper）
- Service 直接返回 entity

### 3.2 注解规范（必读）

#### Controller 层注解

```java
@RestController
@RequestMapping("/api/v1/users")      // 统一前缀，资源名复数 kebab-case
@Slf4j
public class UserController {

    // 参数校验 + 返回 Result
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody CreateUserDTO dto) {
        return Result.success(userService.create(dto));
    }

    // 分页查询
    @GetMapping
    public Result<PageVO<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.list(page, size));
    }

    // 路径参数 + 解析请求头中的 userId
    @GetMapping("/{id}")
    public Result<UserVO> getById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getById(id, userId));
    }
}
```

#### Service 层注解

```java
// 接口
public interface UserService {
    UserVO create(CreateUserDTO dto);
    PageVO<UserVO> list(int page, int size);
    UserVO getById(Long id, Long userId);
}

// 实现
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(CreateUserDTO dto) {
        // 业务逻辑
        User user = new User();
        BeanUtils.copyProperties(dto, user);
        userMapper.insert(user);
        return convertToVO(user);
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
```

#### Entity 注解

```java
@Data
@TableName("user")                    // 对应数据库表名
public class User {
    @TableId(type = IdType.AUTO)      // 自增主键
    private Long id;

    private String username;

    @TableField("password_hash")      // 字段名映射
    private String passwordHash;

    @TableField(fill = FieldFill.INSERT)         // 插入时自动填充
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)  // 插入和更新时自动填充
    private LocalDateTime updatedAt;
}
```

#### Mapper 注解

```java
@Mapper                                // 必须加，否则 MyBatis 无法扫描
public interface UserMapper extends BaseMapper<User> {
    // 自定义查询
    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);
}
```

#### Config 注解

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

#### DTO 注解

```java
@Data
public class CreateUserDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String password;
}
```

#### VO 注解

```java
@Data
public class UserVO {
    private Long id;
    private String username;
    private LocalDateTime createdAt;
}
```

#### 全局异常处理器

```java
@RestControllerAdvice
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

### 3.3 注解层级汇总表

| 层 | 必须注解 | 可选注解 |
|----|---------|---------|
| **Controller** | `@RestController` `@RequestMapping` | `@Slf4j` `@CrossOrigin` |
| **Controller 方法** | `@PostMapping/@GetMapping/...` | `@Valid` `@RequestHeader` |
| **Service 接口** | 无 | — |
| **ServiceImpl** | `@Service` | `@Slf4j` `@Transactional` |
| **Entity** | `@TableName` `@TableId` | `@TableField` `@Data` |
| **Mapper** | `@Mapper` | — |
| **Config** | `@Configuration` | — |
| **DTO** | `@Data` | `@NotBlank` `@Size` `@NotNull` 等校验注解 |
| **VO** | `@Data` | — |
| **ExceptionHandler** | `@RestControllerAdvice` / `@ControllerAdvice` | `@Slf4j` |
| **启动类** | `@SpringBootApplication` `@EnableDiscoveryClient` | `@MapperScan` |

### 3.4 模块内部调用关系（以 generator-service 为例）

```
GeneratorController (controller)
  │  @Autowired GeneratorService
  │
  ├── generate() → service.submitTask(userId, prompt)
  │                 → GenerationTaskMapper.insert(task) → generator_db.generation_task
  │
  ├── progress() → service.executeTaskAsync(taskId, emitter)
  │                 → ExecutorService.execute(() -> {
  │                     taskMapper.selectById(taskId)
  │                     orchestrator.execute(prompt, outputDir, callback)
  │                       → chatModel.chat() / chatModel.streamChat()
  │                       → FileSystemTool.writeFile()
  │                       → PackageTool.generatePackageJson()
  │                     taskMapper.updateById(task)
  │                     emitter.send(SseEmitter.event())
  │                   })
  │
  ├── download() → service.getOutputPath(taskId) → ZipUtil.pack()
  │
  └── getTask() → service.getTask(taskId) → taskMapper.selectById()
```

### 3.5 调用关系关键规则

1. **Controller → Service**：`@Autowired` 注入接口，不注入实现类
2. **Service → Mapper**：`@Autowired` 注入 Mapper 接口
3. **Service → 外部依赖**：通过构造函数注入（如 `AgentOrchestrator(DeepSeekChatModel)`）
4. **跨服务调用**：通过 OpenFeign 接口（放在 `api-contract` 模块）
5. **异步任务**：通过 `ExecutorService` 线程池
6. **SSE 推送**：`SseEmitter` 在 Controller 创建，传给 Service 使用

## 4. 前端开发规范

### 4.1 API 调用规范

```javascript
// api/auth.js
import request from './request'

export function login(data) {
  return request.post('/auth/login', data)
}

// api/project.js
import request from './request'

export function listProjects(page = 1, size = 10) {
  return request.get('/projects', { params: { page, size } })
}
```

### 4.2 Vue 组件规范

```vue
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const loading = ref(false)
const form = ref({ username: '', password: '' })

const handleLogin = async () => {
  loading.value = true
  try {
    const result = await login(form.value)
    // request.js 拦截器已解包，result 直接是 data
    localStorage.setItem('token', result.token)
    router.push('/workspace')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 初始化
})

onUnmounted(() => {
  // 清理（EventSource、定时器等）
})
</script>
```

### 4.3 SSE 使用规范

```javascript
// EventSource 不支持自定义请求头，对应接口必须加入 gateway 白名单
let eventSource = null

const connectSSE = (taskId) => {
  eventSource = new EventSource(`http://localhost:8080/api/v1/generator/progress/${taskId}`)

  eventSource.addEventListener('progress', (event) => {
    logs.value.push(event.data)
  })

  eventSource.addEventListener('complete', (event) => {
    const data = JSON.parse(event.data)
    downloadUrl.value = data.downloadUrl
    eventSource.close()
  })

  eventSource.addEventListener('error', (event) => {
    eventSource.close()
  })
}

onUnmounted(() => {
  eventSource?.close()
})
```

## 5. 常见问题与调试

### 5.1 编译问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `NoSuchBeanDefinitionException` | 缺少 `@Mapper` / `@Service` 注解 | 检查注解 |
| `SQLSyntaxErrorException: Table doesn't exist` | 数据库未建表 | 执行 `db/init.sql` |
| `Connection refused` | MySQL/Nacos 未启动 | 启动基础设施 |
| `maxTokens 导致输出截断` | 非流式调用 maxTokens 太小 | 改用 `streamChat()` maxTokens=16384 |

### 5.2 运行时问题

| 问题 | 原因 | 解决 |
|------|------|------|
| gateway 返回 401 | token 过期或未携带 | 检查 Authorization 头 |
| 前端 `EventSource` 连接失败 | 未加入白名单 | gateway `JwtAuthFilter` 白名单 |
| AI 生成代码截断 | 非流式调用或 maxTokens 不够 | 使用 `streamChat()` + maxTokens=16384 |
| `CountDownLatch` 超时 | AI 模型响应慢 | 增加 timeout 到 300s |

### 5.3 调试技巧

```bash
# 查看 Nacos 服务注册状态
curl http://127.0.0.1:8848/nacos/v1/ns/service/list

# 直接调用下游服务（绕过 gateway）
curl http://127.0.0.1:8083/api/v1/generator/tasks/1

# 通过 gateway 调用（需 token）
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/api/v1/projects

# 查看 MySQL 表结构
mysql -u root -p generator_db -e "DESC generation_task"
```

## 6. 代码质量要求

### 6.1 提交前自检清单

- [ ] `mvn test` 全部通过
- [ ] `npm run test` 全部通过
- [ ] 新增接口返回 `Result<T>`，错误码已登记
- [ ] 数据库变更已同步 `db/init.sql`
- [ ] 无 `console.log` / 注释代码块残留
- [ ] `docs/requirements.md` 已更新
- [ ] 无硬编码魔法值

### 6.2 禁止事项

- 禁止跳过需求文档直接写代码
- 禁止 Controller 写业务逻辑
- 禁止返回裸 entity/Map
- 禁止在组件内直接创建 Axios 实例
- 禁止 `git push --force`
- 禁止 AI 生成代码时使用非流式调用（会导致截断）

## 7. AI 生成器开发专项

### 7.1 Prompt 设计原则

1. **角色设定**：明确告诉 AI 它是谁（"你是一个资深前端开发工程师"）
2. **输出格式约束**：严格限定输出格式，用分隔符（如 `---FILE:`）
3. **禁止无关内容**：明确标注"只输出代码，不要输出任何解释"
4. **完整度要求**：明确标注"代码必须完整，不得截断或使用省略号"
5. **技术栈约束**：指定具体技术栈版本（Express + better-sqlite3）

### 7.2 防截断策略

```
短输出（< 4096 tokens）→ 非流式 chat()，maxTokens=4096
长输出（HTML/CSS/JS/SQL/后端代码）→ 流式 streamChat()，maxTokens=16384
  ├── StreamingResponseHandler 逐 token 收集
  ├── CountDownLatch 同步等待完成
  └── 超时 300s，超时后抛出明确错误
```

### 7.3 新增 Agent 步骤

1. 在 `AgentOrchestrator` 中添加新步骤方法
2. 编写对应的 Prompt（严格约束输出格式）
3. 判断使用 `chat()` 还是 `streamChat()`
4. 添加对应的 `progressCallback.accept()` 进度消息
5. 更新 `requirements.md` 验收标准

## 8. 测试策略

### 8.1 测试分层

```
单元测试（Service/Utils）     ← 优先写，覆盖核心逻辑
集成测试（Controller + Mapper） ← 覆盖接口链路
端到端测试（gateway 全链路）   ← 覆盖核心场景
```

### 8.2 测试命令速查

```bash
# 后端：单个服务
mvn test -pl htmlbuilder-generator-service

# 后端：全部服务
mvn test

# 前端
npm run test

# 前端：单个文件
npx vitest src/utils/__tests__/myFunc.test.js
```