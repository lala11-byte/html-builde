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

/**
 * DeepSeek-v4-pro 自定义模型供应商
 * 通过 HTTP 直接调用网关 API，支持非流式（短输出）和流式（长输出）两种模式
 * <p>
 * API 网关：http://192.200.1.213:18086/v1/messages
 * 认证方式：Bearer Token（环境变量 TOKENHUB_API_KEY）
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
     */
    public String chat(String userMessage) {
        Map<String, Object> requestBody = buildRequestBody(userMessage, 4096, false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(baseUrl, entity, Map.class);

            if (body == null) {
                throw new RuntimeException("AI 返回空响应");
            }

            // 检查错误响应
            if (body.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                throw new RuntimeException("API 错误: " + error.get("message"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI 返回无 choices 字段");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            log.info("非流式调用完成，输出长度: {} 字符", content != null ? content.length() : 0);
            return content != null ? content : "";

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用（用于长输出，如 HTML/CSS/JS/后端代码）
     * 通过 SSE 协议接收 token 并拼接完整结果
     */
    public String streamChat(String userMessage) {
        Map<String, Object> requestBody = buildRequestBody(userMessage, 16384, true);

        try {
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
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // SSE 格式: "data: {json}" 或 "data: [DONE]"
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6).trim();
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    try {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> map = objectMapper.readValue(data, Map.class);
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> choices =
                                                (List<Map<String, Object>>) map.get("choices");
                                        if (choices != null && !choices.isEmpty()) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> delta =
                                                    (Map<String, Object>) choices.get(0).get("delta");
                                            if (delta != null) {
                                                String content = (String) delta.get("content");
                                                if (content != null) {
                                                    result.append(content);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("解析 SSE 数据失败: {}", data, e);
                                    }
                                }
                            }
                        }
                        log.info("流式调用完成，输出长度: {} 字符", result.length());
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
     * 构建 OpenAI 兼容的请求体
     */
    private Map<String, Object> buildRequestBody(String userMessage, int maxTokens, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));
        body.put("temperature", 0.7);
        body.put("max_tokens", maxTokens);
        body.put("stream", stream);
        return body;
    }
}