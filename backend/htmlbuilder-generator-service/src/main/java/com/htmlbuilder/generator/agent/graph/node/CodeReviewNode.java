package com.htmlbuilder.generator.agent.graph.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import com.htmlbuilder.generator.agent.tool.PackageTool;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码审查节点：AI 主动审查并修复生成代码的潜在问题。
 * - 阶段 0：runAutomatedPreChecks 程序化预检查（空函数体、未闭合大括号、假 bcrypt、对话式内容、外部 fetch 等）
 * - 阶段 1：收集全部代码交给 AI 审查修复，写回文件
 * - 审查后重新生成 package.json（依赖可能新增）
 * - 阶段 2：后检查验证 AI 修复是否引入新问题
 */
@Component
public class CodeReviewNode implements NodeAction<GenerationState> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AnthropicChatLanguageModel chatModel;
    private final PackageTool packageTool;
    private final ProgressRegistry progressRegistry;

    public CodeReviewNode(AnthropicChatLanguageModel chatModel, PackageTool packageTool, ProgressRegistry progressRegistry) {
        this.chatModel = chatModel;
        this.packageTool = packageTool;
        this.progressRegistry = progressRegistry;
    }

    @Override
    public Map<String, Object> apply(GenerationState state) {
        Long taskId = state.taskId();
        Consumer<String> callback = progressRegistry.get(taskId);
        if (callback == null) {
            callback = s -> {};
        }

        String projectDir = state.projectDir();

        // --- 阶段 0：自动化预检查（无需 AI，快速发现硬性问题）---
        List<String> preCheckWarnings = runAutomatedPreChecks(projectDir);
        if (!preCheckWarnings.isEmpty()) {
            callback.accept("[审查-预检] 发现 " + preCheckWarnings.size() + " 个问题：");
            for (String warning : preCheckWarnings) {
                callback.accept("[审查-预检] " + warning);
            }
        } else {
            callback.accept("[审查-预检] 自动化检查通过，未发现硬性问题");
        }

        callback.accept("[审查] AI 正在主动审查代码质量并修复问题...");

        // 收集所有项目文件
        String allCode = CodeIOUtil.collectAllCodeForReview(projectDir);

        // 把预检查警告注入到 prompt 中，让 AI 重点关注
        String preCheckContext = "";
        if (!preCheckWarnings.isEmpty()) {
            preCheckContext = "\n【自动化预检查发现的问题，请务必修复】\n";
            for (String w : preCheckWarnings) {
                preCheckContext += "- " + w + "\n";
            }
        }

        String fullPrompt = PromptConstants.CODE_REVIEW + "\n\n" +
                preCheckContext +
                "以下是要审查的完整项目代码：\n\n" + allCode + "\n\n" +
                "请输出审查修复后的代码：";

        String reviewedCode = chatModel.streamChat(fullPrompt, callback);

        // 写入修复后的文件
        String cleaned = CodeIOUtil.stripCodeFences(reviewedCode);
        String[] sections = cleaned.split("---FILE:");
        int fixedCount = 0;
        for (String section : sections) {
            if (section.isBlank()) continue;
            int newlineIdx = section.indexOf('\n');
            if (newlineIdx < 0) continue;
            String filePath = section.substring(0, newlineIdx).trim();
            String content = section.substring(newlineIdx + 1).trim();
            if (content.isEmpty()) continue;
            try {
                CodeIOUtil.writeCodeFile(projectDir, filePath, content);
                fixedCount++;
                callback.accept("[审查] 已审查并更新文件: " + filePath);
            } catch (IOException e) {
                callback.accept("[审查] 写入文件失败: " + e.getMessage());
            }
        }

        callback.accept("[审查] 代码审查完成，共处理 " + fixedCount + " 个文件");

        // 审查后重新生成 package.json（可能新增了 require 依赖）
        try {
            regeneratePackageConfig(projectDir);
            callback.accept("[审查] 已同步更新 package.json 依赖列表");
        } catch (IOException e) {
            callback.accept("[审查] 更新 package.json 失败: " + e.getMessage());
        }

        // --- 阶段 2：自动化后检查，验证 AI 修复是否引入新问题 ---
        List<String> postCheckWarnings = runAutomatedPreChecks(projectDir);
        if (!postCheckWarnings.isEmpty()) {
            callback.accept("[审查-后检] 警告：修复后仍存在 " + postCheckWarnings.size() + " 个问题：");
            for (String warning : postCheckWarnings) {
                callback.accept("[审查-后检] " + warning);
            }
        } else {
            callback.accept("[审查-后检] 自动化后检查通过");
        }

        return Map.of();
    }

    /**
     * 审查后重新生成 package.json / .gitignore / README（依赖可能新增）
     */
    private void regeneratePackageConfig(String projectDir) throws IOException {
        String serverJsContent = CodeIOUtil.readFile(projectDir, "server.js");
        String packageJson = packageTool.generatePackageJson(
                "generated-website",
                "AI generated website project",
                serverJsContent
        );
        CodeIOUtil.writeCodeFile(projectDir, "package.json", packageJson);
        CodeIOUtil.writeCodeFile(projectDir, ".gitignore", packageTool.generateGitignore());
    }

    /**
     * 自动化预检查：在 AI 审查之前，用程序化方式快速检测硬性问题
     */
    private List<String> runAutomatedPreChecks(String projectDir) {
        List<String> warnings = new ArrayList<>();

        // 1. 检查 package.json 是否完整 JSON
        Path packageJsonPath = Path.of(projectDir, "package.json");
        if (Files.exists(packageJsonPath)) {
            try {
                String pkgContent = Files.readString(packageJsonPath);
                objectMapper.readTree(pkgContent);
            } catch (Exception e) {
                warnings.add("[package.json] JSON 格式错误或截断: " + e.getMessage());
            }
        }

        // 2. 检查 app.js 中是否有空函数体（占位符函数）
        Path appJsPath = Path.of(projectDir, "public", "js", "app.js");
        if (Files.exists(appJsPath)) {
            try {
                String jsContent = Files.readString(appJsPath);
                // 检测空函数体模式：function xxx() { } 或 function xxx() {\n}
                Pattern emptyFuncPattern = Pattern.compile(
                        "function\\s+\\w+\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
                Matcher matcher = emptyFuncPattern.matcher(jsContent);
                List<String> emptyFuncs = new ArrayList<>();
                while (matcher.find()) {
                    emptyFuncs.add(matcher.group().trim());
                }
                if (!emptyFuncs.isEmpty()) {
                    warnings.add("[app.js] 发现 " + emptyFuncs.size() + " 个空函数体（占位符），函数无实际逻辑: " +
                            String.join(", ", emptyFuncs.subList(0, Math.min(5, emptyFuncs.size()))));
                }

                // 检测 JS 文件是否截断（末尾是否有未闭合的大括号）
                int openBraces = countUnclosedBraces(jsContent);
                if (openBraces > 0) {
                    warnings.add("[app.js] JS 代码可能截断：未闭合的大括号多出 " + openBraces + " 个");
                }
            } catch (IOException ignored) {}
        }

        // 3. 检查 init.sql 中密码字段是否为有效 bcrypt hash
        Path initSqlPath = Path.of(projectDir, "database", "init.sql");
        if (Files.exists(initSqlPath)) {
            try {
                String sqlContent = Files.readString(initSqlPath);
                // 检查是否有 bcrypt 相关的依赖引入
                Path serverJsPath = Path.of(projectDir, "server.js");
                boolean usesBcrypt = false;
                if (Files.exists(serverJsPath)) {
                    String serverContent = Files.readString(serverJsPath);
                    usesBcrypt = serverContent.contains("bcrypt") || serverContent.contains("bcryptjs");
                }
                if (usesBcrypt) {
                    // 查找所有 INSERT 语句中的密码值
                    Pattern pwdPattern = Pattern.compile(
                            "password[^']*'([^']*)'", Pattern.CASE_INSENSITIVE);
                    Matcher pwdMatcher = pwdPattern.matcher(sqlContent);
                    List<String> fakePasswords = new ArrayList<>();
                    while (pwdMatcher.find()) {
                        String pwd = pwdMatcher.group(1);
                        if (!pwd.startsWith("$2a$") && !pwd.startsWith("$2b$")) {
                            fakePasswords.add(pwd);
                        }
                    }
                    if (!fakePasswords.isEmpty()) {
                        warnings.add("[init.sql] 种子数据密码不是有效 bcrypt hash（应为 $2a$ 开头），疑似明文假 hash: " +
                                String.join(", ", fakePasswords.subList(0, Math.min(5, fakePasswords.size()))));
                    }
                }
            } catch (IOException ignored) {}
        }

        // 4. 检查 server.js 是否截断（末尾是否有未闭合的括号/大括号）
        Path serverJsPath = Path.of(projectDir, "server.js");
        if (Files.exists(serverJsPath)) {
            try {
                String serverContent = Files.readString(serverJsPath);
                int openBraces = countUnclosedBraces(serverContent);
                if (openBraces > 0) {
                    warnings.add("[server.js] 代码可能截断：未闭合的大括号多出 " + openBraces + " 个");
                }
            } catch (IOException ignored) {}
        }

        // 5. 检查代码中是否夹杂了对话式内容
        Path appJsPathForDialogue = Path.of(projectDir, "public", "js", "app.js");
        if (Files.exists(appJsPathForDialogue)) {
            try {
                String jsContent = Files.readString(appJsPathForDialogue);
                String[] dialoguePatterns = {
                        "好的，", "这是你的", "这是您的", "我来帮你", "我帮你",
                        "请问", "你可以", "你需要", "让我来"
                };
                List<String> foundDialogue = new ArrayList<>();
                for (String pattern : dialoguePatterns) {
                    if (jsContent.contains(pattern)) {
                        foundDialogue.add(pattern);
                    }
                }
                if (!foundDialogue.isEmpty()) {
                    warnings.add("[app.js] 代码中夹杂数对话式内容: " + String.join(", ", foundDialogue));
                }
            } catch (IOException ignored) {}
        }

        // 6. 检查前端 fetch 请求是否指向了外部地址或对话系统
        Path appJsPathForFetch = Path.of(projectDir, "public", "js", "app.js");
        if (Files.exists(appJsPathForFetch)) {
            try {
                String jsContent = Files.readString(appJsPathForFetch);
                Pattern fetchUrlPattern = Pattern.compile(
                        "fetch\\s*\\(\\s*['\"](https?://[^'\"]+)['\"]");
                Matcher fetchMatcher = fetchUrlPattern.matcher(jsContent);
                List<String> externalUrls = new ArrayList<>();
                while (fetchMatcher.find()) {
                    String url = fetchMatcher.group(1);
                    // 排除 localhost 和 127.0.0.1
                    if (!url.contains("localhost") && !url.contains("127.0.0.1")) {
                        externalUrls.add(url);
                    }
                }
                if (!externalUrls.isEmpty()) {
                    warnings.add("[app.js] 发现外部 fetch 请求（应为相对路径）: " +
                            String.join(", ", externalUrls.subList(0, Math.min(5, externalUrls.size()))));
                }
            } catch (IOException ignored) {}
        }

        return warnings;
    }

    /**
     * 逐字符统计未闭合的大括号数量，跳过字符串/模板字符串中的大括号
     */
    private int countUnclosedBraces(String content) {
        int openBraces = 0;
        boolean inString = false;
        boolean inTemplate = false;
        char stringChar = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == stringChar) inString = false;
                continue;
            }
            if (inTemplate) {
                if (c == '\\') { i++; continue; }
                if (c == '`') inTemplate = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; stringChar = c; continue; }
            if (c == '`') { inTemplate = true; continue; }
            if (c == '{') openBraces++;
            if (c == '}') openBraces--;
        }
        return openBraces;
    }
}
