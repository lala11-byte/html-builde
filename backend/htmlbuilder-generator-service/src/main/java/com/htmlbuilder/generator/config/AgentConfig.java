package com.htmlbuilder.generator.config;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.FastChatLanguageModel;
import com.htmlbuilder.generator.agent.tool.PackageTool;
import com.htmlbuilder.generator.agent.trace.LangSmithTracer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${llm.model:deepseek-flash}")
    private String modelName;

    /** 轻量模型名称（用于需求分析、规划等短任务，降低超时风险） */
    @Value("${llm.fast-model:deepseek-flash}")
    private String fastModelName;

    /** 统一拼接 OpenAI 兼容的 chat/completions 端点 */
    private String chatEndpoint() {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    /** 启动时校验 API Key，避免运行期才暴露鉴权问题 */
    private String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 llm.api-key，请在 application.yml 或环境变量 LLM_API_KEY 中设置");
        }
        return apiKey.trim();
    }

    private static String mask(String key) {
        if (key == null || key.length() <= 10) {
            return "****";
        }
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }

    /**
     * 重型模型：OpenAI 兼容 chat/completions 格式，支持流式，用于代码生成
     */
    @Primary
    @Bean
    public AnthropicChatLanguageModel chatLanguageModel(LangSmithTracer langSmithTracer) {
        String key = requireApiKey();
        String endpoint = chatEndpoint();
        log.info("LLM 主模型配置: model={}, endpoint={}, apiKey={}", modelName, endpoint, mask(key));
        AnthropicChatLanguageModel model =
                new AnthropicChatLanguageModel(key, endpoint, modelName, 32768, 65536);
        model.setLangSmithTracer(langSmithTracer);
        return model;
    }

    /**
     * 轻量模型：OpenAI 兼容 chat/completions 格式，用于需求分析、规划等短任务
     */
    @Bean
    public FastChatLanguageModel fastChatLanguageModel(LangSmithTracer langSmithTracer) {
        // 推理模型（deepseek-flash）的思考 token 同样计入 max_tokens，预留足够空间避免正文被截断
        FastChatLanguageModel model =
                new FastChatLanguageModel(requireApiKey(), chatEndpoint(), fastModelName, 16384);
        model.setLangSmithTracer(langSmithTracer);
        return model;
    }

    /**
     * PackageTool：生成 package.json 等配置文件（非 Bean 的普通工具类，注册为 Bean 供 DI）
     */
    @Bean
    public PackageTool packageTool() {
        return new PackageTool();
    }
}