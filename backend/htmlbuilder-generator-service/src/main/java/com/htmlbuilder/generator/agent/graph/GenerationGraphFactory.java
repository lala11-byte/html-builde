package com.htmlbuilder.generator.agent.graph;

import com.htmlbuilder.generator.agent.graph.node.*;
import com.htmlbuilder.generator.agent.graph.router.NpmRouter;
import com.htmlbuilder.generator.agent.graph.router.SyntaxRouter;
import com.htmlbuilder.generator.agent.trace.LangSmithTracer;
import com.htmlbuilder.generator.agent.trace.TraceRegistry;
import com.htmlbuilder.generator.agent.trace.TracingNodeAction;
import jakarta.annotation.PostConstruct;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 生成状态图工厂。
 * 构建完整的声明式生成流程 StateGraph 并 compile 为 CompiledGraph。
 *
 * 图结构：
 *   START → requirement_analysis → planning → structure_creation
 *   → frontend_generation → database_generation → backend_generation
 *   → syntax_check ─(SyntaxRouter)→ project_config / backend_generation(重试)
 *   → project_config → npm_install ─(NpmRouter)→ code_review / project_config(重试)
 *   → code_review → END
 */
@Component
public class GenerationGraphFactory {

    private final RequirementAnalysisNode requirementAnalysis;
    private final PlanningNode planning;
    private final StructureCreationNode structureCreation;
    private final FrontendGenerationNode frontendGeneration;
    private final DatabaseGenerationNode databaseGeneration;
    private final BackendGenerationNode backendGeneration;
    private final SyntaxCheckNode syntaxCheck;
    private final ProjectConfigNode projectConfig;
    private final NpmInstallNode npmInstall;
    private final CodeReviewNode codeReview;
    private final SyntaxRouter syntaxRouter;
    private final NpmRouter npmRouter;
    private final LangSmithTracer langSmithTracer;
    private final TraceRegistry traceRegistry;

    private CompiledGraph<GenerationState> graph;

    public GenerationGraphFactory(
            RequirementAnalysisNode requirementAnalysis,
            PlanningNode planning,
            StructureCreationNode structureCreation,
            FrontendGenerationNode frontendGeneration,
            DatabaseGenerationNode databaseGeneration,
            BackendGenerationNode backendGeneration,
            SyntaxCheckNode syntaxCheck,
            ProjectConfigNode projectConfig,
            NpmInstallNode npmInstall,
            CodeReviewNode codeReview,
            SyntaxRouter syntaxRouter,
            NpmRouter npmRouter,
            LangSmithTracer langSmithTracer,
            TraceRegistry traceRegistry) {
        this.requirementAnalysis = requirementAnalysis;
        this.planning = planning;
        this.structureCreation = structureCreation;
        this.frontendGeneration = frontendGeneration;
        this.databaseGeneration = databaseGeneration;
        this.backendGeneration = backendGeneration;
        this.syntaxCheck = syntaxCheck;
        this.projectConfig = projectConfig;
        this.npmInstall = npmInstall;
        this.codeReview = codeReview;
        this.syntaxRouter = syntaxRouter;
        this.npmRouter = npmRouter;
        this.langSmithTracer = langSmithTracer;
        this.traceRegistry = traceRegistry;
    }

    @PostConstruct
    public void init() throws GraphStateException {
        // 如果启用了 LangSmith 追踪，包装所有节点
        NodeAction reqAnaNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(requirementAnalysis, "requirement_analysis", langSmithTracer, traceRegistry)
                : requirementAnalysis;
        NodeAction planNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(planning, "planning", langSmithTracer, traceRegistry)
                : planning;
        NodeAction structNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(structureCreation, "structure_creation", langSmithTracer, traceRegistry)
                : structureCreation;
        NodeAction frontendNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(frontendGeneration, "frontend_generation", langSmithTracer, traceRegistry)
                : frontendGeneration;
        NodeAction dbNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(databaseGeneration, "database_generation", langSmithTracer, traceRegistry)
                : databaseGeneration;
        NodeAction backendNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(backendGeneration, "backend_generation", langSmithTracer, traceRegistry)
                : backendGeneration;
        NodeAction syntaxNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(syntaxCheck, "syntax_check", langSmithTracer, traceRegistry)
                : syntaxCheck;
        NodeAction cfgNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(projectConfig, "project_config", langSmithTracer, traceRegistry)
                : projectConfig;
        NodeAction npmNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(npmInstall, "npm_install", langSmithTracer, traceRegistry)
                : npmInstall;
        NodeAction reviewNode = langSmithTracer.isEnabled()
                ? TracingNodeAction.wrap(codeReview, "code_review", langSmithTracer, traceRegistry)
                : codeReview;

        graph = new StateGraph<>(GenerationState.SCHEMA, GenerationState::new)
                // --- 注册节点 ---
                .addNode("requirement_analysis", AsyncNodeAction.node_async(reqAnaNode))
                .addNode("planning", AsyncNodeAction.node_async(planNode))
                .addNode("structure_creation", AsyncNodeAction.node_async(structNode))
                .addNode("frontend_generation", AsyncNodeAction.node_async(frontendNode))
                .addNode("database_generation", AsyncNodeAction.node_async(dbNode))
                .addNode("backend_generation", AsyncNodeAction.node_async(backendNode))
                .addNode("syntax_check", AsyncNodeAction.node_async(syntaxNode))
                .addNode("project_config", AsyncNodeAction.node_async(cfgNode))
                .addNode("npm_install", AsyncNodeAction.node_async(npmNode))
                .addNode("code_review", AsyncNodeAction.node_async(reviewNode))

                // --- 主流程边 ---
                .addEdge(GraphDefinition.START, "requirement_analysis")
                .addEdge("requirement_analysis", "planning")
                .addEdge("planning", "structure_creation")
                .addEdge("structure_creation", "frontend_generation")
                .addEdge("frontend_generation", "database_generation")
                .addEdge("database_generation", "backend_generation")
                .addEdge("backend_generation", "syntax_check")

                // --- 语法校验条件路由 ---
                // route result → target node:
                //   "project_config"      → syntax passed or retries exhausted
                //   "backend_generation"  → retry backend generation
                .addConditionalEdges("syntax_check",
                        AsyncEdgeAction.edge_async(syntaxRouter),
                        Map.of("project_config", "project_config",
                               "backend_generation", "backend_generation"))

                // --- project_config → npm_install ---
                .addEdge("project_config", "npm_install")

                // --- npm install 条件路由 ---
                // route result → target node:
                //   "code_review"    → npm install success or retries exhausted
                //   "project_config" → retry (AI fix package.json then re-install)
                .addConditionalEdges("npm_install",
                        AsyncEdgeAction.edge_async(npmRouter),
                        Map.of("code_review", "code_review",
                               "project_config", "project_config"))

                // --- 结束 ---
                .addEdge("code_review", GraphDefinition.END)

                .compile();
    }

    /**
     * 获取已编译的生成状态图。
     */
    public CompiledGraph<GenerationState> getGraph() {
        return graph;
    }
}