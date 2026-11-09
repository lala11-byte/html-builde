package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 后端生成节点：流式生成 Express server.js，并初始化后端重试计数。
 * - GENERATE：使用 BACKEND_GENERATE 提示词
 * - RESUME：server.js 已存在则跳过，否则用 BACKEND_RESUME + 已有代码上下文
 * - ITERATE：用 BACKEND_ITERATE + 已有代码上下文
 * 完整 prompt 将 dbSchema 包裹在 ```sql 代码块中传入。
 */
@Component
public class BackendGenerationNode implements NodeAction<GenerationState> {

    private final AnthropicChatLanguageModel chatModel;
    private final ProgressRegistry progressRegistry;

    public BackendGenerationNode(AnthropicChatLanguageModel chatModel, ProgressRegistry progressRegistry) {
        this.chatModel = chatModel;
        this.progressRegistry = progressRegistry;
    }

    @Override
    public Map<String, Object> apply(GenerationState state) {
        Long taskId = state.taskId();
        Consumer<String> callback = progressRegistry.get(taskId);
        if (callback == null) {
            callback = s -> {};
        }

        String mode = state.mode();
        String projectDir = state.projectDir();
        String prompt = state.prompt();
        String customPrompt = state.customPrompt();
        String requirementDoc = state.requirementDoc();
        String plan = state.plan();
        String dbSchema = state.dbSchema();
        String existingCodeContext = state.existingCodeContext();

        // RESUME：server.js 已存在则跳过
        if ("RESUME".equals(mode)) {
            if (Files.exists(Path.of(projectDir, "server.js"))) {
                callback.accept("[后端] 后端代码已存在，跳过生成");
                return Map.of();
            }
        }

        String systemPrompt;
        if ("RESUME".equals(mode)) {
            systemPrompt = PromptConstants.BACKEND_RESUME;
        } else if ("ITERATE".equals(mode)) {
            systemPrompt = PromptConstants.BACKEND_ITERATE;
        } else {
            systemPrompt = PromptConstants.BACKEND_GENERATE;
        }

        StringBuilder fullPrompt = new StringBuilder(
                CodeIOUtil.buildSystemPrompt(systemPrompt, customPrompt));
        fullPrompt.append("\n\n用户需求：").append(prompt);
        fullPrompt.append("\n\n需求分析文档：\n").append(requirementDoc);
        fullPrompt.append("\n\n网站规划：").append(plan);
        fullPrompt.append("\n\n数据库结构：\n```sql\n").append(dbSchema).append("\n```");
        if ("RESUME".equals(mode) || "ITERATE".equals(mode)) {
            fullPrompt.append("\n\n已有代码上下文：\n").append(existingCodeContext);
        }
        fullPrompt.append("\n\n请输出完整的 server.js 代码：");

        callback.accept("[后端] AI 正在生成后端 API 代码（流式传输）...");
        String backendCode = chatModel.streamChat(fullPrompt.toString(), callback);
        try {
            CodeIOUtil.writeBackendFiles(projectDir, backendCode);
        } catch (IOException e) {
            throw new RuntimeException("[后端] 文件写入失败: " + e.getMessage(), e);
        }
        callback.accept("[后端] 后端 API 生成完成");

        // 初始化后端重试计数，供后续语法/运行时重试节点使用
        return Map.of(
                GenerationState.BACKEND_CODE, backendCode,
                GenerationState.BACKEND_RETRY_COUNT, 0
        );
    }
}
