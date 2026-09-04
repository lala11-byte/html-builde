package com.htmlbuilder.generator.agent;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DeepSeek-v4-flash 自定义模型供应商
 * 通过 OpenAI 兼容接口调用 DeepSeek API
 * 支持非流式（短输出）和流式（长输出）两种模式
 */
public class DeepSeekChatModel {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModel.class);

    private final OpenAiChatModel chatModel;
    private final OpenAiStreamingChatModel streamingChatModel;
    private final String modelName = "deepseek-v4-flash";

    public DeepSeekChatModel(String apiKey, String baseUrl) {
        // 非流式模型：用于短输出（规划、JSON 结构）
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(120))
                .logRequests(false)
                .logResponses(false)
                .build();

        // 流式模型：用于长输出（HTML/CSS/JS/SQL/后端代码）
        this.streamingChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(16384)  // 大 maxTokens 防止截断，流式传输保证完整接收
                .timeout(Duration.ofSeconds(300))  // 长输出需要更长的超时
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * 非流式调用（用于短输出，如规划 JSON）
     */
    public String chat(String userMessage) {
        return chatModel.generate(userMessage);
    }

    /**
     * 流式调用（用于长输出，如 HTML/CSS/JS/后端代码）
     * 通过 CountDownLatch 同步等待流式输出完成，返回完整结果
     */
    public String streamChat(String userMessage) {
        StringBuilder buffer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        streamingChatModel.generate(userMessage, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                buffer.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(300, TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException("流式调用超时（300秒），模型输出可能被截断");
            }
            if (errorRef.get() != null) {
                throw new RuntimeException("流式调用失败: " + errorRef.get().getMessage(), errorRef.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("流式调用被中断", e);
        }

        String result = buffer.toString();
        log.info("流式调用完成，输出长度: {} 字符", result.length());
        return result;
    }
}