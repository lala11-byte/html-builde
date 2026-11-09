package com.htmlbuilder.generator.agent.graph.router;

import com.htmlbuilder.generator.agent.graph.GenerationState;
import org.bsc.langgraph4j.action.EdgeAction;
import org.springframework.stereotype.Component;

/**
 * 语法校验条件路由：
 * - 校验通过（SYNTAX_ERROR 为 null/空）→ project_config
 * - 失败且重试次数 < 3 → backend_generation（重试）
 * - 失败且重试次数 >= 3 → project_config（放弃，继续后续流程）
 */
@Component
public class SyntaxRouter implements EdgeAction<GenerationState> {

    private static final int MAX_RETRIES = 3;

    @Override
    public String apply(GenerationState state) {
        String syntaxError = state.syntaxError();
        int retryCount = state.backendRetryCount();

        if (syntaxError == null || syntaxError.isBlank()) {
            return "project_config";
        }
        if (retryCount < MAX_RETRIES) {
            return "backend_generation";
        }
        return "project_config";
    }
}