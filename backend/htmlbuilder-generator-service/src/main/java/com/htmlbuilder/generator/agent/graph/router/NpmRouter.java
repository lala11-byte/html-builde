package com.htmlbuilder.generator.agent.graph.router;

import com.htmlbuilder.generator.agent.graph.GenerationState;
import org.bsc.langgraph4j.action.EdgeAction;
import org.springframework.stereotype.Component;

/**
 * npm install 条件路由：
 * - 安装成功（NPM_ERROR 为 null/空）→ code_review
 * - 失败且重试次数 < 3 → project_config（触发 AI 修复 package.json 后重试）
 * - 失败且重试次数 >= 3 → code_review（放弃，继续后续流程）
 */
@Component
public class NpmRouter implements EdgeAction<GenerationState> {

    private static final int MAX_RETRIES = 3;

    @Override
    public String apply(GenerationState state) {
        String npmError = state.npmError();
        int retryCount = state.npmRetryCount();

        if (npmError == null || npmError.isBlank()) {
            return "code_review";
        }
        if (retryCount < MAX_RETRIES) {
            return "project_config";
        }
        return "code_review";
    }
}