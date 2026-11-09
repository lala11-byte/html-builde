package com.htmlbuilder.generator.agent.graph.node;

import com.htmlbuilder.generator.agent.graph.CodeIOUtil;
import com.htmlbuilder.generator.agent.graph.GenerationState;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 结构创建节点：创建项目目录骨架（public/css|js|images、database）。
 * - RESUME：public/ 已存在则跳过
 * - RESUME/ITERATE：收集已有代码上下文写入 state，供后续节点引用
 */
@Component
public class StructureCreationNode implements NodeAction<GenerationState> {

    private final ProgressRegistry progressRegistry;

    public StructureCreationNode(ProgressRegistry progressRegistry) {
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
        Path publicDir = Path.of(projectDir, "public");

        try {
            if ("RESUME".equals(mode) && Files.exists(publicDir)) {
                callback.accept("[文件] public/ 目录已存在，跳过目录创建");
            } else {
                callback.accept("[文件] 创建项目目录结构...");
                CodeIOUtil.createProjectStructure(projectDir);
                callback.accept("[文件] 目录结构创建完成");
            }

            // RESUME/ITERATE 收集已有代码上下文，供后续节点使用
            if ("RESUME".equals(mode) || "ITERATE".equals(mode)) {
                String existingCodeContext = CodeIOUtil.collectExistingCodeContext(projectDir);
                if (existingCodeContext != null && !existingCodeContext.isBlank()) {
                    return Map.of(GenerationState.EXISTING_CODE_CONTEXT, existingCodeContext);
                }
            }
            return Map.of();
        } catch (IOException e) {
            throw new RuntimeException("[文件] 目录创建失败: " + e.getMessage(), e);
        }
    }
}
