package com.htmlbuilder.generator.agent;

import com.htmlbuilder.generator.agent.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AI Agent 编排器
 * 接收用户需求 → 规划网站结构 → 调用工具生成 → 打包项目
 *
 * 核心设计：
 * 1. 规划阶段使用非流式调用（输出短 JSON）
 * 2. 代码生成阶段使用流式调用（maxTokens=16384，防止截断）
 * 3. 每个 Prompt 严格限定输出格式，禁止模型输出无关内容
 * 4. 流式传输 + 大 maxTokens 确保代码完整生成
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final DeepSeekChatModel chatModel;
    private final FileSystemTool fileSystemTool;
    private final DatabaseSchemaTool databaseSchemaTool;
    private final WebContentTool webContentTool;
    private final PackageTool packageTool;

    public AgentOrchestrator(DeepSeekChatModel chatModel) {
        this.chatModel = chatModel;
        this.fileSystemTool = new FileSystemTool();
        this.databaseSchemaTool = new DatabaseSchemaTool();
        this.webContentTool = new WebContentTool();
        this.packageTool = new PackageTool();
    }

    /**
     * 执行生成任务
     */
    public String execute(String prompt, String baseOutputDir, Consumer<String> progressCallback) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        String projectDir = Path.of(baseOutputDir, taskId).toString();

        try {
            progressCallback.accept("[开始] 任务初始化...");

            // Step 1: 规划网站结构（非流式，输出短 JSON）
            progressCallback.accept("[规划] AI 正在分析需求并规划网站结构...");
            String plan = planProject(prompt);
            progressCallback.accept("[规划] 网站结构规划完成");

            // Step 2: 创建目录结构
            progressCallback.accept("[文件] 创建项目目录结构...");
            createProjectStructure(projectDir);
            progressCallback.accept("[文件] 目录结构创建完成");

            // Step 3: 生成前端页面（流式，防止 HTML/CSS/JS 截断）
            progressCallback.accept("[前端] AI 正在生成前端页面代码（流式传输，防止截断）...");
            String frontendCode = generateFrontend(prompt, plan, progressCallback);
            writeFrontendFiles(projectDir, frontendCode);
            progressCallback.accept("[前端] 前端页面生成完成");

            // Step 4: 生成数据库（流式，防止 SQL 截断）
            progressCallback.accept("[数据库] AI 正在设计数据库结构（流式传输）...");
            String dbSchema = generateDatabase(prompt, plan, progressCallback);
            writeDatabaseFiles(projectDir, dbSchema);
            progressCallback.accept("[数据库] 数据库设计完成");

            // Step 5: 生成后端 API（流式，防止长代码截断）
            progressCallback.accept("[后端] AI 正在生成后端 API 代码（流式传输）...");
            String backendCode = generateBackend(prompt, plan, dbSchema, progressCallback);
            writeBackendFiles(projectDir, backendCode);
            progressCallback.accept("[后端] 后端 API 生成完成");

            // Step 6: 打包项目
            progressCallback.accept("[打包] 生成项目配置文件...");
            generateProjectConfig(projectDir);
            progressCallback.accept("[打包] 项目打包完成");

            progressCallback.accept("[完成] 网站生成完成！文件位于: " + projectDir);

            return projectDir;

        } catch (Exception e) {
            log.error("Generation failed", e);
            progressCallback.accept("[错误] " + e.getMessage());
            throw new RuntimeException("Generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 规划网站结构（非流式，输出短 JSON，严格限定格式）
     */
    private String planProject(String prompt) {
        String systemPrompt = """
【角色】你是一个专业的网站架构师。
【任务】根据用户需求，生成一个 JSON 格式的网站规划。
【输出要求】只输出 JSON，不要输出任何解释、说明、markdown 标记或代码块标记。
【JSON 结构】必须包含以下字段：
{
  "siteName": "网站名称",
  "pages": [
    {"name": "页面文件名(不含扩展名)", "title": "页面标题", "description": "页面描述", "route": "路由路径"}
  ],
  "tables": [
    {"name": "表名", "columns": ["列定义 SQL"], "description": "表描述"}
  ],
  "features": ["功能1", "功能2"]
}
【约束】
- 至少 3 个页面（首页、列表页、详情页）
- 至少 2 张关联表
- 每个页面必须有完整的 name/title/description/route
- 所有输出内容（siteName、title、description、features 等）必须使用简体中文
- 只输出 JSON，不要输出任何其他内容
""";

        return chatModel.chat(systemPrompt + "\n\n用户需求：" + prompt);
    }

    /**
     * 生成前端代码（流式，maxTokens=16384，防止 HTML/CSS/JS 截断）
     */
    private String generateFrontend(String prompt, String plan, Consumer<String> progressCallback) {
        String systemPrompt = """
【角色】你是一个资深前端开发工程师。
【任务】根据用户需求和网站规划，生成完整的网站前端代码。
【输出格式】严格按以下格式输出，用 `---FILE:` 分隔每个文件，每个文件必须完整：
```
---FILE: public/index.html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>网站名称</title>
  <link rel="stylesheet" href="/css/style.css">
</head>
<body>
  ...完整的 HTML 内容...
  <script src="/js/app.js"></script>
</body>
</html>
---FILE: public/css/style.css
/* 完整的 CSS 代码 */
---FILE: public/js/app.js
// 完整的 JavaScript 代码
```
【严格要求】
1. 必须生成所有规划中的页面，每个页面作为独立的 HTML 文件
2. 网站界面语言必须为简体中文：所有页面标题、导航菜单、按钮文字、表单标签、占位符、提示文案、代码注释均使用简体中文，<html lang="zh-CN">
3. 使用现代 CSS（Flexbox/Grid），响应式布局（移动端适配）
4. JavaScript 使用 Fetch API 调用后端接口
5. 每个 HTML 文件必须是完整可独立渲染的文档
6. 所有页面共享同一个 style.css 和 app.js
7. 代码必须完整，不得使用省略号、注释占位符或截断
8. 样式美观、现代，参考 Material Design 或类似设计规范
9. 只输出代码，不要输出任何解释、说明或额外文字
""";

        return chatModel.streamChat(systemPrompt + "\n\n用户需求：" + prompt + "\n\n网站规划：" + plan, progressCallback);
    }

    /**
     * 生成数据库（流式，防止 SQL 截断）
     */
    private String generateDatabase(String prompt, String plan, Consumer<String> progressCallback) {
        String systemPrompt = """
【角色】你是一个数据库设计师。
【任务】根据用户需求和网站规划，设计 SQLite 数据库。
【输出格式】严格按以下格式输出完整的 SQL 脚本：
```
-- 数据库初始化脚本
PRAGMA journal_mode=WAL;

-- 建表语句
CREATE TABLE IF NOT EXISTS table_name (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  column_def TEXT NOT NULL,
  ...
);

-- 种子数据
INSERT INTO table_name (col1, col2) VALUES ('val1', 'val2');
INSERT INTO table_name (col1, col2) VALUES ('val3', 'val4');
INSERT INTO table_name (col1, col2) VALUES ('val5', 'val6');
```
【严格要求】
1. 至少 2 张关联表，使用外键关联
2. 每张表至少 3 条种子数据
3. 使用 SQLite 语法（INTEGER PRIMARY KEY AUTOINCREMENT 等）
4. 建表语句使用 IF NOT EXISTS
5. SQL 注释、COMMENT 说明和种子数据内容必须使用简体中文
6. 所有 SQL 语句必须完整，不得截断或省略
7. 只输出 SQL 代码，不要输出任何解释或额外文字
""";

        return chatModel.streamChat(systemPrompt + "\n\n用户需求：" + prompt + "\n\n网站规划：" + plan, progressCallback);
    }

    /**
     * 生成后端代码（流式，防止长代码截断）
     */
    private String generateBackend(String prompt, String plan, String dbSchema, Consumer<String> progressCallback) {
        String systemPrompt = """
【角色】你是一个 Node.js 后端开发工程师。
【任务】根据用户需求、网站规划和数据库结构，生成完整的 Express 后端代码。
【输出格式】输出一个完整的 server.js 文件：
```
const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// 初始化数据库（数据库已由 database/init.sql 定义，直接读取执行）
const db = new Database(path.join(__dirname, 'data.db'));
db.pragma('journal_mode = WAL');
const initSQL = fs.readFileSync(path.join(__dirname, 'database', 'init.sql'), 'utf8');
db.exec(initSQL);

// RESTful API 路由
app.get('/api/xxx', (req, res) => { ... });
app.post('/api/xxx', (req, res) => { ... });
app.get('/api/xxx/:id', (req, res) => { ... });
app.put('/api/xxx/:id', (req, res) => { ... });
app.delete('/api/xxx/:id', (req, res) => { ... });

app.listen(PORT, () => {
  console.log('Server running at http://localhost:' + PORT);
});
```
【严格要求】
1. 为每张数据表提供完整的 CRUD API（GET 列表/GET 详情/POST 创建/PUT 更新/DELETE 删除）
2. 使用 better-sqlite3 同步 API，不使用 async/await
3. 所有 SQL 查询使用参数化（db.prepare().run() 或 db.prepare().get()）
4. API 返回 JSON 格式 { success: true, data: ... } 或 { success: false, error: "..." }
5. API 错误消息（error 字段）和代码注释必须使用简体中文
6. 静态文件从 public/ 目录提供
7. 【关键】建表 SQL 必须通过 fs.readFileSync 读取 database/init.sql 文件执行，严禁在 server.js 中内联任何 CREATE TABLE/INSERT 语句（SQL 只出现在 db.prepare() 的参数化查询中）
8. 代码必须完整，不得截断或省略
9. 只输出 JavaScript 代码，不要输出任何解释或额外文字，严禁使用 ``` 等 markdown 代码块围栏
""";

        String fullPrompt = systemPrompt + "\n\n" +
                "用户需求：" + prompt + "\n\n" +
                "网站规划：" + plan + "\n\n" +
                "数据库结构：\n```sql\n" + dbSchema + "\n```\n\n" +
                "请输出完整的 server.js 代码：";

        return chatModel.streamChat(fullPrompt, progressCallback);
    }

    private void createProjectStructure(String projectDir) throws IOException {
        Files.createDirectories(Path.of(projectDir, "public", "css"));
        Files.createDirectories(Path.of(projectDir, "public", "js"));
        Files.createDirectories(Path.of(projectDir, "public", "images"));
        Files.createDirectories(Path.of(projectDir, "database"));
    }

    private void writeFrontendFiles(String projectDir, String frontendCode) throws IOException {
        String[] sections = frontendCode.split("---FILE:");
        for (String section : sections) {
            if (section.isBlank()) continue;
            int newlineIdx = section.indexOf('\n');
            if (newlineIdx < 0) continue;
            String filePath = section.substring(0, newlineIdx).trim();
            String content = section.substring(newlineIdx + 1).trim();
            if (content.isEmpty()) continue;
            fileSystemTool.writeFile(projectDir, filePath, content);
        }
    }

    private void writeDatabaseFiles(String projectDir, String dbSchema) throws IOException {
        fileSystemTool.writeFile(projectDir, "database/init.sql", stripCodeFences(dbSchema));
    }

    private void writeBackendFiles(String projectDir, String backendCode) throws IOException {
        fileSystemTool.writeFile(projectDir, "server.js", stripCodeFences(backendCode));
    }

    /**
     * 剥离 AI 输出中误加的 markdown 代码块围栏（```lang ... ```），
     * 防止围栏混入文件内容导致 JS/SQL 语法错误
     */
    private String stripCodeFences(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            int fenceIdx = trimmed.lastIndexOf("```");
            trimmed = trimmed.substring(0, fenceIdx);
        }
        return trimmed.trim();
    }

    private void generateProjectConfig(String projectDir) throws IOException {
        String packageJson = packageTool.generatePackageJson(
                "generated-website",
                "AI generated website project"
        );
        fileSystemTool.writeFile(projectDir, "package.json", packageJson);
        fileSystemTool.writeFile(projectDir, ".gitignore", packageTool.generateGitignore());
        fileSystemTool.writeFile(projectDir, "README.md", generateReadme());
    }

    private String generateReadme() {
        return """
# Generated Website

This project was automatically generated by AI.

## Quick Start

```bash
npm install
npm start
```

Then open http://localhost:3000 in your browser.

## Project Structure

```
├── public/          # Static files (HTML, CSS, JS)
│   ├── css/
│   ├── js/
│   └── images/
├── database/        # SQLite schema
├── server.js        # Express server
└── package.json     # Dependencies
```
""";
    }
}