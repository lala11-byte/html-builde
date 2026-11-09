package com.htmlbuilder.generator.agent.trace;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务 → LangSmith Trace 的注册表。
 * 与 ProgressRegistry 类似，让各 Graph 节点能通过 taskId 获取父 trace runId，
 * 从而为每次 LLM 调用创建子 run，在 LangSmith 中呈现完整的 Agent 调用链。
 */
@Component
public class TraceRegistry {

    private final Map<Long, LangSmithRun> traces = new ConcurrentHashMap<>();

    public void register(Long taskId, LangSmithRun trace) {
        traces.put(taskId, trace);
    }

    public LangSmithRun get(Long taskId) {
        return traces.get(taskId);
    }

    public void unregister(Long taskId) {
        traces.remove(taskId);
    }
}