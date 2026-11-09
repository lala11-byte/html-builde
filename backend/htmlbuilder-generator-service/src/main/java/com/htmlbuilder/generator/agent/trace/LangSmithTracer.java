package com.htmlbuilder.generator.agent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * LangSmith REST API 追踪器。
 * 通过 HTTP 直接向 LangSmith API 提交 Trace/Run 数据，
 * 无需依赖 langchain4j-langsmith 模块。
 *
 * API 文档：https://docs.smith.langchain.com/api
 * 端点：https://api.smith.langchain.com
 */
@Component
public class LangSmithTracer {

    private static final Logger log = LoggerFactory.getLogger(LangSmithTracer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String project;
    private final String endpoint;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    public LangSmithTracer(
            @Value("${langsmith.api-key:}") String apiKey,
            @Value("${langsmith.project:html-builder}") String project,
            @Value("${langsmith.endpoint:https://api.smith.langchain.com}") String endpoint) {
        this.apiKey = apiKey;
        this.project = project;
        this.endpoint = endpoint;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.jsonMapper = new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (enabled) {
            log.info("LangSmith 追踪已启用，项目: {}", project);
        } else {
            log.info("LangSmith 追踪未启用（未配置 langsmith.api-key）");
        }
    }

    /** 是否已启用追踪 */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 创建根级 Trace（对应一次完整的网站生成）
     */
    public LangSmithRun startTrace(String name, Map<String, Object> inputs) {
        if (!enabled) return LangSmithRun.NOOP;

        String runId = UUID.randomUUID().toString();
        Map<String, Object> body = buildRunBody(runId, null, name, "chain", inputs, "running");

        try {
            postRun(body);
            log.debug("LangSmith trace 已创建: {} id={}", name, runId);
            return new LangSmithRun(runId, name, this);
        } catch (Exception e) {
            log.warn("LangSmith trace 创建失败: {}", e.getMessage());
            return LangSmithRun.NOOP;
        }
    }

    /**
     * 在父 run 下创建子 run。
     * 只传 parent_run_id，不传 trace_id（LangSmith 会自动从父 run 推断 trace）。
     */
    public LangSmithRun startChildRun(String parentRunId, String name,
                                       String runType, Map<String, Object> inputs) {
        if (!enabled || parentRunId == null) return LangSmithRun.NOOP;

        String runId = UUID.randomUUID().toString();
        Map<String, Object> body = buildRunBody(runId, parentRunId,
                name, runType, inputs, "running");

        try {
            postRun(body);
            log.debug("LangSmith 子 run 已创建: {} id={}", name, runId);
            return new LangSmithRun(runId, name, this);
        } catch (Exception e) {
            log.warn("LangSmith 子 run 创建失败: {} e={}", name, e.getMessage());
            return LangSmithRun.NOOP;
        }
    }

    /**
     * 结束一个 run（成功）
     */
    public void endRun(String runId, Map<String, Object> outputs) {
        if (!enabled || runId == null) return;

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("end_time", Instant.now().toString());
        patch.put("outputs", outputs != null ? outputs : Map.of());
        patch.put("status", "success");
        patchRun(runId, patch);
    }

    /**
     * 结束一个 run（失败）
     */
    public void endRunWithError(String runId, String error) {
        if (!enabled || runId == null) return;

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("end_time", Instant.now().toString());
        patch.put("error", error);
        patch.put("status", "error");
        patchRun(runId, patch);
    }

    /**
     * 添加中间事件（可选，用于记录进度）
     */
    public void addEvent(String runId, String eventName, Map<String, Object> eventData) {
        if (!enabled || runId == null) return;

        Map<String, Object> patch = new LinkedHashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventName);
        event.put("time", Instant.now().toString());
        if (eventData != null) event.put("data", eventData);
        events.add(event);
        patch.put("events", events);
        patchRun(runId, patch);
    }

    private Map<String, Object> buildRunBody(String runId, String parentRunId,
                                              String name, String runType, Map<String, Object> inputs,
                                              String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", runId);
        body.put("name", name);
        body.put("run_type", runType);
        body.put("start_time", Instant.now().toString());
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("status", status);
        body.put("session_name", project);

        if (parentRunId != null) {
            body.put("parent_run_id", parentRunId);
        }
        return body;
    }

    private void postRun(Map<String, Object> body) {
        try {
            String json = jsonMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/runs"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("LangSmith POST 失败: {}", e.getMessage());
        }
    }

    private void patchRun(String runId, Map<String, Object> patch) {
        try {
            String json = jsonMapper.writeValueAsString(patch);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/runs/" + runId))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("LangSmith patch 失败: runId={}, err={}", runId, e.getMessage());
        }
    }

    // ==================== 便捷工厂方法 ====================

    public static Map<String, Object> inputs(String key1, Object val1) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key1, val1);
        return m;
    }

    public static Map<String, Object> inputs(String key1, Object val1, String key2, Object val2) {
        Map<String, Object> m = inputs(key1, val1);
        m.put(key2, val2);
        return m;
    }

    public static Map<String, Object> outputs(String key, Object val) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, val);
        return m;
    }
}