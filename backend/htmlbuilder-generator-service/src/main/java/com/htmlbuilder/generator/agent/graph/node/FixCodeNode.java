package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 代码修复节点：根据运行错误日志，AI 修复 server.js 及前端代码。
 * - 图节点 apply：从 state.syntaxError() 取错误日志，修复后写回文件。
 * - 同时暴露 public fix() 方法，供 GeneratorService.autoStartWithFix 直接调用（绕过图）。
 */
@Component
public class FixCodeNode implements NodeAction<GenerationState> {

    private final AnthropicChatLanguageModel chatModel;
    private final ProgressRegistry progressRegistry;

    public FixCodeNode(AnthropicChatLanguageModel chatModel, ProgressRegistry progressRegistry) {
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
        fix(state.projectDir(), state.syntaxError(), callback);
        return Map.of();
    }

    /**
     * 根据错误日志修复代码，可直接被外部服务调用（无需经过图）。
     *
     * @param projectDir 项目目录
     * @param errorLog   错误日志
     * @param callback   进度回调
     */
    public void fix(String projectDir, String errorLog, Consumer<String> callback) {
        String error = errorLog != null ? errorLog : "";
        callback.accept("[修复] AI 正在分析运行错误并修复代码...");

        // 收集当前的 server.js 和前端代码
        String existingCode = CodeIOUtil.collectAllCodeForFix(projectDir);

        String fullPrompt = PromptConstants.FIX_CODE + "\n\n" +
                "错误日志：\n" + error + "\n\n" +
                "已有代码：\n" + existingCode + "\n\n" +
                "请输出修复后的代码：";

        String fixedCode = chatModel.streamChat(fullPrompt, callback);

        // 写入修复后的文件
        String cleaned = CodeIOUtil.stripCodeFences(fixedCode);
        String[] sections = cleaned.split("---FILE:");
        for (String section : sections) {
            if (section.isBlank()) continue;
            int newlineIdx = section.indexOf('\n');
            if (newlineIdx < 0) continue;
            String filePath = section.substring(0, newlineIdx).trim();
            String content = section.substring(newlineIdx + 1).trim();
            if (content.isEmpty()) continue;
            try {
                CodeIOUtil.writeCodeFile(projectDir, filePath, content);
                callback.accept("[修复] 已更新文件: " + filePath);
            } catch (IOException e) {
                callback.accept("[修复] 写入文件失败: " + e.getMessage());
                throw new RuntimeException("修复代码写入失败: " + e.getMessage(), e);
            }
        }

        callback.accept("[修复] AI 修复完成，准备重新启动");
    }
}
