package com.htmlbuilder.generator.agent.tool;

import java.util.*;
import java.util.regex.*;

public class PackageTool {

    public String generatePackageJson(String projectName, String description) {
        return generatePackageJson(projectName, description, null);
    }

    /**
     * 生成 package.json，可根据 server.js 内容动态扫描 require() 依赖
     */
    public String generatePackageJson(String projectName, String description, String serverJsContent) {
        // 使用 LinkedHashMap 按包名去重，核心依赖优先
        Map<String, String> deps = new LinkedHashMap<>();
        deps.put("express", "^4.18.2");
        deps.put("better-sqlite3", "^11.0.0");
        deps.put("cors", "^2.8.5");

        // 动态扫描 server.js 中的 require() 调用（不覆盖已有的核心依赖版本）
        if (serverJsContent != null && !serverJsContent.isEmpty()) {
            Map<String, String> scanned = scanRequires(serverJsContent);
            for (Map.Entry<String, String> entry : scanned.entrySet()) {
                String pkgName = entry.getKey();
                if (isBuiltinModule(pkgName)) continue;
                deps.putIfAbsent(pkgName, entry.getValue());
            }
        }

        List<String> depLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : deps.entrySet()) {
            depLines.add("\"" + entry.getKey() + "\": \"" + entry.getValue() + "\"");
        }
        String depsJson = String.join(",\n    ", depLines);

        return """
{
  "name": "%s",
  "version": "1.0.0",
  "description": "%s",
  "main": "server.js",
  "scripts": {
    "start": "node server.js",
    "dev": "node server.js"
  },
  "dependencies": {
    %s
  }
}
""".formatted(projectName, description, depsJson);
    }

    /**
     * 扫描 JavaScript 代码中的 require('xxx') 调用，返回包名→版本号映射
     */
    public Map<String, String> scanRequires(String jsCode) {
        Map<String, String> result = new LinkedHashMap<>();
        // 匹配 require('xxx') 或 require("xxx")
        Pattern pattern = Pattern.compile("require\\(['\"]([^'\"]+)['\"]\\)");
        Matcher matcher = pattern.matcher(jsCode);
        while (matcher.find()) {
            String moduleName = matcher.group(1);
            // 跳过相对路径（如 './xxx', '../xxx'）
            if (moduleName.startsWith(".") || moduleName.startsWith("/")) continue;
            if (!result.containsKey(moduleName)) {
                result.put(moduleName, resolveVersion(moduleName));
            }
        }
        return result;
    }

    private boolean isBuiltinModule(String name) {
        return Set.of(
            "fs", "path", "http", "https", "url", "querystring", "crypto",
            "stream", "util", "os", "events", "buffer", "zlib", "net",
            "dns", "tls", "child_process", "cluster", "assert", "readline",
            "vm", "v8", "worker_threads", "perf_hooks", "process",
            "timers", "tty", "dgram", "domain", "punycode", "string_decoder",
            "module", "console", "constants", "inspector", "trace_events"
        ).contains(name);
    }

    private String resolveVersion(String pkgName) {
        return switch (pkgName) {
            case "jsonwebtoken" -> "^9.0.0";
            case "bcrypt" -> "^5.1.0";
            case "bcryptjs" -> "^2.4.3";
            case "multer" -> "^1.4.5-lts.1";
            case "uuid" -> "^9.0.0";
            case "dotenv" -> "^16.3.0";
            case "axios" -> "^1.6.0";
            case "body-parser" -> "^1.20.2";
            case "morgan" -> "^1.10.0";
            case "helmet" -> "^7.1.0";
            case "express-rate-limit" -> "^7.1.0";
            case "express-validator" -> "^7.0.0";
            case "cookie-parser" -> "^1.4.6";
            case "compression" -> "^1.7.4";
            case "socket.io" -> "^4.7.0";
            case "nodemailer" -> "^6.9.0";
            case "xlsx" -> "^0.18.0";
            case "csv-parser" -> "^3.0.0";
            case "svg-captcha" -> "^1.5.0";
            case "express-session" -> "^1.17.3";
            case "connect-flash" -> "^0.1.1";
            case "method-override" -> "^3.0.0";
            case "ejs" -> "^3.1.9";
            default -> "^1.0.0";  // fallback
        };
    }

    public String generateServerJs(String projectName, String[] routes, String dbSchema) {
        StringBuilder sb = new StringBuilder();
        sb.append("const express = require('express');\n");
        sb.append("const cors = require('cors');\n");
        sb.append("const Database = require('better-sqlite3');\n");
        sb.append("const path = require('path');\n\n");
        sb.append("const app = express();\n");
        sb.append("app.use(cors());\n");
        sb.append("app.use(express.json());\n");
        sb.append("app.use(express.static(path.join(__dirname, 'public')));\n\n");
        sb.append("// Initialize database\n");
        sb.append("const db = new Database(path.join(__dirname, 'data.db'));\n");
        sb.append("db.exec(`").append(dbSchema).append("`);\n\n");
        for (String route : routes) {
            sb.append(route).append("\n");
        }
        sb.append("\nconst PORT = process.env.PORT || 3000;\n");
        sb.append("app.listen(PORT, () => {\n");
        sb.append("  console.log(`Server running at http://localhost:${PORT}`);\n");
        sb.append("});\n");
        return sb.toString();
    }

    public String generateStartScript() {
        return "#!/bin/bash\nnpm install && npm start\n";
    }

    public String generateGitignore() {
        return """
node_modules/
data.db
.env
""";
    }
}