package com.htmlbuilder.generator.agent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.List;
import java.util.Map;

/**
 * 生成流程共享状态
 * 所有值为 String/Long/Integer 等原生可序列化类型，progressCallback 不在此 state 中
 */
public class GenerationState extends AgentState {

    // 模式与输入
    public static final String MODE = "mode";                    // "GENERATE"|"RESUME"|"ITERATE"
    public static final String TASK_ID = "taskId";               // Long
    public static final String PROMPT = "prompt";                // 原始需求
    public static final String ADDITIONAL_PROMPT = "additionalPrompt"; // 迭代追加需求
    public static final String CUSTOM_PROMPT = "customPrompt";   // 自定义提示词（会被语法重试追加）

    // 中间产物
    public static final String PROJECT_DIR = "projectDir";
    public static final String REQUIREMENT_DOC = "requirementDoc";
    public static final String PLAN = "plan";
    public static final String FRONTEND_CODE = "frontendCode";
    public static final String DB_SCHEMA = "dbSchema";
    public static final String BACKEND_CODE = "backendCode";
    public static final String EXISTING_CODE_CONTEXT = "existingCodeContext";

    // 重试控制
    public static final String BACKEND_RETRY_COUNT = "backendRetryCount"; // Integer
    public static final String SYNTAX_ERROR = "syntaxError";     // 末次语法错误
    public static final String NPM_ERROR = "npmError";
    public static final String NPM_RETRY_COUNT = "npmRetryCount";

    // SCHEMA: 全部覆盖写 reducer
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(MODE, Channels.base(() -> "")),
            Map.entry(TASK_ID, Channels.base(() -> 0L)),
            Map.entry(PROMPT, Channels.base(() -> "")),
            Map.entry(ADDITIONAL_PROMPT, Channels.base(() -> "")),
            Map.entry(CUSTOM_PROMPT, Channels.base(() -> "")),
            Map.entry(PROJECT_DIR, Channels.base(() -> "")),
            Map.entry(REQUIREMENT_DOC, Channels.base(() -> "")),
            Map.entry(PLAN, Channels.base(() -> "")),
            Map.entry(FRONTEND_CODE, Channels.base(() -> "")),
            Map.entry(DB_SCHEMA, Channels.base(() -> "")),
            Map.entry(BACKEND_CODE, Channels.base(() -> "")),
            Map.entry(EXISTING_CODE_CONTEXT, Channels.base(() -> "")),
            Map.entry(BACKEND_RETRY_COUNT, Channels.base(() -> 0)),
            Map.entry(SYNTAX_ERROR, Channels.base(() -> "")),
            Map.entry(NPM_ERROR, Channels.base(() -> "")),
            Map.entry(NPM_RETRY_COUNT, Channels.base(() -> 0))
    );

    public GenerationState(Map<String, Object> initData) {
        super(initData);
    }

    public String mode() { return this.<String>value(MODE).orElse("GENERATE"); }
    public Long taskId() { return this.<Long>value(TASK_ID).orElse(null); }
    public String prompt() { return this.<String>value(PROMPT).orElse(""); }
    public String additionalPrompt() { return this.<String>value(ADDITIONAL_PROMPT).orElse(null); }
    public String customPrompt() { return this.<String>value(CUSTOM_PROMPT).orElse(null); }
    public String projectDir() { return this.<String>value(PROJECT_DIR).orElse(""); }
    public String requirementDoc() { return this.<String>value(REQUIREMENT_DOC).orElse(""); }
    public String plan() { return this.<String>value(PLAN).orElse(""); }
    public String frontendCode() { return this.<String>value(FRONTEND_CODE).orElse(""); }
    public String dbSchema() { return this.<String>value(DB_SCHEMA).orElse(""); }
    public String backendCode() { return this.<String>value(BACKEND_CODE).orElse(""); }
    public String existingCodeContext() { return this.<String>value(EXISTING_CODE_CONTEXT).orElse(""); }
    public int backendRetryCount() { return this.<Integer>value(BACKEND_RETRY_COUNT).orElse(0); }
    public String syntaxError() { return this.<String>value(SYNTAX_ERROR).orElse(null); }
    public String npmError() { return this.<String>value(NPM_ERROR).orElse(null); }
    public int npmRetryCount() { return this.<Integer>value(NPM_RETRY_COUNT).orElse(0); }
}
