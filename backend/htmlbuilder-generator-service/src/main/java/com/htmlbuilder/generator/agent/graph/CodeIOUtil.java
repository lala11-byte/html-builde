package com.htmlbuilder.generator.agent.graph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 静态工具方法：供所有生成节点复用
 */
public final class CodeIOUtil {

    private CodeIOUtil() {}

    public static void createProjectStructure(String projectDir) throws IOException {
        Files.createDirectories(Path.of(projectDir, "public", "css"));
        Files.createDirectories(Path.of(projectDir, "public", "js"));
        Files.createDirectories(Path.of(projectDir, "public", "images"));
        Files.createDirectories(Path.of(projectDir, "database"));
    }

    public static void writeFrontendFiles(String projectDir, String frontendCode) throws IOException {
        frontendCode = stripCodeFences(frontendCode);
        String[] sections = frontendCode.split("---FILE:");
        for (String section : sections) {
            if (section.isBlank()) continue;
            int newlineIdx = section.indexOf('\n');
            if (newlineIdx < 0) continue;
            String filePath = section.substring(0, newlineIdx).trim();
            String content = section.substring(newlineIdx + 1).trim();
            if (content.isEmpty()) continue;
            writeCodeFile(projectDir, filePath, content);
        }
    }

    public static void writeDatabaseFiles(String projectDir, String dbSchema) throws IOException {
        writeCodeFile(projectDir, "database/init.sql", stripCodeFences(dbSchema));
    }

    public static void writeBackendFiles(String projectDir, String backendCode) throws IOException {
        writeCodeFile(projectDir, "server.js", stripCodeFences(backendCode));
    }

    public static void writeCodeFile(String projectDir, String filePath, String content) throws IOException {
        Path fullPath = Path.of(projectDir, filePath);
        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, content);
    }

    public static String readFile(String projectDir, String filePath) throws IOException {
        Path fullPath = Path.of(projectDir, filePath);
        if (!Files.exists(fullPath)) return null;
        return Files.readString(fullPath);
    }

    /**
     * 语法校验：对 server.js 执行 node --check，返回错误信息（null 表示语法正确）
     */
    public static String checkServerJsSyntax(Path projectDir) {
        try {
            Path serverJs = projectDir.resolve("server.js");
            if (!Files.exists(serverJs)) {
                return "server.js 不存在";
            }
            ProcessBuilder pb = new ProcessBuilder("node", "--check", "server.js");
            pb.directory(projectDir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder errors = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (errors.length() < 2000) {
                        errors.append(line).append('\n');
                    }
                }
            }
            if (!proc.waitFor(15, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "server.js 语法校验超时";
            }
            if (proc.exitValue() != 0) {
                return errors.toString().trim();
            }
            return null;
        } catch (Exception e) {
            return "语法校验执行失败: " + e.getMessage();
        }
    }

    public static boolean hasFilesInDir(Path dir) {
        try {
            if (!Files.exists(dir)) return false;
            try (var stream = Files.walk(dir)) {
                return stream.anyMatch(Files::isRegularFile);
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 收集已有项目文件作为上下文
     */
    public static String collectExistingCodeContext(String projectDir) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== 已有项目文件 ===\n");

        Path publicDir = Path.of(projectDir, "public");
        if (Files.exists(publicDir)) {
            try (var stream = Files.walk(publicDir)) {
                stream.filter(Files::isRegularFile)
                        .limit(30)
                        .forEach(f -> {
                            String rel = publicDir.relativize(f).toString().replace('\\', '/');
                            ctx.append("- public/").append(rel).append("\n");
                        });
            } catch (IOException ignored) {}
        }

        Path serverPath = Path.of(projectDir, "server.js");
        if (Files.exists(serverPath)) {
            ctx.append("\n=== 已有 server.js 内容（供参考，确保风格一致） ===\n");
            try {
                String content = Files.readString(serverPath);
                if (content.length() > 5000) {
                    content = content.substring(0, 5000) + "\n...（已截断）";
                }
                ctx.append(content);
            } catch (IOException ignored) {}
        }

        Path dbPath = Path.of(projectDir, "database", "init.sql");
        if (Files.exists(dbPath)) {
            ctx.append("\n\n=== 已有数据库结构（供参考） ===\n");
            try {
                ctx.append(Files.readString(dbPath));
            } catch (IOException ignored) {}
        }

        return ctx.toString();
    }

    /**
     * 剥离 AI 输出中误加的 markdown 代码块围栏
     */
    public static String stripCodeFences(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();

        int firstFence = trimmed.indexOf("```");
        if (firstFence >= 0) {
            int fenceEnd = trimmed.indexOf('\n', firstFence);
            if (fenceEnd >= 0) {
                trimmed = trimmed.substring(fenceEnd + 1);
            } else {
                trimmed = trimmed.substring(firstFence + 3);
            }
        }

        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence >= 0) {
            String afterFence = trimmed.substring(lastFence + 3).trim();
            if (afterFence.isEmpty()) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }

        return trimmed.trim();
    }

    /**
     * 从 AI 输出中提取纯 JSON 对象
     */
    public static String extractJson(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).trim();
        }
        return trimmed;
    }

    /**
     * 构建最终 system prompt：如果用户提供了自定义提示词，追加到基础提示词后面
     */
    public static String buildSystemPrompt(String basePrompt, String customPrompt) {
        if (customPrompt == null || customPrompt.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n【用户自定义提示】\n" + customPrompt;
    }

    /**
     * 判断追加需求是否需要更新需求文档
     */
    public static boolean shouldSkipRequirementDocUpdate(String additionalPrompt) {
        if (additionalPrompt == null || additionalPrompt.isBlank()) {
            return false;
        }
        String[] skipKeywords = {
                "bug", "Bug", "BUG", "错误", "报错", "异常", "修复", "修一下", "修改", "改一下",
                "样式", "样式调整", "按钮", "颜色", "字体", "布局", "间距",
                "文案", "文字", "提示", "弹窗", "对话框",
                "性能", "优化", "打开", "启动", "访问不了", "打不开", "404", "500",
                "没反应", "不显示", "显示不出来", "白屏", "卡死", "崩溃", "闪退"
        };
        for (String keyword : skipKeywords) {
            if (additionalPrompt.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常是否为网络连接类错误
     */
    public static boolean isNetworkError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("Connection reset") || msg.contains("Connection refused") ||
                msg.contains("ConnectException") || msg.contains("SocketException") ||
                msg.contains("timeout") || msg.contains("Timeout") ||
                msg.contains("UnknownHostException") || msg.contains("No route to host") ||
                msg.contains("Network is unreachable") || msg.contains("I/O error");
    }

    /**
     * 收集项目所有代码文件内容（供代码审查）
     */
    public static String collectAllCodeForReview(String projectDir) {
        StringBuilder allCode = new StringBuilder();
        Path serverPath = Path.of(projectDir, "server.js");
        if (Files.exists(serverPath)) {
            try {
                allCode.append("=== server.js ===\n");
                allCode.append(Files.readString(serverPath));
            } catch (IOException ignored) {}
        }
        Path publicDir = Path.of(projectDir, "public");
        if (Files.exists(publicDir)) {
            try (var stream = Files.walk(publicDir)) {
                stream.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(f -> {
                            try {
                                String rel = publicDir.relativize(f).toString().replace('\\', '/');
                                allCode.append("\n=== public/").append(rel).append(" ===\n");
                                String content = Files.readString(f);
                                if (content.length() > 8000) {
                                    content = content.substring(0, 8000) + "\n...（已截断）";
                                }
                                allCode.append(content);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
        Path dbPath = Path.of(projectDir, "database", "init.sql");
        if (Files.exists(dbPath)) {
            try {
                allCode.append("\n=== database/init.sql ===\n");
                allCode.append(Files.readString(dbPath));
            } catch (IOException ignored) {}
        }
        return allCode.toString();
    }

    /**
     * 收集当前 server.js 和前端代码（供 fixCodeOnError）
     */
    public static String collectAllCodeForFix(String projectDir) {
        StringBuilder existingCode = new StringBuilder();
        Path serverPath = Path.of(projectDir, "server.js");
        if (Files.exists(serverPath)) {
            try {
                existingCode.append("=== 当前 server.js ===\n");
                existingCode.append(Files.readString(serverPath));
            } catch (IOException ignored) {}
        }
        Path publicDir = Path.of(projectDir, "public");
        if (Files.exists(publicDir)) {
            try (var stream = Files.walk(publicDir)) {
                stream.filter(Files::isRegularFile)
                        .forEach(f -> {
                            try {
                                String rel = publicDir.relativize(f).toString().replace('\\', '/');
                                existingCode.append("\n=== 当前 public/").append(rel).append(" ===\n");
                                String content = Files.readString(f);
                                if (content.length() > 5000) {
                                    content = content.substring(0, 5000) + "\n...（已截断）";
                                }
                                existingCode.append(content);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
        return existingCode.toString();
    }
}
