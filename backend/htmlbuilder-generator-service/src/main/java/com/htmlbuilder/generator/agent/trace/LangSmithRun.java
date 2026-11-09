package com.htmlbuilder.generator.agent.trace;

/**
 * 单个 LangSmith Run 的句柄。
 * 使用完成后必须调用 end() 或 endWithError() 来标记结束。
 */
public class LangSmithRun implements AutoCloseable {

    /** 空操作占位 */
    public static final LangSmithRun NOOP = new LangSmithRun(null, null, null);

    private final String runId;
    private final String name;
    private final LangSmithTracer tracer;

    public LangSmithRun(String runId, String name, LangSmithTracer tracer) {
        this.runId = runId;
        this.name = name;
        this.tracer = tracer;
    }

    public String getRunId() { return runId; }
    public String getName() { return name; }
    public boolean isActive() { return runId != null && tracer != null; }

    /** 标记成功结束 */
    public void end(java.util.Map<String, Object> outputs) {
        if (isActive()) tracer.endRun(runId, outputs);
    }

    /** 标记失败结束 */
    public void endWithError(String error) {
        if (isActive()) tracer.endRunWithError(runId, error);
    }

    /** 添加中间事件 */
    public void addEvent(String eventName, java.util.Map<String, Object> data) {
        if (isActive()) tracer.addEvent(runId, eventName, data);
    }

    @Override
    public void close() {
        // AutoCloseable — 调用方应注意使用 try-with-resources
    }
}