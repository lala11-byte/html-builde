package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.FastChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.agent.graph.PromptConstants;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 规划节点：基于用户需求和需求分析文档生成 JSON 格式的网站规划。
 * - GENERATE/ITERATE：调用 FastChat 生成规划
 * - RESUME：若所有产物已存在则跳过规划，否则生成
 * - ITERATE：将原始需求与追加需求合并后规划
 */
@Component
public class PlanningNode implements NodeAction<GenerationState> {

    private final FastChatLanguageModel fastModel;
    private final ProgressRegistry progressRegistry;

    public PlanningNode(FastChatLanguageModel fastModel, ProgressRegistry progressRegistry) {
        this.fastModel = fastModel;
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
        String additionalPrompt = state.additionalPrompt();
        String customPrompt = state.customPrompt();
        String requirementDoc = state.requirementDoc();

        // RESUME：所有产物已存在时跳过规划
        if ("RESUME".equals(mode)) {
            boolean hasReq = Files.exists(Path.of(projectDir, "requirement.json"));
            boolean hasPublic = Files.exists(Path.of(projectDir, "public"));
            boolean hasDb = Files.exists(Path.of(projectDir, "database", "init.sql"));
            boolean hasBackend = Files.exists(Path.of(projectDir, "server.js"));
            if (hasReq && hasPublic && hasDb && hasBackend) {
                callback.accept("[规划] 所有产物已存在，跳过规划");
                return Map.of();
            }
        }

        // ITERATE：合并原始需求与追加需求
        String effectivePrompt = prompt;
        if ("ITERATE".equals(mode) && additionalPrompt != null && !additionalPrompt.isBlank()) {
            effectivePrompt = prompt + "\n\n【追加需求】\n" + additionalPrompt;
        }

        callback.accept("[规划] AI 正在分析需求并规划网站结构...");
        String raw = fastModel.chat(CodeIOUtil.buildSystemPrompt(PromptConstants.PLANNING, customPrompt)
                + "\n\n用户需求：" + effectivePrompt
                + "\n\n需求分析文档：\n" + requirementDoc);
        String plan = CodeIOUtil.extractJson(CodeIOUtil.stripCodeFences(raw));
        callback.accept("[规划] 网站结构规划完成");

        return Map.of(GenerationState.PLAN, plan);
    }
}
