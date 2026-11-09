package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 前端生成节点：流式生成 HTML/CSS/JS 前端代码。
 * - GENERATE：使用 FRONTEND_GENERATE 提示词
 * - RESUME：public/ 已有文件则跳过，否则用 FRONTEND_RESUME + 已有代码上下文
 * - ITERATE：用 FRONTEND_ITERATE + 已有代码上下文
 */
@Component
public class FrontendGenerationNode implements NodeAction<GenerationState> {

    private final AnthropicChatLanguageModel chatModel;
    private final ProgressRegistry progressRegistry;

    public FrontendGenerationNode(AnthropicChatLanguageModel chatModel, ProgressRegistry progressRegistry) {
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

        // RESUME：public/ 已有文件则跳过
        if ("RESUME".equals(mode)) {
            Path publicDir = Path.of(projectDir, "public");
            if (CodeIOUtil.hasFilesInDir(publicDir)) {
                callback.accept("[前端] 前端代码已存在，跳过生成");
                return Map.of();
            }
        }

        String systemPrompt;
        if ("RESUME".equals(mode)) {
            systemPrompt = PromptConstants.FRONTEND_RESUME;
        } else if ("ITERATE".equals(mode)) {
            systemPrompt = PromptConstants.FRONTEND_ITERATE;
        } else {
            systemPrompt = PromptConstants.FRONTEND_GENERATE;
        }

        StringBuilder fullPrompt = new StringBuilder(
                CodeIOUtil.buildSystemPrompt(systemPrompt, customPrompt));
        fullPrompt.append("\n\n用户需求：").append(prompt);
        fullPrompt.append("\n\n需求分析文档：\n").append(requirementDoc);
        fullPrompt.append("\n\n网站规划：").append(plan);
        if ("RESUME".equals(mode) || "ITERATE".equals(mode)) {
            fullPrompt.append("\n\n已有代码上下文：\n").append(existingCodeContext);
        }

        callback.accept("[前端] AI 正在生成前端页面代码（流式传输，防止截断）...");
        String frontendCode = chatModel.streamChat(fullPrompt.toString(), callback);
        try {
            CodeIOUtil.writeFrontendFiles(projectDir, frontendCode);
        } catch (IOException e) {
            throw new RuntimeException("[前端] 文件写入失败: " + e.getMessage(), e);
        }
        callback.accept("[前端] 前端页面生成完成");

        return Map.of(GenerationState.FRONTEND_CODE, frontendCode);
    }
}
