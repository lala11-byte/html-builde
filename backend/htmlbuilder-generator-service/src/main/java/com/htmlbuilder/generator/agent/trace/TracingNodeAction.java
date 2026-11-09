package com.htmlbuilder.generator.agent.trace;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点追踪装饰器：在每个 Graph 节点的 apply() 执行前后自动创建 LangSmith 子 run。
 * 用法：在 GenerationGraphFactory 中用 TracingNodeAction.wrap(original, name, tracer, taskId) 包装各节点。
 */
public class TracingNodeAction {

    private static final Logger log = LoggerFactory.getLogger(TracingNodeAction.class);

    /** 防止递归包装 */
    public static boolean isTraced = false;

    /**
     * 包装一个 NodeAction，使其自动在 LangSmith 创建节点级子 run。
     * 节点执行时：
     *   1. 从 TraceRegistry 获取父 trace
     *   2. 创建 node run 并压入 TraceContext（后续 LLM 调用自动成为其子 run）
     *   3. 执行原始节点
     *   4. 弹出 TraceContext，结束 node run
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static NodeAction wrap(NodeAction original, String nodeName,
                                   LangSmithTracer tracer, TraceRegistry traceRegistry) {

        return (NodeAction) state -> {
            // 从 state 提取 taskId
            Long taskId = extractTaskId(state);
            if (taskId == null) return original.apply(state);

            LangSmithRun parentTrace = traceRegistry.get(taskId);
            if (parentTrace == null || !parentTrace.isActive()) return original.apply(state);

            String parentRunId = parentTrace.getRunId();
            Map<String, Object> inputs = extractNodeInputs(state, nodeName);
            LangSmithRun nodeRun = tracer.startChildRun(parentRunId,
                    nodeName, "chain", inputs);

            TraceContext.push(nodeRun.getRunId());
            try {
                Map<String, Object> result = original.apply(state);
                Map<String, Object> outputs = new LinkedHashMap<>();
                outputs.put("status", "OK");
                // 从结果中提取关键的产出 key 作为 outputs
                for (String key : new String[]{"requirementDoc", "plan", "frontendCode", "dbSchema", "backendCode"}) {
                    Object val = extractStateValue(state, key);
                    if (val instanceof String s && !s.isEmpty()) {
                        outputs.put(key + "_length", s.length());
                    }
                }
                nodeRun.end(outputs);
                return result;
            } catch (Exception e) {
                nodeRun.endWithError(e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            } finally {
                TraceContext.pop();
                TraceContext.clearCurrentThread();
            }
        };
    }

    /** 截取 state 中的关键字段作为节点 inputs */
    private static Map<String, Object> extractNodeInputs(Object state, String nodeName) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        // 对于 LLM 调用节点，传入 messages 格式以激活对话界面
        if (nodeName.contains("planning") || nodeName.contains("requirement") ||
                nodeName.contains("frontend") || nodeName.contains("backend") ||
                nodeName.contains("database") || nodeName.contains("review")) {
            String prompt = safeStr(extractStateValue(state, "prompt"));
            if (!prompt.isEmpty()) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", "user");
                msg.put("content", prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);
                inputs.put("messages", java.util.List.of(msg));
            }
        }
        return inputs;
    }

    private static String safeStr(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private static Object extractStateValue(Object state, String key) {
        // GenerationState 使用 value() 方法
        try {
            java.lang.reflect.Method getter = state.getClass().getMethod(
                    "value", String.class);
            java.util.Optional<?> opt = (java.util.Optional<?>) getter.invoke(state, key);
            return opt.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long extractTaskId(Object state) {
        try {
            java.lang.reflect.Method getTaskId = state.getClass().getMethod("taskId");
            return (Long) getTaskId.invoke(state);
        } catch (Exception e) {
            return null;
        }
    }
}