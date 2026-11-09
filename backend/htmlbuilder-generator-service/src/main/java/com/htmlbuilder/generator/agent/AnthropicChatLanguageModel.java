package com.htmlbuilder.generator.agent;

import com.htmlbuilder.generator.util.TokenUsageRecorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htmlbuilder.generator.agent.trace.LangSmithRun;
import com.htmlbuilder.generator.agent.trace.LangSmithTracer;
import com.htmlbuilder.generator.agent.trace.TraceContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LangChain4j ChatLanguageModel + StreamingChatLanguageModel 适配器
 * 内部通过 HTTP 调用 OpenAI 兼容格式（/chat/completions）的 LLM 服务
 */
public class AnthropicChatLanguageModel implements ChatLanguageModel, StreamingChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChatLanguageModel.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final RestTemplate restTemplate;
    private final RestTemplate streamingRestTemplate;
    private final int nonStreamingMaxTokens;
    private final int streamingMaxTokens;

    /** 可选：LangSmith 追踪器，非 null 时每次 LLM 调用自动创建 llm 子 run */
    private LangSmithTracer langSmithTracer;

    public AnthropicChatLanguageModel(String apiKey, String baseUrl, String modelName,
                                       int nonStreamingMaxTokens, int streamingMaxTokens) {
        log.info("AnthropicChatLanguageModel 初始化: nonStreamingMaxTokens={}, streamingMaxTokens={}",
                nonStreamingMaxTokens, streamingMaxTokens);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.nonStreamingMaxTokens = nonStreamingMaxTokens;
        this.streamingMaxTokens = streamingMaxTokens;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(720).toMillis());
        this.restTemplate = new RestTemplate(factory);

        SimpleClientHttpRequestFactory streamingFactory = new SimpleClientHttpRequestFactory();
        streamingFactory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        streamingFactory.setReadTimeout((int) Duration.ofSeconds(2400).toMillis());
        streamingFactory.setBufferRequestBody(false);
        this.streamingRestTemplate = new RestTemplate(streamingFactory);
    }

    /**
     * 可选注入 LangSmith 追踪器。
     * 注入后每次 LLM 调用自动创建 llm 类型的子 run。
     */
    public void setLangSmithTracer(LangSmithTracer tracer) {
        this.langSmithTracer = tracer;
    }

    // ==================== LangSmith LLM 追踪 ====================

    /** 创建 LLM 调用的 LangSmith 子 run，返回句柄（调用方负责 end） */
    private LangSmithRun startLlmRun(String userMessage) {
        if (langSmithTracer == null) return LangSmithRun.NOOP;
        String parentRunId = TraceContext.peekRunId();
        if (parentRunId == null) return LangSmithRun.NOOP;

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", truncate(userMessage, 2000));
        messages.add(msg);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("messages", messages);

        return langSmithTracer.startChildRun(parentRunId, "llm-" + modelName, "llm", inputs);
    }

    /** 构建 LangSmith 标准的 generations 输出格式 */
    private Map<String, Object> buildLlmOutputs(long elapsedMs, String result) {
        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("text", truncate(result, 500));
        List<Map<String, Object>> generations = new ArrayList<>();
        generations.add(gen);

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("generations", generations);
        outputs.put("elapsed_ms", elapsedMs);
        outputs.put("output_length", result != null ? result.length() : 0);
        outputs.put("model", modelName);
        return outputs;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...(共" + s.length() + "字符)";
    }

    // ==================== ChatLanguageModel ====================

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String userMessage = extractUserMessage(messages);
        String result = doChat(userMessage);
        return Response.from(AiMessage.from(result));
    }

    /**
     * 兼容旧 API：直接传入用户消息字符串
     */
    public String chat(String userMessage) {
        LangSmithRun llmRun = startLlmRun(userMessage);
        long start = System.currentTimeMillis();
        try {
            String result = doChat(userMessage);
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

    private String doChat(String userMessage) {
        return doChatWithRetry(userMessage, 3);
    }

    private String doChatWithRetry(String userMessage, int maxRetries) {
        Exception lastException = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return doChatOnce(userMessage);
            } catch (LlmException e) {
                if (!e.retryable) {
                    throw e;
                }
                lastException = e;
                log.warn("非流式调用第 {}/{} 次失败: {}", attempt + 1, maxRetries, e.getMessage());
            } catch (Exception e) {
                // 网络中断、超时等，可重试
                lastException = e;
                log.warn("非流式调用第 {}/{} 次失败（可重试）: {}", attempt + 1, maxRetries, e.getMessage());
            }
            if (attempt < maxRetries - 1) {
                sleepBackoff(attempt);
            }
        }
        String errorMsg = "AI 非流式调用失败（已重试 " + maxRetries + " 次）: " +
                (lastException != null ? lastException.getMessage() : "未知错误");
        throw new LlmException(errorMsg, true, lastException);
    }

    /** 单次非流式调用 */
    private String doChatOnce(String userMessage) {
        Map<String, Object> requestBody = buildRequestBody(userMessage, nonStreamingMaxTokens, false);

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
            throw new LlmException("AI 返回空响应体", true);
        }
        if (body.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            throw new LlmException("API 错误: " + error.get("message"), false);
        }

        String content = extractContent(body);
        String finishReason = extractFinishReason(body);
        if (content == null) {
            if (hasReasoningContent(body)) {
                throw new LlmException("模型仅返回思考内容，未输出最终结果（finish_reason=" + finishReason
                        + "），通常是 max_tokens 不足", false);
            }
            throw new LlmException("AI 返回格式无法解析，响应键: " + body.keySet(), false);
        }
        if (content.isBlank()) {
            throw new LlmException("模型返回空内容（finish_reason=" + finishReason
                    + "），可能是 max_tokens 被推理内容占用或输出被截断", false);
        }
        if ("length".equals(finishReason)) {
            log.warn("【截断告警】非流式输出达到 max_tokens 上限（maxTokens={}），输出 {} 字符",
                    nonStreamingMaxTokens, content.length());
        }
        log.info("非流式调用完成，输出长度: {} 字符，finish_reason: {}", content.length(), finishReason);
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

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt) * 1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("重试被中断", false, ie);
        }
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

    // ==================== StreamingChatLanguageModel ====================

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        String userMessage = extractUserMessage(messages);
        doStreamChat(userMessage, text -> handler.onNext(text), handler);
    }

    /**
     * 兼容旧 API：流式调用（带进度回调）
     */
    public String streamChat(String userMessage, Consumer<String> chunkCallback) {
        LangSmithRun llmRun = startLlmRun(userMessage);
        long start = System.currentTimeMillis();
        try {
            String result = streamChat(userMessage, chunkCallback, 3);
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

    /**
     * 兼容旧 API：流式调用（带重试）
     */
    public String streamChat(String userMessage, Consumer<String> chunkCallback, int maxRetries) {
        return streamChatWithRetry(userMessage, chunkCallback, maxRetries);
    }

    private String streamChatWithRetry(String userMessage, Consumer<String> chunkCallback, int maxRetries) {
        String currentMessage = userMessage;
        StringBuilder accumulatedResult = new StringBuilder();
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String partialResult = doStreamChat(currentMessage, chunkCallback, null);
                accumulatedResult.append(partialResult);
                return accumulatedResult.toString();
            } catch (LlmException e) {
                if (!e.retryable) {
                    throw e;
                }
                lastException = e;
                log.warn("流式调用第 {}/{} 次失败: {}，已收集 {} 字符", attempt + 1, maxRetries,
                        e.getMessage(), accumulatedResult.length());
            } catch (HttpStatusCodeException e) {
                LlmException translated = translateHttpError(e);
                if (!translated.retryable) {
                    throw translated;
                }
                lastException = translated;
                log.warn("流式调用第 {}/{} 次失败: {}", attempt + 1, maxRetries, translated.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("流式调用第 {}/{} 次失败（可重试）: {}，已收集 {} 字符", attempt + 1, maxRetries,
                        e.getMessage(), accumulatedResult.length());
            }

            if (attempt < maxRetries - 1) {
                long waitMs = (long) Math.pow(2, attempt) * 1000;
                if (accumulatedResult.length() > 0) {
                    log.info("等待 {}ms 后重试（第 {} 次）...", waitMs, attempt + 2);
                    if (chunkCallback != null) {
                        chunkCallback.accept(String.format(
                                "连接中断，已收集 %d 字符，%d 秒后重试（第 %d/%d 次）...",
                                accumulatedResult.length(), waitMs / 1000, attempt + 2, maxRetries));
                    }
                }
                sleepBackoff(attempt);
                if (accumulatedResult.length() > 0) {
                    currentMessage = userMessage + "\n\n【重要】上次生成在中途断开，已生成的部分内容如下，请从断点继续生成剩余部分，不要重复已生成的内容：\n\n" +
                            accumulatedResult + "\n\n请继续生成后续内容。";
                }
            }
        }
        throw new LlmException("流式调用失败（已重试 " + maxRetries + " 次）: "
                + (lastException != null ? lastException.getMessage() : "未知错误"), true, lastException);
    }

    private String doStreamChat(String userMessage, Consumer<String> chunkCallback,
                                 StreamingResponseHandler<AiMessage> handler) {
        Map<String, Object> requestBody = buildRequestBody(userMessage, streamingMaxTokens, true);

        return streamingRestTemplate.execute(
                baseUrl,
                HttpMethod.POST,
                request -> {
                    request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    request.getHeaders().setBearerAuth(apiKey);
                    objectMapper.writeValue(request.getBody(), requestBody);
                },
                response -> {
                    StringBuilder result = new StringBuilder();
                    int chunkCount = 0;
                    long[] usageAcc = {0, 0};
                    String stopReason = null;
                    boolean messageStopReceived = false;
                    log.info("流式调用开始，streamingMaxTokens={}", streamingMaxTokens);
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if (data.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(data)) {
                                    messageStopReceived = true;
                                    continue;
                                }
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> map = objectMapper.readValue(data, Map.class);
                                    // 提取结束信号（Anthropic: message_delta.delta.stop_reason / 顶层 stop_reason；OpenAI: choices[0].finish_reason）
                                    String reason = extractStopReason(map);
                                    if (reason != null) {
                                        stopReason = reason;
                                    }
                                    if ("message_stop".equals(map.get("type"))) {
                                        messageStopReceived = true;
                                    }
                                    // 部分服务端会在流式帧中返回真实 usage（Anthropic: message.usage / usage）
                                    Object usageObj = map.get("usage");
                                    if (usageObj == null && map.get("message") instanceof Map<?, ?> msgMap) {
                                        usageObj = msgMap.get("usage");
                                    }
                                    long[] parsedUsage = TokenUsageRecorder.usageOf(usageObj);
                                    if (parsedUsage != null) {
                                        usageAcc[0] += parsedUsage[0];
                                        usageAcc[1] += parsedUsage[1];
                                    }
                                    String text = extractDeltaText(map);
                                    if (text != null) {
                                        result.append(text);
                                        chunkCount++;
                                        if (handler != null) {
                                            handler.onNext(text);
                                        }
                                        if (chunkCallback != null && chunkCount % 20 == 0) {
                                            chunkCallback.accept("已生成 " + result.length() + " 字符...");
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("解析 SSE 数据失败: {}", data);
                                }
                            }
                        }
                    }
                    log.info("流式调用完成，输出长度: {} 字符，chunk 数: {}，stop_reason: {}，message_stop: {}",
                            result.length(), chunkCount, stopReason, messageStopReceived);
                    if (usageAcc[0] > 0 || usageAcc[1] > 0) {
                        TokenUsageRecorder.record(usageAcc[0], usageAcc[1]);
                    } else {
                        TokenUsageRecorder.recordResponse(null, userMessage, result.toString());
                    }
                    if ("max_tokens".equals(stopReason) || "length".equals(stopReason)) {
                        log.warn("【截断告警】模型输出达到 max_tokens 上限被强制关闭！streamingMaxTokens={}，实际输出 {} 字符",
                                streamingMaxTokens, result.length());
                    } else if (stopReason == null || !messageStopReceived) {
                        log.warn("【异常关闭】流式读取结束但未收到完整结束信号（stop_reason={}, message_stop={}），可能是连接中断或服务端提前关闭",
                                stopReason, messageStopReceived);
                    }
                    if (result.length() == 0) {
                        throw new LlmException("流式返回内容为空（stop_reason=" + stopReason
                                + "），可能输出被截断或模型仅返回推理内容", true);
                    }
                    if (chunkCallback != null) {
                        chunkCallback.accept("流式生成完成，共 " + result.length() + " 字符");
                    }
                    if (handler != null) {
                        handler.onComplete(Response.from(AiMessage.from(result.toString())));
                    }
                    return result.toString();
                }
        );
    }

    // ==================== 响应解析 ====================

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        Object contentObj = body.get("content");
        if (contentObj instanceof List) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : contentList) {
                String blockType = (String) block.get("type");
                if ("text".equals(blockType)) {
                    sb.append(block.get("text"));
                } else if ("thinking".equals(blockType)) {
                    Object thinking = block.get("thinking");
                    if (thinking instanceof String) {
                        sb.append(thinking);
                    }
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        if (contentObj instanceof String) {
            return (String) contentObj;
        }
        Object choicesObj = body.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null && message.get("content") instanceof String content) {
                    return content;
                }
            }
        }
        return null;
    }

    /** 读取 OpenAI 兼容格式的 choices[0].finish_reason（stop / length / ...） */
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

    /**
     * 提取流式结束原因：
     * - Anthropic 格式：message_delta 事件中 delta.stop_reason（end_turn / max_tokens / stop_sequence 等）
     * - OpenAI 兼容格式：choices[0].finish_reason（stop / length 等）
     */
    @SuppressWarnings("unchecked")
    private String extractStopReason(Map<String, Object> map) {
        Object deltaObj = map.get("delta");
        if (deltaObj instanceof Map) {
            Map<String, Object> delta = (Map<String, Object>) deltaObj;
            Object reason = delta.get("stop_reason");
            if (reason instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        Object topReason = map.get("stop_reason");
        if (topReason instanceof String s && !s.isEmpty()) {
            return s;
        }
        Object choicesObj = map.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Object finishReason = choices.get(0).get("finish_reason");
                if (finishReason instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractDeltaText(Map<String, Object> map) {
        Object deltaObj = map.get("delta");
        if (deltaObj instanceof Map) {
            Map<String, Object> delta = (Map<String, Object>) deltaObj;
            if (delta.containsKey("text")) return (String) delta.get("text");
            if (delta.containsKey("content")) return (String) delta.get("content");
        }
        Object choicesObj = map.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta != null) return (String) delta.get("content");
            }
        }
        return null;
    }

    // ==================== 辅助方法 ====================

    private String extractUserMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElseThrow(() -> new IllegalArgumentException("No user message found"));
    }

    private Map<String, Object> buildRequestBody(String userMessage, int maxTokens, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));
        body.put("max_tokens", maxTokens);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }
}