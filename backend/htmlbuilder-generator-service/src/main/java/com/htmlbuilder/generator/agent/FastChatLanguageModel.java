package com.htmlbuilder.generator.agent;

import com.htmlbuilder.generator.util.TokenUsageRecorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.generator.agent.trace.LangSmithRun;
import com.htmlbuilder.generator.agent.trace.LangSmithTracer;
import com.htmlbuilder.generator.agent.trace.TraceContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级 ChatLanguageModel（OpenAI 兼容 chat/completions 格式）
 * 使用轻量模型，用于需求分析、规划等轻量任务，避免超时截断
 */
public class FastChatLanguageModel implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(FastChatLanguageModel.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final int maxTokens;
    private final RestTemplate restTemplate;

    /** 可选：LangSmith 追踪器，非 null 时每次 LLM 调用自动创建 llm 子 run */
    private LangSmithTracer langSmithTracer;

    public FastChatLanguageModel(String apiKey, String baseUrl, String modelName, int maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.maxTokens = maxTokens;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(360).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /** 可选注入 LangSmith 追踪器。注入后每次 LLM 调用自动创建 llm 类型的子 run。 */
    public void setLangSmithTracer(LangSmithTracer tracer) {
        this.langSmithTracer = tracer;
    }

    // ==================== LangSmith LLM 追踪 ====================

    private LangSmithRun startLlmRun(String userMessage) {
        if (langSmithTracer == null) return LangSmithRun.NOOP;
        String parentRunId = TraceContext.peekRunId();
        if (parentRunId == null) return LangSmithRun.NOOP;

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", userMessage != null && userMessage.length() > 2000 ?
                userMessage.substring(0, 2000) + "...(共" + userMessage.length() + "字符)" : userMessage);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("messages", List.of(msg));

        return langSmithTracer.startChildRun(parentRunId, "llm-" + modelName, "llm", inputs);
    }

    private Map<String, Object> buildLlmOutputs(long elapsedMs, String result) {
        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("text", result != null && result.length() > 500 ?
                result.substring(0, 500) + "...(共" + result.length() + "字符)" : result);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("generations", List.of(gen));
        outputs.put("elapsed_ms", elapsedMs);
        outputs.put("output_length", result != null ? result.length() : 0);
        outputs.put("model", modelName);
        return outputs;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String userMessage = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElseThrow(() -> new IllegalArgumentException("No user message found"));
        return Response.from(AiMessage.from(chat(userMessage)));
    }

    /**
     * 非流式调用（需求分析、规划等轻量任务）
     * OpenAI 兼容格式: POST {base-url}/chat/completions
     */
    public String chat(String userMessage) {
        LangSmithRun llmRun = startLlmRun(userMessage);
        long start = System.currentTimeMillis();
        try {
            String result = chatWithRetry(userMessage, 3);
            if (llmRun.isActive()) {
                llmRun.end(buildLlmOutputs(System.currentTimeMillis() - start, result));
            }
            return result;
        } catch (Exception e) {
            if (llmRun.isActive()) {
                llmRun.endWithError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            throw e;
        }
    }

    public String chatWithRetry(String userMessage, int maxRetries) {
        Exception lastException = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return chatOnce(userMessage);
            } catch (LlmException e) {
                if (!e.retryable) {
                    throw e;
                }
                lastException = e;
                log.warn("FastChat 第 {}/{} 次调用失败: {}", attempt + 1, maxRetries, e.getMessage());
            } catch (Exception e) {
                // 网络中断、超时等，可重试
                lastException = e;
                log.warn("FastChat 第 {}/{} 次调用失败（可重试）: {}", attempt + 1, maxRetries, e.getMessage());
            }
            if (attempt < maxRetries - 1) {
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("重试被中断", false, ie);
                }
            }
        }
        String errorMsg = "FastChat 调用失败（已重试 " + maxRetries + " 次）: " +
                (lastException != null ? lastException.getMessage() : "未知错误");
        throw new LlmException(errorMsg, true, lastException);
    }

    /** 单次非流式调用 */
    private String chatOnce(String userMessage) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", userMessage)));
        requestBody.put("max_tokens", maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        Map<String, Object> body;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(baseUrl, entity, Map.class);
            body = resp;
        } catch (HttpStatusCodeException e) {
            throw translateHttpError(e);
        }

        if (body == null) {
            throw new LlmException("FastChat 返回空响应体", true);
        }
        if (body.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            throw new LlmException("FastChat API 错误: " + error.get("message"), false);
        }

        String content = extractContent(body);
        String finishReason = extractFinishReason(body);
        if (content == null) {
            if (hasReasoningContent(body)) {
                throw new LlmException("模型仅返回思考内容，未输出最终结果（finish_reason=" + finishReason
                        + "），通常是 max_tokens 不足", false);
            }
            throw new LlmException("FastChat 返回格式无法解析，响应键: " + body.keySet(), false);
        }
        if (content.isBlank()) {
            throw new LlmException("模型返回空内容（finish_reason=" + finishReason
                    + "），可能是 max_tokens 被推理内容占用或输出被截断", false);
        }
        if ("length".equals(finishReason)) {
            log.warn("【截断告警】FastChat 输出达到 max_tokens 上限（maxTokens={}），输出 {} 字符",
                    maxTokens, content.length());
        }
        log.info("FastChat 调用完成，输出长度: {} 字符，finish_reason: {}", content.length(), finishReason);
        TokenUsageRecorder.recordResponse(body.get("usage"), userMessage, content);
        return content;
    }

    /** 将 HTTP 异常翻译为可读的 LlmException，并标注是否可重试 */
    private LlmException translateHttpError(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        String detail = e.getResponseBodyAsString();
        if (detail != null && detail.length() > 300) {
            detail = detail.substring(0, 300);
        }
        if (status == 401 || status == 403) {
            return new LlmException("LLM 鉴权失败（HTTP " + status + "）：API Key 无效或已过期，请检查 llm.api-key。详情: "
                    + detail, false, e);
        }
        if (status == 400 || status == 422) {
            return new LlmException("LLM 请求参数错误（HTTP " + status + "）：请检查模型名与请求参数。详情: "
                    + detail, false, e);
        }
        if (status == 404) {
            return new LlmException("LLM 接口不存在（HTTP 404）：请检查 llm.base-url 是否为 https://api.deepseek.com。详情: "
                    + detail, false, e);
        }
        if (status == 429) {
            return new LlmException("LLM 触发限流（HTTP 429），稍后重试。详情: " + detail, true, e);
        }
        if (status >= 500) {
            return new LlmException("LLM 服务端错误（HTTP " + status + "），稍后重试。详情: " + detail, true, e);
        }
        return new LlmException("LLM 调用失败（HTTP " + status + "）。详情: " + detail, false, e);
    }

    /** LLM 调用异常，retryable 标识是否值得重试 */
    private static class LlmException extends RuntimeException {
        private final boolean retryable;

        LlmException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        LlmException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        // Anthropic 格式: content 数组 [{type: "text", text: "..."}, ...]
        Object contentObj = body.get("content");
        if (contentObj instanceof List) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : contentList) {
                String blockType = (String) block.get("type");
                if ("text".equals(blockType)) {
                    sb.append(block.get("text"));
                } else if ("thinking".equals(blockType)) {
                    // DeepSeek 思考内容，也提取（回退模式）
                    Object thinking = block.get("thinking");
                    if (thinking instanceof String) {
                        sb.append(thinking);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        // Anthropic 格式: 直接 content 字段为字符串
        if (contentObj instanceof String) {
            return (String) contentObj;
        }
        // DeepSeek 兼容: content 为单个 Map {type: "text", text: "..."}
        if (contentObj instanceof Map) {
            Map<String, Object> block = (Map<String, Object>) contentObj;
            if ("text".equals(block.get("type")) && block.get("text") != null) {
                return (String) block.get("text");
            }
            // 尝试直接取 text 字段（非标准格式）
            if (block.get("text") instanceof String) {
                return (String) block.get("text");
            }
        }

        // OpenAI 兼容格式: choices[0].message.content
        Object choicesObj = body.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                // message.content
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null && message.get("content") instanceof String content) {
                    return content;
                }
                // DeepSeek 兼容: choices[0].delta.content (流式聚合)
                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                if (delta != null && delta.get("content") instanceof String) {
                    return (String) delta.get("content");
                }
                // DeepSeek 兼容: choices[0].text
                if (choice.get("text") instanceof String) {
                    return (String) choice.get("text");
                }
            }
        }

        // DeepSeek 兼容: 顶层 message 对象 {role: "assistant", content: "..."}
        if (body.get("role") != null && body.get("content") instanceof String) {
            return (String) body.get("content");
        }

        log.error("FastChat 无法解析响应，响应键: {}, content类型: {}",
                body.keySet(),
                contentObj != null ? contentObj.getClass().getSimpleName() : "null");
        return null;
    }

    /** 读取 choices[0].finish_reason（stop / length / ...） */
    @SuppressWarnings("unchecked")
    private String extractFinishReason(Map<String, Object> body) {
        Object choicesObj = body.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Object reason = choices.get(0).get("finish_reason");
                if (reason instanceof String s) {
                    return s;
                }
            }
        }
        Object reason = body.get("stop_reason");
        return reason instanceof String s ? s : null;
    }

    /** 判断响应是否只包含推理内容（reasoning_content）而没有正文 */
    @SuppressWarnings("unchecked")
    private boolean hasReasoningContent(Map<String, Object> body) {
        Object choicesObj = body.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return message != null && message.get("reasoning_content") instanceof String s && !s.isBlank();
            }
        }
        return false;
    }
}