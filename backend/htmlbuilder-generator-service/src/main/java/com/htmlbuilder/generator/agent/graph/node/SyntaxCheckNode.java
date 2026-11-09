package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 语法校验节点：对 server.js 执行 node --check 语法校验。
 * - 校验通过：清空 SYNTAX_ERROR，路由进入下一阶段。
 * - 校验失败：累计 BACKEND_RETRY_COUNT，将语法错误追加到 CUSTOM_PROMPT，
 *   由 SyntaxRouter 决定重试（回到后端生成）或放弃。
 */
@Component
public class SyntaxCheckNode implements NodeAction<GenerationState> {

    private final AnthropicChatLanguageModel chatModel;
    private final ProgressRegistry progressRegistry;

    public SyntaxCheckNode(AnthropicChatLanguageModel chatModel, ProgressRegistry progressRegistry) {
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

        String projectDir = state.projectDir();
        String syntaxError = CodeIOUtil.checkServerJsSyntax(Path.of(projectDir));

        if (syntaxError == null) {
            callback.accept("[后端] server.js 语法校验通过");
            // 清空上一次遗留的语法错误，确保路由判定为通过
            Map<String, Object> result = new HashMap<>();
            result.put(GenerationState.SYNTAX_ERROR, null);
            return result;
        }

        int retryCount = state.backendRetryCount() + 1;
        callback.accept("[后端] server.js 语法校验失败（第" + retryCount + "次）: " + syntaxError);

        String existingCustomPrompt = state.customPrompt();
        String newCustomPrompt = (existingCustomPrompt != null ? existingCustomPrompt + "\n" : "")
                + "【重要】上次生成的 server.js 存在语法错误，请务必修复: " + syntaxError;

        return Map.of(
                GenerationState.BACKEND_RETRY_COUNT, retryCount,
                GenerationState.SYNTAX_ERROR, syntaxError,
                GenerationState.CUSTOM_PROMPT, newCustomPrompt
        );
    }
}
