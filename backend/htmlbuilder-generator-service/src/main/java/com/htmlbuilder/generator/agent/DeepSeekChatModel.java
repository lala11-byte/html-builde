package com.htmlbuilder.generator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek-v4-pro 自定义模型供应商
 * 通过 HTTP 直接调用 Anthropic Messages API 格式的网关
 * <p>
 * API 网关：http://192.200.1.213:18086/v1/messages
 * 认证方式：Bearer Token（环境变量 TOKENHUB_API_KEY）
 * 响应格式：Anthropic Messages API（content[0].text，非 OpenAI choices）
 */
public class DeepSeekChatModel {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModel.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final RestTemplate restTemplate;
    private final RestTemplate streamingRestTemplate;

    public DeepSeekChatModel(String apiKey, String baseUrl, String modelName) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;

        // 非流式：120s 读超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());
        this.restTemplate = new RestTemplate(factory);

        // 流式：300s 读超时，不缓冲请求体
        SimpleClientHttpRequestFactory streamingFactory = new SimpleClientHttpRequestFactory();
        streamingFactory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        streamingFactory.setReadTimeout((int) Duration.ofSeconds(300).toMillis());
        streamingFactory.setBufferRequestBody(false);
        this.streamingRestTemplate = new RestTemplate(streamingFactory);
    }

    /**
     * 非流式调用（用于短输出，如规划 JSON）
     * Anthropic 响应格式：{ content: [{ type: "text", text: "..." }] }
     */
    public String chat(String userMessage) {
        Map<String, Object> requestBody = buildRequestBody(userMessage, 4096, false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        // Anthropic API 需要版本头
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(baseUrl, entity, Map.class);

            if (body == null) {
                throw new RuntimeException("AI 返回空响应");
            }

            log.debug("非流式响应键: {}", body.keySet());

            // 检查错误响应
            if (body.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                throw new RuntimeException("API 错误: " + error.get("message"));
            }

            String content = extractContent(body);
            log.info("非流式调用完成，输出长度: {} 字符", content.length());
            return content;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用（用于长输出，如 HTML/CSS/JS/后端代码）
     * Anthropic SSE 格式：
     *   event: content_block_delta
     *   data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
     */
    public String streamChat(String userMessage) {
        return streamChat(userMessage, null);
    }

    /**
     * 流式调用（带进度回调，每收到一个 chunk 通知一次）
     */
    public String streamChat(String userMessage, Consumer<String> chunkCallback) {
        Map<String, Object> requestBody = buildRequestBody(userMessage, 16384, true);

        try {
            return streamingRestTemplate.execute(
                    baseUrl,
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setBearerAuth(apiKey);
                        request.getHeaders().set("anthropic-version", "2023-06-01");
                        objectMapper.writeValue(request.getBody(), requestBody);
                    },
                    response -> {
                        StringBuilder result = new StringBuilder();
                        int chunkCount = 0;
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // Anthropic SSE: "data: {json}"
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6).trim();
                                    if (data.isEmpty() || "[DONE]".equals(data)) {
                                        continue;
                                    }
                                    try {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> map = objectMapper.readValue(data, Map.class);
                                        String text = extractDeltaText(map);
                                        if (text != null) {
                                            result.append(text);
                                            chunkCount++;
                                            // 每 20 个 chunk 通知一次进度，避免过度频繁
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
                        log.info("流式调用完成，输出长度: {} 字符，chunk 数: {}", result.length(), chunkCount);
                        if (chunkCallback != null) {
                            chunkCallback.accept("流式生成完成，共 " + result.length() + " 字符");
                        }
                        return result.toString();
                    }
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("流式调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从非流式响应中提取文本
     * 兼容两种格式：
     *   Anthropic: content[0].text
     *   OpenAI: choices[0].message.content
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        // Anthropic 格式: content 数组
        Object contentObj = body.get("content");
        if (contentObj instanceof List) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : contentList) {
                if ("text".equals(block.get("type"))) {
                    sb.append(block.get("text"));
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

        // OpenAI 兼容格式: choices[0].message.content
        Object choicesObj = body.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    String content = (String) message.get("content");
                    if (content != null) {
                        return content;
                    }
                }
            }
        }

        log.error("无法解析响应，响应键: {}", body.keySet());
        log.error("响应内容: {}", body);
        throw new RuntimeException("AI 返回格式无法解析，响应键: " + body.keySet());
    }

    /**
     * 从流式 SSE 数据中提取增量文本
     * 兼容两种格式：
     *   Anthropic: delta.text（content_block_delta 事件）
     *   OpenAI: choices[0].delta.content
     */
    @SuppressWarnings("unchecked")
    private String extractDeltaText(Map<String, Object> map) {
        // Anthropic: { type: "content_block_delta", delta: { type: "text_delta", text: "..." } }
        Object deltaObj = map.get("delta");
        if (deltaObj instanceof Map) {
            Map<String, Object> delta = (Map<String, Object>) deltaObj;
            if (delta.containsKey("text")) {
                return (String) delta.get("text");
            }
            if (delta.containsKey("content")) {
                return (String) delta.get("content");
            }
        }

        // OpenAI: { choices: [{ delta: { content: "..." } }] }
        Object choicesObj = map.get("choices");
        if (choicesObj instanceof List) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
            if (!choices.isEmpty()) {
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta != null) {
                    return (String) delta.get("content");
                }
            }
        }

        return null;
    }

    /**
     * 构建 Anthropic Messages API 请求体
     */
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
