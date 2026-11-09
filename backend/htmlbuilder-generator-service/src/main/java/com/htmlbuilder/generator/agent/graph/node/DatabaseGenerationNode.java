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
 * 数据库生成节点：流式生成 SQLite 初始化脚本（database/init.sql）。
 * - GENERATE：使用 DATABASE_GENERATE 提示词
 * - RESUME：database/init.sql 已存在则跳过，否则用 DATABASE_RESUME + 已有代码上下文
 * - ITERATE：用 DATABASE_ITERATE + 已有代码上下文
 */
@Component
public class DatabaseGenerationNode implements NodeAction<GenerationState> {

    private final AnthropicChatLanguageModel chatModel;
    private final ProgressRegistry progressRegistry;

    public DatabaseGenerationNode(AnthropicChatLanguageModel chatModel, ProgressRegistry progressRegistry) {
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
        String existingCodeContext = state.existingCodeContext();

        // RESUME：database/init.sql 已存在则跳过
        if ("RESUME".equals(mode)) {
            if (Files.exists(Path.of(projectDir, "database", "init.sql"))) {
                callback.accept("[数据库] 数据库结构已存在，跳过生成");
                return Map.of();
            }
        }

        String systemPrompt;
        if ("RESUME".equals(mode)) {
            systemPrompt = PromptConstants.DATABASE_RESUME;
        } else if ("ITERATE".equals(mode)) {
            systemPrompt = PromptConstants.DATABASE_ITERATE;
        } else {
            systemPrompt = PromptConstants.DATABASE_GENERATE;
        }

        StringBuilder fullPrompt = new StringBuilder(
                CodeIOUtil.buildSystemPrompt(systemPrompt, customPrompt));
        fullPrompt.append("\n\n用户需求：").append(prompt);
        fullPrompt.append("\n\n需求分析文档：\n").append(requirementDoc);
        fullPrompt.append("\n\n网站规划：").append(plan);
        if ("RESUME".equals(mode) || "ITERATE".equals(mode)) {
            fullPrompt.append("\n\n已有代码上下文：\n").append(existingCodeContext);
        }

        callback.accept("[数据库] AI 正在设计数据库结构（流式传输）...");
        String dbSchema = chatModel.streamChat(fullPrompt.toString(), callback);
        try {
            CodeIOUtil.writeDatabaseFiles(projectDir, dbSchema);
        } catch (IOException e) {
            throw new RuntimeException("[数据库] 文件写入失败: " + e.getMessage(), e);
        }
        callback.accept("[数据库] 数据库设计完成");

        return Map.of(GenerationState.DB_SCHEMA, dbSchema);
    }
}
