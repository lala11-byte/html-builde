package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.FastChatLanguageModel;
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
 * 需求分析节点：根据用户原始需求生成/加载/更新结构化需求分析文档（JSON）。
 * - GENERATE：基于原始需求生成需求文档
 * - RESUME：优先加载已有 requirement.json，缺失时按 GENERATE 流程生成
 * - ITERATE：若追加需求为修改/bug 类且已有文档则直接复用，否则基于追加需求更新文档
 */
@Component
public class RequirementAnalysisNode implements NodeAction<GenerationState> {

    private final AnthropicChatLanguageModel chatModel;
    private final FastChatLanguageModel fastModel;
    private final ProgressRegistry progressRegistry;

    public RequirementAnalysisNode(AnthropicChatLanguageModel chatModel,
                                    FastChatLanguageModel fastModel,
                                    ProgressRegistry progressRegistry) {
        this.chatModel = chatModel;
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
        String prompt = state.prompt();
        String additionalPrompt = state.additionalPrompt();
        String customPrompt = state.customPrompt();
        String projectDir = state.projectDir();

        Path reqPath = Path.of(projectDir, "requirement.json");
        boolean hasReq = Files.exists(reqPath);

        try {
            String requirementDoc;

            if ("RESUME".equals(mode)) {
                if (hasReq) {
                    callback.accept("[需求分析] 加载已有需求分析文档...");
                    requirementDoc = CodeIOUtil.readFile(projectDir, "requirement.json");
                } else {
                    callback.accept("[需求分析] AI 正在分析需求并生成需求分析文档...");
                    String raw = fastModel.chat(CodeIOUtil.buildSystemPrompt(
                            PromptConstants.REQUIREMENT_ANALYSIS, customPrompt)
                            + "\n\n用户原始需求：" + prompt);
                    requirementDoc = CodeIOUtil.extractJson(CodeIOUtil.stripCodeFences(raw));
                    CodeIOUtil.writeCodeFile(projectDir, "requirement.json", requirementDoc);
                    callback.accept("[需求分析] 需求分析文档生成完成");
                }
            } else if ("ITERATE".equals(mode)) {
                if (CodeIOUtil.shouldSkipRequirementDocUpdate(additionalPrompt) && hasReq) {
                    callback.accept("[需求分析] 追加需求为修改/bug修复类，跳过需求文档更新");
                    requirementDoc = CodeIOUtil.readFile(projectDir, "requirement.json");
                } else {
                    callback.accept("[需求分析] AI 正在基于追加需求更新需求分析文档...");
                    String existingReq = hasReq
                            ? CodeIOUtil.readFile(projectDir, "requirement.json") : "";
                    String existingCodeContext = CodeIOUtil.collectExistingCodeContext(projectDir);
                    String raw = fastModel.chat(CodeIOUtil.buildSystemPrompt(
                            PromptConstants.UPDATE_REQUIREMENT, customPrompt)
                            + "\n\n原始需求：" + prompt
                            + "\n\n追加需求：" + additionalPrompt
                            + "\n\n已有需求文档：\n" + existingReq
                            + "\n\n已有代码上下文：\n" + existingCodeContext);
                    requirementDoc = CodeIOUtil.extractJson(CodeIOUtil.stripCodeFences(raw));
                    CodeIOUtil.writeCodeFile(projectDir, "requirement.json", requirementDoc);
                    callback.accept("[需求分析] 需求分析文档已更新");
                }
            } else {
                // GENERATE
                callback.accept("[需求分析] AI 正在分析需求并生成需求分析文档...");
                String raw = fastModel.chat(CodeIOUtil.buildSystemPrompt(
                        PromptConstants.REQUIREMENT_ANALYSIS, customPrompt)
                        + "\n\n用户原始需求：" + prompt);
                requirementDoc = CodeIOUtil.extractJson(CodeIOUtil.stripCodeFences(raw));
                CodeIOUtil.writeCodeFile(projectDir, "requirement.json", requirementDoc);
                callback.accept("[需求分析] 需求分析文档生成完成");
            }

            return Map.of(GenerationState.REQUIREMENT_DOC, requirementDoc);
        } catch (IOException e) {
            throw new RuntimeException("[需求分析] 失败: " + e.getMessage(), e);
        }
    }
}
